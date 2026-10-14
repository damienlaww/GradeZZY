/*
 * File: StructureValidator.java
 *
 * Purpose: Checks the folder layout: a username folder holding one folder per
 * question group.
 *
 * Where it fits: Finds the missing wrapper folder, wrong folder name and
 * missing Qx folder cases described in the brief.
 */
package gradezzy.validation;

import java.util.ArrayList;
import java.util.List;

import gradezzy.model.Anomaly;
import gradezzy.model.Assessment;
import gradezzy.model.Submission;

/**
 * Checks the folder layout: a username folder holding one folder per question
 * group.
 *
 * <p>Tier: MUST. Owner: Damien.
 */
public class StructureValidator extends AbstractValidator {

    /**
     * The name shown in the anomaly log.
     */
    public static final String NAME = "Folder structure";

    /**
     * Creates a new structure validator.
     */
    public StructureValidator() {
        super(NAME);
    }

    /**
     * Checks the folder layout: a username folder holding one folder per
     * question group.
     *
     * @param submission the submission
     * @param assessment the assessment
     * @return the problems found, empty if there are none
     */
    @Override
    public List<Anomaly> validate(Submission submission,
            Assessment assessment) {
        // TODO(Damien): implement: checks for the wrapper folder, its name and
        //     each question folder; report with the matching AnomalyType
        return new ArrayList<>();
    }
}
