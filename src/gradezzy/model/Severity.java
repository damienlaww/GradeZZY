/*
 * File: Severity.java
 *
 * Purpose: How serious an anomaly is.
 *
 * Where it fits: Used by the anomaly log to sort and colour entries.
 */
package gradezzy.model;

/**
 * How serious an anomaly is.
 *
 * <p>Tier: SHOULD. Owner: Jing Yuan.
 */
public enum Severity {
    /**
     * For information only; no marks are affected.
     */
    INFO,

    /**
     * The instructor should take a look.
     */
    WARNING,

    /**
     * Grading of this submission was affected.
     */
    ERROR
}
