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
import gradezzy.model.FixStatus;
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

    // Follows the folder names in Before for a missing username folder
    private static final String AT_THE_TOP = " at the top of the ZIP";

    // Follows them instead when the upload was a folder unzipped by hand
    private static final String AT_THE_TOP_OF_FOLDER =
            " at the top of the uploaded folder";

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
     * Finds what the fix would change: the extra folder that holds the
     * anomaly's question folder, or the whole working copy when the
     * username folder is missing.
     *
     * @param submission the submission
     * @param anomaly the anomaly
     * @return the extra folder or the working copy, or null when the
     *         question folder is not one level too deep
     * @throws FixException if a username folder exists already or the
     *         working copy cannot be read
     */
    @Override
    public Path findPathToChange(Submission submission,
            Anomaly anomaly) throws FixException {
        Path workingCopy = submission.getWorkingCopyPath();
        if (workingCopy == null) {
            throw new FixException("There is no working copy to fix.");
        }

        if (anomaly.getType() == AnomalyType.MISSING_QUESTION_FOLDER) {
            return findExtraFolderOf(submission, anomaly);
        }

        String username = submission.getUsername();
        if (Files.exists(workingCopy.resolve(username),
                LinkOption.NOFOLLOW_LINKS)) {
            throw new FixException("A " + username + " folder already "
                    + "exists, so the folders were left where they are.");
        }
        return workingCopy;
    }

    /**
     * Adds the missing username folder, or moves every question folder up
     * out of an extra folder in one fix, giving each the right letter
     * case. The other problems of that move are then removed, so the
     * instructor sees one fix.
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
        Path pathToChange = findPathToChange(submission, anomaly);
        if (pathToChange == null) {
            return null;
        }

        if (anomaly.getType() == AnomalyType.MISSING_QUESTION_FOLDER) {
            return moveUp(submission, anomaly, pathToChange);
        }
        return addUsernameFolder(submission, anomaly);
    }

    /**
     * Reverses the fix: removes the added username folder, or moves the
     * question folders back down into the extra folder.
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

    // Finds the extra folder holding the anomaly's question folder.
    private Path findExtraFolderOf(Submission submission,
            Anomaly anomaly) throws FixException {
        List<String> folderNames = readFolderNames(config);
        Path contentRoot = findContentRoot(submission.getWorkingCopyPath(),
                submission.getUsername(), folderNames);
        if (contentRoot == null) {
            return null;
        }

        String folderName = findMissingFolderName(submission, anomaly,
                contentRoot, folderNames);
        Path extraFolder = findExtraFolder(contentRoot, folderNames);
        if (folderName == null || extraFolder == null
                || !canMoveUp(folderName, contentRoot, extraFolder)) {
            return null;
        }
        return extraFolder;
    }

    // Says whether a question folder is in the extra folder only.
    private boolean canMoveUp(String folderName, Path contentRoot,
            Path extraFolder) throws FixException {
        return findIgnoringCase(listFolders(contentRoot), folderName) == null
                && findByName(listFolders(extraFolder), folderName) != null;
    }

    // Moves the visible top-level entries into a new username folder.
    private FixRecord addUsernameFolder(Submission submission,
            Anomaly anomaly) throws FixException {
        Path workingCopy = submission.getWorkingCopyPath();
        String username = submission.getUsername();
        Path usernameFolder = workingCopy.resolve(username);
        List<Path> entries = listEntries(workingCopy, false);
        List<String> folderNames = new ArrayList<>();
        List<String> shownBefore = new ArrayList<>();
        for (Path folder : listFolders(workingCopy)) {
            folderNames.add(folder.getFileName().toString());
            shownBefore.add(folder.getFileName() + FOLDER_SEPARATOR);
        }

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
                workingCopy, usernameFolder, null,
                joinNames(shownBefore) + describeTop(submission),
                username + FOLDER_SEPARATOR + joinNames(folderNames));
    }

    // Says where the question folders were: at the top of the ZIP file,
    // or of the folder the instructor chose.
    private String describeTop(Submission submission) {
        Path upload = submission.getOriginalPath();
        boolean folder = upload != null
                && Files.isDirectory(upload, LinkOption.NOFOLLOW_LINKS);
        if (folder) {
            return AT_THE_TOP_OF_FOLDER;
        }
        return AT_THE_TOP;
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

    // Moves every question folder up out of the extra folder at once.
    private FixRecord moveUp(Submission submission, Anomaly anomaly,
            Path extraFolder) throws FixException {
        Path workingCopy = submission.getWorkingCopyPath();
        Path contentRoot = extraFolder.getParent();
        List<String> folderNames = readFolderNames(config);
        List<Anomaly> sameMove = findSameMove(submission, anomaly,
                contentRoot, extraFolder, folderNames);

        List<Path> pathsBefore = new ArrayList<>();
        List<Path> pathsAfter = new ArrayList<>();
        List<String> namesBefore = new ArrayList<>();
        for (String folderName : folderNames) {
            if (canMoveUp(folderName, contentRoot, extraFolder)) {
                Path questionFolder = findByName(listFolders(extraFolder),
                        folderName);
                pathsBefore.add(questionFolder);
                pathsAfter.add(contentRoot.resolve(folderName));
                namesBefore.add(questionFolder.getFileName().toString());
            }
        }
        List<String> namesAfter = new ArrayList<>();
        for (Path movedFolder : pathsAfter) {
            namesAfter.add(movedFolder.getFileName().toString());
        }

        moveFolders(pathsBefore, pathsAfter);
        boolean removed = removeIfEmpty(extraFolder);
        submission.getAnomalies().removeAll(sameMove);

        String description = describeMove(namesBefore, namesAfter,
                extraFolder, removed);
        String valueBefore = showFolder(workingCopy, extraFolder)
                + joinNames(namesBefore);
        String valueAfter = showFolder(workingCopy, contentRoot)
                + joinNames(namesAfter);

        return new FolderMoveRecord(anomaly, NAME, description, workingCopy,
                extraFolder, contentRoot, valueBefore, valueAfter,
                pathsBefore, pathsAfter);
    }

    // Finds the other missing folder problems this move also fixes.
    private List<Anomaly> findSameMove(Submission submission,
            Anomaly anomaly, Path contentRoot, Path extraFolder,
            List<String> folderNames) throws FixException {
        List<Anomaly> sameMove = new ArrayList<>();
        for (Anomaly other : submission.getAnomalies()) {
            boolean isOther = other.getType()
                    == AnomalyType.MISSING_QUESTION_FOLDER
                    && other.getFixStatus() != FixStatus.APPLIED
                    && !other.getId().equals(anomaly.getId());
            if (isOther && isMovedToo(submission, other, contentRoot,
                    extraFolder, folderNames)) {
                sameMove.add(other);
            }
        }
        return sameMove;
    }

    // Says whether the folder another problem is about moves up too.
    private boolean isMovedToo(Submission submission, Anomaly other,
            Path contentRoot, Path extraFolder, List<String> folderNames)
            throws FixException {
        String folderName = findMissingFolderName(submission, other,
                contentRoot, folderNames);
        return folderName != null
                && canMoveUp(folderName, contentRoot, extraFolder);
    }

    // Says in words what moveUp did.
    private String describeMove(List<String> namesBefore,
            List<String> namesAfter, Path extraFolder, boolean removed) {
        String description = "Moved " + joinNames(namesBefore)
                + " up out of the extra " + extraFolder.getFileName()
                + " folder";
        if (!namesBefore.equals(namesAfter)) {
            description += ", named " + joinNames(namesAfter);
        }
        description += ".";

        if (removed) {
            description += " That folder was then empty and was removed.";
        }
        return description;
    }

    // Moves the question folders back into the extra folder.
    private void moveBackDown(FixRecord fixRecord) throws FixException {
        if (!(fixRecord instanceof FolderMoveRecord)) {
            throw new FixException("This fix did not keep the folders it "
                    + "moved, so nothing was moved.");
        }

        FolderMoveRecord moveRecord = (FolderMoveRecord) fixRecord;
        Path contentRoot = findContentRoot(fixRecord.getWorkingCopy(),
                fixRecord.getAnomaly().getUsername(),
                readFolderNames(config));
        if (contentRoot == null) {
            throw new FixException("The moved folders are no longer "
                    + "there, so nothing was moved.");
        }

        List<Path> movedFolders = new ArrayList<>();
        for (Path pathAfter : moveRecord.getPathsAfter()) {
            Path movedFolder = findExact(listFolders(contentRoot),
                    pathAfter.getFileName().toString());
            if (movedFolder == null) {
                throw new FixException("The " + pathAfter.getFileName()
                        + " folder is no longer there, so nothing was "
                        + "moved.");
            }
            movedFolders.add(movedFolder);
        }

        Path extraName = fixRecord.getOriginalPath().getFileName();
        Path extraFolder = contentRoot.resolve(extraName);
        try {
            Files.createDirectories(extraFolder);
        } catch (IOException exception) {
            throw new FixException("Could not make the " + extraName
                    + " folder again: " + exception.getMessage(), exception);
        }

        List<Path> targets = new ArrayList<>();
        for (Path pathBefore : moveRecord.getPathsBefore()) {
            targets.add(extraFolder.resolve(pathBefore.getFileName()));
        }
        moveFolders(movedFolders, targets);
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

    // Moves folders to new paths, putting them back if a move fails.
    private void moveFolders(List<Path> folders,
            List<Path> targets) throws FixException {
        for (int i = 0; i < folders.size(); i++) {
            try {
                moveFolder(folders.get(i), targets.get(i));
            } catch (FixException exception) {
                putBack(targets, folders, i, exception);
                throw exception;
            }
        }
    }

    // Moves the first few moved folders back after a failed move.
    private void putBack(List<Path> targets, List<Path> folders,
            int movedCount, FixException failure) {
        for (int i = 0; i < movedCount; i++) {
            try {
                Files.move(targets.get(i), folders.get(i));
            } catch (IOException exception) {
                failure.addSuppressed(exception);
            }
        }
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
