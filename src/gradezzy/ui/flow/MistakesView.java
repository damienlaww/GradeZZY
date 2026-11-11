/*
 * File: MistakesView.java
 *
 * Purpose: The Errors view of the Review step. For each question, one
 * row per error, such as "Failed test 2" or "Did not compile": its name,
 * how many students, whether it was checked and the buttons that act on
 * the whole group. A row opens like a dropdown to list its students,
 * each with Open code.
 *
 * Where it fits: ReviewScreen shows it in place of the student table
 * when "Errors" is chosen. It reads the groups through
 * DashboardBackend.listAnswerGroups; AnswerGroupActions does what the
 * group buttons ask.
 */
package gradezzy.ui.flow;

import java.util.ArrayList;
import java.util.List;

import javafx.geometry.Pos;
import javafx.scene.Node;
import javafx.scene.control.Button;
import javafx.scene.control.ButtonBase;
import javafx.scene.control.Label;
import javafx.scene.control.ScrollPane;
import javafx.scene.input.KeyCode;
import javafx.scene.input.KeyEvent;
import javafx.scene.input.MouseEvent;
import javafx.scene.layout.HBox;
import javafx.scene.layout.Priority;
import javafx.scene.layout.Region;
import javafx.scene.layout.VBox;

import gradezzy.model.AnswerGroup;
import gradezzy.model.GradingRun;
import gradezzy.model.Submission;
import gradezzy.ui.backend.Capability;
import gradezzy.ui.backend.DashboardBackend;

/**
 * Lists the errors of a run, question by question.
 *
 * <p>Tier: COULD.
 */
public class MistakesView {

    // The gap between a title and the line under it
    private static final double LINE_GAP = 2.0;

    // The size of the pencil beside an error's title, and of the chevron
    // that opens a row
    private static final double PENCIL_SIZE = 14.0;
    private static final double CHEVRON_SIZE = 14.0;

    // How far an open row's chevron turns, so it points down
    private static final double OPEN_ROTATION = 90.0;

    // The quotes around an error's name
    private static final String OPEN_QUOTE = "“";
    private static final String CLOSE_QUOTE = "”";

    // Comes between facts on one line
    private static final String DOT = "  ·  ";

    // The window, for the backend and the run
    private final FlowWindow window;

    // What the group buttons do
    private final AnswerGroupActions actions;

    // The questions and their errors
    private final VBox list = new VBox(Parts.GAP);

    // Scrolls the list
    private final ScrollPane scroll = new ScrollPane(list);

    // The keys of the errors that are open, so they stay open when the
    // view is drawn again
    private final List<String> openKeys = new ArrayList<>();

    // The line above the list, such as "31 answers lost marks, in 22
    // errors. Open an error to see its students; decide each error once."
    private String summary = "";

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
     * Returns the line that sums up the errors last shown.
     *
     * <p>Tier: COULD.
     *
     * @return such as "31 answers lost marks, in 22 errors. Open an
     *         error to see its students; decide each error once."
     */
    public String getSummary() {
        return summary;
    }

    /**
     * Shows the errors of a run, keeping the scroll position and which
     * errors are open.
     *
     * <p>Tier: COULD.
     *
     * @param run the run, or null for none
     */
    public void show(GradingRun run) {
        double place = scroll.getVvalue();
        list.getChildren().clear();
        if (run == null) {
            summary = "";
            return;
        }

        List<AnswerGroup> groups = window.getBackend()
                .listAnswerGroups(run);
        if (groups.isEmpty()) {
            summary = "";
            list.getChildren().add(Parts.subtitle("Every graded answer "
                    + "passed all its tests."));
            return;
        }
        summary = summaryOf(groups);
        addQuestions(run, groups);
        scroll.setVvalue(place);
    }

    // Sums up the errors, such as "31 answers lost marks, in 22 errors.
    // Open an error to see its students; decide each error once." A
    // student who failed two tests is counted once for the question.
    private String summaryOf(List<AnswerGroup> groups) {
        List<String> answers = new ArrayList<>();
        for (AnswerGroup group : groups) {
            for (String username : group.getUsernames()) {
                String answer = group.getQuestionId() + DOT + username;
                if (!answers.contains(answer)) {
                    answers.add(answer);
                }
            }
        }
        return Parts.plural(answers.size(), "answer") + " lost marks, in "
                + Parts.plural(groups.size(), "error") + ". Open an error "
                + "to see its students; decide each error once.";
    }

    // ---------------------------------------------------------- errors

    // Adds a header and a card of errors for each question to the list.
    private void addQuestions(GradingRun run, List<AnswerGroup> groups) {
        String question = null;
        VBox card = null;
        for (AnswerGroup group : groups) {
            if (!group.getQuestionId().equals(question)) {
                question = group.getQuestionId();
                card = new VBox();
                card.getStyleClass().add("errors-card");
                list.getChildren().addAll(questionHeader(question, groups),
                        card);
            }
            card.getChildren().add(errorRow(run, group));
        }
        for (Node node : list.getChildren()) {
            markFirstRow(node);
        }
    }

    // Marks the first row of a card, which has no line above it.
    private void markFirstRow(Node node) {
        boolean errorsCard = node instanceof VBox
                && node.getStyleClass().contains("errors-card")
                && !((VBox) node).getChildren().isEmpty();
        if (errorsCard) {
            ((VBox) node).getChildren().get(0).getStyleClass().add("first");
        }
    }

    // Names a question with how many errors it has, such as "Q2b 5
    // errors".
    private HBox questionHeader(String question, List<AnswerGroup> groups) {
        int count = 0;
        for (AnswerGroup group : groups) {
            if (group.getQuestionId().equals(question)) {
                count++;
            }
        }
        Label title = Parts.title(question);
        Label errorCount = Parts.muted(Parts.plural(count, "error"));
        HBox header = Parts.row(Parts.GAP_SMALL,
                new Node[] {title, errorCount});
        header.setAlignment(Pos.BASELINE_LEFT);
        return header;
    }

    // Builds one error: a header row that opens and closes, and, when
    // open, its students.
    private VBox errorRow(GradingRun run, AnswerGroup group) {
        boolean open = openKeys.contains(group.getKey());
        Node chevron = Parts.icon(Parts.CHEVRON, CHEVRON_SIZE);
        if (open) {
            chevron.setRotate(OPEN_ROTATION);
        }
        chevron.getStyleClass().add("error-chevron");

        Label count = Parts.muted(Parts.plural(group.getUsernames().size(),
                "student"));
        count.setMinWidth(Region.USE_PREF_SIZE);
        Node mistake = mistakeCell(group);
        HBox.setHgrow(mistake, Priority.ALWAYS);
        HBox header = Parts.row(Parts.GAP, new Node[] {chevron, mistake,
            count, stateCell(group), actionsCell(group)});
        header.getStyleClass().add("error-header");
        header.setFocusTraversable(true);
        header.setAccessibleText(group.getTitle() + ", "
                + count.getText() + ". Press Enter to show the students.");
        header.setOnMouseClicked(event -> headerClicked(event, run, group));
        header.setOnKeyPressed(event -> headerKey(event, run, group));
        Parts.tipNode(header, "Click to show or hide the students");

        VBox row = new VBox(header);
        row.getStyleClass().add("error-row");
        // The key names the error, so a check can find its row
        row.setUserData(group.getKey());
        if (open) {
            row.getStyleClass().add("open");
            row.getChildren().add(studentList(run, group));
        }
        return row;
    }

    // Opens or closes an error, unless a button in its row was clicked.
    private void headerClicked(MouseEvent event, GradingRun run,
            AnswerGroup group) {
        if (isInButton(event.getTarget())) {
            return;
        }
        toggle(run, group);
    }

    // Opens or closes an error with Enter or the space bar.
    private void headerKey(KeyEvent event, GradingRun run,
            AnswerGroup group) {
        boolean opens = event.getCode() == KeyCode.ENTER
                || event.getCode() == KeyCode.SPACE;
        if (opens) {
            event.consume();
            toggle(run, group);
        }
    }

    // Says whether a click landed on a button or inside one.
    private boolean isInButton(Object target) {
        Node node = null;
        if (target instanceof Node) {
            node = (Node) target;
        }
        while (node != null && !(node instanceof ButtonBase)) {
            node = node.getParent();
        }
        return node != null;
    }

    // Opens a closed error or closes an open one, and draws the view
    // again.
    private void toggle(GradingRun run, AnswerGroup group) {
        toggleKey(run, group.getKey());
    }

    // Opens or closes the error of a key, and draws the view again.
    private void toggleKey(GradingRun run, String key) {
        if (openKeys.contains(key)) {
            openKeys.remove(key);
        } else {
            openKeys.add(key);
        }
        show(run);
    }

    // Shows the error's title with a pencil to name it, and its name, or
    // "Not named yet", under it.
    private Node mistakeCell(AnswerGroup group) {
        Label title = Parts.strong(group.getTitle());
        title.setMinWidth(0);
        HBox titleRow = Parts.row(Parts.GAP_TINY,
                new Node[] {title, renameButton(group)});
        Label name = Parts.muted("Not named yet");
        if (!group.getLabel().isEmpty()) {
            name = new Label(OPEN_QUOTE + group.getLabel() + CLOSE_QUOTE);
        }
        name.getStyleClass().add("small-text");
        name.setWrapText(true);
        VBox box = new VBox(LINE_GAP, titleRow, name);
        if (group.countSameShape() > 0) {
            box.getChildren().add(sameShapeLabel(group));
        }
        box.setAlignment(Pos.CENTER_LEFT);
        box.setMinWidth(0);
        return box;
    }

    // Builds the pencil button that opens the sheet to name the error.
    private Button renameButton(AnswerGroup group) {
        Button rename = new Button();
        rename.setGraphic(Parts.icon(Parts.PENCIL, PENCIL_SIZE));
        rename.getStyleClass().addAll("flat", "icon-button", "rename-group");
        rename.setMinWidth(Region.USE_PREF_SIZE);
        rename.setAccessibleText("Name this error");
        rename.setOnAction(event -> actions.name(group));
        Parts.tip(rename, "Name this error");
        return rename;
    }

    // Says how many students of an error share the same code shape.
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

    // Builds the buttons that act on the whole error.
    private Node actionsCell(AnswerGroup group) {
        DashboardBackend backend = window.getBackend();
        HBox row = Parts.row(Parts.GAP_SMALL);
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
            Button openCode = Parts.small("Open code",
                    () -> actions.compare(group));
            Parts.tip(openCode, "Read the code of every student with this "
                    + "error, one after another.");
            row.getChildren().add(openCode);
        }
        row.setAlignment(Pos.CENTER_RIGHT);
        row.setMinWidth(Region.USE_PREF_SIZE);
        return row;
    }

    // Lists the students of an open error, each with a warning when their
    // code is like another's, and Open code.
    private VBox studentList(GradingRun run, AnswerGroup group) {
        VBox students = new VBox();
        students.getStyleClass().add("error-students");
        for (String username : group.getUsernames()) {
            students.getChildren().add(studentRow(run, group, username));
        }
        return students;
    }

    // Builds one student of an open error: name, email ID and Open code.
    private HBox studentRow(GradingRun run, AnswerGroup group,
            String username) {
        Submission submission = findSubmission(run, username);
        Label name = Parts.strong(username);
        HBox nameLine = Parts.row(Parts.GAP_SMALL, new Node[] {name});
        if (submission != null) {
            name.setText(Students.name(submission));
            Label warning = Students.similarWarning(submission,
                    window.getBackend());
            if (warning != null) {
                nameLine.getChildren().add(warning);
            }
        }
        Label email = Parts.muted(username);
        email.getStyleClass().add("small-text");
        VBox who = new VBox(LINE_GAP, nameLine, email);
        who.setMinWidth(0);
        HBox.setHgrow(who, Priority.ALWAYS);

        HBox row = Parts.row(Parts.GAP, new Node[] {who});
        row.getStyleClass().add("error-student");
        boolean canOpen = submission != null
                && window.getBackend().supports(Capability.SOURCE_VIEW);
        if (canOpen) {
            Button open = Parts.small("Open code",
                    () -> openCode(submission, group.getQuestionId()));
            Parts.tip(open, "Read " + Students.name(submission) + "'s "
                    + group.getQuestionId() + " code and test output.");
            row.getChildren().add(open);
        }
        return row;
    }

    // Opens a student's code and test output for a question.
    private void openCode(Submission submission, String questionId) {
        window.showSheet(CodeSheet.create(window, submission, questionId));
    }

    // Returns the submission of a username in a run, or null.
    private static Submission findSubmission(GradingRun run,
            String username) {
        for (Submission submission : run.getSubmissions()) {
            if (submission.getUsername().equals(username)) {
                return submission;
            }
        }
        return null;
    }
}
