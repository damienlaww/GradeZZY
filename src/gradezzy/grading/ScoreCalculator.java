/*
 * File: ScoreCalculator.java
 *
 * Purpose: Adds up scores per question and per student.
 *
 * Where it fits: Used by ScoreStage, the exporters and the statistics.
 */
package gradezzy.grading;

import gradezzy.model.Assessment;
import gradezzy.model.QuestionResult;
import gradezzy.model.Submission;

/**
 * Adds up scores per question and per student.
 *
 * <p>Tier: MUST. Owner: Jing Yuan.
 */
public class ScoreCalculator {

    /**
     * Creates a new score calculator.
     */
    public ScoreCalculator() {
        // nothing to set up yet
    }

    /**
     * Returns the score of a question, never above its maximum.
     *
     * @param questionResult the question result
     * @return the marks for the question
     */
    public double questionScore(QuestionResult questionResult) {
        // TODO(Jing Yuan): implement: limiting the score to
        //     question.getMaxScore()
        return 0.0;
    }

    /**
     * Returns the total of a student over all questions.
     *
     * @param submission the submission
     * @return the student's total
     */
    public double totalScore(Submission submission) {
        // TODO(Jing Yuan): implement: the sum of questionScore over the results
        return 0.0;
    }

    /**
     * Returns the highest total possible for the assessment.
     *
     * @param assessment the assessment
     * @return the denominator for the score sheet
     */
    public double maxTotal(Assessment assessment) {
        // TODO(Jing Yuan): implement: the sum of the maximum score of every
        //     question
        return 0.0;
    }
}
