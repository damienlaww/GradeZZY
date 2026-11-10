/*
 * File: QuestionRerun.java
 *
 * Purpose: Runs one question's tester again for one student on a
 * background thread, so the window stays responsive while javac and the
 * tester run.
 *
 * Where it fits: The code and test output sheet starts one when "Run
 * tests again" is pressed, and shows its result when it succeeds. Nothing
 * it returns is saved.
 */
package gradezzy.ui.flow;

import javafx.concurrent.Task;

import gradezzy.grading.GradingException;
import gradezzy.model.GradingRun;
import gradezzy.model.Question;
import gradezzy.model.QuestionResult;
import gradezzy.model.Submission;
import gradezzy.ui.backend.DashboardBackend;

/**
 * Runs one question's tester again in the background.
 *
 * <p>Tier: SHOULD.
 */
public class QuestionRerun extends Task<QuestionResult> {

    // The backend that runs the tester
    private final DashboardBackend backend;

    // The student
    private final Submission submission;

    // The run the student was graded in, or null
    private final GradingRun run;

    // The question to run again
    private final Question question;

    /**
     * Creates the run of one question.
     *
     * @param backend the backend that runs the tester
     * @param submission the student
     * @param run the run the student was graded in, or null
     * @param question the question to run again
     */
    public QuestionRerun(DashboardBackend backend, Submission submission,
            GradingRun run, Question question) {
        this.backend = backend;
        this.submission = submission;
        this.run = run;
        this.question = question;
    }

    /**
     * Returns the question this run is for.
     *
     * @return the question
     */
    public Question getQuestion() {
        return question;
    }

    /**
     * Compiles and runs the tester. JavaFX calls this on a background
     * thread.
     *
     * @return the fresh result
     * @throws GradingException if there is nothing to run
     */
    @Override
    protected QuestionResult call() throws GradingException {
        return backend.rerunQuestion(submission, run, question);
    }
}
