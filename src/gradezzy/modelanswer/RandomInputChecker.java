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

    // Splits the program's output into lines
    private static final String LINE_BREAK = "\\R";

    // The parts of one printed line: number, mark, text
    private static final int FIELD_COUNT = 3;
    private static final int NUMBER_FIELD = 0;
    private static final int MARK_FIELD = 1;
    private static final int TEXT_FIELD = 2;

    // Calls are numbered from 1 by InputDriver
    private static final int FIRST_CALL = 1;

    // Two decimal results this close, relative to their size, are the
    // same answer: adding the same numbers in another order can change
    // the last digits
    private static final double RELATIVE_MARGIN = 0.000001;
    private static final double SMALLEST_SCALE = 1.0;

    // The most differences shown in a flag, and the longest result shown
    private static final int MAX_EXAMPLES = 2;
    private static final int MAX_SHOWN_LENGTH = 40;
    private static final String CUT_MARK = "...";
    private static final String EXAMPLE_SEPARATOR = "; ";
    private static final String SENTENCE_SEPARATOR = " ";

    // How a result of these types is quoted when shown, and how a null
    // result prints
    private static final String STRING_TYPE = "String";
    private static final String CHAR_TYPE = "char";
    private static final String DOUBLE_TYPE = "double";
    private static final String BOXED_DOUBLE_TYPE = "Double";
    private static final String TEXT_QUOTE = "\"";
    private static final String CHAR_QUOTE = "'";
    private static final String NULL_TEXT = "null";

    // The package names the input program writes in full
    private static final String JAVA_UTIL = "java.util.";
    private static final String JAVA_LANG = "java.lang.";

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

    // The methods of each question, ready to check, by question id
    private final Map<String, List<MethodCheck>> prepared = new HashMap<>();

    /**
     * Creates a checker for one grading run, with new random inputs.
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
                notCalled.add(describeMethod(check.getMethod()));
            } else {
                compared = compared + countCompared(check, studentResults);
                different = different + collectDifferences(check,
                        studentResults, examples);
            }
        }

        List<String> sentences = new ArrayList<>();
        if (different > 0) {
            sentences.add(describeDifferences(question, different,
                    compared, examples));
        }
        if (!notCalled.isEmpty()) {
            sentences.add(describeNotCalled(question, notCalled));
        }
        if (sentences.isEmpty()) {
            return null;
        }
        return TextUtils.join(SENTENCE_SEPARATOR, sentences);
    }

    // Says how many of the compared inputs gave a different answer, with
    // the examples.
    private String describeDifferences(Question question, int different,
            int compared, List<String> examples) {
        return question.getId() + ": " + different + " of " + compared
                + " random inputs give a different answer from the model "
                + "answer, for example "
                + TextUtils.join(EXAMPLE_SEPARATOR, examples) + ".";
    }

    // Names the methods that could not be called in the student's folder.
    // The same program compiled with the model answer, so the folder
    // holds a changed method or a class named like one of Java's own,
    // which could be there to dodge this check.
    private String describeNotCalled(Question question,
            List<String> notCalled) {
        return question.getId() + ": "
                + TextUtils.join(EXAMPLE_SEPARATOR, notCalled)
                + " could not be called with random inputs here, "
                + "though it can in the model answer. Check that the "
                + "method is as the question asks.";
    }

    // Names a method with its parameter types, such as
    // countDigit(long, int).
    private String describeMethod(MethodSignature method) {
        return method.describeCall(method.getParameterTypes());
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
            deleteQuietly(workFolder);
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
        String starterSource = readStarter(question);
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

    // Reads the starter's copy of a question's file, or "" when there is
    // none.
    private String readStarter(Question question) throws IOException {
        if (starterDirectory == null) {
            return "";
        }
        Path starterFile = starterDirectory.resolve(question.getFolderName())
                .resolve(question.getFileName());
        if (!Files.isRegularFile(starterFile)) {
            return "";
        }
        return FileUtils.readText(starterFile);
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
            return readResults(run.getStandardOutput());
        } catch (IOException exception) {
            return new HashMap<>();
        } finally {
            deleteFileQuietly(sourceFile);
            deleteFileQuietly(classFile);
        }
    }

    // Reads the program's trusted lines into results by call number.
    // Lines the student's code printed carry TesterRunner's prefix and
    // are skipped.
    private Map<Integer, CallResult> readResults(String output) {
        Map<Integer, CallResult> results = new HashMap<>();
        for (String line : output.split(LINE_BREAK)) {
            boolean fromStudent = line.startsWith(
                    TesterRunner.STUDENT_LINE_PREFIX);
            String[] fields = line.split(InputDriver.FIELD_SEPARATOR,
                    FIELD_COUNT);
            if (!fromStudent && fields.length == FIELD_COUNT) {
                addResult(results, fields);
            }
        }
        return results;
    }

    // Adds the result one line holds, when the line is well formed.
    private void addResult(Map<Integer, CallResult> results,
            String[] fields) {
        String mark = fields[MARK_FIELD];
        boolean threw = mark.equals(InputDriver.THREW_MARK);
        boolean value = mark.equals(InputDriver.VALUE_MARK);
        if (!threw && !value) {
            return;
        }
        try {
            int number = Integer.parseInt(fields[NUMBER_FIELD]);
            results.put(number, new CallResult(threw, fields[TEXT_FIELD]));
        } catch (NumberFormatException exception) {
            // Not one of the program's lines
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
            boolean reached = studentResults.containsKey(number);
            boolean counted = expected != null && reached
                    && !expected.hasThrown();
            if (counted) {
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
        for (int index = 0; index < callCount; index++) {
            int number = index + FIRST_CALL;
            CallResult expected = check.getModelResults().get(number);
            CallResult actual = studentResults.get(number);
            boolean comparable = expected != null && actual != null
                    && !expected.hasThrown();
            boolean decimal = isDecimal(check.getMethod());
            if (comparable && !isSameAnswer(expected, actual, decimal)) {
                different++;
                addExample(examples, check, index, expected, actual);
            }
        }
        return different;
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
        return type.equals(DOUBLE_TYPE) || type.equals(BOXED_DOUBLE_TYPE);
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

    // Adds one difference to the examples, while there are fewer than
    // MAX_EXAMPLES, such as: compress("aab") returned "a2b" where the
    // model answer returns "a2b1".
    private void addExample(List<String> examples, MethodCheck check,
            int index, CallResult expected, CallResult actual) {
        if (examples.size() >= MAX_EXAMPLES) {
            return;
        }
        MethodSignature method = check.getMethod();
        List<String> arguments = check.getInputs().get(index);
        String call = makeReadable(method.describeCall(arguments));
        String got = "returned " + showResult(method, actual.getText());
        if (actual.hasThrown()) {
            got = "threw " + actual.getText();
        }
        examples.add(call + " " + got + " where the model answer returns "
                + showResult(method, expected.getText()));
    }

    // Removes the package names the input program writes in full, so a
    // call reads as the student would write it, such as
    // new ArrayList<Integer>(Arrays.asList(3, 1)).
    private String makeReadable(String call) {
        return call.replace(JAVA_UTIL, "").replace(JAVA_LANG, "");
    }

    // Writes a result as Java writes the value, so empty text shows as
    // "" rather than nothing: text in double quotes, a character in
    // single quotes, anything else as printed. Long results are cut.
    private String showResult(MethodSignature method, String text) {
        String shown = shorten(text);
        String type = method.getReturnType();
        if (text.equals(NULL_TEXT)) {
            return shown;
        } else if (type.equals(STRING_TYPE)) {
            return TEXT_QUOTE + shown + TEXT_QUOTE;
        } else if (type.equals(CHAR_TYPE)) {
            return CHAR_QUOTE + shown + CHAR_QUOTE;
        }
        return shown;
    }

    // Cuts a long result short for a flag.
    private String shorten(String text) {
        if (text.length() <= MAX_SHOWN_LENGTH) {
            return text;
        }
        return text.substring(0, MAX_SHOWN_LENGTH) + CUT_MARK;
    }

    // Deletes a folder this checker made. One that cannot be deleted
    // stays in paths.working.dir.
    private void deleteQuietly(Path folder) {
        try {
            FileUtils.deleteDirectory(folder);
        } catch (IOException exception) {
            System.err.println("Could not delete " + folder);
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
