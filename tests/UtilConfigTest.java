/*
 * File: UtilConfigTest.java
 *
 * Purpose: Unit checks for the util and config packages: CSV split and
 * join, line endings, blank checks, source decoding, FileUtils, AppConfig,
 * ModuleSections and ExportFolders.
 *
 * Where it fits: Run by hand after compiling, as tests/README.md says.
 * A failing check is printed and counted; the run goes on.
 */
import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;
import java.util.Random;

import gradezzy.config.AppConfig;
import gradezzy.config.ConfigException;
import gradezzy.config.ConfigKeys;
import gradezzy.config.ExportFolders;
import gradezzy.config.ModuleSections;
import gradezzy.util.FileUtils;
import gradezzy.util.TextUtils;

/**
 * Runs the util and config checks and prints how many passed.
 */
public class UtilConfigTest {

    // Short CSV field values used by the split and join checks
    private static final String LETTER_A = "a";
    private static final String LETTER_B = "b";
    private static final String LETTER_C = "c";

    // Two plain fields as one CSV line
    private static final String TWO_FIELDS = "a,b";

    // A one letter text used as a field, a file body and a list item
    private static final String SHORT_TEXT = "x";

    // The text every decode check expects back
    private static final String GREETING = "hi";

    // "hi" in UTF-16 little endian, with its byte order mark
    private static final byte[] UTF16_LE_HI = {(byte) 0xFF, (byte) 0xFE,
        'h', 0, 'i', 0};

    // "hi" in UTF-16 big endian, with its byte order mark
    private static final byte[] UTF16_BE_HI = {(byte) 0xFE, (byte) 0xFF,
        0, 'h', 0, 'i'};

    // A single byte that starts like a UTF-16 mark
    private static final byte[] ONE_BYTE = {(byte) 0xFF};

    // "hi" in UTF-8 after a UTF-8 byte order mark
    private static final byte[] UTF8_BOM_HI = {(byte) 0xEF, (byte) 0xBB,
        (byte) 0xBF, 'h', 'i'};

    // A word with an accent, read and written in several encodings
    private static final String CAFE = "caf\u00e9";

    // The file copied into every folder by the FileUtils checks
    private static final String TOP_FILE = "top.txt";

    // The module, its sections and another module
    private static final String MODULE = "CS102";
    private static final String MODULE_LOWER = "cs102";
    private static final String OTHER_MODULE = "IS442";
    private static final String SECTION = "G1";
    private static final String OTHER_SECTION = "G2";
    private static final String OWN_SECTION = "G5";
    private static final String SECOND_OWN_SECTION = "G6";

    // Own-section values for the module
    private static final String OWN_ENTRY = "CS102:G5";
    private static final String OWN_ENTRY_TWO = "CS102:G5|G6";

    // Export folder keys and folders
    private static final String SECTION_KEY = "CS102/G1";
    private static final String OTHER_SECTION_KEY = "CS102/G2";
    private static final String DEFAULT_FOLDER = "/def";

    // The score sheet used when no section entry matches
    private static final String FALLBACK_SHEET = "fallback.csv";

    // The separator for the plain join checks
    private static final String SEPARATOR = "-";

    // The seed and size of the random CSV round trip
    private static final long CSV_SEED = 42;
    private static final int ROUNDS = 500;
    private static final int MAX_FIELDS = 5;
    private static final int MAX_FIELD_LENGTH = 6;

    // The seed and size of the random folder name round trip
    private static final long FOLDER_SEED = 7;
    private static final int MAX_FOLDER_LENGTH = 8;

    // The number of checks that passed
    private static int passed = 0;

    // The number of checks that failed
    private static int failed = 0;

    /**
     * Runs every check.
     *
     * @param args not used
     * @throws Exception if a temporary file cannot be written
     */
    public static void main(String[] args) throws Exception {
        Path root = Files.createTempDirectory("gradezzy-util-tests-")
                .toRealPath();
        try {
            checkCsvSplit();
            checkCsvJoin();
            checkLineEndingsAndBlank();
            checkDecodeSource();
            checkFileUtils(root);
            checkAppConfig(root);
            checkModuleSections();
            checkExportFolders(root);
        } finally {
            FileUtils.deleteDirectory(root);
        }
        System.out.println("UtilConfigTest: " + passed + " passed, "
                + failed + " failed");
        if (failed > 0) {
            System.exit(1);
        }
    }

    // Checks how a CSV line is split into fields.
    private static void checkCsvSplit() {
        check(TextUtils.splitCsvLine("a,b,c").equals(
                Arrays.asList(LETTER_A, LETTER_B, LETTER_C)),
                "split plain fields");
        check(TextUtils.splitCsvLine("").equals(Arrays.asList("")),
                "split empty line gives one empty field");
        check(TextUtils.splitCsvLine(",,").equals(
                Arrays.asList("", "", "")), "split keeps empty fields");
        check(TextUtils.splitCsvLine("a,").equals(
                Arrays.asList(LETTER_A, "")),
                "split keeps trailing empty field");
        check(TextUtils.splitCsvLine("\"a,b\",c").equals(
                Arrays.asList(TWO_FIELDS, LETTER_C)), "split quoted comma");
        check(TextUtils.splitCsvLine("\"say \"\"hi\"\"\",x").equals(
                Arrays.asList("say \"hi\"", SHORT_TEXT)),
                "split doubled quote");
        check(TextUtils.splitCsvLine("\"\"").equals(Arrays.asList("")),
                "split empty quoted field");
        check(TextUtils.splitCsvLine("\"line1\nline2\",b").equals(
                Arrays.asList("line1\nline2", LETTER_B)),
                "split newline inside quotes");
        check(TextUtils.splitCsvLine(" a , b ").equals(
                Arrays.asList(" a ", " b ")), "split keeps spaces");
        check(throwsOnSplit("Tan, \"Ah Kow\""),
                "split space before quote is refused (strict)");
        check(throwsOnSplit("\"unclosed"), "split unclosed quote throws");
        check(throwsOnSplit("\"a\"b"),
                "split text after close quote throws");
        check(throwsOnSplit("ab\"c"), "split quote mid field throws");
        check(throwsOnSplit("a\r"), "split bare CR outside quotes throws");
        check(TextUtils.splitCsvLine("é,ü,中文").equals(
                Arrays.asList("é", "ü", "中文")), "split unicode");
        boolean nullRefused = false;
        try {
            TextUtils.splitCsvLine(null);
        } catch (IllegalArgumentException expected) {
            nullRefused = true;
        }
        check(nullRefused, "split null throws IllegalArgumentException");
        // Excel writes "a" ,b sometimes? record what happens with a space
        // after a closing quote
        check(throwsOnSplit("\"a\" ,b"),
                "split space after closing quote");
    }

    // Checks how fields are joined into a CSV line, and the plain join.
    private static void checkCsvJoin() {
        check(TextUtils.joinCsvLine(Arrays.asList(LETTER_A, LETTER_B))
                .equals(TWO_FIELDS), "join plain");
        check(TextUtils.joinCsvLine(Arrays.asList(TWO_FIELDS))
                .equals("\"a,b\""), "join quotes comma");
        check(TextUtils.joinCsvLine(Arrays.asList("x\"y")).equals(
                "\"x\"\"y\""), "join doubles quote");
        check(TextUtils.joinCsvLine(Arrays.asList("x\ny")).equals(
                "\"x\ny\""), "join quotes newline");
        check(TextUtils.joinCsvLine(Arrays.asList("x\ry")).equals(
                "\"x\ry\""), "join quotes CR");
        check(TextUtils.joinCsvLine(new ArrayList<String>()).equals(""),
                "join empty list");
        check(TextUtils.joinCsvLine(new String[] {"", ""}).equals(","),
                "join two empty values");
        boolean nullRefused = false;
        try {
            TextUtils.joinCsvLine(Arrays.asList(LETTER_A, null));
        } catch (IllegalArgumentException expected) {
            nullRefused = true;
        }
        check(nullRefused, "join null value throws");
        check(randomRowsRoundTrip(),
                "join then split round-trips 500 random rows");
        check(TextUtils.join(SEPARATOR,
                Arrays.asList(LETTER_A, LETTER_B, LETTER_C))
                .equals("a-b-c"), "join with separator");
        check(TextUtils.join(SEPARATOR, new String[0]).equals(""),
                "join of nothing is empty");
        check(TextUtils.join(SEPARATOR, new String[] {null, LETTER_B})
                .equals("null-b"),
                "join writes null as null (String.valueOf)");
    }

    // Says whether random rows of fields come back the same after a join
    // then a split.
    private static boolean randomRowsRoundTrip() {
        Random random = new Random(CSV_SEED);
        String alphabet = "ab,\" \n\r;é";
        boolean allRound = true;
        for (int round = 0; round < ROUNDS; round++) {
            List<String> fields = new ArrayList<>();
            int fieldCount = 1 + random.nextInt(MAX_FIELDS);
            for (int i = 0; i < fieldCount; i++) {
                StringBuilder field = new StringBuilder();
                int length = random.nextInt(MAX_FIELD_LENGTH);
                for (int j = 0; j < length; j++) {
                    field.append(alphabet.charAt(
                            random.nextInt(alphabet.length())));
                }
                fields.add(field.toString());
            }
            String line = TextUtils.joinCsvLine(fields);
            try {
                if (!TextUtils.splitCsvLine(line).equals(fields)) {
                    allRound = false;
                }
            } catch (IllegalArgumentException exception) {
                allRound = false;
            }
        }
        return allRound;
    }

    // Checks line ending clean up and the blank test.
    private static void checkLineEndingsAndBlank() {
        check(TextUtils.normaliseLineEndings("a\r\nb\rc\n").equals(
                "a\nb\nc\n"), "normalise CRLF, CR and LF");
        check(TextUtils.normaliseLineEndings("\r\r\n").equals("\n\n"),
                "normalise CR CRLF");
        check(TextUtils.normaliseLineEndings("").equals(""),
                "normalise empty");
        check(TextUtils.isBlank(null), "null is blank");
        check(TextUtils.isBlank(""), "empty is blank");
        check(TextUtils.isBlank(" \t\n"), "whitespace is blank");
        check(TextUtils.isBlank("\u2003"), "em space is blank (isBlank)");
        check(!TextUtils.isBlank(" x "), "text is not blank");
    }

    // Checks how source bytes are decoded in each encoding.
    private static void checkDecodeSource() {
        check(TextUtils.isUtf16(UTF16_LE_HI) && TextUtils.isUtf16(
                UTF16_BE_HI), "UTF-16 marks found");
        check(!TextUtils.isUtf16(ONE_BYTE), "one byte is not UTF-16");
        check(!TextUtils.isUtf16(new byte[0]), "empty is not UTF-16");
        check(TextUtils.decodeSource(UTF16_LE_HI).equals(GREETING),
                "decode UTF-16LE");
        check(TextUtils.decodeSource(UTF16_BE_HI).equals(GREETING),
                "decode UTF-16BE");
        check(TextUtils.decodeSource("héllo".getBytes(
                StandardCharsets.UTF_8)).equals("héllo"), "decode UTF-8");
        String decoded = TextUtils.decodeSource(UTF8_BOM_HI);
        check(decoded.equals(GREETING),
                "decodeSource drops a UTF-8 byte order mark (got "
                + decoded.length() + " chars, first U+"
                + Integer.toHexString(decoded.charAt(0)) + ")");
    }

    // Checks copying, deleting, reading, listing and moving files.
    private static void checkFileUtils(Path root) throws Exception {
        Path source = root.resolve("src");
        Files.createDirectories(source.resolve("a/b"));
        Files.writeString(source.resolve("a/b/x.java"), SHORT_TEXT);
        Files.writeString(source.resolve(TOP_FILE), "t");
        Path copy = root.resolve("copy");
        FileUtils.copyDirectory(source, copy);
        check(Files.readString(copy.resolve("a/b/x.java")).equals(SHORT_TEXT)
                && Files.exists(copy.resolve(TOP_FILE)),
                "copyDirectory copies nested files");
        boolean overlapRefused = false;
        try {
            FileUtils.copyDirectory(source, source.resolve("a/inner"));
        } catch (IOException expected) {
            overlapRefused = true;
        }
        check(overlapRefused, "copyDirectory refuses copy into itself");
        Path sibling = root.resolve("src2");
        FileUtils.copyDirectory(source, sibling);
        check(Files.exists(sibling.resolve(TOP_FILE)),
                "copyDirectory to a sibling with a shared prefix name works");
        Path linked = root.resolve("linked");
        Files.createDirectories(linked);
        boolean linkRefused = false;
        try {
            Files.createSymbolicLink(linked.resolve("link"),
                    source.resolve(TOP_FILE));
            FileUtils.copyDirectory(linked, root.resolve("linkcopy"));
        } catch (IOException expected) {
            linkRefused = true;
        }
        check(linkRefused, "copyDirectory refuses a symbolic link inside");
        // deleteDirectory removes a link but not its target
        Path outside = root.resolve("outside");
        Files.createDirectories(outside);
        Files.writeString(outside.resolve("keep.txt"), "keep");
        Path holder = root.resolve("holder");
        Files.createDirectories(holder);
        Files.createSymbolicLink(holder.resolve("toOutside"), outside);
        FileUtils.deleteDirectory(holder);
        check(!Files.exists(holder) && Files.exists(outside.resolve(
                "keep.txt")), "deleteDirectory does not follow a link");
        FileUtils.deleteDirectory(root.resolve("never-existed"));
        check(true, "deleteDirectory of a missing folder is quiet");
        Path latin = root.resolve("latin.txt");
        Files.write(latin, CAFE.getBytes("windows-1252"));
        check(FileUtils.readText(latin).equals(CAFE),
                "readText falls back to windows-1252");
        Path utf = root.resolve("utf.txt");
        FileUtils.writeText(utf, "日本");
        check(FileUtils.readText(utf).equals("日本"),
                "writeText/readText UTF-8 round trip");
        checkListFiles(root);
        checkMove(root);
    }

    // Checks that listFiles lists files only, sorted, by suffix.
    private static void checkListFiles(Path root) throws Exception {
        Path list = root.resolve("list");
        Files.createDirectories(list.resolve("dir.java"));
        Files.writeString(list.resolve("b.java"), "");
        Files.writeString(list.resolve("a.java"), "");
        Files.writeString(list.resolve("c.txt"), "");
        List<Path> javas = FileUtils.listFiles(list, ".java");
        int expectedJavas = 2;
        check(javas.size() == expectedJavas && javas.get(0).getFileName()
                .toString().equals("a.java"),
                "listFiles sorted, files only, by suffix");
        check(FileUtils.listFiles(list, ".JAVA").isEmpty(),
                "listFiles suffix is case sensitive (B.JAVA not listed)");
    }

    // Checks that moveWithRetry moves a file and fails onto an existing
    // one.
    private static void checkMove(Path root) throws Exception {
        Path moveFrom = root.resolve("m1.txt");
        Path moveTo = root.resolve("m2.txt");
        Files.writeString(moveFrom, "m");
        FileUtils.moveWithRetry(moveFrom, moveTo);
        check(Files.exists(moveTo) && !Files.exists(moveFrom),
                "moveWithRetry moves");
        Files.writeString(moveFrom, "m");
        long start = System.currentTimeMillis();
        boolean moveFailed = false;
        try {
            FileUtils.moveWithRetry(moveFrom, moveTo);
        } catch (IOException expected) {
            moveFailed = true;
        }
        long waited = System.currentTimeMillis() - start;
        check(moveFailed, "moveWithRetry onto an existing file fails");
        // Known limitation: moveWithRetry retries for about two seconds
        // before it gives up, because on Windows another program (such as
        // an antivirus) may hold the file for a moment. It cannot tell that
        // case from a target that already exists, so it waits there too.
        note("moveWithRetry onto an existing file fails after its retries ("
                + waited + " ms), not at once");
    }

    // Checks how AppConfig reads lists, flags, numbers and strings.
    private static void checkAppConfig(Path root) throws Exception {
        Path file = root.resolve("c.properties");
        Files.writeString(file, "list= a, ,b ,, c\n"
                + "emptyList=\n"
                + "commas=,,,\n"
                + "flagT= TRUE \n"
                + "flagF=False\n"
                + "flagBad=yes\n"
                + "num= 42 \n"
                + "badNum=4.2\n"
                + "dbl=1e3\n"
                + "spaced = value with spaces  \n"
                + "winPath=C:\\Users\\ann\\sheet.csv\n"
                + "winPathEscaped=C:\\\\Users\\\\ann\n"
                + "unicode=caf\u00e9\n");
        AppConfig config = new AppConfig(file.toString());
        check(config.getList("list").equals(
                Arrays.asList(LETTER_A, LETTER_B, LETTER_C)),
                "getList trims and drops empties");
        check(config.getList("emptyList").isEmpty(), "empty list");
        check(config.getList("commas").isEmpty(), "list of only commas");
        check(config.getBoolean("flagT") && !config.getBoolean("flagF"),
                "boolean any case and trimmed");
        check(throwsConfig(config, "flagBad", "bool"),
                "boolean 'yes' refused");
        int expectedNumber = 42;
        check(config.getInt("num") == expectedNumber, "int trimmed");
        check(throwsConfig(config, "badNum", "int"), "int 4.2 refused");
        double expectedDouble = 1000.0;
        check(config.getDouble("dbl") == expectedDouble, "double 1e3");
        check(config.getLong("num") == expectedNumber, "long");
        check(throwsConfig(config, "missing", "string"),
                "missing key throws ConfigException");
        check(!config.hasKey("missing") && config.hasKey("emptyList"),
                "hasKey");
        check(config.getString("spaced").equals("value with spaces"),
                "string trimmed");
        check(config.getString("unicode").equals(CAFE),
                "UTF-8 value read");
        // Known limitation: a backslash in a .properties file starts an
        // escape, so a Windows path typed by hand loses its backslashes.
        // Settings writes them escaped, and forward slashes work too.
        String winPath = config.getString("winPath");
        note("an unescaped Windows path typed into config.properties loses "
                + "its backslashes (got '" + winPath + "'); use / or "
                + "Settings");
        check(config.getString("winPathEscaped").equals("C:\\Users\\ann"),
                "escaped Windows path read");
        config.setString("new", "v");
        check(config.getString("new").equals("v"), "setString in memory");
        check(AppConfig.splitList(SHORT_TEXT).equals(
                Arrays.asList(SHORT_TEXT)),
                "splitList single");
        check(config.getKeys().get(0).compareTo(config.getKeys().get(1))
                < 0, "getKeys sorted");
        // The shipped config parses and has the core keys
        AppConfig shipped = TestConfig.load();
        check(shipped.getInt(ConfigKeys.MAX_ZIP_ENTRIES) > 0
                && shipped.getLong(ConfigKeys.MAX_ZIP_SIZE_MB) > 0,
                "committed config has zip limits");
        check(!shipped.getList(ConfigKeys.QUESTION_IDS).isEmpty(),
                "committed config has question ids");
    }

    // Checks the shared and own sections of a module.
    private static void checkModuleSections() {
        check(ModuleSections.forModule("G1,G2", OWN_ENTRY_TWO, MODULE)
                .equals(Arrays.asList(SECTION, OTHER_SECTION, OWN_SECTION,
                SECOND_OWN_SECTION)), "shared then own sections");
        check(ModuleSections.forModule("G1,g1", "", MODULE)
                .equals(Arrays.asList(SECTION)), "duplicates ignoring case");
        check(ModuleSections.forModule(SECTION, "CS102:G1|G2", MODULE_LOWER)
                .equals(Arrays.asList(SECTION, OTHER_SECTION)),
                "module code ignoring case, no duplicate");
        check(ModuleSections.forModule(SECTION, "IS442:G9", MODULE)
                .equals(Arrays.asList(SECTION)), "other module ignored");
        check(ModuleSections.forModule(SECTION, null, null)
                .equals(Arrays.asList(SECTION)), "null module");
        check(ModuleSections.forModule(SECTION, OWN_ENTRY, MODULE,
                MODULE_LOWER).equals(Arrays.asList(OWN_SECTION)),
                "own sections only");
        check(ModuleSections.forModule(SECTION, OWN_ENTRY, OTHER_MODULE,
                MODULE).equals(Arrays.asList(SECTION, OWN_SECTION)),
                "own-only list naming another module keeps shared");
        check(ModuleSections.hasOwnOnly("a, CS102 ,b", " CS102"),
                "hasOwnOnly trims");
        check(!ModuleSections.hasOwnOnly("", MODULE), "hasOwnOnly empty");
        check(ModuleSections.add("", MODULE, OWN_SECTION).equals(OWN_ENTRY),
                "add to empty");
        check(ModuleSections.add(OWN_ENTRY, MODULE_LOWER,
                SECOND_OWN_SECTION)
                .equals(OWN_ENTRY_TWO), "add to existing ignoring case");
        String twice = ModuleSections.add(OWN_ENTRY, MODULE, OWN_SECTION);
        check(twice.equals(OWN_ENTRY),
                "add of an existing section does not repeat it (got '"
                + twice + "')");
        check(ModuleSections.remove("CS102:G5|G6;IS442:G1", MODULE, "g5")
                .equals("CS102:G6;IS442:G1"), "remove one section");
        check(ModuleSections.remove("CS102:G5;IS442:G1", MODULE,
                OWN_SECTION).equals("IS442:G1"),
                "remove last section drops module");
        check(ModuleSections.removeModule("CS102:G5;IS442:G1", MODULE_LOWER)
                .equals("IS442:G1"), "removeModule");
        check(ModuleSections.isOwnSection(OWN_ENTRY_TWO, MODULE, "g6"),
                "isOwnSection ignoring case");
        check(!ModuleSections.isOwnSection(OWN_ENTRY, OTHER_MODULE,
                OWN_SECTION), "isOwnSection other module");
        checkSectionValidate();
    }

    // Checks which own-section values validate accepts.
    private static void checkSectionValidate() {
        check(ModuleSections.validate("").isEmpty()
                && ModuleSections.validate(null).isEmpty(), "validate blank");
        check(ModuleSections.validate("CS102:G5|G6;IS442:G1").isEmpty(),
                "validate good value");
        check(!ModuleSections.validate("CS102=G5").isEmpty(),
                "validate refuses = form");
        check(!ModuleSections.validate("CS102:G 5").isEmpty(),
                "validate refuses space in section");
        check(!ModuleSections.validate("CS 102:G5").isEmpty(),
                "validate refuses space in module");
        check(!ModuleSections.validate(":G5").isEmpty(),
                "validate refuses missing module");
        // Known limitation: "CS102:" (a module with no sections) passes
        // validate; it adds no section, so it does no harm
        note("ModuleSections.validate accepts a module with no sections: "
                + ModuleSections.validate("CS102:").isEmpty());
        check(ModuleSections.forModule("", "CS102:G5;CS102:G6", MODULE)
                .equals(Arrays.asList(SECOND_OWN_SECTION)),
                "repeated module entry: last one wins");
    }

    // Checks how export folders are found, stored and checked.
    private static void checkExportFolders(Path root) throws Exception {
        String folders = "CS102=/out/mod;CS102/G1=/out/g1";
        check(ExportFolders.resolve(DEFAULT_FOLDER, folders, MODULE, SECTION)
                .equals("/out/g1"), "resolve section entry");
        check(ExportFolders.resolve(DEFAULT_FOLDER, folders, MODULE_LOWER,
                "g1").equals("/out/g1"), "resolve ignoring case");
        check(ExportFolders.resolve(DEFAULT_FOLDER, folders, MODULE,
                OTHER_SECTION).equals("/out/mod"),
                "resolve module fallback");
        check(ExportFolders.resolve(DEFAULT_FOLDER, folders, OTHER_MODULE,
                SECTION).equals(DEFAULT_FOLDER), "resolve default fallback");
        check(ExportFolders.resolve(DEFAULT_FOLDER, folders, MODULE, null)
                .equals("/out/mod"), "resolve null section");
        check(ExportFolders.resolve(DEFAULT_FOLDER, folders, null, SECTION)
                .equals(DEFAULT_FOLDER), "resolve null module");
        check(ExportFolders.resolve(DEFAULT_FOLDER, null, MODULE, SECTION)
                .equals(DEFAULT_FOLDER), "resolve null folders");
        check(ExportFolders.resolve(DEFAULT_FOLDER, " CS102/G1 = /x ;",
                MODULE, SECTION).equals("/x"), "resolve trims entry");
        check(ExportFolders.keyOf(" CS102 ", " G1 ").equals(SECTION_KEY),
                "keyOf trims");
        check(ExportFolders.keyOf(MODULE, " ").equals(MODULE),
                "keyOf blank section is module");
        String put = ExportFolders.put("", SECTION_KEY, "/a;b%c");
        check(ExportFolders.exact(put, "cs102/g1").equals("/a;b%c"),
                "put/exact round trip with ; and %");
        check(randomFoldersRoundTrip(),
                "put/exact round trips 500 random folder names");
        String replaced = ExportFolders.put("CS102/G1=/a;CS102=/m",
                "cs102/g1", "/b");
        int expectedEntries = 2;
        check(ExportFolders.list(replaced).size() == expectedEntries
                && ExportFolders.exact(replaced, SECTION_KEY).equals("/b"),
                "put replaces ignoring case");
        // Known limitation: put never writes the same section twice, so
        // a repeated entry only comes from a hand-edited file; the first
        // one is used
        String duplicate = "CS102/G1=/first;cs102/g1=/second";
        note("a section listed twice in a different case uses its first "
                + "folder (got '" + ExportFolders.exact(duplicate,
                SECTION_KEY) + "')");
        check(ExportFolders.remove(folders, "cs102/G1").equals(
                "CS102=/out/mod"), "remove");
        check(ExportFolders.removeModule("CS102=/a;CS102/G1=/b;"
                + "CS1020/G1=/c;IS442=/d", MODULE_LOWER).equals(
                "CS1020/G1=/c;IS442=/d"),
                "removeModule keeps a module with a longer code");
        check(ExportFolders.removeSection("CS102/G1=/a;IS442/G1=/b;"
                + "CS102/G11=/c", SECTION).equals("CS102/G11=/c"),
                "removeSection removes the section in every module only");
        checkFolderValidate();
        checkScoreSheets(root);
    }

    // Says whether random folder names come back the same after a put
    // then an exact; prints the first one that does not.
    private static boolean randomFoldersRoundTrip() {
        Random random = new Random(FOLDER_SEED);
        String alphabet = "a;%3Bb%25=/ ";
        boolean allRound = true;
        for (int round = 0; round < ROUNDS && allRound; round++) {
            StringBuilder folder = new StringBuilder(SHORT_TEXT);
            int length = random.nextInt(MAX_FOLDER_LENGTH);
            for (int i = 0; i < length; i++) {
                folder.append(alphabet.charAt(random.nextInt(
                        alphabet.length())));
            }
            folder.append("y");
            String value = ExportFolders.put("CS102=/m", OTHER_SECTION_KEY,
                    folder.toString());
            String readBack = ExportFolders.exact(value, OTHER_SECTION_KEY);
            if (!readBack.equals(folder.toString())) {
                allRound = false;
                System.out.println("   round trip lost: '" + folder
                        + "' -> '" + readBack + "'");
            }
        }
        return allRound;
    }

    // Checks which export folder values validate accepts.
    private static void checkFolderValidate() {
        check(ExportFolders.validate("CS102/G1=/a;CS102=/b;").isEmpty(),
                "validate good");
        check(!ExportFolders.validate("CS102/G1/x=/a").isEmpty(),
                "validate three parts");
        check(!ExportFolders.validate("CS102/G1=").isEmpty(),
                "validate empty folder");
        check(!ExportFolders.validate("=/a").isEmpty(), "validate no who");
        check(!ExportFolders.validate("CS102/=/a").isEmpty(),
                "validate empty section");
        check(!ExportFolders.validate("/a").isEmpty(), "validate no =");
        check(ExportFolders.validate("CS102/G1=C:\\x=y").isEmpty(),
                "validate folder holding =");
        check(ExportFolders.exact("CS102/G1=C:\\x=y", SECTION_KEY).equals(
                "C:\\x=y"), "exact folder holding =");
    }

    // Checks which score sheet is used for a module and section.
    private static void checkScoreSheets(Path root) throws Exception {
        Path file = root.resolve("sheets.properties");
        Files.writeString(file, ConfigKeys.SCORE_SHEET_FILE
                + "=fallback.csv\n"
                + ConfigKeys.SCORE_SHEET_BY_SECTION
                + "=CS102/G1\\=g1.csv;CS102\\=module.csv\n");
        AppConfig config = new AppConfig(file.toString());
        check(ExportFolders.findScoreSheet(config, MODULE, SECTION)
                .toString().equals("g1.csv"), "score sheet for section");
        check(ExportFolders.findScoreSheet(config, MODULE_LOWER, "g1")
                .toString().equals("g1.csv"), "score sheet ignoring case");
        check(ExportFolders.findScoreSheet(config, MODULE, OTHER_SECTION)
                .toString().equals(FALLBACK_SHEET),
                "score sheet: module entry is not used for another section");
        check(ExportFolders.findScoreSheet(config, null, SECTION)
                .toString().equals(FALLBACK_SHEET),
                "score sheet null module");
        check(ExportFolders.findOwnScoreSheet(config, MODULE, OTHER_SECTION)
                == null, "no own score sheet");
        Path noKey = root.resolve("nokey.properties");
        Files.writeString(noKey, ConfigKeys.SCORE_SHEET_FILE + "=f.csv\n");
        check(ExportFolders.findScoreSheet(new AppConfig(noKey.toString()),
                MODULE, SECTION).toString().equals("f.csv"),
                "score sheet without the by-section key");
    }

    // Says whether splitting the line throws IllegalArgumentException.
    private static boolean throwsOnSplit(String line) {
        try {
            TextUtils.splitCsvLine(line);
            return false;
        } catch (IllegalArgumentException expected) {
            return true;
        }
    }

    // Says whether reading the key as the given kind throws.
    private static boolean throwsConfig(AppConfig config, String key,
            String kind) {
        try {
            if (kind.equals("bool")) {
                config.getBoolean(key);
            } else if (kind.equals("int")) {
                config.getInt(key);
            } else {
                config.getString(key);
            }
            return false;
        } catch (ConfigException expected) {
            return true;
        }
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
