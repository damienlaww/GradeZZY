/*
 * File: SheetLayer.java
 *
 * Purpose: Shows one sheet at a time and blocks the window behind it until
 * the sheet closes.
 *
 * Where it fits: MainController passes every sheet here. It uses plain JavaFX
 * because AtlantaFX 3.0.0's ModalPane calls Interpolator.ofSpline, which
 * JavaFX 25.0.4 does not have.
 */
package gradezzy.ui.view;

import javafx.animation.FadeTransition;
import javafx.animation.Interpolator;
import javafx.animation.ParallelTransition;
import javafx.animation.TranslateTransition;
import javafx.beans.property.BooleanProperty;
import javafx.scene.Node;
import javafx.scene.control.Button;
import javafx.scene.layout.Region;
import javafx.scene.layout.StackPane;
import javafx.scene.layout.VBox;
import javafx.util.Duration;

import gradezzy.ui.component.Sheet;

/**
 * The layer that shows sheets.
 */
public final class SheetLayer {

    // How long a sheet takes to slide in and out
    private static final Duration SHEET_TIME = Duration.millis(180);

    // How far a sheet slides down as it appears
    private static final double SHEET_SLIDE = -18.0;

    // The space kept above and below a sheet
    private static final double SHEET_MARGIN = 72.0;

    // The layer over the window, hidden while no sheet is shown
    private final StackPane layer;

    // Holds the sheet at the top centre of the layer
    private final VBox holder;

    // The window behind the sheet
    private final Node behind;

    // Whether sheets slide and fade
    private final BooleanProperty animations;

    /**
     * Creates the sheet layer.
     *
     * @param layer the layer over the window
     * @param holder holds the sheet
     * @param behind the window behind the sheet
     * @param animations whether sheets slide and fade
     */
    public SheetLayer(StackPane layer, VBox holder, Node behind,
            BooleanProperty animations) {
        this.layer = layer;
        this.holder = holder;
        this.behind = behind;
        this.animations = animations;
        layer.setAccessibleText("Sheet");
    }

    /**
     * Shows content in a sheet and gives its first button the focus.
     *
     * @param content the content, usually a component.Sheet
     */
    public void show(Node content) {
        holder.getChildren().setAll(content);
        if (content instanceof Region) {
            ((Region) content).maxHeightProperty().bind(
                    holder.heightProperty().subtract(SHEET_MARGIN));
        }

        // The window behind the sheet takes no clicks and no focus.
        behind.setDisable(true);
        layer.setVisible(true);
        if (animations.get()) {
            play(content, true, null);
        }

        if (content instanceof Sheet) {
            Button first = ((Sheet) content).getFirstButton();
            if (first != null) {
                first.requestFocus();
            }
        }
    }

    /**
     * Closes the sheet on show.
     */
    public void close() {
        if (!layer.isVisible()) {
            return;
        }
        if (holder.getChildren().isEmpty() || !animations.get()) {
            hide();
            return;
        }
        play(holder.getChildren().get(0), false, () -> hide());
    }

    /**
     * Says whether a sheet is on show.
     *
     * @return true while a sheet covers the window
     */
    public boolean isShown() {
        return layer.isVisible();
    }

    // Slides a sheet in or out and fades the dimmed backdrop with it.
    private void play(Node content, boolean showing, Runnable onFinished) {
        FadeTransition fade = new FadeTransition(SHEET_TIME, layer);
        TranslateTransition slide = new TranslateTransition(SHEET_TIME,
                content);
        if (showing) {
            fade.setFromValue(0.0);
            fade.setToValue(1.0);
            slide.setFromY(SHEET_SLIDE);
            slide.setToY(0.0);
        } else {
            fade.setFromValue(1.0);
            fade.setToValue(0.0);
            slide.setFromY(0.0);
            slide.setToY(SHEET_SLIDE);
        }
        fade.setInterpolator(Interpolator.EASE_OUT);
        slide.setInterpolator(Interpolator.EASE_OUT);
        ParallelTransition both = new ParallelTransition(fade, slide);
        if (onFinished != null) {
            both.setOnFinished(event -> onFinished.run());
        }
        both.play();
    }

    // Removes the sheet and gives the window back its clicks and focus.
    private void hide() {
        layer.setVisible(false);
        layer.setOpacity(1.0);
        holder.getChildren().clear();
        behind.setDisable(false);
    }
}
