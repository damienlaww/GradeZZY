/*
 * File: ScreenNavigator.java
 *
 * Purpose: Loads each screen's FXML file and connects its controller.
 *
 * Where it fits: The only class that uses FXMLLoader. ShellBuilder creates
 * it and MainController asks it for screens. Each screen is loaded once
 * and kept, so its filters and selection survive navigation.
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
 */
public class ScreenNavigator {

    // Every service, passed to each controller
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
     * @param services every service
     * @param backend the backend
     * @param state the shared state
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
     * Loads one screen without keeping it, for the screen check.
     *
     * @param screen the screen to load
     * @return the root node of the screen
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
     * Returns a screen's controller, loading the screen the first time.
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
