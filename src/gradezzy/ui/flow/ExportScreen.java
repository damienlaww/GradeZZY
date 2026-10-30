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
 * export and listSimilarPairs on DashboardBackend; SimilaritySheet
 * compares one pair.
 */
package gradezzy.ui.flow;

import java.util.ArrayList;
import java.util.List;

import javafx.geometry.Pos;
import javafx.scene.Node;
import javafx.scene.control.CheckBox;
import javafx.scene.control.ContentDisplay;
import javafx.scene.control.Hyperlink;
import javafx.scene.control.Label;
import javafx.scene.control.RadioButton;
import javafx.scene.control.ScrollPane;
import javafx.scene.control.ToggleGroup;
import javafx.scene.layout.ColumnConstraints;
import javafx.scene.layout.GridPane;
import javafx.scene.layout.HBox;
import javafx.scene.layout.Priority;
import javafx.scene.layout.VBox;

import gradezzy.config.ConfigKeys;
import gradezzy.model.GradingRun;
import gradezzy.model.Submission;
import gradezzy.plagiarism.SimilarityPair;
import gradezzy.ui.backend.ActionResult;
import gradezzy.ui.backend.Capability;
import gradezzy.ui.backend.InputKind;

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

    // The share of the figure grid for each column
    private static final double HALF = 50.0;

    // The size of the arrow after "See all statistics"
    private static final double ARROW_SIZE = 13.0;

    // The width of the Compare link, so the links line up
    private static final double LINK_WIDTH = 60.0;

    // The separator between parts of a line
    private static final String DOT = " · ";

    // The scrolling page
    private final ScrollPane view;

    // The line under the heading
    private final Label subtitle = Parts.subtitle("");

    // The small bell curve
    private final BellCurveChart chart = new BellCurveChart(true);

    // The four figures, two by two
    private final GridPane figures = new GridPane();

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

    // Where the result of the last export is shown
    private final HBox result = Parts.row(Parts.GAP_SMALL);

    // The similar code card
    private final VBox similar = new VBox();

    // True after a successful export
    private boolean exported = false;

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

    @Override
    public Node getView() {
        return view;
    }

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
        List<SimilarityPair> pairs = getBackend().listSimilarPairs(run);
        if (index < 0 || index >= pairs.size()) {
            return false;
        }
        getWindow().showSheet(SimilaritySheet.create(getWindow(), run,
                pairs.get(index)));
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
        figures.setHgap(Parts.GAP);
        figures.setVgap(Parts.GAP);
        ColumnConstraints left = new ColumnConstraints();
        left.setPercentWidth(HALF);
        ColumnConstraints right = new ColumnConstraints();
        right.setPercentWidth(HALF);
        figures.getColumnConstraints().addAll(left, right);
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
        json.setSelected(true);
        tickedReason.getStyleClass().addAll("small-text", "choice-reason");
        tickedRow.getChildren().addAll(tickedOnly, tickedReason);
        VBox choices = new VBox(Parts.GAP - 2, everyone, tickedRow, json);
        choices.getStyleClass().add("export-choices");
        savedTo.getStyleClass().add("small-text");
        Label about = Parts.muted("The provided score sheet with the marks "
                + "filled in, in the same format, ready to upload.");
        about.setWrapText(true);
        result.setVisible(false);
        result.setManaged(false);
        return Parts.card("Score sheet",
                new Node[] {about, choices, savedTo, result});
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
        } else {
            tickedOnly.setText("Only ticked students");
            everyone.setSelected(true);
        }
        savedTo.setText("Saved to " + getBackend().getSetting(
                ConfigKeys.OUTPUT_DIR));
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
        figures.add(figure("Average", Parts.mark(mean), max), 0, 0);
        figures.add(figure("Median", Parts.mark(stats.getMedian()), max),
                1, 0);
        figures.add(figure("Passed", Math.round(stats.getPassRate()
                * PERCENT) + "%", null), 0, 1);
        figures.add(figure("Highest", Parts.mark(stats.getHighest()), max),
                1, 1);
    }

    // Builds one compact figure tile.
    private VBox figure(String name, String value, String unit) {
        VBox tile = Parts.figure(name, value, unit, null);
        tile.getStyleClass().add("glance-figure");
        return tile;
    }

    // Fills the similar code card, or hides it when the check is off.
    private void refreshSimilar(GradingRun run) {
        similar.getChildren().clear();
        boolean show = run != null
                && getBackend().supports(Capability.SIMILARITY)
                && getBackend().isSimilarityEnabled();
        similar.setVisible(show);
        similar.setManaged(show);
        if (!show) {
            return;
        }
        similar.getChildren().add(Parts.title("Similar code"));
        List<SimilarityPair> pairs = getBackend().listSimilarPairs(run);
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
        actions.setBack(() -> getWindow().show(Step.REVIEW));
        if (exported) {
            actions.addSecondary(Parts.secondary("Export again",
                    () -> export()));
            actions.setPrimary("Grade another section",
                    () -> gradeAnother(), true);
            return;
        }
        boolean ready = getState().getRun() != null
                && getBackend().supports(Capability.EXPORT_RESULTS);
        actions.setPrimary("Export score sheet", () -> export(), ready);
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

    // Writes the CSV and, if chosen, the JSON file.
    private void export() {
        GradingRun run = getState().getRun();
        if (run == null) {
            return;
        }
        List<Submission> scope = new ArrayList<>(run.getSubmissions());
        if (tickedOnly.isSelected()) {
            scope = new ArrayList<>(getState().getSelected());
        }
        ActionResult csv = getBackend().export(run, scope, "csv");
        if (!csv.isDone()) {
            showResult(false, csv.getMessage());
            return;
        }
        String message = csv.getMessage();
        if (json.isSelected()) {
            ActionResult jsonResult = getBackend().export(run, scope,
                    "json");
            if (!jsonResult.isDone()) {
                message = message + " The JSON file failed: "
                        + jsonResult.getMessage();
            }
        }
        getBackend().recordAudit("Exported", message);
        exported = true;
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
