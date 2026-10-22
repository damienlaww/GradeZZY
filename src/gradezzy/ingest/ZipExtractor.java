/*
 * File: ZipExtractor.java
 *
 * Purpose: Unzips one submission into a folder.
 *
 * Where it fits: Used when a working copy is created. Decompress here means
 * unzip only; nothing is decompiled.
 */
package gradezzy.ingest;

import java.io.IOException;
import java.io.InputStream;
import java.io.OutputStream;
import java.nio.file.DirectoryStream;
import java.nio.file.Files;
import java.nio.file.InvalidPathException;
import java.nio.file.LinkOption;
import java.nio.file.Path;
import java.nio.file.StandardOpenOption;
import java.util.Enumeration;
import java.util.List;
import java.util.zip.CRC32;
import java.util.zip.ZipEntry;
import java.util.zip.ZipFile;

import gradezzy.config.AppConfig;
import gradezzy.config.ConfigKeys;
import gradezzy.util.FileUtils;

/**
 * Unzips one submission into a folder.
 *
 * <p>Tier: MUST. Owner: Damien.
 */
public class ZipExtractor {

    // Binary megabytes, as used by the extracted-size configuration
    private static final long BYTES_PER_MB = 1024L * 1024L;

    // Bytes read at once, so a large entry never has to fit in memory
    private static final int BUFFER_SIZE = 8192;

    // Device names have special meaning even when followed by a suffix.
    private static final String WINDOWS_DEVICE =
            "(?i)(CON|PRN|AUX|NUL|COM[1-9]|LPT[1-9])(\\..*)?";

    // The application settings
    private final AppConfig config;

    /**
     * Creates a new zip extractor.
     *
     * @param config the application settings
     */
    public ZipExtractor(AppConfig config) {
        this.config = config;
    }

    /**
     * Unzips a file into a new or empty target folder. Existing files
     * are never overwritten. Ignored entries still count towards limits.
     *
     * @param zipFile the zip file
     * @param targetDirectory the target directory
     * @return the folder that now holds the unzipped files
     * @throws IngestException if the operation fails
     */
    public Path extract(Path zipFile,
            Path targetDirectory) throws IngestException {
        Path target = targetDirectory.toAbsolutePath().normalize();
        if (!isSafeEntry("entry", target)) {
            throw new IngestException("Choose a target without symbolic "
                    + "links: " + target);
        }
        boolean ready = false;
        try {
            if (zipFile.toRealPath().startsWith(target)) {
                throw new IOException("The archive is inside its target.");
            }
            Files.createDirectories(target);
            try (DirectoryStream<Path> children =
                    Files.newDirectoryStream(target)) {
                if (children.iterator().hasNext()) {
                    throw new IOException("The target must be empty.");
                }
            }
            ready = true;
            unpack(zipFile, target);
            return target;
        } catch (IOException exception) {
            if (ready) {
                try {
                    FileUtils.deleteDirectory(target);
                } catch (IOException cleanup) {
                    exception.addSuppressed(cleanup);
                }
            }
            throw new IngestException("Could not unpack " + zipFile
                    + ": " + exception.getMessage()
                    + " Check the ZIP file and configured limits.",
                    exception);
        }
    }

    /**
     * Checks that a zip entry stays inside the target folder, contains
     * no links and is safe on both Windows and macOS.
     *
     * @param entryName the entry name
     * @param targetDirectory the target directory
     * @return true if the entry is safe to write
     */
    public boolean isSafeEntry(String entryName, Path targetDirectory) {
        if (entryName == null || entryName.isEmpty()
                || entryName.contains(":")) {
            return false;
        }
        try {
            Path entry = Path.of(entryName.replace('\\', '/'));
            if (entry.isAbsolute()) {
                return false;
            }
            for (Path part : entry) {
                String name = part.toString();
                if (name.equals(".")) {
                    continue;
                }
                if (name.equals("..") || name.endsWith(".")
                        || name.endsWith(" ") || name.matches(WINDOWS_DEVICE)
                        || name.matches(".*[<>\"|?*\\p{Cntrl}].*")) {
                    return false;
                }
            }
            Path root = targetDirectory.toAbsolutePath().normalize();
            Path destination = root.resolve(entry).normalize();
            if (destination.equals(root) || !destination.startsWith(root)) {
                return false;
            }
            Path current = destination;
            while (current != null) {
                if (Files.isSymbolicLink(current)) {
                    return false;
                }
                current = current.getParent();
            }
            return true;
        } catch (InvalidPathException exception) {
            return false;
        }
    }

    // ZipFile checks the central directory, unlike a stream accepting
    // arbitrary non-ZIP bytes as an empty archive.
    private void unpack(Path archive, Path target) throws IOException {
        long maxMegabytes = config.getLong(ConfigKeys.MAX_ZIP_SIZE_MB);
        int maxEntries = config.getInt(ConfigKeys.MAX_ZIP_ENTRIES);
        if (maxMegabytes <= 0 || maxMegabytes > Long.MAX_VALUE / BYTES_PER_MB
                || maxEntries <= 0) {
            throw new IOException("Extraction limits are out of range.");
        }
        long maxBytes = maxMegabytes * BYTES_PER_MB;
        long totalBytes = 0;
        long entryCount = 0;
        int fileCount = 0;
        List<String> ignored = config.getList(ConfigKeys.IGNORED_FILE_NAMES);

        try (ZipFile zip = new ZipFile(archive.toFile())) {
            Enumeration<? extends ZipEntry> entries = zip.entries();
            while (entries.hasMoreElements()) {
                ZipEntry entry = entries.nextElement();
                entryCount++;
                if (entryCount > maxEntries) {
                    throw new IOException("Too many ZIP entries.");
                }
                String entryName = entry.getName().replace('\\', '/');
                // Some ZIP tools emit an explicit ./ directory entry.
                if (entryName.endsWith("/") && entryName.matches("(?:\\./)+")) {
                    continue;
                }
                if (!isSafeEntry(entryName, target)) {
                    throw new IOException("Unsafe ZIP entry: "
                            + entry.getName());
                }
                Path relative = Path.of(entryName).normalize();
                boolean skip = isIgnored(relative, ignored);
                Path output = target.resolve(relative).normalize();
                if (entryName.endsWith("/")) {
                    if (!skip) {
                        Files.createDirectories(output);
                    }
                } else {
                    long written = extractFile(zip, entry, output, skip,
                            maxBytes - totalBytes);
                    totalBytes += written;
                    if (!skip) {
                        fileCount++;
                    }
                }
            }
        }
        if (fileCount == 0) {
            throw new IOException("The ZIP has no submission files.");
        }
    }

    // Count actual decompressed bytes, never trust the declared size.
    private long extractFile(ZipFile zip, ZipEntry entry, Path output,
            boolean skip, long remaining) throws IOException {
        if (!skip) {
            Files.createDirectories(output.getParent());
        }
        OutputStream destination = OutputStream.nullOutputStream();
        if (!skip) {
            destination = Files.newOutputStream(output,
                    StandardOpenOption.CREATE_NEW);
        }
        try (OutputStream writer = destination;
                InputStream reader = zip.getInputStream(entry)) {
            byte[] buffer = new byte[BUFFER_SIZE];
            CRC32 checksum = new CRC32();
            long total = 0;
            int count = reader.read(buffer);
            while (count != -1) {
                if (count > remaining - total) {
                    throw new IOException("Unzipped size exceeds the limit.");
                }
                writer.write(buffer, 0, count);
                checksum.update(buffer, 0, count);
                total += count;
                count = reader.read(buffer);
            }
            if (checksum.getValue() != entry.getCrc()
                    || total != entry.getSize()) {
                throw new IOException("Damaged ZIP entry: "
                        + entry.getName());
            }
            return total;
        }
    }

    // Ignore both the named file and every descendant of a named folder.
    private boolean isIgnored(Path path, List<String> ignored) {
        for (Path part : path) {
            if (ignored.contains(part.toString())) {
                return true;
            }
        }
        return false;
    }
}
