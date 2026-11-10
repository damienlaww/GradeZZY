/*
 * File: LineComparison.java
 *
 * Purpose: Compares two students' files line by line, so the lines that
 * are the same on both sides, apart from names, can be shown to the
 * instructor.
 *
 * Where it fits: Built by the Similar code sheet from a
 * SimilarityDetail. The similarity score itself comes from the
 * configured SimilarityDetector; this only helps the instructor read a
 * pair. It uses the same rename-proof k-grams as
 * WinnowingSimilarityDetector, so a renamed line is still marked.
 */
package gradezzy.plagiarism;

import java.util.ArrayList;
import java.util.HashSet;
import java.util.List;
import java.util.Set;

import gradezzy.util.TextUtils;

/**
 * The lines of two files, each marked same, own, starter or blank.
 *
 * <p>A line is same when the other file holds the same line, ignoring
 * spaces, or when every token on it is inside a run of
 * Winnowing.KGRAM_LENGTH rename-proof tokens that is also in the other
 * file, unless that run is written word for word in the starter file.
 * So a line whose only change is a renamed variable is still same.
 *
 * <p>A line in the starter file is starter code and is never marked
 * same. A starter comment line ending in a colon, such as " * Name:", is
 * a label the student fills in, so any line starting with it is starter
 * code too. A line holding only closing brackets, such as "}" or "});",
 * takes the kind of the line above it, so a block that is the same reads
 * as one piece.
 *
 * <p>Tier: SHOULD. Owner: Jing Yuan.
 */
public class LineComparison {

    // The characters a line may hold and still count as brackets only
    private static final String BRACKET_CHARACTERS = "{}();";

    // The text that ends a line
    private static final String NEW_LINE = "\n";

    // What indexOf returns when a character is not found
    private static final int NOT_FOUND = -1;

    // The text that ends a starter label, such as "Name:"
    private static final String LABEL_END = ":";

    // How a block comment line starts once spaces are removed
    private static final String BLOCK_COMMENT_START = "*";

    // How a line comment starts
    private static final String LINE_COMMENT_START = "//";

    // The number of the first line of a file
    private static final int FIRST_LINE = 1;

    // The first file's lines
    private final List<String> firstLines;

    // The second file's lines
    private final List<String> secondLines;

    // The kind of each of the first file's lines
    private final List<LineKind> firstKinds;

    // The kind of each of the second file's lines
    private final List<LineKind> secondKinds;

    /**
     * Compares two files.
     *
     * <p>Tier: SHOULD.
     *
     * @param firstCode the first student's code
     * @param secondCode the second student's code
     * @param starterCode the starter file's code, or "" when there is none
     */
    public LineComparison(String firstCode, String secondCode,
            String starterCode) {
        firstLines = splitLines(firstCode);
        secondLines = splitLines(secondCode);
        Set<String> starter = collectLines(splitLines(starterCode));
        List<String> labels = collectLabels(starter);

        List<JavaToken> firstTokens = tokenize(firstCode);
        List<JavaToken> secondTokens = tokenize(secondCode);
        Set<Integer> starterKGrams = new HashSet<>(hashWrittenKGrams(
                tokenize(starterCode)));
        Set<Integer> firstKGrams = new HashSet<>(hashKGrams(firstTokens));
        Set<Integer> secondKGrams = new HashSet<>(hashKGrams(secondTokens));
        Set<Integer> firstShared = findSharedLines(firstTokens,
                secondKGrams, starterKGrams);
        Set<Integer> secondShared = findSharedLines(secondTokens,
                firstKGrams, starterKGrams);

        firstKinds = markLines(firstLines, collectLines(secondLines),
                firstShared, starter, labels);
        secondKinds = markLines(secondLines, collectLines(firstLines),
                secondShared, starter, labels);
    }

    /**
     * Returns the first file's lines.
     *
     * <p>Tier: SHOULD.
     *
     * @return the lines, without line endings
     */
    public List<String> getFirstLines() {
        return firstLines;
    }

    /**
     * Returns the second file's lines.
     *
     * <p>Tier: SHOULD.
     *
     * @return the lines, without line endings
     */
    public List<String> getSecondLines() {
        return secondLines;
    }

    /**
     * Returns the kind of each of the first file's lines.
     *
     * <p>Tier: SHOULD.
     *
     * @return one kind per line
     */
    public List<LineKind> getFirstKinds() {
        return firstKinds;
    }

    /**
     * Returns the kind of each of the second file's lines.
     *
     * <p>Tier: SHOULD.
     *
     * @return one kind per line
     */
    public List<LineKind> getSecondKinds() {
        return secondKinds;
    }

    /**
     * Counts the lines of one kind, leaving out lines of brackets only.
     *
     * <p>Tier: SHOULD.
     *
     * @param lines a file's lines
     * @param kinds the kind of each of those lines
     * @param kind the kind to count
     * @return how many lines are of that kind and hold more than brackets
     */
    public static int countCode(List<String> lines, List<LineKind> kinds,
            LineKind kind) {
        int total = 0;
        for (int index = 0; index < kinds.size(); index++) {
            String key = removeSpaces(lines.get(index));
            boolean counted = kinds.get(index) == kind
                    && !isBracketsOnly(key);
            if (counted) {
                total++;
            }
        }
        return total;
    }

    /**
     * Finds where each part of same lines starts: a same line whose last
     * line with something on it is not the same.
     *
     * <p>Tier: SHOULD.
     *
     * @param kinds the kinds of a file's lines
     * @return the line indexes, from the top
     */
    public static List<Integer> findSameParts(List<LineKind> kinds) {
        List<Integer> starts = new ArrayList<>();
        LineKind previous = LineKind.BLANK;
        for (int index = 0; index < kinds.size(); index++) {
            LineKind kind = kinds.get(index);
            boolean startsPart = kind == LineKind.SAME
                    && previous != LineKind.SAME;
            if (startsPart) {
                starts.add(index);
            }
            if (kind != LineKind.BLANK) {
                previous = kind;
            }
        }
        return starts;
    }

    // Splits code into lines, whatever its line endings.
    private static List<String> splitLines(String code) {
        List<String> lines = new ArrayList<>();
        if (code == null || code.isEmpty()) {
            return lines;
        }

        String[] parts = TextUtils.normaliseLineEndings(code)
                .split(NEW_LINE);
        for (String part : parts) {
            lines.add(part);
        }
        return lines;
    }

    // Splits code into tokens, with every kind of line ending made a
    // plain new line first so the tokens' line numbers match splitLines.
    private static List<JavaToken> tokenize(String code) {
        if (code == null) {
            return new ArrayList<>();
        }
        return JavaTokenizer.tokenize(TextUtils.normaliseLineEndings(code));
    }

    // Hashes every k-gram of rename-proof tokens, as the winnowing check
    // does; the hash at position p is of the k-gram starting at token p.
    private static List<Integer> hashKGrams(List<JavaToken> tokens) {
        List<String> shapes = Winnowing.normalise(tokens);
        return Winnowing.hashKGrams(shapes, Winnowing.KGRAM_LENGTH);
    }

    // Hashes every k-gram of tokens as written, names and all, so only
    // code copied from the starter word for word matches the starter.
    private static List<Integer> hashWrittenKGrams(List<JavaToken> tokens) {
        List<String> texts = new ArrayList<>();
        for (JavaToken token : tokens) {
            texts.add(token.getText());
        }
        return Winnowing.hashKGrams(texts, Winnowing.KGRAM_LENGTH);
    }

    // Finds the lines, counted from 1, whose every token is inside a
    // rename-proof k-gram that the other file also has, leaving out
    // k-grams written word for word in the starter file.
    private static Set<Integer> findSharedLines(List<JavaToken> tokens,
            Set<Integer> otherKGrams, Set<Integer> starterKGrams) {
        List<Integer> hashes = hashKGrams(tokens);
        List<Integer> writtenHashes = hashWrittenKGrams(tokens);
        boolean[] covered = new boolean[tokens.size()];

        for (int start = 0; start < hashes.size(); start++) {
            boolean inOtherFile = otherKGrams.contains(hashes.get(start));
            boolean inStarter = starterKGrams.contains(
                    writtenHashes.get(start));
            boolean shared = inOtherFile && !inStarter;
            if (shared) {
                markCovered(covered, start);
            }
        }

        return collectCoveredLines(tokens, covered);
    }

    // Marks the tokens of the k-gram that starts at a position.
    private static void markCovered(boolean[] covered, int start) {
        int end = start + Winnowing.KGRAM_LENGTH;
        for (int position = start; position < end; position++) {
            covered[position] = true;
        }
    }

    // Collects the lines that hold at least one token and no token left
    // uncovered.
    private static Set<Integer> collectCoveredLines(List<JavaToken> tokens,
            boolean[] covered) {
        Set<Integer> coveredLines = new HashSet<>();
        Set<Integer> uncoveredLines = new HashSet<>();

        for (int position = 0; position < tokens.size(); position++) {
            int line = tokens.get(position).getLine();
            if (covered[position]) {
                coveredLines.add(line);
            } else {
                uncoveredLines.add(line);
            }
        }

        coveredLines.removeAll(uncoveredLines);
        return coveredLines;
    }

    // Collects the lines with something on them, with spaces removed.
    private static Set<String> collectLines(List<String> lines) {
        Set<String> found = new HashSet<>();
        for (String line : lines) {
            String key = removeSpaces(line);
            if (!key.isEmpty()) {
                found.add(key);
            }
        }
        return found;
    }

    // Collects the starter comment lines that end in a colon, such as
    // "*Name:".
    private static List<String> collectLabels(Set<String> starter) {
        List<String> labels = new ArrayList<>();
        for (String key : starter) {
            if (isLabel(key)) {
                labels.add(key);
            }
        }
        return labels;
    }

    // Says whether a line is a comment line that ends in a colon.
    private static boolean isLabel(String key) {
        boolean comment = key.startsWith(BLOCK_COMMENT_START)
                || key.startsWith(LINE_COMMENT_START);
        return comment && key.endsWith(LABEL_END);
    }

    // Says whether a line is starter code: a starter line, or a label
    // with the student's answer after it.
    private static boolean isStarter(String key, Set<String> starter,
            List<String> labels) {
        if (starter.contains(key)) {
            return true;
        }
        for (String label : labels) {
            if (key.startsWith(label)) {
                return true;
            }
        }
        return false;
    }

    // Works out the kind of every line of one file. sharedLines holds
    // the lines, counted from 1, that findSharedLines found.
    private static List<LineKind> markLines(List<String> lines,
            Set<String> otherLines, Set<Integer> sharedLines,
            Set<String> starter, List<String> labels) {
        List<LineKind> kinds = new ArrayList<>();
        LineKind previous = LineKind.OWN;
        for (int index = 0; index < lines.size(); index++) {
            String key = removeSpaces(lines.get(index));
            int lineNumber = index + FIRST_LINE;
            boolean same = otherLines.contains(key)
                    || sharedLines.contains(lineNumber);
            LineKind kind = LineKind.OWN;
            if (key.isEmpty()) {
                kind = LineKind.BLANK;
            } else if (isBracketsOnly(key)) {
                kind = previous;
            } else if (isStarter(key, starter, labels)) {
                kind = LineKind.STARTER;
            } else if (same) {
                kind = LineKind.SAME;
            }

            if (kind != LineKind.BLANK) {
                previous = kind;
            }
            kinds.add(kind);
        }
        return kinds;
    }

    // Removes every space and tab from a line.
    private static String removeSpaces(String line) {
        String result = "";
        for (int index = 0; index < line.length(); index++) {
            char letter = line.charAt(index);
            if (!Character.isWhitespace(letter)) {
                result += letter;
            }
        }
        return result;
    }

    // Says whether a line, with spaces removed, holds only brackets and
    // semicolons.
    private static boolean isBracketsOnly(String key) {
        for (int index = 0; index < key.length(); index++) {
            char letter = key.charAt(index);
            if (BRACKET_CHARACTERS.indexOf(letter) == NOT_FOUND) {
                return false;
            }
        }
        return true;
    }
}
