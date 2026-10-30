/*
 * File: JsonFields.java
 *
 * Purpose: The names and values of one JSON object, kept in the order they
 * were added.
 *
 * Where it fits: JsonExporter fills one of these for each object it writes
 * and JsonWriter turns it into text, so the fields of every object always
 * appear in the same order.
 */
package gradezzy.report;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;

/**
 * The names and values of one JSON object, in the order they were added.
 * A HashMap alone forgets the order, so the names are also kept in a list.
 *
 * <p>Tier: SHOULD. Owner: Huxley.
 */
public class JsonFields {

    // The names, in the order they were first added
    private final List<String> names = new ArrayList<>();

    // The ready-made value of each name
    private final HashMap<String, String> values = new HashMap<>();

    /**
     * Creates an empty set of fields.
     */
    public JsonFields() {
        // the list and the map start empty
    }

    /**
     * Adds a field at the end. Adding a name a second time replaces its
     * value but keeps its first position.
     *
     * @param name the name of the field
     * @param value the ready-made JSON value, or null for the JSON null
     */
    public void put(String name, String value) {
        if (!values.containsKey(name)) {
            names.add(name);
        }
        values.put(name, value);
    }

    /**
     * Returns the names in the order they were first added.
     *
     * @return the names
     */
    public List<String> getNames() {
        return names;
    }

    /**
     * Returns the value of a field.
     *
     * @param name the name of the field
     * @return the ready-made value, or null if it is null or missing
     */
    public String get(String name) {
        return values.get(name);
    }
}
