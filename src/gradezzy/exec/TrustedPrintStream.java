/*
 * File: TrustedPrintStream.java
 *
 * Purpose: Standard output inside the student's java process that marks
 * the lines printed by the tester itself.
 *
 * Where it fits: TesterHarness puts this stream in place of System.out
 * before the tester runs. TesterRunner later trusts only the marked lines.
 */
package gradezzy.exec;

import java.io.PrintStream;
import java.util.Locale;

/**
 * Standard output that marks the lines printed by the tester itself.
 * Only the calls the provided testers use for test headers, verdicts and
 * the total are marked; every other line passes through unmarked.
 * Unmarked output stops once it reaches its limit, so a student who
 * prints a lot cannot push the tester's later lines past the output
 * limit of ProcessRunner.
 *
 * <p>Tier: MUST. Owner: Zach.
 */
class TrustedPrintStream extends PrintStream {

    // Any line break: \n, \r, \r\n and the Unicode ones
    private static final String LINE_BREAK = "\\R";

    // A single whole line splits into the line and an empty end
    private static final int LINE_AND_END = 2;

    // The secret written in front of each trusted line
    private final String token;

    // The thread that runs the tester
    private final Thread testerThread;

    // The tester class; its own code is trusted
    private final Class<?> tester;

    // Lets the stream see which classes are calling it
    private final StackWalker walker = StackWalker.getInstance(
            StackWalker.Option.RETAIN_CLASS_REFERENCE);

    // The most bytes of unmarked output passed on
    private final long studentLimit;

    // Bytes of unmarked output passed on so far
    private long studentBytes = 0;

    // True while a marked line is being written
    private boolean writingMarked = false;

    /**
     * Creates a stream over the real standard output.
     *
     * @param out the real standard output
     * @param token the secret token
     * @param testerThread the thread that runs the tester
     * @param tester the tester class
     * @param studentLimit the most bytes of unmarked output to pass on
     */
    TrustedPrintStream(PrintStream out, String token,
            Thread testerThread, Class<?> tester, long studentLimit) {
        super(out, true);
        this.token = token;
        this.testerThread = testerThread;
        this.tester = tester;
        this.studentLimit = studentLimit;
    }

    /**
     * Every print ends here as bytes. Marked lines always pass; unmarked
     * bytes pass only while they are under the limit.
     *
     * @param buffer the bytes
     * @param offset where the bytes start
     * @param length how many bytes
     */
    @Override
    public synchronized void write(byte[] buffer, int offset, int length) {
        if (writingMarked) {
            super.write(buffer, offset, length);
            return;
        }
        long room = studentLimit - studentBytes;
        int kept = (int) Math.min(Math.max(room, 0), length);
        if (kept > 0) {
            super.write(buffer, offset, kept);
            studentBytes += kept;
        }
    }

    /**
     * Writes one byte, under the same limit as the other writes.
     *
     * @param oneByte the byte
     */
    @Override
    public void write(int oneByte) {
        write(new byte[] {(byte) oneByte}, 0, 1);
    }

    /**
     * Prints a line, marked if the tester itself printed it.
     *
     * @param line the line
     */
    @Override
    public void println(String line) {
        if (isTrustedCaller() && isOneLine(line)) {
            printMarked(line);
        } else {
            super.println(line);
        }
    }

    /**
     * Prints a number on its own line, marked if the tester itself printed
     * it. The testers print their total score this way.
     *
     * @param value the number
     */
    @Override
    public void println(double value) {
        if (isTrustedCaller()) {
            printMarked(String.valueOf(value));
        } else {
            super.println(value);
        }
    }

    /**
     * Formats text in the default locale; printf comes here too. The
     * signature, including the variable argument list, is fixed by
     * PrintStream, so it cannot be changed to an array parameter.
     *
     * @param format the format text
     * @param args the values for the format
     * @return this stream
     */
    @Override
    public PrintStream format(String format, Object... args) {
        return format(Locale.getDefault(Locale.Category.FORMAT), format,
                args);
    }

    /**
     * Formats text, marked if the tester itself printed it and it is one
     * whole line. The signature is fixed by PrintStream.
     *
     * @param locale the locale for numbers
     * @param format the format text
     * @param args the values for the format
     * @return this stream
     */
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
     * Prints one line with the token in front. A marked line is never cut
     * off by the limit on unmarked output.
     *
     * @param line the line, without a line break
     */
    synchronized void printMarked(String line) {
        writingMarked = true;
        try {
            super.print(token + line + System.lineSeparator());
            super.flush();
        } finally {
            writingMarked = false;
        }
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
     * The walker hands every frame of the call stack, from this method
     * down, to a TrustedFrameCheck.
     *
     * @return true if the print comes from the tester itself
     */
    private boolean isTrustedCaller() {
        if (Thread.currentThread() != testerThread) {
            return false;
        }
        TrustedFrameCheck check = new TrustedFrameCheck(tester);
        walker.forEach(check);
        return check.isAllTrusted();
    }
}
