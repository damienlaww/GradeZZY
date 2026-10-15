/*
 * File: Responsive.java
 *
 * Purpose: Shows or hides a screen's inspector as the window narrows,
 * and gives it a toolbar toggle when it is hidden.
 *
 * Where it fits: Screens with an inspector call bindInspector once in
 * onAttach. Above 1100 pixels the inspector sits beside the list; below
 * that it hides behind the toggle so the list keeps its width.
 *
 * AI use: drafted with Claude Code, reviewed by Jun Sheng.
 */
package gradezzy.ui.component;

import javafx.beans.binding.Bindings;
import javafx.beans.binding.BooleanBinding;
import javafx.beans.property.BooleanProperty;
import javafx.scene.Node;
import javafx.scene.Parent;
import javafx.scene.control.ButtonBase;
import javafx.scene.control.ScrollPane;
import javafx.scene.control.SplitPane;
import javafx.scene.control.Tab;
import javafx.scene.control.TabPane;
import javafx.scene.control.ToggleButton;
import javafx.scene.layout.ColumnConstraints;
import javafx.scene.layout.GridPane;
import javafx.scene.layout.Priority;
import javafx.scene.layout.Region;

/**
 * Layout rules for narrow windows.
 *
 * <p>Tier: SHOULD. Owner: Damien.
 */
public final class Responsive {

    // The whole width, in percent
    private static final double FULL_WIDTH = 100.0;

    // Utility class, never instantiated.
    private Responsive() {
        // nothing to set up
    }

    /**
     * Shows the inspector beside the list in a wide window, and behind a
     * toggle in a compact one.
     *
     * @param inspector the inspector pane
     * @param toggle the toolbar button that opens it when compact
     * @param compact true while the window is compact
     * @param requested shared across screens: whether the instructor
     *        opened the inspector
     */
    public static void bindInspector(Node inspector, ToggleButton toggle,
            BooleanProperty compact, BooleanProperty requested) {
        toggle.selectedProperty().bindBidirectional(requested);
        toggle.visibleProperty().bind(compact);
        toggle.managedProperty().bind(compact);

        BooleanBinding shown = Bindings.or(compact.not(), requested);
        inspector.visibleProperty().bind(shown);
        inspector.managedProperty().bind(shown);
    }

    /**
     * Lays two panes side by side, and stacks the second under the first
     * when the grid is narrower than a break width.
     *
     * @param grid the grid that holds both panes
     * @param left the pane on the left, or on top when stacked
     * @param right the pane on the right, or below when stacked
     * @param leftPercent the share of the width given to the left pane
     * @param breakWidth the width below which the panes stack
     */
    public static void twoColumns(GridPane grid, Node left, Node right,
            double leftPercent, double breakWidth) {
        ColumnConstraints first = new ColumnConstraints();
        ColumnConstraints second = new ColumnConstraints();
        first.setHgrow(Priority.ALWAYS);
        second.setHgrow(Priority.ALWAYS);
        // Columns that may shrink let the grid follow a narrow window
        // instead of holding the page wide, so it can reach the break
        // width and stack.
        first.setMinWidth(0.0);
        second.setMinWidth(0.0);
        grid.getColumnConstraints().setAll(first, second);
        GridPane.setConstraints(left, 0, 0);
        GridPane.setVgrow(left, Priority.ALWAYS);
        GridPane.setVgrow(right, Priority.ALWAYS);

        double gap = grid.getHgap();
        grid.widthProperty().addListener((observable, oldValue, newValue)
                -> arrange(grid, right, first, second, leftPercent, gap,
                newValue.doubleValue() < breakWidth));
        arrange(grid, right, first, second, leftPercent, gap, false);
    }

    // Puts the right pane beside or under the left one. A stacked grid
    // has one column, so it keeps no gap between columns.
    private static void arrange(GridPane grid, Node right,
            ColumnConstraints first, ColumnConstraints second,
            double leftPercent, double gap, boolean stacked) {
        if (stacked) {
            first.setPercentWidth(FULL_WIDTH);
            second.setPercentWidth(0.0);
            grid.setHgap(0.0);
            GridPane.setConstraints(right, 0, 1);
        } else {
            first.setPercentWidth(leftPercent);
            second.setPercentWidth(FULL_WIDTH - leftPercent);
            grid.setHgap(gap);
            GridPane.setConstraints(right, 1, 0);
        }
        grid.requestLayout();
    }

    /**
     * Stops every button under a node from shrinking below its text, so
     * a narrow window wraps text around buttons instead of cutting their
     * labels.
     *
     * @param root the top of the tree to walk
     */
    public static void keepButtonsWhole(Node root) {
        if (root instanceof ButtonBase) {
            ((ButtonBase) root).setMinWidth(Region.USE_PREF_SIZE);
        }

        // Controls without a skin yet do not list their content as
        // children, so scroll panes, tab panes and split panes are
        // opened by hand.
        if (root instanceof ScrollPane) {
            Node content = ((ScrollPane) root).getContent();
            if (content != null) {
                keepButtonsWhole(content);
            }
        } else if (root instanceof TabPane) {
            for (Tab tab : ((TabPane) root).getTabs()) {
                if (tab.getContent() != null) {
                    keepButtonsWhole(tab.getContent());
                }
            }
        } else if (root instanceof SplitPane) {
            for (Node item : ((SplitPane) root).getItems()) {
                keepButtonsWhole(item);
            }
        } else if (root instanceof Parent) {
            for (Node child : ((Parent) root).getChildrenUnmodifiable()) {
                keepButtonsWhole(child);
            }
        }
    }
}
