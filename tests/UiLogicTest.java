/*
 * File: UiLogicTest.java
 *
 * Purpose: Unit checks for window logic that needs no JavaFX:
 * CheatingRemarks (the Remarks column of the score sheet), driven by a
 * small fake DashboardBackend made with java.lang.reflect.Proxy, and the
 * SectionProgress rules that need no FlowState.
 *
 * Where it fits: Run by hand after compiling, as tests/README.md says.
 * The JavaFX jars are not needed on the classpath.
 */
import java.lang.reflect.InvocationHandler;
import java.lang.reflect.Method;
import java.lang.reflect.Proxy;
import java.nio.file.Path;
import java.time.LocalDateTime;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

import gradezzy.config.AppConfig;
import gradezzy.config.ConfigKeys;
import gradezzy.model.Anomaly;
import gradezzy.model.AnomalyType;
import gradezzy.model.Assessment;
import gradezzy.model.GradingRun;
import gradezzy.model.Question;
import gradezzy.model.Section;
import gradezzy.model.Severity;
import gradezzy.model.Student;
import gradezzy.model.Submission;
import gradezzy.model.SubmissionStatus;
import gradezzy.plagiarism.SimilarityPair;
import gradezzy.service.PlagiarismService;
import gradezzy.ui.backend.CheatingRemarks;
import gradezzy.ui.backend.DashboardBackend;
import gradezzy.ui.backend.ImportBatch;
import gradezzy.ui.backend.ImportItem;
import gradezzy.ui.backend.ImportState;
import gradezzy.ui.backend.InputKind;
import gradezzy.ui.backend.ReviewDecision;
import gradezzy.ui.backend.RunRecord;
import gradezzy.ui.flow.SectionProgress;

/**
 * Runs the window logic checks and prints how many passed.
 */
public class UiLogicTest {

    // The course and the section groups the runs are for
    private static final String COURSE = "CS102";
    private static final String GROUP_ONE = "G1";
    private static final String GROUP_TWO = "G2";

    // The section most runs are for
    private static final Section SECTION = new Section(COURSE, GROUP_ONE);

    // The assessment the section progress checks look for
    private static final String LAB_TEST = "Lab Test 3";

    // The questions of the remarks assessment
    private static final String Q1 = "Q1";
    private static final String Q2 = "Q2";

    // The usernames of the students in the remarks run
    private static final String ANN = "ann";
    private static final String BOB = "bob";
    private static final String CAT = "cat";
    private static final String DAN = "dan";
    private static final String EVE = "eve";

    // The upload name in upper case and its partner in the second run
    private static final String ZED = "ZED";
    private static final String YAN = "yan";

    // The empty state and backup text of a run record
    private static final String NO_TEXT = "";

    // Turns a similarity from 0 to 1 into a percentage
    private static final int PERCENT = 100;

    // The year, month, hour and minute the progress runs were made
    private static final int YEAR = 2026;
    private static final int MONTH = 1;
    private static final int HOUR = 9;
    private static final int MINUTE = 0;

    // The days of the month the progress runs were made
    private static final int FIRST_DAY = 1;
    private static final int SECOND_DAY = 2;
    private static final int THIRD_DAY = 3;

    // The number of checks that passed and failed so far
    private static int passed = 0;
    private static int failed = 0;

    // The decision of each flag, by anomaly id; a flag not here is OPEN
    private static final Map<String, ReviewDecision> DECISIONS =
            new HashMap<>();

    // The service the fake backend reads similar pairs with
    private static PlagiarismService plagiarism;

    /**
     * Runs every check.
     *
     * @param args not used
     * @throws Exception if the settings cannot be read
     */
    public static void main(String[] args) throws Exception {
        AppConfig config = TestConfig.load();
        config.setString(ConfigKeys.DATABASE_ENABLED, "false");
        plagiarism = new PlagiarismService(config, null, null);
        checkRemarks();
        checkProgress();
        checkModelAnswersKind();
        System.out.println("UiLogicTest: " + passed + " passed, " + failed
                + " failed");
        if (failed > 0) {
            System.exit(1);
        }
    }

    // Makes a fake backend that answers only the calls CheatingRemarks
    // makes, with the given pairs and the decisions in DECISIONS.
    private static DashboardBackend fakeBackend(
            final List<SimilarityPair> pairs) {
        InvocationHandler handler = new InvocationHandler() {
            // Answers one call made on the fake backend
            @Override
            public Object invoke(Object proxy, Method method, Object[] args) {
                String name = method.getName();
                if (name.equals("listSimilarPairs")) {
                    return pairs;
                } else if (name.equals("findSimilarPair")) {
                    return plagiarism.readPair((GradingRun) args[0],
                            (Anomaly) args[1]);
                } else if (name.equals("getDecision")) {
                    ReviewDecision decision = DECISIONS.get(
                            ((Anomaly) args[0]).getId());
                    return decision == null ? ReviewDecision.OPEN : decision;
                } else if (name.equals("needsReview")) {
                    return !DECISIONS.containsKey(((Anomaly) args[0])
                            .getId());
                } else if (name.equals("toString")) {
                    return "fake backend";
                }
                throw new UnsupportedOperationException(name);
            }
        };
        return (DashboardBackend) Proxy.newProxyInstance(
                DashboardBackend.class.getClassLoader(),
                new Class<?>[] {DashboardBackend.class}, handler);
    }

    // Makes a graded submission; a full name "First Last" also gives it
    // a student from the roster.
    private static Submission student(String user, String full) {
        Submission submission = new Submission(user, SECTION,
                Path.of("/tmp/" + user));
        submission.setStatus(SubmissionStatus.GRADED);
        if (full != null) {
            String[] parts = full.split(" ");
            submission.setStudent(new Student("1", user, parts[1], parts[0],
                    user + "@x"));
        }
        return submission;
    }

    // Adds both students' flags for a pair, as PlagiarismService writes
    // them, and returns the two flags.
    private static Anomaly[] flag(GradingRun run, SimilarityPair pair) {
        String[] users = {pair.getFirstUsername(), pair.getSecondUsername()};
        Anomaly[] flags = new Anomaly[users.length];
        for (int i = 0; i < users.length; i++) {
            String owner = users[i];
            String other = users[1 - i];
            String message = pair.getQuestionId() + " is "
                    + Math.round(pair.getSimilarity() * PERCENT)
                    + "% similar to " + other + ". This is a review aid, not "
                    + "a finding.";
            if (pair.isIdentical()) {
                message += " Identical copy: both files have the same Name, "
                        + "Email ID and code.";
            } else if (pair.isSameEmail()) {
                message += " Same Email ID: both files give the same Email "
                        + "ID in their header.";
            }
            Anomaly anomaly = new Anomaly("SIMILAR-" + run.getId() + "/"
                    + owner + "/" + other + "/" + pair.getQuestionId(),
                    AnomalyType.SIMILAR_CODE, Severity.INFO, owner, message,
                    "Compare");
            run.findLoaded(owner).addAnomaly(anomaly);
            flags[i] = anomaly;
        }
        return flags;
    }

    // Checks the Remarks column as flags are reviewed one by one.
    private static void checkRemarks() {
        Assessment assessment = new Assessment("Lab", COURSE);
        assessment.addQuestion(new Question(Q1, Q1, "Q1.java", 1));
        assessment.addQuestion(new Question(Q2, Q2, "Q2.java", 1));
        GradingRun run = new GradingRun("RUN-7", assessment, SECTION,
                LocalDateTime.now());
        run.addSubmission(student(ANN, "Ann Lee"));
        run.addSubmission(student(BOB, "Bob Tan"));
        run.addSubmission(student(CAT, null));
        run.addSubmission(student(DAN, "Dan Ng"));
        run.addSubmission(student(EVE, "Eve Ho"));
        List<SimilarityPair> pairs = new ArrayList<>();
        // ann/bob identical on Q1, similar on Q2
        double annBobQ2Similarity = 0.9;
        SimilarityPair abQ1 = new SimilarityPair(ANN, BOB, Q1, 1.0, true,
                true);
        SimilarityPair abQ2 = new SimilarityPair(ANN, BOB, Q2,
                annBobQ2Similarity);
        // ann/cat similar on Q1 and Q2
        double annCatQ1Similarity = 0.85;
        double annCatQ2Similarity = 0.88;
        SimilarityPair acQ1 = new SimilarityPair(ANN, CAT, Q1,
                annCatQ1Similarity);
        SimilarityPair acQ2 = new SimilarityPair(CAT, ANN, Q2,
                annCatQ2Similarity);
        // dan/eve similar on Q1 only
        double danEveQ1Similarity = 0.95;
        SimilarityPair deQ1 = new SimilarityPair(DAN, EVE, Q1,
                danEveQ1Similarity);
        pairs.addAll(Arrays.asList(abQ1, abQ2, acQ1, acQ2, deQ1));
        Anomaly[] abQ1Flags = flag(run, abQ1);
        Anomaly[] abQ2Flags = flag(run, abQ2);
        Anomaly[] acQ1Flags = flag(run, acQ1);
        Anomaly[] acQ2Flags = flag(run, acQ2);
        Anomaly[] deQ1Flags = flag(run, deQ1);
        DashboardBackend backend = fakeBackend(pairs);
        CheatingRemarks remarks = new CheatingRemarks(backend);
        Map<String, String> pending = remarks.remarksFor(run);
        check(("Not yet reviewed: flagged with Bob Tan (bob) on 2 questions"
                + ", cat on 2 questions").equals(pending.get(ANN)),
                "nothing reviewed, alike on 2 questions: " + pending.get(ANN));
        check("Not yet reviewed: flagged with Ann Lee (ann) on 2 questions"
                .equals(pending.get(BOB)), "bob waits on ann: "
                + pending.get(BOB));
        check(!pending.containsKey(DAN) && !pending.containsKey(EVE),
                "alike on one question, not reviewed: no remark " + pending);
        check(remarks.isWaiting(run, abQ1), "unreviewed pair waits");
        // Keep ann/bob Q1 (identical) flagged on both sides
        DECISIONS.put(abQ1Flags[0].getId(), ReviewDecision.REVIEWED);
        check(!remarks.remarksFor(run).get(BOB).contains("Cheated"),
                "one side reviewed, other still open: not cheated yet");
        DECISIONS.put(abQ1Flags[1].getId(), ReviewDecision.REVIEWED);
        Map<String, String> remarkMap = remarks.remarksFor(run);
        check(("Cheated with Bob Tan (bob); Not yet reviewed: flagged "
                + "with cat on 2 questions").equals(remarkMap.get(ANN)),
                "ann: cheated with bob, cat waits: " + remarkMap.get(ANN));
        check("Cheated with Ann Lee (ann)".equals(remarkMap.get(BOB)),
                "bob: cheated with ann: " + remarkMap.get(BOB));
        check(!remarks.isWaiting(run, abQ1), "reviewed pair not waiting");
        // ann/cat: Q1 kept, Q2 still open -> no possible remark yet
        DECISIONS.put(acQ1Flags[0].getId(), ReviewDecision.REVIEWED);
        DECISIONS.put(acQ1Flags[1].getId(), ReviewDecision.REVIEWED);
        check(!remarks.remarksFor(run).getOrDefault(CAT, NO_TEXT).contains(
                "Possible"), "not every question reviewed: no possible");
        DECISIONS.put(acQ2Flags[0].getId(), ReviewDecision.REVIEWED);
        DECISIONS.put(acQ2Flags[1].getId(), ReviewDecision.REVIEWED);
        remarkMap = remarks.remarksFor(run);
        check("Cheated with Bob Tan (bob); Possible cheating with cat"
                .equals(remarkMap.get(ANN)), "ann both parts: "
                + remarkMap.get(ANN));
        check("Possible cheating with Ann Lee (ann)".equals(
                remarkMap.get(CAT)), "cat possible with ann: "
                + remarkMap.get(CAT));
        // One side says looks fine on Q2 -> not all kept
        DECISIONS.put(acQ2Flags[1].getId(), ReviewDecision.REJECTED);
        remarkMap = remarks.remarksFor(run);
        System.out.println("   ann/cat Q2 kept by ann's flag, rejected on "
                + "cat's: ann='" + remarkMap.get(ANN) + "' cat='"
                + remarkMap.get(CAT) + "'");
        check(remarkMap.get(ANN).equals(remarkMap.getOrDefault(ANN, NO_TEXT))
                && sameMention(remarkMap.get(ANN), CAT, remarkMap.get(CAT),
                ANN), "the two students of a pair get matching remarks");
        // dan/eve both kept on their only question
        DECISIONS.put(deQ1Flags[0].getId(), ReviewDecision.REVIEWED);
        DECISIONS.put(deQ1Flags[1].getId(), ReviewDecision.REVIEWED);
        remarkMap = remarks.remarksFor(run);
        check("Possible cheating with Eve Ho (eve)".equals(
                remarkMap.get(DAN)), "single-question similar pair kept: "
                + remarkMap.get(DAN));
        // Upper-case upload name maps to lower-case key
        GradingRun upper = new GradingRun("RUN-8", assessment, SECTION,
                LocalDateTime.now());
        upper.addSubmission(student(ZED, "Zed Ko"));
        upper.addSubmission(student(YAN, "Yan Li"));
        SimilarityPair zedYan = new SimilarityPair(ZED, YAN, Q1, 1.0, true,
                true);
        Anomaly[] zedYanFlags = flag(upper, zedYan);
        DECISIONS.put(zedYanFlags[0].getId(), ReviewDecision.REVIEWED);
        DECISIONS.put(zedYanFlags[1].getId(), ReviewDecision.REVIEWED);
        Map<String, String> upperRemarks = new CheatingRemarks(fakeBackend(
                Arrays.asList(zedYan))).remarksFor(upper);
        check(upperRemarks.containsKey("zed"), "remark key is lower case");
        check(new CheatingRemarks(backend).remarksFor(null).isEmpty(),
                "null run");
        // Resolved by retry counts as not kept
        DECISIONS.put(abQ1Flags[0].getId(), ReviewDecision.RESOLVED);
        remarkMap = remarks.remarksFor(run);
        System.out.println("   after ann's identical flag is RESOLVED: "
                + "ann='" + remarkMap.get(ANN) + "' bob='"
                + remarkMap.get(BOB) + "'");
    }

    // Says whether both remarks name the other student, or neither does.
    private static boolean sameMention(String first, String otherOfFirst,
            String second, String otherOfSecond) {
        boolean firstNames = first != null && first.contains(otherOfFirst);
        boolean secondNames = second != null && second.contains(
                otherOfSecond);
        return firstNames == secondNames;
    }

    // Checks how SectionProgress finds each section's run and counts the
    // submissions still waiting.
    private static void checkProgress() {
        Assessment lab = new Assessment(LAB_TEST, COURSE);
        GradingRun g1Old = new GradingRun("RUN-1", lab, SECTION,
                madeOn(FIRST_DAY));
        GradingRun g1New = new GradingRun("RUN-2", lab, SECTION,
                madeOn(SECOND_DAY));
        GradingRun g2 = new GradingRun("RUN-3", lab, new Section(COURSE,
                GROUP_TWO), madeOn(SECOND_DAY));
        GradingRun other = new GradingRun("RUN-4", new Assessment(
                "Lab Test 2", COURSE), SECTION, madeOn(THIRD_DAY));
        GradingRun is442 = new GradingRun("RUN-5", lab, new Section("IS442",
                GROUP_ONE), madeOn(THIRD_DAY));
        List<RunRecord> records = Arrays.asList(
                new RunRecord(g1New, NO_TEXT, NO_TEXT, false, false),
                new RunRecord(g1Old, NO_TEXT, NO_TEXT, false, true),
                new RunRecord(g2, NO_TEXT, NO_TEXT, false, false),
                new RunRecord(other, NO_TEXT, NO_TEXT, false, false),
                new RunRecord(is442, NO_TEXT, NO_TEXT, false, false));
        Map<String, GradingRun> runs = SectionProgress.findRuns(records,
                "cs102", "lab test 3");
        int expectedSections = 2;
        check(runs.size() == expectedSections, "two sections found ("
                + runs.keySet() + ")");
        check(runs.get(GROUP_ONE) == g1Old, "the current run wins for G1");
        check(runs.get(GROUP_TWO) == g2, "G2 run");
        Map<String, GradingRun> noCurrent = SectionProgress.findRuns(
                Arrays.asList(new RunRecord(g1New, NO_TEXT, NO_TEXT, false,
                false), new RunRecord(g1Old, NO_TEXT, NO_TEXT, false,
                false)), COURSE, LAB_TEST);
        check(noCurrent.get(GROUP_ONE) == g1New, "without a current run, "
                + "the first listed wins");
        check(!SectionProgress.isGradedWith(other, LAB_TEST)
                && SectionProgress.isGradedWith(g1New, "LAB TEST 3")
                && !SectionProgress.isGradedWith(g1New, null),
                "isGradedWith");
        GradingRun waiting = new GradingRun("RUN-9", lab, SECTION,
                LocalDateTime.now());
        Submission pending = student("p", null);
        pending.setStatus(SubmissionStatus.PENDING);
        Submission cancelled = student("c", null);
        cancelled.setStatus(SubmissionStatus.CANCELLED);
        Submission done = student("d", null);
        waiting.addSubmission(pending);
        waiting.addSubmission(cancelled);
        waiting.addSubmission(done);
        int expectedWaiting = 2;
        check(SectionProgress.countWaiting(waiting) == expectedWaiting,
                "pending and cancelled wait");
        GradingRun crashed = new GradingRun("RUN-10", lab, SECTION,
                LocalDateTime.now());
        Submission running = student("r", null);
        running.setStatus(SubmissionStatus.RUNNING);
        crashed.addSubmission(running);
        check(SectionProgress.countWaiting(crashed) == 1,
                "a submission left RUNNING (app stopped mid-grade; "
                + "GradingService.needsResume resumes it) counts as waiting");
    }

    // Gives the time a progress run was made on a day of the month.
    private static LocalDateTime madeOn(int day) {
        return LocalDateTime.of(YEAR, MONTH, day, HOUR, MINUTE);
    }

    // Checks that model answers are optional: a batch without them, or
    // with an unusable folder, gives no reason to wait, and a new folder
    // takes the place of the last one.
    private static void checkModelAnswersKind() {
        InputKind kind = InputKind.MODEL_ANSWERS;
        check(!kind.isRequired(), "model answers are optional");
        check(InputKind.valueOf("MODEL_ANSWERS") == kind,
                "the store's kind name reads back");

        ImportBatch batch = new ImportBatch();
        int reasonsWithout = batch.getBlockingReasons().size();
        batch.add(new ImportItem(kind, Path.of("gone"), "",
                ImportState.INVALID, "Not found."));
        check(batch.getBlockingReasons().size() == reasonsWithout,
                "an unusable model answers folder does not block grading");
        batch.add(new ImportItem(kind, Path.of("answers"), "",
                ImportState.ACCEPTED, "Has an answer for every question."));
        check(batch.getItems(kind).size() == 1
                && Path.of("answers").equals(batch.getSinglePath(kind)),
                "a new model answers folder replaces the last one");
    }

    // Counts one check and prints it when it fails.
    private static void check(boolean condition, String message) {
        if (condition) {
            passed++;
        } else {
            failed++;
            System.out.println("FAIL: " + message);
        }
    }
}
