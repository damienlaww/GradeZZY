/*
 * File: Students.java
 *
 * Purpose: Turns a submission into what the instructor reads: the
 * student's name and a status in plain words with its colour.
 *
 * Where it fits: Grade, Review and Export use it so a student looks the
 * same on every screen.
 */
package gradezzy.ui.flow;

import javafx.scene.control.Label;

import gradezzy.model.Anomaly;
import gradezzy.model.Student;
import gradezzy.model.Submission;
import gradezzy.ui.backend.DashboardBackend;
import gradezzy.ui.backend.ResultState;

/**
 * Names and statuses of students.
 */
public final class Students {

    // Not used: every method is static.
    private Students() {
    }

    /**
     * Returns the student's full name, or the username when the student
     * is not on the score sheet.
     *
     * @param submission the submission
     * @return the name
     */
    public static String name(Submission submission) {
        Student student = submission.getStudent();
        if (student == null) {
            return submission.getUsername();
        }
        String name = (student.getFirstName() + " "
                + student.getLastName()).trim();
        if (name.isEmpty()) {
            return submission.getUsername();
        }
        return name;
    }

    /**
     * Returns the student's result state.
     *
     * @param submission the submission
     * @param backend the backend that knows the review decisions
     * @return the state
     */
    public static ResultState state(Submission submission,
            DashboardBackend backend) {
        return ResultState.of(submission, backend);
    }

    /**
     * Counts the open fixes and flags of a student.
     *
     * @param submission the submission
     * @param backend the backend that knows the review decisions
     * @return how many still need the instructor
     */
    public static int openCount(Submission submission,
            DashboardBackend backend) {
        int count = 0;
        for (Anomaly anomaly : submission.getAnomalies()) {
            if (backend.needsReview(anomaly)) {
                count++;
            }
        }
        return count;
    }

    /**
     * Returns the status pill of a student.
     *
     * @param submission the submission
     * @param backend the backend that knows the review decisions
     * @return the pill
     */
    public static Label pill(Submission submission,
            DashboardBackend backend) {
        ResultState state = state(submission, backend);
        if (state == ResultState.NEEDS_REVIEW) {
            int open = openCount(submission, backend);
            return Parts.pill(Parts.plural(open, "thing") + " to check",
                    Tone.WARN);
        }
        return Parts.pill(words(state), tone(state));
    }

    /**
     * Returns a result state in plain words.
     *
     * @param state the state
     * @return the words
     */
    public static String words(ResultState state) {
        if (state == ResultState.COMPLETED) {
            return "Graded";
        } else if (state == ResultState.ZERO) {
            return "Graded, 0 marks";
        } else if (state == ResultState.NEEDS_REVIEW) {
            return "Needs review";
        } else if (state == ResultState.COMPILE_ERROR) {
            return "Did not compile";
        } else if (state == ResultState.TIMED_OUT) {
            return "Timed out";
        } else if (state == ResultState.COULD_NOT_GRADE) {
            return "Could not grade";
        }
        return "Waiting";
    }

    /**
     * Returns the colour of a result state.
     *
     * @param state the state
     * @return the tone
     */
    public static Tone tone(ResultState state) {
        if (state == ResultState.COMPLETED) {
            return Tone.GOOD;
        } else if (state == ResultState.NEEDS_REVIEW) {
            return Tone.WARN;
        } else if (state == ResultState.COMPILE_ERROR
                || state == ResultState.TIMED_OUT
                || state == ResultState.COULD_NOT_GRADE) {
            return Tone.BAD;
        }
        return Tone.NEUTRAL;
    }

    /**
     * Says whether a student belongs under the Failed filter.
     *
     * @param state the state
     * @return true for a student who could not be graded fully
     */
    public static boolean isFailed(ResultState state) {
        return state == ResultState.COMPILE_ERROR
                || state == ResultState.TIMED_OUT
                || state == ResultState.COULD_NOT_GRADE;
    }
}
