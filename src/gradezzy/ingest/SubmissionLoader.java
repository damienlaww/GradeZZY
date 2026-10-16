/*
 * File: SubmissionLoader.java
 *
 * Purpose: Turns the files and folders the instructor uploaded into Submission
 * objects.
 *
 * Where it fits: The first step of batch upload: it accepts many zips, a folder
 * of zips or one zip of zips, for one section.
 */
package gradezzy.ingest;

import java.io.IOException;
import java.nio.file.DirectoryStream;
import java.nio.file.Files;
import java.nio.file.LinkOption;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.Collections;
import java.util.Enumeration;
import java.util.HashSet;
import java.util.List;
import java.util.Locale;
import java.util.Set;
import java.util.zip.ZipEntry;
import java.util.zip.ZipFile;

import gradezzy.config.AppConfig;
import gradezzy.config.ConfigKeys;
import gradezzy.model.Section;
import gradezzy.model.Submission;
import gradezzy.util.FileUtils;
import gradezzy.util.TextUtils;

/**
 * Turns the files and folders the instructor uploaded into Submission objects.
 *
 * <p>Tier: MUST. Owner: Damien.
 */
public class SubmissionLoader {

    // Archive suffix, removed only at the end of a file name
    private static final String ZIP_EXTENSION = ".zip";

    // The application settings
    private final AppConfig config;

    /**
     * Creates a new submission loader.
     *
     * @param config the application settings
     */
    public SubmissionLoader(AppConfig config) {
        this.config = config;
    }

    /**
     * Creates one Submission per uploaded student archive or folder.
     * A folder containing configured question folders is one student;
     * otherwise its immediate children are individual submissions.
     * Non-ZIP files are retained so extraction reports their failures
     * without discarding the other students. Bulk ZIPs containing only
     * student ZIPs are unpacked under the working root and kept for retry.
     *
     * @param inputPaths the input paths
     * @param section the section
     * @return the submissions found, in file name order
     * @throws IngestException if the operation fails
     */
    public List<Submission> loadSubmissions(List<Path> inputPaths,
            Section section) throws IngestException {
        if (inputPaths == null || section == null) {
            throw new IngestException("Choose submissions and a section.");
        }
        List<Path> candidates = new ArrayList<>();
        try {
            for (Path input : inputPaths) {
                if (input == null) {
                    throw new IOException("A submission path is missing.");
                }
                if (!isIgnored(input)) {
                    collectInput(input, candidates);
                }
            }
        } catch (IOException exception) {
            throw new IngestException("Could not read submissions: "
                    + exception.getMessage(), exception);
        }

        // All candidates share the same comparison, even across uploads.
        List<Submission> submissions = new ArrayList<>();
        Set<String> usernames = new HashSet<>();
        Set<Path> seen = new HashSet<>();
        for (Path candidate : candidates) {
            if (seen.add(candidate.toAbsolutePath().normalize())) {
                String username = extractUsername(
                        candidate.getFileName().toString());
                if (TextUtils.isBlank(username)) {
                    throw new IngestException("No username in " + candidate);
                }
                if (!usernames.add(username.toLowerCase(Locale.ROOT))) {
                    throw new IngestException("Duplicate submission for "
                            + username + ". Choose one file per student.");
                }
                submissions.add(new Submission(username, section, candidate));
            }
        }
        // Insertion sort avoids a comparator/lambda for this short list.
        for (int index = 1; index < submissions.size(); index++) {
            Submission next = submissions.get(index);
            int position = index;
            while (position > 0 && submissions.get(position - 1)
                    .getOriginalPath().getFileName().toString().compareTo(
                            next.getOriginalPath().getFileName().toString())
                    > 0) {
                submissions.set(position, submissions.get(position - 1));
                position--;
            }
            submissions.set(position, next);
        }
        return submissions;
    }

    /**
     * Works out the username from a zip file name by removing the configured
     * prefix and the .zip ending.
     *
     * @param zipFileName the zip file name
     * @return the username, or an empty string if none is found
     */
    public String extractUsername(String zipFileName) {
        if (zipFileName == null) {
            return "";
        }
        String username = zipFileName.replaceFirst(
                config.getString(ConfigKeys.ZIP_NAME_PREFIX_PATTERN), "");
        if (username.toLowerCase(Locale.ROOT).endsWith(ZIP_EXTENSION)) {
            return username.substring(0,
                    username.length() - ZIP_EXTENSION.length());
        }
        return username;
    }

    // A direct folder upload can also be an already-unpacked student.
    private void collectInput(Path input, List<Path> candidates)
            throws IOException, IngestException {
        if (Files.isSymbolicLink(input) || !Files.isReadable(input)) {
            throw new IOException("Unreadable or linked path: " + input);
        }
        if (Files.isDirectory(input, LinkOption.NOFOLLOW_LINKS)) {
            if (hasQuestionFolder(input)) {
                candidates.add(input);
            } else {
                collectChildren(input, candidates);
            }
        } else if (Files.isRegularFile(input, LinkOption.NOFOLLOW_LINKS)) {
            if (isBulkArchive(input)) {
                unpackBatch(input, candidates);
            } else {
                candidates.add(input);
            }
        } else {
            throw new IOException("Not a file or folder: " + input);
        }
    }

    // Directory iteration order differs between operating systems.
    private void collectChildren(Path directory, List<Path> candidates)
            throws IOException {
        List<Path> children = new ArrayList<>();
        try (DirectoryStream<Path> entries =
                Files.newDirectoryStream(directory)) {
            for (Path child : entries) {
                if (!isIgnored(child)) {
                    children.add(child);
                }
            }
        }
        Collections.sort(children);
        candidates.addAll(children);
    }

    private boolean hasQuestionFolder(Path directory) {
        for (String id : config.getList(ConfigKeys.QUESTION_IDS)) {
            String folder = config.getString(ConfigKeys.QUESTION_PREFIX
                    + id + ConfigKeys.QUESTION_FOLDER_SUFFIX);
            if (Files.isDirectory(directory.resolve(folder),
                    LinkOption.NOFOLLOW_LINKS)) {
                return true;
            }
        }
        return false;
    }

    // Inspect metadata only. Corrupt student archives fail in ExtractStage.
    private boolean isBulkArchive(Path input) {
        if (!input.getFileName().toString().toLowerCase(Locale.ROOT)
                .endsWith(ZIP_EXTENSION)) {
            return false;
        }
        boolean foundZip = false;
        try (ZipFile zip = new ZipFile(input.toFile())) {
            Enumeration<? extends ZipEntry> entries = zip.entries();
            while (entries.hasMoreElements()) {
                ZipEntry entry = entries.nextElement();
                if (!entry.isDirectory() && !isIgnored(Path.of(
                        entry.getName()))) {
                    if (!entry.getName().toLowerCase(Locale.ROOT)
                            .endsWith(ZIP_EXTENSION)) {
                        return false;
                    }
                    foundZip = true;
                }
            }
        } catch (IOException | java.nio.file.InvalidPathException exception) {
            return false;
        }
        return foundZip;
    }

    // Each batch gets its own directory so original inner ZIPs survive
    // retries without overwriting another upload or a student working copy.
    private void unpackBatch(Path input, List<Path> candidates)
            throws IOException, IngestException {
        Path root = Path.of(config.getString(ConfigKeys.WORKING_DIR))
                .toAbsolutePath().normalize();
        ZipExtractor extractor = new ZipExtractor(config);
        if (!extractor.isSafeEntry("batch", root)) {
            throw new IOException("The working folder contains a link.");
        }
        Files.createDirectories(root);
        Path batch = Files.createTempDirectory(root, ".batch-");
        try {
            extractor.extract(input, batch);
            collectBatchZips(batch, candidates);
        } catch (IOException | IngestException exception) {
            try {
                FileUtils.deleteDirectory(batch);
            } catch (IOException cleanup) {
                exception.addSuppressed(cleanup);
            }
            throw exception;
        }
    }

    private void collectBatchZips(Path directory, List<Path> candidates)
            throws IOException {
        try (DirectoryStream<Path> children =
                Files.newDirectoryStream(directory)) {
            for (Path child : children) {
                if (Files.isDirectory(child, LinkOption.NOFOLLOW_LINKS)) {
                    collectBatchZips(child, candidates);
                } else {
                    candidates.add(child);
                }
            }
        }
    }

    private boolean isIgnored(Path path) {
        List<String> ignored = config.getList(ConfigKeys.IGNORED_FILE_NAMES);
        for (Path part : path) {
            if (ignored.contains(part.toString())) {
                return true;
            }
        }
        return false;
    }
}
