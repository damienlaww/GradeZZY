import java.nio.file.Files;
import java.nio.file.Path;
import java.util.List;
import java.util.zip.ZipEntry;
import java.util.zip.ZipOutputStream;

import gradezzy.app.ConsoleProgressListener;
import gradezzy.config.AppConfig;
import gradezzy.config.ConfigKeys;
import gradezzy.grading.*;
import gradezzy.ingest.*;
import gradezzy.model.*;
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
            check(repeated.size() == 2 && repeated.get(1).getIngestFailure() != null,
                    "selecting the same path twice reports the duplicate");
            Path batch = root.resolve("batch.zip");
            try (ZipOutputStream out = new ZipOutputStream(Files.newOutputStream(batch))) {
                out.putNextEntry(new ZipEntry("2023-2024-alice.zip"));
                out.write(Files.readAllBytes(alice));
                out.closeEntry();
                out.putNextEntry(new ZipEntry("bob.zip"));
                out.write(Files.readAllBytes(bob));
                out.closeEntry();
            }
            check(loader.loadSubmissions(List.of(batch), SECTION).size() == 1,
                    "ordinary ZIP uploads never infer a bulk container");
            check(loader.loadBatchArchive(batch, SECTION).size() == 2,
                    "explicit bulk loading still works");

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
            GradingService service = new GradingService(config, factory, new AssessmentFactory(config));
            GradingRun run = service.startGrading(List.of(alice, duplicate, nameless, windows),
                    SECTION, testers, new ConsoleProgressListener());
            Submission accepted = original(run, alice);
            Submission rejected = original(run, duplicate);
            Submission unnamed = original(run, nameless);
            Submission unknown = original(run, windows);
            check(accepted.getStatus() == SubmissionStatus.GRADED, "valid upload completes in a mixed batch");
            check(accepted.getStudent() != null && accepted.getStudent().getUsername().equals("ALIce"),
                    "service loads roster and matches without case sensitivity");
            check(accepted.getAnomalies().stream().noneMatch(a -> a.getType() == AnomalyType.UNKNOWN_STUDENT),
                    "matched students are not flagged unknown");
            check(rejected.getStatus() == SubmissionStatus.FAILED && rejected.getWorkingCopyPath() == null,
                    "duplicate is rejected before touching another student's working copy");
            check(unnamed.getStatus() == SubmissionStatus.FAILED && !unnamed.getAnomalies().isEmpty(),
                    "nameless upload is flagged without stopping the batch");
            check(Files.exists(accepted.getWorkingCopyPath().resolve("bob.zip")),
                    "outer student's nested ZIP remains in their own working copy");
            check(unknown.getStatus() == SubmissionStatus.GRADED && unknown.getStudent() == null,
                    "unmatched upload remains gradeable");
            check(unknown.getAnomalies().stream().filter(a -> a.getType() == AnomalyType.UNKNOWN_STUDENT).count() == 1,
                    "unmatched upload raises UNKNOWN_STUDENT");
            check(Files.exists(unknown.getWorkingCopyPath().resolve("charlie/Q1/Answer.java"))
                    && Files.exists(unknown.getWorkingCopyPath().resolve("charlie/Q2/Answer.java")),
                    "Windows backslashes and ./ entries extract into real folders");
            service.retryFailed(run, new ConsoleProgressListener());
            check(rejected.getStatus() == SubmissionStatus.FAILED && !rejected.getAnomalies().isEmpty(),
                    "retry cannot admit a duplicate or erase its rejection");
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

    private static Submission original(GradingRun run, Path path) {
        return run.getSubmissions().stream().filter(s -> s.getOriginalPath().equals(path)).findFirst().orElseThrow();
    }

    private static void check(boolean condition, String message) {
        if (!condition) throw new AssertionError(message);
        checks++;
    }
}
