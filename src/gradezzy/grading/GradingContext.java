/*
 * File: GradingContext.java
 *
 * Purpose: The shared information every stage needs.
 *
 * Where it fits: Created once per run and passed to each stage, so stages do
 * not depend on each other directly.
 */
package gradezzy.grading;

import java.util.ArrayList;
import java.util.List;

import gradezzy.config.AppConfig;
import gradezzy.model.Assessment;
import gradezzy.model.Student;
import gradezzy.testcase.TesterFile;

/**
 * The shared information every stage needs.
 *
 * <p>Tier: MUST. Owner: Jing Yuan.
 */
public class GradingContext {

    // The application settings
    private final AppConfig config;

    // The assessment being graded
    private final Assessment assessment;

    // The students on the score sheet
    private final List<Student> roster = new ArrayList<>();

    // The tester files to use
    private final List<TesterFile> testers = new ArrayList<>();

    // The id of the run being graded, so PersistStage can save to it
    private String runId = null;

    /**
     * Creates a new grading context.
     *
     * <p>Tier: MUST.
     *
     * @param config the application settings
     * @param assessment the assessment being graded
     */
    public GradingContext(AppConfig config, Assessment assessment) {
        this.config = config;
        this.assessment = assessment;
    }

    /**
     * Returns the application settings.
     *
     * <p>Tier: MUST.
     *
     * @return the application settings
     */
    public AppConfig getConfig() {
        return config;
    }

    /**
     * Returns the assessment being graded.
     *
     * <p>Tier: MUST.
     *
     * @return the assessment being graded
     */
    public Assessment getAssessment() {
        return assessment;
    }

    /**
     * Returns the students on the score sheet.
     *
     * <p>Tier: MUST.
     *
     * @return the students on the score sheet
     */
    public List<Student> getRoster() {
        return roster;
    }

    /**
     * Returns the tester files to use.
     *
     * <p>Tier: MUST.
     *
     * @return the tester files to use
     */
    public List<TesterFile> getTesters() {
        return testers;
    }

    /**
     * Returns the id of the run being graded.
     *
     * <p>Tier: MUST.
     *
     * @return the run id, or null if none was set
     */
    public String getRunId() {
        return runId;
    }

    /**
     * Sets the id of the run being graded.
     *
     * <p>Tier: MUST.
     *
     * @param runId the run id
     */
    public void setRunId(String runId) {
        this.runId = runId;
    }
}
