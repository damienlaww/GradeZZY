/*
 * File: AnomalyInspector.java
 *
 * Purpose: Fills the inspector of the Review Anomalies screen for the
 * focused flag: what it says, its severity and decision, what the
 * instructor can do, and for validation findings the state of the
 * repair. It also enables the inspector's buttons for that flag.
 *
 * Where it fits: The Review Anomalies screen creates one in onAttach,
 * gives it the inspector's buttons, and calls show whenever the focused
 * row changes. The buttons' actions stay on the screen's controller.
 *
 * AI use: drafted with Claude Code, reviewed by Jun Sheng.
 */
package gradezzy.ui.view;

import javafx.scene.control.Button;
import javafx.scene.layout.FlowPane;
import javafx.scene.layout.HBox;
import javafx.scene.layout.VBox;

import gradezzy.model.Anomaly;
import gradezzy.model.AnomalyType;
import gradezzy.ui.backend.ReviewDecision;
import gradezzy.ui.backend.ReviewRules;
import gradezzy.ui.component.StatusBadge;

/**
 * The flag inspector.
 *
 * <p>Tier: SHOULD. Owner: Damien.
 */
public final class AnomalyInspector {

    // The title, student, message and suggested action
    private final ReviewInspector common;

    // The severity and decision badges
    private final FlowPane badgeRow;

    // The repair section, shown only for validation findings
    private final VBox repairBox;

    // Holds the repair state badge
    private final HBox repairHolder;

    // The students of the run
    private final ReviewStudents students;

    // Knows each flag's decision and whether it can be undone
    private final ReviewDecisions decisions;

    // Mark Reviewed, set by useButtons
    private Button markButton = null;

    // Reject Flag, set by useButtons
    private Button rejectButton = null;

    // Undo Decision, set by useButtons
    private Button undoButton = null;

    // Open Student Evidence, set by useButtons
    private Button openButton = null;

    // Compare in Similarity Review, set by useButtons
    private Button compareButton = null;

    // Open Validation, set by useButtons
    private Button validationButton = null;

    /**
     * Creates the inspector from its nodes.
     *
     * @param common the title, student, message and suggested action
     * @param badgeRow the severity and decision badges
     * @param repairBox the repair section
     * @param repairHolder holds the repair state badge
     * @param students the students of the run
     * @param decisions knows each flag's decision
     */
    public AnomalyInspector(ReviewInspector common, FlowPane badgeRow,
            VBox repairBox, HBox repairHolder, ReviewStudents students,
            ReviewDecisions decisions) {
        this.common = common;
        this.badgeRow = badgeRow;
        this.repairBox = repairBox;
        this.repairHolder = repairHolder;
        this.students = students;
        this.decisions = decisions;
    }

    /**
     * Gives the inspector the buttons it enables for the focused flag.
     *
     * @param mark Mark Reviewed
     * @param reject Reject Flag
     * @param undo Undo Decision
     * @param open Open Student Evidence
     * @param compare Compare in Similarity Review, shown for similar code
     * @param validation Open Validation, shown for validation findings
     */
    public void useButtons(Button mark, Button reject, Button undo,
            Button open, Button compare, Button validation) {
        markButton = mark;
        rejectButton = reject;
        undoButton = undo;
        openButton = open;
        compareButton = compare;
        validationButton = validation;
    }

    /**
     * Shows one flag and enables the buttons that apply to it, or shows a
     * hint and disables them when no flag is focused.
     *
     * @param anomaly the focused flag, or null
     */
    public void show(Anomaly anomaly) {
        badgeRow.getChildren().clear();
        repairHolder.getChildren().clear();
        if (anomaly == null) {
            common.showNothing("No flag selected", "Select a row to read "
                    + "the whole message and decide what to do with the "
                    + "flag.");
            markButton.setDisable(true);
            rejectButton.setDisable(true);
            undoButton.setDisable(true);
            openButton.setDisable(true);
            ReviewInspector.setShown(compareButton, false);
            ReviewInspector.setShown(validationButton, false);
            return;
        }

        ReviewDecision decision = decisions.decisionOf(anomaly);
        common.showAnomaly(anomaly, students.get(anomaly.getUsername()));
        badgeRow.getChildren().addAll(
                StatusBadge.forSeverity(anomaly.getSeverity()),
                StatusBadge.forDecision(decision));
        boolean validation = ReviewRules.isValidationFinding(
                anomaly.getType());
        ReviewInspector.setShown(repairBox, validation);
        if (validation) {
            repairHolder.getChildren().add(StatusBadge.forFix(
                    anomaly.getFixStatus()));
        }

        markButton.setDisable(decision == ReviewDecision.REVIEWED);
        rejectButton.setDisable(decision == ReviewDecision.REJECTED);
        undoButton.setDisable(!decisions.canUndo(anomaly));
        openButton.setDisable(false);
        ReviewInspector.setShown(compareButton,
                anomaly.getType() == AnomalyType.SIMILAR_CODE);
        ReviewInspector.setShown(validationButton, validation);
    }
}
