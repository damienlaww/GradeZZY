/*
 * File: RequiredFilesValidator.java
 *
 * Purpose: Checks that every required Java source file is present.
 *
 * Where it fits: Finds missing files so later stages can skip that question and
 * award zero without failing.
 */
package gradezzy.validation;

import java.util.ArrayList;
import java.util.List;

import gradezzy.model.Anomaly;
import gradezzy.model.Assessment;
import gradezzy.model.Submission;

/**
 * Checks that every required Java source file is present.
 *
 * <p>Tier: MUST. Owner: Damien.
 */
public class RequiredFilesValidator extends AbstractValidator {

    /**
     * The name shown in the anomaly log.
     */
    public static final String NAME = "Required files";

    /**
     * Creates a new required files validator.
     */
    public RequiredFilesValidator() {
        super(NAME);
    }

    /**
     * Checks that every required Java source file is present.
     *
     * @param submission the submission
     * @param assessment the assessment
     * @return the problems found, empty if there are none
     */
    @Override
    public List<Anomaly> validate(Submission submission,
            Assessment assessment) {
        // TODO(Damien): implement: a check that each Question file exists in
        //     its folder
        return new ArrayList<>();
    }
}
