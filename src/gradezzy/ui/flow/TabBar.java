/*
 * File: TabBar.java
 *
 * Purpose: The row of tabs in the middle of the top bar. The tab of the
 * page on screen is underlined in blue.
 *
 * Where it fits: FlowWindow puts it in the top bar and calls select when
 * the page changes.
 */
package gradezzy.ui.flow;

import java.util.HashMap;
import java.util.Map;
import java.util.function.Consumer;

import javafx.geometry.Pos;
import javafx.scene.control.Button;
import javafx.scene.layout.HBox;

/**
 * The tabs Grading, Statistics, Runs and Settings.
 */
public class TabBar extends HBox {

    // The size of the icon before each tab name
    private static final double ICON_SIZE = 15.0;

    // The gap between tabs
    private static final double TAB_GAP = 4.0;

    // One button per page
    private final Map<Page, Button> tabs = new HashMap<>();

    /**
     * Creates the tabs.
     *
     * @param onPick called with the page the instructor clicked
     */
    public TabBar(Consumer<Page> onPick) {
        super(TAB_GAP);
        getStyleClass().add("tab-bar");
        setAlignment(Pos.CENTER);
        for (Page page : Page.values()) {
            Button tab = new Button(page.getLabel());
            tab.getStyleClass().setAll("button", "page-tab");
            tab.setGraphic(Parts.icon(page.getIconPath(), ICON_SIZE));
            tab.setFocusTraversable(false);
            tab.setOnAction(event -> onPick.accept(page));
            tabs.put(page, tab);
            getChildren().add(tab);
        }
    }

    /**
     * Underlines the tab of a page.
     *
     * @param page the page on screen
     */
    public void select(Page page) {
        for (Page other : tabs.keySet()) {
            Button tab = tabs.get(other);
            tab.getStyleClass().remove("selected");
            if (other == page) {
                tab.getStyleClass().add("selected");
            }
        }
    }

    /**
     * Returns the button of a page, for the developer checks.
     *
     * @param page the page
     * @return the button
     */
    public Button getTab(Page page) {
        return tabs.get(page);
    }
}
