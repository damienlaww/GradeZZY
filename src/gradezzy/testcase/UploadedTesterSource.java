/*
 * File: UploadedTesterSource.java
 *
 * Purpose: Supplies tester files the instructor uploaded.
 *
 * Where it fits: The standard way of working: testers such as Q1aTester.java
 * are read from a folder.
 */
package gradezzy.testcase;

import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.List;

import gradezzy.config.AppConfig;
import gradezzy.config.ConfigKeys;
import gradezzy.model.Assessment;
import gradezzy.model.Question;

/**
 * Supplies tester files the instructor uploaded.
 *
 * <p>Tier: MUST. Owner: Zach.
 */
public class UploadedTesterSource implements TestCaseSource {

    /**
     * The name shown in the app.
     */
    public static final String NAME = "Uploaded tester files";

    // Extension of a tester source file
    private static final String JAVA_EXTENSION = ".java";

    // The folder holding the uploaded testers
    private final Path testersDirectory;

    // The application settings
    private final AppConfig config;

    /**
     * Creates a new uploaded tester source.
     *
     * @param testersDirectory the folder holding the uploaded testers
     * @param config the application settings
     */
    public UploadedTesterSource(Path testersDirectory, AppConfig config) {
        this.testersDirectory = testersDirectory;
        this.config = config;
    }

    /**
     * Returns the folder the testers are read from. GradingService keeps
     * it on the run, so Resume uses the same testers.
     *
     * @return the folder holding the uploaded testers
     */
    public Path getTestersDirectory() {
        return testersDirectory;
    }

    /**
     * Returns the name of this source.
     *
     * @return the name shown in the app
     */
    @Override
    public String getName() {
        return NAME;
    }

    /**
     * Finds the tester file of each question in the folder.
     *
     * @param assessment the assessment
     * @return one tester file per question that has tests
     * @throws TestCaseException if the operation fails
     */
    @Override
    public List<TesterFile> prepareTesters(
            Assessment assessment) throws TestCaseException {
        if (testersDirectory == null || !Files.isDirectory(testersDirectory)) {
            throw new TestCaseException(
                    "Tester folder not found: " + testersDirectory);
        }

        String suffix = config.getString(ConfigKeys.TESTER_CLASS_SUFFIX);
        List<TesterFile> testers = new ArrayList<>();

        // A question without a tester is skipped: it simply gets no score
        for (Question question : assessment.getQuestions()) {
            String className = question.getId() + suffix;
            String fileName = className + JAVA_EXTENSION;
            Path sourceFile = testersDirectory.resolve(fileName);

            if (Files.isRegularFile(sourceFile)) {
                testers.add(new TesterFile(question.getId(), className,
                        sourceFile));
            }
        }

        if (testers.isEmpty()) {
            throw new TestCaseException("No file named <question id>"
                    + suffix + JAVA_EXTENSION + " was found in "
                    + testersDirectory);
        }
        return testers;
    }
}
