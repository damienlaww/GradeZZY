/*
 * File: AssessmentLibrary.java
 *
 * Purpose: Keeps the assessments the instructor has saved, one small file
 * each, and switches GradeZZY to one of them.
 *
 * Where it fits: The window lists, creates, edits and chooses assessments
 * through it. Choosing one copies its settings into config.properties
 * with SettingsService, because every part of grading reads the questions,
 * tester folder and starter folder from there.
 */
package gradezzy.service;

import java.io.IOException;
import java.io.Reader;
import java.io.Writer;
import java.nio.charset.StandardCharsets;
import java.nio.file.DirectoryStream;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Properties;

import gradezzy.config.AppConfig;
import gradezzy.config.ConfigKeys;
import gradezzy.model.Question;
import gradezzy.model.SavedAssessment;
import gradezzy.util.FileUtils;
import gradezzy.util.TextUtils;

/**
 * Keeps the saved assessments and switches GradeZZY to one of them.
 *
 * <p>Tier: SHOULD. Owner: Damien.
 */
public class AssessmentLibrary {

    // The ending of every saved assessment file
    private static final String FILE_EXTENSION = ".properties";

    // The folder used when the setting is missing from an older file
    private static final String DEFAULT_FOLDER = "workspace/assessments";

    // The first line written in every saved assessment file
    private static final String FILE_COMMENT = "GradeZZY saved assessment";

    // The separator between the items of a list setting
    private static final String LIST_SEPARATOR = ",";

    // A question id must be letters and digits, because it becomes part
    // of the setting names, such as assessment.question.Q1a.folder
    private static final String QUESTION_ID_PATTERN = "[A-Za-z0-9]+";

    // Characters that may not appear in a folder or file name
    private static final String PATH_CHARACTERS = ".*[/\\\\:*?\"<>|].*";

    // Every answer file is a Java source file
    private static final String JAVA_EXTENSION = ".java";

    // The longest assessment name, so it fits on a card
    private static final int MAX_NAME_LENGTH = 60;

    // The highest mark a single question may carry
    private static final double MAX_QUESTION_MARK = 1000.0;

    // Matches each run of characters that cannot be in a file name
    private static final String NOT_FILE_NAME = "[^a-z0-9]+";

    // Replaces such a run in a file name
    private static final String FILE_NAME_JOIN = "-";

    // The file name used when a name has no letter or digit
    private static final String UNNAMED_FILE = "assessment";

    // The first number added to a file name that is already used
    private static final int FIRST_EXTRA_NUMBER = 2;

    // The application settings
    private final AppConfig config;

    // Checks and saves settings in config.properties
    private final SettingsService settings;

    /**
     * Creates the library.
     *
     * @param config the application settings
     * @param settings the service that checks and saves settings
     */
    public AssessmentLibrary(AppConfig config, SettingsService settings) {
        this.config = config;
        this.settings = settings;
    }

    /**
     * Lists the saved assessments, sorted by name. The assessment in use
     * is always listed: when it has no file yet, as on the first start,
     * one is saved from config.properties. A file that cannot be read is
     * left out.
     *
     * @return the assessments
     * @throws IOException if the folder cannot be read or written
     */
    public List<SavedAssessment> list() throws IOException {
        List<SavedAssessment> assessments = readAll();
        if (findIn(assessments, getActiveName()) == null) {
            SavedAssessment current = readCurrent();
            write(current, fileFor(current.getName()));
            assessments.add(current);
        }
        sortByName(assessments);
        return assessments;
    }

    /**
     * Returns the name of the assessment in use.
     *
     * @return the name in assessment.name
     */
    public String getActiveName() {
        return config.getString(ConfigKeys.ASSESSMENT_NAME);
    }

    /**
     * Reads the assessment in use from config.properties.
     *
     * @return the assessment in use
     */
    public SavedAssessment readCurrent() {
        SavedAssessment current = new SavedAssessment();
        current.setName(getActiveName());
        for (String id : config.getList(ConfigKeys.QUESTION_IDS)) {
            String prefix = ConfigKeys.QUESTION_PREFIX + id;
            current.getQuestions().add(new Question(id,
                    config.getString(prefix
                            + ConfigKeys.QUESTION_FOLDER_SUFFIX),
                    config.getString(prefix
                            + ConfigKeys.QUESTION_FILE_SUFFIX),
                    config.getDouble(prefix
                            + ConfigKeys.QUESTION_MAX_SCORE_SUFFIX)));
        }
        current.setTestersDirectory(config.getString(
                ConfigKeys.TESTERS_DIR));
        current.setStarterDirectory(config.getString(
                ConfigKeys.STARTER_DIR));
        current.getProvidedClassFiles().addAll(config.getList(
                ConfigKeys.PROVIDED_CLASS_FILES));
        current.getTesterDataFiles().addAll(config.getList(
                ConfigKeys.TESTER_DATA_FILES));
        current.getPlaceholderFolderNames().addAll(config.getList(
                ConfigKeys.PLACEHOLDER_FOLDER_NAMES));
        return current;
    }

    /**
     * Checks an assessment before it is saved, without reading any file
     * or folder.
     *
     * @param draft the assessment to check
     * @param originalName the name it had before, or null for a new one
     * @param takenNames the names of the saved assessments
     * @return what is wrong, in one sentence, or an empty string
     */
    public String validate(SavedAssessment draft, String originalName,
            List<String> takenNames) {
        String name = draft.getName().trim();
        if (name.isEmpty()) {
            return "Give the assessment a name, such as Lab Test 2.";
        }
        if (name.length() > MAX_NAME_LENGTH) {
            return "Use a name of at most " + MAX_NAME_LENGTH
                    + " characters.";
        }
        for (String taken : takenNames) {
            boolean same = originalName != null
                    && taken.equalsIgnoreCase(originalName);
            if (!same && taken.equalsIgnoreCase(name)) {
                return "An assessment named " + taken + " already exists. "
                        + "Choose another name.";
            }
        }
        if (draft.getQuestions().isEmpty()) {
            return "Add at least one question.";
        }
        List<String> ids = new ArrayList<>();
        for (Question question : draft.getQuestions()) {
            String problem = checkQuestion(question, ids);
            if (!problem.isEmpty()) {
                return problem;
            }
            ids.add(question.getId().toLowerCase(Locale.ROOT));
        }
        if (TextUtils.isBlank(draft.getTestersDirectory())) {
            return "Choose the folder of tester files.";
        }
        if (TextUtils.isBlank(draft.getStarterDirectory())) {
            return "Choose the starter folder the students were given.";
        }
        return "";
    }

    /**
     * Checks that the tester and starter folders of an assessment exist.
     *
     * @param draft the assessment
     * @return what is wrong, in one sentence, or an empty string
     */
    public String checkFolders(SavedAssessment draft) {
        if (!Files.isDirectory(Paths.get(draft.getTestersDirectory()))) {
            return "The tester folder " + draft.getTestersDirectory()
                    + " does not exist.";
        }
        if (!Files.isDirectory(Paths.get(draft.getStarterDirectory()))) {
            return "The starter folder " + draft.getStarterDirectory()
                    + " does not exist.";
        }
        return "";
    }

    /**
     * Saves an assessment, replacing the one it was edited from. When
     * that one is in use, GradeZZY switches to the saved version.
     *
     * @param draft the assessment to save
     * @param originalName the name it had before, or null for a new one
     * @throws IOException if the file cannot be written
     * @throws IllegalArgumentException if the assessment is not valid;
     *         the message says why
     */
    public void save(SavedAssessment draft, String originalName)
            throws IOException {
        List<SavedAssessment> saved = readAll();
        String problem = validate(draft, originalName, namesOf(saved));
        if (problem.isEmpty()) {
            problem = checkFolders(draft);
        }
        if (!problem.isEmpty()) {
            throw new IllegalArgumentException(problem);
        }

        SavedAssessment clean = draft.copy();
        clean.setName(draft.getName().trim());
        Path oldFile = null;
        if (originalName != null) {
            oldFile = findFile(originalName);
        }
        boolean inUse = originalName != null
                && originalName.equalsIgnoreCase(getActiveName());
        if (inUse) {
            // Checked and switched first, so a bad folder saves nothing
            apply(clean);
        }

        Path target = oldFile;
        if (target == null || !sameFileName(originalName, clean)) {
            target = fileFor(clean.getName());
        }
        write(clean, target);
        if (oldFile != null && !oldFile.equals(target)) {
            Files.deleteIfExists(oldFile);
        }
    }

    /**
     * Switches GradeZZY to a saved assessment: its questions, tester
     * folder and starter folder are written to config.properties.
     *
     * @param name the assessment's name
     * @throws IOException if config.properties cannot be written
     * @throws IllegalArgumentException if there is no such assessment, or
     *         one of its settings is not valid, such as a missing folder
     */
    public void use(String name) throws IOException {
        SavedAssessment chosen = findIn(readAll(), name);
        if (chosen == null) {
            throw new IllegalArgumentException("There is no saved "
                    + "assessment named " + name + ".");
        }
        apply(chosen);
    }

    /**
     * Deletes a saved assessment. The one in use cannot be deleted.
     * Earlier results graded with it are kept.
     *
     * @param name the assessment's name
     * @throws IOException if the file cannot be deleted
     * @throws IllegalArgumentException if it is the one in use or does
     *         not exist
     */
    public void delete(String name) throws IOException {
        if (name.equalsIgnoreCase(getActiveName())) {
            throw new IllegalArgumentException(name + " is in use. Choose "
                    + "another assessment first, then delete this one.");
        }
        Path file = findFile(name);
        if (file == null) {
            throw new IllegalArgumentException("There is no saved "
                    + "assessment named " + name + ".");
        }
        Files.delete(file);
    }

    /**
     * Saves the assessment in use again from config.properties, after its
     * settings were changed on the Settings page.
     *
     * @throws IOException if the file cannot be written
     */
    public void saveCurrentFromSettings() throws IOException {
        SavedAssessment current = readCurrent();
        Path file = findFile(current.getName());
        if (file == null) {
            file = fileFor(current.getName());
        }
        write(current, file);
    }

    // Returns what is wrong with one question, or an empty string. The
    // ids already seen are in lower case.
    private String checkQuestion(Question question, List<String> seenIds) {
        String id = question.getId().trim();
        if (!id.matches(QUESTION_ID_PATTERN)) {
            return "Question ids use only letters and digits, such as Q1a. "
                    + "Fix \"" + id + "\".";
        }
        if (seenIds.contains(id.toLowerCase(Locale.ROOT))) {
            return "Two questions are called " + id + ". Give each its own "
                    + "id.";
        }
        String folder = question.getFolderName().trim();
        if (folder.isEmpty() || folder.matches(PATH_CHARACTERS)) {
            return "Give " + id + " a folder name without slashes, such as "
                    + "Q1.";
        }
        String file = question.getFileName().trim();
        if (!file.endsWith(JAVA_EXTENSION) || file.matches(PATH_CHARACTERS)
                || file.length() == JAVA_EXTENSION.length()) {
            return "Give " + id + " a Java file name, such as " + id
                    + JAVA_EXTENSION + ".";
        }
        double mark = question.getMaxScore();
        if (!(mark > 0) || mark > MAX_QUESTION_MARK) {
            return "Give " + id + " a maximum mark above 0 and at most "
                    + (int) MAX_QUESTION_MARK + ".";
        }
        return "";
    }

    // Copies an assessment's settings into config.properties. Every value
    // is checked before any is changed, so a bad one changes nothing.
    private void apply(SavedAssessment chosen) throws IOException {
        Map<String, String> values = toSettings(chosen);
        // A new question's settings must exist before they can be checked
        for (String key : values.keySet()) {
            if (!config.hasKey(key)) {
                config.setString(key, values.get(key));
            }
        }
        for (String key : values.keySet()) {
            String problem = settings.validate(key, values.get(key));
            if (!problem.isEmpty()) {
                throw new IllegalArgumentException(problem);
            }
        }
        for (String key : values.keySet()) {
            settings.updateSetting(key, values.get(key));
        }
        settings.save();
    }

    // Lists an assessment's settings, the questions' own settings before
    // the list of ids, as SettingsService checks that each id has them.
    private Map<String, String> toSettings(SavedAssessment assessment) {
        Map<String, String> values = new LinkedHashMap<>();
        List<String> ids = new ArrayList<>();
        for (Question question : assessment.getQuestions()) {
            String id = question.getId().trim();
            String prefix = ConfigKeys.QUESTION_PREFIX + id;
            values.put(prefix + ConfigKeys.QUESTION_FOLDER_SUFFIX,
                    question.getFolderName().trim());
            values.put(prefix + ConfigKeys.QUESTION_FILE_SUFFIX,
                    question.getFileName().trim());
            values.put(prefix + ConfigKeys.QUESTION_MAX_SCORE_SUFFIX,
                    formatMark(question.getMaxScore()));
            ids.add(id);
        }
        values.put(ConfigKeys.QUESTION_IDS, joinList(ids));
        values.put(ConfigKeys.ASSESSMENT_NAME, assessment.getName().trim());
        values.put(ConfigKeys.TESTERS_DIR,
                assessment.getTestersDirectory().trim());
        values.put(ConfigKeys.STARTER_DIR,
                assessment.getStarterDirectory().trim());
        values.put(ConfigKeys.PROVIDED_CLASS_FILES,
                joinList(assessment.getProvidedClassFiles()));
        values.put(ConfigKeys.TESTER_DATA_FILES,
                joinList(assessment.getTesterDataFiles()));
        values.put(ConfigKeys.PLACEHOLDER_FOLDER_NAMES,
                joinList(assessment.getPlaceholderFolderNames()));
        return values;
    }

    // Reads every saved assessment that can be read.
    private List<SavedAssessment> readAll() throws IOException {
        List<SavedAssessment> assessments = new ArrayList<>();
        for (Path file : listFiles()) {
            SavedAssessment read = readOrNull(file);
            if (read != null) {
                assessments.add(read);
            }
        }
        return assessments;
    }

    // Lists the saved assessment files; none when the folder is missing.
    private List<Path> listFiles() throws IOException {
        Path folder = getFolder();
        if (!Files.isDirectory(folder)) {
            return new ArrayList<>();
        }
        return FileUtils.listFiles(folder, FILE_EXTENSION);
    }

    // Reads one file, or returns null when it is not a saved assessment.
    private SavedAssessment readOrNull(Path file) {
        try {
            return read(file);
        } catch (IOException exception) {
            return null;
        } catch (IllegalArgumentException exception) {
            // A missing or unreadable value, such as a mark that is not
            // a number
            return null;
        }
    }

    // Reads one saved assessment file.
    private SavedAssessment read(Path file) throws IOException {
        Properties values = new Properties();
        Reader reader = null;
        try {
            reader = Files.newBufferedReader(file, StandardCharsets.UTF_8);
            values.load(reader);
        } finally {
            FileUtils.closeIfOpen(reader);
        }

        SavedAssessment assessment = new SavedAssessment();
        assessment.setName(required(values, ConfigKeys.ASSESSMENT_NAME));
        for (String id : splitList(required(values,
                ConfigKeys.QUESTION_IDS))) {
            String prefix = ConfigKeys.QUESTION_PREFIX + id;
            assessment.getQuestions().add(new Question(id,
                    required(values, prefix
                            + ConfigKeys.QUESTION_FOLDER_SUFFIX),
                    required(values, prefix
                            + ConfigKeys.QUESTION_FILE_SUFFIX),
                    Double.parseDouble(required(values, prefix
                            + ConfigKeys.QUESTION_MAX_SCORE_SUFFIX))));
        }
        assessment.setTestersDirectory(values.getProperty(
                ConfigKeys.TESTERS_DIR, ""));
        assessment.setStarterDirectory(values.getProperty(
                ConfigKeys.STARTER_DIR, ""));
        assessment.getProvidedClassFiles().addAll(splitList(
                values.getProperty(ConfigKeys.PROVIDED_CLASS_FILES, "")));
        assessment.getTesterDataFiles().addAll(splitList(
                values.getProperty(ConfigKeys.TESTER_DATA_FILES, "")));
        assessment.getPlaceholderFolderNames().addAll(splitList(
                values.getProperty(ConfigKeys.PLACEHOLDER_FOLDER_NAMES,
                        "")));
        return assessment;
    }

    // Writes one saved assessment file, creating the folder if needed.
    private void write(SavedAssessment assessment, Path file)
            throws IOException {
        Properties values = new Properties();
        Map<String, String> settingsOf = toSettings(assessment);
        for (String key : settingsOf.keySet()) {
            values.setProperty(key, settingsOf.get(key));
        }
        Files.createDirectories(file.getParent());
        Writer writer = null;
        try {
            writer = Files.newBufferedWriter(file, StandardCharsets.UTF_8);
            values.store(writer, FILE_COMMENT);
        } finally {
            FileUtils.closeIfOpen(writer);
        }
    }

    // Returns a value that must be in a saved file.
    private String required(Properties values, String key) {
        String value = values.getProperty(key);
        if (value == null || value.trim().isEmpty()) {
            throw new IllegalArgumentException("Missing " + key);
        }
        return value.trim();
    }

    // Returns the file of a saved assessment, or null if none has the
    // name.
    private Path findFile(String name) throws IOException {
        for (Path file : listFiles()) {
            SavedAssessment read = readOrNull(file);
            if (read != null && read.getName().equalsIgnoreCase(name)) {
                return file;
            }
        }
        return null;
    }

    // Says whether a renamed assessment would still get the same file
    // name, so its file can be kept.
    private boolean sameFileName(String originalName,
            SavedAssessment assessment) {
        return baseName(originalName).equals(baseName(
                assessment.getName()));
    }

    // Returns a free file for a name, such as lab-test-2.properties, or
    // lab-test-2-2.properties when another assessment has that one.
    private Path fileFor(String name) {
        Path folder = getFolder();
        String base = baseName(name);
        Path file = folder.resolve(base + FILE_EXTENSION);
        int number = FIRST_EXTRA_NUMBER;
        while (Files.exists(file) && !belongsTo(file, name)) {
            file = folder.resolve(base + FILE_NAME_JOIN + number
                    + FILE_EXTENSION);
            number++;
        }
        return file;
    }

    // Says whether a file holds the assessment with a name.
    private boolean belongsTo(Path file, String name) {
        SavedAssessment read = readOrNull(file);
        return read != null && read.getName().equalsIgnoreCase(name);
    }

    // Returns a name in lower case with dashes, for a file name.
    private String baseName(String name) {
        String base = String.valueOf(name).toLowerCase(Locale.ROOT)
                .replaceAll(NOT_FILE_NAME, FILE_NAME_JOIN);
        while (base.startsWith(FILE_NAME_JOIN)) {
            base = base.substring(FILE_NAME_JOIN.length());
        }
        while (base.endsWith(FILE_NAME_JOIN)) {
            base = base.substring(0, base.length()
                    - FILE_NAME_JOIN.length());
        }
        if (base.isEmpty()) {
            return UNNAMED_FILE;
        }
        return base;
    }

    // Returns the folder of the saved assessments.
    private Path getFolder() {
        String folder = DEFAULT_FOLDER;
        if (config.hasKey(ConfigKeys.ASSESSMENTS_DIR)
                && !TextUtils.isBlank(config.getString(
                        ConfigKeys.ASSESSMENTS_DIR))) {
            folder = config.getString(ConfigKeys.ASSESSMENTS_DIR);
        }
        return Paths.get(folder);
    }

    // Returns the assessment with a name, in any case, or null.
    private SavedAssessment findIn(List<SavedAssessment> assessments,
            String name) {
        for (SavedAssessment assessment : assessments) {
            if (assessment.getName().equalsIgnoreCase(name)) {
                return assessment;
            }
        }
        return null;
    }

    // Lists the names of some assessments.
    private List<String> namesOf(List<SavedAssessment> assessments) {
        List<String> names = new ArrayList<>();
        for (SavedAssessment assessment : assessments) {
            names.add(assessment.getName());
        }
        return names;
    }

    // Sorts assessments by name, ignoring case. Insertion sort avoids a
    // comparator for this short list.
    private void sortByName(List<SavedAssessment> assessments) {
        for (int index = 1; index < assessments.size(); index++) {
            SavedAssessment next = assessments.get(index);
            int position = index;
            while (position > 0 && assessments.get(position - 1).getName()
                    .compareToIgnoreCase(next.getName()) > 0) {
                assessments.set(position, assessments.get(position - 1));
                position--;
            }
            assessments.set(position, next);
        }
    }

    // Splits a list value the same way AppConfig.getList does.
    private List<String> splitList(String text) {
        List<String> items = new ArrayList<>();
        for (String part : text.split(LIST_SEPARATOR)) {
            String item = part.trim();
            if (!item.isEmpty()) {
                items.add(item);
            }
        }
        return items;
    }

    // Joins list items with commas.
    private String joinList(List<String> items) {
        List<String> trimmed = new ArrayList<>();
        for (String item : items) {
            if (!TextUtils.isBlank(item)) {
                trimmed.add(item.trim());
            }
        }
        return TextUtils.join(LIST_SEPARATOR, trimmed);
    }

    // Writes a mark without a needless ".0", as config.properties does.
    private String formatMark(double mark) {
        if (mark == Math.rint(mark)) {
            return String.valueOf((long) mark);
        }
        return String.valueOf(mark);
    }
}
