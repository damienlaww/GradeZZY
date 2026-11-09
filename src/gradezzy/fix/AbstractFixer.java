/*
 * File: AbstractFixer.java
 *
 * Purpose: Shared code for all fixers.
 *
 * Where it fits: Holds the name and the backup helper every fixer needs before
 * it changes a file, and the helpers that find question folders and files in
 * a working copy.
 */
package gradezzy.fix;

import java.io.IOException;
import java.nio.file.DirectoryStream;
import java.nio.file.Files;
import java.nio.file.LinkOption;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.Collections;
import java.util.List;

import gradezzy.config.AppConfig;
import gradezzy.config.ConfigKeys;
import gradezzy.model.Anomaly;
import gradezzy.model.AnomalyType;
import gradezzy.model.FixStatus;
import gradezzy.model.Submission;
import gradezzy.util.FileUtils;
import gradezzy.util.TextUtils;

/**
 * Shared code for all fixers.
 *
 * <p>Tier: MUST. Owner: Jun Sheng.
 */
public abstract class AbstractFixer implements Fixer {

    /**
     * The hidden folder in each working copy that holds backups.
     */
    public static final String BACKUP_FOLDER_NAME = ".gradezzy-backup";

    /**
     * Separates the question folder and the file in a path such as
     * Q1/Q1a.java.
     */
    protected static final String FOLDER_SEPARATOR = "/";

    // The start of the name of each backup's own folder
    private static final String BACKUP_SLOT_PREFIX = "fix-";

    // The start of a hidden name
    private static final String HIDDEN_PREFIX = ".";

    // A temporary name ending used when only the letter case changes
    private static final String CASE_RENAME_SUFFIX = ".renaming";

    // Follows the file path at the start of a file anomaly's message
    private static final String PATH_END = " ";

    // Separates folders in a path on Windows
    private static final String WINDOWS_SEPARATOR = "\\";

    // Goes between two names of a list, but the last two
    private static final String LIST_SEPARATOR = ", ";

    // Goes between the last two names of a list
    private static final String LAST_SEPARATOR = " and ";

    // The name shown in the anomaly log
    private final String name;

    /**
     * Creates a new abstract fixer.
     *
     * @param name the name shown in the anomaly log
     */
    protected AbstractFixer(String name) {
        this.name = name;
    }

    /**
     * Returns the name of this fixer.
     *
     * @return the name of this fixer
     */
    @Override
    public String getName() {
        return name;
    }

    /**
     * Returns null, so FixManager always applies this fix. A fixer that
     * can tell what it would change overrides this.
     *
     * @param submission the submission
     * @param anomaly the anomaly
     * @return null
     * @throws FixException never here
     */
    @Override
    public Path findPathToChange(Submission submission, Anomaly anomaly)
            throws FixException {
        return null;
    }

    /**
     * Copies a file or folder into the backup folder before it is changed.
     *
     * @param submission the submission whose working copy holds the file
     * @param fileOrFolder the file or folder
     * @return the path of the backup
     * @throws FixException if the operation fails
     */
    protected Path backup(Submission submission,
            Path fileOrFolder) throws FixException {
        Path workingCopy = submission.getWorkingCopyPath();
        if (workingCopy == null) {
            throw new FixException("There is no working copy to back up.");
        }

        try {
            Path backupFolder = workingCopy.resolve(BACKUP_FOLDER_NAME);
            Files.createDirectories(backupFolder);
            Path slot = Files.createTempDirectory(backupFolder,
                    BACKUP_SLOT_PREFIX);
            Path copy = slot.resolve(fileOrFolder.getFileName());

            if (Files.isDirectory(fileOrFolder, LinkOption.NOFOLLOW_LINKS)) {
                FileUtils.copyDirectory(fileOrFolder, copy);
            } else {
                Files.copy(fileOrFolder, copy);
            }
            return copy;
        } catch (IOException exception) {
            throw new FixException("Could not back up "
                    + fileOrFolder.getFileName() + ": "
                    + exception.getMessage(), exception);
        }
    }

    /**
     * Deletes a backup, and the backup folder once it is empty.
     *
     * @param backupPath the path returned by backup
     * @throws FixException if the operation fails
     */
    protected void discardBackup(Path backupPath) throws FixException {
        Path slot = backupPath.getParent();
        Path backupFolder = slot.getParent();

        try {
            FileUtils.deleteDirectory(slot);
            if (isEmptyFolder(backupFolder)) {
                Files.delete(backupFolder);
            }
        } catch (IOException exception) {
            throw new FixException("Could not remove the backup "
                    + slot.getFileName() + ": " + exception.getMessage(),
                    exception);
        }
    }

    /**
     * Says whether a file or folder is hidden.
     *
     * @param path the file or folder
     * @return true if its name starts with a dot
     */
    protected boolean isHidden(Path path) {
        return path.getFileName().toString().startsWith(HIDDEN_PREFIX);
    }

    /**
     * Says whether a folder exists and holds nothing.
     *
     * @param folder the folder
     * @return true if it exists and is empty
     * @throws IOException if the folder cannot be listed
     */
    protected boolean isEmptyFolder(Path folder) throws IOException {
        if (!Files.isDirectory(folder, LinkOption.NOFOLLOW_LINKS)) {
            return false;
        }
        DirectoryStream<Path> children = null;
        try {
            children = Files.newDirectoryStream(folder);
            return !children.iterator().hasNext();
        } finally {
            FileUtils.closeIfOpen(children);
        }
    }

    /**
     * Reads the folder of one question from the settings.
     *
     * @param config the application settings
     * @param questionId the question id, such as Q1a
     * @return the folder name, such as Q1
     */
    protected String readFolderName(AppConfig config, String questionId) {
        String key = ConfigKeys.QUESTION_PREFIX + questionId
                + ConfigKeys.QUESTION_FOLDER_SUFFIX;
        return config.getString(key);
    }

    /**
     * Reads the source file of one question from the settings.
     *
     * @param config the application settings
     * @param questionId the question id, such as Q1a
     * @return the file name, such as Q1a.java
     */
    protected String readFileName(AppConfig config, String questionId) {
        String key = ConfigKeys.QUESTION_PREFIX + questionId
                + ConfigKeys.QUESTION_FILE_SUFFIX;
        return config.getString(key);
    }

    /**
     * Reads the question folder names, each once, in question order. Q1a
     * and Q1b share Q1, so Q1 appears once.
     *
     * @param config the application settings
     * @return the question folder names
     */
    protected List<String> readFolderNames(AppConfig config) {
        List<String> folderNames = new ArrayList<>();
        for (String questionId : config.getList(ConfigKeys.QUESTION_IDS)) {
            String folderName = readFolderName(config, questionId);
            if (!folderNames.contains(folderName)) {
                folderNames.add(folderName);
            }
        }
        return folderNames;
    }

    /**
     * Finds the folder that holds the question folders, by the rule the
     * validators use: the top of the working copy when a question folder
     * is there, else the folder named after the username, else the only
     * folder there is, else the only top folder that holds a question
     * folder, so an extra folder such as bin is ignored.
     *
     * @param workingCopy the working copy
     * @param username the student's username
     * @param folderNames the question folder names
     * @return the folder, or null if there is no single candidate
     * @throws FixException if the working copy cannot be listed
     */
    protected Path findContentRoot(Path workingCopy, String username,
            List<String> folderNames) throws FixException {
        List<Path> topFolders = listFolders(workingCopy);
        Path usernameFolder = findExact(topFolders, username);

        if (holdsQuestionFolder(topFolders, folderNames)) {
            return workingCopy;
        } else if (usernameFolder != null) {
            return usernameFolder;
        } else if (topFolders.size() == 1) {
            return topFolders.get(0);
        }
        return findOnlyHolder(topFolders, folderNames);
    }

    /**
     * Writes a folder as shown in Before and After: its path relative to
     * the working copy with forward slashes and a slash at the end, such
     * as ping.lee.2023/inner/, or empty text for the working copy itself.
     *
     * @param workingCopy the working copy
     * @param folder the folder inside it
     * @return the folder as shown
     */
    protected String showFolder(Path workingCopy, Path folder) {
        if (folder.equals(workingCopy)) {
            return "";
        }

        String relativePath = workingCopy.relativize(folder).toString();
        return relativePath.replace(WINDOWS_SEPARATOR, FOLDER_SEPARATOR)
                + FOLDER_SEPARATOR;
    }

    /**
     * Joins names as words: Q1, Q2 and Q3.
     *
     * @param names the names, at least one
     * @return the names joined
     */
    protected String joinNames(List<String> names) {
        int last = names.size() - 1;
        String lastName = names.get(last);
        if (last == 0) {
            return lastName;
        }

        List<String> allButLast = names.subList(0, last);
        return TextUtils.join(LIST_SEPARATOR, allButLast) + LAST_SEPARATOR
                + lastName;
    }

    /**
     * Finds a question folder as the working copy is now, so an undo works
     * whichever fixes were undone before it: in the content root, else one
     * level deeper, where undoing a move puts it back.
     *
     * @param workingCopy the working copy
     * @param username the student's username
     * @param folderNames the question folder names
     * @param folderName the folder to find, such as Q1, in any case
     * @return the folder, or null if it is not there
     * @throws FixException if a folder cannot be listed
     */
    protected Path findQuestionFolderNow(Path workingCopy, String username,
            List<String> folderNames, String folderName)
            throws FixException {
        Path contentRoot = findContentRoot(workingCopy, username,
                folderNames);
        if (contentRoot == null) {
            return null;
        }

        List<Path> folders = listFolders(contentRoot);
        Path questionFolder = findByName(folders, folderName);
        if (questionFolder != null) {
            return questionFolder;
        }

        for (Path folder : folders) {
            Path nested = findByName(listFolders(folder), folderName);
            if (nested != null) {
                return nested;
            }
        }
        return null;
    }

    /**
     * Finds the question folder a MISSING_QUESTION_FOLDER anomaly is about.
     * The validator reports the absent folders in question order, and a
     * fixed one is back in place, so the anomaly's place among the unfixed
     * ones is its place among the folders still absent.
     *
     * @param submission the submission
     * @param anomaly the anomaly
     * @param contentRoot the folder that holds the question folders
     * @param folderNames the question folder names
     * @return the folder name, such as Q2, or null if it cannot be told
     * @throws FixException if the content root cannot be listed
     */
    protected String findMissingFolderName(Submission submission,
            Anomaly anomaly, Path contentRoot, List<String> folderNames)
            throws FixException {
        List<Path> folders = listFolders(contentRoot);
        List<String> stillAbsent = new ArrayList<>();
        for (String folderName : folderNames) {
            if (findExact(folders, folderName) == null) {
                stillAbsent.add(folderName);
            }
        }

        int place = 0;
        for (Anomaly other : submission.getAnomalies()) {
            if (other.getId().equals(anomaly.getId())) {
                return pickAt(stillAbsent, place);
            }

            boolean unfixedMissingFolder = other.getType()
                    == AnomalyType.MISSING_QUESTION_FOLDER
                    && other.getFixStatus() != FixStatus.APPLIED;
            if (unfixedMissingFolder) {
                place++;
            }
        }
        return null;
    }

    /**
     * Finds the question a file anomaly is about. Its message starts with
     * the file's path, such as Q1/Q1a.java, in any letter case.
     *
     * @param config the application settings
     * @param anomaly the anomaly
     * @return the question id, such as Q1a, or null if none matches
     */
    protected String findQuestionId(AppConfig config, Anomaly anomaly) {
        String message = anomaly.getMessage();
        for (String questionId : config.getList(ConfigKeys.QUESTION_IDS)) {
            String start = readFolderName(config, questionId)
                    + FOLDER_SEPARATOR + readFileName(config, questionId)
                    + PATH_END;
            boolean startsWithPath = message.length() >= start.length()
                    && message.substring(0, start.length())
                            .equalsIgnoreCase(start);
            if (startsWithPath) {
                return questionId;
            }
        }
        return null;
    }

    /**
     * Lists the visible folders directly inside a folder, in name order.
     *
     * @param folder the folder to list
     * @return the folders found
     * @throws FixException if the folder cannot be listed
     */
    protected List<Path> listFolders(Path folder) throws FixException {
        List<Path> folders = new ArrayList<>();

        try {
            for (Path child : readFolder(folder)) {
                boolean visibleFolder = Files.isDirectory(child,
                        LinkOption.NOFOLLOW_LINKS) && !isHidden(child);
                if (visibleFolder) {
                    folders.add(child);
                }
            }
        } catch (IOException exception) {
            throw new FixException("Could not list " + folder.getFileName()
                    + ": " + exception.getMessage(), exception);
        }

        Collections.sort(folders);
        return folders;
    }

    /**
     * Lists the ordinary files directly inside a folder, in name order.
     *
     * @param folder the folder to list
     * @return the files found
     * @throws FixException if the folder cannot be listed
     */
    protected List<Path> listFiles(Path folder) throws FixException {
        List<Path> files = new ArrayList<>();

        try {
            for (Path child : readFolder(folder)) {
                if (Files.isRegularFile(child, LinkOption.NOFOLLOW_LINKS)) {
                    files.add(child);
                }
            }
        } catch (IOException exception) {
            throw new FixException("Could not list " + folder.getFileName()
                    + ": " + exception.getMessage(), exception);
        }

        Collections.sort(files);
        return files;
    }

    /**
     * Lists everything directly inside a folder, in the order the system
     * gives. The listing is closed even if reading it fails.
     *
     * @param folder the folder to list
     * @return the files and folders found
     * @throws IOException if the folder cannot be listed or closed
     */
    private List<Path> readFolder(Path folder) throws IOException {
        List<Path> children = new ArrayList<>();
        DirectoryStream<Path> listing = null;

        try {
            listing = Files.newDirectoryStream(folder);
            for (Path child : listing) {
                children.add(child);
            }
        } finally {
            FileUtils.closeIfOpen(listing);
        }
        return children;
    }

    /**
     * Finds the path whose name matches exactly, including case. Names are
     * compared as text because Windows and macOS would also find q3 when
     * asked for Q3.
     *
     * @param paths the paths to search
     * @param wantedName the name to find
     * @return the matching path, or null if there is none
     */
    protected Path findExact(List<Path> paths, String wantedName) {
        for (Path path : paths) {
            if (path.getFileName().toString().equals(wantedName)) {
                return path;
            }
        }
        return null;
    }

    /**
     * Finds the path whose name matches when case is ignored.
     *
     * @param paths the paths to search
     * @param wantedName the name to find
     * @return the matching path, or null if there is none
     */
    protected Path findIgnoringCase(List<Path> paths, String wantedName) {
        for (Path path : paths) {
            if (path.getFileName().toString().equalsIgnoreCase(wantedName)) {
                return path;
            }
        }
        return null;
    }

    /**
     * Finds the path with the exact name, else one whose name differs only
     * in case.
     *
     * @param paths the paths to search
     * @param wantedName the name to find
     * @return the matching path, or null if there is none
     */
    protected Path findByName(List<Path> paths, String wantedName) {
        Path exact = findExact(paths, wantedName);
        if (exact != null) {
            return exact;
        }
        return findIgnoringCase(paths, wantedName);
    }

    /**
     * Renames a file or folder. When only the letter case changes, Windows
     * and macOS see the new name as taken already, so the rename goes
     * through a temporary name.
     *
     * @param from the current path
     * @param to the new path
     * @throws FixException if something else already has the new name, or
     *         the rename fails
     */
    protected void rename(Path from, Path to) throws FixException {
        try {
            boolean targetExists = Files.exists(to, LinkOption.NOFOLLOW_LINKS);
            if (targetExists && !Files.isSameFile(from, to)) {
                throw new FixException("A file or folder named "
                        + to.getFileName() + " already exists, so nothing "
                        + "was renamed.");
            }

            if (targetExists) {
                String stepName = from.getFileName() + CASE_RENAME_SUFFIX;
                Path step = from.resolveSibling(stepName);
                FileUtils.moveWithRetry(from, step);
                FileUtils.moveWithRetry(step, to);
            } else {
                FileUtils.moveWithRetry(from, to);
            }
        } catch (IOException exception) {
            throw new FixException("Could not rename "
                    + from.getFileName() + " to " + to.getFileName() + ": "
                    + exception.getMessage(), exception);
        }
    }

    /**
     * Says whether any of the folders is named like a question folder, in
     * any case, as the validators check.
     *
     * @param folders the folders to search
     * @param folderNames the question folder names
     * @return true if one of them is named like a question folder
     */
    protected boolean holdsQuestionFolder(List<Path> folders,
            List<String> folderNames) {
        for (String folderName : folderNames) {
            if (findIgnoringCase(folders, folderName) != null) {
                return true;
            }
        }
        return false;
    }

    // Returns the only folder that holds a question folder, or null.
    private Path findOnlyHolder(List<Path> folders,
            List<String> folderNames) throws FixException {
        List<Path> holders = new ArrayList<>();
        for (Path folder : folders) {
            if (holdsQuestionFolder(listFolders(folder), folderNames)) {
                holders.add(folder);
            }
        }

        if (holders.size() != 1) {
            return null;
        }
        return holders.get(0);
    }

    // Returns the name at a place in the list, or null past its end.
    private String pickAt(List<String> names, int place) {
        if (place >= names.size()) {
            return null;
        }
        return names.get(place);
    }
}
