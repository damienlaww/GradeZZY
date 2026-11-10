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
import java.util.List;
import java.util.Locale;

/**
 * Standard output that marks the lines printed by the tester itself.
 * Every print, println and printf the tester calls is marked, so a total
 * printed as an int, with print, or as an Object is read like one printed
 * with println(double). Text printed with print waits until its line is
 * complete, and only a single whole line is marked. Every other line
 * passes through unmarked. Unmarked output stops once it reaches its
 * limit, and a marked line longer than MAX_MARKED_LINE_LENGTH is cut,
 * so neither a student who prints a lot nor a huge wrong answer the
 * tester echoes can push the tester's later lines past the output limit
 * of ProcessRunner.
 *
 * <p>Tier: MUST. Owner: Zach.
 */
class TrustedPrintStream extends PrintStream {

    // Any line break: \n, \r, \r\n and the Unicode ones
    private static final String LINE_BREAK = "\\R";

    // A single whole line splits into the line and an empty end
    private static final int LINE_AND_END = 2;

    // Split limit that keeps the empty parts at the end of the text
    private static final int KEEP_EMPTY_PARTS = -1;

    // Where the empty end of a single whole line is in its parts
    private static final int END_PART = 1;

    // The longest marked line kept whole. A longer one, such as a wrong
    // answer of 300 000 characters echoed in "Actual :|...|", is cut, so
    // the tester's later lines still fit in exec.max.output.characters
    private static final int MAX_MARKED_LINE_LENGTH = 1000;

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

    // Start of the line the tester prints before each test case
    private final String headerMarker;

    // The test cases to skip, counted from 1
    private final List<Integer> skippedCases;

    // How many test cases the tester has started so far
    private int casesStarted = 0;

    // Bytes of unmarked output passed on so far
    private long studentBytes = 0;

    // True while a marked line is being written
    private boolean writingMarked = false;

    // Text the tester printed with print that still waits for the end of
    // its line; only the tester's thread uses it
    private String pendingLine = "";

    /**
     * Creates a stream over the real standard output.
     *
     * @param out the real standard output
     * @param token the secret token
     * @param testerThread the thread that runs the tester
     * @param tester the tester class
     * @param studentLimit the most bytes of unmarked output to pass on
     * @param headerMarker the start of a test case's header line
     * @param skippedCases the test cases to skip, counted from 1
     */
    TrustedPrintStream(PrintStream out, String token,
            Thread testerThread, Class<?> tester, long studentLimit,
            String headerMarker, List<Integer> skippedCases) {
        super(out, true);
        this.token = token;
        this.testerThread = testerThread;
        this.tester = tester;
        this.studentLimit = studentLimit;
        this.headerMarker = headerMarker;
        this.skippedCases = skippedCases;
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
        long roomLeft = Math.max(room, 0);
        int kept = (int) Math.min(roomLeft, length);
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
     * Prints text, marked if the tester itself printed it.
     *
     * @param text the text
     */
    @Override
    public void print(String text) {
        printText(isTrustedCaller(), String.valueOf(text));
    }

    /**
     * Prints a value, marked if the tester itself printed it. Only a
     * value of a final Java type such as Double is marked: the text of
     * any other object comes from its toString method, which can be
     * student code.
     *
     * @param value the value
     */
    @Override
    public void print(Object value) {
        // Decided before toString runs, because that can be student code
        boolean trusted = isTrustedCaller() && isPlainValue(value);
        printText(trusted, String.valueOf(value));
    }

    /**
     * Prints a number, marked if the tester itself printed it.
     *
     * @param value the number
     */
    @Override
    public void print(double value) {
        printText(isTrustedCaller(), String.valueOf(value));
    }

    /**
     * Prints a number, marked if the tester itself printed it.
     *
     * @param value the number
     */
    @Override
    public void print(float value) {
        printText(isTrustedCaller(), String.valueOf(value));
    }

    /**
     * Prints a number, marked if the tester itself printed it.
     *
     * @param value the number
     */
    @Override
    public void print(int value) {
        printText(isTrustedCaller(), String.valueOf(value));
    }

    /**
     * Prints a number, marked if the tester itself printed it.
     *
     * @param value the number
     */
    @Override
    public void print(long value) {
        printText(isTrustedCaller(), String.valueOf(value));
    }

    /**
     * Prints true or false, marked if the tester itself printed it.
     *
     * @param value the value
     */
    @Override
    public void print(boolean value) {
        printText(isTrustedCaller(), String.valueOf(value));
    }

    /**
     * Prints a character, marked if the tester itself printed it.
     *
     * @param value the character
     */
    @Override
    public void print(char value) {
        printText(isTrustedCaller(), String.valueOf(value));
    }

    /**
     * Prints characters, marked if the tester itself printed them.
     *
     * @param value the characters
     */
    @Override
    public void print(char[] value) {
        printText(isTrustedCaller(), String.valueOf(value));
    }

    /**
     * Ends the current line. A line the tester started with print is
     * marked now; any other line break passes through as before.
     */
    @Override
    public void println() {
        boolean trusted = isTrustedCaller();
        if (trusted && !pendingLine.isEmpty()) {
            printText(true, System.lineSeparator());
        } else {
            super.println();
        }
    }

    /**
     * Prints a line, marked if the tester itself printed it.
     *
     * @param line the line
     */
    @Override
    public void println(String line) {
        printLine(isTrustedCaller(), String.valueOf(line));
    }

    /**
     * Prints a value on its own line, marked if the tester itself printed
     * it and it is a value of a final Java type such as Double.
     *
     * @param value the value
     */
    @Override
    public void println(Object value) {
        // Decided before toString runs, because that can be student code
        boolean trusted = isTrustedCaller() && isPlainValue(value);
        printLine(trusted, String.valueOf(value));
    }

    /**
     * Prints a number on its own line, marked if the tester itself printed
     * it. The provided testers print their total score this way.
     *
     * @param value the number
     */
    @Override
    public void println(double value) {
        printLine(isTrustedCaller(), String.valueOf(value));
    }

    /**
     * Prints a number on its own line, marked if the tester itself printed
     * it.
     *
     * @param value the number
     */
    @Override
    public void println(float value) {
        printLine(isTrustedCaller(), String.valueOf(value));
    }

    /**
     * Prints a number on its own line, marked if the tester itself printed
     * it, such as a total kept in an int.
     *
     * @param value the number
     */
    @Override
    public void println(int value) {
        printLine(isTrustedCaller(), String.valueOf(value));
    }

    /**
     * Prints a number on its own line, marked if the tester itself printed
     * it.
     *
     * @param value the number
     */
    @Override
    public void println(long value) {
        printLine(isTrustedCaller(), String.valueOf(value));
    }

    /**
     * Prints true or false on its own line, marked if the tester itself
     * printed it.
     *
     * @param value the value
     */
    @Override
    public void println(boolean value) {
        printLine(isTrustedCaller(), String.valueOf(value));
    }

    /**
     * Prints a character on its own line, marked if the tester itself
     * printed it.
     *
     * @param value the character
     */
    @Override
    public void println(char value) {
        printLine(isTrustedCaller(), String.valueOf(value));
    }

    /**
     * Prints characters on their own line, marked if the tester itself
     * printed them.
     *
     * @param value the characters
     */
    @Override
    public void println(char[] value) {
        printLine(isTrustedCaller(), String.valueOf(value));
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
     * Formats text, marked if the tester itself printed it. The signature
     * is fixed by PrintStream.
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
        printText(trustedCaller, text);
        return this;
    }

    /**
     * Prints text and a line break, through printText.
     *
     * @param trusted true if the tester itself printed it
     * @param text the text, without the line break
     */
    private void printLine(boolean trusted, String text) {
        printText(trusted, text + System.lineSeparator());
    }

    /**
     * Prints text. Text from anyone but the tester passes through
     * unmarked. The tester's text is added to the line it started with
     * print; once that line ends it is marked. A student's value inside
     * the text could hold a line break and a fake verdict, so text that
     * holds more than one whole line is printed unmarked instead.
     *
     * @param trusted true if the tester itself printed it
     * @param text the text
     */
    private void printText(boolean trusted, String text) {
        if (!trusted) {
            super.print(text);
            return;
        }

        String line = pendingLine + text;
        String[] parts = line.split(LINE_BREAK, KEEP_EMPTY_PARTS);
        if (parts.length == 1) {
            // No line break yet: wait for the rest of the line
            pendingLine = line;
            return;
        }

        pendingLine = "";
        boolean oneWholeLine = parts.length == LINE_AND_END
                && parts[END_PART].isEmpty();
        if (oneWholeLine) {
            printTesterLine(parts[0]);
        } else {
            super.print(line);
        }
    }

    /**
     * Marks and prints the line the tester started with print but never
     * ended, such as a total printed with print. The harness calls this
     * when the tester stops.
     */
    void endPendingLine() {
        if (pendingLine.isEmpty()) {
            return;
        }

        String line = pendingLine;
        pendingLine = "";
        printMarked(line);
    }

    /**
     * Says whether a value prints the same whoever wrote the code: null,
     * a String, a boxed number, a Boolean or a Character. These classes
     * are final, so a student class cannot extend them and change their
     * toString.
     *
     * @param value the value
     * @return true if its text comes from Java itself
     */
    private boolean isPlainValue(Object value) {
        return value == null || value instanceof String
                || value instanceof Double || value instanceof Float
                || value instanceof Integer || value instanceof Long
                || value instanceof Short || value instanceof Byte
                || value instanceof Boolean || value instanceof Character;
    }

    /**
     * Prints a line of the tester. Each tester prints a case's header
     * inside the case's try block, just before it calls the student's
     * code, so throwing here skips that call: the tester's own catch
     * fails the case and it goes on to the next one.
     *
     * @param line the line, without a line break
     */
    private void printTesterLine(String line) {
        printMarked(line);
        if (TesterHarness.isCaseHeader(line, headerMarker)) {
            casesStarted++;
            if (skippedCases.contains(casesStarted)) {
                throw new SkippedCaseException();
            }
        }
    }

    /**
     * Prints one line with the token in front. A marked line is never cut
     * off by the limit on unmarked output, but a line longer than
     * MAX_MARKED_LINE_LENGTH is cut to that length, with a note on how
     * much was cut.
     *
     * @param line the line, without a line break
     */
    synchronized void printMarked(String line) {
        String kept = line;
        if (line.length() > MAX_MARKED_LINE_LENGTH) {
            int cut = line.length() - MAX_MARKED_LINE_LENGTH;
            kept = line.substring(0, MAX_MARKED_LINE_LENGTH) + " ["
                    + cut + " characters cut]";
        }
        writingMarked = true;
        try {
            super.print(token + kept + System.lineSeparator());
            super.flush();
        } finally {
            writingMarked = false;
        }
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
