/*
 * File: CheatingRemarks.java
 *
 * Purpose: Turns the review decisions on a run's Similar code flags into
 * the Remarks column of the score sheet. A student whose identical copy
 * with another student was kept flagged gets "Cheated with <name>". A
 * student whose every similar question with another student was
 * reviewed and kept flagged gets "Possible cheating with <name>". Both
 * students' flags must be kept: a Looks fine on either side gives none.
 * Two students alike on two or more questions, some of them still not
 * reviewed, get "Not yet reviewed: flagged with <name> on N questions"
 * until every one of those questions is decided.
 *
 * Where it fits: LiveBackend gives it to CsvScoreSheetExporter, and the
 * demo's CSV preview uses it too. It reads the pairs, the flags and the
 * decisions through DashboardBackend, so both backends follow one rule.
 */
package gradezzy.ui.backend;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;

import gradezzy.model.Anomaly;
import gradezzy.model.AnomalyType;
import gradezzy.model.GradingRun;
import gradezzy.model.Submission;
import gradezzy.plagiarism.SimilarityPair;
import gradezzy.report.ScoreSheetRemarks;
import gradezzy.util.TextUtils;

/**
 * Gives each student's remark from the decisions on their Similar code
 * flags.
 *
 * <p>Tier: COULD.
 */
public class CheatingRemarks implements ScoreSheetRemarks {

    // Starts the remark for an identical copy kept flagged
    private static final String CHEATED = "Cheated with ";

    // Starts the remark for similar code kept flagged on every question
    private static final String POSSIBLE = "Possible cheating with ";

    // Starts the remark for a pair flagged on several questions, some
    // still waiting for a decision
    private static final String PENDING = "Not yet reviewed: flagged with ";

    // The fewest questions two students must be alike on for a remark
    // before they are reviewed
    private static final int MIN_PENDING_QUESTIONS = 2;

    // Ends the count of questions in a remark not yet reviewed
    private static final String QUESTIONS = " questions";

    // Comes between the other students of one remark
    private static final String NAME_SEPARATOR = ", ";

    // Comes between the two parts of a remark
    private static final String PART_SEPARATOR = "; ";

    // The backend that holds the pairs, the flags and the decisions
    private final DashboardBackend backend;

    /**
     * Creates the remarks of a backend.
     *
     * <p>Tier: COULD.
     *
     * @param backend the backend
     */
    public CheatingRemarks(DashboardBackend backend) {
        this.backend = backend;
    }

    /**
     * Returns the remark of each student with a pair kept flagged. For
     * one other student, each question they are alike on counts as kept
     * flagged when every one of its flags, both students' own, was kept
     * flagged; one that waits or was marked as fine counts as not kept.
     * An identical copy kept flagged gives "Cheated with" that student;
     * otherwise, when every question of the two was kept flagged,
     * "Possible cheating with" them. When the two are alike on two or
     * more questions and one of them still waits, "Not yet reviewed:
     * flagged with" them on that many questions. Otherwise a question
     * that waits, or a flag marked as fine, gives nothing. The run must
     * be the whole run, so each pair has both students' flags and names;
     * an export of only some students passes the whole run here.
     *
     * <p>Tier: COULD.
     *
     * @param gradingRun the whole run being exported
     * @return the remarks by username in lower case
     */
    @Override
    public Map<String, String> remarksFor(GradingRun gradingRun) {
        Map<String, String> remarks = new HashMap<>();
        if (gradingRun == null) {
            return remarks;
        }

        List<SimilarityPair> pairs = backend.listSimilarPairs(gradingRun);
        for (Submission submission : gradingRun.getSubmissions()) {
            String remark = remarkOf(gradingRun, submission, pairs);
            if (!remark.isEmpty()) {
                String key = submission.getUsername().toLowerCase(
                        Locale.ROOT);
                remarks.put(key, remark);
            }
        }
        return remarks;
    }

    // Builds one student's remark from their pairs with each other
    // student, or "" when none was kept flagged.
    private String remarkOf(GradingRun run, Submission submission,
            List<SimilarityPair> pairs) {
        String username = submission.getUsername();
        List<String> cheatedWith = new ArrayList<>();
        List<String> possibleWith = new ArrayList<>();
        List<String> pendingWith = new ArrayList<>();
        for (String other : findOthers(username, pairs)) {
            List<SimilarityPair> shared = findShared(username, other, pairs);
            String shown = describe(run, other);
            if (isIdenticalKept(run, shared)) {
                cheatedWith.add(shown);
            } else if (isAllKept(run, shared)) {
                possibleWith.add(shown);
            } else if (isPending(run, shared)) {
                pendingWith.add(shown + " on " + shared.size() + QUESTIONS);
            }
        }

        List<String> parts = new ArrayList<>();
        if (!cheatedWith.isEmpty()) {
            parts.add(CHEATED + TextUtils.join(NAME_SEPARATOR, cheatedWith));
        }
        if (!possibleWith.isEmpty()) {
            parts.add(POSSIBLE + TextUtils.join(NAME_SEPARATOR,
                    possibleWith));
        }
        if (!pendingWith.isEmpty()) {
            parts.add(PENDING + TextUtils.join(NAME_SEPARATOR, pendingWith));
        }
        return TextUtils.join(PART_SEPARATOR, parts);
    }

    // Lists the other students a student is paired with, in order.
    private List<String> findOthers(String username,
            List<SimilarityPair> pairs) {
        List<String> others = new ArrayList<>();
        for (SimilarityPair pair : pairs) {
            String other = otherOf(pair, username);
            if (other != null && !others.contains(other)) {
                others.add(other);
            }
        }
        return others;
    }

    // Lists the pairs of two students, one per question.
    private List<SimilarityPair> findShared(String username, String other,
            List<SimilarityPair> pairs) {
        List<SimilarityPair> shared = new ArrayList<>();
        for (SimilarityPair pair : pairs) {
            if (other.equals(otherOf(pair, username))) {
                shared.add(pair);
            }
        }
        return shared;
    }

    // Returns the other student of a pair, or null when the student is
    // not in it.
    private String otherOf(SimilarityPair pair, String username) {
        if (pair.getFirstUsername().equals(username)) {
            return pair.getSecondUsername();
        }
        if (pair.getSecondUsername().equals(username)) {
            return pair.getFirstUsername();
        }
        return null;
    }

    // Says whether an identical copy of the two was kept flagged.
    private boolean isIdenticalKept(GradingRun run,
            List<SimilarityPair> shared) {
        for (SimilarityPair pair : shared) {
            if (pair.isIdentical() && isKept(run, pair)) {
                return true;
            }
        }
        return false;
    }

    // Says whether every question of the two was kept flagged.
    private boolean isAllKept(GradingRun run, List<SimilarityPair> shared) {
        for (SimilarityPair pair : shared) {
            if (!isKept(run, pair)) {
                return false;
            }
        }
        return !shared.isEmpty();
    }

    // Says whether two students are alike on enough questions to be
    // named before review, and one of those questions still waits.
    private boolean isPending(GradingRun run, List<SimilarityPair> shared) {
        if (shared.size() < MIN_PENDING_QUESTIONS) {
            return false;
        }
        for (SimilarityPair pair : shared) {
            if (isWaiting(run, pair)) {
                return true;
            }
        }
        return false;
    }

    // Says whether a pair's question was reviewed and kept flagged: it
    // has flags, and every one of them was kept. A flag that waits, looks
    // fine or was resolved by a retry means the two do not agree.
    private boolean isKept(GradingRun run, SimilarityPair pair) {
        List<Anomaly> flags = findFlags(run, pair);
        for (Anomaly flag : flags) {
            if (backend.getDecision(flag) != ReviewDecision.REVIEWED) {
                return false;
            }
        }
        return !flags.isEmpty();
    }

    /**
     * Says whether a pair's question still waits for a decision: one of
     * the two students' Similar code flags about it is not decided, or
     * it has no flag to decide at all.
     *
     * <p>Tier: COULD.
     *
     * @param run the run the pair belongs to
     * @param pair the pair
     * @return true when the question still needs a look
     */
    public boolean isWaiting(GradingRun run, SimilarityPair pair) {
        List<Anomaly> flags = findFlags(run, pair);
        // A pair with no flag to decide was never reviewed
        if (flags.isEmpty()) {
            return true;
        }
        for (Anomaly flag : flags) {
            if (backend.needsReview(flag)) {
                return true;
            }
        }
        return false;
    }

    // Returns the Similar code flags of both students about each other
    // on the pair's question.
    private List<Anomaly> findFlags(GradingRun run, SimilarityPair pair) {
        List<Anomaly> flags = new ArrayList<>();
        for (Submission submission : run.getSubmissions()) {
            for (Anomaly anomaly : submission.getAnomalies()) {
                if (isFlagOf(run, anomaly, pair)) {
                    flags.add(anomaly);
                }
            }
        }
        return flags;
    }

    // Says whether a flag is about a pair: the same two students and the
    // same question.
    private boolean isFlagOf(GradingRun run, Anomaly anomaly,
            SimilarityPair pair) {
        if (anomaly.getType() != AnomalyType.SIMILAR_CODE) {
            return false;
        }
        SimilarityPair named = backend.findSimilarPair(run, anomaly);
        if (named == null) {
            return false;
        }
        boolean sameQuestion = named.getQuestionId().equals(
                pair.getQuestionId());
        boolean sameStudents = isSameStudents(named, pair);
        return sameQuestion && sameStudents;
    }

    // Says whether two pairs name the same two students, in either order.
    private boolean isSameStudents(SimilarityPair first,
            SimilarityPair second) {
        String firstStudent = first.getFirstUsername();
        String secondStudent = first.getSecondUsername();
        boolean same = firstStudent.equals(second.getFirstUsername())
                && secondStudent.equals(second.getSecondUsername());
        boolean swapped = firstStudent.equals(second.getSecondUsername())
                && secondStudent.equals(second.getFirstUsername());
        return same || swapped;
    }

    // Names a student as on the score sheet with their username, such as
    // KEVIN LIM (kevin.lim.2025), or the username alone.
    private String describe(GradingRun run, String username) {
        for (Submission submission : run.getSubmissions()) {
            boolean named = submission.getUsername().equals(username)
                    && submission.getStudent() != null
                    && !submission.getStudent().getFullName().isEmpty();
            if (named) {
                return submission.getStudent().getFullName() + " ("
                        + username + ")";
            }
        }
        return username;
    }
}
