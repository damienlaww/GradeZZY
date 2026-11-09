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
 *
 * <p>Tier: COULD.
 */
public class DemoIdCounter {

    // The pattern of an anomaly id: A- and the count in four digits
    private static final String ID_FORMAT = "A-%04d";

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
        return String.format(ID_FORMAT, count);
    }
}
