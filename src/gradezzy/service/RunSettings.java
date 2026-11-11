/*
 * File: RunSettings.java
 *
 * Purpose: Writes down the grading settings a run starts with, such as
 * the time limits, the checks that were on and how similar code was
 * compared, and reads one back, so a run can show what it was graded
 * with after the settings change.
 *
 * Where it fits: GradingService.createRun keeps the text on the
 * GradingRun, the database saves it with the run, and History shows it.
 */
package gradezzy.service;

import java.util.ArrayList;
import java.util.List;

import gradezzy.config.AppConfig;
import gradezzy.config.ConfigKeys;
import gradezzy.util.TextUtils;

/**
 * Writes and reads the grading settings of a run.
 *
 * <p>Tier: COULD. Owner: Damien.
 */
public final class RunSettings {

    // The settings that change how a run is graded or what it flags, in
    // the order History lists them
    private static final String[] KEYS = {
        ConfigKeys.RUN_TIMEOUT_SECONDS,
        ConfigKeys.COMPILE_TIMEOUT_SECONDS,
        ConfigKeys.MAX_MEMORY_MB,
        ConfigKeys.MAX_HANG_RERUNS,
        ConfigKeys.AUTO_FIX_ENABLED,
        ConfigKeys.DETECT_UNTOUCHED_ENABLED,
        ConfigKeys.DETECT_EMPTY_ENABLED,
        ConfigKeys.DETECT_HARD_CODED_ENABLED,
        ConfigKeys.MODEL_ANSWERS_DIR,
        ConfigKeys.RANDOM_INPUTS_ENABLED,
        ConfigKeys.RANDOM_INPUTS_COUNT,
        ConfigKeys.PLAGIARISM_ENABLED,
        ConfigKeys.PLAGIARISM_ALGORITHM,
        ConfigKeys.PLAGIARISM_THRESHOLD,
        ConfigKeys.PLAGIARISM_MIN_LINES
    };

    // Ends one setting's line, and comes between its key and value. A
    // key never holds the separator, and a value never holds a line end.
    private static final String LINE_END = "\n";
    private static final String KEY_VALUE_SEPARATOR = "=";

    // Stands in for a line end inside a value, so it stays on one line
    private static final String LINE_END_STAND_IN = " ";

    // Not used: every method is static.
    private RunSettings() {
    }

    /**
     * Lists the settings a run keeps, in the order to show them.
     *
     * <p>Tier: COULD.
     *
     * @return the setting keys
     */
    public static List<String> keys() {
        List<String> keys = new ArrayList<>();
        for (String key : KEYS) {
            keys.add(key);
        }
        return keys;
    }

    /**
     * Writes down the settings a run starts with, one key=value a line.
     * A setting an older config.properties does not have is left out.
     *
     * <p>Tier: COULD.
     *
     * @param config the application settings
     * @return the text to keep on the run
     */
    public static String capture(AppConfig config) {
        List<String> lines = new ArrayList<>();
        for (String key : KEYS) {
            if (config.hasKey(key)) {
                String value = config.getString(key).replace(LINE_END,
                        LINE_END_STAND_IN);
                lines.add(key + KEY_VALUE_SEPARATOR + value);
            }
        }
        return TextUtils.join(LINE_END, lines);
    }

    /**
     * Writes some settings into the text a run keeps again, with their
     * values now, such as the similar code settings when the check runs
     * again after they were changed. A run that kept no settings keeps
     * none, and a setting config.properties does not have is left as it
     * was.
     *
     * <p>Tier: COULD.
     *
     * @param settings the run's text, from capture
     * @param config the application settings
     * @param keys the settings to write again
     * @return the run's new text
     */
    public static String update(String settings, AppConfig config,
            String[] keys) {
        if (settings == null || settings.isEmpty()) {
            return settings;
        }
        List<String> lines = new ArrayList<>();
        for (String line : settings.split(LINE_END)) {
            lines.add(line);
        }
        for (String key : keys) {
            if (config.hasKey(key)) {
                String value = config.getString(key).replace(LINE_END,
                        LINE_END_STAND_IN);
                replaceLine(lines, key, key + KEY_VALUE_SEPARATOR + value);
            }
        }
        return TextUtils.join(LINE_END, lines);
    }

    /**
     * Writes one setting into the text a run keeps with a value of its
     * own, such as the model answers folder chosen on Set up in place of
     * the one in the settings. A run that kept no settings keeps none.
     *
     * <p>Tier: COULD.
     *
     * @param settings the run's text, from capture
     * @param key the setting
     * @param value its value for this run
     * @return the run's new text
     */
    public static String put(String settings, String key, String value) {
        if (settings == null || settings.isEmpty()) {
            return settings;
        }
        List<String> lines = new ArrayList<>();
        for (String line : settings.split(LINE_END)) {
            lines.add(line);
        }
        String oneLine = value.replace(LINE_END, " ");
        replaceLine(lines, key, key + KEY_VALUE_SEPARATOR + oneLine);
        return TextUtils.join(LINE_END, lines);
    }

    // Puts a setting's new line in place of its old one, or at the end
    // when the run did not keep it.
    private static void replaceLine(List<String> lines, String key,
            String newLine) {
        String start = key + KEY_VALUE_SEPARATOR;
        for (int i = 0; i < lines.size(); i++) {
            if (lines.get(i).startsWith(start)) {
                lines.set(i, newLine);
                return;
            }
        }
        lines.add(newLine);
    }

    /**
     * Reads one setting back from the text a run keeps.
     *
     * <p>Tier: COULD.
     *
     * @param settings the run's text, from capture
     * @param key the setting
     * @return its value, or null when the run did not keep it
     */
    public static String valueOf(String settings, String key) {
        if (settings == null) {
            return null;
        }
        String start = key + KEY_VALUE_SEPARATOR;
        for (String line : settings.split(LINE_END)) {
            if (line.startsWith(start)) {
                return line.substring(start.length());
            }
        }
        return null;
    }
}
