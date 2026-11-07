/*
 * File: SkippedCaseException.java
 *
 * Purpose: Stops a test case that hung on an earlier run of the tester.
 *
 * Where it fits: Thrown by TrustedPrintStream inside the tester's own try
 * block, so the tester fails the case and goes on to the next one.
 */
package gradezzy.exec;

/**
 * Stops a test case that hung on an earlier run of the tester. The
 * provided testers print the stack trace of any exception in a case, so
 * this one has no stack frames and prints as its message alone: the
 * instructor reads one plain line instead of a GradeZZY stack trace.
 *
 * <p>Tier: SHOULD. Owner: Zach.
 */
public class SkippedCaseException extends RuntimeException {

    /**
     * The line printed for a skipped case, and its detail in the results.
     */
    public static final String SKIPPED_MESSAGE =
            "Skipped: this case hung in an earlier run.";

    /**
     * Creates the exception, with no stack frames to print.
     */
    public SkippedCaseException() {
        super(SKIPPED_MESSAGE);
        setStackTrace(new StackTraceElement[0]);
    }

    /**
     * Returns the message alone, without the class name in front.
     *
     * @return the message
     */
    @Override
    public String toString() {
        return getMessage();
    }
}
