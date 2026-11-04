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
import java.util.Iterator;
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

    // How long to wait for a stopped process to end
    private static final long EXIT_WAIT_MILLIS = 1000;

    // Every program any ProcessRunner started that has not ended. A
    // shutdown hook stops them all if GradeZZY closes during a run.
    private static final RunningProcesses RUNNING = startShutdownHook();

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
        RUNNING.add(process);
        List<ProcessHandle> children = new ArrayList<>();

        try {
            return collectOutput(process, children, startMillis,
                    deadlineMillis, maxCharacters);
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
            RUNNING.remove(process);
        }
    }

    /**
     * Makes the list of running programs and asks Java to stop them all
     * when GradeZZY shuts down. Called once, when this class is loaded.
     *
     * @return the list of running programs
     */
    private static RunningProcesses startShutdownHook() {
        RunningProcesses running = new RunningProcesses();
        Runtime.getRuntime().addShutdownHook(new Thread(running));
        return running;
    }

    /**
     * Waits for the process, up to the deadline, while reading its output
     * and remembering its child processes. Both output streams are closed
     * before this returns or throws.
     *
     * @param process the running process
     * @param children the children seen so far, added to while it runs
     * @param startMillis when the process was started
     * @param deadlineMillis when the process must be stopped
     * @param maxCharacters the most characters to keep of each output
     * @return the exit code, output and timeout flag
     * @throws IOException if the output cannot be read
     * @throws InterruptedException if the grading run was cancelled
     */
    private ProcessResult collectOutput(Process process,
            List<ProcessHandle> children, long startMillis,
            long deadlineMillis, int maxCharacters)
            throws IOException, InterruptedException {
        InputStream output = process.getInputStream();
        InputStream error = process.getErrorStream();
        String outputText = "";
        String errorText = "";
        boolean timedOut = false;

        try {
            while (process.isAlive() && !timedOut) {
                rememberChildren(process, children);
                outputText = readAvailable(output, outputText, maxCharacters);
                errorText = readAvailable(error, errorText, maxCharacters);

                if (System.currentTimeMillis() > deadlineMillis) {
                    stopKeepingOutput(process);
                    timedOut = true;
                } else {
                    Thread.sleep(POLL_INTERVAL_MILLIS);
                }
            }

            int exitCode = process.waitFor();
            // A timed-out process was stopped with its streams left open,
            // so output printed just before, such as a last "Passed", is
            // still read here
            outputText = readRemaining(output, outputText, maxCharacters);
            errorText = readRemaining(error, errorText, maxCharacters);
            long durationMillis = System.currentTimeMillis() - startMillis;
            return new ProcessResult(exitCode, outputText, errorText,
                    timedOut, durationMillis);
        } finally {
            closeStreams(output, error);
        }
    }

    /**
     * Closes both output streams of a process, the error stream first.
     * The normal output stream is closed even if closing the error stream
     * fails.
     *
     * @param output the normal output of the process
     * @param error the error output of the process
     * @throws IOException if a stream cannot be closed
     */
    private void closeStreams(InputStream output, InputStream error)
            throws IOException {
        try {
            error.close();
        } finally {
            output.close();
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

        stopKeepingOutput(process);
        // Also closes the streams of the process
        process.destroyForcibly();
        // On Windows a stopping java.exe holds its working folder, so
        // wait a moment for it to end before the folder is reused
        waitForExit(process.toHandle());
    }

    // Waits briefly for a stopped process to end.
    private static void waitForExit(ProcessHandle handle) {
        try {
            handle.onExit().get(EXIT_WAIT_MILLIS,
                    java.util.concurrent.TimeUnit.MILLISECONDS);
        } catch (java.util.concurrent.TimeoutException
                | java.util.concurrent.ExecutionException exception) {
            // It is still stopping; the folder delete tries again
        } catch (InterruptedException exception) {
            Thread.currentThread().interrupt();
        }
    }

    /**
     * Stops a process and any child processes it started, but leaves its
     * output streams open, so what it printed can still be read.
     *
     * @param process the process
     */
    private void stopKeepingOutput(Process process) {
        // Collect the children first: once the parent dies they are
        // handed to the operating system and can no longer be found here
        List<ProcessHandle> descendants = listDescendants(process);
        for (ProcessHandle descendant : descendants) {
            descendant.destroyForcibly();
        }
        process.toHandle().destroyForcibly();
    }

    /**
     * Lists the child processes of a process running now, and their
     * children in turn. The JDK hands them out one at a time, so they are
     * copied into a list first.
     *
     * @param process the process
     * @return the descendants found
     */
    private List<ProcessHandle> listDescendants(Process process) {
        List<ProcessHandle> descendants = new ArrayList<>();
        Iterator<ProcessHandle> found = process.descendants().iterator();
        while (found.hasNext()) {
            descendants.add(found.next());
        }
        return descendants;
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
        List<ProcessHandle> descendants = listDescendants(process);
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
                waitForExit(child);
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
        try {
            sendInput(process.getOutputStream(), input);
        } catch (IOException exception) {
            terminate(process);
            throw new ProcessExecutionException(
                    "Could not send input to " + command.get(0), exception);
        }
        return process;
    }

    /**
     * Writes the text to the standard input of a process, then closes it.
     * The stream is closed even if writing fails.
     *
     * @param stdin the standard input of the process
     * @param input the text to send
     * @throws IOException if the text cannot be written or the stream
     *         cannot be closed
     */
    private void sendInput(OutputStream stdin, String input)
            throws IOException {
        try {
            stdin.write(input.getBytes(StandardCharsets.UTF_8));
        } finally {
            stdin.close();
        }
    }

    /**
     * Reads whatever output is ready now, without waiting for more.
     *
     * @param stream the output of the process
     * @param text the output collected so far
     * @param maxCharacters the most characters to keep
     * @return the output collected so far with the new output added
     * @throws IOException if the output cannot be read
     */
    private String readAvailable(InputStream stream, String text,
            int maxCharacters) throws IOException {
        int available = stream.available();
        if (available <= 0) {
            return text;
        }

        byte[] buffer = new byte[available];
        int count = stream.read(buffer);
        return appendCapped(text, buffer, count, maxCharacters);
    }

    /**
     * Reads the output that is left after the process has stopped. Only
     * what is already waiting is read: a program the process started can
     * keep the pipe open, and on Windows a read that waits for the end of
     * the pipe would then wait forever.
     *
     * @param stream the output of the process
     * @param text the output collected so far
     * @param maxCharacters the most characters to keep
     * @return the output collected so far with the rest added
     * @throws IOException if the output cannot be read
     */
    private String readRemaining(InputStream stream, String text,
            int maxCharacters) throws IOException {
        String collected = text;
        while (stream.available() > 0) {
            collected = readAvailable(stream, collected, maxCharacters);
        }
        return collected;
    }

    /**
     * Adds bytes to the collected text, dropping anything past the limit.
     * The bytes are still read, so the process never blocks. Each call
     * adds one chunk of at most a few thousand characters and the total is
     * capped, so joining Strings with + stays quick.
     *
     * @param text the output collected so far
     * @param buffer the bytes read
     * @param count how many bytes of the buffer are valid
     * @param maxCharacters the most characters to keep
     * @return the collected text with the new characters added
     */
    private String appendCapped(String text, byte[] buffer, int count,
            int maxCharacters) {
        int room = maxCharacters - text.length();
        if (count <= 0 || room <= 0) {
            return text;
        }

        // shortcut: a character split across two reads may be garbled;
        // fine for the ASCII tester output, upgrade to a Reader if not
        String chunk = new String(buffer, 0, count);
        return text + chunk.substring(0, Math.min(room, chunk.length()));
    }
}
