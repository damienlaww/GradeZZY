/*
 * File: RepairInspector.java
 *
 * Purpose: Fills the Validation screen's inspector for the focused finding
 * and enables its buttons.
 *
 * Where it fits: ValidationController creates one in onAttach and calls
 * show when the focused row changes. The button actions stay there.
 */
package gradezzy.ui.view;

import java.util.ArrayList;
import java.util.List;

import javafx.scene.control.Button;
import javafx.scene.control.Label;
import javafx.scene.layout.HBox;

import gradezzy.model.Anomaly;
import gradezzy.ui.backend.RepairItem;
import gradezzy.ui.component.StatusBadge;

/**
 * The finding inspector.
 */
public final class RepairInspector {

    // The title, student, message and suggested action
    private final ReviewInspector common;

    // Holds the repair state badge
    private final HBox stateHolder;

    // Explains that no automatic repair exists
    private final HBox blockedBanner;

    // What the student submitted
    private final Label originalLabel;

    // What the working copy holds after the repair
    private final Label proposedLabel;

    // The students of the run
    private final ReviewStudents students;

    // Approve, set by useButtons
    private Button approveButton = null;

    // Reject, set by useButtons
    private Button rejectButton = null;

    // Undo Decision, set by useButtons
    private Button undoButton = null;

    // Open Student Evidence, set by useButtons
    private Button openButton = null;

    /**
     * Creates the inspector from its nodes.
     *
     * @param common the title, student, message and suggested action
     * @param stateHolder holds the repair state badge
     * @param blockedBanner explains that no automatic repair exists
     * @param originalLabel what the student submitted
     * @param proposedLabel the proposed working copy
     * @param students the students of the run
     */
    public RepairInspector(ReviewInspector common, HBox stateHolder,
            HBox blockedBanner, Label originalLabel, Label proposedLabel,
            ReviewStudents students) {
        this.common = common;
        this.stateHolder = stateHolder;
        this.blockedBanner = blockedBanner;
        this.originalLabel = originalLabel;
        this.proposedLabel = proposedLabel;
        this.students = students;
    }

    /**
     * Gives the inspector the buttons it enables for the focused finding.
     *
     * @param approve Approve
     * @param reject Reject
     * @param undo Undo Decision
     * @param open Open Student Evidence
     */
    public void useButtons(Button approve, Button reject, Button undo,
            Button open) {
        approveButton = approve;
        rejectButton = reject;
        undoButton = undo;
        openButton = open;
    }

    /**
     * Shows one finding and enables its buttons, or shows a hint if null.
     *
     * @param repair the focused finding, or null
     */
    public void show(RepairItem repair) {
        stateHolder.getChildren().clear();
        List<RepairItem> focused = new ArrayList<>();
        if (repair == null) {
            common.showNothing("No finding selected", "Select a row to "
                    + "compare what the student submitted with the "
                    + "proposed working copy.");
        } else {
            Anomaly anomaly = repair.getAnomaly();
            common.showAnomaly(anomaly, students.get(anomaly.getUsername()));
            originalLabel.setText(repair.getOriginalValue());
            proposedLabel.setText(repair.getProposedValue());
            stateHolder.getChildren().add(StatusBadge.forFix(
                    anomaly.getFixStatus()));
            ReviewInspector.setShown(blockedBanner, repair.isBlocked());
            focused.add(repair);
        }

        approveButton.setDisable(RepairAction.APPROVE.keep(focused)
                .isEmpty());
        rejectButton.setDisable(RepairAction.REJECT.keep(focused).isEmpty());
        undoButton.setDisable(RepairAction.UNDO.keep(focused).isEmpty());
        openButton.setDisable(focused.isEmpty());
    }
}
