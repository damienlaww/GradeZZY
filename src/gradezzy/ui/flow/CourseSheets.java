/*
 * File: CourseSheets.java
 *
 * Purpose: The sheets that add a module or a section, with their checks.
 *
 * Where it fits: Home (its dropdowns) and Settings (Course) both open
 * them, so a module or section is added the same way everywhere.
 */
package gradezzy.ui.flow;

import java.util.HashMap;
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
import gradezzy.config.ModuleSections;
import gradezzy.ui.backend.ActionResult;

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
        Runnable doAdd = () -> addModule(window, code.getText().trim(),
                problem, onAdded);
        add.setOnAction(event -> doAdd.run());
        code.setOnAction(event -> doAdd.run());
        VBox body = new VBox(Parts.GAP, Parts.title("Add a module"),
                Parts.subtitle("A course code you teach. It gets the "
                + "sections every module has, and you can add its own."),
                code, problem, Parts.row(Parts.GAP, new Node[] {
                Parts.grow(), cancel, add}));
        body.setMaxWidth(Parts.TEXT_WIDTH);
        window.showSheet(body);
        code.requestFocus();
    }

    // Adds a module to the settings after checking it, and shows it.
    private static void addModule(FlowWindow window, String code,
            Label problem, Consumer<String> onAdded) {
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
        if (window.getBackend().validateSetting(
                ConfigKeys.OWN_SECTIONS_ONLY_MODULES, "").isEmpty()) {
            String ownOnly = window.getBackend().getSetting(
                    ConfigKeys.OWN_SECTIONS_ONLY_MODULES).trim();
            changes.put(ConfigKeys.OWN_SECTIONS_ONLY_MODULES,
                    ownOnly.isEmpty() ? code : ownOnly + LIST_SEPARATOR
                    + code);
        }
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
        // An older config.properties has no setting for one module's sections
        boolean canOnlyHere = window.getBackend().validateSetting(
                ConfigKeys.MODULE_SECTION_CODES, "").isEmpty();
        onlyHere.setDisable(!canOnlyHere);
        if (!canOnlyHere) {
            everywhere.setSelected(true);
        }
        // A module with only its own sections does not get the sections
        // every module has, so that choice would not show here
        if (ModuleSections.hasOwnOnly(window.getBackend().getSetting(
                ConfigKeys.OWN_SECTIONS_ONLY_MODULES), module)) {
            everywhere.setVisible(false);
            everywhere.setManaged(false);
            onlyHere.setSelected(true);
        }
        Label problem = Parts.muted("");
        problem.getStyleClass().add("field-problem");
        problem.setVisible(false);
        problem.setManaged(false);

        Button cancel = Parts.secondary("Cancel",
                () -> window.closeSheet());
        Button add = Parts.primary("Add section");
        Runnable doAdd = () -> addSection(window, module,
                code.getText().trim(), onlyHere.isSelected(), problem, onAdded);
        add.setOnAction(event -> doAdd.run());
        code.setOnAction(event -> doAdd.run());
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
            String code, boolean onlyHere, Label problem,
            Consumer<String> onAdded) {
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
        boolean own = ModuleSections.isOwnSection(window.getBackend()
                .getSetting(ConfigKeys.MODULE_SECTION_CODES), module, code);
        String detail = own ? code + " is removed from " + module + "."
                : code + " is shared by every module, so it is removed from "
                + "all of them.";
        detail += " Results already graded for it are kept in History, and you "
                + "can add it again later.";
        window.confirm("Delete section " + code + "?", detail,
                "Delete section", () -> deleteSection(window, module, code,
                own, onChanged));
    }

    // Deletes a section, then offers Undo.
    private static void deleteSection(FlowWindow window, String module,
            String code, boolean own, Runnable onChanged) {
        Map<String, String> changes = new HashMap<>();
        Map<String, String> before = new HashMap<>();
        if (own) {
            String key = ConfigKeys.MODULE_SECTION_CODES;
            before.put(key, window.getBackend().getSetting(key));
            changes.put(key, ModuleSections.remove(before.get(key), module,
                    code));
        } else {
            String key = ConfigKeys.SECTION_CODES;
            before.put(key, window.getBackend().getSetting(key));
            StringBuilder kept = new StringBuilder();
            for (String shared : before.get(key).split(LIST_SEPARATOR)) {
                if (!shared.trim().isEmpty()
                        && !shared.trim().equalsIgnoreCase(code)) {
                    if (kept.length() > 0) {
                        kept.append(LIST_SEPARATOR);
                    }
                    kept.append(shared.trim());
                }
            }
            changes.put(key, kept.toString());
        }
        ActionResult result = window.getBackend().saveSettings(changes);
        if (!result.isDone()) {
            window.notify(result.getMessage());
            return;
        }
        onChanged.run();
        String detail = own ? code + " of " + module + " only"
                : code + " of every module";
        Supplier<ActionResult> restore = ActivityUndo.record(
                window.getBackend(), ActivityUndo.SECTION_DELETED, detail,
                () -> restoreSection(window, module, code, own));
        window.notify("Deleted section " + code + ".", "Undo", () -> {
            ActionResult back = restore.get();
            if (!back.isDone()) {
                window.notify(back.getMessage());
            }
            onChanged.run();
        });
    }

    // Adds a deleted section back, without undoing other changes made
    // since.
    private static ActionResult restoreSection(FlowWindow window,
            String module, String code, boolean own) {
        Map<String, String> changes = new HashMap<>();
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
            if (!listed) {
                changes.put(key, value.isEmpty() ? code
                        : value + LIST_SEPARATOR + code);
            }
        }
        if (changes.isEmpty()) {
            return ActionResult.done("Section " + code + " is listed.");
        }
        return window.getBackend().saveSettings(changes);
    }
}
