/*
 * File: ReviewDecision.java
 *
 * Purpose: The instructor's decision about one flagged anomaly.
 *
 * Where it fits: Set from the student panel on Review. A decision never
 * changes a mark.
 */
package gradezzy.ui.backend;

/**
 * A review decision about one anomaly.
 *
 * <p>Tier: SHOULD.
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

    /** The log's words for a flag or problem marked as checked. */
    public static final String AUDIT_CHECKED = "Marked as checked";

    /** The log's words for a flag marked as looking fine. */
    public static final String AUDIT_LOOKS_FINE = "Marked as looks fine";

    /** The log's words for a decision taken back. */
    public static final String AUDIT_TAKEN_BACK = "Decision taken back";

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

    /**
     * Returns the words the activity log uses for this decision.
     *
     * @return such as "Marked as checked"
     */
    public String getAuditAction() {
        if (this == REVIEWED) {
            return AUDIT_CHECKED;
        } else if (this == REJECTED) {
            return AUDIT_LOOKS_FINE;
        }
        return AUDIT_TAKEN_BACK;
    }
}
