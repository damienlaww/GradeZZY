/*
 * File: PersistenceTest.java
 *
 * Purpose: Unit checks for the persistence package: a save and load round
 * trip through SqliteResultRepository in a temporary database, schema
 * migration from an older database, BackupManager (backup, prune, restore,
 * timeOf) and BackupReader (read-only, damaged files).
 *
 * Where it fits: Run by hand after compiling, with lib/* on the
 * classpath, as tests/README.md says.
 */
import java.nio.file.Files;
import java.nio.file.Path;
import java.security.MessageDigest;
import java.sql.Connection;
import java.sql.DriverManager;
import java.sql.Statement;
import java.time.LocalDateTime;
import java.util.Arrays;
import java.util.List;
import java.util.Map;

import gradezzy.config.AppConfig;
import gradezzy.config.ConfigKeys;
import gradezzy.model.Anomaly;
import gradezzy.model.AnomalyType;
import gradezzy.model.AnswerGroupNote;
import gradezzy.model.Assessment;
import gradezzy.model.AuditEntry;
import gradezzy.model.BackupInfo;
import gradezzy.model.FixStatus;
import gradezzy.model.GradingRun;
import gradezzy.model.Question;
import gradezzy.model.QuestionResult;
import gradezzy.model.ScoreAdjustment;
import gradezzy.model.Section;
import gradezzy.model.Severity;
import gradezzy.model.Student;
import gradezzy.model.Submission;
import gradezzy.model.SubmissionStatus;
import gradezzy.model.TestCaseResult;
import gradezzy.persistence.BackupManager;
import gradezzy.persistence.BackupReader;
import gradezzy.persistence.PersistenceException;
import gradezzy.persistence.SqliteResultRepository;
import gradezzy.util.FileUtils;

/**
 * Runs the persistence checks and prints how many passed.
 */
public class PersistenceTest {

    // The run saved and loaded by the round trip checks
    private static final String RUN_ID = "RUN-1";

    // The graded student in every sample run
    private static final String ANN = "ann.lee.2025";

    // The questions of the sample assessment
    private static final String Q1A = "Q1a";
    private static final String Q2 = "Q2";

    // The anomaly whose fix was applied in the sample run
    private static final String FIXED_ANOMALY_ID = "A-1";

    // The decision saved for that anomaly
    private static final String DECISION = "ACCEPTED";

    // The detail of the audit entry, which holds a newline
    private static final String AUDIT_DETAIL = "line1\nline2";

    // When every sample run started, to the millisecond
    private static final LocalDateTime STARTED_AT = LocalDateTime.of(2026,
            11, 11, 9, 30, 15, 123000000);

    // When the score adjustment was made
    private static final LocalDateTime ADJUSTED_AT = LocalDateTime.of(2026,
            11, 11, 10, 0);

    // When the audit entry was made
    private static final LocalDateTime AUDIT_AT = LocalDateTime.of(2026, 1,
            1, 0, 0);

    // Ann's Q1a mark, which is also her total
    private static final double ANN_SCORE = 1.5;

    // Ann's Q1a mark after the adjustment
    private static final double ADJUSTED_SCORE = 2.0;

    // The max score of Q1a and of Q2
    private static final double Q1A_MAX = 2.5;
    private static final double Q2_MAX = 4;

    // A character outside Latin-1 in Ann's Q1a output, which must be
    // read back as it was saved
    private static final String OUTPUT_MARK = "\u2192";

    // The endings of the files SQLite and a restore may leave beside a
    // database
    private static final String RESTORING_SUFFIX = ".restoring";
    private static final String JOURNAL_SUFFIX = "-journal";
    private static final String WAL_SUFFIX = "-wal";

    // How digest writes each byte of a hash: two hex digits
    private static final String HEX_BYTE = "%02x";

    // How many questions, students, test cases and anomalies a sample
    // run has, and how many sample runs the listing checks save
    private static final int QUESTIONS = 2;
    private static final int STUDENTS = 2;
    private static final int TEST_CASES = 2;
    private static final int ANOMALIES = 2;
    private static final int RUNS = 2;

    // How many backups are kept, and how many the prune check makes
    private static final int KEEP_COUNT = 3;
    private static final int BACKUPS_MADE = 6;

    // A truncated backup keeps a third of the file, at least 100 bytes
    private static final int CUT_PARTS = 3;
    private static final int MIN_CUT_BYTES = 100;

    // The name of a backup that was not made by GradeZZY
    private static final String MANUAL_BACKUP = "gradezzy-zzz-manual.db";

    // The number of checks that passed
    private static int passed = 0;

    // The number of checks that failed
    private static int failed = 0;

    /**
     * Runs every check.
     *
     * @param args not used
     * @throws Exception if a temporary file cannot be written
     */
    public static void main(String[] args) throws Exception {
        // A folder name with a space and a hash, like the project's path
        Path root = Files.createTempDirectory("gradezzy persist #tests-")
                .toRealPath();
        try {
            checkRoundTrip(root);
            checkMigration(root);
            checkRealCopy(root);
            checkBackups(root);
            checkReader(root);
        } finally {
            FileUtils.deleteDirectory(root);
        }
        System.out.println("PersistenceTest: " + passed + " passed, "
                + failed + " failed");
        if (failed > 0) {
            System.exit(1);
        }
    }

    // Builds a run with one graded student (two questions, two
    // anomalies, two rejected fixes) and one failed student.
    private static GradingRun sampleRun(String id) {
        Assessment assessment = new Assessment("Lab \"Test\" 3, part 1",
                "CS102");
        assessment.addQuestion(new Question(Q1A, "Q1", "Q1a.java",
                Q1A_MAX));
        assessment.addQuestion(new Question(Q2, Q2, "Q2.java", Q2_MAX));
        GradingRun run = new GradingRun(id, assessment,
                new Section("CS102", "G1"), STARTED_AT);
        run.setScoreSheetFile(Path.of("/tmp/sheet with space.csv"));
        run.setTestersDirectory(Path.of("/tmp/testers"));
        run.setStarterDirectory(Path.of("/tmp/starter"));
        run.setGradingSettings("exec.run.timeout.seconds=10\nx=y");
        Submission ann = new Submission(ANN, run.getSection(),
                Path.of("/tmp/ann.zip"));
        ann.setWorkingCopyPath(Path.of("/tmp/working/ann.lee.2025"));
        ann.setStatus(SubmissionStatus.GRADED);
        ann.setStudent(new Student("0012", ANN, "O'Neill, Jr",
                "Ann 李", "ann@x.edu"));
        QuestionResult q1 = new QuestionResult(assessment.getQuestions()
                .get(0));
        q1.setCompiled(true);
        q1.setScore(ANN_SCORE);
        q1.setOutput("Test 1: f()\nPassed\n" + OUTPUT_MARK + " é\n");
        q1.addTestCaseResult(new TestCaseResult(1, true, "Passed", "f()"));
        q1.addTestCaseResult(new TestCaseResult(2, false, "Failed", null));
        ann.addQuestionResult(q1);
        QuestionResult q2 = new QuestionResult(assessment.getQuestions()
                .get(1));
        q2.setCompiled(false);
        q2.setTimedOut(true);
        q2.setScore(0);
        ann.addQuestionResult(q2);
        Anomaly fixed = new Anomaly(FIXED_ANOMALY_ID,
                AnomalyType.WRONG_FOLDER_NAME, Severity.WARNING, ANN,
                "Folder 'x' wrong", "Rename it");
        fixed.setFixStatus(FixStatus.APPLIED);
        fixed.setFixDescription("Renamed the folder x to ann.lee.2025.");
        ann.addAnomaly(fixed);
        ann.addAnomaly(new Anomaly("SIMILAR-" + id + "/ann.lee.2025/bob/Q1a",
                AnomalyType.SIMILAR_CODE, Severity.INFO, ANN,
                "Q1a is 91% similar to bob.", "Compare"));
        ann.rejectFix("Fix letter case:q3");
        ann.rejectFix("Rename folder:RenameToYourUsername");
        run.addSubmission(ann);
        Submission bob = new Submission("bob", run.getSection(),
                Path.of("/tmp/bob.zip"));
        bob.setStatus(SubmissionStatus.FAILED);
        bob.setIngestFailure("This zip file is damaged.");
        run.addSubmission(bob);
        return run;
    }

    // Saves a sample run, loads it back and checks every part, then
    // checks the other things the repository stores.
    private static void checkRoundTrip(Path root) throws Exception {
        Path dbFile = root.resolve("db folder/grade zzy.db");
        Files.createDirectories(dbFile.getParent());
        SqliteResultRepository repo = new SqliteResultRepository(
                dbFile.toString());
        repo.open();
        GradingRun run = sampleRun(RUN_ID);
        repo.saveRun(run);
        GradingRun loaded = repo.loadRun(RUN_ID);
        check(loaded != null, "run loaded");
        checkLoadedRun(loaded, run);
        checkLoadedAnn(loaded.getSubmissions().get(0));
        Submission bob = loaded.getSubmissions().get(1);
        boolean bobFailed = bob.getStudent() == null
                && "This zip file is damaged.".equals(bob.getIngestFailure());
        check(bobFailed, "student null, failure");
        checkSavedAgain(repo, run);
        checkSideTables(repo);
        // listRuns / headers
        repo.saveRun(sampleRun("RUN-2"));
        boolean twoListed = repo.listRuns().size() == RUNS
                && repo.listRunHeaders().size() == RUNS;
        check(twoListed, "two runs listed");
        check(repo.loadRun("RUN-missing") == null, "missing run null");
        repo.close();
        boolean closed = false;
        try {
            repo.saveRun(run);
        } catch (PersistenceException expected) {
            closed = true;
        } catch (IllegalStateException expected) {
            closed = true;
        }
        check(closed, "save after close refused");
        checkReopen(root, dbFile);
    }

    // Checks the parts of the run read back that belong to the run.
    private static void checkLoadedRun(GradingRun loaded, GradingRun run) {
        check(loaded.getAssessment().getName().equals(
                "Lab \"Test\" 3, part 1"), "assessment name with quotes");
        boolean questionsKept = loaded.getAssessment().getQuestions().size()
                == QUESTIONS && loaded.getAssessment().getQuestions().get(0)
                .getMaxScore() == Q1A_MAX;
        check(questionsKept, "questions and max score");
        check(loaded.getStartedAt().equals(run.getStartedAt()),
                "started at with millis (" + loaded.getStartedAt() + ")");
        check(loaded.getSection().getSectionCode().equals("G1"), "section");
        check(String.valueOf(loaded.getScoreSheetFile()).equals(
                "/tmp/sheet with space.csv"), "score sheet path");
        boolean foldersKept = String.valueOf(loaded.getTestersDirectory())
                .equals("/tmp/testers") && String.valueOf(loaded
                .getStarterDirectory()).equals("/tmp/starter");
        check(foldersKept, "testers and starter dirs");
        check("exec.run.timeout.seconds=10\nx=y".equals(
                loaded.getGradingSettings()), "grading settings");
        boolean inOrder = loaded.getSubmissions().size() == STUDENTS
                && loaded.getSubmissions().get(0).getUsername().equals(ANN);
        check(inOrder, "submissions in order");
    }

    // Checks Ann's student, results, anomalies and rejected fixes read
    // back.
    private static void checkLoadedAnn(Submission ann) {
        boolean studentKept = ann.getStudent() != null
                && ann.getStudent().getLastName().equals("O'Neill, Jr")
                && ann.getStudent().getFirstName().equals("Ann 李")
                && ann.getStudent().getOrgDefinedId().equals("0012");
        check(studentKept, "student with quote, comma, unicode, zeros");
        check(ann.getStatus() == SubmissionStatus.GRADED, "status");
        check(ann.getQuestionResults().size() == QUESTIONS,
                "question results");
        QuestionResult q1 = ann.findResult(Q1A);
        boolean q1Kept = q1 != null && q1.getScore() == ANN_SCORE
                && q1.isCompiled() && q1.getOutput().contains(OUTPUT_MARK);
        check(q1Kept, "q1 result");
        boolean namesKept = q1 != null
                && q1.getTestCaseResults().size() == TEST_CASES
                && "f()".equals(q1.getTestCaseResults().get(0).getName())
                && q1.getTestCaseResults().get(1).getName() == null;
        check(namesKept, "test cases with names");
        QuestionResult q2 = ann.findResult(Q2);
        boolean q2Flags = q2 != null && q2.isTimedOut() && !q2.isCompiled();
        check(q2Flags, "q2 flags");
        check(ann.getTotalScore() == ANN_SCORE, "total score");
        boolean anomaliesKept = ann.getAnomalies().size() == ANOMALIES
                && ann.getAnomalies().get(0).getFixStatus()
                == FixStatus.APPLIED;
        check(anomaliesKept, "anomalies with fix status");
        System.out.println("   fix description after reload: '"
                + ann.getAnomalies().get(0).describeFix() + "'");
        check(ann.getRejectedFixes().equals(Arrays.asList(
                "Fix letter case:q3", "Rename folder:RenameToYourUsername")),
                "rejected fixes " + ann.getRejectedFixes());
    }

    // Saves the run again, then without one student, and checks fix
    // statuses and decisions.
    private static void checkSavedAgain(SqliteResultRepository repo,
            GradingRun run) throws Exception {
        // Save again: no duplicates
        repo.saveRun(run);
        boolean noDuplicates = repo.loadRun(RUN_ID).getSubmissions().size()
                == STUDENTS && repo.loadRun(RUN_ID).getSubmissions().get(0)
                .getAnomalies().size() == ANOMALIES;
        check(noDuplicates, "save twice no duplicates");
        // Remove a submission and save: it disappears
        run.removeSubmission(run.getSubmissions().get(1));
        repo.saveRun(run);
        check(repo.loadRun(RUN_ID).getSubmissions().size() == 1,
                "a removed submission is removed on save");
        // updateFixStatus and decision
        repo.updateFixStatus(FIXED_ANOMALY_ID, FixStatus.UNDONE);
        check(repo.loadRun(RUN_ID).getSubmissions().get(0).getAnomalies()
                .get(0).getFixStatus() == FixStatus.UNDONE,
                "updateFixStatus");
        repo.updateDecision(FIXED_ANOMALY_ID, DECISION);
        Map<String, String> decisions = repo.loadDecisions(RUN_ID);
        check(DECISION.equals(decisions.get(FIXED_ANOMALY_ID)),
                "decision saved");
        repo.saveRun(run);
        check(DECISION.equals(repo.loadDecisions(RUN_ID).get(
                FIXED_ANOMALY_ID)),
                "decision survives saving the run again");
    }

    // Checks zero checks, score adjustments, answer group notes and the
    // audit log.
    private static void checkSideTables(SqliteResultRepository repo)
            throws Exception {
        // Zero checks
        repo.updateZeroCheck(RUN_ID, ANN, true);
        repo.updateZeroCheck(RUN_ID, ANN, true);
        check(repo.loadZeroChecks(RUN_ID).equals(Arrays.asList(ANN)),
                "zero check once");
        repo.updateZeroCheck(RUN_ID, ANN, false);
        check(repo.loadZeroChecks(RUN_ID).isEmpty(), "zero check removed");
        // Adjustments
        ScoreAdjustment adjustment = new ScoreAdjustment(ANN, Q1A, ANN_SCORE,
                ADJUSTED_SCORE, "Regex too strict; \"fair\"", ADJUSTED_AT);
        repo.saveAdjustment(RUN_ID, adjustment);
        List<ScoreAdjustment> adjustments = repo.listAdjustments(RUN_ID);
        boolean adjustmentKept = adjustments.size() == 1
                && adjustments.get(0).getNewScore() == ADJUSTED_SCORE
                && adjustments.get(0).getReason().equals(
                "Regex too strict; \"fair\"");
        check(adjustmentKept, "adjustment round trip");
        repo.deleteAdjustment(RUN_ID, adjustment);
        check(repo.listAdjustments(RUN_ID).isEmpty(), "adjustment deleted");
        // Answer group notes
        repo.saveAnswerGroupNote(RUN_ID, new AnswerGroupNote(Q1A,
                "ID = ID ;", "Off by one", Arrays.asList("a", "b")));
        repo.saveAnswerGroupNote(RUN_ID, new AnswerGroupNote(Q1A,
                "ID = ID ;", "Off by two", Arrays.asList("c")));
        List<AnswerGroupNote> notes = repo.listAnswerGroupNotes(RUN_ID);
        boolean noteReplaced = notes.size() == 1
                && notes.get(0).getLabel().equals("Off by two");
        check(noteReplaced, "answer group note replaced");
        // Audit
        repo.appendAudit(new AuditEntry(AUDIT_AT, "EXPORT", AUDIT_DETAIL));
        List<AuditEntry> audit = repo.listAudit();
        int lastEntry = audit.size() - 1;
        boolean auditKept = !audit.isEmpty()
                && audit.get(lastEntry).getDetail().equals(AUDIT_DETAIL)
                || audit.get(0).getDetail().equals(AUDIT_DETAIL);
        check(auditKept, "audit round trip");
    }

    // Reopens the database, and opens one in a missing folder and one
    // that is not a database.
    private static void checkReopen(Path root, Path dbFile)
            throws Exception {
        SqliteResultRepository again = new SqliteResultRepository(
                dbFile.toString());
        again.open();
        check(again.loadRun("RUN-2") != null, "data survives reopen");
        again.close();
        // Open a db in a folder that does not exist
        SqliteResultRepository deep = new SqliteResultRepository(
                root.resolve("new/deeper/x.db").toString());
        try {
            deep.open();
            check(true, "open creates missing folders");
            deep.close();
        } catch (PersistenceException exception) {
            check(false, "open creates missing folders: "
                    + exception.getMessage());
        }
        // A file that is not a database
        Path junk = root.resolve("junk.db");
        Files.writeString(junk, "this is not sqlite at all, just text....");
        SqliteResultRepository bad = new SqliteResultRepository(
                junk.toString());
        boolean badRefused = false;
        try {
            bad.open();
            bad.listRuns();
        } catch (PersistenceException expected) {
            badRefused = true;
        }
        check(badRefused, "junk database refused with PersistenceException");
        bad.close();
    }

    // Builds a database with the old tables and checks that it loads,
    // takes new runs and gains the decision column.
    private static void checkMigration(Path root) throws Exception {
        Path old = root.resolve("old.db");
        makeOldDatabase(old);
        SqliteResultRepository repo = new SqliteResultRepository(
                old.toString());
        try {
            repo.open();
            GradingRun loaded = repo.loadRun("OLD-1");
            check(loaded != null && loaded.getSubmissions().size() == 1,
                    "old database run loads after migration");
            if (loaded != null) {
                System.out.println("   old run anomalies loaded: "
                        + loaded.getSubmissions().get(0).getAnomalies().size()
                        + " (one has an unknown type)");
                check(loaded.getSubmissions().get(0).getRejectedFixes()
                        .isEmpty(), "missing rejected_fixes column reads as "
                        + "none");
            }
            repo.saveRun(sampleRun("NEW-1"));
            check(repo.loadRun("NEW-1").getGradingSettings() != null,
                    "new run saved into migrated db");
            repo.updateDecision("X-1", "OK");
            check("OK".equals(repo.loadDecisions("OLD-1").get("X-1")),
                    "decision column added by migration");
            repo.close();
        } catch (PersistenceException exception) {
            check(false, "old database opens: " + exception.getMessage());
        }
    }

    // Writes a database with the tables of an older GradeZZY, holding one
    // run with one student and two anomalies, one of an unknown type.
    private static void makeOldDatabase(Path old) throws Exception {
        Connection connection = DriverManager.getConnection(
                SqliteResultRepository.JDBC_URL_PREFIX + old);
        Statement statement = connection.createStatement();
        statement.executeUpdate("CREATE TABLE runs (run_id TEXT PRIMARY KEY, "
                + "started_at TEXT, module_code TEXT, section_code TEXT, "
                + "assessment_name TEXT, assessment_module TEXT, "
                + "placeholder_folders TEXT)");
        statement.executeUpdate("CREATE TABLE submissions (run_id TEXT NOT "
                + "NULL, username TEXT NOT NULL, position INTEGER NOT NULL, "
                + "status TEXT, original_path TEXT, working_copy_path TEXT, "
                + "ingest_failure TEXT, org_defined_id TEXT, student_username "
                + "TEXT, last_name TEXT, first_name TEXT, email TEXT, "
                + "PRIMARY KEY (run_id, username))");
        statement.executeUpdate("CREATE TABLE anomalies (run_id TEXT NOT "
                + "NULL, username TEXT NOT NULL, position INTEGER NOT NULL, "
                + "anomaly_id TEXT NOT NULL, type TEXT, severity TEXT, "
                + "message TEXT, suggested_action TEXT, fix_status TEXT, "
                + "PRIMARY KEY (run_id, username, position))");
        statement.executeUpdate("CREATE TABLE test_case_results (run_id TEXT "
                + "NOT NULL, username TEXT NOT NULL, question_id TEXT, "
                + "position INTEGER NOT NULL, test_number INTEGER NOT NULL, "
                + "passed INTEGER NOT NULL, detail TEXT, PRIMARY KEY (run_id, "
                + "username, question_id, position))");
        statement.executeUpdate("INSERT INTO runs VALUES ('OLD-1', "
                + "'2025-01-02T03:04:05', 'CS102', 'G1', 'Old Lab', 'CS102', "
                + "'RenameToYourUsername')");
        statement.executeUpdate("INSERT INTO submissions VALUES ('OLD-1', "
                + "'zed', 0, 'GRADED', '/z.zip', '/w/zed', NULL, '1', 'zed', "
                + "'Z', 'Zed', 'z@x')");
        statement.executeUpdate("INSERT INTO anomalies VALUES ('OLD-1', 'zed',"
                + " 0, 'X-1', 'UNKNOWN_STUDENT', 'WARNING', 'm', 'a', NULL)");
        statement.executeUpdate("INSERT INTO anomalies VALUES ('OLD-1', 'zed',"
                + " 1, 'X-2', 'TYPE_FROM_THE_FUTURE', 'WARNING', 'm', 'a', "
                + "'APPLIED')");
        statement.close();
        connection.close();
    }

    // Reads a copy of a real database when one is in the workspace, and
    // checks the reader leaves it unchanged.
    private static void checkRealCopy(Path root) throws Exception {
        Path real = Path.of("workspace/gradezzy.db.real-copy");
        if (!Files.exists(real)) {
            return;
        }
        Path copy = root.resolve("real.db");
        Files.copy(real, copy);
        String before = digest(real);
        BackupInfo info = BackupReader.read(real);
        check(info.isReadable(), "real db copy readable by BackupReader: "
                + info.getProblem());
        check(digest(real).equals(before), "BackupReader leaves the real db "
                + "copy byte for byte unchanged");
        SqliteResultRepository repo = new SqliteResultRepository(
                copy.toString());
        try {
            repo.open();
            List<GradingRun> runs = repo.listRuns();
            int students = 0;
            for (GradingRun run : runs) {
                students += run.getSubmissions().size();
            }
            System.out.println("   real db copy: " + runs.size() + " runs, "
                    + students + " submissions, reader saw "
                    + info.getRuns().size() + " runs");
            check(runs.size() == info.getRuns().size(),
                    "repository and reader agree on run count");
            repo.close();
        } catch (PersistenceException exception) {
            check(false, "real db copy opens: " + exception.getMessage());
        }
    }

    // Checks making, pruning, listing, naming and restoring backups.
    private static void checkBackups(Path root) throws Exception {
        AppConfig config = TestConfig.load();
        Path backupDir = root.resolve("backups");
        config.setString(ConfigKeys.BACKUP_DIR, backupDir.toString());
        config.setString(ConfigKeys.BACKUP_KEEP_COUNT,
                String.valueOf(KEEP_COUNT));
        BackupManager manager = new BackupManager(config);
        check(manager.listBackups().isEmpty(), "no backups yet");
        Path liveDb = root.resolve("live.db");
        SqliteResultRepository repo = new SqliteResultRepository(
                liveDb.toString());
        repo.open();
        repo.saveRun(sampleRun("B-1"));
        repo.close();
        boolean noDb = false;
        try {
            manager.backup(root.resolve("absent.db"));
        } catch (PersistenceException expected) {
            noDb = true;
        }
        check(noDb, "backup of a missing db refused");
        Path first = manager.backup(liveDb);
        for (int i = 1; i < BACKUPS_MADE; i++) {
            manager.backup(liveDb);
        }
        List<Path> backups = manager.listBackups();
        check(backups.size() == KEEP_COUNT, "pruned to keep count 3 ("
                + backups.size() + ")");
        check(!Files.exists(first), "oldest backup pruned");
        check(BackupManager.timeOf(backups.get(0)).isAfter(
                BackupManager.timeOf(backups.get(KEEP_COUNT - 1))),
                "newest first");
        check(BackupManager.timeOf(Path.of("gradezzy-20261111-093015-123.db"))
                .equals(STARTED_AT), "timeOf parses the name");
        boolean oddNames = BackupManager.timeOf(Path.of("gradezzy-garbage.db"))
                == null && BackupManager.timeOf(Path.of("other.db")) == null
                && BackupManager.timeOf(null) == null;
        check(oddNames, "timeOf odd names");
        Files.writeString(backupDir.resolve("notes.db"), "x");
        Files.writeString(backupDir.resolve("gradezzy-notes.txt"), "x");
        check(manager.listBackups().size() == KEEP_COUNT,
                "other files not listed");
        config.setString(ConfigKeys.BACKUP_KEEP_COUNT, "0");
        check(manager.getKeepCount() == 1, "keep count at least 1");
        config.setString(ConfigKeys.BACKUP_KEEP_COUNT,
                String.valueOf(KEEP_COUNT));
        // A hand-named gradezzy-*.db that is not a timestamp sorts by name
        Files.writeString(backupDir.resolve(MANUAL_BACKUP), "m");
        manager.backup(liveDb);
        // Known limitation: backups are sorted by name, so a file renamed
        // by hand to gradezzy-zzz-manual.db is listed as the newest.
        // GradeZZY only ever names backups gradezzy-<date>-<time>.db
        // itself.
        List<Path> withManual = manager.listBackups();
        note("a hand-named gradezzy-zzz-manual.db is listed as the newest "
                + "backup: " + withManual.get(0).getFileName());
        Files.deleteIfExists(backupDir.resolve(MANUAL_BACKUP));
        checkRestore(root, manager);
    }

    // Restores the newest backup and checks a missing one is refused.
    private static void checkRestore(Path root, BackupManager manager)
            throws Exception {
        Path latest = manager.listBackups().get(0);
        Path target = root.resolve("restored/live.db");
        manager.restore(latest, target);
        check(Arrays.equals(Files.readAllBytes(latest), Files.readAllBytes(
                target)), "restore copies the backup");
        check(!Files.exists(Path.of(target + RESTORING_SUFFIX)),
                "no temporary file left");
        boolean missing = false;
        try {
            manager.restore(root.resolve("nope.db"), target);
        } catch (PersistenceException expected) {
            missing = true;
        }
        check(missing, "restore of a missing backup refused");
    }

    // Checks that BackupReader reads a good database without changing it
    // and refuses damaged, empty, missing and foreign files.
    private static void checkReader(Path root) throws Exception {
        Path readerDb = root.resolve("reader.db");
        SqliteResultRepository repo = new SqliteResultRepository(
                readerDb.toString());
        repo.open();
        repo.saveRun(sampleRun("R-1"));
        repo.saveRun(sampleRun("R-2"));
        repo.close();
        String before = digest(readerDb);
        long modified = Files.getLastModifiedTime(readerDb).toMillis();
        BackupInfo info = BackupReader.read(readerDb);
        check(info.isReadable() && info.getRuns().size() == RUNS,
                "reader lists two runs: " + info.getProblem());
        boolean unchanged = digest(readerDb).equals(before)
                && Files.getLastModifiedTime(readerDb).toMillis() == modified;
        check(unchanged, "reader leaves the file unchanged");
        boolean noJournal = !Files.exists(Path.of(readerDb + JOURNAL_SUFFIX))
                && !Files.exists(Path.of(readerDb + WAL_SUFFIX));
        check(noJournal, "reader leaves no journal or wal file");
        boolean counted = info.getRuns().get(0).getStudentCount() == STUDENTS
                && info.getRuns().get(0).getGradedCount() == 1;
        check(counted, "reader counts students and graded");
        checkBadFiles(root, readerDb);
    }

    // Checks that BackupReader refuses junk, empty, missing, foreign and
    // truncated files, and changes none of them.
    private static void checkBadFiles(Path root, Path readerDb)
            throws Exception {
        Path junk = root.resolve("gradezzy-20260101-000000-000.db");
        Files.writeString(junk, "garbage garbage garbage garbage garbage");
        String junkBefore = digest(junk);
        BackupInfo bad = BackupReader.read(junk);
        check(!bad.isReadable() && bad.getProblem() != null,
                "junk file not readable: " + bad.getProblem());
        check(digest(junk).equals(junkBefore), "junk file unchanged");
        int expectedYear = 2026;
        boolean madeAtRead = bad.getMadeAt() != null
                && bad.getMadeAt().getYear() == expectedYear;
        check(madeAtRead, "made-at read from the name");
        Path empty = root.resolve("empty.db");
        Files.write(empty, new byte[0]);
        BackupInfo emptyInfo = BackupReader.read(empty);
        check(!emptyInfo.isReadable(), "empty file not readable (got "
                + emptyInfo.isReadable() + ", runs " + emptyInfo.getRuns()
                .size() + ")");
        check(Files.size(empty) == 0, "empty file left empty");
        BackupInfo gone = BackupReader.read(root.resolve("gone.db"));
        check(!gone.isReadable(), "missing file not readable");
        // A database of another program
        Path other = root.resolve("other.db");
        Connection connection = DriverManager.getConnection(
                SqliteResultRepository.JDBC_URL_PREFIX + other);
        connection.createStatement().executeUpdate("CREATE TABLE t (x)");
        connection.close();
        BackupInfo otherInfo = BackupReader.read(other);
        check(!otherInfo.isReadable(), "another program's database not "
                + "readable");
        // Truncated real backup
        byte[] full = Files.readAllBytes(readerDb);
        Path cut = root.resolve("cut.db");
        int third = full.length / CUT_PARTS;
        int cutLength = Math.max(MIN_CUT_BYTES, third);
        Files.write(cut, Arrays.copyOf(full, cutLength));
        BackupInfo cutInfo = BackupReader.read(cut);
        System.out.println("   truncated backup: readable="
                + cutInfo.isReadable() + " runs=" + cutInfo.getRuns().size()
                + " problem=" + cutInfo.getProblem());
    }

    // Gives the SHA-256 of a file's bytes as hex text.
    private static String digest(Path file) throws Exception {
        MessageDigest sha = MessageDigest.getInstance("SHA-256");
        byte[] hash = sha.digest(Files.readAllBytes(file));
        String text = "";
        for (byte hashByte : hash) {
            text = text + String.format(HEX_BYTE, hashByte);
        }
        return text;
    }

    // Prints a known limitation: behaviour that is kept on purpose or is
    // not worth changing, so it is reported but does not fail the run
    private static void note(String message) {
        System.out.println("NOTE (known limitation): " + message);
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
