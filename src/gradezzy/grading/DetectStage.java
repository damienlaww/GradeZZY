/*
 * File: DetectStage.java
 *
 * Purpose: Flags untouched starter files, empty answers, hard-coded answers
 * and headers that name another student, for the instructor to review.
 *
 * Where it fits: Fourth stage. It never changes a score.
 */
package gradezzy.grading;

import java.util.ArrayList;
import java.util.List;
import java.util.UUID;

import gradezzy.detection.Detector;
import gradezzy.detection.RosterCheck;
import gradezzy.model.Anomaly;
import gradezzy.model.AnomalyType;
import gradezzy.model.Assessment;
import gradezzy.model.Severity;
import gradezzy.model.Submission;

/**
 * Flags suspicious answers for the instructor to review.
 *
 * <p>Tier: SHOULD. Owner: Jun Sheng.
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

    // The checks that also read the roster, run after the detectors
    private final List<RosterCheck> rosterChecks;

    /**
     * Creates a new detect stage with no roster checks.
     *
     * @param detectors the detectors to run
     */
    public DetectStage(List<Detector> detectors) {
        this(detectors, new ArrayList<RosterCheck>());
    }

    /**
     * Creates a new detect stage.
     *
     * <p>Tier: SHOULD. Owner: Jing Yuan.
     *
     * @param detectors the detectors to run
     * @param rosterChecks the checks that also read the run's roster
     */
    public DetectStage(List<Detector> detectors,
            List<RosterCheck> rosterChecks) {
        this.detectors = detectors;
        this.rosterChecks = rosterChecks;
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

        for (RosterCheck rosterCheck : rosterChecks) {
            List<Anomaly> flags = runRosterCheck(rosterCheck, submission,
                    context);

            for (Anomaly flag : flags) {
                submission.addAnomaly(flag);
            }
        }
    }

    // Runs one roster check, or returns a warning if it fails.
    private List<Anomaly> runRosterCheck(RosterCheck rosterCheck,
            Submission submission, GradingContext context) {
        try {
            return rosterCheck.check(submission, context.getAssessment(),
                    context.getRoster());
        } catch (RuntimeException exception) {
            return failedCheck(rosterCheck.getName(), submission,
                    exception);
        }
    }

    // Runs one detector, or returns a warning if it fails.
    private List<Anomaly> runDetector(Detector detector,
            Submission submission, Assessment assessment) {
        try {
            return detector.detect(submission, assessment);
        } catch (RuntimeException exception) {
            return failedCheck(detector.getName(), submission, exception);
        }
    }

    // Returns the warning that a check could not run.
    private List<Anomaly> failedCheck(String checkName,
            Submission submission, RuntimeException exception) {
        List<Anomaly> warning = new ArrayList<>();
        warning.add(new Anomaly(ID_PREFIX + UUID.randomUUID(),
                AnomalyType.INTERNAL_ERROR, Severity.WARNING,
                submission.getUsername(), "The check '" + checkName
                + "' could not run: " + exception.getMessage(),
                FAILED_CHECK_ACTION));
        return warning;
    }
}
