/*
 * File: StatisticsMistakes.java
 *
 * Purpose: Fills the Common mistakes panel of the Statistics screen: the
 * test case failed most often and every compile error message with how
 * many times it occurred, most frequent first.
 *
 * Where it fits: StatisticsController creates one and calls fill when
 * statistics are available. Both lists say so plainly when they are
 * empty.
 *
 * AI use: drafted with Claude Code, reviewed by Jun Sheng.
 */
package gradezzy.ui.view;

import java.util.ArrayList;
import java.util.Comparator;
import java.util.List;
import java.util.Map;

import javafx.geometry.Pos;
import javafx.scene.control.Label;
import javafx.scene.layout.HBox;
import javafx.scene.layout.Priority;
import javafx.scene.layout.Region;
import javafx.scene.layout.VBox;

import gradezzy.stats.StatisticsSummary;
import gradezzy.ui.component.Icon;
import gradezzy.ui.component.Labels;

/**
 * The common mistakes of the Statistics screen.
 *
 * <p>Tier: SHOULD. Owner: Damien.
 */
public final class StatisticsMistakes {

    // The gap between the parts of a row built in code
    private static final double ROW_GAP = 12.0;

    // The test case failed most often
    private final Label mostFailedLabel;

    // Holds one row per compile error message
    private final VBox compileRows;

    /**
     * Creates the panel over the nodes it fills.
     *
     * @param mostFailedLabel the test case failed most often
     * @param compileRows holds one row per compile error message
     */
    public StatisticsMistakes(Label mostFailedLabel, VBox compileRows) {
        this.mostFailedLabel = mostFailedLabel;
        this.compileRows = compileRows;
    }

    /**
     * Shows the most failed test case and the compile errors, and adds
     * them to the summary text.
     *
     * @param summary the statistics
     * @param text the plain-text summary, added to
     */
    public void fill(StatisticsSummary summary, StringBuilder text) {
        String most = summary.getMostFailedTest();
        if (most == null || most.isEmpty()) {
            mostFailedLabel.setText("No test case failed.");
        } else {
            mostFailedLabel.setText(most);
        }
        text.append("Most failed test case: ")
                .append(mostFailedLabel.getText()).append('\n');

        compileRows.getChildren().clear();
        List<Map.Entry<String, Integer>> errors = new ArrayList<>(
                summary.getCommonCompileErrors().entrySet());
        errors.sort(new ErrorCountComparator());
        text.append("Compile errors:\n");
        if (errors.isEmpty()) {
            compileRows.getChildren().add(new Label("No compile errors."));
            text.append("  None\n");
            return;
        }
        for (Map.Entry<String, Integer> error : errors) {
            int count = error.getValue();
            String times = count + " time" + Labels.plural(count);
            compileRows.getChildren().add(errorRow(error.getKey(), times));
            text.append("  ").append(error.getKey()).append(": ")
                    .append(times).append('\n');
        }
    }

    // Builds the row of one compile error: icon, message and count.
    private HBox errorRow(String messageText, String times) {
        Label message = new Label(messageText);
        message.getStyleClass().add("mono");
        message.setWrapText(true);
        message.setMaxWidth(Double.MAX_VALUE);
        HBox.setHgrow(message, Priority.ALWAYS);
        Label countLabel = new Label(times);
        countLabel.getStyleClass().add("secondary-text");
        countLabel.setMinWidth(Region.USE_PREF_SIZE);
        HBox row = new HBox(ROW_GAP, Icon.ERROR.create(), message,
                countLabel);
        row.setAlignment(Pos.CENTER_LEFT);
        return row;
    }

    /**
     * Orders compile errors by count, most frequent first, then by
     * message.
     */
    private static class ErrorCountComparator
            implements Comparator<Map.Entry<String, Integer>> {

        /**
         * Compares two compile errors.
         *
         * @param first the first error and its count
         * @param second the second error and its count
         * @return the order
         */
        @Override
        public int compare(Map.Entry<String, Integer> first,
                Map.Entry<String, Integer> second) {
            int order = Integer.compare(second.getValue(),
                    first.getValue());
            if (order != 0) {
                return order;
            }
            return first.getKey().compareTo(second.getKey());
        }
    }
}
