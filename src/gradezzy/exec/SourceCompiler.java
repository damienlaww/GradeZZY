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
import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;

import gradezzy.config.AppConfig;
import gradezzy.config.ConfigKeys;

/**
 * Compiles Java source files with javac.
 *
 * <p>Tier: MUST. Owner: Zach.
 */
public class SourceCompiler {

    // Option that sets where javac looks for classes
    private static final String CLASSPATH_OPTION = "-cp";

    // The question folder itself, so provided .class files are found
    private static final String CURRENT_FOLDER = ".";

    // javac writes its messages as UTF-8, so a student's é or Chinese
    // text in an echoed line reads the same on Windows, whose console
    // code page is not UTF-8. The sun. names are for older JDKs.
    private static final String[] UTF8_OUTPUT_OPTIONS = {
        "-J-Dstdout.encoding=UTF-8", "-J-Dstderr.encoding=UTF-8",
        "-J-Dsun.stdout.encoding=UTF-8", "-J-Dsun.stderr.encoding=UTF-8"
    };

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
        if (sourceFileNames == null || sourceFileNames.isEmpty()) {
            throw new ProcessExecutionException("No source files to compile.");
        }

        List<String> command = new ArrayList<>();
        command.add(config.getString(ConfigKeys.JAVAC_COMMAND));
        command.addAll(Arrays.asList(UTF8_OUTPUT_OPTIONS));
        command.add(CLASSPATH_OPTION);
        command.add(CURRENT_FOLDER);
        command.addAll(sourceFileNames);

        long timeoutSeconds = config.getLong(
                ConfigKeys.COMPILE_TIMEOUT_SECONDS);
        return processRunner.run(command, folder, timeoutSeconds);
    }
}
