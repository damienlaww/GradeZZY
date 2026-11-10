/*
 * File: TestCaseResult.java
 *
 * Purpose: The outcome of one test case inside a tester.
 *
 * Where it fits: Produced by the score parser and shown in the student report.
 * The name is what the tester printed after the case's number, such as
 * "getIsogramWords([alice, ben])", and names the case's error group.
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

    // What the tester's header calls the case, or null when not known
    private final String name;

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
        this(testNumber, passed, detail, null);
    }

    /**
     * Creates a new test case result with the name the tester gave it.
     *
     * <p>Tier: COULD.
     *
     * @param testNumber the position of the test, from 1
     * @param passed whether the test case passed
     * @param detail the tester output for this test case
     * @param name what the tester's header calls the case, such as
     *             "getIsogramWords([alice, ben])", or null when not known
     */
    public TestCaseResult(int testNumber, boolean passed, String detail,
            String name) {
        this.testNumber = testNumber;
        this.passed = passed;
        this.detail = detail;
        this.name = name;
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

    /**
     * Returns what the tester's header calls the case.
     *
     * <p>Tier: COULD.
     *
     * @return the name, such as "getIsogramWords([alice, ben])", or null
     *         when the tester printed none or the run was saved before
     *         names were kept
     */
    public String getName() {
        return name;
    }
}
