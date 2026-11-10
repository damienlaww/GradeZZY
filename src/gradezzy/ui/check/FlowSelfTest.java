/*
 * File: FlowSelfTest.java
 *
 * Purpose: Runs ten everyday tasks through the window with the demo
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

import javafx.event.Event;
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
import javafx.scene.input.KeyCode;
import javafx.scene.input.KeyEvent;
import javafx.scene.layout.VBox;

import gradezzy.config.ConfigKeys;
import gradezzy.model.Anomaly;
import gradezzy.model.AnomalyType;
import gradezzy.model.FixStatus;
import gradezzy.model.GradingRun;
import gradezzy.model.Question;
import gradezzy.model.QuestionResult;
import gradezzy.model.SavedAssessment;
import gradezzy.model.TestCaseResult;
import gradezzy.model.ScoreAdjustment;
import gradezzy.model.Section;
import gradezzy.model.Severity;
import gradezzy.model.Submission;
import gradezzy.model.SubmissionStatus;
import gradezzy.service.ServiceRegistry;
import gradezzy.ui.ThemeManager;
import gradezzy.ui.backend.ActionResult;
import gradezzy.ui.backend.ImportBatch;
import gradezzy.ui.backend.ImportItem;
import gradezzy.ui.backend.ImportState;
import gradezzy.ui.backend.InputKind;
import gradezzy.ui.backend.PipelineStages;
import gradezzy.ui.backend.RepairItem;
import gradezzy.ui.backend.ResultState;
import gradezzy.ui.backend.ReviewDecision;
import gradezzy.ui.backend.ReviewRules;
import gradezzy.ui.backend.StatusCounts;
import gradezzy.ui.demo.DemoBackend;
import gradezzy.ui.demo.DemoData;
import gradezzy.ui.flow.AssessmentEditor;
import gradezzy.ui.flow.FlowWindow;
import gradezzy.ui.flow.GradingJob;
import gradezzy.ui.flow.GuidedTour;
import gradezzy.ui.flow.MarkStats;
import gradezzy.ui.flow.Page;
import gradezzy.ui.flow.Parts;
import gradezzy.ui.flow.StatisticsScreen;
import gradezzy.ui.flow.Step;
import gradezzy.ui.flow.ReviewScreen;
import gradezzy.ui.flow.SectionProgress;
import gradezzy.ui.flow.SetUpScreen;
import gradezzy.ui.flow.StudentPanel;
import gradezzy.ui.flow.Students;

/**
 * The ten demo flows of the window.
 */
public class FlowSelfTest {

    // The size of the off-screen window
    private static final double WIDTH = 1200.0;
    private static final double HEIGHT = 800.0;

    // The index of the tour's stop about the Review step
    private static final int REVIEW_STOP = 9;

    // Steps in one submission: one per stage, then finish
    private static final int STEPS_PER_SUBMISSION = PipelineStages.count()
            + 1;

    // The button that stops grading, in the action bar and its sheet
    private static final String STOP_GRADING = "Stop grading";

    // The submissions graded before Cancel in flow 2
    private static final int GRADED_BEFORE_CANCEL = 3;

    // How far into the third submission flow 2 checks "Now"
    private static final int STEPS_BEFORE_STAGE_CHECK = 4;

    // The six sample files, and the five left after removing one
    private static final int SAMPLE_COUNT = 6;
    private static final int USABLE_COUNT = 5;

    // The Settings groups that change with the module or the assessment:
    // Course, Export folders, Assessment and Score sheets
    private static final int CHANGING_GROUPS = 4;

    // An assessment of the module added in flow 8, which picking that
    // module in Settings leaves unused
    private static final String OTHER_MODULE_ASSESSMENT = "IS442 Lab";

    // The places of the "Did not compile" and "Timed out" rows on the
    // Grade step
    private static final int COMPILE_ROW = 2;
    private static final int TIMEOUT_ROW = 3;

    // The place of the "Waiting" row on the Grade step
    private static final int WAITING_ROW = 5;

    // The share graded before Cancel: 3 of 5 students
    private static final String STOPPED_PERCENT = "60%";

    // The students before Cancel who did not compile, and who timed out
    private static final int COMPILE_BEFORE_CANCEL = 1;
    private static final int TIMEOUT_BEFORE_CANCEL = 1;

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

    // How many flows the self-test runs
    private static final int FLOW_COUNT = 12;

    // The number of the flow that grades two sections at once
    private static final int PARALLEL_FLOW = 11;

    // Comes between the module, section and assessment on the line
    // above the steps
    private static final String CONTEXT_DOT = " \u00B7 ";

    // The second section graded at the same time as the sample one
    private static final String PARALLEL_SECTION = "G3";

    // The error of the demo's Q3 test 3, failed by learner.charlie and
    // learner.echo, and the end of a Did not compile error's key
    private static final String Q3_TEST_3 = "Q3|F:3";
    private static final String COMPILE_PATTERN = "|COMPILE";

    // The students ticked for export in flow 6
    private static final int TICKED_COUNT = 2;

    // The second sample assessment, which DemoBackend saves
    private static final String SECOND_ASSESSMENT = "Lab Test 2";

    // The questions the second sample assessment keeps
    private static final int SECOND_QUESTION_COUNT = 3;

    // The assessment flow 8 creates
    private static final String NEW_ASSESSMENT = "Final Exam";

    // Turns a fraction into a percentage
    private static final double PERCENT = 100.0;

    // Rounds a number to one decimal place
    private static final double ONE_DECIMAL = 10.0;

    // The fewest backup rows needed to check the newest and the second
    private static final int BACKUP_ROWS_CHECKED = 2;

    // The runs the second newest demo backup lists
    private static final int SECOND_BACKUP_RUNS = 4;

    // The full marks of a one-question assessment made in a flow
    private static final int QUESTION_MAX = 10;

    // The tour stop after the first one
    private static final int SECOND_TOUR_STOP = 2;

    // The demo's Q3 mark of learner.charlie and learner.echo, the mark
    // flow 10 changes the group to, the full Q3 mark and a mark changed
    // later by hand
    private static final double Q3_MARK = 2.0;
    private static final double Q3_CHANGED = 3.0;
    private static final double Q3_FULL = 4.0;
    private static final double Q3_BY_HAND = 1.0;

    // The Q3 test cases, and how many a student who joins the group
    // passes
    private static final int Q3_TESTS = 4;
    private static final int Q3_JOINER_PASSES = 2;

    // The batches flow 11 runs at once
    private static final int PARALLEL_BATCHES = 2;

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
     * Runs the ten flows and prints PASS or FAIL for each.
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
        failed += runFlow(8, "Home shows each section; save, use and "
                + "switch assessments", () -> flowHome());
        failed += runFlow(9, "Take the tour from Help, go back and on, "
                + "end it with Done, Esc and Skip tour", () -> flowTour());
        failed += runFlow(10, "Answer groups: name, change the mark, undo, "
                + "check, compare", () -> flowAnswerGroups());
        failed += runFlow(PARALLEL_FLOW, "Grade two sections at once, "
                + "cancel one, finish the other", () -> flowParallel());
        failed += runFlow(FLOW_COUNT, "Grading names its section; Home "
                + "cards and Continue open the step needed; assessment "
                + "warnings", () -> flowGradingContext());

        if (failed == 0) {
            System.out.println("All " + FLOW_COUNT + " flows passed.");
            return 0;
        }
        System.out.println(failed + " of " + FLOW_COUNT + " flows failed.");
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

    // Home first, then Set up: add the samples, remove the unusable one,
    // start enabled.
    private void flowImport() {
        check(window.getCurrentPage() == Page.HOME, "the window opens on "
                + "Home");
        press(tab("Grading"), "the Grading tab");
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
        check(!start.isDisabled(), "Start grading is enabled, as the "
                + "unusable file is skipped");
        check(statusText().contains("cannot be used and is skipped"),
                "the action bar says a ZIP file cannot be used and is "
                + "skipped (it says \"" + statusText() + "\")");

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
        Node marksSheet = sheet();
        check(marksSheet != null && SceneFinder.shows(marksSheet,
                "Total"), "Start grading asks to confirm the questions and "
                + "their marks");
        if (marksSheet != null) {
            press(SceneFinder.button(marksSheet, "Start grading"),
                    "Start grading");
        }
        check(window.getCurrentStep() == Step.GRADE, "the Grade step opened");
        check(window.getState().isGrading(), "grading started");

        backend.stepGrading(STEPS_PER_SUBMISSION * (GRADED_BEFORE_CANCEL - 1)
                + STEPS_BEFORE_STAGE_CHECK);
        settle();
        String now = nowText();
        check(now.startsWith("Now grading ")
                && !SceneFinder.text(root(), "grade-now-stage").isEmpty(),
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
        check(counts.size() > TIMEOUT_ROW
                && counts.get(COMPILE_ROW).equals(
                String.valueOf(COMPILE_BEFORE_CANCEL))
                && counts.get(TIMEOUT_ROW).equals(
                String.valueOf(TIMEOUT_BEFORE_CANCEL)), "So far counts one "
                + "student who did not compile and one who timed out");
        check(SceneFinder.shows(root(), " left"), "the screen shows about "
                + "how long is left");

        press(SceneFinder.button(window.getActionBar(), STOP_GRADING),
                STOP_GRADING);
        Node sheet = sheet();
        check(sheet != null && SceneFinder.shows(sheet, "keep their marks"),
                "the Stop sheet says graded students keep their marks");
        check(sheet != null && SceneFinder.shows(sheet, "Stop grading "
                + window.getState().getSection().getLabel() + "?")
                && SceneFinder.button(sheet, "Keep grading") != null,
                "the Stop sheet asks \"Stop grading CS102 G1?\" with Keep "
                + "grading and Stop grading");
        if (sheet != null) {
            press(SceneFinder.button(sheet, STOP_GRADING), STOP_GRADING);
        }
        check(!window.isSheetOpen(), "the Stop sheet closed");
        check(statusText().startsWith("Stopping"),
                "the action bar says grading is stopping");
        Button stop = SceneFinder.button(window.getActionBar(),
                STOP_GRADING);
        check(stop != null && stop.isDisabled(), "Stop grading is off "
                + "while grading stops");
        backend.finishGrading();
        settle();

        GradingRun run = window.getState().getRun();
        check(run != null, "the stopped run is kept");
        if (run == null) {
            return;
        }
        check(SceneFinder.shows(root(), "Grading stopped"),
                "the heading says Grading stopped");
        checkStoppedCounts();
        check(count(run, SubmissionStatus.GRADED) == GRADED_BEFORE_CANCEL,
                "the three finished results were kept");
        check(count(run, SubmissionStatus.CANCELLED)
                == USABLE_COUNT - GRADED_BEFORE_CANCEL,
                "the other two were cancelled, not graded");
        Button primary = window.getActionBar().getPrimaryButton();
        check("Resume".equals(primary.getText()) && !primary.isDisabled(),
                "Resume is offered");
        checkStoppedSetUp();

        press(window.getActionBar().getPrimaryButton(), "Resume");
        check(SceneFinder.text(root(), "grade-count").equals(
                String.valueOf(GRADED_BEFORE_CANCEL))
                && SceneFinder.text(root(), "grade-of").equals("of "
                + USABLE_COUNT + " students"), "after Resume the count "
                + "goes on from 3 of 5, not 0 of 2 (it says "
                + SceneFinder.text(root(), "grade-count") + " "
                + SceneFinder.text(root(), "grade-of") + ")");
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

    // Checks that Set up of the stopped section offers Resume with the
    // students left first and Start over second, and lists the section
    // on the Continue card. Leaves Set up on screen.
    private void checkStoppedSetUp() {
        window.show(Step.SET_UP);
        settle();
        int waiting = USABLE_COUNT - GRADED_BEFORE_CANCEL;
        Button primary = window.getActionBar().getPrimaryButton();
        check(("Resume (" + waiting + " left)").equals(primary.getText())
                && !primary.isDisabled(), "Set up of the stopped section "
                + "offers Resume (2 left) (it offers \"" + primary.getText()
                + "\")");
        check(SceneFinder.button(window.getActionBar(), "Start over")
                != null, "Set up offers Start over beside Resume");
        check(statusText().contains("stopped after " + GRADED_BEFORE_CANCEL
                + " of " + USABLE_COUNT), "the action bar says the section "
                + "stopped after 3 of 5 (it says \"" + statusText() + "\")");
        check(continueLineOf(DemoData.MODULE_CODE + CONTEXT_DOT
                + DemoData.SAMPLE_SECTION + CONTEXT_DOT) != null,
                "the Continue card lists the stopped section on screen");
    }

    // Checks that the Grade step of the stopped run counts only the
    // students graded before Cancel: the count, the share, the state,
    // the Waiting row and the marks so far all agree.
    private void checkStoppedCounts() {
        int waiting = USABLE_COUNT - GRADED_BEFORE_CANCEL;
        String shownCount = SceneFinder.text(root(), "grade-count");
        check(shownCount.equals(String.valueOf(GRADED_BEFORE_CANCEL)),
                "after Cancel the count says 3, not every student (it "
                + "says " + shownCount + ")");
        String shownPercent = SceneFinder.text(root(), "grade-percent");
        check(shownPercent.equals(STOPPED_PERCENT), "after Cancel the "
                + "share says " + STOPPED_PERCENT + " (it says "
                + shownPercent + ")");
        check(SceneFinder.shows(root(), "Stopped")
                && !SceneFinder.shows(root(), "Done"),
                "after Cancel the state says Stopped, not Done");
        List<String> counts = SceneFinder.texts(root(), "count-value");
        check(counts.size() > WAITING_ROW && counts.get(WAITING_ROW).equals(
                String.valueOf(waiting)), "So far says 2 are waiting");
        GradingJob job = window.getState().getJob(
                window.getState().getSection());
        check(job != null && job.getResults().size()
                == GRADED_BEFORE_CANCEL, "the marks so far leave out the "
                + "students cancelled before their turn");
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
            press(SceneFinder.button(card,
                    "Keep fix"), "Keep fix");
            check(fix.getFixStatus() == FixStatus.APPROVED,
                    "the fix was approved");
            card = cardOf(fix);
            check(card != null && SceneFinder.shows(card, "Fix kept"),
                    "the panel shows Fix kept");
            approvedFix = fix;
        }
        checkApplyAgain(run);

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
        StudentPanel panel = studentPanel();
        if (panel != null) {
            panel.setVvalue(panel.getVmax());
        }
        press(SceneFinder.button(card, "Looks fine"),
                "Looks fine");
        check(backend.getDecision(flag) == ReviewDecision.REJECTED,
                "the flag was rejected");
        ButtonBase noticeUndo = noticeAction();
        check(noticeUndo != null, "the notice offers Undo");
        press(noticeUndo, "Undo in the notice");
        check(backend.getDecision(flag) == ReviewDecision.OPEN,
                "Undo in the notice put the flag back to open");
        card = cardOf(flag);
        press(SceneFinder.button(card, "Looks fine"),
                "Looks fine again");
        check(backend.getDecision(flag) == ReviewDecision.REJECTED,
                "the flag was rejected again");
        String place = "no panel";
        if (panel != null) {
            place = String.valueOf(panel.getVvalue());
        }
        check(panel != null && panel.getVvalue() > panel.getVmin(),
                "the panel kept its place after Looks fine (it is at "
                + place + ")");
        card = cardOf(flag);
        press(SceneFinder.button(card, "Undo"),
                "Undo");
        check(backend.getDecision(flag) == ReviewDecision.OPEN,
                "Reopen put the flag back to open");
        card = cardOf(flag);
        check(card != null && SceneFinder.button(card, "Looks fine")
                != null, "the flag can be decided again");
        reopenedFlag = flag;
        checkSettledIsGraded(run);
        checkSameFixes(run);
    }

    // Keep fix and Reject fix offer to decide the same kind of fix of
    // the other students too: Keep all and its Undo, Just this one,
    // Cancel and Reject all. The demo has one fix of each kind, so a
    // second wrong folder name is added for the check and removed after.
    private void checkSameFixes(GradingRun run) {
        Anomaly first = null;
        Submission other = null;
        for (Submission submission : run.getSubmissions()) {
            for (Anomaly anomaly : submission.getAnomalies()) {
                if (first == null && anomaly.getType()
                        == AnomalyType.WRONG_FOLDER_NAME) {
                    first = anomaly;
                }
            }
        }
        for (Submission submission : run.getSubmissions()) {
            if (other == null && first != null
                    && submission.getStatus() == SubmissionStatus.GRADED
                    && !submission.getUsername().equals(
                    first.getUsername())) {
                other = submission;
            }
        }
        check(first != null && other != null, "a wrong folder name and a "
                + "second graded student exist");
        if (first == null || other == null) {
            return;
        }
        FixStatus firstBefore = first.getFixStatus();
        Anomaly second = new Anomaly("selftest-same-fix",
                AnomalyType.WRONG_FOLDER_NAME, Severity.WARNING,
                other.getUsername(), "The top folder was named "
                + "RenameToYourStudentID (self-test).",
                "Approve the rename, or reject it.");
        other.addAnomaly(second);
        first.setFixStatus(FixStatus.APPLIED);
        second.setFixStatus(FixStatus.APPLIED);
        window.refreshScreen();
        settle();

        openStudent(first.getUsername());
        press(SceneFinder.button(cardOf(first), "Keep fix"), "Keep fix");
        Node asked = sheet();
        check(asked != null && SceneFinder.shows(asked,
                "Keep this fix for 1 more student?")
                && SceneFinder.shows(asked, other.getUsername()),
                "Keep fix asks to keep the same fix of "
                + other.getUsername());
        press(SceneFinder.button(asked, "Keep all 2"), "Keep all 2");
        check(!window.isSheetOpen()
                && first.getFixStatus() == FixStatus.APPROVED
                && second.getFixStatus() == FixStatus.APPROVED,
                "Keep all kept both fixes");
        check(noticeText().equals("Kept 2 fixes."), "the notice says Kept "
                + "2 fixes (it says \"" + noticeText() + "\")");
        press(noticeAction(), "Undo in the notice");
        check(first.getFixStatus() == FixStatus.APPLIED
                && second.getFixStatus() == FixStatus.APPLIED,
                "Undo put both fixes back to review");

        press(SceneFinder.button(cardOf(first), "Keep fix"), "Keep fix");
        press(SceneFinder.button(sheet(), "Cancel"), "Cancel");
        check(!window.isSheetOpen()
                && first.getFixStatus() == FixStatus.APPLIED
                && second.getFixStatus() == FixStatus.APPLIED,
                "Cancel decided neither fix");

        press(SceneFinder.button(cardOf(first), "Keep fix"), "Keep fix");
        press(SceneFinder.button(sheet(), "Just this one"),
                "Just this one");
        check(first.getFixStatus() == FixStatus.APPROVED
                && second.getFixStatus() == FixStatus.APPLIED,
                "Just this one kept only the fix pressed");
        backend.undoRepairDecision(repairOf(run, first));
        window.refreshScreen();
        settle();

        press(SceneFinder.button(cardOf(first), "Reject fix"),
                "Reject fix");
        asked = sheet();
        check(asked != null && SceneFinder.shows(asked,
                "Reject this fix for 1 more student?"),
                "Reject fix asks to reject the same fix too");
        press(SceneFinder.button(asked, "Reject all 2"), "Reject all 2");
        check(first.getFixStatus() == FixStatus.UNDONE
                && second.getFixStatus() == FixStatus.UNDONE,
                "Reject all rejected both fixes");
        check(noticeText().startsWith("Rejected 2 fixes"), "the notice "
                + "says Rejected 2 fixes (it says \"" + noticeText()
                + "\")");

        other.getAnomalies().remove(second);
        first.setFixStatus(firstBefore);
        window.refreshScreen();
        settle();
    }

    // Returns the repair of an anomaly in a run, or null.
    private RepairItem repairOf(GradingRun run, Anomaly anomaly) {
        for (RepairItem repair : backend.listRepairs(run)) {
            if (repair.getAnomaly() == anomaly) {
                return repair;
            }
        }
        return null;
    }

    // A rejected fix can be applied again from the activity log and
    // from its card, which puts it back to wait for review.
    private void checkApplyAgain(GradingRun run) {
        RepairItem waiting = null;
        for (RepairItem repair : backend.listRepairs(run)) {
            if (waiting == null && !repair.isBlocked()
                    && repair.getAnomaly().getFixStatus()
                    == FixStatus.APPLIED) {
                waiting = repair;
            }
        }
        check(waiting != null, "a second fix waits for review");
        if (waiting == null) {
            return;
        }
        Anomaly fix = waiting.getAnomaly();
        rejectInPanel(fix);

        window.showPage(Page.RUNS);
        settle();
        ((gradezzy.ui.flow.RunsScreen) window.getPageScreen(Page.RUNS))
                .showActivity();
        settle();
        press(SceneFinder.button(root(), "Undo"), "Undo on Fix undone");
        check(fix.getFixStatus() == FixStatus.APPLIED,
                "Undo on Fix undone applied the fix again");
        boolean logged = false;
        for (gradezzy.model.AuditEntry entry : backend.listAuditEntries()) {
            logged = logged || "Fix reapplied".equals(entry.getAction());
        }
        check(logged, "Fix reapplied is in the activity log");
        window.show(Step.REVIEW);
        settle();

        rejectInPanel(fix);
        Node card = cardOf(fix);
        press(SceneFinder.button(card,
                "Apply fix again"), "Apply fix again");
        check(fix.getFixStatus() == FixStatus.APPLIED,
                "Apply fix again put the fix back to wait for review");
        card = cardOf(fix);
        check(card != null && SceneFinder.shows(card, "Fixed for you"),
                "the panel shows the fix as Fixed for you again");
    }

    // Rejects a fix in the student panel and checks its card offers
    // Apply fix again.
    private void rejectInPanel(Anomaly fix) {
        openStudent(fix.getUsername());
        Node card = cardOf(fix);
        press(SceneFinder.button(card,
                "Reject fix"), "Reject fix");
        check(fix.getFixStatus() == FixStatus.UNDONE,
                "the fix was rejected");
        card = cardOf(fix);
        check(card != null && SceneFinder.shows(card, "Fix rejected")
                && SceneFinder.button(card, "Apply fix again") != null,
                "a rejected fix offers Apply fix again");
    }

    // A student whose code did not compile shows Graded once every issue
    // is settled, and Open code on the issue opens their code.
    private void checkSettledIsGraded(GradingRun run) {
        Submission compiled = null;
        Anomaly compileError = null;
        for (Submission submission : run.getSubmissions()) {
            for (Anomaly anomaly : submission.getAnomalies()) {
                if (compileError == null
                        && anomaly.getType() == AnomalyType.COMPILE_ERROR) {
                    compiled = submission;
                    compileError = anomaly;
                }
            }
        }
        check(compileError != null, "a compile error is flagged");
        if (compileError == null) {
            return;
        }
        openStudent(compiled.getUsername());
        Node card = cardOf(compileError);
        press(SceneFinder.button(card, "Open code"),
                "Open code");
        check(window.isSheetOpen(), "Open code opened the code sheet");
        Node codeSheet = sheet();
        Button inSheet = SceneFinder.button(codeSheet, "Mark as checked");
        check(inSheet != null, "the code sheet offers Mark as checked");
        press(inSheet, "Mark as checked in the code sheet");
        check(!window.isSheetOpen() && backend.getDecision(compileError)
                == ReviewDecision.REVIEWED, "deciding in the code sheet "
                + "closes it and keeps the decision");
        press(noticeAction(), "Undo in the notice");
        check(backend.getDecision(compileError) == ReviewDecision.OPEN,
                "Undo took back the decision made in the code sheet");
        window.closeSheet();
        settle();

        List<Anomaly> settled = new ArrayList<>();
        for (Anomaly anomaly : compiled.getAnomalies()) {
            if (backend.needsReview(anomaly)) {
                backend.setDecision(anomaly, ReviewDecision.REVIEWED);
                settled.add(anomaly);
            }
        }
        check(Students.state(compiled, backend) == ResultState.COMPLETED,
                "a settled compile error shows Graded (it shows "
                + Students.state(compiled, backend) + ")");
        check(!StatusCounts.needsLook(compiled, backend),
                "a settled student no longer needs a look");
        for (Anomaly anomaly : settled) {
            backend.setDecision(anomaly, ReviewDecision.OPEN);
        }
        window.refreshScreen();
        settle();
        checkMarkAll(run);
    }

    // Mark all as checked settles every open flag it may check together,
    // and Undo puts them back.
    private void checkMarkAll(GradingRun run) {
        Button markAll = null;
        for (Button button : SceneFinder.ofType(window.getActionBar(),
                Button.class)) {
            if (button.getText().startsWith("Mark all ")) {
                markAll = button;
            }
        }
        check(markAll != null, "Review offers Mark all as checked");
        if (markAll == null) {
            return;
        }
        int before = countOpenFlags(run);
        int similarBefore = countOpenSimilar(run);
        press(markAll, "Mark all as checked");
        Node sheet = sheet();
        press(SceneFinder.button(sheet,
                "Mark all as checked"), "Mark all as checked in the sheet");
        check(countOpenFlags(run) == 0, "every flag is checked (still "
                + countOpenFlags(run) + " open)");
        check(similarBefore > 0 && countOpenSimilar(run) == similarBefore,
                "Mark all leaves the " + similarBefore + " Similar code "
                + "flags to decide under Similarity (now "
                + countOpenSimilar(run) + " open)");
        check(actionBarButton("Mark all ") == null, "Mark all is gone once "
                + "nothing is left to check");
        press(noticeAction(), "Undo in the notice");
        check(countOpenFlags(run) == before, "Undo put the " + before
                + " flags back (now " + countOpenFlags(run) + ")");
        checkActivityUndo(run);
    }

    // A flag marked as checked is in the activity log, and Undo there
    // puts it back to check.
    private void checkActivityUndo(GradingRun run) {
        Anomaly flag = null;
        for (Submission submission : run.getSubmissions()) {
            for (Anomaly anomaly : submission.getAnomalies()) {
                if (flag == null && anomaly.getFixStatus()
                        != FixStatus.APPLIED && backend.needsReview(anomaly)) {
                    flag = anomaly;
                }
            }
        }
        if (flag == null) {
            return;
        }
        backend.setDecision(flag, ReviewDecision.REVIEWED);
        String expected = ReviewDecision.REVIEWED.getAuditAction(
                flag.getType());
        boolean logged = false;
        for (gradezzy.model.AuditEntry entry : backend.listAuditEntries()) {
            logged = logged || expected.equals(entry.getAction());
        }
        check(logged, expected + " is in the activity log");
        window.showPage(Page.RUNS);
        settle();
        ((gradezzy.ui.flow.RunsScreen) window.getPageScreen(Page.RUNS))
                .showActivity();
        settle();
        Button undo = SceneFinder.button(root(), "Undo");
        check(undo != null, "the activity log offers Undo");
        press(undo, "Undo in the activity log");
        check(backend.getDecision(flag) == ReviewDecision.OPEN,
                "Undo in the activity log put the flag back to check");
        window.show(Step.REVIEW);
        settle();
    }

    // Returns the action bar button whose words start with a text, or
    // null.
    private Button actionBarButton(String start) {
        for (Button button : SceneFinder.ofType(window.getActionBar(),
                Button.class)) {
            if (button.isVisible() && button.getText().startsWith(start)) {
                return button;
            }
        }
        return null;
    }

    // Counts the Similar code flags still open.
    private int countOpenSimilar(GradingRun run) {
        int open = 0;
        for (Submission submission : run.getSubmissions()) {
            for (Anomaly anomaly : submission.getAnomalies()) {
                if (anomaly.getType() == AnomalyType.SIMILAR_CODE
                        && backend.needsReview(anomaly)) {
                    open++;
                }
            }
        }
        return open;
    }

    // Counts the open flags and problems that are not fixes.
    private int countOpenFlags(GradingRun run) {
        int open = 0;
        for (Submission submission : run.getSubmissions()) {
            for (Anomaly anomaly : submission.getAnomalies()) {
                if (anomaly.getFixStatus() != FixStatus.APPLIED
                        && ReviewRules.canCheckAll(anomaly.getType())
                        && backend.needsReview(anomaly)) {
                    open++;
                }
            }
        }
        return open;
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
        check(SceneFinder.shows(row, "timed out"), "Q1a says, without a "
                + "click, that a test timed out");
        SceneFinder.click(((Parent) row)
                .getChildrenUnmodifiable().get(0));
        settle();
        row = questionRow(DemoData.TIMEOUT_QUESTION);
        press(SceneFinder.link(row, "Grade "
                + DemoData.TIMEOUT_QUESTION + " again"), "Grade Q1a again");
        Node sheet = sheet();
        check(sheet != null, "Grade Q1a again asks first");
        if (sheet != null) {
            press(SceneFinder.button(sheet, "Grade again"), "Grade again");
        }
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
        press(SceneFinder.link(row, "Adjust"),
                "Adjust");
        Node sheet = sheet();
        check(sheet != null && SceneFinder.shows(sheet, "Adjust "
                + ADJUSTED_QUESTION), "the Adjust sheet opened");
        if (sheet == null) {
            return;
        }

        trySave(sheet, Parts.mark(ADJUSTED_TO), "");
        Button save = SceneFinder.button(sheet, "Save");
        check(save != null && save.isDisabled() && window.isSheetOpen(),
                "Save is off while the reason is blank");
        String problem = trySave(sheet, TOO_HIGH, REASON);
        check(problem.contains("from 0 to"), "a mark above the maximum is "
                + "refused");
        check(window.isSheetOpen(), "the sheet stays open after a refusal");
        trySave(sheet, Parts.mark(ADJUSTED_TO), REASON);
        check(!window.isSheetOpen(), "Save closed the sheet");

        QuestionResult q1b = result(bravo, ADJUSTED_QUESTION);
        check(q1b != null && q1b.getScore() == ADJUSTED_TO,
                "Q1b has the new mark");
        check(backend.listAdjustments(window.getState().getRun(), bravo)
                .size() == 1, "the change and its reason are kept");
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
        check(backend.listAdjustments(window.getState().getRun(), bravo)
                .isEmpty(), "Undo removed the change");
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
        check(statusText().contains(TICKED_COUNT + " students ticked."),
                "the action bar says two students are ticked");
        check(SceneFinder.shows(root(), TICKED_COUNT + " students ticked")
                && SceneFinder.link(root(), "Clear ticks") != null
                && SceneFinder.link(root(), "Export only these") != null,
                "the band over the table says two are ticked and offers "
                + "Export only these and Clear ticks");

        press(SceneFinder.link(root(), "Export only these"),
                "Export only these");
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
        checkCommonMistakes();

        choose(DemoData.SAMPLE_SECTION, EMPTY_SECTION);
        check(SceneFinder.shows(root(), EMPTY_SECTION + " has not been "
                + "graded yet"), "a section without a run says so");
        choose(DemoData.MODULE_CODE, DemoData.EMPTY_MODULE_CODE);
        check(SceneFinder.shows(root(), "Nothing graded yet"),
                "the empty module says nothing is graded");
        choose(DemoData.EMPTY_MODULE_CODE, DemoData.MODULE_CODE);
        choose(EMPTY_SECTION, DemoData.SAMPLE_SECTION);
        checkFigures(run);

        press(tab("Grading"), "the Grading tab");
        check(window.getCurrentStep() == Step.EXPORT,
                "Grading went back to Export");
        RadioButton ticked = null;
        for (RadioButton radio : SceneFinder.ofType(root(),
                RadioButton.class)) {
            if (radio.getText().startsWith("Only the students I choose")) {
                ticked = radio;
            }
        }
        check(ticked != null && ticked.isSelected()
                && ticked.getText().endsWith("(" + TICKED_COUNT + ")"),
                "Only the students I choose (2) is chosen");

        CheckBox mistakesBox = null;
        for (CheckBox box : SceneFinder.ofType(root(), CheckBox.class)) {
            if (box.getText().startsWith("Also save the common mistakes")) {
                mistakesBox = box;
            }
        }
        check(mistakesBox != null, "Export offers the common mistakes");
        if (mistakesBox != null) {
            mistakesBox.setSelected(true);
        }
        int filesBefore = outputFileCount();
        press(window.getActionBar().getPrimaryButton(), "Export score sheet");
        Node confirm = sheet();
        String question = "Export " + TICKED_COUNT + " students";
        check(confirm != null && SceneFinder.shows(confirm, question + "?"),
                "Export asks \"" + question + "?\" first");
        check(confirm != null && SceneFinder.shows(confirm,
                "the common mistakes"), "the export question names the "
                + "common mistakes");
        if (mistakesBox != null) {
            mistakesBox.setSelected(false);
        }
        if (confirm != null) {
            press(SceneFinder.button(confirm, question), question);
        }
        String message = "";
        // Demo mode writes no file, so the pill says Preview
        Label saved = SceneFinder.label(root(), "Preview");
        if (saved != null) {
            message = SceneFinder.ofType(saved.getParent(), Label.class)
                    .get(SceneFinder.ofType(saved.getParent(), Label.class)
                    .size() - 1).getText();
        }
        check(saved != null, "the Preview pill is shown");
        check(message.contains("for " + TICKED_COUNT + " students"),
                "only the two ticked students were exported (it says \""
                + message + "\")");
        // Only part of the run was exported, so it is not marked exported
        check("Export score sheet".equals(window.getActionBar()
                .getPrimaryButton().getText()), "after exporting only the "
                + "ticked students, the button still says Export score "
                + "sheet");
        check(!window.getState().isExported(run), "a ticked-only export "
                + "does not mark the run exported");
        check(outputFileCount() == filesBefore,
                "no file appeared in the output folder");
        notes.add("Export: " + message + " Output folder: "
                + backend.getSetting(ConfigKeys.OUTPUT_DIR) + ".");
        checkExportForgotten(run);
        checkKeptBackups();
    }

    // History, Backups lists the kept backups, newest first and open,
    // with the runs inside each. Another opens on a click, Back up now
    // adds one and drops the oldest, and Restore this backup asks first.
    private void checkKeptBackups() {
        window.showPage(Page.RUNS);
        settle();
        ((gradezzy.ui.flow.RunsScreen) window.getPageScreen(Page.RUNS))
                .showBackups();
        settle();
        int keep = backend.getBackupKeepCount();
        check(SceneFinder.shows(root(), "Kept backups"),
                "Backups shows the Kept backups card");
        check(SceneFinder.shows(root(), keep + " of " + keep + " kept; the "
                + "oldest is deleted at the next backup."),
                "the card says " + keep + " of " + keep + " are kept");
        List<Node> rows = new ArrayList<>(
                root().lookupAll(".backup-row"));
        check(rows.size() == keep, "one row for each of the " + keep
                + " kept backups (found " + rows.size() + ")");
        check(count(SceneFinder.texts(root(), "pill"), "Newest") == 1,
                "exactly one backup is marked Newest");
        if (rows.size() < BACKUP_ROWS_CHECKED) {
            return;
        }
        Node newest = rows.get(0);
        check(newest.getStyleClass().contains("open")
                && SceneFinder.shows(newest, "16 Oct 2026, 09:10")
                && SceneFinder.shows(newest, "5 runs")
                && SceneFinder.shows(newest, "Lab Test 2")
                && SceneFinder.shows(newest, "40 students")
                && SceneFinder.shows(newest, "3 not graded"),
                "the newest backup is open and lists its 5 runs, the "
                + "newest one with 40 students, 3 not graded");
        Node second = rows.get(1);
        check(!second.getStyleClass().contains("open"),
                "the other backups start closed");
        SceneFinder.click(second.lookup(".error-header"));
        settle();
        check(second.getStyleClass().contains("open")
                && second.lookupAll(".backup-run").size()
                == SECOND_BACKUP_RUNS,
                "a click opens a backup and lists its 4 runs");

        Object oldest = rows.get(rows.size() - 1).getUserData();
        press(SceneFinder.button(root(), "Back up now"), "Back up now");
        rows = new ArrayList<>(root().lookupAll(".backup-row"));
        boolean oldestGone = true;
        for (Node row : rows) {
            oldestGone = oldestGone && !row.getUserData().equals(oldest);
        }
        check(rows.size() == keep && oldestGone
                && !rows.get(0).getUserData().equals(newest.getUserData()),
                "Back up now lists a new newest backup and drops the "
                + "oldest, still " + keep + " kept");

        press(SceneFinder.button(root(), "Restore this backup"),
                "Restore this backup");
        Node confirm = sheet();
        check(confirm != null && SceneFinder.shows(confirm,
                "The results saved now are backed up first"),
                "Restore this backup asks first, as Choose a backup does");
        if (confirm != null) {
            press(SceneFinder.button(confirm, "Restore"), "Restore");
        }
        check(noticeText().contains("Demo mode only shows sample backups"),
                "in demo mode the restore says nothing was restored");
        press(tab("Grading"), "the Grading tab");
    }

    // With the run forgotten, Export shows no Saved note from the last
    // export. Then the run is opened on Export again, still ticked.
    private void checkExportForgotten(GradingRun run) {
        List<Submission> ticked = new ArrayList<>(
                window.getState().getSelected());
        window.forgetRun();
        window.advanceTo(Step.EXPORT);
        settle();
        check(SceneFinder.label(root(), "Preview") == null,
                "with no run, Export shows no Preview pill");
        check("Export score sheet".equals(window.getActionBar()
                .getPrimaryButton().getText()), "with no run, the button "
                + "says Export score sheet");
        window.openRun(run);
        window.getState().getSelected().addAll(ticked);
        window.advanceTo(Step.EXPORT);
        settle();
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

    // Settings: dark and back; Grading keeps its step, run and choices.
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
        checkModelAnswerChecks();
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
        checkLeaveSettings();
        checkLeaveAssessmentForm();

        press(tab("Grading"), "the Grading tab");
        Node sheet = sheet();
        check(sheet == null, "leaving Settings asked nothing");
        if (sheet != null) {
            window.closeSheet();
        }
        check(window.getCurrentPage() == Page.MARKING
                && window.getCurrentStep() == Step.REVIEW,
                "Grading is still on Review");
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

    // A changed setting asks before another tab opens: Stay keeps
    // Settings, Discard opens the tab and the setting keeps its value.
    private void checkLeaveSettings() {
        gradezzy.ui.flow.SettingsScreen settings =
                (gradezzy.ui.flow.SettingsScreen) window.getPageScreen(
                Page.SETTINGS);
        String before = backend.getSetting(ConfigKeys.OUTPUT_DIR);
        settings.previewChange(ConfigKeys.OUTPUT_DIR, before + "-other");
        settle();
        check(window.getTabBar().isPending(Page.SETTINGS),
                "the Settings tab shows a dot for the changed setting");
        press(tab("Home"), "the Home tab");
        Node sheet = sheet();
        check(sheet != null && SceneFinder.shows(sheet,
                "Save your Settings changes?"), "leaving Settings with a "
                + "changed setting asks to save it");
        if (sheet == null) {
            return;
        }
        press(SceneFinder.button(sheet, "Stay"), "Stay");
        check(sheet() == null && window.getCurrentPage() == Page.SETTINGS,
                "Stay keeps Settings on screen");
        check(settings.hasUnsavedChanges(), "Stay keeps the change");
        press(tab("Home"), "the Home tab");
        sheet = sheet();
        if (sheet == null) {
            problems.add("expected: leaving Settings asks again");
            return;
        }
        press(SceneFinder.button(sheet, "Discard"), "Discard");
        check(window.getCurrentPage() == Page.HOME, "Discard opens Home");
        check(before.equals(backend.getSetting(ConfigKeys.OUTPUT_DIR))
                && !settings.hasUnsavedChanges(), "Discard keeps the "
                + "saved output folder " + before);
        check(!window.getTabBar().isPending(Page.SETTINGS),
                "the Settings tab shows no dot after Discard");
        press(tab("Settings"), "the Settings tab");
    }

    // A change in Settings' assessment form also asks before another
    // tab opens; Discard puts the saved name back.
    private void checkLeaveAssessmentForm() {
        String name = backend.getAssessment().getName();
        TextField nameField = fieldWithText(name);
        check(nameField != null, "Settings' form shows the name " + name);
        if (nameField == null) {
            return;
        }
        nameField.setText(name + " draft");
        press(tab("Home"), "the Home tab");
        Node sheet = sheet();
        check(sheet != null && SceneFinder.shows(sheet, "assessment form"),
                "leaving Settings with a changed assessment form asks to "
                + "save it");
        if (sheet == null) {
            return;
        }
        press(SceneFinder.button(sheet, "Discard"), "Discard");
        check(window.getCurrentPage() == Page.HOME, "Discard opens Home");
        check(name.equals(backend.getAssessment().getName()),
                "Discard keeps the assessment named " + name);
        press(tab("Settings"), "the Settings tab");
        check(fieldWithText(name + " draft") == null,
                "the form shows the saved name again");
    }

    // Returns the first showing text field holding some text, or null.
    private TextField fieldWithText(String text) {
        TextField found = null;
        for (TextField field : SceneFinder.ofType(root(), TextField.class)) {
            if (found == null && text.equals(field.getText())) {
                found = field;
            }
        }
        return found;
    }

    // ------------------------------------------------------------ flow 8

    // Home: a card per section; switching assessment changes the cards;
    // a new assessment is checked and saved; opening a run switches back
    // to the assessment it was graded with; Set up shows no ticks for a
    // new batch.
    private void flowHome() {
        press(tab("Home"), "the Home tab");
        check(window.getCurrentPage() == Page.HOME, "Home is shown");
        check(SceneFinder.texts(root(), "section-name").contains(
                DemoData.SAMPLE_SECTION), "Home has a card for "
                + DemoData.SAMPLE_SECTION);
        check(offersNewSection(), "Home's section dropdown offers to add a "
                + "section");

        window.openAssessments();
        settle();
        check(sheet() != null && SceneFinder.shows(sheet(),
                SECOND_ASSESSMENT), "the assessments sheet lists "
                + SECOND_ASSESSMENT);
        window.closeSheet();
        check(window.useAssessment(SECOND_ASSESSMENT), "switching to "
                + SECOND_ASSESSMENT + " works");
        settle();
        check(backend.getAssessment().getName().equals(SECOND_ASSESSMENT)
                && backend.getAssessment().getQuestions().size()
                == SECOND_QUESTION_COUNT, SECOND_ASSESSMENT + " is in use "
                + "with its own questions");
        check(SceneFinder.texts(root(), "pill").contains("Not graded"),
                "Home shows the sections as not graded for "
                + SECOND_ASSESSMENT);
        checkRunsCurrent();

        SavedAssessment draft = new SavedAssessment();
        draft.setName(SECOND_ASSESSMENT.toUpperCase(Locale.ROOT));
        draft.getQuestions().add(new Question("Q1", "Q1", "Q1.java",
                QUESTION_MAX));
        draft.setTestersDirectory("testers");
        draft.setStarterDirectory("starter");
        check(!backend.validateAssessment(draft, null).isEmpty(),
                "a second assessment with the same name is refused");
        draft.setName(NEW_ASSESSMENT);
        ActionResult saved = backend.saveAssessment(draft, null);
        check(saved.isDone(), "a new assessment is saved ("
                + saved.getMessage() + ")");
        window.editAssessment(NEW_ASSESSMENT);
        settle();
        check(sheet() != null && SceneFinder.shows(sheet(), "Edit "
                + NEW_ASSESSMENT), "the editor opens on " + NEW_ASSESSMENT);
        window.closeSheet();
        checkSaveKeepsInUse();

        GradingRun sample = backend.getRun(window.getState().getSection());
        if (sample == null) {
            problems.add("expected: the sample section has a run");
            return;
        }
        window.openRun(sample);
        settle();
        check(backend.getAssessment().getName().equals(
                sample.getAssessment().getName()), "opening a run switches "
                + "back to the assessment it was graded with");
        checkRunsCurrent();
        checkOpenedRunIsCurrent(sample);
        window.show(Step.GRADE);
        settle();
        String count = "of " + sample.getSubmissions().size() + " students";
        check(SceneFinder.shows(root(), count) && (SceneFinder.shows(root(),
                "Grading stopped") || SceneFinder.shows(root(),
                "Grading finished")), "Grade shows the opened run as it "
                + "ended (" + count + "), not an empty screen; it shows "
                + SceneFinder.texts(root(), "heading") + ", step "
                + window.getCurrentStep());
        window.show(Step.SET_UP);
        settle();
        // Back on Set up of the graded section, Grade stays done
        check(scene.getRoot().lookupAll(".step-dot.done").size() >= 1,
                "Set up of a graded section keeps Grade ticked");
        String otherSection = "G2";
        if (otherSection.equals(sample.getSection().getSectionCode())) {
            otherSection = "G3";
        }
        window.chooseSection(sample.getSection().getModuleCode(),
                otherSection);
        window.show(Step.SET_UP);
        settle();
        check(scene.getRoot().lookupAll(".step-dot.done").isEmpty(),
                "Set up of a section not graded yet shows no ticks");
        window.show(Step.REVIEW);
        settle();
        check(window.getCurrentStep() == Step.SET_UP, "Review of a section "
                + "not graded yet stays locked, so it never shows the run "
                + "of " + sample.getSection().getLabel());
        checkExportFolders();
        checkShortenPaths();
    }

    // Home's recent activity and the activity log shorten each absolute
    // path of a line to its last part, and keep the rest of the line.
    private void checkShortenPaths() {
        String settings = "Changed paths.testers.dir=/Users/me/Lab Test 3/"
                + "Tester-Files, paths.starter.dir=/Users/me/Lab Test 3/"
                + "starter, paths.score.sheet.file=/Users/me/G1.csv.";
        String shortened = Parts.shortenPaths(settings);
        check(shortened.equals("Changed paths.testers.dir=Tester-Files, "
                + "paths.starter.dir=starter, paths.score.sheet.file="
                + "G1.csv."), "a list of settings keeps every name when "
                + "its paths are shortened (it reads \"" + shortened
                + "\")");
        String one = Parts.shortenPaths("Read /Users/me/Test Data/x.csv "
                + "and Q3/Q3.java");
        check(one.equals("Read x.csv and Q3/Q3.java"), "a path with a space "
                + "is shortened and a relative one kept (it reads \"" + one
                + "\")");
    }

    // History, Runs marks as Current only runs of the assessment in use,
    // one per section, the same runs Home's cards show.
    private void checkRunsCurrent() {
        String inUse = backend.getAssessment().getName();
        java.util.Map<String, GradingRun> onHome =
                SectionProgress.findShownRuns(backend.listRuns(),
                window.getState().getModuleCode(), inUse,
                window.getState().getRun());
        window.showPage(Page.RUNS);
        settle();
        ((gradezzy.ui.flow.RunsScreen) window.getPageScreen(Page.RUNS))
                .showRuns();
        settle();
        java.time.format.DateTimeFormatter when =
                java.time.format.DateTimeFormatter.ofPattern(
                "d MMM yyyy, HH:mm");
        List<String> expected = new ArrayList<>();
        for (GradingRun run : onHome.values()) {
            expected.add(run.getSection().getSectionCode() + " Graded "
                    + run.getStartedAt().format(when));
        }
        List<String> sections = new ArrayList<>();
        boolean allInUse = true;
        boolean allOnHome = true;
        for (Node badge : root().lookupAll(".current-badge")) {
            List<Label> labels = SceneFinder.ofType(badge.getParent(),
                    Label.class);
            List<Label> cell = SceneFinder.ofType(badge.getParent()
                    .getParent(), Label.class);
            String name = labels.get(0).getText();
            String code = name.substring(name.lastIndexOf(' ') + 1);
            sections.add(name);
            allInUse = allInUse && labels.get(1).getText().equals(inUse);
            allOnHome = allOnHome && expected.contains(code + " "
                    + cell.get(labels.size()).getText());
        }
        check(allInUse, "History marks as Current only runs of " + inUse);
        check(allOnHome, "each run History marks as Current is the one "
                + "Home shows for its section " + expected);
        check(sections.size() == onHome.size(), "History marks "
                + onHome.size() + " runs as Current, one for each section "
                + "Home shows a run for with " + inUse + " (it marks "
                + sections + ")");
    }

    // Opening an earlier run makes it the Current one of its section in
    // History, then the run shown before is opened again.
    private void checkOpenedRunIsCurrent(GradingRun shownBefore) {
        GradingRun earlier = null;
        for (gradezzy.ui.backend.RunRecord record : backend.listRuns()) {
            if (DemoData.EARLIER_RUN_ID.equals(record.getRun().getId())) {
                earlier = record.getRun();
            }
        }
        if (earlier == null) {
            problems.add("expected: the demo lists an earlier run");
            return;
        }
        window.openRun(earlier);
        settle();
        checkRunsCurrent();
        java.util.Map<String, GradingRun> onHome =
                SectionProgress.findShownRuns(backend.listRuns(),
                window.getState().getModuleCode(),
                backend.getAssessment().getName(),
                window.getState().getRun());
        check(onHome.get(earlier.getSection().getSectionCode()) == earlier,
                "the earlier run opened is the one shown for "
                + earlier.getSection().getLabel());
        window.openRun(shownBefore);
        settle();
    }

    // Saving a duplicated assessment in the editor keeps the one in use;
    // the notice offers Use this one, which switches to it. The copy is
    // then deleted, so the later flows see the same assessments.
    private void checkSaveKeepsInUse() {
        String inUse = backend.getAssessment().getName();
        SavedAssessment copy = null;
        for (SavedAssessment saved : backend.listAssessments()) {
            if (saved.getName().equals(NEW_ASSESSMENT)) {
                copy = saved.copy();
            }
        }
        if (copy == null) {
            problems.add("expected: " + NEW_ASSESSMENT + " is saved");
            return;
        }
        String copyName = NEW_ASSESSMENT + " copy";
        copy.setName(copyName);
        window.showSheet(AssessmentEditor.create(window, copy, null));
        settle();
        press(SceneFinder.button(sheet(), "Save"), "Save in the editor");
        check(sheet() == null, "the editor closes after Save");
        check(backend.getAssessment().getName().equals(inUse), "saving a "
                + "new assessment keeps " + inUse + " in use (now "
                + backend.getAssessment().getName() + ")");
        ButtonBase use = noticeAction();
        check(use != null && use.getText().equals("Use this one"),
                "the notice offers Use this one (it says \""
                + noticeText() + "\")");
        if (use == null) {
            return;
        }
        press(use, "Use this one in the notice");
        check(backend.getAssessment().getName().equals(copyName),
                "Use this one puts " + copyName + " in use");
        check(window.useAssessment(inUse), "switching back to " + inUse
                + " works");
        settle();
        check(backend.deleteAssessment(copyName).isDone(), "the copy is "
                + "deleted again");
        String renamed = NEW_ASSESSMENT + " renamed";
        renameInSettings(NEW_ASSESSMENT, renamed);
        renameInSettings(renamed, NEW_ASSESSMENT);
        check(backend.getAssessment().getName().equals(inUse), "renaming "
                + "in Settings keeps " + inUse + " in use");
    }

    // Renames an assessment not in use in Settings' form; the picker then
    // shows it by its new name, not the assessment in use.
    private void renameInSettings(String oldName, String newName) {
        window.showPage(Page.SETTINGS);
        settle();
        ComboBox<?> picker = settingsAssessmentPicker();
        if (picker == null) {
            problems.add("expected: Settings has an assessment picker");
            return;
        }
        setValue(picker, oldName);
        TextField nameField = null;
        for (TextField field : SceneFinder.ofType(root(), TextField.class)) {
            if (oldName.equals(field.getText())) {
                nameField = field;
            }
        }
        if (nameField == null) {
            problems.add("expected: Settings' form shows " + oldName);
            return;
        }
        nameField.setText(newName);
        press(SceneFinder.button(root(), "Save assessment"),
                "Save assessment");
        picker = settingsAssessmentPicker();
        Object shown = null;
        if (picker != null) {
            shown = picker.getValue();
        }
        check(newName.equals(shown), "after renaming " + oldName
                + ", Settings shows " + newName + " (it shows " + shown
                + ")");
    }

    // Returns Settings' assessment picker, or null.
    private ComboBox<?> settingsAssessmentPicker() {
        for (ComboBox<?> box : SceneFinder.ofType(root(), ComboBox.class)) {
            if ("Assessment to edit".equals(box.getAccessibleText())) {
                return box;
            }
        }
        return null;
    }

    // A section's own export folder wins over its module's, and the
    // others use the folder for everyone.
    private void checkExportFolders() {
        String key = ConfigKeys.OUTPUT_DIR_BY_SECTION;
        String before = backend.getSetting(key);
        java.util.Map<String, String> changes = new java.util.HashMap<>();
        changes.put(key, DemoData.MODULE_CODE + "/G1=workspace/g1;"
                + DemoData.MODULE_CODE + "=workspace/module");
        check(backend.saveSettings(changes).isDone(),
                "export folders for a module and a section can be saved");
        String everyone = backend.getSetting(ConfigKeys.OUTPUT_DIR);
        check(backend.getExportFolder(new gradezzy.model.Section(
                DemoData.MODULE_CODE, "G1")).equals("workspace/g1"),
                "G1 exports to its own folder");
        check(backend.getExportFolder(new gradezzy.model.Section(
                DemoData.MODULE_CODE, "G2")).equals("workspace/module"),
                "G2 exports to its module's folder");
        check(backend.getExportFolder(null).equals(everyone),
                "with no section, exports go to the folder for everyone");

        String sheetKey = ConfigKeys.SCORE_SHEET_BY_SECTION;
        String sheetsBefore = backend.getSetting(sheetKey);
        java.util.Map<String, String> sheets = new java.util.HashMap<>();
        sheets.put(sheetKey, DemoData.MODULE_CODE + "/G1=sheets/g1.csv");
        backend.saveSettings(sheets);
        check(backend.getScoreSheet(new gradezzy.model.Section(
                DemoData.MODULE_CODE, "G1")).equals("sheets/g1.csv"),
                "G1 uses its own score sheet");
        check(backend.getScoreSheet(new gradezzy.model.Section(
                DemoData.MODULE_CODE, "G2")).equals(backend.getSetting(
                ConfigKeys.SCORE_SHEET_FILE)), "G2, with none, uses the "
                + "score sheet for all");
        sheets.put(sheetKey, sheetsBefore);
        backend.saveSettings(sheets);
        changes.put(key, before);
        backend.saveSettings(changes);
        checkDeleteSection();
    }

    // Deleting a section asks first, removes it, and Undo puts it back.
    private void checkDeleteSection() {
        String module = DemoData.MODULE_CODE;
        String section = "G2";
        if (!backend.getSectionCodes(module).contains(section)) {
            return;
        }
        gradezzy.ui.flow.CourseSheets.askDeleteSection(window, module,
                section, () -> { });
        settle();
        Node confirm = sheet();
        check(confirm != null && SceneFinder.shows(confirm, "Delete section "
                + section + "?"), "deleting a section asks first");
        press(SceneFinder.button(confirm,
                "Delete section"), "Delete section");
        check(!backend.getSectionCodes(module).contains(section),
                section + " is deleted");
        boolean logged = false;
        for (gradezzy.model.AuditEntry entry : backend.listAuditEntries()) {
            logged = logged || gradezzy.ui.flow.ActivityUndo.SECTION_DELETED
                    .equals(entry.getAction());
        }
        check(logged, "the delete is in the activity log");
        window.showPage(Page.RUNS);
        settle();
        ((gradezzy.ui.flow.RunsScreen) window.getPageScreen(Page.RUNS))
                .showActivity();
        settle();
        press(SceneFinder.button(root(), "Undo"), "Undo in the activity log");
        check(backend.getSectionCodes(module).contains(section),
                "Undo in the activity log put " + section + " back");
        checkNewModule();
        check(gradezzy.ui.flow.ActivityUndo.find(
                gradezzy.ui.flow.ActivityUndo.SECTION_DELETED, section
                + " of every module") == null,
                "a delete is put back only once");
    }

    // ------------------------------------------------------------ flow 9

    // Help's Take the tour starts the tour on Home. Every stop after the
    // welcome lights up a part of the window. Done, Esc and Skip tour put
    // back the tab it started from, and shortcuts do not reach the
    // window behind the tour. Before any run is open, a step's stop
    // points at the row of steps instead.
    private void flowTour() {
        window.showPage(Page.STATISTICS);
        settle();
        window.showHelp();
        settle();
        press(SceneFinder.button(sheet(), "Take the tour"),
                "Take the tour");
        GuidedTour tour = window.getTour();
        check(tour != null && tour.isOpen(), "Take the tour opens the "
                + "tour");
        if (tour == null || !tour.isOpen()) {
            return;
        }
        check(!window.isSheetOpen(), "the Help sheet closes");
        check(window.getCurrentPage() == Page.HOME, "the tour starts on "
                + "Home");

        // The welcome lights up nothing; the last stop shows Done
        int stops = tour.getStopCount();
        int lastStop = stops - 1;
        checkTourCount(0, stops);
        press(tourButton("Next"), "Next");
        for (int stop = 1; stop < lastStop; stop++) {
            checkTourStop(tour, stop, stops);
            press(tourButton("Next"), "Next");
        }
        checkTourStop(tour, lastStop, stops);
        press(tourButton("Done"), "Done");
        check(!tour.isOpen(), "Done ends the tour");
        check(window.getCurrentPage() == Page.STATISTICS, "Done goes "
                + "back to Statistics");
        check(scene.lookup(".tour-card") == null, "the tour is gone after "
                + "Done");

        window.startTour();
        settle();
        press(tourButton("Next"), "Next");
        press(tourButton("Next"), "Next");
        press(tourButton("Back"), "Back");
        check(tour.getIndex() == 1, "Back goes to the stop before");
        pressKey(KeyCode.RIGHT, false);
        check(tour.getIndex() == SECOND_TOUR_STOP,
                "the right arrow goes on");
        pressKey(KeyCode.LEFT, false);
        check(tour.getIndex() == 1, "the left arrow goes back");
        pressKey(KeyCode.DIGIT4, true);
        check(tour.isOpen() && tour.getIndex() == 1
                && window.getCurrentPage() == Page.HOME, "a step "
                + "shortcut is held back while the tour is on screen");
        pressKey(KeyCode.ESCAPE, false);
        check(!tour.isOpen(), "Esc ends the tour");
        check(window.getCurrentPage() == Page.STATISTICS, "Esc goes back "
                + "to Statistics");

        window.startTour();
        settle();
        press(SceneFinder.link(scene.lookup(".tour-card"), "Skip tour"),
                "Skip tour");
        check(!tour.isOpen(), "Skip tour ends the tour");

        FlowWindow fresh = new FlowWindow(backend, false);
        Scene freshScene = fresh.build(WIDTH, HEIGHT);
        fresh.startTour();
        GuidedTour freshTour = fresh.getTour();
        while (freshTour.isOpen() && freshTour.getIndex() < REVIEW_STOP) {
            freshTour.next();
        }
        SceneFinder.settle(freshScene);
        Node lit = freshTour.getTarget();
        check(lit != null && lit.getStyleClass().contains("step-item"),
                "before any run is open, the Review stop lights up the "
                + "step in the row of steps");
        check(SceneFinder.text(freshScene.getRoot(), "tour-text")
                .contains("This step opens"), "the card says when the "
                + "step opens");
        freshTour.end();
    }

    // Checks that the card counts a stop, such as "4 of 15".
    private void checkTourCount(int stop, int stops) {
        String count = (stop + 1) + " of " + stops;
        check(count.equals(SceneFinder.text(root(), "tour-count")),
                "the card says " + count);
    }

    // Checks the count of a stop after the welcome, and that it lights
    // up a part of the window.
    private void checkTourStop(GuidedTour tour, int stop, int stops) {
        checkTourCount(stop, stops);
        check(tour.getTarget() != null, "stop " + (stop + 1)
                + " lights up a part of the window");
    }

    // Returns a button of the tour's card, or null.
    private Button tourButton(String text) {
        Node card = scene.lookup(".tour-card");
        if (card == null) {
            return null;
        }
        return SceneFinder.button(card, text);
    }

    // Presses a key, with Cmd or Ctrl when asked, as the keyboard would.
    private void pressKey(KeyCode code, boolean shortcut) {
        KeyEvent event = new KeyEvent(KeyEvent.KEY_PRESSED, "", "", code,
                false, shortcut, false, shortcut);
        Event.fireEvent(root(), event);
        settle();
    }

    // Statistics lists the demo's Q3 group under Common mistakes, and
    // hides the section when comparing sections.
    private void checkCommonMistakes() {
        check(SceneFinder.shows(root(), "Common mistakes"),
                "Statistics shows Common mistakes");
        String title = "Failed test 3: " + DemoData.caseName("Q3", 3);
        Node row = rowOf(title, "plain-row");
        check(row != null && SceneFinder.label(row, "Q3") != null
                && SceneFinder.label(row, "3") != null,
                "Common mistakes lists Q3, failed test 3, 3 students");
        StatisticsScreen statistics =
                (StatisticsScreen) window.getPageScreen(Page.STATISTICS);
        statistics.setCompare(true);
        settle();
        check(!SceneFinder.shows(root(), "Common mistakes"),
                "Compare sections hides Common mistakes");
        statistics.setCompare(false);
        settle();
    }

    // ----------------------------------------------------------- flow 10

    // The demo's Q3 group (learner.charlie and learner.echo failed tests
    // 3 and 4): name it, change both marks and undo, a student who moves
    // out of the group while the sheet is open, Undo after a later change
    // by hand, Mark as checked (with a compile error), and Compare code.
    private void flowAnswerGroups() {
        GradingRun run = window.getState().getRun();
        if (run == null) {
            problems.add("expected: a run is open");
            return;
        }
        // The section chosen now is not the run's, so Review stays
        // locked rather than show that run under this section's name
        boolean otherSection = !run.getSection().getLabel().equals(
                window.getState().getSection().getLabel());
        window.showPage(Page.MARKING);
        window.show(Step.REVIEW);
        settle();
        check(!otherSection || window.getCurrentStep() != Step.REVIEW,
                "Review of a section not graded yet stays locked");
        window.openRun(run);
        settle();
        ReviewScreen review = (ReviewScreen) window.getScreen(Step.REVIEW);
        review.showMistakes();
        settle();
        Submission charlie = submissionOf(run, "learner.charlie");
        Submission echo = submissionOf(run, "learner.echo");
        Node row = errorRow(Q3_TEST_3);
        check(row != null && SceneFinder.shows(row, Students.name(charlie))
                && SceneFinder.shows(row, Students.name(echo)),
                "Errors lists the Q3 test 3 error of learner.charlie and "
                + "learner.echo");
        if (row == null) {
            return;
        }

        press((Button) row.lookup(".rename-group"), "the pencil to name");
        ((TextField) sheet().lookup("#group-name")).setText(
                "misses the last case");
        press(SceneFinder.button(sheet(), "Save name"), "Save name");
        row = errorRow(Q3_TEST_3);
        check(row != null && SceneFinder.shows(row, "misses the last case"),
                "the group shows its name");

        changeGroupMark("3");
        check(q3(charlie) == Q3_CHANGED && q3(echo) == Q3_CHANGED,
                "both Q3 marks are 3 ("
                + q3(charlie) + ", " + q3(echo) + ")");
        check(noticeText().equals("Changed the mark of 3 students."),
                "notice: " + noticeText());
        press(noticeAction(), "Undo in the notice");
        check(q3(charlie) == Q3_MARK && q3(echo) == Q3_MARK,
                "Undo put both Q3 marks back to 2");

        // groupActionSkipsMovedStudent
        QuestionResult echoQ3 = resultOf(echo, "Q3");
        List<TestCaseResult> echoTests = new ArrayList<>(
                echoQ3.getTestCaseResults());
        press(SceneFinder.button(errorRow(Q3_TEST_3),
                "Change mark"), "Change mark");
        fillMarkSheet("3");
        echoQ3.getTestCaseResults().clear();
        for (int number = 1; number <= Q3_TESTS; number++) {
            echoQ3.addTestCaseResult(new TestCaseResult(number, true,
                    "Passed"));
        }
        echoQ3.setScore(Q3_FULL);
        press(SceneFinder.button(sheet(), "Change 3 marks"),
                "Change 3 marks");
        check(q3(charlie) == Q3_CHANGED && q3(echo) == Q3_FULL,
                "only the student "
                + "still in the group changed (" + q3(charlie) + ", "
                + q3(echo) + ")");
        press(noticeAction(), "Undo in the notice");
        echoQ3.getTestCaseResults().clear();
        for (TestCaseResult test : echoTests) {
            echoQ3.addTestCaseResult(test);
        }
        echoQ3.setScore(Q3_MARK);
        window.refreshScreen();
        settle();
        check(q3(charlie) == Q3_MARK && q3(echo) == Q3_MARK,
                "marks are back to 2");

        // undoSkipsLaterChange
        changeGroupMark("3");
        backend.adjustScore(run, charlie, "Q3", Q3_BY_HAND, "by hand");
        press(noticeAction(), "Undo in the notice");
        check(q3(charlie) == Q3_BY_HAND && q3(echo) == Q3_MARK,
                "Undo left the later "
                + "change (" + q3(charlie) + ", " + q3(echo) + ")");
        check(noticeText().equals("Put back 2 marks. 1 was changed again "
                + "since and was left."), "notice: " + noticeText());
        undoAllChanges(run, charlie);
        check(q3(charlie) == Q3_MARK, "charlie's Q3 is back to 2");

        // Mark as checked while a student joins the group: the newcomer
        // was not listed, so the group shows 1 new, not Checked
        window.refreshScreen();
        settle();
        Submission alpha = submissionOf(run, "learner.alpha");
        QuestionResult alphaQ3 = resultOf(alpha, "Q3");
        List<TestCaseResult> alphaTests = new ArrayList<>(
                alphaQ3.getTestCaseResults());
        press(SceneFinder.button(errorRow(Q3_TEST_3),
                "Mark as checked"), "Mark as checked");
        alphaQ3.getTestCaseResults().clear();
        for (int number = 1; number <= Q3_TESTS; number++) {
            boolean passed = number <= Q3_JOINER_PASSES;
            alphaQ3.addTestCaseResult(new TestCaseResult(number, passed,
                    "case " + number));
        }
        press(SceneFinder.button(sheet(), "Mark 3 as checked"),
                "Mark 3 as checked");
        row = errorRow(Q3_TEST_3);
        check(row != null && SceneFinder.texts(row, "pill").contains("1 new"),
                "a student who joined before Confirm shows as 1 new (pills: "
                + (row == null ? "none"
                        : SceneFinder.texts(row, "pill")) + ")");
        alphaQ3.getTestCaseResults().clear();
        for (TestCaseResult test : alphaTests) {
            alphaQ3.addTestCaseResult(test);
        }
        window.refreshScreen();
        settle();
        row = errorRow(Q3_TEST_3);
        check(row != null && SceneFinder.texts(row, "pill").contains(
                "Checked"), "the group shows Checked");
        Anomaly compileError = null;
        for (Anomaly anomaly : charlie.getAnomalies()) {
            if (anomaly.getType() == AnomalyType.COMPILE_ERROR) {
                compileError = anomaly;
            }
        }
        Node compileRow = compileRowOf(charlie);
        check(compileRow != null && compileError != null,
                "a Did not compile group lists learner.charlie");
        if (compileRow != null && compileError != null) {
            press(SceneFinder.button(compileRow, "Mark as checked"),
                    "Mark as checked");
            check(sheet() != null && SceneFinder.shows(sheet(),
                    "items will show as checked"), "the confirm says the "
                    + "question's items will show as checked");
            press(SceneFinder.button(sheet(), "Mark 1 as checked"),
                    "Mark 1 as checked");
            check(backend.getDecision(compileError) == ReviewDecision.REVIEWED,
                    "the compile error is checked inside the group");
            press(noticeAction(), "Undo in the notice");
            check(backend.getDecision(compileError) == ReviewDecision.OPEN,
                    "Undo put the compile error back to check");
            // Undo check on the row puts back the flags too
            press(SceneFinder.button(compileRowOf(charlie), "Mark as checked"),
                    "Mark as checked");
            press(SceneFinder.button(sheet(), "Mark 1 as checked"),
                    "Mark 1 as checked");
            press(SceneFinder.button(compileRowOf(charlie), "Undo check"),
                    "Undo check");
            check(backend.getDecision(compileError) == ReviewDecision.OPEN,
                    "Undo check on the row put the compile error back to "
                    + "check");
        }

        // Open code steps through the error's students
        press(SceneFinder.button(errorRow(Q3_TEST_3),
                "Open code"), "Open code");
        check(sheet() != null && SceneFinder.shows(sheet(), "Student 1 of 3"),
                "the code sheet says Student 1 of 3");
        press(SceneFinder.button(sheet(), "Next"), "Next");
        // learner.delta failed every Q3 test, so it is in the test 3
        // error too
        Submission delta = submissionOf(run, "learner.delta");
        check(sheet() != null && SceneFinder.shows(sheet(), "Student 2 of 3")
                && SceneFinder.shows(sheet(), Students.name(delta)),
                "Next shows learner.delta");
        window.closeSheet();
        review.showStudents();
        settle();
    }

    // ----------------------------------------------------------- flow 11

    // Two sections at once: start the sample section, choose another
    // and start it too while the first grades, see both on Home, cancel
    // only the second on its Grade step, then finish both.
    private void flowParallel() {
        Section first = new Section(DemoData.MODULE_CODE,
                DemoData.SAMPLE_SECTION);
        Section second = new Section(DemoData.MODULE_CODE,
                PARALLEL_SECTION);
        startSectionBatch(first);
        check(window.getState().isGrading(first),
                "the first section is grading");
        startSectionBatch(second);
        check(window.getState().isGrading(first)
                && window.getState().isGrading(second),
                "both sections grade at once");
        check(window.getState().getRunningJobs().size()
                == PARALLEL_BATCHES,
                "two batches are running");

        window.showPage(Page.HOME);
        settle();
        check(SceneFinder.shows(root(), first.getLabel() + " and "
                + second.getLabel() + " are being graded now"),
                "Home says both sections are being graded");

        window.watchGrading(second);
        settle();
        check(SceneFinder.shows(root(), second.getLabel()),
                "Grade shows the second section");
        press(SceneFinder.button(window.getActionBar(), STOP_GRADING),
                STOP_GRADING);
        Node cancelSheet = sheet();
        check(cancelSheet != null && SceneFinder.shows(cancelSheet,
                "Other sections keep grading"), "the Stop sheet says "
                + "other sections keep grading");
        if (cancelSheet != null) {
            press(SceneFinder.button(cancelSheet, STOP_GRADING),
                    STOP_GRADING);
        }
        backend.finishGrading();
        settle();

        check(!window.getState().isAnyGrading(), "nothing grades any more");
        GradingRun firstRun = backend.getRun(first);
        GradingRun secondRun = backend.getRun(second);
        check(firstRun != null && count(firstRun, SubmissionStatus.GRADED)
                == firstRun.getSubmissions().size(),
                "the first section was graded in full");
        check(secondRun != null
                && count(secondRun, SubmissionStatus.CANCELLED) > 0,
                "only the second section was cancelled");
    }

    // ----------------------------------------------------------- flow 12

    // The line above the steps names the section on every step, Home's
    // cards open the step a section needs, Set up offers to continue an
    // unfinished run, warns when the section was graded with another
    // assessment, and will not grade a module with no assessment.
    private void flowGradingContext() {
        checkContextLine();
        checkCardsOpenTheirStep();
        checkContinueCard();
        checkOtherAssessmentWarning();
        checkModuleWithoutAssessment();
    }

    // Opens the sample section's run and checks that each of the four
    // steps names its module, section and assessment above the steps.
    private void checkContextLine() {
        Section sample = new Section(DemoData.MODULE_CODE,
                DemoData.SAMPLE_SECTION);
        GradingRun run = backend.getRun(sample);
        if (run == null) {
            problems.add("expected: the sample section has a run");
            return;
        }
        window.openRun(run);
        window.getState().reach(Step.EXPORT);
        settle();
        String names = DemoData.MODULE_CODE + CONTEXT_DOT
                + DemoData.SAMPLE_SECTION + CONTEXT_DOT
                + run.getAssessment().getName();
        for (Step step : Step.values()) {
            window.show(step);
            settle();
            check(contextNames().equals(names), step.getLabel()
                    + " names \"" + names + "\" above the steps (it says \""
                    + contextNames() + "\")");
            check(SceneFinder.link(root(), "Change section") != null,
                    step.getLabel() + " offers Change section");
        }
        window.openSectionPicker();
        settle();
        check(sheet() != null && SceneFinder.shows(sheet(), "Opens on "),
                "Change section lists the sections with the step each "
                + "opens on");
        window.closeSheet();
    }

    // Clicking the card of a section with students to check opens
    // Review, and the card of a section not graded opens Set up.
    private void checkCardsOpenTheirStep() {
        String module = DemoData.MODULE_CODE;
        Section sample = new Section(module, DemoData.SAMPLE_SECTION);
        GradingRun run = backend.getRun(sample);
        boolean toCheck = run != null
                && SectionProgress.countToCheck(run, backend) > 0
                && SectionProgress.countWaiting(run) == 0;
        check(toCheck, "the sample section has students to check");
        window.showPage(Page.HOME);
        settle();
        Node graded = cardOf(DemoData.SAMPLE_SECTION);
        if (toCheck && graded != null) {
            SceneFinder.click(graded);
            settle();
            check(window.getCurrentPage() == Page.MARKING
                    && window.getCurrentStep() == Step.REVIEW,
                    "the card of a section with students to check opens "
                    + "Review (it opened " + window.getCurrentStep() + ")");
        }
        String notGraded = findSectionNotGraded(module);
        if (notGraded == null) {
            problems.add("expected: a section of " + module
                    + " is not graded");
            return;
        }
        window.showPage(Page.HOME);
        settle();
        Node empty = cardOf(notGraded);
        if (empty == null) {
            problems.add("expected: Home has a card for " + notGraded);
            return;
        }
        SceneFinder.click(empty);
        settle();
        check(window.getCurrentStep() == Step.SET_UP
                && notGraded.equals(window.getState().getSectionCode()),
                "the card of a section not graded opens Set up on it");
        check(contextStatus().contains("not graded yet"), "the line above "
                + "the steps says " + notGraded + " is not graded yet (it "
                + "says \"" + contextStatus() + "\")");
    }

    // On Set up of another section, the Continue card lists the stopped
    // run of the second section of flow 11, and Continue opens it on
    // Grade.
    private void checkContinueCard() {
        Section stopped = new Section(DemoData.MODULE_CODE,
                PARALLEL_SECTION);
        GradingRun run = backend.getRun(stopped);
        if (run == null) {
            problems.add("expected: " + stopped.getLabel() + " has a run");
            return;
        }
        window.openSetUp(DemoData.MODULE_CODE, DemoData.SAMPLE_SECTION);
        settle();
        check(SceneFinder.shows(root(), "Continue where you left off"),
                "Set up shows the Continue card");
        String names = DemoData.MODULE_CODE + CONTEXT_DOT + PARALLEL_SECTION
                + CONTEXT_DOT + run.getAssessment().getName();
        Node line = continueLineOf(names);
        check(line != null && SceneFinder.shows(line, "Grade")
                && SceneFinder.shows(line, "not graded"), "the Continue "
                + "card lists " + names + " on Grade with the students not "
                + "graded");
        check(continueLineOf(DemoData.MODULE_CODE + CONTEXT_DOT
                + DemoData.SAMPLE_SECTION + CONTEXT_DOT) == null,
                "the Continue card leaves out the section on screen");
        if (line == null) {
            return;
        }
        press(SceneFinder.button(line, "Continue"), "Continue");
        check(window.getCurrentStep() == Step.GRADE
                && PARALLEL_SECTION.equals(
                        window.getState().getSectionCode()),
                "Continue opens " + stopped.getLabel() + " on Grade");
        check(contextNames().equals(names), "Grade names " + names
                + " above the steps");
    }

    // With another assessment chosen, Set up of the sample section warns
    // that it was last graded with the sample one, and Use puts it back.
    private void checkOtherAssessmentWarning() {
        String sampleName = DemoData.ASSESSMENT_NAME;
        check(window.useAssessment(SECOND_ASSESSMENT), "switching to "
                + SECOND_ASSESSMENT + " works");
        window.openSetUp(DemoData.MODULE_CODE, DemoData.SAMPLE_SECTION);
        settle();
        String warning = "was last graded with " + sampleName + ", but "
                + SECOND_ASSESSMENT + " is chosen now";
        check(SceneFinder.shows(root(), warning), "Set up warns that the "
                + "section " + warning);
        check(!statusText().contains("assessment"), "grading with the "
                + "chosen assessment stays allowed (the action bar says \""
                + statusText() + "\")");
        press(SceneFinder.link(root(), "Use " + sampleName), "Use "
                + sampleName);
        check(backend.getAssessment().getName().equals(sampleName),
                "Use " + sampleName + " puts it in use");
        check(!SceneFinder.shows(root(), warning), "the warning goes once "
                + "the assessments match");
    }

    // Set up of a module with no assessment says so, offers to add one
    // and keeps Start grading disabled with that reason; Home says the
    // same. The empty sample module is given only its own sections and
    // assessments for this check, so the assessment flow 8 saved for
    // every module is not its.
    private void checkModuleWithoutAssessment() {
        String module = DemoData.EMPTY_MODULE_CODE;
        String moduleBefore = window.getState().getModuleCode();
        String sectionBefore = window.getState().getSectionCode();
        String ownKey = ConfigKeys.OWN_SECTIONS_ONLY_MODULES;
        String ownBefore = backend.getSetting(ownKey);
        java.util.Map<String, String> own = new java.util.HashMap<>();
        own.put(ownKey, ownBefore + "," + module);
        backend.saveSettings(own);
        window.openSetUp(module, DemoData.EMPTY_MODULE_SECTION);
        settle();
        SetUpScreen setUp = (SetUpScreen) window.getScreen(Step.SET_UP);
        setUp.addFiles(backend.getSampleFiles(InputKind.SUBMISSION));
        settle();
        String none = module + " has no assessment yet.";
        check(SceneFinder.shows(root(), none), "Set up says \"" + none
                + "\"");
        check(SceneFinder.link(root(), "Add an assessment") != null,
                "Set up offers Add an assessment");
        Button start = window.getActionBar().getPrimaryButton();
        check(start.isDisabled() && statusText().contains(none),
                "Start grading is disabled, saying why (it says \""
                + statusText() + "\")");
        check(contextStatus().contains("no assessment yet"), "the line "
                + "above the steps says there is no assessment");
        window.showPage(Page.HOME);
        settle();
        check(SceneFinder.shows(root(), none), "Home also says \"" + none
                + "\"");
        window.getState().getBatch().clear(InputKind.SUBMISSION);
        own.put(ownKey, ownBefore);
        backend.saveSettings(own);
        window.openSetUp(moduleBefore, sectionBefore);
        settle();
    }

    // Returns the words that name the module, section and assessment
    // above the steps.
    private String contextNames() {
        return SceneFinder.text(root(), "grading-context-names");
    }

    // Returns how far the section on screen has got, as the line above
    // the steps says.
    private String contextStatus() {
        return SceneFinder.text(root(), "grading-context-status");
    }

    // Returns Home's card of a section, or null.
    private Node cardOf(String section) {
        for (Label label : SceneFinder.ofType(root(), Label.class)) {
            boolean named = label.getStyleClass().contains("section-name")
                    && section.equals(label.getText());
            if (named) {
                return SceneFinder.ancestor(label, "section-card");
            }
        }
        return null;
    }

    // Returns a section of a module with no run of the assessment in
    // use, or null.
    private String findSectionNotGraded(String module) {
        java.util.Map<String, GradingRun> runs = SectionProgress.findRuns(
                backend.listRuns(), module, backend.getAssessment()
                        .getName());
        for (String code : backend.getSectionCodes(module)) {
            boolean free = !runs.containsKey(code)
                    && !window.getState().isGrading(new Section(module,
                            code));
            if (free) {
                return code;
            }
        }
        return null;
    }

    // Returns the line of the Continue card that starts with some names,
    // or null.
    private Node continueLineOf(String names) {
        for (javafx.scene.layout.HBox line : SceneFinder.ofType(root(),
                javafx.scene.layout.HBox.class)) {
            boolean match = line.getStyleClass().contains("continue-line")
                    && line.isVisible() && SceneFinder.ofType(line,
                    Label.class).get(0).getText().startsWith(names);
            if (match) {
                return line;
            }
        }
        return null;
    }

    // Chooses a section, adds the sample zip files on Set up, leaves out
    // the one that cannot be used and starts grading it.
    private void startSectionBatch(Section section) {
        window.chooseSection(section.getModuleCode(),
                section.getSectionCode());
        window.showPage(Page.MARKING);
        window.show(Step.SET_UP);
        settle();
        SetUpScreen setUp = (SetUpScreen) window.getScreen(Step.SET_UP);
        setUp.addFiles(backend.getSampleFiles(InputKind.SUBMISSION));
        settle();
        ImportBatch batch = window.getState().getBatch();
        for (ImportItem item : batch.getItems(InputKind.SUBMISSION)) {
            if (batch.getEffectiveState(item) == ImportState.INVALID) {
                batch.remove(item);
            }
        }
        window.startGrading();
        settle();
    }

    // Returns the Did not compile error row that lists a student, opening
    // each such row to see its students.
    private Node compileRowOf(Submission submission) {
        List<String> keys = new ArrayList<>();
        for (VBox box : SceneFinder.ofType(root(), VBox.class)) {
            boolean compileRow = box.getStyleClass().contains("error-row")
                    && String.valueOf(box.getUserData()).endsWith(
                    COMPILE_PATTERN);
            if (compileRow) {
                keys.add((String) box.getUserData());
            }
        }
        for (String key : keys) {
            Node row = errorRow(key);
            if (row != null && SceneFinder.shows(row,
                    Students.name(submission))) {
                return row;
            }
        }
        return null;
    }

    // Returns the row of an error on the Errors view, opened so its
    // students show, or null when the view has no such error.
    private Node errorRow(String key) {
        Node row = findErrorRow(key);
        if (row != null && !row.getStyleClass().contains("open")) {
            SceneFinder.click(row.lookup(".error-header"));
            settle();
            row = findErrorRow(key);
        }
        return row;
    }

    // Returns the row of an error by its key, or null.
    private Node findErrorRow(String key) {
        for (VBox box : SceneFinder.ofType(root(), VBox.class)) {
            boolean match = box.getStyleClass().contains("error-row")
                    && key.equals(box.getUserData());
            if (match) {
                return box;
            }
        }
        return null;
    }

    // Opens the Q3 group's mark sheet and changes both marks.
    private void changeGroupMark(String mark) {
        press(SceneFinder.button(errorRow(Q3_TEST_3),
                "Change mark"), "Change mark");
        fillMarkSheet(mark);
        press(SceneFinder.button(sheet(), "Change 3 marks"),
                "Change 3 marks");
    }

    // Types a mark and a reason into the open mark sheet.
    private void fillMarkSheet(String mark) {
        Node open = sheet();
        check(open != null, "the mark sheet is open");
        if (open == null) {
            return;
        }
        ((TextField) open.lookup("#group-mark")).setText(mark);
        ((TextField) open.lookup("#group-reason")).setText("same slip");
    }

    // Undoes every mark change of a student, newest first.
    private void undoAllChanges(GradingRun run, Submission submission) {
        List<ScoreAdjustment> changes = backend.listAdjustments(run,
                submission);
        while (!changes.isEmpty()) {
            ScoreAdjustment newest = changes.get(0);
            for (ScoreAdjustment change : changes) {
                if (change.getSavedAt().isAfter(newest.getSavedAt())) {
                    newest = change;
                }
            }
            if (!backend.undoAdjustment(run, newest).isDone()) {
                problems.add("could not undo " + newest.getReason());
                return;
            }
            changes = backend.listAdjustments(run, submission);
        }
    }

    // Returns a student of a run by username.
    private Submission submissionOf(GradingRun run, String username) {
        for (Submission submission : run.getSubmissions()) {
            if (submission.getUsername().equals(username)) {
                return submission;
            }
        }
        throw new IllegalStateException("no " + username);
    }

    // Returns a student's result for a question.
    private QuestionResult resultOf(Submission submission, String id) {
        for (QuestionResult result : submission.getQuestionResults()) {
            if (result.getQuestion().getId().equals(id)) {
                return result;
            }
        }
        throw new IllegalStateException("no " + id);
    }

    // Returns a student's Q3 mark.
    private double q3(Submission submission) {
        return resultOf(submission, "Q3").getScore();
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

    // Returns the button of the notice, such as Undo, or null when the
    // notice is hidden or has none.
    private ButtonBase noticeAction() {
        Node notice = scene.lookup(".notice");
        if (notice instanceof Label && notice.isVisible()
                && ((Label) notice).getGraphic() instanceof ButtonBase) {
            return (ButtonBase) ((Label) notice).getGraphic();
        }
        return null;
    }

    // Returns the words of the notice shown now, or "".
    private String noticeText() {
        Node notice = scene.lookup(".notice");
        if (notice instanceof Label && notice.isVisible()) {
            return ((Label) notice).getText();
        }
        return "";
    }

    // Returns the student panel, or null.
    private StudentPanel studentPanel() {
        List<StudentPanel> panels = SceneFinder.ofType(root(),
                StudentPanel.class);
        if (panels.isEmpty()) {
            return null;
        }
        return panels.get(0);
    }

    // Returns the line that says who is being graded now.
    private String nowText() {
        return SceneFinder.text(root(), "grade-now") + " "
                + SceneFinder.text(root(), "grade-now-name") + " "
                + SceneFinder.text(root(), "grade-now-stage");
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
        // A made fix's card says what the fix did, else what was wrong
        Node card = null;
        if (!anomaly.describeFix().isEmpty()) {
            card = rowOf(anomaly.describeFix(), "fix-card");
        }
        if (card == null) {
            card = rowOf(anomaly.getMessage(), "fix-card");
        }
        return card;
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
        Button save = SceneFinder.button(sheet, "Save");
        if (save != null && save.isDisabled()) {
            return "";
        }
        press(save, "Save");
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
                return figureValue((Parent) tile);
            }
        }
        return "";
    }

    // Joins the parts of a figure tile's value, since a percent sign is
    // a label of its own.
    private String figureValue(Parent tile) {
        StringBuilder value = new StringBuilder();
        for (Label part : SceneFinder.ofType(tile, Label.class)) {
            if (part.getStyleClass().contains("figure-value")) {
                value.append(part.getText());
            }
        }
        return value.toString();
    }

    // A module added on Home starts with no sections and no assessments,
    // and Settings lists it.
    private void checkNewModule() {
        String newModule = "IS442";
        String modulesBefore = backend.getSetting(ConfigKeys.MODULE_CODES);
        String ownBefore = backend.getSetting(
                ConfigKeys.OWN_SECTIONS_ONLY_MODULES);
        String moduleBefore = window.getState().getModuleCode();
        gradezzy.ui.flow.CourseSheets.openAddModule(window,
                code -> window.getState().setModuleCode(code));
        settle();
        Node sheet = sheet();
        List<TextField> fields = SceneFinder.ofType(sheet, TextField.class);
        if (fields.isEmpty()) {
            problems.add("expected: the Add module sheet has a field");
            return;
        }
        fields.get(0).setText(newModule);
        press(SceneFinder.button(sheet, "Add module"), "Add module");
        check(backend.getModuleCodes().contains(newModule),
                newModule + " is added");
        check(backend.getSectionCodes(newModule).isEmpty(),
                "a new module has no sections (it has "
                + backend.getSectionCodes(newModule) + ")");
        boolean anyAssessment = false;
        for (SavedAssessment saved : backend.listAssessments()) {
            anyAssessment = anyAssessment || saved.belongsTo(newModule, true);
        }
        check(!anyAssessment, "a new module has no assessments");
        window.showPage(Page.SETTINGS);
        settle();
        boolean listed = false;
        for (ComboBox<?> box : SceneFinder.ofType(root(), ComboBox.class)) {
            listed = listed || box.getItems().contains(newModule);
        }
        check(listed, "Settings lists the module added on Home");
        checkSettingsScope(newModule);
        checkSettingsModule(newModule);
        checkModuleSwitchAsks(newModule, moduleBefore);

        java.util.Map<String, String> back = new java.util.HashMap<>();
        back.put(ConfigKeys.MODULE_CODES, modulesBefore);
        back.put(ConfigKeys.OWN_SECTIONS_ONLY_MODULES, ownBefore);
        backend.saveSettings(back);
        window.getState().setModuleCode(moduleBefore);
        window.showPage(Page.SETTINGS);
        settle();
        checkBandFollows(moduleBefore);
        window.showPage(Page.HOME);
        settle();
    }

    // Switching back to the module chosen before puts its name in the
    // title of Export folders.
    private void checkBandFollows(String module) {
        String title = "Export folders \u00B7 " + module;
        List<String> titles = SceneFinder.texts(root(), "card-title");
        check(titles.contains(title), "switching module puts " + module
                + " in the title of Export folders (it shows " + titles
                + ")");
    }

    // Settings has no bar above its cards and no band headings, and the
    // groups that change with the module or the assessment say so under
    // their title.
    private void checkSettingsScope(String module) {
        check(scene.getRoot().lookup(".settings-scope-bar") == null,
                "Settings has no bar above its cards");
        check(scene.getRoot().lookup(".settings-band-head") == null,
                "Settings shows no band headings");
        int changing = scene.getRoot().lookupAll(".scope-card").size();
        check(changing == CHANGING_GROUPS, "Course, Export folders, "
                + "Assessment and Score sheets are marked as changing ("
                + changing + " marked)");
        check(SceneFinder.shows(root(), "Where " + module + " and its "
                + "sections save their exports"), "Export folders names "
                + module);
        check(!SceneFinder.shows(root(), "Saved with"), "Settings has no "
                + "Saved with tag");
        check(!SceneFinder.shows(root(), "Section files"), "Settings has "
                + "no Section files group");
    }

    // Picking a module in Settings shows its parts there only: it asks
    // nothing, and the module and assessment in use stay as they were.
    private void checkModuleSwitchAsks(String module, String moduleBefore) {
        String inUseBefore = backend.getAssessment().getName();
        SavedAssessment own = new SavedAssessment();
        own.setName(OTHER_MODULE_ASSESSMENT);
        own.setModuleCode(module);
        own.getQuestions().add(new Question("Q1", "Q1", "Q1.java",
                QUESTION_MAX));
        own.setTestersDirectory("testers");
        own.setStarterDirectory("starter");
        ActionResult saved = backend.saveAssessment(own, null);
        check(saved.isDone(), "an assessment of " + module + " is saved ("
                + saved.getMessage() + ")");
        window.getState().setModuleCode(moduleBefore);
        window.showPage(Page.SETTINGS);
        settle();

        pickSettingsModule(module);
        check(sheet() == null, "picking " + module + " in Settings asks "
                + "nothing");
        check(module.equals(settingsModuleValue()), "the Course dropdown "
                + "shows " + module + " (it shows " + settingsModuleValue()
                + ")");
        check(moduleBefore.equals(window.getState().getModuleCode())
                && inUseBefore.equals(backend.getAssessment().getName()),
                "picking " + module + " in Settings leaves the module on "
                + "Home and the assessment in use");

        // Coming back to Settings keeps the module picked there
        window.showPage(Page.HOME);
        settle();
        window.showPage(Page.SETTINGS);
        settle();
        check(module.equals(settingsModuleValue()), "Settings keeps "
                + module + " when shown again (it shows "
                + settingsModuleValue() + ")");

        backend.deleteAssessment(OTHER_MODULE_ASSESSMENT);
        settle();
    }

    // Picks a module in the Course dropdown of Settings.
    private void pickSettingsModule(String module) {
        ComboBox<?> box = settingsModuleBox();
        if (box == null) {
            problems.add("expected: Settings has a module dropdown");
            return;
        }
        setValue(box, module);
    }

    // Returns the module shown in the Course dropdown of Settings, or "".
    private String settingsModuleValue() {
        ComboBox<?> box = settingsModuleBox();
        if (box == null || box.getValue() == null) {
            return "";
        }
        return box.getValue().toString();
    }

    // Returns the Course dropdown of Settings, or null.
    private ComboBox<?> settingsModuleBox() {
        for (ComboBox<?> box : SceneFinder.ofType(root(), ComboBox.class)) {
            if ("Modules".equals(box.getAccessibleText())) {
                return box;
            }
        }
        return null;
    }

    // Settings follows the module chosen on Home: a module with no
    // assessment and no section shows notes, not another module's
    // assessment and score sheets. A new assessment starts blank, and
    // saving it blank says what is missing.
    private void checkSettingsModule(String module) {
        check(SceneFinder.shows(root(), "No assessment for " + module
                + " yet"), "Settings shows that " + module + " has no "
                + "assessment yet, not another module's");
        check(SceneFinder.shows(root(), "Add a section to " + module),
                "Settings lists only " + module + "'s score sheets");
        Button create = SceneFinder.button(root(), "New assessment");
        if (create == null) {
            problems.add("expected: Settings offers New assessment");
            return;
        }
        press(create, "New assessment");
        Node form = scene.getRoot().lookup(".assessment-editor");
        if (form == null) {
            problems.add("expected: New assessment opens the form");
            return;
        }
        boolean blank = true;
        for (TextField field : SceneFinder.ofType(form, TextField.class)) {
            blank = blank && field.getText().isEmpty();
        }
        check(blank, "a new assessment starts with every field blank");
        press(SceneFinder.button(form, "Save assessment"),
                "Save assessment");
        check(SceneFinder.shows(form, "Give the assessment a name"),
                "saving a blank assessment says what is missing");
    }

    // Says whether a dropdown on screen offers to add a section.
    private boolean offersNewSection() {
        for (ComboBox<?> box : SceneFinder.ofType(root(), ComboBox.class)) {
            if (box.getItems().contains("+ New section...")) {
                return true;
            }
        }
        return false;
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

    // The Model answer checks group of Settings is listed by that name,
    // and its card names the assessment in use, whose model answers
    // folder the checks use.
    private void checkModelAnswerChecks() {
        List<String> listed = SceneFinder.texts(root(),
                "settings-index-item");
        check(listed.contains("Model answer checks"), "Settings lists "
                + "Model answer checks (it lists " + listed + ")");
        String name = backend.getAssessment().getName();
        boolean named = SceneFinder.shows(root(), "Uses " + name
                + "'s model answers")
                || SceneFinder.shows(root(), name + " has no model "
                + "answers folder");
        check(named, "Model answer checks names " + name + ", the "
                + "assessment in use");
        List<String> titles = SceneFinder.texts(root(), "card-title");
        check(titles.contains("Score sheets \u00B7 " + name), "Score "
                + "sheets names " + name + ", the assessment in use (titles "
                + titles + ")");
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
