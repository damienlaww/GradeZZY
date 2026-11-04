/*
 * File: ReviewTickCell.java
 *
 * Purpose: The check box at the start of each row of the Review table,
 * which adds the row's student to the export scope.
 *
 * Where it fits: ReviewScreen's tick column makes one per visible row.
 * The cell asks the screen whether its student is ticked and tells it
 * when the box is clicked.
 */
package gradezzy.ui.flow;

import javafx.scene.control.CheckBox;
import javafx.scene.control.TableCell;

import gradezzy.model.Submission;
import gradezzy.ui.backend.ResultState;

/**
 * A check box that adds the row's student to the export scope.
 */
public class ReviewTickCell extends TableCell<Submission, Submission> {

    // The screen that keeps the ticked students
    private final ReviewScreen screen;

    // The check box
    private final CheckBox box = new CheckBox();

    /**
     * Creates the cell.
     *
     * @param screen the screen that keeps the ticked students
     */
    public ReviewTickCell(ReviewScreen screen) {
        this.screen = screen;
        box.setOnAction(event -> boxClicked());
    }

    /**
     * Shows the box, ticked when the row's student is ticked.
     *
     * @param submission the student, or null
     * @param isEmpty true when the row shows no student
     */
    @Override
    protected void updateItem(Submission submission, boolean isEmpty) {
        super.updateItem(submission, isEmpty);
        // A student with no submission has no marks to export
        if (isEmpty || submission == null
                || ResultState.isNoSubmission(submission)) {
            setGraphic(null);
            return;
        }
        box.setSelected(screen.isTickedForExport(submission));
        box.setAccessibleText("Tick " + submission.getUsername());
        setGraphic(box);
    }

    // Tells the screen that the row's student was ticked or unticked.
    private void boxClicked() {
        screen.setTickedForExport(getItem(), box.isSelected());
    }
}
