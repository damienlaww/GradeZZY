/*
 * File: AnomalyService.java
 *
 * Purpose: What the anomaly log screen can do.
 *
 * Where it fits: Sits between the UI and FixManager so controllers hold no
 * logic.
 */
package gradezzy.service;

import java.util.ArrayList;
import java.util.List;

import gradezzy.fix.FixManager;
import gradezzy.model.Anomaly;
import gradezzy.model.GradingRun;
import gradezzy.persistence.ResultRepository;

/**
 * What the anomaly log screen can do.
 *
 * <p>Tier: SHOULD. Owner: Jing Yuan.
 */
public class AnomalyService {

    // The helper that applies and undoes fixes
    private final FixManager fixManager;

    // The store results are saved to
    private final ResultRepository repository;

    /**
     * Creates a new anomaly service.
     *
     * @param fixManager the helper that applies and undoes fixes
     * @param repository the store results are saved to
     */
    public AnomalyService(FixManager fixManager, ResultRepository repository) {
        this.fixManager = fixManager;
        this.repository = repository;
    }

    /**
     * Collects the anomalies of every submission in a run.
     *
     * @param gradingRun the grading run
     * @return all anomalies of the run
     */
    public List<Anomaly> listAnomalies(GradingRun gradingRun) {
        // TODO(Jing Yuan): implement: gathering the anomalies of each
        //     submission
        return new ArrayList<>();
    }

    /**
     * Approves the fix made for an anomaly.
     *
     * @param anomaly the anomaly
     */
    public void approveFix(Anomaly anomaly) {
        // TODO(Jing Yuan): implement: finding the FixRecord, approving it and
        //     saving the new status
    }

    /**
     * Undoes the fix made for an anomaly.
     *
     * @param anomaly the anomaly
     */
    public void undoFix(Anomaly anomaly) {
        // TODO(Jing Yuan): implement: finding the FixRecord, undoing it and
        //     saving the new status
    }

    /**
     * Approves the fixes of all selected anomalies.
     *
     * @param anomalies the anomalies
     */
    public void approveAll(List<Anomaly> anomalies) {
        // TODO(Jing Yuan): implement: calling approveFix for each anomaly
    }

    /**
     * Undoes the fixes of all selected anomalies.
     *
     * @param anomalies the anomalies
     */
    public void undoAll(List<Anomaly> anomalies) {
        // TODO(Jing Yuan): implement: calling undoFix for each anomaly
    }
}
