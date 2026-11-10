/*
 * File: QuestionRegrade.java
 *
 * Purpose: Helpers for grading one question of a student again: the
 * copy that is graded, and swapping its result and problems into the
 * student.
 *
 * Where it fits: GradingService.regradeQuestion uses them. They read
 * and change only the submissions they are given.
 */
package gradezzy.service;

import java.util.ArrayList;
import java.util.List;

import gradezzy.grading.CompileStage;
import gradezzy.grading.ExecuteStage;
import gradezzy.model.Anomaly;
import gradezzy.model.Question;
import gradezzy.model.QuestionResult;
import gradezzy.model.Submission;
import gradezzy.modelanswer.RandomInputStage;
import gradezzy.testcase.TesterFile;
import gradezzy.testcase.TesterInstaller;

/**
 * Helpers for grading one question of a student again.
 *
 * <p>Tier: SHOULD. Owner: Jing Yuan.
 */
final class QuestionRegrade {

    // Start of the id of the problem added when grading one question
    // again fails
    static final String REGRADE_FAILED_ID_PREFIX = "regrade-";

    // Not used: every method is static.
    private QuestionRegrade() {
    }

    // Makes the copy of a student that grades one question again: the
    // same username, upload, working copy and roster entry, with no
    // results and no problems yet.
    static Submission copyForQuestion(Submission submission) {
        Submission copy = new Submission(submission.getUsername(),
                submission.getSection(), submission.getOriginalPath());
        copy.setWorkingCopyPath(submission.getWorkingCopyPath());
        copy.setStudent(submission.getStudent());
        return copy;
    }

    // Swaps the problems the compiler, the tester and the random inputs
    // check gave a question for the ones the new run gave it. Every other
    // problem stays.
    static void replaceQuestionProblems(Submission submission,
            Submission copy, Question question, TesterFile tester) {
        List<Anomaly> earlier = new ArrayList<>();
        for (Anomaly anomaly : submission.getAnomalies()) {
            if (isQuestionRunProblem(anomaly, question, tester)) {
                earlier.add(anomaly);
            }
        }
        submission.getAnomalies().removeAll(earlier);
        for (Anomaly anomaly : copy.getAnomalies()) {
            submission.addAnomaly(anomaly);
        }
    }

    // Says whether a problem came from compiling or running a question's
    // tester or its random inputs: its id is one the tester, compile,
    // execute or random inputs steps give, or an earlier failed try of
    // this question, and its words name the question or its tester.
    private static boolean isQuestionRunProblem(Anomaly anomaly,
            Question question, TesterFile tester) {
        String id = anomaly.getId();
        boolean fromRun = id.startsWith(TesterInstaller.ID_PREFIX)
                || id.startsWith(CompileStage.ID_PREFIX)
                || id.startsWith(ExecuteStage.ID_PREFIX)
                || id.startsWith(RandomInputStage.ID_PREFIX)
                || id.startsWith(REGRADE_FAILED_ID_PREFIX);
        if (!fromRun) {
            return false;
        }
        String message = anomaly.getMessage();
        return containsWord(message, question.getId())
                || containsWord(message, tester.getClassName());
    }

    // Says whether a text holds a word on its own, so Q1 is not found in
    // Q1a and Q2b is found in "Q2b.java".
    private static boolean containsWord(String text, String word) {
        int index = text.indexOf(word);
        while (index >= 0) {
            int end = index + word.length();
            int before = index - 1;
            boolean startsWord = index == 0
                    || !Character.isLetterOrDigit(text.charAt(before));
            boolean endsWord = end == text.length()
                    || !Character.isLetterOrDigit(text.charAt(end));
            if (startsWord && endsWord) {
                return true;
            }
            int next = index + 1;
            index = text.indexOf(word, next);
        }
        return false;
    }

    // Puts a question's new result where its old one was, so the
    // questions keep their order.
    static void replaceResult(Submission submission,
            QuestionResult oldResult, QuestionResult newResult) {
        List<QuestionResult> results = submission.getQuestionResults();
        int place = results.indexOf(oldResult);
        if (place < 0) {
            results.add(newResult);
            return;
        }
        results.set(place, newResult);
    }
}
