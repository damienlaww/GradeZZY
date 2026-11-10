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
 *
 * <p>Tier: SHOULD.
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
     * Says whether a flag may be marked as checked together with others
     * by "Mark all as checked". A flag that means the student was not
     * fully graded, is not on the score sheet or could not be read is
     * left out: it changes what Export does, so it is checked one at a
     * time. Similar code is left out too: it is decided as Looks fine or
     * Keep flagged on the Similar code tab, and a kept flag goes into the
     * score sheet's Remarks.
     *
     * @param type the anomaly type
     * @return true for a review aid such as an empty answer, or a folder
     *         and file finding
     */
    public static boolean canCheckAll(AnomalyType type) {
        return type != AnomalyType.COMPILE_ERROR
                && type != AnomalyType.SIMILAR_CODE
                && type != AnomalyType.TIMEOUT
                && type != AnomalyType.RUNTIME_ERROR
                && type != AnomalyType.UNKNOWN_STUDENT
                && type != AnomalyType.INVALID_ARCHIVE
                && type != AnomalyType.DUPLICATE_SUBMISSION
                && type != AnomalyType.INTERNAL_ERROR;
    }

    /**
     * Says whether a kind of problem is a flag: a suspicion that never
     * changes a mark and that the instructor judges as Looks fine or
     * Keep flagged.
     *
     * @param type the kind
     * @return true for a flag
     */
    public static boolean isFlag(AnomalyType type) {
        return type == AnomalyType.UNTOUCHED_STARTER
                || type == AnomalyType.EMPTY_ANSWER
                || type == AnomalyType.HARD_CODED_ANSWER
                || type == AnomalyType.COPIED_HEADER
                || type == AnomalyType.SIMILAR_CODE
                || type == AnomalyType.DIFFERENT_FROM_MODEL_ANSWER;
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
