/*
 * File: StudentPanel.java
 *
 * Purpose: The panel beside the Review table for one student: the
 * total, each question's mark with its tests, the fixes made for the
 * student with Approve and Undo, the flags and problems found, the
 * marks changed by hand, and the actions that grade the student again.
 *
 * Where it fits: Replaces the old Student Evidence screen and anomaly
 * inspector. ReviewScreen shows it when a row is clicked and is told
 * through a Runnable when something changes a mark or a status.
 */
package gradezzy.ui.flow;

import java.util.ArrayList;
import java.util.List;
import java.util.regex.Pattern;

import javafx.event.ActionEvent;
import javafx.event.EventHandler;
import javafx.geometry.Insets;
import javafx.geometry.Pos;
import javafx.scene.Node;
import javafx.scene.Parent;
import javafx.scene.control.Button;
import javafx.scene.control.Hyperlink;
import javafx.scene.control.Label;
import javafx.scene.control.MenuButton;
import javafx.scene.control.MenuItem;
import javafx.scene.control.ScrollPane;
import javafx.scene.control.Tooltip;
import javafx.scene.input.MouseEvent;
import javafx.scene.layout.FlowPane;
import javafx.scene.layout.HBox;
import javafx.scene.layout.Priority;
import javafx.scene.layout.Region;
import javafx.scene.layout.StackPane;
import javafx.scene.layout.VBox;

import gradezzy.model.Anomaly;
import gradezzy.model.AnomalyType;
import gradezzy.model.Assessment;
import gradezzy.model.FixStatus;
import gradezzy.model.GradingRun;
import gradezzy.model.Question;
import gradezzy.model.QuestionResult;
import gradezzy.model.ScoreAdjustment;
import gradezzy.model.Student;
import gradezzy.model.Submission;
import gradezzy.model.TestCaseResult;
import gradezzy.plagiarism.SimilarityPair;
import gradezzy.service.StudentMatcher;
import gradezzy.ui.backend.ActionResult;
import gradezzy.ui.backend.Capability;
import gradezzy.ui.backend.DashboardBackend;
import gradezzy.ui.backend.RepairItem;
import gradezzy.ui.backend.ResultState;
import gradezzy.ui.backend.ReviewDecision;
import gradezzy.ui.backend.ReviewRules;
import gradezzy.util.TextUtils;

/**
 * Details of one student on the Review step.
 *
 * <p>Tier: SHOULD.
 */
public class StudentPanel extends ScrollPane {

    // The width of the panel: wide enough for a card's four buttons on
    // one line and a long status pill beside the total
    private static final double WIDTH = 440.0;

    // The size of the close icon
    private static final double CLOSE_SIZE = 16.0;

    // The width of the question name column
    private static final double ID_WIDTH = 40.0;

    // The width of the mark column, such as "2 / 3"
    private static final double MARK_WIDTH = 44.0;

    // The gap between the columns of a question row
    private static final double ROW_GAP = 10.0;

    // The gap between the small parts of a card
    private static final double TIGHT_GAP = Parts.GAP_TINY;

    // The gap between a title and the line under it
    private static final double LINE_GAP = 2.0;

    // The total shown for a student with no marks, as on Review
    private static final String NO_MARK = "-";

    // The separator between the username and the section
    private static final String DOT = " · ";

    // Put between a mark and the maximum mark
    private static final String OUT_OF = " / ";

    // Starts a new line in a tip
    private static final String LINE_END = "\n";

    // Starts each line of the Similar code card
    private static final String BULLET = "\u2022 ";

    // Turns a similarity share into a percentage
    private static final double PERCENT = 100.0;

    // How many matches the Similar code card lists before Show all, and
    // the link that hides the rest again
    private static final int SHOWN_SIMILAR_LINES = 5;
    private static final String SHOW_FEWER = "Show fewer";

    // Put before a line the student had, and a line the fix wrote
    private static final String REMOVED_SIGN = "- ";
    private static final String ADDED_SIGN = "+ ";

    // Put around a quoted name so it only matches as a whole word
    private static final String WORD_START = "(?s).*\\b";
    private static final String WORD_END = "\\b.*";

    // Comes between a test's number and whether it passed in its tip
    private static final String VERDICT_SEPARATOR = ": ";

    // Joins an issue's words and its advice, to search both for a name
    private static final String WORD_GAP = " ";

    // The window, for sheets and notices
    private final FlowWindow window;

    // Called after a change so the table can redraw marks and statuses
    private final Runnable onChanged;

    // Called when the close button is pressed
    private final Runnable onClosed;

    // The panel's content
    private final VBox body = new VBox(Parts.GAP);

    // The student shown, or null
    private Submission submission = null;

    // The problems every graded student shares, shown on Review instead
    private final List<Anomaly> sharedProblems = new ArrayList<>();

    // True while a card is built for the code sheet, not the panel
    private boolean buildingForSheet = false;

    // Keeps or rejects the same kind of fix of other students together
    private final SameFixes sameFixes;

    /**
     * Creates an empty panel.
     *
     * @param window the window
     * @param onChanged called after something changes a mark or status
     * @param onClosed called when the instructor closes the panel
     */
    public StudentPanel(FlowWindow window, Runnable onChanged,
            Runnable onClosed) {
        this.window = window;
        this.onChanged = onChanged;
        this.onClosed = onClosed;
        this.sameFixes = new SameFixes(window, () -> changed());
        getStyleClass().add("student-panel");
        body.getStyleClass().add("student-panel-body");
        setContent(body);
        setFitToWidth(true);
        setHbarPolicy(ScrollBarPolicy.NEVER);
        setPrefWidth(WIDTH);
        setMinWidth(WIDTH);
        setMaxWidth(WIDTH);
    }

    /**
     * Shows a student.
     *
     * @param shown the student, or null for none
     */
    public void show(Submission shown) {
        boolean sameStudent = isSameStudent(shown);
        submission = shown;
        refresh();
        // A new student starts at the top; the same one stays in place
        if (!sameStudent) {
            setVvalue(getVmin());
        }
    }

    // Says whether a student is the one shown now.
    private boolean isSameStudent(Submission shown) {
        if (shown == null || submission == null) {
            return false;
        }
        return shown.getUsername().equals(submission.getUsername());
    }

    /**
     * Returns the student shown.
     *
     * @return the student, or null
     */
    public Submission getSubmission() {
        return submission;
    }

    /**
     * Opens the sheet that changes one of the shown student's marks.
     * Does nothing when the backend cannot change marks.
     *
     * @param questionId the question, such as Q1a
     */
    public void openAdjust(String questionId) {
        QuestionResult result = findResult(questionId);
        boolean cannotAdjust = submission == null || result == null
                || !window.getBackend().supports(Capability.SCORE_ADJUSTMENT);
        if (cannotAdjust) {
            return;
        }
        window.showSheet(AdjustMarkSheet.create(window, submission, result,
                () -> changed()));
    }

    /**
     * Redraws the panel for the student shown, keeping how far down it
     * is scrolled.
     */
    public void refresh() {
        double offset = scrolledBy();
        rebuild();
        scrollDownBy(offset);
    }

    // Returns how far down the panel is scrolled, in pixels.
    private double scrolledBy() {
        double hidden = body.getHeight() - getViewportBounds().getHeight();
        if (hidden <= 0) {
            return 0;
        }
        return getVvalue() * hidden;
    }

    // Scrolls down by a number of pixels, as far as the panel allows.
    // The panel is laid out first so the new height is known.
    private void scrollDownBy(double offset) {
        applyCss();
        layout();
        double hidden = body.getHeight() - getViewportBounds().getHeight();
        if (hidden <= 0) {
            setVvalue(getVmin());
            return;
        }
        double share = offset / hidden;
        setVvalue(Math.min(getVmax(), share));
    }

    // Builds the panel's contents for the student shown.
    private void rebuild() {
        body.getChildren().clear();
        if (submission == null) {
            return;
        }
        DashboardBackend backend = window.getBackend();
        body.getChildren().addAll(createTop(), createTotal(),
                createQuestions(backend));
        addAdjustments(backend);
        addFixes(backend);
        addProblems(backend);
        VBox links = createLinks(backend);
        if (!links.getChildren().isEmpty()) {
            body.getChildren().add(links);
        }
    }

    // Builds the name, username and close button.
    private HBox createTop() {
        Label name = Parts.strong(Students.name(submission));
        name.getStyleClass().add("panel-name");
        Label who = Parts.muted(submission.getUsername() + DOT
                + submission.getSection().getLabel());
        who.getStyleClass().add("small-text");
        VBox names = new VBox(name, who);
        addMatchLine(names);
        Label warning = Students.similarWarning(submission,
                window.getBackend());
        if (warning != null) {
            names.getChildren().add(warning);
            VBox.setMargin(warning, new Insets(Parts.GAP_TINY, 0, 0, 0));
        }
        names.setMinWidth(0);
        HBox.setHgrow(names, Priority.ALWAYS);

        Button close = new Button();
        close.setGraphic(Parts.icon(Parts.CLOSE, CLOSE_SIZE));
        close.getStyleClass().addAll("flat", "icon-button");
        close.setTooltip(new Tooltip("Close"));
        close.setOnAction(event -> onClosed.run());

        HBox top = new HBox(Parts.GAP, names, close);
        top.setAlignment(Pos.TOP_LEFT);
        return top;
    }

    // Returns the assessment the open run was graded with, else the one
    // in use, so the questions match the student's results.
    private Assessment shownAssessment() {
        GradingRun run = window.getState().getRun();
        if (run != null && run.getAssessment() != null) {
            return run.getAssessment();
        }
        return window.getBackend().getAssessment();
    }

    // Builds the large total, "out of" and the status pill on one line.
    private HBox createTotal() {
        // With no marks, show the dash Review shows, not a total of 0
        boolean hasMarks = !submission.getQuestionResults().isEmpty();
        String totalText = NO_MARK;
        if (hasMarks) {
            totalText = Parts.mark(submission.getTotalScore());
        }
        Label total = new Label(totalText);
        total.getStyleClass().add("big-total");
        total.setMinWidth(Region.USE_PREF_SIZE);
        Label max = Parts.muted("");
        max.setMinWidth(Region.USE_PREF_SIZE);
        Assessment assessment = shownAssessment();
        if (assessment != null && hasMarks) {
            max.setText("out of " + Parts.mark(assessment.getMaxTotal()));
        }
        Label pill = Students.pill(submission, window.getBackend());
        // A long status, such as three questions that did not compile,
        // may be shortened here; its tooltip keeps the whole text
        pill.setMinWidth(0);
        pill.setTooltip(new Tooltip(pill.getText()));
        HBox row = new HBox(Parts.GAP_SMALL, total, max, Parts.grow(),
                pill);
        row.setAlignment(Pos.BASELINE_LEFT);
        row.getStyleClass().add("total-row");
        return row;
    }

    // Builds one row per question; clicking one shows its tests.
    private VBox createQuestions(DashboardBackend backend) {
        VBox list = new VBox();
        list.getStyleClass().add("question-list");
        Assessment assessment = shownAssessment();
        if (assessment == null) {
            return list;
        }
        for (Question question : assessment.getQuestions()) {
            list.getChildren().add(createQuestion(question,
                    findResult(question.getId()), backend));
        }
        return list;
    }

    // Builds the row of one question: name, bar, mark and Adjust; under
    // them, always, a square per test or why there are none; and, on a
    // click, the links to its code and output and to grade it again.
    private VBox createQuestion(Question question, QuestionResult result,
            DashboardBackend backend) {
        double score = 0;
        if (result != null) {
            score = result.getScore();
        }
        double max = question.getMaxScore();

        Label id = Parts.strong(question.getId());
        fixWidth(id, ID_WIDTH);
        double share = score / max;
        StackPane bar = Parts.track(share, toneOf(score, max, result));
        Label mark = new Label(Parts.mark(score) + OUT_OF + Parts.mark(max));
        mark.getStyleClass().add("question-mark");
        fixWidth(mark, MARK_WIDTH);
        mark.setAlignment(Pos.CENTER_RIGHT);
        HBox top = new HBox(ROW_GAP, id, bar, mark);
        top.setAlignment(Pos.CENTER_LEFT);
        top.getStyleClass().add("question-top");
        addAdjustLink(top, question, result, backend);

        VBox details = createDetails(question, result, backend);
        VBox row = new VBox(top, createSummary(result));
        row.getStyleClass().add("question-row");
        if (details.getChildren().isEmpty()) {
            return row;
        }
        details.setVisible(false);
        details.setManaged(false);
        row.getChildren().add(details);
        row.getStyleClass().add("clickable");
        top.setOnMouseClicked(event -> toggleDetails(details));
        Parts.tipNode(top, "Show the links to " + question.getId()
                + "'s code and output, and to grade it again");
        return row;
    }

    // Adds Adjust to a question's row when its mark can be changed.
    private void addAdjustLink(HBox top, Question question,
            QuestionResult result, DashboardBackend backend) {
        boolean adjustable = result != null
                && backend.supports(Capability.SCORE_ADJUSTMENT);
        if (!adjustable) {
            return;
        }
        Hyperlink adjust = Parts.link("Adjust",
                () -> openAdjust(question.getId()));
        adjust.getStyleClass().add("small-text");
        adjust.addEventHandler(MouseEvent.MOUSE_CLICKED,
                event -> event.consume());
        top.getChildren().add(adjust);
    }

    // Builds the line under a question's bar: a square per test with how
    // many passed, or, when no test ran, why not.
    private FlowPane createSummary(QuestionResult result) {
        FlowPane line = new FlowPane(TIGHT_GAP, TIGHT_GAP);
        line.getStyleClass().add("question-summary");
        line.setAlignment(Pos.CENTER_LEFT);
        if (result == null) {
            line.getChildren().add(summaryText("Not graded", false));
            return line;
        }
        List<TestCaseResult> tests = result.getTestCaseResults();
        if (tests.isEmpty()) {
            line.getChildren().add(summaryText(whyNoTests(result), true));
            return line;
        }
        addTestSquares(line, tests);
        int passed = 0;
        for (TestCaseResult test : tests) {
            if (test.isPassed()) {
                passed++;
            }
        }
        String text = passed + " of " + tests.size() + " passed";
        if (result.isTimedOut()) {
            text += ", timed out";
        }
        Label count = summaryText(text, false);
        FlowPane.setMargin(count, new Insets(0, 0, 0, TIGHT_GAP));
        line.getChildren().add(count);
        return line;
    }

    // Returns the small text of a question's summary line; a problem is
    // shown in red.
    private Label summaryText(String text, boolean problem) {
        Label label = Parts.muted(text);
        label.getStyleClass().add("small-text");
        if (problem) {
            label.getStyleClass().add("summary-problem");
        }
        return label;
    }

    // Says why a graded question has no test results.
    private String whyNoTests(QuestionResult result) {
        if (Students.didNotCompile(result)) {
            return "Did not compile, so no test ran";
        } else if (!result.isCompiled()) {
            return "Not compiled: the file or its tester is missing";
        } else if (result.isTimedOut()) {
            return "Timed out before any test finished";
        }
        return "No test results were recorded";
    }

    /**
     * Shows a question's details when they are hidden, and hides them
     * when they are shown.
     *
     * @param details the reason, test squares and Grade again link
     */
    private void toggleDetails(VBox details) {
        details.setVisible(!details.isVisible());
        details.setManaged(details.isVisible());
    }

    // Builds what a click on a question shows.
    private VBox createDetails(Question question, QuestionResult result,
            DashboardBackend backend) {
        VBox details = new VBox(TIGHT_GAP);
        details.getStyleClass().add("question-details");
        if (result == null) {
            return details;
        }
        if (backend.supports(Capability.SOURCE_VIEW)) {
            Hyperlink code = Parts.link("See " + question.getId()
                    + "'s code and output", () -> openCode(question.getId()));
            code.getStyleClass().add("small-text");
            details.getChildren().add(code);
        }
        boolean regrading = window.getState().isRegrading(submission);
        if (backend.supports(Capability.RETRY_QUESTION) && !regrading) {
            Hyperlink retry = Parts.link("Grade " + question.getId()
                    + " again", () -> askRetryQuestion(backend, question));
            retry.getStyleClass().add("small-text");
            details.getChildren().add(retry);
        }
        return details;
    }

    // Adds one small square per test case to a line. Resting the pointer
    // on a square shows the test's number, verdict and tester line.
    private void addTestSquares(FlowPane tests,
            List<TestCaseResult> results) {
        for (TestCaseResult test : results) {
            String verdict = "failed";
            if (test.isPassed()) {
                verdict = "passed";
            }
            Region square = new Region();
            square.getStyleClass().addAll("test-square", verdict);
            String detail = "Test " + test.getTestNumber()
                    + VERDICT_SEPARATOR + verdict;
            String output = test.getDetail();
            // The tester's line is shown only when it says more, such as
            // why a test failed
            boolean saysMore = output != null && !output.trim().isEmpty()
                    && !output.trim().equalsIgnoreCase(verdict);
            if (saysMore) {
                detail = detail + LINE_END + output;
            }
            Parts.tipNode(square, detail);
            tests.getChildren().add(square);
        }
    }

    // Returns the colour of a score bar: full marks good, some marks
    // amber, none red; grey when the question was not graded.
    private Tone toneOf(double score, double max, QuestionResult result) {
        if (result == null) {
            return Tone.NEUTRAL;
        } else if (score >= max) {
            return Tone.GOOD;
        } else if (score > 0) {
            return Tone.WARN;
        }
        return Tone.BAD;
    }

    // Returns the result of one question, or null.
    private QuestionResult findResult(String questionId) {
        if (submission == null) {
            return null;
        }
        for (QuestionResult result : submission.getQuestionResults()) {
            if (result.getQuestion().getId().equals(questionId)) {
                return result;
            }
        }
        return null;
    }

    // Gives a label one width that never changes.
    private void fixWidth(Label label, double width) {
        label.setMinWidth(width);
        label.setPrefWidth(width);
        label.setMaxWidth(width);
    }

    // ------------------------------------------------------ adjustments

    // Adds a card listing the marks changed by hand, each with Undo.
    private void addAdjustments(DashboardBackend backend) {
        if (!backend.supports(Capability.SCORE_ADJUSTMENT)) {
            return;
        }
        List<ScoreAdjustment> changes = backend.listAdjustments(
                window.getState().getRun(), submission);
        if (changes.isEmpty()) {
            return;
        }
        VBox card = new VBox(Parts.GAP_SMALL);
        card.getStyleClass().add("fix-card");
        String title = Parts.plural(changes.size(), "mark")
                + " changed by hand";
        card.getChildren().add(Parts.pill(title, Tone.NEUTRAL));
        for (ScoreAdjustment change : changes) {
            card.getChildren().add(createAdjustment(change, backend));
        }
        body.getChildren().add(card);
    }

    // Builds the line of one change: what changed, why, and Undo.
    private HBox createAdjustment(ScoreAdjustment change,
            DashboardBackend backend) {
        Label what = Parts.strong(change.getQuestionId() + " changed from "
                + Parts.mark(change.getPreviousScore()) + " to "
                + Parts.mark(change.getNewScore()));
        Label why = Parts.muted(change.getReason());
        why.getStyleClass().add("small-text");
        why.setWrapText(true);
        VBox text = new VBox(LINE_GAP, what, why);
        text.setMinWidth(0);
        HBox.setHgrow(text, Priority.ALWAYS);
        Button undo = Parts.small("Undo",
                () -> undoAdjustment(backend, change));
        HBox line = new HBox(Parts.GAP, text, undo);
        line.setAlignment(Pos.CENTER_LEFT);
        return line;
    }

    // ------------------------------------------------------------ fixes

    // Adds a card for each fix made for the student.
    private void addFixes(DashboardBackend backend) {
        GradingRun run = window.getState().getRun();
        if (run == null || !backend.supports(Capability.REPAIR_DECISIONS)) {
            return;
        }
        String username = submission.getUsername();
        for (RepairItem repair : backend.listRepairs(run)) {
            if (repair.getAnomaly().getUsername().equals(username)) {
                body.getChildren().add(createFixCard(repair, backend));
            }
        }
    }

    // Builds the card of one fix.
    private VBox createFixCard(RepairItem repair, DashboardBackend backend) {
        Anomaly anomaly = repair.getAnomaly();
        FixStatus status = anomaly.getFixStatus();
        Label tag = Parts.pill("Checked", Tone.NEUTRAL);
        String title = Problems.title(anomaly.getType());
        if (status == FixStatus.APPROVED) {
            tag = Parts.pill("Fix kept", Tone.GOOD);
            title = Problems.fixedTitle(anomaly.getType());
        } else if (status == FixStatus.UNDONE) {
            tag = Parts.pill("Fix rejected", Tone.NEUTRAL);
        } else if (status == FixStatus.APPLIED) {
            tag = Parts.pill("Fixed for you", Tone.WARN);
            title = Problems.fixedTitle(anomaly.getType());
        } else if (backend.needsReview(anomaly)) {
            tag = Parts.pill("Could not fix", Tone.BAD);
        }
        VBox card = createCard(tag, title, describeFinding(anomaly));
        addChange(card, repair, backend);
        HBox buttons = Parts.row(Parts.GAP_SMALL);
        addFixButtons(card, buttons, repair, backend);
        addOpenCode(buttons, anomaly);
        addButtons(card, buttons);
        return card;
    }

    // Adds what a fix changed, line by line, when the backend knows it.
    private void addChange(VBox card, RepairItem repair,
            DashboardBackend backend) {
        String before = repair.getOriginalValue();
        boolean known = backend.supports(Capability.REPAIR_VALUES)
                && before != null && !before.isEmpty();
        if (!known) {
            return;
        }
        VBox change = new VBox();
        change.getStyleClass().add("change-diff");
        addDiffLines(change, before, false);
        addDiffLines(change, repair.getProposedValue(), true);
        card.getChildren().add(change);
    }

    // Adds the buttons that decide a fix, or the note that says why
    // there are none yet.
    private void addFixButtons(VBox card, HBox buttons, RepairItem repair,
            DashboardBackend backend) {
        Anomaly anomaly = repair.getAnomaly();
        FixStatus status = anomaly.getFixStatus();
        if (repair.isBlocked()) {
            Label note = Parts.muted(repair.getNote());
            note.getStyleClass().add("small-text");
            note.setWrapText(true);
            card.getChildren().add(note);
            if (backend.needsReview(anomaly)) {
                addAdvice(card, anomaly);
            }
            addCheckButton(buttons, anomaly, backend);
        } else if (isWaitingOnJob(backend, anomaly)) {
            addFixJobNote(card, anomaly);
        } else if (status == FixStatus.APPLIED) {
            Button keep = Parts.small("Keep fix",
                    () -> approveRepair(backend, repair), Tone.GOOD);
            Button reject = Parts.small("Reject fix",
                    () -> rejectRepair(backend, repair), Tone.BAD);
            buttons.getChildren().addAll(keep, reject);
        } else if (canApplyAgain(backend, status, anomaly)) {
            Button again = Parts.small("Apply fix again",
                    () -> reapplyRepair(backend, repair));
            Parts.tip(again, "Grades the student again with this fix.");
            buttons.getChildren().add(again);
        } else if (canTakeBack(backend, anomaly)) {
            Button undo = Parts.small("Undo",
                    () -> undoRepairDecision(backend, repair));
            Parts.tip(undo, "Take back this decision, so the fix waits for "
                    + "you again. The files and marks stay as they are.");
            buttons.getChildren().add(undo);
        }
    }

    // Says whether a rejected fix can be applied again.
    private boolean canApplyAgain(DashboardBackend backend, FixStatus status,
            Anomaly anomaly) {
        return status == FixStatus.UNDONE
                && UndoFixJob.canApplyAgain(backend, anomaly);
    }

    // Says what a fix did once it is made, such as the folder's new name,
    // else what the student had wrong.
    private String describeFinding(Anomaly anomaly) {
        String done = anomaly.describeFix();
        if (!done.isEmpty()) {
            return done;
        }
        return anomaly.getMessage();
    }

    // Adds Open code to a card, opening the code sheet on the question
    // the issue is about, or on the first question when it names none.
    private void addOpenCode(HBox buttons, Anomaly anomaly) {
        boolean noCode = !window.getBackend().supports(Capability.SOURCE_VIEW)
                || submission.getQuestionResults().isEmpty();
        if (noCode) {
            return;
        }
        if (buildingForSheet) {
            return;
        }
        String questionId = questionOf(anomaly);
        Button open = Parts.small("Open code",
                () -> openCodeFor(anomaly, questionId));
        String where = "this student's code";
        if (questionId != null) {
            where = questionId + "'s code and test output";
        }
        Parts.tip(open, "Show " + where + ".");
        buttons.getChildren().add(open);
    }

    // Opens the code sheet on an issue's question, with the issue and its
    // buttons at the top, so it can be decided while reading the code.
    private void openCodeFor(Anomaly anomaly, String questionId) {
        window.showSheet(CodeSheet.create(window, submission, questionId,
                sheetCardOf(anomaly)));
    }

    // Builds an issue's card again for the code sheet: the same words
    // and buttons, without Open code. Pressing a button closes the sheet
    // first, then decides, so the panel and the notice with Undo show.
    private Node sheetCardOf(Anomaly anomaly) {
        DashboardBackend backend = window.getBackend();
        VBox card = null;
        buildingForSheet = true;
        try {
            GradingRun run = window.getState().getRun();
            boolean fixFinding = run != null
                    && ReviewRules.isValidationFinding(anomaly.getType());
            if (fixFinding) {
                for (RepairItem repair : backend.listRepairs(run)) {
                    if (repair.getAnomaly().getId().equals(anomaly.getId())) {
                        card = createFixCard(repair, backend);
                    }
                }
            }
            if (card == null) {
                card = createProblemCard(anomaly, backend);
            }
        } finally {
            buildingForSheet = false;
        }
        for (Button button : buttonsIn(card)) {
            EventHandler<ActionEvent> action = button.getOnAction();
            if (action != null) {
                button.setOnAction(event -> closeThen(action, event));
            }
        }
        card.getStyleClass().add("sheet-issue");
        return card;
    }

    // Closes the sheet, then does what the button does in the panel.
    private void closeThen(EventHandler<ActionEvent> action,
            ActionEvent event) {
        window.closeSheet();
        action.handle(event);
    }

    // Lists every button inside a node.
    private static List<Button> buttonsIn(Node node) {
        List<Button> found = new ArrayList<>();
        if (node instanceof Button) {
            found.add((Button) node);
        } else if (node instanceof Parent) {
            Parent parent = (Parent) node;
            for (Node child : parent.getChildrenUnmodifiable()) {
                found.addAll(buttonsIn(child));
            }
        }
        return found;
    }

    // Finds the question an issue is about from its words: its id, such
    // as Q2b, or its file or folder name. Longer ids are tried first, so
    // "Q1a" is not taken for "Q1". Returns null when none is named.
    private String questionOf(Anomaly anomaly) {
        String text = anomaly.getMessage() + WORD_GAP
                + anomaly.getSuggestedAction();
        String found = null;
        int foundLength = 0;
        for (QuestionResult result : submission.getQuestionResults()) {
            Question question = result.getQuestion();
            if (question != null) {
                String[] names = {question.getId(), question.getFileName(),
                    question.getFolderName()};
                for (String name : names) {
                    if (isLongerName(text, name, foundLength)) {
                        found = question.getId();
                        foundLength = name.length();
                    }
                }
            }
        }
        return found;
    }

    // Says whether a name is longer than the one found so far and is
    // named in the text as a whole word.
    private boolean isLongerName(String text, String name, int foundLength) {
        boolean longer = name != null && !name.isEmpty()
                && name.length() > foundLength;
        if (!longer) {
            return false;
        }
        String wholeWord = WORD_START + Pattern.quote(name) + WORD_END;
        return text.matches(wholeWord);
    }

    // Says whether a fix card waits for a fix being undone or applied
    // again, so its buttons are hidden until that ends.
    private boolean isWaitingOnJob(DashboardBackend backend,
            Anomaly anomaly) {
        if (window.getState().getFixInProgress() == null) {
            return false;
        }
        FixStatus status = anomaly.getFixStatus();
        return status == FixStatus.APPLIED
                || canApplyAgain(backend, status, anomaly);
    }

    // Says, in place of the buttons, that a fix is being undone or
    // applied again.
    private void addFixJobNote(VBox card, Anomaly anomaly) {
        FlowState state = window.getState();
        String text = "Wait until the other fix has finished.";
        if (state.countFixesInProgress() > 1) {
            text = "Wait until the other fixes have finished.";
        }
        boolean running = anomaly == state.getFixInProgress();
        if (!running && state.isFixInProgress(anomaly)) {
            text = "Waiting its turn: this fix is undone and the student "
                    + "graded again after the ones before it. You can keep "
                    + "reviewing.";
        } else if (running && anomaly.getFixStatus() == FixStatus.UNDONE) {
            text = "Applying the fix again and grading this student again. "
                    + "This can take a minute; you can keep reviewing.";
        } else if (running) {
            text = "Undoing the fix and grading this student again. "
                    + "This can take a minute; you can keep reviewing.";
        }
        Label note = Parts.muted(text);
        note.getStyleClass().add("small-text");
        note.setWrapText(true);
        card.getChildren().add(note);
    }

    // Adds one row per line of a fix's value: red with a minus for what
    // the student had, green with a plus for what the working copy holds.
    private void addDiffLines(VBox change, String value, boolean added) {
        if (value == null) {
            return;
        }
        String sign = REMOVED_SIGN;
        String style = "removed";
        if (added) {
            sign = ADDED_SIGN;
            style = "added";
        }
        String[] lines = value.split(LINE_END);
        for (String text : lines) {
            Label line = new Label(sign + text);
            line.setWrapText(true);
            line.setMaxWidth(Double.MAX_VALUE);
            line.getStyleClass().addAll("diff-line", style);
            change.getChildren().add(line);
        }
    }

    // Adds Mark as checked to a fix that could not be made, or Reopen
    // once it is checked.
    private void addCheckButton(HBox buttons, Anomaly anomaly,
            DashboardBackend backend) {
        if (!backend.supports(Capability.FLAG_REVIEW)) {
            return;
        }
        if (backend.needsReview(anomaly)) {
            buttons.getChildren().add(checkedButton(backend, anomaly));
        } else {
            buttons.getChildren().add(undoButton(backend, anomaly));
        }
    }

    // ---------------------------------------------------------- problems

    // Adds a card for each flag and problem that is not a fix. Some
    // kinds share one card, where the first of them would be: a
    // student's Similar code flags, and the questions that did not
    // compile, crashed or ran too long, as each is one thing to look at.
    private void addProblems(DashboardBackend backend) {
        addZeroCard(backend);
        int shared = 0;
        // The kinds that share a card, in the order they first appear,
        // with their flags and the place of their card
        List<AnomalyType> groupedTypes = new ArrayList<>();
        List<List<Anomaly>> groups = new ArrayList<>();
        List<Integer> places = new ArrayList<>();
        for (Anomaly anomaly : submission.getAnomalies()) {
            AnomalyType type = anomaly.getType();
            boolean finding = ReviewRules.isValidationFinding(type);
            boolean similar = type == AnomalyType.SIMILAR_CODE;
            if (similar) {
                addToGroup(anomaly, groupedTypes, groups, places);
            } else if (!finding && isShared(anomaly)) {
                shared++;
            } else if (isGroupedType(type)) {
                addToGroup(anomaly, groupedTypes, groups, places);
            } else if (!finding) {
                body.getChildren().add(createProblemCard(anomaly, backend));
            }
        }
        // The last card first, so the places of earlier ones still hold
        for (int index = groups.size() - 1; index >= 0; index--) {
            body.getChildren().add(places.get(index), createGroupCard(
                    groupedTypes.get(index), groups.get(index), backend));
        }
        if (shared > 0) {
            Label note = Parts.muted("Also " + Parts.plural(shared,
                    "problem") + " every graded student shares, shown "
                    + "above the table.");
            note.setWrapText(true);
            body.getChildren().add(note);
        }
    }

    // Says whether a kind of problem shares one card for all questions.
    private static boolean isGroupedType(AnomalyType type) {
        return type == AnomalyType.SIMILAR_CODE
                || type == AnomalyType.COMPILE_ERROR
                || type == AnomalyType.RUNTIME_ERROR
                || type == AnomalyType.TIMEOUT;
    }

    // Adds a flag to the group of its kind, starting the group, at the
    // place its card goes, when it is the first of its kind.
    private void addToGroup(Anomaly anomaly, List<AnomalyType> types,
            List<List<Anomaly>> groups, List<Integer> places) {
        int index = types.indexOf(anomaly.getType());
        if (index < 0) {
            types.add(anomaly.getType());
            groups.add(new ArrayList<>());
            places.add(body.getChildren().size());
            index = types.size() - 1;
        }
        groups.get(index).add(anomaly);
    }

    // Builds the one card of a group: Similar code, one problem's own
    // card, or the questions of one kind of problem together.
    private VBox createGroupCard(AnomalyType type, List<Anomaly> group,
            DashboardBackend backend) {
        if (type == AnomalyType.SIMILAR_CODE) {
            return createSimilarCard(group, backend);
        }
        if (group.size() == 1) {
            return createProblemCard(group.get(0), backend);
        }
        return createQuestionsCard(type, group, backend);
    }

    // Builds one card for the questions that had the same problem, such
    // as "Q1a and Q1b did not compile": each question's words, one a
    // line, Mark all as checked, and Open code for each question.
    private VBox createQuestionsCard(AnomalyType type, List<Anomaly> group,
            DashboardBackend backend) {
        List<Anomaly> open = new ArrayList<>();
        for (Anomaly anomaly : group) {
            if (backend.needsReview(anomaly)) {
                open.add(anomaly);
            }
        }
        Label tag = Parts.pill("Problem", Tone.BAD);
        if (open.isEmpty()) {
            ReviewDecision decision = backend.getDecision(group.get(0));
            tag = Parts.pill(decisionWords(decision, false), Tone.NEUTRAL);
        }
        List<String> lines = new ArrayList<>();
        for (Anomaly anomaly : group) {
            lines.add(BULLET + anomaly.getMessage());
        }
        VBox card = createCard(tag, questionTitle(type,
                questionsOf(group)), TextUtils.join(LINE_END, lines));
        if (!open.isEmpty()) {
            addAdvice(card, group.get(0));
        }

        HBox buttons = Parts.row(Parts.GAP_SMALL);
        if (backend.supports(Capability.FLAG_REVIEW) && !open.isEmpty()) {
            buttons.getChildren().add(Parts.small("Mark all as checked",
                    () -> decideAll(backend, open, ReviewDecision.REVIEWED),
                    Tone.GOOD));
        } else if (backend.supports(Capability.FLAG_REVIEW)) {
            Button undo = Parts.small("Undo", () -> decideAll(backend,
                    group, ReviewDecision.OPEN));
            Parts.tip(undo, "Take back this decision, so they wait for you "
                    + "again. No mark changes.");
            buttons.getChildren().add(undo);
        }
        addOpenCodeChoices(buttons, group);
        addButtons(card, buttons);
        return card;
    }

    // Lists the questions a group of problems is about, in order, each
    // once.
    private List<String> questionsOf(List<Anomaly> group) {
        List<String> questions = new ArrayList<>();
        for (Anomaly anomaly : group) {
            String questionId = questionOf(anomaly);
            if (questionId != null && !questions.contains(questionId)) {
                questions.add(questionId);
            }
        }
        return questions;
    }

    // Names a problem with its questions, such as "Q1a and Q1b did not
    // compile"; the plain title when it names no question.
    private String questionTitle(AnomalyType type, List<String> questions) {
        if (questions.isEmpty()) {
            return Problems.title(type);
        }
        String names = joinWithAnd(questions);
        boolean one = questions.size() == 1;
        if (type == AnomalyType.COMPILE_ERROR) {
            return names + " did not compile";
        } else if (type == AnomalyType.RUNTIME_ERROR && one) {
            return names + " crashed during a test";
        } else if (type == AnomalyType.RUNTIME_ERROR) {
            return names + " crashed during tests";
        } else if (type == AnomalyType.TIMEOUT && one) {
            return names + " ran too long and was stopped";
        } else if (type == AnomalyType.TIMEOUT) {
            return names + " ran too long and were stopped";
        }
        return Problems.title(type) + DOT + names;
    }

    // Joins names as "A", "A and B" or "A, B and C".
    private static String joinWithAnd(List<String> names) {
        int lastIndex = names.size() - 1;
        if (lastIndex <= 0) {
            return TextUtils.join("", names);
        }
        return TextUtils.join(", ", names.subList(0, lastIndex)) + " and "
                + names.get(lastIndex);
    }

    // Adds Open code for the one question of a group, or a list of its
    // questions when there are several.
    private void addOpenCodeChoices(HBox buttons, List<Anomaly> group) {
        boolean noCode = !window.getBackend().supports(Capability.SOURCE_VIEW)
                || submission.getQuestionResults().isEmpty()
                || buildingForSheet;
        if (noCode) {
            return;
        }
        List<String> questions = questionsOf(group);
        if (questions.size() <= 1) {
            addOpenCode(buttons, group.get(0));
            return;
        }
        MenuButton openCode = new MenuButton("Open code");
        openCode.getStyleClass().addAll("button", "small", "compare-with");
        for (Anomaly anomaly : group) {
            String questionId = questionOf(anomaly);
            if (questionId != null) {
                MenuItem item = new MenuItem(questionId);
                item.setOnAction(event -> openCodeFor(anomaly, questionId));
                openCode.getItems().add(item);
            }
        }
        Parts.tip(openCode, "Pick a question to read its code and test "
                + "output.");
        buttons.getChildren().add(openCode);
    }

    // Adds a card for a 0 that nothing flagged, so the instructor can
    // look at the code and mark it as checked; until then the student
    // needs a look before export.
    private void addZeroCard(DashboardBackend backend) {
        ResultState state = Students.state(submission, backend);
        boolean noZeroCard = state != ResultState.ZERO
                || !submission.getAnomalies().isEmpty();
        if (noZeroCard) {
            return;
        }
        boolean checked = backend.isZeroChecked(submission);
        Label tag = Parts.pill("Problem", Tone.BAD);
        String detailText = "Nothing was flagged that explains the 0. Look "
                + "at the code, then mark it as checked or change a mark.";
        if (checked) {
            tag = Parts.pill("Checked", Tone.NEUTRAL);
            detailText = "You looked at this 0. The student counts as "
                    + "graded.";
        }
        Label title = Parts.strong("Graded with 0 marks");
        title.setWrapText(true);
        Label detail = Parts.muted(detailText);
        detail.setWrapText(true);
        detail.getStyleClass().add("small-text");
        VBox text = new VBox(LINE_GAP, title, detail);
        VBox card = new VBox(Parts.GAP_SMALL, tag, text);
        card.getStyleClass().add("fix-card");
        card.setAlignment(Pos.TOP_LEFT);
        HBox buttons = Parts.row(Parts.GAP_SMALL);
        if (checked) {
            Button undo = Parts.small("Undo",
                    () -> checkZero(backend, false));
            Parts.tip(undo, "Take it back, so the 0 waits for you again. "
                    + "No mark changes.");
            buttons.getChildren().add(undo);
        } else {
            Button check = Parts.small("Mark as checked",
                    () -> checkZero(backend, true), Tone.GOOD);
            buttons.getChildren().add(check);
        }
        addButtons(card, buttons);
        body.getChildren().add(card);
    }

    // Marks the student's 0 as checked, or takes it back, with Undo.
    private void checkZero(DashboardBackend backend, boolean checked) {
        ActionResult result = backend.setZeroChecked(submission, checked);
        apply(result, false);
        if (result.isDone() && checked) {
            window.notify("Marked as checked.", "Undo",
                    () -> checkZero(backend, false));
        }
    }

    /**
     * Keeps the problems every graded student shares. Review shows them
     * once above the table, so the panel leaves out their cards.
     *
     * @param problems one anomaly per shared problem
     */
    public void setSharedProblems(List<Anomaly> problems) {
        sharedProblems.clear();
        sharedProblems.addAll(problems);
    }

    // Says whether a problem has the same type and words as a shared one.
    private boolean isShared(Anomaly anomaly) {
        for (Anomaly shared : sharedProblems) {
            boolean same = shared.getType() == anomaly.getType()
                    && shared.getMessage().equals(anomaly.getMessage());
            if (same) {
                return true;
            }
        }
        return false;
    }

    // Builds the one card of a student's Similar code flags: who the
    // check found alike and how alike, one a line, with Looks fine and
    // Keep flagged deciding every open one at once, Compare for each
    // other student, and Open code.
    private VBox createSimilarCard(List<Anomaly> flags,
            DashboardBackend backend) {
        List<Anomaly> open = new ArrayList<>();
        for (Anomaly flag : flags) {
            if (backend.needsReview(flag)) {
                open.add(flag);
            }
        }
        Label tag = Parts.pill("Flag", Tone.WARN);
        if (open.isEmpty()) {
            ReviewDecision decision = backend.getDecision(flags.get(0));
            tag = Parts.pill(decisionWords(decision, true), Tone.NEUTRAL);
        }
        List<String> lines = describeSimilar(flags, backend);
        int students = countOtherStudents(flags, backend);
        String title = "Very similar to another student's code";
        if (students > 1) {
            title = "Very similar to " + students + " other students' "
                    + "code";
        }
        Label detail = detailLabel("");
        VBox card = createCard(tag, title, detail);
        showSimilarLines(detail, lines, false);
        if (lines.size() > SHOWN_SIMILAR_LINES) {
            Hyperlink more = new Hyperlink("Show all " + lines.size()
                    + " matches");
            more.getStyleClass().addAll("flow-link", "small-text");
            more.setOnAction(event -> toggleSimilarLines(more, detail,
                    lines));
            card.getChildren().add(more);
        }
        if (!open.isEmpty()) {
            Label advice = Parts.muted("Similar is not proof of copying. "
                    + "Compare to read both answers side by side; your "
                    + "decision covers every student listed.");
            advice.setWrapText(true);
            advice.getStyleClass().add("small-text");
            card.getChildren().add(advice);
        }

        HBox buttons = Parts.row(Parts.GAP_SMALL);
        if (backend.supports(Capability.FLAG_REVIEW) && !open.isEmpty()) {
            Button fine = Parts.small("Looks fine", () -> decideAll(backend,
                    open, ReviewDecision.REJECTED), Tone.GOOD);
            Button keep = Parts.small("Keep flagged", () -> decideAll(
                    backend, open, ReviewDecision.REVIEWED), Tone.BAD);
            buttons.getChildren().addAll(fine, keep);
        } else if (backend.supports(Capability.FLAG_REVIEW)) {
            Button undo = Parts.small("Undo", () -> decideAll(backend,
                    flags, ReviewDecision.OPEN));
            Parts.tip(undo, "Take back this decision, so it waits for you "
                    + "again. No mark changes.");
            buttons.getChildren().add(undo);
        }
        addCompareChoices(buttons, flags, backend);
        addOpenCode(buttons, flags.get(0));
        addButtons(card, buttons);
        return card;
    }

    // Shows the first matches of the Similar code card, or all of them.
    private void showSimilarLines(Label detail, List<String> lines,
            boolean all) {
        int shown = lines.size();
        if (!all) {
            shown = Math.min(shown, SHOWN_SIMILAR_LINES);
        }
        detail.setText(TextUtils.join(LINE_END, lines.subList(0, shown)));
    }

    // Shows all the matches, or the first few again.
    private void toggleSimilarLines(Hyperlink link, Label detail,
            List<String> lines) {
        boolean showAll = !link.getText().equals(SHOW_FEWER);
        showSimilarLines(detail, lines, showAll);
        if (showAll) {
            link.setText(SHOW_FEWER);
        } else {
            link.setText("Show all " + lines.size() + " matches");
        }
    }

    // Counts the other students the check found alike, each once, though
    // one may be alike on several questions.
    private int countOtherStudents(List<Anomaly> flags,
            DashboardBackend backend) {
        GradingRun run = window.getState().getRun();
        List<String> others = new ArrayList<>();
        for (Anomaly flag : flags) {
            SimilarityPair pair = null;
            if (run != null) {
                pair = backend.findSimilarPair(run, flag);
            }
            String other = flag.getId();
            if (pair != null) {
                other = otherOf(pair);
            }
            if (!others.contains(other)) {
                others.add(other);
            }
        }
        return others.size();
    }

    // Lists who the check found alike, one a line, such as "Q2b: 100%
    // alike to RAY TAN (ray.tan.2025)", or a flag's own words when its
    // pair cannot be read.
    private List<String> describeSimilar(List<Anomaly> flags,
            DashboardBackend backend) {
        GradingRun run = window.getState().getRun();
        List<String> lines = new ArrayList<>();
        for (Anomaly flag : flags) {
            SimilarityPair pair = null;
            if (run != null) {
                pair = backend.findSimilarPair(run, flag);
            }
            if (pair == null) {
                lines.add(BULLET + flag.getMessage());
            } else {
                long percent = Math.round(pair.getSimilarity() * PERCENT);
                lines.add(BULLET + pair.getQuestionId() + ": " + percent
                        + Parts.PERCENT_SIGN + " alike to "
                        + describeStudent(run, otherOf(pair)));
            }
        }
        return lines;
    }

    // Returns the other student of a pair this student is in.
    private String otherOf(SimilarityPair pair) {
        if (pair.getFirstUsername().equals(submission.getUsername())) {
            return pair.getSecondUsername();
        }
        return pair.getFirstUsername();
    }

    // Names a student by name and email ID, such as RAY TAN
    // (ray.tan.2025), or by email ID alone when not in the run.
    private String describeStudent(GradingRun run, String username) {
        for (Submission other : run.getSubmissions()) {
            boolean named = other.getUsername().equals(username)
                    && !Students.name(other).equals(username);
            if (named) {
                return Students.name(other) + " (" + username + ")";
            }
        }
        return username;
    }

    // Adds Compare for a single pair, or Compare with, a list of the
    // other students, for several.
    private void addCompareChoices(HBox buttons, List<Anomaly> flags,
            DashboardBackend backend) {
        GradingRun run = window.getState().getRun();
        boolean canCompare = run != null
                && backend.supports(Capability.SIMILARITY_DETAIL);
        if (!canCompare) {
            return;
        }
        List<SimilarityPair> pairs = new ArrayList<>();
        for (Anomaly flag : flags) {
            SimilarityPair pair = backend.findSimilarPair(run, flag);
            if (pair != null) {
                pairs.add(pair);
            }
        }
        if (pairs.size() == 1) {
            SimilarityPair pair = pairs.get(0);
            Button compare = Parts.small("Compare",
                    () -> openCompare(run, pair));
            Parts.tip(compare, "Show " + pair.getQuestionId() + " of both "
                    + "students side by side, with the same lines marked.");
            buttons.getChildren().add(compare);
            return;
        }
        if (pairs.isEmpty()) {
            return;
        }
        MenuButton compareWith = new MenuButton("Compare with");
        compareWith.getStyleClass().addAll("button", "small",
                "compare-with");
        for (SimilarityPair pair : pairs) {
            MenuItem item = new MenuItem(pair.getQuestionId() + ": "
                    + describeStudent(run, otherOf(pair)));
            item.setOnAction(event -> openCompare(run, pair));
            compareWith.getItems().add(item);
        }
        Parts.tip(compareWith, "Pick a student to read both answers side "
                + "by side, with the same lines marked.");
        buttons.getChildren().add(compareWith);
    }

    // Decides several flags at once, with one Undo that puts each back.
    private void decideAll(DashboardBackend backend, List<Anomaly> flags,
            ReviewDecision decision) {
        List<ReviewDecision> before = new ArrayList<>();
        ActionResult result = null;
        for (Anomaly flag : flags) {
            before.add(backend.getDecision(flag));
            result = backend.setDecision(flag, decision);
        }
        apply(result, false);
        boolean done = result.isDone() && decision != ReviewDecision.OPEN;
        if (done) {
            boolean flag = Problems.isFlag(flags.get(0).getType());
            window.notify(decisionNotice(decision, flag), "Undo",
                    () -> restoreAll(backend, flags, before));
        }
    }

    // Puts several flags back to the decisions they had.
    private void restoreAll(DashboardBackend backend, List<Anomaly> flags,
            List<ReviewDecision> before) {
        ActionResult result = null;
        for (int index = 0; index < flags.size(); index++) {
            result = backend.setDecision(flags.get(index),
                    before.get(index));
        }
        apply(result, false);
    }

    // Names a flag or problem with its question when it has one, such
    // as "Q1a did not compile" or "The header names another student ·
    // Q3".
    private String cardTitle(Anomaly anomaly) {
        String questionId = questionOf(anomaly);
        String title = Problems.title(anomaly.getType());
        if (questionId == null || title.contains(questionId)) {
            return title;
        }
        List<String> questions = new ArrayList<>();
        questions.add(questionId);
        return questionTitle(anomaly.getType(), questions);
    }

    // Says under the student's name who they were matched to on the
    // score sheet by hand, such as "Matched to tina.goh.2025 on the score
    // sheet", with a link that takes the match back.
    private void addMatchLine(VBox names) {
        Student student = submission.getStudent();
        boolean byHand = student != null && !student.getUsername()
                .equalsIgnoreCase(submission.getUsername());
        if (!byHand) {
            return;
        }
        Label matched = Parts.muted("Matched to " + student.getUsername()
                + " on the score sheet");
        matched.getStyleClass().add("small-text");
        HBox line = Parts.row(Parts.GAP_SMALL, new Node[] {matched});
        if (window.getBackend().supports(Capability.SETTINGS)) {
            Hyperlink undo = Parts.link("Undo match",
                    () -> matchTo(null, student));
            undo.getStyleClass().add("small-text");
            line.getChildren().add(undo);
        }
        names.getChildren().add(line);
    }

    // Offers to match a student who is not on the score sheet to one of
    // its students who has no submission: the closest name first, as
    // "Did you mean ...?" when it looks like a typo, and a list of the
    // rest, closest first. Live mode reads the score sheet in the
    // background the first time, then shows the panel again.
    private void addScoreSheetMatch(VBox card) {
        GradingRun run = window.getState().getRun();
        if (run == null || buildingForSheet) {
            return;
        }
        List<Student> missing = window.findMissingStudents(run,
                () -> refresh());
        if (missing.isEmpty()) {
            return;
        }
        List<Student> ranked = StudentMatcher.rankByCloseness(missing,
                submission.getUsername());
        Student closest = ranked.get(0);
        boolean typo = StudentMatcher.isLikelyTypo(closest.getUsername(),
                submission.getUsername());
        HBox buttons = Parts.row(Parts.GAP_SMALL);
        if (typo) {
            Label guess = Parts.strong("Did you mean " + describe(closest)
                    + "? They are on the score sheet with no submission.");
            guess.setWrapText(true);
            guess.getStyleClass().add("small-text");
            card.getChildren().add(guess);
            Button match = Parts.small("Match to " + closest.getUsername(),
                    () -> matchTo(closest, null), Tone.GOOD);
            Parts.tip(match, "Their marks fill " + closest.getUsername()
                    + "'s row of the score sheet. You can undo it.");
            buttons.getChildren().add(match);
        }
        MenuButton others = new MenuButton("Match to...");
        others.getStyleClass().addAll("button", "small", "compare-with");
        for (Student student : ranked) {
            MenuItem item = new MenuItem(describe(student));
            item.setOnAction(event -> matchTo(student, null));
            others.getItems().add(item);
        }
        Parts.tip(others, "Pick the student on the score sheet this upload "
                + "belongs to, closest names first.");
        buttons.getChildren().add(others);
        addButtons(card, buttons);
    }

    // Names a score sheet student, such as TINA GOH (tina.goh.2025).
    private static String describe(Student student) {
        String name = student.getFullName();
        if (name.isEmpty()) {
            return student.getUsername();
        }
        return name + " (" + student.getUsername() + ")";
    }

    // Matches the student to a score sheet student, or takes the match
    // back with null, and offers Undo. previous is the student to go back
    // to, or null for not on the score sheet.
    private void matchTo(Student student, Student previous) {
        GradingRun run = window.getState().getRun();
        if (run == null) {
            return;
        }
        ActionResult result = window.getBackend().matchStudent(run,
                submission, student);
        // Who has no submission changed
        window.getState().forgetMissingStudents();
        apply(result, false);
        if (result.isDone()) {
            window.notify(result.getMessage(), "Undo",
                    () -> matchTo(previous, student));
        }
    }

    // Builds the card of one flag or problem.
    private VBox createProblemCard(Anomaly anomaly,
            DashboardBackend backend) {
        boolean flag = Problems.isFlag(anomaly.getType());
        ReviewDecision decision = backend.getDecision(anomaly);
        boolean open = backend.needsReview(anomaly);
        Label tag = Parts.pill("Problem", Tone.BAD);
        if (!open) {
            tag = Parts.pill(decisionWords(decision, flag), Tone.NEUTRAL);
        } else if (flag) {
            tag = Parts.pill("Flag", Tone.WARN);
        }
        VBox card = createCard(tag, cardTitle(anomaly),
                anomaly.getMessage());
        if (open) {
            addAdvice(card, anomaly);
        }
        if (anomaly.getType() == AnomalyType.UNKNOWN_STUDENT) {
            addScoreSheetMatch(card);
        }
        HBox buttons = Parts.row(Parts.GAP_SMALL);
        if (!backend.supports(Capability.FLAG_REVIEW)) {
            addCompare(buttons, anomaly, backend);
            addOpenCode(buttons, anomaly);
            addButtons(card, buttons);
            return card;
        }
        if (open && flag) {
            Button fine = Parts.small("Looks fine",
                    () -> decide(backend, anomaly, ReviewDecision.REJECTED),
                    Tone.GOOD);
            Button keep = Parts.small("Keep flagged",
                    () -> decide(backend, anomaly, ReviewDecision.REVIEWED),
                    Tone.BAD);
            buttons.getChildren().addAll(fine, keep);
        } else if (open) {
            buttons.getChildren().add(checkedButton(backend, anomaly));
        } else if (decision != ReviewDecision.OPEN) {
            buttons.getChildren().add(undoButton(backend, anomaly));
        }
        addCompare(buttons, anomaly, backend);
        addOpenCode(buttons, anomaly);
        addButtons(card, buttons);
        return card;
    }

    // Adds Compare to a Similar code flag, opening both students' answers
    // to the flagged question side by side.
    private void addCompare(HBox buttons, Anomaly anomaly,
            DashboardBackend backend) {
        GradingRun run = window.getState().getRun();
        boolean canCompare = anomaly.getType() == AnomalyType.SIMILAR_CODE
                && run != null
                && backend.supports(Capability.SIMILARITY_DETAIL);
        if (!canCompare) {
            return;
        }
        SimilarityPair pair = backend.findSimilarPair(run, anomaly);
        if (pair == null) {
            return;
        }
        Button compare = Parts.small("Compare",
                () -> openCompare(run, pair));
        Parts.tip(compare, "Show " + pair.getQuestionId() + " of both "
                + "students side by side, with the same lines marked.");
        buttons.getChildren().add(compare);
    }

    // Opens the sheet that compares a similar pair.
    private void openCompare(GradingRun run, SimilarityPair pair) {
        window.showSheet(SimilaritySheet.create(window, run, pair));
    }

    // Builds a card with its tag, title and detail.
    private VBox createCard(Label tag, String titleText, String detailText) {
        return createCard(tag, titleText, detailLabel(detailText));
    }

    // Returns the small grey words under a card's title.
    private Label detailLabel(String detailText) {
        Label detail = Parts.muted(Problems.shortenPaths(detailText,
                submission));
        detail.setWrapText(true);
        detail.getStyleClass().add("small-text");
        return detail;
    }

    // Builds a card with its tag, title and a detail label to change
    // later.
    private VBox createCard(Label tag, String titleText, Label detail) {
        Label title = Parts.strong(titleText);
        title.setWrapText(true);
        VBox text = new VBox(LINE_GAP, title, detail);
        VBox card = new VBox(Parts.GAP_SMALL, tag, text);
        card.getStyleClass().add("fix-card");
        card.setAlignment(Pos.TOP_LEFT);
        return card;
    }

    // Adds what the instructor can do about a problem, if it says.
    private void addAdvice(VBox card, Anomaly anomaly) {
        String action = anomaly.getSuggestedAction();
        if (action == null || action.trim().isEmpty()) {
            return;
        }
        Label advice = Parts.muted(action);
        advice.setWrapText(true);
        advice.getStyleClass().add("small-text");
        card.getChildren().add(advice);
    }

    // Adds a row of buttons to a card, unless it is empty.
    // The buttons are moved into a row that wraps, so a card with many
    // buttons never makes the panel wider than it is.
    private void addButtons(VBox card, HBox buttons) {
        if (buttons.getChildren().isEmpty()) {
            return;
        }
        List<Node> buttonNodes = new ArrayList<>(buttons.getChildren());
        buttons.getChildren().clear();
        FlowPane wrapping = new FlowPane(buttons.getSpacing(),
                Parts.GAP_SMALL);
        wrapping.getChildren().addAll(buttonNodes);
        wrapping.getStyleClass().add("card-buttons");
        card.getChildren().add(wrapping);
    }

    // Returns the words for a settled flag or problem.
    private String decisionWords(ReviewDecision decision, boolean flag) {
        if (decision == ReviewDecision.REJECTED) {
            return "Looks fine";
        } else if (decision == ReviewDecision.REVIEWED && flag) {
            return "Kept flagged";
        } else if (decision == ReviewDecision.RESOLVED) {
            return "Resolved by grading again";
        }
        return "Checked";
    }

    // ------------------------------------------------------------ links

    // Builds the links at the bottom: the code, and grading again.
    private VBox createLinks(DashboardBackend backend) {
        VBox links = new VBox(TIGHT_GAP);
        links.getStyleClass().add("panel-links");
        if (backend.supports(Capability.SOURCE_VIEW)) {
            links.getChildren().add(Parts.link("Open code and test output",
                    () -> openCode()));
        }
        if (window.getState().isRegrading(submission)) {
            links.getChildren().add(createRegradeNote());
        } else if (backend.supports(Capability.RETRY_SUBMISSION)) {
            links.getChildren().add(Parts.link("Grade this student again",
                    () -> askRetrySubmission(backend)));
        }
        return links;
    }

    // Says, in place of the Grade again links, that the student is being
    // graded again in the background.
    private Label createRegradeNote() {
        Label note = Parts.muted("Grading again in the background. This "
                + "can take a minute; you can keep reviewing.");
        note.getStyleClass().add("small-text");
        note.setWrapText(true);
        return note;
    }

    // ---------------------------------------------------------- actions

    /**
     * Opens the sheet with the shown student's code and test output.
     */
    private void openCode() {
        window.showSheet(CodeSheet.create(window, submission));
    }

    // Asks before grading the shown student again.
    private void askRetrySubmission(DashboardBackend backend) {
        window.confirm("Grade " + Students.name(submission) + " again?",
                "Their marks are replaced by the new result. Marks you set "
                + "by hand are kept; fixes are made again and wait for you "
                + "to keep them.", "Grade again",
                () -> retrySubmission(backend));
    }

    // Asks before grading one question of the shown student again.
    private void askRetryQuestion(DashboardBackend backend,
            Question question) {
        window.confirm("Grade " + question.getId() + " again?", "The mark "
                + "for " + question.getId() + " is replaced by the new "
                + "result. Other marks, fixes and flags stay, and a mark "
                + "you set by hand is kept.", "Grade again",
                () -> retryQuestion(backend, question));
    }

    /**
     * Opens the code and test output sheet on one question.
     *
     * @param questionId the question shown first, such as Q1a
     */
    private void openCode(String questionId) {
        window.showSheet(CodeSheet.create(window, submission, questionId));
    }

    /**
     * Grades the shown student again from the start. Live mode runs the
     * testers, so it grades on a background thread.
     *
     * @param backend the backend that grades
     */
    private void retrySubmission(DashboardBackend backend) {
        if (backend.isDemo()) {
            applyRegrade(backend.retrySubmission(
                    window.getState().getRun(), submission));
            return;
        }
        startRegradeJob(new RegradeJob(backend, window.getState().getRun(),
                submission));
    }

    /**
     * Grades one question of the shown student again. Live mode runs the
     * tester, so it grades on a background thread.
     *
     * @param backend the backend that grades
     * @param question the question
     */
    private void retryQuestion(DashboardBackend backend, Question question) {
        if (backend.isDemo()) {
            applyRegrade(backend.retryQuestion(window.getState().getRun(),
                    submission, question.getId()));
            return;
        }
        startRegradeJob(new RegradeJob(backend, window.getState().getRun(),
                submission, question.getId()));
    }

    // Starts grading again in the background; the panel says so until it
    // ends. Only one such job runs at a time. The job forgets an earlier
    // export of the run itself.
    private void startRegradeJob(RegradeJob job) {
        // The outcome is always told, as the instructor may have moved on
        boolean started = job.startOnce(window.getState(),
                result -> apply(result, true), null);
        if (started) {
            changed();
            return;
        }
        window.notify("Wait until grading again or undoing a fix has "
                + "finished.");
    }

    /**
     * Puts back a mark that was changed by hand.
     *
     * @param backend the backend that keeps the marks
     * @param change the change to undo
     */
    private void undoAdjustment(DashboardBackend backend,
            ScoreAdjustment change) {
        ActionResult result = backend.undoAdjustment(
                window.getState().getRun(), change);
        if (result.isDone()) {
            String detail = AdjustMarkSheet.describe(change);
            ActivityUndo.forget(ActivityUndo.MARK_CHANGED, detail);
            backend.recordActivity(ActivityUndo.MARK_CHANGE_UNDONE, detail);
        }
        apply(result, true);
    }

    /**
     * Approves a fix made for the student. When other students have the
     * same kind of fix waiting, asks first whether to keep them too.
     *
     * @param backend the backend that keeps the fixes
     * @param repair the fix
     */
    private void approveRepair(DashboardBackend backend, RepairItem repair) {
        List<RepairItem> others = SameFixes.find(backend,
                window.getState().getRun(), repair);
        if (others.isEmpty()) {
            keepRepair(backend, repair);
            return;
        }
        sameFixes.askKeep(repair, others,
                () -> keepRepair(backend, repair));
    }

    // Approves just this fix, and offers Undo.
    private void keepRepair(DashboardBackend backend, RepairItem repair) {
        ActionResult result = backend.approveRepair(repair);
        apply(result, false);
        boolean canUndo = result.isDone()
                && backend.supports(Capability.REPAIR_DECISION_UNDO);
        if (canUndo) {
            String username = repair.getAnomaly().getUsername();
            window.notify("Kept the fix for " + username + ".", "Undo",
                    () -> undoRepairDecision(backend, repair));
        }
    }

    // Says whether a kept fix can be taken back. A rejected fix is
    // applied again instead, as undoing it graded the student again.
    private boolean canTakeBack(DashboardBackend backend, Anomaly anomaly) {
        return anomaly.getFixStatus() == FixStatus.APPROVED
                && backend.supports(Capability.REPAIR_DECISION_UNDO);
    }

    // Returns the Mark as checked button of a flag or problem.
    private Button checkedButton(DashboardBackend backend, Anomaly anomaly) {
        return Parts.small("Mark as checked",
                () -> decide(backend, anomaly, ReviewDecision.REVIEWED),
                Tone.GOOD);
    }

    // Returns the Undo button of a settled flag or problem, which puts it
    // back to be checked again.
    private Button undoButton(DashboardBackend backend, Anomaly anomaly) {
        Button undo = Parts.small("Undo",
                () -> decide(backend, anomaly, ReviewDecision.OPEN));
        Parts.tip(undo, "Take back this decision, so it waits for you "
                + "again. No mark changes.");
        return undo;
    }

    /**
     * Undoes a fix made for the student. When other students have the
     * same kind of fix waiting, asks first whether to reject them too.
     *
     * @param backend the backend that keeps the fixes
     * @param repair the fix
     */
    private void rejectRepair(DashboardBackend backend, RepairItem repair) {
        List<RepairItem> others = SameFixes.find(backend,
                window.getState().getRun(), repair);
        if (others.isEmpty()) {
            rejectOne(backend, repair);
            return;
        }
        sameFixes.askReject(repair, others,
                () -> rejectOne(backend, repair));
    }

    // Undoes just this fix. Live mode grades the student again, so it
    // runs on a background thread and the card says so until it ends.
    private void rejectOne(DashboardBackend backend, RepairItem repair) {
        if (backend.isDemo()) {
            apply(backend.rejectRepair(repair), false);
            return;
        }
        startFixJob(backend, repair, false);
    }

    // Applies a rejected fix again; live mode does it in the background.
    private void reapplyRepair(DashboardBackend backend,
            RepairItem repair) {
        if (backend.isDemo()) {
            apply(backend.reapplyRepair(repair), false);
            return;
        }
        startFixJob(backend, repair, true);
    }

    // Undoes a fix, or applies it again, on a background thread. The
    // card says so until it ends. Only one runs at a time.
    private void startFixJob(DashboardBackend backend, RepairItem repair,
            boolean applyAgain) {
        FlowState state = window.getState();
        UndoFixJob job = new UndoFixJob(backend, repair, applyAgain);
        // The outcome is always told, as the instructor may have moved on
        boolean started = job.startOnce(state, state.getRun(),
                result -> apply(result, true));
        if (started) {
            refresh();
        }
    }

    /**
     * Puts a settled fix back to waiting for a decision.
     *
     * @param backend the backend that keeps the fixes
     * @param repair the fix
     */
    private void undoRepairDecision(DashboardBackend backend,
            RepairItem repair) {
        // Says the fix waits again, as Keep and Reject say what they did
        apply(backend.undoRepairDecision(repair), true);
    }

    /**
     * Records a decision on a flag or problem.
     *
     * @param backend the backend that keeps the decisions
     * @param anomaly the flag or problem
     * @param decision the decision
     */
    private void decide(DashboardBackend backend, Anomaly anomaly,
            ReviewDecision decision) {
        ReviewDecision before = backend.getDecision(anomaly);
        ActionResult result = backend.setDecision(anomaly, decision);
        apply(result, false);
        // A decision says what was done and offers to take it back
        if (result.isDone() && decision != ReviewDecision.OPEN) {
            boolean flag = Problems.isFlag(anomaly.getType());
            window.notify(decisionNotice(decision, flag), "Undo",
                    () -> decide(backend, anomaly, before));
        }
    }

    // Says in a notice what a decision did: a flag that is reviewed was
    // kept flagged, any other problem was marked as checked.
    private String decisionNotice(ReviewDecision decision, boolean flag) {
        if (decision == ReviewDecision.REJECTED) {
            return "Marked as looks fine.";
        } else if (decision == ReviewDecision.REVIEWED && flag) {
            return "Kept flagged.";
        }
        return "Marked as checked.";
    }

    // ----------------------------------------------------------- update

    // Shows the outcome of an action and redraws. Changes to marks
    // always show their message; decisions only show a problem.
    private void apply(ActionResult result, boolean alwaysTell) {
        if (alwaysTell || !result.isDone()) {
            window.notify(result.getMessage());
        }
        changed();
    }

    // Shows the outcome of grading again. New marks make an earlier
    // export of the run out of date.
    private void applyRegrade(ActionResult result) {
        if (result.isDone()) {
            FlowState state = window.getState();
            state.forgetExport(state.getRun());
        }
        apply(result, true);
    }

    // Redraws the panel and tells the table.
    private void changed() {
        refresh();
        onChanged.run();
    }
}
