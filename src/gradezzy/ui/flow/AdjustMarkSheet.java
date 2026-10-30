/*
 * File: AdjustMarkSheet.java
 *
 * Purpose: A small sheet that changes one question's mark by hand: the
 * new mark (from 0 to the question's maximum), a required reason, and
 * Cancel and Save.
 *
 * Where it fits: Opened from the Adjust link of a question in the
 * student panel on Review. Saves through DashboardBackend.adjustScore;
 * the panel lists the change with Undo.
 */
package gradezzy.ui.flow;

import javafx.geometry.Pos;
import javafx.scene.Node;
import javafx.scene.control.Button;
import javafx.scene.control.Label;
import javafx.scene.control.TextArea;
import javafx.scene.control.TextField;
import javafx.scene.layout.HBox;
import javafx.scene.layout.VBox;

import gradezzy.model.QuestionResult;
import gradezzy.model.Submission;
import gradezzy.ui.backend.ActionResult;

/**
 * Builds the sheet that changes one mark.
 */
public final class AdjustMarkSheet {

    // The width of the mark field
    private static final double MARK_WIDTH = 80.0;

    // The number of text rows of the reason field
    private static final int REASON_ROWS = 3;

    // The gap between a field's name and the field
    private static final double LABEL_GAP = 6.0;

    // Not used: every method is static.
    private AdjustMarkSheet() {
    }

    /**
     * Builds the sheet for one question of one student.
     *
     * @param window the window that shows it
     * @param submission the student
     * @param result the question's result, whose mark changes
     * @param onSaved called after the new mark is saved
     * @return the sheet's content
     */
    public static Node create(FlowWindow window, Submission submission,
            QuestionResult result, Runnable onSaved) {
        String questionId = result.getQuestion().getId();
        double max = result.getQuestion().getMaxScore();

        Label help = Parts.subtitle("The new mark replaces the tester's "
                + "mark for " + questionId + ". The reason is kept with "
                + "the change, and you can undo it.");
        help.setMaxWidth(Parts.TEXT_WIDTH);

        TextField mark = new TextField(Parts.mark(result.getScore()));
        mark.setPrefWidth(MARK_WIDTH);
        mark.setMaxWidth(MARK_WIDTH);
        mark.setAccessibleText("New mark");
        Label outOf = Parts.muted("out of " + Parts.mark(max) + ", now "
                + Parts.mark(result.getScore()));
        HBox markRow = Parts.row(Parts.GAP_SMALL, new Node[] {mark, outOf});

        TextArea reason = new TextArea();
        reason.setPrefRowCount(REASON_ROWS);
        reason.setWrapText(true);
        reason.setPrefWidth(Parts.TEXT_WIDTH);
        reason.setMaxWidth(Parts.TEXT_WIDTH);
        reason.setPromptText("For example: the tester data had a typo in "
                + "case 3.");
        reason.setAccessibleText("Reason for the change, required");

        Label problem = Parts.muted("");
        problem.getStyleClass().add("field-problem");
        problem.setWrapText(true);
        problem.setMaxWidth(Parts.TEXT_WIDTH);
        showProblem(problem, "");

        Button cancel = Parts.secondary("Cancel", () -> window.closeSheet());
        Button save = Parts.primary("Save");
        save.setOnAction(event -> save(window, submission, result, mark,
                reason, problem, onSaved));
        HBox buttons = Parts.row(Parts.GAP,
                new Node[] {Parts.grow(), cancel, save});

        VBox body = new VBox(Parts.GAP,
                Parts.title("Adjust " + questionId + " for "
                + Students.name(submission)), help,
                field("New mark", markRow), field("Reason", reason),
                problem, buttons);
        body.getStyleClass().add("adjust-sheet");
        body.setMaxWidth(Parts.TEXT_WIDTH);
        return body;
    }

    /**
     * Checks the typed mark and reason.
     *
     * @param markText the new mark as typed
     * @param reasonText the reason as typed
     * @param max the most the question can earn
     * @return an empty string when both are valid, or the problem
     */
    public static String validate(String markText, String reasonText,
            double max) {
        String notNumber = "Enter the new mark as a number, such as 2 or "
                + "2.5.";
        if (markText == null || markText.trim().isEmpty()) {
            return notNumber;
        }
        double score;
        try {
            score = Double.parseDouble(markText.trim());
        } catch (NumberFormatException exception) {
            return notNumber;
        }
        if (Double.isNaN(score) || Double.isInfinite(score)) {
            return notNumber;
        }
        if (score < 0.0 || score > max) {
            return "Enter a mark from 0 to " + Parts.mark(max) + ".";
        }
        if (reasonText == null || reasonText.trim().isEmpty()) {
            return "Enter a reason for the change. It is kept with the "
                    + "new mark.";
        }
        return "";
    }

    // Checks the form, then saves the mark and closes the sheet.
    private static void save(FlowWindow window, Submission submission,
            QuestionResult result, TextField mark, TextArea reason,
            Label problem, Runnable onSaved) {
        String markText = mark.getText();
        String reasonText = reason.getText();
        String error = validate(markText, reasonText,
                result.getQuestion().getMaxScore());
        if (!error.isEmpty()) {
            showProblem(problem, error);
            return;
        }
        ActionResult saved = window.getBackend().adjustScore(submission,
                result.getQuestion().getId(),
                Double.parseDouble(markText.trim()), reasonText.trim());
        if (!saved.isDone()) {
            showProblem(problem, saved.getMessage());
            return;
        }
        window.closeSheet();
        window.notify(saved.getMessage());
        onSaved.run();
    }

    // Puts a field's name above the field.
    private static VBox field(String name, Node control) {
        Label label = Parts.muted(name);
        label.getStyleClass().add("field-label");
        VBox box = new VBox(LABEL_GAP, label, control);
        box.setAlignment(Pos.TOP_LEFT);
        return box;
    }

    // Shows a problem under the fields, or hides it when empty.
    private static void showProblem(Label problem, String message) {
        problem.setText(message);
        problem.setVisible(!message.isEmpty());
        problem.setManaged(!message.isEmpty());
    }
}
