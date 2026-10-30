/*
 * File: SetUpScreen.java
 *
 * Purpose: Step 1. The instructor chooses the module and section, adds
 * the students' zip files, checks the score sheet and tester files, sees
 * the grading settings this run will use, then starts grading.
 *
 * Where it fits: Replaces the old Modules, Assessments, Submissions and
 * Validation screens. Calls DashboardBackend.inspect for every file and
 * hands the batch to GradeScreen. TestCasesSheet edits typed test cases.
 */
package gradezzy.ui.flow;

import java.io.File;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.List;

import javafx.geometry.HPos;
import javafx.geometry.Pos;
import javafx.scene.Node;
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
import gradezzy.ui.DragDropHandler;
import gradezzy.ui.backend.Capability;
import gradezzy.ui.backend.CapabilityStatus;
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

    // The text a true setting holds
    private static final String TRUE = "true";

    // The separator between a file and what is known about it
    private static final String DOT = "  ·  ";

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

    // The assessment, which cannot be changed here
    private final Label assessmentText = new Label();

    // A note when the chosen section was graded before
    private final HBox earlierRun = Parts.row(Parts.GAP_SMALL);

    // The drop area
    private final HBox dropArea = Parts.row(Parts.GAP);

    // How many files there are and how many are ready
    private final HBox fileSummary = Parts.row(Parts.GAP);

    // The chosen zip files
    private final VBox fileList = new VBox();

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

    // True while the choices are being filled, so no change is handled
    private boolean filling = false;

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
        moduleBox.setOnAction(event -> chooseModule());
        sectionBox.setOnAction(event -> chooseSection());
        assessmentText.getStyleClass().add("read-only-field");
        assessmentText.setMaxWidth(Double.MAX_VALUE);

        GridPane fields = new GridPane();
        fields.setHgap(Parts.GAP);
        fields.getColumnConstraints().addAll(column(CHOICE_PERCENT),
                column(CHOICE_PERCENT), column(ASSESSMENT_PERCENT));
        fields.add(field("Module", moduleBox), 0, 0);
        fields.add(field("Section", sectionBox), 1, 0);
        fields.add(field("Assessment", assessmentText), 2, 0);
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

        fileList.getStyleClass().add("file-list");
        HBox top = Parts.row(Parts.GAP, new Node[] {
                Parts.title("Submissions"), Parts.grow(), fileSummary});
        VBox card = Parts.card(null, new Node[] {top, dropArea, fileList});
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

    // Puts a small label above a control.
    private VBox field(String label, Node control) {
        Label name = Parts.muted(label);
        name.getStyleClass().add("field-label");
        VBox box = new VBox(Parts.GAP_SMALL / 2, name, control);
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
        if (paths.isEmpty()) {
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
            getState().getBatch().add(getBackend().inspect(kind, path));
        }
    }

    // Asks for a new score sheet, tester or starter folder.
    private void changeClassFile(InputKind kind) {
        Path path = chooseClassFile(kind);
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

    // Shows the assessment's name, questions and marks.
    private void refreshAssessment() {
        Assessment assessment = getBackend().getAssessment();
        if (assessment == null) {
            assessmentText.setText("Not set in the settings");
            return;
        }
        assessmentText.setText(assessment.getName() + DOT
                + Parts.plural(assessment.getQuestions().size(),
                "question") + DOT
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
        earlierRun.getChildren().addAll(
                Parts.muted(getState().getSection().getLabel()
                + " was graded before ("
                + Parts.plural(run.getSubmissions().size(), "student")
                + ")."),
                Parts.link("Open its results",
                () -> getWindow().openRun(run)));
    }

    // Redraws the zip file list and the count above it.
    private void refreshFiles() {
        ImportBatch batch = getState().getBatch();
        List<ImportItem> items = batch.getItems(InputKind.SUBMISSION);
        fileList.getChildren().clear();
        for (int index = 0; index < items.size(); index++) {
            HBox row = createFileRow(items.get(index));
            markRow(row, index, items.size());
            fileList.getChildren().add(row);
        }
        fileList.setVisible(!items.isEmpty());
        fileList.setManaged(!items.isEmpty());

        fileSummary.getChildren().clear();
        if (items.isEmpty()) {
            return;
        }
        int unusable = batch.count(InputKind.SUBMISSION,
                ImportState.INVALID) + batch.count(InputKind.SUBMISSION,
                ImportState.DUPLICATE);
        int ready = items.size() - unusable;
        Label count = Parts.muted(Parts.plural(items.size(), "file")
                + DOT + ready + " ready");
        count.getStyleClass().add("small-text");
        fileSummary.getChildren().addAll(count,
                smallLink("Clear all", () -> clearSubmissions()));
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
        if (!item.getUsername().isEmpty()) {
            Label who = Parts.muted(DOT + item.getUsername());
            who.setMinWidth(0);
            line.getChildren().add(who);
        }
        VBox text = new VBox(line);
        if (!state.isUsable() && !item.getMessage().isEmpty()) {
            Label message = Parts.muted(item.getMessage());
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

    // Redraws the score sheet, tester and starter rows.
    private void refreshClassFiles() {
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
            detail = "Not chosen. The untouched-answer check is skipped.";
            status = Parts.pill("Skipped", Tone.NEUTRAL);
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
        return classFileRow(iconPath, title, line, status,
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

    // Returns the grey line under a class file's name.
    private HBox detailLine(String text) {
        Label label = Parts.muted(text);
        label.getStyleClass().add("small-text");
        label.setMinWidth(0);
        return Parts.row(Parts.GAP_SMALL, new Node[] {label});
    }

    // Lays out a class file row: icon, name and detail, status, link.
    private HBox classFileRow(String iconPath, String title, HBox detail,
            Label status, Hyperlink change) {
        VBox text = new VBox(Parts.strong(title), detail);
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
                getBackend().getRunTimeoutSeconds() + " s", true), 0, 0);
        facts.add(fact("Compile time limit",
                getBackend().getCompileTimeoutSeconds() + " s", false),
                1, 0);
        facts.add(fact("Fix common mistakes", fixes, false), 2, 0);
        facts.add(fact("Similar code check", similar, false), 3, 0);
        checksLine.setText(describeChecks());
    }

    // Builds one cell of the facts row.
    private VBox fact(String name, String value, boolean first) {
        Label key = Parts.muted(name);
        key.getStyleClass().add("small-text");
        Label text = Parts.strong(value);
        text.getStyleClass().add("fact-value");
        VBox cell = new VBox(key, text);
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
        boolean ready = batch.isReady() && !getState().isGrading();
        actions.setPrimary("Start grading",
                () -> getWindow().startGrading(), ready);
        if (getState().isGrading()) {
            actions.setStatus("A batch is being graded. See step 2.");
        } else if (!batch.isReady()) {
            List<String> reasons = batch.getBlockingReasons();
            if (reasons.isEmpty()) {
                actions.setStatus("");
            } else {
                actions.setStatus(reasons.get(0));
            }
        } else {
            int count = batch.count(InputKind.SUBMISSION,
                    ImportState.ACCEPTED) + batch.count(
                    InputKind.SUBMISSION, ImportState.WARNING);
            actions.setStatus(Parts.plural(count, "student")
                    + " ready to grade in "
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
