/*
 * File: FlowState.java
 *
 * Purpose: What the guided window remembers while the instructor moves
 * between steps: the class, the chosen files, the run and how far the
 * instructor has got.
 *
 * Where it fits: FlowWindow owns one; every screen reads and changes it.
 * It holds no JavaFX objects, so it is plain data.
 */
package gradezzy.ui.flow;

import java.util.ArrayList;
import java.util.List;

import gradezzy.model.GradingRun;
import gradezzy.model.Section;
import gradezzy.model.Submission;
import gradezzy.ui.backend.ImportBatch;

/**
 * The shared state of the guided window.
 */
public class FlowState {

    // The chosen module, such as CS102
    private String moduleCode = "";

    // The chosen section, such as G2
    private String sectionCode = "";

    // The files chosen on Set up
    private final ImportBatch batch = new ImportBatch();

    // The run shown on Review and Export, or null
    private GradingRun run = null;

    // The furthest step the instructor may open
    private Step furthest = Step.SET_UP;

    // True while a batch is being graded
    private boolean grading = false;

    // How many times grading has started, so a screen can tell that the
    // results may have changed since it last looked
    private int gradingCount = 0;

    // The students ticked on Review, used as the export scope
    private final List<Submission> selected = new ArrayList<>();

    /**
     * Creates an empty state on the first step.
     */
    public FlowState() {
        // the fields start empty
    }

    /**
     * Returns the chosen module.
     *
     * @return the module code, or an empty string
     */
    public String getModuleCode() {
        return moduleCode;
    }

    /**
     * Chooses the module.
     *
     * @param moduleCode the module code
     */
    public void setModuleCode(String moduleCode) {
        this.moduleCode = moduleCode;
    }

    /**
     * Returns the chosen section.
     *
     * @return the section code, or an empty string
     */
    public String getSectionCode() {
        return sectionCode;
    }

    /**
     * Chooses the section.
     *
     * @param sectionCode the section code
     */
    public void setSectionCode(String sectionCode) {
        this.sectionCode = sectionCode;
    }

    /**
     * Returns the chosen module and section together.
     *
     * @return the section
     */
    public Section getSection() {
        return new Section(moduleCode, sectionCode);
    }

    /**
     * Returns the files chosen on Set up.
     *
     * @return the batch
     */
    public ImportBatch getBatch() {
        return batch;
    }

    /**
     * Returns the run shown on Review and Export.
     *
     * @return the run, or null before grading
     */
    public GradingRun getRun() {
        return run;
    }

    /**
     * Sets the run shown on Review and Export and clears the ticked
     * students.
     *
     * @param run the run
     */
    public void setRun(GradingRun run) {
        this.run = run;
        selected.clear();
    }

    /**
     * Returns the furthest step the instructor may open.
     *
     * @return the step
     */
    public Step getFurthest() {
        return furthest;
    }

    /**
     * Lets the instructor open a step and every step before it.
     *
     * @param step the step reached
     */
    public void reach(Step step) {
        if (step.ordinal() > furthest.ordinal()) {
            furthest = step;
        }
    }

    /**
     * Goes back to the first step, for grading another section.
     */
    public void restart() {
        furthest = Step.SET_UP;
        run = null;
        selected.clear();
    }

    /**
     * Says whether a batch is being graded.
     *
     * @return true while grading
     */
    public boolean isGrading() {
        return grading;
    }

    /**
     * Records whether a batch is being graded.
     *
     * @param grading true while grading
     */
    public void setGrading(boolean grading) {
        this.grading = grading;
        if (grading) {
            gradingCount++;
        }
    }

    /**
     * Returns how many times grading has started, counting resumes and
     * retries too.
     *
     * @return the count
     */
    public int getGradingCount() {
        return gradingCount;
    }

    /**
     * Returns the students ticked on Review.
     *
     * @return the list, which the caller may change
     */
    public List<Submission> getSelected() {
        return selected;
    }
}
