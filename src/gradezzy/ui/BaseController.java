/*
 * File: BaseController.java
 *
 * Purpose: Shared code for every screen controller.
 *
 * Where it fits: Each controller extends this class. ScreenNavigator gives
 * it the ServiceRegistry after loading the FXML file, so a controller
 * reaches all application logic through getServices() and holds no logic
 * of its own.
 */
package gradezzy.ui;

import gradezzy.service.ServiceRegistry;

/**
 * The parent of every screen controller.
 *
 * <p>Tier: MUST. Owner: Damien.
 */
public abstract class BaseController {

    // Every service of the application; set after the FXML is loaded
    private ServiceRegistry services = null;

    /**
     * Creates a controller. JavaFX calls this when it loads the FXML.
     */
    protected BaseController() {
        // nothing to set up
    }

    /**
     * Gives the controller access to the application services. Called by
     * ScreenNavigator, after initialize().
     *
     * @param services every service of the application
     */
    public void setServices(ServiceRegistry services) {
        this.services = services;
    }

    /**
     * Returns the application services.
     *
     * @return the service registry, or null before setServices is called
     */
    protected ServiceRegistry getServices() {
        return services;
    }

    /**
     * Tells the instructor that something went wrong, in plain words and
     * with what to do next (usability heuristic 9).
     *
     * @param message what went wrong and how to recover
     */
    protected void showError(String message) {
        // TODO(Damien): implement: an error dialog or inline message, once
        //     the team has chosen the design
    }

    /**
     * Tells the instructor that an action succeeded.
     *
     * @param message what was done
     */
    protected void showInfo(String message) {
        // TODO(Damien): implement: a confirmation message or notification
    }
}
