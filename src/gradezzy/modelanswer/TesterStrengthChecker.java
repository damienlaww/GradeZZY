/*
 * File: TesterStrengthChecker.java
 *
 * Purpose: Measures how strong each tester is by running it on broken
 * copies of the model answer (mutation testing). A broken copy that
 * still gets full marks shows something the tester never checks, which
 * would give wrong marks to a whole class.
 *
 * Where it fits: Called by service.TesterStrengthService before grading.
 * It compiles and runs with the same javac, sandbox and limits as
 * grading (SourceCompiler, TesterRunner), in a folder of its own under
 * paths.working.dir that is deleted afterwards.
 */
package gradezzy.modelanswer;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;
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
import gradezzy.grading.ScoreParser;
import gradezzy.grading.TesterOutputScoreParser;
import gradezzy.model.Assessment;
import gradezzy.model.Question;
import gradezzy.testcase.TestCaseException;
import gradezzy.testcase.TesterInstaller;
import gradezzy.util.FileUtils;

/**
 * Measures how strong each tester is with broken copies of the model
 * answer.
 *
 * <p>Tier: COULD. Owner: Jing Yuan.
 */
public class TesterStrengthChecker {

    // The folder, inside paths.working.dir, that the copies are made in.
    // Its name starts with a dot, so it is never taken for a student.
    private static final String WORK_FOLDER = ".tester-strength";

    // The folder, inside a question's folder, of the unbroken answer
    private static final String MODEL_FOLDER = "model";

    // The extension of a Java source file
    private static final String JAVA_EXTENSION = ".java";

    // Exit code of javac when the files compiled
    private static final int COMPILED = 0;

    // How far below the maximum a mark may be and still be full marks,
    // since marks such as 0.5 are added as doubles
    private static final double FULL_MARKS_MARGIN = 0.0001;

    // Splits compiler output into lines
    private static final String LINE_BREAK = "\\R";

    // The place of the first line of compiler output
    private static final int FIRST_LINE = 0;

    // Separates a question's folder from its file in a problem
    private static final String FOLDER_SEPARATOR = "/";

    // The settings
    private final AppConfig config;

    // Compiles each copy, as grading compiles a student's answer
    private final SourceCompiler compiler;

    // Runs the tester on each copy, as grading runs it
    private final TesterRunner testerRunner;

    // Reads the marks from the tester's output, as grading reads them
    private final ScoreParser scoreParser;

    // Copies the tester's data files next to it
    private final TesterInstaller testerInstaller;

    // Makes the broken copies
    private final MutantMaker mutantMaker = new MutantMaker();

    /**
     * Creates a checker.
     *
     * @param config the settings
     * @param processRunner the helper that runs javac and java
     */
    public TesterStrengthChecker(AppConfig config,
            ProcessRunner processRunner) {
        this.config = config;
        this.compiler = new SourceCompiler(config, processRunner);
        this.testerRunner = new TesterRunner(config, processRunner);
        this.scoreParser = new TesterOutputScoreParser(config);
        this.testerInstaller = new TesterInstaller(config);
    }

    /**
     * Checks the tester of every question of an assessment. A question
     * whose tester or model answer is missing, or whose tester does not
     * give the model answer full marks, gets a problem instead of copies.
     *
     * <p>Tier: COULD.
     *
     * @param assessment the assessment, with each maximum mark read from
     *        its tester
     * @param testersDirectory the folder holding the testers and their
     *        data files
     * @param starterDirectory the starter folder, or null for none
     * @param modelAnswers the model answers folder
     * @return one result per question, in the assessment's order
     */
    public List<QuestionStrength> check(Assessment assessment,
            Path testersDirectory, Path starterDirectory,
            Path modelAnswers) {
        Path workRoot = Paths.get(config.getString(ConfigKeys.WORKING_DIR))
                .resolve(WORK_FOLDER);
        List<QuestionStrength> results = new ArrayList<>();

        for (Question question : assessment.getQuestions()) {
            QuestionStrength result = new QuestionStrength(question.getId(),
                    question.getMaxScore());
            Path questionWork = workRoot.resolve(question.getId());
            checkQuestion(result, question, testersDirectory,
                    starterDirectory, modelAnswers, questionWork);
            results.add(result);
        }

        deleteQuietly(workRoot);
        return results;
    }

    // Checks one question's tester, recording a problem when it cannot.
    private void checkQuestion(QuestionStrength result, Question question,
            Path testersDirectory, Path starterDirectory, Path modelAnswers,
            Path questionWork) {
        String testerClassName = question.getId()
                + config.getString(ConfigKeys.TESTER_CLASS_SUFFIX);
        String testerFileName = testerClassName + JAVA_EXTENSION;
        Path testerFile = testersDirectory.resolve(testerFileName);
        if (!Files.isRegularFile(testerFile)) {
            result.setProblem("There is no " + testerFileName
                    + " in the tester folder.");
            return;
        }
        if (ModelAnswerCopy.findAnswerFile(modelAnswers, question) == null) {
            result.setProblem("The model answers have no "
                    + question.getFolderName() + FOLDER_SEPARATOR
                    + question.getFileName()
                    + ".");
            return;
        }

        try {
            Path modelFolder = questionWork.resolve(MODEL_FOLDER);
            prepareModel(question, testersDirectory, starterDirectory,
                    modelAnswers, testerFile, modelFolder);
            if (!checkModelAnswer(result, question, modelFolder,
                    testerClassName, testerFileName)) {
                return;
            }

            List<Mutant> mutants = makeMutants(question, starterDirectory,
                    modelAnswers);
            result.getMutants().addAll(mutants);
            MutantBatch batch = new MutantBatch(question, mutants,
                    modelFolder, questionWork, testerClassName,
                    testerFileName);
            runBatch(batch);
        } catch (IOException exception) {
            result.setProblem("A file could not be copied: "
                    + exception.getMessage());
        } catch (TestCaseException exception) {
            result.setProblem(exception.getMessage());
        } catch (ProcessExecutionException exception) {
            result.setProblem("javac or java could not be run: "
                    + exception.getMessage());
        }
    }

    // Makes the unbroken copy: starter files, model answer, tester and
    // the tester's data files.
    private void prepareModel(Question question, Path testersDirectory,
            Path starterDirectory, Path modelAnswers, Path testerFile,
            Path modelFolder) throws IOException, TestCaseException {
        ModelAnswerCopy.make(modelAnswers, starterDirectory, question,
                modelFolder);
        // Copied byte for byte, whatever encoding the tester was saved in
        Path testerCopy = modelFolder.resolve(testerFile.getFileName());
        Files.copy(testerFile, testerCopy,
                StandardCopyOption.REPLACE_EXISTING);
        testerInstaller.copyDataFiles(testersDirectory, modelFolder);
    }

    // Compiles and runs the tester on the model answer itself. Returns
    // true when it gets full marks, as it must before broken copies say
    // anything; otherwise records why not and returns false.
    private boolean checkModelAnswer(QuestionStrength result,
            Question question, Path modelFolder, String testerClassName,
            String testerFileName) throws ProcessExecutionException {
        List<String> sourceNames = listAnswerFiles(question, modelFolder);
        sourceNames.add(testerFileName);
        ProcessResult compiled = compiler.compile(modelFolder, sourceNames);
        if (compiled.getExitCode() != COMPILED) {
            result.setProblem("The model answer does not compile with its "
                    + "tester: " + firstLine(compiled) + ".");
            return false;
        }

        ProcessResult run = testerRunner.runTester(modelFolder,
                testerClassName);
        double score = scoreParser.parse(question, run).getScore();
        result.setModelScore(score);
        if (!isFullMarks(score, question)) {
            result.setProblem("The model answer itself gets " + score
                    + " of " + question.getMaxScore() + ", so check the "
                    + "tester and the model answer first.");
            return false;
        }
        return true;
    }

    // Makes the broken copies of every file the student writes for a
    // question, keeping at most tester.strength.max.copies of them.
    private List<Mutant> makeMutants(Question question,
            Path starterDirectory, Path modelAnswers) throws IOException {
        Path answerFolder = modelAnswers.resolve(question.getFolderName());
        List<Mutant> mutants = new ArrayList<>();

        for (String fileName : listAnswerFiles(question, answerFolder)) {
            String source = FileUtils.readText(answerFolder.resolve(
                    fileName));
            String starterSource = readStarter(starterDirectory, question,
                    fileName);
            mutants.addAll(mutantMaker.make(fileName, source,
                    starterSource));
        }

        int limit = config.getInt(ConfigKeys.TESTER_STRENGTH_MAX_COPIES);
        return mutantMaker.spreadOut(mutants, limit);
    }

    // Lists the files the student writes for a question that are in a
    // folder: the answer file, then any extra file such as GoldMember.java.
    private List<String> listAnswerFiles(Question question, Path folder) {
        List<String> names = new ArrayList<>();
        names.add(question.getFileName());
        for (String extraFile : question.getExtraFileNames()) {
            if (Files.isRegularFile(folder.resolve(extraFile))) {
                names.add(extraFile);
            }
        }
        return names;
    }

    // Reads the starter's copy of a file, or "" when there is none.
    private String readStarter(Path starterDirectory, Question question,
            String fileName) throws IOException {
        if (starterDirectory == null) {
            return "";
        }
        Path starterFile = starterDirectory.resolve(question.getFolderName())
                .resolve(fileName);
        if (!Files.isRegularFile(starterFile)) {
            return "";
        }
        return FileUtils.readText(starterFile);
    }

    // Runs every copy of a batch on exec.thread.count threads and waits
    // for them all.
    private void runBatch(MutantBatch batch) {
        int threadCount = Math.min(config.getInt(ConfigKeys.THREAD_COUNT),
                batch.getMutants().size());
        List<Thread> threads = new ArrayList<>();

        for (int count = 0; count < threadCount; count++) {
            Thread thread = new Thread(new MutantWorker(this, batch));
            thread.start();
            threads.add(thread);
        }

        for (Thread thread : threads) {
            try {
                thread.join();
            } catch (InterruptedException exception) {
                // Stopped from outside: keep the flag and stop waiting
                Thread.currentThread().interrupt();
                return;
            }
        }
    }

    /**
     * Runs one broken copy in a folder of its own: copies the compiled
     * model answer, writes the broken file, compiles it with the tester
     * and runs the tester with tester.strength.timeout.seconds. The
     * outcome is set on the copy; a copy that cannot be run at all is
     * left NOT_RUN and out of the counts. Called by MutantWorker.
     *
     * @param batch the question's batch
     * @param index the copy's index in the batch
     */
    void runMutant(MutantBatch batch, int index) {
        Mutant mutant = batch.getMutants().get(index);
        String folderName = String.valueOf(index);
        Path folder = batch.getWorkFolder().resolve(folderName);

        try {
            FileUtils.deleteDirectory(folder);
            FileUtils.copyDirectory(batch.getModelFolder(), folder);
            FileUtils.writeText(folder.resolve(mutant.getFileName()),
                    mutant.getSource());
            runInFolder(batch, mutant, folder);
        } catch (IOException exception) {
            mutant.setOutcome(MutantOutcome.NOT_RUN);
        } catch (ProcessExecutionException exception) {
            mutant.setOutcome(MutantOutcome.NOT_RUN);
        } finally {
            deleteQuietly(folder);
        }
    }

    // Compiles the broken file with the tester and runs the tester.
    private void runInFolder(MutantBatch batch, Mutant mutant, Path folder)
            throws ProcessExecutionException {
        List<String> sourceNames = new ArrayList<>();
        sourceNames.add(mutant.getFileName());
        sourceNames.add(batch.getTesterFileName());
        ProcessResult compiled = compiler.compile(folder, sourceNames);
        if (compiled.getExitCode() != COMPILED) {
            mutant.setOutcome(MutantOutcome.NOT_COMPILED);
            return;
        }

        long timeoutSeconds = config.getLong(
                ConfigKeys.TESTER_STRENGTH_TIMEOUT_SECONDS);
        ProcessResult run = testerRunner.runTester(folder,
                batch.getTesterClassName(), new ArrayList<Integer>(),
                timeoutSeconds);
        if (run.isTimedOut()) {
            mutant.setOutcome(MutantOutcome.TIMED_OUT);
            return;
        }

        double score = scoreParser.parse(batch.getQuestion(), run)
                .getScore();
        mutant.setScore(score);
        if (isFullMarks(score, batch.getQuestion())) {
            mutant.setOutcome(MutantOutcome.SURVIVED);
        } else {
            mutant.setOutcome(MutantOutcome.CAUGHT);
        }
    }

    // Says whether a mark is the question's maximum.
    private boolean isFullMarks(double score, Question question) {
        double lowestFull = question.getMaxScore() - FULL_MARKS_MARGIN;
        return score >= lowestFull;
    }

    // Returns the first line the compiler printed, for a problem.
    private String firstLine(ProcessResult compiled) {
        String output = compiled.getStandardError().trim();
        if (output.isEmpty()) {
            output = compiled.getStandardOutput().trim();
        }
        return output.split(LINE_BREAK)[FIRST_LINE];
    }

    // Deletes a folder the check made. One that cannot be deleted stays
    // in paths.working.dir, where the next check replaces it.
    private void deleteQuietly(Path folder) {
        try {
            FileUtils.deleteDirectory(folder);
        } catch (IOException exception) {
            System.err.println("Could not delete " + folder);
        }
    }
}
