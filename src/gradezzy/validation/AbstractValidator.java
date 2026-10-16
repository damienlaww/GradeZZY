/*
 * File: AbstractValidator.java
 *
 * Purpose: Shared code for all validators.
 *
 * Where it fits: Holds the name and the helper that builds an Anomaly, so each
 * concrete validator only contains its own rule.
 */
package gradezzy.validation;

import gradezzy.model.Anomaly;
import gradezzy.model.AnomalyType;
import gradezzy.model.Severity;
import gradezzy.model.Submission;

/**
 * Shared code for all validators.
 *
 * <p>Tier: MUST. Owner: Seth.
 */
public abstract class AbstractValidator implements Validator {

    // The name shown in the anomaly log
    private final String name;

    /**
     * Creates a new abstract validator.
     *
     * @param name the name shown in the anomaly log
     */
    protected AbstractValidator(String name) {
        this.name = name;
    }

    /**
     * Returns the name of this check.
     *
     * @return the name of this check
     */
    @Override
    public String getName() {
        return name;
    }

    /**
     * Builds an anomaly for the given submission.
     *
     * @param submission the submission
     * @param type the type
     * @param severity the severity
     * @param message the message
     * @return the new anomaly
     */
    protected Anomaly createAnomaly(Submission submission, AnomalyType type,
            Severity severity, String message) {
        // TODO(Seth): implement: creating an Anomaly with a unique id and a
        //     suggested action that fits the type
        return null;
    }
}
