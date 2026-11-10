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
import java.nio.file.FileSystemException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.StandardCopyOption;
import java.util.ArrayList;
import java.util.List;

import gradezzy.model.GradingRun;
import gradezzy.model.Submission;
import gradezzy.report.ExportException;
import gradezzy.report.Exporter;
import gradezzy.util.TextUtils;

/**
 * Keeps the available exporters and runs the chosen one.
 *
 * <p>Tier: MUST. Owner: Jing Yuan.
 */
public class ExportService {

    // Joins the run id, module and section in an export file name
    private static final String NAME_SEPARATOR = "-";

    // Stands in for a character that is not safe in a file name; it is
    // itself safe and is kept
    private static final char SAFE_CHARACTER = '_';

    // A dash is also safe in a file name and is kept
    private static final char DASH = '-';

    // Added to the file name while an export is being written, so a
    // failed export never replaces the earlier file
    private static final String TEMP_SUFFIX = ".part";

    // Joins the counts in the name of an export of some students, such
    // as 3_OF_6
    private static final String OF_SEPARATOR = "_OF_";

    // Comes between a file name and its extension
    private static final String EXTENSION_DOT = ".";

    // Comes between two format names in an error message
    private static final String LIST_SEPARATOR = ", ";

    // The registered exporters
    private final List<Exporter> exporters = new ArrayList<>();

    /**
     * Creates a new export service with no exporters yet.
     *
     * <p>Tier: MUST.
     */
    public ExportService() {
        // the exporters are added with registerExporter
    }

    /**
     * Makes one more export format available.
     *
     * <p>Tier: MUST.
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
     * <p>Tier: MUST.
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
        return export(gradingRun, formatName, outputDirectory, "");
    }

    /**
     * Exports only some students of a run. The CSV keeps every row of
     * the score sheet but fills the mark only for these students; the
     * JSON lists only them. The file name gets the counts, for example
     * RUN-1792545961628-CS102-G2-3_OF_6-JSON.json, so it never replaces
     * the export of the whole run. When the students are the whole run,
     * or none are given, this is the same as exporting the whole run.
     *
     * <p>Tier: COULD.
     *
     * @param gradingRun the grading run
     * @param scope the students to export, all from this run
     * @param formatName the format name, for example CSV
     * @param outputDirectory the folder to write to, created if missing
     * @return the file that was written
     * @throws ExportException if there is no run, the format is unknown,
     *         the folder cannot be created or the exporter wrote no file
     */
    public Path export(GradingRun gradingRun, List<Submission> scope,
            String formatName, Path outputDirectory)
            throws ExportException {
        if (gradingRun == null) {
            throw new ExportException("There is no run to export.");
        }

        int total = gradingRun.getSubmissions().size();
        boolean wholeRun = scope == null || scope.isEmpty()
                || scope.size() >= total;
        if (wholeRun) {
            return export(gradingRun, formatName, outputDirectory);
        }

        GradingRun part = new GradingRun(gradingRun.getId(),
                gradingRun.getAssessment(), gradingRun.getSection(),
                gradingRun.getStartedAt());
        part.setScoreSheetFile(gradingRun.getScoreSheetFile());
        part.setStarterDirectory(gradingRun.getStarterDirectory());
        part.setTestersDirectory(gradingRun.getTestersDirectory());
        for (Submission submission : gradingRun.getSubmissions()) {
            if (scope.contains(submission)) {
                part.addSubmission(submission);
            }
        }

        String counts = part.getSubmissions().size() + OF_SEPARATOR
                + total;
        return export(part, formatName, outputDirectory, counts);
    }

    // Exports a run; nameExtra, if not empty, goes into the file name
    // before the format.
    private Path export(GradingRun gradingRun, String formatName,
            Path outputDirectory, String nameExtra) throws ExportException {
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

        createFolder(outputDirectory);

        String fileName = createFileName(gradingRun, exporter, nameExtra);
        Path outputFile = outputDirectory.resolve(fileName);
        writeThenMove(gradingRun, exporter, outputFile);

        return outputFile;
    }

    // Creates the output folder if it is missing.
    private void createFolder(Path outputDirectory) throws ExportException {
        try {
            Files.createDirectories(outputDirectory);
        } catch (IOException exception) {
            throw new ExportException("The output folder "
                    + outputDirectory + " could not be created: "
                    + exception.getMessage(), exception);
        }
    }

    // Has the exporter write a temporary file beside the output file,
    // then moves it into place, so a failed export keeps the earlier
    // file.
    private void writeThenMove(GradingRun gradingRun, Exporter exporter,
            Path outputFile) throws ExportException {
        String tempName = outputFile.getFileName() + TEMP_SUFFIX;
        Path tempFile = outputFile.resolveSibling(tempName);

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
        } catch (FileSystemException exception) {
            // On Windows, a file open in Excel is locked
            throw new ExportException("The earlier export " + outputFile
                    + " could not be replaced. If it is open, such as in "
                    + "Excel, close it and export again.", exception);
        } catch (IOException exception) {
            throw new ExportException("The earlier export " + outputFile
                    + " could not be replaced: " + exception.getMessage(),
                    exception);
        } finally {
            // Only does something when writing or moving failed
            deleteQuietly(tempFile);
        }
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
    private String createFileName(GradingRun gradingRun, Exporter exporter,
            String nameExtra) {
        String name = gradingRun.getId() + NAME_SEPARATOR
                + gradingRun.getSection().getModuleCode() + NAME_SEPARATOR
                + gradingRun.getSection().getSectionCode() + NAME_SEPARATOR;
        if (!nameExtra.isEmpty()) {
            name = name + nameExtra + NAME_SEPARATOR;
        }
        name = name + exporter.getFormatName();

        return makeSafe(name) + EXTENSION_DOT
                + makeSafe(exporter.getFileExtension());
    }

    // Keeps letters, digits, dashes and underscores, so a code
    // from the settings cannot add a folder to the path.
    private String makeSafe(String text) {
        String safe = "";

        for (int index = 0; index < text.length(); index++) {
            char current = text.charAt(index);

            boolean isSafe = Character.isLetterOrDigit(current)
                    || current == DASH || current == SAFE_CHARACTER;
            if (isSafe) {
                safe += current;
            } else {
                safe += SAFE_CHARACTER;
            }
        }

        return safe;
    }

    // Lists the registered format names, for an error message.
    private String listFormatNames() {
        List<String> names = new ArrayList<>();

        for (Exporter exporter : exporters) {
            names.add(exporter.getFormatName());
        }

        return TextUtils.join(LIST_SEPARATOR, names);
    }
}
