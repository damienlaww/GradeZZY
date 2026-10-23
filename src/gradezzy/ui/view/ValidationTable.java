/*
 * File: ValidationTable.java
 *
 * Purpose: Builds the Validation table's columns, status line and empty states.
 *
 * Where it fits: The Validation screen creates one in onAttach. Each row
 * is one validation finding and its proposed repair.
 */
package gradezzy.ui.view;

import java.util.List;

import javafx.scene.control.TableCell;
import javafx.scene.control.TableColumn;
import javafx.scene.control.TableView;
import javafx.scene.control.Tooltip;

import gradezzy.model.GradingRun;
import gradezzy.ui.AppShell;
import gradezzy.ui.Screen;
import gradezzy.ui.backend.RepairItem;
import gradezzy.ui.backend.ReviewRules;
import gradezzy.ui.component.EmptyState;
import gradezzy.ui.component.Icon;
import gradezzy.ui.component.Labels;
import gradezzy.ui.component.StatusBadge;

/**
 * The columns, status line and empty states of the finding table.
 */
public final class ValidationTable {

    // The narrowest the student column may get
    private static final double STUDENT_WIDTH = 110.0;

    // The width the student column starts from
    private static final double STUDENT_PREF = 140.0;

    // The narrowest the finding column may get
    private static final double FINDING_WIDTH = 160.0;

    // The width the finding column starts from
    private static final double FINDING_PREF = 230.0;

    // The narrowest each value column may get
    private static final double VALUE_WIDTH = 100.0;

    // The width each value column starts from
    private static final double VALUE_PREF = 160.0;

    // The narrowest the state column may get
    private static final double STATE_WIDTH = 184.0;

    // The window, for the empty-state buttons
    private final AppShell shell;

    /**
     * Adds the Student, Finding, Original, Proposed and State columns.
     *
     * @param table the table
     * @param students the students of the run
     * @param shell the window, for the empty-state buttons
     */
    public ValidationTable(TableView<RepairItem> table,
            ReviewStudents students, AppShell shell) {
        this.shell = shell;

        TableColumn<RepairItem, RepairItem> student = ReviewColumns.add(
                table, "Student", STUDENT_WIDTH, STUDENT_PREF);
        student.setCellFactory(column -> ReviewColumns.studentCell(
                students, RepairItem::getAnomaly));
        TableColumn<RepairItem, RepairItem> finding = ReviewColumns.add(
                table, "Finding", FINDING_WIDTH, FINDING_PREF);
        finding.setCellFactory(column -> ReviewColumns.findingCell(
                RepairItem::getAnomaly));
        TableColumn<RepairItem, RepairItem> original = ReviewColumns.add(
                table, "Original", VALUE_WIDTH, VALUE_PREF);
        original.setCellFactory(column -> new ValueCell(true));
        TableColumn<RepairItem, RepairItem> proposed = ReviewColumns.add(
                table, "Proposed", VALUE_WIDTH, VALUE_PREF);
        proposed.setCellFactory(column -> new ValueCell(false));
        TableColumn<RepairItem, RepairItem> state = ReviewColumns.add(
                table, "State", STATE_WIDTH, STATE_WIDTH);
        state.setCellFactory(column -> ReviewColumns.badgeCell(
                repair -> StatusBadge.forFix(repair.getAnomaly()
                .getFixStatus())));
    }

    /**
     * Builds the status line that counts the findings.
     *
     * @param run the run of the section, or null
     * @param repairs every finding of the run
     * @param shownCount how many findings pass the filters
     * @param selectedCount how many findings are selected
     * @param section the section's name, such as "CS102 G1"
     * @return the status line
     */
    public String statusText(GradingRun run, List<RepairItem> repairs,
            int shownCount, int selectedCount, String section) {
        if (run == null) {
            return "No grading run for " + section
                    + " yet, so there is nothing to validate.";
        }
        int waiting = 0;
        int blocked = 0;
        for (RepairItem repair : repairs) {
            if (repair.isBlocked()) {
                blocked++;
            } else if (!ReviewRules.isRepairSettled(repair.getAnomaly())) {
                waiting++;
            }
        }
        String text = repairs.size() + " finding"
                + Labels.plural(repairs.size()) + " for " + section + ", "
                + waiting + " waiting for a decision and " + blocked
                + " with no automatic repair. " + shownCount + " shown, "
                + selectedCount + " selected.";
        if (selectedCount == 0 && shownCount > 0) {
            text += " Select findings to approve or reject their repairs.";
        }
        return text;
    }

    /**
     * Builds the empty state that says why no finding is shown.
     *
     * @param run the run of the section, or null
     * @param anyFindings whether the run has findings at all
     * @param section the section's name, such as "CS102 G1"
     * @param clearFilters clears the student and state filters
     * @return the empty state
     */
    public EmptyState emptyState(GradingRun run, boolean anyFindings,
            String section, Runnable clearFilters) {
        if (run == null) {
            EmptyState empty = new EmptyState(Icon.VALIDATION,
                    "No grading run for " + section + " yet",
                    "Validation findings appear once a batch for this "
                    + "section is graded. Add the submissions first, or "
                    + "choose another section above.");
            empty.setAction("Go to Submissions",
                    () -> shell.navigate(Screen.UPLOAD));
            return empty;
        }
        if (!anyFindings) {
            return new EmptyState(Icon.CHECK, "No validation findings",
                    "Every submission had the expected folders, files and "
                    + "headers.");
        }
        EmptyState empty = new EmptyState(Icon.SEARCH, "No findings match",
                "Change the student or state filter to see more findings.");
        empty.setAction("Clear Filters", clearFilters);
        return empty;
    }

    /**
     * Shows the original or proposed value, monospaced and wrapped.
     */
    private static final class ValueCell
            extends TableCell<RepairItem, RepairItem> {

        // Whether the cell shows the original value
        private final boolean original;

        // Creates a value cell.
        ValueCell(boolean original) {
            this.original = original;
            getStyleClass().add("mono");
            setWrapText(true);
        }

        /**
         * Shows one value.
         *
         * @param repair the row's finding
         * @param empty whether the row is empty
         */
        @Override
        protected void updateItem(RepairItem repair, boolean empty) {
            super.updateItem(repair, empty);
            setGraphic(null);
            if (empty || repair == null) {
                setText(null);
                setTooltip(null);
                return;
            }
            String value = repair.getProposedValue();
            if (original) {
                value = repair.getOriginalValue();
            }
            setText(value);
            setTooltip(new Tooltip(value));
        }
    }
}
