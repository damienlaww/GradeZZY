/*
 * File: ActionBar.java
 *
 * Purpose: The bar along the bottom of the window: Back on the left, a
 * short status sentence, secondary buttons, and the one blue button that
 * moves to the next step.
 *
 * Where it fits: FlowWindow empties it before showing a screen; the
 * screen then fills it in its onShow method.
 */
package gradezzy.ui.flow;

import javafx.geometry.Pos;
import javafx.scene.Node;
import javafx.scene.control.Button;
import javafx.scene.control.Label;
import javafx.scene.layout.HBox;

/**
 * The bottom bar of the guided window.
 */
public class ActionBar extends HBox {

    // The gap between the parts of the bar
    private static final double GAP = 12.0;

    // Goes back one step; does nothing until a screen sets its action
    private final Button backButton = Parts.secondary("Back",
            () -> doNothing());

    // Says why the blue button is disabled, or what happens next
    private final Label status = Parts.muted("");

    // Holds the secondary buttons
    private final HBox secondary = new HBox(GAP);

    // The one blue button
    private final Button primaryButton = Parts.primary("");

    // What the blue button does
    private Runnable primaryAction = null;

    // The keyboard shortcut of the blue button, shown beside it
    private final Label shortcut = Parts.muted(Parts.shortcut("Enter"));

    /**
     * Creates an empty action bar.
     */
    public ActionBar() {
        super(GAP);
        getStyleClass().add("action-bar");
        primaryButton.setOnAction(event -> firePrimary());
        secondary.setAlignment(Pos.CENTER_RIGHT);
        setAlignment(Pos.CENTER_LEFT);
        shortcut.getStyleClass().addAll("small-text", "shortcut-hint");
        shortcut.setMinWidth(Label.USE_PREF_SIZE);
        Parts.tip(primaryButton, "Shortcut: " + Parts.shortcut("Enter"));
        // The hint shows only while the blue button can be pressed
        shortcut.visibleProperty().bind(primaryButton.visibleProperty()
                .and(primaryButton.disabledProperty().not()));
        shortcut.managedProperty().bind(primaryButton.managedProperty());
        getChildren().addAll(backButton, status, Parts.grow(), secondary,
                shortcut, primaryButton);
        reset();
    }

    /**
     * Hides every part, ready for the next screen to fill.
     */
    public void reset() {
        backButton.setVisible(false);
        backButton.setManaged(false);
        status.setText("");
        status.setGraphic(null);
        secondary.getChildren().clear();
        primaryButton.setVisible(false);
        primaryButton.setManaged(false);
        primaryAction = null;
    }

    /**
     * Shows Back with an action.
     *
     * @param action what Back does
     */
    public void setBack(Runnable action) {
        setBack("Back", action);
    }

    /**
     * Shows the left button with other text, such as Cancel while a
     * batch is being graded.
     *
     * @param text the button text
     * @param action what the button does
     */
    public void setBack(String text, Runnable action) {
        backButton.setText(text);
        backButton.setOnAction(event -> action.run());
        backButton.setVisible(true);
        backButton.setManaged(true);
    }

    /**
     * Sets the status sentence next to Back.
     *
     * @param text the sentence, or an empty string
     */
    public void setStatus(String text) {
        status.setText(text);
    }

    /**
     * Shows a small mark before the status sentence, such as the blue dot
     * of an unsaved change. Cleared by reset.
     *
     * @param graphic the mark, or null for none
     */
    public void setStatusGraphic(Node graphic) {
        status.setGraphic(graphic);
    }

    /**
     * Adds a secondary button to the left of the blue button.
     *
     * @param button the button
     */
    public void addSecondary(Button button) {
        secondary.getChildren().add(button);
    }

    /**
     * Shows the blue button.
     *
     * @param text the button text
     * @param action what it does
     * @param enabled whether it can be pressed now
     */
    public void setPrimary(String text, Runnable action, boolean enabled) {
        primaryButton.setText(text);
        primaryButton.setDisable(!enabled);
        primaryButton.setVisible(true);
        primaryButton.setManaged(true);
        primaryAction = action;
    }

    /**
     * Enables or disables the blue button.
     *
     * @param enabled whether it can be pressed now
     */
    public void setPrimaryEnabled(boolean enabled) {
        primaryButton.setDisable(!enabled);
    }

    /**
     * Presses the blue button if it is shown and enabled. The keyboard
     * shortcut calls this.
     */
    public void firePrimary() {
        if (primaryAction == null || primaryButton.isDisabled()
                || !primaryButton.isVisible()) {
            return;
        }
        primaryAction.run();
    }

    /**
     * The action of Back before a screen sets one: nothing happens.
     */
    private void doNothing() {
        // Back is hidden until a screen calls setBack
    }

    /**
     * Returns the blue button, for the developer checks.
     *
     * @return the button
     */
    public Button getPrimaryButton() {
        return primaryButton;
    }
}
