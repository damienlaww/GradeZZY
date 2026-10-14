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
import java.util.ArrayList;
import java.util.List;

/**
 * One student's submission and everything learnt about it.
 *
 * <p>Tier: MUST. Owner: Jing Yuan.
 */
public class Submission {

    // The username taken from the zip file name
    private final String username;

    // The section chosen for this upload
    private final Section section;

    // The uploaded zip file or folder
    private final Path originalPath;

    // The working copy that may be modified
    private Path workingCopyPath = null;

    // The matching roster entry
    private Student student = null;

    // The grading status
    private SubmissionStatus status = SubmissionStatus.PENDING;

    // The problems found so far
    private final List<Anomaly> anomalies = new ArrayList<>();

    // The result of each question
    private final List<QuestionResult> questionResults = new ArrayList<>();

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
     * Adds up the score of every question.
     *
     * @return the student's total
     */
    public double getTotalScore() {
        // TODO(Jing Yuan): implement: the sum of the scores in questionResults
        return 0.0;
    }
}
