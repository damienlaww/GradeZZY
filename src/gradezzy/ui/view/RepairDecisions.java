/*
 * File: RepairDecisions.java
 *
 * Purpose: Applies the instructor's approve, reject and undo decisions on
 * the Validation screen.
 *
 * Where it fits: The Validation screen hands its selection here and
 * redraws afterwards. Outcomes are shown through ReviewDecisions.
 */
package gradezzy.ui.view;

import java.util.List;

import gradezzy.ui.AppShell;
import gradezzy.ui.backend.Capability;
import gradezzy.ui.backend.DashboardBackend;
import gradezzy.ui.backend.RepairItem;
import gradezzy.ui.component.Labels;

/**
 * Repair decisions with confirmations and one report per action.
 */
public final class RepairDecisions {

    // The word before the findings named in an audit entry
    private static final String FINDINGS = "Findings";

    // Applies the decisions
    private final DashboardBackend backend;

    // Shows confirmations and sheets
    private final AppShell shell;

    // Shows and reports the outcomes
    private final ReviewDecisions decisions;

    /**
     * Creates the repair decision helper.
     *
     * @param backend the backend
     * @param shell the window
     * @param decisions shows the outcomes
     */
    public RepairDecisions(DashboardBackend backend, AppShell shell,
            ReviewDecisions decisions) {
        this.backend = backend;
        this.shell = shell;
        this.decisions = decisions;
    }

    /**
     * Applies an action to the selected findings that can take it.
     *
     * @param selected the selected findings
     * @param action the decision
     * @param afterChange redraws the screen
     */
    public void decideSelected(List<RepairItem> selected,
            RepairAction action, Runnable afterChange) {
        List<RepairItem> chosen = action.keep(selected);
        if (chosen.isEmpty()) {
            shell.showError(action.explainNothingToDo(selected));
            return;
        }
        decide(chosen, action, afterChange);
    }

    /**
     * Applies an action to the finding in the inspector, if it can.
     *
     * @param focused the focused finding, as a list of zero or one
     * @param action the decision
     * @param afterChange redraws the screen
     */
    public void decideFocused(List<RepairItem> focused, RepairAction action,
            Runnable afterChange) {
        List<RepairItem> chosen = action.keep(focused);
        if (!chosen.isEmpty()) {
            decide(chosen, action, afterChange);
        }
    }

    /**
     * Returns every decided repair of the run to Applied, after asking.
     *
     * @param repairs every finding of the run
     * @param afterChange redraws the screen
     */
    public void undoAll(List<RepairItem> repairs, Runnable afterChange) {
        List<RepairItem> chosen = RepairAction.UNDO.keep(repairs);
        if (chosen.isEmpty()) {
            shell.showToast("No repair in this run has a decision to undo.");
            return;
        }
        if (!backend.supports(Capability.REPAIR_DECISION_UNDO)) {
            shell.showNotConnected(Capability.REPAIR_DECISION_UNDO);
            return;
        }
        shell.confirm("Undo every repair decision?", "Every approved or "
                + "rejected repair in this run (" + chosen.size()
                + " in total) goes back to Applied, waiting for review. "
                + "The original ZIP files are never changed.", "Undo All",
                true, () -> apply(chosen, RepairAction.UNDO, afterChange));
    }

    // Applies an action; rejecting asks first, as it changes the working copy.
    private void decide(List<RepairItem> chosen, RepairAction action,
            Runnable afterChange) {
        if (action != RepairAction.REJECT) {
            apply(chosen, action, afterChange);
            return;
        }
        if (!backend.supports(Capability.REPAIR_DECISIONS)) {
            shell.showNotConnected(Capability.REPAIR_DECISIONS);
            return;
        }
        int count = chosen.size();
        String title = "Reject this repair?";
        if (count > 1) {
            title = "Reject " + count + " repairs?";
        }
        shell.confirm(title, "Rejecting undoes the repair on the student's "
                + "working copy. The original ZIP was never changed, and "
                + "you can undo this decision later.", "Reject Repair"
                + Labels.plural(count), true,
                () -> apply(chosen, action, afterChange));
    }

    // Applies one action to some repairs, then reports the outcome once.
    private void apply(List<RepairItem> chosen, RepairAction action,
            Runnable afterChange) {
        Capability capability = action.getCapability();
        if (!backend.supports(capability)) {
            shell.showNotConnected(capability);
            return;
        }
        if (chosen.size() == 1) {
            decisions.show(action.apply(backend, chosen.get(0)),
                    action.getAuditName());
            afterChange.run();
            return;
        }

        ReviewOutcome outcome = new ReviewOutcome();
        int index = 0;
        while (index < chosen.size() && !outcome.isStopped()) {
            RepairItem repair = chosen.get(index);
            outcome.record(repair.getAnomaly(), action.apply(backend, repair));
            index++;
        }
        decisions.report(outcome, action.getAuditName(),
                action.describe(outcome.getChangedCount()), FINDINGS);
        afterChange.run();
    }
}
