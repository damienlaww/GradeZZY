/*
 * File: ExtractStage.java
 *
 * Purpose: Creates the working copy of a submission and matches it to a student
 * on the roster.
 *
 * Where it fits: First stage. After it, the submission has a working copy path.
 */
package gradezzy.grading;

import java.io.UncheckedIOException;
import java.nio.file.Path;
import java.util.UUID;

import gradezzy.ingest.IngestException;
import gradezzy.ingest.InvalidArchiveException;
import gradezzy.ingest.RosterReader;
import gradezzy.ingest.WorkingCopyManager;
import gradezzy.model.Anomaly;
import gradezzy.model.AnomalyType;
import gradezzy.model.Severity;
import gradezzy.model.Student;
import gradezzy.model.Submission;

/**
 * Creates the working copy of a submission and matches it to a student on the
 * roster.
 *
 * <p>Tier: MUST. Owner: Damien.
 */
public class ExtractStage implements GradingStage {

    /**
     * The name shown while this stage runs.
     */
    public static final String NAME = "Unpacking";

    // Start of the id of an unknown-student anomaly
    private static final String UNKNOWN_ID_PREFIX = "UNKNOWN-";

    // Start of the id of an anomaly for a zip that cannot be unpacked
    private static final String ARCHIVE_ID_PREFIX = "ARCHIVE-";

    // What the instructor can do about a zip that cannot be unpacked
    private static final String NEW_UPLOAD_ACTION =
            "Ask the student for a new copy and add it to a new batch.";

    // The helper that creates working copies
    private final WorkingCopyManager workingCopyManager;

    // The helper that looks students up
    private final RosterReader rosterReader;

    // The starter folder chosen for the batch, or null for the settings
    private final Path starterDirectory;

    /**
     * Creates a new extract stage that takes provided class files from the
     * starter folder of paths.starter.dir.
     *
     * @param workingCopyManager the helper that creates working copies
     * @param rosterReader the helper that looks students up
     */
    public ExtractStage(WorkingCopyManager workingCopyManager,
            RosterReader rosterReader) {
        this(workingCopyManager, rosterReader, null);
    }

    /**
     * Creates a new extract stage that takes provided class files, such as
     * DataException.class, from the starter folder chosen for the batch.
     *
     * <p>Tier: MUST.
     *
     * @param workingCopyManager the helper that creates working copies
     * @param rosterReader the helper that looks students up
     * @param starterDirectory the starter folder chosen for the batch, or
     *        null for paths.starter.dir
     */
    public ExtractStage(WorkingCopyManager workingCopyManager,
            RosterReader rosterReader, Path starterDirectory) {
        this.workingCopyManager = workingCopyManager;
        this.rosterReader = rosterReader;
        this.starterDirectory = starterDirectory;
    }

    /**
     * Returns the name of this stage.
     *
     * @return the name of this stage
     */
    @Override
    public String getName() {
        return NAME;
    }

    /**
     * Creates the working copy of a submission and matches it to a student on
     * the roster. A zip that is damaged, unsafe or too big gets an
     * INVALID_ARCHIVE anomaly and stops this submission.
     *
     * @param submission the submission
     * @param context the context
     * @throws UploadRejectedException if the zip itself cannot be used
     * @throws GradingException if the operation fails
     */
    @Override
    public void process(Submission submission,
            GradingContext context) throws GradingException {
        if (submission.getIngestFailure() != null) {
            throw new GradingException(submission.getIngestFailure());
        }
        Student matched = rosterReader.findByUsername(context.getRoster(),
                submission.getUsername());
        // A student matched to the score sheet by hand, such as an upload
        // with a typo in its name, keeps that match when graded again
        // while that student is still on the score sheet
        if (matched == null && submission.getStudent() != null) {
            matched = rosterReader.findByUsername(context.getRoster(),
                    submission.getStudent().getUsername());
        }
        submission.setStudent(matched);
        if (submission.getStudent() == null) {
            submission.addAnomaly(new Anomaly(
                    UNKNOWN_ID_PREFIX + UUID.randomUUID(),
                    AnomalyType.UNKNOWN_STUDENT, Severity.WARNING,
                    submission.getUsername(), submission.getUsername()
                            + " is not on the roster.",
                    "Check the upload filename and the selected roster."));
        }
        try {
            Path workingCopy = workingCopyManager.createWorkingCopy(
                    submission);
            submission.setWorkingCopyPath(workingCopy);
            workingCopyManager.removeStudentClassFiles(workingCopy,
                    context.getAssessment(), starterDirectory);
        } catch (InvalidArchiveException exception) {
            throw rejectUpload(submission, exception);
        } catch (IngestException exception) {
            throw prepareFailure(submission, exception);
        } catch (UncheckedIOException exception) {
            throw prepareFailure(submission, exception);
        }
    }

    // Records why the zip cannot be used, in the extractor's own plain
    // words, and builds the error that stops this submission.
    private UploadRejectedException rejectUpload(Submission submission,
            InvalidArchiveException exception) {
        submission.addAnomaly(new Anomaly(
                ARCHIVE_ID_PREFIX + UUID.randomUUID(),
                AnomalyType.INVALID_ARCHIVE, Severity.ERROR,
                submission.getUsername(), exception.getMessage(),
                NEW_UPLOAD_ACTION));
        return new UploadRejectedException(exception.getMessage(),
                exception);
    }

    // Builds the error for a working copy that could not be prepared.
    private GradingException prepareFailure(Submission submission,
            Exception exception) {
        return new GradingException("Could not prepare "
                + submission.getUsername() + ": "
                + exception.getMessage(), exception);
    }
}
