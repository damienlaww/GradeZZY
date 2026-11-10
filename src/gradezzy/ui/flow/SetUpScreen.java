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
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.time.format.DateTimeFormatter;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;

import javafx.geometry.HPos;
import javafx.geometry.Insets;
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
import javafx.scene.layout.Region;
import javafx.scene.layout.StackPane;
import javafx.scene.layout.VBox;
import javafx.scene.text.Text;
import javafx.scene.text.TextFlow;
import javafx.stage.DirectoryChooser;
import javafx.stage.FileChooser;

import gradezzy.config.ConfigKeys;
import gradezzy.config.ExportFolders;
import gradezzy.model.Assessment;
import gradezzy.model.GradingRun;
import gradezzy.model.Question;
import gradezzy.model.SavedAssessment;
import gradezzy.model.Submission;
import gradezzy.service.ChosenSubmissionsStore;
import gradezzy.service.ClassFileMatch;
import gradezzy.ui.DragDropHandler;
import gradezzy.ui.backend.ActionResult;
import gradezzy.ui.backend.Capability;
import gradezzy.ui.backend.CapabilityStatus;
import gradezzy.ui.backend.ImportBatch;
import gradezzy.ui.backend.ImportItem;
import gradezzy.ui.backend.ImportState;
import gradezzy.ui.backend.InputKind;
import gradezzy.ui.backend.RunRecord;
import gradezzy.util.TextUtils;

/**
 * The Set up step.
 *
 * <p>Tier: SHOULD.
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

    // The width of a row's icon column, as set in flow.css .icon-column
    private static final double ROW_ICON_COLUMN = 20.0;

    // The space between a row and its opened file list
    private static final double FILE_LIST_GAP = 8.0;

    // The end of a zip file's name
    private static final String ZIP_EXTENSION = ".zip";

    // Where the chosen files are kept when the setting is missing
    private static final String DEFAULT_CHOSEN_FILE =
            "workspace/chosen-submissions.properties";

    // The most submissions listed on the page; Show all opens the rest
    private static final int MAX_FILE_ROWS = 5;

    // The share of the class row for Module and for Section
    private static final double CHOICE_PERCENT = 27.8;

    // The share of the class row for the assessment
    private static final double ASSESSMENT_PERCENT = 44.4;

    // The column of the class row the assessment sits in, after Module
    // and Section
    private static final int ASSESSMENT_COLUMN = 2;

    // How many facts the settings card shows
    private static final int FACT_COUNT = 4;

    // The share of the facts row for each fact
    private static final double FACT_PERCENT = 100.0 / FACT_COUNT;

    // The columns of the facts row for Fix common mistakes and for
    // Similar code check, after the two time limits
    private static final int FIXES_FACT_COLUMN = 2;
    private static final int SIMILAR_FACT_COLUMN = 3;

    // Where each class file's result sits in the match check's list
    private static final int ROSTER_MATCH = 0;
    private static final int TESTERS_MATCH = 1;
    private static final int STARTER_MATCH = 2;

    // The indent of a row's opened file list, past the icon column
    private static final double FILE_LIST_INDENT = ROW_ICON_COLUMN
            + Parts.GAP;

    // The separator between the parts of a key that names the files a
    // check used
    private static final String KEY_SEPARATOR = "|";

    // The separator between a file's name and its students in the text
    // the sheet's filter searches
    private static final String SEARCH_SEPARATOR = " ";

    // The separator between words joined in a sentence, and before the
    // last of them
    private static final String WORD_SEPARATOR = ", ";
    private static final String LAST_WORD_SEPARATOR = " and ";

    // The titles of the class file rows, which also pick each row's help
    private static final String ROSTER_TITLE = "Score sheet";
    private static final String TESTERS_TITLE = "Tester files";
    private static final String STARTER_TITLE = "Starter files";

    // The names of the background threads that check files
    private static final String INSPECT_THREAD = "gradezzy-inspect";
    private static final String REOPEN_THREAD = "gradezzy-reopen";
    private static final String TESTERS_THREAD = "gradezzy-testers";
    private static final String MATCH_THREAD = "gradezzy-match";

    // Keeps the files chosen for each section between uses, or null
    // until first needed
    private ChosenSubmissionsStore chosenStore = null;

    // The sheet that lists every chosen submission, made when first
    // opened
    private SubmissionsSheet allFilesSheet = null;

    // The sections opened on Set up since GradeZZY started, so a
    // section gets its last run's files back only on the first visit
    private final List<String> visitedSections = new ArrayList<>();

    // The submissions whose file list is open
    private final List<Path> openFileLists = new ArrayList<>();

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

    // The section whose score sheet the batch holds
    private String scoreSheetSection = "";

    // True when the assessment changed since the tester and starter rows
    // were filled, so they are filled again from its folders
    private boolean assessmentChanged = false;

    // True while the choices are being filled, so no change is handled
    private boolean filling = false;

    // How many chosen files are still being checked in the background
    private int filesBeingChecked = 0;

    // The newest check of each kind of class file, so an older pick that
    // finishes later is ignored
    private final Map<InputKind, FileInspection> latestClassChecks =
            new HashMap<>();

    // The tester folder check still running, or null
    private TesterCheck testerCheck = null;

    // The tester and starter folders the problems below were found with,
    // or null when they must be checked again
    private String checkedFolders = null;

    // The problems found in that tester folder
    private final List<String> testerProblems = new ArrayList<>();

    // The check of the class files against the uploads still running,
    // or null
    private ClassFileMatchCheck matchCheck = null;

    // The uploads and class files the matches below were found with, or
    // null when they must be checked again
    private String matchedFiles = null;

    // Whether the score sheet, tester and starter folder belong with the
    // uploads, in that order
    private final List<ClassFileMatch> matches = new ArrayList<>();

    // The marks read from the testers, kept so the tester files are read
    // again only when the folder or the assessment in use changes, not on
    // every redraw. markedTesters and markedBase say what they were read
    // for; markedAssessment is null until the first read.
    private Assessment markedAssessment = null;
    private final List<String> markProblems = new ArrayList<>();
    private Path markedTesters = null;
    private Assessment markedBase = null;
    private boolean markedTyped = false;

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
            scoreSheetSection = sectionLabel();
        }
        // Opened for another section, such as from a card on Home: that
        // section's score sheet replaces the last one's
        if (!sectionLabel().equals(scoreSheetSection)) {
            useSectionScoreSheet();
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
     * Adds students' zip files, as a drop on the drop area would, for
     * files dropped on a section card on Home.
     *
     * @param paths the files or folders
     */
    public void addFiles(List<Path> paths) {
        addSubmissions(paths);
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
        fields.add(assessmentField, ASSESSMENT_COLUMN, 0);
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
        Hyperlink change = smallLink("Change",
                () -> openGradingSettings());
        HBox top = Parts.row(Parts.GAP,
                new Node[] {title, grow, change});
        return Parts.card(null, new Node[] {top, facts, checksLine});
    }

    // Opens the grading settings sheet, then shows any change here.
    private void openGradingSettings() {
        GradingSettingsSheet.open(getWindow(), () -> refresh());
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
        VBox box = new VBox(Parts.GAP_TINY,
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
            boolean graded = run.getSection() != null
                    && StatisticsScreen.isGraded(run);
            if (graded) {
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
        // The batch being graded keeps its module and section
        moduleBox.setDisable(getState().isGrading());
        sectionBox.setDisable(getState().isGrading());
    }

    // Switches the assessment in use.
    private void chooseAssessment() {
        String chosen = assessmentBox.getValue();
        Assessment inUse = getBackend().getAssessment();
        if (filling || chosen == null) {
            return;
        }
        boolean alreadyInUse = inUse != null
                && chosen.equals(inUse.getName());
        if (alreadyInUse) {
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
        useSectionScoreSheet();
        refresh();
    }

    // Handles a new section.
    private void chooseSection() {
        if (filling || sectionBox.getValue() == null) {
            return;
        }
        getState().setSectionCode(sectionBox.getValue());
        useSectionScoreSheet();
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
        Choosers.prepare(chooser);
        List<File> files = chooser.showOpenMultipleDialog(
                getWindow().getWindow());
        if (files == null || files.isEmpty()) {
            return;
        }
        Choosers.remember(files.get(0));
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

    // ------------------------------------------- files kept between uses

    // Returns the store of the files chosen for each section, or null in
    // the demo, which keeps nothing.
    private ChosenSubmissionsStore chosenStore() {
        if (getBackend().isDemo()) {
            return null;
        }

        if (chosenStore == null) {
            String file = getBackend().getSetting(
                    ConfigKeys.CHOSEN_SUBMISSIONS_FILE);
            if (file.trim().isEmpty()) {
                file = DEFAULT_CHOSEN_FILE;
            }
            chosenStore = new ChosenSubmissionsStore(Paths.get(file));
            getState().setSavedPaths(chosenStore.load());
        }

        return chosenStore;
    }

    // Saves the files chosen for each section, so they are listed again
    // after GradeZZY is reopened.
    private void saveChosenFiles() {
        ChosenSubmissionsStore store = chosenStore();
        if (store != null) {
            store.save(getState().listChosenPaths());
        }
    }

    // Checks again the files saved for this section when GradeZZY was
    // last closed, and lists the ones still there. They are added to the
    // section they were chosen for, even if another is open by the time
    // the check ends.
    private void bringBackSavedFiles() {
        if (chosenStore() == null || getState().isGrading()) {
            return;
        }

        String key = getState().getSectionKey();
        boolean firstVisit = !visitedSections.contains(key);
        if (firstVisit) {
            visitedSections.add(key);
        }
        boolean kept = getState().hasKeptFiles();

        List<Path> paths = new ArrayList<>();
        for (Path path : getState().takeSavedPaths()) {
            if (Files.exists(path)) {
                paths.add(path);
            }
        }
        // A section graded before its files were kept gets the files of
        // its last run, once, when it is first opened
        boolean empty = getState().getBatch().getItems(
                InputKind.SUBMISSION).isEmpty();
        boolean useLastRun = paths.isEmpty() && firstVisit && !kept
                && empty;
        if (useLastRun) {
            paths = findLastRunUploads();
        }
        if (paths.isEmpty()) {
            return;
        }

        String label = sectionLabel();
        FileInspection inspection = new FileInspection(getBackend(), paths);
        inspection.setOnSucceeded(event -> savedFilesChecked(inspection,
                key, label));
        inspection.setOnFailed(event -> inspectionFailed(inspection));
        filesBeingChecked += inspection.getPathCount();

        Thread worker = new Thread(inspection, REOPEN_THREAD);
        worker.setDaemon(true);
        worker.start();
    }

    // Returns the uploads the section's last run graded that are still
    // there. Copies GradeZZY unpacked from a zip of student zips are
    // left out. When the uploads all sit in one folder that was chosen
    // as a whole, which is so when some came from a zip of student zips
    // in it or it holds no other zip files, that folder is returned as
    // the one upload.
    private List<Path> findLastRunUploads() {
        List<Path> uploads = new ArrayList<>();
        GradingRun run = getBackend().getRun(getState().getSection());
        if (run == null) {
            return uploads;
        }

        String workingSetting = getBackend().getSetting(
                ConfigKeys.WORKING_DIR);
        Path working = Paths.get(workingSetting).toAbsolutePath()
                .normalize();
        boolean fromBulkZip = false;
        for (Submission submission : run.getSubmissions()) {
            Path original = submission.getOriginalPath();
            boolean known = original != null;
            if (known && isInside(original, working)) {
                fromBulkZip = true;
            } else if (known && isNewUpload(original, uploads)) {
                uploads.add(original);
            }
        }

        Path folder = findSharedFolder(uploads);
        boolean chosenAsWhole = folder != null && uploads.size() > 1
                && (fromBulkZip || !holdsOtherZips(folder, uploads));
        if (chosenAsWhole) {
            List<Path> whole = new ArrayList<>();
            whole.add(folder);
            return whole;
        }

        return uploads;
    }

    // Says whether a path is inside a folder, such as a copy GradeZZY
    // unpacked into its working folder.
    private boolean isInside(Path path, Path folder) {
        return path.toAbsolutePath().normalize().startsWith(folder);
    }

    // Says whether an upload is still there and not listed yet.
    private boolean isNewUpload(Path upload, List<Path> uploads) {
        return Files.exists(upload) && !uploads.contains(upload);
    }

    // Returns the folder every path is directly in, or null when they
    // are in different folders or there are none.
    private Path findSharedFolder(List<Path> paths) {
        if (paths.isEmpty()) {
            return null;
        }

        Path folder = paths.get(0).getParent();
        for (Path path : paths) {
            if (folder == null || !folder.equals(path.getParent())) {
                return null;
            }
        }

        return folder;
    }

    // Says whether a folder holds zip files other than the uploads, as a
    // downloads folder would, so it was not chosen as a whole.
    private boolean holdsOtherZips(Path folder, List<Path> uploads) {
        File[] children = folder.toFile().listFiles();
        if (children == null) {
            return true;
        }

        for (File child : children) {
            boolean zip = child.getName().toLowerCase(Locale.ROOT)
                    .endsWith(ZIP_EXTENSION);
            if (zip && !uploads.contains(child.toPath())) {
                return true;
            }
        }

        return false;
    }

    // Lists the saved files once checked again.
    private void savedFilesChecked(FileInspection inspection, String key,
            String label) {
        filesBeingChecked -= inspection.getPathCount();
        List<ImportItem> items = inspection.getValue();
        getState().addSubmissionsTo(key, items);
        refresh();

        getWindow().notify("Listed again the " + Parts.plural(items.size(),
                "file") + " chosen for " + label + ".");
    }

    // Starts checking files on a background thread.
    private void startInspection(List<Path> paths) {
        FileInspection inspection = new FileInspection(getBackend(), paths);
        inspection.setOnSucceeded(event -> inspectionDone(inspection));
        inspection.setOnFailed(event -> inspectionFailed(inspection));
        filesBeingChecked += inspection.getPathCount();
        refreshActions();
        Thread worker = new Thread(inspection, INSPECT_THREAD);
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
        getWindow().notify("Could not check the chosen files"
                + describeError(inspection.getException()));
    }

    // Returns ": " and an error's message, or "." when it has none.
    private String describeError(Throwable error) {
        boolean noMessage = error == null || error.getMessage() == null
                || error.getMessage().isBlank();
        if (noMessage) {
            return ". Try choosing them again.";
        }
        return ": " + error.getMessage();
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

    // Adds the files just dropped on the drop area.
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

    // Asks before removing every chosen zip file.
    private void clearSubmissions() {
        int count = getState().getBatch().getItems(
                InputKind.SUBMISSION).size();
        getWindow().confirm("Remove all " + Parts.plural(count, "file")
                + "?", "They are only taken off this list; the zip files "
                + "are not changed. You can add them again.",
                "Remove all", () -> removeAllSubmissions());
    }

    // Removes every zip file from the batch, for good.
    private void removeAllSubmissions() {
        getState().forgetSubmissions();
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
        // Each section has its own score sheet
        if (kind == InputKind.ROSTER && !getBackend().isDemo()) {
            String sheet = getBackend().getScoreSheet(getState()
                    .getSection());
            path = null;
            if (sheet != null && !sheet.trim().isEmpty()) {
                path = Paths.get(sheet.trim());
            }
        }
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
        useChosenClassFile(kind, path);
        if (kind == InputKind.ROSTER) {
            rememberScoreSheet(path);
        }
    }

    // Shows the score sheet of the section chosen now, in place of the
    // last section's.
    private void useSectionScoreSheet() {
        scoreSheetSection = sectionLabel();
        if (getBackend().isDemo()) {
            return;
        }
        getState().getBatch().clear(InputKind.ROSTER);
        addDefault(InputKind.ROSTER);
    }

    // Names the section chosen now, such as "CS102 G1", or "".
    private String sectionLabel() {
        if (getState().getSection() == null) {
            return "";
        }
        return getState().getSection().getLabel();
    }

    // Keeps a score sheet chosen on Set up as the chosen section's, so it
    // is there the next time this section is graded.
    private void rememberScoreSheet(Path path) {
        if (getBackend().isDemo() || getState().getSection() == null) {
            return;
        }
        String who = ExportFolders.keyOf(getState().getModuleCode(),
                getState().getSectionCode());
        String sheets = getBackend().getSetting(
                ConfigKeys.SCORE_SHEET_BY_SECTION);
        String updated = ExportFolders.put(sheets, who,
                Parts.configPath(path));
        Map<String, String> changes = new HashMap<>();
        changes.put(ConfigKeys.SCORE_SHEET_BY_SECTION, updated);
        ActionResult result = getBackend().saveSettings(changes);
        if (result.isDone()) {
            getWindow().notify("Score sheet kept for "
                    + getState().getSection().getLabel() + ".");
        }
    }

    // Uses a score sheet, tester or starter folder the instructor chose,
    // or one a suggestion offered instead of the chosen one.
    private void useChosenClassFile(InputKind kind, Path path) {
        if (kind == InputKind.TESTERS) {
            getState().getBatch().setUsingTypedTestCases(false);
        }
        inspectClassFile(kind, path);
        refresh();
    }

    // Checks a class file in the background, or at once in the demo.
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
        inspection.setOnFailed(event -> classFileFailed(inspection));
        latestClassChecks.put(kind, inspection);
        filesBeingChecked += inspection.getPathCount();
        Thread worker = new Thread(inspection, INSPECT_THREAD);
        worker.setDaemon(true);
        worker.start();
    }

    // Uses a finished class file check, unless a newer pick replaced it.
    private void classFileInspected(FileInspection inspection) {
        filesBeingChecked -= inspection.getPathCount();
        if (latestClassChecks.get(inspection.getKind()) == inspection) {
            latestClassChecks.remove(inspection.getKind());
            for (ImportItem item : inspection.getValue()) {
                useClassFile(item);
            }
        }
        refresh();
    }

    // Says a class file check failed, unless a newer pick replaced it.
    private void classFileFailed(FileInspection inspection) {
        if (latestClassChecks.get(inspection.getKind()) != inspection) {
            filesBeingChecked -= inspection.getPathCount();
            refreshActions();
            return;
        }
        latestClassChecks.remove(inspection.getKind());
        inspectionFailed(inspection);
        // The tester check waits for every row, so it may start now
        refreshClassFiles();
    }

    // Puts a checked class file in the batch and re-checks the testers.
    private void useClassFile(ImportItem item) {
        ImportBatch batch = getState().getBatch();
        batch.clear(item.getKind());
        batch.add(item);
        boolean assessmentFolder = item.getKind() == InputKind.TESTERS
                || item.getKind() == InputKind.STARTER;
        if (assessmentFolder) {
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
            Choosers.prepare(chooser);
            File file = chooser.showOpenDialog(getWindow().getWindow());
            Choosers.remember(file);
            if (file == null) {
                return null;
            }
            return file.toPath();
        }
        DirectoryChooser chooser = new DirectoryChooser();
        chooser.setTitle("Choose the " + kind.getTitle().toLowerCase()
                + " folder");
        Choosers.prepare(chooser);
        File folder = chooser.showDialog(getWindow().getWindow());
        Choosers.remember(folder);
        if (folder == null) {
            return null;
        }
        return folder.toPath();
    }

    // Opens the editor of the assessment in use, where its tester and
    // starter files are chosen.
    private void editAssessmentFiles() {
        Assessment inUse = getBackend().getAssessment();
        if (inUse != null) {
            getWindow().editAssessment(inUse.getName());
        } else {
            getWindow().openAssessments();
        }
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
        readMarksIfChanged();
        refreshAssessment();
        refreshEarlierRun();
        bringBackSavedFiles();
        refreshFiles();
        saveChosenFiles();
        refreshClassFiles();
        refreshSettings();
        refreshActions();
    }

    // Shows the questions and marks of the assessment in use under its
    // choice. The marks come from the chosen tester folder, so a new
    // folder updates them.
    private void refreshAssessment() {
        readMarksIfChanged();
        Assessment assessment = markedAssessment;
        if (assessment == null) {
            assessmentText.setText("None is set yet.");
            return;
        }
        int questionCount = assessment.getQuestions().size();
        assessmentText.setText(Parts.plural(questionCount, "question")
                + DOT + Parts.mark(assessment.getMaxTotal()) + " marks");
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
        int studentCount = run.getSubmissions().size();
        Label note = Parts.muted(getState().getSection().getLabel()
                + " was graded" + with + when + " ("
                + Parts.plural(studentCount, "student") + ").");
        Parts.tip(note, "Grading again keeps these results in History, where "
                + "you can open or restore them.");
        earlierRun.getChildren().addAll(note, Parts.link("Open its results",
                () -> getWindow().openRun(run)));
    }

    // Redraws the zip file list and the count above it.
    private void refreshFiles() {
        ImportBatch batch = getState().getBatch();
        List<ImportItem> items = problemsFirst(batch);
        fileList.getChildren().clear();
        // Problems come first, so they stay on the page; the rest are in
        // the sheet that Show all opens
        int shown = Math.min(items.size(), MAX_FILE_ROWS);
        for (int index = 0; index < shown; index++) {
            VBox row = createFileRow(items.get(index));
            row.getStyleClass().add("card-row");
            fileList.getChildren().add(row);
        }

        if (items.size() > shown) {
            String allText = "Show all " + Parts.plural(items.size(),
                    "file");
            Hyperlink all = smallLink(allText, () -> showAllFiles());
            HBox more = Parts.row(0, new Node[] {all});
            more.getStyleClass().add("card-row");
            fileList.getChildren().add(more);
        }
        markEnds(fileList);
        fileList.setVisible(!items.isEmpty());
        fileList.setManaged(!items.isEmpty());
        refreshDuplicateNote(batch);
        refreshAllFilesSheet(items);
        refreshFileSummary(batch, items);
    }

    // Redraws the count above the zip file list and its links.
    private void refreshFileSummary(ImportBatch batch,
            List<ImportItem> items) {
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
            String dropText = "Remove " + Parts.plural(unusable, "file")
                    + " that cannot be used";
            Hyperlink drop = smallLink(dropText, () -> removeUnusable());
            fileSummary.getChildren().add(drop);
        }
        fileSummary.getChildren().add(smallLink("Clear all",
                () -> clearSubmissions()));
    }

    // Opens the sheet listing every chosen submission.
    private void showAllFiles() {
        if (allFilesSheet == null) {
            allFilesSheet = new SubmissionsSheet(getWindow());
        }

        List<ImportItem> items = problemsFirst(getState().getBatch());
        allFilesSheet.show(createAllFileRows(items), searchTextsOf(items));
    }

    // Shows the files again in the open sheet after one was added or
    // removed, or closes it when none are left.
    private void refreshAllFilesSheet(List<ImportItem> items) {
        if (allFilesSheet == null || !allFilesSheet.isShowing()) {
            return;
        }

        if (items.isEmpty()) {
            getWindow().closeSheet();
            return;
        }

        allFilesSheet.setRows(createAllFileRows(items),
                searchTextsOf(items));
    }

    // Builds one row per file for the sheet.
    private List<Node> createAllFileRows(List<ImportItem> items) {
        List<Node> rows = new ArrayList<>();
        for (ImportItem item : items) {
            VBox row = createFileRow(item);
            row.getStyleClass().add("card-row");
            rows.add(row);
        }

        return rows;
    }

    // Returns the text each file is found by in the sheet's filter: its
    // name and the usernames of the students in it.
    private List<String> searchTextsOf(List<ImportItem> items) {
        List<String> texts = new ArrayList<>();
        for (ImportItem item : items) {
            texts.add(item.getName() + SEARCH_SEPARATOR
                    + join(item.getStudents()));
        }

        return texts;
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

    // Gives a list's rows the hairline rules: a line between rows only.
    private void markEnds(VBox list) {
        List<Node> rows = list.getChildren();
        if (rows.isEmpty()) {
            return;
        }
        int lastIndex = rows.size() - 1;
        rows.get(0).getStyleClass().add("first");
        rows.get(lastIndex).getStyleClass().add("last");
    }

    // Builds one row of the zip file list. An opened list of the files
    // inside sits under the row, so the icon, status and Remove stay put.
    private VBox createFileRow(ImportItem item) {
        ImportState state = getState().getBatch().getEffectiveState(item);
        HBox line = createNameLine(item);
        VBox text = new VBox(line);
        String note = noteFor(item, state);
        if (!note.isEmpty()) {
            Label message = Parts.muted(note);
            message.getStyleClass().add("small-text");
            message.setWrapText(true);
            text.getChildren().add(message);
        }
        Node insideList = null;
        if (!item.getFiles().isEmpty()) {
            Path path = item.getPath();
            ContainedFileList files = new ContainedFileList(item.getFiles(),
                    openFileLists.contains(path),
                    () -> toggleFileList(path));
            text.getChildren().add(files.build());
            insideList = files.getList();
        }

        text.setMinWidth(0);
        HBox.setHgrow(text, Priority.ALWAYS);
        Node icon = iconColumn(Parts.FILE);
        Label status = Parts.pill(stateText(state), toneOf(state));
        Hyperlink remove = rowLink("Remove", () -> removeSubmission(item));
        HBox main = Parts.row(Parts.GAP,
                new Node[] {icon, text, status, remove});
        VBox row = new VBox(main);
        if (insideList != null) {
            // Lines the list up with the text, past the icon column
            VBox.setMargin(insideList, new Insets(FILE_LIST_GAP, 0, 0,
                    FILE_LIST_INDENT));
            row.getChildren().add(insideList);
        }

        return row;
    }

    // Builds the first line of a zip file's row: its name, then whose it
    // is or how many students it holds.
    private HBox createNameLine(ImportItem item) {
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
        return line;
    }

    // Remembers whether a row's file list is open, so it stays that way
    // when the rows are drawn again.
    private void toggleFileList(Path path) {
        if (openFileLists.contains(path)) {
            openFileLists.remove(path);
        } else {
            openFileLists.add(path);
        }
    }

    // Returns the note under a file: why it cannot be used, what to
    // check, or what a folder holds. A plain student zip has none.
    private String noteFor(ImportItem item, ImportState state) {
        if (state == ImportState.DUPLICATE) {
            return describeDuplicate(item);
        }
        boolean plainZip = state == ImportState.ACCEPTED
                && !item.getUsername().isEmpty();
        if (plainZip) {
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
        String gradedName = batch.findGradedInstead(item).getName();
        return "Not graded: " + gradedName + " is graded instead. "
                + ONE_FILE_RULE;
    }

    // Redraws the score sheet, tester and starter rows.
    private void refreshClassFiles() {
        checkTestersIfChanged();
        checkMatchesIfChanged();
        HBox[] rows = {
            createClassFileRow(InputKind.ROSTER, ROSTER_TITLE, null),
            createTesterRow(),
            createClassFileRow(InputKind.STARTER, STARTER_TITLE, null)};
        classFiles.getChildren().clear();
        for (int index = 0; index < rows.length; index++) {
            rows[index].getStyleClass().add("card-row");
            classFiles.getChildren().add(rows[index]);
        }
        markEnds(classFiles);
    }

    // Returns the usable chosen tester folder, or null when there is none.
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

    // Returns the usable chosen starter folder, or null for Settings.
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

    // Reads the marks and their problems from the testers when the tester
    // folder, the assessment in use or the typed test cases choice has
    // changed since the last read.
    private void readMarksIfChanged() {
        Path testers = findUsableTesters();
        Assessment base = getBackend().getAssessment();
        boolean typed = getState().getBatch().isUsingTypedTestCases();
        boolean sameFolder = (testers == null && markedTesters == null)
                || (testers != null && testers.equals(markedTesters));
        boolean upToDate = markedAssessment != null && sameFolder
                && base == markedBase && typed == markedTyped;
        if (upToDate) {
            return;
        }
        readMarks();
    }

    // Reads the marks and their problems from the testers now. Both come
    // from the same folder at the same moment, so they always agree.
    private void readMarks() {
        Path testers = findUsableTesters();
        markedTesters = testers;
        markedBase = getBackend().getAssessment();
        markedTyped = getState().getBatch().isUsingTypedTestCases();
        markedAssessment = getBackend().getAssessment(testers);
        markProblems.clear();
        // Typed test cases have no tester folder, so nothing to check
        if (!markedTyped) {
            markProblems.addAll(getBackend().checkMaxScores(testers));
        }
    }

    // Lists why some questions have no maximum mark, from the last read
    private List<String> findMarkProblems() {
        return markProblems;
    }

    // Shows each question's maximum mark in a table and asks the
    // instructor to confirm them before grading starts. Warns when they
    // do not add up to the score sheet's denominator. The testers are
    // read again first, in case a file changed since Set up opened.
    private void confirmStart() {
        readMarks();
        if (!markProblems.isEmpty()) {
            refreshActions();
            return;
        }
        Assessment assessment = markedAssessment;
        double denominator = getBackend().getScoreSheetDenominator(
                findUsableScoreSheet());
        PlainTable table = createMarksTable(assessment);
        VBox denominatorRow = createDenominatorRow(assessment.getMaxTotal(),
                denominator);
        VBox content = new VBox(Parts.GAP, table, denominatorRow);
        getWindow().confirm("Are these questions and marks right?",
                content, "Start grading", () -> startOrAddToRun());
    }

    // Starts grading, first asking whether the section's run takes it.
    private void startOrAddToRun() {
        GradingRun run = null;
        if (getBackend().supports(Capability.LATE_BATCH)) {
            run = getBackend().findRunToJoin(getState().getSection());
        }
        if (run == null) {
            getWindow().startGrading();
            return;
        }
        String when = "";
        if (run.getStartedAt() != null) {
            when = " on " + run.getStartedAt().format(WHEN);
        }
        int studentCount = run.getSubmissions().size();
        String detail = getState().getSection().getLabel() + " was graded"
                + when + " (" + Parts.plural(studentCount, "student")
                + "). A student graded before is graded again, and the new "
                + "result replaces the old one.";
        GradingRun joined = run;
        getWindow().choose("Add these students to that run?", detail,
                "Start a new run", () -> getWindow().startGrading(),
                "Add to that run", () -> getWindow().addToRun(joined));
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
        String totalText = Parts.mark(assessment.getMaxTotal());
        table.addRow(new Node[] {Parts.strong("Total"), new Label(""),
            new Label(""), Parts.strong(totalText)});
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

    // Names the tester and starter folders a tester check uses.
    private String describeFolders(Path testers, Path starter) {
        return testers + KEY_SEPARATOR + starter;
    }

    // Makes the next refresh check the testers again from scratch.
    private void forgetTesterCheck() {
        checkedFolders = null;
        testerCheck = null;
        testerProblems.clear();
    }

    // Starts the tester folder check when the folders are new to it.
    private void checkTestersIfChanged() {
        Path testers = findUsableTesters();
        // Wait for every class file row, so the check starts only once
        if (testers == null || !latestClassChecks.isEmpty()) {
            return;
        }
        Path starter = findUsableStarter();
        String folders = describeFolders(testers, starter);
        if (folders.equals(checkedFolders)) {
            return;
        }
        boolean alreadyChecking = testerCheck != null
                && folders.equals(testerCheck.getFolders());
        if (alreadyChecking) {
            return;
        }
        if (getBackend().isDemo()) {
            List<String> problems = getBackend().checkTesters(testers,
                    starter);
            showTesterProblems(folders, problems);
            return;
        }
        TesterCheck check = new TesterCheck(getBackend(), testers, starter,
                folders);
        check.setOnSucceeded(event -> testerCheckDone(check));
        check.setOnFailed(event -> testerCheckFailed(check));
        testerCheck = check;
        // Not a daemon: when GradeZZY closes, Java waits for the check to
        // end, so it deletes its temporary folder
        Thread worker = new Thread(check, TESTERS_THREAD);
        worker.setDaemon(false);
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
        problems.add("The testers could not be checked"
                + describeError(check.getException()));
        showTesterProblems(check.getFolders(), problems);
        refreshClassFiles();
    }

    // Keeps the problems found with a tester and starter folder.
    private void showTesterProblems(String folders, List<String> problems) {
        checkedFolders = folders;
        testerProblems.clear();
        testerProblems.addAll(problems);
    }

    // Starts checking the class files against the uploads when either
    // changed. Until it ends, the rows show no match problem.
    private void checkMatchesIfChanged() {
        List<ImportItem> uploads = findUsableSubmissions();
        Path[] classFiles = {findChosenPath(InputKind.ROSTER),
            findUsableTesters(), findChosenPath(InputKind.STARTER)};
        String files = describeMatchFiles(uploads, classFiles);
        if (files.equals(matchedFiles)) {
            return;
        }
        if (matchCheck != null && files.equals(matchCheck.getFiles())) {
            return;
        }
        showMatches(null, new ArrayList<>());
        if (uploads.isEmpty()) {
            matchedFiles = files;
            return;
        }

        ClassFileMatchCheck check = new ClassFileMatchCheck(getBackend(),
                uploads, classFiles, files);
        if (getBackend().isDemo()) {
            showMatches(files, check.checkAll());
            return;
        }
        check.setOnSucceeded(event -> matchCheckDone(check));
        check.setOnFailed(event -> matchCheckFailed(check));
        matchCheck = check;
        Thread worker = new Thread(check, MATCH_THREAD);
        worker.setDaemon(true);
        worker.start();
    }

    // Shows what a finished match check found, unless a newer one
    // started.
    private void matchCheckDone(ClassFileMatchCheck check) {
        if (check != matchCheck) {
            return;
        }
        matchCheck = null;
        showMatches(check.getFiles(), check.getValue());
        refreshClassFiles();
    }

    // Shows no match problem when the check stopped before it finished:
    // it only helps, so grading goes on as before.
    private void matchCheckFailed(ClassFileMatchCheck check) {
        if (check != matchCheck) {
            return;
        }
        matchCheck = null;
        showMatches(check.getFiles(), new ArrayList<>());
        refreshClassFiles();
    }

    // Keeps the matches found with a set of uploads and class files.
    private void showMatches(String files, List<ClassFileMatch> found) {
        matchedFiles = files;
        matches.clear();
        matches.addAll(found);
    }

    // Returns what the match check found for a class file's row.
    private ClassFileMatch findMatch(InputKind kind) {
        int index = STARTER_MATCH;
        if (kind == InputKind.ROSTER) {
            index = ROSTER_MATCH;
        } else if (kind == InputKind.TESTERS) {
            index = TESTERS_MATCH;
        }
        if (index >= matches.size()) {
            return ClassFileMatch.fits();
        }
        return matches.get(index);
    }

    // Lists the chosen uploads that can be graded.
    private List<ImportItem> findUsableSubmissions() {
        ImportBatch batch = getState().getBatch();
        List<ImportItem> usable = new ArrayList<>();
        for (ImportItem item : batch.getItems(InputKind.SUBMISSION)) {
            if (batch.getEffectiveState(item).isUsable()) {
                usable.add(item);
            }
        }
        return usable;
    }

    // Returns the chosen file of a kind, even one that cannot be used,
    // or null when none is chosen.
    private Path findChosenPath(InputKind kind) {
        List<ImportItem> items = getState().getBatch().getItems(kind);
        if (items.isEmpty()) {
            return null;
        }
        return items.get(0).getPath();
    }

    // Names the uploads and class files a match check uses, so a change
    // to any of them starts a new check.
    private String describeMatchFiles(List<ImportItem> uploads,
            Path[] classFiles) {
        String files = "";
        for (Path path : classFiles) {
            files = files + path + KEY_SEPARATOR;
        }
        for (ImportItem item : uploads) {
            files = files + item.getPath() + KEY_SEPARATOR;
        }
        return files;
    }

    // Builds the box that offers a better file than the chosen one, with
    // a button that uses it instead.
    private HBox createSuggestion(InputKind kind, ClassFileMatch match) {
        Text name = new Text(match.getSuggestionName());
        name.getStyleClass().addAll("match-suggestion-text",
                "match-suggestion-name");
        Text rest = new Text(match.getSuggestionDetail());
        rest.getStyleClass().add("match-suggestion-text");
        TextFlow words = new TextFlow(new Node[] {name, rest});
        words.setMinWidth(0);
        HBox.setHgrow(words, Priority.ALWAYS);

        String label = "Use this folder instead";
        if (kind == InputKind.ROSTER) {
            label = "Use this file instead";
        }
        Path suggestion = match.getSuggestion();
        Button use = Parts.small(label,
                () -> useChosenClassFile(kind, suggestion));
        HBox box = Parts.row(Parts.GAP, new Node[] {words, use});
        box.getStyleClass().add("match-suggestion");
        VBox.setMargin(box, new Insets(Parts.GAP_SMALL, 0, 0, 0));
        return box;
    }

    // Adds the tester check notes to a row; returns its pill, or null.
    private Label addTesterNotes(VBox notes) {
        Path testers = findUsableTesters();
        if (testers == null) {
            return null;
        }
        Path starter = findUsableStarter();
        String folders = describeFolders(testers, starter);
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
        String count = Parts.plural(testerProblems.size(),
                "tester problem");
        return Parts.pill(count, Tone.WARN);
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
        String detail = "Not chosen. The starter folder in Settings is used.";
        Label status = Parts.pill("From Settings", Tone.NEUTRAL);
        ClassFileMatch match = ClassFileMatch.fits();
        boolean missing = items.isEmpty() && kind.isRequired();
        if (missing && filesBeingChecked > 0) {
            // Chosen files join the list once checked, so not Missing yet
            detail = "Checking...";
            status = Parts.pill("Checking", Tone.NEUTRAL);
        } else if (missing) {
            detail = "Not chosen yet.";
            status = Parts.pill("Missing", Tone.BAD);
        } else if (!items.isEmpty()) {
            ImportItem item = items.get(0);
            ImportState state = getState().getBatch()
                    .getEffectiveState(item);
            detail = item.getName() + DOT + item.getMessage();
            status = Parts.pill(stateText(state), toneOf(state));
            match = findMatch(kind);
            if (match.hasProblem() && state.isUsable()) {
                detail = item.getName() + DOT + match.getProblem();
                status = Parts.pill("Check", Tone.WARN);
            }
        }
        String iconPath = classFileIcon(kind);
        HBox line = detailLine(detail);
        if (extra != null) {
            line.getChildren().add(extra);
        }
        VBox details = new VBox(line);
        if (match.hasSuggestion()) {
            details.getChildren().add(createSuggestion(kind, match));
        }
        if (kind == InputKind.TESTERS) {
            Label warning = addTesterNotes(details);
            if (warning != null) {
                status = warning;
            }
        }
        Hyperlink change = changeLink(kind);
        return classFileRow(iconPath, title, details, status, change);
    }

    // Returns the icon of a class file row: a file for the score sheet,
    // a folder for the others.
    private String classFileIcon(InputKind kind) {
        if (kind == InputKind.ROSTER) {
            return Parts.FILE;
        }
        return Parts.FOLDER;
    }

    // Returns the link at the end of a class file row. Tester and starter
    // files belong to the assessment, so they are changed in its editor;
    // only the score sheet is chosen here.
    private Hyperlink changeLink(InputKind kind) {
        Hyperlink change = rowLink("Change", () -> changeClassFile(kind));
        if (kind != InputKind.ROSTER) {
            change = rowLink("Edit assessment",
                    () -> editAssessmentFiles());
            change.setPrefWidth(Region.USE_COMPUTED_SIZE);
            change.setMinWidth(Region.USE_PREF_SIZE);
        }
        return change;
    }

    // Builds the tester row: a folder, or the typed test cases.
    private HBox createTesterRow() {
        ImportBatch batch = getState().getBatch();
        boolean canType = canTypeTestCases();
        if (!canType) {
            return createClassFileRow(InputKind.TESTERS, TESTERS_TITLE,
                    null);
        }
        if (!batch.isUsingTypedTestCases()) {
            return createClassFileRow(InputKind.TESTERS, TESTERS_TITLE,
                    smallLink("or type test cases", () -> openTestCases()));
        }

        int cases = batch.getTypedTestCases().size();
        String typedText = Parts.plural(cases, "test case")
                + " typed in the app";
        HBox detail = detailLine(typedText);
        detail.getChildren().add(smallLink("Edit test cases",
                () -> openTestCases()));
        Label status = Parts.pill("Missing", Tone.BAD);
        if (cases > 0) {
            status = Parts.pill("Ready", Tone.GOOD);
        }
        Hyperlink change = rowLink("Change",
                () -> changeClassFile(InputKind.TESTERS));
        return classFileRow(Parts.CODE, TESTERS_TITLE, detail, status,
                change);
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
        if (title.equals(ROSTER_TITLE)) {
            help = ROSTER_HELP;
        } else if (title.equals(TESTERS_TITLE)) {
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
        String runLimit = getBackend().getRunTimeoutSeconds() + " s";
        String compileLimit = getBackend().getCompileTimeoutSeconds()
                + " s";
        facts.add(fact("Time limit per test run", runLimit, true, "A "
                + "tester that runs longer is stopped, so an endless loop "
                + "cannot hold up the batch. Cases passed before that keep "
                + "their marks."), 0, 0);
        facts.add(fact("Compile time limit", compileLimit, false,
                "The longest javac may take for one question folder."),
                1, 0);
        facts.add(fact("Fix common mistakes", fixes, false, "Small "
                + "mistakes, such as a wrong folder name or a missing "
                + "wrapper folder, are fixed in a copy, never in the "
                + "original. On Review you approve or undo each fix."),
                FIXES_FACT_COLUMN, 0);
        facts.add(fact("Similar code check", similar, false, "After "
                + "grading, compares answers and flags pairs that look "
                + "alike. It only flags; marks never change."),
                SIMILAR_FACT_COLUMN, 0);
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
        if (words.isEmpty()) {
            return "";
        }
        List<String> allButLast = new ArrayList<>(words);
        int lastIndex = allButLast.size() - 1;
        String last = allButLast.remove(lastIndex);
        if (allButLast.isEmpty()) {
            return last;
        }
        return TextUtils.join(WORD_SEPARATOR, allButLast)
                + LAST_WORD_SEPARATOR + last;
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
        readMarksIfChanged();
        List<String> unknownMarks = findMarkProblems();
        boolean ready = batch.isReady() && !getState().isGrading()
                && filesBeingChecked == 0 && unknownMarks.isEmpty();
        actions.setPrimary("Start grading", () -> confirmStart(), ready);
        if (getState().isGrading()) {
            actions.setStatus("A batch is being graded. See step 2.");
        } else if (filesBeingChecked > 0) {
            actions.setStatus("Checking " + Parts.plural(filesBeingChecked,
                    "file") + "...");
        } else if (!unknownMarks.isEmpty()) {
            actions.setStatus(unknownMarks.get(0));
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
            String students = Parts.plural(batch.countStudents(),
                    "student");
            actions.setStatus(students + " ready to grade in "
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
