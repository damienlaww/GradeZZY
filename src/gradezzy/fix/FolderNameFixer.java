/*
 * File: FolderNameFixer.java
 *
 * Purpose: Renames a wrongly named top folder to the student's username.
 *
 * Where it fits: Handles the forgotten folder renaming case from the brief.
 */
package gradezzy.fix;

import java.io.IOException;
import java.nio.file.DirectoryStream;
import java.nio.file.Files;
import java.nio.file.LinkOption;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.List;

import gradezzy.model.Anomaly;
import gradezzy.model.AnomalyType;
import gradezzy.model.Submission;

/**
 * Renames a wrongly named top folder to the student's username.
 */
public class FolderNameFixer extends AbstractFixer {

    /**
     * The name shown in the anomaly log.
     */
    public static final String NAME = "Rename folder to username";

    /**
     * Creates a new folder name fixer.
     */
    public FolderNameFixer() {
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
        return anomaly.getType() == AnomalyType.WRONG_FOLDER_NAME;
    }

    /**
     * Renames the only top folder of the working copy to the username.
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

        List<Path> topFolders = listTopFolders(workingCopy);
        if (topFolders.size() != 1) {
            throw new FixException("Expected one top folder but found "
                    + topFolders.size() + ", so nothing was renamed.");
        }

        Path wrongFolder = topFolders.get(0);
        String username = submission.getUsername();
        Path usernameFolder = workingCopy.resolve(username);
        rename(wrongFolder, usernameFolder);

        return new FixRecord(anomaly, NAME, "Renamed the folder "
                + wrongFolder.getFileName() + " to " + username + ".",
                workingCopy, wrongFolder, usernameFolder, null);
    }

    /**
     * Reverses this fix by renaming the folder back to its original name.
     *
     * @param fixRecord the fix record
     * @throws FixException if the operation fails
     */
    @Override
    public void undo(FixRecord fixRecord) throws FixException {
        Path usernameFolder = fixRecord.getFixedPath();
        if (!Files.isDirectory(usernameFolder, LinkOption.NOFOLLOW_LINKS)) {
            throw new FixException("The " + usernameFolder.getFileName()
                    + " folder is no longer there, so nothing was renamed.");
        }
        rename(usernameFolder, fixRecord.getOriginalPath());
    }

    // Lists the visible folders at the top of the working copy.
    private List<Path> listTopFolders(Path workingCopy) throws FixException {
        List<Path> folders = new ArrayList<>();

        try (DirectoryStream<Path> children =
                Files.newDirectoryStream(workingCopy)) {
            for (Path child : children) {
                if (Files.isDirectory(child, LinkOption.NOFOLLOW_LINKS)
                        && !isHidden(child)) {
                    folders.add(child);
                }
            }
        } catch (IOException exception) {
            throw new FixException("Could not list the working copy: "
                    + exception.getMessage(), exception);
        }
        return folders;
    }
}
