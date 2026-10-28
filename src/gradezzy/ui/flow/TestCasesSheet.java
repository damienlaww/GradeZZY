/*
 * File: TestCasesSheet.java
 *
 * Purpose: A sheet where the instructor types test cases instead of
 * choosing a tester folder: pick a question, type the method, its
 * arguments and the expected result, add or remove cases, and see the
 * list.
 *
 * Where it fits: Opened from the Tester files row on Set up. The cases
 * are kept in the batch (ImportBatch.getTypedTestCases); LiveBackend
 * turns them into testers with TypedTestCaseSource when grading starts.
 */
package gradezzy.ui.flow;

import java.util.ArrayList;
import java.util.List;

import javafx.geometry.Pos;
import javafx.scene.Node;
import javafx.scene.control.Button;
import javafx.scene.control.ComboBox;
import javafx.scene.control.Hyperlink;
import javafx.scene.control.Label;
import javafx.scene.control.ScrollPane;
import javafx.scene.control.TextField;
import javafx.scene.layout.HBox;
import javafx.scene.layout.Priority;
import javafx.scene.layout.StackPane;
import javafx.scene.layout.VBox;

import gradezzy.model.Assessment;
import gradezzy.model.Question;
import gradezzy.testcase.TestCase;
import gradezzy.ui.backend.ImportBatch;

/**
 * Builds the typed test cases sheet.
 */
public final class TestCasesSheet {

    // The width of the sheet
    private static final double WIDTH = 720.0;

    // The tallest the list of cases may get before it scrolls
    private static final double LIST_HEIGHT = 240.0;

    // The width of the question choice
    private static final double QUESTION_WIDTH = 96.0;

    // The width of the question column in the list
    private static final double QUESTION_COLUMN = 48.0;

    // The width of the Remove link, so the links line up
    private static final double LINK_WIDTH = 52.0;

    // The size of the close icon
    private static final double CLOSE_SIZE = 16.0;

    // The style class of code text
    private static final String CODE = "code-text";

    // Not used: every method is static.
    private TestCasesSheet() {
    }

    /**
     * Builds the sheet.
     *
     * @param window the window that shows it
     * @param onChange called after a case is added or removed
     * @return the sheet's content
     */
    public static Node create(FlowWindow window, Runnable onChange) {
        ImportBatch batch = window.getState().getBatch();
        VBox list = new VBox();
        Label count = Parts.muted("");
        count.getStyleClass().add("small-text");
        Runnable redraw = () -> {
            fillList(window, list, count, onChange);
            onChange.run();
        };

        ComboBox<String> question = new ComboBox<>();
        Assessment assessment = window.getBackend().getAssessment();
        if (assessment != null) {
            for (Question each : assessment.getQuestions()) {
                question.getItems().add(each.getId());
            }
        }
        if (!question.getItems().isEmpty()) {
            question.setValue(question.getItems().get(0));
        }
        question.setPrefWidth(QUESTION_WIDTH);
        question.setMaxWidth(Double.MAX_VALUE);

        TextField method = codeField("countVowels");
        TextField arguments = codeField("\"banana\", 2");
        TextField expected = codeField("3");
        Label problem = new Label();
        problem.getStyleClass().add("field-problem");
        problem.setWrapText(true);
        show(problem, false);

        Button add = Parts.secondary("Add case", () -> addCase(batch,
                question.getValue(), method, arguments, expected, problem,
                redraw));
        expected.setOnAction(event -> add.fire());

        HBox form = Parts.row(Parts.GAP,
                field("Question", question, false),
                field("Method", method, true),
                field("Arguments", arguments, true),
                field("Expected result", expected, true));
        form.setAlignment(Pos.BOTTOM_LEFT);
        HBox addRow = Parts.row(Parts.GAP, problem, Parts.grow(), add);

        ScrollPane scroll = new ScrollPane(list);
        scroll.setFitToWidth(true);
        scroll.setHbarPolicy(ScrollPane.ScrollBarPolicy.NEVER);
        scroll.setMaxHeight(LIST_HEIGHT);
        scroll.getStyleClass().add("page-scroll");
        VBox listCard = new VBox(scroll);
        listCard.getStyleClass().add("case-list");

        Button done = Parts.primary("Done");
        done.setOnAction(event -> window.closeSheet());
        HBox bottom = Parts.row(Parts.GAP, count, Parts.grow(), done);

        Label help = Parts.subtitle("Each case calls one method of the "
                + "student's answer and compares what it returns with the "
                + "expected result. Type Java, as in the examples. Grading "
                + "uses these cases instead of the tester folder.");
        help.setMaxWidth(WIDTH);
        VBox body = new VBox(Parts.GAP, top(window), help, form, addRow,
                listCard, note(window), bottom);
        body.setPrefWidth(WIDTH);
        body.getStyleClass().add("test-cases-sheet");
        fillList(window, list, count, onChange);
        return body;
    }

    // Builds the title row with the close button.
    private static HBox top(FlowWindow window) {
        Button close = new Button();
        close.setGraphic(Parts.icon(Parts.CLOSE, CLOSE_SIZE));
        close.getStyleClass().addAll("flat", "icon-button");
        close.setOnAction(event -> window.closeSheet());
        return Parts.row(Parts.GAP, Parts.title("Type test cases"),
                Parts.grow(), close);
    }

    // Explains what demo mode does with typed cases.
    private static Node note(FlowWindow window) {
        Label text = Parts.muted("");
        text.getStyleClass().add("small-text");
        text.setWrapText(true);
        if (window.getBackend().isDemo()) {
            text.setText("Demo mode keeps these cases for this session; "
                    + "the sample run still uses the sample testers.");
        }
        show(text, window.getBackend().isDemo());
        return text;
    }

    // Puts a small label above a form control.
    private static VBox field(String name, Node control, boolean grow) {
        Label label = Parts.muted(name);
        label.getStyleClass().add("field-label");
        VBox box = new VBox(Parts.GAP_SMALL / 2, label, control);
        if (grow) {
            HBox.setHgrow(box, Priority.ALWAYS);
        }
        return box;
    }

    // Creates a text field for code, with an example as its prompt.
    private static TextField codeField(String example) {
        TextField field = new TextField();
        field.setPromptText(example);
        field.getStyleClass().add(CODE);
        field.setMaxWidth(Double.MAX_VALUE);
        return field;
    }

    // Checks the form, then adds the case to the batch.
    private static void addCase(ImportBatch batch, String questionId,
            TextField method, TextField arguments, TextField expected,
            Label problem, Runnable redraw) {
        String name = method.getText().trim();
        String argumentText = arguments.getText().trim();
        String expectedText = expected.getText().trim();
        String error = check(questionId, name, argumentText, expectedText);
        if (!error.isEmpty()) {
            problem.setText(error);
            show(problem, true);
            return;
        }
        show(problem, false);
        batch.getTypedTestCases().add(new TestCase(questionId, name,
                argumentText, expectedText));
        batch.setUsingTypedTestCases(true);
        arguments.clear();
        expected.clear();
        arguments.requestFocus();
        redraw.run();
    }

    // Returns what is wrong with a typed case, or an empty string.
    private static String check(String questionId, String name,
            String argumentText, String expectedText) {
        if (questionId == null || questionId.isEmpty()) {
            return "Choose a question.";
        }
        if (name.isEmpty()) {
            return "Type the method name, such as countVowels.";
        }
        if (!isJavaName(name)) {
            return "The method name must be one Java name, without spaces "
                    + "or brackets.";
        }
        if (argumentText.isEmpty()) {
            return "Type the arguments, such as \"banana\", 2.";
        }
        if (expectedText.isEmpty()) {
            return "Type the expected result, such as 3.";
        }
        return "";
    }

    // Says whether a text is a single Java name.
    private static boolean isJavaName(String text) {
        if (!Character.isJavaIdentifierStart(text.charAt(0))) {
            return false;
        }
        for (int index = 1; index < text.length(); index++) {
            if (!Character.isJavaIdentifierPart(text.charAt(index))) {
                return false;
            }
        }
        return true;
    }

    // Redraws the list of cases and the count under it.
    private static void fillList(FlowWindow window, VBox list, Label count,
            Runnable onChange) {
        ImportBatch batch = window.getState().getBatch();
        List<TestCase> cases = new ArrayList<>(batch.getTypedTestCases());
        list.getChildren().clear();
        count.setText(Parts.plural(cases.size(), "test case"));
        if (cases.isEmpty()) {
            Label empty = Parts.muted("No test cases yet. Add one for "
                    + "each check a tester should make.");
            StackPane box = new StackPane(empty);
            box.getStyleClass().add("case-empty");
            list.getChildren().add(box);
            return;
        }
        for (int index = 0; index < cases.size(); index++) {
            HBox row = caseRow(window, cases.get(index), list, count,
                    onChange);
            row.getStyleClass().add("case-row");
            if (index == 0) {
                row.getStyleClass().add("first");
            }
            list.getChildren().add(row);
        }
    }

    // Builds one row of the list: question, the call, the result.
    private static HBox caseRow(FlowWindow window, TestCase testCase,
            VBox list, Label count, Runnable onChange) {
        Label question = Parts.strong(testCase.getQuestionId());
        question.setMinWidth(QUESTION_COLUMN);
        question.setPrefWidth(QUESTION_COLUMN);
        Label call = new Label(testCase.getMethodName() + "("
                + testCase.getArgumentExpressions() + ")");
        call.getStyleClass().add(CODE);
        call.setMinWidth(0);
        Label gives = Parts.muted("gives");
        gives.setMinWidth(Label.USE_PREF_SIZE);
        Label result = new Label(testCase.getExpectedExpression());
        result.getStyleClass().add(CODE);
        result.setMinWidth(Label.USE_PREF_SIZE);
        HBox what = Parts.row(Parts.GAP_SMALL, call, gives, result);
        what.setMinWidth(0);
        HBox.setHgrow(what, Priority.ALWAYS);

        Hyperlink remove = Parts.link("Remove", () -> removeCase(window,
                testCase, list, count, onChange));
        remove.getStyleClass().add("small-text");
        remove.setMinWidth(LINK_WIDTH);
        remove.setPrefWidth(LINK_WIDTH);
        remove.setAlignment(Pos.CENTER_RIGHT);
        return Parts.row(Parts.GAP, question, what, remove);
    }

    // Removes one case; with none left, grading goes back to the folder.
    private static void removeCase(FlowWindow window, TestCase testCase,
            VBox list, Label count, Runnable onChange) {
        ImportBatch batch = window.getState().getBatch();
        batch.getTypedTestCases().remove(testCase);
        if (batch.getTypedTestCases().isEmpty()) {
            batch.setUsingTypedTestCases(false);
        }
        fillList(window, list, count, onChange);
        onChange.run();
    }

    // Shows or hides a node and frees its space.
    private static void show(Node node, boolean visible) {
        node.setVisible(visible);
        node.setManaged(visible);
    }
}
