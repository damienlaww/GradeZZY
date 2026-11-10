/*
 * File: RandomInputChecker.java
 *
 * Purpose: Calls a student's methods with edge cases and random inputs
 * and compares each answer with the model answer's, so bugs the fixed
 * test cases miss, and hard-coded answers, show up with the exact input
 * that caused them.
 *
 * Where it fits: One is made per grading run by PipelineFactory, so a
 * run's inputs are new and the same for every student in it.
 * RandomInputStage asks it about each compiled question. It runs the
 * model answer once per question and keeps the results.
 */
package gradezzy.modelanswer;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Random;
import java.util.UUID;

import gradezzy.config.AppConfig;
import gradezzy.config.ConfigKeys;
import gradezzy.exec.ProcessExecutionException;
import gradezzy.exec.ProcessResult;
import gradezzy.exec.SourceCompiler;
import gradezzy.exec.TesterRunner;
import gradezzy.model.Question;
import gradezzy.util.FileUtils;
import gradezzy.util.TextUtils;

/**
 * Compares a student's methods with the model answer's on random inputs.
 *
 * <p>Only inputs the model answer handles count: when the model answer
 * throws an exception, the input is outside the question and is left
 * out. A student whose run is stopped at the time limit is compared on
 * the calls it finished.
 *
 * <p>Tier: COULD. Owner: Jing Yuan.
 */
public class RandomInputChecker {

    // The folder, inside paths.working.dir, that the model answers are
    // run in. Its name starts with a dot, so it is never taken for a
    // student.
    private static final String WORK_FOLDER = ".random-inputs";

    // The extension of a Java source file and of a compiled class
    private static final String JAVA_EXTENSION = ".java";
    private static final String CLASS_EXTENSION = ".class";

    // Exit code of javac when the files compiled
    private static final int COMPILED = 0;

    // Calls are numbered from 1 by InputDriver
    private static final int FIRST_CALL = 1;

    // Two decimal results this close, relative to their size, are the
    // same answer: adding the same numbers in another order can change
    // the last digits
    private static final double RELATIVE_MARGIN = 0.000001;
    private static final double SMALLEST_SCALE = 1.0;

    // Separates the sentences of a flag
    private static final String SENTENCE_SEPARATOR = " ";

    // The boxed type of a decimal result, besides JavaTypes.DOUBLE_TYPE
    private static final String BOXED_DOUBLE_TYPE = "Double";

    // The settings
    private final AppConfig config;

    // Compiles the input program
    private final SourceCompiler compiler;

    // Runs the input program with the sandbox and limits of a tester
    private final TesterRunner testerRunner;

    // The model answers and the starter folder
    private final Path modelAnswers;
    private final Path starterDirectory;

    // Where this run's copies of the model answers are made
    private final Path workFolder;

    // Reads the methods to call, makes inputs, writes the program
    private final SignatureReader signatureReader = new SignatureReader();
    private final InputMaker inputMaker;
    private final InputDriver inputDriver = new InputDriver();

    // Reads the program's output, and writes the flag's sentences
    private final CallResultReader resultReader = new CallResultReader();
    private final DifferenceMessage message = new DifferenceMessage();

    // The methods of each question, ready to check, by question id
    private final Map<String, List<MethodCheck>> prepared = new HashMap<>();

    /**
     * Creates a checker for one grading run, with new random inputs.
     *
     * <p>Tier: COULD.
     *
     * @param config the settings
     * @param compiler compiles the input program
     * @param testerRunner runs the input program like a tester
     * @param modelAnswers the model answers folder
     * @param starterDirectory the starter folder
     * @param seed where the random inputs start; a new one each run
     */
    public RandomInputChecker(AppConfig config, SourceCompiler compiler,
            TesterRunner testerRunner, Path modelAnswers,
            Path starterDirectory, long seed) {
        this.config = config;
        this.compiler = compiler;
        this.testerRunner = testerRunner;
        this.modelAnswers = modelAnswers;
        this.starterDirectory = starterDirectory;
        this.inputMaker = new InputMaker(new Random(seed));
        String runFolder = UUID.randomUUID().toString();
        this.workFolder = Paths.get(config.getString(ConfigKeys.WORKING_DIR))
                .resolve(WORK_FOLDER).resolve(runFolder);
    }

    /**
     * Calls the methods of one question in a student's compiled question
     * folder with the run's inputs and compares the answers with the
     * model answer's.
     *
     * <p>Tier: COULD.
     *
     * @param studentFolder the student's compiled question folder
     * @param question the question
     * @return sentences naming the differences with examples, and any
     *         method that could not be called in the student's folder,
     *         starting with the question id; or null when there is
     *         nothing to report or the question cannot be checked
     * @throws ProcessExecutionException if java could not be run, for
     *         example because grading was cancelled
     */
    public String check(Path studentFolder, Question question)
            throws ProcessExecutionException {
        List<MethodCheck> checks = prepare(question);
        int compared = 0;
        int different = 0;
        List<String> examples = new ArrayList<>();
        List<String> notCalled = new ArrayList<>();

        for (MethodCheck check : checks) {
            Map<Integer, CallResult> studentResults = runProgram(
                    studentFolder, check.getDriverSource());
            if (studentResults == null) {
                notCalled.add(message.describeMethod(check.getMethod()));
            } else {
                compared = compared + countCompared(check, studentResults);
                different = different + collectDifferences(check,
                        studentResults, examples);
            }
        }

        List<String> sentences = new ArrayList<>();
        if (different > 0) {
            sentences.add(message.describeDifferences(question, different,
                    compared, examples));
        }
        if (!notCalled.isEmpty()) {
            sentences.add(message.describeNotCalled(question, notCalled));
        }
        if (sentences.isEmpty()) {
            return null;
        }
        return TextUtils.join(SENTENCE_SEPARATOR, sentences);
    }

    /**
     * Makes the inputs of a question's methods and runs the model answer
     * on them, once per question for the whole run. Only one thread
     * prepares at a time, so the model answer is never run twice.
     *
     * @param question the question
     * @return the methods ready to check; empty when the question has no
     *         model answer or no method that can be called
     * @throws ProcessExecutionException if java could not be run
     */
    private synchronized List<MethodCheck> prepare(Question question)
            throws ProcessExecutionException {
        List<MethodCheck> checks = prepared.get(question.getId());
        if (checks != null) {
            return checks;
        }

        checks = new ArrayList<>();
        prepared.put(question.getId(), checks);
        Path modelFolder = workFolder.resolve(question.getId());
        try {
            prepareInFolder(question, modelFolder, checks);
        } catch (ProcessExecutionException exception) {
            // java could not run, perhaps only for now: the next student
            // tries again rather than every one being skipped
            prepared.remove(question.getId());
            throw exception;
        } catch (IOException exception) {
            System.err.println("Random inputs: the model answer of "
                    + question.getId() + " could not be copied: "
                    + exception.getMessage());
        } finally {
            // Only one question is prepared at a time, so the run's
            // folder holds nothing else and can go too
            ModelAnswerCopy.deleteQuietly(workFolder);
            deleteIfEmpty(workFolder.getParent());
        }
        return checks;
    }

    // Copies and compiles the model answer, then runs it on the inputs of
    // each method it can call.
    private void prepareInFolder(Question question, Path modelFolder,
            List<MethodCheck> checks)
            throws IOException, ProcessExecutionException {
        Path answerFile = ModelAnswerCopy.findAnswerFile(modelAnswers,
                question);
        if (answerFile == null) {
            return;
        }
        boolean compiled = copyAndCompile(question, modelFolder);
        if (!compiled) {
            return;
        }

        // Without the starter's file, the methods the question asks for
        // cannot be told from the model answer's own helpers, which the
        // students do not have, so the question is skipped
        String starterSource = ModelAnswerCopy.readStarter(
                starterDirectory, question, question.getFileName());
        if (starterSource.isEmpty()) {
            return;
        }
        String source = FileUtils.readText(answerFile);
        List<MethodSignature> methods = signatureReader.read(source,
                classNameOf(question), starterSource);
        addChecks(methods, modelFolder, checks);
    }

    // Copies a question's model answer into a folder and compiles it
    // there. Returns true when it compiled.
    private boolean copyAndCompile(Question question, Path modelFolder)
            throws IOException, ProcessExecutionException {
        ModelAnswerCopy.make(modelAnswers, starterDirectory, question,
                modelFolder);
        List<String> sourceNames = new ArrayList<>();
        sourceNames.add(question.getFileName());
        ProcessResult compiled = compiler.compile(modelFolder, sourceNames);
        return compiled.getExitCode() == COMPILED;
    }

    // Makes the inputs of each method and runs the model answer on them,
    // adding a check for each method the model answer could be called
    // with.
    private void addChecks(List<MethodSignature> methods, Path modelFolder,
            List<MethodCheck> checks) throws ProcessExecutionException {
        int count = config.getInt(ConfigKeys.RANDOM_INPUTS_COUNT);
        for (MethodSignature method : methods) {
            List<List<String>> inputs = inputMaker.make(method, count);
            String driver = inputDriver.write(method, inputs);
            Map<Integer, CallResult> modelResults = runProgram(modelFolder,
                    driver);
            if (modelResults != null) {
                checks.add(new MethodCheck(method, inputs, driver,
                        modelResults));
            }
        }
    }

    // Returns the class a question's file holds, such as Q1 for Q1.java.
    private String classNameOf(Question question) {
        String fileName = question.getFileName();
        int nameEnd = fileName.length() - JAVA_EXTENSION.length();
        return fileName.substring(0, nameEnd);
    }

    // Writes, compiles and runs the input program in a folder, then
    // removes it again. Returns the result of each call by number, or
    // null when the program does not compile there, such as when the
    // student changed the method's parameters. A file that cannot be
    // written gives no results rather than null, since it says nothing
    // about the student.
    private Map<Integer, CallResult> runProgram(Path folder, String source)
            throws ProcessExecutionException {
        Path sourceFile = folder.resolve(InputDriver.FILE_NAME);
        Path classFile = folder.resolve(InputDriver.CLASS_NAME
                + CLASS_EXTENSION);
        try {
            FileUtils.writeText(sourceFile, source);
            List<String> sourceNames = new ArrayList<>();
            sourceNames.add(InputDriver.FILE_NAME);
            ProcessResult compiled = compiler.compile(folder, sourceNames);
            if (compiled.getExitCode() != COMPILED) {
                return null;
            }
            ProcessResult run = testerRunner.runTester(folder,
                    InputDriver.CLASS_NAME);
            return resultReader.read(run.getStandardOutput());
        } catch (IOException exception) {
            return new HashMap<>();
        } finally {
            deleteFileQuietly(sourceFile);
            deleteFileQuietly(classFile);
        }
    }

    // Counts the calls both the model answer and the student finished,
    // where the model answer returned a value.
    private int countCompared(MethodCheck check,
            Map<Integer, CallResult> studentResults) {
        int compared = 0;
        int callCount = check.getInputs().size();
        for (int index = 0; index < callCount; index++) {
            int number = index + FIRST_CALL;
            CallResult expected = check.getModelResults().get(number);
            CallResult actual = studentResults.get(number);
            if (isComparable(expected, actual)) {
                compared++;
            }
        }
        return compared;
    }

    // Counts the calls whose answers differ, adding the first ones, in
    // call order, to the examples.
    private int collectDifferences(MethodCheck check,
            Map<Integer, CallResult> studentResults, List<String> examples) {
        int different = 0;
        int callCount = check.getInputs().size();
        boolean decimal = isDecimal(check.getMethod());
        for (int index = 0; index < callCount; index++) {
            int number = index + FIRST_CALL;
            CallResult expected = check.getModelResults().get(number);
            CallResult actual = studentResults.get(number);
            boolean differs = isComparable(expected, actual)
                    && !isSameAnswer(expected, actual, decimal);
            if (differs) {
                different++;
                message.addExample(examples, check, index, expected,
                        actual);
            }
        }
        return different;
    }

    // Says whether one call can be compared: both the model answer and
    // the student finished it, and the model answer returned a value.
    // A result that is missing is null, as the results never hold null.
    private boolean isComparable(CallResult expected, CallResult actual) {
        boolean bothFinished = expected != null && actual != null;
        return bothFinished && !expected.hasThrown();
    }

    // Says whether two answers are the same: equal text, or, for a
    // decimal result, numbers that differ only in their last digits.
    // Whole numbers and text must match exactly, so an answer that is
    // one out on a large number is still found.
    private boolean isSameAnswer(CallResult expected, CallResult actual,
            boolean decimal) {
        if (actual.hasThrown()) {
            return false;
        }
        String expectedText = expected.getText();
        String actualText = actual.getText();
        if (expectedText.equals(actualText)) {
            return true;
        }
        return decimal && areCloseNumbers(expectedText, actualText);
    }

    // Says whether a method returns a decimal number.
    private boolean isDecimal(MethodSignature method) {
        String type = method.getReturnType();
        boolean primitive = type.equals(JavaTypes.DOUBLE_TYPE);
        return primitive || type.equals(BOXED_DOUBLE_TYPE);
    }

    // Says whether two texts are decimal numbers close enough to be the
    // same answer. An infinite expected answer must be matched exactly,
    // since any margin around it would hold every number.
    private boolean areCloseNumbers(String expectedText, String actualText) {
        try {
            double expected = Double.parseDouble(expectedText);
            double actual = Double.parseDouble(actualText);
            if (Double.isInfinite(expected)) {
                return expected == actual;
            }

            double scale = Math.max(SMALLEST_SCALE, Math.abs(expected));
            double margin = scale * RELATIVE_MARGIN;
            double difference = expected - actual;
            return Math.abs(difference) <= margin;
        } catch (NumberFormatException exception) {
            return false;
        }
    }

    // Deletes a folder when nothing is left in it. A folder another run
    // is still using is not empty, and is left alone.
    private void deleteIfEmpty(Path folder) {
        try {
            Files.deleteIfExists(folder);
        } catch (IOException exception) {
            // Not empty: another run is using it
        }
    }

    // Deletes a file of the input program from a question folder.
    private void deleteFileQuietly(Path file) {
        try {
            Files.deleteIfExists(file);
        } catch (IOException exception) {
            System.err.println("Could not delete " + file);
        }
    }
}
