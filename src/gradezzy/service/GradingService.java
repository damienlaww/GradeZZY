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
import java.nio.file.Paths;
import java.time.LocalDateTime;
import java.util.List;

import gradezzy.config.AppConfig;
import gradezzy.config.ConfigKeys;
import gradezzy.grading.CancellationToken;
import gradezzy.grading.GradingContext;
import gradezzy.grading.GradingException;
import gradezzy.grading.GradingPipeline;
import gradezzy.grading.ProgressListener;
import gradezzy.ingest.IngestException;
import gradezzy.ingest.RosterReader;
import gradezzy.ingest.SubmissionLoader;
import gradezzy.model.Assessment;
import gradezzy.model.GradingRun;
import gradezzy.model.Section;
import gradezzy.model.Student;
import gradezzy.model.Submission;
import gradezzy.model.SubmissionStatus;
import gradezzy.persistence.PersistenceException;
import gradezzy.persistence.ResultRepository;
import gradezzy.testcase.TestCaseException;
import gradezzy.testcase.TestCaseSource;
import gradezzy.testcase.UploadedTesterSource;

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

    // The store runs are saved to, or null to save nothing
    private final ResultRepository repository;

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
        this(config, pipelineFactory, assessmentFactory, null);
    }

    /**
     * Creates a new grading service that saves each run.
     *
     * @param config the application settings
     * @param pipelineFactory the helper that builds the pipeline
     * @param assessmentFactory the helper that reads the assessment
     * @param repository the store runs are saved to, or null to save
     *        nothing
     */
    public GradingService(AppConfig config, PipelineFactory pipelineFactory,
            AssessmentFactory assessmentFactory,
            ResultRepository repository) {
        this.config = config;
        this.pipelineFactory = pipelineFactory;
        this.assessmentFactory = assessmentFactory;
        this.repository = repository;
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
     *         example when an argument is missing, the module or section
     *         is not in the settings, or the score sheet, the uploads or
     *         the testers cannot be read
     */
    public GradingRun startGrading(List<Path> inputPaths, Section section,
            TestCaseSource testCaseSource, ProgressListener listener)
            throws GradingException {
        return startGrading(inputPaths, section, testCaseSource, listener,
                Path.of(config.getString(ConfigKeys.SCORE_SHEET_FILE)));
    }

    /** Starts a run using the roster explicitly selected for this batch. */
    public GradingRun startGrading(List<Path> inputPaths, Section section,
            TestCaseSource testCaseSource, ProgressListener listener,
            Path scoreSheetFile) throws GradingException {
        checkArguments(inputPaths, section, testCaseSource, listener);
        cancellationToken.reset();

        Assessment assessment = assessmentFactory.create(
                section.getModuleCode());
        String runId = RUN_ID_PREFIX + System.currentTimeMillis();
        GradingRun gradingRun = new GradingRun(runId, assessment, section,
                LocalDateTime.now());
        // Kept on the run so the CSV export and Resume use this roster,
        // not the one in the settings
        gradingRun.setScoreSheetFile(scoreSheetFile);
        GradingContext context = new GradingContext(config, assessment);
        context.setRunId(runId);

        // The roster for this batch is read first, so a bad score sheet
        // stops the run before any zip is extracted.
        readRoster(context, scoreSheetFile);

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
        } catch (IllegalArgumentException exception) {
            // For example a zip name pattern in the settings that is not
            // a valid regular expression, or a path that is not valid
            throw new GradingException("A setting or path is not valid: "
                    + exception.getMessage(), exception);
        }

        lastRun = gradingRun;
        lastContext = context;

        // Saved before grading so every submission is on record as PENDING
        // and the run can be resumed after a crash
        saveRun(gradingRun);

        // The pipeline grades exec.thread.count submissions at a time.
        GradingPipeline pipeline = pipelineFactory.createPipeline(
                cancellationToken);
        pipeline.run(gradingRun, context, listener);

        // Saved again so FAILED and CANCELLED submissions, which never
        // reach PersistStage, are on record too
        saveRun(gradingRun);
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
     * <p>The listener is told how many submissions are retried and
     * hears about each of them; when the retry ends it is given the run
     * passed in, with every submission, so a later Retry can be called
     * with that run again.
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
        pipeline.run(retryRun, lastContext,
                new RetryProgressListener(listener, gradingRun));

        saveRun(gradingRun);
        return gradingRun;
    }

    /**
     * Continues a run that was cancelled or interrupted. Only the
     * submissions that were not finished (PENDING, RUNNING or CANCELLED)
     * are graded; GRADED and FAILED ones are left as they are. Each
     * unfinished submission starts from scratch, as in
     * {@link #retryFailed}.
     *
     * <p>A run started in this session is continued with the score sheet
     * and testers it was started with. A run from an earlier session is
     * loaded from the database; the score sheet and testers chosen for
     * that batch are not saved, so the ones in the settings
     * (paths.score.sheet.file and paths.testers.dir) are used, with
     * the students already matched in the saved run.
     *
     * <p>Tier: SHOULD.
     *
     * @param runId the id of the saved run
     * @param listener the object told about progress
     * @return the resumed run, with every submission in it
     * @throws GradingException if the id or listener is missing, the run
     *         is not in this session and cannot be loaded, or its score
     *         sheet or testers cannot be read
     */
    public GradingRun resume(String runId, ProgressListener listener)
            throws GradingException {
        if (runId == null || runId.isBlank() || listener == null) {
            throw new GradingException("Choose a run to resume.");
        }

        if (lastRun == null || !runId.equals(lastRun.getId())) {
            GradingRun savedRun = loadSavedRun(runId);
            lastContext = prepareSavedContext(savedRun);
            lastRun = savedRun;
        }
        GradingRun gradingRun = lastRun;

        // The unfinished submissions go through the pipeline on their
        // own, in a run that shares the id, assessment and section.
        GradingRun resumeRun = new GradingRun(gradingRun.getId(),
                gradingRun.getAssessment(), gradingRun.getSection(),
                gradingRun.getStartedAt());

        for (Submission submission : gradingRun.getSubmissions()) {
            if (needsResume(submission)) {
                resetForRetry(submission);
                resumeRun.addSubmission(submission);
            }
        }

        if (resumeRun.getSubmissions().isEmpty()) {
            return gradingRun;
        }

        cancellationToken.reset();
        GradingPipeline pipeline = pipelineFactory.createPipeline(
                cancellationToken);
        pipeline.run(resumeRun, lastContext,
                new RetryProgressListener(listener, gradingRun));

        saveRun(gradingRun);
        return gradingRun;
    }

    // Reads a score sheet into the roster of a run.
    private void readRoster(GradingContext context, Path scoreSheetFile)
            throws GradingException {
        try {
            RosterReader rosterReader = new RosterReader(config);
            context.getRoster().addAll(rosterReader.readRoster(
                    scoreSheetFile));
        } catch (IngestException exception) {
            throw new GradingException("The score sheet could not be "
                    + "read: " + exception.getMessage(), exception);
        } catch (IllegalArgumentException exception) {
            throw new GradingException("The score sheet setting is not a "
                    + "valid path: " + exception.getMessage(), exception);
        }
    }

    // Loads a run of an earlier session from the database.
    private GradingRun loadSavedRun(String runId) throws GradingException {
        if (repository == null
                || !config.getBoolean(ConfigKeys.DATABASE_ENABLED)) {
            throw new GradingException("Run " + runId + " was not "
                    + "started in this session and the database is "
                    + "switched off, so it cannot be resumed.");
        }

        GradingRun savedRun;
        try {
            savedRun = repository.loadRun(runId);
        } catch (PersistenceException exception) {
            throw new GradingException("Run " + runId + " could not be "
                    + "loaded: " + exception.getMessage(), exception);
        }

        if (savedRun == null) {
            throw new GradingException("There is no saved run " + runId
                    + ".");
        }
        return savedRun;
    }

    // Builds the shared information for a run loaded from the database:
    // the students matched before, the score sheet and the testers from
    // the settings.
    private GradingContext prepareSavedContext(GradingRun savedRun)
            throws GradingException {
        Assessment assessment = savedRun.getAssessment();
        GradingContext context = new GradingContext(config, assessment);
        context.setRunId(savedRun.getId());

        for (Submission submission : savedRun.getSubmissions()) {
            Student student = submission.getStudent();
            if (student != null) {
                context.getRoster().add(student);
            }
        }

        // Resume reads the roster the run was graded against. A run saved
        // before the roster was recorded falls back to the settings.
        Path scoreSheetFile = savedRun.getScoreSheetFile();
        if (scoreSheetFile == null) {
            scoreSheetFile = Paths.get(config.getString(
                    ConfigKeys.SCORE_SHEET_FILE));
        }
        readRoster(context, scoreSheetFile);

        try {
            TestCaseSource testCaseSource = new UploadedTesterSource(
                    Paths.get(config.getString(ConfigKeys.TESTERS_DIR)),
                    config);
            context.getTesters().addAll(
                    testCaseSource.prepareTesters(assessment));
        } catch (TestCaseException exception) {
            throw new GradingException("The testers could not be "
                    + "prepared: " + exception.getMessage(), exception);
        } catch (IllegalArgumentException exception) {
            throw new GradingException("The testers setting is not a "
                    + "valid path: " + exception.getMessage(), exception);
        }
        return context;
    }

    // Saves the run when the database is switched on. A failure does not
    // stop grading: the results stay in memory, and PersistStage reports
    // a database that cannot be written on each submission, so it is not
    // reported twice here.
    private void saveRun(GradingRun gradingRun) {
        if (repository == null
                || !config.getBoolean(ConfigKeys.DATABASE_ENABLED)) {
            return;
        }

        try {
            repository.saveRun(gradingRun);
        } catch (PersistenceException exception) {
            // Reported by PersistStage, as explained above
        }
    }

    // Stops a run that is missing an argument or names a module or
    // section that is not in the settings.
    private void checkArguments(List<Path> inputPaths, Section section,
            TestCaseSource testCaseSource, ProgressListener listener)
            throws GradingException {
        if (inputPaths == null || inputPaths.isEmpty()) {
            throw new GradingException("Choose at least one ZIP file or "
                    + "folder to grade.");
        }

        if (section == null) {
            throw new GradingException("Choose a module and section.");
        }

        if (testCaseSource == null || listener == null) {
            throw new GradingException("The testers and the progress "
                    + "listener must be given.");
        }

        if (!isListed(ConfigKeys.MODULE_CODES, section.getModuleCode())) {
            throw new GradingException("Module '" + section.getModuleCode()
                    + "' is not in the setting "
                    + ConfigKeys.MODULE_CODES + ".");
        }

        if (!isListed(ConfigKeys.SECTION_CODES,
                section.getSectionCode())) {
            throw new GradingException("Section '"
                    + section.getSectionCode() + "' is not in the setting "
                    + ConfigKeys.SECTION_CODES + ".");
        }
    }

    // Says whether a code is in a list setting, ignoring case.
    private boolean isListed(String key, String code) {
        if (code == null) {
            return false;
        }

        for (String listed : config.getList(key)) {
            if (listed.equalsIgnoreCase(code)) {
                return true;
            }
        }

        return false;
    }

    // Says whether a submission stopped early and should be graded again.
    private boolean needsRetry(Submission submission) {
        if (submission.getIngestFailure() != null) {
            return false;
        }
        SubmissionStatus status = submission.getStatus();

        return status == SubmissionStatus.FAILED
                || status == SubmissionStatus.CANCELLED;
    }

    // Says whether a submission was not finished when its run stopped.
    // RUNNING means the application stopped while it was being graded.
    private boolean needsResume(Submission submission) {
        if (submission.getIngestFailure() != null) {
            return false;
        }
        SubmissionStatus status = submission.getStatus();

        return status == SubmissionStatus.PENDING
                || status == SubmissionStatus.RUNNING
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
