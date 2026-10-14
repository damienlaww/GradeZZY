/*
 * File: ProcessExecutionException.java
 *
 * Purpose: Signals that javac or java could not be started.
 *
 * Where it fits: Thrown by the exec classes when the process itself fails,
 * which is different from the student's code failing.
 */
package gradezzy.exec;

/**
 * Signals that javac or java could not be started.
 *
 * <p>Tier: MUST. Owner: Zach.
 */
public class ProcessExecutionException extends Exception {

    /**
     * Creates the exception with a message for the instructor.
     *
     * @param message what went wrong, in plain words
     */
    public ProcessExecutionException(String message) {
        super(message);
    }

    /**
     * Creates the exception and keeps the original cause.
     *
     * @param message what went wrong, in plain words
     * @param cause the lower level exception that triggered this
     */
    public ProcessExecutionException(String message, Throwable cause) {
        super(message, cause);
    }
}
