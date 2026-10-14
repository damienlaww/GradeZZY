/*
 * File: TesterRunner.java
 *
 * Purpose: Runs one compiled tester class with java.
 *
 * Where it fits: Used by ExecuteStage. Each tester runs in its own process so
 * an infinite loop cannot block the batch.
 */
package gradezzy.exec;

import java.nio.file.Path;

import gradezzy.config.AppConfig;

/**
 * Runs one compiled tester class with java.
 *
 * <p>Tier: MUST. Owner: Zach.
 */
public class TesterRunner {

    // The application settings
    private final AppConfig config;

    // The helper that runs external commands
    private final ProcessRunner processRunner;

    /**
     * Creates a new tester runner.
     *
     * @param config the application settings
     * @param processRunner the helper that runs external commands
     */
    public TesterRunner(AppConfig config, ProcessRunner processRunner) {
        this.config = config;
        this.processRunner = processRunner;
    }

    /**
     * Runs a tester inside the student's question folder.
     *
     * @param questionFolder the question folder
     * @param testerClassName the tester class name
     * @return the tester output and timeout flag
     * @throws ProcessExecutionException if the operation fails
     */
    public ProcessResult runTester(Path questionFolder,
            String testerClassName) throws ProcessExecutionException {
        // TODO(Zach): implement: building the java command from JAVA_COMMAND
        //     and running it with RUN_TIMEOUT_SECONDS
        return null;
    }
}
