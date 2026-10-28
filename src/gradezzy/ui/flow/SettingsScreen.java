/*
 * File: SettingsScreen.java
 *
 * Purpose: The settings an instructor is likely to change, in plain
 * groups, each checked as it is typed, saved to config.properties.
 *
 * Where it fits: Opened from the top bar over the current step. Replaces
 * the old Settings screen. Reads, checks and saves through
 * DashboardBackend; every key comes from ConfigKeys.
 */
package gradezzy.ui.flow;

import java.util.LinkedHashMap;
import java.util.Map;

import javafx.geometry.Pos;
import javafx.scene.Node;
import javafx.scene.control.CheckBox;
import javafx.scene.control.Control;
import javafx.scene.control.Label;
import javafx.scene.control.ScrollPane;
import javafx.scene.control.TextField;
import javafx.scene.layout.HBox;
import javafx.scene.layout.Priority;
import javafx.scene.layout.VBox;

import gradezzy.config.ConfigKeys;
import gradezzy.ui.ThemeManager;
import gradezzy.ui.backend.ActionResult;
import gradezzy.ui.backend.Capability;

/**
 * The Settings page.
 */
public class SettingsScreen extends FlowScreen {

    // The value of a switched-on true or false setting
    private static final String TRUE = "true";

    // The width of a text field
    private static final double FIELD_WIDTH = 320.0;

    // The scrolling page
    private final ScrollPane view = new ScrollPane();

    // The page's cards
    private final VBox column = new VBox(Parts.GAP);

    // Each setting's control, by key
    private final Map<String, Control> controls = new LinkedHashMap<>();

    // Each setting's value when the page opened, by key
    private final Map<String, String> original = new LinkedHashMap<>();

    // Each setting's problem message, by key
    private final Map<String, Label> problems = new LinkedHashMap<>();

    /**
     * Creates the Settings page.
     *
     * @param window the window
     */
    public SettingsScreen(FlowWindow window) {
        super(window);
        column.getStyleClass().add("narrow-column");
        column.getChildren().add(Parts.header("Settings", "Changes are "
                + "saved to config.properties when you press Save."));
        addGroup("General",
                text(ConfigKeys.MODULE_CODES, "Modules",
                "Separated by commas. The first is the default."),
                text(ConfigKeys.SECTION_CODES, "Sections",
                "Separated by commas, such as G1,G2,G3."),
                toggle(ConfigKeys.UI_THEME, "Dark theme"),
                toggle(ConfigKeys.ANIMATIONS_ENABLED, "Subtle animations"));
        addGroup("Files and folders",
                text(ConfigKeys.SCORE_SHEET_FILE, "Score sheet", ""),
                text(ConfigKeys.TESTERS_DIR, "Tester files folder", ""),
                text(ConfigKeys.STARTER_DIR, "Starter files folder", ""),
                text(ConfigKeys.OUTPUT_DIR, "Save exports in", ""));
        addGroup("Grading",
                text(ConfigKeys.RUN_TIMEOUT_SECONDS, "Time limit per test "
                + "run (seconds)", ""),
                text(ConfigKeys.COMPILE_TIMEOUT_SECONDS, "Time limit to "
                + "compile (seconds)", ""),
                text(ConfigKeys.THREAD_COUNT, "Students graded at once", ""),
                toggle(ConfigKeys.AUTO_FIX_ENABLED, "Fix small mistakes "
                + "automatically (you approve or undo each)"));
        addGroup("Flags",
                toggle(ConfigKeys.DETECT_UNTOUCHED_ENABLED, "Flag starter "
                + "files handed in unchanged"),
                toggle(ConfigKeys.DETECT_EMPTY_ENABLED, "Flag empty "
                + "answers"),
                toggle(ConfigKeys.DETECT_HARD_CODED_ENABLED, "Flag answers "
                + "that may be hard-coded"),
                toggle(ConfigKeys.PLAGIARISM_ENABLED, "Look for similar "
                + "code between students"),
                text(ConfigKeys.PLAGIARISM_THRESHOLD, "Similarity that "
                + "counts as similar (0 to 1)", ""));
        addGroup("Results and saved data",
                text(ConfigKeys.PASS_MARK_PERCENT, "Pass mark (percent)",
                ""),
                toggle(ConfigKeys.DATABASE_ENABLED, "Save results so runs "
                + "can be opened later"),
                toggle(ConfigKeys.AUDIT_ENABLED, "Keep an activity log"));
        VBox centre = new VBox(column);
        centre.setAlignment(Pos.TOP_CENTER);
        view.setContent(centre);
        view.setFitToWidth(true);
        view.getStyleClass().add("page-scroll");
    }

    @Override
    public Node getView() {
        return view;
    }

    @Override
    public void onShow() {
        ActionBar actions = getActions();
        actions.addSecondary(Parts.secondary("Cancel",
                () -> getWindow().closePage()));
        boolean canSave = getBackend().supports(Capability.SETTINGS);
        actions.setPrimary("Save", () -> save(), canSave);
        if (!canSave) {
            actions.setStatus("Settings cannot be saved in this mode.");
        }
    }

    // Adds a titled card of settings.
    private void addGroup(String title, Node... rows) {
        column.getChildren().add(Parts.card(title, rows));
    }

    // Builds a text setting with its label, help and problem line.
    private VBox text(String key, String label, String help) {
        TextField field = new TextField(valueOf(key));
        field.setPrefWidth(FIELD_WIDTH);
        Label problem = Parts.muted("");
        problem.getStyleClass().add("field-problem");
        field.textProperty().addListener((observable, before, after)
                -> check(key, after));
        controls.put(key, field);
        problems.put(key, problem);
        original.put(key, field.getText());

        VBox words = new VBox(Parts.strong(label));
        if (!help.isEmpty()) {
            words.getChildren().add(Parts.muted(help));
        }
        HBox.setHgrow(words, Priority.ALWAYS);
        HBox row = Parts.row(Parts.GAP, words, field);
        return new VBox(Parts.GAP_SMALL / 2, row, problem);
    }

    // Builds a true or false setting as a check box.
    private CheckBox toggle(String key, String label) {
        CheckBox box = new CheckBox(label);
        String value = valueOf(key);
        if (ConfigKeys.UI_THEME.equals(key)) {
            box.setSelected(getWindow().isDark());
            box.setOnAction(event -> getWindow().setDark(box.isSelected()));
        } else {
            box.setSelected(TRUE.equalsIgnoreCase(value));
        }
        controls.put(key, box);
        original.put(key, textOf(key, box));
        return box;
    }

    // Returns a setting's saved value, or an empty string.
    private String valueOf(String key) {
        String value = getBackend().getSetting(key);
        if (value == null) {
            return "";
        }
        return value;
    }

    // Returns the value a control holds, as it is saved.
    private String textOf(String key, Control control) {
        if (control instanceof CheckBox) {
            boolean on = ((CheckBox) control).isSelected();
            if (ConfigKeys.UI_THEME.equals(key)) {
                return on ? ThemeManager.DARK_THEME
                        : ThemeManager.LIGHT_THEME;
            }
            return String.valueOf(on);
        }
        return ((TextField) control).getText().trim();
    }

    // Shows the problem with a typed value, if any.
    private boolean check(String key, String value) {
        Label problem = problems.get(key);
        String message = getBackend().validateSetting(key, value.trim());
        if (message == null) {
            message = "";
        }
        problem.setText(message);
        return message.isEmpty();
    }

    // Saves every changed setting.
    private void save() {
        Map<String, String> changes = new LinkedHashMap<>();
        boolean valid = true;
        for (Map.Entry<String, Control> entry : controls.entrySet()) {
            String key = entry.getKey();
            String value = textOf(key, entry.getValue());
            if (problems.containsKey(key) && !check(key, value)) {
                valid = false;
            }
            if (!value.equals(original.get(key))) {
                changes.put(key, value);
            }
        }
        if (!valid) {
            getWindow().notify("Fix the settings marked in red first.");
            return;
        }
        if (changes.isEmpty()) {
            getWindow().closePage();
            return;
        }
        ActionResult result = getBackend().saveSettings(changes);
        getWindow().notify(result.getMessage());
        if (result.isDone()) {
            getWindow().closePage();
        }
    }
}
