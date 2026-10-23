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
import java.util.Map;

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

    /**
     * Creates a new json writer.
     */
    public JsonWriter() {
        // nothing to set up yet
    }

    /**
     * Wraps text in quotes and escapes special characters.
     *
     * @param text the text
     * @return the text as a JSON string, or null when the text is null
     */
    // Generated with Claude Opus 5.5; reviewed by Huxley
    public String quote(String text) {
        if (text == null) {
            return NULL;
        }

        StringBuilder quoted = new StringBuilder();
        quoted.append('"');
        for (int index = 0; index < text.length(); index++) {
            quoted.append(escape(text.charAt(index)));
        }
        quoted.append('"');
        return quoted.toString();
    }

    /**
     * Builds a JSON object from names and ready-made values.
     *
     * <p>Fields appear in the order the map returns them, so pass a
     * LinkedHashMap to control the order. A null value is written as null.
     *
     * @param fields the fields
     * @return the JSON object text
     */
    // Generated with Claude Opus 5.5; reviewed by Huxley
    public String writeObject(Map<String, String> fields) {
        List<String> pairs = new ArrayList<>();
        for (Map.Entry<String, String> field : fields.entrySet()) {
            pairs.add(quote(field.getKey()) + NAME_SEPARATOR
                    + valueOrNull(field.getValue()));
        }
        return "{" + String.join(SEPARATOR, pairs) + "}";
    }

    /**
     * Builds a JSON array from ready-made values.
     *
     * @param elements the elements
     * @return the JSON array text
     */
    // Generated with Claude Opus 5.5; reviewed by Huxley
    public String writeArray(List<String> elements) {
        List<String> values = new ArrayList<>();
        for (String element : elements) {
            values.add(valueOrNull(element));
        }
        return "[" + String.join(SEPARATOR, values) + "]";
    }

    // Returns the escaped form of one character of a JSON string
    private String escape(char character) {
        if (character == '"') {
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
