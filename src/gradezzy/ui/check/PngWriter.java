/*
 * File: PngWriter.java
 *
 * Purpose: Writes a JavaFX image to a PNG file.
 *
 * Where it fits: Used by SnapshotWriter for off-screen pictures and by
 * WindowTest for pictures of the real screen.
 *
 * Note on untaught Java: javax.imageio writes the PNG file. JavaFX's own
 * image helper is in javafx-swing, which is not in lib/, so the pixels
 * are copied into a java.awt.image.BufferedImage by hand.
 *
 * AI use: drafted with Claude Code, reviewed by Jun Sheng.
 */
package gradezzy.ui.check;

import java.awt.image.BufferedImage;
import java.io.IOException;
import java.nio.file.Path;

import javax.imageio.ImageIO;

import javafx.scene.image.Image;
import javafx.scene.image.PixelFormat;

/**
 * Writes PNG files.
 *
 * <p>Tier: SHOULD. Owner: Damien.
 */
public final class PngWriter {

    // The image format written
    private static final String FORMAT = "png";

    // Utility class, never instantiated.
    private PngWriter() {
        // nothing to set up
    }

    /**
     * Writes an image to a PNG file.
     *
     * @param image the image
     * @param file the file to write
     * @throws IOException if the file cannot be written
     */
    public static void write(Image image, Path file) throws IOException {
        int width = (int) image.getWidth();
        int height = (int) image.getHeight();
        int[] pixels = new int[width * height];
        image.getPixelReader().getPixels(0, 0, width, height,
                PixelFormat.getIntArgbInstance(), pixels, 0, width);

        BufferedImage buffered = new BufferedImage(width, height,
                BufferedImage.TYPE_INT_ARGB);
        buffered.setRGB(0, 0, width, height, pixels, 0, width);
        ImageIO.write(buffered, FORMAT, file.toFile());
    }
}
