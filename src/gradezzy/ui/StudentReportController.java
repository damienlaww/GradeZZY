/*
 * File: StudentReportController.java
 *
 * Purpose: Controller of the Student Evidence screen.
 *
 * Where it fits: Opened from Results for one student. It explains the
 * result: the marks per question, every test case with its expected and
 * actual values, the working-copy source, the logs, the repairs, the
 * mark history and the anomalies. It offers Retry for the narrowest
 * failed question (one tester run covers every case of a question, so a
 * question is the narrowest retry), Retry Entire Submission, a manual
 * mark change with a required reason and Undo, and an export of the
 * student's report. Back to Results returns to the list as it was left.
 * The header, each tab and the Adjust Score sheet are filled by classes
 * in gradezzy.ui.view; this controller wires them and runs the actions.
 *
 * AI use: drafted with Claude Code, reviewed by Jun Sheng.
 */
package gradezzy.ui;

import java.util.ArrayList;
import java.util.List;

import javafx.fxml.FXML;
import javafx.scene.Node;
import javafx.scene.control.Button;
import javafx.scene.control.ComboBox;
import javafx.scene.control.Label;
import javafx.scene.control.SplitPane;
import javafx.scene.control.Tab;
import javafx.scene.control.TabPane;
import javafx.scene.control.TableView;
import javafx.scene.control.TextArea;
import javafx.scene.control.TreeView;
import javafx.scene.layout.FlowPane;
import javafx.scene.layout.GridPane;
import javafx.scene.layout.HBox;
import javafx.scene.layout.VBox;

import gradezzy.model.AuditEntry;
import gradezzy.model.GradingRun;
import gradezzy.model.Submission;
import gradezzy.ui.backend.ActionResult;
import gradezzy.ui.backend.Capability;
import gradezzy.ui.backend.ResultState;
import gradezzy.ui.backend.ScoreAdjustment;
import gradezzy.ui.component.Icon;
import gradezzy.ui.view.AdjustScoreSheet;
import gradezzy.ui.view.EvidenceCaseDetails;
import gradezzy.ui.view.EvidenceCaseRow;
import gradezzy.ui.view.EvidenceCasesTab;
import gradezzy.ui.view.EvidenceHeader;
import gradezzy.ui.view.EvidenceLogsTab;
import gradezzy.ui.view.EvidenceQuestions;
import gradezzy.ui.view.EvidenceRecordsTab;
import gradezzy.ui.view.EvidenceSourceTab;

/**
 * Controller of the Student Evidence screen.
 *
 * <p>Tier: SHOULD. Owner: Damien.
 */
public class StudentReportController extends BaseController {

    @FXML
    private VBox emptyHolder;

    @FXML
    private VBox contentBox;

    @FXML
    private Button backButton;

    @FXML
    private Label titleLabel;

    @FXML
    private HBox stateHolder;

    @FXML
    private Button retryButton;

    @FXML
    private Label statusLabel;

    @FXML
    private Button adjustButton;

    @FXML
    private Button exportButton;

    @FXML
    private HBox savedBar;

    @FXML
    private VBox savedBox;

    @FXML
    private Button undoButton;

    @FXML
    private FlowPane scoreChips;

    @FXML
    private TabPane tabs;

    @FXML
    private GridPane casesGrid;

    @FXML
    private VBox casesPanel;

    @FXML
    private TableView<EvidenceCaseRow> caseTable;

    @FXML
    private VBox caseDetails;

    @FXML
    private Label caseTitle;

    @FXML
    private HBox caseBadgeHolder;

    @FXML
    private VBox caseFacts;

    @FXML
    private Label expectedLabel;

    @FXML
    private Label actualLabel;

    @FXML
    private Label limitLabel;

    @FXML
    private Label keptLabel;

    @FXML
    private TextArea excerptArea;

    @FXML
    private HBox adviceBanner;

    @FXML
    private Label adviceLabel;

    @FXML
    private Label sourceNote;

    @FXML
    private SplitPane sourceSplit;

    @FXML
    private TreeView<String> fileTree;

    @FXML
    private TextArea sourceArea;

    @FXML
    private VBox fileEmptyHolder;

    @FXML
    private VBox sourceEmptyHolder;

    @FXML
    private Label logQuestionLabel;

    @FXML
    private ComboBox<String> logQuestionBox;

    @FXML
    private Label logCountLabel;

    @FXML
    private TextArea logArea;

    @FXML
    private VBox repairRows;

    @FXML
    private VBox historyContent;

    @FXML
    private TableView<ScoreAdjustment> adjustmentTable;

    @FXML
    private Label auditTitle;

    @FXML
    private TableView<AuditEntry> auditTable;

    @FXML
    private VBox historyEmptyHolder;

    @FXML
    private Label anomalySummary;

    @FXML
    private VBox anomalyRows;

    // Fills the username, status line, chips and Saved line
    private EvidenceHeader header = null;

    // Fills the Cases tab
    private EvidenceCasesTab casesView = null;

    // Fills the Source tab
    private EvidenceSourceTab sourceView = null;

    // Fills the Logs tab
    private EvidenceLogsTab logsView = null;

    // Fills the Repairs, History and Anomalies tabs
    private EvidenceRecordsTab recordsView = null;

    // The open Adjust Score sheet, or null
    private AdjustScoreSheet adjustSheet = null;

    // Whether the tab is being chosen by code
    private boolean restoringTab = false;

    /**
     * Creates a new student report controller.
     */
    public StudentReportController() {
        // nothing to set up
    }

    /**
     * Called by JavaFX after the FXML file is loaded.
     */
    @FXML
    public void initialize() {
        backButton.setGraphic(Icon.BACK.create());
        retryButton.setGraphic(Icon.RETRY.create());
        adjustButton.setGraphic(Icon.EDIT.create());
        exportButton.setGraphic(Icon.EXPORT.create());
        undoButton.setGraphic(Icon.UNDO.create());
        adviceBanner.getChildren().add(0, Icon.INFO.create());
        tabs.getSelectionModel().selectedItemProperty().addListener(
                (observable, oldValue, newValue) -> tabChosen(newValue));
    }

    /**
     * Creates the parts that fill the header, the tabs and the sheet.
     */
    @Override
    public void onAttach() {
        header = new EvidenceHeader(getBackend(), titleLabel, stateHolder,
                retryButton, adjustButton, statusLabel, scoreChips, savedBar,
                savedBox);
        EvidenceCaseDetails details = new EvidenceCaseDetails(getBackend(),
                caseTitle, caseBadgeHolder, caseFacts, expectedLabel,
                actualLabel, limitLabel, keptLabel, excerptArea, adviceLabel);
        casesView = new EvidenceCasesTab(caseTable, casesGrid, casesPanel,
                caseDetails, details, questionId -> selectQuestion(
                questionId));
        sourceView = new EvidenceSourceTab(getBackend(), sourceNote,
                sourceSplit, fileTree, sourceArea, fileEmptyHolder,
                sourceEmptyHolder);
        logsView = new EvidenceLogsTab(getState(), logQuestionLabel,
                logQuestionBox, logCountLabel, logArea);
        recordsView = new EvidenceRecordsTab(getBackend(), repairRows,
                historyContent, adjustmentTable, auditTitle, auditTable,
                historyEmptyHolder, anomalySummary, anomalyRows);
    }

    /**
     * Shows the selected student each time the screen appears.
     */
    @Override
    public void onShow() {
        refresh();
    }

    /**
     * Returns the student on show: the student selected on Results, in
     * the run of the section in the context bar.
     *
     * @return the submission, or null when no student is selected or the
     *         student is not in the run
     */
    public Submission getSubmission() {
        return EvidenceQuestions.studentIn(currentRun(),
                getState().getSelectedUsername());
    }

    /**
     * Returns the narrowest question worth retrying: the first question
     * that timed out, else the first that did not compile, else the first
     * with a failed case.
     *
     * @return the question id, or null when nothing failed
     */
    public String getRetryQuestionId() {
        return EvidenceQuestions.retryQuestionOf(getSubmission());
    }

    /**
     * Runs the tester of the failed question again. Only that question's
     * result is replaced.
     */
    @FXML
    public void handleRetryQuestion() {
        Submission shown = getSubmission();
        String questionId = EvidenceQuestions.retryQuestionOf(shown);
        if (shown == null || questionId == null) {
            showError("No question failed, so there is nothing to retry.");
            return;
        }
        ActionResult result = getBackend().retryQuestion(shown, questionId);
        if (showResult(result, "Question retried")) {
            getShell().refreshContext();
        }
        refresh();
    }

    /**
     * Grades the whole submission again from the start, after a
     * confirmation that says the current marks are replaced.
     */
    @FXML
    public void handleRetrySubmission() {
        Submission shown = getSubmission();
        if (shown == null) {
            showError("Choose a student on the Results screen first.");
            return;
        }
        if (!getBackend().supports(Capability.RETRY_SUBMISSION)) {
            getShell().showNotConnected(Capability.RETRY_SUBMISSION);
            return;
        }

        String message = "Every question of " + shown.getUsername()
                + " is graded again from the start, and the current marks "
                + "are replaced. The original ZIP file is unchanged.";
        String questionId = EvidenceQuestions.retryQuestionOf(shown);
        if (questionId != null) {
            message += " To rerun only the failed question, use Retry "
                    + questionId + " instead.";
        }
        getShell().confirm("Retry the entire submission?", message,
                "Retry Entire Submission", "Keep Results", true,
                () -> retrySubmission(shown));
    }

    /**
     * Opens the sheet that changes one mark by hand.
     */
    @FXML
    public void handleAdjustScore() {
        Submission shown = getSubmission();
        if (shown == null || !ResultState.hasTotal(shown)) {
            showError("Only a graded student has marks to adjust.");
            return;
        }
        if (!getBackend().supports(Capability.SCORE_ADJUSTMENT)) {
            getShell().showNotConnected(Capability.SCORE_ADJUSTMENT);
            return;
        }
        String questionId = EvidenceQuestions.questionInFocus(currentRun(),
                getState().getEvidenceQuestionId(), shown);
        adjustSheet = new AdjustScoreSheet(shown, questionId,
                (question, score, reason) -> saveAdjustment(question, score,
                reason), () -> closeAdjustSheet());
        getShell().showSheet(adjustSheet.getSheet());
    }

    /**
     * Changes one mark by hand, then shows the Saved line with Undo and
     * records the change in the audit log.
     *
     * @param questionId the question, such as Q1b
     * @param score the new mark
     * @param reason why the mark changed; required
     * @return what happened
     */
    public ActionResult saveAdjustment(String questionId, double score,
            String reason) {
        Submission shown = getSubmission();
        if (shown == null) {
            ActionResult missing = ActionResult.failed("Choose a student on "
                    + "the Results screen first.");
            showError(missing.getMessage());
            return missing;
        }

        double previous = EvidenceQuestions.scoreOf(shown, questionId);
        ActionResult result = getBackend().adjustScore(shown, questionId,
                score, reason);
        if (result.getOutcome() == ActionResult.Outcome.NOT_CONNECTED) {
            closeAdjustSheet();
            getShell().showNotConnected(Capability.SCORE_ADJUSTMENT);
            return result;
        }
        if (!result.isDone()) {
            if (adjustSheet != null) {
                adjustSheet.showProblem(result.getMessage());
            } else {
                showError(result.getMessage());
            }
            return result;
        }

        closeAdjustSheet();
        header.rememberSaved(shown, questionId, previous, score, reason);
        showResult(result, "Mark adjusted");
        getShell().refreshContext();
        refresh();
        return result;
    }

    /**
     * Checks the Adjust Score form.
     *
     * @param reasonText the reason typed
     * @param scoreText the new mark typed
     * @param max the most the question can earn
     * @return an empty string when the form is valid, or a plain message
     */
    public static String validateAdjustment(String reasonText,
            String scoreText, double max) {
        return AdjustScoreSheet.validate(reasonText, scoreText, max);
    }

    /**
     * Puts back the mark before the most recent manual change.
     */
    @FXML
    public void handleUndoAdjustment() {
        Submission shown = getSubmission();
        if (shown == null) {
            showError("Choose a student on the Results screen first.");
            return;
        }
        List<ScoreAdjustment> changes = getBackend().listAdjustments(shown);
        if (changes.isEmpty()) {
            showError("There is no mark change to undo for "
                    + shown.getUsername() + ".");
            return;
        }
        ActionResult result = getBackend().undoAdjustment(changes.get(0));
        if (showResult(result, "Mark change undone")) {
            header.forgetSaved();
            getShell().refreshContext();
        }
        refresh();
    }

    /**
     * Returns to Results, which restores its own search, sort, filter and
     * selection.
     */
    @FXML
    public void handleBack() {
        getShell().navigate(Screen.RESULTS);
    }

    /**
     * Exports this student's report and shows the preview.
     */
    @FXML
    public void handleExport() {
        Submission shown = getSubmission();
        if (shown == null) {
            showError("Choose a student on the Results screen first.");
            return;
        }
        if (!getBackend().supports(Capability.STUDENT_REPORT_EXPORT)) {
            getShell().showNotConnected(Capability.STUDENT_REPORT_EXPORT);
            return;
        }

        List<Submission> scope = new ArrayList<>();
        scope.add(shown);
        ActionResult result = getBackend().export(currentRun(), scope,
                ResultsController.JSON_FORMAT);
        if (result.isDone() && !result.getPreview().isEmpty()) {
            getBackend().recordAudit("Student report previewed",
                    shown.getUsername() + ": " + result.getMessage());
            getShell().showPreview("Report for " + shown.getUsername(),
                    result.getMessage(), result.getPreview());
            return;
        }
        showResult(result, "Student report exported");
    }

    /**
     * Opens the Validation screen, where every repair can be decided.
     */
    @FXML
    public void handleOpenValidation() {
        getShell().navigate(Screen.VALIDATION);
    }

    /**
     * Opens the Review Anomalies screen.
     */
    @FXML
    public void handleReviewAnomalies() {
        getShell().navigate(Screen.ANOMALY_LOG);
    }

    // Grades the submission again and shows the outcome.
    private void retrySubmission(Submission shown) {
        ActionResult result = getBackend().retrySubmission(shown);
        if (showResult(result, "Submission retried")) {
            getShell().refreshContext();
        }
        refresh();
    }

    // Closes the Adjust Score sheet when it is open.
    private void closeAdjustSheet() {
        if (adjustSheet != null) {
            adjustSheet = null;
            getShell().closeSheet();
        }
    }

    // Returns the run of the section in the context bar.
    private GradingRun currentRun() {
        return getBackend().getRun(getState().getSection());
    }

    // Redraws every part of the screen for the selected student.
    private void refresh() {
        Submission shown = getSubmission();
        boolean found = shown != null;
        show(contentBox, found);
        show(emptyHolder, !found);
        if (!found) {
            emptyHolder.getChildren().setAll(EvidenceHeader.noStudent(
                    getState().getSelectedUsername(),
                    getState().getSection().getLabel(), () -> handleBack()));
            return;
        }

        GradingRun run = currentRun();
        String questionId = EvidenceQuestions.questionInFocus(run,
                getState().getEvidenceQuestionId(), shown);
        getState().setEvidenceQuestionId(questionId);
        header.fill(shown, run);
        casesView.fill(shown, questionId);
        sourceView.fill(shown, run, questionId);
        logsView.fill(run, shown, questionId);
        recordsView.fill(run, shown);
        restoreTab();
    }

    // Makes one question the question in focus on every tab.
    private void selectQuestion(String questionId) {
        getState().setEvidenceQuestionId(questionId);
        logsView.select(questionId);
        sourceView.select(questionId);
    }

    // Shows the tab chosen last time, or the first tab.
    private void restoreTab() {
        String wanted = getState().getEvidenceTab();
        Tab chosen = tabs.getTabs().get(0);
        for (Tab tab : tabs.getTabs()) {
            if (tab.getText().equals(wanted)) {
                chosen = tab;
            }
        }
        restoringTab = true;
        tabs.getSelectionModel().select(chosen);
        restoringTab = false;
    }

    // Remembers the tab chosen by the instructor.
    private void tabChosen(Tab tab) {
        if (restoringTab || tab == null) {
            return;
        }
        getState().setEvidenceTab(tab.getText());
    }

    // Shows or hides a node and frees its space.
    private static void show(Node node, boolean visible) {
        node.setVisible(visible);
        node.setManaged(visible);
    }
}
