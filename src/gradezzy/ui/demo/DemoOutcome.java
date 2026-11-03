/*
 * File: DemoOutcome.java
 *
 * Purpose: Gives a submission its simulated results when its last stage
 * ends in demo mode.
 *
 * Where it fits: DemoBackend passes one to each DemoGrader, which calls
 * it once per finished submission.
 */
package gradezzy.ui.demo;

import gradezzy.model.Submission;

/**
 * Fills in the simulated results of one finished submission.
 */
public interface DemoOutcome {

    /**
     * Fills in the results of one submission.
     *
     * @param submission the submission that finished
     */
    void apply(Submission submission);
}
