/*
 * File: CaseNameFixer.java
 *
 * Purpose: Renames a question folder or a required file whose name differs
 * from the expected one only in letter case, such as q3 to Q3 or q1a.java
 * to Q1a.java.
 *
 * Where it fits: Java and the testers need the exact names. Windows and
 * macOS ignore case, so a wrong case is easy to miss and costs the marks
 * of the whole question.
 */
package gradezzy.fix;

import java.nio.file.Path;
import java.util.List;

import gradezzy.config.AppConfig;
import gradezzy.model.Anomaly;
import gradezzy.model.AnomalyType;
import gradezzy.model.Submission;

/**
 * Renames a question folder or a required file whose name differs from the
 * expected one only in letter case.
 */
public class CaseNameFixer extends AbstractFixer {

    /**
     * The name shown in the anomaly log.
     */
    public static final String NAME = "Fix letter case";

    // The application settings, which name the folders and files
    private final AppConfig config;

    /**
     * Creates a new case name fixer.
     *
     * @param config the application settings
     */
    public CaseNameFixer(AppConfig config) {
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
        return anomaly.getType() == AnomalyType.MISSING_QUESTION_FOLDER
                || anomaly.getType() == AnomalyType.WRONG_FILE_NAME;
    }

    /**
     * Renames the question folder or required file the anomaly is about,
     * when its name differs from the expected one only in case.
     *
     * @param submission the submission
     * @param anomaly the anomaly
     * @return a record that allows the fix to be undone, or null when the
     *         name does not differ only in case
     * @throws FixException if the operation fails
     */
    @Override
    public FixRecord apply(Submission submission,
            Anomaly anomaly) throws FixException {
        Path workingCopy = submission.getWorkingCopyPath();
        if (workingCopy == null) {
            throw new FixException("There is no working copy to fix.");
        }

        Path contentRoot = findContentRoot(workingCopy,
                submission.getUsername(), readFolderNames(config));
        if (contentRoot == null) {
            return null;
        }

        if (anomaly.getType() == AnomalyType.MISSING_QUESTION_FOLDER) {
            return renameFolder(submission, anomaly, contentRoot);
        }
        return renameFile(submission, anomaly, contentRoot);
    }

    /**
     * Gives the folder or file its original name back, wherever it is now.
     *
     * @param fixRecord the fix record
     * @throws FixException if the operation fails
     */
    @Override
    public void undo(FixRecord fixRecord) throws FixException {
        Path renamed = findRenamed(fixRecord);
        if (renamed == null) {
            throw new FixException(fixRecord.getFixedPath().getFileName()
                    + " is no longer there, so nothing was renamed.");
        }
        rename(renamed, renamed.resolveSibling(
                fixRecord.getOriginalPath().getFileName()));
    }

    // Renames the anomaly's question folder when only its case is wrong.
    private FixRecord renameFolder(Submission submission, Anomaly anomaly,
            Path contentRoot) throws FixException {
        String folderName = findMissingFolderName(submission, anomaly,
                contentRoot, readFolderNames(config));
        if (folderName == null) {
            return null;
        }

        Path wrongCase = findIgnoringCase(listFolders(contentRoot),
                folderName);
        if (wrongCase == null) {
            return null;
        }

        Path renamed = contentRoot.resolve(folderName);
        rename(wrongCase, renamed);
        return new FixRecord(anomaly, NAME, "Renamed the folder "
                + wrongCase.getFileName() + " to " + folderName + ".",
                submission.getWorkingCopyPath(), wrongCase, renamed, null);
    }

    // Renames the anomaly's required file when only its case is wrong.
    private FixRecord renameFile(Submission submission, Anomaly anomaly,
            Path contentRoot) throws FixException {
        String questionId = findQuestionId(config, anomaly);
        if (questionId == null) {
            throw new FixException("Could not tell which file this name "
                    + "problem is about, so nothing was renamed.");
        }

        String folderName = readFolderName(config, questionId);
        String fileName = readFileName(config, questionId);
        Path folder = findExact(listFolders(contentRoot), folderName);
        Path wrongCase = null;
        if (folder != null) {
            wrongCase = findWrongCase(listFiles(folder), fileName);
        }
        if (wrongCase == null) {
            return null;
        }

        Path renamed = folder.resolve(fileName);
        rename(wrongCase, renamed);
        return new FixRecord(anomaly, NAME, "Renamed " + folderName
                + FOLDER_SEPARATOR + wrongCase.getFileName() + " to "
                + folderName + FOLDER_SEPARATOR + fileName + ".",
                submission.getWorkingCopyPath(), wrongCase, renamed, null);
    }

    // Returns the file named like the wanted one but in another case.
    private Path findWrongCase(List<Path> files, String wantedName) {
        if (findExact(files, wantedName) != null) {
            return null;
        }
        return findIgnoringCase(files, wantedName);
    }

    // Finds the renamed folder or file as the working copy is now.
    private Path findRenamed(FixRecord fixRecord) throws FixException {
        Path fixedPath = fixRecord.getFixedPath();
        String fixedName = fixedPath.getFileName().toString();
        boolean isFile = fixRecord.getAnomaly().getType()
                == AnomalyType.WRONG_FILE_NAME;
        String folderName = fixedName;
        if (isFile) {
            folderName = fixedPath.getParent().getFileName().toString();
        }

        Path folder = findQuestionFolderNow(fixRecord.getWorkingCopy(),
                fixRecord.getAnomaly().getUsername(),
                readFolderNames(config), folderName);
        if (folder == null) {
            return null;
        } else if (!isFile) {
            return folder;
        }
        return findExact(listFiles(folder), fixedName);
    }
}
