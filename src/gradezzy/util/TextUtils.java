/*
 * File: TextUtils.java
 *
 * Purpose: Small text helpers shared by several packages.
 *
 * Where it fits: Holds the CSV line handling used by the roster reader and the
 * exporters.
 */
package gradezzy.util;

import java.util.ArrayList;
import java.util.List;

/**
 * Small text helpers shared by several packages.
 *
 * <p>Tier: MUST. Owner: Damien.
 */
public final class TextUtils {

    /**
     * Not used: every method here is static.
     */
    private TextUtils() {
        // utility class, never instantiated
    }

    /**
     * Says whether text is null, empty or only spaces.
     *
     * @param text the text
     * @return true if there is no real text
     */
    public static boolean isBlank(String text) {
        return text == null || text.isBlank();
    }

    /**
     * Splits one CSV record into values, retaining empty fields. Doubled
     * quotes escape quotes inside a quoted field. Line endings are allowed
     * only inside quotes; spaces outside closing quotes are rejected.
     *
     * @param line the line
     * @return the values of the line
     * @throws IllegalArgumentException if quotes are malformed or a field
     *         contains an unquoted line ending
     */
    public static List<String> splitCsvLine(String line) {
        if (line == null) {
            throw new IllegalArgumentException("CSV line must not be null.");
        }

        List<String> values = new ArrayList<>();
        StringBuilder value = new StringBuilder();
        boolean quoted = false;
        boolean closedQuote = false;

        for (int index = 0; index < line.length(); index++) {
            char current = line.charAt(index);
            if (quoted) {
                if (current != '"') {
                    value.append(current);
                } else if (index + 1 < line.length()
                        && line.charAt(index + 1) == '"') {
                    value.append('"');
                    index++;
                } else {
                    quoted = false;
                    closedQuote = true;
                }
            } else if (current == ',') {
                values.add(value.toString());
                value.setLength(0);
                closedQuote = false;
            } else if (closedQuote || current == '\r' || current == '\n') {
                throw new IllegalArgumentException("Malformed CSV field.");
            } else if (current == '"') {
                if (!value.isEmpty()) {
                    throw new IllegalArgumentException(
                            "A CSV quote must start a field.");
                }
                quoted = true;
            } else {
                value.append(current);
            }
        }

        if (quoted) {
            throw new IllegalArgumentException("Unclosed CSV quote.");
        }
        values.add(value.toString());
        return values;
    }

    /**
     * Joins values into one CSV line.
     *
     * @param values the values
     * @return the CSV line, without a line ending
     * @throws IllegalArgumentException if a value is null
     */
    public static String joinCsvLine(List<String> values) {
        List<String> fields = new ArrayList<>();
        for (String value : values) {
            if (value == null) {
                throw new IllegalArgumentException(
                        "CSV values must not be null.");
            }
            if (value.contains(",") || value.contains("\"")
                    || value.contains("\r") || value.contains("\n")) {
                fields.add("\"" + value.replace("\"", "\"\"") + "\"");
            } else {
                fields.add(value);
            }
        }
        return String.join(",", fields);
    }

    /**
     * Converts all line endings to a single newline character.
     *
     * @param text the text
     * @return the text with uniform line endings
     */
    public static String normaliseLineEndings(String text) {
        return text.replace("\r\n", "\n").replace('\r', '\n');
    }
}
