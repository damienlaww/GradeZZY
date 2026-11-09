/*
 * File: ModelAnswerCopy.java
 *
 * Purpose: Makes a copy of one question's model answer that can be
 * compiled and run: the starter folder of the question (with its
 * provided .class files and data files), with the model answer's files
 * on top. Also holds the small helpers both model answer checks use.
 *
 * Where it fits: The tester strength check runs the tester on such a
 * copy and on broken copies of it; the random inputs check calls the
 * model answer's methods in one. The model answers are never changed.
 */
package gradezzy.modelanswer;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;

import gradezzy.config.AppConfig;
import gradezzy.config.ConfigKeys;
import gradezzy.model.Question;
import gradezzy.util.FileUtils;

/**
 * Makes a runnable copy of one question's model answer.
 *
 * <p>Tier: COULD. Owner: Jing Yuan.
 */
public final class ModelAnswerCopy {

    // How far below the maximum a mark may be and still be full marks,
    // since marks such as 0.5 are added as doubles
    private static final double FULL_MARKS_MARGIN = 0.0001;

    // Only static methods; never created
    private ModelAnswerCopy() {
    }

    /**
     * Returns the model answers folder from paths.model.answers.dir, or
     * null when the setting is empty or names no folder, which switches
     * the checks that use it off.
     *
     * <p>Tier: COULD.
     *
     * @param config the application settings
     * @return the folder, or null
     */
    public static Path findFolder(AppConfig config) {
        String setting = config.getString(ConfigKeys.MODEL_ANSWERS_DIR);
        if (setting.isEmpty()) {
            return null;
        }

        Path folder = Paths.get(setting);
        if (!Files.isDirectory(folder)) {
            return null;
        }
        return folder;
    }

    /**
     * Returns the model answer's file for a question, such as
     * model-answers/Q1/Q1.java, or null when it has none.
     *
     * <p>Tier: COULD.
     *
     * @param modelAnswers the model answers folder
     * @param question the question
     * @return the file, or null
     */
    public static Path findAnswerFile(Path modelAnswers, Question question) {
        Path file = modelAnswers.resolve(question.getFolderName())
                .resolve(question.getFileName());
        if (!Files.isRegularFile(file)) {
            return null;
        }
        return file;
    }

    /**
     * Makes a fresh copy of a question's model answer in a folder: the
     * starter's question folder first, then every file of the model
     * answer's question folder on top. Anything already in the folder is
     * deleted first. The two folders are the instructor's own, so they
     * are read through their real paths: a link in a folder above them,
     * which copying refuses, does not stop the check.
     *
     * <p>Tier: COULD.
     *
     * @param modelAnswers the model answers folder
     * @param starter the starter folder, or null when there is none
     * @param question the question
     * @param target the folder to make the copy in
     * @throws IOException if a file cannot be copied
     */
    public static void make(Path modelAnswers, Path starter,
            Question question, Path target) throws IOException {
        FileUtils.deleteDirectory(target);
        Files.createDirectories(target);

        if (starter != null) {
            Path starterFolder = starter.resolve(question.getFolderName());
            if (Files.isDirectory(starterFolder)) {
                FileUtils.copyDirectory(starterFolder.toRealPath(), target);
            }
        }

        Path answerFolder = modelAnswers.resolve(question.getFolderName());
        FileUtils.copyDirectory(answerFolder.toRealPath(), target);
    }

    /**
     * Reads the starter's copy of a file of a question.
     *
     * <p>Tier: COULD.
     *
     * @param starterDirectory the starter folder, or null when there is
     *        none
     * @param question the question whose folder holds the file
     * @param fileName the file's name, such as Q1a.java
     * @return the file's text, or "" when there is no such file
     * @throws IOException if the file cannot be read
     */
    public static String readStarter(Path starterDirectory,
            Question question, String fileName) throws IOException {
        if (starterDirectory == null) {
            return "";
        }
        Path starterFile = starterDirectory.resolve(question.getFolderName())
                .resolve(fileName);
        if (!Files.isRegularFile(starterFile)) {
            return "";
        }
        return FileUtils.readText(starterFile);
    }

    /**
     * Says whether a mark is the question's maximum.
     *
     * <p>Tier: COULD.
     *
     * @param score the mark
     * @param question the question
     * @return true for full marks
     */
    public static boolean isFullMarks(double score, Question question) {
        double lowestFull = question.getMaxScore() - FULL_MARKS_MARGIN;
        return score >= lowestFull;
    }

    /**
     * Deletes a folder a check made. One that cannot be deleted stays in
     * paths.working.dir.
     *
     * <p>Tier: COULD.
     *
     * @param folder the folder to delete
     */
    public static void deleteQuietly(Path folder) {
        try {
            FileUtils.deleteDirectory(folder);
        } catch (IOException exception) {
            System.err.println("Could not delete " + folder);
        }
    }
}
