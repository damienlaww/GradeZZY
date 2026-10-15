/*
 * File: ToastLayer.java
 *
 * Purpose: Shows short messages at the bottom centre of the window:
 * confirmations that disappear by themselves, and problems that stay
 * until the instructor closes them.
 *
 * Where it fits: MainController passes every showToast and showError
 * here. At most three messages are shown; the oldest goes first.
 *
 * AI use: drafted with Claude Code, reviewed by Jun Sheng.
 */
package gradezzy.ui.view;

import atlantafx.base.controls.Notification;
import javafx.animation.FadeTransition;
import javafx.animation.Interpolator;
import javafx.animation.PauseTransition;
import javafx.beans.property.BooleanProperty;
import javafx.geometry.Pos;
import javafx.scene.layout.Region;
import javafx.scene.layout.VBox;
import javafx.util.Duration;

import gradezzy.ui.component.Icon;

/**
 * The layer that shows messages.
 *
 * <p>Tier: SHOULD. Owner: Damien.
 */
public final class ToastLayer {

    // How long a confirmation stays
    private static final Duration TOAST_TIME = Duration.seconds(3);

    // How long a message takes to fade in
    private static final Duration FADE_TIME = Duration.millis(160);

    // The most messages shown at once
    private static final int MAX_TOASTS = 3;

    // The column of messages at the bottom of the window
    private final VBox layer;

    // Whether messages fade in
    private final BooleanProperty animations;

    /**
     * Creates the message layer.
     *
     * @param layer the column of messages at the bottom of the window
     * @param animations whether messages fade in
     */
    public ToastLayer(VBox layer, BooleanProperty animations) {
        this.layer = layer;
        this.animations = animations;
        layer.setAlignment(Pos.BOTTOM_CENTER);
        layer.setMouseTransparent(false);
    }

    /**
     * Shows a confirmation that disappears after three seconds.
     *
     * @param message a plain sentence in the past tense
     */
    public void showMessage(String message) {
        Notification toast = new Notification(message, Icon.CHECK.create());
        toast.getStyleClass().add("toast");
        // A confirmation has nothing to press, so clicks pass through it
        // to the controls it covers until it fades.
        toast.setMouseTransparent(true);
        add(toast);

        PauseTransition wait = new PauseTransition(TOAST_TIME);
        wait.setOnFinished(event -> layer.getChildren().remove(toast));
        wait.play();
    }

    /**
     * Shows a problem that stays until the instructor closes it.
     *
     * @param message what went wrong and what to do next
     */
    public void showProblem(String message) {
        Notification toast = new Notification(message, Icon.ERROR.create());
        toast.getStyleClass().addAll("toast", "toast-error");
        toast.setOnClose(event -> layer.getChildren().remove(toast));
        add(toast);
    }

    // Adds a message to the bottom of the window, dropping the oldest.
    private void add(Notification toast) {
        toast.setMaxWidth(Region.USE_PREF_SIZE);
        toast.setAccessibleText(toast.getMessage());
        if (layer.getChildren().size() >= MAX_TOASTS) {
            layer.getChildren().remove(0);
        }
        layer.getChildren().add(toast);
        if (animations.get()) {
            FadeTransition fade = new FadeTransition(FADE_TIME, toast);
            fade.setFromValue(0.0);
            fade.setToValue(1.0);
            fade.setInterpolator(Interpolator.EASE_OUT);
            fade.play();
        }
    }
}
