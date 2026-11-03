/*
 * File: ReviewRules.java
 *
 * Purpose: The review rules that both backends share.
 *
 * Where it fits: Used by LiveBackend and DemoBackend so both modes
 * decide "needs review" the same way.
 */
package gradezzy.ui.backend;

import gradezzy.model.Anomaly;
import gradezzy.model.AnomalyType;
import gradezzy.model.FixStatus;

/**
 * Shared review rules.
 */
public final class ReviewRules {

    // Utility class, never instantiated.
    private ReviewRules() {
        // nothing to set up
    }

    /**
     * Says whether an anomaly's repair was approved or undone.
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
     * Says whether an anomaly type is a validation finding.
     *
     * @param type the anomaly type
     * @return true for structure, file, header and encoding findings
     */
    public static boolean isValidationFinding(AnomalyType type) {
        return type == AnomalyType.MISSING_WRAPPER_FOLDER
                || type == AnomalyType.WRONG_FOLDER_NAME
                || type == AnomalyType.MISSING_QUESTION_FOLDER
                || type == AnomalyType.MISSING_FILE
                || type == AnomalyType.WRONG_FILE_NAME
                || type == AnomalyType.MISSING_HEADER_NAME
                || type == AnomalyType.WRONG_ENCODING;
    }
}
