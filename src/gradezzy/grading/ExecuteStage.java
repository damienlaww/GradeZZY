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
import java.util.ArrayList;
import java.util.Collections;
import java.util.List;
import java.util.UUID;

import gradezzy.config.ConfigKeys;
import gradezzy.exec.ProcessExecutionException;
import gradezzy.exec.ProcessResult;
import gradezzy.exec.SkippedCaseException;
import gradezzy.exec.TesterHarness;
import gradezzy.exec.TesterRunner;
import gradezzy.model.Anomaly;
import gradezzy.model.AnomalyType;
import gradezzy.model.Question;
import gradezzy.model.QuestionResult;
import gradezzy.model.Severity;
import gradezzy.model.Submission;
import gradezzy.model.TestCaseResult;
import gradezzy.testcase.FolderSnapshot;
import gradezzy.testcase.QuestionWorkspace;
import gradezzy.testcase.TesterFile;
import gradezzy.testcase.TesterMarks;
import gradezzy.util.TextUtils;

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

    /**
     * Start of the id of each anomaly this stage reports. Grading one
     * question again uses it to find the ones it replaces.
     */
    public static final String ID_PREFIX = "execute-";

    // Exit code of a tester that finished normally
    private static final int SUCCESS_EXIT_CODE = 0;

    // What the instructor can do about a tester that ended with an error
    private static final String RUNTIME_ERROR_ACTION =
            "Open code and test output on Review to read the tester output.";

    // What the instructor can do about a run that ended before its total
    private static final String EARLY_EXIT_ACTION = "Read the test output "
            + "on Review before accepting this mark: student code can end "
            + "the run and leave passes that the tester never confirmed.";

    // What the instructor can do about a total its cases do not support
    private static final String TOTAL_TOO_HIGH_ACTION =
            "Read the code on Review: it may change the tester's score.";

    // What the instructor can do about a tester whose output was not
    // fully recognised, or whose total is more than the question's maximum
    private static final String CHECK_OUTPUT_ACTION = "Read the test "
            + "output on Review and change the mark if it is wrong.";

    // Separates the reasons in one message
    private static final String REASON_SEPARATOR = "; ";

    // What the instructor can do about files changed before a tester ran
    private static final String FILES_CHANGED_ACTION = "Read the code on "
            + "Review. The mark comes from the original files.";

    // Separates the file names in a message
    private static final String NAME_SEPARATOR = ", ";

    // What the instructor can do about a printed total no verdict backs
    private static final String CHECK_VERDICTS_ACTION = "Read the test "
            + "output on Review and change the mark if it is wrong. If the "
            + "tester words its verdicts differently, set \"Passed line\" "
            + "and \"Failed line\" in Settings (" + ConfigKeys.PASS_MARKER
            + NAME_SEPARATOR + ConfigKeys.FAIL_MARKER + ").";

    // Stands for "no test case hung"
    private static final int NO_CASE = 0;

    // What Java prints when a program uses more memory than it may
    private static final String OUT_OF_MEMORY_MARKER =
            "java.lang.OutOfMemoryError";

    // What the instructor can do about a tester that ran out of memory
    private static final String OUT_OF_MEMORY_ACTION = "Read "
            + "the code on Review: it may keep far more data than the "
            + "question needs. The mark stays as the tester gave it.";

    // Allowed rounding gap when a printed total is compared with the cap
    private static final double SCORE_TOLERANCE = 0.000001;

    // Put between the logs of two runs of the same tester
    private static final String RUN_SEPARATOR = "\n\n";

    // Ends the first line of a run's log, its label
    private static final String LINE_END = "\n";

    // Runs and test cases are counted from 1, while lists count from 0
    private static final int FIRST_NUMBER = 1;

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
        if (tester == null) {
            return;
        }

        FolderSnapshot snapshot = restoreFiles(submission, question, tester);
        // The copy the compile stage made for this question only
        Path folder = QuestionWorkspace.find(submission, question);
        if (folder == null) {
            return;
        }

        // As the brief says: a case that hangs is removed and the tester
        // is run again, so the cases after it are still marked. A case
        // that crashes the whole tester is treated the same way. Each pass
        // skips one more case. Each rerun can take the whole time limit,
        // so after the setting's number of reruns the cases not yet run
        // are left to the instructor, as the brief allows.
        long maxReruns = context.getConfig().getLong(
                ConfigKeys.MAX_HANG_RERUNS);
        List<Integer> skippedCases = new ArrayList<>();
        // Each run's log, kept so a stopped case's own output is not lost
        List<String> runLogs = new ArrayList<>();
        ProcessResult run = runTester(folder, tester.getClassName(),
                skippedCases);
        ProcessResult firstRun = run;
        boolean outOfMemory = ranOutOfMemory(run);
        QuestionResult parsed = scoreParser.parse(question, run);
        int stoppedCase = findStoppedCase(run, parsed, skippedCases);
        while (stoppedCase != NO_CASE && skippedCases.size() < maxReruns) {
            addRunLog(runLogs, run, stoppedCase, parsed.getOutput());
            skippedCases.add(stoppedCase);
            putBackFiles(snapshot, question);
            run = runTester(folder, tester.getClassName(), skippedCases);
            if (ranOutOfMemory(run)) {
                outOfMemory = true;
            }
            parsed = scoreParser.parse(question, run);
            stoppedCase = findStoppedCase(run, parsed, skippedCases);
        }

        result.setTimedOut(firstRun.isTimedOut());
        storeOutput(result, context, runLogs, run, stoppedCase,
                parsed.getOutput());
        copyCases(parsed, skippedCases, result);
        Double printedTotal = scoreParser.readTotal(run);
        result.setScore(scoreOf(submission, tester, question, parsed,
                result, printedTotal, run, skippedCases));
        reportOutcome(submission, context, question, firstRun, run, result,
                skippedCases, stoppedCase, outOfMemory);
        reportUnrecognised(submission, context, question, run, result,
                printedTotal, skippedCases);
    }

    /**
     * Adds the labelled log of one finished run to the logs kept so far.
     *
     * @param runLogs the labelled log of each earlier run, in order
     * @param run the finished run
     * @param stoppedCase the case that stopped the run, or NO_CASE
     * @param log the log of the run
     */
    private void addRunLog(List<String> runLogs, ProcessResult run,
            int stoppedCase, String log) {
        int runNumber = runLogs.size() + FIRST_NUMBER;
        runLogs.add(labelRun(runNumber, run, stoppedCase, log));
    }

    /**
     * Stores the output of a question's runs: the last run's log when the
     * tester ran once, else the labelled log of every run, fitted to the
     * output limit.
     *
     * @param result the result of the question, filled in here
     * @param context the context
     * @param runLogs the labelled log of each earlier run, in order
     * @param lastRun the last run of the tester
     * @param stoppedCase the case that stopped the last run, or NO_CASE
     * @param lastLog the log of the last run
     */
    private void storeOutput(QuestionResult result, GradingContext context,
            List<String> runLogs, ProcessResult lastRun, int stoppedCase,
            String lastLog) {
        if (runLogs.isEmpty()) {
            result.setOutput(lastLog);
            return;
        }

        addRunLog(runLogs, lastRun, stoppedCase, lastLog);
        int maxCharacters = context.getConfig().getInt(
                ConfigKeys.MAX_OUTPUT_CHARACTERS);
        result.setOutput(fitRunLogs(runLogs, maxCharacters));
    }

    /**
     * Copies the test cases of the last run into the question's result.
     * A skipped case is shown as skipped and failed, not as the "Failed ->
     * Exception" the tester printed for it, nor as the "Passed" a tester
     * prints when it expects an exception and catches the skip.
     *
     * @param parsed the parsed last run
     * @param skippedCases the cases that were skipped
     * @param result the result of the question, filled in here
     */
    private void copyCases(QuestionResult parsed, List<Integer> skippedCases,
            QuestionResult result) {
        for (TestCaseResult testCase : parsed.getTestCaseResults()) {
            if (skippedCases.contains(testCase.getTestNumber())) {
                result.addTestCaseResult(new TestCaseResult(
                        testCase.getTestNumber(), false,
                        SkippedCaseException.SKIPPED_MESSAGE,
                        testCase.getName()));
            } else {
                result.addTestCaseResult(testCase);
            }
        }
    }

    /**
     * Works out a question's score from its last run. A run that did not
     * finish never printed its total, and a total that is not a number
     * cannot be read, so the marks of the passed cases are added up. A
     * total above the question's maximum is cut to it and flagged. A
     * total is kept only if it is not more than the passed cases earn:
     * student code can hide the tester's lines and set its score field by
     * reflection, and a tester that expects an exception counts a skipped
     * case as passed. A total that no case's verdict backs is never
     * believed: when no case printed a verdict at all, no case passed, so
     * the question gets 0 with no flag here, because reportUnrecognised
     * already flags the run.
     *
     * @param submission the submission, which gets a flag if the printed
     *        total is too high
     * @param tester the tester of the question
     * @param question the question
     * @param parsed the parsed last run, as the tester printed it
     * @param result the question's result, with skipped cases failed
     * @param printedTotal the total the last run printed, or null
     * @param run the last run
     * @param skippedCases the cases that were skipped
     * @return the score
     */
    private double scoreOf(Submission submission, TesterFile tester,
            Question question, QuestionResult parsed, QuestionResult result,
            Double printedTotal, ProcessResult run,
            List<Integer> skippedCases) {
        // Only a finished run's printed total can cap the passed cases'
        // marks; with no total the marks are all there is, so they are
        // read as for a run that did not finish
        boolean totalPrinted = finishedNormally(run) && printedTotal != null;
        double earned = marksEarned(submission, tester, question, result,
                totalPrinted);
        if (!totalPrinted) {
            return Math.min(earned, question.getMaxScore());
        }

        // No verdict backs the total: reportUnrecognised flags the run
        if (!hasVerdict(result)) {
            return earned;
        }

        double total = capAtMaximum(submission, question, printedTotal);
        if (total <= earned + SCORE_TOLERANCE) {
            return total;
        }

        // The tester's own count, with the skipped cases it caught as
        // passed, explains the total: no flag, the skip message says why
        double earnedAsPrinted = marksEarned(submission, tester, question,
                parsed, totalPrinted);
        boolean skipCounted = !skippedCases.isEmpty()
                && total <= earnedAsPrinted + SCORE_TOLERANCE;
        if (!skipCounted) {
            addAnomaly(submission, AnomalyType.RUNTIME_ERROR, "The "
                    + question.getId() + " tester printed a total of "
                    + printedTotal + ", but only " + passCount(result)
                    + " of " + result.getTestCaseResults().size()
                    + " case(s) passed, so " + earned + " was given.",
                    TOTAL_TOO_HIGH_ACTION);
        }
        return earned;
    }

    /**
     * Cuts a printed total to the question's maximum, and flags a total
     * above it: the maximum read from the tester may be wrong, as with a
     * tester that adds its marks in a helper method called once per case.
     *
     * @param submission the submission, which gets the flag
     * @param question the question
     * @param printedTotal the total the tester printed
     * @return the total, at most the question's maximum
     */
    private double capAtMaximum(Submission submission, Question question,
            double printedTotal) {
        double maxScore = question.getMaxScore();
        if (printedTotal <= maxScore + SCORE_TOLERANCE) {
            return printedTotal;
        }

        addAnomaly(submission, AnomalyType.RUNTIME_ERROR, "The "
                + question.getId() + " tester printed a total of "
                + printedTotal + ", more than the question's maximum of "
                + maxScore + ", so " + maxScore + " was given. The maximum "
                + "is read from the tester's score lines and may be wrong "
                + "for this tester.", CHECK_OUTPUT_ACTION);
        return maxScore;
    }

    /**
     * Flags a run that timed out, was ended early or crashed, and says
     * what happened to its cases.
     *
     * @param submission the submission
     * @param context the context
     * @param question the question
     * @param firstRun the first run of the tester
     * @param lastRun the last run of the tester
     * @param result the question's result, with skipped cases failed
     * @param skippedCases the cases that were skipped
     * @param stoppedCase the case the last run stopped in, or NO_CASE
     * @param outOfMemory true when a run printed OutOfMemoryError
     */
    private void reportOutcome(Submission submission, GradingContext context,
            Question question, ProcessResult firstRun, ProcessResult lastRun,
            QuestionResult result, List<Integer> skippedCases,
            int stoppedCase, boolean outOfMemory) {
        boolean skipFailed =
                lastRun.getExitCode() == TesterRunner.SKIP_FAILED_EXIT_CODE;
        String outcome = passCount(result) + " case(s) passed and their "
                + "marks are kept." + describeSkipped(skippedCases, skipFailed)
                + describeSkipFailed(skippedCases, skipFailed)
                + describeNotRun(stoppedCase);
        if (firstRun.isTimedOut()) {
            long limitSeconds = context.getConfig().getLong(
                    ConfigKeys.RUN_TIMEOUT_SECONDS);
            addAnomaly(submission, AnomalyType.TIMEOUT, "The "
                    + question.getId() + " tester was stopped at the "
                    + limitSeconds + " s limit. " + outcome,
                    "Check the loops in " + question.getFileName()
                    + ", then retry " + question.getId() + ".");
        } else if (firstRun.getExitCode() == TesterRunner.EARLY_EXIT_CODE) {
            // The tester never printed its own total, so nothing confirms
            // the passes: the mark stands, but the instructor is told
            addAnomaly(submission, AnomalyType.RUNTIME_ERROR, "The "
                    + question.getId() + " tester did not reach its end: "
                    + "the program was ended early, for example by "
                    + "System.exit in " + question.getFileName() + ". "
                    + passCount(result) + " case(s) passed before it ended. "
                    + "A run that ends early never prints the tester's "
                    + "total, so these passes could not be checked against "
                    + "it.", EARLY_EXIT_ACTION);
        } else if (outOfMemory) {
            int limitMb = context.getConfig().getInt(
                    ConfigKeys.MAX_MEMORY_MB);
            addAnomaly(submission, AnomalyType.RUNTIME_ERROR, "The "
                    + question.getId() + " tester ran out of memory: "
                    + "student code may use at most " + limitMb + " MB ("
                    + ConfigKeys.MAX_MEMORY_MB + "). " + outcome,
                    OUT_OF_MEMORY_ACTION);
        } else if (firstRun.getExitCode() == TesterHarness.CRASH_EXIT_CODE) {
            addAnomaly(submission, AnomalyType.RUNTIME_ERROR, "The "
                    + question.getId() + " tester stopped because a case "
                    + "threw an error it cannot catch, such as a "
                    + "StackOverflowError from recursion that never ends. "
                    + outcome, RUNTIME_ERROR_ACTION);
        } else if (firstRun.getExitCode() != SUCCESS_EXIT_CODE) {
            addAnomaly(submission, AnomalyType.RUNTIME_ERROR, "The "
                    + question.getId() + " tester ended with an error (exit "
                    + "code " + firstRun.getExitCode() + "). " + outcome,
                    RUNTIME_ERROR_ACTION);
        }
    }

    /**
     * Flags a run that ended normally but whose output was not fully
     * recognised, so marks may be missing with no other flag: its last
     * line is not a total, it printed no case GradeZZY recognises, or a
     * case has no verdict, as when student code replaces System.out and
     * hides the tester's later lines, or when the tester words its
     * verdicts differently. A printed total that no verdict backs was not
     * given, and the message says so.
     *
     * @param submission the submission
     * @param context the context
     * @param question the question
     * @param lastRun the last run of the tester
     * @param result the question's result, with its score set
     * @param printedTotal the total the last run printed, or null
     * @param skippedCases the cases that were skipped
     */
    private void reportUnrecognised(Submission submission,
            GradingContext context, Question question, ProcessResult lastRun,
            QuestionResult result, Double printedTotal,
            List<Integer> skippedCases) {
        if (!finishedNormally(lastRun)) {
            return;
        }

        List<String> reasons = new ArrayList<>();
        if (printedTotal == null) {
            reasons.add("its last line is not a total GradeZZY can read, so "
                    + "each passed case was given what its own score line in "
                    + "the tester gives");
        }
        if (result.getTestCaseResults().isEmpty()) {
            String headerMarker = context.getConfig().getString(
                    ConfigKeys.TEST_HEADER_MARKER);
            reasons.add("it printed no test case GradeZZY recognises (a \""
                    + headerMarker + " 1\" header, or a verdict line)");
        }
        List<String> missing = listMissingVerdicts(result, skippedCases);
        if (!missing.isEmpty()) {
            reasons.add("case(s) " + TextUtils.join(NAME_SEPARATOR, missing)
                    + " printed no verdict that GradeZZY recognises");
        }
        if (reasons.isEmpty()) {
            return;
        }

        String start = "The " + question.getId() + " tester's output was "
                + "not fully recognised: "
                + TextUtils.join(REASON_SEPARATOR, reasons) + ". ";
        if (totalRefused(result, printedTotal)) {
            addAnomaly(submission, AnomalyType.RUNTIME_ERROR, start
                    + "The printed total of " + printedTotal + " could not "
                    + "be checked against any verdict, so the passed "
                    + "cases' marks were given: " + question.getId()
                    + " was given " + result.getScore() + ".",
                    CHECK_VERDICTS_ACTION);
            return;
        }
        addAnomaly(submission, AnomalyType.RUNTIME_ERROR, start
                + question.getId() + " was given " + result.getScore()
                + "; marks may be missing.", CHECK_OUTPUT_ACTION);
    }

    /**
     * Says whether a printed total was not given because no case printed
     * a verdict that could back it.
     *
     * @param result the question's result, with its score set
     * @param printedTotal the total the last run printed, or null
     * @return true if the total was printed, unbacked and not given
     */
    private boolean totalRefused(QuestionResult result,
            Double printedTotal) {
        if (printedTotal == null || hasVerdict(result)) {
            return false;
        }
        return result.getScore() + SCORE_TOLERANCE < printedTotal;
    }

    /**
     * Lists the cases that have no verdict and were not skipped.
     *
     * @param result the question's result
     * @param skippedCases the cases that were skipped
     * @return the numbers of those cases, as text
     */
    private List<String> listMissingVerdicts(QuestionResult result,
            List<Integer> skippedCases) {
        List<String> missing = new ArrayList<>();
        for (TestCaseResult testCase : result.getTestCaseResults()) {
            boolean noVerdict = TesterOutputScoreParser.NOT_FINISHED.equals(
                    testCase.getDetail());
            boolean skipped = skippedCases.contains(
                    testCase.getTestNumber());
            if (noVerdict && !skipped) {
                missing.add(String.valueOf(testCase.getTestNumber()));
            }
        }
        return missing;
    }

    /**
     * Says whether any case of a result printed a pass or fail verdict.
     *
     * @param result the question's result
     * @return true if at least one case has a verdict
     */
    private boolean hasVerdict(QuestionResult result) {
        for (TestCaseResult testCase : result.getTestCaseResults()) {
            boolean noVerdict = TesterOutputScoreParser.NOT_FINISHED.equals(
                    testCase.getDetail())
                    || SkippedCaseException.SKIPPED_MESSAGE.equals(
                            testCase.getDetail());
            if (!noVerdict) {
                return true;
            }
        }
        return false;
    }

    /**
     * Says whether a run reached the end of the tester by itself.
     *
     * @param run the run
     * @return true if it was not stopped and exited normally
     */
    private boolean finishedNormally(ProcessResult run) {
        return !run.isTimedOut() && run.getExitCode() == SUCCESS_EXIT_CODE;
    }

    // Says whether a run of the tester stopped because student code used
    // more memory than exec.max.memory.mb allows.
    private boolean ranOutOfMemory(ProcessResult run) {
        String output = run.getStandardOutput() + run.getStandardError();
        return output.contains(OUT_OF_MEMORY_MARKER);
    }

    /**
     * Finds the test case that stopped the tester: in a run that timed
     * out or crashed, the last one started, if it has no result and was
     * not already skipped. A skip that did not help, as with a tester
     * whose header is outside its try block, is not tried twice. A run
     * the student's own System.exit ended is left as it is.
     *
     * @param run the finished run
     * @param parsed the parsed result of the run
     * @param skippedCases the cases already skipped
     * @return the number of the case, or NO_CASE
     */
    private int findStoppedCase(ProcessResult run, QuestionResult parsed,
            List<Integer> skippedCases) {
        List<TestCaseResult> cases = parsed.getTestCaseResults();
        boolean crashed = run.getExitCode() == TesterHarness.CRASH_EXIT_CODE;
        boolean stopped = run.isTimedOut() || crashed;
        if (!stopped || cases.isEmpty()) {
            return NO_CASE;
        }

        int lastIndex = cases.size() - 1;
        TestCaseResult lastCase = cases.get(lastIndex);
        boolean finishedCase = !TesterOutputScoreParser.NOT_FINISHED.equals(
                lastCase.getDetail());
        boolean alreadySkipped = skippedCases.contains(
                lastCase.getTestNumber());
        if (finishedCase || alreadySkipped) {
            return NO_CASE;
        }
        return lastCase.getTestNumber();
    }

    /**
     * Says which cases were skipped, for the end of a message. When the
     * last skip itself stopped the tester, that case is left out here and
     * described by describeSkipFailed.
     *
     * @param skippedCases the cases skipped
     * @param skipFailed true if the last run stopped at the last skip
     * @return a sentence with a space in front, or empty if none were
     */
    private String describeSkipped(List<Integer> skippedCases,
            boolean skipFailed) {
        int skipsThatWorked = skippedCases.size();
        if (skipFailed) {
            skipsThatWorked--;
        }
        if (skipsThatWorked <= 0) {
            return "";
        }

        List<String> numbers = new ArrayList<>();
        for (int index = 0; index < skipsThatWorked; index++) {
            numbers.add(String.valueOf(skippedCases.get(index)));
        }
        return " Case(s) " + TextUtils.join(NAME_SEPARATOR, numbers)
                + " stopped the tester (a hang or a crash), so they got "
                + "no marks and the tester was run again for the cases "
                + "after them.";
    }

    /**
     * Says that the tester could not skip a case, for the end of a
     * message. The tester prints the case's header outside the case's try
     * block, so the skip stopped the whole tester and the cases after it
     * were never run.
     *
     * @param skippedCases the cases skipped
     * @param skipFailed true if the last run stopped at the last skip
     * @return a sentence with a space in front, or empty if the skip
     *         worked
     */
    private String describeSkipFailed(List<Integer> skippedCases,
            boolean skipFailed) {
        if (!skipFailed || skippedCases.isEmpty()) {
            return "";
        }

        int lastIndex = skippedCases.size() - 1;
        int failedCase = skippedCases.get(lastIndex);
        return " Case " + failedCase + " stopped the tester, and when it "
                + "was run again the tester could not skip case "
                + failedCase + " (it prints the case's header outside the "
                + "case's try block), so the cases after it were not run. "
                + "Case " + failedCase + " and the cases after it got no "
                + "marks. Check them by hand.";
    }

    /**
     * Says that the reruns ran out, for the end of a message.
     *
     * @param stoppedCase the case that stopped the last run, or NO_CASE
     * @return a sentence with a space in front, or empty if none did
     */
    private String describeNotRun(int stoppedCase) {
        if (stoppedCase == NO_CASE) {
            return "";
        }
        return " Case " + stoppedCase + " stopped the tester and it was "
                + "not run again, so case " + stoppedCase + " and the cases "
                + "after it got no marks. Check them by hand.";
    }

    /**
     * Puts a label above the log of one run of a tester.
     *
     * @param runNumber the run, counted from 1
     * @param run the finished run
     * @param stoppedCase the case that stopped the run, or NO_CASE
     * @param log the log of the run
     * @return the labelled log
     */
    private String labelRun(int runNumber, ProcessResult run,
            int stoppedCase, String log) {
        String label = "Run " + runNumber;
        if (stoppedCase != NO_CASE && run.isTimedOut()) {
            label += ": stopped at the time limit in case " + stoppedCase;
        } else if (stoppedCase != NO_CASE) {
            label += ": crashed in case " + stoppedCase;
        } else if (run.getExitCode() == TesterRunner.SKIP_FAILED_EXIT_CODE) {
            label += ": stopped where it skipped a case";
        }
        return "--- " + label + " ---" + LINE_END + log;
    }

    /**
     * Joins the logs of a question's runs so that, apart from the labels
     * and the notes on what was cut, they keep to the output limit of one
     * run. The last run holds the marks that count, so it is kept whole;
     * the earlier runs share what is left and keep their ends, where the
     * case hung.
     *
     * @param runLogs the labelled log of each run, in order
     * @param maxCharacters the setting exec.max.output.characters
     * @return the joined logs
     */
    private String fitRunLogs(List<String> runLogs, int maxCharacters) {
        // Every run but the last; also the index of the last run
        int earlierRuns = runLogs.size() - 1;
        String lastLog = runLogs.get(earlierRuns);
        int spare = maxCharacters - lastLog.length();
        int share = spare / earlierRuns;
        if (share < 0) {
            share = 0;
        }

        List<String> fitted = new ArrayList<>();
        for (int index = 0; index < earlierRuns; index++) {
            fitted.add(keepEnd(runLogs.get(index), share));
        }
        fitted.add(lastLog);
        return TextUtils.join(RUN_SEPARATOR, fitted);
    }

    /**
     * Shortens a labelled run log to its label and its last characters.
     *
     * @param log the labelled log
     * @param limit the most characters kept after the label
     * @return the log, with a note on how much was cut if it was shortened
     */
    private String keepEnd(String log, int limit) {
        int labelEnd = log.indexOf(LINE_END) + LINE_END.length();
        String label = log.substring(0, labelEnd);
        String body = log.substring(labelEnd);
        if (body.length() <= limit) {
            return log;
        }

        int cut = body.length() - limit;
        return label + "[" + cut + " characters cut]" + LINE_END
                + body.substring(cut);
    }

    /**
     * Adds up what the passed cases earn. Each case is worth what its own
     * "score +=" line in the tester gives, so a case worth 0.5 counts as
     * 0.5 and one worth 2 counts as 2. When the tester's lines cannot be
     * matched to its cases one for one, the passed cases of a run that
     * printed its total are taken to be worth the most they can be,
     * because this only caps that total, so no earned mark is taken away.
     * A run with no total, unfinished or with a last line that is not a
     * number, has only these marks, so its passed cases are taken to be
     * worth the least they can be, and the instructor is told.
     *
     * @param submission the submission, which gets a flag when a run with
     *        no total cannot be matched
     * @param tester the tester of the question
     * @param question the question
     * @param parsed the parsed run
     * @param totalPrinted true if the run reached its end and printed a
     *        total, so every case of the tester was run
     * @return what the passed cases earn
     */
    private double marksEarned(Submission submission, TesterFile tester,
            Question question, QuestionResult parsed, boolean totalPrinted) {
        int passes = passCount(parsed);
        if (passes == 0) {
            return 0.0;
        }

        List<TestCaseResult> cases = parsed.getTestCaseResults();
        List<Double> caseMarks = readCaseMarks(tester);
        if (caseMarks == null) {
            // The tester cannot be read now, so every case counts the same
            return passes * question.getMaxScore() / cases.size();
        }
        boolean matched = matchesCases(caseMarks, cases, totalPrinted);
        if (!matched && totalPrinted) {
            return TesterMarks.sumLargest(caseMarks, passes);
        }
        if (!matched) {
            double smallest = sumSmallest(caseMarks, passes);
            addAnomaly(submission, AnomalyType.RUNTIME_ERROR, "The "
                    + question.getId() + " tester's cases could not be "
                    + "matched one for one to its score lines, so its "
                    + passes + " passed case(s) were given the smallest "
                    + "marks a case is worth: " + smallest + " in all.",
                    CHECK_OUTPUT_ACTION);
            return smallest;
        }

        double total = 0.0;
        for (TestCaseResult testCase : cases) {
            if (testCase.isPassed()) {
                int markIndex = testCase.getTestNumber() - FIRST_NUMBER;
                total += caseMarks.get(markIndex);
            }
        }
        return total;
    }

    /**
     * Adds up the smallest marks in a tester, one for each passed case.
     *
     * @param caseMarks the tester's marks in order
     * @param passes the number of passed cases
     * @return the sum of the smallest marks, as many as there are passes
     */
    private double sumSmallest(List<Double> caseMarks, int passes) {
        List<Double> marks = new ArrayList<>(caseMarks);
        Collections.sort(marks);

        double total = 0.0;
        for (int index = 0; index < passes && index < marks.size();
                index++) {
            total += marks.get(index);
        }
        return total;
    }

    /**
     * Reads what each case of a tester is worth, in order.
     *
     * @param tester the tester of the question
     * @return the marks, or null if the tester cannot be read now
     */
    private List<Double> readCaseMarks(TesterFile tester) {
        try {
            return TesterMarks.readCaseMarks(tester.getSourceFile());
        } catch (IOException exception) {
            return null;
        }
    }

    /**
     * Says whether a tester has one mark for each of its cases, so that
     * case n is worth the n-th mark. A run with no total may have printed
     * only its first cases, so it only needs a mark for each.
     *
     * @param caseMarks the tester's marks in order
     * @param cases the cases of the run
     * @param totalPrinted true if the run reached its end and printed a
     *        total
     * @return true if every case has its own mark
     */
    private boolean matchesCases(List<Double> caseMarks,
            List<TestCaseResult> cases, boolean totalPrinted) {
        if (totalPrinted) {
            return caseMarks.size() == cases.size();
        }
        for (TestCaseResult testCase : cases) {
            if (testCase.getTestNumber() > caseMarks.size()) {
                return false;
            }
        }
        return true;
    }

    /**
     * Puts the compiled files back before the tester is run again, so
     * what a stopped run changed cannot affect the next run.
     *
     * @param snapshot the snapshot of the folder, or null if there is none
     * @param question the question
     * @throws GradingException if the files cannot be put back
     */
    private void putBackFiles(FolderSnapshot snapshot, Question question)
            throws GradingException {
        if (snapshot == null) {
            return;
        }

        try {
            snapshot.restore();
        } catch (IOException exception) {
            throw new GradingException("The files of " + question.getId()
                    + " could not be put back: " + exception.getMessage(),
                    exception);
        }
    }

    /**
     * Puts back the compiled files of a question as they were after
     * compiling, and flags any that were changed. Code run for an earlier
     * question may have swapped them, and off macOS nothing stops it. Only
     * a flag: the real tester then runs and sets the mark.
     *
     * @param submission the submission
     * @param question the question
     * @param tester the tester of the question
     * @return the snapshot, or null if there is none
     * @throws GradingException if the files cannot be put back
     */
    private FolderSnapshot restoreFiles(Submission submission,
            Question question, TesterFile tester) throws GradingException {
        FolderSnapshot snapshot = tester.takeSnapshot(
                submission.getUsername());
        if (snapshot == null) {
            return null;
        }

        try {
            List<String> changed = snapshot.restore();
            if (!changed.isEmpty()) {
                String message = "Files in the " + question.getId()
                        + " folder changed after compiling, before its "
                        + "tester ran: "
                        + TextUtils.join(NAME_SEPARATOR, changed)
                        + ". Code run for another question may have changed "
                        + "them. The originals were put back.";
                submission.addAnomaly(new Anomaly(
                        ID_PREFIX + UUID.randomUUID(),
                        AnomalyType.HARD_CODED_ANSWER, Severity.WARNING,
                        submission.getUsername(), message,
                        FILES_CHANGED_ACTION));
            }
            return snapshot;
        } catch (IOException exception) {
            throw new GradingException("The files of " + question.getId()
                    + " could not be put back: " + exception.getMessage(),
                    exception);
        }
    }

    /**
     * Runs a tester. Failing to start java is a problem with the computer,
     * not with the student's work, so it stops this submission.
     *
     * @param folder the question folder
     * @param testerClassName the tester class
     * @param skippedCases the test cases to skip, counted from 1
     * @return the tester result
     * @throws GradingException if java cannot be run
     */
    private ProcessResult runTester(Path folder, String testerClassName,
            List<Integer> skippedCases) throws GradingException {
        try {
            return testerRunner.runTester(folder, testerClassName,
                    skippedCases);
        } catch (ProcessExecutionException exception) {
            throw new GradingException("The tester could not be run: "
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
