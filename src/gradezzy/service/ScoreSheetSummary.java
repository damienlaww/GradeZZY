/*
 * File: ScoreSheetSummary.java
 *
 * Purpose: Which rows of a run's score sheet the CSV export fills, and
 * why the others stay blank.
 *
 * Where it fits: Returned by ExportService.summarizeScoreSheet, so the
 * Export step can say "16 marks filled, 2 left blank: 1 could not be
 * graded, 1 no submission" before and after exporting.
 */
package gradezzy.service;

import java.util.ArrayList;
import java.util.List;

/**
 * Which rows of a run's score sheet the CSV export fills, and why the
 * others stay blank.
 *
 * <p>Tier: SHOULD. Owner: Jing Yuan.
 */
public class ScoreSheetSummary {

    // Students on the sheet whose mark is filled in
    private final List<String> filled = new ArrayList<>();

    // Students on the sheet with a submission that has no mark: it
    // failed, was cancelled, is still waiting or could not be opened
    private final List<String> notGraded = new ArrayList<>();

    // Students on the sheet with no submission in the run
    private final List<String> noSubmission = new ArrayList<>();

    // Submissions in the run whose username is not on the sheet, so
    // their marks are in no row
    private final List<String> notOnSheet = new ArrayList<>();

    /**
     * Creates an empty summary. ExportService fills it.
     */
    public ScoreSheetSummary() {
        // filled in by ExportService
    }

    /**
     * Returns the usernames whose mark is filled in, in sheet order.
     *
     * @return the filled rows
     */
    public List<String> getFilled() {
        return filled;
    }

    /**
     * Returns the usernames on the sheet with a submission that has no
     * mark, because it failed, was cancelled, is still waiting or could
     * not be opened.
     *
     * @return the rows left blank because grading did not finish
     */
    public List<String> getNotGraded() {
        return notGraded;
    }

    /**
     * Returns the usernames on the sheet with no submission in the run.
     *
     * @return the rows left blank because nothing was submitted
     */
    public List<String> getNoSubmission() {
        return noSubmission;
    }

    /**
     * Returns the usernames of submissions that match no row of the
     * sheet, so their marks are not exported.
     *
     * @return the submissions that are not on the sheet
     */
    public List<String> getNotOnSheet() {
        return notOnSheet;
    }

    /**
     * Returns the number of rows left blank, for either reason.
     *
     * @return the number of blank rows
     */
    public int getBlankCount() {
        return notGraded.size() + noSubmission.size();
    }

    /**
     * Returns the line for the Export step, for example "16 marks
     * filled, 2 left blank: 1 could not be graded, 1 no submission."
     *
     * @return the summary line
     */
    public String getMessage() {
        String message = filled.size() + " marks filled";
        if (filled.size() == 1) {
            message = "1 mark filled";
        }
        if (getBlankCount() == 0) {
            return message + ".";
        }

        message = message + ", " + getBlankCount() + " left blank: ";
        String reasons = "";
        if (!notGraded.isEmpty()) {
            reasons = notGraded.size() + " could not be graded";
        }
        if (!noSubmission.isEmpty()) {
            if (!reasons.isEmpty()) {
                reasons = reasons + ", ";
            }
            reasons = reasons + noSubmission.size() + " no submission";
        }
        return message + reasons + ".";
    }
}
