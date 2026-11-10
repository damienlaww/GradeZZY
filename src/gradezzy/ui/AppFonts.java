/*
 * File: AppFonts.java
 *
 * Purpose: Loads the typefaces GradeZZY ships with: IBM Plex Sans for
 * every word on screen and IBM Plex Mono for code and tester output.
 *
 * Where it fits: FlowWindow calls load before it builds a scene, so the
 * stylesheet can name the fonts and every computer shows the same ones,
 * whatever fonts it has installed. The fonts and their licence (SIL Open
 * Font License 1.1) are in gradezzy/ui/fonts.
 */
package gradezzy.ui;

import java.io.IOException;
import java.io.InputStream;

import javafx.scene.text.Font;

/**
 * Loads the bundled fonts once.
 *
 * <p>Tier: COULD.
 */
public final class AppFonts {

    /** The family the stylesheet uses for text. */
    public static final String TEXT_FAMILY = "IBM Plex Sans";

    /** The family the stylesheet uses for code. */
    public static final String CODE_FAMILY = "IBM Plex Mono";

    // The folder of the font files, on the class path
    private static final String FOLDER = "/gradezzy/ui/fonts/";

    // Every font file, one per weight
    private static final String[] FILES = {
        "IBMPlexSans-Regular.ttf", "IBMPlexSans-Medium.ttf",
        "IBMPlexSans-SemiBold.ttf", "IBMPlexSans-Bold.ttf",
        "IBMPlexMono-Regular.ttf", "IBMPlexMono-Medium.ttf"};

    // The size a font is loaded at; CSS sets the size used
    private static final double LOAD_SIZE = 13.0;

    // True once the fonts were loaded
    private static boolean loaded = false;

    // Not used: every method is static.
    private AppFonts() {
    }

    /**
     * Loads every bundled font, the first time only. A font that cannot
     * be found or read is skipped, so the window falls back to the
     * system font instead of failing to open.
     */
    public static synchronized void load() {
        if (loaded) {
            return;
        }
        loaded = true;
        for (String file : FILES) {
            loadOne(FOLDER + file);
        }
    }

    // Loads one font file from the class path.
    private static void loadOne(String resource) {
        InputStream stream = AppFonts.class.getResourceAsStream(resource);
        if (stream == null) {
            return;
        }
        try {
            Font.loadFont(stream, LOAD_SIZE);
        } finally {
            try {
                stream.close();
            } catch (IOException exception) {
                // The font was already read; failing to close the file
                // changes nothing on screen
                return;
            }
        }
    }
}
