/*
 * File: PersistStage.java
 *
 * Purpose: Saves the result of a submission so a run can be resumed.
 *
 * Where it fits: Last stage. Tier SHOULD: skipped when the database is
 * disabled.
 */
package gradezzy.grading;

import gradezzy.model.Submission;
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
     * @param submission the submission
     * @param context the context
     * @throws GradingException if the operation fails
     */
    @Override
    public void process(Submission submission,
            GradingContext context) throws GradingException {
        // TODO(Huxley): implement: calling repository.saveSubmission when
        //     DATABASE_ENABLED is true
    }
}
