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
        Thread watchdog = new Thread(new HarnessWatchdog(
                (limitSeconds + GRACE_SECONDS) * MILLIS_PER_SECOND));
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
        List<Integer> skippedCases = new ArrayList<>();
        for (int index = FIRST_SKIPPED_ARGUMENT; index < args.length;
                index++) {
            skippedCases.add(Integer.valueOf(args[index]));
        }
        TrustedPrintStream trusted = new TrustedPrintStream(System.out,
                token, Thread.currentThread(), tester, studentLimit,
                args[HEADER_MARKER_ARGUMENT], skippedCases);
        System.setOut(trusted);

        Method testerMain = tester.getMethod("main", String[].class);
        try {
            testerMain.invoke(null, (Object) new String[0]);
        } catch (InvocationTargetException exception) {
            endAfterCrash(trusted, exception.getCause());
        } catch (ExceptionInInitializerError error) {
            // Static code in the student's class threw while it loaded
            endAfterCrash(trusted, error);
        }

        trusted.printMarked(FINISHED_LINE);
        trusted.flush();
        // halt, not exit: student threads or shutdown hooks cannot keep
        // the process alive or print after the tester has finished
        Runtime.getRuntime().halt(SUCCESS_EXIT_CODE);
    }

    /**
     * Ends the run after the tester threw: prints the stack trace as java
     * would, then ends with CRASH_EXIT_CODE, so the case it crashed in can
     * be skipped and the tester run again.
     *
     * @param trusted the marking stream
     * @param problem what the tester threw
     */
    private static void endAfterCrash(TrustedPrintStream trusted,
            Throwable problem) {
        problem.printStackTrace();
        trusted.flush();
        Runtime.getRuntime().halt(CRASH_EXIT_CODE);
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
