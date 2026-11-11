/*
 * File: FixedRemarks.java
 *
 * Purpose: Remarks for the score sheet that are given in advance, the
 * same for every run.
 *
 * Where it fits: Used by IngestRegressionTest and ReportTest to export a
 * score sheet with known remarks, without a review backend.
 */
import java.util.Map;

import gradezzy.model.GradingRun;
import gradezzy.report.ScoreSheetRemarks;

/**
 * Gives the same remarks for any run.
 */
public class FixedRemarks implements ScoreSheetRemarks {

    // The remark of each student, by username
    private final Map<String, String> remarks;

    /**
     * Creates remarks that are always the ones given.
     *
     * @param remarks the remark of each student, by username
     */
    public FixedRemarks(Map<String, String> remarks) {
        this.remarks = remarks;
    }

    /**
     * Returns the remarks given when this was made, whatever the run.
     *
     * @param gradingRun the run being exported, not used
     * @return the remarks by username
     */
    @Override
    public Map<String, String> remarksFor(GradingRun gradingRun) {
        return remarks;
    }
}
