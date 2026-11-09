/*
 * File: TesterHarness.java
 *
 * Purpose: Starts a tester inside the student's java process and marks the
 * lines that the tester itself prints.
 *
 * Where it fits: TesterRunner runs this class instead of the tester. The
 * tester and the student's code share one standard output, so without the
 * marks a student could print "Passed" and be believed.
 */
package gradezzy.exec;

import java.io.IOException;
import java.lang.reflect.InvocationTargetException;
import java.lang.reflect.Method;
import java.nio.charset.StandardCharsets;
import java.util.ArrayList;
import java.util.List;

/**
 * Starts a tester inside the student's java process and marks the lines
 * that the tester itself prints.
 *
 * <p>The parent sends a secret token on standard input. A line printed by
 * the tester's own code, with no student code running at that moment, is
 * written with the token in front. The parent then trusts only marked
 * lines, so a plain "Passed" printed by student code is not believed.
 * The marking is done by TrustedPrintStream, in its own file.
 *
 * <p>Limit: the token is kept in this process, next to student code.
 * Student code that uses reflection to read the private token field can
 * print lines the parent trusts. Running this class as a named module
 * would block that reflection; that needs a separate compile step.
 *
 * <p>Tier: MUST. Owner: Zach.
 */
public final class TesterHarness {

    /**
     * The marked line printed when the tester's main method has returned.
     */
    public static final String FINISHED_LINE = "GradeZZY: tester finished";

    /**
     * The marked line printed just before a run ends with CRASH_EXIT_CODE.
     * A student's System.exit(3) never prints it, so TesterRunner can tell
     * that exit from a real crash of the tester.
     */
    public static final String CRASHED_LINE = "GradeZZY: tester crashed";

    /**
     * The marked line printed instead of CRASHED_LINE when the case to
     * skip threw outside the tester's try block, so the tester stopped
     * there and the cases after it were not run.
     */
    public static final String SKIP_FAILED_LINE =
            "GradeZZY: skipped case stopped the tester";

    // Exit code of a run that reached the end of the tester
    private static final int SUCCESS_EXIT_CODE = 0;

    /**
     * Exit code of a run whose tester threw something it did not catch,
     * such as a StackOverflowError from a student's endless recursion.
     * It is not 1, which a student's System.exit(1) also gives, so
     * ExecuteStage can tell a crash from code that ends the program.
     */
    public static final int CRASH_EXIT_CODE = 3;

    // The system property that chooses how java starts other programs
    private static final String LAUNCH_PROPERTY =
            "jdk.lang.Process.launchMechanism";

    // A value java does not accept, so starting a program always fails
    private static final String NO_LAUNCH = "DISABLED_BY_GRADEZZY";

    // Extra time past the limit before the watchdog halts the run, so
    // GradeZZY, when it is still running, always stops it first
    private static final long GRACE_SECONDS = 2;

    // Milliseconds in a second
    private static final long MILLIS_PER_SECOND = 1000;

    // The most causes of a crash looked through for a skipped case
    private static final int MAX_CAUSE_DEPTH = 100;

    // The name of the method that starts the tester
    private static final String MAIN_METHOD = "main";

    // Position of the tester class name in the arguments
    private static final int TESTER_ARGUMENT = 0;

    // Position of the most bytes of student output to pass on
    private static final int STUDENT_LIMIT_ARGUMENT = 1;

    // Position of the time limit in seconds
    private static final int TIME_LIMIT_ARGUMENT = 2;

    // Position of the start of a test case's header line
    private static final int HEADER_MARKER_ARGUMENT = 3;

    // Where the numbers of the test cases to skip start in the arguments
    private static final int FIRST_SKIPPED_ARGUMENT =
            HEADER_MARKER_ARGUMENT + 1;

    // Utility class: never created
    private TesterHarness() {
    }

    /**
     * Says whether a tester line is the header of a test case: the header
     * marker, then optional spaces, then a digit, as in "Test 1: ..." or
     * "Test1:". A banner such as "Testing compress" is not a header.
     * TrustedPrintStream and TesterOutputScoreParser both use this, so
     * they count the same cases.
     *
     * @param line the line, without its line break
     * @param headerMarker the setting grading.test.header.marker
     * @return true if the line starts a test case
     */
    public static boolean isCaseHeader(String line, String headerMarker) {
        String text = line.trim();
        if (!text.startsWith(headerMarker)) {
            return false;
        }

        int index = headerMarker.length();
        while (index < text.length()
                && Character.isWhitespace(text.charAt(index))) {
            index++;
        }
        return index < text.length()
                && Character.isDigit(text.charAt(index));
    }

    /**
     * Reads the token, then runs the tester named in the first argument.
     *
     * @param args the tester class name, the most bytes of student output
     *             to pass on, the time limit in seconds, the start of a
     *             test case's header line, then the number of each test
     *             case to skip
     * @throws IOException if the token cannot be read
     * @throws ReflectiveOperationException if the tester cannot be found
     */
    public static void main(String[] args)
            throws IOException, ReflectiveOperationException {
        // Started before any student code, and a daemon, so it never
        // keeps the program alive by itself
        long limitSeconds = Long.parseLong(args[TIME_LIMIT_ARGUMENT]);
        long watchdogMillis = (limitSeconds + GRACE_SECONDS)
                * MILLIS_PER_SECOND;
        Thread watchdog = new Thread(new HarnessWatchdog(watchdogMillis));
        watchdog.setDaemon(true);
        watchdog.start();

        String token = new String(System.in.readAllBytes(),
                StandardCharsets.UTF_8).trim();
        blockNewPrograms();
        long studentLimit = Long.parseLong(args[STUDENT_LIMIT_ARGUMENT]);

        // Loaded without running its static code, so the student's static
        // code runs later, after the marking stream is in place
        Class<?> tester = Class.forName(args[TESTER_ARGUMENT], false,
                TesterHarness.class.getClassLoader());
        List<Integer> skippedCases = readSkippedCases(args);
        TrustedPrintStream trusted = new TrustedPrintStream(System.out,
                token, Thread.currentThread(), tester, studentLimit,
                args[HEADER_MARKER_ARGUMENT], skippedCases);
        System.setOut(trusted);

        Method testerMain = tester.getMethod(MAIN_METHOD, String[].class);
        try {
            testerMain.invoke(null, (Object) new String[0]);
        } catch (InvocationTargetException exception) {
            endAfterCrash(trusted, exception.getCause());
        } catch (ExceptionInInitializerError error) {
            // Static code in the student's class threw while it loaded
            endAfterCrash(trusted, error);
        }

        // A last line the tester printed with print, with no line break
        // after it, such as its total
        trusted.endPendingLine();
        trusted.printMarked(FINISHED_LINE);
        trusted.flush();
        // halt, not exit: student threads or shutdown hooks cannot keep
        // the process alive or print after the tester has finished
        Runtime.getRuntime().halt(SUCCESS_EXIT_CODE);
    }

    /**
     * Reads the numbers of the test cases to skip, which follow the other
     * arguments.
     *
     * @param args the arguments of the harness
     * @return the test cases to skip, counted from 1
     */
    private static List<Integer> readSkippedCases(String[] args) {
        List<Integer> skippedCases = new ArrayList<>();
        for (int index = FIRST_SKIPPED_ARGUMENT; index < args.length;
                index++) {
            skippedCases.add(Integer.valueOf(args[index]));
        }
        return skippedCases;
    }

    /**
     * Ends the run after the tester threw: prints the stack trace as java
     * would, then a marked line saying why, then ends with
     * CRASH_EXIT_CODE. After CRASHED_LINE the case it crashed in can be
     * skipped and the tester run again. After SKIP_FAILED_LINE the case
     * was already being skipped and the skip itself stopped the tester,
     * because its header is printed outside the case's try block.
     *
     * @param trusted the marking stream
     * @param problem what the tester threw
     */
    private static void endAfterCrash(TrustedPrintStream trusted,
            Throwable problem) {
        problem.printStackTrace();
        trusted.endPendingLine();
        if (causedBySkip(problem)) {
            trusted.printMarked(SKIP_FAILED_LINE);
        } else {
            trusted.printMarked(CRASHED_LINE);
        }
        trusted.flush();
        Runtime.getRuntime().halt(CRASH_EXIT_CODE);
    }

    /**
     * Says whether a SkippedCaseException is the problem or one of its
     * causes, so the skip itself, not student code, ended the tester.
     *
     * @param problem what the tester threw
     * @return true if a skip caused it
     */
    private static boolean causedBySkip(Throwable problem) {
        Throwable cause = problem;
        int depth = 0;
        // The depth limit stops a chain of causes that loops back on itself
        while (cause != null && depth < MAX_CAUSE_DEPTH) {
            if (cause instanceof SkippedCaseException) {
                return true;
            }
            cause = cause.getCause();
            depth++;
        }
        return false;
    }

    /**
     * Makes every later attempt to start another program fail, so student
     * code cannot leave programs running after grading. Java reads the
     * setting once, the first time a program is started, so a failed start
     * here fixes it for the rest of the run. Only macOS and Linux read
     * this setting; on Windows ProcessRunner stops child programs instead.
     *
     * @return true if starting programs is now blocked
     */
    private static boolean blockNewPrograms() {
        System.setProperty(LAUNCH_PROPERTY, NO_LAUNCH);
        try {
            new ProcessBuilder(NO_LAUNCH).start().destroyForcibly();
            return false;
        } catch (IOException exception) {
            // The setting was ignored and only the made-up program failed
            return false;
        } catch (Error exception) {
            return true;
        }
    }
}
