/*
 * File: Assessment.java
 *
 * Purpose: The definition of one lab test or exam being graded.
 *
 * Where it fits: Shared by every stage of the pipeline so that nothing about
 * the questions is hard-coded.
 */
package gradezzy.model;

import java.util.ArrayList;
import java.util.List;

/**
 * The definition of one lab test or exam being graded.
 *
 * <p>Tier: MUST. Owner: Jing Yuan.
 */
public class Assessment {

    // The name of the assessment
    private final String name;

    // The course code, for example CS102
    private final String moduleCode;

    // The questions, in order
    private final List<Question> questions = new ArrayList<>();

    // The starter folder names students should have renamed
    private final List<String> placeholderFolderNames = new ArrayList<>();

    /**
     * Creates a new assessment.
     *
     * @param name the name of the assessment
     * @param moduleCode the course code, for example CS102
     */
    public Assessment(String name, String moduleCode) {
        this.name = name;
        this.moduleCode = moduleCode;
    }

    /**
     * Returns the name of the assessment.
     *
     * @return the name of the assessment
     */
    public String getName() {
        return name;
    }

    /**
     * Returns the course code, for example CS102.
     *
     * @return the course code, for example CS102
     */
    public String getModuleCode() {
        return moduleCode;
    }

    /**
     * Returns the questions, in order.
     *
     * @return the questions, in order
     */
    public List<Question> getQuestions() {
        return questions;
    }

    /**
     * Returns the starter folder names students should have renamed.
     *
     * @return the starter folder names students should have renamed
     */
    public List<String> getPlaceholderFolderNames() {
        return placeholderFolderNames;
    }

    /**
     * Adds a question to the end of the list.
     *
     * @param question the question
     */
    public void addQuestion(Question question) {
        questions.add(question);
    }

    /**
     * Finds a question by its id.
     *
     * @param questionId the question id
     * @return the matching question, or null if there is none
     */
    public Question findQuestion(String questionId) {
        // TODO(Jing Yuan): implement: a search of the questions list by id
        return null;
    }

    /**
     * Adds up the maximum score of every question.
     *
     * @return the highest total a student can get
     */
    public double getMaxTotal() {
        // TODO(Jing Yuan): implement: the sum of maxScore over all questions
        return 0.0;
    }
}
