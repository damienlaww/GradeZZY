/*
 * File: BellCurveChart.java
 *
 * Purpose: Draws how the total marks spread: one bar per mark (how many
 * students got it) with a smooth bell curve on top. The full chart also
 * shades the band where most students fall (the average plus or minus
 * one spread) and draws lines at the average and the pass mark.
 *
 * Where it fits: Statistics shows the full chart; Grade and Export show
 * the small one. It is drawn with JavaFX shapes and redraws itself when
 * its size changes.
 */
package gradezzy.ui.flow;

import javafx.scene.control.Tooltip;
import javafx.scene.layout.Region;
import javafx.scene.shape.Line;
import javafx.scene.shape.Polyline;
import javafx.scene.shape.Rectangle;
import javafx.scene.shape.SVGPath;
import javafx.scene.text.Text;
import javafx.scene.text.TextAlignment;

/**
 * A bar chart of total marks with a bell curve on top.
 */
public class BellCurveChart extends Region {

    /** The height of the full chart. */
    public static final double FULL_HEIGHT = 270.0;

    /** The height of the small chart. */
    public static final double SMALL_HEIGHT = 120.0;

    // Above this maximum, marks are grouped into bins instead of one bar
    // per mark
    private static final double MOST_SINGLE_BARS = 40.0;

    // How many bins when marks are grouped
    private static final int GROUPED_BINS = 20;

    // Space for the axis labels of the full chart: left, right, top,
    // bottom
    private static final double[] FULL_MARGINS = {40, 12, 30, 36};

    // Space for the axis labels of the small chart
    private static final double[] SMALL_MARGINS = {4, 4, 6, 18};

    // The gap between neighbouring bars, in pixels
    private static final double BAR_GAP = 2.0;

    // The radius of the rounded top of a bar
    private static final double BAR_RADIUS = 3.0;

    // How many points draw the curve
    private static final int CURVE_STEPS = 160;

    // Leaves room above the tallest bar or curve
    private static final double HEADROOM = 1.08;

    // The most gridlines on the full chart
    private static final int MOST_GRIDLINES = 5;

    // Candidate gaps between gridlines
    private static final int[] GRID_STEPS = {1, 2, 4, 5, 10, 20, 25, 50};

    // How far labels sit from what they label
    private static final double LABEL_GAP = 6.0;

    // How far the average and pass lines rise above the plot
    private static final double LINE_RISE = 6.0;

    // The number of x-axis labels on the small chart
    private static final int SMALL_TICKS = 4;

    // The usual gap between x-axis labels on the full chart, in marks
    private static final int FULL_TICK_MARKS = 2;

    // Rounds a number to one decimal place
    private static final double ONE_DECIMAL = 10.0;

    // Each bar of a whole mark spans half a mark either side
    private static final double HALF_MARK = 0.5;

    // Turns the name of the up axis on its side
    private static final double QUARTER_TURN = -90.0;

    // A normal curve's height is scaled by one over the square root of
    // two pi
    private static final double ROOT_TWO_PI = Math.sqrt(2 * Math.PI);

    // True for the small chart without lines and labels
    private final boolean small;

    // The figures drawn; null until setStats is called
    private MarkStats stats = null;

    // The size last drawn, so the chart only redraws when it changes
    private double drawnWidth = -1;
    private double drawnHeight = -1;

    /**
     * Creates an empty chart.
     *
     * @param small true for the small chart without lines and labels
     */
    public BellCurveChart(boolean small) {
        this.small = small;
        getStyleClass().add("bell-chart");
        double height = FULL_HEIGHT;
        if (small) {
            height = SMALL_HEIGHT;
        }
        setPrefHeight(height);
        setMinHeight(height);
        setMinWidth(0);
    }

    /**
     * Draws the chart for some figures.
     *
     * @param newStats the figures, or null to clear the chart
     */
    public void setStats(MarkStats newStats) {
        stats = newStats;
        drawnWidth = -1;
        requestLayout();
    }

    /**
     * Redraws the chart when its size has changed.
     */
    @Override
    protected void layoutChildren() {
        double width = getWidth();
        double height = getHeight();
        if (width == drawnWidth && height == drawnHeight) {
            return;
        }
        drawnWidth = width;
        drawnHeight = height;
        getChildren().clear();
        if (stats == null || stats.getMaxTotal() <= 0 || width <= 0) {
            return;
        }
        draw(width, height);
    }

    // Draws every part of the chart into the given size.
    private void draw(double width, double height) {
        double[] margins = FULL_MARGINS;
        if (small) {
            margins = SMALL_MARGINS;
        }
        double left = margins[0];
        double top = margins[2];
        double plotWidth = width - left - margins[1];
        double plotHeight = height - top - margins[3];
        double bottom = top + plotHeight;
        if (plotWidth <= 0 || plotHeight <= 0) {
            return;
        }

        double max = stats.getMaxTotal();
        boolean single = max <= MOST_SINGLE_BARS && max == Math.rint(max);
        int[] counts;
        double binMarks;
        double lowMark;
        double highMark;
        if (single) {
            counts = countSingle((int) max + 1);
            binMarks = 1.0;
            lowMark = -HALF_MARK;
            highMark = max + HALF_MARK;
        } else {
            counts = stats.countBins(GROUPED_BINS);
            binMarks = max / GROUPED_BINS;
            lowMark = 0.0;
            highMark = max;
        }
        ChartScale x = new ChartScale(lowMark, highMark, left,
                left + plotWidth);

        double tallest = curveHeight(stats.getMean(), binMarks);
        for (int count : counts) {
            tallest = Math.max(tallest, count);
        }
        int gridStep = gridStep(tallest * HEADROOM);
        double yMax = Math.max(gridStep, Math.ceil(tallest * HEADROOM
                / gridStep) * gridStep);
        ChartScale y = new ChartScale(0, yMax, bottom, top);

        if (!small) {
            drawGrid(y, gridStep, yMax, left, left + plotWidth);
            drawBand(x, top, plotHeight);
        }
        drawBars(counts, x, y, single, binMarks, bottom);
        drawCurve(x, y, binMarks, lowMark, highMark);
        addLine(left, bottom, left + plotWidth, bottom, "chart-axis");
        drawTicks(x, max, bottom, single);
        if (!small) {
            drawMarkers(x, top, bottom);
            drawAxisNames(left, plotWidth, top, plotHeight, height);
        }
    }

    // Counts how many students got each whole mark, rounding halves up.
    private int[] countSingle(int binCount) {
        int[] counts = new int[binCount];
        for (double total : stats.getTotals()) {
            int mark = (int) Math.round(total);
            mark = Math.max(0, Math.min(binCount - 1, mark));
            counts[mark]++;
        }
        return counts;
    }

    // Draws the horizontal gridlines and their numbers.
    private void drawGrid(ChartScale y, int step, double yMax, double from,
            double to) {
        for (int value = 0; value <= yMax; value += step) {
            double at = y.map(value);
            if (value > 0) {
                addLine(from, at, to, at, "chart-grid");
            }
            Text label = addText(String.valueOf(value), "chart-tick");
            label.setTextAlignment(TextAlignment.RIGHT);
            placeRight(label, from - LABEL_GAP, at);
        }
    }

    // Shades the band of the average plus or minus one spread.
    private void drawBand(ChartScale x, double top, double plotHeight) {
        double spread = stats.getSpread();
        if (spread <= 0) {
            return;
        }
        double from = x.clampMap(stats.getMean() - spread);
        double to = x.clampMap(stats.getMean() + spread);
        Rectangle band = new Rectangle(from, top, to - from, plotHeight);
        band.getStyleClass().add("chart-band");
        band.setMouseTransparent(true);
        getChildren().add(band);
    }

    // Draws one bar per bin, with a tooltip giving its count.
    private void drawBars(int[] counts, ChartScale x, ChartScale y,
            boolean single, double binMarks, double bottom) {
        for (int bin = 0; bin < counts.length; bin++) {
            if (counts[bin] > 0) {
                drawBar(bin, counts[bin], x, y, single, binMarks, bottom);
            }
        }
    }

    // Draws the bar of one bin.
    private void drawBar(int bin, int count, ChartScale x, ChartScale y,
            boolean single, double binMarks, double bottom) {
        double from;
        double to;
        if (single) {
            from = x.map(bin - 0.5);
            to = x.map(bin + 0.5);
        } else {
            from = x.map(bin * binMarks);
            to = x.map((bin + 1) * binMarks);
        }
        double barWidth = Math.max(1, to - from - BAR_GAP);
        double barHeight = bottom - y.map(count);
        SVGPath bar = new SVGPath();
        bar.setContent(roundedTop(from + BAR_GAP / 2, bottom, barWidth,
                barHeight));
        bar.getStyleClass().add("chart-bar");
        Tooltip.install(bar, new Tooltip(binName(bin, single, binMarks)
                + ": " + Parts.plural(count, "student")));
        getChildren().add(bar);
    }

    // Draws the bell curve of the average and spread.
    private void drawCurve(ChartScale x, ChartScale y, double binMarks,
            double lowMark, double highMark) {
        if (stats.getSpread() <= 0 || stats.getCount() < 2) {
            return;
        }
        Polyline curve = new Polyline();
        double step = (highMark - lowMark) / CURVE_STEPS;
        for (int i = 0; i <= CURVE_STEPS; i++) {
            double mark = lowMark + i * step;
            curve.getPoints().addAll(x.map(mark),
                    y.map(curveHeight(mark, binMarks)));
        }
        curve.getStyleClass().add("chart-curve");
        curve.setMouseTransparent(true);
        getChildren().add(curve);
    }

    // Labels the marks along the bottom.
    private void drawTicks(ChartScale x, double max, double bottom,
            boolean single) {
        double step;
        if (small) {
            step = max / SMALL_TICKS;
        } else if (single && max <= MOST_SINGLE_BARS / 2) {
            step = FULL_TICK_MARKS;
        } else {
            step = max / (GROUPED_BINS / FULL_TICK_MARKS);
        }
        for (double mark = 0; mark <= max + step / 2; mark += step) {
            Text label = addText(Parts.mark(round(mark)), "chart-tick");
            placeCentre(label, x.map(mark), bottom + LABEL_GAP);
        }
    }

    // Draws the pass mark and average lines with their names.
    private void drawMarkers(ChartScale x, double top, double bottom) {
        double pass = x.clampMap(stats.getPassMark());
        Line passLine = addLine(pass, top - LINE_RISE, pass, bottom,
                "chart-pass");
        passLine.setMouseTransparent(true);
        Text passName = addText("Pass mark " + Parts.mark(round(
                stats.getPassMark())), "chart-note");
        double mean = x.clampMap(stats.getMean());
        Line meanLine = addLine(mean, top - LINE_RISE, mean, bottom,
                "chart-mean");
        meanLine.setMouseTransparent(true);
        Text meanName = addText("Average " + Parts.mark(round(
                stats.getMean())), "chart-label");

        double nameY = top - LINE_RISE - LABEL_GAP;
        if (mean >= pass) {
            placeRight(passName, pass - LABEL_GAP, nameY);
            placeLeft(meanName, mean + LABEL_GAP, nameY);
        } else {
            placeRight(meanName, mean - LABEL_GAP, nameY);
            placeLeft(passName, pass + LABEL_GAP, nameY);
        }
    }

    // Names the two axes.
    private void drawAxisNames(double left, double plotWidth, double top,
            double plotHeight, double height) {
        Text across = addText("Total mark out of "
                + Parts.mark(stats.getMaxTotal()), "chart-note");
        placeCentre(across, left + plotWidth / 2,
                height - across.getLayoutBounds().getHeight());
        Text up = addText("Students", "chart-note");
        up.setRotate(QUARTER_TURN);
        double textWidth = up.getLayoutBounds().getWidth();
        up.setLayoutX(-textWidth / 2 + up.getLayoutBounds().getHeight() / 2);
        up.setLayoutY(top + plotHeight / 2);
    }

    // Returns the height of the bell curve at a mark, in students.
    private double curveHeight(double mark, double binMarks) {
        double spread = stats.getSpread();
        if (spread <= 0) {
            return 0;
        }
        double z = (mark - stats.getMean()) / spread;
        double density = Math.exp(-z * z / 2) / (spread * ROOT_TWO_PI);
        return stats.getCount() * density * binMarks;
    }

    // Returns the name of a bin for its tooltip.
    private String binName(int bin, boolean single, double binMarks) {
        if (single) {
            return Parts.plural(bin, "mark");
        }
        return Parts.mark(round(bin * binMarks)) + " to "
                + Parts.mark(round((bin + 1) * binMarks)) + " marks";
    }

    // Picks a gap between gridlines that gives at most five lines.
    private static int gridStep(double top) {
        for (int step : GRID_STEPS) {
            if (top / step <= MOST_GRIDLINES) {
                return step;
            }
        }
        return GRID_STEPS[GRID_STEPS.length - 1];
    }

    // Rounds to one decimal place.
    private static double round(double value) {
        return Math.round(value * ONE_DECIMAL) / ONE_DECIMAL;
    }

    // Returns the path of a bar with rounded top corners.
    private static String roundedTop(double x, double bottom, double width,
            double height) {
        double radius = Math.min(BAR_RADIUS, Math.min(width / 2, height));
        return "M" + x + "," + bottom
                + " V" + (bottom - height + radius)
                + " Q" + x + "," + (bottom - height) + " " + (x + radius)
                + "," + (bottom - height)
                + " H" + (x + width - radius)
                + " Q" + (x + width) + "," + (bottom - height) + " "
                + (x + width) + "," + (bottom - height + radius)
                + " V" + bottom + " Z";
    }

    // Adds a line with a style class.
    private Line addLine(double x1, double y1, double x2, double y2,
            String styleClass) {
        Line line = new Line(x1, y1, x2, y2);
        line.getStyleClass().add(styleClass);
        getChildren().add(line);
        return line;
    }

    // Adds a piece of text with a style class, laid out by CSS first so
    // its size is known.
    private Text addText(String value, String styleClass) {
        Text text = new Text(value);
        text.getStyleClass().add(styleClass);
        text.setMouseTransparent(true);
        getChildren().add(text);
        text.applyCss();
        return text;
    }

    // Places text so its right edge is at x and its middle at y.
    private static void placeRight(Text text, double x, double y) {
        text.setLayoutX(x - text.getLayoutBounds().getWidth());
        text.setLayoutY(y - text.getLayoutBounds().getMinY()
                - text.getLayoutBounds().getHeight() / 2);
    }

    // Places text so its left edge is at x and its middle at y.
    private static void placeLeft(Text text, double x, double y) {
        text.setLayoutX(x);
        text.setLayoutY(y - text.getLayoutBounds().getMinY()
                - text.getLayoutBounds().getHeight() / 2);
    }

    // Places text so its centre is at x and its top at y.
    private static void placeCentre(Text text, double x, double y) {
        text.setLayoutX(x - text.getLayoutBounds().getWidth() / 2);
        text.setLayoutY(y - text.getLayoutBounds().getMinY());
    }
}
