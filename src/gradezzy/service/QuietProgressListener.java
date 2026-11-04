/*
 * File: QuietProgressListener.java
 *
 * Purpose: A progress listener that ignores every event.
 *
 * Where it fits: Used when one student is graded again after an Undo.
 * The screen waits for the result instead of following each step, so
 * there is nobody to tell.
 */
package gradezzy.service;

import gradezzy.grading.ProgressListener;
import gradezzy.model.GradingRun;
import gradezzy.model.Submission;

/**
 * A progress listener that ignores every event.
 *
 * <p>Tier: SHOULD. Owner: Jing Yuan.
 */
public class QuietProgressListener implements ProgressListener {

    /**
     * Creates a listener that ignores every event.
     */
    public QuietProgressListener() {
        // nothing to set up
    }

    /**
     * Ignores the start of a run.
     *
     * @param totalSubmissions the number of submissions
     */
    @Override
    public void onRunStarted(int totalSubmissions) {
        // nobody is following the progress
    }

    /**
     * Ignores the start of a submission.
     *
     * @param submission the submission
     */
    @Override
    public void onSubmissionStarted(Submission submission) {
        // nobody is following the progress
    }

    /**
     * Ignores the start of a step.
     *
     * @param submission the submission
     * @param stageName the name of the step
     */
    @Override
    public void onStageStarted(Submission submission, String stageName) {
        // nobody is following the progress
    }

    /**
     * Ignores the end of a submission.
     *
     * @param submission the submission
     */
    @Override
    public void onSubmissionFinished(Submission submission) {
        // nobody is following the progress
    }

    /**
     * Ignores the end of a run.
     *
     * @param gradingRun the run
     */
    @Override
    public void onRunFinished(GradingRun gradingRun) {
        // nobody is following the progress
    }
}
