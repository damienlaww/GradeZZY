/*
 * File: SubmissionLoader.java
 *
 * Purpose: Turns the files and folders the instructor uploaded into Submission
 * objects.
 *
 * Where it fits: The first step of batch upload: it accepts many zips, a folder
 * of zips or of submissions unzipped by hand, or a bulk zip that holds every
 * student's zip, for one section. The window previews a choice with the same
 * public methods, so the preview and grading cannot disagree.
 */
package gradezzy.ingest;

import java.io.IOException;
import java.nio.file.DirectoryStream;
import java.nio.file.Files;
import java.nio.file.LinkOption;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.Collections;
import java.util.HashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.UUID;

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

    // A browser's copy number at the end of a name, such as " (1)"
    private static final String COPY_NUMBER_PATTERN = "\\s*\\(\\d+\\)$";

    // Matches every character that is not a digit
    private static final String NON_DIGIT_PATTERN = "\\D";

    // The copy number of a name that has none: the first download, so
    // every copy ranks as newer
    private static final int NO_COPY_NUMBER = 0;

    // The copy number of a name whose number is too long to read, which
    // ranks as the newest copy
    private static final int UNREADABLE_COPY_NUMBER = Integer.MAX_VALUE;

    // How deep a question folder is looked for in a zip: at the top (0)
    // or inside the folder named after the student (1)
    private static final int TOP_LEVEL = 0;
    private static final int DEEPEST_QUESTION_FOLDER = 1;

    // Names that start with this are hidden files and folders
    private static final String HIDDEN_PREFIX = ".";

    // Start of the name of the folder a bulk zip is unpacked into
    private static final String BATCH_FOLDER_PREFIX = ".batch-";

    // The name of the current folder in a path, which is not hidden
    private static final String CURRENT_FOLDER = ".";

    // Separator between the folders of a zip entry name
    private static final String ENTRY_SEPARATOR = "/";

    // A wrapper folder holds exactly this many visible sub-folders
    private static final int SINGLE_SUBFOLDER = 1;

    // The text in front of the id of every anomaly raised while loading
    private static final String ANOMALY_ID_PREFIX = "INGEST-";

    // What the instructor can do about an upload that cannot be graded
    private static final String CORRECT_UPLOAD_ACTION =
            "Correct this upload and add it to a new batch.";

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
     * Creates one Submission per student. Each chosen path gives the
     * students listCandidates finds, and a bulk zip (see isBatchArchive),
     * chosen or found directly in a chosen folder, is unpacked into one
     * student per zip inside it. A chosen file that is not a zip is still
     * one student, listed as failed because it is not a ZIP file, without
     * discarding the other students. When several files give one
     * username, in any case, only one is graded: the newest download,
     * which is the highest copy
     * number such as " (2)", a file without one being the oldest; for two
     * files of the same name in two chosen folders, such as a late batch
     * chosen with the main one, the newer file, or the one chosen later;
     * else the one whose username is already in lower case, as on the
     * roster, else the first chosen. Each file left out is named in a
     * DUPLICATE_SUBMISSION warning on the graded one. Uploads without a
     * username cannot be graded: the first is kept as a failed submission
     * and each other one is named in a warning on it.
     *
     * @param inputPaths the input paths
     * @param section the section
     * @return the submissions found, in file name order
     * @throws IngestException if a path cannot be read, a chosen folder
     *         holds no student, or a bulk zip cannot be unpacked
     */
    public List<Submission> loadSubmissions(List<Path> inputPaths,
            Section section) throws IngestException {
        if (inputPaths == null || section == null) {
            throw new IngestException("Choose submissions and a section.");
        }
        List<Path> candidates = new ArrayList<>();
        try {
            for (Path input : inputPaths) {
                collectInput(input, candidates);
            }
        } catch (IOException exception) {
            throw new IngestException("Could not read submissions: "
                    + exception.getMessage(), exception);
        }
        return createSubmissions(candidates, section);
    }

    /**
     * Lists the paths a chosen file or folder would turn into students,
     * without unpacking anything. A folder that holds a question folder is
     * one student. Any other folder gives each visible zip directly inside
     * it and each sub-folder that looks unzipped by hand: one that holds a
     * question folder, directly or inside its only visible sub-folder.
     * A sub-folder with a starter folder name, such as
     * RenameToYourUsername, is skipped. A chosen file is one student; for
     * a bulk zip that is the zip itself, and listBatchEntries names the
     * students inside it. A bulk zip directly in a chosen folder is also
     * listed as itself, and loadSubmissions unpacks it.
     *
     * @param input the chosen file or folder
     * @return the students' zips and folders, sorted by name; empty for an
     *         ignored name such as __MACOSX
     * @throws IngestException if the path cannot be read, or a folder
     *         holds no student zip or submission folder
     */
    public List<Path> listCandidates(Path input) throws IngestException {
        if (input == null) {
            throw new IngestException("A submission path is missing.");
        }
        List<Path> candidates = new ArrayList<>();
        if (isIgnored(input)) {
            return candidates;
        }
        try {
            if (Files.isSymbolicLink(input) || !Files.isReadable(input)) {
                throw new IOException("Unreadable or linked path: " + input);
            }
            boolean folder = Files.isDirectory(input,
                    LinkOption.NOFOLLOW_LINKS);
            if (folder && hasQuestionFolder(input)) {
                candidates.add(input);
            } else if (folder) {
                collectFolderStudents(input, candidates);
            } else if (Files.isRegularFile(input, LinkOption.NOFOLLOW_LINKS)) {
                candidates.add(input);
            } else {
                throw new IOException("Not a file or folder: " + input);
            }
        } catch (IOException exception) {
            throw new IngestException("Could not read submissions: "
                    + exception.getMessage(), exception);
        }
        return candidates;
    }

    /**
     * Lists what a chosen folder would skip: its visible files that are
     * not zips, its sub-folders that do not look unzipped by hand and
     * its sub-folders with a starter folder name (the setting
     * assessment.placeholder.folder.names). Hidden and ignored names are
     * always skipped and are not listed.
     *
     * @param folder the chosen folder
     * @return the skipped files and folders, sorted by name; empty when
     *         the path is not a folder or is itself one student
     * @throws IngestException if the folder cannot be read
     */
    public List<Path> listSkipped(Path folder) throws IngestException {
        List<Path> skipped = new ArrayList<>();
        if (folder == null || isIgnored(folder)
                || !Files.isDirectory(folder, LinkOption.NOFOLLOW_LINKS)
                || hasQuestionFolder(folder)) {
            return skipped;
        }
        try {
            splitChildren(folder, new ArrayList<>(), skipped);
        } catch (IOException exception) {
            throw new IngestException("Could not read " + folder + ": "
                    + exception.getMessage(), exception);
        }
        return skipped;
    }

    /**
     * Says whether a zip is a bulk zip of students' zips. It is when its
     * own name is not a student's zip name (the configured prefix, as in
     * 2023-2024-ping.lee.2023.zip), it holds at least one zip that is not
     * named like a question folder (Q1.zip), and it holds no question
     * folder at its top or up to two folders down, as in
     * ping.lee.2023/ping.lee.2023/Q1. A damaged zip is not a bulk zip, so
     * it is graded as one student and extraction reports the problem.
     *
     * @param zipFile the chosen zip file
     * @return true if each zip inside is graded as one student
     */
    public boolean isBatchArchive(Path zipFile) {
        if (zipFile == null || !isZipFile(zipFile)
                || hasStudentPrefix(fileNameOf(zipFile))) {
            return false;
        }
        try {
            return isBatchContent(listVisibleEntries(zipFile));
        } catch (IngestException exception) {
            return false;
        }
    }

    /**
     * Lists the names of the files inside a zip without unzipping it.
     * For a bulk zip (see isBatchArchive) these are the zips graded as
     * students, such as 2023-2024-ping.lee.2023.zip; its other files are
     * skipped. For any other zip these are all its visible files. Hidden
     * and ignored entries, such as __MACOSX, are always left out.
     *
     * @param archive the zip file
     * @return the file names without their folders, sorted
     * @throws IngestException if the file is not a readable zip
     */
    public List<String> listBatchEntries(Path archive)
            throws IngestException {
        if (archive == null) {
            throw new IngestException("Choose a zip file.");
        }
        boolean batch = isBatchArchive(archive);
        List<String> names = new ArrayList<>();
        for (String entryName : listVisibleEntries(archive)) {
            int separator = entryName.lastIndexOf(ENTRY_SEPARATOR);
            String fileName = entryName.substring(separator + 1);
            if (!batch || isStudentZipEntry(entryName)) {
                names.add(fileName);
            }
        }
        Collections.sort(names);
        return names;
    }

    /**
     * Lists the visible files a bulk zip (see isBatchArchive) skips,
     * because they are not students' zips, such as index.html.
     *
     * @param archive the zip file
     * @return the skipped file names without their folders, sorted; empty
     *         when the zip is not a bulk zip
     * @throws IngestException if the file is not a readable zip
     */
    public List<String> listBatchSkipped(Path archive)
            throws IngestException {
        List<String> names = new ArrayList<>();
        if (!isBatchArchive(archive)) {
            return names;
        }
        for (String entryName : listVisibleEntries(archive)) {
            int separator = entryName.lastIndexOf(ENTRY_SEPARATOR);
            String fileName = entryName.substring(separator + 1);
            if (!isStudentZipEntry(entryName)) {
                names.add(fileName);
            }
        }
        Collections.sort(names);
        return names;
    }

    /**
     * Works out the username from a zip file or folder name by removing the
     * configured prefix, the .zip ending and a browser's copy number, so
     * "2023-2024-ping.lee.2023 (1).zip" gives "ping.lee.2023". The
     * username is in lower case, as on the roster, so "PING.LEE.2023.zip"
     * gives "ping.lee.2023" too. The window calls this too, so both show
     * the same username.
     *
     * @param zipFileName the zip file or folder name
     * @return the username, or an empty string if none is found
     */
    public String extractUsername(String zipFileName) {
        if (zipFileName == null) {
            return "";
        }
        String username = removeZipExtension(zipFileName.replaceFirst(
                config.getString(ConfigKeys.ZIP_NAME_PREFIX_PATTERN), ""));
        // A second download of the same file is saved as "name (1).zip"
        username = username.trim().replaceFirst(COPY_NUMBER_PATTERN, "");
        return username.trim().toLowerCase(Locale.ROOT);
    }

    /**
     * Reads the copy number a browser adds to a second download, such as
     * 1 for "2023-2024-ping.lee.2023 (1).zip", by the same rule grading
     * uses to choose one file per student: the highest number is the
     * newest download and is graded. The window calls this too.
     *
     * @param fileName the zip file or folder name
     * @return the copy number, or 0 when the name has none
     */
    public static int readCopyNumber(String fileName) {
        if (fileName == null) {
            return NO_COPY_NUMBER;
        }
        String name = fileName;
        if (name.toLowerCase(Locale.ROOT).endsWith(ZIP_EXTENSION)) {
            name = name.substring(0, name.length() - ZIP_EXTENSION.length());
        }
        name = name.trim();
        String original = name.replaceFirst(COPY_NUMBER_PATTERN, "");
        if (original.equals(name)) {
            return NO_COPY_NUMBER;
        }
        String digits = name.substring(original.length())
                .replaceAll(NON_DIGIT_PATTERN, "");
        try {
            return Integer.parseInt(digits);
        } catch (NumberFormatException exception) {
            return UNREADABLE_COPY_NUMBER;
        }
    }

    /**
     * Loads a ZIP explicitly identified by the caller as a batch container:
     * each zip inside it is one student, except one named like a question
     * folder, such as Q1.zip.
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
        return createSubmissions(candidates, section);
    }

    // Adds the students of one chosen path. A bulk zip, chosen or found
    // in a chosen folder, is replaced by the student zips inside it, as
    // when the instructor puts the section's bulk download in a folder.
    private void collectInput(Path input, List<Path> candidates)
            throws IOException, IngestException {
        for (Path found : listCandidates(input)) {
            if (isBatchArchive(found)) {
                unpackBatch(found, candidates);
            } else {
                candidates.add(found);
            }
        }
    }

    // Adds the students inside a folder; a folder with none is an error.
    private void collectFolderStudents(Path folder, List<Path> candidates)
            throws IOException, IngestException {
        splitChildren(folder, candidates, new ArrayList<>());
        if (candidates.isEmpty()) {
            throw new IngestException(describeFolderWithoutStudents(folder));
        }
    }

    // Splits a folder's visible children into students (zips and folders
    // unzipped by hand) and skipped items, each in name order. A starter
    // folder, such as RenameToYourUsername, is skipped.
    private void splitChildren(Path folder, List<Path> students,
            List<Path> skipped) throws IOException {
        for (Path child : listVisibleChildren(folder)) {
            if (isPlaceholderFolder(child)) {
                skipped.add(child);
            } else if (isZipFile(child) || looksLikeSubmission(child)) {
                students.add(child);
            } else {
                skipped.add(child);
            }
        }
    }

    // Says whether a path is a folder with a starter folder name.
    private boolean isPlaceholderFolder(Path path) {
        if (!Files.isDirectory(path, LinkOption.NOFOLLOW_LINKS)) {
            return false;
        }
        String name = fileNameOf(path);
        for (String placeholder : config.getList(
                ConfigKeys.PLACEHOLDER_FOLDER_NAMES)) {
            if (placeholder.equalsIgnoreCase(name)) {
                return true;
            }
        }
        return false;
    }

    // Lists a folder's children without hidden and ignored names, sorted
    // by name, because directory order differs between operating systems.
    private List<Path> listVisibleChildren(Path folder) throws IOException {
        List<Path> children = new ArrayList<>();
        DirectoryStream<Path> entries = null;
        try {
            entries = Files.newDirectoryStream(folder);
            for (Path child : entries) {
                if (!isHiddenOrIgnored(child.getFileName().toString())) {
                    children.add(child);
                }
            }
        } finally {
            FileUtils.closeIfOpen(entries);
        }
        Collections.sort(children);
        return children;
    }

    // Says whether a folder looks unzipped by hand: it holds a question
    // folder, directly or inside its only visible sub-folder. A folder
    // that cannot be read does not.
    private boolean looksLikeSubmission(Path path) {
        if (!Files.isDirectory(path, LinkOption.NOFOLLOW_LINKS)) {
            return false;
        }
        if (hasQuestionFolder(path)) {
            return true;
        }
        try {
            Path wrapper = findOnlySubfolder(path);
            return wrapper != null && hasQuestionFolder(wrapper);
        } catch (IOException exception) {
            return false;
        }
    }

    // Returns a folder's only visible sub-folder, or null if it has none
    // or several.
    private Path findOnlySubfolder(Path folder) throws IOException {
        List<Path> subfolders = new ArrayList<>();
        for (Path child : listVisibleChildren(folder)) {
            if (Files.isDirectory(child, LinkOption.NOFOLLOW_LINKS)) {
                subfolders.add(child);
            }
        }
        if (subfolders.size() != SINGLE_SUBFOLDER) {
            return null;
        }
        return subfolders.get(0);
    }

    // Says whether a folder holds one of the configured question folders.
    private boolean hasQuestionFolder(Path directory) {
        for (String folder : listQuestionFolders()) {
            if (Files.isDirectory(directory.resolve(folder),
                    LinkOption.NOFOLLOW_LINKS)) {
                return true;
            }
        }
        return false;
    }

    // Lists the configured question folder names, such as Q1.
    private List<String> listQuestionFolders() {
        List<String> folders = new ArrayList<>();
        for (String id : config.getList(ConfigKeys.QUESTION_IDS)) {
            folders.add(config.getString(ConfigKeys.QUESTION_PREFIX
                    + id + ConfigKeys.QUESTION_FOLDER_SUFFIX));
        }
        return folders;
    }

    // Says whether a name is a question folder name, in any case.
    private boolean isQuestionFolderName(String name) {
        for (String folder : listQuestionFolders()) {
            if (folder.equalsIgnoreCase(name)) {
                return true;
            }
        }
        return false;
    }

    // Says a folder holds no student, and names a sub-folder of zips.
    private String describeFolderWithoutStudents(Path folder)
            throws IOException {
        String message = "No student ZIP files or submission folders were "
                + "found in the folder " + folder + ".";
        Path subfolder = findSubfolderWithZips(folder);
        if (subfolder != null) {
            message += " The folder \"" + subfolder.getFileName()
                    + "\" inside it holds ZIP files: choose that folder.";
        }
        return message;
    }

    // Returns the first visible sub-folder, by name, that holds a zip.
    private Path findSubfolderWithZips(Path folder) throws IOException {
        for (Path child : listVisibleChildren(folder)) {
            if (Files.isDirectory(child, LinkOption.NOFOLLOW_LINKS)
                    && holdsZips(child)) {
                return child;
            }
        }
        return null;
    }

    // Says whether a folder holds a zip directly. A folder that cannot be
    // read holds none.
    private boolean holdsZips(Path folder) {
        try {
            for (Path child : listVisibleChildren(folder)) {
                if (isZipFile(child)) {
                    return true;
                }
            }
        } catch (IOException exception) {
            return false;
        }
        return false;
    }

    // Names each candidate, keeps one submission per username and sorts
    // the submissions by file name. The first upload without a username
    // is kept as a failed submission and the others are named on it, so
    // no two submissions share the empty username.
    private List<Submission> createSubmissions(List<Path> candidates,
            Section section) {
        // All candidates share the same comparison, even across uploads.
        List<Submission> submissions = new ArrayList<>();
        List<Submission> named = new ArrayList<>();
        Submission nameless = null;
        for (Path candidate : candidates) {
            String username = extractUsername(fileNameOf(candidate));
            if (!TextUtils.isBlank(username)) {
                Submission submission = new Submission(username, section,
                        candidate);
                // A chosen file that is not a zip, such as a marking
                // note, is listed so the instructor sees why it was not
                // graded, but it is not read as a damaged zip
                if (Files.isRegularFile(candidate, LinkOption.NOFOLLOW_LINKS)
                        && !isZipFile(candidate)) {
                    reject(submission, fileNameOf(candidate) + " is not a "
                            + "ZIP file or a folder, so it cannot be graded. "
                            + "Choose the student's ZIP file or folder.");
                }
                named.add(submission);
            } else if (nameless == null) {
                nameless = new Submission(username, section, candidate);
                reject(nameless, "No username in " + candidate);
                submissions.add(nameless);
            } else {
                warnNameless(nameless, candidate);
            }
        }

        // The submission graded for each username, in lower case. The
        // candidates are in the order chosen, so a tie keeps the first.
        Map<String, Submission> kept = new HashMap<>();
        for (Submission submission : named) {
            String key = submission.getUsername().toLowerCase(Locale.ROOT);
            Submission current = kept.get(key);
            if (current == null || isPreferred(submission, current)) {
                kept.put(key, submission);
            }
        }

        // The others are named on the kept one, so the run never holds
        // two submissions with one username
        for (Submission submission : named) {
            Submission keeper = kept.get(
                    submission.getUsername().toLowerCase(Locale.ROOT));
            if (keeper == submission) {
                submissions.add(submission);
            } else {
                warnDuplicate(keeper, submission.getOriginalPath());
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

    // Marks a submission as failed and records why as an anomaly.
    private void reject(Submission submission, String message) {
        submission.setIngestFailure(message);
        submission.setStatus(SubmissionStatus.FAILED);
        submission.addAnomaly(new Anomaly(ANOMALY_ID_PREFIX
                + UUID.randomUUID(),
                AnomalyType.INVALID_ARCHIVE, Severity.ERROR,
                submission.getUsername(), message, CORRECT_UPLOAD_ACTION));
    }

    // Names, on the kept upload without a username, another upload
    // without one, which is not added to the run.
    private void warnNameless(Submission kept, Path dropped) {
        kept.addAnomaly(new Anomaly(ANOMALY_ID_PREFIX + UUID.randomUUID(),
                AnomalyType.INVALID_ARCHIVE, Severity.WARNING,
                kept.getUsername(), "No username in " + dropped
                + " either. It cannot be graded, so it is not listed on "
                + "its own.", CORRECT_UPLOAD_ACTION));
    }

    // Says whether a file is graded in place of an earlier chosen file
    // with the same username: a higher copy number, because a student who
    // uploads a fixed version gets it saved as "name (1).zip"; for two
    // files of the same name in two chosen folders, the newer one, or on
    // a tie the one chosen later, as a late batch replaces an earlier
    // result; else a file name in lower case, as usernames are.
    private boolean isPreferred(Submission candidate, Submission current) {
        Path candidatePath = candidate.getOriginalPath();
        Path currentPath = current.getOriginalPath();
        int candidateCopy = readCopyNumber(fileNameOf(candidatePath));
        int currentCopy = readCopyNumber(fileNameOf(currentPath));
        if (candidateCopy != currentCopy) {
            return candidateCopy > currentCopy;
        }
        if (isSameNameInTwoFolders(candidatePath, currentPath)) {
            return readModifiedMillis(candidatePath)
                    >= readModifiedMillis(currentPath);
        }
        return isLowerCase(fileNameOf(candidatePath))
                && !isLowerCase(fileNameOf(currentPath));
    }

    // Says whether two different files share a name and were found in
    // chosen folders, not inside a bulk zip.
    private boolean isSameNameInTwoFolders(Path first, Path second) {
        return fileNameOf(first).equals(fileNameOf(second))
                && !isSameFile(first, second)
                && !isInsideBatch(first) && !isInsideBatch(second);
    }

    // Says whether a file was unpacked from a bulk zip.
    private boolean isInsideBatch(Path file) {
        Path folder = file.getParent();
        while (folder != null) {
            if (fileNameOf(folder).startsWith(BATCH_FOLDER_PREFIX)) {
                return true;
            }
            folder = folder.getParent();
        }
        return false;
    }

    // Reads when a file was last changed, or 0 when it cannot be read.
    private long readModifiedMillis(Path file) {
        try {
            return Files.getLastModifiedTime(file,
                    LinkOption.NOFOLLOW_LINKS).toMillis();
        } catch (IOException exception) {
            return 0;
        }
    }

    // Says whether a name has no capital letter.
    private boolean isLowerCase(String name) {
        return name.equals(name.toLowerCase(Locale.ROOT));
    }

    // Tells the instructor, on the graded submission, which file of the
    // same student was left out.
    private void warnDuplicate(Submission kept, Path dropped) {
        Path keptPath = kept.getOriginalPath();
        String keptName = fileNameOf(keptPath);
        String droppedName = fileNameOf(dropped);
        String message = "Another file for this student was not graded: "
                + droppedName + ". Only " + keptName + " was graded.";
        if (isSameFile(keptPath, dropped)) {
            message = keptName + " was chosen more than once. It was graded "
                    + "once.";
        } else if (droppedName.equals(keptName)) {
            message = "Two files are named " + keptName + ", one "
                    + describeFolder(keptPath) + " and one "
                    + describeFolder(dropped) + ". Only the one "
                    + describeFolder(keptPath) + " was graded.";
        }
        kept.addAnomaly(new Anomaly(ANOMALY_ID_PREFIX + UUID.randomUUID(),
                AnomalyType.DUPLICATE_SUBMISSION, Severity.WARNING,
                kept.getUsername(), message, "Check that the graded file "
                + "is the one to mark. Next time, choose one file per "
                + "student."));
    }

    // Says whether two paths are the same file, even if one is relative or
    // a short Windows name.
    private boolean isSameFile(Path first, Path second) {
        try {
            return Files.isSameFile(first, second);
        } catch (IOException exception) {
            return first.toAbsolutePath().normalize().equals(
                    second.toAbsolutePath().normalize());
        }
    }

    // Names where a file was found: in its folder, or inside a bulk zip.
    private String describeFolder(Path file) {
        if (isInsideBatch(file)) {
            return "inside the bulk zip";
        }
        return "in " + file.getParent();
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
        Path batch = Files.createTempDirectory(root, BATCH_FOLDER_PREFIX);
        try {
            extractor.extract(input, batch);
            List<Path> innerZips = new ArrayList<>();
            collectBatchZips(batch, innerZips);
            Collections.sort(innerZips);
            candidates.addAll(innerZips);
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

    // Collects the students' zips in an unpacked bulk zip, in any folder
    // except a question folder.
    private void collectBatchZips(Path directory, List<Path> candidates)
            throws IOException {
        for (Path child : listVisibleChildren(directory)) {
            if (Files.isDirectory(child, LinkOption.NOFOLLOW_LINKS)
                    && !isQuestionFolderName(fileNameOf(child))) {
                collectBatchZips(child, candidates);
            } else if (isZipFile(child)
                    && isStudentZipName(fileNameOf(child))) {
                candidates.add(child);
            }
        }
    }

    // Lists the visible entries of a zip, with their folders.
    private List<String> listVisibleEntries(Path archive)
            throws IngestException {
        List<String> entries = new ArrayList<>();
        ZipExtractor extractor = new ZipExtractor(config);
        for (String entryName : extractor.listFileNames(archive)) {
            if (!isSkippedEntry(entryName)) {
                entries.add(entryName);
            }
        }
        return entries;
    }

    // Says whether a zip's visible entries are a bulk zip's: at least one
    // student's zip and no question folder.
    private boolean isBatchContent(List<String> entryNames) {
        boolean holdsStudentZip = false;
        for (String entryName : entryNames) {
            if (isStudentZipEntry(entryName)) {
                holdsStudentZip = true;
            }
        }
        return holdsStudentZip && !holdsQuestionFolder(entryNames);
    }

    // Says whether a zip entry is a student's zip: a student zip name that
    // is not inside a question folder, where it is part of an answer.
    private boolean isStudentZipEntry(String entryName) {
        int separator = entryName.lastIndexOf(ENTRY_SEPARATOR);
        if (!isStudentZipName(entryName.substring(separator + 1))) {
            return false;
        }
        for (String folder : listEntryFolders(entryName)) {
            if (isQuestionFolderName(folder)) {
                return false;
            }
        }
        return true;
    }

    // Says whether a zip holds a question folder at its top or one folder
    // down, as a student's own zip does.
    private boolean holdsQuestionFolder(List<String> entryNames) {
        for (String entryName : entryNames) {
            List<String> folders = listEntryFolders(entryName);
            for (int depth = TOP_LEVEL; depth <= DEEPEST_QUESTION_FOLDER;
                    depth++) {
                if (isQuestionFolderAt(folders, depth)) {
                    return true;
                }
            }
        }
        return false;
    }

    // Lists the folders of a zip entry, top first, without "." parts,
    // so "./ping/Q1/Q1a.java" gives ping and Q1.
    private List<String> listEntryFolders(String entryName) {
        List<String> folders = new ArrayList<>();
        String[] parts = entryName.split(ENTRY_SEPARATOR);
        // The last part is the file name
        for (int i = 0; i < parts.length - 1; i++) {
            if (!parts[i].isEmpty() && !parts[i].equals(CURRENT_FOLDER)) {
                folders.add(parts[i]);
            }
        }
        return folders;
    }

    // Says whether the folder at a depth of an entry is a question folder.
    private boolean isQuestionFolderAt(List<String> folders, int depth) {
        return folders.size() > depth
                && isQuestionFolderName(folders.get(depth));
    }

    // Says whether a file inside a bulk zip is a student's zip: a .zip
    // that is not named like a question folder, such as Q1.zip.
    private boolean isStudentZipName(String fileName) {
        return hasZipExtension(fileName)
                && !isQuestionFolderName(removeZipExtension(fileName));
    }

    // Says whether a name starts with the configured student prefix.
    private boolean hasStudentPrefix(String fileName) {
        String prefix = config.getString(
                ConfigKeys.ZIP_NAME_PREFIX_PATTERN);
        return !fileName.replaceFirst(prefix, "").equals(fileName);
    }

    // Removes a .zip ending, in any case, from a name.
    private String removeZipExtension(String name) {
        if (!hasZipExtension(name)) {
            return name;
        }
        return name.substring(0, name.length() - ZIP_EXTENSION.length());
    }

    // Returns the last part of a path as text.
    private String fileNameOf(Path path) {
        return String.valueOf(path.getFileName());
    }

    // Says whether a zip entry is, or is inside, a hidden or ignored one.
    private boolean isSkippedEntry(String entryName) {
        for (String part : entryName.split(ENTRY_SEPARATOR)) {
            if (isHiddenOrIgnored(part)) {
                return true;
            }
        }
        return false;
    }

    // Says whether a name starts with a dot or is in the ignored list.
    private boolean isHiddenOrIgnored(String name) {
        List<String> ignored = config.getList(ConfigKeys.IGNORED_FILE_NAMES);
        boolean hidden = name.startsWith(HIDDEN_PREFIX)
                && !name.equals(CURRENT_FOLDER);
        return hidden || ignored.contains(name);
    }

    // Says whether a path is an ordinary file whose name ends with .zip.
    private boolean isZipFile(Path path) {
        return Files.isRegularFile(path, LinkOption.NOFOLLOW_LINKS)
                && hasZipExtension(String.valueOf(path.getFileName()));
    }

    // Says whether a file name ends with .zip, in any case.
    private boolean hasZipExtension(String fileName) {
        return fileName.toLowerCase(Locale.ROOT).endsWith(ZIP_EXTENSION);
    }

    // Says whether any part of a chosen path is an ignored name.
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
