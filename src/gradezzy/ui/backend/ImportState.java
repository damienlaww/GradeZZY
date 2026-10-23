/*
 * File: ImportState.java
 *
 * Purpose: Says whether a chosen input file can be used for grading.
 *
 * Where it fits: Each ImportItem has one state, shown on the Submissions
 * screen and used by ImportBatch.
 */
package gradezzy.ui.backend;

/**
 * The state of one chosen input file.
 */
public enum ImportState {

    /** The file can be used. */
    ACCEPTED("Accepted"),

    /** The file can be used, but something about it needs a look. */
    WARNING("Accepted with a warning"),

    /** Another file already covers the same student. */
    DUPLICATE("Duplicate"),

    /** The file cannot be used. */
    INVALID("Cannot be used");

    // The words shown next to the file
    private final String label;

    // Creates a state constant.
    ImportState(String label) {
        this.label = label;
    }

    /**
     * Returns the words shown next to the file.
     *
     * @return the label
     */
    public String getLabel() {
        return label;
    }

    /**
     * Says whether a file in this state can be graded.
     *
     * @return true for ACCEPTED and WARNING
     */
    public boolean isUsable() {
        return this == ACCEPTED || this == WARNING;
    }
}
