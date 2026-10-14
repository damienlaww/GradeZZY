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

import gradezzy.config.AppConfig;
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
     * Reads the assessment name, module, questions and placeholder folder
     * names from the configuration.
     *
     * @return the assessment to grade
     */
    public Assessment create() {
        Assessment assessment = new Assessment(
                config.getString(ConfigKeys.ASSESSMENT_NAME),
                config.getString(ConfigKeys.MODULE_CODE));

        for (String questionId : config.getList(ConfigKeys.QUESTION_IDS)) {
            assessment.addQuestion(createQuestion(questionId));
        }

        assessment.getPlaceholderFolderNames().addAll(
                config.getList(ConfigKeys.PLACEHOLDER_FOLDER_NAMES));

        return assessment;
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
