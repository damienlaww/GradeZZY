/*
 * File: ThemeManager.java
 *
 * Purpose: Switches the application between the light and dark themes.
 *
 * Where it fits: The only class that sets the AtlantaFX theme. FlowWindow
 * applies it at start-up, and the Settings tab switches it later without
 * reloading any screen.
 */
package gradezzy.ui;

import atlantafx.base.theme.CupertinoDark;
import atlantafx.base.theme.CupertinoLight;
import javafx.application.Application;

/**
 * Applies the light or dark theme.
 */
public class ThemeManager {

    /** The ui.theme value for dark; any other value means light. */
    public static final String DARK_THEME = "dark";

    /** The value of the ui.theme setting for the light theme. */
    public static final String LIGHT_THEME = "light";

    /**
     * Creates a new theme manager.
     */
    public ThemeManager() {
        // nothing to set up
    }

    /**
     * Applies a theme to every window of the application.
     *
     * @param themeName the value of the ui.theme setting
     */
    public void apply(String themeName) {
        if (isDark(themeName)) {
            Application.setUserAgentStylesheet(
                    new CupertinoDark().getUserAgentStylesheet());
        } else {
            Application.setUserAgentStylesheet(
                    new CupertinoLight().getUserAgentStylesheet());
        }
    }

    /**
     * Says whether a ui.theme value selects the dark theme.
     *
     * @param themeName the value
     * @return true for "dark", in any case
     */
    public static boolean isDark(String themeName) {
        return DARK_THEME.equalsIgnoreCase(themeName);
    }

    /**
     * Returns the ui.theme value for a theme.
     *
     * @param dark true for the dark theme
     * @return "dark" or "light"
     */
    public static String themeName(boolean dark) {
        if (dark) {
            return DARK_THEME;
        }
        return LIGHT_THEME;
    }
}
