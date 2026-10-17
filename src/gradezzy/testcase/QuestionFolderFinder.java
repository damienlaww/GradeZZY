/*
 * File: QuestionFolderFinder.java
 *
 * Purpose: Finds the folder of a question inside a working copy.
 *
 * Where it fits: Used by the install, compile and execute stages. It
 * follows the same rules as the validators, so a question the validators
 * report as missing is also skipped here instead of being reported twice.
 */
package gradezzy.testcase;

import java.io.IOException;
import java.nio.file.DirectoryStream;
import java.nio.file.Files;
import java.nio.file.LinkOption;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.List;

import gradezzy.model.Assessment;
import gradezzy.model.Question;
import gradezzy.model.Submission;

/**
 * Finds the folder of a question inside a working copy, whether or not the
 * folder fixes have been applied.
 *
 * <p>Tier: MUST. Owner: Zach.
 */
public class QuestionFolderFinder {

    // Folders whose name starts with this are hidden and skipped
    private static final String HIDDEN_PREFIX = ".";

    /**
     * Finds the folder of a question, matched exactly, including case.
     *
     * @param submission the submission
     * @param assessment the assessment
     * @param question the question
     * @return the question folder, or null if it is missing
     * @throws IOException if the working copy cannot be listed
     */
    public static Path find(Submission submission, Assessment assessment,
            Question question) throws IOException {
        Path contentRoot = findContentRoot(submission, assessment);
        if (contentRoot == null) {
            return null;
        }

        return findByName(listFolders(contentRoot), question.getFolderName());
    }

    /**
     * Finds the folder that holds the question folders: the top of the
     * working copy when the username folder is missing, the folder named
     * after the username, or the only folder there is.
     *
     * @param submission the submission
     * @param assessment the assessment
     * @return the folder, or null if there is no single candidate
     * @throws IOException if the working copy cannot be listed
     */
    private static Path findContentRoot(Submission submission,
            Assessment assessment) throws IOException {
        Path workingCopy = submission.getWorkingCopyPath();
        if (workingCopy == null
                || !Files.isDirectory(workingCopy, LinkOption.NOFOLLOW_LINKS)) {
            return null;
        }

        List<Path> topFolders = listFolders(workingCopy);
        Path usernameFolder = findByName(topFolders, submission.getUsername());

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
     * Says whether any of the folders is named like a question folder,
     * ignoring case, as the validators do.
     *
     * @param folders the folders to search
     * @param assessment the assessment
     * @return true if one of them is a question folder
     */
    private static boolean holdsQuestionFolder(List<Path> folders,
            Assessment assessment) {
        for (Question question : assessment.getQuestions()) {
            for (Path folder : folders) {
                String folderName = folder.getFileName().toString();
                if (folderName.equalsIgnoreCase(question.getFolderName())) {
                    return true;
                }
            }
        }
        return false;
    }

    /**
     * Finds the path whose name matches exactly. Names are compared as text
     * because macOS and Windows would also accept q1 when asked for Q1.
     *
     * @param paths the paths to search
     * @param wantedName the name to find
     * @return the matching path, or null if there is none
     */
    private static Path findByName(List<Path> paths, String wantedName) {
        for (Path path : paths) {
            if (path.getFileName().toString().equals(wantedName)) {
                return path;
            }
        }
        return null;
    }

    /**
     * Lists the folders directly inside a folder, leaving out hidden folders
     * and links.
     *
     * @param directory the folder to list
     * @return the folders found
     * @throws IOException if the folder cannot be listed
     */
    private static List<Path> listFolders(Path directory) throws IOException {
        List<Path> folders = new ArrayList<>();

        try (DirectoryStream<Path> children =
                Files.newDirectoryStream(directory)) {
            for (Path child : children) {
                String childName = child.getFileName().toString();
                if (Files.isDirectory(child, LinkOption.NOFOLLOW_LINKS)
                        && !childName.startsWith(HIDDEN_PREFIX)) {
                    folders.add(child);
                }
            }
        }
        return folders;
    }
}
