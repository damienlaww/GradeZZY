/*
 * File: ExportScreen.java
 *
 * Purpose: Step 4. Shows the results at a glance (a small bell curve,
 * the average, median, pass rate and highest mark), exports the score
 * sheet, the JSON file and the reports, and lists similar code when
 * that check is on.
 *
 * Where it fits: Replaces the old Statistics and Similarity Review
 * screens and the export buttons of Results. The figures come from
 * MarkStats, like the Statistics tab, so the two always agree. Calls
 * export and listSimilarPairs on DashboardBackend; SimilarityCheck runs
 * the live similar code check again in the background when asked, and
 * SimilaritySheet compares one pair.
 */
package gradezzy.ui.flow;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;

import javafx.concurrent.Worker;
import javafx.geometry.Pos;
import javafx.scene.Node;
import javafx.scene.control.Button;
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
import javafx.scene.text.Text;
import javafx.scene.text.TextFlow;

import gradezzy.config.ConfigKeys;
import gradezzy.model.GradingRun;
import gradezzy.model.Student;
import gradezzy.model.Submission;
import gradezzy.model.SubmissionStatus;
import gradezzy.plagiarism.SimilarityPair;
import gradezzy.report.AnomalyReportExporter;
import gradezzy.report.AuditLogExporter;
import gradezzy.report.CommonMistakesExporter;
import gradezzy.report.JsonExporter;
import gradezzy.report.StudentReportExporter;
import gradezzy.service.ClassFileMatch;
import gradezzy.ui.backend.ActionResult;
import gradezzy.ui.backend.Capability;
import gradezzy.ui.backend.ImportItem;
import gradezzy.ui.backend.ResultState;
import gradezzy.ui.backend.RunRecord;
import gradezzy.ui.backend.StatusCounts;
import gradezzy.util.TextUtils;

/**
 * The Export step.
 *
 * <p>Tier: SHOULD.
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

    // The height of the list of students to choose from
    private static final double PICKER_HEIGHT = 280.0;

    // The words of the ticked choice, before its count
    private static final String TICKED_WORDS =
            "Only the students I choose";

    // How many ticked students the export question names
    private static final int NAMES_IN_CONFIRM = 3;

    // The gap between the export choices
    private static final double CHOICE_GAP = 10.0;

    // The gap between the small bell curve and the figures
    private static final double GLANCE_GAP = Parts.GAP + Parts.GAP_TINY;

    // Put between the items of a list in a sentence
    private static final String LIST_SEPARATOR = ", ";

    // Put between a student's name and mark in the picker
    private static final String PICKER_SEPARATOR = "  ·  ";

    // Put between a mark and the maximum mark
    private static final String OUT_OF = " / ";

    // Put before the maximum mark beside a figure
    private static final String MAX_PREFIX = "/ ";

    // A file ending, such as ".zip" or ".rar". It needs a letter, so the
    // year at the end of a username such as "ping.lee.2023" is kept
    private static final String FILE_ENDING_PATTERN =
            "\\.[a-z0-9]*[a-z][a-z0-9]*$";

    // What the score sheet gets for one submission: not counted, a mark
    // filled in, left blank because grading failed, not graded yet or
    // not chosen, or a graded student who is not on the sheet
    private static final int ROW_NONE = 0;
    private static final int ROW_FILLED = 1;
    private static final int ROW_FAILED = 2;
    private static final int ROW_WAITING = 3;
    private static final int ROW_NOT_TICKED = 4;
    private static final int ROW_OFF_SHEET = 5;

    // The names of the background threads
    private static final String SHEET_THREAD = "gradezzy-score-sheet";
    private static final String SIMILARITY_THREAD = "gradezzy-similarity";
    private static final String EXPORT_THREAD = "gradezzy-export";

    // The scrolling page
    private final ScrollPane view;

    // The line under the heading
    private final Label subtitle = Parts.subtitle("");

    // The small bell curve
    private final BellCurveChart chart = new BellCurveChart(true);

    // The four figures, one per row with hairlines between them
    private final VBox figures = new VBox();

    // The chart and figures, or a note when nobody is graded
    private final HBox glance = Parts.row(GLANCE_GAP);

    // Shown instead of the chart when nobody is graded
    private final Label noMarks = Parts.muted("No student has a mark "
            + "yet.");

    // Export every student
    private final RadioButton everyone = new RadioButton();

    // Export only the ticked students
    private final RadioButton tickedOnly = new RadioButton(TICKED_WORDS);

    // Why the ticked choice is off, under it
    private final Hyperlink tickedReason = Parts.link("Choose students",
            () -> openPicker());

    // The ticked choice and its reason
    private final VBox tickedRow = new VBox(Parts.GAP_TINY);

    // Also write the JSON file
    private final CheckBox json = new CheckBox(
            "Also save a JSON file with every question and test");

    // Also write one CSV row per problem found in each submission
    private final CheckBox problemReport = new CheckBox(
            "Also save a list of the problems found");

    // Also write one text file with a report on each student
    private final CheckBox studentReport = new CheckBox(
            "Also save a report on each student");

    // Also write the activity log
    private final CheckBox activityLog = new CheckBox(
            "Also save the activity log");

    // Also write one CSV row per answer group
    private final CheckBox commonMistakes = new CheckBox(
            "Also save the common mistakes, one row per group");

    // Where the files are saved
    private final Label savedTo = Parts.muted("");

    // Says when graded students are not on the score sheet, with a
    // better sheet found nearby or a way back to Set up
    private final HBox sheetWarning = Parts.row(Parts.GAP);
    private final Label warningTitle = new Label();
    private final Label warningText = new Label();

    // The better sheet, in bold, and how many students it lists
    private final Text suggestionName = new Text();
    private final Text suggestionDetail = new Text();
    private final TextFlow suggestionLine = new TextFlow(suggestionName,
            suggestionDetail);

    // Shown when no better sheet is found
    private final Button backToSetUp = Parts.secondary("Back to Set up",
            () -> showSetUp());

    // Shown when a better sheet is found
    private final Button useSheet = Parts.secondary("Use this file instead",
            () -> useBetterSheet());

    // The last search for a better score sheet, and what it found
    private ScoreSheetSearch sheetSearch = null;
    private ClassFileMatch betterSheet = null;

    // True while the run's score sheet is being changed
    private boolean changingSheet = false;

    // What the score sheet will get, under its title
    private final VBox outcome = new VBox(Parts.GAP_SMALL);

    // Says when another run of the section holds students this one lacks
    private final HBox otherRunNote = Parts.row(Parts.GAP_SMALL);

    // The three counts of "What the score sheet will get"
    private final Label filledCount = new Label();
    private final Label blankCount = new Label();
    private final Label blankWhy = Parts.muted("");
    private final Label offCount = new Label();
    private final VBox offCell = new VBox();

    // The counts in one sentence, added to the message after an export
    private String blankSummary = "";

    // Where the result of the last export is shown
    private final HBox result = Parts.row(Parts.GAP_SMALL);

    // The run the note in result is about, "" for none, and how many
    // gradings had started when it was shown
    private String noteRunId = "";
    private int noteFromGrading = 0;

    // The similar code card
    private final VBox similar = new VBox();

    // The pairs listed in the similar code card
    private final List<SimilarityPair> shownPairs = new ArrayList<>();

    // The last Check again of each run, by run id: still running, or
    // ended with its result. Showing the step again reuses it.
    private final Map<String, SimilarityCheck> checks = new HashMap<>();

    // How many gradings had started when the checks above began
    private int checksFromGrading = 0;

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
        // A note about another run, or from before grading again, is old
        boolean oldNote = !currentRunId().equals(noteRunId)
                || noteFromGrading != getState().getGradingCount();
        if (oldNote) {
            clearNote();
        }
        GradingRun run = getState().getRun();
        if (run == null) {
            subtitle.setText("Grade a section first.");
            refreshBlanks();
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
        boolean cannotCompare = run == null
                || !getBackend().supports(Capability.SIMILARITY_DETAIL);
        if (cannotCompare) {
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

    // Builds the score sheet card: a warning when graded students are
    // not on the sheet, who to export, what the sheet will get and
    // where the files go.
    private VBox createScoreSheetCard() {
        ToggleGroup scope = new ToggleGroup();
        everyone.setToggleGroup(scope);
        tickedOnly.setToggleGroup(scope);
        everyone.setSelected(true);
        everyone.setOnAction(event -> chooseEveryone());
        tickedOnly.setOnAction(event -> choosePicked());
        json.setSelected(true);
        tickedReason.getStyleClass().addAll("small-text", "choice-reason");
        tickedRow.getChildren().addAll(tickedOnly, tickedReason);
        VBox choices = new VBox(CHOICE_GAP, everyone, tickedRow,
                json, problemReport, studentReport, activityLog);
        if (getBackend().supports(Capability.ANSWER_GROUPS)) {
            choices.getChildren().add(commonMistakes);
        }
        choices.getStyleClass().add("export-choices");

        Label about = Parts.muted("Your score sheet with each student's "
                + "total filled in, in the same format, ready to upload to "
                + "eLearn. A column with the mark of each question is "
                + "added before the last column.");
        about.setWrapText(true);
        result.setVisible(false);
        result.setManaged(false);
        return Parts.card("Score sheet", new Node[] {about,
            createSheetWarning(), group("Who to export", choices),
            createOutcome(), createSavedLine(), result});
    }

    // Builds the warning shown when graded students are not on the
    // score sheet. Hidden until refreshBlanks finds some.
    private HBox createSheetWarning() {
        warningTitle.getStyleClass().add("warning-title");
        warningTitle.setWrapText(true);
        warningText.setWrapText(true);
        suggestionName.getStyleClass().addAll("sheet-suggestion-text",
                "sheet-suggestion-name");
        suggestionDetail.getStyleClass().add("sheet-suggestion-text");
        VBox words = new VBox(Parts.GAP_TINY, warningTitle,
                warningText, suggestionLine);
        words.setMinWidth(0);
        HBox.setHgrow(words, Priority.ALWAYS);
        backToSetUp.setMinWidth(Button.USE_PREF_SIZE);
        useSheet.setMinWidth(Button.USE_PREF_SIZE);
        sheetWarning.getChildren().addAll(words, backToSetUp, useSheet);
        sheetWarning.getStyleClass().addAll("banner", "warn",
                "sheet-warning");
        sheetWarning.setVisible(false);
        sheetWarning.setManaged(false);
        return sheetWarning;
    }

    // Builds "What the score sheet will get": three counts side by side
    // with hairlines between them. Hidden until the sheet is read.
    private VBox createOutcome() {
        offCell.getChildren().addAll(offCount, Parts.muted(
                "not on the sheet"));
        VBox first = outcomeCell(filledCount, Parts.muted("marks filled in"));
        first.getStyleClass().add("first");
        HBox cells = new HBox(first, outcomeCell(blankCount, blankWhy),
                offCell);
        offCell.getStyleClass().add("outcome-cell");
        offCount.getStyleClass().addAll("outcome-count", "warn-text");
        HBox.setHgrow(offCell, Priority.ALWAYS);
        offCell.setMaxWidth(Double.MAX_VALUE);
        cells.getStyleClass().add("outcome-cells");
        otherRunNote.setVisible(false);
        otherRunNote.setManaged(false);
        outcome.getChildren().addAll(sectionLabel(
                "What the score sheet will get"), cells, otherRunNote);
        outcome.setVisible(false);
        outcome.setManaged(false);
        return outcome;
    }

    // Builds one count of "What the score sheet will get".
    private VBox outcomeCell(Label count, Label words) {
        count.getStyleClass().add("outcome-count");
        words.getStyleClass().add("small-text");
        VBox cell = new VBox(count, words);
        cell.getStyleClass().add("outcome-cell");
        HBox.setHgrow(cell, Priority.ALWAYS);
        cell.setMaxWidth(Double.MAX_VALUE);
        cell.setMinWidth(0);
        return cell;
    }

    // Builds the line that says where the files go, with Open folder
    // and Change in Settings.
    private HBox createSavedLine() {
        savedTo.getStyleClass().add("small-text");
        savedTo.setMinWidth(0);
        Hyperlink open = Parts.link("Open folder", () -> openOutput());
        Hyperlink change = Parts.link("Change in Settings",
                () -> getWindow().showPage(Page.SETTINGS));
        open.getStyleClass().add("small-text");
        change.getStyleClass().add("small-text");
        Label dot = Parts.muted(DOT.trim());
        dot.getStyleClass().add("small-text");
        HBox line = Parts.row(Parts.FIGURE_UNIT_GAP,
                new Node[] {savedTo, open, dot, change});
        line.getStyleClass().add("saved-line");
        return line;
    }

    // Puts a small bold label over a group of controls.
    private VBox group(String title, Node content) {
        return new VBox(Parts.GAP_SMALL, sectionLabel(title), content);
    }

    // Returns a small bold label that names a part of the card.
    private Label sectionLabel(String text) {
        Label label = Parts.strong(text);
        label.getStyleClass().add("card-section");
        return label;
    }

    // Returns the folder this run's section exports to.
    private String exportFolder() {
        GradingRun run = getState().getRun();
        if (run == null) {
            return getBackend().getExportFolder(null);
        }
        return getBackend().getExportFolder(run.getSection());
    }

    // Opens the export folder in Finder or File Explorer.
    private void openOutput() {
        getWindow().openFolder(exportFolder());
    }

    // ---------------------------------------------------------- refresh

    // Sets up the two export choices for a run.
    private void refreshScope(GradingRun run) {
        everyone.setText("Every student (" + run.getSubmissions().size()
                + ")");
        boolean supported = getBackend().supports(Capability.EXPORT_SCOPE);
        tickedRow.setVisible(supported);
        tickedRow.setManaged(supported);
        // Drops chosen students who are no longer in the run
        getState().getExportPicked().retainAll(run.getSubmissions());
        int picked = getState().getExportPicked().size();
        boolean keepTicked = supported && getState().isExportOnlyPicked()
                && picked > 0;
        if (keepTicked) {
            tickedOnly.setSelected(true);
        } else {
            getState().setExportOnlyPicked(false);
            everyone.setSelected(true);
        }
        if (picked > 0) {
            tickedOnly.setText(TICKED_WORDS + " (" + picked + ")");
            tickedReason.setText("Change who is chosen");
        } else {
            tickedOnly.setText(TICKED_WORDS);
            tickedReason.setText("Choose students");
        }
        savedTo.setText("Files are saved in " + exportFolder() + ".");
        refreshBlanks();
    }

    // Shows what the score sheet will get and warns when graded students
    // are not on it. Nothing shows until the score sheet is read.
    private void refreshBlanks() {
        blankSummary = "";
        showOutcome(false);
        GradingRun run = getState().getRun();
        if (run == null) {
            return;
        }
        List<Student> missing = getWindow().findMissingStudents(run,
                () -> missingFound());
        if (getState().getMissingStudents(run) == null) {
            return;
        }

        List<Submission> scope = chooseScope(run);
        int filled = countRows(run, scope, ROW_FILLED);
        int failed = countRows(run, scope, ROW_FAILED);
        int waiting = countRows(run, scope, ROW_WAITING);
        int notTicked = countRows(run, scope, ROW_NOT_TICKED);
        int offSheet = countRows(run, scope, ROW_OFF_SHEET);
        int notOpened = countSkipped(run, missing);
        int noSubmission = missing.size() - notOpened;

        List<String> reasons = new ArrayList<>();
        addReason(reasons, failed, "could not be graded");
        addReason(reasons, notOpened, "could not be opened");
        addReason(reasons, waiting, "not graded yet");
        addReason(reasons, notTicked, "not chosen");
        addReason(reasons, noSubmission, "no submission");
        int blank = failed + notOpened + waiting + notTicked + noSubmission;
        int counted = filled + blank + offSheet;
        if (counted == 0) {
            return;
        }

        String reasonList = TextUtils.join(LIST_SEPARATOR, reasons);
        showCounts(filled, blank, offSheet, reasonList);
        refreshWarning(run, filled, offSheet, missing.size());
        int inRun = filled + failed + waiting + notTicked;
        refreshOtherRun(run, inRun, missing);
        blankSummary = summarise(filled, blank, offSheet, reasonList);
    }

    // Counts the submissions of a run that end up in one row kind.
    private int countRows(GradingRun run, List<Submission> scope,
            int kind) {
        int count = 0;
        for (Submission submission : run.getSubmissions()) {
            if (rowKind(submission, scope) == kind) {
                count++;
            }
        }
        return count;
    }

    // Says what the score sheet gets for one submission: one of the
    // ROW_ kinds, or ROW_NONE when it is not counted.
    private int rowKind(Submission submission, List<Submission> scope) {
        boolean onSheet = submission.getStudent() != null;
        boolean graded = ResultState.hasTotal(submission);
        boolean gradingFailed = submission.getStatus()
                == SubmissionStatus.FAILED;
        boolean exported = graded && scope.contains(submission);
        if (!onSheet && graded) {
            return ROW_OFF_SHEET;
        } else if (onSheet && exported) {
            return ROW_FILLED;
        } else if (onSheet && graded) {
            return ROW_NOT_TICKED;
        } else if (onSheet && gradingFailed) {
            return ROW_FAILED;
        } else if (onSheet) {
            return ROW_WAITING;
        }
        return ROW_NONE;
    }

    // Shows the three counts of "What the score sheet will get".
    private void showCounts(int filled, int blank, int offSheet,
            String reasonList) {
        filledCount.setText(String.valueOf(filled));
        blankCount.setText(String.valueOf(blank));
        blankWhy.setText("left blank");
        if (blank > 0) {
            blankWhy.setText("left blank: " + reasonList);
        }
        offCount.setText(String.valueOf(offSheet));
        offCell.setVisible(offSheet > 0);
        offCell.setManaged(offSheet > 0);
        showOutcome(true);
    }

    // Puts the counts of the score sheet in one sentence, for the
    // message after an export.
    private String summarise(int filled, int blank, int offSheet,
            String reasonList) {
        String summary = Parts.plural(filled, "mark") + " filled";
        if (blank == 0) {
            summary = summary + ", none left blank.";
        } else {
            summary = summary + ", " + blank + " left blank: "
                    + reasonList + ".";
        }
        if (offSheet > 0) {
            summary = summary + " " + offSheet + " not on the "
                    + "score sheet.";
        }
        return summary;
    }

    // Says when another run of this section has students this run lacks.
    private void refreshOtherRun(GradingRun run, int inRun,
            List<Student> missing) {
        otherRunNote.getChildren().clear();
        int more = 0;
        if (!missing.isEmpty() && run.getSection() != null) {
            more = countInOtherRun(run, missing);
        }
        boolean shown = more > 0;
        otherRunNote.setVisible(shown);
        otherRunNote.setManaged(shown);
        if (!shown) {
            return;
        }
        int onSheet = inRun + missing.size();
        Label text = Parts.muted("This run has " + inRun + " of " + onSheet
                + " students. Another " + run.getSection().getSectionCode()
                + " run has " + more + " more.");
        text.setWrapText(true);
        text.setMinWidth(0);
        otherRunNote.getChildren().addAll(Parts.pill(
                "Not the whole section", Tone.WARN), text);
    }

    // Counts the missing students graded in the other run that has most.
    private int countInOtherRun(GradingRun run, List<Student> missing) {
        int most = 0;
        for (RunRecord record : getBackend().listRuns()) {
            GradingRun other = record.getRun();
            if (isOtherRunOfSection(run, other)) {
                int found = countIn(other, missing);
                if (found > most) {
                    most = found;
                }
            }
        }
        return most;
    }

    // Says whether a run is another run of the same module and section.
    private boolean isOtherRunOfSection(GradingRun run, GradingRun other) {
        boolean notOther = other == null || other.getSection() == null
                || other.getId().equals(run.getId());
        if (notOther) {
            return false;
        }
        String module = run.getSection().getModuleCode();
        String section = run.getSection().getSectionCode();
        boolean sameModule = other.getSection().getModuleCode().equals(module);
        return sameModule
                && other.getSection().getSectionCode().equals(section);
    }

    // Counts the students with a submission in a run.
    private int countIn(GradingRun other, List<Student> students) {
        int count = 0;
        for (Student student : students) {
            if (hasSubmission(other, student.getUsername())) {
                count++;
            }
        }
        return count;
    }

    // Says whether a run has a submission of a username.
    private boolean hasSubmission(GradingRun other, String username) {
        for (Submission submission : other.getSubmissions()) {
            if (username.equalsIgnoreCase(submission.getUsername())) {
                return true;
            }
        }
        return false;
    }

    // Shows or hides "What the score sheet will get" and the warning.
    private void showOutcome(boolean shown) {
        outcome.setVisible(shown);
        outcome.setManaged(shown);
        if (!shown) {
            sheetWarning.setVisible(false);
            sheetWarning.setManaged(false);
        }
    }

    // Warns when graded students are not on the score sheet: their marks
    // would not be saved. A better sheet nearby is offered in its place;
    // without one, grading again with the right sheet fixes it.
    private void refreshWarning(GradingRun run, int filled, int offSheet,
            int otherRows) {
        boolean shown = offSheet > 0;
        sheetWarning.setVisible(shown);
        sheetWarning.setManaged(shown);
        if (!shown) {
            return;
        }
        String sheet = "this score sheet";
        if (run.getScoreSheetFile() != null) {
            sheet = run.getScoreSheetFile().getFileName().toString();
        }
        if (filled == 0 && offSheet == 1) {
            warningTitle.setText("The graded student is not on the score "
                    + "sheet");
        } else if (filled == 0) {
            warningTitle.setText("None of the " + offSheet + " graded "
                    + "students are on the score sheet");
        } else {
            warningTitle.setText(Parts.plural(offSheet, "graded student")
                    + " " + isOrAre(offSheet) + " not on the score sheet");
        }
        String text = "Their marks would not be saved to " + sheet + ".";
        if (filled == 0) {
            text = "No mark would be saved to " + sheet + ", which lists "
                    + Parts.plural(otherRows, "other student") + ".";
        }
        boolean better = lookForBetterSheet(run);
        suggestionLine.setVisible(better);
        suggestionLine.setManaged(better);
        useSheet.setVisible(better);
        useSheet.setManaged(better);
        backToSetUp.setVisible(!better);
        backToSetUp.setManaged(!better);
        if (better) {
            suggestionName.setText(betterSheet.getSuggestionName());
            suggestionDetail.setText(betterSheet.getSuggestionDetail());
            warningText.setText(text);
            return;
        }
        warningText.setText(text + " Choose the right score sheet on Set "
                + "up and grade again.");
    }

    // Says whether a better score sheet was found for the run's sheet.
    // The first time, it starts looking and returns false; the warning
    // is shown again once the search ends.
    private boolean lookForBetterSheet(GradingRun run) {
        if (sheetSearch != null && sheetSearch.isFor(run)) {
            return betterSheet != null && betterSheet.hasSuggestion();
        }
        betterSheet = null;
        ScoreSheetSearch search = new ScoreSheetSearch(getBackend(), run);
        sheetSearch = search;
        if (getBackend().isDemo()) {
            betterSheet = search.search();
            return betterSheet.hasSuggestion();
        }
        search.setOnSucceeded(event -> sheetSearched(search));
        search.setOnFailed(event -> sheetNotSearched(search));
        Thread worker = new Thread(search, SHEET_THREAD);
        worker.setDaemon(true);
        worker.start();
        return false;
    }

    // Keeps what a finished search found and shows it.
    private void sheetSearched(ScoreSheetSearch search) {
        if (search != sheetSearch) {
            return;
        }
        betterSheet = search.getValue();
        if (isOnScreen()) {
            refreshBlanks();
        }
    }

    // Treats a failed search as no better sheet, so it is not retried.
    private void sheetNotSearched(ScoreSheetSearch search) {
        if (search == sheetSearch) {
            betterSheet = ClassFileMatch.fits();
        }
    }

    // Opens Set up, to choose the right score sheet and grade again.
    private void showSetUp() {
        getWindow().show(Step.SET_UP);
    }

    // Gives the run the better score sheet, so the marks go to it
    // without grading again. The change runs on a background thread.
    private void useBetterSheet() {
        GradingRun run = getState().getRun();
        boolean blocked = run == null || changingSheet || exporting
                || betterSheet == null || !betterSheet.hasSuggestion();
        if (blocked) {
            return;
        }
        ScoreSheetChange change = new ScoreSheetChange(getBackend(), run,
                betterSheet.getSuggestion());
        if (getBackend().isDemo()) {
            sheetChanged(run, change.changeNow());
            return;
        }
        change.setOnSucceeded(event -> sheetChangeDone(run, change));
        change.setOnFailed(event -> sheetNotChanged(run, change));
        changingSheet = true;
        refreshActions();
        Thread worker = new Thread(change, SHEET_THREAD);
        worker.setDaemon(true);
        worker.start();
    }

    // Shows the outcome of a change of score sheet that finished.
    private void sheetChangeDone(GradingRun run, ScoreSheetChange change) {
        sheetChanged(run, change.getValue());
    }

    // Shows why a change of score sheet stopped before it finished.
    private void sheetNotChanged(GradingRun run, ScoreSheetChange change) {
        sheetChanged(run, ActionResult.failed(change.getException()
                .getMessage()));
    }

    // Reads the new sheet's students again and says what happened, if
    // the run is still the one shown.
    private void sheetChanged(GradingRun run, ActionResult outcome) {
        changingSheet = false;
        getState().forgetMissingStudents();
        refreshBlanks();
        boolean shown = run.getId().equals(currentRunId());
        if (shown && outcome.isDone()) {
            showNote("Score sheet changed", Tone.GOOD,
                    outcome.getMessage());
        } else if (shown) {
            showNote("Not changed", Tone.BAD, outcome.getMessage());
        }
        if (isOnScreen()) {
            refreshActions();
        }
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
        String prefixPattern = getBackend().getSetting(
                ConfigKeys.ZIP_NAME_PREFIX_PATTERN);
        for (ImportItem item : getState().getSkippedFiles(run)) {
            String itemUser = item.getUsername();
            boolean fileOfStudent = itemUser.isEmpty()
                    && isNameOf(item.getName(), username, prefixPattern);
            if (itemUser.equalsIgnoreCase(username) || fileOfStudent) {
                return true;
            }
        }
        return false;
    }

    // Says whether a file or folder name is a student's, read the way
    // grading reads a username from a name: without the zip name prefix
    // and without a file ending, in lower case, the rest equal to the
    // username. A name that only holds the username, as "alice.zip"
    // holds "li", is not that student's. Review matches the same way.
    static boolean isNameOf(String fileName, String username,
            String prefixPattern) {
        String name = fileName;
        if (!TextUtils.isBlank(prefixPattern)) {
            name = name.replaceFirst(prefixPattern, "");
        }
        String lowerName = name.trim().toLowerCase(Locale.ROOT);
        String wanted = username.toLowerCase(Locale.ROOT);
        if (lowerName.equals(wanted)) {
            return true;
        }
        String withoutEnding = lowerName.replaceFirst(FILE_ENDING_PATTERN,
                "");
        return withoutEnding.equals(wanted);
    }

    // Adds "N reason" to the reasons when the count is above zero.
    private void addReason(List<String> reasons, int count, String reason) {
        if (count > 0) {
            reasons.add(count + " " + reason);
        }
    }

    // Shows what the score sheet will get once it is read.
    private void missingFound() {
        if (isOnScreen()) {
            refreshBlanks();
        }
    }

    // Exports every student again.
    private void chooseEveryone() {
        getState().setExportOnlyPicked(false);
        refreshBlanks();
    }

    // Exports only the chosen students, asking who when nobody is chosen.
    private void choosePicked() {
        if (getState().getExportPicked().isEmpty()) {
            everyone.setSelected(true);
            openPicker();
            return;
        }
        getState().setExportOnlyPicked(true);
        refreshBlanks();
    }

    // Opens the sheet that chooses which students to export.
    private void openPicker() {
        GradingRun run = getState().getRun();
        if (run == null) {
            return;
        }
        List<Submission> picked = getState().getExportPicked();
        VBox list = new VBox(Parts.GAP_SMALL);
        List<CheckBox> boxes = new ArrayList<>();
        List<Submission> graded = new ArrayList<>();
        for (Submission submission : run.getSubmissions()) {
            // Only students with a total can be exported
            if (ResultState.hasTotal(submission)) {
                CheckBox box = new CheckBox(Students.name(submission)
                        + PICKER_SEPARATOR
                        + Parts.mark(submission.getTotalScore()) + OUT_OF
                        + Parts.mark(run.getAssessment().getMaxTotal()));
                box.setSelected(picked.contains(submission));
                box.setAccessibleText("Export " + Students.name(submission));
                boxes.add(box);
                graded.add(submission);
                list.getChildren().add(box);
            }
        }
        ScrollPane scroll = new ScrollPane(list);
        scroll.setFitToWidth(true);
        scroll.setPrefViewportHeight(PICKER_HEIGHT);
        scroll.getStyleClass().add("picker-list");

        Hyperlink all = Parts.link("Tick all", () -> setAll(boxes, true));
        Hyperlink none = Parts.link("Untick all",
                () -> setAll(boxes, false));
        Button cancel = Parts.secondary("Cancel",
                () -> getWindow().closeSheet());
        Button done = Parts.primary("Use these students");
        done.setOnAction(event -> pickerDone(boxes, graded));
        VBox body = new VBox(Parts.GAP,
                Parts.title("Choose who to export"),
                Parts.subtitle("Only the ticked students' marks are filled "
                + "in. The other rows of the score sheet are left as they "
                + "are. Students with no mark are not listed."),
                Parts.row(Parts.GAP, new Node[] {all, none}), scroll,
                Parts.row(Parts.GAP, new Node[] {Parts.grow(), cancel,
                done}));
        body.setMaxWidth(Parts.TEXT_WIDTH);
        getWindow().showSheet(body);
    }

    // Ticks or unticks every box of the picker.
    private void setAll(List<CheckBox> boxes, boolean ticked) {
        for (CheckBox box : boxes) {
            box.setSelected(ticked);
        }
    }

    // Keeps the students ticked in the picker and exports only them.
    private void pickerDone(List<CheckBox> boxes, List<Submission> graded) {
        List<Submission> picked = getState().getExportPicked();
        picked.clear();
        for (int index = 0; index < boxes.size(); index++) {
            if (boxes.get(index).isSelected()) {
                picked.add(graded.get(index));
            }
        }
        getState().setExportOnlyPicked(!picked.isEmpty());
        getWindow().closeSheet();
        GradingRun run = getState().getRun();
        if (run != null) {
            refreshScope(run);
        }
    }

    // Returns the students the chosen export choice covers.
    private List<Submission> chooseScope(GradingRun run) {
        if (tickedOnly.isSelected()) {
            return new ArrayList<>(getState().getExportPicked());
        }
        return new ArrayList<>(run.getSubmissions());
    }

    // Fills the small bell curve and the four figures.
    private void refreshFigures(GradingRun run) {
        figures.getChildren().clear();
        MarkStats stats = null;
        if (run != null) {
            String passSetting = getBackend().getSetting(
                    ConfigKeys.PASS_MARK_PERCENT);
            double passPercent = MarkStats.parsePassPercent(passSetting);
            stats = MarkStats.of(run.getSubmissions(),
                    run.getAssessment().getMaxTotal(), passPercent);
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
        String max = MAX_PREFIX + Parts.mark(stats.getMaxTotal());
        double mean = Math.round(stats.getMean() * ONE_DECIMAL)
                / ONE_DECIMAL;
        HBox first = figure("Average", Parts.mark(mean), max);
        first.getStyleClass().add("first");
        HBox last = figure("Highest", Parts.mark(stats.getHighest()), max);
        last.getStyleClass().add("last");
        String passName = "Passed (pass mark "
                + Parts.mark(stats.getPassMark()) + ")";
        long passPercent = Math.round(stats.getPassRate() * PERCENT);
        String passRate = passPercent + Parts.PERCENT_SIGN;
        figures.getChildren().addAll(first,
                figure("Median", Parts.mark(stats.getMedian()), max),
                figure(passName, passRate, null), last);
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
    // Grading runs the check, so the pairs are only read here; a Check
    // again still running or failed for this run is shown instead.
    private void refreshSimilar(GradingRun run) {
        similar.getChildren().clear();
        shownPairs.clear();
        // With the check off, the card still shows identical copies,
        // which are always looked for
        boolean show = run != null
                && getBackend().supports(Capability.SIMILARITY)
                && (getBackend().isSimilarityEnabled()
                || !getBackend().listSimilarPairs(run).isEmpty());
        similar.setVisible(show);
        similar.setManaged(show);
        if (!show) {
            return;
        }
        if (getState().getGradingCount() != checksFromGrading) {
            // Grading checked the answers again, so earlier checks are old
            checks.clear();
            checksFromGrading = getState().getGradingCount();
        }
        SimilarityCheck check = checks.get(run.getId());
        boolean unfinished = check != null && check.getRun() == run
                && check.getState() != Worker.State.SUCCEEDED;
        if (unfinished) {
            showCheck(check);
            return;
        }
        showPairs(run, getBackend().listSimilarPairs(run));
    }

    // Starts the similar code check of a run on a background thread and
    // keeps it, so the card can show it until it ends.
    private SimilarityCheck startSimilarityCheck(GradingRun run) {
        SimilarityCheck check = new SimilarityCheck(getBackend(), run);
        check.setOnSucceeded(event -> showCheck(check));
        check.setOnFailed(event -> showCheck(check));
        checks.put(run.getId(), check);
        Thread worker = new Thread(check, SIMILARITY_THREAD);
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
        boolean outdated = !similar.isVisible() || run != check.getRun()
                || checks.get(run.getId()) != check;
        if (outdated) {
            return;
        }
        if (check.getState() == Worker.State.SUCCEEDED) {
            showPairs(run, check.getValue());
        } else if (check.getState() == Worker.State.FAILED) {
            Label failed = Parts.muted("The check for similar code did not "
                    + "finish.");
            Hyperlink retry = Parts.link("Try again", () -> checkAgain());
            similar.getChildren().setAll(Parts.title("Similar code"), failed,
                    retry);
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
            similar.getChildren().add(Parts.muted("No similar pairs were "
                    + "flagged. Use Check again if the check was off or "
                    + "failed when grading."));
            return;
        }
        Label about = Parts.muted("These pairs share a lot of code beyond "
                + "the starter files. Similar is not proof of copying; "
                + "read both before deciding.");
        about.setWrapText(true);
        VBox rows = new VBox();
        for (SimilarityPair pair : pairs) {
            HBox row = pairRow(run, pair);
            row.getStyleClass().add("card-row");
            rows.getChildren().add(row);
        }
        int lastRow = rows.getChildren().size() - 1;
        rows.getChildren().get(0).getStyleClass().add("first");
        rows.getChildren().get(lastRow).getStyleClass().add("last");
        similar.getChildren().addAll(about, rows);
    }

    // Builds one row of the similar code card.
    private HBox pairRow(GradingRun run, SimilarityPair pair) {
        String first = SimilaritySheet.nameOf(run, pair.getFirstUsername());
        String second = SimilaritySheet.nameOf(run,
                pair.getSecondUsername());
        Label names = Parts.strong(first + " and " + second);
        names.setMinWidth(0);
        Label question = Parts.muted(pair.getQuestionId());
        question.setMinWidth(Label.USE_PREF_SIZE);
        Node grow = Parts.grow();
        Label similarPill = SimilaritySheet.similarityPill(pair);
        HBox row = Parts.row(Parts.GAP,
                new Node[] {names, question, grow, similarPill});
        if (getBackend().supports(Capability.SIMILARITY_DETAIL)) {
            Hyperlink compare = Parts.link("Compare",
                    () -> compare(run, pair));
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

    // Fills the action bar. Exporting and changing the score sheet are
    // off while either is running.
    private void refreshActions() {
        ActionBar actions = getActions();
        actions.reset();
        boolean busy = exporting || changingSheet;
        useSheet.setDisable(busy);
        if (exporting) {
            actions.setStatus("Saving the files...");
            actions.setPrimary("Export score sheet", () -> export(), false);
            return;
        }
        actions.setBack(() -> getWindow().show(Step.REVIEW));
        GradingRun run = getState().getRun();
        if (getState().isExported(run)) {
            Button again = Parts.secondary("Export again",
                    () -> askExportAgain());
            again.setDisable(busy);
            actions.addSecondary(again);
            actions.setPrimary("Grade another section",
                    () -> gradeAnother(), true);
            return;
        }
        boolean ready = run != null && !busy
                && getBackend().supports(Capability.EXPORT_RESULTS);
        actions.setPrimary("Export score sheet", () -> export(), ready);
    }

    // Asks before writing the files again over the last export.
    private void askExportAgain() {
        getWindow().confirm("Export again?", "The files from the last "
                + "export are replaced with new ones.", "Export again",
                () -> export());
    }

    // Empties the submissions list and goes back to Set up for the next
    // section. The files just graded are kept for this section, so they
    // come back when Set up is opened on it again.
    private void gradeAnother() {
        clearNote();
        getState().restart();
        getState().putAwaySubmissions();
        getWindow().show(Step.SET_UP);
    }

    // Says how many students will be exported, and where, then exports
    // once the instructor confirms.
    private void export() {
        GradingRun run = getState().getRun();
        boolean blocked = run == null || exporting || changingSheet;
        if (blocked) {
            return;
        }
        List<Submission> scope = chooseScope(run);
        int count = scope.size();
        int total = run.getSubmissions().size();
        String who = "every student";
        if (count < total) {
            int others = total - count;
            who = "the " + count + " chosen students ("
                    + listNames(scope) + "). The other " + others
                    + " rows of the score sheet are left as they are";
        }
        String detail = "The marks of " + who + " are filled into a copy "
                + "of the score sheet for " + run.getSection().getLabel()
                + ", saved in " + exportFolder() + ".";
        detail += describeOtherFiles();
        int open = countNeedingReview(scope);
        if (open > 0) {
            detail += " Still needing a look: "
                    + Parts.plural(open, "student") + ". You can export "
                    + "again after checking them.";
        }
        String students = Parts.plural(count, "student");
        getWindow().confirm("Export " + students + "?", detail,
                "Export " + students, () -> exportNow(scope));
    }

    // Lists the format names of the files chosen besides the CSV, in the
    // order they are written.
    private List<String> listOtherFormats() {
        List<String> formats = new ArrayList<>();
        if (json.isSelected()) {
            formats.add(JsonExporter.FORMAT_NAME);
        }
        if (problemReport.isSelected()) {
            formats.add(AnomalyReportExporter.FORMAT_NAME);
        }
        if (studentReport.isSelected()) {
            formats.add(StudentReportExporter.FORMAT_NAME);
        }
        if (activityLog.isSelected()) {
            formats.add(AuditLogExporter.FORMAT_NAME);
        }
        if (commonMistakes.isSelected()) {
            formats.add(CommonMistakesExporter.FORMAT_NAME);
        }
        return formats;
    }

    // Says which files are saved beside the score sheet, for the export
    // question, or "" when none is chosen.
    private String describeOtherFiles() {
        List<String> files = new ArrayList<>();
        if (json.isSelected()) {
            files.add("a JSON file with every question and test");
        }
        if (problemReport.isSelected()) {
            files.add("a list of the problems found");
        }
        if (studentReport.isSelected()) {
            files.add("a report on each student");
        }
        if (activityLog.isSelected()) {
            files.add("the activity log");
        }
        if (commonMistakes.isSelected()) {
            files.add("the common mistakes");
        }
        if (files.isEmpty()) {
            return "";
        }

        int count = files.size();
        int lastIndex = count - 1;
        String last = files.remove(lastIndex);
        String text = last;
        if (!files.isEmpty()) {
            text = TextUtils.join(LIST_SEPARATOR, files) + " and " + last;
        }
        String first = text.substring(0, 1).toUpperCase(Locale.ROOT);
        return " " + first + text.substring(1) + " "
                + isOrAre(count) + " saved beside it.";
    }

    // Names up to three students, then says how many more.
    private String listNames(List<Submission> scope) {
        List<String> names = new ArrayList<>();
        int shown = 0;
        for (Submission submission : scope) {
            if (shown < NAMES_IN_CONFIRM) {
                names.add(Students.name(submission));
                shown++;
            }
        }
        String text = TextUtils.join(LIST_SEPARATOR, names);
        if (scope.size() > shown) {
            int more = scope.size() - shown;
            text += " and " + more + " more";
        }
        return text;
    }

    // Counts the students of a scope who still need a look.
    private int countNeedingReview(List<Submission> scope) {
        return StatusCounts.of(scope, getBackend()).getNeedsLook();
    }

    // Returns "is" for one and "are" for more.
    private String isOrAre(int count) {
        if (count == 1) {
            return "is";
        }
        return "are";
    }

    // Writes the CSV and, if chosen, the JSON file and the reports. Live
    // mode writes on a background thread; the demo writes no file.
    private void exportNow(List<Submission> scope) {
        GradingRun run = getState().getRun();
        boolean blocked = run == null || exporting || changingSheet;
        if (blocked) {
            return;
        }
        ExportJob job = new ExportJob(getBackend(), run, scope,
                listOtherFormats());
        // Only an export of every student marks the run as exported
        boolean wholeRun = scope.size() == run.getSubmissions().size();
        if (getBackend().isDemo()) {
            exportDone(run, wholeRun, job.exportNow());
            return;
        }
        job.setOnSucceeded(event -> exportFinished(run, wholeRun, job));
        job.setOnFailed(event -> exportFailed(run, job));
        exporting = true;
        refreshActions();
        Thread worker = new Thread(job, EXPORT_THREAD);
        worker.setDaemon(true);
        worker.start();
    }

    // Shows the outcome of an export that finished.
    private void exportFinished(GradingRun run, boolean wholeRun,
            ExportJob job) {
        exportDone(run, wholeRun, job.getValue());
    }

    // Shows why an export stopped before it finished.
    private void exportFailed(GradingRun run, ExportJob job) {
        exportDone(run, false, ActionResult.failed(job.getException()
                .getMessage()));
    }

    // Records an export that worked, and shows the outcome on screen
    // when its run is still the one shown, else in a notice.
    private void exportDone(GradingRun run, boolean wholeRun,
            ActionResult outcome) {
        exporting = false;
        if (outcome.isDone()) {
            // The files were written, even if another run is shown now
            getBackend().recordAudit("Exported", outcome.getMessage());
        }
        if (outcome.isDone() && wholeRun) {
            getState().markExported(run);
        }
        boolean shown = run.getId().equals(currentRunId());
        if (shown && outcome.isDone()) {
            String message = outcome.getMessage();
            if (!blankSummary.isEmpty()) {
                message = message + " " + blankSummary;
            }
            showResult(true, message);
            getState().reach(Step.EXPORT);
        } else if (shown) {
            showResult(false, outcome.getMessage());
        } else if (outcome.isDone()) {
            getWindow().notify(run.getSection().getLabel() + ": "
                    + outcome.getMessage());
        } else {
            getWindow().notify("The export of "
                    + run.getSection().getLabel() + " failed: "
                    + outcome.getMessage());
        }
        if (isOnScreen()) {
            refreshActions();
        }
    }

    // Shows the outcome under the export choices. Demo mode writes no
    // file, so its pill says Preview.
    private void showResult(boolean done, String message) {
        if (done && getBackend().isDemo()) {
            showNote("Preview", Tone.GOOD, message);
        } else if (done) {
            showNote("Saved", Tone.GOOD, message);
        } else {
            showNote("Not saved", Tone.BAD, message);
        }
    }

    // Shows a pill and a message under the export choices, about the
    // run shown now.
    private void showNote(String pill, Tone tone, String message) {
        result.getChildren().clear();
        result.setVisible(true);
        result.setManaged(true);
        Label text = Parts.muted(message);
        text.setWrapText(true);
        HBox.setHgrow(text, Priority.ALWAYS);
        result.getChildren().addAll(Parts.pill(pill, tone), text);
        noteRunId = currentRunId();
        noteFromGrading = getState().getGradingCount();
    }

    // Empties and hides the note under the export choices.
    private void clearNote() {
        result.getChildren().clear();
        result.setVisible(false);
        result.setManaged(false);
        noteRunId = "";
    }

    // Returns the id of the run shown, or "" when there is none.
    private String currentRunId() {
        GradingRun run = getState().getRun();
        if (run == null) {
            return "";
        }
        return run.getId();
    }
}
