/*
 * File: TestCaseException.java
 *
 * Purpose: Signals that tester files could not be prepared or installed.
 *
 * Where it fits: Thrown by the testcase classes and reported as an anomaly.
 */
package gradezzy.testcase;

/**
 * Signals that tester files could not be prepared or installed.
 *
 * <p>Tier: MUST. Owner: Zach.
 */
public class TestCaseException extends Exception {

    /**
     * Creates the exception with a message for the instructor.
     *
     * @param message what went wrong, in plain words
     */
    public TestCaseException(String message) {
        super(message);
    }

    /**
     * Creates the exception and keeps the original cause.
     *
     * @param message what went wrong, in plain words
     * @param cause the lower level exception that triggered this
     */
    public TestCaseException(String message, Throwable cause) {
        super(message, cause);
    }
}
