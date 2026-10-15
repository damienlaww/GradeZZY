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

    // The lowest mark a question can have
    private static final double MIN_SCORE = 0.0;

    /**
     * Creates a new score calculator.
     */
    public ScoreCalculator() {
        // nothing to set up yet
    }

    /**
     * Returns the score of a question, kept between 0 and the question's
     * maximum. A tester that reports more than the maximum gets the
     * maximum; a negative score or one that is not a number gets 0.
     *
     * @param questionResult the question result
     * @return the marks for the question, or 0 if there is no result
     */
    public double questionScore(QuestionResult questionResult) {
        if (questionResult == null || questionResult.getQuestion() == null) {
            return MIN_SCORE;
        }

        double score = questionResult.getScore();
        double maxScore = questionResult.getQuestion().getMaxScore();

        // NaN fails every comparison, so it is checked first.
        if (Double.isNaN(score) || score < MIN_SCORE) {
            return MIN_SCORE;
        }

        if (score > maxScore) {
            return maxScore;
        }

        return score;
    }

    /**
     * Returns the total of a student over all questions, using the
     * limited score of each question.
     *
     * @param submission the submission
     * @return the student's total, or 0 if there is no submission
     */
    public double totalScore(Submission submission) {
        double total = MIN_SCORE;

        if (submission == null) {
            return total;
        }

        for (QuestionResult questionResult
                : submission.getQuestionResults()) {
            total += questionScore(questionResult);
        }

        return total;
    }

    /**
     * Returns the highest total possible for the assessment.
     *
     * @param assessment the assessment
     * @return the denominator for the score sheet, or 0 if there is no
     *         assessment
     */
    public double maxTotal(Assessment assessment) {
        if (assessment == null) {
            return MIN_SCORE;
        }

        return assessment.getMaxTotal();
    }
}
