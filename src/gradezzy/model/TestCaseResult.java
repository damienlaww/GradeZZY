/*
 * File: TestCaseResult.java
 *
 * Purpose: The outcome of one test case inside a tester.
 *
 * Where it fits: Produced by the score parser and shown in the student report.
 */
package gradezzy.model;

/**
 * The outcome of one test case inside a tester.
 *
 * <p>Tier: MUST. Owner: Jing Yuan.
 */
public class TestCaseResult {

    // The position of the test, from 1
    private final int testNumber;

    // Whether the test case passed
    private final boolean passed;

    // The tester output for this test case
    private final String detail;

    /**
     * Creates a new test case result.
     *
     * <p>Tier: MUST.
     *
     * @param testNumber the position of the test, from 1
     * @param passed whether the test case passed
     * @param detail the tester output for this test case
     */
    public TestCaseResult(int testNumber, boolean passed, String detail) {
        this.testNumber = testNumber;
        this.passed = passed;
        this.detail = detail;
    }

    /**
     * Returns the position of the test, from 1.
     *
     * <p>Tier: MUST.
     *
     * @return the position of the test, from 1
     */
    public int getTestNumber() {
        return testNumber;
    }

    /**
     * Returns whether the test case passed.
     *
     * <p>Tier: MUST.
     *
     * @return whether the test case passed
     */
    public boolean isPassed() {
        return passed;
    }

    /**
     * Returns the tester output for this test case.
     *
     * <p>Tier: MUST.
     *
     * @return the tester output for this test case
     */
    public String getDetail() {
        return detail;
    }
}
