/*
 * File: AppShell.java
 *
 * Purpose: What a screen may ask of the window around it.
 *
 * Where it fits: MainController implements it. Controllers reach it through
 * BaseController.getShell() instead of building their own dialogs.
 */
package gradezzy.ui;

import javafx.scene.Node;
import javafx.stage.Window;

import gradezzy.ui.backend.Capability;

/**
 * The window that hosts the screens.
 */
public interface AppShell {

    /**
     * Shows a screen and keeps the state of the one left.
     *
     * @param screen the screen to show
     */
    void navigate(Screen screen);

    /**
     * Shows a short message that disappears by itself.
     *
     * @param message a sentence in the past tense
     */
    void showToast(String message);

    /**
     * Shows a problem that stays until the instructor closes it.
     *
     * @param message what went wrong and what to do
     */
    void showError(String message);

    /**
     * Shows content in a sheet over the window.
     *
     * @param content the content, usually a component.Sheet
     */
    void showSheet(Node content);

    /**
     * Closes the sheet on show.
     */
    void closeSheet();

    /**
     * Asks the instructor to confirm an action in a sheet.
     *
     * @param title the question
     * @param message what will happen
     * @param actionLabel the verb on the confirm button
     * @param destructive true for a warning colour
     * @param onConfirm run when the instructor confirms
     */
    void confirm(String title, String message, String actionLabel,
            boolean destructive, Runnable onConfirm);

    /**
     * Asks the instructor to confirm an action, with a custom dismiss button.
     *
     * @param title the question
     * @param message what will happen
     * @param actionLabel the verb on the confirm button
     * @param dismissLabel the words on the dismiss button
     * @param destructive true for a warning colour
     * @param onConfirm run when the instructor confirms
     */
    void confirm(String title, String message, String actionLabel,
            String dismissLabel, boolean destructive, Runnable onConfirm);

    /**
     * Explains that a feature has no backend yet, naming its owner.
     *
     * @param capability the missing feature
     */
    void showNotConnected(Capability capability);

    /**
     * Shows read-only text, such as an export preview, in a sheet.
     *
     * @param title the sheet title
     * @param note one sentence about the text
     * @param text the text
     */
    void showPreview(String title, String note, String text);

    /**
     * Switches the theme without reloading any screen.
     *
     * @param dark true for the dark theme
     */
    void setDark(boolean dark);

    /**
     * Refreshes the context bar and sidebar after the run or section changes.
     */
    void refreshContext();

    /**
     * Returns the window, as the owner of file pickers.
     *
     * @return the window, or null when the shell is not on screen
     */
    Window getWindow();
}
