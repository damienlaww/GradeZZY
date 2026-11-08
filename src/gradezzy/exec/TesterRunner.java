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
import java.io.IOException;
import java.net.URISyntaxException;
import java.nio.file.Files;
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
 * <p>On macOS the tester runs inside sandbox-exec: it may write only in
 * its own question folder, and may not read the other working copies,
 * the backups or the submissions folder. JDK 24 removed the
 * SecurityManager, so Java itself can no longer do this. Other systems
 * have no built-in sandbox, so there student code keeps the instructor's
 * file permissions and a note is printed once per run.
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

    // The tester's error output is written in UTF-8, as its normal
    // output is, so names and messages with accents read back the same
    private static final String UTF8_ERROR_OPTION =
            "-Dstderr.encoding=UTF-8";

    // The same for JDKs before 19
    private static final String OLD_UTF8_ERROR_OPTION =
            "-Dsun.stderr.encoding=UTF-8";

    // Student code may not load native libraries, which could do anything
    private static final String NO_NATIVE_OPTION =
            "--illegal-native-access=deny";

    // Limits for each tester run, so one student's code cannot take the
    // memory or CPUs the other workers need: a heap size from the
    // settings, a 2 MB thread stack and one CPU
    private static final String MAX_HEAP_OPTION = "-Xmx";
    private static final String MEGABYTES_SUFFIX = "m";
    private static final String STACK_SIZE_OPTION = "-Xss2m";
    private static final String ONE_PROCESSOR_OPTION =
            "-XX:ActiveProcessorCount=1";

    // Point the home and temp folders at the question folder, so code
    // that saves "to home" or "to temp" stays in its own working copy
    private static final String HOME_OPTION = "-Duser.home=";
    private static final String TEMP_OPTION = "-Djava.io.tmpdir=";

    // Student prints may use half the output limit; the other half is
    // kept for the tester's own lines, so they are never cut off
    private static final int STUDENT_SHARE_DIVISOR = 2;

    // The macOS sandbox tool and its option for an inline rule set
    private static final String SANDBOX_COMMAND = "/usr/bin/sandbox-exec";
    private static final String PROFILE_OPTION = "-p";

    // The command used to test the sandbox once, with the real rules, and
    // the start of the name of the empty folder it runs in
    private static final String VERSION_OPTION = "-version";
    private static final String PROBE_FOLDER_PREFIX = "gradezzy-probe-";

    // Where macOS keeps every user's home folder, with the instructor's
    // files, the testers and the ground truth
    private static final String USERS_FOLDER = "/Users";

    // The system property holding the folder of the running JDK
    private static final String JAVA_HOME_PROPERTY = "java.home";

    // Printed once per run where no sandbox exists
    private static final String NO_SANDBOX_NOTE = "Note: student code is "
            + "not sandboxed, because this system has no sandbox or java "
            + "could not start inside it, so it can reach any file the "
            + "instructor can.";

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

    // Whether testers run inside the macOS sandbox
    private final boolean sandboxed;

    /**
     * Creates a new tester runner. One is made for each grading run, so
     * the note about a missing sandbox is printed once per run.
     *
     * @param config the application settings
     * @param processRunner the helper that runs external commands
     */
    public TesterRunner(AppConfig config, ProcessRunner processRunner) {
        this.config = config;
        this.processRunner = processRunner;
        this.sandboxed = sandboxWorks();
        if (!sandboxed) {
            System.err.println(NO_SANDBOX_NOTE);
        }
    }

    /**
     * Says whether java can run inside the macOS sandbox. It is tried
     * once, with the same rules a tester gets, because the tool can exist
     * and still refuse to start, or java can be kept from reading its own
     * files, and then every tester would fail.
     *
     * @return true if testers can run inside the sandbox
     */
    private boolean sandboxWorks() {
        if (!Files.isExecutable(Path.of(SANDBOX_COMMAND))) {
            return false;
        }
        // An empty folder stands in for the question folder, so the probe
        // gets no more to read than any real run
        Path probeFolder = null;
        try {
            probeFolder = Files.createTempDirectory(PROBE_FOLDER_PREFIX)
                    .toRealPath();
            List<String> command = new ArrayList<>();
            command.add(SANDBOX_COMMAND);
            command.add(PROFILE_OPTION);
            command.add(sandboxRules(probeFolder, harnessLocation()));
            command.add(config.getString(ConfigKeys.JAVA_COMMAND));
            command.add(VERSION_OPTION);
            ProcessResult probe = processRunner.run(command, probeFolder,
                    config.getLong(ConfigKeys.RUN_TIMEOUT_SECONDS));
            return probe.getExitCode() == SUCCESS_EXIT_CODE;
        } catch (ProcessExecutionException exception) {
            return false;
        } catch (IOException exception) {
            return false;
        } finally {
            deleteProbeFolder(probeFolder);
        }
    }

    /**
     * Deletes the empty folder the probe ran in. A folder that cannot be
     * deleted is left in the temp folder, which the system clears.
     *
     * @param probeFolder the folder, or null if it was never made
     */
    private void deleteProbeFolder(Path probeFolder) {
        if (probeFolder == null) {
            return;
        }
        try {
            Files.deleteIfExists(probeFolder);
        } catch (IOException exception) {
            System.err.println("Could not delete " + probeFolder);
        }
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
        return runTester(questionFolder, testerClassName, new ArrayList<>());
    }

    /**
     * Runs a tester, skipping some of its test cases: each one fails
     * without calling the student's code. A case that hung on an earlier
     * run is skipped so the cases after it can still run.
     *
     * @param questionFolder the question folder
     * @param testerClassName the tester class name
     * @param skippedCases the test cases to skip, counted from 1
     * @return the tester output and timeout flag
     * @throws ProcessExecutionException if the operation fails
     */
    public ProcessResult runTester(Path questionFolder,
            String testerClassName, List<Integer> skippedCases)
            throws ProcessExecutionException {
        if (testerClassName == null || testerClassName.isBlank()) {
            throw new ProcessExecutionException("No tester class to run.");
        }

        // The harness comes first, so a student class cannot replace it
        String harness = harnessLocation();
        String classPath = harness + File.pathSeparator + CURRENT_FOLDER;
        String folder = questionFolder.toAbsolutePath().toString();
        int studentLimit = config.getInt(ConfigKeys.MAX_OUTPUT_CHARACTERS)
                / STUDENT_SHARE_DIVISOR;
        List<String> command = new ArrayList<>();
        if (sandboxed) {
            command.add(SANDBOX_COMMAND);
            command.add(PROFILE_OPTION);
            command.add(sandboxRules(questionFolder, harness));
        }
        command.add(config.getString(ConfigKeys.JAVA_COMMAND));
        int maxMemoryMb = config.getInt(ConfigKeys.MAX_MEMORY_MB);
        if (maxMemoryMb > 0) {
            command.add(MAX_HEAP_OPTION + maxMemoryMb + MEGABYTES_SUFFIX);
        }
        // A stack trace on standard error is UTF-8 on Windows too
        command.add(UTF8_ERROR_OPTION);
        command.add(OLD_UTF8_ERROR_OPTION);
        command.add(STACK_SIZE_OPTION);
        command.add(ONE_PROCESSOR_OPTION);
        command.add(LIMIT_MODULES_OPTION);
        command.add(NO_NATIVE_OPTION);
        command.add(HOME_OPTION + folder);
        command.add(TEMP_OPTION + folder);
        command.add(CLASSPATH_OPTION);
        command.add(classPath);
        command.add(TesterHarness.class.getName());
        command.add(testerClassName);
        command.add(String.valueOf(studentLimit));
        long timeoutSeconds = config.getLong(ConfigKeys.RUN_TIMEOUT_SECONDS);
        // The harness stops itself a little after this, in case GradeZZY
        // is gone by then
        command.add(String.valueOf(timeoutSeconds));
        command.add(config.getString(ConfigKeys.TEST_HEADER_MARKER));
        for (Integer skippedCase : skippedCases) {
            command.add(String.valueOf(skippedCase));
        }
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
     * Builds the sandbox rules for one run. In these rules a later line
     * wins over an earlier one, so the JDK, the harness and the own
     * question folder are allowed again after the folders around them are
     * denied. No network is allowed, so files cannot be sent away.
     *
     * @param questionFolder the question folder
     * @param harness the folder or jar holding TesterHarness
     * @return the rules, in the sandbox's own language
     */
    private String sandboxRules(Path questionFolder, String harness) {
        String own = quotedPath(questionFolder);
        String javaHome = quotedPath(Path.of(
                System.getProperty(JAVA_HOME_PROPERTY)));
        // shortcut: reads are blocked under /Users and in the configured
        // folders; a batch folder chosen elsewhere, such as on an external
        // drive, can still be read; pass it in if that matters
        return "(version 1)(allow default)"
                + "(deny network*)"
                + "(deny file-write*)"
                + "(allow file-write* (subpath " + own + ")"
                + " (literal \"/dev/null\"))"
                + "(deny file-read-data"
                + " (subpath \"" + USERS_FOLDER + "\")"
                + " (subpath " + configuredPath(ConfigKeys.WORKING_DIR) + ")"
                + " (subpath " + configuredPath(ConfigKeys.BACKUP_DIR) + ")"
                + " (subpath " + configuredPath(ConfigKeys.SUBMISSIONS_DIR)
                + "))"
                + "(allow file-read-data"
                + " (subpath " + javaHome + ")"
                + " (subpath " + quotedPath(Path.of(harness)) + ")"
                + " (subpath " + own + "))";
    }

    /**
     * Returns a folder from the settings, quoted for the sandbox rules.
     *
     * @param key the setting name
     * @return the quoted full path
     */
    private String configuredPath(String key) {
        return quotedPath(Path.of(config.getString(key)));
    }

    /**
     * Returns the real full path in double quotes. The sandbox compares
     * real paths, so links such as /tmp to /private/tmp are followed.
     *
     * @param path the path
     * @return the quoted path
     */
    private String quotedPath(Path path) {
        String escaped = realPath(path).toString().replace("\\", "\\\\")
                .replace("\"", "\\\"");
        return "\"" + escaped + "\"";
    }

    /**
     * Returns the real full path, following links.
     *
     * @param path the path
     * @return the real path, or the plain full path if it does not exist
     */
    private Path realPath(Path path) {
        try {
            return path.toRealPath();
        } catch (IOException exception) {
            return path.toAbsolutePath().normalize();
        }
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
