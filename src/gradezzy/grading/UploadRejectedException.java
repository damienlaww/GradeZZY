/*
 * File: UploadRejectedException.java
 *
 * Purpose: Signals that a stage stopped a submission because of the upload
 * itself, after recording the reason as an anomaly.
 *
 * Where it fits: Thrown by ExtractStage for a damaged, unsafe or oversized
 * zip. GradingPipeline marks the submission FAILED without adding an
 * INTERNAL_ERROR, because GradeZZY itself did not fail.
 */
package gradezzy.grading;

/**
 * Signals that a stage stopped a submission because of the upload itself.
 * The stage has already added an anomaly that explains the problem.
 *
 * <p>Tier: MUST. Owner: Damien.
 */
public class UploadRejectedException extends GradingException {

    /**
     * Creates the exception with a message for the instructor.
     *
     * @param message what is wrong with the upload, in plain words
     * @param cause the lower level exception that showed the problem
     */
    public UploadRejectedException(String message, Throwable cause) {
        super(message, cause);
    }
}
