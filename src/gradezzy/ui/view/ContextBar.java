/*
 * File: ContextBar.java
 *
 * Purpose: Keeps the context bar's module, section, assessment, run and
 * demo badge in step with UiState.
 *
 * Where it fits: Created by MainController. A module or section change,
 * here or on another screen, tells MainController to reload the screen.
 */
package gradezzy.ui.view;

import java.util.ArrayList;
import java.util.List;

import javafx.collections.FXCollections;
import javafx.scene.Node;
import javafx.scene.control.ComboBox;
import javafx.scene.control.Label;
import javafx.scene.control.Tooltip;

import gradezzy.model.Assessment;
import gradezzy.model.GradingRun;
import gradezzy.model.Submission;
import gradezzy.ui.GradingMonitor;
import gradezzy.ui.UiState;
import gradezzy.ui.backend.DashboardBackend;
import gradezzy.ui.backend.ResultState;
import gradezzy.ui.component.Labels;

/**
 * The context bar at the top of the window.
 */
public final class ContextBar {

    // The module choice
    private final ComboBox<String> moduleBox;

    // The section choice
    private final ComboBox<String> sectionBox;

    // Shows the grading run of the section
    private final Label runLabel;

    // Shows the assessment and its maximum
    private final Label assessmentLabel;

    // Shown only in demo mode
    private final Label demoBadge;

    // Captions dropped in a compact window
    private final List<Node> captions = new ArrayList<>();

    // The backend, for modules, sections and runs
    private final DashboardBackend backend;

    // The state shared by every screen
    private final UiState state;

    // Called after the module or section changed
    private final Runnable onContextChanged;

    // Whether the choices are being filled in by code
    private boolean updating = false;

    /**
     * Creates the context bar.
     *
     * @param moduleBox the module choice
     * @param sectionBox the section choice
     * @param runLabel shows the grading run of the section
     * @param assessmentLabel shows the assessment
     * @param demoBadge shown only in demo mode
     * @param captions caption labels dropped in a compact window
     * @param backend the backend
     * @param state the shared state
     * @param onContextChanged called after the module or section changed
     */
    public ContextBar(ComboBox<String> moduleBox,
            ComboBox<String> sectionBox, Label runLabel,
            Label assessmentLabel, Label demoBadge, List<Node> captions,
            DashboardBackend backend, UiState state,
            Runnable onContextChanged) {
        this.moduleBox = moduleBox;
        this.sectionBox = sectionBox;
        this.runLabel = runLabel;
        this.assessmentLabel = assessmentLabel;
        this.demoBadge = demoBadge;
        this.captions.addAll(captions);
        this.backend = backend;
        this.state = state;
        this.onContextChanged = onContextChanged;
    }

    /**
     * Fills in the choices and labels and follows later changes.
     */
    public void build() {
        updating = true;
        moduleBox.setItems(FXCollections.observableArrayList(
                backend.getModuleCodes()));
        moduleBox.getSelectionModel().selectFirst();
        state.moduleCodeProperty().set(moduleBox.getValue());
        fillSections();
        updating = false;

        Assessment assessment = backend.getAssessment();
        state.assessmentProperty().set(assessment);
        assessmentLabel.setText(assessment.getName() + ", "
                + Labels.formatScore(assessment.getMaxTotal()) + " marks");
        assessmentLabel.setTooltip(new Tooltip(assessmentLabel.getText()));

        demoBadge.setVisible(backend.isDemo());
        demoBadge.setManaged(backend.isDemo());
        demoBadge.setTooltip(new Tooltip("Synthetic students. Demo mode "
                + "never reads, grades or saves real files."));

        moduleBox.valueProperty().addListener(
                (observable, oldValue, newValue) -> moduleChosen(newValue));
        sectionBox.valueProperty().addListener(
                (observable, oldValue, newValue) -> sectionChosen(newValue));
        state.sectionCodeProperty().addListener(
                (observable, oldValue, newValue) -> syncSection(newValue));
        state.moduleCodeProperty().addListener(
                (observable, oldValue, newValue) -> syncModule(newValue));
    }

    /**
     * Shows the run of the section, or the progress of a run going now.
     */
    public void refreshRun() {
        GradingMonitor monitor = state.getMonitor();
        GradingRun run = backend.getRun(state.getSection());
        state.runProperty().set(run);
        if (monitor.isActive()) {
            runLabel.setText("Grading " + monitor.doneProperty().get()
                    + " of " + monitor.totalProperty().get());
        } else if (run == null) {
            runLabel.setText("No run yet");
        } else {
            runLabel.setText("Run " + run.getId() + ", " + countGraded(run)
                    + " of " + run.getSubmissions().size() + " graded");
        }
        if (runLabel.getTooltip() == null) {
            runLabel.setTooltip(new Tooltip());
        }
        runLabel.getTooltip().setText(runLabel.getText());
    }

    /**
     * Drops the captions in a compact window and brings them back.
     *
     * @param compact true while the window is compact
     */
    public void applyCompact(boolean compact) {
        for (Node caption : captions) {
            caption.setVisible(!compact);
            caption.setManaged(!compact);
        }
    }

    // Fills the section choices for the chosen module.
    private void fillSections() {
        List<String> sections = backend.getSectionCodes(
                state.moduleCodeProperty().get());
        sectionBox.setItems(FXCollections.observableArrayList(sections));
        sectionBox.getSelectionModel().selectFirst();
        String section = sectionBox.getValue();
        if (section == null) {
            section = "";
        }
        state.sectionCodeProperty().set(section);
    }

    // Reacts to a new module choice in the bar.
    private void moduleChosen(String moduleCode) {
        if (updating || moduleCode == null) {
            return;
        }
        updating = true;
        state.moduleCodeProperty().set(moduleCode);
        fillSections();
        updating = false;
        onContextChanged.run();
    }

    // Reacts to a new section choice in the bar.
    private void sectionChosen(String sectionCode) {
        if (updating || sectionCode == null) {
            return;
        }
        state.sectionCodeProperty().set(sectionCode);
        onContextChanged.run();
    }

    // Shows a section chosen on another screen.
    private void syncSection(String sectionCode) {
        if (updating || sectionCode == null
                || sectionCode.equals(sectionBox.getValue())) {
            return;
        }
        updating = true;
        sectionBox.setValue(sectionCode);
        updating = false;
        onContextChanged.run();
    }

    // Shows a module chosen on another screen, with its sections.
    private void syncModule(String moduleCode) {
        if (updating || moduleCode == null
                || moduleCode.equals(moduleBox.getValue())) {
            return;
        }
        updating = true;
        moduleBox.setValue(moduleCode);
        List<String> sections = backend.getSectionCodes(moduleCode);
        sectionBox.setItems(FXCollections.observableArrayList(sections));
        String section = state.sectionCodeProperty().get();
        if (!sections.contains(section) && !sections.isEmpty()) {
            state.sectionCodeProperty().set(sections.get(0));
        }
        sectionBox.setValue(state.sectionCodeProperty().get());
        updating = false;
        onContextChanged.run();
    }

    // Counts the graded students of a run.
    private int countGraded(GradingRun run) {
        int graded = 0;
        for (Submission submission : run.getSubmissions()) {
            if (ResultState.hasTotal(submission)) {
                graded++;
            }
        }
        return graded;
    }
}
