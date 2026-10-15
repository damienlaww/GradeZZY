/*
 * File: RunFollower.java
 *
 * Purpose: Handles the end of a background grading run the same way for
 * every screen that starts one: it updates the monitor, refreshes the
 * window, records the outcome in the audit log and tells the instructor.
 *
 * Where it fits: The RunCallback given to DashboardBackend by the
 * Submissions and Grading Progress screens.
 *
 * AI use: drafted with Claude Code, reviewed by Jun Sheng.
 */
package gradezzy.ui;

import gradezzy.model.GradingRun;
import gradezzy.ui.backend.DashboardBackend;
import gradezzy.ui.backend.RunCallback;

/**
 * Follows a grading run to its end.
 *
 * <p>Tier: MUST. Owner: Damien.
 */
public class RunFollower implements RunCallback {

    // The backend, for the audit log
    private final DashboardBackend backend;

    // The state shared by every screen
    private final UiState state;

    // The window, for messages
    private final AppShell shell;

    // What started, such as "Grading", for the messages
    private final String activity;

    /**
     * Creates a follower for one run.
     *
     * @param backend the backend, for the audit log
     * @param state the state shared by every screen
     * @param shell the window, for messages
     * @param activity what started, such as "Grading"
     */
    public RunFollower(DashboardBackend backend, UiState state,
            AppShell shell, String activity) {
        this.backend = backend;
        this.state = state;
        this.shell = shell;
        this.activity = activity;
    }

    /**
     * Records the finished or cancelled run and tells the instructor.
     *
     * @param gradingRun the run with its results so far
     */
    @Override
    public void onFinished(GradingRun gradingRun) {
        GradingMonitor monitor = state.getMonitor();
        monitor.finish(gradingRun);
        state.markDataChanged();
        backend.recordAudit(activity + " ended", monitor.messageProperty()
                .get());
        shell.refreshContext();
        shell.showToast(monitor.messageProperty().get());
    }

    /**
     * Records the failure and tells the instructor what to do next.
     *
     * @param message what went wrong and what to do next
     */
    @Override
    public void onFailed(String message) {
        state.getMonitor().fail(message);
        state.markDataChanged();
        backend.recordAudit(activity + " stopped", message);
        shell.refreshContext();
        shell.showError(message);
    }
}
