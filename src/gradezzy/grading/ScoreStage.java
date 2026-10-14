/*
 * File: ScoreStage.java
 *
 * Purpose: Works out the per-question and per-student scores.
 *
 * Where it fits: Eighth stage. After it, the submission has its final marks.
 */
package gradezzy.grading;

import gradezzy.model.Submission;

/**
 * Works out the per-question and per-student scores.
 *
 * <p>Tier: MUST. Owner: Jing Yuan.
 */
public class ScoreStage implements GradingStage {

    /**
     * The name shown while this stage runs.
     */
    public static final String NAME = "Scoring";

    // The helper that adds up scores
    private final ScoreCalculator scoreCalculator;

    /**
     * Creates a new score stage.
     *
     * @param scoreCalculator the helper that adds up scores
     */
    public ScoreStage(ScoreCalculator scoreCalculator) {
        this.scoreCalculator = scoreCalculator;
    }

    /**
     * Returns the name of this stage.
     *
     * @return the name of this stage
     */
    @Override
    public String getName() {
        return NAME;
    }

    /**
     * Works out the per-question and per-student scores.
     *
     * @param submission the submission
     * @param context the context
     * @throws GradingException if the operation fails
     */
    @Override
    public void process(Submission submission,
            GradingContext context) throws GradingException {
        // TODO(Jing Yuan): implement: setting each question score with
        //     scoreCalculator, never above the question maximum
    }
}
