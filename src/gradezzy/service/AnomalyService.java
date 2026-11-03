/*
 * File: AnomalyService.java
 *
 * Purpose: What the Review step can do with the problems found: list
 * them, and approve or undo fixes one at a time or in bulk.
 *
 * Where it fits: Sits between the UI and FixManager so the screens hold no
 * logic.
 */
package gradezzy.service;

import java.util.ArrayList;
import java.util.List;

import gradezzy.fix.FixException;
import gradezzy.fix.FixManager;
import gradezzy.fix.FixRecord;
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

    // The helper that applies and undoes fixes
    private final FixManager fixManager;

    // The store results are saved to
    private final ResultRepository repository;

    // The log of instructor actions, or null to log nothing
    private final AuditService auditService;

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
        this.fixManager = fixManager;
        this.repository = repository;
        this.auditService = auditService;
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
            record(FIX_APPROVED, anomaly);
        }
    }

    /**
     * Undoes the fix made for an anomaly, putting the working copy back
     * as it was. If the fix cannot be undone, for example because the
     * run was loaded after a restart and the backup of the working copy
     * is no longer known, the status stays as it was, so the caller can
     * tell that nothing changed.
     *
     * @param anomaly the anomaly
     */
    public void undoFix(Anomaly anomaly) {
        if (anomaly == null) {
            return;
        }

        FixStatus before = anomaly.getFixStatus();
        FixRecord fixRecord = fixManager.findRecord(anomaly);
        if (fixRecord == null) {
            return;
        }

        try {
            fixManager.undo(fixRecord);
        } catch (FixException exception) {
            // The status stays APPLIED, which the caller reports
            return;
        }

        copyStatus(fixRecord, anomaly);
        saveStatus(anomaly);
        if (before != FixStatus.UNDONE
                && anomaly.getFixStatus() == FixStatus.UNDONE) {
            record(FIX_UNDONE, anomaly);
        }
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
     * Undoes the fixes of all selected anomalies. A fix that cannot be
     * undone does not stop the others.
     *
     * @param anomalies the anomalies
     */
    public void undoAll(List<Anomaly> anomalies) {
        if (anomalies == null) {
            return;
        }

        for (Anomaly anomaly : anomalies) {
            undoFix(anomaly);
        }
    }

    // FixManager changes the anomaly held in its record. The screen may
    // hold another object with the same id (for example one loaded from
    // the database), so the new status is copied onto that one too.
    private void copyStatus(FixRecord fixRecord, Anomaly anomaly) {
        anomaly.setFixStatus(fixRecord.getAnomaly().getFixStatus());
    }

    // Writes a decision on a fix to the audit log, naming the student and
    // the problem that was fixed.
    private void record(String action, Anomaly anomaly) {
        if (auditService == null) {
            return;
        }
        auditService.record(action, anomaly.getUsername() + ": "
                + anomaly.getMessage());
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
