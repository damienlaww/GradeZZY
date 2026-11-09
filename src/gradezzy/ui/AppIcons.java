/*
 * File: AppIcons.java
 *
 * Purpose: Loads the GradeZZY logo: the window and taskbar icons in
 * several sizes, and the small logo in the top bar.
 *
 * Where it fits: MainApp gives the window icons to the main window,
 * FlowWindow shows the top bar logo, and FlowCheck makes sure every
 * file was found. The pictures are in gradezzy/ui/images.
 */
package gradezzy.ui;

import java.net.URL;
import java.util.ArrayList;
import java.util.List;

import javafx.scene.image.Image;

/**
 * Loads the logo pictures from the class path.
 */
public final class AppIcons {

    /** The window icon files, smallest first. */
    public static final String[] WINDOW_FILES = {
        "logo-16.png", "logo-24.png", "logo-32.png", "logo-48.png",
        "logo-64.png", "logo-128.png", "logo-256.png"};

    /** The top bar logo, twice its shown size for sharp screens. */
    public static final String TOP_BAR_FILE = "logo-44.png";

    // The folder of the pictures, on the class path
    private static final String FOLDER = "/gradezzy/ui/images/";

    // Not used: every method is static.
    private AppIcons() {
    }

    /**
     * Returns the window icons that could be read. A missing or broken
     * file is skipped, so the window opens with fewer icons instead.
     *
     * @return the icons found, smallest first
     */
    public static List<Image> loadWindowIcons() {
        List<Image> icons = new ArrayList<>();
        for (String file : WINDOW_FILES) {
            Image icon = loadOne(file);
            if (icon != null) {
                icons.add(icon);
            }
        }
        return icons;
    }

    /**
     * Returns the top bar logo.
     *
     * @return the logo, or null if it cannot be read
     */
    public static Image loadTopBarLogo() {
        return loadOne(TOP_BAR_FILE);
    }

    // Reads one picture; null if it is missing or not a picture.
    private static Image loadOne(String file) {
        URL url = AppIcons.class.getResource(FOLDER + file);
        if (url == null) {
            return null;
        }
        Image image = new Image(url.toExternalForm());
        if (image.isError()) {
            return null;
        }
        return image;
    }
}
