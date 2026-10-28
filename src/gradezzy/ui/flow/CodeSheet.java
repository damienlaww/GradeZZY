/*
 * File: CodeSheet.java
 *
 * Purpose: A large sheet that shows one student's answer file and the
 * tester's output for each question, read from the working copy.
 *
 * Where it fits: Opened from the student panel on Review. Reads files
 * only through DashboardBackend.readSourceFile.
 */
package gradezzy.ui.flow;

import javafx.scene.Node;
import javafx.scene.control.Button;
import javafx.scene.control.ListView;
import javafx.scene.control.TextArea;
import javafx.scene.layout.HBox;
import javafx.scene.layout.Priority;
import javafx.scene.layout.VBox;

import gradezzy.model.Assessment;
import gradezzy.model.Question;
import gradezzy.model.QuestionResult;
import gradezzy.model.Submission;

/**
 * Builds the code and test output sheet.
 */
public final class CodeSheet {

    // The size of the sheet
    private static final double WIDTH = 920.0;
    private static final double HEIGHT = 560.0;

    // The width of the question list
    private static final double LIST_WIDTH = 150.0;

    // The size of the close icon
    private static final double CLOSE_SIZE = 16.0;

    // Not used: every method is static.
    private CodeSheet() {
    }

    /**
     * Builds the sheet for one student.
     *
     * @param window the window that shows it
     * @param submission the student
     * @return the sheet's content
     */
    public static Node create(FlowWindow window, Submission submission) {
        TextArea code = new TextArea();
        code.setEditable(false);
        code.getStyleClass().add("code-area");
        TextArea output = new TextArea();
        output.setEditable(false);
        output.getStyleClass().add("code-area");
        VBox.setVgrow(code, Priority.ALWAYS);

        ListView<String> questions = new ListView<>();
        questions.setPrefWidth(LIST_WIDTH);
        Assessment assessment = window.getBackend().getAssessment();
        if (assessment != null) {
            for (Question question : assessment.getQuestions()) {
                questions.getItems().add(question.getId());
            }
        }
        questions.getSelectionModel().selectedItemProperty().addListener(
                (observable, before, after) -> showQuestion(window,
                submission, after, code, output));

        Button close = new Button();
        close.setGraphic(Parts.icon(Parts.CLOSE, CLOSE_SIZE));
        close.getStyleClass().addAll("flat", "icon-button");
        close.setOnAction(event -> window.closeSheet());
        HBox top = Parts.row(Parts.GAP, Parts.title(Students.name(submission)
                + "  ·  code and test output"), Parts.grow(), close);

        VBox right = new VBox(Parts.GAP_SMALL, Parts.muted("Answer file, "
                + "after any fixes. The original zip is unchanged."), code,
                Parts.muted("Tester output"), output);
        HBox.setHgrow(right, Priority.ALWAYS);
        HBox middle = new HBox(Parts.GAP, questions, right);
        VBox.setVgrow(middle, Priority.ALWAYS);

        VBox body = new VBox(Parts.GAP, top, middle);
        body.setPrefSize(WIDTH, HEIGHT);
        if (!questions.getItems().isEmpty()) {
            questions.getSelectionModel().selectFirst();
        }
        return body;
    }

    // Shows the answer file and output of one question.
    private static void showQuestion(FlowWindow window,
            Submission submission, String questionId, TextArea code,
            TextArea output) {
        Assessment assessment = window.getBackend().getAssessment();
        if (questionId == null || assessment == null) {
            return;
        }
        Question question = assessment.findQuestion(questionId);
        if (question == null) {
            return;
        }
        String path = question.getFolderName() + "/" + question.getFileName();
        String text = window.getBackend().readSourceFile(submission, path);
        if (text == null || text.isEmpty()) {
            text = path + " could not be read. It may be missing from "
                    + "the zip.";
        }
        code.setText(text);

        String out = "No output was recorded for " + questionId + ".";
        for (QuestionResult result : submission.getQuestionResults()) {
            if (result.getQuestion().getId().equals(questionId)
                    && result.getOutput() != null
                    && !result.getOutput().isEmpty()) {
                out = result.getOutput();
            }
        }
        output.setText(out);
    }
}
