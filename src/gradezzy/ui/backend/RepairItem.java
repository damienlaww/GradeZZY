/*
 * File: RepairItem.java
 *
 * Purpose: One validation finding and the repair proposed for it.
 *
 * Where it fits: DashboardBackend.listRepairs builds these from a run's
 * anomalies, and the student panel on Review lists them.
 */
package gradezzy.ui.backend;

import gradezzy.model.Anomaly;
import gradezzy.model.FixStatus;

/**
 * One validation finding and its proposed repair.
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
     * @param anomaly the finding's anomaly
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
     * Says whether no automatic repair exists, so it is handled by hand.
     *
     * @return true when the fix status is NOT_FIXABLE
     */
    public boolean isBlocked() {
        return anomaly.getFixStatus() == FixStatus.NOT_FIXABLE;
    }
}
