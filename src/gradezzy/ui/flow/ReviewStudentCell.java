/*
 * File: ReviewStudentCell.java
 *
 * Purpose: The Student cell of the Review table: the student's name
 * above the username.
 *
 * Where it fits: ReviewScreen's Student column makes one per visible
 * row.
 */
package gradezzy.ui.flow;

import javafx.geometry.Pos;
import javafx.scene.control.Label;
import javafx.scene.control.TableCell;
import javafx.scene.layout.VBox;

import gradezzy.model.Submission;

/**
 * The student's name above the username.
 */
public class ReviewStudentCell extends TableCell<Submission, Submission> {

    /**
     * Creates an empty cell.
     */
    public ReviewStudentCell() {
        // the content is set in updateItem
    }

    /**
     * Shows the name and username of a student.
     *
     * @param submission the student, or null
     * @param isEmpty true when the row shows no student
     */
    @Override
    protected void updateItem(Submission submission, boolean isEmpty) {
        super.updateItem(submission, isEmpty);
        if (isEmpty || submission == null) {
            setGraphic(null);
            return;
        }
        Label name = Parts.strong(Students.name(submission));
        Label username = Parts.muted(submission.getUsername());
        username.getStyleClass().add("small-text");
        VBox box = new VBox(name, username);
        box.getStyleClass().add("student-cell");
        box.setAlignment(Pos.CENTER_LEFT);
        box.setMinWidth(0);
        setGraphic(box);
    }
}
