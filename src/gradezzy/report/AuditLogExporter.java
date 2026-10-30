/*
 * File: AuditLogExporter.java
 *
 * Purpose: Writes the audit log to a file.
 *
 * Where it fits: Lets the instructor keep a record of every manual decision.
 */
package gradezzy.report;

import java.io.IOException;
import java.nio.file.Path;
import java.time.LocalDateTime;
import java.util.ArrayList;
import java.util.List;

import gradezzy.model.AuditEntry;
import gradezzy.model.GradingRun;
import gradezzy.util.FileUtils;
import gradezzy.util.TextUtils;

/**
 * Writes the audit log to a file.
 *
 * <p>Tier: SHOULD. Owner: Huxley.
 */
public class AuditLogExporter implements Exporter {

    /**
     * The format name.
     */
    public static final String FORMAT_NAME = "Audit log";

    /**
     * The file extension, without the dot.
     */
    public static final String FILE_EXTENSION = "csv";

    // The header row, in the order of the values in each row
    private static final String[] HEADER = {"Time", "Action", "Detail"};

    // CSV files end each row with CRLF so spreadsheets read them cleanly
    private static final String LINE_ENDING = "\r\n";

    // Date, then the time to the second, so every row has the same width
    private static final String TIME_FORMAT = "%sT%02d:%02d:%02d";

    // The entries to write
    private final List<AuditEntry> entries;

    /**
     * Creates a new audit log exporter.
     *
     * @param entries the entries to write
     */
    public AuditLogExporter(List<AuditEntry> entries) {
        this.entries = entries;
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
     * Writes the audit log to a file.
     *
     * <p>One row per entry, in the order given. The audit log covers every
     * run, so the grading run is not used and may be null.
     *
     * @param gradingRun the grading run
     * @param outputFile the output file
     * @throws ExportException if the operation fails
     */
    @Override
    public void export(GradingRun gradingRun,
            Path outputFile) throws ExportException {
        if (outputFile == null) {
            throw new ExportException("Nothing to export: the file is "
                    + "missing.");
        }

        // Every row, the header included, ends with LINE_ENDING
        List<String> lines = new ArrayList<>();
        lines.add(TextUtils.joinCsvLine(HEADER));
        if (entries != null) {
            for (AuditEntry entry : entries) {
                lines.add(TextUtils.joinCsvLine(toRow(entry)));
            }
        }
        String output = TextUtils.join(LINE_ENDING, lines) + LINE_ENDING;

        try {
            FileUtils.writeText(outputFile, output);
        } catch (IOException exception) {
            throw new ExportException("Could not write the audit log to "
                    + outputFile + ". Check that the folder exists and is "
                    + "not read-only.", exception);
        }
    }

    // The values of one row, in the order of HEADER
    private List<String> toRow(AuditEntry entry) {
        List<String> row = new ArrayList<>();
        row.add(formatTime(entry.getTimestamp()));
        row.add(textOf(entry.getAction()));
        row.add(textOf(entry.getDetail()));
        return row;
    }

    // Writes the time to the second, for example 2026-10-21T15:42:50.
    // LocalDateTime.toString would drop the seconds when they are zero.
    private String formatTime(LocalDateTime timestamp) {
        if (timestamp == null) {
            return "";
        }
        return String.format(TIME_FORMAT, timestamp.toLocalDate(),
                timestamp.getHour(), timestamp.getMinute(),
                timestamp.getSecond());
    }

    // A missing value becomes an empty cell
    private String textOf(String value) {
        if (value == null) {
            return "";
        }
        return value;
    }
}
