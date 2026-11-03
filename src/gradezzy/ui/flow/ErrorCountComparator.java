/*
 * File: ErrorCountComparator.java
 *
 * Purpose: Orders compile error reasons with the most common first, and
 * reasons with the same count in the order of their words.
 *
 * Where it fits: SectionView sorts the reasons of the "Common compile
 * errors" card with it before showing the first few.
 */
package gradezzy.ui.flow;

import java.util.Comparator;
import java.util.Map;

/**
 * Compares two compile error reasons by how often they happened.
 */
public class ErrorCountComparator implements Comparator<String> {

    // How many times each reason happened
    private final Map<String, Integer> counts;

    /**
     * Creates a comparator for some counted reasons.
     *
     * @param counts how many times each reason happened
     */
    public ErrorCountComparator(Map<String, Integer> counts) {
        this.counts = counts;
    }

    /**
     * Puts the reason with the higher count first; on a tie, the reason
     * that comes first in alphabetical order.
     *
     * @param first one reason
     * @param second the other reason
     * @return a negative number when first goes first, a positive number
     *         when second goes first
     */
    @Override
    public int compare(String first, String second) {
        int byCount = counts.get(second) - counts.get(first);
        if (byCount != 0) {
            return byCount;
        }
        return first.compareTo(second);
    }
}
