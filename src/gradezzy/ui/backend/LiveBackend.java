/*
 * File: LiveBackend.java
 *
 * Purpose: Connects the screens to the real services of GradeZZY.
 *
 * Where it fits: The default backend, used by "sh run.sh". Each method
 * calls one service or reads a setting, else returns Not connected yet.
 */
package gradezzy.ui.backend;

import java.io.IOException;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;

import javafx.scene.control.TreeItem;

import gradezzy.config.AppConfig;
import gradezzy.config.ConfigKeys;
import gradezzy.grading.ProgressListener;
import gradezzy.model.Anomaly;
import gradezzy.model.Assessment;
import gradezzy.model.AuditEntry;
import gradezzy.model.FixStatus;
import gradezzy.model.GradingRun;
import gradezzy.model.Section;
import gradezzy.model.Submission;
import gradezzy.model.SubmissionStatus;
import gradezzy.plagiarism.SimilarityPair;
import gradezzy.report.ExportException;
import gradezzy.service.AssessmentFactory;
import gradezzy.service.ServiceRegistry;
import gradezzy.stats.StatisticsFilter;
import gradezzy.stats.StatisticsSummary;
import gradezzy.testcase.TestCase;
import gradezzy.testcase.TestCaseSource;
import gradezzy.testcase.TesterGenerator;
import gradezzy.testcase.TypedTestCaseSource;
import gradezzy.testcase.UploadedTesterSource;
import gradezzy.ui.FileTreeBuilder;
import gradezzy.ui.GradingTask;
import gradezzy.util.FileUtils;
import gradezzy.util.InputValidator;

/**
 * The backend that calls the real services.
 */
public class LiveBackend implements DashboardBackend {

    // The extension of a student submission file
    private static final String ZIP_EXTENSION = ".zip";

    // The extension of a score sheet file
    private static final String CSV_EXTENSION = ".csv";

    // The folder for generated testers, inside the output folder
    private static final String GENERATED_TESTERS_FOLDER =
            "generated-testers";

    // Every service of the application
    private final ServiceRegistry services;

    // The application settings
    private final AppConfig config;

    // The assessment, built once from the configuration
    private final Assessment assessment;

    // Latest run per section; a restore clears it on a background thread
    private final Map<String, GradingRun> runsBySection =
            new ConcurrentHashMap<>();

    /**
     * Creates a live backend.
     *
     * @param services every service
     */
    public LiveBackend(ServiceRegistry services) {
        this.services = services;
        this.config = services.getConfig();
        this.assessment = new AssessmentFactory(config).create();
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
     * Returns an empty string: config.properties has no module names.
     */
    @Override
    public String getModuleName(String moduleCode) {
        return "";
    }

    /**
     * Returns the sections listed in config.properties.
     */
    @Override
    public List<String> getSectionCodes(String moduleCode) {
        return config.getList(ConfigKeys.SECTION_CODES);
    }

    /**
     * Returns the assessment built from the configuration.
     */
    @Override
    public Assessment getAssessment() {
        return assessment;
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
     */
    @Override
    public GradingRun getRun(Section section) {
        if (section == null) {
            return null;
        }

        GradingRun recent = runsBySection.get(section.getLabel());
        if (recent != null) {
            return recent;
        }

        for (GradingRun saved : services.getHistoryService().listRuns()) {
            if (saved.getSection() != null && section.getLabel().equals(
                    saved.getSection().getLabel())) {
                return saved;
            }
        }
        return null;
    }

    /**
     * Lists the runs from HistoryService.listRuns.
     */
    @Override
    public List<RunRecord> listRuns() {
        List<RunRecord> records = new ArrayList<>();
        for (GradingRun run : services.getHistoryService().listRuns()) {
            records.add(new RunRecord(run, describeRun(run),
                    "Backup list not connected yet", false, false));
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
     * Returns null, because live mode uses the system file picker.
     */
    @Override
    public Path getSampleReplacement(ImportItem invalidItem) {
        return null;
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
     * Checks the path with InputValidator and its file name.
     */
    @Override
    public ImportItem inspect(InputKind kind, Path path) {
        String name = String.valueOf(path.getFileName());

        if (kind == InputKind.SUBMISSION) {
            return inspectSubmission(path, name);
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
        return new ImportItem(kind, path, "", ImportState.ACCEPTED,
                "Folder found.");
    }

    /**
     * Starts GradingService.startGrading in a GradingTask.
     */
    @Override
    public void startGrading(ImportBatch batch, Section section,
            ProgressListener listener, RunCallback callback) {
        GradingTask task = new GradingTask(services.getGradingService(),
                batch.getSubmissionPaths(), section,
                createTestCaseSource(batch), listener,
                batch.getSinglePath(InputKind.ROSTER));
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
                GradingTask.Kind.RESUME, gradingRun, listener);
        runInBackground(task, callback, Capability.RESUME_RUN);
    }

    /**
     * Starts GradingService.retryFailed in a GradingTask.
     */
    @Override
    public void retryFailedSubmissions(GradingRun gradingRun,
            ProgressListener listener, RunCallback callback) {
        GradingTask task = new GradingTask(services.getGradingService(),
                GradingTask.Kind.RETRY_FAILED, gradingRun, listener);
        runInBackground(task, callback, Capability.RETRY_FAILED);
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
     * Returns OPEN, because no service stores review decisions yet.
     */
    @Override
    public ReviewDecision getDecision(Anomaly anomaly) {
        return ReviewDecision.OPEN;
    }

    /**
     * Returns Not connected yet: no service stores review decisions.
     */
    @Override
    public ActionResult setDecision(Anomaly anomaly,
            ReviewDecision decision) {
        return ActionResult.notConnected(Capability.FLAG_REVIEW);
    }

    /**
     * Applies ReviewRules.needsReview.
     */
    @Override
    public boolean needsReview(Anomaly anomaly) {
        return ReviewRules.needsReview(anomaly, getDecision(anomaly));
    }

    /**
     * Lists the validation findings among the run's anomalies.
     */
    @Override
    public List<RepairItem> listRepairs(GradingRun gradingRun) {
        List<RepairItem> repairs = new ArrayList<>();
        for (Anomaly anomaly : listAnomalies(gradingRun)) {
            if (ReviewRules.isValidationFinding(anomaly.getType())) {
                repairs.add(new RepairItem(anomaly, "Not recorded yet",
                        "Not recorded yet"));
            }
        }
        return repairs;
    }

    /**
     * Calls AnomalyService.approveFix and checks the fix status changed.
     */
    @Override
    public ActionResult approveRepair(RepairItem repair) {
        Anomaly anomaly = repair.getAnomaly();
        services.getAnomalyService().approveFix(anomaly);
        return checkFixStatus(anomaly, FixStatus.APPROVED,
                "Approved the repair for " + anomaly.getUsername() + ".");
    }

    /**
     * Calls AnomalyService.undoFix and checks the fix status changed.
     */
    @Override
    public ActionResult rejectRepair(RepairItem repair) {
        Anomaly anomaly = repair.getAnomaly();
        services.getAnomalyService().undoFix(anomaly);
        return checkFixStatus(anomaly, FixStatus.UNDONE,
                "Rejected the repair for " + anomaly.getUsername()
                + ". The working copy was put back.");
    }

    /**
     * Returns Not connected yet: no service returns a fix to Applied.
     */
    @Override
    public ActionResult undoRepairDecision(RepairItem repair) {
        return ActionResult.notConnected(Capability.REPAIR_DECISION_UNDO);
    }

    /**
     * Calls SearchService.search.
     */
    @Override
    public List<Submission> search(GradingRun gradingRun, String keyword) {
        return services.getSearchService().search(gradingRun, keyword);
    }

    /**
     * Calls SearchService.searchCode.
     */
    @Override
    public List<Submission> searchCode(GradingRun gradingRun,
            String codeFragment) {
        return services.getSearchService().searchCode(gradingRun,
                codeFragment);
    }

    /**
     * Builds the tree of the working copy with FileTreeBuilder.
     */
    @Override
    public TreeItem<String> getFileTree(Submission submission) {
        Path workingCopy = submission.getWorkingCopyPath();
        if (workingCopy == null) {
            return null;
        }
        return new FileTreeBuilder().build(workingCopy);
    }

    /**
     * Reads the file with FileUtils.readText.
     */
    @Override
    public String readSourceFile(Submission submission,
            String relativePath) {
        Path workingCopy = submission.getWorkingCopyPath();
        if (workingCopy == null) {
            return "";
        }

        try {
            return FileUtils.readText(workingCopy.resolve(relativePath));
        } catch (IOException exception) {
            return "";
        }
    }

    /**
     * Returns Not connected yet: no service stores manual marks.
     */
    @Override
    public ActionResult adjustScore(Submission submission,
            String questionId, double newScore, String reason) {
        return ActionResult.notConnected(Capability.SCORE_ADJUSTMENT);
    }

    /**
     * Returns Not connected yet: no service stores manual marks.
     */
    @Override
    public ActionResult undoAdjustment(ScoreAdjustment adjustment) {
        return ActionResult.notConnected(Capability.SCORE_ADJUSTMENT);
    }

    /**
     * Returns an empty list, because no service stores manual marks.
     */
    @Override
    public List<ScoreAdjustment> listAdjustments(Submission submission) {
        return new ArrayList<>();
    }

    /**
     * Calls ExportService.export for the whole run.
     */
    @Override
    public ActionResult export(GradingRun gradingRun,
            List<Submission> scope, String formatName) {
        if (gradingRun == null) {
            return ActionResult.failed("There are no results to export "
                    + "yet. Grade a batch first.");
        }

        Path outputFolder = Paths.get(config.getString(
                ConfigKeys.OUTPUT_DIR));
        try {
            Path written = services.getExportService().export(gradingRun,
                    formatName, outputFolder);
            if (written == null) {
                return ActionResult.failed(waitingMessage(
                        Capability.EXPORT_RESULTS));
            }

            String note = "";
            if (scope.size() < gradingRun.getSubmissions().size()) {
                note = " The whole run was exported, because exporting "
                        + "only chosen students is not connected yet.";
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
            return ActionResult.failed(waitingMessage(Capability.BACKUP));
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
        try {
            services.getHistoryService().restore(backupFile);
        } catch (UnsupportedOperationException exception) {
            return ActionResult.notConnected(Capability.RESTORE);
        } catch (RuntimeException exception) {
            return ActionResult.failed("The restore of "
                    + backupFile.getFileName() + " did not finish ("
                    + exception + "). Check the History screen before "
                    + "grading again.");
        }

        if (Capability.RESTORE.getStatus() != CapabilityStatus.WORKS_NOW) {
            return ActionResult.notConnected(Capability.RESTORE);
        }
        // Forget session runs so getRun reads the restored history.
        runsBySection.clear();
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
        for (Map.Entry<String, String> change : changes.entrySet()) {
            String problem = validateSetting(change.getKey(),
                    change.getValue());
            if (!problem.isEmpty()) {
                return ActionResult.failed(problem);
            }
        }

        for (Map.Entry<String, String> change : changes.entrySet()) {
            services.getSettingsService().updateSetting(change.getKey(),
                    change.getValue());
        }

        try {
            services.getSettingsService().save();
        } catch (IOException exception) {
            return ActionResult.failed("The settings could not be saved: "
                    + exception.getMessage());
        }
        return ActionResult.done("Saved " + changes.size() + " setting"
                + plural(changes.size()) + " to config.properties.");
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

    // Checks one submission file without opening it.
    private ImportItem inspectSubmission(Path path, String name) {
        boolean isFolder = InputValidator.isReadableDirectory(path);
        if (!isFolder && !InputValidator.isReadableFile(path)) {
            return unreadable(InputKind.SUBMISSION, path);
        }
        if (!isFolder && !name.toLowerCase().endsWith(ZIP_EXTENSION)) {
            return new ImportItem(InputKind.SUBMISSION, path, name,
                    ImportState.INVALID, "This is not a ZIP file.");
        }
        return new ImportItem(InputKind.SUBMISSION, path,
                usernameOf(name), ImportState.ACCEPTED, "Ready to grade.");
    }

    // Returns the item for a path InputValidator could not read.
    private ImportItem unreadable(InputKind kind, Path path) {
        String message = "GradeZZY could not read this. Check that it "
                + "exists and is not open in another program.";
        CapabilityStatus checks = Capability.INPUT_CHECKS.getStatus();
        if (checks != CapabilityStatus.WORKS_NOW) {
            message += " While " + Capability.INPUT_CHECKS.getServiceMethod()
                    + " is a placeholder, every file is reported this way.";
        }
        return new ImportItem(kind, path, "", ImportState.INVALID, message);
    }

    // Guesses the username from a zip name; SubmissionLoader sets the real one.
    private String usernameOf(String fileName) {
        String withoutPrefix = fileName.replaceFirst(
                config.getString(ConfigKeys.ZIP_NAME_PREFIX_PATTERN), "");
        if (withoutPrefix.toLowerCase().endsWith(ZIP_EXTENSION)) {
            return withoutPrefix.substring(0,
                    withoutPrefix.length() - ZIP_EXTENSION.length());
        }
        return withoutPrefix;
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
        task.setOnFailed(event -> callback.onFailed(
                describeFailure(task.getException())));

        Thread worker = new Thread(task, "gradezzy-grading");
        worker.setDaemon(true);
        worker.start();
    }

    // Stores and reports the run a finished task returned.
    private void finishTask(GradingTask task, RunCallback callback,
            Capability capability) {
        GradingRun run = task.getValue();
        if (run == null) {
            callback.onFailed(waitingMessage(capability));
            return;
        }
        if (run.getSection() != null) {
            runsBySection.put(run.getSection().getLabel(), run);
        }
        callback.onFinished(run);
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

    // Says whether the anomaly reached the expected repair state.
    private ActionResult checkFixStatus(Anomaly anomaly, FixStatus wanted,
            String doneMessage) {
        if (anomaly.getFixStatus() == wanted) {
            return ActionResult.done(doneMessage);
        }
        return ActionResult.failed(waitingMessage(
                Capability.REPAIR_DECISIONS));
    }

    // Returns "s" for any count other than one.
    private String plural(int count) {
        if (count == 1) {
            return "";
        }
        return "s";
    }
}
