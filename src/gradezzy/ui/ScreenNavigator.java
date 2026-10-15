/*
 * File: ScreenNavigator.java
 *
 * Purpose: Loads the FXML file of a screen and connects its controller to
 * the application services, the backend, the shared state and the shell.
 *
 * Where it fits: The only class that uses FXMLLoader. MainApp and
 * MainController ask it for a screen by its Screen constant. Each screen
 * is loaded once and kept, so its filters, scroll position and selection
 * survive navigation and theme switches.
 *
 * AI use: caching and attach drafted with Claude Code, reviewed by Jun
 * Sheng.
 */
package gradezzy.ui;

import java.io.IOException;
import java.net.URL;
import java.util.EnumMap;
import java.util.Map;

import javafx.fxml.FXMLLoader;
import javafx.scene.Parent;

import gradezzy.service.ServiceRegistry;
import gradezzy.ui.backend.DashboardBackend;

/**
 * Loads screens from their FXML files.
 *
 * <p>Tier: MUST. Owner: Damien.
 */
public class ScreenNavigator {

    // Every service of the application, passed to each controller
    private final ServiceRegistry services;

    // The backend every controller calls
    private final DashboardBackend backend;

    // The state shared by every screen
    private final UiState state;

    // The window around the screens; set once the main screen exists
    private AppShell shell = null;

    // The root node of each screen loaded so far
    private final Map<Screen, Parent> views = new EnumMap<>(Screen.class);

    // The controller of each screen loaded so far
    private final Map<Screen, BaseController> controllers =
            new EnumMap<>(Screen.class);

    /**
     * Creates a new screen navigator.
     *
     * @param services every service of the application
     * @param backend the backend every controller calls
     * @param state the state shared by every screen
     */
    public ScreenNavigator(ServiceRegistry services,
            DashboardBackend backend, UiState state) {
        this.services = services;
        this.backend = backend;
        this.state = state;
    }

    /**
     * Sets the window around the screens. Call before loading any screen
     * other than MAIN.
     *
     * @param shell the shell
     */
    public void setShell(AppShell shell) {
        this.shell = shell;
    }

    /**
     * Loads one screen from its file, without keeping it. Used by the
     * screen check.
     *
     * @param screen the screen to load
     * @return the root node of the screen, ready to be shown
     * @throws IOException if the FXML file is missing or invalid
     */
    public Parent load(Screen screen) throws IOException {
        FXMLLoader loader = createLoader(screen);
        Parent root = loader.load();
        connect(loader.getController());
        return root;
    }

    /**
     * Returns a screen, loading it the first time.
     *
     * @param screen the screen
     * @return the root node of the screen
     * @throws IOException if the FXML file is missing or invalid
     */
    public Parent getView(Screen screen) throws IOException {
        Parent view = views.get(screen);
        if (view != null) {
            return view;
        }

        FXMLLoader loader = createLoader(screen);
        view = loader.load();
        views.put(screen, view);
        Object controller = loader.getController();
        if (controller instanceof BaseController) {
            controllers.put(screen, (BaseController) controller);
        }
        connect(controller);
        return view;
    }

    /**
     * Returns the controller of a screen, loading the screen the first
     * time.
     *
     * @param screen the screen
     * @return the controller, or null when the screen has none
     * @throws IOException if the FXML file is missing or invalid
     */
    public BaseController getController(Screen screen) throws IOException {
        getView(screen);
        return controllers.get(screen);
    }

    // Creates a loader for a screen's FXML file.
    private FXMLLoader createLoader(Screen screen) throws IOException {
        URL location = ScreenNavigator.class.getResource(
                screen.getFxmlPath());

        if (location == null) {
            throw new IOException("Screen file not found: "
                    + screen.getFxmlPath()
                    + ". Run the compile script again.");
        }
        return new FXMLLoader(location);
    }

    // Gives a new controller what it needs, then lets it set up.
    private void connect(Object controller) {
        if (controller instanceof BaseController) {
            BaseController baseController = (BaseController) controller;
            baseController.setServices(services);
            baseController.attach(backend, state, shell);
            baseController.onAttach();
        }
    }
}
