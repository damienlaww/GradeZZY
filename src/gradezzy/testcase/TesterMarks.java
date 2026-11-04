/*
 * File: TesterMarks.java
 *
 * Purpose: Works out a question's maximum mark from its tester file.
 *
 * Where it fits: Used by AssessmentFactory, so the maximum of each question
 * comes from the testers of the batch instead of being typed into
 * config.properties.
 */
package gradezzy.testcase;

import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;

/**
 * Works out a question's maximum mark from its tester file.
 *
 * <p>The provided testers add one mark per test case with a line such as
 * {@code score += 1;}. The maximum is the sum of those numbers, so a test
 * case worth 2 marks also counts correctly.
 *
 * <p>Tier: MUST. Owner: Zach.
 */
public final class TesterMarks {

    /**
     * Returned when the tester has no {@code score += <number>} line, so
     * its maximum mark is unknown.
     */
    public static final double UNKNOWN = 0.0;

    // What a mark line starts with once its spaces are removed
    private static final String SCORE_ADD = "score+=";

    // A line comment, which is never counted
    private static final String LINE_COMMENT = "//";

    // The end of the statement that adds the mark
    private static final String STATEMENT_END = ";";

    // A utility class is never created
    private TesterMarks() {
    }

    /**
     * Adds up the numbers of every {@code score += <number>;} line in a
     * tester source file.
     *
     * <p>A line that adds something other than a plain number, such as
     * {@code score += weight;}, is not counted.
     *
     * @param testerSource the tester's .java file
     * @return the maximum mark, or UNKNOWN when there is no such line
     * @throws IOException if the file cannot be read
     */
    public static double readMaxScore(Path testerSource) throws IOException {
        // ISO-8859-1 reads every byte, so a tester saved as Windows-1252
        // still opens; the mark lines themselves are plain ASCII
        String text = new String(Files.readAllBytes(testerSource),
                StandardCharsets.ISO_8859_1);

        double total = UNKNOWN;
        for (String line : text.split("\\R")) {
            total += readMark(line);
        }
        return total;
    }

    // The number one line adds to the score, or 0 if it adds none
    private static double readMark(String line) {
        String compact = removeSpaces(line);
        if (compact.startsWith(LINE_COMMENT)) {
            return 0.0;
        }

        int start = compact.indexOf(SCORE_ADD);
        if (start < 0 || isPartOfLongerName(compact, start)) {
            return 0.0;
        }

        int amountStart = start + SCORE_ADD.length();
        int amountEnd = compact.indexOf(STATEMENT_END, amountStart);
        if (amountEnd < 0) {
            return 0.0;
        }

        try {
            return Double.parseDouble(compact.substring(amountStart,
                    amountEnd));
        } catch (NumberFormatException exception) {
            // Not a plain number, for example score += weight
            return 0.0;
        }
    }

    // Says whether "score" is the end of a longer name such as totalscore
    private static boolean isPartOfLongerName(String compact, int start) {
        return start > 0
                && Character.isJavaIdentifierPart(compact.charAt(start - 1));
    }

    // Removes the spaces and tabs, so "score += 1" and "score+=1" match
    private static String removeSpaces(String line) {
        String compact = "";
        for (int index = 0; index < line.length(); index++) {
            char current = line.charAt(index);
            if (!Character.isWhitespace(current)) {
                compact += current;
            }
        }
        return compact;
    }
}
