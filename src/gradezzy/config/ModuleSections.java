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

    // Reads the value into the modules, in the order written, and the
    // sections of each module. A module written twice keeps its first
    // place and its last sections.
    private static void read(String value, List<String> modules,
            Map<String, List<String>> sections) {
        if (value == null) {
            return;
        }
        for (String entry : split(value, ENTRY_SEPARATOR)) {
            String[] parts = entry.split(MODULE_SEPARATOR, -1);
            if (parts.length == 2 && !parts[0].trim().isEmpty()) {
                String module = parts[0].trim();
                if (!sections.containsKey(module)) {
                    modules.add(module);
                }
                sections.put(module, split(parts[1], SECTION_SEPARATOR));
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
        String text = "";
        for (String module : modules) {
            if (!text.isEmpty()) {
                text += ENTRY_SEPARATOR;
            }
            text += module + MODULE_SEPARATOR + joinSections(
                    sections.get(module));
        }
        return text;
    }

    // Joins a module's sections with the section separator.
    private static String joinSections(List<String> codes) {
        String text = "";
        for (String code : codes) {
            if (!text.isEmpty()) {
                text += SECTION_SEPARATOR;
            }
            text += code;
        }
        return text;
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
