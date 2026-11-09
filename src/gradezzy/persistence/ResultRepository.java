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
import java.util.Map;

import gradezzy.model.AnswerGroupNote;
import gradezzy.model.AuditEntry;
import gradezzy.model.FixStatus;
import gradezzy.model.GradingRun;
import gradezzy.model.ScoreAdjustment;
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
     * Saves the instructor's review decision on one flagged anomaly, for
     * example REVIEWED or REJECTED. The decision is stored as text, so
     * this package does not depend on the window's own decision type.
     *
     * @param anomalyId the anomaly id
     * @param decision the decision name, or null to clear it
     * @throws PersistenceException if the operation fails
     */
    void updateDecision(String anomalyId,
            String decision) throws PersistenceException;

    /**
     * Loads the saved review decisions of a run.
     *
     * @param runId the run id
     * @return the decision names by anomaly id; anomalies with no
     *         decision are left out
     * @throws PersistenceException if the operation fails
     */
    Map<String, String> loadDecisions(String runId)
            throws PersistenceException;

    /**
     * Saves or removes the mark that the instructor checked a student's
     * 0, a total that no flag explains.
     *
     * @param runId the run id
     * @param username the student
     * @param checked true to save the check, false to remove it
     * @throws PersistenceException if the operation fails
     */
    void updateZeroCheck(String runId, String username,
            boolean checked) throws PersistenceException;

    /**
     * Lists the students of a run whose 0 the instructor checked.
     *
     * @param runId the run id
     * @return the usernames
     * @throws PersistenceException if the operation fails
     */
    List<String> loadZeroChecks(String runId) throws PersistenceException;

    /**
     * Saves a mark changed by hand, with its reason and earlier mark.
     *
     * @param runId the run id
     * @param adjustment the change
     * @throws PersistenceException if the operation fails
     */
    void saveAdjustment(String runId, ScoreAdjustment adjustment)
            throws PersistenceException;

    /**
     * Forgets a mark changed by hand, after it was undone.
     *
     * @param runId the run id
     * @param adjustment the change
     * @throws PersistenceException if the operation fails
     */
    void deleteAdjustment(String runId, ScoreAdjustment adjustment)
            throws PersistenceException;

    /**
     * Lists the marks changed by hand in a run, oldest first.
     *
     * @param runId the run id
     * @return the changes
     * @throws PersistenceException if the operation fails
     */
    List<ScoreAdjustment> listAdjustments(String runId)
            throws PersistenceException;

    /**
     * Saves the marker's name and check for one answer group, replacing
     * the one saved before. A note with no name and no check removes it.
     *
     * <p>Tier: COULD.
     *
     * @param runId the run id
     * @param note the note, keyed by its question and pattern
     * @throws PersistenceException if the operation fails
     */
    void saveAnswerGroupNote(String runId, AnswerGroupNote note)
            throws PersistenceException;

    /**
     * Lists the saved answer group notes of a run.
     *
     * <p>Tier: COULD.
     *
     * @param runId the run id
     * @return the notes, in no particular order
     * @throws PersistenceException if the operation fails
     */
    List<AnswerGroupNote> listAnswerGroupNotes(String runId)
            throws PersistenceException;

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
