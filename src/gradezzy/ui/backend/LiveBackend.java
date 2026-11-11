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
import java.nio.file.InvalidPathException;
import java.nio.file.LinkOption;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.util.ArrayList;
import java.util.Collections;
import java.util.HashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Set;
import java.util.concurrent.ConcurrentHashMap;

import gradezzy.config.AppConfig;
import gradezzy.config.ConfigKeys;
import gradezzy.config.ModuleSections;
import gradezzy.exec.ProcessRunner;
import gradezzy.exec.SourceCompiler;
import gradezzy.fix.FixRecord;
import gradezzy.grading.CompileStage;
import gradezzy.grading.GradingException;
import gradezzy.grading.ProgressListener;
import gradezzy.grading.QuestionRerunner;
import gradezzy.ingest.IngestException;
import gradezzy.ingest.RosterReader;
import gradezzy.model.Anomaly;
import gradezzy.model.AnomalyType;
import gradezzy.model.Assessment;
import gradezzy.model.AuditEntry;
import gradezzy.model.BackupInfo;
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
import gradezzy.modelanswer.ModelAnswerCopy;
import gradezzy.modelanswer.QuestionStrength;
import gradezzy.plagiarism.SimilarityPair;
import gradezzy.report.CsvScoreSheetExporter;
import gradezzy.report.ExportException;
import gradezzy.report.Exporter;
import gradezzy.report.ScoreFormatter;
import gradezzy.service.AnomalyService;
import gradezzy.service.AnswerGroupService;
import gradezzy.service.AssessmentFolderScanner;
import gradezzy.service.AssessmentFactory;
import gradezzy.service.AssessmentLibrary;
import gradezzy.service.ClassFileMatch;
import gradezzy.service.ClassFileMatcher;
import gradezzy.service.DeletedAssessment;
import gradezzy.service.ReapplyResult;
import gradezzy.service.ScoreAdjustmentService;
import gradezzy.service.ServiceRegistry;
import gradezzy.service.UndoResult;
import gradezzy.stats.StatisticsFilter;
import gradezzy.stats.StatisticsSummary;
import gradezzy.testcase.TestCase;
import gradezzy.testcase.TesterMarks;
import gradezzy.testcase.TestCaseSource;
import gradezzy.testcase.TesterGenerator;
import gradezzy.testcase.TypedTestCaseSource;
import gradezzy.testcase.UploadedTesterSource;
import gradezzy.ui.GradingTask;
import gradezzy.ui.GradingTaskKind;
import gradezzy.util.FileUtils;
import gradezzy.util.InputValidator;
import gradezzy.util.TextUtils;

/**
 * The backend that calls the real services.
 *
 * <p>Tier: MUST.
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

    // The activity log actions of grading a student or a question again
    private static final String STUDENT_GRADED_AGAIN = "Student graded again";
    private static final String QUESTION_GRADED_AGAIN =
            "Question graded again";

    // Shown for a student or question that has no mark
    private static final String NOT_GRADED = "not graded";

    // Separates the items of a list in a message, such as kept marks
    private static final String LIST_SEPARATOR = ", ";

    // Comes between two sentences of a notice
    private static final String SENTENCE_GAP = " ";

    // The extension of a Java source file
    private static final String JAVA_EXTENSION = ".java";

    // The name of the thread that grades in the background
    private static final String GRADING_THREAD_NAME = "gradezzy-grading";

    // Separates the username from the anomaly id in a decision key
    private static final String DECISION_KEY_SEPARATOR = "/";

    // Every service of the application
    private final ServiceRegistry services;

    // The application settings
    private final AppConfig config;

    // The assessment in use, built from the configuration again whenever
    // another one is chosen; read by the file-checking thread too
    private volatile Assessment assessment = null;

    // Says what grading will do with a chosen zip file or folder
    private final SubmissionPreview preview;

    // Latest run per section, filled when a grading task ends; a restore
    // clears it. Both happen on the JavaFX thread.
    private final Map<String, GradingRun> runsBySection =
            new ConcurrentHashMap<>();

    // The saved runs, newest first, read once from the database and read
    // again after anything that changes them; null until first needed
    private List<GradingRun> savedRuns = null;

    // The ids of the runs being graded again in the background; several
    // sections can be graded at once
    private final List<String> runsGradedAgain = new ArrayList<>();

    // The assessments deleted since GradeZZY opened, by name in lower
    // case, with the files chosen for them on Set up, for Undo
    private final Map<String, DeletedAssessment> deletedAssessments =
            new HashMap<>();

    // The review decisions, by student and anomaly id: those made in this
    // session and those read back with the saved runs
    private final Map<String, ReviewDecision> decisions =
            new ConcurrentHashMap<>();

    // The ids of the anomalies found by runs started in this session
    private final Set<String> anomaliesFoundNow =
            ConcurrentHashMap.newKeySet();

    // The ids of the runs started in this session
    private final Set<String> runsStartedNow = ConcurrentHashMap.newKeySet();

    // The whole run being exported, or null. An export of only some
    // students hands the score sheet a part of the run, so the Remarks
    // are worked out from this whole run, with every pair and name
    private volatile GradingRun exportedRun = null;

    // The run id of each fix rejected in this session, by anomaly id.
    // Grading again gives the problems new ids, so the card used to
    // reject is no longer found in its run.
    private final Map<String, String> rejectedInRun =
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
        this.preview = new SubmissionPreview(config);
        // The score sheet's Remarks come from the decisions held here
        Exporter scoreSheet = services.getExportService().findExporter(
                CsvScoreSheetExporter.FORMAT_NAME);
        if (scoreSheet instanceof CsvScoreSheetExporter) {
            ((CsvScoreSheetExporter) scoreSheet).setRemarks(
                    part -> remarksOf(part));
        }
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
        String allSections = config.getString(ConfigKeys.SECTION_CODES);
        return ModuleSections.forModule(allSections, byModule, ownOnly,
                moduleCode);
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
        } catch (InvalidPathException exception) {
            return configuredPath(InputKind.ROSTER);
        }
    }

    // Reads a path setting. A new assessment has no score sheet yet, and
    // model answers are optional, so a blank one gives null.
    private Path configuredPath(InputKind kind) {
        if (kind == InputKind.ROSTER) {
            String sheet = config.getString(ConfigKeys.SCORE_SHEET_FILE);
            if (sheet.isEmpty()) {
                return null;
            }
            return Paths.get(sheet);
        } else if (kind == InputKind.TESTERS) {
            return Paths.get(config.getString(ConfigKeys.TESTERS_DIR));
        } else if (kind == InputKind.STARTER) {
            return Paths.get(config.getString(ConfigKeys.STARTER_DIR));
        } else if (kind == InputKind.MODEL_ANSWERS) {
            String answers = config.getString(ConfigKeys.MODEL_ANSWERS_DIR)
                    .trim();
            if (answers.isEmpty()) {
                return null;
            }
            return Paths.get(answers);
        }
        return null;
    }

    /**
     * Checks the path with InputValidator and its file name, and reads a
     * score sheet with RosterReader as grading will. Student zips
     * and folders are checked with SubmissionPreview, a starter folder
     * for the starter file of each question of the assessment in use, a
     * model answers folder for the answer of each question, and a tester
     * folder for the testers it holds.
     */
    @Override
    public ImportItem inspect(InputKind kind, Path path) {
        if (kind == InputKind.SUBMISSION) {
            return inspectSubmission(path);
        }

        if (kind == InputKind.ROSTER) {
            if (!InputValidator.isReadableFile(path)) {
                return unreadable(kind, path);
            }
            String name = String.valueOf(path.getFileName());
            if (!name.toLowerCase(Locale.ROOT).endsWith(CSV_EXTENSION)) {
                return new ImportItem(kind, path, "", ImportState.INVALID,
                        "Choose the score sheet CSV file.");
            }
            // Read it as grading will, so a sheet read wrongly, such as
            // one with ; between values, cannot start grading
            String problem = new RosterReader(config).findProblem(path);
            if (!problem.isEmpty()) {
                return new ImportItem(kind, path, "", ImportState.INVALID,
                        problem);
            }
            return new ImportItem(kind, path, "", ImportState.ACCEPTED,
                    "Score sheet found.");
        }

        if (!InputValidator.isReadableDirectory(path)) {
            return unreadable(kind, path);
        }
        if (kind == InputKind.STARTER) {
            return checkStarter(path, assessment.getQuestions());
        } else if (kind == InputKind.MODEL_ANSWERS) {
            return checkModelAnswers(path, assessment.getQuestions());
        } else if (kind == InputKind.TESTERS) {
            return inspectTesters(path);
        }
        return new ImportItem(kind, path, "", ImportState.ACCEPTED,
                "Folder found.");
    }

    /**
     * Calls CompileStage.checkTesterFolder with the chosen starter folder,
     * or the one in Settings when none is chosen. A folder that is missing
     * or holds no tester is one short problem, and a full path in a
     * problem is shortened to the folder's name. Also says when the
     * starter folder has class files the assessment does not provide.
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

        SourceCompiler compiler = new SourceCompiler(config,
                new ProcessRunner(config));
        CompileStage stage = new CompileStage(compiler, starterDirectory);
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
        addUnlistedClassFiles(starterDirectory, problems);
        return problems;
    }

    // Adds a problem when the starter folder has class files without
    // source, such as Order.class, that the assessment does not list as
    // provided: they are then never given to the students who left them
    // out, whose copies cannot compile.
    private void addUnlistedClassFiles(Path starterDirectory,
            List<String> problems) {
        Path starter = starterDirectory;
        if (starter == null) {
            starter = Paths.get(config.getString(ConfigKeys.STARTER_DIR));
        }
        List<String> unlisted = AssessmentFolderScanner
                .findProvidedClassFiles(starter);
        unlisted.removeAll(config.getList(ConfigKeys.PROVIDED_CLASS_FILES));
        if (!unlisted.isEmpty()) {
            problems.add("The starter folder has " + joinWords(unlisted)
                    + ", but the assessment does not list them under "
                    + "Provided class files, so "
                    + "students who left them out will get a compile "
                    + "error. Add them to the assessment in Settings.");
        }
    }

    /**
     * Calls TesterStrengthService.hasModelAnswers.
     */
    @Override
    public boolean hasModelAnswers() {
        return services.getTesterStrengthService().hasModelAnswers();
    }

    /**
     * Calls TesterStrengthService.checkTesters for the section's module.
     */
    @Override
    public List<QuestionStrength> checkTesterStrength(Section section,
            Path testersDirectory, Path starterDirectory,
            Path modelAnswersDirectory) {
        return services.getTesterStrengthService().checkTesters(
                section.getModuleCode(), testersDirectory,
                starterDirectory, modelAnswersDirectory);
    }

    /**
     * Calls TesterStrengthService.writeReport.
     */
    @Override
    public String saveTesterStrengthReport(Section section,
            List<QuestionStrength> results) {
        try {
            Path report = services.getTesterStrengthService().writeReport(
                    results, section.getModuleCode(),
                    section.getSectionCode());
            return "Saved to " + report + ".";
        } catch (IOException exception) {
            return "The report could not be saved: "
                    + exception.getMessage();
        }
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

    /**
     * Calls GradingService.matchStudent.
     */
    @Override
    public ActionResult matchStudent(GradingRun gradingRun,
            Submission submission, Student student) {
        try {
            services.getGradingService().matchStudent(gradingRun, submission,
                    student);
            forgetSavedRuns();
        } catch (GradingException exception) {
            return ActionResult.failed(exception.getMessage());
        }
        if (student == null) {
            return ActionResult.done(submission.getUsername() + " is no "
                    + "longer matched to the score sheet.");
        }
        return ActionResult.done(submission.getUsername() + " now fills the "
                + "score sheet row of " + student.getUsername() + ".");
    }

    // Says whether a folder holds at least one tester file.
    private boolean holdsTester(Path folder) {
        String ending = config.getString(ConfigKeys.TESTER_CLASS_SUFFIX)
                + JAVA_EXTENSION;
        try {
            for (Path file : FileUtils.listFiles(folder, JAVA_EXTENSION)) {
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
                + JAVA_EXTENSION;
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

    // Says whether the run has a submission of a username: one named so,
    // or one matched to that student on the score sheet by hand.
    private boolean hasSubmission(GradingRun gradingRun, String username) {
        for (Submission submission : gradingRun.getSubmissions()) {
            boolean matched = submission.getStudent() != null
                    && submission.getStudent().getUsername()
                    .equalsIgnoreCase(username);
            if (matched || submission.getUsername().equalsIgnoreCase(
                    username)) {
                return true;
            }
        }
        return false;
    }

    /**
     * Starts GradingService.startGrading in a GradingTask. When no usable
     * starter or model answers folder is chosen, the one in Settings is
     * passed, as its row on Set up says, so the run records the folder it
     * used.
     */
    @Override
    public void startGrading(ImportBatch batch, Section section,
            ProgressListener listener, RunCallback callback) {
        GradingTask task = new GradingTask(services.getGradingService(),
                batch.getSubmissionPaths(), section,
                createTestCaseSource(batch), listener,
                batch.getSinglePath(InputKind.ROSTER),
                findUsableStarter(batch), findUsableModelAnswers(batch));
        runInBackground(task, callback, Capability.IMPORT_AND_GRADE, null);
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
        startGradingAgain(gradingRun.getId());
        runInBackground(task, callback, Capability.LATE_BATCH,
                gradingRun.getId());
    }

    /**
     * Calls GradingService.cancel for the section, so the other sections
     * keep grading.
     */
    @Override
    public void cancelGrading(Section section) {
        services.getGradingService().cancel(section);
    }

    /**
     * Starts GradingService.resume in a GradingTask.
     */
    @Override
    public void resumeGrading(GradingRun gradingRun,
            ProgressListener listener, RunCallback callback) {
        GradingTask task = new GradingTask(services.getGradingService(),
                GradingTaskKind.RESUME, gradingRun, listener);
        startGradingAgain(gradingRun.getId());
        runInBackground(task, callback, Capability.RESUME_RUN,
                gradingRun.getId());
    }

    /**
     * Starts GradingService.retryFailed in a GradingTask.
     */
    @Override
    public void retryFailedSubmissions(GradingRun gradingRun,
            ProgressListener listener, RunCallback callback) {
        GradingTask task = new GradingTask(services.getGradingService(),
                GradingTaskKind.RETRY_FAILED, gradingRun, listener);
        startGradingAgain(gradingRun.getId());
        runInBackground(task, callback, Capability.RETRY_FAILED,
                gradingRun.getId());
    }

    /**
     * Calls GradingService.canRetry: a run started, resumed or added to
     * in this session, whose testers are loaded.
     */
    @Override
    public boolean canRetryFailed(GradingRun gradingRun) {
        return services.getGradingService().canRetry(gradingRun);
    }

    /**
     * Calls GradingService.regradeQuestion, which compiles and runs only
     * that question again and saves the run, then puts a mark set by hand
     * back with ScoreAdjustmentService.keepAfterRegrade. It runs a
     * tester, so call it off the window's thread, as RegradeJob does.
     * The activity log keeps the old and new marks.
     */
    @Override
    public ActionResult retryQuestion(GradingRun run, Submission submission,
            String questionId) {
        String refused = explainNoRegrade(run, submission);
        if (refused != null) {
            return ActionResult.failed(refused);
        }

        ScoreAdjustmentService adjustments =
                services.getScoreAdjustmentService();
        List<ScoreAdjustment> allHandMarks = adjustments.listNewest(run,
                submission);
        List<ScoreAdjustment> handMarks = findHandMark(allHandMarks,
                questionId);
        String markBefore = describeMark(submission, questionId);
        String totalBefore = describeTotal(submission);
        try {
            services.getGradingService().regradeQuestion(run, submission,
                    questionId, null);
        } catch (GradingException exception) {
            return ActionResult.failed(exception.getMessage());
        } finally {
            forgetSavedRuns();
        }
        rememberFoundNow(submission);
        List<ScoreAdjustment> kept = adjustments.keepAfterRegrade(run,
                submission, handMarks);

        String markAfter = describeMark(submission, questionId);
        String totalAfter = describeTotal(submission);
        String keptWords = describeKept(kept);
        String change = submission.getUsername() + " " + questionId + ": "
                + markBefore + " to " + markAfter + ", total " + totalBefore
                + " to " + totalAfter + "." + keptWords;
        recordActivity(QUESTION_GRADED_AGAIN,
                AuditEntry.withReference(change, run.getId()));
        return ActionResult.done("Graded " + questionId + " of "
                + submission.getUsername() + " again: " + markBefore
                + " to " + markAfter + ". Total " + totalAfter + ". Other "
                + "marks did not change." + keptWords);
    }

    /**
     * Calls GradingService.regradeSubmission, which grades the student
     * again from the upload and saves the run, then puts the marks set by
     * hand back with ScoreAdjustmentService.keepAfterRegrade. It runs
     * every tester, so call it off the window's thread, as RegradeJob
     * does. An upload that was refused stays refused. The activity log
     * keeps the old and new totals.
     */
    @Override
    public ActionResult retrySubmission(GradingRun run,
            Submission submission) {
        String refused = explainNoRegrade(run, submission);
        if (refused != null) {
            return ActionResult.failed(refused);
        }

        ScoreAdjustmentService adjustments =
                services.getScoreAdjustmentService();
        List<ScoreAdjustment> handMarks = adjustments.listNewest(run,
                submission);
        String totalBefore = describeTotal(submission);
        try {
            services.getGradingService().regradeSubmission(run, submission,
                    null);
        } catch (GradingException exception) {
            return ActionResult.failed(exception.getMessage());
        } finally {
            forgetSavedRuns();
        }
        rememberFoundNow(submission);
        List<ScoreAdjustment> kept = adjustments.keepAfterRegrade(run,
                submission, handMarks);

        String totalAfter = describeTotal(submission);
        String keptWords = describeKept(kept);
        String change = submission.getUsername() + ACTIVITY_DETAIL_SEPARATOR
                + totalBefore + " to " + totalAfter + "." + keptWords;
        recordActivity(STUDENT_GRADED_AGAIN,
                AuditEntry.withReference(change, run.getId()));
        return ActionResult.done("Graded " + submission.getUsername()
                + " again. Total " + totalBefore + " to " + totalAfter + "."
                + keptWords);
    }

    // Says why a student cannot be graded again, or returns null when
    // they can. An upload that was refused stays refused: grading the
    // same file again cannot help.
    private String explainNoRegrade(GradingRun run, Submission submission) {
        if (submission == null || run == null) {
            return "Choose a student of a graded run first.";
        }
        boolean refused = submission.getIngestFailure() != null
                || ResultState.isRejectedUpload(submission);
        if (refused) {
            return "The upload of " + submission.getUsername() + " was "
                    + "refused, so it stays refused and was not graded "
                    + "again. Choose a new copy of the zip on Set up.";
        }
        return null;
    }

    // Keeps only the mark set by hand for one question.
    private List<ScoreAdjustment> findHandMark(
            List<ScoreAdjustment> handMarks, String questionId) {
        List<ScoreAdjustment> found = new ArrayList<>();
        for (ScoreAdjustment change : handMarks) {
            if (change.getQuestionId().equals(questionId)) {
                found.add(change);
            }
        }
        return found;
    }

    // Says which marks set by hand were kept, such as " Kept the mark set
    // by hand for Q2b (4).", or "" when none was.
    private String describeKept(List<ScoreAdjustment> kept) {
        if (kept.isEmpty()) {
            return "";
        }
        List<String> marks = new ArrayList<>();
        for (ScoreAdjustment change : kept) {
            marks.add(change.getQuestionId() + " ("
                    + ScoreFormatter.format(change.getNewScore()) + ")");
        }
        String words = " Kept the mark set by hand for ";
        if (kept.size() > 1) {
            words = " Kept the marks set by hand for ";
        }
        return words + TextUtils.join(LIST_SEPARATOR, marks) + ".";
    }

    // The total of a student for a message, or "not graded".
    private String describeTotal(Submission submission) {
        if (!ResultState.hasTotal(submission)) {
            return NOT_GRADED;
        }
        return ScoreFormatter.format(submission.getTotalScore());
    }

    // The mark of one question of a student, or "not graded".
    private String describeMark(Submission submission, String questionId) {
        for (QuestionResult result : submission.getQuestionResults()) {
            Question question = result.getQuestion();
            if (question != null && question.getId().equals(questionId)) {
                return ScoreFormatter.format(result.getScore());
            }
        }
        return NOT_GRADED;
    }

    // Remembers the problems a student was given when graded again in
    // this session, so a failed undo of one of their fixes is explained
    // right.
    private void rememberFoundNow(Submission submission) {
        for (Anomaly anomaly : submission.getAnomalies()) {
            anomaliesFoundNow.add(anomaly.getId());
        }
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
            String flag = anomaly.getUsername() + ACTIVITY_DETAIL_SEPARATOR
                    + anomaly.getMessage();
            String action = decision.getAuditAction(anomaly.getType());
            services.getAuditService().record(action,
                    AuditEntry.withReference(flag, anomaly.getId()));
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
            String zeroCheck = submission.getUsername()
                    + ACTIVITY_DETAIL_SEPARATOR + ZERO_CHECK_WORDS;
            recordActivity(action, AuditEntry.withReference(zeroCheck,
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
     * checks the fix status changed. Any other repair is left alone. A
     * fix with no record in memory, such as one made before GradeZZY was
     * last opened, is undone with AnomalyService.undoEarlierFix, which
     * grades the student again to make the record first.
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

        AnomalyService anomalyService = services.getAnomalyService();
        GradingRun run = findSessionRunWith(anomaly);
        UndoResult undone = null;
        if (anomalyService.findFixRecord(anomaly) != null) {
            // A fix applied again in a saved run has a record, but its
            // run is not one of this session
            GradingRun target = null;
            if (run == null) {
                target = findAnyRunWith(anomaly);
                run = target;
            }
            undone = anomalyService.undoFix(target, anomaly);
        } else {
            run = findAnyRunWith(anomaly);
            undone = anomalyService.undoEarlierFix(run, anomaly);
            rememberFoundNow(run, anomaly.getUsername());
        }
        if (run != null) {
            rejectedInRun.put(anomaly.getId(), run.getId());
        }
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
        return undone.getMessage() + SENTENCE_GAP + putBack;
    }

    /**
     * Calls AnomalyService.canReapplyFix, or canReapplyEarlierFix for a
     * fix rejected in a run not graded in this session or before
     * GradeZZY was opened.
     */
    @Override
    public boolean canReapplyRepair(Anomaly anomaly) {
        AnomalyService anomalyService = services.getAnomalyService();
        if (anomalyService.canReapplyFix(anomaly)) {
            return true;
        }
        if (anomaly == null || anomaly.getFixStatus() != FixStatus.UNDONE) {
            return false;
        }
        return anomalyService.canReapplyEarlierFix(
                findRunToReapplyIn(anomaly), anomaly);
    }

    // Returns the run that holds an anomaly, or, for a card whose fix
    // was rejected in this session, the run it was rejected in; null
    // when there is none.
    private GradingRun findRunToReapplyIn(Anomaly anomaly) {
        GradingRun run = findAnyRunWith(anomaly);
        String runId = rejectedInRun.get(anomaly.getId());
        if (run != null || runId == null) {
            return run;
        }
        // A run of this session first, as findAnyRunWith does
        List<GradingRun> runs = new ArrayList<>(
                services.getGradingService().getSessionRuns());
        Collections.reverse(runs);
        runs.addAll(getSavedRuns());
        for (GradingRun candidate : runs) {
            if (candidate.getId().equals(runId)) {
                return candidate;
            }
        }
        return null;
    }

    /**
     * Calls AnomalyService.reapplyFix, which grades the student again
     * with the fix, and says what the marks became. A fix whose key is
     * not known in this session, or whose run is a saved one, is applied
     * again with AnomalyService.reapplyEarlierFix.
     */
    @Override
    public ActionResult reapplyRepair(RepairItem repair) {
        Anomaly anomaly = repair.getAnomaly();
        if (!canReapplyRepair(anomaly)) {
            return ActionResult.failed(explainReapplyFailure(anomaly));
        }

        AnomalyService anomalyService = services.getAnomalyService();
        GradingRun run = findSessionRunWith(anomaly);
        ReapplyResult reapplied = anomalyService.reapplyFix(anomaly);
        if (reapplied == null) {
            run = findRunToReapplyIn(anomaly);
            reapplied = anomalyService.reapplyEarlierFix(run, anomaly);
            rememberFoundNow(run, anomaly.getUsername());
        }
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
        return start + "GradeZZY could not find the student's run or the "
                + "rejected fix in it, or could not grade them again.";
    }

    // Grading the student again cleared their Similar code flags, so the
    // run is checked again. It runs here until GradingService runs it
    // after grading. Returns a sentence when the check failed, else "".
    private String checkAfterUndo(GradingRun run) {
        // Identical copies are checked even with the similarity check off
        if (run == null) {
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

    // Returns the run that holds an anomaly: a run of this session, else
    // a saved run; null when none does.
    private GradingRun findAnyRunWith(Anomaly anomaly) {
        GradingRun sessionRun = findSessionRunWith(anomaly);
        if (sessionRun != null) {
            return sessionRun;
        }

        List<GradingRun> runs = new ArrayList<>(runsBySection.values());
        runs.addAll(getSavedRuns());
        for (GradingRun run : runs) {
            if (holdsAnomaly(run, anomaly.getId())) {
                return run;
            }
        }
        return null;
    }

    // Remembers the problems of one student of a run, once the student
    // was graded again, as rememberFoundNow does.
    private void rememberFoundNow(GradingRun run, String username) {
        if (run == null) {
            return;
        }
        Submission submission = run.findLoaded(username);
        if (submission != null) {
            rememberFoundNow(submission);
        }
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
     * Calls AnomalyService.reopenFix, so a kept fix waits for review again.
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
        String[] parts = relativePath.split(FOLDER_SEPARATOR);
        Path match = matchPath(folder, parts, ignoreCase);
        if (match != null || depth == 0) {
            return match;
        }
        int childDepth = depth - 1;
        for (Path child : listChildren(folder)) {
            if (Files.isDirectory(child, LinkOption.NOFOLLOW_LINKS)) {
                Path found = findSourceFile(child, relativePath, ignoreCase,
                        childDepth);
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
            return ActionResult.done(adjustmentSaved("Saved.") + SENTENCE_GAP
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
            return ActionResult.done(adjustmentSaved("Undone.")
                    + SENTENCE_GAP + adjustment.getQuestionId() + " for "
                    + adjustment.getUsername() + " is "
                    + ScoreFormatter.format(adjustment.getPreviousScore())
                    + " again.");
        } catch (IllegalArgumentException exception) {
            return ActionResult.failed(exception.getMessage());
        }
    }

    /**
     * Says whether the marks changed by hand were saved, from
     * ScoreAdjustmentService.
     */
    @Override
    public String describeMarkSaving() {
        String said = adjustmentSaved("");
        if (said.isEmpty()) {
            return "";
        }
        return SENTENCE_GAP + said;
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

        String exportFolder = getExportFolder(gradingRun.getSection());
        Path outputFolder = Paths.get(exportFolder);
        exportedRun = gradingRun;
        try {
            Path written = services.getExportService().export(gradingRun,
                    scope, formatName, outputFolder);
            if (written == null) {
                return ActionResult.failed(waitingMessage(
                        Capability.EXPORT_RESULTS));
            }

            String note = "";
            int chosenCount = scope.size();
            int runCount = gradingRun.getSubmissions().size();
            if (chosenCount < runCount) {
                note = " It holds " + chosenCount + " of " + runCount
                        + " students.";
            }
            return ActionResult.saved("Exported "
                    + describeFormat(formatName) + " to " + written + "."
                    + note, written);
        } catch (ExportException exception) {
            return ActionResult.failed("The export did not finish: "
                    + exception.getMessage());
        } finally {
            exportedRun = null;
        }
    }

    // Names an export format as the exports list does, such as "CSV
    // score sheet" for "csv", rather than as the screen asked for it.
    private String describeFormat(String formatName) {
        Exporter exporter = services.getExportService().findExporter(
                formatName);
        if (exporter == null) {
            return formatName.toUpperCase(Locale.ROOT);
        }
        return exporter.getFormatName();
    }

    // Returns the Remarks of the students in a part of a run, worked out
    // from the whole run, so they are the same as in an export of all.
    private Map<String, String> remarksOf(GradingRun part) {
        Map<String, String> remarks = new CheatingRemarks(this).remarksFor(
                findWholeRun(part));
        Map<String, String> partRemarks = new HashMap<>();
        if (part == null) {
            return partRemarks;
        }
        for (Submission submission : part.getSubmissions()) {
            String key = submission.getUsername().toLowerCase(Locale.ROOT);
            if (remarks.containsKey(key)) {
                partRemarks.put(key, remarks.get(key));
            }
        }
        return partRemarks;
    }

    // Returns the whole run a score sheet export is of: the run being
    // exported when the part is of it, else the part itself.
    private GradingRun findWholeRun(GradingRun part) {
        GradingRun whole = exportedRun;
        boolean sameRun = whole != null && part != null
                && whole.getId() != null
                && whole.getId().equals(part.getId());
        if (sameRun) {
            return whole;
        }
        return part;
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
     * Calls HistoryService.listBackups.
     */
    @Override
    public List<BackupInfo> listBackups() {
        return services.getHistoryService().listBackups();
    }

    /**
     * Calls HistoryService.getBackupKeepCount.
     */
    @Override
    public int getBackupKeepCount() {
        return services.getHistoryService().getBackupKeepCount();
    }

    /**
     * Returns Not connected yet: no service lists the run in each backup.
     */
    @Override
    public ActionResult restore(RunRecord record) {
        return ActionResult.notConnected(Capability.RESTORE_PREVIEW);
    }

    /**
     * Calls HistoryService.restore, then forgets every run and decision
     * so they are read again from the restored history. The result holds
     * the backup made just before the restore, so it can be undone.
     */
    @Override
    public ActionResult restoreBackup(Path backupFile) {
        if (!config.getBoolean(ConfigKeys.DATABASE_ENABLED)) {
            return ActionResult.failed("Saving results is switched off in "
                    + "Settings, so there is nothing to restore into. "
                    + "Switch it on, then restore again.");
        }

        // A file that is not a GradeZZY backup is refused before anything
        // is changed, with a plain reason
        String problem = services.getHistoryService().checkBackup(
                backupFile);
        if (!problem.isEmpty()) {
            return ActionResult.failed(problem);
        }

        Path safetyBackup = null;
        try {
            safetyBackup = services.getHistoryService().restore(backupFile);
        } catch (UnsupportedOperationException exception) {
            return ActionResult.notConnected(Capability.RESTORE);
        } catch (RuntimeException exception) {
            // A restore that failed half way may still have changed them
            forgetSavedRuns();
            services.getAnswerGroupService().forgetAll();
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
        services.getAnswerGroupService().forgetAll();
        forgetSavedRuns();
        if (Capability.RESTORE.getStatus() != CapabilityStatus.WORKS_NOW) {
            return ActionResult.notConnected(Capability.RESTORE);
        }
        String message = "Restored the saved results from "
                + backupFile.getFileName() + ".";
        if (safetyBackup == null) {
            return ActionResult.done(message);
        }
        return ActionResult.saved(message + " The results saved before "
                + "were backed up as " + safetyBackup.getFileName() + ".",
                safetyBackup);
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
        String saved = "Saved " + changes.size() + " setting"
                + plural(changes.size()) + " to config.properties.";
        // The database file is opened once, when GradeZZY starts
        if (keys.contains(ConfigKeys.DATABASE_FILE)) {
            saved = saved + " Restart GradeZZY to use the new database "
                    + "file.";
        }
        return ActionResult.done(saved);
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
        String savedName = draft.getName().trim();
        recordAudit("Assessment saved", savedName);
        return ActionResult.done("Saved " + savedName + ".");
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
     * Calls AssessmentLibrary.delete, which also clears the files chosen
     * for it on Set up, and keeps what it took for restoreAssessment. The
     * window records it in the activity log, with its Undo.
     */
    @Override
    public ActionResult deleteAssessment(String name) {
        DeletedAssessment deleted = null;
        try {
            deleted = library().delete(name);
        } catch (IOException exception) {
            return ActionResult.failed(name + " could not be deleted: "
                    + exception.getMessage());
        } catch (IllegalArgumentException exception) {
            return ActionResult.failed(exception.getMessage());
        }
        deletedAssessments.put(name.toLowerCase(Locale.ROOT), deleted);
        String cleared = "";
        if (deleted.hadChosenFiles()) {
            cleared = " The files chosen for it on Set up were cleared.";
        }
        return ActionResult.done("Deleted " + name + "." + cleared
                + " Results graded with it are kept.");
    }

    /**
     * Calls AssessmentLibrary.restore with what deleteAssessment kept.
     */
    @Override
    public ActionResult restoreAssessment(String name) {
        String key = name.toLowerCase(Locale.ROOT);
        DeletedAssessment deleted = deletedAssessments.get(key);
        if (deleted == null) {
            return ActionResult.failed(name + " was not deleted since "
                    + "GradeZZY opened, so it cannot be put back.");
        }
        try {
            library().restore(deleted);
        } catch (IOException exception) {
            return ActionResult.failed(name + " could not be put back: "
                    + exception.getMessage());
        } catch (IllegalArgumentException exception) {
            return ActionResult.failed(exception.getMessage());
        }
        deletedAssessments.remove(key);
        return ActionResult.done("Put back " + name + ".");
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
    // the Settings page is saved into the assessment in use too. Each
    // assessment keeps its own score sheets, chosen on Set up, Home or
    // Settings.
    private boolean isAssessmentSetting(String key) {
        return key.startsWith(ConfigKeys.QUESTION_PREFIX)
                || key.equals(ConfigKeys.ASSESSMENT_NAME)
                || key.equals(ConfigKeys.TESTERS_DIR)
                || key.equals(ConfigKeys.STARTER_DIR)
                || key.equals(ConfigKeys.MODEL_ANSWERS_DIR)
                || key.equals(ConfigKeys.PROVIDED_CLASS_FILES)
                || key.equals(ConfigKeys.TESTER_DATA_FILES)
                || key.equals(ConfigKeys.PLACEHOLDER_FOLDER_NAMES)
                || key.equals(ConfigKeys.SCORE_SHEET_BY_SECTION)
                || key.equals(ConfigKeys.SCORE_SHEET_FILE);
    }

    /**
     * Calls AuditService.record.
     */
    @Override
    public void recordActivity(String action, String detail) {
        services.getAuditService().record(action, detail);
    }

    /**
     * Returns the service that saves answer group names and checks with
     * the run.
     */
    @Override
    public AnswerGroupService getAnswerGroupService() {
        return services.getAnswerGroupService();
    }

    /**
     * Calls PlagiarismService.listPairs, which reads the pairs back from
     * the run's Similar code flags, so nothing is checked again and a
     * saved run shows the pairs it was saved with.
     */
    @Override
    public List<SimilarityPair> listSimilarPairs(GradingRun gradingRun) {
        return services.getPlagiarismService().listPairs(gradingRun);
    }

    /**
     * Calls PlagiarismService.readPair, the one place a flag is read.
     */
    @Override
    public SimilarityPair findSimilarPair(GradingRun gradingRun,
            Anomaly anomaly) {
        return services.getPlagiarismService().readPair(gradingRun, anomaly);
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
        boolean anyGone = question == null || first == null || second == null;
        if (anyGone) {
            return null;
        }

        String file = question.getFolderName() + FOLDER_SEPARATOR
                + question.getFileName();
        String firstCode = readSourceFile(first, file);
        String secondCode = readSourceFile(second, file);
        if (firstCode.isEmpty() && secondCode.isEmpty()) {
            return null;
        }

        String starterCode = readStarterCode(gradingRun, question);
        List<String> leftOut = listLeftOut(gradingRun, question,
                starterCode);
        return new SimilarityDetail(pair, file, file, firstCode,
                secondCode, starterCode, leftOut);
    }

    // Returns where a question's starter file is, whether or not it
    // exists.
    private Path findStarterFile(GradingRun gradingRun, Question question) {
        Path starter = gradingRun.getStarterDirectory();
        if (starter == null) {
            starter = configuredPath(InputKind.STARTER);
        }
        return starter.resolve(question.getFolderName())
                .resolve(question.getFileName());
    }

    // Reads a question's starter file, or returns "" when it is missing
    // or cannot be read.
    private String readStarterCode(GradingRun gradingRun,
            Question question) {
        Path starterFile = findStarterFile(gradingRun, question);
        if (!Files.isRegularFile(starterFile)) {
            return "";
        }

        try {
            return FileUtils.readText(starterFile);
        } catch (IOException exception) {
            return "";
        }
    }

    // Lists what the check leaves out of a question: the starter file's
    // code, and every other file in the question folder. A starter file
    // that could not be read is named, so the instructor knows why no
    // line is greyed.
    private List<String> listLeftOut(GradingRun gradingRun,
            Question question, String starterCode) {
        List<String> leftOut = new ArrayList<>();
        Path starterFile = findStarterFile(gradingRun, question);
        if (starterCode.isEmpty()) {
            leftOut.add("Could not read a starter file at " + starterFile
                    + ", so starter lines are not greyed");
        } else {
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

    /**
     * Checks that the folder can be read, then that it holds the starter
     * file of each of the given questions.
     */
    @Override
    public ImportItem inspectStarter(Path folder, List<Question> questions) {
        if (!InputValidator.isReadableDirectory(folder)) {
            return unreadable(InputKind.STARTER, folder);
        }
        return checkStarter(folder, questions);
    }

    // Checks that a starter folder holds each question's starter file,
    // which the untouched and empty answer checks compare answers with.
    // With no questions there is nothing to look for yet.
    private ImportItem checkStarter(Path folder, List<Question> questions) {
        if (questions.isEmpty()) {
            return new ImportItem(InputKind.STARTER, folder, "",
                    ImportState.ACCEPTED, "Folder found. Its starter files "
                    + "are checked once there are questions.");
        }

        List<String> missingFiles = new ArrayList<>();
        List<String> missingIds = new ArrayList<>();
        for (Question question : questions) {
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
        } else if (missingFiles.size() == questions.size()) {
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

    // Checks for which questions a model answers folder holds an answer,
    // as the assessment editor does. The folder is optional, and one
    // with some answers missing still serves the questions it has.
    private ImportItem checkModelAnswers(Path folder,
            List<Question> questions) {
        if (questions.isEmpty()) {
            return new ImportItem(InputKind.MODEL_ANSWERS, folder, "",
                    ImportState.ACCEPTED, "Folder found. Its answers are "
                    + "checked once there are questions.");
        }

        int found = 0;
        for (Question question : questions) {
            if (ModelAnswerCopy.findAnswerFile(folder, question) != null) {
                found++;
            }
        }
        if (found == questions.size()) {
            return new ImportItem(InputKind.MODEL_ANSWERS, folder, "",
                    ImportState.ACCEPTED, "Has an answer for every "
                    + "question.");
        }
        int questionCount = questions.size();
        return new ImportItem(InputKind.MODEL_ANSWERS, folder, "",
                ImportState.WARNING, "Has an answer for " + found + " of "
                + questionCount + " question" + plural(questionCount)
                + ", so the others are left out of the model answer "
                + "checks.");
    }

    // Checks that a tester folder holds testers, with the scan that fills
    // in an assessment's questions, so the folder's status and the
    // questions found always agree. A tester with no marks to read is a
    // warning, because its question would be worth nothing.
    private ImportItem inspectTesters(Path folder) {
        String testerSuffix = config.getString(
                ConfigKeys.TESTER_CLASS_SUFFIX);
        List<Question> found = AssessmentFolderScanner.findQuestions(folder,
                testerSuffix, null);
        if (found.isEmpty()) {
            return new ImportItem(InputKind.TESTERS, folder, "",
                    ImportState.INVALID, "No tester files, such as "
                    + firstTesterName() + ", are in this folder. Choose "
                    + "the folder that holds the testers.");
        }

        List<String> ids = new ArrayList<>();
        List<String> unmarked = new ArrayList<>();
        for (Question question : found) {
            ids.add(question.getId());
            boolean noMarks = question.getMaxScore() <= TesterMarks.UNKNOWN;
            if (noMarks) {
                unmarked.add(question.getId());
            }
        }
        int testerCount = found.size();
        String holds = "Holds " + testerCount + " tester"
                + plural(testerCount) + ": " + joinWords(ids) + ".";
        if (unmarked.isEmpty()) {
            return new ImportItem(InputKind.TESTERS, folder, "",
                    ImportState.ACCEPTED, holds);
        }
        return new ImportItem(InputKind.TESTERS, folder, "",
                ImportState.WARNING, holds + " No marks could be read from "
                + "the tester of " + joinWords(unmarked) + ", so type "
                + "them in.");
    }

    // Says what grading does instead of using a starter folder that
    // cannot be used: it uses the one in Settings, when that is another
    // folder that can be used.
    private String describeStarterFallback(Path folder) {
        Path configured = getConfiguredPath(InputKind.STARTER);
        boolean otherUsable = false;
        if (!isSameFolder(folder, configured)) {
            ImportItem settingsItem = inspect(InputKind.STARTER, configured);
            otherUsable = settingsItem.getState().isUsable();
        }
        if (otherUsable) {
            return "so the starter folder in Settings is used instead.";
        }
        return "so untouched and empty answers cannot be flagged.";
    }

    // Says what grading does instead of using a model answers folder
    // that cannot be used: it uses the one in Settings, when that is
    // another folder that can be used, else it skips the model answer
    // checks.
    private String describeModelAnswersFallback(Path folder) {
        Path configured = configuredPath(InputKind.MODEL_ANSWERS);
        boolean otherUsable = false;
        if (configured != null && !isSameFolder(folder, configured)) {
            ImportItem settingsItem = inspect(InputKind.MODEL_ANSWERS,
                    configured);
            otherUsable = settingsItem.getState().isUsable();
        }
        if (otherUsable) {
            return "so the model answers folder in Settings is used "
                    + "instead.";
        }
        return "so the tester strength check (mutation testing) and the "
                + "random inputs check will be skipped.";
    }

    // Says whether two paths name the same folder.
    private boolean isSameFolder(Path first, Path second) {
        return first.toAbsolutePath().normalize().equals(
                second.toAbsolutePath().normalize());
    }

    // Returns the chosen starter folder, else the one in Settings, so the
    // run records the folder it was graded with. Returns null when
    // neither can be used.
    private Path findUsableStarter(ImportBatch batch) {
        List<ImportItem> starters = batch.getItems(InputKind.STARTER);
        if (!starters.isEmpty() && starters.get(0).getState().isUsable()) {
            return starters.get(0).getPath();
        }

        Path configured = configuredPath(InputKind.STARTER);
        ImportItem settingsItem = inspect(InputKind.STARTER, configured);
        if (settingsItem.getState().isUsable()) {
            return configured;
        }
        return null;
    }

    // Returns the chosen model answers folder, else the one in Settings,
    // so the run records the folder it was graded with. Returns null
    // when neither can be used.
    private Path findUsableModelAnswers(ImportBatch batch) {
        List<ImportItem> answers = batch.getItems(InputKind.MODEL_ANSWERS);
        if (!answers.isEmpty() && answers.get(0).getState().isUsable()) {
            return answers.get(0).getPath();
        }

        Path configured = configuredPath(InputKind.MODEL_ANSWERS);
        if (configured == null) {
            return null;
        }
        ImportItem settingsItem = inspect(InputKind.MODEL_ANSWERS,
                configured);
        if (settingsItem.getState().isUsable()) {
            return configured;
        }
        return null;
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
                    + describeStarterFallback(path) + SENTENCE_GAP + message;
        } else if (kind == InputKind.MODEL_ANSWERS) {
            message = "GradeZZY could not read this, "
                    + describeModelAnswersFallback(path) + SENTENCE_GAP
                    + message;
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

        String outputDir = config.getString(ConfigKeys.OUTPUT_DIR);
        Path outputFolder = Paths.get(outputDir, GENERATED_TESTERS_FOLDER);
        TypedTestCaseSource source = new TypedTestCaseSource(
                new TesterGenerator(config), outputFolder);
        for (TestCase testCase : batch.getTypedTestCases()) {
            source.addTestCase(testCase);
        }
        return source;
    }

    // Runs a grading task on a daemon thread and reports how it ended.
    // gradedAgainId names the run it grades again, or is null for a new
    // run.
    private void runInBackground(GradingTask task, RunCallback callback,
            Capability capability, String gradedAgainId) {
        task.setOnSucceeded(event -> finishTask(task, callback, capability,
                gradedAgainId));
        task.setOnFailed(event -> failTask(task, callback, gradedAgainId));
        // Identical copies are checked even with the similarity check off
        task.checkSimilarityAfter(services.getPlagiarismService());

        Thread worker = new Thread(task, GRADING_THREAD_NAME);
        worker.setDaemon(true);
        worker.start();
    }

    // Stores and reports the run a finished task returned.
    private void finishTask(GradingTask task, RunCallback callback,
            Capability capability, String gradedAgainId) {
        GradingRun run = task.getValue();
        endTask(gradedAgainId);
        if (run == null) {
            callback.onFailed(waitingMessage(capability));
            return;
        }
        rememberAnomalies(run, capability);
        if (task.getSimilarityProblem() != null) {
            recordActivity("Similar code not checked",
                    AuditEntry.withReference(task.getSimilarityProblem(),
                            run.getId()));
        }
        if (run.getSection() != null) {
            runsBySection.put(run.getSection().getLabel(), run);
        }
        callback.onFinished(run);
    }

    // Reports a task that stopped with an exception.
    private void failTask(GradingTask task, RunCallback callback,
            String gradedAgainId) {
        endTask(gradedAgainId);
        callback.onFailed(describeFailure(task.getException()));
    }

    // Clears what a task held while it ran. A task may have saved runs,
    // so the saved runs are read again when next needed.
    private void endTask(String gradedAgainId) {
        if (gradedAgainId != null) {
            endGradingAgain(gradedAgainId);
        }
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
                readBackDecisions(submission, savedDecisions);
            }
        }
    }

    // Reads back the saved decisions on one student's anomalies, keeping
    // any decision already made in this session.
    private void readBackDecisions(Submission submission,
            Map<String, String> savedDecisions) {
        for (Anomaly anomaly : submission.getAnomalies()) {
            String savedName = savedDecisions.get(anomaly.getId());
            ReviewDecision decision = decisionNamed(savedName);
            if (decision != null) {
                decisions.putIfAbsent(decisionKey(anomaly), decision);
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
            Section section = saved.getSection();
            if (section != null && sectionLabel.equals(section.getLabel())) {
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
        boolean sameRunNow = recent != null && !isGradedAgainNow(recent)
                && saved.getId().equals(recent.getId());
        if (!sameRunNow) {
            return saved;
        }
        return recent;
    }

    // Says whether getRun shows this run for its section now.
    private boolean isShownNow(GradingRun run) {
        GradingRun shown = getRun(run.getSection());
        boolean sameRun = shown != null && shown.getId() != null
                && shown.getId().equals(run.getId());
        return sameRun;
    }

    // Records that a run is being graded again in the background.
    private synchronized void startGradingAgain(String runId) {
        runsGradedAgain.add(runId);
    }

    // Records that a run is no longer graded again.
    private synchronized void endGradingAgain(String runId) {
        runsGradedAgain.remove(runId);
    }

    // Says whether the grading threads are changing this run now.
    private synchronized boolean isGradedAgainNow(GradingRun run) {
        return run.getId() != null && runsGradedAgain.contains(run.getId());
    }

    // Keys a review decision by student and anomaly id. Anomaly ids do
    // not repeat (most end in a random UUID, as PIPELINE-<UUID> does), so
    // the username only makes the key easier to read.
    private String decisionKey(Anomaly anomaly) {
        return anomaly.getUsername() + DECISION_KEY_SEPARATOR
                + anomaly.getId();
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

    // Explains why a repair waiting for review could not be undone. A
    // fix with no record in memory is undone by grading the student
    // again, so that grading is what failed.
    private String explainUndoFailure(Anomaly anomaly) {
        String start = "The fix for " + anomaly.getUsername()
                + " could not be undone, so it still shows as applied. ";
        if (services.getGradingService().isGrading()) {
            return start + "Wait until grading has finished.";
        }
        if (!anomaliesFoundNow.contains(anomaly.getId())) {
            return start + "To undo it, GradeZZY grades the student again, "
                    + "and that did not work. Check that the run's tester "
                    + "folder and the student's upload are still there.";
        }
        return start + "The files the fix changed may have been moved or "
                + "deleted. Grade the student again, then undo the fix.";
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
        if (words.isEmpty()) {
            return "";
        }

        int last = words.size() - 1;
        if (last == 0) {
            return words.get(0);
        }

        List<String> allButLast = new ArrayList<>();
        for (int index = 0; index < last; index++) {
            allButLast.add(words.get(index));
        }
        return TextUtils.join(LIST_SEPARATOR, allButLast) + " and "
                + words.get(last);
    }
}
