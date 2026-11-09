/*
 * File: LineKind.java
 *
 * Purpose: What one line of a student's file is, when two students'
 * files are compared line by line.
 *
 * Where it fits: Worked out by LineComparison and used by the Similar
 * code sheet to colour each line.
 */
package gradezzy.plagiarism;

/**
 * What one line of a compared file is.
 *
 * <p>Tier: SHOULD. Owner: Jing Yuan.
 */
public enum LineKind {
    /**
     * The same line is in the other student's file.
     */
    SAME,

    /**
     * The line is only in this student's file.
     */
    OWN,

    /**
     * The line is in the starter file, so it is not compared.
     */
    STARTER,

    /**
     * The line holds nothing but spaces.
     */
    BLANK
}
