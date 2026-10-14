/*
 * File: JsonWriter.java
 *
 * Purpose: Builds JSON text by hand, without a library.
 *
 * Where it fits: Keeps the JSON export free of extra jars and easy to explain.
 */
package gradezzy.report;

import java.util.List;
import java.util.Map;

/**
 * Builds JSON text by hand, without a library.
 *
 * <p>Tier: SHOULD. Owner: Huxley.
 */
public class JsonWriter {

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
     * @return the text as a JSON string
     */
    public String quote(String text) {
        // TODO(Huxley): implement: escaping of quotes, backslashes and control
        //     characters
        return "";
    }

    /**
     * Builds a JSON object from names and ready-made values.
     *
     * @param fields the fields
     * @return the JSON object text
     */
    public String writeObject(Map<String, String> fields) {
        // TODO(Huxley): implement: joining the fields as name and value pairs
        //     in braces
        return "";
    }

    /**
     * Builds a JSON array from ready-made values.
     *
     * @param elements the elements
     * @return the JSON array text
     */
    public String writeArray(List<String> elements) {
        // TODO(Huxley): implement: joining the elements in square brackets
        return "";
    }
}
