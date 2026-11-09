/*
 * File: GradingService.java
 *
 * Purpose: Starts, cancels, retries and resumes a grading run, and runs
 * the similar code check when a batch has been graded.
 *
 * Where it fits: The entry point to batch grading for both the console
 * and the window. It loads the submissions, prepares the testers, builds
 * the pipeline and runs it. It contains no JavaFX, so the whole grading
 * core can be run and tested from the console.
 */
package gradezzy.service;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.nio.file.StandardCopyOption;
import java.time.LocalDateTime;
import java.util.ArrayList;
import java.util.HashSet;
import java.util.List;
import java.util.Locale;
import java.util.Set;
import java.util.UUID;

import gradezzy.config.AppConfig;
import gradezzy.config.ConfigKeys;
import gradezzy.config.ExportFolders;
import gradezzy.config.ModuleSections;
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
import gradezzy.report.ScoreFormatter;
import gradezzy.testcase.TestCaseException;
import gradezzy.testcase.TestCaseSource;
import gradezzy.testcase.TesterFile;
import gradezzy.testcase.TesterInstaller;
import gradezzy.testcase.TypedTestCaseSource;
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

    // Start of the id of the warning on a student who is not on the
    // score sheet, as ExtractStage gives it
    private static final String UNKNOWN_STUDENT_ID_PREFIX = "UNKNOWN-";

    // What the instructor can do about a student not on the score sheet
    private static final String UNKNOWN_STUDENT_ACTION = "Check the upload "
            + "filename and the selected roster.";

    // The audit log actions this service records
    private static final String GRADING_STARTED = "Grading started";
    private static final String GRADING_NOT_STARTED =
            "Grading could not start";
    private static final String SAVE_FAILED = "Save failed";
    private static final String GRADING_CANCELLED = "Grading cancelled";
    private static final String GRADING_RETRIED = "Grading retried";
    private static final String GRADING_RESUMED = "Grading resumed";
    private static final String SCORE_SHEET_CHANGED = "Score sheet changed";
    private static final String STUDENTS_ADDED = "Students added";
    private static final String RESULT_REPLACED = "Result replaced";
    private static final String LATE_UPLOAD_REFUSED = "Late upload refused";
    private static final String SIMILARITY_FAILED =
            "Similar code check failed";

    // The folder, inside paths.output.dir, that keeps each typed run's
    // testers in a folder named after the run
    private static final String TYPED_TESTERS_FOLDER = "generated-testers";

    // Written in the audit log for a student who has no total
    private static final String NOT_GRADED = "not graded";

    // The message when grading, Undo or a late batch is tried during
    // grading
    private static final String STILL_GRADING = "Grading is still "
            + "running. Try again when it has finished.";

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

    // Runs the similar code check after grading, or null to skip it
    private PlagiarismService plagiarismService = null;

    // The flag that stops the current run
    private final CancellationToken cancellationToken =
            new CancellationToken();

    // The last run started, kept so Retry can grade it again
    private GradingRun lastRun = null;

    // The id of the run being graded now, or null while it is still
    // being loaded. Written by the grading thread and read by cancel on
    // the window's thread, so only through the synchronized methods.
    private String currentRunId = null;

    // How many batches, retries or resumes are being graded now. Undo and
    // a late batch wait until it is 0, because they share the stop flag
    // and the run with the grading thread. Only read and changed in the
    // synchronized methods below.
    private int activeGradings = 0;

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
     * Gives the service the similar code check to run when grading ends.
     * When plagiarism.enabled is true, every batch, retry, resume,
     * regrade and late batch is checked before the run is returned, so
     * the console and the window both get the Similar code flags without
     * opening Export.
     *
     * <p>Tier: COULD.
     *
     * @param plagiarismService the check, or null to run none
     */
    public void setPlagiarismService(PlagiarismService plagiarismService) {
        this.plagiarismService = plagiarismService;
    }

    /**
     * Grades a whole batch of submissions for one section against its
     * score sheet: the section's own in paths.score.sheet.by.section, else
     * paths.score.sheet.file. A submission that fails is marked FAILED and
     * does not stop the others.
     *
     * @param inputPaths the zip files or folders the instructor chose
     * @param section the section the batch belongs to
     * @param testCaseSource where the tester files come from
     * @param listener the object told about progress
     * @return the finished run, holding every submission and its results
     * @throws GradingException if the batch cannot start at all, for
     *         example when grading is still running, an argument is
     *         missing, the module or section
     *         is not in the settings, or the score sheet, the uploads or
     *         the testers cannot be read
     */
    public GradingRun startGrading(List<Path> inputPaths, Section section,
            TestCaseSource testCaseSource, ProgressListener listener)
            throws GradingException {
        return startGrading(inputPaths, section, testCaseSource, listener,
                null);
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
     *        the section's one in the settings
     * @return the finished run, holding every submission and its results
     * @throws GradingException if grading is still running, or the
     *         batch cannot start at all
     */
    public GradingRun startGrading(List<Path> inputPaths, Section section,
            TestCaseSource testCaseSource, ProgressListener listener,
            Path scoreSheetFile) throws GradingException {
        return startGrading(inputPaths, section, testCaseSource, listener,
                scoreSheetFile, null);
    }

    /**
     * Starts a run using the roster and the starter folder chosen for
     * this batch; null means the section's score sheet (see
     * ExportFolders.findScoreSheet) or the starter folder in the
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
     *        the section's one in the settings
     * @param starterDirectory the starter folder for this batch, or null
     *        for the one in the settings
     * @return the finished run, holding every submission and its results
     * @throws GradingException if grading is still running, or the
     *         batch cannot start at all
     */
    public GradingRun startGrading(List<Path> inputPaths, Section section,
            TestCaseSource testCaseSource, ProgressListener listener,
            Path scoreSheetFile, Path starterDirectory)
            throws GradingException {
        beginOnlyGrading();
        try {
            return gradeBatch(inputPaths, section, testCaseSource, listener,
                    scoreSheetFile, starterDirectory);
        } finally {
            endGrading();
        }
    }

    // Prepares and grades a whole batch; see startGrading.
    private GradingRun gradeBatch(List<Path> inputPaths, Section section,
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
        checkSimilarity(gradingRun);

        // Saved again so FAILED and CANCELLED submissions, which never
        // reach PersistStage, are on record too, with the Similar code
        // flags
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
        checkTypedCases(testCaseSource);
        setCurrentRunId(null);
        cancellationToken.reset();

        Path rosterFile = scoreSheetFile;
        if (rosterFile == null) {
            rosterFile = findSectionSheet(section);
        }

        // Each question's maximum comes from this batch's testers. Typed
        // test cases set their own maximums when their testers are made,
        // so the tester folder in the settings is not checked for them.
        boolean typed = testCaseSource instanceof TypedTestCaseSource;
        Path testersDirectory = null;
        if (testCaseSource instanceof UploadedTesterSource) {
            UploadedTesterSource uploaded =
                    (UploadedTesterSource) testCaseSource;
            testersDirectory = uploaded.getTestersDirectory();
        }
        if (!typed) {
            List<String> markProblems =
                    assessmentFactory.findMaxScoreProblems(
                            testersDirectory);
            if (!markProblems.isEmpty()) {
                throw new GradingException(markProblems.get(0));
            }
        }

        Assessment assessment = assessmentFactory.create(
                section.getModuleCode(), testersDirectory);
        String runId = RUN_ID_PREFIX + System.currentTimeMillis();
        GradingRun gradingRun = new GradingRun(runId, assessment, section,
                LocalDateTime.now());
        // Kept on the run so the CSV export and Resume use this roster,
        // not the one in the settings
        gradingRun.setScoreSheetFile(rosterFile);
        gradingRun.setStarterDirectory(starterDirectory);
        // Kept so Resume after a restart uses the same testers
        gradingRun.setTestersDirectory(testersDirectory);
        setCurrentRunId(runId);
        GradingContext context = new GradingContext(config, assessment);
        context.setRunId(runId);

        // The roster for this batch is read first, so a bad score sheet
        // stops the run before any zip is extracted.
        readRoster(context, rosterFile);

        List<Submission> submissions = loadSubmissions(inputPaths, section);
        for (Submission submission : submissions) {
            gradingRun.addSubmission(submission);
        }
        addTesters(context, testCaseSource);
        if (typed) {
            keepTypedTesters(gradingRun, context);
        }

        lastRun = gradingRun;
        lastContext = context;
        addSessionRun(gradingRun);
        return gradingRun;
    }

    // Loads the chosen zips and folders as submissions of a section.
    private List<Submission> loadSubmissions(List<Path> inputPaths,
            Section section) throws GradingException {
        try {
            SubmissionLoader loader = new SubmissionLoader(config);
            return loader.loadSubmissions(inputPaths, section);
        } catch (IngestException exception) {
            throw new GradingException("The submissions could not be "
                    + "read: " + exception.getMessage(), exception);
        } catch (IllegalArgumentException exception) {
            // For example a zip name pattern in the settings that is not
            // a valid regular expression, or a path that is not valid
            throw new GradingException("A setting or path is not valid: "
                    + exception.getMessage(), exception);
        }
    }

    // Prepares the testers of a batch and adds them to its context.
    private void addTesters(GradingContext context,
            TestCaseSource testCaseSource) throws GradingException {
        try {
            context.getTesters().addAll(
                    testCaseSource.prepareTesters(context.getAssessment()));
        } catch (TestCaseException exception) {
            throw new GradingException("The testers could not be "
                    + "prepared: " + exception.getMessage(), exception);
        } catch (IllegalArgumentException exception) {
            throw new GradingException("A setting or path is not valid: "
                    + exception.getMessage(), exception);
        }
    }

    // Refuses typed test cases when none were typed: every question
    // would be worth 0 and every student would get an error.
    private void checkTypedCases(TestCaseSource testCaseSource)
            throws GradingException {
        if (!(testCaseSource instanceof TypedTestCaseSource)) {
            return;
        }
        TypedTestCaseSource typedSource =
                (TypedTestCaseSource) testCaseSource;
        if (typedSource.getTestCases().isEmpty()) {
            throw new GradingException("No test case was typed, so there "
                    + "is nothing to grade. Type at least one test case, "
                    + "or choose a tester folder.");
        }
    }

    // Copies the testers made from typed test cases, with their data
    // files, into a folder of this run only, and records that folder on
    // the run. Every typed run writes its testers to the same folder, so
    // Undo, Resume and a rerun read this copy, never another run's.
    private void keepTypedTesters(GradingRun gradingRun,
            GradingContext context) throws GradingException {
        Path runFolder = Paths.get(config.getString(ConfigKeys.OUTPUT_DIR),
                TYPED_TESTERS_FOLDER, gradingRun.getId());
        TesterInstaller installer = new TesterInstaller(config);
        List<TesterFile> copies = new ArrayList<>();
        try {
            Files.createDirectories(runFolder);
            for (TesterFile tester : context.getTesters()) {
                Path source = tester.getSourceFile();
                Path copy = runFolder.resolve(source.getFileName());
                Files.copy(source, copy,
                        StandardCopyOption.REPLACE_EXISTING);
                installer.copyDataFiles(source.getParent(), runFolder);
                copies.add(new TesterFile(tester.getQuestionId(),
                        tester.getClassName(), copy));
            }
        } catch (IOException exception) {
            throw new GradingException("The testers made from the typed "
                    + "test cases could not be kept in " + runFolder
                    + ": " + exception.getMessage(), exception);
        } catch (TestCaseException exception) {
            throw new GradingException("The testers made from the typed "
                    + "test cases could not be kept in " + runFolder
                    + ": " + exception.getMessage(), exception);
        }

        context.getTesters().clear();
        context.getTesters().addAll(copies);
        gradingRun.setTestersDirectory(runFolder);
    }

    // The score sheet of a section: its own one in the settings, else
    // the one for everyone.
    private Path findSectionSheet(Section section) {
        if (section == null) {
            return ExportFolders.findScoreSheet(config, null, null);
        }
        return ExportFolders.findScoreSheet(config, section.getModuleCode(),
                section.getSectionCode());
    }

    // Runs the similar code check on a run whose grading has ended, when
    // plagiarism.enabled is true. A failed check never fails grading:
    // the marks stand and the audit log says why the flags are missing.
    // Returns true when the check ran, so the caller saves the flags.
    private boolean checkSimilarity(GradingRun gradingRun) {
        if (plagiarismService == null
                || !config.getBoolean(ConfigKeys.PLAGIARISM_ENABLED)) {
            return false;
        }

        try {
            plagiarismService.check(gradingRun);
        } catch (RuntimeException exception) {
            auditService.record(SIMILARITY_FAILED, "The similar code "
                    + "check of " + gradingRun.getId() + " stopped: "
                    + exception);
        }
        return true;
    }

    // Keeps the id of the run being graded, for cancel.
    private synchronized void setCurrentRunId(String runId) {
        currentRunId = runId;
    }

    /**
     * Asks the current run to stop after the submission being graded.
     *
     * <p>Tier: SHOULD.
     */
    public synchronized void cancel() {
        if (!cancellationToken.isCancelled()) {
            String detail = "Grading was stopped.";
            if (currentRunId != null) {
                detail = "Grading of " + currentRunId + " was stopped.";
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
     * @throws GradingException if there is no run, grading is still
     *         running, or the run was not the last one started in this
     *         session, so its testers are not loaded
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

        beginOnlyGrading();
        try {
            auditService.record(GRADING_RETRIED, "Grading "
                    + retryRun.getSubmissions().size() + " submission(s) of "
                    + gradingRun.getId() + " again.");
            setCurrentRunId(gradingRun.getId());
            cancellationToken.reset();
            GradingPipeline pipeline = createPipeline(gradingRun);
            pipeline.run(retryRun, lastContext,
                    new RetryProgressListener(listener, gradingRun));
            checkSimilarity(gradingRun);
            saveRun(gradingRun, true);
        } finally {
            endGrading();
        }
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
     * graded with the tester and starter folders saved with it. A run
     * saved before the database kept those folders uses the ones in the
     * settings (paths.testers.dir and paths.starter.dir), and every
     * student graded again gets a warning saying so, because other
     * testers can give other marks.
     *
     * <p>Tier: SHOULD.
     *
     * @param runId the id of the saved run
     * @param listener the object told about progress
     * @return the resumed run, with every submission in it
     * @throws GradingException if the id or listener is missing, grading
     *         is still running, the run is not the last one and cannot be
     *         loaded, or its score sheet or testers cannot be read
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

        beginOnlyGrading();
        try {
            auditService.record(GRADING_RESUMED, "Grading the "
                    + resumeRun.getSubmissions().size() + " unfinished "
                    + "submission(s) of " + gradingRun.getId() + ".");
            setCurrentRunId(gradingRun.getId());
            cancellationToken.reset();
            GradingPipeline pipeline = createPipeline(gradingRun);
            pipeline.run(resumeRun, lastContext,
                    new RetryProgressListener(listener, gradingRun));
            checkSimilarity(gradingRun);
            saveRun(gradingRun, true);
        } finally {
            endGrading();
        }
        return gradingRun;
    }

    /**
     * Grades one student of a run again from the upload, whatever their
     * status, for example after the instructor undid a fix. The fixes in
     * the student's getRejectedFixes list are skipped, so the marks match
     * the files as submitted. The run is saved afterwards.
     *
     * <p>The last run started in this session is graded with its own
     * testers. An earlier run is graded with the score sheet, tester and
     * starter folders recorded on it, as Resume does.
     *
     * <p>Tier: SHOULD.
     *
     * @param gradingRun the run that holds the student
     * @param submission the student to grade again
     * @param listener the object told about progress, or null to tell
     *        nobody
     * @throws GradingException if grading is still running, the student
     *         is not in the run, has an upload that could not be used, or
     *         the run's testers cannot be prepared
     */
    public void regradeSubmission(GradingRun gradingRun,
            Submission submission, ProgressListener listener)
            throws GradingException {
        beginOnlyGrading();
        try {
            regradeAlone(gradingRun, submission, listener);
        } finally {
            endGrading();
        }
    }

    // Grades one student again while nothing else is graded; see
    // regradeSubmission.
    private void regradeAlone(GradingRun gradingRun, Submission submission,
            ProgressListener listener) throws GradingException {
        if (gradingRun == null || submission == null
                || !gradingRun.getSubmissions().contains(submission)) {
            throw new GradingException("That student is not in the run.");
        }
        if (submission.getIngestFailure() != null) {
            throw new GradingException("The upload of "
                    + submission.getUsername() + " could not be used, so "
                    + "it cannot be graded again.");
        }

        ProgressListener progress = listener;
        if (progress == null) {
            progress = new QuietProgressListener();
        }
        GradingContext context = findContext(gradingRun);

        // The student goes through the pipeline on their own, in a run
        // that shares the id, assessment and section.
        GradingRun oneRun = new GradingRun(gradingRun.getId(),
                gradingRun.getAssessment(), gradingRun.getSection(),
                gradingRun.getStartedAt());
        resetForRetry(submission);
        oneRun.addSubmission(submission);

        setCurrentRunId(gradingRun.getId());
        cancellationToken.reset();
        GradingPipeline pipeline = createPipeline(gradingRun);
        try {
            pipeline.run(oneRun, context,
                    new RetryProgressListener(progress, gradingRun));
            // Grading again cleared the student's Similar code flags
            checkSimilarity(gradingRun);
        } finally {
            saveRun(gradingRun, true);
        }
    }

    /**
     * Gives a graded run another score sheet, so its marks go to the
     * right sheet without grading again. Each student is matched to the
     * new sheet by username, as grading does, and the run is saved. A
     * student now on the sheet loses the warning that they are not on
     * it; a student still not on it keeps the warning they had, with any
     * decision made on it, and only a student who had none gets one.
     *
     * <p>Tier: SHOULD.
     *
     * @param gradingRun the run
     * @param scoreSheetFile the score sheet to use from now on
     * @return how many students of the run are on the new sheet
     * @throws GradingException if grading is still running or the sheet
     *         cannot be read
     */
    public int changeScoreSheet(GradingRun gradingRun, Path scoreSheetFile)
            throws GradingException {
        beginOnlyGrading();
        try {
            return changeScoreSheetAlone(gradingRun, scoreSheetFile);
        } finally {
            endGrading();
        }
    }

    // Matches the students of a run to another score sheet while nothing
    // is graded; see changeScoreSheet.
    private int changeScoreSheetAlone(GradingRun gradingRun,
            Path scoreSheetFile) throws GradingException {
        if (gradingRun == null || scoreSheetFile == null) {
            throw new GradingException("Choose a run and a score sheet.");
        }
        List<Student> roster = readStudents(scoreSheetFile);

        RosterReader rosterReader = new RosterReader(config);
        int onSheet = 0;
        for (Submission submission : gradingRun.getSubmissions()) {
            // Grading never matches an upload that could not be used
            if (submission.getIngestFailure() == null) {
                Student student = rosterReader.findByUsername(roster,
                        submission.getUsername());
                submission.setStudent(student);
                if (student != null) {
                    removeUnknownStudent(submission);
                    onSheet++;
                } else if (!hasUnknownStudent(submission)) {
                    // A warning kept from the old sheet keeps its decision
                    addUnknownStudent(submission);
                }
            }
        }

        gradingRun.setScoreSheetFile(scoreSheetFile);
        // Grading a student again in this session reads this roster
        if (gradingRun == lastRun && lastContext != null) {
            lastContext.getRoster().clear();
            lastContext.getRoster().addAll(roster);
        }
        // Named by its section, as the instructor never sees a run's id
        auditService.record(SCORE_SHEET_CHANGED, "The run of "
                + gradingRun.getSection().getLabel() + " now uses "
                + scoreSheetFile + ": " + onSheet + " of "
                + gradingRun.getSubmissions().size() + " submission(s) are "
                + "on it.");
        saveRun(gradingRun, true);
        return onSheet;
    }

    // Says whether a student already has the warning that they are not on
    // the score sheet.
    private boolean hasUnknownStudent(Submission submission) {
        for (Anomaly anomaly : submission.getAnomalies()) {
            if (anomaly.getType() == AnomalyType.UNKNOWN_STUDENT) {
                return true;
            }
        }
        return false;
    }

    // Removes the warnings that a student is not on the score sheet.
    private void removeUnknownStudent(Submission submission) {
        List<Anomaly> unknown = new ArrayList<>();
        for (Anomaly anomaly : submission.getAnomalies()) {
            if (anomaly.getType() == AnomalyType.UNKNOWN_STUDENT) {
                unknown.add(anomaly);
            }
        }
        submission.getAnomalies().removeAll(unknown);
    }

    // Warns that a student is not on the score sheet, as ExtractStage
    // does while grading.
    private void addUnknownStudent(Submission submission) {
        submission.addAnomaly(new Anomaly(UNKNOWN_STUDENT_ID_PREFIX
                + UUID.randomUUID(), AnomalyType.UNKNOWN_STUDENT,
                Severity.WARNING, submission.getUsername(),
                submission.getUsername() + " is not on the roster.",
                UNKNOWN_STUDENT_ACTION));
    }

    /**
     * Says whether a batch, a retry, a resume, an undo or a late batch is
     * being graded now. Each of them is refused while another one is.
     *
     * <p>Tier: SHOULD.
     *
     * @return true while grading is running
     */
    public synchronized boolean isGrading() {
        return activeGradings > 0;
    }

    // Counts one more grading, but only if nothing else is graded now:
    // the gradings share one cancel button, so a second one would reset
    // a cancel meant for the first.
    private synchronized void beginOnlyGrading() throws GradingException {
        if (activeGradings > 0) {
            throw new GradingException(STILL_GRADING);
        }
        activeGradings++;
    }

    // Counts one grading that has ended.
    private synchronized void endGrading() {
        activeGradings--;
    }

    /**
     * Finds the newest run of a module and section: the one started
     * last, from this session's runs and the saved ones. A run of this
     * session is used in place of its saved copy, so its testers stay
     * loaded. Set up uses this to ask whether a late batch should join
     * that run.
     *
     * <p>Tier: SHOULD.
     *
     * @param moduleCode the module code, such as CS102
     * @param sectionCode the section code, such as G3
     * @return the newest run, or null if the section has none
     */
    public GradingRun findNewestRun(String moduleCode, String sectionCode) {
        if (moduleCode == null || sectionCode == null) {
            return null;
        }

        List<GradingRun> sessionCopy = getSessionRuns();
        GradingRun newest = null;
        for (GradingRun gradingRun : sessionCopy) {
            if (isRunOf(gradingRun, moduleCode, sectionCode)
                    && isNewer(gradingRun, newest)) {
                newest = gradingRun;
            }
        }

        for (GradingRun savedRun : listSavedRuns()) {
            if (isRunOf(savedRun, moduleCode, sectionCode)
                    && !hasRunWithId(sessionCopy, savedRun.getId())
                    && isNewer(savedRun, newest)) {
                newest = savedRun;
            }
        }
        return newest;
    }

    /**
     * Grades a late batch into an existing run of the same section, so
     * the section keeps one run for Review, Export and Statistics. A new
     * username is added to the run. A username already in the run is
     * graded again from the new upload, and the new result replaces the
     * old one; the audit log keeps the old total, for example "Replaced
     * alicia.tan.2024: 20 to 18". A late upload that cannot be opened (a
     * damaged zip or a refused duplicate) never replaces a result: the
     * student keeps it and the audit log says why. The run keeps its id
     * and start time.
     *
     * <p>The batch must be graded with the same tester folder and score
     * sheet as the run, because other testers or another roster would
     * make the marks of one section disagree. Otherwise nothing is
     * graded and the reason is given.
     *
     * <p>Tier: SHOULD.
     *
     * @param gradingRun the run to add to, for example from findNewestRun
     * @param inputPaths the zip files or folders of the late batch
     * @param testCaseSource where the tester files come from
     * @param listener the object told about progress
     * @param scoreSheetFile the score sheet chosen for the batch, or null
     *        for the one in the settings
     * @return the same run, now holding the late batch too
     * @throws GradingException if grading is still running, an argument
     *         is missing, the tester folder or score sheet differs from the
     *         run's, or the uploads or testers cannot be read
     */
    public GradingRun addToRun(GradingRun gradingRun, List<Path> inputPaths,
            TestCaseSource testCaseSource, ProgressListener listener,
            Path scoreSheetFile) throws GradingException {
        beginOnlyGrading();
        try {
            return addLateBatch(gradingRun, inputPaths, testCaseSource,
                    listener, scoreSheetFile);
        } finally {
            endGrading();
        }
    }

    // Grades a late batch into a run while nothing else is graded; see
    // addToRun.
    private GradingRun addLateBatch(GradingRun gradingRun,
            List<Path> inputPaths, TestCaseSource testCaseSource,
            ProgressListener listener, Path scoreSheetFile)
            throws GradingException {
        GradingRun batchRun = null;
        List<String> replacedUsernames = new ArrayList<>();
        List<String> oldTotals = new ArrayList<>();
        List<Submission> removedOlder = new ArrayList<>();
        try {
            batchRun = prepareLateBatch(gradingRun, inputPaths,
                    testCaseSource, listener, scoreSheetFile,
                    replacedUsernames, oldTotals, removedOlder);
        } catch (GradingException exception) {
            auditService.record(GRADING_NOT_STARTED, exception.getMessage());
            throw exception;
        }

        auditService.record(STUDENTS_ADDED, "Grading "
                + batchRun.getSubmissions().size() + " submission(s) into "
                + "the earlier run of "
                + gradingRun.getSection().getLabel() + ".");
        saveRun(gradingRun, false);

        // Saved even if grading stops with an error, so the run on
        // record holds the late batch and not the results it replaced
        GradingPipeline pipeline = createPipeline(gradingRun);
        try {
            pipeline.run(batchRun, lastContext,
                    new RetryProgressListener(listener, gradingRun));
        } finally {
            saveRun(gradingRun, true);
        }

        // Written after grading, so each line has the new total too. A
        // late zip that turned out to be damaged gives the student their
        // earlier result back; its working copy was kept, because a zip
        // that cannot be unpacked never replaces one.
        boolean restored = false;
        for (int index = 0; index < replacedUsernames.size(); index++) {
            String username = replacedUsernames.get(index);
            Submission late = findLoaded(gradingRun, username);
            if (isUnusableUpload(late)) {
                putBackOlder(gradingRun, username, removedOlder);
                auditService.record(LATE_UPLOAD_REFUSED, "Kept the earlier "
                        + "result of " + username + " (" + oldTotals.get(index)
                        + "), because the late upload could not be opened.");
                restored = true;
            } else {
                auditService.record(RESULT_REPLACED, "Replaced " + username
                        + ": " + oldTotals.get(index) + " to "
                        + describeTotal(late) + ".");
            }
        }
        // Checked once the run holds the results it keeps, then saved
        // with its flags, as after any other batch
        boolean checked = checkSimilarity(gradingRun);
        if (restored || checked) {
            saveRun(gradingRun, true);
        }
        return gradingRun;
    }

    // Says whether a late upload could not be used at all: it was refused
    // when loaded, or its zip could not be unpacked.
    private boolean isUnusableUpload(Submission late) {
        if (late == null || late.getIngestFailure() != null) {
            return true;
        }
        return hasRejectedZip(late);
    }

    // Says whether ExtractStage refused a submission's zip (a zip bomb,
    // an empty or damaged zip, or a zip slip). Only an ERROR counts: a
    // WARNING of the same type, such as a file left out of the zip, is
    // given to students who were still graded.
    private boolean hasRejectedZip(Submission submission) {
        for (Anomaly anomaly : submission.getAnomalies()) {
            if (anomaly.getType() == AnomalyType.INVALID_ARCHIVE
                    && anomaly.getSeverity() == Severity.ERROR) {
                return true;
            }
        }
        return false;
    }

    // Replaces the late copies of a student with the results they had
    // before the late batch.
    private void putBackOlder(GradingRun gradingRun, String username,
            List<Submission> removedOlder) {
        for (Submission submission : gradingRun.getSubmissions()) {
            if (submission.getUsername().equalsIgnoreCase(username)) {
                gradingRun.removeSubmission(submission);
            }
        }
        for (Submission older : removedOlder) {
            if (older.getUsername().equalsIgnoreCase(username)) {
                gradingRun.addSubmission(older);
            }
        }
    }

    // Checks a late batch, reads its roster and testers, loads its zips
    // and puts them in the run, replacing older results of the same
    // usernames. Each replaced student is added to replacedUsernames,
    // with their old total at the same place in oldTotals, and the
    // submissions taken out go to removedOlder. Nothing in the run
    // changes until every check and file has passed. The returned run
    // holds only the batch, for the pipeline.
    private GradingRun prepareLateBatch(GradingRun gradingRun,
            List<Path> inputPaths, TestCaseSource testCaseSource,
            ProgressListener listener, Path scoreSheetFile,
            List<String> replacedUsernames, List<String> oldTotals,
            List<Submission> removedOlder) throws GradingException {
        if (gradingRun == null) {
            throw new GradingException("Choose the run to add these "
                    + "students to.");
        }
        Section section = gradingRun.getSection();
        checkArguments(inputPaths, section, testCaseSource, listener);
        checkSameFiles(gradingRun, testCaseSource, scoreSheetFile);

        setCurrentRunId(gradingRun.getId());
        cancellationToken.reset();
        GradingContext context = new GradingContext(config,
                gradingRun.getAssessment());
        context.setRunId(gradingRun.getId());
        readRoster(context, findScoreSheet(gradingRun));
        List<Submission> lateSubmissions = loadSubmissions(inputPaths,
                section);
        addTesters(context, testCaseSource);

        // Only a copy that could be opened replaces an earlier result. A
        // late zip that is damaged, or a refused duplicate, never costs a
        // student the marks they already have.
        List<String> lateUsernames = new ArrayList<>();
        for (Submission late : lateSubmissions) {
            if (late.getIngestFailure() == null) {
                lateUsernames.add(late.getUsername().toLowerCase(
                        Locale.ROOT));
            }
        }

        // Older results of the same students leave the run first, so the
        // run never holds two results for one student. The old total is
        // taken before any copy is removed.
        for (Submission older : gradingRun.getSubmissions()) {
            String key = older.getUsername().toLowerCase(Locale.ROOT);
            if (lateUsernames.contains(key)) {
                if (!containsIgnoringCase(replacedUsernames,
                        older.getUsername())) {
                    replacedUsernames.add(older.getUsername());
                    oldTotals.add(describeTotal(findLoaded(gradingRun,
                            older.getUsername())));
                }
                gradingRun.removeSubmission(older);
                removedOlder.add(older);
            }
        }

        GradingRun batchRun = new GradingRun(gradingRun.getId(),
                gradingRun.getAssessment(), section,
                gradingRun.getStartedAt());
        for (Submission late : lateSubmissions) {
            String key = late.getUsername().toLowerCase(Locale.ROOT);
            if (lateUsernames.contains(key)
                    || findLoaded(gradingRun, key) == null) {
                gradingRun.addSubmission(late);
                batchRun.addSubmission(late);
            } else {
                auditService.record(LATE_UPLOAD_REFUSED, "Kept the earlier "
                        + "result of " + late.getUsername() + ", because "
                        + "the late upload could not be used: "
                        + late.getIngestFailure());
            }
        }

        lastRun = gradingRun;
        lastContext = context;
        addSessionRun(gradingRun);
        return batchRun;
    }

    // Refuses a late batch whose tester folder or score sheet is not the
    // run's. A run saved before its tester folder was recorded is taken
    // to use the one in the settings, as Resume does.
    private void checkSameFiles(GradingRun gradingRun,
            TestCaseSource testCaseSource, Path scoreSheetFile)
            throws GradingException {
        String label = gradingRun.getSection().getLabel();

        Path sheet = scoreSheetFile;
        if (sheet == null) {
            sheet = findSectionSheet(gradingRun.getSection());
        }
        Path runSheet = findScoreSheet(gradingRun);
        if (!isSamePath(sheet, runSheet)) {
            throw new GradingException("These students cannot be added to "
                    + "the " + label + " run: it was graded against the "
                    + "score sheet " + runSheet + ", not " + sheet + ". "
                    + "Choose that score sheet, or start a new run.");
        }

        if (!(testCaseSource instanceof UploadedTesterSource)) {
            throw new GradingException("These students cannot be added to "
                    + "the " + label + " run: only a tester folder can be "
                    + "checked against the one the run used. Choose the "
                    + "run's tester folder, or start a new run.");
        }
        UploadedTesterSource uploaded = (UploadedTesterSource) testCaseSource;
        Path testers = uploaded.getTestersDirectory();
        Path runTesters = gradingRun.getTestersDirectory();
        if (runTesters == null) {
            runTesters = Paths.get(config.getString(ConfigKeys.TESTERS_DIR));
        }
        if (!isSamePath(testers, runTesters)) {
            throw new GradingException("These students cannot be added to "
                    + "the " + label + " run: it was graded with the "
                    + "testers in " + runTesters + ", not " + testers
                    + ". Choose that tester folder, or start a new run.");
        }
    }

    // The score sheet a run was graded against, or its section's one in
    // the settings for a run saved before the sheet was recorded, as the
    // Export warning and the CSV export choose it.
    private Path findScoreSheet(GradingRun gradingRun) {
        Path scoreSheetFile = gradingRun.getScoreSheetFile();
        if (scoreSheetFile == null) {
            scoreSheetFile = findSectionSheet(gradingRun.getSection());
        }
        return scoreSheetFile;
    }

    // Says whether two paths name the same file or folder. Real paths
    // are compared when both exist, so a link or a different spelling of
    // the same folder still matches.
    private boolean isSamePath(Path first, Path second) {
        if (first == null || second == null) {
            return first == null && second == null;
        }
        try {
            return first.toRealPath().equals(second.toRealPath());
        } catch (IOException exception) {
            // One of them is missing, so compare the names as written
            return first.toAbsolutePath().normalize().equals(
                    second.toAbsolutePath().normalize());
        }
    }

    // The shared information to grade a run again with: the last run's
    // own, or one built from the files recorded on an earlier run.
    private GradingContext findContext(GradingRun gradingRun)
            throws GradingException {
        if (gradingRun == lastRun && lastContext != null) {
            return lastContext;
        }
        return prepareSavedContext(gradingRun);
    }

    // The saved runs, or an empty list when the database is off or
    // cannot be read; this session's runs are still searched.
    private List<GradingRun> listSavedRuns() {
        if (repository == null
                || !config.getBoolean(ConfigKeys.DATABASE_ENABLED)) {
            return new ArrayList<>();
        }
        try {
            return repository.listRuns();
        } catch (PersistenceException exception) {
            return new ArrayList<>();
        }
    }

    // Says whether a run belongs to a module and section, ignoring case.
    private boolean isRunOf(GradingRun gradingRun, String moduleCode,
            String sectionCode) {
        Section section = gradingRun.getSection();
        if (section == null) {
            return false;
        }
        return moduleCode.equalsIgnoreCase(section.getModuleCode())
                && sectionCode.equalsIgnoreCase(section.getSectionCode());
    }

    // Says whether a run started after another, or the other is null.
    private boolean isNewer(GradingRun gradingRun, GradingRun other) {
        if (other == null) {
            return true;
        }
        if (gradingRun.getStartedAt() == null) {
            return false;
        }
        if (other.getStartedAt() == null) {
            return true;
        }
        return gradingRun.getStartedAt().isAfter(other.getStartedAt());
    }

    // Says whether a list holds a run with this id.
    private boolean hasRunWithId(List<GradingRun> gradingRuns, String runId) {
        for (GradingRun gradingRun : gradingRuns) {
            if (gradingRun.getId().equals(runId)) {
                return true;
            }
        }
        return false;
    }

    // Says whether a list of usernames holds this one, ignoring case.
    private boolean containsIgnoringCase(List<String> usernames,
            String username) {
        for (String listed : usernames) {
            if (listed.equalsIgnoreCase(username)) {
                return true;
            }
        }
        return false;
    }

    // Finds a student's submission in a run, ignoring case. A copy whose
    // upload was refused is used only if no other copy was loaded, as the
    // CSV export does.
    private Submission findLoaded(GradingRun gradingRun, String username) {
        Submission found = null;
        for (Submission submission : gradingRun.getSubmissions()) {
            if (submission.getUsername().equalsIgnoreCase(username)
                    && (found == null || found.getIngestFailure() != null)) {
                found = submission;
            }
        }
        return found;
    }

    // The total of a student for the audit log, or "not graded".
    private String describeTotal(Submission submission) {
        if (submission == null
                || submission.getStatus() != SubmissionStatus.GRADED) {
            return NOT_GRADED;
        }
        return ScoreFormatter.format(submission.getTotalScore());
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
        context.getRoster().addAll(readStudents(scoreSheetFile));
    }

    // Reads the students of a score sheet.
    private List<Student> readStudents(Path scoreSheetFile)
            throws GradingException {
        try {
            RosterReader rosterReader = new RosterReader(config);
            return rosterReader.readRoster(scoreSheetFile);
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
        readRoster(context, findScoreSheet(savedRun));

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

        if (!isModuleSection(section)) {
            throw new GradingException("Section '"
                    + section.getSectionCode() + "' is not a section of "
                    + section.getModuleCode() + " in the settings "
                    + ConfigKeys.SECTION_CODES + " and "
                    + ConfigKeys.MODULE_SECTION_CODES + ".");
        }
    }

    // Says whether a section belongs to its module: one every module has,
    // unless the module has only its own sections, or one of its own.
    private boolean isModuleSection(Section section) {
        String byModule = "";
        if (config.hasKey(ConfigKeys.MODULE_SECTION_CODES)) {
            byModule = config.getString(ConfigKeys.MODULE_SECTION_CODES);
        }
        String ownOnly = "";
        if (config.hasKey(ConfigKeys.OWN_SECTIONS_ONLY_MODULES)) {
            ownOnly = config.getString(ConfigKeys.OWN_SECTIONS_ONLY_MODULES);
        }
        for (String code : ModuleSections.forModule(config.getString(
                ConfigKeys.SECTION_CODES), byModule, ownOnly,
                section.getModuleCode())) {
            if (code.equalsIgnoreCase(section.getSectionCode())) {
                return true;
            }
        }
        return false;
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
    // A refused upload or zip is left out: grading the same file again
    // cannot help. After a zip limit is raised, grade the batch again.
    private boolean needsRetry(Submission submission) {
        if (submission.getIngestFailure() != null
                || hasRejectedZip(submission)) {
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
    // The fixes the instructor undid (getRejectedFixes) are kept too, so
    // FixManager skips them again.
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
