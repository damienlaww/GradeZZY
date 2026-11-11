/*
 * File: BackupRestoreTest.java
 *
 * Purpose: Checks that restoring a file that is not a GradeZZY backup is
 * refused and changes nothing, that a real backup restores and can be
 * undone, and that the copies made before a restore never push out the
 * backups made with Back up now.
 *
 * Where it fits: Run by hand after compiling, as tests/README.md says.
 * It works on a copy of the fixed test settings, a temporary database and a
 * temporary backup folder, so no real setting or result is changed.
 */
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.sql.Connection;
import java.sql.DriverManager;
import java.sql.PreparedStatement;
import java.sql.Statement;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;

import gradezzy.config.AppConfig;
import gradezzy.config.ConfigKeys;
import gradezzy.model.BackupInfo;
import gradezzy.persistence.BackupManager;
import gradezzy.persistence.PersistenceException;
import gradezzy.persistence.SqliteResultRepository;
import gradezzy.service.AuditService;
import gradezzy.service.HistoryService;
import gradezzy.util.FileUtils;

/**
 * Runs the backup restore checks and prints how many passed. Compile:
 * javac -cp classes -d /tmp/gradezzy-tests tests/*.java; run:
 * java -cp "classes:lib/*:/tmp/gradezzy-tests" BackupRestoreTest
 */
public class BackupRestoreTest {

    // How many backups made with Back up now are kept
    private static final int KEEP_COUNT = 3;

    // A lower limit, set later to check the oldest are deleted
    private static final int LOWER_KEEP_COUNT = 2;

    // How many restores are made to fill the copies made before one
    private static final int EXTRA_RESTORES = 4;

    // The address of a SQLite file
    private static final String JDBC = "jdbc:sqlite:";

    // Adds a saved run with only its id
    private static final String INSERT_RUN =
            "INSERT INTO runs (run_id, started_at) VALUES (?, ?)";

    // The start time of every run added here
    private static final String STARTED_AT = "2026-11-11T10:00:00";

    // The start of the id of every run added here
    private static final String RUN_ID_PREFIX = "run-";

    // The ending of the file a restore writes before it replaces the
    // database
    private static final String RESTORING_SUFFIX = ".restoring";

    // The number of checks passed so far
    private static int checks = 0;

    /**
     * Runs every check in a temporary folder, then removes the folder.
     *
     * @param args not used
     * @throws Exception if a check fails or a file cannot be written
     */
    public static void main(String[] args) throws Exception {
        Path root = Files.createTempDirectory("gradezzy-backup-tests-")
                .toRealPath();
        try {
            AppConfig config = TestConfig.copyInto(root);
            Path databaseFile = root.resolve("gradezzy.db");
            Path backupFolder = root.resolve("backup");
            config.setString(ConfigKeys.DATABASE_ENABLED, "true");
            config.setString(ConfigKeys.DATABASE_FILE,
                    databaseFile.toString());
            config.setString(ConfigKeys.BACKUP_DIR, backupFolder.toString());
            config.setString(ConfigKeys.BACKUP_KEEP_COUNT,
                    String.valueOf(KEEP_COUNT));

            SqliteResultRepository repository = new SqliteResultRepository(
                    databaseFile.toString());
            repository.open();
            BackupManager manager = new BackupManager(config);
            HistoryService history = new HistoryService(config, repository,
                    manager, new AuditService(config, repository));

            List<Path> backups = makeBackups(repository, databaseFile,
                    history);
            checkRefused(root, config, repository, databaseFile, history,
                    manager);
            checkRestoreOldest(repository, history, manager, backups);
            checkCopiesKeptApart(config, history, manager);
            repository.close();
            System.out.println("Passed " + checks
                    + " backup restore checks.");
        } finally {
            FileUtils.deleteDirectory(root);
        }
    }

    // Makes KEEP_COUNT backups holding one, two and three runs, and leaves
    // the database with one more run. Returns the backups, oldest first.
    private static List<Path> makeBackups(SqliteResultRepository repository,
            Path databaseFile, HistoryService history) throws Exception {
        List<Path> backups = new ArrayList<>();
        for (int index = 1; index <= KEEP_COUNT; index++) {
            addRun(repository, databaseFile, RUN_ID_PREFIX + index);
            Path backup = history.backupNow();
            check(backup != null, "Back up now makes backup " + index);
            backups.add(backup);
        }
        int runCount = KEEP_COUNT + 1;
        addRun(repository, databaseFile, RUN_ID_PREFIX + runCount);
        check(history.listRuns().size() == runCount,
                "The database holds " + runCount + " runs");
        check(history.listBackups().size() == KEEP_COUNT,
                "Every backup is kept up to the limit");
        return backups;
    }

    // Checks that a text file, the settings file, an empty file and a
    // SQLite file of another program are refused and change nothing.
    private static void checkRefused(Path root, AppConfig config,
            SqliteResultRepository repository, Path databaseFile,
            HistoryService history, BackupManager manager)
            throws Exception {
        Path text = root.resolve("not-a-db.db");
        Files.writeString(text, "This is only some text, not a database.",
                StandardCharsets.UTF_8);
        Path empty = root.resolve("empty.db");
        Files.createFile(empty);
        Path foreign = root.resolve("other.db");
        writeForeignDatabase(foreign);
        Path settings = Path.of(config.getConfigFile());
        Path[] wrongFiles = {text, settings, empty, foreign};

        int runs = history.listRuns().size();
        List<Path> before = manager.listBackups();
        repository.close();
        byte[] liveBytes = Files.readAllBytes(databaseFile);
        repository.open();
        for (Path wrong : wrongFiles) {
            String name = wrong.getFileName().toString();
            check(!history.checkBackup(wrong).isEmpty(),
                    name + " is found not to be a backup");
            boolean refused = false;
            try {
                history.restore(wrong);
            } catch (IllegalStateException exception) {
                refused = exception.getMessage().contains("nothing was");
            }
            check(refused, name + " is refused with a plain reason");
            repository.close();
            check(Arrays.equals(liveBytes,
                    Files.readAllBytes(databaseFile)),
                    "Restoring " + name + " leaves the database as it was");
            repository.open();
            check(history.listRuns().size() == runs,
                    "The database still opens with its runs after " + name);
            check(manager.listBackups().equals(before),
                    "Restoring " + name + " makes no copy and deletes none");
            check(!Files.exists(Path.of(databaseFile + RESTORING_SUFFIX)),
                    "No half restored file is left after " + name);
        }

        // The manager checks the file too, before the database is touched
        repository.close();
        boolean managerRefused = false;
        try {
            manager.restore(text, databaseFile);
        } catch (PersistenceException exception) {
            managerRefused = true;
        }
        check(managerRefused, "BackupManager.restore refuses a text file");
        check(Arrays.equals(liveBytes,
                Files.readAllBytes(databaseFile)),
                "BackupManager.restore leaves the database as it was");
        repository.open();
    }

    // Writes a SQLite file of another program, with one table and row.
    private static void writeForeignDatabase(Path foreign)
            throws Exception {
        Connection connection = DriverManager.getConnection(JDBC + foreign);
        try {
            Statement statement = connection.createStatement();
            try {
                statement.execute("CREATE TABLE foo (bar TEXT)");
                statement.execute("INSERT INTO foo VALUES ('baz')");
            } finally {
                statement.close();
            }
        } finally {
            connection.close();
        }
    }

    // Restores the oldest kept backup: no backup is deleted, the copy made
    // before it does not count, and Undo puts the runs back.
    private static void checkRestoreOldest(
            SqliteResultRepository repository, HistoryService history,
            BackupManager manager, List<Path> backups) {
        int runsBefore = history.listRuns().size();
        Path oldest = backups.get(0);
        Path safety = history.restore(oldest);
        check(history.listRuns().size() == 1,
                "Restoring the oldest backup puts back its one run");
        check(safety != null && BackupInfo.isBeforeRestore(safety),
                "The copy made before the restore is named apart");
        for (Path backup : backups) {
            check(Files.exists(backup), "Restoring the oldest backup keeps "
                    + backup.getFileName());
        }
        check(regularBackups(manager).size() == KEEP_COUNT,
                "The copy made before a restore does not push out a backup");
        check(BackupManager.timeOf(safety) != null,
                "The time is read from the name of a copy made before");

        Path undo = history.restore(safety);
        check(history.listRuns().size() == runsBefore,
                "Undo puts every run back");
        check(Files.exists(safety), "The copy just restored is kept");
        check(undo != null && Files.exists(undo),
                "Undo makes its own copy, so it can be undone too");
        check(regularBackups(manager).size() == KEEP_COUNT,
                "Undo deletes no backup");
    }

    // Checks that the copies made before a restore are capped on their
    // own, and that Back up now still keeps its own limit.
    private static void checkCopiesKeptApart(AppConfig config,
            HistoryService history, BackupManager manager) {
        List<Path> regular = regularBackups(manager);
        for (int index = 0; index < EXTRA_RESTORES; index++) {
            history.restore(regular.get(0));
        }
        check(regularBackups(manager).equals(regular),
                "Many restores delete no backup made with Back up now");
        int copies = manager.listBackups().size() - regular.size();
        check(copies == KEEP_COUNT,
                "Only the newest copies made before a restore are kept: "
                + copies);
        for (Path file : manager.listBackups()) {
            check(history.checkBackup(file).isEmpty(),
                    "Every kept file can be restored: " + file.getFileName());
        }

        config.setString(ConfigKeys.BACKUP_KEEP_COUNT,
                String.valueOf(LOWER_KEEP_COUNT));
        check(history.backupNow() != null, "Back up now works after");
        check(regularBackups(manager).size() == LOWER_KEEP_COUNT,
                "A lower limit deletes the oldest at the next backup");
        int copiesAfter = manager.listBackups().size() - LOWER_KEEP_COUNT;
        check(copiesAfter == KEEP_COUNT,
                "Back up now deletes no copy made before a restore");
    }

    // The backups made with Back up now, newest first
    private static List<Path> regularBackups(BackupManager manager) {
        List<Path> regular = new ArrayList<>();
        for (Path file : manager.listBackups()) {
            if (!BackupInfo.isBeforeRestore(file)) {
                regular.add(file);
            }
        }
        return regular;
    }

    // Adds a run with only an id to the database, with it closed
    private static void addRun(SqliteResultRepository repository,
            Path databaseFile, String runId) throws Exception {
        repository.close();
        Connection connection = DriverManager.getConnection(JDBC
                + databaseFile);
        try {
            PreparedStatement statement = connection.prepareStatement(
                    INSERT_RUN);
            try {
                statement.setString(1, runId);
                statement.setString(2, STARTED_AT);
                statement.executeUpdate();
            } finally {
                statement.close();
            }
        } finally {
            connection.close();
        }
        repository.open();
    }

    // Counts a check that passed, or stops with what failed
    private static void check(boolean passed, String what) {
        if (!passed) {
            throw new IllegalStateException("Failed: " + what);
        }
        checks++;
    }
}
