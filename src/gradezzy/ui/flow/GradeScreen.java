/*
 * File: GradeScreen.java
 *
 * Purpose: Step 2. Shows grading as it happens: how many students are
 * done, about how long is left, what is happening now, a small bell
 * curve of the marks so far and a count of each result. Offers Cancel,
 * Resume after a cancel, and the way back to Set up when grading could
 * not run.
 *
 * Where it fits: Replaces the old Grading Progress screen. It is the
 * ProgressListener and RunCallback of every run it starts, and moves to
 * Review when a run ends.
 */
package gradezzy.ui.flow;

import java.time.format.DateTimeFormatter;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
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

    // When a run was graded, such as "15 Oct, 09:42"
    private static final DateTimeFormatter WHEN =
            DateTimeFormatter.ofPattern("d MMM, HH:mm");

    // The width of the "Now:" line, so it does not move as names change
    private static final double NOW_WIDTH = 420.0;

    // The width of the name in the "Now:" line, so the step after it
    // stays in one place
    private static final double NAME_WIDTH = 180.0;

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

    // "Now:", or a message when no student is being graded
    private final Label now = Parts.muted("");

    // The student being graded now
    private final Label nowName = Parts.muted("");

    // What is being done to that student, such as "· saving"
    private final Label nowStage = Parts.muted("");

    // Why grading could not run, shown only after a failure
    private final Label reason = Parts.muted("");

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
    private final Map<String, Submission> results = new HashMap<>();

    // The usernames in results, in the order they first got a result
    private final List<String> resultOrder = new ArrayList<>();

    // How many students this run grades
    private int total = 0;

    // How many of them are finished
    private int finished = 0;

    // When the run started, in milliseconds
    private long startedAt = 0;

    // True after Cancel was pressed
    private boolean cancelling = false;

    // True once the run has finished, stopped or failed
    private boolean ended = false;

    // The section and assessment, such as "CS102 G1 · Lab Test"
    private String about = "";

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
        view = Parts.narrowPage(
                new Node[] {header, createProgressCard(), cards});
    }

    /**
     * Returns the Grade step built in the constructor.
     */
    @Override
    public Node getView() {
        return view;
    }

    /**
     * When nothing is being graded, fills the screen again from the run
     * on screen, as it ended: one opened from Home or Runs, or one graded
     * and resumed here, so the count always covers every student. With no
     * run, says how to start. Then shows the buttons that fit: Cancel
     * while it grades, Resume when it stopped early, Review results when
     * it is done.
     */
    @Override
    public void onShow() {
        GradingRun run = getState().getRun();
        if (!getState().isGrading() && failure == null) {
            if (run != null) {
                showEndedRun(run);
            } else {
                showNothingYet();
            }
        }
        refreshActions();
    }

    // Fills the screen from a run that is not being graded: how many were
    // graded, the marks so far and each result, as when it ended.
    private void showEndedRun(GradingRun run) {
        prepare(run.getSection().getLabel());
        if (run.getAssessment() != null) {
            about = run.getSection().getLabel() + DOT
                    + run.getAssessment().getName();
        }
        for (Submission submission : run.getSubmissions()) {
            if (!isUnfinished(submission)) {
                finished++;
                addResult(submission);
            }
        }
        total = run.getSubmissions().size();
        ended = true;
        String when = "";
        if (run.getStartedAt() != null) {
            when = DOT + "graded " + run.getStartedAt().format(WHEN);
        }
        subtitle.setText(about + when + ".");
        if (hasUnfinished(run)) {
            heading.setText("Grading stopped");
            showMessage("The rest are waiting; press Resume to grade "
                    + "them.");
        } else {
            heading.setText("Grading finished");
            showMessage("");
        }
        updateCount();
    }

    // Says how to start when no batch was graded in this window yet.
    private void showNothingYet() {
        heading.setText("Nothing is being graded");
        subtitle.setText("Choose the students' zip files on Set up and "
                + "press Start grading. Progress shows here.");
        showMessage("");
        updateCount();
        timeLeft.setText("");
    }

    /**
     * Starts grading the batch chosen on Set up.
     */
    public void start() {
        prepare(getState().getSection().getLabel());
        // Students, not files: a bulk zip or a folder holds several
        total = getState().getBatch().countStudents();
        updateCount();
        getState().setGrading(true);
        // Review shows the files grading skips as "Could not open"
        getState().setSkippedFiles(
                getState().getBatch().getUnusableSubmissions());
        // The backend records "Grading started" with the real count
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
                addResult(submission);
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

    // Builds the count, time left, what is happening now, the reason a
    // run failed and the bar.
    private VBox createProgressCard() {
        done.getStyleClass().add("grade-count");
        ofTotal.getStyleClass().add("grade-of");
        // The count and time keep their width; "now" is cut instead
        done.setMinWidth(Label.USE_PREF_SIZE);
        ofTotal.setMinWidth(Label.USE_PREF_SIZE);
        timeLeft.setMinWidth(Label.USE_PREF_SIZE);
        HBox count = new HBox(Parts.GAP_SMALL / 2, done, ofTotal);
        count.setAlignment(Pos.BASELINE_LEFT);
        VBox left = new VBox(count, timeLeft);
        HBox nowLine = createNowLine();
        HBox top = new HBox(Parts.GAP, left, Parts.grow(), nowLine);
        top.setAlignment(Pos.BOTTOM_LEFT);
        reason.setWrapText(true);
        reason.setVisible(false);
        reason.setManaged(false);

        Region fill = new Region();
        fill.getStyleClass().add("track-fill");
        StackPane bar = new StackPane(fill);
        bar.getStyleClass().add("score-track");
        bar.setAlignment(Pos.CENTER_LEFT);
        fill.maxWidthProperty().bind(bar.widthProperty().multiply(progress));
        fill.minWidthProperty().bind(fill.maxWidthProperty());
        return Parts.card(null, new Node[] {top, reason, bar});
    }

    // Builds the "Now:" line. It has a fixed width and the name has a
    // fixed width, so "Now:" and the step stay put as names change.
    private HBox createNowLine() {
        Label[] parts = {now, nowName, nowStage};
        String[] styles = {"grade-now", "grade-now-name", "grade-now-stage"};
        for (int index = 0; index < parts.length; index++) {
            parts[index].getStyleClass().add("small-text");
            parts[index].getStyleClass().add(styles[index]);
            // Cut instead of pushing the count aside
            parts[index].setMinWidth(0);
        }
        nowName.setPrefWidth(NAME_WIDTH);
        nowName.setMaxWidth(NAME_WIDTH);
        HBox line = new HBox(Parts.FIGURE_UNIT_GAP,
                new Node[] {now, nowName, nowStage});
        line.setAlignment(Pos.BASELINE_LEFT);
        line.setPrefWidth(NOW_WIDTH);
        line.setMinWidth(0);
        return line;
    }

    // Shows a message in the "Now:" line in place of a student.
    private void showMessage(String message) {
        now.setText(message);
        showNowParts(false);
    }

    // Shows a student and what is being done to them in the "Now:" line.
    private void showNow(Submission submission, String words) {
        now.setText("Now:");
        nowName.setText(Students.name(submission));
        nowStage.setText(words);
        showNowParts(true);
    }

    // Shows or hides the name and the step of the "Now:" line.
    private void showNowParts(boolean shown) {
        nowName.setVisible(shown);
        nowName.setManaged(shown);
        nowStage.setVisible(shown);
        nowStage.setManaged(shown);
    }

    // Builds the card with the small bell curve.
    private VBox createMarksCard() {
        StackPane box = new StackPane(chart, noMarks);
        box.setMinHeight(BellCurveChart.SMALL_HEIGHT);
        VBox card = Parts.card("Marks so far", new Node[] {box});
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
            HBox row = Parts.row(Parts.GAP,
                    new Node[] {pills[index], Parts.grow(), counts[index]});
            row.getStyleClass().add("card-row");
            if (index == 0) {
                row.getStyleClass().add("first");
            }
            if (index == words.length - 1) {
                row.getStyleClass().add("last");
            }
            rows.getChildren().add(row);
        }
        VBox card = Parts.card("So far", new Node[] {rows});
        card.setMaxHeight(Double.MAX_VALUE);
        return card;
    }

    // ------------------------------------------------ progress listener

    /**
     * Shows how many submissions the run will grade.
     */
    @Override
    public void onRunStarted(int totalSubmissions) {
        Platform.runLater(() -> showTotal(totalSubmissions));
    }

    /**
     * Shows which student is being graded now.
     */
    @Override
    public void onSubmissionStarted(Submission submission) {
        Platform.runLater(() -> showStudent(submission));
    }

    /**
     * Shows what is happening to the student now.
     */
    @Override
    public void onStageStarted(Submission submission, String stageName) {
        Platform.runLater(() -> showStage(submission, stageName));
    }

    /**
     * Counts the student as done and updates the bar and the chart.
     */
    @Override
    public void onSubmissionFinished(Submission submission) {
        Platform.runLater(() -> countFinished(submission));
    }

    /**
     * Does nothing: onFinished, called after it, moves on.
     */
    @Override
    public void onRunFinished(GradingRun gradingRun) {
        // The callback below moves on; nothing to do here
    }

    /**
     * Shows how many students the run grades. Runs on the JavaFX thread.
     *
     * @param totalSubmissions the number of students
     */
    private void showTotal(int totalSubmissions) {
        total = totalSubmissions;
        updateCount();
    }

    /**
     * Shows which student is being graded. Runs on the JavaFX thread.
     *
     * @param submission the student
     */
    private void showStudent(Submission submission) {
        showNow(submission, "");
    }

    /**
     * Shows which student is being graded and what is being done.
     * Runs on the JavaFX thread.
     *
     * @param submission the student
     * @param stageName the name of the step of grading
     */
    private void showStage(Submission submission, String stageName) {
        int index = PipelineStages.indexOf(stageName);
        String words = "working";
        if (index >= 0 && index < STAGE_WORDS.length) {
            words = STAGE_WORDS[index];
        }
        showNow(submission, DOT.trim() + " " + words);
    }

    /**
     * Counts a finished student and keeps its result. Runs on the
     * JavaFX thread.
     *
     * @param submission the student
     */
    private void countFinished(Submission submission) {
        finished++;
        addResult(submission);
        updateCount();
    }

    // ----------------------------------------------------- run callback

    /**
     * Keeps the run and shows every result. A run that stopped early
     * waits for Resume; a finished one opens Review if the instructor
     * is still on this step.
     */
    @Override
    public void onFinished(GradingRun gradingRun) {
        getState().setGrading(false);
        getState().setRun(gradingRun);
        getState().attachSkippedFiles(gradingRun);
        end();
        if (gradingRun != null) {
            for (Submission submission : gradingRun.getSubmissions()) {
                if (!isUnfinished(submission)) {
                    addResult(submission);
                }
            }
            updateCount();
        }
        if (gradingRun != null && hasUnfinished(gradingRun)) {
            heading.setText("Grading stopped");
            showMessage("The rest are waiting; press Resume to grade "
                    + "them.");
            refreshAfterGrading();
            return;
        }
        heading.setText("Grading finished");
        showMessage("");
        getWindow().notify("Grading finished.");
        getState().reach(Step.REVIEW);
        if (isOnScreen()) {
            getWindow().show(Step.REVIEW);
            return;
        }
        refreshAfterGrading();
    }

    /**
     * Says that grading could not run and why.
     */
    @Override
    public void onFailed(String message) {
        getState().setGrading(false);
        failure = message;
        end();
        heading.setText("Grading could not run");
        showMessage("");
        reason.setText(message);
        reason.setVisible(true);
        reason.setManaged(true);
        updateCount();
        if (!isOnScreen()) {
            getWindow().notify("Grading could not run. See step 2.");
        }
        refreshAfterGrading();
    }

    // ---------------------------------------------------------- helpers

    // Keeps a student's result; a student who already has one keeps
    // their place in the order.
    private void addResult(Submission submission) {
        String username = submission.getUsername();
        if (!results.containsKey(username)) {
            resultOrder.add(username);
        }
        results.put(username, submission);
    }

    // Returns every kept result, in the order they first arrived.
    private List<Submission> resultsInOrder() {
        List<Submission> inOrder = new ArrayList<>();
        for (String username : resultOrder) {
            inOrder.add(results.get(username));
        }
        return inOrder;
    }

    // Empties the screen for a new run.
    private void prepare(String sectionLabel) {
        results.clear();
        resultOrder.clear();
        total = 0;
        finished = 0;
        cancelling = false;
        ended = false;
        failure = null;
        startedAt = System.currentTimeMillis();
        heading.setText("Grading");
        about = sectionLabel;
        Assessment assessment = getBackend().getAssessment();
        if (assessment != null) {
            about = about + DOT + assessment.getName();
        }
        subtitle.setText(about + ". You can leave this tab; grading "
                + "carries on.");
        showMessage("Starting");
        timeLeft.setText("");
        reason.setVisible(false);
        reason.setManaged(false);
    }

    // Marks the run as over, so the screen no longer says it carries on.
    private void end() {
        ended = true;
        subtitle.setText(about + ".");
    }

    // Updates the count, the bar, the time left, the chart and the rows.
    private void updateCount() {
        done.setText(String.valueOf(finished));
        ofTotal.setText("of " + Parts.plural(total, "student"));
        double share = 0;
        if (total > 0) {
            share = (double) finished / total;
        }
        progress.set(share);
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
        if (ended) {
            timeLeft.setText("Stopped");
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
        MarkStats stats = MarkStats.of(resultsInOrder(),
                assessment.getMaxTotal(),
                MarkStats.parsePassPercent(getBackend().getSetting(
                ConfigKeys.PASS_MARK_PERCENT)));
        boolean empty = stats.isEmpty();
        if (empty) {
            chart.setStats(null);
        } else {
            chart.setStats(stats);
        }
        noMarks.setVisible(empty);
    }

    // Counts each kind of result.
    private void updateRows() {
        int[] tally = new int[WAITING + 1];
        boolean onlyCompile = true;
        for (Submission submission : resultsInOrder()) {
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
        // After a failure nobody is waiting: the rest are not graded
        if (failure == null) {
            tally[WAITING] += Math.max(0, total - finished);
        }
        if (onlyCompile) {
            pills[FAILED].setText("Did not compile");
        } else {
            pills[FAILED].setText("Could not grade");
        }
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

    // Redraws the action bar here, or the screen on display when the
    // instructor is elsewhere, so no screen shows what it was before.
    private void refreshAfterGrading() {
        if (isOnScreen()) {
            refreshActions();
        } else {
            getWindow().refreshScreen();
        }
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
            if (cancelling) {
                actions.setStatus("Stopping after the current student.");
            } else {
                actions.setStatus("Results open in Review when grading "
                        + "finishes.");
            }
            actions.setPrimary("Review results", () -> doNothing(), false);
            return;
        }
        if (failure != null) {
            actions.setStatus("Check the files on Set up and try again.");
            actions.setPrimary("Back to Set up",
                    () -> getWindow().show(Step.SET_UP), true);
            return;
        }
        actions.setBack(() -> getWindow().show(Step.SET_UP));
        if (run != null && hasUnfinished(run)) {
            actions.setStatus("Students already graded keep their marks.");
            actions.setPrimary("Resume", () -> continueRun(run, false),
                    true);
            return;
        }
        if (run != null) {
            actions.setPrimary("Review results",
                    () -> getWindow().advanceTo(Step.REVIEW), true);
        }
    }

    // Asks before cancelling.
    private void askCancel() {
        getWindow().confirm("Cancel grading?", "Students already graded "
                + "keep their marks. You can resume the rest later.",
                "Cancel grading", () -> cancelGrading());
    }

    /**
     * Asks the backend to stop after the current student, once the
     * instructor has confirmed.
     */
    private void cancelGrading() {
        cancelling = true;
        getBackend().cancelGrading();
        getActions().setStatus("Stopping after the current student.");
    }

    /**
     * The action of Review results while grading runs: nothing happens,
     * because the button is disabled until grading ends.
     */
    private void doNothing() {
        // the button is disabled while grading runs
    }
}
