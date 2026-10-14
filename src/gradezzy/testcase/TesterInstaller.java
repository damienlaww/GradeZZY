/*
 * File: TesterInstaller.java
 *
 * Purpose: Copies tester files and their data files into a working copy.
 *
 * Where it fits: Does automatically what the readme of the provided testers
 * asks the instructor to do by hand.
 */
package gradezzy.testcase;

import java.nio.file.Path;
import java.util.List;

import gradezzy.config.AppConfig;
import gradezzy.model.Assessment;
import gradezzy.model.Submission;

/**
 * Copies tester files and their data files into a working copy.
 *
 * <p>Tier: MUST. Owner: Zach.
 */
public class TesterInstaller {

    // The application settings
    private final AppConfig config;

    /**
     * Creates a new tester installer.
     *
     * @param config the application settings
     */
    public TesterInstaller(AppConfig config) {
        this.config = config;
    }

    /**
     * Copies each tester into the matching question folder.
     *
     * @param testers the testers
     * @param submission the submission
     * @param assessment the assessment
     * @throws TestCaseException if the operation fails
     */
    public void install(List<TesterFile> testers, Submission submission,
            Assessment assessment) throws TestCaseException {
        // TODO(Zach): implement: copying each tester into the folder of its
        //     question inside the working copy
    }

    /**
     * Copies the data files listed in config next to a tester.
     *
     * @param sourceDirectory the source directory
     * @param questionFolder the question folder
     * @throws TestCaseException if the operation fails
     */
    public void copyDataFiles(Path sourceDirectory,
            Path questionFolder) throws TestCaseException {
        // TODO(Zach): implement: copying the files named in TESTER_DATA_FILES
    }
}
