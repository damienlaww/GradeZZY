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
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.attribute.FileTime;
import java.time.LocalDateTime;
import java.util.List;
import java.util.zip.ZipEntry;
import java.util.zip.ZipOutputStream;

import gradezzy.app.ConsoleProgressListener;
import gradezzy.config.AppConfig;
import gradezzy.config.ConfigKeys;
import gradezzy.grading.*;
import gradezzy.ingest.*;
import gradezzy.model.*;
import gradezzy.report.CsvScoreSheetExporter;
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
 * Compile: javac -cp classes -d /tmp/gradezzy-tests
 * tests/IngestRegressionTest.java; run: java -cp
 * classes:/tmp/gradezzy-tests IngestRegressionTest
 */
public class IngestRegressionTest {

    // The section every upload is graded for
    private static final Section SECTION = new Section("CS102", "G1");

    // How much newer one of two same-named zips is made, in milliseconds
    private static final long ONE_MINUTE_MILLIS = 60000;

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
            AppConfig config = new AppConfig("config.properties");
            config.setString(ConfigKeys.WORKING_DIR,
                    root.resolve("working").toString());
            Path roster = root.resolve("roster.csv");
            Files.writeString(roster, "OrgDefinedId,Username,Last Name,"
                    + "First Name,Email\r\n"
                    + "#1,#ALIce,Example,Alice,alice@example.com\r\n");
            config.setString(ConfigKeys.SCORE_SHEET_FILE, roster.toString());
            SubmissionLoader loader = new SubmissionLoader(config);

            Path bob = zip(root.resolve("bob.zip"), "Q1/Answer.java", "bob");
            Path alice = root.resolve("2023-2024-alice.zip");
            try (ZipOutputStream out = new ZipOutputStream(
                    Files.newOutputStream(alice))) {
                out.putNextEntry(new ZipEntry("bob.zip"));
                out.write(Files.readAllBytes(bob));
                out.closeEntry();
            }
            List<Submission> nested = loader.loadSubmissions(List.of(alice),
                    SECTION);
            check(nested.size() == 1
                    && nested.get(0).getUsername().equals("alice"),
                    "a nested student ZIP never replaces the outer identity");
            check(nested.get(0).getOriginalPath().equals(alice),
                    "outer original is retained");
            List<Submission> repeated = loader.loadSubmissions(
                    List.of(alice, alice), SECTION);
            check(repeated.size() == 1
                    && duplicateWarnings(repeated.get(0)) == 1,
                    "selecting the same path twice grades it once and "
                    + "reports the duplicate");
            Path batch = root.resolve("batch.zip");
            try (ZipOutputStream out = new ZipOutputStream(
                    Files.newOutputStream(batch))) {
                out.putNextEntry(new ZipEntry("2023-2024-alice.zip"));
                out.write(Files.readAllBytes(alice));
                out.closeEntry();
                out.putNextEntry(new ZipEntry("bob.zip"));
                out.write(Files.readAllBytes(bob));
                out.closeEntry();
            }
            check(loader.loadSubmissions(List.of(batch), SECTION).size()
                    == 2, "a zip of zips with no question folder is one "
                    + "student per zip inside");
            check(loader.loadBatchArchive(batch, SECTION).size() == 2,
                    "explicit bulk loading still works");
            Path questionZips = root.resolve("2023-2024-eve.zip");
            try (ZipOutputStream out = new ZipOutputStream(
                    Files.newOutputStream(questionZips))) {
                for (String name : List.of("Q1.zip", "Q2.zip", "notes.txt")) {
                    out.putNextEntry(new ZipEntry(name));
                    out.write(name.getBytes());
                    out.closeEntry();
                }
            }
            check(!loader.isBatchArchive(questionZips)
                    && loader.loadSubmissions(List.of(questionZips),
                    SECTION).size() == 1,
                    "a student zip holding question zips stays one student");

            Path upperAlice = zip(Files.createDirectories(
                    root.resolve("upper")).resolve("2023-2024-ALICE.zip"),
                    "Q1/Answer.java", "upper");
            Path lowerAlice = zip(Files.createDirectories(
                    root.resolve("lower")).resolve("2023-2024-alice.zip"),
                    "Q1/Answer.java", "lower");
            List<Submission> caseDuplicates = loader.loadSubmissions(
                    List.of(upperAlice, lowerAlice), SECTION);
            check(caseDuplicates.size() == 1 && caseDuplicates.get(0)
                    .getOriginalPath().equals(lowerAlice),
                    "a case-variant duplicate keeps the upload whose "
                    + "username is lower case");
            Path otherAlice = zip(Files.createDirectories(
                    root.resolve("other")).resolve("2023-2024-alice.zip"),
                    "Q1/Answer.java", "other");
            // Both zips are written within the same moment, so the newer
            // one is made a minute newer on purpose
            long lowerTime = Files.getLastModifiedTime(lowerAlice)
                    .toMillis();
            long otherTime = lowerTime + ONE_MINUTE_MILLIS;
            Files.setLastModifiedTime(otherAlice,
                    FileTime.fromMillis(otherTime));
            List<Submission> sameNames = loader.loadSubmissions(
                    List.of(otherAlice, lowerAlice), SECTION);
            check(sameNames.size() == 1
                    && sameNames.get(0).getOriginalPath().equals(otherAlice)
                    && sameNames.get(0).getAnomalies().get(0).getMessage()
                    .startsWith("Two files are named"),
                    "of two same-named files in two folders the newer is "
                    + "kept, and they are told apart by folder");

            Path namelessDir = Files.createDirectories(
                    root.resolve("nameless"));
            zip(namelessDir.resolve("2023-2024-.zip"), "Q1/Answer.java",
                    "first");
            zip(namelessDir.resolve("2023-2024- (1).zip"), "Q1/Answer.java",
                    "second");
            zip(namelessDir.resolve("2023-2024-erin.zip"), "Q1/Answer.java",
                    "erin");
            List<Submission> withNameless = loader.loadSubmissions(
                    List.of(namelessDir), SECTION);
            Submission keptNameless = withNameless.stream()
                    .filter(submission -> submission.getUsername().isEmpty())
                    .findFirst().orElseThrow();
            check(withNameless.size() == 2
                    && keptNameless.getStatus() == SubmissionStatus.FAILED
                    && keptNameless.getAnomalies().size() == 2,
                    "uploads without a username give one rejected "
                    + "submission naming the others");

            Path deep = root.resolve("carol.zip");
            try (ZipOutputStream out = new ZipOutputStream(
                    Files.newOutputStream(deep))) {
                for (String name : List.of("carol/carol/Q1/Answer.java",
                        "carol/carol/Q3/notes.zip")) {
                    out.putNextEntry(new ZipEntry(name));
                    out.write(name.getBytes());
                    out.closeEntry();
                }
            }
            List<Submission> deepLoaded = loader.loadSubmissions(
                    List.of(deep), SECTION);
            check(!loader.isBatchArchive(deep)
                    && loader.listBatchSkipped(deep).isEmpty()
                    && deepLoaded.size() == 1
                    && deepLoaded.get(0).getUsername().equals("carol"),
                    "question folders two levels down mean a student zip, "
                    + "not a bulk zip");

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

            Path duplicate = zip(root.resolve("ALICE.zip"), "Q1/Answer.java",
                    "duplicate");
            Path nameless = zip(root.resolve("2023-2024-.zip"),
                    "Q1/Answer.java", "nameless");
            Path windows = root.resolve("charlie.zip");
            try (ZipOutputStream out = new ZipOutputStream(
                    Files.newOutputStream(windows))) {
                for (String name : List.of("./", ".\\charlie\\",
                        ".\\charlie\\Q1\\", ".\\charlie\\Q1\\Answer.java",
                        "./charlie/Q2/Answer.java")) {
                    out.putNextEntry(new ZipEntry(name));
                    if (name.endsWith(".java")) {
                        out.write("class Answer {}".getBytes());
                    }
                    out.closeEntry();
                }
            }
            PipelineFactory factory = new PipelineFactory(config, null,
                    null) {
                /** Builds a pipeline that only unpacks the uploads. */
                @Override
                public GradingPipeline createPipeline(
                        CancellationToken token) {
                    GradingPipeline pipeline = new GradingPipeline(token);
                    pipeline.addStage(new ExtractStage(
                            new WorkingCopyManager(config,
                            new ZipExtractor(config)),
                            new RosterReader(config)));
                    return pipeline;
                }
            };
            TestCaseSource testers = new TestCaseSource() {
                /** Names this source. */
                public String getName() {
                    return "No execution needed for ingestion tests";
                }

                /** Gives no testers, as nothing is run. */
                public List<TesterFile> prepareTesters(
                        Assessment assessment) {
                    return List.of();
                }
            };
            Path damaged = root.resolve("2023-2024-dora.zip");
            Files.writeString(damaged, "not a zip");
            Path damagedCopy = root.resolve("2023-2024-dora (1).zip");
            Files.writeString(damagedCopy, "not a zip either");
            GradingService service = new GradingService(config, factory,
                    new AssessmentFactory(config));
            GradingRun run = service.startGrading(List.of(alice, duplicate,
                    nameless, windows, damagedCopy, damaged), SECTION,
                    testers, new ConsoleProgressListener());
            Submission accepted = original(run, alice);
            Submission unnamed = original(run, nameless);
            Submission unknown = original(run, windows);
            Submission broken = original(run, damagedCopy);
            check(accepted.getStatus() == SubmissionStatus.GRADED,
                    "valid upload completes in a mixed batch");
            check(accepted.getStudent() != null
                    && accepted.getStudent().getUsername().equals("ALIce"),
                    "service loads roster and matches without case "
                    + "sensitivity");
            check(accepted.getAnomalies().stream().noneMatch(
                    anomaly -> anomaly.getType()
                    == AnomalyType.UNKNOWN_STUDENT),
                    "matched students are not flagged unknown");
            check(run.getSubmissions().stream().noneMatch(
                    submission -> submission.getOriginalPath()
                    .equals(duplicate))
                    && run.getSubmissions().stream().noneMatch(
                    submission -> submission.getOriginalPath()
                    .equals(damaged)),
                    "a second file for one student is not added to the run");
            check(duplicateWarnings(accepted) == 1
                    && duplicateWarnings(broken) == 1,
                    "the graded file carries a warning naming the file left "
                    + "out");
            check(broken.getStatus() == SubmissionStatus.FAILED,
                    "the newest copy, (1), is the one graded");
            check(broken.getAnomalies().stream().anyMatch(
                    anomaly -> anomaly.getType() == AnomalyType.INVALID_ARCHIVE
                    && anomaly.getMessage().equals("This zip file is "
                    + "damaged. Ask the student for a new copy."))
                    && broken.getAnomalies().stream().noneMatch(
                    anomaly -> anomaly.getType()
                    == AnomalyType.INTERNAL_ERROR),
                    "a damaged zip is INVALID_ARCHIVE with one plain "
                    + "sentence, not INTERNAL_ERROR");
            check(unnamed.getStatus() == SubmissionStatus.FAILED
                    && !unnamed.getAnomalies().isEmpty(),
                    "nameless upload is flagged without stopping the batch");
            check(Files.exists(accepted.getWorkingCopyPath()
                    .resolve("Q1/Answer.java"))
                    && !Files.exists(accepted.getWorkingCopyPath()
                    .resolve("bob.zip")),
                    "a zip holding only one zip is unpacked one level, "
                    + "keeping the outer identity");
            check(unknown.getStatus() == SubmissionStatus.GRADED
                    && unknown.getStudent() == null,
                    "unmatched upload remains gradeable");
            check(unknown.getAnomalies().stream().filter(
                    anomaly -> anomaly.getType()
                    == AnomalyType.UNKNOWN_STUDENT).count() == 1,
                    "unmatched upload raises UNKNOWN_STUDENT");
            check(Files.exists(unknown.getWorkingCopyPath()
                    .resolve("charlie/Q1/Answer.java"))
                    && Files.exists(unknown.getWorkingCopyPath()
                    .resolve("charlie/Q2/Answer.java")),
                    "Windows backslashes and ./ entries extract into real "
                    + "folders");
            service.retryFailed(run, new ConsoleProgressListener());
            check(run.getSubmissions().stream().noneMatch(
                    submission -> submission.getOriginalPath()
                    .equals(duplicate)),
                    "retry cannot admit a duplicate");
            check(broken.getStatus() == SubmissionStatus.FAILED
                    && duplicateWarnings(broken) == 1,
                    "retry keeps the duplicate warning of the retried "
                    + "submission");
            check(unnamed.getStatus() == SubmissionStatus.FAILED
                    && !unnamed.getAnomalies().isEmpty(),
                    "retry retains the nameless upload rejection");

            config.setString(ConfigKeys.SCORE_SHEET_FILE,
                    root.resolve("missing.csv").toString());
            GradingRun selected = service.startGrading(List.of(alice),
                    SECTION, testers, new ConsoleProgressListener(), roster);
            check(selected.getSubmissions().get(0).getStudent() != null,
                    "selected roster overrides configured roster");

            ZipExtractor extractor = new ZipExtractor(config);
            for (String unsafe : List.of("../escape", "..\\escape",
                    "./../escape", "/absolute", "\\absolute", "C:\\absolute",
                    "\\\\server\\share", "Q1/NUL.java")) {
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
            Path icon = root.resolve("icon.zip");
            try (ZipOutputStream out = new ZipOutputStream(
                    Files.newOutputStream(icon))) {
                for (String name : List.of("w/Icon\r", "__MACOSX/w/what?.txt",
                        "w/Q1/._A.java", "w/Q1/A.java")) {
                    out.putNextEntry(new ZipEntry(name));
                    out.closeEntry();
                }
            }
            List<String> skippedNames = new java.util.ArrayList<>();
            Path iconTarget = extractor.extract(icon,
                    root.resolve("icon-target"), skippedNames);
            check(skippedNames.equals(List.of("w/Icon?"))
                    && Files.exists(iconTarget.resolve("w/Q1/A.java"))
                    && !Files.exists(iconTarget.resolve("w/Q1/._A.java")),
                    "ignored names come first and an odd name outside the "
                    + "question folders is skipped");
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
            Path clash = root.resolve("clash.zip");
            try (ZipOutputStream out = new ZipOutputStream(
                    Files.newOutputStream(clash))) {
                for (String name : List.of("w/Q1/A.java", "w/q1/A.java")) {
                    out.putNextEntry(new ZipEntry(name));
                    out.closeEntry();
                }
            }
            try {
                extractor.extract(clash, root.resolve("clash-target"));
                throw new AssertionError("case clash accepted");
            } catch (InvalidArchiveException expected) {
                check(expected.getMessage().contains("w/q1/A.java"),
                        "a clash names the entry");
            }
            Path explorer = root.resolve("explorer.zip");
            try (ZipOutputStream out = new ZipOutputStream(
                    Files.newOutputStream(explorer),
                    java.nio.charset.Charset.forName("CP437"))) {
                out.putNextEntry(new ZipEntry("w/Q1/Jos\u00e9.txt"));
                out.closeEntry();
            }
            check(Files.exists(extractor.extract(explorer,
                    root.resolve("explorer-target"))
                    .resolve("w/Q1/Jos\u00e9.txt")),
                    "a zip from Windows Explorer opens");

            Path windowsRoster = root.resolve("windows-roster.csv");
            Files.write(windowsRoster, ("OrgDefinedId,Username,Last Name,"
                    + "First Name,Email,End-of-Line Indicator\r\n"
                    + "1,jose,Example,Jos\u00e9,jose@example.com\r\n")
                    .getBytes("windows-1252"));
            List<Student> windowsStudents = new RosterReader(config)
                    .readRoster(windowsRoster);
            check(windowsStudents.size() == 1 && windowsStudents.get(0)
                    .getFirstName().equals("Jos\u00e9"),
                    "a Windows-1252 roster with no # and no End-of-Line "
                    + "value loads");

            Files.writeString(roster, Files.readString(roster)
                    + "#2,#alice,Other,Alice,other@example.com\r\n");
            try {
                new RosterReader(config).readRoster(roster);
                throw new AssertionError("case-only roster duplicate "
                        + "accepted");
            } catch (IngestException expected) {
                checks++;
            }

            ImportBatch ui = new ImportBatch();
            ImportItem first = new ImportItem(InputKind.SUBMISSION, alice,
                    "alice", ImportState.ACCEPTED, "Ready");
            ImportItem second = new ImportItem(InputKind.SUBMISSION,
                    duplicate, "ALICE", ImportState.ACCEPTED, "Ready");
            ui.add(first);
            ui.add(second);
            ui.add(new ImportItem(InputKind.ROSTER, roster, "",
                    ImportState.ACCEPTED, "Ready"));
            ui.add(new ImportItem(InputKind.TESTERS, root, "",
                    ImportState.ACCEPTED, "Ready"));
            check(ui.getEffectiveState(second) == ImportState.DUPLICATE,
                    "UI detects case-only duplicates");
            check(ui.isReady() && ui.getSubmissionPaths().size() == 2,
                    "UI allows grading and passes duplicate to per-file "
                    + "reporting");
            checkAnswerFolders(root.resolve("answer-folders"), config);
            checkInputFixes(root.resolve("input-fixes"), config, loader);
            System.out.println("Passed " + checks
                    + " ingestion regression checks.");
        } finally {
            FileUtils.deleteDirectory(root);
        }
    }

    // Checks the rule that picks the folder to grade, and the folder
    // fixes for chains of folders and a question folder inside itself.
    private static void checkAnswerFolders(Path root, AppConfig config)
            throws Exception {
        List<String> names = List.of("Q1", "Q2", "Q3");
        Path two = root.resolve("two");
        write(two.resolve("u/Q1/Q1a.java"), "class Q1a {}");
        write(two.resolve("u (2)/Q1/Q1a.java"), "class Q1a {}");
        check(AnswerFolderRule.findContentRoot(two, "u", names) == null
                && AnswerFolderRule.findAnswerHolders(two, names).size()
                == 2, "two answer folders give no folder to grade");
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
        Path stale = root.resolve("stale");
        write(stale.resolve("alice/Q1/Q1a.java"), "class Q1a {}");
        for (String folder : names) {
            write(stale.resolve("alice/src/" + folder + "/A.java"),
                    "class A {}");
        }
        check(AnswerFolderRule.findContentRoot(stale, "alice", names) == null
                && AnswerFolderRule.findAnswerHolders(stale, names).equals(
                List.of(stale.resolve("alice"), stale.resolve("alice/src"))),
                "question folders in a top folder and in its src are two "
                + "answers");
        Submission staleUpload = new Submission("alice", SECTION, stale);
        staleUpload.setWorkingCopyPath(stale);
        List<Anomaly> staleFound = new StructureValidator().validate(
                staleUpload, new AssessmentFactory(config).create());
        check(staleFound.size() == 1
                && staleFound.get(0).getMessage().contains(
                "2 places (alice and alice/src)"),
                "one error names both answer places");
        Path backup = root.resolve("backup");
        for (String folder : names) {
            write(backup.resolve("alice/" + folder + "/A.java"),
                    "class A {}");
        }
        write(backup.resolve("alice/backup/Q1/A.java"), "class A {}");
        write(backup.resolve("alice/backup/Q3/A.java"), "class A {}");
        check(AnswerFolderRule.findContentRoot(backup, "alice", names) == null
                && AnswerFolderRule.findAnswerHolders(backup, names).equals(
                List.of(backup.resolve("alice"),
                backup.resolve("alice/backup"))),
                "a backup of question folders beside them is a second "
                + "answer");
        Path nested = root.resolve("nested");
        write(nested.resolve("Q1/Q1/Q1a.java"), "class Q1a {}");
        write(nested.resolve("Q1/Shape.class"), "x");
        check(AnswerFolderRule.findNestedQuestionFolder(
                nested.resolve("Q1"), "Q1").equals(
                nested.resolve("Q1/Q1")),
                "a question folder inside itself is found");

        Assessment assessment = new AssessmentFactory(config).create();
        Path chain = root.resolve("chain");
        for (String file : List.of("Q1/Q1a.java", "Q1/Q1b.java",
                "Q2/Q2a.java", "Q2/Q2b.java", "Q3/Q3.java")) {
            write(chain.resolve("Users/jy/Desktop/u/" + file), "class X {}");
        }
        Submission deep = new Submission("u", SECTION, chain);
        deep.setWorkingCopyPath(chain);
        for (Anomaly found : new StructureValidator().validate(deep,
                assessment)) {
            deep.addAnomaly(found);
        }
        FixManager fixes = new FixManager();
        fixes.addFixer(new FolderHierarchyFixer(config));
        fixes.addFixer(new FolderNameFixer(config));
        List<FixRecord> applied = fixes.applyAll(deep);
        check(applied.size() == 2
                && Files.exists(chain.resolve("u/Q3/Q3.java"))
                && !Files.exists(chain.resolve("Users")),
                "question folders three levels down are moved up and the "
                + "top folder renamed");
        fixes.undo(applied.get(1));
        fixes.undo(applied.get(0));
        check(Files.exists(chain.resolve("Users/jy/Desktop/u/Q3/Q3.java"))
                && !Files.exists(chain.resolve("u")),
                "undo puts the chain of folders back");

        Path inside = root.resolve("inside");
        write(inside.resolve("u/Q1/Q1/Q1a.java"), "class Q1a {}");
        write(inside.resolve("u/Q1/Q1/Q1b.java"), "class Q1b {}");
        write(inside.resolve("u/Q2/Q2a.java"), "class Q2a {}");
        write(inside.resolve("u/Q2/Q2b.java"), "class Q2b {}");
        write(inside.resolve("u/Q3/Q3.java"), "class Q3 {}");
        Submission dragged = new Submission("u", SECTION, inside);
        dragged.setWorkingCopyPath(inside);
        for (Anomaly found : new StructureValidator().validate(dragged,
                assessment)) {
            dragged.addAnomaly(found);
        }
        check(new RequiredFilesValidator().validate(dragged, assessment)
                .isEmpty(), "Q1/Q1 is not also reported as missing files");
        FixManager insideFixes = new FixManager();
        insideFixes.addFixer(new FolderHierarchyFixer(config));
        List<FixRecord> moved = insideFixes.applyAll(dragged);
        check(moved.size() == 1
                && Files.exists(inside.resolve("u/Q1/Q1a.java")),
                "a question folder inside itself is moved up");
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
        Path spaces = root.resolve("spaces.zip");
        try (ZipOutputStream out = new ZipOutputStream(
                Files.newOutputStream(spaces))) {
            for (String name : List.of("w /Q1 /A.java", "w /Q2/B.java.")) {
                out.putNextEntry(new ZipEntry(name));
                out.closeEntry();
            }
        }
        List<String> renamed = new java.util.ArrayList<>();
        Path spacesTarget = extractor.extract(spaces,
                root.resolve("spaces-target"), new java.util.ArrayList<>(),
                renamed);
        check(Files.exists(spacesTarget.resolve("w/Q1/A.java"))
                && Files.exists(spacesTarget.resolve("w/Q2/B.java"))
                && renamed.contains("\"w \" as \"w\""),
                "a space or dot at the end of a name is dropped and named");
        Path trimClash = root.resolve("trim-clash.zip");
        try (ZipOutputStream out = new ZipOutputStream(
                Files.newOutputStream(trimClash))) {
            for (String name : List.of("w/Q1/A.java", "w/Q1 /A.java")) {
                out.putNextEntry(new ZipEntry(name));
                out.closeEntry();
            }
        }
        try {
            extractor.extract(trimClash, root.resolve("trim-clash-target"));
            throw new AssertionError("a clash after trimming accepted");
        } catch (InvalidArchiveException expected) {
            checks++;
        }

        Path sheet = root.resolve("excel.csv");
        Files.writeString(sheet, "OrgDefinedId,Username,Last Name,"
                + "First Name,Email,Calculated Final Grade Numerator\n"
                + "#1,#bob ,B,Bob,bob@example.com,\n"
                + "#2,# carl,C,Carl,carl@example.com,12.5\n"
                + "#3,#dave,D,Dave,dave@example.com,9\n"
                + ",,,,,\n,,,,,\n");
        List<Student> students = new RosterReader(config).readRoster(sheet);
        check(students.size() == 3
                && students.get(0).getUsername().equals("bob")
                && students.get(1).getUsername().equals("carl"),
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
        check(exported.contains("#bob ,B,Bob,bob@example.com,0")
                && exported.contains("# carl,C,Carl,carl@example.com,")
                && !exported.contains("12.5")
                && exported.contains("#dave,D,Dave,dave@example.com,")
                && !exported.contains("dave@example.com,9"),
                "export matches a trimmed username and blanks a mark "
                + "already in the sheet for a failed or missing student");

        check(loader.extractUsername("2023-2024-dan.zip.zip").equals("dan"),
                "a name ending .zip.zip gives the username");

        Path older = zip(root.resolve("older.zip"), "dan/Q1/Q1a.java",
                "old");
        Path newer = zip(root.resolve("newer.zip"), "dan/Q1/Q1a.java",
                "new");
        Path bulk = root.resolve("bulk download.zip");
        try (ZipOutputStream out = new ZipOutputStream(
                Files.newOutputStream(bulk))) {
            ZipEntry first = new ZipEntry("a/2023-2024-dan.zip");
            first.setTime(1700000000000L);
            out.putNextEntry(first);
            out.write(Files.readAllBytes(older));
            out.closeEntry();
            ZipEntry second = new ZipEntry("b/2023-2024-dan.zip");
            second.setTime(1700090000000L);
            out.putNextEntry(second);
            out.write(Files.readAllBytes(newer));
            out.closeEntry();
        }
        List<Submission> fromBulk = loader.loadSubmissions(List.of(bulk),
                SECTION);
        Submission dan = fromBulk.get(0);
        check(fromBulk.size() == 1
                && dan.getOriginalPath().getParent().getFileName()
                .toString().equals("b")
                && dan.getAnomalies().get(0).getMessage().contains(
                "in the folder a of the bulk zip"),
                "the newer of two same-named zips in a bulk zip is graded "
                + "and the message names both folders");

        Assessment assessment = new AssessmentFactory(config).create();
        Path copied = root.resolve("copied");
        write(copied.resolve("u/Q1/Q1a (1).java"), "class Q1a {}");
        write(copied.resolve("u/Q1/Q1b.java"), "class Q1b {}");
        Submission browser = new Submission("u", SECTION, copied);
        browser.setWorkingCopyPath(copied);
        List<Anomaly> names = new FileNameValidator().validate(browser,
                assessment);
        check(names.size() == 1 && names.get(0).getMessage().equals(
                "Q1/Q1a (1).java should be named Q1/Q1a.java.")
                && new RequiredFilesValidator().validate(browser, assessment)
                .stream().noneMatch(anomaly -> anomaly.getMessage()
                .startsWith("Q1/Q1a.java")),
                "a browser copy name is a wrong name, not a missing file");
        check(HeaderNameValidator.findValueStart(" * Name : Ann", "Name:")
                == " * Name :".length()
                && HeaderNameValidator.findValueStart(" * Email ID :",
                "Email ID:") == " * Email ID :".length(),
                "a header label with a space before the colon is read");
    }

    // Writes a text file, making its folders first.
    private static void write(Path file, String text) throws Exception {
        Files.createDirectories(file.getParent());
        Files.writeString(file, text);
    }

    // Writes a zip holding one entry with the given text.
    private static Path zip(Path path, String name, String content)
            throws Exception {
        try (ZipOutputStream out = new ZipOutputStream(
                Files.newOutputStream(path))) {
            out.putNextEntry(new ZipEntry(name));
            out.write(content.getBytes());
            out.closeEntry();
        }
        return path;
    }

    // Counts a submission's duplicate upload warnings.
    private static long duplicateWarnings(Submission submission) {
        return submission.getAnomalies().stream()
                .filter(anomaly -> anomaly.getType()
                == AnomalyType.DUPLICATE_SUBMISSION).count();
    }

    // Finds the submission made from an uploaded path.
    private static Submission original(GradingRun run, Path path) {
        return run.getSubmissions().stream()
                .filter(submission -> submission.getOriginalPath()
                .equals(path)).findFirst().orElseThrow();
    }

    // Counts a passed check, or stops with the message of a failed one.
    private static void check(boolean condition, String message) {
        if (!condition) {
            throw new AssertionError(message);
        }
        checks++;
    }
}
