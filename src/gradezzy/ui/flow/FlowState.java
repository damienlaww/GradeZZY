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

import java.nio.file.Path;
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
import gradezzy.ui.backend.InputKind;

/**
 * The shared state of the guided window.
 *
 * <p>Tier: SHOULD.
 */
public class FlowState {

    // Separates the module and section in a key such as CS102/G2
    private static final String KEY_SEPARATOR = "/";

    // The chosen module, such as CS102
    private String moduleCode = "";

    // The chosen section, such as G2
    private String sectionCode = "";

    // The files chosen on Set up
    private final ImportBatch batch = new ImportBatch();

    // The submissions chosen for the sections not open now, by module
    // and section such as CS102/G2, so they come back when the instructor
    // opens that section again
    private final Map<String, List<ImportItem>> submissionsBySection =
            new HashMap<>();

    // The paths saved for each section when GradeZZY was last closed,
    // until Set up checks them again and lists them
    private final Map<String, List<Path>> savedPaths = new HashMap<>();

    // The sections whose submissions list was cleared on purpose, so
    // nothing is brought back for them
    private final List<String> clearedSections = new ArrayList<>();

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
        String before = sectionKey();
        this.moduleCode = moduleCode;
        swapSubmissions(before);
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
        String before = sectionKey();
        this.sectionCode = sectionCode;
        swapSubmissions(before);
    }

    // Names the chosen module and section, such as CS102/G2.
    private String sectionKey() {
        return moduleCode + KEY_SEPARATOR + sectionCode;
    }

    // Keeps each section's chosen submissions apart: after the module or
    // section changes, the files chosen for the one before are put away
    // and the files chosen earlier for this one come back, so they are
    // never graded as the wrong section and are still listed on return.
    private void swapSubmissions(String before) {
        String now = sectionKey();
        if (now.equals(before)) {
            return;
        }

        // An empty list keeps the files put away earlier for that section
        List<ImportItem> chosen = batch.getItems(InputKind.SUBMISSION);
        if (!chosen.isEmpty()) {
            submissionsBySection.put(before, chosen);
        }

        batch.clear(InputKind.SUBMISSION);
        restoreSubmissions();
    }

    /**
     * Empties the submissions list, keeping its files for this section,
     * so they come back when Set up is opened on this section again.
     * Used when the instructor moves on to grade another section.
     */
    public void putAwaySubmissions() {
        List<ImportItem> chosen = batch.getItems(InputKind.SUBMISSION);
        if (!chosen.isEmpty()) {
            submissionsBySection.put(sectionKey(), chosen);
        }

        batch.clear(InputKind.SUBMISSION);
    }

    /**
     * Brings back the files put away for the chosen section, when the
     * submissions list is empty.
     *
     * @return true when files came back
     */
    public boolean restoreSubmissions() {
        if (!batch.getItems(InputKind.SUBMISSION).isEmpty()) {
            return false;
        }

        List<ImportItem> kept = submissionsBySection.remove(sectionKey());
        if (kept == null) {
            return false;
        }

        for (ImportItem item : kept) {
            batch.add(item);
        }
        return true;
    }

    /**
     * Empties the submissions list for good, so nothing put away for
     * this section comes back. Used by Clear all.
     */
    public void forgetSubmissions() {
        batch.clear(InputKind.SUBMISSION);
        submissionsBySection.remove(sectionKey());
        savedPaths.remove(sectionKey());
        if (!clearedSections.contains(sectionKey())) {
            clearedSections.add(sectionKey());
        }
    }

    /**
     * Says whether files are kept for the chosen section: saved when
     * GradeZZY was last closed, put away while another section was open,
     * or cleared on purpose. Only a section with none of these gets the
     * files of its last run back.
     *
     * @return true when files are kept for the section
     */
    public boolean hasKeptFiles() {
        String key = sectionKey();
        return savedPaths.containsKey(key)
                || submissionsBySection.containsKey(key)
                || clearedSections.contains(key);
    }

    /**
     * Returns the chosen module and section as one name, such as
     * CS102/G2, as the chosen files are kept by.
     *
     * @return the name
     */
    public String getSectionKey() {
        return sectionKey();
    }

    /**
     * Keeps the paths saved when GradeZZY was last closed, until each
     * section is opened on Set up.
     *
     * @param paths the paths by section, such as CS102/G2
     */
    public void setSavedPaths(Map<String, List<Path>> paths) {
        savedPaths.clear();
        savedPaths.putAll(paths);
    }

    /**
     * Takes the saved paths of the chosen section when its list is
     * empty, so Set up can check them again and list them.
     *
     * @return the paths, or an empty list when there are none to bring
     *         back
     */
    public List<Path> takeSavedPaths() {
        if (!batch.getItems(InputKind.SUBMISSION).isEmpty()) {
            return new ArrayList<>();
        }

        List<Path> paths = savedPaths.remove(sectionKey());
        if (paths == null) {
            return new ArrayList<>();
        }

        return paths;
    }

    /**
     * Adds files checked again for a section to that section: to the
     * list when it is open, else to the files kept for it.
     *
     * @param key the section, such as CS102/G2
     * @param items the checked files
     */
    public void addSubmissionsTo(String key, List<ImportItem> items) {
        if (key.equals(sectionKey())) {
            for (ImportItem item : items) {
                batch.add(item);
            }
            return;
        }

        List<ImportItem> kept = submissionsBySection.get(key);
        if (kept == null) {
            kept = new ArrayList<>();
            submissionsBySection.put(key, kept);
        }

        kept.addAll(items);
    }

    /**
     * Lists the paths chosen for every section, to save them: the open
     * section's list, the files kept for the others, paths saved earlier
     * that have not been listed yet, and an empty list for each section
     * cleared on purpose.
     *
     * @return the paths by section
     */
    public Map<String, List<Path>> listChosenPaths() {
        Map<String, List<Path>> chosen = new HashMap<>();
        for (String key : clearedSections) {
            chosen.put(key, new ArrayList<>());
        }

        for (String key : savedPaths.keySet()) {
            chosen.put(key, new ArrayList<>(savedPaths.get(key)));
        }

        for (String key : submissionsBySection.keySet()) {
            chosen.put(key, pathsOf(submissionsBySection.get(key)));
        }

        List<ImportItem> open = batch.getItems(InputKind.SUBMISSION);
        if (!open.isEmpty()) {
            chosen.put(sectionKey(), pathsOf(open));
        }

        return chosen;
    }

    // Returns the paths of chosen files.
    private List<Path> pathsOf(List<ImportItem> items) {
        List<Path> paths = new ArrayList<>();
        for (ImportItem item : items) {
            paths.add(item.getPath());
        }

        return paths;
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
