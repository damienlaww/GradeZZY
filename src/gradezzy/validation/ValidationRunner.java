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

import gradezzy.model.Anomaly;
import gradezzy.model.Assessment;
import gradezzy.model.Submission;

/**
 * Runs every registered validator on a submission.
 *
 * <p>Tier: MUST. Owner: Damien.
 */
public class ValidationRunner {

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
     * Runs all validators and collects what they report.
     *
     * @param submission the submission
     * @param assessment the assessment
     * @return every anomaly found
     */
    public List<Anomaly> runAll(Submission submission, Assessment assessment) {
        // TODO(Damien): implement: calling each validator and adding its
        //     anomalies to one list; a validator that throws must not stop the
        //     rest
        return new ArrayList<>();
    }
}
