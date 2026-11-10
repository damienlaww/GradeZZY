/*
 * File: ScoreSheetRemarks.java
 *
 * Purpose: Gives the Remarks column of the score sheet: a short note for
 * each student, such as "Possible cheating with KEVIN LIM
 * (kevin.lim.2025)".
 *
 * Where it fits: CsvScoreSheetExporter asks it for a run's remarks when
 * one is set. The window's backend sets one, as it holds the review
 * decisions the remarks come from.
 */
package gradezzy.report;

import java.util.Map;

import gradezzy.model.GradingRun;

/**
 * Gives the remark of each student of a run.
 *
 * <p>Tier: COULD.
 */
public interface ScoreSheetRemarks {

    /**
     * Returns the remark of each student of a run that has one.
     *
     * @param gradingRun the run being exported
     * @return the remarks by username in lower case; a student with no
     *         remark is left out
     */
    Map<String, String> remarksFor(GradingRun gradingRun);
}
