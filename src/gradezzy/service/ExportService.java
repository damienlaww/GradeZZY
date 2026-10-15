/*
 * File: ExportService.java
 *
 * Purpose: Keeps the available exporters and runs the chosen one.
 *
 * Where it fits: Lets the export screen list formats without knowing the
 * classes.
 */
package gradezzy.service;

import java.nio.file.Path;
import java.util.ArrayList;
import java.util.List;

import gradezzy.model.GradingRun;
import gradezzy.report.ExportException;
import gradezzy.report.Exporter;

/**
 * Keeps the available exporters and runs the chosen one.
 *
 * <p>Tier: MUST. Owner: Jing Yuan.
 */
public class ExportService {

    // The registered exporters
    private final List<Exporter> exporters = new ArrayList<>();

    /**
     * Creates a new export service.
     */
    public ExportService() {
        // nothing to set up yet
    }

    /**
     * Returns the registered exporters.
     *
     * @return the registered exporters
     */
    public List<Exporter> getExporters() {
        return exporters;
    }

    /**
     * Makes one more export format available.
     *
     * @param exporter the exporter
     */
    public void registerExporter(Exporter exporter) {
        exporters.add(exporter);
    }

    /**
     * Finds an exporter by its format name, ignoring case and spaces at
     * both ends, so "csv" finds the exporter named "CSV".
     *
     * @param formatName the format name
     * @return the exporter, or null if the format is unknown or null
     */
    public Exporter findExporter(String formatName) {
        if (formatName == null) {
            return null;
        }

        String wanted = formatName.trim();

        for (Exporter exporter : exporters) {
            if (wanted.equalsIgnoreCase(exporter.getFormatName())) {
                return exporter;
            }
        }

        return null;
    }

    /**
     * Exports a run in the chosen format.
     *
     * @param gradingRun the grading run
     * @param formatName the format name
     * @param outputDirectory the output directory
     * @return the file that was written
     * @throws ExportException if the operation fails
     */
    public Path export(GradingRun gradingRun, String formatName,
            Path outputDirectory) throws ExportException {
        // TODO(Jing Yuan): implement: choosing a file name, calling the
        //     exporter and returning the file written
        return null;
    }
}
