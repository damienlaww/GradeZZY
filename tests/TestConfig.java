/*
 * File: TestConfig.java
 *
 * Purpose: Gives the checks in tests/ the same fixed settings every time,
 * read from tests/test-config.properties (the sample Lab Test).
 *
 * Where it fits: Every test loads its AppConfig here instead of from the
 * live config.properties, so the checks do not depend on the assessment
 * the instructor has chosen in the window.
 */
import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;

import gradezzy.config.AppConfig;
import gradezzy.config.ConfigKeys;

/**
 * Loads the fixed test settings.
 */
public final class TestConfig {

    /**
     * The fixed settings file, relative to the repository root the tests
     * are run from.
     */
    public static final Path FILE = Path.of("tests",
            "test-config.properties");

    // The name of the settings file copied into a test's own folder
    private static final String COPY_NAME = "config.properties";

    /**
     * Not used: every method here is static.
     */
    private TestConfig() {
        // utility class, never instantiated
    }

    /**
     * Loads the fixed settings straight from tests/. Use it only when the
     * check never saves the settings and never writes to the workspace
     * folders they name.
     *
     * @return the fixed settings
     * @throws IOException if the file cannot be read
     */
    public static AppConfig load() throws IOException {
        return new AppConfig(FILE.toString());
    }

    /**
     * Copies the fixed settings into a folder and loads the copy. The
     * working, output, backup and assessments folders, the chosen files
     * file and the database file are moved inside the folder too, so
     * nothing outside it is ever written.
     *
     * @param folder the test's own temporary folder
     * @return the copied settings
     * @throws IOException if the file cannot be copied or read
     */
    public static AppConfig copyInto(Path folder) throws IOException {
        Path copy = folder.resolve(COPY_NAME);
        Files.copy(FILE, copy);
        AppConfig config = new AppConfig(copy.toString());
        Path workspace = folder.resolve("workspace");
        config.setString(ConfigKeys.WORKING_DIR,
                workspace.resolve("working").toString());
        config.setString(ConfigKeys.OUTPUT_DIR,
                workspace.resolve("output").toString());
        config.setString(ConfigKeys.BACKUP_DIR,
                workspace.resolve("backup").toString());
        config.setString(ConfigKeys.ASSESSMENTS_DIR,
                workspace.resolve("assessments").toString());
        config.setString(ConfigKeys.CHOSEN_SUBMISSIONS_FILE,
                workspace.resolve("chosen-submissions.properties")
                        .toString());
        config.setString(ConfigKeys.DATABASE_FILE,
                workspace.resolve("gradezzy.db").toString());
        return config;
    }

    /**
     * Writes the fixed settings to a file, exactly as they are in tests/,
     * for a check that reads or changes the file itself.
     *
     * @param target the file to write
     * @throws IOException if the file cannot be copied
     */
    public static void copyTo(Path target) throws IOException {
        Files.copy(FILE, target);
    }
}
