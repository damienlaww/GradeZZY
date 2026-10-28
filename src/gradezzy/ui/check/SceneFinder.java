/*
 * File: SceneFinder.java
 *
 * Purpose: Finds the buttons, links, labels and fields of an off-screen
 * window by their text or style class, the way an instructor finds them
 * by looking, and lets the window settle after a press.
 *
 * Where it fits: FlowSelfTest uses it to drive the guided window
 * through its real controls instead of calling screen methods.
 */
package gradezzy.ui.check;

import java.util.ArrayList;
import java.util.List;

import javafx.application.Platform;
import javafx.scene.Node;
import javafx.scene.Parent;
import javafx.scene.Scene;
import javafx.scene.control.Button;
import javafx.scene.control.ButtonBase;
import javafx.scene.control.Hyperlink;
import javafx.scene.control.Label;
import javafx.scene.control.Labeled;
import javafx.scene.control.ScrollPane;
import javafx.scene.input.MouseButton;
import javafx.scene.input.MouseEvent;

/**
 * Finds nodes of a scene by text and style class.
 */
public final class SceneFinder {

    // Layout passes after a press, so nested panes settle
    private static final int LAYOUT_PASSES = 3;

    // Not used: every method is static.
    private SceneFinder() {
    }

    /**
     * Runs the tasks waiting for the JavaFX thread (such as progress
     * updates posted with Platform.runLater), then lays the scene out.
     * Must be called on the JavaFX thread, outside layout.
     *
     * @param scene the scene
     */
    public static void settle(Scene scene) {
        Object key = new Object();
        Platform.runLater(() -> Platform.exitNestedEventLoop(key, null));
        Platform.enterNestedEventLoop(key);
        for (int pass = 0; pass < LAYOUT_PASSES; pass++) {
            scene.getRoot().applyCss();
            scene.getRoot().layout();
        }
    }

    /**
     * Returns every node under a root that is showing (it and every
     * parent are visible), the root first. A scroll pane is entered
     * through its content, so its scroll bars are skipped.
     *
     * @param root where to start
     * @return the nodes, depth first
     */
    public static List<Node> showing(Node root) {
        List<Node> found = new ArrayList<>();
        collect(root, found);
        return found;
    }

    /**
     * Returns every showing node of a type under a root.
     *
     * @param <T> the type
     * @param root where to start
     * @param type the class to look for
     * @return the nodes, depth first
     */
    public static <T> List<T> ofType(Node root, Class<T> type) {
        List<T> found = new ArrayList<>();
        for (Node node : showing(root)) {
            if (type.isInstance(node)) {
                found.add(type.cast(node));
            }
        }
        return found;
    }

    /**
     * Finds the first showing button with exactly this text.
     *
     * @param root where to look
     * @param text the button text
     * @return the button, or null
     */
    public static Button button(Node root, String text) {
        for (Button button : ofType(root, Button.class)) {
            if (text.equals(button.getText())) {
                return button;
            }
        }
        return null;
    }

    /**
     * Finds the first showing link with exactly this text.
     *
     * @param root where to look
     * @param text the link text
     * @return the link, or null
     */
    public static Hyperlink link(Node root, String text) {
        for (Hyperlink link : ofType(root, Hyperlink.class)) {
            if (text.equals(link.getText())) {
                return link;
            }
        }
        return null;
    }

    /**
     * Finds the first showing label with exactly this text.
     *
     * @param root where to look
     * @param text the text
     * @return the label, or null
     */
    public static Label label(Node root, String text) {
        for (Label label : ofType(root, Label.class)) {
            if (text.equals(label.getText())) {
                return label;
            }
        }
        return null;
    }

    /**
     * Returns the texts of the showing labels with a style class.
     *
     * @param root where to look
     * @param styleClass the class, such as "pill"
     * @return the texts, depth first
     */
    public static List<String> texts(Node root, String styleClass) {
        List<String> texts = new ArrayList<>();
        for (Labeled labeled : ofType(root, Labeled.class)) {
            if (labeled.getStyleClass().contains(styleClass)) {
                texts.add(labeled.getText());
            }
        }
        return texts;
    }

    /**
     * Returns the text of the first showing label with a style class.
     *
     * @param root where to look
     * @param styleClass the class
     * @return the text, or an empty string
     */
    public static String text(Node root, String styleClass) {
        List<String> texts = texts(root, styleClass);
        if (texts.isEmpty() || texts.get(0) == null) {
            return "";
        }
        return texts.get(0);
    }

    /**
     * Says whether any showing label or button holds some text.
     *
     * @param root where to look
     * @param fragment the text to find
     * @return true when found
     */
    public static boolean shows(Node root, String fragment) {
        for (Labeled labeled : ofType(root, Labeled.class)) {
            String text = labeled.getText();
            if (text != null && text.contains(fragment)) {
                return true;
            }
        }
        return false;
    }

    /**
     * Returns the nearest ancestor of a node with a style class.
     *
     * @param node where to start
     * @param styleClass the class
     * @return the ancestor, or null
     */
    public static Node ancestor(Node node, String styleClass) {
        Node at = node;
        while (at != null) {
            if (at.getStyleClass().contains(styleClass)) {
                return at;
            }
            at = at.getParent();
        }
        return null;
    }

    /**
     * Presses a button or link if it is there and enabled.
     *
     * @param button the button, or null
     * @return true when it was pressed
     */
    public static boolean press(ButtonBase button) {
        if (button == null || button.isDisabled()) {
            return false;
        }
        button.fire();
        return true;
    }

    /**
     * Clicks a node once with the main mouse button, for rows that open
     * on a click rather than through a button.
     *
     * @param node the node
     */
    public static void click(Node node) {
        node.fireEvent(new MouseEvent(MouseEvent.MOUSE_CLICKED, 0, 0, 0, 0,
                MouseButton.PRIMARY, 1, false, false, false, false, true,
                false, false, true, false, false, null));
    }

    // Adds a node and its showing children.
    private static void collect(Node node, List<Node> found) {
        if (node == null || !node.isVisible()) {
            return;
        }
        found.add(node);
        if (node instanceof ScrollPane) {
            collect(((ScrollPane) node).getContent(), found);
            return;
        }
        if (node instanceof Parent) {
            for (Node child : ((Parent) node).getChildrenUnmodifiable()) {
                collect(child, found);
            }
        }
    }
}
