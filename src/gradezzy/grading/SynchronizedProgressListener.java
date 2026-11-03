/*
 * File: SynchronizedProgressListener.java
 *
 * Purpose: Wraps a progress listener so only one grading worker is inside
 * it at a time.
 *
 * Where it fits: GradingPipeline gives this wrapper, not the window's or
 * console's own listener, to its workers when several submissions are
 * graded at the same time.
 */
package gradezzy.grading;

import gradezzy.model.GradingRun;
import gradezzy.model.Submission;

/**
 * Wraps a progress listener so that only one worker is inside it at a
 * time. This keeps the listener's own counters correct and stops two
 * workers from interleaving the characters of one printed line. The
 * lines of different submissions may still appear mixed together,
 * which is expected when several are graded at once.
 *
 * <p>Tier: MUST. Owner: Jing Yuan.
 */
class SynchronizedProgressListener implements ProgressListener {

    // The listener the window or console gave
    private final ProgressListener delegate;

    /**
     * Creates the wrapper.
     *
     * @param delegate the listener the window or console gave
     */
    SynchronizedProgressListener(ProgressListener delegate) {
        this.delegate = delegate;
    }

    /**
     * Passes the event on while holding this wrapper's lock.
     *
     * @param totalSubmissions the total submissions
     */
    @Override
    public synchronized void onRunStarted(int totalSubmissions) {
        delegate.onRunStarted(totalSubmissions);
    }

    /**
     * Passes the event on while holding this wrapper's lock.
     *
     * @param submission the submission
     */
    @Override
    public synchronized void onSubmissionStarted(Submission submission) {
        delegate.onSubmissionStarted(submission);
    }

    /**
     * Passes the event on while holding this wrapper's lock.
     *
     * @param submission the submission
     * @param stageName the name of the stage
     */
    @Override
    public synchronized void onStageStarted(Submission submission,
            String stageName) {
        delegate.onStageStarted(submission, stageName);
    }

    /**
     * Passes the event on while holding this wrapper's lock.
     *
     * @param submission the submission
     */
    @Override
    public synchronized void onSubmissionFinished(Submission submission) {
        delegate.onSubmissionFinished(submission);
    }

    /**
     * Passes the event on while holding this wrapper's lock.
     *
     * @param gradingRun the run
     */
    @Override
    public synchronized void onRunFinished(GradingRun gradingRun) {
        delegate.onRunFinished(gradingRun);
    }
}
