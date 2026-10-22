/*
 * File: AbstractFixer.java
 *
 * Purpose: Shared code for all fixers.
 *
 * Where it fits: Holds the name and the backup helper every fixer needs before
 * it changes a file.
 */
package gradezzy.fix;

import java.io.IOException;
import java.nio.file.DirectoryStream;
import java.nio.file.Files;
import java.nio.file.LinkOption;
import java.nio.file.Path;

import gradezzy.model.Submission;
import gradezzy.util.FileUtils;

/**
 * Shared code for all fixers.
 */
public abstract class AbstractFixer implements Fixer {

    /**
     * The hidden folder in each working copy that holds backups.
     */
    public static final String BACKUP_FOLDER_NAME = ".gradezzy-backup";

    // The start of the name of each backup's own folder
    private static final String BACKUP_SLOT_PREFIX = "fix-";

    // The start of a hidden name
    private static final String HIDDEN_PREFIX = ".";

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
     * Returns the working copy that holds a backup made by backup.
     *
     * @param backupPath the path returned by backup
     * @return the working copy folder
     */
    protected Path findWorkingCopyOf(Path backupPath) {
        Path slot = backupPath.getParent();
        Path backupFolder = slot.getParent();
        return backupFolder.getParent();
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

    // Says whether a folder exists and holds nothing.
    private boolean isEmptyFolder(Path folder) throws IOException {
        if (!Files.isDirectory(folder, LinkOption.NOFOLLOW_LINKS)) {
            return false;
        }
        try (DirectoryStream<Path> children =
                Files.newDirectoryStream(folder)) {
            return !children.iterator().hasNext();
        }
    }
}
