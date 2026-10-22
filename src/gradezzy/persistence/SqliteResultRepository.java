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
import gradezzy.model.Assessment;
import gradezzy.model.AuditEntry;
import gradezzy.model.FixStatus;
import gradezzy.model.GradingRun;
import gradezzy.model.Question;
import gradezzy.model.QuestionResult;
import gradezzy.model.Section;
import gradezzy.model.Severity;
import gradezzy.model.Student;
import gradezzy.model.Submission;
import gradezzy.model.SubmissionStatus;
import gradezzy.model.TestCaseResult;

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
                + "placeholder_folders TEXT)",
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
                + "PRIMARY KEY (run_id, username, position))",
        "CREATE INDEX IF NOT EXISTS anomalies_by_id "
                + "ON anomalies (anomaly_id)",
        "CREATE TABLE IF NOT EXISTS audit_entries ("
                + "entry_id INTEGER PRIMARY KEY AUTOINCREMENT, "
                + "logged_at TEXT, "
                + "action TEXT, "
                + "detail TEXT)"
    };

    private static final String INSERT_RUN = "INSERT OR REPLACE INTO runs "
            + "(run_id, started_at, module_code, section_code, "
            + "assessment_name, assessment_module, placeholder_folders) "
            + "VALUES (?, ?, ?, ?, ?, ?, ?)";

    private static final String INSERT_QUESTION = "INSERT INTO "
            + "run_questions (run_id, position, question_id, folder_name, "
            + "file_name, max_score) VALUES (?, ?, ?, ?, ?, ?)";

    private static final String INSERT_SUBMISSION = "INSERT INTO "
            + "submissions (run_id, username, position, status, "
            + "original_path, working_copy_path, ingest_failure, "
            + "org_defined_id, student_username, last_name, first_name, "
            + "email) VALUES (?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?)";

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
            + "assessment_module, placeholder_folders FROM runs "
            + "WHERE run_id = ?";

    // Newest first; a run without a start time comes last
    private static final String SELECT_RUN_IDS = "SELECT run_id FROM runs "
            + "ORDER BY started_at DESC, run_id DESC";

    private static final String SELECT_QUESTIONS = "SELECT question_id, "
            + "folder_name, file_name, max_score FROM run_questions "
            + "WHERE run_id = ? ORDER BY position";

    private static final String SELECT_SUBMISSIONS = "SELECT username, "
            + "status, original_path, working_copy_path, ingest_failure, "
            + "org_defined_id, student_username, last_name, first_name, "
            + "email FROM submissions WHERE run_id = ? ORDER BY position";

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
            + "COALESCE(MAX(position) + 1, 0) FROM submissions "
            + "WHERE run_id = ?";

    private static final String UPDATE_FIX_STATUS = "UPDATE anomalies "
            + "SET fix_status = ? WHERE anomaly_id = ?";

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
    // Generated with Claude Opus 5.5; reviewed by Huxley
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
    // Generated with Claude Opus 5.5; reviewed by Huxley
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
    // Generated with Claude Opus 5.5; reviewed by Huxley
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
            insertRunRow(gradingRun);
            for (String sql : DELETE_RUN_CONTENTS) {
                try (PreparedStatement statement =
                        connection.prepareStatement(sql)) {
                    statement.setString(1, runId);
                    statement.executeUpdate();
                }
            }
            insertQuestions(runId, gradingRun.getAssessment());

            List<Submission> submissions = gradingRun.getSubmissions();
            for (int index = 0; index < submissions.size(); index++) {
                insertSubmission(runId, submissions.get(index), index);
            }
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
    // Generated with Claude Opus 5.5; reviewed by Huxley
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
    // Generated with Claude Opus 5.5; reviewed by Huxley
    @Override
    public synchronized List<GradingRun> listRuns()
            throws PersistenceException {
        requireOpen();

        List<String> runIds = new ArrayList<>();
        try (PreparedStatement statement =
                connection.prepareStatement(SELECT_RUN_IDS);
                ResultSet rows = statement.executeQuery()) {
            while (rows.next()) {
                runIds.add(rows.getString("run_id"));
            }
        } catch (SQLException exception) {
            throw new PersistenceException("The saved runs could not be "
                    + "listed: " + exception.getMessage(), exception);
        }

        List<GradingRun> runs = new ArrayList<>();
        for (String runId : runIds) {
            runs.add(loadRun(runId));
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
    // Generated with Claude Opus 5.5; reviewed by Huxley
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
            int position = findPosition(runId, username);
            for (String sql : DELETE_SUBMISSION) {
                try (PreparedStatement statement =
                        connection.prepareStatement(sql)) {
                    statement.setString(1, runId);
                    statement.setString(2, username);
                    statement.executeUpdate();
                }
            }
            insertSubmission(runId, submission, position);
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
    // Generated with Claude Opus 5.5; reviewed by Huxley
    @Override
    public synchronized void updateFixStatus(String anomalyId,
            FixStatus fixStatus) throws PersistenceException {
        requireOpen();
        if (anomalyId == null || fixStatus == null) {
            throw new PersistenceException("There is no fix status to "
                    + "save.");
        }

        try (PreparedStatement statement =
                connection.prepareStatement(UPDATE_FIX_STATUS)) {
            statement.setString(1, fixStatus.name());
            statement.setString(2, anomalyId);
            statement.executeUpdate();
        } catch (SQLException exception) {
            throw new PersistenceException("The fix status of anomaly "
                    + anomalyId + " could not be saved: "
                    + exception.getMessage(), exception);
        }
    }

    /**
     * Adds one entry to the audit log.
     *
     * @param auditEntry the audit entry
     * @throws PersistenceException if the operation fails
     */
    // Generated with Claude Opus 5.5; reviewed by Huxley
    @Override
    public synchronized void appendAudit(AuditEntry auditEntry)
            throws PersistenceException {
        requireOpen();
        if (auditEntry == null) {
            throw new PersistenceException("There is no audit entry to "
                    + "save.");
        }

        try (PreparedStatement statement =
                connection.prepareStatement(INSERT_AUDIT)) {
            statement.setString(1, textOf(auditEntry.getTimestamp()));
            statement.setString(2, auditEntry.getAction());
            statement.setString(3, auditEntry.getDetail());
            statement.executeUpdate();
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
    // Generated with Claude Opus 5.5; reviewed by Huxley
    @Override
    public synchronized List<AuditEntry> listAudit()
            throws PersistenceException {
        requireOpen();

        List<AuditEntry> entries = new ArrayList<>();
        try (PreparedStatement statement =
                connection.prepareStatement(SELECT_AUDIT);
                ResultSet rows = statement.executeQuery()) {
            while (rows.next()) {
                entries.add(new AuditEntry(
                        parseTime(rows.getString("logged_at")),
                        rows.getString("action"),
                        rows.getString("detail")));
            }
        } catch (SQLException exception) {
            throw new PersistenceException("The audit log could not be "
                    + "read: " + exception.getMessage(), exception);
        } catch (DateTimeParseException exception) {
            throw new PersistenceException("The audit log holds a time "
                    + "that cannot be read: " + exception.getMessage(),
                    exception);
        }
        return entries;
    }

    /**
     * Creates the tables if they do not exist yet.
     *
     * @throws PersistenceException if the operation fails
     */
    // Generated with Claude Opus 5.5; reviewed by Huxley
    private void createTables() throws PersistenceException {
        // Fixed text with no values in it, so a plain Statement is safe
        try (Statement statement = connection.createStatement()) {
            for (String sql : CREATE_TABLES) {
                statement.executeUpdate(sql);
            }
        } catch (SQLException exception) {
            throw new PersistenceException("The tables of " + databaseFile
                    + " could not be created: " + exception.getMessage(),
                    exception);
        }
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
        try (PreparedStatement statement =
                connection.prepareStatement(INSERT_RUN)) {
            statement.setString(1, gradingRun.getId());
            statement.setString(2, textOf(gradingRun.getStartedAt()));
            statement.setString(3, section == null ? null
                    : section.getModuleCode());
            statement.setString(4, section == null ? null
                    : section.getSectionCode());
            statement.setString(5, assessment == null ? null
                    : assessment.getName());
            statement.setString(6, assessment == null ? null
                    : assessment.getModuleCode());
            statement.setString(7, assessment == null ? null
                    : String.join(LIST_SEPARATOR,
                            assessment.getPlaceholderFolderNames()));
            statement.executeUpdate();
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
        try (PreparedStatement statement =
                connection.prepareStatement(INSERT_QUESTION)) {
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
        }
    }

    // Writes one submission with its results, test cases and anomalies
    private void insertSubmission(String runId, Submission submission,
            int position) throws SQLException {
        String username = submission.getUsername();
        Student student = submission.getStudent();
        try (PreparedStatement statement =
                connection.prepareStatement(INSERT_SUBMISSION)) {
            statement.setString(1, runId);
            statement.setString(2, username);
            statement.setInt(3, position);
            statement.setString(4, textOf(submission.getStatus()));
            statement.setString(5, textOf(submission.getOriginalPath()));
            statement.setString(6, textOf(submission.getWorkingCopyPath()));
            statement.setString(7, submission.getIngestFailure());
            statement.setString(8, student == null ? null
                    : student.getOrgDefinedId());
            statement.setString(9, student == null ? null
                    : student.getUsername());
            statement.setString(10, student == null ? null
                    : student.getLastName());
            statement.setString(11, student == null ? null
                    : student.getFirstName());
            statement.setString(12, student == null ? null
                    : student.getEmail());
            statement.executeUpdate();
        }

        insertQuestionResults(runId, username,
                submission.getQuestionResults());
        insertAnomalies(runId, username, submission.getAnomalies());
    }

    // Writes the score of each question and each of its test cases
    private void insertQuestionResults(String runId, String username,
            List<QuestionResult> results) throws SQLException {
        try (PreparedStatement resultStatement =
                connection.prepareStatement(INSERT_QUESTION_RESULT);
                PreparedStatement testStatement =
                        connection.prepareStatement(INSERT_TEST_CASE)) {
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
        }
    }

    // Writes each anomaly of one submission
    private void insertAnomalies(String runId, String username,
            List<Anomaly> anomalies) throws SQLException {
        try (PreparedStatement statement =
                connection.prepareStatement(INSERT_ANOMALY)) {
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
        }
    }

    // The place a submission already has in the run, or the next free one
    private int findPosition(String runId,
            String username) throws SQLException {
        try (PreparedStatement statement =
                connection.prepareStatement(SELECT_POSITION)) {
            statement.setString(1, runId);
            statement.setString(2, username);
            try (ResultSet rows = statement.executeQuery()) {
                if (rows.next()) {
                    return rows.getInt("position");
                }
            }
        }

        try (PreparedStatement statement =
                connection.prepareStatement(SELECT_NEXT_POSITION)) {
            statement.setString(1, runId);
            try (ResultSet rows = statement.executeQuery()) {
                rows.next();
                return rows.getInt(1);
            }
        }
    }

    // Rebuilds the run, its assessment and its section, without the
    // submissions. Returns null if the run was never saved.
    private GradingRun readRunRow(String runId) throws SQLException {
        Assessment assessment = null;
        Section section = null;
        LocalDateTime startedAt = null;
        try (PreparedStatement statement =
                connection.prepareStatement(SELECT_RUN)) {
            statement.setString(1, runId);
            try (ResultSet rows = statement.executeQuery()) {
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
            }
        }

        try (PreparedStatement statement =
                connection.prepareStatement(SELECT_QUESTIONS)) {
            statement.setString(1, runId);
            try (ResultSet rows = statement.executeQuery()) {
                while (rows.next()) {
                    assessment.addQuestion(new Question(
                            rows.getString("question_id"),
                            rows.getString("folder_name"),
                            rows.getString("file_name"),
                            rows.getDouble("max_score")));
                }
            }
        }
        return new GradingRun(runId, assessment, section, startedAt);
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
        try (PreparedStatement statement =
                connection.prepareStatement(SELECT_SUBMISSIONS)) {
            statement.setString(1, gradingRun.getId());
            try (ResultSet rows = statement.executeQuery()) {
                while (rows.next()) {
                    Submission submission = readSubmission(rows,
                            gradingRun.getSection());
                    gradingRun.addSubmission(submission);
                    submissions.put(submission.getUsername(), submission);
                }
            }
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
            submission.setStatus(SubmissionStatus.valueOf(status));
        }

        String orgDefinedId = row.getString("org_defined_id");
        if (orgDefinedId != null) {
            submission.setStudent(new Student(orgDefinedId,
                    row.getString("student_username"),
                    row.getString("last_name"),
                    row.getString("first_name"),
                    row.getString("email")));
        }
        return submission;
    }

    // Adds each saved question result to its submission and returns them
    // by username and question id, so test cases can find them
    private Map<String, QuestionResult> readQuestionResults(String runId,
            Assessment assessment, Map<String, Submission> submissions)
            throws SQLException {
        Map<String, QuestionResult> results = new HashMap<>();
        try (PreparedStatement statement =
                connection.prepareStatement(SELECT_QUESTION_RESULTS)) {
            statement.setString(1, runId);
            try (ResultSet rows = statement.executeQuery()) {
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
            }
        }
        return results;
    }

    // Adds each saved test case to its question result
    private void readTestCases(String runId,
            Map<String, QuestionResult> results) throws SQLException {
        try (PreparedStatement statement =
                connection.prepareStatement(SELECT_TEST_CASES)) {
            statement.setString(1, runId);
            try (ResultSet rows = statement.executeQuery()) {
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
            }
        }
    }

    // Adds each saved anomaly, with its fix status, to its submission
    private void readAnomalies(String runId,
            Map<String, Submission> submissions) throws SQLException {
        try (PreparedStatement statement =
                connection.prepareStatement(SELECT_ANOMALIES)) {
            statement.setString(1, runId);
            try (ResultSet rows = statement.executeQuery()) {
                while (rows.next()) {
                    String username = rows.getString("username");
                    Submission submission = submissions.get(username);
                    if (submission != null) {
                        submission.addAnomaly(readAnomaly(rows, username));
                    }
                }
            }
        }
    }

    // Rebuilds one anomaly from a row
    private Anomaly readAnomaly(ResultSet row,
            String username) throws SQLException {
        String type = row.getString("type");
        String severity = row.getString("severity");
        Anomaly anomaly = new Anomaly(row.getString("anomaly_id"),
                type == null ? null : AnomalyType.valueOf(type),
                severity == null ? null : Severity.valueOf(severity),
                username, row.getString("message"),
                row.getString("suggested_action"));

        String fixStatus = row.getString("fix_status");
        if (fixStatus != null) {
            anomaly.setFixStatus(FixStatus.valueOf(fixStatus));
        }
        return anomaly;
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
