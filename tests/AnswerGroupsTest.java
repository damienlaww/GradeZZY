/*
 * File: AnswerGroupsTest.java
 *
 * Purpose: Checks for answer groups: code shape tokens, how answers are
 * grouped and titled, saved names and checks, and the common mistakes
 * exports.
 *
 * Where it fits: Run by hand after compiling, as tests/README.md says.
 * It builds graded runs in memory, so no student code runs.
 */
import java.nio.file.Files;
import java.nio.file.Path;
import java.time.LocalDateTime;
import java.util.Arrays;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

import gradezzy.config.AppConfig;
import gradezzy.config.ConfigKeys;
import gradezzy.exec.ProcessResult;
import gradezzy.grading.TesterOutputScoreParser;
import gradezzy.model.*;
import gradezzy.persistence.SqliteResultRepository;
import gradezzy.plagiarism.JavaTokenizer;
import gradezzy.report.CommonMistakesExporter;
import gradezzy.report.JsonExporter;
import gradezzy.report.JsonWriter;
import gradezzy.service.AnswerGroupService;
import gradezzy.stats.AnswerGrouper;

/**
 * Runs the answer group checks and prints how many passed.
 * Compile: javac -cp classes -d /tmp/gradezzy-tests tests/*.java; run:
 * java -cp classes:lib/*:/tmp/gradezzy-tests AnswerGroupsTest
 */
public class AnswerGroupsTest {

    // The highest score of Q1a and of Q1b
    private static final int Q1_MAX_SCORE = 3;

    // The highest score of Q2b
    private static final int Q2B_MAX_SCORE = 5;

    // The highest score of Q3
    private static final int Q3_MAX_SCORE = 4;

    // The longest test name a title shows, as AnswerGrouper cuts it
    private static final int SHOWN_NAME_LENGTH = 60;

    // The length of a test name long enough to be cut
    private static final int LONG_NAME_LENGTH = 100;

    // The test the checks give a name to and that ben fails in Q2b
    private static final int NAMED_TEST = 3;

    // The mark for a passed test in an answer pattern
    private static final char PASSED = 'P';

    // The start of the id of every flag these checks add
    private static final String ANOMALY_ID_PREFIX = "id-";

    // The character that escapes the next one in JSON text
    private static final char ESCAPE = '\\';

    // The character that starts and ends a JSON string
    private static final char QUOTE = '"';

    // The number of checks passed so far
    private static int checks = 0;

    /**
     * Runs every group of checks.
     *
     * @param args not used
     * @throws Exception if a check fails or a file cannot be written
     */
    public static void main(String[] args) throws Exception {
        tokenizer();
        patterns();
        grouping();
        caseNames();
        notes();
        exports();
        reviewFixes();
        System.out.println("AnswerGroupsTest: " + checks
                + " checks passed");
    }

    // Checks how the tokenizer splits code and gives its shape.
    private static void tokenizer() {
        List<String> tokens = JavaTokenizer.split("int a = b+1; // x");
        check(tokens.equals(Arrays.asList("int", "a", "=", "b", "+", "1",
                ";")), "split drops spaces and comments: " + tokens);
        check(JavaTokenizer.shapeOf("int total = n + 1;")
                .equals(JavaTokenizer.shapeOf("int sum = k + 1;")),
                "renaming variables keeps the shape");
        check(!JavaTokenizer.shapeOf("if (a < b)")
                .equals(JavaTokenizer.shapeOf("if (a <= b)")),
                "< and <= give different shapes");
        check(JavaTokenizer.shapeOf("s = \"hi\";")
                .equals(JavaTokenizer.shapeOf("t = \"bye\";")),
                "string literals do not change the shape");
        check(!JavaTokenizer.shapeOf("x = 1;")
                .equals(JavaTokenizer.shapeOf("x = 2;")),
                "numbers change the shape");
    }

    // ---------------------------------------------------------- Task 2

    // The section every student is graded for
    static final Section SECTION = new Section("CS102", "G1");

    // Returns a lab test with the Q1a, Q1b, Q2b and Q3 questions.
    static Assessment assessment() {
        Assessment assessment = new Assessment("Lab Test", "CS102");
        assessment.addQuestion(new Question("Q1a", "Q1", "Q1a.java",
                Q1_MAX_SCORE));
        assessment.addQuestion(new Question("Q1b", "Q1", "Q1b.java",
                Q1_MAX_SCORE));
        assessment.addQuestion(new Question("Q2b", "Q2", "Q2b.java",
                Q2B_MAX_SCORE));
        assessment.addQuestion(new Question("Q3", "Q3", "Q3.java",
                Q3_MAX_SCORE));
        return assessment;
    }

    // Returns the question with the given id.
    static Question question(Assessment assessment, String id) {
        for (Question question : assessment.getQuestions()) {
            if (question.getId().equals(id)) {
                return question;
            }
        }
        throw new AssertionError("no question " + id);
    }

    /** A graded student who passed every question but the given one. */
    static Submission student(Assessment assessment, String username,
            String questionId, String pattern) {
        Submission submission = new Submission(username, SECTION,
                Path.of(username + ".zip"));
        for (Question question : assessment.getQuestions()) {
            QuestionResult result = new QuestionResult(question);
            result.setCompiled(true);
            int testCount = (int) question.getMaxScore();
            String outcome = String.valueOf(PASSED).repeat(testCount);
            if (question.getId().equals(questionId)) {
                outcome = pattern;
            }
            for (int i = 0; i < outcome.length(); i++) {
                int testNumber = i + 1;
                boolean passed = outcome.charAt(i) == PASSED;
                result.addTestCaseResult(new TestCaseResult(testNumber,
                        passed, ""));
            }
            submission.addQuestionResult(result);
        }
        submission.setStatus(SubmissionStatus.GRADED);
        return submission;
    }

    // Returns a submission's result for the given question.
    static QuestionResult result(Submission submission,
            String questionId) {
        for (QuestionResult result : submission.getQuestionResults()) {
            if (result.getQuestion().getId().equals(questionId)) {
                return result;
            }
        }
        throw new AssertionError("no result " + questionId);
    }

    // Adds an error flag with the given message to a submission.
    static void flag(Submission submission, AnomalyType type,
            String message) {
        String id = ANOMALY_ID_PREFIX + message.hashCode();
        submission.addAnomaly(new Anomaly(id, type, Severity.ERROR,
                submission.getUsername(), message, ""));
    }

    // Checks the pattern and title given to each kind of lost mark.
    private static void patterns() {
        Assessment assessment = assessment();
        failedTestPatterns(assessment);
        Submission missing = missingFilePattern(assessment);
        fixedFolderPatterns(assessment);
        missingFolderPatterns(assessment);
        compileAndTimeoutPatterns(assessment);
        crashAndTesterPatterns(assessment, missing);
    }

    // Checks a full answer and the titles of failed tests.
    private static void failedTestPatterns(Assessment assessment) {
        Submission all = student(assessment, "all", "Q2b", "PPPPP");
        check(AnswerGrouper.patternOf(all, result(all, "Q2b")) == null,
                "a full answer is not grouped");

        Submission partial = student(assessment, "s", "Q2b", "PPFPF");
        check("PPFPF".equals(AnswerGrouper.patternOf(partial,
                result(partial, "Q2b"))), "failed tests give their pattern");
        check(AnswerGrouper.titleOf("PPFPF").equals("Failed tests 3 and 5"),
                "two failed tests: " + AnswerGrouper.titleOf("PPFPF"));
        check(AnswerGrouper.titleOf("PFP").equals("Failed test 2"),
                "one failed test: " + AnswerGrouper.titleOf("PFP"));
        check(AnswerGrouper.titleOf("FFPF")
                .equals("Failed tests 1, 2 and 4"),
                "three failed tests: " + AnswerGrouper.titleOf("FFPF"));
    }

    // Checks a missing file and returns the student who left it out.
    private static Submission missingFilePattern(Assessment assessment) {
        Submission missing = student(assessment, "missing", "Q1b", "");
        result(missing, "Q1b").setCompiled(false);
        flag(missing, AnomalyType.MISSING_FILE, "Q1/Q1b.java is missing.");
        check("MISSING".equals(AnswerGrouper.patternOf(missing,
                result(missing, "Q1b"))), "a missing file is MISSING");
        check(AnswerGrouper.titleOf("MISSING").equals("No answer handed in"),
                "missing title");
        return missing;
    }

    // Checks a question folder that a fix put back.
    private static void fixedFolderPatterns(Assessment assessment) {
        Submission fixed = student(assessment, "fixed", "Q3", "PPPP");
        flag(fixed, AnomalyType.MISSING_QUESTION_FOLDER,
                "The Q3 folder is missing.");
        fixed.getAnomalies().get(0).setFixStatus(FixStatus.APPLIED);
        check(AnswerGrouper.patternOf(fixed, result(fixed, "Q3")) == null,
                "a fixed folder whose answer passed is not grouped");
        Submission fixedBroken = student(assessment, "fixedBroken", "Q3",
                "");
        result(fixedBroken, "Q3").setCompiled(false);
        flag(fixedBroken, AnomalyType.MISSING_QUESTION_FOLDER,
                "The Q3 folder is missing.");
        fixedBroken.getAnomalies().get(0).setFixStatus(FixStatus.APPROVED);
        check("COMPILE".equals(AnswerGrouper.patternOf(fixedBroken,
                result(fixedBroken, "Q3"))),
                "a fixed folder that did not compile is COMPILE");
    }

    // Checks a missing question folder and a file missing beside another.
    private static void missingFolderPatterns(Assessment assessment) {
        Submission noFolder = student(assessment, "noFolder", "Q1a", "");
        result(noFolder, "Q1a").setCompiled(false);
        result(noFolder, "Q1b").setCompiled(false);
        result(noFolder, "Q1b").getTestCaseResults().clear();
        flag(noFolder, AnomalyType.MISSING_QUESTION_FOLDER,
                "The Q1 folder is missing. Keep Q1a and Q1b at 0.");
        boolean aboutQ1a = "MISSING".equals(AnswerGrouper.patternOf(noFolder,
                result(noFolder, "Q1a")));
        boolean aboutQ1b = "MISSING".equals(AnswerGrouper.patternOf(noFolder,
                result(noFolder, "Q1b")));
        check(aboutQ1a && aboutQ1b,
                "a missing folder naming both its questions is about both");
        Submission otherFile = student(assessment, "otherFile", "Q1a", "");
        result(otherFile, "Q1a").setCompiled(false);
        flag(otherFile, AnomalyType.MISSING_FILE, "Q1/Q1b.java is missing.");
        check("COMPILE".equals(AnswerGrouper.patternOf(otherFile,
                result(otherFile, "Q1a"))),
                "a missing Q1b.java is not about Q1a in the same folder");
    }

    // Checks an answer that did not compile and one that ran too long.
    private static void compileAndTimeoutPatterns(Assessment assessment) {
        Submission compile = student(assessment, "compile", "Q1b", "");
        result(compile, "Q1b").setCompiled(false);
        check("COMPILE".equals(AnswerGrouper.patternOf(compile,
                result(compile, "Q1b"))), "not compiled is COMPILE");
        check(AnswerGrouper.titleOf("COMPILE").equals("Did not compile"),
                "compile title");

        Submission slow = student(assessment, "slow", "Q1a", "PP");
        result(slow, "Q1a").setTimedOut(true);
        check("T:PP".equals(AnswerGrouper.patternOf(slow,
                result(slow, "Q1a"))), "a timeout keeps the tests so far");
        check(AnswerGrouper.titleOf("T:PP")
                .equals("Ran too long after test 2"),
                "timeout title: " + AnswerGrouper.titleOf("T:PP"));
        check(AnswerGrouper.titleOf("T:")
                .equals("Ran too long before test 1"),
                "timeout before any test");
    }

    // Checks a crash, a tester problem and which name a flag is about.
    private static void crashAndTesterPatterns(Assessment assessment,
            Submission missing) {
        Submission crash = student(assessment, "crash", "Q1a", "PF");
        flag(crash, AnomalyType.RUNTIME_ERROR,
                "Q1a case 2 threw an exception.");
        check("X:PF".equals(AnswerGrouper.patternOf(crash,
                result(crash, "Q1a"))),
                "a crash is X: with the tests so far");
        check(AnswerGrouper.titleOf("X:PF").equals("Crashed after test 1"),
                "crash title: " + AnswerGrouper.titleOf("X:PF"));
        check(AnswerGrouper.titleOf("X:").equals("Crashed before test 1"),
                "crash before any test");

        Submission broken = student(assessment, "broken", "Q3", "FFFF");
        flag(broken, AnomalyType.INTERNAL_ERROR,
                "The Q3 tester could not run.");
        check(AnswerGrouper.patternOf(broken, result(broken, "Q3")) == null,
                "a tester problem is not the student's mistake");
        check("Q1b".equals(AnswerGrouper.questionOf(
                missing.getAnomalies().get(0), missing)),
                "the longest name wins: Q1b.java over Q1");
    }

    // Checks how a run's answers are grouped, ordered and shaped.
    private static void grouping() throws Exception {
        Assessment assessment = assessment();
        GradingRun run = new GradingRun("run-1", assessment, SECTION,
                LocalDateTime.now());
        run.addSubmission(student(assessment, "q3only", "Q3", "PFFF"));
        run.addSubmission(student(assessment, "amy", "Q2b", "PPFPF"));
        run.addSubmission(student(assessment, "ben", "Q2b", "PPFPF"));
        run.addSubmission(student(assessment, "cat", "Q2b", "PPFPF"));
        Submission failed = student(assessment, "dan", "Q2b", "PPFPF");
        failed.setStatus(SubmissionStatus.FAILED);
        run.addSubmission(failed);

        List<AnswerGroup> groups = AnswerGrouper.group(run);
        // One group per failed test: Q2b tests 3 and 5, Q3 tests 2, 3, 4
        int expectedGroups = 5;
        check(groups.size() == expectedGroups, "one group per failed "
                + "test: " + groups.size());
        AnswerGroup first = groups.get(0);
        boolean q2bFirst = first.getQuestionId().equals("Q2b")
                && first.getPattern().equals("F:3")
                && first.getTitle().equals("Failed test 3");
        check(q2bFirst, "Q2b comes before Q3 in question order");
        AnswerGroup second = groups.get(1);
        boolean sameStudents = second.getTitle().equals("Failed test 5")
                && second.getUsernames().equals(first.getUsernames());
        check(sameStudents, "a student is in the group of each test "
                + "failed: " + second.getTitle());
        check(first.getUsernames().equals(Arrays.asList("amy", "ben",
                "cat")), "usernames in run order, not graded left out: "
                + first.getUsernames());
        check(first.getKey().equals("Q2b|F:3"), "key");
        check(first.getLabel().equals("") && !first.isChecked(),
                "starts unnamed");

        // missingFileHasNoShape, and same code with renamed variables
        Path root = Files.createTempDirectory("answer-groups-");
        writeAnswer(root, "amy", "int total = n + 1;");
        writeAnswer(root, "ben", "int sum = k + 1;");
        writeAnswer(root, "cat", "int sum = k - 1;");
        for (Submission submission : run.getSubmissions()) {
            submission.setWorkingCopyPath(root.resolve(
                    submission.getUsername()));
        }
        AnswerGroup shaped = AnswerGrouper.group(run).get(0);
        boolean oneShape = shaped.getSameShapes().size() == 1
                && shaped.getSameShapes().get(0)
                        .equals(Arrays.asList("amy", "ben"));
        check(oneShape,
                "amy and ben share a shape: " + shaped.getSameShapes());
        // After the two Q2b groups
        int q3Index = 2;
        AnswerGroup q3 = AnswerGrouper.group(run).get(q3Index);
        boolean noShape = q3.getUsernames().equals(Arrays.asList("q3only"))
                && q3.getSameShapes().isEmpty();
        check(noShape, "a student without a file is grouped with no shape");
    }

    // Checks that a group is titled with the name the tester printed for
    // its test, and keeps its key.
    private static void caseNames() throws Exception {
        readCaseNames();
        namedTitles();
        namedGroups();
        savedCaseNames();
    }

    // Checks how a case's name is read from its header line and output.
    private static void readCaseNames() throws Exception {
        String name = TesterOutputScoreParser.readCaseName(
                "Test 1: getIsogramWords([alice, ben])", "Test");
        check("getIsogramWords([alice, ben])".equals(name),
                "name after the number and colon: " + name);
        name = TesterOutputScoreParser.readCaseName(
                "  Test 12 - getHighest with an empty array:", "Test");
        check("getHighest with an empty array".equals(name),
                "dash and end colon dropped: " + name);
        boolean noName = TesterOutputScoreParser.readCaseName("Test 3", "Test")
                == null && TesterOutputScoreParser.readCaseName("Test 4:",
                "Test") == null;
        check(noName, "a header with no words has no name");

        TesterOutputScoreParser parser = new TesterOutputScoreParser(
                TestConfig.load());
        String output = "Test 1: getA([1])\nPassed\nTest 2: getB([])\n"
                + "Failed\nPassed\n1.0";
        ProcessResult run = new ProcessResult(0, output, "", false, 0L);
        Question question = question(assessment(), "Q1a");
        List<TestCaseResult> cases = parser.parse(question, run)
                .getTestCaseResults();
        int expectedCases = 3;
        boolean named = cases.size() == expectedCases
                && "getA([1])".equals(cases.get(0).getName())
                && "getB([])".equals(cases.get(1).getName())
                && cases.get(expectedCases - 1).getName() == null;
        check(named, "parsed cases keep their header names");
    }

    // Checks the titles of patterns with and without names.
    private static void namedTitles() {
        Map<Integer, String> names = new HashMap<>();
        names.put(NAMED_TEST, "getTopStudent(students.txt, CS102)");
        check(AnswerGrouper.titleOf("F:3", names)
                .equals("Failed test 3: getTopStudent(students.txt, CS102)"),
                "named failed test: " + AnswerGrouper.titleOf("F:3", names));
        check(AnswerGrouper.titleOf("F:2", names).equals("Failed test 2"),
                "a test with no name keeps the plain title");
        check(AnswerGrouper.titleOf("T:PPF", names).equals("Ran too long "
                + "at test 3: getTopStudent(students.txt, CS102)"),
                "named timeout: " + AnswerGrouper.titleOf("T:PPF", names));
        check(AnswerGrouper.titleOf("X:PF", names)
                .equals("Crashed after test 1"),
                "a crash at a test with no name keeps the plain title");
        names.put(1, "x".repeat(LONG_NAME_LENGTH));
        String cut = AnswerGrouper.titleOf("F:1", names);
        int longest = "Failed test 1: ".length() + SHOWN_NAME_LENGTH;
        check(cut.length() == longest && cut.endsWith("..."),
                "a long name is cut: " + cut);
    }

    // Checks that groups take the names of any student's cases, keep
    // their keys, and that a run with no names keeps the plain titles.
    private static void namedGroups() {
        Assessment assessment = assessment();
        GradingRun run = new GradingRun("run-names", assessment, SECTION,
                LocalDateTime.now());
        run.addSubmission(student(assessment, "amy", "Q2b", "PPFPF"));
        Submission ben = student(assessment, "ben", "Q2b", "PPFPF");
        QuestionResult benQ2b = result(ben, "Q2b");
        benQ2b.getTestCaseResults().clear();
        for (int number = 1; number <= Q2B_MAX_SCORE; number++) {
            boolean passed = number != NAMED_TEST
                    && number != Q2B_MAX_SCORE;
            benQ2b.addTestCaseResult(new TestCaseResult(number, passed, "",
                    "getTopStudent(case " + number + ")"));
        }
        run.addSubmission(ben);

        List<AnswerGroup> groups = AnswerGrouper.group(run);
        AnswerGroup first = groups.get(0);
        boolean namedSameKey = first.getTitle()
                .equals("Failed test 3: getTopStudent(case 3)")
                && first.getKey().equals("Q2b|F:3")
                && first.getUsernames().equals(Arrays.asList("amy", "ben"));
        check(namedSameKey, "named title, same key: " + first.getTitle());
        check(groups.get(1).getTitle()
                .equals("Failed test 5: getTopStudent(case 5)"),
                "second named title: " + groups.get(1).getTitle());

        List<AnswerGroup> plain = AnswerGrouper.group(twoStudentRun());
        check(plain.get(0).getTitle().equals("Failed test 3"),
                "a run with no names keeps the plain title");
    }

    // Checks that a case's name is saved and read back.
    private static void savedCaseNames() throws Exception {
        Path file = Files.createTempFile("answer-groups-names-", ".db");
        SqliteResultRepository repository = new SqliteResultRepository(
                file.toString());
        repository.open();
        GradingRun run = twoStudentRun();
        QuestionResult amyQ1a = result(run.getSubmissions().get(0), "Q1a");
        amyQ1a.getTestCaseResults().clear();
        amyQ1a.addTestCaseResult(new TestCaseResult(1, false, "Failed",
                "getA([1])"));
        repository.saveRun(run);
        GradingRun loaded = repository.loadRun(run.getId());
        repository.close();
        TestCaseResult loadedCase = result(loaded.getSubmissions().get(0),
                "Q1a").getTestCaseResults().get(0);
        check("getA([1])".equals(loadedCase.getName()),
                "a case's name is saved: " + loadedCase.getName());
        Files.deleteIfExists(file);
    }

    // ---------------------------------------------------------- Task 3

    // Returns a run where amy and ben failed the same Q2b tests.
    static GradingRun twoStudentRun() {
        Assessment assessment = assessment();
        GradingRun run = new GradingRun("run-notes", assessment, SECTION,
                LocalDateTime.now());
        run.addSubmission(student(assessment, "amy", "Q2b", "PPFPF"));
        run.addSubmission(student(assessment, "ben", "Q2b", "PPFPF"));
        return run;
    }

    // Returns the settings with saving switched on or off.
    static AppConfig config(boolean database, Path file) throws Exception {
        AppConfig config = TestConfig.load();
        config.setString(ConfigKeys.DATABASE_ENABLED,
                String.valueOf(database));
        config.setString(ConfigKeys.DATABASE_FILE, file.toString());
        return config;
    }

    // Checks that names and ticks are kept for the session or saved.
    private static void notes() throws Exception {
        sessionNotes();
        savedNotes();
    }

    // Checks names and ticks kept for the session only.
    private static void sessionNotes() throws Exception {
        GradingRun run = twoStudentRun();
        AnswerGroupService session = new AnswerGroupService(null,
                config(false, Path.of("unused.db")));
        AnswerGroup group = session.listGroups(run).get(0);
        session.setLabel(run, group, "  uses < instead\nof <=  ");
        check(session.listGroups(run).get(0).getLabel()
                .equals("uses < instead of <="), "label trimmed, one line: "
                + session.listGroups(run).get(0).getLabel());
        session.setChecked(run, session.listGroups(run).get(0), true);
        check(session.listGroups(run).get(0).isChecked(), "checked");
        run.addSubmission(student(run.getAssessment(), "cat", "Q2b",
                "PPFPF"));
        AnswerGroup grown = session.listGroups(run).get(0);
        check(!grown.isChecked() && grown.countNewSinceChecked() == 1,
                "a newcomer clears the tick and counts as new");
        session.setChecked(run, grown, false);
        check(session.listGroups(run).get(0).getCheckedUsernames()
                .isEmpty(), "unchecking forgets who was checked");
    }

    // Checks names and ticks saved in a database, read back after a
    // restart, and kept for the session when a save fails.
    private static void savedNotes() throws Exception {
        // round trip through the database
        Path file = Files.createTempFile("answer-groups-", ".db");
        AppConfig saving = config(true, file);
        SqliteResultRepository repository = new SqliteResultRepository(
                file.toString());
        repository.open();
        GradingRun saved = twoStudentRun();
        AnswerGroupService first = new AnswerGroupService(repository,
                saving);
        first.setLabel(saved, first.listGroups(saved).get(0), "off by one");
        first.setChecked(saved, first.listGroups(saved).get(0), true);
        check(first.getSaveProblem().isEmpty(), "saved without a problem: "
                + first.getSaveProblem());
        repository.close();
        SqliteResultRepository reopened = new SqliteResultRepository(
                file.toString());
        reopened.open();
        AnswerGroup loaded = new AnswerGroupService(reopened, saving)
                .listGroups(saved).get(0);
        check(loaded.getLabel().equals("off by one") && loaded.isChecked(),
                "name and tick survive a restart");
        AnswerGroupService clearing = new AnswerGroupService(reopened,
                saving);
        clearing.setLabel(saved, clearing.listGroups(saved).get(0), "");
        clearing.setChecked(saved, clearing.listGroups(saved).get(0),
                false);
        check(reopened.listAnswerGroupNotes(saved.getId()).isEmpty(),
                "an empty note removes the row");

        // saveProblemReported
        AnswerGroupService failing = new AnswerGroupService(reopened,
                saving);
        failing.listGroups(saved);
        reopened.close();
        failing.setLabel(saved, failing.listGroups(saved).get(0),
                "kept anyway");
        boolean keptAnyway = failing.listGroups(saved).get(0).getLabel()
                .equals("kept anyway")
                && !failing.getSaveProblem().isEmpty();
        check(keptAnyway,
                "a failed save keeps the name for the session and says why");
        Files.deleteIfExists(file);
    }

    // ---------------------------------------------------------- Task 4

    // Checks the common mistakes CSV and the JSON mistake field.
    private static void exports() throws Exception {
        GradingRun run = twoStudentRun();
        AnswerGroupService service = new AnswerGroupService(null,
                config(false, Path.of("unused.db")));
        service.setLabel(run, service.listGroups(run).get(0),
                "uses < instead of <=");
        Path csv = Files.createTempFile("common-mistakes-", ".csv");
        new CommonMistakesExporter(service).export(run, csv);
        List<String> lines = Files.readAllLines(csv);
        check(lines.get(0).equals("Module,Section,Question,Mistake,Pattern,"
                + "Label,Students,Same code shape,Checked,Usernames"),
                "header: " + lines.get(0));
        // The header and a row for each failed test, 3 and 5
        int expectedLines = 3;
        boolean oneRow = lines.size() == expectedLines
                && lines.get(1).contains("Q2b")
                && lines.get(1).contains("Failed test 3")
                && lines.get(1).contains("uses < instead of <=")
                && lines.get(1).contains("amy ben")
                && lines.get(1).contains(",2,");
        check(oneRow, "one row per group: " + lines);
        check(CommonMistakesExporter.FORMAT_NAME.equals("Common mistakes"),
                "format name");

        // labelWithCommaAndQuote
        service.setLabel(run, service.listGroups(run).get(0), "a, \"b\"");
        new CommonMistakesExporter(service).export(run, csv);
        List<String> quoted = Files.readAllLines(csv);
        boolean oneCell = quoted.size() == expectedLines
                && quoted.get(1).contains("\"a, \"\"b\"\"\"");
        check(oneCell, "a comma and quote stay in one quoted cell: "
                + quoted);

        Path json = Files.createTempFile("results-", ".json");
        JsonExporter jsonExporter = new JsonExporter(new JsonWriter());
        jsonExporter.setAnswerGroupSource(service);
        jsonExporter.export(run, json);
        String text = Files.readString(json);
        boolean mistakeNamed = text.contains("\"mistake\"")
                && text.contains("a, \\\"b\\\"")
                && text.contains("Failed test 3");
        check(mistakeNamed, "the JSON names the mistake");
        check(count(text, '{') == count(text, '}'),
                "the JSON stays balanced");
        Files.deleteIfExists(csv);
        Files.deleteIfExists(json);
    }

    // Counts a character in JSON text, outside quoted strings.
    static int count(String text, char wanted) {
        int count = 0;
        boolean inString = false;
        for (int i = 0; i < text.length(); i++) {
            char character = text.charAt(i);
            if (character == ESCAPE) {
                // skip the escaped character
                i++;
            } else if (character == QUOTE) {
                inString = !inString;
            } else if (!inString && character == wanted) {
                count++;
            }
        }
        return count;
    }

    // ------------------------------------------------- final review fixes

    // Checks the fixes made after the answer group review.
    private static void reviewFixes() throws Exception {
        sessionCheckFixes();
        failedReadFixes();
        flaggedAnswerFixes();
    }

    // Checks fixes I1 and I2: a check keeps only the students the sheet
    // listed, and the note shown when saving is off.
    private static void sessionCheckFixes() throws Exception {
        // I1: a check keeps only the students the sheet listed
        GradingRun run = twoStudentRun();
        AnswerGroupService session = new AnswerGroupService(null,
                config(false, Path.of("unused.db")));
        run.addSubmission(student(run.getAssessment(), "cat", "Q2b",
                "PPFPF"));
        AnswerGroup grown = session.listGroups(run).get(0);
        session.setChecked(run, grown, Arrays.asList("amy", "ben"));
        AnswerGroup after = session.listGroups(run).get(0);
        check(!after.isChecked() && after.countNewSinceChecked() == 1,
                "I1: a student who joined before Confirm is not counted "
                + "as checked");

        // I2: with saving off, say the decision lasts for this session only
        check(session.describeSaving().equals(" For this session only; saving "
                + "results is switched off."), "I2: session-only note: "
                + session.describeSaving());
    }

    // Checks fixes I3 and I4: a failed read never overwrites saved data,
    // and forgetAll reads the store again.
    private static void failedReadFixes() throws Exception {
        // I3: a failed read is retried and never overwrites saved data
        Path file = Files.createTempFile("answer-groups-read-", ".db");
        AppConfig saving = config(true, file);
        SqliteResultRepository repository = new SqliteResultRepository(
                file.toString());
        repository.open();
        GradingRun saved = twoStudentRun();
        AnswerGroupService first = new AnswerGroupService(repository,
                saving);
        first.setLabel(saved, first.listGroups(saved).get(0), "off by one");
        first.setChecked(saved, first.listGroups(saved).get(0), true);
        check(first.describeSaving().isEmpty(),
                "I2: nothing to say when saved");
        repository.close();
        AnswerGroupService second = new AnswerGroupService(repository,
                saving);
        AnswerGroup unread = second.listGroups(saved).get(0);
        check(unread.getLabel().isEmpty() && !second.getSaveProblem().isEmpty(),
                "I3: a failed read is reported");
        repository.open();
        second.setLabel(saved, second.listGroups(saved).get(0), "renamed");
        AnswerGroup reread = new AnswerGroupService(repository, saving)
                .listGroups(saved).get(0);
        check(reread.getLabel().equals("renamed") && reread.isChecked(),
                "I3: the saved tick survives a failed read and a rename: "
                + reread.getLabel() + " " + reread.isChecked());

        // I4: forgetAll reads the store again, as after a restore
        AnswerGroupService cached = new AnswerGroupService(repository,
                saving);
        cached.listGroups(saved);
        new AnswerGroupService(repository, saving).setLabel(saved,
                cached.listGroups(saved).get(0), "from the backup");
        cached.forgetAll();
        check(cached.listGroups(saved).get(0).getLabel()
                .equals("from the backup"),
                "I4: forgetAll drops the cached names");
        repository.close();
        Files.deleteIfExists(file);
    }

    // Checks fixes M2 and M5: a full answer with a runtime flag and a
    // student not on the score sheet are not grouped.
    private static void flaggedAnswerFixes() {
        // M2: a full answer with a runtime flag is not a mistake
        Assessment assessment = assessment();
        Submission full = student(assessment, "full", "Q1a", "PPP");
        flag(full, AnomalyType.RUNTIME_ERROR, "Q1a printed a total of 9.0, "
                + "but only 3 of 3 cases passed.");
        result(full, "Q1a").setScore(Q1_MAX_SCORE);
        check(AnswerGrouper.patternOf(full, result(full, "Q1a")) == null,
                "M2: full marks with a runtime flag is not grouped");

        // M5: a student not on the score sheet is left out
        GradingRun withVisitor = twoStudentRun();
        Submission visitor = student(withVisitor.getAssessment(), "visitor",
                "Q2b", "PPFPF");
        flag(visitor, AnomalyType.UNKNOWN_STUDENT,
                "visitor was not on the score sheet.");
        withVisitor.addSubmission(visitor);
        check(!AnswerGrouper.group(withVisitor).get(0).getUsernames()
                .contains("visitor"),
                "M5: a student not on the score sheet is not grouped");
    }

    // Writes a student's Q2b answer into their working copy.
    static void writeAnswer(Path root, String username, String code)
            throws Exception {
        Path folder = root.resolve(username).resolve("Q2");
        Files.createDirectories(folder);
        Files.writeString(folder.resolve("Q2b.java"), code);
    }

    // Counts a passed check, or stops with the message of a failed one.
    private static void check(boolean condition, String message) {
        if (!condition) {
            throw new AssertionError(message);
        }
        checks++;
    }
}
