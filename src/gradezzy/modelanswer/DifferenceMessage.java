/*
 * File: DifferenceMessage.java
 *
 * Purpose: Writes the words of a random inputs flag: how many inputs
 * gave a different answer, with up to MAX_EXAMPLES of them written as
 * the student would write the call, and which methods could not be
 * called in the student's folder.
 *
 * Where it fits: RandomInputChecker finds the differences and asks this
 * class for the sentences it returns to RandomInputStage.
 */
package gradezzy.modelanswer;

import java.util.List;

import gradezzy.model.Question;
import gradezzy.util.TextUtils;

/**
 * Writes the sentences of a random inputs flag.
 *
 * <p>Tier: COULD. Owner: Jing Yuan.
 */
class DifferenceMessage {

    // The most differences shown in a flag, and the longest result shown
    private static final int MAX_EXAMPLES = 2;
    private static final int MAX_SHOWN_LENGTH = 40;
    private static final String CUT_MARK = "...";

    // Separates the examples, and the methods, in a sentence
    private static final String EXAMPLE_SEPARATOR = "; ";

    // How a text and a character result are quoted when shown, and how a
    // null result prints
    private static final String TEXT_QUOTE = "\"";
    private static final String CHAR_QUOTE = "'";
    private static final String NULL_TEXT = "null";

    // The package names the input program writes in full
    private static final String JAVA_UTIL = "java.util.";
    private static final String JAVA_LANG = "java.lang.";

    // Says how many of the compared inputs gave a different answer, with
    // the examples.
    String describeDifferences(Question question, int different,
            int compared, List<String> examples) {
        return question.getId() + ": " + different + " of " + compared
                + " random inputs give a different answer from the model "
                + "answer, for example "
                + TextUtils.join(EXAMPLE_SEPARATOR, examples) + ".";
    }

    // Names the methods that could not be called in the student's folder.
    // The same program compiled with the model answer, so the folder
    // holds a changed method or a class named like one of Java's own,
    // which could be there to dodge this check.
    String describeNotCalled(Question question, List<String> notCalled) {
        return question.getId() + ": "
                + TextUtils.join(EXAMPLE_SEPARATOR, notCalled)
                + " could not be called with random inputs here, "
                + "though it can in the model answer. Check that the "
                + "method is as the question asks.";
    }

    // Names a method with its parameter types, such as
    // countDigit(long, int).
    String describeMethod(MethodSignature method) {
        return method.describeCall(method.getParameterTypes());
    }

    // Adds one difference to the examples, while there are fewer than
    // MAX_EXAMPLES, such as: compress("aab") returned "a2b" where the
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
        examples.add(call + " " + got + " where the model answer returns "
                + showResult(method, expected.getText()));
    }

    // Removes the package names the input program writes in full, so a
    // call reads as the student would write it, such as
    // new ArrayList<Integer>(Arrays.asList(3, 1)).
    private String makeReadable(String call) {
        return call.replace(JAVA_UTIL, "").replace(JAVA_LANG, "");
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
