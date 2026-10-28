/*
 * File: CompareView.java
 *
 * Purpose: The "Compare sections" view of the Statistics tab: chips for
 * up to three graded sections (remove one with its cross, add one from
 * "+ Add section"), a chart with one bell curve per section, and a
 * table of each section's figures.
 *
 * Where it fits: StatisticsScreen creates one and calls show with the
 * module and its graded sections. Each section's colour comes from the
 * series-1 to series-3 classes in flow.css, and every curve, chip and
 * table row also carries the section's name, so colour is never the
 * only cue.
 */
package gradezzy.ui.flow;

import java.util.ArrayList;
import java.util.List;

import javafx.geometry.Insets;
import javafx.geometry.Pos;
import javafx.scene.Node;
import javafx.scene.control.Button;
import javafx.scene.control.Label;
import javafx.scene.control.MenuButton;
import javafx.scene.control.MenuItem;
import javafx.scene.control.Tooltip;
import javafx.scene.layout.ColumnConstraints;
import javafx.scene.layout.GridPane;
import javafx.scene.layout.HBox;
import javafx.scene.layout.Priority;
import javafx.scene.layout.Region;
import javafx.scene.layout.VBox;
import javafx.scene.shape.Circle;

import gradezzy.model.Assessment;
import gradezzy.model.GradingRun;

/**
 * Up to three graded sections side by side.
 */
public class CompareView {

    // The width of the section column of the table, in percent
    private static final double SECTION_PERCENT = 22.0;

    // The width of each figure column of the table, in percent
    private static final double FIGURE_PERCENT = 13.0;

    // The radius of a section's colour dot
    private static final double DOT_RADIUS = 5.0;

    // The size of the cross on a chip
    private static final double CROSS_SIZE = 12.0;

    // The gap between legend items
    private static final double LEGEND_GAP = 16.0;

    // The gap between a legend line and its name
    private static final double SWATCH_GAP = 6.0;

    // The headings of the table, left to right
    private static final String[] HEADINGS = {"Section", "Students",
        "Average", "Median", "Spread", "Passed", "Hardest question"};

    // The sections chosen, in the order they were added
    private final List<String> chosen = new ArrayList<>();

    // What the view shows
    private final VBox view = new VBox(Parts.CARD_GAP);

    // The graded sections of the module, in the order of the settings
    private final List<String> graded = new ArrayList<>();

    // The run of each graded section, in the same order
    private final List<GradingRun> runs = new ArrayList<>();

    // The figures of each graded section, in the same order
    private final List<MarkStats> stats = new ArrayList<>();

    // The sections not graded yet, in the order of the settings
    private final List<String> notGraded = new ArrayList<>();

    // Every section of the module, in the order of the settings
    private final List<String> order = new ArrayList<>();

    // The assessment, for its questions
    private Assessment assessment = null;

    /**
     * Returns what the view shows.
     *
     * @return the view
     */
    public Node getView() {
        return view;
    }

    /**
     * Returns the sections chosen, for the developer checks.
     *
     * @return a copy of the chosen section codes
     */
    public List<String> getChosen() {
        return new ArrayList<>(chosen);
    }

    /**
     * Fills the view. The chosen sections are kept while they are still
     * graded; when none is, the first graded sections are chosen,
     * starting with the preferred one.
     *
     * @param sections every section of the module, in settings order
     * @param sectionRuns the run of each section, or null when it has
     *        no marks yet
     * @param sectionStats the figures of each section, or null
     * @param newAssessment the assessment
     * @param preferred the section to choose first, or null
     */
    public void show(List<String> sections, List<GradingRun> sectionRuns,
            List<MarkStats> sectionStats, Assessment newAssessment,
            String preferred) {
        assessment = newAssessment;
        graded.clear();
        runs.clear();
        stats.clear();
        notGraded.clear();
        order.clear();
        order.addAll(sections);
        for (int index = 0; index < sections.size(); index++) {
            if (sectionRuns.get(index) == null) {
                notGraded.add(sections.get(index));
            } else {
                graded.add(sections.get(index));
                runs.add(sectionRuns.get(index));
                stats.add(sectionStats.get(index));
            }
        }

        List<String> kept = new ArrayList<>();
        for (String code : chosen) {
            if (graded.contains(code)) {
                kept.add(code);
            }
        }
        chosen.clear();
        chosen.addAll(kept);
        if (chosen.isEmpty()) {
            chooseFirst(preferred);
        }
        rebuild();
    }

    /**
     * Says how many sections of the module have marks.
     *
     * @return the count
     */
    public int getGradedCount() {
        return graded.size();
    }

    // Chooses up to three graded sections, the preferred one first.
    private void chooseFirst(String preferred) {
        if (preferred != null && graded.contains(preferred)) {
            chosen.add(preferred);
        }
        for (String code : graded) {
            if (chosen.size() < CompareChart.MOST_SERIES
                    && !chosen.contains(code)) {
                chosen.add(code);
            }
        }
    }

    // Builds the chips, the chart and the table again.
    private void rebuild() {
        view.getChildren().setAll(createChips(), createChartCard(),
                createTable());
    }

    // ----------------------------------------------------------- chips

    // Builds the row of chosen sections, the add menu and the note.
    private HBox createChips() {
        Label intro = Parts.muted("Compare up to "
                + CompareChart.MOST_SERIES + ":");
        intro.getStyleClass().add("small-text");
        intro.setMinWidth(Region.USE_PREF_SIZE);
        HBox row = Parts.row(Parts.GAP_SMALL, intro);
        for (int index = 0; index < chosen.size(); index++) {
            row.getChildren().add(chip(chosen.get(index), index));
        }

        MenuButton add = new MenuButton("+ Add section");
        add.getStyleClass().add("add-chip");
        add.setMinWidth(Region.USE_PREF_SIZE);
        for (String code : graded) {
            if (!chosen.contains(code)) {
                MenuItem item = new MenuItem(code);
                item.setOnAction(event -> addSection(code));
                add.getItems().add(item);
            }
        }
        // Hidden when three are chosen or no other section is graded;
        // the "Compare up to 3" words say why
        boolean full = chosen.size() >= CompareChart.MOST_SERIES;
        if (!full && !add.getItems().isEmpty()) {
            row.getChildren().add(add);
        }

        if (!notGraded.isEmpty()) {
            Label note = Parts.muted(notGradedText());
            note.getStyleClass().addAll("small-text", "stats-subtle");
            note.setMinWidth(0);
            HBox.setMargin(note, new Insets(0, 0, 0, Parts.GAP_SMALL));
            row.getChildren().add(note);
        }
        return row;
    }

    // Returns the chip of one chosen section, with a cross to remove it.
    private HBox chip(String code, int index) {
        Label name = new Label(code);
        name.setMinWidth(Region.USE_PREF_SIZE);
        HBox chip = Parts.row(SWATCH_GAP, seriesDot(index), name);
        chip.getStyleClass().add("section-chip");
        if (chosen.size() > 1) {
            Button remove = new Button();
            remove.setGraphic(Parts.icon(Parts.CLOSE, CROSS_SIZE));
            remove.getStyleClass().add("chip-remove");
            remove.setTooltip(new Tooltip("Remove " + code));
            remove.setOnAction(event -> removeSection(code));
            chip.getChildren().add(remove);
        } else {
            chip.getStyleClass().add("alone");
        }
        chip.setMinWidth(Region.USE_PREF_SIZE);
        return chip;
    }

    // Adds a section to the chart and table.
    private void addSection(String code) {
        if (chosen.size() < CompareChart.MOST_SERIES
                && !chosen.contains(code)) {
            chosen.add(code);
        }
        rebuild();
    }

    // Takes a section off the chart and table.
    private void removeSection(String code) {
        chosen.remove(code);
        rebuild();
    }

    // Names the sections not graded yet, joining runs of neighbours as
    // "G4 to G10".
    private String notGradedText() {
        List<String> parts = new ArrayList<>();
        int start = 0;
        while (start < notGraded.size()) {
            int end = start;
            while (end + 1 < notGraded.size()
                    && areNeighbours(notGraded.get(end),
                    notGraded.get(end + 1))) {
                end++;
            }
            if (end - start >= 2) {
                parts.add(notGraded.get(start) + " to "
                        + notGraded.get(end));
            } else {
                for (int index = start; index <= end; index++) {
                    parts.add(notGraded.get(index));
                }
            }
            start = end + 1;
        }

        String names = parts.get(0);
        for (int index = 1; index < parts.size(); index++) {
            if (index == parts.size() - 1) {
                names += " and " + parts.get(index);
            } else {
                names += ", " + parts.get(index);
            }
        }
        boolean one = notGraded.size() == 1;
        return names + (one ? " has" : " have") + " not been graded yet.";
    }

    // Says whether two sections sit next to each other in the settings,
    // with no graded section between them.
    private boolean areNeighbours(String first, String second) {
        return order.indexOf(second) == order.indexOf(first) + 1;
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
            legend.getChildren().add(Parts.row(SWATCH_GAP, line, name));
        }
        CompareChart chart = new CompareChart();
        chart.setSeries(chosen, chosenStats);

        Label title = Parts.title("How the marks spread in each section");
        title.setMinWidth(0);
        HBox titleRow = Parts.row(Parts.GAP, title, Parts.grow(), legend);
        VBox card = Parts.card(null, titleRow, chart);
        card.getStyleClass().add("stats-card");
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
        for (int index = 1; index < HEADINGS.length; index++) {
            ColumnConstraints column = new ColumnConstraints();
            column.setPercentWidth(FIGURE_PERCENT);
            table.getColumnConstraints().add(column);
        }

        for (int column = 0; column < HEADINGS.length; column++) {
            Label head = cell(HEADINGS[column], column > 0);
            head.getStyleClass().add("compare-head");
            table.add(head, column, 0);
        }
        for (int index = 0; index < chosen.size(); index++) {
            addTableRow(table, index);
        }
        return table;
    }

    // Adds one section's row to the table.
    private void addTableRow(GridPane table, int index) {
        String code = chosen.get(index);
        MarkStats one = statsOf(code);
        GradingRun run = runs.get(graded.indexOf(code));
        String hardest = QuestionFigures.hardestId(run.getSubmissions(),
                assessment.getQuestions());

        Label name = Parts.strong(code);
        name.setMinWidth(0);
        HBox section = Parts.row(Parts.GAP_SMALL, seriesDot(index), name);
        section.getStyleClass().addAll("compare-cell", "compare-first");
        section.setMaxWidth(Double.MAX_VALUE);
        section.setMaxHeight(Double.MAX_VALUE);
        int row = index + 1;
        table.add(section, 0, row);

        String[] values = {String.valueOf(one.getCount()),
            SectionView.oneDecimal(one.getMean()),
            SectionView.markText(one.getMedian()),
            "± " + SectionView.oneDecimal(one.getSpread()),
            SectionView.percent(one.getPassRate()), hardest};
        for (int column = 0; column < values.length; column++) {
            Label value = cell(values[column], true);
            value.getStyleClass().add("compare-cell");
            if (column == 1) {
                value.getStyleClass().add("strong");
            }
            table.add(value, column + 1, row);
        }
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

    // Returns the colour dot of the n-th chosen section.
    private static Circle seriesDot(int index) {
        Circle dot = new Circle(DOT_RADIUS);
        dot.getStyleClass().addAll("series-dot", seriesClass(index));
        return dot;
    }

    // Returns the style class of the n-th chosen section, from 1.
    private static String seriesClass(int index) {
        return "series-" + (index + 1);
    }
}
