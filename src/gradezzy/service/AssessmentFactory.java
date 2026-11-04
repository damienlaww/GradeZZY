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

    private static final String JAVA_EXTENSION = ".java";

    // The application settings
    private final AppConfig config;

    /**
     * Creates a new assessment factory.
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
     * Lists the questions whose tester is in testersDirectory but has no
     * {@code score += <number>} line, so their maximum score is unknown.
     * Grading must not start while this list is not empty. A question
     * with no tester at all is not listed; the tester check reports it.
     *
     * @param testersDirectory the batch's tester folder, or null for the
     *        one in the settings
     * @return one sentence per problem, empty when every tester is fine
     */
    public List<String> findMaxScoreProblems(Path testersDirectory) {
        List<String> problems = new ArrayList<>();
        Path testers = chooseTesters(testersDirectory);

        for (String questionId : config.getList(ConfigKeys.QUESTION_IDS)) {
            Path testerFile = findTesterFile(questionId, testers);
            if (Files.isRegularFile(testerFile)
                    && readMaxScore(testerFile) <= TesterMarks.UNKNOWN) {
                problems.add(testerFile.getFileName() + " has no \"score "
                        + "+= <number>\" line, so " + questionId
                        + " has no maximum mark. Add one per test case.");
            }
        }
        return problems;
    }

    // Reads the folder and file of one question, and its maximum score
    // from its tester.
    private Question createQuestion(String questionId, Path testers) {
        String prefix = ConfigKeys.QUESTION_PREFIX + questionId;
        String folderName = config.getString(prefix
                + ConfigKeys.QUESTION_FOLDER_SUFFIX);
        String fileName = config.getString(prefix
                + ConfigKeys.QUESTION_FILE_SUFFIX);

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
        return testers.resolve(questionId
                + config.getString(ConfigKeys.TESTER_CLASS_SUFFIX)
                + JAVA_EXTENSION);
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
