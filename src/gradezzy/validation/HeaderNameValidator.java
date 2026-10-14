/*
 * File: HeaderNameValidator.java
 *
 * Purpose: Checks that each Java file has the student name and email in its
 * header comment.
 *
 * Where it fits: Finds blank headers so HeaderNameFixer can fill them in.
 */
package gradezzy.validation;

import java.util.ArrayList;
import java.util.List;

import gradezzy.model.Anomaly;
import gradezzy.model.Assessment;
import gradezzy.model.Submission;

/**
 * Checks that each Java file has the student name and email in its header
 * comment.
 *
 * <p>Tier: MUST. Owner: Seth.
 */
public class HeaderNameValidator extends AbstractValidator {

    /**
     * The name shown in the anomaly log.
     */
    public static final String NAME = "Header name";

    /**
     * Creates a new header name validator.
     */
    public HeaderNameValidator() {
        super(NAME);
    }

    /**
     * Checks that each Java file has the student name and email in its header
     * comment.
     *
     * @param submission the submission
     * @param assessment the assessment
     * @return the problems found, empty if there are none
     */
    @Override
    public List<Anomaly> validate(Submission submission,
            Assessment assessment) {
        // TODO(Seth): implement: reading the first HEADER_SCAN_LINES lines
        //     and checking the text after the name and email labels
        return new ArrayList<>();
    }
}
