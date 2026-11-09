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
import gradezzy.testcase.FolderSnapshot;
import gradezzy.testcase.QuestionFolderFinder;
import gradezzy.testcase.QuestionWorkspace;
import gradezzy.testcase.TestCaseException;
import gradezzy.testcase.TesterFile;
import gradezzy.testcase.TesterInstaller;
import gradezzy.testcase.TesterMarks;
import gradezzy.testcase.UploadedTesterSource;
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

    /**
     * Start of the id of each anomaly this stage reports. Grading one
     * question again uses it to find the ones it replaces.
     */
    public static final String ID_PREFIX = "compile-";

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

    // Part of javac's error when a tester stores what a void method returns
    private static final String VOID_RESULT_ERROR = "void cannot be converted";

    // Added to that error, because typed test cases check a returned value
    private static final String VOID_RESULT_HINT = "; a typed test case can "
            + "only check a method that returns a value, so remove the case "
            + "for the void method";

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

    // Times javac is run on one question before a slow compile is given
    // up, because a busy computer can make one run miss the time limit
    private static final int COMPILE_ATTEMPTS = 2;

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
     * change what Q1b's tester sees. A snapshot of each compiled copy is
     * kept, so ExecuteStage can undo any change made to it later.
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
                    QuestionResult result = compileQuestion(submission,
                            question, tester, copy, context.getTesters(),
                            context.getConfig());
                    submission.addQuestionResult(result);
                    if (result.isCompiled()) {
                        saveSnapshot(context.getConfig(), submission,
                                tester, copy);
                    }
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
     * Lists what is wrong with a tester folder, so Set up can warn before
     * grading starts: questions with no tester, data files that are
     * missing, and testers that do not compile with the starter code.
     * Tier: SHOULD.
     *
     * @param config the application settings
     * @param assessment the assessment
     * @param testersDirectory the tester folder
     * @return one sentence per problem, empty when the folder is fine
     * @throws GradingException if javac cannot be run
     */
    public List<String> checkTesterFolder(AppConfig config,
            Assessment assessment, Path testersDirectory)
            throws GradingException {
        List<String> problems = new ArrayList<>();
        List<TesterFile> testers = new ArrayList<>();

        try {
            testers = new UploadedTesterSource(testersDirectory, config)
                    .prepareTesters(assessment);
        } catch (TestCaseException exception) {
            problems.add(exception.getMessage());
            return problems;
        }

        TesterInstaller installer = new TesterInstaller(config);
        for (Question question : assessment.getQuestions()) {
            TesterFile tester = TesterFile.findFor(testers,
                    question.getId());
            if (tester == null) {
                problems.add(question.getId()
                        + " has no tester, so it will get no marks.");
            } else {
                addDataFileProblems(installer, tester, problems);
                addCompileProblem(config, question, tester, problems);
            }
        }
        return problems;
    }

    // Adds a sentence for each data file the tester reads but its folder
    // does not have.
    private void addDataFileProblems(TesterInstaller installer,
            TesterFile tester, List<String> problems) {
        String testerName = tester.getClassName() + JAVA_EXTENSION;

        try {
            List<String> missing = installer.findMissingDataFiles(
                    tester.getSourceFile());
            for (String dataFileName : missing) {
                problems.add(testerName + " reads " + dataFileName
                        + ", which is not in the tester folder.");
            }
        } catch (IOException exception) {
            problems.add(testerName + " could not be read: "
                    + exception.getMessage());
        }
    }

    // Adds a sentence when the tester does not compile with the starter.
    private void addCompileProblem(AppConfig config, Question question,
            TesterFile tester, List<String> problems)
            throws GradingException {
        String testerError = compileWithStarter(config, question, tester);

        if (!testerError.isEmpty()) {
            problems.add(tester.getClassName() + JAVA_EXTENSION
                    + " does not compile with the starter code ("
                    + testerError + ").");
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
        Path starterFolder = findStarterFolder(config, question);
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
                if (errorLine.contains(VOID_RESULT_ERROR)) {
                    return errorLine + VOID_RESULT_HINT;
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
     * @param config the application settings
     * @return the result, marked compiled or not
     * @throws GradingException if javac cannot be run or is too slow twice
     */
    private QuestionResult compileQuestion(Submission submission,
            Question question, TesterFile tester, Path folder,
            List<TesterFile> testers, AppConfig config)
            throws GradingException {
        String testerSource = tester.getClassName() + JAVA_EXTENSION;
        ProcessResult compileResult = runCompiler(folder,
                sourceFiles(question, testerSource));
        // A slow compiler says nothing about the student's code: the
        // computer may just be busy, so javac is run once more
        int attempts = 1;
        while (compileResult.isTimedOut() && attempts < COMPILE_ATTEMPTS) {
            compileResult = runCompiler(folder,
                    sourceFiles(question, testerSource));
            attempts++;
        }
        // The copy also holds the sources of testers that share the folder,
        // such as Q1bTester.java next to Q1aTester.java
        for (TesterFile anyTester : testers) {
            removeTesterSource(folder.resolve(
                    anyTester.getClassName() + JAVA_EXTENSION));
        }

        // Still too slow: no mark is taken and the submission is left
        // for a retry
        if (compileResult.isTimedOut()) {
            throw new GradingException("javac did not finish "
                    + question.getId() + " in time, even when run again, "
                    + "probably because the computer was busy. Nothing was "
                    + "marked wrong; retry this submission.");
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
            flagHiddenTypes(submission, question, tester, folder, config);
        }
        return result;
    }

    /**
     * Flags each type of the student's whose name the tester also uses.
     * The tester extends the student class and shares its folder, so a
     * member type or a class named ArrayList replaces java.util.ArrayList
     * inside the tester and can make its checks always pass. Only a flag:
     * the mark is kept.
     *
     * @param submission the submission
     * @param question the question
     * @param tester the tester of the question
     * @param folder the question folder, holding the compiled classes
     * @param config the application settings
     */
    private void flagHiddenTypes(Submission submission, Question question,
            TesterFile tester, Path folder, AppConfig config) {
        String fileName = question.getFileName();
        String studentClass = fileName.substring(0,
                fileName.length() - JAVA_EXTENSION.length());
        String prefix = studentClass + MEMBER_SEPARATOR;
        String testerSource = "";
        List<Path> classFiles = new ArrayList<>();
        try {
            // Without comments and quoted text, so the word Test in a
            // printed header does not match a student's class named Test
            testerSource = TesterMarks.readCode(tester.getSourceFile());
            classFiles = FileUtils.listFiles(folder, CLASS_EXTENSION);
        } catch (IOException exception) {
            // The check could not be done; grading goes on as before
            return;
        }

        for (Path classFile : classFiles) {
            String name = classFile.getFileName().toString();
            String typeName = name.substring(0,
                    name.length() - CLASS_EXTENSION.length());
            if (name.startsWith(prefix)) {
                typeName = typeName.substring(prefix.length());
                if (isMemberTypeName(typeName)
                        && usesName(testerSource, typeName)) {
                    flagHiddenType(submission, fileName, typeName, tester,
                            "extends " + studentClass);
                }
            } else if (isExtraType(config, question, tester, studentClass,
                    typeName) && usesName(testerSource, typeName)) {
                flagHiddenType(submission, question.getFolderName()
                        + " folder", typeName, tester, "shares its folder");
            }
        }
    }

    /**
     * Records that a type of the student's may have replaced one in the
     * tester.
     *
     * @param submission the submission
     * @param place what declares the type
     * @param typeName the name of the type
     * @param tester the tester of the question
     * @param link how the tester comes to see the type
     */
    private void flagHiddenType(Submission submission, String place,
            String typeName, TesterFile tester, String link) {
        submission.addAnomaly(new Anomaly(ID_PREFIX + UUID.randomUUID(),
                AnomalyType.HARD_CODED_ANSWER, Severity.WARNING,
                submission.getUsername(), "The " + place
                + " declares its own type " + typeName + ", a name "
                + tester.getClassName() + " also uses. The tester " + link
                + ", so it may have used the student's " + typeName
                + " instead. The mark was not changed.", HIDDEN_TYPE_ACTION));
    }

    /**
     * Says whether a top-level class in a question folder came from the
     * student and is not part of the question: not the student class,
     * not the tester, not a provided class and not in the starter folder,
     * so ShapeComparator, which the student must write, is left alone.
     *
     * @param config the application settings
     * @param question the question
     * @param tester the tester of the question
     * @param studentClass the name of the student class
     * @param typeName the name of the class
     * @return true for a class the question did not ask for
     */
    private boolean isExtraType(AppConfig config, Question question,
            TesterFile tester, String studentClass, String typeName) {
        Path starterFolder = findStarterFolder(config, question);
        String classFile = typeName + CLASS_EXTENSION;
        return !typeName.contains(MEMBER_SEPARATOR)
                && !typeName.equals(studentClass)
                && !typeName.equals(tester.getClassName())
                && !config.getList(ConfigKeys.PROVIDED_CLASS_FILES)
                        .contains(classFile)
                && !Files.exists(starterFolder.resolve(classFile))
                && !Files.exists(starterFolder.resolve(
                        typeName + JAVA_EXTENSION));
    }

    /**
     * Returns the starter folder of a question.
     *
     * @param config the application settings
     * @param question the question
     * @return the folder, which may not exist
     */
    private Path findStarterFolder(AppConfig config, Question question) {
        Path starterRoot = starterDirectory;
        if (starterRoot == null) {
            starterRoot = Path.of(config.getString(ConfigKeys.STARTER_DIR));
        }
        return starterRoot.resolve(question.getFolderName());
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
     * Remembers the compiled files of a question's copy for ExecuteStage.
     *
     * @param config the application settings
     * @param submission the submission
     * @param tester the tester of the question
     * @param copy the copy of the question folder
     * @throws GradingException if the copy cannot be read
     */
    private void saveSnapshot(AppConfig config, Submission submission,
            TesterFile tester, Path copy) throws GradingException {
        try {
            tester.saveSnapshot(submission.getUsername(), new FolderSnapshot(
                    copy, config.getList(ConfigKeys.TESTER_DATA_FILES)));
        } catch (IOException exception) {
            throw new GradingException("The compiled files could not be "
                    + "read: " + exception.getMessage(), exception);
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
