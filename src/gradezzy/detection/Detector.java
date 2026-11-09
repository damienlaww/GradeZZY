/*
 * File: Detector.java
 *
 * Purpose: One check that flags a suspicious answer for review.
 *
 * Where it fits: Like a Validator, but about the content of the answer and
 * never about marks: detectors flag, the instructor decides.
 */
package gradezzy.detection;

import java.util.List;

import gradezzy.model.Anomaly;
import gradezzy.model.Assessment;
import gradezzy.model.Submission;

/**
 * One check that flags a suspicious answer for review.
 *
 * <p>Tier: SHOULD. Owner: Jun Sheng.
 */
public interface Detector {

    /**
     * Returns the name shown in the log.
     *
     * @return the name of this detector
     */
    String getName();

    /**
     * Inspects one submission.
     *
     * @param submission the submission
     * @param assessment the assessment
     * @return the flags raised, empty if there are none
     */
    List<Anomaly> detect(Submission submission, Assessment assessment);
}
