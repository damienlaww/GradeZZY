/*
 * File: Validator.java
 *
 * Purpose: One check of a submission against the rules of the assessment.
 *
 * Where it fits: Implemented once per rule. ValidationRunner calls every
 * Validator, so a new check is added by writing one new class.
 */
package gradezzy.validation;

import java.util.List;

import gradezzy.model.Anomaly;
import gradezzy.model.Assessment;
import gradezzy.model.Submission;

/**
 * One check of a submission against the rules of the assessment.
 *
 * <p>Tier: MUST. Owner: Seth.
 */
public interface Validator {

    /**
     * Returns the name shown in the anomaly log.
     *
     * @return the name of this check
     */
    String getName();

    /**
     * Checks one submission. Must not throw for bad student input; report it as
     * an anomaly instead.
     *
     * @param submission the submission
     * @param assessment the assessment
     * @return the problems found, empty if there are none
     */
    List<Anomaly> validate(Submission submission, Assessment assessment);
}
