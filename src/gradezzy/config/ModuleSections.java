/*
 * File: ModuleSections.java
 *
 * Purpose: Reads and writes the sections that belong to one module only,
 * kept in app.section.codes.by.module as "CS102:G5|G6;IS442:G1".
 *
 * Where it fits: The backends add these to the sections every module has
 * (app.section.codes); Home writes one when "This module only" is chosen;
 * SettingsService and GradingService check them.
 */
package gradezzy.config;

import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

/**
 * The sections of one module only.
 */
public final class ModuleSections {

    // Separates one module's entry from the next
    private static final String ENTRY_SEPARATOR = ";";

    // Separates a module from its sections
    private static final String MODULE_SEPARATOR = ":";

    // Separates the sections of one module
    private static final String SECTION_SEPARATOR = "|";

    // A module or section code: letters, digits and dashes
    private static final String CODE_PATTERN = "[A-Za-z0-9-]+";

    // Not used: every method is static.
    private ModuleSections() {
    }

    /**
     * Returns the sections every module has, then those of one module
     * only, without repeats.
     *
     * @param shared the value of app.section.codes
     * @param byModule the value of app.section.codes.by.module, or null
     * @param moduleCode the module
     * @return the sections in order
     */
    public static List<String> forModule(String shared, String byModule,
            String moduleCode) {
        List<String> codes = new ArrayList<>();
        addCodes(codes, split(shared, ","));
        List<String> own = find(read(byModule), moduleCode);
        if (own != null) {
            addCodes(codes, own);
        }
        return codes;
    }

    /**
     * Returns a module's sections: those every module has, unless the
     * module is listed as having only its own, then its own.
     *
     * @param shared the value of app.section.codes
     * @param byModule the value of app.section.codes.by.module, or null
     * @param ownOnly the value of app.module.codes.own.sections.only, or
     *        null
     * @param moduleCode the module
     * @return the sections in order
     */
    public static List<String> forModule(String shared, String byModule,
            String ownOnly, String moduleCode) {
        if (hasOwnOnly(ownOnly, moduleCode)) {
            return forModule("", byModule, moduleCode);
        }
        return forModule(shared, byModule, moduleCode);
    }

    /**
     * Says whether a module has only its own sections.
     *
     * @param ownOnly the value of app.module.codes.own.sections.only, or
     *        null
     * @param moduleCode the module
     * @return true when it does not get the sections every module has
     */
    public static boolean hasOwnOnly(String ownOnly, String moduleCode) {
        if (moduleCode == null) {
            return false;
        }
        for (String code : split(ownOnly, ",")) {
            if (code.equalsIgnoreCase(moduleCode.trim())) {
                return true;
            }
        }
        return false;
    }

    /**
     * Returns the value with one more section for one module.
     *
     * @param byModule the value now, or null
     * @param moduleCode the module
     * @param sectionCode the new section
     * @return the new value
     */
    public static String add(String byModule, String moduleCode,
            String sectionCode) {
        Map<String, List<String>> entries = read(byModule);
        List<String> sections = find(entries, moduleCode);
        if (sections == null) {
            sections = new ArrayList<>();
            entries.put(moduleCode.trim(), sections);
        }
        sections.add(sectionCode.trim());
        return write(entries);
    }

    /**
     * Returns the value without one section of one module.
     *
     * @param byModule the value now, or null
     * @param moduleCode the module
     * @param sectionCode the section
     * @return the new value
     */
    public static String remove(String byModule, String moduleCode,
            String sectionCode) {
        Map<String, List<String>> entries = read(byModule);
        List<String> sections = find(entries, moduleCode);
        if (sections != null) {
            sections.removeIf(code -> code.equalsIgnoreCase(sectionCode));
        }
        entries.values().removeIf(List::isEmpty);
        return write(entries);
    }

    /**
     * Returns the value without any section of one module, for a module
     * that is deleted.
     *
     * @param byModule the value now, or null
     * @param moduleCode the module
     * @return the new value
     */
    public static String removeModule(String byModule, String moduleCode) {
        Map<String, List<String>> entries = read(byModule);
        entries.keySet().removeIf(key -> key.equalsIgnoreCase(moduleCode));
        return write(entries);
    }

    /**
     * Says whether a section belongs to one module only.
     *
     * @param byModule the value, or null
     * @param moduleCode the module
     * @param sectionCode the section
     * @return true for a section of that module only
     */
    public static boolean isOwnSection(String byModule, String moduleCode,
            String sectionCode) {
        List<String> sections = find(read(byModule), moduleCode);
        if (sections == null) {
            return false;
        }
        for (String code : sections) {
            if (code.equalsIgnoreCase(sectionCode)) {
                return true;
            }
        }
        return false;
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
        for (String entry : split(value, ENTRY_SEPARATOR)) {
            String[] parts = entry.split(MODULE_SEPARATOR, -1);
            if (parts.length != 2 || !parts[0].trim().matches(CODE_PATTERN)) {
                return "Write each module as CS102:G5|G6, separated by ;.";
            }
            for (String section : split(parts[1], SECTION_SEPARATOR)) {
                if (!section.matches(CODE_PATTERN)) {
                    return "Use letters, digits and dashes for sections, "
                            + "such as G11.";
                }
            }
        }
        return "";
    }

    // Reads the value into each module, as written, and its sections.
    private static Map<String, List<String>> read(String value) {
        Map<String, List<String>> entries = new LinkedHashMap<>();
        if (value == null) {
            return entries;
        }
        for (String entry : split(value, ENTRY_SEPARATOR)) {
            String[] parts = entry.split(MODULE_SEPARATOR, -1);
            if (parts.length == 2 && !parts[0].trim().isEmpty()) {
                entries.put(parts[0].trim(), split(parts[1],
                        SECTION_SEPARATOR));
            }
        }
        return entries;
    }

    // Returns the sections of a module, ignoring case, or null.
    private static List<String> find(Map<String, List<String>> entries,
            String moduleCode) {
        if (moduleCode == null) {
            return null;
        }
        for (Map.Entry<String, List<String>> entry : entries.entrySet()) {
            if (entry.getKey().equalsIgnoreCase(moduleCode.trim())) {
                return entry.getValue();
            }
        }
        return null;
    }

    // Writes each module and its sections back into one value.
    private static String write(Map<String, List<String>> entries) {
        StringBuilder text = new StringBuilder();
        for (Map.Entry<String, List<String>> entry : entries.entrySet()) {
            if (text.length() > 0) {
                text.append(ENTRY_SEPARATOR);
            }
            text.append(entry.getKey()).append(MODULE_SEPARATOR)
                    .append(String.join(SECTION_SEPARATOR,
                    entry.getValue()));
        }
        return text.toString();
    }

    // Adds codes that are not listed yet, ignoring case.
    private static void addCodes(List<String> codes, List<String> more) {
        for (String code : more) {
            boolean listed = false;
            for (String existing : codes) {
                if (existing.equalsIgnoreCase(code)) {
                    listed = true;
                }
            }
            if (!listed) {
                codes.add(code);
            }
        }
    }

    // Splits a value and drops blank parts.
    private static List<String> split(String value, String separator) {
        List<String> parts = new ArrayList<>();
        if (value == null) {
            return parts;
        }
        for (String part : value.split(java.util.regex.Pattern.quote(
                separator))) {
            if (!part.trim().isEmpty()) {
                parts.add(part.trim());
            }
        }
        return parts;
    }
}
