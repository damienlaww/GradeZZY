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

import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.time.LocalDateTime;
import java.util.ArrayList;
import java.util.HashSet;
import java.util.List;
import java.util.Set;
import java.util.UUID;

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
import gradezzy.model.Anomaly;
import gradezzy.model.AnomalyType;
import gradezzy.model.Assessment;
import gradezzy.model.GradingRun;
import gradezzy.model.Section;
import gradezzy.model.Severity;
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

    // Start of the id of the warning added when a run cannot be saved
    private static final String SAVE_WARNING_ID_PREFIX = "save-";

    // Start of the id of the warning added when a resumed run uses the
    // tester folder in the settings
    private static final String RESUME_WARNING_ID_PREFIX = "resume-";

    // What the instructor can do when a resumed run used the settings'
    // tester folder
    private static final String RESUME_WARNING_ACTION = "Check the marks "
            + "of this student, or grade the batch again with Set up.";

    // The audit log actions this service records
    private static final String GRADING_STARTED = "Grading started";
    private static final String GRADING_NOT_STARTED =
            "Grading could not start";
    private static final String SAVE_FAILED = "Save failed";
    private static final String GRADING_CANCELLED = "Grading cancelled";
    private static final String GRADING_RETRIED = "Grading retried";
    private static final String GRADING_RESUMED = "Grading resumed";

    // What the instructor can do when a run cannot be saved. PersistStage
    // gives its warning the same text, so both are found as save warnings.
    private static final String SAVE_FAILED_ACTION = "Export the results "
            + "before closing the app, then check database.file in the "
            + "settings.";

    // The application settings
    private final AppConfig config;

    // The helper that builds the pipeline
    private final PipelineFactory pipelineFactory;

    // The helper that reads the assessment from the settings
    private final AssessmentFactory assessmentFactory;

    // The store runs are saved to, or null to save nothing
    private final ResultRepository repository;

    // The log of instructor actions, kept in the same store
    private final AuditService auditService;

    // The flag that stops the current run
    private final CancellationToken cancellationToken =
            new CancellationToken();

    // The last run started, kept so Retry can grade it again
    private GradingRun lastRun = null;

    // The shared information of the last run started, kept for Retry
    private GradingContext lastContext = null;

    // Every run started or resumed in this session, so Statistics can
    // show them even when the database is off or a save failed. Only
    // read and changed in the synchronized methods below.
    private final List<GradingRun> sessionRuns = new ArrayList<>();

    // The ids of the runs whose failed save is already in the audit log,
    // so a run that fails to save again is not logged twice
    private final Set<String> unsavedRunIds = new HashSet<>();

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
        this(config, pipelineFactory, assessmentFactory, repository,
                new AuditService(config, repository));
    }

    /**
     * Creates a new grading service that saves each run and writes its
     * actions to the given audit log.
     *
     * @param config the application settings
     * @param pipelineFactory the helper that builds the pipeline
     * @param assessmentFactory the helper that reads the assessment
     * @param repository the store runs are saved to, or null to save
     *        nothing
     * @param auditService the log of instructor actions
     */
    public GradingService(AppConfig config, PipelineFactory pipelineFactory,
            AssessmentFactory assessmentFactory,
            ResultRepository repository, AuditService auditService) {
        this.config = config;
        this.pipelineFactory = pipelineFactory;
        this.assessmentFactory = assessmentFactory;
        this.repository = repository;
        this.auditService = auditService;
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

    /**
     * Grades a whole batch against the score sheet chosen for it, with
     * the starter folder in the settings.
     *
     * <p>Tier: MUST.
     *
     * @param inputPaths the zip files or folders the instructor chose
     * @param section the section the batch belongs to
     * @param testCaseSource where the tester files come from
     * @param listener the object told about progress
     * @param scoreSheetFile the score sheet for this batch, or null for
     *        the one in the settings
     * @return the finished run, holding every submission and its results
     * @throws GradingException if the batch cannot start at all
     */
    public GradingRun startGrading(List<Path> inputPaths, Section section,
            TestCaseSource testCaseSource, ProgressListener listener,
            Path scoreSheetFile) throws GradingException {
        return startGrading(inputPaths, section, testCaseSource, listener,
                scoreSheetFile, null);
    }

    /**
     * Starts a run using the roster and the starter folder chosen for
     * this batch; null means the score sheet or the starter folder in the
     * settings. The starter folder is kept on the run, so Retry, and
     * Resume while this is still the last run, use it too. A start that
     * fails is written to the audit log with its reason.
     *
     * <p>Tier: MUST.
     *
     * @param inputPaths the zip files or folders the instructor chose
     * @param section the section the batch belongs to
     * @param testCaseSource where the tester files come from
     * @param listener the object told about progress
     * @param scoreSheetFile the score sheet for this batch, or null for
     *        the one in the settings
     * @param starterDirectory the starter folder for this batch, or null
     *        for the one in the settings
     * @return the finished run, holding every submission and its results
     * @throws GradingException if the batch cannot start at all
     */
    public GradingRun startGrading(List<Path> inputPaths, Section section,
            TestCaseSource testCaseSource, ProgressListener listener,
            Path scoreSheetFile, Path starterDirectory)
            throws GradingException {
        GradingRun gradingRun = null;
        try {
            gradingRun = prepareRun(inputPaths, section, testCaseSource,
                    listener, scoreSheetFile, starterDirectory);
        } catch (GradingException exception) {
            auditService.record(GRADING_NOT_STARTED, exception.getMessage());
            throw exception;
        }

        // Written once the files are loaded, so it counts the students,
        // not the zips and folders chosen
        auditService.record(GRADING_STARTED, "Grading "
                + gradingRun.getSubmissions().size() + " submission(s) for "
                + section.getLabel() + ".");

        // Saved before grading so every submission is on record as PENDING
        // and the run can be resumed after a crash. A failure is not
        // logged yet: the save after grading tries again and logs it.
        saveRun(gradingRun, false);

        // The pipeline grades exec.thread.count submissions at a time.
        // prepareRun kept the run's shared information in lastContext.
        GradingPipeline pipeline = createPipeline(gradingRun);
        pipeline.run(gradingRun, lastContext, listener);

        // Saved again so FAILED and CANCELLED submissions, which never
        // reach PersistStage, are on record too
        saveRun(gradingRun, true);
        return gradingRun;
    }

    // Checks the arguments, reads the roster, loads the submissions and
    // prepares the testers, then keeps the run for Retry. Nothing is
    // graded yet.
    private GradingRun prepareRun(List<Path> inputPaths, Section section,
            TestCaseSource testCaseSource, ProgressListener listener,
            Path scoreSheetFile, Path starterDirectory)
            throws GradingException {
        checkArguments(inputPaths, section, testCaseSource, listener);
        cancellationToken.reset();

        Path rosterFile = scoreSheetFile;
        if (rosterFile == null) {
            rosterFile = Path.of(config.getString(
                    ConfigKeys.SCORE_SHEET_FILE));
        }

        Assessment assessment = assessmentFactory.create(
                section.getModuleCode());
        String runId = RUN_ID_PREFIX + System.currentTimeMillis();
        GradingRun gradingRun = new GradingRun(runId, assessment, section,
                LocalDateTime.now());
        // Kept on the run so the CSV export and Resume use this roster,
        // not the one in the settings
        gradingRun.setScoreSheetFile(rosterFile);
        gradingRun.setStarterDirectory(starterDirectory);
        // Kept so Resume after a restart uses the same testers
        if (testCaseSource instanceof UploadedTesterSource) {
            UploadedTesterSource uploaded =
                    (UploadedTesterSource) testCaseSource;
            gradingRun.setTestersDirectory(uploaded.getTestersDirectory());
        }
        GradingContext context = new GradingContext(config, assessment);
        context.setRunId(runId);

        // The roster for this batch is read first, so a bad score sheet
        // stops the run before any zip is extracted.
        readRoster(context, rosterFile);

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
        addSessionRun(gradingRun);
        return gradingRun;
    }

    /**
     * Asks the current run to stop after the submission being graded.
     *
     * <p>Tier: SHOULD.
     */
    public synchronized void cancel() {
        if (!cancellationToken.isCancelled()) {
            String detail = "Grading was stopped.";
            if (lastRun != null) {
                detail = "Grading of " + lastRun.getId() + " was stopped.";
            }
            auditService.record(GRADING_CANCELLED, detail);
        }
        cancellationToken.cancel();
    }

    /**
     * Returns every run started or resumed in this session, oldest first,
     * including runs that could not be saved.
     *
     * <p>Tier: SHOULD.
     *
     * @return a copy of the list of this session's runs
     */
    public synchronized List<GradingRun> getSessionRuns() {
        return new ArrayList<>(sessionRuns);
    }

    // Remembers a run of this session once, keeping the newest object
    // when the same run is loaded again.
    private synchronized void addSessionRun(GradingRun gradingRun) {
        for (int index = 0; index < sessionRuns.size(); index++) {
            if (sessionRuns.get(index).getId().equals(gradingRun.getId())) {
                sessionRuns.set(index, gradingRun);
                return;
            }
        }
        sessionRuns.add(gradingRun);
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

        auditService.record(GRADING_RETRIED, "Grading "
                + retryRun.getSubmissions().size() + " submission(s) of "
                + gradingRun.getId() + " again.");
        cancellationToken.reset();
        GradingPipeline pipeline = createPipeline(gradingRun);
        pipeline.run(retryRun, lastContext,
                new RetryProgressListener(listener, gradingRun));

        saveRun(gradingRun, true);
        return gradingRun;
    }

    /**
     * Continues a run that was cancelled or interrupted. Only the
     * submissions that were not finished (PENDING, RUNNING or CANCELLED)
     * are graded; GRADED and FAILED ones are left as they are. Each
     * unfinished submission starts from scratch, as in
     * {@link #retryFailed}.
     *
     * <p>The last run started or resumed in this session is continued
     * with the score sheet, testers and starter folder it was started
     * with. Any other run, even an earlier one of this session, is loaded
     * from the database, with the students already matched in it. It is
     * graded with the tester folder saved with it, if the database holds
     * one. The database does not save the tester or starter folder yet,
     * so for now such a run uses the ones in the settings
     * (paths.testers.dir and paths.starter.dir), and every student graded
     * again gets a warning saying so, because other testers can give
     * other marks.
     *
     * <p>Tier: SHOULD.
     *
     * @param runId the id of the saved run
     * @param listener the object told about progress
     * @return the resumed run, with every submission in it
     * @throws GradingException if the id or listener is missing, the run
     *         is not the last one and cannot be loaded, or its score
     *         sheet or testers cannot be read
     */
    public GradingRun resume(String runId, ProgressListener listener)
            throws GradingException {
        if (runId == null || runId.isBlank() || listener == null) {
            throw new GradingException("Choose a run to resume.");
        }

        // Set when the saved run does not say which testers it used
        String testerWarning = null;
        if (lastRun == null || !runId.equals(lastRun.getId())) {
            GradingRun savedRun = loadSavedRun(runId);
            lastContext = prepareSavedContext(savedRun);
            lastRun = savedRun;
            addSessionRun(savedRun);
            if (savedRun.getTestersDirectory() == null) {
                testerWarning = "This student was graded after the app "
                        + "was restarted. The tester folder this run was "
                        + "started with was not saved, so the one in the "
                        + "settings was used: "
                        + config.getString(ConfigKeys.TESTERS_DIR)
                        + ". If that is a different folder, the marks can "
                        + "differ from the rest of the run.";
            }
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
                if (testerWarning != null) {
                    submission.addAnomaly(new Anomaly(
                            RESUME_WARNING_ID_PREFIX + UUID.randomUUID(),
                            AnomalyType.INTERNAL_ERROR, Severity.WARNING,
                            submission.getUsername(), testerWarning,
                            RESUME_WARNING_ACTION));
                }
                resumeRun.addSubmission(submission);
            }
        }

        if (resumeRun.getSubmissions().isEmpty()) {
            return gradingRun;
        }

        auditService.record(GRADING_RESUMED, "Grading the "
                + resumeRun.getSubmissions().size() + " unfinished "
                + "submission(s) of " + gradingRun.getId() + ".");
        cancellationToken.reset();
        GradingPipeline pipeline = createPipeline(gradingRun);
        pipeline.run(resumeRun, lastContext,
                new RetryProgressListener(listener, gradingRun));

        saveRun(gradingRun, true);
        return gradingRun;
    }

    // Builds the pipeline for a run. The one-argument factory method,
    // which uses the starter folder in the settings, is kept for a run
    // with no starter folder of its own.
    private GradingPipeline createPipeline(GradingRun gradingRun) {
        Path starterDirectory = gradingRun.getStarterDirectory();
        if (starterDirectory == null) {
            return pipelineFactory.createPipeline(cancellationToken);
        }
        return pipelineFactory.createPipeline(cancellationToken,
                starterDirectory);
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
    // the students matched before, the score sheet and the testers the
    // run was started with, or those in the settings if they were not
    // saved. A saved tester folder that is gone stops the resume, rather
    // than grading the rest with other testers.
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

        Path testersDirectory = savedRun.getTestersDirectory();
        if (testersDirectory == null) {
            testersDirectory = Paths.get(config.getString(
                    ConfigKeys.TESTERS_DIR));
        } else if (!Files.isDirectory(testersDirectory)) {
            throw new GradingException("Run " + savedRun.getId()
                    + " cannot be resumed: it was graded with the testers "
                    + "in " + testersDirectory + ", which is no longer "
                    + "there. Put the folder back, or grade the batch "
                    + "again.");
        }

        try {
            TestCaseSource testCaseSource = new UploadedTesterSource(
                    testersDirectory, config);
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
    // stop grading: the results stay in memory. The save warnings always
    // describe the latest save: the old ones are removed first, and a
    // failed save gives each submission one warning, so the console and
    // the window both show it. When logFailure is true, a failure is also
    // written to the audit log, once per run.
    private void saveRun(GradingRun gradingRun, boolean logFailure) {
        if (repository == null
                || !config.getBoolean(ConfigKeys.DATABASE_ENABLED)) {
            return;
        }

        removeSaveWarnings(gradingRun);
        try {
            repository.saveRun(gradingRun);
            unsavedRunIds.remove(gradingRun.getId());
        } catch (PersistenceException exception) {
            String message = "This run could not be saved to the "
                    + "database, so it is not in the list of runs: "
                    + exception.getMessage() + " The marks are kept until "
                    + "the app is closed.";
            // add returns false when this run's failure is already logged
            if (logFailure && unsavedRunIds.add(gradingRun.getId())) {
                auditService.record(SAVE_FAILED, message);
            }
            for (Submission submission : gradingRun.getSubmissions()) {
                submission.addAnomaly(new Anomaly(SAVE_WARNING_ID_PREFIX
                        + UUID.randomUUID(), AnomalyType.INTERNAL_ERROR,
                        Severity.WARNING, submission.getUsername(),
                        message, SAVE_FAILED_ACTION));
            }
        }
    }

    // Removes the warnings of earlier saves, from this service or from
    // PersistStage, from every submission of a run.
    private void removeSaveWarnings(GradingRun gradingRun) {
        for (Submission submission : gradingRun.getSubmissions()) {
            List<Anomaly> saveWarnings = new ArrayList<>();
            for (Anomaly anomaly : submission.getAnomalies()) {
                if (isSaveWarning(anomaly)) {
                    saveWarnings.add(anomaly);
                }
            }
            submission.getAnomalies().removeAll(saveWarnings);
        }
    }

    // Says whether an anomaly says that marks could not be saved.
    private boolean isSaveWarning(Anomaly anomaly) {
        return anomaly.getType() == AnomalyType.INTERNAL_ERROR
                && SAVE_FAILED_ACTION.equals(anomaly.getSuggestedAction());
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
    // again from the start and would otherwise add duplicates. The window
    // may be reading these lists; Submission makes that safe. A warning
    // about a duplicate file is kept, because loading is not repeated.
    private void resetForRetry(Submission submission) {
        List<Anomaly> loadingWarnings = new ArrayList<>();
        for (Anomaly anomaly : submission.getAnomalies()) {
            if (anomaly.getType() == AnomalyType.DUPLICATE_SUBMISSION) {
                loadingWarnings.add(anomaly);
            }
        }

        submission.setStatus(SubmissionStatus.PENDING);
        submission.getAnomalies().clear();
        submission.getAnomalies().addAll(loadingWarnings);
        submission.getQuestionResults().clear();
        submission.setWorkingCopyPath(null);
    }
}
