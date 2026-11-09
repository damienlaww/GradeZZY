/*
 * File: QuestionStrength.java
 *
 * Purpose: How strong one question's tester is: how many broken copies
 * of the model answer it caught, and which ones still got full marks.
 *
 * Where it fits: Made by TesterStrengthChecker, one per question, and
 * printed by the console and written to the tester strength report.
 */
package gradezzy.modelanswer;

import java.util.ArrayList;
import java.util.List;

/**
 * How strong one question's tester is.
 *
 * <p>Tier: COULD. Owner: Jing Yuan.
 */
public class QuestionStrength {

    // Turns a fraction into a percentage
    private static final int PERCENT = 100;

    // The question, such as Q2b
    private final String questionId;

    // The question's maximum mark, read from its tester
    private final double maxScore;

    // The broken copies tried, with what the tester did with each
    private final List<Mutant> mutants = new ArrayList<>();

    // Why the question could not be checked, or "" when it was
    private String problem = "";

    // The marks the tester gave the model answer itself
    private double modelScore;

    /**
     * Creates the result of one question, with no copies tried yet.
     *
     * @param questionId the question, such as Q2b
     * @param maxScore the question's maximum mark
     */
    public QuestionStrength(String questionId, double maxScore) {
        this.questionId = questionId;
        this.maxScore = maxScore;
    }

    /**
     * Returns the question.
     *
     * @return the question id, such as Q2b
     */
    public String getQuestionId() {
        return questionId;
    }

    /**
     * Returns the question's maximum mark.
     *
     * @return the maximum mark
     */
    public double getMaxScore() {
        return maxScore;
    }

    /**
     * Returns the broken copies tried. The list can be added to.
     *
     * @return the copies
     */
    public List<Mutant> getMutants() {
        return mutants;
    }

    /**
     * Returns why the question could not be checked.
     *
     * @return the reason, or "" when it was checked
     */
    public String getProblem() {
        return problem;
    }

    /**
     * Records why the question could not be checked.
     *
     * @param problem the reason
     */
    public void setProblem(String problem) {
        this.problem = problem;
    }

    /**
     * Returns the marks the tester gave the model answer itself.
     *
     * @return the marks
     */
    public double getModelScore() {
        return modelScore;
    }

    /**
     * Records the marks the tester gave the model answer itself.
     *
     * @param modelScore the marks
     */
    public void setModelScore(double modelScore) {
        this.modelScore = modelScore;
    }

    /**
     * Counts the copies that compiled and were run to an outcome: the
     * ones the tester was really tried on.
     *
     * @return the number of copies tried
     */
    public int countTried() {
        return countOutcome(MutantOutcome.CAUGHT)
                + countOutcome(MutantOutcome.TIMED_OUT)
                + countOutcome(MutantOutcome.SURVIVED);
    }

    /**
     * Counts the copies the tester still gave full marks.
     *
     * @return the number of copies that got full marks
     */
    public int countSurvived() {
        return countOutcome(MutantOutcome.SURVIVED);
    }

    /**
     * Returns the share of tried copies the tester caught, as a whole
     * percentage: the mutation score.
     *
     * @return the percentage, or 0 when no copy was tried
     */
    public long getPercentCaught() {
        int tried = countTried();
        if (tried == 0) {
            return 0;
        }
        int caught = tried - countSurvived();
        double share = (double) caught / tried;
        return Math.round(share * PERCENT);
    }

    /**
     * Returns the copies the tester still gave full marks.
     *
     * @return the copies, in the order of the files
     */
    public List<Mutant> getSurvivors() {
        List<Mutant> survivors = new ArrayList<>();
        for (Mutant mutant : mutants) {
            if (mutant.getOutcome() == MutantOutcome.SURVIVED) {
                survivors.add(mutant);
            }
        }
        return survivors;
    }

    /**
     * Sums up the question in one sentence, such as "Q2b: the tester
     * gave full marks to 7 of 40 broken copies (caught 83%)."
     *
     * @return the sentence
     */
    public String summarise() {
        if (!problem.isEmpty()) {
            return questionId + ": not checked. " + problem;
        }

        int tried = countTried();
        if (tried == 0) {
            return questionId + ": no broken copy could be made and run.";
        }

        return questionId + ": the tester gave full marks to "
                + countSurvived() + " of " + tried + " broken copies "
                + "(caught " + getPercentCaught() + "%).";
    }

    // Counts the copies with one outcome.
    private int countOutcome(MutantOutcome outcome) {
        int count = 0;
        for (Mutant mutant : mutants) {
            if (mutant.getOutcome() == outcome) {
                count++;
            }
        }
        return count;
    }
}
