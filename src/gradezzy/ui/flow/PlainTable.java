/*
 * File: PlainTable.java
 *
 * Purpose: A simple read-only table: a white box with a muted header
 * row and hairlines between rows only, no shadow and no line under the
 * last row. Each column has a fixed width or takes the spare width.
 *
 * Where it fits: The Runs tab (runs, backups, activity) and the
 * questions of the Settings tab use it, so every small table in the
 * window looks the same.
 */
package gradezzy.ui.flow;

import java.util.ArrayList;
import java.util.List;

import javafx.geometry.Pos;
import javafx.scene.Node;
import javafx.scene.control.Label;
import javafx.scene.layout.HBox;
import javafx.scene.layout.Priority;
import javafx.scene.layout.Region;
import javafx.scene.layout.VBox;

/**
 * A read-only table built from rows of nodes.
 */
public class PlainTable extends VBox {

    /** Pass as a width for the column that takes the spare width. */
    public static final double GROW = -1.0;

    // How far a fixed column may shrink in a narrow window, as a share
    private static final double SHRINK = 0.8;

    // The narrowest the growing column may get, enough for a date
    private static final double MIN_GROW_WIDTH = 140.0;

    // The width of each column, or GROW
    private final List<Double> widths = new ArrayList<>();

    // How each column lines up its content
    private final List<Pos> alignments = new ArrayList<>();

    // The header row
    private final HBox header = new HBox();

    // The rows under the header
    private final VBox rows = new VBox();

    // The height of every row under the header
    private final double rowHeight;

    /**
     * Creates an empty table.
     *
     * @param rowHeight the height of every row under the header
     */
    public PlainTable(double rowHeight) {
        this.rowHeight = rowHeight;
        getStyleClass().add("plain-table");
        header.getStyleClass().add("plain-header");
        header.setAlignment(Pos.CENTER_LEFT);
        getChildren().addAll(header, rows);
        setMaxHeight(Region.USE_PREF_SIZE);
    }

    /**
     * Adds a column at the right.
     *
     * @param title the header text, or an empty string
     * @param width the width in pixels, or GROW for the spare width
     * @param alignment how the column lines up its content
     */
    public void addColumn(String title, double width, Pos alignment) {
        widths.add(width);
        alignments.add(alignment);
        Label label = new Label(title);
        label.getStyleClass().add("plain-header-text");
        header.getChildren().add(cell(header.getChildren().size(),
                label));
    }

    /**
     * Adds a row under the others. Pass one node per column.
     *
     * @param cells the content of each column, left to right
     */
    public void addRow(Node[] cells) {
        HBox row = new HBox();
        row.getStyleClass().add("plain-row");
        row.setAlignment(Pos.CENTER_LEFT);
        row.setMinHeight(rowHeight);
        row.setPrefHeight(rowHeight);
        for (int i = 0; i < cells.length && i < widths.size(); i++) {
            row.getChildren().add(cell(i, cells[i]));
        }
        rows.getChildren().add(row);
    }

    /**
     * Removes every row under the header.
     */
    public void clearRows() {
        rows.getChildren().clear();
    }

    /**
     * Shows a message in place of the rows, such as an empty state.
     *
     * @param message what to show
     */
    public void showMessage(Node message) {
        rows.getChildren().clear();
        VBox box = new VBox(message);
        box.getStyleClass().add("plain-empty");
        box.setAlignment(Pos.CENTER);
        rows.getChildren().add(box);
    }

    /**
     * Returns the number of rows under the header.
     *
     * @return the number of rows
     */
    public int getRowCount() {
        return rows.getChildren().size();
    }

    // Wraps a node in a cell of the given column's width.
    private HBox cell(int column, Node content) {
        HBox box = new HBox(content);
        box.getStyleClass().add("plain-cell");
        box.setAlignment(alignments.get(column));
        double width = widths.get(column);
        if (width < 0) {
            box.setMinWidth(MIN_GROW_WIDTH);
            box.setPrefWidth(MIN_GROW_WIDTH);
            box.setMaxWidth(Double.MAX_VALUE);
            HBox.setHgrow(box, Priority.ALWAYS);
        } else {
            box.setMinWidth(width * SHRINK);
            box.setPrefWidth(width);
            box.setMaxWidth(width);
        }
        return box;
    }
}
