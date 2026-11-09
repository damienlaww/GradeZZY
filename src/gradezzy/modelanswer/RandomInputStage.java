/*
 * File: RandomInputStage.java
 *
 * Purpose: After the testers have run, calls each student's methods with
 * random inputs and edge cases and flags the questions whose answers
 * differ from the model answer's, naming the exact inputs.
 *
 * Where it fits: The grading stage after ExecuteStage, added by
 * PipelineFactory when random.inputs.enabled is true and a model
 * answers folder is set. It only flags: marks never change. It sits in
 * this package with the checker it uses, so grading does not depend on
 * the model answer checks.
 */
package gradezzy.modelanswer;

import java.nio.file.Path;
import java.util.UUID;

import gradezzy.exec.ProcessExecutionException;
import gradezzy.grading.GradingContext;
import gradezzy.grading.GradingException;
import gradezzy.grading.GradingStage;
import gradezzy.model.Anomaly;
import gradezzy.model.AnomalyType;
import gradezzy.model.Question;
import gradezzy.model.QuestionResult;
import gradezzy.model.Severity;
import gradezzy.model.Submission;
import gradezzy.testcase.QuestionWorkspace;

/**
 * Flags answers that differ from the model answer on random inputs.
 *
 * <p>Tier: COULD. Owner: Jing Yuan.
 */
public class RandomInputStage implements GradingStage {

    /**
     * The name shown while this step runs.
     */
    public static final String NAME = "Trying random inputs";

    /**
     * Start of the id of each flag this stage adds. Grading one question
     * again replaces the flags with this start that name the question.
     */
    public static final String ID_PREFIX = "random-inputs-";

    // Added to the flag when the tester gave the question full marks,
    // which is when the difference matters most
    private static final String FULL_MARKS_NOTE = " The tester gave this "
            + "question full marks, so its test cases miss this.";

    // What the instructor can do about the flag
    private static final String SUGGESTED_ACTION = "Read the answer with "
            + "the inputs named. The mark is not changed.";

    // How far below the maximum a mark may be and still be full marks
    private static final double FULL_MARKS_MARGIN = 0.0001;

    // Compares the answers with the model answer's
    private final RandomInputChecker checker;

    /**
     * Creates the stage.
     *
     * @param checker the run's checker, with the run's inputs
     */
    public RandomInputStage(RandomInputChecker checker) {
        this.checker = checker;
    }

    /**
     * Returns the name shown while this step runs.
     *
     * @return the name
     */
    @Override
    public String getName() {
        return NAME;
    }

    /**
     * Checks every question that compiled. A question that cannot be
     * checked, such as one whose method takes a file name, is skipped.
     *
     * @param submission the submission
     * @param context the context
     * @throws GradingException never; a failure only skips the check
     */
    @Override
    public void process(Submission submission, GradingContext context)
            throws GradingException {
        for (QuestionResult result : submission.getQuestionResults()) {
            if (result.isCompiled()) {
                checkQuestion(submission, result);
            }
        }
    }

    // Checks one question and flags it when an answer differs. Stops
    // quietly when java cannot be run, such as when grading is
    // cancelled; the pipeline sees the cancel itself.
    private void checkQuestion(Submission submission,
            QuestionResult result) {
        Question question = result.getQuestion();
        Path folder = QuestionWorkspace.find(submission, question);
        if (folder == null) {
            return;
        }

        String message = null;
        try {
            message = checker.check(folder, question);
        } catch (ProcessExecutionException exception) {
            return;
        }
        if (message == null) {
            return;
        }

        Severity severity = Severity.INFO;
        double lowestFull = question.getMaxScore() - FULL_MARKS_MARGIN;
        if (result.getScore() >= lowestFull) {
            severity = Severity.WARNING;
            message = message + FULL_MARKS_NOTE;
        }
        submission.addAnomaly(new Anomaly(ID_PREFIX + UUID.randomUUID(),
                AnomalyType.DIFFERENT_FROM_MODEL_ANSWER, severity,
                submission.getUsername(), message, SUGGESTED_ACTION));
    }
}
