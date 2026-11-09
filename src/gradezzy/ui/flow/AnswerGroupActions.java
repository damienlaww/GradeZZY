/*
 * File: AnswerGroupActions.java
 *
 * Purpose: What the marker can do to a whole answer group at once: name
 * the mistake, change the mark of every student in it, mark it as
 * checked, and step through the students' code.
 *
 * Where it fits: MistakesView puts its buttons on each group's row.
 * Mark changes and checks reuse the per-student adjustScore and
 * setDecision of DashboardBackend, so each one is saved, logged and
 * undone like a single one. Each action applies only to the students
 * the sheet listed who are still in the group when it is confirmed.
 */
package gradezzy.ui.flow;

import java.util.ArrayList;
import java.util.List;

import javafx.geometry.Pos;
import javafx.scene.Node;
import javafx.scene.control.Button;
import javafx.scene.control.Label;
import javafx.scene.control.TextField;
import javafx.scene.layout.HBox;
import javafx.scene.layout.VBox;

import gradezzy.model.Anomaly;
import gradezzy.model.AnswerGroup;
import gradezzy.model.FixStatus;
import gradezzy.model.GradingRun;
import gradezzy.model.Question;
import gradezzy.model.QuestionResult;
import gradezzy.model.ScoreAdjustment;
import gradezzy.model.Submission;
import gradezzy.stats.AnswerGrouper;
import gradezzy.ui.backend.ActionResult;
import gradezzy.ui.backend.DashboardBackend;
import gradezzy.ui.backend.ReviewDecision;

/**
 * The actions on one answer group.
 *
 * <p>Tier: COULD.
 */
public class AnswerGroupActions {

    // The width of the sheets' text
    private static final double SHEET_WIDTH = 520.0;

    // The height of a student's row in the mark sheet
    private static final double ROW_HEIGHT = 48.0;

    // The width of the mark column in the mark sheet
    private static final double MARK_COLUMN = 90.0;

    // The width of the new mark field
    private static final double MARK_WIDTH = 72.0;

    // The width of the name field, in characters
    private static final int NAME_COLUMNS = 32;

    // The gap between a field's name and the field
    private static final double LABEL_GAP = 4.0;

    // The window, for the sheets and notices
    private final FlowWindow window;

    // Called after a group changes, so the view and table redraw
    private final Runnable onChanged;

    /**
     * Creates the actions for one Review screen.
     *
     * <p>Tier: COULD.
     *
     * @param window the window
     * @param onChanged called after a name, mark or check changes
     */
    public AnswerGroupActions(FlowWindow window, Runnable onChanged) {
        this.window = window;
        this.onChanged = onChanged;
    }

    // ------------------------------------------------------------- name

    /**
     * Opens the sheet that names the group's mistake.
     *
     * <p>Tier: COULD.
     *
     * @param group the group
     */
    public void name(AnswerGroup group) {
        TextField field = new TextField(group.getLabel());
        field.setId("group-name");
        field.setPrefColumnCount(NAME_COLUMNS);
        field.setPromptText("For example: uses < instead of <=");
        field.setAccessibleText("Name of the mistake");
        Label about = wrapped(Parts.muted(describe(group) + ", "
                + Parts.plural(group.getUsernames().size(), "student")
                + ". The name shows on the group, in the common mistakes "
                + "export and in each student's JSON."));
        Button cancel = Parts.secondary("Cancel", () -> window.closeSheet());
        Button save = Parts.primary("Save name");
        save.setOnAction(event -> saveName(group, field));
        field.setOnAction(event -> saveName(group, field));
        showSheet(new Node[] {Parts.title("Name this mistake"), about,
            field("Name", field), buttons(cancel, save)});
        field.requestFocus();
    }

    // Saves the typed name and closes the sheet.
    private void saveName(AnswerGroup group, TextField field) {
        GradingRun run = window.getState().getRun();
        ActionResult result = window.getBackend().nameAnswerGroup(run,
                group, field.getText());
        window.closeSheet();
        window.notify(result.getMessage());
        onChanged.run();
    }

    // ------------------------------------------------------- change mark

    /**
     * Opens the sheet that changes the question's mark of every student
     * in the group, with one reason.
     *
     * <p>Tier: COULD.
     *
     * @param group the group
     */
    public void changeMark(AnswerGroup group) {
        GradingRun run = window.getState().getRun();
        Question question = questionOf(run, group);
        if (run == null || question == null) {
            return;
        }
        String questionId = question.getId();
        int count = group.getUsernames().size();
        List<String> listed = new ArrayList<>(group.getUsernames());

        PlainTable table = new PlainTable(ROW_HEIGHT);
        table.getStyleClass().add("compact");
        table.addColumn("Student", PlainTable.GROW, Pos.CENTER_LEFT);
        table.addColumn(questionId + " now", MARK_COLUMN, Pos.CENTER_RIGHT);
        for (String username : listed) {
            Submission submission = findSubmission(run, username);
            table.addRow(new Node[] {studentCell(submission, username),
                new Label(markNow(submission, questionId) + " / "
                + Parts.mark(question.getMaxScore()))});
        }

        TextField mark = new TextField();
        mark.setId("group-mark");
        mark.setPrefWidth(MARK_WIDTH);
        mark.setMaxWidth(MARK_WIDTH);
        mark.setAccessibleText("New mark");
        HBox markRow = Parts.row(Parts.GAP_SMALL, new Node[] {mark,
            Parts.muted("out of " + Parts.mark(question.getMaxScore()))});
        markRow.setAlignment(Pos.CENTER_LEFT);
        TextField reason = new TextField();
        reason.setId("group-reason");
        reason.setPromptText("For example: only the last case is wrong");
        reason.setAccessibleText("Reason for the change, required");
        Label problem = Parts.muted("");
        problem.getStyleClass().add("field-problem");
        wrapped(problem);
        showProblem(problem, "");

        Label about = wrapped(Parts.muted(group.getTitle() + ". Every "
                + "student below gets the same new mark for " + questionId
                + ", with the same reason. You can undo it."));
        Button cancel = Parts.secondary("Cancel", () -> window.closeSheet());
        Button save = Parts.primary("Change " + Parts.plural(count, "mark"));
        save.setOnAction(event -> saveMarks(run, group, listed, question,
                mark, reason, problem));
        showSheet(new Node[] {Parts.title("Change the " + questionId
            + " mark of " + Parts.plural(count, "student")), about, table,
            field("New mark", markRow), field("Reason", reason), problem,
            buttons(cancel, save)});
        mark.requestFocus();
    }

    // Checks the form, then changes the mark of each listed student who
    // is still in the group, and offers one Undo for all of them.
    private void saveMarks(GradingRun run, AnswerGroup group,
            List<String> listed, Question question, TextField mark,
            TextField reason, Label problem) {
        String error = AdjustMarkSheet.validate(mark.getText(),
                reason.getText(), question.getMaxScore());
        if (!error.isEmpty()) {
            showProblem(problem, error);
            return;
        }
        double newMark = Double.parseDouble(mark.getText().trim());
        String why = reason.getText().trim();
        DashboardBackend backend = window.getBackend();
        List<String> still = stillInGroup(run, group, listed);
        List<Submission> changed = new ArrayList<>();
        List<ScoreAdjustment> changes = new ArrayList<>();
        List<Integer> counts = new ArrayList<>();
        String firstProblem = "";
        for (String username : still) {
            Submission submission = findSubmission(run, username);
            List<ScoreAdjustment> before = changesOf(run, submission,
                    question.getId());
            ActionResult result = backend.adjustScore(run, submission,
                    question.getId(), newMark, why);
            ScoreAdjustment made = findNew(changesOf(run, submission,
                    question.getId()), before);
            if (result.isDone() && made != null) {
                changed.add(submission);
                changes.add(made);
                counts.add(Integer.valueOf(before.size() + 1));
            } else if (firstProblem.isEmpty()) {
                firstProblem = " " + result.getMessage();
            }
        }
        window.closeSheet();
        String message = "Changed the mark of "
                + Parts.plural(changed.size(), "student") + "."
                + leftOut(listed.size() - still.size()) + firstProblem;
        if (changed.isEmpty()) {
            window.notify(message);
        } else {
            window.notify(message, "Undo",
                    () -> undoMarks(run, changed, changes, counts));
        }
        onChanged.run();
    }

    // Puts back each group mark change that is still the student's
    // newest change of the question, and says how many were left.
    private void undoMarks(GradingRun run, List<Submission> changed,
            List<ScoreAdjustment> changes, List<Integer> counts) {
        DashboardBackend backend = window.getBackend();
        int putBack = 0;
        int left = 0;
        for (int index = 0; index < changed.size(); index++) {
            ScoreAdjustment change = changes.get(index);
            List<ScoreAdjustment> now = changesOf(run, changed.get(index),
                    change.getQuestionId());
            if (now.contains(change)
                    && now.size() == counts.get(index).intValue()
                    && backend.undoAdjustment(run, change).isDone()) {
                putBack++;
            } else {
                left++;
            }
        }
        String message = "Put back " + Parts.plural(putBack, "mark") + ".";
        if (left == 1) {
            message += " 1 was changed again since and was left.";
        } else if (left > 1) {
            message += " " + left + " were changed again since and were "
                    + "left.";
        }
        window.notify(message);
        onChanged.run();
    }

    // ------------------------------------------------------------ check

    /**
     * Asks before marking the group as checked, with the open items of
     * this question it checks, compile errors included; a checked group
     * is taken back at once.
     *
     * <p>Tier: COULD.
     *
     * @param group the group
     */
    public void check(AnswerGroup group) {
        GradingRun run = window.getState().getRun();
        Question question = questionOf(run, group);
        if (run == null || question == null) {
            return;
        }
        if (group.isChecked()) {
            ActionResult result = window.getBackend().setAnswerGroupChecked(
                    run, group, false);
            window.notify(result.getMessage());
            onChanged.run();
            return;
        }
        List<String> listed = new ArrayList<>(group.getUsernames());
        VBox content = new VBox(Parts.GAP_SMALL);
        Label about = Parts.muted(describe(group) + ". The group is marked "
                + "as checked. No mark changes, and you can undo it:");
        int openCount = 0;
        for (String username : listed) {
            openCount += openItems(findSubmission(run, username),
                    question).size();
        }
        if (openCount > 0) {
            about.setText(describe(group) + ". These " + question.getId()
                    + " items are marked as checked, compile errors "
                    + "included. Their rows will show as checked. No mark "
                    + "changes, and you can undo it:");
        }
        content.getChildren().add(wrapped(about));
        for (String username : listed) {
            content.getChildren().add(wrapped(new Label("• "
                    + describeStudent(run, username, question))));
        }
        window.confirm("Mark this group as checked?", content, "Mark "
                + listed.size() + " as checked",
                () -> confirmCheck(run, group, listed, question));
    }

    // Marks the group as checked, and checks the open items of this
    // question of each listed student still in it; offers Undo.
    private void confirmCheck(GradingRun run, AnswerGroup group,
            List<String> listed, Question question) {
        DashboardBackend backend = window.getBackend();
        AnswerGroup current = findCurrent(run, group);
        List<String> still = stillInGroup(run, group, listed);
        List<Anomaly> decided = new ArrayList<>();
        for (String username : still) {
            for (Anomaly anomaly : openItems(findSubmission(run, username),
                    question)) {
                if (backend.setDecision(anomaly,
                        ReviewDecision.REVIEWED).isDone()) {
                    decided.add(anomaly);
                }
            }
        }
        ActionResult result = backend.setAnswerGroupChecked(run, current,
                true);
        window.notify(result.getMessage() + leftOut(listed.size()
                - still.size()), "Undo",
                () -> undoCheck(run, current, decided));
        onChanged.run();
    }

    // Puts back the items a group check settled, and the group's tick.
    private void undoCheck(GradingRun run, AnswerGroup group,
            List<Anomaly> decided) {
        DashboardBackend backend = window.getBackend();
        for (Anomaly anomaly : decided) {
            if (backend.getDecision(anomaly) == ReviewDecision.REVIEWED) {
                backend.setDecision(anomaly, ReviewDecision.OPEN);
            }
        }
        ActionResult result = backend.setAnswerGroupChecked(run,
                findCurrent(run, group), false);
        window.notify(result.getMessage());
        onChanged.run();
    }

    // Returns a student's items about a question that still wait for a
    // look. A fix waiting for Keep or Reject is decided on its own card.
    private List<Anomaly> openItems(Submission submission,
            Question question) {
        List<Anomaly> open = new ArrayList<>();
        if (submission == null) {
            return open;
        }
        DashboardBackend backend = window.getBackend();
        for (Anomaly anomaly : submission.getAnomalies()) {
            if (anomaly.getFixStatus() != FixStatus.APPLIED
                    && backend.needsReview(anomaly)
                    && AnswerGrouper.isAbout(anomaly, submission,
                    question)) {
                open.add(anomaly);
            }
        }
        return open;
    }

    // Names a student with the open items of a question, such as "KAI
    // XUAN CHEN (kaixuan.chen.2024): Did not compile".
    private String describeStudent(GradingRun run, String username,
            Question question) {
        Submission submission = findSubmission(run, username);
        String text = username;
        if (submission != null) {
            text = Students.name(submission) + " (" + username + ")";
        }
        List<String> items = new ArrayList<>();
        for (Anomaly anomaly : openItems(submission, question)) {
            items.add(Problems.title(anomaly.getType()));
        }
        if (items.isEmpty()) {
            return text;
        }
        return text + ": " + String.join(", ", items);
    }

    // ---------------------------------------------------------- compare

    /**
     * Opens the code sheet on the group's question for its first
     * student, with Previous and Next through the others.
     *
     * <p>Tier: COULD.
     *
     * @param group the group
     */
    public void compare(AnswerGroup group) {
        GradingRun run = window.getState().getRun();
        List<Submission> students = new ArrayList<>();
        for (String username : group.getUsernames()) {
            Submission submission = findSubmission(run, username);
            if (submission != null) {
                students.add(submission);
            }
        }
        if (students.isEmpty()) {
            return;
        }
        window.showSheet(CodeSheet.create(window, students.get(0),
                group.getQuestionId(), null, students));
    }

    // ---------------------------------------------------------- helpers

    // Names the question and mistake, such as "Q2b, failed tests 3 and 5".
    private static String describe(AnswerGroup group) {
        String title = group.getTitle();
        return group.getQuestionId() + ", " + title.substring(0, 1)
                .toLowerCase() + title.substring(1);
    }

    // Says how many listed students were left out because they moved out
    // of the group since the sheet opened, or "" for none.
    private static String leftOut(int count) {
        if (count == 0) {
            return "";
        }
        return " " + Parts.plural(count, "student") + " left out: graded "
                + "again since and no longer in the group.";
    }

    // Returns the group as it is now, or the given one if it is gone.
    private AnswerGroup findCurrent(GradingRun run, AnswerGroup group) {
        for (AnswerGroup now : window.getBackend().listAnswerGroups(run)) {
            if (now.getKey().equals(group.getKey())) {
                return now;
            }
        }
        return group;
    }

    // Returns the listed students who are still in the group now.
    private List<String> stillInGroup(GradingRun run, AnswerGroup group,
            List<String> listed) {
        List<String> still = new ArrayList<>();
        AnswerGroup now = findCurrent(run, group);
        if (now == group) {
            return still;
        }
        for (String username : listed) {
            if (now.getUsernames().contains(username)) {
                still.add(username);
            }
        }
        return still;
    }

    // Returns a student's changes of one question, in the backend's order.
    private List<ScoreAdjustment> changesOf(GradingRun run,
            Submission submission, String questionId) {
        List<ScoreAdjustment> found = new ArrayList<>();
        for (ScoreAdjustment change : window.getBackend().listAdjustments(
                run, submission)) {
            if (change.getQuestionId().equals(questionId)) {
                found.add(change);
            }
        }
        return found;
    }

    // Returns the change in after that was not in before, or null.
    private static ScoreAdjustment findNew(List<ScoreAdjustment> after,
            List<ScoreAdjustment> before) {
        for (ScoreAdjustment change : after) {
            if (!before.contains(change)) {
                return change;
            }
        }
        return null;
    }

    // Returns a student's mark of a question now, such as "3".
    private static String markNow(Submission submission, String questionId) {
        if (submission == null) {
            return "-";
        }
        for (QuestionResult result : submission.getQuestionResults()) {
            if (result.getQuestion() != null
                    && result.getQuestion().getId().equals(questionId)) {
                return Parts.mark(result.getScore());
            }
        }
        return "-";
    }

    // Shows a student's name with the username under it.
    private static Node studentCell(Submission submission, String username) {
        String name = username;
        if (submission != null) {
            name = Students.name(submission);
        }
        Label usernameLabel = Parts.muted(username);
        usernameLabel.getStyleClass().add("small-text");
        VBox box = new VBox(Parts.strong(name), usernameLabel);
        box.setAlignment(Pos.CENTER_LEFT);
        return box;
    }

    // Returns the question of a group in a run, or null.
    private static Question questionOf(GradingRun run, AnswerGroup group) {
        if (run == null || run.getAssessment() == null) {
            return null;
        }
        for (Question question : run.getAssessment().getQuestions()) {
            if (question.getId().equals(group.getQuestionId())) {
                return question;
            }
        }
        return null;
    }

    // Returns the student with a username in a run, or null.
    private static Submission findSubmission(GradingRun run,
            String username) {
        if (run == null) {
            return null;
        }
        for (Submission submission : run.getSubmissions()) {
            if (submission.getUsername().equals(username)) {
                return submission;
            }
        }
        return null;
    }

    // Shows a sheet holding the given parts, one under another.
    private void showSheet(Node[] parts) {
        VBox body = new VBox(Parts.GAP, parts);
        body.setPrefWidth(SHEET_WIDTH + Parts.GAP * 2);
        body.getStyleClass().add("confirm");
        window.showSheet(body);
    }

    // Puts Cancel and the button that goes ahead at the right.
    private static HBox buttons(Button cancel, Button ok) {
        return Parts.row(Parts.GAP, new Node[] {Parts.grow(), cancel, ok});
    }

    // Puts a field's name above the field.
    private static VBox field(String name, Node control) {
        Label label = Parts.muted(name);
        label.getStyleClass().add("field-label");
        VBox box = new VBox(LABEL_GAP, label, control);
        box.setAlignment(Pos.TOP_LEFT);
        return box;
    }

    // Lets a label wrap within the sheet's width.
    private static Label wrapped(Label label) {
        label.setWrapText(true);
        label.setMaxWidth(SHEET_WIDTH);
        return label;
    }

    // Shows a problem under the fields, or hides it when empty.
    private static void showProblem(Label problem, String message) {
        problem.setText(message);
        problem.setVisible(!message.isEmpty());
        problem.setManaged(!message.isEmpty());
    }
}
