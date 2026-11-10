/*
 * File: Fingerprint.java
 *
 * Purpose: One fingerprint of a source file: the hash of a run of
 * rename-proof tokens that winnowing picked.
 *
 * Where it fits: Made by Winnowing.selectFingerprints and compared by
 * WinnowingSimilarityDetector.
 */
package gradezzy.plagiarism;

/**
 * The hash of one run of tokens that winnowing picked.
 *
 * <p>Tier: COULD. Owner: Jing Yuan.
 */
public class Fingerprint {

    // The hash of the run of tokens
    private final int hash;

    /**
     * Creates a new fingerprint.
     *
     * <p>Tier: COULD.
     *
     * @param hash the hash of the run of tokens
     */
    public Fingerprint(int hash) {
        this.hash = hash;
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
