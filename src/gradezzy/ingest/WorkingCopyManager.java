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

    // Ending of every Java source file
    private static final String JAVA_EXTENSION = ".java";

    // Separates a nested class from its outer class in a class file name,
    // as in Order$1.class
    private static final String NESTED_SEPARATOR = "$";

    // How many folders deep Java source is looked for, in the working
    // copy and in the starter folder
    private static final int MAX_SOURCE_DEPTH = 16;

    // The depth of the folder a search for Java source starts in
    private static final int TOP_DEPTH = 0;

    // Start of the name of a macOS resource fork file, as in ._Q1a.java
    private static final String RESOURCE_FORK_PREFIX = "._";

    // Start of the folder an inner zip is moved to while it is unpacked
    private static final String INNER_ZIP_PREFIX = ".inner-zip-";

    // Start of the folder a submission is unpacked in before it is moved
    private static final String STAGING_PREFIX = ".unpacking-";

    // A zip of a zip holds exactly this many files
    private static final int SINGLE_FILE = 1;

    // The text in front of the id of every anomaly raised here
    private static final String ANOMALY_ID_PREFIX = "INGEST-";

    // Separates the names in the warning about files left out
    private static final String NAME_SEPARATOR = ", ";

    // Names that point at the current or the parent folder
    private static final String CURRENT_FOLDER = ".";
    private static final String PARENT_FOLDER = "..";

    // Characters that would make a username a path instead of one name:
    // the macOS and Linux separator, the Windows one and a drive colon
    private static final String SLASH = "/";
    private static final String BACKSLASH = "\\";
    private static final String COLON = ":";

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
     * name cannot be saved is named in a warning on the submission, and so
     * is each name unpacked without the spaces or dots at its end.
     *
     * @param submission the submission
     * @return the path of the new working copy
     * @throws InvalidArchiveException if the zip is damaged, unsafe, too
     *         big or holds two files with one name
     * @throws IngestException if the operation fails for another reason
     */
    public Path createWorkingCopy(
            Submission submission) throws IngestException {
        return createWorkingCopy(submission, null);
    }

    /**
     * Unzips or copies a submission into the folder of its run inside
     * the working folder, as paths.working.dir/RUN-1794337883768/alice,
     * attaches its path and returns it. Each run keeps its own copies, so
     * grading the same student in another section or assessment never
     * replaces the files an earlier run reviews. Without a run id the
     * copy goes straight into the working folder, as before.
     *
     * <p>Tier: MUST.
     *
     * @param submission the submission
     * @param runId the id of the run, or null for no run folder
     * @return the path of the new working copy
     * @throws InvalidArchiveException if the zip is damaged, unsafe, too
     *         big or holds two files with one name
     * @throws IngestException if the operation fails for another reason
     */
    public Path createWorkingCopy(Submission submission,
            String runId) throws IngestException {
        Path staging = null;
        List<String> skippedNames = new ArrayList<>();
        List<String> renamedNames = new ArrayList<>();
        try {
            Path target = workingPath(submission, runId);
            Path original = submission.getOriginalPath().toRealPath();
            if (original.startsWith(target) || target.startsWith(original)) {
                throw new IOException("Original and working copy overlap.");
            }
            Files.createDirectories(target.getParent());
            staging = Files.createTempDirectory(target.getParent(),
                    STAGING_PREFIX);
            if (Files.isDirectory(original, LinkOption.NOFOLLOW_LINKS)) {
                FileUtils.copyDirectory(original, staging);
                removeIgnoredFiles(staging);
            } else {
                zipExtractor.extract(original, staging, skippedNames,
                        renamedNames);
                unpackOnlyInnerZip(staging, skippedNames, renamedNames);
            }

            // Build first, so a corrupt retry keeps the previous copy.
            FileUtils.deleteDirectory(target);
            FileUtils.moveWithRetry(staging, target);
            submission.setWorkingCopyPath(target);
            warnSkipped(submission, skippedNames);
            warnRenamed(submission, renamedNames);
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
     * ones listed in assessment.provided.class.files. A class file whose
     * class has no Java source in the working copy or the starter folder,
     * such as an Order.class the assessment forgot to list, is kept too:
     * it cannot be built again, so deleting it could only stop the
     * question compiling. A question's main class and a class named like
     * a tester are always deleted. Each provided one
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
            Path starter = chooseStarter(starterDirectory);
            List<String> sourceNames = listSourceNames(workingCopy, starter,
                    assessment);
            removeClassFiles(workingCopy, provided, sourceNames);

            if (assessment != null) {
                copyFromStarter(workingCopy, starter, assessment, provided);
            }
        } catch (IOException exception) {
            throw new UncheckedIOException("Could not remove student "
                    + "class files: " + exception.getMessage(), exception);
        }
    }

    /**
     * Copies each provided class file the starter folder holds into every
     * matching question folder of the working copy, as
     * removeStudentClassFiles does, without deleting anything. FixStage
     * calls it after a fix made or moved question folders, so a folder
     * made by a fix gets its provided class files too.
     *
     * <p>Tier: MUST.
     *
     * @param workingCopy the working copy
     * @param assessment the assessment, whose questions name the folders
     * @param starterDirectory the starter folder chosen for the batch, or
     *        null for paths.starter.dir
     * @throws UncheckedIOException if a file cannot be copied or the
     *         working copy is outside the configured working folder
     */
    public void copyProvidedClassFiles(Path workingCopy,
            Assessment assessment, Path starterDirectory) {
        Path starter = chooseStarter(starterDirectory);
        try {
            requireWorkingChild(workingCopy);
            copyFromStarter(workingCopy, starter, assessment,
                    config.getList(ConfigKeys.PROVIDED_CLASS_FILES));
        } catch (IOException exception) {
            throw new UncheckedIOException("Could not copy the provided "
                    + "class files: " + exception.getMessage(), exception);
        }
    }

    // Returns the starter folder chosen for the batch, or the one in
    // paths.starter.dir when none was chosen.
    private Path chooseStarter(Path starterDirectory) {
        if (starterDirectory == null) {
            return Path.of(config.getString(ConfigKeys.STARTER_DIR));
        }
        return starterDirectory;
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
        if (staging == null) {
            return;
        }

        try {
            FileUtils.deleteDirectory(staging);
        } catch (IOException cleanup) {
            exception.addSuppressed(cleanup);
        }
    }

    // A student who zips their zip sends a zip that holds one zip and
    // nothing else. That inner zip is unpacked in its place, with the
    // same limits, and only one level deep.
    private void unpackOnlyInnerZip(Path folder, List<String> skippedNames,
            List<String> renamedNames) throws IOException, IngestException {
        Path innerZip = findOnlyZip(folder);
        if (innerZip == null) {
            return;
        }
        Path holder = Files.createTempDirectory(folder.getParent(),
                INNER_ZIP_PREFIX);
        try {
            Path destination = holder.resolve(innerZip.getFileName());
            Path moved = Files.move(innerZip, destination);
            zipExtractor.extract(moved, folder, skippedNames, renamedNames);
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
        boolean zipFile = Files.isRegularFile(only, LinkOption.NOFOLLOW_LINKS)
                && name.endsWith(ZIP_EXTENSION);
        if (zipFile) {
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
        String id = ANOMALY_ID_PREFIX + UUID.randomUUID();
        String names = TextUtils.join(NAME_SEPARATOR, skippedNames);
        submission.addAnomaly(new Anomaly(id, AnomalyType.INVALID_ARCHIVE,
                Severity.WARNING, submission.getUsername(),
                "Left out of the zip file, because the name cannot be "
                + "saved on this computer: " + names + ".",
                "Check that no answer file is missing."));
    }

    // Names, in one warning, the folders and files of the zip unpacked
    // under a shorter name, because a name that ends with a space or a
    // dot cannot be saved on Windows.
    private void warnRenamed(Submission submission,
            List<String> renamedNames) {
        if (renamedNames.isEmpty()) {
            return;
        }
        String id = ANOMALY_ID_PREFIX + UUID.randomUUID();
        String names = TextUtils.join(NAME_SEPARATOR, renamedNames);
        submission.addAnomaly(new Anomaly(id, AnomalyType.INVALID_ARCHIVE,
                Severity.WARNING, submission.getUsername(),
                "Unpacked under a shorter name, because a name that ends "
                + "with a space or a dot cannot be saved on Windows: "
                + names + ".",
                "Nothing to do: the files were graded under the shorter "
                + "name."));
    }

    // Copies each provided class file the starter folder holds into the
    // matching question folders of the working copy.
    private void copyFromStarter(Path workingCopy, Path starter,
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
                boolean providedHere = className.endsWith(CLASS_EXTENSION)
                        && Files.isRegularFile(source,
                                LinkOption.NOFOLLOW_LINKS);
                if (providedHere) {
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
                    Path target = child.resolve(source.getFileName());
                    Files.copy(source, target,
                            StandardCopyOption.REPLACE_EXISTING);
                } else if (folder) {
                    copyIntoQuestionFolders(child, folderName, source);
                }
            }
        } finally {
            FileUtils.closeIfOpen(children);
        }
    }

    // A username and a run id are each one directory name, never an
    // arbitrary path.
    private Path workingPath(Submission submission, String runId)
            throws IOException {
        String username = submission.getUsername();
        if (!isOneName(username)) {
            throw new IOException("Invalid submission username.");
        }
        Path root = Path.of(config.getString(ConfigKeys.WORKING_DIR))
                .toAbsolutePath().normalize();
        Path folder = root;
        if (runId != null) {
            if (!isOneName(runId)) {
                throw new IOException("Invalid run id.");
            }
            folder = root.resolve(runId);
        }
        Path target = folder.resolve(username).normalize();
        requireWorkingChild(target);
        return target;
    }

    // Says whether a name is one folder name: not blank, not a link to
    // the current or parent folder and without path separators.
    private boolean isOneName(String name) {
        boolean notOneName = TextUtils.isBlank(name)
                || name.equals(CURRENT_FOLDER)
                || name.equals(PARENT_FOLDER)
                || name.contains(SLASH)
                || name.contains(BACKSLASH)
                || name.contains(COLON);
        return !notOneName;
    }

    // Restricts destructive operations to a working copy inside the
    // root: a run's copy in root/runId/username, or one made before runs
    // had their own folders, straight in root/username.
    private void requireWorkingChild(Path path) throws IOException {
        Path root = Path.of(config.getString(ConfigKeys.WORKING_DIR))
                .toAbsolutePath().normalize();
        Path target = path.toAbsolutePath().normalize();
        Path parent = target.getParent();
        boolean directChild = root.equals(parent);
        boolean runChild = parent != null && root.equals(parent.getParent());
        if (!directChild && !runChild) {
            throw new IOException("Path is outside the working folder.");
        }
        String name = root.relativize(target).toString();
        if (!zipExtractor.isSafeEntry(name, root)) {
            throw new IOException("Working path contains a symbolic link.");
        }
    }

    // Lists, in lower case, the classes built from source when graded:
    // each one with a .java file in the working copy or the starter
    // folder, and each question's main class.
    private List<String> listSourceNames(Path workingCopy, Path starter,
            Assessment assessment) throws IOException {
        List<String> names = new ArrayList<>();
        addSourceNames(workingCopy, TOP_DEPTH, names);
        if (assessment != null) {
            for (Question question : assessment.getQuestions()) {
                addSourceName(question.getFileName(), names);
            }
        }
        try {
            if (Files.isDirectory(starter, LinkOption.NOFOLLOW_LINKS)) {
                addSourceNames(starter, TOP_DEPTH, names);
            }
        } catch (IOException exception) {
            // A starter folder that cannot be read adds no names; the
            // question files of the working copy are still listed
            return names;
        }
        return names;
    }

    // Adds the class name of each .java file in a folder and the folders
    // inside it, down to MAX_SOURCE_DEPTH. Links are not followed.
    private void addSourceNames(Path directory, int depth,
            List<String> names) throws IOException {
        DirectoryStream<Path> children = null;
        try {
            children = Files.newDirectoryStream(directory);
            for (Path child : children) {
                boolean folder = Files.isDirectory(child,
                        LinkOption.NOFOLLOW_LINKS);
                if (folder && depth < MAX_SOURCE_DEPTH) {
                    int childDepth = depth + 1;
                    addSourceNames(child, childDepth, names);
                } else if (!folder) {
                    addSourceName(child.getFileName().toString(), names);
                }
            }
        } finally {
            FileUtils.closeIfOpen(children);
        }
    }

    // Adds the class name of a .java file name, in lower case, once.
    private void addSourceName(String fileName, List<String> names) {
        String lower = fileName.toLowerCase(Locale.ROOT);
        if (lower.endsWith(JAVA_EXTENSION)) {
            int nameEnd = lower.length() - JAVA_EXTENSION.length();
            String className = lower.substring(0, nameEnd);
            if (!names.contains(className)) {
                names.add(className);
            }
        }
    }

    // Says whether a class file is built again from source when graded,
    // so the student's copy is stale and must go: its outer class (Order
    // for Order$1.class) has a .java file or is a question's main class,
    // or it is named like a tester, which is always compiled fresh. A
    // precompiled tester or main class could otherwise replace the real
    // one.
    private boolean isRebuilt(String classFileName,
            List<String> sourceNames) {
        String lower = classFileName.toLowerCase(Locale.ROOT);
        int nameEnd = lower.length() - CLASS_EXTENSION.length();
        String className = lower.substring(0, nameEnd);
        int nestedStart = className.indexOf(NESTED_SEPARATOR);
        if (nestedStart >= 0) {
            className = className.substring(0, nestedStart);
        }
        String testerSuffix = config.getString(
                ConfigKeys.TESTER_CLASS_SUFFIX).toLowerCase(Locale.ROOT);
        boolean testerName = !testerSuffix.isEmpty()
                && className.endsWith(testerSuffix);
        return testerName || sourceNames.contains(className);
    }

    // Only ordinary files are deleted; links are rejected, not followed.
    // A class file that cannot be built again from source is kept.
    private void removeClassFiles(Path directory, List<String> provided,
            List<String> sourceNames) throws IOException {
        DirectoryStream<Path> children = null;
        try {
            children = Files.newDirectoryStream(directory);
            for (Path child : children) {
                if (Files.isSymbolicLink(child)) {
                    throw new IOException("Linked working file: " + child);
                }
                if (Files.isDirectory(child, LinkOption.NOFOLLOW_LINKS)) {
                    removeClassFiles(child, provided, sourceNames);
                } else {
                    String name = child.getFileName().toString();
                    boolean studentClass = name.endsWith(CLASS_EXTENSION)
                            && !provided.contains(name)
                            && isRebuilt(name, sourceNames);
                    if (studentClass) {
                        Files.delete(child);
                    }
                }
            }
        } finally {
            FileUtils.closeIfOpen(children);
        }
    }

    // Apply the same ignored-name setting to folder uploads as ZIPs, and
    // drop macOS ._ resource forks as ZipExtractor does
    private void removeIgnoredFiles(Path directory) throws IOException {
        List<String> ignored = config.getList(ConfigKeys.IGNORED_FILE_NAMES);
        DirectoryStream<Path> children = null;
        try {
            children = Files.newDirectoryStream(directory);
            for (Path child : children) {
                String childName = child.getFileName().toString();
                boolean ignoredChild = ignored.contains(childName)
                        || childName.startsWith(RESOURCE_FORK_PREFIX);
                if (ignoredChild) {
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
