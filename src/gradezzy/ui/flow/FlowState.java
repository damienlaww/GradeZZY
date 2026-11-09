/*
 * File: FlowState.java
 *
 * Purpose: What the guided window remembers while the instructor moves
 * between steps: the class, the chosen files, the run and how far the
 * instructor has got, and the assessment and sections compared on
 * Statistics.
 *
 * Where it fits: FlowWindow owns one; every screen reads and changes it.
 * It holds no JavaFX objects, so it is plain data.
 */
package gradezzy.ui.flow;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

import gradezzy.model.Anomaly;
import gradezzy.model.GradingRun;
import gradezzy.model.Section;
import gradezzy.model.Student;
import gradezzy.model.Submission;
import gradezzy.ui.backend.ImportBatch;
import gradezzy.ui.backend.ImportItem;

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

    // The fixes being undone or applied again on a background thread,
    // the one running now first, or none. Only one job runs at a time,
    // and it grades one student again at a time.
    private final List<Anomaly> fixesInProgress = new ArrayList<>();

    // The students a background job still has to grade again (a whole
    // student or one question), the one being graded now first, or none.
    // Only one such job runs at a time.
    private final List<Submission> regradesInProgress = new ArrayList<>();

    // How many times grading has started, so a screen can tell that the
    // results may have changed since it last looked
    private int gradingCount = 0;

    // The students ticked on Review, used as the export scope
    private final List<Submission> selected = new ArrayList<>();

    // The assessment open in Settings, or null for the one in use
    private String settingsAssessment = null;

    // The assessment compared on Statistics, or null for the default
    private String statisticsAssessment = null;

    // The sections compared on Statistics, in the order they were
    // turned on
    private final List<String> statisticsSections = new ArrayList<>();

    // The students chosen on Export when only some are exported
    private final List<Submission> exportPicked = new ArrayList<>();

    // Whether Export fills in only the chosen students
    private boolean exportOnlyPicked = false;

    // The chosen files the grading now running skips, until it ends
    private final List<ImportItem> pendingSkipped = new ArrayList<>();

    // True from the start of grading until the skipped files have a run
    private boolean skippedWaiting = false;

    // The skipped files of each run graded since GradeZZY opened, by run
    // id, so a run opened again from Runs still has them
    private final Map<String, List<ImportItem>> skippedByRun =
            new HashMap<>();

    // The ids of the runs exported since GradeZZY opened. Grading a run
    // again takes its id out, as the export no longer matches its marks.
    private final List<String> exportedRuns = new ArrayList<>();

    // The students on the score sheet with no submission, and their run
    private List<Student> missingStudents = null;
    private GradingRun missingRun = null;

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
        exportPicked.clear();
        exportOnlyPicked = false;
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
        exportPicked.clear();
        exportOnlyPicked = false;
    }

    /**
     * Keeps the chosen files grading is about to skip, until the run they
     * belong to ends.
     *
     * @param files the files that cannot be used
     */
    public void setSkippedFiles(List<ImportItem> files) {
        pendingSkipped.clear();
        pendingSkipped.addAll(files);
        skippedWaiting = true;
    }

    /**
     * Ties the skipped files to the run that has just ended. Only the
     * first run to end after grading started gets them. A late batch
     * added to an earlier run adds its skipped files to that run's.
     * The run was graded again, so an earlier export of it is forgotten.
     *
     * @param gradingRun the run, or null when grading failed
     */
    public void attachSkippedFiles(GradingRun gradingRun) {
        forgetExport(gradingRun);
        if (!skippedWaiting) {
            return;
        }
        skippedWaiting = false;
        if (gradingRun == null || pendingSkipped.isEmpty()) {
            return;
        }
        List<ImportItem> files = skippedByRun.get(gradingRun.getId());
        if (files == null) {
            files = new ArrayList<>();
            skippedByRun.put(gradingRun.getId(), files);
        }
        files.addAll(pendingSkipped);
    }

    /**
     * Returns the files grading skipped for a run, found by its id.
     *
     * @param gradingRun the run
     * @return the files, empty when none were skipped
     */
    public List<ImportItem> getSkippedFiles(GradingRun gradingRun) {
        List<ImportItem> files = new ArrayList<>();
        if (gradingRun == null) {
            return files;
        }
        List<ImportItem> kept = skippedByRun.get(gradingRun.getId());
        if (kept != null) {
            files.addAll(kept);
        }
        return files;
    }

    /**
     * Returns the students on a run's score sheet with no submission, if
     * they were looked up already.
     *
     * @param gradingRun the run
     * @return the students, or null when not looked up yet
     */
    public List<Student> getMissingStudents(GradingRun gradingRun) {
        if (gradingRun == null || gradingRun != missingRun) {
            return null;
        }
        return missingStudents;
    }

    /**
     * Keeps the students on a run's score sheet with no submission.
     *
     * @param gradingRun the run
     * @param students the students
     */
    public void setMissingStudents(GradingRun gradingRun,
            List<Student> students) {
        missingRun = gradingRun;
        missingStudents = new ArrayList<>(students);
    }

    /**
     * Forgets the students with no submission, so they are looked up
     * again, as after the run's score sheet changes.
     */
    public void forgetMissingStudents() {
        missingRun = null;
        missingStudents = null;
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
            // A resume or retry grades the run shown again
            forgetExport(run);
        }
    }

    /**
     * Returns the fix being undone or applied again in the background.
     *
     * @return its anomaly, or null when none is
     */
    public Anomaly getFixInProgress() {
        if (fixesInProgress.isEmpty()) {
            return null;
        }
        return fixesInProgress.get(0);
    }

    /**
     * Records the fix being undone or applied again in the background.
     *
     * @param anomaly its anomaly, or null once the job has ended
     */
    public void setFixInProgress(Anomaly anomaly) {
        fixesInProgress.clear();
        if (anomaly != null) {
            fixesInProgress.add(anomaly);
        }
    }

    /**
     * Records the fixes a background job still has to undo, the one
     * running now first.
     *
     * @param anomalies their anomalies, or none once the job has ended
     */
    public void setFixesInProgress(List<Anomaly> anomalies) {
        fixesInProgress.clear();
        fixesInProgress.addAll(anomalies);
    }

    /**
     * Says whether a fix is being undone now or waits its turn in the
     * background job.
     *
     * @param anomaly the fix's anomaly
     * @return true while the job still has it to do
     */
    public boolean isFixInProgress(Anomaly anomaly) {
        return fixesInProgress.contains(anomaly);
    }

    /**
     * Counts the fixes the background job still has to do.
     *
     * @return the number, 0 when no job runs
     */
    public int countFixesInProgress() {
        return fixesInProgress.size();
    }

    /**
     * Records the students a background job still has to grade again,
     * the one being graded now first.
     *
     * @param students the students, or none once the job has ended
     */
    public void setRegradesInProgress(List<Submission> students) {
        regradesInProgress.clear();
        regradesInProgress.addAll(students);
    }

    /**
     * Says whether a background job is grading students again now.
     *
     * @return true while a job still has a student to grade
     */
    public boolean isRegrading() {
        return !regradesInProgress.isEmpty();
    }

    /**
     * Says whether a student is being graded again now, or waits for
     * their turn in the background job.
     *
     * @param submission the student
     * @return true while the job still has the student to grade
     */
    public boolean isRegrading(Submission submission) {
        return regradesInProgress.contains(submission);
    }

    /**
     * Records that a run's score sheet was exported.
     *
     * @param gradingRun the run
     */
    public void markExported(GradingRun gradingRun) {
        if (gradingRun == null || isExported(gradingRun)) {
            return;
        }
        exportedRuns.add(gradingRun.getId());
    }

    /**
     * Says whether a run was exported since it was last graded.
     *
     * @param gradingRun the run
     * @return true when it was exported
     */
    public boolean isExported(GradingRun gradingRun) {
        return gradingRun != null
                && exportedRuns.contains(gradingRun.getId());
    }

    /**
     * Forgets that a run was exported, as its marks are changing.
     *
     * @param gradingRun the run
     */
    public void forgetExport(GradingRun gradingRun) {
        if (gradingRun != null) {
            exportedRuns.remove(gradingRun.getId());
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

    /**
     * Returns the students chosen on Export, used when only some are
     * exported. Ticking on Review does not change it.
     *
     * @return the chosen students, which can be changed
     */
    public List<Submission> getExportPicked() {
        return exportPicked;
    }

    /**
     * Says whether Export fills in only the chosen students.
     *
     * @return true for only the chosen students
     */
    public boolean isExportOnlyPicked() {
        return exportOnlyPicked;
    }

    /**
     * Sets whether Export fills in only the chosen students.
     *
     * @param onlyPicked true for only the chosen students
     */
    public void setExportOnlyPicked(boolean onlyPicked) {
        exportOnlyPicked = onlyPicked;
    }

    /**
     * Returns the assessment open in Settings.
     *
     * @return its name, or null for the one in use
     */
    public String getSettingsAssessment() {
        return settingsAssessment;
    }

    /**
     * Remembers the assessment open in Settings, so it stays open when
     * the page is built again.
     *
     * @param name its name, or null for the one in use
     */
    public void setSettingsAssessment(String name) {
        settingsAssessment = name;
    }

    /**
     * Returns the assessment compared on the Statistics tab.
     *
     * <p>Tier: SHOULD. Owner: Jing Yuan.
     *
     * @return its name, or null when none has been chosen
     */
    public String getStatisticsAssessment() {
        return statisticsAssessment;
    }

    /**
     * Remembers the assessment compared on the Statistics tab, so it is
     * still chosen after One section or another tab.
     *
     * <p>Tier: SHOULD. Owner: Jing Yuan.
     *
     * @param name its name, or null for the default
     */
    public void setStatisticsAssessment(String name) {
        statisticsAssessment = name;
    }

    /**
     * Returns the sections compared on the Statistics tab. The list is
     * the one kept here, so changing it changes what is remembered.
     *
     * <p>Tier: SHOULD. Owner: Jing Yuan.
     *
     * @return the section codes, in the order they were turned on
     */
    public List<String> getStatisticsSections() {
        return statisticsSections;
    }
}
