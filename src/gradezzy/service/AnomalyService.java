/*
 * File: AnomalyService.java
 *
 * Purpose: What the Review step can do with the problems found: list
 * them, and approve or undo fixes one at a time or in bulk. Undoing a fix
 * grades the student again without it.
 *
 * Where it fits: Sits between the UI and FixManager so the screens hold no
 * logic.
 */
package gradezzy.service;

import java.util.ArrayList;
import java.util.List;
import java.util.Locale;

import gradezzy.fix.FixException;
import gradezzy.fix.FixManager;
import gradezzy.fix.FixRecord;
import gradezzy.grading.GradingException;
import gradezzy.model.Anomaly;
import gradezzy.model.FixStatus;
import gradezzy.model.GradingRun;
import gradezzy.model.Submission;
import gradezzy.persistence.PersistenceException;
import gradezzy.persistence.ResultRepository;

/**
 * What the Review step can do with the problems found.
 *
 * <p>Tier: SHOULD. Owner: Jing Yuan.
 */
public class AnomalyService {

    // The audit log actions this service records
    private static final String FIX_APPROVED = "Fix approved";
    private static final String FIX_UNDONE = "Fix undone";
    private static final String FIX_REOPENED = "Fix decision taken back";

    // Joins the run id and username in the key that groups fixes
    private static final String GROUP_SEPARATOR = "/";

    // The helper that applies and undoes fixes
    private final FixManager fixManager;

    // The store results are saved to
    private final ResultRepository repository;

    // The log of instructor actions, or null to log nothing
    private final AuditService auditService;

    // Grades a student again after an undo, or null to only put the
    // working copy back
    private final GradingService gradingService;

    /**
     * Creates a new anomaly service that logs nothing.
     *
     * @param fixManager the helper that applies and undoes fixes
     * @param repository the store results are saved to
     */
    public AnomalyService(FixManager fixManager, ResultRepository repository) {
        this(fixManager, repository, null);
    }

    /**
     * Creates a new anomaly service that writes every approve and undo to
     * the audit log.
     *
     * @param fixManager the helper that applies and undoes fixes
     * @param repository the store results are saved to
     * @param auditService the log of instructor actions, or null to log
     *        nothing
     */
    public AnomalyService(FixManager fixManager, ResultRepository repository,
            AuditService auditService) {
        this(fixManager, repository, auditService, null);
    }

    /**
     * Creates a new anomaly service that logs every approve and undo and
     * grades a student again when one of their fixes is undone.
     *
     * @param fixManager the helper that applies and undoes fixes
     * @param repository the store results are saved to
     * @param auditService the log of instructor actions, or null to log
     *        nothing
     * @param gradingService grades the student again after an undo, or
     *        null to only put the working copy back
     */
    public AnomalyService(FixManager fixManager, ResultRepository repository,
            AuditService auditService, GradingService gradingService) {
        this.fixManager = fixManager;
        this.repository = repository;
        this.auditService = auditService;
        this.gradingService = gradingService;
    }

    /**
     * Collects the anomalies of every submission in a run.
     *
     * @param gradingRun the grading run
     * @return all anomalies of the run, in submission order, or an empty
     *         list if there is no run
     */
    public List<Anomaly> listAnomalies(GradingRun gradingRun) {
        List<Anomaly> anomalies = new ArrayList<>();

        if (gradingRun == null) {
            return anomalies;
        }

        for (Submission submission : gradingRun.getSubmissions()) {
            anomalies.addAll(submission.getAnomalies());
        }

        return anomalies;
    }

    /**
     * Approves the fix made for an anomaly. Only an APPLIED fix can be
     * approved; any other status is left as it is.
     *
     * <p>A run loaded from the database after a restart has no fix
     * records in memory, so its fix is approved by changing the status
     * alone. Approving changes no file, so nothing else is needed.
     *
     * @param anomaly the anomaly
     */
    public void approveFix(Anomaly anomaly) {
        if (anomaly == null) {
            return;
        }

        FixStatus before = anomaly.getFixStatus();
        FixRecord fixRecord = fixManager.findRecord(anomaly);
        if (fixRecord != null) {
            fixManager.approve(fixRecord);
            copyStatus(fixRecord, anomaly);
        } else if (anomaly.getFixStatus() == FixStatus.APPLIED) {
            anomaly.setFixStatus(FixStatus.APPROVED);
        }

        saveStatus(anomaly);
        if (before != FixStatus.APPROVED
                && anomaly.getFixStatus() == FixStatus.APPROVED) {
            record(FIX_APPROVED, anomaly, "");
        }
    }

    /**
     * Takes back the approval of a fix, so it waits for a decision again.
     * The fixed files stay as they are and the marks do not change; only
     * an APPROVED fix is changed.
     *
     * @param anomaly the anomaly whose fix was approved
     * @return true when the fix waits for a decision again
     */
    public boolean reopenFix(Anomaly anomaly) {
        if (anomaly == null || anomaly.getFixStatus() != FixStatus.APPROVED) {
            return false;
        }
        FixRecord fixRecord = fixManager.findRecord(anomaly);
        if (fixRecord != null) {
            fixRecord.getAnomaly().setFixStatus(FixStatus.APPLIED);
        }
        anomaly.setFixStatus(FixStatus.APPLIED);
        saveStatus(anomaly);
        record(FIX_REOPENED, anomaly, "");
        return true;
    }

    /**
     * Undoes the fix made for an anomaly and grades the student again
     * without it, so the marks match the files as submitted. The fix's
     * key is added to the student's getRejectedFixes list, which grading
     * keeps, so a later Retry or Resume skips the fix too. Grading again
     * runs the student's testers, so it takes a few seconds: call this
     * off the window's thread.
     *
     * <p>If the student cannot be graded again (for example the tester
     * folder is gone), the working copy is still put back, the fix still
     * shows as undone and the result says why the marks did not change.
     * If the working copy cannot be put back either, nothing changes and
     * the fix stays APPLIED, so the caller can tell.
     *
     * <p>A run loaded from the database after a restart has no fix
     * records in memory, so its fixes cannot be undone; null is returned.
     *
     * <p>Tier: SHOULD.
     *
     * @param anomaly the anomaly whose fix to undo
     * @return the marks before and after, or null if nothing was undone
     */
    public UndoResult undoFix(Anomaly anomaly) {
        if (anomaly == null) {
            return null;
        }

        FixRecord fixRecord = fixManager.findRecord(anomaly);
        if (fixRecord == null) {
            return null;
        }

        List<Anomaly> anomalies = new ArrayList<>();
        anomalies.add(anomaly);
        List<FixRecord> fixRecords = new ArrayList<>();
        fixRecords.add(fixRecord);
        return undoForStudent(anomalies, fixRecords);
    }

    /**
     * Returns why the automatic fix for an anomaly failed.
     *
     * @param anomaly the anomaly
     * @return the reason, or null if no fix failed for it
     */
    public String getFailureReason(Anomaly anomaly) {
        return fixManager.getFailureReason(anomaly);
    }

    /**
     * Returns the fix made for an anomaly in this session, which says
     * what it changed before and after.
     *
     * @param anomaly the anomaly
     * @return the fix record, or null if none is kept for it
     */
    public FixRecord findFixRecord(Anomaly anomaly) {
        if (anomaly == null) {
            return null;
        }
        return fixManager.findRecord(anomaly);
    }

    /**
     * Approves the fixes of all selected anomalies.
     *
     * @param anomalies the anomalies
     */
    public void approveAll(List<Anomaly> anomalies) {
        if (anomalies == null) {
            return;
        }

        for (Anomaly anomaly : anomalies) {
            approveFix(anomaly);
        }
    }

    /**
     * Undoes the fixes of all selected anomalies. Each student is graded
     * again once, without all of their selected fixes, rather than once
     * per fix. A fix that cannot be undone does not stop the others.
     *
     * @param anomalies the anomalies
     * @return one result for each student whose fixes were undone, in
     *         the order the students first appear
     */
    public List<UndoResult> undoAll(List<Anomaly> anomalies) {
        List<UndoResult> results = new ArrayList<>();
        if (anomalies == null) {
            return results;
        }

        // The selected anomalies with a fix record, grouped by run and
        // student, since one username can be in two runs of a session
        List<String> groupKeys = new ArrayList<>();
        List<List<Anomaly>> anomalyGroups = new ArrayList<>();
        List<List<FixRecord>> recordGroups = new ArrayList<>();
        for (Anomaly anomaly : anomalies) {
            FixRecord fixRecord = null;
            if (anomaly != null) {
                fixRecord = fixManager.findRecord(anomaly);
            }
            if (fixRecord != null) {
                String groupKey = makeGroupKey(fixRecord);
                int place = groupKeys.indexOf(groupKey);
                if (place < 0) {
                    groupKeys.add(groupKey);
                    anomalyGroups.add(new ArrayList<Anomaly>());
                    recordGroups.add(new ArrayList<FixRecord>());
                    place = groupKeys.size() - 1;
                }
                anomalyGroups.get(place).add(anomaly);
                recordGroups.get(place).add(fixRecord);
            }
        }

        for (int index = 0; index < groupKeys.size(); index++) {
            UndoResult result = undoForStudent(anomalyGroups.get(index),
                    recordGroups.get(index));
            if (result != null) {
                results.add(result);
            }
        }
        return results;
    }

    // Names the run and student of a fix, for grouping in undoAll.
    private String makeGroupKey(FixRecord fixRecord) {
        String runId = "";
        GradingRun gradingRun = findRunOf(fixRecord);
        if (gradingRun != null) {
            runId = gradingRun.getId();
        }
        return runId + GROUP_SEPARATOR
                + fixRecord.getAnomaly().getUsername().toLowerCase(
                        Locale.ROOT);
    }

    // Undoes some fixes of one student, then grades the student again
    // once. anomalies and fixRecords match place for place. Returns null
    // if no fix could be undone or rejected, or grading is running.
    private UndoResult undoForStudent(List<Anomaly> anomalies,
            List<FixRecord> fixRecords) {
        // Grading again would clash with the run being graded
        if (gradingService != null && gradingService.isGrading()) {
            return null;
        }

        String username = anomalies.get(0).getUsername();
        GradingRun gradingRun = findRunOf(fixRecords.get(0));
        Submission submission = findSubmission(gradingRun, username);

        // Put the working copy back. A fix whose files cannot be put back
        // can still be skipped by grading again from the upload.
        List<Boolean> putBack = new ArrayList<>();
        for (FixRecord fixRecord : fixRecords) {
            putBack.add(putBack(fixRecord));
        }

        if (submission == null) {
            // Not graded in this session, so only the files change
            finishWithoutGrading(anomalies, fixRecords, putBack);
            if (!putBack.contains(Boolean.TRUE)) {
                return null;
            }
            return new UndoResult(username, 0.0, 0.0, "the student's run "
                    + "is not open in this session.");
        }

        List<String> addedKeys = new ArrayList<>();
        for (FixRecord fixRecord : fixRecords) {
            String fixKey = fixRecord.getFixKey();
            if (!submission.getRejectedFixes().contains(fixKey)) {
                submission.rejectFix(fixKey);
                addedKeys.add(fixKey);
            }
        }

        double totalBefore = submission.getTotalScore();
        String problem = "";
        try {
            gradingService.regradeSubmission(gradingRun, submission,
                    new QuietProgressListener());
        } catch (GradingException exception) {
            problem = exception.getMessage();
        }

        if (!problem.isEmpty() && !putBack.contains(Boolean.TRUE)) {
            // Nothing changed at all, so the fixes stay as they were
            submission.getRejectedFixes().removeAll(addedKeys);
            return null;
        }

        UndoResult result = new UndoResult(username, totalBefore,
                submission.getTotalScore(), problem);
        for (int index = 0; index < anomalies.size(); index++) {
            Anomaly anomaly = anomalies.get(index);
            FixStatus before = anomaly.getFixStatus();
            anomaly.setFixStatus(FixStatus.UNDONE);
            fixRecords.get(index).getAnomaly().setFixStatus(
                    FixStatus.UNDONE);
            // A regraded run was saved whole, with new problem cards
            if (!result.isRegraded()) {
                saveStatus(anomaly);
            }
            if (before != FixStatus.UNDONE) {
                record(FIX_UNDONE, anomaly, result.getMessage());
            }
        }
        return result;
    }

    // Puts the working copy back as it was before one fix. Returns false
    // if that cannot be done, for example when a file it needs is gone.
    private boolean putBack(FixRecord fixRecord) {
        try {
            fixManager.undo(fixRecord);
            return true;
        } catch (FixException exception) {
            return false;
        }
    }

    // Records the undos of a student who cannot be graded again: only a
    // fix whose files were put back shows as undone.
    private void finishWithoutGrading(List<Anomaly> anomalies,
            List<FixRecord> fixRecords, List<Boolean> putBack) {
        for (int index = 0; index < anomalies.size(); index++) {
            if (putBack.get(index)) {
                Anomaly anomaly = anomalies.get(index);
                FixStatus before = anomaly.getFixStatus();
                copyStatus(fixRecords.get(index), anomaly);
                saveStatus(anomaly);
                if (before != FixStatus.UNDONE
                        && anomaly.getFixStatus() == FixStatus.UNDONE) {
                    record(FIX_UNDONE, anomaly, "");
                }
            }
        }
    }

    // Finds the newest run of this session that holds the fixed problem.
    // A problem a later fix covered may have left the student's list, so
    // the newest run with that student is used then.
    private GradingRun findRunOf(FixRecord fixRecord) {
        if (gradingService == null) {
            return null;
        }

        List<GradingRun> sessionRuns = gradingService.getSessionRuns();
        String anomalyId = fixRecord.getAnomaly().getId();
        String username = fixRecord.getAnomaly().getUsername();
        GradingRun withStudent = null;
        for (int index = sessionRuns.size() - 1; index >= 0; index--) {
            GradingRun gradingRun = sessionRuns.get(index);
            Submission submission = findSubmission(gradingRun, username);
            if (submission != null && hasAnomaly(submission, anomalyId)) {
                return gradingRun;
            }
            if (submission != null && withStudent == null) {
                withStudent = gradingRun;
            }
        }
        return withStudent;
    }

    // Finds the loaded submission of a student in a run, ignoring case.
    // A copy whose upload was refused is used only if no other copy was
    // loaded.
    private Submission findSubmission(GradingRun gradingRun,
            String username) {
        if (gradingRun == null) {
            return null;
        }

        Submission found = null;
        for (Submission submission : gradingRun.getSubmissions()) {
            if (submission.getUsername().equalsIgnoreCase(username)
                    && (found == null || found.getIngestFailure() != null)) {
                found = submission;
            }
        }
        return found;
    }

    // Says whether a submission holds the problem with this id.
    private boolean hasAnomaly(Submission submission, String anomalyId) {
        for (Anomaly anomaly : submission.getAnomalies()) {
            if (anomaly.getId().equals(anomalyId)) {
                return true;
            }
        }
        return false;
    }

    // FixManager changes the anomaly held in its record. The screen may
    // hold another object with the same id (for example one loaded from
    // the database), so the new status is copied onto that one too.
    private void copyStatus(FixRecord fixRecord, Anomaly anomaly) {
        anomaly.setFixStatus(fixRecord.getAnomaly().getFixStatus());
    }

    // Writes a decision on a fix to the audit log, naming the student and
    // the problem that was fixed, then the outcome if there is one.
    private void record(String action, Anomaly anomaly, String outcome) {
        if (auditService == null) {
            return;
        }
        String detail = anomaly.getUsername() + ": " + anomaly.getMessage();
        if (!outcome.isEmpty()) {
            detail = detail + " " + outcome;
        }
        auditService.record(action, detail);
    }

    // Saves the new status when the database is open. A failure keeps the
    // status in memory, so the decision still shows on screen.
    private void saveStatus(Anomaly anomaly) {
        if (repository == null) {
            return;
        }

        try {
            repository.updateFixStatus(anomaly.getId(),
                    anomaly.getFixStatus());
        } catch (PersistenceException exception) {
            // The database is switched off or cannot be written
        }
    }
}
