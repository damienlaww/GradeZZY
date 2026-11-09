/*
 * File: CommonMistakesExporter.java
 *
 * Purpose: Writes the common mistakes of a run as CSV: one row per
 * answer group, with the name the marker gave the mistake.
 *
 * Where it fits: The "common mistakes" report the brief lists as a
 * bonus. ApplicationContext registers it with the AnswerGroupService
 * as its source; Export offers it beside the other extra files.
 */
package gradezzy.report;

import java.io.IOException;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.List;

import gradezzy.model.AnswerGroup;
import gradezzy.model.GradingRun;
import gradezzy.model.Section;
import gradezzy.util.FileUtils;
import gradezzy.util.TextUtils;

/**
 * Writes one row per answer group of a run.
 *
 * <p>Tier: COULD.
 */
public class CommonMistakesExporter implements Exporter {

    /** The format name. */
    public static final String FORMAT_NAME = "Common mistakes";

    /** The file extension, without the dot. */
    public static final String FILE_EXTENSION = "csv";

    // The header row, in the order of the values in each row
    private static final String[] HEADER = {"Module", "Section",
        "Question", "Mistake", "Pattern", "Label", "Students",
        "Same code shape", "Checked", "Usernames"};

    // CSV files end each row with CRLF so spreadsheets read them cleanly
    private static final String LINE_ENDING = "\r\n";

    // Separates the usernames in their cell
    private static final String NAME_SEPARATOR = " ";

    // The words of the Checked column
    private static final String CHECKED = "yes";
    private static final String NOT_CHECKED = "no";

    // Where the groups come from
    private final AnswerGroupSource source;

    /**
     * Creates the exporter.
     *
     * <p>Tier: COULD.
     *
     * @param source where the groups and their names come from
     */
    public CommonMistakesExporter(AnswerGroupSource source) {
        this.source = source;
    }

    /**
     * Returns the format name.
     *
     * <p>Tier: COULD.
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
     * <p>Tier: COULD.
     *
     * @return the extension, without the dot
     */
    @Override
    public String getFileExtension() {
        return FILE_EXTENSION;
    }

    /**
     * Writes one row per answer group, in question order and largest
     * first. A run with no groups gives the header only.
     *
     * <p>Tier: COULD.
     *
     * @param gradingRun the grading run
     * @param outputFile the output file
     * @throws ExportException if the operation fails
     */
    @Override
    public void export(GradingRun gradingRun, Path outputFile)
            throws ExportException {
        if (gradingRun == null || outputFile == null) {
            throw new ExportException(
                    "Nothing to export: the grading run or file is missing.");
        }

        List<String> lines = new ArrayList<>();
        lines.add(TextUtils.joinCsvLine(HEADER));
        for (AnswerGroup group : source.listGroups(gradingRun)) {
            lines.add(TextUtils.joinCsvLine(toRow(gradingRun, group)));
        }
        String output = TextUtils.join(LINE_ENDING, lines) + LINE_ENDING;

        try {
            FileUtils.writeText(outputFile, output);
        } catch (IOException exception) {
            throw new ExportException("Could not write the common mistakes "
                    + "to " + outputFile + ". Check that the folder exists "
                    + "and is not read-only.", exception);
        }
    }

    // The values of one row, in the order of HEADER
    private List<String> toRow(GradingRun run, AnswerGroup group) {
        String module = "";
        String section = "";
        Section runSection = run.getSection();
        if (runSection != null) {
            module = runSection.getModuleCode();
            section = runSection.getSectionCode();
        }
        String checked = NOT_CHECKED;
        if (group.isChecked()) {
            checked = CHECKED;
        }
        List<String> row = new ArrayList<>();
        row.add(module);
        row.add(section);
        row.add(group.getQuestionId());
        row.add(group.getTitle());
        row.add(group.getPattern());
        row.add(group.getLabel());
        row.add(String.valueOf(group.getUsernames().size()));
        row.add(String.valueOf(group.countSameShape()));
        row.add(checked);
        row.add(TextUtils.join(NAME_SEPARATOR, group.getUsernames()));
        return row;
    }
}
