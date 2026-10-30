/*
 * File: GradingTaskKind.java
 *
 * Purpose: What a GradingTask asks GradingService to do.
 *
 * Where it fits: LiveBackend picks a constant when it creates a
 * GradingTask, and the task calls the matching service method.
 */
package gradezzy.ui;

/**
 * What a grading task asks GradingService to do.
 */
public enum GradingTaskKind {

    /** Grade a new batch. */
    START,

    /** Grade the failed and cancelled submissions of a run again. */
    RETRY_FAILED,

    /** Continue a saved run. */
    RESUME
}
