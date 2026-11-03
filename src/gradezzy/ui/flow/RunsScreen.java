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
import java.time.format.DateTimeFormatter;
import java.util.HashMap;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;

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
import gradezzy.model.AuditEntry;
import gradezzy.model.GradingRun;
import gradezzy.model.Section;
import gradezzy.model.Submission;
import gradezzy.model.SubmissionStatus;
import gradezzy.ui.backend.ActionResult;
import gradezzy.ui.backend.Capability;
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
    private static final double ROW_HEIGHT = 52.0;

    // The height of an activity row
    private static final double ACTIVITY_ROW_HEIGHT = 44.0;

    // Column widths of the runs table
    private static final double MODULE_WIDTH = 84.0;
    private static final double SECTION_WIDTH = 76.0;
    private static final double STUDENTS_WIDTH = 84.0;
    private static final double AVERAGE_WIDTH = 190.0;
    private static final double STATUS_WIDTH = 200.0;
    private static final double ACTIONS_WIDTH = 180.0;

    // Column widths of the snapshot and activity tables
    private static final double SNAPSHOT_WIDTH = 260.0;
    private static final double SHOWN_WIDTH = 120.0;
    private static final double WHEN_WIDTH = 160.0;
    private static final double ACTION_WIDTH = 200.0;

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

    // Holds the view picked with the switch
    private final StackPane body = new StackPane();

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

        VBox header = Parts.header("Runs", "Every grading run, the backups "
                + "of saved results, and what was done.");
        HBox headerRow = Parts.row(Parts.GAP,
                new Node[] {header, Parts.grow(), createSwitch()});
        page.getChildren().addAll(headerRow, body);
        view = Parts.widePage(new Node[] {page});
        body.setAlignment(Pos.TOP_LEFT);
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
        runsTable.addColumn("Module", MODULE_WIDTH, Pos.CENTER_LEFT);
        runsTable.addColumn("Section", SECTION_WIDTH, Pos.CENTER_LEFT);
        runsTable.addColumn("Graded", PlainTable.GROW, Pos.CENTER_LEFT);
        runsTable.addColumn("Students", STUDENTS_WIDTH, Pos.CENTER_RIGHT);
        runsTable.addColumn("Average", AVERAGE_WIDTH, Pos.CENTER_LEFT);
        runsTable.addColumn("Status", STATUS_WIDTH, Pos.CENTER_LEFT);
        runsTable.addColumn("", ACTIONS_WIDTH, Pos.CENTER_RIGHT);
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
        activity.getChildren().addAll(activitySearch, activityTable);
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
            body.getChildren().setAll(runsTable);
        }
    }

    // ------------------------------------------------------------- runs

    // Lists every run, newest first.
    private void refreshRuns() {
        runsTable.clearRows();
        if (!getBackend().supports(Capability.HISTORY)) {
            runsTable.showMessage(empty("Earlier runs are not available "
                    + "here.", "Grade a batch on Marking to see its "
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
        for (RunRecord record : records) {
            String label = labelOf(record.getRun());
            boolean hasNewer = newerSeen.contains(label);
            newerSeen.add(label);
            addRunRow(record, isShown(record, shown), hasNewer);
        }
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
                + "is listed here.", "Go to Marking",
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
        runsTable.addRow(new Node[] {Parts.strong(module),
            Parts.strong(sectionCode),
            Parts.muted(run.getStartedAt().format(WHEN)), students,
            average(run), status(run, shown, hasNewer),
            actions(record, shown)});
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
    private Label status(GradingRun run, boolean shown, boolean hasNewer) {
        if (!shown && hasNewer) {
            return Parts.pill("Replaced by a later run", Tone.NEUTRAL);
        }
        if (!shown) {
            return Parts.pill("Replaced by a restored run", Tone.NEUTRAL);
        }
        int waiting = countWaiting(run);
        if (waiting > 0) {
            int total = run.getSubmissions().size();
            return Parts.pill("Stopped at " + (total - waiting) + " of "
                    + total, Tone.WARN);
        }
        return Parts.pill("Finished", Tone.GOOD);
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
            buttons.getChildren().add(Parts.small("Resume",
                    () -> getWindow().resumeRun(run)));
        } else if (getBackend().supports(Capability.STATISTICS)
                && run.getSection() != null) {
            buttons.getChildren().add(Parts.small("Statistics",
                    () -> openStatistics(run)));
        }
        buttons.getChildren().add(Parts.small("Open",
                () -> getWindow().openRun(run)));
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
        ActionResult result = getBackend().restore(record);
        afterRestore(result);
    }

    // Says what a restore did. After a restore the run on Review may no
    // longer be saved, so Marking starts again on Set up.
    private void afterRestore(ActionResult result) {
        getWindow().notify(result.getMessage());
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
                    snapshotTable());
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
        Label text = Parts.muted("Puts back the saved results from a "
                + "backup file. The results saved now are not backed up "
                + "first, so back up before you restore." + gradingNote());
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
            shownCell = Parts.pill("Shown now", Tone.NEUTRAL);
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
        }
        File file = chooser.showOpenDialog(getWindow().getWindow());
        if (file == null) {
            return;
        }
        Path backupFile = file.toPath();
        getWindow().confirm("Restore " + backupFile.getFileName() + "?",
                "The saved results are replaced by the ones in this file. "
                + "The results saved now are not backed up first. "
                + "Original zip files and exports are not changed.",
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
        ActionResult result = getBackend().restoreBackup(backupFile);
        afterRestore(result);
    }

    // --------------------------------------------------------- activity

    // Lists the activity, newest first, matching the search.
    private void refreshActivity() {
        activityTable.clearRows();
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

        int shown = 0;
        for (AuditEntry entry : entries) {
            if (shown < MAX_ACTIVITY_ROWS && matches(entry, text)) {
                addActivityRow(entry);
                shown++;
            }
        }
        if (shown == 0) {
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

    // Adds one entry to the activity table.
    private void addActivityRow(AuditEntry entry) {
        Label detail = new Label(entry.getDetail());
        detail.setTooltip(new Tooltip(entry.getDetail()));
        Label when = Parts.muted(entry.getTimestamp().format(WHEN));
        activityTable.addRow(new Node[] {when,
            Parts.strong(entry.getAction()), detail});
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
