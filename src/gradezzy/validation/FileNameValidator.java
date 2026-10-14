/*
 * File: FileNameValidator.java
 *
 * Purpose: Checks that source files follow the naming convention.
 *
 * Where it fits: Finds files that exist but are misnamed, for example q1a.java
 * or Q1A.java.
 */
package gradezzy.validation;

import java.util.ArrayList;
import java.util.List;

import gradezzy.model.Anomaly;
import gradezzy.model.Assessment;
import gradezzy.model.Submission;

/**
 * Checks that source files follow the naming convention.
 *
 * <p>Tier: MUST. Owner: Seth.
 */
public class FileNameValidator extends AbstractValidator {

    /**
     * The name shown in the anomaly log.
     */
    public static final String NAME = "File names";

    /**
     * Creates a new file name validator.
     */
    public FileNameValidator() {
        super(NAME);
    }

    /**
     * Checks that source files follow the naming convention.
     *
     * @param submission the submission
     * @param assessment the assessment
     * @return the problems found, empty if there are none
     */
    @Override
    public List<Anomaly> validate(Submission submission,
            Assessment assessment) {
        // TODO(Seth): implement: a comparison of the files present with the
        //     expected names
        return new ArrayList<>();
    }
}
