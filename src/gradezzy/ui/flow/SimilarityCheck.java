/*
 * File: SimilarityCheck.java
 *
 * Purpose: Runs the similar code check of one run on a background
 * thread, so the window does not freeze while every pair of answers is
 * compared.
 *
 * Where it fits: ExportScreen starts one when the instructor presses
 * Check again in live mode, and fills the card when the check succeeds.
 *
 * Note on untaught Java: javafx.concurrent.Task runs the check on a
 * background thread, as GradingTask does for grading.
 */
package gradezzy.ui.flow;

import java.util.List;

import javafx.concurrent.Task;

import gradezzy.model.GradingRun;
import gradezzy.plagiarism.SimilarityPair;
import gradezzy.ui.backend.DashboardBackend;

/**
 * A background task that finds the similar pairs of one run.
 */
public class SimilarityCheck extends Task<List<SimilarityPair>> {

    // The backend that runs the check
    private final DashboardBackend backend;

    // The run whose answers are compared
    private final GradingRun run;

    /**
     * Creates the check of one run.
     *
     * @param backend the backend that runs the check
     * @param run the run whose answers are compared
     */
    public SimilarityCheck(DashboardBackend backend, GradingRun run) {
        this.backend = backend;
        this.run = run;
    }

    /**
     * Returns the run being checked.
     *
     * @return the run
     */
    public GradingRun getRun() {
        return run;
    }

    /**
     * Finds the pairs. JavaFX calls this on a background thread.
     *
     * @return the pairs, most similar first
     */
    @Override
    protected List<SimilarityPair> call() {
        return backend.checkSimilarity(run);
    }
}
