/*
 * File: StudentNameComparator.java
 *
 * Purpose: Sorts students by name, ignoring upper and lower case.
 *
 * Where it fits: ReviewScreen sorts its Student column with it when the
 * column header is clicked.
 */
package gradezzy.ui.flow;

import java.util.Comparator;
import java.util.Locale;

import gradezzy.model.Submission;

/**
 * Compares two students by their names in lower case.
 *
 * <p>Tier: SHOULD.
 */
public class StudentNameComparator implements Comparator<Submission> {

    /**
     * Puts the student whose name comes first in alphabetical order
     * first.
     *
     * @param first one student
     * @param second the other student
     * @return a negative number when first goes first, a positive number
     *         when second goes first, or 0 for the same name
     */
    @Override
    public int compare(Submission first, Submission second) {
        String firstName = Students.name(first).toLowerCase(Locale.ROOT);
        String secondName = Students.name(second).toLowerCase(Locale.ROOT);
        return firstName.compareTo(secondName);
    }
}
