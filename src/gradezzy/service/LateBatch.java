/*
 * File: LateBatch.java
 *
 * Purpose: Helpers for adding a late batch to a graded run: which late
 * uploads can be used, taking out the older results they replace and
 * putting them back when a late upload cannot be opened.
 *
 * Where it fits: GradingService.addToRun uses them. They read and
 * change only the run and the lists they are given.
 */
package gradezzy.service;

import java.util.ArrayList;
import java.util.List;
import java.util.Locale;

import gradezzy.model.Anomaly;
import gradezzy.model.AnomalyType;
import gradezzy.model.GradingRun;
import gradezzy.model.Severity;
import gradezzy.model.Submission;
import gradezzy.model.SubmissionStatus;
import gradezzy.report.ScoreFormatter;

/**
 * Helpers for adding a late batch to a graded run.
 *
 * <p>Tier: SHOULD. Owner: Jing Yuan.
 */
final class LateBatch {

    // Written in the audit log for a student who has no total
    private static final String NOT_GRADED = "not graded";

    // Not used: every method is static.
    private LateBatch() {
    }

    // Says whether a late upload could not be used at all: it was refused
    // when loaded, or its zip could not be unpacked.
    static boolean isUnusableUpload(Submission late) {
        if (late == null || late.getIngestFailure() != null) {
            return true;
        }
        return hasRejectedZip(late);
    }

    // Says whether ExtractStage refused a submission's zip (a zip bomb,
    // an empty or damaged zip, or a zip slip). Only an ERROR counts: a
    // WARNING of the same type, such as a file left out of the zip, is
    // given to students who were still graded.
    static boolean hasRejectedZip(Submission submission) {
        for (Anomaly anomaly : submission.getAnomalies()) {
            boolean refused = anomaly.getType() == AnomalyType.INVALID_ARCHIVE
                    && anomaly.getSeverity() == Severity.ERROR;
            if (refused) {
                return true;
            }
        }
        return false;
    }

    // Replaces the late copies of a student with the results they had
    // before the late batch.
    static void putBackOlder(GradingRun gradingRun, String username,
            List<Submission> removedOlder) {
        for (Submission submission : gradingRun.getSubmissions()) {
            if (submission.getUsername().equalsIgnoreCase(username)) {
                gradingRun.removeSubmission(submission);
            }
        }
        for (Submission older : removedOlder) {
            if (older.getUsername().equalsIgnoreCase(username)) {
                gradingRun.addSubmission(older);
            }
        }
    }

    // Lists, in lower case, the usernames of the late uploads that could
    // be opened.
    static List<String> listUsableUsernames(List<Submission> lateSubmissions) {
        List<String> lateUsernames = new ArrayList<>();
        for (Submission late : lateSubmissions) {
            if (late.getIngestFailure() == null) {
                lateUsernames.add(late.getUsername().toLowerCase(
                        Locale.ROOT));
            }
        }
        return lateUsernames;
    }

    // Takes the older results of the late students out of the run, so the
    // run never holds two results for one student. The old total is
    // taken before any copy is removed. See
    // GradingService.prepareLateBatch for the three lists that are
    // filled.
    static void removeOlderResults(GradingRun gradingRun,
            List<String> lateUsernames, List<String> replacedUsernames,
            List<String> oldTotals, List<Submission> removedOlder) {
        for (Submission older : gradingRun.getSubmissions()) {
            String key = older.getUsername().toLowerCase(Locale.ROOT);
            if (lateUsernames.contains(key)) {
                if (!containsIgnoringCase(replacedUsernames,
                        older.getUsername())) {
                    replacedUsernames.add(older.getUsername());
                    Submission loaded = gradingRun.findLoaded(
                            older.getUsername());
                    oldTotals.add(describeTotal(loaded));
                }
                gradingRun.removeSubmission(older);
                removedOlder.add(older);
            }
        }
    }

    // Says whether a list of usernames holds this one, ignoring case.
    private static boolean containsIgnoringCase(List<String> usernames,
            String username) {
        for (String listed : usernames) {
            if (listed.equalsIgnoreCase(username)) {
                return true;
            }
        }
        return false;
    }

    // The total of a student for the audit log, or "not graded".
    static String describeTotal(Submission submission) {
        boolean notGraded = submission == null
                || submission.getStatus() != SubmissionStatus.GRADED;
        if (notGraded) {
            return NOT_GRADED;
        }
        return ScoreFormatter.format(submission.getTotalScore());
    }
}
