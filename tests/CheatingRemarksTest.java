/*
 * File: CheatingRemarksTest.java
 *
 * Purpose: Checks the Remarks column of the score sheet. Similar code
 * flags are never settled by Mark all as checked; a pair gives a remark
 * only when both students' flags were kept flagged, not when one side
 * looks fine; the activity log says "Kept flagged" for such a flag; and
 * an export of only some students gives them the same remark, with the
 * other student's name, as an export of everyone.
 *
 * Where it fits: Run by hand after compiling, as tests/README.md says.
 * It works on a copy of the fixed test settings with its own database and
 * export folder in a temporary folder, so no real file is changed.
 */
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.time.LocalDateTime;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;

import gradezzy.app.ApplicationContext;
import gradezzy.config.AppConfig;
import gradezzy.config.ConfigKeys;
import gradezzy.model.Anomaly;
import gradezzy.model.AnomalyType;
import gradezzy.model.Assessment;
import gradezzy.model.AuditEntry;
import gradezzy.model.GradingRun;
import gradezzy.model.Question;
import gradezzy.model.Section;
import gradezzy.model.Severity;
import gradezzy.model.Student;
import gradezzy.model.Submission;
import gradezzy.model.SubmissionStatus;
import gradezzy.ui.backend.ActionResult;
import gradezzy.ui.backend.CheatingRemarks;
import gradezzy.ui.backend.LiveBackend;
import gradezzy.ui.backend.ReviewDecision;
import gradezzy.ui.backend.ReviewRules;
import gradezzy.util.FileUtils;

/**
 * Runs the Remarks checks and prints how many passed. Compile:
 * javac -cp classes -d /tmp/gradezzy-tests tests/*.java; run:
 * java -cp "classes:lib/*:/tmp/gradezzy-tests" CheatingRemarksTest
 */
public class CheatingRemarksTest {

    // The run, its section and its question
    private static final String RUN_ID = "RUN-77";
    private static final String QUESTION = "Q1";

    // The two students with an identical copy, kept flagged on both sides
    private static final String DAPHNE = "daphne.tan.2025";
    private static final String ELIJAH = "elijah.wee.2025";

    // The two students whose decisions do not agree: one Keep flagged,
    // one Looks fine
    private static final String FIONA = "fiona.lim.2025";
    private static final String GUS = "gus.ong.2025";

    // The remark daphne gets
    private static final String DAPHNE_REMARK =
            "Cheated with ELIJAH WEE (elijah.wee.2025)";

    // The format names ExportService knows
    private static final String CSV = "CSV";

    // The number of checks passed so far
    private static int checks = 0;

    /**
     * Runs every check in a temporary folder, then removes the folder.
     *
     * @param args not used
     * @throws Exception if a check fails or a file cannot be written
     */
    public static void main(String[] args) throws Exception {
        Path root = Files.createTempDirectory("gradezzy-remarks-tests-")
                .toRealPath();
        ApplicationContext context = null;
        try {
            checkBulkRules();
            Path configCopy = root.resolve("config.properties");
            TestConfig.copyTo(configCopy);
            AppConfig config = new AppConfig(configCopy.toString());
            config.setString(ConfigKeys.DATABASE_ENABLED, "true");
            config.setString(ConfigKeys.DATABASE_FILE,
                    root.resolve("gradezzy.db").toString());
            config.setString(ConfigKeys.BACKUP_DIR,
                    root.resolve("backup").toString());
            config.setString(ConfigKeys.OUTPUT_DIR,
                    root.resolve("output").toString());
            config.setString(ConfigKeys.OUTPUT_DIR_BY_SECTION, "");
            config.setString(ConfigKeys.AUDIT_ENABLED, "true");
            context = new ApplicationContext(config);
            LiveBackend backend = new LiveBackend(context.getServices());

            GradingRun run = makeRun(root);
            List<Anomaly> daphneElijah = flagPair(run, DAPHNE, ELIJAH,
                    true);
            List<Anomaly> fionaGus = flagPair(run, FIONA, GUS, false);
            checkConflict(backend, run, fionaGus);
            checkKept(backend, run, daphneElijah);
            checkAudit(backend);
            checkSubsetExport(backend, run, root);
            System.out.println("Passed " + checks + " cheating remarks "
                    + "checks.");
        } finally {
            if (context != null) {
                context.close();
            }
            FileUtils.deleteDirectory(root);
        }
    }

    // Mark all as checked leaves Similar code flags to Similarity, and a
    // kept flag is logged as Kept flagged, not as Marked as checked.
    private static void checkBulkRules() {
        check(!ReviewRules.canCheckAll(AnomalyType.SIMILAR_CODE),
                "Mark all as checked leaves Similar code flags");
        check(ReviewRules.canCheckAll(AnomalyType.EMPTY_ANSWER),
                "Mark all as checked still checks an empty answer");
        check(ReviewDecision.AUDIT_KEPT_FLAGGED.equals(
                ReviewDecision.REVIEWED.getAuditAction(
                AnomalyType.SIMILAR_CODE)),
                "a kept Similar code flag is logged as Kept flagged");
        check(ReviewDecision.AUDIT_CHECKED.equals(
                ReviewDecision.REVIEWED.getAuditAction(
                AnomalyType.COMPILE_ERROR)),
                "a checked compile error is logged as Marked as checked");
    }

    // One side kept flagged and the other marked as looking fine gives
    // neither student a remark.
    private static void checkConflict(LiveBackend backend, GradingRun run,
            List<Anomaly> flags) {
        backend.setDecision(flags.get(0), ReviewDecision.REVIEWED);
        backend.setDecision(flags.get(1), ReviewDecision.REJECTED);
        Map<String, String> remarks = new CheatingRemarks(backend)
                .remarksFor(run);
        check(!remarks.containsKey(FIONA) && !remarks.containsKey(GUS),
                "Keep flagged on one side and Looks fine on the other give "
                + "no remark: " + remarks);
    }

    // Both sides kept flagged give each student a remark naming the other
    // with their name and username.
    private static void checkKept(LiveBackend backend, GradingRun run,
            List<Anomaly> flags) {
        backend.setDecision(flags.get(0), ReviewDecision.REVIEWED);
        Map<String, String> remarks = new CheatingRemarks(backend)
                .remarksFor(run);
        check(!remarks.containsKey(DAPHNE),
                "one side kept, the other still open: no remark yet");
        backend.setDecision(flags.get(1), ReviewDecision.REVIEWED);
        remarks = new CheatingRemarks(backend).remarksFor(run);
        check(DAPHNE_REMARK.equals(remarks.get(DAPHNE)),
                "daphne's remark names elijah: " + remarks.get(DAPHNE));
        check("Cheated with DAPHNE TAN (daphne.tan.2025)".equals(
                remarks.get(ELIJAH)), "elijah's remark names daphne: "
                + remarks.get(ELIJAH));
    }

    // The activity log says Kept flagged for a Similar code flag kept.
    private static void checkAudit(LiveBackend backend) {
        boolean kept = false;
        boolean checked = false;
        for (AuditEntry entry : backend.listAuditEntries()) {
            kept = kept || ReviewDecision.AUDIT_KEPT_FLAGGED.equals(
                    entry.getAction());
            checked = checked || ReviewDecision.AUDIT_CHECKED.equals(
                    entry.getAction());
        }
        check(kept, "the activity log has Kept flagged");
        check(!checked, "the activity log has no Marked as checked for a "
                + "Similar code flag");
    }

    // An export of daphne and fiona gives daphne the same remark as an
    // export of everyone, and elijah, not exported, no remark.
    private static void checkSubsetExport(LiveBackend backend,
            GradingRun run, Path root) throws Exception {
        ActionResult whole = backend.export(run, run.getSubmissions(), CSV);
        check(whole.isDone(), "the whole run exports: "
                + whole.getMessage());
        String wholeText = readExport(root, whole);
        check(lineOf(wholeText, DAPHNE).contains(DAPHNE_REMARK),
                "the whole export gives daphne her remark: "
                + lineOf(wholeText, DAPHNE));

        List<Submission> scope = new ArrayList<>();
        scope.add(run.findLoaded(DAPHNE));
        scope.add(run.findLoaded(FIONA));
        ActionResult part = backend.export(run, scope, CSV);
        check(part.isDone(), "the chosen students export: "
                + part.getMessage());
        String partText = readExport(root, part);
        check(lineOf(partText, DAPHNE).contains(DAPHNE_REMARK),
                "the export of only some students gives daphne the same "
                + "remark with elijah's name: " + lineOf(partText, DAPHNE));
        check(!lineOf(partText, FIONA).contains("cheating")
                && !lineOf(partText, FIONA).contains("Cheated"),
                "fiona, whose decisions do not agree, has no remark: "
                + lineOf(partText, FIONA));
        check(!lineOf(partText, ELIJAH).contains("Cheated"),
                "elijah, not exported, has no remark in that export: "
                + lineOf(partText, ELIJAH));
    }

    // Reads the file an export wrote.
    private static String readExport(Path root, ActionResult result)
            throws Exception {
        Path written = result.getPath();
        if (written == null) {
            return "";
        }
        return new String(Files.readAllBytes(written),
                StandardCharsets.UTF_8);
    }

    // Returns the line of a CSV that names a username, or "".
    private static String lineOf(String csv, String username) {
        String found = "";
        for (String line : csv.split("\r?\n")) {
            if (found.isEmpty() && line.contains(username + ",")) {
                found = line;
            }
        }
        return found;
    }

    // Builds a graded run of four named students, with a score sheet.
    private static GradingRun makeRun(Path root) throws Exception {
        Assessment assessment = new Assessment("Lab Test 9", "CS102");
        assessment.addQuestion(new Question(QUESTION, QUESTION,
                QUESTION + ".java", 1));
        Section section = new Section("CS102", "G9");
        GradingRun run = new GradingRun(RUN_ID, assessment, section,
                LocalDateTime.now());
        String sheet = "OrgDefinedId,Username,Last Name,First Name,Email,"
                + "Calculated Final Grade Numerator,"
                + "Calculated Final Grade Denominator,End-of-Line Indicator\n";
        String[][] students = {{DAPHNE, "TAN", "DAPHNE"},
            {ELIJAH, "WEE", "ELIJAH"}, {FIONA, "LIM", "FIONA"},
            {GUS, "ONG", "GUS"}};
        for (int i = 0; i < students.length; i++) {
            String username = students[i][0];
            Submission submission = new Submission(username, section,
                    root.resolve(username));
            submission.setStatus(SubmissionStatus.GRADED);
            submission.setStudent(new Student("#0" + i, username,
                    students[i][1], students[i][2], username + "@x"));
            run.addSubmission(submission);
            sheet = sheet + "#0" + i + ",#" + username + ","
                    + students[i][1] + "," + students[i][2] + ","
                    + username + "@x,,1,#\n";
        }
        Path sheetFile = root.resolve("sheet.csv");
        Files.write(sheetFile, sheet.getBytes(StandardCharsets.UTF_8));
        run.setScoreSheetFile(sheetFile);
        return run;
    }

    // Adds both students' Similar code flags about each other, worded as
    // PlagiarismService writes them, and returns them, first's first.
    private static List<Anomaly> flagPair(GradingRun run, String first,
            String second, boolean identical) {
        List<Anomaly> flags = new ArrayList<>();
        String[] users = {first, second};
        for (int i = 0; i < users.length; i++) {
            String me = users[i];
            String other = users[users.length - 1 - i];
            String message = QUESTION + " is 95% similar to " + other
                    + ". This is a review aid, not a finding.";
            if (identical) {
                message = message + " Identical copy: both files have the "
                        + "same Name, Email ID and code.";
            }
            Anomaly anomaly = new Anomaly("SIMILAR-" + RUN_ID + "/" + me
                    + "/" + other + "/" + QUESTION,
                    AnomalyType.SIMILAR_CODE, Severity.INFO, me, message,
                    "Compare");
            run.findLoaded(me).addAnomaly(anomaly);
            flags.add(anomaly);
        }
        return flags;
    }

    // Counts a passed check, or stops with the reason it failed.
    private static void check(boolean passed, String what) {
        if (!passed) {
            throw new AssertionError("Failed: " + what);
        }
        checks++;
    }
}
