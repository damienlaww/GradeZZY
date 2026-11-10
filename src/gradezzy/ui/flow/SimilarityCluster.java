/*
 * File: SimilarityCluster.java
 *
 * Purpose: One cluster of similar code: the students of a question whose
 * answers the similarity check linked, directly or through another
 * student, with the pairs that link them. Also builds the clusters of a
 * list of pairs.
 *
 * Where it fits: MistakesView shows each cluster above the questions on
 * the Errors view of Review, so the instructor can pick two of its
 * students and compare their code.
 */
package gradezzy.ui.flow;

import java.util.ArrayList;
import java.util.List;

import gradezzy.plagiarism.SimilarityPair;

/**
 * The students of one question linked by similar code.
 *
 * <p>Tier: COULD.
 */
public class SimilarityCluster {

    // The question, such as Q1a
    private final String questionId;

    // The students, in the order the pairs named them
    private final List<String> usernames = new ArrayList<>();

    // The pairs that link the students, most similar first
    private final List<SimilarityPair> pairs = new ArrayList<>();

    /**
     * Creates an empty cluster of a question.
     *
     * @param questionId the question, such as Q1a
     */
    public SimilarityCluster(String questionId) {
        this.questionId = questionId;
    }

    /**
     * Builds the clusters of a list of pairs: two students are in the
     * same cluster when a pair links them, or a chain of pairs does. The
     * clusters come in the order of their first pair, so the cluster with
     * the most similar pair comes first when the list is most similar
     * first.
     *
     * @param similarPairs the pairs, most similar first
     * @return the clusters
     */
    public static List<SimilarityCluster> build(
            List<SimilarityPair> similarPairs) {
        List<SimilarityCluster> clusters = new ArrayList<>();
        for (SimilarityPair pair : similarPairs) {
            SimilarityCluster first = find(clusters, pair.getQuestionId(),
                    pair.getFirstUsername());
            SimilarityCluster second = find(clusters, pair.getQuestionId(),
                    pair.getSecondUsername());
            SimilarityCluster joined = first;
            if (joined == null) {
                joined = second;
            }
            if (joined == null) {
                joined = new SimilarityCluster(pair.getQuestionId());
                clusters.add(joined);
            }
            boolean twoClusters = first != null && second != null
                    && first != second;
            if (twoClusters) {
                first.takeAll(second);
                clusters.remove(second);
            }
            joined.add(pair);
        }
        return clusters;
    }

    /**
     * Returns the question.
     *
     * @return the question id
     */
    public String getQuestionId() {
        return questionId;
    }

    /**
     * Returns the students, in the order the pairs named them.
     *
     * @return the usernames
     */
    public List<String> getUsernames() {
        return usernames;
    }

    /**
     * Returns the pairs that link the students.
     *
     * @return the pairs, most similar first
     */
    public List<SimilarityPair> getPairs() {
        return pairs;
    }

    /**
     * Returns the highest similarity of the cluster's pairs.
     *
     * @return a share from 0 to 1, or 0 for an empty cluster
     */
    public double getHighestSimilarity() {
        double highest = 0;
        for (SimilarityPair pair : pairs) {
            highest = Math.max(highest, pair.getSimilarity());
        }
        return highest;
    }

    /**
     * Returns the pair that links two students directly, or null when
     * the check did not flag them as a pair.
     *
     * @param firstUsername one student
     * @param secondUsername the other student
     * @return the pair, or null
     */
    public SimilarityPair findPair(String firstUsername,
            String secondUsername) {
        for (SimilarityPair pair : pairs) {
            boolean same = pair.getFirstUsername().equals(firstUsername)
                    && pair.getSecondUsername().equals(secondUsername);
            boolean swapped = pair.getFirstUsername().equals(secondUsername)
                    && pair.getSecondUsername().equals(firstUsername);
            if (same || swapped) {
                return pair;
            }
        }
        return null;
    }

    // Adds a pair and its two students.
    private void add(SimilarityPair pair) {
        pairs.add(pair);
        addUsername(pair.getFirstUsername());
        addUsername(pair.getSecondUsername());
    }

    // Adds a student not listed yet.
    private void addUsername(String username) {
        if (!usernames.contains(username)) {
            usernames.add(username);
        }
    }

    // Moves every pair of another cluster into this one.
    private void takeAll(SimilarityCluster other) {
        for (SimilarityPair pair : other.pairs) {
            add(pair);
        }
    }

    // Returns the cluster of a question that holds a student, or null.
    private static SimilarityCluster find(List<SimilarityCluster> clusters,
            String questionId, String username) {
        for (SimilarityCluster cluster : clusters) {
            boolean holds = cluster.questionId.equals(questionId)
                    && cluster.usernames.contains(username);
            if (holds) {
                return cluster;
            }
        }
        return null;
    }
}
