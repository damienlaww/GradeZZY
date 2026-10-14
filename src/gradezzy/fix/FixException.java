/*
 * File: FixException.java
 *
 * Purpose: Signals that a fix could not be applied or undone.
 *
 * Where it fits: Thrown by fixers and reported in the anomaly log.
 */
package gradezzy.fix;

/**
 * Signals that a fix could not be applied or undone.
 *
 * <p>Tier: MUST. Owner: Jun Sheng.
 */
public class FixException extends Exception {

    /**
     * Creates the exception with a message for the instructor.
     *
     * @param message what went wrong, in plain words
     */
    public FixException(String message) {
        super(message);
    }

    /**
     * Creates the exception and keeps the original cause.
     *
     * @param message what went wrong, in plain words
     * @param cause the lower level exception that triggered this
     */
    public FixException(String message, Throwable cause) {
        super(message, cause);
    }
}
