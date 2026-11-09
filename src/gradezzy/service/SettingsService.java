/*
 * File: SettingsService.java
 *
 * Purpose: Reads, checks and saves settings for the settings page.
 *
 * Where it fits: Keeps the settings page backed by config.properties.
 */
package gradezzy.service;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.InvalidPathException;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.nio.file.StandardCopyOption;
import java.util.ArrayList;
import java.util.List;
import java.util.regex.Pattern;
import java.util.regex.PatternSyntaxException;

import gradezzy.config.AppConfig;
import gradezzy.config.ConfigKeys;
import gradezzy.config.ExportFolders;
import gradezzy.config.ModuleSections;
import gradezzy.util.TextUtils;

/**
 * Reads, checks and saves settings for the settings page. A new value is
 * checked against a rule for its setting before it is accepted. Saving
 * rewrites only the lines of the changed settings, so the comments in
 * config.properties are kept. Code that reads a setting after it changed
 * sees the new value; objects built when the application started see it
 * after a restart.
 *
 * <p>Tier: SHOULD. Owner: Jing Yuan.
 */
public class SettingsService {

    // Separator written between a key and its value
    private static final String KEY_VALUE_SEPARATOR = "=";

    // Characters that end a key on a line of a properties file
    private static final String KEY_END_CHARACTERS = "=: \t\f";

    // Characters that start a comment line in a properties file
    private static final String COMMENT_STARTS = "#!";

    // Character that escapes the next one in a properties value
    private static final String BACKSLASH = "\\";

    // Character at the end of a line that continues onto the next line
    private static final char CONTINUATION = '\\';

    // Line break characters, which no value may contain
    private static final String NEW_LINE = "\n";
    private static final String CARRIAGE_RETURN = "\r";

    // Line ending used by Windows; any other file is written with LF
    private static final String WINDOWS_LINE_ENDING = "\r\n";

    // Line ending used by macOS and Linux
    private static final String UNIX_LINE_ENDING = "\n";

    // Added to the file name for the copy written before replacing it
    private static final String TEMP_SUFFIX = ".tmp";

    // Separator between the items of a list setting
    private static final String LIST_SEPARATOR = ",";

    // Characters allowed in a decimal number setting
    private static final String NUMBER_CHARACTERS = "0123456789.+-eE";

    // Smallest value of a count, a size or a time limit
    private static final int MIN_POSITIVE = 1;

    // Smallest value of a count or limit where 0 means none or no limit
    private static final int MIN_NON_NEGATIVE = 0;

    // Range of a percentage
    private static final int MIN_PERCENT = 0;
    private static final int MAX_PERCENT = 100;

    // Range of the plagiarism similarity
    private static final double MIN_SIMILARITY = 0.0;
    private static final double MAX_SIMILARITY = 1.0;

    // Used to tell an even count of backslashes from an odd one
    private static final int EVEN_DIVISOR = 2;

    // The values a true or false setting accepts
    private static final String[] BOOLEAN_WORDS = {"true", "false"};

    // The values ui.theme accepts
    private static final String[] THEMES = {"light", "dark"};

    // The values export.csv.line.ending accepts
    private static final String[] LINE_ENDINGS = {"CRLF", "LF"};

    // The values plagiarism.algorithm accepts
    private static final String[] ALGORITHMS = {"winnowing", "trigrams"};

    // Newer list settings an older config.properties may not have yet;
    // saving one adds its line at the end of the file.
    private static final String[] ADDABLE_KEYS = {
        ConfigKeys.MODULE_SECTION_CODES,
        ConfigKeys.OWN_SECTIONS_ONLY_MODULES,
        ConfigKeys.OUTPUT_DIR_BY_SECTION,
        ConfigKeys.SCORE_SHEET_BY_SECTION
    };

    // Settings that hold a whole number of at least 1
    private static final String[] POSITIVE_INTEGER_KEYS = {
        ConfigKeys.MAX_ZIP_SIZE_MB,
        ConfigKeys.MAX_ZIP_ENTRIES,
        ConfigKeys.HEADER_SCAN_LINES,
        ConfigKeys.COMPILE_TIMEOUT_SECONDS,
        ConfigKeys.RUN_TIMEOUT_SECONDS,
        ConfigKeys.THREAD_COUNT,
        ConfigKeys.MAX_OUTPUT_CHARACTERS,
        ConfigKeys.BACKUP_KEEP_COUNT,
        ConfigKeys.WINDOW_WIDTH,
        ConfigKeys.WINDOW_HEIGHT,
        ConfigKeys.TESTER_STRENGTH_MAX_COPIES,
        ConfigKeys.TESTER_STRENGTH_TIMEOUT_SECONDS,
        ConfigKeys.RANDOM_INPUTS_COUNT
    };

    // Settings that hold a whole number of 0 or more, where 0 means none
    // or no limit
    private static final String[] NON_NEGATIVE_INTEGER_KEYS = {
        ConfigKeys.MAX_MEMORY_MB,
        ConfigKeys.MAX_HANG_RERUNS
    };

    // Settings that hold true or false
    private static final String[] BOOLEAN_KEYS = {
        ConfigKeys.AUTO_FIX_ENABLED,
        ConfigKeys.DETECT_UNTOUCHED_ENABLED,
        ConfigKeys.DETECT_EMPTY_ENABLED,
        ConfigKeys.DETECT_HARD_CODED_ENABLED,
        ConfigKeys.PLAGIARISM_ENABLED,
        ConfigKeys.DATABASE_ENABLED,
        ConfigKeys.AUDIT_ENABLED,
        ConfigKeys.ANIMATIONS_ENABLED,
        ConfigKeys.TESTER_STRENGTH_ENABLED,
        ConfigKeys.RANDOM_INPUTS_ENABLED
    };

    // Settings that name a folder which must already exist
    private static final String[] EXISTING_FOLDER_KEYS = {
        ConfigKeys.SUBMISSIONS_DIR,
        ConfigKeys.TESTERS_DIR,
        ConfigKeys.STARTER_DIR
    };

    // Settings that name a file which must already exist
    private static final String[] EXISTING_FILE_KEYS = {
        ConfigKeys.SCORE_SHEET_FILE
    };

    // Folders GradeZZY writes into and may delete inside
    private static final String[] WRITTEN_FOLDER_KEYS = {
        ConfigKeys.WORKING_DIR,
        ConfigKeys.OUTPUT_DIR,
        ConfigKeys.BACKUP_DIR,
        ConfigKeys.ASSESSMENTS_DIR
    };

    // Settings naming the uploads, which a written folder must not touch
    private static final String[] INPUT_PATH_KEYS = {
        ConfigKeys.SUBMISSIONS_DIR,
        ConfigKeys.TESTERS_DIR,
        ConfigKeys.STARTER_DIR,
        ConfigKeys.SCORE_SHEET_FILE,
        ConfigKeys.MODEL_ANSWERS_DIR
    };

    // List settings that need at least one item
    private static final String[] REQUIRED_LIST_KEYS = {
        ConfigKeys.MODULE_CODES,
        ConfigKeys.SECTION_CODES,
        ConfigKeys.EXPORT_FORMATS
    };

    // List settings that may be empty
    private static final String[] OPTIONAL_LIST_KEYS = {
        ConfigKeys.IGNORED_FILE_NAMES,
        ConfigKeys.PROVIDED_CLASS_FILES,
        ConfigKeys.TESTER_DATA_FILES,
        ConfigKeys.PLACEHOLDER_FOLDER_NAMES,
        ConfigKeys.OWN_SECTIONS_ONLY_MODULES
    };

    // Settings every config.properties must hold; the newer ones in
    // ADDABLE_KEYS may be missing. Each question also needs its folder
    // and file settings.
    private static final String[] REQUIRED_KEYS = {
        ConfigKeys.MODULE_CODES,
        ConfigKeys.SECTION_CODES,
        ConfigKeys.SUBMISSIONS_DIR,
        ConfigKeys.TESTERS_DIR,
        ConfigKeys.SCORE_SHEET_FILE,
        ConfigKeys.STARTER_DIR,
        ConfigKeys.MODEL_ANSWERS_DIR,
        ConfigKeys.WORKING_DIR,
        ConfigKeys.OUTPUT_DIR,
        ConfigKeys.BACKUP_DIR,
        ConfigKeys.ASSESSMENTS_DIR,
        ConfigKeys.ZIP_NAME_PREFIX_PATTERN,
        ConfigKeys.IGNORED_FILE_NAMES,
        ConfigKeys.MAX_ZIP_SIZE_MB,
        ConfigKeys.MAX_ZIP_ENTRIES,
        ConfigKeys.ASSESSMENT_NAME,
        ConfigKeys.QUESTION_IDS,
        ConfigKeys.PLACEHOLDER_FOLDER_NAMES,
        ConfigKeys.PROVIDED_CLASS_FILES,
        ConfigKeys.TESTER_DATA_FILES,
        ConfigKeys.HEADER_NAME_LABEL,
        ConfigKeys.HEADER_EMAIL_LABEL,
        ConfigKeys.HEADER_SCAN_LINES,
        ConfigKeys.AUTO_FIX_ENABLED,
        ConfigKeys.JAVAC_COMMAND,
        ConfigKeys.JAVA_COMMAND,
        ConfigKeys.COMPILE_TIMEOUT_SECONDS,
        ConfigKeys.RUN_TIMEOUT_SECONDS,
        ConfigKeys.THREAD_COUNT,
        ConfigKeys.MAX_OUTPUT_CHARACTERS,
        ConfigKeys.MAX_MEMORY_MB,
        ConfigKeys.MAX_HANG_RERUNS,
        ConfigKeys.TESTER_CLASS_SUFFIX,
        ConfigKeys.PASS_MARKER,
        ConfigKeys.FAIL_MARKER,
        ConfigKeys.TEST_HEADER_MARKER,
        ConfigKeys.DETECT_UNTOUCHED_ENABLED,
        ConfigKeys.DETECT_EMPTY_ENABLED,
        ConfigKeys.DETECT_HARD_CODED_ENABLED,
        ConfigKeys.TESTER_STRENGTH_ENABLED,
        ConfigKeys.TESTER_STRENGTH_MAX_COPIES,
        ConfigKeys.TESTER_STRENGTH_TIMEOUT_SECONDS,
        ConfigKeys.RANDOM_INPUTS_ENABLED,
        ConfigKeys.RANDOM_INPUTS_COUNT,
        ConfigKeys.PLAGIARISM_ENABLED,
        ConfigKeys.PLAGIARISM_THRESHOLD,
        ConfigKeys.PLAGIARISM_ALGORITHM,
        ConfigKeys.EXPORT_FORMATS,
        ConfigKeys.CSV_LINE_ENDING,
        ConfigKeys.CSV_ID_PREFIX,
        ConfigKeys.CSV_END_OF_LINE_MARKER,
        ConfigKeys.PASS_MARK_PERCENT,
        ConfigKeys.MARK_BAND_WIDTH_PERCENT,
        ConfigKeys.DATABASE_ENABLED,
        ConfigKeys.DATABASE_FILE,
        ConfigKeys.BACKUP_KEEP_COUNT,
        ConfigKeys.AUDIT_ENABLED,
        ConfigKeys.UI_THEME,
        ConfigKeys.WINDOW_WIDTH,
        ConfigKeys.WINDOW_HEIGHT,
        ConfigKeys.ANIMATIONS_ENABLED
    };

    // The audit log action this service records
    private static final String SETTINGS_SAVED = "Settings saved";

    // The application settings
    private final AppConfig config;

    // The log of instructor actions, or null to log nothing
    private final AuditService auditService;

    // The keys changed since the last save, in the order they changed
    private final List<String> changedKeys = new ArrayList<>();

    /**
     * Creates a new settings service that logs nothing.
     *
     * @param config the application settings
     */
    public SettingsService(AppConfig config) {
        this(config, null);
    }

    /**
     * Creates a new settings service that writes every save to the audit
     * log, with the settings that changed.
     *
     * @param config the application settings
     * @param auditService the log of instructor actions, or null to log
     *        nothing
     */
    public SettingsService(AppConfig config, AuditService auditService) {
        this.config = config;
        this.auditService = auditService;
    }

    /**
     * Returns the current value of a setting.
     *
     * @param key the key
     * @return the value, or an empty string if it is not set
     */
    public String getSetting(String key) {
        if (key == null || !config.hasKey(key)) {
            return "";
        }

        return config.getString(key);
    }

    /**
     * Checks a new value before it is accepted. Spaces at both ends of
     * the value are ignored. Only settings already in config.properties,
     * or one of the newer per-module lists, can be changed.
     *
     * @param key the key
     * @param value the value
     * @return an error message, or an empty string if the value is valid
     */
    public String validate(String key, String value) {
        if (key == null) {
            return "Choose a setting first.";
        }

        if (!config.hasKey(key) && !contains(ADDABLE_KEYS, key)) {
            return "There is no setting called '" + key + "'.";
        }

        if (value == null) {
            return "Enter a value for '" + key + "'.";
        }

        if (value.contains(NEW_LINE) || value.contains(CARRIAGE_RETURN)) {
            return "'" + key + "' must fit on one line.";
        }

        String text = value.trim();

        if (contains(POSITIVE_INTEGER_KEYS, key)) {
            return checkWholeNumber(key, text, MIN_POSITIVE,
                    Integer.MAX_VALUE);
        }

        if (contains(NON_NEGATIVE_INTEGER_KEYS, key)) {
            return checkWholeNumber(key, text, MIN_NON_NEGATIVE,
                    Integer.MAX_VALUE);
        }

        if (key.equals(ConfigKeys.PASS_MARK_PERCENT)) {
            return checkWholeNumber(key, text, MIN_PERCENT, MAX_PERCENT);
        }

        if (key.equals(ConfigKeys.MARK_BAND_WIDTH_PERCENT)) {
            return checkWholeNumber(key, text, MIN_POSITIVE, MAX_PERCENT);
        }

        if (key.equals(ConfigKeys.PLAGIARISM_THRESHOLD)) {
            return checkSimilarity(key, text);
        }

        if (contains(BOOLEAN_KEYS, key)) {
            return checkChoice(key, text, BOOLEAN_WORDS);
        }

        if (key.equals(ConfigKeys.UI_THEME)) {
            return checkChoice(key, text, THEMES);
        }

        if (key.equals(ConfigKeys.CSV_LINE_ENDING)) {
            return checkChoice(key, text, LINE_ENDINGS);
        }

        if (key.equals(ConfigKeys.PLAGIARISM_ALGORITHM)) {
            return checkChoice(key, text, ALGORITHMS);
        }

        if (key.equals(ConfigKeys.ZIP_NAME_PREFIX_PATTERN)) {
            return checkPattern(key, text);
        }

        if (contains(EXISTING_FOLDER_KEYS, key)) {
            return checkPath(key, text, true);
        }

        if (key.equals(ConfigKeys.MODEL_ANSWERS_DIR)) {
            return checkOptionalFolder(key, text);
        }

        if (contains(EXISTING_FILE_KEYS, key)) {
            return checkPath(key, text, false);
        }

        if (contains(WRITTEN_FOLDER_KEYS, key)) {
            return checkWrittenFolder(key, text);
        }

        if (key.equals(ConfigKeys.QUESTION_IDS)) {
            return checkQuestionIds(key, text);
        }

        if (contains(REQUIRED_LIST_KEYS, key)) {
            return checkRequiredList(key, text);
        }

        if (contains(OPTIONAL_LIST_KEYS, key)) {
            return "";
        }

        if (key.equals(ConfigKeys.MODULE_SECTION_CODES)) {
            return ModuleSections.validate(text);
        }

        if (key.equals(ConfigKeys.OUTPUT_DIR_BY_SECTION)
                || key.equals(ConfigKeys.SCORE_SHEET_BY_SECTION)) {
            return ExportFolders.validate(text);
        }

        if (text.isEmpty()) {
            return "'" + key + "' cannot be blank.";
        }

        return "";
    }

    /**
     * Checks every setting in config.properties with validate, so a bad
     * value such as database.enabled=yes is found when the app starts,
     * not when that setting is first read. A setting that is missing
     * altogether, such as a deleted exec.run.timeout.seconds line, is
     * found too. The folders and the score sheet that must already exist
     * are not checked for existence: they are chosen again for each
     * batch on Set up, and a missing one is reported there.
     *
     * <p>Tier: SHOULD.
     *
     * @return one message for each missing setting, then one for each
     *         bad setting in alphabetical order of the settings, or an
     *         empty list when all are valid
     */
    public List<String> findProblems() {
        List<String> problems = new ArrayList<>();

        for (String key : listRequiredKeys()) {
            if (!config.hasKey(key)) {
                problems.add("Missing setting '" + key + "'.");
            }
        }
        for (String key : config.getKeys()) {
            // The model answers folder is checked when it is used, so a
            // moved folder skips those checks instead of stopping the app
            boolean mustExist = contains(EXISTING_FOLDER_KEYS, key)
                    || contains(EXISTING_FILE_KEYS, key)
                    || key.equals(ConfigKeys.MODEL_ANSWERS_DIR);
            if (!mustExist) {
                String message = validate(key, config.getString(key));
                if (!message.isEmpty()) {
                    problems.add(message);
                }
            }
        }
        return problems;
    }

    // Lists the settings that must be present: the fixed ones, then the
    // folder and file of each question in assessment.question.ids.
    private List<String> listRequiredKeys() {
        List<String> keys = new ArrayList<>();
        for (String key : REQUIRED_KEYS) {
            keys.add(key);
        }
        if (!config.hasKey(ConfigKeys.QUESTION_IDS)) {
            return keys;
        }
        for (String questionId : config.getList(ConfigKeys.QUESTION_IDS)) {
            String start = ConfigKeys.QUESTION_PREFIX + questionId;
            keys.add(start + ConfigKeys.QUESTION_FOLDER_SUFFIX);
            keys.add(start + ConfigKeys.QUESTION_FILE_SUFFIX);
        }
        return keys;
    }

    /**
     * Changes a setting in memory after checking it. The file is written
     * by save.
     *
     * @param key the key
     * @param value the value
     * @throws IllegalArgumentException if validate rejects the value; the
     *         message is the one validate returns
     */
    public void updateSetting(String key, String value) {
        String message = validate(key, value);

        if (!message.isEmpty()) {
            throw new IllegalArgumentException(message);
        }

        config.setString(key, value.trim());

        if (!changedKeys.contains(key)) {
            changedKeys.add(key);
        }
    }

    /**
     * Writes the changed settings back to config.properties. Only the
     * line of each changed setting is rewritten; comments, blank lines
     * and the other settings are copied as they are. Every line ends with
     * the file's own line ending (CRLF if the file uses it, otherwise
     * LF). Nothing is written when no setting changed.
     *
     * @throws IOException if the file cannot be read or written
     */
    public void save() throws IOException {
        if (changedKeys.isEmpty()) {
            return;
        }

        Path configFile = Paths.get(config.getConfigFile());
        String lineEnding = UNIX_LINE_ENDING;

        if (Files.readString(configFile).contains(WINDOWS_LINE_ENDING)) {
            lineEnding = WINDOWS_LINE_ENDING;
        }

        List<String> lines = Files.readAllLines(configFile);
        List<String> writtenKeys = new ArrayList<>();
        String output = "";
        int index = 0;

        while (index < lines.size()) {
            int lastIndex = findLastLine(lines, index);
            String key = readKey(lines.get(index));

            if (key != null && changedKeys.contains(key)) {
                output += formatLine(key) + lineEnding;
                writtenKeys.add(key);
            } else {
                for (int lineIndex = index; lineIndex <= lastIndex;
                        lineIndex++) {
                    output += lines.get(lineIndex) + lineEnding;
                }
            }

            index = lastIndex + 1;
        }

        // A key that was not in the file yet goes at the end.
        for (String key : changedKeys) {
            if (!writtenKeys.contains(key)) {
                output += formatLine(key) + lineEnding;
            }
        }

        writeSafely(configFile, output);
        if (auditService != null) {
            auditService.record(SETTINGS_SAVED, describeChanges());
        }
        changedKeys.clear();
    }

    // Lists the changed settings with their new values, for the audit log.
    private String describeChanges() {
        List<String> pairs = new ArrayList<>();

        for (String key : changedKeys) {
            pairs.add(key + KEY_VALUE_SEPARATOR + config.getString(key));
        }

        return "Changed " + TextUtils.join(", ", pairs) + ".";
    }

    // Writes a copy first, so a failure halfway leaves the original
    // file untouched, then puts the copy in its place.
    private void writeSafely(Path file, String text) throws IOException {
        Path tempFile = file.resolveSibling(file.getFileName()
                + TEMP_SUFFIX);

        try {
            Files.writeString(tempFile, text);
            Files.move(tempFile, file, StandardCopyOption.REPLACE_EXISTING);
        } finally {
            // Only does something when writing or moving failed.
            Files.deleteIfExists(tempFile);
        }
    }

    // Says whether a list of keys holds a key.
    private boolean contains(String[] keys, String key) {
        for (String candidate : keys) {
            if (candidate.equals(key)) {
                return true;
            }
        }

        return false;
    }

    // Checks a whole number between two limits, both included.
    private String checkWholeNumber(String key, String text, int min,
            int max) {
        String message = "'" + key + "' must be a whole number from "
                + min + " to " + max + ".";

        if (max == Integer.MAX_VALUE) {
            message = "'" + key + "' must be a whole number of at least "
                    + min + ".";
        }

        try {
            int number = Integer.parseInt(text);

            if (number < min || number > max) {
                return message;
            }

            return "";
        } catch (NumberFormatException exception) {
            return message;
        }
    }

    // Checks the plagiarism similarity: a number from 0 to 1.
    private String checkSimilarity(String key, String text) {
        String message = "'" + key + "' must be a number from "
                + MIN_SIMILARITY + " to " + MAX_SIMILARITY + ".";

        if (!isPlainNumber(text)) {
            return message;
        }

        double number = Double.parseDouble(text);

        if (number < MIN_SIMILARITY || number > MAX_SIMILARITY) {
            return message;
        }

        return "";
    }

    // Says whether text is a decimal number such as 2.5 or 1e3, made of
    // digits, a sign, a point and an exponent only. This rejects words
    // Double.parseDouble also accepts, such as NaN, Infinity and 1d.
    private boolean isPlainNumber(String text) {
        if (text.isEmpty()) {
            return false;
        }

        for (int position = 0; position < text.length(); position++) {
            if (NUMBER_CHARACTERS.indexOf(text.charAt(position)) < 0) {
                return false;
            }
        }

        try {
            Double.parseDouble(text);
            return true;
        } catch (NumberFormatException exception) {
            return false;
        }
    }

    // Checks that a value is one of a few words, ignoring case.
    private String checkChoice(String key, String text, String[] choices) {
        for (String choice : choices) {
            if (choice.equalsIgnoreCase(text)) {
                return "";
            }
        }

        return "'" + key + "' must be one of: "
                + TextUtils.join(", ", choices) + ".";
    }

    // Checks that a value is a valid regular expression.
    private String checkPattern(String key, String text) {
        try {
            Pattern.compile(text);
            return "";
        } catch (PatternSyntaxException exception) {
            return "'" + key + "' is not a valid regular expression: "
                    + exception.getDescription() + ".";
        }
    }

    // Checks a folder GradeZZY writes into. Working copies are made by
    // deleting and replacing a folder named after each student, so a
    // folder that holds the program, the uploads or other files could
    // lose them.
    private String checkWrittenFolder(String key, String text) {
        if (text.isEmpty()) {
            return "'" + key + "' cannot be blank.";
        }

        Path folder = null;
        Path programFolder = null;

        try {
            folder = Paths.get(text).toAbsolutePath().normalize();
            programFolder = Paths.get("").toAbsolutePath().normalize();
        } catch (InvalidPathException exception) {
            return "'" + key + "' is not a valid folder name.";
        }

        if (programFolder.startsWith(folder)) {
            return "'" + key + "' cannot be the GradeZZY folder or a folder "
                    + "around it. Use a folder inside workspace instead.";
        }

        for (String inputKey : INPUT_PATH_KEYS) {
            if (overlaps(folder, config.getString(inputKey))) {
                return "'" + key + "' cannot be the same as, inside or "
                        + "around the folder of '" + inputKey + "'.";
            }
        }

        if (key.equals(ConfigKeys.WORKING_DIR)
                && !isSamePath(folder, config.getString(key))
                && !isNewOrEmpty(folder)) {
            return "'" + key + "' must be a new or empty folder, because "
                    + "GradeZZY deletes and replaces the folders inside it.";
        }

        return "";
    }

    // Says whether a folder is the same as, inside or around a path
    // from another setting. A setting that is not a valid path, or is
    // left empty, cannot overlap.
    private boolean overlaps(Path folder, String otherText) {
        if (otherText.isEmpty()) {
            return false;
        }
        try {
            Path other = Paths.get(otherText).toAbsolutePath().normalize();
            return folder.startsWith(other) || other.startsWith(folder);
        } catch (InvalidPathException exception) {
            return false;
        }
    }

    // Says whether a folder is the one a setting holds now.
    private boolean isSamePath(Path folder, String currentText) {
        try {
            return folder.equals(Paths.get(currentText).toAbsolutePath()
                    .normalize());
        } catch (InvalidPathException exception) {
            return false;
        }
    }

    // Says whether a folder does not exist yet or holds nothing.
    private boolean isNewOrEmpty(Path folder) {
        if (!Files.exists(folder)) {
            return true;
        }

        String[] names = folder.toFile().list();
        return names != null && names.length == 0;
    }

    // Checks a folder setting that may be left empty to switch off what
    // uses it, and must otherwise name an existing folder.
    private String checkOptionalFolder(String key, String text) {
        if (text.isEmpty()) {
            return "";
        }
        return checkPath(key, text, true);
    }

    // Checks that a value names an existing folder or file. A relative
    // path is taken from the folder the application was started in.
    private String checkPath(String key, String text, boolean folder) {
        String kind = "file";

        if (folder) {
            kind = "folder";
        }

        String message = "'" + key + "' must name an existing " + kind
                + ".";

        if (text.isEmpty()) {
            return message;
        }

        try {
            Path path = Paths.get(text);

            if (folder && Files.isDirectory(path)) {
                return "";
            } else if (!folder && Files.isRegularFile(path)) {
                return "";
            }

            return message;
        } catch (InvalidPathException exception) {
            return message;
        }
    }

    // Checks the question ids: at least one, each with its settings.
    private String checkQuestionIds(String key, String text) {
        List<String> ids = splitList(text);

        if (ids.isEmpty()) {
            return "'" + key + "' needs at least one question id.";
        }

        for (String id : ids) {
            String prefix = ConfigKeys.QUESTION_PREFIX + id;
            boolean hasFolder = config.hasKey(prefix
                    + ConfigKeys.QUESTION_FOLDER_SUFFIX);
            boolean hasFile = config.hasKey(prefix
                    + ConfigKeys.QUESTION_FILE_SUFFIX);

            if (!hasFolder || !hasFile) {
                return "Question '" + id + "' needs the settings " + prefix
                        + ConfigKeys.QUESTION_FOLDER_SUFFIX + " and "
                        + ConfigKeys.QUESTION_FILE_SUFFIX + " first.";
            }
        }

        return "";
    }

    // Checks that a list setting has at least one item.
    private String checkRequiredList(String key, String text) {
        if (splitList(text).isEmpty()) {
            return "'" + key + "' needs at least one item, separated by "
                    + "commas.";
        }

        return "";
    }

    // Splits a list value the same way AppConfig.getList does: items
    // are trimmed and empty items are left out.
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

    // Returns the key on a line, or null for a blank or comment line.
    // Keys with escaped characters are not supported; GradeZZY has none.
    private String readKey(String line) {
        String trimmed = line.trim();

        if (trimmed.isEmpty()
                || COMMENT_STARTS.indexOf(trimmed.charAt(0)) >= 0) {
            return null;
        }

        int end = 0;

        while (end < trimmed.length()
                && KEY_END_CHARACTERS.indexOf(trimmed.charAt(end)) < 0) {
            end++;
        }

        return trimmed.substring(0, end);
    }

    // Returns the index of the last line of the entry that starts at
    // index. An entry continues while its line ends with an odd number
    // of backslashes. Blank and comment lines never continue.
    private int findLastLine(List<String> lines, int index) {
        if (readKey(lines.get(index)) == null) {
            return index;
        }

        int lastIndex = index;

        while (lastIndex < lines.size() - 1
                && endsWithContinuation(lines.get(lastIndex))) {
            lastIndex++;
        }

        return lastIndex;
    }

    // Says whether a line ends with an odd number of backslashes.
    private boolean endsWithContinuation(String line) {
        int count = 0;
        int position = line.length() - 1;

        while (position >= 0 && line.charAt(position) == CONTINUATION) {
            count++;
            position--;
        }

        return count % EVEN_DIVISOR != 0;
    }

    // Builds the line written for a changed key. A backslash is doubled
    // because the properties format reads it as an escape.
    private String formatLine(String key) {
        String value = config.getString(key).replace(BACKSLASH,
                BACKSLASH + BACKSLASH);
        return key + KEY_VALUE_SEPARATOR + value;
    }
}
