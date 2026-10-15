/*
 * File: SettingsController.java
 *
 * Purpose: Controller of the Settings screen.
 *
 * Where it fits: Shows the settings of config.properties in grouped
 * lists, checks each typed value with the backend as it changes, and
 * saves only the changed settings. Save is unavailable while any value
 * is invalid. The theme and animations change at once and are saved with
 * the other changes. In demo mode, changes last for the session only.
 *
 * AI use: drafted with Claude Code, reviewed by Jun Sheng.
 */
package gradezzy.ui;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

import javafx.fxml.FXML;
import javafx.scene.Cursor;
import javafx.scene.control.Button;
import javafx.scene.control.Label;
import javafx.scene.control.TextField;
import javafx.scene.layout.HBox;
import javafx.scene.layout.VBox;

import atlantafx.base.controls.ToggleSwitch;
import atlantafx.base.theme.Styles;

import gradezzy.config.ConfigKeys;
import gradezzy.model.Question;
import gradezzy.ui.backend.ActionResult;
import gradezzy.ui.component.Icon;
import gradezzy.ui.component.Labels;
import gradezzy.ui.component.StatusBadge;
import gradezzy.ui.component.Tone;

/**
 * Controller of the settings page.
 *
 * <p>Tier: SHOULD. Owner: Damien.
 */
public class SettingsController extends BaseController {

    // The export format name of the score sheet
    private static final String CSV_FORMAT = "csv";

    // The export format name of the JSON export
    private static final String JSON_FORMAT = "json";

    @FXML
    private Label statusLabel;

    @FXML
    private Button saveButton;

    @FXML
    private ToggleSwitch autoFixSwitch;

    @FXML
    private Label rubricValue;

    @FXML
    private TextField runTimeoutField;

    @FXML
    private Label runTimeoutError;

    @FXML
    private TextField compileTimeoutField;

    @FXML
    private Label compileTimeoutError;

    @FXML
    private TextField threadCountField;

    @FXML
    private Label threadCountError;

    @FXML
    private ToggleSwitch untouchedSwitch;

    @FXML
    private ToggleSwitch emptySwitch;

    @FXML
    private ToggleSwitch hardCodedSwitch;

    @FXML
    private ToggleSwitch similaritySwitch;

    @FXML
    private TextField thresholdField;

    @FXML
    private Label thresholdError;

    @FXML
    private Label formatsValue;

    @FXML
    private Label lineEndingValue;

    @FXML
    private Label databaseValue;

    @FXML
    private Label backupDirValue;

    @FXML
    private TextField backupKeepField;

    @FXML
    private Label backupKeepError;

    @FXML
    private VBox lightPreview;

    @FXML
    private VBox darkPreview;

    @FXML
    private HBox lightChoice;

    @FXML
    private HBox darkChoice;

    @FXML
    private ToggleSwitch animationsSwitch;

    // The text settings on the screen, by setting name
    private final Map<String, TextField> fields = new LinkedHashMap<>();

    // The message under each text setting, by setting name
    private final Map<String, Label> errors = new HashMap<>();

    // The on and off settings on the screen, by setting name
    private final Map<String, ToggleSwitch> switches =
            new LinkedHashMap<>();

    // Shows which theme is in use, beside the Use Light button
    private final StatusBadge lightInUse = new StatusBadge("In use",
            Tone.NEUTRAL, Icon.CHECK);

    // Shows which theme is in use, beside the Use Dark button
    private final StatusBadge darkInUse = new StatusBadge("In use",
            Tone.NEUTRAL, Icon.CHECK);

    // Whether the values are being filled in by code
    private boolean loading = false;

    /**
     * Creates a new settings controller.
     */
    public SettingsController() {
        // nothing to set up yet
    }

    /**
     * Called by JavaFX after the FXML file is loaded.
     */
    @FXML
    public void initialize() {
        addField(ConfigKeys.RUN_TIMEOUT_SECONDS, runTimeoutField,
                runTimeoutError);
        addField(ConfigKeys.COMPILE_TIMEOUT_SECONDS, compileTimeoutField,
                compileTimeoutError);
        addField(ConfigKeys.THREAD_COUNT, threadCountField,
                threadCountError);
        addField(ConfigKeys.PLAGIARISM_THRESHOLD, thresholdField,
                thresholdError);
        addField(ConfigKeys.BACKUP_KEEP_COUNT, backupKeepField,
                backupKeepError);

        switches.put(ConfigKeys.AUTO_FIX_ENABLED, autoFixSwitch);
        switches.put(ConfigKeys.DETECT_UNTOUCHED_ENABLED, untouchedSwitch);
        switches.put(ConfigKeys.DETECT_EMPTY_ENABLED, emptySwitch);
        switches.put(ConfigKeys.DETECT_HARD_CODED_ENABLED, hardCodedSwitch);
        switches.put(ConfigKeys.PLAGIARISM_ENABLED, similaritySwitch);

        lightChoice.getChildren().add(lightInUse);
        darkChoice.getChildren().add(darkInUse);
        lightPreview.setCursor(Cursor.HAND);
        darkPreview.setCursor(Cursor.HAND);
    }

    /**
     * Loads the values, then checks each change as it is made.
     */
    @Override
    public void onAttach() {
        for (Map.Entry<String, TextField> entry : fields.entrySet()) {
            String key = entry.getKey();
            entry.getValue().textProperty().addListener(
                    (observable, oldValue, newValue) -> fieldChanged(key));
        }
        for (ToggleSwitch toggle : switches.values()) {
            toggle.selectedProperty().addListener(
                    (observable, oldValue, newValue) -> refreshStatus());
        }

        animationsSwitch.selectedProperty().bindBidirectional(
                getState().animationsEnabledProperty());
        animationsSwitch.selectedProperty().addListener(
                (observable, oldValue, newValue) -> refreshStatus());
        getState().darkProperty().addListener(
                (observable, oldValue, newValue) -> refreshTheme());
        lightPreview.setOnMouseClicked(event -> handleUseLight());
        darkPreview.setOnMouseClicked(event -> handleUseDark());

        loadValues();
    }

    /**
     * Keeps unsaved edits and refreshes the status each time the screen
     * is shown.
     */
    @Override
    public void onShow() {
        refreshTheme();
    }

    /**
     * Saves the changed settings, together with the theme and the
     * animation setting when they changed.
     */
    @FXML
    public void handleSave() {
        if (hasErrors()) {
            showError("Some settings are not valid. Correct the values "
                    + "with a message under them, then save again.");
            return;
        }
        Map<String, String> changes = collectChanges();
        if (changes.isEmpty()) {
            showInfo("Nothing to save.");
            return;
        }

        ActionResult result = getBackend().saveSettings(changes);
        if (showResult(result, "Settings saved")) {
            loadValues();
        }
    }

    /**
     * Discards unsaved changes, after a confirmation, and shows the
     * saved values again.
     */
    @FXML
    public void handleReset() {
        if (collectChanges().isEmpty()) {
            showInfo("Nothing to reset. Every setting matches the saved "
                    + "value.");
            return;
        }
        getShell().confirm("Discard unsaved changes?", "The changed "
                + "settings, theme and animations go back to their saved "
                + "values. Nothing is written to config.properties.",
                "Discard Changes", true, () -> {
                    resetValues();
                    showInfo("Discarded the unsaved changes.");
                });
    }

    /**
     * Switches to the light theme at once.
     */
    @FXML
    public void handleUseLight() {
        getShell().setDark(false);
    }

    /**
     * Switches to the dark theme at once.
     */
    @FXML
    public void handleUseDark() {
        getShell().setDark(true);
    }

    // Registers one text setting with the label for its messages.
    private void addField(String key, TextField field, Label error) {
        fields.put(key, field);
        errors.put(key, error);
        error.setVisible(false);
        error.setManaged(false);
    }

    // Fills every control with the saved value.
    private void loadValues() {
        loading = true;
        for (Map.Entry<String, TextField> entry : fields.entrySet()) {
            entry.getValue().setText(saved(entry.getKey()));
        }
        for (Map.Entry<String, ToggleSwitch> entry : switches.entrySet()) {
            entry.getValue().setSelected(isOn(saved(entry.getKey())));
        }
        loading = false;

        for (String key : fields.keySet()) {
            validate(key);
        }
        showReadOnlyValues();
        refreshTheme();
    }

    // Shows the saved values again, including the theme and animations.
    private void resetValues() {
        loadValues();
        String theme = saved(ConfigKeys.UI_THEME);
        if (!theme.isEmpty()) {
            getShell().setDark(ThemeManager.isDark(theme));
        }
        String animations = saved(ConfigKeys.ANIMATIONS_ENABLED);
        if (!animations.isEmpty()) {
            getState().animationsEnabledProperty().set(isOn(animations));
        }
        refreshStatus();
    }

    // Fills in the settings that cannot be changed here.
    private void showReadOnlyValues() {
        rubricValue.setText(rubricText());
        formatsValue.setText(formatsText(saved(ConfigKeys.EXPORT_FORMATS)));
        lineEndingValue.setText(saved(ConfigKeys.CSV_LINE_ENDING));
        databaseValue.setText(saved(ConfigKeys.DATABASE_FILE));
        backupDirValue.setText(saved(ConfigKeys.BACKUP_DIR));
    }

    // Checks one text setting after it changed.
    private void fieldChanged(String key) {
        if (loading) {
            return;
        }
        validate(key);
        refreshStatus();
    }

    // Asks the backend whether a text setting is valid and shows its
    // message under the field.
    private void validate(String key) {
        TextField field = fields.get(key);
        Label error = errors.get(key);
        String message = getBackend().validateSetting(key, field.getText());
        boolean invalid = !message.isEmpty();
        error.setText(message);
        error.setVisible(invalid);
        error.setManaged(invalid);
        field.pseudoClassStateChanged(Styles.STATE_DANGER, invalid);
    }

    // Says whether any text setting shows an error.
    private boolean hasErrors() {
        for (Label error : errors.values()) {
            if (error.isVisible()) {
                return true;
            }
        }
        return false;
    }

    // Returns the settings whose value differs from the saved one.
    private Map<String, String> collectChanges() {
        Map<String, String> changes = new LinkedHashMap<>();
        for (Map.Entry<String, TextField> entry : fields.entrySet()) {
            String value = entry.getValue().getText().trim();
            if (!value.equals(saved(entry.getKey()))) {
                changes.put(entry.getKey(), value);
            }
        }
        for (Map.Entry<String, ToggleSwitch> entry : switches.entrySet()) {
            boolean selected = entry.getValue().isSelected();
            if (selected != isOn(saved(entry.getKey()))) {
                changes.put(entry.getKey(), String.valueOf(selected));
            }
        }

        String theme = ThemeManager.themeName(getState().darkProperty()
                .get());
        if (!theme.equalsIgnoreCase(saved(ConfigKeys.UI_THEME))) {
            changes.put(ConfigKeys.UI_THEME, theme);
        }
        boolean animations = getState().animationsEnabledProperty().get();
        if (animations != isOn(saved(ConfigKeys.ANIMATIONS_ENABLED))) {
            changes.put(ConfigKeys.ANIMATIONS_ENABLED,
                    String.valueOf(animations));
        }
        return changes;
    }

    // Shows which theme is in use, then refreshes the status line.
    private void refreshTheme() {
        boolean dark = getState().darkProperty().get();
        lightInUse.setVisible(!dark);
        lightInUse.setManaged(!dark);
        darkInUse.setVisible(dark);
        darkInUse.setManaged(dark);
        refreshStatus();
    }

    // Says where changes are kept and how many are unsaved, and enables
    // Save only when every value is valid.
    private void refreshStatus() {
        String text;
        if (getBackend().isDemo()) {
            text = "Changes last for this demo session. config.properties "
                    + "is not changed.";
        } else {
            text = "Save writes to config.properties and keeps its "
                    + "comments.";
        }

        boolean invalid = hasErrors();
        int unsaved = collectChanges().size();
        if (invalid) {
            text += " Correct the values with a message under them before "
                    + "saving.";
        } else if (unsaved == 0) {
            text += " No unsaved changes.";
        } else {
            text += " " + unsaved + " unsaved change"
                    + Labels.plural(unsaved) + ".";
        }
        statusLabel.setText(text);
        saveButton.setDisable(invalid);
    }

    // Writes the rubric as a sum, such as "3 + 3 + 5 + 5 + 4 = 20".
    private String rubricText() {
        List<Question> questions = getBackend().getAssessment()
                .getQuestions();
        if (questions.isEmpty()) {
            return Labels.NO_MARK;
        }
        StringBuilder text = new StringBuilder();
        for (Question question : questions) {
            if (text.length() > 0) {
                text.append(" + ");
            }
            text.append(Labels.formatScore(question.getMaxScore()));
        }
        text.append(" = ");
        text.append(Labels.formatScore(getBackend().getAssessment()
                .getMaxTotal()));
        return text.toString();
    }

    // Names the export formats in plain words.
    private String formatsText(String setting) {
        List<String> names = new ArrayList<>();
        for (String part : setting.split(",")) {
            String format = part.trim().toLowerCase();
            if (format.equals(CSV_FORMAT)) {
                names.add("CSV score sheet");
            } else if (format.equals(JSON_FORMAT)) {
                names.add("JSON");
            } else if (!format.isEmpty()) {
                names.add(format.toUpperCase());
            }
        }
        if (names.isEmpty()) {
            return "None";
        }
        return String.join(" and ", names);
    }

    // Returns the saved value of a setting, without outer spaces.
    private String saved(String key) {
        return getBackend().getSetting(key).trim();
    }

    // Says whether a saved value means on.
    private boolean isOn(String value) {
        return Boolean.parseBoolean(value.trim());
    }
}
