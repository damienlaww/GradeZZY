/*
 * File: RepairInspector.java
 *
 * Purpose: Fills the inspector of the Validation screen for the focused
 * finding: what it says, the state of its repair, and what the student
 * submitted next to the change proposed for the working copy. It also
 * enables the inspector's buttons for that finding.
 *
 * Where it fits: The Validation screen creates one in onAttach, gives it
 * the inspector's buttons, and calls show whenever the focused row
 * changes. Findings with no automatic repair explain that GradeZZY never
 * writes a missing answer. The buttons' actions stay on the controller.
 *
 * AI use: drafted with Claude Code, reviewed by Jun Sheng.
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
 *
 * <p>Tier: SHOULD. Owner: Damien.
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
     * Shows one finding and enables the buttons that apply to it, or
     * shows a hint and disables them when no finding is focused.
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
