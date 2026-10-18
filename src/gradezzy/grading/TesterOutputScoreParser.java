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
        String passMarker = config.getString(ConfigKeys.PASS_MARKER);
        String failMarker = config.getString(ConfigKeys.FAIL_MARKER);
        int testNumber = 0;
        int passCount = 0;
        String lastLine = "";

        for (String rawLine : output.split(LINE_BREAK)) {
            String line = rawLine.trim();

            if (line.equals(passMarker)) {
                testNumber++;
                passCount++;
                result.addTestCaseResult(
                        new TestCaseResult(testNumber, true, line));
            } else if (line.startsWith(failMarker)) {
                testNumber++;
                result.addTestCaseResult(
                        new TestCaseResult(testNumber, false, line));
            }

            if (!line.isEmpty()) {
                lastLine = line;
            }
        }

        result.setCompiled(true);
        result.setTimedOut(processResult.isTimedOut());
        result.setOutput(buildLog(processResult));
        result.setScore(readScore(processResult, lastLine, passCount));
        return result;
    }

    /**
     * Reads the total from the last line. A run that was stopped or that
     * crashed never printed its total, and its last line may be the
     * student's own output, so the passes printed so far are counted
     * instead, one mark each, as the provided testers award.
     *
     * @param processResult the finished run
     * @param lastLine the last line that was not blank
     * @param passCount the number of passed test cases
     * @return the score
     */
    private double readScore(ProcessResult processResult, String lastLine,
            int passCount) {
        if (processResult.isTimedOut() || processResult.getExitCode() != 0) {
            return passCount;
        }

        try {
            return Double.parseDouble(lastLine);
        } catch (NumberFormatException exception) {
            return passCount;
        }
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
