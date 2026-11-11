/*
 * File: SimilarityMostQuestionsComparator.java
 *
 * Purpose: Orders the pairs of students alike on more than one question
 * with the pair alike on the most questions first.
 *
 * Where it fits: SimilarityGroupsView sorts the lines of its warning
 * about students alike on several questions with it.
 */
package gradezzy.ui.flow;

import java.util.Comparator;
import java.util.List;

import gradezzy.plagiarism.SimilarityPair;

/**
 * Compares two students' lists of similar pairs, one pair per question,
 * by how many questions they hold.
 *
 * <p>Tier: COULD.
 */
public class SimilarityMostQuestionsComparator
        implements Comparator<List<SimilarityPair>> {

    /**
     * Creates a comparator that puts the longest list first.
     */
    public SimilarityMostQuestionsComparator() {
    }

    /**
     * Puts the list with more questions first.
     *
     * @param first one pair of students' list
     * @param second the other pair of students' list
     * @return a negative number when first goes first, a positive number
     *         when second goes first, or 0 for the same number of
     *         questions
     */
    @Override
    public int compare(List<SimilarityPair> first,
            List<SimilarityPair> second) {
        return second.size() - first.size();
    }
}
