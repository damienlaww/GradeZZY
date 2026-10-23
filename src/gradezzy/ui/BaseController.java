/*
 * File: BaseController.java
 *
 * Purpose: The parent of every screen controller.
 *
 * Where it fits: ScreenNavigator creates each controller from its FXML
 * file. Controllers check input, call DashboardBackend and show results.
 */
package gradezzy.ui;

import gradezzy.service.ServiceRegistry;
import gradezzy.ui.backend.ActionResult;
import gradezzy.ui.backend.DashboardBackend;

/**
 * The parent of every screen controller.
 */
public abstract class BaseController {

    // Every service; set after the FXML is loaded
    private ServiceRegistry services = null;

    // The backend; set after the FXML is loaded
    private DashboardBackend backend = null;

    // The shared state; set after the FXML is loaded
    private UiState state = null;

    // The window around the screen; set after the FXML is loaded
    private AppShell shell = null;

    /**
     * Creates a controller. JavaFX calls this while loading the FXML.
     */
    protected BaseController() {
        // nothing to set up
    }

    /**
     * Gives the controller every service.
     *
     * @param services the services
     */
    public void setServices(ServiceRegistry services) {
        this.services = services;
    }

    /**
     * Gives the controller the backend, the state and the window. Called
     * once, before onAttach.
     *
     * @param backend the backend
     * @param state the shared state
     * @param shell the window
     */
    public void attach(DashboardBackend backend, UiState state,
            AppShell shell) {
        this.backend = backend;
        this.state = state;
        this.shell = shell;
    }

    /**
     * Sets up what needs the backend or the state. Called once after attach.
     */
    public void onAttach() {
        // nothing to set up by default
    }

    /**
     * Refreshes the screen. Called each time the screen appears.
     */
    public void onShow() {
        // nothing to refresh by default
    }

    /**
     * Moves focus to the screen's search field, for Shortcut+F.
     *
     * @return true if the screen has a search field
     */
    public boolean focusSearch() {
        return false;
    }

    /**
     * Returns every service.
     *
     * @return the services
     */
    protected ServiceRegistry getServices() {
        return services;
    }

    /**
     * Returns the backend.
     *
     * @return the backend
     */
    protected DashboardBackend getBackend() {
        return backend;
    }

    /**
     * Returns the shared state.
     *
     * @return the state
     */
    protected UiState getState() {
        return state;
    }

    /**
     * Returns the window around the screen.
     *
     * @return the shell
     */
    protected AppShell getShell() {
        return shell;
    }

    /**
     * Shows an error that stays until the instructor closes it.
     *
     * @param message what went wrong and what to do next
     */
    protected void showError(String message) {
        if (shell != null) {
            shell.showError(message);
        }
    }

    /**
     * Shows a short message that disappears by itself.
     *
     * @param message a short past-tense sentence
     */
    protected void showInfo(String message) {
        if (shell != null) {
            shell.showToast(message);
        }
    }

    /**
     * Shows the outcome of a backend action. A success is also recorded in
     * the audit log.
     *
     * @param result the outcome
     * @param auditAction the audit name, such as "Repair approved"
     * @return true when the action happened
     */
    protected boolean showResult(ActionResult result, String auditAction) {
        if (result.getOutcome() == ActionResult.Outcome.NOT_CONNECTED) {
            if (shell != null) {
                shell.showNotConnected(result.getCapability());
            }
            return false;
        }
        if (result.getOutcome() == ActionResult.Outcome.FAILED) {
            showError(result.getMessage());
            return false;
        }

        backend.recordAudit(auditAction, result.getMessage());
        state.markDataChanged();
        showInfo(result.getMessage());
        return true;
    }
}
