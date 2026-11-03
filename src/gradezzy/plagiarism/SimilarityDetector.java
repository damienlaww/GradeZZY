/*
 * File: SimilarityDetector.java
 *
 * Purpose: A way of comparing submissions for copied code.
 *
 * Where it fits: An interface so the team can try several algorithms and
 * compare them, which the presentation asks for.
 */
package gradezzy.plagiarism;

import java.nio.file.Path;
import java.util.List;

import gradezzy.model.Assessment;
import gradezzy.model.Submission;

/**
 * A way of comparing submissions for copied code.
 */
public interface SimilarityDetector {

    /**
     * Returns the name of the algorithm.
     *
     * @return the name of the algorithm
     */
    String getName();

    /**
     * Compares every pair of submissions.
     *
     * @param submissions the submissions
     * @param assessment the assessment
     * @return the pairs at or above the configured threshold
     */
    List<SimilarityPair> findSimilar(List<Submission> submissions,
            Assessment assessment);

    /**
     * Compares every pair of submissions, leaving out the code given in
     * the starter folder chosen for the run.
     *
     * @param submissions the submissions
     * @param assessment the assessment
     * @param starterDirectory the run's starter folder, or null to use
     *        paths.starter.dir
     * @return the pairs at or above the configured threshold
     */
    List<SimilarityPair> findSimilar(List<Submission> submissions,
            Assessment assessment, Path starterDirectory);
}
