/*
 * File: ZipExtractor.java
 *
 * Purpose: Unzips one submission into a folder.
 *
 * Where it fits: Used when a working copy is created. Decompress here means
 * unzip only; nothing is decompiled.
 */
package gradezzy.ingest;

import java.io.EOFException;
import java.io.IOException;
import java.io.InputStream;
import java.io.OutputStream;
import java.nio.charset.Charset;
import java.nio.file.DirectoryStream;
import java.nio.file.FileAlreadyExistsException;
import java.nio.file.Files;
import java.nio.file.InvalidPathException;
import java.nio.file.Path;
import java.nio.file.StandardOpenOption;
import java.util.ArrayList;
import java.util.Enumeration;
import java.util.HashSet;
import java.util.List;
import java.util.Locale;
import java.util.Set;
import java.util.zip.CRC32;
import java.util.zip.ZipEntry;
import java.util.zip.ZipException;
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

    // The folder separators of a zip entry name: the standard one, and
    // the one some Windows tools write instead
    private static final String ENTRY_SEPARATOR = "/";
    private static final String WINDOWS_SEPARATOR = "\\";

    // Device names have special meaning even when followed by a suffix.
    private static final String WINDOWS_DEVICE =
            "(?i)(CON|PRN|AUX|NUL|COM[1-9]|LPT[1-9])(\\..*)?";

    // A ZIP entry made only of this, repeated, names the top folder itself
    private static final String CURRENT_FOLDER = "./";

    // The part of an entry name that climbs out of its folder
    private static final String PARENT_FOLDER = "..";

    // A part of an entry name that names its own folder, as in Q1/./A.java
    private static final String SAME_FOLDER = ".";

    // An entry name that starts with a Windows drive, such as C:
    private static final String DRIVE_PATTERN = "(?s)[A-Za-z]:.*";

    // Matches each control character, such as the \r in the macOS Icon\r
    private static final String CONTROL_CHARACTER = "\\p{Cntrl}";

    // Shown in place of a control character in a message
    private static final String UNPRINTABLE = "?";

    // Names that start with this are macOS resource forks, such as
    // ._Q1a.java, which the Mac adds beside each file it zips
    private static final String RESOURCE_FORK_PREFIX = "._";

    // Windows Explorer writes a name that is not plain English in this
    // old DOS code page instead of UTF-8
    private static final String WINDOWS_ZIP_CHARSET = "CP437";

    // The end of a sentence in a message
    private static final String FULL_STOP = ".";

    // What the instructor is asked to do about a zip that cannot be used
    private static final String NEW_COPY_ADVICE =
            " Ask the student for a new copy.";

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
     * An entry whose name cannot be saved is left out when it is outside
     * the question folders.
     *
     * @param zipFile the zip file
     * @param targetDirectory the target directory
     * @return the folder that now holds the unzipped files
     * @throws InvalidArchiveException if the zip is damaged, unsafe, too
     *         big or holds two files with one name
     * @throws IngestException if the operation fails for another reason
     */
    public Path extract(Path zipFile,
            Path targetDirectory) throws IngestException {
        return extract(zipFile, targetDirectory, new ArrayList<>());
    }

    /**
     * Unzips a file into a new or empty target folder, as the other
     * extract method does, and adds to a list the name of each entry left
     * out because its name cannot be saved on this computer, such as the
     * macOS Icon\r file. Such an entry inside a question folder, and any
     * entry that would be written outside the target, still stops the
     * whole zip.
     *
     * <p>Tier: MUST.
     *
     * @param zipFile the zip file
     * @param targetDirectory the target directory
     * @param skippedNames the list the left-out entry names are added to
     * @return the folder that now holds the unzipped files
     * @throws InvalidArchiveException if the zip is damaged, unsafe, too
     *         big or holds two files with one name
     * @throws IngestException if the operation fails for another reason
     */
    public Path extract(Path zipFile, Path targetDirectory,
            List<String> skippedNames) throws IngestException {
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
            DirectoryStream<Path> children = null;
            try {
                children = Files.newDirectoryStream(target);
                if (children.iterator().hasNext()) {
                    throw new IOException("The target must be empty.");
                }
            } finally {
                FileUtils.closeIfOpen(children);
            }
            ready = true;
            unpack(zipFile, target, skippedNames);
            return target;
        } catch (IOException exception) {
            if (ready) {
                removeUnpacked(target, exception);
            }
            throw new IngestException("Could not unpack " + zipFile
                    + ": " + endSentence(exception.getMessage())
                    + " Check the ZIP file and configured limits.",
                    exception);
        } catch (InvalidArchiveException exception) {
            // Only thrown while unpacking, so the target is always ready
            removeUnpacked(target, exception);
            throw exception;
        }
    }

    /**
     * Lists the files inside a zip without unzipping it. Folder entries
     * are left out and every name uses forward slashes.
     *
     * @param zipFile the zip file
     * @return the file names, in the order the zip stores them
     * @throws IngestException if the file is not a readable zip
     */
    public List<String> listFileNames(Path zipFile) throws IngestException {
        try {
            return readFileNames(zipFile);
        } catch (IOException exception) {
            throw unreadable(zipFile, exception);
        } catch (IllegalArgumentException exception) {
            // An entry name that is not valid text
            throw unreadable(zipFile, exception);
        }
    }

    // Reads the file names of a zip; the zip is closed even if reading
    // fails.
    private List<String> readFileNames(Path zipFile) throws IOException {
        List<String> names = new ArrayList<>();
        ZipFile zip = null;
        try {
            zip = openZip(zipFile);
            Enumeration<? extends ZipEntry> entries = zip.entries();
            while (entries.hasMoreElements()) {
                String name = entries.nextElement().getName()
                        .replace(WINDOWS_SEPARATOR, ENTRY_SEPARATOR);
                if (!name.endsWith(ENTRY_SEPARATOR)) {
                    names.add(name);
                }
            }
        } finally {
            FileUtils.closeIfOpen(zip);
        }
        return names;
    }

    // Builds the error for a zip whose file names cannot be read.
    private IngestException unreadable(Path zipFile, Exception cause) {
        return new IngestException("Could not read " + zipFile + ": "
                + cause.getMessage(), cause);
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
            Path entry = Path.of(entryName.replace(WINDOWS_SEPARATOR,
                    ENTRY_SEPARATOR));
            if (entry.isAbsolute()) {
                return false;
            }
            for (Path part : entry) {
                String name = part.toString();
                if (name.equals(SAME_FOLDER)) {
                    continue;
                }
                if (name.equals(PARENT_FOLDER) || name.endsWith(".")
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
    private void unpack(Path archive, Path target, List<String> skippedNames)
            throws IOException, InvalidArchiveException {
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

        // The lower-case path of each file written, because Windows and
        // macOS treat Q1/A.java and q1/a.java as the same file
        Set<String> writtenPaths = new HashSet<>();

        ZipFile zip = null;
        try {
            zip = openZip(archive);
            Enumeration<? extends ZipEntry> entries = zip.entries();
            while (entries.hasMoreElements()) {
                ZipEntry entry = entries.nextElement();
                entryCount++;
                if (entryCount > maxEntries) {
                    throw tooBig(maxMegabytes, maxEntries);
                }
                String entryName = entry.getName().replace(
                        WINDOWS_SEPARATOR, ENTRY_SEPARATOR);
                // Some ZIP tools emit an explicit ./ directory entry.
                if (isOnlyCurrentFolder(entryName)) {
                    continue;
                }

                // Ignored names are checked first, so an odd name inside
                // __MACOSX never matters
                boolean skip = isIgnored(entryName, ignored);
                if (!skip && !isSafeEntry(entryName, target)) {
                    if (escapesFolder(entryName)
                            || isInQuestionFolder(entryName)) {
                        throw unsafe(entryName);
                    }
                    skippedNames.add(printable(entryName));
                    skip = true;
                }

                if (entryName.endsWith(ENTRY_SEPARATOR)) {
                    if (!skip) {
                        Files.createDirectories(target.resolve(entryName)
                                .normalize());
                    }
                } else {
                    Path output = null;
                    if (!skip) {
                        output = target.resolve(entryName).normalize();
                        String key = target.relativize(output).toString()
                                .toLowerCase(Locale.ROOT);
                        if (!writtenPaths.add(key)) {
                            throw clash(entryName, null);
                        }
                    }
                    long written = extractFile(zip, entry, output,
                            maxBytes - totalBytes, maxMegabytes, maxEntries);
                    totalBytes += written;
                    if (!skip) {
                        fileCount++;
                    }
                }
            }
        } catch (ZipException exception) {
            throw damaged(exception);
        } catch (EOFException exception) {
            // An entry that ends before its declared size
            throw damaged(exception);
        } finally {
            FileUtils.closeIfOpen(zip);
        }
        if (fileCount == 0) {
            throw new InvalidArchiveException("This zip file holds no files "
                    + "to grade." + NEW_COPY_ADVICE);
        }
    }

    // Opens a zip. Windows Explorer writes a name that is not plain
    // English in the DOS code page instead of UTF-8, which ZipFile refuses
    // unless it is given that code page. A name marked as UTF-8 is still
    // read as UTF-8. A zip that fails both ways is damaged.
    private ZipFile openZip(Path archive) throws IOException {
        try {
            return new ZipFile(archive.toFile());
        } catch (ZipException exception) {
            return new ZipFile(archive.toFile(),
                    Charset.forName(WINDOWS_ZIP_CHARSET));
        }
    }

    // Count actual decompressed bytes, never trust the declared size. A
    // null output means the entry is left out but still counted.
    private long extractFile(ZipFile zip, ZipEntry entry, Path output,
            long remaining, long maxMegabytes, int maxEntries)
            throws IOException, InvalidArchiveException {
        OutputStream destination = OutputStream.nullOutputStream();
        if (output != null) {
            Files.createDirectories(output.getParent());
            try {
                destination = Files.newOutputStream(output,
                        StandardOpenOption.CREATE_NEW);
            } catch (FileAlreadyExistsException exception) {
                // Two names this computer cannot tell apart, such as two
                // spellings of an accented letter on macOS
                throw clash(entry.getName(), exception);
            }
        }
        try {
            return copyEntry(zip, entry, destination, remaining,
                    maxMegabytes, maxEntries);
        } finally {
            destination.close();
        }
    }

    // Copies one entry into the destination, checking the size limit and
    // the checksum, and returns the number of bytes copied.
    private long copyEntry(ZipFile zip, ZipEntry entry, OutputStream writer,
            long remaining, long maxMegabytes, int maxEntries)
            throws IOException, InvalidArchiveException {
        InputStream reader = null;
        try {
            reader = zip.getInputStream(entry);
            byte[] buffer = new byte[BUFFER_SIZE];
            CRC32 checksum = new CRC32();
            long total = 0;
            int count = reader.read(buffer);
            while (count != -1) {
                if (count > remaining - total) {
                    throw tooBig(maxMegabytes, maxEntries);
                }
                writer.write(buffer, 0, count);
                checksum.update(buffer, 0, count);
                total += count;
                count = reader.read(buffer);
            }
            if (checksum.getValue() != entry.getCrc()
                    || total != entry.getSize()) {
                throw damaged(null);
            }
            return total;
        } finally {
            FileUtils.closeIfOpen(reader);
        }
    }

    // Deletes a half-unpacked target. A failure to delete it is attached
    // to the original error instead of hiding it.
    private void removeUnpacked(Path target, Exception failure) {
        try {
            FileUtils.deleteDirectory(target);
        } catch (IOException cleanup) {
            failure.addSuppressed(cleanup);
        }
    }

    // Builds the error for a zip that cannot be read.
    private InvalidArchiveException damaged(Exception cause) {
        return new InvalidArchiveException("This zip file is damaged."
                + NEW_COPY_ADVICE, cause);
    }

    // Builds the error for a zip that unpacks to more than the limits.
    private InvalidArchiveException tooBig(long maxMegabytes,
            int maxEntries) {
        return new InvalidArchiveException("This zip file unpacks to more "
                + "than " + maxMegabytes + " MB or " + maxEntries
                + " files, so it was not opened." + NEW_COPY_ADVICE);
    }

    // Builds the error for an entry that cannot be written safely.
    private InvalidArchiveException unsafe(String entryName) {
        if (escapesFolder(entryName)) {
            return new InvalidArchiveException("This zip file tries to "
                    + "write a file outside its own folder, so it was not "
                    + "opened." + NEW_COPY_ADVICE);
        }
        return new InvalidArchiveException("This zip file has a file name "
                + "that cannot be saved on this computer: "
                + printable(entryName) + FULL_STOP + NEW_COPY_ADVICE);
    }

    // Builds the error for an entry whose name is already used.
    private InvalidArchiveException clash(String entryName,
            Exception cause) {
        return new InvalidArchiveException("This zip file holds "
                + printable(entryName) + " twice, or two names that "
                + "differ only in upper and lower case." + NEW_COPY_ADVICE,
                cause);
    }

    // Says whether an entry name points outside the folder it is unpacked
    // into: an absolute name, a drive such as C: or a ".." part.
    private boolean escapesFolder(String entryName) {
        if (entryName.startsWith(ENTRY_SEPARATOR)
                || entryName.matches(DRIVE_PATTERN)) {
            return true;
        }
        for (String part : entryName.split(ENTRY_SEPARATOR)) {
            if (part.equals(PARENT_FOLDER)) {
                return true;
            }
        }
        return false;
    }

    // Says whether an entry is inside a question folder, such as Q1, at
    // any depth and in any case.
    private boolean isInQuestionFolder(String entryName) {
        String[] parts = entryName.split(ENTRY_SEPARATOR);
        // The last part is the entry's own name
        for (int index = 0; index < parts.length - 1; index++) {
            if (isQuestionFolderName(parts[index])) {
                return true;
            }
        }
        return false;
    }

    // Says whether a name is a configured question folder name, such as
    // Q1, in any case.
    private boolean isQuestionFolderName(String name) {
        for (String id : config.getList(ConfigKeys.QUESTION_IDS)) {
            String folder = config.getString(ConfigKeys.QUESTION_PREFIX
                    + id + ConfigKeys.QUESTION_FOLDER_SUFFIX);
            if (folder.equalsIgnoreCase(name)) {
                return true;
            }
        }
        return false;
    }

    // Returns an entry name with each control character shown as ?, so
    // a message stays on one line.
    private String printable(String entryName) {
        return entryName.replaceAll(CONTROL_CHARACTER, UNPRINTABLE);
    }

    // Returns a message that ends with a full stop.
    private String endSentence(String message) {
        String text = String.valueOf(message).trim();
        if (text.endsWith(FULL_STOP)) {
            return text;
        }
        return text + FULL_STOP;
    }

    // Says whether an entry name is one or more "./" in a row, such as
    // "./" or "././".
    private boolean isOnlyCurrentFolder(String entryName) {
        if (entryName.isEmpty()) {
            return false;
        }
        String rest = entryName;
        while (rest.startsWith(CURRENT_FOLDER)) {
            rest = rest.substring(CURRENT_FOLDER.length());
        }
        return rest.isEmpty();
    }

    // Ignore both the named file and every descendant of a named folder,
    // and the macOS resource fork beside each file, such as ._Q1a.java.
    private boolean isIgnored(String entryName, List<String> ignored) {
        for (String part : entryName.split(ENTRY_SEPARATOR)) {
            if (ignored.contains(part)
                    || part.startsWith(RESOURCE_FORK_PREFIX)) {
                return true;
            }
        }
        return false;
    }
}
