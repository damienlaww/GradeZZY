/*
 * File: SectionView.java
 *
 * Purpose: The "One section" view of the Statistics tab: four figure
 * tiles (average, spread, passed, range), then a grid of cards: how the
 * marks spread (bell curve), where students are (status ring), the most
 * failed test, each question's average, and the common compile errors.
 *
 * Where it fits: StatisticsScreen creates one and calls show with the
 * run, its figures and the backend's summary. The grid has two columns
 * that line up in both rows; on a narrow window the bell curve takes
 * the full width and the smaller cards pair up below it.
 */
package gradezzy.ui.flow;

import java.util.ArrayList;
import java.util.List;
import java.util.Locale;
import java.util.Map;

import javafx.geometry.HPos;
import javafx.geometry.Pos;
import javafx.geometry.VPos;
import javafx.scene.Node;
import javafx.scene.control.Label;
import javafx.scene.control.Tooltip;
import javafx.scene.layout.ColumnConstraints;
import javafx.scene.layout.GridPane;
import javafx.scene.layout.HBox;
import javafx.scene.layout.Priority;
import javafx.scene.layout.Region;
import javafx.scene.layout.RowConstraints;
import javafx.scene.layout.VBox;
import javafx.scene.shape.Circle;
import javafx.scene.text.Text;
import javafx.scene.text.TextFlow;

import gradezzy.model.Assessment;
import gradezzy.model.GradingRun;
import gradezzy.model.Question;
import gradezzy.model.Submission;
import gradezzy.stats.StatisticsSummary;
import gradezzy.ui.backend.DashboardBackend;
import gradezzy.ui.backend.ResultState;

/**
 * The figures and charts of one graded section.
 */
public class SectionView {

    // The share of the width the left column takes (1.65 to 1)
    private static final double LEFT_PERCENT = 62.26;

    // The share each column takes on a narrow window
    private static final double HALF_PERCENT = 50.0;

    // Each of the four figure tiles takes a quarter of the row
    private static final double QUARTER_PERCENT = 25.0;

    // The number of figure tiles
    private static final int FIGURE_COUNT = 4;

    // Turns a fraction into a percentage
    private static final double PERCENT = 100.0;

    // Rounds a number to one decimal place
    private static final double ONE_DECIMAL = 10.0;

    // The gap between legend items
    private static final double LEGEND_GAP = 16.0;

    // The gap between a legend swatch and its name
    private static final double SWATCH_GAP = 6.0;

    // The gap between legend rows of the status ring
    private static final double RING_ROW_GAP = 8.0;

    // The gap between the ring and its legend
    private static final double RING_GAP = 16.0;

    // The radius of a legend dot
    private static final double DOT_RADIUS = 5.0;

    // The width of the percent column of the ring legend
    private static final double PERCENT_WIDTH = 36.0;

    // The width of the question id column
    private static final double ID_WIDTH = 44.0;

    // The width of the "avg / max" column
    private static final double AVERAGE_WIDTH = 74.0;

    // The width of the pass-rate column
    private static final double PASS_WIDTH = 64.0;

    // The height of a question row
    private static final double QUESTION_ROW_HEIGHT = 34.0;

    // The height of the question header row
    private static final double QUESTION_HEAD_HEIGHT = 22.0;

    // The gap between the chart and the sentence under it
    private static final double NOTE_GAP = 6.0;

    // The most compile errors listed
    private static final int MOST_ERRORS = 5;

    // Statuses in the order the ring and legend show them
    private static final ResultState[] STATE_ORDER = {
        ResultState.COMPLETED, ResultState.ZERO, ResultState.NEEDS_REVIEW,
        ResultState.COMPILE_ERROR, ResultState.TIMED_OUT,
        ResultState.COULD_NOT_GRADE, ResultState.UNGRADED};

    // The backend that knows the review decisions
    private final DashboardBackend backend;

    // The figure tiles and the grid of cards
    private final VBox view = new VBox(Parts.CARD_GAP);

    // The four figure tiles
    private final GridPane figures = new GridPane();

    // The cards, in two aligned columns
    private final GridPane grid = new GridPane();

    // The ring card above the most failed test card, on a wide window
    private final VBox rightStack = new VBox(Parts.CARD_GAP);

    // The cards; rebuilt by show
    private VBox spreadCard = new VBox();
    private VBox ringCard = new VBox();
    private VBox failedCard = new VBox();
    private VBox questionCard = new VBox();
    private VBox errorCard = new VBox();

    // True when the window is too narrow for the wide layout
    private boolean narrow = false;

    /**
     * Creates the view.
     *
     * @param backend the backend that knows the review decisions
     */
    public SectionView(DashboardBackend backend) {
        this.backend = backend;
        figures.setHgap(Parts.GAP);
        for (int index = 0; index < FIGURE_COUNT; index++) {
            figures.getColumnConstraints().add(percentColumn(
                    QUARTER_PERCENT));
        }
        grid.setHgap(Parts.CARD_GAP);
        grid.setVgap(Parts.CARD_GAP);
        view.getChildren().addAll(figures, grid);
    }

    /**
     * Returns what the view shows.
     *
     * @return the view
     */
    public Node getView() {
        return view;
    }

    /**
     * Switches between the wide layout (two columns of 1.65 to 1) and
     * the narrow one (the bell curve on its own row, then pairs).
     *
     * @param newNarrow true for the narrow layout
     */
    public void setNarrow(boolean newNarrow) {
        if (narrow == newNarrow) {
            return;
        }
        narrow = newNarrow;
        arrange();
    }

    /**
     * Fills the view for one run.
     *
     * @param run the run shown
     * @param summary the backend's summary of the run, or null when the
     *        run is not the one the backend summarises
     * @param stats the figures of the run's totals
     * @param assessment the assessment, for its questions
     */
    public void show(GradingRun run, StatisticsSummary summary,
            MarkStats stats, Assessment assessment) {
        fillFigures(stats);
        spreadCard = createSpreadCard(stats);
        ringCard = createRingCard(run);
        failedCard = createFailedCard(summary);
        questionCard = createQuestionCard(run, summary, assessment, stats);
        errorCard = createErrorCard(summary);
        arrange();
    }

    // ---------------------------------------------------------- layout

    // Places the cards in the grid for the current width.
    private void arrange() {
        grid.getChildren().clear();
        rightStack.getChildren().clear();
        grid.getColumnConstraints().clear();
        if (narrow) {
            grid.getColumnConstraints().addAll(percentColumn(HALF_PERCENT),
                    percentColumn(HALF_PERCENT));
            grid.add(spreadCard, 0, 0, 2, 1);
            grid.add(ringCard, 0, 1);
            grid.add(failedCard, 1, 1);
            grid.add(questionCard, 0, 2);
            grid.add(errorCard, 1, 2);
            return;
        }
        grid.getColumnConstraints().addAll(percentColumn(LEFT_PERCENT),
                percentColumn(PERCENT - LEFT_PERCENT));
        rightStack.getChildren().addAll(ringCard, failedCard);
        VBox.setVgrow(failedCard, Priority.ALWAYS);
        grid.add(spreadCard, 0, 0);
        grid.add(rightStack, 1, 0);
        grid.add(questionCard, 0, 1);
        grid.add(errorCard, 1, 1);
    }

    // Returns a grid column that takes a share of the width.
    private static ColumnConstraints percentColumn(double percent) {
        ColumnConstraints column = new ColumnConstraints();
        column.setPercentWidth(percent);
        column.setFillWidth(true);
        return column;
    }

    // --------------------------------------------------------- figures

    // Fills the four figure tiles.
    private void fillFigures(MarkStats stats) {
        String max = Parts.mark(stats.getMaxTotal());
        Node average = Parts.figure("Average", oneDecimal(stats.getMean()),
                "/ " + max, "Median " + Parts.mark(roundOne(
                stats.getMedian())));
        Node spread = Parts.figure("Spread", "± " + oneDecimal(
                stats.getSpread()), "marks", "Standard deviation");
        Node passed = Parts.figure("Passed", percent(stats.getPassRate()),
                null, stats.getPassCount() + " of " + stats.getCount()
                + " scored " + Parts.mark(roundOne(stats.getPassMark()))
                + " or more");
        Node range = Parts.figure("Range", Parts.mark(roundOne(
                stats.getLowest())) + " to " + Parts.mark(roundOne(
                stats.getHighest())), null, "Lowest to highest total");
        figures.getChildren().clear();
        figures.addRow(0, average, spread, passed, range);
    }

    // ---------------------------------------------------- spread card

    // Builds the bell curve card with its legend and sentence.
    private VBox createSpreadCard(MarkStats stats) {
        HBox legend = new HBox(LEGEND_GAP,
                legendItem("legend-bar", "Students per mark"),
                legendItem("legend-line", "Bell curve"),
                legendItem("legend-band", "Most students"));
        legend.setAlignment(Pos.CENTER_RIGHT);
        legend.setMinWidth(Region.USE_PREF_SIZE);

        BellCurveChart chart = new BellCurveChart(false);
        chart.setStats(stats);
        Label note = Parts.muted(spreadSentence(stats));
        note.getStyleClass().add("small-text");
        note.setWrapText(true);
        note.setMinHeight(Region.USE_PREF_SIZE);
        VBox chartBox = new VBox(NOTE_GAP, chart, note);
        VBox.setVgrow(chart, Priority.ALWAYS);
        VBox.setVgrow(chartBox, Priority.ALWAYS);
        return card(titleRow("How the marks spread", legend), chartBox);
    }

    // Returns one legend item: a small swatch and its name.
    private static HBox legendItem(String swatchClass, String text) {
        Region swatch = new Region();
        swatch.getStyleClass().addAll("legend-swatch", swatchClass);
        Label name = Parts.muted(text);
        name.getStyleClass().add("small-text");
        name.setMinWidth(Region.USE_PREF_SIZE);
        return Parts.row(SWATCH_GAP, swatch, name);
    }

    // Says in words where most students fall.
    private static String spreadSentence(MarkStats stats) {
        String hover = " Hover over a bar to see how many students got "
                + "that mark.";
        if (stats.getCount() == 1) {
            return "Only one student has a total so far." + hover;
        }
        if (stats.getSpread() <= 0) {
            return "Every student scored " + Parts.mark(roundOne(
                    stats.getMean())) + "." + hover;
        }
        double max = stats.getMaxTotal();
        long low = Math.round(Math.max(0, stats.getMean()
                - stats.getSpread()));
        long high = Math.round(Math.min(max, stats.getMean()
                + stats.getSpread()));
        return "Most students (about 2 in 3) scored between " + low
                + " and " + high + "." + hover;
    }

    // ------------------------------------------------------ ring card

    // Builds the "Where students are" card: the ring and its legend.
    private VBox createRingCard(GradingRun run) {
        int[] counts = new int[STATE_ORDER.length];
        int total = 0;
        for (Submission submission : run.getSubmissions()) {
            ResultState state = Students.state(submission, backend);
            for (int index = 0; index < STATE_ORDER.length; index++) {
                if (STATE_ORDER[index] == state) {
                    counts[index]++;
                }
            }
            total++;
        }

        StatusRing ring = new StatusRing();
        GridPane legend = new GridPane();
        legend.setHgap(Parts.GAP_SMALL);
        legend.setVgap(RING_ROW_GAP);
        ColumnConstraints dotColumn = new ColumnConstraints();
        ColumnConstraints wordColumn = new ColumnConstraints();
        wordColumn.setHgrow(Priority.ALWAYS);
        wordColumn.setMinWidth(0);
        ColumnConstraints countColumn = new ColumnConstraints();
        countColumn.setHalignment(HPos.RIGHT);
        ColumnConstraints percentColumn = new ColumnConstraints(
                PERCENT_WIDTH);
        percentColumn.setHalignment(HPos.RIGHT);
        legend.getColumnConstraints().addAll(dotColumn, wordColumn,
                countColumn, percentColumn);

        int row = 0;
        for (int index = 0; index < STATE_ORDER.length; index++) {
            if (counts[index] > 0) {
                ResultState state = STATE_ORDER[index];
                String words = Students.words(state);
                Tone tone = Students.tone(state);
                ring.addPart(words, counts[index], tone);
                addLegendRow(legend, row, words, tone, counts[index],
                        total);
                row++;
            }
        }
        legend.setMaxHeight(Region.USE_PREF_SIZE);
        HBox.setHgrow(legend, Priority.ALWAYS);
        HBox body = Parts.row(RING_GAP, ring, legend);
        return card(Parts.title("Where students are"), body);
    }

    // Adds one status to the ring legend: dot, words, count, percent.
    private static void addLegendRow(GridPane legend, int row, String words,
            Tone tone, int count, int total) {
        Circle dot = new Circle(DOT_RADIUS);
        dot.getStyleClass().addAll("ring-dot", tone.getStyleClass());
        Label name = new Label(words);
        name.setMinWidth(0);
        Label number = Parts.strong(String.valueOf(count));
        number.setMinWidth(Region.USE_PREF_SIZE);
        Label share = Parts.muted(percent((double) count / total));
        share.getStyleClass().add("small-text");
        share.setMinWidth(Region.USE_PREF_SIZE);
        legend.addRow(row, dot, name, number, share);
        GridPane.setValignment(dot, VPos.CENTER);
    }

    // ---------------------------------------------- most failed test

    // Builds the card of the test case failed by the most students.
    private VBox createFailedCard(StatisticsSummary summary) {
        if (summary == null) {
            return card(Parts.title("Most failed test"), latestOnly());
        }
        String text = summary.getMostFailedTest();
        if (text == null || text.isBlank()) {
            return card(Parts.title("Most failed test"),
                    smallMuted("No test case failed."));
        }

        // The summary reads like "Q2b case 4 (failed by 3 students)"
        String name = text;
        String detail = "";
        int open = text.lastIndexOf(" (");
        if (open > 0 && text.endsWith(")")) {
            name = text.substring(0, open);
            detail = text.substring(open + 2, text.length() - 1);
        }
        Label nameLabel = Parts.strong(name);
        nameLabel.setMinWidth(Region.USE_PREF_SIZE);
        Label detailLabel = smallMuted(detail);
        detailLabel.setMinWidth(0);
        HBox row = Parts.row(Parts.GAP, nameLabel, detailLabel);
        return card(Parts.title("Most failed test"), row);
    }

    // ----------------------------------------------------- questions

    // Builds the "Each question" card: one row per question.
    private VBox createQuestionCard(GradingRun run,
            StatisticsSummary summary, Assessment assessment,
            MarkStats stats) {
        List<Question> questions = assessment.getQuestions();
        double[] averages = QuestionFigures.averages(run.getSubmissions(),
                questions);
        boolean withPass = summary != null;

        GridPane table = new GridPane();
        table.setHgap(Parts.GAP);
        table.getColumnConstraints().add(new ColumnConstraints(ID_WIDTH));
        ColumnConstraints barColumn = new ColumnConstraints();
        barColumn.setHgrow(Priority.ALWAYS);
        barColumn.setMinWidth(0);
        table.getColumnConstraints().add(barColumn);
        table.getColumnConstraints().add(rightColumn(AVERAGE_WIDTH));
        if (withPass) {
            table.getColumnConstraints().add(rightColumn(PASS_WIDTH));
        }

        addQuestionHead(table, withPass);
        Map<String, Double> passRates = null;
        if (withPass) {
            passRates = summary.getQuestionPassRates();
        }
        for (int index = 0; index < questions.size(); index++) {
            addQuestionRow(table, index + 1, questions.get(index),
                    averages[index], passRates);
        }

        VBox card = card(Parts.title("Each question"), table);
        int hardest = QuestionFigures.hardest(averages, questions);
        if (hardest != QuestionFigures.NONE) {
            card.getChildren().add(hardestNote(questions.get(hardest),
                    averages[hardest], withPass, stats));
        }
        return card;
    }

    // Adds the small grey header row of the question table.
    private static void addQuestionHead(GridPane table, boolean withPass) {
        table.addRow(0, headLabel("Question"), headLabel("Average mark"),
                headLabel("Average"));
        if (withPass) {
            table.add(headLabel("Passed"), 3, 0);
        }
        table.getRowConstraints().add(rowHeight(QUESTION_HEAD_HEIGHT));
    }

    // Adds one question's row: id, bar, average and pass rate.
    private static void addQuestionRow(GridPane table, int row,
            Question question, double average, Map<String, Double> passRates) {
        double max = question.getMaxScore();
        double share = 0;
        if (max > 0) {
            share = average / max;
        }
        Label id = Parts.strong(question.getId());
        Label value = new Label(oneDecimal(average) + " / "
                + Parts.mark(max));
        value.setMinWidth(Region.USE_PREF_SIZE);
        table.addRow(row, id, Parts.track(share, null), value);
        if (passRates != null) {
            Double rate = passRates.get(question.getId());
            String text = "-";
            if (rate != null) {
                text = percent(rate);
            }
            Label pass = Parts.muted(text);
            pass.setMinWidth(Region.USE_PREF_SIZE);
            table.add(pass, 3, row);
        }
        table.getRowConstraints().add(rowHeight(QUESTION_ROW_HEIGHT));
    }

    // Names the hardest question and says what "Passed" means.
    private static TextFlow hardestNote(Question question, double average,
            boolean withPass, MarkStats stats) {
        Text start = noteText("Hardest: ");
        Text name = noteText(question.getId());
        name.getStyleClass().add("stats-note-strong");
        String rest = ", average " + oneDecimal(average) + " of "
                + Parts.mark(question.getMaxScore()) + ".";
        if (withPass) {
            double passPercent = 0;
            if (stats.getMaxTotal() > 0) {
                passPercent = stats.getPassMark() / stats.getMaxTotal()
                        * PERCENT;
            }
            rest += " \"Passed\" is the share of students who scored at "
                    + "least " + Parts.mark(roundOne(passPercent))
                    + "% of that question's marks.";
        }
        TextFlow note = new TextFlow(start, name, noteText(rest));
        note.getStyleClass().add("stats-note");
        return note;
    }

    // Returns a piece of the small grey note text.
    private static Text noteText(String value) {
        Text text = new Text(value);
        text.getStyleClass().add("stats-note-text");
        return text;
    }

    // Returns a small grey column heading.
    private static Label headLabel(String text) {
        Label label = smallMuted(text);
        label.setMinWidth(Region.USE_PREF_SIZE);
        return label;
    }

    // Returns a fixed-width column with its contents on the right.
    private static ColumnConstraints rightColumn(double width) {
        ColumnConstraints column = new ColumnConstraints(width);
        column.setHalignment(HPos.RIGHT);
        return column;
    }

    // Returns a grid row of a fixed height, centred vertically.
    private static RowConstraints rowHeight(double height) {
        RowConstraints row = new RowConstraints(height);
        row.setValignment(VPos.CENTER);
        return row;
    }

    // -------------------------------------------------- compile errors

    // Builds the card of the most common compile errors.
    private VBox createErrorCard(StatisticsSummary summary) {
        if (summary == null) {
            return card(Parts.title("Common compile errors"), latestOnly());
        }
        Map<String, Integer> errors = summary.getCommonCompileErrors();
        if (errors.isEmpty()) {
            return card(Parts.title("Common compile errors"),
                    smallMuted("No compile errors in this section."));
        }

        // Most common first; on a tie, in the order of the words
        List<String> reasons = new ArrayList<>(errors.keySet());
        reasons.sort((first, second) -> {
            int byCount = errors.get(second) - errors.get(first);
            if (byCount != 0) {
                return byCount;
            }
            return first.compareTo(second);
        });

        VBox list = new VBox();
        int shown = Math.min(MOST_ERRORS, reasons.size());
        for (int index = 0; index < shown; index++) {
            String reason = reasons.get(index);
            Label words = new Label(reason);
            words.setMinWidth(0);
            words.setMaxWidth(Double.MAX_VALUE);
            words.setTooltip(new Tooltip(reason));
            HBox.setHgrow(words, Priority.ALWAYS);
            Label count = Parts.strong(String.valueOf(errors.get(reason)));
            count.setMinWidth(Region.USE_PREF_SIZE);
            HBox row = Parts.row(Parts.GAP, words, count);
            row.getStyleClass().add("card-row");
            if (index == 0) {
                row.getStyleClass().add("first");
            }
            if (index == shown - 1) {
                row.getStyleClass().add("last");
            }
            list.getChildren().add(row);
        }
        return card(Parts.title("Common compile errors"), list);
    }

    // --------------------------------------------------------- helpers

    // Returns a card whose first row is a title or a title row.
    private static VBox card(Node titleRow, Node... content) {
        VBox card = Parts.card(null, titleRow);
        card.getChildren().addAll(content);
        card.getStyleClass().add("stats-card");
        card.setMinWidth(0);
        return card;
    }

    // Returns a title with something on the right of the same row.
    private static HBox titleRow(String title, Node right) {
        Label name = Parts.title(title);
        name.setMinWidth(0);
        return Parts.row(Parts.GAP, name, Parts.grow(), right);
    }

    // Says that a card only covers the latest run.
    private static Label latestOnly() {
        Label label = smallMuted("Only worked out for the latest run. "
                + "Choose it under Run to see this.");
        label.setWrapText(true);
        return label;
    }

    // Returns small grey text.
    private static Label smallMuted(String text) {
        Label label = Parts.muted(text);
        label.getStyleClass().add("small-text");
        return label;
    }

    // Formats a number to one decimal place, such as "12.4".
    static String oneDecimal(double value) {
        return String.format(Locale.ROOT, "%.1f", value);
    }

    // Formats a fraction as a whole percentage, such as "80%".
    static String percent(double fraction) {
        return Math.round(fraction * PERCENT) + "%";
    }

    // Formats a mark rounded to one decimal place, without a needless
    // ".0", such as "13" or "12.5".
    static String markText(double value) {
        return Parts.mark(roundOne(value));
    }

    // Rounds to one decimal place.
    private static double roundOne(double value) {
        return Math.round(value * ONE_DECIMAL) / ONE_DECIMAL;
    }
}
