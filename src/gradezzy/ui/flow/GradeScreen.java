/*
 * File: GradeScreen.java
 *
 * Purpose: Step 2. Shows grading as it happens: how many students are
 * done, about how long is left, what is happening now, a small bell
 * curve of the marks so far and a count of each result. Offers Cancel,
 * and Resume after a cancel.
 *
 * Where it fits: Replaces the old Grading Progress screen. It is the
 * ProgressListener and RunCallback of every run it starts, and moves to
 * Review when a run ends.
 */
package gradezzy.ui.flow;

import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.Map;

import javafx.application.Platform;
import javafx.beans.property.DoubleProperty;
import javafx.beans.property.SimpleDoubleProperty;
import javafx.geometry.Pos;
import javafx.scene.Node;
import javafx.scene.control.Label;
import javafx.scene.control.ScrollPane;
import javafx.scene.layout.ColumnConstraints;
import javafx.scene.layout.GridPane;
import javafx.scene.layout.HBox;
import javafx.scene.layout.Priority;
import javafx.scene.layout.Region;
import javafx.scene.layout.StackPane;
import javafx.scene.layout.VBox;

import gradezzy.config.ConfigKeys;
import gradezzy.grading.ProgressListener;
import gradezzy.model.Assessment;
import gradezzy.model.GradingRun;
import gradezzy.model.Submission;
import gradezzy.model.SubmissionStatus;
import gradezzy.ui.backend.ImportItem;
import gradezzy.ui.backend.InputKind;
import gradezzy.ui.backend.PipelineStages;
import gradezzy.ui.backend.ResultState;
import gradezzy.ui.backend.RunCallback;

/**
 * The Grade step.
 */
public class GradeScreen extends FlowScreen
        implements ProgressListener, RunCallback {

    // What each step of grading means, in the order of PipelineStages
    private static final String[] STAGE_WORDS = {"unzipping",
        "checking the folders and files", "fixing small mistakes",
        "looking for answers to flag", "copying the testers",
        "compiling", "running the tests", "adding up the marks",
        "saving"};

    // Milliseconds in a second
    private static final double MILLIS_PER_SECOND = 1000.0;

    // Seconds in a minute
    private static final int SECONDS_PER_MINUTE = 60;

    // The share of the row for each of the two small cards
    private static final double HALF = 50.0;

    // The separator between parts of a line
    private static final String DOT = " · ";

    // The rows of the "So far" card, top to bottom
    private static final int GRADED = 0;
    private static final int REVIEW = 1;
    private static final int FAILED = 2;
    private static final int WAITING = 3;

    // The scrolling page
    private final ScrollPane view;

    // The heading
    private final Label heading = Parts.heading("Grading");

    // The line under the heading
    private final Label subtitle = Parts.subtitle("");

    // How many students are done, such as "27"
    private final Label done = new Label("0");

    // " of 42 students"
    private final Label ofTotal = new Label();

    // "About 1 minute left"
    private final Label timeLeft = Parts.muted("");

    // What is happening now
    private final Label now = Parts.muted("");

    // The share of students done, from 0 to 1
    private final DoubleProperty progress = new SimpleDoubleProperty(0);

    // The bell curve of the marks so far
    private final BellCurveChart chart = new BellCurveChart(true);

    // Shown instead of the chart until a student is graded
    private final Label noMarks = Parts.muted("Marks appear here as "
            + "students finish.");

    // The pill and count of each row of "So far"
    private final Label[] pills = new Label[WAITING + 1];
    private final Label[] counts = new Label[WAITING + 1];

    // Every student of the run that has a result, by username
    private final Map<String, Submission> results = new LinkedHashMap<>();

    // How many students this run grades
    private int total = 0;

    // How many of them are finished
    private int finished = 0;

    // When the run started, in milliseconds
    private long startedAt = 0;

    // True after Cancel was pressed
    private boolean cancelling = false;

    // The message of the last failure, or null
    private String failure = null;

    /**
     * Creates the Grade step.
     *
     * @param window the window
     */
    public GradeScreen(FlowWindow window) {
        super(window);
        GridPane cards = new GridPane();
        cards.setHgap(Parts.CARD_GAP);
        cards.getColumnConstraints().addAll(half(), half());
        cards.add(createMarksCard(), 0, 0);
        cards.add(createSoFarCard(), 1, 0);
        VBox header = new VBox(heading, subtitle);
        header.getStyleClass().add("screen-header");
        view = Parts.narrowPage(header, createProgressCard(), cards);
    }

    @Override
    public Node getView() {
        return view;
    }

    @Override
    public void onShow() {
        refreshActions();
    }

    /**
     * Starts grading the batch chosen on Set up.
     */
    public void start() {
        prepare(getState().getSection().getLabel());
        for (ImportItem item : getState().getBatch().getItems(
                InputKind.SUBMISSION)) {
            if (getState().getBatch().getEffectiveState(item).isUsable()) {
                total++;
            }
        }
        updateCount();
        getState().setGrading(true);
        getBackend().recordAudit("Grading started", "Grading "
                + Parts.plural(total, "submission") + " for "
                + getState().getSection().getLabel() + ".");
        getBackend().startGrading(getState().getBatch(),
                getState().getSection(), this, this);
        refreshActions();
    }

    /**
     * Grades the submissions of a run that were not graded yet, or also
     * the ones that failed.
     *
     * @param run the run
     * @param retryFailed true to grade failed submissions again too
     */
    public void continueRun(GradingRun run, boolean retryFailed) {
        prepare(run.getSection().getLabel());
        for (Submission submission : run.getSubmissions()) {
            boolean again = retryFailed
                    && submission.getStatus() == SubmissionStatus.FAILED;
            if (isUnfinished(submission) || again) {
                total++;
            } else {
                results.put(submission.getUsername(), submission);
            }
        }
        updateCount();
        getState().setGrading(true);
        if (retryFailed) {
            getBackend().retryFailedSubmissions(run, this, this);
        } else {
            getBackend().resumeGrading(run, this, this);
        }
        refreshActions();
    }

    // ------------------------------------------------------------ build

    // Returns a column that takes half of a grid's width.
    private ColumnConstraints half() {
        ColumnConstraints column = new ColumnConstraints();
        column.setPercentWidth(HALF);
        column.setHgrow(Priority.ALWAYS);
        column.setFillWidth(true);
        return column;
    }

    // Builds the count, time left, what is happening now and the bar.
    private VBox createProgressCard() {
        done.getStyleClass().add("grade-count");
        ofTotal.getStyleClass().add("grade-of");
        HBox count = new HBox(Parts.GAP_SMALL / 2, done, ofTotal);
        count.setAlignment(Pos.BASELINE_LEFT);
        VBox left = new VBox(count, timeLeft);
        now.getStyleClass().add("small-text");
        now.setMinWidth(0);
        HBox top = new HBox(Parts.GAP, left, Parts.grow(), now);
        top.setAlignment(Pos.BOTTOM_LEFT);

        Region fill = new Region();
        fill.getStyleClass().add("track-fill");
        StackPane bar = new StackPane(fill);
        bar.getStyleClass().add("score-track");
        bar.setAlignment(Pos.CENTER_LEFT);
        fill.maxWidthProperty().bind(bar.widthProperty().multiply(progress));
        fill.minWidthProperty().bind(fill.maxWidthProperty());
        return Parts.card(null, top, bar);
    }

    // Builds the card with the small bell curve.
    private VBox createMarksCard() {
        StackPane box = new StackPane(chart, noMarks);
        box.setMinHeight(BellCurveChart.SMALL_HEIGHT);
        VBox card = Parts.card("Marks so far", box);
        card.setMaxHeight(Double.MAX_VALUE);
        return card;
    }

    // Builds the card with one row per kind of result.
    private VBox createSoFarCard() {
        String[] words = {"Graded", "Needs review", "Did not compile",
            "Waiting"};
        Tone[] tones = {Tone.GOOD, Tone.WARN, Tone.BAD, Tone.NEUTRAL};
        VBox rows = new VBox();
        for (int index = 0; index < words.length; index++) {
            pills[index] = Parts.pill(words[index], tones[index]);
            counts[index] = Parts.strong("0");
            counts[index].getStyleClass().add("count-value");
            HBox row = Parts.row(Parts.GAP, pills[index], Parts.grow(),
                    counts[index]);
            row.getStyleClass().add("card-row");
            if (index == 0) {
                row.getStyleClass().add("first");
            }
            if (index == words.length - 1) {
                row.getStyleClass().add("last");
            }
            rows.getChildren().add(row);
        }
        VBox card = Parts.card("So far", rows);
        card.setMaxHeight(Double.MAX_VALUE);
        return card;
    }

    // ------------------------------------------------ progress listener

    @Override
    public void onRunStarted(int totalSubmissions) {
        Platform.runLater(() -> {
            total = totalSubmissions;
            updateCount();
        });
    }

    @Override
    public void onSubmissionStarted(Submission submission) {
        Platform.runLater(() -> now.setText("Now: "
                + Students.name(submission)));
    }

    @Override
    public void onStageStarted(Submission submission, String stageName) {
        Platform.runLater(() -> {
            int index = PipelineStages.indexOf(stageName);
            String words = "working";
            if (index >= 0 && index < STAGE_WORDS.length) {
                words = STAGE_WORDS[index];
            }
            now.setText("Now: " + Students.name(submission) + DOT
                    + words);
        });
    }

    @Override
    public void onSubmissionFinished(Submission submission) {
        Platform.runLater(() -> {
            finished++;
            results.put(submission.getUsername(), submission);
            updateCount();
        });
    }

    @Override
    public void onRunFinished(GradingRun gradingRun) {
        // The callback below moves on; nothing to do here
    }

    // ----------------------------------------------------- run callback

    @Override
    public void onFinished(GradingRun gradingRun) {
        getState().setGrading(false);
        getState().setRun(gradingRun);
        if (gradingRun != null) {
            for (Submission submission : gradingRun.getSubmissions()) {
                if (!isUnfinished(submission)) {
                    results.put(submission.getUsername(), submission);
                }
            }
            updateCount();
        }
        if (gradingRun != null && hasUnfinished(gradingRun)) {
            heading.setText("Grading stopped");
            now.setText("The rest are waiting; press Resume to grade "
                    + "them.");
            refreshActions();
            return;
        }
        heading.setText("Grading finished");
        now.setText("");
        getWindow().notify("Grading finished.");
        getState().reach(Step.REVIEW);
        if (getWindow().getCurrentStep() == Step.GRADE
                && getWindow().getCurrentPage() == Page.MARKING) {
            getWindow().show(Step.REVIEW);
            return;
        }
        refreshActions();
    }

    @Override
    public void onFailed(String message) {
        getState().setGrading(false);
        failure = message;
        heading.setText("Grading could not run");
        now.setText(message);
        refreshActions();
    }

    // ---------------------------------------------------------- helpers

    // Empties the screen for a new run.
    private void prepare(String sectionLabel) {
        results.clear();
        total = 0;
        finished = 0;
        cancelling = false;
        failure = null;
        startedAt = System.currentTimeMillis();
        heading.setText("Grading");
        String about = sectionLabel;
        Assessment assessment = getBackend().getAssessment();
        if (assessment != null) {
            about = about + DOT + assessment.getName();
        }
        subtitle.setText(about + ". You can leave this tab; grading "
                + "carries on.");
        now.setText("Starting");
        timeLeft.setText("");
    }

    // Updates the count, the bar, the time left, the chart and the rows.
    private void updateCount() {
        done.setText(String.valueOf(finished));
        ofTotal.setText("of " + Parts.plural(total, "student"));
        progress.set(total > 0 ? (double) finished / total : 0);
        updateTimeLeft();
        updateChart();
        updateRows();
    }

    // Works out about how long is left from the time taken so far.
    private void updateTimeLeft() {
        if (total > 0 && finished >= total) {
            timeLeft.setText("Done");
            return;
        }
        if (finished == 0) {
            timeLeft.setText("Working out the time left");
            return;
        }
        double elapsed = (System.currentTimeMillis() - startedAt)
                / MILLIS_PER_SECOND;
        long seconds = Math.round(elapsed / finished * (total - finished));
        timeLeft.setText("About " + describe(seconds) + " left");
    }

    // Redraws the bell curve of the graded students.
    private void updateChart() {
        Assessment assessment = getBackend().getAssessment();
        if (assessment == null) {
            return;
        }
        MarkStats stats = MarkStats.of(new ArrayList<>(
                results.values()), assessment.getMaxTotal(),
                MarkStats.parsePassPercent(getBackend().getSetting(
                ConfigKeys.PASS_MARK_PERCENT)));
        boolean empty = stats.isEmpty();
        chart.setStats(empty ? null : stats);
        noMarks.setVisible(empty);
    }

    // Counts each kind of result.
    private void updateRows() {
        int[] tally = new int[WAITING + 1];
        boolean onlyCompile = true;
        for (Submission submission : results.values()) {
            ResultState state = Students.state(submission, getBackend());
            if (state == ResultState.NEEDS_REVIEW) {
                tally[REVIEW]++;
            } else if (Students.isFailed(state)) {
                tally[FAILED]++;
                onlyCompile = onlyCompile
                        && state == ResultState.COMPILE_ERROR;
            } else if (state == ResultState.UNGRADED) {
                tally[WAITING]++;
            } else {
                tally[GRADED]++;
            }
        }
        tally[WAITING] += Math.max(0, total - finished);
        pills[FAILED].setText(onlyCompile ? "Did not compile"
                : "Could not grade");
        for (int index = 0; index < tally.length; index++) {
            counts[index].setText(String.valueOf(tally[index]));
        }
    }

    // Says a number of seconds in words.
    private String describe(long seconds) {
        if (seconds < SECONDS_PER_MINUTE) {
            return Parts.plural((int) Math.max(1, seconds), "second");
        }
        long minutes = Math.round((double) seconds / SECONDS_PER_MINUTE);
        return Parts.plural((int) minutes, "minute");
    }

    // Says whether a submission still has to be graded.
    private boolean isUnfinished(Submission submission) {
        return submission.getStatus() == SubmissionStatus.CANCELLED
                || submission.getStatus() == SubmissionStatus.PENDING;
    }

    // Says whether any submission of a run still has to be graded.
    private boolean hasUnfinished(GradingRun run) {
        for (Submission submission : run.getSubmissions()) {
            if (isUnfinished(submission)) {
                return true;
            }
        }
        return false;
    }

    // Fills the action bar for the current situation.
    private void refreshActions() {
        if (!isOnScreen()) {
            return;
        }
        ActionBar actions = getActions();
        actions.reset();
        GradingRun run = getState().getRun();
        if (getState().isGrading()) {
            actions.setBack("Cancel", () -> askCancel());
            actions.setStatus(cancelling ? "Stopping after the current "
                    + "student." : "Results open in Review when grading "
                    + "finishes.");
            actions.setPrimary("Review results", () -> { }, false);
            return;
        }
        actions.setBack(() -> getWindow().show(Step.SET_UP));
        if (failure != null) {
            actions.setStatus("Check the files on Set up and try again.");
            return;
        }
        if (run != null && hasUnfinished(run)) {
            actions.setStatus("Students already graded keep their marks.");
            actions.setPrimary("Resume", () -> continueRun(run, false),
                    true);
            return;
        }
        if (run != null) {
            actions.setPrimary("Review results", () -> getWindow()
                    .advanceTo(Step.REVIEW), true);
        }
    }

    // Asks before cancelling.
    private void askCancel() {
        getWindow().confirm("Cancel grading?", "Students already graded "
                + "keep their marks. You can resume the rest later.",
                "Cancel grading", () -> {
                    cancelling = true;
                    getBackend().cancelGrading();
                    getActions().setStatus("Stopping after the current "
                            + "student.");
                });
    }
}
