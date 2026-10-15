/*
 * File: ScreenShortcuts.java
 *
 * Purpose: The keyboard shortcut of each screen, in one place, so the
 * shortcuts, the sidebar tooltips and the Help screen always agree.
 *
 * Where it fits: MainController installs the shortcuts and Sidebar shows
 * them in tooltips. SHORTCUT is Cmd on macOS and Ctrl on Windows.
 *
 * AI use: drafted with Claude Code, reviewed by Jun Sheng.
 */
package gradezzy.ui.view;

import java.util.ArrayList;
import java.util.List;

import javafx.scene.Node;
import javafx.scene.Parent;
import javafx.scene.control.Control;
import javafx.scene.control.Tooltip;
import javafx.scene.input.KeyCode;
import javafx.scene.input.KeyCodeCombination;
import javafx.scene.input.KeyCombination;

import gradezzy.ui.Screen;

/**
 * The keyboard shortcuts that open screens.
 *
 * <p>Tier: SHOULD. Owner: Damien.
 */
public final class ScreenShortcuts {

    // The nine digit keys, for Shortcut+1 to Shortcut+9
    // The fx:id of a screen's search field
    private static final String SEARCH_ID = "#searchField";

    // The fx:id of a screen's Select All button
    private static final String SELECT_ALL_ID = "#selectAllButton";

    private static final KeyCode[] DIGITS = {KeyCode.DIGIT1, KeyCode.DIGIT2,
        KeyCode.DIGIT3, KeyCode.DIGIT4, KeyCode.DIGIT5, KeyCode.DIGIT6,
        KeyCode.DIGIT7, KeyCode.DIGIT8, KeyCode.DIGIT9};

    // Utility class, never instantiated.
    private ScreenShortcuts() {
        // nothing to set up
    }

    /**
     * Returns the screens reached with Shortcut+1 to Shortcut+9, in order.
     *
     * @return the nine screens
     */
    public static List<Screen> numberedScreens() {
        List<Screen> screens = new ArrayList<>();
        screens.add(Screen.OVERVIEW);
        screens.add(Screen.MODULES);
        screens.add(Screen.TEST_CASES);
        screens.add(Screen.UPLOAD);
        screens.add(Screen.ANOMALY_LOG);
        screens.add(Screen.RESULTS);
        screens.add(Screen.STATISTICS);
        screens.add(Screen.PLAGIARISM);
        screens.add(Screen.HISTORY);
        return screens;
    }

    /**
     * Returns the key combination that opens a screen.
     *
     * @param screen the screen
     * @return the combination, or null when the screen has none
     */
    public static KeyCombination combinationFor(Screen screen) {
        int index = numberedScreens().indexOf(screen);
        if (index >= 0) {
            return new KeyCodeCombination(DIGITS[index],
                    KeyCombination.SHORTCUT_DOWN);
        }
        if (screen == Screen.SETTINGS) {
            return new KeyCodeCombination(KeyCode.COMMA,
                    KeyCombination.SHORTCUT_DOWN);
        }
        if (screen == Screen.HELP) {
            return new KeyCodeCombination(KeyCode.F1);
        }
        return null;
    }

    /**
     * Adds tooltips naming the shortcuts of a screen's search field
     * (Shortcut+F) and Select All button (Shortcut+A in the table), when
     * the screen has them and they have no tooltip yet.
     *
     * @param view the screen
     */
    public static void addTooltips(Parent view) {
        Node search = view.lookup(SEARCH_ID);
        if (search instanceof Control
                && ((Control) search).getTooltip() == null) {
            ((Control) search).setTooltip(new Tooltip("Search ("
                    + withShortcut(KeyCode.F) + ")"));
        }
        Node selectAll = view.lookup(SELECT_ALL_ID);
        if (selectAll instanceof Control
                && ((Control) selectAll).getTooltip() == null) {
            ((Control) selectAll).setTooltip(new Tooltip("Select every row "
                    + "shown (" + withShortcut(KeyCode.A)
                    + " in the table)"));
        }
    }

    /**
     * Returns the shortcut of a screen as the platform writes it.
     *
     * @param screen the screen
     * @return the text, such as "Ctrl+1", or an empty string
     */
    public static String describe(Screen screen) {
        KeyCombination combination = combinationFor(screen);
        if (combination == null) {
            return "";
        }
        return combination.getDisplayText();
    }

    // Writes Shortcut and a key as the platform does, such as Ctrl+F.
    private static String withShortcut(KeyCode key) {
        return new KeyCodeCombination(key, KeyCombination.SHORTCUT_DOWN)
                .getDisplayText();
    }
}
