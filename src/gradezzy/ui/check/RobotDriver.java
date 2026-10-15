/*
 * File: RobotDriver.java
 *
 * Purpose: Drives the real window with operating-system mouse and
 * keyboard events, finds controls by their words, waits for the
 * interface, and takes pictures of the real screen.
 *
 * Where it fits: Used by WindowTest. The test runs on its own thread so
 * the window keeps working; every call that touches the window or the
 * robot is handed to the JavaFX thread and waited for.
 *
 * Note on untaught Java: javafx.scene.robot.Robot sends real mouse and
 * keyboard input, and FutureTask waits for work done on another thread.
 *
 * AI use: drafted with Claude Code, reviewed by Jun Sheng.
 */
package gradezzy.ui.check;

import java.awt.AWTException;
import java.io.IOException;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.concurrent.Callable;
import java.util.concurrent.ExecutionException;
import java.util.concurrent.FutureTask;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.TimeoutException;

import javafx.application.Platform;
import javafx.event.EventHandler;
import javafx.geometry.Bounds;
import javafx.scene.Node;
import javafx.scene.Parent;
import javafx.scene.Scene;
import javafx.scene.control.Labeled;
import javafx.scene.control.ScrollPane;
import javafx.scene.image.Image;
import javafx.scene.image.PixelReader;
import javafx.scene.input.KeyCode;
import javafx.scene.input.MouseEvent;
import javafx.scene.input.MouseButton;
import javafx.scene.paint.Color;
import javafx.scene.robot.Robot;
import javafx.stage.PopupWindow;
import javafx.stage.Stage;
import javafx.stage.Window;

/**
 * Real mouse and keyboard input for the window test.
 *
 * <p>Tier: SHOULD. Owner: Damien.
 */
public final class RobotDriver {

    // How long one call on the JavaFX thread may take
    private static final long CALL_TIMEOUT_SECONDS = 20L;

    // How often a condition is checked while waiting
    private static final long POLL_MILLIS = 100L;

    // The pause after a click or a key, so the window can react
    private static final long SETTLE_MILLIS = 350L;

    // The width of the band around a control searched for its focus ring
    private static final double RING_BAND = 4.0;

    // The largest colour distance that still counts as the ring colour
    private static final double RING_TOLERANCE = 0.16;

    // The fewest ring-coloured pixels that count as a visible ring
    private static final int RING_MIN_PIXELS = 12;

    // The space left above a control scrolled into view
    private static final double REVEAL_MARGIN = 24.0;

    // The lines of the JavaFX thread's stack printed when it hangs
    private static final int STACK_LINES = 40;

    // How long to wait when something covers a control, such as a
    // message that fades after three seconds
    private static final long COVER_WAIT_MILLIS = 3500L;

    /**
     * Sends the mouse events of one kind of click.
     */
    private interface Clicker {

        /**
         * Sends the events with the pointer already over the control.
         * Runs on the JavaFX thread.
         */
        void send();
    }

    /**
     * A check run on the JavaFX thread.
     */
    public interface Condition {

        /**
         * Says whether the window has reached the wanted state.
         *
         * @return true when the state is reached
         */
        boolean holds();
    }

    // The robot, created on the JavaFX thread
    private Robot robot = null;

    // What received the last mouse press, while a click is checked
    private Object pressed = null;

    // Whether the last press reached the control, decided at the press,
    // before the click can rebuild what was pressed
    private boolean pressedInside = false;

    // The modifier of shortcuts: Command on macOS, Control elsewhere
    private final KeyCode shortcutKey;

    /**
     * Creates the driver.
     */
    public RobotDriver() {
        String system = System.getProperty("os.name", "").toLowerCase();
        if (system.contains("mac")) {
            shortcutKey = KeyCode.COMMAND;
        } else {
            shortcutKey = KeyCode.CONTROL;
        }
    }

    /**
     * Runs work on the JavaFX thread and waits for its result.
     *
     * @param work the work
     * @param <T> the type of the result
     * @return the result
     */
    public <T> T onFx(Callable<T> work) {
        if (Platform.isFxApplicationThread()) {
            try {
                return work.call();
            } catch (Exception exception) {
                throw new IllegalStateException(exception);
            }
        }
        FutureTask<T> task = new FutureTask<>(work);
        Platform.runLater(task);
        try {
            return task.get(CALL_TIMEOUT_SECONDS, TimeUnit.SECONDS);
        } catch (InterruptedException exception) {
            Thread.currentThread().interrupt();
            throw new IllegalStateException("Interrupted", exception);
        } catch (ExecutionException exception) {
            throw new IllegalStateException(exception.getCause());
        } catch (TimeoutException exception) {
            printFxThread();
            throw new IllegalStateException("The window did not answer.",
                    exception);
        }
    }

    /**
     * Runs work on the JavaFX thread and waits until it is done.
     *
     * @param work the work
     */
    public void run(Runnable work) {
        onFx(() -> {
            work.run();
            return Boolean.TRUE;
        });
    }

    /**
     * Waits until a condition holds, checking it on the JavaFX thread.
     *
     * @param condition the condition
     * @param timeoutMillis the longest wait
     * @return true when the condition held in time
     */
    public boolean waitUntil(Condition condition, long timeoutMillis) {
        long deadline = System.currentTimeMillis() + timeoutMillis;
        while (System.currentTimeMillis() < deadline) {
            if (onFx(() -> condition.holds())) {
                return true;
            }
            pause(POLL_MILLIS);
        }
        return onFx(() -> condition.holds());
    }

    /**
     * Waits for a while without blocking the window.
     *
     * @param millis the time to wait
     */
    public void pause(long millis) {
        try {
            Thread.sleep(millis);
        } catch (InterruptedException exception) {
            Thread.currentThread().interrupt();
        }
    }

    /**
     * Clicks the middle of a control with the left mouse button. When
     * something else receives the press, such as a message covering the
     * control, it waits as a person would and clicks once more.
     *
     * @param node the control
     * @return true when the control received the click
     */
    public boolean click(Node node) {
        return clickChecked(node, () -> robot().mouseClick(
                MouseButton.PRIMARY));
    }

    /**
     * Double-clicks the middle of a control.
     *
     * @param node the control
     * @return true when the control received the clicks
     */
    public boolean doubleClick(Node node) {
        return clickChecked(node, () -> {
            robot().mouseClick(MouseButton.PRIMARY);
            robot().mouseClick(MouseButton.PRIMARY);
        });
    }

    /**
     * Clicks a control while holding the shortcut key, to add it to a
     * selection.
     *
     * @param node the control
     * @return true when the control received the click
     */
    public boolean shortcutClick(Node node) {
        return clickChecked(node, () -> {
            robot().keyPress(shortcutKey);
            robot().mouseClick(MouseButton.PRIMARY);
            robot().keyRelease(shortcutKey);
        });
    }

    /**
     * Moves the mouse pointer to the middle of a control.
     *
     * @param node the control
     */
    public void moveTo(Node node) {
        boolean scrolled = onFx(() -> reveal(node));
        if (scrolled) {
            pause(SETTLE_MILLIS);
        }
        double[] point = onFx(() -> centre(node));
        if (point == null) {
            throw new IllegalStateException(name(node) + " is not on the "
                    + "screen");
        }
        run(() -> robot().mouseMove(point[0], point[1]));
    }

    /**
     * Drags from the middle of one control to the middle of another in
     * small steps, as a hand would, with the operating system's robot.
     * See SystemMouse for why.
     *
     * @param from the control pressed
     * @param to the control released on
     * @throws AWTException if the system robot is not available
     */
    public void dragBetweenWindows(Node from, Node to) throws AWTException {
        double[] start = onFx(() -> centre(from));
        double[] end = onFx(() -> centre(to));
        new SystemMouse().drag(start, end);
        pause(SETTLE_MILLIS);
    }

    /**
     * Turns the mouse wheel over a control, as a person scrolls a list.
     *
     * @param node the control
     * @param notches the notches turned; positive scrolls down
     */
    public void wheel(Node node, int notches) {
        moveTo(node);
        run(() -> robot().mouseWheel(notches));
        pause(SETTLE_MILLIS);
    }

    /**
     * Presses and releases keys together, such as Control and 1.
     *
     * @param keys the keys, modifiers first
     */
    public void press(KeyCode... keys) {
        run(() -> sendKeys(keys));
        pause(SETTLE_MILLIS);
    }

    /**
     * Presses the shortcut key with another key, such as Ctrl+F on
     * Windows or Cmd+F on macOS.
     *
     * @param key the other key
     */
    public void shortcut(KeyCode key) {
        press(shortcutKey, key);
    }

    /**
     * Types text made of letters, digits, spaces and full stops.
     *
     * @param text the text
     */
    public void type(String text) {
        for (char letter : text.toCharArray()) {
            run(() -> sendLetter(letter));
        }
        pause(SETTLE_MILLIS);
    }

    /**
     * Finds a visible control whose words match, under a node.
     *
     * @param root where to look
     * @param text the words on the control
     * @return the control, or null when none is on show
     */
    public Labeled find(Parent root, String text) {
        return findWithData(root, text, null);
    }

    /**
     * Finds a visible control by its words and its user data, for
     * buttons that share words, such as several Choose Folder buttons.
     *
     * @param root where to look
     * @param text the words on the control
     * @param data the user data set in FXML, or null to ignore it
     * @return the control, or null when none is on show
     */
    public Labeled findWithData(Parent root, String text, Object data) {
        return onFx(() -> searchLabeled(root, text, data));
    }

    /**
     * Finds every visible node with a style class under a node.
     *
     * @param root where to look
     * @param styleClass the style class, without the dot
     * @return the nodes on show
     */
    public List<Node> findAll(Parent root, String styleClass) {
        return onFx(() -> searchStyled(root, styleClass));
    }

    /**
     * Finds a visible control by its words in any pop-up window on show,
     * such as a menu, the list of a combo box, or a tooltip.
     *
     * @param text the words on the control
     * @return the control, or null when no pop-up shows it
     */
    public Labeled findInPopups(String text) {
        return onFx(() -> searchPopups(text));
    }

    /**
     * Says whether the screen shows the focus ring around a control, by
     * taking one picture of the real screen around it and counting the
     * ring-coloured pixels along its top and bottom edges.
     *
     * @param node the focused control
     * @param ring the ring colour of the theme
     * @param edge the inner edge colour of the theme
     * @return true when enough pixels in the band have either colour
     */
    public boolean showsRing(Node node, Color ring, Color edge) {
        return onFx(() -> countRingPixels(node, ring, edge)
                >= RING_MIN_PIXELS);
    }

    /**
     * Saves a picture of part of the real screen.
     *
     * @param bounds the part of the screen
     * @param file the PNG file to write
     * @throws IOException if the file cannot be written
     */
    public void capture(Bounds bounds, Path file) throws IOException {
        PngWriter.write(onFx(() -> robot().getScreenCapture(null,
                bounds.getMinX(), bounds.getMinY(), bounds.getWidth(),
                bounds.getHeight())), file);
    }

    // Presses keys in order, then releases them in reverse order.
    private void sendKeys(KeyCode[] keys) {
        for (KeyCode key : keys) {
            robot().keyPress(key);
        }
        for (int index = keys.length - 1; index >= 0; index--) {
            robot().keyRelease(keys[index]);
        }
    }

    // Types one character, holding Shift for a capital letter.
    private void sendLetter(char letter) {
        boolean upper = Character.isUpperCase(letter);
        if (upper) {
            robot().keyPress(KeyCode.SHIFT);
        }
        robot().keyType(keyFor(letter));
        if (upper) {
            robot().keyRelease(KeyCode.SHIFT);
        }
    }

    // Returns the first visible control with some words and user data.
    private Labeled searchLabeled(Parent root, String text, Object data) {
        List<Node> nodes = new ArrayList<>();
        collect(root, nodes);
        for (Node node : nodes) {
            boolean sameData = data == null
                    || data.equals(node.getUserData());
            if (node instanceof Labeled && isShown(node) && sameData
                    && text.equals(((Labeled) node).getText())) {
                return (Labeled) node;
            }
        }
        return null;
    }

    // Returns every visible node with a style class.
    private List<Node> searchStyled(Parent root, String styleClass) {
        List<Node> found = new ArrayList<>();
        for (Node node : root.lookupAll("." + styleClass)) {
            if (isShown(node)) {
                found.add(node);
            }
        }
        return found;
    }

    // Returns the first control with some words in a pop-up on show.
    private Labeled searchPopups(String text) {
        for (Window window : Window.getWindows()) {
            boolean popup = window instanceof PopupWindow
                    && window.isShowing() && window.getScene() != null;
            if (popup) {
                Labeled found = searchLabeled(window.getScene().getRoot(),
                        text, null);
                if (found != null) {
                    return found;
                }
            }
        }
        return null;
    }

    // Counts the ring-coloured pixels along the top and bottom edges of
    // a control in one picture of the real screen.
    private int countRingPixels(Node node, Color ring, Color edge) {
        Bounds bounds = node.localToScreen(node.getBoundsInLocal());
        Image picture = robot().getScreenCapture(null,
                bounds.getMinX() - RING_BAND, bounds.getMinY() - RING_BAND,
                bounds.getWidth() + RING_BAND * 2,
                bounds.getHeight() + RING_BAND * 2);
        PixelReader pixels = picture.getPixelReader();
        int across = (int) picture.getWidth();
        int down = (int) picture.getHeight();
        int band = (int) Math.ceil(RING_BAND * 2);
        int matches = 0;
        for (int x = 0; x < across; x++) {
            for (int y = 0; y < down; y++) {
                boolean nearEdge = y < band || y >= down - band;
                Color pixel = pixels.getColor(x, y);
                if (nearEdge && (close(pixel, ring) || close(pixel, edge))) {
                    matches++;
                }
            }
        }
        return matches;
    }

    // Says whether two colours are nearly the same.
    private boolean close(Color first, Color second) {
        double red = first.getRed() - second.getRed();
        double green = first.getGreen() - second.getGreen();
        double blue = first.getBlue() - second.getBlue();
        return Math.sqrt(red * red + green * green + blue * blue)
                < RING_TOLERANCE;
    }

    // Clicks a control and checks that the press reached it; tries once
    // more after a wait when something covered it.
    private boolean clickChecked(Node node, Clicker clicker) {
        if (clickOnce(node, clicker)) {
            return true;
        }
        System.out.println("      note: a click meant for " + name(node)
                + " landed on " + name(pressed) + "; waiting, then "
                + "clicking again");
        if (pressed == null) {
            // Another window was in front; bring this one forward first.
            run(() -> bringToFront(node));
        }
        pause(COVER_WAIT_MILLIS);
        return clickOnce(node, clicker);
    }

    // Clicks once and says whether the press reached the control.
    private boolean clickOnce(Node node, Clicker clicker) {
        moveTo(node);
        Scene scene = onFx(() -> node.getScene());
        if (scene == null) {
            pressed = null;
            return false;
        }
        EventHandler<MouseEvent> recorder = event -> recordPress(event,
                node);
        run(() -> {
            pressed = null;
            pressedInside = false;
            scene.addEventFilter(MouseEvent.MOUSE_PRESSED, recorder);
            clicker.send();
        });
        pause(SETTLE_MILLIS);
        run(() -> scene.removeEventFilter(MouseEvent.MOUSE_PRESSED,
                recorder));
        return pressedInside;
    }

    // Brings the window of a control in front of every other window.
    private void bringToFront(Node node) {
        Window window = node.getScene().getWindow();
        if (window instanceof Stage) {
            ((Stage) window).toFront();
        }
        window.requestFocus();
    }

    // Notes what a press reached and whether it lies inside the control.
    private void recordPress(MouseEvent event, Node control) {
        pressed = event.getTarget();
        pressedInside = pressed instanceof Node
                && isInside((Node) pressed, control);
    }

    // Says whether a node is a control or lies inside it.
    private boolean isInside(Node target, Node control) {
        Node current = target;
        while (current != null) {
            if (current == control) {
                return true;
            }
            current = current.getParent();
        }
        return false;
    }

    // Names a node for the report.
    private String name(Object target) {
        if (target instanceof Labeled) {
            return "\"" + ((Labeled) target).getText() + "\"";
        }
        if (target == null) {
            return "nothing in the window";
        }
        Object current = target;
        while (current instanceof Node) {
            Node node = (Node) current;
            if (!node.getStyleClass().isEmpty()) {
                return node.getClass().getSimpleName() + " ."
                        + node.getStyleClass().get(0);
            }
            current = node.getParent();
        }
        return target.getClass().getSimpleName();
    }

    // Prints where the JavaFX thread is, to show why it stopped.
    private void printFxThread() {
        for (Map.Entry<Thread, StackTraceElement[]> entry
                : Thread.getAllStackTraces().entrySet()) {
            if (entry.getKey().getName().contains("JavaFX Application")) {
                System.out.println("      the JavaFX thread is at:");
                StackTraceElement[] frames = entry.getValue();
                int shown = Math.min(frames.length, STACK_LINES);
                for (int index = 0; index < shown; index++) {
                    System.out.println("        " + frames[index]);
                }
            }
        }
    }

    // Scrolls every scroll pane around a node so the node is in view.
    private boolean reveal(Node node) {
        boolean scrolled = false;
        Node current = node.getParent();
        while (current != null) {
            if (current instanceof ScrollPane
                    && scrollTo((ScrollPane) current, node)) {
                scrolled = true;
            }
            current = current.getParent();
        }
        return scrolled;
    }

    // Scrolls one pane down or up until a node inside it is in view.
    private boolean scrollTo(ScrollPane pane, Node node) {
        Node content = pane.getContent();
        if (content == null) {
            return false;
        }
        Bounds target = content.sceneToLocal(node.localToScene(
                node.getBoundsInLocal()));
        double view = pane.getViewportBounds().getHeight();
        double spare = content.getLayoutBounds().getHeight() - view;
        if (spare <= 0.0) {
            return false;
        }
        double top = spare * pane.getVvalue();
        boolean inView = target.getMinY() >= top
                && target.getMaxY() <= top + view;
        if (inView) {
            return false;
        }
        double wanted = (target.getMinY() - REVEAL_MARGIN) / spare;
        pane.setVvalue(Math.max(0.0, Math.min(1.0, wanted)));
        return true;
    }

    // Returns the middle of a control on the screen, or null when the
    // control is not in a window on show.
    private double[] centre(Node node) {
        Bounds bounds = node.localToScreen(node.getBoundsInLocal());
        if (bounds == null) {
            return null;
        }
        double[] point = {bounds.getCenterX(), bounds.getCenterY()};
        return point;
    }

    // Adds a node and everything under it to a list.
    private void collect(Node node, List<Node> nodes) {
        nodes.add(node);
        if (node instanceof Parent) {
            for (Node child : ((Parent) node).getChildrenUnmodifiable()) {
                collect(child, nodes);
            }
        }
    }

    // Says whether a node and every parent of it are visible.
    private boolean isShown(Node node) {
        Node current = node;
        while (current != null) {
            if (!current.isVisible()) {
                return false;
            }
            current = current.getParent();
        }
        return node.getScene() != null;
    }

    // Returns the robot, creating it on first use on the JavaFX thread.
    private Robot robot() {
        if (robot == null) {
            robot = new Robot();
        }
        return robot;
    }

    // Returns the key that types one character.
    private KeyCode keyFor(char letter) {
        if (letter == ' ') {
            return KeyCode.SPACE;
        }
        if (letter == '.') {
            return KeyCode.PERIOD;
        }
        if (Character.isDigit(letter)) {
            return KeyCode.valueOf("DIGIT" + letter);
        }
        return KeyCode.valueOf(String.valueOf(
                Character.toUpperCase(letter)));
    }
}
