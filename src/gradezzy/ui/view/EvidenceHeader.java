/*
 * File: EvidenceHeader.java
 *
 * Purpose: Fills the top of the Student Evidence screen: heading, status
 * line, Retry button, mark chips and the Saved line.
 *
 * Where it fits: StudentReportController calls fill each time the screen
 * is shown. It remembers the latest manual change for the Saved line.
 */
package gradezzy.ui.view;

import java.util.List;

import javafx.scene.control.Button;
import javafx.scene.control.Label;
import javafx.scene.layout.FlowPane;
import javafx.scene.layout.HBox;
import javafx.scene.layout.Pane;
import javafx.scene.layout.Region;
import javafx.scene.layout.VBox;

import gradezzy.model.Anomaly;
import gradezzy.model.GradingRun;
import gradezzy.model.Question;
import gradezzy.model.QuestionResult;
import gradezzy.model.Submission;
import gradezzy.ui.backend.DashboardBackend;
import gradezzy.ui.backend.ResultState;
import gradezzy.ui.backend.ScoreAdjustment;
import gradezzy.ui.component.EmptyState;
import gradezzy.ui.component.Icon;
import gradezzy.ui.component.Labels;
import gradezzy.ui.component.StatusBadge;
import gradezzy.ui.component.Tone;

/**
 * The header of the Student Evidence screen.
 */
public final class EvidenceHeader {

    // The style class of a small score chip
    private static final String CHIP = "stage-chip";

    // The style class that gives a chip the normal text colour
    private static final String CHIP_TEXT = "done";

    // The backend, for review decisions and manual changes
    private final DashboardBackend backend;

    // The username heading
    private final Label titleLabel;

    // Holds the result state badge
    private final HBox stateHolder;

    // The primary Retry button
    private final Button retryButton;

    // The Adjust Score button
    private final Button adjustButton;

    // The status line under the heading
    private final Label statusLabel;

    // Holds one chip per question
    private final FlowPane scoreChips;

    // The Saved banner with its Undo button
    private final HBox savedBar;

    // Holds the Saved badge and the reason
    private final VBox savedBox;

    // The student whose mark was changed last, for the Saved line
    private String savedUsername = "";

    // The Saved line, such as "Saved: Q1b changed from 2 to 3"
    private String savedText = "";

    // The reason given for the last change
    private String savedReason = "";

    /**
     * Creates the header over the nodes it fills.
     *
     * @param backend the backend
     * @param titleLabel the username heading
     * @param stateHolder holds the result state badge
     * @param retryButton the primary Retry button
     * @param adjustButton the Adjust Score button
     * @param statusLabel the status line under the heading
     * @param scoreChips holds one chip per question
     * @param savedBar the Saved banner with its Undo button
     * @param savedBox holds the Saved badge and the reason
     */
    public EvidenceHeader(DashboardBackend backend, Label titleLabel,
            HBox stateHolder, Button retryButton, Button adjustButton,
            Label statusLabel, FlowPane scoreChips, HBox savedBar,
            VBox savedBox) {
        this.backend = backend;
        this.titleLabel = titleLabel;
        this.stateHolder = stateHolder;
        this.retryButton = retryButton;
        this.adjustButton = adjustButton;
        this.statusLabel = statusLabel;
        this.scoreChips = scoreChips;
        this.savedBar = savedBar;
        this.savedBox = savedBox;
        show(savedBar, false);
    }

    /**
     * Shows one student's heading, chips and Saved line.
     *
     * @param shown the student
     * @param run the run the student is in
     */
    public void fill(Submission shown, GradingRun run) {
        showHeading(shown, run);
        showChips(shown, run);
        showSaved(shown);
    }

    /**
     * Remembers a manual change just saved, for the Saved line.
     *
     * @param shown the student
     * @param questionId the question changed
     * @param previous the mark before the change
     * @param score the new mark
     * @param reason why the mark changed
     */
    public void rememberSaved(Submission shown, String questionId,
            double previous, double score, String reason) {
        savedUsername = shown.getUsername();
        savedText = "Saved: " + questionId + " changed from "
                + Labels.formatScore(previous) + " to "
                + Labels.formatScore(score);
        savedReason = reason.trim();
        List<ScoreAdjustment> changes = backend.listAdjustments(shown);
        if (!changes.isEmpty()) {
            ScoreAdjustment latest = changes.get(0);
            savedText = "Saved: " + latest.getQuestionId() + " changed from "
                    + Labels.formatScore(latest.getPreviousScore()) + " to "
                    + Labels.formatScore(latest.getNewScore());
            savedReason = latest.getReason();
        }
    }

    /**
     * Forgets the manual change, after it was undone.
     */
    public void forgetSaved() {
        savedUsername = "";
        savedText = "";
        savedReason = "";
    }

    /**
     * Builds the message shown when no student can be shown.
     *
     * @param username the selected student, or an empty string
     * @param sectionLabel the section in the context bar
     * @param onBack what Go to Results does
     * @return the empty state
     */
    public static EmptyState noStudent(String username, String sectionLabel,
            Runnable onBack) {
        String message;
        if (username == null || username.isEmpty()) {
            message = "No student is selected yet. Open a student from the "
                    + "Results list to see the evidence behind the marks.";
        } else {
            message = username + " is not in the run shown for "
                    + sectionLabel + ". Choose another student, or switch "
                    + "section in the bar above.";
        }
        EmptyState empty = new EmptyState(Icon.STUDENT,
                "Choose a student on the Results screen.", message);
        empty.setAction("Go to Results", onBack);
        return empty;
    }

    // Shows the username, state, status line and buttons.
    private void showHeading(Submission shown, GradingRun run) {
        titleLabel.setText(shown.getUsername());
        titleLabel.setAccessibleText("Student Evidence for "
                + shown.getUsername());
        stateHolder.getChildren().setAll(StatusBadge.forResult(
                stateOf(shown)));

        boolean graded = ResultState.hasTotal(shown);
        String questionId = EvidenceQuestions.retryQuestionOf(shown);
        if (questionId == null) {
            retryButton.setText("Retry Question");
            retryButton.setAccessibleText("Retry Question, unavailable "
                    + "because no question failed");
        } else {
            retryButton.setText("Retry " + questionId);
            retryButton.setAccessibleText("Run the " + questionId
                    + " tester again");
        }
        retryButton.setDisable(questionId == null);
        adjustButton.setDisable(!graded);
        statusLabel.setText(statusText(shown, run, graded, questionId));
    }

    // Writes the status line: name, total and what Retry would do.
    private String statusText(Submission shown, GradingRun run,
            boolean graded, String questionId) {
        String status = EvidenceQuestions.fullName(shown) + ". ";
        if (graded) {
            status += "Total " + Labels.formatMarks(shown.getTotalScore(),
                    run.getAssessment().getMaxTotal()) + ". ";
        } else {
            status += stateOf(shown).getLabel() + ". ";
        }
        status += "The original ZIP file is unchanged.";
        if (!graded) {
            status += " No question has a result yet, so only the entire "
                    + "submission can be retried.";
        } else if (questionId == null) {
            status += " No question failed, so there is nothing to retry.";
        } else {
            status += " Retry " + questionId + " runs only that question's "
                    + "tester again; the other marks stay.";
        }
        return status;
    }

    // Shows one chip per question with its mark.
    private void showChips(Submission shown, GradingRun run) {
        scoreChips.getChildren().clear();
        if (!ResultState.hasTotal(shown)) {
            Label none = new Label("No marks yet. This submission has not "
                    + "been graded.");
            none.getStyleClass().add("secondary-text");
            none.setWrapText(true);
            scoreChips.getChildren().add(none);
            return;
        }
        for (Question question : run.getAssessment().getQuestions()) {
            scoreChips.getChildren().add(scoreChip(question,
                    EvidenceQuestions.find(shown, question.getId())));
        }
    }

    // Builds the chip of one question, such as "Q1a 2 of 3, timed out".
    private Label scoreChip(Question question, QuestionResult result) {
        String text = question.getId() + " ";
        Icon icon = null;
        if (result == null) {
            text += "ungraded";
        } else {
            text += Labels.formatMarks(result.getScore(),
                    question.getMaxScore());
            if (!result.isCompiled()) {
                text += ", not compiled";
                icon = Icon.ERROR;
            } else if (result.isTimedOut()) {
                text += ", timed out";
                icon = Icon.TIMEOUT;
            }
        }

        Label chip = new Label(text);
        chip.getStyleClass().addAll(CHIP, CHIP_TEXT);
        chip.setMinWidth(Region.USE_PREF_SIZE);
        chip.setAccessibleText(text);
        if (icon != null) {
            Pane graphic = icon.create();
            graphic.getStyleClass().add(Tone.ERROR.getStyleClass());
            chip.setGraphic(graphic);
        }
        return chip;
    }

    // Shows the Saved line with Undo after a manual change.
    private void showSaved(Submission shown) {
        savedBox.getChildren().clear();
        boolean visible = shown.getUsername().equals(savedUsername)
                && !savedText.isEmpty()
                && !backend.listAdjustments(shown).isEmpty();
        show(savedBar, visible);
        if (!visible) {
            return;
        }
        Label reason = new Label("Reason: " + savedReason + ". Undo puts "
                + "the earlier mark back.");
        reason.setWrapText(true);
        savedBox.getChildren().addAll(new StatusBadge(savedText,
                Tone.SUCCESS), reason);
    }

    // Works out the state shown for a student.
    private ResultState stateOf(Submission shown) {
        boolean waiting = false;
        for (Anomaly anomaly : shown.getAnomalies()) {
            if (backend.needsReview(anomaly)) {
                waiting = true;
            }
        }
        return ResultState.of(shown, waiting);
    }

    // Shows or hides a node and frees its space.
    private static void show(Region node, boolean visible) {
        node.setVisible(visible);
        node.setManaged(visible);
    }
}
