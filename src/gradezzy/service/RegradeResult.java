/*
 * File: RegradeResult.java
 *
 * Purpose: What happened when a fix was undone or applied again: the
 * student's marks before and after they were graded again.
 *
 * Where it fits: The shared part of UndoResult and ReapplyResult, which
 * AnomalyService returns. Each of them words the Review card's sentence
 * its own way.
 */
package gradezzy.service;

import gradezzy.report.ScoreFormatter;

/**
 * The marks before and after a student was graded again because a fix
 * was undone or applied again.
 *
 * <p>Tier: SHOULD. Owner: Jing Yuan.
 */
public abstract class RegradeResult {

    // The student whose fix changed
    private final String username;

    // The student's total before the fix changed
    private final double totalBefore;

    // The student's total after grading again, or the same as before if
    // the student was not graded again
    private final double totalAfter;

    // Why the student was not graded again, or an empty string
    private final String problem;

    /**
     * Creates the result.
     *
     * <p>Tier: SHOULD.
     *
     * @param username the student whose fix changed
     * @param totalBefore the total before the fix changed
     * @param totalAfter the total after grading again
     * @param problem why the student was not graded again, or an empty
     *        string when they were
     */
    protected RegradeResult(String username, double totalBefore,
            double totalAfter, String problem) {
        this.username = username;
        this.totalBefore = totalBefore;
        this.totalAfter = totalAfter;
        this.problem = problem;
    }

    /**
     * Returns the student whose fix changed.
     *
     * <p>Tier: SHOULD.
     *
     * @return the username
     */
    public String getUsername() {
        return username;
    }

    /**
     * Returns the student's total before the fix changed.
     *
     * <p>Tier: SHOULD.
     *
     * @return the total before
     */
    public double getTotalBefore() {
        return totalBefore;
    }

    /**
     * Returns the student's total after grading again.
     *
     * <p>Tier: SHOULD.
     *
     * @return the total after
     */
    public double getTotalAfter() {
        return totalAfter;
    }

    /**
     * Says whether the student was graded again.
     *
     * <p>Tier: SHOULD.
     *
     * @return true if the marks now follow the change to the fix
     */
    public boolean isRegraded() {
        return problem.isEmpty();
    }

    /**
     * Returns why the student was not graded again.
     *
     * <p>Tier: SHOULD.
     *
     * @return the reason, or an empty string when they were
     */
    public String getProblem() {
        return problem;
    }

    /**
     * Returns the sentence for the Review card.
     *
     * <p>Tier: SHOULD.
     *
     * @return the sentence for the card
     */
    public abstract String getMessage();

    /**
     * Returns "Marks 20 to 16.": the totals before and after.
     *
     * <p>Tier: SHOULD.
     *
     * @return the change of marks, as a sentence
     */
    protected String describeMarks() {
        return "Marks " + ScoreFormatter.format(totalBefore) + " to "
                + ScoreFormatter.format(totalAfter) + ".";
    }
}
