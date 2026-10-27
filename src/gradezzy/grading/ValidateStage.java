/*
 * File: ValidateStage.java
 *
 * Purpose: Runs every validator and records what they find.
 *
 * Where it fits: Second stage. Its anomalies tell FixStage what to correct.
 */
package gradezzy.grading;

import java.util.List;

import gradezzy.model.Anomaly;
import gradezzy.model.Submission;
import gradezzy.validation.ValidationRunner;

/**
 * Runs every validator and records what they find.
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
     * Runs every validator and records what they find. Problems in the
     * student's work become anomalies; only a missing working copy stops
     * the submission, because there is then nothing to check.
     *
     * @param submission the submission
     * @param context the context
     * @throws GradingException if the submission has no working copy
     */
    @Override
    public void process(Submission submission,
            GradingContext context) throws GradingException {
        if (submission.getWorkingCopyPath() == null) {
            throw new GradingException("There is no working copy to check "
                    + "for " + submission.getUsername() + ".");
        }

        List<Anomaly> anomalies = validationRunner.runAll(submission,
                context.getAssessment());

        for (Anomaly anomaly : anomalies) {
            submission.addAnomaly(anomaly);
        }
    }
}
