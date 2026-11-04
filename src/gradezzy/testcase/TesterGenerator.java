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
import java.util.List;

import gradezzy.config.AppConfig;
import gradezzy.config.ConfigKeys;
import gradezzy.model.Question;

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

    // The file ending of a tester source
    private static final String SOURCE_EXTENSION = ".java";

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

        StringBuilder source = new StringBuilder();
        addLine(source, "import java.util.*;");
        addLine(source, "");
        addLine(source, "public class " + getClassName(question)
                + " extends " + question.getId() + " {");
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
        return source.toString();
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
     * @throws TestCaseException if the operation fails
     */
    public Path writeTester(Question question, List<TestCase> testCases,
            Path outputDirectory) throws TestCaseException {
        Path file = outputDirectory.resolve(
                getClassName(question) + SOURCE_EXTENSION);
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

    // Copies each data file the tester folder in the settings has
    private void copyDataFiles(Path outputDirectory) throws IOException {
        Path testersDirectory = Path.of(
                config.getString(ConfigKeys.TESTERS_DIR));
        for (String dataFileName : config.getList(
                ConfigKeys.TESTER_DATA_FILES)) {
            Path dataFile = testersDirectory.resolve(dataFileName);
            if (Files.isRegularFile(dataFile)) {
                Files.copy(dataFile, outputDirectory.resolve(dataFileName),
                        StandardCopyOption.REPLACE_EXISTING);
            }
        }
    }

    // Adds the block that runs one test case
    private void addCase(StringBuilder source, TestCase testCase,
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
    private void addLine(StringBuilder source, String text) {
        source.append(text);
        source.append("\n");
    }

    // Turns text into a Java string literal, so it can be printed as it is
    private String toStringLiteral(String text) {
        String escaped = text.replace("\\", "\\\\");
        escaped = escaped.replace("\"", "\\\"");
        escaped = escaped.replace("\n", " ");
        return "\"" + escaped + "\"";
    }
}
