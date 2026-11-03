/*
 * File: GradingWorker.java
 *
 * Purpose: The job one grading thread runs: grade submissions until the
 * handout has none left.
 *
 * Where it fits: GradingPipeline starts one Thread per worker when several
 * submissions are graded at the same time.
 */
package gradezzy.grading;

import gradezzy.model.Submission;

/**
 * A worker thread that grades submissions until the handout is empty.
 * Each submission is graded by the pipeline's own gradeOne method, so the
 * parallel path does exactly what the in-order path does.
 *
 * <p>Tier: MUST. Owner: Jing Yuan.
 */
class GradingWorker implements Runnable {

    // The pipeline whose stages grade each submission
    private final GradingPipeline pipeline;

    // Where the worker gets its next submission
    private final SubmissionHandout handout;

    // The shared read-only information the stages need
    private final GradingContext context;

    // The listener, already made safe for several threads
    private final ProgressListener listener;

    /**
     * Creates a worker.
     *
     * @param pipeline the pipeline whose stages grade each submission
     * @param handout where the worker gets its next submission
     * @param context the shared read-only information the stages need
     * @param listener the listener, already made safe for several threads
     */
    GradingWorker(GradingPipeline pipeline, SubmissionHandout handout,
            GradingContext context, ProgressListener listener) {
        this.pipeline = pipeline;
        this.handout = handout;
        this.context = context;
        this.listener = listener;
    }

    /**
     * Takes submissions from the handout and grades each one, until there
     * are none left.
     */
    @Override
    public void run() {
        Submission submission = handout.next();

        while (submission != null) {
            pipeline.gradeOne(submission, context, listener);
            submission = handout.next();
        }
    }
}
