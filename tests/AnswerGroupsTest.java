import java.nio.file.Files;
import java.nio.file.Path;
import java.time.LocalDateTime;
import java.util.Arrays;
import java.util.List;

import gradezzy.config.AppConfig;
import gradezzy.config.ConfigKeys;
import gradezzy.model.*;
import gradezzy.persistence.SqliteResultRepository;
import gradezzy.plagiarism.JavaTokenizer;
import gradezzy.service.AnswerGroupService;
import gradezzy.stats.AnswerGrouper;

/** Run after compilation: javac -cp classes -d /tmp/gradezzy-tests tests/AnswerGroupsTest.java
 *  then: java -cp classes:lib/*:/tmp/gradezzy-tests AnswerGroupsTest
 */
public class AnswerGroupsTest {
    private static int checks;

    public static void main(String[] args) throws Exception {
        tokenizer();
        patterns();
        grouping();
        notes();
        System.out.println("AnswerGroupsTest: " + checks + " checks passed");
    }

    private static void tokenizer() {
        List<String> tokens = JavaTokenizer.split("int a = b+1; // x");
        check(tokens.equals(Arrays.asList("int", "a", "=", "b", "+", "1", ";")),
                "split drops spaces and comments: " + tokens);
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

    static final Section SECTION = new Section("CS102", "G1");

    static Assessment assessment() {
        Assessment assessment = new Assessment("Lab Test", "CS102");
        assessment.addQuestion(new Question("Q1a", "Q1", "Q1a.java", 3));
        assessment.addQuestion(new Question("Q1b", "Q1", "Q1b.java", 3));
        assessment.addQuestion(new Question("Q2b", "Q2", "Q2b.java", 5));
        assessment.addQuestion(new Question("Q3", "Q3", "Q3.java", 4));
        return assessment;
    }

    static Question question(Assessment assessment, String id) {
        for (Question question : assessment.getQuestions()) {
            if (question.getId().equals(id)) return question;
        }
        throw new AssertionError("no question " + id);
    }

    /** A graded student whose every question passed, except the given one. */
    static Submission student(Assessment assessment, String username,
            String questionId, String pattern) {
        Submission submission = new Submission(username, SECTION,
                Path.of(username + ".zip"));
        for (Question question : assessment.getQuestions()) {
            QuestionResult result = new QuestionResult(question);
            result.setCompiled(true);
            String outcome = "P".repeat((int) question.getMaxScore());
            if (question.getId().equals(questionId)) outcome = pattern;
            for (int i = 0; i < outcome.length(); i++) {
                result.addTestCaseResult(new TestCaseResult(i + 1,
                        outcome.charAt(i) == 'P', ""));
            }
            submission.addQuestionResult(result);
        }
        submission.setStatus(SubmissionStatus.GRADED);
        return submission;
    }

    static QuestionResult result(Submission submission, String questionId) {
        for (QuestionResult result : submission.getQuestionResults()) {
            if (result.getQuestion().getId().equals(questionId)) return result;
        }
        throw new AssertionError("no result " + questionId);
    }

    static void flag(Submission submission, AnomalyType type, String message) {
        submission.addAnomaly(new Anomaly("id-" + message.hashCode(), type,
                Severity.ERROR, submission.getUsername(), message, ""));
    }

    private static void patterns() {
        Assessment a = assessment();
        Submission all = student(a, "all", "Q2b", "PPPPP");
        check(AnswerGrouper.patternOf(all, result(all, "Q2b")) == null,
                "a full answer is not grouped");

        Submission s = student(a, "s", "Q2b", "PPFPF");
        check("PPFPF".equals(AnswerGrouper.patternOf(s, result(s, "Q2b"))),
                "failed tests give their pattern");
        check(AnswerGrouper.titleOf("PPFPF").equals("Failed tests 3 and 5"),
                "two failed tests: " + AnswerGrouper.titleOf("PPFPF"));
        check(AnswerGrouper.titleOf("PFP").equals("Failed test 2"),
                "one failed test: " + AnswerGrouper.titleOf("PFP"));
        check(AnswerGrouper.titleOf("FFPF").equals("Failed tests 1, 2 and 4"),
                "three failed tests: " + AnswerGrouper.titleOf("FFPF"));

        Submission missing = student(a, "missing", "Q1b", "");
        result(missing, "Q1b").setCompiled(false);
        flag(missing, AnomalyType.MISSING_FILE, "Q1/Q1b.java is missing.");
        check("MISSING".equals(AnswerGrouper.patternOf(missing,
                result(missing, "Q1b"))), "a missing file is MISSING");
        check(AnswerGrouper.titleOf("MISSING").equals("No answer handed in"),
                "missing title");

        Submission fixed = student(a, "fixed", "Q3", "PPPP");
        flag(fixed, AnomalyType.MISSING_QUESTION_FOLDER, "The Q3 folder is missing.");
        fixed.getAnomalies().get(0).setFixStatus(FixStatus.APPLIED);
        check(AnswerGrouper.patternOf(fixed, result(fixed, "Q3")) == null,
                "a fixed folder whose answer passed is not grouped");
        Submission fixedBroken = student(a, "fixedBroken", "Q3", "");
        result(fixedBroken, "Q3").setCompiled(false);
        flag(fixedBroken, AnomalyType.MISSING_QUESTION_FOLDER, "The Q3 folder is missing.");
        fixedBroken.getAnomalies().get(0).setFixStatus(FixStatus.APPROVED);
        check("COMPILE".equals(AnswerGrouper.patternOf(fixedBroken,
                result(fixedBroken, "Q3"))), "a fixed folder that did not compile is COMPILE");

        Submission compile = student(a, "compile", "Q1b", "");
        result(compile, "Q1b").setCompiled(false);
        check("COMPILE".equals(AnswerGrouper.patternOf(compile,
                result(compile, "Q1b"))), "not compiled is COMPILE");
        check(AnswerGrouper.titleOf("COMPILE").equals("Did not compile"),
                "compile title");

        Submission slow = student(a, "slow", "Q1a", "PP");
        result(slow, "Q1a").setTimedOut(true);
        check("T:PP".equals(AnswerGrouper.patternOf(slow, result(slow, "Q1a"))),
                "a timeout keeps the tests so far");
        check(AnswerGrouper.titleOf("T:PP").equals("Ran too long after test 2"),
                "timeout title: " + AnswerGrouper.titleOf("T:PP"));
        check(AnswerGrouper.titleOf("T:").equals("Ran too long before test 1"),
                "timeout before any test");

        Submission crash = student(a, "crash", "Q1a", "PF");
        flag(crash, AnomalyType.RUNTIME_ERROR, "Q1a case 2 threw an exception.");
        check("X:PF".equals(AnswerGrouper.patternOf(crash, result(crash, "Q1a"))),
                "a crash is X: with the tests so far");
        check(AnswerGrouper.titleOf("X:PF").equals("Crashed after test 1"),
                "crash title: " + AnswerGrouper.titleOf("X:PF"));
        check(AnswerGrouper.titleOf("X:").equals("Crashed before test 1"),
                "crash before any test");

        Submission broken = student(a, "broken", "Q3", "FFFF");
        flag(broken, AnomalyType.INTERNAL_ERROR, "The Q3 tester could not run.");
        check(AnswerGrouper.patternOf(broken, result(broken, "Q3")) == null,
                "a tester problem is not the student's mistake");
        check("Q1b".equals(AnswerGrouper.questionOf(missing.getAnomalies().get(0),
                missing)), "the longest name wins: Q1b.java over Q1");
    }

    private static void grouping() throws Exception {
        Assessment a = assessment();
        GradingRun run = new GradingRun("run-1", a, SECTION, LocalDateTime.now());
        run.addSubmission(student(a, "q3only", "Q3", "PFFF"));
        run.addSubmission(student(a, "amy", "Q2b", "PPFPF"));
        run.addSubmission(student(a, "ben", "Q2b", "PPFPF"));
        run.addSubmission(student(a, "cat", "Q2b", "PPFPF"));
        Submission failed = student(a, "dan", "Q2b", "PPFPF");
        failed.setStatus(SubmissionStatus.FAILED);
        run.addSubmission(failed);

        List<AnswerGroup> groups = AnswerGrouper.group(run);
        check(groups.size() == 2, "two groups: " + groups.size());
        AnswerGroup first = groups.get(0);
        check(first.getQuestionId().equals("Q2b") && first.getPattern().equals("PPFPF")
                && first.getTitle().equals("Failed tests 3 and 5"),
                "Q2b comes before Q3 in question order");
        check(first.getUsernames().equals(Arrays.asList("amy", "ben", "cat")),
                "usernames in run order, not graded left out: " + first.getUsernames());
        check(first.getKey().equals("Q2b|PPFPF"), "key");
        check(first.getLabel().equals("") && !first.isChecked(), "starts unnamed");

        // missingFileHasNoShape, and same code with renamed variables
        Path root = Files.createTempDirectory("answer-groups-");
        writeAnswer(root, "amy", "int total = n + 1;");
        writeAnswer(root, "ben", "int sum = k + 1;");
        writeAnswer(root, "cat", "int sum = k - 1;");
        for (Submission submission : run.getSubmissions()) {
            submission.setWorkingCopyPath(root.resolve(submission.getUsername()));
        }
        AnswerGroup shaped = AnswerGrouper.group(run).get(0);
        check(shaped.getSameShapes().size() == 1
                && shaped.getSameShapes().get(0).equals(Arrays.asList("amy", "ben")),
                "amy and ben share a shape: " + shaped.getSameShapes());
        AnswerGroup q3 = AnswerGrouper.group(run).get(1);
        check(q3.getUsernames().equals(Arrays.asList("q3only"))
                && q3.getSameShapes().isEmpty(),
                "a student without a file is grouped with no shape");
    }

    // ---------------------------------------------------------- Task 3

    static GradingRun twoStudentRun() {
        Assessment a = assessment();
        GradingRun run = new GradingRun("run-notes", a, SECTION, LocalDateTime.now());
        run.addSubmission(student(a, "amy", "Q2b", "PPFPF"));
        run.addSubmission(student(a, "ben", "Q2b", "PPFPF"));
        return run;
    }

    static AppConfig config(boolean database, Path file) throws Exception {
        AppConfig config = new AppConfig("config.properties");
        config.setString(ConfigKeys.DATABASE_ENABLED, String.valueOf(database));
        config.setString(ConfigKeys.DATABASE_FILE, file.toString());
        return config;
    }

    private static void notes() throws Exception {
        // sessionOnlyWithoutRepository
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
        run.addSubmission(student(run.getAssessment(), "cat", "Q2b", "PPFPF"));
        AnswerGroup grown = session.listGroups(run).get(0);
        check(!grown.isChecked() && grown.countNewSinceChecked() == 1,
                "a newcomer clears the tick and counts as new");
        session.setChecked(run, grown, false);
        check(session.listGroups(run).get(0).getCheckedUsernames().isEmpty(),
                "unchecking forgets who was checked");

        // round trip through the database
        Path file = Files.createTempFile("answer-groups-", ".db");
        AppConfig saving = config(true, file);
        SqliteResultRepository repository = new SqliteResultRepository(file.toString());
        repository.open();
        GradingRun saved = twoStudentRun();
        AnswerGroupService first = new AnswerGroupService(repository, saving);
        first.setLabel(saved, first.listGroups(saved).get(0), "off by one");
        first.setChecked(saved, first.listGroups(saved).get(0), true);
        check(first.getSaveProblem().isEmpty(), "saved without a problem: "
                + first.getSaveProblem());
        repository.close();
        SqliteResultRepository reopened = new SqliteResultRepository(file.toString());
        reopened.open();
        AnswerGroup loaded = new AnswerGroupService(reopened, saving)
                .listGroups(saved).get(0);
        check(loaded.getLabel().equals("off by one") && loaded.isChecked(),
                "name and tick survive a restart");
        AnswerGroupService clearing = new AnswerGroupService(reopened, saving);
        clearing.setLabel(saved, clearing.listGroups(saved).get(0), "");
        clearing.setChecked(saved, clearing.listGroups(saved).get(0), false);
        check(reopened.listAnswerGroupNotes(saved.getId()).isEmpty(),
                "an empty note removes the row");

        // saveProblemReported
        AnswerGroupService failing = new AnswerGroupService(reopened, saving);
        failing.listGroups(saved);
        reopened.close();
        failing.setLabel(saved, failing.listGroups(saved).get(0), "kept anyway");
        check(failing.listGroups(saved).get(0).getLabel().equals("kept anyway")
                && !failing.getSaveProblem().isEmpty(),
                "a failed save keeps the name for the session and says why");
        Files.deleteIfExists(file);
    }

    static void writeAnswer(Path root, String username, String code) throws Exception {
        Path folder = root.resolve(username).resolve("Q2");
        Files.createDirectories(folder);
        Files.writeString(folder.resolve("Q2b.java"), code);
    }

    private static void check(boolean condition, String message) {
        if (!condition) throw new AssertionError(message);
        checks++;
    }
}
