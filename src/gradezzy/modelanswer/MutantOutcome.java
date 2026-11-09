/*
 * File: MutantOutcome.java
 *
 * Purpose: What a tester did with one broken copy of the model answer.
 *
 * Where it fits: Set on each Mutant by TesterStrengthChecker and counted
 * by QuestionStrength.
 */
package gradezzy.modelanswer;

/**
 * What a tester did with one broken copy of the model answer.
 *
 * <p>Tier: COULD. Owner: Jing Yuan.
 */
public enum MutantOutcome {
    /**
     * Not run yet.
     */
    NOT_RUN,

    /**
     * The broken copy did not compile, so it says nothing about the
     * tester and is left out of the counts.
     */
    NOT_COMPILED,

    /**
     * The tester took marks off: it caught the mistake.
     */
    CAUGHT,

    /**
     * The tester was stopped at the time limit. This counts as caught,
     * since a student's answer like it would not get full marks either.
     */
    TIMED_OUT,

    /**
     * The tester still gave full marks: it never checks what was broken.
     */
    SURVIVED
}
