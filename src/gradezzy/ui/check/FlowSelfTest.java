/*
 * File: FlowSelfTest.java
 *
 * Purpose: Runs seven everyday tasks through the window with the demo
 * data and prints PASS or FAIL for each.
 *
 * Where it fits: Run by "sh run.sh --selftest". Needs no screen. It
 * builds the guided window off screen with the demo backend, presses
 * its real buttons and links (found by their text with SceneFinder) and
 * checks what the instructor would see: the action bar, pills, table
 * rows, sheets and notices. The demo grader is stepped by hand.
 */
package gradezzy.ui.check;

import java.io.File;
import java.util.ArrayList;
import java.util.List;
import java.util.Locale;

import javafx.scene.Node;
import javafx.scene.Parent;
import javafx.scene.Scene;
import javafx.scene.control.Button;
import javafx.scene.control.ButtonBase;
import javafx.scene.control.CheckBox;
import javafx.scene.control.ComboBox;
import javafx.scene.control.Label;
import javafx.scene.control.RadioButton;
import javafx.scene.control.TableRow;
import javafx.scene.control.TableView;
import javafx.scene.control.TextArea;
import javafx.scene.control.TextField;

import gradezzy.config.ConfigKeys;
import gradezzy.model.Anomaly;
import gradezzy.model.AnomalyType;
import gradezzy.model.FixStatus;
import gradezzy.model.GradingRun;
import gradezzy.model.QuestionResult;
import gradezzy.model.Submission;
import gradezzy.model.SubmissionStatus;
import gradezzy.service.ServiceRegistry;
import gradezzy.ui.ThemeManager;
import gradezzy.ui.backend.ImportBatch;
import gradezzy.ui.backend.ImportItem;
import gradezzy.ui.backend.ImportState;
import gradezzy.ui.backend.InputKind;
import gradezzy.ui.backend.RepairItem;
import gradezzy.ui.backend.ReviewDecision;
import gradezzy.ui.demo.DemoBackend;
import gradezzy.ui.demo.DemoData;
import gradezzy.ui.flow.FlowWindow;
import gradezzy.ui.flow.MarkStats;
import gradezzy.ui.flow.Page;
import gradezzy.ui.flow.Parts;
import gradezzy.ui.flow.Step;
import gradezzy.ui.flow.Students;

/**
 * The seven demo flows of the window.
 */
public class FlowSelfTest {

    // The size of the off-screen window
    private static final double WIDTH = 1200.0;
    private static final double HEIGHT = 800.0;

    // Steps in one submission: start, eight more stages, then finish
    private static final int STEPS_PER_SUBMISSION = 10;

    // The submissions graded before Cancel in flow 2
    private static final int GRADED_BEFORE_CANCEL = 3;

    // How far into the third submission flow 2 checks "Now"
    private static final int STEPS_BEFORE_STAGE_CHECK = 4;

    // The six sample files, and the five left after removing one
    private static final int SAMPLE_COUNT = 6;
    private static final int USABLE_COUNT = 5;

    // The place of the "Could not grade" row on the Grade step
    private static final int FAILED_ROW = 2;

    // The students who could not be graded before Cancel (a compile
    // error and a timeout)
    private static final int FAILED_BEFORE_CANCEL = 2;

    // The total of learner.bravo before and after grading Q1a again
    private static final String BRAVO_BEFORE = "19";
    private static final String BRAVO_AFTER = "20";

    // The question adjusted in flow 5, with its old and new marks
    private static final String ADJUSTED_QUESTION = "Q1b";
    private static final double ADJUSTED_FROM = 3.0;
    private static final double ADJUSTED_TO = 2.0;

    // A mark above the maximum of Q1b
    private static final String TOO_HIGH = "4";

    // The reason typed for the change in flow 5
    private static final String REASON = "Method reads the file twice; "
            + "agreed by the module team.";

    // A section without a run, for flow 6
    private static final String EMPTY_SECTION = "G2";

    // The students ticked for export in flow 6
    private static final int TICKED_COUNT = 2;

    // Turns a fraction into a percentage
    private static final double PERCENT = 100.0;

    // Rounds a number to one decimal place
    private static final double ONE_DECIMAL = 10.0;

    /**
     * One flow of the self-test.
     */
    private interface Flow {

        /**
         * Runs the flow; failed checks are recorded, not thrown.
         */
        void run();
    }

    // Every service of the application
    private final ServiceRegistry services;

    // The failed checks of the flow being run
    private final List<String> problems = new ArrayList<>();

    // Lines printed under a passed flow, such as where a file went
    private final List<String> notes = new ArrayList<>();

    // The demo backend, stepped by hand
    private DemoBackend backend = null;

    // The off-screen window and its scene
    private FlowWindow window = null;
    private Scene scene = null;

    // The fix approved in flow 3, checked again in flow 7
    private Anomaly approvedFix = null;

    // The flag rejected and reopened in flow 3, checked again in flow 7
    private Anomaly reopenedFlag = null;

    /**
     * Creates the self-test.
     *
     * @param services every service of the application
     */
    public FlowSelfTest(ServiceRegistry services) {
        this.services = services;
    }

    /**
     * Runs the seven flows and prints PASS or FAIL for each.
     *
     * @return 0 when every flow passed, 1 otherwise
     */
    public int run() {
        backend = new DemoBackend(services);
        backend.setManualStepping(true);
        window = new FlowWindow(backend, false);
        scene = window.build(WIDTH, HEIGHT);
        settle();

        int failed = 0;
        failed += runFlow(1, "Import six ZIP files, remove the unreadable "
                + "one, reach ready to grade", () -> flowImport());
        failed += runFlow(2, "Grade with a compile error and a timeout, "
                + "cancel, resume", () -> flowGrade());
        failed += runFlow(3, "Approve a fix, reject a flag, undo",
                () -> flowDecisions());
        failed += runFlow(4, "Open learner.bravo at 19/20, grade Q1a again",
                () -> flowRetry());
        failed += runFlow(5, "Adjust a mark with a reason, then undo",
                () -> flowAdjust());
        failed += runFlow(6, "Statistics for module and section, export "
                + "the score sheet", () -> flowExport());
        failed += runFlow(7, "Switch themes without losing state",
                () -> flowTheme());

        if (failed == 0) {
            System.out.println("All 7 flows passed.");
            return 0;
        }
        System.out.println(failed + " of 7 flows failed.");
        return 1;
    }

    // Runs one flow and prints its outcome.
    private int runFlow(int number, String name, Flow flow) {
        problems.clear();
        notes.clear();
        try {
            flow.run();
        } catch (RuntimeException exception) {
            problems.add("unexpected " + exception);
            exception.printStackTrace(System.out);
        }

        if (problems.isEmpty()) {
            System.out.println("PASS  " + number + " " + name);
            for (String note : notes) {
                System.out.println("      " + note);
            }
            return 0;
        }
        System.out.println("FAIL  " + number + " " + name);
        for (String problem : problems) {
            System.out.println("      - " + problem);
        }
        return 1;
    }

    // ------------------------------------------------------------ flow 1

    // Set up: add the samples, remove the unusable one, start enabled.
    private void flowImport() {
        check(window.getCurrentStep() == Step.SET_UP, "Set up is shown");
        Button start = window.getActionBar().getPrimaryButton();
        check("Start grading".equals(start.getText()) && start.isDisabled(),
                "Start grading is shown and disabled before any file");

        press(SceneFinder.link(root(), "choose files"), "choose files");
        ImportBatch batch = window.getState().getBatch();
        List<ImportItem> files = batch.getItems(InputKind.SUBMISSION);
        check(files.size() == SAMPLE_COUNT, "six ZIP files were added");
        check(noticeText().equals("Added 6 files."),
                "the notice says six files were added");
        check(count(SceneFinder.texts(root(), "pill"), "Cannot be used")
                == 1, "exactly one file row says Cannot be used");
        check(start.isDisabled(), "Start grading stays disabled");
        check(statusText().contains("cannot be used"),
                "the action bar says a ZIP file cannot be used (it says \""
                + statusText() + "\")");

        ImportItem invalid = null;
        for (ImportItem item : files) {
            if (batch.getEffectiveState(item) == ImportState.INVALID) {
                invalid = item;
            }
        }
        check(invalid != null && invalid.getUsername().equals(
                DemoData.INVALID_ZIP_USERNAME), "the unusable file is "
                + DemoData.INVALID_ZIP_USERNAME + "'s");
        if (invalid == null) {
            return;
        }
        Node row = rowOf(invalid.getName(), "card-row");
        check(row != null && SceneFinder.shows(row, "Cannot be used"),
                "the row of " + invalid.getName() + " says Cannot be used");
        if (row != null) {
            press(SceneFinder.link(row, "Remove"), "Remove");
        }
        check(batch.getItems(InputKind.SUBMISSION).size() == USABLE_COUNT,
                "five files are left");
        check(count(SceneFinder.texts(root(), "pill"), "Cannot be used")
                == 0, "no file row says Cannot be used");

        check(rowSays("Score sheet", "Ready"), "the score sheet is Ready");
        check(rowSays("Tester files", "Ready"), "the testers are Ready");
        check(!start.isDisabled(), "Start grading is enabled");
        check(statusText().contains(USABLE_COUNT + " students ready to "
                + "grade"), "the action bar says five students are ready "
                + "(it says \"" + statusText() + "\")");
    }

    // ------------------------------------------------------------ flow 2

    // Grade: watch progress, cancel with the sheet, resume, reach Review.
    private void flowGrade() {
        press(window.getActionBar().getPrimaryButton(), "Start grading");
        check(window.getCurrentStep() == Step.GRADE, "the Grade step opened");
        check(window.getState().isGrading(), "grading started");

        backend.stepGrading(STEPS_PER_SUBMISSION * (GRADED_BEFORE_CANCEL - 1)
                + STEPS_BEFORE_STAGE_CHECK);
        settle();
        String now = nowText();
        check(now.startsWith("Now: ") && now.contains(" · "),
                "the screen says who is being graded and what is happening "
                + "(it says \"" + now + "\")");
        backend.stepGrading(STEPS_PER_SUBMISSION - STEPS_BEFORE_STAGE_CHECK);
        settle();
        check(SceneFinder.text(root(), "grade-count").equals(
                String.valueOf(GRADED_BEFORE_CANCEL)), "the count says 3");
        check(SceneFinder.text(root(), "grade-of").equals("of "
                + USABLE_COUNT + " students"), "the count says of 5 "
                + "students");
        List<String> counts = SceneFinder.texts(root(), "count-value");
        check(counts.size() > FAILED_ROW && counts.get(FAILED_ROW).equals(
                String.valueOf(FAILED_BEFORE_CANCEL)), "So far counts two "
                + "students who could not be graded (a compile error and a "
                + "timeout)");
        check(SceneFinder.shows(root(), " left"), "the screen shows about "
                + "how long is left");

        press(SceneFinder.button(window.getActionBar(), "Cancel"), "Cancel");
        Node sheet = sheet();
        check(sheet != null && SceneFinder.shows(sheet, "keep their marks"),
                "the Cancel sheet says graded students keep their marks");
        if (sheet != null) {
            press(SceneFinder.button(sheet, "Cancel grading"),
                    "Cancel grading");
        }
        check(!window.isSheetOpen(), "the Cancel sheet closed");
        check(statusText().startsWith("Stopping"),
                "the action bar says grading is stopping");
        backend.finishGrading();
        settle();

        GradingRun run = window.getState().getRun();
        check(run != null, "the stopped run is kept");
        if (run == null) {
            return;
        }
        check(SceneFinder.shows(root(), "Grading stopped"),
                "the heading says Grading stopped");
        check(count(run, SubmissionStatus.GRADED) == GRADED_BEFORE_CANCEL,
                "the three finished results were kept");
        check(count(run, SubmissionStatus.CANCELLED)
                == USABLE_COUNT - GRADED_BEFORE_CANCEL,
                "the other two were cancelled, not graded");
        Button primary = window.getActionBar().getPrimaryButton();
        check("Resume".equals(primary.getText()) && !primary.isDisabled(),
                "Resume is offered");

        press(primary, "Resume");
        backend.finishGrading();
        settle();
        check(count(run, SubmissionStatus.GRADED) == USABLE_COUNT,
                "every submission is graded after Resume");
        check(backend.getGradeCount(DemoData.USERNAMES[0]) == 1,
                "Resume did not grade a finished submission again");
        check(window.getCurrentStep() == Step.REVIEW,
                "Review opened when grading finished");
        check(backend.getRun(window.getState().getSection()) == run,
                "the section shows the new run");
    }

    // ------------------------------------------------------------ flow 3

    // Review: approve a fix and settle a flag in the student panel.
    private void flowDecisions() {
        GradingRun run = window.getState().getRun();
        if (run == null) {
            problems.add("no run to review");
            return;
        }
        RepairItem waiting = null;
        for (RepairItem repair : backend.listRepairs(run)) {
            if (waiting == null && !repair.isBlocked()
                    && repair.getAnomaly().getFixStatus()
                    == FixStatus.APPLIED) {
                waiting = repair;
            }
        }
        check(waiting != null, "a fix waits for review");
        if (waiting != null) {
            Anomaly fix = waiting.getAnomaly();
            openStudent(fix.getUsername());
            Node card = cardOf(fix);
            check(card != null && SceneFinder.shows(card, "Fixed for you"),
                    "the panel shows the fix as Fixed for you");
            press(card == null ? null : SceneFinder.button(card,
                    "Approve"), "Approve");
            check(fix.getFixStatus() == FixStatus.APPROVED,
                    "the fix was approved");
            card = cardOf(fix);
            check(card != null && SceneFinder.shows(card, "Fix approved"),
                    "the panel shows Fix approved");
            approvedFix = fix;
        }

        Anomaly flag = null;
        for (Submission submission : run.getSubmissions()) {
            for (Anomaly anomaly : submission.getAnomalies()) {
                if (flag == null && anomaly.getType()
                        == AnomalyType.HARD_CODED_ANSWER) {
                    flag = anomaly;
                }
            }
        }
        check(flag != null, "a hard-coded answer is flagged");
        if (flag == null) {
            return;
        }
        openStudent(flag.getUsername());
        Node card = cardOf(flag);
        check(card != null && SceneFinder.shows(card, "Flag"),
                "the panel shows the flag");
        press(card == null ? null : SceneFinder.button(card, "Looks fine"),
                "Looks fine");
        check(backend.getDecision(flag) == ReviewDecision.REJECTED,
                "the flag was rejected");
        card = cardOf(flag);
        press(card == null ? null : SceneFinder.button(card, "Reopen"),
                "Reopen");
        check(backend.getDecision(flag) == ReviewDecision.OPEN,
                "Reopen put the flag back to open");
        card = cardOf(flag);
        check(card != null && SceneFinder.button(card, "Looks fine")
                != null, "the flag can be decided again");
        reopenedFlag = flag;
    }

    // ------------------------------------------------------------ flow 4

    // Review: learner.bravo's Q1a timed out; grading it again gives 20.
    private void flowRetry() {
        Submission bravo = openStudent(DemoData.TIMEOUT_USERNAME);
        if (bravo == null) {
            return;
        }
        check(SceneFinder.text(root(), "big-total").equals(BRAVO_BEFORE),
                "the panel shows 19");
        QuestionResult q1a = result(bravo, DemoData.TIMEOUT_QUESTION);
        check(q1a != null && q1a.isTimedOut(), "Q1a timed out");
        double[] before = otherMarks(bravo);

        Node row = questionRow(DemoData.TIMEOUT_QUESTION);
        if (row == null) {
            problems.add("expected: the panel lists Q1a");
            return;
        }
        SceneFinder.click(((Parent) row)
                .getChildrenUnmodifiable().get(0));
        settle();
        row = questionRow(DemoData.TIMEOUT_QUESTION);
        check(row != null && SceneFinder.shows(row, "ran too long"),
                "clicking Q1a says a test ran too long");
        press(row == null ? null : SceneFinder.link(row, "Grade "
                + DemoData.TIMEOUT_QUESTION + " again"), "Grade Q1a again");
        check(q1a != null && !q1a.isTimedOut()
                && q1a.getScore() == q1a.getQuestion().getMaxScore(),
                "Q1a passed every test");
        check(SceneFinder.text(root(), "big-total").equals(BRAVO_AFTER),
                "the panel shows 20");
        check(noticeText().startsWith("Retried Q1a"),
                "a notice says Q1a was graded again");
        check(sameMarks(before, otherMarks(bravo)),
                "the other questions kept their marks");
    }

    // ------------------------------------------------------------ flow 5

    // Review: change Q1b with a reason, then undo the change.
    private void flowAdjust() {
        Submission bravo = openStudent(DemoData.TIMEOUT_USERNAME);
        if (bravo == null) {
            return;
        }
        Node row = questionRow(ADJUSTED_QUESTION);
        press(row == null ? null : SceneFinder.link(row, "Adjust"),
                "Adjust");
        Node sheet = sheet();
        check(sheet != null && SceneFinder.shows(sheet, "Adjust "
                + ADJUSTED_QUESTION), "the Adjust sheet opened");
        if (sheet == null) {
            return;
        }

        String problem = trySave(sheet, Parts.mark(ADJUSTED_TO), "");
        check(problem.contains("reason"), "a blank reason is refused");
        problem = trySave(sheet, TOO_HIGH, REASON);
        check(problem.contains("from 0 to"), "a mark above the maximum is "
                + "refused");
        check(window.isSheetOpen(), "the sheet stays open after a refusal");
        trySave(sheet, Parts.mark(ADJUSTED_TO), REASON);
        check(!window.isSheetOpen(), "Save closed the sheet");

        QuestionResult q1b = result(bravo, ADJUSTED_QUESTION);
        check(q1b != null && q1b.getScore() == ADJUSTED_TO,
                "Q1b has the new mark");
        check(backend.listAdjustments(bravo).size() == 1,
                "the change and its reason are kept");
        String change = ADJUSTED_QUESTION + " changed from "
                + Parts.mark(ADJUSTED_FROM) + " to " + Parts.mark(ADJUSTED_TO);
        Label changeLabel = SceneFinder.label(root(), change);
        check(changeLabel != null, "the panel says \"" + change + "\"");
        check(SceneFinder.shows(root(), REASON), "the panel shows the "
                + "reason");
        check(SceneFinder.text(root(), "big-total").equals(BRAVO_BEFORE),
                "the total went down to 19");
        if (changeLabel == null) {
            return;
        }

        Node line = changeLabel.getParent().getParent();
        press(SceneFinder.button(line, "Undo"), "Undo");
        check(q1b != null && q1b.getScore() == ADJUSTED_FROM,
                "Undo restored the earlier mark");
        check(backend.listAdjustments(bravo).isEmpty(),
                "Undo removed the change");
        check(SceneFinder.label(root(), change) == null,
                "the change is no longer listed");
        check(SceneFinder.text(root(), "big-total").equals(BRAVO_AFTER),
                "the total is back to 20");
    }

    // ------------------------------------------------------------ flow 6

    // Statistics follow module and section; Export writes the score sheet
    // for the ticked students.
    private void flowExport() {
        GradingRun run = window.getState().getRun();
        if (run == null) {
            problems.add("no run to export");
            return;
        }
        tick(DemoData.USERNAMES[0]);
        tick(DemoData.TIMEOUT_USERNAME);
        check(window.getState().getSelected().size() == TICKED_COUNT,
                "two students are ticked");
        check(statusText().contains(TICKED_COUNT + " students ticked for "
                + "export"), "the action bar says two students are ticked");

        press(window.getActionBar().getPrimaryButton(), "Continue to export");
        Node sheet = sheet();
        if (sheet != null) {
            press(SceneFinder.button(sheet, "Continue anyway"),
                    "Continue anyway");
        }
        check(window.getCurrentStep() == Step.EXPORT, "Export opened");

        press(SceneFinder.link(root(), "See all statistics"),
                "See all statistics");
        check(window.getCurrentPage() == Page.STATISTICS,
                "the Statistics tab opened");
        checkFigures(run);

        choose(DemoData.SAMPLE_SECTION, EMPTY_SECTION);
        check(SceneFinder.shows(root(), EMPTY_SECTION + " has not been "
                + "graded yet"), "a section without a run says so");
        choose(DemoData.MODULE_CODE, DemoData.EMPTY_MODULE_CODE);
        check(SceneFinder.shows(root(), "Nothing graded yet"),
                "the empty module says nothing is graded");
        choose(DemoData.EMPTY_MODULE_CODE, DemoData.MODULE_CODE);
        choose(EMPTY_SECTION, DemoData.SAMPLE_SECTION);
        checkFigures(run);

        press(tab("Marking"), "the Marking tab");
        check(window.getCurrentStep() == Step.EXPORT,
                "Marking went back to Export");
        RadioButton ticked = null;
        for (RadioButton radio : SceneFinder.ofType(root(),
                RadioButton.class)) {
            if (radio.getText().startsWith("Only ticked students")) {
                ticked = radio;
            }
        }
        check(ticked != null && !ticked.isDisabled()
                && ticked.getText().endsWith("(" + TICKED_COUNT + ")"),
                "Only ticked students (2) can be chosen");
        press(ticked, "Only ticked students");

        int filesBefore = outputFileCount();
        press(window.getActionBar().getPrimaryButton(), "Export score sheet");
        String message = "";
        Label saved = SceneFinder.label(root(), "Saved");
        if (saved != null) {
            message = SceneFinder.ofType(saved.getParent(), Label.class)
                    .get(SceneFinder.ofType(saved.getParent(), Label.class)
                    .size() - 1).getText();
        }
        check(saved != null, "the Saved pill is shown");
        check(message.contains("for " + TICKED_COUNT + " students"),
                "only the two ticked students were exported (it says \""
                + message + "\")");
        check("Grade another section".equals(window.getActionBar()
                .getPrimaryButton().getText()), "the next step is Grade "
                + "another section");
        check(outputFileCount() == filesBefore,
                "no file appeared in the output folder");
        notes.add("Export: " + message + " Output folder: "
                + backend.getSetting(ConfigKeys.OUTPUT_DIR) + ".");
    }

    // Checks that the Statistics figures match MarkStats of the run.
    private void checkFigures(GradingRun run) {
        MarkStats stats = MarkStats.of(run.getSubmissions(),
                backend.getAssessment().getMaxTotal(),
                MarkStats.parsePassPercent(backend.getSetting(
                ConfigKeys.PASS_MARK_PERCENT)));
        String average = String.format(Locale.ROOT, "%.1f",
                stats.getMean());
        String passed = Math.round(stats.getPassRate() * PERCENT) + "%";
        String range = Parts.mark(roundOne(stats.getLowest())) + " to "
                + Parts.mark(roundOne(stats.getHighest()));
        check(figure("Average").equals(average), "Average shows " + average
                + " (it shows " + figure("Average") + ")");
        check(figure("Passed").equals(passed), "Passed shows " + passed
                + " (it shows " + figure("Passed") + ")");
        check(figure("Range").equals(range), "Range shows " + range
                + " (it shows " + figure("Range") + ")");
        check(SceneFinder.shows(root(), DemoData.MODULE_CODE + " · "
                + DemoData.SAMPLE_SECTION), "the subtitle names CS102 G1");
    }

    // ------------------------------------------------------------ flow 7

    // Settings: dark and back; Marking keeps its step, run and choices.
    private void flowTheme() {
        press(SceneFinder.button(window.getActionBar(), "Back"), "Back");
        check(window.getCurrentStep() == Step.REVIEW, "Back opened Review");
        Submission bravo = openStudent(DemoData.TIMEOUT_USERNAME);
        GradingRun run = window.getState().getRun();
        List<Submission> ticked = new ArrayList<>(
                window.getState().getSelected());
        Node reviewView = window.getScreen(Step.REVIEW).getView();
        String panelName = SceneFinder.text(root(), "panel-name");

        press(tab("Settings"), "the Settings tab");
        check(window.getCurrentPage() == Page.SETTINGS, "Settings opened");
        ComboBox<?> theme = themeBox();
        check(theme != null, "Settings shows the Theme choice");
        if (theme != null) {
            setValue(theme, ThemeManager.DARK_THEME);
            check(window.isDark() && window.getRoot().getStyleClass()
                    .contains("dark"), "the dark theme is on");
            setValue(theme, ThemeManager.LIGHT_THEME);
            check(!window.isDark() && !window.getRoot().getStyleClass()
                    .contains("dark"), "the light theme is back");
        }

        press(tab("Marking"), "the Marking tab");
        Node sheet = sheet();
        check(sheet == null, "leaving Settings asked nothing");
        if (sheet != null) {
            window.closeSheet();
        }
        check(window.getCurrentPage() == Page.MARKING
                && window.getCurrentStep() == Step.REVIEW,
                "Marking is still on Review");
        check(window.getScreen(Step.REVIEW).getView() == reviewView,
                "the Review screen was not rebuilt");
        check(window.getState().getRun() == run, "the same run is shown");
        check(ticked.equals(window.getState().getSelected())
                && ticked.size() == TICKED_COUNT,
                "the two ticked students are still ticked");
        check(bravo != null && panelName.equals(Students.name(bravo))
                && SceneFinder.text(root(), "panel-name").equals(panelName),
                "the student panel still shows " + panelName);
        TableView<?> table = table();
        check(table != null && table.getSelectionModel().getSelectedItem()
                == bravo, "the same row is still selected");
        check(approvedFix != null
                && approvedFix.getFixStatus() == FixStatus.APPROVED,
                "the fix approved in flow 3 is still approved");
        check(reopenedFlag != null && backend.getDecision(reopenedFlag)
                == ReviewDecision.OPEN, "the flag reopened in flow 3 is "
                + "still open");
    }

    // ----------------------------------------------------------- helpers

    // Records a failed check.
    private void check(boolean passed, String expectation) {
        if (!passed) {
            problems.add("expected: " + expectation);
        }
    }

    // Presses a button or link and lets the window settle; records a
    // problem when it is missing or disabled.
    private void press(ButtonBase button, String name) {
        if (!SceneFinder.press(button)) {
            problems.add("expected: \"" + name + "\" can be pressed");
            return;
        }
        settle();
    }

    // Runs waiting tasks and lays the scene out.
    private void settle() {
        SceneFinder.settle(scene);
    }

    // Returns the top of the scene.
    private Node root() {
        return scene.getRoot();
    }

    // Returns the sentence beside Back in the action bar.
    private String statusText() {
        for (Node node : window.getActionBar().getChildren()) {
            if (node instanceof Label) {
                return ((Label) node).getText();
            }
        }
        return "";
    }

    // Returns the text of the notice, or an empty string when hidden.
    private String noticeText() {
        Node notice = scene.lookup(".notice");
        if (notice instanceof Label && notice.isVisible()) {
            return ((Label) notice).getText();
        }
        return "";
    }

    // Returns the line that says who is being graded now.
    private String nowText() {
        for (Label label : SceneFinder.ofType(root(), Label.class)) {
            String text = label.getText();
            if (text != null && text.startsWith("Now: ")) {
                return text;
            }
        }
        return "";
    }

    // Returns the open sheet, or null.
    private Node sheet() {
        if (!window.isSheetOpen()) {
            return null;
        }
        return scene.lookup(".sheet-dim");
    }

    // Returns a tab of the top bar.
    private Button tab(String name) {
        return SceneFinder.button(window.getTabBar(), name);
    }

    // Finds the row (by style class) around a label with some text.
    private Node rowOf(String text, String styleClass) {
        Label label = SceneFinder.label(root(), text);
        if (label == null) {
            return null;
        }
        return SceneFinder.ancestor(label, styleClass);
    }

    // Says whether the class file row of a title shows a pill.
    private boolean rowSays(String title, String pill) {
        Node row = rowOf(title, "card-row");
        return row != null && SceneFinder.texts(row, "pill").contains(pill);
    }

    // Returns the results table of Review, or null.
    private TableView<?> table() {
        List<TableView> tables = SceneFinder.ofType(root(), TableView.class);
        if (tables.isEmpty()) {
            return null;
        }
        return tables.get(0);
    }

    // Selects a student's row in the Review table, which opens the
    // student panel, and returns the student.
    private Submission openStudent(String username) {
        if (window.getCurrentStep() != Step.REVIEW) {
            problems.add("expected: Review is shown");
            return null;
        }
        TableView<?> table = table();
        Submission found = null;
        int index = -1;
        if (table != null) {
            for (int i = 0; i < table.getItems().size(); i++) {
                Object item = table.getItems().get(i);
                if (item instanceof Submission && ((Submission) item)
                        .getUsername().equals(username)) {
                    found = (Submission) item;
                    index = i;
                }
            }
        }
        if (found == null) {
            problems.add("expected: the table lists " + username);
            return null;
        }
        table.getSelectionModel().clearSelection();
        table.getSelectionModel().select(index);
        settle();
        check(SceneFinder.text(root(), "panel-name").equals(
                Students.name(found)), "the panel opened for " + username);
        return found;
    }

    // Ticks a student's box in the Review table.
    private void tick(String username) {
        if (window.getCurrentStep() != Step.REVIEW) {
            problems.add("expected: Review is shown to tick " + username);
            return;
        }
        for (TableRow row : SceneFinder.ofType(root(), TableRow.class)) {
            Object item = row.getItem();
            if (item instanceof Submission && ((Submission) item)
                    .getUsername().equals(username)) {
                List<CheckBox> boxes = SceneFinder.ofType(row,
                        CheckBox.class);
                if (!boxes.isEmpty() && !boxes.get(0).isSelected()) {
                    press(boxes.get(0), "the tick box of " + username);
                }
                return;
            }
        }
        problems.add("expected: a row with a tick box for " + username);
    }

    // Returns the card of the student panel that shows an anomaly.
    private Node cardOf(Anomaly anomaly) {
        return rowOf(anomaly.getMessage(), "fix-card");
    }

    // Returns the row of a question in the student panel.
    private Node questionRow(String questionId) {
        for (Label label : SceneFinder.ofType(root(), Label.class)) {
            Node row = SceneFinder.ancestor(label, "question-row");
            if (questionId.equals(label.getText()) && row != null) {
                return row;
            }
        }
        return null;
    }

    // Types a mark and a reason in the Adjust sheet and presses Save.
    // Returns the problem shown, or an empty string.
    private String trySave(Node sheet, String mark, String reason) {
        List<TextField> fields = SceneFinder.ofType(sheet, TextField.class);
        List<TextArea> areas = SceneFinder.ofType(sheet, TextArea.class);
        if (fields.isEmpty() || areas.isEmpty()) {
            problems.add("expected: the sheet has a mark and a reason field");
            return "";
        }
        fields.get(0).setText(mark);
        areas.get(0).setText(reason);
        press(SceneFinder.button(sheet, "Save"), "Save");
        if (!window.isSheetOpen()) {
            return "";
        }
        return SceneFinder.text(sheet, "field-problem");
    }

    // Returns the value of a figure tile on the Statistics tab.
    private String figure(String name) {
        for (Label label : SceneFinder.ofType(root(), Label.class)) {
            Node tile = SceneFinder.ancestor(label, "figure");
            if (name.equals(label.getText()) && tile != null
                    && label.getStyleClass().contains("figure-label")) {
                return SceneFinder.text(tile, "figure-value");
            }
        }
        return "";
    }

    // Picks a new value in the picker that shows an old one. Does
    // nothing when a picker already shows the new value.
    private void choose(String shown, String value) {
        List<ComboBox> boxes = SceneFinder.ofType(root(), ComboBox.class);
        for (ComboBox<?> box : boxes) {
            if (value.equals(box.getValue())
                    && box.getItems().contains(shown)) {
                return;
            }
        }
        for (ComboBox<?> box : boxes) {
            if (shown.equals(box.getValue())
                    && box.getItems().contains(value)) {
                setValue(box, value);
                return;
            }
        }
        problems.add("expected: a picker showing " + shown + " offers "
                + value);
    }

    // Picks a value of a picker, as a click in its list would.
    @SuppressWarnings("unchecked")
    private void setValue(ComboBox<?> box, String value) {
        ((ComboBox<String>) box).setValue(value);
        settle();
    }

    // Returns the Theme picker of Settings, or null.
    private ComboBox<?> themeBox() {
        for (ComboBox<?> box : SceneFinder.ofType(root(), ComboBox.class)) {
            if (box.getItems().contains(ThemeManager.DARK_THEME)) {
                return box;
            }
        }
        return null;
    }

    // Counts the files in the output folder, or 0 when it is missing.
    private int outputFileCount() {
        File folder = new File(backend.getSetting(ConfigKeys.OUTPUT_DIR));
        String[] names = folder.list();
        if (names == null) {
            return 0;
        }
        return names.length;
    }

    // Counts the texts equal to one value.
    private int count(List<String> texts, String value) {
        int total = 0;
        for (String text : texts) {
            if (value.equals(text)) {
                total++;
            }
        }
        return total;
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

    // Rounds to one decimal place.
    private static double roundOne(double value) {
        return Math.round(value * ONE_DECIMAL) / ONE_DECIMAL;
    }
}
