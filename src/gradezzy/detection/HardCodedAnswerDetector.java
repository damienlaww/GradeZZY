/*
 * File: HardCodedAnswerDetector.java
 *
 * Purpose: Flags answers that look hard-coded to the sample cases.
 *
 * Where it fits: Never changes a mark: it adds a HARD_CODED_ANSWER anomaly for
 * the instructor to review.
 */
package gradezzy.detection;

import java.util.ArrayList;
import java.util.List;
import java.util.UUID;

import gradezzy.model.Anomaly;
import gradezzy.model.AnomalyType;
import gradezzy.model.Assessment;
import gradezzy.model.Question;
import gradezzy.model.Severity;
import gradezzy.model.Submission;

/**
 * Flags answers that look hard-coded to the sample cases.
 */
public class HardCodedAnswerDetector implements Detector {

    /**
     * The name shown in the anomaly log.
     */
    public static final String NAME = "Hard-coded answer";

    // The fewest literal branches in one method that raise a flag
    private static final int MIN_LITERAL_BRANCHES = 3;

    // Values honest code compares with all the time, so they do not count
    private static final String[] COMMON_VALUES = {"0", "1", "-1", "\"\""};

    // The text in front of the id of every flag a detector raises
    private static final String ID_PREFIX = "detection-";

    // What the instructor can do about a hard-coded answer
    private static final String SUGGESTED_ACTION =
            "Read the method and compare it with the sample cases. The mark "
            + "has not been changed.";

    // The keywords that start a branch
    private static final String IF_KEYWORD = "if";
    private static final String CASE_KEYWORD = "case";

    // The two ways to compare for equality
    private static final String EQUALS_OPERATOR = "==";
    private static final String EQUALS_METHOD = ".equals";

    // The brackets around a condition or an argument
    private static final char OPEN_PAREN = '(';
    private static final char CLOSE_PAREN = ')';

    // The sign in front of a negative number
    private static final char MINUS = '-';

    // The point inside a decimal number such as 3.5
    private static final char DECIMAL_POINT = '.';

    // Returned when no comparison starts at a position
    private static final int NOT_FOUND = -1;

    /**
     * Creates a new hard coded answer detector.
     */
    public HardCodedAnswerDetector() {
        // nothing to set up
    }

    /**
     * Returns the name of this detector.
     *
     * @return the name of this detector
     */
    @Override
    public String getName() {
        return NAME;
    }

    /**
     * Flags answers that look hard-coded to the sample cases.
     *
     * @param submission the submission
     * @param assessment the assessment
     * @return the flags raised, empty if there are none
     */
    @Override
    public List<Anomaly> detect(Submission submission, Assessment assessment) {
        List<Anomaly> flags = new ArrayList<>();

        for (Question question : assessment.getQuestions()) {
            String studentText = JavaSourceText.readStudentFile(submission,
                    assessment, question);

            if (studentText != null) {
                checkMethods(flags, submission, question, studentText);
            }
        }

        return flags;
    }

    // Flags each answer method with too many literal branches.
    private void checkMethods(List<Anomaly> flags, Submission submission,
            Question question, String studentText) {
        String code = JavaSourceText.removeComments(studentText);

        for (String methodName : JavaSourceText.findAnswerMethodNames(code)) {
            String body = JavaSourceText.findMethodBody(code, methodName);
            int literalBranches = countLiteralBranches(
                    JavaSourceText.blankLiterals(body));

            if (literalBranches >= MIN_LITERAL_BRANCHES) {
                flags.add(createFlag(submission,
                        JavaSourceText.describeFile(question) + ": "
                        + methodName + " has " + literalBranches + " if or "
                        + "case branches that compare a value with a fixed "
                        + "literal, so it may be hard-coded to the sample "
                        + "cases."));
            }
        }
    }

    // Counts the if and case branches that compare a value with a literal.
    private int countLiteralBranches(String body) {
        int count = 0;

        for (int i = 0; i < body.length(); i++) {
            if (isKeywordAt(body, i, IF_KEYWORD)
                    && comparesWithLiteral(findCondition(body, i))) {
                count++;
            } else if (isKeywordAt(body, i, CASE_KEYWORD)
                    && isSampleValue(readLiteralAt(body,
                    i + CASE_KEYWORD.length()))) {
                count++;
            }
        }

        return count;
    }

    // Says whether the keyword starts at index as a whole word.
    private boolean isKeywordAt(String text, int index, String keyword) {
        int after = index + keyword.length();
        boolean startsWord = index == 0
                || !Character.isJavaIdentifierPart(text.charAt(index - 1));
        boolean endsWord = after >= text.length()
                || !Character.isJavaIdentifierPart(text.charAt(after));

        return text.startsWith(keyword, index) && startsWord && endsWord;
    }

    // Returns the condition in the brackets after the if keyword.
    private String findCondition(String body, int ifIndex) {
        int openIndex = body.indexOf(OPEN_PAREN, ifIndex);

        if (openIndex < 0) {
            return "";
        }

        int closeIndex = JavaSourceText.findClosingBracket(body, openIndex,
                OPEN_PAREN, CLOSE_PAREN);

        return body.substring(openIndex + 1, closeIndex);
    }

    // Says whether a condition compares something with a literal.
    private boolean comparesWithLiteral(String condition) {
        for (int i = 0; i < condition.length(); i++) {
            int rightStart = findRightOperand(condition, i);

            if (rightStart != NOT_FOUND
                    && (isSampleValue(readLiteralBefore(condition, i))
                    || isSampleValue(readLiteralAt(condition, rightStart)))) {
                return true;
            }
        }

        return false;
    }

    // Returns where the value after == or .equals( starts, or NOT_FOUND.
    private int findRightOperand(String condition, int index) {
        if (condition.startsWith(EQUALS_OPERATOR, index)) {
            return index + EQUALS_OPERATOR.length();
        } else if (condition.startsWith(EQUALS_METHOD, index)) {
            return condition.indexOf(OPEN_PAREN, index) + 1;
        }

        return NOT_FOUND;
    }

    // Returns the literal that ends just before index, or "" if none.
    private String readLiteralBefore(String text, int index) {
        int end = index;

        while (end > 0 && Character.isWhitespace(text.charAt(end - 1))) {
            end--;
        }

        if (end > 0 && JavaSourceText.isQuote(text.charAt(end - 1))) {
            int start = text.lastIndexOf(text.charAt(end - 1), end - 2);

            if (start < 0) {
                return "";
            }
            return text.substring(start, end);
        }

        int start = end;

        while (start > 0
                && Character.isJavaIdentifierPart(text.charAt(start - 1))) {
            start--;
        }

        if (start < end && Character.isDigit(text.charAt(start))) {
            return text.substring(start, end);
        }
        return "";
    }

    // Returns the literal that starts at index, or "" if none.
    private String readLiteralAt(String text, int index) {
        int start = index;

        while (start < text.length()
                && Character.isWhitespace(text.charAt(start))) {
            start++;
        }

        int end = start;

        if (end < text.length() && text.charAt(end) == MINUS) {
            end++;
        }

        if (end < text.length() && JavaSourceText.isQuote(text.charAt(end))) {
            int close = text.indexOf(text.charAt(end), end + 1);

            if (close < 0) {
                return "";
            }
            return text.substring(start, close + 1);
        }

        int digitsStart = end;

        while (end < text.length()
                && (Character.isLetterOrDigit(text.charAt(end))
                || text.charAt(end) == DECIMAL_POINT)) {
            end++;
        }

        if (end > digitsStart && Character.isDigit(text.charAt(digitsStart))) {
            return text.substring(start, end);
        }
        return "";
    }

    // Says whether a literal is a sample value, not a common one like 0.
    private boolean isSampleValue(String literal) {
        if (literal.isEmpty()) {
            return false;
        }

        for (String commonValue : COMMON_VALUES) {
            if (literal.equals(commonValue)) {
                return false;
            }
        }
        return true;
    }

    // Builds one flag for the instructor. It never changes a score.
    private Anomaly createFlag(Submission submission, String message) {
        String id = ID_PREFIX + UUID.randomUUID();

        return new Anomaly(id, AnomalyType.HARD_CODED_ANSWER,
                Severity.WARNING, submission.getUsername(), message,
                SUGGESTED_ACTION);
    }
}
