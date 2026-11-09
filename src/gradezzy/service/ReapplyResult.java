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

/**
 * What happened when the instructor applied a rejected fix again.
 *
 * <p>Tier: SHOULD. Owner: Jing Yuan.
 */
public class ReapplyResult extends RegradeResult {

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
        super(username, totalBefore, totalAfter, problem);
    }

    /**
     * Returns the sentence for the card, for example "Fix applied again.
     * Marks 16 to 20." When the fix could not be applied again, it says
     * why and that the marks have not changed.
     *
     * @return the sentence for the card
     */
    @Override
    public String getMessage() {
        if (!isRegraded()) {
            return "The fix was not applied again, so the marks of "
                    + getUsername() + " are unchanged: " + getProblem();
        }
        return "Fix applied again. " + describeMarks();
    }
}
