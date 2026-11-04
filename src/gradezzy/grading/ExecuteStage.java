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
import java.util.List;
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

    // Start of the id of each anomaly this stage reports
    private static final String ID_PREFIX = "execute-";

    // Exit code of a tester that finished normally
    private static final int SUCCESS_EXIT_CODE = 0;

    // What the instructor can do about a tester that ended with an error
    private static final String RUNTIME_ERROR_ACTION =
            "Open code and test output on Review to read the tester output.";

    // What the instructor can do about a total its cases do not support
    private static final String TOTAL_TOO_HIGH_ACTION =
            "Read the code on Review: it may change the tester's score.";

    // What the instructor can do about files changed before a tester ran
    private static final String FILES_CHANGED_ACTION = "Read the code on "
            + "Review. The mark comes from the original files.";

    // Separates the file names in a message
    private static final String NAME_SEPARATOR = ", ";

    // Stands for "no test case hung"
    private static final int NO_CASE = 0;

    // Allowed rounding gap when a printed total is compared with the cap
    private static final double SCORE_TOLERANCE = 0.000001;

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
        // is run again, so the cases after it are still marked. Each pass
        // skips one more case, so this ends after the last case.
        List<Integer> skippedCases = new ArrayList<>();
        ProcessResult run = runTester(folder, tester.getClassName(),
                skippedCases);
        ProcessResult firstRun = run;
        QuestionResult parsed = scoreParser.parse(question, run);
        int stoppedCase = findStoppedCase(run, parsed, skippedCases);
        while (stoppedCase != NO_CASE) {
            skippedCases.add(stoppedCase);
            putBackFiles(snapshot, question);
            run = runTester(folder, tester.getClassName(), skippedCases);
            parsed = scoreParser.parse(question, run);
            stoppedCase = findStoppedCase(run, parsed, skippedCases);
        }

        result.setScore(parsed.getScore());
        result.setTimedOut(firstRun.isTimedOut());
        result.setOutput(parsed.getOutput());
        for (TestCaseResult testCase : parsed.getTestCaseResults()) {
            result.addTestCaseResult(testCase);
        }

        // Student code can set the tester's score field by reflection,
        // so a printed total is never worth more than its Passed lines
        int caseCount = parsed.getTestCaseResults().size();
        boolean finished = !run.isTimedOut()
                && run.getExitCode() == SUCCESS_EXIT_CODE;
        if (finished && caseCount > 0) {
            double earned = marksEarned(tester, question, passCount(parsed),
                    caseCount);
            if (parsed.getScore() > earned + SCORE_TOLERANCE) {
                result.setScore(earned);
                addAnomaly(submission, AnomalyType.RUNTIME_ERROR, "The "
                        + question.getId() + " tester printed a total of "
                        + parsed.getScore() + ", but only "
                        + passCount(parsed) + " of " + caseCount
                        + " case(s) passed, so " + earned + " was given.",
                        TOTAL_TOO_HIGH_ACTION);
            }
        }

        String outcome = passCount(parsed) + " case(s) passed and their "
                + "marks are kept." + describeSkipped(skippedCases);
        if (firstRun.isTimedOut()) {
            long limitSeconds = context.getConfig().getLong(
                    ConfigKeys.RUN_TIMEOUT_SECONDS);
            addAnomaly(submission, AnomalyType.TIMEOUT, "The "
                    + question.getId() + " tester was stopped at the "
                    + limitSeconds + " s limit. " + outcome,
                    "Check the loops in " + question.getFileName()
                    + ", then retry " + question.getId() + ".");
        } else if (firstRun.getExitCode() == TesterRunner.EARLY_EXIT_CODE) {
            addAnomaly(submission, AnomalyType.RUNTIME_ERROR, "The "
                    + question.getId() + " tester did not reach its end: "
                    + "the program was ended early, for example by "
                    + "System.exit in " + question.getFileName() + ". "
                    + outcome, RUNTIME_ERROR_ACTION);
        } else if (firstRun.getExitCode() != SUCCESS_EXIT_CODE) {
            addAnomaly(submission, AnomalyType.RUNTIME_ERROR, "The "
                    + question.getId() + " tester ended with an error (exit "
                    + "code " + firstRun.getExitCode() + "). " + outcome,
                    RUNTIME_ERROR_ACTION);
        }
    }

    /**
     * Finds the test case that hung in a timed-out run: the last one
     * started, if it has no result and was not already skipped. A skip
     * that did not help, as with a tester whose header is outside its try
     * block, is not tried twice. A run ended by a crash or System.exit is
     * left as it is: the brief removes only cases that hang.
     *
     * @param run the finished run
     * @param parsed the parsed result of the run
     * @param skippedCases the cases already skipped
     * @return the number of the case, or NO_CASE
     */
    private int findStoppedCase(ProcessResult run, QuestionResult parsed,
            List<Integer> skippedCases) {
        List<TestCaseResult> cases = parsed.getTestCaseResults();
        if (!run.isTimedOut() || cases.isEmpty()) {
            return NO_CASE;
        }

        TestCaseResult lastCase = cases.get(cases.size() - 1);
        if (!TesterOutputScoreParser.NOT_FINISHED.equals(lastCase.getDetail())
                || skippedCases.contains(lastCase.getTestNumber())) {
            return NO_CASE;
        }
        return lastCase.getTestNumber();
    }

    /**
     * Says which cases were skipped, for the end of a message.
     *
     * @param skippedCases the cases skipped
     * @return a sentence with a space in front, or empty if none were
     */
    private String describeSkipped(List<Integer> skippedCases) {
        if (skippedCases.isEmpty()) {
            return "";
        }

        List<String> numbers = new ArrayList<>();
        for (Integer skippedCase : skippedCases) {
            numbers.add(String.valueOf(skippedCase));
        }
        return " Case(s) " + TextUtils.join(NAME_SEPARATOR, numbers)
                + " hung, so they got no marks and the tester was run "
                + "again for the cases after them.";
    }

    /**
     * Works out the most a number of passed cases can earn. The tester's
     * own marks are used, so a case worth 2 marks counts as 2.
     *
     * @param tester the tester of the question
     * @param question the question
     * @param passes the number of passed cases
     * @param caseCount the number of cases
     * @return the most those cases can earn
     */
    private double marksEarned(TesterFile tester, Question question,
            int passes, int caseCount) {
        try {
            return TesterMarks.readBestMarks(tester.getSourceFile(), passes);
        } catch (IOException exception) {
            // The tester cannot be read now, so every case counts the same
            return passes * question.getMaxScore() / caseCount;
        }
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
                submission.addAnomaly(new Anomaly(
                        ID_PREFIX + UUID.randomUUID(),
                        AnomalyType.HARD_CODED_ANSWER, Severity.WARNING,
                        submission.getUsername(), "Files in the "
                        + question.getId() + " folder changed after "
                        + "compiling, before its tester ran: "
                        + TextUtils.join(NAME_SEPARATOR, changed)
                        + ". Code run for another question may have changed "
                        + "them. The originals were put back.",
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
