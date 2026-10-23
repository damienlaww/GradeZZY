/*
 * File: AnomalyReportExporter.java
 *
 * Purpose: Writes every anomaly of a run, with its fix status.
 *
 * Where it fits: The detailed anomaly report listed as a bonus in the brief.
 */
package gradezzy.report;

import java.io.IOException;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.List;

import gradezzy.model.Anomaly;
import gradezzy.model.GradingRun;
import gradezzy.model.Submission;
import gradezzy.util.FileUtils;
import gradezzy.util.TextUtils;

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

    // The header row, in the order of the values in each row
    private static final List<String> HEADER = List.of("Username", "Type",
            "Severity", "Message", "Suggested Action", "Fix Status");

    // CSV files end each row with CRLF so spreadsheets read them cleanly
    private static final String LINE_ENDING = "\r\n";

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
     * <p>One row per anomaly, in submission order. A run without anomalies
     * gives a file with the header only.
     *
     * @param gradingRun the grading run
     * @param outputFile the output file
     * @throws ExportException if the operation fails
     */
    // Generated with Claude Opus 5.5; reviewed by Huxley
    @Override
    public void export(GradingRun gradingRun,
            Path outputFile) throws ExportException {
        if (gradingRun == null || outputFile == null) {
            throw new ExportException(
                    "Nothing to export: the grading run or file is missing.");
        }

        StringBuilder output = new StringBuilder();
        output.append(TextUtils.joinCsvLine(HEADER)).append(LINE_ENDING);
        for (Submission submission : gradingRun.getSubmissions()) {
            for (Anomaly anomaly : submission.getAnomalies()) {
                output.append(TextUtils.joinCsvLine(toRow(anomaly)))
                        .append(LINE_ENDING);
            }
        }

        try {
            FileUtils.writeText(outputFile, output.toString());
        } catch (IOException exception) {
            throw new ExportException("Could not write the anomaly report to "
                    + outputFile + ". Check that the folder exists and is "
                    + "not read-only.", exception);
        }
    }

    // The values of one row, in the order of HEADER
    private List<String> toRow(Anomaly anomaly) {
        List<String> row = new ArrayList<>();
        row.add(textOf(anomaly.getUsername()));
        row.add(textOf(anomaly.getType()));
        row.add(textOf(anomaly.getSeverity()));
        row.add(textOf(anomaly.getMessage()));
        row.add(textOf(anomaly.getSuggestedAction()));
        row.add(textOf(anomaly.getFixStatus()));
        return row;
    }

    // A missing value becomes an empty cell
    private String textOf(Object value) {
        if (value == null) {
            return "";
        }
        return value.toString();
    }
}
