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
 * guarded by {@code synchronized}, and every call to the progress listener
 * goes through one lock so the listener is never touched by two threads at
 * once.
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

    // The flag checked before each submission
    private final CancellationToken cancellationToken;

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
     * failing submission; it marks that submission as FAILED instead. The
     * number of submissions graded at the same time comes from
     * {@code exec.thread.count}; a value of one, a single submission, or a
     * bad value all grade the submissions in order.
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
            gradeOne(submission, context, listener);
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
            Thread worker = new Thread(
                    new GradingWorker(handout, context, safeListener),
                    WORKER_NAME_PREFIX + number);
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
    // listener the submission is finished. Shared by the in-order and the
    // parallel paths.
    private void gradeOne(Submission submission, GradingContext context,
            ProgressListener listener) {
        if (cancellationToken.isCancelled()) {
            submission.setStatus(SubmissionStatus.CANCELLED);
        } else {
            processSubmission(submission, context, listener);
        }

        listener.onSubmissionFinished(submission);
    }

    // Reads exec.thread.count and keeps it within sensible bounds: never
    // fewer than one, and never more than the number of submissions.
    private int chooseWorkerCount(GradingContext context,
            int submissionCount) {
        int requested = SINGLE_THREADED;

        try {
            requested = context.getConfig().getInt(ConfigKeys.THREAD_COUNT);
        } catch (RuntimeException exception) {
            // A missing or non-numeric setting grades in order rather
            // than stopping the whole run.
            requested = SINGLE_THREADED;
        }

        if (requested < SINGLE_THREADED) {
            requested = SINGLE_THREADED;
        }

        if (requested > submissionCount) {
            requested = submissionCount;
        }

        return requested;
    }

    // Runs all stages for one submission and contains any failure.
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

    // Turns a failure into an anomaly with an id unique across runs.
    private void recordFailure(Submission submission, Exception exception) {
        String message = "Grading stopped early: " + exception.getMessage();
        Anomaly anomaly = new Anomaly(FAILURE_ID_PREFIX + UUID.randomUUID(),
                AnomalyType.INTERNAL_ERROR, Severity.ERROR,
                submission.getUsername(), message, FAILURE_ACTION);

        submission.addAnomaly(anomaly);
        submission.setStatus(SubmissionStatus.FAILED);
    }

    /**
     * Hands each submission to exactly one worker. The index is guarded by
     * synchronized, so two workers never take the same submission and none
     * is missed.
     */
    private static final class SubmissionHandout {

        // The submissions, in batch order
        private final List<Submission> submissions;

        // The next submission to hand out
        private int index = 0;

        private SubmissionHandout(List<Submission> submissions) {
            this.submissions = submissions;
        }

        // Returns the next submission, or null when there are none left.
        private synchronized Submission next() {
            if (index >= submissions.size()) {
                return null;
            }

            Submission submission = submissions.get(index);
            index++;
            return submission;
        }
    }

    /**
     * A worker thread that grades submissions until the handout is empty.
     */
    private final class GradingWorker implements Runnable {

        // Where the worker gets its next submission
        private final SubmissionHandout handout;

        // The shared read-only information the stages need
        private final GradingContext context;

        // The listener, already made safe for several threads
        private final ProgressListener listener;

        private GradingWorker(SubmissionHandout handout,
                GradingContext context, ProgressListener listener) {
            this.handout = handout;
            this.context = context;
            this.listener = listener;
        }

        @Override
        public void run() {
            Submission submission = handout.next();

            while (submission != null) {
                gradeOne(submission, context, listener);
                submission = handout.next();
            }
        }
    }

    /**
     * Wraps a progress listener so that only one worker is inside it at a
     * time. This keeps the listener's own counters correct and stops two
     * workers from interleaving the characters of one printed line. The
     * lines of different submissions may still appear mixed together,
     * which is expected when several are graded at once.
     */
    private static final class SynchronizedProgressListener
            implements ProgressListener {

        // The listener the window or console gave
        private final ProgressListener delegate;

        private SynchronizedProgressListener(ProgressListener delegate) {
            this.delegate = delegate;
        }

        @Override
        public synchronized void onRunStarted(int totalSubmissions) {
            delegate.onRunStarted(totalSubmissions);
        }

        @Override
        public synchronized void onSubmissionStarted(Submission submission) {
            delegate.onSubmissionStarted(submission);
        }

        @Override
        public synchronized void onStageStarted(Submission submission,
                String stageName) {
            delegate.onStageStarted(submission, stageName);
        }

        @Override
        public synchronized void onSubmissionFinished(Submission submission) {
            delegate.onSubmissionFinished(submission);
        }

        @Override
        public synchronized void onRunFinished(GradingRun gradingRun) {
            delegate.onRunFinished(gradingRun);
        }
    }
}
