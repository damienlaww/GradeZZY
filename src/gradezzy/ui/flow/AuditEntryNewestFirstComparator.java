/*
 * File: AuditEntryNewestFirstComparator.java
 *
 * Purpose: Orders activity log entries with the newest first.
 *
 * Where it fits: HomeScreen and RunsScreen sort the activity log with it
 * before showing it.
 */
package gradezzy.ui.flow;

import java.util.Comparator;

import gradezzy.model.AuditEntry;

/**
 * Compares two activity log entries by when they happened.
 */
public class AuditEntryNewestFirstComparator
        implements Comparator<AuditEntry> {

    /**
     * Creates a comparator that puts the newest entry first.
     */
    public AuditEntryNewestFirstComparator() {
    }

    /**
     * Puts the entry with the later timestamp first.
     *
     * @param first one entry
     * @param second the other entry
     * @return a negative number when first goes first, a positive number
     *         when second goes first
     */
    @Override
    public int compare(AuditEntry first, AuditEntry second) {
        return second.getTimestamp().compareTo(first.getTimestamp());
    }
}
