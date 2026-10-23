/*
 * File: Icon.java
 *
 * Purpose: The line icons of the interface, drawn as SVG paths on a
 * 16 by 16 grid.
 *
 * Where it fits: Screens and components call create(). The icons take
 * the theme colours through CSS and always sit next to a text label.
 */
package gradezzy.ui.component;

import javafx.scene.layout.Pane;
import javafx.scene.shape.SVGPath;
import javafx.scene.transform.Scale;

/**
 * A line icon.
 */
public enum Icon {

    /** Overview: four tiles. */
    OVERVIEW("M2.5 2.5h4.5v4.5h-4.5zM9 2.5h4.5v4.5h-4.5zM2.5 9h4.5v4.5h-4.5z"
            + "M9 9h4.5v4.5h-4.5z"),

    /** Modules: stacked layers. */
    MODULES("M8 2l6 3-6 3-6-3zM2 8l6 3 6-3M2 11l6 3 6-3"),

    /** Assessments: a checklist. */
    ASSESSMENTS("M3 3.5h1.5M3 8h1.5M3 12.5h1.5M7 3.5h6M7 8h6M7 12.5h6"),

    /** Submissions: an arrow into a tray. */
    SUBMISSIONS("M8 10V2.5M5 5.5l3-3 3 3M2.5 9.5v3.5h11v-3.5"),

    /** Validation: a shield with a tick. */
    VALIDATION("M8 1.8l5 2v4c0 3-2.2 5.2-5 6.4-2.8-1.2-5-3.4-5-6.4v-4z"
            + "M5.6 8l1.7 1.7 3.2-3.4"),

    /** Grading progress: a clock face. */
    PROGRESS("M13.5 8A5.5 5.5 0 1 1 8 2.5M8 5v3l2 1.5M11 2.8l2.5.4-.4 2.5"),

    /** Review anomalies: a warning triangle. */
    ANOMALIES("M8 2.2l6.2 11H1.8zM8 6.5v3M8 11.3v.2"),

    /** Results: a table. */
    RESULTS("M2.5 3h11v10h-11zM2.5 6.5h11M6.5 6.5V13"),

    /** A student: a person. */
    STUDENT("M8 8a2.8 2.8 0 1 0 0-5.6A2.8 2.8 0 0 0 8 8z"
            + "M2.8 13.8c.6-2.6 2.7-4 5.2-4s4.6 1.4 5.2 4"),

    /** Statistics: bars. */
    STATISTICS("M3 13V8M6.5 13V4M10 13V9.5M13.5 13V6"),

    /** Similarity: two overlapping pages. */
    SIMILARITY("M3 2.5h5.5v8H3zM7.5 5.5H13v8H7.5z"),

    /** History: a clock with a turning arrow. */
    HISTORY("M2.8 8a5.2 5.2 0 1 0 1.6-3.8M2.8 2.8v3h3M8 5.2V8l2 1.3"),

    /** Audit log: a page of lines. */
    AUDIT("M4 1.8h5.5L12 4.3v9.9H4zM6 7h4.5M6 9.5h4.5M6 12h2.5"),

    /** Settings: two sliders. */
    SETTINGS("M2.5 4.5h11M2.5 11.5h11M5.5 3v3M10.5 10v3"),

    /** Help: a question mark in a circle. */
    HELP("M8 14.5a6.5 6.5 0 1 0 0-13 6.5 6.5 0 0 0 0 13z"
            + "M6.2 6.2a1.9 1.9 0 1 1 2.6 1.8c-.5.2-.8.6-.8 1.1v.6M8 11.6v.2"),

    /** Search: a magnifying glass. */
    SEARCH("M7 12a5 5 0 1 0 0-10 5 5 0 0 0 0 10zM10.6 10.6l3.4 3.4"),

    /** Passed or completed: a tick in a circle. */
    CHECK("M8 14.5a6.5 6.5 0 1 0 0-13 6.5 6.5 0 0 0 0 13z"
            + "M5.2 8.2l1.9 1.9 3.8-4"),

    /** Needs review: a triangle with an exclamation mark. */
    WARNING("M8 2.2l6.2 11H1.8zM8 6.5v3M8 11.3v.2"),

    /** Failed: a cross in a circle. */
    ERROR("M8 14.5a6.5 6.5 0 1 0 0-13 6.5 6.5 0 0 0 0 13z"
            + "M5.8 5.8l4.4 4.4M10.2 5.8l-4.4 4.4"),

    /** Information: an i in a circle. */
    INFO("M8 14.5a6.5 6.5 0 1 0 0-13 6.5 6.5 0 0 0 0 13zM8 7.2v4.3M8 4.8v.2"),

    /** Timed out: a stopwatch. */
    TIMEOUT("M8 14.5a5.5 5.5 0 1 0 0-11 5.5 5.5 0 0 0 0 11zM8 6v3M6.5 1.5h3"
            + "M12.3 4l1 -1"),

    /** Ungraded: a dash in a circle. */
    UNGRADED("M8 14.5a6.5 6.5 0 1 0 0-13 6.5 6.5 0 0 0 0 13zM5.3 8h5.4"),

    /** Scored zero: an empty set. */
    ZERO("M8 14.5a6.5 6.5 0 1 0 0-13 6.5 6.5 0 0 0 0 13zM4 12l8-8"),

    /** Back: a chevron pointing left. */
    BACK("M10 3L5 8l5 5"),

    /** Light theme: a sun. */
    SUN("M8 10.8a2.8 2.8 0 1 0 0-5.6 2.8 2.8 0 0 0 0 5.6zM8 1.5v1.5M8 13v1.5"
            + "M1.5 8H3M13 8h1.5M3.4 3.4l1 1M11.6 11.6l1 1M3.4 12.6l1-1"
            + "M11.6 4.4l1-1"),

    /** Dark theme: a moon. */
    MOON("M13 9.6A5.5 5.5 0 1 1 6.4 3a4.4 4.4 0 0 0 6.6 6.6z"),

    /** The inspector: a window with a right pane. */
    INSPECTOR("M2.5 3h11v10h-11zM10 3v10"),

    /** The navigation menu: three lines. */
    MENU("M2.5 4.5h11M2.5 8h11M2.5 11.5h11"),

    /** Retry: a circular arrow. */
    RETRY("M13 8a5 5 0 1 1-1.5-3.6M13 2.5v3h-3"),

    /** A folder. */
    FOLDER("M2 4.5h4l1.5 1.5H14v7H2z"),

    /** A file. */
    FILE("M4 1.8h5.5L12 4.3v9.9H4zM9.5 1.8v2.5H12"),

    /** Not connected yet: a plug. */
    PLUG("M6 2v3M10 2v3M4.5 5h7v2.5a3.5 3.5 0 0 1-7 0zM8 11v3"),

    /** Close: a cross. */
    CLOSE("M4 4l8 8M12 4l-8 8"),

    /** Export: an arrow out of a box. */
    EXPORT("M8 9.5V2M5 5l3-3 3 3M4 7.5H3v6.5h10V7.5h-1"),

    /** Undo: an arrow turning back. */
    UNDO("M5.5 3.5L2.5 6.5l3 3M2.5 6.5h7a3.5 3.5 0 0 1 0 7H8"),

    /** Edit: a pencil. */
    EDIT("M10.5 2.5l3 3-8 8H2.5v-3z");

    /** The size of the grid every icon is drawn on. */
    public static final double GRID = 16.0;

    // The SVG path data
    private final String path;

    // Creates an icon constant.
    Icon(String path) {
        this.path = path;
    }

    /**
     * Creates the icon at its normal 16 pixel size.
     *
     * @return a node holding the icon
     */
    public Pane create() {
        return create(GRID);
    }

    /**
     * Creates the icon at a chosen size.
     *
     * @param size the width and height in pixels
     * @return a node holding the icon
     */
    public Pane create(double size) {
        SVGPath shape = new SVGPath();
        shape.setContent(path);
        shape.getStyleClass().add("icon-shape");
        if (size != GRID) {
            double factor = size / GRID;
            shape.getTransforms().add(new Scale(factor, factor, 0, 0));
        }

        Pane box = new Pane(shape);
        box.getStyleClass().add("icon");
        box.setMinSize(size, size);
        box.setPrefSize(size, size);
        box.setMaxSize(size, size);
        box.setMouseTransparent(true);
        return box;
    }
}
