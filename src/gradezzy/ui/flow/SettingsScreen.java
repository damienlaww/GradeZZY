/*
 * File: SettingsScreen.java
 *
 * Purpose: The Settings tab: every setting in config.properties, in
 * plain groups with a short description, each checked as it is typed.
 * A list on the left jumps to a group and a search box finds a setting.
 * Changes are saved together with Save, or thrown away with Discard.
 *
 * Where it fits: FlowWindow shows it when the Settings tab is picked.
 * Reads, checks and saves through DashboardBackend; every key comes from
 * ConfigKeys.
 */
package gradezzy.ui.flow;

import java.io.File;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Objects;
import java.util.function.Supplier;

import javafx.geometry.Insets;
import javafx.geometry.Pos;
import javafx.scene.Node;
import javafx.scene.control.Button;
import javafx.scene.control.ComboBox;
import javafx.scene.control.Hyperlink;
import javafx.scene.control.Label;
import javafx.scene.control.ScrollPane;
import javafx.scene.control.TextField;
import javafx.scene.layout.FlowPane;
import javafx.scene.layout.HBox;
import javafx.scene.layout.Priority;
import javafx.scene.layout.VBox;
import javafx.scene.shape.Circle;
import javafx.stage.DirectoryChooser;
import javafx.stage.FileChooser;

import atlantafx.base.controls.CustomTextField;

import gradezzy.config.ConfigKeys;
import gradezzy.config.ExportFolders;
import gradezzy.config.ModuleSections;
import gradezzy.model.Assessment;
import gradezzy.model.SavedAssessment;
import gradezzy.ui.ThemeManager;
import gradezzy.ui.backend.ActionResult;
import gradezzy.ui.backend.Capability;
import gradezzy.util.TextUtils;

/**
 * The Settings tab.
 *
 * <p>Tier: SHOULD.
 */
public class SettingsScreen extends FlowScreen {

    // The width of the list of groups on the left
    private static final double INDEX_WIDTH = 180.0;

    // The gap between the list of groups and the cards
    private static final double INDEX_GAP = 28.0;

    // The gap between the header and the body
    private static final double HEADER_GAP = 16.0;

    // The width of the search field
    private static final double SEARCH_WIDTH = 240.0;

    // The size of the search icon
    private static final double SEARCH_ICON_SIZE = 14.0;

    // The gap between entries of the list of groups
    private static final double INDEX_ITEM_GAP = 4.0;

    // The gap between a name and its description
    private static final double LINE_GAP = 2.0;

    // The room between the dot in the action bar and its text, added
    // to the label's own small gap
    private static final double DOT_TEXT_GAP = 4.0;

    // The radius of the dot in the action bar
    private static final double DOT_RADIUS = 3.0;

    // How close to the end of the scroll counts as the bottom
    private static final double BOTTOM_SLACK = 0.001;

    // How far from a group's top still counts as being on that group
    private static final double SCROLL_SLACK = 24.0;

    // The values ui.theme accepts
    private static final String[] THEMES = {ThemeManager.LIGHT_THEME,
        ThemeManager.DARK_THEME};

    // The names shown for the values of ui.theme, in the same order
    private static final String[] THEME_NAMES = {"Light", "Dark"};

    // The values export.csv.line.ending accepts
    private static final String[] LINE_ENDINGS = {"CRLF", "LF"};

    // Their names in the list
    private static final String[] LINE_ENDING_NAMES = {
        "CRLF (as eLearn gives it)", "LF (macOS and Linux)"};

    // The values plagiarism.algorithm accepts
    private static final String[] ALGORITHMS = {"winnowing", "trigrams"};

    // The names shown for the values of plagiarism.algorithm, in the
    // same order
    private static final String[] ALGORITHM_NAMES = {
        "Winnowing: also catches renamed copies",
        "Trigrams: only copies that keep the names"};

    // The values plagiarism.min.lines offers, and the one recommended
    static final String[] MIN_LINES = {"10", "15", "20", "25", "30", "40",
        "50", "60"};
    private static final String RECOMMENDED_MIN_LINES = "40";

    // The values each number setting offers in its list. A value set by
    // hand that is not here is kept as one more choice.
    static final String[] RUN_SECONDS = {"5", "10", "15", "20",
        "30", "60"};
    static final String[] COMPILE_SECONDS = {"10", "20", "30",
        "60", "120"};
    private static final String[] OUTPUT_CHARACTERS = {"10000", "50000",
        "100000", "500000", "1000000"};
    private static final String[] SIMILARITIES = {"0.50", "0.60", "0.70",
        "0.75", "0.80", "0.85", "0.90", "0.95"};
    private static final String[] PASS_PERCENTS = {"40", "45", "50", "55",
        "60", "65", "70", "75"};
    private static final String[] BAND_PERCENTS = {"5", "10", "20", "25"};
    private static final String[] BACKUP_COUNTS = {"1", "3", "5", "10",
        "20"};
    private static final String[] WINDOW_WIDTHS = {"960", "1024", "1200",
        "1280", "1440", "1600", "1920"};
    private static final String[] WINDOW_HEIGHTS = {"640", "720", "800",
        "900", "1080"};
    private static final String[] ZIP_MEGABYTES = {"10", "25", "50", "100",
        "200", "500"};
    private static final String[] ZIP_ENTRIES = {"500", "1000", "2000",
        "5000", "10000"};
    private static final String[] HEADER_LINES = {"5", "10", "15", "20",
        "30"};
    private static final String[] COPY_COUNTS = {"20", "40", "80"};
    private static final String[] COPY_SECONDS = {"2", "5", "10"};
    private static final String[] INPUT_COUNTS = {"50", "100", "200",
        "500"};

    // The most students graded at once the list offers, before the
    // number of processors decides
    private static final int[] THREAD_STEPS = {1, 2, 4, 8, 16};

    // Turns a share such as 0.80 into a percentage such as 80
    private static final double PERCENT = 100.0;

    // Writes a large number with thousands separators, such as 100,000
    private static final String THOUSANDS_FORMAT = "%,d";

    // The count of students that means one at a time
    private static final String ONE_AT_A_TIME = "1";

    // Separates the module and section in a key such as "CS102/G1"
    private static final String KEY_SEPARATOR = "/";

    // Separates the module and section where they are shown
    private static final String SHOWN_SEPARATOR = " · ";

    // The most parts a key such as "CS102/G1" is split into
    private static final int KEY_PARTS = 2;

    // The position of the module in a split key
    private static final int MODULE_PART = 0;

    // The position of the section in a split key
    private static final int SECTION_PART = 1;

    // The position of who an entry is for, such as "CS102/G1"
    private static final int WHO_PART = 0;

    // The position of an entry's folder or file
    private static final int PATH_PART = 1;

    // Separates the codes of a list setting such as app.module.codes
    private static final String LIST_SEPARATOR = ",";

    // A forward slash, which separates folders on every computer
    private static final char FORWARD_SLASH = '/';

    // A backslash, which separates folders on Windows
    private static final char BACKSLASH = '\\';

    // The quote around a setting's key in a message from the check
    private static final String QUOTE = "'";

    // The largest scroll value: the bottom of the cards
    private static final double SCROLL_END = 1.0;

    // Where a chip's "only" tag goes: after the section's name
    private static final int TAG_POSITION = 1;

    // The file name pattern of a score sheet
    private static final String CSV_PATTERN = "*.csv";

    // The page
    private final VBox view = new VBox(HEADER_GAP);

    // The list of groups on the left
    private final VBox index = new VBox(INDEX_ITEM_GAP);

    // The cards on the right
    private final VBox cards = new VBox(Parts.CARD_GAP);

    // Scrolls the cards
    private final ScrollPane cardScroll = new ScrollPane(cards);

    // Finds a setting
    private final CustomTextField search = new CustomTextField();

    // Shown when no setting matches the search
    private final VBox noMatch = new VBox();

    // Every group, top to bottom
    private final List<SettingsGroup> groups = new ArrayList<>();

    // Names the module and the assessment edited, and splits the groups
    // into the bands of what each changes with
    private final SettingsScope scope = new SettingsScope(
            group -> jumpTo(group), () -> isAnimated());

    // Every setting, top to bottom
    private final List<SettingField> fields = new ArrayList<>();

    // The dot shown next to the number of unsaved changes
    private final Circle changesDot = new Circle(DOT_RADIUS);

    // Holds the dot with a little room before the text
    private final HBox changesMark = new HBox(changesDot);

    // The Discard button of the action bar
    private final Button discardButton;

    // The words of the dropdown line that starts a new assessment
    private static final String NEW_ASSESSMENT = "+ New assessment...";

    // The width of the assessment dropdown
    private static final double PICKER_WIDTH = 260.0;

    // The section dropdown's line for a whole module
    private static final String WHOLE_MODULE = "Whole module";

    // Every section with its score sheet
    private final VBox scoreSheetList = new VBox(Parts.GAP_SMALL);

    // The modules and sections with their own export folder
    private final VBox exportFolderList = new VBox(Parts.GAP_SMALL);

    // The Course controls: the modules, the button that deletes one, and
    // the chosen module's sections as chips
    private final ComboBox<String> moduleList = new ComboBox<>();
    private final Button deleteModule = Parts.small("Delete module",
            () -> askDeleteModule(moduleList.getValue()));
    private final FlowPane sectionChips = new FlowPane();

    // True while the module dropdown is filled, so no pick is handled
    private boolean fillingCourse = false;

    // The module whose sections and assessments are shown, or null
    private String shownModule = null;

    // The module chosen on Home when Settings was last shown, so Settings
    // follows a new pick there but keeps its own pick otherwise
    private String homeModuleSeen = null;

    // Whether the course settings can be changed here
    private boolean editableCourse = true;

    // How wide the row of section chips gets before it wraps
    private static final double SECTION_WRAP = 520.0;

    // The size of a chip's delete icon and its gap
    private static final double CHIP_ICON_SIZE = 10.0;
    private static final double CHIP_GAP = 6.0;

    // The dropdown of saved assessments, and the form under it
    private final ComboBox<String> assessmentPicker = new ComboBox<>();
    private final VBox assessmentHolder = new VBox(Parts.GAP);

    // The assessment form shown, or null when the module has none
    private AssessmentEditor assessmentForm = null;

    // True while the dropdown is being filled, so no pick is handled
    private boolean pickingAssessment = false;

    // Scrolls to the first error, shown while an error blocks Save
    private final Button showErrorButton = Parts.secondary("Show error",
            () -> showFirstError());

    // The name of the group of the checks that use the model answers
    private static final String MODEL_ANSWER_CHECKS = "Model answer checks";

    // The name of the group of each section's export folder
    private static final String EXPORT_FOLDERS = "Export folders";

    // Names the model answers those checks use: the folder of the
    // assessment in use
    private final Label modelAnswersUsed = new Label();

    // Shows the Assessment group, where that folder is set
    private final Hyperlink modelAnswersLink = Parts.link(
            "Change in Assessment", () -> scope.showAssessmentGroup());

    // The group being built
    private SettingsGroup building = null;

    // The group highlighted in the list
    private SettingsGroup current = null;

    // True while the page scrolls because a group was clicked
    private boolean jumping = false;

    /**
     * Creates the Settings tab.
     *
     * @param window the window
     */
    public SettingsScreen(FlowWindow window) {
        super(window);
        discardButton = Parts.secondary("Discard", () -> askDiscard());
        changesDot.getStyleClass().add("setting-dot");
        changesMark.setAlignment(Pos.CENTER);
        changesMark.setPadding(new Insets(0, DOT_TEXT_GAP, 0, 0));
        addGroups();
        HBox headerRow = buildHeaderRow();
        buildCardScroll();

        index.setMinWidth(INDEX_WIDTH);
        index.setPrefWidth(INDEX_WIDTH);
        index.setMaxWidth(INDEX_WIDTH);
        HBox body = new HBox(INDEX_GAP, index, cardScroll);
        VBox.setVgrow(body, Priority.ALWAYS);

        view.getChildren().addAll(headerRow, body);
        view.getStyleClass().add("settings-page");
        select(groups.get(0));

        boolean editable = getBackend().supports(Capability.SETTINGS);
        for (SettingField field : fields) {
            field.setEditable(editable);
            field.setOnChange(() -> refreshBar());
        }
        showErrorButton.setVisible(false);
        showErrorButton.setManaged(false);
        // From now on a change of module or assessment lights up the
        // groups it changes
        scope.arm();
    }

    // Says whether Subtle animations is on in Settings.
    private boolean isAnimated() {
        return Boolean.parseBoolean(getBackend().getSetting(
                ConfigKeys.ANIMATIONS_ENABLED));
    }

    // Builds the title and the search field above the cards.
    private HBox buildHeaderRow() {
        search.setPromptText("Find a setting");
        search.setLeft(Parts.icon(Parts.SEARCH, SEARCH_ICON_SIZE));
        search.setPrefWidth(SEARCH_WIDTH);
        search.setMaxWidth(SEARCH_WIDTH);
        search.textProperty().addListener((observable, before, after)
                -> applySearch());
        VBox header = Parts.header("Settings",
                "Your modules, assessments and how grading works. Grading "
                + "settings apply from the next run.");
        HBox headerRow = Parts.row(Parts.GAP,
                new Node[] {header, Parts.grow(), search});
        headerRow.getStyleClass().add("settings-header");
        return headerRow;
    }

    // Adds the no-match note under the cards and sets up their scroll.
    private void buildCardScroll() {
        noMatch.getChildren().add(Parts.muted("No setting matches."));
        noMatch.getStyleClass().add("settings-no-match");
        noMatch.setAlignment(Pos.CENTER);
        noMatch.setVisible(false);
        noMatch.setManaged(false);
        cards.getChildren().add(noMatch);
        cards.getStyleClass().add("settings-cards");

        cardScroll.setFitToWidth(true);
        cardScroll.setHbarPolicy(ScrollPane.ScrollBarPolicy.NEVER);
        cardScroll.getStyleClass().add("page-scroll");
        cardScroll.vvalueProperty().addListener((observable, before,
                after) -> followScroll());
        HBox.setHgrow(cardScroll, Priority.ALWAYS);
    }

    /**
     * Returns the Settings tab built in the constructor.
     */
    @Override
    public Node getView() {
        return view;
    }

    /**
     * Sets up the Save and Discard buttons, or says that settings
     * can only be changed in config.properties.
     */
    @Override
    public void onShow() {
        ActionBar actions = getActions();
        if (!getBackend().supports(Capability.SETTINGS)) {
            actions.setStatus("Settings cannot be changed here. Edit "
                    + "config.properties instead.");
            return;
        }
        // The module chosen on Home or Set up may have changed, and the
        // course on Home or in History, such as a delete taken back there.
        // A module picked here stays until another is chosen on Home.
        String homeModule = getState().getModuleCode();
        String module = shownModule;
        if (module == null || !homeModule.equals(homeModuleSeen)) {
            module = homeModule;
        }
        homeModuleSeen = homeModule;
        fillCourse(module);
        refreshAssessmentPicker();
        // A setting saved elsewhere, such as from Set up, shows its new value
        for (SettingField field : fields) {
            if (!field.isChanged()) {
                field.reload();
            }
        }
        actions.addSecondary(showErrorButton);
        actions.addSecondary(discardButton);
        actions.setPrimary("Save", () -> save(), false);
        refreshBar();
    }

    /**
     * Puts a value in a setting's control without saving it, as if it
     * was typed. Used by the developer checks.
     *
     * @param key the setting's name in config.properties
     * @param value the value
     */
    public void previewChange(String key, String value) {
        for (SettingField field : fields) {
            if (field.getKey().equals(key)) {
                field.setValue(value);
            }
        }
    }

    /**
     * Says whether any setting has a change that is not saved yet.
     *
     * @return true if a setting was changed and not saved
     */
    public boolean hasUnsavedChanges() {
        for (SettingField field : fields) {
            if (field.isChanged()) {
                return true;
            }
        }
        return false;
    }

    /**
     * Says whether the assessment form has a change that is not saved
     * yet. Its own Save assessment saves it, not the bar's Save.
     *
     * @return true if the assessment form was changed and not saved
     */
    public boolean hasUnsavedAssessment() {
        return assessmentForm != null && assessmentForm.hasUnsavedChanges();
    }

    /**
     * Says in a sentence what is not saved yet, for the sheet that asks
     * before leaving Settings.
     *
     * @return the sentence
     */
    public String describeUnsaved() {
        int changed = 0;
        for (SettingField field : fields) {
            if (field.isChanged()) {
                changed++;
            }
        }
        String settingsWords = Parts.plural(changed, "setting");
        if (changed == 0) {
            return "The assessment form has changes that are not saved yet.";
        } else if (hasUnsavedAssessment()) {
            return settingsWords + " and the assessment form have changes "
                    + "that are not saved yet. Save keeps both.";
        }
        return "Your changes to " + settingsWords + " are not saved yet.";
    }

    /**
     * Saves the changed settings and the assessment form. Stops at the
     * first that cannot be saved, which then says what is wrong.
     *
     * @return true if nothing is left unsaved
     */
    public boolean saveAll() {
        if (hasUnsavedChanges() && !save()) {
            return false;
        }
        if (hasUnsavedAssessment()) {
            return assessmentForm.saveChanges();
        }
        return true;
    }

    /**
     * Puts back every saved setting and the saved assessment's values.
     */
    public void discardAll() {
        discardChanges();
        if (hasUnsavedAssessment()) {
            assessmentForm.revertChanges();
        }
    }

    /**
     * Puts every saved value back in its control.
     */
    public void discardChanges() {
        for (SettingField field : fields) {
            field.reset();
        }
        refreshBar();
    }

    // Asks before throwing away the changes that are not saved.
    private void askDiscard() {
        getWindow().confirm("Discard your changes?", "Every setting goes "
                + "back to what config.properties holds now.",
                "Discard changes", () -> discardChanges());
    }

    /**
     * Scrolls the cards to the top or to the bottom.
     *
     * @param bottom true for the bottom
     */
    public void scrollTo(boolean bottom) {
        if (bottom) {
            cardScroll.setVvalue(cardScroll.getVmax());
        } else {
            cardScroll.setVvalue(cardScroll.getVmin());
        }
    }

    // ------------------------------------------------------------ groups

    // Adds every group of settings, so each line of config.properties
    // has a place: first those that change with the module chosen,
    // then those that change with the assessment, then those that are
    // the same for everyone.
    private void addGroups() {
        startGroup("Course");
        scope.forModule(building, "Pick the module to edit; the sections "
                + "below change with it. Home keeps its own module.");
        addCourseRows();

        startGroup(EXPORT_FOLDERS);
        scope.forModule(building, "Where %s and its sections save their "
                + "exports; the others use the folder in Files and "
                + "folders.");
        scope.nameModuleInTitle(building, EXPORT_FOLDERS);
        addExportFolderRows();

        startGroup("Assessment");
        scope.forAssessment(building);
        addAssessmentEditor();

        startGroup("Score sheets");
        scope.forScoreSheets(building);
        addScoreSheetRows();

        startGroup("Files and folders");
        addFileRows();

        startGroup("Checks and fixes");
        addCheckRows();

        startGroup("Running code");
        addRunningRows();

        startGroup(MODEL_ANSWER_CHECKS);
        addModelAnswerRows();

        startGroup("Similar code");
        addSimilarRows();

        startGroup("Statistics");
        addStatisticsRows();

        startGroup("Saved data");
        addSavedDataRows();

        startGroup("Appearance");
        addAppearanceRows();

        startGroup("Advanced");
        addAdvancedRows();
    }

    // Adds the folders every module uses to the group being built.
    private void addFileRows() {
        folder(ConfigKeys.WORKING_DIR, "Working copies",
                "Fixes and tests run here. Originals never change.");
        folder(ConfigKeys.OUTPUT_DIR, "Exports saved in",
                "CSV, JSON and reports, for every module and section unless "
                + "one has its own folder under Export folders.");
        folder(ConfigKeys.BACKUP_DIR, "Backups saved in",
                "Copies made by Back up now in History.");
        folder(ConfigKeys.ASSESSMENTS_DIR, "Assessments saved in",
                "One file per saved assessment.");
    }

    // Adds the checks and the fixes to the group being built.
    private void addCheckRows() {
        toggle(ConfigKeys.AUTO_FIX_ENABLED, "Fix small mistakes",
                "Each fix waits for you to approve or undo it.");
        toggle(ConfigKeys.DETECT_UNTOUCHED_ENABLED, "Flag untouched files",
                "Starter files handed in unchanged. Marks never change.");
        toggle(ConfigKeys.DETECT_EMPTY_ENABLED, "Flag empty answers",
                "Marks never change.");
        toggle(ConfigKeys.DETECT_HARD_CODED_ENABLED,
                "Flag hard-coded answers",
                "Answers that may print the output directly.");
    }

    // Adds the limits and commands of compiling and testing to the group
    // being built.
    private void addRunningRows() {
        listed(ConfigKeys.RUN_TIMEOUT_SECONDS, "Time limit per test run",
                "A tester that runs longer is stopped.", RUN_SECONDS,
                " seconds");
        listed(ConfigKeys.COMPILE_TIMEOUT_SECONDS, "Compile time limit",
                "For one question folder.", COMPILE_SECONDS, " seconds");
        String[] threads = threadChoices();
        choice(ConfigKeys.THREAD_COUNT, "Students graded at once",
                "More is faster on a computer with more processors.",
                threads).showChoicesAs(threadNames(threads));
        text(ConfigKeys.JAVAC_COMMAND, "Compile command",
                "Must be on the PATH.");
        text(ConfigKeys.JAVA_COMMAND, "Run command",
                "Must be on the PATH.");
        choice(ConfigKeys.MAX_OUTPUT_CHARACTERS, "Longest output kept",
                "Per run, to protect memory.", OUTPUT_CHARACTERS)
                .showChoicesAs(groupedNames(OUTPUT_CHARACTERS,
                " characters"));
    }

    // Adds the checks that use the model answers to the group being
    // built. The folder itself belongs to each assessment.
    private void addModelAnswerRows() {
        addModelAnswersUsedRow();
        toggle(ConfigKeys.TESTER_STRENGTH_ENABLED,
                "Check testers before grading",
                "Set up runs each tester on broken copies of the model "
                + "answer. A copy with full marks shows a gap.");
        listed(ConfigKeys.TESTER_STRENGTH_MAX_COPIES,
                "Broken copies per question",
                "More finds more gaps, but takes longer.", COPY_COUNTS, "");
        listed(ConfigKeys.TESTER_STRENGTH_TIMEOUT_SECONDS,
                "Time limit per broken copy",
                "A copy that runs longer counts as caught.", COPY_SECONDS,
                " seconds");
        toggle(ConfigKeys.RANDOM_INPUTS_ENABLED, "Try random inputs",
                "After the tests, answers that differ from the model "
                + "answer are flagged. Marks never change.");
        listed(ConfigKeys.RANDOM_INPUTS_COUNT, "Random inputs per method",
                "", INPUT_COUNTS, "");
    }

    // Adds the similar code check to the group being built.
    private void addSimilarRows() {
        toggle(ConfigKeys.PLAGIARISM_ENABLED, "Look for similar code",
                "Runs after grading; pairs are flagged. Identical copies, "
                + "with the same Name, Email ID and code, are flagged even "
                + "when this is off.");
        choice(ConfigKeys.PLAGIARISM_THRESHOLD, "Similarity that counts",
                "How alike two answers must be to be flagged.",
                SIMILARITIES).showChoicesAs(similarityNames());
        choice(ConfigKeys.PLAGIARISM_ALGORITHM, "How code is compared",
                "Winnowing ignores names, so it finds copies with renamed "
                + "variables, but can also flag honest answers built the "
                + "same way.", ALGORITHMS).showChoicesAs(ALGORITHM_NAMES);
        choice(ConfigKeys.PLAGIARISM_MIN_LINES, "Shortest answer compared",
                "Lines of new code each answer needs, not counting blank "
                + "lines, comments, braces or starter code. Short answers "
                + "to easy questions look alike even when written alone.",
                MIN_LINES).showChoicesAs(minLineNames());
    }

    // Adds the pass mark and the band width to the group being built.
    private void addStatisticsRows() {
        listed(ConfigKeys.PASS_MARK_PERCENT, "Pass mark",
                "Of the maximum mark.", PASS_PERCENTS, Parts.PERCENT_SIGN);
        listed(ConfigKeys.MARK_BAND_WIDTH_PERCENT, "Mark band width",
                "Of the maximum mark, for each bar of the charts.",
                BAND_PERCENTS, Parts.PERCENT_SIGN);
    }

    // Adds the database, the backups and the activity log to the group
    // being built.
    private void addSavedDataRows() {
        toggle(ConfigKeys.DATABASE_ENABLED, "Save results",
                "Needed for History, Resume and the activity log.");
        file(ConfigKeys.DATABASE_FILE, "Database file", "");
        listed(ConfigKeys.BACKUP_KEEP_COUNT, "Backups kept",
                "The oldest is deleted after this many.", BACKUP_COUNTS, "");
        toggle(ConfigKeys.AUDIT_ENABLED, "Keep an activity log",
                "Approvals, undos and exports, with the time.");
    }

    // Adds the theme, the animations and the window size to the group
    // being built.
    private void addAppearanceRows() {
        choice(ConfigKeys.UI_THEME, "Theme", "", THEMES).showChoicesAs(
                THEME_NAMES);
        toggle(ConfigKeys.ANIMATIONS_ENABLED, "Subtle animations", "");
        listed(ConfigKeys.WINDOW_WIDTH, "Window width",
                "Starting size.", WINDOW_WIDTHS, " pixels");
        listed(ConfigKeys.WINDOW_HEIGHT, "Window height",
                "Starting size.", WINDOW_HEIGHTS, " pixels");
    }

    // Adds the settings most people never change to the group being
    // built.
    private void addAdvancedRows() {
        folder(ConfigKeys.SUBMISSIONS_DIR, "Submissions for the console",
                "Only for run.sh console. The window never uses it.");
        text(ConfigKeys.ZIP_NAME_PREFIX_PATTERN, "Zip name prefix",
                "Pattern of the text before the username in a zip name.");
        text(ConfigKeys.IGNORED_FILE_NAMES, "Ignored file names",
                "Inside a submission. Separated by commas.");
        listed(ConfigKeys.MAX_ZIP_SIZE_MB, "Largest unzipped size",
                "For one submission.", ZIP_MEGABYTES, " MB");
        choice(ConfigKeys.MAX_ZIP_ENTRIES, "Most files in one zip", "",
                ZIP_ENTRIES).showChoicesAs(groupedNames(ZIP_ENTRIES,
                " files"));
        text(ConfigKeys.HEADER_NAME_LABEL, "Header name label",
                "In front of the student's name in a file's header.");
        text(ConfigKeys.HEADER_EMAIL_LABEL, "Header email label",
                "In front of the student's email in a file's header.");
        listed(ConfigKeys.HEADER_SCAN_LINES, "Header lines searched",
                "At the top of each file.", HEADER_LINES, " lines");
        text(ConfigKeys.TESTER_CLASS_SUFFIX, "Tester name ending",
                "Added to a question, so Q1a becomes Q1aTester.");
        text(ConfigKeys.PASS_MARKER, "Passed line",
                "What a tester prints when a test case passes.");
        text(ConfigKeys.FAIL_MARKER, "Failed line",
                "Start of what a tester prints when a test case fails.");
        text(ConfigKeys.TEST_HEADER_MARKER, "Test case line",
                "Start of what a tester prints before each test case.");
        choice(ConfigKeys.CSV_LINE_ENDING, "CSV line ending",
                "Keep CRLF unless your tools need LF.", LINE_ENDINGS)
                .showChoicesAs(LINE_ENDING_NAMES);
        text(ConfigKeys.CSV_ID_PREFIX, "CSV ID prefix",
                "In front of the OrgDefinedId and Username values.");
        text(ConfigKeys.CSV_END_OF_LINE_MARKER, "CSV end-of-line value",
                "Value of the End-of-Line Indicator column.");
        toggle(ConfigKeys.CSV_EXTRA_COLUMNS, "Question marks and remarks",
                "Adds a column for each question's mark and a Remarks "
                + "column to the score sheet. Off keeps the provided "
                + "format.");
    }

    // Starts a new group with a card and an entry in the list.
    private void startGroup(String title) {
        SettingsGroup group = new SettingsGroup(title);
        group.getIndexItem().setOnMouseClicked(event -> jumpTo(group));
        groups.add(group);
        index.getChildren().add(group.getIndexItem());
        cards.getChildren().add(group.getCard());
        building = group;
    }

    // Adds a wide text setting to the group being built.
    private void text(String key, String name, String description) {
        add(new SettingField(getWindow(), key, name, description,
                SettingKind.TEXT));
    }

    // Adds a file setting to the group being built.
    private void file(String key, String name, String description) {
        add(new SettingField(getWindow(), key, name, description,
                SettingKind.FILE));
    }

    // Adds a folder setting to the group being built.
    private void folder(String key, String name, String description) {
        add(new SettingField(getWindow(), key, name, description,
                SettingKind.FOLDER));
    }

    // Adds an on and off setting to the group being built.
    private void toggle(String key, String name, String description) {
        add(new SettingField(getWindow(), key, name, description,
                SettingKind.SWITCH));
    }

    // Adds a setting with fixed values to the group being built, and
    // returns it so the values can be given names.
    private SettingField choice(String key, String name,
            String description, String[] values) {
        SettingField field = new SettingField(getWindow(), key, name,
                description, SettingKind.CHOICE, values);
        add(field);
        return field;
    }

    // Adds a number setting as a list of values, each shown with a word
    // after it, such as "10 seconds" or "50%".
    private SettingField listed(String key, String name, String description,
            String[] values, String after) {
        String[] names = new String[values.length];
        for (int index = 0; index < values.length; index++) {
            names[index] = values[index] + after;
        }
        SettingField setting = choice(key, name, description, values);
        setting.showChoicesAs(names);
        return setting;
    }

    // Names large numbers with thousands separators, such as
    // "100,000 characters".
    private static String[] groupedNames(String[] values, String after) {
        String[] names = new String[values.length];
        for (int index = 0; index < values.length; index++) {
            long number = Long.parseLong(values[index]);
            names[index] = String.format(THOUSANDS_FORMAT, number) + after;
        }
        return names;
    }

    // Names each minimum as a number of lines, marking the recommended
    // one, such as "40 lines (recommended)".
    private static String[] minLineNames() {
        String[] names = new String[MIN_LINES.length];
        for (int index = 0; index < MIN_LINES.length; index++) {
            names[index] = MIN_LINES[index] + " lines";
            if (MIN_LINES[index].equals(RECOMMENDED_MIN_LINES)) {
                names[index] += " (recommended)";
            }
        }
        return names;
    }

    // Names each similarity as a percentage, such as "80% alike".
    private static String[] similarityNames() {
        String[] names = new String[SIMILARITIES.length];
        for (int index = 0; index < SIMILARITIES.length; index++) {
            double share = Double.parseDouble(SIMILARITIES[index]);
            long percent = Math.round(share * PERCENT);
            names[index] = percent + "% alike";
        }
        return names;
    }

    // Lists how many students may be graded at once: 1, 2, 4 and so on,
    // up to the number of processors, which is always offered.
    private static String[] threadChoices() {
        int processors = Runtime.getRuntime().availableProcessors();
        List<String> choices = new ArrayList<>();
        for (int step : THREAD_STEPS) {
            if (step <= processors) {
                choices.add(String.valueOf(step));
            }
        }
        if (!choices.contains(String.valueOf(processors))) {
            choices.add(String.valueOf(processors));
        }
        String[] counts = new String[choices.size()];
        for (int index = 0; index < counts.length; index++) {
            counts[index] = choices.get(index);
        }
        return counts;
    }

    // Names each count of students, marking one and the processor count.
    private static String[] threadNames(String[] counts) {
        int processors = Runtime.getRuntime().availableProcessors();
        String[] names = new String[counts.length];
        for (int index = 0; index < counts.length; index++) {
            names[index] = counts[index];
            if (ONE_AT_A_TIME.equals(counts[index])) {
                names[index] = "1 (one at a time)";
            } else if (String.valueOf(processors).equals(counts[index])) {
                names[index] = counts[index] + " (one per processor)";
            }
        }
        return names;
    }

    // Puts a setting in the group being built.
    private void add(SettingField field) {
        fields.add(field);
        building.addRow(field.getRow(), field);
    }

    // Adds the row that names the model answers these checks use: the
    // folder of the assessment in use, which is set under Assessment.
    private void addModelAnswersUsedRow() {
        VBox names = nameColumn("Model answers used", "Each assessment "
                + "has its own folder. These checks use the one of the "
                + "assessment in use; the switches below are the same for "
                + "every assessment.");
        modelAnswersUsed.getStyleClass().add("model-answers-used");
        modelAnswersUsed.setWrapText(true);
        modelAnswersUsed.setMinWidth(0);
        modelAnswersUsed.setMaxWidth(Double.MAX_VALUE);
        VBox value = new VBox(LINE_GAP, modelAnswersUsed,
                modelAnswersLink);
        value.setAlignment(Pos.TOP_LEFT);
        HBox.setHgrow(value, Priority.ALWAYS);
        HBox row = new HBox(SettingField.COLUMN_GAP, names, value);
        row.setAlignment(Pos.TOP_LEFT);
        row.getStyleClass().add("setting-row");
        building.addRow(row, null);
        building.setQuestionsText("model answers used model answer "
                + "checks folder assessment in use tester strength "
                + "random inputs");
        fillModelAnswersUsed();
    }

    // Names the assessment in use and its model answers folder, or says
    // the checks are skipped when it has none. Called whenever the
    // module or the assessment shown changes.
    private void fillModelAnswersUsed() {
        String name = inUseName();
        String folder = getBackend().getSetting(
                ConfigKeys.MODEL_ANSWERS_DIR).trim();
        if (name.isEmpty()) {
            modelAnswersUsed.setText("No assessment is in use, so these "
                    + "checks are skipped.");
            modelAnswersLink.setText("Choose one in Assessment");
            Parts.tip(modelAnswersUsed, "Use an assessment under "
                    + "Assessment to grade with it.");
        } else if (folder.isEmpty()) {
            modelAnswersUsed.setText(name + " has no model answers folder, "
                    + "so these checks are skipped.");
            modelAnswersLink.setText("Set one in Assessment");
            Parts.tip(modelAnswersUsed, "Choose " + name + "'s model "
                    + "answers folder under Assessment to run these "
                    + "checks.");
        } else {
            modelAnswersUsed.setText("Uses " + name + "'s model answers: "
                    + shortPath(folder));
            modelAnswersLink.setText("Change in Assessment");
            Parts.tip(modelAnswersUsed, folder);
        }
    }

    // Returns the last two parts of a path, such as LabTest/Answers, for
    // a short line.
    private static String shortPath(String path) {
        int forward = path.lastIndexOf(FORWARD_SLASH);
        int back = path.lastIndexOf(BACKSLASH);
        int slash = Math.max(forward, back);
        if (slash <= 0) {
            return path;
        }
        String parent = path.substring(0, slash);
        String parentName = fileName(parent);
        if (parentName.isEmpty()) {
            return path;
        }
        return parentName + FORWARD_SLASH + fileName(path);
    }

    // Adds the row that lists each section with its score sheet.
    private void addScoreSheetRows() {
        VBox names = nameColumn("Score sheet of each section",
                "The class list exported from eLearn, for each section of "
                + "the module chosen under Course. Each assessment keeps "
                + "its own; one chosen on Set up is kept here too.");
        HBox.setHgrow(scoreSheetList, Priority.ALWAYS);
        HBox row = new HBox(SettingField.COLUMN_GAP, names, scoreSheetList);
        row.setAlignment(Pos.TOP_LEFT);
        row.getStyleClass().add("setting-row");
        building.addRow(row, null);
        building.setQuestionsText("score sheets score sheet class list "
                + "class lists elearn csv section sections assessment in "
                + "use");
        fillScoreSheets();
    }

    // Lists each section of the chosen module with its score sheet.
    private void fillScoreSheets() {
        scoreSheetList.getChildren().clear();
        String sheets = getBackend().getSetting(
                ConfigKeys.SCORE_SHEET_BY_SECTION);
        String fallback = getBackend().getSetting(
                ConfigKeys.SCORE_SHEET_FILE).trim();
        boolean editable = getBackend().supports(Capability.SETTINGS)
                && !getBackend().isDemo();
        String module = moduleList.getValue();
        List<String> sections = new ArrayList<>();
        if (module != null) {
            sections = getBackend().getSectionCodes(module);
        }
        for (String section : sections) {
            HBox line = scoreSheetLine(module, section, sheets, fallback,
                    editable);
            scoreSheetList.getChildren().add(line);
        }
        if (scoreSheetList.getChildren().isEmpty()) {
            Label none = Parts.muted("Add a section to " + moduleName()
                    + " under Course first.");
            none.getStyleClass().add("small-text");
            scoreSheetList.getChildren().add(none);
        }
    }

    // Builds the line of one section: its name, its score sheet and the
    // links that choose or clear it.
    private HBox scoreSheetLine(String module, String section,
            String sheets, String fallback, boolean editable) {
        String who = ExportFolders.keyOf(module, section);
        String own = ExportFolders.exact(sheets, who);
        Label whoLabel = Parts.strong(module + SHOWN_SEPARATOR + section);
        whoLabel.setMinWidth(Label.USE_PREF_SIZE);
        String shown = fileName(own);
        String fullPath = own;
        String chooseWord = "Change";
        if (own.isEmpty()) {
            fullPath = fallback;
            chooseWord = "Choose";
            if (fallback.isEmpty()) {
                shown = "Not chosen yet";
            } else {
                shown = "Not chosen; uses " + fileName(fallback);
            }
        }
        Label file = Parts.muted(shown);
        file.getStyleClass().add("small-text");
        file.setMinWidth(0);
        Parts.tip(file, fullPath);
        HBox line = Parts.row(Parts.GAP, new Node[] {whoLabel});
        // The section chosen on Home and Set up is marked
        boolean chosenNow = module.equals(getState().getModuleCode())
                && section.equals(getState().getSectionCode());
        if (chosenNow) {
            line.getChildren().add(Parts.pill("Chosen", Tone.NEUTRAL));
        }
        line.getChildren().addAll(file, Parts.grow());
        Hyperlink choose = Parts.link(chooseWord,
                () -> chooseScoreSheet(who));
        choose.setDisable(!editable);
        line.getChildren().add(choose);
        if (!own.isEmpty()) {
            Hyperlink clear = Parts.link("Clear",
                    () -> setScoreSheet(who, null));
            clear.setDisable(!editable);
            line.getChildren().add(clear);
        }
        line.getStyleClass().add("export-folder-line");
        return line;
    }

    // Returns the last part of a path, for a short line.
    private static String fileName(String path) {
        int forward = path.lastIndexOf(FORWARD_SLASH);
        int back = path.lastIndexOf(BACKSLASH);
        int slash = Math.max(forward, back);
        if (slash < 0) {
            return path;
        }
        int nameStart = slash + 1;
        return path.substring(nameStart);
    }

    // Asks for a section's score sheet and keeps it.
    private void chooseScoreSheet(String who) {
        FileChooser chooser = new FileChooser();
        String shownWho = who.replace(KEY_SEPARATOR, SHOWN_SEPARATOR);
        chooser.setTitle("Choose the score sheet of " + shownWho);
        chooser.getExtensionFilters().add(new FileChooser.ExtensionFilter(
                "CSV files", CSV_PATTERN));
        Choosers.prepare(chooser);
        File picked = chooser.showOpenDialog(getWindow().getWindow());
        Choosers.remember(picked);
        if (picked != null) {
            setScoreSheet(who, Parts.configPath(picked.toPath()));
        }
    }

    // Keeps or clears a section's score sheet, with Undo.
    private void setScoreSheet(String who, String path) {
        String previous = ExportFolders.exact(getBackend().getSetting(
                ConfigKeys.SCORE_SHEET_BY_SECTION), who);
        ActionResult result = putScoreSheet(who, path);
        if (!result.isDone()) {
            getWindow().notify(result.getMessage());
            return;
        }
        fillScoreSheets();
        String section = who.replace(KEY_SEPARATOR, SHOWN_SEPARATOR);
        String message = "Cleared the score sheet of " + section + ".";
        if (path != null) {
            message = "Kept " + fileName(path) + " for " + section + ".";
        }
        getWindow().notify(message, "Undo", () -> undoScoreSheet(who,
                previous));
    }

    // Puts back the score sheet one section had, leaving the others as
    // they are now.
    private void undoScoreSheet(String who, String previous) {
        String path = null;
        if (!previous.isEmpty()) {
            path = previous;
        }
        ActionResult result = putScoreSheet(who, path);
        if (!result.isDone()) {
            getWindow().notify(result.getMessage());
        }
        fillScoreSheets();
    }

    // Saves one section's score sheet, or clears it when the path is
    // null.
    private ActionResult putScoreSheet(String who, String path) {
        String now = getBackend().getSetting(
                ConfigKeys.SCORE_SHEET_BY_SECTION);
        String value = ExportFolders.remove(now, who);
        if (path != null) {
            value = ExportFolders.put(now, who, path);
        }
        Map<String, String> changes = new HashMap<>();
        changes.put(ConfigKeys.SCORE_SHEET_BY_SECTION, value);
        return getBackend().saveSettings(changes);
    }

    // Adds the row that lists the modules and sections with their own
    // export folder, with a button that adds one.
    private void addExportFolderRows() {
        VBox names = nameColumn("Exports for one module or section",
                "The module chosen under Course, or one of its sections, "
                + "can save its CSV, JSON and reports in its own folder. A "
                + "section's folder wins over its module's.");
        Button add = Parts.small("Add a folder", () -> openExportFolder(
                null, null));
        add.setGraphic(Parts.icon(Parts.PLUS, SEARCH_ICON_SIZE));
        add.setDisable(!getBackend().supports(Capability.SETTINGS));
        VBox controls = new VBox(Parts.GAP_SMALL, exportFolderList, add);
        HBox.setHgrow(controls, Priority.ALWAYS);
        HBox row = new HBox(SettingField.COLUMN_GAP, names, controls);
        row.setAlignment(Pos.TOP_LEFT);
        row.getStyleClass().add("setting-row");
        building.addRow(row, null);
        building.setQuestionsText("export folders export folder exports "
                + "csv json reports module section");
        fillExportFolders();
    }

    // Lists the chosen module and its sections that have their own
    // export folder.
    private void fillExportFolders() {
        exportFolderList.getChildren().clear();
        List<String[]> entries = moduleEntries(ExportFolders.list(
                getBackend().getSetting(ConfigKeys.OUTPUT_DIR_BY_SECTION)));
        if (entries.isEmpty()) {
            Label none = Parts.muted("None yet. " + moduleName() + " and "
                    + "its sections use the folder in Files and folders.");
            none.getStyleClass().add("small-text");
            exportFolderList.getChildren().add(none);
            return;
        }
        for (String[] entry : entries) {
            String who = entry[WHO_PART];
            String path = entry[PATH_PART];
            Label whoLabel = Parts.strong(who.replace(KEY_SEPARATOR,
                    SHOWN_SEPARATOR));
            whoLabel.setMinWidth(Label.USE_PREF_SIZE);
            Label folder = Parts.muted(path);
            folder.getStyleClass().add("small-text");
            folder.setMinWidth(0);
            Parts.tip(folder, path);
            String[] parts = who.split(KEY_SEPARATOR, KEY_PARTS);
            String module = parts[MODULE_PART];
            String section = sectionOf(who);
            Hyperlink change = Parts.link("Change",
                    () -> openExportFolder(module, section));
            Hyperlink remove = Parts.link("Remove",
                    () -> removeExportFolder(who));
            HBox line = Parts.row(Parts.GAP, new Node[] {whoLabel, folder,
                Parts.grow(), change, remove});
            line.getStyleClass().add("export-folder-line");
            exportFolderList.getChildren().add(line);
        }
    }

    // Returns the export folder entries of the chosen module and its
    // sections.
    private List<String[]> moduleEntries(List<String[]> entries) {
        List<String[]> own = new ArrayList<>();
        String module = moduleList.getValue();
        for (String[] entry : entries) {
            String[] parts = entry[WHO_PART].split(KEY_SEPARATOR, KEY_PARTS);
            if (parts[MODULE_PART].equalsIgnoreCase(module)) {
                own.add(entry);
            }
        }
        return own;
    }

    // Returns the chosen module's code, or words for it when there is
    // none.
    private String moduleName() {
        if (moduleList.getValue() == null) {
            return "The module";
        }
        return moduleList.getValue();
    }

    // Opens the sheet that gives a module or section its own export
    // folder; with a module given, it changes that one's folder.
    private void openExportFolder(String module, String section) {
        ComboBox<String> modules = folderModules(module);
        ComboBox<String> sections = new ComboBox<>();
        fillFolderSections(modules, sections);
        if (section != null) {
            sections.setValue(section);
        } else {
            sections.setValue(WHOLE_MODULE);
        }
        modules.setOnAction(event -> folderModuleChosen(modules, sections));
        modules.setPrefWidth(PICKER_WIDTH);
        sections.setPrefWidth(PICKER_WIDTH);

        TextField folder = new TextField();
        folder.setPromptText("Such as workspace/g1-exports");
        if (module != null) {
            String folders = getBackend().getSetting(
                    ConfigKeys.OUTPUT_DIR_BY_SECTION);
            folder.setText(ExportFolders.resolve("", folders, module,
                    section));
        }
        HBox.setHgrow(folder, Priority.ALWAYS);
        Button choose = Parts.small("Choose",
                () -> chooseExportFolder(folder));
        choose.setDisable(getBackend().isDemo());
        Label problem = Parts.muted("");
        problem.getStyleClass().add("field-problem");
        problem.setWrapText(true);
        problem.setVisible(false);
        problem.setManaged(false);

        Button cancel = Parts.secondary("Cancel",
                () -> getWindow().closeSheet());
        Button save = Parts.primary("Save folder");
        // A changed entry moved to another module or section replaces
        // the original, so the old one does not stay behind
        String original = keyOrNull(module, section);
        String title = "Add an export folder";
        if (module != null) {
            title = "Change the export folder";
        }
        save.setOnAction(event -> saveExportFolderFrom(original, modules,
                sections, folder, problem));
        HBox folderLine = Parts.row(Parts.GAP_SMALL,
                new Node[] {folder, choose});
        HBox buttons = Parts.row(Parts.GAP,
                new Node[] {Parts.grow(), cancel, save});
        VBox body = new VBox(Parts.GAP,
                Parts.title(title),
                Parts.subtitle("Exports of this module or section are saved "
                + "here instead of the folder for everyone."),
                sheetField("Module", modules),
                sheetField("Section", sections),
                sheetField("Folder", folderLine),
                problem, buttons);
        body.setMaxWidth(Parts.TEXT_WIDTH);
        getWindow().showSheet(body);
    }

    // Builds the module list of the export folder sheet, with the given
    // module chosen, else the one chosen under Course, else the first.
    private ComboBox<String> folderModules(String module) {
        ComboBox<String> modules = new ComboBox<>();
        modules.getItems().setAll(getBackend().getModuleCodes());
        String chosen = moduleList.getValue();
        if (module != null) {
            modules.setValue(module);
        } else if (chosen != null && modules.getItems().contains(chosen)) {
            modules.setValue(chosen);
        } else if (!modules.getItems().isEmpty()) {
            modules.setValue(modules.getItems().get(0));
        }
        return modules;
    }

    // Puts a small label above a control, for a sheet.
    private VBox sheetField(String label, Node control) {
        Label name = Parts.muted(label);
        name.getStyleClass().add("field-label");
        return new VBox(LINE_GAP, name, control);
    }

    // Lists the chosen module's sections in the export folder sheet,
    // after the choice of the whole module.
    private void fillFolderSections(ComboBox<String> modules,
            ComboBox<String> sections) {
        sections.getItems().setAll(WHOLE_MODULE);
        if (modules.getValue() != null) {
            sections.getItems().addAll(getBackend().getSectionCodes(
                    modules.getValue()));
        }
    }

    // Follows a new module in the export folder sheet: its sections are
    // listed and the whole module is chosen.
    private void folderModuleChosen(ComboBox<String> modules,
            ComboBox<String> sections) {
        fillFolderSections(modules, sections);
        sections.setValue(WHOLE_MODULE);
    }

    // Asks for the folder an export folder entry saves into.
    private void chooseExportFolder(TextField folder) {
        DirectoryChooser chooser = new DirectoryChooser();
        chooser.setTitle("Choose where these exports are saved");
        Choosers.prepare(chooser);
        File picked = chooser.showDialog(getWindow().getWindow());
        Choosers.remember(picked);
        if (picked != null) {
            folder.setText(Parts.configPath(picked.toPath()));
        }
    }

    // The section in "CS102/G1", or null for a whole module "CS102".
    private static String sectionOf(String who) {
        String[] parts = who.split(KEY_SEPARATOR, KEY_PARTS);
        if (parts.length < KEY_PARTS) {
            return null;
        }
        return parts[SECTION_PART];
    }

    // Who an export folder is for, such as "CS102/G1", or null when no
    // module is given.
    private static String keyOrNull(String module, String section) {
        if (module == null) {
            return null;
        }
        return ExportFolders.keyOf(module, sectionOrNull(section));
    }

    // The section an export folder is for, or null for the whole module.
    private static String sectionOrNull(String section) {
        if (WHOLE_MODULE.equals(section)) {
            return null;
        }
        return section;
    }

    // Saves the export folder typed in the sheet's fields.
    private void saveExportFolderFrom(String original,
            ComboBox<String> modules, ComboBox<String> sections,
            TextField folder, Label problem) {
        saveExportFolder(original, modules.getValue(), sections.getValue(),
                folder.getText().trim(), problem);
    }

    // Checks and saves a module or section's export folder.
    private void saveExportFolder(String original, String module,
            String section, String folder, Label problem) {
        String message = "";
        if (module == null) {
            message = "Choose a module first.";
        } else if (folder.isEmpty()) {
            message = "Choose or type a folder.";
        } else {
            message = getBackend().validateSetting(ConfigKeys.OUTPUT_DIR,
                    folder);
        }
        if (message != null && !message.isEmpty()) {
            showFolderProblem(problem, message);
            return;
        }
        String who = ExportFolders.keyOf(module, sectionOrNull(section));
        String folders = getBackend().getSetting(
                ConfigKeys.OUTPUT_DIR_BY_SECTION);
        if (original != null && !original.equalsIgnoreCase(who)) {
            folders = ExportFolders.remove(folders, original);
        }
        String updated = ExportFolders.put(folders, who, folder);
        Map<String, String> changes = new HashMap<>();
        changes.put(ConfigKeys.OUTPUT_DIR_BY_SECTION, updated);
        ActionResult result = getBackend().saveSettings(changes);
        if (!result.isDone()) {
            showFolderProblem(problem, result.getMessage());
            return;
        }
        String shownWho = who.replace(KEY_SEPARATOR, SHOWN_SEPARATOR);
        getWindow().closeSheet();
        getWindow().notify("Exports of " + shownWho + " are saved in "
                + folder + ".");
        fillExportFolders();
    }

    // Shows why an export folder cannot be saved under the sheet's
    // fields, calling the setting "The folder".
    private void showFolderProblem(Label problem, String message) {
        String quotedKey = QUOTE + ConfigKeys.OUTPUT_DIR + QUOTE;
        problem.setText(message.replace(quotedKey, "The folder"));
        problem.setVisible(true);
        problem.setManaged(true);
    }

    // Removes a module or section's own export folder, with Undo.
    private void removeExportFolder(String who) {
        String before = getBackend().getSetting(
                ConfigKeys.OUTPUT_DIR_BY_SECTION);
        Map<String, String> changes = new HashMap<>();
        changes.put(ConfigKeys.OUTPUT_DIR_BY_SECTION,
                ExportFolders.remove(before, who));
        ActionResult result = getBackend().saveSettings(changes);
        if (!result.isDone()) {
            getWindow().notify(result.getMessage());
            return;
        }
        fillExportFolders();
        String module = who.split(KEY_SEPARATOR)[MODULE_PART];
        String folder = ExportFolders.resolve("", before, module,
                sectionOf(who));
        String shownWho = who.replace(KEY_SEPARATOR, SHOWN_SEPARATOR);
        Supplier<ActionResult> restore = ActivityUndo.record(getBackend(),
                ActivityUndo.FOLDER_REMOVED, shownWho + ": " + folder,
                () -> putExportFolderBack(who, folder));
        getWindow().notify(shownWho + " uses the folder for everyone "
                + "again.", "Undo", () -> undoFolderRemoved(restore));
    }

    // Gives a module or section its removed export folder again.
    private ActionResult putExportFolderBack(String who, String folder) {
        String folders = getBackend().getSetting(
                ConfigKeys.OUTPUT_DIR_BY_SECTION);
        Map<String, String> back = new HashMap<>();
        back.put(ConfigKeys.OUTPUT_DIR_BY_SECTION, ExportFolders.put(folders,
                who, folder));
        return getBackend().saveSettings(back);
    }

    // The Undo of a removed export folder.
    private void undoFolderRemoved(Supplier<ActionResult> restore) {
        notifyIfFailed(restore.get());
        fillExportFolders();
    }

    // Adds the Course rows: a dropdown of the modules and one of the
    // chosen module's sections, each with a button that adds one.
    private void addCourseRows() {
        moduleList.setPrefWidth(PICKER_WIDTH);
        moduleList.setAccessibleText("Modules");
        moduleList.setOnAction(event -> moduleChosen());
        boolean editable = getBackend().supports(Capability.SETTINGS);
        Button addModule = Parts.small("Add module",
                () -> openAddModule());
        Button addSection = Parts.small("Add section",
                () -> openAddSection());
        addModule.setGraphic(Parts.icon(Parts.PLUS, SEARCH_ICON_SIZE));
        addSection.setGraphic(Parts.icon(Parts.PLUS, SEARCH_ICON_SIZE));
        addModule.setDisable(!editable);
        addSection.setDisable(!editable);
        deleteModule.getStyleClass().add("danger-link");
        editableCourse = editable;
        HBox moduleControls = Parts.row(Parts.GAP_SMALL, new Node[] {
            moduleList, addModule, deleteModule});
        building.addRow(courseRow("Modules", "The courses you teach. The "
                + "one chosen here is the one Home and Set up show; the "
                + "sections, score sheets, export folders and assessments "
                + "below are its own.", moduleControls), null);
        sectionChips.setHgap(Parts.GAP_SMALL);
        sectionChips.setVgap(Parts.GAP_SMALL);
        sectionChips.setPrefWrapLength(SECTION_WRAP);
        VBox sectionControls = new VBox(Parts.GAP_SMALL, sectionChips,
                addSection);
        building.addRow(courseRow("Sections", "The class groups of the "
                + "module chosen above: those every module has, and any "
                + "added for this module only.", sectionControls), null);
        building.setQuestionsText("course modules module sections section "
                + "add module add section class groups");
        fillCourse(getState().getModuleCode());
    }

    // Builds one Course row: the name and help on the left, then the
    // dropdown and its Add button.
    private HBox courseRow(String title, String help, Node controls) {
        VBox names = nameColumn(title, help);
        HBox row = new HBox(SettingField.COLUMN_GAP, names, controls);
        row.setAlignment(Pos.CENTER_LEFT);
        row.getStyleClass().add("setting-row");
        return row;
    }

    // Builds the name column of a row that is not a single setting: the
    // name in bold and the help under it, as wide as a setting's name.
    private VBox nameColumn(String title, String help) {
        Label name = Parts.strong(title);
        Label about = Parts.muted(help);
        about.getStyleClass().add("small-text");
        about.setWrapText(true);
        VBox names = new VBox(LINE_GAP, name, about);
        names.setMinWidth(SettingField.NAME_WIDTH);
        names.setPrefWidth(SettingField.NAME_WIDTH);
        names.setMaxWidth(SettingField.NAME_WIDTH);
        return names;
    }

    // Fills the module dropdown, choosing a module, then shows its
    // sections, score sheets and export folders. Its assessments are
    // shown again when the module is not the one shown before.
    private void fillCourse(String chosen) {
        List<String> modules = getBackend().getModuleCodes();
        fillingCourse = true;
        moduleList.getItems().setAll(modules);
        String module = chosen;
        if (module == null || !modules.contains(module)) {
            module = null;
            if (!modules.isEmpty()) {
                module = modules.get(0);
            }
        }
        moduleList.setValue(module);
        fillingCourse = false;
        fillScoreSheets();
        fillExportFolders();
        boolean otherModule = !Objects.equals(module, shownModule);
        shownModule = module;
        // The form is built after the Course rows, so it is empty at first
        if (otherModule && !assessmentHolder.getChildren().isEmpty()) {
            showAssessment(null);
        }
        // The last module cannot be deleted: every setting needs one
        boolean lastModule = modules.size() <= 1;
        deleteModule.setDisable(!editableCourse || lastModule);
        if (lastModule) {
            Parts.tip(deleteModule, "Add another module first; GradeZZY "
                    + "needs at least one.");
        } else {
            Parts.tip(deleteModule, "Delete the module chosen. Results "
                    + "already graded are kept in History.");
        }
        fillSections();
        refreshScope();
    }

    // Follows a pick in the module dropdown: its parts are shown here
    // only. The module chosen on Home and Set up stays as it was.
    private void moduleChosen() {
        String chosen = moduleList.getValue();
        if (fillingCourse || chosen == null) {
            return;
        }
        fillCourse(chosen);
    }

    // Grades with an assessment from now on, as Use this one asks, and
    // says so. The module shown here becomes the one chosen on Home and
    // Set up too, so the assessment in use is always one of its own.
    private void useFromSettings(String name) {
        if (!getWindow().useAssessment(name)) {
            return;
        }
        if (shownModule != null
                && !shownModule.equals(getState().getModuleCode())) {
            getWindow().chooseSection(shownModule,
                    getState().getSectionCode());
        }
        notifyWorkingOn();
    }

    // Says which module and assessment every tab now works on.
    private void notifyWorkingOn() {
        getWindow().notify("Now working on " + getState().getModuleCode()
                + SHOWN_SEPARATOR + inUseName() + " everywhere.");
    }

    // Makes a module the one chosen on Home and Set up, and grades one of
    // its assessments when the one in use is not its own, as Home does.
    // Returns true when the assessment in use was switched.
    private boolean followModule(String module) {
        if (module == null || module.equals(getState().getModuleCode())) {
            return false;
        }
        getWindow().chooseSection(module, getState().getSectionCode());
        // A page built again opens the module's own assessment
        getState().setSettingsAssessment(null);
        String switchTo = assessmentSwitchedBy(module);
        if (switchTo == null) {
            return false;
        }
        return getWindow().useAssessment(switchTo);
    }

    // Returns the module's first assessment when the one in use is not
    // one of its own, so choosing the module switches to it, or null
    // when nothing would switch. Grading reads the assessment from the
    // settings, so nothing switches while any section grades.
    private String assessmentSwitchedBy(String module) {
        List<SavedAssessment> listed = moduleAssessments(module);
        if (getState().isAnyGrading() || listed.isEmpty()) {
            return null;
        }
        String inUse = inUseName();
        for (SavedAssessment saved : listed) {
            if (saved.getName().equalsIgnoreCase(inUse)) {
                return null;
            }
        }
        return listed.get(0).getName();
    }

    // Returns the saved assessments listed for a module, as on Home.
    private List<SavedAssessment> moduleAssessments(String module) {
        String ownOnlyModules = getBackend().getSetting(
                ConfigKeys.OWN_SECTIONS_ONLY_MODULES);
        boolean ownOnly = module != null
                && ModuleSections.hasOwnOnly(ownOnlyModules, module);
        return SavedAssessment.listedFor(getBackend().listAssessments(),
                module, ownOnly);
    }

    // Returns the name of the assessment in use, or "" when there is
    // none.
    private String inUseName() {
        Assessment inUse = getBackend().getAssessment();
        if (inUse == null) {
            return "";
        }
        return inUse.getName();
    }

    // Shows the chosen module's sections as chips, each with a button
    // that deletes it.
    private void fillSections() {
        sectionChips.getChildren().clear();
        String module = moduleList.getValue();
        List<String> sections = new ArrayList<>();
        if (module != null) {
            sections = getBackend().getSectionCodes(module);
        }
        if (sections.isEmpty()) {
            Label none = Parts.muted("No sections yet.");
            none.getStyleClass().add("small-text");
            sectionChips.getChildren().add(none);
            return;
        }
        String byModule = getBackend().getSetting(
                ConfigKeys.MODULE_SECTION_CODES);
        for (String code : sections) {
            sectionChips.getChildren().add(sectionChip(module, code,
                    byModule));
        }
    }

    // Builds the chip of one section, with the button that deletes it
    // and a tag when it belongs to this module only.
    private HBox sectionChip(String module, String code, String byModule) {
        boolean own = ModuleSections.isOwnSection(byModule, module, code);
        Label name = new Label(code);
        name.getStyleClass().add("section-chip-name");
        Button delete = new Button();
        delete.setGraphic(Parts.icon(Parts.CLOSE, CHIP_ICON_SIZE));
        delete.getStyleClass().add("section-chip-delete");
        delete.setAccessibleText("Delete section " + code);
        // The last section every module has cannot go: the settings
        // need one
        String refused = CourseSheets.whyNotDelete(getWindow(), module,
                code);
        delete.setDisable(!editableCourse || !refused.isEmpty());
        if (refused.isEmpty()) {
            Parts.tip(delete, "Delete " + code);
        } else {
            Parts.tip(delete, refused);
        }
        delete.setOnAction(event -> askDeleteSection(module, code));
        HBox chip = new HBox(CHIP_GAP, name, delete);
        chip.setAlignment(Pos.CENTER_LEFT);
        chip.getStyleClass().add("section-chip");
        if (own) {
            Parts.tipNode(name, code + " belongs to " + module
                    + " only.");
            Label tag = Parts.muted(module + " only");
            tag.getStyleClass().addAll("small-text", "section-chip-tag");
            chip.getChildren().add(TAG_POSITION, tag);
        } else {
            Parts.tipNode(name, code + " is shared by every module.");
        }
        return chip;
    }

    // Asks before deleting a module.
    private void askDeleteModule(String module) {
        if (module == null) {
            return;
        }
        getWindow().confirm("Delete module " + module + "?", module
                + " is removed from the modules you can pick, with its own "
                + "sections and export folders. Sections every module has "
                + "stay. Results already graded are kept in History.",
                "Delete module", () -> deleteModule(module));
    }

    // Says whether any section of a module is being graded now.
    private boolean isModuleGrading(String module) {
        for (GradingJob job : getState().getRunningJobs()) {
            if (module.equalsIgnoreCase(job.getSection().getModuleCode())) {
                return true;
            }
        }
        return false;
    }

    // Deletes a module with its own sections and folders, then offers
    // Undo.
    private void deleteModule(String module) {
        boolean beingGraded = isModuleGrading(module);
        if (beingGraded) {
            getWindow().notify("Wait until " + module + " has finished "
                    + "grading.");
            return;
        }
        Map<String, String> before = courseValues();
        List<String> kept = codesWithout(before.get(ConfigKeys.MODULE_CODES),
                module);
        Map<String, String> changes = new HashMap<>();
        changes.put(ConfigKeys.MODULE_CODES, TextUtils.join(LIST_SEPARATOR,
                kept));
        String scoreSheetsBefore = getBackend().getSetting(
                ConfigKeys.SCORE_SHEET_BY_SECTION);
        changes.put(ConfigKeys.MODULE_SECTION_CODES,
                ModuleSections.removeModule(before.get(
                ConfigKeys.MODULE_SECTION_CODES), module));
        changes.put(ConfigKeys.OUTPUT_DIR_BY_SECTION,
                ExportFolders.removeModule(before.get(
                ConfigKeys.OUTPUT_DIR_BY_SECTION), module));
        changes.put(ConfigKeys.SCORE_SHEET_BY_SECTION,
                ExportFolders.removeModule(scoreSheetsBefore, module));
        boolean ownOnly = ModuleSections.hasOwnOnly(before.get(
                ConfigKeys.OWN_SECTIONS_ONLY_MODULES), module);
        if (ownOnly) {
            List<String> others = codesWithout(before.get(
                    ConfigKeys.OWN_SECTIONS_ONLY_MODULES), module);
            changes.put(ConfigKeys.OWN_SECTIONS_ONLY_MODULES,
                    TextUtils.join(LIST_SEPARATOR, others));
        }
        ActionResult result = getBackend().saveSettings(changes);
        if (!result.isDone()) {
            getWindow().notify(result.getMessage());
            return;
        }
        // Only once the module is gone, move off it; zip files chosen for
        // it are cleared
        boolean wasChosen = module.equalsIgnoreCase(
                getState().getModuleCode()) && !kept.isEmpty();
        if (wasChosen) {
            followModule(kept.get(0));
        }
        fillCourse(getState().getModuleCode());
        offerModuleUndo(module, before, scoreSheetsBefore, ownOnly);
    }

    // Records a deleted module in the activity log and offers Undo, which
    // puts back what the module had.
    private void offerModuleUndo(String module, Map<String, String> before,
            String scoreSheetsBefore, boolean ownOnly) {
        // What the module had, so it can be put back
        List<String> ownSections = ModuleSections.forModule("", before.get(
                ConfigKeys.MODULE_SECTION_CODES), module);
        List<String[]> folders = entriesOf(before.get(
                ConfigKeys.OUTPUT_DIR_BY_SECTION), module);
        List<String[]> sheets = entriesOf(scoreSheetsBefore, module);
        Supplier<ActionResult> restore = ActivityUndo.record(getBackend(),
                ActivityUndo.MODULE_DELETED, module, () -> restoreModule(
                module, ownSections, folders, sheets, ownOnly));
        getWindow().notify("Deleted module " + module + ".", "Undo",
                () -> undoModuleDeleted(restore, module));
    }

    // Returns the codes of a list setting without one module's code.
    private List<String> codesWithout(String value, String module) {
        List<String> others = new ArrayList<>();
        for (String code : splitCodes(value)) {
            if (!code.equalsIgnoreCase(module)) {
                others.add(code);
            }
        }
        return others;
    }

    // The Undo of a deleted module: it is listed and shown again.
    private void undoModuleDeleted(Supplier<ActionResult> restore,
            String module) {
        notifyIfFailed(restore.get());
        if (getBackend().getModuleCodes().contains(module)) {
            followModule(module);
        }
        fillCourse(getState().getModuleCode());
    }

    // Opens the sheet that adds a module.
    private void openAddModule() {
        CourseSheets.openAddModule(getWindow(), code -> moduleAdded(code));
    }

    // Shows a module just added, which becomes the one chosen.
    private void moduleAdded(String code) {
        followModule(code);
        fillCourse(code);
        getWindow().notify("Added module " + code + ".");
    }

    // Opens the sheet that adds a section to the chosen module.
    private void openAddSection() {
        CourseSheets.openAddSection(getWindow(), moduleList.getValue(),
                code -> fillChosenModule());
    }

    // Shows the chosen module's sections again.
    private void fillChosenModule() {
        fillCourse(moduleList.getValue());
    }

    // Asks before deleting a section, then shows its module again.
    private void askDeleteSection(String module, String code) {
        CourseSheets.askDeleteSection(getWindow(), module, code,
                () -> fillCourse(module));
    }

    // Adds a deleted module back with its own sections and folders,
    // without undoing other changes made since.
    private ActionResult restoreModule(String module,
            List<String> ownSections, List<String[]> folders,
            List<String[]> sheets, boolean ownOnly) {
        Map<String, String> changes = new HashMap<>();
        List<String> modules = splitCodes(getBackend().getSetting(
                ConfigKeys.MODULE_CODES));
        boolean listed = false;
        for (String code : modules) {
            listed = listed || code.equalsIgnoreCase(module);
        }
        if (!listed) {
            modules.add(module);
            changes.put(ConfigKeys.MODULE_CODES, TextUtils.join(
                    LIST_SEPARATOR, modules));
        }
        String byModule = getBackend().getSetting(
                ConfigKeys.MODULE_SECTION_CODES);
        for (String code : ownSections) {
            if (!ModuleSections.isOwnSection(byModule, module, code)) {
                byModule = ModuleSections.add(byModule, module, code);
            }
        }
        if (!ownSections.isEmpty()) {
            changes.put(ConfigKeys.MODULE_SECTION_CODES, byModule);
        }
        putEntriesBack(changes, ConfigKeys.OUTPUT_DIR_BY_SECTION, folders);
        putEntriesBack(changes, ConfigKeys.SCORE_SHEET_BY_SECTION, sheets);
        String ownOnlyValue = getBackend().getSetting(
                ConfigKeys.OWN_SECTIONS_ONLY_MODULES).trim();
        if (ownOnly && !ModuleSections.hasOwnOnly(ownOnlyValue, module)) {
            String ownOnlyList = module;
            if (!ownOnlyValue.isEmpty()) {
                ownOnlyList = ownOnlyValue + LIST_SEPARATOR + module;
            }
            changes.put(ConfigKeys.OWN_SECTIONS_ONLY_MODULES, ownOnlyList);
        }
        if (changes.isEmpty()) {
            return ActionResult.done("Module " + module + " is listed.");
        }
        return getBackend().saveSettings(changes);
    }

    // Adds entries of a per-section list setting back to its value now,
    // and puts the new value in the changes. Nothing happens when there
    // are no entries.
    private void putEntriesBack(Map<String, String> changes, String key,
            List<String[]> entries) {
        if (entries.isEmpty()) {
            return;
        }
        String value = getBackend().getSetting(key);
        for (String[] entry : entries) {
            value = ExportFolders.put(value, entry[WHO_PART],
                    entry[PATH_PART]);
        }
        changes.put(key, value);
    }

    // The per-section entries of a list that belong to one module.
    private static List<String[]> entriesOf(String value, String module) {
        List<String[]> found = new ArrayList<>();
        String lower = module.toLowerCase(Locale.ROOT);
        String sectionStart = lower + KEY_SEPARATOR;
        for (String[] entry : ExportFolders.list(value)) {
            String who = entry[WHO_PART].toLowerCase(Locale.ROOT);
            if (who.equals(lower) || who.startsWith(sectionStart)) {
                found.add(entry);
            }
        }
        return found;
    }

    // Shows why an Undo did not work.
    private void notifyIfFailed(ActionResult result) {
        if (!result.isDone()) {
            getWindow().notify(result.getMessage());
        }
    }

    // Returns the course settings as they are now.
    private Map<String, String> courseValues() {
        Map<String, String> values = new HashMap<>();
        String[] keys = {ConfigKeys.MODULE_CODES, ConfigKeys.SECTION_CODES,
            ConfigKeys.MODULE_SECTION_CODES, ConfigKeys.OUTPUT_DIR_BY_SECTION,
            ConfigKeys.OWN_SECTIONS_ONLY_MODULES};
        for (String key : keys) {
            values.put(key, getBackend().getSetting(key));
        }
        return values;
    }

    // Splits a list setting into its codes.
    private List<String> splitCodes(String value) {
        List<String> codes = new ArrayList<>();
        if (value == null) {
            return codes;
        }
        for (String part : value.split(LIST_SEPARATOR)) {
            if (!part.trim().isEmpty()) {
                codes.add(part.trim());
            }
        }
        return codes;
    }

    /**
     * Scrolls to the assessment part. Used by the developer checks.
     */
    public void showAssessmentGroup() {
        for (SettingsGroup group : groups) {
            boolean holdsForm = assessmentHolder.getParent() != null
                    && isInside(assessmentHolder, group.getCard());
            if (holdsForm) {
                view.applyCss();
                view.layout();
                jumpTo(group);
            }
        }
    }

    // Adds the assessment part: a dropdown of the saved assessments, then
    // the chosen one's files, questions and the lists found in its files,
    // laid out as in the assessment editor, with its own Save.
    private void addAssessmentEditor() {
        assessmentPicker.setPrefWidth(PICKER_WIDTH);
        assessmentPicker.setAccessibleText("Assessment to edit");
        assessmentPicker.setOnAction(event -> assessmentPicked());
        HBox row = new HBox(assessmentHolder);
        HBox.setHgrow(assessmentHolder, Priority.ALWAYS);
        row.getStyleClass().add("setting-row");
        building.addRow(row, null);
        building.setQuestionsText("assessment name questions marks tester "
                + "starter model answers score sheet class files data files "
                + "unrenamed folder names");
        showAssessment(getState().getSettingsAssessment());
    }

    // Shows one of the chosen module's saved assessments in the form, or
    // a new, blank one for NEW_ASSESSMENT. For null, or a name the module
    // does not list, the one in use opens when it is the module's, else
    // the module's first. A module with none shows a note instead.
    private void showAssessment(String name) {
        String module = moduleList.getValue();
        String inUse = inUseName();
        List<SavedAssessment> listed = moduleAssessments(module);
        boolean creating = NEW_ASSESSMENT.equals(name);
        SavedAssessment start = null;
        String originalName = null;
        String picked = NEW_ASSESSMENT;
        if (creating) {
            // Every field starts empty, for the module chosen here
            start = getWindow().newAssessment();
            start.setModuleCode(module);
        } else {
            start = findAssessment(listed, name, inUse);
            if (start == null) {
                showNoAssessment(module, listed);
                return;
            }
            originalName = start.getName();
            picked = originalName;
        }
        getState().setSettingsAssessment(picked);

        fillAssessmentPicker(namesOf(listed), picked);

        HBox pickRow = assessmentPickRow(start, originalName, inUse,
                creating);
        assessmentForm = AssessmentEditor.inline(getWindow(), start,
                originalName, saved -> showAssessment(saved),
                saved -> useFromSettings(saved));
        assessmentHolder.getChildren().setAll(pickRow,
                assessmentForm.getView());
    }

    // Returns the module's assessment with a name, else the one in use
    // when the module lists it, else the module's first, or null when
    // the module has none.
    private SavedAssessment findAssessment(List<SavedAssessment> listed,
            String name, String inUse) {
        SavedAssessment named = null;
        SavedAssessment used = null;
        for (SavedAssessment saved : listed) {
            if (saved.getName().equalsIgnoreCase(name)) {
                named = saved;
            }
            if (saved.getName().equalsIgnoreCase(inUse)) {
                used = saved;
            }
        }
        if (named != null) {
            return named;
        }
        if (used != null) {
            return used;
        }
        if (listed.isEmpty()) {
            return null;
        }
        return listed.get(0);
    }

    // Returns the names of some saved assessments.
    private List<String> namesOf(List<SavedAssessment> listed) {
        List<String> names = new ArrayList<>();
        for (SavedAssessment saved : listed) {
            names.add(saved.getName());
        }
        return names;
    }

    // Shows, in place of the form, that the chosen module has no
    // assessment yet, with a button that starts a blank one.
    private void showNoAssessment(String module,
            List<SavedAssessment> listed) {
        getState().setSettingsAssessment(null);
        fillAssessmentPicker(namesOf(listed), null);
        assessmentPicker.setPromptText("None yet");
        String moduleWords = "this module";
        if (module != null) {
            moduleWords = module;
        }
        Label heading = Parts.strong("No assessment for " + moduleWords
                + " yet");
        Label about = Parts.muted("Each lab test or exam has its own "
                + "tester and starter folders. A new one starts blank, so "
                + "you choose them for " + moduleWords + ".");
        about.getStyleClass().add("small-text");
        about.setWrapText(true);
        Button create = Parts.primary("New assessment");
        create.setGraphic(Parts.icon(Parts.PLUS, SEARCH_ICON_SIZE));
        create.setOnAction(event -> showAssessment(NEW_ASSESSMENT));
        create.setDisable(!getBackend().supports(Capability.SETTINGS)
                || module == null);
        VBox empty = new VBox(Parts.GAP_SMALL, heading, about, create);
        empty.getStyleClass().add("settings-assessment-empty");
        HBox pickRow = assessmentPickRow(null, null, "", true);
        assessmentForm = null;
        assessmentHolder.getChildren().setAll(pickRow, empty);
    }

    // Lists the saved assessments in the dropdown, with the line that
    // starts a new one when settings can be changed, and picks one value.
    // No pick is handled while it is filled.
    private void fillAssessmentPicker(List<String> names, String value) {
        pickingAssessment = true;
        assessmentPicker.getItems().setAll(names);
        if (getBackend().supports(Capability.SETTINGS)) {
            assessmentPicker.getItems().add(NEW_ASSESSMENT);
        }
        assessmentPicker.setValue(value);
        pickingAssessment = false;
        refreshScope();
    }

    // Shows the module and the assessment being edited in the bar above
    // the cards and in the tags of the groups.
    private void refreshScope() {
        String picked = assessmentPicker.getValue();
        boolean creating = NEW_ASSESSMENT.equals(picked);
        String editing = picked;
        if (creating) {
            editing = null;
        }
        scope.show(moduleList.getValue(), editing, creating, inUseName());
        fillModelAnswersUsed();
    }

    // Builds the row with the assessment dropdown and, for a saved
    // assessment, its In use pill or Use button and its Delete button.
    private HBox assessmentPickRow(SavedAssessment start,
            String originalName, String inUse, boolean creating) {
        String moduleWords = "the module chosen under Course";
        if (moduleList.getValue() != null) {
            moduleWords = moduleList.getValue();
        }
        VBox pickWords = nameColumn("Assessment to edit", "The lab tests "
                + "and exams of " + moduleWords + ", each with its own "
                + "files and questions. The one in use is graded on Set "
                + "up.");
        HBox pickRow = new HBox(SettingField.COLUMN_GAP, pickWords,
                assessmentPicker);
        pickRow.setAlignment(Pos.CENTER_LEFT);
        if (creating) {
            return pickRow;
        }
        boolean inUseNow = originalName.equalsIgnoreCase(inUse);
        if (inUseNow) {
            Label used = Parts.pill("In use", Tone.GOOD);
            Parts.tipNode(used, "Set up grades with " + originalName
                    + ".");
            pickRow.getChildren().add(used);
        } else {
            Label unused = Parts.pill("Not in use", Tone.NEUTRAL);
            Parts.tipNode(unused, "Changes here are saved to "
                    + originalName + " but do not change what Set up "
                    + "grades.");
            pickRow.getChildren().add(unused);
            Button use = Parts.small("Use this one",
                    () -> useFromSettings(originalName));
            Parts.tip(use, "Grade with " + originalName + " from now on.");
            pickRow.getChildren().add(use);
        }
        Button delete = Parts.small("Delete",
                () -> askDeleteAssessment(start));
        delete.getStyleClass().add("danger-link");
        delete.setDisable(inUseNow
                || !getBackend().supports(Capability.SETTINGS));
        if (inUseNow) {
            Parts.tip(delete, "This is the assessment in use. Use "
                    + "another one first, then delete this one.");
        } else {
            Parts.tip(delete, "Delete " + originalName + ". Results "
                    + "graded with it are kept.");
        }
        pickRow.getChildren().add(delete);
        return pickRow;
    }

    // Asks before deleting a saved assessment, then deletes it and
    // offers Undo, which saves it again.
    private void askDeleteAssessment(SavedAssessment saved) {
        String name = saved.getName();
        getWindow().confirm("Delete " + name + "?", "Its questions, "
                + "folders, score sheets and the files chosen for it on Set "
                + "up are cleared. Results already graded with it are kept "
                + "in History.", "Delete assessment",
                () -> deleteAssessment(saved));
    }

    // Deletes a saved assessment, with the files chosen for it on Set up,
    // then offers Undo, which puts it back as it was and shows it again.
    private void deleteAssessment(SavedAssessment saved) {
        String name = saved.getName();
        boolean deleted = getWindow().deleteAssessment(name,
                () -> showAssessment(name));
        if (deleted) {
            showAssessment(null);
        }
    }

    // Lists the chosen module's assessments again, such as after one was
    // added on Home. The one open stays open with its edits; if it is
    // gone, or none was open, the module's own one opens.
    private void refreshAssessmentPicker() {
        String open = assessmentPicker.getValue();
        List<String> names = namesOf(moduleAssessments(
                moduleList.getValue()));
        boolean creating = NEW_ASSESSMENT.equals(open);
        boolean openGone = open == null
                || (!creating && !names.contains(open));
        if (openGone) {
            showAssessment(null);
            return;
        }
        fillAssessmentPicker(names, open);
    }

    // Opens the assessment picked in the dropdown.
    private void assessmentPicked() {
        if (pickingAssessment || assessmentPicker.getValue() == null) {
            return;
        }
        showAssessment(assessmentPicker.getValue());
    }

    // ------------------------------------------------- search, scrolling

    // Shows only the settings that match the search, and only the groups
    // that still hold one.
    private void applySearch() {
        String query = search.getText().trim().toLowerCase(Locale.ROOT);
        boolean any = false;
        for (SettingsGroup group : groups) {
            boolean shown = group.filter(query);
            group.getCard().setVisible(shown);
            group.getCard().setManaged(shown);
            group.getIndexItem().setVisible(shown);
            group.getIndexItem().setManaged(shown);
            if (shown) {
                any = true;
            }
        }
        noMatch.setVisible(!any);
        noMatch.setManaged(!any);
        scrollTo(false);
        followScroll();
    }

    // Scrolls the cards so a group's card is at the top.
    private void jumpTo(SettingsGroup group) {
        double contentHeight = cards.getHeight();
        double viewHeight = cardScroll.getViewportBounds().getHeight();
        double room = contentHeight - viewHeight;
        jumping = true;
        if (room > 0) {
            double share = group.getCard().getBoundsInParent().getMinY() / room;
            double atLeastTop = Math.max(0.0, share);
            double scrolled = Math.min(SCROLL_END, atLeastTop);
            cardScroll.setVvalue(scrolled);
        }
        jumping = false;
        select(group);
    }

    // Highlights the group at the top of the visible cards.
    private void followScroll() {
        if (jumping) {
            return;
        }
        double contentHeight = cards.getHeight();
        double viewHeight = cardScroll.getViewportBounds().getHeight();
        double top = Math.max(0, contentHeight - viewHeight)
                * cardScroll.getVvalue();
        double bottomStart = cardScroll.getVmax() - BOTTOM_SLACK;
        boolean atBottom = cardScroll.getVvalue() >= bottomStart;
        double reachedLine = top + SCROLL_SLACK;
        SettingsGroup found = null;
        for (SettingsGroup group : groups) {
            double cardTop = group.getCard().getBoundsInParent().getMinY();
            boolean reached = found == null || atBottom
                    || cardTop <= reachedLine;
            boolean onGroup = group.getCard().isVisible() && reached;
            if (onGroup) {
                found = group;
            }
        }
        if (found != null) {
            select(found);
        }
    }

    // Marks one group as the current one in the list.
    private void select(SettingsGroup group) {
        if (current != null) {
            current.getIndexItem().getStyleClass().remove("selected");
        }
        current = group;
        group.getIndexItem().getStyleClass().add("selected");
    }

    // ------------------------------------------------------ action bar

    // Updates the count of changes and the Save and Discard buttons.
    private void refreshBar() {
        getWindow().refreshSettingsMark();
        if (!isOnScreen() || !getBackend().supports(Capability.SETTINGS)) {
            return;
        }
        int changed = 0;
        int errors = 0;
        for (SettingField field : fields) {
            if (field.isChanged()) {
                changed++;
                if (!field.isValid()) {
                    errors++;
                }
            }
        }
        boolean valid = errors == 0;
        boolean hasErrors = errors > 0;
        showErrorButton.setVisible(hasErrors);
        showErrorButton.setManaged(hasErrors);
        if (hasErrors) {
            // Says why Save is off, and offers to show the first error
            getActions().setStatus("Fix " + Parts.plural(errors, "error")
                    + " to save");
            getActions().setStatusGraphic(changesMark);
        } else if (changed == 0) {
            getActions().setStatus("No unsaved changes");
            getActions().setStatusGraphic(null);
        } else {
            getActions().setStatus(Parts.plural(changed,
                    "unsaved change"));
            getActions().setStatusGraphic(changesMark);
        }
        discardButton.setDisable(changed == 0);
        getActions().setPrimaryEnabled(changed > 0 && valid);
    }

    // Scrolls to the first changed setting with an error and focuses it.
    private void showFirstError() {
        for (SettingField field : fields) {
            if (field.isChanged() && !field.isValid()) {
                search.setText("");
                for (SettingsGroup group : groups) {
                    if (isInside(field.getRow(), group.getCard())) {
                        jumpTo(group);
                    }
                }
                field.focusControl();
                return;
            }
        }
    }

    // Says whether a node is inside another.
    private boolean isInside(Node node, Node container) {
        Node at = node;
        while (at != null) {
            if (at == container) {
                return true;
            }
            at = at.getParent();
        }
        return false;
    }

    // Saves every changed setting together, in the order they are shown.
    // Returns whether they were saved.
    private boolean save() {
        List<String> keys = new ArrayList<>();
        Map<String, String> changes = new HashMap<>();
        for (SettingField field : fields) {
            if (field.isChanged()) {
                if (!field.isValid()) {
                    getWindow().notify("Fix the settings marked in red "
                            + "first.");
                    return false;
                }
                if (!changes.containsKey(field.getKey())) {
                    keys.add(field.getKey());
                }
                changes.put(field.getKey(), field.getValue());
            }
        }
        if (changes.isEmpty()) {
            return true;
        }
        ActionResult result = getBackend().saveSettings(keys, changes);
        getWindow().notify(result.getMessage());
        if (result.isDone()) {
            for (SettingField field : fields) {
                if (changes.containsKey(field.getKey())) {
                    field.markSaved();
                }
            }
        }
        refreshBar();
        return result.isDone();
    }
}
