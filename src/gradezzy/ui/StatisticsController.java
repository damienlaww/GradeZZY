/*
 * File: StatisticsController.java
 *
 * Purpose: Controller of the statistics dashboard.
 *
 * Where it fits: Shows figures for the graded students of one section;
 * ungraded students are left out, never counted as zero. The panels are
 * filled by classes in gradezzy.ui.view.
 */
package gradezzy.ui;

import java.util.List;

import javafx.collections.FXCollections;
import javafx.fxml.FXML;
import javafx.scene.Node;
import javafx.scene.chart.BarChart;
import javafx.scene.chart.NumberAxis;
import javafx.scene.control.Button;
import javafx.scene.control.ComboBox;
import javafx.scene.control.Label;
import javafx.scene.input.Clipboard;
import javafx.scene.input.ClipboardContent;
import javafx.scene.layout.GridPane;
import javafx.scene.layout.VBox;

import gradezzy.model.GradingRun;
import gradezzy.model.Section;
import gradezzy.model.Submission;
import gradezzy.stats.StatisticsSummary;
import gradezzy.ui.backend.Capability;
import gradezzy.ui.backend.ResultState;
import gradezzy.ui.component.EmptyState;
import gradezzy.ui.component.Icon;
import gradezzy.ui.component.Labels;
import gradezzy.ui.component.MetricTile;
import gradezzy.ui.component.Responsive;
import gradezzy.ui.view.StatisticsBars;
import gradezzy.ui.view.StatisticsDistribution;
import gradezzy.ui.view.StatisticsMistakes;
import gradezzy.ui.view.StatisticsTiles;

/**
 * Controller of the statistics dashboard.
 */
public class StatisticsController extends BaseController {

    // The share of the width given to the question difficulty panel
    private static final double DIFFICULTY_PERCENT = 50.0;

    // The width below which the two bar panels stack
    private static final double STACK_WIDTH = 760.0;

    @FXML
    private Label statusLabel;

    @FXML
    private Button copyButton;

    @FXML
    private Label moduleLabel;

    @FXML
    private ComboBox<String> moduleBox;

    @FXML
    private Label sectionLabel;

    @FXML
    private ComboBox<String> sectionBox;

    @FXML
    private VBox emptyHolder;

    @FXML
    private VBox contentBox;

    @FXML
    private MetricTile completionTile;

    @FXML
    private MetricTile meanTile;

    @FXML
    private MetricTile medianTile;

    @FXML
    private MetricTile passTile;

    @FXML
    private Label distributionSummary;

    @FXML
    private BarChart<Number, String> distributionChart;

    @FXML
    private NumberAxis countAxis;

    @FXML
    private Label distributionNote;

    @FXML
    private GridPane columns;

    @FXML
    private VBox difficultyPanel;

    @FXML
    private VBox anomalyPanel;

    @FXML
    private Label difficultySummary;

    @FXML
    private VBox difficultyRows;

    @FXML
    private Label difficultyNote;

    @FXML
    private Label anomalySummary;

    @FXML
    private VBox anomalyRows;

    @FXML
    private Label mostFailedLabel;

    @FXML
    private VBox compileRows;

    // Fills the four key numbers
    private StatisticsTiles tiles = null;

    // Draws the score distribution
    private StatisticsDistribution distribution = null;

    // Draws the question difficulty and anomaly frequency bars
    private StatisticsBars bars = null;

    // Lists the common mistakes
    private StatisticsMistakes mistakes = null;

    // True while code fills in the filter choices
    private boolean filling = false;

    // Every figure on show as plain text, for Copy Summary
    private String summaryText = "";

    /**
     * Creates a new statistics controller.
     */
    public StatisticsController() {
        // nothing to set up
    }

    /**
     * Called by JavaFX after the FXML file is loaded.
     */
    @FXML
    public void initialize() {
        moduleLabel.setLabelFor(moduleBox);
        sectionLabel.setLabelFor(sectionBox);
        Responsive.twoColumns(columns, difficultyPanel, anomalyPanel,
                DIFFICULTY_PERCENT, STACK_WIDTH);
    }

    /**
     * Creates the parts that fill the panels and keeps the module and
     * section filters in step with the context bar.
     */
    @Override
    public void onAttach() {
        tiles = new StatisticsTiles(getBackend(), completionTile, meanTile,
                medianTile, passTile);
        distribution = new StatisticsDistribution(getState(),
                distributionChart, countAxis, distributionSummary,
                distributionNote);
        bars = new StatisticsBars(getBackend(), difficultySummary,
                difficultyRows, difficultyNote, anomalySummary, anomalyRows);
        mistakes = new StatisticsMistakes(mostFailedLabel, compileRows);
        getState().moduleCodeProperty().addListener(
                (observable, oldValue, newValue) -> fillFilters());
        getState().sectionCodeProperty().addListener(
                (observable, oldValue, newValue) -> fillFilters());
    }

    /**
     * Reloads the statistics each time the screen is shown.
     */
    @Override
    public void onShow() {
        fillFilters();
        refresh();
    }

    /**
     * Applies the chosen module and section, also in the context bar.
     */
    @FXML
    public void handleFilterChanged() {
        if (filling) {
            return;
        }
        String module = moduleBox.getValue();
        String section = sectionBox.getValue();
        if (module == null) {
            return;
        }

        if (!module.equals(getState().moduleCodeProperty().get())) {
            getState().moduleCodeProperty().set(module);
            List<String> sections = getBackend().getSectionCodes(module);
            String current = getState().sectionCodeProperty().get();
            if (!sections.contains(current) && !sections.isEmpty()) {
                getState().sectionCodeProperty().set(sections.get(0));
            }
        } else if (section != null && !section.equals(
                getState().sectionCodeProperty().get())) {
            getState().sectionCodeProperty().set(section);
        }
        fillFilters();
        refresh();
    }

    /**
     * Puts a plain-text summary of every figure on the clipboard.
     */
    @FXML
    public void handleCopySummary() {
        String section = getState().getSection().getLabel();
        if (summaryText.isEmpty()) {
            showError("There are no statistics to copy for " + section
                    + " yet.");
            return;
        }
        ClipboardContent content = new ClipboardContent();
        content.putString(summaryText);
        Clipboard.getSystemClipboard().setContent(content);
        showInfo("Copied the statistics summary of " + section + ".");
    }

    /**
     * Returns the Copy Summary text. Used by the self-test.
     *
     * @return the summary, or an empty string if there is none
     */
    public String getSummaryText() {
        return summaryText;
    }

    // Fills the module and section choices from the shared state.
    private void fillFilters() {
        filling = true;
        String module = getState().moduleCodeProperty().get();
        moduleBox.setItems(FXCollections.observableArrayList(
                getBackend().getModuleCodes()));
        moduleBox.setValue(module);
        sectionBox.setItems(FXCollections.observableArrayList(
                getBackend().getSectionCodes(module)));
        sectionBox.setValue(getState().sectionCodeProperty().get());
        filling = false;
    }

    // Reloads every figure for the section in the context bar.
    private void refresh() {
        Section section = getState().getSection();
        GradingRun run = getBackend().getRun(section);
        StatisticsSummary summary = getBackend().summarise(
                section.getModuleCode(), section.getSectionCode());
        int total = 0;
        int graded = 0;
        if (run != null) {
            total = run.getSubmissions().size();
            graded = countGraded(run);
        }
        statusLabel.setText(statusText(section, run, graded, total));

        boolean hasData = summary != null;
        show(contentBox, hasData);
        show(emptyHolder, !hasData);
        copyButton.setDisable(!hasData);
        summaryText = "";
        if (!hasData) {
            emptyHolder.getChildren().setAll(emptyState(section, run,
                    graded));
            return;
        }

        double max = maxTotal(run);
        StringBuilder text = new StringBuilder();
        text.append("Statistics for ").append(statusLabel.getText())
                .append('\n');
        tiles.fill(summary, run, graded, total, max, text);
        distribution.fill(summary, max, text);
        bars.fill(summary, run, text);
        mistakes.fill(summary, text);
        summaryText = text.toString();
    }

    // Describes the section and how many students the figures cover.
    private String statusText(Section section, GradingRun run, int graded,
            int total) {
        if (run == null) {
            return section.getLabel() + " has no grading run yet, so "
                    + "there is nothing to summarise.";
        }
        int ungraded = total - graded;
        return section.getLabel() + ", run " + run.getId() + ". Based on "
                + graded + " graded student" + Labels.plural(graded) + "; "
                + ungraded + " ungraded student" + Labels.plural(ungraded)
                + " " + isOrAre(ungraded) + " left out.";
    }

    // Explains why there are no statistics and offers the next step.
    private EmptyState emptyState(Section section, GradingRun run,
            int graded) {
        String label = section.getLabel();
        EmptyState empty;
        if (run != null && graded > 0) {
            Capability statistics = Capability.STATISTICS;
            empty = new EmptyState(Icon.PLUG, "Statistics are not connected "
                    + "yet", "Statistics are waiting for "
                    + statistics.getServiceMethod() + " ("
                    + statistics.getOwner() + ").");
            empty.setAction("Backend Connections",
                    () -> getShell().navigate(Screen.HELP));
        } else if (run != null) {
            empty = new EmptyState(Icon.STATISTICS, "No graded students in "
                    + label, "Run " + run.getId() + " has "
                    + run.getSubmissions().size() + " student"
                    + Labels.plural(run.getSubmissions().size())
                    + ", but none is graded yet. Ungraded students are "
                    + "never counted as zero.");
            empty.setAction("Go to Results",
                    () -> getShell().navigate(Screen.RESULTS));
        } else {
            empty = new EmptyState(Icon.STATISTICS, "No graded students in "
                    + label, "Grade a batch for this section on the "
                    + "Submissions screen, or choose another section "
                    + "above.");
            empty.setAction("Go to Submissions",
                    () -> getShell().navigate(Screen.UPLOAD));
        }
        return empty;
    }

    // Returns the most marks of the run's assessment.
    private double maxTotal(GradingRun run) {
        if (run == null) {
            return getBackend().getAssessment().getMaxTotal();
        }
        return run.getAssessment().getMaxTotal();
    }

    // Counts the graded students of a run.
    private static int countGraded(GradingRun run) {
        int graded = 0;
        for (Submission submission : run.getSubmissions()) {
            if (ResultState.hasTotal(submission)) {
                graded++;
            }
        }
        return graded;
    }

    // Returns "is" for one and "are" for any other count.
    private static String isOrAre(int count) {
        if (count == 1) {
            return "is";
        }
        return "are";
    }

    // Shows or hides a node and frees its space.
    private static void show(Node node, boolean visible) {
        node.setVisible(visible);
        node.setManaged(visible);
    }
}
