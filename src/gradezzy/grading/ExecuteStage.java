/*
 * File: ExecuteStage.java
 *
 * Purpose: Runs each tester with a time limit and parses its output.
 *
 * Where it fits: Seventh stage. A tester that hangs is stopped and recorded as
 * a TIMEOUT anomaly.
 */
package gradezzy.grading;

import java.io.IOException;
import java.nio.file.Path;
import java.util.UUID;

import gradezzy.config.ConfigKeys;
import gradezzy.exec.ProcessExecutionException;
import gradezzy.exec.ProcessResult;
import gradezzy.exec.TesterRunner;
import gradezzy.model.Anomaly;
import gradezzy.model.AnomalyType;
import gradezzy.model.Question;
import gradezzy.model.QuestionResult;
import gradezzy.model.Severity;
import gradezzy.model.Submission;
import gradezzy.model.TestCaseResult;
import gradezzy.testcase.QuestionFolderFinder;
import gradezzy.testcase.TesterFile;

/**
 * Runs each tester with a time limit and parses its output.
 *
 * <p>Tier: MUST. Owner: Zach.
 */
public class ExecuteStage implements GradingStage {

    /**
     * The name shown while this stage runs.
     */
    public static final String NAME = "Running testers";

    // Start of the id of each anomaly this stage reports
    private static final String ID_PREFIX = "execute-";

    // Exit code of a tester that finished normally
    private static final int SUCCESS_EXIT_CODE = 0;

    // What the instructor can do about a tester that ended with an error
    private static final String RUNTIME_ERROR_ACTION =
            "Read the log on the Logs tab.";

    // The helper that runs java
    private final TesterRunner testerRunner;

    // The helper that reads tester output
    private final ScoreParser scoreParser;

    /**
     * Creates a new execute stage.
     *
     * @param testerRunner the helper that runs java
     * @param scoreParser the helper that reads tester output
     */
    public ExecuteStage(TesterRunner testerRunner, ScoreParser scoreParser) {
        this.testerRunner = testerRunner;
        this.scoreParser = scoreParser;
    }

    /**
     * Returns the name of this stage.
     *
     * @return the name of this stage
     */
    @Override
    public String getName() {
        return NAME;
    }

    /**
     * Runs the tester of each compiled question, one process each, so a
     * tester that hangs or crashes cannot affect the next one.
     *
     * @param submission the submission
     * @param context the context
     * @throws GradingException if java itself cannot be run
     */
    @Override
    public void process(Submission submission,
            GradingContext context) throws GradingException {
        for (QuestionResult result : submission.getQuestionResults()) {
            if (result.isCompiled()) {
                runQuestion(submission, context, result);
            }
        }
    }

    /**
     * Runs the tester of one question and stores what it reports.
     *
     * @param submission the submission
     * @param context the context
     * @param result the result of the question, filled in here
     * @throws GradingException if java itself cannot be run
     */
    private void runQuestion(Submission submission, GradingContext context,
            QuestionResult result) throws GradingException {
        Question question = result.getQuestion();
        TesterFile tester = TesterFile.findFor(context.getTesters(),
                question.getId());
        Path folder = findFolder(submission, context, question);
        if (tester == null || folder == null) {
            return;
        }

        ProcessResult run = runTester(folder, tester.getClassName());
        QuestionResult parsed = scoreParser.parse(question, run);
        result.setScore(parsed.getScore());
        result.setTimedOut(parsed.isTimedOut());
        result.setOutput(parsed.getOutput());
        for (TestCaseResult testCase : parsed.getTestCaseResults()) {
            result.addTestCaseResult(testCase);
        }

        if (run.isTimedOut()) {
            long limitSeconds = context.getConfig().getLong(
                    ConfigKeys.RUN_TIMEOUT_SECONDS);
            addAnomaly(submission, AnomalyType.TIMEOUT, "The "
                    + question.getId() + " tester was stopped at the "
                    + limitSeconds + " s limit. " + passCount(parsed)
                    + " case(s) passed before that and their marks are "
                    + "kept.", "Check the loops in " + question.getFileName()
                    + ", then retry " + question.getId() + ".");
        } else if (run.getExitCode() != SUCCESS_EXIT_CODE) {
            addAnomaly(submission, AnomalyType.RUNTIME_ERROR, "The "
                    + question.getId() + " tester ended with an error (exit "
                    + "code " + run.getExitCode() + "). " + passCount(parsed)
                    + " case(s) passed and were scored.",
                    RUNTIME_ERROR_ACTION);
        }
    }

    /**
     * Runs a tester. Failing to start java is a problem with the computer,
     * not with the student's work, so it stops this submission.
     *
     * @param folder the question folder
     * @param testerClassName the tester class
     * @return the tester result
     * @throws GradingException if java cannot be run
     */
    private ProcessResult runTester(Path folder, String testerClassName)
            throws GradingException {
        try {
            return testerRunner.runTester(folder, testerClassName);
        } catch (ProcessExecutionException exception) {
            throw new GradingException("The tester could not be run: "
                    + exception.getMessage(), exception);
        }
    }

    /**
     * Finds the question folder the same way the install and compile
     * stages did.
     *
     * @param submission the submission
     * @param context the context
     * @param question the question
     * @return the folder, or null if it is missing
     * @throws GradingException if the working copy cannot be read
     */
    private Path findFolder(Submission submission, GradingContext context,
            Question question) throws GradingException {
        try {
            return QuestionFolderFinder.find(submission,
                    context.getAssessment(), question);
        } catch (IOException exception) {
            throw new GradingException("The working copy could not be read: "
                    + exception.getMessage(), exception);
        }
    }

    /**
     * Counts the passed test cases of a result.
     *
     * @param result the result
     * @return the number of passed test cases
     */
    private int passCount(QuestionResult result) {
        int count = 0;
        for (TestCaseResult testCase : result.getTestCaseResults()) {
            if (testCase.isPassed()) {
                count++;
            }
        }
        return count;
    }

    /**
     * Records a problem found while running a tester.
     *
     * @param submission the submission
     * @param type the type of problem
     * @param message what happened
     * @param action what the instructor can do
     */
    private void addAnomaly(Submission submission, AnomalyType type,
            String message, String action) {
        submission.addAnomaly(new Anomaly(ID_PREFIX + UUID.randomUUID(), type,
                Severity.ERROR, submission.getUsername(), message, action));
    }
}
