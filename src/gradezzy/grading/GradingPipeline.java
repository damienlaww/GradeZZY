/*
 * File: GradingPipeline.java
 *
 * Purpose: Runs every submission of a batch through an ordered list of
 * GradingStage objects, one submission at a time or several at once.
 *
 * Where it fits: The heart of batch grading. It is the one place that
 * guarantees the robustness rule of the brief (section 4.2): when a stage
 * fails for one submission, the failure is recorded as an anomaly on that
 * submission and the batch carries on with the next one.
 */
package gradezzy.grading;

import java.util.ArrayList;
import java.util.List;
import java.util.UUID;

import gradezzy.config.ConfigKeys;
import gradezzy.model.Anomaly;
import gradezzy.model.AnomalyType;
import gradezzy.model.GradingRun;
import gradezzy.model.Severity;
import gradezzy.model.Submission;
import gradezzy.model.SubmissionStatus;

/**
 * Runs each submission through the grading stages and never lets one
 * submission stop the batch. When {@code exec.thread.count} is more than
 * one, that many submissions are graded at the same time.
 *
 * <p>Threads are not part of the CS102 syllabus, so the code here uses
 * only {@code Thread}, {@code Runnable} and {@code synchronized}. Each
 * submission is graded in its own working-copy folder and writes only to
 * its own {@code Submission} object, so the submissions do not interfere.
 * The state the workers share, the handout of the next submission, is
 * guarded by {@code synchronized}, and every call to the progress
 * listener goes through one lock so the listener is never touched by two
 * threads at once. Each worker thread
 * runs a {@code GradingWorker}, takes submissions from a
 * {@code SubmissionHandout} and reports through a
 * {@code SynchronizedProgressListener}; each is in its own file.
 *
 * <p>Tier: MUST. Owner: Jing Yuan.
 */
public class GradingPipeline {

    // Start of the id given to anomalies created by the pipeline itself
    private static final String FAILURE_ID_PREFIX = "PIPELINE-";

    // Advice shown with an anomaly created by the pipeline itself
    private static final String FAILURE_ACTION =
            "Check this submission by hand, then use Retry.";

    // Fewest submissions at a time; one means grade them in order
    private static final int SINGLE_THREADED = 1;

    // Start of the name given to each worker thread, for logs and dumps
    private static final String WORKER_NAME_PREFIX = "grading-worker-";

    // The stages, in the order they run
    private final List<GradingStage> stages = new ArrayList<>();

    // The flag checked before each submission and before each stage
    private final CancellationToken cancellationToken;

    /**
     * Creates an empty pipeline.
     *
     * <p>Tier: MUST.
     *
     * @param cancellationToken the flag that stops the batch when set
     */
    public GradingPipeline(CancellationToken cancellationToken) {
        this.cancellationToken = cancellationToken;
    }

    /**
     * Adds a stage to the end of the pipeline.
     *
     * <p>Tier: MUST.
     *
     * @param stage the stage to add
     */
    public void addStage(GradingStage stage) {
        stages.add(stage);
    }

    /**
     * Grades every submission of a run. This method does not throw for a
     * failing submission; it marks that submission as FAILED instead. The
     * number of submissions graded at the same time comes from
     * {@code exec.thread.count}; a value of one, a single submission, or a
     * bad value all grade the submissions in order.
     *
     * <p>Tier: MUST.
     *
     * @param gradingRun the batch to grade
     * @param context the shared information the stages need
     * @param listener the object told about progress
     */
    public void run(GradingRun gradingRun, GradingContext context,
            ProgressListener listener) {
        List<Submission> submissions = gradingRun.getSubmissions();
        listener.onRunStarted(submissions.size());

        int workerCount = chooseWorkerCount(context, submissions.size());
        if (workerCount <= SINGLE_THREADED) {
            gradeInOrder(submissions, context, listener);
        } else {
            gradeTogether(submissions, context, listener, workerCount);
        }

        listener.onRunFinished(gradingRun);
    }

    // Grades the submissions one after another on the calling thread.
    private void gradeInOrder(List<Submission> submissions,
            GradingContext context, ProgressListener listener) {
        for (Submission submission : submissions) {
            gradeSafely(submission, context, listener);
        }
    }

    // Grades workerCount submissions at the same time. The listener is
    // wrapped so its methods are never called by two workers at once, and
    // a shared handout gives each submission to exactly one worker.
    private void gradeTogether(List<Submission> submissions,
            GradingContext context, ProgressListener listener,
            int workerCount) {
        ProgressListener safeListener =
                new SynchronizedProgressListener(listener);
        SubmissionHandout handout = new SubmissionHandout(submissions);

        List<Thread> workers = new ArrayList<>();
        for (int number = 0; number < workerCount; number++) {
            GradingWorker job = new GradingWorker(this, handout, context,
                    safeListener);
            String workerName = WORKER_NAME_PREFIX + number;
            Thread worker = new Thread(job, workerName);
            workers.add(worker);
            worker.start();
        }

        waitForAll(workers);
    }

    // Waits for every worker to finish. If this thread is interrupted
    // while waiting, the run is cancelled so the workers stop at their
    // next submission, but we still wait for each one to end so no worker
    // is left touching a submission after run returns.
    private void waitForAll(List<Thread> workers) {
        boolean interrupted = false;

        for (Thread worker : workers) {
            while (worker.isAlive()) {
                try {
                    worker.join();
                } catch (InterruptedException exception) {
                    interrupted = true;
                    cancellationToken.cancel();
                }
            }
        }

        if (interrupted) {
            Thread.currentThread().interrupt();
        }
    }

    // Grades one submission: skipped and marked CANCELLED if the run was
    // cancelled, otherwise run through the stages. Always tells the
    // listener the submission is finished. Called through gradeSafely.
    private void gradeOne(Submission submission, GradingContext context,
            ProgressListener listener) {
        if (cancellationToken.isCancelled()) {
            submission.setStatus(SubmissionStatus.CANCELLED);
        } else {
            processSubmission(submission, context, listener);
        }

        listener.onSubmissionFinished(submission);
    }

    /**
     * Grades one submission as gradeOne does, and stops a problem
     * outside the stages, such as one in a listener, from ending the
     * batch or the worker thread: a submission it stopped before the
     * end is marked FAILED with the reason, and a finished one keeps
     * its result. Used by the in-order path and by each GradingWorker.
     *
     * @param submission the submission to grade
     * @param context the shared information the stages need
     * @param listener the object told about progress
     */
    void gradeSafely(Submission submission, GradingContext context,
            ProgressListener listener) {
        try {
            gradeOne(submission, context, listener);
        } catch (RuntimeException exception) {
            if (!isFinished(submission)) {
                recordFailure(submission, exception);
            }
        }
    }

    // Says whether a submission already has its final status.
    private boolean isFinished(Submission submission) {
        SubmissionStatus status = submission.getStatus();
        return status == SubmissionStatus.GRADED
                || status == SubmissionStatus.FAILED
                || status == SubmissionStatus.CANCELLED;
    }

    // Reads exec.thread.count and keeps it within sensible bounds: never
    // fewer than one, and never more than the number of submissions.
    private int chooseWorkerCount(GradingContext context,
            int submissionCount) {
        int requested = SINGLE_THREADED;

        try {
            requested = context.getConfig().getInt(ConfigKeys.THREAD_COUNT);
        } catch (RuntimeException exception) {
            // Ignored: a missing or non-numeric setting keeps
            // SINGLE_THREADED and grades in order rather than stopping
            // the whole run.
        }

        if (requested < SINGLE_THREADED) {
            requested = SINGLE_THREADED;
        }

        if (requested > submissionCount) {
            requested = submissionCount;
        }

        return requested;
    }

    // Runs all stages for one submission and contains any failure. Cancel
    // is checked before each stage too, so a submission being graded
    // stops at the next stage instead of running every tester first. It
    // is then CANCELLED, and Resume grades it again from the start.
    private void processSubmission(Submission submission,
            GradingContext context, ProgressListener listener) {
        listener.onSubmissionStarted(submission);
        if (submission.getIngestFailure() != null) {
            submission.setStatus(SubmissionStatus.FAILED);
            return;
        }
        submission.setStatus(SubmissionStatus.RUNNING);

        try {
            for (GradingStage stage : stages) {
                if (cancellationToken.isCancelled()) {
                    submission.setStatus(SubmissionStatus.CANCELLED);
                    return;
                }
                listener.onStageStarted(submission, stage.getName());
                stage.process(submission, context);
            }

            submission.setStatus(SubmissionStatus.GRADED);
        } catch (UploadRejectedException exception) {
            // The stage has already recorded what is wrong with the upload
            submission.setStatus(SubmissionStatus.FAILED);
        } catch (GradingException exception) {
            recordFailure(submission, exception);
        } catch (RuntimeException exception) {
            // A bug in a stage must not stop the other submissions either.
            recordFailure(submission, exception);
        } catch (StackOverflowError error) {
            // Deep recursion in a stage ends this submission only, so the
            // worker and the batch go on
            recordFailure(submission, error);
        }
    }

    // Turns a failure into an anomaly with an id unique across runs. The
    // submission is touched by only one worker, so no lock is needed.
    private void recordFailure(Submission submission, Throwable exception) {
        String reason = exception.getMessage();
        if (reason == null) {
            // A StackOverflowError usually has no message of its own
            reason = exception.toString();
        }
        String message = "Grading stopped early: " + reason;
        Anomaly anomaly = new Anomaly(FAILURE_ID_PREFIX + UUID.randomUUID(),
                AnomalyType.INTERNAL_ERROR, Severity.ERROR,
                submission.getUsername(), message, FAILURE_ACTION);

        submission.addAnomaly(anomaly);
        submission.setStatus(SubmissionStatus.FAILED);
    }
}
