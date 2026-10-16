/*
 * File: FileUtils.java
 *
 * Purpose: Small file helpers shared by several packages.
 *
 * Where it fits: Keeps repeated file code in one place.
 */
package gradezzy.util;

import java.io.IOException;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.List;

/**
 * Small file helpers shared by several packages.
 *
 * <p>Tier: MUST. Owner: Damien.
 */
public final class FileUtils {

    /**
     * Not used: every method here is static.
     */
    private FileUtils() {
        // utility class, never instantiated
    }

    /**
     * Copies a folder and everything in it.
     *
     * @param source the source
     * @param target the target
     * @throws IOException if the operation fails
     */
    public static void copyDirectory(Path source,
            Path target) throws IOException {
        // TODO(Damien): implement: a recursive copy
    }

    /**
     * Deletes a folder and everything in it.
     *
     * @param directory the directory
     * @throws IOException if the operation fails
     */
    public static void deleteDirectory(Path directory) throws IOException {
        // TODO(Damien): implement: a recursive delete
    }

    /**
     * Reads a whole text file.
     *
     * @param file the file
     * @return the content of the file
     * @throws IOException if the operation fails
     */
    public static String readText(Path file) throws IOException {
        // TODO(Damien): implement: reading the file as UTF-8 text
        return "";
    }

    /**
     * Writes text to a file, replacing what was there.
     *
     * @param file the file
     * @param text the text
     * @throws IOException if the operation fails
     */
    public static void writeText(Path file, String text) throws IOException {
        // TODO(Damien): implement: writing the text as UTF-8
    }

    /**
     * Lists the files in a folder that end with an extension.
     *
     * @param directory the directory
     * @param extension the extension
     * @return the matching files
     * @throws IOException if the operation fails
     */
    public static List<Path> listFiles(Path directory,
            String extension) throws IOException {
        // TODO(Damien): implement: listing matching files in name order
        return new ArrayList<>();
    }
}
