/*
 * File: FileUtils.java
 *
 * Purpose: Small file helpers shared by several packages.
 *
 * Where it fits: Keeps repeated file code in one place.
 */
package gradezzy.util;

import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.DirectoryStream;
import java.nio.file.Files;
import java.nio.file.LinkOption;
import java.nio.file.Path;
import java.nio.file.StandardCopyOption;
import java.util.ArrayList;
import java.util.Collections;
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
     * Copies a folder and everything in it. Symbolic links and overlapping
     * source/target trees are rejected; ordinary target files are replaced.
     *
     * @param source the source
     * @param target the target
     * @throws IOException if the operation fails
     */
    public static void copyDirectory(Path source,
            Path target) throws IOException {
        Path original = source.toAbsolutePath().normalize();
        Path destination = target.toAbsolutePath().normalize();
        rejectLinks(original);
        rejectLinks(destination);
        if (destination.startsWith(original)
                || original.startsWith(destination)) {
            throw new IOException("Source and copy must not overlap.");
        }
        copyContents(original, destination);
    }

    /**
     * Deletes a folder and everything in it. Symbolic links inside the
     * folder are deleted without following them. A missing path is safe.
     *
     * @param directory the directory
     * @throws IOException if the operation fails
     */
    public static void deleteDirectory(Path directory) throws IOException {
        Path parent = directory.toAbsolutePath().normalize().getParent();
        if (parent != null) {
            rejectLinks(parent);
        }
        if (Files.isDirectory(directory, LinkOption.NOFOLLOW_LINKS)) {
            try (DirectoryStream<Path> children =
                    Files.newDirectoryStream(directory)) {
                for (Path child : children) {
                    deleteDirectory(child);
                }
            }
        }
        Files.deleteIfExists(directory);
    }

    /**
     * Reads a whole text file.
     *
     * @param file the file
     * @return the content of the file
     * @throws IOException if the operation fails
     */
    public static String readText(Path file) throws IOException {
        return Files.readString(file, StandardCharsets.UTF_8);
    }

    /**
     * Writes text to a file, replacing what was there.
     *
     * @param file the file
     * @param text the text
     * @throws IOException if the operation fails
     */
    public static void writeText(Path file, String text) throws IOException {
        Files.writeString(file, text, StandardCharsets.UTF_8);
    }

    /**
     * Lists ordinary files directly in a folder in name order. Extension
     * matching is case sensitive; folders and symbolic links are excluded.
     *
     * @param directory the directory
     * @param extension the extension
     * @return the matching files
     * @throws IOException if the operation fails
     */
    public static List<Path> listFiles(Path directory,
            String extension) throws IOException {
        List<Path> matches = new ArrayList<>();
        try (DirectoryStream<Path> children =
                Files.newDirectoryStream(directory)) {
            for (Path child : children) {
                if (Files.isRegularFile(child, LinkOption.NOFOLLOW_LINKS)
                        && child.getFileName().toString()
                                .endsWith(extension)) {
                    matches.add(child);
                }
            }
        }
        Collections.sort(matches);
        return matches;
    }

    // Copies ordinary files only; links must never reach outside a copy.
    private static void copyContents(Path source, Path target)
            throws IOException {
        rejectLinks(source);
        rejectLinks(target);
        if (Files.isDirectory(source, LinkOption.NOFOLLOW_LINKS)) {
            Files.createDirectories(target);
            try (DirectoryStream<Path> children =
                    Files.newDirectoryStream(source)) {
                for (Path child : children) {
                    copyContents(child, target.resolve(child.getFileName()));
                }
            }
        } else if (Files.isRegularFile(source, LinkOption.NOFOLLOW_LINKS)) {
            Files.copy(source, target, StandardCopyOption.REPLACE_EXISTING);
        } else {
            throw new IOException("Not an ordinary file or folder: "
                    + source);
        }
    }

    // Also checks ancestors so a linked parent cannot redirect a write.
    private static void rejectLinks(Path path) throws IOException {
        Path current = path.toAbsolutePath().normalize();
        while (current != null) {
            if (Files.isSymbolicLink(current)) {
                throw new IOException("Symbolic links are not supported: "
                        + current);
            }
            current = current.getParent();
        }
    }
}
