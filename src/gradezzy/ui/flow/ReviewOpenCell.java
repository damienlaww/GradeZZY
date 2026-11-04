/*
 * File: ReviewOpenCell.java
 *
 * Purpose: The last cell of a Review row: a small arrow that shows the
 * row can be clicked to open the student's marks, tests, fixes and flags.
 *
 * Where it fits: ReviewScreen's table makes one per visible row of its
 * last column. Clicking anywhere on the row opens the student panel; the
 * arrow only makes that visible.
 */
package gradezzy.ui.flow;

import javafx.scene.control.TableCell;

import gradezzy.model.Submission;

/**
 * The arrow at the end of a Review row.
 */
public class ReviewOpenCell extends TableCell<Submission, Submission> {

    // The size of the arrow
    private static final double ARROW_SIZE = 16.0;

    /**
     * Creates the cell.
     */
    public ReviewOpenCell() {
        getStyleClass().add("open-cell");
        Parts.tip(this, "Open this student: each question's tests, the "
                + "fixes to approve and the flags to look at.");
    }

    /**
     * Shows the arrow on a row that holds a student.
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
        setGraphic(Parts.icon(Parts.CHEVRON, ARROW_SIZE));
    }
}
