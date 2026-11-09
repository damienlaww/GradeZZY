/*
 * File: RosterCheck.java
 *
 * Purpose: A check for abnormal answers that also needs the students on
 * the score sheet, such as a header that names another student.
 *
 * Where it fits: DetectStage runs each one after the Detectors, giving it
 * the run's roster. Like a Detector, it only adds flags and never changes
 * a mark. Register one in PipelineFactory.
 */
package gradezzy.detection;

import java.util.List;

import gradezzy.model.Anomaly;
import gradezzy.model.Assessment;
import gradezzy.model.Student;
import gradezzy.model.Submission;

/**
 * A check for abnormal answers that also reads the roster.
 *
 * <p>Tier: SHOULD. Owner: Jing Yuan.
 */
public interface RosterCheck {

    /**
     * Returns the name of the check.
     *
     * <p>Tier: SHOULD. Owner: Jing Yuan.
     *
     * @return the name of the check
     */
    String getName();

    /**
     * Checks one submission against the students on the score sheet.
     *
     * <p>Tier: SHOULD. Owner: Jing Yuan.
     *
     * @param submission the submission
     * @param assessment the assessment
     * @param roster the students on the run's score sheet, possibly empty
     * @return the flags found, never null
     */
    List<Anomaly> check(Submission submission, Assessment assessment,
            List<Student> roster);
}
