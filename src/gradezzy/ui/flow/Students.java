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

import java.util.ArrayList;
import java.util.List;

import javafx.scene.control.Label;
import javafx.scene.control.Tooltip;

import gradezzy.model.Anomaly;
import gradezzy.model.AnomalyType;
import gradezzy.model.QuestionResult;
import gradezzy.model.Student;
import gradezzy.model.Submission;
import gradezzy.ui.backend.DashboardBackend;
import gradezzy.ui.backend.ReviewDecision;
import gradezzy.ui.backend.ResultState;
import gradezzy.ui.backend.StatusCounts;
import gradezzy.util.TextUtils;

/**
 * Names and statuses of students.
 *
 * <p>Tier: SHOULD.
 */
public final class Students {

    /** The words shown for a 0 whose flags are all checked. */
    public static final String SETTLED_ZERO_WORDS = "Graded, 0 marks";

    /** The words shown for a chosen file that could not be opened. */
    public static final String SKIPPED_WORDS = "Not graded: zip unreadable";

    // The most questions named in a pill; more are counted instead
    private static final int MAX_NAMED_QUESTIONS = 3;

    // Separates the questions named in a pill
    private static final String LIST_SEPARATOR = ", ";

    // The words of the warning beside the name of a student whose code
    // is like another's, and the line above its tooltip's flags
    private static final String SIMILAR_WORDS = "Similar code";
    private static final String SIMILAR_TIP_START = "Similar code detected. "
            + "Read both answers before deciding:";

    // Ends a line of a tooltip
    private static final String LINE_END = "\n";

    // Not used: every method is static.
    private Students() {
    }

    /**
     * Lists a student's Similar code flags that were not dismissed with
     * Looks fine.
     *
     * @param submission the student
     * @param backend the backend that knows the review decisions
     * @return the flags, empty when there are none
     */
    public static List<Anomaly> openSimilarFlags(Submission submission,
            DashboardBackend backend) {
        List<Anomaly> flags = new ArrayList<>();
        for (Anomaly anomaly : submission.getAnomalies()) {
            boolean similar = anomaly.getType() == AnomalyType.SIMILAR_CODE
                    && backend.getDecision(anomaly)
                    != ReviewDecision.REJECTED;
            if (similar) {
                flags.add(anomaly);
            }
        }
        return flags;
    }

    /**
     * Builds the warning shown beside a student's name when similar code
     * was detected, with each flag's words in its tooltip.
     *
     * @param submission the student
     * @param backend the backend that knows the review decisions
     * @return the warning, or null when there is nothing to warn about
     */
    public static Label similarWarning(Submission submission,
            DashboardBackend backend) {
        List<Anomaly> flags = openSimilarFlags(submission, backend);
        if (flags.isEmpty()) {
            return null;
        }
        String tip = SIMILAR_TIP_START;
        for (Anomaly flag : flags) {
            tip += LINE_END + flag.getMessage();
        }
        Label warning = Parts.pill(SIMILAR_WORDS, Tone.WARN);
        warning.getStyleClass().addAll("small-text", "similar-warning");
        warning.setTooltip(new Tooltip(tip));
        warning.setAccessibleText(SIMILAR_TIP_START);
        return warning;
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
        String name = student.getFullName();
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
     * Counts the open fixes and flags of a student, the Similar code
     * flags as one, as the student panel shows them on one card.
     *
     * @param submission the submission
     * @param backend the backend that knows the review decisions
     * @return how many still need the instructor
     */
    public static int openCount(Submission submission,
            DashboardBackend backend) {
        int count = 0;
        boolean similarOpen = false;
        for (Anomaly anomaly : submission.getAnomalies()) {
            boolean similar = anomaly.getType() == AnomalyType.SIMILAR_CODE;
            if (backend.needsReview(anomaly) && similar) {
                similarOpen = true;
            } else if (backend.needsReview(anomaly)) {
                count++;
            }
        }
        // The Similar code flags are decided together, so count as one
        if (similarOpen) {
            count++;
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
        boolean settledZero = state == ResultState.ZERO
                && !StatusCounts.isUncheckedZero(submission, state, backend);
        if (ResultState.isSkippedFile(submission)) {
            return Parts.pill(SKIPPED_WORDS, Tone.BAD);
        } else if (state == ResultState.NEEDS_REVIEW) {
            int open = openCount(submission, backend);
            return Parts.pill(Parts.plural(open, "thing") + " to check",
                    Tone.WARN);
        } else if (state == ResultState.COMPILE_ERROR) {
            return Parts.pill(compileWords(submission),
                    tone(submission, backend));
        } else if (settledZero) {
            return Parts.pill(SETTLED_ZERO_WORDS, Tone.NEUTRAL);
        }
        return Parts.pill(words(state), tone(submission, backend));
    }

    /**
     * Returns the colour of a student's status. It follows the state
     * only, so Did not compile and Timed out look the same everywhere.
     *
     * @param submission the submission
     * @param backend the backend that knows the review decisions
     * @return the tone
     */
    public static Tone tone(Submission submission,
            DashboardBackend backend) {
        if (ResultState.isSkippedFile(submission)) {
            return Tone.BAD;
        }
        return tone(state(submission, backend));
    }

    /**
     * Says whether a question was compiled and failed. A missing answer
     * is not compiled either, but it has no compiler messages.
     *
     * @param result the result of one question
     * @return true for a compile error
     */
    public static boolean didNotCompile(QuestionResult result) {
        return !result.isCompiled() && result.getOutput() != null
                && !result.getOutput().isEmpty();
    }

    /**
     * Names the questions of a student that did not compile, such as
     * "Q1a, Q2b did not compile". Missing answers are not named.
     *
     * @param submission the submission
     * @return the words
     */
    public static String compileWords(Submission submission) {
        List<String> questionIds = new ArrayList<>();
        for (QuestionResult result : submission.getQuestionResults()) {
            if (didNotCompile(result) && result.getQuestion() != null) {
                questionIds.add(result.getQuestion().getId());
            }
        }

        if (questionIds.isEmpty()) {
            return words(ResultState.COMPILE_ERROR);
        } else if (questionIds.size() > MAX_NAMED_QUESTIONS) {
            return questionIds.size() + " questions did not compile";
        }
        return TextUtils.join(LIST_SEPARATOR, questionIds) + " did not compile";
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
            return "0 marks, check why";
        } else if (state == ResultState.NEEDS_REVIEW) {
            return "Needs review";
        } else if (state == ResultState.COMPILE_ERROR) {
            return "Did not compile";
        } else if (state == ResultState.TIMED_OUT) {
            return "Timed out";
        } else if (state == ResultState.COULD_NOT_GRADE) {
            return "Could not grade";
        } else if (state == ResultState.NO_SUBMISSION) {
            return "No submission";
        }
        return "Not graded yet";
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
        }
        boolean problem = state == ResultState.COMPILE_ERROR
                || state == ResultState.TIMED_OUT
                || state == ResultState.COULD_NOT_GRADE
                || state == ResultState.ZERO;
        if (problem) {
            return Tone.BAD;
        }
        return Tone.NEUTRAL;
    }
}
