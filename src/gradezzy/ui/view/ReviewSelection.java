/*
 * File: ReviewSelection.java
 *
 * Purpose: Gives a review table multiple selection that is remembered in
 * UiState, so the selected rows come back after a reload, a navigation
 * or a theme switch.
 *
 * Where it fits: Review Anomalies and Validation each wrap their table in
 * one. Rows are matched by a key, the anomaly id, because the backend may
 * return new row objects on every reload. The screen is told when the
 * selection changes and when the focused row changes, which JavaFX
 * reports one after the other.
 *
 * AI use: drafted with Claude Code, reviewed by Jun Sheng.
 */
package gradezzy.ui.view;

import java.util.ArrayList;
import java.util.Collection;
import java.util.List;
import java.util.Set;
import java.util.function.Function;

import javafx.collections.FXCollections;
import javafx.collections.ListChangeListener;
import javafx.scene.Node;
import javafx.scene.control.SelectionMode;
import javafx.scene.control.TableView;

/**
 * A remembered multiple selection of table rows.
 *
 * <p>Tier: SHOULD. Owner: Damien.
 *
 * @param <T> the type of the rows
 */
public final class ReviewSelection<T> {

    // The table whose rows are selected
    private final TableView<T> table;

    // The keys of the selected rows, kept in UiState
    private final Set<String> remembered;

    // Returns the key of a row
    private final Function<T, String> keyOf;

    // Run after the selected rows changed
    private final Runnable onChange;

    // Run after the focused row changed
    private final Runnable onFocus;

    // The rows shown in the table
    private final List<T> rows = new ArrayList<>();

    // Whether the selection is being set by code
    private boolean restoring = false;

    /**
     * Sets up multiple selection on a table.
     *
     * @param table the table
     * @param remembered the keys of the selected rows, kept in UiState
     * @param keyOf returns the key of a row
     * @param onChange run after the instructor changed the selection
     * @param onFocus run after the focused row changed
     */
    public ReviewSelection(TableView<T> table, Set<String> remembered,
            Function<T, String> keyOf, Runnable onChange, Runnable onFocus) {
        this.table = table;
        this.remembered = remembered;
        this.keyOf = keyOf;
        this.onChange = onChange;
        this.onFocus = onFocus;

        table.getSelectionModel().setSelectionMode(SelectionMode.MULTIPLE);
        table.setColumnResizePolicy(
                TableView.CONSTRAINED_RESIZE_POLICY_FLEX_LAST_COLUMN);
        table.getSelectionModel().getSelectedItems().addListener(
                (ListChangeListener<T>) change -> selectionChanged());
        table.getSelectionModel().selectedItemProperty().addListener(
                (observable, oldValue, newValue) -> focusChanged());
    }

    /**
     * Shows new rows, or an explanation when there are none, and selects
     * the remembered rows again.
     *
     * @param shown the rows, in table order
     * @param placeholder what the table shows when it has no rows
     */
    public void show(List<T> shown, Node placeholder) {
        rows.clear();
        rows.addAll(shown);
        table.setPlaceholder(placeholder);
        restoring = true;
        table.setItems(FXCollections.observableArrayList(rows));
        restoring = false;
        // The rows may be the same objects with a new decision, which a
        // table does not redraw by itself.
        table.refresh();
        restore();
    }

    /**
     * Selects the rows with the given keys, and only those.
     *
     * @param keys the keys of the rows to select
     */
    public void select(Collection<String> keys) {
        remembered.clear();
        remembered.addAll(keys);
        restore();
    }

    /**
     * Selects every row shown.
     */
    public void selectAll() {
        table.getSelectionModel().selectAll();
    }

    /**
     * Clears the selection and forgets it.
     */
    public void clear() {
        table.getSelectionModel().clearSelection();
        remembered.clear();
    }

    /**
     * Returns the selected rows.
     *
     * @return the rows, in table order
     */
    public List<T> getSelected() {
        List<T> selected = new ArrayList<>();
        for (T row : table.getSelectionModel().getSelectedItems()) {
            if (row != null) {
                selected.add(row);
            }
        }
        return selected;
    }

    /**
     * Returns the focused row, the one shown in the inspector.
     *
     * @return the row, or null when nothing is selected
     */
    public T getFocused() {
        return table.getSelectionModel().getSelectedItem();
    }

    /**
     * Returns the focused row as a list of zero or one.
     *
     * @return the list
     */
    public List<T> getFocusedList() {
        List<T> focused = new ArrayList<>();
        T row = getFocused();
        if (row != null) {
            focused.add(row);
        }
        return focused;
    }

    // Selects the shown rows whose keys are remembered.
    private void restore() {
        restoring = true;
        table.getSelectionModel().clearSelection();
        for (int index = 0; index < rows.size(); index++) {
            if (remembered.contains(keyOf.apply(rows.get(index)))) {
                table.getSelectionModel().select(index);
            }
        }
        restoring = false;
    }

    // Remembers the selection the instructor made and tells the screen.
    private void selectionChanged() {
        if (restoring) {
            return;
        }
        remembered.clear();
        for (T row : table.getSelectionModel().getSelectedItems()) {
            if (row != null) {
                remembered.add(keyOf.apply(row));
            }
        }
        onChange.run();
    }

    // Tells the screen the focused row changed. JavaFX updates the
    // focused row after the selected list, so both are followed.
    private void focusChanged() {
        if (!restoring) {
            onFocus.run();
        }
    }
}
