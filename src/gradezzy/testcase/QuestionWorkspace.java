/*
 * File: QuestionWorkspace.java
 *
 * Purpose: Gives each question its own copy of its folder to compile and
 * run in.
 *
 * Where it fits: Used by the compile and execute stages. Q1a and Q1b share
 * the folder Q1, so without a copy the code of one question could replace
 * the other's tester or compiled classes.
 */
package gradezzy.testcase;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.LinkOption;
import java.nio.file.Path;

import gradezzy.model.Assessment;
import gradezzy.model.Question;
import gradezzy.model.Submission;
import gradezzy.util.FileUtils;

/**
 * Makes and finds the copy of a question folder that one question is
 * compiled and run in, at {@code <working copy>/.questions/<question id>}.
 *
 * <p>Tier: MUST. Owner: Zach.
 */
public class QuestionWorkspace {

    // Holds the copies. Its name starts with a dot, so QuestionFolderFinder
    // never takes it for a student folder.
    private static final String COPIES_FOLDER = ".questions";

    /**
     * Makes a fresh copy of the folder of a question, replacing any copy
     * left by an earlier run.
     *
     * @param submission the submission
     * @param assessment the assessment
     * @param question the question
     * @return the copy, or null if the question folder is missing
     * @throws IOException if the folder cannot be copied
     */
    public static Path prepare(Submission submission, Assessment assessment,
            Question question) throws IOException {
        Path questionFolder = QuestionFolderFinder.find(submission,
                assessment, question);
        if (questionFolder == null) {
            return null;
        }

        Path copy = copyOf(submission, question);
        FileUtils.deleteDirectory(copy);
        FileUtils.copyDirectory(questionFolder, copy);
        return copy;
    }

    /**
     * Finds the copy that prepare made for a question.
     *
     * @param submission the submission
     * @param question the question
     * @return the copy, or null if there is none
     */
    public static Path find(Submission submission, Question question) {
        if (submission.getWorkingCopyPath() == null) {
            return null;
        }

        Path copy = copyOf(submission, question);
        if (!Files.isDirectory(copy, LinkOption.NOFOLLOW_LINKS)) {
            return null;
        }
        return copy;
    }

    /**
     * Returns where the copy of a question folder goes.
     *
     * @param submission the submission
     * @param question the question
     * @return the path of the copy
     */
    private static Path copyOf(Submission submission, Question question) {
        return submission.getWorkingCopyPath().resolve(COPIES_FOLDER)
                .resolve(question.getId());
    }
}
