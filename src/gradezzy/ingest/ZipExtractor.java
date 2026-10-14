/*
 * File: ZipExtractor.java
 *
 * Purpose: Unzips one submission into a folder.
 *
 * Where it fits: Used when a working copy is created. Decompress here means
 * unzip only; nothing is decompiled.
 */
package gradezzy.ingest;

import java.nio.file.Path;

import gradezzy.config.AppConfig;

/**
 * Unzips one submission into a folder.
 *
 * <p>Tier: MUST. Owner: Seth.
 */
public class ZipExtractor {

    // The application settings
    private final AppConfig config;

    /**
     * Creates a new zip extractor.
     *
     * @param config the application settings
     */
    public ZipExtractor(AppConfig config) {
        this.config = config;
    }

    /**
     * Unzips a file into the target folder.
     *
     * @param zipFile the zip file
     * @param targetDirectory the target directory
     * @return the folder that now holds the unzipped files
     * @throws IngestException if the operation fails
     */
    public Path extract(Path zipFile,
            Path targetDirectory) throws IngestException {
        // TODO(Seth): implement: unzipping with java.util.zip, enforcing the
        //     size and entry limits from config
        return null;
    }

    /**
     * Checks that a zip entry stays inside the target folder.
     *
     * @param entryName the entry name
     * @param targetDirectory the target directory
     * @return true if the entry is safe to write
     */
    public boolean isSafeEntry(String entryName, Path targetDirectory) {
        // TODO(Seth): implement: a check that rejects entry names such as
        //     ../../x which would write outside the target folder
        return false;
    }
}
