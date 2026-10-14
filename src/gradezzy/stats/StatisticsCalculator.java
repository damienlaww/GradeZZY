/*
 * File: StatisticsCalculator.java
 *
 * Purpose: Computes the statistics of one or more grading runs.
 *
 * Where it fits: Pure calculation with no user interface, so it can be tested
 * from the console.
 */
package gradezzy.stats;

import java.util.ArrayList;
import java.util.List;

import gradezzy.config.AppConfig;
import gradezzy.model.GradingRun;

/**
 * Computes the statistics of one or more grading runs.
 *
 * <p>Tier: SHOULD. Owner: Huxley.
 */
public class StatisticsCalculator {

    // The application settings
    private final AppConfig config;

    /**
     * Creates a new statistics calculator.
     *
     * @param config the application settings
     */
    public StatisticsCalculator(AppConfig config) {
        this.config = config;
    }

    /**
     * Builds the full summary for the runs that match a filter.
     *
     * @param gradingRuns the grading runs
     * @param filter the filter
     * @return the summary; empty values when no run matches
     */
    public StatisticsSummary summarise(List<GradingRun> gradingRuns,
            StatisticsFilter filter) {
        // TODO(Huxley): implement: filtering by module and section, then
        //     filling every field of StatisticsSummary
        return null;
    }

    /**
     * Returns the mean of a list of scores.
     *
     * @param scores the scores
     * @return the mean
     */
    public double mean(List<Double> scores) {
        // TODO(Huxley): implement: the mean, returning 0 for an empty list
        return 0.0;
    }

    /**
     * Returns the median of a list of scores.
     *
     * @param scores the scores
     * @return the median
     */
    public double median(List<Double> scores) {
        // TODO(Huxley): implement: the median, returning 0 for an empty list
        return 0.0;
    }

    /**
     * Returns the share of scores at or above the pass mark.
     *
     * @param scores the scores
     * @param maxScore the max score
     * @return the pass rate, from 0 to 1
     */
    public double passRate(List<Double> scores, double maxScore) {
        // TODO(Huxley): implement: the pass rate using PASS_MARK_PERCENT
        return 0.0;
    }

    /**
     * Groups scores into bands of equal width.
     *
     * @param scores the scores
     * @param maxScore the max score
     * @return the bands, lowest first
     */
    public List<MarkBand> markBands(List<Double> scores, double maxScore) {
        // TODO(Huxley): implement: banding using MARK_BAND_WIDTH_PERCENT
        return new ArrayList<>();
    }
}
