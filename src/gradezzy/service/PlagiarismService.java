/*
 * File: PlagiarismService.java
 *
 * Purpose: Runs the similarity check on a finished run.
 *
 * Where it fits: Used by LiveBackend for the Similar code card on Export.
 */
package gradezzy.service;

import java.util.ArrayList;
import java.util.List;
import java.util.UUID;

import gradezzy.config.AppConfig;
import gradezzy.config.ConfigKeys;
import gradezzy.model.Anomaly;
import gradezzy.model.AnomalyType;
import gradezzy.model.GradingRun;
import gradezzy.model.Severity;
import gradezzy.model.Submission;
import gradezzy.model.SubmissionStatus;
import gradezzy.plagiarism.SimilarityDetector;
import gradezzy.plagiarism.SimilarityPair;

/**
 * Runs the similarity check on a finished run.
 *
 * <p>Tier: COULD. Owner: Jing Yuan.
 */
public class PlagiarismService {

    // Start of the id of every Similar code anomaly
    private static final String ID_PREFIX = "SIMILAR-";

    // Turns a similarity from 0 to 1 into a percentage
    private static final int PERCENT = 100;

    // What the instructor can do about a Similar code anomaly
    private static final String SUGGESTED_ACTION =
            "Use Compare in the Similar code card on Export.";

    // The application settings
    private final AppConfig config;

    // The algorithm used to compare submissions
    private final SimilarityDetector similarityDetector;

    /**
     * Creates a new plagiarism service.
     *
     * @param config the application settings
     * @param similarityDetector the algorithm used to compare submissions
     */
    public PlagiarismService(AppConfig config,
            SimilarityDetector similarityDetector) {
        this.config = config;
        this.similarityDetector = similarityDetector;
    }

    /**
     * Compares the finished submissions of a run with each other and adds
     * a Similar code anomaly to both students of each pair found. The
     * anomalies of an earlier check are replaced, so checking a run again
     * does not add them twice. Submissions still waiting or being graded
     * are left out and left unchanged.
     *
     * @param gradingRun the grading run
     * @return the similar pairs found, most similar first, or an empty
     *         list when there is no run or plagiarism.enabled is false
     */
    public List<SimilarityPair> check(GradingRun gradingRun) {
        if (gradingRun == null
                || !config.getBoolean(ConfigKeys.PLAGIARISM_ENABLED)) {
            return new ArrayList<>();
        }

        List<Submission> finished = new ArrayList<>();
        for (Submission submission : gradingRun.getSubmissions()) {
            if (isFinished(submission)) {
                removeSimilarFlags(submission);
                finished.add(submission);
            }
        }

        List<SimilarityPair> pairs = similarityDetector.findSimilar(
                finished, gradingRun.getAssessment());

        for (SimilarityPair pair : pairs) {
            flag(finished, pair.getFirstUsername(),
                    pair.getSecondUsername(), pair);
            flag(finished, pair.getSecondUsername(),
                    pair.getFirstUsername(), pair);
        }

        return pairs;
    }

    // Says whether grading of a submission has ended, so its working copy
    // is complete and no grading thread changes its anomalies.
    private boolean isFinished(Submission submission) {
        SubmissionStatus status = submission.getStatus();

        return status == SubmissionStatus.GRADED
                || status == SubmissionStatus.FAILED;
    }

    // Removes the Similar code anomalies of an earlier check. An index
    // loop going backwards keeps the positions still to visit unchanged.
    private void removeSimilarFlags(Submission submission) {
        List<Anomaly> anomalies = submission.getAnomalies();

        for (int i = anomalies.size() - 1; i >= 0; i--) {
            if (anomalies.get(i).getType() == AnomalyType.SIMILAR_CODE) {
                anomalies.remove(i);
            }
        }
    }

    // Adds a Similar code anomaly to one student of a pair, naming the
    // other student.
    private void flag(List<Submission> submissions, String username,
            String otherUsername, SimilarityPair pair) {
        Submission submission = findByUsername(submissions, username);
        if (submission == null) {
            return;
        }

        long percent = Math.round(pair.getSimilarity() * PERCENT);
        String message = pair.getQuestionId() + " is " + percent
                + "% similar to " + otherUsername + ". This is a review "
                + "aid, not a finding.";
        submission.addAnomaly(new Anomaly(ID_PREFIX + UUID.randomUUID(),
                AnomalyType.SIMILAR_CODE, Severity.INFO, username, message,
                SUGGESTED_ACTION));
    }

    // Returns the submission of a username, or null if there is none.
    private Submission findByUsername(List<Submission> submissions,
            String username) {
        for (Submission submission : submissions) {
            if (submission.getUsername().equals(username)) {
                return submission;
            }
        }

        return null;
    }
}
