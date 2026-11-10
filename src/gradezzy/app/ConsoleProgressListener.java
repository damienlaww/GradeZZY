/*
 * File: ConsoleProgressListener.java
 *
 * Purpose: Prints grading progress to the terminal.
 *
 * Where it fits: The console counterpart of the Grade step. It lets
 * the pipeline be watched and tested before any window exists.
 */
package gradezzy.app;

import gradezzy.grading.ProgressListener;
import gradezzy.model.Anomaly;
import gradezzy.model.GradingRun;
import gradezzy.model.Submission;

/**
 * Prints grading progress to the terminal. Several submissions can be
 * graded at the same time, so nothing is printed while a submission is
 * being graded: each one is printed as a single block when it finishes,
 * headed by its username, so its lines never mix with another student's.
 *
 * <p>Tier: MUST. Owner: Jing Yuan.
 */
public class ConsoleProgressListener implements ProgressListener {

    // Indent of each problem line under a finished submission
    private static final String PROBLEM_INDENT = "       ";

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
    public synchronized void onRunStarted(int totalSubmissions) {
        this.totalSubmissions = totalSubmissions;
        this.finishedSubmissions = 0;
        System.out.println("Grading " + totalSubmissions
                + " submission(s)...");
    }

    /**
     * Prints nothing: the submission is printed when it finishes.
     *
     * @param submission the submission being started
     */
    @Override
    public void onSubmissionStarted(Submission submission) {
        // See the class comment
    }

    /**
     * Prints nothing: the submission is printed when it finishes.
     *
     * @param submission the submission being graded
     * @param stageName the name of the stage
     */
    @Override
    public void onStageStarted(Submission submission, String stageName) {
        // See the class comment
    }

    /**
     * Prints one block for a finished submission: how many are done, the
     * username, the status and total, then each problem found, so the
     * reason for a FAILED submission is shown. Synchronized so the
     * counter and the block stay whole when several workers finish at
     * once.
     *
     * @param submission the submission that is done
     */
    @Override
    public synchronized void onSubmissionFinished(Submission submission) {
        finishedSubmissions++;
        System.out.println("[" + finishedSubmissions + "/"
                + totalSubmissions + "] " + submission.getUsername()
                + ": " + submission.getStatus() + ", total "
                + submission.getTotalScore() + ", "
                + submission.getAnomalies().size() + " problem(s)");

        for (Anomaly anomaly : submission.getAnomalies()) {
            System.out.println(PROBLEM_INDENT + anomaly.getType() + ": "
                    + anomaly.getMessage());
        }
    }

    /**
     * Prints a closing line for the run.
     *
     * @param gradingRun the run that has finished
     */
    @Override
    public synchronized void onRunFinished(GradingRun gradingRun) {
        System.out.println("Finished run " + gradingRun.getId() + ".");
    }
}
