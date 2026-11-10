/*
 * File: PlagiarismTest.java
 *
 * Purpose: Unit checks for the plagiarism package and PlagiarismService:
 * the tokenizer, winnowing, both similarity detectors (threshold, minimum
 * lines, starter code), IdenticalCopyFinder (same header and code, renamed
 * variables, missing header, case and spacing), CodeLineCounter,
 * LineComparison, and the flags PlagiarismService writes and reads back.
 *
 * Where it fits: Run by hand after compiling, as tests/README.md says.
 * Reads the fixed test settings. No student code runs.
 */
import java.nio.file.Files;
import java.nio.file.Path;
import java.time.LocalDateTime;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.HashSet;
import java.util.List;
import java.util.Set;

import gradezzy.config.AppConfig;
import gradezzy.config.ConfigKeys;
import gradezzy.model.Anomaly;
import gradezzy.model.AnomalyType;
import gradezzy.model.Assessment;
import gradezzy.model.FixStatus;
import gradezzy.model.GradingRun;
import gradezzy.model.Question;
import gradezzy.model.Section;
import gradezzy.model.Student;
import gradezzy.model.Submission;
import gradezzy.model.SubmissionStatus;
import gradezzy.plagiarism.CodeLineCounter;
import gradezzy.plagiarism.Fingerprint;
import gradezzy.plagiarism.IdenticalCopyFinder;
import gradezzy.plagiarism.JavaToken;
import gradezzy.plagiarism.JavaTokenizer;
import gradezzy.plagiarism.LineComparison;
import gradezzy.plagiarism.LineKind;
import gradezzy.plagiarism.SimilarityPair;
import gradezzy.plagiarism.TokenSimilarityDetector;
import gradezzy.plagiarism.Winnowing;
import gradezzy.plagiarism.WinnowingSimilarityDetector;
import gradezzy.service.AssessmentFactory;
import gradezzy.service.PlagiarismService;
import gradezzy.util.FileUtils;

/**
 * Runs the plagiarism checks and prints how many passed.
 */
public class PlagiarismTest {

    // The section every student is in
    private static final Section SECTION = new Section("CS102", "G1");

    // The question every answer is for, its folder and its file
    private static final String QUESTION = "Q1a";
    private static final String QUESTION_FOLDER = "Q1";
    private static final String QUESTION_FILE = "Q1a.java";

    // The name of the starter code folder
    private static final String STARTER_FOLDER = "starter";

    // The plagiarism.min.lines value most checks use
    private static final String MIN_LINES = "10";

    // The value that turns a setting off
    private static final String OFF = "false";

    // Tokens the tokenizer checks expect
    private static final String INT_KEYWORD = "int";
    private static final String ASSIGN = "=";
    private static final String SEMICOLON = ";";

    // The name prefix of the answer most students hand in
    private static final String MAIN_PREFIX = "a";

    // The students of the detector checks whose names come up often
    private static final String AMY = "amy";
    private static final String HAL = "hal";

    // The students of the identical copy checks
    private static final String FIRST_COPY = "id1";
    private static final String SECOND_COPY = "id2";
    private static final String NO_HEADER = "id4";

    // The Email ID most identical copy students use
    private static final String ANN_EMAIL = "ann.lee@x.edu";

    // The students of the service checks, and the header the copies use
    private static final String SAM_FIRST = "sa";
    private static final String SAM_COPY = "sb";
    private static final String SAM_RENAMED = "sc";
    private static final String SAM_NAME = "Sam A";
    private static final String SAM_EMAIL = "sam@x";

    // The first student of the check with plagiarism turned off
    private static final String OFF_FIRST = "o1";

    // The window and k-gram sizes the winnowing checks use
    private static final int KGRAM_SIZE = 5;
    private static final int WINDOW = 4;

    // Hashes for the winnowing checks: two only, all equal, and mixed
    private static final Integer[] SHORT_HASHES = {5, 3};
    private static final Integer[] EQUAL_HASHES = {7, 7, 7, 7, 7, 7};
    private static final Integer[] MIXED_HASHES = {9, 1, 8, 2, 7, 3, 6, 4,
        5, 0};

    // How many repeated lines the long answers have
    private static final int REPEATS = 20;

    // Turns a similarity into a percentage
    private static final int PERCENT = 100;

    // Text pieces used to build file keys and messages
    private static final String SLASH = "/";
    private static final String CLOSE_BRACKET = ")";
    private static final String NEWLINE = "\n";

    // The number of checks that passed and failed
    private static int passed = 0;
    private static int failed = 0;

    // The folder every working copy is built in
    private static Path working;

    // The fixed test settings
    private static AppConfig config;

    // The assessment the settings describe
    private static Assessment assessment;

    /**
     * Runs every check.
     *
     * @param args not used
     * @throws Exception if a temporary file cannot be written
     */
    public static void main(String[] args) throws Exception {
        Path root = Files.createTempDirectory("gradezzy-plag-tests-")
                .toRealPath();
        try {
            config = TestConfig.load();
            working = root.resolve("working");
            config.setString(ConfigKeys.WORKING_DIR, working.toString());
            config.setString(ConfigKeys.STARTER_DIR,
                    root.resolve(STARTER_FOLDER).toString());
            config.setString(ConfigKeys.PLAGIARISM_MIN_LINES, MIN_LINES);
            config.setString(ConfigKeys.DATABASE_ENABLED, OFF);
            assessment = new AssessmentFactory(config).create();
            checkTokenizer();
            checkWinnowing();
            checkLineCounter(root);
            checkLineComparison();
            checkDetectors(root);
            checkIdentical(root);
            checkService(root);
        } finally {
            FileUtils.deleteDirectory(root);
        }
        System.out.println("PlagiarismTest: " + passed + " passed, "
                + failed + " failed");
        if (failed > 0) {
            System.exit(1);
        }
    }

    // Checks how JavaTokenizer splits source into tokens and lines.
    private static void checkTokenizer() {
        check(JavaTokenizer.split("int x = 1; // c\n/* b */ y++;").equals(
                Arrays.asList(INT_KEYWORD, "x", ASSIGN, "1", SEMICOLON, "y",
                "+", "+", SEMICOLON)),
                "comments dropped, operators one char each");
        check(JavaTokenizer.split("s = \"a // not comment\";").equals(
                Arrays.asList("s", ASSIGN, "\"a // not comment\"",
                SEMICOLON)),
                "comment marker inside a string stays in the string");
        int stringTokens = 4;
        check(JavaTokenizer.split("s = \"a /* x */ b\";").size()
                == stringTokens, "block comment marker inside a string");
        check(JavaTokenizer.split("c = '\\'';").equals(Arrays.asList(
                "c", ASSIGN, "'\\''", SEMICOLON)), "escaped char literal");
        check(JavaTokenizer.split("c = '\"';").equals(Arrays.asList(
                "c", ASSIGN, "'\"'", SEMICOLON)),
                "double quote char literal");
        check(JavaTokenizer.split("s = \"a\\\"b\";").equals(Arrays.asList(
                "s", ASSIGN, "\"a\\\"b\"", SEMICOLON)),
                "escaped quote in string");
        check(JavaTokenizer.split("s = \"open\nint y;")
                .contains(INT_KEYWORD),
                "unclosed string stops at end of line");
        check(JavaTokenizer.split("/* never closed int x;").isEmpty(),
                "unclosed block comment swallows the rest");
        check(JavaTokenizer.split("double d = 3.14e10;").contains("3.14e10"),
                "number with exponent");
        note("1_000L splits into "
                + JavaTokenizer.split("long n = 1_000L;"));
        List<String> negative = JavaTokenizer.split("x = 1e-5;");
        System.out.println("   1e-5 splits into " + negative);
        check(JavaTokenizer.split("int été = 2;").contains(
                "été"), "unicode identifier");
        check(JavaTokenizer.split("String s = \"中文\";").contains(
                "\"中文\""), "unicode string");
        check(JavaTokenizer.split("").isEmpty(), "empty source");
        check(JavaTokenizer.split("   \n\t ").isEmpty(), "spaces only");
        check(JavaTokenizer.shapeToken("count").equals("ID")
                && JavaTokenizer.shapeToken(INT_KEYWORD).equals(INT_KEYWORD)
                && JavaTokenizer.shapeToken("\"x\"").equals("STR")
                && JavaTokenizer.shapeToken("'x'").equals("STR")
                && JavaTokenizer.shapeToken("42").equals("42")
                && JavaTokenizer.shapeToken("{").equals("{"),
                "shapeToken kinds");
        check(JavaTokenizer.shapeOf("int a = b + 1;").equals(
                JavaTokenizer.shapeOf("int zz = qq + 1;")),
                "renamed names give the same shape");
        checkTokenLines();
    }

    // Checks the line numbers and offsets JavaTokenizer gives tokens.
    private static void checkTokenLines() {
        List<JavaToken> tokens = JavaTokenizer.tokenize("a\n/* x\ny */\nb");
        int expectedTokens = 2;
        int lastLine = 4;
        check(tokens.size() == expectedTokens
                && tokens.get(0).getLine() == 1
                && tokens.get(1).getLine() == lastLine,
                "line numbers count lines inside comments");
        List<JavaToken> crTokens = JavaTokenizer.tokenize("a\r\nb\r\nc");
        int thirdToken = 2;
        int thirdLine = 3;
        check(crTokens.get(thirdToken).getLine() == thirdLine,
                "CRLF line numbers");
        // Known limitation: only \n starts a new line, so a file saved
        // with old Mac CR-only endings has every token on line 1. The
        // scores are not changed; only the line shown for a match is.
        List<JavaToken> oldMac = JavaTokenizer.tokenize("a\rb\rc");
        note("CR-only files number every token line "
                + oldMac.get(thirdToken).getLine());
        int expectedStart = 12;
        int expectedEnd = 13;
        check(tokens.get(1).getStart() == expectedStart
                && tokens.get(1).getEnd() == expectedEnd,
                "token start/end offsets");
        List<String> textBlock = JavaTokenizer.split(
                "String s = \"\"\"\n  hello // x\n  \"\"\";\nint y;");
        // Known limitation: text blocks (\"\"\") are read as empty
        // strings around code; CS102 answers do not use them
        note("text block tokens: " + textBlock);
    }

    // Checks k-gram hashing and fingerprint choice in Winnowing.
    private static void checkWinnowing() {
        List<String> shapes = Winnowing.normalise(JavaTokenizer.tokenize(
                "int a = b + c * d - e / f;"));
        List<Integer> hashes = Winnowing.hashKGrams(shapes, KGRAM_SIZE);
        check(hashes.size() == shapes.size() - KGRAM_SIZE + 1,
                "k-gram count n-k+1");
        int fewTokens = 3;
        check(Winnowing.hashKGrams(shapes.subList(0, fewTokens), KGRAM_SIZE)
                .isEmpty(), "fewer tokens than k gives none");
        check(Winnowing.hashKGrams(shapes, KGRAM_SIZE).equals(
                Winnowing.hashKGrams(new ArrayList<>(shapes), KGRAM_SIZE)),
                "hashing deterministic");
        List<Fingerprint> prints = Winnowing.selectFingerprints(hashes,
                WINDOW);
        List<Fingerprint> again = Winnowing.selectFingerprints(hashes,
                WINDOW);
        check(prints.size() == again.size() && prints.size() > 0,
                "fingerprints deterministic");
        check(Winnowing.selectFingerprints(new ArrayList<Integer>(), WINDOW)
                .isEmpty(), "no hashes no prints");
        check(Winnowing.selectFingerprints(Arrays.asList(SHORT_HASHES),
                WINDOW).size() == 1,
                "short list gives one print (window smaller)");
        List<Fingerprint> same = Winnowing.selectFingerprints(
                Arrays.asList(EQUAL_HASHES), WINDOW);
        check(same.size() >= 1, "equal hashes give prints");
        // Every window of w consecutive hashes is covered
        List<Integer> mixed = Arrays.asList(MIXED_HASHES);
        List<Fingerprint> chosen = Winnowing.selectFingerprints(mixed,
                WINDOW);
        Set<Integer> chosenHashes = new HashSet<>();
        for (Fingerprint fingerprint : chosen) {
            chosenHashes.add(fingerprint.getHash());
        }
        boolean covered = true;
        for (int start = 0; start + WINDOW <= mixed.size(); start++) {
            int min = Integer.MAX_VALUE;
            for (int i = start; i < start + WINDOW; i++) {
                min = Math.min(min, mixed.get(i));
            }
            if (!chosenHashes.contains(min)) {
                covered = false;
            }
        }
        check(covered, "the minimum of every window is a fingerprint");
    }

    // Checks which lines CodeLineCounter counts as code.
    private static void checkLineCounter(Path root) throws Exception {
        Question question = new Question(QUESTION, QUESTION_FOLDER,
                QUESTION_FILE, 1);
        Path folder = root.resolve("count");
        Files.createDirectories(folder);
        Files.writeString(folder.resolve(QUESTION_FILE), "class Q1a {\n"
                + "  int a;\n  {\n  }\n  // comment\n  /* b */\n\n"
                + "  int b;\n}\n");
        Set<String> lines = CodeLineCounter.readLines(folder, question);
        int expectedLines = 3;
        check(lines.contains("class Q1a {") && lines.contains("int a ;")
                && lines.size() == expectedLines,
                "lines of code, layout lines dropped: " + lines);
        Set<String> starter = new HashSet<>(Arrays.asList("int a ;"));
        int expectedNew = 2;
        check(CodeLineCounter.countNewLines(folder, question, starter)
                == expectedNew, "starter lines not counted");
        check(CodeLineCounter.countNewLines(null, question, starter) == 0,
                "null folder 0");
        check(CodeLineCounter.countNewLines(root.resolve("nope"), question,
                starter) == 0, "missing folder 0");
    }

    // Checks how LineComparison marks the lines of two files.
    private static void checkLineComparison() {
        String starter = "public class Q {\n  // TODO\n}\n";
        String firstFile = "public class Q {\n  int total = 0;\n"
                + "  for (int i = 0; i < 10; i++) { total += i; }\n"
                + "  String mine = \"a\";\n}\n";
        String secondFile = "public class Q {\n  int sum = 0;\n"
                + "  for (int j = 0; j < 10; j++) { sum += j; }\n"
                + "  double other = 2.5 * 3;\n}\n";
        LineComparison comparison = new LineComparison(firstFile,
                secondFile, starter);
        List<LineKind> kinds = comparison.getFirstKinds();
        check(comparison.getFirstLines().size() == kinds.size(),
                "a kind per line");
        check(kinds.get(0) == LineKind.STARTER,
                "starter line marked STARTER (" + kinds.get(0)
                + CLOSE_BRACKET);
        int loopLine = 2;
        check(kinds.get(loopLine) == LineKind.SAME,
                "renamed loop line marked SAME (" + kinds.get(loopLine)
                + CLOSE_BRACKET);
        int ownLine = 3;
        check(kinds.get(ownLine) == LineKind.OWN,
                "own line marked OWN (" + kinds.get(ownLine)
                + CLOSE_BRACKET);
        check(LineComparison.countCode(comparison.getFirstLines(),
                kinds, LineKind.SAME) >= 1,
                "countCode counts SAME lines");
        int lastSameLine = 4;
        check(LineComparison.findSameParts(Arrays.asList(LineKind.SAME,
                LineKind.BLANK, LineKind.SAME, LineKind.OWN, LineKind.SAME))
                .equals(Arrays.asList(0, lastSameLine)),
                "same parts across blanks");
        LineComparison empty = new LineComparison("", "", "");
        check(empty.getFirstKinds().size() == empty.getFirstLines().size(),
                "empty files");
        LineComparison crlf = new LineComparison(firstFile.replace(NEWLINE,
                "\r\n"), secondFile, starter);
        check(crlf.getFirstKinds().equals(comparison.getFirstKinds()),
                "CRLF first file marked the same as LF");
    }

    // Checks both similarity detectors: renamed and verbatim copies,
    // starter code, short answers, the threshold and min lines.
    private static void checkDetectors(Path root) throws Exception {
        Path starterDir = root.resolve(STARTER_FOLDER);
        Files.createDirectories(starterDir.resolve(QUESTION_FOLDER));
        String starterCode = header("", "") + "public class Q1a {\n"
                + longStarter() + "}\n";
        Files.writeString(starterDir.resolve(QUESTION_FOLDER)
                .resolve(QUESTION_FILE), starterCode);
        List<Submission> subs = new ArrayList<>();
        subs.add(student(AMY, header("Amy", "amy@x"),
                longAnswer(MAIN_PREFIX)));
        subs.add(student("bob", header("Bob", "bob@x"), longAnswer("b")));
        subs.add(student("cat", header("Cat", "cat@x"), otherAnswer()));
        subs.add(student("dan", header("Dan", "dan@x"), longStarter()));
        subs.add(student("eve", header("Eve", "eve@x"), longStarter()));
        subs.add(student("fay", header("Fay", "fay@x"), shortAnswer("f")));
        subs.add(student("gus", header("Gus", "gus@x"), shortAnswer("g")));
        WinnowingSimilarityDetector winnow = new WinnowingSimilarityDetector(
                config);
        List<SimilarityPair> pairs = winnow.findSimilar(subs, assessment,
                starterDir);
        check(hasPair(pairs, AMY, "bob", QUESTION),
                "winnowing flags renamed copy amy/bob: " + describe(pairs));
        check(!hasPair(pairs, AMY, "cat", QUESTION),
                "winnowing does not flag a different answer");
        check(!hasPair(pairs, "dan", "eve", QUESTION),
                "winnowing ignores two untouched starter files");
        check(!hasPair(pairs, "fay", "gus", QUESTION),
                "winnowing ignores short answers under min lines");
        boolean sorted = true;
        for (int i = 1; i < pairs.size(); i++) {
            if (pairs.get(i - 1).getSimilarity() < pairs.get(i)
                    .getSimilarity()) {
                sorted = false;
            }
        }
        check(sorted, "pairs most similar first");
        AppConfig strict = TestConfig.load();
        strict.setString(ConfigKeys.PLAGIARISM_MIN_LINES, MIN_LINES);
        strict.setString(ConfigKeys.PLAGIARISM_THRESHOLD, "1.01");
        check(new WinnowingSimilarityDetector(strict).findSimilar(subs,
                assessment, starterDir).isEmpty(), "threshold over 1 flags "
                + "nothing");
        AppConfig longMin = TestConfig.load();
        longMin.setString(ConfigKeys.PLAGIARISM_MIN_LINES, "500");
        check(new WinnowingSimilarityDetector(longMin).findSimilar(subs,
                assessment, starterDir).isEmpty(), "min lines 500 flags "
                + "nothing");
        // Token detector: a verbatim copy is flagged, an unrelated one not
        List<Submission> verbatim = new ArrayList<>();
        verbatim.add(student(HAL, header("Hal", "hal@x"),
                longAnswer(MAIN_PREFIX)));
        verbatim.add(student("ivy", header("Ivy", "ivy@x"),
                longAnswer(MAIN_PREFIX)));
        verbatim.add(student("jay", header("Jay", "jay@x"), otherAnswer()));
        TokenSimilarityDetector token = new TokenSimilarityDetector(config);
        List<SimilarityPair> tokenPairs = token.findSimilar(verbatim,
                assessment, starterDir);
        check(hasPair(tokenPairs, HAL, "ivy", QUESTION),
                "token detector flags verbatim copy: "
                + describe(tokenPairs));
        check(!hasPair(tokenPairs, HAL, "jay", QUESTION),
                "token detector ignores a different answer");
        // A student with no files is ignored, not an error
        Submission nobody = new Submission("kim", SECTION, working);
        nobody.setWorkingCopyPath(working.resolve("kim-missing"));
        nobody.setStatus(SubmissionStatus.GRADED);
        List<Submission> withMissing = new ArrayList<>(subs);
        withMissing.add(nobody);
        check(winnow.findSimilar(withMissing, assessment, starterDir).size()
                == pairs.size(), "missing working copy ignored");
        // Single submission: nothing
        check(winnow.findSimilar(subs.subList(0, 1), assessment, starterDir)
                .isEmpty(), "one submission no pairs");
    }

    // Checks IdenticalCopyFinder: same header and code, renamed
    // variables, missing header, label spelling, comments and CRLF.
    private static void checkIdentical(Path root) throws Exception {
        IdenticalCopyFinder finder = new IdenticalCopyFinder(config);
        String code = longAnswer(MAIN_PREFIX);
        List<Submission> subs = new ArrayList<>();
        subs.add(student(FIRST_COPY, header("Ann Lee", ANN_EMAIL), code));
        subs.add(student(SECOND_COPY, header("  ann   LEE ",
                "ANN.LEE@x.edu"), code));
        subs.add(student("id3", header("Ann Lee", ANN_EMAIL),
                longAnswer("b")));
        subs.add(student(NO_HEADER, "public class Q1a {}\n", code));
        subs.add(student("id5", header("", ANN_EMAIL), code));
        List<SimilarityPair> pairs = finder.find(subs, assessment);
        SimilarityPair nameCase = find(pairs, FIRST_COPY, SECOND_COPY,
                QUESTION);
        double fullMatch = 1.0;
        check(nameCase != null && nameCase.isIdentical()
                && nameCase.isSameEmail()
                && nameCase.getSimilarity() == fullMatch,
                "same name (case/space) + email + code is identical");
        SimilarityPair renamed = find(pairs, FIRST_COPY, "id3", QUESTION);
        check(renamed != null && !renamed.isIdentical()
                && renamed.isSameEmail(),
                "renamed variables: same Email ID only");
        check(find(pairs, FIRST_COPY, NO_HEADER, QUESTION) == null
                && find(pairs, SECOND_COPY, NO_HEADER, QUESTION) == null,
                "missing header gives nothing");
        SimilarityPair blankName = find(pairs, FIRST_COPY, "id5", QUESTION);
        check(blankName != null && !blankName.isIdentical()
                && blankName.isSameEmail(),
                "blank name with same email: same Email ID, not identical");
        check(find(pairs, FIRST_COPY, SECOND_COPY, "Q1b") == null,
                "no pair for a question where nobody has the file");
        // Different domain, same local part
        List<Submission> domains = new ArrayList<>();
        domains.add(student("dm1", header("Bo", "bo@smu.edu.sg"), code));
        domains.add(student("dm2", header("Bo", "bo@gmail.com"), code));
        SimilarityPair domainPair = find(finder.find(domains, assessment),
                "dm1", "dm2", QUESTION);
        String domainResult = "no pair";
        if (domainPair != null) {
            domainResult = "pair identical=" + domainPair.isIdentical();
        }
        System.out.println("   bo@smu.edu.sg vs bo@gmail.com: "
                + domainResult);
        checkLabelSpelling(finder, code);
        // Code differs only by comments and whitespace: identical
        List<Submission> comments = new ArrayList<>();
        comments.add(student("cm1", header("Ed", "ed@x"), code));
        comments.add(student("cm2", header("Ed", "ed@x"),
                code.replace(NEWLINE, "\n// note\n   ")));
        SimilarityPair commentPair = find(finder.find(comments, assessment),
                "cm1", "cm2", QUESTION);
        check(commentPair != null && commentPair.isIdentical(),
                "comments and spacing do not stop identical");
        check(finder.find(subs, null).isEmpty(), "null assessment");
        // CRLF and windows-1252 file
        List<Submission> crlf = new ArrayList<>();
        crlf.add(student("cr1", header("Fo", "fo@x"), code));
        String crlfUser = "cr2";
        Submission crlfCopy = student(crlfUser, header("Fo", "fo@x"), code);
        Path file = crlfCopy.getWorkingCopyPath().resolve(crlfUser)
                .resolve(QUESTION_FOLDER).resolve(QUESTION_FILE);
        Files.writeString(file, Files.readString(file).replace(NEWLINE,
                "\r\n"));
        crlf.add(crlfCopy);
        SimilarityPair crlfPair = find(finder.find(crlf, assessment), "cr1",
                crlfUser, QUESTION);
        check(crlfPair != null && crlfPair.isIdentical(),
                "CRLF copy still identical");
    }

    // Checks headers whose labels are written with a space before the
    // colon or in lower case.
    private static void checkLabelSpelling(IdenticalCopyFinder finder,
            String code) throws Exception {
        // Label written with a space before the colon (as the header
        // validator accepts)
        String spacedHeader = "/*\n * Name : Cy Ng\n * Email ID : cy@x\n"
                + " */\n";
        List<Submission> spaced = new ArrayList<>();
        spaced.add(student("sp1", spacedHeader, code));
        spaced.add(student("sp2", spacedHeader, code));
        check(find(finder.find(spaced, assessment), "sp1", "sp2", QUESTION)
                != null, "'Email ID :' with a space before the colon is "
                + "read like the header validator reads it");
        // Header label in lower case
        String lowerHeader = "/*\n * name: Di\n * email id: di@x\n */\n";
        List<Submission> lower = new ArrayList<>();
        lower.add(student("lo1", lowerHeader, code));
        lower.add(student("lo2", lowerHeader, code));
        String lowerResult = "matched";
        if (find(finder.find(lower, assessment), "lo1", "lo2", QUESTION)
                == null) {
            lowerResult = "not matched";
        }
        System.out.println("   lower-case labels: " + lowerResult);
    }

    // Checks the flags PlagiarismService writes and reads back.
    private static void checkService(Path root) throws Exception {
        Path starterDir = root.resolve(STARTER_FOLDER);
        GradingRun run = new GradingRun("RUN-test", assessment, SECTION,
                LocalDateTime.now());
        run.setStarterDirectory(starterDir);
        String code = longAnswer(MAIN_PREFIX);
        Submission first = student(SAM_FIRST, header(SAM_NAME, SAM_EMAIL),
                code);
        first.setStudent(new Student("1", SAM_FIRST, "A", "Sam", "sa@x"));
        Submission copy = student(SAM_COPY, header(SAM_NAME, SAM_EMAIL),
                code);
        Submission renamed = student(SAM_RENAMED, header("Cy", "cy@x"),
                longAnswer("q"));
        Submission different = student("sd", header("Di", "di@x"),
                otherAnswer());
        Submission running = student("se", header(SAM_NAME, SAM_EMAIL),
                code);
        running.setStatus(SubmissionStatus.RUNNING);
        for (Submission submission : Arrays.asList(first, copy, renamed,
                different, running)) {
            run.addSubmission(submission);
        }
        PlagiarismService service = new PlagiarismService(config,
                new WinnowingSimilarityDetector(config), null);
        List<SimilarityPair> pairs = service.check(run);
        check(find(pairs, SAM_FIRST, SAM_COPY, QUESTION) != null
                && find(pairs, SAM_FIRST, SAM_COPY, QUESTION).isIdentical(),
                "service: identical pair found");
        // sa, sb and sc share the same rename-proof code, and code three
        // answers share proves nothing (WinnowingSimilarityDetector)
        check(find(pairs, SAM_FIRST, SAM_RENAMED, QUESTION) == null,
                "service: a renamed copy three answers share is not flagged");
        check(find(pairs, SAM_FIRST, "se", QUESTION) == null,
                "service: a running submission is not compared");
        int flagsFirst = countFlags(first);
        check(flagsFirst == pairs.size() - countPairsWithout(pairs,
                SAM_FIRST), "service: one flag on sa per pair it is in");
        check(messageOf(copy, SAM_FIRST).contains("Sam A (sa)"),
                "flag names the other student with full name: "
                + messageOf(copy, SAM_FIRST));
        check(messageOf(first, SAM_COPY)
                .endsWith("same Name, Email ID and code."),
                "identical note in message");
        List<SimilarityPair> readBack = service.listPairs(run);
        check(readBack.size() == pairs.size(), "listPairs reads back all "
                + "pairs (" + readBack.size() + " of " + pairs.size()
                + CLOSE_BRACKET);
        SimilarityPair back = find(readBack, SAM_FIRST, SAM_COPY, QUESTION);
        check(back != null && back.isIdentical() && back.isSameEmail(),
                "listPairs: identical read back");
        SimilarityPair backRenamed = find(readBack, SAM_FIRST, SAM_RENAMED,
                QUESTION);
        check(backRenamed == null,
                "listPairs: no pair kept for the shared renamed copy");
        checkSameEmail(service, starterDir);
        // Re-check keeps fix status of earlier flags, and no duplicates
        for (Anomaly anomaly : first.getAnomalies()) {
            if (anomaly.getType() == AnomalyType.SIMILAR_CODE) {
                anomaly.setFixStatus(FixStatus.APPROVED);
            }
        }
        service.check(run);
        check(countFlags(first) == flagsFirst,
                "re-check does not duplicate flags");
        boolean kept = true;
        for (Anomaly anomaly : first.getAnomalies()) {
            if (anomaly.getType() == AnomalyType.SIMILAR_CODE
                    && anomaly.getFixStatus() != FixStatus.APPROVED) {
                kept = false;
            }
        }
        check(kept, "re-check keeps the earlier review status");
        checkPlagiarismOff(starterDir, code);
        check(service.listPairs(null).isEmpty()
                && service.check(null).isEmpty(), "null run");
        // listPairs ignores flags from another run
        check(service.listPairs(new GradingRun("RUN-other", assessment,
                SECTION, LocalDateTime.now())).isEmpty(),
                "listPairs of an empty run");
        checkDottedNames(service, starterDir, code);
    }

    // Same email, different code: the pair read back says same Email ID.
    private static void checkSameEmail(PlagiarismService service,
            Path starterDir) throws Exception {
        GradingRun emailRun = new GradingRun("RUN-2", assessment, SECTION,
                LocalDateTime.now());
        emailRun.setStarterDirectory(starterDir);
        Submission emailFirst = student("e1", header("Jo", "jo@x"),
                longAnswer(MAIN_PREFIX));
        Submission emailSecond = student("e2", header("Jo", "jo@x"),
                otherAnswer());
        emailRun.addSubmission(emailFirst);
        emailRun.addSubmission(emailSecond);
        service.check(emailRun);
        SimilarityPair emailPair = find(service.listPairs(emailRun), "e1",
                "e2", QUESTION);
        check(emailPair != null && emailPair.isSameEmail()
                && !emailPair.isIdentical(),
                "listPairs: same Email ID read back");
    }

    // With plagiarism.enabled=false, identical copies are still flagged.
    private static void checkPlagiarismOff(Path starterDir, String code)
            throws Exception {
        AppConfig off = TestConfig.load();
        off.setString(ConfigKeys.PLAGIARISM_ENABLED, OFF);
        off.setString(ConfigKeys.PLAGIARISM_MIN_LINES, MIN_LINES);
        off.setString(ConfigKeys.DATABASE_ENABLED, OFF);
        GradingRun offRun = new GradingRun("RUN-3", assessment, SECTION,
                LocalDateTime.now());
        offRun.setStarterDirectory(starterDir);
        Submission offFirst = student(OFF_FIRST, header("Ola", "ola@x"),
                code);
        Submission offCopy = student("o2", header("Ola", "ola@x"), code);
        Submission offRenamed = student("o3", header("Pip", "pip@x"),
                longAnswer("z"));
        offRun.addSubmission(offFirst);
        offRun.addSubmission(offCopy);
        offRun.addSubmission(offRenamed);
        List<SimilarityPair> offPairs = new PlagiarismService(off,
                new WinnowingSimilarityDetector(off), null).check(offRun);
        check(find(offPairs, OFF_FIRST, "o2", QUESTION) != null
                && find(offPairs, OFF_FIRST, "o3", QUESTION) == null,
                "plagiarism off: identical still flagged, renamed not");
    }

    // A username with a dot and dash round trips through the flag id.
    private static void checkDottedNames(PlagiarismService service,
            Path starterDir, String code) throws Exception {
        GradingRun dottedRun = new GradingRun("RUN-4", assessment, SECTION,
                LocalDateTime.now());
        dottedRun.setStarterDirectory(starterDir);
        Submission dottedFirst = student("a.b-2025", header("Q", "q@x"),
                code);
        Submission dottedSecond = student("c.d-2025", header("Q", "q@x"),
                code);
        dottedRun.addSubmission(dottedFirst);
        dottedRun.addSubmission(dottedSecond);
        service.check(dottedRun);
        check(find(service.listPairs(dottedRun), "a.b-2025", "c.d-2025",
                QUESTION) != null, "dotted usernames read back");
    }

    // Counts a submission's similar code flags.
    private static int countFlags(Submission submission) {
        int count = 0;
        for (Anomaly anomaly : submission.getAnomalies()) {
            if (anomaly.getType() == AnomalyType.SIMILAR_CODE) {
                count++;
            }
        }
        return count;
    }

    // Counts the pairs a student is not in.
    private static int countPairsWithout(List<SimilarityPair> pairs,
            String user) {
        int count = 0;
        for (SimilarityPair pair : pairs) {
            if (!pair.getFirstUsername().equals(user)
                    && !pair.getSecondUsername().equals(user)) {
                count++;
            }
        }
        return count;
    }

    // Gives the message of the similar code flag that names the other
    // student, or "" when there is none.
    private static String messageOf(Submission submission, String other) {
        for (Anomaly anomaly : submission.getAnomalies()) {
            if (anomaly.getType() == AnomalyType.SIMILAR_CODE
                    && anomaly.getId().contains(SLASH + other + SLASH)) {
                return anomaly.getMessage();
            }
        }
        return "";
    }

    // Says whether two students are paired on a question.
    private static boolean hasPair(List<SimilarityPair> pairs,
            String firstUser, String secondUser, String questionId) {
        return find(pairs, firstUser, secondUser, questionId) != null;
    }

    // Finds the pair of two students on a question, in either order, or
    // null when there is none.
    private static SimilarityPair find(List<SimilarityPair> pairs,
            String firstUser, String secondUser, String questionId) {
        for (SimilarityPair pair : pairs) {
            boolean match = (pair.getFirstUsername().equals(firstUser)
                    && pair.getSecondUsername().equals(secondUser))
                    || (pair.getFirstUsername().equals(secondUser)
                    && pair.getSecondUsername().equals(firstUser));
            if (match && pair.getQuestionId().equals(questionId)) {
                return pair;
            }
        }
        return null;
    }

    // Writes the pairs on one line, each with its percentage.
    private static String describe(List<SimilarityPair> pairs) {
        List<String> shown = new ArrayList<>();
        for (SimilarityPair pair : pairs) {
            shown.add(pair.getFirstUsername() + SLASH
                    + pair.getSecondUsername() + SLASH
                    + pair.getQuestionId() + "="
                    + Math.round(pair.getSimilarity() * PERCENT));
        }
        return shown.toString();
    }

    // Writes a student's Q1a.java with empty Q2 and Q3 folders, and
    // returns their graded submission.
    private static Submission student(String user, String headerText,
            String body) throws Exception {
        Path copy = working.resolve(user);
        FileUtils.deleteDirectory(copy);
        Path folder = copy.resolve(user).resolve(QUESTION_FOLDER);
        Files.createDirectories(folder);
        Files.createDirectories(copy.resolve(user).resolve("Q2"));
        Files.createDirectories(copy.resolve(user).resolve("Q3"));
        String text = headerText + "public class Q1a {\n" + body + "}\n";
        Files.writeString(folder.resolve(QUESTION_FILE), text);
        Submission submission = new Submission(user, SECTION, copy);
        submission.setWorkingCopyPath(copy);
        submission.setStatus(SubmissionStatus.GRADED);
        return submission;
    }

    // Writes a header comment with a name and an Email ID.
    private static String header(String name, String email) {
        return "/*\n * Name: " + name + "\n * Email ID: " + email + "\n */\n";
    }

    // A long answer whose names start with the prefix; two prefixes give
    // the same code with renamed variables.
    private static String longAnswer(String prefix) {
        StringBuilder code = new StringBuilder();
        code.append("  public static int ").append(prefix)
                .append("Sum(int[] ").append(prefix).append("Values) {\n");
        code.append("    int ").append(prefix).append("Total = 0;\n");
        for (int i = 0; i < REPEATS; i++) {
            code.append("    if (").append(prefix).append("Values.length > ")
                    .append(i).append(") { ").append(prefix)
                    .append("Total += ").append(prefix).append("Values[")
                    .append(i).append("] * ").append(i + 1)
                    .append("; }\n");
        }
        code.append("    while (").append(prefix).append("Total > 1000) { ")
                .append(prefix).append("Total = ").append(prefix)
                .append("Total / 2; }\n");
        code.append("    return ").append(prefix).append("Total;\n  }\n");
        return code.toString();
    }

    // A long answer that shares no plan with longAnswer.
    private static String otherAnswer() {
        StringBuilder code = new StringBuilder();
        code.append("  public static String join(String[] words) {\n");
        code.append("    StringBuilder out = new StringBuilder();\n");
        for (int i = 0; i < REPEATS; i++) {
            code.append("    try { out.append(words[").append(i)
                    .append("].toUpperCase()).append(\"-").append(i)
                    .append("\"); } catch (RuntimeException e").append(i)
                    .append(") { out.append('?'); }\n");
        }
        code.append("    return out.toString().trim();\n  }\n");
        return code.toString();
    }

    // The long starter code that untouched answers keep.
    private static String longStarter() {
        StringBuilder code = new StringBuilder();
        for (int i = 0; i < REPEATS; i++) {
            code.append("  public static double helper").append(i)
                    .append("(double x, double y) {\n    return x * ")
                    .append(i).append(" + y / ").append(i + 1)
                    .append(" - Math.sqrt(x);\n  }\n");
        }
        return code.toString();
    }

    // A short answer, under the min lines, as a method with this name.
    private static String shortAnswer(String name) {
        return "  static int " + name + "(int x) {\n    return x + 1;\n  }\n";
    }

    // Prints a known limitation: behaviour that is kept on purpose or is
    // not worth changing, so it is reported but does not fail the run
    private static void note(String message) {
        System.out.println("NOTE (known limitation): " + message);
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
