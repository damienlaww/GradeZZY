/*
 * File: CallResult.java
 *
 * Purpose: What one call of a method gave: the result printed as text,
 * or the class of the exception it threw.
 *
 * Where it fits: RandomInputChecker reads one from each line the input
 * program prints, and compares the student's with the model answer's.
 */
package gradezzy.modelanswer;

/**
 * What one call of a method gave.
 *
 * <p>Tier: COULD. Owner: Jing Yuan.
 */
public class CallResult {

    // Whether the call threw an exception
    private final boolean threw;

    // The result as text, or the exception's class when it threw
    private final String text;

    /**
     * Creates a call's result.
     *
     * <p>Tier: COULD.
     *
     * @param threw whether the call threw an exception
     * @param text the result as text, or the exception's class
     */
    public CallResult(boolean threw, String text) {
        this.threw = threw;
        this.text = text;
    }

    /**
     * Says whether the call threw an exception.
     *
     * <p>Tier: COULD.
     *
     * @return true when it threw
     */
    public boolean hasThrown() {
        return threw;
    }

    /**
     * Returns the result as text, or the exception's class when the call
     * threw.
     *
     * <p>Tier: COULD.
     *
     * @return the text
     */
    public String getText() {
        return text;
    }
}
