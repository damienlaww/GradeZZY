/*
 * File: StatisticsDistribution.java
 *
 * Purpose: Fills the score distribution panel of the Statistics screen:
 * a horizontal bar chart of the mark bands, labelled in marks such as
 * "0 to 2", with the count at the end of each bar, an accessible text
 * that reads every band, and a one-sentence summary.
 *
 * Where it fits: StatisticsController creates one and calls fill when
 * statistics are available. The bars use the primary colour from CSS,
 * never a status colour, and the chart does not animate when animations
 * are switched off.
 *
 * AI use: drafted with Claude Code, reviewed by Jun Sheng.
 */
package gradezzy.ui.view;

import java.util.ArrayList;
import java.util.List;

import javafx.geometry.Pos;
import javafx.scene.Node;
import javafx.scene.chart.BarChart;
import javafx.scene.chart.NumberAxis;
import javafx.scene.chart.XYChart;
import javafx.scene.control.Label;
import javafx.scene.layout.Region;
import javafx.scene.layout.StackPane;

import gradezzy.stats.MarkBand;
import gradezzy.stats.StatisticsSummary;
import gradezzy.ui.UiState;
import gradezzy.ui.component.Labels;

/**
 * The score distribution of the Statistics screen.
 *
 * <p>Tier: SHOULD. Owner: Damien.
 */
public final class StatisticsDistribution {

    // The gap between the end of a bar and its value
    private static final double VALUE_GAP = 8.0;

    // The most numbered ticks on the count axis
    private static final int MAX_TICKS = 6;

    // The shared state, for the animation setting
    private final UiState state;

    // The bar chart of the mark bands
    private final BarChart<Number, String> chart;

    // The axis of student counts
    private final NumberAxis countAxis;

    // The sentence above the chart
    private final Label summaryLabel;

    /**
     * Creates the distribution over the nodes it fills.
     *
     * @param state the shared state, for the animation setting
     * @param chart the bar chart of the mark bands
     * @param countAxis the axis of student counts
     * @param summaryLabel the sentence above the chart
     * @param noteLabel the note under the chart
     */
    public StatisticsDistribution(UiState state,
            BarChart<Number, String> chart, NumberAxis countAxis,
            Label summaryLabel, Label noteLabel) {
        this.state = state;
        this.chart = chart;
        this.countAxis = countAxis;
        this.summaryLabel = summaryLabel;
        noteLabel.setText("Each range counts the graded students whose "
                + "total is at least its lower end and below the next "
                + "range; the top range also holds full marks.");
    }

    /**
     * Draws the score distribution and adds it to the summary text.
     *
     * @param summary the statistics
     * @param max the most marks of the assessment
     * @param text the plain-text summary, added to
     */
    public void fill(StatisticsSummary summary, double max,
            StringBuilder text) {
        XYChart.Series<Number, String> series = new XYChart.Series<>();
        series.setName("Graded students");
        StringBuilder spoken = new StringBuilder("Score distribution of the "
                + "graded students. ");
        text.append("Score distribution, graded students per range of "
                + "total marks:\n");
        int largest = 0;
        int counted = 0;
        for (MarkBand band : summary.getMarkBands()) {
            String name = bandName(band, max);
            int count = band.getStudentCount();
            series.getData().add(new XYChart.Data<>(count, name));
            largest = Math.max(largest, count);
            counted += count;
            spoken.append(name).append(" marks: ").append(count)
                    .append(". ");
            text.append("  ").append(name).append(": ").append(count)
                    .append('\n');
        }

        int step = Math.max(1, (int) Math.ceil(largest
                / (double) MAX_TICKS));
        countAxis.setLowerBound(0.0);
        countAxis.setUpperBound((largest / step + 1) * step);
        countAxis.setTickUnit(step);
        chart.setAnimated(state.animationsEnabledProperty().get());
        chart.getData().clear();
        chart.getData().add(series);
        for (XYChart.Data<Number, String> data : series.getData()) {
            labelBar(data);
        }
        chart.setAccessibleText(spoken.toString());

        String sentence = sentence(summary, max, largest, counted);
        summaryLabel.setText(sentence);
        text.append(sentence).append('\n');
    }

    // Writes one sentence about where most students scored.
    private String sentence(StatisticsSummary summary, double max,
            int largest, int counted) {
        if (largest == 0) {
            return "No graded totals fall in any range yet.";
        }
        List<String> top = new ArrayList<>();
        for (MarkBand band : summary.getMarkBands()) {
            if (band.getStudentCount() == largest) {
                top.add(bandName(band, max));
            }
        }
        if (top.size() == 1) {
            return "The largest group, " + largest + " of " + counted
                    + " graded students, scored " + top.get(0) + " marks.";
        }
        return "The most common ranges are " + StatisticsBars.joinWords(top)
                + " marks, with " + largest + " of " + counted
                + " graded students each.";
    }

    // Puts the count just past the end of a bar, in the text colour. The
    // chart itself gives each bar its accessible text.
    private void labelBar(XYChart.Data<Number, String> data) {
        Node node = data.getNode();
        if (!(node instanceof StackPane)) {
            return;
        }
        Label value = new Label(String.valueOf(data.getXValue().intValue()));
        value.setMinWidth(Region.USE_PREF_SIZE);
        value.setMouseTransparent(true);
        StackPane.setAlignment(value, Pos.CENTER_RIGHT);
        value.translateXProperty().bind(value.widthProperty().add(
                VALUE_GAP));
        ((StackPane) node).getChildren().add(value);
    }

    // Writes a mark band in marks, such as "0 to 2".
    private static String bandName(MarkBand band, double max) {
        return Labels.formatScore(StatisticsTiles.toMarks(
                band.getLowerPercent(), max)) + " to "
                + Labels.formatScore(StatisticsTiles.toMarks(
                band.getUpperPercent(), max));
    }
}
