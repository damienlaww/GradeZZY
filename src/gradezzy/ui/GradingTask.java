/*
 * File: GradingTask.java
 *
 * Purpose: Runs a grading batch on a background thread so the window
 * stays responsive while submissions are graded.
 *
 * Where it fits: Created by the upload screen and watched by the progress
 * screen. All grading still happens in GradingService; this class only
 * moves the call off the JavaFX thread.
 *
 * Note on untaught Java: javafx.concurrent.Task and threads are not
 * covered in CS102. They are kept inside this one class.
 */
package gradezzy.ui;

import java.nio.file.Path;
import java.util.List;

import javafx.concurrent.Task;

import gradezzy.grading.ProgressListener;
import gradezzy.model.GradingRun;
import gradezzy.model.Section;
import gradezzy.service.GradingService;
import gradezzy.testcase.TestCaseSource;

/**
 * A background task that grades one batch.
 *
 * <p>Tier: MUST. Owner: Damien.
 */
public class GradingTask extends Task<GradingRun> {

    // The service that does the grading
    private final GradingService gradingService;

    // The zip files or folders the instructor chose
    private final List<Path> inputPaths;

    // The section the batch belongs to
    private final Section section;

    // Where the tester files come from
    private final TestCaseSource testCaseSource;

    // The object told about progress
    private final ProgressListener listener;

    /**
     * Creates a task for one batch.
     *
     * @param gradingService the service that does the grading
     * @param inputPaths the zip files or folders the instructor chose
     * @param section the section the batch belongs to
     * @param testCaseSource where the tester files come from
     * @param listener the object told about progress. It is called on the
     *        background thread, so it must use Platform.runLater before
     *        touching any control.
     */
    public GradingTask(GradingService gradingService, List<Path> inputPaths,
            Section section, TestCaseSource testCaseSource,
            ProgressListener listener) {
        this.gradingService = gradingService;
        this.inputPaths = inputPaths;
        this.section = section;
        this.testCaseSource = testCaseSource;
        this.listener = listener;
    }

    /**
     * Grades the batch. JavaFX calls this on a background thread.
     *
     * @return the finished run
     * @throws Exception if the batch cannot start
     */
    @Override
    protected GradingRun call() throws Exception {
        return gradingService.startGrading(inputPaths, section,
                testCaseSource, listener);
    }
}
