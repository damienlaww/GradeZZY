/*
 * File: IngestUnitTest.java
 *
 * Purpose: Unit checks for the ingest package: ZipExtractor (nested zips,
 * zip slip, backslash and CP437 names, limits, empty and damaged zips),
 * RosterReader (BOM, windows-1252, quotes, prefixes, missing columns),
 * SubmissionLoader names and duplicates, and WorkingCopyManager.
 *
 * Where it fits: Run by hand after compiling, as tests/README.md says.
 * Reads the fixed test settings (tests/test-config.properties), never
 * the live config.properties. No student code runs.
 */
import java.io.ByteArrayOutputStream;
import java.io.UncheckedIOException;
import java.nio.charset.Charset;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;
import java.util.zip.ZipEntry;
import java.util.zip.ZipException;
import java.util.zip.ZipOutputStream;

import gradezzy.config.AppConfig;
import gradezzy.config.ConfigKeys;
import gradezzy.ingest.IngestException;
import gradezzy.ingest.InvalidArchiveException;
import gradezzy.ingest.RosterReader;
import gradezzy.ingest.SubmissionLoader;
import gradezzy.ingest.WorkingCopyManager;
import gradezzy.ingest.ZipExtractor;
import gradezzy.model.Anomaly;
import gradezzy.model.Section;
import gradezzy.model.Student;
import gradezzy.model.Submission;
import gradezzy.model.SubmissionStatus;
import gradezzy.service.AssessmentFactory;
import gradezzy.util.FileUtils;

/**
 * Runs the ingest checks and prints how many passed.
 */
public class IngestUnitTest {

    // The section every loaded submission is for
    private static final Section SECTION = new Section("CS102", "G1");

    // The answer file of a student "u", and the same file inside a
    // student's own zip
    private static final String ANSWER = "u/Q1/Q1a.java";
    private static final String QUESTION_ANSWER = "Q1/Q1a.java";

    // The answer file of the working copy student "wc"
    private static final String WC_ANSWER = "wc/Q1/Q1a.java";

    // A file name used twice in one zip, with or without a case change
    private static final String CLASH_FILE = "u/Q1/A.java";

    // The starter folder name and the text of the starter's class file
    private static final String STARTER = "starter";

    // The name of a zip stored inside another zip
    private static final String INNER_ZIP = "inner.zip";

    // The macOS folder settings file that is always left out
    private static final String DS_STORE = ".DS_Store";

    // The student whose upload names are cleaned up
    private static final String ALICE = "alice";

    // An empty text
    private static final String EMPTY = "";

    // Short file contents whose text does not matter
    private static final String TEXT_1 = "1";
    private static final String TEXT_2 = "2";
    private static final String TEXT_A = "a";
    private static final String TEXT_B = "b";
    private static final String TEXT_C = "c";
    private static final String TEXT_D = "d";
    private static final String TEXT_M = "m";
    private static final String TEXT_X = "x";

    // The small zip limits: most unpacked megabytes and most entries
    private static final String SIZE_LIMIT_MB = "1";
    private static final String ENTRY_LIMIT = "3";

    // The bytes in a kilobyte and in a megabyte
    private static final int KILOBYTE = 1024;
    private static final int MEGABYTE = KILOBYTE * KILOBYTE;

    // The size of each of the two entries that are too big together
    private static final int BIG_ENTRY_BYTES = 600 * KILOBYTE;

    // The most bytes a zip of one megabyte of zeros should take
    private static final long SMALL_ZIP_BYTES = 20000;

    // A truncated zip keeps this part of the bytes: one over this
    private static final int TRUNCATE_PARTS = 2;

    // A zip is made from name and text pairs
    private static final int PAIR_SIZE = 2;

    // The number of rows in the rosters that hold two students
    private static final int TWO_ROWS = 2;

    // The byte order marks of UTF-8 and UTF-16 little endian files
    private static final byte[] UTF8_BOM = {(byte) 0xEF, (byte) 0xBB,
        (byte) 0xBF};
    private static final byte[] UTF16_BOM = {(byte) 0xFF, (byte) 0xFE};

    // The number of checks that passed and failed so far
    private static int passed = 0;
    private static int failed = 0;

    /**
     * Runs every check.
     *
     * @param args not used
     * @throws Exception if a temporary file cannot be written
     */
    public static void main(String[] args) throws Exception {
        Path root = Files.createTempDirectory("gradezzy-ingest-unit-")
                .toRealPath();
        try {
            AppConfig config = TestConfig.load();
            config.setString(ConfigKeys.WORKING_DIR,
                    root.resolve("working").toString());
            checkExtractor(root, config);
            checkLimits(root);
            checkRoster(root, config);
            checkNames(config);
            checkLoader(root, config);
            checkWorkingCopy(root, config);
        } finally {
            FileUtils.deleteDirectory(root);
        }
        System.out.println("IngestUnitTest: " + passed + " passed, "
                + failed + " failed");
        if (failed > 0) {
            System.exit(1);
        }
    }

    // Checks unpacking: nested zips, zip slip, odd names, empty, damaged
    // and clashing zips, and the files that are left out.
    private static void checkExtractor(Path root, AppConfig config)
            throws Exception {
        ZipExtractor extractor = new ZipExtractor(config);
        Path plain = checkPlainZips(root, extractor);
        checkZipSlip(root, extractor);
        checkSafeEntries(root, extractor);
        checkEntryNames(root, extractor);
        checkEmptyZips(root, extractor);
        checkDamagedZips(root, extractor, plain);
        checkRefusedEntries(root, extractor);
        checkOtherEntries(root, extractor, plain);
    }

    // Checks a plain zip and a zip holding a zip; returns the plain zip.
    private static Path checkPlainZips(Path root, ZipExtractor extractor)
            throws Exception {
        // Plain zip
        Path plain = zip(root, "plain.zip", new String[] {ANSWER,
                "class Q1a{}", "u/Q2/Q2a.java", "class Q2a{}"});
        Path target = extractor.extract(plain, root.resolve("t-plain"));
        check(Files.readString(target.resolve(ANSWER))
                .equals("class Q1a{}"), "plain zip unpacked");
        // Nested zip is written as a file, not unpacked by ZipExtractor
        byte[] inner = zipBytes(StandardCharsets.UTF_8, false,
                new String[] {QUESTION_ANSWER, TEXT_X});
        Path nested = zipRaw(root, "nested.zip", StandardCharsets.UTF_8,
                false, new String[] {INNER_ZIP}, new byte[][] {inner});
        Path nestedTarget = extractor.extract(nested,
                root.resolve("t-nested"));
        check(Files.isRegularFile(nestedTarget.resolve(INNER_ZIP)),
                "nested zip kept as a file by the extractor");
        return plain;
    }

    // Checks that entries leaving the target folder are refused and
    // write nothing outside it.
    private static void checkZipSlip(Path root, ZipExtractor extractor)
            throws Exception {
        check(extractFails(extractor, zip(root, "slip1.zip",
                new String[] {"../evil.java", TEXT_X}),
                root.resolve("t-slip1")), "../ entry refused");
        check(!Files.exists(root.resolve("evil.java")),
                "../ entry wrote nothing outside");
        check(!Files.exists(root.resolve("t-slip1")),
                "refused zip leaves no target folder");
        check(extractFails(extractor, zip(root, "slip2.zip",
                new String[] {"u/../../evil2.java", TEXT_X}),
                root.resolve("t-slip2")), "nested ../ entry refused");
        check(extractFails(extractor, zip(root, "slip3.zip",
                new String[] {"/tmp/evil3.java", TEXT_X}),
                root.resolve("t-slip3")), "absolute entry refused");
        check(extractFails(extractor, zip(root, "slip4.zip",
                new String[] {"C:/evil4.java", TEXT_X}),
                root.resolve("t-slip4")), "drive entry refused");
        check(extractFails(extractor, zip(root, "slip5.zip",
                new String[] {"..\\evil5.java", TEXT_X}),
                root.resolve("t-slip5")), "backslash ../ entry refused");
        check(extractFails(extractor, zip(root, "slip6.zip",
                new String[] {"u\\..\\..\\evil6.java", TEXT_X}),
                root.resolve("t-slip6")), "backslash nested ../ refused");
        check(!Files.exists(root.getParent().resolve("evil6.java")),
                "backslash slip wrote nothing outside");
    }

    // Checks which entry names isSafeEntry accepts.
    private static void checkSafeEntries(Path root, ZipExtractor extractor) {
        Path probe = root.resolve("probe");
        check(!extractor.isSafeEntry("a:b", probe), "colon unsafe");
        check(!extractor.isSafeEntry(EMPTY, probe), "empty unsafe");
        check(!extractor.isSafeEntry(null, probe), "null unsafe");
        check(!extractor.isSafeEntry(".", probe), "dot alone unsafe");
        check(extractor.isSafeEntry("Q1/./A.java", probe), "Q1/./A safe");
        check(!extractor.isSafeEntry("Q1/CON.java", probe), "CON unsafe");
        check(!extractor.isSafeEntry("Q1/a<b.java", probe), "< unsafe");
        check(!extractor.isSafeEntry("Q1/a.java.", probe),
                "trailing dot unsafe");
        check(extractor.isSafeEntry("Q1/console.java", probe),
                "console.java safe");
    }

    // Checks names with backslashes, names in CP437 and names in UTF-8.
    private static void checkEntryNames(Path root, ZipExtractor extractor)
            throws Exception {
        // Backslash names
        Path backslash = zip(root, "back.zip",
                new String[] {"u\\Q1\\Q1a.java", TEXT_B});
        Path backTarget = extractor.extract(backslash,
                root.resolve("t-back"));
        check(Files.exists(backTarget.resolve(ANSWER)),
                "backslash names become folders");
        check(extractor.listFileNames(backslash).equals(Arrays.asList(
                ANSWER)), "listFileNames converts backslashes");
        // CP437 names (no UTF-8 flag)
        Path cp437 = zipRaw(root, "cp437.zip", Charset.forName("CP437"),
                false, new String[] {"Jos\u00e9/Q1/Q1a.java"},
                new byte[][] {TEXT_C.getBytes()});
        Path cpTarget = extractor.extract(cp437, root.resolve("t-cp437"));
        check(Files.exists(cpTarget.resolve("Jos\u00e9/Q1/Q1a.java")),
                "CP437 name read as Jos\u00e9");
        // UTF-8 name
        Path utf = zip(root, "utf.zip",
                new String[] {"\u674e\u534e/Q1/Q1a.java", "u"});
        check(Files.exists(extractor.extract(utf, root.resolve("t-utf"))
                .resolve("\u674e\u534e/Q1/Q1a.java")), "UTF-8 name");
    }

    // Checks zips with nothing to grade, and the macOS and Windows extra
    // files that are left out.
    private static void checkEmptyZips(Path root, ZipExtractor extractor)
            throws Exception {
        // Empty zip
        Path empty = zipRaw(root, "empty.zip", StandardCharsets.UTF_8,
                false, new String[0], new byte[0][]);
        check(extractThrowsInvalid(extractor, empty,
                root.resolve("t-empty")), "empty zip refused as invalid");
        // Only folders
        Path folders = zipRaw(root, "folders.zip", StandardCharsets.UTF_8,
                false, new String[] {"u/", "u/Q1/"}, new byte[][] {null,
                null});
        check(extractThrowsInvalid(extractor, folders,
                root.resolve("t-folders")), "folders-only zip refused");
        // Only __MACOSX
        Path mac = zip(root, "mac.zip", new String[] {
                "__MACOSX/u/._Q1a.java", TEXT_M, DS_STORE, TEXT_D});
        check(extractThrowsInvalid(extractor, mac, root.resolve("t-mac")),
                "zip of only ignored files refused");
        // macOS extras ignored beside real files
        Path macReal = zip(root, "macreal.zip", new String[] {ANSWER, TEXT_A,
                "__MACOSX/u/Q1/._Q1a.java", TEXT_M, "u/Q1/._Q1a.java",
                TEXT_M, "u/.DS_Store", TEXT_D, "u/Thumbs.db", "t"});
        Path macTarget = extractor.extract(macReal, root.resolve("t-macr"));
        boolean extrasLeftOut = Files.exists(macTarget.resolve(ANSWER))
                && !Files.exists(macTarget.resolve("__MACOSX"))
                && !Files.exists(macTarget.resolve("u/Q1/._Q1a.java"))
                && !Files.exists(macTarget.resolve("u/.DS_Store"))
                && !Files.exists(macTarget.resolve("u/Thumbs.db"));
        check(extrasLeftOut, "macOS and Windows extras left out");
        Path macCase = zip(root, "maccase.zip", new String[] {ANSWER, TEXT_A,
                "u/thumbs.db", "t", "u/.ds_store", TEXT_D});
        Path macCaseTarget = extractor.extract(macCase,
                root.resolve("t-maccase"));
        // Known limitation: ingest.ignored.file.names is matched exactly,
        // and Windows always writes the name as Thumbs.db
        note("thumbs.db in lower case is kept (names are matched exactly): "
                + Files.exists(macCaseTarget.resolve("u/thumbs.db")));
    }

    // Checks zero-byte, junk and truncated zips, and a target folder
    // that already holds a file.
    private static void checkDamagedZips(Path root, ZipExtractor extractor,
            Path plain) throws Exception {
        // Zero-byte and junk files
        Path zero = root.resolve("zero.zip");
        Files.write(zero, new byte[0]);
        check(extractThrowsInvalid(extractor, zero, root.resolve("t-zero")),
                "zero-byte zip refused as damaged (InvalidArchive)");
        Path junk = root.resolve("junk.zip");
        Files.writeString(junk, "this is not a zip file at all");
        check(extractThrowsInvalid(extractor, junk, root.resolve("t-junk")),
                "text file named .zip refused as damaged");
        boolean junkList = false;
        try {
            extractor.listFileNames(junk);
        } catch (IngestException expected) {
            junkList = true;
        }
        check(junkList, "listFileNames on junk throws IngestException");
        // Truncated zip
        byte[] good = Files.readAllBytes(plain);
        Path cut = root.resolve("cut.zip");
        int cutLength = good.length / TRUNCATE_PARTS;
        Files.write(cut, Arrays.copyOf(good, cutLength));
        check(extractFails(extractor, cut, root.resolve("t-cut")),
                "truncated zip refused");
        // Non-empty target
        Path busy = root.resolve("t-busy");
        Files.createDirectories(busy);
        Files.writeString(busy.resolve("keep.txt"), "keep");
        boolean busyKept = extractFails(extractor, plain, busy)
                && Files.readString(busy.resolve("keep.txt")).equals("keep");
        check(busyKept, "non-empty target refused and kept");
    }

    // Checks entries that clash or are named as devices, and a name
    // with a control character.
    private static void checkRefusedEntries(Path root,
            ZipExtractor extractor) throws Exception {
        // Case clash
        check(extractThrowsInvalid(extractor, zip(root, "clash.zip",
                new String[] {CLASH_FILE, TEXT_1, "u/q1/a.java", TEXT_2}),
                root.resolve("t-clash")), "case clash refused");
        // Known limitation: a zip naming one file twice is refused as a
        // zip that cannot be unpacked, not as a damaged zip
        note("a zip naming one file twice is refused as damaged: "
                + extractThrowsInvalid(extractor, zip(root, "dup.zip",
                new String[] {CLASH_FILE, TEXT_1, CLASH_FILE, TEXT_2}),
                root.resolve("t-dup")));
        // Device name outside a question folder is skipped, inside stops
        List<String> skipped = new ArrayList<>();
        Path device = zip(root, "device.zip", new String[] {ANSWER, TEXT_A,
                "u/notes/CON.txt", TEXT_C});
        extractor.extract(device, root.resolve("t-device"), skipped);
        check(skipped.size() == 1 && skipped.get(0).contains("CON"),
                "device name outside question folder skipped and named");
        check(extractThrowsInvalid(extractor, zip(root, "device2.zip",
                new String[] {"u/Q1/AUX.java", TEXT_A, "u/Q2/Q2a.java",
                TEXT_B}), root.resolve("t-device2")),
                "device name inside a question folder stops the zip");
        // Control char name (macOS Icon\r) skipped
        List<String> iconSkipped = new ArrayList<>();
        extractor.extract(zip(root, "icon.zip", new String[] {"u/Icon\r",
                EMPTY, ANSWER, TEXT_A}), root.resolve("t-icon"), iconSkipped);
        check(iconSkipped.size() == 1 && iconSkipped.get(0).equals("u/Icon?"),
                "Icon\\r skipped and shown printable");
    }

    // Checks ./ prefixes, a zip inside its own target, a file and folder
    // with one name, and the file times read from a zip.
    private static void checkOtherEntries(Path root, ZipExtractor extractor,
            Path plain) throws Exception {
        // ./ prefixes
        Path dot = zipRaw(root, "dot.zip", StandardCharsets.UTF_8, false,
                new String[] {"./", "./u/Q1/Q1a.java"},
                new byte[][] {null, TEXT_D.getBytes()});
        check(Files.exists(extractor.extract(dot, root.resolve("t-dot"))
                .resolve(ANSWER)), "./ prefix entries accepted");
        // Archive inside its own target
        Path selfTarget = root.resolve("t-self");
        Files.createDirectories(selfTarget);
        Path selfZip = Files.copy(plain, selfTarget.resolve("p.zip"));
        check(extractFails(extractor, selfZip, selfTarget),
                "archive inside its target refused");
        check(Files.exists(selfZip), "archive inside its target not deleted");
        // File and folder with the same name
        check(extractFails(extractor, zip(root, "filefolder.zip",
                new String[] {"u/Q1", "file", ANSWER, TEXT_A}),
                root.resolve("t-ff")), "file and folder same name refused");
        // readFileTimes
        check(!extractor.readFileTimes(plain).isEmpty(),
                "readFileTimes reads times");
    }

    // Checks the size and entry limits of a zip.
    private static void checkLimits(Path root) throws Exception {
        AppConfig small = TestConfig.load();
        small.setString(ConfigKeys.MAX_ZIP_SIZE_MB, SIZE_LIMIT_MB);
        small.setString(ConfigKeys.MAX_ZIP_ENTRIES, ENTRY_LIMIT);
        ZipExtractor extractor = new ZipExtractor(small);
        int overLimit = MEGABYTE + 1;
        byte[] big = new byte[overLimit];
        Path bomb = zipRaw(root, "bomb.zip", StandardCharsets.UTF_8, false,
                new String[] {ANSWER}, new byte[][] {big});
        check(Files.size(bomb) < SMALL_ZIP_BYTES, "bomb compresses small");
        check(extractThrowsInvalid(extractor, bomb, root.resolve("t-bomb")),
                "over 1 MB unpacked refused");
        byte[] exact = new byte[MEGABYTE];
        Path exactZip = zipRaw(root, "exact.zip", StandardCharsets.UTF_8,
                false, new String[] {ANSWER}, new byte[][] {exact});
        check(!extractFails(extractor, exactZip, root.resolve("t-exact")),
                "exactly 1 MB accepted");
        byte[] half = new byte[BIG_ENTRY_BYTES];
        Path skippedBig = zipRaw(root, "skipbig.zip",
                StandardCharsets.UTF_8, false,
                new String[] {ANSWER, "__MACOSX/big"},
                new byte[][] {half, half});
        check(extractThrowsInvalid(extractor, skippedBig,
                root.resolve("t-skipbig")),
                "ignored entries still count towards the size limit");
        check(!extractFails(extractor, zip(root, "three.zip",
                new String[] {"a/Q1/1", TEXT_1, "a/Q1/2", TEXT_2, "a/Q1/3",
                "3"}), root.resolve("t-three")),
                "exactly 3 entries accepted");
        check(extractThrowsInvalid(extractor, zip(root, "four.zip",
                new String[] {"a/Q1/1", TEXT_1, "a/Q1/2", TEXT_2, "a/Q1/3",
                "3", "a/Q1/4", "4"}), root.resolve("t-four")),
                "4 entries over limit 3 refused");
        AppConfig zeroLimit = TestConfig.load();
        zeroLimit.setString(ConfigKeys.MAX_ZIP_SIZE_MB, "0");
        check(extractFails(new ZipExtractor(zeroLimit), exactZip,
                root.resolve("t-zero-limit")), "limit 0 refused");
    }

    // Checks reading a roster: encodings, quotes, prefixes, columns, rows
    // and the grade denominator.
    private static void checkRoster(Path root, AppConfig config)
            throws Exception {
        RosterReader reader = new RosterReader(config);
        String header = "OrgDefinedId,Username,Last Name,First Name,Email";
        List<Student> basic = checkRosterNames(root, reader, header);
        checkRosterLayouts(root, reader, header);
        checkRosterLookups(root, reader, header, basic);
    }

    // Checks how names are read: prefixes, zeros, a byte order mark,
    // windows-1252, quotes and a newline inside quotes. Returns the
    // students of the basic roster.
    private static List<Student> checkRosterNames(Path root,
            RosterReader reader, String header) throws Exception {
        List<Student> basic = reader.readRoster(write(root, "r1.csv",
                header + "\n#001,#ann,Lee,Ann,ann@x.edu\n"));
        check(basic.size() == 1 && basic.get(0).getUsername().equals("ann"),
                "basic roster, prefix removed");
        check(basic.get(0).getOrgDefinedId().equals("001"),
                "leading zeros of id kept (got "
                + basic.get(0).getOrgDefinedId() + ")");
        Path bom = root.resolve("bom.csv");
        Files.write(bom, concat(UTF8_BOM, (header
                + "\r\n#1,#bob,B,Bob,b@x\r\n")
                .getBytes(StandardCharsets.UTF_8)));
        check(readsOk(reader, bom, 1), "BOM header read");
        Path win = root.resolve("win.csv");
        Files.write(win, (header
                + "\r\n#1,#zoe,M\u00fcller,Zo\u00eb,z@x\r\n")
                .getBytes("windows-1252"));
        List<Student> winStudents = reader.readRoster(win);
        boolean winDecoded = winStudents.get(0).getLastName()
                .equals("M\u00fcller")
                && winStudents.get(0).getFirstName().equals("Zo\u00eb");
        check(winDecoded, "windows-1252 names decoded");
        List<Student> quoted = reader.readRoster(write(root, "q.csv",
                header + "\n#1,#cat,\"Tan, Jr\",\"Ah \"\"Cat\"\"\",c@x\n"));
        boolean quotesRead = quoted.get(0).getLastName().equals("Tan, Jr")
                && quoted.get(0).getFirstName().equals("Ah \"Cat\"");
        check(quotesRead, "quoted names with comma and quotes");
        List<Student> multi = reader.readRoster(write(root, "m.csv",
                header + "\n#1,#dan,\"Two\nLines\",Dan,d@x\n"
                + "#2,#eve,E,Eve,e@x\n"));
        boolean recordsApart = multi.size() == TWO_ROWS
                && multi.get(1).getUsername().equals("eve");
        check(recordsApart,
                "quoted newline inside a name keeps records apart");
        return basic;
    }

    // Checks line endings, column orders, missing and repeated columns,
    // short and long rows, prefixes, blank rows and encodings.
    private static void checkRosterLayouts(Path root, RosterReader reader,
            String header) throws Exception {
        check(readsOk(reader, write(root, "cr.csv",
                header + "\r#1,#fay,F,Fay,f@x\r#2,#gus,G,Gus,g@x\r"),
                TWO_ROWS),
                "old Mac CR line endings");
        check(readsOk(reader, write(root, "order.csv",
                "Email,First Name,Last Name,Username,OrgDefinedId,Extra\n"
                + "h@x,Hal,H,#hal,#7,anything\n"), 1),
                "columns in another order plus an extra column");
        check(!readsOk(reader, write(root, "noemail.csv",
                "OrgDefinedId,Username,Last Name,First Name\n#1,#a,A,A\n"), 1),
                "missing Email column refused");
        check(!readsOk(reader, write(root, "twice.csv", header
                + ",Username\n#1,#a,A,A,a@x,#b\n"), 1),
                "repeated column refused");
        check(!readsOk(reader, write(root, "dupuser.csv", header
                + "\n#1,#ann,A,A,a@x\n#2,#ANN,B,B,b@x\n"), TWO_ROWS),
                "duplicate username ignoring case refused");
        check(!readsOk(reader, write(root, "headonly.csv", header + "\n"), 0),
                "header without rows refused");
        check(!readsOk(reader, write(root, "empty.csv", EMPTY), 0),
                "empty file refused");
        check(!readsOk(reader, write(root, "short.csv", header
                + "\n#1,#a,A\n"), 1), "short row refused");
        check(!readsOk(reader, write(root, "long.csv", header
                + "\n#1,#a,A,A,a@x,extra\n"), 1), "long row refused");
        check(readsOk(reader, write(root, "eol.csv", header
                + ",End-of-Line Indicator\n#1,#a,A,A,a@x\n#2,#b,B,B,b@x,#\n"),
                TWO_ROWS), "row missing only End-of-Line accepted");
        check(!readsOk(reader, write(root, "noid.csv", header
                + "\n#,#a,A,A,a@x\n"), 1), "id of only # refused");
        check(readsOk(reader, write(root, "hash.csv", header
                + "\n1,ann,A,A,a@x\n"), 1), "no # prefix accepted");
        List<Student> doubleHash = reader.readRoster(write(root, "hh.csv",
                header + "\n#1,##ann,A,A,a@x\n"));
        check(doubleHash.get(0).getUsername().equals("#ann"),
                "only one # removed");
        check(readsOk(reader, write(root, "blankrows.csv", header
                + "\n#1,#a,A,A,a@x\n,,,,\n\n  \n"), 1), "blank rows skipped");
        // Known limitation: header names are matched exactly, as the
        // score sheet export matches them; eLearn never adds spaces
        note("a header with a space after a comma is read: "
                + readsOk(reader, write(root, "spacehead.csv",
                "OrgDefinedId, Username,Last Name,First Name,Email\n"
                + "#1,#a,A,A,a@x\n"), 1));
        check(readsOk(reader, write(root, "bomquoted.csv", "\uFEFF\""
                + "OrgDefinedId\",\"Username\",\"Last Name\",\"First Name\","
                + "\"Email\"\n\"#1\",\"#a\",\"A\",\"A\",\"a@x\"\n"), 1),
                "BOM then quoted header");
        Path utf16 = root.resolve("utf16.csv");
        Files.write(utf16, concat(UTF16_BOM,
                (header + "\r\n#1,#a,A,A,a@x\r\n").getBytes(
                StandardCharsets.UTF_16LE)));
        // Known limitation: rosters are read as UTF-8 or windows-1252.
        // Excel's "Unicode text" is also tab separated, so it is not a
        // CSV file anyway; "CSV UTF-8" is the format to choose.
        note("a UTF-16 roster is read: " + readsOk(reader, utf16, 1));
        check(!readsOk(reader, write(root, "unclosed.csv", header
                + "\n#1,#a,\"A,A,a@x\n"), 1), "unclosed quote refused");
    }

    // Checks a missing or null roster, findByUsername and the grade
    // denominator.
    private static void checkRosterLookups(Path root, RosterReader reader,
            String header, List<Student> basic) throws Exception {
        boolean missingFile = false;
        try {
            reader.readRoster(root.resolve("nope.csv"));
        } catch (IngestException expected) {
            missingFile = expected.getMessage().contains("does not exist");
        }
        check(missingFile, "missing roster file named");
        boolean noSheet = false;
        try {
            reader.readRoster(null);
        } catch (IngestException expected) {
            noSheet = true;
        }
        check(noSheet, "null roster path refused");
        boolean foundIgnoringCase = reader.findByUsername(basic, "ANN") != null
                && reader.findByUsername(basic, "zed") == null
                && reader.findByUsername(null, TEXT_A) == null;
        check(foundIgnoringCase, "findByUsername ignoring case");
        Path denom = write(root, "denom.csv", header
                + ",Calculated Final Grade Denominator\n#1,#a,A,A,a@x,20\n"
                + "#2,#b,B,B,b@x,20\n,,,,,\n");
        double expectedDenominator = 20.0;
        check(reader.readDenominator(denom) == expectedDenominator,
                "denominator 20");
        Path mixed = write(root, "denom2.csv", header
                + ",Calculated Final Grade Denominator\n#1,#a,A,A,a@x,20\n"
                + "#2,#b,B,B,b@x,25\n");
        check(reader.readDenominator(mixed) == RosterReader
                .UNKNOWN_DENOMINATOR, "differing denominators unknown");
        check(reader.readDenominator(root.resolve("nope.csv"))
                == RosterReader.UNKNOWN_DENOMINATOR,
                "missing file denominator unknown");
        Path noDenominator = null;
        if (!basic.isEmpty()) {
            noDenominator = write(root, "nodenom.csv", header
                    + "\n#1,#a,A,A,a@x\n");
        }
        check(reader.readDenominator(noDenominator)
                == RosterReader.UNKNOWN_DENOMINATOR, "no denominator column");
        // Real eLearn sample shipped with the project
        Path sample = Path.of("samples/IS442-ScoreSheet.csv");
        if (Files.exists(sample)) {
            check(reader.readRoster(sample).size() > 0,
                    "shipped sample score sheet reads");
        }
    }

    // Checks how a username is read from an upload's file name.
    private static void checkNames(AppConfig config) {
        SubmissionLoader loader = new SubmissionLoader(config);
        check(loader.extractUsername("2023-2024-alice.zip").equals(ALICE),
                "prefix removed");
        check(loader.extractUsername("2023-2024-Alice (1).zip")
                .equals(ALICE), "browser copy number removed");
        check(loader.extractUsername("alice - Copy (2).zip").equals(ALICE),
                "windows copy removed");
        check(loader.extractUsername("alice (1) - Copy.ZIP").equals(ALICE),
                "both copy marks and upper ZIP removed");
        check(loader.extractUsername("alice.zip.zip").equals(ALICE),
                "double .zip removed");
        check(loader.extractUsername("ping.lee.2023.zip")
                .equals("ping.lee.2023"), "dots kept");
        check(loader.extractUsername("1234-5678-").equals(EMPTY),
                "prefix only gives empty");
        check(loader.extractUsername(null).equals(EMPTY), "null name");
        check(loader.extractUsername(" alice .zip").equals(ALICE),
                "spaces around name trimmed");
        check(loader.extractUsername("alice(2).zip").equals(ALICE),
                "copy number without space");
        check(SubmissionLoader.readCopyNumber("alice.zip") == 0,
                "no copy number 0");
        int thirdCopy = 3;
        check(SubmissionLoader.readCopyNumber("alice (3).zip") == thirdCopy,
                "copy number 3");
        check(SubmissionLoader.readCopyNumber("alice - Copy.zip") == 1,
                "windows copy is 1");
        int fourthCopy = 4;
        check(SubmissionLoader.readCopyNumber("alice - Copy (4).zip")
                == fourthCopy,
                "windows copy (4) is 4");
        check(SubmissionLoader.readCopyNumber("alice (99999999999).zip")
                == Integer.MAX_VALUE, "huge copy number newest");
        check(SubmissionLoader.readCopyNumber(null) == 0, "null copy number");
        boolean windowsCopy = SubmissionLoader.isWindowsCopyName("a - copy.zip")
                && !SubmissionLoader.isWindowsCopyName("a-copy.zip")
                && !SubmissionLoader.isWindowsCopyName("copy.zip");
        check(windowsCopy, "isWindowsCopyName");
    }

    // Checks loading submissions from folders, zips and bulk zips,
    // including duplicates, odd files and empty folders.
    private static void checkLoader(Path root, AppConfig config)
            throws Exception {
        SubmissionLoader loader = new SubmissionLoader(config);
        Path benLower = checkFolderLoad(root, loader);
        checkOddInputs(root, loader, benLower);
        checkBulkZips(root, loader);
    }

    // Checks loading a folder of uploads: copies of one student, a name
    // in two cases and the files that are skipped. Returns ben's upload
    // in lower case.
    private static Path checkFolderLoad(Path root, SubmissionLoader loader)
            throws Exception {
        Path folder = root.resolve("uploads");
        Files.createDirectories(folder);
        zip(folder, "2023-2024-amy.zip",
                new String[] {"amy/Q1/Q1a.java", TEXT_1});
        Path amy1 = zip(folder, "2023-2024-amy (1).zip",
                new String[] {"amy/Q1/Q1a.java", TEXT_2});
        Path benLower = zip(folder, "ben.zip",
                new String[] {"ben/Q1/Q1a.java", TEXT_1});
        Path upperFolder = root.resolve("upper");
        Files.createDirectories(upperFolder);
        Path benUpper = zip(upperFolder, "Ben.zip",
                new String[] {"ben/Q1/Q1a.java", TEXT_1});
        Files.writeString(folder.resolve("notes.txt"), "hi");
        Files.writeString(folder.resolve(".hidden.zip"), TEXT_X);
        Files.createDirectories(folder.resolve("RenameToYourUsername/Q1"));
        Files.createDirectories(folder.resolve("cal/Q1"));
        Files.writeString(folder.resolve("cal/Q1/Q1a.java"), TEXT_C);
        List<Submission> loaded = loader.loadSubmissions(
                Arrays.asList(folder), SECTION);
        List<String> names = new ArrayList<>();
        for (Submission submission : loaded) {
            names.add(submission.getUsername());
        }
        int expectedStudents = 3;
        boolean threeStudents = names.size() == expectedStudents
                && names.contains("amy") && names.contains("ben")
                && names.contains("cal");
        check(threeStudents, "three students from a folder, got " + names);
        Submission amy = find(loaded, "amy");
        boolean newestAmy = amy != null && amy.getOriginalPath()
                .getFileName().equals(amy1.getFileName());
        check(newestAmy, "amy (1) graded over amy");
        check(amy != null && hasMessage(amy, "Another file"),
                "the dropped amy file is named");
        Submission ben = find(loader.loadSubmissions(Arrays.asList(benUpper,
                benLower), SECTION), "ben");
        Path benFile = null;
        if (ben != null) {
            benFile = ben.getOriginalPath().getFileName();
        }
        boolean lowerPreferred = ben != null
                && benFile.toString().equals("ben.zip");
        check(lowerPreferred, "lower-case ben.zip preferred over Ben.zip"
                + " (got " + benFile + ")");
        List<Path> skipped = loader.listSkipped(folder);
        boolean notesSkipped = false;
        for (Path path : skipped) {
            if (path.getFileName().toString().equals("notes.txt")) {
                notesSkipped = true;
            }
        }
        check(notesSkipped, "notes.txt listed as skipped");
        return benLower;
    }

    // Checks a file chosen twice, a file that is not a zip, two nameless
    // uploads, an empty folder and a folder of zips one level down.
    private static void checkOddInputs(Path root, SubmissionLoader loader,
            Path benLower) throws Exception {
        // Same path chosen twice
        List<Submission> twice = loader.loadSubmissions(Arrays.asList(
                benLower, benLower), SECTION);
        boolean chosenTwice = twice.size() == 1
                && hasMessage(twice.get(0), "chosen more than once");
        check(chosenTwice, "same file chosen twice");
        // A non-zip file chosen directly
        Path text = Files.writeString(root.resolve("dora.txt"), TEXT_X);
        List<Submission> notZip = loader.loadSubmissions(Arrays.asList(text),
                SECTION);
        boolean textRejected = notZip.size() == 1
                && notZip.get(0).getStatus() == SubmissionStatus.FAILED;
        check(textRejected, "a text file is rejected");
        // Two nameless uploads
        Path nameless1 = zip(root, "2023-2024-.zip",
                new String[] {"x/Q1/Q1a.java", TEXT_1});
        Path otherFolder = root.resolve("other");
        Files.createDirectories(otherFolder);
        Path nameless2 = zip(otherFolder, "0000-0000-.zip",
                new String[] {"x/Q1/Q1a.java", TEXT_1});
        List<Submission> nameless = loader.loadSubmissions(Arrays.asList(
                nameless1, nameless2), SECTION);
        int expectedNotes = 2;
        boolean oneWithTwoNotes = nameless.size() == 1
                && nameless.get(0).getAnomalies().size() == expectedNotes;
        check(oneWithTwoNotes,
                "two nameless uploads give one failed with two notes");
        // An empty folder
        Path emptyFolder = root.resolve("emptyfolder");
        Files.createDirectories(emptyFolder);
        boolean emptyRefused = false;
        try {
            loader.loadSubmissions(Arrays.asList(emptyFolder), SECTION);
        } catch (IngestException expected) {
            emptyRefused = true;
        }
        check(emptyRefused, "empty folder refused");
        // Folder of zips one level down
        Path wrapper = root.resolve("wrapper");
        Files.createDirectories(wrapper.resolve("zips"));
        zip(wrapper.resolve("zips"), "zed.zip",
                new String[] {"zed/Q1/Q1a.java", TEXT_1});
        String hint = EMPTY;
        try {
            loader.loadSubmissions(Arrays.asList(wrapper), SECTION);
        } catch (IngestException expected) {
            hint = expected.getMessage();
        }
        check(hint.contains("\"zips\""), "folder of zips one down is named");
    }

    // Checks which zips are bulk zips of a whole section, and how one is
    // listed and loaded.
    private static void checkBulkZips(Path root, SubmissionLoader loader)
            throws Exception {
        byte[] firstStudent = zipBytes(StandardCharsets.UTF_8, false,
                new String[] {"s1/Q1/Q1a.java", TEXT_1});
        byte[] secondStudent = zipBytes(StandardCharsets.UTF_8, false,
                new String[] {"s2/Q1/Q1a.java", TEXT_1});
        Path bulk = zipRaw(root, "section.zip", StandardCharsets.UTF_8, false,
                new String[] {"G1/2023-2024-s1.zip", "G1/2023-2024-s2.zip",
                "readme.txt"}, new byte[][] {firstStudent, secondStudent,
                "r".getBytes()});
        check(loader.isBatchArchive(bulk), "bulk zip recognised");
        check(loader.listBatchEntries(bulk).equals(Arrays.asList(
                "2023-2024-s1.zip", "2023-2024-s2.zip")),
                "bulk entries listed");
        check(loader.listBatchSkipped(bulk).equals(Arrays.asList(
                "readme.txt")), "bulk skipped listed");
        List<Submission> fromBulk = loader.loadSubmissions(Arrays.asList(
                bulk), SECTION);
        int expectedFromBulk = 2;
        check(fromBulk.size() == expectedFromBulk,
                "bulk zip gives two submissions");
        Path studentPrefix = zipRaw(root, "2023-2024-s3.zip",
                StandardCharsets.UTF_8, false, new String[] {
                INNER_ZIP}, new byte[][] {firstStudent});
        check(!loader.isBatchArchive(studentPrefix),
                "a student-prefixed zip is never a bulk zip");
        Path questionZip = zipRaw(root, "answers.zip", StandardCharsets.UTF_8,
                false, new String[] {QUESTION_ANSWER, "Q1.zip"},
                new byte[][] {TEXT_A.getBytes(), firstStudent});
        check(!loader.isBatchArchive(questionZip),
                "a zip holding a question folder is not a bulk zip");
        check(!loader.isBatchArchive(root.resolve("dora.txt")),
                "text file is not a bulk zip");
        boolean nullRefused = false;
        try {
            loader.loadSubmissions(null, SECTION);
        } catch (IngestException expected) {
            nullRefused = true;
        }
        check(nullRefused, "null inputs refused");
    }

    // Checks making, replacing and cleaning a student's working copy.
    private static void checkWorkingCopy(Path root, AppConfig config)
            throws Exception {
        ZipExtractor extractor = new ZipExtractor(config);
        WorkingCopyManager manager = new WorkingCopyManager(config, extractor);
        Path zipFile = zip(root, "wc.zip", new String[] {WC_ANSWER, TEXT_A,
                "wc/Q1/Q1a.class", TEXT_C, "wc/Q1/Shape.class", TEXT_B});
        Submission submission = new Submission("wc", SECTION, zipFile);
        Path copy = manager.createWorkingCopy(submission);
        boolean madeInWorking = copy.equals(root.resolve("working/wc"))
                && Files.exists(copy.resolve(WC_ANSWER));
        check(madeInWorking, "working copy created under the working folder");
        Path again = manager.createWorkingCopy(submission);
        check(again.equals(copy), "second working copy replaces the first");
        Path starter = root.resolve(STARTER);
        Files.createDirectories(starter.resolve("Q1"));
        Files.writeString(starter.resolve("Q1/Shape.class"), STARTER);
        manager.removeStudentClassFiles(copy,
                new AssessmentFactory(config).create(), starter);
        boolean classesCleaned = !Files.exists(copy.resolve("wc/Q1/Q1a.class"))
                && Files.readString(copy.resolve("wc/Q1/Shape.class"))
                .equals(STARTER);
        check(classesCleaned,
                "student class removed and provided class replaced");
        checkZipOfZip(root, manager);
        checkBadUsernames(manager, zipFile);
        // A damaged retry keeps the previous copy
        Path junk = root.resolve("junk2.zip");
        Files.writeString(junk, "junk");
        boolean junkRefused = false;
        try {
            manager.createWorkingCopy(new Submission("wc", SECTION, junk));
        } catch (IngestException expected) {
            junkRefused = true;
        }
        check(junkRefused && Files.exists(copy.resolve(WC_ANSWER)),
                "a damaged retry keeps the previous working copy");
        checkFolderUpload(root, manager);
        // Outside working folder
        boolean outside = false;
        try {
            manager.removeStudentClassFiles(root, null);
        } catch (UncheckedIOException expected) {
            outside = true;
        }
        check(outside, "removeStudentClassFiles refuses a path outside");
    }

    // Checks that a zip holding only a zip is unpacked once more.
    private static void checkZipOfZip(Path root, WorkingCopyManager manager)
            throws Exception {
        byte[] innerBytes = zipBytes(StandardCharsets.UTF_8, false,
                new String[] {"zz/Q1/Q1a.java", "inner"});
        Path outer = zipRaw(root, "zz.zip", StandardCharsets.UTF_8, false,
                new String[] {"zz.zip"}, new byte[][] {innerBytes});
        Path zzCopy = manager.createWorkingCopy(new Submission("zz", SECTION,
                outer));
        check(Files.exists(zzCopy.resolve("zz/Q1/Q1a.java")),
                "zip holding only a zip is unpacked once more");
    }

    // Checks that usernames that are not a plain folder name are refused.
    private static void checkBadUsernames(WorkingCopyManager manager,
            Path zipFile) {
        for (String bad : new String[] {"..", ".", "a/b", "a\\b", "c:d", " "}) {
            boolean refused = false;
            try {
                manager.createWorkingCopy(new Submission(bad, SECTION,
                        zipFile));
            } catch (IngestException expected) {
                refused = true;
            }
            check(refused, "username '" + bad + "' refused");
        }
    }

    // Checks that a folder upload drops the names a zip would drop.
    private static void checkFolderUpload(Path root,
            WorkingCopyManager manager) throws Exception {
        Path folderUpload = root.resolve("fu");
        Files.createDirectories(folderUpload.resolve("Q1"));
        Files.writeString(folderUpload.resolve(QUESTION_ANSWER), "f");
        Files.writeString(folderUpload.resolve(DS_STORE), TEXT_D);
        Files.writeString(folderUpload.resolve("Q1/._Q1a.java"), "fork");
        Path fuCopy = manager.createWorkingCopy(new Submission("fu", SECTION,
                folderUpload));
        check(!Files.exists(fuCopy.resolve(DS_STORE)),
                "folder upload drops .DS_Store");
        check(!Files.exists(fuCopy.resolve("Q1/._Q1a.java")),
                "folder upload drops ._ resource forks like a zip does");
    }

    // --- helpers -----------------------------------------------------

    // Finds the submission of a username in a list, or null.
    private static Submission find(List<Submission> list, String name) {
        for (Submission submission : list) {
            if (submission.getUsername().equals(name)) {
                return submission;
            }
        }
        return null;
    }

    // Says whether one of a submission's notes holds the given text.
    private static boolean hasMessage(Submission submission, String part) {
        for (Anomaly anomaly : submission.getAnomalies()) {
            if (anomaly.getMessage().contains(part)) {
                return true;
            }
        }
        return false;
    }

    // Says whether a roster reads with exactly the expected number of
    // students, and prints the count when it differs.
    private static boolean readsOk(RosterReader reader, Path file,
            int expected) {
        try {
            int count = reader.readRoster(file).size();
            if (count != expected) {
                System.out.println("   " + file.getFileName() + " read "
                        + count + " not " + expected);
            }
            return count == expected;
        } catch (IngestException exception) {
            return false;
        }
    }

    // Writes a text file in a folder and returns it.
    private static Path write(Path root, String name, String text)
            throws Exception {
        Path file = root.resolve(name);
        Files.writeString(file, text);
        return file;
    }

    // Joins two byte arrays into one.
    private static byte[] concat(byte[] first, byte[] second) {
        byte[] all = Arrays.copyOf(first, first.length + second.length);
        System.arraycopy(second, 0, all, first.length, second.length);
        return all;
    }

    // Says whether unpacking a zip is refused for any reason.
    private static boolean extractFails(ZipExtractor extractor, Path zip,
            Path target) {
        try {
            extractor.extract(zip, target);
            return false;
        } catch (IngestException expected) {
            return true;
        }
    }

    // Says whether unpacking a zip is refused as a damaged or invalid zip.
    private static boolean extractThrowsInvalid(ZipExtractor extractor,
            Path zip, Path target) {
        try {
            extractor.extract(zip, target);
            return false;
        } catch (InvalidArchiveException expected) {
            return true;
        } catch (IngestException other) {
            System.out.println("   " + zip.getFileName()
                    + " gave IngestException, not InvalidArchive: "
                    + other.getMessage());
            return false;
        }
    }

    // Writes a zip of name/text pairs with UTF-8 names.
    private static Path zip(Path folder, String name, String[] pairs)
            throws Exception {
        int entries = pairs.length / PAIR_SIZE;
        String[] names = new String[entries];
        byte[][] data = new byte[entries][];
        for (int i = 0; i < names.length; i++) {
            int nameIndex = PAIR_SIZE * i;
            int textIndex = nameIndex + 1;
            names[i] = pairs[nameIndex];
            data[i] = pairs[textIndex].getBytes(StandardCharsets.UTF_8);
        }
        try {
            return zipRaw(folder, name, StandardCharsets.UTF_8, false, names,
                    data);
        } catch (ZipException duplicate) {
            return folder.resolve(name);
        }
    }

    // Writes a zip of names and contents with the given name charset.
    private static Path zipRaw(Path folder, String name, Charset charset,
            boolean stored, String[] names, byte[][] data) throws Exception {
        Path file = folder.resolve(name);
        Files.write(file, zipBytes(charset, stored, names, data));
        return file;
    }

    // Makes the bytes of a zip of name/text pairs.
    private static byte[] zipBytes(Charset charset, boolean stored,
            String[] pairs) throws Exception {
        int entries = pairs.length / PAIR_SIZE;
        String[] names = new String[entries];
        byte[][] data = new byte[entries][];
        for (int i = 0; i < names.length; i++) {
            int nameIndex = PAIR_SIZE * i;
            int textIndex = nameIndex + 1;
            names[i] = pairs[nameIndex];
            data[i] = pairs[textIndex].getBytes(StandardCharsets.UTF_8);
        }
        return zipBytes(charset, stored, names, data);
    }

    // Makes the bytes of a zip of names and contents; a null content
    // makes a folder entry.
    private static byte[] zipBytes(Charset charset, boolean stored,
            String[] names, byte[][] data) throws Exception {
        ByteArrayOutputStream bytes = new ByteArrayOutputStream();
        ZipOutputStream out = new ZipOutputStream(bytes, charset);
        for (int i = 0; i < names.length; i++) {
            ZipEntry entry = new ZipEntry(names[i]);
            out.putNextEntry(entry);
            if (data[i] != null) {
                out.write(data[i]);
            }
            out.closeEntry();
        }
        out.close();
        return bytes.toByteArray();
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
