/*
 * File: DemoData.java
 *
 * Purpose: Builds the made-up students, runs and files that demo mode shows.
 *
 * Where it fits: Used by DemoBackend and the checks in gradezzy.ui.check.
 * Every name and line of code is invented; the real samples hold real names.
 */
package gradezzy.ui.demo;

import java.nio.file.Path;
import java.nio.file.Paths;
import java.time.LocalDateTime;
import java.util.ArrayList;
import java.util.List;
import java.util.Random;

import gradezzy.model.Anomaly;
import gradezzy.model.AnomalyType;
import gradezzy.model.Assessment;
import gradezzy.model.FixStatus;
import gradezzy.model.GradingRun;
import gradezzy.model.Question;
import gradezzy.model.QuestionResult;
import gradezzy.model.Section;
import gradezzy.model.Severity;
import gradezzy.model.Student;
import gradezzy.model.Submission;
import gradezzy.model.SubmissionStatus;
import gradezzy.model.TestCaseResult;

/**
 * Synthetic data for demo mode.
 */
public final class DemoData {

    /** The module shown in demo mode. */
    public static final String MODULE_CODE = "CS102";

    /** The full name of the demo module. */
    public static final String MODULE_NAME = "Programming Fundamentals II";

    /** A second module with no runs, to show empty states. */
    public static final String EMPTY_MODULE_CODE = "DEMO";

    /** The full name of the empty module. */
    public static final String EMPTY_MODULE_NAME = "Empty sample module";

    /** The only section of the empty module. */
    public static final String EMPTY_MODULE_SECTION = "D1";

    /** The section that holds the sample runs. */
    public static final String SAMPLE_SECTION = "G1";

    /** The username whose tester times out in the sample run. */
    public static final String TIMEOUT_USERNAME = "learner.bravo";

    /** The question whose tester times out in the sample run. */
    public static final String TIMEOUT_QUESTION = "Q1a";

    /** The username whose ZIP file cannot be opened. */
    public static final String INVALID_ZIP_USERNAME = "learner.foxtrot";

    /** The username whose repair was approved before the demo starts. */
    public static final String APPROVED_REPAIR_USERNAME = "learner.alpha";

    /** The username whose flags were reviewed before the demo starts. */
    public static final String REVIEWED_USERNAME = "learner.delta";

    /** The first student of the sample similar pair. */
    public static final String SIMILAR_FIRST_USERNAME = "learner.alpha";

    /** The second student of the sample similar pair. */
    public static final String SIMILAR_SECOND_USERNAME = "learner.echo";

    /** The number of students in the large sample cohort. */
    public static final int LARGE_COHORT_SIZE = 240;

    /** The id of the sample run shown at start-up. */
    public static final String CURRENT_RUN_ID = "024";

    /** The id of the earlier sample run on the History screen. */
    public static final String EARLIER_RUN_ID = "023";

    /** When the sample run started. */
    public static final LocalDateTime CURRENT_RUN_STARTED =
            LocalDateTime.of(2026, 10, 15, 9, 42);

    /** When the earlier sample run started. */
    public static final LocalDateTime EARLIER_RUN_STARTED =
            LocalDateTime.of(2026, 10, 14, 16, 5);

    /** The folder name used for every synthetic sample file. */
    public static final String SAMPLE_FOLDER = "demo-samples";

    /** Added after the username in the replacement ZIP file's name. */
    public static final String REPLACEMENT_SUFFIX = "-resubmitted";

    /** The extension of a submission file. */
    public static final String ZIP_EXTENSION = ".zip";

    /** The synthetic score sheet. */
    public static final String SAMPLE_ROSTER = "sample-score-sheet.csv";

    /** The synthetic tester folder. */
    public static final String SAMPLE_TESTERS = "sample-testers";

    /** The synthetic starter folder. */
    public static final String SAMPLE_STARTER = "sample-starter";

    /** The six sample usernames, in grading order. */
    public static final String[] USERNAMES = {"learner.alpha",
        "learner.bravo", "learner.charlie", "learner.delta",
        "learner.echo", "learner.foxtrot"};

    // First and last names of the six sample students
    private static final String[][] NAMES = {{"Alex", "Morgan"},
        {"Blair", "Chen"}, {"Charlie", "Rivera"},
        {"Devan Alexandra-Rose", "Montgomery-Abernathy"},
        {"Ellis", "Tan"}, {"Frankie", "Park"}};

    // Marks per question (Q1a, Q1b, Q2a, Q2b, Q3) of each sample student
    private static final int[][] SCORES = {{3, 3, 5, 5, 4},
        {2, 3, 5, 5, 4}, {3, 3, 5, 0, 2}, {0, 0, 0, 0, 0},
        {1, 0, 3, 4, 2}, {3, 2, 4, 5, 3}};

    // Indexes of the sample students in USERNAMES
    private static final int ALPHA = 0;
    private static final int BRAVO = 1;
    private static final int CHARLIE = 2;
    private static final int DELTA = 3;
    private static final int ECHO = 4;

    // Indexes of the questions in the assessment
    private static final int Q1A = 0;
    private static final int Q1B = 1;
    private static final int Q2A = 2;
    private static final int Q2B = 3;

    // The case of Q1a that hangs or throws in the sample run
    private static final int FAILING_CASE = 3;

    // The number of characters in the long sample log
    private static final int LONG_LOG_CHARACTERS = 100000;

    // The folder inside the sample folder that holds the working copies
    private static final String WORKING_FOLDER = "working";

    // The student whose files sit in long folders, to test long paths
    private static final String LONG_PATH_USERNAME = "learner.charlie";

    // The folders a portal download put the long-path ZIP file in
    private static final String[] LONG_PATH_FOLDERS = {
        "CS102 Lab Test 2026 Term 1", "Section G1 downloads", "attempt 2"};

    // The working-copy folder of the long-path student
    private static final String LONG_WORKING_COPY =
            "learner.charlie-resubmitted-after-extension";

    // The email domain of synthetic students, reserved for examples
    private static final String EMAIL_DOMAIN = "@example.edu";

    // The seed of the large cohort, so it is the same on every run
    private static final long COHORT_SEED = 102L;

    // Every n-th generated student gets one of these outcomes
    private static final int COMPILE_ERROR_EVERY = 23;
    private static final int TIMEOUT_EVERY = 31;
    private static final int UNGRADED_EVERY = 37;
    private static final int FAILED_EVERY = 41;
    private static final int UNKNOWN_STUDENT_EVERY = 43;
    private static final int ZERO_EVERY = 47;
    private static final int MISSING_FOLDER_EVERY = 53;

    // First and last names for generated students
    private static final String[] FIRST_NAMES = {"Avery", "Rowan",
        "Jordan", "Casey", "Morgan", "Riley", "Quinn", "Harper",
        "Emerson", "Sage", "Remy", "Kai", "Noor", "Wren"};
    private static final String[] LAST_NAMES = {"Lim", "Okafor", "Novak",
        "Santos", "Haddad", "Kowalski", "Nguyen", "Fischer", "Patel",
        "Moreau", "Sato", "Ibrahim", "Larsen", "Costa"};

    // Utility class, never instantiated.
    private DemoData() {
        // nothing to set up
    }

    /**
     * Hands out unique anomaly ids.
     */
    public static class IdCounter {

        // The number of ids handed out so far
        private int count = 0;

        /**
         * Returns the next anomaly id.
         *
         * @return an id such as A-0007
         */
        public String next() {
            count++;
            return String.format("A-%04d", count);
        }
    }

    /**
     * Returns the synthetic student behind a username.
     *
     * @param username a sample or generated username
     * @return the student
     */
    public static Student studentFor(String username) {
        int index = indexOf(username);
        if (index >= 0) {
            return new Student("#" + (index + 1), username,
                    NAMES[index][1], NAMES[index][0],
                    username + EMAIL_DOMAIN);
        }
        return new Student("#0", username, "", username, username
                + EMAIL_DOMAIN);
    }

    /**
     * Builds the sample run shown at start-up. One ZIP file cannot be opened.
     *
     * @param assessment the assessment
     * @param section the section of the run
     * @param timeoutSeconds the tester time limit
     * @param ids the anomaly id counter
     * @return the run
     */
    public static GradingRun buildCurrentRun(Assessment assessment,
            Section section, int timeoutSeconds, IdCounter ids) {
        GradingRun run = new GradingRun(CURRENT_RUN_ID, assessment,
                section, CURRENT_RUN_STARTED);
        for (String username : USERNAMES) {
            Submission submission = newSubmission(username, section);
            if (username.equals(INVALID_ZIP_USERNAME)) {
                submission.addAnomaly(new Anomaly(ids.next(),
                        AnomalyType.INVALID_ARCHIVE, Severity.ERROR,
                        username, username + ZIP_EXTENSION + " could not "
                        + "be opened as a ZIP file, so it was not graded.",
                        "Replace the file on the Submissions screen, then "
                        + "grade the batch again."));
            } else {
                applyOutcome(submission, assessment, timeoutSeconds, ids);
            }
            run.addSubmission(submission);
        }
        return run;
    }

    /**
     * Builds the earlier run on the History screen, with one student not
     * on the roster and one failed submission.
     *
     * @param assessment the assessment
     * @param section the section of the run
     * @param timeoutSeconds the tester time limit
     * @param ids the anomaly id counter
     * @return the run
     */
    public static GradingRun buildEarlierRun(Assessment assessment,
            Section section, int timeoutSeconds, IdCounter ids) {
        GradingRun run = new GradingRun(EARLIER_RUN_ID, assessment,
                section, EARLIER_RUN_STARTED);
        for (int index = 0; index < USERNAMES.length; index++) {
            Submission submission = newSubmission(USERNAMES[index], section);
            if (index < ECHO) {
                applyOutcome(submission, assessment, timeoutSeconds, ids);
            } else if (index == ECHO) {
                addNotOnRosterOutcome(submission, ids);
            } else {
                addGraderFailedOutcome(submission, ids);
            }
            run.addSubmission(submission);
        }
        return run;
    }

    /**
     * Builds the large cohort: the six samples plus generated students.
     *
     * @param assessment the assessment
     * @param section the section of the run
     * @param timeoutSeconds the tester time limit
     * @param ids the anomaly id counter
     * @return the run
     */
    public static GradingRun buildLargeCohort(Assessment assessment,
            Section section, int timeoutSeconds, IdCounter ids) {
        GradingRun run = buildCurrentRun(assessment, section,
                timeoutSeconds, ids);
        Random random = new Random(COHORT_SEED);

        for (int number = USERNAMES.length + 1;
                number <= LARGE_COHORT_SIZE; number++) {
            String username = String.format("learner.g%03d", number);
            Submission submission = new Submission(username, section,
                    samplePath(username + ZIP_EXTENSION));
            submission.setStudent(new Student("#" + number, username,
                    LAST_NAMES[number % LAST_NAMES.length],
                    FIRST_NAMES[number % FIRST_NAMES.length],
                    username + EMAIL_DOMAIN));
            applyGeneratedOutcome(submission, assessment, number, random,
                    timeoutSeconds, ids);
            run.addSubmission(submission);
        }
        return run;
    }

    /**
     * Fills in one sample student's results and anomalies, as grading would.
     *
     * @param submission a sample student's submission
     * @param assessment the assessment
     * @param timeoutSeconds the tester time limit
     * @param ids the anomaly id counter
     */
    public static void applyOutcome(Submission submission,
            Assessment assessment, int timeoutSeconds, IdCounter ids) {
        int index = indexOf(submission.getUsername());
        if (index < 0) {
            return;
        }

        submission.getQuestionResults().clear();
        submission.getAnomalies().clear();
        List<Question> questions = assessment.getQuestions();
        for (int position = 0; position < questions.size(); position++) {
            submission.addQuestionResult(passedFirst(questions.get(position),
                    SCORES[index][position]));
        }

        String username = submission.getUsername();
        if (index == ALPHA) {
            addAlphaOutcome(submission, ids);
        } else if (index == BRAVO) {
            addBravoOutcome(submission, timeoutSeconds, ids);
        } else if (index == CHARLIE) {
            addCharlieOutcome(submission, ids);
        } else if (index == DELTA) {
            addDeltaOutcome(submission, ids);
        } else if (index == ECHO) {
            addEchoOutcome(submission, ids);
        }
        submission.setWorkingCopyPath(workingCopyPath(username));
        submission.setStatus(SubmissionStatus.GRADED);
    }

    /**
     * Makes the timed-out sample question pass, as a good retry would.
     *
     * @param result the question result, changed in place
     */
    public static void applyPassingRetry(QuestionResult result) {
        int cases = (int) result.getQuestion().getMaxScore();
        result.getTestCaseResults().clear();
        for (int number = 1; number <= cases; number++) {
            result.addTestCaseResult(new TestCaseResult(number, true,
                    "Passed"));
        }
        result.setTimedOut(false);
        result.setScore(result.getQuestion().getMaxScore());
        result.setOutput(testerOutput(cases, cases)
                + "\n(Retried: every case finished within the limit.)");
    }

    /**
     * Returns the synthetic files of one sample student's working copy.
     *
     * @param username the student
     * @return paths relative to the working copy, such as Q1/Q1a.java
     */
    public static List<String> sourceFiles(String username) {
        List<String> files = new ArrayList<>();
        int index = indexOf(username);
        files.add("Q1/Q1a.java");
        if (index != ECHO) {
            files.add("Q1/Q1b.java");
        }
        if (index != DELTA) {
            files.add("Q2/Q2a.java");
            if (index == CHARLIE) {
                files.add("Q2/q2b.java");
            } else {
                files.add("Q2/Q2b.java");
            }
        }
        files.add("Q3/Q3.java");
        return files;
    }

    /**
     * Returns the synthetic text of one source file.
     *
     * @param username the student
     * @param relativePath the file, relative to the working copy
     * @return the source text
     */
    public static String sourceText(String username, String relativePath) {
        Student student = studentFor(username);
        String className = relativePath.substring(
                relativePath.lastIndexOf('/') + 1,
                relativePath.length() - ".java".length());
        String header = "/*\n * Name: " + student.getFirstName() + " "
                + student.getLastName() + "\n * Email ID: " + username
                + "\n * Synthetic sample file for the GradeZZY demo.\n */\n";

        if (username.equals(TIMEOUT_USERNAME)
                && className.equals(TIMEOUT_QUESTION)) {
            return header + "public class Q1a {\n"
                    + "    public static int countWords(String[] words) {\n"
                    + "        int count = 0;\n"
                    + "        int index = 0;\n"
                    + "        while (index < words.length) {\n"
                    + "            if (!words[index].isEmpty()) {\n"
                    + "                count++;\n"
                    + "                index++;\n"
                    + "            }\n"
                    + "            // index does not move past an empty word\n"
                    + "        }\n"
                    + "        return count;\n"
                    + "    }\n"
                    + "}\n";
        }

        return header + "public class " + className + " {\n"
                + "    public static int solve(int[] values) {\n"
                + "        int total = 0;\n"
                + "        for (int value : values) {\n"
                + "            total += value;\n"
                + "        }\n"
                + "        return total;\n"
                + "    }\n"
                + "}\n";
    }

    /**
     * Returns a made-up path for a sample file. Nothing exists there.
     *
     * @param fileName the file name
     * @return the path
     */
    public static Path samplePath(String fileName) {
        if (fileName.equals(LONG_PATH_USERNAME + ZIP_EXTENSION)) {
            return Paths.get(SAMPLE_FOLDER, LONG_PATH_FOLDERS)
                    .resolve(fileName);
        }
        return Paths.get(SAMPLE_FOLDER, fileName);
    }

    /**
     * Returns the made-up working copy of a student. Nothing exists there.
     *
     * @param username a sample or generated username
     * @return the path
     */
    public static Path workingCopyPath(String username) {
        Path folder = Paths.get(SAMPLE_FOLDER, WORKING_FOLDER);
        if (!username.equals(LONG_PATH_USERNAME)) {
            return folder.resolve(username);
        }
        for (String name : LONG_PATH_FOLDERS) {
            folder = folder.resolve(name);
        }
        return folder.resolve(LONG_WORKING_COPY);
    }

    /**
     * Returns the position of a sample username.
     *
     * @param username the username
     * @return the index in USERNAMES, or -1 for other usernames
     */
    public static int indexOf(String username) {
        for (int index = 0; index < USERNAMES.length; index++) {
            if (USERNAMES[index].equals(username)) {
                return index;
            }
        }
        return -1;
    }

    // Creates a pending submission of a sample student.
    private static Submission newSubmission(String username,
            Section section) {
        Submission submission = new Submission(username, section,
                samplePath(username + ZIP_EXTENSION));
        submission.setStudent(studentFor(username));
        return submission;
    }

    // A missing wrapper folder, repaired; the repair waits for approval.
    private static void addAlphaOutcome(Submission submission,
            IdCounter ids) {
        Anomaly wrapper = new Anomaly(ids.next(),
                AnomalyType.MISSING_WRAPPER_FOLDER, Severity.INFO,
                submission.getUsername(), "Q1, Q2 and Q3 were at the top of "
                + "the ZIP file instead of inside a learner.alpha folder.",
                "Approve the fix if the new folder is right.");
        wrapper.setFixStatus(FixStatus.APPLIED);
        submission.addAnomaly(wrapper);
    }

    // A tester that timed out on its last case, and two repairs.
    private static void addBravoOutcome(Submission submission,
            int timeoutSeconds, IdCounter ids) {
        String username = submission.getUsername();
        QuestionResult q1a = submission.getQuestionResults().get(Q1A);
        q1a.getTestCaseResults().clear();
        q1a.addTestCaseResult(new TestCaseResult(1, true, "Passed"));
        q1a.addTestCaseResult(new TestCaseResult(2, true, "Passed"));
        q1a.addTestCaseResult(new TestCaseResult(FAILING_CASE, false,
                "Not finished: the tester was stopped at the "
                + timeoutSeconds + " s limit before case 3 printed a "
                + "result."));
        q1a.setTimedOut(true);
        q1a.setOutput("Passed\nPassed\n[No more output. The tester was "
                + "stopped after " + timeoutSeconds + " s.]");

        Anomaly folder = new Anomaly(ids.next(),
                AnomalyType.WRONG_FOLDER_NAME, Severity.WARNING, username,
                "The top folder was named RenameToYourUsername.",
                "Approve the rename to learner.bravo, or reject it.");
        folder.setFixStatus(FixStatus.APPLIED);
        submission.addAnomaly(folder);

        Anomaly header = new Anomaly(ids.next(),
                AnomalyType.MISSING_HEADER_NAME, Severity.WARNING, username,
                "Q1a.java had no name after Name: in its header.",
                "Approve the name taken from the roster, or reject it.");
        header.setFixStatus(FixStatus.APPLIED);
        submission.addAnomaly(header);

        submission.addAnomaly(new Anomaly(ids.next(), AnomalyType.TIMEOUT,
                Severity.ERROR, username, "The Q1a tester was stopped at "
                + "the " + timeoutSeconds + " s limit during case 3. Cases "
                + "1 and 2 passed and their marks are kept.",
                "Check the loop in Q1a.java, then retry Q1a."));
    }

    // A question that did not compile because of a wrong file name.
    private static void addCharlieOutcome(Submission submission,
            IdCounter ids) {
        String username = submission.getUsername();
        QuestionResult q2b = submission.getQuestionResults().get(Q2B);
        q2b.getTestCaseResults().clear();
        q2b.setCompiled(false);
        q2b.setScore(0.0);
        q2b.setOutput("Q2bTester.java:5: error: cannot find symbol\n"
                + "        Q2b answer = new Q2b();\n"
                + "        ^\n  symbol:   class Q2b\n"
                + "  location: class Q2bTester\n1 error");

        submission.addAnomaly(new Anomaly(ids.next(),
                AnomalyType.COMPILE_ERROR, Severity.ERROR, username,
                "Q2b did not compile: cannot find symbol class Q2b. The "
                + "other questions were still graded.",
                "Read the compiler output on the Logs tab."));

        Path found = workingCopyPath(username).resolve("Q2")
                .resolve("q2b.java");
        submission.addAnomaly(new Anomaly(ids.next(),
                AnomalyType.WRONG_FILE_NAME, Severity.WARNING, username,
                "Q2/q2b.java should be named Q2/Q2b.java. Working copy "
                + "file: " + found,
                "Rename the file by hand. No automatic fix exists for "
                + "file names."));
    }

    // Untouched starter files, an empty answer and a missing folder.
    private static void addDeltaOutcome(Submission submission,
            IdCounter ids) {
        String username = submission.getUsername();
        markNotCompiled(submission.getQuestionResults().get(Q2A),
                "The Q2 folder was not found, so Q2a was not compiled.");
        markNotCompiled(submission.getQuestionResults().get(Q2B),
                "The Q2 folder was not found, so Q2b was not compiled.");

        submission.addAnomaly(new Anomaly(ids.next(),
                AnomalyType.UNTOUCHED_STARTER, Severity.WARNING, username,
                "Q1a.java and Q3.java match the starter files exactly.",
                "Check whether the student submitted the starter folder."));
        submission.addAnomaly(new Anomaly(ids.next(),
                AnomalyType.EMPTY_ANSWER, Severity.WARNING, username,
                "The method in Q1b.java still returns the starter's "
                + "placeholder value.",
                "No action is needed if the answer was left blank."));

        Anomaly folder = new Anomaly(ids.next(),
                AnomalyType.MISSING_QUESTION_FOLDER, Severity.ERROR,
                username, "The Q2 folder is missing.",
                "Ask for a corrected submission, or keep Q2a and Q2b at 0.");
        submission.addAnomaly(folder);
    }

    // Flags that wait for review, a missing file and a very long log.
    private static void addEchoOutcome(Submission submission,
            IdCounter ids) {
        String username = submission.getUsername();
        QuestionResult q1a = submission.getQuestionResults().get(Q1A);
        q1a.getTestCaseResults().clear();
        q1a.addTestCaseResult(new TestCaseResult(1, true, "Passed"));
        q1a.addTestCaseResult(new TestCaseResult(2, false,
                "Failed: expected 4 but got 3"));
        q1a.addTestCaseResult(new TestCaseResult(FAILING_CASE, false,
                "Failed: IllegalArgumentException at Q1a.java:27"));
        q1a.setOutput(longLog());

        markNotCompiled(submission.getQuestionResults().get(Q1B),
                "Q1/Q1b.java was not found, so Q1b was not compiled.");

        submission.addAnomaly(new Anomaly(ids.next(),
                AnomalyType.HARD_CODED_ANSWER, Severity.WARNING, username,
                "Q2a returns the same value for every example input, which "
                + "may be a hard-coded answer.",
                "Read Q2a.java before deciding. This flag changes no "
                + "mark."));
        submission.addAnomaly(new Anomaly(ids.next(),
                AnomalyType.RUNTIME_ERROR, Severity.ERROR, username,
                "Q1a case 3 threw IllegalArgumentException. Cases 1 and 2 "
                + "were scored.", "Read the log on the Logs tab."));
        submission.addAnomaly(new Anomaly(ids.next(),
                AnomalyType.MISSING_FILE, Severity.ERROR, username,
                "Q1/Q1b.java is missing. GradeZZY never writes a missing "
                + "answer for a student.",
                "Ask for a corrected submission, or keep Q1b at 0."));
        submission.addAnomaly(new Anomaly(ids.next(),
                AnomalyType.SIMILAR_CODE, Severity.INFO, username,
                "Q2a.java is 84% similar to learner.alpha. This is a "
                + "review aid, not a finding.",
                "Compare the pair on the Similarity Review screen."));
    }

    // A student not on the earlier run's score sheet, so not graded.
    private static void addNotOnRosterOutcome(Submission submission,
            IdCounter ids) {
        String username = submission.getUsername();
        submission.addAnomaly(new Anomaly(ids.next(),
                AnomalyType.UNKNOWN_STUDENT, Severity.WARNING, username,
                username + " was not on the score sheet used for this run, "
                + "so the ZIP file was set aside and not graded.",
                "Check the ZIP file name against the score sheet, then "
                + "grade the batch again."));
    }

    // Grading that stopped on the unreadable ZIP file in the earlier run.
    private static void addGraderFailedOutcome(Submission submission,
            IdCounter ids) {
        String username = submission.getUsername();
        submission.setStatus(SubmissionStatus.FAILED);
        submission.addAnomaly(new Anomaly(ids.next(),
                AnomalyType.INTERNAL_ERROR, Severity.ERROR, username,
                "Grading stopped on this submission in the Unpacking "
                + "stage, so it has no marks. The other submissions were "
                + "graded.",
                "Replace " + username + ZIP_EXTENSION + " on the "
                + "Submissions screen, then grade the batch again."));
    }

    // Gives a generated student an outcome picked by its number.
    private static void applyGeneratedOutcome(Submission submission,
            Assessment assessment, int number, Random random,
            int timeoutSeconds, IdCounter ids) {
        String username = submission.getUsername();
        if (number % UNGRADED_EVERY == 0) {
            return;
        }
        if (number % FAILED_EVERY == 0) {
            submission.setStatus(SubmissionStatus.FAILED);
            submission.addAnomaly(new Anomaly(ids.next(),
                    AnomalyType.INTERNAL_ERROR, Severity.ERROR, username,
                    "Grading stopped on this submission in the Scoring "
                    + "stage. The other submissions were graded.",
                    "Retry the failed submissions from the Progress "
                    + "screen."));
            return;
        }

        boolean zero = number % ZERO_EVERY == 0;
        for (Question question : assessment.getQuestions()) {
            int max = (int) question.getMaxScore();
            int score = 0;
            if (!zero) {
                score = random.nextInt(max + 1);
            }
            submission.addQuestionResult(passedFirst(question, score));
        }

        if (number % COMPILE_ERROR_EVERY == 0) {
            QuestionResult q2a = submission.getQuestionResults().get(Q2A);
            markNotCompiled(q2a, "Q2a.java:12: error: ';' expected");
            submission.addAnomaly(new Anomaly(ids.next(),
                    AnomalyType.COMPILE_ERROR, Severity.ERROR, username,
                    "Q2a did not compile: ';' expected.",
                    "Read the compiler output on the Logs tab."));
        }
        if (number % TIMEOUT_EVERY == 0) {
            submission.getQuestionResults().get(Q1A).setTimedOut(true);
            submission.addAnomaly(new Anomaly(ids.next(),
                    AnomalyType.TIMEOUT, Severity.ERROR, username,
                    "The Q1a tester was stopped at the " + timeoutSeconds
                    + " s limit.", "Check the loop in Q1a.java."));
        }
        if (number % UNKNOWN_STUDENT_EVERY == 0) {
            submission.addAnomaly(new Anomaly(ids.next(),
                    AnomalyType.UNKNOWN_STUDENT, Severity.WARNING, username,
                    username + " is not on the roster.",
                    "Check the ZIP file name against the score sheet."));
        }
        if (number % MISSING_FOLDER_EVERY == 0) {
            submission.addAnomaly(new Anomaly(ids.next(),
                    AnomalyType.MISSING_QUESTION_FOLDER, Severity.ERROR,
                    username, "The Q3 folder is missing.",
                    "Ask for a corrected submission."));
        }
        submission.setWorkingCopyPath(workingCopyPath(username));
        submission.setStatus(SubmissionStatus.GRADED);
    }

    // Creates a question result whose first cases pass.
    private static QuestionResult passedFirst(Question question,
            int score) {
        QuestionResult result = new QuestionResult(question);
        int cases = (int) question.getMaxScore();
        for (int number = 1; number <= cases; number++) {
            if (number <= score) {
                result.addTestCaseResult(new TestCaseResult(number, true,
                        "Passed"));
            } else {
                result.addTestCaseResult(new TestCaseResult(number, false,
                        "Failed: expected " + (number * 2) + " but got "
                        + (number * 2 - 1)));
            }
        }
        result.setCompiled(true);
        result.setScore(score);
        result.setOutput(testerOutput(score, cases));
        return result;
    }

    // Marks a question as not compiled, with no cases and no marks.
    private static void markNotCompiled(QuestionResult result,
            String output) {
        result.getTestCaseResults().clear();
        result.setCompiled(false);
        result.setScore(0.0);
        result.setOutput(output);
    }

    // Builds tester output in the format the provided testers print.
    private static String testerOutput(int passed, int cases) {
        StringBuilder output = new StringBuilder();
        for (int number = 1; number <= cases; number++) {
            if (number <= passed) {
                output.append("Passed\n");
            } else {
                output.append("Failed: expected ").append(number * 2)
                        .append(" but got ").append(number * 2 - 1)
                        .append('\n');
            }
        }
        output.append((double) passed);
        return output.toString();
    }

    // Builds a log of about 100,000 characters, to test long output.
    private static String longLog() {
        StringBuilder log = new StringBuilder("Passed\nFailed: expected 4 "
                + "but got 3\nException in thread \"main\" "
                + "java.lang.IllegalArgumentException: empty word list\n");
        int line = 1;
        while (log.length() < LONG_LOG_CHARACTERS) {
            log.append("    at Q1a.countWords(Q1a.java:27) [repeated call ")
                    .append(line).append("]\n");
            line++;
        }
        log.append("1.0");
        return log.toString();
    }
}
