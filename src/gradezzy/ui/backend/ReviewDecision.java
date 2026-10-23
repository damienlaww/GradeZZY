/*
 * File: ReviewDecision.java
 *
 * Purpose: The instructor's decision about one flagged anomaly.
 *
 * Where it fits: Set on the Review Anomalies screen. A decision never
 * changes a mark.
 */
package gradezzy.ui.backend;

/**
 * A review decision about one anomaly.
 */
public enum ReviewDecision {

    /** Nobody has looked at the flag yet. */
    OPEN("Open"),

    /** The instructor looked at the flag; it needs no action. */
    REVIEWED("Reviewed"),

    /** The instructor decided the flag does not apply. */
    REJECTED("Flag rejected"),

    /** A retry removed the cause of the flag. */
    RESOLVED("Resolved by retry");

    // The words shown next to the flag
    private final String label;

    // Creates a decision constant.
    ReviewDecision(String label) {
        this.label = label;
    }

    /**
     * Returns the words shown next to the flag.
     *
     * @return the label
     */
    public String getLabel() {
        return label;
    }
}
