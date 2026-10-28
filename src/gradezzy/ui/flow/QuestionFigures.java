/*
 * File: QuestionFigures.java
 *
 * Purpose: Works out the average mark of each question of a run and
 * which question was the hardest (the lowest average as a share of its
 * maximum).
 *
 * Where it fits: The Statistics tab uses it for the "Each question" card
 * and the "Hardest question" column when sections are compared. It only
 * counts; it never changes a mark.
 */
package gradezzy.ui.flow;

import java.util.List;

import gradezzy.model.Question;
import gradezzy.model.QuestionResult;
import gradezzy.model.Submission;

/**
 * Averages of each question of a run.
 */
public final class QuestionFigures {

    /** Returned by hardest when there is no question to compare. */
    public static final int NONE = -1;

    // Not used: every method is static.
    private QuestionFigures() {
    }

    /**
     * Works out the average mark of each question over the students
     * that have results, the same students MarkStats counts. A student
     * with no result for a question counts as 0 for it.
     *
     * @param submissions the students of a run
     * @param questions the questions of the assessment
     * @return the average of each question, in the order of questions
     */
    public static double[] averages(List<Submission> submissions,
            List<Question> questions) {
        double[] sums = new double[questions.size()];
        int counted = 0;
        for (Submission submission : submissions) {
            if (!submission.getQuestionResults().isEmpty()) {
                counted++;
                addScores(submission, questions, sums);
            }
        }
        if (counted == 0) {
            return sums;
        }
        for (int index = 0; index < sums.length; index++) {
            sums[index] = sums[index] / counted;
        }
        return sums;
    }

    /**
     * Finds the question with the lowest average as a share of its
     * maximum. On a tie the first one is kept.
     *
     * @param averages the averages from averages()
     * @param questions the questions, in the same order
     * @return the index of the hardest question, or NONE
     */
    public static int hardest(double[] averages, List<Question> questions) {
        int found = NONE;
        double lowest = Double.MAX_VALUE;
        for (int index = 0; index < questions.size(); index++) {
            double max = questions.get(index).getMaxScore();
            if (max > 0 && averages[index] / max < lowest) {
                lowest = averages[index] / max;
                found = index;
            }
        }
        return found;
    }

    /**
     * Returns the id of the hardest question of some students.
     *
     * @param submissions the students of a run
     * @param questions the questions of the assessment
     * @return the question id, or "-" when there is none
     */
    public static String hardestId(List<Submission> submissions,
            List<Question> questions) {
        int index = hardest(averages(submissions, questions), questions);
        if (index == NONE) {
            return "-";
        }
        return questions.get(index).getId();
    }

    // Adds a student's score of each question to the running sums.
    private static void addScores(Submission submission,
            List<Question> questions, double[] sums) {
        for (QuestionResult result : submission.getQuestionResults()) {
            int index = indexOf(questions, result.getQuestion());
            if (index != NONE) {
                sums[index] += result.getScore();
            }
        }
    }

    // Returns where a question is in the list, matched by id, or NONE.
    private static int indexOf(List<Question> questions, Question wanted) {
        if (wanted == null) {
            return NONE;
        }
        for (int index = 0; index < questions.size(); index++) {
            if (questions.get(index).getId().equals(wanted.getId())) {
                return index;
            }
        }
        return NONE;
    }
}
