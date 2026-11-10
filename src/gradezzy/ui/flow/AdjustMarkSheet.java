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

import java.util.function.Supplier;

import javafx.geometry.Pos;
import javafx.scene.Node;
import javafx.scene.control.Button;
import javafx.scene.control.Label;
import javafx.scene.control.TextArea;
import javafx.scene.control.TextField;
import javafx.scene.layout.HBox;
import javafx.scene.layout.VBox;

import gradezzy.model.GradingRun;
import gradezzy.model.QuestionResult;
import gradezzy.model.ScoreAdjustment;
import gradezzy.model.Submission;
import gradezzy.ui.backend.ActionResult;
import gradezzy.util.InputValidator;

/**
 * Builds the sheet that changes one mark.
 *
 * <p>Tier: SHOULD.
 */
public final class AdjustMarkSheet {

    // The width of the mark field
    private static final double MARK_WIDTH = 80.0;

    // The number of text rows of the reason field
    private static final int REASON_ROWS = 3;

    // The gap between a field's name and the field
    private static final double LABEL_GAP = 6.0;

    // Comes between the student and the change in the activity log
    private static final String USER_SEPARATOR = ": ";

    // Ends the change before its reason in the activity log
    private static final String SENTENCE_END = ". ";

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

        TextField mark = new TextField();
        mark.setPromptText(Parts.mark(result.getScore()));
        mark.setPrefWidth(MARK_WIDTH);
        mark.setMaxWidth(MARK_WIDTH);
        mark.setAccessibleText("New mark");
        Label outOf = Parts.muted("out of " + Parts.mark(max)
                + ". The tester gave " + Parts.mark(result.getScore())
                + ".");
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
        // Save is on only once a different mark and a reason are typed
        mark.textProperty().addListener((observable, before, after)
                -> refreshSave(save, mark, reason, result));
        reason.textProperty().addListener((observable, before, after)
                -> refreshSave(save, mark, reason, result));
        refreshSave(save, mark, reason, result);
        HBox buttons = Parts.row(Parts.GAP,
                new Node[] {Parts.grow(), cancel, save});

        Label title = Parts.title("Adjust " + questionId + " for "
                + Students.name(submission));
        VBox body = new VBox(Parts.GAP, title, help,
                field("New mark", markRow), field("Reason", reason),
                problem, buttons);
        body.getStyleClass().add("adjust-sheet");
        body.setMaxWidth(Parts.TEXT_WIDTH);
        return body;
    }

    // Turns Save on or off for what is typed.
    private static void refreshSave(Button save, TextField mark,
            TextArea reason, QuestionResult result) {
        boolean allowed = canSave(mark.getText(), reason.getText(),
                result.getScore());
        save.setDisable(!allowed);
    }

    /**
     * Says whether the typed mark differs from the current one and a
     * reason is typed, so Save can be pressed. A mark that is not a
     * number still turns Save on, so pressing it says what is wrong.
     *
     * @param markText the new mark as typed
     * @param reasonText the reason as typed
     * @param current the mark now
     * @return true when Save can be pressed
     */
    public static boolean canSave(String markText, String reasonText,
            double current) {
        boolean missing = markText == null || markText.trim().isEmpty()
                || reasonText == null || reasonText.trim().isEmpty();
        if (missing) {
            return false;
        }
        double typed = parseMark(markText);
        return Double.isNaN(typed) || typed != current;
    }

    /**
     * Reads a typed mark strictly, as InputValidator.parseMark does: 2,
     * 2.5 or 2,5 are marks, 2f and 1e0 are not.
     *
     * @param markText the mark as typed
     * @return the mark, or NaN when the text is not a mark
     */
    public static double parseMark(String markText) {
        return InputValidator.parseMark(markText);
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
        double score = parseMark(markText);
        if (Double.isNaN(score)) {
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
        GradingRun run = window.getState().getRun();
        double newMark = parseMark(markText);
        ActionResult saved = window.getBackend().adjustScore(run, submission,
                result.getQuestion().getId(), newMark, reasonText.trim());
        if (!saved.isDone()) {
            showProblem(problem, saved.getMessage());
            return;
        }
        window.closeSheet();
        // The change is in the activity log, with Undo there and here
        ScoreAdjustment change = newestChange(window, submission,
                result.getQuestion().getId());
        if (change == null) {
            window.notify(saved.getMessage());
        } else {
            Supplier<ActionResult> undo = ActivityUndo.record(
                    window.getBackend(), ActivityUndo.MARK_CHANGED,
                    describe(change),
                    () -> undoAdjustment(window, run, change));
            window.notify(saved.getMessage(), "Undo",
                    () -> undoChange(window, undo, onSaved));
        }
        onSaved.run();
    }

    // Takes a changed mark back in the backend.
    private static ActionResult undoAdjustment(FlowWindow window,
            GradingRun run, ScoreAdjustment change) {
        return window.getBackend().undoAdjustment(run, change);
    }

    // Takes a changed mark back and says how it went.
    private static void undoChange(FlowWindow window,
            Supplier<ActionResult> undo, Runnable onSaved) {
        ActionResult result = undo.get();
        window.notify(result.getMessage());
        onSaved.run();
    }

    /**
     * Describes a changed mark for the activity log, such as
     * "learner.bravo: Q1b 3 to 2. The tester data had a typo".
     *
     * @param change the change
     * @return the words
     */
    public static String describe(ScoreAdjustment change) {
        return change.getUsername() + USER_SEPARATOR
                + change.getQuestionId() + " "
                + Parts.mark(change.getPreviousScore()) + " to "
                + Parts.mark(change.getNewScore()) + SENTENCE_END
                + change.getReason();
    }

    // Returns the newest change of a student's question, or null.
    private static ScoreAdjustment newestChange(FlowWindow window,
            Submission submission, String questionId) {
        ScoreAdjustment newest = null;
        for (ScoreAdjustment change : window.getBackend().listAdjustments(
                window.getState().getRun(), submission)) {
            if (change.getQuestionId().equals(questionId)) {
                newest = change;
            }
        }
        return newest;
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
        boolean shown = !message.isEmpty();
        problem.setVisible(shown);
        problem.setManaged(shown);
    }
}
