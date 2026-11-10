/*
 * File: GradingTask.java
 *
 * Purpose: Runs a grading batch on a background thread so the window
 * stays responsive.
 *
 * Where it fits: Created by LiveBackend, which passes its progress on to
 * the Grade step. GradingService does the grading itself; when the
 * similar code check is on, PlagiarismService then checks the run.
 *
 * Note on untaught Java: javafx.concurrent.Task runs the grading on a
 * background thread, so the window does not freeze.
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
import gradezzy.service.PlagiarismService;
import gradezzy.testcase.TestCaseSource;

/**
 * A background task that grades one batch.
 *
 * <p>Tier: MUST.
 */
public class GradingTask extends Task<GradingRun> {

    // The service that does the grading
    private final GradingService gradingService;

    // What to ask the service to do
    private final GradingTaskKind kind;

    // The zip files or folders the instructor chose
    private final List<Path> inputPaths;

    // The roster chosen for this batch, or null to use the config
    private Path scoreSheetFile = null;

    // The starter folder chosen for this batch, or null to use the config
    private Path starterDirectory = null;

    // The section the batch belongs to
    private final Section section;

    // Where the tester files come from
    private final TestCaseSource testCaseSource;

    // The run to retry or resume, or null for a new batch
    private final GradingRun gradingRun;

    // The object told about progress
    private final ProgressListener listener;

    // Runs the similar code check once grading ends, or null to skip it
    private PlagiarismService plagiarismService = null;

    // Why the similar code check failed, or null; read on the JavaFX
    // thread once the task ends
    private volatile String similarityProblem = null;

    /**
     * Creates a task for a new batch. The listener runs on the background
     * thread, so it must use Platform.runLater to touch a control.
     *
     * @param gradingService the grading service
     * @param inputPaths the chosen zip files or folders
     * @param section the section of the batch
     * @param testCaseSource where the testers come from
     * @param listener the progress listener
     */
    public GradingTask(GradingService gradingService, List<Path> inputPaths,
            Section section, TestCaseSource testCaseSource,
            ProgressListener listener) {
        this.gradingService = gradingService;
        this.kind = GradingTaskKind.START;
        this.inputPaths = inputPaths;
        this.section = section;
        this.testCaseSource = testCaseSource;
        this.gradingRun = null;
        this.listener = listener;
    }

    /**
     * Creates a task for a new batch with the roster chosen on Set up.
     *
     * @param gradingService the grading service
     * @param inputPaths the chosen zip files or folders
     * @param section the section of the batch
     * @param testCaseSource where the testers come from
     * @param listener the progress listener, on the background thread
     * @param scoreSheetFile the roster chosen on Set up, or null
     */
    public GradingTask(GradingService gradingService, List<Path> inputPaths,
            Section section, TestCaseSource testCaseSource,
            ProgressListener listener, Path scoreSheetFile) {
        this(gradingService, inputPaths, section, testCaseSource, listener);
        this.scoreSheetFile = scoreSheetFile;
    }

    /**
     * Creates a task for a new batch with the roster and starter folder
     * chosen on Set up.
     *
     * @param gradingService the grading service
     * @param inputPaths the chosen zip files or folders
     * @param section the section of the batch
     * @param testCaseSource where the testers come from
     * @param listener the progress listener, on the background thread
     * @param scoreSheetFile the roster chosen on Set up, or null
     * @param starterDirectory the starter folder chosen on Set up, or null
     */
    public GradingTask(GradingService gradingService, List<Path> inputPaths,
            Section section, TestCaseSource testCaseSource,
            ProgressListener listener, Path scoreSheetFile,
            Path starterDirectory) {
        this(gradingService, inputPaths, section, testCaseSource, listener,
                scoreSheetFile);
        this.starterDirectory = starterDirectory;
    }

    /**
     * Creates a task that retries or resumes an existing run.
     *
     * @param gradingService the grading service
     * @param kind RETRY_FAILED or RESUME
     * @param gradingRun the run to retry or resume
     * @param listener the progress listener, on the background thread
     */
    public GradingTask(GradingService gradingService,
            GradingTaskKind kind, GradingRun gradingRun,
            ProgressListener listener) {
        this.gradingService = gradingService;
        this.kind = kind;
        this.inputPaths = new ArrayList<>();
        this.section = gradingRun.getSection();
        this.testCaseSource = null;
        this.gradingRun = gradingRun;
        this.listener = listener;
    }

    /**
     * Creates a task that grades a late batch into an existing run of the
     * same section.
     *
     * @param gradingService the grading service
     * @param gradingRun the run the batch joins
     * @param inputPaths the chosen zip files or folders
     * @param testCaseSource where the testers come from
     * @param listener the progress listener, on the background thread
     * @param scoreSheetFile the roster chosen on Set up, or null
     */
    public GradingTask(GradingService gradingService, GradingRun gradingRun,
            List<Path> inputPaths, TestCaseSource testCaseSource,
            ProgressListener listener, Path scoreSheetFile) {
        this.gradingService = gradingService;
        this.kind = GradingTaskKind.ADD_TO_RUN;
        this.inputPaths = inputPaths;
        this.section = gradingRun.getSection();
        this.testCaseSource = testCaseSource;
        this.gradingRun = gradingRun;
        this.listener = listener;
        this.scoreSheetFile = scoreSheetFile;
    }

    /**
     * Runs the similar code check on the run once grading ends.
     *
     * @param plagiarismService the service that runs the check
     */
    public void checkSimilarityAfter(PlagiarismService plagiarismService) {
        this.plagiarismService = plagiarismService;
    }

    /**
     * Returns why the similar code check after grading failed.
     *
     * @return the reason, or null when it did not fail
     */
    public String getSimilarityProblem() {
        return similarityProblem;
    }

    /**
     * Grades the batch, then checks it for similar code. JavaFX calls
     * this on a background thread.
     *
     * @return the finished run, or null when the service returned none
     * @throws Exception if the batch cannot start
     */
    @Override
    protected GradingRun call() throws Exception {
        GradingRun run = grade();
        checkSimilarity(run);
        return run;
    }

    // Runs the similar code check here, before Review opens, until
    // GradingService runs it after grading. A failure does not fail
    // grading; it is kept for LiveBackend to log.
    private void checkSimilarity(GradingRun run) {
        if (run == null || plagiarismService == null) {
            return;
        }

        try {
            plagiarismService.check(run);
        } catch (RuntimeException exception) {
            similarityProblem = "The check stopped: "
                    + exception.getMessage();
        }
    }

    // Asks the service to grade, retry, resume or add to the batch.
    private GradingRun grade() throws Exception {
        if (kind == GradingTaskKind.RETRY_FAILED) {
            return gradingService.retryFailed(gradingRun, listener);
        } else if (kind == GradingTaskKind.RESUME) {
            return gradingService.resume(gradingRun.getId(), listener);
        } else if (kind == GradingTaskKind.ADD_TO_RUN) {
            return gradingService.addToRun(gradingRun, inputPaths,
                    testCaseSource, listener, scoreSheetFile);
        }
        // A null roster or starter folder means the one in the settings
        return gradingService.startGrading(inputPaths, section,
                testCaseSource, listener, scoreSheetFile, starterDirectory);
    }
}
