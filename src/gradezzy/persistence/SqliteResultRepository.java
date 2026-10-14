/*
 * File: SqliteResultRepository.java
 *
 * Purpose: Saves grading state in a SQLite file through JDBC.
 *
 * Where it fits: The only class that contains SQL. JDBC is not taught in CS102,
 * so it is kept inside this one file.
 */
package gradezzy.persistence;

import java.util.ArrayList;
import java.util.List;

import gradezzy.model.AuditEntry;
import gradezzy.model.FixStatus;
import gradezzy.model.GradingRun;
import gradezzy.model.Submission;

/**
 * Saves grading state in a SQLite file through JDBC.
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
     * @throws PersistenceException if the operation fails
     */
    @Override
    public void open() throws PersistenceException {
        // TODO(Huxley): implement: DriverManager.getConnection(JDBC_URL_PREFIX
        //     + databaseFile) and then createTables
    }

    /**
     * Closes the store. Safe to call twice.
     */
    @Override
    public void close() {
        // TODO(Huxley): implement: closing the JDBC connection if it is open
    }

    /**
     * Saves a run and everything in it.
     *
     * @param gradingRun the grading run
     * @throws PersistenceException if the operation fails
     */
    @Override
    public void saveRun(GradingRun gradingRun) throws PersistenceException {
        // TODO(Huxley): implement: INSERT statements for the run and its
        //     submissions
    }

    /**
     * Loads a saved run so it can be viewed or resumed.
     *
     * @param runId the run id
     * @return the run, or null if the id is unknown
     * @throws PersistenceException if the operation fails
     */
    @Override
    public GradingRun loadRun(String runId) throws PersistenceException {
        // TODO(Huxley): implement: SELECT statements that rebuild the run
        return null;
    }

    /**
     * Lists every saved run, newest first.
     *
     * @return the saved runs
     * @throws PersistenceException if the operation fails
     */
    @Override
    public List<GradingRun> listRuns() throws PersistenceException {
        // TODO(Huxley): implement: a SELECT of all runs, newest first
        return new ArrayList<>();
    }

    /**
     * Saves the current state of one submission of a run.
     *
     * @param runId the run id
     * @param submission the submission
     * @throws PersistenceException if the operation fails
     */
    @Override
    public void saveSubmission(String runId,
            Submission submission) throws PersistenceException {
        // TODO(Huxley): implement: an insert or update of one submission, its
        //     results and its anomalies
    }

    /**
     * Saves the new status of a fix after approve or undo.
     *
     * @param anomalyId the anomaly id
     * @param fixStatus the fix status
     * @throws PersistenceException if the operation fails
     */
    @Override
    public void updateFixStatus(String anomalyId,
            FixStatus fixStatus) throws PersistenceException {
        // TODO(Huxley): implement: an UPDATE of the anomaly row
    }

    /**
     * Adds one entry to the audit log.
     *
     * @param auditEntry the audit entry
     * @throws PersistenceException if the operation fails
     */
    @Override
    public void appendAudit(AuditEntry auditEntry) throws PersistenceException {
        // TODO(Huxley): implement: an INSERT into the audit table
    }

    /**
     * Lists the audit log, oldest first.
     *
     * @return the audit entries
     * @throws PersistenceException if the operation fails
     */
    @Override
    public List<AuditEntry> listAudit() throws PersistenceException {
        // TODO(Huxley): implement: a SELECT of all audit rows, oldest first
        return new ArrayList<>();
    }

    /**
     * Creates the tables if they do not exist yet.
     *
     * @throws PersistenceException if the operation fails
     */
    private void createTables() throws PersistenceException {
        // TODO(Huxley): implement: CREATE TABLE IF NOT EXISTS statements for
        //     runs, submissions, question results, anomalies and audit entries
    }
}
