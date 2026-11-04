/*
 * File: Page.java
 *
 * Purpose: The five tabs along the top of the window: Home, Grading,
 * Statistics, Runs and Settings.
 *
 * Where it fits: FlowWindow shows one page at a time. Only Grading has
 * the four steps of Step.
 */
package gradezzy.ui.flow;

/**
 * A tab along the top of the window.
 */
public enum Page {

    /** Every section at a glance, and what to do next in each. */
    HOME("Home", Parts.HOME),

    /** The four marking steps. */
    MARKING("Grading", Parts.MARKING),

    /** Charts and figures of graded sections. */
    STATISTICS("Statistics", Parts.CHART),

    /** Earlier runs, backups and the activity log. */
    RUNS("History", Parts.CLOCK),

    /** Every setting in config.properties. */
    SETTINGS("Settings", Parts.GEAR);

    // The tab text
    private final String label;

    // The icon path drawn before the text
    private final String iconPath;

    // Creates a page with its tab text and icon.
    Page(String label, String iconPath) {
        this.label = label;
        this.iconPath = iconPath;
    }

    /**
     * Returns the tab text.
     *
     * @return the text
     */
    public String getLabel() {
        return label;
    }

    /**
     * Returns the icon path drawn before the tab text.
     *
     * @return one of the path constants of Parts
     */
    public String getIconPath() {
        return iconPath;
    }
}
