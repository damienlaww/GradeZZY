/*
 * File: MainApp.java
 *
 * Purpose: Starts the JavaFX window of GradeZZY.
 *
 * Where it fits: Started by run.sh and run.bat. Shows the main window with
 * the live or demo backend, or runs a developer check in gradezzy.ui.check.
 */
package gradezzy.ui;

import java.io.IOException;
import java.nio.file.Path;
import java.nio.file.Paths;
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
import gradezzy.ui.check.FlowCheck;
import gradezzy.ui.check.FlowSelfTest;
import gradezzy.ui.check.FlowWindowTest;
import gradezzy.ui.demo.DemoBackend;
import gradezzy.ui.flow.FlowWindow;

/**
 * The JavaFX application class and graphical entry point.
 */
public class MainApp extends Application {

    /** Command line argument that loads every screen once and exits. */
    public static final String CHECK_ARGUMENT = "--check";

    /** Command line argument that shows synthetic demo data. */
    public static final String DEMO_ARGUMENT = "--demo";

    /** Command line argument that runs the seven demo flows and exits. */
    public static final String SELF_TEST_ARGUMENT = "--selftest";

    /** Command line argument that saves a picture of every screen. */
    public static final String SNAPSHOT_ARGUMENT = "--snapshot";

    /** Command line argument that tests the window with real input. */
    public static final String WINDOW_TEST_ARGUMENT = "--windowtest";

    /** The folder pictures are saved to when none is given. */
    public static final String DEFAULT_SNAPSHOT_FOLDER =
            "workspace/ui-snapshots";

    /** The folder window-test pictures are saved to when none is given. */
    public static final String DEFAULT_WINDOW_TEST_FOLDER =
            "workspace/ui-window-test";

    // The title of the window
    private static final String WINDOW_TITLE = "GradeZZY";

    // The smallest window: a 1200 by 800 screen at 200 percent scaling
    private static final double MIN_WIDTH = 600.0;
    private static final double MIN_HEIGHT = 400.0;

    // The exit code of a developer flag
    private static int exitCode = 0;

    // The services; null until start succeeds
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
     * Shows the main window, or runs a developer flag. Called once by JavaFX.
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
                exitCode = new FlowCheck(services, null).run();
                Platform.exit();
            } else if (args.contains(SNAPSHOT_ARGUMENT)) {
                exitCode = new FlowCheck(services, folderAfter(args,
                        SNAPSHOT_ARGUMENT, DEFAULT_SNAPSHOT_FOLDER)).run();
                Platform.exit();
            } else if (args.contains(SELF_TEST_ARGUMENT)) {
                exitCode = new FlowSelfTest(services).run();
                Platform.exit();
            } else if (args.contains(WINDOW_TEST_ARGUMENT)) {
                FlowWindowTest test = new FlowWindowTest(services, stage,
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
     * Releases the database and other resources when the app ends.
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
        String title = WINDOW_TITLE;
        if (demo) {
            backend = new DemoBackend(services);
            title = title + " (demo data)";
        } else {
            backend = new LiveBackend(services);
        }

        boolean dark = ThemeManager.isDark(config.getString(
                ConfigKeys.UI_THEME));
        FlowWindow window = new FlowWindow(backend, dark);
        window.setHostServices(getHostServices());
        Scene scene = window.build(config.getInt(ConfigKeys.WINDOW_WIDTH),
                config.getInt(ConfigKeys.WINDOW_HEIGHT));

        stage.setTitle(title);
        stage.setMinWidth(MIN_WIDTH);
        stage.setMinHeight(MIN_HEIGHT);
        stage.setScene(scene);
        stage.setOnCloseRequest(event -> window.closeRequested(event));
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
