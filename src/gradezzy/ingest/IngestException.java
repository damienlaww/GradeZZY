/*
 * File: IngestException.java
 *
 * Purpose: Signals that an upload could not be read or unpacked.
 *
 * Where it fits: Thrown by the ingest classes and turned into an anomaly by the
 * pipeline, so one bad zip never stops the batch.
 */
package gradezzy.ingest;

/**
 * Signals that an upload could not be read or unpacked.
 *
 * <p>Tier: MUST. Owner: Damien.
 */
public class IngestException extends Exception {

    /**
     * Creates the exception with a message for the instructor.
     *
     * @param message what went wrong, in plain words
     */
    public IngestException(String message) {
        super(message);
    }

    /**
     * Creates the exception and keeps the original cause.
     *
     * @param message what went wrong, in plain words
     * @param cause the lower level exception that triggered this
     */
    public IngestException(String message, Throwable cause) {
        super(message, cause);
    }
}
