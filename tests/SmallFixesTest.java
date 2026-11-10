/*
 * File: SmallFixesTest.java
 *
 * Purpose: Checks a few small fixes: the identical copy finder reads
 * "Email ID : x" as the header validator does and tells email domains
 * apart, a score sheet with a byte order mark exports, a student left
 * RUNNING counts as waiting, and adding a section twice keeps one.
 *
 * Where it fits: Run by hand after compiling, as tests/README.md says.
 * It works on a copy of the fixed test settings and temporary files, so no
 * real setting or file is changed.
 */
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.time.LocalDateTime;
import java.util.ArrayList;
import java.util.List;

import gradezzy.config.AppConfig;
import gradezzy.config.ModuleSections;
import gradezzy.model.Assessment;
import gradezzy.model.GradingRun;
import gradezzy.model.Question;
import gradezzy.model.QuestionResult;
import gradezzy.model.Section;
import gradezzy.model.Submission;
import gradezzy.model.SubmissionStatus;
import gradezzy.plagiarism.IdenticalCopyFinder;
import gradezzy.plagiarism.SimilarityPair;
import gradezzy.report.CsvScoreSheetExporter;
import gradezzy.ui.flow.SectionProgress;
import gradezzy.util.FileUtils;

/**
 * Runs the small fix checks and prints how many passed. Compile:
 * javac -cp classes -d /tmp/gradezzy-tests tests/*.java; run:
 * java -cp "classes:lib/*:/tmp/gradezzy-tests" SmallFixesTest
 */
public class SmallFixesTest {

    // The question every student answers
    private static final String QUESTION = "Q1";
    private static final String FILE_NAME = "Q1.java";

    // The mark some editors put at the very start of a UTF-8 file
    private static final String BYTE_ORDER_MARK = "﻿";

    // The code every student hands in
    private static final String CODE = "public class Q1 {\n"
            + "    public static void main(String[] args) {\n"
            + "        int total = 0;\n"
            + "        for (int i = 0; i < 10; i++) {\n"
            + "            total += i * i;\n"
            + "        }\n"
            + "        System.out.println(total);\n"
            + "    }\n}\n";

    // The section every student is in
    private static final Section SECTION = new Section("CS102", "G1");

    // The number of checks passed so far
    private static int checks = 0;

    /**
     * Runs every check in a temporary folder, then removes the folder.
     *
     * @param args not used
     * @throws Exception if a check fails or a file cannot be written
     */
    public static void main(String[] args) throws Exception {
        Path root = Files.createTempDirectory("gradezzy-small-tests-")
                .toRealPath();
        try {
            Path configCopy = root.resolve("config.properties");
            TestConfig.copyTo(configCopy);
            AppConfig config = new AppConfig(configCopy.toString());
            Assessment assessment = new Assessment("Small Test", "CS102");
            assessment.addQuestion(new Question(QUESTION, QUESTION,
                    FILE_NAME, 10));

            checkIdenticalCopies(root, config, assessment);
            checkByteOrderMark(root, config, assessment);
            checkWaiting(assessment);
            checkModuleSections();
            System.out.println("Passed " + checks + " small fix checks.");
        } finally {
            FileUtils.deleteDirectory(root);
        }
    }

    // Checks the header forms and email comparisons of the identical
    // copy finder.
    private static void checkIdenticalCopies(Path root, AppConfig config,
            Assessment assessment) throws Exception {
        IdenticalCopyFinder finder = new IdenticalCopyFinder(config);

        List<Submission> spaced = new ArrayList<>();
        spaced.add(writeStudent(root, "sp1", "Name : Cy Ng",
                "Email ID : cy@x"));
        spaced.add(writeStudent(root, "sp2", "Name : Cy Ng",
                "Email ID : cy@x"));
        SimilarityPair spacedPair = findPair(finder.find(spaced, assessment),
                "sp1", "sp2");
        check(spacedPair != null && spacedPair.isIdentical(),
                "\"Email ID : x\" is read, so the identical copy is found");

        List<Submission> domains = new ArrayList<>();
        domains.add(writeStudent(root, "dm1", "Name: Bo",
                "Email ID: bo@smu.edu.sg"));
        domains.add(writeStudent(root, "dm2", "Name: Bo",
                "Email ID: bo@gmail.com"));
        check(findPair(finder.find(domains, assessment), "dm1", "dm2")
                == null, "bo@smu.edu.sg and bo@gmail.com are not the same "
                + "Email ID");

        List<Submission> cases = new ArrayList<>();
        cases.add(writeStudent(root, "ca1", "Name: Al",
                "Email ID: Al@SMU.edu.sg "));
        cases.add(writeStudent(root, "ca2", "Name: Al",
                "Email ID: al@smu.edu.sg"));
        check(findPair(finder.find(cases, assessment), "ca1", "ca2")
                != null, "Email IDs are compared trimmed and in lower case");

        List<Submission> noDomain = new ArrayList<>();
        noDomain.add(writeStudent(root, "nd1", "Name: Di",
                "Email ID: di"));
        noDomain.add(writeStudent(root, "nd2", "Name: Di",
                "Email ID: di@smu.edu.sg"));
        check(findPair(finder.find(noDomain, assessment), "nd1", "nd2")
                != null, "With no domain on one side the usernames match");
    }

    // Checks that a score sheet with a byte order mark before its first
    // column exports, and keeps the mark.
    private static void checkByteOrderMark(Path root, AppConfig config,
            Assessment assessment) throws Exception {
        String template = BYTE_ORDER_MARK + "Username,OrgDefinedId,"
                + "Last Name,First Name,Email,"
                + "Calculated Final Grade Numerator\n#ann,#1,L,A,a@x,\n";
        Path sheet = root.resolve("bom.csv");
        Files.write(sheet, template.getBytes(StandardCharsets.UTF_8));
        GradingRun run = new GradingRun("RUN-BOM", assessment, SECTION,
                LocalDateTime.now());
        run.setScoreSheetFile(sheet);
        Submission ann = new Submission("ann", SECTION, sheet);
        ann.setStatus(SubmissionStatus.GRADED);
        QuestionResult result = new QuestionResult(
                assessment.getQuestions().get(0));
        result.setScore(7);
        ann.addQuestionResult(result);
        run.addSubmission(ann);

        Path output = root.resolve("bom-out.csv");
        boolean exported = true;
        try {
            new CsvScoreSheetExporter(config, sheet).export(run, output);
        } catch (Exception exception) {
            System.out.println("   " + exception.getMessage());
            exported = false;
        }
        check(exported, "A score sheet with a byte order mark exports");
        String text = "";
        if (exported) {
            text = new String(Files.readAllBytes(output),
                    StandardCharsets.UTF_8);
        }
        check(text.startsWith(BYTE_ORDER_MARK + "Username,")
                && text.indexOf(BYTE_ORDER_MARK, 1) < 0,
                "The byte order mark is written back once");
        check(text.contains("#ann,#1,L,A,a@x,7"),
                "The student's mark is filled in");
    }

    // Checks that a student left RUNNING counts as waiting.
    private static void checkWaiting(Assessment assessment) {
        GradingRun run = new GradingRun("RUN-WAIT", assessment, SECTION,
                LocalDateTime.now());
        String[] users = {"pen", "run", "can", "gra", "fai"};
        SubmissionStatus[] statuses = {SubmissionStatus.PENDING,
            SubmissionStatus.RUNNING, SubmissionStatus.CANCELLED,
            SubmissionStatus.GRADED, SubmissionStatus.FAILED};
        for (int i = 0; i < users.length; i++) {
            Submission submission = new Submission(users[i], SECTION,
                    Path.of(users[i] + ".zip"));
            submission.setStatus(statuses[i]);
            run.addSubmission(submission);
        }
        check(SectionProgress.countWaiting(run) == 3,
                "Pending, running and cancelled students are waiting");
    }

    // Checks that adding a section a module has already keeps one.
    private static void checkModuleSections() {
        check(ModuleSections.add("CS102:G5", "CS102", "G5")
                .equals("CS102:G5"), "Adding G5 again keeps one G5");
        check(ModuleSections.add("CS102:G5", "cs102", "g5")
                .equals("CS102:G5"), "Adding g5 to cs102 keeps one G5");
        check(ModuleSections.add("CS102:G5", "CS102", "G6")
                .equals("CS102:G5|G6"), "A new section is still added");
        check(ModuleSections.add("", "IS442", " G1 ")
                .equals("IS442:G1"), "A new module is still added");
    }

    // Writes one student's Q1.java with the given header lines and
    // returns their submission.
    private static Submission writeStudent(Path root, String username,
            String nameLine, String emailLine) throws Exception {
        Path folder = root.resolve("students").resolve(username);
        String source = "/*\n * " + nameLine + "\n * " + emailLine
                + "\n */\n" + CODE;
        Path file = folder.resolve(QUESTION).resolve(FILE_NAME);
        Files.createDirectories(file.getParent());
        Files.write(file, source.getBytes(StandardCharsets.UTF_8));
        Submission submission = new Submission(username, SECTION, folder);
        submission.setWorkingCopyPath(folder);
        return submission;
    }

    // Returns the pair of two students, in either order, or null.
    private static SimilarityPair findPair(List<SimilarityPair> pairs,
            String firstUsername, String secondUsername) {
        SimilarityPair found = null;
        for (SimilarityPair pair : pairs) {
            boolean sameOrder = pair.getFirstUsername().equals(firstUsername)
                    && pair.getSecondUsername().equals(secondUsername);
            boolean otherOrder = pair.getFirstUsername()
                    .equals(secondUsername)
                    && pair.getSecondUsername().equals(firstUsername);
            if (sameOrder || otherOrder) {
                found = pair;
            }
        }
        return found;
    }

    // Counts a passed check, or stops with the reason it failed.
    private static void check(boolean passed, String description) {
        if (!passed) {
            System.out.println("FAILED: " + description);
            System.exit(1);
        }
        checks++;
    }
}
