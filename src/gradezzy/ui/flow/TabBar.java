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
import javafx.scene.layout.StackPane;
import javafx.scene.shape.Circle;

/**
 * The tabs Home, Grading, Statistics, Runs and Settings.
 *
 * <p>Tier: SHOULD.
 */
public class TabBar extends HBox {

    // The size of the icon before each tab name
    private static final double ICON_SIZE = 15.0;

    // The gap between tabs
    private static final double TAB_GAP = 4.0;

    // The size of the dot on a tab with changes that are not saved
    private static final double DOT_RADIUS = 3.0;

    // One button per page
    private final Map<Page, Button> tabs = new HashMap<>();

    // One dot per page, shown while the page has changes not saved
    private final Map<Page, Circle> dots = new HashMap<>();

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
            Circle dot = new Circle(DOT_RADIUS);
            dot.getStyleClass().add("setting-dot");
            dot.setVisible(false);
            StackPane graphic = new StackPane(Parts.icon(page.getIconPath(),
                    ICON_SIZE), dot);
            StackPane.setAlignment(dot, Pos.TOP_RIGHT);
            tab.setGraphic(graphic);
            dots.put(page, dot);
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
        for (Page other : Page.values()) {
            Button tab = tabs.get(other);
            tab.getStyleClass().remove("selected");
            if (other == page) {
                tab.getStyleClass().add("selected");
            }
        }
    }

    /**
     * Shows or hides the dot on a tab that says the page has changes
     * that are not saved, such as Settings.
     *
     * @param page the page
     * @param pending true to show the dot
     */
    public void setPending(Page page, boolean pending) {
        dots.get(page).setVisible(pending);
    }

    /**
     * Says whether a tab shows the dot of changes not saved, for the
     * developer checks.
     *
     * @param page the page
     * @return true if the dot is shown
     */
    public boolean isPending(Page page) {
        return dots.get(page).isVisible();
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
