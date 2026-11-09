/*
 * File: SqliteResultRepository.java
 *
 * Purpose: Saves grading state in a SQLite file through JDBC.
 *
 * Where it fits: The only class that contains SQL. JDBC is not taught in CS102,
 * so it is kept inside this one file.
 */
package gradezzy.persistence;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.sql.Connection;
import java.sql.DriverManager;
import java.sql.PreparedStatement;
import java.sql.ResultSet;
import java.sql.SQLException;
import java.sql.Statement;
import java.time.LocalDateTime;
import java.time.format.DateTimeParseException;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

import gradezzy.model.Anomaly;
import gradezzy.model.AnomalyType;
import gradezzy.model.AnswerGroupNote;
import gradezzy.model.Assessment;
import gradezzy.model.AuditEntry;
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
import gradezzy.util.TextUtils;

/**
 * Saves grading state in a SQLite file through JDBC.
 *
 * <p>Every public method is synchronized because grading saves results on a
 * background thread while the screens may read the audit log. Every save
 * runs in one transaction, so a run is never left half written.
 *
 * <p>Tier: SHOULD. Owner: Huxley.
 */
public class SqliteResultRepository implements ResultRepository {

    /**
     * The start of every SQLite JDBC address.
     */
    public static final String JDBC_URL_PREFIX = "jdbc:sqlite:";

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
                + "starter_dir TEXT)",
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

    // These columns were added after the first release. A database made
    // before them has no such columns, so open adds them.
    private static final String SCORE_SHEET_COLUMN = "score_sheet_file";
    private static final String TESTERS_DIR_COLUMN = "testers_dir";
    private static final String STARTER_DIR_COLUMN = "starter_dir";
    private static final String DECISION_COLUMN = "decision";
    private static final String REJECTED_FIXES_COLUMN = "rejected_fixes";

    // List the columns of a table, one row per column
    private static final String LIST_RUN_COLUMNS = "PRAGMA table_info(runs)";
    private static final String LIST_ANOMALY_COLUMNS =
            "PRAGMA table_info(anomalies)";
    private static final String LIST_SUBMISSION_COLUMNS =
            "PRAGMA table_info(submissions)";

    private static final String ADD_SCORE_SHEET_COLUMN = "ALTER TABLE runs "
            + "ADD COLUMN score_sheet_file TEXT";

    private static final String ADD_TESTERS_DIR_COLUMN = "ALTER TABLE runs "
            + "ADD COLUMN testers_dir TEXT";

    private static final String ADD_STARTER_DIR_COLUMN = "ALTER TABLE runs "
            + "ADD COLUMN starter_dir TEXT";

    private static final String ADD_DECISION_COLUMN = "ALTER TABLE "
            + "anomalies ADD COLUMN decision TEXT";

    private static final String ADD_REJECTED_FIXES_COLUMN = "ALTER TABLE "
            + "submissions ADD COLUMN rejected_fixes TEXT";

    // How long a write waits for another GradeZZY to finish its own
    // write before it fails, in milliseconds
    private static final String SET_BUSY_TIMEOUT =
            "PRAGMA busy_timeout = 5000";

    private static final String INSERT_RUN = "INSERT OR REPLACE INTO runs "
            + "(run_id, started_at, module_code, section_code, "
            + "assessment_name, assessment_module, placeholder_folders, "
            + "score_sheet_file, testers_dir, starter_dir) "
            + "VALUES (?, ?, ?, ?, ?, ?, ?, ?, ?, ?)";

    private static final String INSERT_QUESTION = "INSERT INTO "
            + "run_questions (run_id, position, question_id, folder_name, "
            + "file_name, max_score) VALUES (?, ?, ?, ?, ?, ?)";

    private static final String INSERT_SUBMISSION = "INSERT INTO "
            + "submissions (run_id, username, position, status, "
            + "original_path, working_copy_path, ingest_failure, "
            + "org_defined_id, student_username, last_name, first_name, "
            + "email, rejected_fixes) "
            + "VALUES (?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?)";

    private static final String INSERT_QUESTION_RESULT = "INSERT INTO "
            + "question_results (run_id, username, position, question_id, "
            + "score, compiled, timed_out, output) "
            + "VALUES (?, ?, ?, ?, ?, ?, ?, ?)";

    private static final String INSERT_TEST_CASE = "INSERT INTO "
            + "test_case_results (run_id, username, question_id, position, "
            + "test_number, passed, detail) VALUES (?, ?, ?, ?, ?, ?, ?)";

    private static final String INSERT_ANOMALY = "INSERT INTO anomalies "
            + "(run_id, username, position, anomaly_id, type, severity, "
            + "message, suggested_action, fix_status) "
            + "VALUES (?, ?, ?, ?, ?, ?, ?, ?, ?)";

    private static final String INSERT_AUDIT = "INSERT INTO audit_entries "
            + "(logged_at, action, detail) VALUES (?, ?, ?)";

    // Removes everything saved for a run before it is written again
    private static final String[] DELETE_RUN_CONTENTS = {
        "DELETE FROM test_case_results WHERE run_id = ?",
        "DELETE FROM question_results WHERE run_id = ?",
        "DELETE FROM anomalies WHERE run_id = ?",
        "DELETE FROM submissions WHERE run_id = ?",
        "DELETE FROM run_questions WHERE run_id = ?"
    };

    // Removes everything saved for one submission of a run
    private static final String[] DELETE_SUBMISSION = {
        "DELETE FROM test_case_results WHERE run_id = ? AND username = ?",
        "DELETE FROM question_results WHERE run_id = ? AND username = ?",
        "DELETE FROM anomalies WHERE run_id = ? AND username = ?",
        "DELETE FROM submissions WHERE run_id = ? AND username = ?"
    };

    private static final String SELECT_RUN = "SELECT started_at, "
            + "module_code, section_code, assessment_name, "
            + "assessment_module, placeholder_folders, score_sheet_file, "
            + "testers_dir, starter_dir FROM runs WHERE run_id = ?";

    // Newest first; a run without a start time comes last
    private static final String SELECT_RUN_IDS = "SELECT run_id FROM runs "
            + "ORDER BY started_at DESC, run_id DESC";

    private static final String SELECT_QUESTIONS = "SELECT question_id, "
            + "folder_name, file_name, max_score FROM run_questions "
            + "WHERE run_id = ? ORDER BY position";

    private static final String SELECT_SUBMISSIONS = "SELECT username, "
            + "status, original_path, working_copy_path, ingest_failure, "
            + "org_defined_id, student_username, last_name, first_name, "
            + "email, rejected_fixes FROM submissions WHERE run_id = ? "
            + "ORDER BY position";

    private static final String SELECT_QUESTION_RESULTS = "SELECT "
            + "username, question_id, score, compiled, timed_out, output "
            + "FROM question_results WHERE run_id = ? "
            + "ORDER BY username, position";

    private static final String SELECT_TEST_CASES = "SELECT username, "
            + "question_id, test_number, passed, detail "
            + "FROM test_case_results WHERE run_id = ? "
            + "ORDER BY username, question_id, position";

    private static final String SELECT_ANOMALIES = "SELECT username, "
            + "anomaly_id, type, severity, message, suggested_action, "
            + "fix_status FROM anomalies WHERE run_id = ? "
            + "ORDER BY username, position";

    // Keeps a submission's place in the run when it is saved again
    private static final String SELECT_POSITION = "SELECT position "
            + "FROM submissions WHERE run_id = ? AND username = ?";

    // The next free place at the end of a run
    private static final String SELECT_NEXT_POSITION = "SELECT "
            + "COALESCE(MAX(position) + 1, 0) AS next_position "
            + "FROM submissions WHERE run_id = ?";

    private static final String UPDATE_FIX_STATUS = "UPDATE anomalies "
            + "SET fix_status = ? WHERE anomaly_id = ?";

    private static final String UPDATE_DECISION = "UPDATE anomalies "
            + "SET decision = ? WHERE anomaly_id = ?";

    private static final String SELECT_DECISIONS = "SELECT anomaly_id, "
            + "decision FROM anomalies WHERE run_id = ? "
            + "AND decision IS NOT NULL";

    // Saves, removes and lists the answer group notes
    private static final String UPSERT_GROUP_NOTE = "INSERT OR REPLACE "
            + "INTO answer_group_notes (run_id, question_id, pattern, "
            + "label, checked_usernames) VALUES (?, ?, ?, ?, ?)";

    private static final String DELETE_GROUP_NOTE = "DELETE FROM "
            + "answer_group_notes WHERE run_id = ? AND question_id = ? "
            + "AND pattern = ?";

    private static final String SELECT_GROUP_NOTES = "SELECT question_id, "
            + "pattern, label, checked_usernames FROM answer_group_notes "
            + "WHERE run_id = ?";

    // Separates the checked usernames saved in one column
    private static final String NAME_SEPARATOR = "\n";

    // Saves, removes and lists the 0s marked as checked
    private static final String INSERT_ZERO_CHECK = "INSERT OR IGNORE "
            + "INTO zero_checks (run_id, username) VALUES (?, ?)";

    private static final String DELETE_ZERO_CHECK = "DELETE FROM "
            + "zero_checks WHERE run_id = ? AND username = ?";

    private static final String SELECT_ZERO_CHECKS = "SELECT username "
            + "FROM zero_checks WHERE run_id = ? ORDER BY username";

    // Saves, removes and lists the marks changed by hand
    private static final String INSERT_ADJUSTMENT = "INSERT INTO "
            + "score_adjustments (run_id, username, question_id, "
            + "previous_score, new_score, reason, saved_at) "
            + "VALUES (?, ?, ?, ?, ?, ?, ?)";

    // Removes one row only, so two changes saved at the same time with
    // the same marks are taken back one at a time
    private static final String DELETE_ADJUSTMENT = "DELETE FROM "
            + "score_adjustments WHERE rowid = (SELECT rowid FROM "
            + "score_adjustments WHERE run_id = ? AND username = ? "
            + "AND question_id = ? AND saved_at = ? "
            + "AND previous_score = ? AND new_score = ? LIMIT 1)";

    private static final String SELECT_ADJUSTMENTS = "SELECT username, "
            + "question_id, previous_score, new_score, reason, saved_at "
            + "FROM score_adjustments WHERE run_id = ? ORDER BY saved_at";

    private static final String SELECT_AUDIT = "SELECT logged_at, action, "
            + "detail FROM audit_entries ORDER BY entry_id";

    // Placeholder folder names are stored in one column, as in the
    // settings, where a comma already separates them
    private static final String LIST_SEPARATOR = ",";

    // Joins a username and a question id into one map key. A newline
    // cannot appear in either.
    private static final String KEY_SEPARATOR = "\n";

    // Used when a saved question is missing from the saved assessment
    private static final double UNKNOWN_MAX_SCORE = 0.0;

    // The location of the SQLite file
    private final String databaseFile;

    // The open connection, or null while the store is closed
    private Connection connection = null;

    /**
     * Creates a new sqlite result repository.
     *
     * @param databaseFile the location of the SQLite file
     */
    public SqliteResultRepository(String databaseFile) {
        this.databaseFile = databaseFile;
    }

    /**
     * Opens the store and prepares it for use.
     *
     * <p>Creates the folder and the file when they do not exist yet.
     * Opening a store that is already open does nothing.
     *
     * @throws PersistenceException if the operation fails
     */
    @Override
    public synchronized void open() throws PersistenceException {
        if (connection != null) {
            return;
        }
        if (databaseFile == null || databaseFile.isBlank()) {
            throw new PersistenceException("No database file is set. "
                    + "Check database.file in the settings.");
        }

        try {
            Path parent = Paths.get(databaseFile).toAbsolutePath()
                    .getParent();
            if (parent != null) {
                Files.createDirectories(parent);
            }
            connection = DriverManager.getConnection(JDBC_URL_PREFIX
                    + databaseFile);
            createTables();
        } catch (IOException exception) {
            throw new PersistenceException("The folder for the database "
                    + databaseFile + " could not be created: "
                    + exception.getMessage(), exception);
        } catch (SQLException exception) {
            close();
            throw new PersistenceException("The database " + databaseFile
                    + " could not be opened: " + exception.getMessage(),
                    exception);
        } catch (PersistenceException exception) {
            close();
            throw exception;
        }
    }

    /**
     * Closes the store. Safe to call twice.
     */
    @Override
    public synchronized void close() {
        if (connection == null) {
            return;
        }

        try {
            connection.close();
        } catch (SQLException exception) {
            // Nothing useful can be done when closing fails; the file is
            // released when the program ends
        }
        connection = null;
    }

    /**
     * Saves a run and everything in it.
     *
     * <p>Whatever was saved for the run before is replaced, so this can be
     * called again after grading to save the final state.
     *
     * @param gradingRun the grading run
     * @throws PersistenceException if the operation fails
     */
    @Override
    public synchronized void saveRun(GradingRun gradingRun)
            throws PersistenceException {
        requireOpen();
        if (gradingRun == null || gradingRun.getId() == null) {
            throw new PersistenceException("There is no run to save.");
        }

        String runId = gradingRun.getId();
        try {
            connection.setAutoCommit(false);
            // A flag saved again keeps its id, so it keeps its decision
            Map<String, String> decisions = readDecisions(runId);
            insertRunRow(gradingRun);
            for (String sql : DELETE_RUN_CONTENTS) {
                PreparedStatement statement = null;
                try {
                    statement = connection.prepareStatement(sql);
                    statement.setString(1, runId);
                    statement.executeUpdate();
                } finally {
                    closeStatement(statement);
                }
            }
            insertQuestions(runId, gradingRun.getAssessment());

            List<Submission> submissions = gradingRun.getSubmissions();
            for (int index = 0; index < submissions.size(); index++) {
                insertSubmission(runId, submissions.get(index), index);
            }
            writeDecisions(decisions);
            connection.commit();
        } catch (SQLException exception) {
            rollback();
            throw new PersistenceException("Run " + runId + " could not "
                    + "be saved: " + exception.getMessage(), exception);
        } finally {
            restoreAutoCommit();
        }
    }

    /**
     * Loads a saved run so it can be viewed or resumed.
     *
     * @param runId the run id
     * @return the run, or null if the id is unknown
     * @throws PersistenceException if the operation fails
     */
    @Override
    public synchronized GradingRun loadRun(String runId)
            throws PersistenceException {
        requireOpen();
        if (runId == null) {
            return null;
        }

        try {
            GradingRun gradingRun = readRunRow(runId);
            if (gradingRun == null) {
                return null;
            }

            Map<String, Submission> submissions =
                    readSubmissions(gradingRun);
            Map<String, QuestionResult> results = readQuestionResults(
                    runId, gradingRun.getAssessment(), submissions);
            readTestCases(runId, results);
            readAnomalies(runId, submissions);
            return gradingRun;
        } catch (SQLException exception) {
            throw new PersistenceException("Saved run " + runId + " could "
                    + "not be read: " + exception.getMessage(), exception);
        } catch (IllegalArgumentException exception) {
            // An unknown status or type name, or a path that is not valid
            throw new PersistenceException("Saved run " + runId + " holds "
                    + "a value this version cannot read: "
                    + exception.getMessage(), exception);
        } catch (DateTimeParseException exception) {
            throw new PersistenceException("Saved run " + runId + " has a "
                    + "start time that cannot be read: "
                    + exception.getMessage(), exception);
        }
    }

    /**
     * Lists every saved run, newest first.
     *
     * @return the saved runs
     * @throws PersistenceException if the operation fails
     */
    @Override
    public synchronized List<GradingRun> listRuns()
            throws PersistenceException {
        requireOpen();

        List<String> runIds = null;
        try {
            runIds = readRunIds();
        } catch (SQLException exception) {
            throw new PersistenceException("The saved runs could not be "
                    + "listed: " + exception.getMessage(), exception);
        }

        // A run that cannot be read is left out, so one bad row does not
        // empty History and Statistics
        List<GradingRun> runs = new ArrayList<>();
        for (String runId : runIds) {
            GradingRun gradingRun = loadReadableRun(runId);
            if (gradingRun != null) {
                runs.add(gradingRun);
            }
        }
        return runs;
    }

    /**
     * Saves the current state of one submission of a run.
     *
     * <p>A submission saved before keeps its place in the run; a new one
     * goes at the end.
     *
     * @param runId the run id
     * @param submission the submission
     * @throws PersistenceException if the operation fails
     */
    @Override
    public synchronized void saveSubmission(String runId,
            Submission submission) throws PersistenceException {
        requireOpen();
        if (runId == null || submission == null
                || submission.getUsername() == null) {
            throw new PersistenceException("There is no submission to "
                    + "save.");
        }

        String username = submission.getUsername();
        try {
            connection.setAutoCommit(false);
            // A flag saved again keeps its id, so it keeps its decision
            Map<String, String> decisions = readDecisions(runId);
            int position = findPosition(runId, username);
            for (String sql : DELETE_SUBMISSION) {
                PreparedStatement statement = null;
                try {
                    statement = connection.prepareStatement(sql);
                    statement.setString(1, runId);
                    statement.setString(2, username);
                    statement.executeUpdate();
                } finally {
                    closeStatement(statement);
                }
            }
            insertSubmission(runId, submission, position);
            writeDecisions(decisions);
            connection.commit();
        } catch (SQLException exception) {
            rollback();
            throw new PersistenceException("The result of " + username
                    + " could not be saved: " + exception.getMessage(),
                    exception);
        } finally {
            restoreAutoCommit();
        }
    }

    /**
     * Saves the new status of a fix after approve or undo.
     *
     * <p>An anomaly that was never saved, for example because the database
     * was switched off during grading, has no row to change; that is not
     * an error.
     *
     * @param anomalyId the anomaly id
     * @param fixStatus the fix status
     * @throws PersistenceException if the operation fails
     */
    @Override
    public synchronized void updateFixStatus(String anomalyId,
            FixStatus fixStatus) throws PersistenceException {
        requireOpen();
        if (anomalyId == null || fixStatus == null) {
            throw new PersistenceException("There is no fix status to "
                    + "save.");
        }

        try {
            writeFixStatus(anomalyId, fixStatus);
        } catch (SQLException exception) {
            throw new PersistenceException("The fix status of anomaly "
                    + anomalyId + " could not be saved: "
                    + exception.getMessage(), exception);
        }
    }

    /**
     * Saves the instructor's review decision on one flagged anomaly.
     *
     * <p>Like a fix status, a decision for an anomaly that was never saved
     * has no row to change; that is not an error. Grading a student again
     * saves new anomalies with no decision.
     *
     * @param anomalyId the anomaly id
     * @param decision the decision name, or null to clear it
     * @throws PersistenceException if the operation fails
     */
    @Override
    public synchronized void updateDecision(String anomalyId,
            String decision) throws PersistenceException {
        requireOpen();
        if (anomalyId == null) {
            throw new PersistenceException("There is no decision to "
                    + "save.");
        }

        try {
            writeDecision(anomalyId, decision);
        } catch (SQLException exception) {
            throw new PersistenceException("The decision on anomaly "
                    + anomalyId + " could not be saved: "
                    + exception.getMessage(), exception);
        }
    }

    /**
     * Loads the saved review decisions of a run.
     *
     * @param runId the run id
     * @return the decision names by anomaly id; anomalies with no
     *         decision are left out
     * @throws PersistenceException if the operation fails
     */
    @Override
    public synchronized Map<String, String> loadDecisions(String runId)
            throws PersistenceException {
        requireOpen();
        if (runId == null) {
            return new HashMap<>();
        }

        try {
            return readDecisions(runId);
        } catch (SQLException exception) {
            throw new PersistenceException("The decisions of run " + runId
                    + " could not be read: " + exception.getMessage(),
                    exception);
        }
    }

    /**
     * Saves or removes the mark that the instructor checked a student's 0.
     *
     * @param runId the run id
     * @param username the student
     * @param checked true to save the check, false to remove it
     * @throws PersistenceException if the operation fails
     */
    @Override
    public synchronized void updateZeroCheck(String runId, String username,
            boolean checked) throws PersistenceException {
        requireOpen();
        if (runId == null || username == null) {
            throw new PersistenceException("There is no student to save "
                    + "the check for.");
        }

        try {
            writeZeroCheck(runId, username, checked);
        } catch (SQLException exception) {
            throw new PersistenceException("The check on the 0 of "
                    + username + " could not be saved: "
                    + exception.getMessage(), exception);
        }
    }

    /**
     * Lists the students of a run whose 0 the instructor checked.
     *
     * @param runId the run id
     * @return the usernames, in alphabetical order
     * @throws PersistenceException if the operation fails
     */
    @Override
    public synchronized List<String> loadZeroChecks(String runId)
            throws PersistenceException {
        requireOpen();
        if (runId == null) {
            return new ArrayList<>();
        }

        try {
            return readZeroChecks(runId);
        } catch (SQLException exception) {
            throw new PersistenceException("The checked 0s of run " + runId
                    + " could not be read: " + exception.getMessage(),
                    exception);
        }
    }

    /**
     * Saves a mark changed by hand.
     *
     * @param runId the run id
     * @param adjustment the change
     * @throws PersistenceException if the operation fails
     */
    @Override
    public synchronized void saveAdjustment(String runId,
            ScoreAdjustment adjustment) throws PersistenceException {
        requireOpen();
        PreparedStatement statement = null;
        try {
            try {
                statement = connection.prepareStatement(INSERT_ADJUSTMENT);
                statement.setString(1, runId);
                statement.setString(2, adjustment.getUsername());
                statement.setString(3, adjustment.getQuestionId());
                statement.setDouble(4, adjustment.getPreviousScore());
                statement.setDouble(5, adjustment.getNewScore());
                statement.setString(6, adjustment.getReason());
                statement.setString(7, textOf(adjustment.getSavedAt()));
                statement.executeUpdate();
            } finally {
                closeStatement(statement);
            }
        } catch (SQLException exception) {
            throw new PersistenceException("The changed mark could not be "
                    + "saved: " + exception.getMessage(), exception);
        }
    }

    /**
     * Forgets a mark changed by hand.
     *
     * @param runId the run id
     * @param adjustment the change
     * @throws PersistenceException if the operation fails
     */
    @Override
    public synchronized void deleteAdjustment(String runId,
            ScoreAdjustment adjustment) throws PersistenceException {
        requireOpen();
        PreparedStatement statement = null;
        try {
            try {
                statement = connection.prepareStatement(DELETE_ADJUSTMENT);
                statement.setString(1, runId);
                statement.setString(2, adjustment.getUsername());
                statement.setString(3, adjustment.getQuestionId());
                statement.setString(4, textOf(adjustment.getSavedAt()));
                statement.setDouble(5, adjustment.getPreviousScore());
                statement.setDouble(6, adjustment.getNewScore());
                statement.executeUpdate();
            } finally {
                closeStatement(statement);
            }
        } catch (SQLException exception) {
            throw new PersistenceException("The changed mark could not be "
                    + "removed: " + exception.getMessage(), exception);
        }
    }

    /**
     * Lists the marks changed by hand in a run, oldest first.
     *
     * @param runId the run id
     * @return the changes
     * @throws PersistenceException if the operation fails
     */
    @Override
    public synchronized List<ScoreAdjustment> listAdjustments(String runId)
            throws PersistenceException {
        requireOpen();
        List<ScoreAdjustment> changes = new ArrayList<>();
        PreparedStatement statement = null;
        ResultSet rows = null;
        try {
            try {
                statement = connection.prepareStatement(SELECT_ADJUSTMENTS);
                statement.setString(1, runId);
                rows = statement.executeQuery();
                while (rows.next()) {
                    changes.add(new ScoreAdjustment(
                            rows.getString("username"),
                            rows.getString("question_id"),
                            rows.getDouble("previous_score"),
                            rows.getDouble("new_score"),
                            rows.getString("reason"),
                            parseTime(rows.getString("saved_at"))));
                }
            } finally {
                closeQuery(rows, statement);
            }
        } catch (SQLException exception) {
            throw new PersistenceException("The changed marks could not be "
                    + "read: " + exception.getMessage(), exception);
        } catch (DateTimeParseException exception) {
            throw new PersistenceException("A changed mark has a time that "
                    + "cannot be read: " + exception.getMessage(), exception);
        }
        return changes;
    }

    /**
     * Saves the marker's name and check for one answer group, or removes
     * them when the note holds neither.
     *
     * <p>Tier: COULD.
     *
     * @param runId the run id
     * @param note the note
     * @throws PersistenceException if the operation fails
     */
    @Override
    public synchronized void saveAnswerGroupNote(String runId,
            AnswerGroupNote note) throws PersistenceException {
        requireOpen();
        PreparedStatement statement = null;
        try {
            try {
                if (note.isEmpty()) {
                    statement = connection.prepareStatement(
                            DELETE_GROUP_NOTE);
                } else {
                    statement = connection.prepareStatement(
                            UPSERT_GROUP_NOTE);
                    statement.setString(4, note.getLabel());
                    statement.setString(5, TextUtils.join(NAME_SEPARATOR,
                            note.getCheckedUsernames()));
                }
                statement.setString(1, runId);
                statement.setString(2, note.getQuestionId());
                statement.setString(3, note.getPattern());
                statement.executeUpdate();
            } finally {
                closeStatement(statement);
            }
        } catch (SQLException exception) {
            throw new PersistenceException("The name of the mistake could "
                    + "not be saved: " + exception.getMessage(), exception);
        }
    }

    /**
     * Lists the saved answer group notes of a run.
     *
     * <p>Tier: COULD.
     *
     * @param runId the run id
     * @return the notes
     * @throws PersistenceException if the operation fails
     */
    @Override
    public synchronized List<AnswerGroupNote> listAnswerGroupNotes(
            String runId) throws PersistenceException {
        requireOpen();
        List<AnswerGroupNote> notes = new ArrayList<>();
        PreparedStatement statement = null;
        ResultSet rows = null;
        try {
            try {
                statement = connection.prepareStatement(SELECT_GROUP_NOTES);
                statement.setString(1, runId);
                rows = statement.executeQuery();
                while (rows.next()) {
                    notes.add(new AnswerGroupNote(
                            rows.getString("question_id"),
                            rows.getString("pattern"),
                            rows.getString("label"),
                            splitNames(rows.getString("checked_usernames"))));
                }
            } finally {
                closeQuery(rows, statement);
            }
        } catch (SQLException exception) {
            throw new PersistenceException("The names of the mistakes "
                    + "could not be read: " + exception.getMessage(),
                    exception);
        }
        return notes;
    }

    // Splits the saved checked usernames; none when empty or null.
    private List<String> splitNames(String saved) {
        List<String> names = new ArrayList<>();
        if (saved == null || saved.isEmpty()) {
            return names;
        }
        String[] parts = saved.split(NAME_SEPARATOR);
        for (String part : parts) {
            if (!part.isEmpty()) {
                names.add(part);
            }
        }
        return names;
    }

    /**
     * Adds one entry to the audit log.
     *
     * @param auditEntry the audit entry
     * @throws PersistenceException if the operation fails
     */
    @Override
    public synchronized void appendAudit(AuditEntry auditEntry)
            throws PersistenceException {
        requireOpen();
        if (auditEntry == null) {
            throw new PersistenceException("There is no audit entry to "
                    + "save.");
        }

        try {
            insertAudit(auditEntry);
        } catch (SQLException exception) {
            throw new PersistenceException("The audit entry could not be "
                    + "saved: " + exception.getMessage(), exception);
        }
    }

    /**
     * Lists the audit log, oldest first.
     *
     * @return the audit entries
     * @throws PersistenceException if the operation fails
     */
    @Override
    public synchronized List<AuditEntry> listAudit()
            throws PersistenceException {
        requireOpen();

        try {
            return readAudit();
        } catch (SQLException exception) {
            throw new PersistenceException("The audit log could not be "
                    + "read: " + exception.getMessage(), exception);
        } catch (DateTimeParseException exception) {
            throw new PersistenceException("The audit log holds a time "
                    + "that cannot be read: " + exception.getMessage(),
                    exception);
        }
    }

    /**
     * Creates the tables if they do not exist yet.
     *
     * @throws PersistenceException if the operation fails
     */
    private void createTables() throws PersistenceException {
        try {
            runTableSetup();
        } catch (SQLException exception) {
            throw new PersistenceException("The tables of " + databaseFile
                    + " could not be created: " + exception.getMessage(),
                    exception);
        }
    }

    // Runs the statements of createTables. Fixed text with no values in
    // it, so a plain Statement is safe.
    private void runTableSetup() throws SQLException {
        Statement statement = null;
        try {
            statement = connection.createStatement();
            // Another GradeZZY writing at the same moment makes this one
            // wait instead of failing at once
            statement.executeUpdate(SET_BUSY_TIMEOUT);

            for (String sql : CREATE_TABLES) {
                statement.executeUpdate(sql);
            }
            if (!hasColumn(statement, LIST_RUN_COLUMNS,
                    SCORE_SHEET_COLUMN)) {
                statement.executeUpdate(ADD_SCORE_SHEET_COLUMN);
            }
            if (!hasColumn(statement, LIST_RUN_COLUMNS,
                    TESTERS_DIR_COLUMN)) {
                statement.executeUpdate(ADD_TESTERS_DIR_COLUMN);
            }
            if (!hasColumn(statement, LIST_RUN_COLUMNS,
                    STARTER_DIR_COLUMN)) {
                statement.executeUpdate(ADD_STARTER_DIR_COLUMN);
            }
            if (!hasColumn(statement, LIST_ANOMALY_COLUMNS,
                    DECISION_COLUMN)) {
                statement.executeUpdate(ADD_DECISION_COLUMN);
            }
            if (!hasColumn(statement, LIST_SUBMISSION_COLUMNS,
                    REJECTED_FIXES_COLUMN)) {
                statement.executeUpdate(ADD_REJECTED_FIXES_COLUMN);
            }
        } finally {
            closeStatement(statement);
        }
    }

    // Reads the id of every saved run, newest first
    private List<String> readRunIds() throws SQLException {
        List<String> runIds = new ArrayList<>();
        PreparedStatement statement = null;
        ResultSet rows = null;
        try {
            statement = connection.prepareStatement(SELECT_RUN_IDS);
            rows = statement.executeQuery();
            while (rows.next()) {
                runIds.add(rows.getString("run_id"));
            }
        } finally {
            closeQuery(rows, statement);
        }
        return runIds;
    }

    // Changes the saved fix status of one anomaly
    private void writeFixStatus(String anomalyId, FixStatus fixStatus)
            throws SQLException {
        PreparedStatement statement = null;
        try {
            statement = connection.prepareStatement(UPDATE_FIX_STATUS);
            statement.setString(1, fixStatus.name());
            statement.setString(2, anomalyId);
            statement.executeUpdate();
        } finally {
            closeStatement(statement);
        }
    }

    // Writes one row of the audit log
    private void insertAudit(AuditEntry auditEntry) throws SQLException {
        PreparedStatement statement = null;
        try {
            statement = connection.prepareStatement(INSERT_AUDIT);
            statement.setString(1, textOf(auditEntry.getTimestamp()));
            statement.setString(2, auditEntry.getAction());
            statement.setString(3, auditEntry.getDetail());
            statement.executeUpdate();
        } finally {
            closeStatement(statement);
        }
    }

    // Reads the whole audit log, oldest first
    private List<AuditEntry> readAudit() throws SQLException {
        List<AuditEntry> entries = new ArrayList<>();
        PreparedStatement statement = null;
        ResultSet rows = null;
        try {
            statement = connection.prepareStatement(SELECT_AUDIT);
            rows = statement.executeQuery();
            while (rows.next()) {
                entries.add(new AuditEntry(
                        parseTime(rows.getString("logged_at")),
                        rows.getString("action"),
                        rows.getString("detail")));
            }
        } finally {
            closeQuery(rows, statement);
        }
        return entries;
    }

    // Closes a statement opened inside a try block, from its finally
    // block. Null means the statement was never opened.
    private void closeStatement(Statement statement) throws SQLException {
        if (statement != null) {
            statement.close();
        }
    }

    // Closes the rows of a query, then the statement that made them. The
    // statement is closed even if closing the rows fails. Null means that
    // one was never opened.
    private void closeQuery(ResultSet rows, Statement statement)
            throws SQLException {
        try {
            if (rows != null) {
                rows.close();
            }
        } finally {
            closeStatement(statement);
        }
    }

    // Closes two statements in the order given. The second is closed
    // even if closing the first fails.
    private void closeStatements(Statement first, Statement second)
            throws SQLException {
        try {
            closeStatement(first);
        } finally {
            closeStatement(second);
        }
    }

    // Says whether a table already has the named column. A database made
    // before the column existed does not. listColumnsSql is one of the
    // fixed PRAGMA table_info texts above.
    private boolean hasColumn(Statement statement, String listColumnsSql,
            String columnName) throws SQLException {
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
            closeQuery(columns, null);
        }
        return found;
    }

    // Fails with a clear message when open was not called or failed
    private void requireOpen() throws PersistenceException {
        if (connection == null) {
            throw new PersistenceException("The database is not open. "
                    + "Check database.file and database.enabled in the "
                    + "settings.");
        }
    }

    // Undoes a transaction that failed halfway
    private void rollback() {
        try {
            connection.rollback();
        } catch (SQLException exception) {
            // The original failure is the one worth reporting
        }
    }

    // Goes back to saving each statement on its own
    private void restoreAutoCommit() {
        try {
            connection.setAutoCommit(true);
        } catch (SQLException exception) {
            // The next transaction sets it again
        }
    }

    // Writes the row of the run itself
    private void insertRunRow(GradingRun gradingRun) throws SQLException {
        Section section = gradingRun.getSection();
        Assessment assessment = gradingRun.getAssessment();

        // A missing section or assessment leaves its columns empty (null)
        String moduleCode = null;
        String sectionCode = null;
        if (section != null) {
            moduleCode = section.getModuleCode();
            sectionCode = section.getSectionCode();
        }
        String assessmentName = null;
        String assessmentModule = null;
        String placeholders = null;
        if (assessment != null) {
            assessmentName = assessment.getName();
            assessmentModule = assessment.getModuleCode();
            placeholders = TextUtils.join(LIST_SEPARATOR,
                    assessment.getPlaceholderFolderNames());
        }

        PreparedStatement statement = null;
        try {
            statement = connection.prepareStatement(INSERT_RUN);
            statement.setString(1, gradingRun.getId());
            statement.setString(2, textOf(gradingRun.getStartedAt()));
            statement.setString(3, moduleCode);
            statement.setString(4, sectionCode);
            statement.setString(5, assessmentName);
            statement.setString(6, assessmentModule);
            statement.setString(7, placeholders);
            statement.setString(8, textOf(gradingRun.getScoreSheetFile()));
            statement.setString(9,
                    textOf(gradingRun.getTestersDirectory()));
            statement.setString(10,
                    textOf(gradingRun.getStarterDirectory()));
            statement.executeUpdate();
        } finally {
            closeStatement(statement);
        }
    }

    // Saves the questions so a run still shows the right maximums after
    // the settings change
    private void insertQuestions(String runId,
            Assessment assessment) throws SQLException {
        if (assessment == null) {
            return;
        }

        List<Question> questions = assessment.getQuestions();
        PreparedStatement statement = null;
        try {
            statement = connection.prepareStatement(INSERT_QUESTION);
            for (int index = 0; index < questions.size(); index++) {
                Question question = questions.get(index);
                statement.setString(1, runId);
                statement.setInt(2, index);
                statement.setString(3, question.getId());
                statement.setString(4, question.getFolderName());
                statement.setString(5, question.getFileName());
                statement.setDouble(6, question.getMaxScore());
                statement.executeUpdate();
            }
        } finally {
            closeStatement(statement);
        }
    }

    // Writes one submission with its results, test cases and anomalies
    private void insertSubmission(String runId, Submission submission,
            int position) throws SQLException {
        String username = submission.getUsername();
        Student student = submission.getStudent();

        // A student who is not on the roster leaves these columns empty
        String orgDefinedId = null;
        String studentUsername = null;
        String lastName = null;
        String firstName = null;
        String email = null;
        if (student != null) {
            orgDefinedId = student.getOrgDefinedId();
            studentUsername = student.getUsername();
            lastName = student.getLastName();
            firstName = student.getFirstName();
            email = student.getEmail();
        }

        PreparedStatement statement = null;
        try {
            statement = connection.prepareStatement(INSERT_SUBMISSION);
            statement.setString(1, runId);
            statement.setString(2, username);
            statement.setInt(3, position);
            statement.setString(4, textOf(submission.getStatus()));
            statement.setString(5, textOf(submission.getOriginalPath()));
            statement.setString(6, textOf(submission.getWorkingCopyPath()));
            statement.setString(7, submission.getIngestFailure());
            statement.setString(8, orgDefinedId);
            statement.setString(9, studentUsername);
            statement.setString(10, lastName);
            statement.setString(11, firstName);
            statement.setString(12, email);
            statement.setString(13, joinFixKeys(
                    submission.getRejectedFixes()));
            statement.executeUpdate();
        } finally {
            closeStatement(statement);
        }

        insertQuestionResults(runId, username,
                submission.getQuestionResults());
        insertAnomalies(runId, username, submission.getAnomalies());
    }

    // Writes the score of each question and each of its test cases
    private void insertQuestionResults(String runId, String username,
            List<QuestionResult> results) throws SQLException {
        PreparedStatement resultStatement = null;
        PreparedStatement testStatement = null;
        try {
            resultStatement = connection.prepareStatement(
                    INSERT_QUESTION_RESULT);
            testStatement = connection.prepareStatement(INSERT_TEST_CASE);
            for (int index = 0; index < results.size(); index++) {
                QuestionResult result = results.get(index);
                String questionId = questionIdOf(result);
                resultStatement.setString(1, runId);
                resultStatement.setString(2, username);
                resultStatement.setInt(3, index);
                resultStatement.setString(4, questionId);
                resultStatement.setDouble(5, result.getScore());
                resultStatement.setBoolean(6, result.isCompiled());
                resultStatement.setBoolean(7, result.isTimedOut());
                resultStatement.setString(8, result.getOutput());
                resultStatement.executeUpdate();

                List<TestCaseResult> testCases = result.getTestCaseResults();
                for (int testIndex = 0; testIndex < testCases.size();
                        testIndex++) {
                    TestCaseResult testCase = testCases.get(testIndex);
                    testStatement.setString(1, runId);
                    testStatement.setString(2, username);
                    testStatement.setString(3, questionId);
                    testStatement.setInt(4, testIndex);
                    testStatement.setInt(5, testCase.getTestNumber());
                    testStatement.setBoolean(6, testCase.isPassed());
                    testStatement.setString(7, testCase.getDetail());
                    testStatement.executeUpdate();
                }
            }
        } finally {
            // Closed in the opposite order to opening
            closeStatements(testStatement, resultStatement);
        }
    }

    // Writes each anomaly of one submission
    private void insertAnomalies(String runId, String username,
            List<Anomaly> anomalies) throws SQLException {
        PreparedStatement statement = null;
        try {
            statement = connection.prepareStatement(INSERT_ANOMALY);
            for (int index = 0; index < anomalies.size(); index++) {
                Anomaly anomaly = anomalies.get(index);
                statement.setString(1, runId);
                statement.setString(2, username);
                statement.setInt(3, index);
                statement.setString(4, anomaly.getId());
                statement.setString(5, textOf(anomaly.getType()));
                statement.setString(6, textOf(anomaly.getSeverity()));
                statement.setString(7, anomaly.getMessage());
                statement.setString(8, anomaly.getSuggestedAction());
                statement.setString(9, textOf(anomaly.getFixStatus()));
                statement.executeUpdate();
            }
        } finally {
            closeStatement(statement);
        }
    }

    // The place a submission already has in the run, or the next free one
    private int findPosition(String runId,
            String username) throws SQLException {
        PreparedStatement statement = null;
        ResultSet rows = null;
        try {
            statement = connection.prepareStatement(SELECT_POSITION);
            statement.setString(1, runId);
            statement.setString(2, username);
            rows = statement.executeQuery();
            if (rows.next()) {
                return rows.getInt("position");
            }
        } finally {
            closeQuery(rows, statement);
        }

        return findNextPosition(runId);
    }

    // The next free place at the end of a run
    private int findNextPosition(String runId) throws SQLException {
        PreparedStatement statement = null;
        ResultSet rows = null;
        try {
            statement = connection.prepareStatement(SELECT_NEXT_POSITION);
            statement.setString(1, runId);
            rows = statement.executeQuery();
            rows.next();
            return rows.getInt("next_position");
        } finally {
            closeQuery(rows, statement);
        }
    }

    // Rebuilds the run, its assessment and its section, without the
    // submissions. Returns null if the run was never saved.
    private GradingRun readRunRow(String runId) throws SQLException {
        Assessment assessment = null;
        Section section = null;
        LocalDateTime startedAt = null;
        Path scoreSheetFile = null;
        Path testersDirectory = null;
        Path starterDirectory = null;
        PreparedStatement statement = null;
        ResultSet rows = null;
        try {
            statement = connection.prepareStatement(SELECT_RUN);
            statement.setString(1, runId);
            rows = statement.executeQuery();
            if (!rows.next()) {
                return null;
            }
            startedAt = parseTime(rows.getString("started_at"));
            section = new Section(rows.getString("module_code"),
                    rows.getString("section_code"));
            assessment = new Assessment(
                    rows.getString("assessment_name"),
                    rows.getString("assessment_module"));
            addPlaceholders(assessment,
                    rows.getString("placeholder_folders"));
            scoreSheetFile = toPath(rows.getString(SCORE_SHEET_COLUMN));
            testersDirectory = toPath(rows.getString(TESTERS_DIR_COLUMN));
            starterDirectory = toPath(rows.getString(STARTER_DIR_COLUMN));
        } finally {
            closeQuery(rows, statement);
        }

        readQuestions(runId, assessment);
        GradingRun gradingRun = new GradingRun(runId, assessment, section,
                startedAt);
        gradingRun.setScoreSheetFile(scoreSheetFile);
        gradingRun.setTestersDirectory(testersDirectory);
        gradingRun.setStarterDirectory(starterDirectory);
        return gradingRun;
    }

    // Adds the saved questions of a run to its assessment, in order
    private void readQuestions(String runId,
            Assessment assessment) throws SQLException {
        PreparedStatement statement = null;
        ResultSet rows = null;
        try {
            statement = connection.prepareStatement(SELECT_QUESTIONS);
            statement.setString(1, runId);
            rows = statement.executeQuery();
            while (rows.next()) {
                assessment.addQuestion(new Question(
                        rows.getString("question_id"),
                        rows.getString("folder_name"),
                        rows.getString("file_name"),
                        rows.getDouble("max_score")));
            }
        } finally {
            closeQuery(rows, statement);
        }
    }

    // Splits the saved placeholder names back into the assessment
    private void addPlaceholders(Assessment assessment, String joined) {
        if (joined == null || joined.isEmpty()) {
            return;
        }
        for (String name : joined.split(LIST_SEPARATOR)) {
            assessment.getPlaceholderFolderNames().add(name);
        }
    }

    // Adds the saved submissions to the run in their saved order and
    // returns them by username
    private Map<String, Submission> readSubmissions(GradingRun gradingRun)
            throws SQLException {
        Map<String, Submission> submissions = new HashMap<>();
        PreparedStatement statement = null;
        ResultSet rows = null;
        try {
            statement = connection.prepareStatement(SELECT_SUBMISSIONS);
            statement.setString(1, gradingRun.getId());
            rows = statement.executeQuery();
            while (rows.next()) {
                Submission submission = readSubmission(rows,
                        gradingRun.getSection());
                gradingRun.addSubmission(submission);
                submissions.put(submission.getUsername(), submission);
            }
        } finally {
            closeQuery(rows, statement);
        }
        return submissions;
    }

    // Rebuilds one submission and its roster entry from a row
    private Submission readSubmission(ResultSet row,
            Section section) throws SQLException {
        Submission submission = new Submission(row.getString("username"),
                section, toPath(row.getString("original_path")));
        submission.setWorkingCopyPath(
                toPath(row.getString("working_copy_path")));
        submission.setIngestFailure(row.getString("ingest_failure"));

        String status = row.getString("status");
        if (status != null) {
            submission.setStatus(submissionStatusOf(status));
        }

        String orgDefinedId = row.getString("org_defined_id");
        if (orgDefinedId != null) {
            submission.setStudent(new Student(orgDefinedId,
                    row.getString("student_username"),
                    row.getString("last_name"),
                    row.getString("first_name"),
                    row.getString("email")));
        }

        // Undone fixes stay undone when the run is resumed or retried
        String rejectedFixes = row.getString(REJECTED_FIXES_COLUMN);
        if (rejectedFixes != null && !rejectedFixes.isEmpty()) {
            for (String fixKey : rejectedFixes.split(KEY_SEPARATOR)) {
                submission.rejectFix(fixKey);
            }
        }
        return submission;
    }

    // Joins the keys of the undone fixes into one column, or returns null
    // when there are none. A fix key names a fixer and a path, so it has
    // no newline in it.
    private String joinFixKeys(List<String> fixKeys) {
        if (fixKeys.isEmpty()) {
            return null;
        }
        return TextUtils.join(KEY_SEPARATOR, fixKeys);
    }

    // Adds each saved question result to its submission and returns them
    // by username and question id, so test cases can find them
    private Map<String, QuestionResult> readQuestionResults(String runId,
            Assessment assessment, Map<String, Submission> submissions)
            throws SQLException {
        Map<String, QuestionResult> results = new HashMap<>();
        PreparedStatement statement = null;
        ResultSet rows = null;
        try {
            statement = connection.prepareStatement(SELECT_QUESTION_RESULTS);
            statement.setString(1, runId);
            rows = statement.executeQuery();
            while (rows.next()) {
                String username = rows.getString("username");
                String questionId = rows.getString("question_id");
                Submission submission = submissions.get(username);
                if (submission != null) {
                    QuestionResult result = new QuestionResult(
                            findQuestion(assessment, questionId));
                    result.setScore(rows.getDouble("score"));
                    result.setCompiled(rows.getBoolean("compiled"));
                    result.setTimedOut(rows.getBoolean("timed_out"));
                    result.setOutput(rows.getString("output"));
                    submission.addQuestionResult(result);
                    results.put(username + KEY_SEPARATOR + questionId,
                            result);
                }
            }
        } finally {
            closeQuery(rows, statement);
        }
        return results;
    }

    // Adds each saved test case to its question result
    private void readTestCases(String runId,
            Map<String, QuestionResult> results) throws SQLException {
        PreparedStatement statement = null;
        ResultSet rows = null;
        try {
            statement = connection.prepareStatement(SELECT_TEST_CASES);
            statement.setString(1, runId);
            rows = statement.executeQuery();
            while (rows.next()) {
                QuestionResult result = results.get(
                        rows.getString("username") + KEY_SEPARATOR
                        + rows.getString("question_id"));
                if (result != null) {
                    result.addTestCaseResult(new TestCaseResult(
                            rows.getInt("test_number"),
                            rows.getBoolean("passed"),
                            rows.getString("detail")));
                }
            }
        } finally {
            closeQuery(rows, statement);
        }
    }

    // Adds each saved anomaly, with its fix status, to its submission
    private void readAnomalies(String runId,
            Map<String, Submission> submissions) throws SQLException {
        PreparedStatement statement = null;
        ResultSet rows = null;
        try {
            statement = connection.prepareStatement(SELECT_ANOMALIES);
            statement.setString(1, runId);
            rows = statement.executeQuery();
            while (rows.next()) {
                String username = rows.getString("username");
                Submission submission = submissions.get(username);
                if (submission != null) {
                    submission.addAnomaly(readAnomaly(rows, username));
                }
            }
        } finally {
            closeQuery(rows, statement);
        }
    }

    // Rebuilds one anomaly from a row
    private Anomaly readAnomaly(ResultSet row,
            String username) throws SQLException {
        String type = row.getString("type");
        String severity = row.getString("severity");
        String anomalyId = row.getString("anomaly_id");

        // A missing type or severity stays null
        AnomalyType anomalyType = null;
        if (type != null) {
            anomalyType = anomalyTypeOf(type);
        }
        Severity anomalySeverity = null;
        if (severity != null) {
            anomalySeverity = severityOf(severity);
        }

        Anomaly anomaly = new Anomaly(anomalyId, anomalyType,
                anomalySeverity, username, row.getString("message"),
                row.getString("suggested_action"));

        FixStatus fixStatus = fixStatusOf(row.getString("fix_status"));
        if (fixStatus != null) {
            anomaly.setFixStatus(fixStatus);
        }
        return anomaly;
    }

    // Reads a saved submission status. A name this version does not know
    // becomes FAILED, so the student still shows and can be graded again.
    private SubmissionStatus submissionStatusOf(String name) {
        try {
            return SubmissionStatus.valueOf(name);
        } catch (IllegalArgumentException exception) {
            return SubmissionStatus.FAILED;
        }
    }

    // Reads a saved anomaly type. A type removed from AnomalyType becomes
    // INTERNAL_ERROR, so the flag still shows for review.
    private AnomalyType anomalyTypeOf(String name) {
        try {
            return AnomalyType.valueOf(name);
        } catch (IllegalArgumentException exception) {
            return AnomalyType.INTERNAL_ERROR;
        }
    }

    // Reads a saved severity. An unknown name stays null, the same as a
    // missing one.
    private Severity severityOf(String name) {
        try {
            return Severity.valueOf(name);
        } catch (IllegalArgumentException exception) {
            return null;
        }
    }

    // Reads a saved fix status. A missing or unknown name gives null, and
    // the anomaly keeps its default status.
    private FixStatus fixStatusOf(String name) {
        if (name == null) {
            return null;
        }
        try {
            return FixStatus.valueOf(name);
        } catch (IllegalArgumentException exception) {
            return null;
        }
    }

    // Changes the saved review decision of one anomaly
    private void writeDecision(String anomalyId, String decision)
            throws SQLException {
        PreparedStatement statement = null;
        try {
            statement = connection.prepareStatement(UPDATE_DECISION);
            statement.setString(1, decision);
            statement.setString(2, anomalyId);
            statement.executeUpdate();
        } finally {
            closeStatement(statement);
        }
    }

    // Saves or removes one checked 0
    private void writeZeroCheck(String runId, String username,
            boolean checked) throws SQLException {
        String sql = DELETE_ZERO_CHECK;
        if (checked) {
            sql = INSERT_ZERO_CHECK;
        }
        PreparedStatement statement = null;
        try {
            statement = connection.prepareStatement(sql);
            statement.setString(1, runId);
            statement.setString(2, username);
            statement.executeUpdate();
        } finally {
            closeStatement(statement);
        }
    }

    // Reads the usernames of a run whose 0 was checked
    private List<String> readZeroChecks(String runId) throws SQLException {
        List<String> usernames = new ArrayList<>();
        PreparedStatement statement = null;
        ResultSet rows = null;
        try {
            statement = connection.prepareStatement(SELECT_ZERO_CHECKS);
            statement.setString(1, runId);
            rows = statement.executeQuery();
            while (rows.next()) {
                usernames.add(rows.getString("username"));
            }
        } finally {
            closeQuery(rows, statement);
        }
        return usernames;
    }

    // Writes back decisions read before a run or student was saved
    // again. A flag that is gone has no row, so its decision is dropped.
    private void writeDecisions(Map<String, String> decisions)
            throws SQLException {
        for (String anomalyId : decisions.keySet()) {
            writeDecision(anomalyId, decisions.get(anomalyId));
        }
    }

    // Reads the saved review decisions of a run, by anomaly id
    private Map<String, String> readDecisions(String runId)
            throws SQLException {
        Map<String, String> decisions = new HashMap<>();
        PreparedStatement statement = null;
        ResultSet rows = null;
        try {
            statement = connection.prepareStatement(SELECT_DECISIONS);
            statement.setString(1, runId);
            rows = statement.executeQuery();
            while (rows.next()) {
                decisions.put(rows.getString("anomaly_id"),
                        rows.getString(DECISION_COLUMN));
            }
        } finally {
            closeQuery(rows, statement);
        }
        return decisions;
    }

    // Loads one run for listRuns, or null if it cannot be read
    private GradingRun loadReadableRun(String runId) {
        try {
            return loadRun(runId);
        } catch (PersistenceException exception) {
            return null;
        }
    }

    // The question of the saved assessment, or a stand-in with no marks
    // if the run's questions were not saved
    private Question findQuestion(Assessment assessment, String questionId) {
        Question question = assessment.findQuestion(questionId);
        if (question == null) {
            return new Question(questionId, "", "", UNKNOWN_MAX_SCORE);
        }
        return question;
    }

    // The question id of a result, or null if it has no question
    private String questionIdOf(QuestionResult result) {
        if (result.getQuestion() == null) {
            return null;
        }
        return result.getQuestion().getId();
    }

    // Stores enums, paths and times as text; null stays null
    private String textOf(Object value) {
        if (value == null) {
            return null;
        }
        return value.toString();
    }

    // Reads a time saved by textOf; null stays null
    private LocalDateTime parseTime(String text) {
        if (text == null) {
            return null;
        }
        return LocalDateTime.parse(text);
    }

    // Reads a path saved by textOf; null stays null
    private Path toPath(String text) {
        if (text == null) {
            return null;
        }
        return Paths.get(text);
    }
}
