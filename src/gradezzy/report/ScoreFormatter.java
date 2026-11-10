/*
 * File: ScoreFormatter.java
 *
 * Purpose: Writes a score the same way in every export.
 *
 * Where it fits: Shared by the exporters, so the CSV, JSON and reports all
 * show 12 for a whole score and 10.5 otherwise.
 */
package gradezzy.report;

import java.util.Locale;

/**
 * Writes a score the same way in every export.
 *
 * <p>Tier: SHOULD. Owner: Huxley.
 */
public final class ScoreFormatter {

    // Scores are rounded to this many places after the decimal point
    private static final int DECIMAL_PLACES = 2;

    // Always a point, never an exponent, for example 0.30 or 15.00
    private static final String ROUNDED_FORMAT = "%." + DECIMAL_PLACES
            + "f";

    // What a tiny negative score looks like after rounding
    private static final String NEGATIVE_ZERO = "-0";

    // A trailing zero after the point, and the text of a zero score
    private static final String ZERO = "0";

    // The point that the format always writes
    private static final String DECIMAL_POINT = ".";

    // A utility class is never created
    private ScoreFormatter() {
    }

    /**
     * Writes 12 for a whole score and 10.5 otherwise, rounded to 2
     * decimal places, so 0.1 + 0.2 is 0.3 and 0.00001 is 0. Never writes
     * an exponent such as 1.0E-5.
     *
     * @param score the score
     * @return the score as text
     */
    public static String format(double score) {
        if (Double.isNaN(score) || Double.isInfinite(score)) {
            return String.valueOf(score);
        }

        // Locale.ROOT keeps a point, never a comma, whatever the computer
        String text = String.format(Locale.ROOT, ROUNDED_FORMAT, score);

        // Drop the zeros after the point, then the point itself. The
        // format always writes a point, so the loop stops there.
        while (text.endsWith(ZERO)) {
            int lastIndex = text.length() - 1;
            text = text.substring(0, lastIndex);
        }
        if (text.endsWith(DECIMAL_POINT)) {
            int lastIndex = text.length() - 1;
            text = text.substring(0, lastIndex);
        }

        if (text.equals(NEGATIVE_ZERO)) {
            return ZERO;
        }
        return text;
    }
}
