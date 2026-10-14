/*
 * File: TokenSimilarityDetector.java
 *
 * Purpose: Compares submissions by the tokens of their source code.
 *
 * Where it fits: A basic first algorithm; names and comments are ignored so
 * renaming variables does not hide copying.
 */
package gradezzy.plagiarism;

import java.util.ArrayList;
import java.util.List;

import gradezzy.config.AppConfig;
import gradezzy.model.Assessment;
import gradezzy.model.Submission;

/**
 * Compares submissions by the tokens of their source code.
 *
 * <p>Tier: COULD. Owner: Jun Sheng.
 */
public class TokenSimilarityDetector implements SimilarityDetector {

    /**
     * The name of the algorithm.
     */
    public static final String NAME = "Token similarity";

    // The application settings
    private final AppConfig config;

    /**
     * Creates a new token similarity detector.
     *
     * @param config the application settings
     */
    public TokenSimilarityDetector(AppConfig config) {
        this.config = config;
    }

    /**
     * Returns the name of the algorithm.
     *
     * @return the name of the algorithm
     */
    @Override
    public String getName() {
        return NAME;
    }

    /**
     * Compares every pair of submissions.
     *
     * @param submissions the submissions
     * @param assessment the assessment
     * @return the pairs at or above the configured threshold
     */
    @Override
    public List<SimilarityPair> findSimilar(List<Submission> submissions,
            Assessment assessment) {
        // TODO(Jun Sheng): implement: comparing each pair per question and
        //     keeping pairs at or above PLAGIARISM_THRESHOLD
        return new ArrayList<>();
    }

    /**
     * Measures how alike two source texts are.
     *
     * @param firstSource the first source
     * @param secondSource the second source
     * @return the similarity, from 0 to 1
     */
    public double similarity(String firstSource, String secondSource) {
        // TODO(Jun Sheng): implement: a token based similarity measure
        return 0.0;
    }
}
