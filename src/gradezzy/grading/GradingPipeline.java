/*
 * File: GradingPipeline.java
 *
 * Purpose: Runs every submission of a batch through an ordered list of
 * GradingStage objects.
 *
 * Where it fits: The heart of batch grading. It is the one place that
 * guarantees the robustness rule of the brief (section 4.2): when a stage
 * fails for one submission, the failure is recorded as an anomaly on that
 * submission and the batch carries on with the next one.
 */
package gradezzy.grading;

import java.util.ArrayList;
import java.util.List;

import gradezzy.model.Anomaly;
import gradezzy.model.AnomalyType;
import gradezzy.model.GradingRun;
import gradezzy.model.Severity;
import gradezzy.model.Submission;
import gradezzy.model.SubmissionStatus;

/**
 * Runs each submission through the grading stages, one after another, and
 * never lets one submission stop the batch.
 *
 * <p>Tier: MUST. Owner: Jing Yuan.
 */
public class GradingPipeline {

    // Start of the id given to anomalies created by the pipeline itself
    private static final String FAILURE_ID_PREFIX = "PIPELINE-";

    // Advice shown with an anomaly created by the pipeline itself
    private static final String FAILURE_ACTION =
            "Check this submission by hand, then use Retry.";

    // The stages, in the order they run
    private final List<GradingStage> stages = new ArrayList<>();

    // The flag checked before each submission
    private final CancellationToken cancellationToken;

    // How many failures were recorded, used to number their anomalies
    private int failureCount = 0;

    /**
     * Creates an empty pipeline.
     *
     * @param cancellationToken the flag that stops the batch when set
     */
    public GradingPipeline(CancellationToken cancellationToken) {
        this.cancellationToken = cancellationToken;
    }

    /**
     * Adds a stage to the end of the pipeline.
     *
     * @param stage the stage to add
     */
    public void addStage(GradingStage stage) {
        stages.add(stage);
    }

    /**
     * Returns the stages in the order they run.
     *
     * @return the stages
     */
    public List<GradingStage> getStages() {
        return stages;
    }

    /**
     * Grades every submission of a run. This method does not throw for a
     * failing submission; it marks that submission as FAILED instead.
     *
     * @param gradingRun the batch to grade
     * @param context the shared information the stages need
     * @param listener the object told about progress
     */
    public void run(GradingRun gradingRun, GradingContext context,
            ProgressListener listener) {
        List<Submission> submissions = gradingRun.getSubmissions();
        listener.onRunStarted(submissions.size());

        for (Submission submission : submissions) {
            if (cancellationToken.isCancelled()) {
                submission.setStatus(SubmissionStatus.CANCELLED);
            } else {
                processSubmission(submission, context, listener);
            }

            listener.onSubmissionFinished(submission);
        }

        listener.onRunFinished(gradingRun);
    }

    // Runs all stages for one submission and contains any failure.
    private void processSubmission(Submission submission,
            GradingContext context, ProgressListener listener) {
        listener.onSubmissionStarted(submission);
        submission.setStatus(SubmissionStatus.RUNNING);

        try {
            for (GradingStage stage : stages) {
                listener.onStageStarted(submission, stage.getName());
                stage.process(submission, context);
            }

            submission.setStatus(SubmissionStatus.GRADED);
        } catch (GradingException exception) {
            recordFailure(submission, exception);
        } catch (RuntimeException exception) {
            // A bug in a stage must not stop the other submissions either.
            recordFailure(submission, exception);
        }
    }

    // Turns a failure into an anomaly on the submission.
    private void recordFailure(Submission submission, Exception exception) {
        failureCount++;
        String message = "Grading stopped early: " + exception.getMessage();
        Anomaly anomaly = new Anomaly(FAILURE_ID_PREFIX + failureCount,
                AnomalyType.INTERNAL_ERROR, Severity.ERROR,
                submission.getUsername(), message, FAILURE_ACTION);

        submission.addAnomaly(anomaly);
        submission.setStatus(SubmissionStatus.FAILED);
    }
}
