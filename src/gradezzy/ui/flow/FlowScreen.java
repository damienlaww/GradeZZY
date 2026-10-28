/*
 * File: FlowScreen.java
 *
 * Purpose: What every screen of the guided window has in common: the
 * window it belongs to, a view, and a method called each time it is
 * shown.
 *
 * Where it fits: Each step screen and each page (History, Settings)
 * extends it. FlowWindow calls onShow after emptying the action bar.
 */
package gradezzy.ui.flow;

import javafx.scene.Node;

import gradezzy.ui.backend.DashboardBackend;

/**
 * One screen of the guided window.
 */
public abstract class FlowScreen {

    // The window that shows this screen
    private final FlowWindow window;

    /**
     * Creates a screen for a window.
     *
     * @param window the window
     */
    protected FlowScreen(FlowWindow window) {
        this.window = window;
    }

    /**
     * Returns what the screen shows.
     *
     * @return the view
     */
    public abstract Node getView();

    /**
     * Refreshes the view and fills the action bar. Called each time the
     * screen is shown.
     */
    public abstract void onShow();

    /**
     * Returns the window that shows this screen.
     *
     * @return the window
     */
    protected FlowWindow getWindow() {
        return window;
    }

    /**
     * Returns the backend the screen calls.
     *
     * @return the backend
     */
    protected DashboardBackend getBackend() {
        return window.getBackend();
    }

    /**
     * Returns the shared state.
     *
     * @return the state
     */
    protected FlowState getState() {
        return window.getState();
    }

    /**
     * Says whether this screen is the one the instructor sees now.
     *
     * @return true when on screen
     */
    protected boolean isOnScreen() {
        return window.isOnScreen(this);
    }

    /**
     * Returns the bottom bar.
     *
     * @return the action bar
     */
    protected ActionBar getActions() {
        return window.getActionBar();
    }
}
