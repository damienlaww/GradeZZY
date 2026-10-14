/*
 * File: ThemeManager.java
 *
 * Purpose: Switches the whole application between the light and the dark
 * theme.
 *
 * Where it fits: The only class that uses the AtlantaFX library. MainApp
 * applies the theme named in config.properties at start-up and the
 * settings page can change it later.
 */
package gradezzy.ui;

import atlantafx.base.theme.CupertinoDark;
import atlantafx.base.theme.CupertinoLight;
import javafx.application.Application;

/**
 * Applies the light or dark theme.
 *
 * <p>Tier: SHOULD. Owner: Seth.
 */
public class ThemeManager {

    /**
     * The value of the ui.theme setting that selects the dark theme. Any
     * other value selects the light theme.
     */
    public static final String DARK_THEME = "dark";

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
        if (DARK_THEME.equalsIgnoreCase(themeName)) {
            Application.setUserAgentStylesheet(
                    new CupertinoDark().getUserAgentStylesheet());
        } else {
            Application.setUserAgentStylesheet(
                    new CupertinoLight().getUserAgentStylesheet());
        }
    }
}
