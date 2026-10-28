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
import java.nio.file.Paths;

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
import javafx.util.StringConverter;

import atlantafx.base.controls.ToggleSwitch;

import gradezzy.config.ConfigKeys;
import gradezzy.ui.ThemeManager;

/**
 * One setting with its name, description and control.
 *
 * <p>Tier: SHOULD.
 */
public class SettingField {

    /**
     * The kind of control a setting gets.
     */
    public enum Kind {
        /** A wide text field. */
        TEXT,
        /** A short text field for a number. */
        NUMBER,
        /** A path field and a Choose button that picks a file. */
        FILE,
        /** A path field and a Choose button that picks a folder. */
        FOLDER,
        /** An on and off switch. */
        SWITCH,
        /** A list of fixed values. */
        CHOICE
    }

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
    private static final double CHOICE_WIDTH = 160.0;

    // The radius of the blue changed dot
    private static final double DOT_RADIUS = 3.0;

    // The gap between the name and the changed dot
    private static final double DOT_GAP = 6.0;

    // The gap between the name and the description
    private static final double LINE_GAP = 2.0;

    // The window, for file choosers and the theme
    private final FlowWindow window;

    // The setting's name in config.properties
    private final String key;

    // The name shown
    private final String name;

    // The description shown, or an empty string
    private final String description;

    // The kind of control
    private final Kind kind;

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
     * <p>Tier: SHOULD.
     *
     * @param window the window
     * @param key the setting's name in config.properties
     * @param name the name shown
     * @param description the description, or an empty string
     * @param kind the kind of control
     * @param choices the fixed values of a CHOICE setting
     */
    public SettingField(FlowWindow window, String key, String name,
            String description, Kind kind, String... choices) {
        this.window = window;
        this.key = key;
        this.name = name;
        this.description = description;
        this.kind = kind;
        this.original = savedValue();
        choice.getItems().addAll(choices);
        buildRow();
        setControlValue(original);
        listen();
        update();
    }

    /**
     * Returns the row to show.
     *
     * <p>Tier: SHOULD.
     *
     * @return the row
     */
    public HBox getRow() {
        return row;
    }

    /**
     * Returns the setting's name in config.properties.
     *
     * <p>Tier: SHOULD.
     *
     * @return the key
     */
    public String getKey() {
        return key;
    }

    /**
     * Returns the value the control holds now, as it would be saved.
     *
     * <p>Tier: SHOULD.
     *
     * @return the value
     */
    public String getValue() {
        if (kind == Kind.SWITCH) {
            if (toggle.isSelected()) {
                return TRUE;
            }
            return FALSE;
        }
        if (kind == Kind.CHOICE) {
            if (choice.getValue() == null) {
                return "";
            }
            return choice.getValue();
        }
        return field.getText().trim();
    }

    /**
     * Puts a value in the control, as if it was typed or picked.
     *
     * <p>Tier: SHOULD.
     *
     * @param value the value
     */
    public void setValue(String value) {
        setControlValue(value);
    }

    /**
     * Says whether the value differs from the saved one.
     *
     * <p>Tier: SHOULD.
     *
     * @return true when changed
     */
    public boolean isChanged() {
        return !getValue().equals(original);
    }

    /**
     * Says whether the value can be saved.
     *
     * <p>Tier: SHOULD.
     *
     * @return true when the check found no problem
     */
    public boolean isValid() {
        return problem.getText().isEmpty();
    }

    /**
     * Puts the saved value back in the control.
     *
     * <p>Tier: SHOULD.
     */
    public void reset() {
        setControlValue(original);
    }

    /**
     * Treats the value in the control as saved.
     *
     * <p>Tier: SHOULD.
     */
    public void markSaved() {
        original = getValue();
        update();
    }

    /**
     * Says whether the name or description holds some text.
     *
     * <p>Tier: SHOULD.
     *
     * @param query the text in lower case; empty matches every row
     * @return true when the row matches
     */
    public boolean matches(String query) {
        if (query.isEmpty()) {
            return true;
        }
        String all = (name + " " + description + " " + key).toLowerCase();
        return all.contains(query);
    }

    /**
     * Allows or stops changes.
     *
     * <p>Tier: SHOULD.
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
     * Shows each fixed value of a CHOICE setting under a plain name,
     * such as "Light" for "light". The value saved does not change.
     *
     * <p>Tier: COULD.
     *
     * @param names one name per value, in the same order
     */
    public void showChoicesAs(String... names) {
        choice.setConverter(new StringConverter<String>() {

            /**
             * Returns the name shown for a value.
             *
             * @param value the saved value
             * @return its name, or the value itself when it has none
             */
            @Override
            public String toString(String value) {
                int position = choice.getItems().indexOf(value);
                if (position < 0 || position >= names.length) {
                    return value;
                }
                return names[position];
            }

            /**
             * Returns the value of a shown name.
             *
             * @param text the name
             * @return the saved value, or the text when it is no name
             */
            @Override
            public String fromString(String text) {
                for (int i = 0; i < names.length; i++) {
                    if (names[i].equals(text)) {
                        return choice.getItems().get(i);
                    }
                }
                return text;
            }
        });
    }

    /**
     * Sets what is called after every change.
     *
     * <p>Tier: SHOULD.
     *
     * @param action the action
     */
    public void setOnChange(Runnable action) {
        onChange = action;
    }

    // Builds the name column and the control column.
    private void buildRow() {
        Label title = Parts.strong(name);
        title.setMinWidth(0);
        dot.getStyleClass().add("setting-dot");
        HBox nameLine = Parts.row(DOT_GAP, title, dot);
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
        VBox control = new VBox(LINE_GAP * 2, createControl(), problem);
        control.setAlignment(Pos.CENTER_LEFT);
        HBox.setHgrow(control, Priority.ALWAYS);

        row.getChildren().addAll(names, control);
        row.setAlignment(Pos.CENTER_LEFT);
        row.getStyleClass().add("setting-row");
    }

    // Builds the control that fits the kind of setting.
    private Node createControl() {
        if (kind == Kind.SWITCH) {
            toggle.pseudoClassStateChanged(SUCCESS, true);
            return toggle;
        }
        if (kind == Kind.CHOICE) {
            choice.setPrefWidth(CHOICE_WIDTH);
            return choice;
        }
        if (kind == Kind.FILE || kind == Kind.FOLDER) {
            field.setPrefWidth(PATH_WIDTH);
            field.setMaxWidth(PATH_WIDTH);
            chooseButton = Parts.small("Choose", () -> choose());
            HBox line = Parts.row(Parts.GAP_SMALL, field, chooseButton);
            HBox.setHgrow(field, Priority.SOMETIMES);
            return line;
        }
        double width = TEXT_WIDTH;
        if (kind == Kind.NUMBER) {
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
        choice.valueProperty().addListener((observable, before, after) -> {
            if (ConfigKeys.UI_THEME.equals(key)) {
                window.setDark(ThemeManager.DARK_THEME.equals(after));
            }
            update();
        });
    }

    // Checks the value, shows the dot and the message, and reports it.
    private void update() {
        String message = "";
        if (kind != Kind.SWITCH && kind != Kind.CHOICE) {
            message = window.getBackend().validateSetting(key,
                    getValue());
        }
        if (message == null) {
            message = "";
        }
        message = message.replace("'" + key + "'", name);
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
        if (kind == Kind.SWITCH) {
            toggle.setSelected(TRUE.equalsIgnoreCase(value));
        } else if (kind == Kind.CHOICE) {
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
        if (kind == Kind.FOLDER) {
            DirectoryChooser chooser = new DirectoryChooser();
            chooser.setTitle("Choose a folder for " + name);
            if (start != null) {
                chooser.setInitialDirectory(start);
            }
            picked = chooser.showDialog(window.getWindow());
        } else {
            FileChooser chooser = new FileChooser();
            chooser.setTitle("Choose a file for " + name);
            if (start != null) {
                chooser.setInitialDirectory(start);
            }
            picked = chooser.showOpenDialog(window.getWindow());
        }
        if (picked != null) {
            field.setText(shortPath(picked.toPath()));
        }
    }

    // Returns the folder of the current path, if it exists.
    private File startFolder() {
        String text = getValue();
        if (text.isEmpty()) {
            return null;
        }
        Path path = Paths.get(text).toAbsolutePath();
        if (kind == Kind.FILE) {
            path = path.getParent();
        }
        if (path == null || !Files.isDirectory(path)) {
            return null;
        }
        return path.toFile();
    }

    // Writes a path relative to the app folder when it is inside it,
    // with forward slashes, as config.properties asks.
    private String shortPath(Path picked) {
        Path home = Paths.get("").toAbsolutePath();
        Path absolute = picked.toAbsolutePath();
        Path shown = absolute;
        if (absolute.startsWith(home)) {
            shown = home.relativize(absolute);
        }
        return shown.toString().replace('\\', '/');
    }
}
