/*
 * File: ChoiceNameConverter.java
 *
 * Purpose: Shows each fixed value of a setting under a plain name, such
 * as "Light" for "light", without changing the value that is saved.
 *
 * Where it fits: SettingField.showChoicesAs gives one to the list of a
 * CHOICE setting.
 */
package gradezzy.ui.flow;

import java.util.List;

import javafx.util.StringConverter;

/**
 * Turns the saved values of a list into the names shown, and back.
 *
 * <p>Tier: SHOULD.
 */
public class ChoiceNameConverter extends StringConverter<String> {

    // The saved values, in the order of the list
    private final List<String> values;

    // One name per value, in the same order
    private final String[] names;

    /**
     * Creates a converter for a list of values.
     *
     * @param values the saved values, in the order of the list
     * @param names one name per value, in the same order
     */
    public ChoiceNameConverter(List<String> values, String[] names) {
        this.values = values;
        this.names = names;
    }

    /**
     * Returns the name shown for a value.
     *
     * @param value the saved value
     * @return its name, or the value itself when it has none
     */
    @Override
    public String toString(String value) {
        int position = values.indexOf(value);
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
                return values.get(i);
            }
        }
        return text;
    }
}
