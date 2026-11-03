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

import javafx.beans.binding.Bindings;
import javafx.scene.Node;
import javafx.scene.Scene;
import javafx.scene.control.Button;
import javafx.scene.control.Label;
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

    // The largest size of the sheet's content
    private static final double WIDTH = 920.0;
    private static final double HEIGHT = 560.0;

    // The space kept around the sheet in a small window, including the
    // sheet's own padding
    private static final double MARGIN = 120.0;

    // The width of the question list
    private static final double LIST_WIDTH = 120.0;

    // The height of the tester output box
    private static final double OUTPUT_HEIGHT = 150.0;

    // The size of the close icon
    private static final double CLOSE_SIZE = 16.0;

    // The gap between a caption and its box
    private static final double CAPTION_GAP = 6.0;

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
        VBox.setVgrow(code, Priority.ALWAYS);
        TextArea output = new TextArea();
        output.setEditable(false);
        output.getStyleClass().add("code-area");
        output.setPrefHeight(OUTPUT_HEIGHT);
        output.setMinHeight(OUTPUT_HEIGHT);
        Label file = caption("Answer file");

        ListView<String> questions = new ListView<>();
        questions.getStyleClass().add("code-questions");
        VBox.setVgrow(questions, Priority.ALWAYS);
        Assessment assessment = window.getBackend().getAssessment();
        if (assessment != null) {
            for (Question question : assessment.getQuestions()) {
                questions.getItems().add(question.getId());
            }
        }
        questions.getSelectionModel().selectedItemProperty().addListener(
                (observable, before, after) -> showQuestion(window,
                submission, after, file, code, output));

        Button close = new Button();
        close.setGraphic(Parts.icon(Parts.CLOSE, CLOSE_SIZE));
        close.getStyleClass().addAll("flat", "icon-button");
        close.setOnAction(event -> window.closeSheet());
        VBox titles = new VBox(Parts.title(Students.name(submission)
                + ": code and test output"), Parts.muted("Files after any "
                + "fixes. The original zip is unchanged."));
        HBox top = Parts.row(Parts.GAP,
                new Node[] {titles, Parts.grow(), close});

        VBox left = new VBox(CAPTION_GAP, caption("Question"), questions);
        left.setMinWidth(LIST_WIDTH);
        left.setPrefWidth(LIST_WIDTH);
        left.setMaxWidth(LIST_WIDTH);
        VBox right = new VBox(CAPTION_GAP, file, code,
                caption("Tester output"), output);
        HBox.setHgrow(right, Priority.ALWAYS);
        HBox middle = new HBox(Parts.GAP + Parts.GAP_SMALL / 2, left,
                right);
        VBox.setVgrow(middle, Priority.ALWAYS);

        VBox body = new VBox(Parts.GAP + Parts.GAP_SMALL / 2, top, middle);
        body.getStyleClass().add("code-sheet");
        body.setPrefSize(WIDTH, HEIGHT);
        body.sceneProperty().addListener((observable, before, after)
                -> fitToScene(body, after));
        if (!questions.getItems().isEmpty()) {
            questions.getSelectionModel().selectFirst();
        }
        return body;
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

    // Shows the answer file and output of one question.
    private static void showQuestion(FlowWindow window,
            Submission submission, String questionId, Label file,
            TextArea code, TextArea output) {
        Assessment assessment = window.getBackend().getAssessment();
        if (questionId == null || assessment == null) {
            return;
        }
        Question question = assessment.findQuestion(questionId);
        if (question == null) {
            return;
        }
        String path = question.getFolderName() + "/" + question.getFileName();
        file.setText("Answer file: " + path);
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
