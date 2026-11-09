/*
 * File: JavaSourceScanner.java
 *
 * Purpose: Splits Java source into names and symbols, and lists the
 * static methods a file declares and the methods it calls.
 *
 * Where it fits: ClassFileMatcher uses it to tell whether a tester or
 * starter folder belongs with the uploads: the testers should call, and
 * the starter files declare, the methods the students' files declare.
 * It only reads text; nothing is compiled.
 */
package gradezzy.service;

import java.util.ArrayList;
import java.util.HashSet;
import java.util.List;
import java.util.Set;

/**
 * Reads the method names out of Java source text.
 *
 * <p>Tier: SHOULD. Owner: Jing Yuan.
 */
final class JavaSourceScanner {

    // How many tokens after "static" a method name is looked for. A real
    // declaration has a few modifiers and a type; the limit keeps a file
    // made of "static" words from taking long.
    private static final int MAX_DECLARATION_TOKENS = 40;

    // The character that starts both kinds of comment, and the one after
    // it in a line comment (//) and in a block comment (/*)
    private static final char SLASH = '/';
    private static final char STAR = '*';

    // Ends a line comment
    private static final String LINE_END = "\n";

    // Length of "/*", which opens a block comment, and the text that
    // closes it
    private static final int COMMENT_START_LENGTH = 2;
    private static final String COMMENT_END = "*/";

    // The quotes around a string and around a character
    private static final char DOUBLE_QUOTE = '"';
    private static final char SINGLE_QUOTE = '\'';

    // Starts an escape in a string or character, such as \t, and the
    // length of the escape
    private static final char BACKSLASH = '\\';
    private static final int ESCAPE_LENGTH = 2;

    // A string or character that is not closed ends at the line end
    private static final char LINE_FEED = '\n';

    // Stands for the character after the last one, so a check of the
    // next character needs no length test
    private static final char NO_CHARACTER = ' ';

    // The method every program may declare, which says nothing about the
    // lab test
    private static final String MAIN_METHOD = "main";

    // The word in front of a method that belongs to the class
    private static final String STATIC_KEYWORD = "static";

    // Opens the parameter list of a method or a call
    private static final String OPEN_PARENTHESIS = "(";

    // Tokens that end a "static" declaration that is not a method: a
    // field with a value, a field, a static block or a stray ")"
    private static final String[] NOT_METHOD_ENDS = {"=", ";", "{", ")"};

    // Joins an object or class to the method called on it
    private static final String DOT = ".";

    // The word in front of a constructor call
    private static final String NEW_KEYWORD = "new";

    // Words that are followed by "(" but are not method calls
    private static final String[] NOT_CALLS = {"if", "for", "while",
        "switch", "catch", "return", "synchronized", "super", "this",
        "new", "assert", "throw"};

    // Not used: every method is static.
    private JavaSourceScanner() {
    }

    /**
     * Splits Java source into names and single symbols, leaving out
     * comments, strings and characters.
     *
     * @param source the Java source text
     * @return the tokens, in order
     */
    static List<String> tokenize(String source) {
        List<String> tokens = new ArrayList<>();
        int index = 0;
        int length = source.length();
        while (index < length) {
            char current = source.charAt(index);
            char next = NO_CHARACTER;
            int nextIndex = index + 1;
            if (nextIndex < length) {
                next = source.charAt(nextIndex);
            }
            boolean isQuote = current == DOUBLE_QUOTE
                    || current == SINGLE_QUOTE;
            if (current == SLASH && next == SLASH) {
                index = skipPast(source, index, LINE_END);
            } else if (current == SLASH && next == STAR) {
                index = skipPast(source, index + COMMENT_START_LENGTH,
                        COMMENT_END);
            } else if (isQuote) {
                index = skipQuoted(source, index);
            } else if (Character.isJavaIdentifierStart(current)) {
                int end = readNameEnd(source, nextIndex);
                tokens.add(source.substring(index, end));
                index = end;
            } else {
                if (!Character.isWhitespace(current)) {
                    tokens.add(String.valueOf(current));
                }
                index++;
            }
        }
        return tokens;
    }

    /**
     * Lists the static methods a class declares, except main: the
     * methods a question asks for, such as getIsogramWords.
     *
     * @param tokens the tokens of the class, from tokenize
     * @return the method names, in order, each once
     */
    static List<String> findStaticMethods(List<String> tokens) {
        List<String> methods = new ArrayList<>();
        Set<String> seen = new HashSet<>();
        for (int index = 0; index < tokens.size(); index++) {
            if (tokens.get(index).equals(STATIC_KEYWORD)) {
                int afterStatic = index + 1;
                String name = findDeclaredName(tokens, afterStatic);
                boolean isNewMethod = name != null
                        && !name.equals(MAIN_METHOD) && seen.add(name);
                if (isNewMethod) {
                    methods.add(name);
                }
            }
        }
        return methods;
    }

    /**
     * Lists the methods a tester calls that could be the student's:
     * calls with no object in front, or with the student's class name in
     * front, such as Q1a.getIsogramWords(...).
     *
     * @param tokens the tokens of the tester, from tokenize
     * @param className the student's class name, such as Q1a
     * @return the method names, in order, each once
     */
    static List<String> findCalls(List<String> tokens, String className) {
        List<String> calls = new ArrayList<>();
        Set<String> seen = new HashSet<>();
        // The two tokens before the current one, empty at the start
        String twoBefore = "";
        String before = "";
        for (int index = 0; index + 1 < tokens.size(); index++) {
            String name = tokens.get(index);
            String after = tokens.get(index + 1);
            boolean onClass = before.equals(DOT)
                    && twoBefore.equals(className);
            boolean isCall = after.equals(OPEN_PARENTHESIS) && isName(name)
                    && !isNotCall(name) && !name.equals(MAIN_METHOD)
                    && !before.equals(NEW_KEYWORD);
            boolean couldBeStudents = !before.equals(DOT) || onClass;
            if (isCall && couldBeStudents && seen.add(name)) {
                calls.add(name);
            }
            twoBefore = before;
            before = name;
        }
        return calls;
    }

    // Returns the index just after a name that starts before from.
    private static int readNameEnd(String source, int from) {
        int end = from;
        while (end < source.length()
                && Character.isJavaIdentifierPart(source.charAt(end))) {
            end++;
        }
        return end;
    }

    // Returns the index just after the next end text, or the length.
    private static int skipPast(String source, int from, String end) {
        int found = source.indexOf(end, from);
        if (found < 0) {
            return source.length();
        }
        return found + end.length();
    }

    // Returns the index just after a quoted string or character.
    private static int skipQuoted(String source, int start) {
        char quote = source.charAt(start);
        int index = start + 1;
        while (index < source.length()) {
            char current = source.charAt(index);
            if (current == BACKSLASH) {
                index = index + ESCAPE_LENGTH;
            } else if (current == quote || current == LINE_FEED) {
                return index + 1;
            } else {
                index++;
            }
        }
        return source.length();
    }

    // Returns the method name declared after "static", or null when
    // "static" starts a field, a block or a nested class instead. It
    // looks at no more than MAX_DECLARATION_TOKENS tokens and stops at
    // the next "static", so the whole file is read in one pass.
    private static String findDeclaredName(List<String> tokens, int start) {
        int stop = Math.min(tokens.size(), start + MAX_DECLARATION_TOKENS);
        for (int index = start; index < stop; index++) {
            String token = tokens.get(index);
            if (token.equals(STATIC_KEYWORD)) {
                return null;
            }
            if (token.equals(OPEN_PARENTHESIS)) {
                int nameIndex = index - 1;
                String name = tokens.get(nameIndex);
                if (index > start && isName(name)) {
                    return name;
                }
                return null;
            }
            if (isOneOf(token, NOT_METHOD_ENDS)) {
                return null;
            }
        }
        return null;
    }

    // Says whether a token is a Java name.
    private static boolean isName(String token) {
        return !token.isEmpty()
                && Character.isJavaIdentifierStart(token.charAt(0));
    }

    // Says whether a word before "(" is a keyword, not a method.
    private static boolean isNotCall(String word) {
        return isOneOf(word, NOT_CALLS);
    }

    // Says whether a token is one of the given words.
    private static boolean isOneOf(String token, String[] words) {
        for (String word : words) {
            if (word.equals(token)) {
                return true;
            }
        }
        return false;
    }
}
