/*
 * File: QuestionResult.java
 *
 * Purpose: The result of one question for one student.
 *
 * Where it fits: Filled in by the compile, execute and score stages and read by
 * the exporters and statistics.
 */
package gradezzy.model;

import java.util.ArrayList;
import java.util.List;

/**
 * The result of one question for one student.
 *
 * <p>Tier: MUST. Owner: Jing Yuan.
 */
public class QuestionResult {

    // The question that was graded
    private final Question question;

    // The marks awarded
    private double score = 0.0;

    // Whether the code compiled
    private boolean compiled = false;

    // Whether the tester was stopped at the time limit
    private boolean timedOut = false;

    // The captured compiler or tester output
    private String output = "";

    // The result of each test case
    private final List<TestCaseResult> testCaseResults = new ArrayList<>();

    /**
     * Creates a new question result.
     *
     * @param question the question that was graded
     */
    public QuestionResult(Question question) {
        this.question = question;
    }

    /**
     * Returns the question that was graded.
     *
     * @return the question that was graded
     */
    public Question getQuestion() {
        return question;
    }

    /**
     * Returns the marks awarded.
     *
     * @return the marks awarded
     */
    public double getScore() {
        return score;
    }

    /**
     * Sets the marks awarded.
     *
     * @param score the marks awarded
     */
    public void setScore(double score) {
        this.score = score;
    }

    /**
     * Returns whether the code compiled.
     *
     * @return whether the code compiled
     */
    public boolean isCompiled() {
        return compiled;
    }

    /**
     * Sets whether the code compiled.
     *
     * @param compiled whether the code compiled
     */
    public void setCompiled(boolean compiled) {
        this.compiled = compiled;
    }

    /**
     * Returns whether the tester was stopped at the time limit.
     *
     * @return whether the tester was stopped at the time limit
     */
    public boolean isTimedOut() {
        return timedOut;
    }

    /**
     * Sets whether the tester was stopped at the time limit.
     *
     * @param timedOut whether the tester was stopped at the time limit
     */
    public void setTimedOut(boolean timedOut) {
        this.timedOut = timedOut;
    }

    /**
     * Returns the captured compiler or tester output.
     *
     * @return the captured compiler or tester output
     */
    public String getOutput() {
        return output;
    }

    /**
     * Sets the captured compiler or tester output.
     *
     * @param output the captured compiler or tester output
     */
    public void setOutput(String output) {
        this.output = output;
    }

    /**
     * Returns the result of each test case.
     *
     * @return the result of each test case
     */
    public List<TestCaseResult> getTestCaseResults() {
        return testCaseResults;
    }

    /**
     * Adds the result of one test case.
     *
     * @param testCaseResult the test case result
     */
    public void addTestCaseResult(TestCaseResult testCaseResult) {
        testCaseResults.add(testCaseResult);
    }
}
