/*
 * File: StatusRing.java
 *
 * Purpose: Draws a ring (donut) chart of how many students are in each
 * status, with the number of students in the middle.
 *
 * Where it fits: The "Where students are" card of the Statistics tab.
 * The card's legend repeats every status in words with its count, so
 * the ring never relies on colour alone.
 */
package gradezzy.ui.flow;

import java.util.ArrayList;
import java.util.List;

import javafx.scene.control.Tooltip;
import javafx.scene.layout.Region;
import javafx.scene.shape.Arc;
import javafx.scene.shape.ArcType;
import javafx.scene.text.Text;

/**
 * A ring chart of status counts.
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

    // The name of each part
    private final List<String> names = new ArrayList<>();

    // The count of each part
    private final List<Integer> counts = new ArrayList<>();

    // The colour of each part
    private final List<Tone> tones = new ArrayList<>();

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
        tones.clear();
        draw();
    }

    /**
     * Adds one part after the others, going clockwise from the top.
     *
     * @param name the status in words, for the tooltip
     * @param count how many students have it
     * @param tone its colour
     */
    public void addPart(String name, int count, Tone tone) {
        names.add(name);
        counts.add(count);
        tones.add(tone);
        draw();
    }

    // Draws the parts and the total in the middle.
    private void draw() {
        getChildren().clear();
        int total = 0;
        for (int count : counts) {
            total += count;
        }
        double centre = SIZE / 2;
        double radius = centre - INSET;
        if (total > 0) {
            drawParts(total, centre, radius);
        }

        Text number = new Text(String.valueOf(total));
        number.getStyleClass().add("ring-total");
        Text word = new Text(total == 1 ? "student" : "students");
        word.getStyleClass().add("chart-note");
        getChildren().addAll(number, word);
        number.applyCss();
        word.applyCss();
        double numberHeight = number.getLayoutBounds().getHeight();
        double wordHeight = word.getLayoutBounds().getHeight();
        double top = centre - (numberHeight + TEXT_GAP + wordHeight) / 2;
        number.setLayoutX(centre - number.getLayoutBounds().getWidth() / 2);
        number.setLayoutY(top - number.getLayoutBounds().getMinY());
        word.setLayoutX(centre - word.getLayoutBounds().getWidth() / 2);
        word.setLayoutY(top + numberHeight + TEXT_GAP
                - word.getLayoutBounds().getMinY());
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
                Arc arc = new Arc(centre, centre, radius, radius,
                        start, -Math.max(0, length - gap));
                arc.setType(ArcType.OPEN);
                arc.getStyleClass().addAll("ring-part",
                        tones.get(index).getStyleClass());
                Tooltip.install(arc, new Tooltip(names.get(index) + ": "
                        + Parts.plural(count, "student")));
                getChildren().add(arc);
                start -= length;
            }
        }
    }
}
