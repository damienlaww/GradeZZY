/*
 * File: CsvScoreSheetExporter.java
 *
 * Purpose: Writes the provided score sheet with the final grade filled in.
 *
 * Where it fits: The export required by the brief. Columns, the id prefix, the
 * end marker and the line ending stay exactly as provided.
 */
package gradezzy.report;

import java.io.IOException;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

import gradezzy.config.AppConfig;
import gradezzy.config.ConfigKeys;
import gradezzy.model.GradingRun;
import gradezzy.model.Submission;
import gradezzy.model.SubmissionStatus;
import gradezzy.util.FileUtils;
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

    private static final String CRLF = "\r\n";

    private static final String LF = "\n";

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
     * <p>Every row and value of the template is kept as it is, apart from
     * the numerator, which receives the student's total. A student without
     * a graded submission is left blank so the instructor notices.
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
        List<String> lines = readTemplateLines(template);
        if (lines.isEmpty()) {
            throw new ExportException("The score sheet template "
                    + template + " is empty.");
        }

        // Find the two columns by name so a reordered sheet still works
        List<String> header = splitLine(lines.get(0), 1);
        int usernameIndex = findColumn(header, USERNAME_COLUMN);
        int numeratorIndex = findColumn(header, NUMERATOR_COLUMN);

        Map<String, Submission> submissionsByUsername =
                indexByUsername(gradingRun.getSubmissions());
        String lineEnding = getLineEnding();
        StringBuilder output = new StringBuilder();
        output.append(lines.get(0)).append(lineEnding);

        for (int index = 1; index < lines.size(); index++) {
            String line = lines.get(index);
            String filledLine = fillRow(line, index + 1, usernameIndex,
                    numeratorIndex, submissionsByUsername);
            output.append(filledLine).append(lineEnding);
        }

        try {
            FileUtils.writeText(outputFile, output.toString());
        } catch (IOException exception) {
            throw new ExportException("Could not write the score sheet to "
                    + outputFile + ". Check that the folder exists and is "
                    + "not read-only.", exception);
        }
    }

    // The sheet the batch was graded against, so the export lists the
    // same students. A run saved before the sheet was recorded uses the
    // sheet from the settings.
    private Path chooseTemplate(GradingRun gradingRun) {
        if (gradingRun.getScoreSheetFile() != null) {
            return gradingRun.getScoreSheetFile();
        }
        return templateScoreSheet;
    }

    // Reads the template and returns its lines without line endings.
    // The final line ending does not create an extra empty line.
    private List<String> readTemplateLines(Path template)
            throws ExportException {
        String text = "";
        try {
            text = FileUtils.readText(template);
        } catch (IOException exception) {
            throw new ExportException("Could not read the score sheet "
                    + template + " that this batch was graded against. "
                    + "Put the file back, or grade the batch again with "
                    + "a score sheet that exists.", exception);
        }

        String normalised = TextUtils.normaliseLineEndings(text);
        List<String> lines = new ArrayList<>();
        int lineStart = 0;
        int lineEnd = normalised.indexOf(LF);
        while (lineEnd >= 0) {
            lines.add(normalised.substring(lineStart, lineEnd));
            lineStart = lineEnd + LF.length();
            lineEnd = normalised.indexOf(LF, lineStart);
        }

        // Keep a last line that has no line ending after it
        if (lineStart < normalised.length()) {
            lines.add(normalised.substring(lineStart));
        }
        return lines;
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

    // Maps each username to its submission for a quick lookup per row
    private Map<String, Submission> indexByUsername(
            List<Submission> submissions) {
        Map<String, Submission> submissionsByUsername = new HashMap<>();
        for (Submission submission : submissions) {
            submissionsByUsername.put(submission.getUsername(), submission);
        }
        return submissionsByUsername;
    }

    // Returns the row with the numerator filled in. A blank line, or a row
    // too short to hold both columns, is kept exactly as it was.
    private String fillRow(String line, int lineNumber, int usernameIndex,
            int numeratorIndex, Map<String, Submission> submissionsByUsername)
            throws ExportException {
        if (TextUtils.isBlank(line)) {
            return line;
        }

        List<String> values = splitLine(line, lineNumber);
        if (values.size() <= Math.max(usernameIndex, numeratorIndex)) {
            return line;
        }

        String username = removeIdPrefix(values.get(usernameIndex));
        Submission submission = submissionsByUsername.get(username);
        values.set(numeratorIndex, formatScore(submission));
        return TextUtils.joinCsvLine(values);
    }

    // Splits one line, naming the line if its quotes are broken
    private List<String> splitLine(String line,
            int lineNumber) throws ExportException {
        try {
            return TextUtils.splitCsvLine(line);
        } catch (IllegalArgumentException exception) {
            throw new ExportException("Line " + lineNumber + " of the score "
                    + "sheet template is not valid CSV: "
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
        if (submission == null
                || submission.getStatus() != SubmissionStatus.GRADED) {
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
