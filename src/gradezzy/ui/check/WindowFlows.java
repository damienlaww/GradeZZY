/*
 * File: WindowFlows.java
 *
 * Purpose: The seven demo flows of the brief, done in the real window with
 * real clicks, typing and keys.
 *
 * Where it fits: Run by WindowTest. Each step's outcome is read from the
 * demo backend and the window.
 */
package gradezzy.ui.check;

import java.io.IOException;
import java.util.ArrayList;
import java.util.List;

import javafx.scene.Node;
import javafx.scene.Parent;
import javafx.scene.control.ComboBox;
import javafx.scene.control.Labeled;
import javafx.scene.control.TableRow;
import javafx.scene.control.TextArea;
import javafx.scene.input.KeyCode;

import gradezzy.model.Anomaly;
import gradezzy.model.AnomalyType;
import gradezzy.model.FixStatus;
import gradezzy.model.GradingRun;
import gradezzy.model.QuestionResult;
import gradezzy.model.Submission;
import gradezzy.model.SubmissionStatus;
import gradezzy.ui.GradingMonitor;
import gradezzy.ui.ResultsController;
import gradezzy.ui.Screen;
import gradezzy.ui.backend.ImportBatch;
import gradezzy.ui.backend.ImportItem;
import gradezzy.ui.backend.ImportState;
import gradezzy.ui.backend.InputKind;
import gradezzy.ui.backend.RepairItem;
import gradezzy.ui.backend.ReviewDecision;
import gradezzy.ui.demo.DemoBackend;
import gradezzy.ui.demo.DemoData;

/**
 * The seven demo flows with real input.
 */
public class WindowFlows {

    // How long a screen or a sheet may take to appear
    private static final long SCREEN_WAIT_MILLIS = 3000L;

    // How long a whole demo run may take
    private static final long RUN_WAIT_MILLIS = 20000L;

    // The submissions finished before Cancel is pressed
    private static final int DONE_BEFORE_CANCEL = 2;

    // The six sample students
    private static final int SAMPLE_COUNT = 6;

    // The total of learner.bravo before and after the retry
    private static final double BRAVO_BEFORE = 19.0;
    private static final double BRAVO_AFTER = 20.0;

    // The question adjusted in flow 5, with its old and new marks
    private static final String ADJUSTED_QUESTION = "Q1b";
    private static final double ADJUSTED_FROM = 3.0;
    private static final double ADJUSTED_TO = 2.0;

    // The new mark and the reason typed in flow 5
    private static final String NEW_MARK = "2";
    private static final String REASON = "Tester data had a typo in case 3.";

    // A section with no run, for flow 6
    private static final String EMPTY_SECTION = "G2";

    // Lines in a two-student score sheet: a header and two rows
    private static final int TWO_STUDENT_LINES = 3;

    // The test that keeps the score
    private final WindowTest test;

    // Sends real input and reads the screen
    private final RobotDriver driver;

    /**
     * Creates the flows.
     *
     * @param test the test that keeps the score
     */
    public WindowFlows(WindowTest test) {
        this.test = test;
        this.driver = test.getDriver();
    }

    /**
     * Runs the seven flows in order.
     */
    public void runAll() {
        flowImport();
        flowGrade();
        flowDecisions();
        flowRetry();
        flowAdjust();
        flowExport();
        flowTheme();
    }

    // Flow 1: imports and replaces files until Start Grading is available.
    private void flowImport() {
        test.section("Flow 1: import six ZIP files, replace the unreadable "
                + "one, reach Ready to Grade");
        test.openFromSidebar("Submissions", Screen.UPLOAD);
        ImportBatch batch = test.getState().getImportBatch();
        Node start = test.byId("startButton");
        test.check(driver.onFx(() -> start.isDisabled()), "Start Grading "
                + "is unavailable before any file is chosen");

        test.clickText("Choose Files");
        boolean added = driver.waitUntil(() -> batch.getItems(
                InputKind.SUBMISSION).size() == SAMPLE_COUNT,
                SCREEN_WAIT_MILLIS);
        int invalid = driver.onFx(() -> batch.count(InputKind.SUBMISSION,
                ImportState.INVALID));
        test.check(added && invalid == 1, "Choose Files adds six ZIP "
                + "files, one of which cannot be used");

        Node broken = test.rowFor("list-cell", item -> isInvalidZip(item));
        test.check(broken != null, "the unusable ZIP file is in the list");
        if (broken == null) {
            return;
        }
        driver.click(broken);
        test.clickText("Replace File");
        boolean replaced = driver.waitUntil(() -> batch.count(
                InputKind.SUBMISSION, ImportState.INVALID) == 0,
                SCREEN_WAIT_MILLIS);
        test.check(replaced, "selecting it and pressing Replace File "
                + "leaves no unusable file");

        clickWithData("Choose File", "ROSTER");
        clickWithData("Choose Folder", "TESTERS");
        boolean ready = driver.waitUntil(() -> batch.isReady()
                && !start.isDisabled(), SCREEN_WAIT_MILLIS);
        test.check(ready, "after the roster and testers, Start Grading is "
                + "available");
        test.capture("10-ready-to-grade");
    }

    // Flow 2: grades, cancels and keeps results, then resumes the rest.
    private void flowGrade() {
        test.section("Flow 2: grade, cancel, resume");
        GradingMonitor monitor = test.getState().getMonitor();
        test.clickText("Start Grading");
        boolean progress = driver.waitUntil(() -> test.getMain()
                .getCurrentScreen() == Screen.PROGRESS, SCREEN_WAIT_MILLIS);
        test.check(progress && driver.onFx(() -> monitor.isActive()),
                "Start Grading opens Grading Progress and the run starts");

        driver.waitUntil(() -> monitor.doneProperty().get()
                >= DONE_BEFORE_CANCEL, RUN_WAIT_MILLIS);
        test.capture("11-grading");
        test.clickText("Cancel Grading");
        boolean asked = driver.waitUntil(() -> test.getMain()
                .isSheetShown(), SCREEN_WAIT_MILLIS);
        test.check(asked, "Cancel Grading asks for confirmation in a sheet");
        test.clickInSheet("Cancel Grading");
        boolean cancelled = driver.waitUntil(() -> monitor.statusProperty()
                .get() == GradingMonitor.RunStatus.CANCELLED,
                RUN_WAIT_MILLIS);
        GradingRun run = driver.onFx(() -> monitor.getRun());
        int graded = countStatus(run, SubmissionStatus.GRADED);
        test.check(cancelled && graded >= DONE_BEFORE_CANCEL
                && graded < SAMPLE_COUNT, "confirming cancels the run and "
                + "keeps the " + graded + " finished results");

        test.clickText("Resume Grading");
        boolean finished = driver.waitUntil(() -> monitor.statusProperty()
                .get() == GradingMonitor.RunStatus.FINISHED,
                RUN_WAIT_MILLIS);
        GradingRun resumed = driver.onFx(() -> monitor.getRun());
        DemoBackend demo = (DemoBackend) test.getBackend();
        int alphaGrades = driver.onFx(() -> demo.getGradeCount(
                DemoData.USERNAMES[0]));
        test.check(finished && countStatus(resumed, SubmissionStatus.GRADED)
                == SAMPLE_COUNT, "Resume Grading finishes every submission");
        test.check(alphaGrades == 1, "Resume did not grade a finished "
                + "submission again");
        test.capture("12-run-finished");
    }

    // Flow 3: approves a repair, rejects a flag, then undoes the rejection.
    private void flowDecisions() {
        test.section("Flow 3: approve a repair, reject a flag, undo");
        test.openFromSidebar("Submissions", Screen.UPLOAD);
        test.clickText("Open Validation");
        test.check(driver.waitUntil(() -> test.getMain().getCurrentScreen()
                == Screen.VALIDATION, SCREEN_WAIT_MILLIS),
                "Open Validation opens Validation");

        Node repairRow = test.rowFor("table-row-cell",
                item -> isWaitingRepair(item));
        test.check(repairRow != null, "a repair waits for review");
        if (repairRow != null) {
            RepairItem repair = (RepairItem) driver.onFx(
                    () -> itemOf(repairRow));
            driver.click(repairRow);
            test.clickText("Approve Selected");
            boolean approved = driver.waitUntil(() -> repair.getAnomaly()
                    .getFixStatus() == FixStatus.APPROVED,
                    SCREEN_WAIT_MILLIS);
            test.check(approved, "selecting it and pressing Approve "
                    + "Selected approves it");
            test.capture("13-repair-approved");
            driver.pause(SCREEN_WAIT_MILLIS);
            test.capture("13b-repair-approved-later");
        }

        test.openFromSidebar("Review Anomalies", Screen.ANOMALY_LOG);
        Node flagRow = test.rowFor("table-row-cell",
                item -> isHardCodedFlag(item));
        test.check(flagRow != null, "the hard-coded answer flag is listed");
        if (flagRow == null) {
            return;
        }
        test.capture("13c-review-anomalies");
        Anomaly flag = (Anomaly) driver.onFx(() -> itemOf(flagRow));
        DemoBackend demo = (DemoBackend) test.getBackend();
        driver.click(flagRow);
        test.clickText("Reject Selected");
        boolean rejected = driver.waitUntil(() -> demo.getDecision(flag)
                == ReviewDecision.REJECTED, SCREEN_WAIT_MILLIS);
        test.check(rejected, "Reject Selected rejects the flag");

        test.clickText("Undo Decision");
        boolean open = driver.waitUntil(() -> demo.getDecision(flag)
                == ReviewDecision.OPEN, SCREEN_WAIT_MILLIS);
        test.check(rejected && open, "Undo Decision puts the rejected flag "
                + "back to Open");
    }

    // Flow 4: retries learner.bravo's Q1a timeout, taking 19 to 20.
    private void flowRetry() {
        test.section("Flow 4: open learner.bravo from Results, retry Q1a");
        test.openFromSidebar("Results", Screen.RESULTS);
        Node bravoRow = test.rowFor("table-row-cell",
                item -> isStudent(item, DemoData.TIMEOUT_USERNAME));
        test.check(bravoRow != null, "learner.bravo is in the table");
        if (bravoRow == null) {
            return;
        }
        driver.doubleClick(bravoRow);
        boolean opened = driver.waitUntil(() -> test.getMain()
                .getCurrentScreen() == Screen.STUDENT_REPORT,
                SCREEN_WAIT_MILLIS);
        test.check(opened && total(DemoData.TIMEOUT_USERNAME)
                == BRAVO_BEFORE, "a double click opens the evidence at 19");
        test.capture("14-evidence-bravo");

        String retryText = "Retry " + DemoData.TIMEOUT_QUESTION;
        test.check(test.clickText(retryText), "the screen offers "
                + retryText);
        boolean retried = driver.waitUntil(() -> total(
                DemoData.TIMEOUT_USERNAME) == BRAVO_AFTER,
                SCREEN_WAIT_MILLIS);
        test.check(retried, retryText + " brings the total to 20");
    }

    // Flow 5: a change needs a reason, is saved, and is undone.
    private void flowAdjust() {
        test.section("Flow 5: adjust a mark with a reason, Saved, Undo");
        test.clickText("Adjust Score");
        boolean shown = driver.waitUntil(() -> test.getMain()
                .isSheetShown(), SCREEN_WAIT_MILLIS);
        test.check(shown, "Adjust Score opens a sheet");
        if (!shown) {
            return;
        }
        Parent sheet = (Parent) test.byId("sheetHolder");

        List<Node> boxes = driver.findAll(sheet, "combo-box");
        if (boxes.isEmpty()) {
            test.check(false, "the sheet has a question choice");
            return;
        }
        ComboBox<?> questionBox = (ComboBox<?>) boxes.get(0);
        driver.click(questionBox);
        clickInPopup(ADJUSTED_QUESTION);
        test.check(ADJUSTED_QUESTION.equals(driver.onFx(
                () -> questionBox.getValue())), "the question list picks "
                + ADJUSTED_QUESTION);

        Node markField = driver.findAll(sheet, "text-field").get(0);
        driver.click(markField);
        driver.shortcut(KeyCode.A);
        driver.type(NEW_MARK);
        test.clickInSheet("Save");
        boolean refused = driver.onFx(() -> test.getMain().isSheetShown())
                && !driver.findAll(sheet, "field-error").isEmpty();
        test.check(refused, "Save with no reason keeps the sheet open "
                + "and explains the problem");
        test.capture("15-reason-required");

        Node reasonArea = driver.findAll(sheet, "text-area").get(0);
        driver.click(reasonArea);
        driver.type(REASON);
        test.clickInSheet("Save");
        boolean closed = driver.waitUntil(() -> !test.getMain()
                .isSheetShown(), SCREEN_WAIT_MILLIS);
        Node savedBar = test.byId("savedBar");
        boolean saved = closed && mark(DemoData.TIMEOUT_USERNAME,
                ADJUSTED_QUESTION) == ADJUSTED_TO
                && driver.onFx(() -> savedBar.isVisible());
        test.check(saved, "with a reason, Save changes Q1b to 2 and shows "
                + "the Saved line");
        test.capture("16-saved");

        test.clickText("Undo");
        boolean undone = driver.waitUntil(() -> mark(
                DemoData.TIMEOUT_USERNAME, ADJUSTED_QUESTION)
                == ADJUSTED_FROM, SCREEN_WAIT_MILLIS);
        test.check(undone, "Undo restores the mark of 3");
    }

    // Flow 6: results follow the section; exports use the selection.
    private void flowExport() {
        test.section("Flow 6: filter by section, select, export");
        test.clickText("Back to Results");
        test.check(driver.waitUntil(() -> test.getMain().getCurrentScreen()
                == Screen.RESULTS, SCREEN_WAIT_MILLIS),
                "Back to Results returns to Results");
        ResultsController results = resultsController();
        if (results == null) {
            return;
        }

        chooseSection(EMPTY_SECTION);
        boolean empty = driver.waitUntil(() -> results.getShownSubmissions()
                .isEmpty(), SCREEN_WAIT_MILLIS);
        test.check(empty, "choosing section G2 in the context bar shows no "
                + "results");
        test.capture("17-empty-section");
        chooseSection(DemoData.SAMPLE_SECTION);
        boolean six = driver.waitUntil(() -> results.getShownSubmissions()
                .size() == SAMPLE_COUNT, SCREEN_WAIT_MILLIS);
        test.check(six, "choosing G1 shows the six students again");

        Node alpha = test.rowFor("table-row-cell",
                item -> isStudent(item, DemoData.USERNAMES[0]));
        Node bravo = test.rowFor("table-row-cell",
                item -> isStudent(item, DemoData.TIMEOUT_USERNAME));
        if (alpha == null || bravo == null) {
            test.check(false, "learner.alpha and learner.bravo are shown");
            return;
        }
        driver.click(alpha);
        driver.shortcutClick(bravo);
        int chosen = driver.onFx(() -> results.getExportScope().size());
        test.check(chosen == 2, "a click and a Ctrl-click select two "
                + "students");

        test.clickText("Export Score Sheet");
        String csv = sheetText();
        test.check(countLines(csv) == TWO_STUDENT_LINES, "the score sheet "
                + "preview has a header and two rows");
        test.capture("18-export-preview");
        driver.press(KeyCode.ESCAPE);
        test.check(driver.waitUntil(() -> !test.getMain().isSheetShown(),
                SCREEN_WAIT_MILLIS), "Escape closes the sheet");

        test.clickText("Export JSON");
        String json = sheetText();
        test.check(json.contains(DemoData.USERNAMES[0])
                && json.contains(DemoData.TIMEOUT_USERNAME)
                && !json.contains("learner.charlie"), "the JSON preview "
                + "holds only the two selected students");
        test.clickInSheet("Done");
        test.check(driver.waitUntil(() -> !test.getMain().isSheetShown(),
                SCREEN_WAIT_MILLIS), "Done closes the sheet");
    }

    // Flow 7: switching themes keeps the screen, rows and selection.
    private void flowTheme() {
        test.section("Flow 7: switch themes without losing state");
        ResultsController results = resultsController();
        if (results == null) {
            return;
        }
        int shown = driver.onFx(() -> results.getShownSubmissions().size());
        List<String> selected = driver.onFx(() -> new ArrayList<>(
                test.getState().getSelectedResults()));

        driver.click(test.byId("darkButton"));
        boolean dark = driver.waitUntil(() -> hasDarkSheet(),
                SCREEN_WAIT_MILLIS);
        test.check(dark, "the Dark button applies the dark theme");
        test.capture("19-dark-results");
        boolean kept = driver.onFx(() -> test.getMain().getCurrentScreen()
                == Screen.RESULTS
                && results.getShownSubmissions().size() == shown
                && selected.equals(new ArrayList<>(
                test.getState().getSelectedResults())));
        test.check(kept, "the screen, rows and selection are kept");

        driver.click(test.byId("lightButton"));
        boolean light = driver.waitUntil(() -> !hasDarkSheet(),
                SCREEN_WAIT_MILLIS);
        test.check(light, "the Light button brings the light theme back");
    }

    // Clicks the control with some words and some user data.
    private void clickWithData(String text, String data) {
        Labeled control = driver.findWithData(test.getRoot(), text, data);
        if (control == null) {
            test.note("no control " + text + " for " + data);
            return;
        }
        driver.click(control);
    }

    // Clicks an entry in the pop-up list on show.
    private void clickInPopup(String text) {
        driver.waitUntil(() -> driver.findInPopups(text) != null,
                SCREEN_WAIT_MILLIS);
        Labeled entry = driver.findInPopups(text);
        if (entry == null) {
            test.note("no entry " + text + " in the pop-up list");
            driver.press(KeyCode.ESCAPE);
            return;
        }
        driver.click(entry);
    }

    // Picks a section from the context bar.
    private void chooseSection(String section) {
        Parent bar = (Parent) test.byId("contextBar");
        Node box = driver.onFx(() -> bar.lookup("#sectionBox"));
        driver.click(box);
        clickInPopup(section);
    }

    // Returns the text of the preview sheet once it is on show.
    private String sheetText() {
        driver.waitUntil(() -> test.getMain().isSheetShown(),
                SCREEN_WAIT_MILLIS);
        List<Node> areas = driver.findAll((Parent) test.byId("sheetHolder"),
                "text-area");
        if (areas.isEmpty()) {
            return "";
        }
        TextArea area = (TextArea) areas.get(0);
        return driver.onFx(() -> area.getText());
    }

    // Returns the controller of the Results screen.
    private ResultsController resultsController() {
        try {
            return (ResultsController) test.getBuilder().getNavigator()
                    .getController(Screen.RESULTS);
        } catch (IOException exception) {
            test.check(false, "the Results screen loads: "
                    + exception.getMessage());
            return null;
        }
    }

    // Says whether the window uses the dark stylesheet.
    private boolean hasDarkSheet() {
        for (String sheet : test.getStage().getScene().getStylesheets()) {
            if (sheet.endsWith("app-dark.css")) {
                return true;
            }
        }
        return false;
    }

    // Returns the item of a row, read on the JavaFX thread.
    private Object itemOf(Node row) {
        if (row instanceof TableRow) {
            return ((TableRow<?>) row).getItem();
        }
        return null;
    }

    // Says whether an item is the unusable sample ZIP file.
    private boolean isInvalidZip(Object item) {
        if (!(item instanceof ImportItem)) {
            return false;
        }
        ImportItem file = (ImportItem) item;
        return DemoData.INVALID_ZIP_USERNAME.equals(file.getUsername())
                && file.getState() == ImportState.INVALID;
    }

    // Says whether an item is a repair that waits for review.
    private boolean isWaitingRepair(Object item) {
        if (!(item instanceof RepairItem)) {
            return false;
        }
        RepairItem repair = (RepairItem) item;
        return !repair.isBlocked() && repair.getAnomaly().getFixStatus()
                == FixStatus.APPLIED;
    }

    // Says whether an item is the hard-coded answer flag.
    private boolean isHardCodedFlag(Object item) {
        return item instanceof Anomaly && ((Anomaly) item).getType()
                == AnomalyType.HARD_CODED_ANSWER;
    }

    // Says whether an item is one student's submission.
    private boolean isStudent(Object item, String username) {
        return item instanceof Submission && username.equals(
                ((Submission) item).getUsername());
    }

    // Returns a student's total in the run shown.
    private double total(String username) {
        Submission student = test.submission(username);
        if (student == null) {
            return -1.0;
        }
        return student.getTotalScore();
    }

    // Returns a student's mark for one question in the run shown.
    private double mark(String username, String questionId) {
        Submission student = test.submission(username);
        if (student == null) {
            return -1.0;
        }
        for (QuestionResult result : student.getQuestionResults()) {
            if (result.getQuestion().getId().equals(questionId)) {
                return result.getScore();
            }
        }
        return -1.0;
    }

    // Counts the submissions of a run with a status.
    private int countStatus(GradingRun run, SubmissionStatus status) {
        if (run == null) {
            return 0;
        }
        int count = 0;
        for (Submission submission : run.getSubmissions()) {
            if (submission.getStatus() == status) {
                count++;
            }
        }
        return count;
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
}
