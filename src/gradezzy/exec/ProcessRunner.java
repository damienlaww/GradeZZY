/*
 * File: ProcessRunner.java
 *
 * Purpose: Runs an external command with a time limit and captures its output.
 *
 * Where it fits: The one place that starts processes. It safely terminates
 * programs that exceed the limit, as the brief requires.
 */
package gradezzy.exec;

import java.io.IOException;
import java.io.InputStream;
import java.io.OutputStream;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.List;

import gradezzy.config.AppConfig;
import gradezzy.config.ConfigKeys;

/**
 * Runs an external command with a time limit and captures its output.
 *
 * <p>Output is read in a polling loop while the process runs. If nobody
 * read it, the process would freeze once its output buffer filled, and
 * the grader would wait for it forever.
 *
 * <p>Tier: MUST. Owner: Zach.
 */
public class ProcessRunner {

    // How long to pause between two checks of a running process
    private static final long POLL_INTERVAL_MILLIS = 50;

    // Milliseconds in one second
    private static final long MILLIS_PER_SECOND = 1000;

    // Size of the buffer used to read the rest of the output at the end
    private static final int READ_BUFFER_SIZE = 8192;

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
        return run(command, workingDirectory, timeoutSeconds, "");
    }

    /**
     * Runs a command with some text on its standard input, and waits for
     * it, up to the time limit. Standard input is closed after the text.
     *
     * @param command the command
     * @param workingDirectory the working directory
     * @param timeoutSeconds the timeout seconds
     * @param input the text sent to standard input
     * @return the exit code, output and timeout flag
     * @throws ProcessExecutionException if the operation fails
     */
    public ProcessResult run(List<String> command, Path workingDirectory,
            long timeoutSeconds, String input)
            throws ProcessExecutionException {
        if (command == null || command.isEmpty()) {
            throw new ProcessExecutionException("No command to run.");
        }
        if (workingDirectory == null || !Files.isDirectory(workingDirectory)) {
            throw new ProcessExecutionException(
                    "Working directory not found: " + workingDirectory);
        }
        if (timeoutSeconds <= 0) {
            throw new ProcessExecutionException(
                    "Time limit must be positive: " + timeoutSeconds);
        }

        int maxCharacters = config.getInt(ConfigKeys.MAX_OUTPUT_CHARACTERS);
        long startMillis = System.currentTimeMillis();
        long deadlineMillis = startMillis + timeoutSeconds * MILLIS_PER_SECOND;
        Process process = startProcess(command, workingDirectory, input);
        StringBuilder outputText = new StringBuilder();
        StringBuilder errorText = new StringBuilder();
        List<ProcessHandle> children = new ArrayList<>();
        boolean timedOut = false;

        try (InputStream output = process.getInputStream();
                InputStream error = process.getErrorStream()) {
            while (process.isAlive() && !timedOut) {
                rememberChildren(process, children);
                readAvailable(output, outputText, maxCharacters);
                readAvailable(error, errorText, maxCharacters);

                if (System.currentTimeMillis() > deadlineMillis) {
                    terminate(process);
                    timedOut = true;
                } else {
                    Thread.sleep(POLL_INTERVAL_MILLIS);
                }
            }

            int exitCode = process.waitFor();
            // Killing a process closes its streams, so after a timeout
            // only the output read inside the loop is kept
            if (!timedOut) {
                readRemaining(output, outputText, maxCharacters);
                readRemaining(error, errorText, maxCharacters);
            }
            long durationMillis = System.currentTimeMillis() - startMillis;
            return new ProcessResult(exitCode, outputText.toString(),
                    errorText.toString(), timedOut, durationMillis);
        } catch (IOException exception) {
            terminate(process);
            throw new ProcessExecutionException(
                    "Could not read the output of " + command.get(0),
                    exception);
        } catch (InterruptedException exception) {
            // The grading run was cancelled: stop the child and pass it on
            terminate(process);
            Thread.currentThread().interrupt();
            throw new ProcessExecutionException(
                    "Interrupted while running " + command.get(0), exception);
        } finally {
            // Student code may start programs that outlive the tester
            stopChildren(children);
        }
    }

    /**
     * Stops a process and any child processes it started.
     *
     * @param process the process
     */
    public void terminate(Process process) {
        if (process == null) {
            return;
        }

        // Collect the children first: once the parent dies they are
        // handed to the operating system and can no longer be found here
        List<ProcessHandle> descendants = process.descendants().toList();
        for (ProcessHandle descendant : descendants) {
            descendant.destroyForcibly();
        }
        process.destroyForcibly();
    }

    /**
     * Adds the child processes running now to the list, so they can still
     * be stopped after the process that started them has ended.
     *
     * @param process the process
     * @param children the children seen so far
     */
    private void rememberChildren(Process process,
            List<ProcessHandle> children) {
        List<ProcessHandle> descendants = process.descendants().toList();
        for (ProcessHandle descendant : descendants) {
            if (!children.contains(descendant)) {
                children.add(descendant);
            }
        }
    }

    /**
     * Stops every remembered child process that is still running.
     *
     * @param children the children seen while the process ran
     */
    private void stopChildren(List<ProcessHandle> children) {
        for (ProcessHandle child : children) {
            if (child.isAlive()) {
                child.destroyForcibly();
            }
        }
    }

    /**
     * Starts the command in the working directory.
     *
     * @param command the command
     * @param workingDirectory the working directory
     * @param input the text sent to standard input
     * @return the running process
     * @throws ProcessExecutionException if the process cannot be started
     */
    private Process startProcess(List<String> command, Path workingDirectory,
            String input) throws ProcessExecutionException {
        ProcessBuilder builder = new ProcessBuilder(command);
        builder.directory(workingDirectory.toFile());

        Process process = null;
        try {
            process = builder.start();
        } catch (IOException exception) {
            throw new ProcessExecutionException(
                    "Could not start " + command.get(0), exception);
        }

        // A program waiting for keyboard input gets end-of-input right
        // after the text instead of hanging until the time limit
        try (OutputStream stdin = process.getOutputStream()) {
            stdin.write(input.getBytes(StandardCharsets.UTF_8));
        } catch (IOException exception) {
            terminate(process);
            throw new ProcessExecutionException(
                    "Could not send input to " + command.get(0), exception);
        }
        return process;
    }

    /**
     * Reads whatever output is ready now, without waiting for more.
     *
     * @param stream the output of the process
     * @param text where the output is collected
     * @param maxCharacters the most characters to keep
     * @throws IOException if the output cannot be read
     */
    private void readAvailable(InputStream stream, StringBuilder text,
            int maxCharacters) throws IOException {
        int available = stream.available();
        if (available <= 0) {
            return;
        }

        byte[] buffer = new byte[available];
        int count = stream.read(buffer);
        appendCapped(text, buffer, count, maxCharacters);
    }

    /**
     * Reads the output that is left after the process has stopped.
     *
     * @param stream the output of the process
     * @param text where the output is collected
     * @param maxCharacters the most characters to keep
     * @throws IOException if the output cannot be read
     */
    private void readRemaining(InputStream stream, StringBuilder text,
            int maxCharacters) throws IOException {
        byte[] buffer = new byte[READ_BUFFER_SIZE];
        int count = stream.read(buffer);

        while (count != -1) {
            appendCapped(text, buffer, count, maxCharacters);
            count = stream.read(buffer);
        }
    }

    /**
     * Adds bytes to the collected text, dropping anything past the limit.
     * The bytes are still read, so the process never blocks.
     *
     * @param text where the output is collected
     * @param buffer the bytes read
     * @param count how many bytes of the buffer are valid
     * @param maxCharacters the most characters to keep
     */
    private void appendCapped(StringBuilder text, byte[] buffer, int count,
            int maxCharacters) {
        int room = maxCharacters - text.length();
        if (count <= 0 || room <= 0) {
            return;
        }

        // shortcut: a character split across two reads may be garbled;
        // fine for the ASCII tester output, upgrade to a Reader if not
        String chunk = new String(buffer, 0, count);
        text.append(chunk, 0, Math.min(room, chunk.length()));
    }
}
