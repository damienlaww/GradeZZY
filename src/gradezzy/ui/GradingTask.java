/*
 * File: GradingTask.java
 *
 * Purpose: Runs a grading batch on a background thread so the window
 * stays responsive while submissions are graded.
 *
 * Where it fits: Created by LiveBackend for a new batch, a retry of the
 * failed submissions or a resumed run, and watched by the progress
 * screen through GradingMonitor. All grading still happens in
 * GradingService; this class only moves the call off the JavaFX thread.
 *
 * Note on untaught Java: javafx.concurrent.Task and threads are not
 * covered in CS102. They are kept inside this one class.
 *
 * AI use: retry and resume kinds drafted with Claude Code, reviewed by
 * Jun Sheng.
 */
package gradezzy.ui;

import java.nio.file.Path;
import java.util.ArrayList;
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

    /**
     * What the task asks GradingService to do.
     */
    public enum Kind {

        /** Grade a new batch. */
        START,

        /** Grade the failed and cancelled submissions of a run again. */
        RETRY_FAILED,

        /** Continue a saved run. */
        RESUME
    }

    // The service that does the grading
    private final GradingService gradingService;

    // What to ask the service to do
    private final Kind kind;

    // The zip files or folders the instructor chose
    private final List<Path> inputPaths;

    // The roster explicitly selected for this batch, or null for config.
    private Path scoreSheetFile;

    // The section the batch belongs to
    private final Section section;

    // Where the tester files come from
    private final TestCaseSource testCaseSource;

    // The run to retry or resume, or null for a new batch
    private final GradingRun gradingRun;

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
        this.kind = Kind.START;
        this.inputPaths = inputPaths;
        this.section = section;
        this.testCaseSource = testCaseSource;
        this.gradingRun = null;
        this.listener = listener;
    }

    /** Creates a task with the roster selected on the upload screen. */
    public GradingTask(GradingService gradingService, List<Path> inputPaths,
            Section section, TestCaseSource testCaseSource,
            ProgressListener listener, Path scoreSheetFile) {
        this(gradingService, inputPaths, section, testCaseSource, listener);
        this.scoreSheetFile = scoreSheetFile;
    }

    /**
     * Creates a task that retries or resumes an existing run.
     *
     * @param gradingService the service that does the grading
     * @param kind RETRY_FAILED or RESUME
     * @param gradingRun the run to retry or resume
     * @param listener the object told about progress, on the background
     *        thread
     */
    public GradingTask(GradingService gradingService, Kind kind,
            GradingRun gradingRun, ProgressListener listener) {
        this.gradingService = gradingService;
        this.kind = kind;
        this.inputPaths = new ArrayList<>();
        this.section = gradingRun.getSection();
        this.testCaseSource = null;
        this.gradingRun = gradingRun;
        this.listener = listener;
    }

    /**
     * Grades the batch. JavaFX calls this on a background thread.
     *
     * @return the finished run, or null when the service returned none
     * @throws Exception if the batch cannot start
     */
    @Override
    protected GradingRun call() throws Exception {
        if (kind == Kind.RETRY_FAILED) {
            return gradingService.retryFailed(gradingRun, listener);
        } else if (kind == Kind.RESUME) {
            return gradingService.resume(gradingRun.getId(), listener);
        }
        if (scoreSheetFile != null) {
            return gradingService.startGrading(inputPaths, section,
                    testCaseSource, listener, scoreSheetFile);
        }
        return gradingService.startGrading(inputPaths, section,
                testCaseSource, listener);
    }
}
