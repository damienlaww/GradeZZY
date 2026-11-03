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
    private static final String[] COMMON_VALUES = {"0", "1", "-1"};

    // The text in front of the id of every flag a detector raises
    private static final String ID_PREFIX = "detection-";

    // What the instructor can do about a hard-coded answer
    private static final String SUGGESTED_ACTION =
            "Read the method and compare it with the sample cases. The mark "
            + "has not been changed.";

    // The keywords that start a branch
    private static final String IF_KEYWORD = "if";
    private static final String CASE_KEYWORD = "case";

    // The ways to test a value against a literal
    private static final String EQUALS_OPERATOR = "==";
    private static final String EQUALS_METHOD = ".equals";
    private static final String CONTAINS_METHOD = ".contains";

    // The signs of a calculation, such as the % in i % 2
    private static final String ARITHMETIC_SIGNS = "+-*/%";

    // The arrow after a label such as case 3 ->, which is not a minus sign
    private static final String ARROW = "->";

    // The brackets around a condition or an argument
    private static final char OPEN_PAREN = '(';
    private static final char CLOSE_PAREN = ')';

    // The bracket that ends an array index, as in a[i] - 5
    private static final char CLOSE_BRACKET = ']';

    // The sign in front of a negative number
    private static final char MINUS = '-';

    // Starts an escape such as \n inside a literal
    private static final char BACKSLASH = '\\';

    // The length of a simple escape: the backslash and one character
    private static final int ESCAPE_LENGTH = 2;

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

    // Flags each answer method, overloads too, with too many literal branches.
    private void checkMethods(List<Anomaly> flags, Submission submission,
            Question question, String studentText) {
        String code = JavaSourceText.removeComments(studentText);

        for (String methodName : JavaSourceText.findAnswerMethodNames(code)) {
            for (String body : JavaSourceText.findMethodBodies(code,
                    methodName)) {
                int literalBranches = countLiteralBranches(body);

                if (literalBranches >= MIN_LITERAL_BRANCHES) {
                    flags.add(createFlag(submission,
                            JavaSourceText.describeFile(question) + ": "
                            + methodName + " has " + literalBranches
                            + " if or case branches that compare a value "
                            + "with a fixed literal, so it may be "
                            + "hard-coded to the sample cases."));
                }
            }
        }
    }

    // Counts the if and case branches that compare a value with a literal.
    private int countLiteralBranches(String body) {
        // Keywords and brackets inside a literal are hidden in this copy
        String blanked = JavaSourceText.blankLiterals(body);
        int count = 0;

        for (int i = 0; i < blanked.length(); i++) {
            if (isKeywordAt(blanked, i, IF_KEYWORD)
                    && comparesWithLiteral(findCondition(body, blanked, i))) {
                count++;
            } else if (isKeywordAt(blanked, i, CASE_KEYWORD)
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
    private String findCondition(String body, String blanked, int ifIndex) {
        int openIndex = blanked.indexOf(OPEN_PAREN, ifIndex);

        if (openIndex < 0) {
            return "";
        }

        int closeIndex = JavaSourceText.findClosingBracket(blanked, openIndex,
                OPEN_PAREN, CLOSE_PAREN);

        return body.substring(openIndex + 1, closeIndex);
    }

    // Says whether a condition compares something with a literal.
    private boolean comparesWithLiteral(String condition) {
        String blanked = JavaSourceText.blankLiterals(condition);

        for (int i = 0; i < blanked.length(); i++) {
            int rightStart = findRightOperand(blanked, i);

            if (rightStart != NOT_FOUND
                    && (isSampleValue(readLiteralBefore(condition, i))
                    || isSampleValue(readLiteralAt(condition, rightStart)))) {
                return true;
            }
        }

        return false;
    }

    // Returns where the value after ==, .equals( or .contains( starts.
    private int findRightOperand(String condition, int index) {
        if (condition.startsWith(EQUALS_OPERATOR, index)) {
            return index + EQUALS_OPERATOR.length();
        } else if (condition.startsWith(EQUALS_METHOD, index)
                || condition.startsWith(CONTAINS_METHOD, index)) {
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

        boolean isNumber = start < end
                && Character.isDigit(text.charAt(start));

        // The minus of a negative number such as -5 belongs to it
        if (isNumber && start > 0 && text.charAt(start - 1) == MINUS
                && isNegativeSign(text, start - 1)) {
            start--;
        }

        if (isNumber && !isInCalculation(text, start, end)) {
            return text.substring(start, end);
        }
        return "";
    }

    // Says whether a minus starts a number, as in (-5 or == -5, because
    // nothing before it can end a value, unlike the minus in n - 5.
    private boolean isNegativeSign(String text, int minusIndex) {
        int before = minusIndex - 1;

        while (before >= 0 && Character.isWhitespace(text.charAt(before))) {
            before--;
        }

        if (before < 0) {
            return true;
        }

        char previous = text.charAt(before);

        return !Character.isJavaIdentifierPart(previous)
                && previous != CLOSE_PAREN && previous != CLOSE_BRACKET
                && !JavaSourceText.isQuote(previous);
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

        boolean isNumber = end > digitsStart
                && Character.isDigit(text.charAt(digitsStart));

        if (isNumber && !isInCalculation(text, start, end)) {
            return text.substring(start, end);
        }
        return "";
    }

    // Says whether a sign such as % is next to a number, as in i % 2.
    private boolean isInCalculation(String text, int start, int end) {
        int before = start - 1;

        while (before >= 0 && Character.isWhitespace(text.charAt(before))) {
            before--;
        }

        int after = end;

        while (after < text.length()
                && Character.isWhitespace(text.charAt(after))) {
            after++;
        }

        boolean signBefore = before >= 0
                && ARITHMETIC_SIGNS.indexOf(text.charAt(before)) >= 0;
        boolean signAfter = after < text.length()
                && ARITHMETIC_SIGNS.indexOf(text.charAt(after)) >= 0
                && !text.startsWith(ARROW, after);

        return signBefore || signAfter;
    }

    // Says whether a literal is a sample value, not 0 or a separator.
    private boolean isSampleValue(String literal) {
        if (literal.isEmpty() || isSeparator(literal)) {
            return false;
        }

        for (String commonValue : COMMON_VALUES) {
            if (literal.equals(commonValue)) {
                return false;
            }
        }
        return true;
    }

    // Says whether a quoted literal holds only signs and escapes, such as
    // "-", ", " or '\n', with no letter or digit of its own.
    private boolean isSeparator(String literal) {
        if (!JavaSourceText.isQuote(literal.charAt(0))) {
            return false;
        }

        int index = 0;

        while (index < literal.length()) {
            char current = literal.charAt(index);

            if (current == BACKSLASH) {
                // The n of \n is part of the escape, not a letter
                index += ESCAPE_LENGTH;
            } else if (Character.isLetterOrDigit(current)) {
                return false;
            } else {
                index++;
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
