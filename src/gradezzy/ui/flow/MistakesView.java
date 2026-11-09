/*
 * File: MistakesView.java
 *
 * Purpose: The Mistakes view of the Review step: for each question, the
 * groups of students who lost marks the same way, each with its name,
 * its students, whether it was checked, and the buttons that act on the
 * whole group.
 *
 * Where it fits: ReviewScreen shows it in place of the student table
 * when "Mistakes" is chosen. It reads the groups through
 * DashboardBackend.listAnswerGroups; AnswerGroupActions does what the
 * buttons ask.
 */
package gradezzy.ui.flow;

import java.util.ArrayList;
import java.util.List;

import javafx.geometry.Pos;
import javafx.scene.Node;
import javafx.scene.control.Label;
import javafx.scene.control.ScrollPane;
import javafx.scene.layout.HBox;
import javafx.scene.layout.VBox;

import gradezzy.model.AnswerGroup;
import gradezzy.model.GradingRun;
import gradezzy.model.Submission;
import gradezzy.ui.backend.Capability;
import gradezzy.ui.backend.DashboardBackend;
import gradezzy.util.TextUtils;

/**
 * Lists the answer groups of a run, question by question.
 *
 * <p>Tier: COULD.
 */
public class MistakesView {

    // The height of a group's row: a title with a line under it
    private static final double ROW_HEIGHT = 76.0;

    // The width of the Students column, and in a narrow window
    private static final double STUDENTS_WIDTH = 270.0;
    private static final double NARROW_STUDENTS_WIDTH = 190.0;

    // The width of the column with the Checked or To check pill, and in
    // a narrow window
    private static final double STATE_WIDTH = 110.0;
    private static final double NARROW_STATE_WIDTH = 100.0;

    // Below this width the view uses the narrow columns, so the mistake
    // keeps room for its title
    private static final double NARROW_VIEW = 1000.0;

    // The width of the column with the buttons
    private static final double ACTIONS_WIDTH = 390.0;

    // The gap between a title and the line under it
    private static final double LINE_GAP = 2.0;

    // The quotes around a mistake's name
    private static final String OPEN_QUOTE = "“";
    private static final String CLOSE_QUOTE = "”";

    // The header of a column that needs no words
    private static final String NO_HEADER = "";

    // Comes between the student count and the names of the students
    private static final String COUNT_SEPARATOR = ": ";

    // Comes between the names of two students
    private static final String NAME_SEPARATOR = ", ";

    // The window, for the backend and the run
    private final FlowWindow window;

    // What the buttons do
    private final AnswerGroupActions actions;

    // The questions and their groups
    private final VBox list = new VBox(Parts.GAP);

    // Scrolls the list
    private final ScrollPane scroll = new ScrollPane(list);

    // The line above the list, such as "31 answers lost marks, in 22
    // groups."
    private String summary = "";

    // The run shown, or null
    private GradingRun shownRun = null;

    // True while the view is narrower than NARROW_VIEW
    private boolean narrow = false;

    /**
     * Creates the view.
     *
     * <p>Tier: COULD.
     *
     * @param window the window
     * @param actions what the group buttons do
     */
    public MistakesView(FlowWindow window, AnswerGroupActions actions) {
        this.window = window;
        this.actions = actions;
        scroll.setFitToWidth(true);
        scroll.getStyleClass().addAll("edge-to-edge", "mistakes-view");
        list.getStyleClass().add("mistakes-list");
        scroll.widthProperty().addListener((observable, before, after)
                -> widthChanged(after));
    }

    // Draws the list again with the other column widths when the view
    // becomes narrower or wider than NARROW_VIEW.
    private void widthChanged(Number newWidth) {
        double width = newWidth.doubleValue();
        boolean nowNarrow = width > 0 && width < NARROW_VIEW;
        if (nowNarrow != narrow) {
            narrow = nowNarrow;
            show(shownRun);
        }
    }

    /**
     * Returns the view.
     *
     * <p>Tier: COULD.
     *
     * @return the scrolling list
     */
    public Node getView() {
        return scroll;
    }

    /**
     * Returns the line that sums up the groups last shown.
     *
     * <p>Tier: COULD.
     *
     * @return such as "31 answers lost marks, in 22 groups. Decide each
     *         group once."
     */
    public String getSummary() {
        return summary;
    }

    /**
     * Shows the groups of a run, keeping the scroll position.
     *
     * <p>Tier: COULD.
     *
     * @param run the run, or null for none
     */
    public void show(GradingRun run) {
        shownRun = run;
        double place = scroll.getVvalue();
        list.getChildren().clear();
        List<AnswerGroup> groups = new ArrayList<>();
        if (run != null) {
            groups = window.getBackend().listAnswerGroups(run);
        }
        if (groups.isEmpty()) {
            summary = "";
            list.getChildren().add(Parts.subtitle("Every graded answer "
                    + "passed all its tests."));
            return;
        }
        summary = summaryOf(groups);
        addTables(run, groups);
        scroll.setVvalue(place);
    }

    // Sums up the groups, such as "31 answers lost marks, in 22 groups.
    // Decide each group once."
    private String summaryOf(List<AnswerGroup> groups) {
        int answers = 0;
        for (AnswerGroup group : groups) {
            answers += group.getUsernames().size();
        }
        return Parts.plural(answers, "answer") + " lost marks, in "
                + Parts.plural(groups.size(), "group") + ". Decide each "
                + "group once.";
    }

    // Adds a header and a table of groups for each question to the list.
    private void addTables(GradingRun run, List<AnswerGroup> groups) {
        String question = null;
        PlainTable table = null;
        for (AnswerGroup group : groups) {
            if (!group.getQuestionId().equals(question)) {
                question = group.getQuestionId();
                table = createTable();
                HBox header = questionHeader(question, groups);
                list.getChildren().addAll(header, table);
            }
            Node mistake = mistakeCell(group);
            Node students = studentsCell(run, group);
            Node state = stateCell(group);
            Node buttons = actionsCell(group);
            table.addRow(new Node[] {mistake, students, state, buttons});
        }
    }

    // Builds the table of one question's groups.
    private PlainTable createTable() {
        PlainTable table = new PlainTable(ROW_HEIGHT);
        table.addColumn("Mistake", PlainTable.GROW, Pos.CENTER_LEFT);
        table.addColumn("Students", studentsWidth(), Pos.CENTER_LEFT);
        if (narrow) {
            table.addColumn(NO_HEADER, NARROW_STATE_WIDTH, Pos.CENTER_LEFT);
        } else {
            table.addColumn(NO_HEADER, STATE_WIDTH, Pos.CENTER_LEFT);
        }
        table.addColumn(NO_HEADER, ACTIONS_WIDTH, Pos.CENTER_RIGHT);
        return table;
    }

    // Names a question with how many groups it has, such as "Q2b 5
    // groups".
    private HBox questionHeader(String question, List<AnswerGroup> groups) {
        int count = 0;
        for (AnswerGroup group : groups) {
            if (group.getQuestionId().equals(question)) {
                count++;
            }
        }
        Label title = Parts.title(question);
        Label groupCount = Parts.muted(Parts.plural(count, "group"));
        HBox header = Parts.row(Parts.GAP_SMALL,
                new Node[] {title, groupCount});
        header.setAlignment(Pos.BASELINE_LEFT);
        return header;
    }

    // Shows the group's title with its name, or "Not named yet", under it.
    private Node mistakeCell(AnswerGroup group) {
        Label name = Parts.muted("Not named yet");
        if (!group.getLabel().isEmpty()) {
            name = new Label(OPEN_QUOTE + group.getLabel() + CLOSE_QUOTE);
        }
        name.getStyleClass().add("small-text");
        name.setWrapText(true);
        VBox box = new VBox(LINE_GAP, Parts.strong(group.getTitle()), name);
        box.setAlignment(Pos.CENTER_LEFT);
        box.setMinWidth(0);
        return box;
    }

    // Shows the students by name, and which share the same code shape.
    private Node studentsCell(GradingRun run, AnswerGroup group) {
        List<String> names = new ArrayList<>();
        for (String username : group.getUsernames()) {
            names.add(nameOf(run, username));
        }
        int size = group.getUsernames().size();
        Label who = new Label(Parts.plural(size, "student")
                + COUNT_SEPARATOR + TextUtils.join(NAME_SEPARATOR, names));
        who.setWrapText(true);
        double maxWidth = studentsWidth() - Parts.GAP;
        who.setMaxWidth(maxWidth);
        VBox box = new VBox(LINE_GAP, who);
        if (group.countSameShape() > 0) {
            box.getChildren().add(sameShapeLabel(group));
        }
        box.setAlignment(Pos.CENTER_LEFT);
        return box;
    }

    // Says how many students of a group share the same code shape.
    private Label sameShapeLabel(AnswerGroup group) {
        int same = group.countSameShape();
        int size = group.getUsernames().size();
        String text = "Same code shape";
        if (same < size) {
            text = same + " of " + size + " have the same code shape";
        }
        Label shape = Parts.muted(text);
        shape.getStyleClass().add("small-text");
        Parts.tipNode(shape, "Their code is the same except for the "
                + "names they chose, so one look covers them.");
        return shape;
    }

    // Shows Checked, how many joined since, or To check.
    private Node stateCell(AnswerGroup group) {
        if (group.isChecked()) {
            return Parts.pill("Checked", Tone.GOOD);
        }
        if (!group.getCheckedUsernames().isEmpty()) {
            return Parts.pill(group.countNewSinceChecked() + " new",
                    Tone.WARN);
        }
        return Parts.pill("To check", Tone.WARN);
    }

    // Builds the buttons that act on the whole group.
    private Node actionsCell(AnswerGroup group) {
        DashboardBackend backend = window.getBackend();
        HBox row = Parts.row(Parts.GAP_SMALL);
        row.getChildren().add(Parts.small("Name",
                () -> actions.name(group)));
        if (backend.supports(Capability.SCORE_ADJUSTMENT)) {
            row.getChildren().add(Parts.small("Change mark",
                    () -> actions.changeMark(group)));
        }
        String checkText = "Mark as checked";
        if (group.isChecked()) {
            checkText = "Undo check";
        }
        row.getChildren().add(Parts.small(checkText,
                () -> actions.check(group)));
        if (backend.supports(Capability.SOURCE_VIEW)) {
            row.getChildren().add(Parts.small("Compare code",
                    () -> actions.compare(group)));
        }
        row.setAlignment(Pos.CENTER_RIGHT);
        return row;
    }

    // Returns the width of the Students column for the view's width.
    private double studentsWidth() {
        if (narrow) {
            return NARROW_STUDENTS_WIDTH;
        }
        return STUDENTS_WIDTH;
    }

    // Returns a student's name, or the username when not on the roster.
    private static String nameOf(GradingRun run, String username) {
        for (Submission submission : run.getSubmissions()) {
            if (submission.getUsername().equals(username)) {
                return Students.name(submission);
            }
        }
        return username;
    }
}
