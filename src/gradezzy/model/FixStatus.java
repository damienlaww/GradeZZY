/*
 * File: FixStatus.java
 *
 * Purpose: The state of an automatic correction.
 *
 * Where it fits: Changed when a fixer runs and when the instructor approves or
 * undoes the fix in the anomaly log.
 */
package gradezzy.model;

/**
 * The state of an automatic correction.
 *
 * <p>Tier: MUST. Owner: Jing Yuan.
 */
public enum FixStatus {
    /**
     * No automatic fix exists for this anomaly.
     */
    NOT_FIXABLE,

    /**
     * A fix exists but has not been applied yet.
     */
    PENDING,

    /**
     * Fixed on the working copy, waiting for review.
     */
    APPLIED,

    /**
     * The instructor accepted the fix.
     */
    APPROVED,

    /**
     * The instructor reverted the fix.
     */
    UNDONE
}
