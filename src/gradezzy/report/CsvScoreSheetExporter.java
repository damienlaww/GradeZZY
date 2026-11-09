/*
 * File: CsvScoreSheetExporter.java
 *
 * Purpose: Writes the provided score sheet with the final grade filled in.
 *
 * Where it fits: The export required by the brief. Columns, the id prefix, the
 * end marker, quotes, the encoding and the line ending stay exactly as
 * provided.
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
import gradezzy.model.GradingRun;
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

    // Places in the int[] that findField returns
    private static final int FIELD_START = 0;
    private static final int FIELD_END = 1;

    // Written when a student has no graded submission
    private static final String NO_SCORE = "";

    // The application settings
    private final AppConfig config;

    // The score sheet from the settings, used only for a run that does
    // not record the sheet it was graded against
    private final Path templateScoreSheet;

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
     * total. A student without a graded submission is left blank so the
     * instructor notices. The file is written in the encoding the template
     * was saved in.
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
        List<String> records = splitRecords(templateText);
        if (records.isEmpty()) {
            throw new ExportException("The score sheet template "
                    + template + " is empty.");
        }

        // Find the two columns by name so a reordered sheet still works
        List<String> header = splitLine(records.get(0), HEADER_RECORD_NUMBER);
        int usernameIndex = findColumn(header, USERNAME_COLUMN);
        int numeratorIndex = findColumn(header, NUMERATOR_COLUMN);

        Map<String, Submission> submissionsByUsername =
                indexByUsername(gradingRun.getSubmissions());
        String lineEnding = getLineEnding();
        List<String> outputRecords = fillRecords(records, usernameIndex,
                numeratorIndex, submissionsByUsername);

        // Every record, the header included, ends with the line ending
        String output = TextUtils.join(lineEnding, outputRecords)
                + lineEnding;

        try {
            Files.write(outputFile, output.getBytes(charset));
        } catch (IOException exception) {
            throw new ExportException("Could not write the score sheet to "
                    + outputFile + ". Check that the folder exists and is "
                    + "not read-only.", exception);
        }
    }

    // The header as it was, then every other record with its numerator
    // filled in, in the template's order
    private List<String> fillRecords(List<String> records, int usernameIndex,
            int numeratorIndex, Map<String, Submission> submissionsByUsername)
            throws ExportException {
        List<String> outputRecords = new ArrayList<>();
        outputRecords.add(records.get(0));

        for (int index = 1; index < records.size(); index++) {
            String record = records.get(index);
            int recordNumber = index + 1;
            String filledRecord = fillRow(record, recordNumber,
                    usernameIndex, numeratorIndex, submissionsByUsername);
            outputRecords.add(filledRecord);
        }
        return outputRecords;
    }

    // The sheet the batch was graded against, so the export lists the
    // same students. A run saved before the sheet was recorded uses its
    // section's own sheet, else the one for everyone, both read from the
    // settings now (Settings can change them while the app is open), as
    // the Export warning (ExportService.summarizeScoreSheet) does. A run
    // with no section uses the template.
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
    // encoding Excel uses on Windows. A UTF-8 byte order mark is kept as
    // part of the first header, so it is written back too.
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
    // named PING.LEE.2023 still fills the row of ping.lee.2023. A
    // rejected duplicate never replaces a submission that was loaded.
    private Map<String, Submission> indexByUsername(
            List<Submission> submissions) {
        Map<String, Submission> submissionsByUsername = new HashMap<>();
        for (Submission submission : submissions) {
            String key = submission.getUsername().toLowerCase(Locale.ROOT);
            Submission earlier = submissionsByUsername.get(key);
            if (earlier == null || earlier.getIngestFailure() != null) {
                submissionsByUsername.put(key, submission);
            }
        }
        return submissionsByUsername;
    }

    // Returns the record with the numerator filled in. Only the text of
    // the numerator field changes; every other character, quotes
    // included, is copied as it is. A blank record, or one too short to
    // hold both columns, is kept exactly as it was.
    private String fillRow(String record, int recordNumber,
            int usernameIndex, int numeratorIndex,
            Map<String, Submission> submissionsByUsername)
            throws ExportException {
        if (TextUtils.isBlank(record)) {
            return record;
        }

        int[] usernameField = findField(record, usernameIndex);
        int[] numeratorField = findField(record, numeratorIndex);
        if (usernameField == null || numeratorField == null) {
            return record;
        }

        // The username field may be quoted, so read its value properly
        String usernameText = record.substring(usernameField[FIELD_START],
                usernameField[FIELD_END]);
        List<String> usernameValues = splitLine(usernameText, recordNumber);
        String username = removeIdPrefix(usernameValues.get(0));
        String key = username.toLowerCase(Locale.ROOT);
        Submission submission = submissionsByUsername.get(key);

        return record.substring(0, numeratorField[FIELD_START])
                + formatScore(submission)
                + record.substring(numeratorField[FIELD_END]);
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
    // submission gets a number; anything else stays blank.
    private String formatScore(Submission submission) {
        boolean notGraded = submission == null
                || submission.getStatus() != SubmissionStatus.GRADED;
        if (notGraded) {
            return NO_SCORE;
        }

        return ScoreFormatter.format(submission.getTotalScore());
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
