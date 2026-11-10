/*
 * File: GradingException.java
 *
 * Purpose: Signals that a stage could not finish for one submission.
 *
 * Where it fits: Thrown by a GradingStage. GradingPipeline catches it, records
 * an anomaly and moves on to the next submission.
 */
package gradezzy.grading;

/**
 * Signals that a stage could not finish for one submission.
 *
 * <p>Tier: MUST. Owner: Jing Yuan.
 */
public class GradingException extends Exception {

    /**
     * Creates the exception with a message for the instructor.
     *
     * <p>Tier: MUST.
     *
     * @param message what went wrong, in plain words
     */
    public GradingException(String message) {
        super(message);
    }

    /**
     * Creates the exception and keeps the original cause.
     *
     * <p>Tier: MUST.
     *
     * @param message what went wrong, in plain words
     * @param cause the lower level exception that triggered this
     */
    public GradingException(String message, Throwable cause) {
        super(message, cause);
    }
}
