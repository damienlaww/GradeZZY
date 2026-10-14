/*
 * File: StudentReportExporter.java
 *
 * Purpose: Writes the individual report of one student.
 *
 * Where it fits: Used from the student report screen.
 */
package gradezzy.report;

import java.nio.file.Path;

import gradezzy.model.GradingRun;

/**
 * Writes the individual report of one student.
 *
 * <p>Tier: SHOULD. Owner: Huxley.
 */
public class StudentReportExporter implements Exporter {

    /**
     * The format name.
     */
    public static final String FORMAT_NAME = "Student report";

    /**
     * The file extension, without the dot.
     */
    public static final String FILE_EXTENSION = "txt";

    // The student the report is for
    private final String username;

    /**
     * Creates a new student report exporter.
     *
     * @param username the student the report is for
     */
    public StudentReportExporter(String username) {
        this.username = username;
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
     * Writes the individual report of one student.
     *
     * @param gradingRun the grading run
     * @param outputFile the output file
     * @throws ExportException if the operation fails
     */
    @Override
    public void export(GradingRun gradingRun,
            Path outputFile) throws ExportException {
        // TODO(Huxley): implement: a readable report of scores, failed tests
        //     and anomalies for the chosen student
    }
}
