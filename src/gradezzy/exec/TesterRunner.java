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
import java.util.List;

import gradezzy.config.AppConfig;
import gradezzy.config.ConfigKeys;

/**
 * Runs one compiled tester class with java.
 *
 * <p>Tier: MUST. Owner: Zach.
 */
public class TesterRunner {

    // Option that sets where java looks for classes
    private static final String CLASSPATH_OPTION = "-cp";

    // The question folder itself, where the tester and student classes are
    private static final String CURRENT_FOLDER = ".";

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
        if (testerClassName == null || testerClassName.isBlank()) {
            throw new ProcessExecutionException("No tester class to run.");
        }

        List<String> command = List.of(
                config.getString(ConfigKeys.JAVA_COMMAND),
                CLASSPATH_OPTION, CURRENT_FOLDER, testerClassName);
        long timeoutSeconds = config.getLong(ConfigKeys.RUN_TIMEOUT_SECONDS);
        return processRunner.run(command, questionFolder, timeoutSeconds);
    }
}
