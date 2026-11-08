/*
 * File: ExportFolders.java
 *
 * Purpose: Reads and writes the export folders kept for one module or
 * one section, in paths.output.dir.by.section, such as
 * "CS102/G1=workspace/g1-exports;IS442=workspace/is442". A section's own
 * folder wins, then its module's, then paths.output.dir for everyone.
 *
 * Where it fits: The live export writes to the folder this picks, Export
 * shows it, and Settings lists, adds and removes the folders.
 */
package gradezzy.config;

import java.util.ArrayList;
import java.util.List;
import java.util.Locale;

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

    // Where who an entry is for, and its folder, sit in an entry
    private static final int WHO = 0;
    private static final int FOLDER = 1;

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
        List<String[]> entries = read(folders);
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
        if (found == null) {
            return "";
        }
        return found;
    }

    /**
     * Returns who each folder is for, such as "CS102" or "CS102/G1", in
     * the order they were added, with its folder.
     *
     * @param folders the value of paths.output.dir.by.section, or null
     * @return each entry as {who, folder}
     */
    public static List<String[]> list(String folders) {
        return read(folders);
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
        List<String[]> entries = read(folders);
        int existing = indexOf(entries, who);
        if (existing >= 0) {
            entries.remove(existing);
        }
        entries.add(new String[] {who.trim(), folder.trim()});
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
        List<String[]> entries = read(folders);
        int existing = indexOf(entries, who);
        if (existing >= 0) {
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
        String module = moduleCode.trim().toLowerCase(Locale.ROOT);
        List<String[]> kept = new ArrayList<>();
        for (String[] entry : read(folders)) {
            String lower = entry[WHO].toLowerCase(Locale.ROOT);
            if (!lower.equals(module)
                    && !lower.startsWith(module + SECTION_SEPARATOR)) {
                kept.add(entry);
            }
        }
        return write(kept);
    }

    /**
     * Returns the value without the entries of one section in any
     * module, for a section every module has that is deleted.
     *
     * @param folders the value now, or null
     * @param sectionCode the section
     * @return the new value
     */
    public static String removeSection(String folders, String sectionCode) {
        String ending = (SECTION_SEPARATOR + sectionCode.trim())
                .toLowerCase(Locale.ROOT);
        List<String[]> kept = new ArrayList<>();
        for (String[] entry : read(folders)) {
            if (!entry[WHO].toLowerCase(Locale.ROOT).endsWith(ending)) {
                kept.add(entry);
            }
        }
        return write(kept);
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
            String problem = validateEntry(entry);
            if (!problem.isEmpty()) {
                return problem;
            }
        }
        return "";
    }

    // Checks one entry, such as "CS102/G1=folder"; a blank one is fine.
    private static String validateEntry(String entry) {
        if (entry.trim().isEmpty()) {
            return "";
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
        return "";
    }

    // Reads the value into entries of {who, folder}, in order.
    private static List<String[]> read(String value) {
        List<String[]> entries = new ArrayList<>();
        if (value == null) {
            return entries;
        }
        for (String entry : value.split(ENTRY_SEPARATOR)) {
            int equals = entry.indexOf(VALUE_SEPARATOR);
            if (equals > 0) {
                String who = entry.substring(0, equals).trim();
                String folder = decode(entry.substring(equals + 1).trim());
                if (!who.isEmpty() && !folder.isEmpty()) {
                    putEntry(entries, who, folder);
                }
            }
        }
        return entries;
    }

    // Adds an entry, or replaces the folder of one written the same way,
    // so a repeated entry keeps its first place and its last folder.
    private static void putEntry(List<String[]> entries, String who,
            String folder) {
        for (String[] entry : entries) {
            if (entry[WHO].equals(who)) {
                entry[FOLDER] = folder;
                return;
            }
        }
        entries.add(new String[] {who, folder});
    }

    // Writes the entries back into one value.
    private static String write(List<String[]> entries) {
        String text = "";
        for (String[] entry : entries) {
            if (!text.isEmpty()) {
                text += ENTRY_SEPARATOR;
            }
            text += entry[WHO] + VALUE_SEPARATOR + encode(entry[FOLDER]);
        }
        return text;
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
    private static String find(List<String[]> entries, String who) {
        int index = indexOf(entries, who);
        if (index < 0) {
            return null;
        }
        return entries.get(index)[FOLDER];
    }

    // Returns where the entry for a module or section is, ignoring case,
    // or -1.
    private static int indexOf(List<String[]> entries, String who) {
        for (int index = 0; index < entries.size(); index++) {
            if (entries.get(index)[WHO].equalsIgnoreCase(who.trim())) {
                return index;
            }
        }
        return -1;
    }
}
