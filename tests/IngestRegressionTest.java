/*
 * File: IngestRegressionTest.java
 *
 * Purpose: Regression checks for ingestion: nested and bulk zips,
 * duplicate uploads, unsafe entries, rosters, the Set up batch, the
 * folder chosen for grading and the folder fixes.
 *
 * Where it fits: Run by hand after compiling, as tests/README.md says.
 * It grades with a pipeline that only unpacks, so no student code runs.
 */
import java.nio.charset.Charset;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.attribute.FileTime;
import java.time.LocalDateTime;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;
import java.util.zip.ZipEntry;
import java.util.zip.ZipOutputStream;

import gradezzy.app.ConsoleProgressListener;
import gradezzy.config.AppConfig;
import gradezzy.config.ConfigKeys;
import gradezzy.ingest.*;
import gradezzy.model.*;
import gradezzy.report.CsvScoreSheetExporter;
import gradezzy.report.RunAuditFilter;
import gradezzy.service.*;
import gradezzy.testcase.*;
import gradezzy.ui.backend.*;
import gradezzy.fix.FixManager;
import gradezzy.fix.FixRecord;
import gradezzy.fix.FolderHierarchyFixer;
import gradezzy.fix.FolderNameFixer;
import gradezzy.util.AnswerFolderRule;
import gradezzy.util.FileUtils;
import gradezzy.validation.FileNameValidator;
import gradezzy.validation.HeaderNameValidator;
import gradezzy.validation.RequiredFilesValidator;
import gradezzy.validation.StructureValidator;

/**
 * Runs the ingestion regression checks and prints how many passed.
 * Compile: javac -cp classes -d /tmp/gradezzy-tests tests/*.java; run:
 * java -cp classes:/tmp/gradezzy-tests IngestRegressionTest
 */
public class IngestRegressionTest {

    // The section every upload is graded for
    private static final Section SECTION = new Section("CS102", "G1");

    // How much newer one of two same-named zips is made, in milliseconds
    private static final long ONE_MINUTE_MILLIS = 60000;

    // The time stored on the older of two same-named zips in a bulk zip
    private static final long OLDER_ENTRY_TIME = 1700000000000L;

    // The time stored on the newer of two same-named zips in a bulk zip
    private static final long NEWER_ENTRY_TIME = 1700090000000L;

    // The name encoding a zip made by Windows Explorer uses
    private static final String EXPLORER_CHARSET = "CP437";

    // The encoding Excel on Windows saves a CSV roster in
    private static final String WINDOWS_ROSTER_CHARSET = "windows-1252";

    // The name encoding ZipOutputStream uses when none is given
    private static final Charset ZIP_NAME_CHARSET = StandardCharsets.UTF_8;

    // The ending of a Java source file name
    private static final String JAVA_SUFFIX = ".java";

    // The content of a zip entry that holds nothing
    private static final byte[] NO_BYTES = new byte[0];

    // The number of checks passed so far
    private static int checks = 0;

    /**
     * Runs every check in a temporary folder, then removes the folder.
     *
     * @param args not used
     * @throws Exception if a check fails or a file cannot be written
     */
    public static void main(String[] args) throws Exception {
        Path root = Files.createTempDirectory("gradezzy-ingest-tests-")
                .toRealPath();
        try {
            AppConfig config = TestConfig.copyInto(root);
            config.setString(ConfigKeys.WORKING_DIR,
                    root.resolve("working").toString());
            Path roster = root.resolve("roster.csv");
            Files.writeString(roster, "OrgDefinedId,Username,Last Name,"
                    + "First Name,Email\r\n"
                    + "#1,#ALIce,Example,Alice,alice@example.com\r\n");
            config.setString(ConfigKeys.SCORE_SHEET_FILE, roster.toString());
            // A score sheet chosen for a section in the instructor's own
            // settings would win over this roster, so the checks set none
            config.setString(ConfigKeys.SCORE_SHEET_BY_SECTION, "");
            SubmissionLoader loader = new SubmissionLoader(config);

            Path alice = checkNestedZips(root, loader);
            Path upperAlice = checkSameStudentUploads(root, loader);
            checkNamelessUploads(root, loader);
            checkDeepStudentZip(root, loader);
            checkExportCase(root, config, upperAlice);
            checkExportQuestionColumns(root, config);
            checkRunAuditFilter();
            Path duplicate = checkMixedBatch(root, config, roster, alice);

            ZipExtractor extractor = new ZipExtractor(config);
            checkUnsafeEntries(root, extractor);
            checkOddNames(root, extractor);
            checkCaseClash(root, extractor);
            checkExplorerZip(root, extractor);
            checkRosters(root, config, roster);
            checkScoreSheetProblems(root, config);
            checkImportBatch(root, roster, alice, duplicate);
            checkAnswerFolders(root.resolve("answer-folders"), config);
            checkInputFixes(root.resolve("input-fixes"), config, loader);
            System.out.println("Passed " + checks
                    + " ingestion regression checks.");
        } finally {
            FileUtils.deleteDirectory(root);
        }
    }

    // Checks a student zip holding one zip and the same path chosen
    // twice, then the zips of zips; returns alice's zip.
    private static Path checkNestedZips(Path root, SubmissionLoader loader)
            throws Exception {
        Path bob = zip(root.resolve("bob.zip"), "Q1/Answer.java", "bob");
        Path alice = zipOfFiles(root.resolve("2023-2024-alice.zip"),
                Arrays.asList("bob.zip"), Arrays.asList(bob));
        List<Submission> nested = loader.loadSubmissions(
                Arrays.asList(alice), SECTION);
        boolean outerKept = nested.size() == 1
                && nested.get(0).getUsername().equals("alice");
        check(outerKept,
                "a nested student ZIP never replaces the outer identity");
        check(nested.get(0).getOriginalPath().equals(alice),
                "outer original is retained");
        List<Submission> repeated = loader.loadSubmissions(
                Arrays.asList(alice, alice), SECTION);
        boolean gradedOnce = repeated.size() == 1
                && duplicateWarnings(repeated.get(0)) == 1;
        check(gradedOnce, "selecting the same path twice grades it once and "
                + "reports the duplicate");
        checkZipsOfZips(root, loader, alice, bob);
        return alice;
    }

    // Checks a bulk zip of student zips and a student zip holding
    // question zips.
    private static void checkZipsOfZips(Path root, SubmissionLoader loader,
            Path alice, Path bob) throws Exception {
        // The bulk zip holds alice's zip and bob's zip
        int studentsInBatch = 2;
        Path batch = zipOfFiles(root.resolve("batch.zip"),
                Arrays.asList("2023-2024-alice.zip", "bob.zip"),
                Arrays.asList(alice, bob));
        int loaded = loader.loadSubmissions(Arrays.asList(batch), SECTION)
                .size();
        check(loaded == studentsInBatch, "a zip of zips with no question "
                + "folder is one student per zip inside");
        check(loader.loadBatchArchive(batch, SECTION).size()
                == studentsInBatch, "explicit bulk loading still works");
        Path questionZips = namesAsContentZip(
                root.resolve("2023-2024-eve.zip"),
                Arrays.asList("Q1.zip", "Q2.zip", "notes.txt"));
        boolean oneStudent = !loader.isBatchArchive(questionZips)
                && loader.loadSubmissions(Arrays.asList(questionZips),
                SECTION).size() == 1;
        check(oneStudent,
                "a student zip holding question zips stays one student");
    }

    // Checks which of two uploads for one student is kept when the names
    // differ in case or the same name sits in two folders; returns the
    // upper-case upload.
    private static Path checkSameStudentUploads(Path root,
            SubmissionLoader loader) throws Exception {
        Path upperFolder = Files.createDirectories(root.resolve("upper"));
        Path upperAlice = zip(upperFolder.resolve("2023-2024-ALICE.zip"),
                "Q1/Answer.java", "upper");
        Path lowerFolder = Files.createDirectories(root.resolve("lower"));
        Path lowerAlice = zip(lowerFolder.resolve("2023-2024-alice.zip"),
                "Q1/Answer.java", "lower");
        List<Submission> caseDuplicates = loader.loadSubmissions(
                Arrays.asList(upperAlice, lowerAlice), SECTION);
        boolean lowerKept = caseDuplicates.size() == 1
                && caseDuplicates.get(0).getOriginalPath().equals(lowerAlice);
        check(lowerKept, "a case-variant duplicate keeps the upload whose "
                + "username is lower case");
        Path otherFolder = Files.createDirectories(root.resolve("other"));
        Path otherAlice = zip(otherFolder.resolve("2023-2024-alice.zip"),
                "Q1/Answer.java", "other");
        // Both zips are written within the same moment, so the newer
        // one is made a minute newer on purpose
        long lowerTime = Files.getLastModifiedTime(lowerAlice)
                .toMillis();
        long otherTime = lowerTime + ONE_MINUTE_MILLIS;
        Files.setLastModifiedTime(otherAlice,
                FileTime.fromMillis(otherTime));
        List<Submission> sameNames = loader.loadSubmissions(
                Arrays.asList(otherAlice, lowerAlice), SECTION);
        boolean newerKept = sameNames.size() == 1
                && sameNames.get(0).getOriginalPath().equals(otherAlice)
                && sameNames.get(0).getAnomalies().get(0).getMessage()
                .startsWith("Two files are named");
        check(newerKept, "of two same-named files in two folders the newer "
                + "is kept, and they are told apart by folder");
        return upperAlice;
    }

    // Checks that uploads without a username give one rejected upload.
    private static void checkNamelessUploads(Path root,
            SubmissionLoader loader) throws Exception {
        // erin's upload and the one rejected upload without a username
        int expectedSubmissions = 2;
        // The flags the rejected upload carries
        int rejectedFlags = 2;
        Path namelessDir = Files.createDirectories(
                root.resolve("nameless"));
        zip(namelessDir.resolve("2023-2024-.zip"), "Q1/Answer.java",
                "first");
        zip(namelessDir.resolve("2023-2024- (1).zip"), "Q1/Answer.java",
                "second");
        zip(namelessDir.resolve("2023-2024-erin.zip"), "Q1/Answer.java",
                "erin");
        List<Submission> withNameless = loader.loadSubmissions(
                Arrays.asList(namelessDir), SECTION);
        Submission keptNameless = findNameless(withNameless);
        boolean oneRejected = withNameless.size() == expectedSubmissions
                && keptNameless.getStatus() == SubmissionStatus.FAILED
                && keptNameless.getAnomalies().size() == rejectedFlags;
        check(oneRejected, "uploads without a username give one rejected "
                + "submission naming the others");
    }

    // Checks that question folders two levels down mean a student zip.
    private static void checkDeepStudentZip(Path root,
            SubmissionLoader loader) throws Exception {
        Path deep = namesAsContentZip(root.resolve("carol.zip"),
                Arrays.asList("carol/carol/Q1/Answer.java",
                "carol/carol/Q3/notes.zip"));
        List<Submission> deepLoaded = loader.loadSubmissions(
                Arrays.asList(deep), SECTION);
        boolean studentZip = !loader.isBatchArchive(deep)
                && loader.listBatchSkipped(deep).isEmpty()
                && deepLoaded.size() == 1
                && deepLoaded.get(0).getUsername().equals("carol");
        check(studentZip, "question folders two levels down mean a student "
                + "zip, not a bulk zip");
    }

    // Checks that the CSV export finds a username in any case.
    private static void checkExportCase(Path root, AppConfig config,
            Path upperAlice) throws Exception {
        Path sheet = root.resolve("sheet.csv");
        Files.writeString(sheet, "OrgDefinedId,Username,"
                + "Calculated Final Grade Numerator\r\n#1,#alice,\r\n");
        GradingRun exported = new GradingRun("RUN-export",
                new AssessmentFactory(config).create(), SECTION,
                LocalDateTime.now());
        Submission upperGraded = new Submission("ALICE", SECTION,
                upperAlice);
        upperGraded.setStatus(SubmissionStatus.GRADED);
        exported.addSubmission(upperGraded);
        exported.setScoreSheetFile(sheet);
        Path csv = root.resolve("export.csv");
        new CsvScoreSheetExporter(config, sheet).export(exported, csv);
        check(Files.readString(csv).contains("#alice,0"),
                "the CSV export finds a username in any case");
    }

    // Checks that the CSV export adds one column per question before the
    // End-of-Line Indicator, blank for a student who was not graded, and
    // fills those columns in place when the template already has them.
    private static void checkExportQuestionColumns(Path root,
            AppConfig config) throws Exception {
        Path sheet = root.resolve("question-sheet.csv");
        Files.writeString(sheet, "OrgDefinedId,Username,Last Name,"
                + "First Name,Email,Calculated Final Grade Numerator,"
                + "Calculated Final Grade Denominator,"
                + "End-of-Line Indicator\r\n"
                + "#1,#bob,B,Bob,bob@example.com,,7,#\r\n"
                + "#2,#carl,C,Carl,carl@example.com,3,7,#\r\n"
                + "#3,#dave,D,Dave,dave@example.com,,7,#\r\n");
        Assessment assessment = new Assessment("Lab Test 2", "CS102");
        int firstMaxScore = 4;
        int secondMaxScore = 3;
        Question first = new Question("Q1a", "Q1", "Q1a.java",
                firstMaxScore);
        Question second = new Question("Q1b", "Q1", "Q1b.java",
                secondMaxScore);
        assessment.addQuestion(first);
        assessment.addQuestion(second);
        GradingRun run = new GradingRun("RUN-columns", assessment, SECTION,
                LocalDateTime.now());
        Submission bob = new Submission("bob", SECTION, sheet);
        bob.setStatus(SubmissionStatus.GRADED);
        QuestionResult bobFirst = new QuestionResult(first);
        bobFirst.setScore(firstMaxScore);
        bob.addQuestionResult(bobFirst);
        QuestionResult bobSecond = new QuestionResult(second);
        bobSecond.setScore(1.5);
        bob.addQuestionResult(bobSecond);
        run.addSubmission(bob);
        Submission carl = new Submission("carl", SECTION, sheet);
        carl.setStatus(SubmissionStatus.FAILED);
        run.addSubmission(carl);
        run.setScoreSheetFile(sheet);
        config.setString(ConfigKeys.CSV_LINE_ENDING, "CRLF");

        // Off, as by default, the sheet keeps the provided columns
        String extraColumns = config.getString(ConfigKeys.CSV_EXTRA_COLUMNS);
        config.setString(ConfigKeys.CSV_EXTRA_COLUMNS, "false");
        Path plain = root.resolve("question-export-plain.csv");
        new CsvScoreSheetExporter(config, sheet).export(run, plain);
        String plainExpected = "OrgDefinedId,Username,Last Name,"
                + "First Name,Email,Calculated Final Grade Numerator,"
                + "Calculated Final Grade Denominator,"
                + "End-of-Line Indicator\r\n"
                + "#1,#bob,B,Bob,bob@example.com,5.5,7,#\r\n"
                + "#2,#carl,C,Carl,carl@example.com,,7,#\r\n"
                + "#3,#dave,D,Dave,dave@example.com,,7,#\r\n";
        String plainExported = Files.readString(plain);
        check(plainExported.equals(plainExpected), "with the extra columns "
                + "off the CSV export keeps the provided format: "
                + plainExported);

        config.setString(ConfigKeys.CSV_EXTRA_COLUMNS, "true");
        Path csv = root.resolve("question-export.csv");
        new CsvScoreSheetExporter(config, sheet).export(run, csv);
        String expected = "OrgDefinedId,Username,Last Name,First Name,"
                + "Email,Calculated Final Grade Numerator,"
                + "Calculated Final Grade Denominator,"
                + "Lab Test 2 Q1a Points Grade,Lab Test 2 Q1b Points Grade,"
                + "Remarks,End-of-Line Indicator\r\n"
                + "#1,#bob,B,Bob,bob@example.com,5.5,7,4,1.5,,#\r\n"
                + "#2,#carl,C,Carl,carl@example.com,,7,,,,#\r\n"
                + "#3,#dave,D,Dave,dave@example.com,,7,,,,#\r\n";
        String exported = Files.readString(csv);
        check(exported.equals(expected), "the CSV export adds a points "
                + "column per question and a Remarks column before the "
                + "End-of-Line Indicator: " + exported);

        // An exported sheet used again as the template is not widened
        run.setScoreSheetFile(csv);
        Path again = root.resolve("question-export-again.csv");
        new CsvScoreSheetExporter(config, csv).export(run, again);
        check(Files.readString(again).equals(expected), "question columns "
                + "the template already has are filled in place");

        // A remark is written, in quotes when it holds a comma
        CsvScoreSheetExporter withRemarks =
                new CsvScoreSheetExporter(config, sheet);
        withRemarks.setRemarks(gradingRun -> remarksOf("bob",
                "Cheated with CARL C (carl)", "carl",
                "Possible cheating with BOB B (bob), DAVE D (dave)"));
        run.setScoreSheetFile(sheet);
        Path remarked = root.resolve("question-export-remarks.csv");
        withRemarks.export(run, remarked);
        String withRemark = Files.readString(remarked);
        check(withRemark.contains("#1,#bob,B,Bob,bob@example.com,5.5,7,4,"
                + "1.5,Cheated with CARL C (carl),#\r\n"), "a student's "
                + "remark goes in the Remarks column: " + withRemark);
        check(withRemark.contains(",\"Possible cheating with BOB B (bob), "
                + "DAVE D (dave)\",#\r\n"), "a remark with a comma is "
                + "quoted: " + withRemark);
        config.setString(ConfigKeys.CSV_EXTRA_COLUMNS, extraColumns);
    }

    // Returns two students' remarks by username.
    private static java.util.Map<String, String> remarksOf(String first,
            String firstRemark, String second, String secondRemark) {
        java.util.Map<String, String> remarks = new java.util.HashMap<>();
        remarks.put(first, firstRemark);
        remarks.put(second, secondRemark);
        return remarks;
    }

    // Checks which audit log lines belong to a run: by reference, by the
    // run id in the words, or by the section or a student while the run
    // was the newest of its section. Lines about no run are left out.
    private static void checkRunAuditFilter() {
        LocalDateTime start = LocalDateTime.of(2026, 11, 2, 9, 0);
        Section other = new Section("CS102", "G2");
        Assessment assessment = new Assessment("Lab Test 2", "CS102");
        int maxScore = 4;
        assessment.addQuestion(new Question("Q1a", "Q1", "Q1a.java",
                maxScore));
        GradingRun first = new GradingRun("RUN-100", assessment, SECTION,
                start);
        Submission bob = new Submission("bob.2025", SECTION, null);
        bob.addAnomaly(new Anomaly("flag-1", AnomalyType.COMPILE_ERROR,
                Severity.ERROR, "bob.2025", "Q1a did not compile.", "Read "
                + "the output."));
        first.addSubmission(bob);
        GradingRun second = new GradingRun("RUN-200", assessment, other,
                start.plusMinutes(1));
        second.addSubmission(new Submission("carl.2025", other, null));
        GradingRun third = new GradingRun("RUN-300", assessment, SECTION,
                start.plusMinutes(10));
        third.addSubmission(new Submission("bob.2025", SECTION, null));
        List<GradingRun> runs = Arrays.asList(third, second, first);

        AuditEntry byRun = new AuditEntry(start.plusMinutes(20),
                "Question graded again", AuditEntry.withReference(
                "bob.2025 Q1a: 0 to 4.", "RUN-100"));
        AuditEntry byFlag = new AuditEntry(start.plusMinutes(20),
                "Marked as checked", AuditEntry.withReference(
                "bob.2025: Q1a did not compile.", "flag-1"));
        AuditEntry goneFlag = new AuditEntry(start.plusMinutes(2),
                "Marked as checked", AuditEntry.withReference(
                "bob.2025: Q1a did not compile.", "flag-gone"));
        AuditEntry started = new AuditEntry(start.plusSeconds(5),
                "Grading started", "Grading 1 submission(s) for CS102 G1.");
        AuditEntry startedLater = new AuditEntry(start.plusMinutes(11),
                "Grading started", "Grading 1 submission(s) for CS102 G1.");
        AuditEntry fix = new AuditEntry(start.plusMinutes(2),
                "Fix approved", "bob.2025: The top folder was renamed.");
        AuditEntry stopped = new AuditEntry(start.plusMinutes(2),
                "Grading cancelled", "Grading of RUN-200 (CS102 G2) was "
                + "stopped.");
        AuditEntry settings = new AuditEntry(start.plusMinutes(2),
                "Settings saved", "Pass mark: 50 to 40.");
        AuditEntry group = new AuditEntry(start.plusMinutes(2),
                "Group checked", "Q1a Did not compile");
        AuditEntry notStarted = new AuditEntry(start.plusMinutes(2),
                "Grading could not start", "bob.2025 is also in CS102 G1, "
                + "which is being graded now.");
        List<AuditEntry> log = Arrays.asList(byRun, byFlag, goneFlag,
                started, startedLater, fix, stopped, settings, group,
                notStarted);

        List<AuditEntry> firstLines = new RunAuditFilter(first, runs)
                .keep(log);
        check(firstLines.equals(Arrays.asList(byRun, byFlag, started, fix)),
                "a run's audit export keeps its own lines by reference, "
                + "section and student, and nothing about another run");
        List<AuditEntry> secondLines = new RunAuditFilter(second, runs)
                .keep(log);
        check(secondLines.equals(Arrays.asList(stopped)),
                "a line naming a run id belongs to that run only");
        List<AuditEntry> thirdLines = new RunAuditFilter(third, runs)
                .keep(log);
        check(thirdLines.equals(Arrays.asList(startedLater)), "a later run "
                + "of the same section gets the lines written after it "
                + "started");
    }

    // Grades a mixed batch through the service, retries it, then grades
    // with a chosen roster; returns the second upload for alice.
    private static Path checkMixedBatch(Path root, AppConfig config,
            Path roster, Path alice) throws Exception {
        Path duplicate = zip(root.resolve("ALICE.zip"), "Q1/Answer.java",
                "duplicate");
        Path nameless = zip(root.resolve("2023-2024-.zip"),
                "Q1/Answer.java", "nameless");
        Path windows = windowsZip(root.resolve("charlie.zip"));
        PipelineFactory factory = new ExtractOnlyPipelineFactory(config);
        TestCaseSource testers = new NoTestersSource();
        Path damaged = root.resolve("2023-2024-dora.zip");
        Files.writeString(damaged, "not a zip");
        Path damagedCopy = root.resolve("2023-2024-dora (1).zip");
        Files.writeString(damagedCopy, "not a zip either");
        GradingService service = new GradingService(config, factory,
                new AssessmentFactory(config));
        List<Path> uploads = Arrays.asList(alice, duplicate, nameless,
                windows, damagedCopy, damaged);
        GradingRun run = service.startGrading(uploads, SECTION, testers,
                new ConsoleProgressListener());
        Submission accepted = original(run, alice);
        Submission unnamed = original(run, nameless);
        Submission unknown = original(run, windows);
        Submission broken = original(run, damagedCopy);
        checkAcceptedUpload(run, accepted, duplicate, damaged);
        checkDamagedUpload(accepted, broken);
        checkOtherUploads(accepted, unnamed, unknown);
        service.retryFailed(run, new ConsoleProgressListener());
        checkRetry(run, duplicate, broken, unnamed);

        // A file only the first run's copy holds, to see it survive
        Path firstCopy = accepted.getWorkingCopyPath();
        write(firstCopy.resolve("first-run.txt"), "first");
        config.setString(ConfigKeys.SCORE_SHEET_FILE,
                root.resolve("missing.csv").toString());
        GradingRun selected = service.startGrading(Arrays.asList(alice),
                SECTION, testers, new ConsoleProgressListener(), roster);
        check(selected.getSubmissions().get(0).getStudent() != null,
                "selected roster overrides configured roster");
        checkRunFolders(root, config, run, firstCopy,
                selected.getSubmissions().get(0).getWorkingCopyPath());
        return duplicate;
    }

    // Checks that two runs grading one student keep two working copies,
    // each in its run's folder, and that a copy made before runs had
    // their own folders still passes the safety check.
    private static void checkRunFolders(Path root, AppConfig config,
            GradingRun firstRun, Path firstCopy, Path secondCopy)
            throws Exception {
        Path working = root.resolve("working");
        Path expected = working.resolve(firstRun.getId()).resolve("alice");
        check(firstCopy.equals(expected),
                "a working copy sits in its run's folder");
        boolean twoCopies = !firstCopy.equals(secondCopy)
                && Files.exists(secondCopy.resolve("Q1/Answer.java"));
        check(twoCopies, "a second run grading the same student gets its "
                + "own working copy");
        check(Files.exists(firstCopy.resolve("first-run.txt")),
                "a later run leaves the earlier run's copy untouched");

        WorkingCopyManager manager = new WorkingCopyManager(config,
                new ZipExtractor(config));
        Path oldCopy = working.resolve("old-layout-student");
        // Old.java is beside it, so Old.class can be rebuilt and goes
        write(oldCopy.resolve("Q1/Old.java"), "class Old {}");
        write(oldCopy.resolve("Q1/Old.class"), "old");
        manager.removeStudentClassFiles(oldCopy, null);
        check(!Files.exists(oldCopy.resolve("Q1/Old.class")),
                "a copy straight in the working folder is still cleaned");
        Path tooDeep = firstCopy.resolve("Q1");
        boolean refused = false;
        try {
            manager.removeStudentClassFiles(tooDeep, null);
        } catch (java.io.UncheckedIOException exception) {
            refused = true;
        }
        check(refused, "a folder deeper than a run's copy is refused");
    }

    // Checks the valid upload of the mixed batch and that the second
    // files for one student are left out.
    private static void checkAcceptedUpload(GradingRun run,
            Submission accepted, Path duplicate, Path damaged) {
        check(accepted.getStatus() == SubmissionStatus.GRADED,
                "valid upload completes in a mixed batch");
        boolean matched = accepted.getStudent() != null
                && accepted.getStudent().getUsername().equals("ALIce");
        check(matched, "service loads roster and matches without case "
                + "sensitivity");
        check(!hasAnomaly(accepted, AnomalyType.UNKNOWN_STUDENT),
                "matched students are not flagged unknown");
        boolean secondsLeftOut = !hasOriginal(run, duplicate)
                && !hasOriginal(run, damaged);
        check(secondsLeftOut,
                "a second file for one student is not added to the run");
    }

    // Checks the duplicate warnings and the damaged zip of the batch.
    private static void checkDamagedUpload(Submission accepted,
            Submission broken) {
        boolean bothWarned = duplicateWarnings(accepted) == 1
                && duplicateWarnings(broken) == 1;
        check(bothWarned, "the graded file carries a warning naming the file "
                + "left out");
        check(broken.getStatus() == SubmissionStatus.FAILED,
                "the newest copy, (1), is the one graded");
        String damagedMessage = "This zip file is damaged. Ask the student "
                + "for a new copy.";
        boolean plainSentence = hasAnomalyWithMessage(broken,
                AnomalyType.INVALID_ARCHIVE, damagedMessage)
                && !hasAnomaly(broken, AnomalyType.INTERNAL_ERROR);
        check(plainSentence, "a damaged zip is INVALID_ARCHIVE with one "
                + "plain sentence, not INTERNAL_ERROR");
    }

    // Checks the nameless upload, the unpacked zip of one zip and the
    // upload of a student missing from the roster.
    private static void checkOtherUploads(Submission accepted,
            Submission unnamed, Submission unknown) {
        boolean unnamedFlagged = unnamed.getStatus() == SubmissionStatus.FAILED
                && !unnamed.getAnomalies().isEmpty();
        check(unnamedFlagged,
                "nameless upload is flagged without stopping the batch");
        Path acceptedCopy = accepted.getWorkingCopyPath();
        Path acceptedAnswer = acceptedCopy.resolve("Q1/Answer.java");
        Path innerZip = acceptedCopy.resolve("bob.zip");
        boolean oneLevel = Files.exists(acceptedAnswer)
                && !Files.exists(innerZip);
        check(oneLevel, "a zip holding only one zip is unpacked one level, "
                + "keeping the outer identity");
        boolean gradeable = unknown.getStatus() == SubmissionStatus.GRADED
                && unknown.getStudent() == null;
        check(gradeable, "unmatched upload remains gradeable");
        check(countAnomalies(unknown, AnomalyType.UNKNOWN_STUDENT) == 1,
                "unmatched upload raises UNKNOWN_STUDENT");
        Path unknownCopy = unknown.getWorkingCopyPath();
        Path backslashAnswer = unknownCopy.resolve("charlie/Q1/Answer.java");
        Path dotSlashAnswer = unknownCopy.resolve("charlie/Q2/Answer.java");
        boolean realFolders = Files.exists(backslashAnswer)
                && Files.exists(dotSlashAnswer);
        check(realFolders, "Windows backslashes and ./ entries extract into "
                + "real folders");
    }

    // Checks the batch after a retry of its failed uploads.
    private static void checkRetry(GradingRun run, Path duplicate,
            Submission broken, Submission unnamed) {
        check(!hasOriginal(run, duplicate), "retry cannot admit a duplicate");
        boolean warningKept = broken.getStatus() == SubmissionStatus.FAILED
                && duplicateWarnings(broken) == 1;
        check(warningKept, "retry keeps the duplicate warning of the "
                + "retried submission");
        boolean rejectionKept = unnamed.getStatus() == SubmissionStatus.FAILED
                && !unnamed.getAnomalies().isEmpty();
        check(rejectionKept, "retry retains the nameless upload rejection");
    }

    // Checks that entries leaving the target folder are refused.
    private static void checkUnsafeEntries(Path root, ZipExtractor extractor)
            throws Exception {
        List<String> unsafeNames = Arrays.asList("../escape", "..\\escape",
                "./../escape", "/absolute", "\\absolute", "C:\\absolute",
                "\\\\server\\share", "Q1/NUL.java");
        for (String unsafe : unsafeNames) {
            check(!extractor.isSafeEntry(unsafe, root.resolve("safe")),
                    "unsafe path rejected: " + unsafe);
        }
        Path malicious = zip(root.resolve("malicious.zip"),
                "..\\escape.java", "escape");
        try {
            extractor.extract(malicious, root.resolve("unsafe-target"));
            throw new AssertionError("traversal archive accepted");
        } catch (IngestException expected) {
            check(!Files.exists(root.resolve("escape.java")),
                    "traversal writes no escaped file");
        }
    }

    // Checks odd names outside and inside a question folder.
    private static void checkOddNames(Path root, ZipExtractor extractor)
            throws Exception {
        Path icon = emptyEntriesZip(root.resolve("icon.zip"),
                Arrays.asList("w/Icon\r", "__MACOSX/w/what?.txt",
                "w/Q1/._A.java", "w/Q1/A.java"), ZIP_NAME_CHARSET);
        List<String> skippedNames = new ArrayList<>();
        Path iconTarget = extractor.extract(icon,
                root.resolve("icon-target"), skippedNames);
        boolean oddNameSkipped = skippedNames.equals(
                Arrays.asList("w/Icon?"))
                && Files.exists(iconTarget.resolve("w/Q1/A.java"))
                && !Files.exists(iconTarget.resolve("w/Q1/._A.java"));
        check(oddNameSkipped, "ignored names come first and an odd name "
                + "outside the question folders is skipped");
        Path badQuestion = zip(root.resolve("bad-question.zip"),
                "w/Q1/Icon\r", "x");
        try {
            extractor.extract(badQuestion,
                    root.resolve("bad-question-target"));
            throw new AssertionError("odd name in a question folder "
                    + "accepted");
        } catch (InvalidArchiveException expected) {
            checks++;
        }
    }

    // Checks that two entries differing only in case are refused.
    private static void checkCaseClash(Path root, ZipExtractor extractor)
            throws Exception {
        Path clash = emptyEntriesZip(root.resolve("clash.zip"),
                Arrays.asList("w/Q1/A.java", "w/q1/A.java"),
                ZIP_NAME_CHARSET);
        try {
            extractor.extract(clash, root.resolve("clash-target"));
            throw new AssertionError("case clash accepted");
        } catch (InvalidArchiveException expected) {
            check(expected.getMessage().contains("w/q1/A.java"),
                    "a clash names the entry");
        }
    }

    // Checks that a zip whose names Windows Explorer encoded opens.
    private static void checkExplorerZip(Path root, ZipExtractor extractor)
            throws Exception {
        Path explorer = emptyEntriesZip(root.resolve("explorer.zip"),
                Arrays.asList("w/Q1/Jos\u00e9.txt"),
                Charset.forName(EXPLORER_CHARSET));
        Path explorerTarget = extractor.extract(explorer,
                root.resolve("explorer-target"));
        check(Files.exists(explorerTarget.resolve("w/Q1/Jos\u00e9.txt")),
                "a zip from Windows Explorer opens");
    }

    // Checks a Windows-1252 roster and a roster with a case-only
    // duplicate.
    private static void checkRosters(Path root, AppConfig config,
            Path roster) throws Exception {
        Path windowsRoster = root.resolve("windows-roster.csv");
        Files.write(windowsRoster, ("OrgDefinedId,Username,Last Name,"
                + "First Name,Email,End-of-Line Indicator\r\n"
                + "1,jose,Example,Jos\u00e9,jose@example.com\r\n")
                .getBytes(WINDOWS_ROSTER_CHARSET));
        List<Student> windowsStudents = new RosterReader(config)
                .readRoster(windowsRoster);
        boolean windowsLoaded = windowsStudents.size() == 1
                && windowsStudents.get(0).getFirstName().equals("Jos\u00e9");
        check(windowsLoaded, "a Windows-1252 roster with no # and no "
                + "End-of-Line value loads");

        Files.writeString(roster, Files.readString(roster)
                + "#2,#alice,Other,Alice,other@example.com\r\n");
        try {
            new RosterReader(config).readRoster(roster);
            throw new AssertionError("case-only roster duplicate "
                    + "accepted");
        } catch (IngestException expected) {
            checks++;
        }
    }

    // Checks that Set up's score sheet check, which reads the sheet as
    // grading does, refuses a sheet with ; between values with a plain
    // reason that blocks Start grading, accepts a good sheet, and reads
    // the denominator Set up compares with the assessment's total.
    private static void checkScoreSheetProblems(Path root,
            AppConfig config) throws Exception {
        String header = "OrgDefinedId,Username,Last Name,First Name,Email,"
                + "Calculated Final Grade Numerator,"
                + "Calculated Final Grade Denominator,End-of-Line Indicator";
        String row = "#01600101,#aaron.koh.2025,KOH,AARON,"
                + "aaron.koh.2025@smu.edu.sg,,20,#";
        RosterReader reader = new RosterReader(config);

        Path semicolons = root.resolve("semicolons.csv");
        Files.writeString(semicolons, header.replace(',', ';') + "\r\n"
                + row.replace(',', ';') + "\r\n");
        String problem = reader.findProblem(semicolons);
        check(problem.startsWith("No OrgDefinedId column.")
                && problem.contains(";"), "a score sheet with ; between "
                + "values cannot be used, and the reason says so");

        Path noId = root.resolve("no-id.csv");
        Files.writeString(noId, "Username,Last Name,First Name,Email\r\n"
                + "#aaron,KOH,AARON,a@example.com\r\n");
        check(reader.findProblem(noId).equals("No OrgDefinedId column."),
                "a score sheet with no OrgDefinedId column says so");

        Path outOfTwenty = root.resolve("denominator-20.csv");
        Files.writeString(outOfTwenty, header + "\r\n" + row + "\r\n");
        check(reader.findProblem(outOfTwenty).isEmpty(),
                "a good score sheet has no problem");
        check(reader.readDenominator(outOfTwenty) == 20.0,
                "the score sheet's denominator is read for Set up");

        ImportBatch batch = new ImportBatch();
        batch.add(new ImportItem(InputKind.SUBMISSION, root.resolve(
                "aaron.koh.2025.zip"), "aaron.koh.2025",
                ImportState.ACCEPTED, "Ready"));
        batch.add(new ImportItem(InputKind.ROSTER, semicolons, "",
                ImportState.INVALID, problem));
        batch.add(new ImportItem(InputKind.TESTERS, root, "",
                ImportState.ACCEPTED, "Ready"));
        List<String> reasons = batch.getBlockingReasons();
        check(!batch.isReady() && reasons.size() == 1
                && reasons.get(0).contains(problem),
                "a score sheet that cannot be used blocks Start grading "
                + "with its reason");
    }

    // Checks that the Set up batch finds case-only duplicates and still
    // allows grading.
    private static void checkImportBatch(Path root, Path roster,
            Path alice, Path duplicate) {
        // Both of alice's uploads are passed on to be reported per file
        int expectedPaths = 2;
        ImportBatch importBatch = new ImportBatch();
        ImportItem first = new ImportItem(InputKind.SUBMISSION, alice,
                "alice", ImportState.ACCEPTED, "Ready");
        ImportItem second = new ImportItem(InputKind.SUBMISSION,
                duplicate, "ALICE", ImportState.ACCEPTED, "Ready");
        importBatch.add(first);
        importBatch.add(second);
        importBatch.add(new ImportItem(InputKind.ROSTER, roster, "",
                ImportState.ACCEPTED, "Ready"));
        importBatch.add(new ImportItem(InputKind.TESTERS, root, "",
                ImportState.ACCEPTED, "Ready"));
        check(importBatch.getEffectiveState(second) == ImportState.DUPLICATE,
                "UI detects case-only duplicates");
        boolean ready = importBatch.isReady()
                && importBatch.getSubmissionPaths().size() == expectedPaths;
        check(ready, "UI allows grading and passes duplicate to per-file "
                + "reporting");
    }

    // Checks the rule that picks the folder to grade, and the folder
    // fixes for chains of folders and a question folder inside itself.
    private static void checkAnswerFolders(Path root, AppConfig config)
            throws Exception {
        List<String> names = Arrays.asList("Q1", "Q2", "Q3");
        checkFolderToGrade(root, names);
        checkStaleSrc(root, config, names);
        checkBackupFolders(root, names);
        Path nested = root.resolve("nested");
        write(nested.resolve("Q1/Q1/Q1a.java"), "class Q1a {}");
        write(nested.resolve("Q1/Shape.class"), "x");
        check(AnswerFolderRule.findNestedQuestionFolder(
                nested.resolve("Q1"), "Q1").equals(
                nested.resolve("Q1/Q1")),
                "a question folder inside itself is found");

        Assessment assessment = new AssessmentFactory(config).create();
        checkFolderChain(root, config, assessment);
        checkFolderInsideItself(root, config, assessment);
    }

    // Checks which folder is graded when there are two answers, junk,
    // IDE output or a folder inside one of the same name.
    private static void checkFolderToGrade(Path root, List<String> names)
            throws Exception {
        // The answers in u and in u (2)
        int answerFolders = 2;
        Path two = root.resolve("two");
        write(two.resolve("u/Q1/Q1a.java"), "class Q1a {}");
        write(two.resolve("u (2)/Q1/Q1a.java"), "class Q1a {}");
        Path twoRoot = AnswerFolderRule.findContentRoot(two, "u", names);
        int twoHolders = AnswerFolderRule.findAnswerHolders(two, names)
                .size();
        boolean noFolder = twoRoot == null && twoHolders == answerFolders;
        check(noFolder, "two answer folders give no folder to grade");
        Path junk = root.resolve("junk");
        write(junk.resolve("Q1/notes.txt"), "notes");
        write(junk.resolve("u/Q1/Q1a.java"), "class Q1a {}");
        check(AnswerFolderRule.findContentRoot(junk, "u", names)
                .equals(junk.resolve("u")),
                "a top-level question folder without Java does not count");
        Path ide = root.resolve("ide");
        write(ide.resolve("u/src/Q1/Q1a.java"), "class Q1a {}");
        write(ide.resolve("u/out/production/u/Q1/Q1a.class"), "x");
        check(AnswerFolderRule.findExtraFolder(ide.resolve("u"), names)
                .equals(ide.resolve("u/src")),
                "IDE output beside src is not a second answer");
        check(AnswerFolderRule.findContentRoot(ide, "u", names)
                .equals(ide.resolve("u")),
                "an IntelliJ project is graded from its top folder");
        Path twice = root.resolve("twice");
        write(twice.resolve("u/u/Q1/Q1a.java"), "class Q1a {}");
        check(AnswerFolderRule.findContentRoot(twice, "u", names)
                .equals(twice.resolve("u")),
                "a folder inside one of the same name is one answer");
    }

    // Checks question folders in a top folder and again in its src.
    private static void checkStaleSrc(Path root, AppConfig config,
            List<String> names) throws Exception {
        Path stale = root.resolve("stale");
        write(stale.resolve("alice/Q1/Q1a.java"), "class Q1a {}");
        for (String folder : names) {
            write(stale.resolve("alice/src/" + folder + "/A.java"),
                    "class A {}");
        }
        List<Path> staleHolders = Arrays.asList(stale.resolve("alice"),
                stale.resolve("alice/src"));
        Path staleRoot = AnswerFolderRule.findContentRoot(stale, "alice",
                names);
        boolean twoAnswers = staleRoot == null
                && AnswerFolderRule.findAnswerHolders(stale, names).equals(
                staleHolders);
        check(twoAnswers, "question folders in a top folder and in its src "
                + "are two answers");
        Submission staleUpload = new Submission("alice", SECTION, stale);
        staleUpload.setWorkingCopyPath(stale);
        List<Anomaly> staleFound = new StructureValidator().validate(
                staleUpload, new AssessmentFactory(config).create());
        boolean bothNamed = staleFound.size() == 1
                && staleFound.get(0).getMessage().contains(
                "2 places (alice and alice/src)");
        check(bothNamed, "one error names both answer places");
    }

    // Checks a backup of question folders beside them.
    private static void checkBackupFolders(Path root, List<String> names)
            throws Exception {
        Path backup = root.resolve("backup");
        for (String folder : names) {
            write(backup.resolve("alice/" + folder + "/A.java"),
                    "class A {}");
        }
        write(backup.resolve("alice/backup/Q1/A.java"), "class A {}");
        write(backup.resolve("alice/backup/Q3/A.java"), "class A {}");
        List<Path> backupHolders = Arrays.asList(backup.resolve("alice"),
                backup.resolve("alice/backup"));
        Path backupRoot = AnswerFolderRule.findContentRoot(backup, "alice",
                names);
        boolean secondAnswer = backupRoot == null
                && AnswerFolderRule.findAnswerHolders(backup, names).equals(
                backupHolders);
        check(secondAnswer, "a backup of question folders beside them is a "
                + "second answer");
    }

    // Checks the fixes for question folders three levels down, and undo.
    private static void checkFolderChain(Path root, AppConfig config,
            Assessment assessment) throws Exception {
        // One fix moves the folders up and one renames the top folder
        int expectedFixes = 2;
        Path chain = root.resolve("chain");
        List<String> files = Arrays.asList("Q1/Q1a.java", "Q1/Q1b.java",
                "Q2/Q2a.java", "Q2/Q2b.java", "Q3/Q3.java");
        for (String file : files) {
            write(chain.resolve("Users/jy/Desktop/u/" + file), "class X {}");
        }
        Submission deep = new Submission("u", SECTION, chain);
        deep.setWorkingCopyPath(chain);
        addStructureProblems(deep, assessment);
        FixManager fixes = new FixManager();
        fixes.addFixer(new FolderHierarchyFixer(config));
        fixes.addFixer(new FolderNameFixer(config));
        List<FixRecord> applied = fixes.applyAll(deep);
        boolean movedUp = applied.size() == expectedFixes
                && Files.exists(chain.resolve("u/Q3/Q3.java"))
                && !Files.exists(chain.resolve("Users"));
        check(movedUp, "question folders three levels down are moved up and "
                + "the top folder renamed");
        fixes.undo(applied.get(1));
        fixes.undo(applied.get(0));
        Path originalPlace = chain.resolve("Users/jy/Desktop/u/Q3/Q3.java");
        boolean putBack = Files.exists(originalPlace)
                && !Files.exists(chain.resolve("u"));
        check(putBack, "undo puts the chain of folders back");
    }

    // Checks the fix for a question folder inside itself, and undo.
    private static void checkFolderInsideItself(Path root, AppConfig config,
            Assessment assessment) throws Exception {
        Path inside = root.resolve("inside");
        write(inside.resolve("u/Q1/Q1/Q1a.java"), "class Q1a {}");
        write(inside.resolve("u/Q1/Q1/Q1b.java"), "class Q1b {}");
        write(inside.resolve("u/Q2/Q2a.java"), "class Q2a {}");
        write(inside.resolve("u/Q2/Q2b.java"), "class Q2b {}");
        write(inside.resolve("u/Q3/Q3.java"), "class Q3 {}");
        Submission dragged = new Submission("u", SECTION, inside);
        dragged.setWorkingCopyPath(inside);
        addStructureProblems(dragged, assessment);
        check(new RequiredFilesValidator().validate(dragged, assessment)
                .isEmpty(), "Q1/Q1 is not also reported as missing files");
        FixManager insideFixes = new FixManager();
        insideFixes.addFixer(new FolderHierarchyFixer(config));
        List<FixRecord> moved = insideFixes.applyAll(dragged);
        boolean movedUp = moved.size() == 1
                && Files.exists(inside.resolve("u/Q1/Q1a.java"));
        check(movedUp, "a question folder inside itself is moved up");
        insideFixes.undo(moved.get(0));
        check(Files.exists(inside.resolve("u/Q1/Q1/Q1b.java")),
                "undo puts the question folder back inside itself");
    }

    // Checks the fixes for names with spaces at the end, Excel rows,
    // spaces around roster values, prefilled marks of ungraded students,
    // .zip.zip, bulk zip times, copy-numbered files and header labels.
    private static void checkInputFixes(Path root, AppConfig config,
            SubmissionLoader loader) throws Exception {
        Files.createDirectories(root);
        ZipExtractor extractor = new ZipExtractor(config);
        checkTrimmedNames(root, extractor);
        checkExcelRoster(root, config);
        checkZipNames(root, loader);
        checkCopyNumberedNames(root, config);
    }

    // Checks that a space or dot at the end of a name is dropped, and that
    // a clash made by dropping it is refused.
    private static void checkTrimmedNames(Path root, ZipExtractor extractor)
            throws Exception {
        Path spaces = emptyEntriesZip(root.resolve("spaces.zip"),
                Arrays.asList("w /Q1 /A.java", "w /Q2/B.java."),
                ZIP_NAME_CHARSET);
        List<String> renamed = new ArrayList<>();
        List<String> skipped = new ArrayList<>();
        Path spacesTarget = extractor.extract(spaces,
                root.resolve("spaces-target"), skipped, renamed);
        boolean trimmed = Files.exists(spacesTarget.resolve("w/Q1/A.java"))
                && Files.exists(spacesTarget.resolve("w/Q2/B.java"))
                && renamed.contains("\"w \" as \"w\"");
        check(trimmed,
                "a space or dot at the end of a name is dropped and named");
        Path trimClash = emptyEntriesZip(root.resolve("trim-clash.zip"),
                Arrays.asList("w/Q1/A.java", "w/Q1 /A.java"),
                ZIP_NAME_CHARSET);
        try {
            extractor.extract(trimClash, root.resolve("trim-clash-target"));
            throw new AssertionError("a clash after trimming accepted");
        } catch (InvalidArchiveException expected) {
            checks++;
        }
    }

    // Checks empty Excel rows, trimmed roster values and the marks the
    // export leaves blank.
    private static void checkExcelRoster(Path root, AppConfig config)
            throws Exception {
        // The rows of bob, carl and dave
        int filledRows = 3;
        Path sheet = root.resolve("excel.csv");
        Files.writeString(sheet, "OrgDefinedId,Username,Last Name,"
                + "First Name,Email,Calculated Final Grade Numerator\n"
                + "#1,#bob ,B,Bob,bob@example.com,\n"
                + "#2,# carl,C,Carl,carl@example.com,12.5\n"
                + "#3,#dave,D,Dave,dave@example.com,9\n"
                + ",,,,,\n,,,,,\n");
        List<Student> students = new RosterReader(config).readRoster(sheet);
        boolean rowsRead = students.size() == filledRows
                && students.get(0).getUsername().equals("bob")
                && students.get(1).getUsername().equals("carl");
        check(rowsRead,
                "empty Excel rows are skipped and roster values trimmed");
        GradingRun run = new GradingRun("RUN-trim",
                new AssessmentFactory(config).create(), SECTION,
                LocalDateTime.now());
        Submission bob = new Submission("bob", SECTION, sheet);
        bob.setStatus(SubmissionStatus.GRADED);
        run.addSubmission(bob);
        Submission carl = new Submission("carl", SECTION, sheet);
        carl.setStatus(SubmissionStatus.FAILED);
        run.addSubmission(carl);
        run.setScoreSheetFile(sheet);
        Path csv = root.resolve("trim-export.csv");
        new CsvScoreSheetExporter(config, sheet).export(run, csv);
        String exported = Files.readString(csv);
        boolean marksBlanked = exported.contains(
                "#bob ,B,Bob,bob@example.com,0")
                && exported.contains("# carl,C,Carl,carl@example.com,")
                && !exported.contains("12.5")
                && exported.contains("#dave,D,Dave,dave@example.com,")
                && !exported.contains("dave@example.com,9");
        check(marksBlanked, "export matches a trimmed username and blanks a "
                + "mark already in the sheet for a failed or missing "
                + "student");
    }

    // Checks a name ending .zip.zip and two same-named zips in a bulk zip.
    private static void checkZipNames(Path root, SubmissionLoader loader)
            throws Exception {
        check(loader.extractUsername("2023-2024-dan.zip.zip").equals("dan"),
                "a name ending .zip.zip gives the username");

        Path older = zip(root.resolve("older.zip"), "dan/Q1/Q1a.java",
                "old");
        Path newer = zip(root.resolve("newer.zip"), "dan/Q1/Q1a.java",
                "new");
        ZipEntry first = new ZipEntry("a/2023-2024-dan.zip");
        first.setTime(OLDER_ENTRY_TIME);
        ZipEntry second = new ZipEntry("b/2023-2024-dan.zip");
        second.setTime(NEWER_ENTRY_TIME);
        List<byte[]> contents = new ArrayList<>();
        contents.add(Files.readAllBytes(older));
        contents.add(Files.readAllBytes(newer));
        Path bulk = writeZip(root.resolve("bulk download.zip"),
                Arrays.asList(first, second), contents, ZIP_NAME_CHARSET);
        List<Submission> fromBulk = loader.loadSubmissions(
                Arrays.asList(bulk), SECTION);
        Submission dan = fromBulk.get(0);
        String danFolder = dan.getOriginalPath().getParent().getFileName()
                .toString();
        boolean newerGraded = fromBulk.size() == 1
                && danFolder.equals("b")
                && dan.getAnomalies().get(0).getMessage().contains(
                "in the folder a of the bulk zip");
        check(newerGraded, "the newer of two same-named zips in a bulk zip "
                + "is graded and the message names both folders");
    }

    // Checks a browser copy name such as Q1a (1).java and a header label
    // with a space before the colon.
    private static void checkCopyNumberedNames(Path root, AppConfig config)
            throws Exception {
        Assessment assessment = new AssessmentFactory(config).create();
        Path copied = root.resolve("copied");
        write(copied.resolve("u/Q1/Q1a (1).java"), "class Q1a {}");
        write(copied.resolve("u/Q1/Q1b.java"), "class Q1b {}");
        Submission browser = new Submission("u", SECTION, copied);
        browser.setWorkingCopyPath(copied);
        List<Anomaly> names = new FileNameValidator().validate(browser,
                assessment);
        boolean wrongName = names.size() == 1
                && names.get(0).getMessage().equals(
                "Q1/Q1a (1).java should be named Q1/Q1a.java.")
                && !hasMessageStarting(new RequiredFilesValidator()
                .validate(browser, assessment), "Q1/Q1a.java");
        check(wrongName,
                "a browser copy name is a wrong name, not a missing file");
        int nameValueStart = " * Name :".length();
        int emailValueStart = " * Email ID :".length();
        boolean labelsRead = HeaderNameValidator.findValueStart(
                " * Name : Ann", "Name:") == nameValueStart
                && HeaderNameValidator.findValueStart(" * Email ID :",
                "Email ID:") == emailValueStart;
        check(labelsRead,
                "a header label with a space before the colon is read");
    }

    // Writes a text file, making its folders first.
    private static void write(Path file, String text) throws Exception {
        Files.createDirectories(file.getParent());
        Files.writeString(file, text);
    }

    // Writes a zip holding the given entries, each with the bytes at the
    // same position, with names in the given encoding.
    private static Path writeZip(Path path, List<ZipEntry> entries,
            List<byte[]> contents, Charset charset) throws Exception {
        ZipOutputStream out = null;
        try {
            out = new ZipOutputStream(Files.newOutputStream(path), charset);
            for (int i = 0; i < entries.size(); i++) {
                out.putNextEntry(entries.get(i));
                out.write(contents.get(i));
                out.closeEntry();
            }
        } finally {
            if (out != null) {
                out.close();
            }
        }
        return path;
    }

    // Makes one zip entry for each name.
    private static List<ZipEntry> entriesFor(List<String> names) {
        List<ZipEntry> entries = new ArrayList<>();
        for (String name : names) {
            entries.add(new ZipEntry(name));
        }
        return entries;
    }

    // Writes a zip holding one empty entry for each name.
    private static Path emptyEntriesZip(Path path, List<String> names,
            Charset charset) throws Exception {
        List<byte[]> contents = new ArrayList<>();
        for (int i = 0; i < names.size(); i++) {
            contents.add(NO_BYTES);
        }
        return writeZip(path, entriesFor(names), contents, charset);
    }

    // Writes a zip whose entries each hold their own name as text.
    private static Path namesAsContentZip(Path path, List<String> names)
            throws Exception {
        List<byte[]> contents = new ArrayList<>();
        for (String name : names) {
            contents.add(name.getBytes());
        }
        return writeZip(path, entriesFor(names), contents, ZIP_NAME_CHARSET);
    }

    // Writes a zip whose entries hold the bytes of the given files.
    private static Path zipOfFiles(Path path, List<String> names,
            List<Path> files) throws Exception {
        List<byte[]> contents = new ArrayList<>();
        for (Path file : files) {
            contents.add(Files.readAllBytes(file));
        }
        return writeZip(path, entriesFor(names), contents, ZIP_NAME_CHARSET);
    }

    // Writes a zip with Windows backslashes and ./ in its entry names,
    // where only the Java files hold code.
    private static Path windowsZip(Path path) throws Exception {
        List<String> names = Arrays.asList("./", ".\\charlie\\",
                ".\\charlie\\Q1\\", ".\\charlie\\Q1\\Answer.java",
                "./charlie/Q2/Answer.java");
        List<byte[]> contents = new ArrayList<>();
        for (String name : names) {
            if (name.endsWith(JAVA_SUFFIX)) {
                contents.add("class Answer {}".getBytes());
            } else {
                contents.add(NO_BYTES);
            }
        }
        return writeZip(path, entriesFor(names), contents, ZIP_NAME_CHARSET);
    }

    // Writes a zip holding one entry with the given text.
    private static Path zip(Path path, String name, String content)
            throws Exception {
        List<byte[]> contents = new ArrayList<>();
        contents.add(content.getBytes());
        return writeZip(path, entriesFor(Arrays.asList(name)), contents,
                ZIP_NAME_CHARSET);
    }

    // Counts a submission's flags of the given type.
    private static int countAnomalies(Submission submission,
            AnomalyType type) {
        int count = 0;
        for (Anomaly anomaly : submission.getAnomalies()) {
            if (anomaly.getType() == type) {
                count++;
            }
        }
        return count;
    }

    // Counts a submission's duplicate upload warnings.
    private static int duplicateWarnings(Submission submission) {
        return countAnomalies(submission, AnomalyType.DUPLICATE_SUBMISSION);
    }

    // Tells whether a submission has a flag of the given type.
    private static boolean hasAnomaly(Submission submission,
            AnomalyType type) {
        return countAnomalies(submission, type) > 0;
    }

    // Tells whether a submission has a flag of the given type and message.
    private static boolean hasAnomalyWithMessage(Submission submission,
            AnomalyType type, String message) {
        for (Anomaly anomaly : submission.getAnomalies()) {
            boolean matches = anomaly.getType() == type
                    && anomaly.getMessage().equals(message);
            if (matches) {
                return true;
            }
        }
        return false;
    }

    // Tells whether any flag's message starts with the given text.
    private static boolean hasMessageStarting(List<Anomaly> anomalies,
            String start) {
        for (Anomaly anomaly : anomalies) {
            if (anomaly.getMessage().startsWith(start)) {
                return true;
            }
        }
        return false;
    }

    // Tells whether a run has a submission made from an uploaded path.
    private static boolean hasOriginal(GradingRun run, Path path) {
        for (Submission submission : run.getSubmissions()) {
            if (submission.getOriginalPath().equals(path)) {
                return true;
            }
        }
        return false;
    }

    // Finds the submission made from an uploaded path.
    private static Submission original(GradingRun run, Path path) {
        for (Submission submission : run.getSubmissions()) {
            if (submission.getOriginalPath().equals(path)) {
                return submission;
            }
        }
        throw new AssertionError("no submission made from " + path);
    }

    // Finds the submission that has no username.
    private static Submission findNameless(List<Submission> submissions) {
        for (Submission submission : submissions) {
            if (submission.getUsername().isEmpty()) {
                return submission;
            }
        }
        throw new AssertionError("no submission without a username");
    }

    // Adds the structure problems found in a submission to it.
    private static void addStructureProblems(Submission submission,
            Assessment assessment) {
        for (Anomaly found : new StructureValidator().validate(submission,
                assessment)) {
            submission.addAnomaly(found);
        }
    }

    // Counts a passed check, or stops with the message of a failed one.
    private static void check(boolean condition, String message) {
        if (!condition) {
            throw new AssertionError(message);
        }
        checks++;
    }
}
