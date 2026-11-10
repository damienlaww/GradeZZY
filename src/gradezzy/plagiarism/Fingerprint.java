/*
 * File: Fingerprint.java
 *
 * Purpose: One fingerprint of a source file: the hash of a run of
 * rename-proof tokens that winnowing picked, and where the run starts.
 *
 * Where it fits: Made by Winnowing.selectFingerprints and compared by
 * WinnowingSimilarityDetector.
 */
package gradezzy.plagiarism;

/**
 * The hash of one run of tokens that winnowing picked, and where it is.
 *
 * <p>Tier: COULD. Owner: Jing Yuan.
 */
public class Fingerprint {

    // The hash of the run of tokens
    private final int hash;

    // The position of the run's first token in the file, counted from 0
    private final int position;

    /**
     * Creates a new fingerprint.
     *
     * <p>Tier: COULD.
     *
     * @param hash the hash of the run of tokens
     * @param position the position of the run's first token, counted
     *        from 0
     */
    public Fingerprint(int hash, int position) {
        this.hash = hash;
        this.position = position;
    }

    /**
     * Returns the hash of the run of tokens.
     *
     * <p>Tier: COULD.
     *
     * @return the hash
     */
    public int getHash() {
        return hash;
    }
}
