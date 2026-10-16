/*
 * File: GradingService.java
 *
 * Purpose: Starts, cancels, retries and resumes a grading run.
 *
 * Where it fits: The entry point to batch grading for both the console
 * and the window. It loads the submissions, prepares the testers, builds
 * the pipeline and runs it. It contains no JavaFX, so the whole grading
 * core can be run and tested from the console.
 */
package gradezzy.service;

import java.nio.file.Path;
import java.time.LocalDateTime;
import java.util.List;

import gradezzy.config.AppConfig;
import gradezzy.grading.CancellationToken;
import gradezzy.grading.GradingContext;
import gradezzy.grading.GradingException;
import gradezzy.grading.GradingPipeline;
import gradezzy.grading.ProgressListener;
import gradezzy.ingest.IngestException;
import gradezzy.ingest.SubmissionLoader;
import gradezzy.model.Assessment;
import gradezzy.model.GradingRun;
import gradezzy.model.Section;
import gradezzy.model.Submission;
import gradezzy.model.SubmissionStatus;
import gradezzy.testcase.TestCaseException;
import gradezzy.testcase.TestCaseSource;

/**
 * Starts, cancels, retries and resumes grading runs.
 *
 * <p>Tier: MUST. Owner: Jing Yuan.
 */
public class GradingService {

    // Start of the id given to every run
    private static final String RUN_ID_PREFIX = "RUN-";

    // The application settings
    private final AppConfig config;

    // The helper that builds the pipeline
    private final PipelineFactory pipelineFactory;

    // The helper that reads the assessment from the settings
    private final AssessmentFactory assessmentFactory;

    // The flag that stops the current run
    private final CancellationToken cancellationToken =
            new CancellationToken();

    // The last run started, kept so Retry can grade it again
    private GradingRun lastRun = null;

    // The shared information of the last run started, kept for Retry
    private GradingContext lastContext = null;

    /**
     * Creates a new grading service.
     *
     * @param config the application settings
     * @param pipelineFactory the helper that builds the pipeline
     * @param assessmentFactory the helper that reads the assessment
     */
    public GradingService(AppConfig config, PipelineFactory pipelineFactory,
            AssessmentFactory assessmentFactory) {
        this.config = config;
        this.pipelineFactory = pipelineFactory;
        this.assessmentFactory = assessmentFactory;
    }

    /**
     * Grades a whole batch of submissions for one section. A submission
     * that fails is marked FAILED and does not stop the others.
     *
     * @param inputPaths the zip files or folders the instructor chose
     * @param section the section the batch belongs to
     * @param testCaseSource where the tester files come from
     * @param listener the object told about progress
     * @return the finished run, holding every submission and its results
     * @throws GradingException if the batch cannot start at all, for
     *         example when the uploads or the testers cannot be read
     */
    public GradingRun startGrading(List<Path> inputPaths, Section section,
            TestCaseSource testCaseSource, ProgressListener listener)
            throws GradingException {
        cancellationToken.reset();

        Assessment assessment = assessmentFactory.create(
                section.getModuleCode());
        String runId = RUN_ID_PREFIX + System.currentTimeMillis();
        GradingRun gradingRun = new GradingRun(runId, assessment, section,
                LocalDateTime.now());
        GradingContext context = new GradingContext(config, assessment);

        try {
            SubmissionLoader loader = new SubmissionLoader(config);
            List<Submission> submissions = loader.loadSubmissions(
                    inputPaths, section);

            for (Submission submission : submissions) {
                gradingRun.addSubmission(submission);
            }

            context.getTesters().addAll(
                    testCaseSource.prepareTesters(assessment));
        } catch (IngestException exception) {
            throw new GradingException("The submissions could not be "
                    + "read: " + exception.getMessage(), exception);
        } catch (TestCaseException exception) {
            throw new GradingException("The testers could not be "
                    + "prepared: " + exception.getMessage(), exception);
        }

        lastRun = gradingRun;
        lastContext = context;

        // TODO(Jing Yuan): implement: read the roster into
        //     context.getRoster() with RosterReader, and save the run with
        //     the repository before grading starts
        // TODO(Jing Yuan): implement: grade THREAD_COUNT submissions at a
        //     time. For now submissions are graded one after another.
        GradingPipeline pipeline = pipelineFactory.createPipeline(
                cancellationToken);
        pipeline.run(gradingRun, context, listener);

        return gradingRun;
    }

    /**
     * Asks the current run to stop after the submission being graded.
     *
     * <p>Tier: SHOULD.
     */
    public void cancel() {
        cancellationToken.cancel();
    }

    /**
     * Grades the failed and cancelled submissions of a run again. GRADED
     * and PENDING submissions are left as they are. Each retried
     * submission starts from scratch: its status goes back to PENDING,
     * its earlier anomalies and question results are removed and its
     * working copy is forgotten, so every stage runs again and builds a
     * new one. Fixes applied in the earlier attempt are therefore not
     * carried over; the stages find and fix the problems again.
     *
     * <p>The listener hears about a temporary run that holds only the
     * retried submissions. A screen should keep showing the run it
     * passed in, which is also the run returned.
     *
     * <p>Tier: SHOULD.
     *
     * @param gradingRun the run to retry
     * @param listener the object told about progress
     * @return the same run, with updated results
     * @throws GradingException if there is no run, or if the run was not
     *         the last one started in this session, so its testers are
     *         not loaded
     */
    public GradingRun retryFailed(GradingRun gradingRun,
            ProgressListener listener) throws GradingException {
        if (gradingRun == null) {
            throw new GradingException("There is no run to retry.");
        }

        if (gradingRun != lastRun) {
            throw new GradingException("Run " + gradingRun.getId()
                    + " was not the last run started in this session, so "
                    + "its testers are not loaded. Start grading again "
                    + "instead.");
        }

        // The retried submissions go through the pipeline on their own,
        // in a run that shares the id, assessment and section.
        GradingRun retryRun = new GradingRun(gradingRun.getId(),
                gradingRun.getAssessment(), gradingRun.getSection(),
                gradingRun.getStartedAt());

        for (Submission submission : gradingRun.getSubmissions()) {
            if (needsRetry(submission)) {
                resetForRetry(submission);
                retryRun.addSubmission(submission);
            }
        }

        if (retryRun.getSubmissions().isEmpty()) {
            return gradingRun;
        }

        cancellationToken.reset();
        GradingPipeline pipeline = pipelineFactory.createPipeline(
                cancellationToken);
        pipeline.run(retryRun, lastContext, listener);

        return gradingRun;
    }

    /**
     * Continues a run that was interrupted, using the saved state.
     *
     * <p>Tier: SHOULD.
     *
     * @param runId the id of the saved run
     * @param listener the object told about progress
     * @return the resumed run, or null until this is implemented
     * @throws GradingException if the run cannot be loaded
     */
    public GradingRun resume(String runId, ProgressListener listener)
            throws GradingException {
        // TODO(Jing Yuan): implement: load the run from the repository and
        //     run the pipeline on the submissions that are still PENDING
        return null;
    }

    // Says whether a submission stopped early and should be graded again.
    private boolean needsRetry(Submission submission) {
        SubmissionStatus status = submission.getStatus();

        return status == SubmissionStatus.FAILED
                || status == SubmissionStatus.CANCELLED;
    }

    // Clears what the earlier attempt left, because every stage runs
    // again from the start and would otherwise add duplicates.
    private void resetForRetry(Submission submission) {
        submission.setStatus(SubmissionStatus.PENDING);
        submission.getAnomalies().clear();
        submission.getQuestionResults().clear();
        submission.setWorkingCopyPath(null);
    }
}
