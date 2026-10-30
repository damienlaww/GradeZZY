/*
 * File: SubmissionHandout.java
 *
 * Purpose: Hands each submission of a batch to exactly one grading worker.
 *
 * Where it fits: Shared by the GradingWorker threads that GradingPipeline
 * starts when several submissions are graded at the same time.
 */
package gradezzy.grading;

import java.util.List;

import gradezzy.model.Submission;

/**
 * Hands each submission to exactly one worker. The index is guarded by
 * synchronized, so two workers never take the same submission and none
 * is missed.
 *
 * <p>Tier: MUST. Owner: Jing Yuan.
 */
class SubmissionHandout {

    // The submissions, in batch order
    private final List<Submission> submissions;

    // The next submission to hand out
    private int index = 0;

    /**
     * Creates a handout over the submissions of one batch.
     *
     * @param submissions the submissions, in batch order
     */
    SubmissionHandout(List<Submission> submissions) {
        this.submissions = submissions;
    }

    /**
     * Returns the next submission. Synchronized so only one worker at a
     * time can read and move the index.
     *
     * @return the next submission, or null when there are none left
     */
    synchronized Submission next() {
        if (index >= submissions.size()) {
            return null;
        }

        Submission submission = submissions.get(index);
        index++;
        return submission;
    }
}
