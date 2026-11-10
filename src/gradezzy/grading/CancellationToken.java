/*
 * File: CancellationToken.java
 *
 * Purpose: A flag that tells a running batch to stop.
 *
 * Where it fits: The Cancel button sets it through GradingService and
 * GradingPipeline checks it before each submission and before each
 * stage. It lets the user interface stop grading without knowing how
 * the pipeline works.
 *
 * Note on untaught Java: the methods are synchronized because the flag is
 * set from the window while grading runs on another thread. That keyword
 * is not covered in CS102; it only makes sure both threads see the same
 * value.
 */
package gradezzy.grading;

/**
 * A flag that asks a running batch to stop at the next stage. The
 * submission being graded then becomes CANCELLED.
 *
 * <p>Tier: SHOULD. Owner: Jing Yuan.
 */
public class CancellationToken {

    // Whether cancellation has been requested
    private boolean cancelled = false;

    /**
     * Creates a token that is not cancelled.
     *
     * <p>Tier: SHOULD.
     */
    public CancellationToken() {
        // nothing to set up
    }

    /**
     * Asks the running batch to stop.
     *
     * <p>Tier: SHOULD.
     */
    public synchronized void cancel() {
        cancelled = true;
    }

    /**
     * Clears the flag so a new batch can start.
     *
     * <p>Tier: SHOULD.
     */
    public synchronized void reset() {
        cancelled = false;
    }

    /**
     * Says whether cancellation has been requested.
     *
     * <p>Tier: SHOULD.
     *
     * @return true if the batch should stop
     */
    public synchronized boolean isCancelled() {
        return cancelled;
    }
}
