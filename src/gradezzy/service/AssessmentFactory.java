/*
 * File: AssessmentFactory.java
 *
 * Purpose: Builds the Assessment (its questions, folders, files and
 * maximum scores) from config.properties.
 *
 * Where it fits: Called by GradingService at the start of a run. Because
 * the questions come from the configuration, a different lab test can be
 * graded by editing the file, with no code change.
 */
package gradezzy.service;

import java.util.List;

import gradezzy.config.AppConfig;
import gradezzy.config.ConfigException;
import gradezzy.config.ConfigKeys;
import gradezzy.model.Assessment;
import gradezzy.model.Question;

/**
 * Builds the Assessment described in config.properties.
 *
 * <p>Tier: MUST. Owner: Jing Yuan.
 */
public class AssessmentFactory {

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
     * Reads the assessment name, questions and placeholder folder names
     * from the configuration, for one module. Every module shares the
     * same questions.
     *
     * @param moduleCode the module, or null or blank for the default
     * @return the assessment to grade
     * @throws ConfigException if no module is given and app.module.codes
     *         lists none
     */
    public Assessment create(String moduleCode) {
        String chosenModule = moduleCode;

        if (chosenModule == null || chosenModule.trim().isEmpty()) {
            chosenModule = getDefaultModuleCode();
        }

        Assessment assessment = new Assessment(
                config.getString(ConfigKeys.ASSESSMENT_NAME),
                chosenModule.trim());

        for (String questionId : config.getList(ConfigKeys.QUESTION_IDS)) {
            assessment.addQuestion(createQuestion(questionId));
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

    // Reads the three settings of one question.
    private Question createQuestion(String questionId) {
        String prefix = ConfigKeys.QUESTION_PREFIX + questionId;
        String folderName = config.getString(prefix
                + ConfigKeys.QUESTION_FOLDER_SUFFIX);
        String fileName = config.getString(prefix
                + ConfigKeys.QUESTION_FILE_SUFFIX);
        double maxScore = config.getDouble(prefix
                + ConfigKeys.QUESTION_MAX_SCORE_SUFFIX);

        return new Question(questionId, folderName, fileName, maxScore);
    }
}
