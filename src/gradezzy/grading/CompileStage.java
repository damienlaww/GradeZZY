/*
 * File: CompileStage.java
 *
 * Purpose: Compiles the student's code and the testers, one question at a time.
 *
 * Where it fits: Sixth stage. A question that does not compile gets a
 * COMPILE_ERROR anomaly and the others still continue.
 */
package gradezzy.grading;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.List;
import java.util.UUID;

import gradezzy.exec.ProcessExecutionException;
import gradezzy.exec.ProcessResult;
import gradezzy.exec.SourceCompiler;
import gradezzy.model.Anomaly;
import gradezzy.model.AnomalyType;
import gradezzy.model.Assessment;
import gradezzy.model.Question;
import gradezzy.model.QuestionResult;
import gradezzy.model.Severity;
import gradezzy.model.Submission;
import gradezzy.testcase.QuestionFolderFinder;
import gradezzy.testcase.TesterFile;

/**
 * Compiles the student's code and the testers, one question at a time.
 *
 * <p>Tier: MUST. Owner: Zach.
 */
public class CompileStage implements GradingStage {

    /**
     * The name shown while this stage runs.
     */
    public static final String NAME = "Compiling";

    // Start of the id of each anomaly this stage reports
    private static final String ID_PREFIX = "compile-";

    // Extension of a Java source file
    private static final String JAVA_EXTENSION = ".java";

    // Exit code of a successful javac run
    private static final int SUCCESS_EXIT_CODE = 0;

    // Splits compiler messages into lines, whatever the line ending
    private static final String LINE_BREAK = "\\R";

    // Full stop at the end of a sentence
    private static final String FULL_STOP = ".";

    // What the instructor can do about a compile error
    private static final String COMPILE_ERROR_ACTION =
            "Read the compiler output on the Logs tab.";

    // The helper that runs javac
    private final SourceCompiler sourceCompiler;

    /**
     * Creates a new compile stage.
     *
     * @param sourceCompiler the helper that runs javac
     */
    public CompileStage(SourceCompiler sourceCompiler) {
        this.sourceCompiler = sourceCompiler;
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
     * Compiles the student's code and the testers, one question at a time.
     * A question that does not compile gets a COMPILE_ERROR anomaly and the
     * others are still compiled. A question with no tester, folder or file
     * is skipped: the validators already reported what is missing.
     *
     * @param submission the submission
     * @param context the context
     * @throws GradingException if javac cannot be run or runs too long
     */
    @Override
    public void process(Submission submission,
            GradingContext context) throws GradingException {
        Assessment assessment = context.getAssessment();

        for (Question question : assessment.getQuestions()) {
            TesterFile tester = TesterFile.findFor(context.getTesters(),
                    question.getId());
            Path folder = findFolder(submission, assessment, question);

            if (tester != null && folder != null
                    && Files.isRegularFile(
                            folder.resolve(question.getFileName()))) {
                submission.addQuestionResult(compileQuestion(submission,
                        question, tester, folder));
            }
        }
    }

    /**
     * Compiles one question and its tester.
     *
     * @param submission the submission
     * @param question the question
     * @param tester the tester of the question
     * @param folder the question folder
     * @return the result, marked compiled or not
     * @throws GradingException if javac cannot be run or runs too long
     */
    private QuestionResult compileQuestion(Submission submission,
            Question question, TesterFile tester, Path folder)
            throws GradingException {
        String testerSource = tester.getClassName() + JAVA_EXTENSION;
        List<String> sourceFiles = List.of(question.getFileName(),
                testerSource);
        ProcessResult compileResult = runCompiler(folder, sourceFiles);
        removeTesterSource(folder.resolve(testerSource));

        // A slow compiler says nothing about the student's code: the
        // computer may just be busy, so no mark is taken and the
        // submission is left for a retry
        if (compileResult.isTimedOut()) {
            throw new GradingException("javac did not finish "
                    + question.getId() + " in time, probably because the "
                    + "computer was busy. Nothing was marked wrong; retry "
                    + "this submission.");
        }
        boolean compiled = compileResult.getExitCode() == SUCCESS_EXIT_CODE;

        QuestionResult result = new QuestionResult(question);
        result.setCompiled(compiled);
        result.setOutput(compileResult.getStandardError());

        if (!compiled) {
            String message = question.getId() + " did not compile: "
                    + firstLine(compileResult.getStandardError())
                    + ". The other questions were still graded.";
            submission.addAnomaly(new Anomaly(ID_PREFIX + UUID.randomUUID(),
                    AnomalyType.COMPILE_ERROR, Severity.ERROR,
                    submission.getUsername(), message,
                    COMPILE_ERROR_ACTION));
        }
        return result;
    }

    /**
     * Runs javac. Failing to start it is a problem with the computer, not
     * with the student's work, so it stops this submission.
     *
     * @param folder the question folder
     * @param sourceFiles the files to compile
     * @return the compiler result
     * @throws GradingException if javac cannot be run
     */
    private ProcessResult runCompiler(Path folder, List<String> sourceFiles)
            throws GradingException {
        try {
            return sourceCompiler.compile(folder, sourceFiles);
        } catch (ProcessExecutionException exception) {
            throw new GradingException("The compiler could not be run: "
                    + exception.getMessage(), exception);
        }
    }

    /**
     * Deletes the copied tester source once it is compiled. Only the
     * compiled class is needed to run it, and student code could read the
     * source to find the expected answers.
     *
     * @param testerSource the tester source in the question folder
     * @throws GradingException if it cannot be deleted
     */
    private void removeTesterSource(Path testerSource)
            throws GradingException {
        try {
            Files.deleteIfExists(testerSource);
        } catch (IOException exception) {
            throw new GradingException("The tester source could not be "
                    + "removed: " + exception.getMessage(), exception);
        }
    }

    /**
     * Finds the question folder, turning a listing failure into a
     * GradingException.
     *
     * @param submission the submission
     * @param assessment the assessment
     * @param question the question
     * @return the folder, or null if it is missing
     * @throws GradingException if the working copy cannot be read
     */
    private Path findFolder(Submission submission, Assessment assessment,
            Question question) throws GradingException {
        try {
            return QuestionFolderFinder.find(submission, assessment,
                    question);
        } catch (IOException exception) {
            throw new GradingException("The working copy could not be read: "
                    + exception.getMessage(), exception);
        }
    }

    /**
     * Returns the first line of the compiler messages, such as
     * "Q1a.java:12: error: ';' expected".
     *
     * @param messages the compiler messages
     * @return the first line, or a note when there is no message
     */
    private String firstLine(String messages) {
        String trimmed = messages.trim();
        if (trimmed.isEmpty()) {
            return "the compiler stopped without a message";
        }
        String line = trimmed.split(LINE_BREAK)[0];
        // The message is put inside a sentence, so its own full stop goes
        if (line.endsWith(FULL_STOP)) {
            return line.substring(0, line.length() - FULL_STOP.length());
        }
        return line;
    }
}
