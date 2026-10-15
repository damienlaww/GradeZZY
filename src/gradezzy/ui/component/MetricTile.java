/*
 * File: MetricTile.java
 *
 * Purpose: Shows one key number with its name and a short explanation.
 *
 * Where it fits: Used on Overview, Grading Progress and Statistics. The
 * number is always paired with words, so it is never read alone.
 *
 * AI use: drafted with Claude Code, reviewed by Jun Sheng.
 */
package gradezzy.ui.component;

import javafx.scene.control.Label;
import javafx.scene.layout.VBox;

/**
 * One key number.
 *
 * <p>Tier: SHOULD. Owner: Damien.
 */
public final class MetricTile extends VBox {

    // What the number is
    private final Label name = new Label();

    // The number
    private final Label value = new Label();

    // What the number means
    private final Label caption = new Label();

    /**
     * Creates an empty tile, for use in FXML.
     */
    public MetricTile() {
        this("", "", "");
    }

    /**
     * Creates a tile.
     *
     * @param nameText what the number is
     * @param valueText the number
     * @param captionText what the number means
     */
    public MetricTile(String nameText, String valueText,
            String captionText) {
        getStyleClass().add("metric-tile");
        name.getStyleClass().add("metric-name");
        value.getStyleClass().add("metric-value");
        caption.getStyleClass().add("metric-caption");
        caption.setWrapText(true);
        getChildren().addAll(name, value, caption);
        setName(nameText);
        setValue(valueText);
        setCaption(captionText);
    }

    /**
     * Returns what the number is.
     *
     * @return the name
     */
    public String getName() {
        return name.getText();
    }

    /**
     * Sets what the number is.
     *
     * @param text the name
     */
    public void setName(String text) {
        name.setText(text);
        updateAccessibleText();
    }

    /**
     * Returns the number.
     *
     * @return the value
     */
    public String getValue() {
        return value.getText();
    }

    /**
     * Sets the number.
     *
     * @param text the value
     */
    public void setValue(String text) {
        value.setText(text);
        updateAccessibleText();
    }

    /**
     * Returns what the number means.
     *
     * @return the caption
     */
    public String getCaption() {
        return caption.getText();
    }

    /**
     * Sets what the number means.
     *
     * @param text the caption
     */
    public void setCaption(String text) {
        caption.setText(text);
        updateAccessibleText();
    }

    // Reads the tile as one sentence to a screen reader.
    private void updateAccessibleText() {
        setAccessibleText(name.getText() + ": " + value.getText() + ". "
                + caption.getText());
    }
}
