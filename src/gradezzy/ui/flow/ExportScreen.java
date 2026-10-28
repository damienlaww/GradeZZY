/*
 * File: ExportScreen.java
 *
 * Purpose: Step 4. Shows the results at a glance (average, median, pass
 * rate, highest, how the marks spread), exports the score sheet and the
 * extra files, and lists similar code when that check is on.
 *
 * Where it fits: Replaces the old Statistics and Similarity Review
 * screens and the export buttons of Results. Calls summarise, export and
 * listSimilarPairs on DashboardBackend.
 */
package gradezzy.ui.flow;

import java.util.ArrayList;
import java.util.List;

import javafx.geometry.Pos;
import javafx.scene.Node;
import javafx.scene.control.CheckBox;
import javafx.scene.control.Label;
import javafx.scene.control.ProgressBar;
import javafx.scene.control.RadioButton;
import javafx.scene.control.ScrollPane;
import javafx.scene.control.ToggleGroup;
import javafx.scene.layout.HBox;
import javafx.scene.layout.Priority;
import javafx.scene.layout.Region;
import javafx.scene.layout.VBox;

import gradezzy.model.Assessment;
import gradezzy.model.GradingRun;
import gradezzy.model.Submission;
import gradezzy.plagiarism.SimilarityPair;
import gradezzy.stats.MarkBand;
import gradezzy.stats.StatisticsSummary;
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

    // The starting width of a figure tile; spare width is shared
    private static final double FIGURE_WIDTH = 120.0;

    // The width of a band's label
    private static final double BAND_LABEL_WIDTH = 70.0;

    // The width of a band's count
    private static final double BAND_COUNT_WIDTH = 30.0;

    // The scrolling page
    private final ScrollPane view = new ScrollPane();

    // The line under the heading
    private final Label subtitle = Parts.subtitle("");

    // The four figures
    private final HBox figures = new HBox(Parts.GAP);

    // The mark bands
    private final VBox bands = new VBox(Parts.GAP_SMALL);

    // Export every student
    private final RadioButton everyone = new RadioButton();

    // Export only the ticked students
    private final RadioButton tickedOnly = new RadioButton();

    // Also write the JSON file
    private final CheckBox json = new CheckBox(
            "Also save the JSON file with every question and test");

    // Where the result of the last export is shown
    private final HBox result = Parts.row(Parts.GAP_SMALL);

    // The similar code card
    private final VBox similar = new VBox(Parts.GAP_SMALL);

    // True after a successful export
    private boolean exported = false;

    /**
     * Creates the Export step.
     *
     * @param window the window
     */
    public ExportScreen(FlowWindow window) {
        super(window);
        ToggleGroup scope = new ToggleGroup();
        everyone.setToggleGroup(scope);
        tickedOnly.setToggleGroup(scope);
        everyone.setSelected(true);
        json.setSelected(true);

        VBox scoreSheet = Parts.card("Score sheet",
                Parts.muted("The provided score sheet with the marks "
                + "filled in, in the same format, ready to upload."),
                new VBox(Parts.GAP_SMALL, everyone, tickedOnly), json,
                result);
        similar.getStyleClass().add("flow-card");

        VBox column = new VBox(Parts.GAP,
                new VBox(Parts.heading("Export the results"), subtitle),
                Parts.card("Results at a glance", figures, bands),
                scoreSheet, similar);
        column.getStyleClass().add("narrow-column");
        VBox centre = new VBox(column);
        centre.setAlignment(Pos.TOP_CENTER);
        view.setContent(centre);
        view.setFitToWidth(true);
        view.getStyleClass().add("page-scroll");
    }

    @Override
    public Node getView() {
        return view;
    }

    @Override
    public void onShow() {
        GradingRun run = getState().getRun();
        getActions().setBack(() -> getWindow().show(Step.REVIEW));
        if (run == null) {
            subtitle.setText("Grade a section first.");
            return;
        }
        subtitle.setText(run.getSection().getModuleCode() + "  ·  "
                + run.getSection().getSectionCode() + "  ·  "
                + run.getAssessment().getName() + "  ·  "
                + Parts.plural(run.getSubmissions().size(), "student"));
        int ticked = getState().getSelected().size();
        everyone.setText("Every student ("
                + run.getSubmissions().size() + ")");
        tickedOnly.setText("Only the students ticked on Review ("
                + ticked + ")");
        boolean canScope = ticked > 0
                && getBackend().supports(Capability.EXPORT_SCOPE);
        tickedOnly.setDisable(!canScope);
        if (!canScope) {
            everyone.setSelected(true);
        }
        refreshFigures(run);
        refreshSimilar(run);
        refreshActions();
    }

    // Fills the four figures and the mark bands.
    private void refreshFigures(GradingRun run) {
        figures.getChildren().clear();
        bands.getChildren().clear();
        Assessment assessment = run.getAssessment();
        double highest = 0;
        for (Submission submission : run.getSubmissions()) {
            highest = Math.max(highest, submission.getTotalScore());
        }
        StatisticsSummary summary = null;
        if (getBackend().supports(Capability.STATISTICS)) {
            summary = getBackend().summarise(
                    run.getSection().getModuleCode(),
                    run.getSection().getSectionCode());
        }
        String max = " / " + Parts.mark(assessment.getMaxTotal());
        if (summary == null) {
            figures.getChildren().add(figure("Highest",
                    Parts.mark(highest) + max));
            return;
        }
        figures.getChildren().addAll(
                figure("Average", Parts.mark(Math.round(summary.getMean()
                * ONE_DECIMAL) / ONE_DECIMAL) + max),
                figure("Median", Parts.mark(summary.getMedian()) + max),
                figure("Passed", Math.round(summary.getPassRate()
                * PERCENT) + "%"),
                figure("Highest", Parts.mark(highest) + max));

        int most = 1;
        for (MarkBand band : summary.getMarkBands()) {
            most = Math.max(most, band.getStudentCount());
        }
        bands.getChildren().add(Parts.muted("How the marks spread"));
        for (MarkBand band : summary.getMarkBands()) {
            Label name = Parts.muted(Math.round(band.getLowerPercent())
                    + "-" + Math.round(band.getUpperPercent()) + "%");
            name.setMinWidth(BAND_LABEL_WIDTH);
            ProgressBar bar = new ProgressBar(
                    (double) band.getStudentCount() / most);
            bar.getStyleClass().add("band-bar");
            bar.setMaxWidth(Double.MAX_VALUE);
            HBox.setHgrow(bar, Priority.ALWAYS);
            Label count = Parts.strong(String.valueOf(
                    band.getStudentCount()));
            count.setMinWidth(BAND_COUNT_WIDTH);
            bands.getChildren().add(Parts.row(Parts.GAP, name, bar, count));
        }
    }

    // Builds one figure tile.
    private VBox figure(String name, String value) {
        Label number = new Label(value);
        number.getStyleClass().add("figure-value");
        VBox tile = new VBox(Parts.muted(name), number);
        tile.getStyleClass().add("figure");
        HBox.setHgrow(tile, Priority.ALWAYS);
        tile.setPrefWidth(FIGURE_WIDTH);
        tile.setMinWidth(Region.USE_PREF_SIZE);
        tile.setMaxWidth(Double.MAX_VALUE);
        return tile;
    }

    // Fills the similar code card, or hides it when the check is off.
    private void refreshSimilar(GradingRun run) {
        similar.getChildren().clear();
        boolean show = getBackend().supports(Capability.SIMILARITY)
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
        similar.getChildren().add(Parts.muted("These pairs share a lot of "
                + "code beyond the starter files. Similar is not proof of "
                + "copying; read both before deciding."));
        for (SimilarityPair pair : pairs) {
            similar.getChildren().add(Parts.row(Parts.GAP,
                    Parts.strong(pair.getFirstUsername() + " and "
                    + pair.getSecondUsername()),
                    Parts.muted(pair.getQuestionId()), Parts.grow(),
                    Parts.pill(Math.round(pair.getSimilarity() * PERCENT)
                    + "% similar", Tone.WARN)));
        }
    }

    // Fills the action bar.
    private void refreshActions() {
        ActionBar actions = getActions();
        actions.reset();
        actions.setBack(() -> getWindow().show(Step.REVIEW));
        if (exported) {
            actions.addSecondary(Parts.secondary("Export again",
                    () -> export()));
            actions.setPrimary("Grade another section", () -> {
                exported = false;
                result.getChildren().clear();
                getState().restart();
                getState().getBatch().clear(
                        InputKind.SUBMISSION);
                getWindow().show(Step.SET_UP);
            }, true);
            return;
        }
        boolean ready = getState().getRun() != null
                && getBackend().supports(Capability.EXPORT_RESULTS);
        actions.setPrimary("Export score sheet", () -> export(), ready);
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
