/*
 * File: WindowTest.java
 *
 * Purpose: Tests the real window with real mouse and keyboard input and
 * prints PASS or FAIL for each check.
 *
 * Where it fits: Run by "sh run.sh --windowtest [folder]"; leave the mouse
 * and keyboard alone while it runs. The scenarios live in WindowScenarios
 * and WindowFlows.
 */
package gradezzy.ui.check;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.List;
import java.util.function.IntConsumer;

import javafx.application.Platform;
import javafx.geometry.BoundingBox;
import javafx.geometry.Bounds;
import javafx.scene.Node;
import javafx.scene.Parent;
import javafx.scene.Scene;
import javafx.scene.control.Labeled;
import javafx.scene.control.ListCell;
import javafx.scene.control.ListView;
import javafx.scene.control.TableRow;
import javafx.scene.control.TableView;
import javafx.stage.Stage;

import gradezzy.model.GradingRun;
import gradezzy.model.Submission;
import gradezzy.service.ServiceRegistry;
import gradezzy.ui.MainController;
import gradezzy.ui.Screen;
import gradezzy.ui.ShellBuilder;
import gradezzy.ui.ThemeManager;
import gradezzy.ui.UiState;
import gradezzy.ui.backend.DashboardBackend;
import gradezzy.ui.backend.LiveBackend;
import gradezzy.ui.demo.DemoBackend;

/**
 * The real-window test.
 */
public class WindowTest {

    /**
     * Picks a row of a table or a list by the item it shows.
     */
    public interface RowMatch {

        /**
         * Says whether a row's item is the one wanted.
         *
         * @param item the row's item, possibly null
         * @return true for the wanted row
         */
        boolean matches(Object item);
    }

    // The size of the window the test starts with
    private static final double WIDTH = 1200.0;
    private static final double HEIGHT = 800.0;

    // The smallest window, as in MainApp
    private static final double MIN_WIDTH = 600.0;
    private static final double MIN_HEIGHT = 400.0;

    // How long a screen may take to appear after a click
    private static final long SCREEN_WAIT_MILLIS = 3000L;

    // The most wheel notches turned to bring a row into view
    private static final int MAX_WHEEL_NOTCHES = 12;

    // The style classes of lists that can scroll
    private static final String[] LIST_CLASSES = {"table-view",
        "list-view"};

    // The style class of the visible part of a table or list
    private static final String CLIPPED_CLASS = "clipped-container";

    // The words on the sidebar items, in order
    private static final String[] SIDEBAR_ITEMS = {"Overview", "Modules",
        "Assessments", "Submissions", "Review Anomalies", "Results",
        "Statistics", "Similarity Review", "History", "Audit Log",
        "Settings", "Help"};

    // Every service of the application
    private final ServiceRegistry services;

    // The real window
    private final Stage stage;

    // Where the pictures are saved
    private final Path folder;

    // Sends real input and reads the screen
    private final RobotDriver driver = new RobotDriver();

    // The backend of the window being tested
    private DashboardBackend backend = null;

    // The state of the window being tested
    private UiState state = null;

    // Builds the window being tested
    private ShellBuilder builder = null;

    // The number of checks that passed
    private int passed = 0;

    // The number of checks that failed
    private int failed = 0;

    /**
     * Creates the window test.
     *
     * @param services every service of the application
     * @param stage the real window
     * @param folder where the pictures are saved
     */
    public WindowTest(ServiceRegistry services, Stage stage, Path folder) {
        this.services = services;
        this.stage = stage;
        this.folder = folder;
    }

    /**
     * Opens the window and runs the test on its own thread. Call on the
     * JavaFX thread.
     *
     * @param onDone called on the JavaFX thread with 0 if all passed, else 1
     * @throws IOException if the window cannot be built
     */
    public void start(IntConsumer onDone) throws IOException {
        Files.createDirectories(folder);
        open(new DemoBackend(services), "GradeZZY (demo data)");
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
     * Prints a heading for a group of checks.
     *
     * @param name the group
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
     * Saves a picture of the whole window as it is on the screen.
     *
     * @param name the file name, without .png
     */
    public void capture(String name) {
        try {
            Bounds window = driver.onFx(() -> new BoundingBox(stage.getX(),
                    stage.getY(), stage.getWidth(), stage.getHeight()));
            driver.capture(window, folder.resolve(name + ".png"));
        } catch (IOException exception) {
            note("could not save picture " + name + ": "
                    + exception.getMessage());
        }
    }

    /**
     * Clicks a sidebar item and waits for its screen.
     *
     * @param item the words on the item
     * @param screen the screen it opens
     * @return true when the screen appeared
     */
    public boolean openFromSidebar(String item, Screen screen) {
        Parent sidebar = driver.onFx(() -> (Parent) getRoot().lookup(
                "#sidebar"));
        Labeled button = driver.find(sidebar, item);
        if (button == null) {
            return false;
        }
        driver.click(button);
        return driver.waitUntil(() -> getMain().getCurrentScreen() == screen,
                SCREEN_WAIT_MILLIS);
    }

    /**
     * Resizes the window so its content has a given size.
     *
     * @param width the content width
     * @param height the content height
     */
    public void resize(double width, double height) {
        driver.run(() -> {
            Scene scene = stage.getScene();
            double borderX = stage.getWidth() - scene.getWidth();
            double borderY = stage.getHeight() - scene.getHeight();
            stage.setWidth(width + borderX);
            stage.setHeight(height + borderY);
        });
        driver.pause(SCREEN_WAIT_MILLIS / 3);
    }

    /**
     * Returns the node with an id in the window.
     *
     * @param id the fx:id
     * @return the node, or null
     */
    public Node byId(String id) {
        return driver.onFx(() -> getRoot().lookup("#" + id));
    }

    /**
     * Says whether a sheet covers the window.
     *
     * @return true while a sheet is on show
     */
    public boolean sheetShown() {
        return driver.onFx(() -> getMain().isSheetShown());
    }

    /**
     * Clicks a visible control with some words in the screen's content area.
     *
     * @param text the words on the control
     * @return true when the control was found and clicked
     */
    public boolean clickText(String text) {
        Labeled control = driver.find((Parent) byId("contentHost"), text);
        if (control == null) {
            note("no control with the words: " + text);
            return false;
        }
        driver.click(control);
        return true;
    }

    /**
     * Clicks the control with some words inside the sheet on show.
     *
     * @param text the words on the control
     * @return true when the control was found and clicked
     */
    public boolean clickInSheet(String text) {
        Labeled control = driver.find((Parent) byId("sheetHolder"), text);
        if (control == null) {
            note("no control in the sheet with the words: " + text);
            return false;
        }
        driver.click(control);
        return true;
    }

    /**
     * Finds the visible table row or list cell that shows an item.
     *
     * @param styleClass "table-row-cell" or "list-cell"
     * @param match picks the item
     * @return the row, or null
     */
    public Node rowFor(String styleClass, RowMatch match) {
        for (int notch = 0; notch < MAX_WHEEL_NOTCHES; notch++) {
            Node row = findRow(styleClass, match);
            if (row != null && driver.onFx(() -> fullyShown(row))) {
                return row;
            }
            Node list = listHolding(match);
            if (list == null) {
                return row;
            }
            driver.wheel(list, 1);
        }
        return findRow(styleClass, match);
    }

    /**
     * Returns the run shown for the section in the context bar.
     *
     * @return the run, or null
     */
    public GradingRun currentRun() {
        return driver.onFx(() -> backend.getRun(state.getSection()));
    }

    /**
     * Finds a student in the run shown.
     *
     * @param username the student
     * @return the submission, or null
     */
    public Submission submission(String username) {
        GradingRun run = currentRun();
        if (run == null) {
            return null;
        }
        for (Submission candidate : run.getSubmissions()) {
            if (candidate.getUsername().equals(username)) {
                return candidate;
            }
        }
        return null;
    }

    /**
     * Returns the controller of the main window.
     *
     * @return the controller
     */
    public MainController getMain() {
        return builder.getController();
    }

    /**
     * Returns the root of the window, above the sidebar and the sheets.
     *
     * @return the scene's root
     */
    public Parent getRoot() {
        return stage.getScene().getRoot();
    }

    /**
     * Returns the state of the window being tested.
     *
     * @return the state
     */
    public UiState getState() {
        return state;
    }

    /**
     * Returns the backend of the window being tested.
     *
     * @return the backend
     */
    public DashboardBackend getBackend() {
        return backend;
    }

    /**
     * Returns the screen loader of the window being tested.
     *
     * @return the builder, which holds the navigator
     */
    public ShellBuilder getBuilder() {
        return builder;
    }

    /**
     * Returns the real window.
     *
     * @return the stage
     */
    public Stage getStage() {
        return stage;
    }

    /**
     * Returns the input driver.
     *
     * @return the driver
     */
    public RobotDriver getDriver() {
        return driver;
    }

    // Runs the demo scenarios and the live pass, and prints the score.
    private int runAll() {
        try {
            driver.pause(SCREEN_WAIT_MILLIS);
            WindowScenarios scenarios = new WindowScenarios(this);
            scenarios.runBasics();
            new WindowFlows(this).runAll();
            scenarios.runWindowChecks();
            livePass();
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

    // Opens every screen with the real services and checks for errors.
    private void livePass() {
        section("Live mode: every screen opens without an error");
        driver.run(() -> {
            try {
                open(new LiveBackend(services), "GradeZZY");
            } catch (IOException exception) {
                throw new IllegalStateException(exception);
            }
        });
        driver.pause(SCREEN_WAIT_MILLIS);

        Screen[] screens = {Screen.OVERVIEW, Screen.MODULES,
            Screen.TEST_CASES, Screen.UPLOAD, Screen.ANOMALY_LOG,
            Screen.RESULTS, Screen.STATISTICS, Screen.PLAGIARISM,
            Screen.HISTORY, Screen.AUDIT_LOG, Screen.SETTINGS, Screen.HELP};
        for (int index = 0; index < SIDEBAR_ITEMS.length; index++) {
            boolean opened = openFromSidebar(SIDEBAR_ITEMS[index],
                    screens[index]);
            boolean noError = driver.findAll(getRoot(), "toast-error")
                    .isEmpty();
            check(opened && noError, "live: " + SIDEBAR_ITEMS[index]
                    + " opens and shows no error");
            boolean pictured = screens[index] == Screen.OVERVIEW
                    || screens[index] == Screen.UPLOAD
                    || screens[index] == Screen.RESULTS;
            if (pictured) {
                capture("30-live-" + screens[index].name().toLowerCase());
            }
        }
    }

    // Finds the row or cell on show whose item matches.
    private Node findRow(String styleClass, RowMatch match) {
        for (Node node : driver.findAll(getRoot(), styleClass)) {
            Object item = driver.onFx(() -> itemOf(node));
            if (match.matches(item)) {
                return node;
            }
        }
        return null;
    }

    // Finds the table or list on show that holds a matching item.
    private Node listHolding(RowMatch match) {
        for (String kind : LIST_CLASSES) {
            for (Node node : driver.findAll(getRoot(), kind)) {
                if (driver.onFx(() -> holdsMatch(node, match))) {
                    return node;
                }
            }
        }
        return null;
    }

    // Says whether a table or list holds a matching item.
    private boolean holdsMatch(Node node, RowMatch match) {
        List<?> items = null;
        if (node instanceof TableView) {
            items = ((TableView<?>) node).getItems();
        } else if (node instanceof ListView) {
            items = ((ListView<?>) node).getItems();
        }
        if (items == null) {
            return false;
        }
        for (Object item : items) {
            if (match.matches(item)) {
                return true;
            }
        }
        return false;
    }

    // Says whether a row is wholly visible, so a click on it lands.
    private boolean fullyShown(Node row) {
        Node holder = row.getParent();
        while (holder != null
                && !holder.getStyleClass().contains(CLIPPED_CLASS)) {
            holder = holder.getParent();
        }
        if (holder == null) {
            return true;
        }
        Bounds rowBounds = row.localToScene(row.getBoundsInLocal());
        Bounds view = holder.localToScene(holder.getLayoutBounds());
        return rowBounds.getMinY() >= view.getMinY() - 1.0
                && rowBounds.getMaxY() <= view.getMaxY() + 1.0;
    }

    // Returns the item of a table row or list cell.
    private Object itemOf(Node node) {
        if (node instanceof TableRow) {
            return ((TableRow<?>) node).getItem();
        }
        if (node instanceof ListCell) {
            return ((ListCell<?>) node).getItem();
        }
        return null;
    }

    // Builds a window with a backend and puts it on the screen.
    private void open(DashboardBackend chosen, String title)
            throws IOException {
        backend = chosen;
        state = ShellBuilder.createState(services.getConfig());
        state.darkProperty().set(false);
        builder = new ShellBuilder(services, backend, state,
                new ThemeManager());
        Scene scene = builder.build(WIDTH, HEIGHT);
        stage.setTitle(title);
        stage.setMinWidth(MIN_WIDTH);
        stage.setMinHeight(MIN_HEIGHT);
        stage.setScene(scene);
        stage.sizeToScene();
        stage.centerOnScreen();
        // Stays on top, so no test click reaches another application.
        stage.setAlwaysOnTop(true);
        stage.show();
        stage.toFront();
        stage.requestFocus();
    }
}
