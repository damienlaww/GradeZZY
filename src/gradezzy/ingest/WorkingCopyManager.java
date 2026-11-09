/*
 * File: WorkingCopyManager.java
 *
 * Purpose: Creates and removes the working copy of each submission.
 *
 * Where it fits: Guarantees that original uploads are never modified: every fix
 * and every tester run happens in the working copy.
 */
package gradezzy.ingest;

import java.io.IOException;
import java.io.UncheckedIOException;
import java.nio.file.DirectoryStream;
import java.nio.file.Files;
import java.nio.file.LinkOption;
import java.nio.file.Path;
import java.nio.file.StandardCopyOption;
import java.util.ArrayList;
import java.util.List;
import java.util.Locale;
import java.util.UUID;

import gradezzy.config.AppConfig;
import gradezzy.config.ConfigKeys;
import gradezzy.model.Anomaly;
import gradezzy.model.AnomalyType;
import gradezzy.model.Assessment;
import gradezzy.model.Question;
import gradezzy.model.Severity;
import gradezzy.model.Submission;
import gradezzy.util.FileUtils;
import gradezzy.util.TextUtils;

/**
 * Creates and removes the working copy of each submission.
 *
 * <p>Tier: MUST. Owner: Damien.
 */
public class WorkingCopyManager {

    // Archive suffix of a zip inside a zip
    private static final String ZIP_EXTENSION = ".zip";

    // Ending of every compiled Java file
    private static final String CLASS_EXTENSION = ".class";

    // Start of the folder an inner zip is moved to while it is unpacked
    private static final String INNER_ZIP_PREFIX = ".inner-zip-";

    // A zip of a zip holds exactly this many files
    private static final int SINGLE_FILE = 1;

    // The text in front of the id of every anomaly raised here
    private static final String ANOMALY_ID_PREFIX = "INGEST-";

    // The application settings
    private final AppConfig config;

    // The helper that unzips archives
    private final ZipExtractor zipExtractor;

    /**
     * Creates a new working copy manager.
     *
     * @param config the application settings
     * @param zipExtractor the helper that unzips archives
     */
    public WorkingCopyManager(AppConfig config, ZipExtractor zipExtractor) {
        this.config = config;
        this.zipExtractor = zipExtractor;
    }

    /**
     * Unzips or copies a submission into the working folder, attaches
     * its path and returns it. A failed unpack keeps the previous copy.
     * A zip that holds nothing but one zip, as when a student zips their
     * zip, is unpacked one level further. Each file left out because its
     * name cannot be saved is named in a warning on the submission.
     *
     * @param submission the submission
     * @return the path of the new working copy
     * @throws InvalidArchiveException if the zip is damaged, unsafe, too
     *         big or holds two files with one name
     * @throws IngestException if the operation fails for another reason
     */
    public Path createWorkingCopy(
            Submission submission) throws IngestException {
        Path staging = null;
        List<String> skippedNames = new ArrayList<>();
        try {
            Path target = workingPath(submission);
            Path original = submission.getOriginalPath().toRealPath();
            if (original.startsWith(target) || target.startsWith(original)) {
                throw new IOException("Original and working copy overlap.");
            }
            Files.createDirectories(target.getParent());
            staging = Files.createTempDirectory(target.getParent(),
                    ".unpacking-");
            if (Files.isDirectory(original, LinkOption.NOFOLLOW_LINKS)) {
                FileUtils.copyDirectory(original, staging);
                removeIgnoredFiles(staging);
            } else {
                zipExtractor.extract(original, staging, skippedNames);
                unpackOnlyInnerZip(staging, skippedNames);
            }

            // Build first, so a corrupt retry keeps the previous copy.
            FileUtils.deleteDirectory(target);
            FileUtils.moveWithRetry(staging, target);
            submission.setWorkingCopyPath(target);
            warnSkipped(submission, skippedNames);
            return target;
        } catch (InvalidArchiveException exception) {
            // Already one plain sentence about the zip, so it is kept
            removeStaging(staging, exception);
            throw exception;
        } catch (IOException exception) {
            throw copyFailure(submission, staging, exception);
        } catch (IngestException exception) {
            throw copyFailure(submission, staging, exception);
        }
    }

    /**
     * Deletes compiled files the student submitted, keeping the provided ones
     * listed in config, then replaces each provided one with the copy in
     * the starter folder of paths.starter.dir.
     *
     * @param workingCopy the working copy
     * @param assessment the assessment
     * @throws UncheckedIOException if cleanup fails or is outside the
     *         configured working folder
     */
    public void removeStudentClassFiles(Path workingCopy,
            Assessment assessment) {
        removeStudentClassFiles(workingCopy, assessment, null);
    }

    /**
     * Deletes compiled files the student submitted, keeping the provided
     * ones listed in assessment.provided.class.files. Each provided one
     * the starter folder holds, such as Q2/DataException.class, is then
     * copied into every matching question folder of the working copy,
     * replacing the student's copy, so a missing or edited file cannot
     * change the marks. A provided file the starter folder lacks is left
     * as the student sent it.
     *
     * <p>Tier: MUST.
     *
     * @param workingCopy the working copy
     * @param assessment the assessment, whose questions name the folders
     * @param starterDirectory the starter folder chosen for the batch, or
     *        null for paths.starter.dir
     * @throws UncheckedIOException if cleanup fails or is outside the
     *         configured working folder
     */
    public void removeStudentClassFiles(Path workingCopy,
            Assessment assessment, Path starterDirectory) {
        try {
            requireWorkingChild(workingCopy);
            List<String> provided = config.getList(
                    ConfigKeys.PROVIDED_CLASS_FILES);
            removeClassFiles(workingCopy, provided);

            Path starter = starterDirectory;
            if (starter == null) {
                starter = Path.of(config.getString(ConfigKeys.STARTER_DIR));
            }
            if (assessment != null) {
                copyProvidedClassFiles(workingCopy, starter, assessment,
                        provided);
            }
        } catch (IOException exception) {
            throw new UncheckedIOException("Could not remove student "
                    + "class files: " + exception.getMessage(), exception);
        }
    }

    // Removes the half-built staging folder, if any, and builds the error
    // for a working copy that could not be created. A failure to remove
    // the folder is attached to the original error instead of hiding it.
    private IngestException copyFailure(Submission submission, Path staging,
            Exception exception) {
        removeStaging(staging, exception);
        return new IngestException("Could not create the working copy "
                + "for " + submission.getUsername() + ": "
                + exception.getMessage(), exception);
    }

    // Removes the half-built staging folder, if any. A failure to remove
    // it is attached to the original error instead of hiding it.
    private void removeStaging(Path staging, Exception exception) {
        if (staging != null) {
            try {
                FileUtils.deleteDirectory(staging);
            } catch (IOException cleanup) {
                exception.addSuppressed(cleanup);
            }
        }
    }

    // A student who zips their zip sends a zip that holds one zip and
    // nothing else. That inner zip is unpacked in its place, with the
    // same limits, and only one level deep.
    private void unpackOnlyInnerZip(Path folder, List<String> skippedNames)
            throws IOException, IngestException {
        Path innerZip = findOnlyZip(folder);
        if (innerZip == null) {
            return;
        }
        Path holder = Files.createTempDirectory(folder.getParent(),
                INNER_ZIP_PREFIX);
        try {
            Path moved = Files.move(innerZip,
                    holder.resolve(innerZip.getFileName()));
            zipExtractor.extract(moved, folder, skippedNames);
        } finally {
            FileUtils.deleteDirectory(holder);
        }
    }

    // Returns the only thing in a folder when it is an ordinary file
    // ending in .zip, in any case, or null otherwise.
    private Path findOnlyZip(Path folder) throws IOException {
        List<Path> children = new ArrayList<>();
        DirectoryStream<Path> entries = null;
        try {
            entries = Files.newDirectoryStream(folder);
            for (Path child : entries) {
                children.add(child);
            }
        } finally {
            FileUtils.closeIfOpen(entries);
        }
        if (children.size() != SINGLE_FILE) {
            return null;
        }
        Path only = children.get(0);
        String name = only.getFileName().toString().toLowerCase(Locale.ROOT);
        if (Files.isRegularFile(only, LinkOption.NOFOLLOW_LINKS)
                && name.endsWith(ZIP_EXTENSION)) {
            return only;
        }
        return null;
    }

    // Names, in one warning, the files of the zip that were left out
    // because their names cannot be saved on this computer.
    private void warnSkipped(Submission submission,
            List<String> skippedNames) {
        if (skippedNames.isEmpty()) {
            return;
        }
        submission.addAnomaly(new Anomaly(ANOMALY_ID_PREFIX
                + UUID.randomUUID(), AnomalyType.INVALID_ARCHIVE,
                Severity.WARNING, submission.getUsername(),
                "Left out of the zip file, because the name cannot be "
                + "saved on this computer: "
                + TextUtils.join(", ", skippedNames) + ".",
                "Check that no answer file is missing."));
    }

    // Copies each provided class file the starter folder holds into the
    // matching question folders of the working copy.
    private void copyProvidedClassFiles(Path workingCopy, Path starter,
            Assessment assessment, List<String> provided)
            throws IOException {
        List<String> folderNames = new ArrayList<>();
        for (Question question : assessment.getQuestions()) {
            if (!folderNames.contains(question.getFolderName())) {
                folderNames.add(question.getFolderName());
            }
        }
        for (String folderName : folderNames) {
            for (String className : provided) {
                Path source = starter.resolve(folderName).resolve(className);
                if (className.endsWith(CLASS_EXTENSION)
                        && Files.isRegularFile(source,
                                LinkOption.NOFOLLOW_LINKS)) {
                    copyIntoQuestionFolders(workingCopy, folderName,
                            source);
                }
            }
        }
    }

    // Copies a file into every folder named like a question folder, in
    // any case and at any depth, replacing a file of the same name. A
    // question folder is not searched further, so Q2/Q2 is not filled.
    private void copyIntoQuestionFolders(Path directory, String folderName,
            Path source) throws IOException {
        DirectoryStream<Path> children = null;
        try {
            children = Files.newDirectoryStream(directory);
            for (Path child : children) {
                boolean folder = Files.isDirectory(child,
                        LinkOption.NOFOLLOW_LINKS);
                String name = child.getFileName().toString();
                if (folder && name.equalsIgnoreCase(folderName)) {
                    Files.copy(source, child.resolve(source.getFileName()),
                            StandardCopyOption.REPLACE_EXISTING);
                } else if (folder) {
                    copyIntoQuestionFolders(child, folderName, source);
                }
            }
        } finally {
            FileUtils.closeIfOpen(children);
        }
    }

    // A username is one directory name, never an arbitrary path.
    private Path workingPath(Submission submission) throws IOException {
        String username = submission.getUsername();
        if (TextUtils.isBlank(username) || username.equals(".")
                || username.equals("..") || username.contains("/")
                || username.contains("\\") || username.contains(":")) {
            throw new IOException("Invalid submission username.");
        }
        Path root = Path.of(config.getString(ConfigKeys.WORKING_DIR))
                .toAbsolutePath().normalize();
        Path target = root.resolve(username).normalize();
        requireWorkingChild(target);
        return target;
    }

    // Restrict destructive operations to one direct child of the root.
    private void requireWorkingChild(Path path) throws IOException {
        Path root = Path.of(config.getString(ConfigKeys.WORKING_DIR))
                .toAbsolutePath().normalize();
        Path target = path.toAbsolutePath().normalize();
        if (!root.equals(target.getParent())) {
            throw new IOException("Path is outside the working folder.");
        }
        if (!zipExtractor.isSafeEntry(target.getFileName().toString(),
                root)) {
            throw new IOException("Working path contains a symbolic link.");
        }
    }

    // Only ordinary files are deleted; links are rejected, not followed.
    private void removeClassFiles(Path directory, List<String> provided)
            throws IOException {
        DirectoryStream<Path> children = null;
        try {
            children = Files.newDirectoryStream(directory);
            for (Path child : children) {
                if (Files.isSymbolicLink(child)) {
                    throw new IOException("Linked working file: " + child);
                }
                if (Files.isDirectory(child, LinkOption.NOFOLLOW_LINKS)) {
                    removeClassFiles(child, provided);
                } else {
                    String name = child.getFileName().toString();
                    if (name.endsWith(CLASS_EXTENSION)
                            && !provided.contains(name)) {
                        Files.delete(child);
                    }
                }
            }
        } finally {
            FileUtils.closeIfOpen(children);
        }
    }

    // Apply the same ignored-name setting to folder uploads as ZIPs.
    private void removeIgnoredFiles(Path directory) throws IOException {
        List<String> ignored = config.getList(ConfigKeys.IGNORED_FILE_NAMES);
        DirectoryStream<Path> children = null;
        try {
            children = Files.newDirectoryStream(directory);
            for (Path child : children) {
                if (ignored.contains(child.getFileName().toString())) {
                    FileUtils.deleteDirectory(child);
                } else if (Files.isDirectory(child,
                        LinkOption.NOFOLLOW_LINKS)) {
                    removeIgnoredFiles(child);
                }
            }
        } finally {
            FileUtils.closeIfOpen(children);
        }
    }
}
