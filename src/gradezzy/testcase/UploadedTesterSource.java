/*
 * File: UploadedTesterSource.java
 *
 * Purpose: Supplies tester files the instructor uploaded.
 *
 * Where it fits: The standard way of working: testers such as Q1aTester.java
 * are read from a folder.
 */
package gradezzy.testcase;

import java.nio.file.Path;
import java.util.ArrayList;
import java.util.List;

import gradezzy.model.Assessment;

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

    // The folder holding the uploaded testers
    private final Path testersDirectory;

    /**
     * Creates a new uploaded tester source.
     *
     * @param testersDirectory the folder holding the uploaded testers
     */
    public UploadedTesterSource(Path testersDirectory) {
        this.testersDirectory = testersDirectory;
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
        // TODO(Zach): implement: matching
        //     <questionId><TESTER_CLASS_SUFFIX>.java files to the questions of
        //     the assessment
        return new ArrayList<>();
    }
}
