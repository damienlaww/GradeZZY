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

import gradezzy.util.TextUtils;

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

    // Comes between the names in a list
    private static final String LIST_SEPARATOR = ", ";

    // Comes between the two parts of the summary of every question
    private static final String SUMMARY_SEPARATOR = "; ";

    // The question, such as Q2b
    private final String questionId;

    // The broken copies tried, with what the tester did with each
    private final List<Mutant> mutants = new ArrayList<>();

    // Why the question could not be checked, or "" when it was
    private String problem = "";

    // True when the model answer did not compile with its tester
    private boolean modelNotCompiling = false;

    // The names javac could not find in the model answer, such as
    // InvalidOrderException
    private final List<String> missingNames = new ArrayList<>();

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
     * Returns the question, such as Q2b.
     *
     * @return the question's id
     */
    public String getQuestionId() {
        return questionId;
    }

    /**
     * Notes that the model answer does not compile with its tester, and
     * the names javac could not find in it.
     *
     * @param missing the names not found, such as InvalidOrderException;
     *        may be empty
     */
    public void markModelNotCompiling(List<String> missing) {
        modelNotCompiling = true;
        missingNames.addAll(missing);
    }

    /**
     * Says whether the model answer does not compile with its tester.
     *
     * @return true when it does not compile
     */
    public boolean isModelNotCompiling() {
        return modelNotCompiling;
    }

    /**
     * Returns the names javac could not find in the model answer.
     *
     * @return the names, possibly none
     */
    public List<String> getMissingNames() {
        return missingNames;
    }

    /**
     * Sums up every question's result for the Set up row, such as "2 of
     * 6 testers could not be checked: the model answer for Q2b, Q3 does
     * not compile (missing InvalidOrderException, Member); 1 of 4
     * testers miss some broken copies of the model answer".
     *
     * @param results the result of each question
     * @return the words, with no full stop
     */
    public static String summariseAll(List<QuestionStrength> results) {
        int weak = 0;
        int checked = 0;
        List<String> notCompiling = new ArrayList<>();
        List<String> missing = new ArrayList<>();
        for (QuestionStrength result : results) {
            if (result.countTried() > 0) {
                checked++;
            }
            if (result.countSurvived() > 0) {
                weak++;
            }
            if (result.isModelNotCompiling()) {
                notCompiling.add(result.getQuestionId());
                addNew(missing, result.getMissingNames());
            }
        }
        int unchecked = results.size() - checked;
        if (checked == 0 && notCompiling.isEmpty()) {
            return "no tester could be checked";
        }
        String text = "";
        if (unchecked > 0) {
            text = describeUnchecked(unchecked, results.size(),
                    notCompiling, missing);
        }
        if (checked == 0) {
            return text;
        }
        if (!text.isEmpty()) {
            text = text + SUMMARY_SEPARATOR;
        }
        if (weak == 0) {
            return text + "every tester checked caught every broken copy";
        }
        return text + weak + " of " + countTesters(checked)
                + " miss some broken copies of the model answer";
    }

    // Says how many testers could not be checked, and which model
    // answers do not compile and why.
    private static String describeUnchecked(int unchecked, int total,
            List<String> notCompiling, List<String> missing) {
        String text = unchecked + " of " + countTesters(total)
                + " could not be checked";
        if (notCompiling.isEmpty()) {
            return text;
        }
        text = text + ": the model answer for "
                + TextUtils.join(LIST_SEPARATOR, notCompiling)
                + " does not compile";
        if (!missing.isEmpty()) {
            text = text + " (missing "
                    + TextUtils.join(LIST_SEPARATOR, missing) + ")";
        }
        return text;
    }

    // Says a number of testers, such as "1 tester" or "6 testers".
    private static String countTesters(int count) {
        if (count == 1) {
            return "1 tester";
        }
        return count + " testers";
    }

    // Adds the names not in a list yet to it.
    private static void addNew(List<String> list, List<String> names) {
        for (String name : names) {
            if (!list.contains(name)) {
                list.add(name);
            }
        }
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
