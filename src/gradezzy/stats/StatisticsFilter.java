/*
 * File: StatisticsFilter.java
 *
 * Purpose: Which runs the statistics should cover.
 *
 * Where it fits: Set from the module and section choosers on the statistics
 * screen.
 */
package gradezzy.stats;

/**
 * Which runs the statistics should cover.
 *
 * <p>Tier: SHOULD. Owner: Huxley.
 */
public class StatisticsFilter {

    // The module to include, or null for all
    private final String moduleCode;

    // The section to include, or null for all
    private final String sectionCode;

    /**
     * Creates a new statistics filter.
     *
     * @param moduleCode the module to include, or null for all
     * @param sectionCode the section to include, or null for all
     */
    public StatisticsFilter(String moduleCode, String sectionCode) {
        this.moduleCode = moduleCode;
        this.sectionCode = sectionCode;
    }

    /**
     * Returns the module to include, or null for all.
     *
     * @return the module to include, or null for all
     */
    public String getModuleCode() {
        return moduleCode;
    }

    /**
     * Returns the section to include, or null for all.
     *
     * @return the section to include, or null for all
     */
    public String getSectionCode() {
        return sectionCode;
    }
}
