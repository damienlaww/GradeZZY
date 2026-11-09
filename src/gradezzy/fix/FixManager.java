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

import java.nio.file.Path;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

import gradezzy.model.Anomaly;
import gradezzy.model.FixStatus;
import gradezzy.model.Submission;

/**
 * Applies fixes and lets the instructor approve or undo them.
 *
 * <p>Shared by every grading thread, so each public method is synchronized.
 */
public class FixManager {

    // Starts the reason kept for an error no fixer expected
    private static final String UNEXPECTED_FAILURE =
            "The fix stopped with an unexpected error: ";

    // The fixers available, in the order they run
    private final List<Fixer> fixers = new ArrayList<>();

    // Every fix applied so far
    private final List<FixRecord> fixRecords = new ArrayList<>();

    // Why a fix failed, by username and then by anomaly id
    private final Map<String, Map<String, String>> failureReasons =
            new HashMap<>();

    // The key of each rejected fix skipped, by username and anomaly id
    private final Map<String, Map<String, String>> skippedFixKeys =
            new HashMap<>();

    /**
     * Creates a new fix manager.
     */
    public FixManager() {
        // nothing to set up
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
     * Applies every available fix to one submission, skipping the fixes
     * the instructor undid for it.
     *
     * @param submission the submission
     * @return the fixes that were applied
     */
    public synchronized List<FixRecord> applyAll(Submission submission) {
        return applyAll(submission, findRejectedFixes(submission));
    }

    /**
     * Applies every available fix to one submission. Older fixes and
     * failure reasons for the same student are dropped first. A failed
     * fix is skipped and its reason kept, and an anomaly that is already
     * fixed is not offered to the next fixer. A fix whose key is in the
     * rejected list is not applied and its anomaly is marked UNDONE, so
     * the student is graded on the files as submitted.
     *
     * @param submission the submission
     * @param rejectedKeys the keys of the fixes the instructor undid
     * @return the fixes that were applied
     */
    public synchronized List<FixRecord> applyAll(Submission submission,
            List<String> rejectedKeys) {
        forgetFixesOf(submission.getUsername());
        failureReasons.remove(submission.getUsername());
        skippedFixKeys.remove(submission.getUsername());
        return applyTo(submission,
                new ArrayList<>(submission.getAnomalies()), rejectedKeys);
    }

    /**
     * Applies the available fixes to some anomalies of a submission only,
     * keeping the fixes and failure reasons already recorded for it, and
     * skipping the fixes the instructor undid.
     *
     * @param submission the submission
     * @param anomalies the anomalies to fix
     * @return the fixes that were applied
     */
    public synchronized List<FixRecord> applyTo(Submission submission,
            List<Anomaly> anomalies) {
        return applyTo(submission, anomalies,
                findRejectedFixes(submission));
    }

    /**
     * Applies the available fixes to some anomalies of a submission only,
     * keeping the fixes and failure reasons already recorded for it. A fix
     * whose key is in the rejected list is skipped and its anomaly marked
     * UNDONE. Other anomalies of the same skipped fix are removed, so it
     * shows as one card, as it does when applied.
     *
     * @param submission the submission
     * @param anomalies the anomalies to fix
     * @param rejectedKeys the keys of the fixes the instructor undid
     * @return the fixes that were applied
     */
    public synchronized List<FixRecord> applyTo(Submission submission,
            List<Anomaly> anomalies, List<String> rejectedKeys) {
        List<FixRecord> applied = new ArrayList<>();
        List<String> skippedKeys = new ArrayList<>();
        List<Anomaly> repeats = new ArrayList<>();

        for (Fixer fixer : fixers) {
            for (Anomaly anomaly : anomalies) {
                FixRecord fixRecord = null;
                if (isOpen(submission, anomaly) && fixer.canFix(anomaly)) {
                    fixRecord = tryApply(fixer, submission, anomaly,
                            rejectedKeys, skippedKeys, repeats);
                }
                if (fixRecord != null) {
                    anomaly.setFixStatus(FixStatus.APPLIED);
                    anomaly.setFixDescription(fixRecord.getDescription());
                    forgetFailure(anomaly);
                    fixRecords.add(fixRecord);
                    applied.add(fixRecord);
                }
            }
        }

        // Removed only now, as fixers find a folder by its problem's place
        submission.getAnomalies().removeAll(repeats);
        return applied;
    }

    /**
     * Returns why the automatic fix for an anomaly failed, in applyAll or
     * applyTo. The reason is dropped when a later fix of the anomaly
     * works or applyAll runs again for its student.
     *
     * @param anomaly the anomaly
     * @return the reason, or null if no fix failed for it
     */
    public synchronized String getFailureReason(Anomaly anomaly) {
        if (anomaly == null) {
            return null;
        }

        Map<String, String> reasons =
                failureReasons.get(anomaly.getUsername());
        if (reasons == null) {
            return null;
        }
        return reasons.get(anomaly.getId());
    }

    /**
     * Returns the key of the rejected fix that grading skipped for an
     * anomaly, so the fix can be applied again. It is dropped when
     * applyAll runs again for the anomaly's student.
     *
     * @param anomaly the anomaly marked UNDONE by grading
     * @return the fix key, or null if no rejected fix was skipped for it
     */
    public synchronized String findRejectedKey(Anomaly anomaly) {
        if (anomaly == null) {
            return null;
        }

        Map<String, String> keys = skippedFixKeys.get(anomaly.getUsername());
        if (keys == null) {
            return null;
        }
        return keys.get(anomaly.getId());
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

    // Returns a copy of the keys of the fixes the instructor undid.
    private List<String> findRejectedFixes(Submission submission) {
        // A key added during this pass waits for the next one
        return new ArrayList<>(submission.getRejectedFixes());
    }

    // Says whether an anomaly still waits for a fix in this pass.
    private boolean isOpen(Submission submission, Anomaly anomaly) {
        return anomaly.getFixStatus() != FixStatus.APPLIED
                && anomaly.getFixStatus() != FixStatus.UNDONE
                && submission.getAnomalies().contains(anomaly);
    }

    // Applies one fix, or skips it, or returns null if it fails.
    private FixRecord tryApply(Fixer fixer, Submission submission,
            Anomaly anomaly, List<String> rejectedKeys,
            List<String> skippedKeys, List<Anomaly> repeats) {
        try {
            Path pathToChange = fixer.findPathToChange(submission, anomaly);
            String plannedKey = null;
            if (pathToChange != null) {
                plannedKey = makeKey(fixer.getName(), submission,
                        pathToChange);
            }
            if (plannedKey != null && rejectedKeys.contains(plannedKey)) {
                skip(anomaly, plannedKey, skippedKeys, repeats);
                return null;
            }

            FixRecord fixRecord = fixer.apply(submission, anomaly);
            if (fixRecord != null) {
                fixRecord.setFixKey(makeKey(fixer.getName(), submission,
                        fixRecord.getOriginalPath()));
            }
            return fixRecord;
        } catch (FixException exception) {
            rememberFailure(anomaly, exception.getMessage());
            return null;
        } catch (RuntimeException exception) {
            rememberFailure(anomaly, UNEXPECTED_FAILURE + exception);
            return null;
        }
    }

    // Marks a rejected fix UNDONE.
    private void skip(Anomaly anomaly, String fixKey,
            List<String> skippedKeys, List<Anomaly> repeats) {
        // applyTo removes a repeat once every fixer has run
        if (skippedKeys.contains(fixKey)) {
            repeats.add(anomaly);
        } else {
            skippedKeys.add(fixKey);
            rememberSkip(anomaly, fixKey);
        }
        anomaly.setFixStatus(FixStatus.UNDONE);
    }

    // Keeps the key of the rejected fix skipped for an anomaly.
    private void rememberSkip(Anomaly anomaly, String fixKey) {
        Map<String, String> keys = skippedFixKeys.get(anomaly.getUsername());
        if (keys == null) {
            keys = new HashMap<>();
            skippedFixKeys.put(anomaly.getUsername(), keys);
        }
        keys.put(anomaly.getId(), fixKey);
    }

    // Makes a fix key from the path the student uploaded.
    private String makeKey(String fixerName, Submission submission,
            Path path) {
        // Undo this student's earlier fixes on the path, newest first
        Path uploadedPath = path;
        for (int i = fixRecords.size() - 1; i >= 0; i--) {
            FixRecord earlier = fixRecords.get(i);
            if (earlier.getAnomaly().getUsername().equals(
                    submission.getUsername())) {
                uploadedPath = earlier.findPathBefore(uploadedPath);
            }
        }
        return FixRecord.makeFixKey(fixerName,
                submission.getWorkingCopyPath(), uploadedPath);
    }

    // Keeps why the fix for an anomaly failed.
    private void rememberFailure(Anomaly anomaly, String reason) {
        Map<String, String> reasons =
                failureReasons.get(anomaly.getUsername());
        if (reasons == null) {
            reasons = new HashMap<>();
            failureReasons.put(anomaly.getUsername(), reasons);
        }
        reasons.put(anomaly.getId(), reason);
    }

    // Drops the failure reason of an anomaly that a later fixer fixed.
    private void forgetFailure(Anomaly anomaly) {
        Map<String, String> reasons =
                failureReasons.get(anomaly.getUsername());
        if (reasons != null) {
            reasons.remove(anomaly.getId());
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
