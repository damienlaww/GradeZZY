/*
 * File: AbstractValidator.java
 *
 * Purpose: Shared code for all validators.
 *
 * Where it fits: Holds the name and the helper that builds an Anomaly, so each
 * concrete validator only contains its own rule.
 */
package gradezzy.validation;

import java.io.IOException;
import java.nio.file.DirectoryStream;
import java.nio.file.Files;
import java.nio.file.LinkOption;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.Collections;
import java.util.List;
import java.util.UUID;

import gradezzy.model.Anomaly;
import gradezzy.model.AnomalyType;
import gradezzy.model.Assessment;
import gradezzy.model.Question;
import gradezzy.model.Severity;
import gradezzy.model.Submission;
import gradezzy.util.FileUtils;

/**
 * Shared code for all validators.
 */
public abstract class AbstractValidator implements Validator {

    // The text in front of the id of every anomaly a validator reports
    private static final String ID_PREFIX = "validation-";

    // The start of a hidden name, such as .idea, which is never a folder
    // of the submission itself
    private static final String HIDDEN_PREFIX = ".";

    // The suggested actions, one for each type a validator reports
    private static final String WRAPPER_ACTION =
            "Approve the repair if the new username folder is right.";
    private static final String FOLDER_NAME_ACTION =
            "Approve the rename to the username, or reject it.";
    private static final String QUESTION_FOLDER_ACTION =
            "Ask for a corrected submission, or keep the questions in "
            + "that folder at 0.";
    private static final String MISSING_FILE_ACTION =
            "Ask for a corrected submission, or keep the question at 0.";
    private static final String FILE_NAME_ACTION =
            "Rename the file by hand. No automatic repair exists for file "
            + "names.";
    private static final String HEADER_ACTION =
            "Approve the name taken from the roster, or reject it.";
    private static final String UNREADABLE_ACTION =
            "Check that the working copy can be read, then grade again.";
    private static final String DEFAULT_ACTION =
            "Open the working copy and check the submission by hand.";

    // The name shown in the anomaly log
    private final String name;

    /**
     * Creates a new abstract validator.
     *
     * @param name the name shown in the anomaly log
     */
    protected AbstractValidator(String name) {
        this.name = name;
    }

    /**
     * Returns the name of this check.
     *
     * @return the name of this check
     */
    @Override
    public String getName() {
        return name;
    }

    /**
     * Builds an anomaly for the given submission. The id is random, so it
     * stays unique across runs, and the suggested action is chosen from the
     * type.
     *
     * @param submission the submission
     * @param type the type
     * @param severity the severity
     * @param message the message
     * @return the new anomaly
     */
    protected Anomaly createAnomaly(Submission submission, AnomalyType type,
            Severity severity, String message) {
        String id = ID_PREFIX + UUID.randomUUID();

        return new Anomaly(id, type, severity, submission.getUsername(),
                message, suggestActionFor(type));
    }

    /**
     * Builds the list returned when the working copy could not be read. It
     * holds one INTERNAL_ERROR anomaly, because the fault is in reading the
     * files, not in what the student submitted.
     *
     * @param submission the submission
     * @param exception the error raised while reading
     * @return a list holding the one anomaly
     */
    protected List<Anomaly> reportUnreadable(Submission submission,
            IOException exception) {
        List<Anomaly> anomalies = new ArrayList<>();

        anomalies.add(createAnomaly(submission, AnomalyType.INTERNAL_ERROR,
                Severity.ERROR, "The check '" + name + "' could not read "
                + "the working copy: " + exception.getMessage()));

        return anomalies;
    }

    /**
     * Finds the folder that should hold the question folders. Checks run
     * before any fix, so this is wherever they really are: the top of the
     * working copy when the username folder is missing, the folder named
     * after the username, or the only folder there is.
     *
     * @param submission the submission
     * @param assessment the assessment
     * @return the folder, or null if there is no single candidate
     * @throws IOException if the working copy cannot be listed
     */
    protected Path findContentRoot(Submission submission,
            Assessment assessment) throws IOException {
        Path workingCopy = submission.getWorkingCopyPath();

        if (workingCopy == null
                || !Files.isDirectory(workingCopy, LinkOption.NOFOLLOW_LINKS)) {
            return null;
        }

        List<Path> topFolders = listFolders(workingCopy);
        Path usernameFolder = findExact(topFolders,
                submission.getUsername());

        if (holdsQuestionFolder(topFolders, assessment)) {
            return workingCopy;
        } else if (usernameFolder != null) {
            return usernameFolder;
        } else if (topFolders.size() == 1) {
            return topFolders.get(0);
        }

        return null;
    }

    /**
     * Returns the folder names of the assessment, each once, in question
     * order. Q1a and Q1b share Q1, so Q1 appears once.
     *
     * @param assessment the assessment
     * @return the question folder names
     */
    protected List<String> getQuestionFolderNames(Assessment assessment) {
        List<String> folderNames = new ArrayList<>();

        for (Question question : assessment.getQuestions()) {
            if (!folderNames.contains(question.getFolderName())) {
                folderNames.add(question.getFolderName());
            }
        }

        return folderNames;
    }

    /**
     * Lists the folders directly inside a folder, in name order. Hidden
     * folders and symbolic links are left out.
     *
     * @param directory the folder to list
     * @return the folders found
     * @throws IOException if the folder cannot be listed
     */
    protected List<Path> listFolders(Path directory) throws IOException {
        List<Path> folders = new ArrayList<>();

        DirectoryStream<Path> children = null;

        try {
            children = Files.newDirectoryStream(directory);
            for (Path child : children) {
                String childName = child.getFileName().toString();

                if (Files.isDirectory(child, LinkOption.NOFOLLOW_LINKS)
                        && !childName.startsWith(HIDDEN_PREFIX)) {
                    folders.add(child);
                }
            }
        } finally {
            FileUtils.closeIfOpen(children);
        }

        Collections.sort(folders);
        return folders;
    }

    /**
     * Lists the ordinary files directly inside a folder, in name order.
     *
     * @param directory the folder to list
     * @return the files found
     * @throws IOException if the folder cannot be listed
     */
    protected List<Path> listFiles(Path directory) throws IOException {
        List<Path> files = new ArrayList<>();

        DirectoryStream<Path> children = null;

        try {
            children = Files.newDirectoryStream(directory);
            for (Path child : children) {
                if (Files.isRegularFile(child, LinkOption.NOFOLLOW_LINKS)) {
                    files.add(child);
                }
            }
        } finally {
            FileUtils.closeIfOpen(children);
        }

        Collections.sort(files);
        return files;
    }

    /**
     * Finds the path whose name matches exactly, including case. The names
     * are compared as text because Windows would report Q1a.java as
     * existing when only q1a.java is there.
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
     * Finds the folder of a question, matched exactly, inside the content
     * root.
     *
     * @param contentRoot the folder that holds the question folders
     * @param question the question
     * @return the question folder, or null if it is missing
     * @throws IOException if the content root cannot be listed
     */
    protected Path findQuestionFolder(Path contentRoot,
            Question question) throws IOException {
        return findExact(listFolders(contentRoot), question.getFolderName());
    }

    /**
     * Says whether any of the folders is a question folder, ignoring case.
     * At the top of the working copy this means the student zipped the
     * question folders directly.
     *
     * @param folders the folders to search
     * @param assessment the assessment
     * @return true if one of them is named like a question folder
     */
    protected boolean holdsQuestionFolder(List<Path> folders,
            Assessment assessment) {
        for (String folderName : getQuestionFolderNames(assessment)) {
            if (findIgnoringCase(folders, folderName) != null) {
                return true;
            }
        }

        return false;
    }

    // Chooses what the instructor can do about each type of problem.
    private String suggestActionFor(AnomalyType type) {
        if (type == AnomalyType.MISSING_WRAPPER_FOLDER) {
            return WRAPPER_ACTION;
        } else if (type == AnomalyType.WRONG_FOLDER_NAME) {
            return FOLDER_NAME_ACTION;
        } else if (type == AnomalyType.MISSING_QUESTION_FOLDER) {
            return QUESTION_FOLDER_ACTION;
        } else if (type == AnomalyType.MISSING_FILE) {
            return MISSING_FILE_ACTION;
        } else if (type == AnomalyType.WRONG_FILE_NAME) {
            return FILE_NAME_ACTION;
        } else if (type == AnomalyType.MISSING_HEADER_NAME) {
            return HEADER_ACTION;
        } else if (type == AnomalyType.INTERNAL_ERROR) {
            return UNREADABLE_ACTION;
        }

        return DEFAULT_ACTION;
    }
}
