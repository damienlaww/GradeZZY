/*
 * File: SourceCompiler.java
 *
 * Purpose: Compiles Java source files with javac.
 *
 * Where it fits: Used by CompileStage for the student's code and the tester
 * files.
 */
package gradezzy.exec;

import java.nio.file.Path;
import java.util.List;

import gradezzy.config.AppConfig;

/**
 * Compiles Java source files with javac.
 *
 * <p>Tier: MUST. Owner: Zach.
 */
public class SourceCompiler {

    // The application settings
    private final AppConfig config;

    // The helper that runs external commands
    private final ProcessRunner processRunner;

    /**
     * Creates a new source compiler.
     *
     * @param config the application settings
     * @param processRunner the helper that runs external commands
     */
    public SourceCompiler(AppConfig config, ProcessRunner processRunner) {
        this.config = config;
        this.processRunner = processRunner;
    }

    /**
     * Compiles the given files inside one question folder.
     *
     * @param folder the folder
     * @param sourceFileNames the source file names
     * @return the compiler exit code and messages
     * @throws ProcessExecutionException if the operation fails
     */
    public ProcessResult compile(Path folder,
            List<String> sourceFileNames) throws ProcessExecutionException {
        // TODO(Zach): implement: building the javac command from JAVAC_COMMAND
        //     and running it with COMPILE_TIMEOUT_SECONDS
        return null;
    }
}
