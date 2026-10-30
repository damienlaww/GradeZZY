/*
 * File: ReviewStatusCell.java
 *
 * Purpose: The Status cell of the Review table: the student's status
 * pill.
 *
 * Where it fits: ReviewScreen's Status column makes one per visible
 * row. The backend says which flags still need review.
 */
package gradezzy.ui.flow;

import javafx.scene.control.TableCell;

import gradezzy.model.Submission;
import gradezzy.ui.backend.DashboardBackend;

/**
 * The status pill of a student.
 *
 * <p>Tier: MUST.
 */
public class ReviewStatusCell extends TableCell<Submission, Submission> {

    // The backend that knows the review decisions
    private final DashboardBackend backend;

    /**
     * Creates the cell.
     *
     * @param backend the backend that knows the review decisions
     */
    public ReviewStatusCell(DashboardBackend backend) {
        this.backend = backend;
    }

    /**
     * Shows the status pill of a student.
     *
     * <p>Tier: MUST.
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
        setGraphic(Students.pill(submission, backend));
    }
}
