/*
 * File: ValidationController.java
 *
 * Purpose: Controller of the Validation screen.
 *
 * Where it fits: Reached from Submissions and Review Anomalies. Lists the
 * validation findings of the run for the section in the context bar,
 * each with what the student submitted and the change proposed for the
 * working copy, and lets the instructor approve, reject or undo the
 * repairs, one at a time or several at once. Only working copies change;
 * the original ZIP files never do. The selection is kept in UiState, so
 * it comes back after navigation or a theme switch. The table, filters,
 * inspector and repair actions are parts in gradezzy.ui.view; this
 * controller wires them together.
 *
 * AI use: drafted with Claude Code, reviewed by Jun Sheng.
 */
package gradezzy.ui;

import java.util.ArrayList;
import java.util.Collection;
import java.util.List;

import javafx.fxml.FXML;
import javafx.scene.control.Button;
import javafx.scene.control.ComboBox;
import javafx.scene.control.Label;
import javafx.scene.control.TableView;
import javafx.scene.control.ToggleButton;
import javafx.scene.layout.HBox;
import javafx.scene.layout.VBox;

import gradezzy.model.FixStatus;
import gradezzy.model.GradingRun;
import gradezzy.ui.backend.Capability;
import gradezzy.ui.backend.RepairItem;
import gradezzy.ui.component.Icon;
import gradezzy.ui.component.Responsive;
import gradezzy.ui.view.RepairAction;
import gradezzy.ui.view.RepairDecisions;
import gradezzy.ui.view.RepairInspector;
import gradezzy.ui.view.ReviewDecisions;
import gradezzy.ui.view.ReviewInspector;
import gradezzy.ui.view.ReviewSelection;
import gradezzy.ui.view.ReviewStudents;
import gradezzy.ui.view.ValidationFilters;
import gradezzy.ui.view.ValidationTable;

/**
 * Controller of the Validation screen.
 *
 * <p>Tier: SHOULD. Owner: Damien.
 */
public class ValidationController extends BaseController {

    @FXML
    private Label statusLabel;

    @FXML
    private Button undoAllButton;

    @FXML
    private Button rejectButton;

    @FXML
    private Button approveButton;

    @FXML
    private HBox valuesNote;

    @FXML
    private Label studentLabel;

    @FXML
    private ComboBox<String> studentFilter;

    @FXML
    private Label stateLabel;

    @FXML
    private ComboBox<FixStatus> stateFilter;

    @FXML
    private Button selectAllButton;

    @FXML
    private Button clearSelectionButton;

    @FXML
    private ToggleButton inspectorToggle;

    @FXML
    private TableView<RepairItem> table;

    @FXML
    private VBox inspector;

    @FXML
    private Label titleLabel;

    @FXML
    private Label hintLabel;

    @FXML
    private VBox detailBox;

    @FXML
    private Label nameLabel;

    @FXML
    private Label usernameLabel;

    @FXML
    private HBox stateHolder;

    @FXML
    private Label messageLabel;

    @FXML
    private Label actionLabel;

    @FXML
    private Label originalLabel;

    @FXML
    private Label proposedLabel;

    @FXML
    private HBox blockedBanner;

    @FXML
    private Button approveOneButton;

    @FXML
    private Button rejectOneButton;

    @FXML
    private Button undoOneButton;

    @FXML
    private Button openButton;

    // Every finding of the run, before filtering
    private final List<RepairItem> repairs = new ArrayList<>();

    // The findings shown, after filtering
    private final List<RepairItem> shown = new ArrayList<>();

    // The students of the run
    private final ReviewStudents students = new ReviewStudents();

    // The student and state filters
    private ValidationFilters filters = null;

    // The columns and empty states of the table
    private ValidationTable findingTable = null;

    // The remembered selection of the table
    private ReviewSelection<RepairItem> selection = null;

    // The inspector of the focused finding
    private RepairInspector repairInspector = null;

    // Applies repair decisions and reports them once
    private RepairDecisions repairDecisions = null;

    /**
     * Creates a new validation controller.
     */
    public ValidationController() {
        // nothing to set up
    }

    /**
     * Called by JavaFX after the FXML file is loaded.
     */
    @FXML
    public void initialize() {
        studentLabel.setLabelFor(studentFilter);
        stateLabel.setLabelFor(stateFilter);
        inspectorToggle.setGraphic(Icon.INSPECTOR.create());
        filters = new ValidationFilters(studentFilter, stateFilter);
    }

    /**
     * Builds the parts that depend on the backend and the state.
     */
    @Override
    public void onAttach() {
        selection = new ReviewSelection<>(table,
                getState().getSelectedRepairs(),
                repair -> repair.getAnomaly().getId(),
                this::selectionChanged, this::focusChanged);
        findingTable = new ValidationTable(table, students, getShell());
        repairInspector = new RepairInspector(new ReviewInspector(titleLabel,
                hintLabel, detailBox, nameLabel, usernameLabel, messageLabel,
                actionLabel), stateHolder, blockedBanner, originalLabel,
                proposedLabel, students);
        repairInspector.useButtons(approveOneButton, rejectOneButton,
                undoOneButton, openButton);
        repairDecisions = new RepairDecisions(getBackend(), getShell(),
                new ReviewDecisions(getBackend(), getState(), getShell()));
        Responsive.bindInspector(inspector, inspectorToggle,
                getState().compactProperty(),
                getState().inspectorRequestedProperty());
        filters.listen(this::refresh);
    }

    /**
     * Reloads the findings and restores the selection each time the
     * screen is shown.
     */
    @Override
    public void onShow() {
        refresh();
    }

    /**
     * Approves the repairs of the selected findings that wait for review.
     */
    @FXML
    public void handleApproveSelected() {
        repairDecisions.decideSelected(selection.getSelected(),
                RepairAction.APPROVE, this::refresh);
    }

    /**
     * Rejects the repairs of the selected findings, after a confirmation.
     * Rejecting undoes the repair on the working copy.
     */
    @FXML
    public void handleRejectSelected() {
        repairDecisions.decideSelected(selection.getSelected(),
                RepairAction.REJECT, this::refresh);
    }

    /**
     * Returns the decided repairs among the selected findings to Applied,
     * waiting for review.
     */
    public void handleUndoSelected() {
        repairDecisions.decideSelected(selection.getSelected(),
                RepairAction.UNDO, this::refresh);
    }

    /**
     * Returns every decided repair of the run to Applied, waiting for
     * review, after a confirmation.
     */
    @FXML
    public void handleUndoAll() {
        repairDecisions.undoAll(repairs, this::refresh);
    }

    /**
     * Approves the repair of the finding in the inspector.
     */
    @FXML
    public void handleApproveFocused() {
        repairDecisions.decideFocused(selection.getFocusedList(),
                RepairAction.APPROVE, this::refresh);
    }

    /**
     * Rejects the repair of the finding in the inspector, after a
     * confirmation.
     */
    @FXML
    public void handleRejectFocused() {
        repairDecisions.decideFocused(selection.getFocusedList(),
                RepairAction.REJECT, this::refresh);
    }

    /**
     * Returns the repair of the finding in the inspector to Applied.
     */
    @FXML
    public void handleUndoFocused() {
        repairDecisions.decideFocused(selection.getFocusedList(),
                RepairAction.UNDO, this::refresh);
    }

    /**
     * Opens the evidence of the student of the finding in the inspector.
     */
    @FXML
    public void handleOpenStudent() {
        RepairItem repair = selection.getFocused();
        if (repair == null) {
            showError("Select a finding first.");
            return;
        }
        getState().setSelectedUsername(repair.getAnomaly().getUsername());
        getState().setEvidenceTab("");
        getState().setEvidenceQuestionId("");
        getShell().navigate(Screen.STUDENT_REPORT);
    }

    /**
     * Opens the Review Anomalies screen.
     */
    @FXML
    public void handleOpenAnomalies() {
        getShell().navigate(Screen.ANOMALY_LOG);
    }

    /**
     * Explains why the original and proposed values are not shown yet.
     */
    @FXML
    public void handleValuesNotConnected() {
        getShell().showNotConnected(Capability.REPAIR_VALUES);
    }

    /**
     * Selects every finding shown.
     */
    @FXML
    public void handleSelectAll() {
        selection.selectAll();
    }

    /**
     * Clears the selection.
     */
    @FXML
    public void handleClearSelection() {
        selection.clear();
        selectionChanged();
    }

    /**
     * Shows every student and every state again.
     */
    public void clearFilters() {
        filters.clear();
        refresh();
    }

    /**
     * Returns the findings shown, after filtering. Used by the self-test.
     *
     * @return the findings in table order
     */
    public List<RepairItem> getShownRepairs() {
        return new ArrayList<>(shown);
    }

    /**
     * Selects findings by anomaly id. Used by the self-test.
     *
     * @param anomalyIds the findings to select
     */
    public void selectRepairs(Collection<String> anomalyIds) {
        selection.select(anomalyIds);
        selectionChanged();
    }

    // Returns the run of the section in the context bar.
    private GradingRun currentRun() {
        return getBackend().getRun(getState().getSection());
    }

    // Reloads the findings, applies the filters and the remembered
    // selection, and redraws the screen.
    private void refresh() {
        GradingRun run = currentRun();
        repairs.clear();
        if (run != null) {
            repairs.addAll(getBackend().listRepairs(run));
        }
        if (students.remember(run)) {
            filters.fillStudents(students.getUsernames());
        }

        shown.clear();
        for (RepairItem repair : repairs) {
            if (filters.matches(repair)) {
                shown.add(repair);
            }
        }
        selection.show(shown, findingTable.emptyState(run,
                !repairs.isEmpty(), getState().getSection().getLabel(),
                () -> clearFilters()));

        boolean noValues = !getBackend().supports(Capability.REPAIR_VALUES)
                && !repairs.isEmpty();
        ReviewInspector.setShown(valuesNote, noValues);
        undoAllButton.setDisable(RepairAction.UNDO.keep(repairs).isEmpty());
        selectAllButton.setDisable(shown.isEmpty());
        selectionChanged();
    }

    // Updates the status line and the inspector after the selection
    // changed.
    private void selectionChanged() {
        refreshStatus();
        focusChanged();
    }

    // Describes what is shown and selected, and enables the buttons.
    private void refreshStatus() {
        List<RepairItem> selected = selection.getSelected();
        clearSelectionButton.setDisable(selected.isEmpty());
        approveButton.setDisable(RepairAction.APPROVE.keep(selected)
                .isEmpty());
        rejectButton.setDisable(RepairAction.REJECT.keep(selected)
                .isEmpty());

        statusLabel.setText(findingTable.statusText(currentRun(), repairs,
                shown.size(), selected.size(),
                getState().getSection().getLabel()));
    }

    // Shows the focused finding in the inspector.
    private void focusChanged() {
        repairInspector.show(selection.getFocused());
    }
}
