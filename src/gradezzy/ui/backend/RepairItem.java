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
 *
 * <p>Tier: MUST.
 */
public class RepairItem {

    /** The note for a finding that has no repair, when no reason is known. */
    public static final String NO_REPAIR_NOTE = "No automatic fix was "
            + "made for this. Handle it by hand.";

    // The anomaly the finding was recorded as
    private final Anomaly anomaly;

    // What the student submitted
    private final String originalValue;

    // What the working copy holds after the repair
    private final String proposedValue;

    // Why no repair was made, shown when the finding is handled by hand
    private final String note;

    /**
     * Creates a repair item with the usual note for a finding that has
     * no repair.
     *
     * @param anomaly the finding's anomaly
     * @param originalValue what the student submitted
     * @param proposedValue what the working copy holds after the repair
     */
    public RepairItem(Anomaly anomaly, String originalValue,
            String proposedValue) {
        this(anomaly, originalValue, proposedValue, NO_REPAIR_NOTE);
    }

    /**
     * Creates a repair item that says why no repair was made.
     *
     * @param anomaly the finding's anomaly
     * @param originalValue what the student submitted
     * @param proposedValue what the working copy holds after the repair
     * @param note why no repair was made, such as why a fix failed
     */
    public RepairItem(Anomaly anomaly, String originalValue,
            String proposedValue, String note) {
        this.anomaly = anomaly;
        this.originalValue = originalValue;
        this.proposedValue = proposedValue;
        this.note = note;
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
     * Returns why no repair was made, for a finding handled by hand.
     *
     * @return the note, such as the reason an automatic fix failed
     */
    public String getNote() {
        return note;
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
