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
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.Collections;
import java.util.List;
import java.util.Map;
import java.util.TreeMap;
import java.util.stream.Stream;

import gradezzy.config.AppConfig;
import gradezzy.config.ConfigKeys;
import gradezzy.fix.CaseNameFixer;
import gradezzy.fix.EncodingFixer;
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

    // The number of checks that passed and failed
    private static int passed = 0;
    private static int failed = 0;

    // The fixed test settings
    private static AppConfig config;

    // The assessment the settings describe
    private static Assessment assessment;

    // The folder every working copy is built in
    private static Path working;

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
        Outcome outcome = fixCase("ann", files);
        check(outcome.has("ann" + Q1A_PATH)
                && !outcome.has(TEMPLATE_FOLDER),
                "wrong top folder renamed to username: " + outcome.after);
        check(outcome.fixed(AnomalyType.WRONG_FOLDER_NAME),
                "WRONG_FOLDER_NAME marked applied");
        check(outcome.undoneEqual, "undo restores the wrong folder name");
        // 2. No wrapper folder
        files = all("", GOOD_HEADER);
        outcome = fixCase("ben", files);
        check(outcome.has("ben" + Q1A_PATH),
                "missing wrapper added: " + outcome.after);
        check(outcome.undoneEqual, "undo removes the added wrapper");
        // 3. Double wrapper
        files = all("cat/cat", GOOD_HEADER);
        outcome = fixCase("cat", files);
        check(outcome.has("cat" + Q1A_PATH), "double wrapper flattened: "
                + outcome.after);
        check(outcome.undoneEqual, "undo restores the double wrapper");
        // 4. Folder in the wrong case
        files = all(DAN, GOOD_HEADER);
        rename(files, DAN + "/Q1/", DAN + "/q1/");
        outcome = fixCase(DAN, files);
        check(outcome.has(DAN + Q1A_PATH)
                && outcome.has(DAN + "/Q1/Q1b.java"),
                "q1 renamed to Q1: " + outcome.after);
        check(outcome.undoneEqual, "undo restores q1");
    }

    // Cases 5 to 8: the answer files have the wrong names or places.
    private static void checkFileFixes() throws Exception {
        // 5. File in the wrong case
        Map<String, byte[]> files = all("eve", GOOD_HEADER);
        rename(files, "eve/Q2/Q2a.java", "eve/Q2/q2A.java");
        Outcome outcome = fixCase("eve", files);
        check(outcome.has("eve/Q2/Q2a.java")
                && !outcome.has("eve/Q2/q2A.java"),
                "q2A.java renamed to Q2a.java: " + outcome.after);
        check(outcome.undoneEqual, "undo restores q2A.java");
        // 6. Browser copy name
        files = all("fay", GOOD_HEADER);
        rename(files, "fay/Q3/Q3.java", "fay/Q3/Q3 (1).java");
        outcome = fixCase("fay", files);
        check(outcome.has("fay/Q3/Q3.java"),
                "Q3 (1).java renamed: " + outcome.after);
        check(outcome.undoneEqual, "undo restores Q3 (1).java");
        // 7. File in the wrong question folder
        files = all(GUS, GOOD_HEADER);
        rename(files, GUS + Q2B_PATH, GUS + "/Q1/Q2b.java");
        outcome = fixCase(GUS, files);
        check(outcome.has(GUS + Q2B_PATH)
                && !outcome.has(GUS + "/Q1/Q2b.java"),
                "Q2b.java moved from Q1 to Q2: " + outcome.after);
        check(outcome.undoneEqual, "undo moves Q2b.java back");
        // 8. Answer files with no question folders
        files = new TreeMap<>();
        for (String[] question : questions()) {
            files.put(HAL + SLASH + question[FILE_PART], bytes(GOOD_HEADER
                    + "class " + question[CLASS_PART] + " {}\n"));
        }
        outcome = fixCase(HAL, files);
        check(outcome.has(HAL + Q1A_PATH) && outcome.has(HAL + Q2B_PATH)
                && outcome.has(HAL + "/Q3/Q3.java"),
                "loose files put into question folders: " + outcome.after);
        check(outcome.undoneEqual, "undo puts loose files back");
    }

    // Cases 9 to 11: the header is blank, half filled or has no labels.
    private static void checkHeaderFixes() throws Exception {
        // 9. Blank header filled from the roster
        Map<String, byte[]> files = all(IVY, header("", ""));
        Outcome outcome = fixCase(IVY, files);
        String q1a = outcome.text(IVY + Q1A_PATH);
        check(q1a.contains("Ivy Tan") && q1a.contains("ivy@x.edu"),
                "header filled from roster: " + firstLines(q1a));
        check(outcome.fixed(AnomalyType.MISSING_HEADER_NAME),
                "MISSING_HEADER_NAME marked applied");
        check(outcome.undoneEqual, "undo restores the blank header");
        check(!outcome.afterUndo.keySet().toString().contains("backup"),
                "no backup file left in the working copy after undo");
        // 10. Header with only email missing
        files = all(JAY, header("Jay Ong", ""));
        outcome = fixCase(JAY, files);
        String jayAnswer = outcome.text(JAY + Q1A_PATH);
        check(jayAnswer.contains("jay@x.edu")
                && jayAnswer.contains("Jay Ong"),
                "email filled, name kept");
        check(outcome.undoneEqual, "undo of email fill");
        // 11. Header without labels at all
        files = all(KIM, "public class X {}\n");
        outcome = fixCase(KIM, files);
        System.out.println("   no-label header after fix: "
                + firstLines(outcome.text(KIM + Q1A_PATH)));
        check(outcome.undoneEqual, "undo of header with no labels");
    }

    // Cases 12 to 14: the answer is not saved in plain UTF-8.
    private static void checkEncodingFixes() throws Exception {
        // 12. Windows-1252 source
        Map<String, byte[]> files = all(LEE, GOOD_HEADER);
        String leePath = LEE + Q1A_PATH;
        files.put(leePath, (GOOD_HEADER + "// café\nclass Q1a {}\n")
                .getBytes(WINDOWS_1252));
        Outcome outcome = fixCase(LEE, files);
        check(outcome.text(leePath).contains("café")
                && isUtf8(outcome.afterBytes.get(leePath)),
                "windows-1252 file saved as UTF-8");
        check(outcome.fixed(AnomalyType.WRONG_ENCODING),
                "WRONG_ENCODING applied");
        check(outcome.undoneEqual, "undo restores windows-1252 bytes");
        // 13. UTF-8 with BOM
        files = all(MAY, GOOD_HEADER);
        byte[] plain = bytes(GOOD_HEADER + "class Q1a {}\n");
        byte[] bom = {BOM_FIRST, BOM_SECOND, BOM_THIRD};
        byte[] withBom = new byte[plain.length + bom.length];
        System.arraycopy(bom, 0, withBom, 0, bom.length);
        System.arraycopy(plain, 0, withBom, bom.length, plain.length);
        files.put(MAY + Q1A_PATH, withBom);
        outcome = fixCase(MAY, files);
        check(Arrays.equals(outcome.afterBytes.get(MAY + Q1A_PATH), plain),
                "BOM removed");
        check(outcome.undoneEqual, "undo puts the BOM back");
        // 14. UTF-16
        files = all(NED, GOOD_HEADER);
        byte[] utf16 = (GOOD_HEADER + "class Q1a {}\n").getBytes(
                StandardCharsets.UTF_16);
        files.put(NED + Q1A_PATH, utf16);
        outcome = fixCase(NED, files);
        check(Arrays.equals(outcome.afterBytes.get(NED + Q1A_PATH), plain),
                "UTF-16 saved as UTF-8");
        check(outcome.undoneEqual, "undo puts UTF-16 back");
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
        Outcome outcome = fixCase(OLI, files);
        String oliPath = OLI + Q1A_PATH;
        check(outcome.has(oliPath) && outcome.has(OLI + Q2B_PATH),
                "combined: layout fixed: " + outcome.after);
        check(outcome.text(oliPath).contains("Oli Wu")
                && outcome.text(oliPath).contains("naïve"),
                "combined: header and encoding fixed");
        check(outcome.undoneEqual, "combined: undo restores everything");
        check(outcome.openAfter == 0,
                "combined: no open structure problems left ("
                + outcome.openAfter + ": " + outcome.openMessages
                + CLOSE_BRACKET);
        // 16. Student not on the roster: header left alone
        files = all(PAT, header("", ""));
        outcome = fixCaseWith(PAT, files, false);
        check(Arrays.equals(outcome.afterBytes.get(PAT + Q1A_PATH),
                files.get(PAT + Q1A_PATH)),
                "header not changed for a student not on the roster");
        check(outcome.failureReasons.toString()
                .contains("not on the roster"),
                "failure reason names the roster");
        // 17. Two candidate files in a case clash
        files = all(QUY, GOOD_HEADER);
        files.put(QUY + "/Q1/q1a.java", bytes("class q1a {}"));
        files.remove(QUY + Q1A_PATH);
        files.put(QUY + "/Q1/Q1A.JAVA", bytes("class Q1A {}"));
        outcome = fixCaseLenient(QUY, files);
        System.out.println("   two case-variant candidates for Q1a.java: "
                + outcome.after + " reasons=" + outcome.failureReasons);
        check(outcome.undoneEqual, "undo with two case variants");
    }

    // Checks undo, approve and rejected fix keys on FixManager.
    private static void checkRejectedKeys() throws Exception {
        Map<String, byte[]> files = all(TEMPLATE_FOLDER,
                header("Ray Koh", "ray@x.edu"));
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
        // Approve
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
        } catch (gradezzy.fix.FixException expected) {
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
        } catch (gradezzy.fix.FixException expected) {
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

    // What one fix case left behind: the files after the fix and after
    // the undo, and what the fixers and validators said.
    private static class Outcome {
        // Every file and folder after the fix, by path
        private Map<String, byte[]> afterBytes = new TreeMap<>();

        // Every file and folder after every fix was undone
        private Map<String, byte[]> afterUndo = new TreeMap<>();

        // The paths after the fix, as one line of text
        private String after = "";

        // Whether the undo gave back exactly what the student sent
        private boolean undoneEqual = false;

        // The problems the checks found
        private List<Anomaly> anomalies = new ArrayList<>();

        // The kinds of problem whose fix was applied
        private List<AnomalyType> appliedTypes = new ArrayList<>();

        // Why each fix that could not be made was not made, by kind
        private Map<String, String> failureReasons = new TreeMap<>();

        // How many problems the checks still found after the fix
        private int openAfter = 0;

        // The problems the checks still found after the fix
        private List<String> openMessages = new ArrayList<>();

        // Says whether a file or folder is there after the fix.
        private boolean has(String path) {
            for (String key : afterBytes.keySet()) {
                if (key.equals(path) || key.startsWith(path + SLASH)) {
                    return true;
                }
            }
            return false;
        }

        // Reads a file after the fix as UTF-8 text.
        private String text(String path) {
            byte[] data = afterBytes.get(path);
            if (data == null) {
                return "<missing>";
            }
            return new String(data, StandardCharsets.UTF_8);
        }

        // Says whether a fix of this kind was applied.
        private boolean fixed(AnomalyType type) {
            return appliedTypes.contains(type);
        }
    }

    // Runs one case for a student on the roster.
    private static Outcome fixCase(String user, Map<String, byte[]> files)
            throws Exception {
        return fixCaseWith(user, files, true);
    }

    // Runs one case where the fixers may not agree on a fix.
    private static Outcome fixCaseLenient(String user,
            Map<String, byte[]> files) throws Exception {
        return fixCaseWith(user, files, true);
    }

    // Builds the working copy, fixes it, checks it again, undoes every
    // fix and says what happened at each step.
    private static Outcome fixCaseWith(String user,
            Map<String, byte[]> files, boolean onRoster) throws Exception {
        Submission submission = build(user, files, onRoster);
        Map<String, byte[]> before = snapshot(
                submission.getWorkingCopyPath());
        FixManager manager = manager();
        runStages(submission, manager);
        Outcome outcome = new Outcome();
        outcome.anomalies = new ArrayList<>(submission.getAnomalies());
        for (Anomaly anomaly : outcome.anomalies) {
            if (anomaly.getFixStatus() == FixStatus.APPLIED) {
                outcome.appliedTypes.add(anomaly.getType());
            }
        }
        outcome.afterBytes = snapshot(submission.getWorkingCopyPath());
        outcome.after = outcome.afterBytes.keySet().toString();
        for (Anomaly anomaly : submission.getAnomalies()) {
            String reason = manager.getFailureReason(anomaly);
            if (reason != null) {
                outcome.failureReasons.put(anomaly.getType().toString(),
                        reason);
            }
        }
        // Re-validate to count open structure problems
        Submission recheck = new Submission(user, SECTION,
                submission.getOriginalPath());
        recheck.setWorkingCopyPath(submission.getWorkingCopyPath());
        recheck.setStudent(submission.getStudent());
        for (Anomaly anomaly : validators().runAll(recheck, assessment)) {
            outcome.openAfter++;
            outcome.openMessages.add(anomaly.getType() + ":"
                    + anomaly.getMessage());
        }
        List<FixRecord> records = new ArrayList<>(manager.getFixRecords());
        Collections.reverse(records);
        try {
            manager.undoAll(records);
        } catch (gradezzy.fix.FixException exception) {
            System.out.println("   undo failed for " + user + ": "
                    + exception.getMessage());
        }
        outcome.afterUndo = snapshot(submission.getWorkingCopyPath());
        outcome.undoneEqual = sameTree(before, outcome.afterUndo);
        if (!outcome.undoneEqual) {
            System.out.println("   " + user + " before=" + before.keySet()
                    + " afterUndo=" + outcome.afterUndo.keySet());
        }
        return outcome;
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
        for (Map.Entry<String, byte[]> entry : files.entrySet()) {
            Path file = copy.resolve(entry.getKey());
            Files.createDirectories(file.getParent());
            Files.write(file, entry.getValue());
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
        for (Map.Entry<String, byte[]> entry : copy.entrySet()) {
            String key = entry.getKey();
            if (key.startsWith(from)) {
                key = to + key.substring(from.length());
            }
            files.put(key, entry.getValue());
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
                    java.nio.ByteBuffer.wrap(data));
            return true;
        } catch (java.nio.charset.CharacterCodingException exception) {
            return false;
        }
    }

    // Gives the first few lines of a file on one line.
    private static String firstLines(String text) {
        String[] lines = text.split("\n");
        StringBuilder shown = new StringBuilder();
        for (int i = 0; i < Math.min(SHOWN_LINES, lines.length); i++) {
            shown.append(lines[i]).append(" | ");
        }
        return shown.toString();
    }

    // Reads every file and folder below a folder, by relative path;
    // folders end in a slash and have no bytes.
    private static Map<String, byte[]> snapshot(Path folder)
            throws Exception {
        Map<String, byte[]> files = new TreeMap<>();
        if (!Files.exists(folder)) {
            return files;
        }
        try (Stream<Path> walk = Files.walk(folder)) {
            for (Path path : (Iterable<Path>) walk::iterator) {
                String key = folder.relativize(path).toString();
                if (Files.isRegularFile(path)) {
                    files.put(key, Files.readAllBytes(path));
                } else if (!key.isEmpty()) {
                    files.put(key + SLASH, new byte[0]);
                }
            }
        }
        return files;
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
