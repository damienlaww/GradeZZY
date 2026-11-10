/*
 * File: InputMaker.java
 *
 * Purpose: Makes inputs for a method, written as Java: first the edge
 * cases (0, 1, -1, the largest and smallest numbers, empty text, null,
 * empty arrays and lists), then random values.
 *
 * Where it fits: RandomInputChecker gives the inputs to InputDriver,
 * which writes one call per input. The same inputs go to the model
 * answer and to every student in a run; a new run makes new ones, so a
 * hard-coded answer cannot know them.
 */
package gradezzy.modelanswer;

import java.util.ArrayList;
import java.util.HashSet;
import java.util.List;
import java.util.Random;
import java.util.Set;

import gradezzy.util.TextUtils;

/**
 * Makes edge-case and random inputs for a method, as Java text.
 *
 * <p>Tier: COULD. Owner: Jing Yuan.
 */
public class InputMaker {

    // Edge cases of each simple type, as Java text
    private static final String[] INT_EDGES = {
        "0", "1", "-1", "2", "10", "-7", "100", "2147483647", "-2147483648"
    };
    private static final String[] LONG_EDGES = {
        "0L", "1L", "-1L", "10L", "-25L", "1000000L",
        "9223372036854775807L", "-9223372036854775808L"
    };
    private static final String[] DOUBLE_EDGES = {
        "0.0", "1.0", "-1.0", "0.5", "2.75", "-3.5", "100.0"
    };
    private static final String[] BOOLEAN_EDGES = {"true", "false"};
    private static final String[] CHAR_EDGES = {
        "'a'", "'z'", "'A'", "'0'", "' '"
    };
    private static final String[] STRING_EDGES = {
        "\"\"", "null", "\"a\"", "\"aa\"", "\"ab\"", "\"aaabbc\"",
        "\"Hello World\"", "\"12345\"", "\"AbC\""
    };

    // The characters random text and characters are made of. Letters
    // repeat, so random text often has runs such as "aab"; there is no
    // quote or backslash, so the text can be written as Java as it is.
    private static final String CHARACTER_POOL = "aaabbbccxyzABC 0129";

    // Java text around an array or a list. Classes are named in full,
    // such as java.util.ArrayList, so a student class with the same
    // short name cannot take their place.
    private static final String NEW = "new ";
    private static final String ARRAY_OPEN = " {";
    private static final String ARRAY_CLOSE = "}";
    private static final String LIST_TYPE = "java.util.ArrayList<";
    private static final String JAVA_LANG = "java.lang.";
    private static final String EMPTY_LIST_END = ">()";
    private static final String FILLED_LIST_MIDDLE =
            ">(java.util.Arrays.asList(";
    private static final String FILLED_LIST_END = "))";
    private static final String NULL = "null";
    private static final String ELEMENT_SEPARATOR = ", ";
    private static final String TEXT_QUOTE = "\"";
    private static final String CHAR_QUOTE = "'";
    private static final String LONG_SUFFIX = "L";

    // How many rounds of edge cases come before the random inputs
    private static final int EDGE_ROUNDS = 10;

    // The round that gives an array or a list of null and of none
    private static final int NULL_ROUND = 0;
    private static final int EMPTY_ROUND = 1;

    // Added to a count so the count itself can be picked, as nextInt
    // never returns its bound
    private static final int INCLUDE_BOUND = 1;

    // How many tries each wanted input may take, since repeated inputs
    // are dropped and a method of one true or false has only two
    private static final int TRIES_PER_INPUT = 5;

    // Sizes of random values: the longest text, array and list, and the
    // ranges numbers are picked from, smallest first
    private static final int MAX_TEXT_LENGTH = 12;
    private static final int MAX_ELEMENTS = 8;
    private static final int DIGIT_RANGE = 10;
    private static final int SMALL_RANGE = 100;
    private static final int LARGE_RANGE = 100000;
    private static final double DOUBLE_RANGE = 1000.0;

    // The share of random numbers that are digits, and the share that
    // are digits or small, so most inputs stay in a sensible range
    private static final double DIGIT_SHARE = 0.4;
    private static final double SMALL_SHARE = 0.8;

    // Random doubles are rounded to two decimal places
    private static final double HUNDREDTHS = 100.0;

    // The source of random values, one per run
    private final Random random;

    /**
     * Creates an input maker.
     *
     * <p>Tier: COULD.
     *
     * @param random the source of random values
     */
    public InputMaker(Random random) {
        this.random = random;
    }

    /**
     * Makes up to count different inputs for a method: the edge cases
     * first, then random ones. Each input holds one argument per
     * parameter, as Java text.
     *
     * <p>Tier: COULD.
     *
     * @param method the method
     * @param count how many inputs to make
     * @return the inputs
     */
    public List<List<String>> make(MethodSignature method, int count) {
        List<List<String>> inputs = new ArrayList<>();
        Set<String> seen = new HashSet<>();
        int maxTries = count * TRIES_PER_INPUT;

        for (int round = 0; round < maxTries; round++) {
            if (inputs.size() >= count) {
                return inputs;
            }
            List<String> arguments = new ArrayList<>();
            for (String type : method.getParameterTypes()) {
                arguments.add(makeValue(type, round));
            }
            String key = TextUtils.join(ELEMENT_SEPARATOR, arguments);
            if (seen.add(key)) {
                inputs.add(arguments);
            }
        }
        return inputs;
    }

    // Makes one value of a type: an edge case in the first rounds, else
    // a random one.
    private String makeValue(String type, int round) {
        String arrayElement = JavaTypes.findArrayElement(type);
        if (arrayElement != null) {
            return makeArray(arrayElement, round);
        }

        String listElement = JavaTypes.findListElement(type);
        if (listElement != null) {
            return makeList(listElement, round);
        }
        return makeSimple(type, round);
    }

    // Makes an array, such as new int[] {3, 0, -1}: null and empty in
    // the first two rounds, then random elements.
    private String makeArray(String elementType, int round) {
        if (round == NULL_ROUND) {
            return NULL;
        }
        List<String> elements = makeElements(elementType, round);
        String writtenType = elementType;
        if (elementType.equals(JavaTypes.STRING_TYPE)) {
            writtenType = JAVA_LANG + elementType;
        }
        return NEW + writtenType + JavaTypes.ARRAY_END + ARRAY_OPEN
                + TextUtils.join(ELEMENT_SEPARATOR, elements) + ARRAY_CLOSE;
    }

    // Makes a list, such as new ArrayList<Integer>(Arrays.asList(3, 0)).
    private String makeList(String elementType, int round) {
        if (round == NULL_ROUND) {
            return NULL;
        }
        String simpleType = JavaTypes.simpleTypeOf(elementType);
        List<String> elements = makeElements(simpleType, round);
        String writtenType = JAVA_LANG + elementType;
        if (elements.isEmpty()) {
            return NEW + LIST_TYPE + writtenType + EMPTY_LIST_END;
        }
        return NEW + LIST_TYPE + writtenType + FILLED_LIST_MIDDLE
                + TextUtils.join(ELEMENT_SEPARATOR, elements)
                + FILLED_LIST_END;
    }

    // Makes the elements of an array or list: none in the empty round,
    // else a random number of them. A list's elements are never null,
    // so its text elements are never "null".
    private List<String> makeElements(String elementType, int round) {
        List<String> elements = new ArrayList<>();
        if (round == EMPTY_ROUND) {
            return elements;
        }

        int size = random.nextInt(MAX_ELEMENTS) + INCLUDE_BOUND;
        for (int count = 0; count < size; count++) {
            elements.add(randomSimple(elementType));
        }
        return elements;
    }

    // Makes a simple value: an edge case in the first rounds, else a
    // random one.
    private String makeSimple(String type, int round) {
        String[] edges = edgesOf(type);
        if (round < EDGE_ROUNDS && edges.length > 0) {
            int edgeIndex = round % edges.length;
            return edges[edgeIndex];
        }
        return randomSimple(type);
    }

    // Returns the edge cases of a simple type.
    private String[] edgesOf(String type) {
        if (type.equals(JavaTypes.INT_TYPE)) {
            return INT_EDGES;
        } else if (type.equals(JavaTypes.LONG_TYPE)) {
            return LONG_EDGES;
        } else if (type.equals(JavaTypes.DOUBLE_TYPE)) {
            return DOUBLE_EDGES;
        } else if (type.equals(JavaTypes.BOOLEAN_TYPE)) {
            return BOOLEAN_EDGES;
        } else if (type.equals(JavaTypes.CHAR_TYPE)) {
            return CHAR_EDGES;
        }
        return STRING_EDGES;
    }

    // Makes a random value of a simple type, as Java text.
    private String randomSimple(String type) {
        if (type.equals(JavaTypes.INT_TYPE)) {
            return String.valueOf(randomWholeNumber());
        } else if (type.equals(JavaTypes.LONG_TYPE)) {
            return randomWholeNumber() + LONG_SUFFIX;
        } else if (type.equals(JavaTypes.DOUBLE_TYPE)) {
            return String.valueOf(randomDouble());
        } else if (type.equals(JavaTypes.BOOLEAN_TYPE)) {
            return String.valueOf(random.nextBoolean());
        } else if (type.equals(JavaTypes.CHAR_TYPE)) {
            return CHAR_QUOTE + randomCharacter() + CHAR_QUOTE;
        }
        return TEXT_QUOTE + randomText() + TEXT_QUOTE;
    }

    // Picks a whole number: most often a digit or a small number, and
    // sometimes a large one, negative numbers included.
    private int randomWholeNumber() {
        double kind = random.nextDouble();
        if (kind < DIGIT_SHARE) {
            return random.nextInt(DIGIT_RANGE);
        } else if (kind < SMALL_SHARE) {
            return pickBetween(SMALL_RANGE);
        }
        return pickBetween(LARGE_RANGE);
    }

    // Picks a whole number from -range to range.
    private int pickBetween(int range) {
        int width = range + range;
        int widthWithEnd = width + INCLUDE_BOUND;
        return random.nextInt(widthWithEnd) - range;
    }

    // Picks a number from -DOUBLE_RANGE to DOUBLE_RANGE with two decimal
    // places.
    private double randomDouble() {
        double width = DOUBLE_RANGE + DOUBLE_RANGE;
        double value = random.nextDouble() * width - DOUBLE_RANGE;
        double inHundredths = value * HUNDREDTHS;
        return Math.round(inHundredths) / HUNDREDTHS;
    }

    // Picks one character from CHARACTER_POOL.
    private char randomCharacter() {
        int index = random.nextInt(CHARACTER_POOL.length());
        return CHARACTER_POOL.charAt(index);
    }

    // Makes random text of 0 to MAX_TEXT_LENGTH characters.
    private String randomText() {
        int lengthBound = MAX_TEXT_LENGTH + INCLUDE_BOUND;
        int length = random.nextInt(lengthBound);
        String text = "";
        for (int count = 0; count < length; count++) {
            text = text + randomCharacter();
        }
        return text;
    }
}
