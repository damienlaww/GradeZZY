/*
 * File: ReportTest.java
 *
 * Purpose: Unit checks for the report package: CsvScoreSheetExporter
 * (question columns, Remarks column, a template that already has them,
 * quoting, CRLF, encodings, BOM), ScoreFormatter, RunAuditFilter rules,
 * and the JSON and anomaly exporters on a small run.
 *
 * Where it fits: Run by hand after compiling, as tests/README.md says.
 * Reads the fixed test settings. Pass -Dreport.out=folder to keep the
 * JSON file for a check with another tool.
 */
import java.nio.charset.Charset;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
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
import gradezzy.model.AuditEntry;
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
import gradezzy.report.AnomalyReportExporter;
import gradezzy.report.CsvScoreSheetExporter;
import gradezzy.report.ExportException;
import gradezzy.report.JsonExporter;
import gradezzy.report.JsonWriter;
import gradezzy.report.RunAuditFilter;
import gradezzy.report.ScoreFormatter;
import gradezzy.report.ScoreSheetColumns;
import gradezzy.report.ScoreSheetRemarks;
import gradezzy.report.StudentReportExporter;
import gradezzy.util.FileUtils;
import gradezzy.util.TextUtils;

/**
 * Runs the report checks and prints how many passed.
 */
public class ReportTest {

    // The module every run is for
    private static final String MODULE = "CS102";

    // The section every run is for
    private static final Section SECTION = new Section(MODULE, "G1");

    // The header row of a score sheet template from the LMS
    private static final String HEADER = "OrgDefinedId,Username,Last Name,"
            + "First Name,Email,Calculated Final Grade Numerator,"
            + "Calculated Final Grade Denominator,End-of-Line Indicator";

    // The assessment names used by the runs
    private static final String LAB_TEST = "Lab Test 3";
    private static final String LAB = "Lab";

    // The name of the Q1 column for Lab Test 3
    private static final String Q1_COLUMN = "Lab Test 3 Q1 Points Grade";

    // The students on the score sheet
    private static final String ANN = "ann";
    private static final String ANN_LEE = "ann.lee";

    // Ann's and Bob's marks for Q1 and Q2a
    private static final double ANN_Q1 = 1.5;
    private static final double ANN_Q2 = 2.25;
    private static final double BOB_Q1 = 2;
    private static final double BOB_Q2 = 3;

    // Ann's Q1 mark and total as written on the score sheet
    private static final String ANN_Q1_TEXT = "1.5";
    private static final String ANN_TOTAL = "3.75";

    // The columns of the LMS header that the checks read
    private static final int LAST_NAME_COLUMN = 2;
    private static final int NUMERATOR_COLUMN = 5;

    // The audit actions the filter checks use
    private static final String GRADED_ACTION = "Graded";
    private static final String FIX_ACTION = "Fix";
    private static final String ADJUST_ACTION = "Adjust";
    private static final String NOTE_ACTION = "Note";

    // The audit detail that names Ann and her new Q1 mark
    private static final String ADJUST_DETAIL = "ann.lee Q1 2 -> 3";

    // The text of the fix entries that name an anomaly
    private static final String APPROVED_FIX = "Approved fix";

    // When the three audit runs started, all on the same day
    private static final LocalDateTime FIRST_RUN_AT = LocalDateTime.of(2026,
            1, 1, 10, 0, 0);
    private static final LocalDateTime LATER_RUN_AT = LocalDateTime.of(2026,
            1, 1, 12, 0, 0);
    private static final LocalDateTime OTHER_RUN_AT = LocalDateTime.of(2026,
            1, 1, 11, 0);

    // Times of audit entries: before the first run, between the first
    // and the later run, and after the later run
    private static final LocalDateTime BEFORE_RUN = LocalDateTime.of(2026,
            1, 1, 9, 0);
    private static final LocalDateTime DURING_RUN = LocalDateTime.of(2026,
            1, 1, 10, 30);
    private static final LocalDateTime AFTER_LATER_RUN = LocalDateTime.of(
            2026, 1, 1, 12, 30);

    // When the run exported to JSON started
    private static final LocalDateTime JSON_RUN_AT = LocalDateTime.of(2026,
            11, 11, 9, 0);

    // The line break inside the written sheets
    private static final String NEWLINE = "\n";

    // The lowest character JSON may hold in a string without escaping
    private static final char FIRST_PLAIN_CHAR = 0x20;

    // The number of checks that passed
    private static int passed = 0;

    // The number of checks that failed
    private static int failed = 0;

    // The fixed test settings
    private static AppConfig config;

    // The text of the last score sheet written by export
    private static String lastOutput = "";

    /**
     * Runs every check.
     *
     * @param args not used
     * @throws Exception if a temporary file cannot be written
     */
    public static void main(String[] args) throws Exception {
        Path root = Files.createTempDirectory("gradezzy-report-tests-")
                .toRealPath();
        try {
            config = TestConfig.load();
            // Most checks are of the question and Remarks columns, which
            // are off in the fixed test settings, as by default
            config.setString(ConfigKeys.CSV_EXTRA_COLUMNS, "true");
            checkFormatter();
            checkCsv(root);
            checkAudit();
            checkOtherExporters(root);
        } finally {
            FileUtils.deleteDirectory(root);
        }
        System.out.println("ReportTest: " + passed + " passed, " + failed
                + " failed");
        if (failed > 0) {
            System.exit(1);
        }
    }

    // Checks how scores are written as text.
    private static void checkFormatter() {
        check(ScoreFormatter.format(3).equals("3"), "3");
        check(ScoreFormatter.format(2.5).equals("2.5"), "2.5");
        check(ScoreFormatter.format(0.125).equals("0.13")
                || ScoreFormatter.format(0.125).equals("0.12"), "0.125");
        check(ScoreFormatter.format(1.0 / 3).equals("0.33"), "1/3");
        check(ScoreFormatter.format(-0.001).equals("0"), "-0 is 0");
        check(ScoreFormatter.format(10.0).equals("10"),
                "10 keeps its zero before the point");
        check(ScoreFormatter.format(100).equals("100"), "100");
        check(ScoreFormatter.format(0).equals("0"), "0");
        check(ScoreFormatter.format(1e7).equals("10000000"), "1e7");
        check(ScoreFormatter.format(Double.NaN).equals("NaN"), "NaN shown");
    }

    // Builds an assessment with Q1 (out of 2) and Q2a (out of 3).
    private static Assessment assessment(String name) {
        Assessment assessment = new Assessment(name, MODULE);
        assessment.addQuestion(new Question("Q1", "Q1", "Q1.java", 2));
        assessment.addQuestion(new Question("Q2a", "Q2", "Q2a.java", 3));
        return assessment;
    }

    // Builds a run where Ann and Bob are graded and Cat failed.
    private static GradingRun run(Path sheet, String name) {
        GradingRun run = new GradingRun("RUN-9", assessment(name), SECTION,
                LocalDateTime.now());
        run.setScoreSheetFile(sheet);
        run.addSubmission(graded(ANN, ANN_Q1, ANN_Q2));
        run.addSubmission(graded("bob", BOB_Q1, BOB_Q2));
        Submission cat = new Submission("cat", SECTION, sheet);
        cat.setStatus(SubmissionStatus.FAILED);
        run.addSubmission(cat);
        return run;
    }

    // Builds a graded submission with the two question marks.
    private static Submission graded(String user, double q1, double q2) {
        Submission submission = new Submission(user, SECTION,
                Path.of("/tmp/" + user + ".zip"));
        submission.setStatus(SubmissionStatus.GRADED);
        Assessment lab = assessment(LAB);
        QuestionResult firstResult = new QuestionResult(lab.getQuestions()
                .get(0));
        firstResult.setScore(q1);
        QuestionResult secondResult = new QuestionResult(lab.getQuestions()
                .get(1));
        secondResult.setScore(q2);
        submission.addQuestionResult(firstResult);
        submission.addQuestionResult(secondResult);
        return submission;
    }

    // Writes the template, exports the run (or a new one) into it and
    // gives back the rows of the written sheet.
    private static List<List<String>> export(Path root, String name,
            String template, Charset charset, GradingRun run,
            ScoreSheetRemarks remarks) throws Exception {
        Path sheet = root.resolve(name + ".csv");
        Files.write(sheet, template.getBytes(charset));
        GradingRun exportRun = run;
        if (exportRun == null) {
            exportRun = run(sheet, LAB_TEST);
        }
        exportRun.setScoreSheetFile(sheet);
        CsvScoreSheetExporter exporter = new CsvScoreSheetExporter(config,
                sheet);
        if (remarks != null) {
            exporter.setRemarks(remarks);
        }
        Path out = root.resolve(name + "-out.csv");
        exporter.export(exportRun, out);
        String text = new String(Files.readAllBytes(out), charset);
        lastOutput = text;
        List<List<String>> rows = new ArrayList<>();
        for (String line : splitRecords(text)) {
            rows.add(TextUtils.splitCsvLine(line));
        }
        return rows;
    }

    // Checks the score sheet export on many kinds of template.
    private static void checkCsv(Path root) throws Exception {
        String body = HEADER + "\r\n#1,#ann,Lee,Ann,ann@x,,5,#\r\n"
                + "#2,#bob,\"Tan, Jr.\",Bob,bob@x,,5,#\r\n"
                + "#3,#cat,C,Cat,cat@x,,5,#\r\n"
                + "#4,#dan,D,Dan,dan@x,7,5,#\r\n";
        checkProvidedFormat(root, body);
        checkBasicSheet(root, body);
        checkFullTemplate(root);
        // LF setting
        AppConfig lfConfig = TestConfig.load();
        lfConfig.setString(ConfigKeys.CSV_LINE_ENDING, "LF");
        Path lfSheet = root.resolve("lf.csv");
        Files.writeString(lfSheet, body);
        Path lfOut = root.resolve("lf-out.csv");
        new CsvScoreSheetExporter(lfConfig, lfSheet).export(run(lfSheet,
                LAB), lfOut);
        check(!Files.readString(lfOut).contains("\r"), "LF setting");
        // windows-1252 template
        String latin = HEADER
                + "\r\n#1,#ann,M\u00fcller,Zo\u00eb,ann@x,,5,#\r\n";
        Charset latinCharset = Charset.forName("windows-1252");
        List<List<String>> latinRows = export(root, "latin", latin,
                latinCharset, null, null);
        check(latinRows.get(1).get(LAST_NAME_COLUMN).equals("M\u00fcller")
                && latinRows.get(1).get(NUMERATOR_COLUMN).equals(ANN_TOTAL),
                "windows-1252 template written back in windows-1252");
        checkOddTemplates(root);
        checkOddRuns(root, body);
        // Chinese remark into a windows-1252 sheet
        Map<String, String> chinese = new HashMap<>();
        chinese.put(ANN, "\u6284\u88ad");
        export(root, "latin2", latin, latinCharset, null, run2 -> chinese);
        // Known limitation: the sheet is written back in its own encoding,
        // so a remark it cannot hold is written as ?. Save the score sheet
        // as "CSV UTF-8" to keep such remarks.
        note("a Chinese remark in a windows-1252 sheet became: "
                + lastOutput.split(NEWLINE)[1].trim());
    }

    // Checks the columns, marks and line endings of a plain export.
    // With export.csv.extra.columns off, as by default, the sheet keeps
    // the provided format: the same header and columns, and only the
    // total filled in.
    private static void checkProvidedFormat(Path root, String body)
            throws Exception {
        AppConfig plainConfig = TestConfig.load();
        Path sheet = root.resolve("plain.csv");
        Files.writeString(sheet, body);
        Path out = root.resolve("plain-out.csv");
        CsvScoreSheetExporter exporter = new CsvScoreSheetExporter(
                plainConfig, sheet);
        exporter.setRemarks(run -> new HashMap<String, String>());
        exporter.export(run(sheet, LAB_TEST), out);
        String text = Files.readString(out);
        String[] lines = text.split("\r\n");
        check(lines[0].equals(HEADER), "extra columns off: header kept: "
                + lines[0]);
        check(lines[1].equals("#1,#ann,Lee,Ann,ann@x," + ANN_TOTAL
                + ",5,#"), "extra columns off: only the total is filled: "
                + lines[1]);
        check(lines[4].equals("#4,#dan,D,Dan,dan@x,,5,#"), "extra columns "
                + "off: a student not in the run is blanked: " + lines[4]);
    }

    private static void checkBasicSheet(Path root, String body)
            throws Exception {
        List<List<String>> rows = export(root, "basic", body,
                StandardCharsets.UTF_8, null, null);
        List<String> header = rows.get(0);
        String q1Name = ScoreSheetColumns.nameColumn(assessment(LAB_TEST),
                assessment(LAB_TEST).getQuestions().get(0));
        check(q1Name.equals(Q1_COLUMN), "question column "
                + "name: " + q1Name);
        int q1 = header.indexOf(q1Name);
        int remarks = header.indexOf("Remarks");
        int end = header.indexOf("End-of-Line Indicator");
        check(q1 > 0 && remarks > q1 && end == header.size() - 1,
                "question and Remarks columns inserted before End-of-Line: "
                + header);
        check(lastOutput.contains("\r\n") && !lastOutput.replace("\r\n", "")
                .contains(NEWLINE), "CRLF line endings kept");
        List<String> ann = rows.get(1);
        check(ann.get(NUMERATOR_COLUMN).equals(ANN_TOTAL), "ann total 3.75 ("
                + ann.get(NUMERATOR_COLUMN) + ")");
        check(ann.get(q1).equals(ANN_Q1_TEXT)
                && ann.get(q1 + 1).equals("2.25"), "ann question marks");
        check(ann.size() == header.size(), "ann row as wide as header");
        int bobRow = 2;
        int catRow = 3;
        int danRow = 4;
        List<String> bob = rows.get(bobRow);
        check(bob.get(LAST_NAME_COLUMN).equals("Tan, Jr.")
                && bob.get(NUMERATOR_COLUMN).equals("5"),
                "quoted last name kept, bob total 5");
        check(rows.get(catRow).get(NUMERATOR_COLUMN).isEmpty(),
                "failed cat left blank");
        check(rows.get(danRow).get(NUMERATOR_COLUMN).isEmpty(),
                "dan not graded: old value 7 cleared (got '"
                + rows.get(danRow).get(NUMERATOR_COLUMN) + "')");
        check(rows.get(1).get(end).equals("#"), "End-of-Line marker kept");
    }

    // Checks a template that already has the question and Remarks
    // columns, with a remark that holds a comma, a quote and a newline.
    private static void checkFullTemplate(Path root) throws Exception {
        String full = "OrgDefinedId,Username,Last Name,First Name,Email,"
                + "Calculated Final Grade Numerator,Lab Test 3 Q1 Points "
                + "Grade,Lab Test 3 Q2a Points Grade,Remarks,"
                + "End-of-Line Indicator\n"
                + "#1,#ann,Lee,Ann,ann@x,,9,9,old remark,#\n";
        String remark = "Copied, \"maybe\"\nsee Q1";
        Map<String, String> remarkMap = new HashMap<>();
        remarkMap.put(ANN, remark);
        List<List<String>> fullRows = export(root, "full", full,
                StandardCharsets.UTF_8, null, run2 -> remarkMap);
        int fullWidth = 10;
        check(fullRows.get(0).size() == fullWidth, "no column added twice ("
                + fullRows.get(0) + ")");
        int headerAndRest = 2;
        System.out.println("   remark with newline written as: "
                + lastOutput.split(NEWLINE, headerAndRest)[1].replace("\r",
                "\\r"));
        // Remark with a newline: the row must still read back
        int fullQ1Column = 6;
        int fullRemarksColumn = 8;
        boolean rowReads = true;
        try {
            List<String> annRow = fullRows.get(1);
            rowReads = annRow.get(fullQ1Column).equals(ANN_Q1_TEXT)
                    && annRow.get(fullRemarksColumn).equals(remark);
        } catch (RuntimeException exception) {
            rowReads = false;
        }
        check(rowReads || lastOutput.contains(
                "\"Copied, \"\"maybe\"\"\nsee Q1\""),
                "remark with comma, quote and newline quoted in the file");
    }

    // Checks templates with a byte order mark, other username forms, no
    // numerator, no file, blank Excel rows and a short row.
    private static void checkOddTemplates(Path root) throws Exception {
        // UTF-8 BOM template with Username first
        String bomFirst = "\uFEFFUsername,OrgDefinedId,Last Name,First Name,"
                + "Email,Calculated Final Grade Numerator\n#ann,#1,L,A,a@x,\n";
        boolean bomOk = false;
        try {
            List<List<String>> bomRows = export(root, "bom", bomFirst,
                    StandardCharsets.UTF_8, null, null);
            bomOk = bomRows.get(1).get(NUMERATOR_COLUMN).equals(ANN_TOTAL);
        } catch (ExportException exception) {
            System.out.println("   BOM + Username first: " + exception
                    .getMessage());
            bomOk = false;
        }
        check(bomOk, "a template with a byte order mark and Username first "
                + "exports (the roster reader accepts it)");
        // Username in different case, no prefix
        String mixed = HEADER + "\n1,ANN,L,A,a@x,,5,#\n";
        check(export(root, "mixed", mixed, StandardCharsets.UTF_8, null, null)
                .get(1).get(NUMERATOR_COLUMN).equals(ANN_TOTAL),
                "username case and no prefix");
        // Missing numerator column
        boolean noNumerator = false;
        try {
            export(root, "nonum", "OrgDefinedId,Username\n#1,#ann\n",
                    StandardCharsets.UTF_8, null, null);
        } catch (ExportException expected) {
            noNumerator = true;
        }
        check(noNumerator, "template without numerator refused");
        // Missing template file
        GradingRun gone = run(root.resolve("gone.csv"), LAB);
        boolean goneRefused = false;
        try {
            new CsvScoreSheetExporter(config, null).export(gone,
                    root.resolve("x.csv"));
        } catch (ExportException expected) {
            goneRefused = true;
        }
        check(goneRefused, "missing template refused");
        // Empty Excel rows at the end keep their width
        String excel = HEADER + "\n#1,#ann,L,A,a@x,,5,#\n,,,,,,,\n";
        List<List<String>> excelRows = export(root, "excel", excel,
                StandardCharsets.UTF_8, null, null);
        int blankRow = 2;
        check(excelRows.get(blankRow).size() == excelRows.get(0).size(),
                "empty Excel row padded to the new width ("
                + excelRows.get(blankRow).size() + " vs "
                + excelRows.get(0).size() + ")");
        // A row short of End-of-Line
        String shortRow = HEADER + "\n#1,#ann,L,A,a@x,,5\n";
        List<List<String>> shortRows = export(root, "short", shortRow,
                StandardCharsets.UTF_8, null, null);
        int shortQ1 = shortRows.get(0).indexOf(Q1_COLUMN);
        check(shortRows.get(1).size() > shortQ1
                && shortRows.get(1).get(shortQ1).equals(ANN_Q1_TEXT),
                "row without End-of-Line gets its marks in the right column: "
                + shortRows.get(1));
    }

    // Checks an assessment name with odd characters and a student who
    // is in the run twice.
    private static void checkOddRuns(Path root, String body)
            throws Exception {
        // Assessment name with odd characters
        Assessment odd = assessment("Lab/Test: 3 (final)");
        check(ScoreSheetColumns.nameColumn(odd, odd.getQuestions().get(0))
                .equals("Lab Test 3 final Q1 Points Grade"),
                "odd characters in item name: " + ScoreSheetColumns
                .nameColumn(odd, odd.getQuestions().get(0)));
        // Same student twice: graded one wins over failed one
        GradingRun twice = run(root.resolve("basic.csv"), LAB_TEST);
        Submission failedAnn = new Submission("ANN", SECTION,
                root.resolve("x"));
        failedAnn.setStatus(SubmissionStatus.FAILED);
        failedAnn.setIngestFailure("damaged");
        twice.addSubmission(failedAnn);
        List<List<String>> twiceRows = export(root, "twice", body,
                StandardCharsets.UTF_8, twice, null);
        check(twiceRows.get(1).get(NUMERATOR_COLUMN).equals(ANN_TOTAL),
                "a failed duplicate does not blank the graded mark");
    }

    // Checks which audit entries the filter says belong to a run.
    private static void checkAudit() {
        GradingRun one = new GradingRun("RUN-100", assessment(LAB),
                SECTION, FIRST_RUN_AT);
        Submission ann = graded(ANN_LEE, 1, 1);
        ann.addAnomaly(new Anomaly("A-77", AnomalyType.COMPILE_ERROR,
                Severity.ERROR, ANN_LEE, "x", "y"));
        one.addSubmission(ann);
        GradingRun later = new GradingRun("RUN-200", assessment(LAB),
                SECTION, LATER_RUN_AT);
        later.addSubmission(graded(ANN_LEE, 1, 1));
        GradingRun otherSection = new GradingRun("RUN-300", assessment(LAB),
                new Section(MODULE, "G2"), OTHER_RUN_AT);
        List<GradingRun> all = Arrays.asList(one, later, otherSection);
        RunAuditFilter filter = new RunAuditFilter(one, all);
        check(filter.belongs(entry(DURING_RUN, GRADED_ACTION,
                "RUN-100 done")), "names the run");
        check(!filter.belongs(entry(DURING_RUN, GRADED_ACTION,
                "RUN-200 done")), "names another run");
        check(!filter.belongs(entry(DURING_RUN, GRADED_ACTION,
                "RUN-1000 done")), "RUN-1000 is not RUN-100");
        check(filter.belongs(entry(DURING_RUN, "Export",
                "RUN-100-CS102-G1.csv")), "run id followed by a dash");
        check(filter.belongs(entry(DURING_RUN, FIX_ACTION,
                AuditEntry.withReference(APPROVED_FIX, "A-77"))),
                "reference to the run's anomaly");
        check(!filter.belongs(entry(DURING_RUN, FIX_ACTION,
                AuditEntry.withReference(APPROVED_FIX, "A-99"))),
                "reference to another anomaly");
        check(filter.belongs(entry(DURING_RUN, ADJUST_ACTION,
                ADJUST_DETAIL)), "names a student while newest of section");
        check(!filter.belongs(entry(AFTER_LATER_RUN, ADJUST_ACTION,
                ADJUST_DETAIL)),
                "names a student after a later run of the section");
        check(!filter.belongs(entry(DURING_RUN, ADJUST_ACTION,
                "ann.lee2 Q1")), "ann.lee2 is not ann.lee");
        check(filter.belongs(entry(DURING_RUN, ADJUST_ACTION,
                "ann.lee. Done")), "trailing dot after a username");
        check(!filter.belongs(entry(DURING_RUN, "Grading could not start",
                "CS102 G1 ann.lee")), "grading not started never belongs");
        check(filter.belongs(entry(DURING_RUN, NOTE_ACTION,
                "Q1 Did not compile")), "question line while newest");
        check(!filter.belongs(entry(BEFORE_RUN, NOTE_ACTION, ANN_LEE)),
                "before the run started");
        check(!filter.belongs(entry(DURING_RUN, NOTE_ACTION,
                "RUN-555 unknown")), "unknown run id");
        check(filter.keep(null).isEmpty(), "keep null");
        System.out.println("   'CS102 G1' label form: " + SECTION.getLabel());
        check(filter.belongs(entry(DURING_RUN, NOTE_ACTION, "Batch for "
                + SECTION.getLabel() + " started")), "names the section");
        check(!filter.belongs(entry(DURING_RUN, NOTE_ACTION, "Batch for "
                + SECTION.getLabel() + "0 started")), "G10 is not G1");
    }

    // Builds an audit entry.
    private static AuditEntry entry(LocalDateTime time, String action,
            String detail) {
        return new AuditEntry(time, action, detail);
    }

    // Checks the JSON, anomaly CSV and student report exporters on a run
    // with quotes, control characters and newlines.
    private static void checkOtherExporters(Path root) throws Exception {
        GradingRun run = new GradingRun("RUN-json", assessment("Lab \"3\""),
                SECTION, JSON_RUN_AT);
        double annQ2 = 2;
        Submission ann = graded(ANN, 1, annQ2);
        ann.setStudent(new Student("1", ANN, "O\"Neil\\", "Ann\tB", "a@x"));
        ann.getQuestionResults().get(0).setOutput(
                "line1\r\nline2\u0001\u2028");
        ann.getQuestionResults().get(0).addTestCaseResult(
                new TestCaseResult(1, true, "Passed", "f(\"x\")"));
        Anomaly anomaly = new Anomaly("A-1", AnomalyType.SIMILAR_CODE,
                Severity.INFO, ANN, "Q1 is 90% similar, \"see\"\nnow",
                "Compare");
        anomaly.setFixStatus(FixStatus.APPROVED);
        ann.addAnomaly(anomaly);
        run.addSubmission(ann);
        Submission empty = new Submission("zed", SECTION, root);
        empty.setStatus(SubmissionStatus.FAILED);
        run.addSubmission(empty);
        String keep = System.getProperty("report.out");
        Path folder = root;
        if (keep != null) {
            folder = Path.of(keep);
        }
        Files.createDirectories(folder);
        Path json = folder.resolve("run.json");
        new JsonExporter(new JsonWriter()).export(run, json);
        String text = Files.readString(json);
        check(text.contains("\\\"3\\\"") && text.contains("\\r\\n")
                && text.contains("\\u0001") && text.contains("\\t"),
                "JSON escapes quotes, CR LF, control chars and tabs");
        check(balanced(text), "JSON brackets balanced");
        Path anomalies = root.resolve("anomalies.csv");
        new AnomalyReportExporter().export(run, anomalies);
        String csv = Files.readString(anomalies);
        boolean csvReads = true;
        List<String> records = splitRecords(csv);
        for (String record : records) {
            try {
                TextUtils.splitCsvLine(record);
            } catch (IllegalArgumentException exception) {
                csvReads = false;
            }
        }
        int minRecords = 2;
        check(csvReads && records.size() >= minRecords,
                "anomaly CSV is valid CSV (" + records.size() + " records)");
        check(csv.contains("\"Q1 is 90% similar, \"\"see\"\"\nnow\""),
                "anomaly message quoted");
        Path report = root.resolve("ann.txt");
        new StudentReportExporter(ANN).export(run, report);
        check(Files.readString(report).contains(ANN),
                "student report written");
        boolean nullRun = false;
        try {
            new JsonExporter(new JsonWriter()).export(null, json);
        } catch (ExportException expected) {
            nullRun = true;
        } catch (RuntimeException unexpected) {
            nullRun = false;
        }
        check(nullRun, "JSON export of null run refused cleanly");
        check(new JsonWriter().quote(null).equals("null")
                && new JsonWriter().quote("\\").equals("\"\\\\\""),
                "JsonWriter quote");
    }

    // Splits CSV text into records, keeping quoted newlines.
    private static List<String> splitRecords(String text) {
        List<String> records = new ArrayList<>();
        StringBuilder current = new StringBuilder();
        boolean quoted = false;
        String normal = TextUtils.normaliseLineEndings(text);
        for (int i = 0; i < normal.length(); i++) {
            char character = normal.charAt(i);
            if (character == '"') {
                quoted = !quoted;
            }
            if (character == '\n' && !quoted) {
                records.add(current.toString());
                current = new StringBuilder();
            } else {
                current.append(character);
            }
        }
        if (current.length() > 0) {
            records.add(current.toString());
        }
        return records;
    }

    // Says whether the brackets outside strings are balanced.
    private static boolean balanced(String json) {
        int depth = 0;
        boolean inString = false;
        for (int i = 0; i < json.length(); i++) {
            char character = json.charAt(i);
            if (inString) {
                if (character == '\\') {
                    i++;
                } else if (character == '"') {
                    inString = false;
                } else if (character < FIRST_PLAIN_CHAR) {
                    return false;
                }
            } else if (character == '"') {
                inString = true;
            } else if (character == '{' || character == '[') {
                depth++;
            } else if (character == '}' || character == ']') {
                depth--;
                if (depth < 0) {
                    return false;
                }
            }
        }
        return depth == 0 && !inString;
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
