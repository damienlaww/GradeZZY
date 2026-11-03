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

import javafx.scene.Node;
import javafx.scene.control.Label;
import javafx.scene.layout.HBox;
import javafx.scene.layout.VBox;

/**
 * One group of settings: its card, its entry in the list and its rows.
 *
 * <p>Tier: SHOULD.
 */
public class SettingsGroup {

    // The entry in the list on the left
    private final Label indexItem;

    // The card on the right
    private final VBox card;

    // The rows of the card
    private final VBox rowBox = new VBox();

    // Each row, top to bottom
    private final List<HBox> rows = new ArrayList<>();

    // The setting of each row, or null for the questions table
    private final List<SettingField> rowFields = new ArrayList<>();

    // The words the questions row matches, if this group has it
    private String questionsText = "";

    /**
     * Creates an empty group.
     *
     * @param title the name of the group
     */
    public SettingsGroup(String title) {
        indexItem = new Label(title);
        indexItem.getStyleClass().add("settings-index-item");
        indexItem.setMaxWidth(Double.MAX_VALUE);
        card = Parts.card(title, new Node[] {rowBox});
    }

    /**
     * Returns the entry in the list on the left.
     *
     * <p>Tier: SHOULD.
     *
     * @return the entry
     */
    public Label getIndexItem() {
        return indexItem;
    }

    /**
     * Returns the card on the right.
     *
     * <p>Tier: SHOULD.
     *
     * @return the card
     */
    public VBox getCard() {
        return card;
    }

    /**
     * Sets the words the questions row matches in a search.
     *
     * <p>Tier: SHOULD.
     *
     * @param text the words, in lower case
     */
    public void setQuestionsText(String text) {
        questionsText = text;
    }

    /**
     * Adds a row under the others.
     *
     * <p>Tier: SHOULD.
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
     * <p>Tier: SHOULD.
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
     *
     * <p>Tier: SHOULD.
     */
    public void markEnds() {
        HBox last = null;
        for (HBox row : rows) {
            row.getStyleClass().removeAll("first", "last");
            if (row.isVisible()) {
                if (last == null) {
                    row.getStyleClass().add("first");
                }
                last = row;
            }
        }
        if (last != null) {
            last.getStyleClass().add("last");
        }
    }
}
