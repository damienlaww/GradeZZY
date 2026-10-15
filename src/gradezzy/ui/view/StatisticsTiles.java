/*
 * File: StatisticsTiles.java
 *
 * Purpose: Fills the four key numbers of the Statistics screen:
 * completion, mean, median and pass rate, each with a caption.
 *
 * Where it fits: StatisticsController creates one and calls fill when
 * statistics are available. Ungraded students are left out of every
 * figure; the completion tile says how many. The pass rate caption names
 * the pass mark from the stats.pass.mark.percent setting.
 *
 * AI use: drafted with Claude Code, reviewed by Jun Sheng.
 */
package gradezzy.ui.view;

import java.util.Locale;

import gradezzy.config.ConfigKeys;
import gradezzy.model.GradingRun;
import gradezzy.stats.StatisticsSummary;
import gradezzy.ui.backend.DashboardBackend;
import gradezzy.ui.component.Labels;
import gradezzy.ui.component.MetricTile;

/**
 * The key numbers of the Statistics screen.
 *
 * <p>Tier: SHOULD. Owner: Damien.
 */
public final class StatisticsTiles {

    // One hundred percent
    private static final double FULL_PERCENT = 100.0;

    // Marks are rounded to two decimal places in captions
    private static final double HUNDREDTHS = 100.0;

    // How a mean or median is written
    private static final String ONE_DECIMAL = "%.1f";

    // The backend, for the pass mark setting
    private final DashboardBackend backend;

    // Graded students out of all students
    private final MetricTile completionTile;

    // The mean total
    private final MetricTile meanTile;

    // The median total
    private final MetricTile medianTile;

    // The share of graded students who passed
    private final MetricTile passTile;

    /**
     * Creates the tiles over the nodes they fill.
     *
     * @param backend the backend, for the pass mark setting
     * @param completionTile graded students out of all students
     * @param meanTile the mean total
     * @param medianTile the median total
     * @param passTile the share of graded students who passed
     */
    public StatisticsTiles(DashboardBackend backend,
            MetricTile completionTile, MetricTile meanTile,
            MetricTile medianTile, MetricTile passTile) {
        this.backend = backend;
        this.completionTile = completionTile;
        this.meanTile = meanTile;
        this.medianTile = medianTile;
        this.passTile = passTile;
    }

    /**
     * Fills in the four key numbers and adds them to the summary text.
     *
     * @param summary the statistics
     * @param run the run of the section, or null
     * @param graded the number of graded students
     * @param total the number of students in the run
     * @param max the most marks of the assessment
     * @param text the plain-text summary, added to
     */
    public void fill(StatisticsSummary summary, GradingRun run, int graded,
            int total, double max, StringBuilder text) {
        String maxText = Labels.formatScore(max);
        if (run == null) {
            completionTile.setValue(Labels.NO_MARK);
            completionTile.setCaption("No run is loaded for this section");
        } else {
            int ungraded = total - graded;
            completionTile.setValue(graded + " of " + total);
            if (ungraded == 0) {
                completionTile.setCaption("Every student is graded");
            } else {
                completionTile.setCaption(ungraded + " ungraded, left out "
                        + "of every figure");
            }
        }
        meanTile.setValue(oneDecimal(summary.getMean()) + " of " + maxText);
        meanTile.setCaption("Average total of the graded students");
        medianTile.setValue(oneDecimal(summary.getMedian()) + " of "
                + maxText);
        medianTile.setCaption("Half scored this or more");
        passTile.setValue(Labels.formatPercent(summary.getPassRate()));
        passTile.setCaption(passMarkText(max));

        text.append("Completion: ").append(completionTile.getValue())
                .append(" graded\n");
        text.append("Mean: ").append(meanTile.getValue()).append('\n');
        text.append("Median: ").append(medianTile.getValue()).append('\n');
        text.append("Pass rate: ").append(passTile.getValue()).append(" (")
                .append(passTile.getCaption()).append(")\n");
    }

    // Turns a percentage of the maximum into marks, to two decimals.
    static double toMarks(double percent, double max) {
        return Math.round(percent / FULL_PERCENT * max * HUNDREDTHS)
                / HUNDREDTHS;
    }

    // Describes the pass mark, such as "Pass mark 50%, 10 of 20".
    private String passMarkText(double max) {
        String setting = backend.getSetting(ConfigKeys.PASS_MARK_PERCENT);
        double percent;
        try {
            percent = Double.parseDouble(setting.trim());
        } catch (NumberFormatException exception) {
            return "Pass mark not set in the settings";
        }
        return "Pass mark " + Labels.formatScore(percent) + "%, "
                + Labels.formatMarks(toMarks(percent, max), max);
    }

    // Writes a number with one decimal place.
    private static String oneDecimal(double value) {
        return String.format(Locale.ROOT, ONE_DECIMAL, value);
    }
}
