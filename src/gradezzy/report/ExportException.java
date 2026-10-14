/*
 * File: ExportException.java
 *
 * Purpose: Signals that an export could not be written.
 *
 * Where it fits: Thrown by exporters and shown to the instructor with the file
 * name.
 */
package gradezzy.report;

/**
 * Signals that an export could not be written.
 *
 * <p>Tier: MUST. Owner: Huxley.
 */
public class ExportException extends Exception {

    /**
     * Creates the exception with a message for the instructor.
     *
     * @param message what went wrong, in plain words
     */
    public ExportException(String message) {
        super(message);
    }

    /**
     * Creates the exception and keeps the original cause.
     *
     * @param message what went wrong, in plain words
     * @param cause the lower level exception that triggered this
     */
    public ExportException(String message, Throwable cause) {
        super(message, cause);
    }
}
