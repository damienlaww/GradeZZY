/*
 * File: ValidateStage.java
 *
 * Purpose: Runs every validator and records what they find.
 *
 * Where it fits: Second stage. Its anomalies tell FixStage what to correct.
 */
package gradezzy.grading;

import gradezzy.model.Submission;
import gradezzy.validation.ValidationRunner;

/**
 * Runs every validator and records what they find.
 *
 * <p>Tier: MUST. Owner: Seth.
 */
public class ValidateStage implements GradingStage {

    /**
     * The name shown while this stage runs.
     */
    public static final String NAME = "Checking structure";

    // The helper that runs all validators
    private final ValidationRunner validationRunner;

    /**
     * Creates a new validate stage.
     *
     * @param validationRunner the helper that runs all validators
     */
    public ValidateStage(ValidationRunner validationRunner) {
        this.validationRunner = validationRunner;
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
     * Runs every validator and records what they find.
     *
     * @param submission the submission
     * @param context the context
     * @throws GradingException if the operation fails
     */
    @Override
    public void process(Submission submission,
            GradingContext context) throws GradingException {
        // TODO(Seth): implement: calling validationRunner.runAll and adding
        //     each anomaly to the submission
    }
}
