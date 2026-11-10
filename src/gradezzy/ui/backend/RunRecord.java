/*
 * File: RunRecord.java
 *
 * Purpose: One earlier grading run and its backup, for the Runs tab.
 *
 * Where it fits: Returned by DashboardBackend.listRuns.
 */
package gradezzy.ui.backend;

import gradezzy.model.GradingRun;

/**
 * An earlier grading run and its backup.
 *
 * <p>Tier: SHOULD.
 */
public class RunRecord {

    // The run itself
    private final GradingRun run;

    // A short description of how far the run got
    private final String stateText;

    // A short description of the run's backup
    private final String backupText;

    // Whether a backup of this run can be restored
    private final boolean restorable;

    // Whether this is the run the screens show now
    private final boolean current;

    /**
     * Creates a run record.
     *
     * @param run the run itself
     * @param stateText how far the run got
     * @param backupText the run's backup state
     * @param restorable whether its backup can be restored
     * @param current whether the screens show this run now
     */
    public RunRecord(GradingRun run, String stateText, String backupText,
            boolean restorable, boolean current) {
        this.run = run;
        this.stateText = stateText;
        this.backupText = backupText;
        this.restorable = restorable;
        this.current = current;
    }

    /**
     * Returns the run.
     *
     * @return the grading run
     */
    public GradingRun getRun() {
        return run;
    }

    /**
     * Returns how far the run got.
     *
     * @return a short description
     */
    public String getStateText() {
        return stateText;
    }

    /**
     * Returns the run's backup state.
     *
     * @return a short description
     */
    public String getBackupText() {
        return backupText;
    }

    /**
     * Says whether a backup of this run can be restored.
     *
     * @return true when a backup exists
     */
    public boolean isRestorable() {
        return restorable;
    }

    /**
     * Says whether this is the run the screens show now.
     *
     * @return true for the current run
     */
    public boolean isCurrent() {
        return current;
    }
}
