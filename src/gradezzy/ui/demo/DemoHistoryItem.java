/*
 * File: DemoHistoryItem.java
 *
 * Purpose: One run on the Runs tab in demo mode, with a description of its
 * latest snapshot.
 *
 * Where it fits: DemoBackend keeps a list of these, newest first, and
 * turns each into a RunRecord for the Runs screen.
 */
package gradezzy.ui.demo;

import gradezzy.model.GradingRun;

/**
 * One run on the Runs tab with its backup.
 *
 * <p>Tier: COULD.
 */
public class DemoHistoryItem {

    // The run
    private final GradingRun run;

    // A description of its latest snapshot
    private String backupText = "";

    // Whether a snapshot can be restored
    private boolean restorable = false;

    /**
     * Creates a history item.
     *
     * @param run the run
     * @param backupText a description of its latest snapshot
     * @param restorable whether a snapshot can be restored
     */
    public DemoHistoryItem(GradingRun run, String backupText,
            boolean restorable) {
        this.run = run;
        this.backupText = backupText;
        this.restorable = restorable;
    }

    /**
     * Returns the run.
     *
     * @return the run
     */
    public GradingRun getRun() {
        return run;
    }

    /**
     * Returns the description of the latest snapshot.
     *
     * @return the description
     */
    public String getBackupText() {
        return backupText;
    }

    /**
     * Records a new snapshot, which can always be restored.
     *
     * @param backupText a description of the new snapshot
     */
    public void recordSnapshot(String backupText) {
        this.backupText = backupText;
        this.restorable = true;
    }

    /**
     * Says whether a snapshot can be restored.
     *
     * @return true when a snapshot can be restored
     */
    public boolean isRestorable() {
        return restorable;
    }
}
