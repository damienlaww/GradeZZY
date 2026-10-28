/*
 * File: GradeScreen.java
 *
 * Purpose: Step 2. Shows grading as it happens: how many students are
 * done, about how long is left, what is happening now, and a status for
 * each student. Offers Cancel, and Resume after a cancel.
 *
 * Where it fits: Replaces the old Grading Progress screen. It is the
 * ProgressListener and RunCallback of every run it starts, and moves to
 * Review when a run ends.
 */
package gradezzy.ui.flow;

import java.util.LinkedHashMap;
import java.util.Map;

import javafx.application.Platform;
import javafx.geometry.Pos;
import javafx.scene.Node;
import javafx.scene.control.Label;
import javafx.scene.control.ProgressBar;
import javafx.scene.control.ScrollPane;
import javafx.scene.layout.FlowPane;
import javafx.scene.layout.HBox;
import javafx.scene.layout.VBox;

import gradezzy.grading.ProgressListener;
import gradezzy.model.GradingRun;
import gradezzy.model.Submission;
import gradezzy.model.SubmissionStatus;
import gradezzy.ui.backend.ImportItem;
import gradezzy.ui.backend.InputKind;
import gradezzy.ui.backend.PipelineStages;
import gradezzy.ui.backend.RunCallback;

/**
 * The Grade step.
 */
public class GradeScreen extends FlowScreen
        implements ProgressListener, RunCallback {

    // What each pipeline stage means, in the order of PipelineStages
    private static final String[] STAGE_WORDS = {"Unzipping",
        "Checking the folders and files", "Fixing small mistakes",
        "Looking for answers to flag", "Copying the testers",
        "Compiling", "Running the tests", "Adding up the marks",
        "Saving"};

    // Milliseconds in a second
    private static final double MILLIS_PER_SECOND = 1000.0;

    // Seconds in a minute
    private static final int SECONDS_PER_MINUTE = 60;

    // The width of one student tile
    private static final double TILE_WIDTH = 230.0;

    // The scrolling page
    private final ScrollPane view = new ScrollPane();

    // The heading
    private final Label heading = Parts.heading("Grading");

    // The line under the heading
    private final Label subtitle = Parts.subtitle("");

    // "12 of 38 students"
    private final Label count = new Label();

    // "About 1 minute left"
    private final Label timeLeft = Parts.muted("");

    // The progress bar
    private final ProgressBar bar = new ProgressBar(0);

    // What is happening now
    private final Label now = Parts.muted("");

    // One tile per student
    private final FlowPane tiles = new FlowPane(Parts.GAP, Parts.GAP);

    // The tile of each username, in the order they were added
    private final Map<String, HBox> tileByUser = new LinkedHashMap<>();

    // How many students the run will grade
    private int total = 0;

    // How many are finished
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
        count.getStyleClass().add("big-number");
        bar.setMaxWidth(Double.MAX_VALUE);
        bar.getStyleClass().add("grade-bar");
        HBox countRow = Parts.row(Parts.GAP, count, Parts.grow(), timeLeft);
        countRow.setAlignment(Pos.BASELINE_LEFT);
        VBox progress = Parts.card(null, countRow, bar, now);
        tiles.getStyleClass().add("tiles");
        VBox column = new VBox(Parts.GAP,
                new VBox(heading, subtitle), progress,
                Parts.card("Students", tiles));
        column.getStyleClass().add("wide-column");
        view.setContent(column);
        view.setFitToWidth(true);
        view.getStyleClass().add("page-scroll");
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
                addTile(item.getUsername(), item.getUsername());
            }
        }
        total = tileByUser.size();
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
            addTile(submission.getUsername(), Students.name(submission));
            boolean again = retryFailed
                    && submission.getStatus() == SubmissionStatus.FAILED;
            if (isUnfinished(submission) || again) {
                total++;
            } else {
                setTile(submission);
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
        Platform.runLater(() -> {
            now.setText("Now: " + Students.name(submission));
            HBox tile = tileFor(submission);
            replacePill(tile, Parts.pill("Grading", Tone.NEUTRAL));
            tile.getStyleClass().add("active");
        });
    }

    @Override
    public void onStageStarted(Submission submission, String stageName) {
        Platform.runLater(() -> {
            int index = PipelineStages.indexOf(stageName);
            String words = "Working";
            if (index >= 0 && index < STAGE_WORDS.length) {
                words = STAGE_WORDS[index];
            }
            now.setText("Now: " + Students.name(submission)
                    + "  ·  " + words);
        });
    }

    @Override
    public void onSubmissionFinished(Submission submission) {
        Platform.runLater(() -> {
            finished++;
            setTile(submission);
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
                setTile(submission);
            }
        }
        if (gradingRun != null && hasUnfinished(gradingRun)) {
            heading.setText("Grading stopped");
            now.setText("Graded " + finished + " of " + total
                    + ". The rest are waiting; press Resume to grade them.");
            refreshActions();
            return;
        }
        getWindow().notify("Grading finished.");
        getState().reach(Step.REVIEW);
        if (getWindow().getCurrentStep() == Step.GRADE
                && !getWindow().isPageOpen()) {
            getWindow().show(Step.REVIEW);
        }
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
        tileByUser.clear();
        tiles.getChildren().clear();
        total = 0;
        finished = 0;
        cancelling = false;
        failure = null;
        startedAt = System.currentTimeMillis();
        heading.setText("Grading " + sectionLabel);
        subtitle.setText("Each submission is unzipped, checked, compiled "
                + "and tested. You can open History or Settings while "
                + "you wait.");
        now.setText("Starting");
        timeLeft.setText("");
    }

    // Adds a waiting tile for a student.
    private void addTile(String username, String name) {
        Label nameLabel = Parts.strong(name);
        HBox tile = Parts.row(Parts.GAP_SMALL, nameLabel, Parts.grow(),
                Parts.pill("Waiting", Tone.NEUTRAL));
        tile.getStyleClass().add("student-tile");
        tile.setPrefWidth(TILE_WIDTH);
        tileByUser.put(username, tile);
        tiles.getChildren().add(tile);
    }

    // Returns the tile of a student, adding one if needed.
    private HBox tileFor(Submission submission) {
        HBox tile = tileByUser.get(submission.getUsername());
        if (tile == null) {
            addTile(submission.getUsername(), Students.name(submission));
            tile = tileByUser.get(submission.getUsername());
        }
        return tile;
    }

    // Shows a finished student's name and status.
    private void setTile(Submission submission) {
        HBox tile = tileFor(submission);
        tile.getStyleClass().remove("active");
        ((Label) tile.getChildren().get(0)).setText(
                Students.name(submission));
        if (isUnfinished(submission)) {
            replacePill(tile, Parts.pill("Waiting", Tone.NEUTRAL));
        } else {
            replacePill(tile, Students.pill(submission, getBackend()));
        }
    }

    // Puts a new pill at the end of a tile.
    private void replacePill(HBox tile, Label pill) {
        int last = tile.getChildren().size() - 1;
        tile.getChildren().set(last, pill);
    }

    // Updates the count, the bar and the time left.
    private void updateCount() {
        count.setText(finished + " of " + Parts.plural(total, "student"));
        if (total > 0) {
            bar.setProgress((double) finished / total);
        } else {
            bar.setProgress(ProgressBar.INDETERMINATE_PROGRESS);
        }
        if (finished == 0 || finished >= total) {
            timeLeft.setText(finished >= total && total > 0 ? "Done" : "");
            return;
        }
        double elapsed = (System.currentTimeMillis() - startedAt)
                / MILLIS_PER_SECOND;
        long seconds = Math.round(elapsed / finished * (total - finished));
        timeLeft.setText("About " + describe(seconds) + " left");
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
            actions.addSecondary(Parts.secondary("Cancel grading",
                    () -> askCancel()));
            actions.setStatus(cancelling ? "Stopping after the current "
                    + "student." : "");
            return;
        }
        actions.setBack(() -> getWindow().show(Step.SET_UP));
        if (failure != null) {
            actions.setStatus("Check the files on Set up and try again.");
            return;
        }
        if (run != null && hasUnfinished(run)) {
            actions.setPrimary("Resume", () -> continueRun(run, false),
                    true);
            return;
        }
        if (run != null) {
            actions.setPrimary("Continue to review", () -> getWindow()
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
