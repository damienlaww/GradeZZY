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
        String value = "";
        boolean quoted = false;
        boolean closedQuote = false;

        for (int index = 0; index < line.length(); index++) {
            char current = line.charAt(index);
            if (quoted) {
                if (current != '"') {
                    value += current;
                } else if (index + 1 < line.length()
                        && line.charAt(index + 1) == '"') {
                    value += '"';
                    index++;
                } else {
                    quoted = false;
                    closedQuote = true;
                }
            } else if (current == ',') {
                values.add(value);
                value = "";
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
                value += current;
            }
        }

        if (quoted) {
            throw new IllegalArgumentException("Unclosed CSV quote.");
        }
        values.add(value);
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
        return join(",", fields);
    }

    /**
     * Joins values into one CSV line, for a row that is written as a fixed
     * array, such as a header.
     *
     * @param values the values
     * @return the CSV line, without a line ending
     * @throws IllegalArgumentException if a value is null
     */
    public static String joinCsvLine(String[] values) {
        List<String> list = new ArrayList<>();
        for (String value : values) {
            list.add(value);
        }
        return joinCsvLine(list);
    }

    /**
     * Joins values into one text, with the separator between each pair.
     * An empty list gives empty text and a null value is written as null.
     *
     * <p>Tier: MUST.
     *
     * @param separator the text put between two values
     * @param values the values, in order
     * @return the values joined together
     */
    public static String join(String separator, List<String> values) {
        String[] texts = new String[values.size()];
        for (int index = 0; index < values.size(); index++) {
            texts[index] = values.get(index);
        }
        return join(separator, texts);
    }

    /**
     * Joins values into one text, with the separator between each pair.
     * An empty array gives empty text and a null value is written as null.
     * The characters are copied into one array of the final length, so
     * joining many long values (a whole JSON export) stays quick.
     *
     * <p>Tier: MUST.
     *
     * @param separator the text put between two values
     * @param values the values, in order
     * @return the values joined together
     */
    public static String join(String separator, String[] values) {
        int length = 0;
        for (int index = 0; index < values.length; index++) {
            if (index > 0) {
                length += separator.length();
            }
            length += String.valueOf(values[index]).length();
        }

        char[] joined = new char[length];
        int count = 0;
        for (int index = 0; index < values.length; index++) {
            if (index > 0) {
                count = copyInto(separator, joined, count);
            }
            count = copyInto(String.valueOf(values[index]), joined, count);
        }
        return new String(joined);
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

    // Copies the characters of text into target, starting at start, and
    // returns the position just after the last one copied.
    private static int copyInto(String text, char[] target, int start) {
        for (int index = 0; index < text.length(); index++) {
            target[start + index] = text.charAt(index);
        }
        return start + text.length();
    }
}
