/*
 * File: CsvScoreSheetExporter.java
 *
 * Purpose: Writes the provided score sheet with the final grade filled in.
 *
 * Where it fits: The export required by the brief. Columns, the id prefix, the
 * end marker and the line ending stay exactly as provided.
 */
package gradezzy.report;

import java.nio.file.Path;

import gradezzy.config.AppConfig;
import gradezzy.model.GradingRun;

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

    // The application settings
    private final AppConfig config;

    // The provided score sheet used as the template
    private final Path templateScoreSheet;

    /**
     * Creates a new csv score sheet exporter.
     *
     * @param config the application settings
     * @param templateScoreSheet the provided score sheet used as the template
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
     * @param gradingRun the grading run
     * @param outputFile the output file
     * @throws ExportException if the operation fails
     */
    @Override
    public void export(GradingRun gradingRun,
            Path outputFile) throws ExportException {
        // TODO(Huxley): implement: copying the template rows and filling
        //     Calculated Final Grade Numerator with each student's total
    }
}
