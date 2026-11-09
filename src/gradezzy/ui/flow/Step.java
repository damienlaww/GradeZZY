/*
 * File: Step.java
 *
 * Purpose: The four steps of grading a section, in order.
 *
 * Where it fits: The step bar shows them and FlowWindow moves between
 * them. Each step has one screen.
 */
package gradezzy.ui.flow;

/**
 * One step of grading a section.
 *
 * <p>Tier: SHOULD.
 */
public enum Step {

    /** Choose the class and add the files. */
    SET_UP("Set up"),

    /** Grade every submission. */
    GRADE("Grade"),

    /** Check fixes, flags and failed submissions. */
    REVIEW("Review"),

    /** Export the score sheet and reports. */
    EXPORT("Export");

    // The name shown in the step bar
    private final String label;

    // Creates a step constant.
    Step(String label) {
        this.label = label;
    }

    /**
     * Returns the name shown in the step bar.
     *
     * @return the name
     */
    public String getLabel() {
        return label;
    }

    /**
     * Returns the number shown in the step bar, starting at 1.
     *
     * @return the step number
     */
    public int getNumber() {
        return ordinal() + 1;
    }

    /**
     * Returns the step after this one, or this step if it is the last.
     *
     * @return the next step
     */
    public Step next() {
        Step[] steps = values();
        int nextIndex = ordinal() + 1;
        if (nextIndex >= steps.length) {
            return this;
        }
        return steps[nextIndex];
    }
}
