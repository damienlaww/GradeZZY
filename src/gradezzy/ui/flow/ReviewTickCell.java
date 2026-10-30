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

/**
 * A check box that adds the row's student to the export scope.
 *
 * <p>Tier: SHOULD.
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
     * <p>Tier: SHOULD.
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
        box.setSelected(screen.isTickedForExport(submission));
        setGraphic(box);
    }

    // Tells the screen that the row's student was ticked or unticked.
    private void boxClicked() {
        screen.setTickedForExport(getItem(), box.isSelected());
    }
}
