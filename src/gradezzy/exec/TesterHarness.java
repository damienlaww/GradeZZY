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

/**
 * Starts a tester inside the student's java process and marks the lines
 * that the tester itself prints.
 *
 * <p>The parent sends a secret token on standard input. A line printed by
 * the tester's own code, with no student code running at that moment, is
 * written with the token in front. Student code cannot add the token,
 * because it never sees it. The parent then trusts only marked lines.
 * The marking is done by TrustedPrintStream, in its own file.
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

    // Exit code java itself uses when main throws
    private static final int ERROR_EXIT_CODE = 1;

    // The system property that chooses how java starts other programs
    private static final String LAUNCH_PROPERTY =
            "jdk.lang.Process.launchMechanism";

    // A value java does not accept, so starting a program always fails
    private static final String NO_LAUNCH = "DISABLED_BY_GRADEZZY";

    // Utility class: never created
    private TesterHarness() {
    }

    /**
     * Reads the token, then runs the tester named in the first argument.
     *
     * @param args the tester class name
     * @throws IOException if the token cannot be read
     * @throws ReflectiveOperationException if the tester cannot be found
     */
    public static void main(String[] args)
            throws IOException, ReflectiveOperationException {
        String token = new String(System.in.readAllBytes(),
                StandardCharsets.UTF_8).trim();
        blockNewPrograms();

        // Loaded without running its static code, so the student's static
        // code runs later, after the marking stream is in place
        Class<?> tester = Class.forName(args[0], false,
                TesterHarness.class.getClassLoader());
        TrustedPrintStream trusted = new TrustedPrintStream(System.out,
                token, Thread.currentThread(), tester);
        System.setOut(trusted);

        Method testerMain = tester.getMethod("main", String[].class);
        try {
            testerMain.invoke(null, (Object) new String[0]);
        } catch (InvocationTargetException exception) {
            exception.getCause().printStackTrace();
            trusted.flush();
            Runtime.getRuntime().halt(ERROR_EXIT_CODE);
        }

        trusted.printMarked(FINISHED_LINE);
        trusted.flush();
        // halt, not exit: student threads or shutdown hooks cannot keep
        // the process alive or print after the tester has finished
        Runtime.getRuntime().halt(SUCCESS_EXIT_CODE);
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
