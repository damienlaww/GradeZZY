/*
 * File: InstallTestersStage.java
 *
 * Purpose: Copies the tester files into the working copy.
 *
 * Where it fits: Fifth stage. After it, each question folder holds its tester.
 */
package gradezzy.grading;

import gradezzy.model.Submission;
import gradezzy.testcase.TesterInstaller;

/**
 * Copies the tester files into the working copy.
 *
 * <p>Tier: MUST. Owner: Zach.
 */
public class InstallTestersStage implements GradingStage {

    /**
     * The name shown while this stage runs.
     */
    public static final String NAME = "Adding testers";

    // The helper that copies testers
    private final TesterInstaller testerInstaller;

    /**
     * Creates a new install testers stage.
     *
     * @param testerInstaller the helper that copies testers
     */
    public InstallTestersStage(TesterInstaller testerInstaller) {
        this.testerInstaller = testerInstaller;
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
     * Copies the tester files into the working copy.
     *
     * @param submission the submission
     * @param context the context
     * @throws GradingException if the operation fails
     */
    @Override
    public void process(Submission submission,
            GradingContext context) throws GradingException {
        // TODO(Zach): implement: calling testerInstaller.install with
        //     context.getTesters()
    }
}
