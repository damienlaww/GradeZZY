/*
 * File: StatisticsBars.java
 *
 * Purpose: Fills the two bar lists of the Statistics screen: question
 * difficulty (the share of graded students who passed each question) and
 * anomaly frequency (how often each anomaly type occurred, relative to
 * the most frequent type), each with a one-sentence summary that also
 * handles ties.
 *
 * Where it fits: StatisticsController creates one and calls fill when
 * statistics are available. The bars are ProgressBars in the primary
 * colour, never status colours, and each has an accessible text.
 *
 * AI use: drafted with Claude Code, reviewed by Jun Sheng.
 */
package gradezzy.ui.view;

import java.util.ArrayList;
import java.util.Collections;
import java.util.Comparator;
import java.util.EnumMap;
import java.util.List;
import java.util.Map;

import javafx.geometry.Pos;
import javafx.scene.control.Label;
import javafx.scene.control.ProgressBar;
import javafx.scene.layout.HBox;
import javafx.scene.layout.Priority;
import javafx.scene.layout.VBox;

import gradezzy.model.Anomaly;
import gradezzy.model.AnomalyType;
import gradezzy.model.GradingRun;
import gradezzy.model.Question;
import gradezzy.stats.StatisticsSummary;
import gradezzy.ui.backend.DashboardBackend;
import gradezzy.ui.component.Labels;

/**
 * The question difficulty and anomaly frequency bars.
 *
 * <p>Tier: SHOULD. Owner: Damien.
 */
public final class StatisticsBars {

    // The width of a question name in the difficulty rows
    private static final double QUESTION_NAME_WIDTH = 56.0;

    // The width of an anomaly name in the frequency rows
    private static final double ANOMALY_NAME_WIDTH = 176.0;

    // The width of the number at the end of a bar row
    private static final double VALUE_WIDTH = 44.0;

    // The gap between the parts of a row built in code
    private static final double ROW_GAP = 12.0;

    // The backend, for the assessment and the anomalies
    private final DashboardBackend backend;

    // The sentence above the difficulty bars
    private final Label difficultySummary;

    // Holds one difficulty bar per question
    private final VBox difficultyRows;

    // The sentence above the frequency bars
    private final Label anomalySummary;

    // Holds one frequency bar per anomaly type
    private final VBox anomalyRows;

    /**
     * Creates the bar lists over the nodes they fill.
     *
     * @param backend the backend, for the assessment and the anomalies
     * @param difficultySummary the sentence above the difficulty bars
     * @param difficultyRows holds one difficulty bar per question
     * @param difficultyNote the note under the difficulty bars
     * @param anomalySummary the sentence above the frequency bars
     * @param anomalyRows holds one frequency bar per anomaly type
     */
    public StatisticsBars(DashboardBackend backend, Label difficultySummary,
            VBox difficultyRows, Label difficultyNote, Label anomalySummary,
            VBox anomalyRows) {
        this.backend = backend;
        this.difficultySummary = difficultySummary;
        this.difficultyRows = difficultyRows;
        this.anomalySummary = anomalySummary;
        this.anomalyRows = anomalyRows;
        difficultyNote.setText("Each bar is the share of graded students "
                + "who passed that question. A shorter bar means a harder "
                + "question.");
    }

    /**
     * Draws both bar lists and adds them to the summary text.
     *
     * @param summary the statistics
     * @param run the run of the section, or null
     * @param text the plain-text summary, added to
     */
    public void fill(StatisticsSummary summary, GradingRun run,
            StringBuilder text) {
        showDifficulty(summary, run, text);
        showFrequency(run, text);
    }

    // Joins words as "A, B and C".
    static String joinWords(List<String> words) {
        if (words.size() == 1) {
            return words.get(0);
        }
        String start = String.join(", ", words.subList(0,
                words.size() - 1));
        return start + " and " + words.get(words.size() - 1);
    }

    // Shows the share of graded students who passed each question.
    private void showDifficulty(StatisticsSummary summary, GradingRun run,
            StringBuilder text) {
        difficultyRows.getChildren().clear();
        Map<String, Double> rates = summary.getQuestionPassRates();
        List<String> ids = questionIds(run, rates);
        double lowest = Double.MAX_VALUE;
        text.append("Question pass rates:\n");
        for (String questionId : ids) {
            double rate = rates.get(questionId);
            String percent = Labels.formatPercent(rate);
            difficultyRows.getChildren().add(barRow(questionId,
                    QUESTION_NAME_WIDTH, rate, percent, questionId + ": "
                    + percent + " of graded students passed"));
            text.append("  ").append(questionId).append(": ")
                    .append(percent).append('\n');
            lowest = Math.min(lowest, rate);
        }

        if (ids.isEmpty()) {
            difficultySummary.setText("No question pass rates yet.");
            return;
        }
        List<String> hardest = new ArrayList<>();
        for (String questionId : ids) {
            if (rates.get(questionId) == lowest) {
                hardest.add(questionId);
            }
        }
        String percent = Labels.formatPercent(lowest);
        String sentence;
        if (hardest.size() == 1) {
            sentence = hardest.get(0) + " is the hardest question: "
                    + percent + " of graded students passed it.";
        } else {
            sentence = joinWords(hardest) + " are the hardest questions: "
                    + percent + " of graded students passed each.";
        }
        difficultySummary.setText(sentence);
        text.append(sentence).append('\n');
    }

    // Returns the questions with a pass rate, in assessment order.
    private List<String> questionIds(GradingRun run,
            Map<String, Double> rates) {
        List<String> ids = new ArrayList<>();
        List<Question> questions = backend.getAssessment().getQuestions();
        if (run != null) {
            questions = run.getAssessment().getQuestions();
        }
        for (Question question : questions) {
            if (rates.get(question.getId()) != null) {
                ids.add(question.getId());
            }
        }
        if (ids.isEmpty()) {
            ids.addAll(rates.keySet());
            Collections.sort(ids);
        }
        return ids;
    }

    // Shows how often each anomaly type occurred in the run.
    private void showFrequency(GradingRun run, StringBuilder text) {
        anomalyRows.getChildren().clear();
        Map<AnomalyType, Integer> counts = new EnumMap<>(AnomalyType.class);
        List<Anomaly> anomalies = backend.listAnomalies(run);
        for (Anomaly anomaly : anomalies) {
            counts.put(anomaly.getType(), counts.getOrDefault(
                    anomaly.getType(), 0) + 1);
        }
        List<TypeCount> sorted = new ArrayList<>();
        for (Map.Entry<AnomalyType, Integer> entry : counts.entrySet()) {
            sorted.add(new TypeCount(entry.getKey(), entry.getValue()));
        }
        sorted.sort(new TypeCountComparator());

        text.append("Anomalies by type:\n");
        if (sorted.isEmpty()) {
            anomalySummary.setText("No anomalies were recorded in this "
                    + "run.");
            text.append("  None\n");
            return;
        }
        int largest = sorted.get(0).count;
        for (TypeCount item : sorted) {
            String title = Labels.anomalyTitle(item.type);
            anomalyRows.getChildren().add(barRow(title, ANOMALY_NAME_WIDTH,
                    item.count / (double) largest, String.valueOf(
                    item.count), title + ": " + item.count));
            text.append("  ").append(title).append(": ").append(item.count)
                    .append('\n');
        }
        String sentence = frequencySentence(sorted, anomalies.size());
        anomalySummary.setText(sentence);
        text.append(sentence).append('\n');
    }

    // Writes one sentence about the most common anomaly types.
    private static String frequencySentence(List<TypeCount> sorted,
            int flags) {
        int largest = sorted.get(0).count;
        int tied = 0;
        for (TypeCount item : sorted) {
            if (item.count == largest) {
                tied++;
            }
        }
        String each = largest + " flag" + Labels.plural(largest);
        if (tied == 1) {
            return Labels.anomalyTitle(sorted.get(0).type) + " is the most "
                    + "common anomaly, with " + largest + " of " + flags
                    + " flags.";
        } else if (tied == sorted.size()) {
            return "No anomaly type stands out: each of the " + tied
                    + " types has " + each + ", " + flags + " flags in "
                    + "total.";
        }
        return Labels.anomalyTitle(sorted.get(0).type) + " and "
                + (tied - 1) + " other type" + Labels.plural(tied - 1)
                + " are the most common, with " + each + " each, out of "
                + flags + ".";
    }

    // Builds one row of a bar list: name, bar and value.
    private HBox barRow(String name, double nameWidth, double fraction,
            String valueText, String spoken) {
        Label nameLabel = new Label(name);
        nameLabel.setMinWidth(nameWidth);
        nameLabel.setPrefWidth(nameWidth);
        nameLabel.setWrapText(true);
        ProgressBar bar = new ProgressBar(fraction);
        bar.setMaxWidth(Double.MAX_VALUE);
        bar.setAccessibleText(spoken);
        HBox.setHgrow(bar, Priority.ALWAYS);
        Label value = new Label(valueText);
        value.setMinWidth(VALUE_WIDTH);
        value.setAlignment(Pos.CENTER_RIGHT);
        HBox row = new HBox(ROW_GAP, nameLabel, bar, value);
        row.getStyleClass().add("bar-row");
        return row;
    }

    /**
     * How often one anomaly type occurred.
     */
    private static class TypeCount {

        // The anomaly type
        private final AnomalyType type;

        // How often it occurred
        private final int count;

        // Creates a count.
        TypeCount(AnomalyType type, int count) {
            this.type = type;
            this.count = count;
        }
    }

    /**
     * Orders anomaly types by count, most frequent first, then in the
     * order of the AnomalyType enum.
     */
    private static class TypeCountComparator
            implements Comparator<TypeCount> {

        /**
         * Compares two counts.
         *
         * @param first the first count
         * @param second the second count
         * @return the order
         */
        @Override
        public int compare(TypeCount first, TypeCount second) {
            if (first.count != second.count) {
                return Integer.compare(second.count, first.count);
            }
            return first.type.compareTo(second.type);
        }
    }
}
