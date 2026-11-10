/*
 * File: BackupReader.java
 *
 * Purpose: Lists the saved runs inside a backup file without changing
 * the file.
 *
 * Where it fits: HistoryService uses it so the Backups view on History
 * can show what each kept backup holds before it is restored.
 */
package gradezzy.persistence;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.sql.Connection;
import java.sql.DriverManager;
import java.sql.PreparedStatement;
import java.sql.ResultSet;
import java.sql.SQLException;
import java.time.LocalDateTime;
import java.time.ZoneId;
import java.time.format.DateTimeParseException;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Properties;

import gradezzy.model.BackupInfo;
import gradezzy.model.BackupRun;

/**
 * Reads the saved runs of a backup file.
 *
 * <p>The file is opened read only and as immutable, so SQLite writes
 * nothing to it, adds no journal file next to it and never prepares its
 * tables the way SqliteResultRepository does. Only columns every
 * version of the database has are read, so an older backup can be
 * listed too.
 *
 * <p>Tier: COULD. Owner: Huxley.
 */
public final class BackupReader {

    // Opens the file read only; immutable means SQLite takes no locks and
    // writes no journal, as the file is never changed while it is read
    private static final String READ_ONLY_OPTIONS = "?mode=ro&immutable=1";

    // The SQLite driver's connection setting for how the file is opened.
    // Set here by name so the grading core needs no class from the jar.
    private static final String OPEN_MODE = "open_mode";

    // SQLite's open flags: read only (1) plus file: addresses (64)
    private static final String READ_ONLY_URI_FLAGS = "65";

    // The saved runs, newest first; a run without a start time comes last
    private static final String SELECT_RUNS = "SELECT run_id, started_at, "
            + "module_code, section_code, assessment_name FROM runs "
            + "ORDER BY started_at DESC, run_id DESC";

    // How many students each run has and how many were graded
    private static final String COUNT_STUDENTS = "SELECT run_id, "
            + "COUNT(*) AS students, "
            + "SUM(CASE WHEN status = 'GRADED' THEN 1 ELSE 0 END) "
            + "AS graded FROM submissions GROUP BY run_id";

    // The two numbers kept for each run: students, then graded
    private static final int STUDENTS = 0;
    private static final int GRADED = 1;
    private static final int COUNT_SIZE = 2;

    /**
     * Not used: every method here is static.
     */
    private BackupReader() {
        // utility class, never instantiated
    }

    /**
     * Reads what a backup file holds. Never throws: a file that cannot
     * be read is returned with the reason.
     *
     * @param backupFile the backup file
     * @return the backup and its saved runs, newest first
     */
    public static BackupInfo read(Path backupFile) {
        LocalDateTime madeAt = madeAt(backupFile);
        long size = sizeOf(backupFile);
        if (backupFile == null || !Files.isRegularFile(backupFile)) {
            return new BackupInfo(backupFile, madeAt, size,
                    "The file is no longer there.");
        }

        Connection connection = null;
        try {
            connection = openReadOnly(backupFile);
            List<BackupRun> runs = readRuns(connection);
            return new BackupInfo(backupFile, madeAt, size, runs);
        } catch (SQLException exception) {
            return new BackupInfo(backupFile, madeAt, size,
                    "It is not a GradeZZY database or it is damaged ("
                    + exception.getMessage() + ").");
        } finally {
            closeQuietly(connection);
        }
    }

    // Opens a connection that can only read the file
    private static Connection openReadOnly(Path file) throws SQLException {
        Properties settings = new Properties();
        settings.setProperty(OPEN_MODE, READ_ONLY_URI_FLAGS);
        String address = SqliteResultRepository.JDBC_URL_PREFIX
                + file.toAbsolutePath().toUri() + READ_ONLY_OPTIONS;
        return DriverManager.getConnection(address, settings);
    }

    // Reads the saved runs with their student counts, newest first
    private static List<BackupRun> readRuns(Connection connection)
            throws SQLException {
        Map<String, int[]> counts = readCounts(connection);
        List<BackupRun> runs = new ArrayList<>();
        PreparedStatement statement = null;
        ResultSet rows = null;
        try {
            statement = connection.prepareStatement(SELECT_RUNS);
            rows = statement.executeQuery();
            while (rows.next()) {
                String runId = rows.getString("run_id");
                int[] count = counts.get(runId);
                if (count == null) {
                    count = new int[COUNT_SIZE];
                }
                runs.add(new BackupRun(runId,
                        rows.getString("module_code"),
                        rows.getString("section_code"),
                        rows.getString("assessment_name"),
                        parseTime(rows.getString("started_at")),
                        count[STUDENTS], count[GRADED]));
            }
        } finally {
            JdbcSupport.closeQuery(rows, statement);
        }
        return runs;
    }

    // Counts the students of each run. A backup without the submissions
    // table still lists its runs, each with no students.
    private static Map<String, int[]> readCounts(Connection connection) {
        Map<String, int[]> counts = new HashMap<>();
        PreparedStatement statement = null;
        ResultSet rows = null;
        try {
            statement = connection.prepareStatement(COUNT_STUDENTS);
            rows = statement.executeQuery();
            while (rows.next()) {
                int[] count = new int[COUNT_SIZE];
                count[STUDENTS] = rows.getInt("students");
                count[GRADED] = rows.getInt("graded");
                counts.put(rows.getString("run_id"), count);
            }
        } catch (SQLException exception) {
            return new HashMap<>();
        } finally {
            closeQueryQuietly(rows, statement);
        }
        return counts;
    }

    // Reads a saved start time, or null when it is missing or unreadable
    private static LocalDateTime parseTime(String text) {
        try {
            return JdbcSupport.parseTime(text);
        } catch (DateTimeParseException exception) {
            return null;
        }
    }

    // When a backup was made: the time in its name, or else the time the
    // file was last changed, or null when neither can be read
    private static LocalDateTime madeAt(Path backupFile) {
        if (backupFile == null) {
            return null;
        }
        LocalDateTime named = BackupManager.timeOf(backupFile);
        if (named != null) {
            return named;
        }
        try {
            return LocalDateTime.ofInstant(Files.getLastModifiedTime(
                    backupFile).toInstant(), ZoneId.systemDefault());
        } catch (IOException exception) {
            return null;
        }
    }

    // The size of a file in bytes, or 0 when it cannot be read
    private static long sizeOf(Path file) {
        if (file == null) {
            return 0L;
        }
        try {
            return Files.size(file);
        } catch (IOException exception) {
            return 0L;
        }
    }

    // Closes the rows and statement of a query that may have failed
    private static void closeQueryQuietly(ResultSet rows,
            PreparedStatement statement) {
        try {
            JdbcSupport.closeQuery(rows, statement);
        } catch (SQLException exception) {
            // Closing a read-only query loses nothing
        }
    }

    // Closes a connection that may never have opened
    private static void closeQuietly(Connection connection) {
        if (connection == null) {
            return;
        }
        try {
            connection.close();
        } catch (SQLException exception) {
            // Closing a read-only connection loses nothing
        }
    }
}
