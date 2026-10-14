/*
 * File: ExecuteStage.java
 *
 * Purpose: Runs each tester with a time limit and parses its output.
 *
 * Where it fits: Seventh stage. A tester that hangs is stopped and recorded as
 * a TIMEOUT anomaly.
 */
package gradezzy.grading;

import gradezzy.exec.TesterRunner;
import gradezzy.model.Submission;

/**
 * Runs each tester with a time limit and parses its output.
 *
 * <p>Tier: MUST. Owner: Zach.
 */
public class ExecuteStage implements GradingStage {

    /**
     * The name shown while this stage runs.
     */
    public static final String NAME = "Running testers";

    // The helper that runs java
    private final TesterRunner testerRunner;

    // The helper that reads tester output
    private final ScoreParser scoreParser;

    /**
     * Creates a new execute stage.
     *
     * @param testerRunner the helper that runs java
     * @param scoreParser the helper that reads tester output
     */
    public ExecuteStage(TesterRunner testerRunner, ScoreParser scoreParser) {
        this.testerRunner = testerRunner;
        this.scoreParser = scoreParser;
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
     * Runs each tester with a time limit and parses its output.
     *
     * @param submission the submission
     * @param context the context
     * @throws GradingException if the operation fails
     */
    @Override
    public void process(Submission submission,
            GradingContext context) throws GradingException {
        // TODO(Zach): implement: running the tester of each compiled question
        //     and passing the output to scoreParser
    }
}
