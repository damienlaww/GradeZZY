/*
 * File: ProcessResult.java
 *
 * Purpose: What came back from running one external command.
 *
 * Where it fits: Returned by ProcessRunner and read by the compile stage and
 * the score parser.
 */
package gradezzy.exec;

/**
 * What came back from running one external command.
 *
 * <p>Tier: MUST. Owner: Zach.
 */
public class ProcessResult {

    // The exit code of the process
    private final int exitCode;

    // Everything printed to standard output
    private final String standardOutput;

    // Everything printed to standard error
    private final String standardError;

    // Whether the process was stopped at the time limit
    private final boolean timedOut;

    // How long the process ran, in milliseconds
    private final long durationMillis;

    /**
     * Creates a new process result.
     *
     * @param exitCode the exit code of the process
     * @param standardOutput everything printed to standard output
     * @param standardError everything printed to standard error
     * @param timedOut whether the process was stopped at the time limit
     * @param durationMillis how long the process ran, in milliseconds
     */
    public ProcessResult(int exitCode, String standardOutput,
            String standardError, boolean timedOut, long durationMillis) {
        this.exitCode = exitCode;
        this.standardOutput = standardOutput;
        this.standardError = standardError;
        this.timedOut = timedOut;
        this.durationMillis = durationMillis;
    }

    /**
     * Returns the exit code of the process.
     *
     * @return the exit code of the process
     */
    public int getExitCode() {
        return exitCode;
    }

    /**
     * Returns everything printed to standard output.
     *
     * @return everything printed to standard output
     */
    public String getStandardOutput() {
        return standardOutput;
    }

    /**
     * Returns everything printed to standard error.
     *
     * @return everything printed to standard error
     */
    public String getStandardError() {
        return standardError;
    }

    /**
     * Returns whether the process was stopped at the time limit.
     *
     * @return whether the process was stopped at the time limit
     */
    public boolean isTimedOut() {
        return timedOut;
    }

    /**
     * Returns how long the process ran, in milliseconds.
     *
     * @return how long the process ran, in milliseconds
     */
    public long getDurationMillis() {
        return durationMillis;
    }
}
