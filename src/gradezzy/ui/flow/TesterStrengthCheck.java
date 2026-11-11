/*
 * File: TesterStrengthCheck.java
 *
 * Purpose: Checks how strong each tester is on a background thread: the
 * model answer is broken in small ways and the tester is run on every
 * broken copy, then the report is saved. It compiles and runs the tester
 * once per copy, so it takes a minute or two.
 *
 * Where it fits: SetUpScreen starts one when the tester, starter or model
 * answers folder changes, or when the instructor asks, and shows the
 * results on the tester row when it ends. TesterStrengthSheet lists the
 * broken copies each tester missed.
 *
 * Note on untaught Java: javafx.concurrent.Task runs the check on a
 * background thread, as TesterCheck does for the tester folder.
 */
package gradezzy.ui.flow;

import java.nio.file.Path;
import java.util.List;

import javafx.concurrent.Task;

import gradezzy.model.Section;
import gradezzy.modelanswer.QuestionStrength;
import gradezzy.ui.backend.DashboardBackend;

/**
 * A background task that checks how strong each tester is.
 *
 * <p>Tier: COULD.
 */
public class TesterStrengthCheck extends Task<List<QuestionStrength>> {

    // The backend that runs the check
    private final DashboardBackend backend;

    // The section the report is saved for
    private final Section section;

    // The tester folder
    private final Path testersDirectory;

    // The starter folder, or null for the one in Settings
    private final Path starterDirectory;

    // The model answers folder, or null for the one in Settings
    private final Path modelAnswersDirectory;

    // Names the folders checked, so Set up can tell which check this is
    private final String folders;

    // Where the report was saved, or why not, once the check has ended
    private String reportMessage = "";

    /**
     * Creates the check of the testers in a folder.
     *
     * @param backend the backend that runs the check
     * @param section the section the report is saved for
     * @param testersDirectory the tester folder
     * @param starterDirectory the starter folder, or null for Settings
     * @param modelAnswersDirectory the model answers folder, or null for
     *        Settings
     * @param folders names the folders checked
     */
    public TesterStrengthCheck(DashboardBackend backend, Section section,
            Path testersDirectory, Path starterDirectory,
            Path modelAnswersDirectory, String folders) {
        this.backend = backend;
        this.section = section;
        this.testersDirectory = testersDirectory;
        this.starterDirectory = starterDirectory;
        this.modelAnswersDirectory = modelAnswersDirectory;
        this.folders = folders;
    }

    /**
     * Returns the names of the folders checked.
     *
     * @return the names
     */
    public String getFolders() {
        return folders;
    }

    /**
     * Returns where the report was saved, or why it was not.
     *
     * @return one sentence, or "" before the check ends
     */
    public String getReportMessage() {
        return reportMessage;
    }

    /**
     * Runs the check and saves the report. JavaFX calls this on a
     * background thread.
     *
     * @return one result per question
     */
    @Override
    protected List<QuestionStrength> call() {
        List<QuestionStrength> results = backend.checkTesterStrength(
                section, testersDirectory, starterDirectory,
                modelAnswersDirectory);
        if (!results.isEmpty()) {
            reportMessage = backend.saveTesterStrengthReport(section,
                    results);
        }
        return results;
    }
}
