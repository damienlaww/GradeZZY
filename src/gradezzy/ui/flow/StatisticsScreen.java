/*
 * File: StatisticsScreen.java
 *
 * Purpose: The Statistics tab: the figures and charts of one graded
 * section, or the bell curves and figures of up to three sections side
 * by side. Module and Section pickers and a "One section | Compare
 * sections" switch sit on the header row. A Run picker appears when the
 * section has earlier runs.
 *
 * Where it fits: FlowWindow shows it when the Statistics tab is picked,
 * and openStatistics (from Export and Runs) points it at one section
 * first. It reads getRun, listRuns and summarise from DashboardBackend;
 * SectionView and CompareView build the two views.
 */
package gradezzy.ui.flow;

import java.time.format.DateTimeFormatter;
import java.util.ArrayList;
import java.util.List;

import javafx.geometry.Insets;
import javafx.geometry.Pos;
import javafx.scene.Node;
import javafx.scene.control.ComboBox;
import javafx.scene.control.Label;
import javafx.scene.control.ScrollPane;
import javafx.scene.control.ToggleButton;
import javafx.scene.control.ToggleGroup;
import javafx.scene.layout.HBox;
import javafx.scene.layout.Region;
import javafx.scene.layout.VBox;

import gradezzy.config.ConfigKeys;
import gradezzy.model.Assessment;
import gradezzy.model.GradingRun;
import gradezzy.model.Section;
import gradezzy.model.Submission;
import gradezzy.stats.StatisticsSummary;
import gradezzy.ui.backend.Capability;
import gradezzy.ui.backend.DashboardBackend;
import gradezzy.ui.backend.RunRecord;

/**
 * The Statistics tab.
 */
public class StatisticsScreen extends FlowScreen {

    // Below this content width the header and the cards rearrange
    private static final double NARROW_WIDTH = 1000.0;

    // The widths of the three pickers
    private static final double MODULE_WIDTH = 110.0;
    private static final double SECTION_WIDTH = 90.0;
    private static final double RUN_WIDTH = 150.0;

    // The extra gap before the One section | Compare sections switch
    private static final double SWITCH_GAP = 8.0;

    // The gap between the heading and its subtitle
    private static final double TITLE_GAP = 4.0;

    // The gap between a picker's name and the picker
    private static final double FIELD_GAP = 4.0;

    // The gap between the lines of an empty state
    private static final double EMPTY_GAP = 8.0;

    // Separates the parts of the subtitle
    private static final String DOT = " · ";

    // When a run was graded, such as "28 Oct 2026, 14:05"
    private static final DateTimeFormatter WHEN =
            DateTimeFormatter.ofPattern("d MMM yyyy, HH:mm");

    // The day of a run in the Run picker, such as "28 Oct"
    private static final DateTimeFormatter DAY =
            DateTimeFormatter.ofPattern("d MMM");

    // The heading and subtitle
    private final Label subtitle = Parts.subtitle("");
    private final VBox titleBox = new VBox(TITLE_GAP,
            Parts.heading("Statistics"), subtitle);

    // The pickers and the switch
    private final ComboBox<String> moduleBox = new ComboBox<>();
    private final ComboBox<String> sectionBox = new ComboBox<>();
    private final ComboBox<String> runBox = new ComboBox<>();
    private final VBox sectionField = field("Section", sectionBox);
    private final VBox runField = field("Run", runBox);
    private final ToggleButton oneButton = new ToggleButton("One section");
    private final ToggleButton compareButton =
            new ToggleButton("Compare sections");
    private final HBox controls = new HBox(Parts.GAP);

    // The header: one row on a wide window, two rows on a narrow one
    private final VBox headerArea = new VBox(Parts.GAP);
    private final HBox wideHeader = new HBox(Parts.GAP);

    // Everything under the header
    private final VBox body = new VBox(Parts.CARD_GAP);

    // The page
    private final ScrollPane view = Parts.widePage(headerArea, body);

    // The two views
    private final SectionView sectionView;
    private final CompareView compareView = new CompareView();

    // The runs of the section in the Run picker; the first is the one in
    // use, the others are earlier runs, newest first
    private final List<GradingRun> runChoices = new ArrayList<>();

    // The module shown
    private String moduleCode = null;

    // The section shown
    private String sectionCode = null;

    // The run picked, as an index into runChoices
    private int runIndex = 0;

    // True while the pickers are being filled, so their events are
    // ignored
    private boolean filling = false;

    // True when the window is too narrow for one header row
    private boolean narrow = false;

    /**
     * Creates the screen.
     *
     * @param window the window that shows it
     */
    public StatisticsScreen(FlowWindow window) {
        super(window);
        sectionView = new SectionView(window.getBackend());
        view.getStyleClass().add("statistics-page");
        createControls();
        arrangeHeader();
        view.getContent().layoutBoundsProperty().addListener(
                (observable, before, after) -> applyWidth(after.getWidth()));
    }

    /**
     * Points the screen at one section and switches to One section.
     * FlowWindow.openStatistics calls it just before showing the tab.
     *
     * @param newModuleCode the module
     * @param newSectionCode the section
     */
    public void selectSection(String newModuleCode,
            String newSectionCode) {
        moduleCode = newModuleCode;
        sectionCode = newSectionCode;
        runIndex = 0;
        oneButton.setSelected(true);
    }

    /**
     * Switches between One section and Compare sections and refreshes
     * the view. Also used by the developer checks.
     *
     * @param compare true for Compare sections
     */
    public void setCompare(boolean compare) {
        if (compare) {
            compareButton.setSelected(true);
        } else {
            oneButton.setSelected(true);
        }
    }

    /**
     * Scrolls the page to its top or bottom, for the developer checks.
     *
     * @param bottom true for the bottom, false for the top
     */
    public void scrollTo(boolean bottom) {
        if (bottom) {
            view.setVvalue(view.getVmax());
        } else {
            view.setVvalue(view.getVmin());
        }
    }

    @Override
    public Node getView() {
        return view;
    }

    @Override
    public void onShow() {
        refresh();
    }

    @Override
    public boolean usesActionBar() {
        return false;
    }

    // ------------------------------------------------------------ build

    // Builds the pickers and the switch and wires their events.
    private void createControls() {
        moduleBox.setPrefWidth(MODULE_WIDTH);
        sectionBox.setPrefWidth(SECTION_WIDTH);
        runBox.setPrefWidth(RUN_WIDTH);
        moduleBox.setOnAction(event -> chooseModule());
        sectionBox.setOnAction(event -> chooseSection());
        runBox.setOnAction(event -> chooseRun());

        ToggleGroup group = new ToggleGroup();
        oneButton.setToggleGroup(group);
        compareButton.setToggleGroup(group);
        oneButton.setSelected(true);
        oneButton.getStyleClass().add("left-pill");
        compareButton.getStyleClass().add("right-pill");
        group.selectedToggleProperty().addListener((observable, before,
                after) -> {
            if (after == null) {
                before.setSelected(true);
                return;
            }
            if (!filling) {
                refresh();
            }
        });
        HBox switchBar = new HBox(oneButton, compareButton);
        switchBar.getStyleClass().add("segmented");
        switchBar.setMinWidth(Region.USE_PREF_SIZE);
        HBox.setMargin(switchBar, new Insets(0, 0, 0, SWITCH_GAP));

        controls.getChildren().addAll(field("Module", moduleBox),
                sectionField, runField, switchBar);
        controls.setAlignment(Pos.BOTTOM_LEFT);
        controls.setFillHeight(false);
        controls.setMinWidth(Region.USE_PREF_SIZE);
        titleBox.setMinWidth(0);
        wideHeader.setAlignment(Pos.BOTTOM_LEFT);
        headerArea.getStyleClass().add("stats-header");
    }

    // Returns a picker with its small name above it.
    private static VBox field(String label, Node control) {
        Label name = Parts.muted(label);
        name.getStyleClass().add("field-label");
        VBox box = new VBox(FIELD_GAP, name, control);
        box.setMinWidth(Region.USE_PREF_SIZE);
        return box;
    }

    // Rearranges the header and cards when the width crosses the limit.
    private void applyWidth(double width) {
        Insets padding = ((Region) view.getContent()).getInsets();
        double content = width - padding.getLeft() - padding.getRight();
        boolean nowNarrow = content < NARROW_WIDTH;
        if (nowNarrow == narrow) {
            return;
        }
        narrow = nowNarrow;
        arrangeHeader();
        sectionView.setNarrow(narrow);
    }

    // Puts the pickers beside the heading, or under it when narrow.
    private void arrangeHeader() {
        if (narrow) {
            wideHeader.getChildren().clear();
            headerArea.getChildren().setAll(titleBox, controls);
            return;
        }
        wideHeader.getChildren().setAll(titleBox, Parts.grow(), controls);
        headerArea.getChildren().setAll(wideHeader);
    }

    // ---------------------------------------------------------- refresh

    // Reads the backend again and shows the view that is switched on.
    private void refresh() {
        DashboardBackend backend = getBackend();
        if (!backend.supports(Capability.STATISTICS)) {
            showUnsupported();
            return;
        }
        controls.setVisible(true);
        controls.setManaged(true);

        fillModules();
        if (moduleCode == null) {
            subtitle.setText("No module is set up yet.");
            body.getChildren().setAll(emptyState("Nothing graded yet",
                    "Add a module in Settings, then grade a section in "
                    + "Marking."));
            return;
        }
        fillSections();
        if (compareButton.isSelected()) {
            showCompare();
        } else {
            showSection();
        }
    }

    // Fills the module picker and picks a module.
    private void fillModules() {
        List<String> modules = getBackend().getModuleCodes();
        if (!modules.contains(moduleCode)) {
            moduleCode = null;
            String stateModule = getState().getModuleCode();
            if (modules.contains(stateModule)) {
                moduleCode = stateModule;
            } else if (!modules.isEmpty()) {
                moduleCode = modules.get(0);
            }
        }
        filling = true;
        moduleBox.getItems().setAll(modules);
        moduleBox.setValue(moduleCode);
        filling = false;
    }

    // Fills the section picker and picks a section: the one asked for,
    // else the one on Marking if it has marks, else the first with marks.
    private void fillSections() {
        List<String> sections = getBackend().getSectionCodes(moduleCode);
        if (!sections.contains(sectionCode)) {
            sectionCode = null;
            runIndex = 0;
            String stateSection = getState().getSectionCode();
            if (sections.contains(stateSection)
                    && isGraded(runOf(stateSection))) {
                sectionCode = stateSection;
            }
            for (String code : sections) {
                if (sectionCode == null && isGraded(runOf(code))) {
                    sectionCode = code;
                }
            }
            if (sectionCode == null && !sections.isEmpty()) {
                sectionCode = sections.get(0);
            }
        }
        filling = true;
        sectionBox.getItems().setAll(sections);
        sectionBox.setValue(sectionCode);
        filling = false;
    }

    // Fills the Run picker with the run in use and the section's earlier
    // runs. The picker is hidden when there is no earlier run.
    private void fillRuns() {
        runChoices.clear();
        GradingRun current = runOf(sectionCode);
        if (current != null) {
            runChoices.add(current);
            addEarlierRuns(current);
        }
        if (runIndex >= runChoices.size()) {
            runIndex = 0;
        }

        List<String> labels = new ArrayList<>();
        for (int index = 0; index < runChoices.size(); index++) {
            labels.add(runLabel(index));
        }
        filling = true;
        runBox.getItems().setAll(labels);
        if (!labels.isEmpty()) {
            runBox.getSelectionModel().select(runIndex);
        }
        filling = false;
        boolean earlier = runChoices.size() > 1;
        runField.setVisible(earlier);
        runField.setManaged(earlier);
    }

    // Adds the section's other saved runs, newest first.
    private void addEarlierRuns(GradingRun current) {
        DashboardBackend backend = getBackend();
        if (!backend.supports(Capability.HISTORY)) {
            return;
        }
        String label = current.getSection().getLabel();
        for (RunRecord record : backend.listRuns()) {
            GradingRun run = record.getRun();
            boolean sameSection = run.getSection() != null
                    && label.equals(run.getSection().getLabel());
            if (sameSection && !run.getId().equals(current.getId())) {
                insertByDate(run);
            }
        }
    }

    // Puts an earlier run after the run in use, newest first.
    private void insertByDate(GradingRun run) {
        int at = 1;
        while (at < runChoices.size() && isNewer(runChoices.get(at), run)) {
            at++;
        }
        runChoices.add(at, run);
    }

    // Names one run of the Run picker.
    private String runLabel(int index) {
        GradingRun run = runChoices.get(index);
        if (index > 0) {
            return when(run);
        }
        String name = "Latest";
        for (int other = 1; other < runChoices.size(); other++) {
            if (isNewer(runChoices.get(other), run)) {
                name = "In use";
            }
        }
        if (run.getStartedAt() == null) {
            return name;
        }
        return name + DOT + run.getStartedAt().format(DAY);
    }

    // ------------------------------------------------------ one section

    // Shows the figures and charts of the section and run picked.
    private void showSection() {
        sectionField.setVisible(true);
        sectionField.setManaged(true);
        fillRuns();
        Assessment assessment = getBackend().getAssessment();
        GradingRun run = null;
        if (!runChoices.isEmpty()) {
            run = runChoices.get(runIndex);
        }

        String text = moduleCode + DOT + sectionCode + DOT
                + assessment.getName();
        if (run != null && run.getStartedAt() != null) {
            text += DOT + "graded " + when(run);
        } else if (run == null) {
            text += DOT + "not graded yet";
        }
        subtitle.setText(text);

        if (!isGraded(run)) {
            body.getChildren().setAll(notGradedState());
            return;
        }
        MarkStats stats = MarkStats.of(run.getSubmissions(),
                assessment.getMaxTotal(), passPercent());
        StatisticsSummary summary = null;
        if (runIndex == 0) {
            summary = getBackend().summarise(moduleCode, sectionCode);
        }
        sectionView.show(run, summary, stats, assessment);
        sectionView.setNarrow(narrow);
        body.getChildren().setAll(sectionView.getView());
    }

    // Returns the empty state of a section without marks.
    private VBox notGradedState() {
        for (String code : getBackend().getSectionCodes(moduleCode)) {
            if (isGraded(runOf(code))) {
                return emptyState(sectionCode + " has not been graded yet",
                        "Choose another section above, or grade this one "
                        + "in Marking.");
            }
        }
        return emptyState("Nothing graded yet", "Grade a section of "
                + moduleCode + " in Marking and its charts appear here.");
    }

    // --------------------------------------------------------- compare

    // Shows the bell curves and figures of up to three sections.
    private void showCompare() {
        sectionField.setVisible(false);
        sectionField.setManaged(false);
        runField.setVisible(false);
        runField.setManaged(false);
        Assessment assessment = getBackend().getAssessment();
        subtitle.setText(moduleCode + DOT + assessment.getName() + DOT
                + "current run of each section");

        List<String> sections = getBackend().getSectionCodes(moduleCode);
        List<GradingRun> runs = new ArrayList<>();
        List<MarkStats> stats = new ArrayList<>();
        List<String> graded = new ArrayList<>();
        for (String code : sections) {
            GradingRun run = runOf(code);
            if (isGraded(run)) {
                runs.add(run);
                stats.add(MarkStats.of(run.getSubmissions(),
                        assessment.getMaxTotal(), passPercent()));
                graded.add(code);
            } else {
                runs.add(null);
                stats.add(null);
            }
        }

        if (graded.isEmpty()) {
            body.getChildren().setAll(emptyState("Nothing graded yet",
                    "Grade a section of " + moduleCode + " in Marking, "
                    + "then grade another to compare them."));
            return;
        }
        if (graded.size() == 1) {
            body.getChildren().setAll(emptyState(
                    "Compare needs two graded sections",
                    "Only " + graded.get(0) + " of " + moduleCode
                    + " has been graded so far. Grade another section in "
                    + "Marking, then come back to compare them."));
            return;
        }
        compareView.show(sections, runs, stats, assessment, sectionCode);
        body.getChildren().setAll(compareView.getView());
    }

    // ---------------------------------------------------------- events

    // Shows another module.
    private void chooseModule() {
        if (filling || moduleBox.getValue() == null) {
            return;
        }
        moduleCode = moduleBox.getValue();
        sectionCode = null;
        runIndex = 0;
        refresh();
    }

    // Shows another section.
    private void chooseSection() {
        if (filling || sectionBox.getValue() == null) {
            return;
        }
        sectionCode = sectionBox.getValue();
        runIndex = 0;
        refresh();
    }

    // Shows another run of the section.
    private void chooseRun() {
        int picked = runBox.getSelectionModel().getSelectedIndex();
        if (filling || picked < 0) {
            return;
        }
        runIndex = picked;
        refresh();
    }

    // ----------------------------------------------------- empty states

    // Hides everything when the backend cannot work out statistics.
    private void showUnsupported() {
        controls.setVisible(false);
        controls.setManaged(false);
        subtitle.setText("Charts of the graded sections.");
        Label title = Parts.title("Statistics are not available yet");
        Label text = Parts.muted("This version cannot work out statistics."
                + " The marks are still on the score sheet you export in "
                + "Marking.");
        text.setWrapText(true);
        VBox box = new VBox(EMPTY_GAP, title, text);
        box.getStyleClass().addAll("flow-card", "stats-empty");
        box.setAlignment(Pos.CENTER);
        body.getChildren().setAll(box);
    }

    // Returns an empty state with a link that opens the Marking tab.
    private VBox emptyState(String heading, String help) {
        Label title = Parts.title(heading);
        Label text = Parts.muted(help);
        text.setWrapText(true);
        VBox box = new VBox(EMPTY_GAP, title, text, Parts.link(
                "Open Marking", () -> getWindow().showPage(Page.MARKING)));
        box.getStyleClass().addAll("flow-card", "stats-empty");
        box.setAlignment(Pos.CENTER);
        return box;
    }

    // --------------------------------------------------------- helpers

    // Returns the run in use for a section of the module shown, or null.
    private GradingRun runOf(String code) {
        if (code == null) {
            return null;
        }
        return getBackend().getRun(new Section(moduleCode, code));
    }

    // Reads the pass percentage from the settings.
    private double passPercent() {
        return MarkStats.parsePassPercent(getBackend().getSetting(
                ConfigKeys.PASS_MARK_PERCENT));
    }

    // Says whether a run has at least one student with a total.
    static boolean isGraded(GradingRun run) {
        if (run == null) {
            return false;
        }
        for (Submission submission : run.getSubmissions()) {
            if (!submission.getQuestionResults().isEmpty()) {
                return true;
            }
        }
        return false;
    }

    // Says whether the first run started after the second one.
    private static boolean isNewer(GradingRun run, GradingRun other) {
        if (run.getStartedAt() == null) {
            return false;
        }
        if (other.getStartedAt() == null) {
            return true;
        }
        return run.getStartedAt().isAfter(other.getStartedAt());
    }

    // Says when a run was graded.
    private static String when(GradingRun run) {
        if (run.getStartedAt() == null) {
            return "date unknown";
        }
        return run.getStartedAt().format(WHEN);
    }
}
