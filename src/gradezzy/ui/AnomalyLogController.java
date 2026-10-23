/*
 * File: AnomalyLogController.java
 *
 * Purpose: Controller of the Review Anomalies screen.
 *
 * Where it fits: Lists the flags of the section in the context bar for the
 * instructor to review, reject or undo. A flag never changes a mark.
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
import javafx.scene.control.TextField;
import javafx.scene.control.ToggleButton;
import javafx.scene.layout.FlowPane;
import javafx.scene.layout.HBox;
import javafx.scene.layout.VBox;

import gradezzy.model.Anomaly;
import gradezzy.model.AnomalyType;
import gradezzy.model.GradingRun;
import gradezzy.model.Severity;
import gradezzy.ui.backend.ReviewDecision;
import gradezzy.ui.backend.ReviewRules;
import gradezzy.ui.component.Icon;
import gradezzy.ui.component.Responsive;
import gradezzy.ui.view.AnomalyFilters;
import gradezzy.ui.view.AnomalyInspector;
import gradezzy.ui.view.AnomalyTable;
import gradezzy.ui.view.ReviewDecisions;
import gradezzy.ui.view.ReviewInspector;
import gradezzy.ui.view.ReviewSelection;
import gradezzy.ui.view.ReviewStudents;

/**
 * Controller of the Review Anomalies screen.
 */
public class AnomalyLogController extends BaseController {

    @FXML
    private Label statusLabel;

    @FXML
    private Button rejectButton;

    @FXML
    private Button approveButton;

    @FXML
    private TextField searchField;

    @FXML
    private Label typeLabel;

    @FXML
    private ComboBox<AnomalyType> typeFilter;

    @FXML
    private Label severityLabel;

    @FXML
    private ComboBox<Severity> severityFilter;

    @FXML
    private Label decisionLabel;

    @FXML
    private ComboBox<ReviewDecision> decisionFilter;

    @FXML
    private Label sortLabel;

    @FXML
    private ComboBox<SortOrder> sortBox;

    @FXML
    private Button selectAllButton;

    @FXML
    private Button clearSelectionButton;

    @FXML
    private Button undoSelectedButton;

    @FXML
    private ToggleButton inspectorToggle;

    @FXML
    private TableView<Anomaly> table;

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
    private FlowPane badgeRow;

    @FXML
    private Label messageLabel;

    @FXML
    private Label actionLabel;

    @FXML
    private VBox repairBox;

    @FXML
    private HBox repairHolder;

    @FXML
    private Button markButton;

    @FXML
    private Button rejectFlagButton;

    @FXML
    private Button undoButton;

    @FXML
    private Button openButton;

    @FXML
    private Button compareButton;

    @FXML
    private Button validationButton;

    // Every flag of the run, before filtering
    private final List<Anomaly> anomalies = new ArrayList<>();

    // The flags shown, after search, filters and sort
    private final List<Anomaly> shown = new ArrayList<>();

    // The students of the run
    private final ReviewStudents students = new ReviewStudents();

    // The columns and empty states of the table
    private AnomalyTable flagTable = null;

    // The remembered selection of the table
    private ReviewSelection<Anomaly> selection = null;

    // The search, filters and sort
    private AnomalyFilters filters = null;

    // The inspector of the focused flag
    private AnomalyInspector flagInspector = null;

    // Records decisions with Undo and reports them once
    private ReviewDecisions decisions = null;

    /**
     * Creates a new anomaly log controller.
     */
    public AnomalyLogController() {
        // nothing to set up
    }

    /**
     * Called by JavaFX after the FXML file is loaded.
     */
    @FXML
    public void initialize() {
        typeLabel.setLabelFor(typeFilter);
        severityLabel.setLabelFor(severityFilter);
        decisionLabel.setLabelFor(decisionFilter);
        sortLabel.setLabelFor(sortBox);
        inspectorToggle.setGraphic(Icon.INSPECTOR.create());
    }

    /**
     * Builds the parts that depend on the backend and the state.
     */
    @Override
    public void onAttach() {
        selection = new ReviewSelection<>(table,
                getState().getSelectedAnomalies(), Anomaly::getId,
                this::selectionChanged, this::focusChanged);
        flagTable = new AnomalyTable(table, students, getBackend(),
                getShell());
        filters = new AnomalyFilters(searchField, typeFilter,
                severityFilter, decisionFilter, sortBox, getState(),
                students);
        decisions = new ReviewDecisions(getBackend(), getState(),
                getShell());
        flagInspector = new AnomalyInspector(new ReviewInspector(titleLabel,
                hintLabel, detailBox, nameLabel, usernameLabel, messageLabel,
                actionLabel), badgeRow, repairBox, repairHolder, students,
                decisions);
        flagInspector.useButtons(markButton, rejectFlagButton, undoButton,
                openButton, compareButton, validationButton);
        Responsive.bindInspector(inspector, inspectorToggle,
                getState().compactProperty(),
                getState().inspectorRequestedProperty());
        filters.listen(this::refresh);
    }

    /**
     * Restores the search, filters, sort and selection, and reloads the
     * flags each time it is shown.
     */
    @Override
    public void onShow() {
        handleFilterChanged();
    }

    /**
     * Moves keyboard focus to the search field.
     *
     * @return true, because this screen has a search field
     */
    @Override
    public boolean focusSearch() {
        filters.focusSearch();
        return true;
    }

    /**
     * Marks the selected flags reviewed. No mark changes.
     */
    @FXML
    public void handleApprove() {
        if (decisions.decideSelected(selection.getSelected(),
                ReviewDecision.REVIEWED)) {
            refresh();
        }
    }

    /**
     * Rejects the selected flags. No mark changes.
     */
    @FXML
    public void handleReject() {
        if (decisions.decideSelected(selection.getSelected(),
                ReviewDecision.REJECTED)) {
            refresh();
        }
    }

    /**
     * Undoes this session's latest decision on the selected flags.
     */
    @FXML
    public void handleUndo() {
        if (decisions.undoSelected(selection.getSelected())) {
            refresh();
        }
    }

    /**
     * Selects every flag shown.
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
     * Applies the search, filters and sort held in UiState to the table.
     */
    @FXML
    public void handleFilterChanged() {
        filters.showState();
        refresh();
    }

    /**
     * Clears the search and every filter. The sort order is kept.
     */
    public void clearFilters() {
        filters.clear();
        handleFilterChanged();
    }

    /**
     * Marks the flag in the inspector reviewed.
     */
    @FXML
    public void handleMarkFocused() {
        if (decisions.decideOne(selection.getFocused(),
                ReviewDecision.REVIEWED, "Flag reviewed")) {
            refresh();
        }
    }

    /**
     * Rejects the flag in the inspector.
     */
    @FXML
    public void handleRejectFocused() {
        if (decisions.decideOne(selection.getFocused(),
                ReviewDecision.REJECTED, "Flag rejected")) {
            refresh();
        }
    }

    /**
     * Restores the decision the flag in the inspector had before.
     */
    @FXML
    public void handleUndoFocused() {
        if (decisions.undoOne(selection.getFocused(),
                "Flag decision undone")) {
            refresh();
        }
    }

    /**
     * Opens the evidence of the student of the flag in the inspector.
     */
    @FXML
    public void handleOpenStudent() {
        Anomaly anomaly = selection.getFocused();
        if (anomaly == null) {
            showError("Select a flag first.");
            return;
        }
        getState().setSelectedUsername(anomaly.getUsername());
        getState().setEvidenceTab("");
        getState().setEvidenceQuestionId("");
        getShell().navigate(Screen.STUDENT_REPORT);
    }

    /**
     * Opens Similarity Review for the student of the focused flag.
     */
    @FXML
    public void handleCompare() {
        Anomaly anomaly = selection.getFocused();
        if (anomaly != null) {
            getState().setSelectedUsername(anomaly.getUsername());
        }
        getShell().navigate(Screen.PLAGIARISM);
    }

    /**
     * Opens the Validation screen, selecting the focused flag if it is a
     * validation finding.
     */
    @FXML
    public void handleOpenValidation() {
        Anomaly anomaly = selection.getFocused();
        if (anomaly != null
                && ReviewRules.isValidationFinding(anomaly.getType())) {
            getState().getSelectedRepairs().clear();
            getState().getSelectedRepairs().add(anomaly.getId());
        }
        getShell().navigate(Screen.VALIDATION);
    }

    /**
     * Returns the flags shown. Used by the self-test.
     *
     * @return the flags in table order
     */
    public List<Anomaly> getShownAnomalies() {
        return new ArrayList<>(shown);
    }

    /**
     * Selects flags by anomaly id. Used by the self-test.
     *
     * @param anomalyIds the flags to select
     */
    public void selectAnomalies(Collection<String> anomalyIds) {
        selection.select(anomalyIds);
        selectionChanged();
    }

    // Returns the run of the section in the context bar.
    private GradingRun currentRun() {
        return getBackend().getRun(getState().getSection());
    }

    // Reloads the flags, applies the filters and redraws the screen.
    private void refresh() {
        GradingRun run = currentRun();
        anomalies.clear();
        anomalies.addAll(getBackend().listAnomalies(run));
        students.remember(run);

        shown.clear();
        shown.addAll(filters.apply(anomalies, decisions::decisionOf));

        selection.show(shown, flagTable.emptyState(run,
                !anomalies.isEmpty(), getState().getSection().getLabel(),
                () -> clearFilters()));
        selectAllButton.setDisable(shown.isEmpty());
        selectionChanged();
    }

    // Updates the status line and the inspector.
    private void selectionChanged() {
        refreshStatus();
        focusChanged();
    }

    // Shows the focused flag in the inspector.
    private void focusChanged() {
        flagInspector.show(selection.getFocused());
    }

    // Updates the status line and the selection buttons.
    private void refreshStatus() {
        List<Anomaly> selected = selection.getSelected();
        boolean none = selected.isEmpty();
        clearSelectionButton.setDisable(none);
        approveButton.setDisable(none);
        rejectButton.setDisable(none);
        undoSelectedButton.setDisable(decisions.undoable(selected)
                .isEmpty());

        statusLabel.setText(flagTable.statusText(currentRun(), anomalies,
                shown.size(), selected.size(),
                getState().getSection().getLabel()));
    }
}
