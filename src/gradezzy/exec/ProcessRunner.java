/*
 * File: ProcessRunner.java
 *
 * Purpose: Runs an external command with a time limit and captures its output.
 *
 * Where it fits: The one place that starts processes. It safely terminates
 * programs that exceed the limit, as the brief requires.
 */
package gradezzy.exec;

import java.nio.file.Path;
import java.util.List;

import gradezzy.config.AppConfig;

/**
 * Runs an external command with a time limit and captures its output.
 *
 * <p>Tier: MUST. Owner: Zach.
 */
public class ProcessRunner {

    // The application settings
    private final AppConfig config;

    /**
     * Creates a new process runner.
     *
     * @param config the application settings
     */
    public ProcessRunner(AppConfig config) {
        this.config = config;
    }

    /**
     * Runs a command and waits for it, up to the time limit.
     *
     * @param command the command
     * @param workingDirectory the working directory
     * @param timeoutSeconds the timeout seconds
     * @return the exit code, output and timeout flag
     * @throws ProcessExecutionException if the operation fails
     */
    public ProcessResult run(List<String> command, Path workingDirectory,
            long timeoutSeconds) throws ProcessExecutionException {
        // TODO(Zach): implement: starting the process with ProcessBuilder,
        //     reading output without blocking, waiting with a timeout and
        //     calling terminate if it is exceeded
        return null;
    }

    /**
     * Stops a process and any child processes it started.
     *
     * @param process the process
     */
    public void terminate(Process process) {
        // TODO(Zach): implement: destroyForcibly on the process and its
        //     descendants
    }
}
