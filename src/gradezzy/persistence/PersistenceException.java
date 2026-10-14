/*
 * File: PersistenceException.java
 *
 * Purpose: Signals that saved data could not be read or written.
 *
 * Where it fits: Hides SQL errors from the rest of the code.
 */
package gradezzy.persistence;

/**
 * Signals that saved data could not be read or written.
 *
 * <p>Tier: SHOULD. Owner: Huxley.
 */
public class PersistenceException extends Exception {

    /**
     * Creates the exception with a message for the instructor.
     *
     * @param message what went wrong, in plain words
     */
    public PersistenceException(String message) {
        super(message);
    }

    /**
     * Creates the exception and keeps the original cause.
     *
     * @param message what went wrong, in plain words
     * @param cause the lower level exception that triggered this
     */
    public PersistenceException(String message, Throwable cause) {
        super(message, cause);
    }
}
