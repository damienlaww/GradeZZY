/*
 * File: DashboardBackend.java
 *
 * Purpose: Everything the screens ask of the backend, in one interface.
 *
 * Where it fits: The screens call only this interface. LiveBackend passes
 * each call to a service; DemoBackend answers from data in memory.
 */
package gradezzy.ui.backend;

import java.nio.file.Path;
import java.util.List;
import java.util.Map;


import gradezzy.grading.GradingException;
import gradezzy.grading.ProgressListener;
import gradezzy.model.Anomaly;
import gradezzy.model.Assessment;
import gradezzy.model.AuditEntry;
import gradezzy.model.GradingRun;
import gradezzy.model.Question;
import gradezzy.model.QuestionResult;
import gradezzy.model.SavedAssessment;
import gradezzy.model.Section;
import gradezzy.model.Submission;
import gradezzy.plagiarism.SimilarityPair;
import gradezzy.stats.StatisticsSummary;

/**
 * The backend as the screens see it.
 */
public interface DashboardBackend {

    // ------------------------------------------------------------ mode

    /**
     * Says whether this backend uses synthetic demo data.
     *
     * @return true in demo mode
     */
    boolean isDemo();

    /**
     * Says whether a screen may offer a feature. If not, the screen shows
     * a Not connected yet panel.
     *
     * @param capability the feature
     * @return true when the feature can be called
     */
    boolean supports(Capability capability);

    // --------------------------------------------------------- context

    /**
     * Returns the module codes the instructor can choose from.
     *
     * @return the module codes, in order
     */
    List<String> getModuleCodes();

    /**
     * Returns the section codes of a module.
     *
     * @param moduleCode the module code
     * @return the section codes, in order
     */
    List<String> getSectionCodes(String moduleCode);

    /**
     * Returns the assessment with its questions and maximum marks.
     *
     * @return the assessment
     */
    Assessment getAssessment();

    /**
     * Returns the time limit for one tester run.
     *
     * @return the limit in seconds
     */
    int getRunTimeoutSeconds();

    /**
     * Returns the time limit for compiling one question folder.
     *
     * @return the limit in seconds
     */
    int getCompileTimeoutSeconds();

    // ------------------------------------------------------------ runs

    /**
     * Returns the run shown for a section.
     *
     * @param section the module and section
     * @return the run, or null when the section has none
     */
    GradingRun getRun(Section section);

    /**
     * Returns the earlier runs and their backups, newest first.
     *
     * @return the run records
     */
    List<RunRecord> listRuns();

    // ---------------------------------------------------------- import

    /**
     * Returns stand-in files for an input group, used in demo mode.
     *
     * @param kind the input group
     * @return synthetic paths, or an empty list in live mode
     */
    List<Path> getSampleFiles(InputKind kind);

    /**
     * Returns the path config.properties names for an input group.
     *
     * @param kind the roster, tester or starter group
     * @return the configured path, or null if none
     */
    Path getConfiguredPath(InputKind kind);

    /**
     * Checks whether a chosen file or folder can be used, without grading.
     *
     * @param kind the input group
     * @param path the chosen file or folder
     * @return the checked item
     */
    ImportItem inspect(InputKind kind, Path path);

    // --------------------------------------------------------- grading

    /**
     * Starts grading a batch in the background.
     *
     * @param batch the chosen files
     * @param section the batch's section
     * @param listener told about progress, possibly on another thread
     * @param callback told how the run ended, on the JavaFX thread
     */
    void startGrading(ImportBatch batch, Section section,
            ProgressListener listener, RunCallback callback);

    /**
     * Stops the batch after the current submission. Results are kept.
     */
    void cancelGrading();

    /**
     * Grades a run's submissions that were cancelled before they started.
     *
     * @param gradingRun the run
     * @param listener told about progress
     * @param callback told how the run ended
     */
    void resumeGrading(GradingRun gradingRun, ProgressListener listener,
            RunCallback callback);

    /**
     * Grades the failed and cancelled submissions of a run again.
     *
     * @param gradingRun the run
     * @param listener told about progress
     * @param callback told how the run ended
     */
    void retryFailedSubmissions(GradingRun gradingRun,
            ProgressListener listener, RunCallback callback);

    /**
     * Says whether retryFailedSubmissions can work on a run now.
     *
     * @param gradingRun the run, or null
     * @return true when its failed submissions can be graded again
     */
    boolean canRetryFailed(GradingRun gradingRun);

    /**
     * Runs one question's tester again and replaces only that result.
     *
     * @param submission the student
     * @param questionId the question, such as Q1a
     * @return what happened
     */
    ActionResult retryQuestion(Submission submission, String questionId);

    /**
     * Grades one submission again from the start.
     *
     * @param submission the student
     * @return what happened
     */
    ActionResult retrySubmission(Submission submission);

    // ------------------------------------------------- anomalies, repairs

    /**
     * Returns every anomaly of a run.
     *
     * @param gradingRun the run, or null
     * @return the anomalies, in submission order
     */
    List<Anomaly> listAnomalies(GradingRun gradingRun);

    /**
     * Returns the review decision about an anomaly.
     *
     * @param anomaly the anomaly
     * @return the decision
     */
    ReviewDecision getDecision(Anomaly anomaly);

    /**
     * Records a review decision about an anomaly. It never changes a mark.
     *
     * @param anomaly the anomaly
     * @param decision the new decision
     * @return what happened
     */
    ActionResult setDecision(Anomaly anomaly, ReviewDecision decision);

    /**
     * Says whether an anomaly still waits for the instructor.
     *
     * @param anomaly the anomaly
     * @return true when its flag is open and its repair is not settled
     */
    boolean needsReview(Anomaly anomaly);

    /**
     * Returns the validation findings of a run with their repairs.
     *
     * @param gradingRun the run, or null
     * @return the findings
     */
    List<RepairItem> listRepairs(GradingRun gradingRun);

    /**
     * Approves a repair of the working copy.
     *
     * @param repair the repair
     * @return what happened
     */
    ActionResult approveRepair(RepairItem repair);

    /**
     * Rejects a repair, which undoes it on the working copy.
     *
     * @param repair the repair
     * @return what happened
     */
    ActionResult rejectRepair(RepairItem repair);

    /**
     * Returns a decided repair to Applied, waiting for review again.
     *
     * @param repair the repair
     * @return what happened
     */
    ActionResult undoRepairDecision(RepairItem repair);

    // ----------------------------------------------------------- source

    /**
     * Compiles and runs one question's tester again for one student and
     * returns what it printed. Nothing is saved: the marks stay as they
     * are.
     *
     * @param submission the student
     * @param gradingRun the run the student was graded in, whose
     *        assessment and tester folder are used, or null for the ones
     *        in the settings
     * @param question the question to run again
     * @return the fresh result, with its tests and output
     * @throws GradingException if there is nothing to run; the message
     *         says why in plain words
     */
    QuestionResult rerunQuestion(Submission submission,
            GradingRun gradingRun, Question question)
            throws GradingException;

    /**
     * Returns the text of one file of a working copy.
     *
     * @param submission the student
     * @param relativePath the file, relative to the working copy
     * @return the text, or an empty string when it cannot be read
     */
    String readSourceFile(Submission submission, String relativePath);

    // ----------------------------------------------------------- marks

    /**
     * Changes a question's mark by hand.
     *
     * @param submission the student
     * @param questionId the question
     * @param newScore the new mark, within the question's maximum
     * @param reason why the mark changed; required
     * @return what happened
     */
    ActionResult adjustScore(Submission submission, String questionId,
            double newScore, String reason);

    /**
     * Restores the mark before a manual change.
     *
     * @param adjustment the change to undo
     * @return what happened
     */
    ActionResult undoAdjustment(ScoreAdjustment adjustment);

    /**
     * Returns the manual changes to one student's marks, newest first.
     *
     * @param submission the student
     * @return the changes
     */
    List<ScoreAdjustment> listAdjustments(Submission submission);

    // ---------------------------------------------------------- export

    /**
     * Exports results.
     *
     * @param gradingRun the run
     * @param scope the students to include
     * @param formatName "csv" or "json"
     * @return what happened, with a preview or the written file
     */
    ActionResult export(GradingRun gradingRun, List<Submission> scope,
            String formatName);

    // --------------------------------------------------- statistics

    /**
     * Returns the statistics for a module and section.
     *
     * @param moduleCode the module, or null for all
     * @param sectionCode the section, or null for all
     * @return the summary, or null when none is available
     */
    StatisticsSummary summarise(String moduleCode, String sectionCode);

    // ------------------------------------------------- history, audit

    /**
     * Backs up the saved results now.
     *
     * @return what happened
     */
    ActionResult backupNow();

    /**
     * Restores an earlier run, after saving a snapshot of the current one.
     * A backend without RESTORE_PREVIEW returns Not connected yet.
     *
     * @param record the run to restore
     * @return what happened
     */
    ActionResult restore(RunRecord record);

    /**
     * Restores the saved results from a backup file.
     *
     * @param backupFile the chosen backup file
     * @return what happened
     */
    ActionResult restoreBackup(Path backupFile);

    /**
     * Returns the audit log, newest first.
     *
     * @return the entries
     */
    List<AuditEntry> listAuditEntries();

    /**
     * Records an instructor action in the audit log.
     *
     * @param action a short name for the action
     * @param detail the full description
     */
    void recordAudit(String action, String detail);

    // -------------------------------------------------------- settings

    /**
     * Returns the value of a setting.
     *
     * @param key the setting name
     * @return the value, or an empty string
     */
    String getSetting(String key);

    /**
     * Checks a new value for a setting.
     *
     * @param key the setting name
     * @param value the new value
     * @return an empty string when the value is fine, or a message
     */
    String validateSetting(String key, String value);

    /**
     * Saves changed settings.
     *
     * @param changes the new values, by setting name
     * @return what happened
     */
    ActionResult saveSettings(Map<String, String> changes);

    /**
     * Saves changed settings, checking and saving them in a given order.
     *
     * @param keys the names of the changed settings, in order
     * @param changes the new values, by setting name
     * @return what happened
     */
    ActionResult saveSettings(List<String> keys,
            Map<String, String> changes);

    // ----------------------------------------------------- assessments

    /**
     * Lists the saved assessments, sorted by name. The one in use, whose
     * name getAssessment gives, is always listed.
     *
     * @return the assessments
     */
    List<SavedAssessment> listAssessments();

    /**
     * Checks an assessment before it is saved.
     *
     * @param draft the assessment
     * @param originalName the name it had before, or null for a new one
     * @return an empty string when it can be saved, or what is wrong
     */
    String validateAssessment(SavedAssessment draft, String originalName);

    /**
     * Saves an assessment, replacing the one it was edited from. When that
     * one is in use, grading uses the saved version from now on.
     *
     * @param draft the assessment
     * @param originalName the name it had before, or null for a new one
     * @return what happened
     */
    ActionResult saveAssessment(SavedAssessment draft, String originalName);

    /**
     * Grades with a saved assessment from now on: its questions, tester
     * folder and starter folder replace the ones in use.
     *
     * @param name the assessment's name
     * @return what happened
     */
    ActionResult useAssessment(String name);

    /**
     * Deletes a saved assessment that is not in use. Results graded with
     * it are kept.
     *
     * @param name the assessment's name
     * @return what happened
     */
    ActionResult deleteAssessment(String name);

    // ------------------------------------------------------ similarity

    /**
     * Returns the pairs of similar submissions in a run.
     *
     * @param gradingRun the run, or null
     * @return the pairs, most similar first
     */
    List<SimilarityPair> listSimilarPairs(GradingRun gradingRun);

    /**
     * Returns the evidence for one similar pair.
     *
     * @param pair the pair
     * @return the evidence, or null when none is available
     */
    SimilarityDetail getSimilarityDetail(SimilarityPair pair);

    /**
     * Says whether the similarity check is switched on.
     *
     * @return true when it runs after grading
     */
    boolean isSimilarityEnabled();

    // ------------------------------------------------------------ demo

    /**
     * Switches between six sample students and a large cohort. Does
     * nothing in live mode.
     *
     * @param large true for the large cohort
     */
    void useLargeSampleCohort(boolean large);
}
