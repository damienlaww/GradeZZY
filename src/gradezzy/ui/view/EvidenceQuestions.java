/*
 * File: EvidenceQuestions.java
 *
 * Purpose: Finds the question in focus on Student Evidence and the one to
 * retry.
 *
 * Where it fits: Used by StudentReportController and the Evidence tabs.
 * One tester run covers a whole question, so a retry is per question.
 */
package gradezzy.ui.view;

import java.util.List;

import gradezzy.model.GradingRun;
import gradezzy.model.Question;
import gradezzy.model.QuestionResult;
import gradezzy.model.Student;
import gradezzy.model.Submission;
import gradezzy.model.TestCaseResult;
import gradezzy.ui.backend.ResultState;

/**
 * Question choices for the Student Evidence screen.
 */
public final class EvidenceQuestions {

    // Utility class, never instantiated.
    private EvidenceQuestions() {
        // nothing to set up
    }

    /**
     * Finds a student in a run.
     *
     * @param run the run, or null
     * @param username the username, or null or empty when none is chosen
     * @return the submission, or null when the student is not in the run
     */
    public static Submission studentIn(GradingRun run, String username) {
        if (run == null || username == null || username.isEmpty()) {
            return null;
        }
        for (Submission candidate : run.getSubmissions()) {
            if (candidate.getUsername().equals(username)) {
                return candidate;
            }
        }
        return null;
    }

    /**
     * Returns the question to retry: the first that timed out, else the
     * first that did not compile, else the first with a failed case.
     *
     * @param shown the student, or null
     * @return the question id, or null if ungraded or nothing failed
     */
    public static String retryQuestionOf(Submission shown) {
        if (shown == null || !ResultState.hasTotal(shown)) {
            return null;
        }
        String timedOut = null;
        String notCompiled = null;
        String failedCase = null;
        for (QuestionResult result : shown.getQuestionResults()) {
            String id = result.getQuestion().getId();
            if (timedOut == null && result.isTimedOut()) {
                timedOut = id;
            }
            if (notCompiled == null && !result.isCompiled()) {
                notCompiled = id;
            }
            if (failedCase == null && hasFailedCase(result)) {
                failedCase = id;
            }
        }
        if (timedOut != null) {
            return timedOut;
        } else if (notCompiled != null) {
            return notCompiled;
        }
        return failedCase;
    }

    /**
     * Returns the remembered question, else the one to retry, else the first.
     *
     * @param run the run the student is in, or null
     * @param remembered the question chosen last time, or an empty string
     * @param shown the student
     * @return the question id, or an empty string when there is none
     */
    public static String questionInFocus(GradingRun run, String remembered,
            Submission shown) {
        if (run == null) {
            return "";
        }
        if (remembered != null && run.getAssessment().findQuestion(
                remembered) != null) {
            return remembered;
        }
        String retry = retryQuestionOf(shown);
        if (retry != null) {
            return retry;
        }
        List<Question> questions = run.getAssessment().getQuestions();
        if (questions.isEmpty()) {
            return "";
        }
        return questions.get(0).getId();
    }

    /**
     * Returns the path of a question's file, such as Q1/Q1a.java.
     *
     * @param run the run, or null
     * @param questionId the question
     * @return the path relative to the working copy, or an empty string
     */
    public static String questionFile(GradingRun run, String questionId) {
        if (run == null) {
            return "";
        }
        Question question = run.getAssessment().findQuestion(questionId);
        if (question == null) {
            return "";
        }
        return question.getFolderName() + "/" + question.getFileName();
    }

    /**
     * Finds the result of one question.
     *
     * @param shown the student
     * @param questionId the question
     * @return the result, or null when the question has none
     */
    public static QuestionResult find(Submission shown, String questionId) {
        for (QuestionResult result : shown.getQuestionResults()) {
            if (result.getQuestion().getId().equals(questionId)) {
                return result;
            }
        }
        return null;
    }

    /**
     * Returns the mark of one question.
     *
     * @param shown the student
     * @param questionId the question
     * @return the mark, or 0 when the question has no result
     */
    public static double scoreOf(Submission shown, String questionId) {
        QuestionResult result = find(shown, questionId);
        if (result == null) {
            return 0.0;
        }
        return result.getScore();
    }

    /**
     * Returns the student's full name.
     *
     * @param shown the student
     * @return the name, or a note when the student is not on the roster
     */
    public static String fullName(Submission shown) {
        Student student = shown.getStudent();
        if (student == null) {
            return "Not on the roster";
        }
        return student.getFirstName() + " " + student.getLastName();
    }

    // Says whether a question has a case that did not pass.
    private static boolean hasFailedCase(QuestionResult result) {
        for (TestCaseResult testCase : result.getTestCaseResults()) {
            if (!testCase.isPassed()) {
                return true;
            }
        }
        return false;
    }
}
