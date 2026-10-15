/*
 * File: CapabilityStatus.java
 *
 * Purpose: Says how far a backend feature is from working in the live
 * application.
 *
 * Where it fits: Every Capability has one status. The Help screen shows
 * it in the Backend connections table, and LiveBackend uses it to decide
 * whether a screen may call the feature at all.
 *
 * AI use: drafted with Claude Code, reviewed by Jun Sheng.
 */
package gradezzy.ui.backend;

/**
 * How ready a backend feature is.
 *
 * <p>Tier: MUST. Owner: Damien.
 */
public enum CapabilityStatus {

    /** The service method is written and returns real data. */
    WORKS_NOW("Works now"),

    /**
     * The service method exists but still holds a placeholder, so the
     * screen shows an empty state until its owner finishes it.
     */
    WAITING("Waiting for backend"),

    /**
     * No service method exists yet. The screen shows a Not connected yet
     * panel instead of calling anything.
     */
    PROPOSED("Proposed, no contract yet");

    // The words shown on the Help screen
    private final String label;

    // Creates a status constant.
    CapabilityStatus(String label) {
        this.label = label;
    }

    /**
     * Returns the words shown for this status.
     *
     * @return the label
     */
    public String getLabel() {
        return label;
    }
}
