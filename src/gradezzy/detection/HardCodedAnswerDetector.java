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
 *
 * <p>Tier: COULD. Owner: Jun Sheng.
 */
public class HardCodedAnswerDetector implements Detector {

    /**
     * The name shown in the anomaly log.
     */
    public static final String NAME = "Hard-coded answer";

    // The fewest literal branches in one method that raise a flag. A
    // starter's main often has only two examples, so two branches that
    // pick them out are enough; no honest answer in samples/e2e or
    // samples/lab-test-2 has two
    private static final int MIN_LITERAL_BRANCHES = 2;

    // Values honest code compares with all the time, so they do not count
    private static final String[] COMMON_VALUES = {"0", "1", "-1"};

    // The text in front of the id of every flag a detector raises
    private static final String ID_PREFIX = "detection-";

    // What the instructor can do about a hard-coded answer
    private static final String SUGGESTED_ACTION =
            "Read the method and compare it with the sample cases. The mark "
            + "has not been changed.";

    // The keyword that starts an if branch
    private static final String IF_KEYWORD = "if";

    // The keyword that starts a case branch
    private static final String CASE_KEYWORD = "case";

    // Tests a value against a literal with an operator
    private static final String EQUALS_OPERATOR = "==";

    // Tests a value against a literal with equals
    private static final String EQUALS_METHOD = ".equals";

    // Tests a value against a literal with contains
    private static final String CONTAINS_METHOD = ".contains";

    // The signs of a calculation, such as the % in i % 2
    private static final String ARITHMETIC_SIGNS = "+-*/%";

    // The arrow after a label such as case 3 ->, which is not a minus sign
    private static final String ARROW = "->";

    // The bracket before a condition or an argument
    private static final char OPEN_PAREN = '(';

    // The bracket after a condition or an argument
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
            List<String> fileNames = JavaSourceText.listSourceFiles(question);

            for (String fileName : fileNames) {
                String studentText = JavaSourceText.readStudentFile(
                        submission, assessment, question, fileName);
                String fileDescription = JavaSourceText.describeFile(
                        question, fileName);

                if (studentText != null) {
                    checkMethods(flags, submission, fileDescription,
                            studentText);
                }
            }
        }

        return flags;
    }

    // Flags each answer method, overloads too, with too many literal branches.
    private void checkMethods(List<Anomaly> flags, Submission submission,
            String fileDescription, String studentText) {
        String code = JavaSourceText.removeComments(studentText);

        for (String methodName : JavaSourceText.findAnswerMethodNames(code)) {
            List<String> bodies = JavaSourceText.findMethodBodies(code,
                    methodName);
            for (String body : bodies) {
                int literalBranches = countLiteralBranches(body);

                if (literalBranches >= MIN_LITERAL_BRANCHES) {
                    flags.add(createFlag(submission,
                            fileDescription + ": "
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
            boolean literalBranch = isLiteralIf(body, blanked, i)
                    || isLiteralCase(body, blanked, i);
            if (literalBranch) {
                count++;
            }
        }

        return count;
    }

    // Says whether an if starts at index whose condition compares a value
    // with a literal.
    private boolean isLiteralIf(String body, String blanked, int index) {
        if (!isKeywordAt(blanked, index, IF_KEYWORD)) {
            return false;
        }

        String condition = findCondition(body, blanked, index);
        return comparesWithLiteral(condition);
    }

    // Says whether a case starts at index whose label is a sample value.
    private boolean isLiteralCase(String body, String blanked, int index) {
        if (!isKeywordAt(blanked, index, CASE_KEYWORD)) {
            return false;
        }

        int labelStart = index + CASE_KEYWORD.length();
        String label = readLiteralAt(body, labelStart);
        return isSampleValue(label);
    }

    // Says whether the keyword starts at index as a whole word.
    private boolean isKeywordAt(String text, int index, String keyword) {
        int after = index + keyword.length();
        int before = index - 1;
        boolean startsWord = index == 0
                || !Character.isJavaIdentifierPart(text.charAt(before));
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

        int conditionStart = openIndex + 1;
        return body.substring(conditionStart, closeIndex);
    }

    // Says whether a condition compares something with a literal.
    private boolean comparesWithLiteral(String condition) {
        String blanked = JavaSourceText.blankLiterals(condition);

        for (int i = 0; i < blanked.length(); i++) {
            int rightStart = findRightOperand(blanked, i);
            boolean comparison = rightStart != NOT_FOUND;

            if (comparison && hasSampleOperand(condition, i, rightStart)) {
                return true;
            }
        }

        return false;
    }

    // Says whether the value before a comparison at index, or the value
    // after it at rightStart, is a sample value.
    private boolean hasSampleOperand(String condition, int index,
            int rightStart) {
        String left = readLiteralBefore(condition, index);
        if (isSampleValue(left)) {
            return true;
        }

        String right = readLiteralAt(condition, rightStart);
        return isSampleValue(right);
    }

    // Returns where the value after ==, .equals( or .contains( starts.
    private int findRightOperand(String condition, int index) {
        if (condition.startsWith(EQUALS_OPERATOR, index)) {
            return index + EQUALS_OPERATOR.length();
        }

        boolean methodCall = condition.startsWith(EQUALS_METHOD, index)
                || condition.startsWith(CONTAINS_METHOD, index);
        if (methodCall) {
            int openIndex = condition.indexOf(OPEN_PAREN, index);
            return openIndex + 1;
        }

        return NOT_FOUND;
    }

    // Returns the literal that ends just before index, or "" if none.
    private String readLiteralBefore(String text, int index) {
        int end = index;

        while (JavaSourceText.isSpaceBefore(text, end)) {
            end--;
        }

        int closingQuote = end - 1;
        boolean quoteBefore = end > 0
                && JavaSourceText.isQuote(text.charAt(closingQuote));

        if (quoteBefore) {
            char quote = text.charAt(closingQuote);
            int searchFrom = closingQuote - 1;
            int openingQuote = text.lastIndexOf(quote, searchFrom);

            if (openingQuote < 0) {
                return "";
            }
            return text.substring(openingQuote, end);
        }

        int start = end;

        while (JavaSourceText.isNamePartBefore(text, start)) {
            start--;
        }

        boolean isNumber = start < end
                && Character.isDigit(text.charAt(start));

        // The minus of a negative number such as -5 belongs to it
        int minusIndex = start - 1;
        boolean negative = isNumber && start > 0
                && text.charAt(minusIndex) == MINUS
                && isNegativeSign(text, minusIndex);
        if (negative) {
            start--;
        }

        if (isNumber && !isInCalculation(text, start, end)) {
            return text.substring(start, end);
        }
        return "";
    }

    // Says whether a minus starts a number, as in (-5, not n - 5.
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

        while (isSpaceAt(text, start)) {
            start++;
        }

        int end = start;

        if (end < text.length() && text.charAt(end) == MINUS) {
            end++;
        }

        if (end < text.length() && JavaSourceText.isQuote(text.charAt(end))) {
            char quote = text.charAt(end);
            int afterQuote = end + 1;
            int close = text.indexOf(quote, afterQuote);

            if (close < 0) {
                return "";
            }
            int literalEnd = close + 1;
            return text.substring(start, literalEnd);
        }

        int digitsStart = end;

        while (isNumberPartAt(text, end)) {
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

        while (isSpaceAt(text, after)) {
            after++;
        }

        boolean signBefore = before >= 0
                && ARITHMETIC_SIGNS.indexOf(text.charAt(before)) >= 0;
        boolean signAfter = after < text.length()
                && ARITHMETIC_SIGNS.indexOf(text.charAt(after)) >= 0
                && !text.startsWith(ARROW, after);

        return signBefore || signAfter;
    }

    // Says whether the character at index is a space; false past the end.
    private boolean isSpaceAt(String text, int index) {
        return index < text.length()
                && Character.isWhitespace(text.charAt(index));
    }

    // Says whether the character at index can be part of a number, such
    // as a digit or the point of 3.5; false past the end.
    private boolean isNumberPartAt(String text, int index) {
        if (index >= text.length()) {
            return false;
        }

        char current = text.charAt(index);
        return Character.isLetterOrDigit(current) || current == DECIMAL_POINT;
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

    // Says whether a quoted literal holds only signs, such as ", ".
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
