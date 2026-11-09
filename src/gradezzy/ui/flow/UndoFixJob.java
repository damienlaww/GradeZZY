/*
 * File: UndoFixJob.java
 *
 * Purpose: Undoes one fix or several, or applies a rejected one again,
 * on a background thread. Either way the student is graded again, which
 * compiles and runs every tester, so it would freeze the window on its
 * thread.
 *
 * Where it fits: StudentPanel, SameFixes and the activity log on
 * RunsScreen start one in live mode, and show the outcome when it ends.
 * The demo calls the backend directly, as its undo is instant.
 *
 * Note on untaught Java: javafx.concurrent.Task runs the job on a
 * background thread, as ExportJob does for exports.
 */
package gradezzy.ui.flow;

import java.util.ArrayList;
import java.util.List;
import java.util.function.Consumer;

import javafx.concurrent.Task;

import gradezzy.model.Anomaly;
import gradezzy.model.GradingRun;
import gradezzy.ui.backend.ActionResult;
import gradezzy.ui.backend.Capability;
import gradezzy.ui.backend.DashboardBackend;
import gradezzy.ui.backend.RepairItem;

/**
 * A background task that undoes one fix or several, or applies one
 * again, and grades each student again.
 *
 * <p>Tier: SHOULD.
 */
public class UndoFixJob extends Task<ActionResult> {

    // The name of the thread the job runs on
    private static final String THREAD_NAME = "gradezzy-fix";

    // The backend that undoes the fix
    private final DashboardBackend backend;

    // The fixes to undo in order, or the one to apply again
    private final List<RepairItem> repairs = new ArrayList<>();

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
        this.repairs.add(repair);
        this.applyAgain = applyAgain;
    }

    /**
     * Creates the undo of several fixes, done one after another.
     *
     * @param backend the backend that undoes the fixes
     * @param repairs the fixes to undo, in order
     */
    public UndoFixJob(DashboardBackend backend, List<RepairItem> repairs) {
        this.backend = backend;
        this.repairs.addAll(repairs);
        this.applyAgain = false;
    }

    /**
     * Says whether a rejected fix may be applied again.
     *
     * @param backend the backend
     * @param anomaly the anomaly of the rejected fix
     * @return true when Apply fix again may be offered
     */
    public static boolean canApplyAgain(DashboardBackend backend,
            Anomaly anomaly) {
        return backend.supports(Capability.REAPPLY_FIX)
                && backend.canReapplyRepair(anomaly);
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
        return startOnce(state, run, onEnd, null);
    }

    /**
     * Starts the job on a background thread, unless another fix job is
     * still running. Until it ends, the state names the fixes it still
     * has to do, and onStep runs each time one of them is done.
     *
     * @param state the window's state, shared by every screen
     * @param run the run the students belong to
     * @param onEnd what to do with the outcome once the job ends
     * @param onStep what to do after each fix, or null
     * @return true when the job started
     */
    public boolean startOnce(FlowState state, GradingRun run,
            Consumer<ActionResult> onEnd, Runnable onStep) {
        if (state.getFixInProgress() != null) {
            return false;
        }

        setOnSucceeded(event -> ended(state, run, onEnd));
        setOnFailed(event -> ended(state, run, onEnd));
        if (onStep != null) {
            workDoneProperty().addListener(
                    (value, before, now) -> stepEnded(state, onStep));
        }
        state.setFixesInProgress(anomaliesFrom(0));
        // A daemon thread does not keep GradeZZY open
        Thread worker = new Thread(this, THREAD_NAME);
        worker.setDaemon(true);
        worker.start();
        return true;
    }

    /**
     * Undoes the fixes on the calling thread, for the demo, whose undo
     * is instant.
     *
     * @return the outcome
     */
    public ActionResult runHere() {
        return call();
    }

    // Leaves in the state only the fixes not done yet, then tells the
    // screen. A step reported after the end changes nothing.
    private void stepEnded(FlowState state, Runnable onStep) {
        int done = (int) getWorkDone();
        if (isDone() || done < 0) {
            return;
        }
        state.setFixesInProgress(anomaliesFrom(done));
        onStep.run();
    }

    // Returns the anomalies of the fixes from a position to the end.
    private List<Anomaly> anomaliesFrom(int start) {
        List<Anomaly> anomalies = new ArrayList<>();
        for (int index = start; index < repairs.size(); index++) {
            anomalies.add(repairs.get(index).getAnomaly());
        }
        return anomalies;
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
        } else if (repairs.size() > 1) {
            what = "Not every fix was undone: ";
        }
        return what + getException().getMessage();
    }

    /**
     * Undoes the fixes or applies the fix again. JavaFX calls this on a
     * background thread.
     *
     * @return the outcome
     */
    @Override
    protected ActionResult call() {
        if (applyAgain) {
            return backend.reapplyRepair(repairs.get(0));
        }
        if (repairs.size() == 1) {
            return backend.rejectRepair(repairs.get(0));
        }
        return rejectAll();
    }

    // Undoes every fix in turn. One that cannot be undone does not stop
    // the others; the outcome names the first that failed.
    private ActionResult rejectAll() {
        int rejected = 0;
        String firstProblem = "";
        for (int index = 0; index < repairs.size(); index++) {
            ActionResult result = rejectOne(repairs.get(index));
            if (result.isDone()) {
                rejected++;
            } else if (firstProblem.isEmpty()) {
                firstProblem = result.getMessage();
            }
            int done = index + 1;
            updateProgress(done, repairs.size());
        }
        return describeRejected(rejected, firstProblem);
    }

    // Undoes one fix, turning an unexpected error into a failed outcome.
    private ActionResult rejectOne(RepairItem repair) {
        try {
            return backend.rejectRepair(repair);
        } catch (RuntimeException exception) {
            return ActionResult.failed("The fix for "
                    + repair.getAnomaly().getUsername() + " was not "
                    + "undone: " + exception.getMessage());
        }
    }

    // Says how many fixes were undone, such as "Rejected 5 fixes and
    // graded those students again."
    private ActionResult describeRejected(int rejected,
            String firstProblem) {
        if (rejected == 0) {
            return ActionResult.failed("No fix was rejected. "
                    + firstProblem);
        }
        String count = Parts.plural(rejected, "fix", "fixes");
        if (rejected < repairs.size()) {
            count = rejected + " of " + Parts.plural(repairs.size(), "fix",
                    "fixes");
        }
        String message = "Rejected " + count + " and graded those students "
                + "again. Their own files were put back; the original ZIP "
                + "files were never changed.";
        if (!firstProblem.isEmpty()) {
            message = message + " " + firstProblem;
        }
        return ActionResult.done(message);
    }
}
