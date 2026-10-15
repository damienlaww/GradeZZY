/*
 * File: ScoreStage.java
 *
 * Purpose: Works out the per-question and per-student scores.
 *
 * Where it fits: Eighth stage. After it, the submission has its final marks.
 */
package gradezzy.grading;

import gradezzy.model.Assessment;
import gradezzy.model.Question;
import gradezzy.model.QuestionResult;
import gradezzy.model.Submission;

/**
 * Works out the per-question and per-student scores.
 *
 * <p>Tier: MUST. Owner: Jing Yuan.
 */
public class ScoreStage implements GradingStage {

    /**
     * The name shown while this stage runs.
     */
    public static final String NAME = "Scoring";

    // The helper that adds up scores
    private final ScoreCalculator scoreCalculator;

    /**
     * Creates a new score stage.
     *
     * @param scoreCalculator the helper that adds up scores
     */
    public ScoreStage(ScoreCalculator scoreCalculator) {
        this.scoreCalculator = scoreCalculator;
    }

    /**
     * Returns the name of this stage.
     *
     * @return the name of this stage
     */
    @Override
    public String getName() {
        return NAME;
    }

    /**
     * Works out the per-question and per-student scores. A question with
     * no result, for example because its file was missing, gets a result
     * with zero marks, so every question appears in the per-question
     * results. Every score is then kept between 0 and its question's
     * maximum; a tester that reports more than the maximum is clamped
     * without a warning.
     *
     * @param submission the submission
     * @param context the context
     * @throws GradingException if the context holds no assessment
     */
    @Override
    public void process(Submission submission,
            GradingContext context) throws GradingException {
        Assessment assessment = context.getAssessment();

        if (assessment == null) {
            throw new GradingException("No assessment to score against.");
        }

        for (Question question : assessment.getQuestions()) {
            if (!hasResult(submission, question)) {
                // A question with no result (for example a missing file)
                // still appears in the results, with zero marks.
                submission.addQuestionResult(new QuestionResult(question));
            }
        }

        for (QuestionResult questionResult
                : submission.getQuestionResults()) {
            questionResult.setScore(
                    scoreCalculator.questionScore(questionResult));
        }
    }

    // Says whether the submission already has a result for a question.
    private boolean hasResult(Submission submission, Question question) {
        for (QuestionResult questionResult
                : submission.getQuestionResults()) {
            Question graded = questionResult.getQuestion();

            if (graded != null && graded.getId().equals(question.getId())) {
                return true;
            }
        }

        return false;
    }
}
