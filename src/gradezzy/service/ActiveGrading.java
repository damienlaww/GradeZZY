/*
 * File: ActiveGrading.java
 *
 * Purpose: One grading that is running now: the section it grades, its
 * own stop flag, the run once it has one, and the students it grades.
 * Several can run at once, one per section, so each needs its own flag
 * and its own students.
 *
 * Where it fits: GradingService makes one when a batch, retry, resume,
 * late batch or regrade starts and drops it when it ends. Cancel stops
 * the gradings of one section, and a batch that would grade a student
 * another running grading holds is refused, as both would write the
 * same working copy.
 */
package gradezzy.service;

import java.util.ArrayList;
import java.util.List;

import gradezzy.grading.CancellationToken;
import gradezzy.model.Section;
import gradezzy.model.Submission;

/**
 * A grading that is running now.
 *
 * <p>Tier: COULD. Owner: Damien.
 */
public class ActiveGrading {

    // The section graded, or null when the caller gave none
    private final Section section;

    // The flag that stops this grading only
    private final CancellationToken cancellationToken =
            new CancellationToken();

    // The id of the run, or null while its files are still being loaded
    private String runId = null;

    // The usernames this grading holds working copies for
    private final List<String> usernames = new ArrayList<>();

    /**
     * Creates the grading of a section.
     *
     * <p>Tier: COULD.
     *
     * @param section the section, or null when none was given
     */
    public ActiveGrading(Section section) {
        this.section = section;
    }

    /**
     * Says whether this grading is of a section, by its label, so CS102
     * G1 matches however it was written.
     *
     * <p>Tier: COULD.
     *
     * @param other the section, or null
     * @return true when both name the same section
     */
    public boolean isOf(Section other) {
        if (section == null || other == null) {
            return false;
        }
        return section.getLabel().equalsIgnoreCase(other.getLabel());
    }

    /**
     * Returns the section graded.
     *
     * <p>Tier: COULD.
     *
     * @return the section, or null when none was given
     */
    public Section getSection() {
        return section;
    }

    /**
     * Returns the flag that stops this grading.
     *
     * <p>Tier: COULD.
     *
     * @return the flag
     */
    public CancellationToken getCancellationToken() {
        return cancellationToken;
    }

    /**
     * Returns the id of the run graded.
     *
     * <p>Tier: COULD.
     *
     * @return the id, or null while the files are being loaded
     */
    public String getRunId() {
        return runId;
    }

    /**
     * Sets the id of the run graded.
     *
     * <p>Tier: COULD.
     *
     * @param runId the id
     */
    public void setRunId(String runId) {
        this.runId = runId;
    }

    /**
     * Notes the students this grading writes working copies for.
     *
     * <p>Tier: COULD.
     *
     * @param submissions the students
     */
    public void addStudents(List<Submission> submissions) {
        for (Submission submission : submissions) {
            String username = submission.getUsername();
            if (!usernames.contains(username)) {
                usernames.add(username);
            }
        }
    }

    /**
     * Returns the first of some students this grading also holds, or
     * null when it holds none of them.
     *
     * <p>Tier: COULD.
     *
     * @param submissions the students
     * @return the username both hold, or null
     */
    public String findShared(List<Submission> submissions) {
        for (Submission submission : submissions) {
            if (usernames.contains(submission.getUsername())) {
                return submission.getUsername();
            }
        }
        return null;
    }

    /**
     * Names the section graded, for a message.
     *
     * <p>Tier: COULD.
     *
     * @return such as CS102 G1, or "another section" when none was given
     */
    public String describeSection() {
        if (section == null) {
            return "another section";
        }
        return section.getLabel();
    }
}
