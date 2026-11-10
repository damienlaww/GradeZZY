/*
 * File: StatusCounts.java
 *
 * Purpose: Counts students by their state, with one rule for who needs
 * a look, so every screen shows the same numbers.
 *
 * Where it fits: Grade, Review, Statistics, Export and Home count with
 * it. It is worked out from the submissions, never stored.
 */
package gradezzy.ui.backend;

import java.util.HashMap;
import java.util.List;
import java.util.Map;

import gradezzy.model.Anomaly;
import gradezzy.model.Submission;

/**
 * How many students are in each state, and how many need a look.
 *
 * <p>Tier: MUST.
 */
public final class StatusCounts {

    // How many students are in each counted state
    private final Map<ResultState, Integer> stateCounts = new HashMap<>();

    // How many students need a look before export
    private int needsLookCount = 0;

    // How many were not fully graded, leaving out unopened files
    private int notFullyGradedCount = 0;

    // How many chosen files could not be opened
    private int couldNotOpenCount = 0;

    // How many students were counted
    private int total = 0;

    // Made only by of().
    private StatusCounts() {
    }

    /**
     * Counts a list of students.
     *
     * @param rows the students
     * @param backend the backend that knows the review decisions
     * @return the counts
     */
    public static StatusCounts of(List<Submission> rows,
            DashboardBackend backend) {
        StatusCounts counts = new StatusCounts();
        for (Submission submission : rows) {
            counts.add(submission, backend);
        }
        return counts;
    }

    /**
     * Returns the state a student is counted under: their state, except
     * that a 0 someone checked, or whose flags are all settled, counts
     * as graded.
     *
     * @param submission the student
     * @param backend the backend that knows the review decisions
     * @return the state to count
     */
    public static ResultState countedState(Submission submission,
            DashboardBackend backend) {
        ResultState state = ResultState.of(submission, backend);
        boolean settledZero = state == ResultState.ZERO
                && !isUncheckedZero(submission, state, backend);
        if (settledZero) {
            return ResultState.COMPLETED;
        }
        return state;
    }

    /**
     * Says whether a student needs the instructor before export: an open
     * fix or flag, a 0 nobody checked, or code that did not compile,
     * timed out or could not be graded. Students not graded yet, and
     * rows made on Review, do not count.
     *
     * @param submission the student
     * @param backend the backend that knows the review decisions
     * @return true when the student needs a look
     */
    public static boolean needsLook(Submission submission,
            DashboardBackend backend) {
        if (ResultState.isMadeOnReview(submission)) {
            return false;
        }
        ResultState state = ResultState.of(submission, backend);
        boolean notGradedYet = state == ResultState.UNGRADED
                || state == ResultState.NO_SUBMISSION;
        if (notGradedYet) {
            return false;
        }
        return hasOpenItem(submission, backend)
                || isUncheckedZero(submission, state, backend)
                || isNotFullyGraded(state);
    }

    /**
     * Says whether a total of 0 still needs a look: nothing was flagged
     * and nobody marked the 0 as checked.
     *
     * @param submission the student
     * @param state the student's state
     * @param backend the backend that knows which 0s were checked
     * @return true for a 0 nobody has looked into
     */
    public static boolean isUncheckedZero(Submission submission,
            ResultState state, DashboardBackend backend) {
        return state == ResultState.ZERO
                && submission.getAnomalies().isEmpty()
                && !backend.isZeroChecked(submission);
    }

    /**
     * Says whether a state means the student was not fully graded: the
     * code did not compile, timed out or could not be graded.
     *
     * @param state the state
     * @return true when the student was not fully graded
     */
    public static boolean isNotFullyGraded(ResultState state) {
        return state == ResultState.COMPILE_ERROR
                || state == ResultState.TIMED_OUT
                || state == ResultState.COULD_NOT_GRADE;
    }

    /**
     * Returns how many students are counted under a state.
     *
     * @param state the state
     * @return the count
     */
    public int count(ResultState state) {
        Integer count = stateCounts.get(state);
        if (count == null) {
            return 0;
        }
        return count;
    }

    /**
     * Returns how many students need a look before export.
     *
     * @return the count
     */
    public int getNeedsLook() {
        return needsLookCount;
    }

    /**
     * Returns how many students did not compile, timed out or could not
     * be graded, leaving out files that could not be opened.
     *
     * @return the count
     */
    public int getNotFullyGraded() {
        return notFullyGradedCount;
    }

    /**
     * Returns how many chosen files could not be opened.
     *
     * @return the count
     */
    public int getCouldNotOpen() {
        return couldNotOpenCount;
    }

    /**
     * Returns how many students were counted.
     *
     * @return the count
     */
    public int getTotal() {
        return total;
    }

    // Counts one student.
    private void add(Submission submission, DashboardBackend backend) {
        ResultState state = countedState(submission, backend);
        int newCount = count(state) + 1;
        stateCounts.put(state, newCount);
        total++;

        if (ResultState.isSkippedFile(submission)) {
            couldNotOpenCount++;
        } else if (isNotFullyGraded(state)) {
            notFullyGradedCount++;
        }
        if (needsLook(submission, backend)) {
            needsLookCount++;
        }
    }

    // Says whether a fix or flag of the student still waits for review.
    private static boolean hasOpenItem(Submission submission,
            DashboardBackend backend) {
        for (Anomaly anomaly : submission.getAnomalies()) {
            if (backend.needsReview(anomaly)) {
                return true;
            }
        }
        return false;
    }
}
