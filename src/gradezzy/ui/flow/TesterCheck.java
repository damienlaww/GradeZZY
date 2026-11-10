/*
 * File: TesterCheck.java
 *
 * Purpose: Checks the chosen tester folder on a background thread before
 * grading: questions with no tester, missing data files and testers that
 * do not compile with the chosen starter. The check compiles every
 * tester, so it takes seconds.
 *
 * Where it fits: SetUpScreen starts one when the tester or starter folder
 * changes and shows the problems on the tester row when it ends.
 *
 * Note on untaught Java: javafx.concurrent.Task runs the check on a
 * background thread, as FileInspection does for the zip files.
 */
package gradezzy.ui.flow;

import java.nio.file.Path;
import java.util.List;

import javafx.concurrent.Task;

import gradezzy.ui.backend.DashboardBackend;

/**
 * A background task that checks a tester folder.
 *
 * <p>Tier: SHOULD.
 */
public class TesterCheck extends Task<List<String>> {

    // The backend that checks the folder
    private final DashboardBackend backend;

    // The tester folder
    private final Path testersDirectory;

    // The starter folder, or null for the one in Settings
    private final Path starterDirectory;

    // Names both folders, so Set up can tell which check this is
    private final String folders;

    /**
     * Creates the check of a tester folder.
     *
     * @param backend the backend that checks the folder
     * @param testersDirectory the tester folder
     * @param starterDirectory the starter folder, or null for Settings
     * @param folders names both folders
     */
    public TesterCheck(DashboardBackend backend, Path testersDirectory,
            Path starterDirectory, String folders) {
        this.backend = backend;
        this.testersDirectory = testersDirectory;
        this.starterDirectory = starterDirectory;
        this.folders = folders;
    }

    /**
     * Returns the names of the tester and starter folders checked.
     *
     * @return the names
     */
    public String getFolders() {
        return folders;
    }

    /**
     * Checks the folder. JavaFX calls this on a background thread.
     *
     * @return one sentence per problem, empty when the folder is fine
     */
    @Override
    protected List<String> call() {
        return backend.checkTesters(testersDirectory, starterDirectory);
    }
}
