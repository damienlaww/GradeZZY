/*
 * File: RunsScreen.java
 *
 * Purpose: The Runs tab: every grading run in one table (open, resume,
 * restore or see the statistics of one), the backups of the saved
 * results, and the activity log of what instructors did.
 *
 * Where it fits: FlowWindow shows it when the Runs tab is picked. It has
 * no action bar. Replaces the old History and Audit Log screens.
 */
package gradezzy.ui.flow;

import java.io.File;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.time.LocalDate;
import java.time.format.DateTimeFormatter;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.function.Supplier;

import javafx.geometry.Insets;
import javafx.geometry.Pos;
import javafx.scene.Cursor;
import javafx.scene.Node;
import javafx.scene.control.Button;
import javafx.scene.control.Hyperlink;
import javafx.scene.control.Label;
import javafx.scene.control.ScrollPane;
import javafx.scene.control.Toggle;
import javafx.scene.control.ToggleButton;
import javafx.scene.control.ToggleGroup;
import javafx.scene.control.Tooltip;
import javafx.scene.layout.HBox;
import javafx.scene.layout.StackPane;
import javafx.scene.layout.VBox;
import javafx.stage.FileChooser;

import atlantafx.base.controls.CustomTextField;

import gradezzy.config.ConfigKeys;
import gradezzy.model.Anomaly;
import gradezzy.model.AuditEntry;
import gradezzy.model.FixStatus;
import gradezzy.model.GradingRun;
import gradezzy.model.SavedAssessment;
import gradezzy.model.Section;
import gradezzy.model.Submission;
import gradezzy.ui.backend.ActionResult;
import gradezzy.ui.backend.Capability;
import gradezzy.ui.backend.DashboardBackend;
import gradezzy.ui.backend.RepairItem;
import gradezzy.ui.backend.ReviewDecision;
import gradezzy.ui.backend.RunRecord;

/**
 * The Runs tab.
 *
 * <p>Tier: SHOULD.
 */
public class RunsScreen extends FlowScreen {

    // How dates are shown
    private static final DateTimeFormatter WHEN =
            DateTimeFormatter.ofPattern("d MMM yyyy, HH:mm");

    // The value of a switched-off true or false setting
    private static final String FALSE = "false";

    // The height of a run or backup row
    private static final double ROW_HEIGHT = 64.0;

    // The kinds of activity the filter offers
    private static final String[] KINDS = {"All", "Grading", "Fixes",
        "Flags", "Settings and deletes"};

    // A day and a time of day, for the activity
    private static final DateTimeFormatter DAY =
            DateTimeFormatter.ofPattern("EEEE, d MMM yyyy");
    private static final DateTimeFormatter TIME =
            DateTimeFormatter.ofPattern("HH:mm");

    // Column widths of the activity tables
    private static final double TIME_WIDTH = 70.0;
    private static final double STUDENT_WIDTH = 160.0;
    private static final double UNDO_WIDTH = 80.0;

    // A student's username is shorter than this
    private static final int STUDENT_NAME_LIMIT = 40;

    // Comes between the student and the rest of an activity's detail
    private static final String NAME_SEPARATOR = ": ";

    // The height of an activity row
    private static final double ACTIVITY_ROW_HEIGHT = 44.0;

    // Column widths of the runs table
    private static final double MODULE_WIDTH = 84.0;
    private static final double SECTION_WIDTH = 76.0;
    private static final double ASSESSMENT_WIDTH = 150.0;
    private static final double STUDENTS_WIDTH = 84.0;
    private static final double AVERAGE_WIDTH = 190.0;
    private static final double STATUS_WIDTH = 150.0;
    private static final double ACTIONS_WIDTH = 180.0;

    // Column widths of the snapshot and activity tables
    private static final double SNAPSHOT_WIDTH = 260.0;
    private static final double SHOWN_WIDTH = 120.0;
    private static final double WHEN_WIDTH = 160.0;
    private static final double ACTION_WIDTH = 220.0;

    // The width of the average bar
    private static final double AVERAGE_BAR_WIDTH = 90.0;

    // The narrowest the average bar may shrink to
    private static final double AVERAGE_BAR_MIN_WIDTH =
            AVERAGE_BAR_WIDTH / 2;

    // The width of the activity search field
    private static final double SEARCH_WIDTH = 260.0;

    // The runs search field, a third wider than the activity one
    private static final double RUNS_SEARCH_WIDTH = SEARCH_WIDTH
            + SEARCH_WIDTH / 3;

    // The gap between a run's name and when it was graded
    private static final double RUN_CELL_GAP = 2.0;

    // The size of the search icon
    private static final double SEARCH_ICON_SIZE = 14.0;

    // The most activity rows drawn at once
    private static final int MAX_ACTIVITY_ROWS = 500;

    // Writes a mark with one decimal place, such as 12.4
    private static final String ONE_DECIMAL_FORMAT = "%.1f";

    // Separates words; a student's username has none
    private static final String SPACE = " ";

    // The activity log actions of a fix decision, as AnomalyService
    // writes them
    private static final String FIX_APPROVED = "Fix approved";
    private static final String FIX_UNDONE = "Fix undone";
    private static final String FIX_REAPPLIED = "Fix reapplied";

    // Shown in a cell that has no value
    private static final String NO_VALUE = "-";

    // Separates a module from its section, such as "CS102 · G1"
    private static final String SECTION_DOT = " · ";

    // Comes between the section and the assessment in a run's key
    private static final String KEY_SEPARATOR = "|";

    // Separates a mark from the maximum, such as "12.4 / 20"
    private static final String OUT_OF = " / ";

    // Separates the graded count from the total, such as "3/40"
    private static final String COUNT_SLASH = "/";

    // The page
    private final VBox page = new VBox(Parts.CARD_GAP);

    // The scrolling page
    private final ScrollPane view;

    // Holds the view picked with the switch, one part under another
    private final VBox body = new VBox(Parts.GAP);

    // The Runs view: a search, the table and a note under it
    private final VBox runsView = new VBox(Parts.GAP);

    // Filters the runs by module, section or assessment
    private final CustomTextField runsSearch = new CustomTextField();

    // How many runs are listed
    private final Label runsCount = Parts.muted("");

    // Shows every run, or only the latest run of each module, section
    // and assessment
    private final ToggleButton allRunsButton = new ToggleButton("All runs");
    private final ToggleButton latestRunsButton =
            new ToggleButton("Latest runs");

    // The Activity view's kind filter
    private final ToggleGroup kindGroup = new ToggleGroup();

    // How many activity entries are listed
    private final Label activityCount = Parts.muted("");

    // The activity, one table per day
    private final VBox activityDays = new VBox(Parts.GAP);

    // The Runs view
    private final PlainTable runsTable = new PlainTable(ROW_HEIGHT);

    // The Backups view
    private final VBox backups = new VBox(Parts.CARD_GAP);

    // The kept backups and the runs inside each, in the Backups view
    private final KeptBackupsView keptBackups =
            new KeptBackupsView(file -> askRestore(file));

    // The Activity view
    private final VBox activity = new VBox(Parts.CARD_GAP);

    // The activity table
    private final PlainTable activityTable =
            new PlainTable(ACTIVITY_ROW_HEIGHT);

    // Filters the activity
    private final CustomTextField activitySearch = new CustomTextField();

    // The three parts of the switch
    private final ToggleButton runsButton = new ToggleButton("Runs");
    private final ToggleButton backupsButton = new ToggleButton("Backups");
    private final ToggleButton activityButton =
            new ToggleButton("Activity");

    /**
     * Creates the Runs tab.
     *
     * @param window the window
     */
    public RunsScreen(FlowWindow window) {
        super(window);
        createRunsTable();
        createActivity();

        VBox header = Parts.header("History", "Every grading run, the backups "
                + "of saved results, and what was done.");
        HBox headerRow = Parts.row(Parts.GAP,
                new Node[] {header, Parts.grow(), createSwitch()});
        page.getChildren().addAll(headerRow, body);
        view = Parts.widePage(new Node[] {page});
        body.setFillWidth(true);
    }

    /**
     * Returns the Runs tab built in the constructor.
     */
    @Override
    public Node getView() {
        return view;
    }

    /**
     * Reloads the runs, backups and activity log.
     */
    @Override
    public void onShow() {
        refresh();
    }

    /**
     * Returns false: the Runs tab has no action bar.
     */
    @Override
    public boolean usesActionBar() {
        return false;
    }

    /**
     * Shows the table of runs.
     */
    public void showRuns() {
        runsButton.setSelected(true);
    }

    /**
     * Shows the backups.
     */
    public void showBackups() {
        backupsButton.setSelected(true);
    }

    /**
     * Shows the activity log.
     */
    public void showActivity() {
        activityButton.setSelected(true);
    }

    // ------------------------------------------------------------ build

    // Builds the Runs, Backups and Activity switch.
    private HBox createSwitch() {
        ToggleGroup group = new ToggleGroup();
        runsButton.setToggleGroup(group);
        backupsButton.setToggleGroup(group);
        activityButton.setToggleGroup(group);
        runsButton.getStyleClass().add("left-pill");
        backupsButton.getStyleClass().add("center-pill");
        activityButton.getStyleClass().add("right-pill");
        runsButton.setSelected(true);
        group.selectedToggleProperty().addListener((observable, before,
                after) -> switchView(before, after));
        HBox bar = new HBox(runsButton, backupsButton, activityButton);
        bar.getStyleClass().add("runs-switch");
        bar.setMinWidth(HBox.USE_PREF_SIZE);
        return bar;
    }

    // Follows a click on Runs, Backups or Activity. Clicking the button
    // that is already on would turn every button off, so it stays on.
    private void switchView(Toggle before, Toggle after) {
        if (after == null) {
            before.setSelected(true);
            return;
        }
        refresh();
    }

    // Adds the columns of the runs table.
    private void createRunsTable() {
        runsTable.addColumn("Run", PlainTable.GROW, Pos.CENTER_LEFT);
        runsTable.addColumn("Students", STUDENTS_WIDTH, Pos.CENTER);
        runsTable.addColumn("Average", AVERAGE_WIDTH, Pos.CENTER_LEFT);
        runsTable.addColumn("Status", STATUS_WIDTH, Pos.CENTER_LEFT);
        runsTable.addColumn("", ACTIONS_WIDTH, Pos.CENTER_RIGHT);
        runsTable.getStyleClass().add("runs-table");

        runsSearch.setPromptText("Search by module, section or assessment");
        runsSearch.setLeft(Parts.icon(Parts.SEARCH, SEARCH_ICON_SIZE));
        runsSearch.setPrefWidth(RUNS_SEARCH_WIDTH);
        runsSearch.setMaxWidth(RUNS_SEARCH_WIDTH);
        runsSearch.textProperty().addListener((observable, before,
                after) -> refresh());
        runsCount.getStyleClass().add("small-text");
        HBox tools = Parts.row(Parts.GAP, new Node[] {runsSearch,
            createRunsFilter(), Parts.grow(), runsCount});
        runsView.getChildren().addAll(tools, runsTable, restoreNote(
                "Current marks the run each section shows on Home, Review "
                + "and Statistics for the assessment in use. Restore makes "
                + "an earlier run current; the other is kept here, so you "
                + "can switch back."));
    }

    // Builds the All runs | Latest runs switch. All runs is on at first.
    private HBox createRunsFilter() {
        ToggleGroup group = new ToggleGroup();
        allRunsButton.setToggleGroup(group);
        latestRunsButton.setToggleGroup(group);
        allRunsButton.setSelected(true);
        allRunsButton.getStyleClass().add("left-pill");
        latestRunsButton.getStyleClass().add("right-pill");
        Parts.tip(allRunsButton, "Every grading run, newest first.");
        Parts.tip(latestRunsButton, "Only the latest run of each module, "
                + "section and assessment.");
        group.selectedToggleProperty().addListener((observable, before,
                after) -> runsFilterClicked(before, after));
        HBox bar = new HBox(allRunsButton, latestRunsButton);
        bar.getStyleClass().add("segmented");
        bar.setMinWidth(HBox.USE_PREF_SIZE);
        return bar;
    }

    // Follows a click on All runs or Latest runs; clicking the one that
    // is on keeps it on.
    private void runsFilterClicked(Toggle before, Toggle after) {
        if (after == null) {
            before.setSelected(true);
            return;
        }
        refreshRuns();
    }

    // Builds the search field and the columns of the activity table.
    private void createActivity() {
        activitySearch.setPromptText("Search the activity");
        activitySearch.setLeft(Parts.icon(Parts.SEARCH, SEARCH_ICON_SIZE));
        activitySearch.setPrefWidth(SEARCH_WIDTH);
        activitySearch.setMaxWidth(SEARCH_WIDTH);
        activitySearch.textProperty().addListener((observable, before,
                after) -> refreshActivity());
        activityTable.addColumn("When", WHEN_WIDTH, Pos.CENTER_LEFT);
        activityTable.addColumn("Action", ACTION_WIDTH, Pos.CENTER_LEFT);
        activityTable.addColumn("Detail", PlainTable.GROW,
                Pos.CENTER_LEFT);
        // The kinds of activity, as a row of filter buttons
        HBox kinds = new HBox();
        kinds.getStyleClass().add("segmented");
        int lastIndex = KINDS.length - 1;
        ToggleButton all = createKind(KINDS[0], "left-pill");
        all.setSelected(true);
        kinds.getChildren().add(all);
        for (int index = 1; index < lastIndex; index++) {
            kinds.getChildren().add(createKind(KINDS[index], "center-pill"));
        }
        kinds.getChildren().add(createKind(KINDS[lastIndex], "right-pill"));
        kinds.setMinWidth(HBox.USE_PREF_SIZE);
        kindGroup.selectedToggleProperty().addListener((observable,
                before, after) -> kindChosen(before, after));
        activityCount.getStyleClass().add("small-text");
        HBox tools = Parts.row(Parts.GAP, new Node[] {activitySearch,
            kinds, Parts.grow(), activityCount});
        activity.getChildren().addAll(tools, activityDays);
    }

    // Returns one filter button of the activity, in the shape given.
    private ToggleButton createKind(String name, String shape) {
        ToggleButton kind = new ToggleButton(name);
        kind.setToggleGroup(kindGroup);
        kind.setUserData(name);
        kind.getStyleClass().add(shape);
        return kind;
    }

    // Shows the view picked with the switch.
    private void refresh() {
        if (backupsButton.isSelected()) {
            refreshBackups();
            body.getChildren().setAll(backups);
        } else if (activityButton.isSelected()) {
            refreshActivity();
            body.getChildren().setAll(activity);
        } else {
            refreshRuns();
            body.getChildren().setAll(runsView);
        }
    }

    // ------------------------------------------------------------- runs

    // Lists the runs, newest first: every run, or only the latest run of
    // each module, section and assessment.
    private void refreshRuns() {
        runsTable.clearRows();
        if (!getBackend().supports(Capability.HISTORY)) {
            runsTable.showMessage(empty("Earlier runs are not available "
                    + "here.", "Grade a batch on Grading to see its "
                    + "results.", null, null));
            return;
        }

        List<RunRecord> records = getBackend().listRuns();
        if (records.isEmpty()) {
            showNoRuns();
            return;
        }

        Map<String, GradingRun> shown = new HashMap<>();
        Map<String, Map<String, GradingRun>> onHome = new HashMap<>();
        Set<String> newerSeen = new HashSet<>();
        String text = runsSearch.getText().trim().toLowerCase();
        int listed = 0;
        for (RunRecord record : records) {
            // Runs are newest first, so a key seen before has a newer run
            String key = keyOf(record.getRun());
            boolean hasNewer = newerSeen.contains(key);
            newerSeen.add(key);
            boolean hidden = hasNewer && latestRunsButton.isSelected();
            if (!hidden && runMatches(record.getRun(), text)) {
                addRunRow(record, isOnHome(record, records, onHome),
                        isShown(record, shown), hasNewer);
                listed++;
            }
        }
        runsCount.setText(Parts.plural(listed, "run"));
        if (listed == 0) {
            runsTable.showMessage(empty("No run matches \""
                    + runsSearch.getText().trim() + "\".", "Try a module, "
                    + "section or assessment name.", null, null));
        }
    }

    // Says whether a run's module, section or assessment holds the text.
    private boolean runMatches(GradingRun run, String text) {
        if (text.isEmpty()) {
            return true;
        }
        String all = labelOf(run);
        if (run.getAssessment() != null) {
            all += SPACE + run.getAssessment().getName();
        }
        return all.toLowerCase().contains(text);
    }

    // Explains why no run is listed.
    private void showNoRuns() {
        if (savingIsOff()) {
            runsTable.showMessage(empty("Runs are not kept.", "Saving "
                    + "results is switched off in Settings.",
                    "Open Settings",
                    () -> getWindow().showPage(Page.SETTINGS)));
            return;
        }
        runsTable.showMessage(empty("No runs yet.", "Each batch you grade "
                + "is listed here.", "Go to Grading",
                () -> getWindow().showPage(Page.MARKING)));
    }

    // Adds one run to the table. A current run is the one Home, Review
    // and Statistics show; a shown run is the backend's run of its
    // section, which Restore replaces.
    private void addRunRow(RunRecord record, boolean current,
            boolean shown, boolean hasNewer) {
        GradingRun run = record.getRun();
        Section section = run.getSection();
        String module = NO_VALUE;
        String sectionCode = NO_VALUE;
        if (section != null) {
            module = section.getModuleCode();
            sectionCode = section.getSectionCode();
        }
        String assessmentName = NO_VALUE;
        if (run.getAssessment() != null) {
            assessmentName = run.getAssessment().getName();
        }
        Label assessment = new Label(assessmentName);
        assessment.setMinWidth(0);
        Parts.tip(assessment, "The assessment this run was graded with. "
                + "Opening or resuming the run switches to it.");
        Label name = Parts.strong(module + SECTION_DOT + sectionCode);
        name.setMinWidth(Label.USE_PREF_SIZE);
        HBox title = Parts.row(Parts.GAP_SMALL, new Node[] {name,
            assessment});
        if (current) {
            Label badge = new Label("Current");
            badge.getStyleClass().addAll("small-text", "current-badge");
            badge.setMinWidth(Label.USE_PREF_SIZE);
            Parts.tip(badge, "The run Home, Review and Statistics show "
                    + "for this section.");
            title.getChildren().add(badge);
        }
        Label when = Parts.muted("Graded " + when(run));
        when.getStyleClass().add("small-text");
        Hyperlink settings = Parts.link("View settings",
                () -> showSettings(run));
        settings.getStyleClass().add("small-text");
        HBox whenLine = Parts.row(Parts.GAP_SMALL, new Node[] {when,
            settings});
        VBox runCell = new VBox(RUN_CELL_GAP, title, whenLine);
        runCell.setAlignment(Pos.CENTER_LEFT);
        runCell.setMinWidth(0);
        // The whole cell opens the settings, so it shows a hand and a tip
        runCell.getStyleClass().add("run-cell");
        runCell.setCursor(Cursor.HAND);
        runCell.setOnMouseClicked(event -> showSettings(run));
        Tooltip.install(runCell, new Tooltip("Click to see the files and "
                + "settings this run was graded with."));
        Label students = new Label(String.valueOf(
                run.getSubmissions().size()));
        runsTable.addRow(new Node[] {runCell, students, average(run),
            status(run, hasNewer), actions(record, shown)});
    }

    // Says when a run was graded. A run read back from a database row
    // with no start time has none.
    private static String when(GradingRun run) {
        if (run.getStartedAt() == null) {
            return "date unknown";
        }
        return run.getStartedAt().format(WHEN);
    }

    // Shows the average of a run as a small bar and "x / max".
    private Node average(GradingRun run) {
        double max = run.getAssessment().getMaxTotal();
        double passPercent = MarkStats.parsePassPercent(
                getBackend().getSetting(ConfigKeys.PASS_MARK_PERCENT));
        MarkStats stats = MarkStats.of(run.getSubmissions(), max,
                passPercent);
        if (stats.isEmpty() || max <= 0) {
            return Parts.muted(NO_VALUE);
        }
        double share = stats.getMean() / max;
        StackPane bar = Parts.track(share, null);
        bar.setMinWidth(AVERAGE_BAR_MIN_WIDTH);
        bar.setPrefWidth(AVERAGE_BAR_WIDTH);
        bar.setMaxWidth(AVERAGE_BAR_WIDTH);
        Label value = new Label(String.format(ONE_DECIMAL_FORMAT,
                stats.getMean()) + OUT_OF + Parts.mark(max));
        value.setMinWidth(Label.USE_PREF_SIZE);
        return Parts.row(Parts.GAP_SMALL, new Node[] {bar, value});
    }

    // Returns the pill that says how far a run got. The newest finished
    // run of each module, section and assessment says so in green; an
    // older one of the same is an earlier run.
    private Node status(GradingRun run, boolean hasNewer) {
        Label pill = Parts.pill("Latest run", Tone.GOOD);
        String tip = "The newest run of this module, section and "
                + "assessment. Every student was graded.";
        int waiting = countWaiting(run);
        if (hasNewer) {
            pill = Parts.pill("Earlier run", Tone.NEUTRAL);
            tip = "A newer run of this module, section and assessment was "
                    + "graded after this one.";
        } else if (waiting > 0) {
            int total = run.getSubmissions().size();
            int graded = total - waiting;
            pill = Parts.pill("Stopped " + graded + COUNT_SLASH + total,
                    Tone.WARN);
            tip = "Grading was cancelled, or GradeZZY closed, before every "
                    + "student was graded. Resume grades the rest; marks "
                    + "already given are kept.";
        }
        Parts.tip(pill, tip);
        return pill;
    }

    // Returns the buttons that fit a run. Every run can be opened and have
    // its statistics shown; an earlier run can also be restored, and a
    // stopped one resumed.
    private HBox actions(RunRecord record, boolean shown) {
        GradingRun run = record.getRun();
        HBox buttons = Parts.row(Parts.GAP_SMALL);
        buttons.setAlignment(Pos.CENTER_RIGHT);
        boolean canRestore = !shown && record.isRestorable()
                && getBackend().supports(Capability.RESTORE_PREVIEW);
        if (canRestore) {
            buttons.getChildren().add(restoreButton(record));
        }

        boolean canResume = shown && countWaiting(run) > 0
                && getBackend().supports(Capability.RESUME_RUN);
        boolean hasStatistics = getBackend().supports(Capability.STATISTICS)
                && run.getSection() != null && run.getAssessment() != null
                && StatisticsScreen.isGraded(run);
        if (canResume) {
            Button resume = Parts.small("Resume",
                    () -> getWindow().resumeRun(run));
            Parts.tip(resume, "Grade the students not graded yet. Marks "
                    + "already given are kept.");
            buttons.getChildren().add(resume);
        }
        if (hasStatistics) {
            Button statistics = Parts.small("Statistics",
                    () -> getWindow().openStatistics(run));
            Parts.tip(statistics, "Charts and figures for this run.");
            buttons.getChildren().add(statistics);
        }
        Button open = Parts.small("Open", () -> getWindow().openRun(run));
        Parts.tip(open, "Show this run's students and marks on Review, "
                + "where you can also export them.");
        buttons.getChildren().add(open);
        return buttons;
    }

    // Shows the files and grading settings a run was graded with.
    private void showSettings(GradingRun run) {
        getWindow().showSheet(RunSettingsSheet.create(getWindow(), run));
    }

    // Says whether a run is the one Home, Review and Statistics show for
    // its section: by the same rule as Home's section cards, for the
    // assessment the module uses and the run open on Review. The runs
    // are looked up once per module and kept in a map.
    private boolean isOnHome(RunRecord record, List<RunRecord> records,
            Map<String, Map<String, GradingRun>> onHome) {
        GradingRun run = record.getRun();
        if (run.getSection() == null || run.getId() == null) {
            return false;
        }
        String module = run.getSection().getModuleCode();
        if (!onHome.containsKey(module)) {
            onHome.put(module, SectionProgress.findShownRuns(records,
                    module, assessmentOf(module), getState().getRun()));
        }
        GradingRun current = onHome.get(module).get(
                run.getSection().getSectionCode());
        return current != null && run.getId().equals(current.getId());
    }

    // Returns the assessment Home shows for a module: the one in use when
    // it is the module's own, else the module's first, as Home switches
    // to when the module is chosen.
    private String assessmentOf(String module) {
        String inUse = "";
        if (getBackend().getAssessment() != null) {
            inUse = getBackend().getAssessment().getName();
        }
        List<SavedAssessment> own = getWindow().listModuleAssessments(
                module);
        boolean ownInUse = false;
        for (SavedAssessment saved : own) {
            if (saved.getName().equalsIgnoreCase(inUse)) {
                ownInUse = true;
            }
        }
        if (ownInUse || own.isEmpty()) {
            return inUse;
        }
        return own.get(0).getName();
    }

    // Says whether a run is the one shown for its section now. The shown
    // runs are looked up once per section and kept in a map.
    private boolean isShown(RunRecord record,
            Map<String, GradingRun> shown) {
        if (record.isCurrent()) {
            return true;
        }
        GradingRun run = record.getRun();
        if (run.getSection() == null) {
            return false;
        }
        String label = labelOf(run);
        if (!shown.containsKey(label)) {
            shown.put(label, getBackend().getRun(run.getSection()));
        }
        GradingRun current = shown.get(label);
        return current != null && current.getId() != null
                && current.getId().equals(run.getId());
    }

    // Returns the Restore button of a run, which waits while a batch is
    // being graded.
    private Button restoreButton(RunRecord record) {
        Button button = Parts.small("Restore", () -> restore(record));
        button.setDisable(getState().isAnyGrading());
        Parts.tip(button, "Show this run for its section again, in place of "
                + "the one shown now. A copy of the run shown now is "
                + "kept.");
        return button;
    }

    // Asks, then shows an earlier run in place of the one shown now.
    private void restore(RunRecord record) {
        if (blockedByGrading()) {
            return;
        }
        String section = labelOf(record.getRun());
        getWindow().confirm("Restore this run?", "It is shown for "
                + section + " instead of the run shown now. A copy of "
                + "the run shown now is kept, so you can restore it to go "
                + "back. Original zip files and exports are not changed.",
                "Restore", () -> restoreRun(record));
    }

    // Shows an earlier run in place of the one shown now, once the
    // instructor has confirmed.
    private void restoreRun(RunRecord record) {
        if (blockedByGrading()) {
            return;
        }
        // The run shown now, so Undo can show it again
        GradingRun before = null;
        if (record.getRun().getSection() != null) {
            before = getBackend().getRun(record.getRun().getSection());
        }
        ActionResult result = getBackend().restore(record);
        boolean canUndo = result.isDone() && before != null
                && before.getId() != null;
        if (canUndo) {
            String beforeId = before.getId();
            afterRestore(result, () -> undoRestore(beforeId));
        } else {
            afterRestore(result, null);
        }
    }

    // Shows the run that was shown before a restore again.
    private void undoRestore(String runId) {
        if (blockedByGrading()) {
            return;
        }
        for (RunRecord record : getBackend().listRuns()) {
            if (runId.equals(record.getRun().getId())) {
                afterRestore(getBackend().restore(record), null);
                return;
            }
        }
        getWindow().notify("The earlier run is no longer listed, so it "
                + "cannot be shown again.");
    }

    // Says what a restore did. After a restore the run on Review may no
    // longer be saved, so Grading starts again on Set up.
    private void afterRestore(ActionResult result, Runnable undo) {
        if (undo == null) {
            getWindow().notify(result.getMessage());
        } else {
            getWindow().notify(result.getMessage(), "Undo", undo);
        }
        if (result.isDone()) {
            getWindow().forgetRun();
        }
        refresh();
    }

    // Says so and returns true while a batch is being graded. Backing up
    // or restoring then would mix the batch into other saved results.
    private boolean blockedByGrading() {
        if (!getState().isAnyGrading()) {
            return false;
        }
        getWindow().notify("Wait until every section has finished "
                + "grading.");
        return true;
    }

    // Counts the students of a run still waiting to be graded, as Home
    // and Set up count them.
    private int countWaiting(GradingRun run) {
        return SectionProgress.countWaiting(run);
    }

    // Returns the section of a run, such as "CS102 G1".
    private String labelOf(GradingRun run) {
        if (run.getSection() == null) {
            return "an unknown section";
        }
        return run.getSection().getLabel();
    }

    // Names the module, section and assessment of a run together, so
    // only runs of the same lab test of a section are older or newer
    // than each other.
    private String keyOf(GradingRun run) {
        String assessmentName = NO_VALUE;
        if (run.getAssessment() != null) {
            assessmentName = run.getAssessment().getName();
        }
        return labelOf(run) + KEY_SEPARATOR + assessmentName;
    }

    // ---------------------------------------------------------- backups

    // Shows what can be backed up and restored.
    private void refreshBackups() {
        backups.getChildren().clear();
        boolean canBackUp = getBackend().supports(Capability.BACKUP);
        boolean canRestoreFile = getBackend().supports(Capability.RESTORE)
                && !getBackend().isDemo();
        boolean canPreview = getBackend().supports(
                Capability.RESTORE_PREVIEW);
        boolean nothingToShow = savingIsOff() || !(canBackUp
                || canRestoreFile || canPreview);
        if (nothingToShow) {
            PlainTable none = new PlainTable(ROW_HEIGHT);
            none.showMessage(empty("No backups.", "Saving results is "
                    + "switched off in Settings, so there is nothing to "
                    + "back up.", "Open Settings",
                    () -> getWindow().showPage(Page.SETTINGS)));
            backups.getChildren().add(none);
            return;
        }

        if (canBackUp) {
            backups.getChildren().add(backupCard());
        }
        if (canRestoreFile) {
            backups.getChildren().add(restoreFileCard());
        }
        if (canBackUp) {
            backups.getChildren().add(keptBackups.build(
                    getBackend().listBackups(),
                    getBackend().getBackupKeepCount(),
                    !getState().isAnyGrading()));
        }
        if (canPreview) {
            backups.getChildren().addAll(Parts.title("Saved copies"),
                    snapshotTable(), restoreNote("Restore on a saved copy "
                    + "shows that run for its section again, as on the Runs "
                    + "list. "
                    + "Restore from a backup file is different: it "
                    + "replaces every saved result with the file's copy."));
        }
    }

    // Builds the card with Back up now.
    private VBox backupCard() {
        String where = "A backup is a copy of every saved run.";
        if (!getBackend().isDemo()) {
            String folder = getBackend().getSetting(ConfigKeys.BACKUP_DIR);
            String keep = getBackend().getSetting(
                    ConfigKeys.BACKUP_KEEP_COUNT);
            where = where + " Backups are saved in " + folder + ", and the "
                    + "newest " + keep + " are kept.";
        }
        Label text = Parts.muted(where + gradingNote());
        text.setWrapText(true);
        VBox words = new VBox(Parts.GAP_TINY,
                Parts.strong("Back up the saved results"), text);
        Node grow = Parts.grow();
        Button backUpButton = Parts.secondary("Back up now",
                () -> backUp());
        backUpButton.setDisable(getState().isAnyGrading());
        HBox row = Parts.row(Parts.GAP,
                new Node[] {words, grow, backUpButton});
        return Parts.card(null, new Node[] {row});
    }

    // Builds the card that restores a backup file.
    private VBox restoreFileCard() {
        Label text = Parts.muted("Replaces every saved result with the "
                + "copy in a backup file. The results saved now are backed "
                + "up first, so the restore can be undone." + gradingNote());
        text.setWrapText(true);
        VBox words = new VBox(Parts.GAP_TINY,
                Parts.strong("Restore from a backup file"), text);
        Node grow = Parts.grow();
        Button chooseButton = Parts.secondary("Choose a backup",
                () -> chooseBackup());
        chooseButton.setDisable(getState().isAnyGrading());
        HBox row = Parts.row(Parts.GAP,
                new Node[] {words, grow, chooseButton});
        return Parts.card(null, new Node[] {row});
    }

    // Returns a small note under a table that says what Restore does.
    private Label restoreNote(String text) {
        Label note = Parts.muted(text);
        note.getStyleClass().add("small-text");
        note.setWrapText(true);
        return note;
    }

    // Returns a sentence saying why the buttons wait, while grading.
    private String gradingNote() {
        if (getState().isAnyGrading()) {
            return " Available when every section has finished grading.";
        }
        return "";
    }

    // Builds the table of runs with a saved copy that can be restored.
    private PlainTable snapshotTable() {
        PlainTable table = new PlainTable(ROW_HEIGHT);
        table.addColumn("Module", MODULE_WIDTH, Pos.CENTER_LEFT);
        table.addColumn("Section", SECTION_WIDTH, Pos.CENTER_LEFT);
        table.addColumn("Graded", PlainTable.GROW, Pos.CENTER_LEFT);
        table.addColumn("Saved copy", SNAPSHOT_WIDTH, Pos.CENTER_LEFT);
        table.addColumn("Status", SHOWN_WIDTH, Pos.CENTER_LEFT);
        table.addColumn("", ACTIONS_WIDTH, Pos.CENTER_RIGHT);

        Map<String, GradingRun> shown = new HashMap<>();
        for (RunRecord record : getBackend().listRuns()) {
            if (record.isRestorable()) {
                addSnapshotRow(table, record, isShown(record, shown));
            }
        }
        if (table.getRowCount() == 0) {
            table.showMessage(empty("No saved copies yet.", "Press Back "
                    + "up now to keep a copy of the run shown now.", null,
                    null));
        }
        return table;
    }

    // Adds one run with a saved copy to the table.
    private void addSnapshotRow(PlainTable table, RunRecord record,
            boolean shown) {
        GradingRun run = record.getRun();
        String module = NO_VALUE;
        String sectionCode = NO_VALUE;
        if (run.getSection() != null) {
            module = run.getSection().getModuleCode();
            sectionCode = run.getSection().getSectionCode();
        }
        Node shownCell = Parts.muted(NO_VALUE);
        HBox buttons = Parts.row(Parts.GAP_SMALL);
        buttons.setAlignment(Pos.CENTER_RIGHT);
        if (shown) {
            shownCell = Parts.pill("Current", Tone.GOOD);
        } else {
            buttons.getChildren().add(restoreButton(record));
        }
        table.addRow(new Node[] {Parts.strong(module),
            Parts.strong(sectionCode),
            Parts.muted(when(run)),
            new Label(record.getBackupText()), shownCell, buttons});
    }

    // Backs up the saved results now.
    private void backUp() {
        if (blockedByGrading()) {
            return;
        }
        ActionResult result = getBackend().backupNow();
        getWindow().notify(result.getMessage());
        refresh();
    }

    // Asks for a backup file, then restores it once confirmed.
    private void chooseBackup() {
        if (blockedByGrading()) {
            return;
        }
        FileChooser chooser = new FileChooser();
        chooser.setTitle("Choose a backup made by Back up now");
        Path folder = Paths.get(getBackend().getSetting(
                ConfigKeys.BACKUP_DIR));
        if (Files.isDirectory(folder)) {
            chooser.setInitialDirectory(folder.toFile());
        } else {
            Choosers.prepare(chooser);
        }
        File file = chooser.showOpenDialog(getWindow().getWindow());
        Choosers.remember(file);
        if (file == null) {
            return;
        }
        askRestore(file.toPath());
    }

    // Asks before restoring a backup file, from Choose a backup or from
    // Restore this backup on a kept backup.
    private void askRestore(Path backupFile) {
        if (blockedByGrading()) {
            return;
        }
        getWindow().confirm("Restore " + backupFile.getFileName() + "?",
                "The saved results are replaced by the ones in this file. "
                + "The results saved now are backed up first, so you can "
                + "undo it. Original zip files and exports are not "
                + "changed.",
                "Restore", () -> restoreFile(backupFile));
    }

    // Puts back the saved results from a backup file, once the
    // instructor has confirmed. The backend backs up the results saved
    // now first and returns that copy, so Undo can put them back.
    private void restoreFile(Path backupFile) {
        if (blockedByGrading()) {
            return;
        }
        ActionResult result = getBackend().restoreBackup(backupFile);
        Path undoFile = result.getPath();
        if (result.isDone() && undoFile != null) {
            afterRestore(result, () -> undoFileRestore(undoFile));
        } else {
            afterRestore(result, null);
        }
    }

    // Puts back the results saved just before a backup file was restored.
    private void undoFileRestore(Path safety) {
        if (blockedByGrading()) {
            return;
        }
        afterRestore(getBackend().restoreBackup(safety), null);
    }

    // --------------------------------------------------------- activity

    // Lists the activity, newest first, matching the search.
    private void refreshActivity() {
        activityTable.clearRows();
        activityDays.getChildren().setAll(activityTable);
        activityCount.setText("");
        boolean logOff = !getBackend().supports(Capability.AUDIT_LOG)
                || FALSE.equalsIgnoreCase(getBackend().getSetting(
                ConfigKeys.AUDIT_ENABLED));
        if (logOff) {
            activitySearch.setDisable(true);
            activityTable.showMessage(empty("The activity log is off.",
                    "Switch on Keep an activity log in Settings.",
                    "Open Settings",
                    () -> getWindow().showPage(Page.SETTINGS)));
            return;
        }
        activitySearch.setDisable(false);

        List<AuditEntry> entries = getBackend().listAuditEntries();
        if (entries.isEmpty()) {
            activityTable.showMessage(empty("Nothing recorded yet.",
                    "Approvals, undos and exports are listed here with "
                    + "the time.", null, null));
            return;
        }

        List<AuditEntry> sorted = new ArrayList<>(entries);
        sorted.sort(new AuditEntryNewestFirstComparator());
        String kind = String.valueOf(kindGroup.getSelectedToggle()
                .getUserData());
        activityDays.getChildren().clear();
        String text = activitySearch.getText().trim().toLowerCase();
        int shown = fillActivityDays(sorted, text, kind);
        activityCount.setText(Parts.plural(shown, "entry", "entries"));
        if (shown == 0) {
            activityDays.getChildren().setAll(activityTable);
            activityTable.showMessage(empty("No activity matches \""
                    + activitySearch.getText().trim() + "\".", "Try "
                    + "another word.", null, null));
        }
    }

    // Adds the entries that match the search and the kind, at most
    // MAX_ACTIVITY_ROWS, under a title and in a table per day, and
    // returns how many were added.
    private int fillActivityDays(List<AuditEntry> sorted, String text,
            String kind) {
        int shown = 0;
        LocalDate day = null;
        PlainTable dayTable = null;
        for (AuditEntry entry : sorted) {
            boolean listed = shown < MAX_ACTIVITY_ROWS
                    && matches(entry, text) && isKind(entry, kind);
            if (listed) {
                LocalDate entryDay = entry.getTimestamp().toLocalDate();
                if (!entryDay.equals(day)) {
                    day = entryDay;
                    dayTable = newDayTable();
                    Label dayTitle = Parts.strong(dayName(day));
                    dayTitle.getStyleClass().add("activity-day");
                    activityDays.getChildren().addAll(dayTitle, dayTable);
                }
                addActivityRow(dayTable, entry);
                shown++;
            }
        }
        return shown;
    }

    // Says whether an entry holds the searched text.
    private boolean matches(AuditEntry entry, String text) {
        if (text.isEmpty()) {
            return true;
        }
        String all = (entry.getAction() + SPACE + entry.getDetail())
                .toLowerCase();
        return all.contains(text);
    }

    // Returns what takes an activity entry back, or null when it cannot
    // be taken back or already was: a kept fix that is still kept, a
    // rejected or reapplied fix still in that state, or a flag decision
    // that is still the flag's decision. A line with a reference names
    // the flag (or, for a checked 0, the run) it is about, so the same
    // words in another run do not match; an older line without one is
    // matched by its words.
    private Runnable findUndo(AuditEntry entry, String student,
            String message) {
        if (getState().isAnyGrading()) {
            return null;
        }
        // A delete made since GradeZZY opened can be put back
        Supplier<ActionResult> restore = ActivityUndo.find(
                entry.getAction(), entry.getDetail());
        if (restore != null) {
            return () -> putBack(restore, entry);
        }
        if (student.isEmpty()) {
            return null;
        }
        String action = entry.getAction();
        boolean fix = FIX_APPROVED.equals(action);
        boolean undone = FIX_UNDONE.equals(action);
        boolean reapplied = FIX_REAPPLIED.equals(action);
        boolean decision = isDecisionAction(action);
        boolean undoable = fix || undone || reapplied || decision;
        if (!undoable) {
            return null;
        }
        // The decision the log line made; Undo is offered only while it
        // is still the current one, so an older line cannot take back a
        // newer decision
        ReviewDecision logged = ReviewDecision.REVIEWED;
        if (ReviewDecision.AUDIT_LOOKS_FINE.equals(action)) {
            logged = ReviewDecision.REJECTED;
        }
        String reference = entry.getReference();
        for (GradingRun run : runsToSearch()) {
            boolean runNamed = reference.isEmpty()
                    || reference.equals(run.getId());
            for (Submission submission : run.getSubmissions()) {
                if (submission.getUsername().equals(student)) {
                    Runnable undo = findStudentUndo(run, runNamed,
                            submission, entry, message, logged);
                    if (undo != null) {
                        return undo;
                    }
                }
            }
        }
        return null;
    }

    // Returns what takes back a log line's action on one student's
    // result in a run: a checked 0, or one of the student's flags. Returns
    // null when the line is about nothing that can be taken back.
    private Runnable findStudentUndo(GradingRun run, boolean runNamed,
            Submission submission, AuditEntry entry, String message,
            ReviewDecision logged) {
        String reference = entry.getReference();
        String action = entry.getAction();
        boolean decision = isDecisionAction(action);
        boolean zeroCheckLine = decision
                && logged == ReviewDecision.REVIEWED
                && DashboardBackend.ZERO_CHECK_WORDS.equals(message)
                && runNamed
                && getBackend().isZeroChecked(submission);
        if (zeroCheckLine) {
            return () -> takeBackZeroCheck(submission);
        }
        for (Anomaly anomaly : submission.getAnomalies()) {
            if (isAbout(message, reference, anomaly, !decision)) {
                Runnable undo = findAnomalyUndo(run, anomaly, action,
                        logged);
                if (undo != null) {
                    return undo;
                }
            }
        }
        return null;
    }

    // Returns what takes back a log line's action on one anomaly, or null
    // when the anomaly is no longer in the state the line left it in.
    private Runnable findAnomalyUndo(GradingRun run, Anomaly anomaly,
            String action, ReviewDecision logged) {
        boolean keptFix = FIX_APPROVED.equals(action)
                && anomaly.getFixStatus() == FixStatus.APPROVED
                && getBackend().supports(Capability.REPAIR_DECISION_UNDO);
        if (keptFix) {
            return () -> takeBackFix(run, anomaly);
        }
        boolean undoneFix = FIX_UNDONE.equals(action)
                && anomaly.getFixStatus() == FixStatus.UNDONE
                && UndoFixJob.canApplyAgain(getBackend(), anomaly);
        if (undoneFix) {
            return () -> runFixJob(run, anomaly, true);
        }
        boolean reappliedFix = FIX_REAPPLIED.equals(action)
                && anomaly.getFixStatus() == FixStatus.APPLIED
                && getBackend().supports(Capability.REPAIR_DECISIONS);
        if (reappliedFix) {
            return () -> runFixJob(run, anomaly, false);
        }
        boolean sameDecision = isDecisionAction(action)
                && getBackend().getDecision(anomaly) == logged;
        if (sameDecision) {
            return () -> takeBackDecision(anomaly);
        }
        return null;
    }

    // Says whether a log line's action is a decision on a flag.
    private static boolean isDecisionAction(String action) {
        return ReviewDecision.AUDIT_CHECKED.equals(action)
                || ReviewDecision.AUDIT_KEPT_FLAGGED.equals(action)
                || ReviewDecision.AUDIT_LOOKS_FINE.equals(action);
    }

    // Shows the kind of activity picked. Picking the chosen kind again
    // would choose none, so it stays chosen.
    private void kindChosen(Toggle before, Toggle after) {
        if (after == null) {
            before.setSelected(true);
            return;
        }
        refreshActivity();
    }

    // Takes back one activity line, then redraws the log.
    private void undoActivity(Runnable undo) {
        undo.run();
        refreshActivity();
    }

    // Puts back a delete from the activity log, and says how it went.
    private void putBack(Supplier<ActionResult> restore, AuditEntry entry) {
        ActionResult result = restore.get();
        if (result.isDone()) {
            getWindow().notify("Put back: " + entry.getDetail() + ".");
        } else {
            getWindow().notify(result.getMessage());
        }
    }

    // Says whether a log line is about an anomaly: by its id when the
    // line has a reference, else by its words. A fix line may end with a
    // space and the outcome.
    private boolean isAbout(String message, String reference,
            Anomaly anomaly, boolean fixLine) {
        if (!reference.isEmpty()) {
            return reference.equals(anomaly.getId());
        }
        String words = anomaly.getMessage();
        if (fixLine) {
            return message.equals(words)
                    || message.startsWith(words + SPACE);
        }
        return message.equals(words);
    }

    // Undoes a fix, or applies a rejected one again. Live mode grades
    // the student again, so it runs in the background, one at a time.
    private void runFixJob(GradingRun run, Anomaly anomaly,
            boolean applyAgain) {
        RepairItem repair = findRepair(run, anomaly);
        if (repair == null) {
            getWindow().notify("That fix can no longer be changed.");
            return;
        }
        DashboardBackend backend = getBackend();
        if (backend.isDemo() && applyAgain) {
            getWindow().notify(backend.reapplyRepair(repair).getMessage());
            return;
        } else if (backend.isDemo()) {
            getWindow().notify(backend.rejectRepair(repair).getMessage());
            return;
        }
        UndoFixJob job = new UndoFixJob(backend, repair, applyAgain);
        boolean started = job.startOnce(getState(), run,
                result -> fixJobFinished(result));
        if (!started) {
            getWindow().notify("Wait until the other fix has finished.");
            return;
        }
        getWindow().notify("Grading " + anomaly.getUsername()
                + " again. This can take a minute.");
    }

    // Tells how a fix job ended and redraws the activity log.
    private void fixJobFinished(ActionResult result) {
        getWindow().notify(result.getMessage());
        refreshActivity();
    }

    // Finds the repair item of an anomaly in a run, or null.
    private RepairItem findRepair(GradingRun run, Anomaly anomaly) {
        for (RepairItem repair : getBackend().listRepairs(run)) {
            if (repair.getAnomaly() == anomaly) {
                return repair;
            }
        }
        return null;
    }

    // Returns the run on Review and every listed run, each once.
    private List<GradingRun> runsToSearch() {
        List<GradingRun> runs = new ArrayList<>();
        if (getState().getRun() != null) {
            runs.add(getState().getRun());
        }
        if (getBackend().supports(Capability.HISTORY)) {
            for (RunRecord record : getBackend().listRuns()) {
                if (!runs.contains(record.getRun())) {
                    runs.add(record.getRun());
                }
            }
        }
        return runs;
    }

    // Takes back a student's zero check and says so.
    private void takeBackZeroCheck(Submission submission) {
        ActionResult result = getBackend().setZeroChecked(submission,
                false);
        getWindow().notify(result.getMessage());
    }

    // Takes back a kept fix, so it waits for review again.
    private void takeBackFix(GradingRun run, Anomaly anomaly) {
        RepairItem repair = findRepair(run, anomaly);
        if (repair == null) {
            getWindow().notify("That fix can no longer be taken back.");
            return;
        }
        getWindow().notify(getBackend().undoRepairDecision(repair)
                .getMessage());
    }

    // Puts a flag or problem back to be checked again.
    private void takeBackDecision(Anomaly anomaly) {
        getBackend().setDecision(anomaly, ReviewDecision.OPEN);
        getWindow().notify("Put the item for " + anomaly.getUsername()
                + " back to check. No mark changed.");
    }

    // Builds the table of one day's activity.
    private PlainTable newDayTable() {
        PlainTable table = new PlainTable(ACTIVITY_ROW_HEIGHT);
        table.addColumn("Time", TIME_WIDTH, Pos.CENTER_LEFT);
        table.addColumn("Action", ACTION_WIDTH, Pos.CENTER_LEFT);
        table.addColumn("Student", STUDENT_WIDTH, Pos.CENTER_LEFT);
        table.addColumn("Detail", PlainTable.GROW, Pos.CENTER_LEFT);
        table.addColumn("", UNDO_WIDTH, Pos.CENTER_RIGHT);
        return table;
    }

    // Names a day, such as "Today", "Yesterday" or "4 Nov 2026".
    private String dayName(LocalDate day) {
        LocalDate today = LocalDate.now();
        if (day.equals(today)) {
            return "Today";
        } else if (day.equals(today.minusDays(1))) {
            return "Yesterday";
        }
        return day.format(DAY);
    }

    // Says whether an entry is of the kind picked, by its action's words.
    private boolean isKind(AuditEntry entry, String kind) {
        String action = entry.getAction().toLowerCase();
        if (KINDS[0].equals(kind)) {
            return true;
        } else if ("Grading".equals(kind)) {
            return action.contains("grading") || action.contains("run")
                    || action.contains("graded")
                    || action.contains("mark change")
                    || action.contains("result") || action.contains("student");
        } else if ("Fixes".equals(kind)) {
            return action.contains("fix");
        } else if ("Flags".equals(kind)) {
            return action.contains("flag") || action.contains("check");
        }
        return action.contains("backup") || action.contains("restore")
                || action.contains("setting") || action.contains("export")
                || action.contains("save") || action.contains("deleted")
                || action.contains("module") || action.contains("section")
                || action.contains("assessment");
    }

    // Returns the colour of an action: kept and approved are green,
    // taken back, undone and cancelled are amber, failures are red.
    private Tone toneOf(String action) {
        String words = action.toLowerCase();
        boolean failed = words.contains("fail") || words.contains("refused");
        boolean removed = words.contains("deleted")
                || words.contains("removed");
        boolean takenBack = words.contains("undone")
                || words.contains("cancel") || words.contains("taken back")
                || words.contains("replaced");
        boolean kept = words.contains("approved") || words.contains("kept")
                || words.contains("reviewed") || words.contains("made")
                || words.contains("restored");
        if (failed || removed) {
            return Tone.BAD;
        } else if (takenBack) {
            return Tone.WARN;
        } else if (kept) {
            return Tone.GOOD;
        }
        return Tone.NEUTRAL;
    }

    // Adds one entry to a day's table: the time, the action as a pill,
    // the student it is about, and the rest of the detail.
    private void addActivityRow(PlainTable table, AuditEntry entry) {
        String detailText = entry.getShownDetail();
        String student = "";
        int colon = detailText.indexOf(NAME_SEPARATOR);
        boolean startsWithName = colon > 0 && colon < STUDENT_NAME_LIMIT
                && !detailText.substring(0, colon).contains(SPACE);
        if (startsWithName) {
            int detailStart = colon + NAME_SEPARATOR.length();
            student = detailText.substring(0, colon);
            detailText = detailText.substring(detailStart);
        }
        Label detail = new Label(Parts.shortenPaths(detailText));
        detail.setMinWidth(0);
        detail.setTooltip(new Tooltip(entry.getDetail()));
        Label time = Parts.muted(entry.getTimestamp().format(TIME));
        String whoText = student;
        if (student.isEmpty()) {
            whoText = NO_VALUE;
        }
        Label who = Parts.strong(whoText);
        who.setMinWidth(0);
        Node undoCell = new Label("");
        Runnable undo = findUndo(entry, student, detailText);
        if (undo != null) {
            Button button = Parts.small("Undo", () -> undoActivity(undo));
            Parts.tip(button, "Take this back. No other change is undone.");
            undoCell = button;
        }
        table.addRow(new Node[] {time, Parts.pill(entry.getAction(),
            toneOf(entry.getAction())), who, detail, undoCell});
    }

    // ----------------------------------------------------------- shared

    // Says whether results are not saved, so nothing is kept.
    private boolean savingIsOff() {
        return FALSE.equalsIgnoreCase(getBackend().getSetting(
                ConfigKeys.DATABASE_ENABLED));
    }

    // Builds an empty state: a bold line, a muted line and a link.
    private VBox empty(String title, String detail, String linkText,
            Runnable action) {
        VBox box = new VBox(Parts.GAP_TINY, Parts.strong(title),
                Parts.muted(detail));
        box.setAlignment(Pos.CENTER);
        if (linkText != null) {
            Button link = Parts.secondary(linkText, action);
            VBox.setMargin(link, new Insets(Parts.GAP_SMALL, 0, 0, 0));
            box.getChildren().add(link);
        }
        return box;
    }
}
