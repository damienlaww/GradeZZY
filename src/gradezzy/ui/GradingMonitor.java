/*
 * File: GradingMonitor.java
 *
 * Purpose: Turns progress events from a grading run into values the
 * screens can show.
 *
 * Where it fits: The ProgressListener given to both backends. Kept in
 * UiState, so the Progress screen can be left and opened during a run.
 *
 * Note on untaught Java: Platform.runLater moves events onto the JavaFX
 * thread, and a Timeline refreshes the clock every second.
 */
package gradezzy.ui;

import javafx.animation.KeyFrame;
import javafx.animation.Timeline;
import javafx.application.Platform;
import javafx.beans.property.DoubleProperty;
import javafx.beans.property.IntegerProperty;
import javafx.beans.property.LongProperty;
import javafx.beans.property.ObjectProperty;
import javafx.beans.property.SimpleDoubleProperty;
import javafx.beans.property.SimpleIntegerProperty;
import javafx.beans.property.SimpleLongProperty;
import javafx.beans.property.SimpleObjectProperty;
import javafx.beans.property.SimpleStringProperty;
import javafx.beans.property.StringProperty;
import javafx.collections.FXCollections;
import javafx.collections.ObservableList;
import javafx.util.Duration;

import gradezzy.grading.ProgressListener;
import gradezzy.model.Anomaly;
import gradezzy.model.AnomalyType;
import gradezzy.model.GradingRun;
import gradezzy.model.Submission;
import gradezzy.model.SubmissionStatus;
import gradezzy.service.ProgressEstimator;
import gradezzy.ui.backend.PipelineStages;
import gradezzy.ui.component.Labels;
import gradezzy.ui.component.Tone;

/**
 * Follows the grading run in the background.
 */
public class GradingMonitor implements ProgressListener {

    /** The value shown when the time left is not known yet. */
    public static final long UNKNOWN_SECONDS = -1L;

    /**
     * Where the run is.
     */
    public enum RunStatus {

        /** No run has started in this session. */
        IDLE,

        /** A run is going. */
        RUNNING,

        /** Cancel was pressed; the current submission is finishing. */
        CANCELLING,

        /** The run stopped after Cancel. Completed results are kept. */
        CANCELLED,

        /** Every submission was processed. */
        FINISHED,

        /** The run could not start or stopped with an error. */
        FAILED
    }

    /**
     * One student as the Progress screen lists it.
     */
    public static class ProgressRow {

        // The student
        private final String username;

        // What is happening to the submission
        private final StringProperty status = new SimpleStringProperty();

        // The stage being run, or what happened
        private final StringProperty detail = new SimpleStringProperty("");

        // The colour and icon of the status
        private final ObjectProperty<Tone> tone =
                new SimpleObjectProperty<>(Tone.NEUTRAL);

        /**
         * Creates a row.
         *
         * @param username the student
         * @param status the first status
         */
        public ProgressRow(String username, String status) {
            this.username = username;
            this.status.set(status);
        }

        /**
         * Returns the student.
         *
         * @return the username
         */
        public String getUsername() {
            return username;
        }

        /**
         * Returns the status property.
         *
         * @return the property
         */
        public StringProperty statusProperty() {
            return status;
        }

        /**
         * Returns the detail property.
         *
         * @return the property
         */
        public StringProperty detailProperty() {
            return detail;
        }

        /**
         * Returns the tone of the status, which picks its icon.
         *
         * @return the property
         */
        public ObjectProperty<Tone> toneProperty() {
            return tone;
        }

        /**
         * Sets the status and its tone. The tone is set first, so a cell
         * redrawn for the new status finds the new tone.
         *
         * @param text the status
         * @param newTone the tone of that status
         */
        public void setState(String text, Tone newTone) {
            tone.set(newTone);
            status.set(text);
        }
    }

    // How often the elapsed time and time left are refreshed
    private static final Duration CLOCK_TICK = Duration.seconds(1);

    // Nanoseconds in one second
    private static final long NANOS_PER_SECOND = 1000000000L;

    // Where the run is
    private final ObjectProperty<RunStatus> status =
            new SimpleObjectProperty<>(RunStatus.IDLE);

    // The number of submissions in the run
    private final IntegerProperty total = new SimpleIntegerProperty(0);

    // The number of submissions processed
    private final IntegerProperty done = new SimpleIntegerProperty(0);

    // The number of submissions the pipeline could not grade
    private final IntegerProperty failures = new SimpleIntegerProperty(0);

    // The number of compile errors, timeouts and runtime errors
    private final IntegerProperty issues = new SimpleIntegerProperty(0);

    // The student being graded, or an empty string
    private final StringProperty currentUsername =
            new SimpleStringProperty("");

    // The stage being run, or an empty string
    private final StringProperty currentStage = new SimpleStringProperty("");

    // The position of the stage being run, or -1
    private final IntegerProperty stageIndex = new SimpleIntegerProperty(-1);

    // Seconds since the run started
    private final LongProperty elapsedSeconds = new SimpleLongProperty(0L);

    // Estimated seconds left, or UNKNOWN_SECONDS
    private final LongProperty remainingSeconds =
            new SimpleLongProperty(UNKNOWN_SECONDS);

    // The share of submissions processed, from 0 to 1
    private final DoubleProperty fraction = new SimpleDoubleProperty(0.0);

    // What the run is doing or why it stopped, in plain words
    private final StringProperty message = new SimpleStringProperty("");

    // A plain description of each execution issue found so far
    private final ObservableList<String> issueLines =
            FXCollections.observableArrayList();

    // One row per student, in the order they were reached
    private final ObservableList<ProgressRow> rows =
            FXCollections.observableArrayList();

    // Estimates the time left
    private final ProgressEstimator estimator = new ProgressEstimator();

    // The run being followed, once known
    private GradingRun run = null;

    // When the run started, from System.nanoTime
    private long startNanos = 0L;

    // Refreshes the times every second while the run is going
    private Timeline clock = null;

    /**
     * Creates a monitor with no run.
     */
    public GradingMonitor() {
        // nothing to set up
    }

    /**
     * Gets ready to follow a new run. Call on the JavaFX thread before
     * the backend starts the run.
     *
     * @param description the first message, such as "Grading 6 submissions"
     */
    public void begin(String description) {
        total.set(0);
        done.set(0);
        failures.set(0);
        issues.set(0);
        currentUsername.set("");
        currentStage.set("");
        stageIndex.set(-1);
        elapsedSeconds.set(0L);
        remainingSeconds.set(UNKNOWN_SECONDS);
        fraction.set(0.0);
        issueLines.clear();
        rows.clear();
        message.set(description);
        startNanos = System.nanoTime();
        status.set(RunStatus.RUNNING);
        startClock();
    }

    /**
     * Records that Cancel was pressed.
     */
    public void markCancelling() {
        if (status.get() == RunStatus.RUNNING) {
            status.set(RunStatus.CANCELLING);
            message.set("Stopping after the current submission. Completed "
                    + "results are kept.");
        }
    }

    /**
     * Records that the run ended. Call on the JavaFX thread.
     *
     * @param finishedRun the run with its results
     */
    public void finish(GradingRun finishedRun) {
        run = finishedRun;
        stopClock();
        currentUsername.set("");
        currentStage.set("");
        stageIndex.set(-1);
        remainingSeconds.set(0L);

        if (countCancelled(finishedRun) > 0) {
            status.set(RunStatus.CANCELLED);
            message.set("Cancelled. " + done.get() + " of " + total.get()
                    + " submissions were processed and their results "
                    + "are kept. Resume to grade the rest.");
        } else {
            status.set(RunStatus.FINISHED);
            message.set("Finished. " + total.get() + " submission"
                    + Labels.plural(total.get()) + " processed.");
        }
    }

    /**
     * Records that the run failed. Call on the JavaFX thread.
     *
     * @param reason what went wrong and what to do
     */
    public void fail(String reason) {
        stopClock();
        status.set(RunStatus.FAILED);
        message.set(reason);
    }

    /**
     * Says whether a run is going.
     *
     * @return true while running or cancelling
     */
    public boolean isActive() {
        return status.get() == RunStatus.RUNNING
                || status.get() == RunStatus.CANCELLING;
    }

    /**
     * Returns the last run followed.
     *
     * @return the run, or null
     */
    public GradingRun getRun() {
        return run;
    }

    /**
     * Called by the pipeline when the run starts.
     *
     * @param totalSubmissions the number of submissions in the run
     */
    @Override
    public void onRunStarted(int totalSubmissions) {
        onJavaFxThread(() -> {
            total.set(totalSubmissions);
            estimator.start(totalSubmissions);
            message.set("Grading " + totalSubmissions + " submission"
                    + Labels.plural(totalSubmissions) + ".");
        });
    }

    /**
     * Called by the pipeline when a submission starts.
     *
     * @param submission the submission
     */
    @Override
    public void onSubmissionStarted(Submission submission) {
        String username = submission.getUsername();
        onJavaFxThread(() -> {
            currentUsername.set(username);
            rowFor(username).setState("Grading", Tone.INFO);
        });
    }

    /**
     * Called by the pipeline when a stage starts.
     *
     * @param submission the submission
     * @param stageName the stage
     */
    @Override
    public void onStageStarted(Submission submission, String stageName) {
        String username = submission.getUsername();
        onJavaFxThread(() -> {
            currentUsername.set(username);
            currentStage.set(stageName);
            stageIndex.set(PipelineStages.indexOf(stageName));
            rowFor(username).detailProperty().set(stageName);
        });
    }

    /**
     * Called when a submission finishes or is skipped after Cancel.
     *
     * @param submission the submission
     */
    @Override
    public void onSubmissionFinished(Submission submission) {
        String username = submission.getUsername();
        SubmissionStatus finalStatus = submission.getStatus();
        String issueSummary = describeIssues(submission);
        int issueCount = countIssues(submission);
        onJavaFxThread(() -> recordFinished(username, finalStatus,
                issueSummary, issueCount));
    }

    /**
     * Called by the pipeline when the run ends.
     *
     * @param gradingRun the run
     */
    @Override
    public void onRunFinished(GradingRun gradingRun) {
        onJavaFxThread(() -> run = gradingRun);
    }

    /**
     * Returns the run status property.
     *
     * @return the property
     */
    public ObjectProperty<RunStatus> statusProperty() {
        return status;
    }

    /**
     * Returns the total property.
     *
     * @return the property
     */
    public IntegerProperty totalProperty() {
        return total;
    }

    /**
     * Returns the processed count property.
     *
     * @return the property
     */
    public IntegerProperty doneProperty() {
        return done;
    }

    /**
     * Returns the failure count property.
     *
     * @return the property
     */
    public IntegerProperty failuresProperty() {
        return failures;
    }

    /**
     * Returns the execution issue count property.
     *
     * @return the property
     */
    public IntegerProperty issuesProperty() {
        return issues;
    }

    /**
     * Returns the current student property.
     *
     * @return the property
     */
    public StringProperty currentUsernameProperty() {
        return currentUsername;
    }

    /**
     * Returns the current stage property.
     *
     * @return the property
     */
    public StringProperty currentStageProperty() {
        return currentStage;
    }

    /**
     * Returns the current stage position property.
     *
     * @return the property
     */
    public IntegerProperty stageIndexProperty() {
        return stageIndex;
    }

    /**
     * Returns the elapsed seconds property.
     *
     * @return the property
     */
    public LongProperty elapsedSecondsProperty() {
        return elapsedSeconds;
    }

    /**
     * Returns the estimated seconds left property.
     *
     * @return the property
     */
    public LongProperty remainingSecondsProperty() {
        return remainingSeconds;
    }

    /**
     * Returns the fraction done property.
     *
     * @return the property
     */
    public DoubleProperty fractionProperty() {
        return fraction;
    }

    /**
     * Returns the message property.
     *
     * @return the property
     */
    public StringProperty messageProperty() {
        return message;
    }

    /**
     * Returns the execution issues found so far.
     *
     * @return one plain line per issue
     */
    public ObservableList<String> getIssueLines() {
        return issueLines;
    }

    /**
     * Returns one row per student reached so far.
     *
     * @return the rows
     */
    public ObservableList<ProgressRow> getRows() {
        return rows;
    }

    // Updates the counts when a submission is done.
    private void recordFinished(String username,
            SubmissionStatus finalStatus, String issueSummary,
            int issueCount) {
        ProgressRow row = rowFor(username);
        if (finalStatus == SubmissionStatus.CANCELLED) {
            row.setState("Cancelled, not graded", Tone.NEUTRAL);
            row.detailProperty().set("Resume to grade");
            return;
        }

        done.set(done.get() + 1);
        estimator.itemFinished();
        fraction.set(estimator.getFractionDone());
        remainingSeconds.set(estimator.estimateSecondsRemaining());

        if (finalStatus == SubmissionStatus.FAILED) {
            failures.set(failures.get() + 1);
            row.setState("Could not grade", Tone.ERROR);
        } else if (issueCount > 0) {
            row.setState("Graded with issues", Tone.WARNING);
        } else {
            row.setState("Graded", Tone.SUCCESS);
        }
        row.detailProperty().set(issueSummary);

        if (issueCount > 0) {
            issues.set(issues.get() + issueCount);
            issueLines.add(username + ": " + issueSummary);
        }
    }

    // Returns the row of a student, adding it first when it is missing.
    private ProgressRow rowFor(String username) {
        for (ProgressRow row : rows) {
            if (row.getUsername().equals(username)) {
                return row;
            }
        }
        ProgressRow row = new ProgressRow(username, "Waiting");
        rows.add(row);
        return row;
    }

    // Counts the execution problems of a submission.
    private int countIssues(Submission submission) {
        int count = 0;
        for (Anomaly anomaly : submission.getAnomalies()) {
            if (isExecutionIssue(anomaly.getType())) {
                count++;
            }
        }
        return count;
    }

    // Describes the execution problems of a submission in plain words.
    private String describeIssues(Submission submission) {
        StringBuilder text = new StringBuilder();
        for (Anomaly anomaly : submission.getAnomalies()) {
            if (isExecutionIssue(anomaly.getType())) {
                if (text.length() > 0) {
                    text.append("; ");
                }
                text.append(Labels.anomalyTitle(anomaly.getType()));
            }
        }
        return text.toString();
    }

    // Says whether an anomaly type is a problem found while running code.
    private boolean isExecutionIssue(AnomalyType type) {
        return type == AnomalyType.COMPILE_ERROR
                || type == AnomalyType.TIMEOUT
                || type == AnomalyType.RUNTIME_ERROR
                || type == AnomalyType.INTERNAL_ERROR;
    }

    // Counts the submissions skipped after Cancel.
    private int countCancelled(GradingRun finishedRun) {
        int count = 0;
        if (finishedRun == null) {
            return count;
        }
        for (Submission submission : finishedRun.getSubmissions()) {
            if (submission.getStatus() == SubmissionStatus.CANCELLED) {
                count++;
            }
        }
        return count;
    }

    // Starts refreshing the times every second.
    private void startClock() {
        stopClock();
        clock = new Timeline(new KeyFrame(CLOCK_TICK, event -> tick()));
        clock.setCycleCount(Timeline.INDEFINITE);
        clock.play();
    }

    // Stops refreshing the times.
    private void stopClock() {
        if (clock != null) {
            clock.stop();
            clock = null;
        }
        tick();
    }

    // Refreshes the elapsed time and the estimate.
    private void tick() {
        if (startNanos == 0L) {
            return;
        }
        elapsedSeconds.set((System.nanoTime() - startNanos)
                / NANOS_PER_SECOND);
        if (isActive() && done.get() > 0) {
            remainingSeconds.set(estimator.estimateSecondsRemaining());
        }
    }

    // Runs an update on the JavaFX thread, now if already on it.
    private void onJavaFxThread(Runnable update) {
        if (Platform.isFxApplicationThread()) {
            update.run();
        } else {
            Platform.runLater(update);
        }
    }
}
