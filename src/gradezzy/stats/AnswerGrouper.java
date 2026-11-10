/*
 * File: AnswerGrouper.java
 *
 * Purpose: Groups the students of a run who lost marks the same way on
 * a question: one group for each failed test, so a student who failed
 * tests 1 and 3 is in the groups of test 1 and of test 3, or the same
 * "did not compile", "no answer", "ran too long" or "crashed". Inside a
 * group it notes which students wrote code of the same shape (the same
 * code but for names). A group of one test is titled with the name the
 * tester printed for it, such as "Failed test 3: getIsogramWords([])".
 *
 * Where it fits: AnswerGroupService lists these groups with the
 * marker's names and checks. The groups are worked out from the results
 * each time and never stored, so a student graded again moves to the
 * group of the new result.
 */
package gradezzy.stats;

import java.util.ArrayList;
import java.util.HashMap;
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
import gradezzy.util.TextUtils;

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

    /** The start of the pattern of one failed test, such as F:3. */
    public static final String FAILED_TEST_PREFIX = "F:";

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

    // Come between the test numbers of a failed-tests title
    private static final String NUMBER_SEPARATOR = ", ";
    private static final String LAST_NUMBER_SEPARATOR = " and ";

    // Tests are numbered from 1, one more than their index
    private static final int FIRST_TEST_NUMBER = 1;

    // Comes between a question's folder and file in a flag's message
    private static final String FOLDER_SEPARATOR = "/";

    // Comes between a test's number and its name in a title
    private static final String NAME_SEPARATOR = ": ";

    // Names the test a timeout or crash stopped at, such as " at test 3"
    private static final String AT_TEST = " at test ";

    // The longest test name shown in a title, and what ends a cut name
    private static final int MAX_NAME_LENGTH = 60;
    private static final String CUT_MARK = "...";

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
        if (run == null) {
            return groups;
        }
        Assessment assessment = run.getAssessment();
        if (assessment == null) {
            return groups;
        }
        for (Question question : assessment.getQuestions()) {
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
        if (question == null) {
            return null;
        }
        if (hasFlag(submission, question, AnomalyType.INTERNAL_ERROR)) {
            return null;
        }
        if (!result.isCompiled()) {
            if (isMissing(submission, question)) {
                return MISSING;
            }
            return COMPILE;
        }
        String tests = testPattern(result);
        if (result.isTimedOut()) {
            return TIMEOUT_PREFIX + tests;
        }
        boolean failedOne = tests.indexOf(FAILED) >= 0;
        if (isCrash(submission, question, result, failedOne)) {
            return CRASH_PREFIX + tests;
        }
        if (!failedOne) {
            return null;
        }
        return tests;
    }

    // Says whether an answer crashed and lost marks: a runtime error flag
    // about the question with a failed test or less than full marks. A
    // runtime flag on a full answer (such as a total that does not match
    // the cases) is not a mistake of the student's.
    private static boolean isCrash(Submission submission, Question question,
            QuestionResult result, boolean failedOne) {
        if (!hasFlag(submission, question, AnomalyType.RUNTIME_ERROR)) {
            return false;
        }
        boolean lostMarks = result.getScore() < question.getMaxScore();
        return failedOne || lostMarks;
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
        return titleOf(pattern, new HashMap<Integer, String>());
    }

    /**
     * Returns the words shown for a pattern, with the name the tester
     * printed for the test when it is known. A test with no name keeps
     * the words of titleOf(pattern).
     *
     * <p>Tier: COULD.
     *
     * @param pattern a pattern from patternOf
     * @param caseNames the name of each test by its number, from 1
     * @return such as "Failed test 3: getIsogramWords([alice, ben])" or
     *         "Ran too long at test 3: getAverageAge(people.txt, LEE)"
     */
    public static String titleOf(String pattern,
            Map<Integer, String> caseNames) {
        if (pattern.startsWith(FAILED_TEST_PREFIX)) {
            String number = pattern.substring(FAILED_TEST_PREFIX.length());
            return FAILED_ONE + number + nameAfter(number, caseNames);
        }
        if (MISSING.equals(pattern)) {
            return "No answer handed in";
        }
        if (COMPILE.equals(pattern)) {
            return "Did not compile";
        }
        if (pattern.startsWith(TIMEOUT_PREFIX)) {
            String tests = pattern.substring(TIMEOUT_PREFIX.length());
            return RAN_TOO_LONG + whereStopped(tests, caseNames);
        }
        if (pattern.startsWith(CRASH_PREFIX)) {
            String tests = pattern.substring(CRASH_PREFIX.length());
            return CRASHED + whereStopped(tests, caseNames);
        }
        return failedTitle(pattern);
    }

    // Returns ": " and the shown name of the test with a number, or
    // nothing when its name is not known.
    private static String nameAfter(String number,
            Map<Integer, String> caseNames) {
        String name = caseNames.get(Integer.valueOf(number));
        if (name == null) {
            return "";
        }
        return NAME_SEPARATOR + shorten(name);
    }

    // Cuts a long test name to MAX_NAME_LENGTH characters, ending it with
    // "...", so a title fits on its row.
    private static String shorten(String name) {
        if (name.length() <= MAX_NAME_LENGTH) {
            return name;
        }
        int keep = MAX_NAME_LENGTH - CUT_MARK.length();
        return name.substring(0, keep).trim() + CUT_MARK;
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
        return FlagQuestionMatcher.questionOf(anomaly, submission);
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
        return FlagQuestionMatcher.isAbout(anomaly, submission, question);
    }

    // Says whether a student's question is missing: a missing answer
    // file or folder flag whose fix was not applied or kept. A file or
    // folder that was fixed is no longer missing. A missing second file,
    // such as Q3/GoldMember.java beside Q3.java, is a compile error.
    private static boolean isMissing(Submission submission,
            Question question) {
        boolean noFile = hasMissingAnswerFile(submission, question);
        boolean noFolder = hasUnfixedFlag(submission, question,
                AnomalyType.MISSING_QUESTION_FOLDER);
        return noFile || noFolder;
    }

    // Groups the answers of one question, in the run's order: one group
    // for each failed test, and one for each other way of losing marks.
    // The patterns are kept in a list beside the map so the groups come
    // out in the order they were first seen.
    private static List<AnswerGroup> groupQuestion(GradingRun run,
            Question question) {
        Map<Integer, String> caseNames = readCaseNames(run, question);
        List<String> patterns = new ArrayList<>();
        Map<String, AnswerGroup> byPattern = new HashMap<>();
        for (Submission submission : run.getSubmissions()) {
            String pattern = gradedPattern(submission, question);
            for (String groupPattern : splitPattern(pattern)) {
                AnswerGroup group = byPattern.get(groupPattern);
                if (group == null) {
                    group = new AnswerGroup(question.getId(), groupPattern,
                            titleOf(groupPattern, caseNames));
                    patterns.add(groupPattern);
                    byPattern.put(groupPattern, group);
                }
                group.addUsername(submission.getUsername());
            }
        }

        List<AnswerGroup> groups = new ArrayList<>();
        for (String pattern : patterns) {
            groups.add(byPattern.get(pattern));
        }
        return groups;
    }

    // Returns the name of each test of a question by its number: the first
    // name any student's result has for it. Every student runs the same
    // tester, so the names agree. A run saved before names were kept has
    // none.
    private static Map<Integer, String> readCaseNames(GradingRun run,
            Question question) {
        Map<Integer, String> caseNames = new HashMap<>();
        for (Submission submission : run.getSubmissions()) {
            QuestionResult result = findResult(submission, question);
            if (result != null) {
                addCaseNames(result, caseNames);
            }
        }
        return caseNames;
    }

    // Adds the names of a result's tests that are not known yet.
    private static void addCaseNames(QuestionResult result,
            Map<Integer, String> caseNames) {
        for (TestCaseResult testCase : result.getTestCaseResults()) {
            String name = testCase.getName();
            Integer number = Integer.valueOf(testCase.getTestNumber());
            if (name != null && !caseNames.containsKey(number)) {
                caseNames.put(number, name);
            }
        }
    }

    // Returns the groups an answer's pattern puts it in: one F:<test> for
    // each failed test of a pattern of tests, such as F:3 and F:5 for
    // PPFPF; the pattern itself for any other way of losing marks; none
    // for an answer that is not grouped.
    private static List<String> splitPattern(String pattern) {
        List<String> groupPatterns = new ArrayList<>();
        if (pattern == null) {
            return groupPatterns;
        }
        if (!isTestPattern(pattern)) {
            groupPatterns.add(pattern);
            return groupPatterns;
        }
        for (String number : failedNumbers(pattern)) {
            groupPatterns.add(FAILED_TEST_PREFIX + number);
        }
        return groupPatterns;
    }

    // Says whether a pattern is only the P and F of each test.
    private static boolean isTestPattern(String pattern) {
        boolean special = MISSING.equals(pattern) || COMPILE.equals(pattern)
                || pattern.startsWith(TIMEOUT_PREFIX)
                || pattern.startsWith(CRASH_PREFIX);
        return !special;
    }

    // Returns the pattern of a graded student's answer to a question, or
    // null when the student was not graded, has no result for it or the
    // answer is not grouped.
    private static String gradedPattern(Submission submission,
            Question question) {
        if (submission.getStatus() != SubmissionStatus.GRADED) {
            return null;
        }
        // A student not on the score sheet gets no mark, so is left out
        if (hasType(submission, AnomalyType.UNKNOWN_STUDENT)) {
            return null;
        }
        QuestionResult result = findResult(submission, question);
        if (result == null) {
            return null;
        }
        return patternOf(submission, result);
    }

    // Puts a group after the groups of earlier questions and before the
    // smaller groups of its own question; equal sizes go by title.
    private static void insertBySize(List<AnswerGroup> groups,
            AnswerGroup group, Question question) {
        int position = groups.size();
        while (goesBefore(groups, position, group, question)) {
            position--;
        }
        groups.add(position, group);
    }

    // Says whether a new group goes before the group just ahead of a
    // position of the list; nothing is ahead of the start.
    private static boolean goesBefore(List<AnswerGroup> groups,
            int position, AnswerGroup group, Question question) {
        if (position <= 0) {
            return false;
        }
        int before = position - 1;
        return comesBefore(group, groups.get(before), question);
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
        List<String> shapes = new ArrayList<>();
        Map<String, List<String>> byShape = new HashMap<>();
        for (String username : group.getUsernames()) {
            Submission submission = findSubmission(run, username);
            String shape = CodeShapeReader.readShape(run, submission,
                    question);
            if (shape != null) {
                List<String> same = byShape.get(shape);
                if (same == null) {
                    same = new ArrayList<>();
                    shapes.add(shape);
                    byShape.put(shape, same);
                }
                same.add(username);
            }
        }

        // The shapes in the order they were first seen
        for (String shape : shapes) {
            List<String> same = byShape.get(shape);
            if (same.size() > 1) {
                group.addSameShape(same);
            }
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

    // Names where a tester stopped: at the first failed test when its
    // name is known, else after the last test before it, or before test 1.
    private static String whereStopped(String tests,
            Map<Integer, String> caseNames) {
        int finished = tests.indexOf(FAILED);
        if (finished < 0) {
            finished = tests.length();
        }
        int stoppedNumber = finished + FIRST_TEST_NUMBER;
        String name = caseNames.get(Integer.valueOf(stoppedNumber));
        boolean stoppedInTest = finished < tests.length();
        if (stoppedInTest && name != null) {
            return AT_TEST + stoppedNumber + NAME_SEPARATOR + shorten(name);
        }
        if (finished == 0) {
            return BEFORE_FIRST;
        }
        return AFTER_TEST + finished;
    }

    // Names the failed tests, such as "Failed tests 1, 2 and 4".
    private static String failedTitle(String tests) {
        List<String> numbers = failedNumbers(tests);
        if (numbers.isEmpty()) {
            return FAILED_MANY;
        }
        if (numbers.size() == 1) {
            return FAILED_ONE + numbers.get(0);
        }

        // All but the last number between commas, then "and" the last
        int lastIndex = numbers.size() - 1;
        List<String> allButLast = numbers.subList(0, lastIndex);
        String last = numbers.get(lastIndex);
        return FAILED_MANY + TextUtils.join(NUMBER_SEPARATOR, allButLast)
                + LAST_NUMBER_SEPARATOR + last;
    }

    // Returns the numbers of the failed tests, in order.
    private static List<String> failedNumbers(String tests) {
        List<String> numbers = new ArrayList<>();
        for (int index = 0; index < tests.length(); index++) {
            if (tests.charAt(index) == FAILED) {
                int testNumber = index + FIRST_TEST_NUMBER;
                numbers.add(String.valueOf(testNumber));
            }
        }
        return numbers;
    }

    // Says whether a student has a flag of a type, about any question.
    private static boolean hasType(Submission submission, AnomalyType type) {
        for (Anomaly anomaly : submission.getAnomalies()) {
            if (anomaly.getType() == type) {
                return true;
            }
        }
        return false;
    }

    // Says whether a student has a flag of a type about a question.
    private static boolean hasFlag(Submission submission, Question question,
            AnomalyType type) {
        for (Anomaly anomaly : submission.getAnomalies()) {
            boolean sameType = anomaly.getType() == type;
            if (sameType && isAbout(anomaly, submission, question)) {
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
            boolean unfixed = anomaly.getType() == type
                    && !isFixed(anomaly);
            if (unfixed && isAbout(anomaly, submission, question)) {
                return true;
            }
        }
        return false;
    }

    // Says whether a student has an unfixed missing file flag about a
    // question that names the question's own file, such as Q3/Q3.java.
    private static boolean hasMissingAnswerFile(Submission submission,
            Question question) {
        String answerFile = question.getFolderName() + FOLDER_SEPARATOR
                + question.getFileName();
        for (Anomaly anomaly : submission.getAnomalies()) {
            boolean unfixed = anomaly.getType() == AnomalyType.MISSING_FILE
                    && !isFixed(anomaly);
            boolean namesAnswer = anomaly.getMessage() != null
                    && anomaly.getMessage().contains(answerFile);
            if (unfixed && namesAnswer
                    && isAbout(anomaly, submission, question)) {
                return true;
            }
        }
        return false;
    }

    // Says whether a flag's fix was applied or kept.
    private static boolean isFixed(Anomaly anomaly) {
        FixStatus status = anomaly.getFixStatus();
        boolean applied = status == FixStatus.APPLIED;
        boolean approved = status == FixStatus.APPROVED;
        return applied || approved;
    }

    // Returns a student's result for a question, or null.
    private static QuestionResult findResult(Submission submission,
            Question question) {
        for (QuestionResult result : submission.getQuestionResults()) {
            if (isFor(result, question)) {
                return result;
            }
        }
        return null;
    }

    // Says whether a result is for a question.
    private static boolean isFor(QuestionResult result, Question question) {
        Question resultQuestion = result.getQuestion();
        if (resultQuestion == null) {
            return false;
        }
        return resultQuestion.getId().equals(question.getId());
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
