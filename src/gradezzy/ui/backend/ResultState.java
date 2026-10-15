/*
 * File: ResultState.java
 *
 * Purpose: The one state shown for a student's result, so ungraded, zero,
 * failed, flagged and completed results always look different.
 *
 * Where it fits: The Results, Overview, Statistics and Student Evidence
 * screens show it. It is worked out from the submission; it is never
 * stored.
 *
 * AI use: drafted with Claude Code, reviewed by Jun Sheng.
 */
package gradezzy.ui.backend;

import gradezzy.model.Anomaly;
import gradezzy.model.AnomalyType;
import gradezzy.model.QuestionResult;
import gradezzy.model.Submission;
import gradezzy.model.SubmissionStatus;

/**
 * The state shown for one student's result.
 *
 * <p>Tier: MUST. Owner: Damien.
 */
public enum ResultState {

    /** Not graded yet, or grading was cancelled before it. */
    UNGRADED("Ungraded"),

    /** The pipeline stopped on this submission. */
    COULD_NOT_GRADE("Could not grade"),

    /** At least one question has a compile error anomaly. */
    COMPILE_ERROR("Compile error"),

    /** At least one tester was stopped at the time limit. */
    TIMED_OUT("Timed out"),

    /** Graded, with flags or repairs that wait for a decision. */
    NEEDS_REVIEW("Needs review"),

    /** Graded, and every question scored zero. */
    ZERO("Scored zero"),

    /** Graded with nothing left to review. */
    COMPLETED("Completed");

    // The words shown for this state
    private final String label;

    // Creates a state constant.
    ResultState(String label) {
        this.label = label;
    }

    /**
     * Returns the words shown for this state.
     *
     * @return the label
     */
    public String getLabel() {
        return label;
    }

    /**
     * Works out the state of one submission.
     *
     * @param submission the submission
     * @param waitingForReview whether a flag or repair of the submission
     *        still waits for the instructor
     * @return the state to show
     */
    public static ResultState of(Submission submission,
            boolean waitingForReview) {
        SubmissionStatus status = submission.getStatus();

        if (status == SubmissionStatus.FAILED) {
            return COULD_NOT_GRADE;
        }
        if (status != SubmissionStatus.GRADED
                || submission.getQuestionResults().isEmpty()) {
            return UNGRADED;
        }
        if (hasAnomaly(submission, AnomalyType.COMPILE_ERROR)) {
            return COMPILE_ERROR;
        }
        if (anyTimedOut(submission)) {
            return TIMED_OUT;
        }
        if (waitingForReview) {
            return NEEDS_REVIEW;
        }
        if (submission.getTotalScore() == 0.0) {
            return ZERO;
        }
        return COMPLETED;
    }

    /**
     * Works out the state of one submission, asking the backend which of
     * its anomalies still wait for the instructor.
     *
     * @param submission the submission
     * @param backend the backend that holds the review decisions
     * @return the state to show
     */
    public static ResultState of(Submission submission,
            DashboardBackend backend) {
        boolean waiting = false;
        for (Anomaly anomaly : submission.getAnomalies()) {
            if (backend.needsReview(anomaly)) {
                waiting = true;
            }
        }
        return of(submission, waiting);
    }

    /**
     * Says whether a submission has a total that can be compared with
     * other students.
     *
     * @param submission the submission
     * @return true when the submission was graded
     */
    public static boolean hasTotal(Submission submission) {
        return submission.getStatus() == SubmissionStatus.GRADED
                && !submission.getQuestionResults().isEmpty();
    }

    // Says whether the submission has an anomaly of one type. A compile
    // error is read from the anomaly, because a missing file also leaves
    // a question uncompiled without being a compile error.
    private static boolean hasAnomaly(Submission submission,
            AnomalyType type) {
        for (Anomaly anomaly : submission.getAnomalies()) {
            if (anomaly.getType() == type) {
                return true;
            }
        }
        return false;
    }

    // Says whether any tester of the submission timed out.
    private static boolean anyTimedOut(Submission submission) {
        for (QuestionResult result : submission.getQuestionResults()) {
            if (result.isTimedOut()) {
                return true;
            }
        }
        return false;
    }
}
