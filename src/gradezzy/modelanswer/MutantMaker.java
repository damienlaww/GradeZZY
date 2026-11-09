/*
 * File: MutantMaker.java
 *
 * Purpose: Makes broken copies of a model answer file, each with one
 * small change of the kind students make: a comparison off by one ("<"
 * made "<="), an operator swapped ("+" made "-", "&&" made "||"), a
 * number changed by one, an "if" condition reversed, or a line removed.
 *
 * Where it fits: TesterStrengthChecker runs the tester on each copy. A
 * copy that still gets full marks shows a gap in the tester.
 */
package gradezzy.modelanswer;

import java.util.ArrayList;
import java.util.HashSet;
import java.util.List;
import java.util.Set;

import gradezzy.plagiarism.JavaToken;
import gradezzy.plagiarism.JavaTokenizer;

/**
 * Makes broken copies of a model answer file with standard mutation
 * rules applied to its tokens.
 *
 * <p>Only lines the student had to write are changed: a line that is
 * also in the starter file, and the main method, which testers never
 * call, are left alone, so their copies would only pad the count.
 *
 * <p>Tier: COULD. Owner: Jing Yuan.
 */
public class MutantMaker {

    // Each operator or word in SWAP_FROM is replaced by the one at the
    // same place in SWAP_TO: comparisons off by one or reversed, the
    // logical and arithmetic operators swapped, and true for false
    private static final String[] SWAP_FROM = {
        "<", "<=", ">", ">=", "==", "!=", "&&", "||", "+", "-", "*",
        "/", "%", "++", "--", "+=", "-=", "true", "false"
    };
    private static final String[] SWAP_TO = {
        "<=", "<", ">=", ">", "!=", "==", "||", "&&", "-", "+", "/",
        "*", "*", "--", "++", "-=", "+=", "false", "true"
    };

    // Operators written with two characters. The tokenizer splits every
    // operator into single characters, so two that touch are joined.
    private static final String[] TWO_CHARACTER_OPERATORS = {
        "<=", ">=", "==", "!=", "&&", "||", "++", "--", "+=", "-=",
        "*=", "/=", "%=", "->", "::"
    };

    // Words that start a statement that may be removed
    private static final String[] REMOVABLE_KEYWORDS = {
        "return", "break", "continue"
    };

    // Tokens that may follow a name at the start of a statement that may
    // be removed: an assignment, a change by one, or a method call
    private static final String[] AFTER_REMOVABLE_NAME = {
        "=", "+=", "-=", "*=", "/=", "%=", "++", "--", ".", "(", "["
    };

    // Tokens a statement starts after
    private static final String[] STATEMENT_STARTS = {";", "{", "}"};

    // The tokens around a condition and a block
    private static final String OPEN_PAREN = "(";
    private static final String CLOSE_PAREN = ")";
    private static final String OPEN_BRACE = "{";
    private static final String CLOSE_BRACE = "}";
    private static final String STATEMENT_END = ";";
    private static final String OPEN_ANGLE = "<";
    private static final String CLOSE_ANGLE = ">";
    private static final String PLUS = "+";

    // The words that mark an if statement and the main method
    private static final String IF_WORD = "if";
    private static final String VOID_WORD = "void";
    private static final String MAIN_WORD = "main";

    // What JavaTokenizer.shapeToken makes of a name and of a text literal
    private static final String NAME_SHAPE = "ID";
    private static final String TEXT_SHAPE = "STR";

    // The text that ends a line, and the prefix that reverses a condition
    private static final String NEW_LINE = "\n";
    private static final String NOT = "!";

    // Any run of spaces, removed to compare a line with the starter's
    private static final String SPACES = "\\s+";

    // A number literal made only of digits, such as 0 or 42
    private static final String DIGITS = "[0-9]+";

    // How much a number literal is changed by
    private static final long NUMBER_CHANGE = 1;

    // The position of the token after a given one
    private static final int NEXT = 1;

    // The number of the first line: a token counts its line from 1
    private static final int FIRST_LINE = 1;

    // The longest removed statement shown in a change, in characters
    private static final int MAX_SHOWN_LENGTH = 60;
    private static final String CUT_MARK = "...";

    // Returned when no matching token is found
    private static final int NOT_FOUND = -1;

    // Makes split keep empty lines at the end, so line numbers match
    private static final int KEEP_EMPTY_LINES = -1;

    // The quote put around a token in a change's words
    private static final String QUOTE = "\"";

    // What a run of spaces becomes in a shortened statement
    private static final String ONE_SPACE = " ";

    /**
     * Makes every broken copy of one file that the rules allow.
     *
     * <p>Tier: COULD.
     *
     * @param fileName the file's name, such as Q1.java
     * @param source the model answer's source
     * @param starterSource the starter file's source, or "" for none
     * @return the broken copies, in the order of the file
     */
    public List<Mutant> make(String fileName, String source,
            String starterSource) {
        List<JavaToken> tokens = joinOperators(JavaTokenizer.tokenize(
                source));
        String[] lines = source.split(NEW_LINE, KEEP_EMPTY_LINES);
        boolean[] changeable = findChangeable(tokens, lines, starterSource);
        boolean[] generic = findGenericBrackets(tokens);
        List<Mutant> mutants = new ArrayList<>();

        for (int index = 0; index < tokens.size(); index++) {
            if (changeable[index] && !generic[index]) {
                addSwap(mutants, fileName, source, lines, tokens, index);
                addNumberChange(mutants, fileName, source, lines,
                        tokens.get(index));
                addReversedIf(mutants, fileName, source, lines, tokens,
                        index);
                addRemovedStatement(mutants, fileName, source, lines,
                        tokens, index);
            }
        }

        return mutants;
    }

    // Joins single-character tokens that touch into the two-character
    // operators Java has, such as "<" and "=" into "<=".
    private List<JavaToken> joinOperators(List<JavaToken> tokens) {
        List<JavaToken> joined = new ArrayList<>();
        int index = 0;

        while (index < tokens.size()) {
            JavaToken token = tokens.get(index);
            int nextIndex = index + NEXT;
            JavaToken next = null;
            if (nextIndex < tokens.size()) {
                next = tokens.get(nextIndex);
            }

            if (next != null && isTwoCharacterOperator(token, next)) {
                String text = token.getText() + next.getText();
                joined.add(new JavaToken(text, token.getStart(),
                        token.getLine()));
                index = nextIndex + NEXT;
            } else {
                joined.add(token);
                index = nextIndex;
            }
        }

        return joined;
    }

    // Says whether two tokens touch and together make a two-character
    // operator.
    private boolean isTwoCharacterOperator(JavaToken first,
            JavaToken second) {
        boolean touching = first.getEnd() == second.getStart();
        String text = first.getText() + second.getText();
        return touching && contains(TWO_CHARACTER_OPERATORS, text);
    }

    // Marks the tokens that may be changed: those on a line the student
    // wrote, outside the main method.
    private boolean[] findChangeable(List<JavaToken> tokens, String[] lines,
            String starterSource) {
        Set<String> starterLines = collectLines(starterSource);
        boolean[] changeable = new boolean[tokens.size()];
        int mainEnd = NOT_FOUND;

        for (int index = 0; index < tokens.size(); index++) {
            if (index > mainEnd && startsMain(tokens, index)) {
                mainEnd = findBlockEnd(tokens, index);
            }

            String lineKey = removeSpaces(lineOf(lines,
                    tokens.get(index)));
            boolean inMain = index <= mainEnd;
            boolean fromStarter = starterLines.contains(lineKey);
            changeable[index] = !inMain && !fromStarter;
        }

        return changeable;
    }

    // Collects every line of a source, without spaces.
    private Set<String> collectLines(String source) {
        Set<String> lines = new HashSet<>();
        for (String line : source.split(NEW_LINE)) {
            lines.add(removeSpaces(line));
        }
        return lines;
    }

    // Says whether a token is the "void" of "void main(".
    private boolean startsMain(List<JavaToken> tokens, int index) {
        int nameIndex = index + NEXT;
        int parenIndex = nameIndex + NEXT;
        if (parenIndex >= tokens.size()) {
            return false;
        }

        boolean isVoid = tokens.get(index).getText().equals(VOID_WORD);
        boolean isMain = tokens.get(nameIndex).getText().equals(MAIN_WORD);
        boolean isCall = tokens.get(parenIndex).getText().equals(
                OPEN_PAREN);
        return isVoid && isMain && isCall;
    }

    // Returns the index of the "}" that closes the first block opened
    // after a token, or the last index when it is never closed.
    private int findBlockEnd(List<JavaToken> tokens, int from) {
        int open = findText(tokens, OPEN_BRACE, from);
        if (open == NOT_FOUND) {
            return tokens.size() - NEXT;
        }

        int close = findClosing(tokens, open, OPEN_BRACE, CLOSE_BRACE);
        if (close == NOT_FOUND) {
            return tokens.size() - NEXT;
        }
        return close;
    }

    // Marks the "<" and ">" of generic types, such as ArrayList<Integer>,
    // which are not comparisons. A "<" right after a name starting with a
    // capital letter, and followed by one or by ">", opens a type; the
    // ">" that closes it is marked too.
    private boolean[] findGenericBrackets(List<JavaToken> tokens) {
        boolean[] generic = new boolean[tokens.size()];
        int depth = 0;

        for (int index = 0; index < tokens.size(); index++) {
            String text = tokens.get(index).getText();
            if (text.equals(OPEN_ANGLE) && opensType(tokens, index)) {
                generic[index] = true;
                depth++;
            } else if (text.equals(CLOSE_ANGLE) && depth > 0) {
                generic[index] = true;
                depth--;
            }
        }

        return generic;
    }

    // Says whether the "<" at an index opens a generic type.
    private boolean opensType(List<JavaToken> tokens, int index) {
        int nextIndex = index + NEXT;
        if (index == 0 || nextIndex >= tokens.size()) {
            return false;
        }

        int beforeIndex = index - NEXT;
        String before = tokens.get(beforeIndex).getText();
        String after = tokens.get(nextIndex).getText();
        boolean typeBefore = Character.isUpperCase(before.charAt(0));
        boolean typeAfter = Character.isUpperCase(after.charAt(0))
                || after.equals(CLOSE_ANGLE);
        return typeBefore && typeAfter;
    }

    // Adds a copy with the operator or word at an index swapped, such as
    // "<" for "<=". A "+" next to text is joining strings, and "-" there
    // would not compile, so it is left alone.
    private void addSwap(List<Mutant> mutants, String fileName,
            String source, String[] lines, List<JavaToken> tokens,
            int index) {
        JavaToken token = tokens.get(index);
        int swap = indexOf(SWAP_FROM, token.getText());
        if (swap == NOT_FOUND) {
            return;
        }
        if (token.getText().equals(PLUS) && touchesText(tokens, index)) {
            return;
        }

        String replacement = SWAP_TO[swap];
        String change = quote(token.getText()) + " changed to "
                + quote(replacement);
        mutants.add(makeMutant(fileName, source, lines, token,
                token.getStart(), token.getEnd(), replacement, change));
    }

    // Says whether the token before or after an index is a text literal.
    private boolean touchesText(List<JavaToken> tokens, int index) {
        int beforeIndex = index - NEXT;
        int nextIndex = index + NEXT;
        boolean textBefore = index > 0
                && isText(tokens.get(beforeIndex));
        boolean textAfter = nextIndex < tokens.size()
                && isText(tokens.get(nextIndex));
        return textBefore || textAfter;
    }

    // Says whether a token is a string or char literal.
    private boolean isText(JavaToken token) {
        return JavaTokenizer.shapeToken(token.getText()).equals(TEXT_SHAPE);
    }

    // Adds a copy with a whole-number literal one larger, such as 0 made
    // 1, the classic off-by-one mistake.
    private void addNumberChange(List<Mutant> mutants, String fileName,
            String source, String[] lines, JavaToken token) {
        String text = token.getText();
        if (!text.matches(DIGITS)) {
            return;
        }

        long changed = 0;
        try {
            changed = Long.parseLong(text) + NUMBER_CHANGE;
        } catch (NumberFormatException exception) {
            return;
        }

        String replacement = String.valueOf(changed);
        String change = quote(text) + " changed to " + quote(replacement);
        mutants.add(makeMutant(fileName, source, lines, token,
                token.getStart(), token.getEnd(), replacement, change));
    }

    // Adds a copy with the condition of an "if" reversed, so its block
    // runs exactly when it should not.
    private void addReversedIf(List<Mutant> mutants, String fileName,
            String source, String[] lines, List<JavaToken> tokens,
            int index) {
        int openIndex = index + NEXT;
        boolean isIf = tokens.get(index).getText().equals(IF_WORD);
        boolean opensCondition = openIndex < tokens.size()
                && tokens.get(openIndex).getText().equals(OPEN_PAREN);
        if (!isIf || !opensCondition) {
            return;
        }

        int closeIndex = findClosing(tokens, openIndex, OPEN_PAREN,
                CLOSE_PAREN);
        if (closeIndex == NOT_FOUND) {
            return;
        }

        int conditionStart = tokens.get(openIndex).getEnd();
        int conditionEnd = tokens.get(closeIndex).getStart();
        String condition = source.substring(conditionStart, conditionEnd);
        String replacement = NOT + OPEN_PAREN + condition + CLOSE_PAREN;
        mutants.add(makeMutant(fileName, source, lines, tokens.get(index),
                conditionStart, conditionEnd, replacement,
                "the \"if\" condition reversed"));
    }

    // Adds a copy with the statement starting at an index removed, when
    // it is a return, break or continue, an assignment, a change by one
    // or a method call. Declarations that start with a type word, such as
    // "int count = 0;", are kept, since removing one only stops the copy
    // compiling; one that starts with a class name, such as
    // "String[] parts = ...;", can slip through, and its copy is left out
    // when it does not compile.
    private void addRemovedStatement(List<Mutant> mutants, String fileName,
            String source, String[] lines, List<JavaToken> tokens,
            int index) {
        boolean removable = startsStatement(tokens, index)
                && isRemovable(tokens, index);
        if (!removable) {
            return;
        }

        int endIndex = findStatementEnd(tokens, index);
        if (endIndex == NOT_FOUND) {
            return;
        }

        JavaToken first = tokens.get(index);
        int end = tokens.get(endIndex).getEnd();
        String statement = source.substring(first.getStart(), end);
        String change = "line removed: " + shorten(statement);
        mutants.add(makeMutant(fileName, source, lines, first,
                first.getStart(), end, "", change));
    }

    // Says whether a statement starts at an index: the first token, or
    // the token after ";", "{" or "}".
    private boolean startsStatement(List<JavaToken> tokens, int index) {
        if (index == 0) {
            return true;
        }
        int beforeIndex = index - NEXT;
        String before = tokens.get(beforeIndex).getText();
        return contains(STATEMENT_STARTS, before);
    }

    // Says whether the statement at an index is one that may be removed.
    private boolean isRemovable(List<JavaToken> tokens, int index) {
        String first = tokens.get(index).getText();
        if (contains(REMOVABLE_KEYWORDS, first)) {
            return true;
        }

        int nextIndex = index + NEXT;
        if (nextIndex >= tokens.size()) {
            return false;
        }

        boolean name = JavaTokenizer.shapeToken(first).equals(NAME_SHAPE);
        String next = tokens.get(nextIndex).getText();
        return name && contains(AFTER_REMOVABLE_NAME, next);
    }

    // Returns the index of the ";" that ends the statement starting at an
    // index, outside any brackets, or NOT_FOUND when a block starts or
    // ends first.
    private int findStatementEnd(List<JavaToken> tokens, int from) {
        int depth = 0;

        for (int index = from; index < tokens.size(); index++) {
            String text = tokens.get(index).getText();
            boolean isBrace = text.equals(OPEN_BRACE)
                    || text.equals(CLOSE_BRACE);
            if (text.equals(OPEN_PAREN)) {
                depth++;
            } else if (text.equals(CLOSE_PAREN)) {
                depth--;
            } else if (isBrace) {
                return NOT_FOUND;
            } else if (text.equals(STATEMENT_END) && depth == 0) {
                return index;
            }
        }

        return NOT_FOUND;
    }

    // Returns the index of the token that closes the bracket at an
    // index, or NOT_FOUND when it is never closed.
    private int findClosing(List<JavaToken> tokens, int openIndex,
            String open, String close) {
        int depth = 0;

        for (int index = openIndex; index < tokens.size(); index++) {
            String text = tokens.get(index).getText();
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
    private int findText(List<JavaToken> tokens, String text, int from) {
        for (int index = from; index < tokens.size(); index++) {
            if (tokens.get(index).getText().equals(text)) {
                return index;
            }
        }
        return NOT_FOUND;
    }

    // Makes a copy of the source with the text from start to end
    // replaced.
    private Mutant makeMutant(String fileName, String source,
            String[] lines, JavaToken token, int start, int end,
            String replacement, String change) {
        String changed = source.substring(0, start) + replacement
                + source.substring(end);
        String line = lineOf(lines, token).trim();
        return new Mutant(fileName, token.getLine(), line, change,
                changed);
    }

    // Returns the line a token is on.
    private String lineOf(String[] lines, JavaToken token) {
        int lineIndex = token.getLine() - FIRST_LINE;
        if (lineIndex >= lines.length) {
            return "";
        }
        return lines[lineIndex];
    }

    /**
     * Keeps every copy when there are at most limit of them, or else
     * limit copies spread evenly from the first to the last, so every
     * part of the answer is still tried.
     *
     * <p>Tier: COULD.
     *
     * @param mutants the copies, in the order of the files
     * @param limit the most copies to keep
     * @return the copies kept
     */
    public List<Mutant> spreadOut(List<Mutant> mutants, int limit) {
        if (mutants.size() <= limit) {
            return mutants;
        }

        List<Mutant> chosen = new ArrayList<>();
        for (int count = 0; count < limit; count++) {
            int index = count * mutants.size() / limit;
            chosen.add(mutants.get(index));
        }
        return chosen;
    }

    // Writes a removed statement on one line, cut short when it is long.
    private String shorten(String statement) {
        String oneLine = statement.replaceAll(SPACES, ONE_SPACE).trim();
        if (oneLine.length() <= MAX_SHOWN_LENGTH) {
            return oneLine;
        }
        return oneLine.substring(0, MAX_SHOWN_LENGTH) + CUT_MARK;
    }

    // Puts double quotes around a token for the change's words.
    private String quote(String text) {
        return QUOTE + text + QUOTE;
    }

    // Removes every space from a line, so spacing does not matter when
    // it is compared with the starter's lines.
    private String removeSpaces(String line) {
        return line.replaceAll(SPACES, "");
    }

    // Returns where a text is in a list, or NOT_FOUND.
    private int indexOf(String[] values, String text) {
        for (int index = 0; index < values.length; index++) {
            if (values[index].equals(text)) {
                return index;
            }
        }
        return NOT_FOUND;
    }

    // Says whether a list holds a text.
    private boolean contains(String[] values, String text) {
        return indexOf(values, text) != NOT_FOUND;
    }
}
