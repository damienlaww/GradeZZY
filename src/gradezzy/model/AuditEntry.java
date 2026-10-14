/*
 * File: AuditEntry.java
 *
 * Purpose: One line of the audit log.
 *
 * Where it fits: Written by AuditService whenever the instructor does something
 * that changes results, such as approving or undoing a fix.
 */
package gradezzy.model;

import java.time.LocalDateTime;

/**
 * One line of the audit log.
 *
 * <p>Tier: SHOULD. Owner: Jing Yuan.
 */
public class AuditEntry {

    // The time of the action
    private final LocalDateTime timestamp;

    // A short name for the action
    private final String action;

    // The full description of the action
    private final String detail;

    /**
     * Creates a new audit entry.
     *
     * @param timestamp the time of the action
     * @param action a short name for the action
     * @param detail the full description of the action
     */
    public AuditEntry(LocalDateTime timestamp, String action, String detail) {
        this.timestamp = timestamp;
        this.action = action;
        this.detail = detail;
    }

    /**
     * Returns the time of the action.
     *
     * @return the time of the action
     */
    public LocalDateTime getTimestamp() {
        return timestamp;
    }

    /**
     * Returns a short name for the action.
     *
     * @return a short name for the action
     */
    public String getAction() {
        return action;
    }

    /**
     * Returns the full description of the action.
     *
     * @return the full description of the action
     */
    public String getDetail() {
        return detail;
    }
}
