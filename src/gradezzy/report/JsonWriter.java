/*
 * File: JsonWriter.java
 *
 * Purpose: Builds JSON text by hand, without a library.
 *
 * Where it fits: Keeps the JSON export free of extra jars and easy to explain.
 */
package gradezzy.report;

import java.util.ArrayList;
import java.util.List;

import gradezzy.util.TextUtils;

/**
 * Builds JSON text by hand, without a library.
 *
 * <p>Tier: SHOULD. Owner: Huxley.
 */
public class JsonWriter {

    /**
     * The JSON literal for a missing value.
     */
    public static final String NULL = "null";

    // Separator between the values of an object or array
    private static final String SEPARATOR = ",";

    // Separator between a name and its value
    private static final String NAME_SEPARATOR = ":";

    // Characters below a space are control characters and must be escaped
    private static final char FIRST_PRINTABLE = ' ';

    // Format of a control character written as a unicode escape
    private static final String UNICODE_ESCAPE = "\\u%04x";

    // The most characters one escaped character can take: a backslash,
    // the letter u and four hex digits
    private static final int MAX_ESCAPE_LENGTH = 6;

    // The quote around a JSON string
    private static final char QUOTE = '"';

    // The quotes around a JSON string, one at each end
    private static final int QUOTE_COUNT = 2;

    // The brackets around a JSON object
    private static final String OBJECT_START = "{";
    private static final String OBJECT_END = "}";

    // The brackets around a JSON array
    private static final String ARRAY_START = "[";
    private static final String ARRAY_END = "]";

    /**
     * Creates a new json writer.
     */
    public JsonWriter() {
        // nothing to set up
    }

    /**
     * Wraps text in quotes and escapes special characters.
     *
     * @param text the text
     * @return the text as a JSON string, or null when the text is null
     */
    public String quote(String text) {
        if (text == null) {
            return NULL;
        }

        // Tester output can be long, so the characters go into an array
        // big enough for the worst case, every character escaped, plus
        // the two quotes; count says how much of it is filled
        int capacity = text.length() * MAX_ESCAPE_LENGTH + QUOTE_COUNT;
        char[] quoted = new char[capacity];
        int count = 0;
        quoted[count] = QUOTE;
        count++;
        for (int index = 0; index < text.length(); index++) {
            String escaped = escape(text.charAt(index));
            for (int i = 0; i < escaped.length(); i++) {
                quoted[count] = escaped.charAt(i);
                count++;
            }
        }
        quoted[count] = QUOTE;
        count++;
        return new String(quoted, 0, count);
    }

    /**
     * Builds a JSON object from names and ready-made values.
     *
     * <p>Fields appear in the order they were added to the JsonFields.
     * A null value is written as null.
     *
     * @param fields the fields
     * @return the JSON object text
     */
    public String writeObject(JsonFields fields) {
        List<String> pairs = new ArrayList<>();
        for (String name : fields.getNames()) {
            String pair = quote(name) + NAME_SEPARATOR
                    + valueOrNull(fields.get(name));
            pairs.add(pair);
        }
        return OBJECT_START + TextUtils.join(SEPARATOR, pairs) + OBJECT_END;
    }

    /**
     * Builds a JSON array from ready-made values.
     *
     * @param elements the elements
     * @return the JSON array text
     */
    public String writeArray(List<String> elements) {
        List<String> values = new ArrayList<>();
        for (String element : elements) {
            values.add(valueOrNull(element));
        }
        return ARRAY_START + TextUtils.join(SEPARATOR, values) + ARRAY_END;
    }

    // Returns the escaped form of one character of a JSON string
    private String escape(char character) {
        if (character == QUOTE) {
            return "\\\"";
        } else if (character == '\\') {
            return "\\\\";
        } else if (character == '\n') {
            return "\\n";
        } else if (character == '\r') {
            return "\\r";
        } else if (character == '\t') {
            return "\\t";
        } else if (character == '\b') {
            return "\\b";
        } else if (character == '\f') {
            return "\\f";
        } else if (character < FIRST_PRINTABLE) {
            return String.format(UNICODE_ESCAPE, (int) character);
        }
        return String.valueOf(character);
    }

    // A ready-made value that is null becomes the JSON literal null
    private String valueOrNull(String value) {
        if (value == null) {
            return NULL;
        }
        return value;
    }
}
