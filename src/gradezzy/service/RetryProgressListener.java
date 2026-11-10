/*
 * File: RetryProgressListener.java
 *
 * Purpose: Passes progress events on, but reports the whole run when a
 * retry finishes.
 *
 * Where it fits: GradingService.retryFailed runs the pipeline on a
 * temporary run that holds only the retried submissions. This listener
 * makes sure the screen is given back the run it passed in, so a second
 * Retry still finds the run GradingService remembers.
 */
package gradezzy.service;

import gradezzy.grading.ProgressListener;
import gradezzy.model.GradingRun;
import gradezzy.model.Submission;

/**
 * Passes progress events on, but reports the whole run when a retry
 * finishes.
 *
 * <p>Tier: SHOULD. Owner: Jing Yuan.
 */
class RetryProgressListener implements ProgressListener {

    // The listener the screen or console gave
    private final ProgressListener listener;

    // The run being retried, with every submission in it
    private final GradingRun wholeRun;

    /**
     * Creates a listener that reports the whole run at the end.
     *
     * @param listener the listener the screen or console gave
     * @param wholeRun the run being retried
     */
    RetryProgressListener(ProgressListener listener, GradingRun wholeRun) {
        this.listener = listener;
        this.wholeRun = wholeRun;
    }

    /**
     * Passes on the number of submissions being retried.
     *
     * <p>Tier: SHOULD.
     *
     * @param totalSubmissions the number of submissions being retried
     */
    @Override
    public void onRunStarted(int totalSubmissions) {
        listener.onRunStarted(totalSubmissions);
    }

    /**
     * Passes on the start of a submission.
     *
     * <p>Tier: SHOULD.
     *
     * @param submission the submission
     */
    @Override
    public void onSubmissionStarted(Submission submission) {
        listener.onSubmissionStarted(submission);
    }

    /**
     * Passes on the start of a stage.
     *
     * <p>Tier: SHOULD.
     *
     * @param submission the submission
     * @param stageName the stage name
     */
    @Override
    public void onStageStarted(Submission submission, String stageName) {
        listener.onStageStarted(submission, stageName);
    }

    /**
     * Passes on the end of a submission.
     *
     * <p>Tier: SHOULD.
     *
     * @param submission the submission
     */
    @Override
    public void onSubmissionFinished(Submission submission) {
        listener.onSubmissionFinished(submission);
    }

    /**
     * Reports the whole run instead of the temporary one.
     *
     * <p>Tier: SHOULD.
     *
     * @param gradingRun the temporary run, not passed on
     */
    @Override
    public void onRunFinished(GradingRun gradingRun) {
        listener.onRunFinished(wholeRun);
    }
}
