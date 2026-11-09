/*
 * File: ReviewRow.java
 *
 * Purpose: One row of the Review table. It knows whether it shows the
 * last student, so it draws no line under itself on top of the table's
 * border.
 *
 * Where it fits: ReviewScreen's table makes one per visible row through
 * its row factory.
 */
package gradezzy.ui.flow;

import javafx.css.PseudoClass;
import javafx.scene.control.TableRow;
import javafx.scene.control.TableView;

import gradezzy.model.Submission;

/**
 * A row of the Review table that marks itself when it is the last.
 *
 * <p>Tier: SHOULD.
 */
public class ReviewRow extends TableRow<Submission> {

    // Marks the last row, so it has no line under it
    private static final PseudoClass LAST_ROW =
            PseudoClass.getPseudoClass("last-row");

    // The table the row belongs to
    private final TableView<Submission> table;

    /**
     * Creates a row of a table.
     *
     * @param table the table the row belongs to
     */
    public ReviewRow(TableView<Submission> table) {
        this.table = table;
    }

    /**
     * Moves the row to another position and checks whether it is last.
     *
     * @param index the new position
     */
    @Override
    public void updateIndex(int index) {
        super.updateIndex(index);
        markLast();
    }

    /**
     * Shows another student and checks whether the row is last.
     *
     * @param submission the student, or null
     * @param isEmpty true when the row shows no student
     */
    @Override
    protected void updateItem(Submission submission, boolean isEmpty) {
        super.updateItem(submission, isEmpty);
        markLast();
    }

    // Sets the last-row style when this row shows the last student.
    private void markLast() {
        int lastIndex = table.getItems().size() - 1;
        boolean last = !isEmpty() && getIndex() == lastIndex;
        pseudoClassStateChanged(LAST_ROW, last);
    }
}
