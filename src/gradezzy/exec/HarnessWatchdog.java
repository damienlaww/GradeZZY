/*
 * File: HarnessWatchdog.java
 *
 * Purpose: Ends a tester run by itself once its time is up, even if
 * GradeZZY is no longer there to stop it.
 *
 * Where it fits: TesterHarness starts it on its own thread before any
 * student code runs. GradeZZY normally stops a run at the time limit; this
 * is the backup for when GradeZZY crashed or was force-quit.
 */
package gradezzy.exec;

/**
 * Waits until a deadline, then halts the tester's program.
 *
 * <p>Tier: SHOULD. Owner: Zach.
 */
public class HarnessWatchdog implements Runnable {

    /**
     * Exit code of a run the watchdog ended, the one the Unix timeout
     * command uses.
     */
    public static final int WATCHDOG_EXIT_CODE = 124;

    // How long to wait before halting
    private final long waitMillis;

    /**
     * Creates a watchdog.
     *
     * @param waitMillis how long the run may last, in milliseconds
     */
    public HarnessWatchdog(long waitMillis) {
        this.waitMillis = waitMillis;
    }

    /**
     * Sleeps until the deadline, then halts. Student code can interrupt
     * this thread, so an interrupted sleep simply goes on until the
     * deadline.
     */
    @Override
    public void run() {
        long deadline = System.currentTimeMillis() + waitMillis;
        long remaining = waitMillis;
        while (remaining > 0) {
            try {
                Thread.sleep(remaining);
            } catch (InterruptedException exception) {
                // Student code interrupted the sleep: keep waiting
                remaining = deadline - System.currentTimeMillis();
            }
            remaining = deadline - System.currentTimeMillis();
        }
        Runtime.getRuntime().halt(WATCHDOG_EXIT_CODE);
    }
}
