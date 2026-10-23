/*
 * File: CapabilityStatus.java
 *
 * Purpose: Says how far a backend feature is from working in the live
 * application.
 *
 * Where it fits: Every Capability has one. The Help screen shows it, and
 * LiveBackend uses it to decide whether a screen may call the feature.
 */
package gradezzy.ui.backend;

/**
 * How ready a backend feature is.
 */
public enum CapabilityStatus {

    /** The service method is written and returns real data. */
    WORKS_NOW("Works now"),

    /** The service method exists but still holds a placeholder. */
    WAITING("Waiting for backend"),

    /** No service method exists yet, so the screen shows Not connected yet. */
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
