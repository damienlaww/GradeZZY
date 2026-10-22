/*
 * File: ScoreFormatter.java
 *
 * Purpose: Writes a score the same way in every export.
 *
 * Where it fits: Shared by the exporters, so the CSV, JSON and reports all
 * show 12 for a whole score and 10.5 otherwise.
 */
package gradezzy.report;

/**
 * Writes a score the same way in every export.
 *
 * <p>Tier: SHOULD. Owner: Huxley.
 */
public final class ScoreFormatter {

    // A utility class is never created
    private ScoreFormatter() {
    }

    /**
     * Writes 12 for a whole score and 10.5 otherwise.
     *
     * @param score the score
     * @return the score as text
     */
    // Generated with Claude Opus 5.5; reviewed by Huxley
    public static String format(double score) {
        if (score == Math.floor(score) && !Double.isInfinite(score)) {
            return String.valueOf((long) score);
        }
        return String.valueOf(score);
    }
}
