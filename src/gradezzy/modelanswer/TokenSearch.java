/*
 * File: TokenSearch.java
 *
 * Purpose: The searches through the tokens of a Java file that more than
 * one class needs: the token that closes a bracket, the next token with
 * a text, and whether a list of words holds a text. It also names the
 * brackets and the ";" those classes look for.
 *
 * Where it fits: SignatureReader uses it to find the methods of a class,
 * and MutantMaker to find the blocks and conditions it changes.
 */
package gradezzy.modelanswer;

import java.util.List;

/**
 * Searches the texts of a Java file's tokens.
 *
 * <p>Tier: COULD. Owner: Jing Yuan.
 */
final class TokenSearch {

    // The brackets around parameters and conditions
    static final String OPEN_PAREN = "(";
    static final String CLOSE_PAREN = ")";

    // The brackets around a block
    static final String OPEN_BRACE = "{";
    static final String CLOSE_BRACE = "}";

    // The brackets around the element type of a generic type
    static final String OPEN_ANGLE = "<";
    static final String CLOSE_ANGLE = ">";

    // The token that ends a statement
    static final String STATEMENT_END = ";";

    // The position of the token after a given one
    static final int NEXT = 1;

    // Returned when no matching token or text is found
    static final int NOT_FOUND = -1;

    // Only static methods; never created
    private TokenSearch() {
    }

    // Returns the index of the token that closes the bracket at an index,
    // or NOT_FOUND when it is never closed.
    static int findClosing(List<String> tokens, int openIndex, String open,
            String close) {
        int depth = 0;

        for (int index = openIndex; index < tokens.size(); index++) {
            String text = tokens.get(index);
            if (text.equals(open)) {
                depth++;
            } else if (text.equals(close)) {
                depth--;
                if (depth == 0) {
                    return index;
                }
            }
        }

        return NOT_FOUND;
    }

    // Returns the index of the first token with a text from an index on,
    // or NOT_FOUND.
    static int findFrom(List<String> tokens, String text, int from) {
        for (int index = from; index < tokens.size(); index++) {
            if (tokens.get(index).equals(text)) {
                return index;
            }
        }
        return NOT_FOUND;
    }

    // Returns where a text is in a list, or NOT_FOUND.
    static int indexOf(String[] values, String text) {
        for (int index = 0; index < values.length; index++) {
            if (values[index].equals(text)) {
                return index;
            }
        }
        return NOT_FOUND;
    }

    // Says whether a list holds a text.
    static boolean contains(String[] values, String text) {
        return indexOf(values, text) != NOT_FOUND;
    }
}
