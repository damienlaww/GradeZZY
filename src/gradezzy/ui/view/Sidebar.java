/*
 * File: Sidebar.java
 *
 * Purpose: Builds the sidebar, its narrow-window menu and count badges.
 *
 * Where it fits: Created by MainController. Every item navigates through
 * AppShell, so the sidebar knows nothing about the screens.
 */
package gradezzy.ui.view;

import java.util.ArrayList;
import java.util.EnumMap;
import java.util.List;
import java.util.Map;

import javafx.css.PseudoClass;
import javafx.geometry.Insets;
import javafx.geometry.Pos;
import javafx.scene.control.Button;
import javafx.scene.control.Label;
import javafx.scene.control.MenuButton;
import javafx.scene.control.MenuItem;
import javafx.scene.control.Tooltip;
import javafx.scene.layout.StackPane;
import javafx.scene.layout.VBox;

import gradezzy.ui.AppShell;
import gradezzy.ui.Screen;
import gradezzy.ui.component.Icon;

/**
 * The navigation sidebar.
 */
public final class Sidebar {

    // The pseudo-class of the item of the screen on show
    private static final PseudoClass SELECTED =
            PseudoClass.getPseudoClass("selected");

    // The style class of a sidebar button that shows a count
    private static final String HAS_BADGE = "has-badge";

    // The space between a badge and the edge of its row
    private static final Insets BADGE_MARGIN = new Insets(0, 8, 0, 0);

    /**
     * One sidebar item.
     */
    private static final class NavEntry {

        // The screen it opens
        private final Screen screen;

        // The words shown
        private final String label;

        // The icon shown
        private final Icon icon;

        // The group heading above it, or null
        private final String group;

        // Creates a sidebar item.
        NavEntry(Screen screen, String label, Icon icon, String group) {
            this.screen = screen;
            this.label = label;
            this.icon = icon;
            this.group = group;
        }
    }

    // The list of the main items
    private final VBox navList;

    // The list pinned under the main items
    private final VBox footerNav;

    // The menu shown instead of the sidebar in a narrow window
    private final MenuButton navMenu;

    // Opens the screens
    private final AppShell shell;

    // The button of each screen
    private final Map<Screen, Button> buttons = new EnumMap<>(Screen.class);

    // The count badge of each screen
    private final Map<Screen, Label> badges = new EnumMap<>(Screen.class);

    // The row holding each button and its badge
    private final Map<Screen, StackPane> rows = new EnumMap<>(Screen.class);

    /**
     * Creates the sidebar.
     *
     * @param navList the list of the main items
     * @param footerNav the list pinned under the main items
     * @param navMenu the menu shown instead in a narrow window
     * @param shell opens the screens
     */
    public Sidebar(VBox navList, VBox footerNav, MenuButton navMenu,
            AppShell shell) {
        this.navList = navList;
        this.footerNav = footerNav;
        this.navMenu = navMenu;
        this.shell = shell;
    }

    /**
     * Adds every item, with its group heading, and fills the menu.
     */
    public void build() {
        String lastGroup = "";
        for (NavEntry entry : mainEntries()) {
            if (entry.group != null && !entry.group.equals(lastGroup)) {
                Label heading = new Label(entry.group);
                heading.getStyleClass().add("sidebar-group");
                navList.getChildren().add(heading);
                lastGroup = entry.group;
            }
            navList.getChildren().add(createRow(entry));
        }
        for (NavEntry entry : footerEntries()) {
            footerNav.getChildren().add(createRow(entry));
        }
        refreshMenu();
    }

    /**
     * Marks the item of the screen on show. Student Evidence belongs to
     * Results and Validation to Submissions.
     *
     * @param current the screen on show
     */
    public void select(Screen current) {
        for (Map.Entry<Screen, Button> item : buttons.entrySet()) {
            Screen screen = item.getKey();
            boolean selected = screen == current
                    || screen == Screen.RESULTS
                    && current == Screen.STUDENT_REPORT
                    || screen == Screen.UPLOAD
                    && current == Screen.VALIDATION;
            item.getValue().pseudoClassStateChanged(SELECTED, selected);
        }
    }

    /**
     * Shows a count next to an item, or hides it at zero.
     *
     * @param screen the item's screen
     * @param count the number waiting for review
     */
    public void setBadge(Screen screen, int count) {
        Label badge = badges.get(screen);
        Button button = buttons.get(screen);
        boolean show = count > 0;
        badge.setText(String.valueOf(count));
        badge.setVisible(show);
        button.getStyleClass().remove(HAS_BADGE);
        if (show) {
            button.getStyleClass().add(HAS_BADGE);
            button.setAccessibleText(screen.getTitle() + ", " + count
                    + " waiting for review");
        } else {
            button.setAccessibleText(screen.getTitle());
        }
    }

    /**
     * Shows or hides one item, such as Grading Progress before a run.
     *
     * @param screen the item's screen
     * @param shown true to show it
     */
    public void setShown(Screen screen, boolean shown) {
        StackPane row = rows.get(screen);
        row.setVisible(shown);
        row.setManaged(shown);
    }

    /**
     * Rebuilds the narrow-window menu from the items on show.
     */
    public void refreshMenu() {
        List<MenuItem> items = new ArrayList<>();
        List<NavEntry> entries = mainEntries();
        entries.addAll(footerEntries());
        for (NavEntry entry : entries) {
            StackPane row = rows.get(entry.screen);
            if (row == null || row.isVisible()) {
                MenuItem item = new MenuItem(entry.label,
                        entry.icon.create());
                item.setOnAction(event -> shell.navigate(entry.screen));
                items.add(item);
            }
        }
        navMenu.getItems().setAll(items);
    }

    // Returns the items above the spacer, in order.
    private List<NavEntry> mainEntries() {
        List<NavEntry> entries = new ArrayList<>();
        entries.add(new NavEntry(Screen.OVERVIEW, "Overview", Icon.OVERVIEW,
                null));
        entries.add(new NavEntry(Screen.MODULES, "Modules", Icon.MODULES,
                null));
        entries.add(new NavEntry(Screen.TEST_CASES, "Assessments",
                Icon.ASSESSMENTS, "Grading"));
        entries.add(new NavEntry(Screen.UPLOAD, "Submissions",
                Icon.SUBMISSIONS, "Grading"));
        entries.add(new NavEntry(Screen.PROGRESS, "Grading",
                Icon.PROGRESS, "Grading"));
        entries.add(new NavEntry(Screen.ANOMALY_LOG, "Review Anomalies",
                Icon.ANOMALIES, "Grading"));
        entries.add(new NavEntry(Screen.RESULTS, "Results", Icon.RESULTS,
                "Grading"));
        entries.add(new NavEntry(Screen.STATISTICS, "Statistics",
                Icon.STATISTICS, "Insights"));
        entries.add(new NavEntry(Screen.PLAGIARISM, "Similarity Review",
                Icon.SIMILARITY, "Insights"));
        entries.add(new NavEntry(Screen.HISTORY, "History", Icon.HISTORY,
                "Records"));
        entries.add(new NavEntry(Screen.AUDIT_LOG, "Audit Log", Icon.AUDIT,
                "Records"));
        return entries;
    }

    // Returns the items pinned below the spacer.
    private List<NavEntry> footerEntries() {
        List<NavEntry> entries = new ArrayList<>();
        entries.add(new NavEntry(Screen.SETTINGS, "Settings", Icon.SETTINGS,
                null));
        entries.add(new NavEntry(Screen.HELP, "Help", Icon.HELP, null));
        return entries;
    }

    // Creates one row: a button with a count badge over its right end.
    private StackPane createRow(NavEntry entry) {
        Button button = new Button(entry.label, entry.icon.create());
        button.getStyleClass().add("nav-item");
        button.setAlignment(Pos.CENTER_LEFT);
        button.setMaxWidth(Double.MAX_VALUE);
        button.setAccessibleText(entry.label);
        String shortcut = ScreenShortcuts.describe(entry.screen);
        if (!shortcut.isEmpty()) {
            button.setTooltip(new Tooltip(entry.label + " (" + shortcut
                    + ")"));
        }
        button.setOnAction(event -> shell.navigate(entry.screen));

        Label badge = new Label();
        badge.getStyleClass().add("nav-badge");
        badge.setMouseTransparent(true);
        badge.setVisible(false);

        StackPane row = new StackPane(button, badge);
        row.getStyleClass().add("nav-row");
        StackPane.setAlignment(badge, Pos.CENTER_RIGHT);
        StackPane.setMargin(badge, BADGE_MARGIN);

        buttons.put(entry.screen, button);
        badges.put(entry.screen, badge);
        rows.put(entry.screen, row);
        return row;
    }
}
