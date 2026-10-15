/*
 * File: BaseController.java
 *
 * Purpose: The parent of every screen controller. Gives each screen the
 * backend, the shared state and the window around it, and shows
 * messages the same way everywhere.
 *
 * Where it fits: ScreenNavigator creates a controller from its FXML file,
 * then calls attach and onAttach. The shell calls onShow each time the
 * screen appears. Controllers hold no grading logic: they check input,
 * call one DashboardBackend method and show the result.
 *
 * AI use: attach, onAttach, onShow and the message methods drafted with
 * Claude Code, reviewed by Jun Sheng.
 */
package gradezzy.ui;

import gradezzy.service.ServiceRegistry;
import gradezzy.ui.backend.ActionResult;
import gradezzy.ui.backend.DashboardBackend;

/**
 * The parent of every screen controller.
 *
 * <p>Tier: MUST. Owner: Damien.
 */
public abstract class BaseController {

    // Every service of the application; set after the FXML is loaded
    private ServiceRegistry services = null;

    // The backend the screen calls; set after the FXML is loaded
    private DashboardBackend backend = null;

    // The state shared by every screen; set after the FXML is loaded
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
     * Gives the controller every service of the application.
     *
     * @param services the services
     */
    public void setServices(ServiceRegistry services) {
        this.services = services;
    }

    /**
     * Gives the controller the backend, the shared state and the window.
     * Called once, after the FXML is loaded and before onAttach.
     *
     * @param backend the backend the screen calls
     * @param state the state shared by every screen
     * @param shell the window around the screen
     */
    public void attach(DashboardBackend backend, UiState state,
            AppShell shell) {
        this.backend = backend;
        this.state = state;
        this.shell = shell;
    }

    /**
     * Sets up whatever needs the backend or the shared state. Called once
     * after attach. Screens override this; the default does nothing.
     */
    public void onAttach() {
        // nothing to set up by default
    }

    /**
     * Refreshes the screen from the backend and the shared state. Called
     * each time the screen appears. Screens override this; the default
     * does nothing.
     */
    public void onShow() {
        // nothing to refresh by default
    }

    /**
     * Moves keyboard focus to the screen's search field, for the
     * Shortcut+F key.
     *
     * @return true when the screen has a search field
     */
    public boolean focusSearch() {
        return false;
    }

    /**
     * Returns every service of the application.
     *
     * @return the services
     */
    protected ServiceRegistry getServices() {
        return services;
    }

    /**
     * Returns the backend the screen calls.
     *
     * @return the backend
     */
    protected DashboardBackend getBackend() {
        return backend;
    }

    /**
     * Returns the state shared by every screen.
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
     * Shows a problem that stays until the instructor closes it.
     *
     * @param message what went wrong and what to do next
     */
    protected void showError(String message) {
        if (shell != null) {
            shell.showError(message);
        }
    }

    /**
     * Shows a short confirmation that disappears by itself.
     *
     * @param message a plain sentence in the past tense
     */
    protected void showInfo(String message) {
        if (shell != null) {
            shell.showToast(message);
        }
    }

    /**
     * Shows the outcome of a backend action: a confirmation, a problem or
     * a Not connected yet sheet. A successful action is also recorded in
     * the audit log.
     *
     * @param result the outcome
     * @param auditAction the short audit name, such as "Repair approved"
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
