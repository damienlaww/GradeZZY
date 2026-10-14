/*
 * File: Exporter.java
 *
 * Purpose: One output format for grading results.
 *
 * Where it fits: ExportService keeps a list of these, so a new format is one
 * new class.
 */
package gradezzy.report;

import java.nio.file.Path;

import gradezzy.model.GradingRun;

/**
 * One output format for grading results.
 *
 * <p>Tier: MUST. Owner: Huxley.
 */
public interface Exporter {

    /**
     * Returns the name of the format, for example CSV.
     *
     * @return the format name
     */
    String getFormatName();

    /**
     * Returns the file extension without the dot.
     *
     * @return the extension, for example csv
     */
    String getFileExtension();

    /**
     * Writes the results of a run to a file.
     *
     * @param gradingRun the grading run
     * @param outputFile the output file
     * @throws ExportException if the operation fails
     */
    void export(GradingRun gradingRun, Path outputFile) throws ExportException;
}
