/*
 * File: ConsoleProgressListener.java
 *
 * Purpose: Prints grading progress to the terminal.
 *
 * Where it fits: The console counterpart of the progress screen. It lets
 * the pipeline be watched and tested before any window exists.
 */
package gradezzy.app;

import gradezzy.grading.ProgressListener;
import gradezzy.model.Anomaly;
import gradezzy.model.GradingRun;
import gradezzy.model.Submission;

/**
 * Prints grading progress to the terminal.
 *
 * <p>Tier: MUST. Owner: Jing Yuan.
 */
public class ConsoleProgressListener implements ProgressListener {

    // How many submissions the run has
    private int totalSubmissions = 0;

    // How many submissions are finished
    private int finishedSubmissions = 0;

    /**
     * Creates a new console progress listener.
     */
    public ConsoleProgressListener() {
        // nothing to set up
    }

    /**
     * Prints the size of the batch.
     *
     * @param totalSubmissions the number of submissions in the run
     */
    @Override
    public void onRunStarted(int totalSubmissions) {
        this.totalSubmissions = totalSubmissions;
        this.finishedSubmissions = 0;
        System.out.println("Grading " + totalSubmissions
                + " submission(s)...");
    }

    /**
     * Prints the submission that is about to be graded.
     *
     * @param submission the submission being started
     */
    @Override
    public void onSubmissionStarted(Submission submission) {
        System.out.println("[" + (finishedSubmissions + 1) + "/"
                + totalSubmissions + "] " + submission.getUsername());
    }

    /**
     * Prints the stage that is about to run.
     *
     * @param submission the submission being graded
     * @param stageName the name of the stage
     */
    @Override
    public void onStageStarted(Submission submission, String stageName) {
        System.out.println("    " + stageName);
    }

    /**
     * Prints the status of a finished submission and each of its
     * anomalies, so the reason for a FAILED submission is shown.
     *
     * @param submission the submission that is done
     */
    @Override
    public void onSubmissionFinished(Submission submission) {
        finishedSubmissions++;
        System.out.println("    -> " + submission.getStatus() + ", "
                + submission.getAnomalies().size() + " anomaly(ies)");

        for (Anomaly anomaly : submission.getAnomalies()) {
            System.out.println("       " + anomaly.getType() + ": "
                    + anomaly.getMessage());
        }
    }

    /**
     * Prints a closing line for the run.
     *
     * @param gradingRun the run that has finished
     */
    @Override
    public void onRunFinished(GradingRun gradingRun) {
        System.out.println("Finished run " + gradingRun.getId() + ".");
    }
}
