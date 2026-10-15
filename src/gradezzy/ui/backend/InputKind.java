/*
 * File: InputKind.java
 *
 * Purpose: Names the four groups of files the instructor adds before
 * grading.
 *
 * Where it fits: The Submissions screen shows one group per constant,
 * and ImportBatch keeps the chosen files apart by group.
 *
 * AI use: drafted with Claude Code, reviewed by Jun Sheng.
 */
package gradezzy.ui.backend;

/**
 * A group of input files.
 *
 * <p>Tier: MUST. Owner: Damien.
 */
public enum InputKind {

    /** The students' ZIP files. Required. */
    SUBMISSION("Student ZIP submissions", true),

    /** The score sheet that lists the students. Required. */
    ROSTER("Score sheet or roster", true),

    /** The tester files. Required. */
    TESTERS("Tester files", true),

    /** The starter folder, used to spot untouched answers. Optional. */
    STARTER("Starter resources", false);

    // The heading shown on the Submissions screen
    private final String title;

    // Whether grading needs at least one file of this kind
    private final boolean required;

    // Creates an input kind constant.
    InputKind(String title, boolean required) {
        this.title = title;
        this.required = required;
    }

    /**
     * Returns the heading shown on the Submissions screen.
     *
     * @return the title
     */
    public String getTitle() {
        return title;
    }

    /**
     * Says whether grading needs at least one file of this kind.
     *
     * @return true for required groups
     */
    public boolean isRequired() {
        return required;
    }
}
