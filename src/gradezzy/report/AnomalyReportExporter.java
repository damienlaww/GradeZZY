/*
 * File: AnomalyReportExporter.java
 *
 * Purpose: Writes every anomaly of a run, with its fix status.
 *
 * Where it fits: The detailed anomaly report listed as a bonus in the brief.
 */
package gradezzy.report;

import java.nio.file.Path;

import gradezzy.model.GradingRun;

/**
 * Writes every anomaly of a run, with its fix status.
 *
 * <p>Tier: SHOULD. Owner: Huxley.
 */
public class AnomalyReportExporter implements Exporter {

    /**
     * The format name.
     */
    public static final String FORMAT_NAME = "Anomaly report";

    /**
     * The file extension, without the dot.
     */
    public static final String FILE_EXTENSION = "csv";

    /**
     * Creates a new anomaly report exporter.
     */
    public AnomalyReportExporter() {
        // nothing to set up yet
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
     * Writes every anomaly of a run, with its fix status.
     *
     * @param gradingRun the grading run
     * @param outputFile the output file
     * @throws ExportException if the operation fails
     */
    @Override
    public void export(GradingRun gradingRun,
            Path outputFile) throws ExportException {
        // TODO(Huxley): implement: one row per anomaly: username, type,
        //     severity, message, fix status
    }
}
