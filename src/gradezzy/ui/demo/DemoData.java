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
import java.util.List;
import java.util.Random;

import gradezzy.model.Anomaly;
import gradezzy.model.AnomalyType;
import gradezzy.model.Assessment;
import gradezzy.model.FixStatus;
import gradezzy.model.GradingRun;
import gradezzy.model.Question;
import gradezzy.model.QuestionResult;
import gradezzy.model.SavedAssessment;
import gradezzy.model.Section;
import gradezzy.model.Severity;
import gradezzy.model.Student;
import gradezzy.model.Submission;
import gradezzy.model.SubmissionStatus;
import gradezzy.model.TestCaseResult;

/**
 * Synthetic data for demo mode.
 *
 * <p>Tier: COULD.
 */
public final class DemoData {

    /** The module shown in demo mode. */
    public static final String MODULE_CODE = "CS102";

    /** A second module with no runs, to show empty states. */
    public static final String EMPTY_MODULE_CODE = "DEMO";

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

    /** The username whose ZIP file holds an extra resources folder. */
    public static final String EXTRA_FOLDER_USERNAME = "learner.echo";

    /** The number of students in the large sample cohort. */
    public static final int LARGE_COHORT_SIZE = 240;

    /** The id of the sample run shown at start-up. */
    public static final String CURRENT_RUN_ID = "024";

    /** The id of the earlier sample run on the Runs tab. */
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

    // Positions of the first and last name in a row of NAMES
    private static final int FIRST_NAME = 0;
    private static final int LAST_NAME = 1;

    // The text before the number of a synthetic OrgDefinedId
    private static final String ID_PREFIX = "#";

    // The OrgDefinedId of a student who is not a sample student
    private static final String UNLISTED_ID = ID_PREFIX + "0";

    // The pattern of a generated username: learner.g and three digits
    private static final String GENERATED_USERNAME_FORMAT = "learner.g%03d";

    // The separator between folders in a relative source path
    private static final char PATH_SEPARATOR = '/';

    // The line ending of tester output
    private static final String LINE_END = "\n";

    // The line a tester prints for a passed case
    private static final String PASSED = "Passed";

    /** The name of the sample assessment. */
    public static final String ASSESSMENT_NAME = "Lab Test";

    // The sample assessment's questions, their folders and their maximum
    // marks, as in the provided Lab Test sample. The demo uses these
    // instead of config.properties, so SCORES always fits its questions.
    private static final String[] QUESTION_IDS = {"Q1a", "Q1b", "Q2a",
        "Q2b", "Q3"};
    private static final String[] QUESTION_FOLDERS = {"Q1", "Q1", "Q2",
        "Q2", "Q3"};
    private static final double[] MAX_MARKS = {3, 3, 5, 5, 4};

    // The ending of an answer's source file
    private static final String JAVA_EXTENSION = ".java";

    // The files that come with the sample assessment
    private static final String[] PROVIDED_CLASS_FILES = {
        "DataException.class", "Shape.class", "Circle.class",
        "Rectangle.class"};
    private static final String[] TESTER_DATA_FILES = {"personstester.txt",
        "studentstester.txt"};
    private static final String PLACEHOLDER_FOLDER = "RenameToYourUsername";

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

    // The first two cases of Q1a, which pass in the sample run
    private static final int FIRST_CASE = 1;
    private static final int SECOND_CASE = 2;

    // The case of Q1a that hangs or throws in the sample run
    private static final int FAILING_CASE = 3;

    // A failed case expects its number times this, and gets one less
    private static final int EXPECTED_FACTOR = 2;

    // The number of characters in the long sample log
    private static final int LONG_LOG_CHARACTERS = 100000;

    // Room past LONG_LOG_CHARACTERS for the last log line and the score
    private static final int LOG_SPARE_CHARACTERS = 100;

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
     * Builds the sample assessment the demo grades with: five questions
     * worth 20 marks, the same whatever config.properties names.
     *
     * <p>Tier: MUST.
     *
     * @return a new copy of the sample assessment
     */
    public static SavedAssessment sampleAssessment() {
        SavedAssessment sample = new SavedAssessment();
        sample.setName(ASSESSMENT_NAME);
        sample.setModuleCode(MODULE_CODE);
        sample.setTestersDirectory(samplePath(SAMPLE_TESTERS).toString());
        sample.setStarterDirectory(samplePath(SAMPLE_STARTER).toString());
        for (int index = 0; index < QUESTION_IDS.length; index++) {
            String id = QUESTION_IDS[index];
            sample.getQuestions().add(new Question(id,
                    QUESTION_FOLDERS[index], id + JAVA_EXTENSION,
                    MAX_MARKS[index]));
        }
        for (String name : PROVIDED_CLASS_FILES) {
            sample.getProvidedClassFiles().add(name);
        }
        for (String name : TESTER_DATA_FILES) {
            sample.getTesterDataFiles().add(name);
        }
        sample.getPlaceholderFolderNames().add(PLACEHOLDER_FOLDER);
        return sample;
    }

    /**
     * Returns the synthetic student behind a username.
     *
     * @param username a sample or generated username
     * @return the student
     */
    public static Student studentFor(String username) {
        int index = indexOf(username);
        String email = username + EMAIL_DOMAIN;
        if (index >= 0) {
            int idNumber = index + 1;
            return new Student(ID_PREFIX + idNumber, username,
                    NAMES[index][LAST_NAME], NAMES[index][FIRST_NAME],
                    email);
        }
        return new Student(UNLISTED_ID, username, "", username, email);
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
            Section section, int timeoutSeconds, DemoIdCounter ids) {
        GradingRun run = new GradingRun(CURRENT_RUN_ID, assessment,
                section, CURRENT_RUN_STARTED);
        for (String username : USERNAMES) {
            Submission submission = newSubmission(username, section);
            if (username.equals(INVALID_ZIP_USERNAME)) {
                submission.addAnomaly(new Anomaly(ids.next(),
                        AnomalyType.INVALID_ARCHIVE, Severity.ERROR,
                        username, username + ZIP_EXTENSION + " could not "
                        + "be opened as a ZIP file, so it was not graded.",
                        "Replace the file on Set up, then grade the batch "
                        + "again."));
            } else {
                applyOutcome(submission, assessment, timeoutSeconds, ids);
            }
            run.addSubmission(submission);
        }
        return run;
    }

    /**
     * Builds the earlier run on the Runs tab, with one student not
     * on the roster and one failed submission.
     *
     * @param assessment the assessment
     * @param section the section of the run
     * @param timeoutSeconds the tester time limit
     * @param ids the anomaly id counter
     * @return the run
     */
    public static GradingRun buildEarlierRun(Assessment assessment,
            Section section, int timeoutSeconds, DemoIdCounter ids) {
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
            Section section, int timeoutSeconds, DemoIdCounter ids) {
        GradingRun run = buildCurrentRun(assessment, section,
                timeoutSeconds, ids);
        Random random = new Random(COHORT_SEED);

        int firstNumber = USERNAMES.length + 1;
        for (int number = firstNumber; number <= LARGE_COHORT_SIZE;
                number++) {
            String username = String.format(GENERATED_USERNAME_FORMAT,
                    number);
            String fileName = username + ZIP_EXTENSION;
            Submission submission = new Submission(username, section,
                    samplePath(fileName));
            int lastNameIndex = number % LAST_NAMES.length;
            int firstNameIndex = number % FIRST_NAMES.length;
            String email = username + EMAIL_DOMAIN;
            submission.setStudent(new Student(ID_PREFIX + number, username,
                    LAST_NAMES[lastNameIndex], FIRST_NAMES[firstNameIndex],
                    email));
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
            Assessment assessment, int timeoutSeconds, DemoIdCounter ids) {
        int index = indexOf(submission.getUsername());
        if (index < 0) {
            return;
        }

        submission.getQuestionResults().clear();
        submission.getAnomalies().clear();
        List<Question> questions = assessment.getQuestions();
        for (int position = 0; position < questions.size(); position++) {
            submission.addQuestionResult(passedFirst(questions.get(position),
                    sampleScore(index, position)));
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
                    PASSED));
        }
        result.setTimedOut(false);
        result.setScore(result.getQuestion().getMaxScore());
        result.setOutput(testerOutput(cases, cases) + LINE_END
                + "(Retried: every case finished within the limit.)");
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
        int nameStart = relativePath.lastIndexOf(PATH_SEPARATOR) + 1;
        int nameEnd = relativePath.length() - JAVA_EXTENSION.length();
        String className = relativePath.substring(nameStart, nameEnd);
        String header = "/*\n * Name: " + student.getFirstName() + " "
                + student.getLastName() + "\n * Email ID: " + username
                + "\n * Synthetic sample file for the GradeZZY demo.\n */\n";

        boolean hangingAnswer = username.equals(TIMEOUT_USERNAME)
                && className.equals(TIMEOUT_QUESTION);
        if (hangingAnswer) {
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
        String longPathZip = LONG_PATH_USERNAME + ZIP_EXTENSION;
        if (fileName.equals(longPathZip)) {
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
        String fileName = username + ZIP_EXTENSION;
        Submission submission = new Submission(username, section,
                samplePath(fileName));
        submission.setStudent(studentFor(username));
        return submission;
    }

    // A missing wrapper folder, repaired; the repair waits for approval.
    private static void addAlphaOutcome(Submission submission,
            DemoIdCounter ids) {
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
            int timeoutSeconds, DemoIdCounter ids) {
        String username = submission.getUsername();
        QuestionResult q1a = submission.getQuestionResults().get(Q1A);
        q1a.getTestCaseResults().clear();
        q1a.addTestCaseResult(new TestCaseResult(FIRST_CASE, true, PASSED));
        q1a.addTestCaseResult(new TestCaseResult(SECOND_CASE, true, PASSED));
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
            DemoIdCounter ids) {
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
                "Open code and test output on Review to read the "
                + "compiler output."));

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
            DemoIdCounter ids) {
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
            DemoIdCounter ids) {
        String username = submission.getUsername();
        QuestionResult q1a = submission.getQuestionResults().get(Q1A);
        q1a.getTestCaseResults().clear();
        q1a.addTestCaseResult(new TestCaseResult(FIRST_CASE, true, PASSED));
        q1a.addTestCaseResult(new TestCaseResult(SECOND_CASE, false,
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
                + "were scored.", "Open code and test output on Review "
                + "to read the tester output."));
        submission.addAnomaly(new Anomaly(ids.next(),
                AnomalyType.MISSING_FILE, Severity.ERROR, username,
                "Q1/Q1b.java is missing. GradeZZY never writes a missing "
                + "answer for a student.",
                "Ask for a corrected submission, or keep Q1b at 0."));
        submission.addAnomaly(new Anomaly(ids.next(),
                AnomalyType.SIMILAR_CODE, Severity.INFO, username,
                "Q2a.java is 84% similar to learner.alpha. This is a "
                + "review aid, not a finding.",
                "Use Compare in the Similar code card on Export."));
    }

    // A student not on the earlier run's score sheet, so not graded.
    private static void addNotOnRosterOutcome(Submission submission,
            DemoIdCounter ids) {
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
            DemoIdCounter ids) {
        String username = submission.getUsername();
        submission.setStatus(SubmissionStatus.FAILED);
        submission.addAnomaly(new Anomaly(ids.next(),
                AnomalyType.INTERNAL_ERROR, Severity.ERROR, username,
                "Grading stopped on this submission while it was "
                + "being unpacked, so it has no marks. The other "
                + "submissions were graded.",
                "Replace " + username + ZIP_EXTENSION + " on Set up, "
                + "then grade the batch again."));
    }

    // Gives a generated student an outcome picked by its number.
    private static void applyGeneratedOutcome(Submission submission,
            Assessment assessment, int number, Random random,
            int timeoutSeconds, DemoIdCounter ids) {
        String username = submission.getUsername();
        if (number % UNGRADED_EVERY == 0) {
            return;
        }
        if (number % FAILED_EVERY == 0) {
            submission.setStatus(SubmissionStatus.FAILED);
            submission.addAnomaly(new Anomaly(ids.next(),
                    AnomalyType.INTERNAL_ERROR, Severity.ERROR, username,
                    "Grading stopped on this submission while it was "
                    + "being scored. The other submissions were graded.",
                    "Retry the failed submissions on Review."));
            return;
        }

        addGeneratedScores(submission, assessment, number, random);
        addGeneratedFlags(submission, number, timeoutSeconds, ids);
        submission.setWorkingCopyPath(workingCopyPath(username));
        submission.setStatus(SubmissionStatus.GRADED);
    }

    // Gives a generated student random marks, or zero on every question.
    private static void addGeneratedScores(Submission submission,
            Assessment assessment, int number, Random random) {
        boolean zero = number % ZERO_EVERY == 0;
        for (Question question : assessment.getQuestions()) {
            int max = (int) question.getMaxScore();
            int score = 0;
            if (!zero) {
                int bound = max + 1;
                score = random.nextInt(bound);
            }
            submission.addQuestionResult(passedFirst(question, score));
        }
    }

    // Adds the flags a generated student gets, picked by its number.
    private static void addGeneratedFlags(Submission submission, int number,
            int timeoutSeconds, DemoIdCounter ids) {
        String username = submission.getUsername();
        if (number % COMPILE_ERROR_EVERY == 0) {
            QuestionResult q2a = submission.getQuestionResults().get(Q2A);
            markNotCompiled(q2a, "Q2a.java:12: error: ';' expected");
            submission.addAnomaly(new Anomaly(ids.next(),
                    AnomalyType.COMPILE_ERROR, Severity.ERROR, username,
                    "Q2a did not compile: ';' expected.",
                    "Open code and test output on Review to read the "
                    + "compiler output."));
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
    }

    // Returns a sample student's mark for the question at a position, or
    // 0 past the sample's five questions, for an assessment saved in the
    // demo with more questions.
    private static int sampleScore(int student, int position) {
        if (position >= SCORES[student].length) {
            return 0;
        }
        return SCORES[student][position];
    }

    // Creates a question result whose first cases pass.
    private static QuestionResult passedFirst(Question question,
            int score) {
        QuestionResult result = new QuestionResult(question);
        int cases = (int) question.getMaxScore();
        for (int number = 1; number <= cases; number++) {
            if (number <= score) {
                result.addTestCaseResult(new TestCaseResult(number, true,
                        PASSED));
            } else {
                result.addTestCaseResult(new TestCaseResult(number, false,
                        failedLine(number)));
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
        String output = "";
        for (int number = 1; number <= cases; number++) {
            if (number <= passed) {
                output += PASSED + LINE_END;
            } else {
                output += failedLine(number) + LINE_END;
            }
        }
        output += (double) passed;
        return output;
    }

    // Writes the made-up Failed line of one test case.
    private static String failedLine(int number) {
        int expected = number * EXPECTED_FACTOR;
        int got = expected - 1;
        return "Failed: expected " + expected + " but got " + got;
    }

    // Builds a log of about 100,000 characters, to test long output. The
    // text is long, so it is built in a char array, not with + in a loop.
    private static String longLog() {
        int capacity = LONG_LOG_CHARACTERS + LOG_SPARE_CHARACTERS;
        char[] log = new char[capacity];
        int count = copyInto(log, 0, "Passed\nFailed: expected 4 "
                + "but got 3\nException in thread \"main\" "
                + "java.lang.IllegalArgumentException: empty word list\n");
        int line = 1;
        while (count < LONG_LOG_CHARACTERS) {
            count = copyInto(log, count, "    at Q1a.countWords(Q1a.java:27)"
                    + " [repeated call " + line + "]\n");
            line++;
        }
        count = copyInto(log, count, "1.0");
        return new String(log, 0, count);
    }

    // Copies text into the array from a position; returns the position
    // after the last character copied.
    private static int copyInto(char[] target, int start, String text) {
        int position = start;
        for (int index = 0; index < text.length(); index++) {
            target[position] = text.charAt(index);
            position++;
        }
        return position;
    }
}
