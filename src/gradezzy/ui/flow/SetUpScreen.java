/*
 * File: SetUpScreen.java
 *
 * Purpose: Step 1. The instructor chooses the module and section, adds
 * the students' zip files and checks the score sheet and tester files,
 * then starts grading.
 *
 * Where it fits: Replaces the old Modules, Assessments, Submissions and
 * Validation screens. Calls DashboardBackend.inspect for every file and
 * hands the batch to GradeScreen.
 */
package gradezzy.ui.flow;

import java.io.File;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.List;

import javafx.geometry.Pos;
import javafx.scene.Node;
import javafx.scene.control.ComboBox;
import javafx.scene.control.Label;
import javafx.scene.control.ScrollPane;
import javafx.scene.layout.HBox;
import javafx.scene.layout.Priority;
import javafx.scene.layout.VBox;
import javafx.stage.DirectoryChooser;
import javafx.stage.FileChooser;

import gradezzy.model.Assessment;
import gradezzy.model.GradingRun;
import gradezzy.ui.DragDropHandler;
import gradezzy.ui.backend.ImportBatch;
import gradezzy.ui.backend.ImportItem;
import gradezzy.ui.backend.ImportState;
import gradezzy.ui.backend.InputKind;

/**
 * The Set up step.
 */
public class SetUpScreen extends FlowScreen {

    // The file name pattern of a submission
    private static final String ZIP_PATTERN = "*.zip";

    // The file name pattern of a score sheet
    private static final String CSV_PATTERN = "*.csv";

    // The size of the icon in the drop area
    private static final double DROP_ICON_SIZE = 22.0;

    // The size of a file icon in a list row
    private static final double ROW_ICON_SIZE = 18.0;

    // The most file rows shown before the list scrolls
    private static final double LIST_MAX_HEIGHT = 176.0;

    // The width of the module and section boxes
    private static final double CHOICE_WIDTH = 150.0;

    // The scrolling page
    private final ScrollPane view = new ScrollPane();

    // The module choice
    private final ComboBox<String> moduleBox = new ComboBox<>();

    // The section choice
    private final ComboBox<String> sectionBox = new ComboBox<>();

    // A note when the chosen section was graded before
    private final HBox earlierRun = Parts.row(Parts.GAP_SMALL);

    // The drop area
    private final VBox dropArea = new VBox(Parts.GAP_SMALL);

    // The chosen zip files
    private final VBox fileList = new VBox();

    // Scrolls the file list when it is long
    private final ScrollPane listScroll = new ScrollPane(fileList);

    // The line under the file list
    private final HBox summary = Parts.row(Parts.GAP_SMALL);

    // The score sheet, tester and starter rows
    private final VBox classFiles = new VBox(Parts.GAP);

    // Reads files dropped on the drop area
    private final DragDropHandler dropHandler = new DragDropHandler();

    // True once the class files were filled from the settings
    private boolean filledDefaults = false;

    // True while the choices are being filled, so no change is handled
    private boolean filling = false;

    /**
     * Creates the Set up step.
     *
     * @param window the window
     */
    public SetUpScreen(FlowWindow window) {
        super(window);
        VBox column = new VBox(Parts.GAP,
                Parts.header("Set up a grading run", "Choose the class, "
                + "add the students' zip files, then start grading."),
                createClassCard(), createSubmissionCard(),
                Parts.card("Class files", classFiles));
        column.getStyleClass().add("narrow-column");
        VBox centre = new VBox(column);
        centre.setAlignment(Pos.TOP_CENTER);
        view.setContent(centre);
        view.setFitToWidth(true);
        view.getStyleClass().add("page-scroll");
    }

    @Override
    public Node getView() {
        return view;
    }

    @Override
    public void onShow() {
        fillChoices();
        if (!filledDefaults) {
            fillClassFiles();
            filledDefaults = true;
        }
        refresh();
    }

    // ------------------------------------------------------------ build

    // Builds the module, section and assessment row.
    private VBox createClassCard() {
        moduleBox.setPrefWidth(CHOICE_WIDTH);
        sectionBox.setPrefWidth(CHOICE_WIDTH);
        moduleBox.setOnAction(event -> chooseModule());
        sectionBox.setOnAction(event -> chooseSection());

        Assessment assessment = getBackend().getAssessment();
        String about = "Not set";
        if (assessment != null) {
            about = assessment.getName() + "  ·  "
                    + Parts.plural(assessment.getQuestions().size(),
                    "question") + ", "
                    + Parts.mark(assessment.getMaxTotal()) + " marks";
        }
        Label assessmentText = new Label(about);
        assessmentText.getStyleClass().add("read-only-field");
        assessmentText.setMaxWidth(Double.MAX_VALUE);

        VBox assessmentField = field("Assessment", assessmentText);
        HBox.setHgrow(assessmentField, Priority.ALWAYS);
        HBox fields = new HBox(Parts.GAP, field("Module", moduleBox),
                field("Section", sectionBox), assessmentField);
        return Parts.card("Class", fields, earlierRun);
    }

    // Builds the drop area, the file list and its summary line.
    private VBox createSubmissionCard() {
        Label drop = Parts.strong("Drop the zip files or their folder here");
        HBox choose = Parts.row(0, Parts.muted("or "),
                Parts.link("choose files", () -> chooseSubmissions()));
        choose.setAlignment(Pos.CENTER);
        VBox iconCircle = new VBox(Parts.icon(Parts.UPLOAD, DROP_ICON_SIZE));
        iconCircle.getStyleClass().add("drop-icon");
        dropArea.getChildren().addAll(iconCircle, drop, choose);
        dropArea.getStyleClass().add("drop-area");
        dropArea.setAlignment(Pos.CENTER);
        dropHandler.install(dropArea);
        dropHandler.setOnDropped(() -> addSubmissions(
                dropHandler.getDroppedPaths()));

        listScroll.setFitToWidth(true);
        listScroll.setMaxHeight(LIST_MAX_HEIGHT);
        listScroll.getStyleClass().add("file-scroll");
        fileList.getStyleClass().add("file-list");
        return Parts.card("Submissions", dropArea, summary, listScroll);
    }

    // Puts a small label above a control.
    private VBox field(String label, Node control) {
        Label name = Parts.muted(label);
        name.getStyleClass().add("field-label");
        VBox box = new VBox(Parts.GAP_SMALL / 2, name, control);
        return box;
    }

    // ---------------------------------------------------------- choices

    // Fills the module and section boxes from the backend.
    private void fillChoices() {
        filling = true;
        List<String> modules = getBackend().getModuleCodes();
        moduleBox.getItems().setAll(modules);
        String module = getState().getModuleCode();
        if (!modules.contains(module) && !modules.isEmpty()) {
            module = modules.get(0);
        }
        moduleBox.setValue(module);
        getState().setModuleCode(module);
        fillSections();
        filling = false;
    }

    // Fills the section box for the chosen module.
    private void fillSections() {
        List<String> sections = getBackend().getSectionCodes(
                getState().getModuleCode());
        sectionBox.getItems().setAll(sections);
        String section = getState().getSectionCode();
        if (!sections.contains(section) && !sections.isEmpty()) {
            section = sections.get(0);
        }
        sectionBox.setValue(section);
        getState().setSectionCode(section);
    }

    // Handles a new module.
    private void chooseModule() {
        if (filling || moduleBox.getValue() == null) {
            return;
        }
        filling = true;
        getState().setModuleCode(moduleBox.getValue());
        fillSections();
        filling = false;
        refresh();
    }

    // Handles a new section.
    private void chooseSection() {
        if (filling || sectionBox.getValue() == null) {
            return;
        }
        getState().setSectionCode(sectionBox.getValue());
        refresh();
    }

    // ------------------------------------------------------------ files

    // Asks for zip files, or adds the samples in demo mode.
    private void chooseSubmissions() {
        if (getBackend().isDemo()) {
            addSubmissions(getBackend().getSampleFiles(
                    InputKind.SUBMISSION));
            return;
        }
        FileChooser chooser = new FileChooser();
        chooser.setTitle("Choose the students' zip files");
        chooser.getExtensionFilters().add(new FileChooser.ExtensionFilter(
                "Zip files", ZIP_PATTERN));
        List<File> files = chooser.showOpenMultipleDialog(
                getWindow().getWindow());
        if (files == null) {
            return;
        }
        List<Path> paths = new ArrayList<>();
        for (File file : files) {
            paths.add(file.toPath());
        }
        addSubmissions(paths);
    }

    // Checks each path and adds it to the batch.
    private void addSubmissions(List<Path> paths) {
        if (getBackend().isDemo() && paths.isEmpty()) {
            return;
        }
        ImportBatch batch = getState().getBatch();
        for (Path path : paths) {
            batch.add(getBackend().inspect(InputKind.SUBMISSION, path));
        }
        refresh();
        getWindow().notify("Added " + Parts.plural(paths.size(), "file")
                + ".");
    }

    // Removes one zip file from the batch.
    private void removeSubmission(ImportItem item) {
        getState().getBatch().remove(item);
        refresh();
    }

    // Fills the score sheet, tester and starter rows from the settings.
    private void fillClassFiles() {
        InputKind[] kinds = {InputKind.ROSTER, InputKind.TESTERS,
            InputKind.STARTER};
        ImportBatch batch = getState().getBatch();

        for (InputKind kind : kinds) {
            if (batch.getItems(kind).isEmpty()) {
                addDefault(kind);
            }
        }
    }

    // Adds the configured path of a kind, or the demo sample.
    private void addDefault(InputKind kind) {
        Path path = getBackend().getConfiguredPath(kind);
        if (path == null && getBackend().isDemo()) {
            List<Path> samples = getBackend().getSampleFiles(kind);
            if (!samples.isEmpty()) {
                path = samples.get(0);
            }
        }
        if (path != null) {
            getState().getBatch().add(getBackend().inspect(kind, path));
        }
    }

    // Asks for a new score sheet, tester or starter folder.
    private void changeClassFile(InputKind kind) {
        Path path = null;
        if (getBackend().isDemo()) {
            List<Path> samples = getBackend().getSampleFiles(kind);
            if (!samples.isEmpty()) {
                path = samples.get(0);
            }
        } else if (kind == InputKind.ROSTER) {
            FileChooser chooser = new FileChooser();
            chooser.setTitle("Choose the score sheet");
            chooser.getExtensionFilters().add(
                    new FileChooser.ExtensionFilter("CSV files",
                    CSV_PATTERN));
            File file = chooser.showOpenDialog(getWindow().getWindow());
            if (file != null) {
                path = file.toPath();
            }
        } else {
            DirectoryChooser chooser = new DirectoryChooser();
            chooser.setTitle("Choose the " + kind.getTitle().toLowerCase()
                    + " folder");
            File folder = chooser.showDialog(getWindow().getWindow());
            if (folder != null) {
                path = folder.toPath();
            }
        }
        if (path == null) {
            return;
        }
        ImportBatch batch = getState().getBatch();
        batch.clear(kind);
        batch.add(getBackend().inspect(kind, path));
        if (kind == InputKind.TESTERS) {
            batch.setUsingTypedTestCases(false);
        }
        refresh();
    }

    // ---------------------------------------------------------- refresh

    // Redraws the lists and the action bar.
    private void refresh() {
        refreshEarlierRun();
        refreshFiles();
        refreshClassFiles();
        refreshActions();
    }

    // Offers the results of an earlier run of the chosen section.
    private void refreshEarlierRun() {
        earlierRun.getChildren().clear();
        GradingRun run = getBackend().getRun(getState().getSection());
        if (run == null || run.getSubmissions().isEmpty()) {
            return;
        }
        earlierRun.getChildren().addAll(
                Parts.muted(getState().getSection().getLabel()
                + " was graded before ("
                + Parts.plural(run.getSubmissions().size(), "student")
                + ")."),
                Parts.link("Open its results", () -> getWindow()
                .openRun(run)));
    }

    // Redraws the zip file list and the summary line.
    private void refreshFiles() {
        ImportBatch batch = getState().getBatch();
        List<ImportItem> items = batch.getItems(InputKind.SUBMISSION);
        fileList.getChildren().clear();
        for (ImportItem item : items) {
            fileList.getChildren().add(createFileRow(item));
        }
        listScroll.setVisible(!items.isEmpty());
        listScroll.setManaged(!items.isEmpty());
        dropArea.getStyleClass().remove("compact");
        if (!items.isEmpty()) {
            dropArea.getStyleClass().add("compact");
        }

        summary.getChildren().clear();
        if (items.isEmpty()) {
            summary.setVisible(false);
            summary.setManaged(false);
            return;
        }
        summary.setVisible(true);
        summary.setManaged(true);
        int unusable = batch.count(InputKind.SUBMISSION,
                ImportState.INVALID) + batch.count(InputKind.SUBMISSION,
                ImportState.DUPLICATE);
        int ready = items.size() - unusable;
        Node tick = Parts.icon(Parts.CHECK, ROW_ICON_SIZE);
        tick.getStyleClass().add("good-icon");
        summary.getChildren().addAll(tick,
                Parts.strong(Parts.plural(ready, "zip file") + " ready."));
        if (unusable > 0) {
            summary.getChildren().add(Parts.muted(unusable
                    + " cannot be used; remove or replace "
                    + (unusable == 1 ? "it." : "them.")));
        }
        summary.getChildren().addAll(Parts.grow(),
                Parts.link("Clear all", () -> {
                    getState().getBatch().clear(InputKind.SUBMISSION);
                    refresh();
                }));
    }

    // Builds one row of the zip file list.
    private HBox createFileRow(ImportItem item) {
        ImportState state = getState().getBatch().getEffectiveState(item);
        VBox text = new VBox(Parts.strong(item.getName()));
        if (!item.getMessage().isEmpty() && !state.isUsable()) {
            text.getChildren().add(Parts.muted(item.getMessage()));
        } else if (!item.getUsername().isEmpty()) {
            text.getChildren().add(Parts.muted(item.getUsername()));
        }
        HBox.setHgrow(text, Priority.ALWAYS);
        HBox row = Parts.row(Parts.GAP,
                Parts.icon(Parts.FILE, ROW_ICON_SIZE), text,
                Parts.pill(stateText(state), toneOf(state)),
                Parts.link("Remove", () -> removeSubmission(item)));
        row.getStyleClass().add("file-row");
        return row;
    }

    // Redraws the score sheet, tester and starter rows.
    private void refreshClassFiles() {
        classFiles.getChildren().setAll(
                createClassFileRow(InputKind.ROSTER, "Score sheet"),
                createClassFileRow(InputKind.TESTERS, "Tester files"),
                createClassFileRow(InputKind.STARTER,
                "Starter files (optional)"));
    }

    // Builds one class file row.
    private HBox createClassFileRow(InputKind kind, String title) {
        List<ImportItem> items = getState().getBatch().getItems(kind);
        String detail;
        Node status;
        if (items.isEmpty()) {
            detail = kind.isRequired() ? "Not chosen yet."
                    : "Not chosen. The untouched-answer check is skipped.";
            status = Parts.pill(kind.isRequired() ? "Missing"
                    : "Skipped", kind.isRequired() ? Tone.BAD
                    : Tone.NEUTRAL);
        } else {
            ImportItem item = items.get(0);
            ImportState state = getState().getBatch()
                    .getEffectiveState(item);
            detail = item.getName() + "  ·  " + item.getMessage();
            status = Parts.pill(stateText(state), toneOf(state));
        }
        String iconPath = Parts.FOLDER;
        if (kind == InputKind.ROSTER) {
            iconPath = Parts.FILE;
        }
        VBox text = new VBox(Parts.strong(title), Parts.muted(detail));
        HBox.setHgrow(text, Priority.ALWAYS);
        HBox row = Parts.row(Parts.GAP, Parts.icon(iconPath, ROW_ICON_SIZE),
                text, status, Parts.link("Change",
                () -> changeClassFile(kind)));
        return row;
    }

    // Fills the action bar.
    private void refreshActions() {
        ImportBatch batch = getState().getBatch();
        ActionBar actions = getActions();
        actions.reset();
        boolean ready = batch.isReady() && !getState().isGrading();
        actions.setPrimary("Start grading", () -> getWindow()
                .startGrading(), ready);
        if (getState().isGrading()) {
            actions.setStatus("A batch is being graded. See step 2.");
        } else if (!batch.isReady()) {
            List<String> reasons = batch.getBlockingReasons();
            actions.setStatus(reasons.isEmpty() ? ""
                    : reasons.get(0));
        } else {
            int count = batch.count(InputKind.SUBMISSION,
                    ImportState.ACCEPTED) + batch.count(
                    InputKind.SUBMISSION, ImportState.WARNING);
            actions.setStatus("Ready to grade "
                    + Parts.plural(count, "student") + " in "
                    + getState().getSection().getLabel() + ".");
        }
    }

    // Returns the plain words for a file's state.
    private String stateText(ImportState state) {
        if (state == ImportState.ACCEPTED) {
            return "Ready";
        } else if (state == ImportState.WARNING) {
            return "Ready, check note";
        } else if (state == ImportState.DUPLICATE) {
            return "Added twice";
        }
        return "Cannot be used";
    }

    // Returns the colour for a file's state.
    private Tone toneOf(ImportState state) {
        if (state == ImportState.ACCEPTED) {
            return Tone.GOOD;
        } else if (state == ImportState.WARNING) {
            return Tone.WARN;
        }
        return Tone.BAD;
    }
}
