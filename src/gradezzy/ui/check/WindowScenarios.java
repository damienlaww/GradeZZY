/*
 * File: WindowScenarios.java
 *
 * Purpose: The window-level checks of the real-window test: the window
 * opens, the sidebar and shortcuts move between screens, Tab moves the
 * focus with a visible ring, tooltips appear, files can be dragged onto
 * the drop area, and the layout adapts when the window is resized.
 *
 * Where it fits: Run by WindowTest before and after WindowFlows. Every
 * action is real mouse or keyboard input through RobotDriver.
 *
 * AI use: drafted with Claude Code, reviewed by Jun Sheng.
 */
package gradezzy.ui.check;

import java.awt.AWTException;
import java.io.File;
import java.io.FileOutputStream;
import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.List;
import java.util.concurrent.atomic.AtomicBoolean;
import java.util.zip.ZipOutputStream;

import javafx.collections.ListChangeListener;
import javafx.scene.Node;
import javafx.scene.Parent;
import javafx.scene.Scene;
import javafx.scene.control.Label;
import javafx.scene.control.Labeled;
import javafx.scene.input.ClipboardContent;
import javafx.scene.input.Dragboard;
import javafx.scene.input.KeyCode;
import javafx.scene.input.TransferMode;
import javafx.scene.layout.StackPane;
import javafx.scene.paint.Color;
import javafx.stage.Stage;
import javafx.stage.StageStyle;

import gradezzy.ui.Screen;
import gradezzy.ui.backend.InputKind;
import gradezzy.ui.view.ScreenShortcuts;

/**
 * Window-level checks with real input.
 *
 * <p>Tier: SHOULD. Owner: Damien.
 */
public class WindowScenarios {

    // The focus ring colour of the light theme
    private static final Color RING = Color.web("#60A5FA");

    // The inner focus edge colour of the light theme
    private static final Color EDGE = Color.web("#1E3A8A");

    // The number of Tab presses whose focus is checked
    private static final int TAB_PRESSES = 8;

    // How long a tooltip may take to appear under a resting pointer
    private static final long TOOLTIP_WAIT_MILLIS = 3000L;

    // How long the pointer stays away before coming back onto an item
    private static final long HOVER_GAP_MILLIS = 600L;

    // The focus ring colour of the sidebar items
    private static final Color SIDEBAR_RING = Color.web("#93C5FD");

    // How long a screen may take to appear
    private static final long SCREEN_WAIT_MILLIS = 3000L;

    // The sample ZIP files the drop puts on the drop area
    private static final int SAMPLE_COUNT = 6;

    // The compact and narrow window sizes checked
    private static final double COMPACT_WIDTH = 960.0;
    private static final double COMPACT_HEIGHT = 640.0;
    private static final double NARROW_WIDTH = 600.0;
    private static final double NARROW_HEIGHT = 400.0;
    private static final double FULL_WIDTH = 1200.0;
    private static final double FULL_HEIGHT = 800.0;

    // The size of the small window that drags files
    private static final double SOURCE_SIZE = 140.0;

    // The test that keeps the score
    private final WindowTest test;

    // Sends real input and reads the screen
    private final RobotDriver driver;

    /**
     * Creates the window-level checks.
     *
     * @param test the test that keeps the score
     */
    public WindowScenarios(WindowTest test) {
        this.test = test;
        this.driver = test.getDriver();
    }

    /**
     * Runs the checks that come before the grading flows.
     */
    public void runBasics() {
        windowOpens();
        sidebarNavigation();
        keyboardShortcuts();
        focusOrder();
        tooltip();
    }

    /**
     * Runs the checks that come after the grading flows.
     */
    public void runWindowChecks() {
        dragAndDrop();
        resizeLayouts();
    }

    // The window is on the screen at 1200 by 800.
    private void windowOpens() {
        test.section("The window opens");
        Stage stage = test.getStage();
        boolean showing = driver.onFx(() -> stage.isShowing());
        String title = driver.onFx(() -> stage.getTitle());
        double width = driver.onFx(() -> stage.getScene().getWidth());
        double height = driver.onFx(() -> stage.getScene().getHeight());
        test.check(showing, "the window is on the screen");
        test.check(title.contains("GradeZZY"), "the title is \"" + title
                + "\"");
        test.check(width == FULL_WIDTH && height == FULL_HEIGHT,
                "the content is 1200 by 800 (it is " + width + " by "
                + height + ")");
        Labeled title1 = driver.find(test.getRoot(), "Overview");
        if (title1 != null) {
            driver.click(title1);
        }
        test.capture("01-overview-light");
    }

    // Every sidebar item opens its screen when clicked.
    private void sidebarNavigation() {
        test.section("Sidebar: each item opens its screen with a click");
        String[] items = {"Modules", "Assessments", "Submissions",
            "Review Anomalies", "Results", "Statistics", "Similarity Review",
            "History", "Audit Log", "Settings", "Help", "Overview"};
        Screen[] screens = {Screen.MODULES, Screen.TEST_CASES,
            Screen.UPLOAD, Screen.ANOMALY_LOG, Screen.RESULTS,
            Screen.STATISTICS, Screen.PLAGIARISM, Screen.HISTORY,
            Screen.AUDIT_LOG, Screen.SETTINGS, Screen.HELP,
            Screen.OVERVIEW};
        for (int index = 0; index < items.length; index++) {
            test.check(test.openFromSidebar(items[index], screens[index]),
                    "clicking " + items[index] + " opens it");
        }
    }

    // Shortcut+1 to 9, Shortcut+comma, F1 and Shortcut+[ work.
    private void keyboardShortcuts() {
        test.section("Keyboard shortcuts");
        KeyCode[] digits = {KeyCode.DIGIT1, KeyCode.DIGIT2, KeyCode.DIGIT3,
            KeyCode.DIGIT4, KeyCode.DIGIT5, KeyCode.DIGIT6, KeyCode.DIGIT7,
            KeyCode.DIGIT8, KeyCode.DIGIT9};
        List<Screen> numbered = ScreenShortcuts.numberedScreens();
        for (int index = 0; index < numbered.size(); index++) {
            Screen wanted = numbered.get(index);
            driver.shortcut(digits[index]);
            test.check(onScreen(wanted), ScreenShortcuts.describe(wanted)
                    + " opens " + wanted.getTitle());
        }
        driver.shortcut(KeyCode.COMMA);
        test.check(onScreen(Screen.SETTINGS), "Shortcut+Comma opens "
                + "Settings");
        driver.press(KeyCode.F1);
        test.check(onScreen(Screen.HELP), "F1 opens Help");
        driver.shortcut(KeyCode.OPEN_BRACKET);
        test.check(onScreen(Screen.SETTINGS), "Shortcut+[ goes back to "
                + "Settings");
        driver.shortcut(KeyCode.DIGIT6);
        driver.shortcut(KeyCode.F);
        Node focus = focusOwner();
        test.check(focus != null && "searchField".equals(focus.getId()),
                "Shortcut+F puts the focus in the Results search");
    }

    // Tab moves the focus from control to control, each with a ring
    // that is really drawn on the screen, and Shift+Tab goes back.
    private void focusOrder() {
        test.section("Tab order and visible focus on Results");
        List<Node> visited = new ArrayList<>();
        int withRing = 0;
        int checkedRings = 0;
        for (int press = 0; press < TAB_PRESSES; press++) {
            driver.press(KeyCode.TAB);
            Node focus = focusOwner();
            if (focus == null) {
                test.check(false, "Tab " + (press + 1) + " leaves the "
                        + "focus on a control");
                return;
            }
            boolean moved = visited.isEmpty()
                    || visited.get(visited.size() - 1) != focus;
            boolean visible = driver.onFx(() -> focus.isFocusVisible());
            boolean ring;
            if (focus.getStyleClass().contains("nav-item")) {
                ring = driver.showsRing(focus, SIDEBAR_RING, SIDEBAR_RING);
            } else {
                ring = driver.showsRing(focus, RING, EDGE);
            }
            test.note("Tab " + (press + 1) + ": " + describe(focus)
                    + ", ring on screen: " + ring);
            test.check(moved && visible, "Tab " + (press + 1)
                    + " moves the focus to a new control and marks it "
                    + "visible");
            if (isRingedControl(focus)) {
                checkedRings++;
                if (ring) {
                    withRing++;
                }
            }
            visited.add(focus);
        }
        test.check(checkedRings > 0 && withRing == checkedRings,
                withRing + " of " + checkedRings + " focused buttons, "
                + "fields and choices show the focus ring on the screen");
        test.capture("02-focus-ring");

        driver.press(KeyCode.SHIFT, KeyCode.TAB);
        Node back = focusOwner();
        test.check(back == visited.get(visited.size() - 2),
                "Shift+Tab returns the focus to the previous control");
    }

    // A sidebar tooltip shows the item's keyboard shortcut.
    private void tooltip() {
        test.section("Tooltips");
        Parent sidebar = (Parent) test.byId("sidebar");
        Labeled overview = driver.find(sidebar, "Overview");
        Labeled results = driver.find(sidebar, "Results");
        // A tooltip opens when the pointer comes onto a control, so the
        // pointer first leaves the item it may already rest on.
        driver.moveTo(results);
        driver.pause(HOVER_GAP_MILLIS);
        driver.moveTo(overview);
        String expected = "Overview ("
                + ScreenShortcuts.describe(Screen.OVERVIEW) + ")";
        boolean shown = driver.waitUntil(() -> driver.findInPopups(expected)
                != null, TOOLTIP_WAIT_MILLIS);
        test.check(shown, "resting on Overview shows the tooltip \""
                + expected + "\"");
        driver.moveTo(results);
    }

    // ZIP files dragged from another window land on the drop area. Demo
    // mode reads none of them and adds the sample files instead.
    private void dragAndDrop() {
        test.section("Drag and drop onto the Submissions drop area");
        test.openFromSidebar("Submissions", Screen.UPLOAD);
        boolean filled = driver.onFx(() -> !test.getState()
                .getImportBatch().getItems(InputKind.SUBMISSION).isEmpty());
        if (filled) {
            test.clickText("Clear All");
            test.clickInSheet("Remove All");
        }

        List<File> files;
        try {
            files = sampleZips();
        } catch (IOException exception) {
            test.check(false, "temporary ZIP files could be made: "
                    + exception.getMessage());
            return;
        }
        Stage source = driver.onFx(() -> openDragSource(files));
        Node handle = driver.onFx(() -> source.getScene().getRoot());
        Node dropZone = test.byId("dropZone");

        // The drop area is watched rather than asked, because the system
        // drag loop holds the JavaFX thread until the drop.
        AtomicBoolean hovered = new AtomicBoolean(false);
        driver.run(() -> dropZone.getStyleClass().addListener(
                (ListChangeListener<String>) change -> {
                    if (dropZone.getStyleClass().contains("drag-over")) {
                        hovered.set(true);
                    }
                }));
        try {
            driver.dragBetweenWindows(handle, dropZone);
        } catch (AWTException exception) {
            test.check(false, "the system robot can drag: "
                    + exception.getMessage());
            driver.run(() -> source.close());
            return;
        }
        boolean added = driver.waitUntil(() -> test.getState()
                .getImportBatch().getItems(InputKind.SUBMISSION).size()
                == SAMPLE_COUNT, SCREEN_WAIT_MILLIS);
        test.check(hovered.get(), "the drop area is highlighted while ZIP "
                + "files hover over it");
        test.check(added, "dropping adds the files (demo mode adds the "
                + "six sample files and reads nothing)");
        test.capture("20-after-drop");
        driver.run(() -> source.close());
    }

    // The layout narrows the sidebar, then swaps it for a menu.
    private void resizeLayouts() {
        test.section("Window sizes");
        test.resize(COMPACT_WIDTH, COMPACT_HEIGHT);
        boolean compact = driver.onFx(() -> test.getState()
                .compactProperty().get());
        Node sidebar = test.byId("sidebar");
        boolean narrowSidebar = driver.onFx(() -> sidebar.getStyleClass()
                .contains("compact"));
        test.check(compact && narrowSidebar, "at 960 by 640 the window "
                + "is compact and the sidebar narrows");
        test.capture("21-compact-960");

        test.resize(NARROW_WIDTH, NARROW_HEIGHT);
        boolean narrow = driver.onFx(() -> test.getState().narrowProperty()
                .get());
        Node menu = test.byId("navMenu");
        boolean menuShown = driver.onFx(() -> menu.isVisible()
                && !sidebar.isVisible());
        test.check(narrow && menuShown, "at 600 by 400 the sidebar becomes "
                + "a menu button");
        driver.click(menu);
        driver.waitUntil(() -> driver.findInPopups("Results") != null,
                SCREEN_WAIT_MILLIS);
        Labeled item = driver.findInPopups("Results");
        if (item != null) {
            // A menu entry takes the press on the row around its words.
            driver.click(driver.onFx(() -> item.getParent()));
        }
        test.check(onScreen(Screen.RESULTS), "the menu opens Results");
        test.capture("22-narrow-600");

        test.resize(FULL_WIDTH, FULL_HEIGHT);
        boolean wide = driver.onFx(() -> !test.getState().compactProperty()
                .get() && sidebar.isVisible());
        test.check(wide, "back at 1200 by 800 the full sidebar returns");
    }

    // Opens a small window that starts a drag of real files.
    private Stage openDragSource(List<File> files) {
        Label handle = new Label("Drag these ZIP files");
        StackPane pane = new StackPane(handle);
        pane.setOnDragDetected(event -> {
            Dragboard board = pane.startDragAndDrop(TransferMode.COPY);
            ClipboardContent content = new ClipboardContent();
            content.putFiles(files);
            board.setContent(content);
            event.consume();
        });
        Stage source = new Stage(StageStyle.UTILITY);
        source.setScene(new Scene(pane, SOURCE_SIZE, SOURCE_SIZE));
        source.setAlwaysOnTop(true);
        Stage main = test.getStage();
        source.setX(main.getX() + main.getWidth() - SOURCE_SIZE * 2);
        source.setY(main.getY() + main.getHeight() - SOURCE_SIZE * 2);
        source.show();
        return source;
    }

    // Makes two empty ZIP files in a temporary folder.
    private List<File> sampleZips() throws IOException {
        Path folder = Files.createTempDirectory("gradezzy-drop");
        List<File> files = new ArrayList<>();
        String[] names = {"2025-2026-drop.one.zip", "2025-2026-drop.two.zip"};
        for (String name : names) {
            File file = folder.resolve(name).toFile();
            ZipOutputStream zip = new ZipOutputStream(
                    new FileOutputStream(file));
            zip.close();
            file.deleteOnExit();
            files.add(file);
        }
        folder.toFile().deleteOnExit();
        return files;
    }

    // Waits for a screen to be on show.
    private boolean onScreen(Screen screen) {
        return driver.waitUntil(() -> test.getMain().getCurrentScreen()
                == screen, SCREEN_WAIT_MILLIS);
    }

    // Returns the control that has the keyboard focus.
    private Node focusOwner() {
        return driver.onFx(() -> test.getStage().getScene()
                .getFocusOwner());
    }

    // Says whether a control draws the focus ring checked on screen.
    private boolean isRingedControl(Node node) {
        List<String> classes = node.getStyleClass();
        boolean excluded = classes.contains("table-view")
                || classes.contains("list-view");
        boolean ringed = classes.contains("button")
                || classes.contains("toggle-button")
                || classes.contains("text-field")
                || classes.contains("combo-box-base")
                || classes.contains("combo-box");
        return ringed && !excluded;
    }

    // Describes a control in a few words for the report.
    private String describe(Node node) {
        String name = node.getClass().getSimpleName();
        if (node instanceof Labeled) {
            return name + " \"" + ((Labeled) node).getText() + "\"";
        }
        String text = node.getAccessibleText();
        if (text == null) {
            return name;
        }
        return name + " \"" + text + "\"";
    }
}
