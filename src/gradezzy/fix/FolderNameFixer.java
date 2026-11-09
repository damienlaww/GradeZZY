/*
 * File: FolderNameFixer.java
 *
 * Purpose: Renames a wrongly named top folder to the student's username.
 *
 * Where it fits: Handles the forgotten folder renaming case from the brief.
 */
package gradezzy.fix;

import java.nio.file.Files;
import java.nio.file.LinkOption;
import java.nio.file.Path;

import gradezzy.config.AppConfig;
import gradezzy.model.Anomaly;
import gradezzy.model.AnomalyType;
import gradezzy.model.Submission;

/**
 * Renames a wrongly named top folder to the student's username.
 *
 * <p>Tier: MUST. Owner: Jun Sheng.
 */
public class FolderNameFixer extends AbstractFixer {

    /**
     * The name shown in the anomaly log.
     */
    public static final String NAME = "Rename folder to username";

    // The application settings, which name the question folders
    private final AppConfig config;

    /**
     * Creates a new folder name fixer.
     *
     * @param config the application settings
     */
    public FolderNameFixer(AppConfig config) {
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
        return anomaly.getType() == AnomalyType.WRONG_FOLDER_NAME;
    }

    /**
     * Finds the top folder that holds the question folders, when it is
     * not named after the username. Other top folders, such as bin, are
     * left alone.
     *
     * @param submission the submission
     * @param anomaly the anomaly
     * @return the folder to rename, or null if it is named right already
     * @throws FixException if no single top folder holds the questions
     */
    @Override
    public Path findPathToChange(Submission submission,
            Anomaly anomaly) throws FixException {
        Path workingCopy = submission.getWorkingCopyPath();
        if (workingCopy == null) {
            throw new FixException("There is no working copy to fix.");
        }

        String username = submission.getUsername();
        Path contentRoot = findContentRoot(workingCopy, username,
                readFolderNames(config));
        if (contentRoot == null) {
            throw new FixException("Could not tell which top folder holds "
                    + "the question folders, so nothing was renamed.");
        }

        boolean namedRight = contentRoot.equals(workingCopy)
                || contentRoot.getFileName().toString().equals(username);
        if (namedRight) {
            return null;
        }
        return contentRoot;
    }

    /**
     * Renames the top folder that holds the question folders to the
     * username.
     *
     * @param submission the submission
     * @param anomaly the anomaly
     * @return a record that allows the fix to be undone, or null when the
     *         folder is named right already
     * @throws FixException if the operation fails
     */
    @Override
    public FixRecord apply(Submission submission,
            Anomaly anomaly) throws FixException {
        Path wrongFolder = findPathToChange(submission, anomaly);
        if (wrongFolder == null) {
            return null;
        }

        Path workingCopy = submission.getWorkingCopyPath();
        String username = submission.getUsername();
        Path usernameFolder = workingCopy.resolve(username);
        rename(wrongFolder, usernameFolder);

        return new FixRecord(anomaly, NAME, "Renamed the folder "
                + wrongFolder.getFileName() + " to " + username + ".",
                workingCopy, wrongFolder, usernameFolder, null,
                wrongFolder.getFileName() + FOLDER_SEPARATOR,
                username + FOLDER_SEPARATOR);
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
}
