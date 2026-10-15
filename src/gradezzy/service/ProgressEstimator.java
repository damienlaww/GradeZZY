/*
 * File: ProgressEstimator.java
 *
 * Purpose: Tracks how far a run is and estimates the time left.
 *
 * Where it fits: Feeds the progress bar and the estimated time of completion.
 */
package gradezzy.service;

/**
 * Tracks how far a run is and estimates the time left. The estimate is
 * the average time per finished submission so far, multiplied by the
 * number of submissions left. One object is meant to be updated and read
 * by one caller at a time.
 *
 * <p>Tier: SHOULD. Owner: Jing Yuan.
 */
public class ProgressEstimator {

    /**
     * Returned by estimateSecondsRemaining when no submission has
     * finished yet, so there is no average to estimate from. A screen
     * should show something like "estimating" instead of a number.
     */
    public static final long UNKNOWN_SECONDS = -1;

    // Nanoseconds in one second
    private static final double NANOS_PER_SECOND = 1000000000.0;

    // The smallest number of submissions a run can hold
    private static final int NO_ITEMS = 0;

    // How many submissions the run holds
    private int totalItems = NO_ITEMS;

    // How many submissions are done
    private int finishedItems = NO_ITEMS;

    // When the run started, from System.nanoTime
    private long startNanos = 0;

    /**
     * Creates a new progress estimator.
     */
    public ProgressEstimator() {
        // nothing to set up yet
    }

    /**
     * Starts timing a run of the given size. A negative size counts as
     * an empty run.
     *
     * @param totalItems the total items
     */
    public void start(int totalItems) {
        this.totalItems = Math.max(NO_ITEMS, totalItems);
        finishedItems = NO_ITEMS;
        startNanos = System.nanoTime();
    }

    /**
     * Records that one more submission is done. Calls after the last
     * submission are ignored, so the fraction never passes 1.
     */
    public void itemFinished() {
        if (finishedItems < totalItems) {
            finishedItems++;
        }
    }

    /**
     * Returns how much of the run is done.
     *
     * @return a value from 0 to 1, and 0 for an empty run
     */
    public double getFractionDone() {
        if (totalItems == NO_ITEMS) {
            return 0.0;
        }

        return (double) finishedItems / totalItems;
    }

    /**
     * Estimates the time left from the average so far.
     *
     * @return the estimated seconds remaining, 0 when nothing is left,
     *         or UNKNOWN_SECONDS when no submission has finished yet
     */
    public long estimateSecondsRemaining() {
        int remainingItems = totalItems - finishedItems;

        if (remainingItems == NO_ITEMS) {
            return 0;
        }

        if (finishedItems == NO_ITEMS) {
            return UNKNOWN_SECONDS;
        }

        long elapsedNanos = System.nanoTime() - startNanos;
        double nanosPerItem = (double) elapsedNanos / finishedItems;

        return Math.round(nanosPerItem * remainingItems / NANOS_PER_SECOND);
    }
}
