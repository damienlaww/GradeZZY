/*
 * File: SubmissionStatus.java
 *
 * Purpose: Where a submission is in the grading process.
 *
 * Where it fits: Set by the pipeline and shown in the progress and results
 * screens.
 */
package gradezzy.model;

/**
 * Where a submission is in the grading process.
 *
 * <p>Tier: MUST. Owner: Jing Yuan.
 */
public enum SubmissionStatus {
    /**
     * Waiting to be graded.
     */
    PENDING,

    /**
     * Being graded right now.
     */
    RUNNING,

    /**
     * Finished; it may still have anomalies.
     */
    GRADED,

    /**
     * A stage failed, so grading of it stopped early.
     */
    FAILED,

    /**
     * Skipped because the instructor cancelled the run.
     */
    CANCELLED
}
