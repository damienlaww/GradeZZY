/*
 * File: AdjustScoreSheet.java
 *
 * Purpose: The Adjust Score sheet of the Student Evidence screen: a
 * question choice, the new mark beside the maximum and current mark, a
 * required reason and an inline problem message, with Cancel and Save.
 *
 * Where it fits: StudentReportController creates one for the student on
 * show and passes the sheet to AppShell.showSheet. Save checks the form
 * with validate and hands the values to the controller, which calls the
 * backend and records the change in the audit log.
 *
 * AI use: drafted with Claude Code, reviewed by Jun Sheng.
 */
package gradezzy.ui.view;

import java.util.ArrayList;
import java.util.List;

import javafx.collections.FXCollections;
import javafx.geometry.Pos;
import javafx.geometry.VPos;
import javafx.scene.Node;
import javafx.scene.control.ComboBox;
import javafx.scene.control.Label;
import javafx.scene.control.TextArea;
import javafx.scene.control.TextField;
import javafx.scene.layout.ColumnConstraints;
import javafx.scene.layout.GridPane;
import javafx.scene.layout.HBox;
import javafx.scene.layout.Priority;

import gradezzy.model.QuestionResult;
import gradezzy.model.Submission;
import gradezzy.ui.component.Labels;
import gradezzy.ui.component.Sheet;

/**
 * The sheet that changes one mark by hand.
 *
 * <p>Tier: SHOULD. Owner: Damien.
 */
public final class AdjustScoreSheet {

    // The gap between the mark field and the maximum beside it
    private static final double ROW_GAP = 8.0;

    // The number of text rows of the reason field
    private static final int REASON_ROWS = 3;

    // The number of characters the mark field shows
    private static final int MARK_COLUMNS = 6;

    // The sheet shown over the window
    private final Sheet sheet;

    // The student whose mark changes
    private final Submission target;

    // What Save does with a valid form
    private final SaveAction onSave;

    // The question choice
    private final ComboBox<String> questionBox = new ComboBox<>();

    // The new mark
    private final TextField markField = new TextField();

    // The maximum and current mark beside the field
    private final Label maxLabel = new Label();

    // Why the mark changes
    private final TextArea reasonArea = new TextArea();

    // The problem with the form, hidden when there is none
    private final Label problemLabel = new Label();

    /**
     * What Save does with a valid form.
     */
    public interface SaveAction {

        /**
         * Saves the new mark.
         *
         * @param questionId the question
         * @param score the new mark
         * @param reason why the mark changed
         */
        void save(String questionId, double score, String reason);
    }

    /**
     * Builds the sheet for one student.
     *
     * @param target the student whose mark changes
     * @param questionId the question chosen first
     * @param onSave what Save does with a valid form
     * @param onCancel what Cancel does
     */
    public AdjustScoreSheet(Submission target, String questionId,
            SaveAction onSave, Runnable onCancel) {
        this.target = target;
        this.onSave = onSave;
        sheet = new Sheet("Adjust a mark for " + target.getUsername());
        sheet.setMessage("The new mark replaces the tester's mark for one "
                + "question. A reason is required and is kept with the "
                + "change. You can undo it.");

        List<String> ids = new ArrayList<>();
        for (QuestionResult result : target.getQuestionResults()) {
            ids.add(result.getQuestion().getId());
        }
        questionBox.setItems(FXCollections.observableArrayList(ids));
        questionBox.setAccessibleText("Question");
        questionBox.valueProperty().addListener(
                (observable, oldValue, newValue) -> questionChanged(
                newValue));
        markField.setPrefColumnCount(MARK_COLUMNS);
        markField.setAccessibleText("New mark");
        reasonArea.setPrefRowCount(REASON_ROWS);
        reasonArea.setWrapText(true);
        reasonArea.setPromptText("For example: the tester data had a typo "
                + "in case 3.");
        reasonArea.setAccessibleText("Reason for the change, required");
        problemLabel.getStyleClass().add("field-error");
        problemLabel.setWrapText(true);
        showProblem("");

        sheet.addContent(buildForm());
        sheet.addSecondaryButton("Cancel", onCancel);
        sheet.addPrimaryButton("Save", false, () -> save());
        if (ids.contains(questionId)) {
            questionBox.setValue(questionId);
        } else if (!ids.isEmpty()) {
            questionBox.setValue(ids.get(0));
        }
    }

    /**
     * Checks the form.
     *
     * @param reasonText the reason typed
     * @param scoreText the new mark typed
     * @param max the most the question can earn
     * @return an empty string when the form is valid, or a plain message
     */
    public static String validate(String reasonText, String scoreText,
            double max) {
        String notNumber = "Enter the new mark as a number, such as 2 or "
                + "2.5.";
        if (reasonText == null || reasonText.trim().isEmpty()) {
            return "Enter a reason for the change. It is kept with the new "
                    + "mark.";
        }
        if (scoreText == null || scoreText.trim().isEmpty()) {
            return notNumber;
        }

        double score;
        try {
            score = Double.parseDouble(scoreText.trim());
        } catch (NumberFormatException exception) {
            return notNumber;
        }
        if (Double.isNaN(score) || Double.isInfinite(score)) {
            return notNumber;
        }
        if (score < 0.0) {
            return "The new mark cannot be below 0.";
        }
        if (score > max) {
            return "The new mark cannot be above " + Labels.formatScore(max)
                    + ", the most this question can earn.";
        }
        return "";
    }

    /**
     * Returns the sheet to show over the window.
     *
     * @return the sheet
     */
    public Sheet getSheet() {
        return sheet;
    }

    /**
     * Shows a problem under the fields, or hides it when empty.
     *
     * @param message the problem, or an empty string
     */
    public void showProblem(String message) {
        problemLabel.setText(message);
        problemLabel.setVisible(!message.isEmpty());
        problemLabel.setManaged(!message.isEmpty());
    }

    // Lays out the labelled fields.
    private GridPane buildForm() {
        GridPane form = new GridPane();
        form.getStyleClass().add("detail-grid");
        ColumnConstraints names = new ColumnConstraints();
        ColumnConstraints fields = new ColumnConstraints();
        fields.setHgrow(Priority.ALWAYS);
        form.getColumnConstraints().setAll(names, fields);

        HBox markRow = new HBox(ROW_GAP, markField, maxLabel);
        markRow.setAlignment(Pos.CENTER_LEFT);
        form.add(fieldName("Question", questionBox), 0, 0);
        form.add(questionBox, 1, 0);
        form.add(fieldName("New mark", markField), 0, 1);
        form.add(markRow, 1, 1);
        Label reasonName = fieldName("Reason", reasonArea);
        GridPane.setValignment(reasonName, VPos.TOP);
        form.add(reasonName, 0, 2);
        form.add(reasonArea, 1, 2);
        form.add(problemLabel, 1, 3);
        return form;
    }

    // Creates the name of a field, linked to it for screen readers.
    private Label fieldName(String text, Node field) {
        Label name = new Label(text);
        name.getStyleClass().add("detail-name");
        name.setLabelFor(field);
        return name;
    }

    // Shows the maximum and current mark of the chosen question.
    private void questionChanged(String questionId) {
        QuestionResult result = EvidenceQuestions.find(target, questionId);
        if (result == null) {
            maxLabel.setText("");
            return;
        }
        String current = Labels.formatScore(result.getScore());
        maxLabel.setText("of " + Labels.formatScore(
                result.getQuestion().getMaxScore()) + ", now " + current);
        markField.setText(current);
        showProblem("");
    }

    // Checks the fields and hands a valid form to the save action.
    private void save() {
        QuestionResult result = EvidenceQuestions.find(target,
                questionBox.getValue());
        if (result == null) {
            showProblem("Choose a question.");
            return;
        }
        String problem = validate(reasonArea.getText(), markField.getText(),
                result.getQuestion().getMaxScore());
        if (!problem.isEmpty()) {
            showProblem(problem);
            return;
        }
        onSave.save(questionBox.getValue(), Double.parseDouble(
                markField.getText().trim()), reasonArea.getText().trim());
    }
}
