/*
 * File: PlagiarismService.java
 *
 * Purpose: Runs the similarity check on a finished run.
 *
 * Where it fits: Used by the plagiarism screen.
 */
package gradezzy.service;

import java.util.ArrayList;
import java.util.List;

import gradezzy.model.GradingRun;
import gradezzy.plagiarism.SimilarityDetector;
import gradezzy.plagiarism.SimilarityPair;

/**
 * Runs the similarity check on a finished run.
 *
 * <p>Tier: COULD. Owner: Jing Yuan.
 */
public class PlagiarismService {

    // The algorithm used to compare submissions
    private final SimilarityDetector similarityDetector;

    /**
     * Creates a new plagiarism service.
     *
     * @param similarityDetector the algorithm used to compare submissions
     */
    public PlagiarismService(SimilarityDetector similarityDetector) {
        this.similarityDetector = similarityDetector;
    }

    /**
     * Compares the submissions of a run with each other.
     *
     * @param gradingRun the grading run
     * @return the similar pairs found
     */
    public List<SimilarityPair> check(GradingRun gradingRun) {
        // TODO(Jing Yuan): implement: calling similarityDetector.findSimilar
        //     and adding a SIMILAR_CODE anomaly to both submissions of each
        //     pair
        return new ArrayList<>();
    }
}
