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
import java.util.Comparator;
import java.util.HashMap;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.function.Supplier;

import javafx.geometry.Insets;
import javafx.geometry.Pos;
import javafx.scene.Node;
import javafx.scene.control.Button;
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
import gradezzy.model.Section;
import gradezzy.model.Submission;
import gradezzy.model.SubmissionStatus;
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

    // The width of the activity search field
    private static final double SEARCH_WIDTH = 260.0;

    // The size of the search icon
    private static final double SEARCH_ICON_SIZE = 14.0;

    // The most activity rows drawn at once
    private static final int MAX_ACTIVITY_ROWS = 500;

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
     * <p>Tier: SHOULD.
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
     *
     * <p>Tier: SHOULD.
     */
    public void showRuns() {
        runsButton.setSelected(true);
    }

    /**
     * Shows the backups.
     *
     * <p>Tier: SHOULD.
     */
    public void showBackups() {
        backupsButton.setSelected(true);
    }

    /**
     * Shows the activity log.
     *
     * <p>Tier: SHOULD.
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

    /**
     * Follows a click on Runs, Backups or Activity. Clicking the button
     * that is already on would turn every button off, so it stays on.
     *
     * @param before the button that was on
     * @param after the button that is on now, or null for none
     */
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
        runsSearch.setPrefWidth(SEARCH_WIDTH + SEARCH_WIDTH / 3);
        runsSearch.setMaxWidth(SEARCH_WIDTH + SEARCH_WIDTH / 3);
        runsSearch.textProperty().addListener((observable, before,
                after) -> refresh());
        runsCount.getStyleClass().add("small-text");
        HBox tools = Parts.row(Parts.GAP, new Node[] {runsSearch,
            Parts.grow(), runsCount});
        runsView.getChildren().addAll(tools, runsTable, restoreNote(
                "Current marks the run each section shows on Home, Review "
                + "and Statistics. Restore makes an earlier run current; "
                + "the other is kept here, so you can switch back."));
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
        for (int index = 0; index < KINDS.length; index++) {
            ToggleButton kind = new ToggleButton(KINDS[index]);
            kind.setToggleGroup(kindGroup);
            kind.setUserData(KINDS[index]);
            if (index == 0) {
                kind.getStyleClass().add("left-pill");
                kind.setSelected(true);
            } else if (index == KINDS.length - 1) {
                kind.getStyleClass().add("right-pill");
            } else {
                kind.getStyleClass().add("center-pill");
            }
            kinds.getChildren().add(kind);
        }
        kinds.setMinWidth(HBox.USE_PREF_SIZE);
        kindGroup.selectedToggleProperty().addListener((observable,
                before, after) -> kindChosen(before, after));
        activityCount.getStyleClass().add("small-text");
        HBox tools = Parts.row(Parts.GAP, new Node[] {activitySearch,
            kinds, Parts.grow(), activityCount});
        activity.getChildren().addAll(tools, activityDays);
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

    // Lists every run, newest first.
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
        Set<String> newerSeen = new HashSet<>();
        String text = runsSearch.getText().trim().toLowerCase();
        int listed = 0;
        for (RunRecord record : records) {
            String label = labelOf(record.getRun());
            boolean hasNewer = newerSeen.contains(label);
            newerSeen.add(label);
            if (runMatches(record.getRun(), text)) {
                addRunRow(record, isShown(record, shown), hasNewer);
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
            all += " " + run.getAssessment().getName();
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

    // Adds one run to the table.
    private void addRunRow(RunRecord record, boolean shown,
            boolean hasNewer) {
        GradingRun run = record.getRun();
        Section section = run.getSection();
        String module = "-";
        String sectionCode = "-";
        if (section != null) {
            module = section.getModuleCode();
            sectionCode = section.getSectionCode();
        }
        Label students = new Label(String.valueOf(
                run.getSubmissions().size()));
        String assessmentName = "-";
        if (run.getAssessment() != null) {
            assessmentName = run.getAssessment().getName();
        }
        Label assessment = new Label(assessmentName);
        assessment.setMinWidth(0);
        Parts.tip(assessment, "The assessment this run was graded with. "
                + "Opening or resuming the run switches to it.");
        Label name = Parts.strong(module + " · " + sectionCode);
        name.setMinWidth(Label.USE_PREF_SIZE);
        HBox title = Parts.row(Parts.GAP_SMALL, new Node[] {name,
            assessment});
        if (shown) {
            Label current = new Label("Current");
            current.getStyleClass().addAll("small-text", "current-badge");
            current.setMinWidth(Label.USE_PREF_SIZE);
            Parts.tip(current, "The run Home, Review and Statistics show "
                    + "for this section.");
            title.getChildren().add(current);
        }
        Label when = Parts.muted("Graded " + run.getStartedAt().format(WHEN));
        when.getStyleClass().add("small-text");
        VBox runCell = new VBox(2, title, when);
        runCell.setAlignment(Pos.CENTER_LEFT);
        runCell.setMinWidth(0);
        runsTable.addRow(new Node[] {runCell, students, average(run),
            status(run, shown, hasNewer), actions(record, shown)});
    }

    // Shows the average of a run as a small bar and "x / max".
    private Node average(GradingRun run) {
        double max = run.getAssessment().getMaxTotal();
        MarkStats stats = MarkStats.of(run.getSubmissions(), max,
                MarkStats.parsePassPercent(getBackend().getSetting(
                ConfigKeys.PASS_MARK_PERCENT)));
        if (stats.isEmpty() || max <= 0) {
            return Parts.muted("-");
        }
        StackPane bar = Parts.track(stats.getMean() / max, null);
        bar.setMinWidth(AVERAGE_BAR_WIDTH / 2);
        bar.setPrefWidth(AVERAGE_BAR_WIDTH);
        bar.setMaxWidth(AVERAGE_BAR_WIDTH);
        Label value = new Label(String.format("%.1f", stats.getMean())
                + " / " + Parts.mark(max));
        value.setMinWidth(Label.USE_PREF_SIZE);
        return Parts.row(Parts.GAP_SMALL, new Node[] {bar, value});
    }

    // Returns the pill that says how far a run got.
    private Node status(GradingRun run, boolean shown, boolean hasNewer) {
        Label pill;
        String tip;
        int waiting = countWaiting(run);
        if (!shown && hasNewer) {
            pill = Parts.pill("Earlier run", Tone.NEUTRAL);
            tip = "A newer run of this section is the one shown on Home "
                    + "and Review. Restore shows this one again instead.";
        } else if (!shown) {
            pill = Parts.pill("Not shown", Tone.NEUTRAL);
            tip = "An older run of this section was restored and is shown "
                    + "instead.";
        } else if (waiting > 0) {
            int total = run.getSubmissions().size();
            pill = Parts.pill("Stopped " + (total - waiting) + "/" + total,
                    Tone.WARN);
            tip = "Grading was cancelled, or GradeZZY closed, before every "
                    + "student was graded. Resume grades the rest; marks "
                    + "already given are kept.";
        } else {
            pill = Parts.pill("Finished", Tone.GOOD);
            tip = "Every student in this run was graded.";
        }
        Parts.tip(pill, tip);
        return pill;
    }

    // Returns the buttons that fit a run.
    private HBox actions(RunRecord record, boolean shown) {
        GradingRun run = record.getRun();
        HBox buttons = Parts.row(Parts.GAP_SMALL);
        buttons.setAlignment(Pos.CENTER_RIGHT);
        if (!shown) {
            if (record.isRestorable()
                    && getBackend().supports(Capability.RESTORE_PREVIEW)) {
                buttons.getChildren().add(restoreButton(record));
            }
            return buttons;
        }

        if (countWaiting(run) > 0
                && getBackend().supports(Capability.RESUME_RUN)) {
            Button resume = Parts.small("Resume",
                    () -> getWindow().resumeRun(run));
            Parts.tip(resume, "Grade the students not graded yet. Marks "
                    + "already given are kept.");
            buttons.getChildren().add(resume);
        } else if (getBackend().supports(Capability.STATISTICS)
                && run.getSection() != null) {
            Button statistics = Parts.small("Statistics",
                    () -> openStatistics(run));
            Parts.tip(statistics, "Charts and figures for this section.");
            buttons.getChildren().add(statistics);
        }
        Button open = Parts.small("Open", () -> getWindow().openRun(run));
        Parts.tip(open, "Show this run's students and marks on Review, "
                + "where you can also export them.");
        buttons.getChildren().add(open);
        return buttons;
    }

    /**
     * Opens the Statistics tab on the section of a run.
     *
     * @param run the run, which has a section
     */
    private void openStatistics(GradingRun run) {
        getWindow().openStatistics(run.getSection().getModuleCode(),
                run.getSection().getSectionCode());
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
        button.setDisable(getState().isGrading());
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

    /**
     * Shows an earlier run in place of the one shown now, once the
     * instructor has confirmed.
     *
     * @param record the earlier run
     */
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
        if (result.isDone() && before != null && before.getId() != null) {
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
        if (!getState().isGrading()) {
            return false;
        }
        getWindow().notify("Wait for the current batch to finish first.");
        return true;
    }

    // Counts the students of a run still waiting to be graded.
    private int countWaiting(GradingRun run) {
        int waiting = 0;
        for (Submission submission : run.getSubmissions()) {
            if (submission.getStatus() == SubmissionStatus.CANCELLED
                    || submission.getStatus() == SubmissionStatus.PENDING) {
                waiting++;
            }
        }
        return waiting;
    }

    // Returns the section of a run, such as "CS102 G1".
    private String labelOf(GradingRun run) {
        if (run.getSection() == null) {
            return "an unknown section";
        }
        return run.getSection().getLabel();
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
        if (savingIsOff() || !(canBackUp || canRestoreFile
                || canPreview)) {
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
        String folder = getBackend().getSetting(ConfigKeys.BACKUP_DIR);
        String keep = getBackend().getSetting(ConfigKeys.BACKUP_KEEP_COUNT);
        String where = "A backup is a copy of every saved run.";
        if (!getBackend().isDemo()) {
            where = where + " Backups are saved in " + folder + ", and the "
                    + "newest " + keep + " are kept.";
        }
        Label text = Parts.muted(where + gradingNote());
        text.setWrapText(true);
        VBox words = new VBox(Parts.GAP_SMALL / 2,
                Parts.strong("Back up the saved results"), text);
        Node grow = Parts.grow();
        Button backUpButton = Parts.secondary("Back up now",
                () -> backUp());
        backUpButton.setDisable(getState().isGrading());
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
        VBox words = new VBox(Parts.GAP_SMALL / 2,
                Parts.strong("Restore from a backup file"), text);
        Node grow = Parts.grow();
        Button chooseButton = Parts.secondary("Choose a backup",
                () -> chooseBackup());
        chooseButton.setDisable(getState().isGrading());
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
        if (getState().isGrading()) {
            return " Available when the batch being graded finishes.";
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
        String module = "-";
        String sectionCode = "-";
        if (run.getSection() != null) {
            module = run.getSection().getModuleCode();
            sectionCode = run.getSection().getSectionCode();
        }
        Node shownCell = Parts.muted("-");
        HBox buttons = Parts.row(Parts.GAP_SMALL);
        buttons.setAlignment(Pos.CENTER_RIGHT);
        if (shown) {
            shownCell = Parts.pill("Current", Tone.GOOD);
        } else {
            buttons.getChildren().add(restoreButton(record));
        }
        table.addRow(new Node[] {Parts.strong(module),
            Parts.strong(sectionCode),
            Parts.muted(run.getStartedAt().format(WHEN)),
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
        Path backupFile = file.toPath();
        getWindow().confirm("Restore " + backupFile.getFileName() + "?",
                "The saved results are replaced by the ones in this file. "
                + "The results saved now are backed up first, so you can "
                + "undo it. Original zip files and exports are not "
                + "changed.",
                "Restore", () -> restoreFile(backupFile));
    }

    /**
     * Puts back the saved results from a backup file, once the
     * instructor has confirmed.
     *
     * @param backupFile the backup file
     */
    private void restoreFile(Path backupFile) {
        if (blockedByGrading()) {
            return;
        }
        // A copy of the results saved now, so Undo can put them back
        Path safety = null;
        if (getBackend().supports(Capability.BACKUP)) {
            ActionResult copy = getBackend().backupNow();
            if (copy.isDone()) {
                safety = copy.getPath();
            }
        }
        ActionResult result = getBackend().restoreBackup(backupFile);
        Path undoFile = safety;
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
        if (!getBackend().supports(Capability.AUDIT_LOG)
                || FALSE.equalsIgnoreCase(getBackend().getSetting(
                ConfigKeys.AUDIT_ENABLED))) {
            activitySearch.setDisable(true);
            activityTable.showMessage(empty("The activity log is off.",
                    "Switch on Keep an activity log in Settings.",
                    "Open Settings",
                    () -> getWindow().showPage(Page.SETTINGS)));
            return;
        }
        activitySearch.setDisable(false);

        String text = activitySearch.getText().trim().toLowerCase();
        List<AuditEntry> entries = getBackend().listAuditEntries();
        if (entries.isEmpty()) {
            activityTable.showMessage(empty("Nothing recorded yet.",
                    "Approvals, undos and exports are listed here with "
                    + "the time.", null, null));
            return;
        }

        List<AuditEntry> sorted = new ArrayList<>(entries);
        sorted.sort(Comparator.comparing(AuditEntry::getTimestamp)
                .reversed());
        String kind = String.valueOf(kindGroup.getSelectedToggle()
                .getUserData());
        int shown = 0;
        LocalDate day = null;
        PlainTable dayTable = null;
        activityDays.getChildren().clear();
        for (AuditEntry entry : sorted) {
            if (shown < MAX_ACTIVITY_ROWS && matches(entry, text)
                    && isKind(entry, kind)) {
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
        activityCount.setText(Parts.plural(shown, "entry", "entries"));
        if (shown == 0) {
            activityDays.getChildren().setAll(activityTable);
            activityTable.showMessage(empty("No activity matches \""
                    + activitySearch.getText().trim() + "\".", "Try "
                    + "another word.", null, null));
        }
    }

    // Says whether an entry holds the searched text.
    private boolean matches(AuditEntry entry, String text) {
        if (text.isEmpty()) {
            return true;
        }
        String all = (entry.getAction() + " " + entry.getDetail())
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
        String reference = entry.getReference();
        if (getState().isGrading()) {
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
        boolean fix = "Fix approved".equals(action);
        boolean undone = "Fix undone".equals(action);
        boolean reapplied = "Fix reapplied".equals(action);
        boolean decision = ReviewDecision.AUDIT_CHECKED.equals(action)
                || ReviewDecision.AUDIT_LOOKS_FINE.equals(action);
        if (!fix && !undone && !reapplied && !decision) {
            return null;
        }
        // The decision the log line made; Undo is offered only while it
        // is still the current one, so an older line cannot take back a
        // newer decision
        ReviewDecision logged = ReviewDecision.REVIEWED;
        if (ReviewDecision.AUDIT_LOOKS_FINE.equals(action)) {
            logged = ReviewDecision.REJECTED;
        }
        for (GradingRun run : runsToSearch()) {
            boolean runNamed = reference.isEmpty()
                    || reference.equals(run.getId());
            for (Submission submission : run.getSubmissions()) {
                if (!submission.getUsername().equals(student)) {
                    continue;
                }
                if (decision && logged == ReviewDecision.REVIEWED
                        && DashboardBackend.ZERO_CHECK_WORDS.equals(message)
                        && runNamed
                        && getBackend().isZeroChecked(submission)) {
                    return () -> getWindow().notify(getBackend()
                            .setZeroChecked(submission, false).getMessage());
                }
                for (Anomaly anomaly : submission.getAnomalies()) {
                    if (!isAbout(message, reference, anomaly, !decision)) {
                        continue;
                    }
                    if (fix && anomaly.getFixStatus() == FixStatus.APPROVED
                            && getBackend().supports(
                            Capability.REPAIR_DECISION_UNDO)) {
                        return () -> takeBackFix(run, anomaly);
                    }
                    if (undone && anomaly.getFixStatus() == FixStatus.UNDONE
                            && UndoFixJob.canApplyAgain(getBackend())) {
                        return () -> runFixJob(run, anomaly, true);
                    }
                    if (reapplied
                            && anomaly.getFixStatus() == FixStatus.APPLIED
                            && getBackend().supports(
                            Capability.REPAIR_DECISIONS)) {
                        return () -> runFixJob(run, anomaly, false);
                    }
                    if (decision && getBackend().getDecision(anomaly)
                            == logged) {
                        return () -> takeBackDecision(anomaly);
                    }
                }
            }
        }
        return null;
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
                    || message.startsWith(words + " ");
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
        if (!job.startOnce(getState(), run,
                result -> fixJobFinished(result))) {
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
        if (words.contains("fail") || words.contains("refused")) {
            return Tone.BAD;
        } else if (words.contains("deleted") || words.contains("removed")) {
            return Tone.BAD;
        } else if (words.contains("undone") || words.contains("cancel")
                || words.contains("taken back") || words.contains("replaced")) {
            return Tone.WARN;
        } else if (words.contains("approved") || words.contains("kept")
                || words.contains("reviewed") || words.contains("made")
                || words.contains("restored")) {
            return Tone.GOOD;
        }
        return Tone.NEUTRAL;
    }

    // Adds one entry to a day's table: the time, the action as a pill,
    // the student it is about, and the rest of the detail.
    private void addActivityRow(PlainTable table, AuditEntry entry) {
        String detailText = entry.getShownDetail();
        String student = "";
        int colon = detailText.indexOf(": ");
        if (colon > 0 && colon < STUDENT_NAME_LIMIT
                && !detailText.substring(0, colon).contains(" ")) {
            student = detailText.substring(0, colon);
            detailText = detailText.substring(colon + 2);
        }
        Label detail = new Label(Parts.shortenPaths(detailText));
        detail.setMinWidth(0);
        detail.setTooltip(new Tooltip(entry.getDetail()));
        Label time = Parts.muted(entry.getTimestamp().format(TIME));
        String whoText = student;
        if (student.isEmpty()) {
            whoText = "-";
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
        VBox box = new VBox(Parts.GAP_SMALL / 2, Parts.strong(title),
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
