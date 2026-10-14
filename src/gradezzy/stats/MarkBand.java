/*
 * File: MarkBand.java
 *
 * Purpose: The number of students whose mark falls in one range.
 *
 * Where it fits: One bar of the mark band chart.
 */
package gradezzy.stats;

/**
 * The number of students whose mark falls in one range.
 *
 * <p>Tier: SHOULD. Owner: Huxley.
 */
public class MarkBand {

    // The lower end of the range
    private final double lowerPercent;

    // The upper end of the range
    private final double upperPercent;

    // The number of students in the range
    private final int studentCount;

    /**
     * Creates a new mark band.
     *
     * @param lowerPercent the lower end of the range
     * @param upperPercent the upper end of the range
     * @param studentCount the number of students in the range
     */
    public MarkBand(double lowerPercent, double upperPercent,
            int studentCount) {
        this.lowerPercent = lowerPercent;
        this.upperPercent = upperPercent;
        this.studentCount = studentCount;
    }

    /**
     * Returns the lower end of the range.
     *
     * @return the lower end of the range
     */
    public double getLowerPercent() {
        return lowerPercent;
    }

    /**
     * Returns the upper end of the range.
     *
     * @return the upper end of the range
     */
    public double getUpperPercent() {
        return upperPercent;
    }

    /**
     * Returns the number of students in the range.
     *
     * @return the number of students in the range
     */
    public int getStudentCount() {
        return studentCount;
    }
}
