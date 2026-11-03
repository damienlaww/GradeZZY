/*
 * File: ScoreCalculator.java
 *
 * Purpose: Works out the mark of each question, kept between 0 and the
 * question's maximum.
 *
 * Where it fits: Used by ScoreStage.
 */
package gradezzy.grading;

import gradezzy.model.QuestionResult;

/**
 * Works out the mark of each question.
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
}
