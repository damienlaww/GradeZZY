/*
 * File: Parts.java
 *
 * Purpose: Builds the small pieces every screen of the guided window
 * uses: headings, cards, status pills, buttons, links and icons.
 *
 * Where it fits: Every screen in gradezzy.ui.flow calls these, so the
 * look is set in one place and in flow.css.
 */
package gradezzy.ui.flow;

import java.net.URL;

import javafx.geometry.Bounds;
import javafx.geometry.Insets;
import javafx.geometry.Pos;
import javafx.scene.Group;
import javafx.scene.Node;
import javafx.scene.control.Button;
import javafx.scene.control.Hyperlink;
import javafx.scene.control.Label;
import javafx.scene.control.Control;
import javafx.scene.control.ScrollPane;
import javafx.scene.control.Tooltip;
import javafx.scene.layout.HBox;
import javafx.scene.layout.Priority;
import javafx.scene.layout.Region;
import javafx.scene.layout.StackPane;
import javafx.scene.layout.VBox;
import javafx.scene.shape.Circle;
import javafx.scene.shape.SVGPath;
import javafx.stage.Popup;
import javafx.util.Duration;

/**
 * Factory methods for the shared pieces of the guided window.
 */
public final class Parts {

    /** Upload arrow. */
    public static final String UPLOAD = "M12 15V4M7 9l5-5 5 5"
            + "M4 15v4a1 1 0 0 0 1 1h14a1 1 0 0 0 1-1v-4";

    /** Tick. */
    public static final String CHECK = "M5 12.5l4.5 4.5L19 7.5";

    /** Arrow pointing right. */
    public static final String ARROW = "M5 12h14M13 6l6 6-6 6";

    /** Clock, for History. */
    public static final String CLOCK = "M12 3a9 9 0 1 0 0.01 0Z"
            + "M12 7v5l3 2";

    /** Gear, for Settings. */
    public static final String GEAR = "M12 9a3 3 0 1 0 0.01 0Z"
            + "M12 2v3M12 19v3M4.9 4.9l2.1 2.1M17 17l2.1 2.1"
            + "M2 12h3M19 12h3M4.9 19.1 7 17M17 7l2.1-2.1";

    /** Question mark in a circle, for Help. */
    public static final String HELP = "M12 3a9 9 0 1 0 0.01 0Z"
            + "M9.5 9.5a2.5 2.5 0 0 1 4.9 0.6c0 1.7-2.4 2.2-2.4 3.4"
            + "M12 17h0.01";

    /** A file. */
    public static final String FILE = "M14 3H7a1 1 0 0 0-1 1v16"
            + "a1 1 0 0 0 1 1h10a1 1 0 0 0 1-1V7zM14 3v4h4";

    /** A folder. */
    public static final String FOLDER = "M3 7a1 1 0 0 1 1-1h5l2 2h9"
            + "a1 1 0 0 1 1 1v9a1 1 0 0 1-1 1H4a1 1 0 0 1-1-1z";

    /** Code brackets. */
    public static final String CODE = "M8 8l-4 4 4 4M16 8l4 4-4 4";

    /** A cross, for closing. */
    public static final String CLOSE = "M6 6l12 12M18 6L6 18";

    /** Magnifying glass. */
    public static final String SEARCH = "M11 4.5a6.5 6.5 0 1 0 0.01 0Z"
            + "M20 20l-4.2-4.2";

    /** A tick in a box, for the Marking tab. */
    public static final String MARKING = "M9 11l3 3L21 4"
            + "M20 12v7a1 1 0 0 1-1 1H5a1 1 0 0 1-1-1V5a1 1 0 0 1 1-1h11";

    /** Axes with a curve, for the Statistics tab. */
    public static final String CHART = "M4 4v16h16"
            + "M7 16c2-5 3.5-8 5.5-8s3 4 6.5 8";

    /** The usual gap between parts of a row or card. */
    public static final double GAP = 12.0;

    /** A smaller gap, between an icon and its text. */
    public static final double GAP_SMALL = 8.0;

    /** The gap between cards. */
    public static final double CARD_GAP = 12.0;

    /** The width of the centred column of forms (Set up, Export). */
    public static final double COLUMN_WIDTH = 760.0;

    /** The percent sign, drawn in regular weight next to bold text. */
    public static final String PERCENT_SIGN = "%";

    /** The gap between a figure's value and its unit. */
    public static final double FIGURE_UNIT_GAP = 4.0;

    /** A house: the Home tab. */
    public static final String HOME = "M4 11l8-7 8 7M6 9.5V20h4.5v-5.5h3V20"
            + "H18V9.5";

    /** A chevron pointing right: this row opens. */
    public static final String CHEVRON = "M9 6l6 6-6 6";

    /** A plus sign: add something. */
    public static final String PLUS = "M12 5v14M5 12h14";

    /** The widest a paragraph in a sheet may be. */
    public static final double TEXT_WIDTH = 420.0;

    // The size of the square the icon paths are drawn in
    private static final double ICON_BOX = 24.0;

    // The radius of the dot inside a status pill
    private static final double DOT_RADIUS = 3.5;

    // How long the pointer rests on something before its tip shows
    private static final Duration TIP_DELAY = Duration.millis(300);

    // How long a tip stays while the pointer rests
    private static final Duration TIP_DURATION = Duration.seconds(20);

    // The widest a tip may be before its text wraps
    private static final double TIP_WIDTH = 320.0;

    // The text of the small help mark
    private static final String INFO_MARK = "?";

    // The widest the explanation of a "?" may be before it wraps
    private static final double EXPLAIN_WIDTH = 300.0;

    // The padding around an explanation, on both sides together
    private static final double EXPLAIN_PADDING = 28.0;

    // The gap between a "?" and its explanation
    private static final double EXPLAIN_OFFSET = 6.0;

    // The style class the window's root has in the dark theme
    private static final String DARK_CLASS = "dark";

    // Not used: every method is static.
    private Parts() {
    }

    /**
     * Returns the large heading of a screen.
     *
     * @param text the heading
     * @return the label
     */
    public static Label heading(String text) {
        return styled(new Label(text), "heading");
    }

    /**
     * Returns the one line of help under a heading.
     *
     * @param text the help
     * @return the label
     */
    public static Label subtitle(String text) {
        Label label = styled(new Label(text), "subtitle");
        label.setWrapText(true);
        return label;
    }

    /**
     * Returns a heading and its line of help, stacked.
     *
     * @param heading the heading
     * @param help the line under it
     * @return the box
     */
    public static VBox header(String heading, String help) {
        VBox box = new VBox(heading(heading), subtitle(help));
        box.getStyleClass().add("screen-header");
        return box;
    }

    /**
     * Returns the title of a card or section.
     *
     * @param text the title
     * @return the label
     */
    public static Label title(String text) {
        return styled(new Label(text), "card-title");
    }

    /**
     * Returns grey secondary text.
     *
     * @param text the text
     * @return the label
     */
    public static Label muted(String text) {
        return styled(new Label(text), "muted");
    }

    /**
     * Returns bold body text.
     *
     * @param text the text
     * @return the label
     */
    public static Label strong(String text) {
        return styled(new Label(text), "strong");
    }

    /**
     * Returns a white card with an optional title.
     *
     * @param title the title, or null for none
     * @param content what the card holds
     * @return the card
     */
    public static VBox card(String title, Node[] content) {
        VBox card = new VBox();
        card.getStyleClass().add("flow-card");
        if (title != null) {
            card.getChildren().add(title(title));
        }
        card.getChildren().addAll(content);
        return card;
    }

    /**
     * Returns a status pill: a coloured dot and the status in words, so
     * the status never relies on colour alone.
     *
     * @param text the status in words
     * @param tone the status colour
     * @return the pill
     */
    public static Label pill(String text, Tone tone) {
        Circle dot = new Circle(DOT_RADIUS);
        dot.getStyleClass().add("pill-dot");
        Label pill = new Label(text, dot);
        pill.getStyleClass().addAll("pill", tone.getStyleClass());
        if (text.contains(PERCENT_SIGN)) {
            // JavaFX cuts off the right edge of the bold percent sign
            pill.getStyleClass().add("regular-weight");
        }
        pill.setMinWidth(Region.USE_PREF_SIZE);
        return pill;
    }

    /**
     * Returns the blue pill button that moves to the next step.
     *
     * @param text the button text
     * @return the button
     */
    public static Button primary(String text) {
        Button button = new Button(text);
        button.getStyleClass().addAll("accent", "primary-pill");
        button.setDefaultButton(false);
        button.setMinWidth(Region.USE_PREF_SIZE);
        return button;
    }

    /**
     * Returns a plain secondary button.
     *
     * @param text the button text
     * @param action what it does
     * @return the button
     */
    public static Button secondary(String text, Runnable action) {
        Button button = new Button(text);
        button.getStyleClass().add("secondary");
        button.setMinWidth(Region.USE_PREF_SIZE);
        button.setOnAction(event -> action.run());
        return button;
    }

    /**
     * Returns a small secondary button, for actions inside a card.
     *
     * @param text the button text
     * @param action what it does
     * @return the button
     */
    public static Button small(String text, Runnable action) {
        Button button = secondary(text, action);
        button.getStyleClass().add("small");
        return button;
    }

    /**
     * Returns a small button coloured by what it decides: green for
     * keeping a fix or clearing a flag, red for undoing a fix or keeping
     * a flag.
     *
     * @param text the button text
     * @param action what it does
     * @param tone GOOD or BAD
     * @return the button
     */
    public static Button small(String text, Runnable action, Tone tone) {
        Button button = small(text, action);
        button.getStyleClass().add(tone.getStyleClass());
        return button;
    }

    /**
     * Returns a blue text link.
     *
     * @param text the link text
     * @param action what it does
     * @return the link
     */
    public static Hyperlink link(String text, Runnable action) {
        Hyperlink link = new Hyperlink(text);
        link.getStyleClass().add("flow-link");
        link.setMinWidth(Region.USE_PREF_SIZE);
        link.setOnAction(event -> action.run());
        return link;
    }

    /**
     * Returns an empty region that takes the spare width of a row.
     *
     * @return the region
     */
    public static Region grow() {
        Region region = new Region();
        HBox.setHgrow(region, Priority.ALWAYS);
        VBox.setVgrow(region, Priority.ALWAYS);
        return region;
    }

    /**
     * Returns an empty row, centred vertically, to be filled later.
     *
     * @param spacing the gap between nodes
     * @return the row
     */
    public static HBox row(double spacing) {
        return row(spacing, new Node[0]);
    }

    /**
     * Returns a row of nodes, centred vertically.
     *
     * @param spacing the gap between nodes
     * @param nodes the nodes
     * @return the row
     */
    public static HBox row(double spacing, Node[] nodes) {
        HBox row = new HBox(spacing, nodes);
        row.setAlignment(Pos.CENTER_LEFT);
        return row;
    }

    /**
     * Returns a line icon drawn from a path in a 24 by 24 box.
     *
     * @param path one of the path constants of this class
     * @param size the width and height in pixels
     * @return the icon
     */
    public static Node icon(String path, double size) {
        SVGPath shape = new SVGPath();
        shape.setContent(path);
        shape.getStyleClass().add("icon");
        double scale = size / ICON_BOX;
        shape.setScaleX(scale);
        shape.setScaleY(scale);
        Group group = new Group(shape);
        group.getStyleClass().add("icon-box");
        return group;
    }

    /**
     * Returns a scrolling page with a centred column of fixed width, for
     * forms such as Set up and Export.
     *
     * @param content the header and cards, top to bottom
     * @return the page
     */
    public static ScrollPane narrowPage(Node[] content) {
        VBox column = new VBox(CARD_GAP, content);
        column.getStyleClass().add("narrow-column");
        column.setMaxWidth(COLUMN_WIDTH);
        column.setPrefWidth(COLUMN_WIDTH);
        StackPane centre = new StackPane(column);
        centre.setAlignment(Pos.TOP_CENTER);
        ScrollPane pane = scroll(centre);
        // Centres the column on the window, not on the space left beside
        // the scroll bar, so pages with and without one line up.
        pane.viewportBoundsProperty().addListener((seen, old, bounds) ->
                centreColumn(pane, centre, bounds));
        return pane;
    }

    /**
     * Pads the left of a narrow page by the width of its scroll bar, so
     * the column is centred on the window.
     *
     * @param pane the scrolling page
     * @param centre the box that centres the column
     * @param bounds the new bounds of the visible part of the page
     */
    private static void centreColumn(ScrollPane pane, StackPane centre,
            Bounds bounds) {
        double scrollBarWidth = Math.max(0,
                pane.getWidth() - bounds.getWidth());
        centre.setPadding(new Insets(0, 0, 0, scrollBarWidth));
    }

    /**
     * Returns a scrolling page that uses the full width with even
     * margins, for tables and charts such as Statistics and Runs.
     *
     * @param content the header and cards, top to bottom
     * @return the page
     */
    public static ScrollPane widePage(Node[] content) {
        VBox column = new VBox(CARD_GAP, content);
        column.getStyleClass().add("wide-column");
        return scroll(column);
    }

    /**
     * Returns a figure tile: a small label, a large value with an
     * optional unit, and an optional line under it.
     *
     * @param label the label, such as "Average"
     * @param value the value, such as "12.4"
     * @param unit the unit after the value, such as "/ 20", or null
     * @param hint the line under the value, or null
     * @return the tile
     */
    public static VBox figure(String label, String value, String unit,
            String hint) {
        Label name = styled(new Label(label), "figure-label");
        HBox line = new HBox(FIGURE_UNIT_GAP, figureValue(value));
        line.setAlignment(Pos.BASELINE_LEFT);
        if (unit != null) {
            Label after = styled(new Label(unit), "figure-unit");
            after.setMinWidth(Region.USE_PREF_SIZE);
            line.getChildren().add(after);
        }
        VBox tile = new VBox(name, line);
        tile.getStyleClass().add("figure");
        if (hint != null) {
            tile.getChildren().add(styled(new Label(hint), "figure-hint"));
        }
        HBox.setHgrow(tile, Priority.ALWAYS);
        tile.setMaxWidth(Double.MAX_VALUE);
        return tile;
    }

    /**
     * Returns a thin bar that fills a share of its width, such as a
     * score out of the maximum.
     *
     * @param fraction the share to fill, from 0 to 1
     * @param tone the colour, or null for the accent blue
     * @return the bar
     */
    public static StackPane track(double fraction, Tone tone) {
        double share = Math.max(0, Math.min(1, fraction));
        Region bar = new Region();
        bar.getStyleClass().add("track-fill");
        if (tone != null) {
            bar.getStyleClass().add(tone.getStyleClass());
        }
        StackPane track = new StackPane(bar);
        track.getStyleClass().add("score-track");
        track.setAlignment(Pos.CENTER_LEFT);
        bar.maxWidthProperty().bind(track.widthProperty().multiply(share));
        bar.minWidthProperty().bind(bar.maxWidthProperty());
        HBox.setHgrow(track, Priority.ALWAYS);
        return track;
    }

    /**
     * Returns a count and a word, adding "s" when the count is not one.
     *
     * @param count the count
     * @param word the singular word
     * @return the text, such as "3 files"
     */
    public static String plural(int count, String word) {
        if (count == 1) {
            return count + " " + word;
        }
        return count + " " + word + "s";
    }

    /**
     * Returns a count and a word, with an irregular plural.
     *
     * @param count the count
     * @param word the singular word
     * @param words the plural word
     * @return the text, such as "3 fixes"
     */
    public static String plural(int count, String word, String words) {
        if (count == 1) {
            return count + " " + word;
        }
        return count + " " + words;
    }

    /**
     * Formats a mark without a needless ".0".
     *
     * @param mark the mark
     * @return the text, such as "12" or "2.5"
     */
    public static String mark(double mark) {
        if (mark == Math.rint(mark)) {
            return String.valueOf((long) mark);
        }
        return String.valueOf(mark);
    }

    // Returns the large value of a figure tile. A percent sign is drawn
    // in regular weight beside the bold number, because JavaFX cuts off
    // the right edge of the bold percent sign at small sizes.
    private static HBox figureValue(String value) {
        boolean percent = value.endsWith(PERCENT_SIGN);
        String number = value;
        if (percent) {
            number = value.substring(0, value.length()
                    - PERCENT_SIGN.length());
        }
        Label digits = styled(new Label(number), "figure-value");
        digits.setMinWidth(Region.USE_PREF_SIZE);
        HBox box = new HBox(digits);
        box.setAlignment(Pos.BASELINE_LEFT);
        if (percent) {
            Label sign = styled(new Label(PERCENT_SIGN), "figure-value");
            sign.getStyleClass().add("regular-weight");
            sign.setMinWidth(Region.USE_PREF_SIZE);
            box.getChildren().add(sign);
        }
        return box;
    }

    // Wraps a page in a scroll pane that only scrolls up and down.
    private static ScrollPane scroll(Node content) {
        ScrollPane pane = new ScrollPane(content);
        pane.setFitToWidth(true);
        pane.setHbarPolicy(ScrollPane.ScrollBarPolicy.NEVER);
        pane.getStyleClass().add("page-scroll");
        return pane;
    }

    /**
     * Gives a control a tip that shows soon after the pointer rests on
     * it and wraps long text.
     *
     * @param control the control, such as a button or field
     * @param text what it is for, in one or two sentences
     */
    public static void tip(Control control, String text) {
        control.setTooltip(tooltip(text));
    }

    /**
     * Gives any node, such as a row or a pill, a tip like tip(Control).
     *
     * @param node the node
     * @param text what it is for, in one or two sentences
     */
    public static void tipNode(Node node, String text) {
        Tooltip.install(node, tooltip(text));
    }

    /**
     * Returns a small round "?" button for a word a first-time user may
     * not know. Clicking it, or pressing Space on it, opens a popup that
     * explains the word; a click anywhere else or Esc closes it.
     *
     * @param title the word explained, shown as the popup's title
     * @param text the explanation
     * @return the button
     */
    public static Button info(String title, String text) {
        Button mark = new Button(INFO_MARK);
        mark.getStyleClass().setAll("info-mark");
        mark.setMinWidth(Region.USE_PREF_SIZE);
        mark.setTooltip(tooltip("What is " + title + "? Click to read."));
        mark.setAccessibleText("What is " + title + "?");
        mark.setOnAction(event -> explain(mark, title, text));
        return mark;
    }

    /**
     * Returns a label followed by its "?" button, for a field name.
     *
     * @param text the field name, also the title of the explanation
     * @param help the explanation
     * @return the row
     */
    public static HBox labelWithInfo(Label text, String help) {
        HBox box = row(GAP_SMALL / 2, new Node[] {text,
            info(text.getText(), help)});
        box.setMaxWidth(Region.USE_PREF_SIZE);
        return box;
    }

    /**
     * Returns the box a "?" opens: the word in bold and its explanation.
     * Public so the developer checks can take a picture of it.
     *
     * @param title the word explained
     * @param text the explanation
     * @return the box
     */
    public static VBox explanation(String title, String text) {
        Label heading = new Label(title);
        heading.getStyleClass().add("info-popup-title");
        Label body = new Label(text);
        body.getStyleClass().add("info-popup-text");
        body.setWrapText(true);
        body.setMaxWidth(EXPLAIN_WIDTH);
        body.setMinHeight(Region.USE_PREF_SIZE);
        VBox box = new VBox(GAP_SMALL, heading, body);
        box.getStyleClass().add("info-popup");
        box.setMaxWidth(EXPLAIN_WIDTH + EXPLAIN_PADDING);
        return box;
    }

    // Opens the explanation of a "?" just under it. A "?" that is not
    // in a window yet has nowhere to open it.
    private static void explain(Button mark, String title, String text) {
        if (mark.getScene() == null || mark.getScene().getWindow() == null) {
            return;
        }
        VBox box = explanation(title, text);
        if (mark.getScene().getRoot().getStyleClass().contains(DARK_CLASS)) {
            box.getStyleClass().add(DARK_CLASS);
        }
        Popup popup = new Popup();
        popup.setAutoHide(true);
        popup.setHideOnEscape(true);
        popup.getContent().add(box);
        URL css = Parts.class.getResource(FlowWindow.STYLESHEET);
        if (css != null) {
            popup.getScene().getStylesheets().add(css.toExternalForm());
        }
        Bounds bounds = mark.localToScreen(mark.getBoundsInLocal());
        if (bounds != null) {
            popup.show(mark, bounds.getMinX(), bounds.getMaxY()
                    + EXPLAIN_OFFSET);
        }
    }

    // Builds a tip that shows quickly and wraps.
    private static Tooltip tooltip(String text) {
        Tooltip tip = new Tooltip(text);
        tip.setShowDelay(TIP_DELAY);
        tip.setShowDuration(TIP_DURATION);
        tip.setWrapText(true);
        tip.setMaxWidth(TIP_WIDTH);
        tip.getStyleClass().add("flow-tip");
        return tip;
    }

    // Adds a style class to a label and returns it.
    private static Label styled(Label label, String styleClass) {
        label.getStyleClass().add(styleClass);
        return label;
    }
}
