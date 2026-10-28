/*
 * File: CompareChart.java
 *
 * Purpose: Draws one smooth bell curve per section on the same axes, so
 * up to three sections can be compared. Each curve is scaled to the
 * share of students (not the number), so sections of different sizes
 * compare fairly. Each curve has a light fill, a dot at its peak and
 * the section's name next to the dot.
 *
 * Where it fits: The Compare sections view of the Statistics tab. Its
 * colours come from the series-1 to series-3 classes in flow.css,
 * which passed a colour-blindness check in both themes. It redraws
 * itself when its size changes.
 */
package gradezzy.ui.flow;

import java.util.ArrayList;
import java.util.List;

import javafx.geometry.BoundingBox;
import javafx.geometry.Bounds;
import javafx.scene.layout.Region;
import javafx.scene.shape.Circle;
import javafx.scene.shape.Line;
import javafx.scene.shape.Polygon;
import javafx.scene.shape.Polyline;
import javafx.scene.text.Text;

/**
 * Bell curves of several sections on one chart.
 */
public class CompareChart extends Region {

    /** The most sections one chart shows. */
    public static final int MOST_SERIES = 3;

    /** The height of the chart. */
    public static final double HEIGHT = 300.0;

    // Space for the axis labels: left, right, top, bottom
    private static final double[] MARGINS = {48, 16, 24, 36};

    // Turns a fraction into a percentage
    private static final double PERCENT = 100.0;

    // Leaves room above the tallest curve for its label
    private static final double HEADROOM = 1.15;

    // The most gridlines
    private static final int MOST_GRIDLINES = 5;

    // Candidate gaps between gridlines, in percent
    private static final int[] GRID_STEPS = {1, 2, 4, 5, 10, 20, 25, 50};

    // How many points draw each curve
    private static final int CURVE_STEPS = 160;

    // Up to this maximum, the marks are labelled every TICK_MARKS marks
    private static final double MOST_TICK_MAX = 20.0;

    // The gap between mark labels for a small maximum
    private static final double TICK_MARKS = 2.0;

    // How many mark labels for a large maximum
    private static final int TICK_COUNT = 10;

    // The radius of the dot at a curve's peak
    private static final double DOT_RADIUS = 4.0;

    // How far a label sits from what it labels
    private static final double LABEL_GAP = 6.0;

    // How far a section's name sits above its dot
    private static final double NAME_GAP = 10.0;

    // The gap kept between two names moved apart
    private static final double NAME_SPACING = 2.0;

    // Turns the name of the up axis on its side
    private static final double QUARTER_TURN = -90.0;

    // A normal curve's height is scaled by one over the square root of
    // two pi
    private static final double ROOT_TWO_PI = Math.sqrt(2 * Math.PI);

    // The name of each section
    private final List<String> names = new ArrayList<>();

    // The figures of each section
    private final List<MarkStats> stats = new ArrayList<>();

    // The size last drawn, so the chart only redraws when it changes
    private double drawnWidth = -1;
    private double drawnHeight = -1;

    /**
     * Creates an empty chart.
     */
    public CompareChart() {
        getStyleClass().add("compare-chart");
        setMinHeight(HEIGHT);
        setPrefHeight(HEIGHT);
        setMinWidth(0);
    }

    /**
     * Sets the sections drawn. Only the first MOST_SERIES are drawn;
     * the n-th one takes the class series-n.
     *
     * @param newNames the name of each section
     * @param newStats the figures of each section, in the same order
     */
    public void setSeries(List<String> newNames, List<MarkStats> newStats) {
        names.clear();
        stats.clear();
        int count = Math.min(MOST_SERIES, Math.min(newNames.size(),
                newStats.size()));
        for (int index = 0; index < count; index++) {
            names.add(newNames.get(index));
            stats.add(newStats.get(index));
        }
        drawnWidth = -1;
        requestLayout();
    }

    /**
     * Returns how many sections are drawn, for the developer checks.
     *
     * @return the count
     */
    public int getSeriesCount() {
        return stats.size();
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
        if (stats.isEmpty() || width <= 0) {
            return;
        }
        draw(width, height);
    }

    // Draws every part of the chart into the given size.
    private void draw(double width, double height) {
        double left = MARGINS[0];
        double top = MARGINS[2];
        double plotWidth = width - left - MARGINS[1];
        double plotHeight = height - top - MARGINS[3];
        double bottom = top + plotHeight;
        double max = stats.get(0).getMaxTotal();
        if (plotWidth <= 0 || plotHeight <= 0 || max <= 0) {
            return;
        }

        double tallest = 0;
        for (MarkStats one : stats) {
            tallest = Math.max(tallest, peak(one));
        }
        int step = gridStep(tallest * HEADROOM);
        double yMax = Math.max(step, Math.ceil(tallest * HEADROOM / step)
                * step);
        yMax = Math.min(yMax, PERCENT);
        double right = left + plotWidth;

        drawGrid(step, yMax, left, right, top, plotHeight);
        for (int index = 0; index < stats.size(); index++) {
            drawCurve(index, max, yMax, left, plotWidth, top, plotHeight);
        }
        Line axis = new Line(left, bottom, right, bottom);
        axis.getStyleClass().add("chart-axis");
        getChildren().add(axis);
        drawTicks(max, left, plotWidth, bottom);
        List<Bounds> placed = new ArrayList<>();
        for (int index = 0; index < stats.size(); index++) {
            drawPeak(index, max, yMax, left, plotWidth, top, plotHeight,
                    placed);
        }
        drawAxisNames(max, left, plotWidth, top, plotHeight, height);
    }

    // Draws the horizontal gridlines and their percentages.
    private void drawGrid(int step, double yMax, double from, double to,
            double top, double plotHeight) {
        for (int value = 0; value <= yMax; value += step) {
            double at = top + plotHeight - plotHeight * value / yMax;
            if (value > 0) {
                Line line = new Line(from, at, to, at);
                line.getStyleClass().add("chart-grid");
                getChildren().add(line);
            }
            Text label = addText(value + "%", "chart-tick");
            label.setLayoutX(from - LABEL_GAP
                    - label.getLayoutBounds().getWidth());
            label.setLayoutY(at - label.getLayoutBounds().getMinY()
                    - label.getLayoutBounds().getHeight() / 2);
        }
    }

    // Draws one section's curve with a light fill under it.
    private void drawCurve(int index, double max, double yMax, double left,
            double plotWidth, double top, double plotHeight) {
        MarkStats one = stats.get(index);
        if (one.getSpread() <= 0) {
            return;
        }
        double bottom = top + plotHeight;
        Polyline curve = new Polyline();
        Polygon fill = new Polygon();
        fill.getPoints().addAll(left, bottom);
        for (int i = 0; i <= CURVE_STEPS; i++) {
            double mark = max * i / CURVE_STEPS;
            double share = Math.min(yMax, share(one, mark));
            double x = left + plotWidth * mark / max;
            double y = bottom - plotHeight * share / yMax;
            curve.getPoints().addAll(x, y);
            fill.getPoints().addAll(x, y);
        }
        fill.getPoints().addAll(left + plotWidth, bottom);
        String series = seriesClass(index);
        fill.getStyleClass().addAll("compare-fill", series);
        curve.getStyleClass().addAll("compare-curve", series);
        fill.setMouseTransparent(true);
        curve.setMouseTransparent(true);
        getChildren().addAll(fill, curve);
    }

    // Draws the dot at a curve's peak and the section's name above it,
    // moving the name up when it would cover a name already placed.
    private void drawPeak(int index, double max, double yMax, double left,
            double plotWidth, double top, double plotHeight,
            List<Bounds> placed) {
        MarkStats one = stats.get(index);
        double mean = Math.max(0, Math.min(max, one.getMean()));
        double x = left + plotWidth * mean / max;
        double y = top + plotHeight - plotHeight
                * Math.min(yMax, peak(one)) / yMax;
        Circle dot = new Circle(x, y, DOT_RADIUS);
        dot.getStyleClass().addAll("compare-dot", seriesClass(index));
        getChildren().add(dot);

        Text name = addText(names.get(index), "chart-label");
        double textWidth = name.getLayoutBounds().getWidth();
        double textHeight = name.getLayoutBounds().getHeight();
        double nameTop = y - NAME_GAP - textHeight;
        Bounds box = new BoundingBox(x - textWidth / 2, nameTop, textWidth,
                textHeight);
        boolean moved = true;
        while (moved) {
            moved = false;
            for (Bounds other : placed) {
                if (other.intersects(box)) {
                    nameTop = other.getMinY() - textHeight
                            - NAME_SPACING;
                    box = new BoundingBox(box.getMinX(), nameTop,
                            textWidth, textHeight);
                    moved = true;
                }
            }
        }
        placed.add(box);
        name.setLayoutX(box.getMinX());
        name.setLayoutY(nameTop - name.getLayoutBounds().getMinY());
    }

    // Labels the marks along the bottom.
    private void drawTicks(double max, double left, double plotWidth,
            double bottom) {
        double step = TICK_MARKS;
        if (max > MOST_TICK_MAX || max != Math.rint(max)) {
            step = max / TICK_COUNT;
        }
        for (double mark = 0; mark <= max + step / 2; mark += step) {
            Text label = addText(Parts.mark(Math.min(max, mark)),
                    "chart-tick");
            label.setLayoutX(left + plotWidth * mark / max
                    - label.getLayoutBounds().getWidth() / 2);
            label.setLayoutY(bottom + LABEL_GAP
                    - label.getLayoutBounds().getMinY());
        }
    }

    // Names the two axes.
    private void drawAxisNames(double max, double left, double plotWidth,
            double top, double plotHeight, double height) {
        Text across = addText("Total mark out of " + Parts.mark(max),
                "chart-note");
        across.setLayoutX(left + plotWidth / 2
                - across.getLayoutBounds().getWidth() / 2);
        across.setLayoutY(height - across.getLayoutBounds().getHeight()
                - across.getLayoutBounds().getMinY());
        Text up = addText("Share of students", "chart-note");
        up.setRotate(QUARTER_TURN);
        double textWidth = up.getLayoutBounds().getWidth();
        up.setLayoutX(-textWidth / 2 + up.getLayoutBounds().getHeight() / 2);
        up.setLayoutY(top + plotHeight / 2);
    }

    // Returns the height of a section's curve at a mark, as the percent
    // of its students expected to get that mark.
    private static double share(MarkStats one, double mark) {
        double spread = one.getSpread();
        double z = (mark - one.getMean()) / spread;
        return PERCENT * Math.exp(-z * z / 2) / (spread * ROOT_TWO_PI);
    }

    // Returns the height of a section's peak. When every student has
    // the same total there is no curve, and all of them are at the mean.
    private static double peak(MarkStats one) {
        if (one.getSpread() <= 0) {
            return PERCENT;
        }
        return share(one, one.getMean());
    }

    // Returns the style class of the n-th section, counting from 1.
    private static String seriesClass(int index) {
        return "series-" + (index + 1);
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
}
