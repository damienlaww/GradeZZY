/*
 * File: SqliteSchema.java
 *
 * Purpose: Creates the tables of the SQLite file and brings an older file
 * up to date.
 *
 * Where it fits: Run by SqliteResultRepository each time it opens the
 * database, before anything is saved or loaded.
 */
package gradezzy.persistence;

import java.sql.Connection;
import java.sql.ResultSet;
import java.sql.SQLException;
import java.sql.Statement;

/**
 * The tables of the SQLite file, and the columns added since the first
 * release.
 *
 * <p>Tier: SHOULD. Owner: Huxley.
 */
final class SqliteSchema {

    // These columns were added after the first release. A database made
    // before them has no such columns, so prepare adds them. The table
    // classes read them by these names.
    static final String SCORE_SHEET_COLUMN = "score_sheet_file";
    static final String TESTERS_DIR_COLUMN = "testers_dir";
    static final String STARTER_DIR_COLUMN = "starter_dir";
    static final String GRADING_SETTINGS_COLUMN = "grading_settings";
    static final String DECISION_COLUMN = "decision";
    static final String REJECTED_FIXES_COLUMN = "rejected_fixes";
    static final String CASE_NAME_COLUMN = "case_name";

    // There are no foreign keys: PersistStage may save a submission before
    // its run row exists, and the run row arrives with saveRun.
    private static final String[] CREATE_TABLES = {
        "CREATE TABLE IF NOT EXISTS runs ("
                + "run_id TEXT PRIMARY KEY, "
                + "started_at TEXT, "
                + "module_code TEXT, "
                + "section_code TEXT, "
                + "assessment_name TEXT, "
                + "assessment_module TEXT, "
                + "placeholder_folders TEXT, "
                + "score_sheet_file TEXT, "
                + "testers_dir TEXT, "
                + "starter_dir TEXT, "
                + "grading_settings TEXT)",
        "CREATE TABLE IF NOT EXISTS run_questions ("
                + "run_id TEXT NOT NULL, "
                + "position INTEGER NOT NULL, "
                + "question_id TEXT NOT NULL, "
                + "folder_name TEXT, "
                + "file_name TEXT, "
                + "max_score REAL NOT NULL, "
                + "PRIMARY KEY (run_id, question_id))",
        "CREATE TABLE IF NOT EXISTS submissions ("
                + "run_id TEXT NOT NULL, "
                + "username TEXT NOT NULL, "
                + "position INTEGER NOT NULL, "
                + "status TEXT, "
                + "original_path TEXT, "
                + "working_copy_path TEXT, "
                + "ingest_failure TEXT, "
                + "org_defined_id TEXT, "
                + "student_username TEXT, "
                + "last_name TEXT, "
                + "first_name TEXT, "
                + "email TEXT, "
                + "rejected_fixes TEXT, "
                + "PRIMARY KEY (run_id, username))",
        "CREATE TABLE IF NOT EXISTS question_results ("
                + "run_id TEXT NOT NULL, "
                + "username TEXT NOT NULL, "
                + "position INTEGER NOT NULL, "
                + "question_id TEXT, "
                + "score REAL NOT NULL, "
                + "compiled INTEGER NOT NULL, "
                + "timed_out INTEGER NOT NULL, "
                + "output TEXT, "
                + "PRIMARY KEY (run_id, username, position))",
        "CREATE TABLE IF NOT EXISTS test_case_results ("
                + "run_id TEXT NOT NULL, "
                + "username TEXT NOT NULL, "
                + "question_id TEXT, "
                + "position INTEGER NOT NULL, "
                + "test_number INTEGER NOT NULL, "
                + "passed INTEGER NOT NULL, "
                + "detail TEXT, "
                + "PRIMARY KEY (run_id, username, question_id, position))",
        "CREATE TABLE IF NOT EXISTS anomalies ("
                + "run_id TEXT NOT NULL, "
                + "username TEXT NOT NULL, "
                + "position INTEGER NOT NULL, "
                + "anomaly_id TEXT NOT NULL, "
                + "type TEXT, "
                + "severity TEXT, "
                + "message TEXT, "
                + "suggested_action TEXT, "
                + "fix_status TEXT, "
                + "decision TEXT, "
                + "PRIMARY KEY (run_id, username, position))",
        "CREATE INDEX IF NOT EXISTS anomalies_by_id "
                + "ON anomalies (anomaly_id)",
        "CREATE TABLE IF NOT EXISTS score_adjustments ("
                + "run_id TEXT NOT NULL, "
                + "username TEXT NOT NULL, "
                + "question_id TEXT NOT NULL, "
                + "previous_score REAL NOT NULL, "
                + "new_score REAL NOT NULL, "
                + "reason TEXT, "
                + "saved_at TEXT NOT NULL)",
        // A 0 the instructor looked at; kept apart from the submissions
        // so saving a run again does not forget it
        "CREATE TABLE IF NOT EXISTS zero_checks ("
                + "run_id TEXT NOT NULL, "
                + "username TEXT NOT NULL, "
                + "PRIMARY KEY (run_id, username))",
        // The marker's name and check for an answer group, kept apart
        // from the submissions so grading again does not forget them
        "CREATE TABLE IF NOT EXISTS answer_group_notes ("
                + "run_id TEXT NOT NULL, "
                + "question_id TEXT NOT NULL, "
                + "pattern TEXT NOT NULL, "
                + "label TEXT, "
                + "checked_usernames TEXT, "
                + "PRIMARY KEY (run_id, question_id, pattern))",
        "CREATE TABLE IF NOT EXISTS audit_entries ("
                + "entry_id INTEGER PRIMARY KEY AUTOINCREMENT, "
                + "logged_at TEXT, "
                + "action TEXT, "
                + "detail TEXT)"
    };

    // List the columns of a table, one row per column
    private static final String LIST_RUN_COLUMNS = "PRAGMA table_info(runs)";
    private static final String LIST_ANOMALY_COLUMNS =
            "PRAGMA table_info(anomalies)";
    private static final String LIST_SUBMISSION_COLUMNS =
            "PRAGMA table_info(submissions)";
    private static final String LIST_TEST_CASE_COLUMNS =
            "PRAGMA table_info(test_case_results)";

    // Add the columns that a database made before them lacks: the score
    // sheet of a run
    private static final String ADD_SCORE_SHEET_COLUMN = "ALTER TABLE runs "
            + "ADD COLUMN score_sheet_file TEXT";

    // The tester folder of a run
    private static final String ADD_TESTERS_DIR_COLUMN = "ALTER TABLE runs "
            + "ADD COLUMN testers_dir TEXT";

    // The starter folder of a run
    private static final String ADD_STARTER_DIR_COLUMN = "ALTER TABLE runs "
            + "ADD COLUMN starter_dir TEXT";

    // The grading settings a run started with
    private static final String ADD_GRADING_SETTINGS_COLUMN = "ALTER TABLE "
            + "runs ADD COLUMN grading_settings TEXT";

    // The review decision of an anomaly
    private static final String ADD_DECISION_COLUMN = "ALTER TABLE "
            + "anomalies ADD COLUMN decision TEXT";

    // The fixes a student's grading skips
    private static final String ADD_REJECTED_FIXES_COLUMN = "ALTER TABLE "
            + "submissions ADD COLUMN rejected_fixes TEXT";

    // What the tester's header calls a test case
    private static final String ADD_CASE_NAME_COLUMN = "ALTER TABLE "
            + "test_case_results ADD COLUMN case_name TEXT";

    // How long a write waits for another GradeZZY to finish its own
    // write before it fails, in milliseconds
    private static final String SET_BUSY_TIMEOUT =
            "PRAGMA busy_timeout = 5000";

    /**
     * Not used: every method here is static.
     */
    private SqliteSchema() {
        // utility class, never instantiated
    }

    /**
     * Sets the busy timeout, creates the tables that do not exist yet and
     * adds the columns an older database lacks. Safe to run on every
     * open.
     *
     * @param connection the open connection
     * @throws SQLException if a table or column cannot be created
     */
    static void prepare(Connection connection) throws SQLException {
        // Fixed text with no values in it, so a plain Statement is safe
        Statement statement = null;
        try {
            statement = connection.createStatement();
            // Another GradeZZY writing at the same moment makes this one
            // wait instead of failing at once
            statement.executeUpdate(SET_BUSY_TIMEOUT);

            for (String sql : CREATE_TABLES) {
                statement.executeUpdate(sql);
            }
            addColumnIfMissing(statement, LIST_RUN_COLUMNS,
                    SCORE_SHEET_COLUMN, ADD_SCORE_SHEET_COLUMN);
            addColumnIfMissing(statement, LIST_RUN_COLUMNS,
                    TESTERS_DIR_COLUMN, ADD_TESTERS_DIR_COLUMN);
            addColumnIfMissing(statement, LIST_RUN_COLUMNS,
                    STARTER_DIR_COLUMN, ADD_STARTER_DIR_COLUMN);
            addColumnIfMissing(statement, LIST_RUN_COLUMNS,
                    GRADING_SETTINGS_COLUMN, ADD_GRADING_SETTINGS_COLUMN);
            addColumnIfMissing(statement, LIST_ANOMALY_COLUMNS,
                    DECISION_COLUMN, ADD_DECISION_COLUMN);
            addColumnIfMissing(statement, LIST_SUBMISSION_COLUMNS,
                    REJECTED_FIXES_COLUMN, ADD_REJECTED_FIXES_COLUMN);
            addColumnIfMissing(statement, LIST_TEST_CASE_COLUMNS,
                    CASE_NAME_COLUMN, ADD_CASE_NAME_COLUMN);
        } finally {
            JdbcSupport.closeStatement(statement);
        }
    }

    // Runs addColumnSql when the table listed by listColumnsSql has no
    // column named columnName
    private static void addColumnIfMissing(Statement statement,
            String listColumnsSql, String columnName, String addColumnSql)
            throws SQLException {
        if (!hasColumn(statement, listColumnsSql, columnName)) {
            statement.executeUpdate(addColumnSql);
        }
    }

    // Says whether a table already has the named column. A database made
    // before the column existed does not. listColumnsSql is one of the
    // fixed PRAGMA table_info texts above.
    private static boolean hasColumn(Statement statement,
            String listColumnsSql, String columnName) throws SQLException {
        boolean found = false;
        ResultSet columns = null;
        try {
            columns = statement.executeQuery(listColumnsSql);
            while (columns.next()) {
                if (columnName.equals(columns.getString("name"))) {
                    found = true;
                }
            }
        } finally {
            // The statement belongs to the caller, so only the rows close
            JdbcSupport.closeQuery(columns, null);
        }
        return found;
    }
}
