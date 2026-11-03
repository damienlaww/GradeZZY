/*
 * File: TesterOutputScoreParser.java
 *
 * Purpose: Parses the output format of the provided tester files.
 *
 * Where it fits: Those testers print the pass marker or a fail line per test
 * case and the total score as the last line.
 */
package gradezzy.grading;

import gradezzy.config.AppConfig;
import gradezzy.config.ConfigKeys;
import gradezzy.exec.ProcessResult;
import gradezzy.exec.TesterRunner;
import gradezzy.model.Question;
import gradezzy.model.QuestionResult;
import gradezzy.model.TestCaseResult;

/**
 * Parses the output format of the provided tester files.
 *
 * <p>Tier: MUST. Owner: Zach.
 */
public class TesterOutputScoreParser implements ScoreParser {

    // Splits output into lines, whatever the line ending
    private static final String LINE_BREAK = "\\R";

    // Separates standard output from standard error in the saved log
    private static final String LOG_SEPARATOR = "\n";

    // Exit code of a tester that finished normally
    private static final int SUCCESS_EXIT_CODE = 0;

    // Detail of a test case that was still running when the tester stopped
    private static final String NOT_FINISHED = "Not finished: the tester "
            + "stopped before this case printed a result.";

    // The application settings
    private final AppConfig config;

    /**
     * Creates a new tester output score parser.
     *
     * @param config the application settings
     */
    public TesterOutputScoreParser(AppConfig config) {
        this.config = config;
    }

    /**
     * Reads the tester output for one question.
     *
     * @param question the question
     * @param processResult the process result
     * @return the score and the result of each test case
     */
    @Override
    public QuestionResult parse(Question question,
            ProcessResult processResult) {
        QuestionResult result = new QuestionResult(question);
        if (processResult == null) {
            return result;
        }

        String output = processResult.getStandardOutput();
        String headerMarker = config.getString(ConfigKeys.TEST_HEADER_MARKER);
        String passMarker = config.getString(ConfigKeys.PASS_MARKER);
        String failMarker = config.getString(ConfigKeys.FAIL_MARKER);
        int testNumber = 0;
        String verdict = "";
        String lastLine = "";

        // A test case runs from its header to the next header. Lines that
        // student code printed are marked by TesterRunner and never count.
        for (String rawLine : output.split(LINE_BREAK)) {
            String line = rawLine.trim();

            if (rawLine.startsWith(TesterRunner.STUDENT_LINE_PREFIX)) {
                line = "";
            } else if (line.startsWith(headerMarker)) {
                if (testNumber > 0) {
                    addCase(result, testNumber, verdict, passMarker);
                }
                testNumber++;
                verdict = "";
            } else if (line.equals(passMarker)
                    || line.startsWith(failMarker)) {
                // A verdict with no open case, as from a tester that
                // prints no headers, is a case of its own
                if (testNumber == 0 || !verdict.isEmpty()) {
                    if (testNumber > 0) {
                        addCase(result, testNumber, verdict, passMarker);
                    }
                    testNumber++;
                }
                verdict = line;
            }

            if (!line.isEmpty()) {
                lastLine = line;
            }
        }

        if (testNumber > 0) {
            addCase(result, testNumber, verdict, passMarker);
        }

        int passCount = countPasses(result);
        result.setCompiled(true);
        result.setTimedOut(processResult.isTimedOut());
        result.setOutput(buildLog(processResult));
        result.setScore(readScore(processResult, lastLine, passCount,
                question.getMaxScore()));
        return result;
    }

    /**
     * Reads the total from the tester's last line, capped at the question's
     * maximum score. Only the tester can print that line, so a tester that
     * gives more than one mark per case is believed; ExecuteStage then caps
     * it at what the passed cases earn. A run that was
     * stopped, crashed or ended early never printed its total, so the
     * passes printed so far are counted instead, one mark each, as the
     * provided testers award.
     *
     * @param processResult the finished run
     * @param lastLine the last line that was not blank
     * @param passCount the number of passed test cases
     * @param maxScore the question's maximum score
     * @return the score
     */
    private double readScore(ProcessResult processResult, String lastLine,
            int passCount, double maxScore) {
        if (!finishedNormally(processResult)) {
            return Math.min(passCount, maxScore);
        }

        try {
            return Math.min(Double.parseDouble(lastLine), maxScore);
        } catch (NumberFormatException exception) {
            return Math.min(passCount, maxScore);
        }
    }

    /**
     * Records one test case. A case with no verdict never finished.
     *
     * @param result where the case is added
     * @param testNumber the position of the case, from 1
     * @param verdict the verdict line, or empty if none was printed
     * @param passMarker the line printed for a pass
     */
    private void addCase(QuestionResult result, int testNumber,
            String verdict, String passMarker) {
        if (verdict.isEmpty()) {
            result.addTestCaseResult(new TestCaseResult(testNumber, false,
                    NOT_FINISHED));
        } else {
            result.addTestCaseResult(new TestCaseResult(testNumber,
                    verdict.equals(passMarker), verdict));
        }
    }

    /**
     * Counts the passed test cases of a result.
     *
     * @param result the result
     * @return the number of passed cases
     */
    private int countPasses(QuestionResult result) {
        int count = 0;
        for (TestCaseResult testCase : result.getTestCaseResults()) {
            if (testCase.isPassed()) {
                count++;
            }
        }
        return count;
    }

    /**
     * Says whether the tester ended by itself without an error.
     *
     * @param processResult the finished run
     * @return true if it was not stopped and exited normally
     */
    private boolean finishedNormally(ProcessResult processResult) {
        return !processResult.isTimedOut()
                && processResult.getExitCode() == SUCCESS_EXIT_CODE;
    }

    /**
     * Joins standard output and standard error into one log, so the stack
     * trace of a failing case is kept for the instructor.
     *
     * @param processResult the finished run
     * @return the log
     */
    private String buildLog(ProcessResult processResult) {
        String error = processResult.getStandardError();
        if (error.isEmpty()) {
            return processResult.getStandardOutput();
        }
        return processResult.getStandardOutput() + LOG_SEPARATOR + error;
    }
}
