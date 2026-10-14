/*
 * File: ScreenNavigator.java
 *
 * Purpose: Loads the FXML file of a screen and connects its controller to
 * the application services.
 *
 * Where it fits: The only class that uses FXMLLoader. MainApp and
 * MainController ask it for a screen by its Screen constant.
 */
package gradezzy.ui;

import java.io.IOException;
import java.net.URL;

import javafx.fxml.FXMLLoader;
import javafx.scene.Parent;

import gradezzy.service.ServiceRegistry;

/**
 * Loads screens from their FXML files.
 *
 * <p>Tier: MUST. Owner: Seth.
 */
public class ScreenNavigator {

    // Every service of the application, passed to each controller
    private final ServiceRegistry services;

    /**
     * Creates a new screen navigator.
     *
     * @param services every service of the application
     */
    public ScreenNavigator(ServiceRegistry services) {
        this.services = services;
    }

    /**
     * Loads one screen.
     *
     * @param screen the screen to load
     * @return the root node of the screen, ready to be shown
     * @throws IOException if the FXML file is missing or invalid
     */
    public Parent load(Screen screen) throws IOException {
        URL location = ScreenNavigator.class.getResource(
                screen.getFxmlPath());

        if (location == null) {
            throw new IOException("Screen file not found: "
                    + screen.getFxmlPath()
                    + ". Run the compile script again.");
        }

        FXMLLoader loader = new FXMLLoader(location);
        Parent root = loader.load();
        Object controller = loader.getController();

        if (controller instanceof BaseController) {
            BaseController baseController = (BaseController) controller;
            baseController.setServices(services);
        }

        return root;
    }
}
