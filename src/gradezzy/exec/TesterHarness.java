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
import java.io.PrintStream;
import java.lang.reflect.InvocationTargetException;
import java.lang.reflect.Method;
import java.nio.charset.StandardCharsets;
import java.util.Locale;

/**
 * Starts a tester inside the student's java process and marks the lines
 * that the tester itself prints.
 *
 * <p>The parent sends a secret token on standard input. A line printed by
 * the tester's own code, with no student code running at that moment, is
 * written with the token in front. Student code cannot add the token,
 * because it never sees it. The parent then trusts only marked lines.
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

    // Any line break: \n, \r, \r\n and the Unicode ones
    private static final String LINE_BREAK = "\\R";

    // A single whole line splits into the line and an empty end
    private static final int LINE_AND_END = 2;

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

    /**
     * Standard output that marks the lines printed by the tester itself.
     * Only the calls the provided testers use for test headers, verdicts
     * and the total are marked; every other line passes through unmarked.
     */
    private static final class TrustedPrintStream extends PrintStream {

        // The secret written in front of each trusted line
        private final String token;

        // The thread that runs the tester
        private final Thread testerThread;

        // The tester class; its own code is trusted
        private final Class<?> tester;

        // Lets the stream see which classes are calling it
        private final StackWalker walker = StackWalker.getInstance(
                StackWalker.Option.RETAIN_CLASS_REFERENCE);

        /**
         * Creates a stream over the real standard output.
         *
         * @param out the real standard output
         * @param token the secret token
         * @param testerThread the thread that runs the tester
         * @param tester the tester class
         */
        TrustedPrintStream(PrintStream out, String token,
                Thread testerThread, Class<?> tester) {
            super(out, true);
            this.token = token;
            this.testerThread = testerThread;
            this.tester = tester;
        }

        @Override
        public void println(String line) {
            if (isTrustedCaller() && isOneLine(line)) {
                printMarked(line);
            } else {
                super.println(line);
            }
        }

        @Override
        public void println(double value) {
            if (isTrustedCaller()) {
                printMarked(String.valueOf(value));
            } else {
                super.println(value);
            }
        }

        @Override
        public PrintStream format(String format, Object... args) {
            return format(Locale.getDefault(Locale.Category.FORMAT), format,
                    args);
        }

        @Override
        public PrintStream format(Locale locale, String format,
                Object... args) {
            // Decided before the arguments are turned into text, because
            // that can run student code such as a toString method
            boolean trustedCaller = isTrustedCaller();
            String text = String.format(locale, format, args);
            String[] parts = text.split(LINE_BREAK, -1);

            // A student's value inside the text could hold a line break
            // and a fake verdict, so only a single whole line is marked
            if (trustedCaller && parts.length == LINE_AND_END
                    && parts[1].isEmpty()) {
                printMarked(parts[0]);
            } else {
                super.print(text);
            }
            return this;
        }

        /**
         * Prints one line with the token in front.
         *
         * @param line the line, without a line break
         */
        void printMarked(String line) {
            super.print(token + line + System.lineSeparator());
        }

        /**
         * Says whether the text holds no line break.
         *
         * @param text the text
         * @return true if it is a single line
         */
        private boolean isOneLine(String text) {
            return text != null && text.split(LINE_BREAK, -1).length == 1;
        }

        /**
         * Says whether only the tester and Java itself are running on the
         * tester's thread, with no student code anywhere in the call stack.
         *
         * @return true if the print comes from the tester itself
         */
        private boolean isTrustedCaller() {
            if (Thread.currentThread() != testerThread) {
                return false;
            }
            return walker.walk(frames -> frames.allMatch(
                    frame -> isTrustedClass(frame.getDeclaringClass())));
        }

        /**
         * Says whether a class is part of Java, this harness or the tester.
         * Classes nested in the tester count as the tester; student classes
         * and classes from any other class loader do not.
         *
         * @param type the class of one frame in the call stack
         * @return true if the class is trusted
         */
        private boolean isTrustedClass(Class<?> type) {
            ClassLoader loader = type.getClassLoader();
            if (loader == null
                    || loader == ClassLoader.getPlatformClassLoader()) {
                return true;
            }
            Class<?> host = type.getNestHost();
            return loader == TesterHarness.class.getClassLoader()
                    && (host == tester || host == TesterHarness.class);
        }
    }
}
