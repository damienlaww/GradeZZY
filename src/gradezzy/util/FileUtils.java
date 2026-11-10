/*
 * File: FileUtils.java
 *
 * Purpose: Small file helpers shared by several packages.
 *
 * Where it fits: Keeps repeated file code in one place.
 */
package gradezzy.util;

import java.io.Closeable;
import java.io.IOException;
import java.nio.charset.Charset;
import java.nio.charset.MalformedInputException;
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

    // The encoding Excel on Windows uses when it saves a "CSV" file
    private static final String WINDOWS_CHARSET = "windows-1252";

    // How many times a move or delete is tried before giving up
    private static final int MOVE_ATTEMPTS = 5;

    // The Windows file attributes and their read-only flag
    private static final String DOS_VIEW = "dos";
    private static final String DOS_READ_ONLY = "dos:readonly";

    // How long to wait between tries, in milliseconds
    private static final long MOVE_WAIT_MILLIS = 500;

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
        boolean overlapping = destination.startsWith(original)
                || original.startsWith(destination);
        if (overlapping) {
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
            DirectoryStream<Path> children = null;
            try {
                children = Files.newDirectoryStream(directory);
                for (Path child : children) {
                    deleteDirectory(child);
                }
            } finally {
                closeIfOpen(children);
            }
        }
        deleteWithRetry(directory);
    }

    // Deletes one file or empty folder. On Windows a read-only file
    // cannot be deleted, and antivirus or a process that just stopped
    // can hold a file for a moment, so it clears read-only and tries
    // again a few times; the last try reports the real error. Elsewhere
    // such a failure does not pass by itself, so it is reported at once
    // instead of after the waits.
    private static void deleteWithRetry(Path path) throws IOException {
        for (int attempt = 1; attempt < MOVE_ATTEMPTS; attempt++) {
            try {
                Files.deleteIfExists(path);
                return;
            } catch (IOException exception) {
                if (!clearReadOnly(path)) {
                    throw exception;
                }
                waitBeforeRetry();
            }
        }
        Files.deleteIfExists(path);
    }

    // Clears the Windows read-only flag. Returns false when the file is
    // not on a Windows file system or is gone, so there is nothing to
    // wait for.
    private static boolean clearReadOnly(Path path) {
        try {
            boolean dosDisk = Files.getFileStore(path)
                    .supportsFileAttributeView(DOS_VIEW);
            if (!dosDisk) {
                return false;
            }
            Files.setAttribute(path, DOS_READ_ONLY, false,
                    LinkOption.NOFOLLOW_LINKS);
            return true;
        } catch (IOException exception) {
            return false;
        } catch (UnsupportedOperationException exception) {
            return false;
        } catch (IllegalArgumentException exception) {
            return false;
        }
    }

    /**
     * Moves a file or folder, trying again for a short time if it fails.
     * On Windows, a program such as antivirus can hold a new file open for
     * a moment, which stops its folder from being renamed.
     *
     * @param source the file or folder to move
     * @param target where it is moved to
     * @throws IOException if the last try also fails
     */
    public static void moveWithRetry(Path source,
            Path target) throws IOException {
        for (int attempt = 1; attempt < MOVE_ATTEMPTS; attempt++) {
            try {
                Files.move(source, target);
                return;
            } catch (IOException exception) {
                // The file may be free again after a short wait
                waitBeforeRetry();
            }
        }

        // The last try reports the real error
        Files.move(source, target);
    }

    /**
     * Reads a whole text file as UTF-8. A file that is not valid UTF-8,
     * such as a score sheet saved as CSV by Excel on Windows with a name
     * like José in it, is read as Windows-1252 instead.
     *
     * @param file the file
     * @return the content of the file
     * @throws IOException if the operation fails
     */
    public static String readText(Path file) throws IOException {
        try {
            return Files.readString(file, StandardCharsets.UTF_8);
        } catch (MalformedInputException exception) {
            return Files.readString(file, Charset.forName(WINDOWS_CHARSET));
        }
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
        DirectoryStream<Path> children = null;
        try {
            children = Files.newDirectoryStream(directory);
            for (Path child : children) {
                boolean matching = Files.isRegularFile(child,
                        LinkOption.NOFOLLOW_LINKS)
                        && child.getFileName().toString().endsWith(extension);
                if (matching) {
                    matches.add(child);
                }
            }
        } finally {
            closeIfOpen(children);
        }
        Collections.sort(matches);
        return matches;
    }

    /**
     * Closes a file, folder listing or stream that was opened inside a try
     * block. Called from the matching finally block, so null means the
     * opening itself failed and there is nothing to close.
     *
     * <p>Tier: MUST.
     *
     * @param resource the thing to close, or null
     * @throws IOException if closing fails
     */
    public static void closeIfOpen(Closeable resource) throws IOException {
        if (resource != null) {
            resource.close();
        }
    }

    // Copies ordinary files only; links must never reach outside a copy.
    private static void copyContents(Path source, Path target)
            throws IOException {
        rejectLinks(source);
        rejectLinks(target);
        if (Files.isDirectory(source, LinkOption.NOFOLLOW_LINKS)) {
            Files.createDirectories(target);
            DirectoryStream<Path> children = null;
            try {
                children = Files.newDirectoryStream(source);
                for (Path child : children) {
                    copyContents(child, target.resolve(child.getFileName()));
                }
            } finally {
                closeIfOpen(children);
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

    // Waits before the next move or delete; keeps an interrupt for the
    // caller.
    private static void waitBeforeRetry() {
        try {
            Thread.sleep(MOVE_WAIT_MILLIS);
        } catch (InterruptedException exception) {
            Thread.currentThread().interrupt();
        }
    }
}
