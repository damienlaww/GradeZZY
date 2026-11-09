/*
 * File: CommonMistakesSection.java
 *
 * Purpose: The "Common mistakes" section of the Statistics tab: the
 * largest answer groups of the run shown, with the names the marker
 * gave them. Clicking a row lists its students.
 *
 * Where it fits: StatisticsScreen puts it under the charts of one
 * section; Compare sections leaves it out. The groups come from
 * DashboardBackend.listAnswerGroups; they are named on Review, under
 * Mistakes.
 */
package gradezzy.ui.flow;

import java.util.ArrayList;
import java.util.List;

import javafx.geometry.Pos;
import javafx.scene.Cursor;
import javafx.scene.Node;
import javafx.scene.control.Button;
import javafx.scene.control.Label;
import javafx.scene.input.MouseEvent;
import javafx.scene.layout.HBox;
import javafx.scene.layout.VBox;

import gradezzy.model.AnswerGroup;
import gradezzy.model.GradingRun;
import gradezzy.model.Submission;

/**
 * Lists the largest answer groups of a run.
 *
 * <p>Tier: COULD.
 */
public class CommonMistakesSection {

    // The most groups listed before "and N more"
    private static final int MAX_ROWS = 8;

    // The height of a row
    private static final double ROW_HEIGHT = 44.0;

    // The widths of the Question, Mistake and Students columns
    private static final double QUESTION_WIDTH = 100.0;
    private static final double MISTAKE_WIDTH = 260.0;
    private static final double STUDENTS_WIDTH = 100.0;

    // The bullet before each student in the students sheet
    private static final String BULLET = "• ";

    // Joins a student's name and username in the students sheet
    private static final String USERNAME_OPEN = " (";
    private static final String USERNAME_CLOSE = ")";

    // Joins the question and the mistake in the students sheet's title
    private static final String TITLE_SEPARATOR = ", ";

    // The window, for the backend and the sheet
    private final FlowWindow window;

    // The section: title, help, table and the note under it
    private final VBox view = new VBox(Parts.GAP);

    /**
     * Creates the section.
     *
     * <p>Tier: COULD.
     *
     * @param window the window
     */
    public CommonMistakesSection(FlowWindow window) {
        this.window = window;
        view.getStyleClass().add("common-mistakes");
    }

    /**
     * Returns the section.
     *
     * <p>Tier: COULD.
     *
     * @return the section
     */
    public Node getView() {
        return view;
    }

    /**
     * Shows the largest groups of a run.
     *
     * <p>Tier: COULD.
     *
     * @param run the run shown on the page
     */
    public void show(GradingRun run) {
        view.getChildren().setAll(Parts.title("Common mistakes"),
                Parts.muted("Students who lost marks the same way on a "
                + "question. Name them on Review, under Mistakes."));
        List<AnswerGroup> listed = window.getBackend().listAnswerGroups(run);
        List<AnswerGroup> groups = largestFirst(listed);
        if (groups.isEmpty()) {
            view.getChildren().add(Parts.subtitle(
                    "No common mistakes in this run."));
            return;
        }

        PlainTable table = new PlainTable(ROW_HEIGHT);
        table.addColumn("Question", QUESTION_WIDTH, Pos.CENTER_LEFT);
        table.addColumn("Mistake", MISTAKE_WIDTH, Pos.CENTER_LEFT);
        table.addColumn("Name", PlainTable.GROW, Pos.CENTER_LEFT);
        table.addColumn("Students", STUDENTS_WIDTH, Pos.CENTER_RIGHT);
        int shown = Math.min(groups.size(), MAX_ROWS);
        for (int index = 0; index < shown; index++) {
            addRow(table, run, groups.get(index));
        }
        Label hint = Parts.muted(note(groups.size(), shown));
        view.getChildren().addAll(table, hint);
    }

    // Adds one group's row, which lists its students when clicked.
    private void addRow(PlainTable table, GradingRun run, AnswerGroup group) {
        Label name = Parts.muted("Not named");
        if (!group.getLabel().isEmpty()) {
            name = new Label(group.getLabel());
        }
        int students = group.getUsernames().size();
        Label question = new Label(group.getQuestionId());
        Label mistake = new Label(group.getTitle());
        Label count = new Label(String.valueOf(students));
        HBox row = table.addRow(new Node[] {question, mistake, name, count});
        row.setCursor(Cursor.HAND);
        row.setOnMouseClicked(event -> rowClicked(event, run, group));
    }

    // Lists a group's students in a sheet.
    private void rowClicked(MouseEvent event, GradingRun run,
            AnswerGroup group) {
        event.consume();
        VBox list = new VBox(Parts.GAP_SMALL);
        for (String username : group.getUsernames()) {
            Label line = new Label(BULLET + describe(run, username));
            list.getChildren().add(line);
        }
        Button close = Parts.secondary("Close", () -> window.closeSheet());
        HBox buttons = Parts.row(Parts.GAP, new Node[] {Parts.grow(), close});
        String title = group.getQuestionId() + TITLE_SEPARATOR
                + group.getTitle();
        VBox body = new VBox(Parts.GAP, Parts.title(title), list, buttons);
        body.getStyleClass().add("confirm");
        window.showSheet(body);
    }

    // Names a student with the username, such as "Ellis Tan
    // (learner.echo)".
    private static String describe(GradingRun run, String username) {
        for (Submission submission : run.getSubmissions()) {
            boolean same = submission.getUsername().equals(username);
            if (same) {
                return Students.name(submission) + USERNAME_OPEN + username
                        + USERNAME_CLOSE;
            }
        }
        return username;
    }

    // Says how to see more, under the table.
    private static String note(int total, int shown) {
        String clickHint = "Click a row to see its students.";
        int hidden = total - shown;
        if (hidden == 0) {
            return clickHint;
        }
        return "And " + Parts.plural(hidden, "more group", "more groups")
                + ", all on Review under Mistakes. " + clickHint;
    }

    // Orders the groups largest first; equal sizes keep their order.
    private static List<AnswerGroup> largestFirst(List<AnswerGroup> groups) {
        List<AnswerGroup> ordered = new ArrayList<>();
        for (AnswerGroup group : groups) {
            int position = ordered.size();
            int size = group.getUsernames().size();
            boolean goesEarlier = isLargerThanBefore(ordered, position, size);
            while (goesEarlier) {
                position--;
                goesEarlier = isLargerThanBefore(ordered, position, size);
            }
            ordered.add(position, group);
        }
        return ordered;
    }

    // Says whether a group of a size goes before the group just before a
    // position.
    private static boolean isLargerThanBefore(List<AnswerGroup> ordered,
            int position, int size) {
        if (position == 0) {
            return false;
        }
        int before = position - 1;
        return ordered.get(before).getUsernames().size() < size;
    }
}
