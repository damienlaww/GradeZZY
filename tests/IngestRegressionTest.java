import java.nio.file.Files;
import java.nio.file.Path;
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
import gradezzy.util.FileUtils;

/** Run after compilation: javac -cp classes -d /tmp/gradezzy-tests tests/IngestRegressionTest.java
 *  then: java -cp classes:/tmp/gradezzy-tests IngestRegressionTest
 */
public class IngestRegressionTest {
    private static final Section SECTION = new Section("CS102", "G1");
    private static int checks;

    public static void main(String[] args) throws Exception {
        Path root = Files.createTempDirectory("gradezzy-ingest-tests-").toRealPath();
        try {
            AppConfig config = new AppConfig("config.properties");
            config.setString(ConfigKeys.WORKING_DIR, root.resolve("working").toString());
            Path roster = root.resolve("roster.csv");
            Files.writeString(roster, "OrgDefinedId,Username,Last Name,First Name,Email\r\n"
                    + "#1,#ALIce,Example,Alice,alice@example.com\r\n");
            config.setString(ConfigKeys.SCORE_SHEET_FILE, roster.toString());
            SubmissionLoader loader = new SubmissionLoader(config);

            Path bob = zip(root.resolve("bob.zip"), "Q1/Answer.java", "bob");
            Path alice = root.resolve("2023-2024-alice.zip");
            try (ZipOutputStream out = new ZipOutputStream(Files.newOutputStream(alice))) {
                out.putNextEntry(new ZipEntry("bob.zip"));
                out.write(Files.readAllBytes(bob));
                out.closeEntry();
            }
            List<Submission> nested = loader.loadSubmissions(List.of(alice), SECTION);
            check(nested.size() == 1 && nested.get(0).getUsername().equals("alice"),
                    "a nested student ZIP never replaces the outer identity");
            check(nested.get(0).getOriginalPath().equals(alice), "outer original is retained");
            List<Submission> repeated = loader.loadSubmissions(List.of(alice, alice), SECTION);
            check(repeated.size() == 1 && duplicateWarnings(repeated.get(0)) == 1,
                    "selecting the same path twice grades it once and reports the duplicate");
            Path batch = root.resolve("batch.zip");
            try (ZipOutputStream out = new ZipOutputStream(Files.newOutputStream(batch))) {
                out.putNextEntry(new ZipEntry("2023-2024-alice.zip"));
                out.write(Files.readAllBytes(alice));
                out.closeEntry();
                out.putNextEntry(new ZipEntry("bob.zip"));
                out.write(Files.readAllBytes(bob));
                out.closeEntry();
            }
            check(loader.loadSubmissions(List.of(batch), SECTION).size() == 2,
                    "a zip of zips with no question folder is one student per zip inside");
            check(loader.loadBatchArchive(batch, SECTION).size() == 2,
                    "explicit bulk loading still works");
            Path questionZips = root.resolve("2023-2024-eve.zip");
            try (ZipOutputStream out = new ZipOutputStream(Files.newOutputStream(questionZips))) {
                for (String name : List.of("Q1.zip", "Q2.zip", "notes.txt")) {
                    out.putNextEntry(new ZipEntry(name));
                    out.write(name.getBytes());
                    out.closeEntry();
                }
            }
            check(!loader.isBatchArchive(questionZips)
                    && loader.loadSubmissions(List.of(questionZips), SECTION).size() == 1,
                    "a student zip holding question zips stays one student");

            Path upperAlice = zip(Files.createDirectories(root.resolve("upper"))
                    .resolve("2023-2024-ALICE.zip"), "Q1/Answer.java", "upper");
            Path lowerAlice = zip(Files.createDirectories(root.resolve("lower"))
                    .resolve("2023-2024-alice.zip"), "Q1/Answer.java", "lower");
            List<Submission> caseDuplicates = loader.loadSubmissions(List.of(upperAlice, lowerAlice), SECTION);
            check(caseDuplicates.size() == 1 && caseDuplicates.get(0).getOriginalPath().equals(lowerAlice),
                    "a case-variant duplicate keeps the upload whose username is lower case");
            Path otherAlice = zip(Files.createDirectories(root.resolve("other"))
                    .resolve("2023-2024-alice.zip"), "Q1/Answer.java", "other");
            List<Submission> sameNames = loader.loadSubmissions(List.of(otherAlice, lowerAlice), SECTION);
            check(sameNames.size() == 1 && sameNames.get(0).getOriginalPath().equals(otherAlice)
                    && sameNames.get(0).getAnomalies().get(0).getMessage().startsWith("Two files are named"),
                    "a tie keeps the first chosen and two same-named files are told apart by folder");

            Path namelessDir = Files.createDirectories(root.resolve("nameless"));
            zip(namelessDir.resolve("2023-2024-.zip"), "Q1/Answer.java", "first");
            zip(namelessDir.resolve("2023-2024- (1).zip"), "Q1/Answer.java", "second");
            zip(namelessDir.resolve("2023-2024-erin.zip"), "Q1/Answer.java", "erin");
            List<Submission> withNameless = loader.loadSubmissions(List.of(namelessDir), SECTION);
            Submission keptNameless = withNameless.stream()
                    .filter(s -> s.getUsername().isEmpty()).findFirst().orElseThrow();
            check(withNameless.size() == 2 && keptNameless.getStatus() == SubmissionStatus.FAILED
                    && keptNameless.getAnomalies().size() == 2,
                    "uploads without a username give one rejected submission naming the others");

            Path deep = root.resolve("carol.zip");
            try (ZipOutputStream out = new ZipOutputStream(Files.newOutputStream(deep))) {
                for (String name : List.of("carol/carol/Q1/Answer.java", "carol/carol/Q3/notes.zip")) {
                    out.putNextEntry(new ZipEntry(name));
                    out.write(name.getBytes());
                    out.closeEntry();
                }
            }
            List<Submission> deepLoaded = loader.loadSubmissions(List.of(deep), SECTION);
            check(!loader.isBatchArchive(deep) && loader.listBatchSkipped(deep).isEmpty()
                    && deepLoaded.size() == 1 && deepLoaded.get(0).getUsername().equals("carol"),
                    "question folders two levels down mean a student zip, not a bulk zip");

            Path sheet = root.resolve("sheet.csv");
            Files.writeString(sheet, "OrgDefinedId,Username,Calculated Final Grade Numerator\r\n#1,#alice,\r\n");
            GradingRun exported = new GradingRun("RUN-export", new AssessmentFactory(config).create(),
                    SECTION, LocalDateTime.now());
            Submission upperGraded = new Submission("ALICE", SECTION, upperAlice);
            upperGraded.setStatus(SubmissionStatus.GRADED);
            exported.addSubmission(upperGraded);
            exported.setScoreSheetFile(sheet);
            Path csv = root.resolve("export.csv");
            new CsvScoreSheetExporter(config, sheet).export(exported, csv);
            check(Files.readString(csv).contains("#alice,0"),
                    "the CSV export finds a username in any case");

            Path duplicate = zip(root.resolve("ALICE.zip"), "Q1/Answer.java", "duplicate");
            Path nameless = zip(root.resolve("2023-2024-.zip"), "Q1/Answer.java", "nameless");
            Path windows = root.resolve("charlie.zip");
            try (ZipOutputStream out = new ZipOutputStream(Files.newOutputStream(windows))) {
                for (String name : List.of("./", ".\\charlie\\", ".\\charlie\\Q1\\",
                        ".\\charlie\\Q1\\Answer.java", "./charlie/Q2/Answer.java")) {
                    out.putNextEntry(new ZipEntry(name));
                    if (name.endsWith(".java")) out.write("class Answer {}".getBytes());
                    out.closeEntry();
                }
            }
            PipelineFactory factory = new PipelineFactory(config, null, null) {
                @Override public GradingPipeline createPipeline(CancellationToken token) {
                    GradingPipeline pipeline = new GradingPipeline(token);
                    pipeline.addStage(new ExtractStage(new WorkingCopyManager(config,
                            new ZipExtractor(config)), new RosterReader(config)));
                    return pipeline;
                }
            };
            TestCaseSource testers = new TestCaseSource() {
                public String getName() { return "No execution needed for ingestion tests"; }
                public List<TesterFile> prepareTesters(Assessment assessment) { return List.of(); }
            };
            Path damaged = root.resolve("2023-2024-dora.zip");
            Files.writeString(damaged, "not a zip");
            Path damagedCopy = root.resolve("2023-2024-dora (1).zip");
            Files.writeString(damagedCopy, "not a zip either");
            GradingService service = new GradingService(config, factory, new AssessmentFactory(config));
            GradingRun run = service.startGrading(List.of(alice, duplicate, nameless, windows,
                    damagedCopy, damaged), SECTION, testers, new ConsoleProgressListener());
            Submission accepted = original(run, alice);
            Submission unnamed = original(run, nameless);
            Submission unknown = original(run, windows);
            Submission broken = original(run, damagedCopy);
            check(accepted.getStatus() == SubmissionStatus.GRADED, "valid upload completes in a mixed batch");
            check(accepted.getStudent() != null && accepted.getStudent().getUsername().equals("ALIce"),
                    "service loads roster and matches without case sensitivity");
            check(accepted.getAnomalies().stream().noneMatch(a -> a.getType() == AnomalyType.UNKNOWN_STUDENT),
                    "matched students are not flagged unknown");
            check(run.getSubmissions().stream().noneMatch(s -> s.getOriginalPath().equals(duplicate))
                    && run.getSubmissions().stream().noneMatch(s -> s.getOriginalPath().equals(damaged)),
                    "a second file for one student is not added to the run");
            check(duplicateWarnings(accepted) == 1 && duplicateWarnings(broken) == 1,
                    "the graded file carries a warning naming the file left out");
            check(broken.getStatus() == SubmissionStatus.FAILED,
                    "the newest copy, (1), is the one graded");
            check(broken.getAnomalies().stream().anyMatch(a -> a.getType() == AnomalyType.INVALID_ARCHIVE
                    && a.getMessage().equals("This zip file is damaged. Ask the student for a new copy."))
                    && broken.getAnomalies().stream().noneMatch(a -> a.getType() == AnomalyType.INTERNAL_ERROR),
                    "a damaged zip is INVALID_ARCHIVE with one plain sentence, not INTERNAL_ERROR");
            check(unnamed.getStatus() == SubmissionStatus.FAILED && !unnamed.getAnomalies().isEmpty(),
                    "nameless upload is flagged without stopping the batch");
            check(Files.exists(accepted.getWorkingCopyPath().resolve("Q1/Answer.java"))
                    && !Files.exists(accepted.getWorkingCopyPath().resolve("bob.zip")),
                    "a zip holding only one zip is unpacked one level, keeping the outer identity");
            check(unknown.getStatus() == SubmissionStatus.GRADED && unknown.getStudent() == null,
                    "unmatched upload remains gradeable");
            check(unknown.getAnomalies().stream().filter(a -> a.getType() == AnomalyType.UNKNOWN_STUDENT).count() == 1,
                    "unmatched upload raises UNKNOWN_STUDENT");
            check(Files.exists(unknown.getWorkingCopyPath().resolve("charlie/Q1/Answer.java"))
                    && Files.exists(unknown.getWorkingCopyPath().resolve("charlie/Q2/Answer.java")),
                    "Windows backslashes and ./ entries extract into real folders");
            service.retryFailed(run, new ConsoleProgressListener());
            check(run.getSubmissions().stream().noneMatch(s -> s.getOriginalPath().equals(duplicate)),
                    "retry cannot admit a duplicate");
            check(broken.getStatus() == SubmissionStatus.FAILED && duplicateWarnings(broken) == 1,
                    "retry keeps the duplicate warning of the retried submission");
            check(unnamed.getStatus() == SubmissionStatus.FAILED && !unnamed.getAnomalies().isEmpty(),
                    "retry retains the nameless upload rejection");

            config.setString(ConfigKeys.SCORE_SHEET_FILE, root.resolve("missing.csv").toString());
            GradingRun selected = service.startGrading(List.of(alice), SECTION, testers,
                    new ConsoleProgressListener(), roster);
            check(selected.getSubmissions().get(0).getStudent() != null,
                    "selected roster overrides configured roster");

            ZipExtractor extractor = new ZipExtractor(config);
            for (String unsafe : List.of("../escape", "..\\escape", "./../escape", "/absolute",
                    "\\absolute", "C:\\absolute", "\\\\server\\share", "Q1/NUL.java")) {
                check(!extractor.isSafeEntry(unsafe, root.resolve("safe")), "unsafe path rejected: " + unsafe);
            }
            Path malicious = zip(root.resolve("malicious.zip"), "..\\escape.java", "escape");
            try {
                extractor.extract(malicious, root.resolve("unsafe-target"));
                throw new AssertionError("traversal archive accepted");
            } catch (IngestException expected) {
                check(!Files.exists(root.resolve("escape.java")), "traversal writes no escaped file");
            }
            Path icon = root.resolve("icon.zip");
            try (ZipOutputStream out = new ZipOutputStream(Files.newOutputStream(icon))) {
                for (String name : List.of("w/Icon\r", "__MACOSX/w/what?.txt", "w/Q1/._A.java", "w/Q1/A.java")) {
                    out.putNextEntry(new ZipEntry(name));
                    out.closeEntry();
                }
            }
            List<String> skippedNames = new java.util.ArrayList<>();
            Path iconTarget = extractor.extract(icon, root.resolve("icon-target"), skippedNames);
            check(skippedNames.equals(List.of("w/Icon?")) && Files.exists(iconTarget.resolve("w/Q1/A.java"))
                    && !Files.exists(iconTarget.resolve("w/Q1/._A.java")),
                    "ignored names come first and an odd name outside the question folders is skipped");
            Path badQuestion = zip(root.resolve("bad-question.zip"), "w/Q1/Icon\r", "x");
            try {
                extractor.extract(badQuestion, root.resolve("bad-question-target"));
                throw new AssertionError("odd name in a question folder accepted");
            } catch (InvalidArchiveException expected) { checks++; }
            Path clash = root.resolve("clash.zip");
            try (ZipOutputStream out = new ZipOutputStream(Files.newOutputStream(clash))) {
                for (String name : List.of("w/Q1/A.java", "w/q1/A.java")) {
                    out.putNextEntry(new ZipEntry(name));
                    out.closeEntry();
                }
            }
            try {
                extractor.extract(clash, root.resolve("clash-target"));
                throw new AssertionError("case clash accepted");
            } catch (InvalidArchiveException expected) {
                check(expected.getMessage().contains("w/q1/A.java"), "a clash names the entry");
            }
            Path explorer = root.resolve("explorer.zip");
            try (ZipOutputStream out = new ZipOutputStream(Files.newOutputStream(explorer),
                    java.nio.charset.Charset.forName("CP437"))) {
                out.putNextEntry(new ZipEntry("w/Q1/Jos\u00e9.txt"));
                out.closeEntry();
            }
            check(Files.exists(extractor.extract(explorer, root.resolve("explorer-target"))
                    .resolve("w/Q1/Jos\u00e9.txt")), "a zip from Windows Explorer opens");

            Path windowsRoster = root.resolve("windows-roster.csv");
            Files.write(windowsRoster, ("OrgDefinedId,Username,Last Name,First Name,Email,End-of-Line Indicator\r\n"
                    + "1,jose,Example,Jos\u00e9,jose@example.com\r\n").getBytes("windows-1252"));
            List<Student> windowsStudents = new RosterReader(config).readRoster(windowsRoster);
            check(windowsStudents.size() == 1 && windowsStudents.get(0).getFirstName().equals("Jos\u00e9"),
                    "a Windows-1252 roster with no # and no End-of-Line value loads");

            Files.writeString(roster, Files.readString(roster) + "#2,#alice,Other,Alice,other@example.com\r\n");
            try {
                new RosterReader(config).readRoster(roster);
                throw new AssertionError("case-only roster duplicate accepted");
            } catch (IngestException expected) { checks++; }

            ImportBatch ui = new ImportBatch();
            ImportItem first = new ImportItem(InputKind.SUBMISSION, alice, "alice", ImportState.ACCEPTED, "Ready");
            ImportItem second = new ImportItem(InputKind.SUBMISSION, duplicate, "ALICE", ImportState.ACCEPTED, "Ready");
            ui.add(first);
            ui.add(second);
            ui.add(new ImportItem(InputKind.ROSTER, roster, "", ImportState.ACCEPTED, "Ready"));
            ui.add(new ImportItem(InputKind.TESTERS, root, "", ImportState.ACCEPTED, "Ready"));
            check(ui.getEffectiveState(second) == ImportState.DUPLICATE, "UI detects case-only duplicates");
            check(ui.isReady() && ui.getSubmissionPaths().size() == 2,
                    "UI allows grading and passes duplicate to per-file reporting");
            System.out.println("Passed " + checks + " ingestion regression checks.");
        } finally { FileUtils.deleteDirectory(root); }
    }

    private static Path zip(Path path, String name, String content) throws Exception {
        try (ZipOutputStream out = new ZipOutputStream(Files.newOutputStream(path))) {
            out.putNextEntry(new ZipEntry(name));
            out.write(content.getBytes());
            out.closeEntry();
        }
        return path;
    }

    private static long duplicateWarnings(Submission submission) {
        return submission.getAnomalies().stream()
                .filter(a -> a.getType() == AnomalyType.DUPLICATE_SUBMISSION).count();
    }

    private static Submission original(GradingRun run, Path path) {
        return run.getSubmissions().stream().filter(s -> s.getOriginalPath().equals(path)).findFirst().orElseThrow();
    }

    private static void check(boolean condition, String message) {
        if (!condition) throw new AssertionError(message);
        checks++;
    }
}
