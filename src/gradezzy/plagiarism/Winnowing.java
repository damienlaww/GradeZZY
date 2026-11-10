/*
 * File: Winnowing.java
 *
 * Purpose: The winnowing fingerprint algorithm of Schleimer, Wilkerson
 * and Aiken (2003), the one MOSS uses, over rename-proof tokens. Every
 * name that is not a Java keyword becomes ID and every text literal STR,
 * so renaming changes nothing. Each run of KGRAM_LENGTH such tokens in a
 * row (a k-gram) is hashed, and in every WINDOW_SIZE hashes in a row the
 * smallest one is kept as a fingerprint.
 *
 * Where it fits: WinnowingSimilarityDetector compares the fingerprints of
 * two answers. LineComparison uses the same k-gram hashes to show the
 * instructor which lines of two files match.
 */
package gradezzy.plagiarism;

import java.util.ArrayList;
import java.util.List;

import gradezzy.util.TextUtils;

/**
 * Rename-proof k-grams and the fingerprints winnowing picks from them.
 *
 * <p>Two files that share a run of at least KGRAM_LENGTH + WINDOW_SIZE
 * - 1 tokens, apart from names and text, always share a fingerprint,
 * wherever the run is in each file. That is why moving a method does not
 * hide copied code.
 *
 * <p>Tier: COULD. Owner: Jing Yuan.
 */
public final class Winnowing {

    /**
     * How many rename-proof tokens one k-gram holds.
     */
    public static final int KGRAM_LENGTH = 5;

    /**
     * How many k-gram hashes in a row one window covers.
     */
    public static final int WINDOW_SIZE = 4;

    // The text placed between the tokens of a k-gram before hashing
    private static final String TOKEN_SEPARATOR = " ";

    // The position of the first token or hash in a list
    private static final int FIRST_POSITION = 0;

    // The position recorded before any fingerprint has been picked
    private static final int NONE_PICKED = -1;

    // Only static methods; never created
    private Winnowing() {
        // Nothing to set up
    }

    /**
     * Turns tokens into rename-proof tokens with
     * JavaTokenizer.shapeToken: a name that is not a keyword becomes ID,
     * a string or char literal STR, and everything else stays.
     *
     * <p>Tier: COULD.
     *
     * @param tokens the tokens of a file, in order
     * @return the rename-proof tokens, in the same order
     */
    public static List<String> normalise(List<JavaToken> tokens) {
        List<String> shapes = new ArrayList<>();
        for (JavaToken token : tokens) {
            shapes.add(JavaTokenizer.shapeToken(token.getText()));
        }
        return shapes;
    }

    /**
     * Hashes every run of a given number of tokens in a row, with
     * String.hashCode of the tokens joined by a space. The hash at
     * position p is the hash of the run that starts at token p.
     *
     * <p>Tier: COULD.
     *
     * @param shapes the rename-proof tokens of a file
     * @param length how many tokens one run holds, such as KGRAM_LENGTH
     * @return one hash per run, in order; empty when the file has fewer
     *         tokens than one run
     */
    public static List<Integer> hashKGrams(List<String> shapes,
            int length) {
        List<Integer> hashes = new ArrayList<>();
        int lastStart = shapes.size() - length;

        for (int start = FIRST_POSITION; start <= lastStart; start++) {
            int end = start + length;
            String kGram = TextUtils.join(TOKEN_SEPARATOR,
                    shapes.subList(start, end));
            hashes.add(kGram.hashCode());
        }

        return hashes;
    }

    /**
     * Picks the fingerprints of a file by winnowing: in every window of
     * a given number of hashes in a row, the smallest hash is picked,
     * the rightmost one when several are smallest. A position picked by
     * several windows in a row is recorded once. A file with fewer
     * hashes than one window is one window.
     *
     * <p>Tier: COULD.
     *
     * @param hashes the k-gram hashes of a file, from hashKGrams
     * @param windowSize how many hashes one window covers, such as
     *        WINDOW_SIZE
     * @return the fingerprints, from the top of the file
     */
    public static List<Fingerprint> selectFingerprints(List<Integer> hashes,
            int windowSize) {
        List<Fingerprint> fingerprints = new ArrayList<>();
        if (hashes.isEmpty()) {
            return fingerprints;
        }

        int lastFullWindowStart = hashes.size() - windowSize;
        int lastWindowStart = Math.max(FIRST_POSITION, lastFullWindowStart);
        int lastPicked = NONE_PICKED;

        for (int start = FIRST_POSITION; start <= lastWindowStart;
                start++) {
            int fullWindowEnd = start + windowSize;
            int windowEnd = Math.min(fullWindowEnd, hashes.size());
            int picked = findRightmostSmallest(hashes, start, windowEnd);

            if (picked != lastPicked) {
                fingerprints.add(new Fingerprint(hashes.get(picked)));
                lastPicked = picked;
            }
        }

        return fingerprints;
    }

    // Returns the position of the smallest hash from start up to, not
    // including, end; the rightmost one when several are smallest.
    private static int findRightmostSmallest(List<Integer> hashes,
            int start, int end) {
        int smallest = start;

        for (int position = start; position < end; position++) {
            if (hashes.get(position) <= hashes.get(smallest)) {
                smallest = position;
            }
        }

        return smallest;
    }
}
