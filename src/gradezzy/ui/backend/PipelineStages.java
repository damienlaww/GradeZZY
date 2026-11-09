/*
 * File: PipelineStages.java
 *
 * Purpose: The names of the nine grading stages, in pipeline order.
 *
 * Where it fits: Used by the Grade step. The names come from each
 * stage's NAME constant, so they match what GradingPipeline reports.
 */
package gradezzy.ui.backend;

import gradezzy.grading.CompileStage;
import gradezzy.grading.DetectStage;
import gradezzy.grading.ExecuteStage;
import gradezzy.grading.ExtractStage;
import gradezzy.grading.FixStage;
import gradezzy.grading.InstallTestersStage;
import gradezzy.grading.PersistStage;
import gradezzy.grading.ScoreStage;
import gradezzy.grading.ValidateStage;

/**
 * The grading stages, in order.
 *
 * <p>Tier: MUST.
 */
public final class PipelineStages {

    // The stage names, in the order PipelineFactory adds the stages
    private static final String[] NAMES = {ExtractStage.NAME,
        ValidateStage.NAME, FixStage.NAME, DetectStage.NAME,
        InstallTestersStage.NAME, CompileStage.NAME, ExecuteStage.NAME,
        ScoreStage.NAME, PersistStage.NAME};

    // Utility class, never instantiated.
    private PipelineStages() {
        // nothing to set up
    }

    /**
     * Returns the number of stages.
     *
     * @return the count
     */
    public static int count() {
        return NAMES.length;
    }

    /**
     * Returns the name of one stage.
     *
     * @param index the position, from 0
     * @return the stage name
     */
    public static String nameAt(int index) {
        return NAMES[index];
    }

    /**
     * Returns the position of a stage.
     *
     * @param name the stage name
     * @return the position from 0, or -1 for an unknown name
     */
    public static int indexOf(String name) {
        for (int index = 0; index < NAMES.length; index++) {
            if (NAMES[index].equals(name)) {
                return index;
            }
        }
        return -1;
    }
}
