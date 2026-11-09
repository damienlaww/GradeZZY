/*
 * File: ReviewMarkCell.java
 *
 * Purpose: A mark cell of the Review table: the mark, grey when it is
 * zero, and a dash when there is none.
 *
 * Where it fits: ReviewScreen's question columns and Total column make
 * one per visible row.
 */
package gradezzy.ui.flow;

import javafx.geometry.Pos;
import javafx.scene.control.TableCell;

import gradezzy.model.Submission;

/**
 * A mark, grey when zero, a dash when there is none.
 *
 * <p>Tier: SHOULD.
 */
public class ReviewMarkCell extends TableCell<Submission, Double> {

    // Shown when there is no mark
    private static final String NO_MARK = "-";

    // True for the bold total column
    private final boolean bold;

    /**
     * Creates the cell.
     *
     * @param bold true for the bold total column
     */
    public ReviewMarkCell(boolean bold) {
        this.bold = bold;
        setAlignment(Pos.CENTER);
    }

    /**
     * Shows a mark.
     *
     * @param mark the mark, or null when there is none
     * @param isEmpty true when the row shows no student
     */
    @Override
    protected void updateItem(Double mark, boolean isEmpty) {
        super.updateItem(mark, isEmpty);
        getStyleClass().removeAll("zero", "total");
        if (isEmpty) {
            setText(null);
            return;
        }
        if (mark == null) {
            setText(NO_MARK);
            getStyleClass().add("zero");
            return;
        }
        setText(Parts.mark(mark));
        if (mark == 0.0) {
            getStyleClass().add("zero");
        }
        if (bold) {
            getStyleClass().add("total");
        }
    }
}
