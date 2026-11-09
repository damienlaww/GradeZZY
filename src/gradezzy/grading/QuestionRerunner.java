/*
 * File: QuestionRerunner.java
 *
 * Purpose: Compiles and runs one question's tester again for one student,
 * so the instructor can see fresh output, without changing any saved
 * mark.
 *
 * Where it fits: The code and test output sheet calls it through the
 * backend. It uses the same pieces as grading (a fresh copy of the
 * question folder, the tester and data files from the run's tester
 * folder, SourceCompiler, the sandboxed TesterRunner and
 * TesterOutputScoreParser), so the output is what grading would print.
 */
package gradezzy.grading;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.LinkOption;
import java.nio.file.Path;
import java.nio.file.StandardCopyOption;
import java.util.ArrayList;
import java.util.List;

import gradezzy.config.AppConfig;
import gradezzy.config.ConfigKeys;
import gradezzy.exec.ProcessExecutionException;
import gradezzy.exec.ProcessResult;
import gradezzy.exec.ProcessRunner;
import gradezzy.exec.SourceCompiler;
import gradezzy.exec.TesterRunner;
import gradezzy.model.Assessment;
import gradezzy.model.Question;
import gradezzy.model.QuestionResult;
import gradezzy.model.Submission;
import gradezzy.testcase.QuestionFolderFinder;
import gradezzy.testcase.QuestionWorkspace;
import gradezzy.testcase.TestCaseException;
import gradezzy.testcase.TesterInstaller;

/**
 * Runs one question's tester again for one student. The result it returns
 * is never saved: the student's marks stay as they are.
 *
 * <p>Tier: SHOULD. Owner: Damien.
 */
public class QuestionRerunner {

    // The ending of a Java source file
    private static final String JAVA_EXTENSION = ".java";

    // The exit code of a compiler that succeeded
    private static final int SUCCESS_EXIT_CODE = 0;

    // The application settings
    private final AppConfig config;

    // Compiles the answer with its tester
    private final SourceCompiler compiler;

    // Runs the tester in the sandbox, with the time limit
    private final TesterRunner runner;

    // Reads the tester's output into tests and a score
    private final TesterOutputScoreParser parser;

    /**
     * Creates the rerunner.
     *
     * @param config the application settings
     */
    public QuestionRerunner(AppConfig config) {
        this.config = config;
        ProcessRunner processes = new ProcessRunner(config);
        this.compiler = new SourceCompiler(config, processes);
        this.runner = new TesterRunner(config, processes);
        this.parser = new TesterOutputScoreParser(config);
    }

    /**
     * Compiles and runs one question's tester again in a fresh copy of the
     * student's question folder, with the tester and its data files from
     * the tester folder the student was graded with. A question that does
     * not compile gives a result marked not compiled whose output is the
     * compiler's messages.
     *
     * @param submission the student, with a working copy
     * @param assessment the assessment the student was graded with
     * @param question the question to run again
     * @param testersDirectory the tester folder the student was graded
     *        with
     * @return the fresh result, with its tests and output; never saved
     * @throws GradingException if there is nothing to run, or javac or
     *         java cannot be started; the message says why in plain words
     */
    public QuestionResult run(Submission submission, Assessment assessment,
            Question question, Path testersDirectory)
            throws GradingException {
        String testerClass = question.getId()
                + config.getString(ConfigKeys.TESTER_CLASS_SUFFIX);
        Path folder = prepareCopy(submission, assessment, question);
        installTester(folder, testersDirectory, testerClass);

        List<String> sources = new ArrayList<>();
        sources.add(question.getFileName());
        sources.add(testerClass + JAVA_EXTENSION);
        try {
            ProcessResult compiled = compiler.compile(folder, sources);
            QuestionResult result = new QuestionResult(question);
            if (compiled.isTimedOut()) {
                throw new GradingException("javac did not finish in time, "
                        + "probably because the computer is busy. Try "
                        + "again in a moment.");
            }
            if (compiled.getExitCode() != SUCCESS_EXIT_CODE) {
                result.setCompiled(false);
                result.setOutput(compiled.getStandardError());
                return result;
            }
            QuestionResult parsed = parser.parse(question,
                    runner.runTester(folder, testerClass));
            parsed.setCompiled(true);
            return parsed;
        } catch (ProcessExecutionException exception) {
            throw new GradingException("Java could not be started: "
                    + exception.getMessage(), exception);
        }
    }

    // Makes a fresh copy of the question folder, as grading does, and
    // checks that it holds the answer and the tester.
    private Path prepareCopy(Submission submission, Assessment assessment,
            Question question) throws GradingException {
        Path workingCopy = submission.getWorkingCopyPath();
        boolean noWorkingCopy = workingCopy == null
                || !Files.isDirectory(workingCopy, LinkOption.NOFOLLOW_LINKS);
        if (noWorkingCopy) {
            throw new GradingException("This student has no working copy, "
                    + "so there is nothing to run. Grade the student again "
                    + "first.");
        }
        Path folder = null;
        try {
            Path questionFolder = QuestionFolderFinder.find(submission,
                    assessment, question);
            if (questionFolder == null) {
                throw new GradingException("The " + question.getFolderName()
                        + " folder is missing from this student's files.");
            }
            folder = QuestionWorkspace.prepare(submission, assessment,
                    question);
        } catch (IOException exception) {
            throw new GradingException("The question folder could not be "
                    + "copied: " + exception.getMessage(), exception);
        }
        Path answer = folder.resolve(question.getFileName());
        if (!Files.isRegularFile(answer, LinkOption.NOFOLLOW_LINKS)) {
            throw new GradingException(question.getFileName() + " is "
                    + "missing from this student's " + question.getFolderName()
                    + " folder.");
        }
        return folder;
    }

    // Copies the tester and its data files into the copy, replacing any
    // the student sent, as grading does.
    private void installTester(Path folder, Path testersDirectory,
            String testerClass) throws GradingException {
        if (testersDirectory == null) {
            throw new GradingException("The tester folder of this run is "
                    + "not known, so the tests cannot run.");
        }
        String testerFileName = testerClass + JAVA_EXTENSION;
        Path tester = testersDirectory.resolve(testerFileName);
        if (!Files.isRegularFile(tester, LinkOption.NOFOLLOW_LINKS)) {
            throw new GradingException(testerFileName + " is not in the "
                    + "tester folder " + testersDirectory + ", so the tests "
                    + "cannot run.");
        }
        try {
            Path installed = folder.resolve(testerFileName);
            Files.copy(tester, installed,
                    StandardCopyOption.REPLACE_EXISTING);
            new TesterInstaller(config).copyDataFiles(testersDirectory,
                    folder);
        } catch (IOException exception) {
            throw new GradingException("The tester could not be copied: "
                    + exception.getMessage(), exception);
        } catch (TestCaseException exception) {
            throw new GradingException(exception.getMessage(), exception);
        }
    }
}
