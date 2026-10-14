/*
 * File: DetectStage.java
 *
 * Purpose: Flags untouched starter files, empty answers and hard-coded answers
 * for the instructor to review.
 *
 * Where it fits: Fourth stage. It never changes a score.
 */
package gradezzy.grading;

import java.util.List;

import gradezzy.detection.Detector;
import gradezzy.model.Submission;

/**
 * Flags untouched starter files, empty answers and hard-coded answers for the
 * instructor to review.
 *
 * <p>Tier: MUST. Owner: Jun Sheng.
 */
public class DetectStage implements GradingStage {

    /**
     * The name shown while this stage runs.
     */
    public static final String NAME = "Looking for abnormal answers";

    // The detectors to run
    private final List<Detector> detectors;

    /**
     * Creates a new detect stage.
     *
     * @param detectors the detectors to run
     */
    public DetectStage(List<Detector> detectors) {
        this.detectors = detectors;
    }

    /**
     * Returns the name of this stage.
     *
     * @return the name of this stage
     */
    @Override
    public String getName() {
        return NAME;
    }

    /**
     * Flags untouched starter files, empty answers and hard-coded answers for
     * the instructor to review.
     *
     * @param submission the submission
     * @param context the context
     * @throws GradingException if the operation fails
     */
    @Override
    public void process(Submission submission,
            GradingContext context) throws GradingException {
        // TODO(Jun Sheng): implement: calling each detector and adding its
        //     anomalies to the submission
    }
}
