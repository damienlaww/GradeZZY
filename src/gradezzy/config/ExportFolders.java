/*
 * File: ExportFolders.java
 *
 * Purpose: Reads and writes the export folders kept for one module or
 * one section, in paths.output.dir.by.section, such as
 * "CS102/G1=workspace/g1-exports;IS442=workspace/is442". A section's own
 * folder wins, then its module's, then paths.output.dir for everyone.
 * It also picks a section's score sheet from paths.score.sheet.by.section,
 * which is written the same way.
 *
 * Where it fits: The live export writes to the folder this picks, Export
 * shows it, and Settings lists, adds and removes the folders.
 */
package gradezzy.config;

import java.nio.file.Path;
import java.nio.file.Paths;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

/**
 * The export folders of single modules and sections.
 */
public final class ExportFolders {

    // Separates one entry from the next
    private static final String ENTRY_SEPARATOR = ";";

    // Separates who an entry is for from its folder
    private static final String VALUE_SEPARATOR = "=";

    // Separates a module from a section in who an entry is for
    private static final String SECTION_SEPARATOR = "/";

    // A module or section code: letters, digits and dashes
    private static final String CODE_PATTERN = "[A-Za-z0-9-]+";

    // Not used: every method is static.
    private ExportFolders() {
    }

    /**
     * Returns the folder a section's exports are saved in: its own, else
     * its module's, else the folder for everyone.
     *
     * @param defaultFolder the value of paths.output.dir
     * @param folders the value of paths.output.dir.by.section, or null
     * @param moduleCode the module, or null
     * @param sectionCode the section, or null
     * @return the folder
     */
    public static String resolve(String defaultFolder, String folders,
            String moduleCode, String sectionCode) {
        Map<String, String> entries = read(folders);
        if (moduleCode != null && sectionCode != null) {
            String own = find(entries, keyOf(moduleCode, sectionCode));
            if (own != null) {
                return own;
            }
        }
        if (moduleCode != null) {
            String module = find(entries, moduleCode);
            if (module != null) {
                return module;
            }
        }
        return defaultFolder;
    }

    /**
     * Returns the value kept for exactly one module or section, without
     * falling back to anything else.
     *
     * @param folders the value of the setting, or null
     * @param who the module, or module/section, such as "CS102/G1"
     * @return the value, or "" when none is kept for it
     */
    public static String exact(String folders, String who) {
        String found = find(read(folders), who);
        return found == null ? "" : found;
    }

    /**
     * Returns who each folder is for, such as "CS102" or "CS102/G1", in
     * the order they were added, with its folder.
     *
     * @param folders the value of paths.output.dir.by.section, or null
     * @return each entry as {who, folder}
     */
    public static List<String[]> list(String folders) {
        List<String[]> list = new ArrayList<>();
        for (Map.Entry<String, String> entry : read(folders).entrySet()) {
            list.add(new String[] {entry.getKey(), entry.getValue()});
        }
        return list;
    }

    /**
     * Returns the value with one entry set, replacing any for the same
     * module or section.
     *
     * @param folders the value now, or null
     * @param who the module, or module/section, such as "CS102/G1"
     * @param folder the folder
     * @return the new value
     */
    public static String put(String folders, String who, String folder) {
        Map<String, String> entries = read(folders);
        String existing = matchingKey(entries, who);
        if (existing != null) {
            entries.remove(existing);
        }
        entries.put(who.trim(), folder.trim());
        return write(entries);
    }

    /**
     * Returns the value without the entry of a module or section.
     *
     * @param folders the value now, or null
     * @param who the module, or module/section
     * @return the new value
     */
    public static String remove(String folders, String who) {
        Map<String, String> entries = read(folders);
        String existing = matchingKey(entries, who);
        if (existing != null) {
            entries.remove(existing);
        }
        return write(entries);
    }

    /**
     * Returns the value without a module's folder or any of its
     * sections' folders, for a module that is deleted.
     *
     * @param folders the value now, or null
     * @param moduleCode the module
     * @return the new value
     */
    public static String removeModule(String folders, String moduleCode) {
        Map<String, String> entries = read(folders);
        String module = moduleCode.trim().toLowerCase(java.util.Locale.ROOT);
        entries.keySet().removeIf(key -> {
            String lower = key.toLowerCase(java.util.Locale.ROOT);
            return lower.equals(module)
                    || lower.startsWith(module + SECTION_SEPARATOR);
        });
        return write(entries);
    }

    /**
     * Says who an entry is for, as written in the setting.
     *
     * @param moduleCode the module
     * @param sectionCode the section, or null for the whole module
     * @return such as "CS102/G1" or "CS102"
     */
    public static String keyOf(String moduleCode, String sectionCode) {
        if (sectionCode == null || sectionCode.trim().isEmpty()) {
            return moduleCode.trim();
        }
        return moduleCode.trim() + SECTION_SEPARATOR + sectionCode.trim();
    }

    /**
     * Returns the score sheet of a section: its own one in
     * paths.score.sheet.by.section, which has the same form as the export
     * folders, else paths.score.sheet.file for everyone. Grading, the
     * Export warning and the CSV export all use this rule for a run that
     * has no score sheet recorded, so they agree on the sheet.
     *
     * <p>Tier: SHOULD.
     *
     * @param config the application settings
     * @param moduleCode the module, or null
     * @param sectionCode the section, or null for the sheet for everyone
     * @return the score sheet's path
     */
    public static Path findScoreSheet(AppConfig config, String moduleCode,
            String sectionCode) {
        Path own = findOwnScoreSheet(config, moduleCode, sectionCode);
        if (own != null) {
            return own;
        }
        return Paths.get(config.getString(ConfigKeys.SCORE_SHEET_FILE));
    }

    /**
     * Returns the score sheet chosen for one section in
     * paths.score.sheet.by.section, without falling back to the one for
     * everyone.
     *
     * <p>Tier: SHOULD.
     *
     * @param config the application settings
     * @param moduleCode the module, or null
     * @param sectionCode the section, or null
     * @return the section's own score sheet, or null if it has none
     */
    public static Path findOwnScoreSheet(AppConfig config, String moduleCode,
            String sectionCode) {
        if (moduleCode == null || sectionCode == null
                || !config.hasKey(ConfigKeys.SCORE_SHEET_BY_SECTION)) {
            return null;
        }

        String own = exact(config.getString(
                ConfigKeys.SCORE_SHEET_BY_SECTION),
                keyOf(moduleCode, sectionCode));
        if (own.isEmpty()) {
            return null;
        }
        return Paths.get(own);
    }

    /**
     * Checks the value's form.
     *
     * @param value the value
     * @return an empty string when it is valid, or the problem
     */
    public static String validate(String value) {
        if (value == null || value.trim().isEmpty()) {
            return "";
        }
        for (String entry : value.split(ENTRY_SEPARATOR)) {
            if (entry.trim().isEmpty()) {
                continue;
            }
            int equals = entry.indexOf(VALUE_SEPARATOR);
            if (equals <= 0 || entry.substring(equals + 1).trim().isEmpty()) {
                return "Write each folder as CS102/G1=folder, separated by ;.";
            }
            String who = entry.substring(0, equals).trim();
            String[] parts = who.split(SECTION_SEPARATOR, -1);
            if (parts.length > 2) {
                return "Name a module, or a module and a section, such as "
                        + "CS102/G1.";
            }
            for (String part : parts) {
                if (!part.trim().matches(CODE_PATTERN)) {
                    return "Use letters, digits and dashes for modules and "
                            + "sections, such as CS102/G1.";
                }
            }
        }
        return "";
    }

    // Reads the value into who each folder is for and the folder.
    private static Map<String, String> read(String value) {
        Map<String, String> entries = new LinkedHashMap<>();
        if (value == null) {
            return entries;
        }
        for (String entry : value.split(ENTRY_SEPARATOR)) {
            int equals = entry.indexOf(VALUE_SEPARATOR);
            if (equals > 0) {
                String who = entry.substring(0, equals).trim();
                String folder = decode(entry.substring(equals + 1).trim());
                if (!who.isEmpty() && !folder.isEmpty()) {
                    entries.put(who, folder);
                }
            }
        }
        return entries;
    }

    // Writes the entries back into one value.
    private static String write(Map<String, String> entries) {
        StringBuilder text = new StringBuilder();
        for (Map.Entry<String, String> entry : entries.entrySet()) {
            if (text.length() > 0) {
                text.append(ENTRY_SEPARATOR);
            }
            text.append(entry.getKey()).append(VALUE_SEPARATOR)
                    .append(encode(entry.getValue()));
        }
        return text.toString();
    }

    // Writes a folder so a ; in its name does not end the entry: % is
    // written %25 and ; is written %3B.
    private static String encode(String folder) {
        return folder.replace("%", "%25").replace(ENTRY_SEPARATOR, "%3B");
    }

    // Reads a folder written by encode.
    private static String decode(String folder) {
        return folder.replace("%3B", ENTRY_SEPARATOR).replace("%3b",
                ENTRY_SEPARATOR).replace("%25", "%");
    }

    // Returns the folder of an entry, ignoring case, or null.
    private static String find(Map<String, String> entries, String who) {
        String key = matchingKey(entries, who);
        return key == null ? null : entries.get(key);
    }

    // Returns the key written for a module or section, ignoring case.
    private static String matchingKey(Map<String, String> entries,
            String who) {
        for (String key : entries.keySet()) {
            if (key.equalsIgnoreCase(who.trim())) {
                return key;
            }
        }
        return null;
    }
}
