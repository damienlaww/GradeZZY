/*
 * File: FixManager.java
 *
 * Purpose: Applies fixes automatically and lets the instructor approve or undo
 * them, one at a time or in bulk.
 *
 * Where it fits: Called by FixStage during grading and by AnomalyService when
 * the instructor approves or undoes a fix on Review.
 */
package gradezzy.fix;

import java.util.ArrayList;
import java.util.List;

import gradezzy.model.Anomaly;
import gradezzy.model.FixStatus;
import gradezzy.model.Submission;

/**
 * Applies fixes and lets the instructor approve or undo them.
 *
 * <p>Shared by every grading thread, so each public method is synchronized.
 */
public class FixManager {

    // The fixers available, in the order they run
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
     * Returns a copy of every fix applied so far.
     *
     * @return every fix applied so far
     */
    public synchronized List<FixRecord> getFixRecords() {
        return new ArrayList<>(fixRecords);
    }

    /**
     * Registers one more fixer. Fixers run in the order they are added.
     *
     * @param fixer the fixer
     */
    public synchronized void addFixer(Fixer fixer) {
        fixers.add(fixer);
    }

    /**
     * Applies every available fix to one submission. Older fixes for the
     * same student are dropped first, and a failed fix is skipped.
     *
     * @param submission the submission
     * @return the fixes that were applied
     */
    public synchronized List<FixRecord> applyAll(Submission submission) {
        forgetFixesOf(submission.getUsername());
        List<FixRecord> applied = new ArrayList<>();
        List<Anomaly> anomalies = new ArrayList<>(submission.getAnomalies());

        for (Fixer fixer : fixers) {
            for (Anomaly anomaly : anomalies) {
                FixRecord fixRecord = null;
                if (fixer.canFix(anomaly)) {
                    fixRecord = tryApply(fixer, submission, anomaly);
                }
                if (fixRecord != null) {
                    anomaly.setFixStatus(FixStatus.APPLIED);
                    fixRecords.add(fixRecord);
                    applied.add(fixRecord);
                }
            }
        }
        return applied;
    }

    /**
     * Finds the fix made for an anomaly.
     *
     * @param anomaly the anomaly
     * @return the fix record, or null if no fix was applied for it
     */
    public synchronized FixRecord findRecord(Anomaly anomaly) {
        for (FixRecord fixRecord : fixRecords) {
            if (fixRecord.getAnomaly().getId().equals(anomaly.getId())) {
                return fixRecord;
            }
        }
        return null;
    }

    /**
     * Marks an applied fix as approved by the instructor.
     *
     * @param fixRecord the fix record, or null, which does nothing
     */
    public synchronized void approve(FixRecord fixRecord) {
        if (fixRecord == null) {
            return;
        }

        Anomaly anomaly = fixRecord.getAnomaly();
        if (anomaly.getFixStatus() == FixStatus.APPLIED) {
            anomaly.setFixStatus(FixStatus.APPROVED);
        }
    }

    /**
     * Reverses one fix. Undoing it twice does nothing.
     *
     * @param fixRecord the fix record, or null, which does nothing
     * @throws FixException if the operation fails
     */
    public synchronized void undo(FixRecord fixRecord) throws FixException {
        if (fixRecord == null) {
            return;
        }

        Anomaly anomaly = fixRecord.getAnomaly();
        if (anomaly.getFixStatus() == FixStatus.UNDONE) {
            return;
        }

        Fixer fixer = findFixerNamed(fixRecord.getFixerName());
        if (fixer == null) {
            throw new FixException("No fixer named "
                    + fixRecord.getFixerName() + " is available to undo "
                    + "this fix.");
        }
        fixer.undo(fixRecord);
        anomaly.setFixStatus(FixStatus.UNDONE);
    }

    /**
     * Approves several fixes at once.
     *
     * @param records the records
     */
    public synchronized void approveAll(List<FixRecord> records) {
        for (FixRecord fixRecord : records) {
            approve(fixRecord);
        }
    }

    /**
     * Undoes several fixes, then throws the first failure, if any.
     *
     * @param records the records
     * @throws FixException if the operation fails
     */
    public synchronized void undoAll(List<FixRecord> records)
            throws FixException {
        FixException firstFailure = null;
        for (FixRecord fixRecord : records) {
            try {
                undo(fixRecord);
            } catch (FixException exception) {
                if (firstFailure == null) {
                    firstFailure = exception;
                }
            }
        }

        if (firstFailure != null) {
            throw firstFailure;
        }
    }

    // Applies one fix, or returns null if it fails.
    private FixRecord tryApply(Fixer fixer, Submission submission,
            Anomaly anomaly) {
        try {
            return fixer.apply(submission, anomaly);
        } catch (FixException exception) {
            return null;
        } catch (RuntimeException exception) {
            return null;
        }
    }

    // Removes the fixes recorded for one student.
    private void forgetFixesOf(String username) {
        for (int i = fixRecords.size() - 1; i >= 0; i--) {
            FixRecord fixRecord = fixRecords.get(i);
            if (fixRecord.getAnomaly().getUsername().equals(username)) {
                fixRecords.remove(i);
            }
        }
    }

    // Finds the fixer that made a fix, by its name.
    private Fixer findFixerNamed(String fixerName) {
        for (Fixer fixer : fixers) {
            if (fixer.getName().equals(fixerName)) {
                return fixer;
            }
        }
        return null;
    }
}
