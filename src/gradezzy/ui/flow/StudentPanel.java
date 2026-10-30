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

import java.util.List;

import javafx.geometry.Pos;
import javafx.scene.control.Button;
import javafx.scene.control.Hyperlink;
import javafx.scene.control.Label;
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
import gradezzy.model.Assessment;
import gradezzy.model.FixStatus;
import gradezzy.model.GradingRun;
import gradezzy.model.Question;
import gradezzy.model.QuestionResult;
import gradezzy.model.Submission;
import gradezzy.model.TestCaseResult;
import gradezzy.ui.backend.ActionResult;
import gradezzy.ui.backend.Capability;
import gradezzy.ui.backend.DashboardBackend;
import gradezzy.ui.backend.RepairItem;
import gradezzy.ui.backend.ReviewDecision;
import gradezzy.ui.backend.ReviewRules;
import gradezzy.ui.backend.ScoreAdjustment;

/**
 * Details of one student on the Review step.
 */
public class StudentPanel extends ScrollPane {

    // The width of the panel
    private static final double WIDTH = 360.0;

    // The size of the close icon
    private static final double CLOSE_SIZE = 16.0;

    // The width of the question name column
    private static final double ID_WIDTH = 40.0;

    // The width of the mark column, such as "2 / 3"
    private static final double MARK_WIDTH = 44.0;

    // The gap between the columns of a question row
    private static final double ROW_GAP = 10.0;

    // The gap between the small parts of a card
    private static final double TIGHT_GAP = 4.0;

    // The gap between a title and the line under it
    private static final double LINE_GAP = 2.0;

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
        submission = shown;
        setVvalue(0);
        refresh();
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
        if (submission == null || result == null || !window.getBackend()
                .supports(Capability.SCORE_ADJUSTMENT)) {
            return;
        }
        window.showSheet(AdjustMarkSheet.create(window, submission, result,
                () -> changed()));
    }

    /**
     * Redraws the panel for the student shown.
     */
    public void refresh() {
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
        Label who = Parts.muted(submission.getUsername() + " · "
                + submission.getSection().getLabel());
        who.getStyleClass().add("small-text");
        VBox names = new VBox(name, who);
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

    // Builds the large total, "out of" and the status pill on one line.
    private HBox createTotal() {
        Assessment assessment = window.getBackend().getAssessment();
        Label total = new Label(Parts.mark(submission.getTotalScore()));
        total.getStyleClass().add("big-total");
        total.setMinWidth(Region.USE_PREF_SIZE);
        Label max = Parts.muted("");
        if (assessment != null) {
            max.setText("out of " + Parts.mark(assessment.getMaxTotal()));
        }
        Label pill = Students.pill(submission, window.getBackend());
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
        Assessment assessment = backend.getAssessment();
        if (assessment == null) {
            return list;
        }
        for (Question question : assessment.getQuestions()) {
            list.getChildren().add(createQuestion(question,
                    findResult(question.getId()), backend));
        }
        return list;
    }

    // Builds the row of one question: name, bar, mark and Adjust, with
    // the reason, the test squares and Grade again shown on a click.
    private VBox createQuestion(Question question, QuestionResult result,
            DashboardBackend backend) {
        double score = 0;
        if (result != null) {
            score = result.getScore();
        }
        double max = question.getMaxScore();

        Label id = Parts.strong(question.getId());
        fixWidth(id, ID_WIDTH);
        StackPane bar = Parts.track(score / max, toneOf(score, max,
                result));
        Label mark = new Label(Parts.mark(score) + " / " + Parts.mark(max));
        mark.getStyleClass().add("question-mark");
        fixWidth(mark, MARK_WIDTH);
        mark.setAlignment(Pos.CENTER_RIGHT);
        HBox top = new HBox(ROW_GAP, id, bar, mark);
        top.setAlignment(Pos.CENTER_LEFT);
        top.getStyleClass().add("question-top");
        if (result != null && backend.supports(
                Capability.SCORE_ADJUSTMENT)) {
            Hyperlink adjust = Parts.link("Adjust",
                    () -> openAdjust(question.getId()));
            adjust.getStyleClass().add("small-text");
            adjust.addEventHandler(MouseEvent.MOUSE_CLICKED,
                    event -> event.consume());
            top.getChildren().add(adjust);
        }

        VBox details = createDetails(question, result, backend);
        VBox row = new VBox(top);
        row.getStyleClass().add("question-row");
        if (details.getChildren().isEmpty()) {
            return row;
        }
        details.setVisible(false);
        details.setManaged(false);
        row.getChildren().add(details);
        row.getStyleClass().add("clickable");
        top.setOnMouseClicked(event -> toggleDetails(details));
        Tooltip.install(top, new Tooltip("Show the tests"));
        return row;
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
        String reason = reasonFor(result);
        if (!reason.isEmpty()) {
            Label why = Parts.muted(reason);
            why.getStyleClass().add("small-text");
            details.getChildren().add(why);
        }
        if (!result.getTestCaseResults().isEmpty()) {
            details.getChildren().add(createTests(
                    result.getTestCaseResults()));
        }
        if (backend.supports(Capability.RETRY_QUESTION)) {
            Hyperlink retry = Parts.link("Grade " + question.getId()
                    + " again", () -> retryQuestion(backend, question));
            retry.getStyleClass().add("small-text");
            details.getChildren().add(retry);
        }
        return details;
    }

    // Builds one small square per test case.
    private FlowPane createTests(List<TestCaseResult> results) {
        FlowPane tests = new FlowPane(TIGHT_GAP, TIGHT_GAP);
        tests.getStyleClass().add("test-squares");
        for (TestCaseResult test : results) {
            String outcome = "failed";
            if (test.isPassed()) {
                outcome = "passed";
            }
            Region square = new Region();
            square.getStyleClass().addAll("test-square", outcome);
            String detail = "Test " + test.getTestNumber() + ": " + outcome;
            if (test.getDetail() != null && !test.getDetail().isEmpty()) {
                detail = detail + "\n" + test.getDetail();
            }
            Tooltip.install(square, new Tooltip(detail));
            tests.getChildren().add(square);
        }
        return tests;
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

    // Returns the short reason for a question's mark.
    private String reasonFor(QuestionResult result) {
        if (!result.isCompiled()) {
            return "Did not compile.";
        }
        if (result.isTimedOut()) {
            return "A test ran too long and was stopped.";
        }
        int failed = 0;
        int all = result.getTestCaseResults().size();
        for (TestCaseResult test : result.getTestCaseResults()) {
            if (!test.isPassed()) {
                failed++;
            }
        }
        if (all == 0) {
            return "";
        }
        if (failed == 0) {
            return "Every test passed.";
        }
        return failed + " of " + Parts.plural(all, "test") + " failed.";
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
        List<ScoreAdjustment> changes = backend.listAdjustments(submission);
        if (changes.isEmpty()) {
            return;
        }
        VBox card = new VBox(Parts.GAP_SMALL);
        card.getStyleClass().add("fix-card");
        card.getChildren().add(Parts.pill(Parts.plural(changes.size(),
                "mark") + " changed by hand", Tone.NEUTRAL));
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
        HBox line = new HBox(Parts.GAP, text, Parts.small("Undo",
                () -> undoAdjustment(backend, change)));
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
        for (RepairItem repair : backend.listRepairs(run)) {
            if (repair.getAnomaly().getUsername().equals(
                    submission.getUsername())) {
                body.getChildren().add(createFixCard(repair, backend));
            }
        }
    }

    // Builds the card of one fix.
    private VBox createFixCard(RepairItem repair, DashboardBackend backend) {
        Anomaly anomaly = repair.getAnomaly();
        FixStatus status = anomaly.getFixStatus();
        Label tag;
        if (status == FixStatus.APPROVED) {
            tag = Parts.pill("Fix approved", Tone.GOOD);
        } else if (status == FixStatus.UNDONE) {
            tag = Parts.pill("Fix undone", Tone.NEUTRAL);
        } else if (status == FixStatus.APPLIED) {
            tag = Parts.pill("Fixed for you", Tone.WARN);
        } else {
            tag = Parts.pill("Could not fix", Tone.BAD);
        }
        VBox card = createCard(tag, anomaly);

        String before = repair.getOriginalValue();
        if (backend.supports(Capability.REPAIR_VALUES) && before != null
                && !before.isEmpty()) {
            Label change = Parts.muted("Before: " + before
                    + "\nAfter:  " + repair.getProposedValue());
            change.setWrapText(true);
            change.getStyleClass().add("change-text");
            card.getChildren().add(change);
        }

        HBox buttons = Parts.row(Parts.GAP_SMALL);
        if (repair.isBlocked()) {
            Label note = Parts.muted("This fix cannot be changed now.");
            note.getStyleClass().add("small-text");
            buttons.getChildren().add(note);
        } else if (status == FixStatus.APPLIED) {
            buttons.getChildren().addAll(
                    Parts.small("Approve",
                    () -> approveRepair(backend, repair)),
                    Parts.small("Undo",
                    () -> rejectRepair(backend, repair)));
        } else if (ReviewRules.isRepairSettled(anomaly)
                && backend.supports(Capability.REPAIR_DECISION_UNDO)) {
            buttons.getChildren().add(Parts.small("Change decision",
                    () -> undoRepairDecision(backend, repair)));
        }
        addButtons(card, buttons);
        return card;
    }

    // ---------------------------------------------------------- problems

    // Adds a card for each flag and problem that is not a fix.
    private void addProblems(DashboardBackend backend) {
        for (Anomaly anomaly : submission.getAnomalies()) {
            if (!ReviewRules.isValidationFinding(anomaly.getType())) {
                body.getChildren().add(createProblemCard(anomaly, backend));
            }
        }
    }

    // Builds the card of one flag or problem.
    private VBox createProblemCard(Anomaly anomaly,
            DashboardBackend backend) {
        boolean flag = Problems.isFlag(anomaly.getType());
        ReviewDecision decision = backend.getDecision(anomaly);
        boolean open = backend.needsReview(anomaly);
        Label tag;
        if (!open) {
            tag = Parts.pill(decisionWords(decision, flag), Tone.NEUTRAL);
        } else if (flag) {
            tag = Parts.pill("Flag", Tone.WARN);
        } else {
            tag = Parts.pill("Problem", Tone.BAD);
        }
        VBox card = createCard(tag, anomaly);
        if (!backend.supports(Capability.FLAG_REVIEW)) {
            return card;
        }

        HBox buttons = Parts.row(Parts.GAP_SMALL);
        if (open && flag) {
            buttons.getChildren().addAll(
                    Parts.small("Looks fine", () -> decide(backend,
                    anomaly, ReviewDecision.REJECTED)),
                    Parts.small("Keep flagged", () -> decide(backend,
                    anomaly, ReviewDecision.REVIEWED)));
        } else if (open) {
            buttons.getChildren().add(Parts.small("Mark as checked",
                    () -> decide(backend, anomaly,
                    ReviewDecision.REVIEWED)));
        } else if (decision != ReviewDecision.OPEN) {
            buttons.getChildren().add(Parts.small("Reopen",
                    () -> decide(backend, anomaly, ReviewDecision.OPEN)));
        }
        addButtons(card, buttons);
        return card;
    }

    // Builds a card with its tag, title and detail.
    private VBox createCard(Label tag, Anomaly anomaly) {
        Label title = Parts.strong(Problems.title(anomaly.getType()));
        title.setWrapText(true);
        Label detail = Parts.muted(anomaly.getMessage());
        detail.setWrapText(true);
        detail.getStyleClass().add("small-text");
        VBox text = new VBox(LINE_GAP, title, detail);
        VBox card = new VBox(Parts.GAP_SMALL, tag, text);
        card.getStyleClass().add("fix-card");
        card.setAlignment(Pos.TOP_LEFT);
        return card;
    }

    // Adds a row of buttons to a card, unless it is empty.
    private void addButtons(VBox card, HBox buttons) {
        if (!buttons.getChildren().isEmpty()) {
            buttons.getStyleClass().add("card-buttons");
            card.getChildren().add(buttons);
        }
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
        if (backend.supports(Capability.RETRY_SUBMISSION)) {
            links.getChildren().add(Parts.link("Grade this student again",
                    () -> retrySubmission(backend)));
        }
        return links;
    }

    // ---------------------------------------------------------- actions

    /**
     * Opens the sheet with the shown student's code and test output.
     */
    private void openCode() {
        window.showSheet(CodeSheet.create(window, submission));
    }

    /**
     * Grades the shown student again from the start.
     *
     * @param backend the backend that grades
     */
    private void retrySubmission(DashboardBackend backend) {
        apply(backend.retrySubmission(submission), true);
    }

    /**
     * Grades one question of the shown student again.
     *
     * @param backend the backend that grades
     * @param question the question
     */
    private void retryQuestion(DashboardBackend backend, Question question) {
        apply(backend.retryQuestion(submission, question.getId()), true);
    }

    /**
     * Puts back a mark that was changed by hand.
     *
     * @param backend the backend that keeps the marks
     * @param change the change to undo
     */
    private void undoAdjustment(DashboardBackend backend,
            ScoreAdjustment change) {
        apply(backend.undoAdjustment(change), true);
    }

    /**
     * Approves a fix made for the student.
     *
     * @param backend the backend that keeps the fixes
     * @param repair the fix
     */
    private void approveRepair(DashboardBackend backend, RepairItem repair) {
        apply(backend.approveRepair(repair), false);
    }

    /**
     * Undoes a fix made for the student.
     *
     * @param backend the backend that keeps the fixes
     * @param repair the fix
     */
    private void rejectRepair(DashboardBackend backend, RepairItem repair) {
        apply(backend.rejectRepair(repair), false);
    }

    /**
     * Puts a settled fix back to waiting for a decision.
     *
     * @param backend the backend that keeps the fixes
     * @param repair the fix
     */
    private void undoRepairDecision(DashboardBackend backend,
            RepairItem repair) {
        apply(backend.undoRepairDecision(repair), false);
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
        apply(backend.setDecision(anomaly, decision), false);
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

    // Redraws the panel and tells the table.
    private void changed() {
        refresh();
        onChanged.run();
    }
}
