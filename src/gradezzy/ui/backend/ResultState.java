/*
 * File: ResultState.java
 *
 * Purpose: The one state shown for a student's result.
 *
 * Where it fits: Shown on Grade, Review and Statistics. It is worked out
 * from the submission, never stored.
 */
package gradezzy.ui.backend;

import gradezzy.model.Anomaly;
import gradezzy.model.AnomalyType;
import gradezzy.model.QuestionResult;
import gradezzy.model.Severity;
import gradezzy.model.Submission;
import gradezzy.model.SubmissionStatus;

/**
 * The state shown for one student's result.
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
    COMPLETED("Completed"),

    /** On the score sheet, but no file of this student was graded. */
    NO_SUBMISSION("No submission");

    /**
     * The reason stored on a row made for a student with no submission,
     * which marks the row as one.
     */
    public static final String NO_SUBMISSION_REASON =
            "Nothing of this student was graded in this run.";

    /**
     * The start of the reason stored on a row made for a chosen file that
     * grading skipped, which marks the row as one.
     */
    public static final String COULD_NOT_OPEN_PREFIX = "Could not open ";

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
     * Works out a submission's state, asking the backend about reviews.
     *
     * @param submission the submission
     * @param backend the backend with the review decisions
     * @return the state to show
     */
    public static ResultState of(Submission submission,
            DashboardBackend backend) {
        SubmissionStatus status = submission.getStatus();
        if (isNoSubmission(submission)) {
            return NO_SUBMISSION;
        }
        if (status == SubmissionStatus.FAILED) {
            return COULD_NOT_GRADE;
        }
        if (status != SubmissionStatus.GRADED
                || submission.getQuestionResults().isEmpty()) {
            return UNGRADED;
        }
        // A compile error or timeout shows only while its flag is open:
        // once the instructor has checked it, the student is graded
        boolean waiting = false;
        boolean compileOpen = false;
        boolean timeoutOpen = false;
        boolean timeoutFlagged = false;
        for (Anomaly anomaly : submission.getAnomalies()) {
            boolean open = backend.needsReview(anomaly);
            waiting = waiting || open;
            if (anomaly.getType() == AnomalyType.COMPILE_ERROR && open) {
                compileOpen = true;
            } else if (anomaly.getType() == AnomalyType.TIMEOUT) {
                timeoutFlagged = true;
                timeoutOpen = timeoutOpen || open;
            }
        }
        if (compileOpen) {
            return COMPILE_ERROR;
        }
        // A timeout with no flag to check cannot be settled, so it shows
        if (timeoutOpen || (!timeoutFlagged && anyTimedOut(submission))) {
            return TIMED_OUT;
        }
        if (waiting) {
            return NEEDS_REVIEW;
        }
        if (submission.getTotalScore() == 0.0) {
            return ZERO;
        }
        return COMPLETED;
    }

    /**
     * Says whether a row stands for a student on the score sheet with no
     * submission in the run.
     *
     * @param submission the row
     * @return true for a student with no submission
     */
    public static boolean isNoSubmission(Submission submission) {
        return submission.getStatus() == SubmissionStatus.PENDING
                && NO_SUBMISSION_REASON.equals(
                        submission.getIngestFailure());
    }

    /**
     * Says whether a row stands for a chosen file that grading skipped
     * because it could not be opened.
     *
     * @param submission the row
     * @return true for a skipped file
     */
    public static boolean isSkippedFile(Submission submission) {
        String failure = submission.getIngestFailure();
        return submission.getStatus() == SubmissionStatus.FAILED
                && failure != null
                && failure.startsWith(COULD_NOT_OPEN_PREFIX);
    }

    /**
     * Says whether a submission's zip was refused, such as a damaged one.
     *
     * @param submission the submission
     * @return true when the upload has an error about its zip
     */
    public static boolean isRejectedUpload(Submission submission) {
        for (Anomaly anomaly : submission.getAnomalies()) {
            if (anomaly.getType() == AnomalyType.INVALID_ARCHIVE
                    && anomaly.getSeverity() == Severity.ERROR) {
                return true;
            }
        }
        return false;
    }

    /**
     * Says whether a row was made on Review rather than graded: a student
     * with no submission or a skipped file. Such a row has no marks to
     * export and nothing to open.
     *
     * @param submission the row
     * @return true for a row made on Review
     */
    public static boolean isMadeOnReview(Submission submission) {
        return isNoSubmission(submission) || isSkippedFile(submission);
    }

    /**
     * Says whether a submission has a total to compare with others.
     *
     * @param submission the submission
     * @return true when the submission was graded
     */
    public static boolean hasTotal(Submission submission) {
        return submission.getStatus() == SubmissionStatus.GRADED
                && !submission.getQuestionResults().isEmpty();
    }

    // Says whether the submission has an anomaly of one type.
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
