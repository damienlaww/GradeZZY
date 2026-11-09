/*
 * File: SimilarityDetail.java
 *
 * Purpose: The evidence the instructor needs to compare one similar pair.
 *
 * Where it fits: Returned by DashboardBackend.getSimilarityDetail and
 * shown by SimilaritySheet, opened from Export.
 */
package gradezzy.ui.backend;

import java.util.ArrayList;
import java.util.List;

import gradezzy.plagiarism.SimilarityPair;

/**
 * The evidence for one similar pair.
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

    // The starter file's code, or "" when there is none
    private final String starterCode;

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
        this(pair, firstFile, secondFile, firstExcerpt, secondExcerpt, "",
                excludedFiles);
    }

    /**
     * Creates the evidence for one pair, with the starter file's code so
     * the starter lines can be told apart from the students' own.
     *
     * @param pair the pair being compared
     * @param firstFile the first student's file
     * @param secondFile the second student's file
     * @param firstExcerpt a matching excerpt from the first file
     * @param secondExcerpt a matching excerpt from the second file
     * @param starterCode the starter file's code, or "" when there is
     *        none
     * @param excludedFiles the files left out of the comparison
     */
    public SimilarityDetail(SimilarityPair pair, String firstFile,
            String secondFile, String firstExcerpt, String secondExcerpt,
            String starterCode, List<String> excludedFiles) {
        this.pair = pair;
        this.firstFile = firstFile;
        this.secondFile = secondFile;
        this.firstExcerpt = firstExcerpt;
        this.secondExcerpt = secondExcerpt;
        this.starterCode = starterCode;
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
     * Returns the starter file's code.
     *
     * @return the code, or "" when there is none
     */
    public String getStarterCode() {
        return starterCode;
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
