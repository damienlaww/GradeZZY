/*
 * File: ChosenSubmissionsStore.java
 *
 * Purpose: Keeps the submission files chosen for each section and
 * assessment in a small file, so Set up can list them again after
 * GradeZZY is closed and opened. Only the paths are kept; the files are
 * checked again when they are listed.
 *
 * Where it fits: SetUpScreen saves to it whenever the chosen files
 * change and reads it once when it is first shown. The file is named by
 * paths.chosen.submissions.file in config.properties.
 */
package gradezzy.service;

import java.io.IOException;
import java.io.Reader;
import java.io.Writer;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.InvalidPathException;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Properties;

import gradezzy.util.FileUtils;
import gradezzy.util.TextUtils;

/**
 * Saves and reads the chosen submission paths of each section and
 * assessment.
 *
 * <p>Tier: COULD.
 */
public class ChosenSubmissionsStore {

    // Comes between the paths of one section; a path holds no new line
    private static final String SEPARATOR = "\n";

    // The comment at the top of the file
    private static final String FILE_COMMENT = "Submission files chosen "
            + "on Set up, by module/section/assessment. Written by "
            + "GradeZZY.";

    // The file the paths are kept in
    private final Path file;

    // The comment written at the top of the file
    private final String comment;

    /**
     * Creates a store that keeps its paths in a file.
     *
     * <p>Tier: COULD.
     *
     * @param file the file to read and write
     */
    public ChosenSubmissionsStore(Path file) {
        this(file, FILE_COMMENT);
    }

    /**
     * Creates a store that keeps paths in a file under another heading,
     * such as the score sheet, tester and starter folders chosen on Set
     * up for each section and assessment.
     *
     * <p>Tier: COULD.
     *
     * @param file the file to read and write
     * @param comment the comment at the top of the file
     */
    public ChosenSubmissionsStore(Path file, String comment) {
        this.file = file;
        this.comment = comment;
    }

    /**
     * Reads the saved paths of every section and assessment.
     *
     * <p>Tier: COULD.
     *
     * @return the paths by section and assessment, such as
     *         CS102/G1/Lab Test 2, with an empty list for one whose files
     *         were cleared; empty when there is no file yet or it cannot
     *         be read
     */
    public Map<String, List<Path>> load() {
        Map<String, List<Path>> chosen = new HashMap<>();
        if (!Files.isRegularFile(file)) {
            return chosen;
        }

        Properties values = null;
        try {
            values = readValues();
        } catch (IOException exception) {
            // An unreadable file lists nothing, as if none were saved
            return chosen;
        }

        for (String section : values.stringPropertyNames()) {
            chosen.put(section, toPaths(values.getProperty(section)));
        }

        return chosen;
    }

    /**
     * Saves the paths of every section and assessment, replacing what was
     * saved. An empty list is kept too, so one cleared on purpose stays
     * cleared.
     *
     * <p>Tier: COULD.
     *
     * @param chosen the paths by section and assessment, such as
     *        CS102/G1/Lab Test 2
     * @return true when the file was written
     */
    public boolean save(Map<String, List<Path>> chosen) {
        Properties values = new Properties();
        for (String section : chosen.keySet()) {
            List<String> texts = new ArrayList<>();
            for (Path path : chosen.get(section)) {
                texts.add(path.toAbsolutePath().toString());
            }
            values.setProperty(section, TextUtils.join(SEPARATOR, texts));
        }

        try {
            writeValues(values);
            return true;
        } catch (IOException exception) {
            return false;
        }
    }

    // Reads the file's sections and paths.
    private Properties readValues() throws IOException {
        Properties values = new Properties();
        Reader reader = null;
        try {
            reader = Files.newBufferedReader(file, StandardCharsets.UTF_8);
            values.load(reader);
        } finally {
            FileUtils.closeIfOpen(reader);
        }

        return values;
    }

    // Writes the sections and paths, creating the folder if needed.
    private void writeValues(Properties values) throws IOException {
        Path folder = file.toAbsolutePath().getParent();
        if (folder != null) {
            Files.createDirectories(folder);
        }

        Writer writer = null;
        try {
            writer = Files.newBufferedWriter(file, StandardCharsets.UTF_8);
            values.store(writer, comment);
        } finally {
            FileUtils.closeIfOpen(writer);
        }
    }

    // Turns the saved text of one section back into paths, leaving out
    // blank lines and any line that is not a path on this computer.
    private List<Path> toPaths(String text) {
        List<Path> paths = new ArrayList<>();
        for (String line : text.split(SEPARATOR)) {
            Path path = toPath(line.trim());
            if (path != null) {
                paths.add(path);
            }
        }

        return paths;
    }

    // Returns the path a saved line names, or null when the line is
    // blank or not a path on this computer, such as one saved on another
    // kind of computer.
    private Path toPath(String line) {
        if (line.isEmpty()) {
            return null;
        }

        try {
            return Path.of(line);
        } catch (InvalidPathException exception) {
            return null;
        }
    }
}
