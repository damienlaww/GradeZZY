/*
 * File: SettingField.java
 *
 * Purpose: One row of the Settings tab: the setting's name and plain
 * description on the left, its control on the right, a blue dot when it
 * has changed and a red message under the control when the new value
 * cannot be used.
 *
 * Where it fits: SettingsScreen builds one for each setting in
 * config.properties and saves the changed ones together. Values are
 * checked with DashboardBackend.validateSetting.
 */
package gradezzy.ui.flow;

import java.io.File;
import java.nio.file.Files;
import java.nio.file.Path;

import javafx.css.PseudoClass;
import javafx.geometry.Pos;
import javafx.scene.Node;
import javafx.scene.control.Button;
import javafx.scene.control.ComboBox;
import javafx.scene.control.Label;
import javafx.scene.control.TextField;
import javafx.scene.layout.HBox;
import javafx.scene.layout.Priority;
import javafx.scene.layout.VBox;
import javafx.scene.shape.Circle;
import javafx.stage.DirectoryChooser;
import javafx.stage.FileChooser;

import atlantafx.base.controls.ToggleSwitch;

import gradezzy.config.ConfigKeys;
import gradezzy.ui.ThemeManager;

/**
 * One setting with its name, description and control.
 *
 * <p>Tier: SHOULD.
 */
public class SettingField {

    /** The width of the name column. */
    public static final double NAME_WIDTH = 260.0;

    /** The gap between the name column and the control. */
    public static final double COLUMN_GAP = 20.0;

    // Colours a switched-on switch green, as the theme offers
    private static final PseudoClass SUCCESS =
            PseudoClass.getPseudoClass("success");

    // Gives a field with a problem the theme's red border
    private static final PseudoClass DANGER =
            PseudoClass.getPseudoClass("danger");

    // The value of a switched-on setting
    private static final String TRUE = "true";

    // The value of a switched-off setting
    private static final String FALSE = "false";

    // The width of a text field
    private static final double TEXT_WIDTH = 360.0;

    // The width of a number field
    private static final double NUMBER_WIDTH = 120.0;

    // The width of a path field
    private static final double PATH_WIDTH = 300.0;

    // The width of a list of fixed values
    private static final double CHOICE_WIDTH = 240.0;

    // The radius of the blue changed dot
    private static final double DOT_RADIUS = 3.0;

    // The gap between the name and the changed dot
    private static final double DOT_GAP = 6.0;

    // The gap between the name and the description
    private static final double LINE_GAP = 2.0;

    // The gap between a control and the problem under it
    private static final double CONTROL_GAP = 4.0;

    // Separates the name, description and key in the text a search reads
    private static final String WORD_SEPARATOR = " ";

    // The quote around a setting's key in a message from the check
    private static final String QUOTE = "'";

    // The window, for file choosers and the theme
    private final FlowWindow window;

    // The setting's name in config.properties
    private final String key;

    // The name shown
    private final String name;

    // The description shown, or an empty string
    private final String description;

    // The kind of control
    private final SettingKind kind;

    // The text field, for text, number and path settings
    private final TextField field = new TextField();

    // The switch, for on and off settings
    private final ToggleSwitch toggle = new ToggleSwitch();

    // The list, for settings with fixed values
    private final ComboBox<String> choice = new ComboBox<>();

    // The Choose button of a path setting
    private Button chooseButton = null;

    // The blue dot shown when the value changed
    private final Circle dot = new Circle(DOT_RADIUS);

    // The red message under the control
    private final Label problem = new Label();

    // The whole row
    private final HBox row = new HBox(COLUMN_GAP);

    // The value saved in config.properties
    private String original;

    // Called after every change
    private Runnable onChange = null;

    /**
     * Creates a setting row with the saved value.
     *
     * @param window the window
     * @param key the setting's name in config.properties
     * @param name the name shown
     * @param description the description, or an empty string
     * @param kind the kind of control
     * @param choices the fixed values of a CHOICE setting
     */
    public SettingField(FlowWindow window, String key, String name,
            String description, SettingKind kind, String[] choices) {
        this.window = window;
        this.key = key;
        this.name = name;
        this.description = description;
        this.kind = kind;
        this.original = savedValue();
        choice.getItems().addAll(choices);
        keepHandSetChoice();
        buildRow();
        setControlValue(original);
        listen();
        update();
    }

    /**
     * Creates a setting row with the saved value, for a setting that
     * has no fixed values.
     *
     * @param window the window
     * @param key the setting's name in config.properties
     * @param name the name shown
     * @param description the description, or an empty string
     * @param kind the kind of control
     */
    public SettingField(FlowWindow window, String key, String name,
            String description, SettingKind kind) {
        this(window, key, name, description, kind, new String[0]);
    }

    /**
     * Returns the row to show.
     *
     * @return the row
     */
    public HBox getRow() {
        return row;
    }

    /**
     * Returns the setting's name in config.properties.
     *
     * @return the key
     */
    public String getKey() {
        return key;
    }

    /**
     * Returns the value the control holds now, as it would be saved.
     *
     * @return the value
     */
    public String getValue() {
        if (kind == SettingKind.SWITCH) {
            if (toggle.isSelected()) {
                return TRUE;
            }
            return FALSE;
        }
        if (kind == SettingKind.CHOICE) {
            if (choice.getValue() == null) {
                return "";
            }
            return choice.getValue();
        }
        if (kind == SettingKind.FILE || kind == SettingKind.FOLDER) {
            return Parts.cleanPath(field.getText());
        }
        return field.getText().trim();
    }

    /**
     * Puts a value in the control, as if it was typed or picked.
     *
     * @param value the value
     */
    public void setValue(String value) {
        setControlValue(value);
    }

    /**
     * Says whether the value differs from the saved one.
     *
     * @return true when changed
     */
    public boolean isChanged() {
        return !getValue().equals(original);
    }

    /**
     * Says whether the value can be saved.
     *
     * @return true when the check found no problem
     */
    public boolean isValid() {
        return problem.getText().isEmpty();
    }

    /**
     * Puts the saved value back in the control.
     */
    public void reset() {
        setControlValue(original);
    }

    /**
     * Reads the saved value again and shows it, for a setting saved
     * somewhere else since this row was built.
     */
    public void reload() {
        original = savedValue();
        keepHandSetChoice();
        setControlValue(original);
    }

    /**
     * Treats the value in the control as saved.
     */
    public void markSaved() {
        original = getValue();
        update();
    }

    /**
     * Says whether the name or description holds some text.
     *
     * @param query the text in lower case; empty matches every row
     * @return true when the row matches
     */
    public boolean matches(String query) {
        if (query.isEmpty()) {
            return true;
        }
        String words = name + WORD_SEPARATOR + description + WORD_SEPARATOR
                + key;
        String all = words.toLowerCase();
        return all.contains(query);
    }

    /**
     * Allows or stops changes.
     *
     * @param editable true to allow changes
     */
    public void setEditable(boolean editable) {
        field.setEditable(editable);
        toggle.setDisable(!editable);
        choice.setDisable(!editable);
        if (chooseButton != null) {
            chooseButton.setDisable(!editable);
        }
    }

    /**
     * Moves the keyboard focus to the setting's control.
     */
    public void focusControl() {
        if (kind == SettingKind.SWITCH) {
            toggle.requestFocus();
        } else if (kind == SettingKind.CHOICE) {
            choice.requestFocus();
        } else {
            field.requestFocus();
        }
    }

    /**
     * Shows each fixed value of a CHOICE setting under a plain name,
     * such as "Light" for "light". The value saved does not change.
     *
     * @param names one name per value, in the same order
     */
    public void showChoicesAs(String[] names) {
        choice.setConverter(new ChoiceNameConverter(choice.getItems(),
                names));
    }

    /**
     * Sets what is called after every change.
     *
     * @param action the action
     */
    public void setOnChange(Runnable action) {
        onChange = action;
    }

    // Keeps a value set by hand that is not one of the choices as one
    // more choice, so opening Settings never changes it.
    private void keepHandSetChoice() {
        boolean handSet = kind == SettingKind.CHOICE && !original.isEmpty()
                && !choice.getItems().contains(original);
        if (handSet) {
            choice.getItems().add(original);
        }
    }

    // Builds the name column and the control column.
    private void buildRow() {
        Label title = Parts.strong(name);
        title.setMinWidth(0);
        dot.getStyleClass().add("setting-dot");
        HBox nameLine = Parts.row(DOT_GAP, new Node[] {title, dot});
        VBox names = new VBox(LINE_GAP, nameLine);
        if (!description.isEmpty()) {
            Label help = Parts.muted(description);
            help.getStyleClass().add("small-text");
            help.setWrapText(true);
            names.getChildren().add(help);
        }
        names.setAlignment(Pos.CENTER_LEFT);
        names.setMinWidth(NAME_WIDTH);
        names.setPrefWidth(NAME_WIDTH);
        names.setMaxWidth(NAME_WIDTH);

        problem.getStyleClass().add("setting-problem");
        problem.setWrapText(true);
        VBox control = new VBox(CONTROL_GAP, createControl(), problem);
        control.setAlignment(Pos.CENTER_LEFT);
        HBox.setHgrow(control, Priority.ALWAYS);

        row.getChildren().addAll(names, control);
        row.setAlignment(Pos.CENTER_LEFT);
        row.getStyleClass().add("setting-row");
    }

    // Builds the control that fits the kind of setting.
    private Node createControl() {
        if (kind == SettingKind.SWITCH) {
            toggle.pseudoClassStateChanged(SUCCESS, true);
            return toggle;
        }
        if (kind == SettingKind.CHOICE) {
            choice.setPrefWidth(CHOICE_WIDTH);
            return choice;
        }
        if (kind == SettingKind.FILE || kind == SettingKind.FOLDER) {
            field.setPrefWidth(PATH_WIDTH);
            field.setMaxWidth(PATH_WIDTH);
            chooseButton = Parts.small("Choose", () -> choose());
            HBox line = Parts.row(Parts.GAP_SMALL,
                    new Node[] {field, chooseButton});
            HBox.setHgrow(field, Priority.SOMETIMES);
            return line;
        }
        double width = TEXT_WIDTH;
        if (kind == SettingKind.NUMBER) {
            width = NUMBER_WIDTH;
        }
        field.setPrefWidth(width);
        field.setMaxWidth(width);
        return field;
    }

    // Calls update after every change of the control.
    private void listen() {
        field.textProperty().addListener((observable, before, after)
                -> update());
        toggle.selectedProperty().addListener((observable, before, after)
                -> update());
        choice.valueProperty().addListener((observable, before, after)
                -> choiceChanged(after));
    }

    // Follows a new value of the list: the theme setting changes the
    // window's theme at once, then the value is checked as usual.
    private void choiceChanged(String after) {
        if (ConfigKeys.UI_THEME.equals(key)) {
            window.setDark(ThemeManager.DARK_THEME.equals(after));
        }
        update();
    }

    // Checks the value, shows the dot and the message, and reports it.
    private void update() {
        String message = "";
        if (kind != SettingKind.SWITCH && kind != SettingKind.CHOICE) {
            message = window.getBackend().validateSetting(key,
                    getValue());
        }
        if (message == null) {
            message = "";
        }
        String quotedKey = QUOTE + key + QUOTE;
        message = message.replace(quotedKey, name);
        problem.setText(message);
        boolean hasProblem = !message.isEmpty();
        problem.setVisible(hasProblem);
        problem.setManaged(hasProblem);
        field.pseudoClassStateChanged(DANGER, hasProblem);

        boolean changed = isChanged();
        dot.setVisible(changed);
        dot.setManaged(changed);
        if (onChange != null) {
            onChange.run();
        }
    }

    // Puts a value in whichever control this setting uses.
    private void setControlValue(String value) {
        if (kind == SettingKind.SWITCH) {
            toggle.setSelected(TRUE.equalsIgnoreCase(value));
        } else if (kind == SettingKind.CHOICE) {
            choice.setValue(value);
        } else {
            field.setText(value);
        }
    }

    // Returns the saved value; the theme follows the window.
    private String savedValue() {
        if (ConfigKeys.UI_THEME.equals(key)) {
            if (window.isDark()) {
                return ThemeManager.DARK_THEME;
            }
            return ThemeManager.LIGHT_THEME;
        }
        String value = window.getBackend().getSetting(key);
        if (value == null) {
            return "";
        }
        return value.trim();
    }

    // Asks for a file or folder and puts its path in the field.
    private void choose() {
        File start = startFolder();
        File picked = null;
        if (kind == SettingKind.FOLDER) {
            DirectoryChooser chooser = new DirectoryChooser();
            chooser.setTitle("Choose a folder for " + name);
            if (start != null) {
                chooser.setInitialDirectory(start);
            } else {
                Choosers.prepare(chooser);
            }
            picked = chooser.showDialog(window.getWindow());
        } else {
            FileChooser chooser = new FileChooser();
            chooser.setTitle("Choose a file for " + name);
            if (start != null) {
                chooser.setInitialDirectory(start);
            } else {
                Choosers.prepare(chooser);
            }
            picked = chooser.showOpenDialog(window.getWindow());
        }
        Choosers.remember(picked);
        if (picked != null) {
            field.setText(Parts.configPath(picked.toPath()));
        }
    }

    // Returns the folder of the current path, if it exists.
    private File startFolder() {
        String text = getValue();
        if (text.isEmpty()) {
            return null;
        }
        Path path = Parts.toPath(text);
        if (path == null) {
            return null;
        }
        path = path.toAbsolutePath();
        if (kind == SettingKind.FILE) {
            path = path.getParent();
        }
        if (path == null || !Files.isDirectory(path)) {
            return null;
        }
        return path.toFile();
    }
}
