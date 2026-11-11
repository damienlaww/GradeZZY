/*
 * File: TesterStrengthSummaryTest.java
 *
 * Purpose: Regression checks for the tester strength line on Set up:
 * when model answers do not compile, it says which testers could not be
 * checked and the names javac could not find, instead of only counting
 * the testers that were checked.
 *
 * Where it fits: Run by hand after compiling, as tests/README.md says.
 * Nothing is compiled or run; the results are built in memory.
 */
import java.util.ArrayList;
import java.util.List;

import gradezzy.exec.ProcessResult;
import gradezzy.modelanswer.Mutant;
import gradezzy.modelanswer.MutantOutcome;
import gradezzy.modelanswer.QuestionStrength;
import gradezzy.modelanswer.TesterStrengthChecker;

/**
 * Runs the tester strength summary checks and prints how many passed.
 */
public class TesterStrengthSummaryTest {

    // What javac prints when the model answer of Q2b uses classes the
    // starter files do not have
    private static final String JAVAC_ERRORS = "Q2b.java:12: error: "
            + "cannot find symbol\n"
            + "        throw new InvalidOrderException();\n"
            + "                  ^\n"
            + "  symbol:   class InvalidOrderException\n"
            + "  location: class Shop\n"
            + "Q2b.java:20: error: cannot find symbol\n"
            + "  symbol:   class Member\n"
            + "  location: class Shop\n"
            + "Q2b.java:24: error: cannot find symbol\n"
            + "  symbol:   class Member\n"
            + "3 errors\n";

    // The exit code of javac when the files did not compile
    private static final int NOT_COMPILED = 1;

    // The number of checks passed so far
    private static int checks = 0;

    /**
     * Runs every check.
     *
     * @param args not used
     */
    public static void main(String[] args) {
        checkMissingNames();
        checkSummaries();
        System.out.println("Passed " + checks + " tester strength summary "
                + "checks.");
    }

    // The names javac could not find are read from its symbol lines,
    // each once.
    private static void checkMissingNames() {
        ProcessResult compiled = new ProcessResult(NOT_COMPILED, "",
                JAVAC_ERRORS, false, 0);
        List<String> names = TesterStrengthChecker.findMissingNames(
                compiled);
        int expectedNames = 2;
        check(names.size() == expectedNames,
                "two names are missing, Member once");
        boolean namesFound = names.get(0).equals("InvalidOrderException")
                && names.get(1).equals("Member");
        check(namesFound,
                "the missing names are InvalidOrderException and Member");
        ProcessResult clean = new ProcessResult(0, "", "", false, 0);
        check(TesterStrengthChecker.findMissingNames(clean).isEmpty(),
                "a clean compile has no missing names");
    }

    // The Set up line names the model answers that do not compile.
    private static void checkSummaries() {
        List<QuestionStrength> results = new ArrayList<>();
        results.add(withOutcome("Q1", MutantOutcome.CAUGHT));
        results.add(withOutcome("Q2a", MutantOutcome.SURVIVED));
        results.add(notCompiling("Q2b", "InvalidOrderException"));
        results.add(notCompiling("Q3", "Member"));
        results.add(withOutcome("Q4", MutantOutcome.CAUGHT));
        results.add(withOutcome("Q5", MutantOutcome.CAUGHT));
        String text = QuestionStrength.summariseAll(results);
        check(text.equals("2 of 6 testers could not be checked: the model "
                + "answer for Q2b, Q3 does not compile (missing "
                + "InvalidOrderException, Member); 1 of 4 testers miss "
                + "some broken copies of the model answer"),
                "the line names Q2b and Q3 and what is missing (it says \""
                + text + "\")");

        List<QuestionStrength> broken = new ArrayList<>();
        broken.add(notCompiling("Q2b", "Member"));
        String onlyBroken = QuestionStrength.summariseAll(broken);
        check(onlyBroken.equals("1 of 1 tester could not be checked: the "
                + "model answer for Q2b does not compile (missing Member)"),
                "a single broken model answer is named (it says \""
                + onlyBroken + "\")");

        List<QuestionStrength> strong = new ArrayList<>();
        strong.add(withOutcome("Q1", MutantOutcome.CAUGHT));
        String allCaught = QuestionStrength.summariseAll(strong);
        check(allCaught.equals("every tester checked caught every broken "
                + "copy"), "a strong tester is praised (it says \""
                + allCaught + "\")");
        check(QuestionStrength.summariseAll(new ArrayList<>()).equals(
                "no tester could be checked"), "no results say so");
    }

    // Builds a question whose tester was tried on one broken copy.
    private static QuestionStrength withOutcome(String id,
            MutantOutcome outcome) {
        QuestionStrength result = new QuestionStrength(id);
        Mutant mutant = new Mutant(id + ".java", 1, "", "", "");
        mutant.setOutcome(outcome);
        result.getMutants().add(mutant);
        return result;
    }

    // Builds a question whose model answer does not compile.
    private static QuestionStrength notCompiling(String id, String missing) {
        QuestionStrength result = new QuestionStrength(id);
        result.setProblem("The model answer does not compile.");
        List<String> names = new ArrayList<>();
        names.add(missing);
        result.markModelNotCompiling(names);
        return result;
    }

    // Counts a check that passed, or stops with its description.
    private static void check(boolean passed, String description) {
        if (!passed) {
            throw new AssertionError("Failed: " + description);
        }
        checks++;
    }
}
