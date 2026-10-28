/*
 * File: StudentPanel.java
 *
 * Purpose: The panel beside the Review table for one student: the
 * total, each question's mark with its tests, the fixes made for the
 * student with Approve and Undo, and the flags and problems found.
 *
 * Where it fits: Replaces the old Student Evidence screen and anomaly
 * inspector. ReviewScreen shows it when a row is clicked and is told
 * through a Runnable when a decision changes a status.
 */
package gradezzy.ui.flow;

import java.util.List;

import javafx.scene.control.Button;
import javafx.scene.control.Label;
import javafx.scene.control.ProgressBar;
import javafx.scene.control.ScrollPane;
import javafx.scene.control.Tooltip;
import javafx.scene.layout.FlowPane;
import javafx.scene.layout.HBox;
import javafx.scene.layout.Priority;
import javafx.scene.layout.Region;
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

/**
 * Details of one student on the Review step.
 */
public class StudentPanel extends ScrollPane {

    // The width of the panel
    private static final double WIDTH = 380.0;

    // The size of the close icon
    private static final double CLOSE_SIZE = 16.0;

    // The size of the code icon
    private static final double CODE_ICON_SIZE = 16.0;

    // The window, for sheets and notices
    private final FlowWindow window;

    // Called after a decision so the table can redraw the status
    private final Runnable onChanged;

    // The panel's content
    private final VBox body = new VBox(Parts.GAP + Parts.GAP_SMALL / 2);

    // The student shown, or null
    private Submission submission = null;

    /**
     * Creates an empty panel.
     *
     * @param window the window
     * @param onChanged called after a decision changes something
     */
    public StudentPanel(FlowWindow window, Runnable onChanged) {
        this.window = window;
        this.onChanged = onChanged;
        getStyleClass().add("student-panel");
        body.getStyleClass().add("student-panel-body");
        setContent(body);
        setFitToWidth(true);
        setPrefWidth(WIDTH);
        setMinWidth(WIDTH);
        setMaxWidth(WIDTH);
    }

    /**
     * Shows a student.
     *
     * @param shown the student
     */
    public void show(Submission shown) {
        submission = shown;
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
     * Redraws the panel for the student shown.
     */
    public void refresh() {
        body.getChildren().clear();
        if (submission == null) {
            return;
        }
        DashboardBackend backend = window.getBackend();
        body.getChildren().addAll(createTop(), createTotal(),
                createQuestions());
        addFixes(backend);
        addProblems(backend);
        if (backend.supports(Capability.SOURCE_VIEW)) {
            HBox open = Parts.row(Parts.GAP_SMALL / 2,
                    Parts.icon(Parts.CODE, CODE_ICON_SIZE),
                    Parts.link("Open code and test output",
                    () -> window.showSheet(CodeSheet.create(window,
                    submission))));
            body.getChildren().add(open);
        }
    }

    // Builds the name, username and close button.
    private HBox createTop() {
        Label name = Parts.title(Students.name(submission));
        name.getStyleClass().add("panel-name");
        VBox who = new VBox(name, Parts.muted(submission.getUsername()
                + "  ·  " + submission.getSection().getLabel()));
        HBox.setHgrow(who, Priority.ALWAYS);
        Button close = new Button();
        close.setGraphic(Parts.icon(Parts.CLOSE, CLOSE_SIZE));
        close.getStyleClass().addAll("flat", "icon-button");
        close.setOnAction(event -> {
            submission = null;
            setVisible(false);
            setManaged(false);
        });
        return Parts.row(Parts.GAP, who, close);
    }

    // Builds the large total and the status.
    private HBox createTotal() {
        Assessment assessment = window.getBackend().getAssessment();
        Label total = new Label(Parts.mark(submission.getTotalScore()));
        total.getStyleClass().add("big-total");
        String max = "";
        if (assessment != null) {
            max = "out of " + Parts.mark(assessment.getMaxTotal());
        }
        HBox row = Parts.row(Parts.GAP_SMALL, total, Parts.muted(max),
                Parts.grow(), Students.pill(submission,
                window.getBackend()));
        return row;
    }

    // Builds one row per question; clicking one shows its tests.
    private VBox createQuestions() {
        VBox list = new VBox(Parts.GAP_SMALL);
        Assessment assessment = window.getBackend().getAssessment();
        if (assessment == null) {
            return list;
        }
        for (Question question : assessment.getQuestions()) {
            list.getChildren().add(createQuestion(question,
                    findResult(question.getId())));
        }
        return list;
    }

    // Builds the row of one question.
    private VBox createQuestion(Question question, QuestionResult result) {
        double score = 0;
        String reason = "Not graded";
        if (result != null) {
            score = result.getScore();
            reason = reasonFor(result);
        }
        Label id = Parts.strong(question.getId());
        id.setMinWidth(Region.USE_PREF_SIZE);
        Label why = Parts.muted(reason);
        HBox.setHgrow(why, Priority.ALWAYS);
        why.setMaxWidth(Double.MAX_VALUE);
        HBox top = Parts.row(Parts.GAP, id, why,
                Parts.strong(Parts.mark(score) + " / "
                + Parts.mark(question.getMaxScore())));
        ProgressBar bar = new ProgressBar(score / question.getMaxScore());
        bar.setMaxWidth(Double.MAX_VALUE);
        bar.getStyleClass().add("score-bar");
        VBox row = new VBox(Parts.GAP_SMALL / 2, top, bar);
        row.getStyleClass().add("question-row");

        if (result != null && !result.getTestCaseResults().isEmpty()) {
            FlowPane tests = createTests(result.getTestCaseResults());
            tests.setVisible(false);
            tests.setManaged(false);
            row.getChildren().add(tests);
            row.getStyleClass().add("clickable");
            row.setOnMouseClicked(event -> {
                tests.setVisible(!tests.isVisible());
                tests.setManaged(tests.isVisible());
            });
            Tooltip.install(row, new Tooltip("Show the tests"));
        }
        return row;
    }

    // Builds one small square per test case.
    private FlowPane createTests(List<TestCaseResult> results) {
        FlowPane tests = new FlowPane(Parts.GAP_SMALL / 2,
                Parts.GAP_SMALL / 2);
        tests.getStyleClass().add("test-squares");
        for (TestCaseResult test : results) {
            Region square = new Region();
            square.getStyleClass().addAll("test-square",
                    test.isPassed() ? "passed" : "failed");
            String detail = "Test " + test.getTestNumber() + ": "
                    + (test.isPassed() ? "passed" : "failed");
            if (test.getDetail() != null && !test.getDetail().isEmpty()) {
                detail = detail + "\n" + test.getDetail();
            }
            Tooltip.install(square, new Tooltip(detail));
            tests.getChildren().add(square);
        }
        return tests;
    }

    // Returns the short reason under a question's mark.
    private String reasonFor(QuestionResult result) {
        if (!result.isCompiled()) {
            return "Did not compile";
        }
        if (result.isTimedOut()) {
            return "Timed out";
        }
        int failed = 0;
        int all = result.getTestCaseResults().size();
        for (TestCaseResult test : result.getTestCaseResults()) {
            if (!test.isPassed()) {
                failed++;
            }
        }
        if (failed == 0) {
            return "";
        }
        return failed + " of " + Parts.plural(all, "test") + " failed";
    }

    // Returns the result of one question, or null.
    private QuestionResult findResult(String questionId) {
        for (QuestionResult result : submission.getQuestionResults()) {
            if (result.getQuestion().getId().equals(questionId)) {
                return result;
            }
        }
        return null;
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
        VBox card = new VBox(Parts.GAP_SMALL / 2);
        card.getStyleClass().add("fix-card");

        Label tag;
        if (status == FixStatus.APPROVED) {
            tag = Parts.pill("Fix approved", Tone.GOOD);
        } else if (status == FixStatus.UNDONE) {
            tag = Parts.pill("Fix undone", Tone.NEUTRAL);
        } else if (status == FixStatus.APPLIED) {
            tag = Parts.pill("Fixed for you", Tone.WARN);
            card.getStyleClass().add("open");
        } else {
            tag = Parts.pill("Could not fix", Tone.BAD);
        }
        Label title = Parts.strong(Problems.title(anomaly.getType()));
        title.setWrapText(true);
        Label detail = Parts.muted(anomaly.getMessage());
        detail.setWrapText(true);
        card.getChildren().addAll(tag, title, detail);

        String before = repair.getOriginalValue();
        if (backend.supports(Capability.REPAIR_VALUES) && before != null
                && !before.isEmpty()) {
            Label change = Parts.muted("Before: " + before
                    + "\nAfter: " + repair.getProposedValue());
            change.setWrapText(true);
            change.getStyleClass().add("change-text");
            card.getChildren().add(change);
        }

        HBox buttons = Parts.row(Parts.GAP_SMALL);
        if (repair.isBlocked()) {
            buttons.getChildren().add(Parts.muted("This fix cannot be "
                    + "changed now."));
        } else if (status == FixStatus.APPLIED) {
            buttons.getChildren().addAll(
                    Parts.small("Approve", () -> apply(
                    backend.approveRepair(repair))),
                    Parts.small("Undo", () -> apply(
                    backend.rejectRepair(repair))));
        } else if (ReviewRules.isRepairSettled(anomaly)
                && backend.supports(Capability.REPAIR_DECISION_UNDO)) {
            buttons.getChildren().add(Parts.small("Change decision",
                    () -> apply(backend.undoRepairDecision(repair))));
        }
        if (!buttons.getChildren().isEmpty()) {
            card.getChildren().add(buttons);
        }
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
        VBox card = new VBox(Parts.GAP_SMALL / 2);
        card.getStyleClass().add("fix-card");

        Label tag;
        if (!open) {
            tag = Parts.pill(decisionWords(decision, flag), Tone.NEUTRAL);
        } else if (flag) {
            tag = Parts.pill("Flag", Tone.WARN);
            card.getStyleClass().add("open");
        } else {
            tag = Parts.pill("Problem", Tone.BAD);
            card.getStyleClass().add("open-bad");
        }
        Label title = Parts.strong(Problems.title(anomaly.getType()));
        title.setWrapText(true);
        Label detail = Parts.muted(anomaly.getMessage());
        detail.setWrapText(true);
        card.getChildren().addAll(tag, title, detail);

        if (!backend.supports(Capability.FLAG_REVIEW)) {
            return card;
        }
        HBox buttons = Parts.row(Parts.GAP_SMALL);
        if (open && flag) {
            buttons.getChildren().addAll(
                    Parts.small("Looks fine", () -> apply(
                    backend.setDecision(anomaly, ReviewDecision.REJECTED))),
                    Parts.small("Keep flagged", () -> apply(
                    backend.setDecision(anomaly, ReviewDecision.REVIEWED))));
        } else if (open) {
            buttons.getChildren().add(Parts.small("Mark as checked",
                    () -> apply(backend.setDecision(anomaly,
                    ReviewDecision.REVIEWED))));
        } else if (decision != ReviewDecision.OPEN) {
            buttons.getChildren().add(Parts.small("Reopen", () -> apply(
                    backend.setDecision(anomaly, ReviewDecision.OPEN))));
        }
        if (!buttons.getChildren().isEmpty()) {
            card.getChildren().add(buttons);
        }
        return card;
    }

    // Returns the words for a settled flag or problem.
    private String decisionWords(ReviewDecision decision, boolean flag) {
        if (decision == ReviewDecision.REJECTED) {
            return "Looks fine";
        } else if (decision == ReviewDecision.REVIEWED && flag) {
            return "Kept flagged";
        } else if (decision == ReviewDecision.RESOLVED) {
            return "Resolved by retry";
        }
        return "Checked";
    }

    // Shows the outcome of a decision and redraws.
    private void apply(ActionResult result) {
        if (!result.isDone()) {
            window.notify(result.getMessage());
        }
        refresh();
        onChanged.run();
    }
}
