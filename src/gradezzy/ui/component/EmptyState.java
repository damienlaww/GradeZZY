/*
 * File: EmptyState.java
 *
 * Purpose: Explains why a list or panel is empty and offers the next step.
 *
 * Where it fits: Shown by any screen with no data yet, a filter with no
 * match, or a backend that still holds a placeholder.
 */
package gradezzy.ui.component;

import javafx.geometry.Pos;
import javafx.scene.control.Button;
import javafx.scene.control.Label;
import javafx.scene.layout.VBox;

/**
 * A message for an empty list.
 */
public final class EmptyState extends VBox {

    // The size of the icon above the title
    private static final double ICON_SIZE = 28.0;

    // The title
    private final Label title = new Label();

    // The explanation
    private final Label message = new Label();

    // The next step, hidden until setAction is called
    private final Button action = new Button();

    /**
     * Creates an empty state.
     *
     * @param icon the icon above the title
     * @param titleText what is empty
     * @param messageText why, and what to do next
     */
    public EmptyState(Icon icon, String titleText, String messageText) {
        getStyleClass().add("empty-state");
        setAlignment(Pos.CENTER);

        title.getStyleClass().add("empty-title");
        title.setText(titleText);
        message.getStyleClass().add("empty-message");
        message.setText(messageText);
        message.setWrapText(true);

        action.setVisible(false);
        action.setManaged(false);
        getChildren().addAll(icon.create(ICON_SIZE), title, message,
                action);
    }

    /**
     * Shows a button for the next step.
     *
     * @param label the verb on the button
     * @param onAction what the button does
     */
    public void setAction(String label, Runnable onAction) {
        action.setText(label);
        action.setOnAction(event -> onAction.run());
        action.setVisible(true);
        action.setManaged(true);
    }

    /**
     * Changes the title and explanation.
     *
     * @param titleText the new title
     * @param messageText the new explanation
     */
    public void setText(String titleText, String messageText) {
        title.setText(titleText);
        message.setText(messageText);
    }
}
