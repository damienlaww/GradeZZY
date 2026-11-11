/*
 * File: ServiceTest.java
 *
 * Purpose: Unit checks for the service package: SettingsService
 * validation and saving (comments, CRLF, Windows paths), AnomalyService
 * approve, reopen and undo in one session and after a restart (a new
 * FixManager and a run read back from SQLite, through undoEarlierFix),
 * and AssessmentLibrary save, list, validate, use and rename.
 *
 * Where it fits: Run by hand after compiling, with lib/* on the
 * classpath, as tests/README.md says. Grades with a pipeline that only
 * unpacks, checks and fixes, so no student code runs. Works on copies of
 * the fixed test settings in a temporary folder.
 */
import java.io.ByteArrayOutputStream;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;
import java.util.zip.ZipEntry;
import java.util.zip.ZipOutputStream;

import gradezzy.config.AppConfig;
import gradezzy.config.ConfigKeys;
import gradezzy.fix.CaseNameFixer;
import gradezzy.fix.EncodingFixer;
import gradezzy.fix.FixManager;
import gradezzy.fix.FolderHierarchyFixer;
import gradezzy.fix.FolderNameFixer;
import gradezzy.fix.HeaderNameFixer;
import gradezzy.fix.MisplacedFileFixer;
import gradezzy.model.Anomaly;
import gradezzy.model.AnomalyType;
import gradezzy.model.FixStatus;
import gradezzy.model.GradingRun;
import gradezzy.model.Question;
import gradezzy.model.SavedAssessment;
import gradezzy.model.Section;
import gradezzy.model.Submission;
import gradezzy.persistence.SqliteResultRepository;
import gradezzy.service.AnomalyService;
import gradezzy.service.AssessmentFactory;
import gradezzy.service.AssessmentLibrary;
import gradezzy.service.GradingService;
import gradezzy.service.QuietProgressListener;
import gradezzy.service.SettingsService;
import gradezzy.service.UndoResult;
import gradezzy.util.FileUtils;

/**
 * Runs the service checks and prints how many passed.
 */
public class ServiceTest {

    // The section every student is in
    private static final Section SECTION = new Section("CS102", "G1");

    // The student graded in the restart checks
    private static final String AMY = "amy";

    // The top folder name the starter code ships with
    private static final String TEMPLATE_FOLDER = "RenameToYourUsername";

    // The first question's folder and id
    private static final String Q1_FOLDER = "Q1";
    private static final String Q1A = "Q1a";

    // The folder and class of each answer in the uploaded zip
    private static final String[][] ANSWERS = {{Q1_FOLDER, Q1A},
        {Q1_FOLDER, "Q1b"}, {"Q2", "Q2a"}, {"Q2", "Q2b"}, {"Q3", "Q3"}};

    // How many strings zipBytes takes for each entry: a name and a text
    private static final int ENTRY_PARTS = 2;

    // Where the folder and the class name sit in a row of ANSWERS
    private static final int ANSWER_FOLDER = 0;
    private static final int ANSWER_CLASS = 1;

    // The ending of a Java source file name
    private static final String JAVA_SUFFIX = ".java";

    // What separates a key from its value in a settings file
    private static final String KEY_VALUE_SEPARATOR = "=";

    // What a line break is shown as in a check message
    private static final String SHOWN_LINE_BREAK = "|";

    // The folder separator, also the root folder
    private static final String SLASH = "/";

    // Line endings
    private static final String CRLF = "\r\n";
    private static final String LF = "\n";

    // One letter used as filler text and as a value that is no number
    private static final String FILLER = "x";

    // The zip entry limit saved and read back
    private static final int SAVED_ENTRIES = 321;

    // One letter longer than an assessment name may be
    private static final int TOO_LONG_NAME = 61;

    // The assessment the library checks save, and the name it is
    // renamed to
    private static final String LAB_NAME = "Lab Test 9";
    private static final String RENAMED_LAB = "Lab Test 10";

    // The file the library saves the assessment in
    private static final String LAB_FILE =
            "assessments/lab-test-9.properties";

    // The number of checks that passed and failed
    private static int passed = 0;
    private static int failed = 0;

    /**
     * Runs every check.
     *
     * @param args not used
     * @throws Exception if a temporary file cannot be written
     */
    public static void main(String[] args) throws Exception {
        Path root = Files.createTempDirectory("gradezzy-service-tests-")
                .toRealPath();
        try {
            checkSettings(root);
            checkAnomalyRestart(root);
            checkLibrary(root);
        } finally {
            FileUtils.deleteDirectory(root);
        }
        System.out.println("ServiceTest: " + passed + " passed, " + failed
                + " failed");
        if (failed > 0) {
            System.exit(1);
        }
    }

    // Copies the fixed test settings into a file and loads the copy.
    private static AppConfig configCopy(Path root, String name)
            throws Exception {
        Path file = root.resolve(name);
        TestConfig.copyTo(file);
        return new AppConfig(file.toString());
    }

    // Checks SettingsService: which values it refuses, and that saving
    // keeps CRLF, comments, Windows paths and continued entries.
    private static void checkSettings(Path root) throws Exception {
        Path file = root.resolve("crlf.properties");
        String original = new String(Files.readAllBytes(TestConfig.FILE),
                StandardCharsets.UTF_8)
                .replace(CRLF, LF).replace(LF, CRLF);
        Files.writeString(file, original);
        AppConfig config = new AppConfig(file.toString());
        SettingsService settings = new SettingsService(config);
        checkRefusedValues(settings, config);
        checkSaving(root, file, settings);
        checkContinuedEntry(root);
    }

    // Checks which values SettingsService refuses and that the fixed
    // settings have no problem.
    private static void checkRefusedValues(SettingsService settings,
            AppConfig config) {
        check(settings.validate("nope.key", "1").contains("no setting"),
                "unknown key refused");
        check(!settings.validate(ConfigKeys.MAX_ZIP_ENTRIES, "0").isEmpty(),
                "0 entries refused");
        check(!settings.validate(ConfigKeys.MAX_ZIP_ENTRIES, "-5").isEmpty(),
                "negative refused");
        check(!settings.validate(ConfigKeys.MAX_ZIP_ENTRIES, "1.5").isEmpty(),
                "fraction refused");
        check(settings.validate(ConfigKeys.MAX_ZIP_ENTRIES, " 10 ").isEmpty(),
                "spaces around a number accepted");
        check(!settings.validate(ConfigKeys.PLAGIARISM_THRESHOLD, "NaN")
                .isEmpty(), "NaN threshold refused");
        check(!settings.validate(ConfigKeys.PLAGIARISM_THRESHOLD, "1.5")
                .isEmpty(), "1.5 threshold refused");
        check(settings.validate(ConfigKeys.PLAGIARISM_THRESHOLD, "0.75")
                .isEmpty(), "0.75 accepted");
        check(!settings.validate(ConfigKeys.PLAGIARISM_ENABLED, "yes")
                .isEmpty(), "yes refused as boolean");
        check(settings.validate(ConfigKeys.PLAGIARISM_ENABLED, "TRUE")
                .isEmpty(), "TRUE accepted");
        check(!settings.validate(ConfigKeys.ZIP_NAME_PREFIX_PATTERN, "([")
                .isEmpty(), "bad regex refused");
        check(!settings.validate(ConfigKeys.ASSESSMENT_NAME, "a\nb").isEmpty(),
                "newline refused");
        check(!settings.validate(ConfigKeys.QUESTION_IDS, "Q1a,Q9")
                .isEmpty(), "question without folder/file refused");
        check(!settings.validate(ConfigKeys.QUESTION_IDS, " , ").isEmpty(),
                "empty question list refused");
        check(!settings.validate(ConfigKeys.WORKING_DIR, ".").isEmpty(),
                "working dir = program folder refused");
        check(!settings.validate(ConfigKeys.WORKING_DIR, SLASH).isEmpty(),
                "working dir = / refused");
        check(!settings.validate(ConfigKeys.WORKING_DIR,
                config.getString(ConfigKeys.SUBMISSIONS_DIR)).isEmpty(),
                "working dir = submissions dir refused");
        check(!settings.validate(ConfigKeys.MODULE_SECTION_CODES, "CS102=G1")
                .isEmpty(), "bad module sections refused");
        check(!settings.validate(ConfigKeys.SCORE_SHEET_BY_SECTION,
                "CS102/G1/x=a.csv").isEmpty(), "bad by-section refused");
        check(!settings.validate(ConfigKeys.CSV_LINE_ENDING, "CR").isEmpty(),
                "CR line ending refused");
        check(settings.findProblems().isEmpty(), "committed config has no "
                + "problems: " + settings.findProblems());
    }

    // Checks that a bad value is refused, and that saving keeps CRLF,
    // comments, Windows paths, unicode and each key once.
    private static void checkSaving(Path root, Path file,
            SettingsService settings) throws Exception {
        boolean thrown = false;
        try {
            settings.updateSetting(ConfigKeys.MAX_ZIP_ENTRIES, FILLER);
        } catch (IllegalArgumentException expected) {
            thrown = true;
        }
        check(thrown, "updateSetting refuses a bad value");
        String winPath = "C:\\Users\\ann\\Lab Tests\\sheet #1.csv";
        Path sheet = root.resolve("sheet.csv");
        Files.writeString(sheet, FILLER);
        settings.updateSetting(ConfigKeys.ASSESSMENT_NAME, "Lab \u00e9 3");
        settings.updateSetting(ConfigKeys.MAX_ZIP_ENTRIES,
                String.valueOf(SAVED_ENTRIES));
        settings.updateSetting(ConfigKeys.SCORE_SHEET_BY_SECTION,
                "CS102/G1=" + winPath);
        settings.save();
        String saved = Files.readString(file);
        check(saved.contains("ingest.max.zip.entries=" + SAVED_ENTRIES
                + CRLF),
                "changed line written with CRLF");
        check(!saved.replace(CRLF, "").contains(LF),
                "every line keeps CRLF");
        check(saved.contains("# How many"), "comments kept");
        AppConfig reread = new AppConfig(file.toString());
        check(reread.getString(ConfigKeys.SCORE_SHEET_BY_SECTION).equals(
                "CS102/G1=" + winPath), "Windows path round trips: "
                + reread.getString(ConfigKeys.SCORE_SHEET_BY_SECTION));
        check(reread.getString(ConfigKeys.ASSESSMENT_NAME).equals(
                "Lab \u00e9 3"), "unicode value round trips");
        check(reread.getInt(ConfigKeys.MAX_ZIP_ENTRIES) == SAVED_ENTRIES,
                "int saved");
        check(!Files.exists(root.resolve("crlf.properties.tmp")),
                "no temp file left");
        int countKeys = 0;
        for (String line : saved.split(CRLF)) {
            if (line.startsWith(ConfigKeys.MAX_ZIP_ENTRIES
                    + KEY_VALUE_SEPARATOR)) {
                countKeys++;
            }
        }
        check(countKeys == 1, "key written once");
        // A value with a leading '#' or '!' or ':'
        settings.updateSetting(ConfigKeys.CSV_ID_PREFIX, "#");
        settings.save();
        check(new AppConfig(file.toString()).getString(
                ConfigKeys.CSV_ID_PREFIX).equals("#"), "# value kept");
    }

    // Checks that an entry continued on the next line of the file is
    // replaced as one entry.
    private static void checkContinuedEntry(Path root) throws Exception {
        Path cont = root.resolve("cont.properties");
        Files.writeString(cont, "a.b=1\n" + ConfigKeys.ASSESSMENT_NAME
                + "=Lab \\\n    Test\nz=2\n");
        AppConfig contConfig = new AppConfig(cont.toString());
        SettingsService contSettings = new SettingsService(contConfig);
        contSettings.updateSetting(ConfigKeys.ASSESSMENT_NAME, "New");
        contSettings.save();
        String contText = Files.readString(cont);
        check(contText.equals("a.b=1\n" + ConfigKeys.ASSESSMENT_NAME
                + "=New\nz=2\n"), "continued entry replaced whole: "
                + contText.replace(LF, SHOWN_LINE_BREAK));
    }

    // Makes a zip in memory from entry names, each followed by its text.
    private static byte[] zipBytes(String[] pairs) throws Exception {
        ByteArrayOutputStream bytes = new ByteArrayOutputStream();
        ZipOutputStream out = new ZipOutputStream(bytes);
        for (int i = 0; i < pairs.length; i += ENTRY_PARTS) {
            int textIndex = i + 1;
            out.putNextEntry(new ZipEntry(pairs[i]));
            out.write(pairs[textIndex].getBytes(StandardCharsets.UTF_8));
            out.closeEntry();
        }
        out.close();
        return bytes.toByteArray();
    }

    // Writes a header comment with a name and an Email ID.
    private static String header(String name, String email) {
        return "/*\n * Name: " + name + "\n * Email ID: " + email + "\n */\n";
    }

    // Makes a grading service that only unpacks, checks and fixes.
    private static GradingService service(AppConfig config, FixManager fix,
            SqliteResultRepository repo) {
        return new GradingService(config, new FixOnlyPipelineFactory(config,
                fix), new AssessmentFactory(config), repo);
    }

    // Makes a FixManager with every fixer the app uses.
    private static FixManager fixManager(AppConfig config) {
        FixManager manager = new FixManager();
        manager.addFixer(new EncodingFixer());
        manager.addFixer(new FolderHierarchyFixer(config));
        manager.addFixer(new FolderNameFixer(config));
        manager.addFixer(new CaseNameFixer(config));
        manager.addFixer(new MisplacedFileFixer(config));
        manager.addFixer(new HeaderNameFixer(config));
        return manager;
    }

    // Finds a submission's first problem of a kind, or null.
    private static Anomaly findType(Submission submission, AnomalyType type) {
        for (Anomaly anomaly : submission.getAnomalies()) {
            if (anomaly.getType() == type) {
                return anomaly;
            }
        }
        return null;
    }

    // Checks approve, reopen and undo of a fix in one session, after a
    // restart and after grading again.
    private static void checkAnomalyRestart(Path root) throws Exception {
        AppConfig config = configCopy(root, "anomaly.properties");
        Path working = root.resolve("working");
        config.setString(ConfigKeys.WORKING_DIR, working.toString());
        Path roster = root.resolve("roster.csv");
        Files.writeString(roster, "OrgDefinedId,Username,Last Name,First Name,"
                + "Email\n#1,#amy,Ong,Amy,amy@x.edu\n");
        config.setString(ConfigKeys.SCORE_SHEET_FILE, roster.toString());
        config.setString(ConfigKeys.SCORE_SHEET_BY_SECTION, "");
        config.setString(ConfigKeys.PLAGIARISM_ENABLED, "false");
        Path db = root.resolve("anomaly.db");
        config.setString(ConfigKeys.DATABASE_FILE, db.toString());
        Path upload = root.resolve("2023-2024-amy.zip");
        writeUpload(upload);

        String runId = checkFirstSession(config, db, upload);
        checkSecondSession(config, db, runId);
        checkThirdSession(config, db, runId);
    }

    // Writes amy's upload: every answer with a good header, inside the
    // starter's top folder, which the folder name fix renames.
    private static void writeUpload(Path upload) throws Exception {
        String good = header("Amy Ong", "amy@x.edu");
        List<String> pairs = new ArrayList<>();
        for (String[] answer : ANSWERS) {
            pairs.add(TEMPLATE_FOLDER + SLASH + answer[ANSWER_FOLDER] + SLASH
                    + answer[ANSWER_CLASS] + JAVA_SUFFIX);
            pairs.add(good + "class " + answer[ANSWER_CLASS] + " {}\n");
        }
        Files.write(upload, zipBytes(pairs.toArray(new String[0])));
    }

    // Session 1: grades, the folder is renamed, then approves and reopens
    // the fix. Returns the id of the run.
    private static String checkFirstSession(AppConfig config, Path db,
            Path upload) throws Exception {
        SqliteResultRepository repo1 = new SqliteResultRepository(
                db.toString());
        repo1.open();
        FixManager fix1 = fixManager(config);
        GradingService grading1 = service(config, fix1, repo1);
        GradingRun run1 = grading1.startGrading(Arrays.asList(upload),
                SECTION, new NoTestersSource(), new QuietProgressListener());
        Submission amy = run1.findLoaded(AMY);
        Anomaly folder = findType(amy, AnomalyType.WRONG_FOLDER_NAME);
        check(folder != null && folder.getFixStatus() == FixStatus.APPLIED,
                "session 1: wrong folder name fixed");
        check(Files.isDirectory(amy.getWorkingCopyPath().resolve(AMY)),
                "session 1: amy folder made");
        AnomalyService anomalies1 = new AnomalyService(fix1, repo1, null,
                grading1);
        anomalies1.approveFix(folder);
        check(folder.getFixStatus() == FixStatus.APPROVED, "approve");
        boolean reopened = anomalies1.reopenFix(folder)
                && folder.getFixStatus() == FixStatus.APPLIED;
        check(reopened, "reopen");
        check(!anomalies1.reopenFix(folder), "reopen twice false");
        String runId = run1.getId();
        repo1.close();
        return runId;
    }

    // Session 2: a new FixManager and the run read back from the
    // database; approves with no record, then undoes the earlier fix.
    private static void checkSecondSession(AppConfig config, Path db,
            String runId) throws Exception {
        SqliteResultRepository repo2 = new SqliteResultRepository(
                db.toString());
        repo2.open();
        GradingRun loaded = repo2.loadRun(runId);
        Submission loadedAmy = loaded.findLoaded(AMY);
        Anomaly loadedFolder = findType(loadedAmy,
                AnomalyType.WRONG_FOLDER_NAME);
        boolean readBackApplied = loadedFolder != null
                && loadedFolder.getFixStatus() == FixStatus.APPLIED;
        check(readBackApplied, "session 2: fix status read back APPLIED");
        FixManager fix2 = fixManager(config);
        GradingService grading2 = service(config, fix2, repo2);
        AnomalyService anomalies2 = new AnomalyService(fix2, repo2, null,
                grading2);
        check(anomalies2.undoFix(loadedFolder) == null,
                "session 2: plain undo has no record and does nothing");
        // Approve after restart: no record, saved straight to the database
        anomalies2.approveFix(loadedFolder);
        check(loadedFolder.getFixStatus() == FixStatus.APPROVED,
                "session 2: approve without a record");
        boolean approveSaved = repo2.loadRun(runId).findLoaded(AMY) != null
                && findType(repo2.loadRun(runId).findLoaded(AMY),
                AnomalyType.WRONG_FOLDER_NAME).getFixStatus()
                == FixStatus.APPROVED;
        check(approveSaved, "session 2: approve saved");
        UndoResult undone = anomalies2.undoEarlierFix(loaded, loadedFolder);
        check(undone != null, "session 2: undoEarlierFix works");
        check(loadedFolder.getFixStatus() == FixStatus.UNDONE,
                "session 2: card shows UNDONE");
        Submission afterUndo = loaded.findLoaded(AMY);
        boolean originalFolderBack = afterUndo != null
                && Files.isDirectory(afterUndo.getWorkingCopyPath()
                .resolve(TEMPLATE_FOLDER));
        check(originalFolderBack,
                "session 2: working copy has the original folder again");
        String rejected = "";
        if (afterUndo != null) {
            rejected = afterUndo.getRejectedFixes().toString();
        }
        check(afterUndo != null && !afterUndo.getRejectedFixes().isEmpty(),
                "session 2: the fix is rejected for next time " + rejected);
        repo2.close();
    }

    // Session 3: reads the run back and grades again; the fix stays
    // undone and can be applied again.
    private static void checkThirdSession(AppConfig config, Path db,
            String runId) throws Exception {
        SqliteResultRepository repo3 = new SqliteResultRepository(
                db.toString());
        repo3.open();
        GradingRun third = repo3.loadRun(runId);
        Submission thirdAmy = third.findLoaded(AMY);
        Anomaly thirdFolder = findType(thirdAmy,
                AnomalyType.WRONG_FOLDER_NAME);
        String thirdStatus = "none";
        if (thirdFolder != null) {
            thirdStatus = String.valueOf(thirdFolder.getFixStatus());
        }
        boolean undoneReadBack = thirdFolder != null
                && thirdFolder.getFixStatus() == FixStatus.UNDONE;
        check(undoneReadBack,
                "session 3: UNDONE read back (" + thirdStatus + ")");
        check(!thirdAmy.getRejectedFixes().isEmpty(),
                "session 3: rejected fix read back");
        FixManager fix3 = fixManager(config);
        GradingService grading3 = service(config, fix3, repo3);
        grading3.regradeSubmission(third, thirdAmy,
                new QuietProgressListener());
        Submission regraded = third.findLoaded(AMY);
        Anomaly regradedFolder = findType(regraded,
                AnomalyType.WRONG_FOLDER_NAME);
        boolean notMadeAgain = regradedFolder != null
                && regradedFolder.getFixStatus() != FixStatus.APPLIED
                && Files.isDirectory(regraded.getWorkingCopyPath()
                .resolve(TEMPLATE_FOLDER));
        check(notMadeAgain,
                "session 3: regrade does not make the undone fix again");
        AnomalyService anomalies3 = new AnomalyService(fix3, repo3, null,
                grading3);
        boolean canReapply = regradedFolder != null
                && anomalies3.canReapplyFix(regradedFolder);
        check(canReapply, "session 3: fix can be reapplied");
        repo3.close();
        check(anomalies3.listAnomalies(null).isEmpty(), "listAnomalies null");
        anomalies3.approveFix(null);
        boolean nullQuiet = anomalies3.undoFix(null) == null
                && anomalies3.undoEarlierFix(null, null) == null;
        check(nullQuiet, "null anomalies quiet");
    }

    // Checks AssessmentLibrary: validate, save, list, rename and delete.
    private static void checkLibrary(Path root) throws Exception {
        AppConfig config = configCopy(root, "library.properties");
        config.setString(ConfigKeys.ASSESSMENTS_DIR,
                root.resolve("assessments").toString());
        config.setString(ConfigKeys.CHOSEN_SUBMISSIONS_FILE,
                root.resolve("chosen.properties").toString());
        AssessmentLibrary library = new AssessmentLibrary(config,
                new SettingsService(config));
        // The sample Lab Test's folders, from the fixed test settings
        String testersFolder = config.getString(ConfigKeys.TESTERS_DIR);
        String starterFolder = config.getString(ConfigKeys.STARTER_DIR);
        SavedAssessment draft = new SavedAssessment();
        draft.setName("  Lab Test 9  ");
        draft.setTestersDirectory(testersFolder);
        draft.setStarterDirectory(starterFolder);
        draft.setModuleCode("CS102");
        draft.getQuestions().add(new Question(Q1A, Q1_FOLDER, "Q1a.java",
                0));
        checkValidate(library, draft);
        checkFolders(library, draft, testersFolder);
        checkSaveAndRename(root, library, draft);
        checkOddNames(root, library, draft);
    }

    // Checks which drafts AssessmentLibrary.validate refuses.
    private static void checkValidate(AssessmentLibrary library,
            SavedAssessment draft) {
        check(library.validate(draft, null, new ArrayList<String>())
                .isEmpty(), "valid draft: " + library.validate(draft, null,
                new ArrayList<String>()));
        check(!library.validate(draft, null, Arrays.asList("lab test 9"))
                .isEmpty(), "taken name ignoring case refused");
        check(library.validate(draft, "LAB TEST 9", Arrays.asList(
                "lab test 9")).isEmpty(), "keeping own name allowed");
        SavedAssessment blank = draft.copy();
        blank.setName("   ");
        check(!library.validate(blank, null, new ArrayList<String>())
                .isEmpty(), "blank name refused");
        SavedAssessment longName = draft.copy();
        longName.setName(FILLER.repeat(TOO_LONG_NAME));
        check(!library.validate(longName, null, new ArrayList<String>())
                .isEmpty(), "61-char name refused");
        SavedAssessment dupQ = draft.copy();
        dupQ.getQuestions().add(new Question("q1A", Q1_FOLDER, "Q1b.java",
                0));
        check(!library.validate(dupQ, null, new ArrayList<String>())
                .isEmpty(), "duplicate question id ignoring case refused");
        SavedAssessment noQ = draft.copy();
        noQ.getQuestions().clear();
        check(!library.validate(noQ, null, new ArrayList<String>())
                .isEmpty(), "no questions refused");
    }

    // Checks the tester and starter folder check and the max score read
    // from a tester.
    private static void checkFolders(AssessmentLibrary library,
            SavedAssessment draft, String testersFolder) {
        check(library.checkFolders(draft).isEmpty(), "folders ok: "
                + library.checkFolders(draft));
        check(library.readMaxScore(testersFolder, Q1A) > 0,
                "max score read from tester");
        SavedAssessment missingTester = draft.copy();
        missingTester.getQuestions().add(new Question("Q9", "Q9", "Q9.java",
                0));
        check(library.checkFolders(missingTester).contains("Q9Tester"),
                "missing tester named");
    }

    // Checks saving, the file name chosen, two names with one file name
    // and a rename.
    private static void checkSaveAndRename(Path root,
            AssessmentLibrary library, SavedAssessment draft)
            throws Exception {
        library.save(draft, null);
        List<SavedAssessment> saved = library.list();
        SavedAssessment found = null;
        for (SavedAssessment entry : saved) {
            if (entry.getName().equals(LAB_NAME)) {
                found = entry;
            }
        }
        check(found != null, "saved name trimmed and listed");
        check(Files.exists(root.resolve(LAB_FILE)),
                "file named lab-test-9.properties");
        SavedAssessment same = draft.copy();
        same.setName("Lab-Test-9!");
        library.save(same, null);
        boolean ownFile = Files.exists(root.resolve(LAB_FILE))
                && Files.exists(root.resolve(
                "assessments/lab-test-9-2.properties"));
        check(ownFile, "a name with the same file name gets its own file");
        int expectedListed = 2;
        check(library.list().size() >= expectedListed, "both listed");
        // Rename
        SavedAssessment renamed = draft.copy();
        renamed.setName(RENAMED_LAB);
        library.save(renamed, LAB_NAME);
        boolean oldGone = true;
        for (SavedAssessment entry : library.list()) {
            if (entry.getName().equals(LAB_NAME)) {
                oldGone = false;
            }
        }
        check(oldGone && !Files.exists(root.resolve(LAB_FILE)),
                "rename moves file");
    }

    // Checks a junk file in the folder, a name of only symbols and a
    // delete.
    private static void checkOddNames(Path root, AssessmentLibrary library,
            SavedAssessment draft) throws Exception {
        // A saved file that is not an assessment is skipped
        Files.writeString(root.resolve("assessments/junk.properties"),
                "nonsense=1\n\u0000\u0001");
        boolean listed = true;
        try {
            library.list();
        } catch (RuntimeException exception) {
            listed = false;
        }
        check(listed, "a junk file in the folder does not break the list");
        // Name with only symbols
        SavedAssessment symbols = draft.copy();
        symbols.setName("!!!");
        library.save(symbols, null);
        boolean hiddenFile = Files.exists(root.resolve(
                "assessments/.properties"));
        check(!hiddenFile, "a name of symbols does not make a hidden file");
        // Delete
        library.delete(RENAMED_LAB);
        boolean deleted = true;
        for (SavedAssessment entry : library.list()) {
            if (entry.getName().equals(RENAMED_LAB)) {
                deleted = false;
            }
        }
        check(deleted, "delete removes it");
    }

    // Counts one check and prints it when it fails.
    private static void check(boolean condition, String message) {
        if (condition) {
            passed++;
        } else {
            failed++;
            System.out.println("FAIL: " + message);
        }
    }
}
