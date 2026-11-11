/*
 * File: StatusRing.java
 *
 * Purpose: Draws a ring (donut) chart of how many students are in each
 * status, with the number of students in the middle.
 *
 * Where it fits: The "Student status" card of the Statistics tab.
 * The card's legend repeats every status in words with its count, so
 * the ring never relies on colour alone.
 */
package gradezzy.ui.flow;

import java.util.ArrayList;
import java.util.List;

import javafx.geometry.Pos;
import javafx.scene.control.Tooltip;
import javafx.scene.layout.Region;
import javafx.scene.layout.VBox;
import javafx.scene.shape.Arc;
import javafx.scene.shape.ArcType;
import javafx.scene.text.Text;

/**
 * A ring chart of status counts.
 *
 * <p>Tier: SHOULD.
 */
public class StatusRing extends Region {

    /** The width and height of the ring. */
    public static final double SIZE = 132.0;

    // The distance from the edge of the box to the middle of the ring
    private static final double INSET = 10.0;

    // The gap between two parts of the ring, in pixels along the ring
    private static final double PART_GAP = 3.0;

    // A full turn, in degrees
    private static final double FULL_TURN = 360.0;

    // Parts start at the top of the ring
    private static final double TOP = 90.0;

    // The gap between the number and the word under it
    private static final double TEXT_GAP = 2.0;

    // Divides a length in two, to find a middle
    private static final double HALF = 2.0;

    // Separates a part's name from its count in the part's tip
    private static final String TIP_SEPARATOR = ": ";

    // The name of each part
    private final List<String> names = new ArrayList<>();

    // The count of each part
    private final List<Integer> counts = new ArrayList<>();

    // The colour of each part
    private final List<String> colours = new ArrayList<>();

    /**
     * Creates an empty ring.
     */
    public StatusRing() {
        getStyleClass().add("status-ring");
        setMinSize(SIZE, SIZE);
        setPrefSize(SIZE, SIZE);
        setMaxSize(SIZE, SIZE);
    }

    /**
     * Removes every part.
     */
    public void clear() {
        names.clear();
        counts.clear();
        colours.clear();
        draw();
    }

    /**
     * Adds one part after the others, going clockwise from the top.
     *
     * @param name the status in words, for the tooltip
     * @param count how many students have it
     * @param colour the style class that colours it, such as "good"
     */
    public void addPart(String name, int count, String colour) {
        names.add(name);
        counts.add(count);
        colours.add(colour);
        draw();
    }

    // Draws the parts and the total in the middle.
    private void draw() {
        getChildren().clear();
        int total = 0;
        for (int count : counts) {
            total += count;
        }
        double centre = SIZE / HALF;
        double radius = centre - INSET;
        if (total > 0) {
            drawParts(total, centre, radius);
        }
        drawTotal(total);
    }

    // Draws the number of students and the word under it in the middle.
    // A box the size of the ring centres them when it is laid out, once
    // the fonts of the stylesheet are known, so the number sits in the
    // middle whatever its width.
    private void drawTotal(int total) {
        Text number = new Text(String.valueOf(total));
        number.getStyleClass().add("ring-total");
        String unit = "students";
        if (total == 1) {
            unit = "student";
        }
        Text word = new Text(unit);
        word.getStyleClass().add("chart-note");
        VBox middle = new VBox(TEXT_GAP, number, word);
        middle.setAlignment(Pos.CENTER);
        middle.setMinSize(SIZE, SIZE);
        middle.setPrefSize(SIZE, SIZE);
        middle.setMaxSize(SIZE, SIZE);
        middle.setMouseTransparent(true);
        getChildren().add(middle);
    }

    // Draws one arc per part with a small gap between parts.
    private void drawParts(int total, double centre, double radius) {
        int shown = 0;
        for (int count : counts) {
            if (count > 0) {
                shown++;
            }
        }
        double gap = 0;
        if (shown > 1) {
            gap = Math.toDegrees(PART_GAP / radius);
        }
        double start = TOP;
        for (int index = 0; index < counts.size(); index++) {
            int count = counts.get(index);
            if (count > 0) {
                double length = FULL_TURN * count / total;
                double drawn = Math.max(0, length - gap);
                Arc arc = new Arc(centre, centre, radius, radius,
                        start, -drawn);
                arc.setType(ArcType.OPEN);
                arc.getStyleClass().addAll("ring-part",
                        colours.get(index));
                String tip = names.get(index) + TIP_SEPARATOR
                        + Parts.plural(count, "student");
                Tooltip.install(arc, new Tooltip(tip));
                getChildren().add(arc);
                start -= length;
            }
        }
    }
}
