/*
 * File: TesterCheck.java
 *
 * Purpose: Checks the chosen tester folder on a background thread before
 * grading: questions with no tester, missing data files and testers that
 * do not compile. The check compiles every tester, so it takes seconds.
 *
 * Where it fits: SetUpScreen starts one when the tester folder changes
 * and shows the problems on the tester row when it ends.
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
 */
public class TesterCheck extends Task<List<String>> {

    // The backend that checks the folder
    private final DashboardBackend backend;

    // The tester folder
    private final Path testersDirectory;

    /**
     * Creates the check of a tester folder.
     *
     * @param backend the backend that checks the folder
     * @param testersDirectory the tester folder
     */
    public TesterCheck(DashboardBackend backend, Path testersDirectory) {
        this.backend = backend;
        this.testersDirectory = testersDirectory;
    }

    /**
     * Returns the tester folder being checked.
     *
     * @return the folder
     */
    public Path getTestersDirectory() {
        return testersDirectory;
    }

    /**
     * Checks the folder. JavaFX calls this on a background thread.
     *
     * @return one sentence per problem, empty when the folder is fine
     */
    @Override
    protected List<String> call() {
        return backend.checkTesters(testersDirectory);
    }
}
