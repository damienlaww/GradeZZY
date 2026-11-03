/*
 * File: ExportService.java
 *
 * Purpose: Keeps the available exporters and runs the chosen one.
 *
 * Where it fits: Lets the export screen list formats without knowing the
 * classes.
 */
package gradezzy.service;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.StandardCopyOption;
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

    // Joins the run id, module and section in an export file name
    private static final String NAME_SEPARATOR = "-";

    // Stands in for a character that is not safe in a file name
    private static final char SAFE_CHARACTER = '_';

    // Added to the file name while an export is being written, so a
    // failed export never replaces the earlier file
    private static final String TEMP_SUFFIX = ".part";

    // The registered exporters
    private final List<Exporter> exporters = new ArrayList<>();

    /**
     * Creates a new export service.
     */
    public ExportService() {
        // nothing to set up yet
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
     * both ends, so "csv score sheet" finds the exporter named "CSV score
     * sheet". When no name matches, the first exporter registered with
     * that file extension is used, so the screens' "csv" finds the score
     * sheet exporter, which ApplicationContext registers first.
     *
     * @param formatName the format name or file extension
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

        for (Exporter exporter : exporters) {
            if (wanted.equalsIgnoreCase(exporter.getFileExtension())) {
                return exporter;
            }
        }

        return null;
    }

    /**
     * Exports a run in the chosen format. The file is named after the
     * run, module, section and format, for example
     * RUN-1792545961628-CS102-G2-JSON.json, so exports of different runs
     * or formats never overwrite each other. Exporting the same run in
     * the same format again replaces the earlier file, but only once the
     * new file is complete: the exporter writes to a temporary file that
     * is then moved into place, so an export that fails keeps the
     * earlier file.
     *
     * <p>Tier: MUST.
     *
     * @param gradingRun the grading run
     * @param formatName the format name, for example CSV
     * @param outputDirectory the folder to write to, created if missing
     * @return the file that was written
     * @throws ExportException if there is no run, the format is unknown,
     *         the folder cannot be created or the exporter wrote no file
     */
    public Path export(GradingRun gradingRun, String formatName,
            Path outputDirectory) throws ExportException {
        if (gradingRun == null) {
            throw new ExportException("There is no run to export.");
        }

        Exporter exporter = findExporter(formatName);

        if (exporter == null) {
            throw new ExportException("Unknown export format '"
                    + formatName + "'. Choose one of: "
                    + listFormatNames() + ".");
        }

        if (outputDirectory == null) {
            throw new ExportException("No output folder was given.");
        }

        try {
            Files.createDirectories(outputDirectory);
        } catch (IOException exception) {
            throw new ExportException("The output folder "
                    + outputDirectory + " could not be created: "
                    + exception.getMessage(), exception);
        }

        Path outputFile = outputDirectory.resolve(
                createFileName(gradingRun, exporter));

        Path tempFile = outputFile.resolveSibling(outputFile.getFileName()
                + TEMP_SUFFIX);

        try {
            // A file left by an earlier failed export would hide an
            // exporter that writes nothing.
            deleteQuietly(tempFile);
            exporter.export(gradingRun, tempFile);

            // A screen would otherwise report a file that is not there.
            if (!Files.isRegularFile(tempFile)) {
                throw new ExportException("The " + exporter.getFormatName()
                        + " exporter did not write " + outputFile + ".");
            }

            Files.move(tempFile, outputFile,
                    StandardCopyOption.REPLACE_EXISTING);
        } catch (IOException exception) {
            throw new ExportException("The earlier export " + outputFile
                    + " could not be replaced: " + exception.getMessage(),
                    exception);
        } finally {
            // Only does something when writing or moving failed
            deleteQuietly(tempFile);
        }

        return outputFile;
    }

    // Deletes a file if it is there. A file that cannot be deleted is
    // left; it is only ever a half-written export.
    private void deleteQuietly(Path file) {
        try {
            Files.deleteIfExists(file);
        } catch (IOException exception) {
            // Nothing else to do; see the comment above
        }
    }

    // Builds the file name from the run id, module, section, format
    // name and the exporter's extension. The format name is needed
    // because two formats can share an extension.
    private String createFileName(GradingRun gradingRun, Exporter exporter) {
        String name = gradingRun.getId() + NAME_SEPARATOR
                + gradingRun.getSection().getModuleCode() + NAME_SEPARATOR
                + gradingRun.getSection().getSectionCode() + NAME_SEPARATOR
                + exporter.getFormatName();

        return makeSafe(name) + "." + makeSafe(exporter.getFileExtension());
    }

    // Keeps letters, digits, dashes and underscores, so a code
    // from the settings cannot add a folder to the path.
    private String makeSafe(String text) {
        String safe = "";

        for (int index = 0; index < text.length(); index++) {
            char current = text.charAt(index);

            if (Character.isLetterOrDigit(current) || current == '-'
                    || current == '_') {
                safe += current;
            } else {
                safe += SAFE_CHARACTER;
            }
        }

        return safe;
    }

    // Lists the registered format names, for an error message.
    private String listFormatNames() {
        String names = "";

        for (Exporter exporter : exporters) {
            if (names.length() > 0) {
                names += ", ";
            }
            names += exporter.getFormatName();
        }

        return names;
    }
}
