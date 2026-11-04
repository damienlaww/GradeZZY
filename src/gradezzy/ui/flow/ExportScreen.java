/*
 * File: ExportScreen.java
 *
 * Purpose: Step 4. Shows the results at a glance (a small bell curve,
 * the average, median, pass rate and highest mark), exports the score
 * sheet and the JSON file, and lists similar code when that check is on.
 *
 * Where it fits: Replaces the old Statistics and Similarity Review
 * screens and the export buttons of Results. The figures come from
 * MarkStats, like the Statistics tab, so the two always agree. Calls
 * export and listSimilarPairs on DashboardBackend; SimilarityCheck runs
 * the live similar code check in the background and SimilaritySheet
 * compares one pair.
 */
package gradezzy.ui.flow;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

import javafx.concurrent.Worker;
import javafx.geometry.Pos;
import javafx.scene.Node;
import javafx.scene.control.CheckBox;
import javafx.scene.control.ContentDisplay;
import javafx.scene.control.Hyperlink;
import javafx.scene.control.Label;
import javafx.scene.control.RadioButton;
import javafx.scene.control.ScrollPane;
import javafx.scene.control.ToggleGroup;
import javafx.scene.layout.HBox;
import javafx.scene.layout.Priority;
import javafx.scene.layout.VBox;

import gradezzy.config.ConfigKeys;
import gradezzy.model.GradingRun;
import gradezzy.model.Student;
import gradezzy.model.Submission;
import gradezzy.model.SubmissionStatus;
import gradezzy.plagiarism.SimilarityPair;
import gradezzy.ui.backend.ActionResult;
import gradezzy.ui.backend.Capability;
import gradezzy.ui.backend.ImportItem;
import gradezzy.ui.backend.InputKind;
import gradezzy.ui.backend.ResultState;
import gradezzy.util.TextUtils;

/**
 * The Export step.
 */
public class ExportScreen extends FlowScreen {

    // Percentages are shown as whole numbers
    private static final double PERCENT = 100.0;

    // Averages are shown to one decimal place
    private static final double ONE_DECIMAL = 10.0;

    // The width of the small bell curve
    private static final double CHART_WIDTH = 340.0;

    // The size of the arrow after "See all statistics"
    private static final double ARROW_SIZE = 13.0;

    // The width of the Compare link, so the links line up
    private static final double LINK_WIDTH = 60.0;

    // The separator between parts of a line
    private static final String DOT = " · ";

    // How many ticked students the export question names
    private static final int NAMES_IN_CONFIRM = 3;

    // The scrolling page
    private final ScrollPane view;

    // The line under the heading
    private final Label subtitle = Parts.subtitle("");

    // The small bell curve
    private final BellCurveChart chart = new BellCurveChart(true);

    // The four figures, one per row with hairlines between them
    private final VBox figures = new VBox();

    // The chart and figures, or a note when nobody is graded
    private final HBox glance = Parts.row(Parts.GAP + Parts.GAP_SMALL / 2);

    // Shown instead of the chart when nobody is graded
    private final Label noMarks = Parts.muted("No student has a mark "
            + "yet.");

    // Export every student
    private final RadioButton everyone = new RadioButton();

    // Export only the ticked students
    private final RadioButton tickedOnly = new RadioButton(
            "Only ticked students");

    // Why the ticked choice is off
    private final Label tickedReason = new Label(
            DOT + "Tick students on Review first");

    // The ticked choice and its reason
    private final HBox tickedRow = Parts.row(Parts.GAP_SMALL);

    // Also write the JSON file
    private final CheckBox json = new CheckBox(
            "Also save a JSON file with every question and test");

    // Where the files are saved
    private final Label savedTo = Parts.muted("");

    // How many marks are filled in and why the others are left blank
    private final Label blanks = Parts.muted("");

    // Where the result of the last export is shown
    private final HBox result = Parts.row(Parts.GAP_SMALL);

    // The similar code card
    private final VBox similar = new VBox();

    // The pairs listed in the similar code card
    private final List<SimilarityPair> shownPairs = new ArrayList<>();

    // The similar code check of each run, by run id: still running, or
    // ended with its result. Showing the step again reuses it.
    private final Map<String, SimilarityCheck> checks = new HashMap<>();

    // How many gradings had started when the checks above began
    private int checksFromGrading = 0;

    // True after a successful export
    private boolean exported = false;

    // True while the files are being written in the background
    private boolean exporting = false;

    /**
     * Creates the Export step.
     *
     * @param window the window
     */
    public ExportScreen(FlowWindow window) {
        super(window);
        VBox header = new VBox(Parts.heading("Export the results"),
                subtitle);
        header.getStyleClass().add("screen-header");
        similar.getStyleClass().add("flow-card");
        view = Parts.narrowPage(new Node[] {header, createGlanceCard(),
                createScoreSheetCard(), similar});
    }

    /**
     * Returns the Export step built in the constructor.
     */
    @Override
    public Node getView() {
        return view;
    }

    /**
     * Fills the figures, the score sheet card and the similar code
     * card for the current run.
     */
    @Override
    public void onShow() {
        GradingRun run = getState().getRun();
        if (run == null) {
            subtitle.setText("Grade a section first.");
            refreshFigures(null);
            refreshSimilar(null);
            refreshActions();
            return;
        }
        subtitle.setText(run.getSection().getModuleCode() + DOT
                + run.getSection().getSectionCode() + DOT
                + run.getAssessment().getName() + DOT
                + Parts.plural(run.getSubmissions().size(), "student"));
        refreshScope(run);
        refreshFigures(run);
        refreshSimilar(run);
        refreshActions();
    }

    /**
     * Opens the sheet that compares one similar pair of the current run,
     * for the developer checks. Does nothing when comparing is not
     * supported or there is no such pair.
     *
     * @param index the pair's place in the list, from 0
     * @return true when the sheet was opened
     */
    public boolean openSimilarPair(int index) {
        GradingRun run = getState().getRun();
        if (run == null
                || !getBackend().supports(Capability.SIMILARITY_DETAIL)) {
            return false;
        }
        if (index < 0 || index >= shownPairs.size()) {
            return false;
        }
        getWindow().showSheet(SimilaritySheet.create(getWindow(), run,
                shownPairs.get(index)));
        return true;
    }

    // ------------------------------------------------------------ build

    // Builds the card with the small bell curve and four figures.
    private VBox createGlanceCard() {
        Hyperlink all = Parts.link("See all statistics", () -> openStats());
        all.setGraphic(Parts.icon(Parts.ARROW, ARROW_SIZE));
        all.setContentDisplay(ContentDisplay.RIGHT);
        all.getStyleClass().addAll("small-text", "arrow-link");
        HBox top = Parts.row(Parts.GAP, new Node[] {
                Parts.title("Results at a glance"), Parts.grow(), all});

        chart.setPrefWidth(CHART_WIDTH);
        chart.setMinWidth(CHART_WIDTH);
        chart.setMaxWidth(CHART_WIDTH);
        // The chart grows to the height of the figures beside it
        chart.setMaxHeight(Double.MAX_VALUE);
        HBox.setHgrow(figures, Priority.ALWAYS);
        glance.getChildren().addAll(chart, figures);
        return Parts.card(null, new Node[] {top, glance, noMarks});
    }

    // Builds the score sheet card with the export choices.
    private VBox createScoreSheetCard() {
        ToggleGroup scope = new ToggleGroup();
        everyone.setToggleGroup(scope);
        tickedOnly.setToggleGroup(scope);
        everyone.setSelected(true);
        everyone.setOnAction(event -> refreshBlanks());
        tickedOnly.setOnAction(event -> refreshBlanks());
        json.setSelected(true);
        tickedReason.getStyleClass().addAll("small-text", "choice-reason");
        tickedRow.getChildren().addAll(tickedOnly, tickedReason);
        VBox choices = new VBox(Parts.GAP - 2, everyone, tickedRow, json);
        choices.getStyleClass().add("export-choices");
        savedTo.getStyleClass().add("small-text");
        Label about = Parts.muted("The provided score sheet with the marks "
                + "filled in, in the same format, ready to upload.");
        about.setWrapText(true);
        blanks.setWrapText(true);
        result.setVisible(false);
        result.setManaged(false);
        return Parts.card("Score sheet",
                new Node[] {about, choices, blanks, savedTo, result});
    }

    // ---------------------------------------------------------- refresh

    // Sets up the two export choices for a run.
    private void refreshScope(GradingRun run) {
        int ticked = getState().getSelected().size();
        everyone.setText("Every student (" + run.getSubmissions().size()
                + ")");
        boolean supported = getBackend().supports(Capability.EXPORT_SCOPE);
        tickedRow.setVisible(supported);
        tickedRow.setManaged(supported);
        boolean canScope = supported && ticked > 0;
        tickedOnly.setDisable(!canScope);
        tickedReason.setVisible(!canScope);
        tickedReason.setManaged(!canScope);
        if (canScope) {
            tickedOnly.setText("Only ticked students (" + ticked + ")");
            // Ticking on Review says which students to export
            tickedOnly.setSelected(true);
        } else {
            tickedOnly.setText("Only ticked students");
            everyone.setSelected(true);
        }
        savedTo.setText("Saved to " + getBackend().getSetting(
                ConfigKeys.OUTPUT_DIR));
        refreshBlanks();
    }

    // Shows how many marks the export fills in and why others are blank.
    private void refreshBlanks() {
        GradingRun run = getState().getRun();
        String text = "";
        if (run != null) {
            text = describeBlanks(run, chooseScope(run));
        }
        blanks.setText(text);
        blanks.setVisible(!text.isEmpty());
        blanks.setManaged(!text.isEmpty());
    }

    // Says how many score sheet marks are filled and why the rest are blank.
    private String describeBlanks(GradingRun run, List<Submission> scope) {
        List<Student> missing = getWindow().findMissingStudents(run,
                () -> missingFound());
        if (getState().getMissingStudents(run) == null) {
            return "";
        }

        int filled = 0;
        int failed = 0;
        int waiting = 0;
        int notTicked = 0;
        for (Submission submission : run.getSubmissions()) {
            boolean onSheet = submission.getStudent() != null;
            boolean graded = ResultState.hasTotal(submission);
            if (onSheet && graded && scope.contains(submission)) {
                filled++;
            } else if (onSheet && graded) {
                notTicked++;
            } else if (onSheet && submission.getStatus()
                    == SubmissionStatus.FAILED) {
                failed++;
            } else if (onSheet) {
                waiting++;
            }
        }
        int notOpened = countSkipped(run, missing);
        int noSubmission = missing.size() - notOpened;

        List<String> reasons = new ArrayList<>();
        addReason(reasons, failed, "could not be graded");
        addReason(reasons, notOpened, "could not be opened");
        addReason(reasons, waiting, "not graded yet");
        addReason(reasons, notTicked, "not ticked");
        addReason(reasons, noSubmission, "no submission");
        int blank = failed + notOpened + waiting + notTicked + noSubmission;
        if (filled + blank == 0) {
            return "";
        }
        String text = Parts.plural(filled, "mark") + " filled";
        if (blank == 0) {
            return text + ", none left blank.";
        }
        return text + ", " + blank + " left blank: "
                + TextUtils.join(", ", reasons) + ".";
    }

    // Counts the students with no submission whose file grading skipped.
    private int countSkipped(GradingRun run, List<Student> missing) {
        int count = 0;
        for (Student student : missing) {
            if (isSkipped(run, student.getUsername())) {
                count++;
            }
        }
        return count;
    }

    // Says whether grading skipped a file of a username.
    private boolean isSkipped(GradingRun run, String username) {
        String wanted = username.toLowerCase();
        for (ImportItem item : getState().getSkippedFiles(run)) {
            String itemUser = item.getUsername().toLowerCase();
            if (itemUser.equals(wanted)) {
                return true;
            } else if (itemUser.isEmpty()
                    && item.getName().toLowerCase().contains(wanted)) {
                return true;
            }
        }
        return false;
    }

    // Adds "N reason" to the reasons when the count is above zero.
    private void addReason(List<String> reasons, int count, String reason) {
        if (count > 0) {
            reasons.add(count + " " + reason);
        }
    }

    // Shows the blank count once the score sheet is read.
    private void missingFound() {
        if (isOnScreen()) {
            refreshBlanks();
        }
    }

    // Returns the students the chosen export choice covers.
    private List<Submission> chooseScope(GradingRun run) {
        if (tickedOnly.isSelected()) {
            return new ArrayList<>(getState().getSelected());
        }
        return new ArrayList<>(run.getSubmissions());
    }

    // Fills the small bell curve and the four figures.
    private void refreshFigures(GradingRun run) {
        figures.getChildren().clear();
        MarkStats stats = null;
        if (run != null) {
            stats = MarkStats.of(run.getSubmissions(),
                    run.getAssessment().getMaxTotal(),
                    MarkStats.parsePassPercent(getBackend().getSetting(
                    ConfigKeys.PASS_MARK_PERCENT)));
        }
        boolean empty = stats == null || stats.isEmpty();
        glance.setVisible(!empty);
        glance.setManaged(!empty);
        noMarks.setVisible(empty);
        noMarks.setManaged(empty);
        if (empty) {
            chart.setStats(null);
            return;
        }
        chart.setStats(stats);
        String max = "/ " + Parts.mark(stats.getMaxTotal());
        double mean = Math.round(stats.getMean() * ONE_DECIMAL)
                / ONE_DECIMAL;
        HBox first = figure("Average", Parts.mark(mean), max);
        first.getStyleClass().add("first");
        HBox last = figure("Highest", Parts.mark(stats.getHighest()), max);
        last.getStyleClass().add("last");
        figures.getChildren().addAll(first,
                figure("Median", Parts.mark(stats.getMedian()), max),
                figure("Passed", Math.round(stats.getPassRate() * PERCENT)
                + "%", null), last);
    }

    // Builds one row of the figure list: the name on the left and the
    // value on the right.
    private HBox figure(String name, String value, String unit) {
        HBox row = Parts.row(Parts.GAP, new Node[] {Parts.muted(name),
                Parts.grow(), Parts.figureLine(value, unit)});
        row.getStyleClass().addAll("card-row", "glance-figure");
        return row;
    }

    // Fills the similar code card, or hides it when the check is off.
    // Demo pairs are in memory, so they are shown at once; the live
    // check reads every answer, so it runs in the background, once per
    // run until grading starts again or the instructor asks.
    private void refreshSimilar(GradingRun run) {
        similar.getChildren().clear();
        shownPairs.clear();
        boolean show = run != null
                && getBackend().supports(Capability.SIMILARITY)
                && getBackend().isSimilarityEnabled();
        similar.setVisible(show);
        similar.setManaged(show);
        if (!show) {
            return;
        }
        if (getBackend().isDemo()) {
            showPairs(run, getBackend().listSimilarPairs(run));
            return;
        }
        if (getState().getGradingCount() != checksFromGrading) {
            // Grading may have changed the answers, so check them again
            checks.clear();
            checksFromGrading = getState().getGradingCount();
        }
        SimilarityCheck check = checks.get(run.getId());
        // A run read again from the database is a new object without the
        // flags of the earlier check, so it is checked again
        if (check == null || check.getRun() != run) {
            check = startSimilarityCheck(run);
        }
        showCheck(check);
    }

    // Starts the similar code check of a run on a background thread and
    // keeps it, so the card can show it until it ends.
    private SimilarityCheck startSimilarityCheck(GradingRun run) {
        SimilarityCheck check = new SimilarityCheck(getBackend(), run);
        check.setOnSucceeded(event -> showCheck(check));
        check.setOnFailed(event -> showCheck(check));
        checks.put(run.getId(), check);
        Thread worker = new Thread(check, "gradezzy-similarity");
        worker.setDaemon(true);
        worker.start();
        return check;
    }

    // Checks the run shown again, when the instructor asks.
    private void checkAgain() {
        GradingRun run = getState().getRun();
        if (run == null) {
            return;
        }
        showCheck(startSimilarityCheck(run));
    }

    // Shows a check in the card: running, its pairs, or that it failed.
    // Does nothing when another run or a newer check is shown since.
    private void showCheck(SimilarityCheck check) {
        GradingRun run = getState().getRun();
        if (!similar.isVisible() || run != check.getRun()
                || checks.get(run.getId()) != check) {
            return;
        }
        if (check.getState() == Worker.State.SUCCEEDED) {
            showPairs(run, check.getValue());
        } else if (check.getState() == Worker.State.FAILED) {
            similar.getChildren().setAll(Parts.title("Similar code"),
                    Parts.muted("The check for similar code did not "
                    + "finish."), Parts.link("Try again",
                    () -> checkAgain()));
        } else {
            similar.getChildren().setAll(Parts.title("Similar code"),
                    Parts.muted("Comparing the students' answers. This "
                    + "can take a moment."));
        }
    }

    // Fills the similar code card with the pairs found for a run. Live
    // mode offers to check again, as the files may have changed.
    private void showPairs(GradingRun run, List<SimilarityPair> pairs) {
        HBox top = Parts.row(Parts.GAP,
                new Node[] {Parts.title("Similar code")});
        if (!getBackend().isDemo()) {
            Hyperlink again = Parts.link("Check again", () -> checkAgain());
            again.getStyleClass().add("small-text");
            top.getChildren().addAll(Parts.grow(), again);
        }
        similar.getChildren().setAll(top);
        shownPairs.clear();
        shownPairs.addAll(pairs);
        if (pairs.isEmpty()) {
            similar.getChildren().add(Parts.muted("No two students' "
                    + "answers are unusually similar."));
            return;
        }
        Label about = Parts.muted("These pairs share a lot of code beyond "
                + "the starter files. Similar is not proof of copying; "
                + "read both before deciding.");
        about.setWrapText(true);
        VBox rows = new VBox();
        for (int index = 0; index < pairs.size(); index++) {
            HBox row = pairRow(run, pairs.get(index));
            row.getStyleClass().add("card-row");
            if (index == 0) {
                row.getStyleClass().add("first");
            }
            if (index == pairs.size() - 1) {
                row.getStyleClass().add("last");
            }
            rows.getChildren().add(row);
        }
        similar.getChildren().addAll(about, rows);
    }

    // Builds one row of the similar code card.
    private HBox pairRow(GradingRun run, SimilarityPair pair) {
        Label names = Parts.strong(SimilaritySheet.nameOf(run,
                pair.getFirstUsername()) + " and "
                + SimilaritySheet.nameOf(run, pair.getSecondUsername()));
        names.setMinWidth(0);
        Label question = Parts.muted(pair.getQuestionId());
        question.setMinWidth(Label.USE_PREF_SIZE);
        Node grow = Parts.grow();
        Label similar = Parts.pill(Math.round(pair.getSimilarity()
                * PERCENT) + "% similar", Tone.WARN);
        HBox row = Parts.row(Parts.GAP,
                new Node[] {names, question, grow, similar});
        if (getBackend().supports(Capability.SIMILARITY_DETAIL)) {
            Hyperlink compare = Parts.link("Compare", () -> compare(run,
                    pair));
            compare.setMinWidth(LINK_WIDTH);
            compare.setPrefWidth(LINK_WIDTH);
            compare.setAlignment(Pos.CENTER_RIGHT);
            row.getChildren().add(compare);
        }
        return row;
    }

    // Opens the sheet that shows a pair's code side by side.
    private void compare(GradingRun run, SimilarityPair pair) {
        getWindow().showSheet(SimilaritySheet.create(getWindow(), run,
                pair));
    }

    // Opens the Statistics tab on this section.
    private void openStats() {
        GradingRun run = getState().getRun();
        if (run == null) {
            getWindow().openStatistics(getState().getModuleCode(),
                    getState().getSectionCode());
            return;
        }
        getWindow().openStatistics(run.getSection().getModuleCode(),
                run.getSection().getSectionCode());
    }

    // Fills the action bar.
    private void refreshActions() {
        ActionBar actions = getActions();
        actions.reset();
        if (exporting) {
            actions.setStatus("Saving the files...");
            actions.setPrimary("Export score sheet", () -> export(), false);
            return;
        }
        actions.setBack(() -> getWindow().show(Step.REVIEW));
        if (exported) {
            actions.addSecondary(Parts.secondary("Export again",
                    () -> askExportAgain()));
            actions.setPrimary("Grade another section",
                    () -> gradeAnother(), true);
            return;
        }
        boolean ready = getState().getRun() != null
                && getBackend().supports(Capability.EXPORT_RESULTS);
        actions.setPrimary("Export score sheet", () -> export(), ready);
    }

    // Asks before writing the files again over the last export.
    private void askExportAgain() {
        getWindow().confirm("Export again?", "The files from the last "
                + "export are replaced with new ones.", "Export again",
                () -> export());
    }

    // Empties the batch and goes back to Set up.
    private void gradeAnother() {
        exported = false;
        result.getChildren().clear();
        result.setVisible(false);
        result.setManaged(false);
        getState().restart();
        getState().getBatch().clear(InputKind.SUBMISSION);
        getWindow().show(Step.SET_UP);
    }

    // Says how many students will be exported, and where, then exports
    // once the instructor confirms.
    private void export() {
        GradingRun run = getState().getRun();
        if (run == null || exporting) {
            return;
        }
        List<Submission> scope = chooseScope(run);
        int count = scope.size();
        int total = run.getSubmissions().size();
        String who = "every student";
        if (count < total) {
            who = "the " + count + " ticked students ("
                    + listNames(scope) + "). The other " + (total - count)
                    + " rows of the score sheet are left as they are";
        }
        String detail = "The marks of " + who + " are filled into a copy "
                + "of the score sheet for " + run.getSection().getLabel()
                + ", saved in " + getBackend().getSetting(
                ConfigKeys.OUTPUT_DIR) + ".";
        if (json.isSelected()) {
            detail += " A JSON file with every question and test is saved "
                    + "beside it.";
        }
        int open = countNeedingReview(scope);
        if (open > 0) {
            detail += " " + Parts.plural(open, "student") + " still "
                    + isOrAre(open) + " marked Needs review; you can export "
                    + "again after checking them.";
        }
        String students = Parts.plural(count, "student");
        getWindow().confirm("Export " + students + "?", detail,
                "Export " + students, () -> exportNow(scope));
    }

    // Names up to three students, then says how many more.
    private String listNames(List<Submission> scope) {
        String text = "";
        int shown = 0;
        for (Submission submission : scope) {
            if (shown < NAMES_IN_CONFIRM) {
                if (!text.isEmpty()) {
                    text += ", ";
                }
                text += Students.name(submission);
                shown++;
            }
        }
        if (scope.size() > shown) {
            text += " and " + (scope.size() - shown) + " more";
        }
        return text;
    }

    // Counts the students of a scope who still need review.
    private int countNeedingReview(List<Submission> scope) {
        int count = 0;
        for (Submission submission : scope) {
            if (Students.state(submission, getBackend())
                    == ResultState.NEEDS_REVIEW) {
                count++;
            }
        }
        return count;
    }

    // Returns "is" for one and "are" for more.
    private String isOrAre(int count) {
        if (count == 1) {
            return "is";
        }
        return "are";
    }

    // Writes the CSV and, if chosen, the JSON file. Live mode writes on a
    // background thread; the demo writes no file.
    private void exportNow(List<Submission> scope) {
        GradingRun run = getState().getRun();
        if (run == null || exporting) {
            return;
        }
        ExportJob job = new ExportJob(getBackend(), run, scope,
                json.isSelected());
        if (getBackend().isDemo()) {
            exportDone(job.exportNow());
            return;
        }
        job.setOnSucceeded(event -> exportDone(job.getValue()));
        job.setOnFailed(event -> exportFailed(job));
        exporting = true;
        refreshActions();
        Thread worker = new Thread(job, "gradezzy-export");
        worker.setDaemon(true);
        worker.start();
    }

    // Shows why an export stopped before it finished.
    private void exportFailed(ExportJob job) {
        exportDone(ActionResult.failed(job.getException().getMessage()));
    }

    // Shows the outcome of an export and records it when it worked.
    private void exportDone(ActionResult outcome) {
        exporting = false;
        if (!outcome.isDone()) {
            showResult(false, outcome.getMessage());
            refreshActions();
            return;
        }
        getBackend().recordAudit("Exported", outcome.getMessage());
        exported = true;
        String message = outcome.getMessage();
        if (!blanks.getText().isEmpty()) {
            message = message + " " + blanks.getText();
        }
        showResult(true, message);
        getState().reach(Step.EXPORT);
        refreshActions();
    }

    // Shows the outcome under the export choices.
    private void showResult(boolean done, String message) {
        result.getChildren().clear();
        result.setVisible(true);
        result.setManaged(true);
        Label text = Parts.muted(message);
        text.setWrapText(true);
        HBox.setHgrow(text, Priority.ALWAYS);
        if (done) {
            result.getChildren().addAll(Parts.pill("Saved", Tone.GOOD),
                    text);
        } else {
            result.getChildren().addAll(Parts.pill("Not saved", Tone.BAD),
                    text);
        }
    }
}
