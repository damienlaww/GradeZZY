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
import java.util.List;

import gradezzy.config.AppConfig;
import gradezzy.config.ConfigKeys;
import gradezzy.model.Assessment;
import gradezzy.model.Submission;
import gradezzy.util.FileUtils;
import gradezzy.util.TextUtils;

/**
 * Creates and removes the working copy of each submission.
 *
 * <p>Tier: MUST. Owner: Damien.
 */
public class WorkingCopyManager {

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
     *
     * @param submission the submission
     * @return the path of the new working copy
     * @throws IngestException if the operation fails
     */
    public Path createWorkingCopy(
            Submission submission) throws IngestException {
        Path staging = null;
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
                zipExtractor.extract(original, staging);
            }

            // Build first, so a corrupt retry keeps the previous copy.
            FileUtils.deleteDirectory(target);
            Files.move(staging, target);
            submission.setWorkingCopyPath(target);
            return target;
        } catch (IOException exception) {
            throw copyFailure(submission, staging, exception);
        } catch (IngestException exception) {
            throw copyFailure(submission, staging, exception);
        }
    }

    /**
     * Deletes compiled files the student submitted, keeping the provided ones
     * listed in config.
     *
     * @param workingCopy the working copy
     * @param assessment the assessment
     * @throws UncheckedIOException if cleanup fails or is outside the
     *         configured working folder
     */
    public void removeStudentClassFiles(Path workingCopy,
            Assessment assessment) {
        try {
            requireWorkingChild(workingCopy);
            removeClassFiles(workingCopy,
                    config.getList(ConfigKeys.PROVIDED_CLASS_FILES));
        } catch (IOException exception) {
            throw new UncheckedIOException("Could not remove student "
                    + "class files: " + exception.getMessage(), exception);
        }
    }

    /**
     * Removes the working copy of a submission.
     *
     * @param submission the submission
     * @throws UncheckedIOException if the copy cannot safely be removed
     */
    public void deleteWorkingCopy(Submission submission) {
        if (submission.getWorkingCopyPath() == null) {
            return;
        }
        try {
            Path expected = workingPath(submission);
            Path actual = submission.getWorkingCopyPath()
                    .toAbsolutePath().normalize();
            if (!actual.equals(expected)) {
                throw new IOException("Unexpected working copy path.");
            }
            Path original = submission.getOriginalPath().toRealPath();
            if (original.startsWith(actual) || actual.startsWith(original)) {
                throw new IOException("Original and working copy overlap.");
            }
            FileUtils.deleteDirectory(actual);
            submission.setWorkingCopyPath(null);
        } catch (IOException exception) {
            throw new UncheckedIOException("Could not delete working copy: "
                    + exception.getMessage(), exception);
        }
    }

    // Removes the half-built staging folder, if any, and builds the error
    // for a working copy that could not be created. A failure to remove
    // the folder is attached to the original error instead of hiding it.
    private IngestException copyFailure(Submission submission, Path staging,
            Exception exception) {
        if (staging != null) {
            try {
                FileUtils.deleteDirectory(staging);
            } catch (IOException cleanup) {
                exception.addSuppressed(cleanup);
            }
        }
        return new IngestException("Could not create the working copy "
                + "for " + submission.getUsername() + ": "
                + exception.getMessage(), exception);
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
                    if (name.endsWith(".class") && !provided.contains(name)) {
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
