/*
 * File: GuidedTour.java
 *
 * Purpose: The guided tour. It dims the window, lights up one part at a
 * time inside a pulsing blue border and explains it on a card in the
 * bottom-left corner, with Skip tour, Back and Next. The instructor only
 * reads and presses Next; the tour opens each tab and step by itself.
 *
 * Where it fits: FlowWindow lays it over the whole window. Help and the
 * offer on the first launch start it. It only opens tabs and steps, and
 * goes back to where the instructor was when it ends; it never changes
 * a setting, a file or a mark.
 */
package gradezzy.ui.flow;

import java.io.File;
import java.io.IOException;
import java.util.ArrayList;
import java.util.List;
import java.util.Locale;

import javafx.animation.Animation;
import javafx.animation.FadeTransition;
import javafx.animation.Interpolator;
import javafx.animation.KeyFrame;
import javafx.animation.KeyValue;
import javafx.animation.Timeline;
import javafx.beans.property.DoubleProperty;
import javafx.beans.property.SimpleDoubleProperty;
import javafx.geometry.BoundingBox;
import javafx.geometry.Bounds;
import javafx.scene.Node;
import javafx.scene.Parent;
import javafx.scene.control.Button;
import javafx.scene.control.Hyperlink;
import javafx.scene.control.Label;
import javafx.scene.control.ScrollPane;
import javafx.scene.input.KeyCode;
import javafx.scene.input.KeyEvent;
import javafx.scene.layout.HBox;
import javafx.scene.layout.Pane;
import javafx.scene.layout.Region;
import javafx.scene.layout.VBox;
import javafx.scene.shape.Rectangle;
import javafx.scene.shape.Shape;
import javafx.scene.shape.StrokeType;
import javafx.util.Duration;

import gradezzy.config.ConfigKeys;
import gradezzy.ui.backend.DashboardBackend;

/**
 * The guided tour of the window.
 *
 * <p>Tier: COULD.
 */
public class GuidedTour {

    // The file, next to the database, that says the tour was offered
    private static final String OFFERED_FILE = "tour-offered";

    // The space between the window's edge and the card
    private static final double MARGIN = 24.0;

    // The width of the card
    private static final double CARD_WIDTH = 340.0;

    // The space between the part lit up and the edge of the light
    private static final double HOLE_PADDING = 6.0;

    // The roundness of the light's corners
    private static final double HOLE_ARC = 20.0;

    // The gap between the light and its blue border
    private static final double RING_GAP = 3.0;

    // The width of the blue border
    private static final double RING_WIDTH = 2.0;

    // How far the pulse spreads out from the border
    private static final double PULSE_SPREAD = 14.0;

    // How strong the pulse is when it starts
    private static final double PULSE_OPACITY = 0.55;

    // How long the pulse takes to spread out and fade
    private static final Duration PULSE_FADE = Duration.millis(1100);

    // How long one pulse lasts, with the rest before the next one
    private static final Duration PULSE_TIME = Duration.millis(1600);

    // How long the light takes to move to the next part
    private static final Duration MOVE_TIME = Duration.millis(280);

    // How long the tour takes to fade in
    private static final Duration FADE_TIME = Duration.millis(160);

    // The space kept above a part that is scrolled into view
    private static final double SCROLL_MARGIN = 32.0;

    // The least space between the light and the window's edge, so the
    // blue border stays in view around a part such as the bottom bar
    private static final double EDGE_SPACE = 6.0;

    // The window the tour explains
    private final FlowWindow window;

    // Every stop, in order
    private final List<TourStop> stops;

    // Covers the whole window and holds the dim, the border and the card
    private final Pane overlay = new Pane();

    // The blue border around the light
    private final Rectangle ring = new Rectangle();

    // The pulse that spreads out from the border
    private final Rectangle pulse = new Rectangle();

    // The card in the bottom-left corner
    private final VBox card = new VBox(Parts.GAP_SMALL);

    // The part of the window, above the title
    private final Label area = new Label();

    // The stop number, such as "4 of 15"
    private final Label count = new Label();

    // The title of the stop
    private final Label title = new Label();

    // The sentences under the title
    private final Label text = new Label();

    // Ends the tour at once
    private final Hyperlink skip;

    // Goes to the stop before
    private final Button back;

    // Goes to the next stop, or ends the tour on the last one
    private final Button next;

    // The left edge of the light
    private final DoubleProperty holeX = new SimpleDoubleProperty();

    // The top edge of the light
    private final DoubleProperty holeY = new SimpleDoubleProperty();

    // The width of the light
    private final DoubleProperty holeWidth = new SimpleDoubleProperty();

    // The height of the light
    private final DoubleProperty holeHeight = new SimpleDoubleProperty();

    // Makes the border pulse, again and again
    private final Timeline pulseTimeline = new Timeline();

    // Moves the light to the next part, or null
    private Timeline moveTimeline = null;

    // Fades the tour in when it starts, or null
    private FadeTransition fade = null;

    // The dimmed area with the light cut out of it, or null
    private Shape dim = null;

    // The part lit up now, or null for none
    private Node target = null;

    // The stop on screen
    private int index = 0;

    // True while the tour is on screen
    private boolean open = false;

    // The tab the instructor was on before the tour
    private Page startPage = Page.HOME;

    // The step the instructor was on before the tour
    private Step startStep = Step.SET_UP;

    /**
     * Creates the tour. It shows nothing until start is called.
     *
     * <p>Tier: COULD.
     *
     * @param window the window it explains
     */
    public GuidedTour(FlowWindow window) {
        this.window = window;
        this.stops = createStops();
        skip = Parts.link("Skip tour", () -> end());
        back = Parts.secondary("Back", () -> back());
        next = Parts.secondary("Next", () -> next());
        buildCard();
        buildLight();

        overlay.getStyleClass().add("tour");
        overlay.setPickOnBounds(true);
        // Keeps the card's shadow inside the window
        Rectangle edges = new Rectangle();
        edges.widthProperty().bind(overlay.widthProperty());
        edges.heightProperty().bind(overlay.heightProperty());
        overlay.setClip(edges);
        overlay.getChildren().addAll(ring, pulse, card);
        overlay.widthProperty().addListener((observable, before,
                after) -> placeAgain());
        overlay.heightProperty().addListener((observable, before,
                after) -> placeAgain());
        holeX.addListener((observable, before, after) -> drawDim());
        holeY.addListener((observable, before, after) -> drawDim());
        holeWidth.addListener((observable, before, after) -> drawDim());
        holeHeight.addListener((observable, before, after) -> drawDim());
    }

    /**
     * Starts the tour at its first stop, on Home. Does nothing while it
     * is already on screen.
     *
     * <p>Tier: COULD.
     */
    public void start() {
        if (open) {
            return;
        }
        startPage = window.getCurrentPage();
        startStep = window.getCurrentStep();
        open = true;
        index = 0;
        target = null;
        window.showOverlay(overlay);
        overlay.setOpacity(0.0);
        fade = new FadeTransition(FADE_TIME, overlay);
        fade.setToValue(1.0);
        fade.play();
        pulseTimeline.play();
        showStop();
    }

    /**
     * Goes to the next stop, or ends the tour after the last one.
     *
     * <p>Tier: COULD.
     */
    public void next() {
        if (!open) {
            return;
        }
        if (index == stops.size() - 1) {
            end();
            return;
        }
        index++;
        showStop();
    }

    /**
     * Goes to the stop before, if there is one.
     *
     * <p>Tier: COULD.
     */
    public void back() {
        if (!open || index == 0) {
            return;
        }
        index--;
        showStop();
    }

    /**
     * Ends the tour and goes back to the tab and step the instructor was
     * on before it started.
     *
     * <p>Tier: COULD.
     */
    public void end() {
        if (!open) {
            return;
        }
        open = false;
        pulseTimeline.stop();
        if (moveTimeline != null) {
            moveTimeline.stop();
        }
        window.hideOverlay(overlay);
        if (startPage == Page.MARKING) {
            window.show(startStep);
        } else {
            window.showPage(startPage);
        }
    }

    /**
     * Acts on a key while the tour is on screen: Esc ends it, Right and
     * Enter go on, Left goes back. Space presses the focused button.
     * Every other key is held back, so nothing behind the tour changes.
     *
     * <p>Tier: COULD.
     *
     * @param event the key press; consumed unless it is Space
     */
    public void handleKey(KeyEvent event) {
        KeyCode code = event.getCode();
        if (code == KeyCode.SPACE) {
            return;
        }
        event.consume();
        if (code == KeyCode.ESCAPE) {
            end();
        } else if (code == KeyCode.RIGHT || code == KeyCode.ENTER) {
            next();
        } else if (code == KeyCode.LEFT) {
            back();
        }
    }

    /**
     * Says whether the tour is on screen.
     *
     * <p>Tier: COULD.
     *
     * @return true while it is on screen
     */
    public boolean isOpen() {
        return open;
    }

    /**
     * Returns the number of the stop on screen, counting from 0, for the
     * developer checks.
     *
     * @return the stop's index
     */
    public int getIndex() {
        return index;
    }

    /**
     * Returns how many stops the tour has, for the developer checks.
     *
     * @return the number of stops
     */
    public int getStopCount() {
        return stops.size();
    }

    /**
     * Puts the tour where its fade and slide end, for the developer
     * checks, whose pictures are taken before any animation runs.
     */
    public void finishMoving() {
        if (!open) {
            return;
        }
        if (fade != null) {
            fade.stop();
        }
        overlay.setOpacity(1.0);
        placeAgain();
    }

    /**
     * Returns the part of the window lit up now, for the developer
     * checks.
     *
     * @return the part, or null when nothing is lit up
     */
    public Node getTarget() {
        return target;
    }

    /**
     * Notes that the tour was offered, in a small file next to the
     * database, and says whether this was the first time. Offering it
     * again after clean.sh empties the workspace is expected.
     *
     * <p>Tier: COULD.
     *
     * @param backend the backend whose settings name the database file
     * @return true the first time only; false also when the note cannot
     *         be saved, so the offer does not come back on every launch
     */
    public static boolean firstOffer(DashboardBackend backend) {
        String database = backend.getSetting(ConfigKeys.DATABASE_FILE);
        if (database == null || database.trim().isEmpty()) {
            return false;
        }
        File folder = new File(database.trim()).getAbsoluteFile()
                .getParentFile();
        File note = new File(folder, OFFERED_FILE);
        if (note.exists()) {
            return false;
        }
        try {
            folder.mkdirs();
            return note.createNewFile();
        } catch (IOException e) {
            return false;
        }
    }

    // Lists every stop, in the order the tour shows them.
    private static List<TourStop> createStops() {
        List<TourStop> list = new ArrayList<>();
        list.add(new TourStop(Page.HOME, null, "Welcome",
                "Welcome to GradeZZY",
                "This tour shows what each part of the window does. It "
                + "takes about a minute. Press Next or the right arrow "
                + "key to go on, and Esc to stop at any time."));
        list.add(new TourStop(Page.HOME, ".tab-bar", "Tabs",
                "Five tabs",
                "Home, Grading, Statistics, History and Settings. Switch "
                + "tabs whenever you like: grading carries on while you "
                + "look elsewhere."));
        list.add(new TourStop(Page.HOME, ".home-context-row", "Home",
                "Choose what you are marking",
                "Pick the module, the section and the assessment. Manage "
                + "assessments adds or edits the questions and their "
                + "marks."));
        list.add(new TourStop(Page.HOME, ".section-card", "Home",
                "One card per section",
                "Each card shows how far its section has got and its "
                + "average mark. Click a card to carry on, or drop zip "
                + "files on it to start."));
        list.add(new TourStop(Step.SET_UP, ".step-bar", "Four steps",
                "Grading goes Set up, Grade, Review, Export. A step opens "
                + "once you reach it, and you can go back to any step "
                + "you have reached.", null));
        list.add(new TourStop(Step.SET_UP, ".flow-card",
                "The class",
                "Choose the module, the section and the assessment for "
                + "this batch. If the section was graded before, a link "
                + "opens those results.", null));
        list.add(new TourStop(Step.SET_UP, ".drop-area",
                "Add the students' zip files",
                "Drop the zip files, or a whole folder, here. Each file "
                + "gets a check: Ready means it can be graded, and a note "
                + "says what is wrong with the others.", null));
        list.add(new TourStop(Step.SET_UP, ".action-bar",
                "The bar at the bottom",
                "It says what is ready and holds the blue button for the "
                + "next step, such as Start grading. "
                + Parts.shortcut("Enter") + " presses it too.", null));
        list.add(new TourStop(Step.GRADE, ".score-track",
                "Watch the batch being graded",
                "Each submission is checked, compiled and run against "
                + "the tester files. The bar shows how far it has got, "
                + "and a stopped batch can be resumed from Home.",
                "This step opens when you start grading."));
        list.add(new TourStop(Step.REVIEW, ".table-frame",
                "Check the marks",
                "One row per student with the mark for each question. "
                + "Click a student to see the tests, keep or undo fixes, "
                + "check flags and change a mark.",
                "This step opens when a batch is graded, or from a "
                + "section's card on Home."));
        list.add(new TourStop(Step.EXPORT, ".export-choices",
                "Save the results",
                "Saves the score sheet in the format it came in, with "
                + "the marks filled in. Tick the extras you want, such "
                + "as a JSON file with each question's marks.",
                "This step opens after Review."));
        list.add(new TourStop(Page.STATISTICS, ".stats-header",
                "Statistics", "How a section did",
                "The average, the spread and the pass rate of a section, "
                + "and how each question went. Compare up to three "
                + "sections side by side."));
        list.add(new TourStop(Page.RUNS, ".runs-switch", "History",
                "Earlier runs and backups",
                "Every earlier run, the backups of the saved results, "
                + "and a log of what was done. Open a run to review it "
                + "again."));
        list.add(new TourStop(Page.SETTINGS, ".settings-header",
                "Settings", "Every setting in one place",
                "Folders, time limits and everything else GradeZZY "
                + "uses. Search for a setting, change it, then press "
                + "Save."));
        list.add(new TourStop(Page.HOME, ".top-link", "Help",
                "Help is here",
                "Help explains each tab and the keyboard shortcuts. "
                + "Take the tour from there to see this again."));
        return list;
    }

    // Builds the card: the area and the count, the title, the text and
    // the buttons.
    private void buildCard() {
        area.getStyleClass().add("tour-area");
        count.getStyleClass().add("tour-count");
        title.getStyleClass().add("tour-title");
        title.setWrapText(true);
        title.setMinHeight(Region.USE_PREF_SIZE);
        text.getStyleClass().add("tour-text");
        text.setWrapText(true);
        text.setMinHeight(Region.USE_PREF_SIZE);
        skip.getStyleClass().add("tour-skip");
        next.getStyleClass().add("confirm-ok");

        HBox top = Parts.row(Parts.GAP, new Node[] {area, Parts.grow(),
            count});
        HBox buttons = Parts.row(Parts.GAP_SMALL, new Node[] {skip,
            Parts.grow(), back, next});
        card.getChildren().addAll(top, title, text, buttons);
        card.getStyleClass().add("tour-card");
        card.setMinWidth(CARD_WIDTH);
        card.setPrefWidth(CARD_WIDTH);
        card.setMaxWidth(CARD_WIDTH);
    }

    // Sets up the blue border and its pulse.
    private void buildLight() {
        ring.getStyleClass().add("tour-ring");
        ring.setStrokeType(StrokeType.OUTSIDE);
        ring.setStrokeWidth(RING_WIDTH);
        ring.setMouseTransparent(true);
        pulse.getStyleClass().add("tour-ring");
        pulse.setStrokeType(StrokeType.OUTSIDE);
        pulse.setMouseTransparent(true);

        KeyFrame begin = new KeyFrame(Duration.ZERO, new KeyValue[] {
            new KeyValue(pulse.strokeWidthProperty(), RING_WIDTH),
            new KeyValue(pulse.opacityProperty(), PULSE_OPACITY)});
        KeyFrame spread = new KeyFrame(PULSE_FADE, new KeyValue[] {
            new KeyValue(pulse.strokeWidthProperty(), PULSE_SPREAD),
            new KeyValue(pulse.opacityProperty(), 0.0)});
        KeyFrame rest = new KeyFrame(PULSE_TIME, new KeyValue[] {
            new KeyValue(pulse.strokeWidthProperty(), PULSE_SPREAD),
            new KeyValue(pulse.opacityProperty(), 0.0)});
        pulseTimeline.getKeyFrames().addAll(begin, spread, rest);
        pulseTimeline.setCycleCount(Animation.INDEFINITE);
    }

    // Opens the stop's tab or step, fills the card and moves the light.
    private void showStop() {
        TourStop stop = stops.get(index);
        boolean locked = openPlace(stop);
        area.setText(stop.getArea().toUpperCase(Locale.ROOT));
        count.setText((index + 1) + " of " + stops.size());
        title.setText(stop.getTitle());
        text.setText(stop.getText(locked));
        back.setDisable(index == 0);
        boolean last = index == stops.size() - 1;
        skip.setVisible(!last);
        if (last) {
            next.setText("Done");
        } else {
            next.setText("Next");
        }

        layoutNow();
        Node found = findTarget(stop, locked);
        if (found != null) {
            scrollIntoView(found);
            layoutNow();
        }
        moveLight(found, true);
        next.requestFocus();
    }

    // Opens the tab or step of a stop, unless it is on screen already.
    // A step that cannot be opened yet is pointed at in the row of
    // steps instead; true is returned then.
    private boolean openPlace(TourStop stop) {
        Step step = stop.getStep();
        if (step == null) {
            if (window.getCurrentPage() != stop.getPage()) {
                window.showPage(stop.getPage());
            }
            return false;
        }
        Step furthest = window.getState().getFurthest();
        boolean locked = step.ordinal() > furthest.ordinal();
        if (locked) {
            if (window.getCurrentPage() != Page.MARKING) {
                window.showPage(Page.MARKING);
            }
            return true;
        }
        if (window.getCurrentPage() != Page.MARKING
                || window.getCurrentStep() != step) {
            window.show(step);
        }
        return false;
    }

    // Lays out the whole window now, so the parts have their sizes.
    private void layoutNow() {
        if (overlay.getScene() == null) {
            return;
        }
        Parent root = overlay.getScene().getRoot();
        root.applyCss();
        root.layout();
    }

    // Finds the part a stop lights up, or null when it is not on screen.
    private Node findTarget(TourStop stop, boolean locked) {
        String wanted = stop.getTarget();
        Node found = null;
        if (locked || TourStop.STEP.equals(wanted)) {
            found = stepItem(stop.getStep());
        } else if (TourStop.TAB.equals(wanted)) {
            found = window.getTabBar().getTab(stop.getPage());
        } else if (wanted != null) {
            found = window.getRoot().lookup(wanted);
        }
        if (found == null || !isShown(found)) {
            return null;
        }
        return found;
    }

    // Returns a step's dot and name in the row of steps, or null.
    private Node stepItem(Step step) {
        int position = 0;
        for (Node item : window.getRoot().lookupAll(".step-item")) {
            if (position == step.ordinal()) {
                return item;
            }
            position++;
        }
        return null;
    }

    // Says whether a part and everything around it is visible and has
    // a size.
    private static boolean isShown(Node node) {
        Bounds box = node.getBoundsInLocal();
        if (box.getWidth() <= 0 || box.getHeight() <= 0) {
            return false;
        }
        Node part = node;
        while (part != null) {
            if (!part.isVisible()) {
                return false;
            }
            part = part.getParent();
        }
        return true;
    }

    // Scrolls the page holding a part so that the part's top is in
    // view.
    private static void scrollIntoView(Node node) {
        ScrollPane scroll = scrollPaneOf(node);
        if (scroll == null || scroll.getContent() == null) {
            return;
        }
        Node content = scroll.getContent();
        Bounds box = content.sceneToLocal(node.localToScene(
                node.getBoundsInLocal()));
        double view = scroll.getViewportBounds().getHeight();
        double extra = content.getBoundsInLocal().getHeight() - view;
        if (extra <= 0) {
            return;
        }
        double top = scroll.getVvalue() * extra;
        if (box.getMinY() >= top && box.getMaxY() <= top + view) {
            return;
        }
        double wanted = (box.getMinY() - SCROLL_MARGIN) / extra;
        scroll.setVvalue(Math.max(0.0, Math.min(1.0, wanted)));
    }

    // Returns the scroll pane a part sits in, or null.
    private static ScrollPane scrollPaneOf(Node node) {
        Node part = node.getParent();
        while (part != null) {
            if (part instanceof ScrollPane) {
                return (ScrollPane) part;
            }
            part = part.getParent();
        }
        return null;
    }

    // Returns where a part is on the tour, cut to the page it scrolls
    // in and to the window.
    private Bounds visibleBox(Node node) {
        Bounds box = node.localToScene(node.getBoundsInLocal());
        double left = box.getMinX();
        double top = box.getMinY();
        double right = box.getMaxX();
        double bottom = box.getMaxY();
        ScrollPane scroll = scrollPaneOf(node);
        if (scroll != null) {
            Bounds view = scroll.localToScene(scroll.getBoundsInLocal());
            left = Math.max(left, view.getMinX());
            top = Math.max(top, view.getMinY());
            right = Math.min(right, view.getMaxX());
            bottom = Math.min(bottom, view.getMaxY());
        }
        Bounds local = overlay.sceneToLocal(new BoundingBox(left, top,
                Math.max(0.0, right - left), Math.max(0.0, bottom - top)));
        double minX = Math.max(local.getMinX(), 0.0);
        double minY = Math.max(local.getMinY(), 0.0);
        double maxX = Math.min(local.getMaxX(), overlay.getWidth());
        double maxY = Math.min(local.getMaxY(), overlay.getHeight());
        return new BoundingBox(minX, minY, Math.max(0.0, maxX - minX),
                Math.max(0.0, maxY - minY));
    }

    // Moves the light to a part, sliding when asked, or dims the whole
    // window when there is no part.
    private void moveLight(Node found, boolean slide) {
        if (moveTimeline != null) {
            moveTimeline.stop();
        }
        boolean wasLit = target != null;
        target = found;
        if (found == null) {
            ring.setVisible(false);
            pulse.setVisible(false);
            drawDim();
            placeCard(null);
            return;
        }
        Bounds box = visibleBox(found);
        double holeLeft = Math.max(EDGE_SPACE, box.getMinX() - HOLE_PADDING);
        double holeTop = Math.max(EDGE_SPACE, box.getMinY() - HOLE_PADDING);
        double right = Math.min(overlay.getWidth() - EDGE_SPACE,
                box.getMaxX() + HOLE_PADDING);
        double bottom = Math.min(overlay.getHeight() - EDGE_SPACE,
                box.getMaxY() + HOLE_PADDING);
        double width = Math.max(0.0, right - holeLeft);
        double height = Math.max(0.0, bottom - holeTop);
        ring.setVisible(true);
        pulse.setVisible(true);
        if (!slide) {
            setHole(holeLeft, holeTop, width, height);
        } else {
            if (!wasLit) {
                // Grow out of the part's middle
                setHole(holeLeft + width / 2, holeTop + height / 2, 0.0, 0.0);
            }
            KeyFrame end = new KeyFrame(MOVE_TIME, new KeyValue[] {
                new KeyValue(holeX, holeLeft, Interpolator.EASE_BOTH),
                new KeyValue(holeY, holeTop, Interpolator.EASE_BOTH),
                new KeyValue(holeWidth, width, Interpolator.EASE_BOTH),
                new KeyValue(holeHeight, height, Interpolator.EASE_BOTH)});
            moveTimeline = new Timeline(new KeyFrame[] {end});
            moveTimeline.play();
        }
        placeCard(new BoundingBox(holeLeft, holeTop, width, height));
    }

    // Puts the light at once where it is given.
    private void setHole(double left, double top, double width, double height) {
        holeX.set(left);
        holeY.set(top);
        holeWidth.set(width);
        holeHeight.set(height);
        drawDim();
    }

    // Draws the dimmed area again, with the light cut out of it, and
    // moves the border and the pulse around the light.
    private void drawDim() {
        Rectangle all = new Rectangle(overlay.getWidth(),
                overlay.getHeight());
        Shape shape = all;
        if (target != null) {
            Rectangle hole = new Rectangle(holeX.get(), holeY.get(),
                    holeWidth.get(), holeHeight.get());
            hole.setArcWidth(HOLE_ARC);
            hole.setArcHeight(HOLE_ARC);
            shape = Shape.subtract(all, hole);
        }
        shape.getStyleClass().add("tour-dim");
        if (dim != null) {
            overlay.getChildren().remove(dim);
        }
        dim = shape;
        overlay.getChildren().add(0, dim);
        placeRing(ring);
        placeRing(pulse);
    }

    // Puts a border just outside the light.
    private void placeRing(Rectangle border) {
        border.setX(holeX.get() - RING_GAP);
        border.setY(holeY.get() - RING_GAP);
        border.setWidth(holeWidth.get() + 2 * RING_GAP);
        border.setHeight(holeHeight.get() + 2 * RING_GAP);
        border.setArcWidth(HOLE_ARC + 2 * RING_GAP);
        border.setArcHeight(HOLE_ARC + 2 * RING_GAP);
    }

    // Puts the card in the bottom-left corner. When the light is there,
    // the card goes to the bottom-right corner, and when the light
    // covers both, above the light.
    private void placeCard(Bounds light) {
        double height = card.prefHeight(CARD_WIDTH);
        double left = MARGIN;
        double right = overlay.getWidth() - CARD_WIDTH - MARGIN;
        double bottom = overlay.getHeight() - height - MARGIN;
        double cardLeft = left;
        double cardTop = bottom;
        if (light != null && light.intersects(left, bottom, CARD_WIDTH,
                height)) {
            cardLeft = right;
            if (light.intersects(right, bottom, CARD_WIDTH, height)) {
                cardLeft = left;
                cardTop = Math.max(MARGIN, light.getMinY() - height - MARGIN);
            }
        }
        card.resize(CARD_WIDTH, height);
        card.relocate(cardLeft, cardTop);
    }

    // Puts the light and the card in place again after the window
    // changed size.
    private void placeAgain() {
        if (!open) {
            return;
        }
        Node found = target;
        if (found != null && (found.getScene() == null
                || !isShown(found))) {
            found = null;
        }
        moveLight(found, false);
    }
}
