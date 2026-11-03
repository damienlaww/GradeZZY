/*
 * File: InvalidArchiveException.java
 *
 * Purpose: Signals that a zip file cannot be graded because of the zip
 * itself: it is damaged, unsafe, too big or holds clashing names.
 *
 * Where it fits: Thrown by ZipExtractor. ExtractStage turns it into an
 * INVALID_ARCHIVE anomaly, so the instructor sees a problem with the upload
 * instead of a failure of GradeZZY.
 */
package gradezzy.ingest;

/**
 * Signals that a zip file cannot be graded because of the zip itself. The
 * message is one plain sentence for the instructor, with no file paths.
 *
 * <p>Tier: MUST. Owner: Damien.
 */
public class InvalidArchiveException extends IngestException {

    /**
     * Creates the exception with a message for the instructor.
     *
     * @param message what is wrong with the zip, in plain words
     */
    public InvalidArchiveException(String message) {
        super(message);
    }

    /**
     * Creates the exception and keeps the original cause.
     *
     * @param message what is wrong with the zip, in plain words
     * @param cause the lower level exception that showed the problem
     */
    public InvalidArchiveException(String message, Throwable cause) {
        super(message, cause);
    }
}
