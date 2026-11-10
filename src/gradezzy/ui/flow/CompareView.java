/*
 * File: CompareView.java
 *
 * Purpose: The "Compare sections" view of the Statistics tab: a
 * labelled Sections field with one toggle chip per section of the
 * module, a chart with one bell curve per section turned on (up to
 * three), and a table of each section's figures and the date of the run
 * used.
 *
 * Where it fits: StatisticsScreen creates one and calls show with the
 * module's sections and, for each, its run of the chosen assessment.
 * Each section's colour comes from the series-1 to series-3 classes in
 * flow.css, and every curve, chip and table row also carries the
 * section's name, so colour is never the only cue. The chips stay in
 * place when one is turned on or off, so Tab and Space keep working.
 */
package gradezzy.ui.flow;

import java.time.format.DateTimeFormatter;
import java.util.ArrayList;
import java.util.List;

import javafx.geometry.Pos;
import javafx.scene.Node;
import javafx.scene.control.Label;
import javafx.scene.control.ToggleButton;
import javafx.scene.control.Tooltip;
import javafx.scene.layout.ColumnConstraints;
import javafx.scene.layout.FlowPane;
import javafx.scene.layout.GridPane;
import javafx.scene.layout.HBox;
import javafx.scene.layout.Priority;
import javafx.scene.layout.Region;
import javafx.scene.layout.StackPane;
import javafx.scene.layout.VBox;
import javafx.scene.shape.Circle;

import gradezzy.model.GradingRun;

/**
 * Up to three graded sections side by side.
 *
 * <p>Tier: SHOULD. Owner: Jing Yuan.
 */
public class CompareView {

    // The width of the section column of the table, in percent
    private static final double SECTION_PERCENT = 22.0;

    // The width of each figure column of the table, in percent
    private static final double FIGURE_PERCENT = 13.0;

    // The radius of a section's colour dot
    private static final double DOT_RADIUS = 5.0;

    // The radius of the round mark on a chip
    private static final double MARK_RADIUS = 7.0;

    // The size of the tick inside the mark of a chip that is on
    private static final double TICK_SIZE = 12.0;

    // The gap between legend items
    private static final double LEGEND_GAP = 16.0;

    // The gap between a legend line and its name
    private static final double SWATCH_GAP = 6.0;

    // The fewest sections worth comparing
    private static final int FEWEST_SERIES = 2;

    // The column of the Average figures, which are in bold
    private static final int AVERAGE_COLUMN = 1;

    // Added to the headings of the figures shown as percentages
    private static final String PERCENT_HEADING = " (%)";

    // Before the spread of a section
    private static final String PLUS_MINUS = "± ";

    // Comes between a section and its assessment in a chip's tip
    private static final String TIP_SEPARATOR = ": ";

    // The table row of the first section, under the headings
    private static final int FIRST_ROW = 1;

    // The table column of the first figure, after the section's name
    private static final int FIRST_FIGURE_COLUMN = 1;

    // Starts the style class of a series; the number counts from 1
    private static final String SERIES_PREFIX = "series-";

    // When the run of a table row was graded, such as "9 Nov, 14:05"
    private static final DateTimeFormatter WHEN =
            DateTimeFormatter.ofPattern("d MMM, HH:mm");

    // When the run of a chip was graded, such as "9 Nov 2026, 14:05"
    private static final DateTimeFormatter WHEN_LONG =
            DateTimeFormatter.ofPattern("d MMM yyyy, HH:mm");

    // The headings of the table, left to right
    private static final String[] HEADINGS = {"Section", "Students",
        "Average", "Median", "Spread", "Passed", "Hardest question"};

    // The headings that become percentages when the maximums differ
    private static final boolean[] PERCENT_COLUMNS = {false, false, true,
        true, true, false, false};

    // The sections turned on, in the order they were turned on; the list
    // is kept in FlowState so it outlives this view
    private final List<String> chosen;

    // What the view shows
    private final VBox view = new VBox(Parts.CARD_GAP);

    // The graded sections of the module, in the order of the settings
    private final List<String> graded = new ArrayList<>();

    // The run of each graded section, in the same order
    private final List<GradingRun> runs = new ArrayList<>();

    // The figures of each graded section, in the same order
    private final List<MarkStats> stats = new ArrayList<>();

    // Every section of the module, in the order of the settings
    private final List<String> order = new ArrayList<>();

    // The chip of each section, in the same order
    private final List<ToggleButton> chips = new ArrayList<>();

    // The tip of each chip, set on the box around it so it shows even
    // when the chip is turned off
    private final List<Tooltip> chipTips = new ArrayList<>();

    // The line under the chips
    private final Label hint = Parts.muted("");

    // The Sections field: its name, the chips and the hint
    private VBox sectionsField = null;

    // The assessment compared, such as Lab Test 2
    private String assessmentName = "";

    // True when the figures are percentages of each run's maximum
    private boolean percent = false;

    // Says why the figures are percentages, or empty
    private String percentNote = "";

    // Shown in place of the chart and table, or null to draw them
    private Node message = null;

    /**
     * Creates an empty view.
     *
     * <p>Tier: SHOULD. Owner: Jing Yuan.
     *
     * @param chosenSections the list that remembers the sections turned
     *        on; the view changes it as chips are turned on and off
     */
    public CompareView(List<String> chosenSections) {
        chosen = chosenSections;
        hint.getStyleClass().add("small-text");
        hint.setWrapText(true);
    }

    /**
     * Returns what the view shows.
     *
     * <p>Tier: SHOULD. Owner: Jing Yuan.
     *
     * @return the view
     */
    public Node getView() {
        return view;
    }

    /**
     * Says whether the figures given to show are percentages of each
     * run's maximum, and why. Call it before show.
     *
     * <p>Tier: SHOULD. Owner: Jing Yuan.
     *
     * @param newPercent true for percentages, false for marks
     * @param note the line under the chart that says why, or empty
     */
    public void setPercent(boolean newPercent, String note) {
        percent = newPercent;
        percentNote = note;
    }

    /**
     * Sets what is shown under the Sections field in place of the chart
     * and table, such as an empty state. Call it before show.
     *
     * <p>Tier: SHOULD. Owner: Jing Yuan.
     *
     * @param newMessage the message, or null to draw the chart and table
     */
    public void setMessage(Node newMessage) {
        message = newMessage;
    }

    /**
     * Fills the view. The sections turned on stay on while they are
     * still graded; when fewer than two are left, graded sections are
     * turned on up to three, starting with the preferred one.
     *
     * <p>Tier: SHOULD. Owner: Jing Yuan.
     *
     * @param sections every section of the module, in settings order
     * @param sectionRuns the run of each section, or null when it has
     *        no graded run of the assessment
     * @param sectionStats the figures of each section, or null
     * @param newAssessmentName the assessment compared
     * @param preferred the section to turn on first, or null
     */
    public void show(List<String> sections, List<GradingRun> sectionRuns,
            List<MarkStats> sectionStats, String newAssessmentName,
            String preferred) {
        assessmentName = newAssessmentName;
        graded.clear();
        runs.clear();
        stats.clear();
        order.clear();
        order.addAll(sections);
        for (int index = 0; index < sections.size(); index++) {
            if (sectionRuns.get(index) != null) {
                graded.add(sections.get(index));
                runs.add(sectionRuns.get(index));
                stats.add(sectionStats.get(index));
            }
        }

        keepGradedChoices();
        if (chosen.size() < FEWEST_SERIES) {
            chooseFirst(preferred);
        }
        sectionsField = createSectionsField();
        updateChips();
        showContent();
    }

    // Turns off the sections that are not graded for this assessment.
    private void keepGradedChoices() {
        List<String> kept = new ArrayList<>();
        for (String code : chosen) {
            if (graded.contains(code)) {
                kept.add(code);
            }
        }
        chosen.clear();
        chosen.addAll(kept);
    }

    // Turns on graded sections up to three, the preferred one first.
    private void chooseFirst(String preferred) {
        if (preferred != null && graded.contains(preferred)) {
            chosen.add(preferred);
        }
        for (String code : graded) {
            boolean add = chosen.size() < CompareChart.MOST_SERIES
                    && !chosen.contains(code);
            if (add) {
                chosen.add(code);
            }
        }
    }

    // Shows the Sections field above the chart and table, or above the
    // message when there is one.
    private void showContent() {
        if (message != null) {
            view.getChildren().setAll(sectionsField, message);
            return;
        }
        if (chosen.isEmpty()) {
            view.getChildren().setAll(sectionsField, createNoneCard());
            return;
        }
        view.getChildren().setAll(sectionsField, createChartCard(),
                createTable());
    }

    // ----------------------------------------------------------- chips

    // Builds the Sections field: its name, one chip per section of the
    // module and the hint under them.
    private VBox createSectionsField() {
        Label name = Parts.muted("Sections");
        name.getStyleClass().add("field-label");
        FlowPane chipRow = new FlowPane(Parts.GAP_SMALL, Parts.GAP_SMALL);
        chipRow.getStyleClass().add("compare-chips");
        chips.clear();
        chipTips.clear();
        for (String code : order) {
            chipRow.getChildren().add(createChip(code));
        }
        VBox field = new VBox(Parts.GAP_TINY, name, chipRow,
                hint);
        field.getStyleClass().add("compare-sections");
        return field;
    }

    // Returns one section's chip inside a box that carries its tip, as
    // a chip that is turned off shows no tip of its own.
    private StackPane createChip(String code) {
        ToggleButton chip = new ToggleButton(code);
        chip.getStyleClass().add("compare-chip");
        chip.setMinWidth(Region.USE_PREF_SIZE);
        chip.setOnAction(event -> toggleSection(code));
        chips.add(chip);

        StackPane box = new StackPane(chip);
        box.setMinWidth(Region.USE_PREF_SIZE);
        Tooltip tip = Parts.tooltip("");
        Tooltip.install(box, tip);
        chipTips.add(tip);
        return box;
    }

    // Turns a section on or off and draws the chart and table again.
    private void toggleSection(String code) {
        boolean full = chosen.size() >= CompareChart.MOST_SERIES;
        boolean canAdd = !full && graded.contains(code);
        if (chosen.contains(code)) {
            chosen.remove(code);
        } else if (canAdd) {
            chosen.add(code);
        }
        updateChips();
        showContent();
    }

    // Brings every chip and the hint up to date with the choice.
    private void updateChips() {
        for (int index = 0; index < order.size(); index++) {
            updateChip(index);
        }
        hint.setText(hintText());
    }

    // Brings one chip up to date: on or off, its colour, whether it can
    // be turned on, and its tip.
    private void updateChip(int index) {
        String code = order.get(index);
        ToggleButton chip = chips.get(index);
        int position = chosen.indexOf(code);
        boolean on = position >= 0;
        boolean canGrade = graded.contains(code);
        boolean full = chosen.size() >= CompareChart.MOST_SERIES;
        boolean blocked = !on && full;

        boolean locked = !canGrade || blocked;
        chip.setSelected(on);
        chip.setDisable(locked);
        for (int series = 0; series < CompareChart.MOST_SERIES; series++) {
            chip.getStyleClass().remove(seriesClass(series));
        }
        if (on) {
            chip.getStyleClass().add(seriesClass(position));
        }
        chip.setGraphic(chipMark(position));
        chipTips.get(index).setText(chipTip(code, canGrade, blocked));
    }

    // Returns the round mark of a chip: filled with the series colour
    // and ticked when on, an empty ring when off.
    private static StackPane chipMark(int position) {
        Circle circle = new Circle(MARK_RADIUS);
        circle.getStyleClass().add("chip-mark");
        StackPane mark = new StackPane(circle);
        if (position < 0) {
            circle.getStyleClass().add("off");
            return mark;
        }
        circle.getStyleClass().add(seriesClass(position));
        Node tick = Parts.icon(Parts.CHECK, TICK_SIZE);
        tick.getStyleClass().add("chip-tick");
        mark.getChildren().add(tick);
        return mark;
    }

    // Says what a chip does, or why it cannot be turned on.
    private String chipTip(String code, boolean canGrade, boolean blocked) {
        if (!canGrade) {
            return "Not graded for " + assessmentName;
        }
        if (blocked) {
            return "One chart shows up to " + CompareChart.MOST_SERIES
                    + " sections. Turn one off to show " + code + ".";
        }
        GradingRun run = runOf(code);
        String when = "date unknown";
        if (run.getStartedAt() != null) {
            when = run.getStartedAt().format(WHEN_LONG);
        }
        return code + TIP_SEPARATOR + assessmentName + ", graded " + when
                + ". Click or press Space to show or hide it.";
    }

    // Returns the line under the chips.
    private String hintText() {
        String text = "Choose " + FEWEST_SERIES + " or "
                + CompareChart.MOST_SERIES + " sections.";
        if (chosen.size() >= CompareChart.MOST_SERIES) {
            text = CompareChart.MOST_SERIES + " sections are on, the most "
                    + "one chart shows. Turn one off to choose another.";
        }
        if (graded.size() < order.size()) {
            text += " Greyed-out sections have not been graded for "
                    + assessmentName + ".";
        }
        return text;
    }

    // Returns the card shown when every section is turned off.
    private VBox createNoneCard() {
        Label title = Parts.title("No section chosen");
        Label text = Parts.muted("Turn on " + FEWEST_SERIES + " or "
                + CompareChart.MOST_SERIES + " sections above to compare "
                + "them.");
        text.setWrapText(true);
        VBox box = new VBox(Parts.GAP_SMALL, title, text);
        box.getStyleClass().addAll("flow-card", "stats-empty");
        box.setAlignment(Pos.CENTER);
        return box;
    }

    // ----------------------------------------------------------- chart

    // Builds the card with one bell curve per chosen section.
    private VBox createChartCard() {
        List<MarkStats> chosenStats = new ArrayList<>();
        HBox legend = new HBox(LEGEND_GAP);
        legend.setAlignment(Pos.CENTER_RIGHT);
        for (int index = 0; index < chosen.size(); index++) {
            chosenStats.add(statsOf(chosen.get(index)));
            Region line = new Region();
            line.getStyleClass().addAll("legend-swatch", "legend-series",
                    seriesClass(index));
            Label name = Parts.muted(chosen.get(index));
            name.getStyleClass().add("small-text");
            legend.getChildren().add(Parts.row(SWATCH_GAP,
                    new Node[] {line, name}));
        }
        CompareChart chart = new CompareChart();
        chart.setPercent(percent);
        chart.setSeries(chosen, chosenStats);

        Label title = Parts.title("How the marks spread in each section");
        title.setMinWidth(0);
        HBox titleRow = Parts.row(Parts.GAP,
                new Node[] {title, Parts.grow(), legend});
        VBox card = Parts.card(null, new Node[] {titleRow, chart});
        card.getStyleClass().add("stats-card");
        if (percent) {
            Label note = Parts.muted(percentNote);
            note.getStyleClass().add("small-text");
            note.setWrapText(true);
            card.getChildren().add(note);
        }
        return card;
    }

    // ----------------------------------------------------------- table

    // Builds the table of each chosen section's figures.
    private GridPane createTable() {
        GridPane table = new GridPane();
        table.getStyleClass().add("compare-table");
        ColumnConstraints first = new ColumnConstraints();
        first.setPercentWidth(SECTION_PERCENT);
        table.getColumnConstraints().add(first);
        for (int index = FIRST_FIGURE_COLUMN; index < HEADINGS.length;
                index++) {
            ColumnConstraints column = new ColumnConstraints();
            column.setPercentWidth(FIGURE_PERCENT);
            table.getColumnConstraints().add(column);
        }

        for (int column = 0; column < HEADINGS.length; column++) {
            Label head = cell(heading(column), column > 0);
            head.getStyleClass().add("compare-head");
            table.add(head, column, 0);
        }
        for (int index = 0; index < chosen.size(); index++) {
            addTableRow(table, index);
        }
        return table;
    }

    // Returns the heading of a column, marked when it is a percentage.
    private String heading(int column) {
        if (percent && PERCENT_COLUMNS[column]) {
            return HEADINGS[column] + PERCENT_HEADING;
        }
        return HEADINGS[column];
    }

    // Adds one section's row to the table.
    private void addTableRow(GridPane table, int index) {
        String code = chosen.get(index);
        MarkStats one = statsOf(code);
        GradingRun run = runOf(code);
        // Each run keeps the questions it was graded with
        String hardest = QuestionFigures.hardestId(run.getSubmissions(),
                run.getAssessment().getQuestions());

        int row = index + FIRST_ROW;
        table.add(sectionCell(code, index, run), 0, row);

        String[] values = {String.valueOf(one.getCount()),
            SectionView.oneDecimal(one.getMean()),
            SectionView.markText(one.getMedian()),
            PLUS_MINUS + SectionView.oneDecimal(one.getSpread()),
            SectionView.percent(one.getPassRate()), hardest};
        for (int column = 0; column < values.length; column++) {
            Label value = cell(values[column], true);
            value.getStyleClass().add("compare-cell");
            if (column == AVERAGE_COLUMN) {
                value.getStyleClass().add("strong");
            }
            int gridColumn = column + FIRST_FIGURE_COLUMN;
            table.add(value, gridColumn, row);
        }
    }

    // Returns the first cell of a row: the colour dot and the section's
    // name, and under them when its run was graded.
    private static VBox sectionCell(String code, int index, GradingRun run) {
        Label name = Parts.strong(code);
        name.setMinWidth(0);
        HBox nameRow = Parts.row(Parts.GAP_SMALL,
                new Node[] {seriesDot(index), name});
        String when = "date unknown";
        if (run.getStartedAt() != null) {
            when = run.getStartedAt().format(WHEN);
        }
        Label date = Parts.muted("graded " + when);
        date.getStyleClass().add("small-text");
        date.setMinWidth(0);

        VBox section = new VBox(nameRow, date);
        section.getStyleClass().addAll("compare-cell", "compare-first");
        section.setAlignment(Pos.CENTER_LEFT);
        section.setMaxWidth(Double.MAX_VALUE);
        section.setMaxHeight(Double.MAX_VALUE);
        return section;
    }

    // Returns one table cell that fills its grid cell.
    private static Label cell(String text, boolean right) {
        Label label = new Label(text);
        label.setMinWidth(0);
        label.setMaxWidth(Double.MAX_VALUE);
        label.setMaxHeight(Double.MAX_VALUE);
        if (right) {
            label.setAlignment(Pos.CENTER_RIGHT);
        } else {
            label.setAlignment(Pos.CENTER_LEFT);
        }
        GridPane.setHgrow(label, Priority.ALWAYS);
        return label;
    }

    // --------------------------------------------------------- helpers

    // Returns the figures of a graded section.
    private MarkStats statsOf(String code) {
        return stats.get(graded.indexOf(code));
    }

    // Returns the run of a graded section.
    private GradingRun runOf(String code) {
        return runs.get(graded.indexOf(code));
    }

    // Returns the colour dot of the n-th chosen section.
    private static Circle seriesDot(int index) {
        Circle dot = new Circle(DOT_RADIUS);
        dot.getStyleClass().addAll("series-dot", seriesClass(index));
        return dot;
    }

    // Returns the style class of the n-th chosen section, from 1.
    private static String seriesClass(int index) {
        int seriesNumber = index + 1;
        return SERIES_PREFIX + seriesNumber;
    }
}
