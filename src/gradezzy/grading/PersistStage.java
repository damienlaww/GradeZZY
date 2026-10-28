/*
 * File: PersistStage.java
 *
 * Purpose: Saves the result of a submission so a run can be resumed.
 *
 * Where it fits: Last stage. Tier SHOULD: skipped when the database is
 * disabled.
 */
package gradezzy.grading;

import java.util.UUID;

import gradezzy.config.ConfigKeys;
import gradezzy.model.Anomaly;
import gradezzy.model.AnomalyType;
import gradezzy.model.Severity;
import gradezzy.model.Submission;
import gradezzy.model.SubmissionStatus;
import gradezzy.persistence.PersistenceException;
import gradezzy.persistence.ResultRepository;

/**
 * Saves the result of a submission so a run can be resumed.
 *
 * <p>Tier: MUST. Owner: Huxley.
 */
public class PersistStage implements GradingStage {

    /**
     * The name shown while this stage runs.
     */
    public static final String NAME = "Saving";

    // Start of the id of the warning added when saving fails
    private static final String ID_PREFIX = "persist-";

    private static final String SAVE_FAILED_ACTION = "Export the results "
            + "before closing the app, then check database.file in the "
            + "settings.";

    // The store results are saved to
    private final ResultRepository repository;

    /**
     * Creates a new persist stage.
     *
     * @param repository the store results are saved to
     */
    public PersistStage(ResultRepository repository) {
        this.repository = repository;
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
     * Saves the result of a submission so a run can be resumed.
     *
     * <p>A failed save never fails the submission: its marks stay in
     * memory and a warning tells the instructor to export them.
     *
     * @param submission the submission
     * @param context the context
     * @throws GradingException if the operation fails
     */
    @Override
    public void process(Submission submission,
            GradingContext context) throws GradingException {
        if (!context.getConfig().getBoolean(ConfigKeys.DATABASE_ENABLED)) {
            return;
        }

        // A pipeline built without a run, as in some tests, has nowhere
        // to save to
        String runId = context.getRunId();
        if (runId == null) {
            return;
        }

        // This is the last stage, so every earlier stage succeeded. The
        // pipeline marks the submission GRADED as soon as this returns;
        // marking it first means the saved row says GRADED, not RUNNING.
        submission.setStatus(SubmissionStatus.GRADED);
        try {
            repository.saveSubmission(runId, submission);
        } catch (PersistenceException exception) {
            submission.addAnomaly(new Anomaly(ID_PREFIX + UUID.randomUUID(),
                    AnomalyType.INTERNAL_ERROR, Severity.WARNING,
                    submission.getUsername(), "The marks could not be "
                    + "saved to the database: " + exception.getMessage()
                    + " They are kept until the app is closed.",
                    SAVE_FAILED_ACTION));
        }
    }
}
