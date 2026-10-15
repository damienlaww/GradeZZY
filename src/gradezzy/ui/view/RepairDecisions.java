/*
 * File: RepairDecisions.java
 *
 * Purpose: Applies the instructor's repair decisions on the Validation
 * screen: approve, reject or undo, for the selected findings, the
 * focused one or every decided repair of the run. Rejecting and undoing
 * everything ask first.
 *
 * Where it fits: The Validation screen hands its selection here and
 * redraws afterwards. One repair shows the backend's own message; several
 * show one message and one audit entry, through ReviewDecisions. Only
 * working copies change; the original ZIP files never do.
 *
 * AI use: drafted with Claude Code, reviewed by Jun Sheng.
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
 *
 * <p>Tier: SHOULD. Owner: Damien.
 */
public final class RepairDecisions {

    // The word before the findings named in an audit entry
    private static final String FINDINGS = "Findings";

    // The backend that applies the repair decisions
    private final DashboardBackend backend;

    // The window that shows confirmations and sheets
    private final AppShell shell;

    // Shows and reports the outcomes
    private final ReviewDecisions decisions;

    /**
     * Creates the repair decision helper of the Validation screen.
     *
     * @param backend the backend that applies the decisions
     * @param shell the window that shows confirmations and sheets
     * @param decisions shows and reports the outcomes
     */
    public RepairDecisions(DashboardBackend backend, AppShell shell,
            ReviewDecisions decisions) {
        this.backend = backend;
        this.shell = shell;
        this.decisions = decisions;
    }

    /**
     * Applies an action to the selected findings that can take it, or
     * explains why none of them can.
     *
     * @param selected the selected findings
     * @param action the decision
     * @param afterChange redraws the screen once the decision is made
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
     * Applies an action to the finding in the inspector, when it can
     * take it.
     *
     * @param focused the focused finding as a list of zero or one
     * @param action the decision
     * @param afterChange redraws the screen once the decision is made
     */
    public void decideFocused(List<RepairItem> focused, RepairAction action,
            Runnable afterChange) {
        List<RepairItem> chosen = action.keep(focused);
        if (!chosen.isEmpty()) {
            decide(chosen, action, afterChange);
        }
    }

    /**
     * Returns every decided repair of the run to Applied, after a
     * confirmation.
     *
     * @param repairs every finding of the run
     * @param afterChange redraws the screen once the decisions are undone
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

    // Applies an action, asking first when it rejects repairs, because
    // rejecting changes the working copy.
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

    // Applies one action to some repairs, then reports the outcome once:
    // the Not connected sheet or the first problem, then one message and
    // one audit entry for the repairs that changed.
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
