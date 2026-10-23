/*
 * File: ValidationRunner.java
 *
 * Purpose: Runs every registered validator on a submission.
 *
 * Where it fits: Called by ValidateStage. Validators are registered in
 * PipelineFactory.
 */
package gradezzy.validation;

import java.util.ArrayList;
import java.util.List;
import java.util.UUID;

import gradezzy.model.Anomaly;
import gradezzy.model.AnomalyType;
import gradezzy.model.Assessment;
import gradezzy.model.Severity;
import gradezzy.model.Submission;

/**
 * Runs every registered validator on a submission.
 *
 * <p>Tier: MUST. Owner: Seth.
 */
public class ValidationRunner {

    // The text in front of the id of an anomaly for a failed validator
    private static final String FAILURE_ID_PREFIX = "validation-failure-";

    // What the instructor can do when a validator itself fails
    private static final String FAILURE_ACTION =
            "Check this submission by hand and report the message to the "
            + "team.";

    // The validators to run, in order
    private final List<Validator> validators = new ArrayList<>();

    /**
     * Creates a new validation runner.
     */
    public ValidationRunner() {
        // nothing to set up yet
    }

    /**
     * Registers one more validator.
     *
     * @param validator the validator
     */
    public void addValidator(Validator validator) {
        validators.add(validator);
    }

    /**
     * Runs all validators and collects what they report. A validator that
     * throws is recorded as an INTERNAL_ERROR anomaly and the rest still
     * run.
     *
     * @param submission the submission
     * @param assessment the assessment
     * @return every anomaly found
     */
    public List<Anomaly> runAll(Submission submission, Assessment assessment) {
        List<Anomaly> anomalies = new ArrayList<>();

        for (Validator validator : validators) {
            try {
                List<Anomaly> found = validator.validate(submission,
                        assessment);

                if (found != null) {
                    anomalies.addAll(found);
                }
            } catch (RuntimeException exception) {
                anomalies.add(describeFailure(submission, validator,
                        exception));
            }
        }

        return anomalies;
    }

    // Turns a validator that threw into an anomaly on the submission.
    private Anomaly describeFailure(Submission submission,
            Validator validator, RuntimeException exception) {
        String id = FAILURE_ID_PREFIX + UUID.randomUUID();
        String message = "The check '" + validator.getName() + "' failed: "
                + exception.getMessage();

        return new Anomaly(id, AnomalyType.INTERNAL_ERROR, Severity.ERROR,
                submission.getUsername(), message, FAILURE_ACTION);
    }
}
