/*
 * File: ProgressEstimator.java
 *
 * Purpose: Tracks how far a run is and estimates the time left.
 *
 * Where it fits: Feeds the progress bar and the estimated time of completion.
 */
package gradezzy.service;

/**
 * Tracks how far a run is and estimates the time left.
 *
 * <p>Tier: SHOULD. Owner: Jing Yuan.
 */
public class ProgressEstimator {

    /**
     * Creates a new progress estimator.
     */
    public ProgressEstimator() {
        // nothing to set up yet
    }

    /**
     * Starts timing a run of the given size.
     *
     * @param totalItems the total items
     */
    public void start(int totalItems) {
        // TODO(Jing Yuan): implement: storing the total and the start time
    }

    /**
     * Records that one more submission is done.
     */
    public void itemFinished() {
        // TODO(Jing Yuan): implement: increasing the finished count
    }

    /**
     * Returns how much of the run is done.
     *
     * @return a value from 0 to 1
     */
    public double getFractionDone() {
        // TODO(Jing Yuan): implement: finished divided by total, 0 when total
        //     is 0
        return 0.0;
    }

    /**
     * Estimates the time left from the average so far.
     *
     * @return the estimated seconds remaining
     */
    public long estimateSecondsRemaining() {
        // TODO(Jing Yuan): implement: average seconds per item times items
        //     remaining
        return 0;
    }
}
