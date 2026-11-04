/*
 * File: CodeSheet.java
 *
 * Purpose: A large sheet that shows one student's answer file and the
 * tester's output for each question, read from the working copy, with a
 * button that compiles and runs the question's tests again to show fresh
 * output.
 *
 * Where it fits: Opened from the student panel on Review. Reads files
 * only through DashboardBackend.readSourceFile, and runs tests again
 * through DashboardBackend.rerunQuestion, which saves nothing. On the
 * left, each question shows its mark and how many tests passed; on the
 * right, the code and the output each sit in a panel with a header.
 */
package gradezzy.ui.flow;

import java.util.ArrayList;
import java.util.List;

import javafx.beans.binding.Bindings;
import javafx.geometry.Insets;
import javafx.geometry.Orientation;
import javafx.geometry.Pos;
import javafx.scene.Node;
import javafx.scene.Scene;
import javafx.scene.control.Button;
import javafx.scene.control.Label;
import javafx.scene.control.SplitPane;
import javafx.scene.control.TextArea;
import javafx.scene.control.Toggle;
import javafx.scene.control.ToggleButton;
import javafx.scene.control.ToggleGroup;
import javafx.scene.layout.HBox;
import javafx.scene.layout.Priority;
import javafx.scene.layout.StackPane;
import javafx.scene.layout.VBox;

import gradezzy.model.Assessment;
import gradezzy.model.GradingRun;
import gradezzy.model.Question;
import gradezzy.model.QuestionResult;
import gradezzy.model.Submission;
import gradezzy.model.TestCaseResult;
import gradezzy.ui.backend.Capability;

/**
 * Builds the code and test output sheet.
 */
public final class CodeSheet {

    // The largest size of the sheet's content
    private static final double WIDTH = 1040.0;
    private static final double HEIGHT = 680.0;

    // The space kept around the sheet in a small window, including the
    // sheet's own padding
    private static final double MARGIN = 120.0;

    // The width of the question list
    private static final double LIST_WIDTH = 150.0;

    // The share of the height the code takes at first; the instructor
    // can drag the line between the code and the output
    private static final double CODE_SHARE = 0.56;

    // The smallest height of the code or the output box
    private static final double MIN_BOX_HEIGHT = 90.0;

    // The size of the close icon
    private static final double CLOSE_SIZE = 16.0;

    // The size of the icon on the run button
    private static final double RUN_ICON_SIZE = 12.0;

    // The gap between a caption and its box
    private static final double CAPTION_GAP = 8.0;

    // The space between each panel and the line that divides them
    private static final double PANEL_GAP = 10.0;

    // The gap between the question list and the panels
    private static final double COLUMN_GAP = 20.0;

    // A triangle pointing right: run
    private static final String RUN_ICON = "M8 5v14l11-7z";

    // The window that shows the sheet
    private final FlowWindow window;

    // The student whose code is shown
    private final Submission submission;

    // The answer file of the question shown
    private final TextArea code = new TextArea();

    // The width of one digit of the line numbers, in IBM Plex Mono 12px
    private static final double DIGIT_WIDTH = 7.5;

    // The space around the line numbers, left and right together
    private static final double GUTTER_PADDING = 26.0;

    // The line numbers beside the code, scrolled with it
    private final TextArea lineNumbers = new TextArea();

    // The tester output or compiler messages of the question shown
    private final TextArea output = new TextArea();

    // The path of the answer file, in the code panel's header
    private final Label file = new Label("");

    // What the output box holds, in the output panel's header
    private final Label outputTitle = new Label("Tester output");

    // Where the output came from: grading, or a run just now
    private final HBox outputSource = Parts.row(0);

    // How many tests passed, beside where the output came from
    private final Label outputSummary = Parts.muted("");

    // Runs the shown question's tests again
    private final Button rerun = Parts.small("Run tests again",
            () -> rerunShown());

    // One button per question, in the list on the left
    private final List<ToggleButton> questionButtons = new ArrayList<>();

    // Makes the question buttons act as one choice
    private final ToggleGroup questionGroup = new ToggleGroup();

    // The question shown, or null
    private Question shown = null;

    // The issue shown above the code with its buttons, or null
    private Node issue = null;

    // The run of the tests still going whose result is wanted, or null
    private QuestionRerun running = null;

    // Not used: create builds the sheet.
    private CodeSheet(FlowWindow window, Submission submission) {
        this.window = window;
        this.submission = submission;
    }

    /**
     * Builds the sheet for one student.
     *
     * @param window the window that shows it
     * @param submission the student
     * @return the sheet's content
     */
    public static Node create(FlowWindow window, Submission submission) {
        return create(window, submission, null);
    }

    /**
     * Builds the sheet for one student, showing one question first.
     *
     * @param window the window that shows it
     * @param submission the student
     * @param questionId the question shown first, or null for the first
     * @return the sheet's content
     */
    public static Node create(FlowWindow window, Submission submission,
            String questionId) {
        return create(window, submission, questionId, null);
    }

    /**
     * Builds the sheet for one student, showing one question first, with
     * an issue and its buttons above the code, so the issue can be
     * decided while the code is open.
     *
     * @param window the window that shows it
     * @param submission the student
     * @param questionId the question shown first, or null for the first
     * @param issue the issue's card, or null for none
     * @return the sheet's content
     */
    public static Node create(FlowWindow window, Submission submission,
            String questionId, Node issue) {
        CodeSheet sheet = new CodeSheet(window, submission);
        sheet.issue = issue;
        return sheet.build(questionId);
    }

    // ------------------------------------------------------------ build

    // Builds the sheet and shows a question.
    private Node build(String questionId) {
        Button close = new Button();
        close.setGraphic(Parts.icon(Parts.CLOSE, CLOSE_SIZE));
        close.getStyleClass().addAll("flat", "icon-button");
        close.setOnAction(event -> window.closeSheet());
        Parts.tip(close, "Close");
        Label subtitle = Parts.muted("The files after any fixes; the "
                + "original zip is unchanged. Pick a question on the left.");
        subtitle.getStyleClass().add("small-text");
        VBox titles = new VBox(Parts.GAP_SMALL / 2, Parts.title(
                Students.name(submission) + ": code and test output"),
                subtitle);
        HBox top = Parts.row(Parts.GAP,
                new Node[] {titles, Parts.grow(), close});

        // Each panel keeps a gap from the line between them, so the code
        // and the output read as two separate boxes
        StackPane codeHolder = new StackPane(createCodePanel());
        codeHolder.setPadding(new Insets(0, 0, PANEL_GAP, 0));
        StackPane outputHolder = new StackPane(createOutputPanel());
        outputHolder.setPadding(new Insets(PANEL_GAP, 0, 0, 0));
        SplitPane panels = new SplitPane(codeHolder, outputHolder);
        panels.setOrientation(Orientation.VERTICAL);
        panels.setDividerPositions(CODE_SHARE);
        panels.getStyleClass().add("code-split");
        HBox.setHgrow(panels, Priority.ALWAYS);
        HBox middle = new HBox(COLUMN_GAP, createQuestionList(), panels);
        VBox.setVgrow(middle, Priority.ALWAYS);

        VBox body = new VBox(Parts.GAP + Parts.GAP_SMALL, top, middle);
        if (issue != null) {
            body.getChildren().add(1, issue);
        }
        body.getStyleClass().add("code-sheet");
        body.setPrefSize(WIDTH, HEIGHT);
        body.sceneProperty().addListener((observable, before, after)
                -> fitToScene(body, after));
        selectFirst(questionId);
        return body;
    }

    // Builds the list on the left: one button per question, with its
    // mark and how many of its tests passed.
    private VBox createQuestionList() {
        VBox items = new VBox(Parts.GAP_SMALL / 2);
        Assessment assessment = shownAssessment();
        if (assessment != null) {
            for (Question question : assessment.getQuestions()) {
                ToggleButton button = questionButton(question);
                questionButtons.add(button);
                items.getChildren().add(button);
            }
        }
        questionGroup.selectedToggleProperty().addListener((observable,
                before, after) -> questionPicked(before, after));
        Label caption = caption("Questions");
        VBox list = new VBox(CAPTION_GAP, caption, items);
        list.getStyleClass().add("code-question-list");
        list.setMinWidth(LIST_WIDTH);
        list.setPrefWidth(LIST_WIDTH);
        list.setMaxWidth(LIST_WIDTH);
        return list;
    }

    // Builds the button of one question: its id, then its mark and
    // passed tests in small grey text, with a coloured dot.
    private ToggleButton questionButton(Question question) {
        QuestionResult result = savedResult(question.getId());
        Label id = new Label(question.getId());
        id.getStyleClass().add("code-question-id");
        String detail = "Not graded";
        Tone tone = Tone.NEUTRAL;
        if (result != null) {
            detail = Parts.mark(result.getScore()) + " / "
                    + Parts.mark(question.getMaxScore());
            int all = result.getTestCaseResults().size();
            if (all > 0) {
                detail += "  ·  " + countPassed(result) + " of " + all
                        + " tests";
            } else if (Students.didNotCompile(result)) {
                detail += "  ·  did not compile";
            }
            tone = toneOf(result.getScore(), question.getMaxScore());
        }
        Label detailLabel = Parts.muted(detail);
        detailLabel.getStyleClass().add("small-text");
        VBox words = new VBox(id, detailLabel);
        Label dot = new Label();
        dot.getStyleClass().addAll("code-question-dot", tone.getStyleClass());
        HBox content = Parts.row(Parts.GAP_SMALL, new Node[] {dot, words});

        ToggleButton button = new ToggleButton();
        button.setGraphic(content);
        button.setUserData(question.getId());
        button.setToggleGroup(questionGroup);
        button.setMaxWidth(Double.MAX_VALUE);
        button.setAlignment(Pos.CENTER_LEFT);
        button.getStyleClass().setAll("code-question");
        return button;
    }

    // Builds the code panel: a header with the file's path, then the
    // code, which cannot be edited.
    private VBox createCodePanel() {
        code.setEditable(false);
        code.getStyleClass().add("code-area");
        code.setMinHeight(MIN_BOX_HEIGHT);
        VBox.setVgrow(code, Priority.ALWAYS);
        file.getStyleClass().add("code-path");
        Label readOnly = Parts.muted("Read only");
        readOnly.getStyleClass().add("small-text");
        HBox header = Parts.row(Parts.GAP, new Node[] {
            panelTitle("Answer file"), file, Parts.grow(), readOnly});
        header.getStyleClass().add("code-panel-header");
        lineNumbers.setEditable(false);
        lineNumbers.setFocusTraversable(false);
        lineNumbers.setMouseTransparent(true);
        lineNumbers.getStyleClass().addAll("code-area", "line-numbers");
        lineNumbers.setAccessibleText("Line numbers");
        // The numbers move with the code when it scrolls
        lineNumbers.scrollTopProperty().bindBidirectional(
                code.scrollTopProperty());
        HBox.setHgrow(code, Priority.ALWAYS);
        HBox codeRow = new HBox(lineNumbers, code);
        VBox.setVgrow(codeRow, Priority.ALWAYS);
        VBox panel = new VBox(header, codeRow);
        panel.getStyleClass().add("code-panel");
        return panel;
    }

    // Shows one number per line of the code, right-aligned, and sizes
    // the column to the widest number.
    private void showLineNumbers(String text) {
        int lines = text.split("\n", -1).length;
        int digits = String.valueOf(lines).length();
        StringBuilder numbers = new StringBuilder();
        for (int line = 1; line <= lines; line++) {
            numbers.append(String.format("%" + digits + "d", line));
            numbers.append('\n');
        }
        // One more line, so the numbers still reach the last line when
        // the code has a horizontal scroll bar
        numbers.append(' ');
        lineNumbers.setText(numbers.toString());
        double width = digits * DIGIT_WIDTH + GUTTER_PADDING;
        lineNumbers.setMinWidth(width);
        lineNumbers.setPrefWidth(width);
        lineNumbers.setMaxWidth(width);
    }

    // Builds the output panel: a header with where the output came from,
    // how many tests passed and the run button, then the output.
    private VBox createOutputPanel() {
        output.setEditable(false);
        output.getStyleClass().add("code-area");
        output.setMinHeight(MIN_BOX_HEIGHT);
        VBox.setVgrow(output, Priority.ALWAYS);
        outputTitle.getStyleClass().add("code-panel-title");
        outputSummary.getStyleClass().add("small-text");
        rerun.setGraphic(Parts.icon(RUN_ICON, RUN_ICON_SIZE));
        rerun.getStyleClass().add("run-button");
        Parts.tip(rerun, "Compile this question's answer and run its tests "
                + "again, to see fresh output. Nothing is saved: the marks "
                + "stay as they are; use Adjust to change one.");
        boolean canRerun = window.getBackend().supports(
                Capability.RERUN_QUESTION);
        rerun.setVisible(canRerun);
        rerun.setManaged(canRerun);
        HBox header = Parts.row(Parts.GAP, new Node[] {outputTitle,
            outputSource, outputSummary, Parts.grow(), rerun});
        header.getStyleClass().add("code-panel-header");
        VBox panel = new VBox(header, output);
        panel.getStyleClass().add("code-panel");
        return panel;
    }

    // Returns the bold title of a panel's header.
    private static Label panelTitle(String text) {
        Label label = new Label(text);
        label.getStyleClass().add("code-panel-title");
        return label;
    }

    // Selects a question, or the first when it is not listed.
    private void selectFirst(String questionId) {
        for (ToggleButton button : questionButtons) {
            if (button.getUserData().equals(questionId)) {
                button.setSelected(true);
                return;
            }
        }
        if (!questionButtons.isEmpty()) {
            questionButtons.get(0).setSelected(true);
        }
    }

    // Keeps the sheet inside a small window.
    private static void fitToScene(VBox body, Scene scene) {
        if (scene == null) {
            return;
        }
        body.prefWidthProperty().bind(Bindings.min(WIDTH,
                scene.widthProperty().subtract(MARGIN)));
        body.prefHeightProperty().bind(Bindings.min(HEIGHT,
                scene.heightProperty().subtract(MARGIN)));
    }

    // Returns the small grey caption above a box.
    private static Label caption(String text) {
        Label label = Parts.muted(text);
        label.getStyleClass().addAll("small-text", "code-caption");
        return label;
    }

    // ------------------------------------------------------------ show

    // Shows the question of a picked button. Clicking the picked one again
    // would leave none picked, so it stays picked.
    private void questionPicked(Toggle before, Toggle after) {
        if (after == null) {
            if (before != null) {
                before.setSelected(true);
            }
            return;
        }
        showQuestion((String) after.getUserData());
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

    // Returns the saved result of a question, or null.
    private QuestionResult savedResult(String questionId) {
        for (QuestionResult result : submission.getQuestionResults()) {
            if (result.getQuestion().getId().equals(questionId)) {
                return result;
            }
        }
        return null;
    }

    // Shows the answer file and saved output of one question. When the
    // question did not compile, the output is the compiler's messages.
    // A run of another question's tests that is still going is ignored.
    private void showQuestion(String questionId) {
        Assessment assessment = shownAssessment();
        if (questionId == null || assessment == null) {
            return;
        }
        Question question = assessment.findQuestion(questionId);
        if (question == null) {
            return;
        }
        shown = question;
        running = null;
        rerun.setDisable(false);
        rerun.setText("Run tests again");

        String path = question.getFolderName() + "/" + question.getFileName();
        file.setText(path);
        String text = window.getBackend().readSourceFile(submission, path);
        if (text == null || text.isEmpty()) {
            text = path + " could not be read. It may be missing from "
                    + "the zip.";
        }
        code.setText(text);
        code.positionCaret(0);
        showLineNumbers(text);
        code.setScrollTop(0);

        QuestionResult result = savedResult(questionId);
        String out = "No output was recorded for " + questionId + ". The "
                + "question may not have been graded, for example because "
                + "its file is missing.";
        if (result != null && result.getOutput() != null
                && !result.getOutput().isEmpty()) {
            out = result.getOutput();
        }
        showOutput(result, "From grading", Tone.NEUTRAL, out);
    }

    // ------------------------------------------------------------- run

    // Compiles and runs the shown question's tests on a background
    // thread, then shows the fresh output. Nothing is saved.
    private void rerunShown() {
        if (shown == null || running != null) {
            return;
        }
        if (window.getState().isGrading()) {
            window.notify("Wait for the current batch to finish first.");
            return;
        }
        QuestionRerun task = new QuestionRerun(window.getBackend(),
                submission, window.getState().getRun(), shown);
        running = task;
        rerun.setDisable(true);
        rerun.setText("Running...");
        showOutput(null, "Running", Tone.NEUTRAL, "Compiling "
                + shown.getFileName() + " with its tester, then running "
                + "the tests. This takes as long as grading one question.");
        task.setOnSucceeded(event -> rerunDone(task));
        task.setOnFailed(event -> rerunFailed(task));
        Thread worker = new Thread(task, "gradezzy-rerun");
        worker.setDaemon(true);
        worker.start();
    }

    // Shows the fresh output, unless another question is shown now.
    private void rerunDone(QuestionRerun task) {
        if (task != running) {
            return;
        }
        finishRun();
        QuestionResult fresh = task.getValue();
        String out = fresh.getOutput();
        if (out == null || out.isEmpty()) {
            out = "The tester printed nothing.";
        }
        Tone tone = Tone.GOOD;
        if (!fresh.isCompiled() || fresh.isTimedOut()) {
            tone = Tone.BAD;
        } else if (countPassed(fresh) < fresh.getTestCaseResults().size()) {
            tone = Tone.WARN;
        }
        showOutput(fresh, "Run just now", tone, out);
        Parts.tip(outputSummary, "From the run just now. Saved marks are "
                + "unchanged; use Adjust to change one.");
    }

    // Says why the tests could not be run again.
    private void rerunFailed(QuestionRerun task) {
        if (task != running) {
            return;
        }
        finishRun();
        String reason = "The tests could not be run.";
        if (task.getException() != null
                && task.getException().getMessage() != null) {
            reason = task.getException().getMessage();
        }
        showOutput(null, "Could not run", Tone.BAD, reason);
    }

    // Lets the button be pressed again after a run ended.
    private void finishRun() {
        running = null;
        rerun.setDisable(false);
        rerun.setText("Run tests again");
    }

    // ---------------------------------------------------------- helpers

    // Puts a result's output in the output panel, with where it came
    // from and how many tests passed in the header, scrolled to the top.
    private void showOutput(QuestionResult result, String source, Tone tone,
            String text) {
        boolean compilerMessages = result != null
                && (Students.didNotCompile(result) || !result.isCompiled())
                && result.getTestCaseResults().isEmpty()
                && result.getOutput() != null
                && !result.getOutput().isEmpty();
        if (compilerMessages) {
            outputTitle.setText("Compiler messages");
        } else {
            outputTitle.setText("Tester output");
        }
        outputSource.getChildren().setAll(Parts.pill(source, tone));
        outputSummary.setText(summaryOf(result));
        outputSummary.setTooltip(null);
        output.setText(text);
        output.positionCaret(0);
    }

    // Says how many tests passed, such as "2 of 3 tests passed".
    private String summaryOf(QuestionResult result) {
        if (result == null) {
            return "";
        }
        int all = result.getTestCaseResults().size();
        if (all == 0) {
            if (Students.didNotCompile(result) || !result.isCompiled()) {
                return "Did not compile";
            }
            return "";
        }
        String text = countPassed(result) + " of "
                + Parts.plural(all, "test") + " passed";
        if (result.isTimedOut()) {
            text += ", timed out";
        }
        return text;
    }

    // Counts the tests of a result that passed.
    private static int countPassed(QuestionResult result) {
        int passed = 0;
        for (TestCaseResult test : result.getTestCaseResults()) {
            if (test.isPassed()) {
                passed++;
            }
        }
        return passed;
    }

    // Returns the colour of a mark: full marks good, some amber, none red.
    private static Tone toneOf(double score, double max) {
        if (score >= max) {
            return Tone.GOOD;
        } else if (score > 0) {
            return Tone.WARN;
        }
        return Tone.BAD;
    }
}
