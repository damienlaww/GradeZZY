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
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.StandardCopyOption;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.UUID;

import gradezzy.config.AppConfig;
import gradezzy.config.ConfigKeys;
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
import gradezzy.testcase.QuestionWorkspace;
import gradezzy.testcase.TesterFile;
import gradezzy.util.FileUtils;

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

    // What the instructor can do about a tester that does not compile
    private static final String BROKEN_TESTER_ACTION =
            "Fix the tester file, then grade again.";

    // Extension of a compiled class file
    private static final String CLASS_EXTENSION = ".class";

    // Joins a class and its member type in a class file name, as Q1a$Node
    private static final String MEMBER_SEPARATOR = "$";

    // What the instructor can do about a type that hides a tester's type
    private static final String HIDDEN_TYPE_ACTION = "Read the code on Review: "
            + "the tester may have checked the student's class instead.";

    // Start of the name of the temporary folder for the tester check
    private static final String CHECK_FOLDER_PREFIX = "gradezzy-tester-check-";

    // Stands for "the tester compiled, or could not be checked"
    private static final String NO_TESTER_ERROR = "";

    // The helper that runs javac
    private final SourceCompiler sourceCompiler;

    // The starter folder chosen for the batch, or null to use
    // paths.starter.dir
    private final Path starterDirectory;

    // For each tester class, its compile error against the starter code,
    // or NO_TESTER_ERROR. One stage is made per run, so each tester is
    // checked once per run.
    private final HashMap<String, String> testerErrors = new HashMap<>();

    /**
     * Creates a new compile stage.
     *
     * @param sourceCompiler the helper that runs javac
     * @param starterDirectory the starter folder chosen for the batch,
     *        or null to use paths.starter.dir
     */
    public CompileStage(SourceCompiler sourceCompiler,
            Path starterDirectory) {
        this.sourceCompiler = sourceCompiler;
        this.starterDirectory = starterDirectory;
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
     * is skipped: the validators already reported what is missing. A
     * question whose tester does not compile even with the starter code
     * is not graded, and gets an INTERNAL_ERROR instead of a
     * COMPILE_ERROR, so the student is not blamed. Each question is
     * compiled in its own copy of its folder, so code run for Q1a cannot
     * change what Q1b's tester sees.
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
                String testerError = checkTester(context.getConfig(),
                        question, tester);
                if (testerError.isEmpty()) {
                    Path copy = prepareCopy(submission, assessment,
                            question);
                    submission.addQuestionResult(compileQuestion(
                            submission, question, tester, copy,
                            context.getTesters()));
                    removeTesterSource(folder.resolve(
                            tester.getClassName() + JAVA_EXTENSION));
                } else {
                    reportBrokenTester(submission, question, tester,
                            testerError);
                }
            }
        }
    }

    /**
     * Returns the compile error of a tester against the starter code,
     * checking each tester only once. The worker threads share this
     * stage, so only one of them checks a tester at a time.
     *
     * @param config the application settings
     * @param question the question
     * @param tester the tester of the question
     * @return the first error line, or NO_TESTER_ERROR
     * @throws GradingException if javac cannot be run
     */
    private synchronized String checkTester(AppConfig config,
            Question question, TesterFile tester) throws GradingException {
        String className = tester.getClassName();
        if (!testerErrors.containsKey(className)) {
            testerErrors.put(className,
                    compileWithStarter(config, question, tester));
        }
        return testerErrors.get(className);
    }

    /**
     * Compiles a tester with the starter code in a temporary copy. Every
     * provided tester compiles with the starter, so a tester that does
     * not is itself broken, and the students must not be blamed for it.
     *
     * @param config the application settings
     * @param question the question
     * @param tester the tester of the question
     * @return the first error line, or NO_TESTER_ERROR if it compiled or
     *         could not be checked
     * @throws GradingException if javac cannot be run
     */
    private String compileWithStarter(AppConfig config, Question question,
            TesterFile tester) throws GradingException {
        Path starterRoot = starterDirectory;
        if (starterRoot == null) {
            starterRoot = Path.of(config.getString(ConfigKeys.STARTER_DIR));
        }
        Path starterFolder = starterRoot.resolve(question.getFolderName());
        if (!Files.isRegularFile(
                starterFolder.resolve(question.getFileName()))) {
            return NO_TESTER_ERROR;
        }

        String testerSource = tester.getClassName() + JAVA_EXTENSION;
        try {
            // The real path, because copyDirectory refuses linked folders
            // and the macOS temp folder sits behind one
            Path checkFolder = Files.createTempDirectory(
                    CHECK_FOLDER_PREFIX).toRealPath();
            try {
                Path copy = checkFolder.resolve(question.getFolderName());
                FileUtils.copyDirectory(starterFolder, copy);
                Files.copy(tester.getSourceFile(),
                        copy.resolve(testerSource),
                        StandardCopyOption.REPLACE_EXISTING);
                ProcessResult result = runCompiler(copy,
                        sourceFiles(question, testerSource));
                if (result.getExitCode() == SUCCESS_EXIT_CODE
                        || result.isTimedOut()) {
                    return NO_TESTER_ERROR;
                }
                // An error in the starter file says nothing about the
                // tester, so the students are graded as before
                String errorLine = firstLine(result.getStandardError());
                if (!errorLine.startsWith(testerSource)) {
                    return NO_TESTER_ERROR;
                }
                return errorLine;
            } finally {
                FileUtils.deleteDirectory(checkFolder);
            }
        } catch (IOException exception) {
            // The check could not be done, so grade as before
            return NO_TESTER_ERROR;
        }
    }

    /**
     * Records that a question was not graded because its tester is broken.
     *
     * @param submission the submission
     * @param question the question
     * @param tester the tester of the question
     * @param testerError the first compile error of the tester
     */
    private void reportBrokenTester(Submission submission, Question question,
            TesterFile tester, String testerError) {
        String message = question.getId() + " was not graded: "
                + tester.getClassName() + JAVA_EXTENSION
                + " does not compile even with the starter code ("
                + testerError + "). This is not the student's error.";
        submission.addAnomaly(new Anomaly(ID_PREFIX + UUID.randomUUID(),
                AnomalyType.INTERNAL_ERROR, Severity.ERROR,
                submission.getUsername(), message, BROKEN_TESTER_ACTION));
    }

    /**
     * Compiles one question and its tester.
     *
     * @param submission the submission
     * @param question the question
     * @param tester the tester of the question
     * @param folder the copy of the question folder
     * @param testers every tester of the run
     * @return the result, marked compiled or not
     * @throws GradingException if javac cannot be run or runs too long
     */
    private QuestionResult compileQuestion(Submission submission,
            Question question, TesterFile tester, Path folder,
            List<TesterFile> testers) throws GradingException {
        String testerSource = tester.getClassName() + JAVA_EXTENSION;
        ProcessResult compileResult = runCompiler(folder,
                sourceFiles(question, testerSource));
        // The copy also holds the sources of testers that share the folder,
        // such as Q1bTester.java next to Q1aTester.java
        for (TesterFile anyTester : testers) {
            removeTesterSource(folder.resolve(
                    anyTester.getClassName() + JAVA_EXTENSION));
        }

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
        } else {
            flagHiddenTypes(submission, question, tester, folder);
        }
        return result;
    }

    /**
     * Flags each member type of the student class whose name the tester
     * also uses. The tester extends the student class, so a member type
     * named ArrayList replaces java.util.ArrayList inside the tester and
     * can make its checks always pass. Only a flag: the mark is kept.
     *
     * @param submission the submission
     * @param question the question
     * @param tester the tester of the question
     * @param folder the question folder, holding the compiled classes
     */
    private void flagHiddenTypes(Submission submission, Question question,
            TesterFile tester, Path folder) {
        String fileName = question.getFileName();
        String studentClass = fileName.substring(0,
                fileName.length() - JAVA_EXTENSION.length());
        String prefix = studentClass + MEMBER_SEPARATOR;
        String testerSource = "";
        List<Path> classFiles = new ArrayList<>();
        try {
            // ISO-8859-1 reads any bytes; the names searched are ASCII
            testerSource = Files.readString(tester.getSourceFile(),
                    StandardCharsets.ISO_8859_1);
            classFiles = FileUtils.listFiles(folder, CLASS_EXTENSION);
        } catch (IOException exception) {
            // The check could not be done; grading goes on as before
            return;
        }

        for (Path classFile : classFiles) {
            String name = classFile.getFileName().toString();
            if (name.startsWith(prefix)) {
                String memberName = name.substring(prefix.length(),
                        name.length() - CLASS_EXTENSION.length());
                if (isMemberTypeName(memberName)
                        && usesName(testerSource, memberName)) {
                    submission.addAnomaly(new Anomaly(ID_PREFIX
                            + UUID.randomUUID(),
                            AnomalyType.HARD_CODED_ANSWER, Severity.WARNING,
                            submission.getUsername(), fileName
                            + " declares its own type " + memberName
                            + ", a name " + tester.getClassName()
                            + " also uses. The tester extends "
                            + studentClass + ", so it may have used the "
                            + "student's " + memberName + " instead. The "
                            + "mark was not changed.", HIDDEN_TYPE_ACTION));
                }
            }
        }
    }

    /**
     * Says whether the part of a class file name after Q1a$ names a
     * member type. Anonymous and local classes start with a digit, as
     * Q1a$1, and types nested deeper hold another $; neither is seen by
     * the tester.
     *
     * @param memberName the part after the student class and $
     * @return true for a member type of the student class
     */
    private boolean isMemberTypeName(String memberName) {
        return !memberName.isEmpty()
                && !Character.isDigit(memberName.charAt(0))
                && !memberName.contains(MEMBER_SEPARATOR);
    }

    /**
     * Says whether the source holds the name as a whole word, so Node is
     * not found inside NodeList.
     *
     * @param source the source text
     * @param name the name to find
     * @return true if the name appears as a whole word
     */
    private boolean usesName(String source, String name) {
        int index = source.indexOf(name);
        while (index >= 0) {
            int end = index + name.length();
            boolean startsWord = index == 0
                    || !Character.isJavaIdentifierPart(
                            source.charAt(index - 1));
            boolean endsWord = end == source.length()
                    || !Character.isJavaIdentifierPart(source.charAt(end));
            if (startsWord && endsWord) {
                return true;
            }
            index = source.indexOf(name, index + 1);
        }
        return false;
    }

    /**
     * Lists the two files javac compiles for a question: the student's
     * file, then the tester.
     *
     * @param question the question
     * @param testerSource the file name of the tester source
     * @return the file names
     */
    private List<String> sourceFiles(Question question,
            String testerSource) {
        List<String> files = new ArrayList<>();
        files.add(question.getFileName());
        files.add(testerSource);
        return files;
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
     * Makes this question's own copy of its folder, turning a copy failure
     * into a GradingException.
     *
     * @param submission the submission
     * @param assessment the assessment
     * @param question the question
     * @return the copy
     * @throws GradingException if the folder cannot be copied
     */
    private Path prepareCopy(Submission submission, Assessment assessment,
            Question question) throws GradingException {
        try {
            return QuestionWorkspace.prepare(submission, assessment,
                    question);
        } catch (IOException exception) {
            throw new GradingException("The folder of " + question.getId()
                    + " could not be copied: " + exception.getMessage(),
                    exception);
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
