/*
 * File: SqliteResultRepository.java
 *
 * Purpose: Saves grading state in a SQLite file through JDBC.
 *
 * Where it fits: The only way from the rest of the code to the SQLite file.
 * JDBC is not taught in CS102, so it stays inside this package: this class
 * owns the connection and the transactions, and the package-private table
 * classes beside it hold the SQL.
 */
package gradezzy.persistence;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.sql.Connection;
import java.sql.DriverManager;
import java.sql.SQLException;
import java.time.format.DateTimeParseException;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

import gradezzy.model.AuditEntry;
import gradezzy.model.FixStatus;
import gradezzy.model.GradingRun;
import gradezzy.model.ScoreAdjustment;
import gradezzy.model.Submission;

/**
 * Saves grading state in a SQLite file through JDBC.
 *
 * <p>Every public method is synchronized because grading saves results on a
 * background thread while the screens may read the audit log. Every save
 * runs in one transaction, so a run is never left half written.
 *
 * <p>This class opens and closes the one connection, starts, commits and
 * rolls back every transaction, and turns database errors into
 * PersistenceException. The SQL is in SqliteSchema (tables and columns
 * added later), RunTables, SubmissionTables, ReviewTables (fix status,
 * decisions and checked 0s), AdjustmentTable and AuditTable. They work on
 * the connection they are given and never commit or close it.
 *
 * <p>Tier: SHOULD. Owner: Huxley.
 */
public class SqliteResultRepository implements ResultRepository {

    /**
     * The start of every SQLite JDBC address.
     */
    public static final String JDBC_URL_PREFIX = "jdbc:sqlite:";

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
            Map<String, String> decisions =
                    ReviewTables.readDecisions(connection, runId);
            RunTables.insertRun(connection, gradingRun);
            SubmissionTables.deleteRunSubmissions(connection, runId);
            RunTables.deleteQuestions(connection, runId);
            RunTables.insertQuestions(connection, runId,
                    gradingRun.getAssessment());

            List<Submission> submissions = gradingRun.getSubmissions();
            for (int index = 0; index < submissions.size(); index++) {
                SubmissionTables.insertSubmission(connection, runId,
                        submissions.get(index), index);
            }
            ReviewTables.writeDecisions(connection, decisions);
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
            GradingRun gradingRun = RunTables.readRun(connection, runId);
            if (gradingRun == null) {
                return null;
            }

            SubmissionTables.readSubmissions(connection, gradingRun);
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
            runIds = RunTables.readRunIds(connection);
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
            Map<String, String> decisions =
                    ReviewTables.readDecisions(connection, runId);
            int position = SubmissionTables.findPosition(connection, runId,
                    username);
            SubmissionTables.deleteSubmission(connection, runId, username);
            SubmissionTables.insertSubmission(connection, runId, submission,
                    position);
            ReviewTables.writeDecisions(connection, decisions);
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
            ReviewTables.writeFixStatus(connection, anomalyId, fixStatus);
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
            ReviewTables.writeDecision(connection, anomalyId, decision);
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
            return ReviewTables.readDecisions(connection, runId);
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
            ReviewTables.writeZeroCheck(connection, runId, username,
                    checked);
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
            return ReviewTables.readZeroChecks(connection, runId);
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
        try {
            AdjustmentTable.insertAdjustment(connection, runId, adjustment);
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
        try {
            AdjustmentTable.deleteAdjustment(connection, runId, adjustment);
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
        try {
            return AdjustmentTable.readAdjustments(connection, runId);
        } catch (SQLException exception) {
            throw new PersistenceException("The changed marks could not be "
                    + "read: " + exception.getMessage(), exception);
        } catch (DateTimeParseException exception) {
            throw new PersistenceException("A changed mark has a time that "
                    + "cannot be read: " + exception.getMessage(), exception);
        }
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
            AuditTable.insertAudit(connection, auditEntry);
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
            return AuditTable.readAudit(connection);
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
            SqliteSchema.prepare(connection);
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

    // Loads one run for listRuns, or null if it cannot be read
    private GradingRun loadReadableRun(String runId) {
        try {
            return loadRun(runId);
        } catch (PersistenceException exception) {
            return null;
        }
    }
}
