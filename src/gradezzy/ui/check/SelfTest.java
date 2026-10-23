/*
 * File: SelfTest.java
 *
 * Purpose: Runs the seven demo flows of the brief without a window and
 * checks how each one ends.
 *
 * Where it fits: Run by "sh run.sh --selftest". It builds the main window
 * off screen with the demo backend and calls the handlers the buttons call.
 */
package gradezzy.ui.check;

import java.io.IOException;
import java.util.ArrayList;
import java.util.List;

import javafx.scene.Node;
import javafx.scene.Parent;
import javafx.scene.control.Button;
import javafx.scene.control.Label;
import javafx.scene.control.ScrollPane;

import gradezzy.model.Anomaly;
import gradezzy.model.AnomalyType;
import gradezzy.model.FixStatus;
import gradezzy.model.GradingRun;
import gradezzy.model.QuestionResult;
import gradezzy.model.Submission;
import gradezzy.model.SubmissionStatus;
import gradezzy.model.TestCaseResult;
import gradezzy.service.ServiceRegistry;
import gradezzy.ui.AnomalyLogController;
import gradezzy.ui.GradingMonitor;
import gradezzy.ui.MainController;
import gradezzy.ui.ProgressController;
import gradezzy.ui.ResultsController;
import gradezzy.ui.Screen;
import gradezzy.ui.ScreenNavigator;
import gradezzy.ui.ShellBuilder;
import gradezzy.ui.StudentReportController;
import gradezzy.ui.ThemeManager;
import gradezzy.ui.UiState;
import gradezzy.ui.UploadController;
import gradezzy.ui.ValidationController;
import gradezzy.ui.backend.ActionResult;
import gradezzy.ui.backend.ImportBatch;
import gradezzy.ui.backend.ImportItem;
import gradezzy.ui.backend.ImportState;
import gradezzy.ui.backend.InputKind;
import gradezzy.ui.backend.PipelineStages;
import gradezzy.ui.backend.RepairItem;
import gradezzy.ui.backend.ReviewDecision;
import gradezzy.ui.component.Labels;
import gradezzy.ui.component.MetricTile;
import gradezzy.ui.demo.DemoBackend;
import gradezzy.ui.demo.DemoData;

/**
 * Runs the seven demo flows and checks their outcomes.
 */
public class SelfTest {

    // The size of the off-screen window
    private static final double WIDTH = 1200.0;
    private static final double HEIGHT = 800.0;

    // Steps in one submission: start, eight more stages, then finish
    private static final int STEPS_PER_SUBMISSION = 10;

    // The submissions graded before Cancel in flow 2
    private static final int GRADED_BEFORE_CANCEL = 3;

    // How far into the last submission flow 2 checks the stage
    private static final int STEPS_BEFORE_STAGE_CHECK = 4;

    // The six sample students
    private static final int SAMPLE_COUNT = 6;

    // The total of learner.bravo before and after the retry
    private static final double BRAVO_BEFORE = 19.0;
    private static final double BRAVO_AFTER = 20.0;

    // The question adjusted in flow 5, with its old and new marks
    private static final String ADJUSTED_QUESTION = "Q1b";
    private static final double ADJUSTED_FROM = 3.0;
    private static final double ADJUSTED_TO = 2.0;

    // A section with no run, for flow 6
    private static final String EMPTY_SECTION = "G2";

    /**
     * One flow of the self-test.
     */
    private interface Flow {

        /**
         * Runs the flow and records every failed check.
         *
         * @throws IOException if a screen cannot be loaded
         */
        void run() throws IOException;
    }

    // Every service of the application
    private final ServiceRegistry services;

    // The demo backend, stepped by hand
    private DemoBackend backend = null;

    // The state shared by the screens
    private UiState state = null;

    // The off-screen window
    private ShellBuilder builder = null;

    // The controller of the off-screen window
    private MainController main = null;

    // The failed checks of the flow being run
    private final List<String> problems = new ArrayList<>();

    // The repair approved in flow 3, checked again in flow 7
    private Anomaly approvedRepair = null;

    // The flag rejected and put back to Open in flow 3
    private Anomaly reopenedFlag = null;

    /**
     * Creates the self-test.
     *
     * @param services every service of the application
     */
    public SelfTest(ServiceRegistry services) {
        this.services = services;
    }

    /**
     * Runs the seven flows in order and prints PASS or FAIL for each.
     *
     * @return 0 when every flow passed, 1 otherwise
     */
    public int run() {
        try {
            setUp();
        } catch (IOException exception) {
            System.out.println("FAIL  set-up: " + exception.getMessage());
            return 1;
        }

        int failed = 0;
        failed += runFlow("1 Import six ZIP files, replace the unreadable "
                + "one, reach Ready to Grade", () -> flowImport());
        failed += runFlow("2 Grade with a compile error and a timeout, "
                + "cancel, resume", () -> flowGrade());
        failed += runFlow("3 Approve a repair, reject a flag, undo",
                () -> flowDecisions());
        failed += runFlow("4 Open learner.bravo at 19/20, retry Q1a",
                () -> flowRetry());
        failed += runFlow("5 Adjust a mark with a reason, Saved, Undo",
                () -> flowAdjust());
        failed += runFlow("6 Filter by module and section, export",
                () -> flowExport());
        failed += runFlow("7 Switch themes without losing state",
                () -> flowTheme());

        if (failed == 0) {
            System.out.println("All 7 flows passed.");
            return 0;
        }
        System.out.println(failed + " of 7 flows failed.");
        return 1;
    }

    // Builds the off-screen window with the demo backend.
    private void setUp() throws IOException {
        backend = new DemoBackend(services);
        backend.setManualStepping(true);
        state = ShellBuilder.createState(services.getConfig());
        state.animationsEnabledProperty().set(false);
        state.darkProperty().set(false);
        builder = new ShellBuilder(services, backend, state,
                new ThemeManager());
        builder.build(WIDTH, HEIGHT);
        main = builder.getController();
        main.setAutoConfirm(true);
    }

    // Runs one flow and prints its outcome.
    private int runFlow(String name, Flow flow) {
        problems.clear();
        try {
            flow.run();
        } catch (IOException exception) {
            problems.add("a screen could not load: "
                    + exception.getMessage());
        } catch (RuntimeException exception) {
            problems.add("unexpected " + exception);
            exception.printStackTrace(System.out);
        }

        if (problems.isEmpty()) {
            System.out.println("PASS  " + name);
            return 0;
        }
        System.out.println("FAIL  " + name);
        for (String problem : problems) {
            System.out.println("      - " + problem);
        }
        return 1;
    }

    // Flow 1: import six files, replace the bad one, add roster and testers.
    private void flowImport() throws IOException {
        main.navigate(Screen.UPLOAD);
        UploadController upload = (UploadController) controller(
                Screen.UPLOAD);
        ImportBatch batch = state.getImportBatch();
        Parent view = builder.getNavigator().getView(Screen.UPLOAD);
        Node start = findNode(view, "#startButton");
        check(start != null && start.isDisabled(),
                "Start Grading is unavailable before any file is added");

        upload.handleChooseFiles();
        List<ImportItem> files = batch.getItems(InputKind.SUBMISSION);
        check(files.size() == SAMPLE_COUNT, "six ZIP files were added");
        check(batch.count(InputKind.SUBMISSION, ImportState.INVALID) == 1,
                "exactly one ZIP file cannot be used");
        check(!batch.isReady(), "grading is blocked at first");
        check(start != null && start.isDisabled(),
                "Start Grading stays unavailable while grading is blocked");
        check(isShown(view, "#blockingBanner"),
                "the screen lists why grading cannot start");
        check(reasonsMention(batch, "cannot be used"),
                "the blocking reasons name the unusable ZIP file");
        check(reasonsMention(batch, "roster"),
                "the blocking reasons name the missing roster");

        List<ImportItem> invalid = new ArrayList<>();
        for (ImportItem item : files) {
            if (batch.getEffectiveState(item) == ImportState.INVALID) {
                invalid.add(item);
            }
        }
        upload.selectFiles(invalid);
        upload.handleReplace();
        check(batch.count(InputKind.SUBMISSION, ImportState.INVALID) == 0,
                "the unusable ZIP file was replaced");

        upload.chooseInput(InputKind.ROSTER);
        upload.chooseInput(InputKind.TESTERS);
        check(batch.isReady(), "the batch is Ready to Grade");
        check(start != null && !start.isDisabled(),
                "Start Grading is available once the batch is ready");
        check(!isShown(view, "#blockingBanner"),
                "the list of blocking reasons is gone");
    }

    // Flow 2: grade with a compile error and a timeout, cancel, then resume.
    private void flowGrade() throws IOException {
        UploadController upload = (UploadController) controller(
                Screen.UPLOAD);
        upload.handleStartGrading();
        GradingMonitor monitor = state.getMonitor();
        check(monitor.isActive(), "grading started");
        check(main.getCurrentScreen() == Screen.PROGRESS,
                "the Grading Progress screen opened");
        Parent view = builder.getNavigator().getView(Screen.PROGRESS);

        backend.stepGrading(STEPS_PER_SUBMISSION * (GRADED_BEFORE_CANCEL - 1)
                + STEPS_BEFORE_STAGE_CHECK);
        String stage = monitor.currentStageProperty().get();
        check(PipelineStages.indexOf(stage) >= 0,
                "the monitor reports the stage being run");
        check(labelText(view, "#nowLabel").contains(stage),
                "the screen names the stage being run");
        backend.stepGrading(STEPS_PER_SUBMISSION - STEPS_BEFORE_STAGE_CHECK);
        check(monitor.doneProperty().get() == GRADED_BEFORE_CANCEL,
                "three submissions finished before Cancel");
        check(monitor.issuesProperty().get() == 2,
                "a timeout and a compile error were recorded");
        check(showsDuration(view, "#elapsedTile",
                monitor.elapsedSecondsProperty().get()),
                "the screen shows the elapsed time");
        long left = monitor.remainingSecondsProperty().get();
        check(left >= 0 && showsDuration(view, "#remainingTile", left),
                "the screen shows an estimate of the time left");

        ProgressController progress = (ProgressController) controller(
                Screen.PROGRESS);
        main.setAutoConfirm(false);
        progress.handleCancel();
        String explanation = sheetMessage();
        check(explanation.contains("completed result")
                && explanation.contains("kept"),
                "the Cancel sheet says completed results are kept");
        pressSheetAction();
        main.setAutoConfirm(true);
        check(!main.isSheetShown(), "the Cancel sheet closed");
        backend.finishGrading();
        check(monitor.statusProperty().get()
                == GradingMonitor.RunStatus.CANCELLED, "the run was cancelled");

        GradingRun run = monitor.getRun();
        check(run != null, "the cancelled run is kept");
        if (run == null) {
            return;
        }
        check(count(run, SubmissionStatus.GRADED) == GRADED_BEFORE_CANCEL,
                "the three finished results were kept");
        check(count(run, SubmissionStatus.CANCELLED)
                == SAMPLE_COUNT - GRADED_BEFORE_CANCEL,
                "the other three were cancelled, not graded");

        progress.handleResume();
        backend.finishGrading();
        check(monitor.statusProperty().get()
                == GradingMonitor.RunStatus.FINISHED, "the resumed run "
                + "finished");
        check(count(run, SubmissionStatus.GRADED) == SAMPLE_COUNT,
                "every submission is graded after Resume");
        check(backend.getGradeCount(DemoData.USERNAMES[0]) == 1,
                "Resume did not grade a finished submission again");
        check(backend.getRun(state.getSection()) == run,
                "Results show the new run");
    }

    // Flow 3: approve a repair, reject a flag, then undo the rejection.
    private void flowDecisions() throws IOException {
        main.navigate(Screen.VALIDATION);
        ValidationController validation = (ValidationController) controller(
                Screen.VALIDATION);
        RepairItem waiting = null;
        for (RepairItem repair : validation.getShownRepairs()) {
            if (waiting == null && !repair.isBlocked()
                    && repair.getAnomaly().getFixStatus()
                    == FixStatus.APPLIED) {
                waiting = repair;
            }
        }
        check(waiting != null, "a repair waits for review");
        if (waiting != null) {
            List<String> ids = new ArrayList<>();
            ids.add(waiting.getAnomaly().getId());
            validation.selectRepairs(ids);
            validation.handleApproveSelected();
            check(waiting.getAnomaly().getFixStatus() == FixStatus.APPROVED,
                    "the repair was approved");
            approvedRepair = waiting.getAnomaly();
        }

        main.navigate(Screen.ANOMALY_LOG);
        AnomalyLogController anomalies = (AnomalyLogController) controller(
                Screen.ANOMALY_LOG);
        anomalies.clearFilters();
        Anomaly flag = null;
        for (Anomaly anomaly : anomalies.getShownAnomalies()) {
            if (flag == null && anomaly.getType()
                    == AnomalyType.HARD_CODED_ANSWER) {
                flag = anomaly;
            }
        }
        check(flag != null, "the hard-coded answer flag is listed");
        if (flag == null) {
            return;
        }
        List<String> ids = new ArrayList<>();
        ids.add(flag.getId());
        anomalies.selectAnomalies(ids);
        anomalies.handleReject();
        check(backend.getDecision(flag) == ReviewDecision.REJECTED,
                "the flag was rejected");
        anomalies.selectAnomalies(ids);
        anomalies.handleUndo();
        check(backend.getDecision(flag) == ReviewDecision.OPEN,
                "Undo put the flag back to Open");
        reopenedFlag = flag;
    }

    // Flow 4: retry the timed-out Q1a of learner.bravo; only Q1a changes.
    private void flowRetry() throws IOException {
        state.setSelectedUsername(DemoData.TIMEOUT_USERNAME);
        main.navigate(Screen.STUDENT_REPORT);
        StudentReportController evidence =
                (StudentReportController) controller(Screen.STUDENT_REPORT);
        Submission bravo = evidence.getSubmission();
        check(bravo != null, "the evidence of learner.bravo opened");
        if (bravo == null) {
            return;
        }
        check(bravo.getTotalScore() == BRAVO_BEFORE, "the total is 19");
        check(DemoData.TIMEOUT_QUESTION.equals(
                evidence.getRetryQuestionId()), "Retry offers Q1a");

        QuestionResult q1a = result(bravo, DemoData.TIMEOUT_QUESTION);
        check(q1a != null && q1a.isTimedOut(), "Q1a timed out");
        check(q1a != null && unfinishedCase(q1a), "case 3 did not finish");
        double[] before = otherMarks(bravo);

        evidence.handleRetryQuestion();
        check(q1a != null && !q1a.isTimedOut()
                && q1a.getScore() == q1a.getQuestion().getMaxScore(),
                "Retry Q1a passed every case");
        check(bravo.getTotalScore() == BRAVO_AFTER, "the total is now 20");
        check(sameMarks(before, otherMarks(bravo)),
                "the other questions kept their marks");
    }

    // Flow 5: a change needs a reason, is saved, and is undone.
    private void flowAdjust() throws IOException {
        StudentReportController evidence =
                (StudentReportController) controller(Screen.STUDENT_REPORT);
        Submission bravo = evidence.getSubmission();
        check(!StudentReportController.validateAdjustment("", "2",
                ADJUSTED_FROM).isEmpty(), "a blank reason is refused");
        check(!StudentReportController.validateAdjustment("Late fix", "4",
                ADJUSTED_FROM).isEmpty(), "a mark above the maximum is "
                + "refused");
        check(StudentReportController.validateAdjustment("Late fix", "2",
                ADJUSTED_FROM).isEmpty(), "a valid change is accepted");
        if (bravo == null) {
            problems.add("no student is open");
            return;
        }

        ActionResult saved = evidence.saveAdjustment(ADJUSTED_QUESTION,
                ADJUSTED_TO, "Method reads the file twice; agreed by "
                + "the module team.");
        QuestionResult q1b = result(bravo, ADJUSTED_QUESTION);
        check(saved.isDone(), "the change was saved");
        check(q1b != null && q1b.getScore() == ADJUSTED_TO,
                "Q1b shows the new mark");
        check(backend.listAdjustments(bravo).size() == 1,
                "the change and its reason are kept");

        evidence.handleUndoAdjustment();
        check(q1b != null && q1b.getScore() == ADJUSTED_FROM,
                "Undo restored the earlier mark");
        check(backend.listAdjustments(bravo).isEmpty(),
                "Undo removed the change");
    }

    // Flow 6: results follow module and section; exports use the selection.
    private void flowExport() throws IOException {
        main.navigate(Screen.RESULTS);
        ResultsController results = (ResultsController) controller(
                Screen.RESULTS);
        results.clearFilters();

        state.sectionCodeProperty().set(EMPTY_SECTION);
        results.onShow();
        check(results.getShownSubmissions().isEmpty(),
                "a section without a run shows no results");

        state.moduleCodeProperty().set(DemoData.EMPTY_MODULE_CODE);
        state.sectionCodeProperty().set(DemoData.EMPTY_MODULE_SECTION);
        results.onShow();
        check(results.getShownSubmissions().isEmpty(),
                "the empty module shows no results");

        state.moduleCodeProperty().set(DemoData.MODULE_CODE);
        state.sectionCodeProperty().set(DemoData.SAMPLE_SECTION);
        results.onShow();
        check(results.getShownSubmissions().size() == SAMPLE_COUNT,
                "CS102 G1 shows six students");

        List<String> chosen = new ArrayList<>();
        chosen.add(DemoData.USERNAMES[0]);
        chosen.add(DemoData.TIMEOUT_USERNAME);
        results.selectUsernames(chosen);
        List<Submission> scope = results.getExportScope();
        check(scope.size() == 2, "the export uses the two selected "
                + "students");

        GradingRun run = backend.getRun(state.getSection());
        ActionResult csv = backend.export(run, scope,
                ResultsController.CSV_FORMAT);
        ActionResult json = backend.export(run, scope,
                ResultsController.JSON_FORMAT);
        check(csv.isDone() && countLines(csv.getPreview()) == 3,
                "the CSV preview has a header and two rows");
        check(json.isDone() && json.getPreview().contains(chosen.get(0))
                && !json.getPreview().contains("learner.charlie"),
                "the JSON preview holds only the selected students");
        results.handleExportCsv();
        results.handleExportJson();
    }

    // Flow 7: switching themes keeps the screens and their state.
    private void flowTheme() throws IOException {
        ScreenNavigator navigator = builder.getNavigator();
        Parent before = navigator.getView(Screen.RESULTS);
        Parent progressView = navigator.getView(Screen.PROGRESS);
        ResultsController results = (ResultsController) controller(
                Screen.RESULTS);
        ValidationController validation = (ValidationController) controller(
                Screen.VALIDATION);
        int shown = results.getShownSubmissions().size();
        List<String> selected = new ArrayList<>(state.getSelectedResults());
        GradingMonitor monitor = state.getMonitor();
        GradingMonitor.RunStatus runStatus = monitor.statusProperty().get();
        String processed = tileValue(progressView, "#processedTile");

        main.setDark(true);
        check(state.darkProperty().get(), "the dark theme is on");
        check(hasDarkSheet(), "the dark stylesheet is applied");
        main.setDark(false);
        check(!hasDarkSheet(), "the light theme is back");

        check(navigator.getView(Screen.RESULTS) == before,
                "the Results screen was not reloaded");
        check(main.getCurrentScreen() == Screen.RESULTS,
                "the same screen is still shown");
        check(results.getShownSubmissions().size() == shown,
                "the same students are shown");
        check(selected.equals(new ArrayList<>(state.getSelectedResults())),
                "the selection was kept");

        check(navigator.getView(Screen.PROGRESS) == progressView,
                "the Grading Progress screen was not reloaded");
        check(monitor.statusProperty().get() == runStatus
                && !processed.isEmpty()
                && processed.equals(tileValue(progressView,
                "#processedTile")), "the progress of the last run was kept");
        check(approvedRepair != null
                && approvedRepair.getFixStatus() == FixStatus.APPROVED
                && listsApproved(validation, approvedRepair),
                "the repair approved in flow 3 is still approved");
        check(reopenedFlag != null
                && backend.getDecision(reopenedFlag) == ReviewDecision.OPEN,
                "the flag put back to Open in flow 3 is still Open");
    }

    // Records a failed check.
    private void check(boolean passed, String expectation) {
        if (!passed) {
            problems.add("expected: " + expectation);
        }
    }

    // Returns the controller of a screen.
    private Object controller(Screen screen) throws IOException {
        return builder.getNavigator().getController(screen);
    }

    // Finds a node by "#id"; lookup misses scroll panes with no skin yet.
    private Node findNode(Node node, String selector) {
        if (selector.equals("#" + node.getId())) {
            return node;
        }
        List<Node> children = new ArrayList<>();
        if (node instanceof ScrollPane) {
            Node content = ((ScrollPane) node).getContent();
            if (content != null) {
                children.add(content);
            }
        } else if (node instanceof Parent) {
            children.addAll(((Parent) node).getChildrenUnmodifiable());
        }
        for (Node child : children) {
            Node found = findNode(child, selector);
            if (found != null) {
                return found;
            }
        }
        return null;
    }

    // Says whether a node of a view is on show.
    private boolean isShown(Parent view, String selector) {
        Node node = findNode(view, selector);
        return node != null && node.isVisible();
    }

    // Returns the text of a label of a view, or an empty string.
    private String labelText(Parent view, String selector) {
        Node node = findNode(view, selector);
        if (node instanceof Label) {
            return ((Label) node).getText();
        }
        return "";
    }

    // Returns the value of a metric tile of a view, or an empty string.
    private String tileValue(Parent view, String selector) {
        Node node = findNode(view, selector);
        if (node instanceof MetricTile) {
            return ((MetricTile) node).getValue();
        }
        return "";
    }

    // Says whether a metric tile of a view shows a duration.
    private boolean showsDuration(Parent view, String selector,
            long seconds) {
        String value = tileValue(view, selector);
        return !value.isEmpty()
                && value.equals(Labels.formatDuration(seconds));
    }

    // Returns the explanation of the sheet on show, or an empty string.
    private String sheetMessage() {
        Node holder = builder.getScene().lookup("#sheetHolder");
        if (holder == null) {
            return "";
        }
        Node message = holder.lookup(".sheet-message");
        if (message instanceof Label) {
            return ((Label) message).getText();
        }
        return "";
    }

    // Presses the sheet's action button, the last button in its row.
    private void pressSheetAction() {
        Node holder = builder.getScene().lookup("#sheetHolder");
        Node row = null;
        if (holder != null) {
            row = holder.lookup(".sheet-buttons");
        }
        List<Node> buttons = new ArrayList<>();
        if (row instanceof Parent) {
            buttons.addAll(((Parent) row).getChildrenUnmodifiable());
        }
        if (buttons.isEmpty()) {
            problems.add("expected: a sheet with buttons is on show");
            return;
        }
        Node action = buttons.get(buttons.size() - 1);
        if (action instanceof Button) {
            ((Button) action).fire();
        }
    }

    // Says whether the Validation screen lists a repair as approved.
    private boolean listsApproved(ValidationController validation,
            Anomaly anomaly) {
        for (RepairItem repair : validation.getShownRepairs()) {
            Anomaly shown = repair.getAnomaly();
            if (shown.getId().equals(anomaly.getId())
                    && shown.getFixStatus() == FixStatus.APPROVED) {
                return true;
            }
        }
        return false;
    }

    // Says whether a blocking reason holds some text.
    private boolean reasonsMention(ImportBatch batch, String text) {
        for (String reason : batch.getBlockingReasons()) {
            if (reason.toLowerCase().contains(text)) {
                return true;
            }
        }
        return false;
    }

    // Counts the submissions of a run with a status.
    private int count(GradingRun run, SubmissionStatus status) {
        int total = 0;
        for (Submission submission : run.getSubmissions()) {
            if (submission.getStatus() == status) {
                total++;
            }
        }
        return total;
    }

    // Finds the result of one question.
    private QuestionResult result(Submission submission, String questionId) {
        for (QuestionResult result : submission.getQuestionResults()) {
            if (result.getQuestion().getId().equals(questionId)) {
                return result;
            }
        }
        return null;
    }

    // Says whether a question has a case that did not finish.
    private boolean unfinishedCase(QuestionResult result) {
        for (TestCaseResult testCase : result.getTestCaseResults()) {
            if (testCase.getDetail().startsWith("Not finished")) {
                return true;
            }
        }
        return false;
    }

    // Returns the marks of every question except the timed-out one.
    private double[] otherMarks(Submission submission) {
        List<QuestionResult> results = submission.getQuestionResults();
        double[] marks = new double[results.size()];
        for (int index = 0; index < results.size(); index++) {
            QuestionResult result = results.get(index);
            if (!result.getQuestion().getId().equals(
                    DemoData.TIMEOUT_QUESTION)) {
                marks[index] = result.getScore();
            }
        }
        return marks;
    }

    // Says whether two lists of marks are equal.
    private boolean sameMarks(double[] first, double[] second) {
        if (first.length != second.length) {
            return false;
        }
        for (int index = 0; index < first.length; index++) {
            if (first[index] != second[index]) {
                return false;
            }
        }
        return true;
    }

    // Counts the non-empty lines of a text.
    private int countLines(String text) {
        int lines = 0;
        for (String line : text.split("\r?\n")) {
            if (!line.trim().isEmpty()) {
                lines++;
            }
        }
        return lines;
    }

    // Says whether the window uses the dark stylesheet.
    private boolean hasDarkSheet() {
        for (String sheet : builder.getScene().getStylesheets()) {
            if (sheet.endsWith("app-dark.css")) {
                return true;
            }
        }
        return false;
    }
}
