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

import gradezzy.ingest.IngestException;
import gradezzy.ingest.RosterReader;
import gradezzy.ingest.WorkingCopyManager;
import gradezzy.model.Submission;
import gradezzy.model.Anomaly;
import gradezzy.model.AnomalyType;
import gradezzy.model.Severity;

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

    // The helper that creates working copies
    private final WorkingCopyManager workingCopyManager;

    // The helper that looks students up
    private final RosterReader rosterReader;

    /**
     * Creates a new extract stage.
     *
     * @param workingCopyManager the helper that creates working copies
     * @param rosterReader the helper that looks students up
     */
    public ExtractStage(WorkingCopyManager workingCopyManager,
            RosterReader rosterReader) {
        this.workingCopyManager = workingCopyManager;
        this.rosterReader = rosterReader;
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
     * the roster.
     *
     * @param submission the submission
     * @param context the context
     * @throws GradingException if the operation fails
     */
    @Override
    public void process(Submission submission,
            GradingContext context) throws GradingException {
        if (submission.getIngestFailure() != null) {
            throw new GradingException(submission.getIngestFailure());
        }
        submission.setStudent(rosterReader.findByUsername(
                context.getRoster(), submission.getUsername()));
        if (submission.getStudent() == null) {
            submission.addAnomaly(new Anomaly("UNKNOWN-"
                    + java.util.UUID.randomUUID(),
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
                    context.getAssessment());
        } catch (IngestException | UncheckedIOException exception) {
            throw new GradingException("Could not prepare "
                    + submission.getUsername() + ": "
                    + exception.getMessage(), exception);
        }
    }
}
