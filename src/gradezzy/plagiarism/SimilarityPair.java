/*
 * File: SimilarityPair.java
 *
 * Purpose: Two submissions whose answer to a question is very similar.
 *
 * Where it fits: Returned by a SimilarityDetector and listed in the
 * Similar code card on Export.
 */
package gradezzy.plagiarism;

/**
 * Two submissions whose answer to a question is very similar.
 *
 * <p>Tier: COULD. Owner: Jun Sheng.
 */
public class SimilarityPair {

    // The first student's username
    private final String firstUsername;

    // The second student's username
    private final String secondUsername;

    // The question the similar code is in
    private final String questionId;

    // The similarity, from 0 to 1
    private final double similarity;

    /**
     * Creates a new similarity pair.
     *
     * @param firstUsername the first student's username
     * @param secondUsername the second student's username
     * @param questionId the question the similar code is in
     * @param similarity the similarity, from 0 to 1
     */
    public SimilarityPair(String firstUsername, String secondUsername,
            String questionId, double similarity) {
        this.firstUsername = firstUsername;
        this.secondUsername = secondUsername;
        this.questionId = questionId;
        this.similarity = similarity;
    }

    /**
     * Returns the first student's username.
     *
     * @return the first student's username
     */
    public String getFirstUsername() {
        return firstUsername;
    }

    /**
     * Returns the second student's username.
     *
     * @return the second student's username
     */
    public String getSecondUsername() {
        return secondUsername;
    }

    /**
     * Returns the question the similar code is in.
     *
     * @return the question the similar code is in
     */
    public String getQuestionId() {
        return questionId;
    }

    /**
     * Returns the similarity, from 0 to 1.
     *
     * @return the similarity, from 0 to 1
     */
    public double getSimilarity() {
        return similarity;
    }
}
