/*
 * File: ReapplyFixTest.java
 *
 * Purpose: Checks that a fix rejected in a run graded before GradeZZY was
 * opened can be applied again, both in the session it was rejected in
 * and after another restart, and that the marks come back each time.
 *
 * Where it fits: Run by hand after compiling, as tests/README.md says.
 * It grades one sample upload with an answer file saved in the wrong
 * question folder, in a temporary folder with a copy of the fixed test
 * settings and a temporary database, so no real setting or result is
 * changed. A restart is a new ApplicationContext on the same database.
 */
import java.io.InputStream;
import java.io.OutputStream;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.Arrays;
import java.util.List;
import java.util.zip.ZipEntry;
import java.util.zip.ZipInputStream;
import java.util.zip.ZipOutputStream;

import gradezzy.app.ApplicationContext;
import gradezzy.app.ConsoleProgressListener;
import gradezzy.config.AppConfig;
import gradezzy.config.ConfigKeys;
import gradezzy.model.Anomaly;
import gradezzy.model.FixStatus;
import gradezzy.model.GradingRun;
import gradezzy.model.Section;
import gradezzy.model.Submission;
import gradezzy.service.AnomalyService;
import gradezzy.service.ServiceRegistry;
import gradezzy.testcase.UploadedTesterSource;
import gradezzy.ui.backend.ActionResult;
import gradezzy.ui.backend.LiveBackend;
import gradezzy.ui.backend.RepairItem;
import gradezzy.util.FileUtils;

/**
 * Runs the apply fix again checks and prints how many passed. Compile:
 * javac -cp classes -d /tmp/gradezzy-tests tests/*.java; run:
 * java -cp "classes:lib/*:/tmp/gradezzy-tests" ReapplyFixTest
 */
public class ReapplyFixTest {

    // The sample upload the test changes
    private static final Path SAMPLE_UPLOAD = Path.of("samples",
            "student-submission", "2023-2024-ping.lee.2023.zip");

    // The student of the sample upload
    private static final String USERNAME = "ping.lee.2023";

    // The answer file saved in the wrong folder, and where it is saved
    private static final String ANSWER_ENTRY = USERNAME + "/Q1/Q1a.java";
    private static final String MISPLACED_ENTRY = USERNAME + "/Q2/Q1a.java";

    // The module and section graded
    private static final String MODULE_CODE = "CS102";
    private static final String SECTION_CODE = "G1";

    // The tester files of the samples
    private static final Path TESTERS = Path.of("samples", "Tester-Files");

    // The value that switches a setting off
    private static final String OFF = "false";

    // How close two totals must be to count as the same
    private static final double SAME_TOTAL = 0.001;

    // The number of checks passed so far
    private static int checks = 0;

    /**
     * Runs every check in a temporary folder, then removes the folder.
     *
     * @param args not used
     * @throws Exception if a check fails or a file cannot be written
     */
    public static void main(String[] args) throws Exception {
        Path root = Files.createTempDirectory("gradezzy-reapply-tests-")
                .toRealPath();
        try {
            AppConfig config = makeConfig(root);
            Path upload = makeMisplacedUpload(root);
            double fixedTotal = gradeFirstTime(config, upload);
            checkInSessionOfReject(config, fixedTotal);
            checkAfterRestart(config, fixedTotal);
        } catch (AssertionError error) {
            System.out.println("FAILED: " + error.getMessage());
            System.exit(1);
        } finally {
            FileUtils.deleteDirectory(root);
        }
        System.out.println("Passed " + checks + " apply fix again checks");
    }

    // Makes a copy of the settings that writes only into the temporary
    // folder and skips the slow checks.
    private static AppConfig makeConfig(Path root) throws Exception {
        Path configCopy = root.resolve("config.properties");
        TestConfig.copyTo(configCopy);
        AppConfig config = new AppConfig(configCopy.toString());
        config.setString(ConfigKeys.DATABASE_ENABLED, "true");
        config.setString(ConfigKeys.DATABASE_FILE,
                root.resolve("gradezzy.db").toString());
        config.setString(ConfigKeys.WORKING_DIR,
                root.resolve("working").toString());
        config.setString(ConfigKeys.OUTPUT_DIR,
                root.resolve("output").toString());
        config.setString(ConfigKeys.BACKUP_DIR,
                root.resolve("backup").toString());
        config.setString(ConfigKeys.ASSESSMENTS_DIR,
                root.resolve("assessments").toString());
        config.setString(ConfigKeys.CHOSEN_SUBMISSIONS_FILE,
                root.resolve("chosen.properties").toString());
        config.setString(ConfigKeys.PLAGIARISM_ENABLED, OFF);
        config.setString(ConfigKeys.TESTER_STRENGTH_ENABLED, OFF);
        config.setString(ConfigKeys.RANDOM_INPUTS_ENABLED, OFF);
        return config;
    }

    // Copies the sample upload with its Q1a.java saved in Q2, which the
    // misplaced file fix moves back to Q1.
    private static Path makeMisplacedUpload(Path root) throws Exception {
        Path upload = root.resolve(SAMPLE_UPLOAD.getFileName());
        ZipInputStream zipIn = null;
        ZipOutputStream zipOut = null;
        try {
            InputStream in = Files.newInputStream(SAMPLE_UPLOAD);
            zipIn = new ZipInputStream(in);
            OutputStream out = Files.newOutputStream(upload);
            zipOut = new ZipOutputStream(out);
            copyEntries(zipIn, zipOut);
        } finally {
            FileUtils.closeIfOpen(zipOut);
            FileUtils.closeIfOpen(zipIn);
        }
        return upload;
    }

    // Copies every entry of one zip into another, moving the answer
    // file into the wrong question folder.
    private static void copyEntries(ZipInputStream zipIn,
            ZipOutputStream zipOut) throws Exception {
        ZipEntry entry = zipIn.getNextEntry();
        while (entry != null) {
            String name = entry.getName();
            if (name.equals(ANSWER_ENTRY)) {
                name = MISPLACED_ENTRY;
            }
            zipOut.putNextEntry(new ZipEntry(name));
            zipIn.transferTo(zipOut);
            zipOut.closeEntry();
            entry = zipIn.getNextEntry();
        }
    }

    // Grades the upload once, as in an earlier session, and returns the
    // total with the fix.
    private static double gradeFirstTime(AppConfig config, Path upload)
            throws Exception {
        ApplicationContext context = new ApplicationContext(config);
        try {
            ServiceRegistry services = context.getServices();
            GradingRun run = services.getGradingService().startGrading(
                    Arrays.asList(upload),
                    new Section(MODULE_CODE, SECTION_CODE),
                    new UploadedTesterSource(TESTERS, config),
                    new ConsoleProgressListener());
            Submission submission = run.findLoaded(USERNAME);
            check(submission != null, "the student was graded");
            Anomaly fixed = findWithStatus(submission, FixStatus.APPLIED);
            check(fixed != null, "grading moved the misplaced file");
            return submission.getTotalScore();
        } finally {
            context.close();
        }
    }

    // After a restart, rejects the fix and applies it again in the same
    // session, from the card used to reject and from a reloaded one.
    private static void checkInSessionOfReject(AppConfig config,
            double fixedTotal) {
        ApplicationContext context = new ApplicationContext(config);
        try {
            LiveBackend backend = new LiveBackend(context.getServices());
            Anomaly card = findSaved(context, FixStatus.APPLIED);
            check(card != null, "the saved run shows the fix");
            ActionResult rejected = backend.rejectRepair(
                    new RepairItem(card, "", ""));
            check(rejected.isDone(), "the fix of an earlier run is "
                    + "rejected: " + rejected.getMessage());
            double rejectedTotal = savedTotal(context);
            check(rejectedTotal < fixedTotal, "rejecting lowers the marks");

            check(backend.canReapplyRepair(card), "apply again is offered "
                    + "on the card used to reject");
            Anomaly reloaded = findSaved(context, FixStatus.UNDONE);
            check(reloaded != null, "the saved run shows the fix undone");
            check(backend.canReapplyRepair(reloaded), "apply again is "
                    + "offered on the reloaded card");
            ActionResult reapplied = backend.reapplyRepair(
                    new RepairItem(reloaded, "", ""));
            check(reapplied.isDone(), "the fix is applied again in the "
                    + "session it was rejected in: "
                    + reapplied.getMessage());
            check(reloaded.getFixStatus() == FixStatus.APPLIED,
                    "the card shows the fix applied");
            checkSame(savedTotal(context), fixedTotal,
                    "applying again gives the marks back");
            check(findSaved(context, FixStatus.APPLIED) != null,
                    "the saved run shows the fix applied");

            // Rejected once more, for the restart below
            Anomaly again = findSaved(context, FixStatus.APPLIED);
            check(backend.rejectRepair(new RepairItem(again, "", ""))
                    .isDone(), "the fix is rejected again");
            checkSame(savedTotal(context), rejectedTotal, "rejecting "
                    + "again in a saved run lowers the saved marks");
        } finally {
            context.close();
        }
    }

    // After another restart, the rejected fix is still offered and is
    // applied again, with the marks back.
    private static void checkAfterRestart(AppConfig config,
            double fixedTotal) {
        ApplicationContext context = new ApplicationContext(config);
        try {
            LiveBackend backend = new LiveBackend(context.getServices());
            AnomalyService anomalyService =
                    context.getServices().getAnomalyService();
            Anomaly card = findSaved(context, FixStatus.UNDONE);
            check(card != null, "after a restart the fix shows undone");
            check(!anomalyService.canReapplyFix(card), "the key of the "
                    + "fix is not known in a new session");
            check(backend.canReapplyRepair(card), "apply again is offered "
                    + "after a restart");
            ActionResult reapplied = backend.reapplyRepair(
                    new RepairItem(card, "", ""));
            check(reapplied.isDone(), "the fix is applied again after a "
                    + "restart: " + reapplied.getMessage());
            check(card.getFixStatus() == FixStatus.APPLIED,
                    "the card shows the fix applied after a restart");
            checkSame(savedTotal(context), fixedTotal,
                    "the marks are back after a restart");
            Submission submission = savedSubmission(context);
            check(submission.getRejectedFixes().isEmpty(),
                    "no fix is left rejected");
        } finally {
            context.close();
        }
    }

    // Returns the student of the newest saved run, read from the
    // database again.
    private static Submission savedSubmission(ApplicationContext context) {
        List<GradingRun> runs =
                context.getServices().getHistoryService().listRuns();
        check(!runs.isEmpty(), "the run was saved");
        Submission submission = runs.get(0).findLoaded(USERNAME);
        check(submission != null, "the saved run holds the student");
        return submission;
    }

    // Returns the saved total of the student.
    private static double savedTotal(ApplicationContext context) {
        return savedSubmission(context).getTotalScore();
    }

    // Returns the saved problem of the student with a fix status, or null.
    private static Anomaly findSaved(ApplicationContext context,
            FixStatus status) {
        return findWithStatus(savedSubmission(context), status);
    }

    // Returns the student's first problem with a fix status, or null.
    private static Anomaly findWithStatus(Submission submission,
            FixStatus status) {
        for (Anomaly anomaly : submission.getAnomalies()) {
            if (anomaly.getFixStatus() == status) {
                return anomaly;
            }
        }
        return null;
    }

    // Checks two totals are the same.
    private static void checkSame(double actual, double expected,
            String description) {
        check(Math.abs(actual - expected) < SAME_TOTAL, description
                + " (expected " + expected + ", got " + actual + ")");
    }

    // Counts a passed check, or stops with its description.
    private static void check(boolean passed, String description) {
        if (!passed) {
            throw new AssertionError(description);
        }
        checks++;
    }
}
