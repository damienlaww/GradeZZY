/*
 * File: ThemeManager.java
 *
 * Purpose: Switches the whole application between the light and the dark
 * theme.
 *
 * Where it fits: The only class that sets the AtlantaFX theme. MainApp
 * applies the theme named in config.properties at start-up, and the
 * context bar and Settings switch it later. Switching replaces only
 * stylesheets; no screen is reloaded, so nothing the instructor chose is
 * lost.
 *
 * AI use: stylesheet handling drafted with Claude Code, reviewed by Jun
 * Sheng.
 */
package gradezzy.ui;

import java.io.IOException;
import java.net.URL;
import java.nio.charset.StandardCharsets;
import java.util.Base64;
import java.util.List;

import atlantafx.base.theme.CupertinoDark;
import atlantafx.base.theme.CupertinoLight;
import javafx.application.Application;
import javafx.scene.Scene;
import javafx.scene.text.Font;

/**
 * Applies the light or dark theme.
 *
 * <p>Tier: SHOULD. Owner: Damien.
 */
public class ThemeManager {

    /**
     * The value of the ui.theme setting that selects the dark theme. Any
     * other value selects the light theme.
     */
    public static final String DARK_THEME = "dark";

    /** The value of the ui.theme setting for the light theme. */
    public static final String LIGHT_THEME = "light";

    /** The stylesheet with the team's tokens and components. */
    public static final String APP_STYLESHEET = "/gradezzy/ui/css/app.css";

    /** The stylesheet that switches the tokens to their dark values. */
    public static final String DARK_STYLESHEET =
            "/gradezzy/ui/css/app-dark.css";

    // Monospaced font families to try, best first
    private static final String[] MONOSPACED_FAMILIES = {"Consolas",
        "Menlo", "SF Mono", "Monospaced"};

    // The start of a stylesheet given inline as base64 text
    private static final String DATA_URI = "data:text/css;base64,";

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
     * Applies a theme to the application and the team's stylesheets to
     * one scene.
     *
     * @param scene the scene
     * @param dark true for the dark theme
     * @throws IOException if a stylesheet is missing from the classes
     *         folder
     */
    public void applyTo(Scene scene, boolean dark) throws IOException {
        apply(themeName(dark));

        List<String> sheets = scene.getStylesheets();
        sheets.clear();
        sheets.add(resource(APP_STYLESHEET));
        if (dark) {
            sheets.add(resource(DARK_STYLESHEET));
        }
        sheets.add(monospacedStylesheet());
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

    /**
     * Returns the address of a stylesheet in the classes folder.
     *
     * @param path the classpath location, starting with a slash
     * @return the address to give a scene
     * @throws IOException if the stylesheet is missing
     */
    public static String resource(String path) throws IOException {
        URL url = ThemeManager.class.getResource(path);
        if (url == null) {
            throw new IOException("Stylesheet not found: " + path
                    + ". Run the compile script again.");
        }
        return url.toExternalForm();
    }

    // Builds a small stylesheet that gives .mono the best installed
    // monospaced font, because CSS cannot list fallback fonts in JavaFX.
    private String monospacedStylesheet() {
        String family = MONOSPACED_FAMILIES[MONOSPACED_FAMILIES.length - 1];
        List<String> installed = Font.getFamilies();
        for (String candidate : MONOSPACED_FAMILIES) {
            if (installed.contains(candidate)) {
                family = candidate;
                break;
            }
        }

        String css = ".mono, .mono .text, .code-view, .code-view .text {"
                + " -fx-font-family: \"" + family + "\"; }";
        return DATA_URI + Base64.getEncoder().encodeToString(
                css.getBytes(StandardCharsets.UTF_8));
    }
}
