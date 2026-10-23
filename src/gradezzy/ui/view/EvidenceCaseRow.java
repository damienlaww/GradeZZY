/*
 * File: EvidenceCaseRow.java
 *
 * Purpose: One row of the Cases table on the Student Evidence screen, with
 * the plain words that explain it.
 *
 * Where it fits: Listed by EvidenceCasesTab. Expected and actual values are
 * read from tester details shaped "Failed: expected X but got Y".
 */
package gradezzy.ui.view;

import java.util.ArrayList;
import java.util.List;

import gradezzy.model.Question;
import gradezzy.model.QuestionResult;
import gradezzy.model.Submission;
import gradezzy.model.TestCaseResult;
import gradezzy.ui.backend.ResultState;
import gradezzy.ui.component.Icon;
import gradezzy.ui.component.Labels;
import gradezzy.ui.component.StatusBadge;
import gradezzy.ui.component.Tone;

/**
 * One row of the Cases table.
 */
public final class EvidenceCaseRow {

    // How a failed case reports its expected value
    private static final String EXPECTED_PREFIX = "Failed: expected ";

    // How a failed case separates the expected and actual values
    private static final String ACTUAL_SEPARATOR = " but got ";

    // How a failed case starts its detail
    private static final String FAILED_PREFIX = "Failed: ";

    // How an unfinished case starts its detail
    private static final String NOT_FINISHED_PREFIX = "Not finished";

    // The question the case belongs to
    private final QuestionResult result;

    // The case, or null when no case ran
    private final TestCaseResult testCase;

    // How the case ended
    private final Outcome outcome;

    // Creates a row.
    private EvidenceCaseRow(QuestionResult result, TestCaseResult testCase,
            Outcome outcome) {
        this.result = result;
        this.testCase = testCase;
        this.outcome = outcome;
    }

    /**
     * Builds one student's rows: one per case, or a single row for a
     * question that did not compile or ran no case.
     *
     * @param shown the student
     * @return the rows, or an empty list when the student is not graded
     */
    public static List<EvidenceCaseRow> rowsOf(Submission shown) {
        List<EvidenceCaseRow> rows = new ArrayList<>();
        if (!ResultState.hasTotal(shown)) {
            return rows;
        }
        for (QuestionResult marks : shown.getQuestionResults()) {
            if (!marks.isCompiled()) {
                rows.add(new EvidenceCaseRow(marks, null,
                        Outcome.NOT_COMPILED));
            } else if (marks.getTestCaseResults().isEmpty()) {
                rows.add(new EvidenceCaseRow(marks, null, Outcome.NO_CASES));
            } else {
                for (TestCaseResult item : marks.getTestCaseResults()) {
                    rows.add(new EvidenceCaseRow(marks, item,
                            outcomeOf(item)));
                }
            }
        }
        return rows;
    }

    /**
     * Returns the question the case belongs to.
     *
     * @return the question result
     */
    public QuestionResult getResult() {
        return result;
    }

    /**
     * Returns the question id.
     *
     * @return the id, such as Q1a
     */
    public String getQuestionId() {
        return result.getQuestion().getId();
    }

    /**
     * Returns the case number as text.
     *
     * @return the number, or "None ran" when no case ran
     */
    public String getCaseText() {
        if (testCase == null) {
            return "None ran";
        }
        return String.valueOf(testCase.getTestNumber());
    }

    /**
     * Returns the question's mark.
     *
     * @return the mark, such as "2 of 3"
     */
    public String getMarksText() {
        return Labels.formatMarks(result.getScore(),
                result.getQuestion().getMaxScore());
    }

    /**
     * Says whether the case passed.
     *
     * @return true for a passed case
     */
    public boolean isPassed() {
        return outcome == Outcome.PASSED;
    }

    /**
     * Says whether the tester was stopped before the case printed a result.
     *
     * @return true for an unfinished case
     */
    public boolean isUnfinished() {
        return outcome == Outcome.NOT_FINISHED;
    }

    /**
     * Returns the outcome in words.
     *
     * @return the words, such as "Not finished"
     */
    public String getOutcomeText() {
        return outcome.label;
    }

    /**
     * Creates the badge of the outcome.
     *
     * @return the badge
     */
    public StatusBadge createBadge() {
        return new StatusBadge(outcome.label, outcome.tone, outcome.icon);
    }

    /**
     * Returns the heading of the case details.
     *
     * @return the heading, such as "Q1a case 3"
     */
    public String getTitle() {
        String questionId = getQuestionId();
        if (outcome == Outcome.NOT_COMPILED) {
            return questionId + " did not compile";
        } else if (outcome == Outcome.NO_CASES) {
            return questionId + ": no cases recorded";
        }
        return questionId + " case " + testCase.getTestNumber();
    }

    /**
     * Returns what the tester expected.
     *
     * @return the expected value or a plain description
     */
    public String getExpected() {
        String detail = getDetail();
        if (outcome == Outcome.NOT_FINISHED) {
            return "A result within the time limit";
        } else if (outcome == Outcome.NOT_COMPILED) {
            return "Code that compiles";
        } else if (outcome == Outcome.NO_CASES) {
            return "A result for each case";
        } else if (outcome == Outcome.PASSED) {
            return "The value the tester checks for";
        } else if (detail.startsWith(EXPECTED_PREFIX)
                && detail.contains(ACTUAL_SEPARATOR)) {
            return detail.substring(EXPECTED_PREFIX.length(),
                    detail.indexOf(ACTUAL_SEPARATOR));
        }
        return "A normal result";
    }

    /**
     * Returns what the code produced.
     *
     * @return the actual value or a plain description
     */
    public String getActual() {
        String detail = getDetail();
        if (outcome == Outcome.NOT_FINISHED) {
            return "No result before the tester was stopped";
        } else if (outcome == Outcome.NOT_COMPILED) {
            String first = firstLine(result.getOutput());
            if (first.isEmpty()) {
                return "The compiler stopped with an error";
            }
            return first;
        } else if (outcome == Outcome.NO_CASES) {
            return "The tester printed no case results";
        } else if (outcome == Outcome.PASSED) {
            return "The same value, so the case passed";
        } else if (detail.startsWith(EXPECTED_PREFIX)
                && detail.contains(ACTUAL_SEPARATOR)) {
            return detail.substring(detail.indexOf(ACTUAL_SEPARATOR)
                    + ACTUAL_SEPARATOR.length());
        } else if (detail.startsWith(FAILED_PREFIX)) {
            return detail.substring(FAILED_PREFIX.length());
        }
        return detail;
    }

    /**
     * Returns plain advice for the case.
     *
     * @return what the instructor can do next
     */
    public String getAdvice() {
        Question question = result.getQuestion();
        if (outcome == Outcome.PASSED) {
            return "Nothing to do. This case passed and its mark is kept.";
        } else if (outcome == Outcome.NOT_COMPILED) {
            return "Read the compiler message on the Logs tab.";
        } else if (outcome == Outcome.NOT_FINISHED || result.isTimedOut()) {
            return "Check that every loop in " + question.getFileName()
                    + " ends. Cases that passed keep their marks. Retry "
                    + question.getId() + " after reviewing the working copy.";
        } else if (outcome == Outcome.NO_CASES) {
            return "Read the tester output on the Logs tab.";
        }
        return "Compare the expected and actual values.";
    }

    // Returns what the tester printed for the case.
    private String getDetail() {
        if (testCase == null || testCase.getDetail() == null) {
            return "";
        }
        return testCase.getDetail();
    }

    // Returns how one case ended.
    private static Outcome outcomeOf(TestCaseResult item) {
        String detail = item.getDetail();
        if (item.isPassed()) {
            return Outcome.PASSED;
        } else if (detail != null && detail.startsWith(NOT_FINISHED_PREFIX)) {
            return Outcome.NOT_FINISHED;
        }
        return Outcome.FAILED;
    }

    // Returns the first line of a text.
    private static String firstLine(String text) {
        if (text == null) {
            return "";
        }
        int end = text.indexOf('\n');
        if (end < 0) {
            return text.trim();
        }
        return text.substring(0, end).trim();
    }

    /**
     * How one test case ended.
     */
    private enum Outcome {

        /** The case passed. */
        PASSED("Passed", Tone.SUCCESS, Icon.CHECK),

        /** The case printed a wrong result. */
        FAILED("Failed", Tone.ERROR, Icon.ERROR),

        /** The tester was stopped before the case printed a result. */
        NOT_FINISHED("Not finished", Tone.ERROR, Icon.TIMEOUT),

        /** The question did not compile, so no case ran. */
        NOT_COMPILED("Not compiled", Tone.ERROR, Icon.ERROR),

        /** The question compiled but no case result was recorded. */
        NO_CASES("No cases recorded", Tone.NEUTRAL, Icon.UNGRADED);

        // The words shown
        private final String label;

        // The status colour of the icon
        private final Tone tone;

        // The icon shown
        private final Icon icon;

        // Creates an outcome constant.
        Outcome(String label, Tone tone, Icon icon) {
            this.label = label;
            this.tone = tone;
            this.icon = icon;
        }
    }
}
