/*
 * File: LiveBackend.java
 *
 * Purpose: Connects the screens to the real services of GradeZZY.
 *
 * Where it fits: The default backend, used by "sh run.sh". Each method
 * calls one service or reads a setting, else returns Not connected yet.
 * Review decisions on flags are kept here, in memory, for one session.
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
import gradezzy.service.AssessmentFactory;
import gradezzy.service.AssessmentLibrary;
import gradezzy.service.ClassFileMatch;
import gradezzy.service.ClassFileMatcher;
import gradezzy.service.ServiceRegistry;
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

    // The review decisions of this session, by student and anomaly id
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
        Path sheet = gradingRun.getScoreSheetFile();
        if (sheet == null) {
            sheet = getConfiguredPath(InputKind.ROSTER);
        }

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

        Path scoreSheet = gradingRun.getScoreSheetFile();
        if (scoreSheet == null) {
            scoreSheet = getConfiguredPath(InputKind.ROSTER);
        }

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
     * Returns the decision recorded in this session, or OPEN.
     */
    @Override
    public ReviewDecision getDecision(Anomaly anomaly) {
        return decisions.getOrDefault(decisionKey(anomaly),
                ReviewDecision.OPEN);
    }

    /**
     * Records the decision in memory until GradeZZY closes. No service
     * saves review decisions, so they are not kept after a restart.
     */
    @Override
    public ActionResult setDecision(Anomaly anomaly,
            ReviewDecision decision) {
        if (anomaly == null || decision == null) {
            return ActionResult.failed("Choose a flag and a decision.");
        }

        ReviewDecision before = getDecision(anomaly);
        if (decision == ReviewDecision.OPEN) {
            decisions.remove(decisionKey(anomaly));
        } else {
            decisions.put(decisionKey(anomaly), decision);
        }
        // Every decision is in the activity log, so it can be undone there
        if (before != decision) {
            services.getAuditService().record(decision.getAuditAction(),
                    anomaly.getUsername() + ": " + anomaly.getMessage());
        }
        return ActionResult.done("Marked the flag for "
                + anomaly.getUsername() + " as "
                + decision.getLabel().toLowerCase() + ". No mark changed. "
                + "This is kept until GradeZZY closes.");
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

        services.getAnomalyService().undoFix(anomaly);
        forgetSavedRuns();
        if (anomaly.getFixStatus() == FixStatus.UNDONE) {
            return ActionResult.done("Undid the fix for "
                    + anomaly.getUsername()
                    + ". The working copy was put back.");
        }
        return ActionResult.failed(explainUndoFailure(anomaly));
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
            return ActionResult.done("Saved. " + questionId + " for "
                    + submission.getUsername() + " changed from "
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
            return ActionResult.done("Undone. " + adjustment.getQuestionId()
                    + " for " + adjustment.getUsername() + " is "
                    + ScoreFormatter.format(adjustment.getPreviousScore())
                    + " again.");
        } catch (IllegalArgumentException exception) {
            return ActionResult.failed(exception.getMessage());
        }
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

        // Forget every run so getRun reads the restored history.
        runsBySection.clear();
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
     * Calls PlagiarismService.check.
     */
    @Override
    public List<SimilarityPair> listSimilarPairs(GradingRun gradingRun) {
        if (gradingRun == null) {
            return new ArrayList<>();
        }
        return services.getPlagiarismService().check(gradingRun);
    }

    /**
     * Returns null, because no service describes one pair yet.
     */
    @Override
    public SimilarityDetail getSimilarityDetail(SimilarityPair pair) {
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
        }
        return savedRuns;
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
