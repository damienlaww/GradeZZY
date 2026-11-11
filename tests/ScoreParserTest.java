/*
 * File: ScoreParserTest.java
 *
 * Purpose: Unit checks for TesterOutputScoreParser (markers, case names,
 * printed totals, odd output) and ScoreCalculator. No process is run:
 * each check builds a ProcessResult by hand.
 *
 * Where it fits: Run by hand after compiling, as tests/README.md says.
 */
import java.util.List;

import gradezzy.exec.ProcessResult;
import gradezzy.exec.TesterRunner;
import gradezzy.grading.ScoreCalculator;
import gradezzy.grading.TesterOutputScoreParser;
import gradezzy.model.Question;
import gradezzy.model.QuestionResult;
import gradezzy.model.TestCaseResult;

/**
 * Runs the score parser checks and prints how many passed.
 */
public class ScoreParserTest {

    // The number of checks that passed and failed so far
    private static int passed = 0;
    private static int failed = 0;

    // The start of a line the student's own code printed
    private static final String STUDENT = TesterRunner.STUDENT_LINE_PREFIX;

    // The divider line the tester prints between cases
    private static final String LINE =
            "-------------------------------------------------------";

    // The standard error text when the tester printed none
    private static final String NO_ERROR = "";

    // The marker that starts a case header
    private static final String MARKER = "Test";

    // The line endings of output on macOS and on Windows
    private static final String LF = "\n";
    private static final String CRLF = "\r\n";

    // The most marks the question gives
    private static final double MAX_MARKS = 4.0;

    // How long each made-up run took, in milliseconds
    private static final long RUN_MILLIS = 10;

    // The parser under test, made from the fixed settings
    private static TesterOutputScoreParser parser = null;

    // The question every made-up run is for
    private static final Question QUESTION = new Question("Q2a", "Q2",
            "Q2a.java", MAX_MARKS);

    /**
     * Runs every check.
     *
     * @param args not used
     * @throws Exception if the settings cannot be read
     */
    public static void main(String[] args) throws Exception {
        parser = new TesterOutputScoreParser(
                TestConfig.load());
        checkNormal();
        checkOdd();
        checkTotals();
        checkCaseNames();
        checkCalculator();
        System.out.println("ScoreParserTest: " + passed + " passed, "
                + failed + " failed");
        if (failed > 0) {
            System.exit(1);
        }
    }

    // Checks a normal tester output with four cases and a printed total.
    private static void checkNormal() {
        String out = LINE + "\n---- Q2a ----\n" + LINE + "\n"
                + "Test 1: getTotalSpent(nosuchfile.csv, Bob)\n"
                + "Expected  :|-1.0|\nActual    :|-1.0|\nPassed\n"
                + LINE + "\n"
                + "Test 2: getTotalSpent(orderstester.csv, Bob)\n"
                + "Expected  :|13.5|\nActual    :|0.0|\nFailed\n"
                + LINE + "\n"
                + "Test 3: getTotalSpent(x, y)\nFailed -> Exception\n"
                + LINE + "\n"
                + "Test 4: getTotalSpent(a, b)\nPassed\n" + LINE
                + "\n2.0\n";
        int expectedCases = 4;
        double printedTotal = 2.0;
        QuestionResult result = parse(0, out, NO_ERROR, false);
        List<TestCaseResult> cases = result.getTestCaseResults();
        check(cases.size() == expectedCases, "four cases (" + cases.size()
                + ")");
        int thirdCase = 2;
        int fourthCase = 3;
        boolean verdicts = cases.get(0).isPassed() && !cases.get(1).isPassed()
                && !cases.get(thirdCase).isPassed()
                && cases.get(fourthCase).isPassed();
        check(verdicts, "pass/fail per case");
        check(result.getScore() == printedTotal, "printed total 2.0 used ("
                + result.getScore() + ")");
        check("getTotalSpent(nosuchfile.csv, Bob)".equals(cases.get(0)
                .getName()), "case name read (" + cases.get(0).getName()
                + ")");
        check(result.isCompiled() && !result.isTimedOut(),
                "compiled, not timed out");
        // CRLF output
        QuestionResult crlf = parse(0, out.replace(LF, CRLF), NO_ERROR,
                false);
        boolean sameAsLf = crlf.getTestCaseResults().size() == expectedCases
                && crlf.getScore() == printedTotal;
        check(sameAsLf, "CRLF output parsed the same");
        // stderr kept in the log
        QuestionResult withErr = parse(0, out, "java.lang.Exception: x",
                false);
        check(withErr.getOutput().contains("java.lang.Exception"),
                "stderr kept in the log");
    }

    // Checks odd output: student lines, no output, time outs, crashes,
    // odd headers and verdicts, and other line endings.
    private static void checkOdd() {
        checkStudentLines();
        checkTimeOutAndCrash();
        checkVerdicts();
        checkHeaders();
        checkOtherOutput();
    }

    // Checks that lines the student printed never count, and that no
    // output gives 0.
    private static void checkStudentLines() {
        // Student prints fake verdicts and a fake total
        String out = "Test 1: f()\n" + STUDENT + "Passed\n" + STUDENT
                + "Test 2: g()\n" + "Failed\n" + STUDENT + "4.0\n" + "0.0\n";
        QuestionResult result = parse(0, out, NO_ERROR, false);
        boolean ignored = result.getTestCaseResults().size() == 1
                && !result.getTestCaseResults().get(0).isPassed();
        check(ignored, "student lines never count as verdicts or headers");
        check(result.getScore() == 0.0, "student-printed total ignored ("
                + result.getScore() + ")");
        // No output at all
        QuestionResult none = parse(0, "", NO_ERROR, false);
        check(none.getTestCaseResults().isEmpty() && none.getScore() == 0.0,
                "empty output gives 0");
    }

    // Checks a tester stopped by the time out, and one that crashed.
    private static void checkTimeOutAndCrash() {
        // Timed out after case 2 started
        String partial = "Test 1: a\nPassed\nTest 2: b\n";
        int startedCases = 2;
        int killedExit = -1;
        QuestionResult timed = parse(killedExit, partial, NO_ERROR, true);
        boolean unfinished = timed.getTestCaseResults().size() == startedCases
                && timed.getTestCaseResults().get(1).getDetail().equals(
                TesterOutputScoreParser.NOT_FINISHED);
        check(unfinished, "unfinished case marked not finished");
        check(timed.getScore() == 1.0 && timed.isTimedOut(),
                "timed out: score is pass count (" + timed.getScore() + ")");
        // Crash: non-zero exit with a number as last line
        QuestionResult crashed = parse(1, "Test 1: a\nPassed\n3.0\n",
                NO_ERROR, false);
        check(crashed.getScore() == 1.0,
                "non-zero exit ignores printed total (" + crashed.getScore()
                + ")");
    }

    // Checks verdicts with no header, two after one header, indented and
    // in lower case.
    private static void checkVerdicts() {
        // No headers, only verdicts
        int bareCases = 3;
        double barePasses = 2.0;
        QuestionResult bare = parse(0, "Passed\nPassed\nFailed\n", NO_ERROR,
                false);
        boolean bareCounted = bare.getTestCaseResults().size() == bareCases
                && bare.getScore() == barePasses;
        check(bareCounted, "verdicts without headers count ("
                + bare.getTestCaseResults().size() + ", " + bare.getScore()
                + ")");
        // Two verdicts after one header: the second has no open case, so
        // it is a case of its own, as for a tester that prints no headers
        int twiceCases = 2;
        QuestionResult twice = parse(0, "Test 1: a\nPassed\nFailed\n",
                NO_ERROR, false);
        boolean ownCase = twice.getTestCaseResults().size() == twiceCases
                && twice.getTestCaseResults().get(0).isPassed()
                && !twice.getTestCaseResults().get(1).isPassed();
        check(ownCase,
                "a second verdict after one header is a case of its own");
        // Verdict with leading spaces
        QuestionResult spaced = parse(0, "Test 1: a\n   Passed!  \n",
                NO_ERROR, false);
        check(spaced.getTestCaseResults().get(0).isPassed(),
                "indented 'Passed!' counts");
        // Lower-case markers
        QuestionResult lower = parse(0, "Test 1: a\npassed\n", NO_ERROR,
                false);
        check(!lower.getTestCaseResults().get(0).isPassed(),
                "lower-case 'passed' is not a pass (markers are exact)");
    }

    // Checks which lines are case headers, names in unicode and verdicts
    // with text after the marker.
    private static void checkHeaders() {
        // "Testing" is not a header
        QuestionResult testing = parse(0, "Testing the class\nTest 1: a\n"
                + "Passed\n", NO_ERROR, false);
        check(testing.getTestCaseResults().size() == 1,
                "'Testing' line is not a case header");
        // Header with no space: Test1
        int tightCases = 2;
        QuestionResult tight = parse(0, "Test1 a\nPassed\nTest2 b\nFailed\n",
                NO_ERROR, false);
        check(tight.getTestCaseResults().size() == tightCases,
                "Test1 without a space is a header");
        // Unicode in names
        QuestionResult unicode = parse(0, "Test 1: greet(éè)\nPassed\n",
                NO_ERROR, false);
        check("greet(éè)".equals(unicode.getTestCaseResults().get(0)
                .getName()), "unicode case name");
        // Verdict line saying "Passed: false" counts as a pass (startsWith)
        QuestionResult tricky = parse(0, "Test 1: a\nFailed: expected x\n"
                + "Test 2: b\nPassed (partial)\n", NO_ERROR, false);
        boolean markerFirst = !tricky.getTestCaseResults().get(0).isPassed()
                && tricky.getTestCaseResults().get(1).isPassed();
        check(markerFirst, "verdicts with text after the marker");
    }

    // Checks a null process result, CR-only output and tab-indented
    // output.
    private static void checkOtherOutput() {
        // Null process result
        QuestionResult nothing = parser.parse(QUESTION, null);
        check(nothing != null && nothing.getTestCaseResults().isEmpty(),
                "null process result");
        check(parser.readTotal(null) == null, "readTotal null");
        // Old Mac CR only
        int crCases = 2;
        double crTotal = 2.0;
        QuestionResult carriage = parse(0, "Test 1: a\rPassed\rTest 2: b\r"
                + "Passed\r2\r", NO_ERROR, false);
        boolean crRead = carriage.getTestCaseResults().size() == crCases
                && carriage.getScore() == crTotal;
        check(crRead, "CR-only output");
        // Tab-indented student-looking output that is not prefixed
        QuestionResult tabbed = parse(0, "\tTest 1: a\n\tPassed\n", NO_ERROR,
                false);
        check(tabbed.getTestCaseResults().size() == 1,
                "tab-indented header and verdict counted");
    }

    // Checks how the printed total on the last line is read.
    private static void checkTotals() {
        double fractionalTotal = 1.5;
        check(scoreOf("Test 1: a\nPassed\n1.5\n") == fractionalTotal,
                "fractional total");
        check(scoreOf("Test 1: a\nPassed\n99\n") == MAX_MARKS,
                "total capped at max 4");
        Double negative = scoreOf("Test 1: a\nPassed\n-2\n");
        check(negative >= 0.0, "negative printed total not below 0 ("
                + negative + ")");
        Double notANumber = scoreOf("Test 1: a\nPassed\nNaN\n");
        check(!Double.isNaN(notANumber), "printed 'NaN' does not give a NaN "
                + "score (" + notANumber + ")");
        Double infinite = scoreOf("Test 1: a\nFailed\nInfinity\n");
        check(infinite == 0.0, "printed 'Infinity' with every case failed "
                + "is not full marks (" + infinite + ")");
        // Known limitation: the total is read with Double.valueOf, which
        // accepts Java's d and f endings; only the tester prints that line
        Double suffixed = scoreOf("Test 1: a\nFailed\n4d\n");
        note("'4d' (Java double ending) is read as a total of 4 ("
                + suffixed + "); ExecuteStage then caps it at the marks "
                + "the passed cases earn");
        check(scoreOf("Test 1: a\nPassed\nScore: 3\n") == 1.0,
                "non-number last line: pass count");
        double paddedTotal = 2.0;
        check(scoreOf("Test 1: a\nPassed\n  2.0  \n\n\n") == paddedTotal,
                "trailing blank lines skipped");
        check(scoreOf("Test 1: a\nPassed\n1,5\n") == 1.0,
                "comma decimal falls back to pass count");
        // Pass count capped at max
        int passingCases = 6;
        String many = "";
        for (int i = 1; i <= passingCases; i++) {
            many = many + "Test " + i + ": x\nPassed\n";
        }
        check(scoreOf(many) == MAX_MARKS,
                "pass count capped at max");
        // Printed total disagrees with verdicts
        Double disagree = scoreOf("Test 1: a\nFailed\nTest 2: b\nFailed\n"
                + "2.0\n");
        System.out.println("   printed total 2.0 with both cases failed "
                + "gives " + disagree
                + " (the total wins; no warning in the parser)");
        check(parser.readTotal(new ProcessResult(0, "1\n" + STUDENT + "9\n",
                NO_ERROR, false, 1)).equals(1.0),
                "readTotal skips student lines");
    }

    // Checks how the name of a case is read from its header line.
    private static void checkCaseNames() {
        check(sameText(TesterOutputScoreParser.readCaseName("Test 1: f(x)",
                MARKER), "f(x)"), "colon");
        check(sameText(TesterOutputScoreParser.readCaseName("Test 12 - f(x)",
                MARKER), "f(x)"), "dash");
        check(sameText(TesterOutputScoreParser.readCaseName("Test 3) f",
                MARKER), "f"), "close bracket separator");
        check(sameText(TesterOutputScoreParser.readCaseName("Test 4 => f()",
                MARKER), "f()"), "arrow");
        check(sameText(TesterOutputScoreParser.readCaseName(
                "Test 5: getHighest([])", MARKER), "getHighest([])"),
                "closing brackets kept");
        check(sameText(TesterOutputScoreParser.readCaseName(
                "Test 6: empty array:  ", MARKER), "empty array"),
                "trailing colon and spaces trimmed");
        check(TesterOutputScoreParser.readCaseName("Test 7", MARKER) == null,
                "no name null");
        check(TesterOutputScoreParser.readCaseName("Test 8:", MARKER) == null,
                "only colon null");
        check(TesterOutputScoreParser.readCaseName("Nope 1: x", MARKER)
                == null, "other marker null");
        check(sameText(TesterOutputScoreParser.readCaseName("   Test 9:f",
                MARKER), "f"), "leading spaces and no space after colon");
        // Known limitation: - and # also separate a number from a name
        // ("Test 1 - name"), so they are dropped from the start of a name
        note("a case name starting with a minus sign loses it (got "
                + TesterOutputScoreParser.readCaseName("Test 10: -5 is odd",
                MARKER) + ")");
        note("a case name starting with # loses it (got "
                + TesterOutputScoreParser.readCaseName("Test 11: #tag",
                MARKER) + ")");
        check(sameText(TesterOutputScoreParser.readCaseName(
                "Test 12: 3 items", MARKER), "3 items"),
                "name starting with a digit after separator");
    }

    // Checks that ScoreCalculator keeps a score inside 0 and the marks.
    private static void checkCalculator() {
        ScoreCalculator calculator = new ScoreCalculator();
        QuestionResult result = new QuestionResult(QUESTION);
        double inRange = 2.5;
        result.setScore(inRange);
        check(calculator.questionScore(result) == inRange, "score kept");
        result.setScore(-1);
        check(calculator.questionScore(result) == 0.0, "negative clamped");
        result.setScore(Double.NaN);
        check(calculator.questionScore(result) == 0.0, "NaN clamped");
        double overMax = 10;
        result.setScore(overMax);
        check(calculator.questionScore(result) == MAX_MARKS,
                "over max clamped");
        result.setScore(Double.POSITIVE_INFINITY);
        check(calculator.questionScore(result) == MAX_MARKS,
                "infinity clamped");
        check(calculator.questionScore(null) == 0.0, "null result");
        check(calculator.questionScore(new QuestionResult(null)) == 0.0,
                "null question");
    }

    // Says whether a text is not null and equals the expected text.
    private static boolean sameText(String actual, String expected) {
        return actual != null && actual.equals(expected);
    }

    // Parses a normal run of the given output and returns its score.
    private static Double scoreOf(String out) {
        return parse(0, out, NO_ERROR, false).getScore();
    }

    // Parses a made-up run with the given exit code, output, errors and
    // time out flag.
    private static QuestionResult parse(int exit, String out, String err,
            boolean timedOut) {
        return parser.parse(QUESTION, new ProcessResult(exit, out, err,
                timedOut, RUN_MILLIS));
    }

    // Prints a known limitation: behaviour that is kept on purpose or is
    // not worth changing, so it is reported but does not fail the run
    private static void note(String message) {
        System.out.println("NOTE (known limitation): " + message);
    }

    // Counts one check and prints it when it fails.
    private static void check(boolean condition, String message) {
        if (condition) {
            passed++;
        } else {
            failed++;
            System.out.println("FAIL: " + message);
        }
    }
}
