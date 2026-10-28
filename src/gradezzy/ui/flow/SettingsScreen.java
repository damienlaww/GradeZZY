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

import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

import javafx.geometry.Insets;
import javafx.geometry.Pos;
import javafx.scene.Node;
import javafx.scene.control.Button;
import javafx.scene.control.Label;
import javafx.scene.control.ScrollPane;
import javafx.scene.layout.HBox;
import javafx.scene.layout.Priority;
import javafx.scene.layout.VBox;
import javafx.scene.shape.Circle;

import atlantafx.base.controls.CustomTextField;

import gradezzy.config.ConfigKeys;
import gradezzy.model.Assessment;
import gradezzy.model.Question;
import gradezzy.ui.ThemeManager;
import gradezzy.ui.backend.ActionResult;
import gradezzy.ui.backend.Capability;

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

    // The width of the questions table
    private static final double QUESTIONS_WIDTH = 420.0;

    // The height of a row of the questions table
    private static final double QUESTION_ROW_HEIGHT = 32.0;

    // Column widths of the questions table
    private static final double QUESTION_WIDTH = 84.0;
    private static final double FOLDER_WIDTH = 110.0;
    private static final double MAX_WIDTH = 64.0;

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
    private final List<Group> groups = new ArrayList<>();

    // Every setting, top to bottom
    private final List<SettingField> fields = new ArrayList<>();

    // The dot shown next to the number of unsaved changes
    private final Circle changesDot = new Circle(DOT_RADIUS);

    // Holds the dot with a little room before the text
    private final HBox changesMark = new HBox(changesDot);

    // The Discard button of the action bar
    private final Button discardButton;

    // The group being built
    private Group building = null;

    // The group highlighted in the list
    private Group current = null;

    // True while the page scrolls because a group was clicked
    private boolean jumping = false;

    /**
     * Creates the Settings tab.
     *
     * <p>Tier: SHOULD.
     *
     * @param window the window
     */
    public SettingsScreen(FlowWindow window) {
        super(window);
        discardButton = Parts.secondary("Discard", () -> discardChanges());
        changesDot.getStyleClass().add("setting-dot");
        changesMark.setAlignment(Pos.CENTER);
        changesMark.setPadding(new Insets(0, DOT_TEXT_GAP, 0, 0));
        addGroups();

        search.setPromptText("Find a setting");
        search.setLeft(Parts.icon(Parts.SEARCH, SEARCH_ICON_SIZE));
        search.setPrefWidth(SEARCH_WIDTH);
        search.setMaxWidth(SEARCH_WIDTH);
        search.textProperty().addListener((observable, before, after)
                -> applySearch());
        HBox headerRow = Parts.row(Parts.GAP, Parts.header("Settings",
                "Everything GradeZZY uses, saved in config.properties."),
                Parts.grow(), search);
        headerRow.getStyleClass().add("settings-header");

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
    }

    @Override
    public Node getView() {
        return view;
    }

    @Override
    public void onShow() {
        ActionBar actions = getActions();
        if (!getBackend().supports(Capability.SETTINGS)) {
            actions.setStatus("Settings cannot be changed here. Edit "
                    + "config.properties instead.");
            return;
        }
        actions.addSecondary(discardButton);
        actions.setPrimary("Save", () -> save(), false);
        refreshBar();
    }

    /**
     * Puts a value in a setting's control without saving it, as if it
     * was typed. Used by the developer checks.
     *
     * <p>Tier: COULD.
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
     * Puts every saved value back in its control.
     *
     * <p>Tier: SHOULD.
     */
    public void discardChanges() {
        for (SettingField field : fields) {
            field.reset();
        }
        refreshBar();
    }

    /**
     * Scrolls the cards to the top or to the bottom.
     *
     * <p>Tier: COULD.
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
    // has a place.
    private void addGroups() {
        startGroup("Course");
        text(ConfigKeys.MODULE_CODES, "Modules",
                "Separated by commas. The first is the default.");
        text(ConfigKeys.SECTION_CODES, "Sections", "Separated by commas.");

        startGroup("Files and folders");
        file(ConfigKeys.SCORE_SHEET_FILE, "Score sheet",
                "The roster, and the template for the export.");
        folder(ConfigKeys.TESTERS_DIR, "Tester files",
                "Testers and the data files they read.");
        folder(ConfigKeys.STARTER_DIR, "Starter files",
                "Used to spot answers left untouched.");
        folder(ConfigKeys.SUBMISSIONS_DIR, "Submissions for the console",
                "Graded by run.sh console.");
        folder(ConfigKeys.WORKING_DIR, "Working copies",
                "Fixes and tests run here. Originals never change.");
        folder(ConfigKeys.OUTPUT_DIR, "Exports saved in",
                "CSV, JSON and reports.");
        folder(ConfigKeys.BACKUP_DIR, "Backups saved in",
                "Copies made by Back up now on Runs.");

        startGroup("Assessment");
        text(ConfigKeys.ASSESSMENT_NAME, "Name", "");
        addQuestions();
        text(ConfigKeys.TESTER_DATA_FILES, "Tester data files",
                "Copied next to each tester. Separated by commas.");
        text(ConfigKeys.PROVIDED_CLASS_FILES, "Provided class files",
                "Given without source; kept when .class files are cleared.");
        text(ConfigKeys.PLACEHOLDER_FOLDER_NAMES, "Unrenamed folder names",
                "Starter folder names a student forgot to rename.");

        startGroup("Checks and fixes");
        toggle(ConfigKeys.AUTO_FIX_ENABLED, "Fix small mistakes",
                "Each fix waits for you to approve or undo it.");
        toggle(ConfigKeys.DETECT_UNTOUCHED_ENABLED, "Flag untouched files",
                "Starter files handed in unchanged. Marks never change.");
        toggle(ConfigKeys.DETECT_EMPTY_ENABLED, "Flag empty answers",
                "Marks never change.");
        toggle(ConfigKeys.DETECT_HARD_CODED_ENABLED,
                "Flag hard-coded answers",
                "Answers that may print the output directly.");

        startGroup("Running code");
        number(ConfigKeys.RUN_TIMEOUT_SECONDS, "Time limit per test run",
                "Seconds. A tester that runs longer is stopped.");
        number(ConfigKeys.COMPILE_TIMEOUT_SECONDS, "Time limit to compile",
                "Seconds, for one question folder.");
        number(ConfigKeys.THREAD_COUNT, "Students graded at once", "");
        text(ConfigKeys.JAVAC_COMMAND, "Compile command",
                "Must be on the PATH.");
        text(ConfigKeys.JAVA_COMMAND, "Run command",
                "Must be on the PATH.");
        number(ConfigKeys.MAX_OUTPUT_CHARACTERS, "Longest output kept",
                "Characters per run, to protect memory.");

        startGroup("Similar code");
        toggle(ConfigKeys.PLAGIARISM_ENABLED, "Look for similar code",
                "Runs after grading; pairs are flagged.");
        number(ConfigKeys.PLAGIARISM_THRESHOLD, "Similarity that counts",
                "From 0 to 1.");

        startGroup("Statistics");
        number(ConfigKeys.PASS_MARK_PERCENT, "Pass mark",
                "Percentage of the maximum mark.");
        number(ConfigKeys.MARK_BAND_WIDTH_PERCENT, "Mark band width",
                "Percentage of the maximum mark.");

        startGroup("Saved data");
        toggle(ConfigKeys.DATABASE_ENABLED, "Save results",
                "Needed for Runs, Resume and the activity log.");
        file(ConfigKeys.DATABASE_FILE, "Database file", "");
        number(ConfigKeys.BACKUP_KEEP_COUNT, "Backups kept",
                "The oldest is deleted after this many.");
        toggle(ConfigKeys.AUDIT_ENABLED, "Keep an activity log",
                "Approvals, undos and exports, with the time.");

        startGroup("Appearance");
        choice(ConfigKeys.UI_THEME, "Theme", "", THEMES).showChoicesAs(
                THEME_NAMES);
        toggle(ConfigKeys.ANIMATIONS_ENABLED, "Subtle animations", "");
        number(ConfigKeys.WINDOW_WIDTH, "Window width",
                "Starting size, in pixels.");
        number(ConfigKeys.WINDOW_HEIGHT, "Window height",
                "Starting size, in pixels.");

        startGroup("Advanced");
        text(ConfigKeys.ZIP_NAME_PREFIX_PATTERN, "Zip name prefix",
                "Pattern of the text before the username in a zip name.");
        text(ConfigKeys.IGNORED_FILE_NAMES, "Ignored file names",
                "Inside a submission. Separated by commas.");
        number(ConfigKeys.MAX_ZIP_SIZE_MB, "Largest unzipped size",
                "Megabytes, for one submission.");
        number(ConfigKeys.MAX_ZIP_ENTRIES, "Most files in one zip", "");
        text(ConfigKeys.HEADER_NAME_LABEL, "Header name label",
                "In front of the student's name in a file's header.");
        text(ConfigKeys.HEADER_EMAIL_LABEL, "Header email label",
                "In front of the student's email in a file's header.");
        number(ConfigKeys.HEADER_SCAN_LINES, "Header lines searched",
                "At the top of each file.");
        text(ConfigKeys.TESTER_CLASS_SUFFIX, "Tester name ending",
                "Added to a question, so Q1a becomes Q1aTester.");
        text(ConfigKeys.PASS_MARKER, "Passed line",
                "What a tester prints when a test case passes.");
        text(ConfigKeys.FAIL_MARKER, "Failed line",
                "Start of what a tester prints when a test case fails.");
        text(ConfigKeys.TEST_HEADER_MARKER, "Test case line",
                "Start of what a tester prints before each test case.");
        text(ConfigKeys.EXPORT_FORMATS, "Export formats",
                "Offered by default. Separated by commas.");
        choice(ConfigKeys.CSV_LINE_ENDING, "CSV line ending",
                "CRLF as provided, or LF.", LINE_ENDINGS);
        text(ConfigKeys.CSV_ID_PREFIX, "CSV ID prefix",
                "In front of the OrgDefinedId and Username values.");
        text(ConfigKeys.CSV_END_OF_LINE_MARKER, "CSV end-of-line value",
                "Value of the End-of-Line Indicator column.");
    }

    // Starts a new group with a card and an entry in the list.
    private void startGroup(String title) {
        Group group = new Group(title);
        group.indexItem.setOnMouseClicked(event -> jumpTo(group));
        groups.add(group);
        index.getChildren().add(group.indexItem);
        cards.getChildren().add(group.card);
        building = group;
    }

    // Adds a wide text setting to the group being built.
    private void text(String key, String name, String description) {
        add(new SettingField(getWindow(), key, name, description,
                SettingField.Kind.TEXT));
    }

    // Adds a short number setting to the group being built.
    private void number(String key, String name, String description) {
        add(new SettingField(getWindow(), key, name, description,
                SettingField.Kind.NUMBER));
    }

    // Adds a file setting to the group being built.
    private void file(String key, String name, String description) {
        add(new SettingField(getWindow(), key, name, description,
                SettingField.Kind.FILE));
    }

    // Adds a folder setting to the group being built.
    private void folder(String key, String name, String description) {
        add(new SettingField(getWindow(), key, name, description,
                SettingField.Kind.FOLDER));
    }

    // Adds an on and off setting to the group being built.
    private void toggle(String key, String name, String description) {
        add(new SettingField(getWindow(), key, name, description,
                SettingField.Kind.SWITCH));
    }

    // Adds a setting with fixed values to the group being built, and
    // returns it so the values can be given names.
    private SettingField choice(String key, String name,
            String description, String[] values) {
        SettingField field = new SettingField(getWindow(), key, name,
                description, SettingField.Kind.CHOICE, values);
        add(field);
        return field;
    }

    // Puts a setting in the group being built.
    private void add(SettingField field) {
        fields.add(field);
        building.addRow(field.getRow(), field);
    }

    // Adds the read-only table of questions to the group being built.
    // The questions are only shown: changing a question means changing
    // several lines together, and the assessment is read at start-up.
    private void addQuestions() {
        Assessment assessment = getBackend().getAssessment();
        PlainTable table = new PlainTable(QUESTION_ROW_HEIGHT);
        table.getStyleClass().add("compact");
        table.addColumn("Question", QUESTION_WIDTH, Pos.CENTER_LEFT);
        table.addColumn("Folder", FOLDER_WIDTH, Pos.CENTER_LEFT);
        table.addColumn("File", PlainTable.GROW, Pos.CENTER_LEFT);
        table.addColumn("Max", MAX_WIDTH, Pos.CENTER_RIGHT);
        for (Question question : assessment.getQuestions()) {
            table.addRow(Parts.strong(question.getId()),
                    new Label(question.getFolderName()),
                    new Label(question.getFileName()),
                    new Label(Parts.mark(question.getMaxScore())));
        }
        if (assessment.getQuestions().isEmpty()) {
            table.showMessage(Parts.muted("No questions are listed."));
        }
        table.setPrefWidth(QUESTIONS_WIDTH);
        table.setMaxWidth(QUESTIONS_WIDTH);

        String description = "Folder, file and maximum mark of each "
                + "question. Total " + Parts.mark(assessment.getMaxTotal())
                + ". Change them in config.properties.";
        Label name = Parts.strong("Questions");
        Label help = Parts.muted(description);
        help.getStyleClass().add("small-text");
        help.setWrapText(true);
        VBox names = new VBox(LINE_GAP, name, help);
        names.setMinWidth(SettingField.NAME_WIDTH);
        names.setPrefWidth(SettingField.NAME_WIDTH);
        names.setMaxWidth(SettingField.NAME_WIDTH);
        HBox row = new HBox(SettingField.COLUMN_GAP, names, table);
        row.setAlignment(Pos.TOP_LEFT);
        row.getStyleClass().add("setting-row");
        building.addRow(row, null);
        building.questionsText = ("questions " + description).toLowerCase();
    }

    // ------------------------------------------------- search, scrolling

    // Shows only the settings that match the search, and only the groups
    // that still hold one.
    private void applySearch() {
        String query = search.getText().trim().toLowerCase();
        boolean any = false;
        for (Group group : groups) {
            boolean shown = group.filter(query);
            group.card.setVisible(shown);
            group.card.setManaged(shown);
            group.indexItem.setVisible(shown);
            group.indexItem.setManaged(shown);
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
    private void jumpTo(Group group) {
        double contentHeight = cards.getHeight();
        double viewHeight = cardScroll.getViewportBounds().getHeight();
        double room = contentHeight - viewHeight;
        jumping = true;
        if (room > 0) {
            double share = group.card.getBoundsInParent().getMinY() / room;
            cardScroll.setVvalue(Math.min(1.0, Math.max(0.0, share)));
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
        boolean atBottom = cardScroll.getVvalue()
                >= cardScroll.getVmax() - BOTTOM_SLACK;
        Group found = null;
        for (Group group : groups) {
            if (group.card.isVisible() && (found == null || atBottom
                    || group.card.getBoundsInParent().getMinY()
                    <= top + SCROLL_SLACK)) {
                found = group;
            }
        }
        if (found != null) {
            select(found);
        }
    }

    // Marks one group as the current one in the list.
    private void select(Group group) {
        if (current != null) {
            current.indexItem.getStyleClass().remove("selected");
        }
        current = group;
        group.indexItem.getStyleClass().add("selected");
    }

    // ------------------------------------------------------ action bar

    // Updates the count of changes and the Save and Discard buttons.
    private void refreshBar() {
        if (!isOnScreen() || !getBackend().supports(Capability.SETTINGS)) {
            return;
        }
        int changed = 0;
        boolean valid = true;
        for (SettingField field : fields) {
            if (field.isChanged()) {
                changed++;
                if (!field.isValid()) {
                    valid = false;
                }
            }
        }
        if (changed == 0) {
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

    // Saves every changed setting together.
    private void save() {
        Map<String, String> changes = new LinkedHashMap<>();
        for (SettingField field : fields) {
            if (field.isChanged()) {
                if (!field.isValid()) {
                    getWindow().notify("Fix the settings marked in red "
                            + "first.");
                    return;
                }
                changes.put(field.getKey(), field.getValue());
            }
        }
        if (changes.isEmpty()) {
            return;
        }
        ActionResult result = getBackend().saveSettings(changes);
        getWindow().notify(result.getMessage());
        if (result.isDone()) {
            for (SettingField field : fields) {
                if (changes.containsKey(field.getKey())) {
                    field.markSaved();
                }
            }
        }
        refreshBar();
    }

    /**
     * One group of settings: its card, its entry in the list and its
     * rows.
     */
    private static class Group {

        // The entry in the list on the left
        private final Label indexItem;

        // The card on the right
        private final VBox card;

        // The rows of the card
        private final VBox rowBox = new VBox();

        // Each row, top to bottom
        private final List<HBox> rows = new ArrayList<>();

        // The setting of each row, or null for the questions table
        private final List<SettingField> rowFields = new ArrayList<>();

        // The words the questions row matches, if this group has it
        private String questionsText = "";

        // Creates an empty group.
        Group(String title) {
            indexItem = new Label(title);
            indexItem.getStyleClass().add("settings-index-item");
            indexItem.setMaxWidth(Double.MAX_VALUE);
            card = Parts.card(title, rowBox);
        }

        // Adds a row under the others.
        void addRow(HBox row, SettingField field) {
            rows.add(row);
            rowFields.add(field);
            rowBox.getChildren().add(row);
            markEnds();
        }

        // Shows the rows that match; says whether any is shown.
        boolean filter(String query) {
            boolean any = false;
            for (int i = 0; i < rows.size(); i++) {
                SettingField field = rowFields.get(i);
                boolean shown = query.isEmpty();
                if (field != null) {
                    shown = field.matches(query);
                } else if (!shown) {
                    shown = questionsText.contains(query);
                }
                rows.get(i).setVisible(shown);
                rows.get(i).setManaged(shown);
                if (shown) {
                    any = true;
                }
            }
            markEnds();
            return any;
        }

        // Gives the first shown row no line above it, and the first and
        // last shown rows no gap against the card's edge.
        void markEnds() {
            HBox last = null;
            for (HBox row : rows) {
                row.getStyleClass().removeAll("first", "last");
                if (row.isVisible()) {
                    if (last == null) {
                        row.getStyleClass().add("first");
                    }
                    last = row;
                }
            }
            if (last != null) {
                last.getStyleClass().add("last");
            }
        }
    }
}
