/*
 * File: NotConnectedPanel.java
 *
 * Purpose: Explains that a feature has no backend yet: what it needs,
 * who owns it and which service method it waits for. It never pretends
 * the action happened.
 *
 * Where it fits: Shown inline on a screen, or in a sheet when the
 * instructor presses a button whose capability is not supported.
 *
 * AI use: drafted with Claude Code, reviewed by Jun Sheng.
 */
package gradezzy.ui.component;

import javafx.geometry.Pos;
import javafx.scene.control.Label;
import javafx.scene.layout.GridPane;
import javafx.scene.layout.HBox;
import javafx.scene.layout.VBox;

import gradezzy.ui.backend.Capability;

/**
 * A Not connected yet message for one capability.
 *
 * <p>Tier: MUST. Owner: Damien.
 */
public final class NotConnectedPanel extends VBox {

    // The size of the plug icon
    private static final double ICON_SIZE = 20.0;

    /**
     * Creates the panel for one capability.
     *
     * @param capability the missing feature
     */
    public NotConnectedPanel(Capability capability) {
        getStyleClass().add("not-connected");

        Label title = new Label("Not connected yet");
        title.getStyleClass().add("panel-title");
        HBox heading = new HBox(Icon.PLUG.create(ICON_SIZE), title);
        heading.getStyleClass().add("not-connected-heading");
        heading.setAlignment(Pos.CENTER_LEFT);

        Label summary = new Label(capability.getTitle() + " has no backend "
                + "yet, so nothing was changed.");
        summary.setWrapText(true);

        GridPane details = new GridPane();
        details.getStyleClass().add("detail-grid");
        addRow(details, 0, "Needs", capability.getNeed());
        addRow(details, 1, "Owner", capability.getOwner());
        addRow(details, 2, "Service", capability.getServiceMethod());
        addRow(details, 3, "Status", capability.getStatus().getLabel());

        getChildren().addAll(heading, summary, details);
        setAccessibleText("Not connected yet. " + capability.getTitle()
                + ". Owner: " + capability.getOwner() + ".");
    }

    // Adds one labelled row to the details.
    private void addRow(GridPane grid, int row, String name, String value) {
        Label nameLabel = new Label(name);
        nameLabel.getStyleClass().add("detail-name");
        Label valueLabel = new Label(value);
        valueLabel.setWrapText(true);
        grid.add(nameLabel, 0, row);
        grid.add(valueLabel, 1, row);
    }
}
