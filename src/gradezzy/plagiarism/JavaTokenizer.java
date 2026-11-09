/*
 * File: JavaTokenizer.java
 *
 * Purpose: Splits Java source into tokens, skipping spaces and comments,
 * and gives the shape of a piece of code: its tokens with every name and
 * text literal replaced, so renaming variables does not change it.
 *
 * Where it fits: TokenSimilarityDetector builds its trigrams from the
 * tokens; Winnowing builds its rename-proof k-grams from shapeToken;
 * AnswerGrouper compares shapes to tell the marker which students in an
 * answer group wrote the same code. tokenize also keeps each token's
 * position and line, for the tester strength check and for the lines
 * LineComparison marks.
 */
package gradezzy.plagiarism;

import java.util.ArrayList;
import java.util.Arrays;
import java.util.HashSet;
import java.util.List;
import java.util.Set;

import gradezzy.util.TextUtils;

/**
 * Splits Java source into tokens and gives its shape.
 *
 * <p>Tier: COULD.
 */
public final class JavaTokenizer {

    // The text that starts a comment running to the end of the line
    private static final String LINE_COMMENT = "//";

    // The text that starts a comment running to BLOCK_COMMENT_END
    private static final String BLOCK_COMMENT_START = "/*";

    // The text that ends a block comment
    private static final String BLOCK_COMMENT_END = "*/";

    // The text that ends a line
    private static final String NEW_LINE = "\n";

    // The characters that end a line, which also end an open literal
    private static final char LINE_FEED = '\n';
    private static final char CARRIAGE_RETURN = '\r';

    // The quote around a string literal
    private static final char DOUBLE_QUOTE = '"';

    // The quote around a char literal
    private static final char SINGLE_QUOTE = '\'';

    // The character that escapes the next one inside a literal
    private static final char BACKSLASH = '\\';

    // The point inside a decimal number such as 3.14
    private static final char DECIMAL_POINT = '.';

    // The text placed between the tokens of a shape
    private static final String SHAPE_SEPARATOR = " ";

    // What a name that is not a keyword becomes in a shape
    private static final String NAME_TOKEN = "ID";

    // What a string or char literal becomes in a shape
    private static final String TEXT_TOKEN = "STR";

    // How far a piece of one character reaches past its start
    private static final int ONE_CHARACTER = 1;

    // The number of the first line of a file
    private static final int FIRST_LINE = 1;

    // The words a shape keeps: Java's reserved words and literals
    private static final Set<String> KEYWORDS = new HashSet<>(Arrays.asList(
            new String[] {"abstract", "assert", "boolean", "break", "byte",
                "case", "catch", "char", "class", "const", "continue",
                "default", "do", "double", "else", "enum", "extends",
                "final", "finally", "float", "for", "goto", "if",
                "implements", "import", "instanceof", "int", "interface",
                "long", "native", "new", "package", "private",
                "protected", "public", "return", "short", "static",
                "strictfp", "super", "switch", "synchronized", "this",
                "throw", "throws", "transient", "try", "void", "volatile",
                "while", "true", "false", "null", "var"}));

    // Only static methods; never created
    private JavaTokenizer() {
    }

    /**
     * Splits source code into tokens, skipping spaces and comments.
     *
     * <p>Tier: COULD.
     *
     * @param source the source code
     * @return the tokens in order
     */
    public static List<String> split(String source) {
        List<String> tokens = new ArrayList<>();
        for (JavaToken token : tokenize(source)) {
            tokens.add(token.getText());
        }
        return tokens;
    }

    /**
     * Splits source code into tokens, skipping spaces and comments, and
     * keeps where each token is: its start in the text and its line.
     *
     * <p>Tier: COULD.
     *
     * @param source the source code
     * @return the tokens in order
     */
    public static List<JavaToken> tokenize(String source) {
        List<JavaToken> tokens = new ArrayList<>();
        int start = 0;
        int line = FIRST_LINE;

        while (start < source.length()) {
            int end = findPieceEnd(source, start);
            String piece = source.substring(start, end);

            if (isToken(piece)) {
                tokens.add(new JavaToken(piece, start, line));
            }

            line = line + countLineBreaks(piece);
            start = end;
        }

        return tokens;
    }

    // Counts the line breaks in a piece of source, such as a comment
    // that runs over several lines.
    private static int countLineBreaks(String piece) {
        int count = 0;
        int found = piece.indexOf(NEW_LINE);
        while (found >= 0) {
            count++;
            int next = found + NEW_LINE.length();
            found = piece.indexOf(NEW_LINE, next);
        }
        return count;
    }

    /**
     * Returns the shape of source code: its tokens joined by a space,
     * with every name that is not a Java keyword replaced by ID and every
     * string or char literal by STR. Numbers and operators stay, as
     * "&lt;" against "&lt;=" or "+ 1" against "- 1" is the mistake.
     *
     * <p>Tier: COULD.
     *
     * @param source the source code
     * @return the shape; equal shapes mean the same code but for names
     */
    public static String shapeOf(String source) {
        List<String> shape = new ArrayList<>();
        for (String token : split(source)) {
            shape.add(shapeToken(token));
        }
        return TextUtils.join(SHAPE_SEPARATOR, shape);
    }

    /**
     * Returns what one token becomes in a shape: ID for a name that is
     * not a Java keyword, STR for a string or char literal, and the
     * token itself for anything else.
     *
     * <p>Tier: COULD.
     *
     * @param token the token
     * @return the token as it is in a shape
     */
    public static String shapeToken(String token) {
        char first = token.charAt(0);
        if (isQuote(first)) {
            return TEXT_TOKEN;
        }
        boolean word = Character.isJavaIdentifierStart(first);
        boolean keyword = KEYWORDS.contains(token);
        if (word && !keyword) {
            return NAME_TOKEN;
        }
        return token;
    }

    // Says whether a character starts a string or char literal.
    private static boolean isQuote(char character) {
        boolean doubleQuote = character == DOUBLE_QUOTE;
        boolean singleQuote = character == SINGLE_QUOTE;
        return doubleQuote || singleQuote;
    }

    // Returns where the token, space or comment starting at start ends.
    private static int findPieceEnd(String source, int start) {
        char first = source.charAt(start);

        if (source.startsWith(LINE_COMMENT, start)) {
            return findEndAfter(source, NEW_LINE, start);
        } else if (source.startsWith(BLOCK_COMMENT_START, start)) {
            int afterStart = start + BLOCK_COMMENT_START.length();
            return findEndAfter(source, BLOCK_COMMENT_END, afterStart);
        } else if (isQuote(first)) {
            return findLiteralEnd(source, start);
        } else if (Character.isDigit(first)) {
            return findNumberEnd(source, start);
        } else if (Character.isJavaIdentifierStart(first)) {
            return findWordEnd(source, start);
        }

        return start + ONE_CHARACTER;
    }

    // Returns the position just after the next marker, or the source end.
    private static int findEndAfter(String source, String marker, int from) {
        int markerStart = source.indexOf(marker, from);

        if (markerStart < 0) {
            return source.length();
        }

        return markerStart + marker.length();
    }

    // Returns where a string or char literal ends, after its closing
    // quote. A literal left open stops at the end of its line, as javac
    // stops it, so it does not swallow the rest of the file.
    private static int findLiteralEnd(String source, int start) {
        char quote = source.charAt(start);
        int end = start + ONE_CHARACTER;

        while (isInsideLiteral(source, end, quote)) {
            int next = end + ONE_CHARACTER;
            boolean escapes = source.charAt(end) == BACKSLASH
                    && !isLineEnd(source, next);
            if (escapes) {
                end++;
            }

            end++;
        }

        boolean closed = end < source.length()
                && source.charAt(end) == quote;
        if (!closed) {
            return Math.min(end, source.length());
        }
        return end + ONE_CHARACTER;
    }

    // Says whether a position is still inside a literal: before the end
    // of the source and of its line, and not its closing quote.
    private static boolean isInsideLiteral(String source, int position,
            char quote) {
        if (position >= source.length()) {
            return false;
        }
        if (isLineEnd(source, position)) {
            return false;
        }
        return source.charAt(position) != quote;
    }

    // Says whether the character at a position ends a line.
    private static boolean isLineEnd(String source, int position) {
        if (position >= source.length()) {
            return false;
        }
        char character = source.charAt(position);
        boolean lineFeed = character == LINE_FEED;
        boolean carriageReturn = character == CARRIAGE_RETURN;
        return lineFeed || carriageReturn;
    }

    // Returns where a number such as 42, 3.14 or 10L ends.
    private static int findNumberEnd(String source, int start) {
        int end = start + ONE_CHARACTER;

        while (isNumberPart(source, end)) {
            end++;
        }

        return end;
    }

    // Says whether the character at a position can carry on a number: a
    // letter, a digit or a decimal point before the end of the source.
    private static boolean isNumberPart(String source, int position) {
        if (position >= source.length()) {
            return false;
        }
        char character = source.charAt(position);
        boolean letterOrDigit = Character.isLetterOrDigit(character);
        return letterOrDigit || character == DECIMAL_POINT;
    }

    // Returns where a name or keyword ends.
    private static int findWordEnd(String source, int start) {
        int end = start + ONE_CHARACTER;

        while (isWordPart(source, end)) {
            end++;
        }

        return end;
    }

    // Says whether the character at a position can carry on a name,
    // before the end of the source.
    private static boolean isWordPart(String source, int position) {
        if (position >= source.length()) {
            return false;
        }
        return Character.isJavaIdentifierPart(source.charAt(position));
    }

    // Says whether a piece of source is a token, not a space or a comment.
    private static boolean isToken(String piece) {
        if (Character.isWhitespace(piece.charAt(0))) {
            return false;
        }
        if (piece.startsWith(LINE_COMMENT)) {
            return false;
        }
        return !piece.startsWith(BLOCK_COMMENT_START);
    }
}
