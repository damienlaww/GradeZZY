/*
 * File: ProgressController.java
 *
 * Purpose: Controller of the grading progress screen.
 *
 * Where it fits: Shows the run followed by GradingMonitor, with Cancel,
 * Resume and Retry. It can be left and opened again during a run.
 */
package gradezzy.ui;

import javafx.beans.property.ReadOnlyStringWrapper;
import javafx.fxml.FXML;
import javafx.scene.control.Button;
import javafx.scene.control.Label;
import javafx.scene.control.ListCell;
import javafx.scene.control.ListView;
import javafx.scene.control.ProgressBar;
import javafx.scene.control.TableCell;
import javafx.scene.control.TableColumn;
import javafx.scene.control.TableView;
import javafx.scene.layout.GridPane;
import javafx.scene.layout.VBox;

import gradezzy.model.GradingRun;
import gradezzy.ui.GradingMonitor.ProgressRow;
import gradezzy.ui.GradingMonitor.RunStatus;
import gradezzy.ui.component.EmptyState;
import gradezzy.ui.component.Icon;
import gradezzy.ui.component.Labels;
import gradezzy.ui.component.MetricTile;
import gradezzy.ui.component.Responsive;
import gradezzy.ui.component.StatusBadge;
import gradezzy.ui.component.Tone;

/**
 * Controller of the grading progress screen.
 */
public class ProgressController extends BaseController {

    // The share of the width given to the student list
    private static final double STUDENTS_PERCENT = 60.0;

    // The width below which the issue list moves under the students
    private static final double STACK_WIDTH = 700.0;

    // The width of the student column
    private static final double STUDENT_WIDTH = 170.0;

    // The width of the status column
    private static final double STATUS_WIDTH = 160.0;

    // The width of the detail column
    private static final double DETAIL_WIDTH = 240.0;

    @FXML
    private Label statusLabel;

    @FXML
    private Button retryButton;

    @FXML
    private Button resultsButton;

    @FXML
    private Button cancelButton;

    @FXML
    private Button resumeButton;

    @FXML
    private Button reviewButton;

    @FXML
    private VBox emptyHolder;

    @FXML
    private VBox runBox;

    @FXML
    private Label countLabel;

    @FXML
    private Label percentLabel;

    @FXML
    private ProgressBar progressBar;

    @FXML
    private Label nowLabel;

    @FXML
    private MetricTile processedTile;

    @FXML
    private MetricTile issuesTile;

    @FXML
    private MetricTile elapsedTile;

    @FXML
    private MetricTile remainingTile;

    @FXML
    private GridPane columns;

    @FXML
    private VBox studentsPanel;

    @FXML
    private VBox issuesPanel;

    @FXML
    private TableView<ProgressRow> rowTable;

    @FXML
    private ListView<String> issueList;

    /**
     * Creates a new progress controller.
     */
    public ProgressController() {
        // nothing to set up
    }

    /**
     * Called by JavaFX after the FXML file is loaded.
     */
    @FXML
    public void initialize() {
        Responsive.twoColumns(columns, studentsPanel, issuesPanel,
                STUDENTS_PERCENT, STACK_WIDTH);

        TableColumn<ProgressRow, String> student = new TableColumn<>(
                "Student");
        student.setCellValueFactory(data -> new ReadOnlyStringWrapper(
                data.getValue().getUsername()));
        student.setPrefWidth(STUDENT_WIDTH);
        TableColumn<ProgressRow, String> status = new TableColumn<>(
                "Status");
        status.setCellValueFactory(data -> data.getValue().statusProperty());
        status.setCellFactory(column -> new StatusCell());
        status.setPrefWidth(STATUS_WIDTH);
        TableColumn<ProgressRow, String> detail = new TableColumn<>(
                "Stage or issue");
        detail.setCellValueFactory(data -> data.getValue().detailProperty());
        detail.setPrefWidth(DETAIL_WIDTH);
        rowTable.getColumns().add(student);
        rowTable.getColumns().add(status);
        rowTable.getColumns().add(detail);
        rowTable.setPlaceholder(new Label("Students appear here as they "
                + "are reached."));
        issueList.setPlaceholder(new Label("No execution issues so far."));
        issueList.setCellFactory(list -> new IssueCell());
    }

    /**
     * Connects the screen to the run monitor.
     */
    @Override
    public void onAttach() {
        GradingMonitor monitor = getState().getMonitor();
        rowTable.setItems(monitor.getRows());
        issueList.setItems(monitor.getIssueLines());
        statusLabel.textProperty().bind(monitor.messageProperty());

        monitor.statusProperty().addListener(
                (observable, oldValue, newValue) -> refresh());
        monitor.doneProperty().addListener(
                (observable, oldValue, newValue) -> refresh());
        monitor.totalProperty().addListener(
                (observable, oldValue, newValue) -> refresh());
        monitor.issuesProperty().addListener(
                (observable, oldValue, newValue) -> refresh());
        monitor.stageIndexProperty().addListener(
                (observable, oldValue, newValue) -> refreshStage());
        monitor.currentUsernameProperty().addListener(
                (observable, oldValue, newValue) -> refreshStage());
        monitor.elapsedSecondsProperty().addListener(
                (observable, oldValue, newValue) -> refreshTimes());
        monitor.remainingSecondsProperty().addListener(
                (observable, oldValue, newValue) -> refreshTimes());
    }

    /**
     * Refreshes the screen each time it is shown.
     */
    @Override
    public void onShow() {
        refresh();
    }

    /**
     * Asks the batch to stop after the current submission, once confirmed.
     */
    @FXML
    public void handleCancel() {
        GradingMonitor monitor = getState().getMonitor();
        if (!monitor.isActive()) {
            return;
        }
        int done = monitor.doneProperty().get();
        getShell().confirm("Cancel grading?", "Grading stops after the "
                + "current submission. The " + done + " completed result"
                + Labels.plural(done) + " are kept, and you can resume the "
                + "rest later.", "Cancel Grading", "Keep Grading", true,
                () -> {
                    monitor.markCancelling();
                    getBackend().cancelGrading();
                    getBackend().recordAudit("Grading cancelled",
                            "Stopped after the current submission; "
                            + done + " completed results kept.");
                    refresh();
                });
    }

    /**
     * Grades the failed and cancelled submissions of the last run again.
     */
    @FXML
    public void handleRetry() {
        GradingRun run = lastRun();
        if (run == null) {
            showError("There is no run to retry. Grade a batch first.");
            return;
        }
        getState().getMonitor().begin("Retrying the failed and cancelled "
                + "submissions of run " + run.getId() + ".");
        getBackend().recordAudit("Retry started", "Failed and cancelled "
                + "submissions of run " + run.getId());
        getBackend().retryFailedSubmissions(run, getState().getMonitor(),
                new RunFollower(getBackend(), getState(), getShell(),
                "Retry"));
        refresh();
    }

    /**
     * Continues the cancelled run with the submissions not yet graded.
     */
    @FXML
    public void handleResume() {
        GradingRun run = lastRun();
        if (run == null) {
            showError("There is no cancelled run to resume.");
            return;
        }
        getState().getMonitor().begin("Resuming run " + run.getId()
                + ". Completed submissions are not graded again.");
        getBackend().recordAudit("Grading resumed", "Run " + run.getId());
        getBackend().resumeGrading(run, getState().getMonitor(),
                new RunFollower(getBackend(), getState(), getShell(),
                "Resumed grading"));
        refresh();
    }

    /**
     * Opens the Review Anomalies screen.
     */
    @FXML
    public void handleReview() {
        getShell().navigate(Screen.ANOMALY_LOG);
    }

    /**
     * Opens the Results screen.
     */
    @FXML
    public void handleViewResults() {
        getShell().navigate(Screen.RESULTS);
    }

    // Returns the run the monitor followed last.
    private GradingRun lastRun() {
        GradingRun run = getState().getMonitor().getRun();
        if (run != null) {
            return run;
        }
        return getBackend().getRun(getState().getSection());
    }

    // Redraws the counts, the buttons and the empty state.
    private void refresh() {
        GradingMonitor monitor = getState().getMonitor();
        RunStatus status = monitor.statusProperty().get();
        boolean idle = status == RunStatus.IDLE;

        emptyHolder.getChildren().clear();
        if (idle) {
            EmptyState empty = new EmptyState(Icon.PROGRESS,
                    "No grading run in this session",
                    "Add the inputs on the Submissions screen, then press "
                    + "Start Grading. Progress appears here.");
            empty.setAction("Go to Submissions",
                    () -> getShell().navigate(Screen.UPLOAD));
            emptyHolder.getChildren().add(empty);
        }
        runBox.setVisible(!idle);
        runBox.setManaged(!idle);

        int done = monitor.doneProperty().get();
        int total = monitor.totalProperty().get();
        countLabel.setText(done + " of " + total + " submission"
                + Labels.plural(total) + " processed");
        if (monitor.isActive() && total == 0) {
            progressBar.setProgress(ProgressBar.INDETERMINATE_PROGRESS);
            percentLabel.setText("");
        } else {
            progressBar.setProgress(monitor.fractionProperty().get());
            percentLabel.setText(Labels.formatPercent(
                    monitor.fractionProperty().get()));
        }
        progressBar.setAccessibleText(countLabel.getText());

        processedTile.setValue(done + " of " + total);
        processedTile.setCaption("Completed results are kept");
        int issues = monitor.issuesProperty().get();
        issuesTile.setValue(String.valueOf(issues));
        issuesTile.setCaption(issueCaption(issues,
                monitor.failuresProperty().get()));

        showButtons(status, monitor.failuresProperty().get());
        refreshStage();
        refreshTimes();
    }

    // Shows only the buttons that make sense for the run's status.
    private void showButtons(RunStatus status, int failures) {
        boolean active = status == RunStatus.RUNNING
                || status == RunStatus.CANCELLING;
        boolean cancelled = status == RunStatus.CANCELLED;
        boolean finished = status == RunStatus.FINISHED;

        show(cancelButton, active);
        cancelButton.setDisable(status == RunStatus.CANCELLING);
        show(resumeButton, cancelled);
        show(reviewButton, finished);
        show(resultsButton, cancelled || finished);
        show(retryButton, (cancelled || finished) && failures > 0);
    }

    // Describes the student and stage currently being processed.
    private void refreshStage() {
        GradingMonitor monitor = getState().getMonitor();
        String username = monitor.currentUsernameProperty().get();
        if (monitor.isActive() && !username.isEmpty()) {
            nowLabel.setText("Now grading " + username + ": "
                    + monitor.currentStageProperty().get() + ".");
        } else if (monitor.isActive()) {
            nowLabel.setText("Preparing the batch.");
        } else {
            nowLabel.setText("Nothing is being graded now.");
        }
    }

    // Shows the elapsed time and the estimate.
    private void refreshTimes() {
        GradingMonitor monitor = getState().getMonitor();
        elapsedTile.setValue(Labels.formatDuration(
                monitor.elapsedSecondsProperty().get()));
        elapsedTile.setCaption("Since the run started");
        remainingTile.setValue(Labels.formatDuration(
                monitor.remainingSecondsProperty().get()));
        remainingTile.setCaption("An estimate, not a promise");
    }

    // Describes the execution issues in a few words.
    private String issueCaption(int issues, int failures) {
        if (issues == 0) {
            return "None so far";
        }
        if (failures == 0) {
            return "Recorded; the batch carried on";
        }
        return failures + " could not be graded; the batch carried on";
    }

    // Shows or hides a button and frees its space.
    private void show(Button button, boolean visible) {
        button.setVisible(visible);
        button.setManaged(visible);
    }

    /**
     * Shows a student's status as an icon and a label.
     */
    private static final class StatusCell
            extends TableCell<ProgressRow, String> {

        /**
         * Shows one status.
         *
         * @param item the status
         * @param empty whether the row is empty
         */
        @Override
        protected void updateItem(String item, boolean empty) {
            super.updateItem(item, empty);
            setText(null);
            ProgressRow row = null;
            if (getTableRow() != null) {
                row = getTableRow().getItem();
            }
            if (empty || item == null || row == null) {
                setGraphic(null);
                return;
            }
            setGraphic(new StatusBadge(item, row.toneProperty().get()));
        }
    }

    /**
     * Shows one execution issue as a warning icon and its words.
     */
    private static final class IssueCell extends ListCell<String> {

        /**
         * Shows one issue.
         *
         * @param item the student and the issue
         * @param empty whether the row is empty
         */
        @Override
        protected void updateItem(String item, boolean empty) {
            super.updateItem(item, empty);
            setText(null);
            if (empty || item == null) {
                setGraphic(null);
                return;
            }
            setGraphic(new StatusBadge(item, Tone.WARNING));
        }
    }
}
