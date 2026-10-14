/*
 * File: MainApp.java
 *
 * Purpose: Starts the JavaFX window of GradeZZY.
 *
 * Where it fits: The graphical entry point, started by run.sh and
 * run.bat. It loads the configuration, builds the services through
 * ApplicationContext (the same wiring the console uses), applies the
 * theme and shows the main screen.
 */
package gradezzy.ui;

import java.io.IOException;

import javafx.application.Application;
import javafx.application.Platform;
import javafx.scene.Parent;
import javafx.scene.Scene;
import javafx.stage.Stage;

import gradezzy.app.ApplicationContext;
import gradezzy.config.AppConfig;
import gradezzy.config.ConfigException;
import gradezzy.config.ConfigKeys;

/**
 * The JavaFX application class and graphical entry point.
 *
 * <p>Tier: MUST. Owner: Damien.
 */
public class MainApp extends Application {

    /**
     * Command line argument that loads every screen once and then exits,
     * without showing a window. Used to check that all FXML files and
     * controllers load: run.sh --check
     */
    public static final String CHECK_ARGUMENT = "--check";

    // The services of the application; null until start succeeds
    private ApplicationContext context = null;

    /**
     * Starts the JavaFX application.
     *
     * @param args the command line arguments
     */
    public static void main(String[] args) {
        launch(args);
    }

    /**
     * Builds the application and shows the main window. JavaFX calls this
     * once at start-up.
     *
     * @param stage the main window
     */
    @Override
    public void start(Stage stage) {
        try {
            AppConfig config = AppConfig.loadDefault();
            context = new ApplicationContext(config);

            ThemeManager themeManager = new ThemeManager();
            themeManager.apply(config.getString(ConfigKeys.UI_THEME));

            ScreenNavigator navigator = new ScreenNavigator(
                    context.getServices());

            if (getParameters().getRaw().contains(CHECK_ARGUMENT)) {
                checkScreens(navigator);
                Platform.exit();
            } else {
                showMainWindow(stage, navigator, config);
            }
        } catch (IOException exception) {
            failToStart(exception.getMessage());
        } catch (ConfigException exception) {
            failToStart(exception.getMessage());
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

    // Shows the main screen in the window, sized from the configuration.
    private void showMainWindow(Stage stage, ScreenNavigator navigator,
            AppConfig config) throws IOException {
        Parent root = navigator.load(Screen.MAIN);
        Scene scene = new Scene(root,
                config.getInt(ConfigKeys.WINDOW_WIDTH),
                config.getInt(ConfigKeys.WINDOW_HEIGHT));

        stage.setTitle(Screen.MAIN.getTitle());
        stage.setScene(scene);
        stage.show();
    }

    // Loads every screen once so a broken FXML file is found early.
    private void checkScreens(ScreenNavigator navigator)
            throws IOException {
        for (Screen screen : Screen.values()) {
            navigator.load(screen);
            System.out.println("Loaded screen: " + screen);
        }

        System.out.println("All " + Screen.values().length
                + " screens loaded.");
    }

    // Reports a start-up failure and closes the application.
    private void failToStart(String message) {
        // TODO(Damien): implement: show this in an error dialog as well
        System.err.println("GradeZZY could not start: " + message);
        Platform.exit();
    }
}
