/*
 * File: SetUpScreen.java
 *
 * Purpose: Step 1. The instructor chooses the module, section and
 * assessment, adds the students' zip files, checks the score sheet and
 * tester files, sees the grading settings this run will use, then starts
 * grading. A "?" beside each word a first-time user may not know
 * explains it.
 *
 * Where it fits: Replaces the old Modules, Assessments, Submissions and
 * Validation screens. Calls DashboardBackend.inspect for every file and
 * hands the batch to GradeScreen. TestCasesSheet edits typed test cases.
 */
package gradezzy.ui.flow;

import java.io.File;
import java.nio.file.Path;
import java.time.format.DateTimeFormatter;
import java.util.ArrayList;
import java.util.List;

import javafx.geometry.HPos;
import javafx.geometry.Pos;
import javafx.scene.Node;
import javafx.scene.control.Button;
import javafx.scene.control.ComboBox;
import javafx.scene.control.Hyperlink;
import javafx.scene.control.Label;
import javafx.scene.control.ScrollPane;
import javafx.scene.layout.ColumnConstraints;
import javafx.scene.layout.GridPane;
import javafx.scene.layout.HBox;
import javafx.scene.layout.Priority;
import javafx.scene.layout.StackPane;
import javafx.scene.layout.VBox;
import javafx.stage.DirectoryChooser;
import javafx.stage.FileChooser;

import gradezzy.config.ConfigKeys;
import gradezzy.model.Assessment;
import gradezzy.model.GradingRun;
import gradezzy.model.SavedAssessment;
import gradezzy.model.Question;
import gradezzy.ui.DragDropHandler;
import gradezzy.ui.backend.Capability;
import gradezzy.ui.backend.CapabilityStatus;
import gradezzy.ui.backend.ImportBatch;
import gradezzy.ui.backend.ImportItem;
import gradezzy.ui.backend.ImportState;
import gradezzy.ui.backend.InputKind;
import gradezzy.ui.backend.RunRecord;

/**
 * The Set up step.
 */
public class SetUpScreen extends FlowScreen {

    // The file name pattern of a submission
    private static final String ZIP_PATTERN = "*.zip";

    // The file name pattern of a score sheet
    private static final String CSV_PATTERN = "*.csv";

    // The text a true setting holds
    private static final String TRUE = "true";

    // When a run was graded, such as "28 Oct 2026, 14:05"
    private static final DateTimeFormatter WHEN =
            DateTimeFormatter.ofPattern("d MMM yyyy, HH:mm");

    // The separator between a file and what is known about it
    private static final String DOT = "  ·  ";

    // The table of questions and marks shown before grading starts
    private static final double MARKS_TABLE_WIDTH = 420.0;
    private static final double MARKS_ROW_HEIGHT = 32.0;
    private static final double MARKS_QUESTION_WIDTH = 84.0;
    private static final double MARKS_FOLDER_WIDTH = 110.0;
    private static final double MARKS_MAX_WIDTH = 64.0;

    // How grading picks one file when a student has several
    private static final String ONE_FILE_RULE = "Only one file per "
            + "student is graded: the newest download, with the highest "
            + "copy number such as (1), else the one named in lower "
            + "case, else the first chosen. The others are not graded and "
            + "are noted on that student.";

    // What the score sheet is, for a first-time user
    private static final String ROSTER_HELP = "The class list exported "
            + "from eLearn as a CSV file. It matches each zip file to a "
            + "student, and Export fills the marks into a copy of it.";

    // What the tester files are
    private static final String TESTERS_HELP = "The tester programs, such "
            + "as Q1aTester.java, that run each student's answer and award "
            + "the marks, with the data files they read. They come with "
            + "the assessment.";

    // What the starter files are
    private static final String STARTER_HELP = "The folder students were "
            + "given before the test, such as RenameToYourUsername. It is "
            + "used to spot untouched answers and to supply provided class "
            + "files such as DataException.class.";

    // The size of the icon in the drop area
    private static final double DROP_ICON_SIZE = 20.0;

    // The size of a file icon in a list row
    private static final double ROW_ICON_SIZE = 16.0;

    // The width of the Remove and Change links, so they line up
    private static final double ROW_LINK_WIDTH = 52.0;

    // The share of the class row for Module and for Section
    private static final double CHOICE_PERCENT = 27.8;

    // The share of the class row for the assessment
    private static final double ASSESSMENT_PERCENT = 44.4;

    // How many facts the settings card shows
    private static final int FACT_COUNT = 4;

    // The share of the facts row for each fact
    private static final double FACT_PERCENT = 100.0 / FACT_COUNT;

    // The scrolling page
    private final ScrollPane view;

    // The module choice
    private final ComboBox<String> moduleBox = new ComboBox<>();

    // The section choice
    private final ComboBox<String> sectionBox = new ComboBox<>();

    // The assessment choice, which switches the assessment in use
    private final ComboBox<String> assessmentBox = new ComboBox<>();

    // The questions and marks of the assessment in use
    private final Label assessmentText = Parts.muted("");

    // A note when the chosen section was graded before
    private final HBox earlierRun = Parts.row(Parts.GAP_SMALL);

    // The drop area
    private final HBox dropArea = Parts.row(Parts.GAP);

    // How many files there are and how many are ready
    private final HBox fileSummary = Parts.row(Parts.GAP);

    // The chosen zip files
    private final VBox fileList = new VBox();

    // The students found in more than one chosen file or folder
    private final Label duplicateNote = Parts.muted("");

    // The score sheet, tester and starter rows
    private final VBox classFiles = new VBox();

    // The four facts of the grading settings
    private final GridPane facts = new GridPane();

    // The line about the checks that flag students
    private final Label checksLine = new Label();

    // Reads files dropped on the drop area
    private final DragDropHandler dropHandler = new DragDropHandler();

    // True once the class files were filled from the settings
    private boolean filledDefaults = false;

    // True when the assessment changed since the tester and starter rows
    // were filled, so they are filled again from its folders
    private boolean assessmentChanged = false;

    // True while the choices are being filled, so no change is handled
    private boolean filling = false;

    // How many chosen files are still being checked in the background
    private int filesBeingChecked = 0;

    // The tester folder check still running, or null
    private TesterCheck testerCheck = null;

    // The tester and starter folders the problems below were found with,
    // or null when they must be checked again
    private String checkedFolders = null;

    // The problems found in that tester folder
    private final List<String> testerProblems = new ArrayList<>();

    /**
     * Creates the Set up step.
     *
     * @param window the window
     */
    public SetUpScreen(FlowWindow window) {
        super(window);
        Node header = Parts.header("Set up a grading run", "Choose the "
                + "class, add the students' zip files, then start grading.");
        view = Parts.narrowPage(new Node[] {header, createClassCard(),
            createSubmissionCard(),
            Parts.card("Class files", new Node[] {classFiles}),
            createSettingsCard()});
    }

    /**
     * Returns the Set up step built in the constructor.
     */
    @Override
    public Node getView() {
        return view;
    }

    /**
     * Fills the module and section choices, the first time also the
     * configured files, then refreshes the checks and the action bar.
     */
    @Override
    public void onShow() {
        fillChoices();
        if (!filledDefaults) {
            fillClassFiles();
            filledDefaults = true;
        }
        if (assessmentChanged) {
            refillAssessmentFolders();
            assessmentChanged = false;
        }
        refresh();
    }

    /**
     * Notes that another assessment is in use, so its tester and starter
     * folders replace the ones chosen for the last one when this step is
     * next shown. Typed test cases are for the last one's questions, so
     * they are dropped.
     */
    public void assessmentChanged() {
        assessmentChanged = true;
    }

    /**
     * Opens the sheet where the instructor types test cases. Does nothing
     * when typed test cases cannot be used.
     */
    public void openTestCases() {
        if (!canTypeTestCases()) {
            return;
        }
        getWindow().showSheet(TestCasesSheet.create(getWindow(),
                () -> refresh()));
    }

    // ------------------------------------------------------------ build

    // Builds the module, section and assessment row.
    private VBox createClassCard() {
        moduleBox.setMaxWidth(Double.MAX_VALUE);
        sectionBox.setMaxWidth(Double.MAX_VALUE);
        assessmentBox.setMaxWidth(Double.MAX_VALUE);
        moduleBox.setOnAction(event -> chooseModule());
        sectionBox.setOnAction(event -> chooseSection());
        assessmentBox.setOnAction(event -> chooseAssessment());
        assessmentText.getStyleClass().add("small-text");

        GridPane fields = new GridPane();
        fields.setHgap(Parts.GAP);
        fields.getColumnConstraints().addAll(column(CHOICE_PERCENT),
                column(CHOICE_PERCENT), column(ASSESSMENT_PERCENT));
        fields.add(field("Module", moduleBox, "The course code, such as "
                + "CS102. Add modules in Settings, under Course."), 0, 0);
        fields.add(field("Section", sectionBox, "The class group these zip "
                + "files belong to, such as G1. Results are kept per "
                + "section. Add sections on Home."), 1, 0);
        HBox manage = Parts.row(Parts.GAP_SMALL, new Node[] {assessmentText,
            Parts.grow(), smallLink("Manage assessments",
                    () -> getWindow().openAssessments())});
        VBox assessmentField = field("Assessment", assessmentBox, "The lab "
                + "test or exam being graded: its questions, marks, tester "
                + "files and starter files. Choose another, or add one with "
                + "Manage assessments.");
        assessmentField.getChildren().add(manage);
        fields.add(assessmentField, 2, 0);
        return Parts.card("Class", new Node[] {fields, earlierRun});
    }

    // Builds the drop area and the file list.
    private VBox createSubmissionCard() {
        StackPane circle = new StackPane(Parts.icon(Parts.UPLOAD,
                DROP_ICON_SIZE));
        circle.getStyleClass().add("drop-icon");
        Label choose = Parts.muted("or ");
        choose.getStyleClass().add("small-text");
        Hyperlink chooseLink = Parts.link("choose files",
                () -> chooseSubmissions());
        chooseLink.getStyleClass().add("small-text");
        VBox words = new VBox(Parts.strong("Drop zip files or a folder "
                + "here"), Parts.row(0, new Node[] {choose, chooseLink}));
        words.setAlignment(Pos.CENTER_LEFT);
        dropArea.getChildren().addAll(circle, words);
        dropArea.getStyleClass().add("drop-area");
        dropArea.setAlignment(Pos.CENTER);
        dropArea.setFillHeight(false);
        dropHandler.install(dropArea);
        dropHandler.setOnDropped(() -> addDroppedFiles());
        Parts.tipNode(dropArea, "Students' zip files, such as "
                + "2024-2025-alice.tan.2024.zip, a folder that holds them, "
                + "or the bulk download with every student's zip inside. "
                + "Nothing is unpacked until you start grading.");

        fileList.getStyleClass().add("file-list");
        duplicateNote.getStyleClass().add("small-text");
        duplicateNote.setWrapText(true);
        HBox top = Parts.row(Parts.GAP, new Node[] {
                Parts.title("Submissions"), Parts.grow(), fileSummary});
        VBox card = Parts.card(null, new Node[] {top, dropArea, fileList,
            duplicateNote});
        return card;
    }

    // Builds the card that shows the settings this run will use.
    private VBox createSettingsCard() {
        facts.getStyleClass().add("facts");
        for (int index = 0; index < FACT_COUNT; index++) {
            facts.getColumnConstraints().add(column(FACT_PERCENT));
        }
        checksLine.getStyleClass().addAll("muted", "small-text");
        checksLine.setWrapText(true);
        Label title = Parts.title("Grading settings");
        Parts.tip(title, "How this run will grade. They are the same for "
                + "every student and are changed in Settings.");
        Node grow = Parts.grow();
        Hyperlink change = smallLink("Change in Settings",
                () -> getWindow().showPage(Page.SETTINGS));
        HBox top = Parts.row(Parts.GAP,
                new Node[] {title, grow, change});
        return Parts.card(null, new Node[] {top, facts, checksLine});
    }

    // Returns a column that takes a share of a grid's width.
    private ColumnConstraints column(double percent) {
        ColumnConstraints constraints = new ColumnConstraints();
        constraints.setPercentWidth(percent);
        constraints.setHgrow(Priority.ALWAYS);
        constraints.setFillWidth(true);
        constraints.setHalignment(HPos.LEFT);
        return constraints;
    }

    // Puts a small label and its "?" above a control.
    private VBox field(String label, Node control, String help) {
        Label name = Parts.muted(label);
        name.getStyleClass().add("field-label");
        VBox box = new VBox(Parts.GAP_SMALL / 2,
                Parts.labelWithInfo(name, help), control);
        box.setMaxWidth(Double.MAX_VALUE);
        return box;
    }

    // Returns a small blue link.
    private Hyperlink smallLink(String text, Runnable action) {
        Hyperlink link = Parts.link(text, action);
        link.getStyleClass().add("small-text");
        return link;
    }

    // Returns a link of fixed width with its text on the right, so the
    // links of a list line up.
    private Hyperlink rowLink(String text, Runnable action) {
        Hyperlink link = smallLink(text, action);
        link.setMinWidth(ROW_LINK_WIDTH);
        link.setPrefWidth(ROW_LINK_WIDTH);
        link.setAlignment(Pos.CENTER_RIGHT);
        return link;
    }

    // Puts an icon in the fixed 20-pixel icon column.
    private StackPane iconColumn(String path) {
        StackPane box = new StackPane(Parts.icon(path, ROW_ICON_SIZE));
        box.getStyleClass().add("icon-column");
        return box;
    }

    // ---------------------------------------------------------- choices

    // Fills the module and section boxes from the backend.
    private void fillChoices() {
        filling = true;
        if (getState().getSectionCode().isEmpty()) {
            startOnNewestRun();
        }
        List<String> modules = getBackend().getModuleCodes();
        moduleBox.getItems().setAll(modules);
        String module = getState().getModuleCode();
        if (!modules.contains(module) && !modules.isEmpty()) {
            module = modules.get(0);
        }
        moduleBox.setValue(module);
        getState().setModuleCode(module);
        fillSections();
        fillAssessments();
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

    // Starts on the module and section of the newest graded run, so the
    // results shown first are the latest ones. listRuns is newest first.
    private void startOnNewestRun() {
        if (!getBackend().supports(Capability.HISTORY)) {
            return;
        }
        for (RunRecord record : getBackend().listRuns()) {
            GradingRun run = record.getRun();
            if (run.getSection() != null
                    && StatisticsScreen.isGraded(run)) {
                getState().setModuleCode(run.getSection().getModuleCode());
                getState().setSectionCode(
                        run.getSection().getSectionCode());
                return;
            }
        }
    }

    // Fills the assessment choice with the saved assessments.
    private void fillAssessments() {
        List<String> names = new ArrayList<>();
        for (SavedAssessment saved : getBackend().listAssessments()) {
            names.add(saved.getName());
        }
        assessmentBox.getItems().setAll(names);
        Assessment assessment = getBackend().getAssessment();
        if (assessment != null) {
            assessmentBox.setValue(assessment.getName());
        }
        assessmentBox.setDisable(getState().isGrading());
    }

    // Switches the assessment in use.
    private void chooseAssessment() {
        String chosen = assessmentBox.getValue();
        Assessment inUse = getBackend().getAssessment();
        if (filling || chosen == null
                || (inUse != null && chosen.equals(inUse.getName()))) {
            return;
        }
        if (!getWindow().useAssessment(chosen)) {
            filling = true;
            fillAssessments();
            filling = false;
        }
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

    // Checks each path and adds it to the batch. Live mode opens the
    // files on a background thread; the demo reads no files.
    private void addSubmissions(List<Path> paths) {
        if (paths.isEmpty()) {
            return;
        }
        if (!getBackend().isDemo()) {
            startInspection(paths);
            return;
        }
        List<ImportItem> items = new ArrayList<>();
        for (Path path : paths) {
            items.add(getBackend().inspect(InputKind.SUBMISSION, path));
        }
        addInspected(items);
    }

    // Starts checking files on a background thread.
    private void startInspection(List<Path> paths) {
        FileInspection inspection = new FileInspection(getBackend(), paths);
        inspection.setOnSucceeded(event -> inspectionDone(inspection));
        inspection.setOnFailed(event -> inspectionFailed(inspection));
        filesBeingChecked += inspection.getPathCount();
        refreshActions();
        Thread worker = new Thread(inspection, "gradezzy-inspect");
        worker.setDaemon(true);
        worker.start();
    }

    // Adds the files a background check has finished.
    private void inspectionDone(FileInspection inspection) {
        filesBeingChecked -= inspection.getPathCount();
        addInspected(inspection.getValue());
    }

    // Says that a background check stopped before it finished.
    private void inspectionFailed(FileInspection inspection) {
        filesBeingChecked -= inspection.getPathCount();
        refreshActions();
        getWindow().notify("Could not check the chosen files: "
                + inspection.getException().getMessage());
    }

    // Adds checked files to the batch and says how many were added.
    private void addInspected(List<ImportItem> items) {
        ImportBatch batch = getState().getBatch();
        for (ImportItem item : items) {
            batch.add(item);
        }
        refresh();
        getWindow().notify("Added " + Parts.plural(items.size(), "file")
                + ".");
    }

    /**
     * Adds the files just dropped on the drop area.
     */
    private void addDroppedFiles() {
        addSubmissions(dropHandler.getDroppedPaths());
    }

    // Removes one zip file from the batch.
    private void removeSubmission(ImportItem item) {
        getState().getBatch().remove(item);
        refresh();
    }

    // Removes the zip files that cannot be used, so the rest can be
    // graded. Each one is named in the notice.
    private void removeUnusable() {
        ImportBatch batch = getState().getBatch();
        List<String> names = new ArrayList<>();
        for (ImportItem item : batch.getItems(InputKind.SUBMISSION)) {
            if (batch.getEffectiveState(item) == ImportState.INVALID) {
                batch.remove(item);
                names.add(item.getName());
            }
        }
        refresh();
        if (!names.isEmpty()) {
            getWindow().notify("Removed " + join(names) + ".");
        }
    }

    // Counts the zip files that cannot be used.
    private int countUnusable() {
        ImportBatch batch = getState().getBatch();
        int count = 0;
        for (ImportItem item : batch.getItems(InputKind.SUBMISSION)) {
            if (batch.getEffectiveState(item) == ImportState.INVALID) {
                count++;
            }
        }
        return count;
    }

    // Fills the tester and starter rows from the assessment in use, after
    // it changed. Typed test cases are for the last one's questions.
    private void refillAssessmentFolders() {
        ImportBatch batch = getState().getBatch();
        batch.clear(InputKind.TESTERS);
        batch.clear(InputKind.STARTER);
        batch.getTypedTestCases().clear();
        batch.setUsingTypedTestCases(false);
        addDefault(InputKind.TESTERS);
        addDefault(InputKind.STARTER);
    }

    // Removes every zip file from the batch.
    private void clearSubmissions() {
        getState().getBatch().clear(InputKind.SUBMISSION);
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
            inspectClassFile(kind, path);
        }
    }

    // Asks for a new score sheet, tester or starter folder.
    private void changeClassFile(InputKind kind) {
        Path path = chooseClassFile(kind);
        if (path == null) {
            return;
        }
        if (kind == InputKind.TESTERS) {
            getState().getBatch().setUsingTypedTestCases(false);
        }
        inspectClassFile(kind, path);
        refresh();
    }

    // Checks a score sheet, tester or starter folder and puts it in the
    // batch. Live mode reads it on a background thread; the demo reads no
    // files.
    private void inspectClassFile(InputKind kind, Path path) {
        if (getBackend().isDemo()) {
            useClassFile(getBackend().inspect(kind, path));
            return;
        }
        List<Path> paths = new ArrayList<>();
        paths.add(path);
        FileInspection inspection = new FileInspection(getBackend(), kind,
                paths);
        inspection.setOnSucceeded(event -> classFileInspected(inspection));
        inspection.setOnFailed(event -> inspectionFailed(inspection));
        filesBeingChecked += inspection.getPathCount();
        Thread worker = new Thread(inspection, "gradezzy-inspect");
        worker.setDaemon(true);
        worker.start();
    }

    // Uses the class file a background check has finished.
    private void classFileInspected(FileInspection inspection) {
        filesBeingChecked -= inspection.getPathCount();
        for (ImportItem item : inspection.getValue()) {
            useClassFile(item);
        }
        refresh();
    }

    // Replaces the batch's file of the item's kind with the item. A new
    // tester or starter folder is checked again for tester problems.
    private void useClassFile(ImportItem item) {
        ImportBatch batch = getState().getBatch();
        batch.clear(item.getKind());
        batch.add(item);
        if (item.getKind() == InputKind.TESTERS
                || item.getKind() == InputKind.STARTER) {
            forgetTesterCheck();
        }
    }

    // Asks for a file or folder of a kind, or returns the demo sample.
    private Path chooseClassFile(InputKind kind) {
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
            File file = chooser.showOpenDialog(getWindow().getWindow());
            if (file == null) {
                return null;
            }
            return file.toPath();
        }
        DirectoryChooser chooser = new DirectoryChooser();
        chooser.setTitle("Choose the " + kind.getTitle().toLowerCase()
                + " folder");
        File folder = chooser.showDialog(getWindow().getWindow());
        if (folder == null) {
            return null;
        }
        return folder.toPath();
    }

    // Says whether the instructor may type test cases. Live mode needs
    // the tester generator to be finished, not only declared.
    private boolean canTypeTestCases() {
        Capability typed = Capability.TYPED_TEST_CASES;
        if (!getBackend().supports(typed)) {
            return false;
        }
        return getBackend().isDemo()
                || typed.getStatus() == CapabilityStatus.WORKS_NOW;
    }

    // ---------------------------------------------------------- refresh

    // Redraws the lists, the settings and the action bar.
    private void refresh() {
        refreshAssessment();
        refreshEarlierRun();
        refreshFiles();
        refreshClassFiles();
        refreshSettings();
        refreshActions();
    }

    // Shows the questions and marks of the assessment in use under its
    // choice. The marks come from the chosen tester folder, so a new
    // folder updates them.
    private void refreshAssessment() {
        Assessment assessment = getBackend().getAssessment(
                findUsableTesters());
        if (assessment == null) {
            assessmentText.setText("None is set yet.");
            return;
        }
        assessmentText.setText(Parts.plural(assessment.getQuestions()
                .size(), "question") + DOT
                + Parts.mark(assessment.getMaxTotal()) + " marks");
    }

    // Offers the results of an earlier run of the chosen section.
    private void refreshEarlierRun() {
        earlierRun.getChildren().clear();
        GradingRun run = getBackend().getRun(getState().getSection());
        boolean show = run != null && !run.getSubmissions().isEmpty();
        earlierRun.setVisible(show);
        earlierRun.setManaged(show);
        if (!show) {
            return;
        }
        String when = " before";
        if (run.getStartedAt() != null) {
            when = " on " + run.getStartedAt().format(WHEN);
        }
        String with = "";
        if (run.getAssessment() != null) {
            with = " with " + run.getAssessment().getName();
        }
        Label note = Parts.muted(getState().getSection().getLabel()
                + " was graded" + with + when + " ("
                + Parts.plural(run.getSubmissions().size(), "student")
                + ").");
        Parts.tip(note, "Grading again keeps these results on Runs, where "
                + "you can open or restore them.");
        earlierRun.getChildren().addAll(note, Parts.link("Open its results",
                () -> getWindow().openRun(run)));
    }

    // Redraws the zip file list and the count above it.
    private void refreshFiles() {
        ImportBatch batch = getState().getBatch();
        List<ImportItem> items = problemsFirst(batch);
        fileList.getChildren().clear();
        for (int index = 0; index < items.size(); index++) {
            HBox row = createFileRow(items.get(index));
            markRow(row, index, items.size());
            fileList.getChildren().add(row);
        }
        fileList.setVisible(!items.isEmpty());
        fileList.setManaged(!items.isEmpty());
        refreshDuplicateNote(batch);

        fileSummary.getChildren().clear();
        if (items.isEmpty()) {
            return;
        }
        Label count = Parts.muted(Parts.plural(items.size(), "file")
                + DOT + Parts.plural(batch.countStudents(), "student")
                + " ready");
        count.getStyleClass().add("small-text");
        fileSummary.getChildren().add(count);
        int unusable = countUnusable();
        if (unusable > 0) {
            Hyperlink drop = smallLink("Remove " + Parts.plural(unusable,
                    "file") + " that cannot be used",
                    () -> removeUnusable());
            fileSummary.getChildren().add(drop);
        }
        fileSummary.getChildren().add(smallLink("Clear all",
                () -> clearSubmissions()));
    }

    // Lists the zip files with the ones that cannot be used first, then
    // warnings and duplicates, then the rest, each in the order added.
    private List<ImportItem> problemsFirst(ImportBatch batch) {
        ImportState[] order = {ImportState.INVALID, ImportState.WARNING,
            ImportState.DUPLICATE, ImportState.ACCEPTED};
        List<ImportItem> items = batch.getItems(InputKind.SUBMISSION);
        List<ImportItem> sorted = new ArrayList<>();
        for (ImportState state : order) {
            for (ImportItem item : items) {
                if (batch.getEffectiveState(item) == state) {
                    sorted.add(item);
                }
            }
        }
        return sorted;
    }

    // Names the students found in more than one chosen file or folder.
    private void refreshDuplicateNote(ImportBatch batch) {
        List<String> duplicates = batch.listDuplicateUsernames();
        duplicateNote.setVisible(!duplicates.isEmpty());
        duplicateNote.setManaged(!duplicates.isEmpty());
        if (duplicates.isEmpty()) {
            return;
        }
        duplicateNote.setText("More than one file for " + join(duplicates)
                + ". " + ONE_FILE_RULE);
    }

    // Gives a list row the hairline rules: a line between rows only.
    private void markRow(HBox row, int index, int count) {
        row.getStyleClass().add("card-row");
        if (index == 0) {
            row.getStyleClass().add("first");
        }
        if (index == count - 1) {
            row.getStyleClass().add("last");
        }
    }

    // Builds one row of the zip file list.
    private HBox createFileRow(ImportItem item) {
        ImportState state = getState().getBatch().getEffectiveState(item);
        Label name = Parts.strong(item.getName());
        name.setMinWidth(0);
        HBox line = Parts.row(0, new Node[] {name});
        String who = item.getUsername();
        if (who.isEmpty() && item.getStudentCount() > 0) {
            who = Parts.plural(item.getStudentCount(), "student");
        }
        if (!who.isEmpty()) {
            Label whoLabel = Parts.muted(DOT + who);
            whoLabel.setMinWidth(0);
            line.getChildren().add(whoLabel);
        }
        VBox text = new VBox(line);
        String note = noteFor(item, state);
        if (!note.isEmpty()) {
            Label message = Parts.muted(note);
            message.getStyleClass().add("small-text");
            message.setWrapText(true);
            text.getChildren().add(message);
        }
        text.setMinWidth(0);
        HBox.setHgrow(text, Priority.ALWAYS);
        Node icon = iconColumn(Parts.FILE);
        Label status = Parts.pill(stateText(state), toneOf(state));
        Hyperlink remove = rowLink("Remove", () -> removeSubmission(item));
        return Parts.row(Parts.GAP,
                new Node[] {icon, text, status, remove});
    }

    // Returns the note under a file: why it cannot be used, what to
    // check, or what a folder holds. A plain student zip has none.
    private String noteFor(ImportItem item, ImportState state) {
        if (state == ImportState.DUPLICATE) {
            return describeDuplicate(item);
        } else if (state == ImportState.ACCEPTED
                && !item.getUsername().isEmpty()) {
            return "";
        }
        return item.getMessage();
    }

    // Names the file graded in place of a duplicate, in the words
    // grading notes on that student.
    private String describeDuplicate(ImportItem item) {
        ImportBatch batch = getState().getBatch();
        if (batch.isChosenAgain(item)) {
            return "This was chosen more than once. It is graded once.";
        }
        return "Not graded: " + batch.findGradedInstead(item).getName()
                + " is graded instead. " + ONE_FILE_RULE;
    }

    // Redraws the score sheet, tester and starter rows.
    private void refreshClassFiles() {
        checkTestersIfChanged();
        HBox[] rows = {
            createClassFileRow(InputKind.ROSTER, "Score sheet", null),
            createTesterRow(),
            createClassFileRow(InputKind.STARTER, "Starter files", null)};
        classFiles.getChildren().clear();
        for (int index = 0; index < rows.length; index++) {
            markRow(rows[index], index, rows.length);
            classFiles.getChildren().add(rows[index]);
        }
    }

    // Returns the chosen tester folder when it can be used and typed test
    // cases are off, or null.
    private Path findUsableTesters() {
        ImportBatch batch = getState().getBatch();
        List<ImportItem> items = batch.getItems(InputKind.TESTERS);
        if (batch.isUsingTypedTestCases() || items.isEmpty()) {
            return null;
        }
        ImportItem item = items.get(0);
        if (batch.getEffectiveState(item) == ImportState.INVALID) {
            return null;
        }
        return item.getPath();
    }

    // Returns the chosen score sheet, or null to use the one in Settings.
    private Path findUsableScoreSheet() {
        ImportBatch batch = getState().getBatch();
        List<ImportItem> items = batch.getItems(InputKind.ROSTER);
        if (items.isEmpty()) {
            return null;
        }
        ImportItem item = items.get(0);
        if (batch.getEffectiveState(item) == ImportState.INVALID) {
            return null;
        }
        return item.getPath();
    }

    // Returns the chosen starter folder when it can be used, or null for
    // the one in Settings.
    private Path findUsableStarter() {
        ImportBatch batch = getState().getBatch();
        List<ImportItem> items = batch.getItems(InputKind.STARTER);
        if (items.isEmpty()) {
            return null;
        }
        ImportItem item = items.get(0);
        if (!batch.getEffectiveState(item).isUsable()) {
            return null;
        }
        return item.getPath();
    }

    // Lists the testers with no "score += <number>" line. Typed test
    // cases have no tester folder, so they have nothing to list.
    private List<String> findMarkProblems() {
        if (getState().getBatch().isUsingTypedTestCases()) {
            return new ArrayList<>();
        }
        return getBackend().checkMaxScores(findUsableTesters());
    }

    // Shows each question's maximum mark in a table and asks the
    // instructor to confirm them before grading starts. Warns when they
    // do not add up to the score sheet's denominator.
    private void confirmStart() {
        Assessment assessment = getBackend().getAssessment(
                findUsableTesters());
        double denominator = getBackend().getScoreSheetDenominator(
                findUsableScoreSheet());
        VBox content = new VBox(Parts.GAP, createMarksTable(assessment),
                createDenominatorRow(assessment.getMaxTotal(),
                denominator));
        getWindow().confirm("Are these questions and marks right?",
                content, "Start grading", () -> getWindow().startGrading());
    }

    // One row per question with its folder, file and maximum mark, then
    // the total. The maximums come from the testers.
    private PlainTable createMarksTable(Assessment assessment) {
        PlainTable table = new PlainTable(MARKS_ROW_HEIGHT);
        table.getStyleClass().add("compact");
        table.addColumn("Question", MARKS_QUESTION_WIDTH, Pos.CENTER_LEFT);
        table.addColumn("Folder", MARKS_FOLDER_WIDTH, Pos.CENTER_LEFT);
        table.addColumn("File", PlainTable.GROW, Pos.CENTER_LEFT);
        table.addColumn("Max", MARKS_MAX_WIDTH, Pos.CENTER_RIGHT);
        for (Question question : assessment.getQuestions()) {
            table.addRow(new Node[] {Parts.strong(question.getId()),
                new Label(question.getFolderName()),
                new Label(question.getFileName()),
                new Label(Parts.mark(question.getMaxScore()))});
        }
        table.addRow(new Node[] {Parts.strong("Total"), new Label(""),
            new Label(""), Parts.strong(Parts.mark(
            assessment.getMaxTotal()))});
        table.setPrefWidth(MARKS_TABLE_WIDTH);
        table.setMaxWidth(MARKS_TABLE_WIDTH);
        return table;
    }

    // The score sheet's denominator next to whether the total matches it.
    // A negative denominator means the sheet gives none.
    private VBox createDenominatorRow(double total, double denominator) {
        String value = "Not found";
        Label status = Parts.pill("Cannot check", Tone.NEUTRAL);
        String note = "The score sheet has no single denominator to check "
                + "the total against.";
        if (denominator >= 0 && denominator == total) {
            value = Parts.mark(denominator);
            status = Parts.pill("Matches", Tone.GOOD);
            note = "";
        } else if (denominator >= 0) {
            value = Parts.mark(denominator);
            status = Parts.pill("Does not match", Tone.WARN);
            note = "The questions add up to " + Parts.mark(total)
                    + ". Check the testers or the score sheet before you "
                    + "go on.";
        }

        Label name = Parts.muted("Score sheet denominator");
        HBox row = Parts.row(Parts.GAP, new Node[] {name,
            Parts.strong(value), Parts.grow(), status});
        row.setMaxWidth(MARKS_TABLE_WIDTH);
        VBox block = new VBox(Parts.GAP_SMALL, row);
        if (!note.isEmpty()) {
            Label noteLabel = Parts.muted(note);
            noteLabel.getStyleClass().add("small-text");
            noteLabel.setWrapText(true);
            noteLabel.setMaxWidth(MARKS_TABLE_WIDTH);
            block.getChildren().add(noteLabel);
        }
        return block;
    }

    // Names the tester and starter folders a check uses, so a change to
    // either starts a new check.
    private String describeFolders(Path testers, Path starter) {
        return testers + "|" + starter;
    }

    // Makes the next refresh check the testers again, even when the same
    // folders are chosen, as their files may have changed.
    private void forgetTesterCheck() {
        checkedFolders = null;
        testerCheck = null;
        testerProblems.clear();
    }

    // Starts the tester folder check when the folders are new to it.
    private void checkTestersIfChanged() {
        Path testers = findUsableTesters();
        if (testers == null) {
            return;
        }
        Path starter = findUsableStarter();
        String folders = describeFolders(testers, starter);
        if (folders.equals(checkedFolders)) {
            return;
        }
        if (testerCheck != null
                && folders.equals(testerCheck.getFolders())) {
            return;
        }
        if (getBackend().isDemo()) {
            showTesterProblems(folders, getBackend().checkTesters(testers,
                    starter));
            return;
        }
        TesterCheck check = new TesterCheck(getBackend(), testers, starter,
                folders);
        check.setOnSucceeded(event -> testerCheckDone(check));
        check.setOnFailed(event -> testerCheckFailed(check));
        testerCheck = check;
        Thread worker = new Thread(check, "gradezzy-testers");
        worker.setDaemon(true);
        worker.start();
    }

    // Shows what a finished check found, unless a newer one started.
    private void testerCheckDone(TesterCheck check) {
        if (check != testerCheck) {
            return;
        }
        testerCheck = null;
        showTesterProblems(check.getFolders(), check.getValue());
        refreshClassFiles();
    }

    // Shows that a check stopped before it finished.
    private void testerCheckFailed(TesterCheck check) {
        if (check != testerCheck) {
            return;
        }
        testerCheck = null;
        List<String> problems = new ArrayList<>();
        problems.add("The testers could not be checked: "
                + check.getException().getMessage());
        showTesterProblems(check.getFolders(), problems);
        refreshClassFiles();
    }

    // Keeps the problems found with a tester and starter folder.
    private void showTesterProblems(String folders, List<String> problems) {
        checkedFolders = folders;
        testerProblems.clear();
        testerProblems.addAll(problems);
    }

    // Adds the tester check's progress or problems to the tester row's
    // detail, and returns the pill to show instead, or null.
    private Label addTesterNotes(VBox notes) {
        Path testers = findUsableTesters();
        if (testers == null) {
            return null;
        }
        String folders = describeFolders(testers, findUsableStarter());
        if (!folders.equals(checkedFolders)) {
            notes.getChildren().add(smallNote("Checking the testers..."));
            return null;
        }
        if (testerProblems.isEmpty()) {
            return null;
        }
        for (String problem : testerProblems) {
            notes.getChildren().add(smallNote(problem));
        }
        return Parts.pill(Parts.plural(testerProblems.size(),
                "tester problem"), Tone.WARN);
    }

    // Returns a small grey note that wraps.
    private Label smallNote(String text) {
        Label label = Parts.muted(text);
        label.getStyleClass().add("small-text");
        label.setMinWidth(0);
        label.setWrapText(true);
        return label;
    }

    // Builds one class file row, with an optional link after the detail.
    private HBox createClassFileRow(InputKind kind, String title,
            Hyperlink extra) {
        List<ImportItem> items = getState().getBatch().getItems(kind);
        String detail;
        Label status;
        if (items.isEmpty() && kind.isRequired()) {
            detail = "Not chosen yet.";
            status = Parts.pill("Missing", Tone.BAD);
        } else if (items.isEmpty()) {
            detail = "Not chosen. The starter folder in Settings is used.";
            status = Parts.pill("From Settings", Tone.NEUTRAL);
        } else {
            ImportItem item = items.get(0);
            ImportState state = getState().getBatch()
                    .getEffectiveState(item);
            detail = item.getName() + DOT + item.getMessage();
            status = Parts.pill(stateText(state), toneOf(state));
        }
        String iconPath = Parts.FOLDER;
        if (kind == InputKind.ROSTER) {
            iconPath = Parts.FILE;
        }
        HBox line = detailLine(detail);
        if (extra != null) {
            line.getChildren().add(extra);
        }
        VBox details = new VBox(line);
        if (kind == InputKind.TESTERS) {
            Label warning = addTesterNotes(details);
            if (warning != null) {
                status = warning;
            }
        }
        return classFileRow(iconPath, title, details, status,
                rowLink("Change", () -> changeClassFile(kind)));
    }

    // Builds the tester row: a folder, or the typed test cases.
    private HBox createTesterRow() {
        ImportBatch batch = getState().getBatch();
        boolean canType = canTypeTestCases();
        if (!canType) {
            return createClassFileRow(InputKind.TESTERS, "Tester files",
                    null);
        }
        if (!batch.isUsingTypedTestCases()) {
            return createClassFileRow(InputKind.TESTERS, "Tester files",
                    smallLink("or type test cases", () -> openTestCases()));
        }

        int cases = batch.getTypedTestCases().size();
        HBox detail = detailLine(Parts.plural(cases, "test case")
                + " typed in the app");
        detail.getChildren().add(smallLink("Edit test cases",
                () -> openTestCases()));
        Label status;
        if (cases > 0) {
            status = Parts.pill("Ready", Tone.GOOD);
        } else {
            status = Parts.pill("Missing", Tone.BAD);
        }
        return classFileRow(Parts.CODE, "Tester files", detail, status,
                rowLink("Change", () -> changeClassFile(
                InputKind.TESTERS)));
    }

    // Returns the grey line under a class file's name. It wraps, so a
    // long note about a folder is read in full.
    private HBox detailLine(String text) {
        Label label = Parts.muted(text);
        label.getStyleClass().add("small-text");
        label.setMinWidth(0);
        label.setWrapText(true);
        return Parts.row(Parts.GAP_SMALL, new Node[] {label});
    }

    // Lays out a class file row: icon, name and detail, status, link.
    private HBox classFileRow(String iconPath, String title, Node detail,
            Label status, Hyperlink change) {
        String help = STARTER_HELP;
        if (title.equals("Score sheet")) {
            help = ROSTER_HELP;
        } else if (title.equals("Tester files")) {
            help = TESTERS_HELP;
        }
        VBox text = new VBox(Parts.labelWithInfo(Parts.strong(title), help),
                detail);
        text.setMinWidth(0);
        HBox.setHgrow(text, Priority.ALWAYS);
        return Parts.row(Parts.GAP,
                new Node[] {iconColumn(iconPath), text, status, change});
    }

    // Fills the four facts and the line about the checks.
    private void refreshSettings() {
        facts.getChildren().clear();
        String fixes = "Off";
        if (isOn(ConfigKeys.AUTO_FIX_ENABLED)) {
            fixes = "On, you approve";
        }
        String similar = "Off";
        if (isOn(ConfigKeys.PLAGIARISM_ENABLED)) {
            similar = "On";
        }
        facts.add(fact("Time limit per test",
                getBackend().getRunTimeoutSeconds() + " s", true, "A "
                + "tester that runs longer is stopped, so an endless loop "
                + "cannot hold up the batch. Cases passed before that keep "
                + "their marks."), 0, 0);
        facts.add(fact("Compile time limit",
                getBackend().getCompileTimeoutSeconds() + " s", false,
                "The longest javac may take for one question folder."),
                1, 0);
        facts.add(fact("Fix common mistakes", fixes, false, "Small "
                + "mistakes, such as a wrong folder name or a missing "
                + "wrapper folder, are fixed in a copy, never in the "
                + "original. On Review you approve or undo each fix."),
                2, 0);
        facts.add(fact("Similar code check", similar, false, "After "
                + "grading, compares answers and flags pairs that look "
                + "alike. It only flags; marks never change."), 3, 0);
        checksLine.setText(describeChecks());
    }

    // Builds one cell of the facts row, with its "?".
    private VBox fact(String name, String value, boolean first,
            String help) {
        Label key = Parts.muted(name);
        key.getStyleClass().add("small-text");
        Label text = Parts.strong(value);
        text.getStyleClass().add("fact-value");
        VBox cell = new VBox(Parts.labelWithInfo(key, help), text);
        cell.getStyleClass().add("fact");
        if (first) {
            cell.getStyleClass().add("first");
        }
        cell.setMaxWidth(Double.MAX_VALUE);
        return cell;
    }

    // Says which checks that flag students are on.
    private String describeChecks() {
        String[] keys = {ConfigKeys.DETECT_UNTOUCHED_ENABLED,
            ConfigKeys.DETECT_EMPTY_ENABLED,
            ConfigKeys.DETECT_HARD_CODED_ENABLED};
        String[] words = {"untouched", "empty", "hard-coded"};
        List<String> on = new ArrayList<>();
        List<String> off = new ArrayList<>();
        for (int index = 0; index < keys.length; index++) {
            if (isOn(keys[index])) {
                on.add(words[index]);
            } else {
                off.add(words[index]);
            }
        }
        if (on.isEmpty()) {
            return "Checks for untouched, empty and hard-coded answers are "
                    + "off.";
        }
        String text = "Checks for " + join(on) + " answers "
                + isOrAre(on.size()) + " on";
        if (!off.isEmpty()) {
            text = text + "; " + join(off) + " " + isOrAre(off.size())
                    + " off";
        }
        return text + ". They only flag students; marks never change.";
    }

    // Returns "is" for one thing and "are" for more.
    private String isOrAre(int count) {
        if (count == 1) {
            return "is";
        }
        return "are";
    }

    // Joins words as "a", "a and b" or "a, b and c".
    private String join(List<String> words) {
        String text = "";
        for (int index = 0; index < words.size(); index++) {
            if (index > 0 && index == words.size() - 1) {
                text += " and ";
            } else if (index > 0) {
                text += ", ";
            }
            text += words.get(index);
        }
        return text;
    }

    // Says whether an on or off setting is on.
    private boolean isOn(String key) {
        String value = getBackend().getSetting(key);
        return value != null && TRUE.equalsIgnoreCase(value.trim());
    }

    // Fills the action bar.
    private void refreshActions() {
        if (!isOnScreen()) {
            return;
        }
        ImportBatch batch = getState().getBatch();
        ActionBar actions = getActions();
        actions.reset();
        // A tester with no "score += <number>" line has no maximum mark,
        // so grading cannot start until it is fixed
        List<String> markProblems = findMarkProblems();
        boolean ready = batch.isReady() && !getState().isGrading()
                && filesBeingChecked == 0 && markProblems.isEmpty();
        actions.setPrimary("Start grading", () -> confirmStart(), ready);
        if (getState().isGrading()) {
            actions.setStatus("A batch is being graded. See step 2.");
        } else if (filesBeingChecked > 0) {
            actions.setStatus("Checking " + Parts.plural(filesBeingChecked,
                    "file") + "...");
        } else if (!markProblems.isEmpty()) {
            actions.setStatus(markProblems.get(0));
        } else if (!batch.isReady()) {
            List<String> reasons = batch.getBlockingReasons();
            if (reasons.isEmpty()) {
                actions.setStatus("");
            } else {
                actions.setStatus(reasons.get(0));
            }
        } else {
            String skipped = batch.describeSkipped();
            if (!skipped.isEmpty()) {
                skipped = " " + skipped;
            }
            actions.setStatus(Parts.plural(batch.countStudents(),
                    "student") + " ready to grade in "
                    + getState().getSection().getLabel() + "." + skipped);
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
