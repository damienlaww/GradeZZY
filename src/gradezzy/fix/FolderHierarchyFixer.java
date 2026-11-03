/*
 * File: FolderHierarchyFixer.java
 *
 * Purpose: Corrects a wrong folder hierarchy: moves Q folders that sit at the
 * top of the zip into a new folder named after the username, and moves Q
 * folders that sit one level too deep back up.
 *
 * Where it fits: Handles the improper hierarchy cases from the brief.
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
import gradezzy.model.Anomaly;
import gradezzy.model.AnomalyType;
import gradezzy.model.Submission;

/**
 * Corrects a wrong folder hierarchy: adds a missing username folder, or
 * moves question folders up out of an extra folder.
 */
public class FolderHierarchyFixer extends AbstractFixer {

    /**
     * The name shown in the anomaly log.
     */
    public static final String NAME = "Fix folder hierarchy";

    // The application settings, which name the question folders
    private final AppConfig config;

    /**
     * Creates a new folder hierarchy fixer.
     *
     * @param config the application settings
     */
    public FolderHierarchyFixer(AppConfig config) {
        super(NAME);
        this.config = config;
    }

    /**
     * Says whether this fixer handles the given anomaly.
     *
     * @param anomaly the anomaly
     * @return true if apply may be called for it
     */
    @Override
    public boolean canFix(Anomaly anomaly) {
        return anomaly.getType() == AnomalyType.MISSING_WRAPPER_FOLDER
                || anomaly.getType() == AnomalyType.MISSING_QUESTION_FOLDER;
    }

    /**
     * Adds the missing username folder, or moves the anomaly's question
     * folder up out of an extra folder, giving it the right letter case.
     *
     * @param submission the submission
     * @param anomaly the anomaly
     * @return a record that allows the fix to be undone, or null when the
     *         question folder is not one level too deep
     * @throws FixException if the operation fails
     */
    @Override
    public FixRecord apply(Submission submission,
            Anomaly anomaly) throws FixException {
        Path workingCopy = submission.getWorkingCopyPath();
        if (workingCopy == null) {
            throw new FixException("There is no working copy to fix.");
        }

        if (anomaly.getType() == AnomalyType.MISSING_QUESTION_FOLDER) {
            return moveUp(submission, anomaly);
        }
        return addUsernameFolder(submission, anomaly);
    }

    /**
     * Reverses the fix: removes the added username folder, or moves the
     * question folder back down into the extra folder.
     *
     * @param fixRecord the fix record
     * @throws FixException if the operation fails
     */
    @Override
    public void undo(FixRecord fixRecord) throws FixException {
        if (fixRecord.getAnomaly().getType()
                == AnomalyType.MISSING_QUESTION_FOLDER) {
            moveBackDown(fixRecord);
        } else {
            removeUsernameFolder(fixRecord);
        }
    }

    // Moves the visible top-level entries into a new username folder.
    private FixRecord addUsernameFolder(Submission submission,
            Anomaly anomaly) throws FixException {
        Path workingCopy = submission.getWorkingCopyPath();
        String username = submission.getUsername();
        Path usernameFolder = workingCopy.resolve(username);
        if (Files.exists(usernameFolder, LinkOption.NOFOLLOW_LINKS)) {
            throw new FixException("A " + username + " folder already "
                    + "exists, so the folders were left where they are.");
        }

        List<Path> entries = listEntries(workingCopy, false);
        try {
            Files.createDirectory(usernameFolder);
        } catch (IOException exception) {
            throw new FixException("Could not create the " + username
                    + " folder: " + exception.getMessage(), exception);
        }

        try {
            moveEntries(entries, workingCopy, usernameFolder);
        } catch (FixException exception) {
            removeEmptyFolder(usernameFolder, exception);
            throw exception;
        }

        return new FixRecord(anomaly, NAME, "Moved the question folders "
                + "into a new " + username + " folder.", workingCopy,
                workingCopy, usernameFolder, null);
    }

    // Moves everything back to the top and removes the username folder.
    private void removeUsernameFolder(FixRecord fixRecord)
            throws FixException {
        Path usernameFolder = fixRecord.getFixedPath();
        Path workingCopy = fixRecord.getOriginalPath();
        if (!Files.isDirectory(usernameFolder, LinkOption.NOFOLLOW_LINKS)) {
            throw new FixException("The " + usernameFolder.getFileName()
                    + " folder is no longer there, so nothing was moved.");
        }

        moveEntries(listEntries(usernameFolder, true), usernameFolder,
                workingCopy);
        try {
            Files.delete(usernameFolder);
        } catch (IOException exception) {
            throw new FixException("Moved the folders back but could not "
                    + "remove " + usernameFolder.getFileName() + ": "
                    + exception.getMessage(), exception);
        }
    }

    // Moves the anomaly's question folder up out of the extra folder.
    private FixRecord moveUp(Submission submission,
            Anomaly anomaly) throws FixException {
        Path workingCopy = submission.getWorkingCopyPath();
        List<String> folderNames = readFolderNames(config);
        Path contentRoot = findContentRoot(workingCopy,
                submission.getUsername(), folderNames);
        if (contentRoot == null) {
            return null;
        }

        String folderName = findMissingFolderName(submission, anomaly,
                contentRoot, folderNames);
        Path extraFolder = findExtraFolder(contentRoot, folderNames);
        if (folderName == null || extraFolder == null) {
            return null;
        }

        Path questionFolder = findByName(listFolders(extraFolder),
                folderName);
        if (questionFolder == null) {
            return null;
        }

        Path movedFolder = contentRoot.resolve(folderName);
        moveFolder(questionFolder, movedFolder);
        boolean removed = removeIfEmpty(extraFolder);
        return new FixRecord(anomaly, NAME, describeMove(questionFolder,
                folderName, extraFolder, removed), workingCopy,
                questionFolder, movedFolder, null);
    }

    // Says in words what moveUp did.
    private String describeMove(Path questionFolder, String folderName,
            Path extraFolder, boolean removed) {
        String oldName = questionFolder.getFileName().toString();
        String description = "Moved " + oldName + " up out of the extra "
                + extraFolder.getFileName() + " folder";
        if (!oldName.equals(folderName)) {
            description += " and renamed it " + folderName;
        }
        description += ".";

        if (removed) {
            description += " That folder was then empty and was removed.";
        }
        return description;
    }

    // Moves the question folder back into the extra folder, old name and all.
    private void moveBackDown(FixRecord fixRecord) throws FixException {
        Path fixedName = fixRecord.getFixedPath().getFileName();
        Path originalPath = fixRecord.getOriginalPath();
        Path extraName = originalPath.getParent().getFileName();
        Path contentRoot = findContentRoot(fixRecord.getWorkingCopy(),
                fixRecord.getAnomaly().getUsername(),
                readFolderNames(config));
        Path movedFolder = null;
        if (contentRoot != null) {
            movedFolder = findExact(listFolders(contentRoot),
                    fixedName.toString());
        }
        if (movedFolder == null) {
            throw new FixException("The " + fixedName + " folder is no "
                    + "longer there, so nothing was moved.");
        }

        Path extraFolder = contentRoot.resolve(extraName);
        try {
            Files.createDirectories(extraFolder);
        } catch (IOException exception) {
            throw new FixException("Could not make the " + extraName
                    + " folder again: " + exception.getMessage(), exception);
        }
        moveFolder(movedFolder, extraFolder.resolve(
                originalPath.getFileName()));
    }

    // Returns the one folder that is not a question folder but holds one.
    private Path findExtraFolder(Path contentRoot,
            List<String> folderNames) throws FixException {
        List<Path> extraFolders = new ArrayList<>();
        for (Path folder : listFolders(contentRoot)) {
            String name = folder.getFileName().toString();
            if (!isQuestionFolderName(name, folderNames)
                    && holdsQuestionFolder(listFolders(folder),
                    folderNames)) {
                extraFolders.add(folder);
            }
        }

        if (extraFolders.size() != 1) {
            return null;
        }
        return extraFolders.get(0);
    }

    // Says whether a name is a question folder name, in any case.
    private boolean isQuestionFolderName(String name,
            List<String> folderNames) {
        for (String folderName : folderNames) {
            if (folderName.equalsIgnoreCase(name)) {
                return true;
            }
        }
        return false;
    }

    // Moves one folder to a new path, whose name may differ in case.
    private void moveFolder(Path folder, Path target) throws FixException {
        if (Files.exists(target, LinkOption.NOFOLLOW_LINKS)) {
            throw new FixException(target.getFileName() + " already exists "
                    + "in " + target.getParent().getFileName()
                    + ", so nothing was moved.");
        }

        try {
            Files.move(folder, target);
        } catch (IOException exception) {
            throw new FixException("Could not move " + folder.getFileName()
                    + ": " + exception.getMessage(), exception);
        }
    }

    // Removes a now empty folder; one that cannot be removed is harmless.
    private boolean removeIfEmpty(Path folder) {
        try {
            if (isEmptyFolder(folder)) {
                Files.delete(folder);
                return true;
            }
            return false;
        } catch (IOException exception) {
            return false;
        }
    }

    // Moves entries to another folder, putting them back if a move fails.
    private void moveEntries(List<Path> entries, Path from,
            Path to) throws FixException {
        for (Path entry : entries) {
            Path target = to.resolve(entry.getFileName());
            if (Files.exists(target, LinkOption.NOFOLLOW_LINKS)) {
                throw new FixException(entry.getFileName() + " already "
                        + "exists in " + to.getFileName()
                        + ", so nothing was moved.");
            }
        }

        List<Path> moved = new ArrayList<>();
        try {
            for (Path entry : entries) {
                Path target = to.resolve(entry.getFileName());
                Files.move(entry, target);
                moved.add(target);
            }
        } catch (IOException exception) {
            moveBack(moved, from, exception);
            throw new FixException("Could not move the folders: "
                    + exception.getMessage(), exception);
        }
    }

    // Moves entries back after a failed move.
    private void moveBack(List<Path> moved, Path from,
            IOException failure) {
        for (Path entry : moved) {
            try {
                Files.move(entry, from.resolve(entry.getFileName()));
            } catch (IOException exception) {
                failure.addSuppressed(exception);
            }
        }
    }

    // Removes the username folder made by a fix that then failed.
    private void removeEmptyFolder(Path folder, FixException failure) {
        try {
            Files.deleteIfExists(folder);
        } catch (IOException exception) {
            failure.addSuppressed(exception);
        }
    }

    // Lists the entries of a folder in name order.
    private List<Path> listEntries(Path folder,
            boolean includeHidden) throws FixException {
        List<Path> entries = new ArrayList<>();

        try {
            DirectoryStream<Path> children = Files.newDirectoryStream(folder);
            try {
                for (Path child : children) {
                    if (includeHidden || !isHidden(child)) {
                        entries.add(child);
                    }
                }
            } finally {
                children.close();
            }
        } catch (IOException exception) {
            throw new FixException("Could not list "
                    + folder.getFileName() + ": " + exception.getMessage(),
                    exception);
        }

        Collections.sort(entries);
        return entries;
    }
}
