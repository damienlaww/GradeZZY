/*
 * File: TestCase.java
 *
 * Purpose: One test case typed into the app by the instructor.
 *
 * Where it fits: Turned into a tester source file by TesterGenerator.
 */
package gradezzy.testcase;

/**
 * One test case typed into the app by the instructor.
 *
 * <p>Tier: SHOULD. Owner: Zach.
 */
public class TestCase {

    // The question this case belongs to
    private final String questionId;

    // The student method to call
    private final String methodName;

    // The Java expressions passed as arguments
    private final String argumentExpressions;

    // The Java expression for the expected result
    private final String expectedExpression;

    /**
     * Creates a new test case.
     *
     * @param questionId the question this case belongs to
     * @param methodName the student method to call
     * @param argumentExpressions the Java expressions passed as arguments
     * @param expectedExpression the Java expression for the expected result
     */
    public TestCase(String questionId, String methodName,
            String argumentExpressions, String expectedExpression) {
        this.questionId = questionId;
        this.methodName = methodName;
        this.argumentExpressions = argumentExpressions;
        this.expectedExpression = expectedExpression;
    }

    /**
     * Returns the question this case belongs to.
     *
     * @return the question this case belongs to
     */
    public String getQuestionId() {
        return questionId;
    }

    /**
     * Returns the student method to call.
     *
     * @return the student method to call
     */
    public String getMethodName() {
        return methodName;
    }

    /**
     * Returns the Java expressions passed as arguments.
     *
     * @return the Java expressions passed as arguments
     */
    public String getArgumentExpressions() {
        return argumentExpressions;
    }

    /**
     * Returns the Java expression for the expected result.
     *
     * @return the Java expression for the expected result
     */
    public String getExpectedExpression() {
        return expectedExpression;
    }
}
