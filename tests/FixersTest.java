/*
 * File: FixersTest.java
 *
 * Purpose: Unit checks for the fix package. Each case builds a working
 * copy with one kind of mistake, runs the real validators and FixStage,
 * checks the layout after the fix, then undoes every fix and checks that
 * the working copy is byte for byte what the student sent. Also checks
 * FixManager's rejected keys and records.
 *
 * Where it fits: Run by hand after compiling, as tests/README.md says.
 * Reads the fixed test settings. No student code runs.
 */
import java.io.File;
import java.nio.ByteBuffer;
import java.nio.charset.CharacterCodingException;
import java.nio.charset.StandardCharsets;
import java.nio.file.DirectoryStream;
import java.nio.file.Files;
import java.nio.file.LinkOption;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.Collections;
import java.util.List;
import java.util.Map;
import java.util.TreeMap;

import gradezzy.config.AppConfig;
import gradezzy.config.ConfigKeys;
import gradezzy.fix.CaseNameFixer;
import gradezzy.fix.EncodingFixer;
import gradezzy.fix.FixException;
import gradezzy.fix.FixManager;
import gradezzy.fix.FixRecord;
import gradezzy.fix.FolderHierarchyFixer;
import gradezzy.fix.FolderNameFixer;
import gradezzy.fix.HeaderNameFixer;
import gradezzy.fix.MisplacedFileFixer;
import gradezzy.grading.FixStage;
import gradezzy.grading.GradingContext;
import gradezzy.grading.ValidateStage;
import gradezzy.ingest.WorkingCopyManager;
import gradezzy.ingest.ZipExtractor;
import gradezzy.model.Anomaly;
import gradezzy.model.AnomalyType;
import gradezzy.model.Assessment;
import gradezzy.model.FixStatus;
import gradezzy.model.Section;
import gradezzy.model.Student;
import gradezzy.model.Submission;
import gradezzy.service.AssessmentFactory;
import gradezzy.util.FileUtils;
import gradezzy.validation.FileNameValidator;
import gradezzy.validation.HeaderNameValidator;
import gradezzy.validation.RequiredFilesValidator;
import gradezzy.validation.StructureValidator;
import gradezzy.validation.ValidationRunner;

/**
 * Runs the fixer checks and prints how many passed.
 */
public class FixersTest {

    // The section every student is in
    private static final Section SECTION = new Section("CS102", "G1");

    // The top folder name the starter code ships with
    private static final String TEMPLATE_FOLDER = "RenameToYourUsername";

    // The paths of two answer files below a student's top folder
    private static final String Q1A_PATH = "/Q1/Q1a.java";
    private static final String Q2B_PATH = "/Q2/Q2b.java";

    // Students whose last names the roster knows
    private static final String IVY = "ivy";
    private static final String JAY = "jay";
    private static final String OLI = "oli";
    private static final String RAY = "ray";

    // The students of the cases whose names come up often
    private static final String DAN = "dan";
    private static final String GUS = "gus";
    private static final String HAL = "hal";
    private static final String KIM = "kim";
    private static final String MAY = "may";
    private static final String NED = "ned";
    private static final String PAT = "pat";
    private static final String QUY = "quy";

    // The student whose answer is saved in windows-1252
    private static final String LEE = "lee";

    // The header every case starts from when it needs a good one
    private static final String GOOD_HEADER = header("Ann Lee",
            "ann@x.edu");

    // The name of the Windows text encoding
    private static final String WINDOWS_1252 = "windows-1252";

    // The three bytes of a UTF-8 byte order mark
    private static final byte BOM_FIRST = (byte) 0xEF;
    private static final byte BOM_SECOND = (byte) 0xBB;
    private static final byte BOM_THIRD = (byte) 0xBF;

    // The two bytes that start a little endian UTF-16 file
    private static final byte UTF16_FIRST = (byte) 0xFF;
    private static final byte UTF16_SECOND = (byte) 0xFE;

    // A lone é in windows-1252, which is not valid UTF-8
    private static final byte LONE_E_ACUTE = (byte) 0xE9;

    // Where each part sits in a row of questions()
    private static final int CLASS_PART = 0;
    private static final int FILE_PART = 1;
    private static final int FOLDER_PART = 2;

    // How many lines of a file firstLines shows
    private static final int SHOWN_LINES = 4;

    // The folder separator used in file keys
    private static final String SLASH = "/";

    // The end of a bracketed count in a check message
    private static final String CLOSE_BRACKET = ")";

    // The line break that splits a file into lines
    private static final String NEWLINE = "\n";

    // What firstLines puts after each line it shows
    private static final String LINE_SEPARATOR = " | ";

    // What goes between the kind of an open problem and its message
    private static final String TYPE_SEPARATOR = ":";

    // The number of checks that passed and failed
    private static int passed = 0;
    private static int failed = 0;

    // The fixed test settings
    private static AppConfig config = null;

    // The assessment the settings describe
    private static Assessment assessment = null;

    // The folder every working copy is built in
    private static Path working = null;

    /**
     * Runs every check.
     *
     * @param args not used
     * @throws Exception if a temporary file cannot be written
     */
    public static void main(String[] args) throws Exception {
        Path root = Files.createTempDirectory("gradezzy-fix-tests-")
                .toRealPath();
        try {
            config = TestConfig.load();
            working = root.resolve("working");
            Files.createDirectories(working);
            config.setString(ConfigKeys.WORKING_DIR, working.toString());
            config.setString(ConfigKeys.STARTER_DIR,
                    root.resolve("no-starter").toString());
            assessment = new AssessmentFactory(config).create();
            runCases();
            checkRejectedKeys();
            checkStatics();
        } finally {
            FileUtils.deleteDirectory(root);
        }
        System.out.println("FixersTest: " + passed + " passed, " + failed
                + " failed");
        if (failed > 0) {
            System.exit(1);
        }
    }

    // Runs every fix case, in order.
    private static void runCases() throws Exception {
        checkFolderFixes();
        checkFileFixes();
        checkHeaderFixes();
        checkEncodingFixes();
        checkHardCases();
    }

    // Cases 1 to 4: the folders around the answers are wrong.
    private static void checkFolderFixes() throws Exception {
        // 1. Wrong top folder name
        Map<String, byte[]> files = all(TEMPLATE_FOLDER, GOOD_HEADER);
        FixCaseOutcome outcome = fixCase("ann", files);
        boolean topRenamed = outcome.has("ann" + Q1A_PATH)
                && !outcome.has(TEMPLATE_FOLDER);
        check(topRenamed, "wrong top folder renamed to username: "
                + outcome.getAfter());
        check(outcome.fixed(AnomalyType.WRONG_FOLDER_NAME),
                "WRONG_FOLDER_NAME marked applied");
        check(outcome.isUndoneEqual(), "undo restores the wrong folder name");
        // 2. No wrapper folder
        files = all("", GOOD_HEADER);
        outcome = fixCase("ben", files);
        check(outcome.has("ben" + Q1A_PATH),
                "missing wrapper added: " + outcome.getAfter());
        check(outcome.isUndoneEqual(), "undo removes the added wrapper");
        // 3. Double wrapper
        files = all("cat/cat", GOOD_HEADER);
        outcome = fixCase("cat", files);
        check(outcome.has("cat" + Q1A_PATH), "double wrapper flattened: "
                + outcome.getAfter());
        check(outcome.isUndoneEqual(), "undo restores the double wrapper");
        // 4. Folder in the wrong case
        files = all(DAN, GOOD_HEADER);
        rename(files, DAN + "/Q1/", DAN + "/q1/");
        outcome = fixCase(DAN, files);
        boolean folderRenamed = outcome.has(DAN + Q1A_PATH)
                && outcome.has(DAN + "/Q1/Q1b.java");
        check(folderRenamed, "q1 renamed to Q1: " + outcome.getAfter());
        check(outcome.isUndoneEqual(), "undo restores q1");
    }

    // Cases 5 to 8: the answer files have the wrong names or places.
    private static void checkFileFixes() throws Exception {
        // 5. File in the wrong case
        Map<String, byte[]> files = all("eve", GOOD_HEADER);
        rename(files, "eve/Q2/Q2a.java", "eve/Q2/q2A.java");
        FixCaseOutcome outcome = fixCase("eve", files);
        boolean fileRenamed = outcome.has("eve/Q2/Q2a.java")
                && !outcome.has("eve/Q2/q2A.java");
        check(fileRenamed, "q2A.java renamed to Q2a.java: "
                + outcome.getAfter());
        check(outcome.isUndoneEqual(), "undo restores q2A.java");
        // 6. Browser copy name
        files = all("fay", GOOD_HEADER);
        rename(files, "fay/Q3/Q3.java", "fay/Q3/Q3 (1).java");
        outcome = fixCase("fay", files);
        check(outcome.has("fay/Q3/Q3.java"),
                "Q3 (1).java renamed: " + outcome.getAfter());
        check(outcome.isUndoneEqual(), "undo restores Q3 (1).java");
        // 7. File in the wrong question folder
        files = all(GUS, GOOD_HEADER);
        rename(files, GUS + Q2B_PATH, GUS + "/Q1/Q2b.java");
        outcome = fixCase(GUS, files);
        boolean fileMoved = outcome.has(GUS + Q2B_PATH)
                && !outcome.has(GUS + "/Q1/Q2b.java");
        check(fileMoved, "Q2b.java moved from Q1 to Q2: "
                + outcome.getAfter());
        check(outcome.isUndoneEqual(), "undo moves Q2b.java back");
        // 8. Answer files with no question folders
        files = new TreeMap<>();
        for (String[] question : questions()) {
            files.put(HAL + SLASH + question[FILE_PART], bytes(GOOD_HEADER
                    + "class " + question[CLASS_PART] + " {}\n"));
        }
        outcome = fixCase(HAL, files);
        boolean looseFiled = outcome.has(HAL + Q1A_PATH)
                && outcome.has(HAL + Q2B_PATH)
                && outcome.has(HAL + "/Q3/Q3.java");
        check(looseFiled, "loose files put into question folders: "
                + outcome.getAfter());
        check(outcome.isUndoneEqual(), "undo puts loose files back");
    }

    // Cases 9 to 11: the header is blank, half filled or has no labels.
    private static void checkHeaderFixes() throws Exception {
        // 9. Blank header filled from the roster
        Map<String, byte[]> files = all(IVY, header("", ""));
        FixCaseOutcome outcome = fixCase(IVY, files);
        String q1a = outcome.text(IVY + Q1A_PATH);
        check(q1a.contains("Ivy Tan") && q1a.contains("ivy@x.edu"),
                "header filled from roster: " + firstLines(q1a));
        check(outcome.fixed(AnomalyType.MISSING_HEADER_NAME),
                "MISSING_HEADER_NAME marked applied");
        check(outcome.isUndoneEqual(), "undo restores the blank header");
        String undonePaths = outcome.getAfterUndo().keySet().toString();
        check(!undonePaths.contains("backup"),
                "no backup file left in the working copy after undo");
        // 10. Header with only email missing
        files = all(JAY, header("Jay Ong", ""));
        outcome = fixCase(JAY, files);
        String jayAnswer = outcome.text(JAY + Q1A_PATH);
        boolean emailFilled = jayAnswer.contains("jay@x.edu")
                && jayAnswer.contains("Jay Ong");
        check(emailFilled, "email filled, name kept");
        check(outcome.isUndoneEqual(), "undo of email fill");
        // 11. Header without labels at all
        files = all(KIM, "public class X {}\n");
        outcome = fixCase(KIM, files);
        System.out.println("   no-label header after fix: "
                + firstLines(outcome.text(KIM + Q1A_PATH)));
        check(outcome.isUndoneEqual(), "undo of header with no labels");
    }

    // Cases 12 to 14: the answer is not saved in plain UTF-8.
    private static void checkEncodingFixes() throws Exception {
        // 12. Windows-1252 source
        Map<String, byte[]> files = all(LEE, GOOD_HEADER);
        String leePath = LEE + Q1A_PATH;
        files.put(leePath, (GOOD_HEADER + "// café\nclass Q1a {}\n")
                .getBytes(WINDOWS_1252));
        FixCaseOutcome outcome = fixCase(LEE, files);
        boolean savedAsUtf8 = outcome.text(leePath).contains("café")
                && isUtf8(outcome.getAfterBytes().get(leePath));
        check(savedAsUtf8, "windows-1252 file saved as UTF-8");
        check(outcome.fixed(AnomalyType.WRONG_ENCODING),
                "WRONG_ENCODING applied");
        check(outcome.isUndoneEqual(), "undo restores windows-1252 bytes");
        // 13. UTF-8 with BOM
        files = all(MAY, GOOD_HEADER);
        byte[] plain = bytes(GOOD_HEADER + "class Q1a {}\n");
        byte[] bom = {BOM_FIRST, BOM_SECOND, BOM_THIRD};
        byte[] withBom = new byte[plain.length + bom.length];
        System.arraycopy(bom, 0, withBom, 0, bom.length);
        System.arraycopy(plain, 0, withBom, bom.length, plain.length);
        files.put(MAY + Q1A_PATH, withBom);
        outcome = fixCase(MAY, files);
        byte[] mayAnswer = outcome.getAfterBytes().get(MAY + Q1A_PATH);
        check(Arrays.equals(mayAnswer, plain), "BOM removed");
        check(outcome.isUndoneEqual(), "undo puts the BOM back");
        // 14. UTF-16
        files = all(NED, GOOD_HEADER);
        byte[] utf16 = (GOOD_HEADER + "class Q1a {}\n").getBytes(
                StandardCharsets.UTF_16);
        files.put(NED + Q1A_PATH, utf16);
        outcome = fixCase(NED, files);
        byte[] nedAnswer = outcome.getAfterBytes().get(NED + Q1A_PATH);
        check(Arrays.equals(nedAnswer, plain), "UTF-16 saved as UTF-8");
        check(outcome.isUndoneEqual(), "undo puts UTF-16 back");
    }

    // Cases 15 to 17: everything wrong at once, a student not on the
    // roster, and two files that both look like the same answer.
    private static void checkHardCases() throws Exception {
        // 15. Everything wrong at once
        Map<String, byte[]> files = all(TEMPLATE_FOLDER, header("", ""));
        rename(files, TEMPLATE_FOLDER + "/Q1/", TEMPLATE_FOLDER + "/q1/");
        rename(files, TEMPLATE_FOLDER + Q2B_PATH,
                TEMPLATE_FOLDER + "/Q3/Q2b.java");
        files.put(TEMPLATE_FOLDER + "/q1/Q1a.java",
                (header("", "") + "// naïve\n").getBytes(WINDOWS_1252));
        FixCaseOutcome outcome = fixCase(OLI, files);
        String oliPath = OLI + Q1A_PATH;
        check(outcome.has(oliPath) && outcome.has(OLI + Q2B_PATH),
                "combined: layout fixed: " + outcome.getAfter());
        boolean headerAndEncoding = outcome.text(oliPath).contains("Oli Wu")
                && outcome.text(oliPath).contains("naïve");
        check(headerAndEncoding, "combined: header and encoding fixed");
        check(outcome.isUndoneEqual(), "combined: undo restores everything");
        check(outcome.getOpenAfter() == 0,
                "combined: no open structure problems left ("
                + outcome.getOpenAfter() + ": " + outcome.getOpenMessages()
                + CLOSE_BRACKET);
        // 16. Student not on the roster: header left alone
        files = all(PAT, header("", ""));
        outcome = fixCaseWith(PAT, files, false);
        check(Arrays.equals(outcome.getAfterBytes().get(PAT + Q1A_PATH),
                files.get(PAT + Q1A_PATH)),
                "header not changed for a student not on the roster");
        check(outcome.getFailureReasons().toString()
                .contains("not on the roster"),
                "failure reason names the roster");
        // 17. Two candidate files in a case clash
        files = all(QUY, GOOD_HEADER);
        files.put(QUY + "/Q1/q1a.java", bytes("class q1a {}"));
        files.remove(QUY + Q1A_PATH);
        files.put(QUY + "/Q1/Q1A.JAVA", bytes("class Q1A {}"));
        outcome = fixCaseLenient(QUY, files);
        System.out.println("   two case-variant candidates for Q1a.java: "
                + outcome.getAfter() + " reasons="
                + outcome.getFailureReasons());
        check(outcome.isUndoneEqual(), "undo with two case variants");
    }

    // Checks undo, approve and rejected fix keys on FixManager.
    private static void checkRejectedKeys() throws Exception {
        Map<String, byte[]> files = all(TEMPLATE_FOLDER,
                header("Ray Koh", "ray@x.edu"));
        FixRecord record = checkUndo(files);
        checkRejected(files, record);
        checkApprove(files);
    }

    // Checks that a fix is recorded with a key and that undo marks it
    // undone, quietly the second time; returns the fix's record.
    private static FixRecord checkUndo(Map<String, byte[]> files)
            throws Exception {
        Submission submission = build(RAY, files, true);
        FixManager manager = manager();
        runStages(submission, manager);
        List<FixRecord> records = manager.getFixRecords();
        check(records.size() == 1,
                "one fix record for a wrong folder name ("
                + records.size() + CLOSE_BRACKET);
        FixRecord record = records.get(0);
        check(record.getFixKey() != null, "fix key set");
        manager.undo(record);
        check(record.getAnomaly().getFixStatus() == FixStatus.UNDONE,
                "undo marks UNDONE");
        manager.undo(record);
        check(true, "second undo is quiet");
        submission.rejectFix(record.getFixKey());
        return record;
    }

    // Checks that a rejected fix is not applied again when the student
    // is graded again, and that the skipped fix can be named.
    private static void checkRejected(Map<String, byte[]> files,
            FixRecord record) throws Exception {
        // Run again from a fresh working copy, as a re-grade does
        Submission again = build(RAY, files, true);
        again.rejectFix(record.getFixKey());
        FixManager second = manager();
        runStages(again, second);
        check(second.getFixRecords().isEmpty(),
                "a rejected fix is not applied again");
        check(Files.isDirectory(again.getWorkingCopyPath().resolve(
                TEMPLATE_FOLDER)), "rejected fix left the folder");
        boolean skippedNamed = false;
        for (Anomaly anomaly : again.getAnomalies()) {
            if (second.findRejectedKey(anomaly) != null) {
                skippedNamed = true;
            }
        }
        check(skippedNamed, "findRejectedKey names the skipped fix");
    }

    // Checks approve, undo with no fixer, applyAll again, and undo once
    // the working copy is gone.
    private static void checkApprove(Map<String, byte[]> files)
            throws Exception {
        Submission third = build(RAY, files, true);
        FixManager approveManager = manager();
        runStages(third, approveManager);
        FixRecord approved = approveManager.getFixRecords().get(0);
        approveManager.approve(approved);
        check(approved.getAnomaly().getFixStatus() == FixStatus.APPROVED,
                "approve marks APPROVED");
        approveManager.approve(null);
        approveManager.undo(null);
        check(true, "approve/undo null quiet");
        // A FixManager without the fixer cannot undo
        FixManager empty = new FixManager();
        boolean noFixer = false;
        try {
            empty.undo(approved);
        } catch (FixException expected) {
            noFixer = true;
        }
        check(noFixer, "undo without the fixer throws FixException");
        // applyAll forgets the student's earlier records
        approveManager.applyAll(third);
        int count = 0;
        for (FixRecord rayRecord : approveManager.getFixRecords()) {
            if (rayRecord.getAnomaly().getUsername().equals(RAY)) {
                count++;
            }
        }
        check(count <= 1,
                "applyAll again keeps at most one record for ray ("
                + count + CLOSE_BRACKET);
        // Undo after the working copy was rebuilt (folder gone)
        FileUtils.deleteDirectory(third.getWorkingCopyPath());
        boolean goneRefused = false;
        try {
            approveManager.undo(approved);
        } catch (FixException expected) {
            goneRefused = true;
        }
        check(goneRefused,
                "undo when the folder is gone throws FixException");
        check(approved.getAnomaly().getFixStatus() != FixStatus.UNDONE,
                "failed undo does not mark UNDONE");
    }

    // Checks the static encoding helpers of EncodingFixer.
    private static void checkStatics() {
        byte[] ascii = bytes("class A {}");
        check(!EncodingFixer.needsConverting(ascii), "ASCII needs nothing");
        check(!EncodingFixer.needsConverting(new byte[0]), "empty fine");
        check(EncodingFixer.needsConverting(new byte[] {LONE_E_ACUTE}),
                "lone 0xE9 needs converting");
        check(EncodingFixer.needsConverting(new byte[] {BOM_FIRST,
                BOM_SECOND, BOM_THIRD, 'a'}), "BOM needs converting");
        check(EncodingFixer.hasByteOrderMark(new byte[] {BOM_FIRST,
                BOM_SECOND, BOM_THIRD}), "hasByteOrderMark");
        check(!EncodingFixer.hasByteOrderMark(new byte[] {BOM_FIRST}),
                "short not BOM");
        check(EncodingFixer.needsConverting(new byte[] {UTF16_FIRST,
                UTF16_SECOND, 'a', 0}), "UTF-16 needs converting");
        check(!EncodingFixer.needsConverting(bytes("// 中文")),
                "valid UTF-8 Chinese fine");
        String described = EncodingFixer.describeProblem("u/Q1/A.java",
                new byte[] {LONE_E_ACUTE});
        check(described.startsWith("u/Q1/A.java"),
                "describeProblem starts with the path");
    }

    // Runs one case for a student on the roster.
    private static FixCaseOutcome fixCase(String user,
            Map<String, byte[]> files) throws Exception {
        return fixCaseWith(user, files, true);
    }

    // Runs one case where the fixers may not agree on a fix.
    private static FixCaseOutcome fixCaseLenient(String user,
            Map<String, byte[]> files) throws Exception {
        return fixCaseWith(user, files, true);
    }

    // Builds the working copy, fixes it, checks it again, undoes every
    // fix and says what happened at each step.
    private static FixCaseOutcome fixCaseWith(String user,
            Map<String, byte[]> files, boolean onRoster) throws Exception {
        Submission submission = build(user, files, onRoster);
        Map<String, byte[]> before = snapshot(
                submission.getWorkingCopyPath());
        FixManager manager = manager();
        runStages(submission, manager);
        FixCaseOutcome outcome = new FixCaseOutcome();
        List<Anomaly> anomalies = new ArrayList<>(submission.getAnomalies());
        for (Anomaly anomaly : anomalies) {
            if (anomaly.getFixStatus() == FixStatus.APPLIED) {
                outcome.addAppliedType(anomaly.getType());
            }
        }
        outcome.setAfterBytes(snapshot(submission.getWorkingCopyPath()));
        for (Anomaly anomaly : submission.getAnomalies()) {
            String reason = manager.getFailureReason(anomaly);
            if (reason != null) {
                outcome.putFailureReason(anomaly.getType().toString(),
                        reason);
            }
        }
        recheck(user, submission, outcome);
        undoEvery(user, manager);
        outcome.setAfterUndo(snapshot(submission.getWorkingCopyPath()));
        outcome.setUndoneEqual(sameTree(before, outcome.getAfterUndo()));
        if (!outcome.isUndoneEqual()) {
            System.out.println("   " + user + " before=" + before.keySet()
                    + " afterUndo=" + outcome.getAfterUndo().keySet());
        }
        return outcome;
    }

    // Validates the fixed working copy again and notes every structure
    // problem still open.
    private static void recheck(String user, Submission submission,
            FixCaseOutcome outcome) {
        Submission recheck = new Submission(user, SECTION,
                submission.getOriginalPath());
        recheck.setWorkingCopyPath(submission.getWorkingCopyPath());
        recheck.setStudent(submission.getStudent());
        for (Anomaly anomaly : validators().runAll(recheck, assessment)) {
            outcome.addOpenMessage(anomaly.getType() + TYPE_SEPARATOR
                    + anomaly.getMessage());
        }
    }

    // Undoes every fix the manager made, newest first, and prints why
    // when the undo fails.
    private static void undoEvery(String user, FixManager manager) {
        List<FixRecord> records = new ArrayList<>(manager.getFixRecords());
        Collections.reverse(records);
        try {
            manager.undoAll(records);
        } catch (FixException exception) {
            System.out.println("   undo failed for " + user + ": "
                    + exception.getMessage());
        }
    }

    // Makes a FixManager with every fixer the app uses.
    private static FixManager manager() {
        FixManager manager = new FixManager();
        manager.addFixer(new EncodingFixer());
        manager.addFixer(new FolderHierarchyFixer(config));
        manager.addFixer(new FolderNameFixer(config));
        manager.addFixer(new CaseNameFixer(config));
        manager.addFixer(new MisplacedFileFixer(config));
        manager.addFixer(new HeaderNameFixer(config));
        return manager;
    }

    // Makes a runner with every validator the app uses.
    private static ValidationRunner validators() {
        ValidationRunner runner = new ValidationRunner();
        runner.addValidator(new StructureValidator());
        runner.addValidator(new FileNameValidator());
        runner.addValidator(new RequiredFilesValidator());
        runner.addValidator(new HeaderNameValidator(config));
        return runner;
    }

    // Runs the validate and fix stages on one submission.
    private static void runStages(Submission submission, FixManager manager)
            throws Exception {
        GradingContext context = new GradingContext(config, assessment);
        new ValidateStage(validators()).process(submission, context);
        ValidationRunner fileChecks = new ValidationRunner();
        fileChecks.addValidator(new FileNameValidator());
        fileChecks.addValidator(new RequiredFilesValidator());
        fileChecks.addValidator(new HeaderNameValidator(config));
        WorkingCopyManager copies = new WorkingCopyManager(config,
                new ZipExtractor(config));
        new FixStage(manager, fileChecks, copies, null).process(submission,
                context);
    }

    // Writes the files into a fresh working copy and returns its
    // submission, with a roster entry when the student is on it.
    private static Submission build(String user, Map<String, byte[]> files,
            boolean onRoster) throws Exception {
        Path copy = working.resolve(user);
        FileUtils.deleteDirectory(copy);
        for (String key : files.keySet()) {
            Path file = copy.resolve(key);
            Files.createDirectories(file.getParent());
            Files.write(file, files.get(key));
        }
        Files.createDirectories(copy);
        Submission submission = new Submission(user, SECTION, copy);
        submission.setWorkingCopyPath(copy);
        if (onRoster) {
            String first = Character.toUpperCase(user.charAt(0))
                    + user.substring(1);
            String last = lastNameOf(user);
            submission.setStudent(new Student("1", user, last, first,
                    user + "@x.edu"));
        }
        return submission;
    }

    // Gives the roster's last name for a student.
    private static String lastNameOf(String user) {
        if (user.equals(IVY)) {
            return "Tan";
        }
        if (user.equals(JAY)) {
            return "Ong";
        }
        if (user.equals(OLI)) {
            return "Wu";
        }
        if (user.equals(RAY)) {
            return "Koh";
        }
        return "Lee";
    }

    // Lists each answer as its class name, file name and folder.
    private static String[][] questions() {
        return new String[][] {{"Q1a", "Q1a.java", "Q1"},
            {"Q1b", "Q1b.java", "Q1"}, {"Q2a", "Q2a.java", "Q2"},
            {"Q2b", "Q2b.java", "Q2"}, {"Q3", "Q3.java", "Q3"}};
    }

    // Every answer file under the given top folder ("" for none).
    private static Map<String, byte[]> all(String top, String headerText) {
        Map<String, byte[]> files = new TreeMap<>();
        String prefix = "";
        if (!top.isEmpty()) {
            prefix = top + SLASH;
        }
        for (String[] question : questions()) {
            files.put(prefix + question[FOLDER_PART] + SLASH
                    + question[FILE_PART], bytes(headerText + "class "
                    + question[CLASS_PART] + " {}\n"));
        }
        return files;
    }

    // Renames every path that starts with one prefix to start with
    // another.
    private static void rename(Map<String, byte[]> files, String from,
            String to) {
        Map<String, byte[]> copy = new TreeMap<>(files);
        files.clear();
        for (String oldKey : copy.keySet()) {
            String key = oldKey;
            if (key.startsWith(from)) {
                key = to + key.substring(from.length());
            }
            files.put(key, copy.get(oldKey));
        }
    }

    // Writes a header comment with a name and an Email ID.
    private static String header(String name, String email) {
        return "/*\n * Name: " + name + "\n * Email ID: " + email + "\n */\n";
    }

    // Gives the UTF-8 bytes of some text.
    private static byte[] bytes(String text) {
        return text.getBytes(StandardCharsets.UTF_8);
    }

    // Says whether the bytes are valid UTF-8.
    private static boolean isUtf8(byte[] data) {
        try {
            StandardCharsets.UTF_8.newDecoder().decode(
                    ByteBuffer.wrap(data));
            return true;
        } catch (CharacterCodingException exception) {
            return false;
        }
    }

    // Gives the first few lines of a file on one line.
    private static String firstLines(String text) {
        String[] lines = text.split(NEWLINE);
        int shownCount = Math.min(SHOWN_LINES, lines.length);
        String shown = "";
        for (int i = 0; i < shownCount; i++) {
            shown = shown + lines[i] + LINE_SEPARATOR;
        }
        return shown;
    }

    // Reads every file and folder below a folder, by relative path;
    // folders end in a slash and have no bytes.
    private static Map<String, byte[]> snapshot(Path folder)
            throws Exception {
        Map<String, byte[]> files = new TreeMap<>();
        if (!Files.exists(folder)) {
            return files;
        }
        addEntries(folder, folder, files);
        return files;
    }

    // Adds every file and folder inside one folder to a snapshot, by its
    // path from the top folder, then the entries of each folder inside.
    private static void addEntries(Path top, Path folder,
            Map<String, byte[]> files) throws Exception {
        DirectoryStream<Path> children = null;
        try {
            children = Files.newDirectoryStream(folder);
            for (Path path : children) {
                // Keys use / on Windows too, as the checks expect
                String key = top.relativize(path).toString()
                        .replace(File.separator, SLASH);
                if (Files.isRegularFile(path)) {
                    files.put(key, Files.readAllBytes(path));
                } else {
                    files.put(key + SLASH, new byte[0]);
                }
                if (Files.isDirectory(path, LinkOption.NOFOLLOW_LINKS)) {
                    addEntries(top, path, files);
                }
            }
        } finally {
            FileUtils.closeIfOpen(children);
        }
    }

    // Says whether two snapshots have the same paths and bytes.
    private static boolean sameTree(Map<String, byte[]> expected,
            Map<String, byte[]> actual) {
        if (!expected.keySet().equals(actual.keySet())) {
            return false;
        }
        for (String key : expected.keySet()) {
            if (!Arrays.equals(expected.get(key), actual.get(key))) {
                System.out.println("   bytes differ: " + key);
                return false;
            }
        }
        return true;
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
