/*
 * File: RegradeJob.java
 *
 * Purpose: Grades one student, one question of a student, or the students
 * ticked on Review again, on a background thread. Grading again compiles
 * and runs testers, so it would freeze the window on its thread.
 *
 * Where it fits: StudentPanel starts one for "Grade this student again"
 * and "Grade Q2b again", and ReviewScreen for "Grade 3 ticked students
 * again", in live mode. The demo runs it on the window's thread with
 * runHere, as its grading is instant. For several students the job keeps
 * whose totals changed, so ReviewScreen can show a summary at the end.
 *
 * Note on untaught Java: javafx.concurrent.Task runs the job on a
 * background thread, as UndoFixJob does for undoing fixes.
 */
package gradezzy.ui.flow;

import java.util.ArrayList;
import java.util.List;
import java.util.function.Consumer;

import javafx.concurrent.Task;

import gradezzy.model.GradingRun;
import gradezzy.model.ScoreAdjustment;
import gradezzy.model.Submission;
import gradezzy.ui.backend.ActionResult;
import gradezzy.ui.backend.DashboardBackend;
import gradezzy.ui.backend.ResultState;
import gradezzy.util.TextUtils;

/**
 * A background task that grades students, or one question, again.
 *
 * <p>Tier: SHOULD. Owner: Jing Yuan.
 */
public class RegradeJob extends Task<ActionResult> {

    // The name of the thread the job runs on
    private static final String THREAD_NAME = "gradezzy-regrade";

    // Shown for a student who has no total
    private static final String NOT_GRADED = "not graded";

    // Separates the questions whose marks set by hand were kept
    private static final String LIST_SEPARATOR = ", ";

    // Separates a student's name from what happened to them
    private static final String NAME_END = ": ";

    // The backend that grades
    private final DashboardBackend backend;

    // The run the students belong to
    private final GradingRun run;

    // The students to grade again, in order
    private final List<Submission> students = new ArrayList<>();

    // The question to grade again, or null to grade whole students
    private final String questionId;

    // True when the job grades the ticked students and keeps a summary
    private final boolean several;

    // For several students: "Elena Wu: 17 to 18" for each changed total
    private final List<String> changedTotals = new ArrayList<>();

    // For several students: the names of those whose total stayed
    private final List<String> sameTotals = new ArrayList<>();

    // For several students: "Elena Wu: Q2b" for each kept mark set by hand
    private final List<String> keptHandMarks = new ArrayList<>();

    // For several students: the names of those whose upload was refused
    private final List<String> refusedUploads = new ArrayList<>();

    // For several students: "Elena Wu: why" for each who could not be
    // graded again
    private final List<String> failures = new ArrayList<>();

    /**
     * Creates the job that grades one student again from the start.
     *
     * @param backend the backend that grades
     * @param run the run the student belongs to
     * @param student the student
     */
    public RegradeJob(DashboardBackend backend, GradingRun run,
            Submission student) {
        this.backend = backend;
        this.run = run;
        this.students.add(student);
        this.questionId = null;
        this.several = false;
    }

    /**
     * Creates the job that grades one question of a student again.
     *
     * @param backend the backend that grades
     * @param run the run the student belongs to
     * @param student the student
     * @param questionId the question, such as Q2b
     */
    public RegradeJob(DashboardBackend backend, GradingRun run,
            Submission student, String questionId) {
        this.backend = backend;
        this.run = run;
        this.students.add(student);
        this.questionId = questionId;
        this.several = false;
    }

    /**
     * Creates the job that grades the ticked students again, one after
     * another, and keeps a summary of what changed.
     *
     * @param backend the backend that grades
     * @param run the run the students belong to
     * @param students the students, in order
     */
    public RegradeJob(DashboardBackend backend, GradingRun run,
            List<Submission> students) {
        this.backend = backend;
        this.run = run;
        this.students.addAll(students);
        this.questionId = null;
        this.several = true;
    }

    /**
     * Starts the job on a background thread, unless another job still
     * grades students again or undoes a fix. Until it ends, the state
     * names the students it still has to grade, and onStep runs each
     * time one of them is done.
     *
     * @param state the window's state, shared by every screen
     * @param onEnd what to do with the outcome once the job ends
     * @param onStep what to do after each student, or null
     * @return true when the job started
     */
    public boolean startOnce(FlowState state, Consumer<ActionResult> onEnd,
            Runnable onStep) {
        boolean busy = state.isRegrading()
                || state.getFixInProgress() != null;
        if (busy) {
            return false;
        }

        setOnSucceeded(event -> ended(state, onEnd));
        setOnFailed(event -> ended(state, onEnd));
        workDoneProperty().addListener(
                (value, before, now) -> stepEnded(state, onStep));
        state.setRegradesInProgress(studentsFrom(0));
        // A daemon thread does not keep GradeZZY open
        Thread worker = new Thread(this, THREAD_NAME);
        worker.setDaemon(true);
        worker.start();
        return true;
    }

    /**
     * Grades on the calling thread, for the demo, whose grading is
     * instant.
     *
     * @return the outcome
     */
    public ActionResult runHere() {
        return call();
    }

    /**
     * Returns the number of students the job grades again.
     *
     * @return the number
     */
    public int countStudents() {
        return students.size();
    }

    /**
     * Returns the line of each student whose total changed, such as
     * "Elena Wu: 17 to 18".
     *
     * @return the lines, in grading order
     */
    public List<String> getChangedTotals() {
        return changedTotals;
    }

    /**
     * Returns the names of the students whose total stayed the same.
     *
     * @return the names, in grading order
     */
    public List<String> getSameTotals() {
        return sameTotals;
    }

    /**
     * Returns the line of each student whose marks set by hand were kept,
     * such as "Elena Wu: Q2b".
     *
     * @return the lines, in grading order
     */
    public List<String> getKeptHandMarks() {
        return keptHandMarks;
    }

    /**
     * Returns the names of the students whose upload was refused, so
     * they were not graded again.
     *
     * @return the names, in grading order
     */
    public List<String> getRefusedUploads() {
        return refusedUploads;
    }

    /**
     * Returns the line of each student who could not be graded again,
     * with the reason.
     *
     * @return the lines, in grading order
     */
    public List<String> getFailures() {
        return failures;
    }

    // Leaves in the state only the students not done yet, then tells the
    // screen. A step reported after the end changes nothing.
    private void stepEnded(FlowState state, Runnable onStep) {
        int done = (int) getWorkDone();
        if (isDone() || done < 0) {
            return;
        }
        state.setRegradesInProgress(studentsFrom(done));
        if (onStep != null) {
            onStep.run();
        }
    }

    // Returns the students from a position to the end.
    private List<Submission> studentsFrom(int start) {
        List<Submission> left = new ArrayList<>();
        for (int index = start; index < students.size(); index++) {
            left.add(students.get(index));
        }
        return left;
    }

    // Clears the students in progress and passes the outcome on. New
    // marks make an earlier export of the run out of date.
    private void ended(FlowState state, Consumer<ActionResult> onEnd) {
        state.setRegradesInProgress(new ArrayList<Submission>());
        ActionResult result = getValue();
        if (getException() != null) {
            result = ActionResult.failed("Grading again stopped: "
                    + getException().getMessage());
        }
        boolean anyGraded = result.isDone() || !changedTotals.isEmpty()
                || !sameTotals.isEmpty();
        if (anyGraded) {
            state.forgetExport(run);
        }
        onEnd.accept(result);
    }

    /**
     * Grades again. JavaFX calls this on a background thread.
     *
     * @return the outcome
     */
    @Override
    protected ActionResult call() {
        if (several) {
            return regradeAll();
        }
        Submission student = students.get(0);
        if (questionId != null) {
            return backend.retryQuestion(run, student, questionId);
        }
        return backend.retrySubmission(run, student);
    }

    // Grades every student in turn. One that cannot be graded does not
    // stop the others; the summary names them.
    private ActionResult regradeAll() {
        int total = students.size();
        updateProgress(0, total);
        for (int index = 0; index < total; index++) {
            Submission student = students.get(index);
            updateMessage(Students.name(student));
            regradeOne(student);
            int done = index + 1;
            updateProgress(done, total);
        }
        return describeAll();
    }

    // Grades one ticked student again and notes what changed.
    private void regradeOne(Submission student) {
        String name = Students.name(student);
        boolean refused = student.getIngestFailure() != null
                || ResultState.isRejectedUpload(student);
        if (refused) {
            refusedUploads.add(name);
            return;
        }

        String before = totalOf(student);
        ActionResult result = tryRegrade(student);
        if (!result.isDone()) {
            failures.add(name + NAME_END + result.getMessage());
            return;
        }
        String after = totalOf(student);
        if (before.equals(after)) {
            sameTotals.add(name);
        } else {
            changedTotals.add(name + NAME_END + before + " to " + after);
        }
        List<String> handMarked = findHandMarked(student);
        if (!handMarked.isEmpty()) {
            keptHandMarks.add(name + NAME_END
                    + TextUtils.join(LIST_SEPARATOR, handMarked));
        }
    }

    // Grades one student again, turning an unexpected error into a
    // failed outcome, so the next student is still graded.
    private ActionResult tryRegrade(Submission student) {
        try {
            return backend.retrySubmission(run, student);
        } catch (RuntimeException exception) {
            return ActionResult.failed("Grading again stopped: "
                    + exception.getMessage());
        }
    }

    // Lists the questions whose marks set by hand a student still has
    // after grading again, which are the ones that were kept.
    private List<String> findHandMarked(Submission student) {
        List<String> questions = new ArrayList<>();
        for (ScoreAdjustment change : backend.listAdjustments(run,
                student)) {
            if (!questions.contains(change.getQuestionId())) {
                questions.add(change.getQuestionId());
            }
        }
        return questions;
    }

    // The total of a student as shown on Review, or "not graded".
    private String totalOf(Submission student) {
        if (!ResultState.hasTotal(student)) {
            return NOT_GRADED;
        }
        return Parts.mark(student.getTotalScore());
    }

    // Says how many students were graded again.
    private ActionResult describeAll() {
        int graded = changedTotals.size() + sameTotals.size();
        if (graded == 0) {
            return ActionResult.failed("No student was graded again.");
        }
        String count = Parts.plural(graded, "student");
        if (graded < students.size()) {
            count = graded + " of " + Parts.plural(students.size(),
                    "student");
        }
        return ActionResult.done("Graded " + count + " again. "
                + Parts.plural(changedTotals.size(), "total")
                + " changed.");
    }
}
