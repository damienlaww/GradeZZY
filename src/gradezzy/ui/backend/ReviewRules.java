/*
 * File: ReviewRules.java
 *
 * Purpose: The few rules both backends share about when an anomaly
 * still waits for the instructor and which anomalies are validation
 * findings.
 *
 * Where it fits: Used by LiveBackend and DemoBackend so both modes
 * decide "needs review" the same way.
 *
 * AI use: drafted with Claude Code, reviewed by Jun Sheng.
 */
package gradezzy.ui.backend;

import gradezzy.model.Anomaly;
import gradezzy.model.AnomalyType;
import gradezzy.model.FixStatus;

/**
 * Shared review rules.
 *
 * <p>Tier: SHOULD. Owner: Damien.
 */
public final class ReviewRules {

    // Utility class, never instantiated.
    private ReviewRules() {
        // nothing to set up
    }

    /**
     * Says whether the instructor has already settled an anomaly's
     * repair by approving or undoing it.
     *
     * @param anomaly the anomaly
     * @return true when the repair was approved or undone
     */
    public static boolean isRepairSettled(Anomaly anomaly) {
        FixStatus status = anomaly.getFixStatus();
        return status == FixStatus.APPROVED || status == FixStatus.UNDONE;
    }

    /**
     * Says whether an anomaly still waits for the instructor.
     *
     * @param anomaly the anomaly
     * @param decision the review decision about it
     * @return true when the flag is open and its repair is not settled
     */
    public static boolean needsReview(Anomaly anomaly,
            ReviewDecision decision) {
        return decision == ReviewDecision.OPEN && !isRepairSettled(anomaly);
    }

    /**
     * Says whether an anomaly type comes from checking the structure,
     * names and headers of a submission.
     *
     * @param type the anomaly type
     * @return true for structure, file and header findings
     */
    public static boolean isValidationFinding(AnomalyType type) {
        return type == AnomalyType.MISSING_WRAPPER_FOLDER
                || type == AnomalyType.WRONG_FOLDER_NAME
                || type == AnomalyType.MISSING_QUESTION_FOLDER
                || type == AnomalyType.MISSING_FILE
                || type == AnomalyType.WRONG_FILE_NAME
                || type == AnomalyType.MISSING_HEADER_NAME;
    }
}
