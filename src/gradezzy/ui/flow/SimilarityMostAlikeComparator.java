/*
 * File: SimilarityMostAlikeComparator.java
 *
 * Purpose: Orders groups of similar code with the most alike group
 * first.
 *
 * Where it fits: SimilarityGroupsView sorts its groups with it when the
 * instructor chooses to sort by similarity.
 */
package gradezzy.ui.flow;

import java.util.Comparator;

/**
 * Compares two groups of similar code by their highest similarity.
 *
 * <p>Tier: COULD.
 */
public class SimilarityMostAlikeComparator
        implements Comparator<SimilarityCluster> {

    /**
     * Creates a comparator that puts the most alike group first.
     */
    public SimilarityMostAlikeComparator() {
    }

    /**
     * Puts the group whose most alike pair is more alike first.
     *
     * @param first one group
     * @param second the other group
     * @return a negative number when first goes first, a positive number
     *         when second goes first, or 0 for the same similarity
     */
    @Override
    public int compare(SimilarityCluster first, SimilarityCluster second) {
        return Double.compare(second.getHighestSimilarity(),
                first.getHighestSimilarity());
    }
}
