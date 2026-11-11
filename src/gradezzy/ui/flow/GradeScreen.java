/*
 * File: GradeScreen.java
 *
 * Purpose: Step 2. Shows grading as it happens for the section on
 * screen: how many students are done, about how long is left, what is
 * happening now, a small bell curve of the marks so far and a count of
 * each result. Offers Stop, Resume after a stop, and the way back to
 * Set up when grading could not run. Other sections can be graded at the
 * same time; each has its own batch, shown here when its section is
 * chosen.
 *
 * Where it fits: Replaces the old Grading Progress screen. It starts a
 * GradingJob for each batch, which the backend tells about progress;
 * FlowWindow hands each change of the section on screen back here, and
 * moves to Review when its batch ends.
 */
package gradezzy.ui.flow;

import java.time.format.DateTimeFormatter;
import java.util.ArrayList;
import java.util.List;

import javafx.animation.Interpolator;
import javafx.animation.KeyFrame;
import javafx.animation.KeyValue;
import javafx.animation.Timeline;
import javafx.beans.property.DoubleProperty;
import javafx.beans.property.SimpleDoubleProperty;
import javafx.geometry.Pos;
import javafx.scene.Node;
import javafx.scene.control.Button;
import javafx.scene.control.Label;
import javafx.scene.control.ScrollPane;
import javafx.scene.layout.ColumnConstraints;
import javafx.scene.layout.GridPane;
import javafx.scene.layout.HBox;
import javafx.scene.layout.Priority;
import javafx.scene.layout.Region;
import javafx.scene.layout.StackPane;
import javafx.scene.layout.VBox;
import javafx.util.Duration;

import gradezzy.config.ConfigKeys;
import gradezzy.model.Assessment;
import gradezzy.model.GradingRun;
import gradezzy.model.Section;
import gradezzy.model.Submission;
import gradezzy.model.SubmissionStatus;
import gradezzy.ui.backend.ResultState;
import gradezzy.ui.backend.StatusCounts;

/**
 * The Grade step.
 *
 * <p>Tier: SHOULD.
 */
public class GradeScreen extends FlowScreen {

    // The five steps shown under the bar, and which of them each stage
    // of grading belongs to
    private static final String[] STEP_NAMES = {"Unzip", "Check",
        "Compile", "Test", "Save"};
    private static final int[] STAGE_STEP = {0, 1, 1, 1, 2, 2, 3, 3, 4, 4};

    // Turns a share into a percentage
    private static final double PERCENT = 100.0;

    // What each step of grading means, in the order of PipelineStages
    private static final String[] STAGE_WORDS = {"unzipping",
        "checking the folders and files", "fixing small mistakes",
        "looking for answers to flag", "copying the testers",
        "compiling", "running the tests", "trying random inputs",
        "adding up the marks",
        "saving"};

    // The shortest and longest glide of the bar to a new share, in
    // milliseconds. In between it takes as long as one student takes.
    private static final double MIN_GLIDE_MILLIS = 250.0;
    private static final double MAX_GLIDE_MILLIS = 1500.0;

    // Milliseconds in a second
    private static final double MILLIS_PER_SECOND = 1000.0;

    // Seconds in a minute
    private static final int SECONDS_PER_MINUTE = 60;

    // The share of the row for each of the two small cards
    private static final double HALF = 50.0;

    // The gap between the share done and the time left
    private static final double TIME_GAP = 2.0;

    // The button that stops a batch, and the one that says so in its sheet
    private static final String STOP_GRADING = "Stop grading";

    // The separator between parts of a line
    private static final String DOT = " · ";

    // Comes between two of the steps under the bar
    private static final String STEP_ARROW = "\u203A";

    // Where the first letter of a step's words ends
    private static final int FIRST_LETTER_END = 1;

    // The fewest seconds said as time left, so it never reads "0 seconds"
    private static final long FEWEST_SECONDS = 1;

    // When a run was graded, such as "15 Oct, 09:42"
    private static final DateTimeFormatter WHEN =
            DateTimeFormatter.ofPattern("d MMM, HH:mm");

    // The rows of the "So far" card, top to bottom
    private static final int GRADED = 0;
    private static final int REVIEW = 1;
    private static final int COMPILE = 2;
    private static final int TIMEOUT = 3;
    private static final int COULD_NOT = 4;
    private static final int WAITING = 5;

    // How many rows the "So far" card has
    private static final int ROW_COUNT = WAITING + 1;

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

    // The share of students done, such as "60%"
    private final Label percent = new Label("0%");

    // The five steps each student goes through, with the current one lit
    private final HBox stageSteps = new HBox(Parts.GAP_TINY);
    private final List<Label> stepLabels = new ArrayList<>();

    // "Now:", or a message when no student is being graded
    private final Label now = Parts.muted("");

    // The student being graded now
    private final Label nowName = Parts.muted("");

    // What is being done to that student, such as "· saving"
    private final Label nowStage = Parts.muted("");

    // Why grading could not run, shown only after a failure
    private final Label reason = Parts.muted("");

    // The share the bar shows: it glides after the share done, so it keeps
    // moving between students instead of jumping when one finishes
    private final DoubleProperty shownProgress = new SimpleDoubleProperty(0);

    // The bar's glide to the newest share, or null when it is still
    private Timeline progressGlide = null;

    // The bell curve of the marks so far
    private final BellCurveChart chart = new BellCurveChart(true);

    // Shown instead of the chart until a student is graded
    private final Label noMarks = Parts.muted("Marks appear here as "
            + "students finish.");

    // The count of each row of "So far"
    private final Label[] counts = new Label[ROW_COUNT];

    // The batch shown: the section's batch being graded or just ended,
    // or one made to show a run as it ended; null when there is none
    private GradingJob shown = null;

    // The other sections being graded now, each a link to its batch
    private final HBox otherBatches = Parts.row(Parts.GAP_SMALL);

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
        otherBatches.getStyleClass().add("other-batches");
        VBox header = new VBox(heading, subtitle, otherBatches);
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
     * Shows the batch of the section on screen: the one being graded or
     * the one that just failed; else the run on screen as it ended; else
     * how to start. Then shows the buttons that fit: Stop while it
     * grades, Resume when it stopped early, Review results when it is
     * done.
     */
    @Override
    public void onShow() {
        GradingJob job = getState().getJob(getState().getSection());
        boolean liveJob = job != null
                && (!job.isEnded() || job.getFailure() != null);
        GradingRun run = getState().getRun();
        // Never the run of another section under this one's name
        boolean runOfSection = run != null && run.getSection() != null
                && run.getSection().getLabel().equalsIgnoreCase(
                        getState().getSection().getLabel());
        if (liveJob) {
            render(job, false);
        } else if (runOfSection) {
            render(GradingJob.ofEndedRun(run, describeAbout(run)), false);
        } else {
            shown = null;
            showNothingYet();
        }
        refreshActions();
    }

    // Says how to start when no batch was graded in this window yet.
    private void showNothingYet() {
        heading.setText("Nothing is being graded");
        subtitle.setText("Choose the students' zip files on Set up and "
                + "press Start grading. Progress shows here.");
        showMessage("");
        reason.setVisible(false);
        reason.setManaged(false);
        done.setText("0");
        ofTotal.setText("of 0 students");
        percent.setText("0" + Parts.PERCENT_SIGN);
        shownProgress.set(0);
        timeLeft.setText("");
        chart.setStats(null);
        noMarks.setVisible(true);
        for (Label count : counts) {
            count.setText("0");
        }
    }

    /**
     * Starts grading the batch chosen on Set up for the section on
     * screen. Other sections may be grading at the same time.
     */
    public void start() {
        GradingJob job = newJob(getState().getSection());
        // Students, not files: a bulk zip or a folder holds several
        job.setTotal(getState().getBatch().countStudents());
        job.addUsernames(getState().getBatch().listStudents());
        // Review shows the files grading skips as "Could not open"
        job.getSkippedFiles().addAll(
                getState().getBatch().getUnusableSubmissions());
        getState().startJob(job);
        render(job, false);
        // The backend records "Grading started" with the real count
        getBackend().startGrading(getState().getBatch(),
                getState().getSection(), job, job);
        refreshActions();
    }

    /**
     * Grades the batch on Set up into the section's existing run, so
     * Review and Export show the whole section.
     *
     * @param run the run the batch joins
     */
    public void startAdding(GradingRun run) {
        GradingJob job = newJob(getState().getSection());
        job.setTotal(getState().getBatch().countStudents());
        job.addUsernames(getState().getBatch().listStudents());
        job.getSkippedFiles().addAll(
                getState().getBatch().getUnusableSubmissions());
        getState().startJob(job);
        render(job, false);
        getBackend().addToRun(run, getState().getBatch(), job, job);
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
        GradingJob job = newJob(run.getSection());
        int total = 0;
        int gradedBefore = 0;
        for (Submission submission : run.getSubmissions()) {
            boolean again = retryFailed
                    && submission.getStatus() == SubmissionStatus.FAILED;
            if (GradingJob.isUnfinished(submission) || again) {
                total++;
                List<String> one = new ArrayList<>();
                one.add(submission.getUsername());
                job.addUsernames(one);
            } else {
                job.addResult(submission);
                gradedBefore++;
            }
        }
        job.setTotal(total);
        // The count goes on from the students graded before the stop
        job.setCarried(gradedBefore);
        getState().startJob(job);
        render(job, false);
        if (retryFailed) {
            getBackend().retryFailedSubmissions(run, job, job);
        } else {
            getBackend().resumeGrading(run, job, job);
        }
        refreshActions();
    }

    /**
     * Shows a batch's progress when it is the one on screen. FlowWindow
     * calls it each time the batch of the section on screen changes.
     *
     * @param job the batch
     */
    public void progressed(GradingJob job) {
        if (job == shown) {
            render(job, true);
        } else {
            renderOtherBatches();
        }
    }

    /**
     * Shows how the batch of the section on screen ended. FlowWindow
     * calls it once the window's state has the run. A finished batch
     * opens Review when the instructor is still on this step.
     *
     * @param job the batch, ended
     */
    public void ended(GradingJob job) {
        boolean finishedHere = job.getFailure() == null
                && !job.hasUnfinished() && isOnScreen();
        if (finishedHere) {
            getWindow().show(Step.REVIEW);
            return;
        }
        if (job == shown || isOnScreen()) {
            render(job, true);
        }
        if (isOnScreen()) {
            refreshActions();
        } else {
            getWindow().refreshScreen();
        }
    }

    // Makes the batch of a section, named with the assessment it grades.
    private GradingJob newJob(Section section) {
        String about = section.getLabel();
        Assessment assessment = getBackend().getAssessment();
        if (assessment != null) {
            about = about + DOT + assessment.getName();
        }
        return new GradingJob(getWindow(), section, about);
    }

    // Names a run's section and assessment, such as "CS102 G1 · Lab
    // Test 2".
    private String describeAbout(GradingRun run) {
        String about = run.getSection().getLabel();
        if (run.getAssessment() != null) {
            about = about + DOT + run.getAssessment().getName();
        }
        return about;
    }

    // Fills the screen from a batch: the heading and the line under it,
    // the count, the bar, the time left, who is graded now, the chart
    // and the rows. animate is false when another batch was shown before,
    // so the bar jumps instead of gliding from the other one.
    private void render(GradingJob job, boolean animate) {
        boolean another = job != shown;
        shown = job;
        renderHeading(job);
        renderOtherBatches();
        renderNow(job);
        done.setText(String.valueOf(job.getFinished()));
        ofTotal.setText("of " + Parts.plural(job.getTotal(), "student"));
        double share = 0;
        if (job.getTotal() > 0) {
            share = (double) job.getFinished() / job.getTotal();
        }
        long shownPercent = Math.round(share * PERCENT);
        percent.setText(shownPercent + Parts.PERCENT_SIGN);
        // An ended batch shows its final share at once, so a stopped one
        // never keeps gliding past what was graded
        boolean jump = another || !animate || job.isEnded();
        if (jump) {
            stopGlide();
            shownProgress.set(share);
        } else {
            glideTo(share);
        }
        updateTimeLeft(job);
        updateChart(job);
        updateRows(job);
    }

    // Shows the heading, the line under it and why grading failed.
    private void renderHeading(GradingJob job) {
        boolean failed = job.getFailure() != null;
        reason.setVisible(failed);
        reason.setManaged(failed);
        if (failed) {
            heading.setText("Grading could not run");
            subtitle.setText(job.getAbout() + ".");
            reason.setText(job.getFailure());
            return;
        }
        if (!job.isEnded()) {
            heading.setText("Grading");
            subtitle.setText(job.getAbout() + ". You can leave this tab or "
                    + "grade another section; grading carries on.");
            return;
        }
        String when = "";
        GradingRun run = job.getRun();
        if (run != null && run.getStartedAt() != null) {
            when = DOT + "graded " + run.getStartedAt().format(WHEN);
        }
        subtitle.setText(job.getAbout() + when + ".");
        if (job.hasUnfinished()) {
            heading.setText("Grading stopped");
        } else {
            heading.setText("Grading finished");
        }
    }

    // Lists the other sections being graded now, each with how far it has
    // got and a link that shows it, such as "Also grading: CS102 G1, 12
    // of 65". Hidden when only one section grades.
    private void renderOtherBatches() {
        otherBatches.getChildren().clear();
        for (GradingJob job : getState().getRunningJobs()) {
            if (job != shown) {
                Section section = job.getSection();
                String text = section.getLabel() + ", " + job.getFinished()
                        + " of " + job.getTotal();
                otherBatches.getChildren().add(Parts.link(text,
                        () -> getWindow().watchGrading(section)));
            }
        }
        boolean any = !otherBatches.getChildren().isEmpty();
        if (any) {
            Label also = Parts.muted("Also grading:");
            also.setMinWidth(Region.USE_PREF_SIZE);
            otherBatches.getChildren().add(0, also);
        }
        otherBatches.setVisible(any);
        otherBatches.setManaged(any);
    }

    // Shows who is being graded now and at which step, or a message.
    private void renderNow(GradingJob job) {
        if (job.isEnded() && job.hasUnfinished()) {
            showMessage("The rest are waiting; press Resume to grade "
                    + "them.");
        } else if (job.isEnded()) {
            showMessage("");
        } else if (job.getNowSubmission() == null) {
            showMessage("Starting");
        } else {
            showNow(job.getNowSubmission(), stageWords(job),
                    job.getNowStage());
        }
    }

    // Says what is being done to the student now, such as "Compiling",
    // or "" before the first step.
    private String stageWords(GradingJob job) {
        if (job.getNowStageName().isEmpty()) {
            return "";
        }
        int index = job.getNowStage();
        String words = "Working";
        if (index >= 0 && index < STAGE_WORDS.length) {
            words = STAGE_WORDS[index];
            words = Character.toUpperCase(words.charAt(0))
                    + words.substring(FIRST_LETTER_END);
        }
        return words;
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
        HBox count = new HBox(Parts.GAP_TINY, done, ofTotal);
        count.setAlignment(Pos.BASELINE_LEFT);
        // The share done and the time left, on the right of the count
        percent.getStyleClass().add("grade-percent");
        percent.setMinWidth(Label.USE_PREF_SIZE);
        timeLeft.getStyleClass().add("small-text");
        VBox right = new VBox(TIME_GAP, percent, timeLeft);
        right.setAlignment(Pos.CENTER_RIGHT);
        HBox top = new HBox(Parts.GAP, count, Parts.grow(), right);
        top.setAlignment(Pos.CENTER_LEFT);
        reason.setWrapText(true);
        reason.setVisible(false);
        reason.setManaged(false);

        Region fill = new Region();
        fill.getStyleClass().add("track-fill");
        StackPane bar = new StackPane(fill);
        bar.getStyleClass().add("score-track");
        bar.setAlignment(Pos.CENTER_LEFT);
        fill.maxWidthProperty().bind(bar.widthProperty().multiply(
                shownProgress));
        fill.minWidthProperty().bind(fill.maxWidthProperty());
        return Parts.card(null, new Node[] {top, bar, createNowLine(),
            reason});
    }

    // Builds the line under the bar: "Now grading", the student, what
    // is being done to them as a chip, and on the right the five steps
    // each student goes through, with the current one lit.
    private HBox createNowLine() {
        now.getStyleClass().addAll("small-text", "grade-now");
        nowName.getStyleClass().addAll("grade-now-name");
        nowStage.getStyleClass().addAll("small-text", "grade-now-stage");
        now.setMinWidth(Label.USE_PREF_SIZE);
        nowStage.setMinWidth(Label.USE_PREF_SIZE);
        // A long name is cut, never the stage
        nowName.setMinWidth(0);
        stageSteps.getChildren().add(stepLabel(STEP_NAMES[0]));
        for (int index = 1; index < STEP_NAMES.length; index++) {
            Label arrow = Parts.muted(STEP_ARROW);
            arrow.getStyleClass().add("small-text");
            stageSteps.getChildren().add(arrow);
            stageSteps.getChildren().add(stepLabel(STEP_NAMES[index]));
        }
        stageSteps.setAlignment(Pos.CENTER_RIGHT);
        stageSteps.setMinWidth(HBox.USE_PREF_SIZE);
        HBox line = new HBox(Parts.GAP_SMALL, new Node[] {now, nowName,
            nowStage, Parts.grow(), stageSteps});
        line.setAlignment(Pos.CENTER_LEFT);
        line.getStyleClass().add("grade-now-line");
        line.setMinWidth(0);
        return line;
    }

    // Builds the label of one step and keeps it, so it can be lit.
    private Label stepLabel(String step) {
        Label label = new Label(step);
        label.getStyleClass().addAll("small-text", "grade-step");
        label.setMinWidth(Label.USE_PREF_SIZE);
        stepLabels.add(label);
        return label;
    }

    // Shows a message in the line under the bar in place of a student.
    private void showMessage(String message) {
        now.setText(message);
        showNowParts(false);
    }

    // Shows a student and what is being done to them. The stage is the
    // index of the step of grading, or -1 before the first one.
    private void showNow(Submission submission, String words, int stage) {
        now.setText("Now grading");
        nowName.setText(Students.name(submission));
        nowStage.setText(words);
        showNowParts(true);
        boolean hasWords = !words.isEmpty();
        nowStage.setVisible(hasWords);
        nowStage.setManaged(hasWords);
        int step = -1;
        if (stage >= 0 && stage < STAGE_STEP.length) {
            step = STAGE_STEP[stage];
        }
        for (int index = 0; index < stepLabels.size(); index++) {
            Label label = stepLabels.get(index);
            label.getStyleClass().removeAll("done", "current");
            if (index < step) {
                label.getStyleClass().add("done");
            } else if (index == step) {
                label.getStyleClass().add("current");
            }
        }
    }

    // Shows or hides the name and the step of the "Now:" line.
    private void showNowParts(boolean shown) {
        nowName.setVisible(shown);
        nowName.setManaged(shown);
        nowStage.setVisible(shown);
        stageSteps.setVisible(shown);
        stageSteps.setManaged(shown);
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
            "Timed out", "Could not grade", "Waiting"};
        Tone[] tones = {Tone.GOOD, Tone.WARN, Tone.BAD, Tone.BAD, Tone.BAD,
            Tone.NEUTRAL};
        VBox rows = new VBox();
        for (int index = 0; index < words.length; index++) {
            Label pill = Parts.pill(words[index], tones[index]);
            counts[index] = Parts.strong("0");
            counts[index].getStyleClass().add("count-value");
            HBox row = Parts.row(Parts.GAP,
                    new Node[] {pill, Parts.grow(), counts[index]});
            row.getStyleClass().add("card-row");
            rows.getChildren().add(row);
        }
        int lastRow = rows.getChildren().size() - 1;
        rows.getChildren().get(0).getStyleClass().add("first");
        rows.getChildren().get(lastRow).getStyleClass().add("last");
        VBox card = Parts.card("So far", new Node[] {rows});
        card.setMaxHeight(Double.MAX_VALUE);
        return card;
    }

    // Moves the bar to a new share. With Subtle animations on, it glides
    // there at a steady speed from where it is, taking about as long as
    // one student takes, so it is still moving when the next one ends.
    // Going back, as when a new run starts, it jumps.
    private void glideTo(double target) {
        stopGlide();
        if (!isAnimated() || target <= shownProgress.get()) {
            shownProgress.set(target);
            return;
        }
        KeyValue end = new KeyValue(shownProgress, target,
                Interpolator.LINEAR);
        Duration glide = Duration.millis(findGlideMillis());
        KeyFrame frame = new KeyFrame(glide, new KeyValue[] {end});
        progressGlide = new Timeline(new KeyFrame[] {frame});
        progressGlide.play();
    }

    // Stops the bar's glide, if it is moving.
    private void stopGlide() {
        if (progressGlide != null) {
            progressGlide.stop();
            progressGlide = null;
        }
    }

    // Returns how long the bar glides: the time one student has taken so
    // far, kept between MIN_GLIDE_MILLIS and MAX_GLIDE_MILLIS.
    private double findGlideMillis() {
        if (shown == null || shown.getFinishedNow() <= 0) {
            return MIN_GLIDE_MILLIS;
        }
        double each = (System.currentTimeMillis() - shown.getStartedAt())
                / (double) shown.getFinishedNow();
        return Math.max(MIN_GLIDE_MILLIS, Math.min(MAX_GLIDE_MILLIS, each));
    }

    // Says whether Subtle animations is on in Settings.
    private boolean isAnimated() {
        return Boolean.parseBoolean(getBackend().getSetting(
                ConfigKeys.ANIMATIONS_ENABLED));
    }

    // Works out about how long is left from the time taken so far.
    private void updateTimeLeft(GradingJob job) {
        int total = job.getTotal();
        int finished = job.getFinished();
        if (total > 0 && finished >= total) {
            timeLeft.setText("Done");
            return;
        }
        if (job.isEnded()) {
            timeLeft.setText("Stopped");
            return;
        }
        // Only the students of this batch tell how long each one takes
        int finishedNow = job.getFinishedNow();
        if (finishedNow == 0) {
            timeLeft.setText("Working out the time left");
            return;
        }
        double elapsed = (System.currentTimeMillis() - job.getStartedAt())
                / MILLIS_PER_SECOND;
        int remaining = total - finished;
        double secondsLeft = elapsed / finishedNow * remaining;
        long seconds = Math.round(secondsLeft);
        timeLeft.setText("About " + describe(seconds) + " left");
    }

    // Redraws the bell curve of the graded students.
    private void updateChart(GradingJob job) {
        // The run's own marks, read from its testers; the assessment in
        // Settings only until the run is known
        Assessment assessment = getBackend().getAssessment();
        GradingRun run = job.getRun();
        if (run != null && run.getAssessment() != null) {
            assessment = run.getAssessment();
        }
        if (assessment == null) {
            return;
        }
        List<Submission> graded = job.getResults();
        String passSetting = getBackend().getSetting(
                ConfigKeys.PASS_MARK_PERCENT);
        double passPercent = MarkStats.parsePassPercent(passSetting);
        MarkStats stats = MarkStats.of(graded, assessment.getMaxTotal(),
                passPercent);
        boolean empty = stats.isEmpty();
        if (empty) {
            chart.setStats(null);
        } else {
            chart.setStats(stats);
        }
        noMarks.setVisible(empty);
    }

    // Counts each kind of result, the same way Review and Statistics do.
    private void updateRows(GradingJob job) {
        StatusCounts counted = StatusCounts.of(job.getResults(),
                getBackend());
        int[] tally = new int[ROW_COUNT];
        tally[GRADED] = counted.count(ResultState.COMPLETED);
        // A 0 nobody checked waits for a look like a flag does
        tally[REVIEW] = counted.count(ResultState.NEEDS_REVIEW)
                + counted.count(ResultState.ZERO);
        tally[COMPILE] = counted.count(ResultState.COMPILE_ERROR);
        tally[TIMEOUT] = counted.count(ResultState.TIMED_OUT);
        tally[COULD_NOT] = counted.count(ResultState.COULD_NOT_GRADE);
        tally[WAITING] = counted.count(ResultState.UNGRADED);
        // After a failure nobody is waiting: the rest are not graded
        if (job.getFailure() == null) {
            int notStarted = job.getTotal() - job.getFinished();
            tally[WAITING] += Math.max(0, notStarted);
        }
        for (int index = 0; index < tally.length; index++) {
            counts[index].setText(String.valueOf(tally[index]));
        }
    }

    // Says a number of seconds in words.
    private String describe(long seconds) {
        if (seconds < SECONDS_PER_MINUTE) {
            long shownSeconds = Math.max(FEWEST_SECONDS, seconds);
            return Parts.plural((int) shownSeconds, "second");
        }
        double exactMinutes = (double) seconds / SECONDS_PER_MINUTE;
        long minutes = Math.round(exactMinutes);
        return Parts.plural((int) minutes, "minute");
    }

    // Grades the rest of a run, unless a fix job still grades a student.
    private void resume(GradingRun run) {
        if (getWindow().blockedByFix()) {
            return;
        }
        getWindow().resumeRun(run);
    }

    // Fills the action bar for the batch on screen.
    private void refreshActions() {
        if (!isOnScreen()) {
            return;
        }
        ActionBar actions = getActions();
        actions.reset();
        if (shown != null && !shown.isEnded()) {
            // Stop sits beside the main button, where the eye already is
            GradingJob job = shown;
            Button stop = Parts.secondary(STOP_GRADING, () -> askCancel(job));
            // Pressed once is enough: it stays off while grading stops
            stop.setDisable(job.isCancelling());
            actions.addSecondary(stop);
            if (job.isCancelling()) {
                actions.setStatus("Stopping after the current student.");
            } else {
                actions.setStatus("Results open in Review when grading "
                        + "finishes.");
            }
            actions.setPrimary("Review results", () -> doNothing(), false);
            return;
        }
        if (shown != null && shown.getFailure() != null) {
            actions.setStatus("Check the files on Set up and try again.");
            actions.setPrimary("Back to Set up",
                    () -> getWindow().show(Step.SET_UP), true);
            return;
        }
        actions.setBack(() -> getWindow().show(Step.SET_UP));
        GradingRun run = getState().getRun();
        boolean stopped = run != null && shown != null
                && shown.hasUnfinished();
        if (stopped) {
            actions.setStatus("Students already graded keep their marks.");
            actions.setPrimary("Resume", () -> resume(run), true);
            return;
        }
        if (run != null) {
            actions.setPrimary("Review results",
                    () -> getWindow().advanceTo(Step.REVIEW), true);
        }
    }

    // Asks before stopping a batch.
    private void askCancel(GradingJob job) {
        Label text = Parts.subtitle("Students already graded keep their "
                + "marks. You can resume the rest later. Other sections "
                + "keep grading.");
        text.setMaxWidth(Parts.TEXT_WIDTH);
        text.setWrapText(true);
        getWindow().confirm("Stop grading " + job.getSection().getLabel()
                + "?", text, "Keep grading", null, null, STOP_GRADING,
                () -> cancelGrading(job), true);
    }

    // Asks the backend to stop a batch after the current student, once
    // the instructor has confirmed.
    private void cancelGrading(GradingJob job) {
        job.markCancelling();
        getBackend().cancelGrading(job.getSection());
        refreshActions();
    }

    /**
     * The action of Review results while grading runs: nothing happens,
     * because the button is disabled until grading ends.
     */
    private void doNothing() {
        // the button is disabled while grading runs
    }
}
