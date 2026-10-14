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
 * <p>Tier: MUST. Owner: Seth.
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
        // TODO(Seth): implement: the blank check
        return false;
    }

    /**
     * Splits one CSV line into its values.
     *
     * @param line the line
     * @return the values of the line
     */
    public static List<String> splitCsvLine(String line) {
        // TODO(Seth): implement: splitting on commas while respecting quoted
        //     values
        return new ArrayList<>();
    }

    /**
     * Joins values into one CSV line.
     *
     * @param values the values
     * @return the CSV line, without a line ending
     */
    public static String joinCsvLine(List<String> values) {
        // TODO(Seth): implement: joining with commas and quoting values that
        //     need it
        return "";
    }

    /**
     * Converts all line endings to a single newline character.
     *
     * @param text the text
     * @return the text with uniform line endings
     */
    public static String normaliseLineEndings(String text) {
        // TODO(Seth): implement: replacing Windows and old Mac line endings
        return "";
    }
}
