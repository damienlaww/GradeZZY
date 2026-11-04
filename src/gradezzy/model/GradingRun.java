/*
 * File: GradingRun.java
 *
 * Purpose: One batch of submissions graded together.
 *
 * Where it fits: Created by GradingService, saved through ResultRepository and
 * handed to exporters and statistics.
 */
package gradezzy.model;

import java.nio.file.Path;
import java.time.LocalDateTime;
import java.util.List;
import java.util.concurrent.CopyOnWriteArrayList;

/**
 * One batch of submissions graded together.
 *
 * <p>Tier: MUST. Owner: Jing Yuan.
 */
public class GradingRun {

    // The unique id of this run
    private final String id;

    // The assessment being graded
    private final Assessment assessment;

    // The section the batch belongs to
    private final Section section;

    // The time the run started
    private final LocalDateTime startedAt;

    // The submissions in this run. Copy-on-write, because a late batch
    // adds and replaces students while the window may be looping over
    // the list.
    private final List<Submission> submissions =
            new CopyOnWriteArrayList<>();

    // The score sheet the batch was graded against, or null if unknown
    private Path scoreSheetFile = null;

    // The starter folder chosen for the batch, or null for the settings
    private Path starterDirectory = null;

    // The tester folder chosen for the batch, or null if unknown
    private Path testersDirectory = null;

    /**
     * Creates a new grading run.
     *
     * @param id the unique id of this run
     * @param assessment the assessment being graded
     * @param section the section the batch belongs to
     * @param startedAt the time the run started
     */
    public GradingRun(String id, Assessment assessment, Section section,
            LocalDateTime startedAt) {
        this.id = id;
        this.assessment = assessment;
        this.section = section;
        this.startedAt = startedAt;
    }

    /**
     * Returns the unique id of this run.
     *
     * @return the unique id of this run
     */
    public String getId() {
        return id;
    }

    /**
     * Returns the assessment being graded.
     *
     * @return the assessment being graded
     */
    public Assessment getAssessment() {
        return assessment;
    }

    /**
     * Returns the section the batch belongs to.
     *
     * @return the section the batch belongs to
     */
    public Section getSection() {
        return section;
    }

    /**
     * Returns the time the run started.
     *
     * @return the time the run started
     */
    public LocalDateTime getStartedAt() {
        return startedAt;
    }

    /**
     * Returns the submissions in this run.
     *
     * @return the submissions in this run
     */
    public List<Submission> getSubmissions() {
        return submissions;
    }

    /**
     * Adds a submission to this run.
     *
     * @param submission the submission
     */
    public void addSubmission(Submission submission) {
        submissions.add(submission);
    }

    /**
     * Removes a submission from this run, for example the older result
     * of a student whose late upload replaces it.
     *
     * <p>Tier: SHOULD.
     *
     * @param submission the submission
     */
    public void removeSubmission(Submission submission) {
        submissions.remove(submission);
    }

    /**
     * Returns the score sheet the batch was graded against. The CSV
     * export fills in this sheet.
     *
     * @return the score sheet, or null for a run saved before this was
     *         recorded
     */
    public Path getScoreSheetFile() {
        return scoreSheetFile;
    }

    /**
     * Sets the score sheet the batch was graded against.
     *
     * @param scoreSheetFile the score sheet
     */
    public void setScoreSheetFile(Path scoreSheetFile) {
        this.scoreSheetFile = scoreSheetFile;
    }

    /**
     * Returns the starter folder chosen for the batch. The untouched and
     * empty answer checks compare each answer with its files.
     *
     * @return the starter folder, or null to use the one in the settings
     */
    public Path getStarterDirectory() {
        return starterDirectory;
    }

    /**
     * Sets the starter folder chosen for the batch.
     *
     * @param starterDirectory the starter folder, or null for the settings
     */
    public void setStarterDirectory(Path starterDirectory) {
        this.starterDirectory = starterDirectory;
    }

    /**
     * Returns the tester folder the batch was graded with. Resume uses it,
     * so a run is never finished with other testers than it started with.
     *
     * @return the tester folder, or null if the testers were typed in the
     *         app or the run was saved before this was recorded
     */
    public Path getTestersDirectory() {
        return testersDirectory;
    }

    /**
     * Sets the tester folder the batch was graded with.
     *
     * @param testersDirectory the tester folder, or null if unknown
     */
    public void setTestersDirectory(Path testersDirectory) {
        this.testersDirectory = testersDirectory;
    }
}
