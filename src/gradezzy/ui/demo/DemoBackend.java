/*
 * File: DemoBackend.java
 *
 * Purpose: Answers every screen from synthetic data held in memory.
 *
 * Where it fits: Chosen with "sh run.sh --demo". It never reads or
 * writes files, starts processes, opens the database or saves settings.
 */
package gradezzy.ui.demo;

import java.nio.file.Path;
import java.time.LocalDateTime;
import java.time.format.DateTimeFormatter;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;


import gradezzy.config.AppConfig;
import gradezzy.config.ConfigKeys;
import gradezzy.grading.ProgressListener;
import gradezzy.model.Anomaly;
import gradezzy.model.AnomalyType;
import gradezzy.model.Assessment;
import gradezzy.model.AuditEntry;
import gradezzy.model.FixStatus;
import gradezzy.model.GradingRun;
import gradezzy.model.Question;
import gradezzy.model.QuestionResult;
import gradezzy.model.Section;
import gradezzy.model.Student;
import gradezzy.model.Submission;
import gradezzy.model.SubmissionStatus;
import gradezzy.plagiarism.SimilarityPair;
import gradezzy.service.AssessmentFactory;
import gradezzy.service.ServiceRegistry;
import gradezzy.stats.StatisticsSummary;
import gradezzy.ui.backend.ActionResult;
import gradezzy.ui.backend.Capability;
import gradezzy.ui.backend.DashboardBackend;
import gradezzy.ui.backend.ImportBatch;
import gradezzy.ui.backend.ImportItem;
import gradezzy.ui.backend.ImportState;
import gradezzy.ui.backend.InputKind;
import gradezzy.ui.backend.RepairItem;
import gradezzy.ui.backend.ResultState;
import gradezzy.ui.backend.ReviewDecision;
import gradezzy.ui.backend.ReviewRules;
import gradezzy.ui.backend.RunCallback;
import gradezzy.ui.backend.RunRecord;
import gradezzy.ui.backend.ScoreAdjustment;
import gradezzy.ui.backend.SimilarityDetail;

/**
 * The backend used in demo mode.
 */
public final class DemoBackend implements DashboardBackend {

    // The similarity of the sample pair
    private static final double SAMPLE_SIMILARITY = 0.84;

    // The question of the sample similar pair
    private static final String SIMILAR_QUESTION = "Q2a";

    // The file of the sample similar pair
    private static final String SIMILAR_FILE = "Q2/Q2a.java";

    // The first number given to a run started in demo mode
    private static final int FIRST_NEW_RUN = 25;

    // Seconds between two seeded audit entries
    private static final long SEED_STEP_SECONDS = 47L;

    // Minutes after a run started when its sample snapshot was saved
    private static final long SNAPSHOT_DELAY_MINUTES = 16L;

    // How times are written in backup descriptions
    private static final DateTimeFormatter TIME_FORMAT =
            DateTimeFormatter.ofPattern("d MMM yyyy, HH:mm");

    // Sample audit entries, oldest first: action, then detail
    private static final String[][] AUDIT_SEED = {
        {"Run created", "CS102 G1, Lab Test, 6 submissions"},
        {"Fix applied automatically",
            "learner.bravo: folder renamed from RenameToYourUsername"},
        {"Fix approved", "learner.alpha: wrapper folder added"},
        {"Flag reviewed", "learner.delta: untouched starter file"},
        {"Grading cancelled", "Run 024 stopped before learner.foxtrot"}};

    // Every service; only side-effect-free methods are used
    private final ServiceRegistry services;

    // The application settings, read but never written
    private final AppConfig config;

    // The assessment, built from the configuration
    private final Assessment assessment;

    // Hands out anomaly ids
    private final DemoIdCounter ids = new DemoIdCounter();

    // The run shown for each section, by section label
    private final Map<String, GradingRun> runsBySection = new HashMap<>();

    // Every run on the Runs tab, newest first
    private final List<DemoHistoryItem> history = new ArrayList<>();

    // Review decisions, by anomaly id
    private final Map<String, ReviewDecision> decisions = new HashMap<>();

    // The audit log, newest first
    private final List<AuditEntry> audit = new ArrayList<>();

    // Manual score changes, by username, oldest first
    private final Map<String, List<ScoreAdjustment>> adjustments =
            new HashMap<>();

    // Settings changed in this session, by key
    private final Map<String, String> settingChanges = new HashMap<>();

    // How many times each username was graded in this session
    private final Map<String, Integer> gradeCounts = new HashMap<>();

    // The six-student run of the sample section
    private GradingRun sampleRun;

    // The 240-student run, built when first needed
    private GradingRun largeRun = null;

    // Whether the sample section shows the large cohort
    private boolean useLarge = false;

    // The simulated run in progress, or null
    private DemoGrader grader = null;

    // Whether the self-test steps the simulated run itself
    private boolean manualStepping = false;

    // The number of the next run started in demo mode
    private int nextRunNumber = FIRST_NEW_RUN;

    /**
     * Creates the demo backend with its sample runs.
     *
     * @param services every service; only methods that change nothing are used
     */
    public DemoBackend(ServiceRegistry services) {
        this.services = services;
        this.config = services.getConfig();
        this.assessment = new AssessmentFactory(config).create();

        Section section = sampleSection();
        sampleRun = DemoData.buildCurrentRun(assessment, section,
                getRunTimeoutSeconds(), ids);
        GradingRun earlierRun = DemoData.buildEarlierRun(assessment,
                section, getRunTimeoutSeconds(), ids);
        settleSampleDecisions(sampleRun);

        runsBySection.put(section.getLabel(), sampleRun);
        history.add(new DemoHistoryItem(sampleRun, snapshotText(sampleRun,
                "A"), true));
        history.add(new DemoHistoryItem(earlierRun,
                snapshotText(earlierRun, "A"), true));
        seedAudit();
    }

    // ---------------------------------------------------- self-test hooks

    /**
     * Makes simulated runs wait for stepGrading instead of a timer.
     *
     * @param manual true to step by hand
     */
    public void setManualStepping(boolean manual) {
        this.manualStepping = manual;
    }

    /**
     * Moves the simulated run forward. Used by the self-test.
     *
     * @param steps the most stages to run
     * @return the number of stages run
     */
    public int stepGrading(int steps) {
        int done = 0;
        while (grader != null && done < steps && grader.step()) {
            done++;
        }
        return done;
    }

    /**
     * Runs the simulated run to its end. Used by the self-test.
     */
    public void finishGrading() {
        if (grader != null) {
            grader.runToEnd();
        }
    }

    /**
     * Returns the number of submissions the simulated run has finished.
     *
     * @return the count, or 0 when nothing is running
     */
    public int getFinishedCount() {
        if (grader == null) {
            return 0;
        }
        return grader.getFinishedCount();
    }

    /**
     * Returns how many times a student was graded in this session.
     *
     * @param username the student
     * @return the count
     */
    public int getGradeCount(String username) {
        if (!gradeCounts.containsKey(username)) {
            return 0;
        }
        return gradeCounts.get(username);
    }

    // ------------------------------------------------------------ mode

    /**
     * Returns true, because this backend uses synthetic data.
     */
    @Override
    public boolean isDemo() {
        return true;
    }

    /**
     * Allows every feature, simulated in memory.
     */
    @Override
    public boolean supports(Capability capability) {
        return true;
    }

    // --------------------------------------------------------- context

    /**
     * Returns the sample module and an empty second module.
     */
    @Override
    public List<String> getModuleCodes() {
        List<String> codes = new ArrayList<>();
        codes.add(DemoData.MODULE_CODE);
        codes.add(DemoData.EMPTY_MODULE_CODE);
        return codes;
    }

    /**
     * Returns the configured sections, or one for the empty module.
     */
    @Override
    public List<String> getSectionCodes(String moduleCode) {
        if (DemoData.EMPTY_MODULE_CODE.equals(moduleCode)) {
            List<String> codes = new ArrayList<>();
            codes.add(DemoData.EMPTY_MODULE_SECTION);
            return codes;
        }
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
     * Returns the same assessment: the demo has no tester files.
     */
    @Override
    public Assessment getAssessment(Path testersDirectory) {
        return assessment;
    }

    /**
     * Returns no problems: the demo has no tester files.
     */
    @Override
    public List<String> checkMaxScores(Path testersDirectory) {
        return new ArrayList<>();
    }

    /**
     * Returns the assessment's total, so the demo always adds up.
     */
    @Override
    public double getScoreSheetDenominator(Path scoreSheetFile) {
        return assessment.getMaxTotal();
    }

    /**
     * Returns the configured tester time limit.
     */
    @Override
    public int getRunTimeoutSeconds() {
        return parseInt(getSetting(ConfigKeys.RUN_TIMEOUT_SECONDS),
                config.getInt(ConfigKeys.RUN_TIMEOUT_SECONDS));
    }

    /**
     * Returns the configured compile time limit.
     */
    @Override
    public int getCompileTimeoutSeconds() {
        return parseInt(getSetting(ConfigKeys.COMPILE_TIMEOUT_SECONDS),
                config.getInt(ConfigKeys.COMPILE_TIMEOUT_SECONDS));
    }

    // ------------------------------------------------------------ runs

    /**
     * Returns the run of a section, or the large cohort when it is on.
     */
    @Override
    public GradingRun getRun(Section section) {
        if (section == null) {
            return null;
        }
        GradingRun run = runsBySection.get(section.getLabel());
        if (run == sampleRun && useLarge) {
            return largeRun;
        }
        return run;
    }

    /**
     * Lists the sample runs and the runs started in this session.
     */
    @Override
    public List<RunRecord> listRuns() {
        List<RunRecord> records = new ArrayList<>();
        for (DemoHistoryItem entry : history) {
            GradingRun run = entry.getRun();
            GradingRun shown = runsBySection.get(
                    run.getSection().getLabel());
            records.add(new RunRecord(run, describeRun(run),
                    entry.getBackupText(), entry.isRestorable(),
                    shown == run));
        }
        return records;
    }

    // ---------------------------------------------------------- import

    /**
     * Returns synthetic paths that stand for sample files.
     */
    @Override
    public List<Path> getSampleFiles(InputKind kind) {
        List<Path> paths = new ArrayList<>();
        if (kind == InputKind.SUBMISSION) {
            for (String username : DemoData.USERNAMES) {
                paths.add(DemoData.samplePath(username
                        + DemoData.ZIP_EXTENSION));
            }
        } else if (kind == InputKind.ROSTER) {
            paths.add(DemoData.samplePath(DemoData.SAMPLE_ROSTER));
        } else if (kind == InputKind.TESTERS) {
            paths.add(DemoData.samplePath(DemoData.SAMPLE_TESTERS));
        } else {
            paths.add(DemoData.samplePath(DemoData.SAMPLE_STARTER));
        }
        return paths;
    }

    /**
     * Returns null, because demo mode reads no configured files.
     */
    @Override
    public Path getConfiguredPath(InputKind kind) {
        return null;
    }

    /**
     * Judges a sample file by its name. No file is opened.
     */
    @Override
    public ImportItem inspect(InputKind kind, Path path) {
        String name = String.valueOf(path.getFileName());
        if (kind == InputKind.ROSTER) {
            return new ImportItem(kind, path, "", ImportState.ACCEPTED,
                    "Six synthetic students.");
        } else if (kind == InputKind.TESTERS) {
            return new ImportItem(kind, path, "", ImportState.ACCEPTED,
                    "Five tester files and two data files.");
        } else if (kind == InputKind.STARTER) {
            return new ImportItem(kind, path, "", ImportState.ACCEPTED,
                    "Starter files for the untouched-answer check.");
        }

        String username = name.replace(DemoData.REPLACEMENT_SUFFIX, "")
                .replace(DemoData.ZIP_EXTENSION, "");
        if (name.equals(DemoData.INVALID_ZIP_USERNAME
                + DemoData.ZIP_EXTENSION)) {
            return new ImportItem(kind, path, username, ImportState.INVALID,
                    "This file could not be opened as a ZIP archive. "
                    + "Replace it with a new copy.");
        }
        if (name.startsWith("learner.echo")) {
            return new ImportItem(kind, path, username, ImportState.WARNING,
                    "Holds an extra resources folder. It will still be "
                    + "graded.");
        }
        return new ImportItem(kind, path, username, ImportState.ACCEPTED,
                "Ready to grade.");
    }

    // --------------------------------------------------------- grading

    /**
     * Starts a simulated run of the batch and records it in the audit
     * log, as GradingService does in live mode.
     */
    @Override
    public void startGrading(ImportBatch batch, Section section,
            ProgressListener listener, RunCallback callback) {
        GradingRun run = new GradingRun(String.format("%03d",
                nextRunNumber), assessment, section, LocalDateTime.now());
        nextRunNumber++;

        for (ImportItem item : batch.getItems(InputKind.SUBMISSION)) {
            if (batch.getEffectiveState(item).isUsable()) {
                Submission submission = new Submission(item.getUsername(),
                        section, item.getPath());
                submission.setStudent(DemoData.studentFor(
                        item.getUsername()));
                run.addSubmission(submission);
            }
        }
        recordAudit("Grading started", "Grading "
                + run.getSubmissions().size() + " submission(s) for "
                + section.getLabel() + ".");
        startGrader(run, run.getSubmissions(), listener, callback, true);
    }

    /**
     * Stops the simulated run after the current submission.
     */
    @Override
    public void cancelGrading() {
        if (grader != null) {
            grader.cancel();
        }
    }

    /**
     * Grades the cancelled and pending submissions of a run.
     */
    @Override
    public void resumeGrading(GradingRun gradingRun,
            ProgressListener listener, RunCallback callback) {
        List<Submission> remaining = new ArrayList<>();
        for (Submission submission : gradingRun.getSubmissions()) {
            SubmissionStatus status = submission.getStatus();
            if (status == SubmissionStatus.CANCELLED
                    || status == SubmissionStatus.PENDING) {
                remaining.add(submission);
            }
        }
        startGrader(gradingRun, remaining, listener, callback, false);
    }

    /**
     * Grades the failed and cancelled submissions of a run again.
     */
    @Override
    public void retryFailedSubmissions(GradingRun gradingRun,
            ProgressListener listener, RunCallback callback) {
        List<Submission> retried = new ArrayList<>();
        for (Submission submission : gradingRun.getSubmissions()) {
            SubmissionStatus status = submission.getStatus();
            if (status == SubmissionStatus.FAILED
                    || status == SubmissionStatus.CANCELLED) {
                submission.getAnomalies().clear();
                submission.getQuestionResults().clear();
                retried.add(submission);
            }
        }
        startGrader(gradingRun, retried, listener, callback, false);
    }

    /**
     * Returns true for any run, because the simulated grader can grade
     * any run again.
     */
    @Override
    public boolean canRetryFailed(GradingRun gradingRun) {
        return gradingRun != null;
    }

    /**
     * Returns no problems: the demo testers are always fine.
     */
    @Override
    public List<String> checkTesters(Path testersDirectory,
            Path starterDirectory) {
        return new ArrayList<>();
    }

    /**
     * Returns no students: every demo student has a submission.
     */
    @Override
    public List<Student> listMissingStudents(GradingRun gradingRun) {
        return new ArrayList<>();
    }

    /**
     * Runs one question again. The timed-out sample question passes;
     * any other question keeps its result.
     */
    @Override
    public ActionResult retryQuestion(Submission submission,
            String questionId) {
        QuestionResult result = findResult(submission, questionId);
        if (result == null) {
            return ActionResult.failed(questionId + " has no result to "
                    + "retry. Grade the submission first.");
        }

        boolean sampleTimeout = submission.getUsername().equals(
                DemoData.TIMEOUT_USERNAME)
                && questionId.equals(DemoData.TIMEOUT_QUESTION)
                && result.isTimedOut();
        if (!sampleTimeout) {
            return ActionResult.done("Retried " + questionId + " for "
                    + submission.getUsername() + ". The result did not "
                    + "change.");
        }

        DemoData.applyPassingRetry(result);
        for (Anomaly anomaly : submission.getAnomalies()) {
            if (anomaly.getType() == AnomalyType.TIMEOUT) {
                decisions.put(anomaly.getId(), ReviewDecision.RESOLVED);
            }
        }
        return ActionResult.done("Retried " + questionId + " for "
                + submission.getUsername() + ". Every case passed, so "
                + questionId + " is now " + formatScore(result.getScore())
                + " of " + formatScore(result.getQuestion().getMaxScore())
                + ". Other marks did not change.");
    }

    /**
     * Grades one submission again from the start.
     */
    @Override
    public ActionResult retrySubmission(Submission submission) {
        if (DemoData.indexOf(submission.getUsername()) < 0) {
            return ActionResult.done("Graded " + submission.getUsername()
                    + " again. The result did not change.");
        }
        DemoData.applyOutcome(submission, assessment,
                getRunTimeoutSeconds(), ids);
        countGrade(submission.getUsername());
        return ActionResult.done("Graded " + submission.getUsername()
                + " again. Total: " + formatScore(submission.getTotalScore())
                + " of " + formatScore(assessment.getMaxTotal()) + ".");
    }

    // ------------------------------------------------- anomalies, repairs

    /**
     * Lists every anomaly of the run, in submission order.
     */
    @Override
    public List<Anomaly> listAnomalies(GradingRun gradingRun) {
        List<Anomaly> anomalies = new ArrayList<>();
        if (gradingRun == null) {
            return anomalies;
        }
        for (Submission submission : gradingRun.getSubmissions()) {
            anomalies.addAll(submission.getAnomalies());
        }
        return anomalies;
    }

    /**
     * Returns the decision recorded in this session.
     */
    @Override
    public ReviewDecision getDecision(Anomaly anomaly) {
        if (!decisions.containsKey(anomaly.getId())) {
            return ReviewDecision.OPEN;
        }
        return decisions.get(anomaly.getId());
    }

    /**
     * Records the decision in memory.
     */
    @Override
    public ActionResult setDecision(Anomaly anomaly,
            ReviewDecision decision) {
        decisions.put(anomaly.getId(), decision);
        return ActionResult.done("Marked the flag for "
                + anomaly.getUsername() + " as "
                + decision.getLabel().toLowerCase() + ". No mark changed.");
    }

    /**
     * Applies ReviewRules.needsReview with the recorded decision.
     */
    @Override
    public boolean needsReview(Anomaly anomaly) {
        return ReviewRules.needsReview(anomaly, getDecision(anomaly));
    }

    /**
     * Lists the validation findings with synthetic before and after values.
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

    /**
     * Marks the repair approved.
     */
    @Override
    public ActionResult approveRepair(RepairItem repair) {
        if (repair.isBlocked()) {
            return ActionResult.failed("No automatic fix exists for "
                    + "this finding. Handle it by hand.");
        }
        repair.getAnomaly().setFixStatus(FixStatus.APPROVED);
        return ActionResult.done("Approved the fix for "
                + repair.getAnomaly().getUsername() + ".");
    }

    /**
     * Marks the repair undone.
     */
    @Override
    public ActionResult rejectRepair(RepairItem repair) {
        if (repair.isBlocked()) {
            return ActionResult.failed("No automatic fix exists for "
                    + "this finding. Handle it by hand.");
        }
        repair.getAnomaly().setFixStatus(FixStatus.UNDONE);
        return ActionResult.done("Rejected the fix for "
                + repair.getAnomaly().getUsername() + ". The working copy "
                + "was put back. The original ZIP was never changed.");
    }

    /**
     * Returns the repair to Applied.
     */
    @Override
    public ActionResult undoRepairDecision(RepairItem repair) {
        if (repair.isBlocked()) {
            return ActionResult.failed("This finding has no fix to "
                    + "undo.");
        }
        repair.getAnomaly().setFixStatus(FixStatus.APPLIED);
        return ActionResult.done("The fix for "
                + repair.getAnomaly().getUsername() + " waits for review "
                + "again.");
    }

    // --------------------------------------------------- search, source

    /**
     * Searches the synthetic source files.
     */
    @Override
    public List<Submission> searchCode(GradingRun gradingRun,
            String codeFragment) {
        List<Submission> matches = new ArrayList<>();
        if (gradingRun == null || codeFragment == null
                || codeFragment.isEmpty()) {
            return matches;
        }
        for (Submission submission : gradingRun.getSubmissions()) {
            if (sourceContains(submission, codeFragment)) {
                matches.add(submission);
            }
        }
        return matches;
    }

    /**
     * Returns the synthetic source text.
     */
    @Override
    public String readSourceFile(Submission submission,
            String relativePath) {
        return DemoData.sourceText(submission.getUsername(), relativePath);
    }

    // ----------------------------------------------------------- marks

    /**
     * Changes the mark in memory and keeps the earlier one for undo.
     */
    @Override
    public ActionResult adjustScore(Submission submission,
            String questionId, double newScore, String reason) {
        QuestionResult result = findResult(submission, questionId);
        if (result == null) {
            return ActionResult.failed(questionId + " has no result to "
                    + "adjust. Grade the submission first.");
        }
        if (reason == null || reason.trim().isEmpty()) {
            return ActionResult.failed("Enter a reason for the change.");
        }
        double max = result.getQuestion().getMaxScore();
        if (newScore < 0.0 || newScore > max) {
            return ActionResult.failed("Enter a mark from 0 to "
                    + formatScore(max) + ".");
        }

        ScoreAdjustment adjustment = new ScoreAdjustment(
                submission.getUsername(), questionId, result.getScore(),
                newScore, reason.trim(), LocalDateTime.now());
        result.setScore(newScore);
        adjustmentsOf(submission.getUsername()).add(adjustment);
        return ActionResult.done("Saved. " + questionId + " for "
                + submission.getUsername() + " changed from "
                + formatScore(adjustment.getPreviousScore()) + " to "
                + formatScore(newScore) + ".");
    }

    /**
     * Restores the earlier mark and forgets the change.
     */
    @Override
    public ActionResult undoAdjustment(ScoreAdjustment adjustment) {
        GradingRun run = findRunOf(adjustment.getUsername());
        Submission submission = findSubmission(run,
                adjustment.getUsername());
        if (submission == null) {
            return ActionResult.failed("The student is no longer in the "
                    + "run shown.");
        }
        QuestionResult result = findResult(submission,
                adjustment.getQuestionId());
        if (result == null) {
            return ActionResult.failed("The question has no result.");
        }
        result.setScore(adjustment.getPreviousScore());
        adjustmentsOf(adjustment.getUsername()).remove(adjustment);
        return ActionResult.done("Undid the change. "
                + adjustment.getQuestionId() + " is back to "
                + formatScore(adjustment.getPreviousScore()) + ".");
    }

    /**
     * Returns the changes made in this session, newest first.
     */
    @Override
    public List<ScoreAdjustment> listAdjustments(Submission submission) {
        List<ScoreAdjustment> oldestFirst =
                adjustmentsOf(submission.getUsername());
        List<ScoreAdjustment> newestFirst = new ArrayList<>();
        for (int index = oldestFirst.size() - 1; index >= 0; index--) {
            newestFirst.add(oldestFirst.get(index));
        }
        return newestFirst;
    }

    // ---------------------------------------------------------- export

    /**
     * Builds a preview of the export. No file is written.
     */
    @Override
    public ActionResult export(GradingRun gradingRun,
            List<Submission> scope, String formatName) {
        if (gradingRun == null || scope.isEmpty()) {
            return ActionResult.failed("Choose at least one student to "
                    + "export.");
        }

        String preview;
        if ("json".equalsIgnoreCase(formatName)) {
            preview = DemoExports.toJson(gradingRun, scope);
        } else {
            preview = DemoExports.toCsv(gradingRun, scope);
        }
        return ActionResult.preview("Previewed " + formatName.toUpperCase()
                + " for " + scope.size() + " student"
                + plural(scope.size()) + ". Demo mode writes no file.",
                preview);
    }

    // ------------------------------------------------------ statistics

    /**
     * Works out the statistics of the run shown for the section.
     */
    @Override
    public StatisticsSummary summarise(String moduleCode,
            String sectionCode) {
        GradingRun run = getRun(new Section(moduleCode, sectionCode));
        return DemoStatistics.summarise(run,
                config.getDouble(ConfigKeys.PASS_MARK_PERCENT),
                config.getDouble(ConfigKeys.MARK_BAND_WIDTH_PERCENT));
    }

    // ------------------------------------------------- history, audit

    /**
     * Records a new snapshot of the current run in memory.
     */
    @Override
    public ActionResult backupNow() {
        DemoHistoryItem entry = findEntry(sampleRunShown());
        if (entry == null) {
            return ActionResult.failed("There is no run to back up yet.");
        }
        String runId = entry.getRun().getId();
        entry.recordSnapshot("Snapshot " + runId + "-B, "
                + LocalDateTime.now().format(TIME_FORMAT));
        return ActionResult.done("Backed up run " + runId
                + ". Demo mode keeps the snapshot in memory only.");
    }

    /**
     * Shows an earlier run after saving a recovery snapshot of the current one.
     */
    @Override
    public ActionResult restore(RunRecord record) {
        if (record.isCurrent()) {
            return ActionResult.failed("Run " + record.getRun().getId()
                    + " is already the run shown.");
        }
        if (!record.isRestorable()) {
            return ActionResult.failed("Run " + record.getRun().getId()
                    + " has no snapshot to restore.");
        }

        String label = record.getRun().getSection().getLabel();
        GradingRun current = runsBySection.get(label);
        DemoHistoryItem currentEntry = findEntry(current);
        if (currentEntry != null) {
            currentEntry.recordSnapshot("Recovery snapshot, "
                    + LocalDateTime.now().format(TIME_FORMAT));
        }
        runsBySection.put(label, record.getRun());
        if (record.getRun() != sampleRun) {
            useLarge = false;
        }
        String currentId = "none";
        if (current != null) {
            currentId = current.getId();
        }
        return ActionResult.done("Restored run " + record.getRun().getId()
                + ". A recovery snapshot of run " + currentId + " was "
                + "saved first, so you can restore it to go back.");
    }

    /**
     * Restores nothing, because demo mode never reads files.
     */
    @Override
    public ActionResult restoreBackup(Path backupFile) {
        return ActionResult.failed("Demo mode never reads backup files. "
                + "Select a run with a snapshot on History and press "
                + "Restore instead.");
    }

    /**
     * Returns the audit log of this session, newest first.
     */
    @Override
    public List<AuditEntry> listAuditEntries() {
        return new ArrayList<>(audit);
    }

    /**
     * Adds an entry to the audit log in memory.
     */
    @Override
    public void recordAudit(String action, String detail) {
        audit.add(0, new AuditEntry(LocalDateTime.now(), action, detail));
    }

    // -------------------------------------------------------- settings

    /**
     * Returns the value changed in this session, or the configured one.
     */
    @Override
    public String getSetting(String key) {
        String changed = settingChanges.get(key);
        if (changed != null) {
            return changed;
        }
        return services.getSettingsService().getSetting(key);
    }

    /**
     * Calls SettingsService.validate, which writes nothing.
     */
    @Override
    public String validateSetting(String key, String value) {
        return services.getSettingsService().validate(key, value);
    }

    /**
     * Keeps valid changes for this session only, not in config.properties.
     */
    @Override
    public ActionResult saveSettings(Map<String, String> changes) {
        return saveSettings(new ArrayList<>(changes.keySet()), changes);
    }

    /**
     * Checks the changes in order and keeps them for this session only,
     * not in config.properties.
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
            settingChanges.put(key, changes.get(key));
        }
        return ActionResult.done("Saved " + changes.size() + " setting"
                + plural(changes.size()) + " for this demo session. "
                + "config.properties was not changed.");
    }

    // ------------------------------------------------------ similarity

    /**
     * Returns the sample pair when both students were graded.
     */
    @Override
    public List<SimilarityPair> listSimilarPairs(GradingRun gradingRun) {
        List<SimilarityPair> pairs = new ArrayList<>();
        Submission first = findSubmission(gradingRun,
                DemoData.SIMILAR_FIRST_USERNAME);
        Submission second = findSubmission(gradingRun,
                DemoData.SIMILAR_SECOND_USERNAME);
        if (first != null && second != null && ResultState.hasTotal(first)
                && ResultState.hasTotal(second)) {
            pairs.add(new SimilarityPair(first.getUsername(),
                    second.getUsername(), SIMILAR_QUESTION,
                    SAMPLE_SIMILARITY));
        }
        return pairs;
    }

    /**
     * Returns synthetic excerpts and the files left out of the comparison.
     */
    @Override
    public SimilarityDetail getSimilarityDetail(SimilarityPair pair) {
        List<String> excluded = new ArrayList<>();
        excluded.add("RenameToYourUsername/Q2/Q2a.java (starter file)");
        excluded.add("Q2aTester.java (tester)");
        excluded.add("DataException.class (provided class)");
        excluded.add("personstester.txt, studentstester.txt (tester data)");
        String excerpt = "for (int i = 0; i < items.length; i++) {\n"
                + "    if (items[i].getScore() > best) {\n"
                + "        best = items[i].getScore();\n"
                + "    }\n"
                + "}\n";
        String renamed = "for (int k = 0; k < list.length; k++) {\n"
                + "    if (list[k].getScore() > top) {\n"
                + "        top = list[k].getScore();\n"
                + "    }\n"
                + "}\n";
        return new SimilarityDetail(pair, SIMILAR_FILE, SIMILAR_FILE,
                excerpt, renamed, excluded);
    }

    /**
     * Returns plagiarism.enabled, as changed in this session.
     */
    @Override
    public boolean isSimilarityEnabled() {
        return Boolean.parseBoolean(getSetting(
                ConfigKeys.PLAGIARISM_ENABLED));
    }

    // ------------------------------------------------------------ demo

    /**
     * Switches the sample section between six and 240 students.
     */
    @Override
    public void useLargeSampleCohort(boolean large) {
        if (large && largeRun == null) {
            largeRun = DemoData.buildLargeCohort(assessment,
                    sampleSection(), getRunTimeoutSeconds(), ids);
            settleSampleDecisions(largeRun);
        }
        useLarge = large;
    }

    // ------------------------------------------------------- helpers

    // Starts a simulated run and reports when it ends.
    private void startGrader(GradingRun run, List<Submission> queue,
            ProgressListener listener, RunCallback callback,
            boolean newRun) {
        DemoOutcome outcome = submission -> gradeOne(submission);
        grader = new DemoGrader(run, queue, listener, outcome,
                () -> finishRun(run, callback, newRun));
        grader.start(!manualStepping);
    }

    // Gives a finished submission its sample results.
    private void gradeOne(Submission submission) {
        DemoData.applyOutcome(submission, assessment,
                getRunTimeoutSeconds(), ids);
        countGrade(submission.getUsername());
    }

    // Shows a finished run and adds it to the history.
    private void finishRun(GradingRun run, RunCallback callback,
            boolean newRun) {
        runsBySection.put(run.getSection().getLabel(), run);
        if (newRun) {
            history.add(0, new DemoHistoryItem(run, "No snapshot yet",
                    false));
            useLarge = false;
        }
        callback.onFinished(run);
    }

    // Records that a student was graded once more.
    private void countGrade(String username) {
        gradeCounts.put(username, getGradeCount(username) + 1);
    }

    // Approves the sample repair and marks the sample flags reviewed.
    private void settleSampleDecisions(GradingRun run) {
        for (Submission submission : run.getSubmissions()) {
            for (Anomaly anomaly : submission.getAnomalies()) {
                String username = submission.getUsername();
                if (anomaly.getType() == AnomalyType.MISSING_WRAPPER_FOLDER
                        && username.equals(
                        DemoData.APPROVED_REPAIR_USERNAME)) {
                    anomaly.setFixStatus(FixStatus.APPROVED);
                }
                if (username.equals(DemoData.REVIEWED_USERNAME)) {
                    decisions.put(anomaly.getId(), ReviewDecision.REVIEWED);
                }
            }
        }
    }

    // Adds the sample audit entries, newest first.
    private void seedAudit() {
        for (int index = 0; index < AUDIT_SEED.length; index++) {
            LocalDateTime time = DemoData.CURRENT_RUN_STARTED.plusSeconds(
                    SEED_STEP_SECONDS * index);
            audit.add(0, new AuditEntry(time, AUDIT_SEED[index][0],
                    AUDIT_SEED[index][1]));
        }
    }

    // Creates the repair item of a finding with synthetic values.
    private RepairItem createRepair(Anomaly anomaly) {
        String username = anomaly.getUsername();
        AnomalyType type = anomaly.getType();
        String noRepair = "No automatic fix. Ask for a corrected "
                + "submission.";

        if (type == AnomalyType.MISSING_WRAPPER_FOLDER) {
            return new RepairItem(anomaly, "Q1/, Q2/ and Q3/ at the top of "
                    + "the ZIP", username + "/Q1, Q2 and Q3");
        } else if (type == AnomalyType.WRONG_FOLDER_NAME) {
            return new RepairItem(anomaly, "RenameToYourUsername/",
                    username + "/");
        } else if (type == AnomalyType.MISSING_HEADER_NAME) {
            Student student = DemoData.studentFor(username);
            return new RepairItem(anomaly, "Name: (blank) in Q1a.java",
                    "Name: " + student.getFirstName() + " "
                    + student.getLastName());
        } else if (type == AnomalyType.WRONG_ENCODING) {
            return new RepairItem(anomaly, "Q1a.java saved as UTF-8 with "
                    + "BOM", "Q1a.java saved as UTF-8");
        } else if (type == AnomalyType.WRONG_FILE_NAME) {
            // The full working-copy path shows how long paths wrap.
            Path folder = DemoData.workingCopyPath(username).resolve("Q2");
            return new RepairItem(anomaly,
                    folder.resolve("q2b.java").toString(),
                    "No automatic fix. Rename by hand to "
                    + folder.resolve("Q2b.java") + ".");
        } else if (type == AnomalyType.MISSING_FILE) {
            return new RepairItem(anomaly, "Q1/Q1b.java not found",
                    noRepair);
        }
        return new RepairItem(anomaly, "Question folder not found",
                noRepair);
    }

    // Says whether any synthetic source file of a student holds text.
    private boolean sourceContains(Submission submission, String text) {
        if (submission.getWorkingCopyPath() == null) {
            return false;
        }
        for (String file : DemoData.sourceFiles(submission.getUsername())) {
            if (DemoData.sourceText(submission.getUsername(), file)
                    .contains(text)) {
                return true;
            }
        }
        return false;
    }

    // Describes how far a run got.
    private String describeRun(GradingRun run) {
        int graded = 0;
        for (Submission submission : run.getSubmissions()) {
            if (ResultState.hasTotal(submission)) {
                graded++;
            }
        }
        int waiting = run.getSubmissions().size() - graded;
        if (waiting == 0) {
            return graded + " graded";
        }
        return graded + " graded, " + waiting + " not graded";
    }

    // Describes the sample snapshot of a run.
    private String snapshotText(GradingRun run, String letter) {
        return "Snapshot " + run.getId() + "-" + letter + ", "
                + run.getStartedAt().plusMinutes(SNAPSHOT_DELAY_MINUTES)
                .format(TIME_FORMAT);
    }

    // Finds the history entry of a run.
    private DemoHistoryItem findEntry(GradingRun run) {
        for (DemoHistoryItem entry : history) {
            if (entry.getRun() == run) {
                return entry;
            }
        }
        return null;
    }

    // Returns the run shown for the sample section.
    private GradingRun sampleRunShown() {
        return runsBySection.get(sampleSection().getLabel());
    }

    // Returns the run that holds a student, looking at shown runs first.
    private GradingRun findRunOf(String username) {
        for (GradingRun run : runsBySection.values()) {
            if (findSubmission(run, username) != null) {
                return run;
            }
        }
        if (largeRun != null && findSubmission(largeRun, username) != null) {
            return largeRun;
        }
        return null;
    }

    // Finds a student in a run.
    private Submission findSubmission(GradingRun run, String username) {
        if (run == null) {
            return null;
        }
        for (Submission submission : run.getSubmissions()) {
            if (submission.getUsername().equals(username)) {
                return submission;
            }
        }
        return null;
    }

    // Finds the result of one question.
    private QuestionResult findResult(Submission submission,
            String questionId) {
        for (QuestionResult result : submission.getQuestionResults()) {
            Question question = result.getQuestion();
            if (question.getId().equals(questionId)) {
                return result;
            }
        }
        return null;
    }

    // Returns the list of changes of one student, creating it if needed.
    private List<ScoreAdjustment> adjustmentsOf(String username) {
        List<ScoreAdjustment> list = adjustments.get(username);
        if (list == null) {
            list = new ArrayList<>();
            adjustments.put(username, list);
        }
        return list;
    }

    // Returns the sample section.
    private Section sampleSection() {
        return new Section(DemoData.MODULE_CODE, DemoData.SAMPLE_SECTION);
    }

    // Reads a whole number, or returns a fallback.
    private int parseInt(String text, int fallback) {
        try {
            return Integer.parseInt(text.trim());
        } catch (NumberFormatException exception) {
            return fallback;
        }
    }

    // Writes a mark without a decimal point when it is whole.
    private static String formatScore(double score) {
        if (score == Math.rint(score)) {
            return String.valueOf((long) score);
        }
        return String.valueOf(score);
    }

    // Returns "s" for any count other than one.
    private static String plural(int count) {
        if (count == 1) {
            return "";
        }
        return "s";
    }
}
