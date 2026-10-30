/*
 * File: SubmissionLoader.java
 *
 * Purpose: Turns the files and folders the instructor uploaded into Submission
 * objects.
 *
 * Where it fits: The first step of batch upload: it accepts many zips, a folder
 * of zips, or an explicitly selected bulk archive, for one section.
 */
package gradezzy.ingest;

import java.io.IOException;
import java.nio.file.DirectoryStream;
import java.nio.file.Files;
import java.nio.file.LinkOption;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.Collections;
import java.util.HashSet;
import java.util.List;
import java.util.Locale;
import java.util.Set;

import gradezzy.config.AppConfig;
import gradezzy.config.ConfigKeys;
import gradezzy.model.Anomaly;
import gradezzy.model.AnomalyType;
import gradezzy.model.Severity;
import gradezzy.model.SubmissionStatus;
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
     * without discarding the other students. A ZIP always belongs to the
     * student named by its outer filename; use loadBatchArchive explicitly
     * for a ZIP of student ZIPs.
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
        for (Path candidate : candidates) {
            String username = extractUsername(
                    candidate.getFileName().toString());
            Submission submission = new Submission(username, section,
                    candidate);
            if (TextUtils.isBlank(username)) {
                reject(submission, "No username in " + candidate);
            } else if (!usernames.add(username.toLowerCase(Locale.ROOT))) {
                reject(submission, "Duplicate submission for " + username
                        + ". Choose one file per student.");
            }
            submissions.add(submission);
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
            candidates.add(input);
        } else {
            throw new IOException("Not a file or folder: " + input);
        }
    }

    // Directory iteration order differs between operating systems.
    private void collectChildren(Path directory, List<Path> candidates)
            throws IOException {
        List<Path> children = new ArrayList<>();
        DirectoryStream<Path> entries = null;
        try {
            entries = Files.newDirectoryStream(directory);
            for (Path child : entries) {
                if (!isIgnored(child)) {
                    children.add(child);
                }
            }
        } finally {
            FileUtils.closeIfOpen(entries);
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

    /**
     * Loads a ZIP explicitly identified by the caller as a batch container.
     * Ordinary uploads never infer student identities from nested ZIPs.
     *
     * @param archive the batch ZIP
     * @param section the section
     * @return the student submissions
     * @throws IngestException if the batch archive cannot be unpacked
     */
    public List<Submission> loadBatchArchive(Path archive, Section section)
            throws IngestException {
        if (archive == null || section == null) {
            throw new IngestException("Choose a batch archive and a section.");
        }
        List<Path> candidates = new ArrayList<>();
        try {
            unpackBatch(archive, candidates);
        } catch (IOException exception) {
            throw new IngestException("Could not read batch archive: "
                    + exception.getMessage(), exception);
        }
        Collections.sort(candidates);
        return loadSubmissions(candidates, section);
    }

    private void reject(Submission submission, String message) {
        submission.setIngestFailure(message);
        submission.setStatus(SubmissionStatus.FAILED);
        submission.addAnomaly(new Anomaly(
                "INGEST-" + java.util.UUID.randomUUID(),
                AnomalyType.INVALID_ARCHIVE, Severity.ERROR,
                submission.getUsername(), message,
                "Correct this upload and add it to a new batch."));
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
        } catch (IOException exception) {
            deleteFailedBatch(batch, exception);
            throw exception;
        } catch (IngestException exception) {
            deleteFailedBatch(batch, exception);
            throw exception;
        }
    }

    // Removes a half-unpacked batch folder. A failure to remove it is
    // attached to the original error instead of hiding it.
    private void deleteFailedBatch(Path batch, Exception failure) {
        try {
            FileUtils.deleteDirectory(batch);
        } catch (IOException cleanup) {
            failure.addSuppressed(cleanup);
        }
    }

    private void collectBatchZips(Path directory, List<Path> candidates)
            throws IOException {
        DirectoryStream<Path> children = null;
        try {
            children = Files.newDirectoryStream(directory);
            for (Path child : children) {
                if (Files.isDirectory(child, LinkOption.NOFOLLOW_LINKS)) {
                    collectBatchZips(child, candidates);
                } else {
                    candidates.add(child);
                }
            }
        } finally {
            FileUtils.closeIfOpen(children);
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
