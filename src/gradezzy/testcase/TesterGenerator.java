/*
 * File: TesterGenerator.java
 *
 * Purpose: Writes the Java source of a tester from typed test cases.
 *
 * Where it fits: Makes typed test cases behave exactly like uploaded testers,
 * so the rest of the pipeline does not care where tests came from.
 */
package gradezzy.testcase;

import java.nio.file.Path;
import java.util.List;

import gradezzy.config.AppConfig;
import gradezzy.model.Question;

/**
 * Writes the Java source of a tester from typed test cases.
 *
 * <p>Tier: SHOULD. Owner: Zach.
 */
public class TesterGenerator {

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
     * @param question the question
     * @param testCases the test cases
     * @return the Java source of the tester class
     */
    public String generateSource(Question question, List<TestCase> testCases) {
        // TODO(Zach): implement: source generation in the same output format as
        //     the provided tester files
        return "";
    }

    /**
     * Writes the generated tester to a file.
     *
     * @param question the question
     * @param testCases the test cases
     * @param outputDirectory the output directory
     * @return the path of the written file
     * @throws TestCaseException if the operation fails
     */
    public Path writeTester(Question question, List<TestCase> testCases,
            Path outputDirectory) throws TestCaseException {
        // TODO(Zach): implement: writing generateSource to <className>.java
        return null;
    }
}
