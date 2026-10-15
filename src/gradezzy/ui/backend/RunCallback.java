/*
 * File: RunCallback.java
 *
 * Purpose: Tells the user interface how a background grading run ended.
 *
 * Where it fits: Passed to DashboardBackend.startGrading, resumeGrading
 * and retryFailedSubmissions. Both methods are always called on the
 * JavaFX Application Thread.
 *
 * AI use: drafted with Claude Code, reviewed by Jun Sheng.
 */
package gradezzy.ui.backend;

import gradezzy.model.GradingRun;

/**
 * Receives the end of a background grading run.
 *
 * <p>Tier: MUST. Owner: Damien.
 */
public interface RunCallback {

    /**
     * Called when the run finished or was cancelled.
     *
     * @param gradingRun the run with its results so far
     */
    void onFinished(GradingRun gradingRun);

    /**
     * Called when the run could not start or stopped with an error.
     *
     * @param message what went wrong and what to do next
     */
    void onFailed(String message);
}
