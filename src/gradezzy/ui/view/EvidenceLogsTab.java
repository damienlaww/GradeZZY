/*
 * File: EvidenceLogsTab.java
 *
 * Purpose: Fills the Logs tab of the Student Evidence screen: a question
 * choice and the read-only compiler and tester output of that question,
 * with its length.
 *
 * Where it fits: StudentReportController creates one, calls fill each
 * time the screen is shown and select when another question comes into
 * focus. A question chosen here becomes the question in focus in UiState.
 * The text area stays responsive and scrollable for the 100,000-character
 * sample log, because it does not wrap and is only refilled when the text
 * changes.
 *
 * AI use: drafted with Claude Code, reviewed by Jun Sheng.
 */
package gradezzy.ui.view;

import java.util.ArrayList;
import java.util.List;
import java.util.Locale;

import javafx.collections.FXCollections;
import javafx.scene.control.ComboBox;
import javafx.scene.control.Label;
import javafx.scene.control.TextArea;

import gradezzy.model.GradingRun;
import gradezzy.model.Question;
import gradezzy.model.QuestionResult;
import gradezzy.model.Submission;
import gradezzy.ui.UiState;
import gradezzy.ui.backend.ResultState;

/**
 * The Logs tab of the Student Evidence screen.
 *
 * <p>Tier: SHOULD. Owner: Damien.
 */
public final class EvidenceLogsTab {

    // How a character count is written, with thousands separators
    private static final String COUNT_FORMAT = "%,d";

    // The shared state, which remembers the question in focus
    private final UiState state;

    // The question choice
    private final ComboBox<String> questionBox;

    // The length of the output on show
    private final Label countLabel;

    // The read-only output
    private final TextArea logArea;

    // The student on show, or null
    private Submission shown = null;

    // Whether the question choice is being set by code
    private boolean updating = false;

    /**
     * Creates the tab over the nodes it fills.
     *
     * @param state the shared state, which remembers the question in
     *        focus
     * @param questionLabel the label of the question choice
     * @param questionBox the question choice
     * @param countLabel the length of the output on show
     * @param logArea the read-only output
     */
    public EvidenceLogsTab(UiState state, Label questionLabel,
            ComboBox<String> questionBox, Label countLabel,
            TextArea logArea) {
        this.state = state;
        this.questionBox = questionBox;
        this.countLabel = countLabel;
        this.logArea = logArea;
        questionLabel.setLabelFor(questionBox);
        questionBox.valueProperty().addListener(
                (observable, oldValue, newValue) -> questionChosen(
                newValue));
    }

    /**
     * Fills the question choice for one student and shows one log.
     *
     * @param run the run the student is in
     * @param student the student
     * @param questionId the question in focus
     */
    public void fill(GradingRun run, Submission student, String questionId) {
        shown = student;
        List<String> ids = new ArrayList<>();
        for (Question question : run.getAssessment().getQuestions()) {
            ids.add(question.getId());
        }
        updating = true;
        questionBox.setItems(FXCollections.observableArrayList(ids));
        questionBox.setValue(questionId);
        updating = false;
        showLog(questionId);
    }

    /**
     * Shows the log of the question now in focus.
     *
     * @param questionId the question
     */
    public void select(String questionId) {
        updating = true;
        questionBox.setValue(questionId);
        updating = false;
        showLog(questionId);
    }

    // Reacts to a question chosen by the instructor.
    private void questionChosen(String questionId) {
        if (updating || questionId == null) {
            return;
        }
        state.setEvidenceQuestionId(questionId);
        showLog(questionId);
    }

    // Shows the output of one question with its length.
    private void showLog(String questionId) {
        QuestionResult result = null;
        if (shown != null && ResultState.hasTotal(shown)) {
            result = EvidenceQuestions.find(shown, questionId);
        }
        String text = "";
        if (result != null && result.getOutput() != null) {
            text = result.getOutput();
        }
        if (!text.equals(logArea.getText())) {
            logArea.setText(text);
        }
        logArea.setScrollTop(0.0);

        if (result == null) {
            countLabel.setText("No output yet: " + questionId
                    + " has not been graded.");
        } else if (text.isEmpty()) {
            countLabel.setText(questionId + " printed no output.");
        } else {
            countLabel.setText(String.format(Locale.ROOT, COUNT_FORMAT,
                    text.length()) + " characters of compiler and tester "
                    + "output for " + questionId + ".");
        }
    }
}
