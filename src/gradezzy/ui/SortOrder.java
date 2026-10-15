/*
 * File: SortOrder.java
 *
 * Purpose: The ways a list of students or anomalies can be sorted, with
 * the words shown in the Sort menu.
 *
 * Where it fits: The Results and Review Anomalies screens offer these,
 * and UiState remembers the choice of each screen.
 *
 * AI use: drafted with Claude Code, reviewed by Jun Sheng.
 */
package gradezzy.ui;

/**
 * A sort order offered on a list screen.
 *
 * <p>Tier: SHOULD. Owner: Damien.
 */
public enum SortOrder {

    /** Usernames from A to Z. */
    NAME_ASCENDING("Student, A to Z"),

    /** Usernames from Z to A. */
    NAME_DESCENDING("Student, Z to A"),

    /** Highest total first. */
    TOTAL_DESCENDING("Total, highest first"),

    /** Lowest total first. */
    TOTAL_ASCENDING("Total, lowest first"),

    /** Errors first, then warnings, then notes. */
    SEVERITY("Severity, errors first");

    // The words shown in the Sort menu
    private final String label;

    // Creates a sort order constant.
    SortOrder(String label) {
        this.label = label;
    }

    /**
     * Returns the words shown in the Sort menu.
     *
     * @return the label
     */
    @Override
    public String toString() {
        return label;
    }
}
