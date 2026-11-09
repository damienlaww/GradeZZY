/*
 * File: SettingKind.java
 *
 * Purpose: The kind of control a setting gets on the Settings tab.
 *
 * Where it fits: SettingsScreen picks one for each setting, and
 * SettingField builds the matching control.
 */
package gradezzy.ui.flow;

/**
 * The kind of control a setting gets.
 */
public enum SettingKind {
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
