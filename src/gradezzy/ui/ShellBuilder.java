/*
 * File: ShellBuilder.java
 *
 * Purpose: Builds the main window: loads the MAIN screen, puts it in a
 * scene, applies the theme and starts the shell.
 *
 * Where it fits: Used by MainApp for the real window, and by the screen
 * check, the self-test and the snapshot writer, which build the same
 * window without showing it.
 *
 * AI use: drafted with Claude Code, reviewed by Jun Sheng.
 */
package gradezzy.ui;

import java.io.IOException;

import javafx.scene.Group;
import javafx.scene.Parent;
import javafx.scene.Scene;

import gradezzy.config.AppConfig;
import gradezzy.config.ConfigKeys;
import gradezzy.service.ServiceRegistry;
import gradezzy.ui.backend.DashboardBackend;

/**
 * Builds the main window.
 *
 * <p>Tier: MUST. Owner: Damien.
 */
public class ShellBuilder {

    // Every service of the application
    private final ServiceRegistry services;

    // The backend every screen calls
    private final DashboardBackend backend;

    // The state shared by every screen
    private final UiState state;

    // Switches the theme
    private final ThemeManager themeManager;

    // Loads and keeps the screens, once built
    private ScreenNavigator navigator = null;

    // The controller of the main window, once built
    private MainController controller = null;

    // The scene holding the window, once built
    private Scene scene = null;

    /**
     * Creates a builder.
     *
     * @param services every service of the application
     * @param backend the backend every screen calls
     * @param state the state shared by every screen
     * @param themeManager switches the theme
     */
    public ShellBuilder(ServiceRegistry services, DashboardBackend backend,
            UiState state, ThemeManager themeManager) {
        this.services = services;
        this.backend = backend;
        this.state = state;
        this.themeManager = themeManager;
    }

    /**
     * Creates the shared state with the theme and animation settings from
     * config.properties.
     *
     * @param config the application settings
     * @return the state
     */
    public static UiState createState(AppConfig config) {
        UiState state = new UiState();
        state.darkProperty().set(ThemeManager.isDark(config.getString(
                ConfigKeys.UI_THEME)));
        state.animationsEnabledProperty().set(config.getBoolean(
                ConfigKeys.ANIMATIONS_ENABLED));
        return state;
    }

    /**
     * Builds the window in a scene of a given size and shows the first
     * screen.
     *
     * @param width the scene width in pixels
     * @param height the scene height in pixels
     * @return the scene
     * @throws IOException if an FXML file or stylesheet is missing
     */
    public Scene build(double width, double height) throws IOException {
        navigator = new ScreenNavigator(services, backend, state);
        Parent root = navigator.getView(Screen.MAIN);
        controller = (MainController) navigator.getController(Screen.MAIN);
        scene = new Scene(root, width, height);
        themeManager.applyTo(scene, state.darkProperty().get());
        controller.start(navigator, themeManager);
        return scene;
    }

    /**
     * Moves the window into a new scene of another size, keeping every
     * screen and its state. Used to render several window sizes.
     *
     * @param width the new width in pixels
     * @param height the new height in pixels
     * @return the new scene
     * @throws IOException if a stylesheet is missing
     */
    public Scene resize(double width, double height) throws IOException {
        Parent root = scene.getRoot();
        scene.setRoot(new Group());
        scene = new Scene(root, width, height);
        themeManager.applyTo(scene, state.darkProperty().get());
        root.applyCss();
        root.layout();
        return scene;
    }

    /**
     * Returns the screen loader.
     *
     * @return the navigator, or null before build
     */
    public ScreenNavigator getNavigator() {
        return navigator;
    }

    /**
     * Returns the controller of the main window.
     *
     * @return the controller, or null before build
     */
    public MainController getController() {
        return controller;
    }

    /**
     * Returns the scene holding the window.
     *
     * @return the scene, or null before build
     */
    public Scene getScene() {
        return scene;
    }
}
