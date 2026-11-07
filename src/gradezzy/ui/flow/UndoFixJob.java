/*
 * File: UndoFixJob.java
 *
 * Purpose: Undoes one fix, or applies a rejected one again, on a
 * background thread. Either way the student is graded again, which
 * compiles and runs every tester, so it would freeze the window on its
 * thread.
 *
 * Where it fits: StudentPanel and the activity log on RunsScreen start
 * one in live mode, and show the outcome when it ends. The demo calls
 * the backend directly, as its undo is instant.
 *
 * Note on untaught Java: javafx.concurrent.Task runs the job on a
 * background thread, as ExportJob does for exports.
 */
package gradezzy.ui.flow;

import java.util.function.Consumer;

import javafx.concurrent.Task;

import gradezzy.model.GradingRun;
import gradezzy.ui.backend.ActionResult;
import gradezzy.ui.backend.Capability;
import gradezzy.ui.backend.DashboardBackend;
import gradezzy.ui.backend.RepairItem;

/**
 * A background task that undoes one fix, or applies it again, and
 * grades the student again.
 */
public class UndoFixJob extends Task<ActionResult> {

    // The backend that undoes the fix
    private final DashboardBackend backend;

    // The fix to undo or apply again
    private final RepairItem repair;

    // True to apply a rejected fix again instead of undoing it
    private final boolean applyAgain;

    /**
     * Creates the undo of a fix, or the job that applies it again.
     *
     * @param backend the backend that undoes the fix
     * @param repair the fix to undo or apply again
     * @param applyAgain true to apply a rejected fix again
     */
    public UndoFixJob(DashboardBackend backend, RepairItem repair,
            boolean applyAgain) {
        this.backend = backend;
        this.repair = repair;
        this.applyAgain = applyAgain;
    }

    /**
     * Says whether a rejected fix may be applied again.
     *
     * @param backend the backend
     * @return true when Apply fix again may be offered
     */
    public static boolean canApplyAgain(DashboardBackend backend) {
        return backend.supports(Capability.REAPPLY_FIX);
    }

    /**
     * Starts the job on a background thread, unless another fix job is
     * still running. Until it ends, the state names this fix as running.
     *
     * @param state the window's state, shared by every screen
     * @param run the run the student belongs to
     * @param onEnd what to do with the outcome once the job ends
     * @return true when the job started
     */
    public boolean startOnce(FlowState state, GradingRun run,
            Consumer<ActionResult> onEnd) {
        if (state.getFixInProgress() != null) {
            return false;
        }

        setOnSucceeded(event -> ended(state, run, onEnd));
        setOnFailed(event -> ended(state, run, onEnd));
        state.setFixInProgress(repair.getAnomaly());
        // A daemon thread does not keep GradeZZY open
        Thread worker = new Thread(this, "gradezzy-fix");
        worker.setDaemon(true);
        worker.start();
        return true;
    }

    // Clears the running fix and passes the outcome on. New marks make
    // an earlier export of the run out of date.
    private void ended(FlowState state, GradingRun run,
            Consumer<ActionResult> onEnd) {
        state.setFixInProgress(null);
        ActionResult result = getValue();
        if (getException() != null) {
            result = ActionResult.failed(describeFailure());
        }
        if (result.isDone()) {
            state.forgetExport(run);
        }
        onEnd.accept(result);
    }

    // Says why the job stopped before it finished.
    private String describeFailure() {
        String what = "The fix was not undone: ";
        if (applyAgain) {
            what = "The fix was not applied again: ";
        }
        return what + getException().getMessage();
    }

    /**
     * Undoes the fix or applies it again. JavaFX calls this on a
     * background thread.
     *
     * @return the outcome
     */
    @Override
    protected ActionResult call() {
        if (applyAgain) {
            return backend.reapplyRepair(repair);
        }
        return backend.rejectRepair(repair);
    }
}
