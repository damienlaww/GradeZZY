/*
 * File: AnswerGrouper.java
 *
 * Purpose: Groups the students of a run who lost marks the same way on
 * a question: the same tests failed, or the same "did not compile", "no
 * answer", "ran too long" or "crashed". Inside a group it notes which
 * students wrote code of the same shape (the same code but for names).
 *
 * Where it fits: AnswerGroupService lists these groups with the
 * marker's names and checks. The groups are worked out from the results
 * each time and never stored, so a student graded again moves to the
 * group of the new result.
 */
package gradezzy.stats;

import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

import gradezzy.model.Anomaly;
import gradezzy.model.AnomalyType;
import gradezzy.model.AnswerGroup;
import gradezzy.model.Assessment;
import gradezzy.model.FixStatus;
import gradezzy.model.GradingRun;
import gradezzy.model.Question;
import gradezzy.model.QuestionResult;
import gradezzy.model.Submission;
import gradezzy.model.SubmissionStatus;
import gradezzy.model.TestCaseResult;
import gradezzy.plagiarism.JavaTokenizer;
import gradezzy.testcase.QuestionFolderFinder;

/**
 * Works out the answer groups of a run.
 *
 * <p>Tier: COULD.
 */
public final class AnswerGrouper {

    /** The pattern of a question with no file or folder handed in. */
    public static final String MISSING = "MISSING";

    /** The pattern of a question that did not compile. */
    public static final String COMPILE = "COMPILE";

    /** Starts the pattern of a tester stopped at the time limit. */
    public static final String TIMEOUT_PREFIX = "T:";

    /** Starts the pattern of a tester the student's code crashed. */
    public static final String CRASH_PREFIX = "X:";

    // One passed test in a pattern
    private static final char PASSED = 'P';

    // One failed test in a pattern
    private static final char FAILED = 'F';

    // The words before the test numbers of a failed-tests title
    private static final String FAILED_ONE = "Failed test ";
    private static final String FAILED_MANY = "Failed tests ";

    // How the tests so far are named in a timeout or crash title
    private static final String RAN_TOO_LONG = "Ran too long";
    private static final String CRASHED = "Crashed";
    private static final String BEFORE_FIRST = " before test 1";
    private static final String AFTER_TEST = " after test ";

    // Only static methods; never created
    private AnswerGrouper() {
    }

    /**
     * Works out the groups of a run: in the assessment's question order,
     * and inside a question the largest group first, then by title.
     * Only graded students are grouped, and only answers that lost marks
     * on the tests.
     *
     * <p>Tier: COULD.
     *
     * @param run the run
     * @return the groups, with usernames in the run's order
     */
    public static List<AnswerGroup> group(GradingRun run) {
        List<AnswerGroup> groups = new ArrayList<>();
        if (run == null || run.getAssessment() == null) {
            return groups;
        }
        for (Question question : run.getAssessment().getQuestions()) {
            List<AnswerGroup> questionGroups = groupQuestion(run, question);
            for (AnswerGroup group : questionGroups) {
                addSameShapes(run, question, group);
                insertBySize(groups, group, question);
            }
        }
        return groups;
    }

    /**
     * Returns how one answer lost marks, or null when it is not grouped:
     * all tests passed, or its tester had a problem that is not the
     * student's.
     *
     * <p>Tier: COULD.
     *
     * @param submission the student
     * @param result the student's result for the question
     * @return MISSING, COMPILE, T: or X: with the tests so far, the
     *         P and F of each test such as PPFPF, or null
     */
    public static String patternOf(Submission submission,
            QuestionResult result) {
        Question question = result.getQuestion();
        if (question == null
                || hasFlag(submission, question, AnomalyType.INTERNAL_ERROR)) {
            return null;
        }
        if (!result.isCompiled()) {
            // A file or folder that was fixed is no longer missing
            if (hasUnfixedFlag(submission, question,
                    AnomalyType.MISSING_FILE)
                    || hasUnfixedFlag(submission, question,
                    AnomalyType.MISSING_QUESTION_FOLDER)) {
                return MISSING;
            }
            return COMPILE;
        }
        String tests = testPattern(result);
        if (result.isTimedOut()) {
            return TIMEOUT_PREFIX + tests;
        }
        if (hasFlag(submission, question, AnomalyType.RUNTIME_ERROR)) {
            return CRASH_PREFIX + tests;
        }
        if (tests.indexOf(FAILED) < 0) {
            return null;
        }
        return tests;
    }

    /**
     * Returns the words shown for a pattern.
     *
     * <p>Tier: COULD.
     *
     * @param pattern a pattern from patternOf
     * @return such as "Failed tests 3 and 5", "Did not compile", "No
     *         answer handed in", "Ran too long after test 2" or "Crashed
     *         before test 1"
     */
    public static String titleOf(String pattern) {
        if (MISSING.equals(pattern)) {
            return "No answer handed in";
        }
        if (COMPILE.equals(pattern)) {
            return "Did not compile";
        }
        if (pattern.startsWith(TIMEOUT_PREFIX)) {
            return RAN_TOO_LONG + whereStopped(pattern.substring(
                    TIMEOUT_PREFIX.length()));
        }
        if (pattern.startsWith(CRASH_PREFIX)) {
            return CRASHED + whereStopped(pattern.substring(
                    CRASH_PREFIX.length()));
        }
        return failedTitle(pattern);
    }

    /**
     * Returns the question a flag is about: the longest of a question's
     * id, file name or folder name named as a whole word in the flag's
     * message or suggested action.
     *
     * <p>Tier: COULD.
     *
     * @param anomaly the flag
     * @param submission the student it belongs to
     * @return the question id, or null when it names none
     */
    public static String questionOf(Anomaly anomaly, Submission submission) {
        String text = textOf(anomaly);
        String found = null;
        int foundLength = 0;
        for (QuestionResult result : submission.getQuestionResults()) {
            Question question = result.getQuestion();
            if (question == null) {
                continue;
            }
            String[] names = {question.getId(), question.getFileName(),
                question.getFolderName()};
            for (String name : names) {
                if (name != null && name.length() > foundLength
                        && namesWord(text, name)) {
                    found = question.getId();
                    foundLength = name.length();
                }
            }
        }
        return found;
    }

    /**
     * Says whether a flag is about a question: it names the question's id
     * or file, or it names the question's folder and no other question
     * (such as "The Q2 folder is missing"), which is about every question
     * in that folder.
     *
     * <p>Tier: COULD.
     *
     * @param anomaly the flag
     * @param submission the student it belongs to
     * @param question the question
     * @return true when the flag is about the question
     */
    public static boolean isAbout(Anomaly anomaly, Submission submission,
            Question question) {
        String text = textOf(anomaly);
        if (namesQuestion(text, question)) {
            return true;
        }
        return namesWord(text, question.getFolderName())
                && !namesOtherQuestion(text, submission, question);
    }

    // Groups the answers of one question by pattern, in the run's order.
    private static List<AnswerGroup> groupQuestion(GradingRun run,
            Question question) {
        Map<String, AnswerGroup> byPattern = new LinkedHashMap<>();
        for (Submission submission : run.getSubmissions()) {
            QuestionResult result = findResult(submission, question);
            if (submission.getStatus() != SubmissionStatus.GRADED
                    || result == null) {
                continue;
            }
            String pattern = patternOf(submission, result);
            if (pattern == null) {
                continue;
            }
            AnswerGroup group = byPattern.get(pattern);
            if (group == null) {
                group = new AnswerGroup(question.getId(), pattern,
                        titleOf(pattern));
                byPattern.put(pattern, group);
            }
            group.addUsername(submission.getUsername());
        }
        return new ArrayList<>(byPattern.values());
    }

    // Puts a group after the groups of earlier questions and before the
    // smaller groups of its own question; equal sizes go by title.
    private static void insertBySize(List<AnswerGroup> groups,
            AnswerGroup group, Question question) {
        int position = groups.size();
        while (position > 0 && comesBefore(group,
                groups.get(position - 1), question)) {
            position--;
        }
        groups.add(position, group);
    }

    // Says whether a new group goes before an earlier one of the list.
    private static boolean comesBefore(AnswerGroup group, AnswerGroup other,
            Question question) {
        if (!other.getQuestionId().equals(question.getId())) {
            return false;
        }
        int size = group.getUsernames().size();
        int otherSize = other.getUsernames().size();
        if (size != otherSize) {
            return size > otherSize;
        }
        return group.getTitle().compareTo(other.getTitle()) < 0;
    }

    // Notes the students of a group whose code has the same shape. A
    // student whose file cannot be read has no shape.
    private static void addSameShapes(GradingRun run, Question question,
            AnswerGroup group) {
        Map<String, List<String>> byShape = new LinkedHashMap<>();
        for (String username : group.getUsernames()) {
            String shape = readShape(run, findSubmission(run, username),
                    question);
            if (shape == null) {
                continue;
            }
            List<String> same = byShape.get(shape);
            if (same == null) {
                same = new ArrayList<>();
                byShape.put(shape, same);
            }
            same.add(username);
        }
        for (List<String> same : byShape.values()) {
            if (same.size() > 1) {
                group.addSameShape(same);
            }
        }
    }

    // Returns the shape of a student's file for a question, or null.
    private static String readShape(GradingRun run, Submission submission,
            Question question) {
        if (submission == null) {
            return null;
        }
        try {
            Assessment assessment = run.getAssessment();
            Path folder = QuestionFolderFinder.find(submission, assessment,
                    question);
            if (folder == null) {
                return null;
            }
            Path file = folder.resolve(question.getFileName());
            if (!Files.isRegularFile(file)) {
                return null;
            }
            return JavaTokenizer.shapeOf(new String(Files.readAllBytes(
                    file), StandardCharsets.UTF_8));
        } catch (IOException exception) {
            return null;
        }
    }

    // Returns the P or F of each test of a result, in order.
    private static String testPattern(QuestionResult result) {
        String tests = "";
        for (TestCaseResult testCase : result.getTestCaseResults()) {
            if (testCase.isPassed()) {
                tests += PASSED;
            } else {
                tests += FAILED;
            }
        }
        return tests;
    }

    // Names where a tester stopped: after the last test before the first
    // failed one, or before test 1.
    private static String whereStopped(String tests) {
        int finished = tests.indexOf(FAILED);
        if (finished < 0) {
            finished = tests.length();
        }
        if (finished == 0) {
            return BEFORE_FIRST;
        }
        return AFTER_TEST + finished;
    }

    // Names the failed tests, such as "Failed tests 1, 2 and 4".
    private static String failedTitle(String tests) {
        List<String> numbers = new ArrayList<>();
        for (int index = 0; index < tests.length(); index++) {
            if (tests.charAt(index) == FAILED) {
                numbers.add(String.valueOf(index + 1));
            }
        }
        if (numbers.size() == 1) {
            return FAILED_ONE + numbers.get(0);
        }
        String title = FAILED_MANY;
        for (int index = 0; index < numbers.size(); index++) {
            if (index == numbers.size() - 1) {
                title += " and ";
            } else if (index > 0) {
                title += ", ";
            }
            title += numbers.get(index);
        }
        return title;
    }

    // Says whether a student has a flag of a type about a question.
    private static boolean hasFlag(Submission submission, Question question,
            AnomalyType type) {
        for (Anomaly anomaly : submission.getAnomalies()) {
            if (anomaly.getType() == type
                    && isAbout(anomaly, submission, question)) {
                return true;
            }
        }
        return false;
    }

    // Says whether a student has a flag of a type about a question whose
    // fix was not applied or kept.
    private static boolean hasUnfixedFlag(Submission submission,
            Question question, AnomalyType type) {
        for (Anomaly anomaly : submission.getAnomalies()) {
            FixStatus status = anomaly.getFixStatus();
            if (anomaly.getType() == type && status != FixStatus.APPLIED
                    && status != FixStatus.APPROVED
                    && isAbout(anomaly, submission, question)) {
                return true;
            }
        }
        return false;
    }

    // Says whether a text names a question's id or file name.
    private static boolean namesQuestion(String text, Question question) {
        return namesWord(text, question.getId())
                || namesWord(text, question.getFileName());
    }

    // Says whether a text names another question than the one given.
    private static boolean namesOtherQuestion(String text,
            Submission submission, Question question) {
        for (QuestionResult result : submission.getQuestionResults()) {
            Question other = result.getQuestion();
            if (other != null && !other.getId().equals(question.getId())
                    && namesQuestion(text, other)) {
                return true;
            }
        }
        return false;
    }

    // Returns a flag's message and suggested action as one text.
    private static String textOf(Anomaly anomaly) {
        return anomaly.getMessage() + " " + anomaly.getSuggestedAction();
    }

    // Says whether a text holds a name as a whole word: no letter, digit
    // or underscore right before or after it.
    private static boolean namesWord(String text, String name) {
        if (text == null || name == null || name.isEmpty()) {
            return false;
        }
        int start = text.indexOf(name);
        while (start >= 0) {
            int end = start + name.length();
            boolean startsWord = start == 0
                    || !isWordChar(text.charAt(start - 1));
            boolean endsWord = end == text.length()
                    || !isWordChar(text.charAt(end));
            if (startsWord && endsWord) {
                return true;
            }
            start = text.indexOf(name, start + 1);
        }
        return false;
    }

    // Says whether a character can be part of a word.
    private static boolean isWordChar(char character) {
        return Character.isLetterOrDigit(character) || character == '_';
    }

    // Returns a student's result for a question, or null.
    private static QuestionResult findResult(Submission submission,
            Question question) {
        for (QuestionResult result : submission.getQuestionResults()) {
            if (result.getQuestion() != null && result.getQuestion().getId()
                    .equals(question.getId())) {
                return result;
            }
        }
        return null;
    }

    // Returns the student with a username in a run, or null.
    private static Submission findSubmission(GradingRun run,
            String username) {
        for (Submission submission : run.getSubmissions()) {
            if (submission.getUsername().equals(username)) {
                return submission;
            }
        }
        return null;
    }
}
