/*
 * File: MisplacedFileFixer.java
 *
 * Purpose: Moves a required file that was saved in another question's
 * folder, such as Q1a.java in Q2, into its own question folder.
 *
 * Where it fits: A file in the wrong folder is reported as missing and
 * its question scores 0, although the answer is there. The move is made
 * in the working copy and waits for the instructor to keep or reject it,
 * like every other fix.
 */
package gradezzy.fix;

import java.nio.file.Files;
import java.nio.file.LinkOption;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.List;

import gradezzy.config.AppConfig;
import gradezzy.model.Anomaly;
import gradezzy.model.AnomalyType;
import gradezzy.model.Submission;

/**
 * Moves a required file found in exactly one other question folder into
 * its own question folder.
 *
 * <p>Tier: SHOULD. Owner: Jing Yuan.
 */
public class MisplacedFileFixer extends AbstractFixer {

    /**
     * The name shown in the anomaly log.
     */
    public static final String NAME = "Move file to its question folder";

    // A file found in this many other question folders can be moved;
    // in more, it is unclear which copy is the answer
    private static final int ONE_PLACE = 1;

    // The application settings, which name the folders and files
    private final AppConfig config;

    /**
     * Creates a new misplaced file fixer.
     *
     * <p>Tier: SHOULD. Owner: Jing Yuan.
     *
     * @param config the application settings
     */
    public MisplacedFileFixer(AppConfig config) {
        super(NAME);
        this.config = config;
    }

    /**
     * Says whether this fixer handles the given anomaly: a required file
     * that is missing from its question folder.
     *
     * <p>Tier: SHOULD. Owner: Jing Yuan.
     *
     * @param anomaly the anomaly
     * @return true if apply may be called for it
     */
    @Override
    public boolean canFix(Anomaly anomaly) {
        return anomaly.getType() == AnomalyType.MISSING_FILE;
    }

    /**
     * Finds the missing file in the other question folders, by name in
     * any letter case.
     *
     * <p>Tier: SHOULD. Owner: Jing Yuan.
     *
     * @param submission the submission
     * @param anomaly the anomaly
     * @return the file to move, or null when it is in no other question
     *         folder, in more than one, or the anomaly names no question
     * @throws FixException if the working copy cannot be read
     */
    @Override
    public Path findPathToChange(Submission submission,
            Anomaly anomaly) throws FixException {
        Path workingCopy = submission.getWorkingCopyPath();
        if (workingCopy == null) {
            throw new FixException("There is no working copy to fix.");
        }

        String questionId = findQuestionId(config, anomaly);
        if (questionId == null) {
            return null;
        }
        List<String> folderNames = readFolderNames(config);
        Path contentRoot = findContentRoot(workingCopy,
                submission.getUsername(), folderNames);
        if (contentRoot == null) {
            return null;
        }

        String folderName = readFolderName(config, questionId);
        String fileName = readFileName(config, questionId);
        List<Path> found = new ArrayList<>();
        for (Path folder : listFolders(contentRoot)) {
            String name = folder.getFileName().toString();
            boolean otherQuestion = !name.equalsIgnoreCase(folderName)
                    && isQuestionFolderName(name, folderNames);
            Path file = null;
            if (otherQuestion) {
                file = findByName(listFiles(folder), fileName);
            }
            if (file != null) {
                found.add(file);
            }
        }

        if (found.size() != ONE_PLACE) {
            return null;
        }
        return found.get(0);
    }

    /**
     * Moves the file into its question folder, under the expected name.
     *
     * <p>Tier: SHOULD. Owner: Jing Yuan.
     *
     * @param submission the submission
     * @param anomaly the anomaly
     * @return a record that allows the move to be undone, or null when
     *         the file is not found once in another question folder
     * @throws FixException if the question folder is gone or already
     *         holds the file, or the move fails
     */
    @Override
    public FixRecord apply(Submission submission,
            Anomaly anomaly) throws FixException {
        Path misplaced = findPathToChange(submission, anomaly);
        if (misplaced == null) {
            return null;
        }

        String questionId = findQuestionId(config, anomaly);
        String folderName = readFolderName(config, questionId);
        String fileName = readFileName(config, questionId);
        Path contentRoot = misplaced.getParent().getParent();
        Path questionFolder = findExact(listFolders(contentRoot),
                folderName);
        if (questionFolder == null) {
            throw new FixException("The " + folderName + " folder is not "
                    + "there, so " + fileName + " was not moved.");
        }

        Path target = questionFolder.resolve(fileName);
        rename(misplaced, target);

        String shownBefore = misplaced.getParent().getFileName()
                + FOLDER_SEPARATOR + misplaced.getFileName();
        String shownAfter = folderName + FOLDER_SEPARATOR + fileName;
        return new FixRecord(anomaly, NAME, "Moved " + shownBefore
                + " to " + shownAfter + ", where the tester looks for it.",
                submission.getWorkingCopyPath(), misplaced, target, null,
                shownBefore, shownAfter);
    }

    /**
     * Moves the file back to the folder and name the student gave it,
     * wherever the question folders are now.
     *
     * <p>Tier: SHOULD. Owner: Jing Yuan.
     *
     * @param fixRecord the fix record
     * @throws FixException if the moved file or its old folder is gone,
     *         or the move fails
     */
    @Override
    public void undo(FixRecord fixRecord) throws FixException {
        Path workingCopy = fixRecord.getWorkingCopy();
        String username = fixRecord.getAnomaly().getUsername();
        List<String> folderNames = readFolderNames(config);
        Path fixedPath = fixRecord.getFixedPath();
        Path originalPath = fixRecord.getOriginalPath();

        String movedFolderName =
                fixedPath.getParent().getFileName().toString();
        String oldFolderName =
                originalPath.getParent().getFileName().toString();
        Path movedFolder = findQuestionFolderNow(workingCopy, username,
                folderNames, movedFolderName);
        Path oldFolder = findQuestionFolderNow(workingCopy, username,
                folderNames, oldFolderName);
        Path moved = null;
        if (movedFolder != null) {
            moved = movedFolder.resolve(fixedPath.getFileName());
        }

        boolean canMoveBack = moved != null && oldFolder != null
                && Files.isRegularFile(moved, LinkOption.NOFOLLOW_LINKS);
        if (!canMoveBack) {
            throw new FixException(fixedPath.getFileName() + " or its old "
                    + "folder is no longer there, so nothing was moved.");
        }
        Path movedBack = oldFolder.resolve(originalPath.getFileName());
        rename(moved, movedBack);
    }

    // Says whether a folder name is a question folder name, in any case.
    private boolean isQuestionFolderName(String name,
            List<String> folderNames) {
        for (String folderName : folderNames) {
            if (folderName.equalsIgnoreCase(name)) {
                return true;
            }
        }
        return false;
    }
}
