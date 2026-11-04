/*
 * File: QuestionFields.java
 *
 * Purpose: The four fields of one question in the assessment editor: its
 * id, folder and file, and its maximum mark as read from its tester,
 * laid out as one row.
 *
 * Where it fits: AssessmentEditor keeps one per question and reads them
 * back when the instructor saves.
 */
package gradezzy.ui.flow;

import javafx.geometry.Pos;
import javafx.scene.Node;
import javafx.scene.control.Hyperlink;
import javafx.scene.control.Label;
import javafx.scene.control.TextField;
import javafx.scene.layout.HBox;
import javafx.scene.layout.Priority;

import gradezzy.model.Question;
import gradezzy.testcase.TesterMarks;

/**
 * One question's row in the assessment editor.
 */
public class QuestionFields {

    /** The width of the id and mark columns. */
    public static final double SHORT_WIDTH = 72.0;

    /** The width of the folder column. */
    public static final double FOLDER_WIDTH = 110.0;

    /** The width of the Remove link. */
    public static final double LINK_WIDTH = 60.0;

    // The ending of an answer file
    private static final String JAVA_EXTENSION = ".java";

    // The question id, such as Q1a
    private final TextField id = new TextField();

    // The folder the answer is in, such as Q1
    private final TextField folder = new TextField();

    // The answer file, such as Q1a.java
    private final TextField file = new TextField();

    // The maximum mark read from the question's tester, such as 3
    private final Label mark = new Label();

    // The maximum mark shown, or TesterMarks.UNKNOWN
    private double maxScore = TesterMarks.UNKNOWN;

    // The row that holds the fields and the Remove link
    private final HBox row;

    // What Remove does, set by the editor once this row exists
    private Runnable onRemove = null;

    /**
     * Creates the fields of a question, filled from it when one is given.
     *
     * @param question the question, or null for an empty row
     * @param onChange called when the id changes, so the editor can read
     *        the question's maximum mark from its tester again
     */
    public QuestionFields(Question question, Runnable onChange) {
        if (question != null) {
            id.setText(question.getId());
            folder.setText(question.getFolderName());
            file.setText(question.getFileName());
            setMaxScore(question.getMaxScore());
        }
        id.setPromptText("Q1a");
        folder.setPromptText("Q1");
        file.setPromptText("Q1a.java");
        id.setPrefWidth(SHORT_WIDTH);
        folder.setPrefWidth(FOLDER_WIDTH);
        mark.setPrefWidth(SHORT_WIDTH);
        file.setMaxWidth(Double.MAX_VALUE);
        HBox.setHgrow(file, Priority.ALWAYS);
        Parts.tip(id, "A short name for the question, letters and digits "
                + "only, such as Q1a. Testers are found by it: Q1aTester.");
        Parts.tip(folder, "The folder the answer is in, inside the "
                + "student's zip, such as Q1.");
        Parts.tip(file, "The Java file the student writes, such as "
                + "Q1a.java.");
        mark.getStyleClass().add("question-max");
        mark.setAlignment(Pos.CENTER_RIGHT);

        // A new id fills in the folder and file the usual way, once
        id.focusedProperty().addListener((seen, before, now) ->
                suggestNames(now));
        id.textProperty().addListener((seen, before, now) ->
                onChange.run());

        Hyperlink remove = Parts.link("Remove", () -> removeClicked());
        remove.getStyleClass().add("small-text");
        remove.setMinWidth(LINK_WIDTH);
        row = Parts.row(Parts.GAP_SMALL, new Node[] {id, folder, file, mark,
            remove});
    }

    /**
     * Sets what Remove does.
     *
     * @param action the action, usually taking this row off the list
     */
    public void setOnRemove(Runnable action) {
        onRemove = action;
    }

    /**
     * Returns the row to put in the list.
     *
     * @return the row
     */
    public HBox getRow() {
        return row;
    }

    /**
     * Returns the typed id.
     *
     * @return the id, trimmed
     */
    public String getId() {
        return id.getText().trim();
    }

    /**
     * Returns the typed folder.
     *
     * @return the folder, trimmed
     */
    public String getFolder() {
        return folder.getText().trim();
    }

    /**
     * Returns the typed file name.
     *
     * @return the file name, trimmed
     */
    public String getFile() {
        return file.getText().trim();
    }

    /**
     * Returns the maximum mark read from the question's tester.
     *
     * @return the mark, or TesterMarks.UNKNOWN when it is not known
     */
    public double getMaxScore() {
        return maxScore;
    }

    /**
     * Shows the maximum mark read from the question's tester, or "?" with
     * a tip saying why when it is not known.
     *
     * @param score the mark, or TesterMarks.UNKNOWN
     */
    public void setMaxScore(double score) {
        maxScore = score;
        if (score > TesterMarks.UNKNOWN) {
            mark.setText(Parts.mark(score));
            Parts.tip(mark, "Read from the question's tester: the sum of "
                    + "its \"score += <number>\" lines.");
        } else {
            String example = getId();
            if (example.isEmpty()) {
                example = "Q1a";
            }
            mark.setText("?");
            Parts.tip(mark, "The tester folder has no tester with marks "
                    + "for this question yet, such as " + example
                    + "Tester.java with \"score += 1;\" lines.");
        }
    }

    /**
     * Puts the cursor in the id field.
     */
    public void focus() {
        id.requestFocus();
    }

    // Runs the Remove action, once it is set.
    private void removeClicked() {
        if (onRemove != null) {
            onRemove.run();
        }
    }

    // When the id field loses focus, fills an empty folder with the id's
    // leading letter and digits (Q1a gives Q1) and an empty file with the
    // id and .java.
    private void suggestNames(boolean focused) {
        String typed = getId();
        if (focused || typed.isEmpty()) {
            return;
        }
        if (getFolder().isEmpty()) {
            folder.setText(leadingFolder(typed));
        }
        if (getFile().isEmpty()) {
            file.setText(typed + JAVA_EXTENSION);
        }
    }

    // Returns an id without the letters after its last digit, so Q1a
    // gives Q1 and Q3 stays Q3.
    private String leadingFolder(String typed) {
        int end = typed.length();
        while (end > 0 && Character.isLetter(typed.charAt(end - 1))) {
            end--;
        }
        if (end == 0) {
            return typed;
        }
        return typed.substring(0, end);
    }
}
