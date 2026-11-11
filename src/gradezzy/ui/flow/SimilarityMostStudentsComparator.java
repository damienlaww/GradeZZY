/*
 * File: SimilarityMostStudentsComparator.java
 *
 * Purpose: Orders groups of similar code with the group of the most
 * students first.
 *
 * Where it fits: SimilarityGroupsView sorts its groups with it when the
 * instructor chooses to sort by students.
 */
package gradezzy.ui.flow;

import java.util.Comparator;

/**
 * Compares two groups of similar code by how many students they have.
 *
 * <p>Tier: COULD.
 */
public class SimilarityMostStudentsComparator
        implements Comparator<SimilarityCluster> {

    /**
     * Creates a comparator that puts the largest group first.
     */
    public SimilarityMostStudentsComparator() {
    }

    /**
     * Puts the group with more students first.
     *
     * @param first one group
     * @param second the other group
     * @return a negative number when first goes first, a positive number
     *         when second goes first, or 0 for the same number of
     *         students
     */
    @Override
    public int compare(SimilarityCluster first, SimilarityCluster second) {
        int firstSize = first.getUsernames().size();
        int secondSize = second.getUsernames().size();
        return secondSize - firstSize;
    }
}
