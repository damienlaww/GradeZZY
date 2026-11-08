/*
 * File: ReapplyResult.java
 *
 * Purpose: What happened when the instructor applied a rejected fix
 * again: the student's marks before and after they were graded again
 * with it.
 *
 * Where it fits: Returned by AnomalyService.reapplyFix, so the Review card
 * can say "Fix applied again. Marks 16 to 20".
 */
package gradezzy.service;

import gradezzy.report.ScoreFormatter;

/**
 * What happened when the instructor applied a rejected fix again.
 *
 * <p>Tier: SHOULD. Owner: Jing Yuan.
 */
public class ReapplyResult {

    // The student whose fix was applied again
    private final String username;

    // The student's total before the fix was applied again
    private final double totalBefore;

    // The student's total after grading again, or the same as before if
    // the fix was not applied again
    private final double totalAfter;

    // Why the fix was not applied again, or an empty string
    private final String problem;

    /**
     * Creates the result of applying a fix again.
     *
     * @param username the student whose fix was applied again
     * @param totalBefore the total before the fix was applied again
     * @param totalAfter the total after grading again
     * @param problem why the fix was not applied again, or an empty string
     *        when it was
     */
    public ReapplyResult(String username, double totalBefore,
            double totalAfter, String problem) {
        this.username = username;
        this.totalBefore = totalBefore;
        this.totalAfter = totalAfter;
        this.problem = problem;
    }

    /**
     * Returns the student whose fix was applied again.
     *
     * @return the username
     */
    public String getUsername() {
        return username;
    }

    /**
     * Returns the student's total before the fix was applied again.
     *
     * @return the total before
     */
    public double getTotalBefore() {
        return totalBefore;
    }

    /**
     * Returns the student's total after grading again with the fix.
     *
     * @return the total after
     */
    public double getTotalAfter() {
        return totalAfter;
    }

    /**
     * Says whether the student was graded again with the fix applied.
     *
     * @return true if the marks now include the fix
     */
    public boolean isRegraded() {
        return problem.isEmpty();
    }

    /**
     * Returns why the fix was not applied again.
     *
     * @return the reason, or an empty string when it was
     */
    public String getProblem() {
        return problem;
    }

    /**
     * Returns the sentence for the card, for example "Fix applied again.
     * Marks 16 to 20." When the fix could not be applied again, it says
     * why and that the marks have not changed.
     *
     * @return the sentence for the card
     */
    public String getMessage() {
        if (!isRegraded()) {
            return "The fix was not applied again, so the marks of "
                    + username + " are unchanged: " + problem;
        }
        return "Fix applied again. Marks " + ScoreFormatter.format(totalBefore)
                + " to " + ScoreFormatter.format(totalAfter) + ".";
    }
}
