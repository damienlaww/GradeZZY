/*
 * File: FixStage.java
 *
 * Purpose: Applies automatic corrections to the working copy.
 *
 * Where it fits: Third stage. Skipped when AUTO_FIX_ENABLED is false.
 */
package gradezzy.grading;

import gradezzy.config.ConfigKeys;
import gradezzy.fix.FixManager;
import gradezzy.model.Submission;

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

    /**
     * Creates a new fix stage.
     *
     * @param fixManager the helper that applies fixes
     */
    public FixStage(FixManager fixManager) {
        this.fixManager = fixManager;
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
     * Applies automatic corrections unless they are turned off.
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
        fixManager.applyAll(submission);
    }
}
