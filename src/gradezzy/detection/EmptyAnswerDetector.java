/*
 * File: EmptyAnswerDetector.java
 *
 * Purpose: Flags methods whose body was left empty.
 *
 * Where it fits: Tells the instructor a zero is due to no attempt.
 */
package gradezzy.detection;

import java.util.ArrayList;
import java.util.List;

import gradezzy.model.Anomaly;
import gradezzy.model.Assessment;
import gradezzy.model.Submission;

/**
 * Flags methods whose body was left empty.
 *
 * <p>Tier: SHOULD. Owner: Jun Sheng.
 */
public class EmptyAnswerDetector implements Detector {

    /**
     * The name shown in the anomaly log.
     */
    public static final String NAME = "Empty answer";

    /**
     * Creates a new empty answer detector.
     */
    public EmptyAnswerDetector() {
        // nothing to set up yet
    }

    /**
     * Returns the name of this detector.
     *
     * @return the name of this detector
     */
    @Override
    public String getName() {
        return NAME;
    }

    /**
     * Flags methods whose body was left empty.
     *
     * @param submission the submission
     * @param assessment the assessment
     * @return the flags raised, empty if there are none
     */
    @Override
    public List<Anomaly> detect(Submission submission, Assessment assessment) {
        // TODO(Jun Sheng): implement: a check for answer methods that only hold
        //     the starter comment and return statement
        return new ArrayList<>();
    }
}
