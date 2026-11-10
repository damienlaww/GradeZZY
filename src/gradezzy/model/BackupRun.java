/*
 * File: BackupRun.java
 *
 * Purpose: One saved grading run as a backup file holds it: its section,
 * assessment, when it was graded and how many students it has.
 *
 * Where it fits: BackupReader reads these from a backup file, and the
 * Backups view on History lists them under the backup.
 */
package gradezzy.model;

import java.time.LocalDateTime;

/**
 * A saved run inside a backup file, without its submissions.
 *
 * <p>Tier: COULD.
 */
public class BackupRun {

    // The run id, such as RUN-1794329453771
    private final String runId;

    // The module, such as CS102, or "" when not saved
    private final String moduleCode;

    // The section, such as G1, or "" when not saved
    private final String sectionCode;

    // The assessment, such as Lab Test 2, or "" when not saved
    private final String assessmentName;

    // When grading started, or null when not saved
    private final LocalDateTime startedAt;

    // How many students the run has
    private final int studentCount;

    // How many of them were graded
    private final int gradedCount;

    /**
     * Creates a saved run of a backup.
     *
     * <p>Tier: COULD.
     *
     * @param runId the run id
     * @param moduleCode the module; null for not saved
     * @param sectionCode the section; null for not saved
     * @param assessmentName the assessment; null for not saved
     * @param startedAt when grading started, or null
     * @param studentCount how many students the run has
     * @param gradedCount how many of them were graded
     */
    public BackupRun(String runId, String moduleCode, String sectionCode,
            String assessmentName, LocalDateTime startedAt,
            int studentCount, int gradedCount) {
        this.runId = runId;
        this.moduleCode = textOrEmpty(moduleCode);
        this.sectionCode = textOrEmpty(sectionCode);
        this.assessmentName = textOrEmpty(assessmentName);
        this.startedAt = startedAt;
        this.studentCount = studentCount;
        this.gradedCount = gradedCount;
    }

    /**
     * Returns the run id.
     *
     * <p>Tier: COULD.
     *
     * @return the run id
     */
    public String getRunId() {
        return runId;
    }

    /**
     * Returns the module.
     *
     * <p>Tier: COULD.
     *
     * @return the module, or "" when not saved
     */
    public String getModuleCode() {
        return moduleCode;
    }

    /**
     * Returns the section.
     *
     * <p>Tier: COULD.
     *
     * @return the section, or "" when not saved
     */
    public String getSectionCode() {
        return sectionCode;
    }

    /**
     * Returns the assessment.
     *
     * <p>Tier: COULD.
     *
     * @return the assessment name, or "" when not saved
     */
    public String getAssessmentName() {
        return assessmentName;
    }

    /**
     * Returns when grading started.
     *
     * <p>Tier: COULD.
     *
     * @return the time, or null when not saved
     */
    public LocalDateTime getStartedAt() {
        return startedAt;
    }

    /**
     * Returns how many students the run has.
     *
     * <p>Tier: COULD.
     *
     * @return the number of students
     */
    public int getStudentCount() {
        return studentCount;
    }

    /**
     * Returns how many students were graded.
     *
     * <p>Tier: COULD.
     *
     * @return the number graded
     */
    public int getGradedCount() {
        return gradedCount;
    }

    // Returns the text, or "" for null
    private static String textOrEmpty(String text) {
        if (text == null) {
            return "";
        }
        return text;
    }
}
