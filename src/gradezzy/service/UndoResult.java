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

/**
 * What happened when the instructor undid a fix.
 *
 * <p>Tier: SHOULD. Owner: Jing Yuan.
 */
public class UndoResult extends RegradeResult {

    /**
     * Creates the result of an undo.
     *
     * <p>Tier: SHOULD.
     *
     * @param username the student whose fix was undone
     * @param totalBefore the total before the fix was undone
     * @param totalAfter the total after grading again
     * @param problem why the student could not be graded again, or an
     *        empty string when they were
     */
    public UndoResult(String username, double totalBefore, double totalAfter,
            String problem) {
        super(username, totalBefore, totalAfter, problem);
    }

    /**
     * Returns the sentence for the card, for example "Fix undone. Marks
     * 20 to 16." When the student could not be graded again, it says why
     * and that the marks have not changed yet.
     *
     * <p>Tier: SHOULD.
     *
     * @return the sentence for the card
     */
    @Override
    public String getMessage() {
        if (!isRegraded()) {
            return "Fix undone, but " + getUsername() + " could not be "
                    + "graded again, so the marks still include it: "
                    + getProblem();
        }
        return "Fix undone. " + describeMarks();
    }
}
