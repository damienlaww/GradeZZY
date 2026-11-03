/*
 * File: PlagiarismService.java
 *
 * Purpose: Runs the similarity check on a finished run.
 *
 * Where it fits: Used by LiveBackend for the Similar code card on Export.
 */
package gradezzy.service;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

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

    // Separates the run, the two students and the question in an id
    private static final String ID_SEPARATOR = "/";

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
     * does not add them twice. A pair found again gets the same anomaly
     * id and keeps its fix status, so a review decision about it is kept.
     * Submissions still waiting or being graded are left out and left
     * unchanged. One check runs at a time, so two checks of a run at once
     * cannot both add their anomalies.
     *
     * @param gradingRun the grading run
     * @return the similar pairs found, most similar first, or an empty
     *         list when there is no run or plagiarism.enabled is false
     */
    public synchronized List<SimilarityPair> check(GradingRun gradingRun) {
        if (gradingRun == null
                || !config.getBoolean(ConfigKeys.PLAGIARISM_ENABLED)) {
            return new ArrayList<>();
        }

        List<Submission> finished = new ArrayList<>();
        Map<String, Anomaly> earlierFlags = new HashMap<>();
        for (Submission submission : gradingRun.getSubmissions()) {
            if (isFinished(submission)) {
                removeSimilarFlags(submission, earlierFlags);
                finished.add(submission);
            }
        }

        List<SimilarityPair> pairs = similarityDetector.findSimilar(
                finished, gradingRun.getAssessment());

        for (SimilarityPair pair : pairs) {
            flag(gradingRun, finished, earlierFlags,
                    pair.getFirstUsername(), pair.getSecondUsername(), pair);
            flag(gradingRun, finished, earlierFlags,
                    pair.getSecondUsername(), pair.getFirstUsername(), pair);
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

    // Removes the Similar code anomalies of an earlier check, keeping
    // them by id. They are found first and then removed one by one, by
    // object, so a Retry clearing the same list at once cannot make a
    // position wrong.
    private void removeSimilarFlags(Submission submission,
            Map<String, Anomaly> earlierFlags) {
        List<Anomaly> anomalies = submission.getAnomalies();
        List<Anomaly> similarFlags = new ArrayList<>();
        for (Anomaly anomaly : anomalies) {
            if (anomaly.getType() == AnomalyType.SIMILAR_CODE) {
                similarFlags.add(anomaly);
            }
        }

        for (Anomaly anomaly : similarFlags) {
            earlierFlags.put(anomaly.getId(), anomaly);
            anomalies.remove(anomaly);
        }
    }

    // Adds a Similar code anomaly to one student of a pair, naming the
    // other student. Its id names the run, both students and the
    // question, so the same pair found again gets the same id.
    private void flag(GradingRun gradingRun, List<Submission> submissions,
            Map<String, Anomaly> earlierFlags, String username,
            String otherUsername, SimilarityPair pair) {
        Submission submission = findByUsername(submissions, username);
        if (submission == null) {
            return;
        }

        String id = ID_PREFIX + gradingRun.getId() + ID_SEPARATOR
                + username + ID_SEPARATOR + otherUsername + ID_SEPARATOR
                + pair.getQuestionId();
        long percent = Math.round(pair.getSimilarity() * PERCENT);
        String message = pair.getQuestionId() + " is " + percent
                + "% similar to " + otherUsername + ". This is a review "
                + "aid, not a finding.";
        Anomaly anomaly = new Anomaly(id, AnomalyType.SIMILAR_CODE,
                Severity.INFO, username, message, SUGGESTED_ACTION);

        Anomaly earlier = earlierFlags.get(id);
        if (earlier != null) {
            anomaly.setFixStatus(earlier.getFixStatus());
        }
        submission.addAnomaly(anomaly);
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
