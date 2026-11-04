/*
 * File: ExportService.java
 *
 * Purpose: Keeps the available exporters and runs the chosen one, and
 * says which score sheet rows the CSV export leaves blank.
 *
 * Where it fits: Lets the export screen list formats without knowing the
 * classes.
 */
package gradezzy.service;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.nio.file.StandardCopyOption;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;

import gradezzy.config.AppConfig;
import gradezzy.config.ConfigKeys;
import gradezzy.ingest.IngestException;
import gradezzy.ingest.RosterReader;
import gradezzy.model.GradingRun;
import gradezzy.model.Student;
import gradezzy.model.Submission;
import gradezzy.model.SubmissionStatus;
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

    // Joins the counts in the name of an export of some students, such
    // as 3_OF_6
    private static final String OF_SEPARATOR = "_OF_";

    // The registered exporters
    private final List<Exporter> exporters = new ArrayList<>();

    // The application settings, or null if the score sheet cannot be
    // summarised
    private final AppConfig config;

    /**
     * Creates a new export service that cannot summarise a score sheet.
     */
    public ExportService() {
        this(null);
    }

    /**
     * Creates a new export service.
     *
     * @param config the application settings, used to read a run's
     *        score sheet
     */
    public ExportService(AppConfig config) {
        this.config = config;
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
        if (scope == null || scope.isEmpty() || scope.size() >= total) {
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

        try {
            Files.createDirectories(outputDirectory);
        } catch (IOException exception) {
            throw new ExportException("The output folder "
                    + outputDirectory + " could not be created: "
                    + exception.getMessage(), exception);
        }

        Path outputFile = outputDirectory.resolve(
                createFileName(gradingRun, exporter, nameExtra));

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

    /**
     * Says which rows of a run's score sheet the CSV export fills, and
     * why the others stay blank: the student's submission has no mark,
     * or there is none. It also lists submissions that match no row.
     * Rows are matched to submissions by username, ignoring case, as the
     * CSV export does, so the counts agree with the file it writes. Only
     * a GRADED submission fills its row. Nothing is written. The sheet
     * is read with RosterReader, as it was when the run started, so a
     * sheet edited since then to hold a row RosterReader refuses (a
     * blank id or a repeated username) gives an ExportException here,
     * though the CSV export would still write it.
     *
     * <p>Tier: SHOULD.
     *
     * @param gradingRun the grading run
     * @return the summary
     * @throws ExportException if there is no run, or its score sheet
     *         cannot be read
     */
    public ScoreSheetSummary summarizeScoreSheet(GradingRun gradingRun)
            throws ExportException {
        if (gradingRun == null) {
            throw new ExportException("There is no run to export.");
        }
        if (config == null) {
            throw new ExportException("The score sheet cannot be checked "
                    + "without the settings.");
        }

        Path scoreSheetFile = gradingRun.getScoreSheetFile();
        if (scoreSheetFile == null) {
            scoreSheetFile = Paths.get(config.getString(
                    ConfigKeys.SCORE_SHEET_FILE));
        }

        List<Student> roster = null;
        try {
            roster = new RosterReader(config).readRoster(scoreSheetFile);
        } catch (IngestException exception) {
            throw new ExportException(exception.getMessage(), exception);
        }

        Map<String, Submission> submissionsByUsername =
                indexByUsername(gradingRun.getSubmissions());
        ScoreSheetSummary summary = new ScoreSheetSummary();
        List<String> sheetUsernames = new ArrayList<>();
        for (Student student : roster) {
            String key = student.getUsername().toLowerCase(Locale.ROOT);
            sheetUsernames.add(key);
            Submission submission = submissionsByUsername.get(key);
            if (submission == null) {
                summary.getNoSubmission().add(student.getUsername());
            } else if (submission.getStatus() == SubmissionStatus.GRADED) {
                summary.getFilled().add(student.getUsername());
            } else {
                summary.getNotGraded().add(student.getUsername());
            }
        }

        // In upload order, each student once
        List<String> listed = new ArrayList<>();
        for (Submission submission : gradingRun.getSubmissions()) {
            String key = submission.getUsername().toLowerCase(Locale.ROOT);
            if (!sheetUsernames.contains(key) && !listed.contains(key)) {
                listed.add(key);
                summary.getNotOnSheet().add(submission.getUsername());
            }
        }
        return summary;
    }

    // Maps each username, in lower case, to its submission, as the CSV
    // export does: a copy whose upload was refused never replaces one
    // that was loaded.
    private Map<String, Submission> indexByUsername(
            List<Submission> submissions) {
        Map<String, Submission> submissionsByUsername = new HashMap<>();
        for (Submission submission : submissions) {
            String key = submission.getUsername().toLowerCase(Locale.ROOT);
            Submission earlier = submissionsByUsername.get(key);
            if (earlier == null || earlier.getIngestFailure() != null) {
                submissionsByUsername.put(key, submission);
            }
        }
        return submissionsByUsername;
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
