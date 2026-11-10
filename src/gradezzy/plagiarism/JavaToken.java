/*
 * File: JavaToken.java
 *
 * Purpose: One token of Java source, with where it starts and ends in the
 * text and the line it is on.
 *
 * Where it fits: Made by JavaTokenizer.tokenize. The similarity check
 * uses the line to show which lines match; the tester strength check
 * uses the start and end to change one token in a copy of the model
 * answer.
 */
package gradezzy.plagiarism;

/**
 * One token of Java source and where it is.
 *
 * <p>Tier: COULD. Owner: Jing Yuan.
 */
public class JavaToken {

    // The token as written, such as "<=" or "count"
    private final String text;

    // Where the token starts in the source, counted from 0
    private final int start;

    // The line the token starts on, counted from 1
    private final int line;

    /**
     * Creates a new token.
     *
     * <p>Tier: COULD.
     *
     * @param text the token as written
     * @param start where the token starts in the source, counted from 0
     * @param line the line the token starts on, counted from 1
     */
    public JavaToken(String text, int start, int line) {
        this.text = text;
        this.start = start;
        this.line = line;
    }

    /**
     * Returns the token as written.
     *
     * <p>Tier: COULD.
     *
     * @return the token as written
     */
    public String getText() {
        return text;
    }

    /**
     * Returns where the token starts in the source, counted from 0.
     *
     * <p>Tier: COULD.
     *
     * @return the start position
     */
    public int getStart() {
        return start;
    }

    /**
     * Returns the position just after the token's last character.
     *
     * <p>Tier: COULD.
     *
     * @return the end position
     */
    public int getEnd() {
        return start + text.length();
    }

    /**
     * Returns the line the token starts on, counted from 1.
     *
     * <p>Tier: COULD.
     *
     * @return the line number
     */
    public int getLine() {
        return line;
    }
}
