/*
 * File: TesterOutputScoreParser.java
 *
 * Purpose: Parses the output format of the provided tester files.
 *
 * Where it fits: Those testers print the pass marker or a fail line per test
 * case and the total score as the last line.
 */
package gradezzy.grading;

import java.util.ArrayList;
import java.util.List;

import gradezzy.config.AppConfig;
import gradezzy.config.ConfigKeys;
import gradezzy.exec.ProcessResult;
import gradezzy.exec.TesterHarness;
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

    /**
     * Detail of a test case that was still running when the tester stopped.
     */
    public static final String NOT_FINISHED = "Not finished: the tester "
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
        // The verdict of each case in order, empty while it has none
        List<String> verdicts = new ArrayList<>();

        // A test case runs from its header to the next header. Lines that
        // student code printed are marked by TesterRunner and never count.
        for (String rawLine : output.split(LINE_BREAK)) {
            String line = rawLine.trim();
            boolean studentLine = rawLine.startsWith(
                    TesterRunner.STUDENT_LINE_PREFIX);

            boolean header = !studentLine
                    && TesterHarness.isCaseHeader(line, headerMarker);
            boolean verdict = !studentLine
                    && isVerdict(line, passMarker, failMarker);

            if (header) {
                verdicts.add("");
            } else if (verdict) {
                // A verdict with no open case, as from a tester that
                // prints no headers, is a case of its own
                if (!hasOpenCase(verdicts)) {
                    verdicts.add("");
                }
                int openCase = verdicts.size() - 1;
                verdicts.set(openCase, line);
            }
        }

        for (int index = 0; index < verdicts.size(); index++) {
            int testNumber = index + 1;
            addCase(result, testNumber, verdicts.get(index), passMarker,
                    failMarker);
        }

        int passCount = countPasses(result);
        result.setCompiled(true);
        result.setTimedOut(processResult.isTimedOut());
        result.setOutput(buildLog(processResult));
        result.setScore(readScore(processResult, passCount,
                question.getMaxScore()));
        return result;
    }

    /**
     * Reads the total the tester printed as its last line, as printed.
     * Lines student code printed are skipped.
     *
     * @param processResult the process result
     * @return the printed total, or null if the last tester line is not
     *         a number or there is no run
     */
    @Override
    public Double readTotal(ProcessResult processResult) {
        if (processResult == null) {
            return null;
        }

        String lastLine = findLastTesterLine(
                processResult.getStandardOutput());
        try {
            return Double.valueOf(lastLine);
        } catch (NumberFormatException exception) {
            return null;
        }
    }

    /**
     * Finds the last line the tester printed that is not blank.
     *
     * @param output the standard output of the run
     * @return the line, trimmed, or empty if there is none
     */
    private String findLastTesterLine(String output) {
        String lastLine = "";
        for (String rawLine : output.split(LINE_BREAK)) {
            String line = rawLine.trim();
            boolean studentLine = rawLine.startsWith(
                    TesterRunner.STUDENT_LINE_PREFIX);
            if (!studentLine && !line.isEmpty()) {
                lastLine = line;
            }
        }
        return lastLine;
    }

    /**
     * Reads the total from the tester's last line, capped at the question's
     * maximum score. Only the tester can print that line, so a tester that
     * gives more than one mark per case is believed; ExecuteStage then caps
     * it at what the passed cases earn. A run that was stopped, crashed or
     * ended early never printed its total, and a total that is not a
     * number cannot be read, so the passes are counted here one mark
     * each; ExecuteStage replaces that with what each passed case is worth
     * in the tester.
     *
     * @param processResult the finished run
     * @param passCount the number of passed test cases
     * @param maxScore the question's maximum score
     * @return the score
     */
    private double readScore(ProcessResult processResult, int passCount,
            double maxScore) {
        Double printedTotal = readTotal(processResult);
        boolean totalRead = finishedNormally(processResult)
                && printedTotal != null;
        if (!totalRead) {
            return Math.min(passCount, maxScore);
        }
        return Math.min(printedTotal, maxScore);
    }

    // Says whether a line is a fail or a pass verdict. A pass is any line
    // that starts with the pass marker, such as "Passed!".
    private boolean isVerdict(String line, String passMarker,
            String failMarker) {
        return line.startsWith(failMarker) || line.startsWith(passMarker);
    }

    // Says whether the last case is still waiting for its verdict.
    private boolean hasOpenCase(List<String> verdicts) {
        if (verdicts.isEmpty()) {
            return false;
        }
        int lastCase = verdicts.size() - 1;
        return verdicts.get(lastCase).isEmpty();
    }

    /**
     * Records one test case. A case with no verdict never finished. The
     * fail marker is checked first, so a line that starts with both, such
     * as "Failed" when the pass marker is "F", is a fail.
     *
     * @param result where the case is added
     * @param testNumber the position of the case, from 1
     * @param verdict the verdict line, or empty if none was printed
     * @param passMarker the start of the line printed for a pass
     * @param failMarker the start of the line printed for a fail
     */
    private void addCase(QuestionResult result, int testNumber,
            String verdict, String passMarker, String failMarker) {
        if (verdict.isEmpty()) {
            result.addTestCaseResult(new TestCaseResult(testNumber, false,
                    NOT_FINISHED));
        } else {
            boolean passed = !verdict.startsWith(failMarker)
                    && verdict.startsWith(passMarker);
            result.addTestCaseResult(new TestCaseResult(testNumber, passed,
                    verdict));
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
