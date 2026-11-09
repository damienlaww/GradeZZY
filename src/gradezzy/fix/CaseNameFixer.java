/*
 * File: CaseNameFixer.java
 *
 * Purpose: Renames a question folder or a required file whose name differs
 * from the expected one only in letter case, such as q3 to Q3 or q1a.java
 * to Q1a.java, or a file whose name has an ending added, such as
 * Q2a.java.txt to Q2a.java.
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
 * expected one only in letter case. A required file can also be the answer
 * file with an ending added, such as Q2a.java.txt, or another file of the
 * starter, such as TaskComparator.java. FileNameValidator names the wrong
 * and the right file in its message, and this fixer reads them from there.
 *
 * <p>Tier: SHOULD. Owner: Jun Sheng.
 */
public class CaseNameFixer extends AbstractFixer {

    /**
     * The name shown in the anomaly log.
     */
    public static final String NAME = "Fix letter case";

    // Comes between the wrong and the right path in the message that
    // FileNameValidator writes, such as "Q4/Taskcomparator.java should be
    // named Q4/TaskComparator.java."
    private static final String RIGHT_PATH_START = " should be named ";

    // Ends the message that FileNameValidator writes
    private static final String MESSAGE_END = ".";

    // The application settings, which name the folders
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
     * Finds the question folder or required file the anomaly is about,
     * when its name differs from the expected one only in case, or the
     * file's name has an ending added.
     *
     * @param submission the submission
     * @param anomaly the anomaly
     * @return the folder or file to rename, or null when there is nothing
     *         to rename
     * @throws FixException if the working copy cannot be read
     */
    @Override
    public Path findPathToChange(Submission submission,
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
            return findWrongCaseFolder(submission, anomaly, contentRoot);
        }
        return findWrongCaseFile(anomaly, contentRoot);
    }

    /**
     * Renames the question folder or required file the anomaly is about,
     * when its name differs from the expected one only in case, or the
     * file's name has an ending added.
     *
     * @param submission the submission
     * @param anomaly the anomaly
     * @return a record that allows the fix to be undone, or null when there
     *         is nothing to rename
     * @throws FixException if the operation fails
     */
    @Override
    public FixRecord apply(Submission submission,
            Anomaly anomaly) throws FixException {
        Path wrongCase = findPathToChange(submission, anomaly);
        if (wrongCase == null) {
            return null;
        }

        String wrongName = wrongCase.getFileName().toString();
        String rightName = findRightName(anomaly, wrongName);
        Path renamed = wrongCase.resolveSibling(rightName);
        rename(wrongCase, renamed);

        if (anomaly.getType() == AnomalyType.MISSING_QUESTION_FOLDER) {
            return new FixRecord(anomaly, NAME, "Renamed the folder "
                    + wrongName + " to " + rightName + ".",
                    submission.getWorkingCopyPath(), wrongCase, renamed,
                    null, wrongName + FOLDER_SEPARATOR,
                    rightName + FOLDER_SEPARATOR);
        }

        String folderName = wrongCase.getParent().getFileName()
                + FOLDER_SEPARATOR;
        return new FixRecord(anomaly, NAME, "Renamed " + folderName
                + wrongName + " to " + folderName + rightName + ".",
                submission.getWorkingCopyPath(), wrongCase, renamed, null,
                folderName + wrongName, folderName + rightName);
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
        Path originalName = fixRecord.getOriginalPath().getFileName();
        rename(renamed, renamed.resolveSibling(originalName));
    }

    // Finds the anomaly's question folder when only its case is wrong.
    private Path findWrongCaseFolder(Submission submission, Anomaly anomaly,
            Path contentRoot) throws FixException {
        String folderName = findMissingFolderName(submission, anomaly,
                contentRoot, readFolderNames(config));
        if (folderName == null) {
            return null;
        }
        return findIgnoringCase(listFolders(contentRoot), folderName);
    }

    // Finds the anomaly's misnamed file while the right name is not there.
    private Path findWrongCaseFile(Anomaly anomaly,
            Path contentRoot) throws FixException {
        String wrongPath = readWrongPath(anomaly);
        String rightPath = readRightPath(anomaly);
        boolean pathsRead = isShownPath(wrongPath) && isShownPath(rightPath);
        if (!pathsRead) {
            throw new FixException("Could not tell which file this name "
                    + "problem is about, so nothing was renamed.");
        }

        String folderName = readFolderPart(rightPath);
        Path folder = findExact(listFolders(contentRoot), folderName);
        if (folder == null) {
            return null;
        }

        List<Path> files = listFiles(folder);
        String rightName = readNamePart(rightPath);
        if (findExact(files, rightName) != null) {
            return null;
        }
        String wrongName = readNamePart(wrongPath);
        return findExact(files, wrongName);
    }

    // Says whether a path read from a message names a folder and a file.
    private boolean isShownPath(String shownPath) {
        return shownPath != null && shownPath.contains(FOLDER_SEPARATOR);
    }

    // Returns the expected name that a wrong name stands for.
    private String findRightName(Anomaly anomaly, String wrongName) {
        if (anomaly.getType() == AnomalyType.WRONG_FILE_NAME) {
            String rightPath = readRightPath(anomaly);
            return readNamePart(rightPath);
        }

        String rightName = wrongName;
        for (String folderName : readFolderNames(config)) {
            if (folderName.equalsIgnoreCase(wrongName)) {
                rightName = folderName;
            }
        }
        return rightName;
    }

    // Reads the wrong path from a file name message, such as
    // Q2/Q2a.java.txt, or returns null for another message. The message
    // is split at its last RIGHT_PATH_START, because only the wrong name
    // comes from the student.
    private String readWrongPath(Anomaly anomaly) {
        String message = anomaly.getMessage();
        int middle = message.lastIndexOf(RIGHT_PATH_START);
        if (middle < 0) {
            return null;
        }
        return message.substring(0, middle);
    }

    // Reads the right path from a file name message, such as
    // Q2/Q2a.java, or returns null for another message.
    private String readRightPath(Anomaly anomaly) {
        String message = anomaly.getMessage();
        int middle = message.lastIndexOf(RIGHT_PATH_START);
        boolean isNameMessage = middle >= 0 && message.endsWith(MESSAGE_END);
        if (!isNameMessage) {
            return null;
        }

        int rightStart = middle + RIGHT_PATH_START.length();
        int rightEnd = message.length() - MESSAGE_END.length();
        return message.substring(rightStart, rightEnd);
    }

    // Returns the folder of a path such as Q2/Q2a.java: Q2.
    private String readFolderPart(String shownPath) {
        int separatorIndex = shownPath.lastIndexOf(FOLDER_SEPARATOR);
        return shownPath.substring(0, separatorIndex);
    }

    // Returns the file of a path such as Q2/Q2a.java: Q2a.java.
    private String readNamePart(String shownPath) {
        int nameStart = shownPath.lastIndexOf(FOLDER_SEPARATOR)
                + FOLDER_SEPARATOR.length();
        return shownPath.substring(nameStart);
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
