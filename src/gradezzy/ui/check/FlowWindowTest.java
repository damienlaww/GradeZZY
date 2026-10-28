/*
 * File: FlowWindowTest.java
 *
 * Purpose: Tests the real window with real mouse and keyboard input and
 * prints PASS or FAIL for each check. After each scenario it saves a
 * picture of the window and checks that no sheet is left open and that
 * nothing on the page has a drop shadow.
 *
 * Where it fits: Run by "sh run.sh --windowtest [folder]"; leave the
 * mouse and keyboard alone while it runs. The scenarios live in
 * FlowWindowScenarios. Replaces the old WindowTest.
 */
package gradezzy.ui.check;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.List;
import java.util.function.IntConsumer;

import javafx.application.Platform;
import javafx.geometry.BoundingBox;
import javafx.geometry.Bounds;
import javafx.scene.Node;
import javafx.scene.Parent;
import javafx.scene.Scene;
import javafx.scene.control.Labeled;
import javafx.scene.effect.DropShadow;
import javafx.scene.effect.Effect;
import javafx.stage.Stage;

import gradezzy.service.ServiceRegistry;
import gradezzy.ui.demo.DemoBackend;
import gradezzy.ui.flow.FlowWindow;
import gradezzy.ui.flow.Page;

/**
 * Drives the real window.
 */
public class FlowWindowTest {

    // The size of the window the test uses
    private static final double WIDTH = 1200.0;
    private static final double HEIGHT = 800.0;

    /** The pause after an input before the window is checked. */
    public static final long SETTLE_MILLIS = 400L;

    // How long the window may take to react to one input
    private static final long WAIT_MILLIS = 3000L;

    // The pause before the first input, so the window is drawn
    private static final long START_MILLIS = 1500L;

    // The most drop shadows named in one failure
    private static final int MAX_NAMED = 5;

    // Every service of the application
    private final ServiceRegistry services;

    // The window to drive
    private final Stage stage;

    // Where pictures are saved
    private final Path folder;

    // Sends real input and reads the screen
    private final RobotDriver driver = new RobotDriver();

    // The window being tested
    private FlowWindow window = null;

    // The number of checks that passed
    private int passed = 0;

    // The number of checks that failed
    private int failed = 0;

    /**
     * Creates the test.
     *
     * @param services every service of the application
     * @param stage the window to drive
     * @param folder where pictures are saved
     */
    public FlowWindowTest(ServiceRegistry services, Stage stage,
            Path folder) {
        this.services = services;
        this.stage = stage;
        this.folder = folder;
    }

    /**
     * Shows the window with demo data and runs the test on its own
     * thread. Call on the JavaFX thread.
     *
     * @param onDone called with 0 when every check passed, 1 otherwise
     */
    public void start(IntConsumer onDone) {
        try {
            Files.createDirectories(folder);
        } catch (IOException exception) {
            System.out.println("FAIL  cannot create " + folder + ": "
                    + exception.getMessage());
            onDone.accept(1);
            return;
        }
        window = new FlowWindow(new DemoBackend(services), false);
        Scene scene = window.build(WIDTH, HEIGHT);
        stage.setTitle("GradeZZY (window test, demo data)");
        stage.setScene(scene);
        stage.show();
        stage.toFront();

        Thread worker = new Thread(() -> {
            int code = runAll();
            Platform.runLater(() -> onDone.accept(code));
        }, "gradezzy-window-test");
        worker.setDaemon(true);
        worker.start();
    }

    /**
     * Records one check and prints its outcome.
     *
     * @param ok whether the check passed
     * @param expectation what should be true
     */
    public void check(boolean ok, String expectation) {
        if (ok) {
            passed++;
            System.out.println("PASS  " + expectation);
        } else {
            failed++;
            System.out.println("FAIL  " + expectation);
        }
    }

    /**
     * Prints a heading for a scenario.
     *
     * @param name the scenario
     */
    public void section(String name) {
        System.out.println();
        System.out.println("== " + name);
    }

    /**
     * Prints a line of information that is not a check.
     *
     * @param text the line
     */
    public void note(String text) {
        System.out.println("      " + text);
    }

    /**
     * Ends a scenario: saves a picture, then checks that no sheet is left
     * open and that nothing on the page has a drop shadow.
     *
     * @param picture the picture's file name, without .png
     */
    public void endScenario(String picture) {
        driver.pause(SETTLE_MILLIS);
        capture(picture);
        check(!driver.onFx(() -> window.isSheetOpen()),
                picture + ": no sheet is left open");
        List<String> shadows = driver.onFx(() -> findShadows());
        check(shadows.isEmpty(), picture + ": nothing on the page has a "
                + "drop shadow" + describe(shadows));
    }

    /**
     * Waits until a condition holds.
     *
     * @param condition the condition, checked on the JavaFX thread
     * @return true when it held in time
     */
    public boolean waitFor(RobotDriver.Condition condition) {
        return driver.waitUntil(condition, WAIT_MILLIS);
    }

    /**
     * Clicks the first visible control with some words in the window.
     *
     * @param text the words on the control
     * @return true when it was found and clicked
     */
    public boolean clickText(String text) {
        return clickIn(getRoot(), text);
    }

    /**
     * Clicks the first visible control with some words under a node.
     *
     * @param root where to look
     * @param text the words on the control
     * @return true when it was found and clicked
     */
    public boolean clickIn(Parent root, String text) {
        Labeled control = driver.find(root, text);
        if (control == null) {
            note("no control with the words: " + text);
            return false;
        }
        return driver.click(control);
    }

    /**
     * Returns the first visible node with a style class, or null.
     *
     * @param styleClass the style class, without the dot
     * @return the node, or null
     */
    public Node firstStyled(String styleClass) {
        List<Node> nodes = driver.findAll(getRoot(), styleClass);
        if (nodes.isEmpty()) {
            return null;
        }
        return nodes.get(0);
    }

    /**
     * Returns the window being tested.
     *
     * @return the window
     */
    public FlowWindow getWindow() {
        return window;
    }

    /**
     * Returns the root of the scene.
     *
     * @return the root
     */
    public Parent getRoot() {
        return window.getRoot();
    }

    /**
     * Returns the input driver.
     *
     * @return the driver
     */
    public RobotDriver getDriver() {
        return driver;
    }

    // Runs every scenario and prints the score.
    private int runAll() {
        try {
            driver.pause(START_MILLIS);
            if (!inputReachesWindow()) {
                return 1;
            }
            new FlowWindowScenarios(this).runAll();
        } catch (RuntimeException exception) {
            failed++;
            System.out.println("FAIL  the test stopped: " + exception);
            exception.printStackTrace(System.out);
        }
        System.out.println();
        System.out.println(passed + " checks passed, " + failed
                + " failed. Pictures: " + folder.toAbsolutePath());
        if (failed == 0) {
            return 0;
        }
        return 1;
    }

    // Clicks the Marking tab once to see whether real input reaches the
    // window. macOS drops robot input unless the app that started java
    // may control the computer (Accessibility), and shows only the
    // desktop in pictures unless it may record the screen.
    private boolean inputReachesWindow() {
        boolean reached = driver.click(window.getTabBar().getTab(
                Page.MARKING));
        check(reached, "real mouse input reaches the window");
        if (!reached) {
            note("Stopped: the system did not deliver the robot's clicks. "
                    + "On macOS, allow the app that runs java (Terminal "
                    + "or your editor) in System Settings, Privacy and "
                    + "Security, under Accessibility and Screen "
                    + "Recording, then run the test again.");
        }
        return reached;
    }

    /**
     * Saves a picture of the whole window as it is on the screen, with
     * no checks, for states such as an open sheet.
     *
     * @param name the file name, without .png
     */
    public void picture(String name) {
        driver.pause(SETTLE_MILLIS);
        capture(name);
    }

    // Saves a picture of the whole window as it is on the screen.
    private void capture(String name) {
        try {
            Bounds bounds = driver.onFx(() -> new BoundingBox(stage.getX(),
                    stage.getY(), stage.getWidth(), stage.getHeight()));
            driver.capture(bounds, folder.resolve(name + ".png"));
        } catch (IOException exception) {
            note("could not save picture " + name + ": "
                    + exception.getMessage());
        }
    }

    // Names every visible node with a drop shadow, except the box of an
    // open sheet, which floats above the page. Runs on the JavaFX thread.
    private List<String> findShadows() {
        List<String> found = new ArrayList<>();
        walk(getRoot(), found);
        return found;
    }

    // Looks for drop shadows in a node and everything under it.
    private void walk(Node node, List<String> found) {
        if (!node.isVisible()) {
            return;
        }
        if (hasDropShadow(node.getEffect()) && !isSheetBox(node)) {
            found.add(name(node));
        }
        if (node instanceof Parent) {
            for (Node child : ((Parent) node).getChildrenUnmodifiable()) {
                walk(child, found);
            }
        }
    }

    // Says whether an effect is a drop shadow.
    private boolean hasDropShadow(Effect effect) {
        return effect instanceof DropShadow;
    }

    // Says whether a node is the box of a sheet, the only allowed shadow.
    private boolean isSheetBox(Node node) {
        Parent parent = node.getParent();
        return node.getStyleClass().contains("sheet") && parent != null
                && parent.getStyleClass().contains("sheet-dim");
    }

    // Names a node by its class, style classes and words.
    private String name(Node node) {
        String text = node.getClass().getSimpleName() + " "
                + node.getStyleClass();
        if (node instanceof Labeled) {
            text = text + " \"" + ((Labeled) node).getText() + "\"";
        }
        Parent parent = node.getParent();
        if (parent != null) {
            text = text + " in " + parent.getClass().getSimpleName() + " "
                    + parent.getStyleClass();
        }
        return text;
    }

    // Lists the first few shadows found, for a failure line.
    private String describe(List<String> shadows) {
        if (shadows.isEmpty()) {
            return "";
        }
        StringBuilder text = new StringBuilder(" (found "
                + shadows.size() + ":");
        for (int i = 0; i < shadows.size() && i < MAX_NAMED; i++) {
            text.append(" [").append(shadows.get(i)).append("]");
        }
        return text.append(")").toString();
    }
}
