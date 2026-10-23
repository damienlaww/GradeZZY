/*
 * File: StudentInspector.java
 *
 * Purpose: Fills the inspector beside the Results table with the focused
 * student.
 *
 * Where it fits: Used by ResultsController with the inspector's FXML
 * nodes. It only shows data.
 */
package gradezzy.ui.view;

import javafx.geometry.Pos;
import javafx.scene.control.Button;
import javafx.scene.control.Label;
import javafx.scene.layout.HBox;
import javafx.scene.layout.VBox;

import gradezzy.model.Anomaly;
import gradezzy.model.GradingRun;
import gradezzy.model.Question;
import gradezzy.model.QuestionResult;
import gradezzy.model.Submission;
import gradezzy.ui.backend.DashboardBackend;
import gradezzy.ui.backend.ResultState;
import gradezzy.ui.component.Labels;
import gradezzy.ui.component.StatusBadge;

/**
 * The inspector of the Results screen.
 */
public final class StudentInspector {

    // The width of the question name in each line
    private static final double QUESTION_WIDTH = 54.0;

    // The space between the question name and its mark
    private static final double ROW_GAP = 8.0;

    // The student's full name
    private final Label nameLabel;

    // The student's username
    private final Label usernameLabel;

    // The total, or a dash
    private final Label totalLabel;

    // "of 20"
    private final Label maxLabel;

    // Holds the state badge
    private final HBox stateHolder;

    // One line per question
    private final VBox questionRows;

    // The first message that waits for review
    private final Label noteLabel;

    // Opens the evidence of the student
    private final Button openButton;

    // Answers which flags still wait for review
    private final DashboardBackend backend;

    /**
     * Creates the inspector.
     *
     * @param nameLabel the student's full name
     * @param usernameLabel the student's username
     * @param totalLabel the total
     * @param maxLabel the maximum, as "of 20"
     * @param stateHolder holds the state badge
     * @param questionRows one line per question
     * @param noteLabel the first message that waits for review
     * @param openButton opens the evidence of the student
     * @param backend answers which flags still wait for review
     */
    public StudentInspector(Label nameLabel, Label usernameLabel,
            Label totalLabel, Label maxLabel, HBox stateHolder,
            VBox questionRows, Label noteLabel, Button openButton,
            DashboardBackend backend) {
        this.nameLabel = nameLabel;
        this.usernameLabel = usernameLabel;
        this.totalLabel = totalLabel;
        this.maxLabel = maxLabel;
        this.stateHolder = stateHolder;
        this.questionRows = questionRows;
        this.noteLabel = noteLabel;
        this.openButton = openButton;
        this.backend = backend;
    }

    /**
     * Shows a student, or explains that none is selected.
     *
     * @param submission the focused student, or null
     * @param run the run the student belongs to
     */
    public void show(Submission submission, GradingRun run) {
        questionRows.getChildren().clear();
        stateHolder.getChildren().clear();

        if (submission == null || run == null) {
            nameLabel.setText("No student selected");
            usernameLabel.setText("");
            totalLabel.setText("");
            maxLabel.setText("");
            noteLabel.setText("Select a row to see the marks per question "
                    + "and open the evidence behind them.");
            openButton.setDisable(true);
            return;
        }

        String name = ResultsColumns.fullName(submission);
        if (name.isEmpty()) {
            name = submission.getUsername();
        }
        nameLabel.setText(name);
        usernameLabel.setText(submission.getUsername());

        if (ResultState.hasTotal(submission)) {
            totalLabel.setText(Labels.formatScore(
                    submission.getTotalScore()));
        } else {
            totalLabel.setText(Labels.NO_MARK);
        }
        maxLabel.setText("of " + Labels.formatScore(
                run.getAssessment().getMaxTotal()));
        stateHolder.getChildren().add(StatusBadge.forResult(
                ResultState.of(submission, backend)));

        for (Question question : run.getAssessment().getQuestions()) {
            questionRows.getChildren().add(questionRow(submission,
                    question));
        }
        noteLabel.setText(firstNote(submission));
        openButton.setDisable(false);
    }

    // Builds one line: the question and its mark.
    private HBox questionRow(Submission submission, Question question) {
        Label name = new Label(question.getId());
        name.setMinWidth(QUESTION_WIDTH);
        QuestionResult result = null;
        for (QuestionResult candidate : submission.getQuestionResults()) {
            if (candidate.getQuestion().getId().equals(question.getId())) {
                result = candidate;
            }
        }

        String text;
        if (result == null || !ResultState.hasTotal(submission)) {
            text = "Ungraded";
        } else {
            text = Labels.formatMarks(result.getScore(),
                    question.getMaxScore());
            if (!result.isCompiled()) {
                text += ", not compiled";
            } else if (result.isTimedOut()) {
                text += ", timed out";
            }
        }
        HBox row = new HBox(ROW_GAP, name, new Label(text));
        row.setAlignment(Pos.CENTER_LEFT);
        return row;
    }

    // Returns the first message worth reading for a student.
    private String firstNote(Submission submission) {
        for (Anomaly anomaly : submission.getAnomalies()) {
            if (backend.needsReview(anomaly)) {
                return anomaly.getMessage();
            }
        }
        if (!ResultState.hasTotal(submission)) {
            return "Not graded yet.";
        }
        return "Nothing waits for review.";
    }
}
