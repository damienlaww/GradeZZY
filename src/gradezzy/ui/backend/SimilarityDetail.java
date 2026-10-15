/*
 * File: SimilarityDetail.java
 *
 * Purpose: What the instructor needs to compare one similar pair fairly:
 * the two files, matching excerpts and what was left out of the
 * comparison.
 *
 * Where it fits: Returned by DashboardBackend.getSimilarityDetail and
 * shown on the Similarity Review screen.
 *
 * AI use: drafted with Claude Code, reviewed by Jun Sheng.
 */
package gradezzy.ui.backend;

import java.util.ArrayList;
import java.util.List;

import gradezzy.plagiarism.SimilarityPair;

/**
 * The evidence for one similar pair.
 *
 * <p>Tier: COULD. Owner: Damien.
 */
public class SimilarityDetail {

    // The pair being compared
    private final SimilarityPair pair;

    // The first student's file, relative to the working copy
    private final String firstFile;

    // The second student's file, relative to the working copy
    private final String secondFile;

    // A matching excerpt from the first file
    private final String firstExcerpt;

    // A matching excerpt from the second file
    private final String secondExcerpt;

    // The starter and tester files left out of the comparison
    private final List<String> excludedFiles = new ArrayList<>();

    /**
     * Creates the evidence for one pair.
     *
     * @param pair the pair being compared
     * @param firstFile the first student's file
     * @param secondFile the second student's file
     * @param firstExcerpt a matching excerpt from the first file
     * @param secondExcerpt a matching excerpt from the second file
     * @param excludedFiles the files left out of the comparison
     */
    public SimilarityDetail(SimilarityPair pair, String firstFile,
            String secondFile, String firstExcerpt, String secondExcerpt,
            List<String> excludedFiles) {
        this.pair = pair;
        this.firstFile = firstFile;
        this.secondFile = secondFile;
        this.firstExcerpt = firstExcerpt;
        this.secondExcerpt = secondExcerpt;
        this.excludedFiles.addAll(excludedFiles);
    }

    /**
     * Returns the pair being compared.
     *
     * @return the pair
     */
    public SimilarityPair getPair() {
        return pair;
    }

    /**
     * Returns the first student's file.
     *
     * @return the path relative to the working copy
     */
    public String getFirstFile() {
        return firstFile;
    }

    /**
     * Returns the second student's file.
     *
     * @return the path relative to the working copy
     */
    public String getSecondFile() {
        return secondFile;
    }

    /**
     * Returns a matching excerpt from the first file.
     *
     * @return the excerpt
     */
    public String getFirstExcerpt() {
        return firstExcerpt;
    }

    /**
     * Returns a matching excerpt from the second file.
     *
     * @return the excerpt
     */
    public String getSecondExcerpt() {
        return secondExcerpt;
    }

    /**
     * Returns the files left out of the comparison.
     *
     * @return the file names
     */
    public List<String> getExcludedFiles() {
        return excludedFiles;
    }
}
