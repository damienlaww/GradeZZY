/*
 * File: ActionOutcome.java
 *
 * Purpose: The three ways a backend action can end.
 *
 * Where it fits: Every ActionResult holds one constant, and a screen
 * reads it to choose between the message and a Not connected yet panel.
 */
package gradezzy.ui.backend;

/**
 * The three possible outcomes of a backend action.
 */
public enum ActionOutcome {

    /** The action happened. */
    DONE,

    /** No backend provides this action yet. */
    NOT_CONNECTED,

    /** The action was tried and did not work. */
    FAILED
}
