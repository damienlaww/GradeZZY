/*
 * File: GradingJob.java
 *
 * Purpose: One batch being graded, or just graded, for one section: how
 * many students it grades and has finished, who is being graded now and
 * at which step, each result so far, when it started, and how it ended.
 * Several sections can be graded at once, so each batch keeps its own.
 *
 * Where it fits: GradeScreen starts one for the section on screen and
 * hands it to the backend as the batch's progress listener and callback.
 * FlowState keeps the latest job of each section. FlowWindow is told
 * about each change, so Grade, Home and the notices show every batch,
 * whichever section is on screen.
 *
 * Note on untaught Java: Platform.runLater hands each change from the
 * grading threads to the window's thread, as GradeScreen did before.
 */
package gradezzy.ui.flow;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;

import javafx.application.Platform;

import gradezzy.grading.ProgressListener;
import gradezzy.model.GradingRun;
import gradezzy.model.Section;
import gradezzy.model.Submission;
import gradezzy.model.SubmissionStatus;
import gradezzy.ui.backend.ImportItem;
import gradezzy.ui.backend.PipelineStages;
import gradezzy.ui.backend.RunCallback;

/**
 * One section's batch being graded and how far it has got.
 *
 * <p>Tier: COULD.
 */
public class GradingJob implements ProgressListener, RunCallback {

    // The step of grading before the first one has started
    private static final int NO_STAGE = -1;

    // The window told about each change, or null for a job made only to
    // show a run that has ended
    private final FlowWindow window;

    // The section graded
    private final Section section;

    // The section and assessment, such as "CS102 G1 · Lab Test 2"
    private final String about;

    // The chosen files grading skips, kept for the run when it ends
    private final List<ImportItem> skippedFiles = new ArrayList<>();

    // The usernames the batch grades, in lower case, so Set up can warn
    // before another section's batch holds one of them too
    private final List<String> usernames = new ArrayList<>();

    // Every student with a result, by username, and the usernames in the
    // order they first got one
    private final Map<String, Submission> results = new HashMap<>();
    private final List<String> resultOrder = new ArrayList<>();

    // How many students the batch grades, and how many are finished
    private int total = 0;
    private int finished = 0;

    // When the batch started, in milliseconds
    private final long startedAt = System.currentTimeMillis();

    // The student being graded now and what is being done to them, or
    // null and "" before the first
    private Submission nowSubmission = null;
    private String nowStageName = "";

    // True after Cancel was pressed, and once the batch has ended
    private boolean cancelling = false;
    private boolean ended = false;

    // The run the batch graded, once it ended, or null
    private GradingRun run = null;

    // Why grading could not run, or null
    private String failure = null;

    /**
     * Creates the job of a section's batch.
     *
     * @param window the window told about each change, or null for none
     * @param section the section graded
     * @param about the section and assessment, as shown on Grade
     */
    public GradingJob(FlowWindow window, Section section, String about) {
        this.window = window;
        this.section = section;
        this.about = about;
    }

    /**
     * Builds a job that shows a run that is not being graded, as it
     * ended: its finished students and how many it has.
     *
     * @param run the run
     * @param about the section and assessment, as shown on Grade
     * @return the job, already ended
     */
    public static GradingJob ofEndedRun(GradingRun run, String about) {
        GradingJob job = new GradingJob(null, run.getSection(), about);
        for (Submission submission : run.getSubmissions()) {
            if (!isUnfinished(submission)) {
                job.finished++;
                job.addResult(submission);
            }
        }
        job.total = run.getSubmissions().size();
        job.run = run;
        job.ended = true;
        return job;
    }

    /**
     * Says whether a student is not graded yet: waiting, being graded or
     * cancelled.
     *
     * @param submission the student
     * @return true when not graded yet
     */
    public static boolean isUnfinished(Submission submission) {
        SubmissionStatus status = submission.getStatus();
        return status == SubmissionStatus.PENDING
                || status == SubmissionStatus.RUNNING
                || status == SubmissionStatus.CANCELLED;
    }

    // ------------------------------------------------------------ facts

    /**
     * Says whether this job grades a section.
     *
     * @param other the section, or null
     * @return true when it is the same section
     */
    public boolean isOf(Section other) {
        return other != null && section != null
                && section.getLabel().equalsIgnoreCase(other.getLabel());
    }

    /**
     * Returns the section graded.
     *
     * @return the section
     */
    public Section getSection() {
        return section;
    }

    /**
     * Returns the section and assessment, such as "CS102 G1 · Lab Test 2".
     *
     * @return the words
     */
    public String getAbout() {
        return about;
    }

    /**
     * Returns how many students the batch grades.
     *
     * @return the number
     */
    public int getTotal() {
        return total;
    }

    /**
     * Sets how many students the batch grades, before the backend says.
     *
     * @param total the number
     */
    public void setTotal(int total) {
        this.total = total;
    }

    /**
     * Returns how many students are finished.
     *
     * @return the number
     */
    public int getFinished() {
        return finished;
    }

    /**
     * Returns when the batch started.
     *
     * @return the time, in milliseconds
     */
    public long getStartedAt() {
        return startedAt;
    }

    /**
     * Returns the student being graded now.
     *
     * @return the student, or null before the first
     */
    public Submission getNowSubmission() {
        return nowSubmission;
    }

    /**
     * Returns the name of the step the student now is at.
     *
     * @return the step's name, or "" before the first step
     */
    public String getNowStageName() {
        return nowStageName;
    }

    /**
     * Returns the index of the step the student now is at.
     *
     * @return the index, or -1 before the first step
     */
    public int getNowStage() {
        if (nowStageName.isEmpty()) {
            return NO_STAGE;
        }
        return PipelineStages.indexOf(nowStageName);
    }

    /**
     * Returns every result so far, in the order they first arrived.
     *
     * @return the students with a result
     */
    public List<Submission> getResults() {
        List<Submission> inOrder = new ArrayList<>();
        for (String username : resultOrder) {
            inOrder.add(results.get(username));
        }
        return inOrder;
    }

    /**
     * Keeps a student's result; a student who already has one keeps
     * their place in the order.
     *
     * @param submission the student
     */
    public void addResult(Submission submission) {
        String username = submission.getUsername();
        if (!results.containsKey(username)) {
            resultOrder.add(username);
        }
        results.put(username, submission);
    }

    /**
     * Returns the chosen files grading skips, which the job gives the
     * run when it ends.
     *
     * @return the files, which the caller may add to
     */
    public List<ImportItem> getSkippedFiles() {
        return skippedFiles;
    }

    /**
     * Notes the students the batch grades.
     *
     * @param students their usernames
     */
    public void addUsernames(List<String> students) {
        for (String username : students) {
            String key = username.toLowerCase(Locale.ROOT);
            if (!usernames.contains(key)) {
                usernames.add(key);
            }
        }
    }

    /**
     * Lists the students of a batch this one grades too.
     *
     * @param students the other batch's usernames
     * @return the ones this batch also grades, each once
     */
    public List<String> findShared(List<String> students) {
        List<String> shared = new ArrayList<>();
        for (String username : students) {
            String key = username.toLowerCase(Locale.ROOT);
            if (usernames.contains(key) && !shared.contains(key)) {
                shared.add(key);
            }
        }
        return shared;
    }

    /**
     * Says whether Cancel was pressed.
     *
     * @return true after Cancel
     */
    public boolean isCancelling() {
        return cancelling;
    }

    /**
     * Notes that Cancel was pressed.
     */
    public void markCancelling() {
        cancelling = true;
    }

    /**
     * Says whether the batch has ended: finished, stopped or failed.
     *
     * @return true once it ended
     */
    public boolean isEnded() {
        return ended;
    }

    /**
     * Returns the run the batch graded, once it ended.
     *
     * @return the run, or null
     */
    public GradingRun getRun() {
        return run;
    }

    /**
     * Returns why grading could not run.
     *
     * @return the reason, or null
     */
    public String getFailure() {
        return failure;
    }

    /**
     * Says whether the run stopped with students not graded yet.
     *
     * @return true when some are waiting
     */
    public boolean hasUnfinished() {
        if (run == null) {
            return false;
        }
        for (Submission submission : run.getSubmissions()) {
            if (isUnfinished(submission)) {
                return true;
            }
        }
        return false;
    }

    // ------------------------------------------------ progress listener

    /**
     * Notes how many students the batch grades.
     */
    @Override
    public void onRunStarted(int totalSubmissions) {
        Platform.runLater(() -> totalKnown(totalSubmissions));
    }

    /**
     * Notes which student is being graded now.
     */
    @Override
    public void onSubmissionStarted(Submission submission) {
        Platform.runLater(() -> studentStarted(submission));
    }

    /**
     * Notes what is happening to the student now.
     */
    @Override
    public void onStageStarted(Submission submission, String stageName) {
        Platform.runLater(() -> stageStarted(submission, stageName));
    }

    /**
     * Counts the student as done.
     */
    @Override
    public void onSubmissionFinished(Submission submission) {
        Platform.runLater(() -> studentFinished(submission));
    }

    /**
     * Does nothing: onFinished, called after it, ends the job.
     */
    @Override
    public void onRunFinished(GradingRun gradingRun) {
        // The callback below ends the job; nothing to do here
    }

    // Keeps the number of students, on the window's thread.
    private void totalKnown(int totalSubmissions) {
        total = totalSubmissions;
        changed(false);
    }

    // Keeps the student being graded, on the window's thread.
    private void studentStarted(Submission submission) {
        nowSubmission = submission;
        nowStageName = "";
        changed(false);
    }

    // Keeps the step the student is at, on the window's thread.
    private void stageStarted(Submission submission, String stageName) {
        nowSubmission = submission;
        nowStageName = stageName;
        changed(false);
    }

    // Counts a finished student and keeps the result, on the window's
    // thread.
    private void studentFinished(Submission submission) {
        finished++;
        addResult(submission);
        changed(true);
    }

    // Tells the window the job changed; counted is true when a student
    // finished, so the Home cards are drawn again only then.
    private void changed(boolean counted) {
        if (window != null) {
            window.gradingProgressed(this, counted);
        }
    }

    // ----------------------------------------------------- run callback

    /**
     * Keeps the run and every result, and tells the window the batch
     * ended. Called on the window's thread.
     */
    @Override
    public void onFinished(GradingRun gradingRun) {
        run = gradingRun;
        ended = true;
        nowSubmission = null;
        if (gradingRun != null) {
            for (Submission submission : gradingRun.getSubmissions()) {
                if (!isUnfinished(submission)) {
                    addResult(submission);
                }
            }
        }
        if (window != null) {
            window.gradingEnded(this);
        }
    }

    /**
     * Keeps why grading could not run, and tells the window the batch
     * ended. Called on the window's thread.
     */
    @Override
    public void onFailed(String message) {
        failure = message;
        ended = true;
        nowSubmission = null;
        if (window != null) {
            window.gradingEnded(this);
        }
    }
}
