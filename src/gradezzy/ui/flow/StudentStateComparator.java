/*
 * File: StudentStateComparator.java
 *
 * Purpose: Sorts students by their status, in the order the statuses
 * are listed in ResultState.
 *
 * Where it fits: ReviewScreen sorts its Status column with it when the
 * column header is clicked.
 */
package gradezzy.ui.flow;

import java.util.Comparator;

import gradezzy.model.Submission;
import gradezzy.ui.backend.DashboardBackend;

/**
 * Compares two students by their status.
 */
public class StudentStateComparator implements Comparator<Submission> {

    // The backend that knows the review decisions
    private final DashboardBackend backend;

    /**
     * Creates the comparator.
     *
     * @param backend the backend that knows the review decisions
     */
    public StudentStateComparator(DashboardBackend backend) {
        this.backend = backend;
    }

    /**
     * Puts the student whose status comes first in ResultState first.
     *
     * @param first one student
     * @param second the other student
     * @return a negative number when first goes first, a positive number
     *         when second goes first, or 0 for the same status
     */
    @Override
    public int compare(Submission first, Submission second) {
        int firstOrder = Students.state(first, backend).ordinal();
        int secondOrder = Students.state(second, backend).ordinal();
        return firstOrder - secondOrder;
    }
}
