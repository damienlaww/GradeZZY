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
import java.util.HashMap;
import java.util.List;
import java.util.Map;

import javafx.geometry.Pos;
import javafx.scene.Node;
import javafx.scene.control.Button;
import javafx.scene.control.Label;
import javafx.scene.control.TextField;
import javafx.scene.layout.HBox;
import javafx.scene.layout.VBox;

import gradezzy.model.Anomaly;
import gradezzy.model.AnomalyType;
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
import gradezzy.util.TextUtils;

/**
 * The actions on one answer group.
 *
 * <p>Tier: COULD.
 */
public class AnswerGroupActions {

    // The width of the sheets' text
    private static final double SHEET_WIDTH = 520.0;

    // The width of a sheet: its text with a gap on each side
    private static final double SHEET_BODY_WIDTH = SHEET_WIDTH + Parts.GAP
            + Parts.GAP;

    // The height of a student's row in the mark sheet
    private static final double ROW_HEIGHT = 48.0;

    // The width of the mark column in the mark sheet
    private static final double MARK_COLUMN = 90.0;

    // The width of the new mark field
    private static final double MARK_WIDTH = 72.0;

    // The width of the name field, in characters
    private static final int NAME_COLUMNS = 32;

    // The gap between a field's name and the field
    private static final double LABEL_GAP = Parts.GAP_TINY;

    // Comes between a mark and the most it can be, such as "3 / 5"
    private static final String MARK_SEPARATOR = " / ";

    // Shown in place of a mark when there is none
    private static final String NO_MARK = "-";

    // Starts each student of the check sheet's list
    private static final String BULLET = "• ";

    // Comes before a sentence added to the end of another
    private static final String SENTENCE_SEPARATOR = " ";

    // Comes between the question and the mistake, such as "Q2b, failed
    // tests 3 and 5"
    private static final String QUESTION_SEPARATOR = ", ";

    // Comes between the mistake and its count of students, such as
    // "Q2b, failed tests 3 and 5, 4 students"
    private static final String COUNT_SEPARATOR = ", ";

    // Comes between a student and their open items
    private static final String ITEMS_SEPARATOR = ": ";

    // Comes between two open items of a student
    private static final String ITEM_SEPARATOR = ", ";

    // Put around a student's username after the name
    private static final String OPEN_BRACKET = " (";
    private static final String CLOSE_BRACKET = ")";

    // Where the first letter of a title ends
    private static final int FIRST_LETTER_END = 1;

    // The most students named in the warning about other groups
    private static final int MOST_NAMED = 3;

    // The one change a group mark change adds to each student
    private static final int ONE_CHANGE = 1;

    // The window, for the sheets and notices
    private final FlowWindow window;

    // Called after a group changes, so the view and table redraw
    private final Runnable onChanged;

    // The items each group's check settled this session, by group key,
    // so Undo check on the row puts them back too
    private final Map<String, List<Anomaly>> decidedByGroup =
            new HashMap<>();

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
        String aboutText = describe(group) + COUNT_SEPARATOR
                + Parts.plural(group.getUsernames().size(), "student")
                + ". The name shows on the group, in the common mistakes "
                + "export and in each student's JSON.";
        Label about = wrapped(Parts.muted(aboutText));
        Button cancel = Parts.secondary("Cancel", () -> window.closeSheet());
        Button save = Parts.primary("Save name");
        save.setOnAction(event -> saveName(group, field));
        field.setOnAction(event -> saveName(group, field));
        Label title = Parts.title("Name this mistake");
        VBox nameField = field("Name", field);
        HBox buttonRow = buttons(cancel, save);
        showSheet(new Node[] {title, about, nameField, buttonRow});
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
        // There is no question when there is no run
        Question question = questionOf(run, group);
        if (question == null) {
            return;
        }
        String questionId = question.getId();
        int count = group.getUsernames().size();
        List<String> listed = new ArrayList<>(group.getUsernames());
        PlainTable table = createMarkTable(run, listed, question);

        TextField mark = new TextField();
        mark.setId("group-mark");
        mark.setPrefWidth(MARK_WIDTH);
        mark.setMaxWidth(MARK_WIDTH);
        mark.setAccessibleText("New mark");
        Label outOf = Parts.muted("out of "
                + Parts.mark(question.getMaxScore()));
        HBox markRow = Parts.row(Parts.GAP_SMALL, new Node[] {mark, outOf});
        markRow.setAlignment(Pos.CENTER_LEFT);
        TextField reason = new TextField();
        reason.setId("group-reason");
        reason.setPromptText("For example: only the last case is wrong");
        reason.setAccessibleText("Reason for the change, required");
        Label problem = Parts.muted("");
        problem.getStyleClass().add("field-problem");
        wrapped(problem);
        showProblem(problem, "");

        String aboutText = group.getTitle() + ". Every student below gets "
                + "the same new mark for " + questionId + ", with the same "
                + "reason. You can undo it.";
        String overlap = describeOverlap(run, group, listed);
        if (!overlap.isEmpty()) {
            aboutText = aboutText + SENTENCE_SEPARATOR + overlap;
        }
        Label about = wrapped(Parts.muted(aboutText));
        Button cancel = Parts.secondary("Cancel", () -> window.closeSheet());
        Button save = Parts.primary("Change " + Parts.plural(count, "mark"));
        save.setOnAction(event -> saveMarks(run, group, listed, question,
                mark, reason, problem));
        Label title = Parts.title("Change the " + questionId + " mark of "
                + Parts.plural(count, "student"));
        VBox markField = field("New mark", markRow);
        VBox reasonField = field("Reason", reason);
        HBox buttonRow = buttons(cancel, save);
        showSheet(new Node[] {title, about, table, markField, reasonField,
            problem, buttonRow});
        mark.requestFocus();
    }

    // Warns about the listed students who are also in another group of
    // the same question, such as a student who failed every test: the
    // new mark replaces their whole mark for the question, not only what
    // this group's test is worth. Returns "" when there is none.
    private String describeOverlap(GradingRun run, AnswerGroup group,
            List<String> listed) {
        List<String> inOthers = new ArrayList<>();
        for (AnswerGroup other : window.getBackend().listAnswerGroups(run)) {
            boolean sameQuestion = other.getQuestionId().equals(
                    group.getQuestionId())
                    && !other.getKey().equals(group.getKey());
            if (sameQuestion) {
                addListed(inOthers, other.getUsernames(), listed);
            }
        }
        if (inOthers.isEmpty()) {
            return "";
        }
        String who = TextUtils.join(ITEM_SEPARATOR, inOthers);
        if (inOthers.size() > MOST_NAMED) {
            who = Parts.plural(inOthers.size(), "student") + " below";
        }
        String questionId = group.getQuestionId();
        return "Check first: " + who + " also lost marks in another "
                + questionId + " group, and this sets their whole "
                + questionId + " mark.";
    }

    // Adds each username that is listed and not added yet.
    private void addListed(List<String> added, List<String> usernames,
            List<String> listed) {
        for (String username : usernames) {
            if (listed.contains(username) && !added.contains(username)) {
                added.add(username);
            }
        }
    }

    // Builds the table of the listed students with their mark of the
    // question now.
    private PlainTable createMarkTable(GradingRun run, List<String> listed,
            Question question) {
        String questionId = question.getId();
        String maxMark = Parts.mark(question.getMaxScore());
        PlainTable table = new PlainTable(ROW_HEIGHT);
        table.getStyleClass().add("compact");
        table.addColumn("Student", PlainTable.GROW, Pos.CENTER_LEFT);
        table.addColumn(questionId + " now", MARK_COLUMN, Pos.CENTER_RIGHT);
        for (String username : listed) {
            Submission submission = findSubmission(run, username);
            Node student = studentCell(submission, username);
            Label markLabel = new Label(markNow(submission, questionId)
                    + MARK_SEPARATOR + maxMark);
            table.addRow(new Node[] {student, markLabel});
        }
        return table;
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
        double newMark = AdjustMarkSheet.parseMark(mark.getText());
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
            List<ScoreAdjustment> after = changesOf(run, submission,
                    question.getId());
            ScoreAdjustment made = findNew(after, before);
            boolean saved = result.isDone() && made != null;
            if (saved) {
                int countAfter = before.size() + ONE_CHANGE;
                changed.add(submission);
                changes.add(made);
                counts.add(Integer.valueOf(countAfter));
            } else if (firstProblem.isEmpty()) {
                firstProblem = SENTENCE_SEPARATOR + result.getMessage();
            }
        }
        window.closeSheet();
        int leftCount = listed.size() - still.size();
        notifyMarks(run, leftCount, firstProblem, changed, changes, counts);
        onChanged.run();
    }

    // Says how many marks the group change made, with one Undo for all
    // of them when it made any.
    private void notifyMarks(GradingRun run, int leftCount,
            String firstProblem, List<Submission> changed,
            List<ScoreAdjustment> changes, List<Integer> counts) {
        String saving = "";
        if (!changed.isEmpty()) {
            saving = window.getBackend().describeMarkSaving();
        }
        String message = "Changed the mark of "
                + Parts.plural(changed.size(), "student") + "."
                + leftOut(leftCount) + firstProblem + saving;
        if (changed.isEmpty()) {
            window.notify(message);
            return;
        }
        window.notify(message, "Undo",
                () -> undoMarks(run, changed, changes, counts));
    }

    // Puts back each group mark change that is still the student's
    // newest change of the question, and says how many were left.
    private void undoMarks(GradingRun run, List<Submission> changed,
            List<ScoreAdjustment> changes, List<Integer> counts) {
        int putBack = 0;
        int left = 0;
        for (int index = 0; index < changed.size(); index++) {
            Submission submission = changed.get(index);
            ScoreAdjustment change = changes.get(index);
            int count = counts.get(index).intValue();
            if (putBackOne(run, submission, change, count)) {
                putBack++;
            } else {
                left++;
            }
        }
        String message = "Put back " + Parts.plural(putBack, "mark") + ".";
        if (left == 1) {
            message += " 1 was changed again since and was left.";
        } else if (left > 1) {
            message += SENTENCE_SEPARATOR + left + " were changed again "
                    + "since and were left.";
        }
        window.notify(message);
        onChanged.run();
    }

    // Puts back one group mark change when it is still the student's
    // newest change of the question: the student has the count of
    // changes the group change left. Says whether it was put back.
    private boolean putBackOne(GradingRun run, Submission submission,
            ScoreAdjustment change, int count) {
        List<ScoreAdjustment> now = changesOf(run, submission,
                change.getQuestionId());
        if (!now.contains(change) || now.size() != count) {
            return false;
        }
        ActionResult result = window.getBackend().undoAdjustment(run,
                change);
        return result.isDone();
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
        // There is no question when there is no run
        Question question = questionOf(run, group);
        if (question == null) {
            return;
        }
        if (group.isChecked()) {
            // Takes back the tick and the items the check settled
            List<Anomaly> decided = decidedByGroup.remove(group.getKey());
            if (decided == null) {
                decided = new ArrayList<>();
            }
            undoCheck(run, group, decided);
            return;
        }
        List<String> listed = new ArrayList<>(group.getUsernames());
        VBox content = createCheckContent(run, group, listed, question);
        window.confirm("Mark this group as checked?", content, "Mark "
                + listed.size() + " as checked",
                () -> confirmCheck(run, group, listed, question));
    }

    // Builds what the check sheet says: what is checked, then each
    // listed student with their open items of the question.
    private VBox createCheckContent(GradingRun run, AnswerGroup group,
            List<String> listed, Question question) {
        VBox content = new VBox(Parts.GAP_SMALL);
        Label about = Parts.muted(describe(group) + ". The group is marked "
                + "as checked. No mark changes, and you can undo it:");
        if (countOpenItems(run, listed, question) > 0) {
            about.setText(describe(group) + ". Their open "
                    + question.getId() + " items, compile errors "
                    + "included, are marked as checked, so those items "
                    + "will show as checked. No mark changes, and you can "
                    + "undo it:");
        }
        content.getChildren().add(wrapped(about));
        for (String username : listed) {
            Label line = new Label(BULLET
                    + describeStudent(run, username, question));
            content.getChildren().add(wrapped(line));
        }
        if (hasOpenSimilarCode(run, listed, question)) {
            Label similar = Parts.muted("Similar code flags are left as "
                    + "they are: decide them under Similarity.");
            content.getChildren().add(wrapped(similar));
        }
        return content;
    }

    // Says whether a listed student has a Similar code flag about the
    // question still open, which checking the group leaves as it is.
    private boolean hasOpenSimilarCode(GradingRun run, List<String> listed,
            Question question) {
        DashboardBackend backend = window.getBackend();
        for (String username : listed) {
            Submission submission = findSubmission(run, username);
            if (submission != null && hasOpenSimilarCode(backend,
                    submission, question)) {
                return true;
            }
        }
        return false;
    }

    // Says whether a student has a Similar code flag about the question
    // still open.
    private boolean hasOpenSimilarCode(DashboardBackend backend,
            Submission submission, Question question) {
        for (Anomaly anomaly : submission.getAnomalies()) {
            boolean open = anomaly.getType() == AnomalyType.SIMILAR_CODE
                    && backend.needsReview(anomaly)
                    && AnswerGrouper.isAbout(anomaly, submission, question);
            if (open) {
                return true;
            }
        }
        return false;
    }

    // Counts the open items of a question of the listed students.
    private int countOpenItems(GradingRun run, List<String> listed,
            Question question) {
        int openCount = 0;
        for (String username : listed) {
            Submission submission = findSubmission(run, username);
            openCount += openItems(submission, question).size();
        }
        return openCount;
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
            Submission submission = findSubmission(run, username);
            for (Anomaly anomaly : openItems(submission, question)) {
                ActionResult decision = backend.setDecision(anomaly,
                        ReviewDecision.REVIEWED);
                if (decision.isDone()) {
                    decided.add(anomaly);
                }
            }
        }
        if (still.isEmpty()) {
            window.notify("Nothing was checked: every listed student "
                    + "left the group since the sheet opened.");
            onChanged.run();
            return;
        }
        // Only the students the sheet listed count as checked; one who
        // joined since shows as new
        ActionResult result = backend.setAnswerGroupChecked(run, current,
                still);
        decidedByGroup.put(current.getKey(), decided);
        int leftCount = listed.size() - still.size();
        String message = result.getMessage() + leftOut(leftCount);
        window.notify(message, "Undo",
                () -> undoCheck(run, current, decided));
        onChanged.run();
    }

    // Puts back the items a group check settled, and the group's tick.
    private void undoCheck(GradingRun run, AnswerGroup group,
            List<Anomaly> decided) {
        DashboardBackend backend = window.getBackend();
        decidedByGroup.remove(group.getKey());
        for (Anomaly anomaly : decided) {
            if (backend.getDecision(anomaly) == ReviewDecision.REVIEWED) {
                backend.setDecision(anomaly, ReviewDecision.OPEN);
            }
        }
        AnswerGroup current = findCurrent(run, group);
        ActionResult result = backend.setAnswerGroupChecked(run, current,
                false);
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
        for (Anomaly anomaly : submission.getAnomalies()) {
            if (isOpenItem(anomaly, submission, question)) {
                open.add(anomaly);
            }
        }
        return open;
    }

    // Says whether an item of a student waits for a look and is about a
    // question; a fix waiting for Keep or Reject does not count, nor does
    // Similar code, which is decided under Similarity.
    private boolean isOpenItem(Anomaly anomaly, Submission submission,
            Question question) {
        return anomaly.getFixStatus() != FixStatus.APPLIED
                && anomaly.getType() != AnomalyType.SIMILAR_CODE
                && window.getBackend().needsReview(anomaly)
                && AnswerGrouper.isAbout(anomaly, submission, question);
    }

    // Names a student with the open items of a question, such as "KAI
    // XUAN CHEN (kaixuan.chen.2024): Did not compile".
    private String describeStudent(GradingRun run, String username,
            Question question) {
        Submission submission = findSubmission(run, username);
        String text = username;
        if (submission != null) {
            text = Students.name(submission) + OPEN_BRACKET + username
                    + CLOSE_BRACKET;
        }
        List<String> items = new ArrayList<>();
        for (Anomaly anomaly : openItems(submission, question)) {
            items.add(Problems.title(anomaly.getType()));
        }
        if (items.isEmpty()) {
            return text;
        }
        String joined = TextUtils.join(ITEM_SEPARATOR, items);
        return text + ITEMS_SEPARATOR + joined;
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
        Submission first = students.get(0);
        Node sheet = CodeSheet.create(window, first, group.getQuestionId(),
                null, students);
        window.showSheet(sheet);
    }

    // ---------------------------------------------------------- helpers

    // Names the question and mistake, such as "Q2b, failed tests 3 and 5".
    private static String describe(AnswerGroup group) {
        String title = lowerFirst(group.getTitle());
        return group.getQuestionId() + QUESTION_SEPARATOR + title;
    }

    // Returns a text with its first letter in lower case.
    private static String lowerFirst(String text) {
        String first = text.substring(0, FIRST_LETTER_END);
        String rest = text.substring(FIRST_LETTER_END);
        return first.toLowerCase() + rest;
    }

    // Says how many listed students were left out because they moved out
    // of the group since the sheet opened, or "" for none.
    private static String leftOut(int count) {
        if (count == 0) {
            return "";
        }
        return SENTENCE_SEPARATOR + Parts.plural(count, "student")
                + " left out: graded again since and no longer in the "
                + "group.";
    }

    // Returns the group as it is now, or the given one if it is gone.
    private AnswerGroup findCurrent(GradingRun run, AnswerGroup group) {
        List<AnswerGroup> groups = window.getBackend().listAnswerGroups(run);
        for (AnswerGroup now : groups) {
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
        List<ScoreAdjustment> all = window.getBackend().listAdjustments(run,
                submission);
        for (ScoreAdjustment change : all) {
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
            return NO_MARK;
        }
        for (QuestionResult result : submission.getQuestionResults()) {
            if (isFor(result, questionId)) {
                return Parts.mark(result.getScore());
            }
        }
        return NO_MARK;
    }

    // Says whether a result is for the question with an id.
    private static boolean isFor(QuestionResult result, String questionId) {
        Question question = result.getQuestion();
        return question != null && question.getId().equals(questionId);
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
        body.setPrefWidth(SHEET_BODY_WIDTH);
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
        boolean shown = !message.isEmpty();
        problem.setText(message);
        problem.setVisible(shown);
        problem.setManaged(shown);
    }
}
