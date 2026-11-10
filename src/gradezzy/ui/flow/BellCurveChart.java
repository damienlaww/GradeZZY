/*
 * File: BellCurveChart.java
 *
 * Purpose: Draws how the total marks spread: one bar per mark (how many
 * students got it) with a smooth bell curve on top. The curve is
 * stretched so its peak meets the tallest bar, because a small class
 * spread over many marks gives a true curve too low to see; it shows the
 * shape of the spread, not a number of students. The small chart
 * groups the marks into five wider bars instead, so a small class still
 * shows the bell's shape. The full chart also
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
import javafx.scene.shape.Polygon;
import javafx.scene.shape.Polyline;
import javafx.scene.shape.Rectangle;
import javafx.scene.shape.SVGPath;
import javafx.scene.text.Text;
import javafx.scene.text.TextAlignment;

/**
 * A bar chart of total marks with a bell curve on top.
 *
 * <p>Tier: SHOULD.
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

    // How many bins the small chart always uses, such as 0 to 4, 4 to 8
    // and so on out of 20. With one bar per mark, a few students spread
    // over the marks give a curve too low to see against the bars.
    private static final int SMALL_BINS = 5;

    // Space for the axis labels of the full chart: left, right, top,
    // bottom
    private static final double[] FULL_MARGINS = {40, 12, 30, 36};

    // Space for the axis labels of the small chart
    private static final double[] SMALL_MARGINS = {4, 4, 6, 18};

    // Where each side is kept in the margins above
    private static final int LEFT_SIDE = 0;
    private static final int RIGHT_SIDE = 1;
    private static final int TOP_SIDE = 2;
    private static final int BOTTOM_SIDE = 3;

    // The fewest students with a total that a curve is drawn for
    private static final int FEWEST_FOR_CURVE = 2;

    // Separates a bar's marks from its count in the bar's tip
    private static final String TIP_SEPARATOR = ": ";

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
        double left = margins[LEFT_SIDE];
        double top = margins[TOP_SIDE];
        double plotWidth = width - left - margins[RIGHT_SIDE];
        double plotHeight = height - top - margins[BOTTOM_SIDE];
        double bottom = top + plotHeight;
        double right = left + plotWidth;
        if (plotWidth <= 0 || plotHeight <= 0) {
            return;
        }

        double max = stats.getMaxTotal();
        boolean single = !small && max <= MOST_SINGLE_BARS
                && max == Math.rint(max);
        int binCount = GROUPED_BINS;
        if (small) {
            binCount = SMALL_BINS;
        }
        int[] counts = null;
        double binMarks = 0.0;
        double lowMark = 0.0;
        double highMark = 0.0;
        if (single) {
            int markCount = (int) max + 1;
            counts = countSingle(markCount);
            binMarks = 1.0;
            lowMark = -HALF_MARK;
            highMark = max + HALF_MARK;
        } else {
            counts = stats.countBins(binCount);
            binMarks = max / binCount;
            lowMark = 0.0;
            highMark = max;
        }
        ChartScale xScale = new ChartScale(lowMark, highMark, left, right);

        double tallest = 0;
        for (int count : counts) {
            tallest = Math.max(tallest, count);
        }
        // The curve's peak meets the tallest bar, so its shape shows even
        // when a few students are spread over many marks
        double peak = curveHeight(stats.getMean(), binMarks);
        double curveScale = 1.0;
        if (peak > 0 && tallest > 0) {
            curveScale = tallest / peak;
        }
        tallest = Math.max(tallest, peak * curveScale);
        double roomyTop = tallest * HEADROOM;
        int gridStep = gridStep(roomyTop);
        double gridLines = Math.ceil(roomyTop / gridStep);
        double yMax = Math.max(gridStep, gridLines * gridStep);
        ChartScale yScale = new ChartScale(0, yMax, bottom, top);

        if (!small) {
            drawGrid(yScale, gridStep, yMax, left, right);
            drawBand(xScale, top, plotHeight);
        }
        drawBars(counts, xScale, yScale, single, binMarks, bottom);
        double stretchedBinMarks = binMarks * curveScale;
        drawCurve(xScale, yScale, stretchedBinMarks, lowMark, highMark);
        addLine(left, bottom, right, bottom, "chart-axis");
        drawTicks(xScale, max, bottom, single);
        if (!small) {
            drawMarkers(xScale, top, bottom);
            drawAxisNames(left, plotWidth, top, plotHeight, height);
        }
    }

    // Counts how many students got each whole mark, rounding halves up.
    private int[] countSingle(int binCount) {
        int[] counts = new int[binCount];
        int lastBin = binCount - 1;
        for (double total : stats.getTotals()) {
            int mark = (int) Math.round(total);
            mark = Math.max(0, Math.min(lastBin, mark));
            counts[mark]++;
        }
        return counts;
    }

    // Draws the horizontal gridlines and their numbers.
    private void drawGrid(ChartScale yScale, int step, double yMax,
            double from, double to) {
        // The axis is the line at 0, so 0 gets only its number
        addGridNumber(yScale, 0, from);
        for (int value = step; value <= yMax; value += step) {
            double at = yScale.map(value);
            addLine(from, at, to, at, "chart-grid");
            addGridNumber(yScale, value, from);
        }
    }

    // Writes the number of one gridline on its left.
    private void addGridNumber(ChartScale yScale, int value, double from) {
        Text label = addText(String.valueOf(value), "chart-tick");
        label.setTextAlignment(TextAlignment.RIGHT);
        placeRight(label, from - LABEL_GAP, yScale.map(value));
    }

    // Shades the band of the average plus or minus one spread.
    private void drawBand(ChartScale xScale, double top, double plotHeight) {
        double spread = stats.getSpread();
        if (spread <= 0) {
            return;
        }
        double from = xScale.clampMap(stats.getMean() - spread);
        double to = xScale.clampMap(stats.getMean() + spread);
        Rectangle band = new Rectangle(from, top, to - from, plotHeight);
        band.getStyleClass().add("chart-band");
        band.setMouseTransparent(true);
        getChildren().add(band);
    }

    // Draws one bar per bin, with a tooltip giving its count.
    private void drawBars(int[] counts, ChartScale xScale, ChartScale yScale,
            boolean single, double binMarks, double bottom) {
        for (int bin = 0; bin < counts.length; bin++) {
            if (counts[bin] > 0) {
                drawBar(bin, counts[bin], xScale, yScale, single, binMarks,
                        bottom);
            }
        }
    }

    // Draws the bar of one bin.
    private void drawBar(int bin, int count, ChartScale xScale,
            ChartScale yScale, boolean single, double binMarks,
            double bottom) {
        double from = 0.0;
        double to = 0.0;
        if (single) {
            from = xScale.map(bin - HALF_MARK);
            to = xScale.map(bin + HALF_MARK);
        } else {
            from = xScale.map(bin * binMarks);
            to = xScale.map((bin + 1) * binMarks);
        }
        double spaceWidth = to - from - BAR_GAP;
        double barWidth = Math.max(1, spaceWidth);
        double barHeight = bottom - yScale.map(count);
        double barLeft = from + BAR_GAP / 2;
        SVGPath bar = new SVGPath();
        bar.setContent(roundedTop(barLeft, bottom, barWidth, barHeight));
        bar.getStyleClass().add("chart-bar");
        Tooltip.install(bar, new Tooltip(binName(bin, single, binMarks)
                + TIP_SEPARATOR + Parts.plural(count, "student")));
        getChildren().add(bar);
    }

    // Draws the bell curve of the average and spread, with the area under
    // it tinted so the shape reads at a glance. binMarks also carries the
    // stretch that makes the curve's peak meet the tallest bar.
    private void drawCurve(ChartScale xScale, ChartScale yScale,
            double binMarks, double lowMark, double highMark) {
        if (stats.getSpread() <= 0 || stats.getCount() < FEWEST_FOR_CURVE) {
            return;
        }
        Polyline curve = new Polyline();
        Polygon area = new Polygon();
        area.getPoints().addAll(xScale.map(lowMark), yScale.map(0));
        double step = (highMark - lowMark) / CURVE_STEPS;
        for (int i = 0; i <= CURVE_STEPS; i++) {
            double mark = lowMark + i * step;
            double at = yScale.map(curveHeight(mark, binMarks));
            curve.getPoints().addAll(xScale.map(mark), at);
            area.getPoints().addAll(xScale.map(mark), at);
        }
        area.getPoints().addAll(xScale.map(highMark), yScale.map(0));
        area.getStyleClass().add("chart-curve-area");
        area.setMouseTransparent(true);
        curve.getStyleClass().add("chart-curve");
        curve.setMouseTransparent(true);
        getChildren().addAll(area, curve);
    }

    // Labels the marks along the bottom.
    private void drawTicks(ChartScale xScale, double max, double bottom,
            boolean single) {
        double step = 0.0;
        if (small) {
            // One label on each edge between bars
            step = max / SMALL_BINS;
        } else if (single && max <= MOST_SINGLE_BARS / 2) {
            step = FULL_TICK_MARKS;
        } else {
            int tickCount = GROUPED_BINS / FULL_TICK_MARKS;
            step = max / tickCount;
        }
        double lastTick = max + step / 2;
        for (double mark = 0; mark <= lastTick; mark += step) {
            Text label = addText(Parts.mark(round(mark)), "chart-tick");
            placeCentre(label, xScale.map(mark), bottom + LABEL_GAP);
        }
    }

    // Draws the pass mark and average lines with their names.
    private void drawMarkers(ChartScale xScale, double top, double bottom) {
        double pass = xScale.clampMap(stats.getPassMark());
        Line passLine = addLine(pass, top - LINE_RISE, pass, bottom,
                "chart-pass");
        passLine.setMouseTransparent(true);
        Text passName = addText("Pass mark " + Parts.mark(round(
                stats.getPassMark())), "chart-note");
        double mean = xScale.clampMap(stats.getMean());
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
        double plotCentreX = left + plotWidth / 2;
        double acrossTop = height - across.getLayoutBounds().getHeight();
        placeCentre(across, plotCentreX, acrossTop);
        Text up = addText("Students", "chart-note");
        up.setRotate(QUARTER_TURN);
        double textWidth = up.getLayoutBounds().getWidth();
        double textHeight = up.getLayoutBounds().getHeight();
        double upX = -textWidth / 2 + textHeight / 2;
        double plotCentreY = top + plotHeight / 2;
        up.setLayoutX(upX);
        up.setLayoutY(plotCentreY);
    }

    // Returns the height of the true bell curve at a mark, in students,
    // before it is stretched to the tallest bar.
    private double curveHeight(double mark, double binMarks) {
        double spread = stats.getSpread();
        if (spread <= 0) {
            return 0;
        }
        double zScore = (mark - stats.getMean()) / spread;
        double density = Math.exp(-zScore * zScore / 2)
                / (spread * ROOT_TWO_PI);
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
        int lastIndex = GRID_STEPS.length - 1;
        return GRID_STEPS[lastIndex];
    }

    // Rounds to one decimal place.
    private static double round(double value) {
        return Math.round(value * ONE_DECIMAL) / ONE_DECIMAL;
    }

    // Returns the path of a bar with rounded top corners.
    private static String roundedTop(double leftX, double bottom,
            double width, double height) {
        double halfWidth = width / 2;
        double radius = Math.min(BAR_RADIUS, Math.min(halfWidth, height));
        double barTop = bottom - height;
        double cornerBottom = barTop + radius;
        double rightX = leftX + width;
        double leftCornerEnd = leftX + radius;
        double rightCornerStart = rightX - radius;
        return "M" + leftX + "," + bottom
                + " V" + cornerBottom
                + " Q" + leftX + "," + barTop + " "
                + leftCornerEnd + "," + barTop
                + " H" + rightCornerStart
                + " Q" + rightX + "," + barTop + " "
                + rightX + "," + cornerBottom
                + " V" + bottom + " Z";
    }

    // Adds a line with a style class.
    private Line addLine(double startX, double startY, double endX,
            double endY, String styleClass) {
        Line line = new Line(startX, startY, endX, endY);
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

    // Places text so its right edge is at rightX and its middle at
    // middleY.
    private static void placeRight(Text text, double rightX,
            double middleY) {
        text.setLayoutX(rightX - text.getLayoutBounds().getWidth());
        text.setLayoutY(middleY - text.getLayoutBounds().getMinY()
                - text.getLayoutBounds().getHeight() / 2);
    }

    // Places text so its left edge is at leftX and its middle at middleY.
    private static void placeLeft(Text text, double leftX, double middleY) {
        text.setLayoutX(leftX);
        text.setLayoutY(middleY - text.getLayoutBounds().getMinY()
                - text.getLayoutBounds().getHeight() / 2);
    }

    // Places text so its centre is at centreX and its top at topY.
    private static void placeCentre(Text text, double centreX,
            double topY) {
        text.setLayoutX(centreX - text.getLayoutBounds().getWidth() / 2);
        text.setLayoutY(topY - text.getLayoutBounds().getMinY());
    }
}
