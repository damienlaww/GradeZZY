/*
 * File: AuditLogExporter.java
 *
 * Purpose: Writes the audit log to a file.
 *
 * Where it fits: Lets the instructor keep a record of every manual decision.
 */
package gradezzy.report;

import java.nio.file.Path;
import java.util.List;

import gradezzy.model.AuditEntry;
import gradezzy.model.GradingRun;

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
     * @param gradingRun the grading run
     * @param outputFile the output file
     * @throws ExportException if the operation fails
     */
    @Override
    public void export(GradingRun gradingRun,
            Path outputFile) throws ExportException {
        // TODO(Huxley): implement: one row per audit entry: time, action,
        //     detail
    }
}
