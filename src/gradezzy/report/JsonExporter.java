/*
 * File: JsonExporter.java
 *
 * Purpose: Writes the full results of a run as JSON.
 *
 * Where it fits: Holds what the score sheet cannot: per-question scores, test
 * case results and anomalies.
 */
package gradezzy.report;

import java.nio.file.Path;

import gradezzy.model.GradingRun;

/**
 * Writes the full results of a run as JSON.
 *
 * <p>Tier: SHOULD. Owner: Huxley.
 */
public class JsonExporter implements Exporter {

    /**
     * The format name.
     */
    public static final String FORMAT_NAME = "JSON";

    /**
     * The file extension, without the dot.
     */
    public static final String FILE_EXTENSION = "json";

    // The helper that formats JSON text
    private final JsonWriter jsonWriter;

    /**
     * Creates a new json exporter.
     *
     * @param jsonWriter the helper that formats JSON text
     */
    public JsonExporter(JsonWriter jsonWriter) {
        this.jsonWriter = jsonWriter;
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
     * Writes the full results of a run as JSON.
     *
     * @param gradingRun the grading run
     * @param outputFile the output file
     * @throws ExportException if the operation fails
     */
    @Override
    public void export(GradingRun gradingRun,
            Path outputFile) throws ExportException {
        // TODO(Huxley): implement: building the JSON text with JsonWriter
    }
}
