/*
 * File: LiveBackend.java
 *
 * Purpose: Connects the screens to the real services of GradeZZY.
 *
 * Where it fits: The default backend, used by "sh run.sh". Each method
 * calls one service or reads a setting, else returns Not connected yet.
 * Review decisions on flags and checked 0s are kept here in memory, and
 * saved through AnomalyService so they are read back after a restart.
 */
package gradezzy.ui.backend;

import java.io.IOException;
import java.nio.file.DirectoryStream;
import java.nio.file.Files;
import java.nio.file.LinkOption;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.util.ArrayList;
import java.util.Collections;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.concurrent.ConcurrentHashMap;

import javafx.scene.control.TreeItem;

import gradezzy.config.AppConfig;
import gradezzy.config.ConfigKeys;
import gradezzy.config.ModuleSections;
import gradezzy.exec.ProcessRunner;
import gradezzy.exec.SourceCompiler;
import gradezzy.fix.FixRecord;
import gradezzy.grading.GradingException;
import gradezzy.grading.ProgressListener;
import gradezzy.grading.QuestionRerunner;
import gradezzy.grading.CompileStage;
import gradezzy.ingest.IngestException;
import gradezzy.ingest.RosterReader;
import gradezzy.model.Anomaly;
import gradezzy.model.AnomalyType;
import gradezzy.model.Assessment;
import gradezzy.model.AuditEntry;
import gradezzy.model.FixStatus;
import gradezzy.model.GradingRun;
import gradezzy.model.Question;
import gradezzy.model.QuestionResult;
import gradezzy.model.SavedAssessment;
import gradezzy.model.ScoreAdjustment;
import gradezzy.model.Section;
import gradezzy.model.Student;
import gradezzy.model.Submission;
import gradezzy.model.SubmissionStatus;
import gradezzy.plagiarism.SimilarityPair;
import gradezzy.report.ExportException;
import gradezzy.report.ScoreFormatter;
import gradezzy.service.AnomalyService;
import gradezzy.service.AssessmentFactory;
import gradezzy.service.AssessmentLibrary;
import gradezzy.service.ClassFileMatch;
import gradezzy.service.ClassFileMatcher;
import gradezzy.service.ReapplyResult;
import gradezzy.service.ScoreAdjustmentService;
import gradezzy.service.ServiceRegistry;
import gradezzy.service.UndoResult;
import gradezzy.stats.StatisticsFilter;
import gradezzy.stats.StatisticsSummary;
import gradezzy.testcase.TestCase;
import gradezzy.testcase.TestCaseSource;
import gradezzy.testcase.TesterGenerator;
import gradezzy.testcase.TypedTestCaseSource;
import gradezzy.testcase.UploadedTesterSource;
import gradezzy.ui.GradingTask;
import gradezzy.ui.GradingTaskKind;
import gradezzy.util.FileUtils;
import gradezzy.util.InputValidator;

/**
 * The backend that calls the real services.
 */
public class LiveBackend implements DashboardBackend {

    // The extension of a score sheet file
    private static final String CSV_EXTENSION = ".csv";

    // The folder for generated testers, inside the output folder
    private static final String GENERATED_TESTERS_FOLDER =
            "generated-testers";

    // The separator between a question's folder and file, as in Q1/Q1a.java
    private static final String FOLDER_SEPARATOR = "/";

    // How many folders deep an answer file is looked for: the student's
    // folder, and one wrapper folder around it
    private static final int SOURCE_SEARCH_DEPTH = 3;

    // How PlagiarismService writes a Similar code flag: its id is
    // SIMILAR-<run>/<student>/<other student>/<question>, and its message
    // starts "<question> is <percent>% similar"
    private static final String SIMILAR_ID_PREFIX = "SIMILAR-";
    private static final String SIMILAR_ID_SEPARATOR = "/";
    private static final int SIMILAR_ID_PARTS = 3;
    private static final int STUDENT_PART = 0;
    private static final int OTHER_STUDENT_PART = 1;
    private static final int QUESTION_PART = 2;
    private static final String SIMILAR_MESSAGE_MIDDLE = " is ";
    private static final String PERCENT_SIGN = "%";

    // Turns a percentage into a similarity from 0 to 1
    private static final double PERCENT = 100.0;

    // Every service of the application
    private final ServiceRegistry services;

    // The application settings
    private final AppConfig config;

    // The assessment in use, built from the configuration again whenever
    // another one is chosen; read by the file-checking thread too
    private volatile Assessment assessment;

    // Says what grading will do with a chosen zip file or folder
    private final SubmissionPreview preview;

    // Latest run per section, filled when a grading task ends; a restore
    // clears it. Both happen on the JavaFX thread.
    private final Map<String, GradingRun> runsBySection =
            new ConcurrentHashMap<>();

    // The saved runs, newest first, read once from the database and read
    // again after anything that changes them; null until first needed
    private List<GradingRun> savedRuns = null;

    // The id of the run being graded again in the background, or null
    private String runGradedAgainId = null;

    // The last run this session started or resumed, the only run whose
    // testers GradingService keeps for Retry; null until one finishes
    private GradingRun lastRunStarted = null;

    // The review decisions, by student and anomaly id: those made in this
    // session and those read back with the saved runs
    private final Map<String, ReviewDecision> decisions =
            new ConcurrentHashMap<>();

    // The ids of the anomalies found by runs started in this session
    private final Set<String> anomaliesFoundNow =
            ConcurrentHashMap.newKeySet();

    // The ids of the runs started in this session
    private final Set<String> runsStartedNow = ConcurrentHashMap.newKeySet();

    /**
     * Creates a live backend.
     *
     * @param services every service
     */
    public LiveBackend(ServiceRegistry services) {
        this.services = services;
        this.config = services.getConfig();
        this.assessment = new AssessmentFactory(config).create();
        this.preview = new SubmissionPreview(config);
    }

    /**
     * Returns false, because this backend uses the real services.
     */
    @Override
    public boolean isDemo() {
        return false;
    }

    /**
     * Allows every feature that has a service method.
     */
    @Override
    public boolean supports(Capability capability) {
        return capability.isWired();
    }



    /**
     * Returns the module codes listed in config.properties.
     */
    @Override
    public List<String> getModuleCodes() {
        return config.getList(ConfigKeys.MODULE_CODES);
    }

    /**
     * Returns the sections listed in config.properties.
     */
    @Override
    public List<String> getSectionCodes(String moduleCode) {
        String byModule = null;
        if (config.hasKey(ConfigKeys.MODULE_SECTION_CODES)) {
            byModule = config.getString(ConfigKeys.MODULE_SECTION_CODES);
        }
        String ownOnly = null;
        if (config.hasKey(ConfigKeys.OWN_SECTIONS_ONLY_MODULES)) {
            ownOnly = config.getString(ConfigKeys.OWN_SECTIONS_ONLY_MODULES);
        }
        return ModuleSections.forModule(config.getString(
                ConfigKeys.SECTION_CODES), byModule, ownOnly, moduleCode);
    }

    /**
     * Returns the assessment built from the configuration.
     */
    @Override
    public Assessment getAssessment() {
        return assessment;
    }

    /**
     * Calls AssessmentFactory.create with the tester folder.
     */
    @Override
    public Assessment getAssessment(Path testersDirectory) {
        // Read now, even for the folder in Settings, so the marks match
        // what checkMaxScores reads at the same moment
        return new AssessmentFactory(config).create(
                assessment.getModuleCode(), testersDirectory);
    }

    /**
     * Calls AssessmentFactory.findMaxScoreProblems.
     */
    @Override
    public List<String> checkMaxScores(Path testersDirectory) {
        return new AssessmentFactory(config).findMaxScoreProblems(
                testersDirectory);
    }

    /**
     * Calls RosterReader.readDenominator on the score sheet, or the one in
     * Settings when none is given.
     */
    @Override
    public double getScoreSheetDenominator(Path scoreSheetFile) {
        Path sheet = scoreSheetFile;
        if (sheet == null) {
            sheet = Paths.get(config.getString(
                    ConfigKeys.SCORE_SHEET_FILE));
        }
        return new RosterReader(config).readDenominator(sheet);
    }

    /**
     * Returns exec.run.timeout.seconds.
     */
    @Override
    public int getRunTimeoutSeconds() {
        return config.getInt(ConfigKeys.RUN_TIMEOUT_SECONDS);
    }

    /**
     * Returns exec.compile.timeout.seconds.
     */
    @Override
    public int getCompileTimeoutSeconds() {
        return config.getInt(ConfigKeys.COMPILE_TIMEOUT_SECONDS);
    }

    /**
     * Returns this session's run for the section, else its newest saved run.
     * A run being graded again is never returned, because the grading
     * threads are changing it; its saved copy is returned instead.
     */
    @Override
    public GradingRun getRun(Section section) {
        if (section == null) {
            return null;
        }

        GradingRun recent = runsBySection.get(section.getLabel());
        if (recent != null && !isGradedAgainNow(recent)) {
            return recent;
        }
        return findNewestSavedRun(section.getLabel());
    }

    /**
     * Lists the runs from HistoryService.listRuns, giving this session's
     * run in place of its saved copy, so Retry works on a run opened from
     * the Runs tab. A run is current when getRun shows it for its
     * section. No run is restorable on its own, because no service
     * restores one run, so no backup is described.
     */
    @Override
    public List<RunRecord> listRuns() {
        List<RunRecord> records = new ArrayList<>();
        for (GradingRun saved : getSavedRuns()) {
            GradingRun run = findSessionRun(saved);
            records.add(new RunRecord(run, describeRun(run), "", false,
                    isShownNow(run)));
        }
        return records;
    }

    /**
     * Returns an empty list, because live mode uses the system file picker.
     */
    @Override
    public List<Path> getSampleFiles(InputKind kind) {
        return new ArrayList<>();
    }

    /**
     * Returns the path config.properties names for the group.
     */
    @Override
    public Path getConfiguredPath(InputKind kind) {
        return configuredPath(kind);
    }

    // The score sheet a run used, else its section's own score sheet,
    // else the one for everyone.
    private Path scoreSheetOf(GradingRun gradingRun) {
        if (gradingRun.getScoreSheetFile() != null) {
            return gradingRun.getScoreSheetFile();
        }
        try {
            return Paths.get(getScoreSheet(gradingRun.getSection()));
        } catch (java.nio.file.InvalidPathException exception) {
            return configuredPath(InputKind.ROSTER);
        }
    }

    // Reads a path setting.
    private Path configuredPath(InputKind kind) {
        if (kind == InputKind.ROSTER) {
            return Paths.get(config.getString(ConfigKeys.SCORE_SHEET_FILE));
        } else if (kind == InputKind.TESTERS) {
            return Paths.get(config.getString(ConfigKeys.TESTERS_DIR));
        } else if (kind == InputKind.STARTER) {
            return Paths.get(config.getString(ConfigKeys.STARTER_DIR));
        }
        return null;
    }

    /**
     * Checks the path with InputValidator and its file name. Student zips
     * and folders are checked with SubmissionPreview, and a starter folder
     * for the starter file of each question.
     */
    @Override
    public ImportItem inspect(InputKind kind, Path path) {
        String name = String.valueOf(path.getFileName());

        if (kind == InputKind.SUBMISSION) {
            return inspectSubmission(path);
        }

        if (kind == InputKind.ROSTER) {
            if (!InputValidator.isReadableFile(path)) {
                return unreadable(kind, path);
            }
            if (!name.toLowerCase().endsWith(CSV_EXTENSION)) {
                return new ImportItem(kind, path, "", ImportState.INVALID,
                        "Choose the score sheet CSV file.");
            }
            return new ImportItem(kind, path, "", ImportState.ACCEPTED,
                    "Score sheet found.");
        }

        if (!InputValidator.isReadableDirectory(path)) {
            return unreadable(kind, path);
        }
        if (kind == InputKind.STARTER) {
            return inspectStarter(path);
        }
        return new ImportItem(kind, path, "", ImportState.ACCEPTED,
                "Folder found.");
    }

    /**
     * Calls CompileStage.checkTesterFolder with the chosen starter folder,
     * or the one in Settings when none is chosen. A folder that is missing
     * or holds no tester is one short problem, and a full path in a
     * problem is shortened to the folder's name.
     */
    @Override
    public List<String> checkTesters(Path testersDirectory,
            Path starterDirectory) {
        List<String> problems = new ArrayList<>();
        String folderName = String.valueOf(testersDirectory.getFileName());
        if (!Files.isDirectory(testersDirectory)) {
            problems.add("The tester folder " + folderName
                    + " could not be found.");
            return problems;
        }
        if (!holdsTester(testersDirectory)) {
            problems.add(folderName + " holds no tester files, such as "
                    + firstTesterName() + ".");
            return problems;
        }

        CompileStage stage = new CompileStage(new SourceCompiler(config,
                new ProcessRunner(config)), starterDirectory);
        List<String> found = new ArrayList<>();
        try {
            found = stage.checkTesterFolder(config, assessment,
                    testersDirectory);
        } catch (GradingException exception) {
            found.add("The testers could not be checked: "
                    + exception.getMessage());
        }
        String fullPath = testersDirectory.toString();
        for (String problem : found) {
            problems.add(problem.replace(fullPath, folderName));
        }
        return problems;
    }

    /**
     * Calls ClassFileMatcher with the uploads' paths and usernames, for
     * the assessment in use.
     */
    @Override
    public ClassFileMatch checkClassFileMatch(InputKind kind, Path chosen,
            List<ImportItem> submissions) {
        List<Path> uploads = new ArrayList<>();
        List<String> usernames = new ArrayList<>();
        for (ImportItem item : submissions) {
            uploads.add(item.getPath());
            usernames.addAll(item.getStudents());
        }

        ClassFileMatcher matcher = new ClassFileMatcher(config,
                getAssessment());
        if (kind == InputKind.ROSTER) {
            return matcher.checkScoreSheet(chosen, usernames, uploads);
        } else if (kind == InputKind.TESTERS) {
            return matcher.checkTesters(chosen, uploads);
        } else if (kind == InputKind.STARTER) {
            return matcher.checkStarter(chosen, uploads);
        }
        return ClassFileMatch.fits();
    }

    /**
     * Calls ClassFileMatcher with the run's score sheet, the usernames of
     * its graded students and their uploads.
     */
    @Override
    public ClassFileMatch findBetterScoreSheet(GradingRun gradingRun) {
        if (gradingRun == null) {
            return ClassFileMatch.fits();
        }
        Path sheet = scoreSheetOf(gradingRun);

        List<String> usernames = new ArrayList<>();
        List<Path> uploads = new ArrayList<>();
        for (Submission submission : gradingRun.getSubmissions()) {
            if (ResultState.hasTotal(submission)) {
                usernames.add(submission.getUsername());
            }
            Path upload = submission.getOriginalPath();
            if (upload != null && !uploads.contains(upload)) {
                uploads.add(upload);
            }
        }
        ClassFileMatcher matcher = new ClassFileMatcher(config,
                gradingRun.getAssessment());
        return matcher.checkScoreSheet(sheet, usernames, uploads,
                "graded student");
    }

    /**
     * Calls GradingService.changeScoreSheet.
     */
    @Override
    public ActionResult changeScoreSheet(GradingRun gradingRun,
            Path scoreSheet) {
        try {
            int onSheet = services.getGradingService().changeScoreSheet(
                    gradingRun, scoreSheet);
            return ActionResult.done("The marks now go to "
                    + scoreSheet.getFileName() + ", which lists " + onSheet
                    + " of the " + gradingRun.getSubmissions().size()
                    + " students.");
        } catch (GradingException exception) {
            return ActionResult.failed(exception.getMessage());
        }
    }

    // Says whether a folder holds at least one tester file.
    private boolean holdsTester(Path folder) {
        String ending = config.getString(ConfigKeys.TESTER_CLASS_SUFFIX)
                + ".java";
        try {
            for (Path file : FileUtils.listFiles(folder, ".java")) {
                if (file.getFileName().toString().endsWith(ending)) {
                    return true;
                }
            }
        } catch (IOException exception) {
            return false;
        }
        return false;
    }

    // Returns the first question's tester file name, such as Q1aTester.java.
    private String firstTesterName() {
        String suffix = config.getString(ConfigKeys.TESTER_CLASS_SUFFIX)
                + ".java";
        if (assessment == null || assessment.getQuestions().isEmpty()) {
            return suffix;
        }
        return assessment.getQuestions().get(0).getId() + suffix;
    }

    /**
     * Reads the run's score sheet with RosterReader, or the one in
     * Settings when the run has none, and keeps the students with no
     * submission in the run.
     */
    @Override
    public List<Student> listMissingStudents(GradingRun gradingRun) {
        List<Student> missing = new ArrayList<>();
        if (gradingRun == null) {
            return missing;
        }

        Path scoreSheet = scoreSheetOf(gradingRun);

        List<Student> roster = new ArrayList<>();
        try {
            roster = new RosterReader(config).readRoster(scoreSheet);
        } catch (IngestException exception) {
            return missing;
        }

        for (Student student : roster) {
            if (!hasSubmission(gradingRun, student.getUsername())) {
                missing.add(student);
            }
        }
        return missing;
    }

    // Says whether the run has a submission of a username.
    private boolean hasSubmission(GradingRun gradingRun, String username) {
        for (Submission submission : gradingRun.getSubmissions()) {
            if (submission.getUsername().equalsIgnoreCase(username)) {
                return true;
            }
        }
        return false;
    }

    /**
     * Starts GradingService.startGrading in a GradingTask. A starter
     * folder that cannot be used is not passed, so grading uses the one
     * in Settings, as its row on Set up says.
     */
    @Override
    public void startGrading(ImportBatch batch, Section section,
            ProgressListener listener, RunCallback callback) {
        GradingTask task = new GradingTask(services.getGradingService(),
                batch.getSubmissionPaths(), section,
                createTestCaseSource(batch), listener,
                batch.getSinglePath(InputKind.ROSTER),
                findUsableStarter(batch));
        runInBackground(task, callback, Capability.IMPORT_AND_GRADE);
    }

    /**
     * Calls GradingService.findNewestRun. A run with no students has
     * nothing to join, so it gives null.
     */
    @Override
    public GradingRun findRunToJoin(Section section) {
        if (section == null) {
            return null;
        }
        GradingRun run = services.getGradingService().findNewestRun(
                section.getModuleCode(), section.getSectionCode());
        if (run == null || run.getSubmissions().isEmpty()) {
            return null;
        }
        return run;
    }

    /**
     * Starts GradingService.addToRun in a GradingTask. The service refuses
     * a tester folder or score sheet that is not the run's, and says why.
     */
    @Override
    public void addToRun(GradingRun gradingRun, ImportBatch batch,
            ProgressListener listener, RunCallback callback) {
        GradingTask task = new GradingTask(services.getGradingService(),
                gradingRun, batch.getSubmissionPaths(),
                createTestCaseSource(batch), listener,
                batch.getSinglePath(InputKind.ROSTER));
        setRunGradedAgain(gradingRun.getId());
        runInBackground(task, callback, Capability.LATE_BATCH);
    }

    /**
     * Calls GradingService.cancel.
     */
    @Override
    public void cancelGrading() {
        services.getGradingService().cancel();
    }

    /**
     * Starts GradingService.resume in a GradingTask.
     */
    @Override
    public void resumeGrading(GradingRun gradingRun,
            ProgressListener listener, RunCallback callback) {
        GradingTask task = new GradingTask(services.getGradingService(),
                GradingTaskKind.RESUME, gradingRun, listener);
        setRunGradedAgain(gradingRun.getId());
        runInBackground(task, callback, Capability.RESUME_RUN);
    }

    /**
     * Starts GradingService.retryFailed in a GradingTask.
     */
    @Override
    public void retryFailedSubmissions(GradingRun gradingRun,
            ProgressListener listener, RunCallback callback) {
        GradingTask task = new GradingTask(services.getGradingService(),
                GradingTaskKind.RETRY_FAILED, gradingRun, listener);
        setRunGradedAgain(gradingRun.getId());
        runInBackground(task, callback, Capability.RETRY_FAILED);
    }

    /**
     * Says whether the run is the last one this session started or
     * resumed. GradingService.retryFailed refuses any other run, because
     * its testers are not loaded.
     */
    @Override
    public synchronized boolean canRetryFailed(GradingRun gradingRun) {
        return gradingRun != null && gradingRun == lastRunStarted;
    }

    /**
     * Returns Not connected yet: no service retries one question.
     */
    @Override
    public ActionResult retryQuestion(Submission submission,
            String questionId) {
        return ActionResult.notConnected(Capability.RETRY_QUESTION);
    }

    /**
     * Returns Not connected yet: no service retries one submission.
     */
    @Override
    public ActionResult retrySubmission(Submission submission) {
        return ActionResult.notConnected(Capability.RETRY_SUBMISSION);
    }

    /**
     * Calls AnomalyService.listAnomalies.
     */
    @Override
    public List<Anomaly> listAnomalies(GradingRun gradingRun) {
        return services.getAnomalyService().listAnomalies(gradingRun);
    }

    /**
     * Returns the decision made in this session or saved with the run,
     * or OPEN.
     */
    @Override
    public ReviewDecision getDecision(Anomaly anomaly) {
        return decisions.getOrDefault(decisionKey(anomaly),
                ReviewDecision.OPEN);
    }

    /**
     * Records the decision and saves it with AnomalyService.saveDecision,
     * so it is kept after a restart. When it cannot be saved, it is kept
     * until GradeZZY closes and the message says so.
     */
    @Override
    public ActionResult setDecision(Anomaly anomaly,
            ReviewDecision decision) {
        if (anomaly == null || decision == null) {
            return ActionResult.failed("Choose a flag and a decision.");
        }

        ReviewDecision before = getDecision(anomaly);
        String savedName = null;
        if (decision == ReviewDecision.OPEN) {
            decisions.remove(decisionKey(anomaly));
        } else {
            decisions.put(decisionKey(anomaly), decision);
            savedName = decision.name();
        }
        boolean saved = services.getAnomalyService().saveDecision(anomaly,
                savedName);
        // Every decision is in the activity log, so it can be undone
        // there; the id tells it apart from the same flag in another run
        if (before != decision) {
            services.getAuditService().record(decision.getAuditAction(),
                    AuditEntry.withReference(anomaly.getUsername() + ": "
                    + anomaly.getMessage(), anomaly.getId()));
        }
        String message = "Marked the flag for " + anomaly.getUsername()
                + " as " + decision.getLabel().toLowerCase()
                + ". No mark changed.";
        if (!saved) {
            message = message + " It could not be saved, so it is kept "
                    + "only until GradeZZY closes.";
        }
        return ActionResult.done(message);
    }

    /**
     * Marks a student's 0 as checked, or takes that back, and saves it
     * with AnomalyService.saveZeroCheck so it is kept after a restart.
     * The activity line names the run, so History's Undo finds the right
     * student when a section was graded twice.
     */
    @Override
    public ActionResult setZeroChecked(Submission submission,
            boolean checked) {
        if (submission == null) {
            return ActionResult.failed("Choose a student first.");
        }

        GradingRun run = findRunHolding(submission);
        boolean saved = services.getAnomalyService().saveZeroCheck(run,
                submission, checked);
        String runId = null;
        if (run != null) {
            runId = run.getId();
        }
        if (ZeroChecks.set(submission, checked)) {
            String action = ReviewDecision.AUDIT_TAKEN_BACK;
            if (checked) {
                action = ReviewDecision.AUDIT_CHECKED;
            }
            recordActivity(action, AuditEntry.withReference(
                    submission.getUsername() + ": " + ZERO_CHECK_WORDS,
                    runId));
        }
        String message = "The 0 of " + submission.getUsername()
                + " waits for a look again.";
        if (checked) {
            message = "Marked the 0 of " + submission.getUsername()
                    + " as checked. No mark changed.";
        }
        if (!saved) {
            message = message + " It could not be saved, so it is kept "
                    + "only until GradeZZY closes.";
        }
        return ActionResult.done(message);
    }

    /**
     * Applies ReviewRules.needsReview.
     */
    @Override
    public boolean needsReview(Anomaly anomaly) {
        return ReviewRules.needsReview(anomaly, getDecision(anomaly));
    }

    /**
     * Lists the validation findings among the run's anomalies, with the
     * Before and After of each fix made in this session.
     */
    @Override
    public List<RepairItem> listRepairs(GradingRun gradingRun) {
        List<RepairItem> repairs = new ArrayList<>();
        for (Anomaly anomaly : listAnomalies(gradingRun)) {
            if (ReviewRules.isValidationFinding(anomaly.getType())) {
                repairs.add(createRepair(anomaly));
            }
        }
        return repairs;
    }

    // Builds the repair item of a finding. A fix not made in this
    // session has no Before and After, so the panel hides them.
    private RepairItem createRepair(Anomaly anomaly) {
        FixRecord fixRecord =
                services.getAnomalyService().findFixRecord(anomaly);
        String before = null;
        String after = null;
        if (fixRecord != null) {
            before = fixRecord.getValueBefore();
            after = fixRecord.getValueAfter();
        }
        return new RepairItem(anomaly, before, after,
                describeNoRepair(anomaly));
    }

    /**
     * Calls AnomalyService.approveFix and checks the fix status changed.
     */
    @Override
    public ActionResult approveRepair(RepairItem repair) {
        Anomaly anomaly = repair.getAnomaly();
        services.getAnomalyService().approveFix(anomaly);
        // The new status is saved, so saved copies of the run are stale
        forgetSavedRuns();

        if (anomaly.getFixStatus() == FixStatus.APPROVED) {
            return ActionResult.done("Approved the fix for "
                    + anomaly.getUsername() + ".");
        }
        return ActionResult.failed("The fix for " + anomaly.getUsername()
                + " was not approved, because it is "
                + describeFixStatus(anomaly.getFixStatus()) + ".");
    }

    /**
     * Calls AnomalyService.undoFix for an applied or approved repair and
     * checks the fix status changed. Any other repair is left alone.
     */
    @Override
    public ActionResult rejectRepair(RepairItem repair) {
        Anomaly anomaly = repair.getAnomaly();
        FixStatus before = anomaly.getFixStatus();
        if (before != FixStatus.APPLIED && before != FixStatus.APPROVED) {
            return ActionResult.failed("The fix for "
                    + anomaly.getUsername() + " was not undone, because "
                    + "it is " + describeFixStatus(before) + ".");
        }

        GradingRun run = findSessionRunWith(anomaly);
        UndoResult undone = services.getAnomalyService().undoFix(anomaly);
        forgetSavedRuns();
        if (anomaly.getFixStatus() == FixStatus.UNDONE) {
            return ActionResult.done(describeUndo(undone, anomaly)
                    + checkAfterUndo(run));
        }
        return ActionResult.failed(explainUndoFailure(anomaly));
    }

    // Says what an undo did, with the marks before and after when the
    // service says them, such as "Fix undone. Marks 20 to 16."
    private String describeUndo(UndoResult undone, Anomaly anomaly) {
        String putBack = "The working copy of " + anomaly.getUsername()
                + " was put back.";
        if (undone == null) {
            return "Undid the fix. " + putBack;
        }
        return undone.getMessage() + " " + putBack;
    }

    /**
     * Calls AnomalyService.canReapplyFix.
     */
    @Override
    public boolean canReapplyRepair(Anomaly anomaly) {
        return services.getAnomalyService().canReapplyFix(anomaly);
    }

    /**
     * Calls AnomalyService.reapplyFix, which grades the student again
     * with the fix, and says what the marks became.
     */
    @Override
    public ActionResult reapplyRepair(RepairItem repair) {
        Anomaly anomaly = repair.getAnomaly();
        if (!canReapplyRepair(anomaly)) {
            return ActionResult.failed(explainReapplyFailure(anomaly));
        }

        GradingRun run = findSessionRunWith(anomaly);
        ReapplyResult reapplied =
                services.getAnomalyService().reapplyFix(anomaly);
        forgetSavedRuns();
        if (reapplied == null) {
            return ActionResult.failed(explainReapplyFailure(anomaly));
        }
        if (!reapplied.getProblem().isEmpty()) {
            return ActionResult.failed(reapplied.getMessage());
        }
        return ActionResult.done(reapplied.getMessage()
                + " It waits for you to keep or reject it."
                + checkAfterUndo(run));
    }

    // Explains why a rejected fix cannot be applied again.
    private String explainReapplyFailure(Anomaly anomaly) {
        String start = "The fix for " + anomaly.getUsername()
                + " was not applied again. ";
        if (anomaly.getFixStatus() != FixStatus.UNDONE) {
            return start + "Only a rejected fix can be applied again.";
        }
        if (services.getGradingService().isGrading()) {
            return start + "Wait until grading has finished.";
        }
        return start + "GradeZZY can only apply again a fix rejected "
                + "since it was opened, or in a run resumed since.";
    }

    // Grading the student again cleared their Similar code flags, so the
    // run is checked again. It runs here until GradingService runs it
    // after grading. Returns a sentence when the check failed, else "".
    private String checkAfterUndo(GradingRun run) {
        if (run == null || !isSimilarityEnabled()) {
            return "";
        }

        try {
            checkSimilarity(run);
            return "";
        } catch (RuntimeException exception) {
            return " The similar code check failed: "
                    + exception.getMessage();
        }
    }

    // Returns the newest run of this session that holds an anomaly, the
    // run AnomalyService grades again on an undo, or null.
    private GradingRun findSessionRunWith(Anomaly anomaly) {
        List<GradingRun> sessionRuns =
                services.getGradingService().getSessionRuns();
        for (int index = sessionRuns.size() - 1; index >= 0; index--) {
            GradingRun run = sessionRuns.get(index);
            if (holdsAnomaly(run, anomaly.getId())) {
                return run;
            }
        }
        return null;
    }

    // Says whether any submission of a run holds the anomaly with an id.
    private boolean holdsAnomaly(GradingRun run, String anomalyId) {
        for (Submission submission : run.getSubmissions()) {
            for (Anomaly held : submission.getAnomalies()) {
                if (held.getId().equals(anomalyId)) {
                    return true;
                }
            }
        }
        return false;
    }

    /**
     * Returns Not connected yet: no service returns a fix to Applied.
     */
    @Override
    public ActionResult undoRepairDecision(RepairItem repair) {
        Anomaly anomaly = repair.getAnomaly();
        // A rejected fix put the files back and graded again, so it
        // cannot be taken back here; grade the student again instead
        if (!services.getAnomalyService().reopenFix(anomaly)) {
            return ActionResult.failed("Only a kept fix can be taken "
                    + "back. The fix for " + anomaly.getUsername()
                    + " is " + describeFixStatus(anomaly.getFixStatus())
                    + ".");
        }
        forgetSavedRuns();
        return ActionResult.done("The fix for " + anomaly.getUsername()
                + " waits for review again.");
    }

    /**
     * Calls QuestionRerunner.run, which saves nothing, with the run's
     * assessment and tester folder, else the ones in the settings.
     */
    @Override
    public QuestionResult rerunQuestion(Submission submission,
            GradingRun gradingRun, Question question)
            throws GradingException {
        Assessment graded = assessment;
        Path testers = Paths.get(config.getString(ConfigKeys.TESTERS_DIR));
        if (gradingRun != null && gradingRun.getAssessment() != null) {
            graded = gradingRun.getAssessment();
        }
        if (gradingRun != null && gradingRun.getTestersDirectory() != null) {
            testers = gradingRun.getTestersDirectory();
        }
        return new QuestionRerunner(config).run(submission, graded,
                question, testers);
    }

    /**
     * Reads the file with FileUtils.readText, looking inside the
     * student's folders as grading does.
     */
    @Override
    public String readSourceFile(Submission submission,
            String relativePath) {
        Path workingCopy = submission.getWorkingCopyPath();
        if (workingCopy == null) {
            return "";
        }

        try {
            Path file = findSourceFile(workingCopy, relativePath, false,
                    SOURCE_SEARCH_DEPTH);
            if (file == null) {
                file = findSourceFile(workingCopy, relativePath, true,
                        SOURCE_SEARCH_DEPTH);
            }
            if (file == null) {
                return "";
            }
            return FileUtils.readText(file);
        } catch (IOException exception) {
            return "";
        }
    }

    // Finds a file such as Q1/Q1a.java in a working copy: at its top, or
    // inside the folders around it, such as the folder named after the
    // student, as grading finds it. Links are never followed.
    private Path findSourceFile(Path folder, String relativePath,
            boolean ignoreCase, int depth) throws IOException {
        Path match = matchPath(folder, relativePath.split(FOLDER_SEPARATOR),
                ignoreCase);
        if (match != null || depth == 0) {
            return match;
        }
        for (Path child : listChildren(folder)) {
            if (Files.isDirectory(child, LinkOption.NOFOLLOW_LINKS)) {
                Path found = findSourceFile(child, relativePath, ignoreCase,
                        depth - 1);
                if (found != null) {
                    return found;
                }
            }
        }
        return null;
    }

    // Follows the parts of a path down from a folder, matching each name
    // exactly or ignoring case. Returns the ordinary file, or null.
    private Path matchPath(Path folder, String[] parts, boolean ignoreCase)
            throws IOException {
        Path current = folder;
        for (String part : parts) {
            Path next = null;
            for (Path child : listChildren(current)) {
                String name = child.getFileName().toString();
                boolean same = name.equals(part)
                        || (ignoreCase && name.equalsIgnoreCase(part));
                if (same && next == null) {
                    next = child;
                }
            }
            if (next == null) {
                return null;
            }
            current = next;
        }
        if (Files.isRegularFile(current, LinkOption.NOFOLLOW_LINKS)) {
            return current;
        }
        return null;
    }

    // Lists a folder's children, in name order; none for a file.
    private List<Path> listChildren(Path folder) throws IOException {
        List<Path> children = new ArrayList<>();
        if (!Files.isDirectory(folder, LinkOption.NOFOLLOW_LINKS)) {
            return children;
        }
        DirectoryStream<Path> entries = null;
        try {
            entries = Files.newDirectoryStream(folder);
            for (Path child : entries) {
                children.add(child);
            }
        } finally {
            FileUtils.closeIfOpen(entries);
        }
        Collections.sort(children);
        return children;
    }

    /**
     * Calls ScoreAdjustmentService.adjust on the run shown on Review.
     */
    @Override
    public ActionResult adjustScore(GradingRun run, Submission submission,
            String questionId, double newScore, String reason) {
        if (run == null || !run.getSubmissions().contains(submission)) {
            return ActionResult.failed("Open the student's run on Review "
                    + "first.");
        }
        try {
            ScoreAdjustment change = services.getScoreAdjustmentService()
                    .adjust(run, submission, questionId, newScore, reason);
            return ActionResult.done(adjustmentSaved("Saved.") + " "
                    + questionId + " for " + submission.getUsername()
                    + " changed from "
                    + ScoreFormatter.format(change.getPreviousScore())
                    + " to " + ScoreFormatter.format(newScore) + ".");
        } catch (IllegalArgumentException exception) {
            return ActionResult.failed(exception.getMessage());
        }
    }

    /**
     * Calls ScoreAdjustmentService.undo, which puts the earlier mark back
     * in the run shown on Review.
     */
    @Override
    public ActionResult undoAdjustment(GradingRun run,
            ScoreAdjustment adjustment) {
        if (run == null) {
            return ActionResult.failed("Open the student's run on Review "
                    + "first.");
        }
        try {
            services.getScoreAdjustmentService().undo(run, adjustment);
            return ActionResult.done(adjustmentSaved("Undone.") + " "
                    + adjustment.getQuestionId() + " for "
                    + adjustment.getUsername() + " is "
                    + ScoreFormatter.format(adjustment.getPreviousScore())
                    + " again.");
        } catch (IllegalArgumentException exception) {
            return ActionResult.failed(exception.getMessage());
        }
    }

    // Starts the message about a changed mark: the given word when it was
    // saved, else that it lasts only until GradeZZY closes, and why.
    private String adjustmentSaved(String savedWord) {
        ScoreAdjustmentService adjustments =
                services.getScoreAdjustmentService();
        String problem = adjustments.getSaveProblem();
        if (!adjustments.isSaving()) {
            return "Changed for this session only; saving results is "
                    + "switched off.";
        } else if (!problem.isEmpty()) {
            return "Not saved, so it lasts only until GradeZZY closes ("
                    + problem + ").";
        }
        return savedWord;
    }

    /**
     * Calls ScoreAdjustmentService.list for the run shown on Review.
     */
    @Override
    public List<ScoreAdjustment> listAdjustments(GradingRun run,
            Submission submission) {
        if (run == null) {
            return new ArrayList<>();
        }
        return services.getScoreAdjustmentService().list(run,
                submission.getUsername());
    }

    /**
     * Calls ExportService.export for the chosen students.
     */
    @Override
    public ActionResult export(GradingRun gradingRun,
            List<Submission> scope, String formatName) {
        if (gradingRun == null) {
            return ActionResult.failed("There are no results to export "
                    + "yet. Grade a batch first.");
        }

        Path outputFolder = Paths.get(getExportFolder(
                gradingRun.getSection()));
        try {
            Path written = services.getExportService().export(gradingRun,
                    scope, formatName, outputFolder);
            if (written == null) {
                return ActionResult.failed(waitingMessage(
                        Capability.EXPORT_RESULTS));
            }

            String note = "";
            if (scope.size() < gradingRun.getSubmissions().size()) {
                note = " It holds " + scope.size() + " of "
                        + gradingRun.getSubmissions().size()
                        + " students.";
            }
            return ActionResult.saved("Exported " + formatName
                    + " to " + written + "." + note, written);
        } catch (ExportException exception) {
            return ActionResult.failed("The export did not finish: "
                    + exception.getMessage());
        }
    }

    /**
     * Calls StatisticsService.summarise.
     */
    @Override
    public StatisticsSummary summarise(String moduleCode,
            String sectionCode) {
        return services.getStatisticsService().summarise(
                new StatisticsFilter(moduleCode, sectionCode));
    }

    /**
     * Calls HistoryService.backupNow.
     */
    @Override
    public ActionResult backupNow() {
        Path backup = services.getHistoryService().backupNow();
        if (backup == null) {
            return ActionResult.failed(explainBackupFailure());
        }
        return ActionResult.saved("Backed up the saved results to "
                + backup + ".", backup);
    }

    /**
     * Returns Not connected yet: no service lists the run in each backup.
     */
    @Override
    public ActionResult restore(RunRecord record) {
        return ActionResult.notConnected(Capability.RESTORE_PREVIEW);
    }

    /**
     * Calls HistoryService.restore. It returns nothing, so this says Not
     * connected yet until the RESTORE capability works.
     */
    @Override
    public ActionResult restoreBackup(Path backupFile) {
        if (!config.getBoolean(ConfigKeys.DATABASE_ENABLED)) {
            return ActionResult.failed("Saving results is switched off in "
                    + "Settings, so there is nothing to restore into. "
                    + "Switch it on, then restore again.");
        }

        try {
            services.getHistoryService().restore(backupFile);
        } catch (UnsupportedOperationException exception) {
            return ActionResult.notConnected(Capability.RESTORE);
        } catch (RuntimeException exception) {
            // A restore that failed half way may still have changed them
            forgetSavedRuns();
            return ActionResult.failed("The restore of "
                    + backupFile.getFileName() + " did not finish ("
                    + exception.getMessage() + "). Check the History tab "
                    + "before grading again.");
        }

        // Forget every run and decision so getRun reads the restored
        // history, with the decisions saved in it.
        runsBySection.clear();
        decisions.clear();
        services.getScoreAdjustmentService().forgetAll();
        forgetSavedRuns();
        if (Capability.RESTORE.getStatus() != CapabilityStatus.WORKS_NOW) {
            return ActionResult.notConnected(Capability.RESTORE);
        }
        return ActionResult.done("Restored the saved results from "
                + backupFile.getFileName() + ".");
    }

    /**
     * Calls AuditService.listEntries and puts the newest first.
     */
    @Override
    public List<AuditEntry> listAuditEntries() {
        List<AuditEntry> oldestFirst =
                services.getAuditService().listEntries();
        List<AuditEntry> newestFirst = new ArrayList<>();
        for (int index = oldestFirst.size() - 1; index >= 0; index--) {
            newestFirst.add(oldestFirst.get(index));
        }
        return newestFirst;
    }

    /**
     * Calls AuditService.record.
     */
    @Override
    public void recordAudit(String action, String detail) {
        services.getAuditService().record(action, detail);
    }

    /**
     * Calls SettingsService.getSetting.
     */
    @Override
    public String getSetting(String key) {
        return services.getSettingsService().getSetting(key);
    }

    /**
     * Calls SettingsService.validate.
     */
    @Override
    public String validateSetting(String key, String value) {
        return services.getSettingsService().validate(key, value);
    }

    /**
     * Validates every change, then saves them with SettingsService.
     */
    @Override
    public ActionResult saveSettings(Map<String, String> changes) {
        return saveSettings(new ArrayList<>(changes.keySet()), changes);
    }

    /**
     * Validates every change in order, then saves them with
     * SettingsService in the same order.
     */
    @Override
    public ActionResult saveSettings(List<String> keys,
            Map<String, String> changes) {
        for (String key : keys) {
            String problem = validateSetting(key, changes.get(key));
            if (!problem.isEmpty()) {
                return ActionResult.failed(problem);
            }
        }

        for (String key : keys) {
            services.getSettingsService().updateSetting(key,
                    changes.get(key));
        }
        // Switching the database off or on changes the saved runs shown
        forgetSavedRuns();

        try {
            services.getSettingsService().save();
            if (changesAssessment(keys)) {
                library().saveCurrentFromSettings();
                assessment = new AssessmentFactory(config).create();
            }
        } catch (IOException exception) {
            return ActionResult.failed("The settings could not be saved: "
                    + exception.getMessage());
        }
        return ActionResult.done("Saved " + changes.size() + " setting"
                + plural(changes.size()) + " to config.properties.");
    }

    // ----------------------------------------------------- assessments

    /**
     * Calls AssessmentLibrary.list. If the folder cannot be read, only the
     * assessment in use is listed.
     */
    @Override
    public List<SavedAssessment> listAssessments() {
        try {
            return library().list();
        } catch (IOException exception) {
            List<SavedAssessment> current = new ArrayList<>();
            current.add(library().readCurrent());
            return current;
        }
    }

    /**
     * Calls AssessmentLibrary.validate, then checks that the folders
     * exist.
     */
    @Override
    public String validateAssessment(SavedAssessment draft,
            String originalName) {
        List<String> names = new ArrayList<>();
        for (SavedAssessment saved : listAssessments()) {
            names.add(saved.getName());
        }
        String problem = library().validate(draft, originalName, names);
        if (!problem.isEmpty()) {
            return problem;
        }
        return library().checkFolders(draft);
    }

    /**
     * Calls AssessmentLibrary.save and records it in the activity log.
     */
    @Override
    public ActionResult saveAssessment(SavedAssessment draft,
            String originalName) {
        try {
            library().save(draft, originalName);
        } catch (IOException exception) {
            return ActionResult.failed("The assessment could not be saved: "
                    + exception.getMessage());
        } catch (IllegalArgumentException exception) {
            return ActionResult.failed(exception.getMessage());
        }
        assessment = new AssessmentFactory(config).create();
        recordAudit("Assessment saved", draft.getName().trim());
        return ActionResult.done("Saved " + draft.getName().trim() + ".");
    }

    /**
     * Calls AssessmentLibrary.use and records it in the activity log.
     */
    @Override
    public ActionResult useAssessment(String name) {
        try {
            library().use(name);
        } catch (IOException exception) {
            return ActionResult.failed("GradeZZY could not switch to "
                    + name + ": " + exception.getMessage());
        } catch (IllegalArgumentException exception) {
            return ActionResult.failed("GradeZZY could not switch to "
                    + name + ": " + exception.getMessage());
        }
        assessment = new AssessmentFactory(config).create();
        recordAudit("Assessment chosen", name);
        return ActionResult.done("Now grading " + name + ".");
    }

    /**
     * Calls AssessmentLibrary.delete and records it in the activity log.
     */
    @Override
    public ActionResult deleteAssessment(String name) {
        try {
            library().delete(name);
        } catch (IOException exception) {
            return ActionResult.failed(name + " could not be deleted: "
                    + exception.getMessage());
        } catch (IllegalArgumentException exception) {
            return ActionResult.failed(exception.getMessage());
        }
        recordAudit("Assessment deleted", name);
        return ActionResult.done("Deleted " + name + ". Results graded "
                + "with it are kept.");
    }

    // Says whether any of the changed settings belongs to the assessment.
    private boolean changesAssessment(List<String> keys) {
        for (String key : keys) {
            if (isAssessmentSetting(key)) {
                return true;
            }
        }
        return false;
    }

    // Returns the saved assessments.
    private AssessmentLibrary library() {
        return services.getAssessmentLibrary();
    }

    // Says whether a setting belongs to the assessment, so a change on
    // the Settings page is saved into the assessment in use too.
    private boolean isAssessmentSetting(String key) {
        return key.startsWith(ConfigKeys.QUESTION_PREFIX)
                || key.equals(ConfigKeys.ASSESSMENT_NAME)
                || key.equals(ConfigKeys.TESTERS_DIR)
                || key.equals(ConfigKeys.STARTER_DIR)

                || key.equals(ConfigKeys.PROVIDED_CLASS_FILES)
                || key.equals(ConfigKeys.TESTER_DATA_FILES)
                || key.equals(ConfigKeys.PLACEHOLDER_FOLDER_NAMES);
    }

    /**
     * Calls AuditService.record.
     */
    @Override
    public void recordActivity(String action, String detail) {
        services.getAuditService().record(action, detail);
    }

    /**
     * Reads the pairs back from the run's Similar code flags, so nothing
     * is checked again and a saved run shows the pairs it was saved with.
     */
    @Override
    public List<SimilarityPair> listSimilarPairs(GradingRun gradingRun) {
        List<SimilarityPair> pairs = new ArrayList<>();
        if (gradingRun == null) {
            return pairs;
        }

        String idStart = SIMILAR_ID_PREFIX + gradingRun.getId()
                + SIMILAR_ID_SEPARATOR;
        // Both students of a pair hold a flag, so each pair is kept once
        List<String> pairKeys = new ArrayList<>();
        for (Submission submission : gradingRun.getSubmissions()) {
            for (Anomaly anomaly : submission.getAnomalies()) {
                SimilarityPair pair = readSimilarFlag(anomaly, idStart);
                if (pair != null && !pairKeys.contains(pairKey(pair))) {
                    pairKeys.add(pairKey(pair));
                    insertBySimilarity(pairs, pair);
                }
            }
        }
        return pairs;
    }

    /**
     * Calls PlagiarismService.check.
     */
    @Override
    public List<SimilarityPair> checkSimilarity(GradingRun gradingRun) {
        if (gradingRun == null) {
            return new ArrayList<>();
        }
        List<SimilarityPair> pairs =
                services.getPlagiarismService().check(gradingRun);
        // The check saves the run, so saved copies of it are stale
        forgetSavedRuns();
        return pairs;
    }

    // Reads the pair a Similar code flag of the run names, or returns
    // null for any other anomaly or a flag that cannot be read.
    private SimilarityPair readSimilarFlag(Anomaly anomaly, String idStart) {
        if (anomaly.getType() != AnomalyType.SIMILAR_CODE
                || !anomaly.getId().startsWith(idStart)) {
            return null;
        }

        String[] parts = anomaly.getId().substring(idStart.length())
                .split(SIMILAR_ID_SEPARATOR);
        if (parts.length != SIMILAR_ID_PARTS) {
            return null;
        }

        String questionId = parts[QUESTION_PART];
        String message = anomaly.getMessage();
        String start = questionId + SIMILAR_MESSAGE_MIDDLE;
        int end = message.indexOf(PERCENT_SIGN);
        if (!message.startsWith(start) || end < start.length()) {
            return null;
        }

        try {
            int percent = Integer.parseInt(message.substring(
                    start.length(), end));
            return new SimilarityPair(parts[STUDENT_PART],
                    parts[OTHER_STUDENT_PART], questionId,
                    percent / PERCENT);
        } catch (NumberFormatException exception) {
            return null;
        }
    }

    // Names a pair the same way whichever student comes first.
    private String pairKey(SimilarityPair pair) {
        String first = pair.getFirstUsername();
        String second = pair.getSecondUsername();
        if (first.compareTo(second) > 0) {
            return pair.getQuestionId() + SIMILAR_ID_SEPARATOR + second
                    + SIMILAR_ID_SEPARATOR + first;
        }
        return pair.getQuestionId() + SIMILAR_ID_SEPARATOR + first
                + SIMILAR_ID_SEPARATOR + second;
    }

    // Adds a pair after every pair at least as similar, so the list stays
    // most similar first.
    private void insertBySimilarity(List<SimilarityPair> pairs,
            SimilarityPair pair) {
        int place = 0;
        while (place < pairs.size()
                && pairs.get(place).getSimilarity() >= pair.getSimilarity()) {
            place++;
        }
        pairs.add(place, pair);
    }

    /**
     * Reads both students' answer files with readSourceFile, as the check
     * compares the whole answer file of the question. Returns null when
     * the run, a student or the question is gone, or neither file can be
     * read.
     */
    @Override
    public SimilarityDetail getSimilarityDetail(GradingRun gradingRun,
            SimilarityPair pair) {
        if (gradingRun == null || pair == null) {
            return null;
        }

        Question question = findQuestion(gradingRun.getAssessment(),
                pair.getQuestionId());
        Submission first = findSubmission(gradingRun,
                pair.getFirstUsername());
        Submission second = findSubmission(gradingRun,
                pair.getSecondUsername());
        if (question == null || first == null || second == null) {
            return null;
        }

        String file = question.getFolderName() + FOLDER_SEPARATOR
                + question.getFileName();
        String firstCode = readSourceFile(first, file);
        String secondCode = readSourceFile(second, file);
        if (firstCode.isEmpty() && secondCode.isEmpty()) {
            return null;
        }

        return new SimilarityDetail(pair, file, file, firstCode,
                secondCode, listLeftOut(gradingRun, question));
    }

    // Lists what the check leaves out of a question: the starter file's
    // code, and every other file in the question folder.
    private List<String> listLeftOut(GradingRun gradingRun,
            Question question) {
        Path starter = gradingRun.getStarterDirectory();
        if (starter == null) {
            starter = configuredPath(InputKind.STARTER);
        }

        List<String> leftOut = new ArrayList<>();
        Path starterFile = starter.resolve(question.getFolderName())
                .resolve(question.getFileName());
        if (Files.isRegularFile(starterFile)) {
            leftOut.add(starterFile.toString() + " (starter code, taken "
                    + "out before comparing)");
        }
        leftOut.add("Every other file in " + question.getFolderName()
                + ", such as the tester (only " + question.getFileName()
                + " is compared)");
        return leftOut;
    }

    // Returns the question with an id, or null if there is none.
    private Question findQuestion(Assessment runAssessment,
            String questionId) {
        if (runAssessment == null) {
            return null;
        }
        for (Question question : runAssessment.getQuestions()) {
            if (question.getId().equals(questionId)) {
                return question;
            }
        }
        return null;
    }

    // Returns the submission of a username in a run, or null.
    private Submission findSubmission(GradingRun gradingRun,
            String username) {
        for (Submission submission : gradingRun.getSubmissions()) {
            if (submission.getUsername().equals(username)) {
                return submission;
            }
        }
        return null;
    }

    /**
     * Returns plagiarism.enabled.
     */
    @Override
    public boolean isSimilarityEnabled() {
        return config.getBoolean(ConfigKeys.PLAGIARISM_ENABLED);
    }

    /**
     * Does nothing, because live mode has no sample cohort.
     */
    @Override
    public void useLargeSampleCohort(boolean large) {
        // live mode has no sample cohort
    }

    // Checks one chosen zip or folder with the SubmissionLoader methods
    // grading uses, so Set up says what grading will really do.
    private ImportItem inspectSubmission(Path path) {
        if (InputValidator.isReadableDirectory(path)) {
            return preview.inspectFolder(path);
        } else if (InputValidator.isReadableFile(path)) {
            return preview.inspectFile(path);
        }
        return unreadable(InputKind.SUBMISSION, path);
    }

    // Checks that a starter folder holds each question's starter file,
    // which the untouched and empty answer checks compare answers with.
    private ImportItem inspectStarter(Path folder) {
        List<String> missingFiles = new ArrayList<>();
        List<String> missingIds = new ArrayList<>();
        for (Question question : assessment.getQuestions()) {
            Path file = folder.resolve(question.getFolderName())
                    .resolve(question.getFileName());
            if (!InputValidator.isReadableFile(file)) {
                missingFiles.add(question.getFolderName()
                        + FOLDER_SEPARATOR + question.getFileName());
                missingIds.add(question.getId());
            }
        }

        if (missingFiles.isEmpty()) {
            return new ImportItem(InputKind.STARTER, folder, "",
                    ImportState.ACCEPTED, "Holds the starter file of every "
                    + "question.");
        } else if (missingFiles.size() == assessment.getQuestions().size()) {
            return new ImportItem(InputKind.STARTER, folder, "",
                    ImportState.INVALID, "None of the starter files, such "
                    + "as " + missingFiles.get(0) + ", is in this folder, "
                    + describeStarterFallback(folder)
                    + " Choose the folder given to the students.");
        }
        String verb = " are";
        if (missingFiles.size() == 1) {
            verb = " is";
        }
        return new ImportItem(InputKind.STARTER, folder, "",
                ImportState.WARNING, joinWords(missingFiles) + verb
                + " not in this folder, so untouched or empty answers to "
                + joinWords(missingIds) + " will not be flagged.");
    }

    // Says what grading does instead of using a starter folder that
    // cannot be used: it uses the one in Settings, when that is another
    // folder that can be used.
    private String describeStarterFallback(Path folder) {
        Path configured = getConfiguredPath(InputKind.STARTER);
        if (!isSameFolder(folder, configured) && inspect(InputKind.STARTER,
                configured).getState().isUsable()) {
            return "so the starter folder in Settings is used instead.";
        }
        return "so untouched and empty answers cannot be flagged.";
    }

    // Says whether two paths name the same folder.
    private boolean isSameFolder(Path first, Path second) {
        return first.toAbsolutePath().normalize().equals(
                second.toAbsolutePath().normalize());
    }

    // Returns the chosen starter folder, or null when none is chosen or
    // it cannot be used, so grading uses the one in Settings.
    private Path findUsableStarter(ImportBatch batch) {
        List<ImportItem> starters = batch.getItems(InputKind.STARTER);
        if (starters.isEmpty() || !starters.get(0).getState().isUsable()) {
            return null;
        }
        return starters.get(0).getPath();
    }

    // Returns the item for a path InputValidator could not read. A path
    // that is not there is named in full, because the usual cause is a
    // setting that points at a folder this computer does not have.
    private ImportItem unreadable(InputKind kind, Path path) {
        String message = "GradeZZY could not read this. Check that it "
                + "is not open in another program.";
        if (!Files.exists(path)) {
            message = "Not found at " + path.toAbsolutePath().normalize()
                    + ".";
        }
        if (kind == InputKind.STARTER) {
            message = "GradeZZY could not read this, "
                    + describeStarterFallback(path) + " " + message;
        }
        CapabilityStatus checks = Capability.INPUT_CHECKS.getStatus();
        if (checks != CapabilityStatus.WORKS_NOW) {
            message += " While " + Capability.INPUT_CHECKS.getServiceMethod()
                    + " is a placeholder, every file is reported this way.";
        }
        return new ImportItem(kind, path, "", ImportState.INVALID, message);
    }

    // Creates the source of tester files for a batch.
    private TestCaseSource createTestCaseSource(ImportBatch batch) {
        if (!batch.isUsingTypedTestCases()) {
            return new UploadedTesterSource(
                    batch.getSinglePath(InputKind.TESTERS), config);
        }

        Path outputFolder = Paths.get(config.getString(
                ConfigKeys.OUTPUT_DIR), GENERATED_TESTERS_FOLDER);
        TypedTestCaseSource source = new TypedTestCaseSource(
                new TesterGenerator(config), outputFolder);
        for (TestCase testCase : batch.getTypedTestCases()) {
            source.addTestCase(testCase);
        }
        return source;
    }

    // Runs a grading task on a daemon thread and reports how it ended.
    private void runInBackground(GradingTask task, RunCallback callback,
            Capability capability) {
        task.setOnSucceeded(event -> finishTask(task, callback, capability));
        task.setOnFailed(event -> failTask(task, callback));
        if (isSimilarityEnabled()) {
            task.checkSimilarityAfter(services.getPlagiarismService());
        }

        Thread worker = new Thread(task, "gradezzy-grading");
        worker.setDaemon(true);
        worker.start();
    }

    // Stores and reports the run a finished task returned.
    private void finishTask(GradingTask task, RunCallback callback,
            Capability capability) {
        GradingRun run = task.getValue();
        endTask();
        if (run == null) {
            callback.onFailed(waitingMessage(capability));
            return;
        }
        rememberAnomalies(run, capability);
        if (task.getSimilarityProblem() != null) {
            recordActivity("Similar code not checked",
                    task.getSimilarityProblem());
        }
        if (capability != Capability.RETRY_FAILED) {
            setLastRunStarted(run);
        }
        if (run.getSection() != null) {
            runsBySection.put(run.getSection().getLabel(), run);
        }
        callback.onFinished(run);
    }

    // Records the last run started or resumed, which Retry can grade.
    private synchronized void setLastRunStarted(GradingRun run) {
        lastRunStarted = run;
    }

    // Reports a task that stopped with an exception.
    private void failTask(GradingTask task, RunCallback callback) {
        endTask();
        callback.onFailed(describeFailure(task.getException()));
    }

    // Clears what a task held while it ran. A task may have saved runs,
    // so the saved runs are read again when next needed.
    private void endTask() {
        setRunGradedAgain(null);
        forgetSavedRuns();
    }

    // Records the anomalies of a run started in this session, so a failed
    // undo can say whether FixManager could know about its fix.
    private void rememberAnomalies(GradingRun run,
            Capability capability) {
        if (capability == Capability.IMPORT_AND_GRADE) {
            runsStartedNow.add(run.getId());
        }
        // A resumed run of an earlier session mixes old fixes in
        if (!runsStartedNow.contains(run.getId())) {
            return;
        }

        for (Submission submission : run.getSubmissions()) {
            for (Anomaly anomaly : submission.getAnomalies()) {
                anomaliesFoundNow.add(anomaly.getId());
            }
        }
    }

    // Turns an exception from a background task into a plain sentence.
    private String describeFailure(Throwable exception) {
        if (exception == null || exception.getMessage() == null) {
            return "Grading stopped unexpectedly. Completed results are "
                    + "kept. Try again, or check the console for details.";
        }
        return exception.getMessage();
    }

    // Builds the sentence shown when a service returned a placeholder.
    private String waitingMessage(Capability capability) {
        return capability.getTitle() + " is waiting for "
                + capability.getServiceMethod() + " ("
                + capability.getOwner() + ").";
    }

    // Describes how far a saved run got.
    private String describeRun(GradingRun run) {
        int graded = 0;
        for (Submission submission : run.getSubmissions()) {
            if (submission.getStatus() == SubmissionStatus.GRADED) {
                graded++;
            }
        }
        return graded + " of " + run.getSubmissions().size() + " graded";
    }

    // Returns the saved runs, newest first, reading them from the
    // database only when they are not read yet.
    private synchronized List<GradingRun> getSavedRuns() {
        if (savedRuns == null) {
            savedRuns = services.getHistoryService().listRuns();
            for (GradingRun saved : savedRuns) {
                readBackChecks(saved);
            }
        }
        return savedRuns;
    }

    // Reads back the decisions and checked 0s saved with a run, onto the
    // saved copy and this session's copy of it. A decision made in this
    // session wins, because it may not have been saved.
    private void readBackChecks(GradingRun saved) {
        AnomalyService anomalyService = services.getAnomalyService();
        Map<String, String> savedDecisions =
                anomalyService.loadDecisions(saved);
        List<String> checkedZeros = anomalyService.loadZeroChecks(saved);
        GradingRun[] copies = {saved, findSessionRun(saved)};
        for (GradingRun copy : copies) {
            for (Submission submission : copy.getSubmissions()) {
                if (checkedZeros.contains(submission.getUsername())) {
                    ZeroChecks.set(submission, true);
                }
                for (Anomaly anomaly : submission.getAnomalies()) {
                    ReviewDecision decision = decisionNamed(
                            savedDecisions.get(anomaly.getId()));
                    if (decision != null) {
                        decisions.putIfAbsent(decisionKey(anomaly),
                                decision);
                    }
                }
            }
        }
    }

    // Reads a saved decision name, or null for none or an unknown name.
    private ReviewDecision decisionNamed(String name) {
        if (name == null) {
            return null;
        }
        try {
            return ReviewDecision.valueOf(name);
        } catch (IllegalArgumentException exception) {
            return null;
        }
    }

    // Finds the run that holds a submission: a run of this session, else
    // a saved run; null when none does.
    private GradingRun findRunHolding(Submission submission) {
        List<GradingRun> runs = new ArrayList<>(
                services.getGradingService().getSessionRuns());
        runs.addAll(runsBySection.values());
        runs.addAll(getSavedRuns());
        for (GradingRun run : runs) {
            for (Submission held : run.getSubmissions()) {
                if (held == submission) {
                    return run;
                }
            }
        }
        return null;
    }

    // Forgets the saved runs, so the next call reads them again.
    private synchronized void forgetSavedRuns() {
        savedRuns = null;
    }

    // Returns the newest saved run of a section, or null.
    private GradingRun findNewestSavedRun(String sectionLabel) {
        for (GradingRun saved : getSavedRuns()) {
            if (saved.getSection() != null && sectionLabel.equals(
                    saved.getSection().getLabel())) {
                return saved;
            }
        }
        return null;
    }

    // Returns this session's object of a saved run, else the saved copy.
    // GradingService retries only the run object it graded. A run being
    // graded again keeps its saved copy, as getRun does.
    private GradingRun findSessionRun(GradingRun saved) {
        if (saved.getSection() == null || saved.getId() == null) {
            return saved;
        }
        GradingRun recent = runsBySection.get(saved.getSection().getLabel());
        if (recent == null || isGradedAgainNow(recent)
                || !saved.getId().equals(recent.getId())) {
            return saved;
        }
        return recent;
    }

    // Says whether getRun shows this run for its section now.
    private boolean isShownNow(GradingRun run) {
        GradingRun shown = getRun(run.getSection());
        return shown != null && shown.getId() != null
                && shown.getId().equals(run.getId());
    }

    // Records which run is being graded again, or null for none.
    private synchronized void setRunGradedAgain(String runId) {
        runGradedAgainId = runId;
    }

    // Says whether the grading threads are changing this run now.
    private synchronized boolean isGradedAgainNow(GradingRun run) {
        return run.getId() != null && run.getId().equals(runGradedAgainId);
    }

    // Keys a review decision by student and anomaly id. Anomaly ids do
    // not repeat (most end in a random UUID, as PIPELINE-<UUID> does), so
    // the username only makes the key easier to read.
    private String decisionKey(Anomaly anomaly) {
        return anomaly.getUsername() + "/" + anomaly.getId();
    }

    // Says a fix status in words, for a message.
    private String describeFixStatus(FixStatus status) {
        if (status == FixStatus.APPROVED) {
            return "already approved";
        } else if (status == FixStatus.UNDONE) {
            return "already undone";
        } else if (status == FixStatus.APPLIED) {
            return "still waiting for review";
        } else if (status == FixStatus.PENDING) {
            return "not applied yet";
        }
        return "not a fix GradeZZY made";
    }

    // Returns why the automatic fix for an anomaly failed, or null.
    private String findFixFailure(Anomaly anomaly) {
        return services.getAnomalyService().getFailureReason(anomaly);
    }

    // Says why a finding has no repair: why its fix failed, if known.
    private String describeNoRepair(Anomaly anomaly) {
        String reason = findFixFailure(anomaly);
        if (reason == null) {
            return RepairItem.NO_REPAIR_NOTE;
        }
        return "The automatic fix did not work: " + reason;
    }

    // Explains why a repair waiting for review could not be undone.
    // FixManager keeps the record of each fix only in memory, and forgets
    // a student's fixes when the student is graded again.
    private String explainUndoFailure(Anomaly anomaly) {
        String start = "The fix for " + anomaly.getUsername()
                + " could not be undone, so it still shows as applied. ";
        if (!anomaliesFoundNow.contains(anomaly.getId())) {
            return start + "Its run was graded before GradeZZY was last "
                    + "opened, and GradeZZY can only undo a fix made "
                    + "since then.";
        }
        return start + "Either the student was graded again since, or "
                + "the files the fix changed were moved or deleted.";
    }

    // Explains why HistoryService.backupNow wrote no backup.
    private String explainBackupFailure() {
        if (!config.getBoolean(ConfigKeys.DATABASE_ENABLED)) {
            return "Saving results is switched off in Settings, so there "
                    + "is nothing to back up.";
        }
        return "The backup could not be written. If no batch has been "
                + "graded yet there is nothing to back up; otherwise check "
                + "that " + config.getString(ConfigKeys.BACKUP_DIR)
                + " can be written.";
    }

    // Returns "s" for any count other than one.
    private String plural(int count) {
        if (count == 1) {
            return "";
        }
        return "s";
    }

    // Joins words as "a", "a and b" or "a, b and c".
    private String joinWords(List<String> words) {
        String text = "";
        for (int index = 0; index < words.size(); index++) {
            if (index > 0 && index == words.size() - 1) {
                text += " and ";
            } else if (index > 0) {
                text += ", ";
            }
            text += words.get(index);
        }
        return text;
    }
}
