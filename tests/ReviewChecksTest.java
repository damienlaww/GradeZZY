/*
 * File: ReviewChecksTest.java
 *
 * Purpose: Checks two small rules of the Review step: a typed mark is
 * read strictly (2f and 1e0 are not marks), and an answer whose second
 * file is missing, such as Q3/GoldMember.java, is grouped as a compile
 * error, not as "No answer handed in".
 *
 * Where it fits: Run by hand after compiling, as tests/README.md says.
 * It uses the answer group helpers of AnswerGroupsTest and changes no
 * file. The Adjust mark sheets read marks with InputValidator.parseMark.
 */
import gradezzy.model.AnomalyType;
import gradezzy.model.Assessment;
import gradezzy.model.Submission;
import gradezzy.stats.AnswerGrouper;
import gradezzy.util.InputValidator;

/**
 * Runs the Review checks and prints how many passed. Compile:
 * javac -cp classes -d /tmp/gradezzy-tests tests/*.java; run:
 * java -cp "classes:lib/*:/tmp/gradezzy-tests" ReviewChecksTest
 */
public class ReviewChecksTest {

    // The marks typed and what they are read as
    private static final double TWO = 2.0;
    private static final double TWO_AND_A_HALF = 2.5;
    private static final double THREE = 3.0;

    // The number of checks passed so far
    private static int checks = 0;

    /**
     * Runs every check.
     *
     * @param args not used
     */
    public static void main(String[] args) {
        try {
            markChecks();
            missingFileChecks();
        } catch (AssertionError error) {
            System.out.println("FAILED: " + error.getMessage());
            System.exit(1);
        }
        System.out.println("Passed " + checks + " review checks");
    }

    // Checks which typed marks are read, and as what.
    private static void markChecks() {
        check(InputValidator.parseMark("2") == TWO, "2 is a mark");
        check(InputValidator.parseMark(" 3 ") == THREE,
                "spaces around a mark are ignored");
        check(InputValidator.parseMark("2.5") == TWO_AND_A_HALF,
                "2.5 is a mark");
        check(InputValidator.parseMark("2,5") == TWO_AND_A_HALF,
                "a decimal comma is read as a point");
        String[] notMarks = {"2f", "1e0", "2d", "NaN", "Infinity", "1.2.3",
            "0x2", "", "two"};
        for (String text : notMarks) {
            check(Double.isNaN(InputValidator.parseMark(text)),
                    "\"" + text + "\" is not a mark");
        }
    }

    // Checks a missing second file is a compile error, while a missing
    // answer file is still no answer.
    private static void missingFileChecks() {
        Assessment assessment = AnswerGroupsTest.assessment();
        Submission helper = AnswerGroupsTest.student(assessment, "helper",
                "Q3", "");
        AnswerGroupsTest.result(helper, "Q3").setCompiled(false);
        AnswerGroupsTest.flag(helper, AnomalyType.MISSING_FILE,
                "Q3/GoldMember.java is missing, so Q3 cannot compile. The "
                + "starter has it.");
        check(AnswerGrouper.COMPILE.equals(AnswerGrouper.patternOf(helper,
                AnswerGroupsTest.result(helper, "Q3"))),
                "a missing second file is a compile error");

        Submission answer = AnswerGroupsTest.student(assessment, "answer",
                "Q3", "");
        AnswerGroupsTest.result(answer, "Q3").setCompiled(false);
        AnswerGroupsTest.flag(answer, AnomalyType.MISSING_FILE,
                "Q3/Q3.java is missing.");
        check(AnswerGrouper.MISSING.equals(AnswerGrouper.patternOf(answer,
                AnswerGroupsTest.result(answer, "Q3"))),
                "a missing answer file is still no answer");
    }

    // Counts a passed check, or stops with its description.
    private static void check(boolean passed, String description) {
        if (!passed) {
            throw new AssertionError(description);
        }
        checks++;
    }
}
