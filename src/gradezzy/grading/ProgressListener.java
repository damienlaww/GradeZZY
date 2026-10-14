/*
 * File: ProgressListener.java
 *
 * Purpose: Receives progress events while a run is in progress.
 *
 * Where it fits: Implemented by the console entry point and by the progress
 * screen, so the pipeline never depends on any user interface.
 */
package gradezzy.grading;

import gradezzy.model.GradingRun;
import gradezzy.model.Submission;

/**
 * Receives progress events while a run is in progress.
 *
 * <p>Tier: MUST. Owner: Jing Yuan.
 */
public interface ProgressListener {

    /**
     * Called once before the first submission.
     *
     * @param totalSubmissions the total submissions
     */
    void onRunStarted(int totalSubmissions);

    /**
     * Called when grading of a submission begins.
     *
     * @param submission the submission
     */
    void onSubmissionStarted(Submission submission);

    /**
     * Called when a stage begins for a submission.
     *
     * @param submission the submission
     * @param stageName the stage name
     */
    void onStageStarted(Submission submission, String stageName);

    /**
     * Called when a submission is done, whatever its status.
     *
     * @param submission the submission
     */
    void onSubmissionFinished(Submission submission);

    /**
     * Called once after the last submission.
     *
     * @param gradingRun the grading run
     */
    void onRunFinished(GradingRun gradingRun);
}
