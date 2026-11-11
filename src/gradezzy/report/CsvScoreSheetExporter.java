/*
 * File: CsvScoreSheetExporter.java
 *
 * Purpose: Writes the provided score sheet with the final grade filled
 * in, and, when export.csv.extra.columns is on, the mark of every
 * question and a remark too.
 *
 * Where it fits: The export required by the brief. Columns, the id prefix, the
 * end marker, quotes, the encoding and the line ending stay exactly as
 * provided. With export.csv.extra.columns on, one "Points Grade" column
 * per question and a Remarks column are added just before the
 * End-of-Line Indicator, which stays last (see ScoreSheetColumns).
 */
package gradezzy.report;

import java.io.IOException;
import java.nio.ByteBuffer;
import java.nio.charset.CharacterCodingException;
import java.nio.charset.Charset;
import java.nio.charset.CharsetDecoder;
import java.nio.charset.CodingErrorAction;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;

import gradezzy.config.AppConfig;
import gradezzy.config.ConfigKeys;
import gradezzy.config.ExportFolders;
import gradezzy.model.Assessment;
import gradezzy.model.GradingRun;
import gradezzy.model.Question;
import gradezzy.model.QuestionResult;
import gradezzy.model.Section;
import gradezzy.model.Submission;
import gradezzy.model.SubmissionStatus;
import gradezzy.util.TextUtils;

/**
 * Writes the provided score sheet with the final grade filled in.
 *
 * <p>Tier: MUST. Owner: Huxley.
 */
public class CsvScoreSheetExporter implements Exporter {

    /**
     * The format name.
     */
    public static final String FORMAT_NAME = "CSV score sheet";

    /**
     * The file extension, without the dot.
     */
    public static final String FILE_EXTENSION = "csv";

    /** The column of each student's remark, added after the marks. */
    public static final String REMARKS_COLUMN = "Remarks";

    // Stands for a column the sheet does not have
    private static final int NO_COLUMN = -1;

    // Header of the column that holds each student's username
    private static final String USERNAME_COLUMN = "Username";

    // Header of the column that receives each student's total
    private static final String NUMERATOR_COLUMN =
            "Calculated Final Grade Numerator";

    // Value of CSV_LINE_ENDING that selects Windows line endings
    private static final String CRLF_SETTING = "CRLF";

    // The Windows line ending, as in the provided sheet
    private static final String CRLF = "\r\n";

    // The line ending used when the setting is not CRLF
    private static final String LF = "\n";

    // Opens and closes a quoted CSV value
    private static final char QUOTE = '"';

    // Separates the fields of a CSV record
    private static final char COMMA = ',';

    // Ends a record on its own (old Mac) or as the first half of CRLF
    private static final char CARRIAGE_RETURN = '\r';

    // Ends a record on its own or as the second half of CRLF
    private static final char LINE_FEED = '\n';

    // Records are counted from 1 in messages, so the header is record 1
    private static final int HEADER_RECORD_NUMBER = 1;

    // A sheet that is not valid UTF-8 was saved by Excel on Windows
    private static final String WINDOWS_CHARSET = "windows-1252";

    // The mark some editors put at the very start of a UTF-8 file
    private static final String BYTE_ORDER_MARK = "\uFEFF";

    // Places in the int[] that findField returns
    private static final int FIELD_START = 0;
    private static final int FIELD_END = 1;

    // Written when a student has no graded submission
    private static final String NO_SCORE = "";

    // Separates the fields that are added to a record
    private static final String FIELD_SEPARATOR = ",";

    // The application settings
    private final AppConfig config;

    // The score sheet from the settings, used only for a run that does
    // not record the sheet it was graded against
    private final Path templateScoreSheet;

    // Gives each student's remark, or null for a blank Remarks column
    private ScoreSheetRemarks remarks = null;

    /**
     * Creates a new csv score sheet exporter.
     *
     * @param config the application settings
     * @param templateScoreSheet the score sheet from the settings, used for
     *        a run that does not record the sheet it was graded against
     */
    public CsvScoreSheetExporter(AppConfig config, Path templateScoreSheet) {
        this.config = config;
        this.templateScoreSheet = templateScoreSheet;
    }

    /**
     * Sets what gives each student's remark, such as "Cheated with
     * KEVIN LIM (kevin.lim.2025)". With none set, the Remarks column is
     * left blank.
     *
     * @param remarks the remarks, or null for none
     */
    public void setRemarks(ScoreSheetRemarks remarks) {
        this.remarks = remarks;
    }

    /**
     * Returns the format name.
     *
     * @return the format name
     */
    @Override
    public String getFormatName() {
        return FORMAT_NAME;
    }

    /**
     * Returns the file extension.
     *
     * @return the extension, without the dot
     */
    @Override
    public String getFileExtension() {
        return FILE_EXTENSION;
    }

    /**
     * Writes the provided score sheet with the final grade filled in.
     *
     * <p>Every row and value of the template is kept as it is, quotes
     * included, apart from the numerator, which receives the student's
     * total. Only when export.csv.extra.columns is on, each question of
     * the run's assessment also gets a column, such as "Lab Test 2 Q1a
     * Points Grade", in assessment order, just before the End-of-Line
     * Indicator; a column the template already has is filled in place.
     * A Remarks column follows them, with each student's remark from
     * setRemarks, such as "Possible cheating with KEVIN LIM
     * (kevin.lim.2025)"; a sheet that has one already is filled in place.
     * A student without a graded submission is left
     * blank so the instructor notices, even if the template held a mark.
     * The file is written in the encoding the template was saved in.
     *
     * @param gradingRun the grading run
     * @param outputFile the output file
     * @throws ExportException if the operation fails
     */
    @Override
    public void export(GradingRun gradingRun,
            Path outputFile) throws ExportException {
        if (gradingRun == null || outputFile == null) {
            throw new ExportException(
                    "Nothing to export: the grading run or file is missing.");
        }

        Path template = chooseTemplate(gradingRun);
        byte[] templateBytes = readTemplateBytes(template);
        Charset charset = chooseCharset(templateBytes);
        String templateText = new String(templateBytes, charset);
        // Set a byte order mark aside, as RosterReader does, so the first
        // column is still found by name; it is written back below
        String byteOrderMark = "";
        if (templateText.startsWith(BYTE_ORDER_MARK)) {
            byteOrderMark = BYTE_ORDER_MARK;
            templateText = templateText.substring(BYTE_ORDER_MARK.length());
        }
        List<String> records = splitRecords(templateText);
        if (records.isEmpty()) {
            throw new ExportException("The score sheet template "
                    + template + " is empty.");
        }

        // Find the columns by name so a reordered sheet still works
        List<String> header = splitLine(records.get(0), HEADER_RECORD_NUMBER);
        int usernameIndex = findColumn(header, USERNAME_COLUMN);
        int numeratorIndex = findColumn(header, NUMERATOR_COLUMN);
        boolean extraColumns = wantsExtraColumns();
        ScoreSheetColumns columns = chooseColumns(gradingRun, header,
                usernameIndex, numeratorIndex, extraColumns);

        Map<String, Submission> submissionsByUsername =
                indexByUsername(gradingRun.getSubmissions());
        String lineEnding = getLineEnding();
        Map<String, String> remarkMap = readRemarks(gradingRun,
                extraColumns);
        int remarksIndex = header.indexOf(REMARKS_COLUMN);
        List<String> outputRecords = fillRecords(records, columns,
                submissionsByUsername, remarkMap, remarksIndex,
                extraColumns);

        // Every record, the header included, ends with the line ending
        String output = byteOrderMark
                + TextUtils.join(lineEnding, outputRecords) + lineEnding;

        try {
            Files.write(outputFile, output.getBytes(charset));
        } catch (IOException exception) {
            throw new ExportException("Could not write the score sheet to "
                    + outputFile + ". Check that the folder exists and is "
                    + "not read-only.", exception);
        }
    }

    // Places the question columns of the sheet. With the extra columns
    // off, no question is given a column.
    private ScoreSheetColumns chooseColumns(GradingRun gradingRun,
            List<String> header, int usernameIndex, int numeratorIndex,
            boolean extraColumns) {
        Assessment columnAssessment = null;
        if (extraColumns) {
            columnAssessment = gradingRun.getAssessment();
        }
        return new ScoreSheetColumns(header, usernameIndex, numeratorIndex,
                columnAssessment);
    }

    // Maps each username to its remark, or gives an empty map when the
    // extra columns are off or no remarks were set.
    private Map<String, String> readRemarks(GradingRun gradingRun,
            boolean extraColumns) {
        Map<String, String> remarkMap = new HashMap<>();
        boolean remarksWanted = extraColumns && remarks != null;
        if (remarksWanted) {
            remarkMap = remarks.remarksFor(gradingRun);
        }
        return remarkMap;
    }

    // The header with the question columns added, then every other
    // record with its marks filled in, in the template's order. With the
    // extra columns off, the header is kept and only the total is filled.
    private List<String> fillRecords(List<String> records,
            ScoreSheetColumns columns,
            Map<String, Submission> submissionsByUsername,
            Map<String, String> remarkMap, int remarksIndex,
            boolean extraColumns) throws ExportException {
        List<String> outputRecords = new ArrayList<>();
        List<String> addedNames = new ArrayList<>(columns.listAddedNames());
        boolean addRemarksColumn = extraColumns
                && remarksIndex == NO_COLUMN;
        if (addRemarksColumn) {
            addedNames.add(REMARKS_COLUMN);
        }
        String header = insertFields(records.get(0),
                columns.getInsertIndex(), addedNames);
        outputRecords.add(header);

        for (int index = 1; index < records.size(); index++) {
            String record = records.get(index);
            int recordNumber = index + 1;
            String filledRecord = fillRow(record, recordNumber, columns,
                    submissionsByUsername, remarkMap, remarksIndex,
                    extraColumns);
            outputRecords.add(filledRecord);
        }
        return outputRecords;
    }

    // The sheet the batch was graded against, so the export lists the
    // same students. A run saved before the sheet was recorded uses its
    // section's own sheet, else the one for everyone, both read from the
    // settings now (Settings can change them while the app is open). A
    // run with no section uses the template.
    private Path chooseTemplate(GradingRun gradingRun) {
        if (gradingRun.getScoreSheetFile() != null) {
            return gradingRun.getScoreSheetFile();
        }
        Section section = gradingRun.getSection();
        if (section == null) {
            return templateScoreSheet;
        }
        return ExportFolders.findScoreSheet(config, section.getModuleCode(),
                section.getSectionCode());
    }

    // Reads the template as bytes, so its encoding can be checked
    private byte[] readTemplateBytes(Path template) throws ExportException {
        try {
            return Files.readAllBytes(template);
        } catch (IOException exception) {
            throw new ExportException("Could not read the score sheet "
                    + template + " that this batch was graded against. "
                    + "Put the file back, or grade the batch again with "
                    + "a score sheet that exists.", exception);
        }
    }

    // UTF-8 when the bytes are valid UTF-8, otherwise Windows-1252, the
    // encoding Excel uses on Windows. A UTF-8 byte order mark is set
    // aside by export and written back at the start of the file.
    private Charset chooseCharset(byte[] bytes) {
        CharsetDecoder decoder = StandardCharsets.UTF_8.newDecoder();
        decoder.onMalformedInput(CodingErrorAction.REPORT);
        decoder.onUnmappableCharacter(CodingErrorAction.REPORT);

        try {
            decoder.decode(ByteBuffer.wrap(bytes));
            return StandardCharsets.UTF_8;
        } catch (CharacterCodingException exception) {
            return Charset.forName(WINDOWS_CHARSET);
        }
    }

    // Splits the template into records without their line endings. A line
    // ending inside quotes belongs to the value (a name over two lines),
    // so it stays in the record exactly as it was. CRLF, LF and CR all end
    // a record, and the final line ending does not make an empty record.
    private List<String> splitRecords(String text) throws ExportException {
        List<String> records = new ArrayList<>();
        int recordStart = 0;
        boolean quoted = false;
        int index = 0;
        while (index < text.length()) {
            char current = text.charAt(index);
            if (current == QUOTE) {
                quoted = !quoted;
            } else if (!quoted && current == CARRIAGE_RETURN) {
                records.add(text.substring(recordStart, index));
                int next = index + 1;
                boolean partOfCrlf = next < text.length()
                        && text.charAt(next) == LINE_FEED;
                if (partOfCrlf) {
                    index++;
                }
                recordStart = index + 1;
            } else if (!quoted && current == LINE_FEED) {
                records.add(text.substring(recordStart, index));
                recordStart = index + 1;
            }
            index++;
        }

        if (quoted) {
            throw new ExportException("The score sheet template has a "
                    + "quote that is never closed.");
        }

        // Keep a last record that has no line ending after it
        if (recordStart < text.length()) {
            records.add(text.substring(recordStart));
        }
        return records;
    }

    // Returns the position of a column, or fails if it is not in the header
    private int findColumn(List<String> header,
            String columnName) throws ExportException {
        int columnIndex = header.indexOf(columnName);
        if (columnIndex < 0) {
            throw new ExportException("The score sheet template has no \""
                    + columnName + "\" column.");
        }
        return columnIndex;
    }

    // Maps each username, in lower case, to its submission, so an upload
    // named PING.LEE.2023 still fills the row of ping.lee.2023. A student
    // matched to the score sheet by hand, such as an upload named with a
    // typo, fills the row of the student it was matched to. A rejected
    // duplicate never replaces a submission that was loaded.
    private Map<String, Submission> indexByUsername(
            List<Submission> submissions) {
        Map<String, Submission> submissionsByUsername = new HashMap<>();
        for (Submission submission : submissions) {
            String username = submission.getUsername();
            if (submission.getStudent() != null) {
                username = submission.getStudent().getUsername();
            }
            String key = username.toLowerCase(Locale.ROOT);
            Submission earlier = submissionsByUsername.get(key);
            if (earlier == null || earlier.getIngestFailure() != null) {
                submissionsByUsername.put(key, submission);
            }
        }
        return submissionsByUsername;
    }

    // Returns the record with the total and the question marks filled
    // in. Only the text of those fields changes, and the added fields go
    // before the End-of-Line Indicator; every other character, quotes
    // included, is copied as it is. The username is matched without the
    // spaces around it. A blank record, or one too short to hold the
    // username and the total, is kept exactly as it was. With the extra
    // columns off, only the total is filled in.
    private String fillRow(String record, int recordNumber,
            ScoreSheetColumns columns,
            Map<String, Submission> submissionsByUsername,
            Map<String, String> remarkMap, int remarksIndex,
            boolean extraColumns) throws ExportException {
        if (TextUtils.isBlank(record)) {
            return record;
        }

        int[] usernameField = findField(record, columns.getUsernameIndex());
        int[] numeratorField = findField(record,
                columns.getNumeratorIndex());
        if (usernameField == null || numeratorField == null) {
            return record;
        }

        // The username field may be quoted, so read its value properly
        String usernameText = record.substring(usernameField[FIELD_START],
                usernameField[FIELD_END]);
        List<String> usernameValues = splitLine(usernameText, recordNumber);
        String username = removeIdPrefix(usernameValues.get(0).trim())
                .trim();
        String key = username.toLowerCase(Locale.ROOT);
        Submission submission = submissionsByUsername.get(key);

        String filled = replaceField(record, columns.getNumeratorIndex(),
                formatScore(submission));
        if (!extraColumns) {
            return filled;
        }

        List<String> addedMarks = new ArrayList<>();
        List<Question> questions = columns.getQuestions();
        for (int position = 0; position < questions.size(); position++) {
            String mark = formatQuestionScore(submission,
                    questions.get(position));
            if (columns.isInSheet(position)) {
                filled = replaceField(filled,
                        columns.getQuestionIndex(position), mark);
            } else {
                addedMarks.add(mark);
            }
        }
        // An added field is quoted when it is joined in; a field in place
        // is written whole, so it is quoted here
        String remark = remarkMap.getOrDefault(key, "");
        if (remarksIndex == NO_COLUMN) {
            addedMarks.add(remark);
        } else {
            List<String> field = new ArrayList<>();
            field.add(remark);
            filled = replaceField(filled, remarksIndex,
                    TextUtils.joinCsvLine(field));
        }
        return insertFields(filled, columns.getInsertIndex(), addedMarks);
    }

    // Returns the record with the text of one field replaced, or the
    // record as it was when it is too short to have that field
    private String replaceField(String record, int fieldIndex, String value) {
        int[] field = findField(record, fieldIndex);
        if (field == null) {
            return record;
        }
        return record.substring(0, field[FIELD_START]) + value
                + record.substring(field[FIELD_END]);
    }

    // Returns the record with new fields put in at a place, before the
    // field that was there. A record with fewer fields gets empty fields
    // first, so the new ones still line up with the header.
    private String insertFields(String record, int fieldIndex,
            List<String> values) {
        if (values.isEmpty()) {
            return record;
        }
        String joined = TextUtils.joinCsvLine(values);
        int[] field = findField(record, fieldIndex);
        if (field != null) {
            return record.substring(0, field[FIELD_START]) + joined
                    + FIELD_SEPARATOR + record.substring(field[FIELD_START]);
        }

        String padded = record;
        int fieldCount = countFields(record);
        for (int count = fieldCount; count < fieldIndex; count++) {
            padded += FIELD_SEPARATOR;
        }
        return padded + FIELD_SEPARATOR + joined;
    }

    // Counts the fields of a record; a comma inside quotes is part of a
    // value
    private int countFields(String record) {
        int fieldCount = 1;
        boolean quoted = false;
        for (int index = 0; index < record.length(); index++) {
            char current = record.charAt(index);
            if (current == QUOTE) {
                quoted = !quoted;
            } else if (!quoted && current == COMMA) {
                fieldCount++;
            }
        }
        return fieldCount;
    }

    // Finds where field number fieldIndex starts and ends in a record, as
    // {start, end} with end just after its last character, or null if the
    // record has fewer fields. A comma inside quotes is part of a value.
    private int[] findField(String record, int fieldIndex) {
        int fieldNumber = 0;
        int fieldStart = 0;
        boolean quoted = false;
        for (int index = 0; index < record.length(); index++) {
            char current = record.charAt(index);
            if (current == QUOTE) {
                quoted = !quoted;
            } else if (!quoted && current == COMMA) {
                if (fieldNumber == fieldIndex) {
                    return new int[] {fieldStart, index};
                }
                fieldNumber++;
                fieldStart = index + 1;
            }
        }

        if (fieldNumber == fieldIndex) {
            return new int[] {fieldStart, record.length()};
        }
        return null;
    }

    // Splits one record, naming the record if its quotes are broken
    private List<String> splitLine(String record,
            int recordNumber) throws ExportException {
        try {
            return TextUtils.splitCsvLine(record);
        } catch (IllegalArgumentException exception) {
            throw new ExportException("Record " + recordNumber + " of the "
                    + "score sheet template is not valid CSV: "
                    + exception.getMessage(), exception);
        }
    }

    // Removes one id prefix, so "#ping.lee.2023" becomes "ping.lee.2023"
    private String removeIdPrefix(String value) {
        String prefix = config.getString(ConfigKeys.CSV_ID_PREFIX);
        if (!prefix.isEmpty() && value.startsWith(prefix)) {
            return value.substring(prefix.length());
        }
        return value;
    }

    // Writes 12 for a whole total and 10.5 otherwise. Only a graded
    // submission gets a number; anything else is left blank, even if the
    // template held a mark, as the Export step says.
    private String formatScore(Submission submission) {
        boolean notGraded = submission == null
                || submission.getStatus() != SubmissionStatus.GRADED;
        if (notGraded) {
            return NO_SCORE;
        }

        return ScoreFormatter.format(submission.getTotalScore());
    }

    // Writes a question's mark, as formatScore writes the total: blank
    // for a student who was not graded, and blank for a question the
    // student has no result for
    private String formatQuestionScore(Submission submission,
            Question question) {
        boolean notGraded = submission == null
                || submission.getStatus() != SubmissionStatus.GRADED;
        if (notGraded) {
            return NO_SCORE;
        }

        QuestionResult result = submission.findResult(question.getId());
        if (result == null) {
            return NO_SCORE;
        }
        return ScoreFormatter.format(result.getScore());
    }

    // Reads export.csv.extra.columns; an older config.properties without
    // the line keeps the provided format
    private boolean wantsExtraColumns() {
        if (!config.hasKey(ConfigKeys.CSV_EXTRA_COLUMNS)) {
            return false;
        }
        return config.getBoolean(ConfigKeys.CSV_EXTRA_COLUMNS);
    }

    // Reads the line ending setting: CRLF as provided, or LF
    private String getLineEnding() {
        String setting = config.getString(ConfigKeys.CSV_LINE_ENDING);
        if (setting.equalsIgnoreCase(CRLF_SETTING)) {
            return CRLF;
        }
        return LF;
    }
}
