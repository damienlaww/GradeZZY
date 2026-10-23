/*
 * File: Responsive.java
 *
 * Purpose: Layout rules that keep screens usable in a narrow window.
 *
 * Where it fits: Called by the screen controllers. Below 1100 pixels
 * (UiState.COMPACT_WIDTH) the inspector hides behind a toolbar toggle so
 * the list keeps its width.
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
 */
public final class Responsive {

    // The whole width, in percent
    private static final double FULL_WIDTH = 100.0;

    // Utility class, never instantiated.
    private Responsive() {
        // nothing to set up
    }

    /**
     * Shows the inspector in a wide window, or behind a toggle when compact.
     *
     * @param inspector the inspector pane
     * @param toggle the toolbar button that opens it
     * @param compact true while the window is compact
     * @param requested whether it was opened, shared by every screen
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
     * Lays two panes side by side, or stacks them below a break width.
     *
     * @param grid the grid holding both panes
     * @param left the pane on the left, or on top when stacked
     * @param right the pane on the right, or below when stacked
     * @param leftPercent the left pane's share of the width
     * @param breakWidth the width below which the panes stack
     */
    public static void twoColumns(GridPane grid, Node left, Node right,
            double leftPercent, double breakWidth) {
        ColumnConstraints first = new ColumnConstraints();
        ColumnConstraints second = new ColumnConstraints();
        first.setHgrow(Priority.ALWAYS);
        second.setHgrow(Priority.ALWAYS);
        // Columns may shrink, so the grid can get narrow enough to stack.
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

    // Puts the right pane beside the left one, or under it with no gap.
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
     * Stops every button under a node from shrinking below its text.
     *
     * @param root the top of the tree to walk
     */
    public static void keepButtonsWhole(Node root) {
        if (root instanceof ButtonBase) {
            ((ButtonBase) root).setMinWidth(Region.USE_PREF_SIZE);
        }

        // Without a skin these panes list no children, so open them by hand.
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
