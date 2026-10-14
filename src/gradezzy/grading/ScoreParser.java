/*
 * File: ScoreParser.java
 *
 * Purpose: Turns the output of a tester run into a QuestionResult.
 *
 * Where it fits: Separates how a tester prints its score from the rest of
 * grading, so a different tester format needs only a new parser.
 */
package gradezzy.grading;

import gradezzy.exec.ProcessResult;
import gradezzy.model.Question;
import gradezzy.model.QuestionResult;

/**
 * Turns the output of a tester run into a QuestionResult.
 *
 * <p>Tier: MUST. Owner: Zach.
 */
public interface ScoreParser {

    /**
     * Reads the tester output for one question.
     *
     * @param question the question
     * @param processResult the process result
     * @return the score and the result of each test case
     */
    QuestionResult parse(Question question, ProcessResult processResult);
}
