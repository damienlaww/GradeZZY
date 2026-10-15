/*
 * File: ResultsColumns.java
 *
 * Purpose: Builds the columns of the Results table (Student, one column
 * per question with its maximum, Total and Status) and the orders the
 * table can be sorted in.
 *
 * Where it fits: Used by ResultsController. Ungraded marks show a dash,
 * a real zero shows 0, and compile failures and timeouts carry an icon
 * and words, so they never look alike. Ungraded students sort last.
 *
 * AI use: drafted with Claude Code, reviewed by Jun Sheng.
 */
package gradezzy.ui.view;

import java.util.Comparator;

import javafx.beans.property.ReadOnlyObjectWrapper;
import javafx.geometry.Pos;
import javafx.scene.control.Label;
import javafx.scene.control.TableCell;
import javafx.scene.control.TableColumn;
import javafx.scene.control.TableView;
import javafx.scene.layout.VBox;

import gradezzy.model.Assessment;
import gradezzy.model.Question;
import gradezzy.model.QuestionResult;
import gradezzy.model.Student;
import gradezzy.model.Submission;
import gradezzy.ui.SortOrder;
import gradezzy.ui.backend.DashboardBackend;
import gradezzy.ui.backend.ResultState;
import gradezzy.ui.component.Labels;
import gradezzy.ui.component.QuestionScoreCell;
import gradezzy.ui.component.StatusBadge;

/**
 * The columns and sort orders of the Results table.
 *
 * <p>Tier: MUST. Owner: Damien.
 */
public final class ResultsColumns {

    // The narrowest the student column may get
    private static final double STUDENT_WIDTH = 150.0;

    // The narrowest each question column may get
    private static final double QUESTION_WIDTH = 56.0;

    // The narrowest the total column may get
    private static final double TOTAL_WIDTH = 68.0;

    // The narrowest the status column may get
    private static final double STATUS_WIDTH = 128.0;

    // Answers which flags still wait for review
    private final DashboardBackend backend;

    /**
     * Creates the column builder.
     *
     * @param backend answers which flags still wait for review
     */
    public ResultsColumns(DashboardBackend backend) {
        this.backend = backend;
    }

    /**
     * Adds the Student, question, Total and Status columns to a table.
     *
     * @param table the Results table
     * @param assessment gives the questions and their maximums
     */
    public void addTo(TableView<Submission> table, Assessment assessment) {
        TableColumn<Submission, Submission> student = column("Student",
                STUDENT_WIDTH);
        student.setCellFactory(cell -> new StudentCell());
        student.setComparator(new UsernameComparator());
        table.getColumns().add(student);

        for (Question question : assessment.getQuestions()) {
            TableColumn<Submission, Submission> marks = column(
                    question.getId() + " /" + Labels.formatScore(
                    question.getMaxScore()), QUESTION_WIDTH);
            marks.setCellFactory(cell -> new QuestionScoreCell(
                    question.getId()));
            marks.setComparator(new QuestionComparator(question.getId()));
            marks.getStyleClass().add("num");
            table.getColumns().add(marks);
        }

        TableColumn<Submission, Submission> total = column("Total /"
                + Labels.formatScore(assessment.getMaxTotal()), TOTAL_WIDTH);
        total.setCellFactory(cell -> new TotalCell());
        total.setComparator(new TotalComparator(false));
        total.getStyleClass().add("num");
        table.getColumns().add(total);

        TableColumn<Submission, Submission> status = column("Status",
                STATUS_WIDTH);
        status.setCellFactory(cell -> new StateCell());
        status.setSortable(false);
        table.getColumns().add(status);
    }

    /**
     * Returns the comparator of a sort order.
     *
     * @param order the sort order
     * @return the comparator
     */
    public static Comparator<Submission> comparatorFor(SortOrder order) {
        if (order == SortOrder.NAME_DESCENDING) {
            return new UsernameComparator().reversed();
        } else if (order == SortOrder.TOTAL_DESCENDING) {
            return new TotalComparator(true);
        } else if (order == SortOrder.TOTAL_ASCENDING) {
            return new TotalComparator(false);
        }
        return new UsernameComparator();
    }

    /**
     * Returns the full name of a student.
     *
     * @param submission the student
     * @return the first and last name, or an empty string
     */
    public static String fullName(Submission submission) {
        Student student = submission.getStudent();
        if (student == null) {
            return "";
        }
        return student.getFirstName() + " " + student.getLastName();
    }

    // Creates a column whose cells receive the whole row.
    private static TableColumn<Submission, Submission> column(String title,
            double minWidth) {
        TableColumn<Submission, Submission> column = new TableColumn<>(title);
        column.setCellValueFactory(data -> new ReadOnlyObjectWrapper<>(
                data.getValue()));
        column.setMinWidth(minWidth);
        return column;
    }

    // Finds the result of one question.
    private static QuestionResult find(Submission submission,
            String questionId) {
        for (QuestionResult result : submission.getQuestionResults()) {
            if (result.getQuestion().getId().equals(questionId)) {
                return result;
            }
        }
        return null;
    }

    /**
     * Orders students by username.
     */
    private static final class UsernameComparator
            implements Comparator<Submission> {

        /**
         * Compares two usernames, ignoring case.
         *
         * @param first the first student
         * @param second the second student
         * @return the order
         */
        @Override
        public int compare(Submission first, Submission second) {
            return first.getUsername().compareToIgnoreCase(
                    second.getUsername());
        }
    }

    /**
     * Orders students by total. Ungraded students always come last.
     */
    private static final class TotalComparator
            implements Comparator<Submission> {

        // Whether the highest total comes first
        private final boolean highestFirst;

        // Creates the comparator.
        TotalComparator(boolean highestFirst) {
            this.highestFirst = highestFirst;
        }

        /**
         * Compares two totals.
         *
         * @param first the first student
         * @param second the second student
         * @return the order
         */
        @Override
        public int compare(Submission first, Submission second) {
            boolean firstGraded = ResultState.hasTotal(first);
            boolean secondGraded = ResultState.hasTotal(second);
            if (firstGraded != secondGraded) {
                if (firstGraded) {
                    return -1;
                }
                return 1;
            }
            int order = Double.compare(first.getTotalScore(),
                    second.getTotalScore());
            if (highestFirst) {
                return -order;
            }
            return order;
        }
    }

    /**
     * Orders students by one question's mark.
     */
    private static final class QuestionComparator
            implements Comparator<Submission> {

        // The question compared
        private final String questionId;

        // Creates the comparator.
        QuestionComparator(String questionId) {
            this.questionId = questionId;
        }

        /**
         * Compares two marks of the question; ungraded counts as lowest.
         *
         * @param first the first student
         * @param second the second student
         * @return the order
         */
        @Override
        public int compare(Submission first, Submission second) {
            return Double.compare(markOf(first), markOf(second));
        }

        // Returns the mark, or -1 when there is none.
        private double markOf(Submission submission) {
            QuestionResult result = find(submission, questionId);
            if (result == null || !ResultState.hasTotal(submission)) {
                return -1.0;
            }
            return result.getScore();
        }
    }

    /**
     * Shows the username in bold with the full name under it.
     */
    private static final class StudentCell
            extends TableCell<Submission, Submission> {

        /**
         * Shows one student.
         *
         * @param submission the row's student
         * @param empty whether the row is empty
         */
        @Override
        protected void updateItem(Submission submission, boolean empty) {
            super.updateItem(submission, empty);
            setText(null);
            if (empty || submission == null) {
                setGraphic(null);
                return;
            }
            Label username = new Label(submission.getUsername());
            username.getStyleClass().add("context-value");
            Label name = new Label(fullName(submission));
            name.getStyleClass().add("secondary-text");
            VBox box = new VBox(1, username, name);
            box.setAlignment(Pos.CENTER_LEFT);
            setGraphic(box);
            setAccessibleText(submission.getUsername() + ", "
                    + fullName(submission));
        }
    }

    /**
     * Shows the total, or a dash when the student is ungraded.
     */
    private static final class TotalCell
            extends TableCell<Submission, Submission> {

        // Creates a total cell.
        TotalCell() {
            getStyleClass().add("total-cell");
        }

        /**
         * Shows one total.
         *
         * @param submission the row's student
         * @param empty whether the row is empty
         */
        @Override
        protected void updateItem(Submission submission, boolean empty) {
            super.updateItem(submission, empty);
            if (empty || submission == null) {
                setText(null);
                return;
            }
            if (ResultState.hasTotal(submission)) {
                setText(Labels.formatScore(submission.getTotalScore()));
                setAccessibleText("Total " + getText());
            } else {
                setText(Labels.NO_MARK);
                setAccessibleText("Total: ungraded");
            }
        }
    }

    /**
     * Shows the result state as an icon and words.
     */
    private final class StateCell extends TableCell<Submission, Submission> {

        /**
         * Shows one state.
         *
         * @param submission the row's student
         * @param empty whether the row is empty
         */
        @Override
        protected void updateItem(Submission submission, boolean empty) {
            super.updateItem(submission, empty);
            setText(null);
            if (empty || submission == null) {
                setGraphic(null);
                return;
            }
            setGraphic(StatusBadge.forResult(ResultState.of(submission,
                    backend)));
        }
    }
}
