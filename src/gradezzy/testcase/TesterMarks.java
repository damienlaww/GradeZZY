/*
 * File: TesterMarks.java
 *
 * Purpose: Works out a question's maximum mark from its tester file, and
 * says when it cannot be worked out safely.
 *
 * Where it fits: Used by AssessmentFactory and the assessment editor, so
 * the maximum of each question comes from the testers of the batch
 * instead of being typed into config.properties.
 */
package gradezzy.testcase;

import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.Collections;
import java.util.List;

/**
 * Works out a question's maximum mark from its tester file.
 *
 * <p>The provided testers add one mark per test case with a statement such
 * as {@code score += 1;}. The maximum is the sum of those numbers, so a
 * test case worth 2 marks also counts correctly. Comments and text in
 * quotes are ignored.
 *
 * <p>Any other change to {@code score} cannot be counted by reading the
 * file: {@code score = score + 1}, {@code score++}, {@code score += MARK},
 * or a {@code score +=} inside a loop. Such a tester is reported by
 * findProblems instead of being counted short, and readMaxScore gives
 * UNKNOWN for it.
 *
 * <p>Tier: MUST. Owner: Zach.
 */
public final class TesterMarks {

    /**
     * Returned when the maximum mark cannot be worked out: the tester has
     * no {@code score += <number>} statement, or changes the score in a
     * way that cannot be counted.
     */
    public static final double UNKNOWN = 0.0;

    // The name of the tester's mark field
    private static final String SCORE = "score";

    // Words that start a loop; a score change inside one cannot be counted
    private static final String[] LOOP_WORDS = {"for", "while", "do"};

    // What the reader is inside while it removes comments and text
    private static final int CODE = 0;
    private static final int LINE_COMMENT = 1;
    private static final int BLOCK_COMMENT = 2;
    private static final int TEXT = 3;
    private static final int CHARACTER = 4;

    // The characters that start and end comments
    private static final char SLASH = '/';
    private static final char STAR = '*';

    // The quotes around a text and around a character
    private static final char DOUBLE_QUOTE = '"';
    private static final char SINGLE_QUOTE = '\'';

    // Starts an escaped character inside quotes, such as \"
    private static final char BACKSLASH = '\\';

    // Ends a line of the tester
    private static final char LINE_END = '\n';

    // Replaces a blanked character; also stands in after the last one
    private static final char BLANK = ' ';

    // The characters skipped between "score" and its operator
    private static final char SPACE = ' ';
    private static final char TAB = '\t';

    // The brackets followed to know when a loop starts and ends
    private static final char OPEN_PARENTHESIS = '(';
    private static final char CLOSE_PARENTHESIS = ')';
    private static final char OPEN_BRACE = '{';
    private static final char CLOSE_BRACE = '}';

    // Ends a statement
    private static final char SEMICOLON = ';';

    // The two characters other than letters and digits a Java name holds
    private static final char UNDERSCORE = '_';
    private static final char DOLLAR = '$';

    // The point of a decimal number such as 2.5
    private static final char DECIMAL_POINT = '.';

    // The most points a plain number can have
    private static final int MAX_POINTS = 1;

    // The operators that change the score in a way that cannot be counted
    private static final String INCREMENT = "++";
    private static final String DECREMENT = "--";
    private static final String SUBTRACT_FROM = "-=";
    private static final String MULTIPLY_BY = "*=";
    private static final String DIVIDE_BY = "/=";

    // The operator of a countable statement such as score += 1
    private static final String ADD_TO = "+=";

    // Sets the score; the comparison starts the same way
    private static final String ASSIGN = "=";
    private static final String EQUALS = "==";

    // Comes between "score" and the rest of a statement in a message
    private static final String WORD_SEPARATOR = " ";

    // Comes between the file name and the line number in a message
    private static final String LINE_LABEL = " line ";

    // A utility class is never created
    private TesterMarks() {
    }

    /**
     * Adds up the numbers of every {@code score += <number>;} statement in
     * a tester source file.
     *
     * @param testerSource the tester's .java file
     * @return the maximum mark, or UNKNOWN when there is no such statement
     *         or findProblems reports a problem
     * @throws IOException if the file cannot be read
     */
    public static double readMaxScore(Path testerSource) throws IOException {
        List<String> problems = new ArrayList<>();
        String code = readCode(testerSource);
        String fileName = fileNameOf(testerSource);
        List<Double> marks = new ArrayList<>();
        double total = countMarks(code, fileName, problems, marks);
        if (!problems.isEmpty()) {
            return UNKNOWN;
        }
        return total;
    }

    /**
     * Adds up the largest of a tester's marks: the most that a number of
     * passed cases can be worth, whichever cases they were.
     *
     * @param caseMarks the tester's marks, as readCaseMarks gives them
     * @param passes the number of passed cases
     * @return the sum of the largest marks, one per passed case
     */
    public static double sumLargest(List<Double> caseMarks, int passes) {
        List<Double> marks = new ArrayList<>(caseMarks);
        Collections.sort(marks, Collections.reverseOrder());

        double total = 0.0;
        for (int index = 0; index < passes && index < marks.size(); index++) {
            total += marks.get(index);
        }
        return total;
    }

    /**
     * Lists the number of every {@code score += <number>;} statement in a
     * tester, in the order they appear. In the provided testers each test
     * case has one such statement, so the first number is what case 1 is
     * worth, the second what case 2 is worth, and so on.
     *
     * @param testerSource the tester's .java file
     * @return the marks in the order they appear; empty when there are none
     * @throws IOException if the file cannot be read
     */
    public static List<Double> readCaseMarks(Path testerSource)
            throws IOException {
        List<Double> marks = new ArrayList<>();
        String code = readCode(testerSource);
        String fileName = fileNameOf(testerSource);
        List<String> problems = new ArrayList<>();
        countMarks(code, fileName, problems, marks);
        return marks;
    }

    /**
     * Lists why a tester's maximum mark cannot be worked out: one sentence
     * for each change to the score that cannot be counted, or one sentence
     * when the tester never adds to the score.
     *
     * @param testerSource the tester's .java file
     * @return one sentence per problem, empty when the maximum is known
     * @throws IOException if the file cannot be read
     */
    public static List<String> findProblems(Path testerSource)
            throws IOException {
        List<String> problems = new ArrayList<>();
        String fileName = fileNameOf(testerSource);
        String code = readCode(testerSource);
        List<Double> marks = new ArrayList<>();
        double total = countMarks(code, fileName, problems, marks);
        boolean noMarks = problems.isEmpty() && total <= UNKNOWN;
        if (noMarks) {
            problems.add(fileName + " has no \"score += <number>;\" line, "
                    + "so its question has no maximum mark. Add one per "
                    + "test case.");
        }
        return problems;
    }

    /**
     * Reads the tester with its comments and quoted text blanked out.
     * ISO-8859-1 reads every byte, so a tester saved as Windows-1252 still
     * opens; the code that changes the score is plain ASCII.
     *
     * @param testerSource the tester's .java file
     * @return the code of the tester
     * @throws IOException if the file cannot be read
     */
    public static String readCode(Path testerSource) throws IOException {
        byte[] bytes = Files.readAllBytes(testerSource);
        String source = new String(bytes, StandardCharsets.ISO_8859_1);
        return removeCommentsAndText(source);
    }

    // The file's name, such as Q1aTester.java, for a message
    private static String fileNameOf(Path testerSource) {
        return testerSource.getFileName().toString();
    }

    // Replaces comments and the text inside quotes with spaces. Line
    // breaks are kept, so line numbers stay right.
    private static String removeCommentsAndText(String source) {
        char[] code = source.toCharArray();
        int state = CODE;
        int index = 0;
        while (index < code.length) {
            char current = code[index];
            int nextIndex = index + 1;
            char next = BLANK;
            if (nextIndex < code.length) {
                next = code[nextIndex];
            }

            if (state == CODE) {
                state = stateAfterCode(code, index, next);
            } else if (state == LINE_COMMENT) {
                if (current == LINE_END) {
                    state = CODE;
                } else {
                    code[index] = BLANK;
                }
            } else if (state == BLOCK_COMMENT) {
                if (current == STAR && next == SLASH) {
                    code[index] = BLANK;
                    code[nextIndex] = BLANK;
                    index++;
                    state = CODE;
                } else if (current != LINE_END) {
                    code[index] = BLANK;
                }
            } else {
                index = blankQuoted(code, index, state);
                state = CODE;
            }
            index++;
        }
        return new String(code);
    }

    // Returns what a character of code starts: a comment, a quoted text
    // or character, or nothing. The first slash of a comment is blanked.
    private static int stateAfterCode(char[] code, int index, char next) {
        char current = code[index];
        if (current == SLASH && next == SLASH) {
            code[index] = BLANK;
            return LINE_COMMENT;
        }
        if (current == SLASH && next == STAR) {
            code[index] = BLANK;
            return BLOCK_COMMENT;
        }
        if (current == DOUBLE_QUOTE) {
            return TEXT;
        }
        if (current == SINGLE_QUOTE) {
            return CHARACTER;
        }
        return CODE;
    }

    // Blanks the inside of a quoted text or character, starting just
    // after its opening quote, and returns the index of the closing quote
    private static int blankQuoted(char[] code, int start, int state) {
        char quote = DOUBLE_QUOTE;
        if (state == CHARACTER) {
            quote = SINGLE_QUOTE;
        }

        int index = start;
        while (isInsideQuote(code, index, quote)) {
            int nextIndex = index + 1;
            boolean escaped = code[index] == BACKSLASH
                    && nextIndex < code.length;
            if (escaped) {
                // An escaped character such as \" belongs to the text
                code[index] = BLANK;
                index++;
            }
            code[index] = BLANK;
            index++;
        }
        return index;
    }

    // Says whether an index is still inside a quoted text or character:
    // not past the end, at the closing quote or at the end of the line
    private static boolean isInsideQuote(char[] code, int index,
            char quote) {
        if (index >= code.length) {
            return false;
        }
        boolean closing = code[index] == quote;
        boolean lineEnds = code[index] == LINE_END;
        return !closing && !lineEnds;
    }

    // Adds up the score += <number> statements and lists every other
    // change to the score. Braces are followed so that a change inside a
    // loop is caught. Each mark found is also added to marks.
    private static double countMarks(String code, String fileName,
            List<String> problems, List<Double> marks) {
        double total = 0.0;
        int lineNumber = 1;
        int parenDepth = 0;
        int loopDepth = 0;
        boolean loopStarting = false;
        List<Boolean> braceIsLoop = new ArrayList<>();

        int index = 0;
        while (index < code.length()) {
            char current = code.charAt(index);
            int next = index + 1;
            if (isWordStart(code, index)) {
                int wordEnd = findWordEnd(code, index);
                String word = code.substring(index, wordEnd);
                if (isLoopWord(word)) {
                    loopStarting = true;
                } else if (word.equals(SCORE)) {
                    boolean inLoop = loopDepth > 0 || loopStarting;
                    String where = fileName + LINE_LABEL + lineNumber;
                    double mark = readChange(code, index, wordEnd, inLoop,
                            where, problems);
                    total += mark;
                    if (mark > 0) {
                        marks.add(mark);
                    }
                }
                next = wordEnd;
            } else if (current == LINE_END) {
                lineNumber++;
            } else if (current == OPEN_PARENTHESIS) {
                parenDepth++;
            } else if (current == CLOSE_PARENTHESIS) {
                parenDepth--;
            } else if (current == OPEN_BRACE) {
                braceIsLoop.add(loopStarting);
                if (loopStarting) {
                    loopDepth++;
                }
                loopStarting = false;
            } else if (current == CLOSE_BRACE && !braceIsLoop.isEmpty()) {
                int lastBrace = braceIsLoop.size() - 1;
                boolean closesLoop = braceIsLoop.remove(lastBrace);
                if (closesLoop) {
                    loopDepth--;
                }
            } else if (current == SEMICOLON && parenDepth == 0) {
                // A loop with no braces ends with its one statement
                loopStarting = false;
            }
            index = next;
        }
        return total;
    }

    // Reads what the code does to the score just after the word "score".
    // Returns the marks a countable statement adds, and adds a problem
    // for a change that cannot be counted. Reading the score, as in
    // println(score), is neither.
    private static double readChange(String code, int wordStart,
            int wordEnd, boolean inLoop, String where,
            List<String> problems) {
        String before = code.substring(0, wordStart).trim();
        int operatorStart = skipSpaces(code, wordEnd);
        String after = code.substring(operatorStart);
        String statement = SCORE + WORD_SEPARATOR + readUntilEnd(after);

        boolean stepsBefore = before.endsWith(INCREMENT)
                || before.endsWith(DECREMENT);
        boolean stepsAfter = after.startsWith(INCREMENT)
                || after.startsWith(DECREMENT);
        boolean otherOperator = after.startsWith(SUBTRACT_FROM)
                || after.startsWith(MULTIPLY_BY)
                || after.startsWith(DIVIDE_BY);
        boolean uncountable = stepsBefore || stepsAfter || otherOperator;
        if (uncountable) {
            problems.add(cannotCount(where, statement));
            return 0.0;
        } else if (after.startsWith(ADD_TO)) {
            String added = after.substring(ADD_TO.length());
            String amount = readUntilEnd(added).trim();
            if (inLoop) {
                problems.add(where + " adds to the score inside a loop (\""
                        + statement + "\"), so GradeZZY cannot tell how "
                        + "many times it runs.");
                return 0.0;
            } else if (!isPlainNumber(amount)) {
                problems.add(cannotCount(where, statement));
                return 0.0;
            }
            return Double.parseDouble(amount);
        } else if (after.startsWith(ASSIGN) && !after.startsWith(EQUALS)) {
            // Setting the score back to 0 adds no marks
            String assigned = after.substring(ASSIGN.length());
            String value = readUntilEnd(assigned).trim();
            if (!isZero(value)) {
                problems.add(cannotCount(where, statement));
            }
        }
        return 0.0;
    }

    // The sentence for a score change that cannot be counted
    private static String cannotCount(String where, String statement) {
        return where + " changes the score with \"" + statement + "\", "
                + "which GradeZZY cannot count. Use \"score += <number>;\" "
                + "once per test case.";
    }

    // The text up to the end of the statement, without the semicolon
    private static String readUntilEnd(String text) {
        int end = text.indexOf(SEMICOLON);
        int lineEnd = text.indexOf(LINE_END);
        boolean lineEndsFirst = lineEnd >= 0 && lineEnd < end;
        boolean useLineEnd = end < 0 || lineEndsFirst;
        if (useLineEnd) {
            end = lineEnd;
        }
        if (end < 0) {
            return text.trim();
        }
        return text.substring(0, end).trim();
    }

    // Says whether text is a plain number such as 1 or 2.5
    private static boolean isPlainNumber(String text) {
        int digits = 0;
        int points = 0;
        for (int index = 0; index < text.length(); index++) {
            char current = text.charAt(index);
            if (Character.isDigit(current)) {
                digits++;
            } else if (current == DECIMAL_POINT) {
                points++;
            } else {
                return false;
            }
        }
        return digits > 0 && points <= MAX_POINTS;
    }

    // Says whether text is a plain number equal to 0, such as 0 or 0.0
    private static boolean isZero(String text) {
        return isPlainNumber(text) && Double.parseDouble(text) == 0.0;
    }

    // Says whether a word starts at index: a letter, _ or $ that does not
    // continue the word before it
    private static boolean isWordStart(String code, int index) {
        char current = code.charAt(index);
        boolean letter = Character.isLetter(current);
        boolean starts = letter || current == UNDERSCORE || current == DOLLAR;
        if (!starts) {
            return false;
        }
        if (index == 0) {
            return true;
        }
        int before = index - 1;
        return !isWordPart(code.charAt(before));
    }

    // Returns the index just after the word that starts at index
    private static int findWordEnd(String code, int index) {
        int end = index;
        while (end < code.length() && isWordPart(code.charAt(end))) {
            end++;
        }
        return end;
    }

    // Says whether a character can be part of a Java name
    private static boolean isWordPart(char character) {
        boolean letterOrDigit = Character.isLetterOrDigit(character);
        return letterOrDigit || character == UNDERSCORE
                || character == DOLLAR;
    }

    // Says whether a word starts a loop
    private static boolean isLoopWord(String word) {
        for (String loopWord : LOOP_WORDS) {
            if (loopWord.equals(word)) {
                return true;
            }
        }
        return false;
    }

    // Returns the index of the first character at or after index that is
    // not a space or a tab
    private static int skipSpaces(String code, int index) {
        int position = index;
        while (isSpaceAt(code, position)) {
            position++;
        }
        return position;
    }

    // Says whether the character at a position is a space or a tab;
    // nothing is past the end
    private static boolean isSpaceAt(String code, int position) {
        if (position >= code.length()) {
            return false;
        }
        char current = code.charAt(position);
        return current == SPACE || current == TAB;
    }
}
