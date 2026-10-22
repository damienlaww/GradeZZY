/*
 * File: DetectStage.java
 *
 * Purpose: Flags untouched starter files, empty answers and hard-coded answers
 * for the instructor to review.
 *
 * Where it fits: Fourth stage. It never changes a score.
 */
package gradezzy.grading;

import java.util.ArrayList;
import java.util.List;
import java.util.UUID;

import gradezzy.detection.Detector;
import gradezzy.model.Anomaly;
import gradezzy.model.AnomalyType;
import gradezzy.model.Assessment;
import gradezzy.model.Severity;
import gradezzy.model.Submission;

/**
 * Flags suspicious answers for the instructor to review.
 */
public class DetectStage implements GradingStage {

    /**
     * The name shown while this stage runs.
     */
    public static final String NAME = "Looking for abnormal answers";

    // The text in front of the id of a failed check's warning
    private static final String ID_PREFIX = "detection-";

    // What the instructor can do when a check could not run
    private static final String FAILED_CHECK_ACTION =
            "Check this submission by hand. No mark was changed.";

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
     * Flags suspicious answers for the instructor to review.
     *
     * @param submission the submission
     * @param context the context
     * @throws GradingException if the operation fails
     */
    @Override
    public void process(Submission submission,
            GradingContext context) throws GradingException {
        for (Detector detector : detectors) {
            List<Anomaly> flags = runDetector(detector, submission,
                    context.getAssessment());

            for (Anomaly flag : flags) {
                submission.addAnomaly(flag);
            }
        }
    }

    // Runs one detector, or returns a warning if it fails.
    private List<Anomaly> runDetector(Detector detector,
            Submission submission, Assessment assessment) {
        try {
            return detector.detect(submission, assessment);
        } catch (RuntimeException exception) {
            List<Anomaly> warning = new ArrayList<>();
            warning.add(new Anomaly(ID_PREFIX + UUID.randomUUID(),
                    AnomalyType.INTERNAL_ERROR, Severity.WARNING,
                    submission.getUsername(), "The check '"
                    + detector.getName() + "' could not run: "
                    + exception.getMessage(), FAILED_CHECK_ACTION));
            return warning;
        }
    }
}
