/*
 * File: ReviewChoices.java
 *
 * Purpose: Fills a filter's choice box with "no filter" first, then the values.
 *
 * Where it fits: Used by the filters of Review Anomalies and Validation.
 * The "no filter" choice is null and stands for "show everything".
 */
package gradezzy.ui.view;

import java.util.ArrayList;
import java.util.List;
import java.util.function.Function;

import javafx.collections.FXCollections;
import javafx.scene.control.ComboBox;
import javafx.util.StringConverter;

/**
 * Filter choices with a "no filter" entry.
 */
public final class ReviewChoices {

    // Utility class, never instantiated.
    private ReviewChoices() {
        // nothing to set up
    }

    /**
     * Fills a choice box with null, shown as noneText, then each value.
     *
     * @param <T> the type of the values
     * @param box the choice box
     * @param values the values, in order
     * @param noneText the words for null, such as "All types"
     * @param label returns the words for a value
     */
    public static <T> void fill(ComboBox<T> box, List<T> values,
            String noneText, Function<T, String> label) {
        List<T> items = new ArrayList<>();
        items.add(null);
        items.addAll(values);
        box.setItems(FXCollections.observableArrayList(items));
        box.setConverter(new ChoiceConverter<>(box, noneText, label));
    }

    /**
     * Turns a choice into words and back.
     *
     * @param <T> the type of the values
     */
    private static final class ChoiceConverter<T>
            extends StringConverter<T> {

        // The choice box whose items are read back
        private final ComboBox<T> box;

        // The words for null
        private final String noneText;

        // Returns the words for a value
        private final Function<T, String> label;

        // Creates the converter.
        ChoiceConverter(ComboBox<T> box, String noneText,
                Function<T, String> label) {
            this.box = box;
            this.noneText = noneText;
            this.label = label;
        }

        /**
         * Returns the words for a choice.
         *
         * @param value the value, or null for no filter
         * @return the words
         */
        @Override
        public String toString(T value) {
            if (value == null) {
                return noneText;
            }
            return label.apply(value);
        }

        /**
         * Reads a choice back from its words.
         *
         * @param text the words
         * @return the value, or null for no filter
         */
        @Override
        public T fromString(String text) {
            for (T value : box.getItems()) {
                if (value != null && label.apply(value).equals(text)) {
                    return value;
                }
            }
            return null;
        }
    }
}
