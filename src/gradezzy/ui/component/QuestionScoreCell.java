/*
 * File: QuestionScoreCell.java
 *
 * Purpose: Shows one question's mark in a results table so that an
 * ungraded mark, a real zero, a compile failure and a timeout all look
 * different, each with words for screen readers.
 *
 * Where it fits: Used by the Results table for the Q1a to Q3 columns.
 *
 * AI use: drafted with Claude Code, reviewed by Jun Sheng.
 */
package gradezzy.ui.component;

import javafx.geometry.Pos;
import javafx.scene.control.ContentDisplay;
import javafx.scene.control.TableCell;
import javafx.scene.control.Tooltip;

import gradezzy.model.QuestionResult;
import gradezzy.model.Submission;
import gradezzy.ui.backend.ResultState;

/**
 * A table cell for one question's mark.
 *
 * <p>Tier: MUST. Owner: Damien.
 */
public final class QuestionScoreCell
        extends TableCell<Submission, Submission> {

    // The question this column shows
    private final String questionId;

    /**
     * Creates a cell for one question.
     *
     * @param questionId the question, such as Q1a
     */
    public QuestionScoreCell(String questionId) {
        this.questionId = questionId;
        getStyleClass().add("score-cell");
        setAlignment(Pos.CENTER_RIGHT);
        setContentDisplay(ContentDisplay.RIGHT);
    }

    /**
     * Shows the mark of the row's submission.
     *
     * @param submission the row's submission
     * @param empty whether the row is empty
     */
    @Override
    protected void updateItem(Submission submission, boolean empty) {
        super.updateItem(submission, empty);
        setGraphic(null);
        setTooltip(null);

        if (empty || submission == null) {
            setText(null);
            setAccessibleText(null);
            return;
        }

        QuestionResult result = find(submission);
        if (!ResultState.hasTotal(submission) || result == null) {
            setText(Labels.NO_MARK);
            setAccessibleText(questionId + ": ungraded");
            return;
        }

        String mark = Labels.formatScore(result.getScore());
        setText(mark);
        if (!result.isCompiled()) {
            setGraphic(Icon.ERROR.create());
            getGraphic().getStyleClass().add(Tone.ERROR.getStyleClass());
            setTooltip(new Tooltip("Not compiled. See the student's "
                    + "evidence for the reason."));
            setAccessibleText(questionId + ": " + mark + ", not compiled");
        } else if (result.isTimedOut()) {
            setGraphic(Icon.TIMEOUT.create());
            getGraphic().getStyleClass().add(Tone.ERROR.getStyleClass());
            setTooltip(new Tooltip("The tester was stopped at the time "
                    + "limit. Marks from earlier cases are kept."));
            setAccessibleText(questionId + ": " + mark + ", timed out");
        } else {
            setAccessibleText(questionId + ": " + mark + " of "
                    + Labels.formatScore(result.getQuestion()
                    .getMaxScore()));
        }
    }

    // Finds the result of this cell's question.
    private QuestionResult find(Submission submission) {
        for (QuestionResult result : submission.getQuestionResults()) {
            if (result.getQuestion().getId().equals(questionId)) {
                return result;
            }
        }
        return null;
    }
}
