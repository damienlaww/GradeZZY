/*
 * File: StatisticsScreen.java
 *
 * Purpose: The Statistics tab: the figures and charts of one graded
 * section, or the bell curves and figures of up to three sections side
 * by side. Module, Assessment and Section pickers and a "One section |
 * Compare sections" switch sit on the header row. In One section the
 * charts are of the section's runs of the assessment picked, and a Run
 * picker appears when it has more than one. In Compare sections the
 * Assessment picker stays and Section and Run hide: each section's latest
 * run of that assessment is compared, so two assessments never share a
 * chart.
 *
 * Where it fits: FlowWindow shows it when the Statistics tab is picked,
 * and openStatistics (from Export and Runs) points it at one section
 * first. It reads getRun, listRuns and summarise from DashboardBackend;
 * ModuleRuns finds the runs to compare; SectionView and CompareView
 * build the two views. The assessment and sections compared are kept
 * in FlowState.
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
import javafx.scene.control.Toggle;
import javafx.scene.control.ToggleButton;
import javafx.scene.control.ToggleGroup;
import javafx.scene.control.Tooltip;
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
import gradezzy.util.TextUtils;

/**
 * The Statistics tab.
 *
 * <p>Tier: SHOULD.
 */
public class StatisticsScreen extends FlowScreen {

    // Below this content width the header and the cards rearrange
    private static final double NARROW_WIDTH = 1000.0;

    // The widths of the four pickers
    private static final double MODULE_WIDTH = 110.0;
    private static final double ASSESSMENT_WIDTH = 160.0;
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

    // Separates the maximums listed in a sentence
    private static final String LIST_SEPARATOR = ", ";

    // Two maximums closer than this are the same
    private static final double SAME_MAXIMUM = 1e-9;

    // Turns a fraction into a percentage
    private static final double PERCENT = 100.0;

    // The fewest graded sections Compare sections draws
    private static final int FEWEST_COMPARED = 2;

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
    private final ComboBox<String> assessmentBox = new ComboBox<>();
    private final VBox moduleField = field("Module", moduleBox);
    private final VBox assessmentField = field("Assessment",
            assessmentBox);
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
    private final ScrollPane view =
            Parts.widePage(new Node[] {headerArea, body});

    // The tip of a Module picker with one module, set on its field as a
    // turned-off picker shows no tip of its own
    private final Tooltip moduleTip = Parts.tooltip("More modules are "
            + "added in Settings.");

    // The two views
    private final SectionView sectionView;
    private final CompareView compareView;

    // The largest answer groups of the run shown, under the charts
    private final CommonMistakesSection commonMistakes;

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

    // The id of the run to pick when the runs are next listed, or null
    private String wantedRunId = null;

    /**
     * Creates the screen.
     *
     * @param window the window that shows it
     */
    public StatisticsScreen(FlowWindow window) {
        super(window);
        sectionView = new SectionView(window.getBackend());
        commonMistakes = new CommonMistakesSection(window);
        compareView = new CompareView(getState().getStatisticsSections());
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
        // Show the assessment the section was graded with last
        getState().setStatisticsAssessment(null);
        oneButton.setSelected(true);
    }

    /**
     * Points the screen at one run, even an earlier one: its section and
     * assessment, with the run picked, in One section.
     *
     * @param run the run, which has a section and an assessment
     */
    public void selectRun(GradingRun run) {
        moduleCode = run.getSection().getModuleCode();
        sectionCode = run.getSection().getSectionCode();
        runIndex = 0;
        wantedRunId = run.getId();
        getState().setStatisticsAssessment(run.getAssessment().getName());
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

    /**
     * Returns the Statistics tab built in the constructor.
     */
    @Override
    public Node getView() {
        return view;
    }

    /**
     * Reloads the graded runs and redraws the charts.
     */
    @Override
    public void onShow() {
        refresh();
    }

    /**
     * Returns false: the Statistics tab has no action bar.
     */
    @Override
    public boolean usesActionBar() {
        return false;
    }

    // ------------------------------------------------------------ build

    // Follows a click on One section or Compare sections. Clicking the
    // button that is already on would turn both off, so it stays on.
    private void switchView(Toggle before, Toggle after) {
        if (after == null) {
            before.setSelected(true);
            return;
        }
        if (!filling) {
            refresh();
        }
    }

    // Generated by Claude; modified for the Assessment picker
    // Builds the pickers and the switch and wires their events.
    private void createControls() {
        moduleBox.setPrefWidth(MODULE_WIDTH);
        sectionBox.setPrefWidth(SECTION_WIDTH);
        runBox.setPrefWidth(RUN_WIDTH);
        assessmentBox.setPrefWidth(ASSESSMENT_WIDTH);
        moduleBox.setOnAction(event -> chooseModule());
        assessmentBox.setOnAction(event -> chooseAssessment());
        sectionBox.setOnAction(event -> chooseSection());
        runBox.setOnAction(event -> chooseRun());

        ToggleGroup group = new ToggleGroup();
        oneButton.setToggleGroup(group);
        compareButton.setToggleGroup(group);
        oneButton.setSelected(true);
        oneButton.getStyleClass().add("left-pill");
        compareButton.getStyleClass().add("right-pill");
        group.selectedToggleProperty().addListener((observable, before,
                after) -> switchView(before, after));
        HBox switchBar = new HBox(oneButton, compareButton);
        switchBar.getStyleClass().add("segmented");
        switchBar.setMinWidth(Region.USE_PREF_SIZE);
        HBox.setMargin(switchBar, new Insets(0, 0, 0, SWITCH_GAP));

        controls.getChildren().addAll(moduleField, assessmentField,
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
                    + "Grading."));
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

        // With one module there is nothing to choose, but the picker
        // stays, so it is clear which module is shown
        boolean single = modules.size() <= 1;
        moduleBox.setDisable(single);
        if (single) {
            Tooltip.install(moduleField, moduleTip);
        } else {
            Tooltip.uninstall(moduleField, moduleTip);
        }
    }

    // Fills the section picker and picks a section: the one asked for,
    // else the one on Grading if it has marks, else the first with marks.
    private void fillSections() {
        List<String> sections = getBackend().getSectionCodes(moduleCode);
        if (!sections.contains(sectionCode)) {
            sectionCode = null;
            runIndex = 0;
            String stateSection = getState().getSectionCode();
            boolean stateGraded = sections.contains(stateSection)
                    && isGraded(runOf(stateSection));
            if (stateGraded) {
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

    // Fills the Run picker with the section's runs of an assessment: the
    // run in use first when it is of that assessment, then the earlier
    // ones, newest first. The picker is hidden when there is only one.
    private void fillRuns(String assessmentName) {
        runChoices.clear();
        GradingRun current = runOf(sectionCode);
        if (current != null) {
            runChoices.add(current);
            addEarlierRuns(current);
        }
        if (assessmentName != null) {
            keepRunsOf(assessmentName);
        }
        pickWantedRun();
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

    // Picks the run asked for by selectRun, once.
    private void pickWantedRun() {
        if (wantedRunId == null) {
            return;
        }
        for (int index = 0; index < runChoices.size(); index++) {
            if (runChoices.get(index).getId().equals(wantedRunId)) {
                runIndex = index;
            }
        }
        wantedRunId = null;
    }

    // Leaves only the runs graded with one assessment in the Run picker.
    private void keepRunsOf(String assessmentName) {
        List<GradingRun> kept = new ArrayList<>();
        for (GradingRun run : runChoices) {
            boolean fits = run.getAssessment() != null
                    && run.getAssessment().getName().equals(assessmentName);
            if (fits) {
                kept.add(run);
            }
        }
        runChoices.clear();
        runChoices.addAll(kept);
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

    // Generated by Claude; modified for each run's own questions
    // Shows the figures and charts of the section, assessment and run
    // picked.
    private void showSection() {
        sectionField.setVisible(true);
        sectionField.setManaged(true);
        List<String> sections = getBackend().getSectionCodes(moduleCode);
        ModuleRuns moduleRuns = new ModuleRuns(getBackend(), moduleCode,
                sections);
        List<String> assessments = moduleRuns.getAssessmentNames();
        preferSectionAssessment(assessments);
        String assessmentName = fillAssessments(assessments);
        fillRuns(assessmentName);
        GradingRun run = null;
        if (!runChoices.isEmpty()) {
            run = runChoices.get(runIndex);
        }
        // A run keeps the questions it was graded with, which may not be
        // those of the assessment in use now
        Assessment assessment = getBackend().getAssessment();
        if (run != null && run.getAssessment() != null) {
            assessment = run.getAssessment();
        }
        String shownName = assessment.getName();
        if (run == null && assessmentName != null) {
            shownName = assessmentName;
        }

        String text = moduleCode + DOT + sectionCode + DOT + shownName;
        if (sectionCode == null) {
            // A module with no section has no section or run to name
            text = moduleCode + DOT + "no sections yet";
        } else if (run != null && run.getStartedAt() != null) {
            text += DOT + "graded " + when(run);
        } else if (run == null) {
            text += DOT + "not graded yet";
        }
        subtitle.setText(text);

        if (!isGraded(run)) {
            body.getChildren().setAll(notGradedState(assessmentName));
            return;
        }
        MarkStats stats = MarkStats.of(run.getSubmissions(),
                assessment.getMaxTotal(), passPercent());
        // The backend's summary is of the run in use only
        StatisticsSummary summary = null;
        GradingRun current = runOf(sectionCode);
        boolean isCurrent = current != null
                && current.getId().equals(run.getId());
        if (isCurrent) {
            summary = getBackend().summarise(moduleCode, sectionCode);
        }
        sectionView.show(run, summary, stats, assessment);
        sectionView.setNarrow(narrow);
        body.getChildren().setAll(sectionView.getView());
        if (getBackend().supports(Capability.ANSWER_GROUPS)) {
            commonMistakes.show(run);
            body.getChildren().add(commonMistakes.getView());
        }
    }

    // Returns the empty state of a section without marks for the
    // assessment picked, or null for any assessment.
    private VBox notGradedState(String assessmentName) {
        if (assessmentName != null && isGraded(runOf(sectionCode))) {
            return emptyState(sectionCode + " has no graded run of "
                    + assessmentName, "Choose another assessment or "
                    + "section above, or grade this one in Grading.");
        }
        for (String code : getBackend().getSectionCodes(moduleCode)) {
            if (isGraded(runOf(code))) {
                return emptyState(sectionCode + " has not been graded yet",
                        "Choose another section above, or grade this one "
                        + "in Grading.");
            }
        }
        return emptyState("Nothing graded yet", "Grade a section of "
                + moduleCode + " in Grading and its charts appear here.");
    }

    // --------------------------------------------------------- compare

    // Generated by Claude; modified for comparing sections by assessment
    // Shows the bell curves and figures of up to three sections, each
    // from its latest run of the assessment picked.
    private void showCompare() {
        setShown(sectionField, false);
        setShown(runField, false);
        List<String> sections = getBackend().getSectionCodes(moduleCode);
        ModuleRuns moduleRuns = new ModuleRuns(getBackend(), moduleCode,
                sections);
        List<String> assessments = moduleRuns.getAssessmentNames();
        String assessmentName = fillAssessments(assessments);
        if (assessmentName == null) {
            subtitle.setText(moduleCode + DOT + "nothing graded yet");
            body.getChildren().setAll(emptyState("Nothing graded yet",
                    "Grade a section of " + moduleCode + " in Grading, "
                    + "then grade another to compare them."));
            return;
        }
        subtitle.setText(moduleCode + DOT + assessmentName + DOT
                + "latest run of each section");

        List<GradingRun> runs = new ArrayList<>();
        for (String code : sections) {
            runs.add(moduleRuns.findRun(code, assessmentName));
        }
        boolean mixed = hasMixedMaximums(runs);
        List<MarkStats> stats = new ArrayList<>();
        for (GradingRun run : runs) {
            stats.add(compareStats(run, mixed));
        }
        boolean otherAssessments = assessments.size() > 1;
        compareView.setPercent(mixed, maximumsNote(assessmentName, runs));
        compareView.setMessage(compareMessage(sections, runs,
                assessmentName, otherAssessments));
        compareView.show(sections, runs, stats, assessmentName,
                sectionCode);
        body.getChildren().setAll(compareView.getView());
    }

    // Picks the assessment the section's run in use was graded with, when
    // none is picked yet, so a section opens on its latest results.
    private void preferSectionAssessment(List<String> names) {
        if (names.contains(getState().getStatisticsAssessment())) {
            return;
        }
        GradingRun current = runOf(sectionCode);
        boolean hasAssessment = current != null
                && current.getAssessment() != null
                && names.contains(current.getAssessment().getName());
        if (hasAssessment) {
            getState().setStatisticsAssessment(
                    current.getAssessment().getName());
        }
    }

    // Fills the Assessment picker and returns the assessment picked: the
    // one picked before, else the one in Settings when it has runs, else
    // the one graded most recently, or null when none is graded.
    private String fillAssessments(List<String> names) {
        String picked = getState().getStatisticsAssessment();
        if (!names.contains(picked)) {
            picked = null;
            String inSettings = getBackend().getAssessment().getName();
            if (names.contains(inSettings)) {
                picked = inSettings;
            } else if (!names.isEmpty()) {
                picked = names.get(0);
            }
        }
        filling = true;
        assessmentBox.getItems().setAll(names);
        assessmentBox.setValue(picked);
        filling = false;
        setShown(assessmentField, !names.isEmpty());
        return picked;
    }

    // Says whether the graded runs have different maximums.
    private static boolean hasMixedMaximums(List<GradingRun> runs) {
        List<Double> maximums = maximumsOf(runs);
        return maximums.size() > 1;
    }

    // Lists the different maximums of the graded runs, in order.
    private static List<Double> maximumsOf(List<GradingRun> runs) {
        List<Double> maximums = new ArrayList<>();
        for (GradingRun run : runs) {
            boolean newMaximum = run != null
                    && !hasMaximum(maximums, maxOf(run));
            if (newMaximum) {
                maximums.add(maxOf(run));
            }
        }
        return maximums;
    }

    // Says whether a list already holds a maximum.
    private static boolean hasMaximum(List<Double> maximums, double max) {
        for (double listed : maximums) {
            if (Math.abs(listed - max) < SAME_MAXIMUM) {
                return true;
            }
        }
        return false;
    }

    // Returns the highest possible total of a run, from its own
    // assessment.
    private static double maxOf(GradingRun run) {
        return run.getAssessment().getMaxTotal();
    }

    // Works out the figures of a run, as marks or as percentages of its
    // maximum, or returns null for a section with no run.
    private MarkStats compareStats(GradingRun run, boolean asPercent) {
        if (run == null) {
            return null;
        }
        if (!asPercent) {
            return MarkStats.of(run.getSubmissions(), maxOf(run),
                    passPercent());
        }
        return new MarkStats(percentTotals(run), PERCENT, passPercent());
    }

    // Returns the total of each graded student as a percentage of the
    // run's maximum.
    private static List<Double> percentTotals(GradingRun run) {
        List<Double> totals = new ArrayList<>();
        double max = maxOf(run);
        for (Submission submission : run.getSubmissions()) {
            boolean hasMarks = !submission.getQuestionResults().isEmpty();
            boolean counted = hasMarks && max > 0;
            if (counted) {
                double share = submission.getTotalScore() * PERCENT / max;
                totals.add(share);
            }
        }
        return totals;
    }

    // Says why the figures are percentages, or returns an empty text
    // when every run has the same maximum.
    private static String maximumsNote(String assessmentName,
            List<GradingRun> runs) {
        List<Double> maximums = maximumsOf(runs);
        if (maximums.size() <= 1) {
            return "";
        }
        List<String> marks = new ArrayList<>();
        for (double max : maximums) {
            marks.add(Parts.mark(max));
        }
        String markList = TextUtils.join(LIST_SEPARATOR, marks);
        return "These runs of " + assessmentName + " have different "
                + "maximums (" + markList + "), so each total is shown as a "
                + "percentage of its own run's maximum.";
    }

    // Returns the empty state shown in place of the chart when fewer
    // than two sections are graded for the assessment, or null.
    private VBox compareMessage(List<String> sections,
            List<GradingRun> runs, String assessmentName,
            boolean otherAssessments) {
        List<String> graded = new ArrayList<>();
        for (int index = 0; index < sections.size(); index++) {
            if (runs.get(index) != null) {
                graded.add(sections.get(index));
            }
        }
        if (graded.size() >= FEWEST_COMPARED) {
            return null;
        }
        String next = ", then come back to compare them.";
        if (otherAssessments) {
            next = ", or choose another assessment above.";
        }
        String which = "another section";
        if (graded.isEmpty()) {
            which = "a section";
        }
        String help = "Grade " + which + " of " + moduleCode + " for "
                + assessmentName + " in Grading" + next;
        if (graded.isEmpty()) {
            return emptyState("Nothing graded for " + assessmentName
                    + " yet", help);
        }
        return emptyState("Compare needs two graded sections", "Only "
                + graded.get(0) + " has been graded for " + assessmentName
                + " so far. " + help);
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

    // Shows another assessment: its runs of the section, or each
    // section's latest run of it in Compare sections.
    private void chooseAssessment() {
        boolean ignored = filling || assessmentBox.getValue() == null;
        if (ignored) {
            return;
        }
        getState().setStatisticsAssessment(assessmentBox.getValue());
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
                + "Grading.");
        text.setWrapText(true);
        VBox box = new VBox(EMPTY_GAP, title, text);
        box.getStyleClass().addAll("flow-card", "stats-empty");
        box.setAlignment(Pos.CENTER);
        body.getChildren().setAll(box);
    }

    // Returns an empty state with a link that opens the Grading tab.
    private VBox emptyState(String heading, String help) {
        Label title = Parts.title(heading);
        Label text = Parts.muted(help);
        text.setWrapText(true);
        VBox box = new VBox(EMPTY_GAP, title, text, Parts.link(
                "Go to Grading", () -> getWindow().showPage(Page.MARKING)));
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

    // Shows or hides a node and the room it takes.
    private static void setShown(Node node, boolean shown) {
        node.setVisible(shown);
        node.setManaged(shown);
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
