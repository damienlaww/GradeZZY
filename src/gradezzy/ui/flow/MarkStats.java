/*
 * File: MarkStats.java
 *
 * Purpose: Works out the figures the charts show from a list of total
 * marks: average, median, spread (standard deviation), lowest, highest,
 * how many passed, and how many students got each mark.
 *
 * Where it fits: The bell curve on Grade, Export and Statistics, and the
 * figures beside it. It only counts; it never changes a mark.
 */
package gradezzy.ui.flow;

import java.util.ArrayList;
import java.util.Collections;
import java.util.List;

import gradezzy.model.Submission;

/**
 * The figures of one set of total marks.
 */
public class MarkStats {

    /** The usual share of the maximum mark that counts as a pass. */
    public static final double DEFAULT_PASS_PERCENT = 50.0;

    // Turns a percentage into a fraction
    private static final double PERCENT = 100.0;

    // The totals, sorted from lowest to highest
    private final List<Double> totals;

    // The highest possible total
    private final double maxTotal;

    // The total that counts as a pass
    private final double passMark;

    /**
     * Works out the figures of some totals.
     *
     * @param totals the total of each student
     * @param maxTotal the highest possible total, such as 20
     * @param passPercent the share of maxTotal that passes, such as 50
     */
    public MarkStats(List<Double> totals, double maxTotal,
            double passPercent) {
        this.totals = new ArrayList<>(totals);
        Collections.sort(this.totals);
        this.maxTotal = maxTotal;
        this.passMark = maxTotal * passPercent / PERCENT;
    }

    /**
     * Works out the figures of the graded students of a list.
     * Students still waiting are left out.
     *
     * @param submissions the students
     * @param maxTotal the highest possible total
     * @param passPercent the share of maxTotal that passes
     * @return the figures
     */
    public static MarkStats of(List<Submission> submissions,
            double maxTotal, double passPercent) {
        List<Double> totals = new ArrayList<>();
        for (Submission submission : submissions) {
            if (!submission.getQuestionResults().isEmpty()) {
                totals.add(submission.getTotalScore());
            }
        }
        return new MarkStats(totals, maxTotal, passPercent);
    }

    /**
     * Reads the pass percentage from a setting value, or returns the
     * usual 50 when the value is missing or not a number.
     *
     * @param value the text of stats.pass.mark.percent
     * @return the percentage
     */
    public static double parsePassPercent(String value) {
        if (value == null || value.isBlank()) {
            return DEFAULT_PASS_PERCENT;
        }
        try {
            return Double.parseDouble(value.trim());
        } catch (NumberFormatException exception) {
            return DEFAULT_PASS_PERCENT;
        }
    }

    /**
     * Returns how many totals there are.
     *
     * @return the count
     */
    public int getCount() {
        return totals.size();
    }

    /**
     * Says whether there is no total at all.
     *
     * @return true when empty
     */
    public boolean isEmpty() {
        return totals.isEmpty();
    }

    /**
     * Returns the sorted totals.
     *
     * @return a copy of the totals, lowest first
     */
    public List<Double> getTotals() {
        return new ArrayList<>(totals);
    }

    /**
     * Returns the highest possible total.
     *
     * @return the maximum
     */
    public double getMaxTotal() {
        return maxTotal;
    }

    /**
     * Returns the total that counts as a pass.
     *
     * @return the pass mark
     */
    public double getPassMark() {
        return passMark;
    }

    /**
     * Returns the average total.
     *
     * @return the mean, or 0 when empty
     */
    public double getMean() {
        if (totals.isEmpty()) {
            return 0;
        }
        double sum = 0;
        for (double total : totals) {
            sum += total;
        }
        return sum / totals.size();
    }

    /**
     * Returns the middle total.
     *
     * @return the median, or 0 when empty
     */
    public double getMedian() {
        int count = totals.size();
        if (count == 0) {
            return 0;
        }
        int middle = count / 2;
        if (count % 2 == 1) {
            return totals.get(middle);
        }
        return (totals.get(middle - 1) + totals.get(middle)) / 2;
    }

    /**
     * Returns the spread of the totals around the average (the
     * population standard deviation).
     *
     * @return the standard deviation, or 0 when empty
     */
    public double getSpread() {
        if (totals.isEmpty()) {
            return 0;
        }
        double mean = getMean();
        double sum = 0;
        for (double total : totals) {
            sum += (total - mean) * (total - mean);
        }
        return Math.sqrt(sum / totals.size());
    }

    /**
     * Returns the lowest total.
     *
     * @return the lowest, or 0 when empty
     */
    public double getLowest() {
        if (totals.isEmpty()) {
            return 0;
        }
        return totals.get(0);
    }

    /**
     * Returns the highest total.
     *
     * @return the highest, or 0 when empty
     */
    public double getHighest() {
        if (totals.isEmpty()) {
            return 0;
        }
        return totals.get(totals.size() - 1);
    }

    /**
     * Returns how many totals reach the pass mark.
     *
     * @return the count
     */
    public int getPassCount() {
        int count = 0;
        for (double total : totals) {
            if (total >= passMark) {
                count++;
            }
        }
        return count;
    }

    /**
     * Returns the share of totals that reach the pass mark.
     *
     * @return a fraction from 0 to 1, or 0 when empty
     */
    public double getPassRate() {
        if (totals.isEmpty()) {
            return 0;
        }
        return (double) getPassCount() / totals.size();
    }

    /**
     * Counts the totals in bins of equal width from 0 to the maximum.
     * A total on a bin's upper edge falls in the next bin, except the
     * maximum, which falls in the last.
     *
     * @param binCount how many bins
     * @return the count of each bin, lowest first
     */
    public int[] countBins(int binCount) {
        int[] counts = new int[binCount];
        if (binCount == 0 || maxTotal <= 0) {
            return counts;
        }
        double width = maxTotal / binCount;
        for (double total : totals) {
            int bin = (int) Math.floor(total / width);
            bin = Math.max(0, Math.min(binCount - 1, bin));
            counts[bin]++;
        }
        return counts;
    }
}
