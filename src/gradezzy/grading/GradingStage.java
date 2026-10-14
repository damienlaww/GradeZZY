/*
 * File: GradingStage.java
 *
 * Purpose: One step of the grading pipeline.
 *
 * Where it fits: The pipeline is a list of these. A new step (or a replacement)
 * is one new class registered in PipelineFactory.
 */
package gradezzy.grading;

import gradezzy.model.Submission;

/**
 * One step of the grading pipeline.
 *
 * <p>Tier: MUST. Owner: Jing Yuan.
 */
public interface GradingStage {

    /**
     * Returns the name shown while this stage runs.
     *
     * @return the name of this stage
     */
    String getName();

    /**
     * Does this stage's work for one submission. Problems with the student's
     * work are added to the submission as anomalies; throw only when the stage
     * itself cannot go on.
     *
     * @param submission the submission
     * @param context the context
     * @throws GradingException if the operation fails
     */
    void process(Submission submission,
            GradingContext context) throws GradingException;
}
