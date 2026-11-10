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

    // Follows a share printed as a percentage
    private static final String PERCENT_SIGN = "%";

    // The question, such as Q2b
    private final String questionId;

    // The broken copies tried, with what the tester did with each
    private final List<Mutant> mutants = new ArrayList<>();

    // Why the question could not be checked, or "" when it was
    private String problem = "";

    /**
     * Creates the result of one question, with no copies tried yet.
     *
     * <p>Tier: COULD.
     *
     * @param questionId the question, such as Q2b
     */
    public QuestionStrength(String questionId) {
        this.questionId = questionId;
    }

    /**
     * Returns the broken copies tried. The list can be added to.
     *
     * <p>Tier: COULD.
     *
     * @return the copies
     */
    public List<Mutant> getMutants() {
        return mutants;
    }

    /**
     * Records why the question could not be checked.
     *
     * <p>Tier: COULD.
     *
     * @param problem the reason
     */
    public void setProblem(String problem) {
        this.problem = problem;
    }

    /**
     * Counts the copies that compiled and were run to an outcome: the
     * ones the tester was really tried on.
     *
     * <p>Tier: COULD.
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
     * <p>Tier: COULD.
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
     * <p>Tier: COULD.
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
        double percent = share * PERCENT;
        return Math.round(percent);
    }

    /**
     * Returns the copies the tester still gave full marks.
     *
     * <p>Tier: COULD.
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
     * gave full marks to 7 of 40 broken copies (caught 83%)." Copies
     * caught only because they ran past the time limit are counted
     * apart.
     *
     * <p>Tier: COULD.
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

        String caught = "caught " + getPercentCaught() + PERCENT_SIGN;
        int timedOut = countOutcome(MutantOutcome.TIMED_OUT);
        if (timedOut > 0) {
            // Shown apart, since a slow computer can stop a copy that
            // would have survived
            caught = caught + ", " + timedOut + " of them only by the "
                    + "time limit";
        }
        return questionId + ": the tester gave full marks to "
                + countSurvived() + " of " + tried + " broken copies ("
                + caught + ").";
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
