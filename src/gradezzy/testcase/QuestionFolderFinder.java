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
import gradezzy.util.AnswerFolderRule;
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
     * Finds the folder that holds the question folders by the rule the
     * checks and the fixes share (AnswerFolderRule): null when several
     * folders hold question folders, so such a student is never graded
     * from a guess.
     *
     * @param submission the submission
     * @param assessment the assessment
     * @return the folder, or null if there is no single candidate
     * @throws IOException if the working copy cannot be listed
     */
    private static Path findContentRoot(Submission submission,
            Assessment assessment) throws IOException {
        return AnswerFolderRule.findContentRoot(
                submission.getWorkingCopyPath(), submission.getUsername(),
                listFolderNames(assessment));
    }

    // Lists the question folder names, each once, in question order.
    private static List<String> listFolderNames(Assessment assessment) {
        List<String> folderNames = new ArrayList<>();
        for (Question question : assessment.getQuestions()) {
            if (!folderNames.contains(question.getFolderName())) {
                folderNames.add(question.getFolderName());
            }
        }
        return folderNames;
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
