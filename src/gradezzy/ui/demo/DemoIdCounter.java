/*
 * File: DemoIdCounter.java
 *
 * Purpose: Hands out unique anomaly ids in demo mode.
 *
 * Where it fits: DemoBackend keeps one and passes it to every DemoData
 * method that adds an anomaly, so no two anomalies share an id.
 */
package gradezzy.ui.demo;

/**
 * Hands out unique anomaly ids.
 */
public class DemoIdCounter {

    // The number of ids handed out so far
    private int count = 0;

    /**
     * Creates a counter that has handed out no ids yet.
     */
    public DemoIdCounter() {
        // nothing to set up
    }

    /**
     * Returns the next anomaly id.
     *
     * @return an id such as A-0007
     */
    public String next() {
        count++;
        return String.format("A-%04d", count);
    }
}
