/*
 * File: FixManager.java
 *
 * Purpose: Applies fixes automatically and lets the instructor approve or undo
 * them, one at a time or in bulk.
 *
 * Where it fits: Called by FixStage during grading and by AnomalyService from
 * the anomaly log screen.
 */
package gradezzy.fix;

import java.util.ArrayList;
import java.util.List;

import gradezzy.model.Submission;

/**
 * Applies fixes automatically and lets the instructor approve or undo them, one
 * at a time or in bulk.
 *
 * <p>Tier: MUST. Owner: Jun Sheng.
 */
public class FixManager {

    // The fixers available
    private final List<Fixer> fixers = new ArrayList<>();

    // Every fix applied so far
    private final List<FixRecord> fixRecords = new ArrayList<>();

    /**
     * Creates a new fix manager.
     */
    public FixManager() {
        // nothing to set up yet
    }

    /**
     * Returns every fix applied so far.
     *
     * @return every fix applied so far
     */
    public List<FixRecord> getFixRecords() {
        return fixRecords;
    }

    /**
     * Registers one more fixer.
     *
     * @param fixer the fixer
     */
    public void addFixer(Fixer fixer) {
        fixers.add(fixer);
    }

    /**
     * Applies every available fix to one submission.
     *
     * @param submission the submission
     * @return the fixes that were applied
     */
    public List<FixRecord> applyAll(Submission submission) {
        // TODO(Jun Sheng): implement: for each anomaly, finding a fixer with
        //     canFix, calling apply, storing the record and setting the status
        //     to APPLIED
        return new ArrayList<>();
    }

    /**
     * Marks a fix as accepted by the instructor.
     *
     * <p>Tier: SHOULD.
     *
     * @param fixRecord the fix record
     */
    public void approve(FixRecord fixRecord) {
        // TODO(Jun Sheng): implement: setting the anomaly status to APPROVED
    }

    /**
     * Reverses one fix.
     *
     * <p>Tier: SHOULD.
     *
     * @param fixRecord the fix record
     * @throws FixException if the operation fails
     */
    public void undo(FixRecord fixRecord) throws FixException {
        // TODO(Jun Sheng): implement: calling undo on the right fixer and
        //     setting the status to UNDONE
    }

    /**
     * Approves several fixes at once.
     *
     * <p>Tier: SHOULD.
     *
     * @param records the records
     */
    public void approveAll(List<FixRecord> records) {
        // TODO(Jun Sheng): implement: calling approve for each record
    }

    /**
     * Undoes several fixes at once.
     *
     * <p>Tier: SHOULD.
     *
     * @param records the records
     * @throws FixException if the operation fails
     */
    public void undoAll(List<FixRecord> records) throws FixException {
        // TODO(Jun Sheng): implement: calling undo for each record
    }
}
