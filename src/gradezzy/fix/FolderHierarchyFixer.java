/*
 * File: FolderHierarchyFixer.java
 *
 * Purpose: Moves Q folders that sit at the top of the zip into a new folder
 * named after the username.
 *
 * Where it fits: Handles the improper hierarchy case from the brief.
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

import gradezzy.model.Anomaly;
import gradezzy.model.AnomalyType;
import gradezzy.model.Submission;

/**
 * Moves Q folders that sit at the top of the zip into a new folder named after
 * the username.
 */
public class FolderHierarchyFixer extends AbstractFixer {

    /**
     * The name shown in the anomaly log.
     */
    public static final String NAME = "Add username folder";

    /**
     * Creates a new folder hierarchy fixer.
     */
    public FolderHierarchyFixer() {
        super(NAME);
    }

    /**
     * Says whether this fixer handles the given anomaly.
     *
     * @param anomaly the anomaly
     * @return true if apply may be called for it
     */
    @Override
    public boolean canFix(Anomaly anomaly) {
        return anomaly.getType() == AnomalyType.MISSING_WRAPPER_FOLDER;
    }

    /**
     * Moves the visible top-level entries into a new username folder.
     *
     * @param submission the submission
     * @param anomaly the anomaly
     * @return a record that allows the fix to be undone
     * @throws FixException if the operation fails
     */
    @Override
    public FixRecord apply(Submission submission,
            Anomaly anomaly) throws FixException {
        Path workingCopy = submission.getWorkingCopyPath();
        if (workingCopy == null) {
            throw new FixException("There is no working copy to fix.");
        }

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
                usernameFolder, null);
    }

    /**
     * Moves everything back to the top and removes the username folder.
     *
     * @param fixRecord the fix record
     * @throws FixException if the operation fails
     */
    @Override
    public void undo(FixRecord fixRecord) throws FixException {
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

        try (DirectoryStream<Path> children =
                Files.newDirectoryStream(folder)) {
            for (Path child : children) {
                if (includeHidden || !isHidden(child)) {
                    entries.add(child);
                }
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
