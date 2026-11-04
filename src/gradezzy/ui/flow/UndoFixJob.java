/*
 * File: UndoFixJob.java
 *
 * Purpose: Undoes one fix on a background thread. Undoing a fix puts
 * the working copy back and grades the student again, which compiles
 * and runs every tester, so it would freeze the window on its thread.
 *
 * Where it fits: StudentPanel starts one in live mode when Undo is
 * pressed on a fix, and shows the outcome when it ends. The demo calls
 * the backend directly, as its undo is instant.
 *
 * Note on untaught Java: javafx.concurrent.Task runs the undo on a
 * background thread, as ExportJob does for exports.
 */
package gradezzy.ui.flow;

import javafx.concurrent.Task;

import gradezzy.ui.backend.ActionResult;
import gradezzy.ui.backend.DashboardBackend;
import gradezzy.ui.backend.RepairItem;

/**
 * A background task that undoes one fix and grades the student again.
 */
public class UndoFixJob extends Task<ActionResult> {

    // The backend that undoes the fix
    private final DashboardBackend backend;

    // The fix to undo
    private final RepairItem repair;

    /**
     * Creates the undo of a fix.
     *
     * @param backend the backend that undoes the fix
     * @param repair the fix to undo
     */
    public UndoFixJob(DashboardBackend backend, RepairItem repair) {
        this.backend = backend;
        this.repair = repair;
    }

    /**
     * Undoes the fix. JavaFX calls this on a background thread.
     *
     * @return the outcome
     */
    @Override
    protected ActionResult call() {
        return backend.rejectRepair(repair);
    }
}
