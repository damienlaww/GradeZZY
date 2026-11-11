/*
 * File: SimilarityTest.java
 *
 * Purpose: Checks the similar code check: honest answers that follow
 * the same plan in their own words are not flagged, a copy with every
 * name changed and a method moved is, and a change of
 * plagiarism.algorithm takes effect at the next check without a
 * restart.
 *
 * Where it fits: Run by hand after compiling, as tests/README.md says.
 * It works on a copy of the fixed test settings and temporary student
 * folders, so no real setting or file is changed.
 */
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.time.LocalDateTime;
import java.util.ArrayList;
import java.util.List;

import gradezzy.config.AppConfig;
import gradezzy.config.ConfigKeys;
import gradezzy.model.Assessment;
import gradezzy.model.GradingRun;
import gradezzy.model.Question;
import gradezzy.model.Section;
import gradezzy.model.Submission;
import gradezzy.model.SubmissionStatus;
import gradezzy.plagiarism.ChosenSimilarityDetector;
import gradezzy.plagiarism.SimilarityDetector;
import gradezzy.plagiarism.SimilarityPair;
import gradezzy.plagiarism.TokenSimilarityDetector;
import gradezzy.plagiarism.WinnowingSimilarityDetector;
import gradezzy.service.PlagiarismService;
import gradezzy.service.RunSettings;
import gradezzy.util.FileUtils;

/**
 * Runs the similar code checks and prints how many passed. Compile:
 * javac -cp classes -d /tmp/gradezzy-tests tests/*.java; run:
 * java -cp "classes:lib/*:/tmp/gradezzy-tests" SimilarityTest
 */
public class SimilarityTest {

    // The question every student answers
    private static final String QUESTION = "Q1";
    private static final String FILE_NAME = "Q1.java";

    // A model answers folder chosen for one run
    private static final String CHOSEN_ANSWERS = "chosen/answers";

    // The plagiarism.algorithm values
    private static final String WINNOWING = "winnowing";
    private static final String TRIGRAMS =
            ChosenSimilarityDetector.TRIGRAM_ALGORITHM;

    // The students who wrote the common plan, each with their own names
    private static final String[] HONEST = {"ann.lee", "ben.ong",
        "cara.tan", "dev.raj", "eve.ng"};

    // The names each honest student chose, in the order the plan uses
    // them: the array, the sum, the smallest, the largest, the loop
    // value, the count above the mean and the result
    private static final String[][] HONEST_NAMES = {
        {"numbers", "sum", "lowest", "highest", "value", "above", "result"},
        {"arr", "total", "min", "max", "x", "count", "answer"},
        {"data", "acc", "small", "big", "item", "over", "out"},
        {"list", "s", "lo", "hi", "n", "c", "res"},
        {"marks", "running", "least", "most", "mark", "high", "summary"}};

    // The student with a plan no one else used, and the student who
    // copied it with every name changed and the helper moved first
    private static final String ORIGINAL = "fay.koh";
    private static final String RENAMED_COPY = "gus.lim";

    // A student who handed in an honest student's answer as it was
    private static final String WORD_FOR_WORD = "hal.wu";

    // The starter file: a header, the class and a main to keep
    private static final String STARTER = "/*\n * Name:\n * Email ID:\n"
            + " */\npublic class Q1 {\n    // insert your code here\n\n"
            + "    public static void main(String[] args) {\n"
            + "        System.out.println(\"Q1\");\n    }\n}\n";

    // The common plan; %1$s to %7$s are the student's own names
    private static final String COMMON_PLAN =
            "    public static int[] summarise(int[] %1$s) {\n"
            + "        int %2$s = 0;\n"
            + "        int %3$s = Integer.MAX_VALUE;\n"
            + "        int %4$s = Integer.MIN_VALUE;\n"
            + "        for (int %5$s : %1$s) {\n"
            + "            %2$s += %5$s;\n"
            + "            if (%5$s < %3$s) {\n"
            + "                %3$s = %5$s;\n"
            + "            }\n"
            + "            if (%5$s > %4$s) {\n"
            + "                %4$s = %5$s;\n"
            + "            }\n"
            + "        }\n"
            + "        int %6$s = 0;\n"
            + "        for (int %5$s : %1$s) {\n"
            + "            if (%5$s * %1$s.length > %2$s) {\n"
            + "                %6$s++;\n"
            + "            }\n"
            + "        }\n"
            + "        int[] %7$s = {%2$s, %3$s, %4$s, %6$s};\n"
            + "        return %7$s;\n"
            + "    }\n";

    // The main method of the unusual plan; %1$s to %7$s are the names
    private static final String UNUSUAL_MAIN =
            "    public static int[] summarise(int[] %1$s) {\n"
            + "        int[] %2$s = %7$s(%1$s);\n"
            + "        long %3$s = 0L;\n"
            + "        int %4$s = 0;\n"
            + "        while (%4$s < %2$s.length) {\n"
            + "            %3$s = %3$s + %2$s[%4$s];\n"
            + "            %4$s = %4$s + 1;\n"
            + "        }\n"
            + "        int %5$s = 0;\n"
            + "        int %6$s = %2$s.length - 1;\n"
            + "        while (%6$s >= 0\n"
            + "                && (long) %2$s[%6$s] * %2$s.length > %3$s) {\n"
            + "            %5$s = %5$s + 1;\n"
            + "            %6$s = %6$s - 1;\n"
            + "        }\n"
            + "        return new int[] {(int) %3$s, %2$s[0],\n"
            + "                %2$s[%2$s.length - 1], %5$s};\n"
            + "    }\n";

    // The helper of the unusual plan; %1$s to %3$s are the names
    private static final String UNUSUAL_HELPER =
            "    private static int[] %3$s(int[] %1$s) {\n"
            + "        int[] %2$s = new int[%1$s.length];\n"
            + "        for (int i = 0; i < %1$s.length; i++) {\n"
            + "            %2$s[i] = %1$s[i];\n"
            + "        }\n"
            + "        java.util.Arrays.sort(%2$s);\n"
            + "        return %2$s;\n"
            + "    }\n";

    // The number of checks passed so far
    private static int checks = 0;

    /**
     * Runs every check in a temporary folder, then removes the folder.
     *
     * @param args not used
     * @throws Exception if a check fails or a file cannot be written
     */
    public static void main(String[] args) throws Exception {
        Path root = Files.createTempDirectory("gradezzy-similar-tests-")
                .toRealPath();
        try {
            AppConfig config = TestConfig.copyInto(root);
            config.setString(ConfigKeys.PLAGIARISM_ENABLED, "true");
            config.setString(ConfigKeys.PLAGIARISM_THRESHOLD, "0.80");
            config.setString(ConfigKeys.PLAGIARISM_MIN_LINES, "10");
            config.setString(ConfigKeys.PLAGIARISM_ALGORITHM, WINNOWING);
            config.setString(ConfigKeys.DATABASE_ENABLED, "false");

            Path starter = root.resolve("starter");
            write(starter.resolve(QUESTION).resolve(FILE_NAME), STARTER);
            List<Submission> submissions = writeStudents(root);
            Assessment assessment = new Assessment("Similar Test", "CS102");
            assessment.addQuestion(new Question(QUESTION, QUESTION,
                    FILE_NAME, 10));

            checkWinnowing(config, submissions, assessment, starter);
            checkTrigrams(config, submissions, assessment, starter);
            checkChosenDetector(config, submissions, assessment, starter);
            checkRecheck(config, submissions, assessment, starter);
            checkRunSettings(config);
            System.out.println("Passed " + checks + " similarity checks.");
        } finally {
            FileUtils.deleteDirectory(root);
        }
    }

    // Checks that winnowing flags the renamed copy and the word for word
    // copy, and no pair of honest students.
    private static void checkWinnowing(AppConfig config,
            List<Submission> submissions, Assessment assessment,
            Path starter) {
        SimilarityDetector detector = new WinnowingSimilarityDetector(config);
        List<SimilarityPair> pairs = detector.findSimilar(submissions,
                assessment, starter);

        check(hasPair(pairs, ORIGINAL, RENAMED_COPY), "Winnowing flags "
                + "a copy with every name changed and a method moved");
        check(hasPair(pairs, HONEST[0], WORD_FOR_WORD),
                "Winnowing flags a word for word copy");
        check(countHonestPairs(pairs) == 0, "Winnowing flags no pair of "
                + "honest answers that follow the same plan in their own "
                + "words, found " + countHonestPairs(pairs));
        check(pairs.size() == 2, "Winnowing flags only the two copies, "
                + "found " + pairs.size());
    }

    // Checks that trigrams flag the word for word copy, but not the
    // renamed copy or the honest answers.
    private static void checkTrigrams(AppConfig config,
            List<Submission> submissions, Assessment assessment,
            Path starter) {
        SimilarityDetector detector = new TokenSimilarityDetector(config);
        List<SimilarityPair> pairs = detector.findSimilar(submissions,
                assessment, starter);

        check(hasPair(pairs, HONEST[0], WORD_FOR_WORD),
                "Trigrams flag a word for word copy");
        check(!hasPair(pairs, ORIGINAL, RENAMED_COPY),
                "Trigrams do not see through renamed names");
        check(countHonestPairs(pairs) == 0,
                "Trigrams flag no pair of honest answers");
    }

    // Checks that the detector the app uses follows plagiarism.algorithm
    // at each check, without being made again.
    private static void checkChosenDetector(AppConfig config,
            List<Submission> submissions, Assessment assessment,
            Path starter) {
        SimilarityDetector detector = new ChosenSimilarityDetector(config);

        config.setString(ConfigKeys.PLAGIARISM_ALGORITHM, WINNOWING);
        check(WinnowingSimilarityDetector.NAME.equals(detector.getName()),
                "The chosen detector is winnowing when the setting says so");
        check(hasPair(detector.findSimilar(submissions, assessment,
                starter), ORIGINAL, RENAMED_COPY),
                "With winnowing chosen, the renamed copy is flagged");

        config.setString(ConfigKeys.PLAGIARISM_ALGORITHM, TRIGRAMS);
        check(TokenSimilarityDetector.NAME.equals(detector.getName()),
                "The same detector is trigrams once the setting changes");
        check(!hasPair(detector.findSimilar(submissions, assessment,
                starter), ORIGINAL, RENAMED_COPY),
                "With trigrams chosen, the renamed copy is not flagged");

        config.setString(ConfigKeys.PLAGIARISM_ALGORITHM, WINNOWING);
        check(hasPair(detector.findSimilar(submissions, assessment,
                starter), ORIGINAL, RENAMED_COPY),
                "Back to winnowing, the renamed copy is flagged again");
    }

    // Checks that checking a run again uses the settings of now, and
    // that the run's recorded settings then say so.
    private static void checkRecheck(AppConfig config,
            List<Submission> submissions, Assessment assessment,
            Path starter) {
        config.setString(ConfigKeys.PLAGIARISM_ALGORITHM, WINNOWING);
        GradingRun run = new GradingRun("RUN-SIMILAR", assessment,
                new Section("CS102", "G1"), LocalDateTime.now());
        run.setStarterDirectory(starter);
        run.setGradingSettings(RunSettings.capture(config));
        for (Submission submission : submissions) {
            submission.setStatus(SubmissionStatus.GRADED);
            run.addSubmission(submission);
        }
        PlagiarismService service = new PlagiarismService(config,
                new ChosenSimilarityDetector(config), null);

        List<SimilarityPair> first = service.check(run);
        check(hasPair(first, ORIGINAL, RENAMED_COPY),
                "The first check, with winnowing, flags the renamed copy");

        config.setString(ConfigKeys.PLAGIARISM_ALGORITHM, TRIGRAMS);
        List<SimilarityPair> second = service.check(run);
        check(!hasPair(second, ORIGINAL, RENAMED_COPY), "Checking again "
                + "after choosing trigrams uses trigrams, no restart");
        check(hasPair(second, HONEST[0], WORD_FOR_WORD),
                "Checking again still flags the word for word copy");
        String recorded = RunSettings.valueOf(run.getGradingSettings(),
                ConfigKeys.PLAGIARISM_ALGORITHM);
        check(TRIGRAMS.equals(recorded), "The run records the algorithm "
                + "its last check used, found " + recorded);
        String timeout = RunSettings.valueOf(run.getGradingSettings(),
                ConfigKeys.RUN_TIMEOUT_SECONDS);
        check(config.getString(ConfigKeys.RUN_TIMEOUT_SECONDS)
                .equals(timeout), "The run keeps its other settings");
        config.setString(ConfigKeys.PLAGIARISM_ALGORITHM, WINNOWING);
    }

    // Checks that a run that kept no settings is left without them.
    private static void checkRunSettings(AppConfig config) {
        String[] keys = {ConfigKeys.PLAGIARISM_ALGORITHM};
        check("".equals(RunSettings.update("", config, keys)),
                "A run that kept no settings is not given some");
        String added = RunSettings.update("a.key=1", config, keys);
        check(WINNOWING.equals(RunSettings.valueOf(added,
                ConfigKeys.PLAGIARISM_ALGORITHM))
                && "1".equals(RunSettings.valueOf(added, "a.key")),
                "A setting the run did not keep is added after the rest");
        String answers = RunSettings.put(added,
                ConfigKeys.MODEL_ANSWERS_DIR, CHOSEN_ANSWERS);
        check(CHOSEN_ANSWERS.equals(RunSettings.valueOf(answers,
                ConfigKeys.MODEL_ANSWERS_DIR))
                && "1".equals(RunSettings.valueOf(answers, "a.key")),
                "A folder chosen for a run is written into its settings");
        check("".equals(RunSettings.put("", ConfigKeys.MODEL_ANSWERS_DIR,
                CHOSEN_ANSWERS)),
                "A run that kept no settings is not given a folder");
    }

    // Writes every student's answer and returns their submissions.
    private static List<Submission> writeStudents(Path root)
            throws Exception {
        Section section = new Section("CS102", "G1");
        List<Submission> submissions = new ArrayList<>();
        for (int i = 0; i < HONEST.length; i++) {
            String answer = String.format(COMMON_PLAN,
                    (Object[]) HONEST_NAMES[i]);
            submissions.add(writeStudent(root, section, HONEST[i], answer));
        }

        String original = String.format(UNUSUAL_MAIN, "values", "sorted",
                "total", "index", "above", "position", "copySorted")
                + "\n" + String.format(UNUSUAL_HELPER, "values", "copy",
                "copySorted");
        submissions.add(writeStudent(root, section, ORIGINAL, original));

        String renamed = String.format(UNUSUAL_HELPER, "input", "dup",
                "orderedCopy") + "\n" + String.format(UNUSUAL_MAIN, "input",
                "ordered", "acc", "k", "bigOnes", "back", "orderedCopy");
        submissions.add(writeStudent(root, section, RENAMED_COPY, renamed));

        String copied = String.format(COMMON_PLAN,
                (Object[]) HONEST_NAMES[0]);
        submissions.add(writeStudent(root, section, WORD_FOR_WORD, copied));
        return submissions;
    }

    // Writes one student's Q1.java with their own header around an
    // answer, and returns their submission.
    private static Submission writeStudent(Path root, Section section,
            String username, String answer) throws Exception {
        Path folder = root.resolve("students").resolve(username);
        String source = "/*\n * Name: " + username + "\n * Email ID: "
                + username + "@smu.edu.sg\n */\npublic class Q1 {\n"
                + answer + "\n    public static void main(String[] args) "
                + "{\n        System.out.println(\"Q1\");\n    }\n}\n";
        write(folder.resolve(QUESTION).resolve(FILE_NAME), source);
        Submission submission = new Submission(username, section, folder);
        submission.setWorkingCopyPath(folder);
        return submission;
    }

    // Writes a text file, making its folder first.
    private static void write(Path file, String text) throws Exception {
        Files.createDirectories(file.getParent());
        Files.write(file, text.getBytes(StandardCharsets.UTF_8));
    }

    // Says whether two students are flagged as a pair, in either order.
    private static boolean hasPair(List<SimilarityPair> pairs,
            String firstUsername, String secondUsername) {
        for (SimilarityPair pair : pairs) {
            boolean sameOrder = pair.getFirstUsername().equals(firstUsername)
                    && pair.getSecondUsername().equals(secondUsername);
            boolean otherOrder = pair.getFirstUsername()
                    .equals(secondUsername)
                    && pair.getSecondUsername().equals(firstUsername);
            if (sameOrder || otherOrder) {
                return true;
            }
        }
        return false;
    }

    // Counts the flagged pairs of two honest students.
    private static int countHonestPairs(List<SimilarityPair> pairs) {
        int count = 0;
        for (SimilarityPair pair : pairs) {
            if (isHonest(pair.getFirstUsername())
                    && isHonest(pair.getSecondUsername())) {
                count++;
            }
        }
        return count;
    }

    // Says whether a student wrote the common plan alone.
    private static boolean isHonest(String username) {
        for (String honest : HONEST) {
            if (honest.equals(username)) {
                return true;
            }
        }
        return false;
    }

    // Counts a check that passed, or stops with its description.
    private static void check(boolean passed, String description) {
        if (!passed) {
            throw new AssertionError("Failed: " + description);
        }
        checks++;
    }
}
