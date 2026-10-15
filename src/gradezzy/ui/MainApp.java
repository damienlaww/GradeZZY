/*
 * File: MainApp.java
 *
 * Purpose: Starts the JavaFX window of GradeZZY.
 *
 * Where it fits: The graphical entry point, started by run.sh and
 * run.bat. It loads the configuration, builds the services through
 * ApplicationContext (the same wiring the console uses), chooses the live
 * or demo backend and shows the main window. The developer flags only
 * dispatch to classes in gradezzy.ui.check:
 *   --check      load every screen in both modes and themes, then exit
 *   --demo       use synthetic demo data instead of the real services
 *   --selftest   run the seven demo flows without a window, then exit
 *   --snapshot   save pictures of every screen, then exit; an optional
 *                folder and comma-separated screen list may follow, and
 *                --fullpage also saves each scrolling page whole
 *   --windowtest drive the real window with real mouse and keyboard
 *                input, then exit; an optional picture folder may follow
 *
 * AI use: the backend choice and developer flags drafted with Claude
 * Code, reviewed by Jun Sheng.
 */
package gradezzy.ui;

import java.io.IOException;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.util.ArrayList;
import java.util.List;

import javafx.application.Application;
import javafx.application.Platform;
import javafx.scene.Scene;
import javafx.scene.control.Alert;
import javafx.stage.Stage;

import gradezzy.app.ApplicationContext;
import gradezzy.config.AppConfig;
import gradezzy.config.ConfigException;
import gradezzy.config.ConfigKeys;
import gradezzy.service.ServiceRegistry;
import gradezzy.ui.backend.DashboardBackend;
import gradezzy.ui.backend.LiveBackend;
import gradezzy.ui.check.ScreenCheck;
import gradezzy.ui.check.SelfTest;
import gradezzy.ui.check.SnapshotWriter;
import gradezzy.ui.check.WindowTest;
import gradezzy.ui.demo.DemoBackend;

/**
 * The JavaFX application class and graphical entry point.
 *
 * <p>Tier: MUST. Owner: Damien.
 */
public class MainApp extends Application {

    /**
     * Command line argument that loads every screen once and then exits,
     * without showing a window. Used to check that all FXML files,
     * stylesheets and controllers load: run.sh --check
     */
    public static final String CHECK_ARGUMENT = "--check";

    /** Command line argument that shows synthetic demo data. */
    public static final String DEMO_ARGUMENT = "--demo";

    /** Command line argument that runs the seven demo flows and exits. */
    public static final String SELF_TEST_ARGUMENT = "--selftest";

    /** Command line argument that saves a picture of every screen. */
    public static final String SNAPSHOT_ARGUMENT = "--snapshot";

    /** With --snapshot, also saves each scrolling page at full height. */
    public static final String FULL_PAGE_ARGUMENT = "--fullpage";

    /**
     * Command line argument that tests the real window with real mouse
     * and keyboard input. Leave the mouse and keyboard alone while it
     * runs.
     */
    public static final String WINDOW_TEST_ARGUMENT = "--windowtest";

    /** The folder pictures are saved to when none is given. */
    public static final String DEFAULT_SNAPSHOT_FOLDER =
            "workspace/ui-snapshots";

    /** The folder window-test pictures are saved to when none is given. */
    public static final String DEFAULT_WINDOW_TEST_FOLDER =
            "workspace/ui-window-test";

    // The smallest window: a 1200 by 800 window at 200 percent scaling
    private static final double MIN_WIDTH = 600.0;
    private static final double MIN_HEIGHT = 400.0;

    // The exit code of a developer flag, given to the operating system
    private static int exitCode = 0;

    // The services of the application; null until start succeeds
    private ApplicationContext context = null;

    /**
     * Starts the JavaFX application.
     *
     * @param args the command line arguments
     */
    public static void main(String[] args) {
        launch(args);
        if (exitCode != 0) {
            System.exit(exitCode);
        }
    }

    /**
     * Builds the application and shows the main window, or runs a
     * developer flag. JavaFX calls this once at start-up.
     *
     * @param stage the main window
     */
    @Override
    public void start(Stage stage) {
        List<String> args = getParameters().getRaw();
        boolean tool = args.contains(CHECK_ARGUMENT)
                || args.contains(SELF_TEST_ARGUMENT)
                || args.contains(SNAPSHOT_ARGUMENT)
                || args.contains(WINDOW_TEST_ARGUMENT);

        try {
            AppConfig config = AppConfig.loadDefault();
            context = new ApplicationContext(config);
            ServiceRegistry services = context.getServices();

            if (args.contains(CHECK_ARGUMENT)) {
                exitCode = new ScreenCheck(services).run();
                Platform.exit();
            } else if (args.contains(SELF_TEST_ARGUMENT)) {
                exitCode = new SelfTest(services).run();
                Platform.exit();
            } else if (args.contains(SNAPSHOT_ARGUMENT)) {
                SnapshotWriter writer = new SnapshotWriter(services,
                        folderAfter(args, SNAPSHOT_ARGUMENT,
                        DEFAULT_SNAPSHOT_FOLDER));
                writer.setScreens(snapshotScreens(args));
                writer.setFullPage(args.contains(FULL_PAGE_ARGUMENT));
                exitCode = writer.run();
                Platform.exit();
            } else if (args.contains(WINDOW_TEST_ARGUMENT)) {
                WindowTest test = new WindowTest(services, stage,
                        folderAfter(args, WINDOW_TEST_ARGUMENT,
                        DEFAULT_WINDOW_TEST_FOLDER));
                test.start(code -> finishWindowTest(code));
            } else {
                showMainWindow(stage, services, config,
                        args.contains(DEMO_ARGUMENT));
            }
        } catch (IOException exception) {
            failToStart(exception.getMessage(), !tool);
        } catch (ConfigException exception) {
            failToStart(exception.getMessage(), !tool);
        }
    }

    /**
     * Releases the database and other resources. JavaFX calls this when
     * the application ends.
     */
    @Override
    public void stop() {
        if (context != null) {
            context.close();
        }
    }

    // Shows the main window, sized from the configuration.
    private void showMainWindow(Stage stage, ServiceRegistry services,
            AppConfig config, boolean demo) throws IOException {
        DashboardBackend backend;
        String title = Screen.MAIN.getTitle();
        if (demo) {
            backend = new DemoBackend(services);
            title = title + " (demo data)";
        } else {
            backend = new LiveBackend(services);
        }

        UiState state = ShellBuilder.createState(config);
        ShellBuilder builder = new ShellBuilder(services, backend, state,
                new ThemeManager());
        Scene scene = builder.build(config.getInt(ConfigKeys.WINDOW_WIDTH),
                config.getInt(ConfigKeys.WINDOW_HEIGHT));

        stage.setTitle(title);
        stage.setMinWidth(MIN_WIDTH);
        stage.setMinHeight(MIN_HEIGHT);
        stage.setScene(scene);
        stage.show();
    }

    // Ends the application with the outcome of the window test.
    private void finishWindowTest(int code) {
        exitCode = code;
        Platform.exit();
    }

    // Returns the folder named after a flag, or the default one.
    private Path folderAfter(List<String> args, String flag,
            String defaultFolder) {
        int index = args.indexOf(flag);
        if (index >= 0 && index + 1 < args.size()
                && !args.get(index + 1).startsWith("--")) {
            return Paths.get(args.get(index + 1));
        }
        return Paths.get(defaultFolder);
    }

    // Returns the screens named after the snapshot folder, such as
    // "RESULTS,PROGRESS", or an empty list for every screen.
    private List<Screen> snapshotScreens(List<String> args) {
        List<Screen> screens = new ArrayList<>();
        int index = args.indexOf(SNAPSHOT_ARGUMENT) + 2;
        if (index < args.size() && !args.get(index).startsWith("--")) {
            for (String name : args.get(index).split(",")) {
                screens.add(Screen.valueOf(name.trim()));
            }
        }
        return screens;
    }

    // Reports a start-up failure and closes the application.
    private void failToStart(String message, boolean showDialog) {
        System.err.println("GradeZZY could not start: " + message);
        exitCode = 1;
        if (showDialog) {
            Alert alert = new Alert(Alert.AlertType.ERROR);
            alert.setTitle("GradeZZY could not start");
            alert.setHeaderText("GradeZZY could not start");
            alert.setContentText(message + "\n\nCheck config.properties, "
                    + "then run the compile script again.");
            alert.showAndWait();
        }
        Platform.exit();
    }
}
