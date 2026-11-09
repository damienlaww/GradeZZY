/*
 * File: GradingSettingsSheet.java
 *
 * Purpose: The sheet that changes the four grading settings shown on
 * Set up, without leaving Set up.
 *
 * Where it fits: SetUpScreen opens it from "Change" on its Grading
 * settings card, so the zip files already chosen stay where they are.
 */
package gradezzy.ui.flow;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

import javafx.scene.Node;
import javafx.scene.control.Button;
import javafx.scene.control.Label;
import javafx.scene.layout.HBox;
import javafx.scene.layout.VBox;

import gradezzy.config.ConfigKeys;
import gradezzy.ui.backend.ActionResult;
import gradezzy.ui.backend.Capability;

/**
 * Builds the sheet that changes the grading settings over Set up.
 */
public final class GradingSettingsSheet {

    // The widest the sheet gets
    private static final double SHEET_WIDTH = 720.0;

    // Not used: every method is static.
    private GradingSettingsSheet() {
    }

    /**
     * Opens the sheet over the current step.
     *
     * @param window the window that shows it
     * @param onSaved called after the settings are saved
     */
    public static void open(FlowWindow window, Runnable onSaved) {
        List<SettingField> fields = new ArrayList<>();
        fields.add(listed(window, ConfigKeys.RUN_TIMEOUT_SECONDS,
                "Time limit per test run", "A tester that runs longer is "
                + "stopped.", SettingsScreen.RUN_SECONDS, " seconds"));
        fields.add(listed(window, ConfigKeys.COMPILE_TIMEOUT_SECONDS,
                "Compile time limit", "For one question folder.",
                SettingsScreen.COMPILE_SECONDS, " seconds"));
        fields.add(new SettingField(window, ConfigKeys.AUTO_FIX_ENABLED,
                "Fix common mistakes", "Each fix waits for you to keep or "
                + "reject it.", SettingKind.SWITCH));
        fields.add(new SettingField(window, ConfigKeys.PLAGIARISM_ENABLED,
                "Similar code check", "Runs after grading; pairs are "
                + "flagged.", SettingKind.SWITCH));

        boolean editable = window.getBackend().supports(
                Capability.SETTINGS);
        VBox rows = new VBox();
        for (SettingField field : fields) {
            field.setEditable(editable);
            rows.getChildren().add(field.getRow());
        }

        Label problem = Parts.muted("");
        problem.getStyleClass().add("field-problem");
        problem.setVisible(false);
        problem.setManaged(false);

        Button more = Parts.secondary("All settings",
                () -> openAllSettings(window));
        Button cancel = Parts.secondary("Cancel", () -> window.closeSheet());
        Button save = Parts.primary("Save");
        save.setDisable(!editable);
        save.setOnAction(event -> save(window, fields, problem, onSaved));
        HBox buttons = Parts.row(Parts.GAP,
                new Node[] {more, Parts.grow(), cancel, save});

        Label help = Parts.subtitle("These apply to every run from now "
                + "on. The files you chose on Set up stay as they are.");
        help.setWrapText(true);
        VBox body = new VBox(Parts.GAP, Parts.title("Grading settings"),
                help, rows, problem, buttons);
        body.getStyleClass().add("grading-settings-sheet");
        body.setMaxWidth(SHEET_WIDTH);
        window.showSheet(body);
    }

    // Builds a list of values, each shown with a word after it.
    private static SettingField listed(FlowWindow window, String key,
            String name, String description, String[] values,
            String after) {
        SettingField field = new SettingField(window, key, name,
                description, SettingKind.CHOICE, values);
        String[] names = new String[values.length];
        for (int index = 0; index < values.length; index++) {
            names[index] = values[index] + after;
        }
        field.showChoicesAs(names);
        return field;
    }

    // Closes the sheet and opens the Settings page.
    private static void openAllSettings(FlowWindow window) {
        window.closeSheet();
        window.showPage(Page.SETTINGS);
    }

    // Saves the changed settings, then closes the sheet.
    private static void save(FlowWindow window, List<SettingField> fields,
            Label problem, Runnable onSaved) {
        List<String> keys = new ArrayList<>();
        Map<String, String> changes = new HashMap<>();
        for (SettingField field : fields) {
            if (field.isChanged()) {
                keys.add(field.getKey());
                changes.put(field.getKey(), field.getValue());
            }
        }
        if (changes.isEmpty()) {
            window.closeSheet();
            return;
        }
        ActionResult result = window.getBackend().saveSettings(keys,
                changes);
        if (!result.isDone()) {
            problem.setText(result.getMessage());
            problem.setVisible(true);
            problem.setManaged(true);
            return;
        }
        window.closeSheet();
        window.notify(result.getMessage());
        onSaved.run();
    }
}
