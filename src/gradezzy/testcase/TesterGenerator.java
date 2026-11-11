/*
 * File: TesterGenerator.java
 *
 * Purpose: Writes the Java source of a tester from typed test cases.
 *
 * Where it fits: Makes typed test cases behave exactly like uploaded testers,
 * so the rest of the pipeline does not care where tests came from.
 */
package gradezzy.testcase;

import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.StandardCopyOption;
import java.util.ArrayList;
import java.util.List;

import gradezzy.config.AppConfig;
import gradezzy.config.ConfigKeys;
import gradezzy.model.Question;
import gradezzy.util.TextUtils;

/**
 * Writes the Java source of a tester from typed test cases.
 *
 * <p>The generated tester extends the student's class, has no package and
 * prints the same lines as the provided testers: a header per test case,
 * the expected and actual values, the pass or fail marker, and the total
 * score as a decimal on the last line. Each passed case is worth 1 mark,
 * so TesterMarks finds the maximum mark from the file.
 *
 * <p>Tier: SHOULD. Owner: Zach.
 */
public class TesterGenerator {

    /**
     * The marks one passed test case earns, like the provided testers.
     */
    public static final int MARKS_PER_CASE = 1;

    // The line printed between test cases, like the provided testers
    private static final String SEPARATOR =
            "-------------------------------------------------------";

    // The line ending of the generated source
    private static final String LINE_BREAK = "\n";

    // A carriage return, the first half of a Windows line ending
    private static final String CARRIAGE_RETURN = "\r";

    // The file ending of a tester source
    private static final String SOURCE_EXTENSION = ".java";

    // A backslash, and how it is written inside a Java string literal
    private static final String BACKSLASH = "\\";
    private static final String ESCAPED_BACKSLASH = "\\\\";

    // A double quote, and how it is written inside a Java string literal
    private static final String QUOTE = "\"";
    private static final String ESCAPED_QUOTE = "\\\"";

    // Stands in for a line break inside a string literal
    private static final String LINE_BREAK_STAND_IN = " ";

    // The application settings
    private final AppConfig config;

    /**
     * Creates a new tester generator.
     *
     * @param config the application settings
     */
    public TesterGenerator(AppConfig config) {
        this.config = config;
    }

    /**
     * Builds tester source code that prints the pass and fail markers and then
     * the score, like the provided testers.
     *
     * <p>Each case compares the expected expression with the result using
     * {@code Objects.deepEquals}, so the return type need not be known and
     * arrays are compared by content. Two numbers are compared by value,
     * so an expected {@code -1} matches a method that returns a double.
     *
     * @param question the question
     * @param testCases the test cases
     * @return the Java source of the tester class
     */
    public String generateSource(Question question, List<TestCase> testCases) {
        String passMarker = config.getString(ConfigKeys.PASS_MARKER);
        String failMarker = config.getString(ConfigKeys.FAIL_MARKER);
        String headerMarker = config.getString(ConfigKeys.TEST_HEADER_MARKER);

        List<String> source = new ArrayList<>();
        addLine(source, "import java.util.*;");
        addLine(source, "");
        // The student's class is named by its file (Alpha.java holds
        // Alpha), which is not always the question id
        String studentClass = question.getFileName();
        if (studentClass.endsWith(SOURCE_EXTENSION)) {
            int nameEnd = studentClass.length() - SOURCE_EXTENSION.length();
            studentClass = studentClass.substring(0, nameEnd);
        }
        addLine(source, "public class " + getClassName(question)
                + " extends " + studentClass + " {");
        addLine(source, "");
        addLine(source, "    private static double score;");
        addLine(source, "");
        addLine(source, "    public static void main(String[] args) {");
        addLine(source, "        int tcNum = 1;");

        for (TestCase testCase : testCases) {
            addCase(source, testCase, passMarker, failMarker, headerMarker);
        }

        addLine(source, "        System.out.println(score);");
        addLine(source, "    }");
        addLine(source, "");
        addLine(source, "    private static boolean sameValue(Object expected,"
                + " Object result) {");
        addLine(source, "        if (expected instanceof Number"
                + " && result instanceof Number) {");
        addLine(source, "            return ((Number) expected).doubleValue()"
                + " == ((Number) result).doubleValue();");
        addLine(source, "        }");
        addLine(source, "        return Objects.deepEquals(expected, result);");
        addLine(source, "    }");
        addLine(source, "}");
        addLine(source, "");
        return TextUtils.join(LINE_BREAK, source);
    }

    /**
     * Writes the generated tester to a file, with the data files from the
     * tester folder in the settings next to it, because the tester
     * installer copies data files from a tester's own folder.
     *
     * @param question the question
     * @param testCases the test cases
     * @param outputDirectory the output directory
     * @return the path of the written file
     * @throws TestCaseException if a case has a line break or the file
     *         cannot be written
     */
    public Path writeTester(Question question, List<TestCase> testCases,
            Path outputDirectory) throws TestCaseException {
        String fileName = getClassName(question) + SOURCE_EXTENSION;
        Path file = outputDirectory.resolve(fileName);
        checkOneLine(testCases);
        String source = generateSource(question, testCases);

        try {
            Files.createDirectories(outputDirectory);
            Files.write(file, source.getBytes(StandardCharsets.UTF_8));
            copyDataFiles(outputDirectory);
        } catch (IOException exception) {
            throw new TestCaseException("Could not write the tester "
                    + file + ".", exception);
        }

        return file;
    }

    /**
     * Returns the tester class name for a question, such as Q1aTester.
     *
     * @param question the question
     * @return the tester class name
     */
    public String getClassName(Question question) {
        return question.getId()
                + config.getString(ConfigKeys.TESTER_CLASS_SUFFIX);
    }

    // A line break typed inside a case would end a Java string literal
    // in the tester, which then cannot compile, so refuse the case. A
    // newline inside a string is typed as the two characters \n.
    private void checkOneLine(List<TestCase> testCases)
            throws TestCaseException {
        for (TestCase testCase : testCases) {
            String typed = testCase.getArgumentExpressions()
                    + testCase.getExpectedExpression();
            boolean hasLineBreak = typed.contains(CARRIAGE_RETURN)
                    || typed.contains(LINE_BREAK);
            if (hasLineBreak) {
                throw new TestCaseException("The typed case "
                        + testCase.getMethodName() + " for "
                        + testCase.getQuestionId() + " has a line break in "
                        + "its arguments or expected result. Type it on "
                        + "one line, writing \\n inside a string.");
            }
        }
    }

    // Copies each data file the tester folder in the settings has
    private void copyDataFiles(Path outputDirectory) throws IOException {
        String testersFolder = config.getString(ConfigKeys.TESTERS_DIR);
        Path testersDirectory = Path.of(testersFolder);
        List<String> dataFileNames =
                config.getList(ConfigKeys.TESTER_DATA_FILES);
        for (String dataFileName : dataFileNames) {
            Path dataFile = testersDirectory.resolve(dataFileName);
            if (Files.isRegularFile(dataFile)) {
                Path copy = outputDirectory.resolve(dataFileName);
                Files.copy(dataFile, copy,
                        StandardCopyOption.REPLACE_EXISTING);
            }
        }
    }

    // Adds the block that runs one test case
    private void addCase(List<String> source, TestCase testCase,
            String passMarker, String failMarker, String headerMarker) {
        String call = testCase.getMethodName() + "("
                + testCase.getArgumentExpressions() + ")";

        addLine(source, "        {");
        addLine(source, "            try {");
        addLine(source, "                System.out.println(\"" + headerMarker
                + " \" + tcNum++ + \": \" + "
                + toStringLiteral(call) + ");");
        addLine(source, "                Object expected = "
                + testCase.getExpectedExpression() + ";");
        addLine(source, "                Object result = " + call + ";");
        addLine(source, "                System.out.println(\"Expected  :|\""
                + " + expected + \"|\");");
        addLine(source, "                System.out.println(\"Actual    :|\""
                + " + result + \"|\");");
        addLine(source, "                if (sameValue(expected, result)) {");
        addLine(source, "                    score += " + MARKS_PER_CASE + ";");
        addLine(source, "                    System.out.println(\""
                + passMarker + "\");");
        addLine(source, "                } else {");
        addLine(source, "                    System.out.println(\""
                + failMarker + "\");");
        addLine(source, "                }");
        addLine(source, "            } catch (Exception e) {");
        addLine(source, "                System.out.println(\"" + failMarker
                + " -> Exception\");");
        addLine(source, "            }");
        addLine(source, "            System.out.println(\"" + SEPARATOR
                + "\");");
        addLine(source, "        }");
    }

    // Adds one line of generated source
    private void addLine(List<String> source, String text) {
        source.add(text);
    }

    // Turns text into a Java string literal, so it can be printed as it is
    private String toStringLiteral(String text) {
        String escaped = text.replace(BACKSLASH, ESCAPED_BACKSLASH);
        escaped = escaped.replace(QUOTE, ESCAPED_QUOTE);
        escaped = escaped.replace(CARRIAGE_RETURN, LINE_BREAK_STAND_IN);
        escaped = escaped.replace(LINE_BREAK, LINE_BREAK_STAND_IN);
        return QUOTE + escaped + QUOTE;
    }
}
