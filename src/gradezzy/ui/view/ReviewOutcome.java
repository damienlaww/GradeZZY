/*
 * File: ReviewOutcome.java
 *
 * Purpose: Collects what happened when one action was applied to several
 * flags or repairs: which changed, the first problem, and whether a
 * backend was missing.
 *
 * Where it fits: ReviewDecisions fills one for bulk flag decisions, and
 * the Validation screen fills one for bulk repair decisions. The outcome
 * is then reported once, so the instructor sees one message, one audit
 * entry and at most one Not connected sheet.
 *
 * AI use: drafted with Claude Code, reviewed by Jun Sheng.
 */
package gradezzy.ui.view;

import java.util.ArrayList;
import java.util.List;

import gradezzy.model.Anomaly;
import gradezzy.ui.backend.ActionResult;
import gradezzy.ui.backend.Capability;
import gradezzy.ui.component.Labels;

/**
 * The outcome of one action on several anomalies.
 *
 * <p>Tier: SHOULD. Owner: Damien.
 */
public final class ReviewOutcome {

    // The most anomalies named in one audit entry
    private static final int MAX_LISTED = 5;

    // The anomalies the action changed
    private final List<Anomaly> changed = new ArrayList<>();

    // The missing backend feature, or null while every call was answered
    private Capability missing = null;

    // The first problem reported, or an empty string
    private String problem = "";

    /**
     * Creates an outcome with nothing recorded yet.
     */
    public ReviewOutcome() {
        // nothing to set up
    }

    /**
     * Records the result of the action on one anomaly.
     *
     * @param anomaly the flag or the finding of a repair
     * @param result what the backend answered
     */
    public void record(Anomaly anomaly, ActionResult result) {
        if (result.getOutcome() == ActionResult.Outcome.NOT_CONNECTED) {
            missing = result.getCapability();
        } else if (result.isDone()) {
            changed.add(anomaly);
        } else if (problem.isEmpty()) {
            problem = result.getMessage();
        }
    }

    /**
     * Says whether the action must stop, because no backend provides it.
     *
     * @return true once a call came back Not connected
     */
    public boolean isStopped() {
        return missing != null;
    }

    /**
     * Says whether nothing happened at all: nothing changed, nothing
     * failed and no backend was missing.
     *
     * @return true when there is nothing to report
     */
    public boolean isEmpty() {
        return changed.isEmpty() && missing == null && problem.isEmpty();
    }

    /**
     * Returns how many anomalies the action changed.
     *
     * @return the count
     */
    public int getChangedCount() {
        return changed.size();
    }

    /**
     * Returns the missing backend feature.
     *
     * @return the capability, or null when every call was answered
     */
    public Capability getMissing() {
        return missing;
    }

    /**
     * Returns the first problem reported.
     *
     * @return the message, or an empty string
     */
    public String getProblem() {
        return problem;
    }

    /**
     * Names the first few changed anomalies, for the audit log.
     *
     * @param label what they are, such as "Flags"
     * @return the text, such as "Flags: learner.bravo (Timed out)."
     */
    public String listChanged(String label) {
        List<String> names = new ArrayList<>();
        int count = Math.min(MAX_LISTED, changed.size());
        for (int index = 0; index < count; index++) {
            Anomaly anomaly = changed.get(index);
            names.add(anomaly.getUsername() + " ("
                    + Labels.anomalyTitle(anomaly.getType()) + ")");
        }
        String text = label + ": " + String.join(", ", names);
        int more = changed.size() - count;
        if (more > 0) {
            text += " and " + more + " more";
        }
        return text + ".";
    }
}
