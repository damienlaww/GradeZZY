/*
 * File: UploadController.java
 *
 * Purpose: Controller of the upload screen.
 *
 * Where it fits: The instructor picks a section, adds the ZIP files,
 * roster, testers and starter files, and starts grading here. The backend
 * checks each chosen file; this screen never opens one.
 */
package gradezzy.ui;

import java.io.File;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.List;

import javafx.collections.FXCollections;
import javafx.collections.ListChangeListener;
import javafx.event.ActionEvent;
import javafx.fxml.FXML;
import javafx.scene.Node;
import javafx.scene.control.Button;
import javafx.scene.control.ComboBox;
import javafx.scene.control.Label;
import javafx.scene.control.ListView;
import javafx.scene.control.SelectionMode;
import javafx.scene.layout.GridPane;
import javafx.scene.layout.HBox;
import javafx.scene.layout.VBox;
import javafx.stage.DirectoryChooser;
import javafx.stage.FileChooser;

import gradezzy.config.ConfigKeys;
import gradezzy.model.GradingRun;
import gradezzy.ui.backend.ImportBatch;
import gradezzy.ui.backend.ImportItem;
import gradezzy.ui.backend.ImportState;
import gradezzy.ui.backend.InputKind;
import gradezzy.ui.component.Labels;
import gradezzy.ui.component.Responsive;
import gradezzy.ui.view.ImportRows;
import gradezzy.ui.view.ImportRows.ImportCell;

/**
 * Controller of the upload screen.
 */
public class UploadController extends BaseController {

    // The share of the width given to the submissions panel
    private static final double SUBMISSIONS_PERCENT = 58.0;

    // The width below which the side column moves under the list
    private static final double STACK_WIDTH = 720.0;

    // The file picker filter for submissions
    private static final String ZIP_PATTERN = "*.zip";

    // The file picker filter for the roster
    private static final String CSV_PATTERN = "*.csv";

    @FXML
    private Label statusLabel;

    @FXML
    private Button validationButton;

    @FXML
    private Button startButton;

    @FXML
    private Label safetyLabel;

    @FXML
    private VBox blockingBanner;

    @FXML
    private VBox reasonsBox;

    @FXML
    private GridPane columns;

    @FXML
    private VBox submissionPanel;

    @FXML
    private VBox sideColumn;

    @FXML
    private Label sectionLabel;

    @FXML
    private ComboBox<String> sectionBox;

    @FXML
    private VBox dropZone;

    @FXML
    private Label limitsLabel;

    @FXML
    private Label submissionSummary;

    @FXML
    private ListView<ImportItem> submissionList;

    @FXML
    private Button replaceButton;

    @FXML
    private Button removeButton;

    @FXML
    private Button clearButton;

    @FXML
    private HBox rosterStatus;

    @FXML
    private HBox testersStatus;

    @FXML
    private HBox starterStatus;

    @FXML
    private Label testersHelp;

    @FXML
    private Button rosterConfigured;

    @FXML
    private Button testersConfigured;

    @FXML
    private Button starterConfigured;

    @FXML
    private Button rosterRemove;

    @FXML
    private Button testersRemove;

    @FXML
    private Button starterRemove;

    // Accepts ZIP files and folders dropped on the drop area
    private final DragDropHandler dragDropHandler = new DragDropHandler();

    // Whether the section choice is being filled in by code
    private boolean updatingSection = false;

    /**
     * Creates a new upload controller.
     */
    public UploadController() {
        // nothing to set up
    }

    /**
     * Called by JavaFX after the FXML file is loaded.
     */
    @FXML
    public void initialize() {
        sectionLabel.setLabelFor(sectionBox);
        submissionList.getSelectionModel().setSelectionMode(
                SelectionMode.MULTIPLE);
        submissionList.setPlaceholder(new Label("No ZIP files added yet."));
        submissionList.getSelectionModel().getSelectedItems().addListener(
                (ListChangeListener<ImportItem>) change
                -> updateListButtons());

        dragDropHandler.install(dropZone);
        dragDropHandler.setOnDropped(() -> addDroppedFiles());
        Responsive.twoColumns(columns, submissionPanel, sideColumn,
                SUBMISSIONS_PERCENT, STACK_WIDTH);
    }

    /**
     * Sets up the parts that depend on the backend.
     */
    @Override
    public void onAttach() {
        submissionList.setCellFactory(list -> new ImportCell(
                getState().getImportBatch()));
        limitsLabel.setText("One ZIP file per student. Each may hold up to "
                + getBackend().getSetting(ConfigKeys.MAX_ZIP_SIZE_MB)
                + " MB and " + getBackend().getSetting(
                ConfigKeys.MAX_ZIP_ENTRIES) + " files once unpacked.");

        boolean demo = getBackend().isDemo();
        if (demo) {
            safetyLabel.setText("Original ZIP files are never changed. Demo "
                    + "mode adds sample files instead of opening the file "
                    + "picker, and never reads a real file.");
        }
        rosterConfigured.setVisible(!demo);
        rosterConfigured.setManaged(!demo);
        testersConfigured.setVisible(!demo);
        testersConfigured.setManaged(!demo);
        starterConfigured.setVisible(!demo);
        starterConfigured.setManaged(!demo);
    }

    /**
     * Refreshes the screen each time it is shown.
     */
    @Override
    public void onShow() {
        fillSections();
        refresh();
    }

    /**
     * Opens the file picker, or adds the sample files in demo mode.
     */
    @FXML
    public void handleChooseFiles() {
        if (getBackend().isDemo()) {
            addSampleSubmissions();
            return;
        }

        FileChooser chooser = new FileChooser();
        chooser.setTitle("Choose student ZIP files");
        chooser.getExtensionFilters().add(new FileChooser.ExtensionFilter(
                "ZIP files", ZIP_PATTERN));
        List<File> files = chooser.showOpenMultipleDialog(
                getShell().getWindow());
        if (files == null) {
            return;
        }

        List<Path> paths = new ArrayList<>();
        for (File file : files) {
            paths.add(file.toPath());
        }
        addSubmissions(paths);
    }

    /**
     * Opens the folder picker, or adds the sample files in demo mode.
     */
    @FXML
    public void handleChooseFolder() {
        if (getBackend().isDemo()) {
            addSampleSubmissions();
            return;
        }

        DirectoryChooser chooser = new DirectoryChooser();
        chooser.setTitle("Choose a folder of student ZIP files");
        File folder = chooser.showDialog(getShell().getWindow());
        if (folder == null) {
            return;
        }

        List<Path> paths = new ArrayList<>();
        paths.add(folder.toPath());
        addSubmissions(paths);
    }

    /**
     * Reacts to a new section choice.
     */
    @FXML
    public void handleSectionChosen() {
        if (updatingSection || sectionBox.getValue() == null) {
            return;
        }
        getState().sectionCodeProperty().set(sectionBox.getValue());
        refresh();
    }

    /**
     * Starts grading the whole batch and moves to the progress screen.
     */
    @FXML
    public void handleStartGrading() {
        ImportBatch batch = getState().getImportBatch();
        if (!batch.isReady()) {
            showError("Start Grading is not available yet: "
                    + String.join(" ", batch.getBlockingReasons()));
            return;
        }
        if (getState().getMonitor().isActive()) {
            showError("A batch is already being graded. Wait for it to "
                    + "finish, or cancel it on the Grading Progress screen.");
            return;
        }

        int count = batch.getSubmissionPaths().size();
        String description = "Grading " + count + " submission"
                + Labels.plural(count) + " for "
                + getState().getSection().getLabel() + ".";
        getState().getMonitor().begin(description);
        getBackend().recordAudit("Grading started", description);
        getBackend().startGrading(batch, getState().getSection(),
                getState().getMonitor(), new RunFollower(getBackend(),
                getState(), getShell(), "Grading"));
        getShell().navigate(Screen.PROGRESS);
    }

    /**
     * Replaces the selected file that cannot be used.
     */
    @FXML
    public void handleReplace() {
        ImportItem item = submissionList.getSelectionModel()
                .getSelectedItem();
        if (item == null) {
            return;
        }

        Path replacement = getBackend().getSampleReplacement(item);
        if (replacement == null) {
            FileChooser chooser = new FileChooser();
            chooser.setTitle("Choose the corrected ZIP file");
            chooser.getExtensionFilters().add(
                    new FileChooser.ExtensionFilter("ZIP files",
                    ZIP_PATTERN));
            File file = chooser.showOpenDialog(getShell().getWindow());
            if (file == null) {
                return;
            }
            replacement = file.toPath();
        }

        ImportItem checked = getBackend().inspect(InputKind.SUBMISSION,
                replacement);
        getState().getImportBatch().replace(item, checked);
        refresh();
        showInfo("Replaced " + item.getName() + " with "
                + checked.getName() + ".");
    }

    /**
     * Removes the selected files from the batch.
     */
    @FXML
    public void handleRemove() {
        List<ImportItem> selected = new ArrayList<>(
                submissionList.getSelectionModel().getSelectedItems());
        for (ImportItem item : selected) {
            getState().getImportBatch().remove(item);
        }
        refresh();
        if (!selected.isEmpty()) {
            showInfo("Removed " + selected.size() + " file"
                    + Labels.plural(selected.size()) + " from the batch.");
        }
    }

    /**
     * Removes every chosen ZIP file after a confirmation.
     */
    @FXML
    public void handleClearAll() {
        getShell().confirm("Remove every ZIP file?", "The files are only "
                + "removed from this batch. Nothing is deleted from disk.",
                "Remove All", true, () -> {
                    getState().getImportBatch().clear(InputKind.SUBMISSION);
                    refresh();
                    showInfo("Removed every ZIP file from the batch.");
                });
    }

    /**
     * Chooses the roster, tester folder or starter folder, as named by the
     * button's user data.
     *
     * @param event the button press
     */
    @FXML
    public void handleChooseInput(ActionEvent event) {
        chooseInput(kindOf(event));
    }

    /**
     * Chooses an input with a picker, or a sample file in demo mode.
     *
     * @param kind the roster, tester or starter group
     */
    public void chooseInput(InputKind kind) {
        Path path = choosePath(kind);
        if (path == null) {
            return;
        }
        addInput(kind, path);
    }

    /**
     * Selects files in the ZIP list, as a click would. Used by the self-test.
     *
     * @param items the files to select
     */
    public void selectFiles(List<ImportItem> items) {
        submissionList.getSelectionModel().clearSelection();
        for (ImportItem item : items) {
            submissionList.getSelectionModel().select(item);
        }
        updateListButtons();
    }

    /**
     * Uses the path config.properties names for a group.
     *
     * @param event the button press
     */
    @FXML
    public void handleUseConfigured(ActionEvent event) {
        InputKind kind = kindOf(event);
        Path path = getBackend().getConfiguredPath(kind);
        if (path == null) {
            showError("config.properties names no path for the "
                    + kind.getTitle().toLowerCase() + ".");
            return;
        }
        addInput(kind, path);
    }

    /**
     * Removes the roster, tester folder or starter folder.
     *
     * @param event the button press
     */
    @FXML
    public void handleRemoveInput(ActionEvent event) {
        InputKind kind = kindOf(event);
        getState().getImportBatch().clear(kind);
        refresh();
    }

    /**
     * Opens the Validation screen for the section's run.
     */
    @FXML
    public void handleOpenValidation() {
        getShell().navigate(Screen.VALIDATION);
    }

    /**
     * Opens the Assessments screen, where test cases can be typed.
     */
    @FXML
    public void handleOpenAssessments() {
        getShell().navigate(Screen.TEST_CASES);
    }

    // Adds the six sample ZIP files in demo mode.
    private void addSampleSubmissions() {
        List<Path> samples = getBackend().getSampleFiles(
                InputKind.SUBMISSION);
        addSubmissions(samples);
    }

    // Adds files dropped on the drop area.
    private void addDroppedFiles() {
        if (getBackend().isDemo()) {
            showInfo("Demo mode does not read dropped files, so the sample "
                    + "files were added instead.");
            addSampleSubmissions();
            return;
        }
        addSubmissions(dragDropHandler.getDroppedPaths());
    }

    // Checks each path and adds it to the batch.
    private void addSubmissions(List<Path> paths) {
        ImportBatch batch = getState().getImportBatch();
        int invalid = 0;
        for (Path path : paths) {
            ImportItem item = getBackend().inspect(InputKind.SUBMISSION,
                    path);
            batch.add(item);
            if (batch.getEffectiveState(item) == ImportState.INVALID) {
                invalid++;
            }
        }
        refresh();

        String message = "Added " + paths.size() + " file"
                + Labels.plural(paths.size()) + ".";
        if (invalid > 0) {
            message += " " + invalid + " cannot be used; replace or remove "
                    + "it before grading.";
        }
        showInfo(message);
    }

    // Checks a roster, tester or starter path and keeps it.
    private void addInput(InputKind kind, Path path) {
        ImportItem item = getBackend().inspect(kind, path);
        getState().getImportBatch().add(item);
        if (kind == InputKind.TESTERS) {
            getState().getImportBatch().setUsingTypedTestCases(false);
        }
        refresh();
        showInfo("Added " + item.getName() + " as the "
                + kind.getTitle().toLowerCase() + ".");
    }

    // Asks for a roster file or a folder, or returns a sample in demo mode.
    private Path choosePath(InputKind kind) {
        if (getBackend().isDemo()) {
            List<Path> samples = getBackend().getSampleFiles(kind);
            if (samples.isEmpty()) {
                return null;
            }
            return samples.get(0);
        }

        if (kind == InputKind.ROSTER) {
            FileChooser chooser = new FileChooser();
            chooser.setTitle("Choose the score sheet");
            chooser.getExtensionFilters().add(
                    new FileChooser.ExtensionFilter("CSV files",
                    CSV_PATTERN));
            File file = chooser.showOpenDialog(getShell().getWindow());
            if (file == null) {
                return null;
            }
            return file.toPath();
        }

        DirectoryChooser chooser = new DirectoryChooser();
        chooser.setTitle("Choose the " + kind.getTitle().toLowerCase()
                + " folder");
        File folder = chooser.showDialog(getShell().getWindow());
        if (folder == null) {
            return null;
        }
        return folder.toPath();
    }

    // Reads which group a button belongs to from its user data.
    private InputKind kindOf(ActionEvent event) {
        Object data = ((Node) event.getSource()).getUserData();
        return InputKind.valueOf(String.valueOf(data));
    }

    // Fills the section choice from the module in the context bar.
    private void fillSections() {
        updatingSection = true;
        List<String> sections = getBackend().getSectionCodes(
                getState().moduleCodeProperty().get());
        sectionBox.setItems(FXCollections.observableArrayList(sections));
        sectionBox.setValue(getState().sectionCodeProperty().get());
        updatingSection = false;
    }

    // Redraws every part of the screen from the batch.
    private void refresh() {
        ImportBatch batch = getState().getImportBatch();
        List<ImportItem> submissions = batch.getItems(InputKind.SUBMISSION);
        submissionList.setItems(FXCollections.observableArrayList(
                submissions));
        submissionList.refresh();

        ImportRows.showChosen(rosterStatus, InputKind.ROSTER, batch);
        ImportRows.showChosen(starterStatus, InputKind.STARTER, batch);
        if (batch.isUsingTypedTestCases()) {
            ImportRows.showTypedTesters(testersStatus, batch);
        } else {
            ImportRows.showChosen(testersStatus, InputKind.TESTERS,
                    batch);
        }

        submissionSummary.setText(ImportRows.summarise(batch));
        submissionSummary.setVisible(!submissions.isEmpty());
        submissionSummary.setManaged(!submissions.isEmpty());
        rosterRemove.setDisable(batch.getItems(InputKind.ROSTER).isEmpty());
        testersRemove.setDisable(batch.getItems(InputKind.TESTERS)
                .isEmpty());
        starterRemove.setDisable(batch.getItems(InputKind.STARTER)
                .isEmpty());
        List<String> reasons = batch.getBlockingReasons();
        reasonsBox.getChildren().clear();
        for (String reason : reasons) {
            Label line = new Label("\u2022 " + reason);
            line.setWrapText(true);
            reasonsBox.getChildren().add(line);
        }
        boolean ready = reasons.isEmpty();
        blockingBanner.setVisible(!ready);
        blockingBanner.setManaged(!ready);
        startButton.setDisable(!ready);

        String section = getState().getSection().getLabel();
        if (ready) {
            int count = batch.getSubmissionPaths().size();
            statusLabel.setText("Ready to grade " + count + " submission"
                    + Labels.plural(count) + " for " + section + ".");
        } else {
            statusLabel.setText("Add the inputs for " + section + ". "
                    + reasons.size() + " thing" + Labels.plural(
                    reasons.size()) + " left before grading can start.");
        }

        GradingRun run = getBackend().getRun(getState().getSection());
        validationButton.setVisible(run != null);
        validationButton.setManaged(run != null);
        updateListButtons();
    }

    // Enables Replace and Remove for the current selection.
    private void updateListButtons() {
        List<ImportItem> selected =
                submissionList.getSelectionModel().getSelectedItems();
        boolean oneInvalid = selected.size() == 1 && getState()
                .getImportBatch().getEffectiveState(selected.get(0))
                == ImportState.INVALID;
        replaceButton.setDisable(!oneInvalid);
        removeButton.setDisable(selected.isEmpty());
        clearButton.setDisable(submissionList.getItems().isEmpty());
    }
}
