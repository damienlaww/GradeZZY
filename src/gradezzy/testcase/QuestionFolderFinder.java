/*
 * File: QuestionFolderFinder.java
 *
 * Purpose: Finds the folder of a question inside a working copy.
 *
 * Where it fits: Used by the install and compile stages and the checks. It
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
import gradezzy.util.FileUtils;

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
     * after the username, the only folder there is, or else the only
     * folder that holds a question folder (so an extra IDE folder such as
     * bin is ignored). This is the same rule as AbstractFixer's.
     *
     * @param submission the submission
     * @param assessment the assessment
     * @return the folder, or null if there is no single candidate
     * @throws IOException if the working copy cannot be listed
     */
    private static Path findContentRoot(Submission submission,
            Assessment assessment) throws IOException {
        Path workingCopy = submission.getWorkingCopyPath();
        boolean noWorkingCopy = workingCopy == null
                || !Files.isDirectory(workingCopy, LinkOption.NOFOLLOW_LINKS);
        if (noWorkingCopy) {
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
        return findOnlyHolder(topFolders, assessment);
    }

    // Returns the only folder that holds a question folder, or null.
    private static Path findOnlyHolder(List<Path> folders,
            Assessment assessment) throws IOException {
        List<Path> holders = new ArrayList<>();
        for (Path folder : folders) {
            if (holdsQuestionFolder(listFolders(folder), assessment)) {
                holders.add(folder);
            }
        }

        if (holders.size() != 1) {
            return null;
        }
        return holders.get(0);
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

        DirectoryStream<Path> children = null;
        try {
            children = Files.newDirectoryStream(directory);
            for (Path child : children) {
                String childName = child.getFileName().toString();
                boolean shownFolder = Files.isDirectory(child,
                        LinkOption.NOFOLLOW_LINKS)
                        && !childName.startsWith(HIDDEN_PREFIX);
                if (shownFolder) {
                    folders.add(child);
                }
            }
        } finally {
            FileUtils.closeIfOpen(children);
        }
        return folders;
    }
}
