/*
 * File: ClassFilesTest.java
 *
 * Purpose: Regression checks for class files the assessment forgot to
 * provide: the working copy keeps a student's class file that has no
 * source, the tester check on Set up names the classes nobody provides,
 * and a run warns when a question did not compile for nearly everyone.
 *
 * Where it fits: Run by hand after compiling, as tests/README.md says.
 * The tester check runs javac on a tiny tester; no student code runs.
 */
import java.nio.file.Files;
import java.nio.file.Path;
import java.time.LocalDateTime;
import java.util.List;

import gradezzy.config.AppConfig;
import gradezzy.config.ConfigKeys;
import gradezzy.exec.ProcessRunner;
import gradezzy.exec.SourceCompiler;
import gradezzy.grading.CompileStage;
import gradezzy.ingest.WorkingCopyManager;
import gradezzy.ingest.ZipExtractor;
import gradezzy.model.Anomaly;
import gradezzy.model.Assessment;
import gradezzy.model.GradingRun;
import gradezzy.model.Question;
import gradezzy.model.QuestionResult;
import gradezzy.model.Section;
import gradezzy.model.Submission;
import gradezzy.model.SubmissionStatus;
import gradezzy.service.CompileFailureCheck;
import gradezzy.util.FileUtils;

/**
 * Runs the class file checks and prints how many passed.
 */
public class ClassFilesTest {

    // The section every run is for
    private static final Section SECTION = new Section("CS102", "G9");

    // The bytes written as a class file the checks never load
    private static final byte[] FAKE_CLASS = {1, 2, 3};

    // How many students the compile failure runs grade
    private static final int STUDENTS = 6;

    // The number of checks passed so far
    private static int checks = 0;

    /**
     * Runs every check in a temporary folder, then removes the folder.
     *
     * @param args not used
     * @throws Exception if a check fails or a file cannot be written
     */
    public static void main(String[] args) throws Exception {
        Path root = Files.createTempDirectory("gradezzy-class-tests-")
                .toRealPath();
        try {
            AppConfig config = TestConfig.copyInto(root);
            config.setString(ConfigKeys.WORKING_DIR,
                    root.resolve("working").toString());
            config.setString(ConfigKeys.TESTER_CLASS_SUFFIX, "Tester");
            checkWorkingCopy(root, config, createAssessment());
            checkTesterFolder(root, config);
            checkCompileFailures();
            System.out.println("Passed " + checks + " class file checks.");
        } finally {
            FileUtils.deleteDirectory(root);
        }
    }

    // Builds an assessment like Lab Test 3: Q2a in Q2, Q3 and Q4.
    private static Assessment createAssessment() {
        Assessment assessment = new Assessment("Lab Test 3", "CS102");
        assessment.addQuestion(new Question("Q2a", "Q2", "Q2a.java", 4));
        assessment.addQuestion(new Question("Q3", "Q3", "Q3.java", 6));
        assessment.addQuestion(new Question("Q4", "Q4", "Q4.java", 4));
        return assessment;
    }

    // A class file with no source anywhere is kept; one with source in
    // the copy or the starter, a main class, a tester and stale IDE
    // output are deleted; a provided one is kept.
    private static void checkWorkingCopy(Path root, AppConfig config,
            Assessment assessment) throws Exception {
        Path starter = root.resolve("starter");
        write(starter.resolve("Q3/GoldMember.java"), "class GoldMember {}");
        config.setString(ConfigKeys.STARTER_DIR, starter.toString());
        config.setString(ConfigKeys.PROVIDED_CLASS_FILES, "Shape.class");

        Path copy = root.resolve("working/alice");
        Path q2 = copy.resolve("Q2");
        writeClass(q2.resolve("Order.class"));
        writeClass(q2.resolve("Order$1.class"));
        writeClass(q2.resolve("InvalidOrderException.class"));
        write(q2.resolve("Q2a.java"), "public class Q2a {}");
        writeClass(q2.resolve("Q2a.class"));
        write(q2.resolve("Helper.java"), "class Helper {}");
        writeClass(q2.resolve("Helper.class"));
        writeClass(q2.resolve("Helper$Inner.class"));
        writeClass(q2.resolve("Q2aTester.class"));
        writeClass(q2.resolve("Shape.class"));
        writeClass(copy.resolve("Q3/GoldMember.class"));
        writeClass(copy.resolve("Q3/Member.class"));
        Path out = copy.resolve("out/production/alice/Q4");
        writeClass(out.resolve("Q4.class"));
        writeClass(out.resolve("TaskComparator.class"));
        write(copy.resolve("src/Q4/TaskComparator.java"),
                "class TaskComparator {}");

        WorkingCopyManager manager = new WorkingCopyManager(config,
                new ZipExtractor(config));
        manager.removeStudentClassFiles(copy, assessment, starter);

        check(Files.exists(q2.resolve("Order.class")),
                "Order.class without source is kept");
        check(Files.exists(q2.resolve("Order$1.class")),
                "Order$1.class belongs to Order, so it is kept too");
        check(Files.exists(q2.resolve("InvalidOrderException.class")),
                "InvalidOrderException.class without source is kept");
        check(Files.exists(copy.resolve("Q3/Member.class")),
                "Member.class without source is kept");
        check(!Files.exists(q2.resolve("Q2a.class")),
                "Q2a.class with Q2a.java is deleted");
        check(!Files.exists(q2.resolve("Helper.class"))
                && !Files.exists(q2.resolve("Helper$Inner.class")),
                "Helper.class and Helper$Inner.class with Helper.java "
                + "are deleted");
        check(!Files.exists(q2.resolve("Q2aTester.class")),
                "a precompiled tester is always deleted");
        check(Files.exists(q2.resolve("Shape.class")),
                "a provided class file is kept");
        check(!Files.exists(copy.resolve("Q3/GoldMember.class")),
                "GoldMember.class is deleted: the starter has its source, "
                + "so the student must hand in GoldMember.java");
        check(!Files.exists(out.resolve("Q4.class")),
                "a main class without source is deleted");
        check(!Files.exists(out.resolve("TaskComparator.class")),
                "stale IDE output whose source is elsewhere in the copy "
                + "is deleted");
    }

    // The tester check names the classes the starter and testers use
    // that neither folder has, and says nothing once the starter has
    // them.
    private static void checkTesterFolder(Path root, AppConfig config)
            throws Exception {
        Assessment assessment = new Assessment("Lab Test 3", "CS102");
        assessment.addQuestion(new Question("Q2a", "Q2", "Q2a.java", 1));
        Path testers = root.resolve("testers");
        write(testers.resolve("Q2aTester.java"), "public class Q2aTester "
                + "extends Q2a {\n    private static double score;\n"
                + "    public static void main(String[] args) {\n"
                + "        Order order = new Order();\n"
                + "        if (count(order) == 1) {\n"
                + "            score += 1;\n        }\n"
                + "        System.out.println(score);\n    }\n}\n");
        Path starter = root.resolve("starter-src");
        write(starter.resolve("Q2/Q2a.java"), "public class Q2a {\n"
                + "    public static int count(Order order) {\n"
                + "        return 0;\n    }\n}\n");
        config.setString(ConfigKeys.STARTER_DIR, starter.toString());
        config.setString(ConfigKeys.PROVIDED_CLASS_FILES, "");

        List<String> problems = checkTesters(config, assessment, testers,
                starter);
        check(mentions(problems, "Order.class"), "a source-only starter "
                + "that uses Order is reported (" + problems + ")");

        Path order = starter.resolve("Q2/Order.java");
        write(order, "public class Order {}");
        Process javac = new ProcessBuilder("javac", order.toString())
                .inheritIO().start();
        check(javac.waitFor() == 0, "Order.java compiles for the check");
        Files.delete(order);
        problems = checkTesters(config, assessment, testers, starter);
        check(!mentions(problems, "Order.class"), "a starter with "
                + "Order.class raises no missing class (" + problems + ")");
    }

    // Runs the Set up tester check on a folder.
    private static List<String> checkTesters(AppConfig config,
            Assessment assessment, Path testers, Path starter)
            throws Exception {
        CompileStage stage = new CompileStage(new SourceCompiler(config,
                new ProcessRunner(config)), starter);
        return stage.checkTesterFolder(config, assessment, testers);
    }

    // A question that did not compile for 5 of 6 students is warned
    // about once per student; 4 of 6 is not.
    private static void checkCompileFailures() {
        Assessment assessment = createAssessment();
        Question q3 = assessment.getQuestions().get(1);
        GradingRun run = new GradingRun("RUN-compile", assessment, SECTION,
                LocalDateTime.now());
        for (int i = 0; i < STUDENTS; i++) {
            Submission submission = new Submission("student" + i, SECTION,
                    Path.of("student" + i + ".zip"));
            submission.setStatus(SubmissionStatus.GRADED);
            QuestionResult result = new QuestionResult(q3);
            result.setCompiled(i == 0);
            submission.addQuestionResult(result);
            run.addSubmission(submission);
        }
        CompileFailureCheck failureCheck = new CompileFailureCheck();
        List<String> warnings = failureCheck.findWarnings(run);
        check(warnings.size() == 1 && warnings.get(0).startsWith(
                "Q3 did not compile for 5 of 6 graded students"),
                "Q3 failing for 5 of 6 is warned about (" + warnings + ")");
        failureCheck.refresh(run);
        failureCheck.refresh(run);
        check(countWarnings(run.getSubmissions().get(0)) == 1
                && countWarnings(run.getSubmissions().get(STUDENTS - 1))
                == 1, "every graded student gets the warning once, even "
                + "after two saves");

        run.getSubmissions().get(1).findResult("Q3").setCompiled(true);
        failureCheck.refresh(run);
        check(countWarnings(run.getSubmissions().get(0)) == 0,
                "4 of 6 is no warning, and the old one is removed");
    }

    // Counts a student's warnings from the compile failure check.
    private static int countWarnings(Submission submission) {
        int count = 0;
        for (Anomaly anomaly : submission.getAnomalies()) {
            if (CompileFailureCheck.ACTION.equals(
                    anomaly.getSuggestedAction())) {
                count++;
            }
        }
        return count;
    }

    // Says whether any problem contains the text.
    private static boolean mentions(List<String> problems, String text) {
        boolean found = false;
        for (String problem : problems) {
            found = found || problem.contains(text);
        }
        return found;
    }

    // Writes a text file, making its folders.
    private static void write(Path file, String text) throws Exception {
        Files.createDirectories(file.getParent());
        Files.writeString(file, text);
    }

    // Writes a fake class file, making its folders.
    private static void writeClass(Path file) throws Exception {
        Files.createDirectories(file.getParent());
        Files.write(file, FAKE_CLASS);
    }

    // Counts a passed check, or stops with the reason.
    private static void check(boolean passed, String description) {
        if (!passed) {
            throw new AssertionError("Failed: " + description);
        }
        checks++;
    }
}
