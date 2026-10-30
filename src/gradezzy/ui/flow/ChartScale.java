/*
 * File: ChartScale.java
 *
 * Purpose: Maps a range of values, such as marks or numbers of students,
 * onto a range of pixels.
 *
 * Where it fits: BellCurveChart makes one for each axis every time it
 * draws, and places its bars, lines and labels with it.
 */
package gradezzy.ui.flow;

/**
 * Maps a range of values onto a range of pixels.
 *
 * <p>Tier: SHOULD.
 */
public final class ChartScale {

    // The value range
    private final double fromValue;
    private final double toValue;

    // The pixel range
    private final double fromPixel;
    private final double toPixel;

    /**
     * Creates the mapping.
     *
     * @param fromValue the value drawn at fromPixel
     * @param toValue the value drawn at toPixel
     * @param fromPixel the pixel of fromValue
     * @param toPixel the pixel of toValue
     */
    public ChartScale(double fromValue, double toValue, double fromPixel,
            double toPixel) {
        this.fromValue = fromValue;
        this.toValue = toValue;
        this.fromPixel = fromPixel;
        this.toPixel = toPixel;
    }

    /**
     * Returns the pixel of a value.
     *
     * <p>Tier: SHOULD.
     *
     * @param value the value
     * @return the pixel
     */
    public double map(double value) {
        double share = (value - fromValue) / (toValue - fromValue);
        return fromPixel + share * (toPixel - fromPixel);
    }

    /**
     * Returns the pixel of a value kept inside the range.
     *
     * <p>Tier: SHOULD.
     *
     * @param value the value
     * @return the pixel, never outside the pixel range
     */
    public double clampMap(double value) {
        double low = Math.min(fromValue, toValue);
        double high = Math.max(fromValue, toValue);
        return map(Math.max(low, Math.min(high, value)));
    }
}
