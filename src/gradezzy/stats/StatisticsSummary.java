/*
 * File: StatisticsSummary.java
 *
 * Purpose: The numbers shown on the Statistics tab.
 *
 * Where it fits: Built by StatisticsCalculator and displayed on the
 * Statistics tab.
 */
package gradezzy.stats;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

/**
 * The numbers shown on the Statistics tab.
 *
 * <p>Tier: SHOULD. Owner: Huxley.
 */
public class StatisticsSummary {

    // The mean total score
    private double mean = 0.0;

    // The median total score
    private double median = 0.0;

    // The share of students who passed
    private double passRate = 0.0;

    // The test case failed most often
    private String mostFailedTest = "";

    // The mark bands
    private final List<MarkBand> markBands = new ArrayList<>();

    // The pass rate of each question, by question id
    private final Map<String, Double> questionPassRates = new HashMap<>();

    // How often each compile error message occurred
    private final Map<String, Integer> commonCompileErrors = new HashMap<>();

    /**
     * Creates a new statistics summary.
     */
    public StatisticsSummary() {
        // nothing to set up yet
    }

    /**
     * Returns the mean total score.
     *
     * @return the mean total score
     */
    public double getMean() {
        return mean;
    }

    /**
     * Sets the mean total score.
     *
     * @param mean the mean total score
     */
    public void setMean(double mean) {
        this.mean = mean;
    }

    /**
     * Returns the median total score.
     *
     * @return the median total score
     */
    public double getMedian() {
        return median;
    }

    /**
     * Sets the median total score.
     *
     * @param median the median total score
     */
    public void setMedian(double median) {
        this.median = median;
    }

    /**
     * Returns the share of students who passed.
     *
     * @return the share of students who passed
     */
    public double getPassRate() {
        return passRate;
    }

    /**
     * Sets the share of students who passed.
     *
     * @param passRate the share of students who passed
     */
    public void setPassRate(double passRate) {
        this.passRate = passRate;
    }

    /**
     * Returns the test case failed most often.
     *
     * @return the test case failed most often
     */
    public String getMostFailedTest() {
        return mostFailedTest;
    }

    /**
     * Sets the test case failed most often.
     *
     * @param mostFailedTest the test case failed most often
     */
    public void setMostFailedTest(String mostFailedTest) {
        this.mostFailedTest = mostFailedTest;
    }

    /**
     * Returns the mark bands.
     *
     * @return the mark bands
     */
    public List<MarkBand> getMarkBands() {
        return markBands;
    }

    /**
     * Returns the pass rate of each question, by question id.
     *
     * @return the pass rate of each question, by question id
     */
    public Map<String, Double> getQuestionPassRates() {
        return questionPassRates;
    }

    /**
     * Returns how often each compile error message occurred.
     *
     * @return how often each compile error message occurred
     */
    public Map<String, Integer> getCommonCompileErrors() {
        return commonCompileErrors;
    }
}
