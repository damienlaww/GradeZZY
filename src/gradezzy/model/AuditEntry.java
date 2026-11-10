/*
 * File: AuditEntry.java
 *
 * Purpose: One line of the audit log.
 *
 * Where it fits: Written by AuditService whenever the instructor does something
 * that changes results, such as approving or undoing a fix. A detail may
 * end with a reference such as " [ref compile-1234]", the id of the flag
 * or run the line is about, so History can tell two lines with the same
 * words apart.
 */
package gradezzy.model;

import java.time.LocalDateTime;

/**
 * One line of the audit log.
 *
 * <p>Tier: SHOULD. Owner: Jing Yuan.
 */
public class AuditEntry {

    // The text before the reference at the end of a detail
    private static final String REFERENCE_START = " [ref ";

    // The text after the reference at the end of a detail
    private static final String REFERENCE_END = "]";

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

    /**
     * Returns the description without the reference at its end, as the
     * window shows it.
     *
     * @return the description to show
     */
    public String getShownDetail() {
        int start = referenceStart();
        if (start < 0) {
            return detail;
        }
        return detail.substring(0, start);
    }

    /**
     * Returns the id of the flag or run the line is about, as added by
     * withReference.
     *
     * @return the id, or an empty text for a line with no reference
     */
    public String getReference() {
        int start = referenceStart();
        if (start < 0) {
            return "";
        }
        int idStart = start + REFERENCE_START.length();
        int idEnd = detail.length() - REFERENCE_END.length();
        return detail.substring(idStart, idEnd);
    }

    /**
     * Adds a reference to the end of a description, such as the id of
     * the flag the line is about.
     *
     * @param detail the description
     * @param reference the id; null or empty adds nothing
     * @return the description with its reference
     */
    public static String withReference(String detail, String reference) {
        if (reference == null || reference.isEmpty()) {
            return detail;
        }
        return detail + REFERENCE_START + reference + REFERENCE_END;
    }

    // Finds where the reference starts, or -1 when there is none.
    private int referenceStart() {
        if (detail == null || !detail.endsWith(REFERENCE_END)) {
            return -1;
        }
        return detail.lastIndexOf(REFERENCE_START);
    }
}
