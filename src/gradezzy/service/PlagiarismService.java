/*
 * File: PlagiarismService.java
 *
 * Purpose: Runs the similarity check on a finished run, and reads the
 * similar pairs back from the flags it added.
 *
 * Where it fits: GradingService runs the check when a batch, retry,
 * resume, regrade or late batch has been graded, for the console and the
 * window alike. The Similar code card on Export reads the pairs with
 * listPairs. The run is saved after each check, so the flags are kept
 * when the app is closed.
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
import gradezzy.persistence.PersistenceException;
import gradezzy.persistence.ResultRepository;
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

    // How many parts follow the run in an id: the student, the other
    // student and the question
    private static final int ID_PARTS = 3;
    private static final int STUDENT_PART = 0;
    private static final int OTHER_STUDENT_PART = 1;
    private static final int QUESTION_PART = 2;

    // A flag's message starts "<question> is <percent>% similar to"
    private static final String MESSAGE_MIDDLE = " is ";
    private static final String PERCENT_SIGN = "%";

    // Turns a similarity from 0 to 1 into a percentage
    private static final int PERCENT = 100;

    // What the instructor can do about a Similar code anomaly
    private static final String SUGGESTED_ACTION =
            "Use Compare to read both answers side by side.";

    // The application settings
    private final AppConfig config;

    // The algorithm used to compare submissions
    private final SimilarityDetector similarityDetector;

    // The store the checked run is saved to, or null to save nothing
    private final ResultRepository repository;

    /**
     * Creates a new plagiarism service that saves the run after each
     * check.
     *
     * <p>Tier: COULD.
     *
     * @param config the application settings
     * @param similarityDetector the algorithm used to compare submissions
     * @param repository the store the run is saved to, or null to save
     *        nothing
     */
    public PlagiarismService(AppConfig config,
            SimilarityDetector similarityDetector,
            ResultRepository repository) {
        this.config = config;
        this.similarityDetector = similarityDetector;
        this.repository = repository;
    }

    /**
     * Compares the finished submissions of a run with each other and adds
     * a Similar code anomaly to both students of each pair found. The
     * anomalies of an earlier check are replaced, so checking a run again
     * does not add them twice. A pair found again gets the same anomaly
     * id and keeps its fix status, so a review decision about it is kept.
     * Submissions still waiting or being graded are left out and left
     * unchanged. One check runs at a time, so two checks of a run at once
     * cannot both add their anomalies. The run is then saved when the
     * database is switched on, so the flags are kept after a restart.
     *
     * <p>Tier: COULD.
     *
     * @param gradingRun the grading run
     * @return the similar pairs found, most similar first, or an empty
     *         list when there is no run or plagiarism.enabled is false
     */
    public synchronized List<SimilarityPair> check(GradingRun gradingRun) {
        boolean checkOff = gradingRun == null
                || !config.getBoolean(ConfigKeys.PLAGIARISM_ENABLED);
        if (checkOff) {
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

        // The starter folder chosen for the run, as grading uses it
        List<SimilarityPair> pairs = similarityDetector.findSimilar(
                finished, gradingRun.getAssessment(),
                gradingRun.getStarterDirectory());

        for (SimilarityPair pair : pairs) {
            flag(gradingRun, finished, earlierFlags,
                    pair.getFirstUsername(), pair.getSecondUsername(), pair);
            flag(gradingRun, finished, earlierFlags,
                    pair.getSecondUsername(), pair.getFirstUsername(), pair);
        }

        saveRun(gradingRun);
        return pairs;
    }

    /**
     * Reads the similar pairs back from the Similar code flags of a run,
     * without reading any file or checking again, so a saved run shows
     * the pairs it was saved with. Both students of a pair hold a flag;
     * each pair is listed once. The similarity is the percentage the
     * flag shows, so it is rounded to a whole percent.
     *
     * <p>Tier: COULD.
     *
     * @param gradingRun the grading run
     * @return the pairs, most similar first, or an empty list when there
     *         is no run or no flag
     */
    public List<SimilarityPair> listPairs(GradingRun gradingRun) {
        List<SimilarityPair> pairs = new ArrayList<>();
        if (gradingRun == null) {
            return pairs;
        }

        String idStart = ID_PREFIX + gradingRun.getId() + ID_SEPARATOR;
        List<String> pairKeys = new ArrayList<>();
        for (Submission submission : gradingRun.getSubmissions()) {
            for (Anomaly anomaly : submission.getAnomalies()) {
                SimilarityPair pair = readFlag(anomaly, idStart);
                if (pair == null) {
                    continue;
                }
                String pairKey = makePairKey(pair);
                if (!pairKeys.contains(pairKey)) {
                    pairKeys.add(pairKey);
                    insertBySimilarity(pairs, pair);
                }
            }
        }
        return pairs;
    }

    /**
     * Reads the pair a Similar code flag names: the student it is on,
     * the other student, the question and the similarity, as the check
     * wrote them into its id and message. The window uses it to open
     * Compare from the flag.
     *
     * <p>Tier: COULD.
     *
     * @param gradingRun the run the flag belongs to
     * @param anomaly the flag
     * @return the pair, or null for any other anomaly, a flag of another
     *         run or a flag that cannot be read
     */
    public SimilarityPair readPair(GradingRun gradingRun, Anomaly anomaly) {
        if (gradingRun == null || anomaly == null) {
            return null;
        }
        String idStart = ID_PREFIX + gradingRun.getId() + ID_SEPARATOR;
        return readFlag(anomaly, idStart);
    }

    // Reads the pair a Similar code flag of the run names, as flag wrote
    // it, or returns null for any other anomaly.
    private SimilarityPair readFlag(Anomaly anomaly, String idStart) {
        boolean otherFlag = anomaly.getType() != AnomalyType.SIMILAR_CODE
                || !anomaly.getId().startsWith(idStart);
        if (otherFlag) {
            return null;
        }

        String afterStart = anomaly.getId().substring(idStart.length());
        String[] parts = afterStart.split(ID_SEPARATOR);
        if (parts.length != ID_PARTS) {
            return null;
        }

        String questionId = parts[QUESTION_PART];
        String message = anomaly.getMessage();
        String start = questionId + MESSAGE_MIDDLE;
        int end = message.indexOf(PERCENT_SIGN);
        if (!message.startsWith(start) || end <= start.length()) {
            return null;
        }

        try {
            String percentText = message.substring(start.length(), end);
            int percent = Integer.parseInt(percentText);
            double similarity = (double) percent / PERCENT;
            return new SimilarityPair(parts[STUDENT_PART],
                    parts[OTHER_STUDENT_PART], questionId, similarity);
        } catch (NumberFormatException exception) {
            return null;
        }
    }

    // Names a pair the same way whichever student comes first.
    private String makePairKey(SimilarityPair pair) {
        String first = pair.getFirstUsername();
        String second = pair.getSecondUsername();
        if (first.compareTo(second) > 0) {
            String earlier = second;
            second = first;
            first = earlier;
        }
        return pair.getQuestionId() + ID_SEPARATOR + first + ID_SEPARATOR
                + second;
    }

    // Adds a pair after every pair at least as similar, so the list stays
    // most similar first.
    private void insertBySimilarity(List<SimilarityPair> pairs,
            SimilarityPair pair) {
        int place = 0;
        while (isAtLeastAsSimilar(pairs, place, pair)) {
            place++;
        }
        pairs.add(place, pair);
    }

    // Says whether there is a pair at a place and it is at least as
    // similar as the new pair, so the new pair goes after it.
    private boolean isAtLeastAsSimilar(List<SimilarityPair> pairs,
            int place, SimilarityPair pair) {
        if (place >= pairs.size()) {
            return false;
        }
        double similarity = pairs.get(place).getSimilarity();
        return similarity >= pair.getSimilarity();
    }

    // Saves the run with its new flags when the database is switched on.
    // A failure keeps the flags in memory, so they still show until the
    // app is closed; GradingService already warns when a run cannot be
    // saved.
    private void saveRun(GradingRun gradingRun) {
        boolean saveOff = repository == null
                || !config.getBoolean(ConfigKeys.DATABASE_ENABLED);
        if (saveOff) {
            return;
        }

        try {
            repository.saveRun(gradingRun);
        } catch (PersistenceException exception) {
            // See the comment above
        }
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
        double exactPercent = pair.getSimilarity() * PERCENT;
        long percent = Math.round(exactPercent);
        // listPairs reads the question and percentage back from the start
        String message = pair.getQuestionId() + MESSAGE_MIDDLE + percent
                + PERCENT_SIGN + " similar to " + otherUsername + ". This is "
                + "a review aid, not a finding.";
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
