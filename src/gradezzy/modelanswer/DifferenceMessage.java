/*
 * File: DifferenceMessage.java
 *
 * Purpose: Writes the words of a random inputs flag: how many inputs
 * gave a different answer, with up to MAX_EXAMPLES of them written as
 * the student would write the call, and which methods could not be
 * called in the student's folder. Each example and method is on a line
 * of its own, so the flag reads as a short list.
 *
 * Where it fits: RandomInputChecker finds the differences and asks this
 * class for the sentences it returns to RandomInputStage.
 */
package gradezzy.modelanswer;

import java.util.List;

import gradezzy.model.Question;

/**
 * Writes the sentences of a random inputs flag.
 *
 * <p>Tier: COULD. Owner: Jing Yuan.
 */
class DifferenceMessage {

    // The most differences shown in a flag, and the longest result shown
    private static final int MAX_EXAMPLES = 3;
    private static final int MAX_SHOWN_LENGTH = 40;
    private static final String CUT_MARK = "...";

    // Ends a line of the flag, and starts each example or method
    private static final String NEW_LINE = "\n";
    private static final String BULLET = "\u2022 ";

    // How a text and a character result are quoted when shown, and how a
    // null result prints
    private static final String TEXT_QUOTE = "\"";
    private static final String CHAR_QUOTE = "'";
    private static final String NULL_TEXT = "null";

    // The package names the input program writes in full
    private static final String JAVA_UTIL = "java.util.";
    private static final String JAVA_LANG = "java.lang.";

    // How the input program writes a list and an array, and how a flag
    // writes them instead: a list as ArrayList prints it, [3, 1], and an
    // array as the values in braces, {3, 1}
    private static final String LIST_START = "new ArrayList<";
    private static final char TYPE_END = '>';
    private static final String EMPTY_LIST_END = ">()";
    private static final String FILLED_LIST_MIDDLE = ">(Arrays.asList(";
    private static final String FILLED_LIST_END = "))";
    private static final String LIST_OPEN = "[";
    private static final String LIST_CLOSE = "]";
    private static final String ARRAY_START_PATTERN =
            "new [A-Za-z]+(\\[\\])+ \\{";
    private static final String ARRAY_OPEN = "{";

    // The characters that open and close a call, quote a text or a
    // character, and escape the next character in one
    private static final char OPEN_PARENTHESIS = '(';
    private static final char CLOSE_PARENTHESIS = ')';
    private static final char DOUBLE_QUOTE = '"';
    private static final char SINGLE_QUOTE = '\'';
    private static final char ESCAPE = '\\';
    private static final char NOT_IN_QUOTE = ' ';

    // Says how many of the compared inputs gave a different answer, then
    // lists the examples, one a line, such as:
    // Q5: 37 of 200 random inputs gave a different answer from the model
    // answer.
    // For example:
    // * countDigit(-4809, 4) returned 0, but the model answer returns 1
    String describeDifferences(Question question, int different,
            int compared, List<String> examples) {
        return question.getId() + ": " + different + " of " + compared
                + " random inputs gave a different answer from the model "
                + "answer." + NEW_LINE + "For example:"
                + listLines(examples);
    }

    // Names the methods that could not be called in the student's folder.
    // The same program compiled with the model answer, so the folder
    // holds a changed method or a class named like one of Java's own,
    // which could be there to dodge this check.
    String describeNotCalled(Question question, List<String> notCalled) {
        return question.getId() + ": these methods could not be called "
                + "with random inputs here, though they can in the model "
                + "answer:" + listLines(notCalled) + NEW_LINE
                + "Check that each method is as the question asks.";
    }

    // Writes items as a list: each on a new line after a bullet.
    private String listLines(List<String> items) {
        String text = "";
        for (String item : items) {
            text += NEW_LINE + BULLET + item;
        }
        return text;
    }

    // Names a method with its parameter types, such as
    // countDigit(long, int).
    String describeMethod(MethodSignature method) {
        return method.describeCall(method.getParameterTypes());
    }

    // Adds one difference to the examples, while there are fewer than
    // MAX_EXAMPLES, such as: compress("aab") returned "a2b", but the
    // model answer returns "a2b1".
    void addExample(List<String> examples, MethodCheck check, int index,
            CallResult expected, CallResult actual) {
        if (examples.size() >= MAX_EXAMPLES) {
            return;
        }
        MethodSignature method = check.getMethod();
        List<String> arguments = check.getInputs().get(index);
        String call = makeReadable(method.describeCall(arguments));
        String got = "returned " + showResult(method, actual.getText());
        if (actual.hasThrown()) {
            got = "threw " + actual.getText();
        }
        examples.add(call + " " + got + ", but the model answer returns "
                + showResult(method, expected.getText()));
    }

    // Writes a call so it is quick to read: the package names the input
    // program writes in full are removed, a list is written as ArrayList
    // prints it and an array as its values in braces, such as
    // secondLargest([0, -19, 0]) or sum({3, 1}).
    private String makeReadable(String call) {
        String text = call.replace(JAVA_UTIL, "").replace(JAVA_LANG, "");
        text = text.replaceAll(ARRAY_START_PATTERN, ARRAY_OPEN);
        return writeListsShort(text);
    }

    // Writes each list in a call as its values in square brackets, such
    // as new ArrayList<Integer>(Arrays.asList(3, 1)) as [3, 1] and an
    // empty one as [].
    private String writeListsShort(String text) {
        String result = "";
        int index = 0;
        while (index < text.length()) {
            int start = text.indexOf(LIST_START, index);
            int typeEnd = -1;
            if (start >= 0) {
                typeEnd = text.indexOf(TYPE_END, start);
            }
            if (start < 0 || typeEnd < 0) {
                result += text.substring(index);
                index = text.length();
            } else if (text.startsWith(EMPTY_LIST_END, typeEnd)) {
                result += text.substring(index, start) + LIST_OPEN
                        + LIST_CLOSE;
                index = typeEnd + EMPTY_LIST_END.length();
            } else if (text.startsWith(FILLED_LIST_MIDDLE, typeEnd)) {
                int open = typeEnd + FILLED_LIST_MIDDLE.length();
                int close = findClosingParenthesis(text, open);
                result += text.substring(index, start) + LIST_OPEN
                        + text.substring(open, close) + LIST_CLOSE;
                index = Math.min(text.length(),
                        close + FILLED_LIST_END.length());
            } else {
                result += text.substring(index, typeEnd + 1);
                index = typeEnd + 1;
            }
        }
        return result;
    }

    // Returns where the parenthesis that closes a call starts, from just
    // after its opening one, skipping texts and characters in quotes; the
    // end of the text when there is none.
    private int findClosingParenthesis(String text, int from) {
        int depth = 0;
        char quote = NOT_IN_QUOTE;
        int index = from;
        while (index < text.length()) {
            char next = text.charAt(index);
            boolean inQuote = quote != NOT_IN_QUOTE;
            boolean opensQuote = next == DOUBLE_QUOTE
                    || next == SINGLE_QUOTE;
            if (inQuote && next == ESCAPE) {
                // The escaped character is skipped with this one
                index++;
            } else if (inQuote && next == quote) {
                quote = NOT_IN_QUOTE;
            } else if (!inQuote && opensQuote) {
                quote = next;
            } else if (!inQuote && next == OPEN_PARENTHESIS) {
                depth++;
            } else if (!inQuote && next == CLOSE_PARENTHESIS
                    && depth == 0) {
                return index;
            } else if (!inQuote && next == CLOSE_PARENTHESIS) {
                depth--;
            }
            index++;
        }
        return text.length();
    }

    // Writes a result as Java writes the value, so empty text shows as
    // "" rather than nothing: text in double quotes, a character in
    // single quotes, anything else as printed. Long results are cut.
    private String showResult(MethodSignature method, String text) {
        String shown = shorten(text);
        String type = method.getReturnType();
        if (text.equals(NULL_TEXT)) {
            return shown;
        } else if (type.equals(JavaTypes.STRING_TYPE)) {
            return TEXT_QUOTE + shown + TEXT_QUOTE;
        } else if (type.equals(JavaTypes.CHAR_TYPE)) {
            return CHAR_QUOTE + shown + CHAR_QUOTE;
        }
        return shown;
    }

    // Cuts a long result short for a flag.
    private String shorten(String text) {
        if (text.length() <= MAX_SHOWN_LENGTH) {
            return text;
        }
        return text.substring(0, MAX_SHOWN_LENGTH) + CUT_MARK;
    }
}
