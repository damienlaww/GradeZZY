/*
 * File: ScoreStage.java
 *
 * Purpose: Works out the per-question and per-student scores.
 *
 * Where it fits: Eighth stage. After it, the submission has its final marks.
 */
package gradezzy.grading;

import java.util.UUID;

import gradezzy.config.ConfigKeys;
import gradezzy.model.Anomaly;
import gradezzy.model.AnomalyType;
import gradezzy.model.Assessment;
import gradezzy.model.Question;
import gradezzy.model.QuestionResult;
import gradezzy.model.Severity;
import gradezzy.model.Submission;
import gradezzy.testcase.TesterFile;

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

    // Start of the id of the flag for a question that has no tester
    private static final String NO_TESTER_ID_PREFIX = "no-tester-";

    // Added to a tester class name to give its file name
    private static final String JAVA_EXTENSION = ".java";

    // The helper that adds up scores
    private final ScoreCalculator scoreCalculator;

    /**
     * Creates a new score stage.
     *
     * <p>Tier: MUST.
     *
     * @param scoreCalculator the helper that adds up scores
     */
    public ScoreStage(ScoreCalculator scoreCalculator) {
        this.scoreCalculator = scoreCalculator;
    }

    /**
     * Returns the name of this stage.
     *
     * <p>Tier: MUST.
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
     * <p>A question that has no tester at all gets an error flag on every
     * submission, because its zero marks are not the student's doing and
     * would otherwise go into the export unnoticed. A question worth 0,
     * such as one with no typed test case, gets no flag: by design it has
     * no tester and adds nothing to any total.
     *
     * <p>Tier: MUST.
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
            boolean noTester = TesterFile.findFor(context.getTesters(),
                    question.getId()) == null;
            boolean worthMarks = question.getMaxScore() > 0;
            if (noTester && worthMarks) {
                flagMissingTester(submission, question, context);
            }

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

    // Adds an error flag saying that a question was not graded because
    // the tester folder has no tester for it.
    private void flagMissingTester(Submission submission, Question question,
            GradingContext context) {
        String testerFile = question.getId()
                + context.getConfig().getString(
                        ConfigKeys.TESTER_CLASS_SUFFIX)
                + JAVA_EXTENSION;
        String message = question.getId() + " was not graded: the tester "
                + "folder has no " + testerFile + ", so it scores 0 for "
                + "every student. This is not the student's error.";
        String action = "Add " + testerFile + " to the tester folder, then "
                + "grade again.";

        String id = NO_TESTER_ID_PREFIX + UUID.randomUUID();
        submission.addAnomaly(new Anomaly(id, AnomalyType.INTERNAL_ERROR,
                Severity.ERROR, submission.getUsername(), message, action));
    }

    // Says whether the submission already has a result for a question.
    private boolean hasResult(Submission submission, Question question) {
        return submission.findResult(question.getId()) != null;
    }
}
