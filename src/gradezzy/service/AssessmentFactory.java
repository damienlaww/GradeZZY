/*
 * File: AssessmentFactory.java
 *
 * Purpose: Builds the Assessment: its questions, folders and files from
 * config.properties, and each question's maximum score from its tester.
 *
 * Where it fits: Called by GradingService at the start of a run. Because
 * the questions come from the configuration and the marks from the
 * testers, a different lab test can be graded with no code change.
 */
package gradezzy.service;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.util.ArrayList;
import java.util.List;

import gradezzy.config.AppConfig;
import gradezzy.config.ConfigException;
import gradezzy.config.ConfigKeys;
import gradezzy.model.Assessment;
import gradezzy.model.Question;
import gradezzy.testcase.TesterMarks;

/**
 * Builds the Assessment described in config.properties, with the maximum
 * scores read from the tester files.
 *
 * <p>Tier: MUST. Owner: Jing Yuan.
 */
public class AssessmentFactory {

    // The file ending of a tester's Java source
    private static final String JAVA_EXTENSION = ".java";

    // The end of the old setting that held a question's maximum score
    private static final String OLD_MAX_SCORE_SUFFIX = ".max.score";

    // The application settings
    private final AppConfig config;

    /**
     * Creates a new assessment factory.
     *
     * <p>Tier: MUST.
     *
     * @param config the application settings
     */
    public AssessmentFactory(AppConfig config) {
        this.config = config;
    }

    /**
     * Reads the assessment for the default module, the first one in
     * app.module.codes.
     *
     * <p>Tier: MUST.
     *
     * @return the assessment to grade
     * @throws ConfigException if app.module.codes lists no module
     */
    public Assessment create() {
        return create(getDefaultModuleCode());
    }

    /**
     * Reads the assessment for one module, with the maximum scores taken
     * from the tester folder in the settings (paths.testers.dir).
     *
     * <p>Tier: MUST.
     *
     * @param moduleCode the module, or null or blank for the default
     * @return the assessment to grade
     * @throws ConfigException if no module is given and app.module.codes
     *         lists none
     */
    public Assessment create(String moduleCode) {
        return create(moduleCode, null);
    }

    /**
     * Reads the assessment name, questions and placeholder folder names
     * from the configuration, for one module. Every module shares the
     * same questions. Each question's maximum score is the sum of the
     * {@code score += <number>} lines of its tester in testersDirectory;
     * a question with no tester, or whose tester has no such line, gets
     * 0 (see findMaxScoreProblems).
     *
     * <p>Tier: MUST.
     *
     * @param moduleCode the module, or null or blank for the default
     * @param testersDirectory the batch's tester folder, or null for the
     *        one in the settings
     * @return the assessment to grade
     * @throws ConfigException if no module is given and app.module.codes
     *         lists none
     */
    public Assessment create(String moduleCode, Path testersDirectory) {
        String chosenModule = moduleCode;
        Path testers = chooseTesters(testersDirectory);

        if (chosenModule == null || chosenModule.trim().isEmpty()) {
            chosenModule = getDefaultModuleCode();
        }

        Assessment assessment = new Assessment(
                config.getString(ConfigKeys.ASSESSMENT_NAME),
                chosenModule.trim());

        for (String questionId : config.getList(ConfigKeys.QUESTION_IDS)) {
            assessment.addQuestion(createQuestion(questionId, testers));
        }

        assessment.getPlaceholderFolderNames().addAll(
                config.getList(ConfigKeys.PLACEHOLDER_FOLDER_NAMES));

        return assessment;
    }

    /**
     * Returns the first module in app.module.codes.
     *
     * <p>Tier: MUST.
     *
     * @return the default module code
     * @throws ConfigException if app.module.codes lists no module
     */
    public String getDefaultModuleCode() {
        List<String> moduleCodes = config.getList(ConfigKeys.MODULE_CODES);

        if (moduleCodes.isEmpty()) {
            throw new ConfigException("Setting '" + ConfigKeys.MODULE_CODES
                    + "' lists no module.");
        }

        return moduleCodes.get(0);
    }

    /**
     * Lists why some questions have no maximum mark: their tester is not
     * in testersDirectory, cannot be read, never adds to the score, or
     * changes the score in a way TesterMarks cannot count. Grading must
     * not start while this list is not empty.
     *
     * <p>Tier: MUST.
     *
     * @param testersDirectory the batch's tester folder, or null for the
     *        one in the settings
     * @return one sentence per problem, empty when every maximum is known
     */
    public List<String> findMaxScoreProblems(Path testersDirectory) {
        List<String> problems = new ArrayList<>();
        Path testers = chooseTesters(testersDirectory);

        for (String questionId : config.getList(ConfigKeys.QUESTION_IDS)) {
            Path testerFile = findTesterFile(questionId, testers);
            if (!Files.isRegularFile(testerFile)) {
                problems.add(questionId + " has no tester ("
                        + testerFile.getFileName() + " is not in "
                        + testers + "), so it has no maximum mark.");
            } else {
                addTesterProblems(testerFile, questionId, problems);
            }
        }
        return problems;
    }

    /**
     * Lists the old max.score settings that are still in the configuration.
     * They are no longer used, because each maximum now comes from the
     * question's tester, so the instructor is told they are ignored.
     *
     * <p>Tier: MUST.
     *
     * @return the names of the ignored settings, empty when there are none
     */
    public List<String> findIgnoredMaxScoreSettings() {
        List<String> ignored = new ArrayList<>();
        for (String questionId : config.getList(ConfigKeys.QUESTION_IDS)) {
            String key = ConfigKeys.QUESTION_PREFIX + questionId
                    + OLD_MAX_SCORE_SUFFIX;
            if (config.hasKey(key)) {
                ignored.add(key);
            }
        }
        return ignored;
    }

    // Adds what TesterMarks found wrong with one tester
    private void addTesterProblems(Path testerFile, String questionId,
            List<String> problems) {
        try {
            problems.addAll(TesterMarks.findProblems(testerFile));
        } catch (IOException exception) {
            problems.add(testerFile.getFileName() + " could not be read, "
                    + "so " + questionId + " has no maximum mark: "
                    + exception.getMessage());
        }
    }

    // Reads the folder and file of one question, and its maximum score
    // from its tester.
    private Question createQuestion(String questionId, Path testers) {
        String prefix = ConfigKeys.QUESTION_PREFIX + questionId;
        String folderKey = prefix + ConfigKeys.QUESTION_FOLDER_SUFFIX;
        String fileKey = prefix + ConfigKeys.QUESTION_FILE_SUFFIX;
        String folderName = config.getString(folderKey);
        String fileName = config.getString(fileKey);

        double maxScore = TesterMarks.UNKNOWN;
        Path testerFile = findTesterFile(questionId, testers);
        if (Files.isRegularFile(testerFile)) {
            maxScore = readMaxScore(testerFile);
        }

        return new Question(questionId, folderName, fileName, maxScore);
    }

    // The batch's tester folder, or the one in the settings
    private Path chooseTesters(Path testersDirectory) {
        if (testersDirectory != null) {
            return testersDirectory;
        }
        return Paths.get(config.getString(ConfigKeys.TESTERS_DIR));
    }

    // Where the tester of a question is, for example Q1aTester.java
    private Path findTesterFile(String questionId, Path testers) {
        String testerName = questionId
                + config.getString(ConfigKeys.TESTER_CLASS_SUFFIX)
                + JAVA_EXTENSION;
        return testers.resolve(testerName);
    }

    // The tester's maximum, or UNKNOWN if the file cannot be read
    private double readMaxScore(Path testerFile) {
        try {
            return TesterMarks.readMaxScore(testerFile);
        } catch (IOException exception) {
            return TesterMarks.UNKNOWN;
        }
    }
}
