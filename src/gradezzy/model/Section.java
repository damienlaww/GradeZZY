/*
 * File: Section.java
 *
 * Purpose: A teaching section of a module, such as CS102 G1.
 *
 * Where it fits: Chosen by the instructor for each upload batch; statistics and
 * filters group by it.
 */
package gradezzy.model;

/**
 * A teaching section of a module, such as CS102 G1.
 *
 * <p>Tier: MUST. Owner: Jing Yuan.
 */
public class Section {

    // The course code, for example CS102
    private final String moduleCode;

    // The section code, for example G1
    private final String sectionCode;

    /**
     * Creates a new section.
     *
     * <p>Tier: MUST.
     *
     * @param moduleCode the course code, for example CS102
     * @param sectionCode the section code, for example G1
     */
    public Section(String moduleCode, String sectionCode) {
        this.moduleCode = moduleCode;
        this.sectionCode = sectionCode;
    }

    /**
     * Returns the course code, for example CS102.
     *
     * <p>Tier: MUST.
     *
     * @return the course code, for example CS102
     */
    public String getModuleCode() {
        return moduleCode;
    }

    /**
     * Returns the section code, for example G1.
     *
     * <p>Tier: MUST.
     *
     * @return the section code, for example G1
     */
    public String getSectionCode() {
        return sectionCode;
    }

    /**
     * Returns the module and section as one label.
     *
     * <p>Tier: MUST.
     *
     * @return the label, for example CS102 G1
     */
    public String getLabel() {
        return moduleCode + " " + sectionCode;
    }
}
