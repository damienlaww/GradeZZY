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

    // True when both files have the same header and the same code
    private final boolean identical;

    // True when both files give the same Email ID in their header
    private final boolean sameEmail;

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
        this(firstUsername, secondUsername, questionId, similarity, false,
                false);
    }

    /**
     * Creates a new similarity pair that may be an identical copy.
     *
     * @param firstUsername the first student's username
     * @param secondUsername the second student's username
     * @param questionId the question the similar code is in
     * @param similarity the similarity, from 0 to 1
     * @param identical true when both files have the same Name and Email
     *        ID in their header and the same code
     */
    public SimilarityPair(String firstUsername, String secondUsername,
            String questionId, double similarity, boolean identical) {
        this(firstUsername, secondUsername, questionId, similarity,
                identical, identical);
    }

    /**
     * Creates a new similarity pair that may share an Email ID or be an
     * identical copy.
     *
     * @param firstUsername the first student's username
     * @param secondUsername the second student's username
     * @param questionId the question the similar code is in
     * @param similarity the similarity, from 0 to 1
     * @param identical true when both files have the same Name and Email
     *        ID in their header and the same code
     * @param sameEmail true when both files give the same Email ID in
     *        their header; always true for an identical copy
     */
    public SimilarityPair(String firstUsername, String secondUsername,
            String questionId, double similarity, boolean identical,
            boolean sameEmail) {
        this.firstUsername = firstUsername;
        this.secondUsername = secondUsername;
        this.questionId = questionId;
        this.similarity = similarity;
        this.identical = identical;
        this.sameEmail = sameEmail || identical;
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

    /**
     * Says whether both files have the same Name and Email ID in their
     * header and the same code, so one was handed in as the other.
     *
     * @return true for an identical copy
     */
    public boolean isIdentical() {
        return identical;
    }

    /**
     * Says whether both files give the same Email ID in their header, so
     * one may be handed in as the other's work. An identical copy always
     * does.
     *
     * @return true when the Email IDs are the same
     */
    public boolean isSameEmail() {
        return sameEmail;
    }
}
