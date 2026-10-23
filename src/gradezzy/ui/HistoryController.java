/*
 * File: HistoryController.java
 *
 * Purpose: Controller of the history screen.
 *
 * Where it fits: Lists earlier runs and their backups, with Back Up Now,
 * Restore and Open Run. Until the restore preview is connected, the live
 * app asks the instructor for a backup file to restore.
 *
 * Note on untaught Java: backups and restores read and write files, so
 * they run in a javafx.concurrent.Task on a background thread.
 */
package gradezzy.ui;

import java.io.File;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.List;

import javafx.beans.property.ReadOnlyObjectWrapper;
import javafx.beans.property.ReadOnlyStringWrapper;
import javafx.collections.FXCollections;
import javafx.concurrent.Task;
import javafx.fxml.FXML;
import javafx.geometry.Pos;
import javafx.scene.control.Button;
import javafx.scene.control.Label;
import javafx.scene.control.SelectionMode;
import javafx.scene.control.TableCell;
import javafx.scene.control.TableColumn;
import javafx.scene.control.TableView;
import javafx.scene.layout.GridPane;
import javafx.scene.layout.HBox;
import javafx.scene.layout.Region;
import javafx.scene.layout.VBox;
import javafx.stage.FileChooser;

import gradezzy.config.ConfigKeys;
import gradezzy.model.GradingRun;
import gradezzy.model.Section;
import gradezzy.ui.backend.ActionResult;
import gradezzy.ui.backend.Capability;
import gradezzy.ui.backend.CapabilityStatus;
import gradezzy.ui.backend.RunRecord;
import gradezzy.ui.component.EmptyState;
import gradezzy.ui.component.Icon;
import gradezzy.ui.component.Labels;
import gradezzy.ui.component.Responsive;
import gradezzy.ui.component.StatusBadge;
import gradezzy.ui.component.Tone;

/**
 * Controller of the history screen.
 */
public class HistoryController extends BaseController {

    // The share of the width given to the table
    private static final double TABLE_PERCENT = 64.0;

    // The width below which the run details move under the table
    private static final double STACK_WIDTH = 760.0;

    // The narrowest the run column may get
    private static final double RUN_WIDTH = 130.0;

    // The narrowest the section column may get
    private static final double SECTION_WIDTH = 84.0;

    // The narrowest the state column may get
    private static final double STATE_WIDTH = 120.0;

    // The narrowest the shown column may get
    private static final double SHOWN_WIDTH = 104.0;

    // The narrowest the backup column may get
    private static final double BACKUP_WIDTH = 150.0;

    // The gap between the two lines of the run cell
    private static final double LINE_GAP = 0.0;

    // The words of the badge on the run shown now
    private static final String SHOWN_NOW = "Shown now";

    // The words of the Restore button when it asks for a backup file
    private static final String RESTORE_FILE_LABEL = "Restore From File";

    @FXML
    private Label statusLabel;

    @FXML
    private Button restoreButton;

    @FXML
    private Button openButton;

    @FXML
    private Button backupButton;

    @FXML
    private GridPane columns;

    @FXML
    private VBox tablePanel;

    @FXML
    private TableView<RunRecord> table;

    @FXML
    private VBox detailPanel;

    @FXML
    private Label runTitle;

    @FXML
    private HBox runBadgeHolder;

    @FXML
    private GridPane detailGrid;

    @FXML
    private Label startedLabel;

    @FXML
    private Label sectionLabel;

    @FXML
    private Label stateLabel;

    @FXML
    private Label backupLabel;

    @FXML
    private VBox consequenceBox;

    @FXML
    private Label restoreNote;

    // The runs listed
    private final List<RunRecord> records = new ArrayList<>();

    // The selected run, as its id and section, kept across reloads
    private String selectedKey = "";

    // Whether the table selection is being restored by code
    private boolean restoring = false;

    // Whether Restore asks for a backup file instead of using the list
    private boolean restoreFromFile = false;

    // Whether a backup or restore runs in the background
    private boolean busy = false;

    // What the status line says while a backup or restore runs
    private String busyMessage = "";

    /**
     * Creates a new history controller.
     */
    public HistoryController() {
        // nothing to set up
    }

    /**
     * Called by JavaFX after the FXML file is loaded.
     */
    @FXML
    public void initialize() {
        Responsive.twoColumns(columns, tablePanel, detailPanel,
                TABLE_PERCENT, STACK_WIDTH);
        backupButton.setGraphic(Icon.HISTORY.create());
        buildColumns();
        table.getSelectionModel().setSelectionMode(SelectionMode.SINGLE);
        table.setColumnResizePolicy(
                TableView.CONSTRAINED_RESIZE_POLICY_FLEX_LAST_COLUMN);
        table.getSelectionModel().selectedItemProperty().addListener(
                (observable, oldValue, newValue) -> selectionChanged(
                newValue));
    }

    /**
     * Chooses whether Restore uses the list or asks for a backup file.
     */
    @Override
    public void onAttach() {
        restoreFromFile = !getBackend().isDemo()
                && !getBackend().supports(Capability.RESTORE_PREVIEW);
        if (restoreFromFile) {
            restoreButton.setText(RESTORE_FILE_LABEL);
        }
    }

    /**
     * Reloads the runs each time the screen is shown.
     */
    @Override
    public void onShow() {
        refresh();
    }

    /**
     * Opens the results of the selected run. A run not shown now is
     * restored first, after asking.
     */
    @FXML
    public void handleOpenRun() {
        RunRecord record = table.getSelectionModel().getSelectedItem();
        if (record == null) {
            showError("Select a run to open first.");
            return;
        }
        if (record.isCurrent()) {
            openResults(record);
            return;
        }

        String id = record.getRun().getId();
        if (!record.isRestorable()) {
            showError("Run " + id + " is not the run shown now and has no "
                    + "backup, so its results cannot be opened.");
            return;
        }
        if (!getBackend().supports(Capability.RESTORE_PREVIEW)) {
            getShell().showNotConnected(Capability.RESTORE_PREVIEW);
            return;
        }
        getShell().confirm("Restore run " + id + " first?", "Only the run "
                + "shown now can be opened on the Results screen, so run "
                + id + " must be restored first.\n"
                + consequencesText(record), "Restore and Open",
                "Keep Current Run", false, () -> restore(record, true));
    }

    /**
     * Backs up the saved results now, in the background.
     */
    @FXML
    public void handleBackup() {
        if (busy) {
            return;
        }
        Task<ActionResult> task = new Task<>() {

            /**
             * Asks the backend for a backup, on a background thread.
             *
             * @return what happened
             */
            @Override
            protected ActionResult call() {
                return getBackend().backupNow();
            }
        };
        task.setOnSucceeded(event -> finishBackup(task.getValue()));
        task.setOnFailed(event -> stopped("Back Up Now",
                task.getException()));
        setBusy(true, "Backing up the saved results. You can keep "
                + "working; this line changes when the backup is done.");
        runInBackground(task, "gradezzy-backup");
    }

    /**
     * Restores the selected run, or a chosen backup file, after asking.
     */
    @FXML
    public void handleRestore() {
        if (busy) {
            return;
        }
        if (restoreFromFile) {
            restoreBackupFile();
            return;
        }
        RunRecord record = table.getSelectionModel().getSelectedItem();
        if (record == null) {
            showError("Select a run to restore first.");
            return;
        }
        String id = record.getRun().getId();
        if (record.isCurrent()) {
            showError("Run " + id + " is already the run shown for "
                    + sectionOf(record) + ".");
            return;
        }
        if (!record.isRestorable()) {
            showError("Run " + id + " has no backup, so it cannot be "
                    + "restored.");
            return;
        }
        if (!getBackend().supports(Capability.RESTORE_PREVIEW)) {
            getShell().showNotConnected(Capability.RESTORE_PREVIEW);
            return;
        }
        getShell().confirm("Restore run " + id + "?",
                consequencesText(record), "Restore Run " + id,
                "Keep Current Run", false, () -> restore(record, false));
    }

    // Restores a run in the background, then opens its results if asked.
    private void restore(RunRecord record, boolean open) {
        Task<ActionResult> task = new Task<>() {

            /**
             * Asks the backend to restore the run, on a background thread.
             *
             * @return what happened
             */
            @Override
            protected ActionResult call() {
                return getBackend().restore(record);
            }
        };
        task.setOnSucceeded(event -> finishRestore(task.getValue(),
                record, open));
        task.setOnFailed(event -> stopped("Restore", task.getException()));
        setBusy(true, "Restoring run " + record.getRun().getId() + ".");
        runInBackground(task, "gradezzy-restore");
    }

    // Shows how a restore of a run ended, then its results when asked to.
    private void finishRestore(ActionResult result, RunRecord record,
            boolean open) {
        setBusy(false, "");
        if (showResult(result, "Run restored")) {
            getShell().refreshContext();
            if (open) {
                openResults(record);
                return;
            }
        }
        refresh();
    }

    // Asks for a backup file, then restores it once confirmed. Live only.
    private void restoreBackupFile() {
        if (!getBackend().supports(Capability.RESTORE)) {
            getShell().showNotConnected(Capability.RESTORE);
            return;
        }
        FileChooser chooser = new FileChooser();
        chooser.setTitle("Choose a backup made by Back Up Now");
        File file = chooser.showOpenDialog(getShell().getWindow());
        if (file == null) {
            return;
        }
        Path backupFile = file.toPath();
        getShell().confirm("Restore " + backupFile.getFileName() + "?",
                String.join("\n", fileConsequences(backupFile)),
                "Restore Backup", "Keep Current Results", true,
                () -> restoreFile(backupFile));
    }

    // Restores a backup file in the background.
    private void restoreFile(Path backupFile) {
        Task<ActionResult> task = new Task<>() {

            /**
             * Asks the backend to restore the file, on a background thread.
             *
             * @return what happened
             */
            @Override
            protected ActionResult call() {
                return getBackend().restoreBackup(backupFile);
            }
        };
        task.setOnSucceeded(event -> finishFileRestore(task.getValue()));
        task.setOnFailed(event -> stopped("Restore", task.getException()));
        setBusy(true, "Restoring the saved results from "
                + backupFile.getFileName() + ".");
        runInBackground(task, "gradezzy-restore");
    }

    // Shows how a restore of a backup file ended.
    private void finishFileRestore(ActionResult result) {
        setBusy(false, "");
        if (showResult(result, "Backup restored")) {
            getShell().refreshContext();
        }
        refresh();
    }

    // Shows how a backup ended.
    private void finishBackup(ActionResult result) {
        setBusy(false, "");
        showResult(result, "Backup saved");
        refresh();
    }

    // Reports a background call that ended with an exception.
    private void stopped(String action, Throwable problem) {
        setBusy(false, "");
        String detail = "no details were given";
        if (problem != null && problem.getMessage() != null) {
            detail = problem.getMessage();
        }
        showError(action + " stopped unexpectedly (" + detail + "). Check "
                + "the runs listed here before trying again.");
        refresh();
    }

    // Marks a backup or restore as running or ended, and updates buttons.
    private void setBusy(boolean running, String message) {
        busy = running;
        busyMessage = message;
        refreshButtons(table.getSelectionModel().getSelectedItem());
        refreshStatus();
    }

    // Starts a task on a daemon thread, so it never keeps the app open.
    private static void runInBackground(Task<?> task, String name) {
        Thread worker = new Thread(task, name);
        worker.setDaemon(true);
        worker.start();
    }

    // Shows the Results screen for the section of a run.
    private void openResults(RunRecord record) {
        Section section = record.getRun().getSection();
        if (section != null) {
            getState().moduleCodeProperty().set(section.getModuleCode());
            getState().sectionCodeProperty().set(section.getSectionCode());
        }
        getShell().navigate(Screen.RESULTS);
    }

    // Reloads the runs and restores the selection.
    private void refresh() {
        records.clear();
        records.addAll(getBackend().listRuns());
        table.setPlaceholder(emptyState());
        restoring = true;
        table.setItems(FXCollections.observableArrayList(records));
        RunRecord wanted = findSelected();
        if (wanted != null) {
            table.getSelectionModel().select(wanted);
        }
        restoring = false;
        showDetails(wanted);
        refreshStatus();
    }

    // Returns the run chosen before, else the shown run, else the first.
    private RunRecord findSelected() {
        String section = getState().getSection().getLabel();
        RunRecord shown = null;
        for (RunRecord record : records) {
            if (keyOf(record).equals(selectedKey)) {
                return record;
            }
            if (shown == null && record.isCurrent()
                    && sectionOf(record).equals(section)) {
                shown = record;
            }
        }
        if (shown != null) {
            return shown;
        } else if (!records.isEmpty()) {
            return records.get(0);
        }
        return null;
    }

    // Remembers the run chosen by the instructor.
    private void selectionChanged(RunRecord record) {
        if (restoring) {
            return;
        }
        if (record != null) {
            selectedKey = keyOf(record);
        }
        showDetails(record);
        refreshStatus();
    }

    // Describes what the screen shows, or what runs in the background.
    private void refreshStatus() {
        if (busy) {
            statusLabel.setText(busyMessage);
            return;
        }
        String status = "Earlier grading runs and their backups. Restoring "
                + "never changes original submissions or exported files.";
        if (!records.isEmpty()) {
            status += " " + records.size() + " run"
                    + Labels.plural(records.size()) + " listed.";
        }
        statusLabel.setText(status);
    }

    // Enables the buttons that fit the chosen run, and none while busy.
    private void refreshButtons(RunRecord record) {
        boolean selected = record != null;
        backupButton.setDisable(busy);
        openButton.setDisable(busy || !selected);
        if (restoreFromFile) {
            restoreButton.setDisable(busy);
        } else {
            restoreButton.setDisable(busy || !selected
                    || record.isCurrent() || !record.isRestorable());
        }
    }

    // Shows the selected run and what restoring it would do.
    private void showDetails(RunRecord record) {
        runBadgeHolder.getChildren().clear();
        consequenceBox.getChildren().clear();
        boolean selected = record != null;
        detailGrid.setVisible(selected);
        detailGrid.setManaged(selected);
        refreshButtons(record);
        if (selected) {
            showRun(record);
        } else {
            runTitle.setText("No run selected");
        }

        if (restoreFromFile) {
            addSteps(fileConsequences(null));
            restoreNote.setText(RESTORE_FILE_LABEL + " asks for a backup "
                    + "made by Back Up Now. Backups are kept in "
                    + getBackend().getSetting(ConfigKeys.BACKUP_DIR) + ".");
        } else if (!selected) {
            restoreNote.setText("Select a run to see what restoring it "
                    + "would do.");
        } else if (record.isCurrent()) {
            restoreNote.setText("Run " + record.getRun().getId() + " is the "
                    + "run shown now for " + sectionOf(record) + ", so "
                    + "there is nothing to restore. Open Run shows its "
                    + "results.");
        } else if (!record.isRestorable()) {
            restoreNote.setText("Run " + record.getRun().getId() + " has "
                    + "no backup, so it cannot be restored.");
        } else {
            addSteps(consequences(record));
            restoreNote.setText("You can restore the recovery snapshot to "
                    + "go back.");
        }
    }

    // Fills the details of one run.
    private void showRun(RunRecord record) {
        GradingRun run = record.getRun();
        runTitle.setText("Run " + run.getId());
        runBadgeHolder.getChildren().add(badgeOf(record));
        startedLabel.setText(Labels.formatDateTime(run.getStartedAt()));
        sectionLabel.setText(sectionOf(record));
        stateLabel.setText(record.getStateText());
        backupLabel.setText(record.getBackupText());
    }

    // Lists the steps of a restore under WHAT RESTORE DOES.
    private void addSteps(List<String> lines) {
        for (String line : lines) {
            Label step = new Label(line);
            step.setWrapText(true);
            step.setMinHeight(Region.USE_PREF_SIZE);
            consequenceBox.getChildren().add(step);
        }
    }

    // Returns the steps of restoring a backup file; null means any file.
    private List<String> fileConsequences(Path backupFile) {
        String source = "the backup file you choose";
        if (backupFile != null) {
            source = String.valueOf(backupFile.getFileName());
        }
        List<String> lines = new ArrayList<>();
        lines.add("1. The saved results are replaced by the ones in "
                + source + ".");
        lines.add("2. The results saved now are not backed up first, "
                + "because a recovery snapshot is not connected yet. Press "
                + "Back Up Now before restoring to keep them.");
        lines.add("3. Original ZIP files and exported files are not "
                + "changed.");
        return lines;
    }

    // Returns the numbered steps of restoring a run.
    private List<String> consequences(RunRecord record) {
        List<String> lines = new ArrayList<>();
        String section = sectionOf(record);
        GradingRun shown = null;
        if (record.getRun().getSection() != null) {
            shown = getBackend().getRun(record.getRun().getSection());
        }
        if (shown == null) {
            lines.add("1. Nothing is shown for " + section + " now, so no "
                    + "recovery snapshot is needed.");
        } else {
            lines.add("1. A recovery snapshot of the run shown now, run "
                    + shown.getId() + ", is saved.");
        }
        lines.add("2. Run " + record.getRun().getId() + " is shown for "
                + section + " instead.");
        lines.add("3. Original ZIP files and exported files are not "
                + "changed.");
        return lines;
    }

    // Writes the steps of restoring a run as one message.
    private String consequencesText(RunRecord record) {
        return String.join("\n", consequences(record)) + "\nYou can "
                + "restore the recovery snapshot to go back.";
    }

    // Explains why no run is listed.
    private EmptyState emptyState() {
        Capability history = Capability.HISTORY;
        if (history.getStatus() != CapabilityStatus.WORKS_NOW) {
            EmptyState waiting = new EmptyState(Icon.PLUG,
                    "No earlier runs to show yet", "Earlier runs appear "
                    + "here when " + history.getServiceMethod() + " ("
                    + history.getOwner() + ") returns the saved runs.");
            waiting.setAction("Backend Connections",
                    () -> getShell().navigate(Screen.HELP));
            return waiting;
        }
        EmptyState empty = new EmptyState(Icon.HISTORY,
                "No grading runs saved yet", "Grade a batch on the "
                + "Submissions screen. Each run is listed here with its "
                + "backups.");
        empty.setAction("Go to Submissions",
                () -> getShell().navigate(Screen.UPLOAD));
        return empty;
    }

    // Returns the badge of a run in the details.
    private StatusBadge badgeOf(RunRecord record) {
        if (record.isCurrent()) {
            return new StatusBadge(SHOWN_NOW, Tone.NEUTRAL, Icon.RESULTS);
        } else if (record.isRestorable()) {
            return new StatusBadge("Backup available", Tone.NEUTRAL,
                    Icon.HISTORY);
        }
        return new StatusBadge("No backup yet", Tone.NEUTRAL);
    }

    // Returns the section of a run, such as "CS102 G1".
    private static String sectionOf(RunRecord record) {
        Section section = record.getRun().getSection();
        if (section == null) {
            return "an unknown section";
        }
        return section.getLabel();
    }

    // Returns a key that names a run across reloads.
    private static String keyOf(RunRecord record) {
        return record.getRun().getId() + "|" + sectionOf(record);
    }

    // Adds the Run, Section, State, Shown and Backup columns.
    private void buildColumns() {
        TableColumn<RunRecord, RunRecord> run = new TableColumn<>("Run");
        run.setCellValueFactory(data -> new ReadOnlyObjectWrapper<>(
                data.getValue()));
        run.setCellFactory(column -> new RunCell());
        run.setMinWidth(RUN_WIDTH);
        run.setSortable(false);

        TableColumn<RunRecord, String> section = new TableColumn<>(
                "Section");
        section.setCellValueFactory(data -> new ReadOnlyStringWrapper(
                sectionOf(data.getValue())));
        section.setMinWidth(SECTION_WIDTH);

        TableColumn<RunRecord, String> state = new TableColumn<>("State");
        state.setCellValueFactory(data -> new ReadOnlyStringWrapper(
                data.getValue().getStateText()));
        state.setCellFactory(column -> new WrapCell());
        state.setMinWidth(STATE_WIDTH);

        TableColumn<RunRecord, RunRecord> shown = new TableColumn<>(
                "Shown");
        shown.setCellValueFactory(data -> new ReadOnlyObjectWrapper<>(
                data.getValue()));
        shown.setCellFactory(column -> new ShownCell());
        shown.setMinWidth(SHOWN_WIDTH);
        shown.setSortable(false);

        TableColumn<RunRecord, String> backup = new TableColumn<>("Backup");
        backup.setCellValueFactory(data -> new ReadOnlyStringWrapper(
                data.getValue().getBackupText()));
        backup.setCellFactory(column -> new WrapCell());
        backup.setMinWidth(BACKUP_WIDTH);

        table.getColumns().add(run);
        table.getColumns().add(section);
        table.getColumns().add(state);
        table.getColumns().add(shown);
        table.getColumns().add(backup);
    }

    /**
     * Shows the run id in bold with its start time under it.
     */
    private static class RunCell extends TableCell<RunRecord, RunRecord> {

        /**
         * Shows one run.
         *
         * @param record the row's run
         * @param empty whether the row is empty
         */
        @Override
        protected void updateItem(RunRecord record, boolean empty) {
            super.updateItem(record, empty);
            setText(null);
            if (empty || record == null) {
                setGraphic(null);
                setAccessibleText(null);
                return;
            }
            GradingRun run = record.getRun();
            Label id = new Label("Run " + run.getId());
            id.getStyleClass().add("context-value");
            Label started = new Label(Labels.formatDateTime(
                    run.getStartedAt()));
            started.getStyleClass().add("secondary-text");
            VBox box = new VBox(LINE_GAP, id, started);
            box.setAlignment(Pos.CENTER_LEFT);
            setGraphic(box);
            setAccessibleText("Run " + run.getId() + ", started "
                    + started.getText());
        }
    }

    /**
     * Shows the Shown now badge on the run the screens show.
     */
    private static class ShownCell extends TableCell<RunRecord, RunRecord> {

        /**
         * Shows the badge, or nothing for an earlier run.
         *
         * @param record the row's run
         * @param empty whether the row is empty
         */
        @Override
        protected void updateItem(RunRecord record, boolean empty) {
            super.updateItem(record, empty);
            setText(null);
            if (empty || record == null || !record.isCurrent()) {
                setGraphic(null);
                return;
            }
            setGraphic(new StatusBadge(SHOWN_NOW, Tone.NEUTRAL,
                    Icon.RESULTS));
        }
    }

    /**
     * Shows text that wraps onto a second line in a tall row.
     */
    private static class WrapCell extends TableCell<RunRecord, String> {

        /**
         * Creates a wrapping cell.
         */
        WrapCell() {
            setWrapText(true);
        }

        /**
         * Shows one text.
         *
         * @param text the text
         * @param empty whether the row is empty
         */
        @Override
        protected void updateItem(String text, boolean empty) {
            super.updateItem(text, empty);
            if (empty || text == null) {
                setText(null);
                return;
            }
            setText(text);
        }
    }
}
