/*
 * File: ReviewColumns.java
 *
 * Purpose: The table columns and cells the review screens share: the
 * student with their full name, the finding or flag with its message,
 * and a status badge.
 *
 * Where it fits: The Review Anomalies and Validation tables are built
 * from these, so both show students and findings the same way. Each
 * column receives the whole row; a function picks the anomaly or the
 * badge out of it.
 *
 * AI use: drafted with Claude Code, reviewed by Jun Sheng.
 */
package gradezzy.ui.view;

import java.util.function.Function;

import javafx.beans.property.ReadOnlyObjectWrapper;
import javafx.geometry.Pos;
import javafx.scene.Node;
import javafx.scene.control.Label;
import javafx.scene.control.TableCell;
import javafx.scene.control.TableColumn;
import javafx.scene.control.TableView;
import javafx.scene.control.Tooltip;
import javafx.scene.layout.VBox;

import gradezzy.model.Anomaly;
import gradezzy.ui.component.Labels;

/**
 * Shared columns and cells of the review tables.
 *
 * <p>Tier: SHOULD. Owner: Damien.
 */
public final class ReviewColumns {

    // The CSS class of the bold username
    private static final String BOLD = "context-value";

    // The CSS class of the second, smaller line
    private static final String SECONDARY = "secondary-text";

    // Utility class, never instantiated.
    private ReviewColumns() {
        // nothing to set up
    }

    /**
     * Adds a column whose cells receive the whole row. Columns cannot be
     * sorted by their header, because the screen keeps its own order.
     *
     * @param <T> the type of the rows
     * @param table the table
     * @param title the column header
     * @param minWidth the narrowest the column may get
     * @param prefWidth the width the column starts from
     * @return the column
     */
    public static <T> TableColumn<T, T> add(TableView<T> table,
            String title, double minWidth, double prefWidth) {
        TableColumn<T, T> column = new TableColumn<>(title);
        column.setCellValueFactory(data -> new ReadOnlyObjectWrapper<>(
                data.getValue()));
        column.setMinWidth(minWidth);
        column.setPrefWidth(prefWidth);
        column.setSortable(false);
        table.getColumns().add(column);
        return column;
    }

    /**
     * Creates a cell that shows the username in bold with the full name
     * under it.
     *
     * @param <T> the type of the rows
     * @param students the students of the run
     * @param anomalyOf picks the anomaly out of a row
     * @return the cell
     */
    public static <T> TableCell<T, T> studentCell(ReviewStudents students,
            Function<T, Anomaly> anomalyOf) {
        return new StudentCell<>(students, anomalyOf);
    }

    /**
     * Creates a cell that shows a finding's title with its message under
     * it. The whole message is in the tooltip.
     *
     * @param <T> the type of the rows
     * @param anomalyOf picks the anomaly out of a row
     * @return the cell
     */
    public static <T> TableCell<T, T> findingCell(
            Function<T, Anomaly> anomalyOf) {
        return new FindingCell<>(anomalyOf);
    }

    /**
     * Creates a cell that shows a status badge.
     *
     * @param <T> the type of the rows
     * @param badgeOf builds the badge of a row
     * @return the cell
     */
    public static <T> TableCell<T, T> badgeCell(Function<T, Node> badgeOf) {
        return new BadgeCell<>(badgeOf);
    }

    /**
     * Shows the username in bold with the full name under it.
     *
     * @param <T> the type of the rows
     */
    private static final class StudentCell<T> extends TableCell<T, T> {

        // The students of the run
        private final ReviewStudents students;

        // Picks the anomaly out of a row
        private final Function<T, Anomaly> anomalyOf;

        // Creates the cell.
        StudentCell(ReviewStudents students, Function<T, Anomaly> anomalyOf) {
            this.students = students;
            this.anomalyOf = anomalyOf;
        }

        /**
         * Shows the student of one row.
         *
         * @param row the row
         * @param empty whether the row is empty
         */
        @Override
        protected void updateItem(T row, boolean empty) {
            super.updateItem(row, empty);
            setText(null);
            if (empty || row == null) {
                setGraphic(null);
                setAccessibleText(null);
                return;
            }
            String username = anomalyOf.apply(row).getUsername();
            Label name = new Label(username);
            name.getStyleClass().add(BOLD);
            VBox box = new VBox(1, name);
            String fullName = students.fullName(username);
            String spoken = username;
            if (!fullName.isEmpty()) {
                Label secondary = new Label(fullName);
                secondary.getStyleClass().add(SECONDARY);
                box.getChildren().add(secondary);
                spoken = username + ", " + fullName;
            }
            box.setAlignment(Pos.CENTER_LEFT);
            setGraphic(box);
            setAccessibleText(spoken);
        }
    }

    /**
     * Shows a finding's title with its message under it.
     *
     * @param <T> the type of the rows
     */
    private static final class FindingCell<T> extends TableCell<T, T> {

        // Picks the anomaly out of a row
        private final Function<T, Anomaly> anomalyOf;

        // Creates the cell.
        FindingCell(Function<T, Anomaly> anomalyOf) {
            this.anomalyOf = anomalyOf;
        }

        /**
         * Shows the finding of one row.
         *
         * @param row the row
         * @param empty whether the row is empty
         */
        @Override
        protected void updateItem(T row, boolean empty) {
            super.updateItem(row, empty);
            setText(null);
            if (empty || row == null) {
                setGraphic(null);
                setTooltip(null);
                setAccessibleText(null);
                return;
            }
            Anomaly anomaly = anomalyOf.apply(row);
            String title = Labels.anomalyTitle(anomaly.getType());
            Label message = new Label(anomaly.getMessage());
            message.getStyleClass().add(SECONDARY);
            VBox box = new VBox(1, new Label(title), message);
            box.setAlignment(Pos.CENTER_LEFT);
            setGraphic(box);
            setTooltip(new Tooltip(anomaly.getMessage()));
            setAccessibleText(title + ". " + anomaly.getMessage());
        }
    }

    /**
     * Shows a status badge.
     *
     * @param <T> the type of the rows
     */
    private static final class BadgeCell<T> extends TableCell<T, T> {

        // Builds the badge of a row
        private final Function<T, Node> badgeOf;

        // Creates the cell.
        BadgeCell(Function<T, Node> badgeOf) {
            this.badgeOf = badgeOf;
        }

        /**
         * Shows the badge of one row.
         *
         * @param row the row
         * @param empty whether the row is empty
         */
        @Override
        protected void updateItem(T row, boolean empty) {
            super.updateItem(row, empty);
            setText(null);
            if (empty || row == null) {
                setGraphic(null);
                return;
            }
            setGraphic(badgeOf.apply(row));
        }
    }
}
