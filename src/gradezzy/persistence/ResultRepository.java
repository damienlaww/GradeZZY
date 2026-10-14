/*
 * File: ResultRepository.java
 *
 * Purpose: Where grading state is saved and loaded.
 *
 * Where it fits: The only door to storage. The rest of the code uses this
 * interface and never sees SQL, so SQLite can be replaced by another store.
 */
package gradezzy.persistence;

import java.util.List;

import gradezzy.model.AuditEntry;
import gradezzy.model.FixStatus;
import gradezzy.model.GradingRun;
import gradezzy.model.Submission;

/**
 * Where grading state is saved and loaded.
 *
 * <p>Tier: SHOULD. Owner: Huxley.
 */
public interface ResultRepository {

    /**
     * Opens the store and prepares it for use.
     *
     * @throws PersistenceException if the operation fails
     */
    void open() throws PersistenceException;

    /**
     * Closes the store. Safe to call twice.
     */
    void close();

    /**
     * Saves a run and everything in it.
     *
     * @param gradingRun the grading run
     * @throws PersistenceException if the operation fails
     */
    void saveRun(GradingRun gradingRun) throws PersistenceException;

    /**
     * Loads a saved run so it can be viewed or resumed.
     *
     * @param runId the run id
     * @return the run, or null if the id is unknown
     * @throws PersistenceException if the operation fails
     */
    GradingRun loadRun(String runId) throws PersistenceException;

    /**
     * Lists every saved run, newest first.
     *
     * @return the saved runs
     * @throws PersistenceException if the operation fails
     */
    List<GradingRun> listRuns() throws PersistenceException;

    /**
     * Saves the current state of one submission of a run.
     *
     * @param runId the run id
     * @param submission the submission
     * @throws PersistenceException if the operation fails
     */
    void saveSubmission(String runId,
            Submission submission) throws PersistenceException;

    /**
     * Saves the new status of a fix after approve or undo.
     *
     * @param anomalyId the anomaly id
     * @param fixStatus the fix status
     * @throws PersistenceException if the operation fails
     */
    void updateFixStatus(String anomalyId,
            FixStatus fixStatus) throws PersistenceException;

    /**
     * Adds one entry to the audit log.
     *
     * @param auditEntry the audit entry
     * @throws PersistenceException if the operation fails
     */
    void appendAudit(AuditEntry auditEntry) throws PersistenceException;

    /**
     * Lists the audit log, oldest first.
     *
     * @return the audit entries
     * @throws PersistenceException if the operation fails
     */
    List<AuditEntry> listAudit() throws PersistenceException;
}
