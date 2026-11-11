/*
 * File: AuditLogExporter.java
 *
 * Purpose: Writes the audit log of one run to a file.
 *
 * Where it fits: Lets the instructor keep a record of every manual decision
 * made on a run, beside the run's score sheet. RunAuditFilter decides
 * which lines of the shared audit log belong to the run.
 */
package gradezzy.report;

import java.io.IOException;
import java.nio.file.Path;
import java.time.LocalDateTime;
import java.util.ArrayList;
import java.util.List;
import java.util.Locale;

import gradezzy.model.AuditEntry;
import gradezzy.model.GradingRun;
import gradezzy.persistence.PersistenceException;
import gradezzy.persistence.ResultRepository;
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

    // The entries to write, or null to read them from the repository
    private final List<AuditEntry> entries;

    // Where the audit log is saved, or null when entries were given
    private final ResultRepository repository;

    /**
     * Creates a new audit log exporter that writes the given entries,
     * all of them, whatever run is exported.
     *
     * @param entries the entries to write
     */
    public AuditLogExporter(List<AuditEntry> entries) {
        this.entries = entries;
        this.repository = null;
    }

    /**
     * Creates a new audit log exporter that reads the saved audit log
     * each time it exports, so the file holds every entry of the run up
     * to that moment. This is the one ApplicationContext registers.
     *
     * @param repository where the audit log is saved
     */
    public AuditLogExporter(ResultRepository repository) {
        this.entries = null;
        this.repository = repository;
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
     * Writes the audit log of a run to a file.
     *
     * <p>One row per entry, oldest first. Read from the saved log, only
     * the entries that belong to the run are written, by the rules of
     * RunAuditFilter; a line about no run, such as a change to the
     * settings, is left out. The run may be null only when the entries
     * were given to the constructor.
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
        List<AuditEntry> entriesToWrite = chooseEntries(gradingRun);
        List<String> lines = new ArrayList<>();
        lines.add(TextUtils.joinCsvLine(HEADER));
        if (entriesToWrite != null) {
            for (AuditEntry entry : entriesToWrite) {
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

    // The entries given to the constructor, or else the lines of the
    // audit log as it is saved now that belong to the run
    private List<AuditEntry> chooseEntries(GradingRun gradingRun)
            throws ExportException {
        if (repository == null) {
            return entries;
        }
        if (gradingRun == null) {
            throw new ExportException("Nothing to export: the grading run "
                    + "is missing.");
        }

        List<AuditEntry> saved = null;
        try {
            saved = repository.listAudit();
        } catch (PersistenceException exception) {
            throw new ExportException("The audit log could not be read: "
                    + exception.getMessage(), exception);
        }
        RunAuditFilter filter = new RunAuditFilter(gradingRun,
                listSavedRuns());
        return filter.keep(saved);
    }

    // Every saved run without its submissions, so the filter knows when
    // the next run of the section started. If they cannot be read, the
    // filter knows only the exported run, so it may keep a line of a
    // later run of the same section that names a student or the section.
    private List<GradingRun> listSavedRuns() {
        try {
            return repository.listRunHeaders();
        } catch (PersistenceException exception) {
            return new ArrayList<>();
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
        return String.format(Locale.ROOT, TIME_FORMAT,
                timestamp.toLocalDate(),
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
