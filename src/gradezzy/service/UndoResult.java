/*
 * File: UndoResult.java
 *
 * Purpose: What happened when the instructor undid a fix: the student's
 * marks before and after they were graded again without it.
 *
 * Where it fits: Returned by AnomalyService.undoFix, so the Review card
 * can say "Fix undone. Marks 20 to 16".
 */
package gradezzy.service;

import gradezzy.report.ScoreFormatter;

/**
 * What happened when the instructor undid a fix.
 *
 * <p>Tier: SHOULD. Owner: Jing Yuan.
 */
public class UndoResult {

    // The student whose fix was undone
    private final String username;

    // The student's total before the fix was undone
    private final double totalBefore;

    // The student's total after grading again, or the same as before if
    // the student was not graded again
    private final double totalAfter;

    // Why the student could not be graded again, or an empty string
    private final String problem;

    /**
     * Creates the result of an undo.
     *
     * @param username the student whose fix was undone
     * @param totalBefore the total before the fix was undone
     * @param totalAfter the total after grading again
     * @param problem why the student could not be graded again, or an
     *        empty string when they were
     */
    public UndoResult(String username, double totalBefore, double totalAfter,
            String problem) {
        this.username = username;
        this.totalBefore = totalBefore;
        this.totalAfter = totalAfter;
        this.problem = problem;
    }

    /**
     * Returns the student whose fix was undone.
     *
     * @return the username
     */
    public String getUsername() {
        return username;
    }

    /**
     * Returns the student's total before the fix was undone.
     *
     * @return the total before
     */
    public double getTotalBefore() {
        return totalBefore;
    }

    /**
     * Returns the student's total after grading again without the fix.
     *
     * @return the total after
     */
    public double getTotalAfter() {
        return totalAfter;
    }

    /**
     * Says whether the student was graded again without the fix.
     *
     * @return true if the marks are now those of the files as submitted
     */
    public boolean isRegraded() {
        return problem.isEmpty();
    }

    /**
     * Returns why the student could not be graded again.
     *
     * @return the reason, or an empty string when they were
     */
    public String getProblem() {
        return problem;
    }

    /**
     * Returns the sentence for the card, for example "Fix undone. Marks
     * 20 to 16." When the student could not be graded again, it says why
     * and that the marks have not changed yet.
     *
     * @return the sentence for the card
     */
    public String getMessage() {
        if (!isRegraded()) {
            return "Fix undone, but " + username + " could not be graded "
                    + "again, so the marks still include it: " + problem;
        }
        return "Fix undone. Marks " + ScoreFormatter.format(totalBefore)
                + " to " + ScoreFormatter.format(totalAfter) + ".";
    }
}
