/*
 * File: JavaSourceText.java
 *
 * Purpose: Reads and compares the Java source files the detectors check.
 *
 * Where it fits: Shared by the detectors.
 */
package gradezzy.detection;

import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.LinkOption;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.List;

import gradezzy.model.Assessment;
import gradezzy.model.Question;
import gradezzy.model.Submission;
import gradezzy.testcase.QuestionFolderFinder;
import gradezzy.util.TextUtils;

/**
 * Reads and compares the Java source files the detectors check.
 */
final class JavaSourceText {

    // The method that holds the sample test cases, never an answer
    private static final String MAIN_METHOD = "main";

    // Separates the folder and the file in a name such as Q1/Q1a.java
    private static final String FOLDER_SEPARATOR = "/";

    // The line ending every file has after normalising
    private static final String LINE_END = "\n";

    // The same line ending as a single character, for building text in an
    // array of characters
    private static final char LINE_END_CHAR = '\n';

    // The invisible mark some editors put at the start of a file
    private static final String BYTE_ORDER_MARK = "\uFEFF";

    // The marks that start and end comments
    private static final String BLOCK_COMMENT_START = "/*";
    private static final String BLOCK_COMMENT_END = "*/";
    private static final String LINE_COMMENT_START = "//";

    // The characters around string and char literals
    private static final char DOUBLE_QUOTE = '"';
    private static final char SINGLE_QUOTE = '\'';

    // Escapes the character after it inside a literal
    private static final char BACKSLASH = '\\';

    // What the inside of a literal is replaced with
    private static final char BLANK = ' ';

    // The characters that shape a class and its methods
    private static final char OPEN_BRACE = '{';
    private static final char CLOSE_BRACE = '}';
    private static final char OPEN_PAREN = '(';
    private static final char CLOSE_PAREN = ')';
    private static final char SEMICOLON = ';';
    private static final char ASSIGN = '=';

    // How many braces are open around a method header: just the class
    private static final int CLASS_BODY_DEPTH = 1;

    /**
     * Not used: every method here is static.
     */
    private JavaSourceText() {
        // utility class, never instantiated
    }

    // Reads the student's file for a question, or returns null if missing.
    static String readStudentFile(Submission submission,
            Assessment assessment, Question question) {
        try {
            Path folder = QuestionFolderFinder.find(submission, assessment,
                    question);

            if (folder == null) {
                return null;
            }

            return readFile(folder.resolve(question.getFileName()));
        } catch (IOException exception) {
            return null;
        }
    }

    // Reads the starter file for a question, or returns null if missing.
    static String readStarterFile(Path starterDirectory, Question question) {
        Path folder = starterDirectory.resolve(question.getFolderName());

        return readFile(folder.resolve(question.getFileName()));
    }

    // Names a question's file the way the instructor sees it: Q1/Q1a.java.
    static String describeFile(Question question) {
        return question.getFolderName() + FOLDER_SEPARATOR
                + question.getFileName();
    }

    // Says whether a file is unchanged from the starter file.
    static boolean isUnchanged(String studentText, String starterText) {
        String student = removeBlankLines(removeHeader(studentText));
        String starter = removeBlankLines(removeHeader(starterText));

        return student.equals(starter);
    }

    // Says whether two pieces of code match once all spacing is removed.
    static boolean sameIgnoringSpaces(String first, String second) {
        return removeWhitespace(first).equals(removeWhitespace(second));
    }

    // Removes every comment but keeps string and char literals unchanged.
    static String removeComments(String code) {
        // Each comment becomes one line end, so the result fits
        char[] result = new char[code.length()];
        int count = 0;
        int index = 0;

        while (index < code.length()) {
            char current = code.charAt(index);

            if (isQuote(current)) {
                int literalEnd = skipLiteral(code, index);

                for (int i = index; i < literalEnd; i++) {
                    result[count] = code.charAt(i);
                    count++;
                }
                index = literalEnd;
            } else if (code.startsWith(LINE_COMMENT_START, index)) {
                index = skipPast(code, LINE_END, index);
                result[count] = LINE_END_CHAR;
                count++;
            } else if (code.startsWith(BLOCK_COMMENT_START, index)) {
                index = skipPast(code, BLOCK_COMMENT_END,
                        index + BLOCK_COMMENT_START.length());
                result[count] = LINE_END_CHAR;
                count++;
            } else {
                result[count] = current;
                count++;
                index++;
            }
        }

        return new String(result, 0, count);
    }

    // Replaces the inside of each literal with spaces.
    static String blankLiterals(String code) {
        char[] result = code.toCharArray();
        int index = 0;

        while (index < code.length()) {
            if (isQuote(code.charAt(index))) {
                int literalEnd = skipLiteral(code, index);

                for (int i = index + 1; i < literalEnd - 1; i++) {
                    result[i] = BLANK;
                }

                index = literalEnd;
            } else {
                index++;
            }
        }

        return new String(result);
    }

    // Lists the methods in code without comments, except main, in order.
    static List<String> findAnswerMethodNames(String code) {
        String blanked = blankLiterals(code);
        List<String> names = new ArrayList<>();

        for (int brace : findMethodBraces(blanked)) {
            String name = findMethodName(blanked, brace);

            if (!name.equals(MAIN_METHOD) && !names.contains(name)) {
                names.add(name);
            }
        }

        return names;
    }

    // Returns the body of the first method with that name, or null.
    static String findMethodBody(String code, String methodName) {
        List<String> bodies = findMethodBodies(code, methodName);

        if (bodies.isEmpty()) {
            return null;
        }

        return bodies.get(0);
    }

    // Returns the body of every method with that name, overloads included.
    static List<String> findMethodBodies(String code, String methodName) {
        String blanked = blankLiterals(code);
        List<String> bodies = new ArrayList<>();

        for (int brace : findMethodBraces(blanked)) {
            if (findMethodName(blanked, brace).equals(methodName)) {
                int closeBrace = findClosingBracket(blanked, brace,
                        OPEN_BRACE, CLOSE_BRACE);

                bodies.add(code.substring(brace + 1, closeBrace));
            }
        }

        return bodies;
    }

    // Returns where the bracket opened at openIndex is closed.
    static int findClosingBracket(String code, int openIndex, char open,
            char close) {
        int depth = 0;

        for (int i = openIndex; i < code.length(); i++) {
            if (code.charAt(i) == open) {
                depth++;
            } else if (code.charAt(i) == close) {
                depth--;
            }

            if (depth == 0) {
                return i;
            }
        }

        return code.length();
    }

    // Says whether a character starts or ends a string or char literal.
    static boolean isQuote(char character) {
        return character == DOUBLE_QUOTE || character == SINGLE_QUOTE;
    }

    // Reads a file with "\n" line endings, or returns null if unreadable.
    private static String readFile(Path file) {
        if (!Files.isRegularFile(file, LinkOption.NOFOLLOW_LINKS)) {
            return null;
        }

        try {
            byte[] bytes = Files.readAllBytes(file);

            String text = TextUtils.normaliseLineEndings(new String(bytes,
                    StandardCharsets.UTF_8));

            if (text.startsWith(BYTE_ORDER_MARK)) {
                return text.substring(BYTE_ORDER_MARK.length());
            }
            return text;
        } catch (IOException exception) {
            return null;
        }
    }

    // Removes the block comment at the top that holds the name and email.
    private static String removeHeader(String text) {
        String trimmed = text.stripLeading();
        int headerEnd = trimmed.indexOf(BLOCK_COMMENT_END,
                BLOCK_COMMENT_START.length());

        if (!trimmed.startsWith(BLOCK_COMMENT_START) || headerEnd < 0) {
            return text;
        }

        return trimmed.substring(headerEnd + BLOCK_COMMENT_END.length());
    }

    // Drops blank lines and the spaces at the end of every line.
    private static String removeBlankLines(String text) {
        List<String> kept = new ArrayList<>();
        int lineStart = 0;

        while (lineStart < text.length()) {
            int lineEnd = text.indexOf(LINE_END, lineStart);

            if (lineEnd < 0) {
                lineEnd = text.length();
            }

            String line = text.substring(lineStart, lineEnd).stripTrailing();

            if (!line.isEmpty()) {
                kept.add(line + LINE_END);
            }

            lineStart = lineEnd + LINE_END.length();
        }

        // Joined once, as += would copy the text again for every line
        return TextUtils.join("", kept);
    }

    // Drops every space, tab and line break.
    private static String removeWhitespace(String text) {
        char[] result = new char[text.length()];
        int count = 0;

        for (int i = 0; i < text.length(); i++) {
            char current = text.charAt(i);

            if (!Character.isWhitespace(current)) {
                result[count] = current;
                count++;
            }
        }

        return new String(result, 0, count);
    }

    // Returns the position after the literal that starts at start.
    private static int skipLiteral(String code, int start) {
        char quote = code.charAt(start);
        int index = start + 1;

        while (index < code.length() && code.charAt(index) != quote) {
            if (code.charAt(index) == BACKSLASH) {
                index++;
            }

            index++;
        }

        return Math.min(index + 1, code.length());
    }

    // Returns the position after the next target, or the end of the code.
    private static int skipPast(String code, String target, int from) {
        int found = code.indexOf(target, from);

        if (found < 0) {
            return code.length();
        }

        return found + target.length();
    }

    // Finds the opening brace of each method directly inside the class.
    private static List<Integer> findMethodBraces(String code) {
        List<Integer> braces = new ArrayList<>();
        int depth = 0;
        int headerStart = 0;

        for (int i = 0; i < code.length(); i++) {
            char current = code.charAt(i);

            if (current == OPEN_BRACE && depth == CLASS_BODY_DEPTH
                    && isMethodHeader(code.substring(headerStart, i))) {
                braces.add(i);
            }

            if (current == OPEN_BRACE) {
                depth++;
            } else if (current == CLOSE_BRACE) {
                depth--;
            }

            if (current == OPEN_BRACE || current == CLOSE_BRACE
                    || current == SEMICOLON) {
                headerStart = i + 1;
            }
        }

        return braces;
    }

    // Says whether a header declares a method: brackets, no = outside them.
    private static boolean isMethodHeader(String header) {
        int depth = 0;
        boolean assignsValue = false;

        // An = inside brackets can only be in an annotation
        for (int i = 0; i < header.length(); i++) {
            char current = header.charAt(i);

            if (current == OPEN_PAREN) {
                depth++;
            } else if (current == CLOSE_PAREN) {
                depth--;
            } else if (current == ASSIGN && depth == 0) {
                assignsValue = true;
            }
        }

        return header.indexOf(OPEN_PAREN) >= 0 && !assignsValue;
    }

    // Returns the name in front of the parameter list.
    private static String findMethodName(String code, int braceIndex) {
        // The last bracket pair, which holds any annotation's brackets
        int closeIndex = code.lastIndexOf(CLOSE_PAREN, braceIndex);
        int nameEnd = findOpeningBracket(code, closeIndex, OPEN_PAREN,
                CLOSE_PAREN);

        while (nameEnd > 0
                && Character.isWhitespace(code.charAt(nameEnd - 1))) {
            nameEnd--;
        }

        int nameStart = nameEnd;

        while (nameStart > 0
                && Character.isJavaIdentifierPart(code.charAt(nameStart - 1))) {
            nameStart--;
        }

        return code.substring(nameStart, nameEnd);
    }

    // Returns where the bracket closed at closeIndex opens, or 0.
    private static int findOpeningBracket(String code, int closeIndex,
            char open, char close) {
        int depth = 0;

        for (int i = closeIndex; i >= 0; i--) {
            if (code.charAt(i) == close) {
                depth++;
            } else if (code.charAt(i) == open) {
                depth--;
            }

            if (depth == 0) {
                return i;
            }
        }

        return 0;
    }
}
