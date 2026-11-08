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


import gradezzy.config.ConfigKeys;
import gradezzy.config.ExportFolders;
import gradezzy.grading.GradingException;
import gradezzy.grading.ProgressListener;
import gradezzy.model.Anomaly;
import gradezzy.model.Assessment;
import gradezzy.model.AuditEntry;
import gradezzy.model.GradingRun;
import gradezzy.model.Question;
import gradezzy.model.QuestionResult;
import gradezzy.model.SavedAssessment;
import gradezzy.model.ScoreAdjustment;
import gradezzy.model.Section;
import gradezzy.model.Student;
import gradezzy.model.Submission;
import gradezzy.plagiarism.SimilarityPair;
import gradezzy.service.ClassFileMatch;
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
     * Returns the assessment with each question's maximum mark read from
     * the testers in a folder, as a batch graded with them would use.
     *
     * @param testersDirectory the batch's tester folder, or null for the
     *        one in Settings
     * @return the assessment
     */
    Assessment getAssessment(Path testersDirectory);

    /**
     * Lists the testers in a folder that have no "score += <number>"
     * line, so their question has no maximum mark. Grading cannot start
     * while this list is not empty.
     *
     * @param testersDirectory the tester folder, or null for the one in
     *        Settings
     * @return one sentence per problem, empty when every tester is fine
     */
    List<String> checkMaxScores(Path testersDirectory);

    /**
     * Reads the score sheet's Calculated Final Grade Denominator, which
     * the question maximums should add up to.
     *
     * @param scoreSheetFile the score sheet, or null for the one in
     *        Settings
     * @return the denominator, or a negative number when the sheet gives
     *         no single one
     */
    double getScoreSheetDenominator(Path scoreSheetFile);

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

    /**
     * Lists what is wrong with a tester folder before grading: questions
     * with no tester, data files that are missing and testers that do not
     * compile with the starter code. It compiles, so it takes seconds.
     *
     * @param testersDirectory the tester folder
     * @param starterDirectory the starter folder chosen on Set up, or null
     *        for the one in Settings
     * @return one sentence per problem, empty when the folder is fine
     */
    List<String> checkTesters(Path testersDirectory, Path starterDirectory);

    /**
     * Checks whether a score sheet, tester folder or starter folder
     * belongs with the chosen uploads, and looks nearby for one that fits
     * them better. It only reads files, so it takes well under a second,
     * but still call it off the window's thread. Grading is never blocked
     * by what it finds.
     *
     * @param kind the roster, tester or starter group
     * @param chosen the chosen file or folder
     * @param submissions the usable uploads chosen on Set up
     * @return what the check found; ClassFileMatch.fits() when it has
     *         nothing to say
     */
    ClassFileMatch checkClassFileMatch(InputKind kind, Path chosen,
            List<ImportItem> submissions);

    /**
     * Looks near a graded run's score sheet for one that lists more of
     * its graded students, for Export when most of them are not on the
     * sheet. It only reads files, but call it off the window's thread.
     *
     * @param gradingRun the run
     * @return the better sheet; ClassFileMatch.fits() when the sheet fits
     *         or nothing better is found
     */
    ClassFileMatch findBetterScoreSheet(GradingRun gradingRun);

    /**
     * Gives a graded run another score sheet without grading again: each
     * student is matched to the new sheet and the run is saved. Call it
     * off the window's thread.
     *
     * @param gradingRun the run
     * @param scoreSheet the score sheet to use from now on
     * @return done, or failed with the reason
     */
    ActionResult changeScoreSheet(GradingRun gradingRun, Path scoreSheet);

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
     * Returns the run a late batch of a section would join: its newest
     * run, from this session or saved.
     *
     * @param section the section
     * @return the run, or null when the section has none to join
     */
    GradingRun findRunToJoin(Section section);

    /**
     * Grades a batch into an existing run of the same section in the
     * background. A student graded before is graded again, and the new
     * result replaces the old one.
     *
     * @param gradingRun the run the batch joins
     * @param batch the chosen files
     * @param listener told about progress, possibly on another thread
     * @param callback told how the run ended, on the JavaFX thread
     */
    void addToRun(GradingRun gradingRun, ImportBatch batch,
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
     * Lists the students on the run's score sheet who have no submission
     * in the run.
     *
     * @param gradingRun the run
     * @return the students, in score sheet order; empty when the score
     *         sheet cannot be read
     */
    List<Student> listMissingStudents(GradingRun gradingRun);

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
     * Says whether the instructor checked a student graded with 0 marks
     * that nothing flagged. Kept until GradeZZY closes.
     *
     * @param submission the student
     * @return true once checked
     */
    default boolean isZeroChecked(Submission submission) {
        return ZeroChecks.isChecked(submission);
    }

    /**
     * Marks a student's 0 as checked, or takes that back. It never
     * changes a mark, and it is in the activity log.
     *
     * @param submission the student
     * @param checked true to mark it as checked
     * @return what happened
     */
    default ActionResult setZeroChecked(Submission submission,
            boolean checked) {
        if (submission == null) {
            return ActionResult.failed("Choose a student first.");
        }
        if (ZeroChecks.set(submission, checked)) {
            recordActivity(checked ? ReviewDecision.AUDIT_CHECKED
                    : ReviewDecision.AUDIT_TAKEN_BACK,
                    submission.getUsername() + ": " + ZERO_CHECK_WORDS);
        }
        return ActionResult.done(checked ? "Marked the 0 of "
                + submission.getUsername() + " as checked. No mark changed."
                : "The 0 of " + submission.getUsername()
                + " waits for a look again.");
    }

    /** The activity log's words for a 0 nothing flagged. */
    String ZERO_CHECK_WORDS = "graded with 0 marks";

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
     * Applies a rejected repair again, which grades the student again.
     *
     * @param repair the repair
     * @return what happened
     */
    ActionResult reapplyRepair(RepairItem repair);

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
     * @param run the run the student is in, as shown on Review
     * @param submission the student
     * @param questionId the question
     * @param newScore the new mark, within the question's maximum
     * @param reason why the mark changed; required
     * @return what happened
     */
    ActionResult adjustScore(GradingRun run, Submission submission,
            String questionId, double newScore, String reason);

    /**
     * Restores the mark before a manual change.
     *
     * @param run the run the change was made in, as shown on Review
     * @param adjustment the change to undo
     * @return what happened
     */
    ActionResult undoAdjustment(GradingRun run, ScoreAdjustment adjustment);

    /**
     * Returns the manual changes to one student's marks, oldest first.
     *
     * @param run the run the student is in, as shown on Review
     * @param submission the student
     * @return the changes
     */
    List<ScoreAdjustment> listAdjustments(GradingRun run,
            Submission submission);

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
     * Adds a line to the activity log, such as a section that was
     * deleted.
     *
     * @param action what was done, such as "Section deleted"
     * @param detail what it was done to
     */
    void recordActivity(String action, String detail);

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
     * Returns the score sheet of a section: its own if one was chosen,
     * else the one in SCORE_SHEET_FILE.
     *
     * @param section the section, or null for the one for all
     * @return the score sheet's path, as written in the settings
     */
    default String getScoreSheet(Section section) {
        String module = section == null ? null : section.getModuleCode();
        String code = section == null ? null : section.getSectionCode();
        if (code == null) {
            return getSetting(ConfigKeys.SCORE_SHEET_FILE);
        }
        String own = ExportFolders.exact(getSetting(
                ConfigKeys.SCORE_SHEET_BY_SECTION), ExportFolders.keyOf(
                module, code));
        return own.isEmpty() ? getSetting(ConfigKeys.SCORE_SHEET_FILE) : own;
    }

    /**
     * Returns the folder a section's exports are saved in: its own if one
     * is set in Settings, else its module's, else the folder for all.
     *
     * @param section the section, or null for the folder for all
     * @return the folder
     */
    default String getExportFolder(Section section) {
        String module = section == null ? null : section.getModuleCode();
        String code = section == null ? null : section.getSectionCode();
        return ExportFolders.resolve(getSetting(ConfigKeys.OUTPUT_DIR),
                getSetting(ConfigKeys.OUTPUT_DIR_BY_SECTION), module, code);
    }

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
     * Returns the pairs of similar submissions found by the last check of
     * a run, without checking again.
     *
     * @param gradingRun the run, or null
     * @return the pairs, most similar first
     */
    List<SimilarityPair> listSimilarPairs(GradingRun gradingRun);

    /**
     * Checks a run for similar submissions again and flags the pairs.
     *
     * @param gradingRun the run, or null
     * @return the pairs, most similar first
     */
    List<SimilarityPair> checkSimilarity(GradingRun gradingRun);

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
