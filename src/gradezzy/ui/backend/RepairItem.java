/*
 * File: RepairItem.java
 *
 * Purpose: One finding from validation, with the change proposed for the
 * working copy, shown so the instructor can approve or reject it.
 *
 * Where it fits: DashboardBackend.listRepairs builds these from the
 * anomalies of a run. The Validation screen lists them side by side.
 *
 * AI use: drafted with Claude Code, reviewed by Jun Sheng.
 */
package gradezzy.ui.backend;

import gradezzy.model.Anomaly;
import gradezzy.model.FixStatus;

/**
 * One validation finding and its proposed repair.
 *
 * <p>Tier: SHOULD. Owner: Damien.
 */
public class RepairItem {

    // The anomaly the finding was recorded as
    private final Anomaly anomaly;

    // What the student submitted
    private final String originalValue;

    // What the working copy holds after the repair
    private final String proposedValue;

    /**
     * Creates a repair item.
     *
     * @param anomaly the anomaly the finding was recorded as
     * @param originalValue what the student submitted
     * @param proposedValue what the working copy holds after the repair
     */
    public RepairItem(Anomaly anomaly, String originalValue,
            String proposedValue) {
        this.anomaly = anomaly;
        this.originalValue = originalValue;
        this.proposedValue = proposedValue;
    }

    /**
     * Returns the anomaly the finding was recorded as.
     *
     * @return the anomaly
     */
    public Anomaly getAnomaly() {
        return anomaly;
    }

    /**
     * Returns what the student submitted.
     *
     * @return the original value
     */
    public String getOriginalValue() {
        return originalValue;
    }

    /**
     * Returns what the working copy holds after the repair.
     *
     * @return the proposed value
     */
    public String getProposedValue() {
        return proposedValue;
    }

    /**
     * Says whether no automatic repair exists for this finding, so the
     * instructor must handle it by hand.
     *
     * @return true when the fix status is NOT_FIXABLE
     */
    public boolean isBlocked() {
        return anomaly.getFixStatus() == FixStatus.NOT_FIXABLE;
    }
}
