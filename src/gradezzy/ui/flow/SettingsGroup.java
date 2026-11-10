/*
 * File: SettingsGroup.java
 *
 * Purpose: One group of settings on the Settings tab, such as "Course"
 * or "Running code": its card, its entry in the list on the left and
 * its rows.
 *
 * Where it fits: SettingsScreen creates one per group, adds the rows of
 * its settings, and asks it to filter them when the search changes.
 */
package gradezzy.ui.flow;

import java.util.ArrayList;
import java.util.List;

import javafx.animation.FadeTransition;
import javafx.scene.Node;
import javafx.scene.control.Label;
import javafx.scene.layout.HBox;
import javafx.scene.layout.Region;
import javafx.scene.layout.VBox;
import javafx.util.Duration;

/**
 * One group of settings: its card, its entry in the list and its rows.
 *
 * <p>Tier: SHOULD.
 */
public class SettingsGroup {

    // How long a card stays lit up after its settings change
    private static final double FLASH_MILLIS = 1000.0;

    // The opacity of the light-up layer at its brightest, and when off
    private static final double LIT = 1.0;
    private static final double UNLIT = 0.0;

    // The entry in the list on the left
    private final Label indexItem;

    // The card on the right
    private final VBox card;

    // The title at the top of the card
    private final Label titleLabel;

    // The rows of the card
    private final VBox rowBox = new VBox();

    // Each row, top to bottom
    private final List<HBox> rows = new ArrayList<>();

    // The setting of each row, or null for the questions table
    private final List<SettingField> rowFields = new ArrayList<>();

    // The words the questions row matches, if this group has it
    private String questionsText = "";

    // Lights the card up when the settings it shows change
    private final Region flashLayer = new Region();

    // Fades the light-up layer out again
    private final FadeTransition flashFade = new FadeTransition(
            Duration.millis(FLASH_MILLIS), flashLayer);

    /**
     * Creates an empty group.
     *
     * @param title the name of the group
     */
    public SettingsGroup(String title) {
        indexItem = new Label(title);
        indexItem.getStyleClass().add("settings-index-item");
        indexItem.setMaxWidth(Double.MAX_VALUE);
        titleLabel = Parts.title(title);
        card = Parts.card(null, new Node[] {titleLabel, rowBox});
    }

    /**
     * Changes the title of the card, such as to name the assessment its
     * settings belong to. The entry in the list keeps its short name.
     *
     * @param title the new title
     */
    public void setTitle(String title) {
        titleLabel.setText(title);
    }

    /**
     * Returns the entry in the list on the left.
     *
     * @return the entry
     */
    public Label getIndexItem() {
        return indexItem;
    }

    /**
     * Returns the card on the right.
     *
     * @return the card
     */
    public VBox getCard() {
        return card;
    }

    /**
     * Marks the group as one that changes with the module or the
     * assessment chosen: an accent edge on the card's left, a note under
     * its title, and a layer that can light the card up for a moment.
     *
     * @param note the note, such as which module the settings are for
     */
    public void markChanging(Label note) {
        Node title = card.getChildren().remove(0);
        VBox head = new VBox(Parts.GAP_TINY, title, note);
        head.getStyleClass().add("settings-card-head");
        card.getChildren().add(0, head);
        card.getStyleClass().add("scope-card");

        // Behind the card's content, sized to the card by hand as the
        // card does not lay it out
        flashLayer.getStyleClass().add("scope-flash");
        flashLayer.setManaged(false);
        flashLayer.setMouseTransparent(true);
        flashLayer.setOpacity(UNLIT);
        card.widthProperty().addListener((observable, before, after)
                -> fitFlashLayer());
        card.heightProperty().addListener((observable, before, after)
                -> fitFlashLayer());
        card.getChildren().add(0, flashLayer);
        flashFade.setFromValue(LIT);
        flashFade.setToValue(UNLIT);
    }

    /**
     * Lights the card up for a moment, then lets it fade back, to show
     * that its settings just changed. Does nothing for a group not
     * marked as changing, or one hidden by a search.
     */
    public void flash() {
        if (flashLayer.getParent() == null || !card.isVisible()) {
            return;
        }
        flashFade.stop();
        flashLayer.setOpacity(LIT);
        flashFade.playFromStart();
    }

    // Sizes the light-up layer to the whole card.
    private void fitFlashLayer() {
        flashLayer.resizeRelocate(0, 0, card.getWidth(), card.getHeight());
    }

    /**
     * Sets the words the questions row matches in a search.
     *
     * @param text the words, in lower case
     */
    public void setQuestionsText(String text) {
        questionsText = text;
    }

    /**
     * Adds a row under the others.
     *
     * @param row the row
     * @param field the setting of the row, or null for the questions
     *        table
     */
    public void addRow(HBox row, SettingField field) {
        rows.add(row);
        rowFields.add(field);
        rowBox.getChildren().add(row);
        markEnds();
    }

    /**
     * Shows the rows that match a search.
     *
     * @param query the search in lower case; empty matches every row
     * @return true when any row is shown
     */
    public boolean filter(String query) {
        boolean any = false;
        for (int i = 0; i < rows.size(); i++) {
            SettingField field = rowFields.get(i);
            boolean shown = query.isEmpty();
            if (field != null) {
                shown = field.matches(query);
            } else if (!shown) {
                shown = questionsText.contains(query);
            }
            rows.get(i).setVisible(shown);
            rows.get(i).setManaged(shown);
            if (shown) {
                any = true;
            }
        }
        markEnds();
        return any;
    }

    /**
     * Gives the first shown row no line above it, and the first and last
     * shown rows no gap against the card's edge.
     */
    public void markEnds() {
        List<HBox> shown = new ArrayList<>();
        for (HBox row : rows) {
            row.getStyleClass().removeAll("first", "last");
            if (row.isVisible()) {
                shown.add(row);
            }
        }
        if (!shown.isEmpty()) {
            int lastIndex = shown.size() - 1;
            shown.get(0).getStyleClass().add("first");
            shown.get(lastIndex).getStyleClass().add("last");
        }
    }
}
