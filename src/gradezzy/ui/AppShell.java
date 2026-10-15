/*
 * File: AppShell.java
 *
 * Purpose: What a screen may ask of the window around it: navigation,
 * messages, sheets and confirmations.
 *
 * Where it fits: MainController implements it and every controller
 * reaches it through BaseController.getShell(), so screens never build
 * their own dialogs or know about each other.
 *
 * AI use: drafted with Claude Code, reviewed by Jun Sheng.
 */
package gradezzy.ui;

import javafx.scene.Node;
import javafx.stage.Window;

import gradezzy.ui.backend.Capability;

/**
 * The window that hosts the screens.
 *
 * <p>Tier: MUST. Owner: Damien.
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
     * @param message a plain sentence in the past tense
     */
    void showToast(String message);

    /**
     * Shows a problem that stays until the instructor closes it.
     *
     * @param message what went wrong and what to do next
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
     * @param title the question, such as "Cancel grading?"
     * @param message what will happen and what is kept
     * @param actionLabel the verb on the confirm button
     * @param destructive true to colour the confirm button as a warning
     * @param onConfirm what to do when the instructor confirms
     */
    void confirm(String title, String message, String actionLabel,
            boolean destructive, Runnable onConfirm);

    /**
     * Asks the instructor to confirm an action, with chosen words on the
     * button that dismisses the sheet.
     *
     * @param title the question
     * @param message what will happen and what is kept
     * @param actionLabel the verb on the confirm button
     * @param dismissLabel the words on the button that changes nothing,
     *        such as "Keep Grading"
     * @param destructive true to colour the confirm button as a warning
     * @param onConfirm what to do when the instructor confirms
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
     * Switches between the light and dark theme without reloading any
     * screen.
     *
     * @param dark true for the dark theme
     */
    void setDark(boolean dark);

    /**
     * Refreshes the context bar and sidebar after the run or section
     * changed.
     */
    void refreshContext();

    /**
     * Returns the window, as the owner of file pickers.
     *
     * @return the window, or null when the shell is not on screen
     */
    Window getWindow();
}
