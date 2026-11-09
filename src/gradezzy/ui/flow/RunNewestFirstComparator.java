/*
 * File: RunNewestFirstComparator.java
 *
 * Purpose: Orders grading runs with the newest first. A run with no
 * start time goes last.
 *
 * Where it fits: ModuleRuns sorts the graded runs of a module with it,
 * so the Assessment picker of Compare sections lists the assessment
 * graded most recently first.
 */
package gradezzy.ui.flow;

import java.util.Comparator;

import gradezzy.model.GradingRun;

/**
 * Compares two grading runs by when they started.
 *
 * <p>Tier: SHOULD. Owner: Jing Yuan.
 */
public class RunNewestFirstComparator implements Comparator<GradingRun> {

    // What compare returns when the first run goes first
    private static final int FIRST_GOES_FIRST = -1;

    // What compare returns when the second run goes first
    private static final int SECOND_GOES_FIRST = 1;

    // What compare returns when the two runs are equal
    private static final int SAME = 0;

    /**
     * Creates a comparator that puts the newest run first.
     *
     * <p>Tier: SHOULD. Owner: Jing Yuan.
     */
    public RunNewestFirstComparator() {
    }

    /**
     * Puts the run that started later first, and a run with no start
     * time last.
     *
     * <p>Tier: SHOULD. Owner: Jing Yuan.
     *
     * @param first one run
     * @param second the other run
     * @return a negative number when first goes first, a positive number
     *         when second goes first, 0 when they are equal
     */
    @Override
    public int compare(GradingRun first, GradingRun second) {
        boolean firstUnknown = first.getStartedAt() == null;
        boolean secondUnknown = second.getStartedAt() == null;
        boolean bothUnknown = firstUnknown && secondUnknown;
        if (bothUnknown) {
            return SAME;
        }
        if (firstUnknown) {
            return SECOND_GOES_FIRST;
        }
        if (secondUnknown) {
            return FIRST_GOES_FIRST;
        }
        return second.getStartedAt().compareTo(first.getStartedAt());
    }
}
