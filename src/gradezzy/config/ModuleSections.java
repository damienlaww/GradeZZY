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
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.regex.Pattern;

import gradezzy.util.TextUtils;

/**
 * The sections of one module only.
 *
 * <p>Tier: COULD. Owner: Jing Yuan.
 */
public final class ModuleSections {

    // Separates one module's entry from the next
    private static final String ENTRY_SEPARATOR = ";";

    // Separates a module from its sections
    private static final String MODULE_SEPARATOR = ":";

    // Separates the codes in app.section.codes and
    // app.module.codes.own.sections.only
    private static final String LIST_SEPARATOR = ",";

    // Separates the sections of one module
    private static final String SECTION_SEPARATOR = "|";

    // A module or section code: letters, digits and dashes
    private static final String CODE_PATTERN = "[A-Za-z0-9-]+";

    // The parts of one entry: the module and its sections
    private static final int ENTRY_PARTS = 2;

    // Where the module is among the parts of one entry
    private static final int MODULE_PART = 0;

    // Where the sections are among the parts of one entry
    private static final int SECTIONS_PART = 1;

    // The split limit that keeps empty parts, so "CS102:" has two parts
    private static final int KEEP_EMPTY_PARTS = -1;

    // Not used: every method is static.
    private ModuleSections() {
        // utility class, never instantiated
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
        addCodes(codes, split(shared, LIST_SEPARATOR));
        List<String> modules = new ArrayList<>();
        Map<String, List<String>> sections = new HashMap<>();
        read(byModule, modules, sections);
        List<String> own = find(modules, sections, moduleCode);
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
        for (String code : split(ownOnly, LIST_SEPARATOR)) {
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
        List<String> modules = new ArrayList<>();
        Map<String, List<String>> sections = new HashMap<>();
        read(byModule, modules, sections);
        List<String> own = find(modules, sections, moduleCode);
        if (own == null) {
            own = new ArrayList<>();
            modules.add(moduleCode.trim());
            sections.put(moduleCode.trim(), own);
        }
        own.add(sectionCode.trim());
        return write(modules, sections);
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
        List<String> modules = new ArrayList<>();
        Map<String, List<String>> sections = new HashMap<>();
        read(byModule, modules, sections);
        List<String> own = find(modules, sections, moduleCode);
        if (own != null) {
            for (int index = own.size() - 1; index >= 0; index--) {
                if (own.get(index).equalsIgnoreCase(sectionCode)) {
                    own.remove(index);
                }
            }
        }
        // A module left with no section of its own is not written
        List<String> kept = new ArrayList<>();
        for (String module : modules) {
            if (!sections.get(module).isEmpty()) {
                kept.add(module);
            }
        }
        return write(kept, sections);
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
        List<String> modules = new ArrayList<>();
        Map<String, List<String>> sections = new HashMap<>();
        read(byModule, modules, sections);
        List<String> kept = new ArrayList<>();
        for (String module : modules) {
            if (!module.equalsIgnoreCase(moduleCode)) {
                kept.add(module);
            }
        }
        return write(kept, sections);
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
        List<String> modules = new ArrayList<>();
        Map<String, List<String>> sections = new HashMap<>();
        read(byModule, modules, sections);
        List<String> own = find(modules, sections, moduleCode);
        if (own == null) {
            return false;
        }
        for (String code : own) {
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
            String[] parts = entry.split(MODULE_SEPARATOR, KEEP_EMPTY_PARTS);
            boolean badEntry = parts.length != ENTRY_PARTS
                    || !parts[MODULE_PART].trim().matches(CODE_PATTERN);
            if (badEntry) {
                return "Write each module as CS102:G5|G6, separated by ;.";
            }
            List<String> entrySections = split(parts[SECTIONS_PART],
                    SECTION_SEPARATOR);
            for (String section : entrySections) {
                if (!section.matches(CODE_PATTERN)) {
                    return "Use letters, digits and dashes for sections, "
                            + "such as G11.";
                }
            }
        }
        return "";
    }

    // Reads the value into the modules, in the order written, and the
    // sections of each module. A module written twice keeps its first
    // place and its last sections.
    private static void read(String value, List<String> modules,
            Map<String, List<String>> sections) {
        if (value == null) {
            return;
        }
        for (String entry : split(value, ENTRY_SEPARATOR)) {
            String[] parts = entry.split(MODULE_SEPARATOR, KEEP_EMPTY_PARTS);
            boolean namedModule = parts.length == ENTRY_PARTS
                    && !parts[MODULE_PART].trim().isEmpty();
            if (namedModule) {
                String module = parts[MODULE_PART].trim();
                if (!sections.containsKey(module)) {
                    modules.add(module);
                }
                sections.put(module, split(parts[SECTIONS_PART],
                        SECTION_SEPARATOR));
            }
        }
    }

    // Returns the sections of a module, ignoring case, or null.
    private static List<String> find(List<String> modules,
            Map<String, List<String>> sections, String moduleCode) {
        if (moduleCode == null) {
            return null;
        }
        for (String module : modules) {
            if (module.equalsIgnoreCase(moduleCode.trim())) {
                return sections.get(module);
            }
        }
        return null;
    }

    // Writes each module and its sections back into one value.
    private static String write(List<String> modules,
            Map<String, List<String>> sections) {
        List<String> entries = new ArrayList<>();
        for (String module : modules) {
            String moduleSections = TextUtils.join(SECTION_SEPARATOR,
                    sections.get(module));
            entries.add(module + MODULE_SEPARATOR + moduleSections);
        }
        return TextUtils.join(ENTRY_SEPARATOR, entries);
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
        for (String part : value.split(Pattern.quote(separator))) {
            if (!part.trim().isEmpty()) {
                parts.add(part.trim());
            }
        }
        return parts;
    }
}
