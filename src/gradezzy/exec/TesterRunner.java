/*
 * File: TesterRunner.java
 *
 * Purpose: Runs one compiled tester class with java.
 *
 * Where it fits: Used by ExecuteStage. Each tester runs in its own process so
 * an infinite loop cannot block the batch.
 */
package gradezzy.exec;

import java.io.File;
import java.net.URISyntaxException;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.List;
import java.util.UUID;

import gradezzy.config.AppConfig;
import gradezzy.config.ConfigKeys;
import gradezzy.util.TextUtils;

/**
 * Runs one compiled tester class with java.
 *
 * <p>The tester is started through TesterHarness, which marks the lines
 * the tester itself prints with a secret token. Every other line was
 * printed by student code, and is returned with STUDENT_LINE_PREFIX in
 * front so the score parser never counts it.
 *
 * <p>Tier: MUST. Owner: Zach.
 */
public class TesterRunner {

    /**
     * Put in front of each output line printed by student code.
     */
    public static final String STUDENT_LINE_PREFIX = "[student] ";

    /**
     * Exit code reported when the program ended with code 0 before the
     * tester reached its end, for example through System.exit in student
     * code.
     */
    public static final int EARLY_EXIT_CODE = -1;

    // Option that sets where java looks for classes
    private static final String CLASSPATH_OPTION = "-cp";

    // The question folder itself, where the tester and student classes are
    private static final String CURRENT_FOLDER = ".";

    // Only core Java is loaded, so student code cannot reach the compiler,
    // the attach and heap dump tools or other low-level modules
    private static final String LIMIT_MODULES_OPTION =
            "--limit-modules=java.base";

    // Student code may not load native libraries, which could do anything
    private static final String NO_NATIVE_OPTION =
            "--illegal-native-access=deny";

    // Exit code of a program that ended normally
    private static final int SUCCESS_EXIT_CODE = 0;

    // Splits output into lines, whatever the line ending
    private static final String LINE_BREAK = "\\R";

    // Ends each line of the rebuilt output
    private static final String NEW_LINE = "\n";

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

        // The harness comes first, so a student class cannot replace it
        String classPath = harnessLocation() + File.pathSeparator
                + CURRENT_FOLDER;
        List<String> command = new ArrayList<>();
        command.add(config.getString(ConfigKeys.JAVA_COMMAND));
        command.add(LIMIT_MODULES_OPTION);
        command.add(NO_NATIVE_OPTION);
        command.add(CLASSPATH_OPTION);
        command.add(classPath);
        command.add(TesterHarness.class.getName());
        command.add(testerClassName);
        long timeoutSeconds = config.getLong(ConfigKeys.RUN_TIMEOUT_SECONDS);
        // A new random token for every run, sent where only the harness
        // reads it before any student code starts
        String token = UUID.randomUUID().toString();

        ProcessResult raw = processRunner.run(command, questionFolder,
                timeoutSeconds, token);
        return separateStudentLines(raw, token);
    }

    /**
     * Rebuilds the output: marked lines lose their token, every other line
     * gets STUDENT_LINE_PREFIX, and the harness's finished line is
     * removed. A run that exited with code 0 without that line was ended
     * early by student code, so it gets EARLY_EXIT_CODE.
     *
     * @param raw the result as the process printed it
     * @param token the token of this run
     * @return the result with the lines told apart
     */
    private ProcessResult separateStudentLines(ProcessResult raw,
            String token) {
        // The lines are joined once at the end: a student printing many
        // short lines would make += copy the whole output again each time
        List<String> lines = new ArrayList<>();
        boolean finished = false;

        for (String line : raw.getStandardOutput().split(LINE_BREAK)) {
            int tokenStart = line.indexOf(token);
            String studentPart = line;
            if (tokenStart >= 0) {
                studentPart = line.substring(0, tokenStart);
            }
            if (!studentPart.isEmpty()) {
                lines.add(STUDENT_LINE_PREFIX + studentPart + NEW_LINE);
            }

            String testerLine = "";
            if (tokenStart >= 0) {
                testerLine = line.substring(tokenStart + token.length());
            }
            if (testerLine.equals(TesterHarness.FINISHED_LINE)) {
                finished = true;
            } else if (tokenStart >= 0) {
                lines.add(testerLine + NEW_LINE);
            }
        }

        int exitCode = raw.getExitCode();
        if (exitCode == SUCCESS_EXIT_CODE && !finished && !raw.isTimedOut()) {
            exitCode = EARLY_EXIT_CODE;
        }
        return new ProcessResult(exitCode, TextUtils.join("", lines),
                raw.getStandardError(), raw.isTimedOut(),
                raw.getDurationMillis());
    }

    /**
     * Finds the folder or jar that holds the compiled TesterHarness.
     *
     * @return the location, for the class path
     * @throws ProcessExecutionException if it cannot be found
     */
    private String harnessLocation() throws ProcessExecutionException {
        try {
            return Path.of(TesterHarness.class.getProtectionDomain()
                    .getCodeSource().getLocation().toURI()).toString();
        } catch (URISyntaxException exception) {
            throw new ProcessExecutionException(
                    "The tester harness could not be found.", exception);
        }
    }
}
