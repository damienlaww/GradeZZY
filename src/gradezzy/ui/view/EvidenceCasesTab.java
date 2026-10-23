/*
 * File: EvidenceCasesTab.java
 *
 * Purpose: Fills the Cases tab of Student Evidence, with one row per test
 * case and the selected case's details.
 *
 * Where it fits: StudentReportController calls fill each time the screen
 * is shown. Picking a row tells the controller, so Logs and Source follow.
 */
package gradezzy.ui.view;

import java.util.ArrayList;
import java.util.List;
import java.util.function.Consumer;

import javafx.beans.property.ReadOnlyObjectWrapper;
import javafx.beans.property.ReadOnlyStringWrapper;
import javafx.collections.FXCollections;
import javafx.scene.Node;
import javafx.scene.control.SelectionMode;
import javafx.scene.control.TableCell;
import javafx.scene.control.TableColumn;
import javafx.scene.control.TableView;
import javafx.scene.layout.GridPane;

import gradezzy.model.Submission;
import gradezzy.ui.component.EmptyState;
import gradezzy.ui.component.Icon;
import gradezzy.ui.component.Responsive;

/**
 * The Cases tab of the Student Evidence screen.
 */
public final class EvidenceCasesTab {

    // The share of the width given to the case table
    private static final double CASES_PERCENT = 56.0;

    // The width below which the case details move under the table
    private static final double CASES_STACK_WIDTH = 620.0;

    // The narrowest the question column may get
    private static final double QUESTION_WIDTH = 76.0;

    // The narrowest the case column may get
    private static final double CASE_WIDTH = 56.0;

    // The narrowest the outcome column may get
    private static final double OUTCOME_WIDTH = 130.0;

    // The narrowest the marks column may get
    private static final double MARKS_WIDTH = 70.0;

    // The table of cases
    private final TableView<EvidenceCaseRow> table;

    // The details beside the table
    private final EvidenceCaseDetails details;

    // Told which question the instructor picked
    private final Consumer<String> onQuestionChosen;

    // The rows of the table
    private final List<EvidenceCaseRow> rows = new ArrayList<>();

    // Whether the selection is being made by code
    private boolean updating = false;

    /**
     * Creates the tab over the nodes it fills.
     *
     * @param table the table of cases
     * @param grid the grid that holds the table and the details
     * @param tablePanel the panel around the table
     * @param detailsPanel the panel of the details
     * @param details fills the details panel
     * @param onQuestionChosen told the question of a picked row
     */
    public EvidenceCasesTab(TableView<EvidenceCaseRow> table, GridPane grid,
            Node tablePanel, Node detailsPanel, EvidenceCaseDetails details,
            Consumer<String> onQuestionChosen) {
        this.table = table;
        this.details = details;
        this.onQuestionChosen = onQuestionChosen;
        Responsive.twoColumns(grid, tablePanel, detailsPanel, CASES_PERCENT,
                CASES_STACK_WIDTH);
        buildColumns();
        table.getSelectionModel().setSelectionMode(SelectionMode.SINGLE);
        table.setColumnResizePolicy(
                TableView.CONSTRAINED_RESIZE_POLICY_FLEX_LAST_COLUMN);
        table.getSelectionModel().selectedItemProperty().addListener(
                (observable, oldValue, newValue) -> caseChosen(newValue));
    }

    /**
     * Lists every case of one student and selects the default case of the
     * question in focus.
     *
     * @param shown the student
     * @param questionId the question in focus
     */
    public void fill(Submission shown, String questionId) {
        rows.clear();
        rows.addAll(EvidenceCaseRow.rowsOf(shown));

        updating = true;
        table.setItems(FXCollections.observableArrayList(rows));
        table.setPlaceholder(new EmptyState(Icon.UNGRADED,
                "No case results yet", "This submission has not been "
                + "graded, so no tester has run. Retry Entire Submission "
                + "grades it."));
        EvidenceCaseRow chosen = defaultRow(questionId);
        if (chosen != null) {
            table.getSelectionModel().select(chosen);
            table.scrollTo(firstRowOf(chosen.getQuestionId()));
        }
        updating = false;
        details.show(chosen, !rows.isEmpty());
    }

    // Picks the row shown first: unfinished, else failed, else any case.
    private EvidenceCaseRow defaultRow(String questionId) {
        EvidenceCaseRow unfinished = null;
        EvidenceCaseRow failed = null;
        EvidenceCaseRow first = null;
        for (EvidenceCaseRow row : rows) {
            if (row.getQuestionId().equals(questionId)) {
                if (unfinished == null && row.isUnfinished()) {
                    unfinished = row;
                }
                if (failed == null && !row.isPassed()) {
                    failed = row;
                }
                if (first == null) {
                    first = row;
                }
            }
        }
        if (unfinished != null) {
            return unfinished;
        } else if (failed != null) {
            return failed;
        } else if (first != null) {
            return first;
        } else if (!rows.isEmpty()) {
            return rows.get(0);
        }
        return null;
    }

    // Finds a question's first row, so all its cases scroll into view.
    private int firstRowOf(String questionId) {
        for (int index = 0; index < rows.size(); index++) {
            if (rows.get(index).getQuestionId().equals(questionId)) {
                return index;
            }
        }
        return 0;
    }

    // Reacts to a case chosen by the instructor.
    private void caseChosen(EvidenceCaseRow row) {
        if (updating || row == null) {
            return;
        }
        details.show(row, true);
        onQuestionChosen.accept(row.getQuestionId());
    }

    // Adds the Question, Case, Outcome and Marks columns.
    private void buildColumns() {
        TableColumn<EvidenceCaseRow, String> question = new TableColumn<>(
                "Question");
        question.setCellValueFactory(data -> new ReadOnlyStringWrapper(
                data.getValue().getQuestionId()));
        question.setMinWidth(QUESTION_WIDTH);
        TableColumn<EvidenceCaseRow, String> number = new TableColumn<>(
                "Case");
        number.setCellValueFactory(data -> new ReadOnlyStringWrapper(
                data.getValue().getCaseText()));
        number.setMinWidth(CASE_WIDTH);
        TableColumn<EvidenceCaseRow, EvidenceCaseRow> outcome =
                new TableColumn<>("Outcome");
        outcome.setCellValueFactory(data -> new ReadOnlyObjectWrapper<>(
                data.getValue()));
        outcome.setCellFactory(column -> new OutcomeCell());
        outcome.setMinWidth(OUTCOME_WIDTH);
        TableColumn<EvidenceCaseRow, String> marks = new TableColumn<>(
                "Marks");
        marks.setCellValueFactory(data -> new ReadOnlyStringWrapper(
                data.getValue().getMarksText()));
        marks.setMinWidth(MARKS_WIDTH);

        List<TableColumn<EvidenceCaseRow, ?>> columns = new ArrayList<>();
        columns.add(question);
        columns.add(number);
        columns.add(outcome);
        columns.add(marks);
        for (TableColumn<EvidenceCaseRow, ?> column : columns) {
            column.setSortable(false);
        }
        table.getColumns().setAll(columns);
    }

    /**
     * Shows a case's outcome as an icon and words.
     */
    private static class OutcomeCell
            extends TableCell<EvidenceCaseRow, EvidenceCaseRow> {

        /**
         * Shows one outcome.
         *
         * @param row the table row's case
         * @param empty whether the row is empty
         */
        @Override
        protected void updateItem(EvidenceCaseRow row, boolean empty) {
            super.updateItem(row, empty);
            setText(null);
            if (empty || row == null) {
                setGraphic(null);
                setAccessibleText(null);
                return;
            }
            setGraphic(row.createBadge());
            setAccessibleText(row.getQuestionId() + " case "
                    + row.getCaseText() + ": " + row.getOutcomeText());
        }
    }
}
