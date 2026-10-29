/*
 * File: FixStage.java
 *
 * Purpose: Applies automatic corrections to the working copy.
 *
 * Where it fits: Third stage. Skipped when AUTO_FIX_ENABLED is false.
 */
package gradezzy.grading;

import java.util.ArrayList;
import java.util.List;

import gradezzy.config.ConfigKeys;
import gradezzy.fix.FixManager;
import gradezzy.fix.FixRecord;
import gradezzy.model.Anomaly;
import gradezzy.model.AnomalyType;
import gradezzy.model.Submission;
import gradezzy.validation.ValidationRunner;

/**
 * Applies automatic corrections to the working copy.
 */
public class FixStage implements GradingStage {

    /**
     * The name shown while this stage runs.
     */
    public static final String NAME = "Correcting common errors";

    // The helper that applies fixes
    private final FixManager fixManager;

    // The checks of the required files, run again after a move or rename
    private final ValidationRunner fileChecks;

    /**
     * Creates a new fix stage.
     *
     * @param fixManager the helper that applies fixes
     * @param fileChecks the checks of the required files, run again after
     *        a fix moved or renamed something
     */
    public FixStage(FixManager fixManager, ValidationRunner fileChecks) {
        this.fixManager = fixManager;
        this.fileChecks = fileChecks;
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
     * Applies automatic corrections unless they are turned off. A move or
     * rename can bring files into view that the checks could not see, so
     * the required files are then checked again and new problems fixed.
     *
     * @param submission the submission
     * @param context the context
     * @throws GradingException if the operation fails
     */
    @Override
    public void process(Submission submission,
            GradingContext context) throws GradingException {
        if (!context.getConfig().getBoolean(ConfigKeys.AUTO_FIX_ENABLED)) {
            return;
        }

        List<FixRecord> applied = fixManager.applyAll(submission);
        while (movedOrRenamed(applied)) {
            List<Anomaly> newAnomalies = checkFilesAgain(submission,
                    context);
            applied = fixManager.applyTo(submission, newAnomalies);
        }
    }

    // Says whether a fix moved or renamed something: all but header fixes.
    private boolean movedOrRenamed(List<FixRecord> applied) {
        for (FixRecord fixRecord : applied) {
            if (fixRecord.getAnomaly().getType()
                    != AnomalyType.MISSING_HEADER_NAME) {
                return true;
            }
        }
        return false;
    }

    // Checks the required files again and adds the problems not yet known.
    private List<Anomaly> checkFilesAgain(Submission submission,
            GradingContext context) {
        List<Anomaly> newAnomalies = new ArrayList<>();
        for (Anomaly found : fileChecks.runAll(submission,
                context.getAssessment())) {
            if (!isKnown(submission, found)) {
                submission.addAnomaly(found);
                newAnomalies.add(found);
            }
        }
        return newAnomalies;
    }

    // Says whether the submission already has this problem, paths aside.
    private boolean isKnown(Submission submission, Anomaly found) {
        String foundText = cutAtPath(submission, found);
        for (Anomaly known : submission.getAnomalies()) {
            if (known.getType() == found.getType()
                    && cutAtPath(submission, known).equals(foundText)) {
                return true;
            }
        }
        return false;
    }

    // Returns the message up to its first path in the working copy.
    private String cutAtPath(Submission submission, Anomaly anomaly) {
        String message = anomaly.getMessage();
        int pathStart = message.indexOf(
                submission.getWorkingCopyPath().toString());
        if (pathStart < 0) {
            return message;
        }
        return message.substring(0, pathStart);
    }
}
