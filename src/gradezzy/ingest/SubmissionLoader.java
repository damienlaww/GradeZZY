/*
 * File: SubmissionLoader.java
 *
 * Purpose: Turns the files and folders the instructor uploaded into Submission
 * objects.
 *
 * Where it fits: The first step of batch upload: it accepts many zips, a folder
 * of zips or one zip of zips, for one section.
 */
package gradezzy.ingest;

import java.nio.file.Path;
import java.util.ArrayList;
import java.util.List;

import gradezzy.config.AppConfig;
import gradezzy.model.Section;
import gradezzy.model.Submission;

/**
 * Turns the files and folders the instructor uploaded into Submission objects.
 *
 * <p>Tier: MUST. Owner: Damien.
 */
public class SubmissionLoader {

    // The application settings
    private final AppConfig config;

    /**
     * Creates a new submission loader.
     *
     * @param config the application settings
     */
    public SubmissionLoader(AppConfig config) {
        this.config = config;
    }

    /**
     * Creates one Submission per uploaded student archive.
     *
     * @param inputPaths the input paths
     * @param section the section
     * @return the submissions found, in file name order
     * @throws IngestException if the operation fails
     */
    public List<Submission> loadSubmissions(List<Path> inputPaths,
            Section section) throws IngestException {
        // TODO(Damien): implement: walking the input paths, creating a
        //     Submission for each zip or folder, and skipping ignored file
        //     names
        return new ArrayList<>();
    }

    /**
     * Works out the username from a zip file name by removing the configured
     * prefix and the .zip ending.
     *
     * @param zipFileName the zip file name
     * @return the username, or an empty string if none is found
     */
    public String extractUsername(String zipFileName) {
        // TODO(Damien): implement: prefix removal using ZIP_NAME_PREFIX_PATTERN
        return "";
    }
}
