/*
 * File: Submission.java
 *
 * Purpose: One student's submission and everything learnt about it.
 *
 * Where it fits: Passed through every stage of the pipeline. The original path
 * is never written to; all changes happen in the working copy.
 */
package gradezzy.model;

import java.nio.file.Path;
import java.util.List;
import java.util.concurrent.CopyOnWriteArrayList;

/**
 * One student's submission and everything learnt about it.
 *
 * <p>Tier: MUST. Owner: Jing Yuan.
 */
public class Submission {

    // Totals are rounded to this many hundredths: 100 keeps two decimal
    // places, which is finer than any tester's marks
    private static final double ROUNDING_FACTOR = 100.0;

    // The username taken from the zip file name
    private final String username;

    // The section chosen for this upload
    private final Section section;

    // The uploaded zip file or folder
    private final Path originalPath;

    // The working copy that may be modified
    private Path workingCopyPath = null;

    // A rejected upload must never reach a working copy, including on retry.
    private String ingestFailure = null;

    // The matching roster entry
    private Student student = null;

    // The grading status
    private SubmissionStatus status = SubmissionStatus.PENDING;

    // The two lists below are copy-on-write: each change builds a new copy,
    // so the window can loop over them while a grading thread adds to or
    // clears them for Retry, without a ConcurrentModificationException.

    // The problems found so far
    private final List<Anomaly> anomalies = new CopyOnWriteArrayList<>();

    // The result of each question
    private final List<QuestionResult> questionResults =
            new CopyOnWriteArrayList<>();

    // The keys of the fixes the instructor undid, such as Fix letter
    // case:q3. Kept when the student is graded again, so FixManager
    // skips those fixes and the marks match the files as submitted.
    private final List<String> rejectedFixes = new CopyOnWriteArrayList<>();

    /**
     * Creates a new submission.
     *
     * @param username the username taken from the zip file name
     * @param section the section chosen for this upload
     * @param originalPath the uploaded zip file or folder
     */
    public Submission(String username, Section section, Path originalPath) {
        this.username = username;
        this.section = section;
        this.originalPath = originalPath;
    }

    /**
     * Returns why the upload was rejected before grading, for example a
     * file that is not a ZIP file.
     *
     * @return the rejection reason, or null for an accepted upload
     */
    public String getIngestFailure() {
        return ingestFailure;
    }

    /**
     * Records why this upload cannot be graded. The pipeline then marks
     * the submission FAILED without running any stage.
     *
     * @param ingestFailure the rejection reason
     */
    public void setIngestFailure(String ingestFailure) {
        this.ingestFailure = ingestFailure;
    }

    /**
     * Returns the username taken from the zip file name.
     *
     * @return the username taken from the zip file name
     */
    public String getUsername() {
        return username;
    }

    /**
     * Returns the section chosen for this upload.
     *
     * @return the section chosen for this upload
     */
    public Section getSection() {
        return section;
    }

    /**
     * Returns the uploaded zip file or folder.
     *
     * @return the uploaded zip file or folder
     */
    public Path getOriginalPath() {
        return originalPath;
    }

    /**
     * Returns the working copy that may be modified.
     *
     * @return the working copy that may be modified
     */
    public Path getWorkingCopyPath() {
        return workingCopyPath;
    }

    /**
     * Sets the working copy that may be modified.
     *
     * @param workingCopyPath the working copy that may be modified
     */
    public void setWorkingCopyPath(Path workingCopyPath) {
        this.workingCopyPath = workingCopyPath;
    }

    /**
     * Returns the matching roster entry.
     *
     * @return the matching roster entry
     */
    public Student getStudent() {
        return student;
    }

    /**
     * Sets the matching roster entry.
     *
     * @param student the matching roster entry
     */
    public void setStudent(Student student) {
        this.student = student;
    }

    /**
     * Returns the grading status.
     *
     * @return the grading status
     */
    public SubmissionStatus getStatus() {
        return status;
    }

    /**
     * Sets the grading status.
     *
     * @param status the grading status
     */
    public void setStatus(SubmissionStatus status) {
        this.status = status;
    }

    /**
     * Returns the problems found so far.
     *
     * @return the problems found so far
     */
    public List<Anomaly> getAnomalies() {
        return anomalies;
    }

    /**
     * Returns the result of each question.
     *
     * @return the result of each question
     */
    public List<QuestionResult> getQuestionResults() {
        return questionResults;
    }

    /**
     * Returns the keys of the fixes the instructor undid. Grading the
     * student again keeps this list, and FixManager does not apply a fix
     * whose key is in it. The database saves the list with the submission
     * (column rejected_fixes), so a run resumed after a restart skips
     * those fixes too.
     *
     * <p>Tier: SHOULD.
     *
     * @return the keys of the undone fixes, oldest first
     */
    public List<String> getRejectedFixes() {
        return rejectedFixes;
    }

    /**
     * Records that the instructor undid a fix, so it is skipped the next
     * time the student is graded. A key already in the list is not added
     * again.
     *
     * <p>Tier: SHOULD.
     *
     * @param fixKey the key of the fix, from FixRecord.getFixKey
     */
    public void rejectFix(String fixKey) {
        if (fixKey == null || rejectedFixes.contains(fixKey)) {
            return;
        }
        rejectedFixes.add(fixKey);
    }

    /**
     * Takes back the undo of a fix, so it is applied again the next time
     * the student is graded. A key not in the list is ignored.
     *
     * <p>Tier: SHOULD.
     *
     * @param fixKey the key of the fix, from FixManager.findRejectedKey
     */
    public void unrejectFix(String fixKey) {
        rejectedFixes.remove(fixKey);
    }

    /**
     * Records a problem found in this submission.
     *
     * @param anomaly the anomaly
     */
    public void addAnomaly(Anomaly anomaly) {
        anomalies.add(anomaly);
    }

    /**
     * Records the result of one question.
     *
     * @param questionResult the question result
     */
    public void addQuestionResult(QuestionResult questionResult) {
        questionResults.add(questionResult);
    }

    /**
     * Adds up the score of every question. The scores are used as they
     * are stored; ScoreStage has already kept each one within its
     * question's maximum. The total is rounded to two decimal places,
     * because adding doubles leaves tiny errors (0.1 + 0.2 is
     * 0.30000000000000004) that would otherwise reach the export.
     *
     * @return the student's total
     */
    public double getTotalScore() {
        double total = 0.0;

        for (QuestionResult questionResult : questionResults) {
            total += questionResult.getScore();
        }

        return Math.round(total * ROUNDING_FACTOR) / ROUNDING_FACTOR;
    }
}
