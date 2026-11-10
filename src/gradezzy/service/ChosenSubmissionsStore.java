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
 * paths.chosen.submissions.file in config.properties. A second store
 * beside it keeps the score sheet, tester and starter folder chosen on
 * Set up. AssessmentLibrary clears an assessment's lines from both when
 * it is deleted, puts them back on Undo and moves them when it is
 * renamed, so a new assessment with the same name starts with none.
 */
package gradezzy.service;

import java.io.IOException;
import java.io.Reader;
import java.io.Writer;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.InvalidPathException;
import java.nio.file.Path;
import java.nio.file.Paths;
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

    /** Where the chosen files are kept when the setting is missing. */
    public static final String DEFAULT_FILE =
            "workspace/chosen-submissions.properties";

    // The file beside it that keeps the score sheet, tester and starter
    // folder chosen for each module, section and assessment
    private static final String CLASS_FILES_NAME =
            "chosen-class-files.properties";

    // The comment at the top of the class files' file
    private static final String CLASS_FILES_COMMENT = "Score sheet, tester "
            + "and starter folder chosen on Set up, by "
            + "module/section/assessment|kind. Written by GradeZZY.";

    // Separates the module, section and assessment in a key such as
    // CS102/G2/Lab Test 2
    private static final String KEY_SEPARATOR = "/";

    // Comes before the kind of class file in a key such as
    // CS102/G2/Lab Test 2|ROSTER
    private static final String KIND_SEPARATOR = "|";

    // A kind of class file after the separator, such as ROSTER
    private static final String KIND_PATTERN = "[A-Z_]+";

    // How many separators come before the assessment in a key: after the
    // module and after the section
    private static final int SEPARATORS_BEFORE_NAME = 2;

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
     * Creates the store of the submission files chosen on Set up.
     *
     * <p>Tier: COULD.
     *
     * @param setting the value of paths.chosen.submissions.file, or ""
     *        for the usual file
     * @return the store
     */
    public static ChosenSubmissionsStore forSubmissions(String setting) {
        return new ChosenSubmissionsStore(chosenFile(setting));
    }

    /**
     * Creates the store of the score sheet, tester and starter folder
     * chosen on Set up. It sits beside the file of chosen submissions.
     *
     * <p>Tier: COULD.
     *
     * @param setting the value of paths.chosen.submissions.file, or ""
     *        for the usual file
     * @return the store
     */
    public static ChosenSubmissionsStore forClassFiles(String setting) {
        Path beside = chosenFile(setting).toAbsolutePath()
                .resolveSibling(CLASS_FILES_NAME);
        return new ChosenSubmissionsStore(beside, CLASS_FILES_COMMENT);
    }

    // Returns the file of chosen submissions a setting names.
    private static Path chosenFile(String setting) {
        String file = DEFAULT_FILE;
        if (setting != null && !setting.trim().isEmpty()) {
            file = setting.trim();
        }
        return Paths.get(file);
    }

    /**
     * Says whether a key belongs to an assessment, such as
     * CS102/G2/Lab Test 2 or CS102/G2/Lab Test 2|ROSTER for Lab Test 2.
     * The name is matched in any case.
     *
     * <p>Tier: COULD.
     *
     * @param key the key of some chosen files
     * @param assessmentName the assessment's name
     * @return true when the files were chosen for that assessment
     */
    public static boolean isKeyOf(String key, String assessmentName) {
        return renameKey(key, assessmentName, assessmentName) != null;
    }

    /**
     * Gives a key of an assessment the assessment's new name, keeping its
     * module, section and kind of file.
     *
     * <p>Tier: COULD.
     *
     * @param key the key, such as CS102/G2/Lab Test 2|ROSTER
     * @param oldName the name the assessment had, matched in any case
     * @param newName its new name
     * @return the new key, such as CS102/G2/Lab Test 3|ROSTER, or null
     *         when the key does not belong to the assessment
     */
    public static String renameKey(String key, String oldName,
            String newName) {
        int nameStart = findNameStart(key);
        if (nameStart < 0) {
            return null;
        }
        String section = key.substring(0, nameStart);
        String rest = key.substring(nameStart);
        if (rest.equalsIgnoreCase(oldName)) {
            return section + newName;
        }
        int kindStart = rest.lastIndexOf(KIND_SEPARATOR);
        if (kindStart < 0) {
            return null;
        }
        String name = rest.substring(0, kindStart);
        String kind = rest.substring(kindStart);
        String kindName = kind.substring(KIND_SEPARATOR.length());
        boolean ofName = name.equalsIgnoreCase(oldName)
                && kindName.matches(KIND_PATTERN);
        if (!ofName) {
            return null;
        }
        return section + newName + kind;
    }

    // Returns where the assessment starts in a key, after the module and
    // the section, or -1 for a key that names no assessment, such as one
    // an older version kept for the section only.
    private static int findNameStart(String key) {
        int start = 0;
        int found = 0;
        while (found < SEPARATORS_BEFORE_NAME && start >= 0) {
            int separator = key.indexOf(KEY_SEPARATOR, start);
            start = -1;
            if (separator >= 0) {
                start = separator + KEY_SEPARATOR.length();
            }
            found++;
        }
        return start;
    }

    /**
     * Takes out the paths chosen for an assessment in every module and
     * section, such as when it is deleted.
     *
     * <p>Tier: COULD.
     *
     * @param assessmentName the assessment's name
     * @return the paths taken out, by key, to put back with putBack
     * @throws IOException if the file could not be written
     */
    public Map<String, List<Path>> removeAssessment(String assessmentName)
            throws IOException {
        Map<String, List<Path>> chosen = load();
        Map<String, List<Path>> removed = new HashMap<>();
        for (String key : chosen.keySet()) {
            if (isKeyOf(key, assessmentName)) {
                removed.put(key, chosen.get(key));
            }
        }
        if (removed.isEmpty()) {
            return removed;
        }

        for (String key : removed.keySet()) {
            chosen.remove(key);
        }
        saveOrFail(chosen);
        return removed;
    }

    /**
     * Puts back paths taken out by removeAssessment, such as on Undo.
     *
     * <p>Tier: COULD.
     *
     * @param removed the paths by key
     * @throws IOException if the file could not be written
     */
    public void putBack(Map<String, List<Path>> removed) throws IOException {
        if (removed.isEmpty()) {
            return;
        }
        Map<String, List<Path>> chosen = load();
        chosen.putAll(removed);
        saveOrFail(chosen);
    }

    /**
     * Moves the paths chosen for an assessment to its new name in every
     * module and section, after it was renamed.
     *
     * <p>Tier: COULD.
     *
     * @param oldName the name it had
     * @param newName its new name
     * @throws IOException if the file could not be written
     */
    public void renameAssessment(String oldName, String newName)
            throws IOException {
        Map<String, List<Path>> chosen = load();
        Map<String, List<Path>> moved = new HashMap<>();
        for (String key : chosen.keySet()) {
            String newKey = renameKey(key, oldName, newName);
            if (newKey != null) {
                moved.put(key, chosen.get(key));
            }
        }
        if (moved.isEmpty()) {
            return;
        }

        for (String key : moved.keySet()) {
            chosen.remove(key);
        }
        for (String key : moved.keySet()) {
            chosen.put(renameKey(key, oldName, newName), moved.get(key));
        }
        saveOrFail(chosen);
    }

    // Saves the paths, or says the file could not be written.
    private void saveOrFail(Map<String, List<Path>> chosen)
            throws IOException {
        if (!save(chosen)) {
            throw new IOException(file + " could not be written.");
        }
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
