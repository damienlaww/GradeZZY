/*
 * File: ReviewDecisions.java
 *
 * Purpose: Records the instructor's decisions on flags, one or several at
 * a time, keeps the decision each flag had before so it can be undone,
 * and reports the outcome once.
 *
 * Where it fits: Shared by Review Anomalies, Similarity Review and
 * Validation. The decision a flag had before its latest change is kept in
 * UiState, so Undo works from any of these screens. A bulk action shows
 * at most one Not connected sheet, one message and one audit entry. A
 * decision never changes a mark.
 *
 * AI use: drafted with Claude Code, reviewed by Jun Sheng.
 */
package gradezzy.ui.view;

import java.util.ArrayList;
import java.util.List;
import java.util.Map;

import gradezzy.model.Anomaly;
import gradezzy.ui.AppShell;
import gradezzy.ui.UiState;
import gradezzy.ui.backend.ActionResult;
import gradezzy.ui.backend.Capability;
import gradezzy.ui.backend.DashboardBackend;
import gradezzy.ui.backend.ReviewDecision;
import gradezzy.ui.component.Labels;

/**
 * Review decisions with Undo and one report per action.
 *
 * <p>Tier: SHOULD. Owner: Damien.
 */
public final class ReviewDecisions {

    // The word before the flags named in an audit entry
    private static final String FLAGS = "Flags";

    // The backend that stores the decisions
    private final DashboardBackend backend;

    // The state that keeps the earlier decisions for Undo
    private final UiState state;

    // The window that shows messages and sheets
    private final AppShell shell;

    /**
     * Creates the decision helper of one screen.
     *
     * @param backend the backend that stores the decisions
     * @param state the state that keeps the earlier decisions
     * @param shell the window that shows messages and sheets
     */
    public ReviewDecisions(DashboardBackend backend, UiState state,
            AppShell shell) {
        this.backend = backend;
        this.state = state;
        this.shell = shell;
    }

    /**
     * Says whether flag decisions can be stored, and shows the Not
     * connected sheet when they cannot.
     *
     * @return true when a backend stores decisions
     */
    public boolean checkConnected() {
        if (backend.supports(Capability.FLAG_REVIEW)) {
            return true;
        }
        shell.showNotConnected(Capability.FLAG_REVIEW);
        return false;
    }

    /**
     * Returns the decision recorded on a flag.
     *
     * @param anomaly the flag
     * @return the decision
     */
    public ReviewDecision decisionOf(Anomaly anomaly) {
        return backend.getDecision(anomaly);
    }

    /**
     * Stores the decision a flag has now, so it can be undone, then
     * records the new one. The stored decision is put back when the
     * backend did not record the new one.
     *
     * @param anomaly the flag
     * @param decision the new decision
     * @return what the backend answered
     */
    public ActionResult decide(Anomaly anomaly, ReviewDecision decision) {
        Map<String, ReviewDecision> previous = state.getPreviousDecisions();
        String id = anomaly.getId();
        ReviewDecision stored = previous.get(id);
        previous.put(id, backend.getDecision(anomaly));
        ActionResult result = backend.setDecision(anomaly, decision);
        if (!result.isDone()) {
            if (stored == null) {
                previous.remove(id);
            } else {
                previous.put(id, stored);
            }
        }
        return result;
    }

    /**
     * Gives a flag back the decision stored before its latest change.
     *
     * @param anomaly the flag
     * @return what the backend answered
     */
    public ActionResult restore(Anomaly anomaly) {
        Map<String, ReviewDecision> previous = state.getPreviousDecisions();
        ReviewDecision earlier = previous.get(anomaly.getId());
        ActionResult result = backend.setDecision(anomaly, earlier);
        if (result.isDone()) {
            previous.remove(anomaly.getId());
        }
        return result;
    }

    /**
     * Says whether a flag's latest decision can be undone in this
     * session.
     *
     * @param anomaly the flag
     * @return true when an earlier decision is stored
     */
    public boolean canUndo(Anomaly anomaly) {
        return state.getPreviousDecisions().containsKey(anomaly.getId());
    }

    /**
     * Keeps the flags whose latest decision can be undone.
     *
     * @param anomalies the flags
     * @return the flags with an earlier decision stored
     */
    public List<Anomaly> undoable(List<Anomaly> anomalies) {
        List<Anomaly> result = new ArrayList<>();
        for (Anomaly anomaly : anomalies) {
            if (canUndo(anomaly)) {
                result.add(anomaly);
            }
        }
        return result;
    }

    /**
     * Records one decision on several flags. Flags that already have it
     * are left alone, and the work stops when no backend stores
     * decisions.
     *
     * @param anomalies the flags
     * @param decision the new decision
     * @return what happened
     */
    public ReviewOutcome decideAll(List<Anomaly> anomalies,
            ReviewDecision decision) {
        ReviewOutcome outcome = new ReviewOutcome();
        int index = 0;
        while (index < anomalies.size() && !outcome.isStopped()) {
            Anomaly anomaly = anomalies.get(index);
            if (backend.getDecision(anomaly) != decision) {
                outcome.record(anomaly, decide(anomaly, decision));
            }
            index++;
        }
        return outcome;
    }

    /**
     * Gives several flags back their earlier decisions.
     *
     * @param anomalies the flags, each with an earlier decision stored
     * @return what happened
     */
    public ReviewOutcome restoreAll(List<Anomaly> anomalies) {
        ReviewOutcome outcome = new ReviewOutcome();
        int index = 0;
        while (index < anomalies.size() && !outcome.isStopped()) {
            Anomaly anomaly = anomalies.get(index);
            outcome.record(anomaly, restore(anomaly));
            index++;
        }
        return outcome;
    }

    /**
     * Records one decision on the selected flags of Review Anomalies and
     * reports it once, or explains why nothing changed.
     *
     * @param selected the selected flags
     * @param decision the new decision, Reviewed or Rejected
     * @return true when the screen should redraw
     */
    public boolean decideSelected(List<Anomaly> selected,
            ReviewDecision decision) {
        if (selected.isEmpty()) {
            shell.showError("Select one or more flags first.");
            return false;
        }
        if (!checkConnected()) {
            return false;
        }

        ReviewOutcome outcome = decideAll(selected, decision);
        boolean reviewed = decision == ReviewDecision.REVIEWED;
        if (outcome.isEmpty()) {
            if (reviewed) {
                shell.showToast("Nothing changed: the selected flags were "
                        + "already marked reviewed.");
            } else {
                shell.showToast("Nothing changed: the selected flags were "
                        + "already rejected.");
            }
            return false;
        }
        int count = outcome.getChangedCount();
        String flags = count + " flag" + Labels.plural(count);
        if (reviewed) {
            report(outcome, "Flags reviewed", "Marked " + flags
                    + " as reviewed. No mark changed.", FLAGS);
        } else {
            report(outcome, "Flags rejected", "Rejected " + flags
                    + ". No mark changed.", FLAGS);
        }
        return true;
    }

    /**
     * Gives the selected flags of Review Anomalies back their earlier
     * decisions and reports it once.
     *
     * @param selected the selected flags
     * @return true when the screen should redraw
     */
    public boolean undoSelected(List<Anomaly> selected) {
        List<Anomaly> chosen = undoable(selected);
        if (chosen.isEmpty()) {
            shell.showError("None of the selected flags has a decision to "
                    + "undo in this session.");
            return false;
        }
        if (!checkConnected()) {
            return false;
        }
        ReviewOutcome outcome = restoreAll(chosen);
        int count = outcome.getChangedCount();
        report(outcome, "Flag decisions undone", "Restored the earlier "
                + "decision on " + count + " flag" + Labels.plural(count)
                + ". No mark changed.", FLAGS);
        return true;
    }

    /**
     * Records one decision on one flag and shows the backend's message.
     * Nothing happens when the flag is missing or already has it.
     *
     * @param anomaly the flag, or null
     * @param decision the new decision
     * @param auditAction the short audit name, such as "Flag reviewed"
     * @return true when the screen should redraw
     */
    public boolean decideOne(Anomaly anomaly, ReviewDecision decision,
            String auditAction) {
        if (anomaly == null || backend.getDecision(anomaly) == decision) {
            return false;
        }
        if (!checkConnected()) {
            return false;
        }
        show(decide(anomaly, decision), auditAction);
        return true;
    }

    /**
     * Gives one flag back its earlier decision and shows the backend's
     * message. Nothing happens when there is nothing to undo.
     *
     * @param anomaly the flag, or null
     * @param auditAction the short audit name
     * @return true when the screen should redraw
     */
    public boolean undoOne(Anomaly anomaly, String auditAction) {
        if (anomaly == null || !canUndo(anomaly)) {
            return false;
        }
        if (!checkConnected()) {
            return false;
        }
        show(restore(anomaly), auditAction);
        return true;
    }

    /**
     * Shows the outcome of one backend action: a confirmation, a problem
     * or a Not connected sheet. A successful action is recorded in the
     * audit log. The sidebar counts are refreshed afterwards.
     *
     * @param result what the backend answered
     * @param auditAction the short audit name
     */
    public void show(ActionResult result, String auditAction) {
        if (result.getOutcome() == ActionResult.Outcome.NOT_CONNECTED) {
            shell.showNotConnected(result.getCapability());
        } else if (result.getOutcome() == ActionResult.Outcome.FAILED) {
            shell.showError(result.getMessage());
        } else {
            backend.recordAudit(auditAction, result.getMessage());
            state.markDataChanged();
            shell.showToast(result.getMessage());
        }
        shell.refreshContext();
    }

    /**
     * Reports the outcome of one action on several items once: the Not
     * connected sheet or the first problem, then one message and one
     * audit entry when something changed. The sidebar counts are
     * refreshed afterwards.
     *
     * @param outcome what happened
     * @param auditAction the short audit name
     * @param message the message, in the past tense
     * @param listLabel the word before the changed items in the audit
     *        entry, such as "Flags", or an empty string to name none
     */
    public void report(ReviewOutcome outcome, String auditAction,
            String message, String listLabel) {
        if (outcome.getMissing() != null) {
            shell.showNotConnected(outcome.getMissing());
        }
        if (!outcome.getProblem().isEmpty()) {
            shell.showError(outcome.getProblem());
        }
        if (outcome.getChangedCount() > 0) {
            String detail = message;
            if (!listLabel.isEmpty()) {
                detail = message + " " + outcome.listChanged(listLabel);
            }
            backend.recordAudit(auditAction, detail);
            state.markDataChanged();
            shell.showToast(message);
        }
        shell.refreshContext();
    }
}
