/*
 * File: CourseSheets.java
 *
 * Purpose: The sheets that add a module or a section, with their checks.
 *
 * Where it fits: Home (its dropdowns) and Settings (Course) both open
 * them, so a module or section is added the same way everywhere.
 */
package gradezzy.ui.flow;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.function.Consumer;
import java.util.function.Supplier;

import javafx.scene.Node;
import javafx.scene.control.Button;
import javafx.scene.control.Label;
import javafx.scene.control.RadioButton;
import javafx.scene.control.TextField;
import javafx.scene.control.ToggleGroup;
import javafx.scene.layout.VBox;

import gradezzy.config.ConfigKeys;
import gradezzy.config.ExportFolders;
import gradezzy.config.ModuleSections;
import gradezzy.ui.backend.ActionResult;
import gradezzy.util.TextUtils;

/**
 * Adds a module or a section, after checking it.
 */
public final class CourseSheets {

    // The width of the field that names a new module or section
    private static final double NEW_SECTION_WIDTH = 160.0;

    // A code is letters, digits and dashes, such as G1 or T-02
    private static final String SECTION_PATTERN = "[A-Za-z0-9-]+";

    // The longest code
    private static final int MAX_SECTION_LENGTH = 12;

    // The separator between codes in the settings
    private static final String LIST_SEPARATOR = ",";

    // Not used: every method is static.
    private CourseSheets() {
    }

    /**
     * Opens the sheet that adds a module.
     *
     * @param window the window that shows it
     * @param onAdded called with the new module's code once it is saved
     */
    public static void openAddModule(FlowWindow window,
            Consumer<String> onAdded) {
        TextField code = new TextField();
        code.setPromptText("Such as IS442");
        code.setPrefWidth(NEW_SECTION_WIDTH);
        code.setMaxWidth(NEW_SECTION_WIDTH);
        code.setAccessibleText("New module");
        Label problem = Parts.muted("");
        problem.getStyleClass().add("field-problem");
        problem.setVisible(false);
        problem.setManaged(false);
        Button cancel = Parts.secondary("Cancel",
                () -> window.closeSheet());
        Button add = Parts.primary("Add module");
        add.setOnAction(event -> addModule(window, code, problem, onAdded));
        code.setOnAction(event -> addModule(window, code, problem, onAdded));
        VBox body = new VBox(Parts.GAP, Parts.title("Add a module"),
                Parts.subtitle("A course code you teach. It starts with no "
                + "sections; add its own sections next."),
                code, problem, Parts.row(Parts.GAP, new Node[] {
                Parts.grow(), cancel, add}));
        body.setMaxWidth(Parts.TEXT_WIDTH);
        window.showSheet(body);
        code.requestFocus();
    }

    // Adds a module to the settings after checking it, and shows it.
    private static void addModule(FlowWindow window, TextField codeField,
            Label problem, Consumer<String> onAdded) {
        String code = codeField.getText().trim();
        String message = "";
        if (!code.matches(SECTION_PATTERN)
                || code.length() > MAX_SECTION_LENGTH) {
            message = "Use letters, digits and dashes only, such as IS442.";
        }
        for (String existing : window.getBackend().getModuleCodes()) {
            if (existing.equalsIgnoreCase(code)) {
                message = existing + " is already listed.";
            }
        }
        if (!message.isEmpty()) {
            problem.setText(message);
            problem.setVisible(true);
            problem.setManaged(true);
            return;
        }
        String value = window.getBackend().getSetting(ConfigKeys.MODULE_CODES)
                .trim();
        if (!value.isEmpty()) {
            value += LIST_SEPARATOR;
        }
        Map<String, String> changes = new HashMap<>();
        changes.put(ConfigKeys.MODULE_CODES, value + code);
        // A new module starts empty: no sections every module has; its
        // own are added by the instructor
        String ownOnly = window.getBackend().getSetting(
                ConfigKeys.OWN_SECTIONS_ONLY_MODULES).trim();
        if (!ownOnly.isEmpty()) {
            ownOnly += LIST_SEPARATOR;
        }
        changes.put(ConfigKeys.OWN_SECTIONS_ONLY_MODULES, ownOnly + code);
        ActionResult result = window.getBackend().saveSettings(changes);
        if (!result.isDone()) {
            problem.setText(result.getMessage());
            problem.setVisible(true);
            problem.setManaged(true);
            return;
        }
        window.closeSheet();
        window.notify("Added module " + code + ".");
        onAdded.accept(code);
    }

    /**
     * Opens the sheet that adds a section to one module or to every
     * module.
     *
     * @param window the window that shows it
     * @param module the module the section can belong to alone
     * @param onAdded called with the new section's code once it is saved
     */
    public static void openAddSection(FlowWindow window, String module,
            Consumer<String> onAdded) {
        TextField code = new TextField();
        code.setPromptText("Such as G11");
        code.setPrefWidth(NEW_SECTION_WIDTH);
        code.setMaxWidth(NEW_SECTION_WIDTH);
        code.setAccessibleText("New section");
        ToggleGroup where = new ToggleGroup();
        RadioButton onlyHere = new RadioButton("Only " + module);
        RadioButton everywhere = new RadioButton("Every module");
        onlyHere.setToggleGroup(where);
        everywhere.setToggleGroup(where);
        onlyHere.setSelected(true);
        // A module with only its own sections does not get the sections
        // every module has, so that choice would not show here
        if (ModuleSections.hasOwnOnly(window.getBackend().getSetting(
                ConfigKeys.OWN_SECTIONS_ONLY_MODULES), module)) {
            everywhere.setVisible(false);
            everywhere.setManaged(false);
        }
        Label problem = Parts.muted("");
        problem.getStyleClass().add("field-problem");
        problem.setVisible(false);
        problem.setManaged(false);

        Button cancel = Parts.secondary("Cancel",
                () -> window.closeSheet());
        Button add = Parts.primary("Add section");
        add.setOnAction(event -> addSection(window, module, code, onlyHere,
                problem, onAdded));
        code.setOnAction(event -> addSection(window, module, code, onlyHere,
                problem, onAdded));
        VBox body = new VBox(Parts.GAP, Parts.title("Add a section"),
                Parts.subtitle("A class group you teach. Its card appears "
                + "on Home and it can be picked on Set up."),
                code, new VBox(Parts.GAP_SMALL, onlyHere, everywhere),
                problem, Parts.row(Parts.GAP, new Node[] {Parts.grow(),
                cancel, add}));
        body.setMaxWidth(Parts.TEXT_WIDTH);
        window.showSheet(body);
        code.requestFocus();
    }

    // Adds a section to this module only or to every module, after
    // checking it.
    private static void addSection(FlowWindow window, String module,
            TextField codeField, RadioButton onlyHereButton, Label problem,
            Consumer<String> onAdded) {
        String code = codeField.getText().trim();
        boolean onlyHere = onlyHereButton.isSelected();
        String message = "";
        if (!code.matches(SECTION_PATTERN)
                || code.length() > MAX_SECTION_LENGTH) {
            message = "Use letters, digits and dashes only, such as G11.";
        }
        for (String existing : window.getBackend().getSectionCodes(module)) {
            if (existing.equalsIgnoreCase(code)) {
                message = existing + " is already listed for " + module
                        + ".";
            }
        }
        if (!message.isEmpty()) {
            problem.setText(message);
            problem.setVisible(true);
            problem.setManaged(true);
            return;
        }
        Map<String, String> changes = new HashMap<>();
        if (onlyHere) {
            String byModule = window.getBackend().getSetting(
                    ConfigKeys.MODULE_SECTION_CODES);
            changes.put(ConfigKeys.MODULE_SECTION_CODES, ModuleSections.add(
                    byModule, module, code));
        } else {
            String value = window.getBackend().getSetting(
                    ConfigKeys.SECTION_CODES).trim();
            if (!value.isEmpty()) {
                value += LIST_SEPARATOR;
            }
            changes.put(ConfigKeys.SECTION_CODES, value + code);
        }
        ActionResult result = window.getBackend().saveSettings(changes);
        if (!result.isDone()) {
            problem.setText(result.getMessage());
            problem.setVisible(true);
            problem.setManaged(true);
            return;
        }
        window.closeSheet();
        String scope = "every module";
        if (onlyHere) {
            scope = module + " only";
        }
        window.notify("Added section " + code + " to " + scope + ".");
        onAdded.accept(code);
    }

    /**
     * Asks before deleting a section, saying who loses it, then deletes
     * it and offers Undo. A section every module has is removed from all
     * of them; one added for a module alone is removed from that module.
     *
     * @param window the window that asks
     * @param module the module the section is shown for
     * @param code the section
     * @param onChanged called after the delete and after Undo
     */
    public static void askDeleteSection(FlowWindow window, String module,
            String code, Runnable onChanged) {
        String refused = whyNotDelete(window, module, code);
        if (!refused.isEmpty()) {
            window.notify(refused);
            return;
        }
        boolean own = ModuleSections.isOwnSection(window.getBackend()
                .getSetting(ConfigKeys.MODULE_SECTION_CODES), module, code);
        String detail = code + " is shared by every module, so it is "
                + "removed from all of them.";
        if (own) {
            detail = code + " is removed from " + module + ".";
        }
        detail += " Its score sheet and export folder are forgotten too. "
                + "Results already graded for it are kept in History, and "
                + "you can add it again later.";
        window.confirm("Delete section " + code + "?", detail,
                "Delete section", () -> deleteSection(window, module, code,
                own, onChanged));
    }

    /**
     * Says why a section cannot be deleted now: it is being graded, or
     * it is the last section every module has, which the settings need.
     *
     * @param window the window
     * @param module the module the section is shown for
     * @param code the section
     * @return the reason, or an empty text when it can be deleted
     */
    public static String whyNotDelete(FlowWindow window, String module,
            String code) {
        FlowState state = window.getState();
        if (state.isGrading()
                && code.equalsIgnoreCase(state.getSectionCode())) {
            return "Wait until " + code + " has finished grading.";
        }
        boolean own = ModuleSections.isOwnSection(window.getBackend()
                .getSetting(ConfigKeys.MODULE_SECTION_CODES), module, code);
        if (!own && sharedWithout(window, code).isEmpty()) {
            return code + " is the only section every module has, and "
                    + "GradeZZY needs one. Add another first.";
        }
        return "";
    }

    // Deletes a section with its score sheets and export folders, then
    // offers Undo.
    private static void deleteSection(FlowWindow window, String module,
            String code, boolean own, Runnable onChanged) {
        Map<String, String> changes = new HashMap<>();
        if (own) {
            String key = ConfigKeys.MODULE_SECTION_CODES;
            changes.put(key, ModuleSections.remove(window.getBackend()
                    .getSetting(key), module, code));
        } else {
            changes.put(ConfigKeys.SECTION_CODES, sharedWithout(window,
                    code));
        }
        String[] entryKeys = {ConfigKeys.SCORE_SHEET_BY_SECTION,
            ConfigKeys.OUTPUT_DIR_BY_SECTION};
        // What the section had, so Undo can put it back
        List<String[]> sheets = sectionEntries(window, entryKeys[0], module,
                code, own);
        List<String[]> folders = sectionEntries(window, entryKeys[1],
                module, code, own);
        for (String key : entryKeys) {
            String value = window.getBackend().getSetting(key);
            if (own) {
                value = ExportFolders.remove(value, ExportFolders.keyOf(
                        module, code));
            } else {
                value = ExportFolders.removeSection(value, code);
            }
            changes.put(key, value);
        }
        ActionResult result = window.getBackend().saveSettings(changes);
        if (!result.isDone()) {
            window.notify(result.getMessage());
            return;
        }
        onChanged.run();
        String detail = code + " of every module";
        if (own) {
            detail = code + " of " + module + " only";
        }
        Supplier<ActionResult> restore = ActivityUndo.record(
                window.getBackend(), ActivityUndo.SECTION_DELETED, detail,
                () -> restoreSection(window, module, code, own, sheets,
                folders));
        window.notify("Deleted section " + code + ".", "Undo",
                () -> undoDelete(window, restore, onChanged));
    }

    // Puts a deleted section back, saying so if it could not be.
    private static void undoDelete(FlowWindow window,
            Supplier<ActionResult> restore, Runnable onChanged) {
        ActionResult back = restore.get();
        if (!back.isDone()) {
            window.notify(back.getMessage());
        }
        onChanged.run();
    }

    // The sections every module has, without one, as the setting holds
    // them.
    private static String sharedWithout(FlowWindow window, String code) {
        List<String> kept = new ArrayList<>();
        String value = window.getBackend().getSetting(
                ConfigKeys.SECTION_CODES);
        for (String shared : value.split(LIST_SEPARATOR)) {
            if (!shared.trim().isEmpty()
                    && !shared.trim().equalsIgnoreCase(code)) {
                kept.add(shared.trim());
            }
        }
        return TextUtils.join(LIST_SEPARATOR, kept);
    }

    // The entries of a "CS102/G1=..." setting that belong to a section:
    // its module's alone, or every module's for a shared section.
    private static List<String[]> sectionEntries(FlowWindow window,
            String key, String module, String code, boolean own) {
        List<String[]> found = new ArrayList<>();
        String ending = ("/" + code).toLowerCase(Locale.ROOT);
        for (String[] entry : ExportFolders.list(window.getBackend()
                .getSetting(key))) {
            String who = entry[0];
            if (own && who.equalsIgnoreCase(ExportFolders.keyOf(module,
                    code))) {
                found.add(entry);
            } else if (!own && who.toLowerCase(Locale.ROOT).endsWith(
                    ending)) {
                found.add(entry);
            }
        }
        return found;
    }

    // Adds a deleted section back, with its score sheets and export
    // folders, without undoing other changes made since.
    private static ActionResult restoreSection(FlowWindow window,
            String module, String code, boolean own, List<String[]> sheets,
            List<String[]> folders) {
        Map<String, String> changes = new HashMap<>();
        putEntriesBack(window, changes, ConfigKeys.SCORE_SHEET_BY_SECTION,
                sheets);
        putEntriesBack(window, changes, ConfigKeys.OUTPUT_DIR_BY_SECTION,
                folders);
        if (own) {
            String key = ConfigKeys.MODULE_SECTION_CODES;
            String value = window.getBackend().getSetting(key);
            if (!ModuleSections.isOwnSection(value, module, code)) {
                changes.put(key, ModuleSections.add(value, module, code));
            }
        } else {
            String key = ConfigKeys.SECTION_CODES;
            String value = window.getBackend().getSetting(key).trim();
            boolean listed = false;
            for (String shared : value.split(LIST_SEPARATOR)) {
                listed = listed || shared.trim().equalsIgnoreCase(code);
            }
            if (!listed && value.isEmpty()) {
                changes.put(key, code);
            } else if (!listed) {
                changes.put(key, value + LIST_SEPARATOR + code);
            }
        }
        if (changes.isEmpty()) {
            return ActionResult.done("Section " + code + " is listed.");
        }
        return window.getBackend().saveSettings(changes);
    }

    // Adds entries back to a "CS102/G1=..." setting, keeping any set
    // since for the same module and section.
    private static void putEntriesBack(FlowWindow window,
            Map<String, String> changes, String key, List<String[]> entries) {
        if (entries.isEmpty()) {
            return;
        }
        String value = window.getBackend().getSetting(key);
        for (String[] entry : entries) {
            if (ExportFolders.exact(value, entry[0]).isEmpty()) {
                value = ExportFolders.put(value, entry[0], entry[1]);
            }
        }
        changes.put(key, value);
    }
}
