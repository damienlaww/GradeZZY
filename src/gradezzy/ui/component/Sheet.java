/*
 * File: Sheet.java
 *
 * Purpose: The content of a sheet that slides over the window.
 *
 * Where it fits: MainController shows sheets over the window. Screens
 * build one for each confirmation, form and preview.
 */
package gradezzy.ui.component;

import javafx.geometry.Pos;
import javafx.scene.Node;
import javafx.scene.control.Button;
import javafx.scene.control.Label;
import javafx.scene.control.Tooltip;
import javafx.scene.input.KeyCode;
import javafx.scene.layout.HBox;
import javafx.scene.layout.Priority;
import javafx.scene.layout.Region;
import javafx.scene.layout.VBox;

/**
 * The content of one sheet.
 */
public final class Sheet extends VBox {

    // The CSS class of a filled primary button
    private static final String ACCENT = "accent";

    // The CSS class of a destructive button
    private static final String DANGER = "danger";

    // The usual words on the button that closes a sheet
    private static final String CANCEL = "Cancel";

    // The title
    private final Label title = new Label();

    // The explanation, hidden when empty
    private final Label message = new Label();

    // Holds the optional content
    private final VBox body = new VBox();

    // The row of buttons
    private final HBox buttons = new HBox();

    /**
     * Creates a sheet with a title.
     *
     * @param titleText the title, usually a question or a noun
     */
    public Sheet(String titleText) {
        getStyleClass().add("gz-sheet");
        title.getStyleClass().add("sheet-title");
        title.setText(titleText);
        title.setWrapText(true);
        message.getStyleClass().add("sheet-message");
        message.setWrapText(true);
        message.setVisible(false);
        message.setManaged(false);
        body.getStyleClass().add("sheet-body");
        buttons.getStyleClass().add("sheet-buttons");
        buttons.setAlignment(Pos.CENTER_RIGHT);
        setMaxHeight(Region.USE_PREF_SIZE);
        getChildren().addAll(title, message, body, buttons);
        setAccessibleText(titleText);
    }

    /**
     * Sets the explanation under the title.
     *
     * @param text what will happen and what is kept
     * @return this sheet
     */
    public Sheet setMessage(String text) {
        message.setText(text);
        message.setVisible(!text.isEmpty());
        message.setManaged(!text.isEmpty());
        return this;
    }

    /**
     * Adds content under the explanation.
     *
     * @param node the content
     * @return this sheet
     */
    public Sheet addContent(Node node) {
        body.getChildren().add(node);
        VBox.setVgrow(node, Priority.SOMETIMES);
        return this;
    }

    /**
     * Adds a secondary button, such as Copy, on the left. A button named
     * Cancel becomes the dismiss button.
     *
     * @param label the button text
     * @param onAction what the button does
     * @return the button
     */
    public Button addSecondaryButton(String label, Runnable onAction) {
        if (CANCEL.equals(label)) {
            return addDismissButton(label, onAction);
        }
        Button button = new Button(label);
        button.setOnAction(event -> onAction.run());
        buttons.getChildren().add(button);
        return button;
    }

    /**
     * Adds a button that closes the sheet without acting, such as Cancel.
     * Esc does the same.
     *
     * @param label the button text
     * @param onAction what the button does
     * @return the button
     */
    public Button addDismissButton(String label, Runnable onAction) {
        Button button = new Button(label);
        button.setCancelButton(true);
        button.setOnAction(event -> onAction.run());
        markEscape(button);
        buttons.getChildren().add(button);
        return button;
    }

    /**
     * Gives a button that closes a sheet a tooltip naming the Esc key.
     *
     * @param button the button
     * @return the button
     */
    public static Button markEscape(Button button) {
        button.setTooltip(new Tooltip(button.getText() + " ("
                + KeyCode.ESCAPE.getName() + ")"));
        return button;
    }

    /**
     * Adds the main button on the right of the row.
     *
     * @param label the verb on the button
     * @param destructive true to show it in the error colour
     * @param onAction what the button does
     * @return the button
     */
    public Button addPrimaryButton(String label, boolean destructive,
            Runnable onAction) {
        Button button = new Button(label);
        if (destructive) {
            button.getStyleClass().add(DANGER);
        } else {
            button.getStyleClass().add(ACCENT);
            button.setDefaultButton(true);
        }
        button.setOnAction(event -> onAction.run());
        buttons.getChildren().add(button);
        return button;
    }

    /**
     * Returns the first button, so the caller can give it focus.
     *
     * @return the button, or null when there is none
     */
    public Button getFirstButton() {
        if (buttons.getChildren().isEmpty()) {
            return null;
        }
        return (Button) buttons.getChildren().get(0);
    }
}
