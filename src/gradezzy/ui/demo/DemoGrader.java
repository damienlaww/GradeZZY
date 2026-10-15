/*
 * File: DemoGrader.java
 *
 * Purpose: Imitates a grading run in demo mode by stepping through the
 * nine pipeline stages for each submission and reporting progress, with
 * no files, processes or threads.
 *
 * Where it fits: DemoBackend starts one for each simulated run. It calls
 * the same ProgressListener methods as GradingPipeline, in the same
 * order, so the Progress screen has one code path for both modes. A
 * Timeline calls step() on the JavaFX thread; the self-test calls step()
 * directly.
 *
 * AI use: drafted with Claude Code, reviewed by Jun Sheng.
 */
package gradezzy.ui.demo;

import java.util.ArrayList;
import java.util.List;

import javafx.animation.KeyFrame;
import javafx.animation.Timeline;
import javafx.util.Duration;

import gradezzy.grading.ProgressListener;
import gradezzy.model.GradingRun;
import gradezzy.model.Submission;
import gradezzy.model.SubmissionStatus;
import gradezzy.ui.backend.PipelineStages;

/**
 * A simulated grading run.
 *
 * <p>Tier: SHOULD. Owner: Damien.
 */
public class DemoGrader {

    /** The time between two simulated stages. */
    public static final Duration STEP_TIME = Duration.millis(110);

    /**
     * Gives a submission its simulated results when its last stage ends.
     */
    public interface Outcome {

        /**
         * Fills in the results of one submission.
         *
         * @param submission the submission that finished
         */
        void apply(Submission submission);
    }

    // The value of stageIndex before a submission has started
    private static final int NOT_STARTED = -1;

    // The run being graded
    private final GradingRun run;

    // The submissions to grade, in order
    private final List<Submission> queue = new ArrayList<>();

    // Told about each stage, as GradingPipeline would
    private final ProgressListener listener;

    // Fills in the results of a finished submission
    private final Outcome outcome;

    // Called once on the JavaFX thread when the run ends
    private final Runnable onDone;

    // The submission being graded, as an index into the queue
    private int submissionIndex = 0;

    // The stage being run, or NOT_STARTED between submissions
    private int stageIndex = NOT_STARTED;

    // Whether Cancel was pressed
    private boolean cancelRequested = false;

    // Whether the run has ended
    private boolean finished = false;

    // Calls step() regularly when the run is automatic
    private Timeline timeline = null;

    /**
     * Creates a simulated run.
     *
     * @param run the run being graded
     * @param submissions the submissions to grade, in order
     * @param listener told about each stage
     * @param outcome fills in the results of a finished submission
     * @param onDone called once when the run ends
     */
    public DemoGrader(GradingRun run, List<Submission> submissions,
            ProgressListener listener, Outcome outcome, Runnable onDone) {
        this.run = run;
        this.queue.addAll(submissions);
        this.listener = listener;
        this.outcome = outcome;
        this.onDone = onDone;
    }

    /**
     * Starts the run.
     *
     * @param automatic true to step on a timer, false when the caller
     *        calls step() itself
     */
    public void start(boolean automatic) {
        for (Submission submission : queue) {
            submission.setStatus(SubmissionStatus.PENDING);
        }
        listener.onRunStarted(queue.size());

        if (automatic) {
            timeline = new Timeline(new KeyFrame(STEP_TIME,
                    event -> step()));
            timeline.setCycleCount(Timeline.INDEFINITE);
            timeline.play();
        }
    }

    /**
     * Moves the run forward by one stage.
     *
     * @return true while the run is still going
     */
    public boolean step() {
        if (finished) {
            return false;
        }
        if (submissionIndex >= queue.size()) {
            finish();
            return false;
        }

        Submission submission = queue.get(submissionIndex);
        if (stageIndex == NOT_STARTED) {
            if (cancelRequested) {
                cancelRemaining();
                finish();
                return false;
            }
            listener.onSubmissionStarted(submission);
            submission.setStatus(SubmissionStatus.RUNNING);
            stageIndex = 0;
            listener.onStageStarted(submission,
                    PipelineStages.nameAt(stageIndex));
            return true;
        }

        stageIndex++;
        if (stageIndex < PipelineStages.count()) {
            listener.onStageStarted(submission,
                    PipelineStages.nameAt(stageIndex));
            return true;
        }

        outcome.apply(submission);
        listener.onSubmissionFinished(submission);
        submissionIndex++;
        stageIndex = NOT_STARTED;
        return true;
    }

    /**
     * Steps until the run ends. Used by the self-test.
     */
    public void runToEnd() {
        boolean going = true;
        while (going) {
            going = step();
        }
    }

    /**
     * Asks the run to stop after the current submission.
     */
    public void cancel() {
        cancelRequested = true;
    }

    /**
     * Says whether the run has ended.
     *
     * @return true after the last submission or a cancel
     */
    public boolean isFinished() {
        return finished;
    }

    /**
     * Returns the number of submissions that finished so far.
     *
     * @return the count
     */
    public int getFinishedCount() {
        return submissionIndex;
    }

    // Marks every submission that did not start as cancelled, as the
    // pipeline does after Cancel.
    private void cancelRemaining() {
        for (int index = submissionIndex; index < queue.size(); index++) {
            Submission submission = queue.get(index);
            submission.setStatus(SubmissionStatus.CANCELLED);
            listener.onSubmissionFinished(submission);
        }
        submissionIndex = queue.size();
    }

    // Ends the run once.
    private void finish() {
        if (finished) {
            return;
        }
        finished = true;
        if (timeline != null) {
            timeline.stop();
        }
        listener.onRunFinished(run);
        onDone.run();
    }
}
