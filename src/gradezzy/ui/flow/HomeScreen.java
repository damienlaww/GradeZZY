/*
 * File: HomeScreen.java
 *
 * Purpose: The Home tab. One line names the module and assessment in
 * use, with a Change menu. A "Next up" bar offers the one most useful
 * thing to do (resume a stopped batch, or review students who need a
 * look). A checklist appears only while something is missing. Each
 * section has a card with its progress through Set up, Grade, Review
 * and Export, one main button and a "..." menu for the rest. Under the
 * cards, recent activity and the marks of each section.
 *
 * Where it fits: The first screen FlowWindow shows. Its buttons lead into
 * the Grading steps with the section already chosen. The Change menu
 * here and the assessment picker on Set up both switch the assessment
 * in use.
 */
package gradezzy.ui.flow;

import java.io.File;
import java.nio.file.InvalidPathException;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.time.format.DateTimeFormatter;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

import javafx.application.Platform;
import javafx.geometry.Insets;
import javafx.geometry.Pos;
import javafx.scene.Node;
import javafx.scene.control.Button;
import javafx.scene.control.ButtonBase;
import javafx.scene.control.ComboBox;
import javafx.scene.control.Hyperlink;
import javafx.scene.control.Label;
import javafx.scene.control.MenuButton;
import javafx.scene.control.MenuItem;
import javafx.scene.control.ScrollPane;
import javafx.scene.control.SeparatorMenuItem;
import javafx.scene.input.MouseEvent;
import javafx.scene.layout.ColumnConstraints;
import javafx.scene.layout.FlowPane;
import javafx.scene.layout.GridPane;
import javafx.scene.layout.HBox;
import javafx.scene.layout.Priority;
import javafx.scene.layout.Region;
import javafx.scene.layout.StackPane;
import javafx.scene.layout.VBox;
import javafx.scene.shape.Circle;
import javafx.stage.FileChooser;

import gradezzy.config.ConfigKeys;
import gradezzy.config.ModuleSections;
import gradezzy.model.Assessment;
import gradezzy.model.AuditEntry;
import gradezzy.model.GradingRun;
import gradezzy.model.SavedAssessment;
import gradezzy.model.Section;
import gradezzy.ui.DragDropHandler;
import gradezzy.ui.backend.ActionResult;
import gradezzy.ui.backend.Capability;
import gradezzy.ui.backend.ImportItem;
import gradezzy.ui.backend.InputKind;
import gradezzy.ui.backend.RunRecord;
import gradezzy.util.TextUtils;

/**
 * The Home tab.
 *
 * <p>Tier: SHOULD.
 */
public class HomeScreen extends FlowScreen {

    // When a run was graded, such as "15 Oct, 09:42"
    private static final DateTimeFormatter WHEN =
            DateTimeFormatter.ofPattern("d MMM, HH:mm");

    // The separator between facts on one line
    private static final String DOT = "  ·  ";

    // The width of one section card
    private static final double CARD_WIDTH = 252.0;

    // The names of the four steps, and each one's place
    private static final String[] STEP_NAMES = {"Set up", "Grade",
        "Review", "Export"};
    private static final int SET_UP = 0;
    private static final int GRADE = 1;
    private static final int REVIEW = 2;
    private static final int EXPORT = 3;

    // Past the last step: the section's score sheet is exported
    private static final int EXPORTED = STEP_NAMES.length;

    // The gap between the four parts of the progress bar
    private static final double PROGRESS_GAP = 4.0;

    // The height of the progress bar
    private static final double PROGRESS_HEIGHT = 6.0;

    // The height of every section card, so a row lines up
    private static final double CARD_HEIGHT = 224.0;

    // The dots of the "..." menu
    private static final double MORE_DOT_RADIUS = 1.6;
    private static final double MORE_DOT_GAP = 3.0;
    private static final int MORE_DOT_COUNT = 3;

    // The size of the small icons
    private static final double ICON_SIZE = 12.0;

    // The size of the upload icon of an empty card
    private static final double DROP_ICON_SIZE = 18.0;

    // The file name pattern of a score sheet
    private static final String CSV_PATTERN = "*.csv";

    // The most activity lines shown on Home
    private static final int MOST_ACTIVITY = 5;

    // Shares of the width of the lower row
    private static final double HALF_PERCENT = 50.0;

    // How many columns the lower row has
    private static final int LOWER_COLUMNS = 2;

    // Turns a share into a percentage
    private static final double PERCENT = 100.0;

    // Writes a mark with one decimal place, such as 12.4
    private static final String ONE_DECIMAL_FORMAT = "%.1f";

    // A tiny preferred width, so the parts of the progress bar share it
    // evenly
    private static final double PART_PREF_WIDTH = 1.0;

    // The numbers of the checklist steps
    private static final int ASSESSMENT_STEP = 1;
    private static final int SCORE_SHEET_STEP = 2;
    private static final int FIRST_GRADE_STEP = 3;

    // The last line of each dropdown, which creates a new one
    private static final String NEW_MODULE = "+ New module...";
    private static final String NEW_SECTION = "+ New section...";
    private static final String NEW_ASSESSMENT = "+ New assessment...";

    // The first line of the section dropdown, which shows every card
    private static final String ALL_SECTIONS = "All sections";

    // The width of each dropdown
    private static final double CHOICE_WIDTH = 200.0;

    // Comes before the most marks after an average, such as "/ 20"
    private static final String OUT_OF = "/ ";

    // Comes between a mark and the most it can be, such as "12.4 / 20"
    private static final String MARK_SEPARATOR = " / ";

    // Comes between what was done and its detail in the activity card
    private static final String ACTION_SEPARATOR = ": ";

    // Comes between the names of a list but the last two, such as "G1,
    // G2 and G5"
    private static final String NAME_SEPARATOR = ", ";

    // The scrolling page
    private final ScrollPane view;

    // The module, section and assessment dropdowns
    private final ComboBox<String> moduleBox = new ComboBox<>();
    private final ComboBox<String> sectionBox = new ComboBox<>();
    private final ComboBox<String> assessmentBox = new ComboBox<>();

    // How many questions and marks the assessment has
    private final Label contextDetail = Parts.muted("");

    // Adds an assessment, shown when the module has none, as on Set up
    private final Hyperlink addAssessment = Parts.link("Add an assessment",
            () -> getWindow().editAssessment(null));

    // True while the dropdowns are being filled, so no change is handled
    private boolean filling = false;

    // The section whose card alone is shown, or null for every section
    private String shownSection = null;

    // The bar that offers the one most useful thing to do
    private final VBox nextUp = new VBox();

    // The checklist, shown only while something is missing
    private final VBox checklist = new VBox();

    // One card per section
    private final FlowPane cards = new FlowPane();

    // Recent activity and the marks of each section
    private final GridPane lower = new GridPane();

    // The section the Next up bar is about, or null
    private String nextUpSection = null;

    /**
     * Creates the Home tab.
     *
     * @param window the window
     */
    public HomeScreen(FlowWindow window) {
        super(window);
        Node header = Parts.header("Your sections", "What to do next in "
                + "each section, for the module and assessment chosen "
                + "below.");
        cards.setHgap(Parts.CARD_GAP);
        cards.setVgap(Parts.CARD_GAP);
        cards.getStyleClass().add("section-cards");
        lower.setHgap(Parts.CARD_GAP);
        lower.setVgap(Parts.CARD_GAP);
        for (int index = 0; index < LOWER_COLUMNS; index++) {
            ColumnConstraints column = new ColumnConstraints();
            column.setPercentWidth(HALF_PERCENT);
            lower.getColumnConstraints().add(column);
        }
        view = Parts.widePage(new Node[] {header, createContext(), nextUp,
            checklist, cards, lower});
    }

    /**
     * Returns the Home tab built in the constructor.
     */
    @Override
    public Node getView() {
        return view;
    }

    /**
     * Fills the context line, the bars, the cards and the lower row again.
     */
    @Override
    public void onShow() {
        refresh();
    }

    /**
     * Returns false, because Home has no bottom bar.
     */
    @Override
    public boolean usesActionBar() {
        return false;
    }

    // ------------------------------------------------------------ build

    // Builds the module, section and assessment dropdowns. The last
    // line of each creates a new one.
    private VBox createContext() {
        setUpBox(moduleBox, "Module", "The course code, such as CS102.");
        setUpBox(sectionBox, "Section", "Show one section's card, or all of "
                + "them. Set up opens on the section chosen here.");
        setUpBox(assessmentBox, "Assessment", "The lab test or exam you are "
                + "grading: its questions, marks, tester files and starter "
                + "files. Changing it here changes it everywhere, including "
                + "Set up.");
        moduleBox.setOnAction(event -> moduleChosen());
        sectionBox.setOnAction(event -> sectionChosen());
        assessmentBox.setOnAction(event -> assessmentChosen());

        HBox boxes = Parts.row(Parts.GAP, new Node[] {
            field("Module", moduleBox), field("Section", sectionBox),
            field("Assessment", assessmentBox)});
        boxes.setAlignment(Pos.BOTTOM_LEFT);
        contextDetail.getStyleClass().add("small-text");
        HBox line = Parts.row(Parts.GAP_SMALL, new Node[] {contextDetail,
            addAssessment, Parts.link("Manage assessments",
                    () -> getWindow().openAssessments())});
        VBox context = new VBox(Parts.GAP_SMALL, boxes, line);
        context.getStyleClass().add("home-context-row");
        return context;
    }

    // Gives a dropdown its width, name and tip.
    private void setUpBox(ComboBox<String> box, String name, String tip) {
        box.setPrefWidth(CHOICE_WIDTH);
        box.setAccessibleText(name);
        Parts.tip(box, tip);
    }

    // Puts a small label above a control.
    private VBox field(String label, Node control) {
        Label name = Parts.muted(label);
        name.getStyleClass().add("field-label");
        return new VBox(Parts.GAP_TINY, name, control);
    }

    // ---------------------------------------------------------- refresh

    // Redraws everything from the backend.
    private void refresh() {
        fillContext();
        List<RunRecord> records = new ArrayList<>();
        if (getBackend().supports(Capability.HISTORY)) {
            records = getBackend().listRuns();
        }
        Map<String, GradingRun> runs = findRuns(records);
        refreshNextUp(records, runs);
        refreshChecklist(records);
        refreshCards(runs);
        refreshLower(runs);
    }

    // Fills the three dropdowns.
    private void fillContext() {
        filling = true;
        String module = fillModules();
        fillSections(module);
        List<String> names = fillAssessments(module);
        // Grading reads the assessment from the settings, so it stays
        // while any section grades; sections can be graded at once
        assessmentBox.setDisable(getState().isAnyGrading());
        showContextDetail(module, names);
        filling = false;
    }

    // Fills the module dropdown and returns the module chosen, which
    // becomes the first one when the chosen one is no longer listed.
    private String fillModules() {
        List<String> modules = getBackend().getModuleCodes();
        String module = getState().getModuleCode();
        if (!modules.contains(module) && !modules.isEmpty()) {
            module = modules.get(0);
            getState().setModuleCode(module);
        }
        moduleBox.getItems().setAll(modules);
        if (getBackend().supports(Capability.SETTINGS)) {
            moduleBox.getItems().add(NEW_MODULE);
        }
        moduleBox.setValue(module);
        return module;
    }

    // Fills the section dropdown with the module's sections.
    private void fillSections(String module) {
        List<String> sections = getBackend().getSectionCodes(module);
        if (shownSection != null && !sections.contains(shownSection)) {
            shownSection = null;
        }
        sectionBox.getItems().setAll(ALL_SECTIONS);
        sectionBox.getItems().addAll(sections);
        if (getBackend().supports(Capability.SETTINGS)) {
            sectionBox.getItems().add(NEW_SECTION);
        }
        if (shownSection == null) {
            sectionBox.setValue(ALL_SECTIONS);
        } else {
            sectionBox.setValue(shownSection);
        }
    }

    // Fills the assessment dropdown with the module's assessments and
    // returns their names.
    private List<String> fillAssessments(String module) {
        // Only the chosen module's assessments; a new module has none
        boolean ownOnly = isOwnOnly(module);
        List<String> names = new ArrayList<>();
        for (SavedAssessment saved : getBackend().listAssessments()) {
            if (saved.belongsTo(module, ownOnly)) {
                names.add(saved.getName());
            }
        }
        assessmentBox.getItems().setAll(names);
        assessmentBox.getItems().add(NEW_ASSESSMENT);
        if (names.contains(activeName())) {
            assessmentBox.setValue(activeName());
        } else if (names.isEmpty()) {
            assessmentBox.setValue(null);
            assessmentBox.setPromptText("None yet");
        } else {
            assessmentBox.setValue(null);
            assessmentBox.setPromptText("Choose one");
        }
        return names;
    }

    // Says under the dropdowns how big the assessment is, or what to do
    // when none is chosen.
    private void showContextDetail(String module, List<String> names) {
        show(addAssessment, names.isEmpty());
        if (names.contains(activeName())) {
            contextDetail.setText(describeAssessmentSize());
        } else if (names.isEmpty()) {
            contextDetail.setText(module + " has no assessment yet.");
        } else {
            contextDetail.setText("Choose the assessment to grade for "
                    + module + ".");
        }
    }

    // Follows a pick in the module dropdown.
    private void moduleChosen() {
        String chosen = moduleBox.getValue();
        if (filling || chosen == null) {
            return;
        }
        if (NEW_MODULE.equals(chosen)) {
            undoPick();
            openAddModule();
            return;
        }
        boolean switched = getWindow().chooseSection(chosen,
                getState().getSectionCode());
        if (!switched) {
            undoPick();
            return;
        }
        shownSection = null;
        getWindow().useModuleAssessment(chosen);
        refresh();
    }

    // Follows a pick in the section dropdown.
    private void sectionChosen() {
        String chosen = sectionBox.getValue();
        if (filling || chosen == null) {
            return;
        }
        if (NEW_SECTION.equals(chosen)) {
            undoPick();
            openAddSection();
            return;
        }
        if (ALL_SECTIONS.equals(chosen)) {
            shownSection = null;
        } else if (chooseInModule(chosen)) {
            shownSection = chosen;
        } else {
            undoPick();
            return;
        }
        refresh();
    }

    // Switches Set up to a section of the chosen module, and says whether
    // it switched.
    private boolean chooseInModule(String section) {
        return getWindow().chooseSection(getState().getModuleCode(),
                section);
    }

    // Says whether a module has only its own sections, none of the
    // sections every module has, as the settings list it.
    private boolean isOwnOnly(String module) {
        String setting = getBackend().getSetting(
                ConfigKeys.OWN_SECTIONS_ONLY_MODULES);
        return ModuleSections.hasOwnOnly(setting, module);
    }

    // Follows a pick in the assessment dropdown.
    private void assessmentChosen() {
        String chosen = assessmentBox.getValue();
        if (filling || chosen == null) {
            return;
        }
        if (NEW_ASSESSMENT.equals(chosen)) {
            undoPick();
            getWindow().editAssessment(null);
            return;
        }
        chooseAssessment(chosen);
    }

    // Puts the dropdowns back on what was chosen before a "New" line was
    // picked. Done after the pick is finished, so the dropdown accepts it.
    private void undoPick() {
        Platform.runLater(() -> fillContext());
    }

    // Shows the one most useful thing to do: a batch being graded, a
    // stopped batch to resume, or students who need a look.
    private void refreshNextUp(List<RunRecord> records,
            Map<String, GradingRun> runs) {
        nextUp.getChildren().clear();
        nextUpSection = null;
        Node content = null;
        List<GradingJob> running = getState().getRunningJobs();
        if (!running.isEmpty()) {
            GradingJob first = running.get(0);
            nextUpSection = first.getSection().getSectionCode();
            content = nextUpRow(describeGrading(running), "See progress",
                    () -> getWindow().watchGrading(first.getSection()));
        } else {
            content = stoppedNextUp(records, runs);
        }
        show(nextUp, content != null);
        if (content != null) {
            nextUp.getChildren().add(content);
        }
    }

    // Returns the Next up row for a stopped batch to resume, else for
    // the first section with students who need a look, or null.
    private Node stoppedNextUp(List<RunRecord> records,
            Map<String, GradingRun> runs) {
        GradingRun stopped = findStoppedRun(records);
        if (stopped == null) {
            return reviewNextUp(runs);
        }
        int total = stopped.getSubmissions().size();
        int waiting = countWaiting(stopped);
        int graded = total - waiting;
        nextUpSection = stopped.getSection().getSectionCode();
        return nextUpRow(stopped.getSection().getLabel() + DOT
                + stopped.getAssessment().getName() + " stopped after "
                + graded + " of " + total
                + " students. Marks already given are kept.", "Resume",
                () -> getWindow().resumeRun(stopped));
    }

    // Returns the Next up row for the first section with students who
    // need a look, or null when none has.
    private Node reviewNextUp(Map<String, GradingRun> runs) {
        for (String section : getBackend().getSectionCodes(
                getState().getModuleCode())) {
            GradingRun run = runs.get(section);
            if (run != null && countToCheck(run) > 0) {
                nextUpSection = section;
                int check = countToCheck(run);
                String who = Parts.plural(check, "student") + " "
                        + needOrNeeds(check);
                return nextUpRow(run.getSection().getLabel() + DOT + who
                        + " a look before you export.", "Review",
                        () -> getWindow().openRun(run));
            }
        }
        return null;
    }

    // Builds the row inside the Next up bar.
    private HBox nextUpRow(String text, String buttonText,
            Runnable action) {
        Label tag = Parts.muted("Next up");
        tag.getStyleClass().addAll("small-text", "next-up-tag");
        tag.setMinWidth(Label.USE_PREF_SIZE);
        Label words = Parts.strong(text);
        words.setWrapText(true);
        words.setMinWidth(0);
        HBox.setHgrow(words, Priority.ALWAYS);
        Button go = Parts.primary(buttonText);
        go.setOnAction(event -> action.run());
        go.setMinWidth(Button.USE_PREF_SIZE);
        HBox row = Parts.row(Parts.GAP, new Node[] {tag, words, go});
        row.getStyleClass().add("home-banner");
        return row;
    }

    // Shows the checklist while the assessment or score sheet has a
    // problem, or before the first section is graded. Each step that is
    // fine has a tick; a step with a problem has a warning.
    private void refreshChecklist(List<RunRecord> records) {
        checklist.getChildren().clear();
        boolean anyRun = !records.isEmpty();
        String testersProblem = problemOf(InputKind.TESTERS);
        String starterProblem = problemOf(InputKind.STARTER);
        String rosterProblem = problemOf(InputKind.ROSTER);
        boolean assessmentReady = testersProblem.isEmpty()
                && starterProblem.isEmpty();
        boolean rosterReady = rosterProblem.isEmpty();
        boolean shown = !anyRun || !assessmentReady || !rosterReady;
        show(checklist, shown);
        if (!shown) {
            return;
        }

        String assessmentDetail = describeAssessment() + ". Its tester "
                + "files and starter files were found.";
        String assessmentFull = assessmentDetail;
        if (!testersProblem.isEmpty()) {
            assessmentDetail = "Tester files: " + shorten(testersProblem,
                    InputKind.TESTERS);
            assessmentFull = "Tester files: " + testersProblem;
        } else if (!starterProblem.isEmpty()) {
            assessmentDetail = "Starter files: " + shorten(starterProblem,
                    InputKind.STARTER);
            assessmentFull = "Starter files: " + starterProblem;
        }
        String rosterDetail = "The class list from eLearn (CSV). It "
                + "matches each zip file to a student, and Export fills the "
                + "marks into a copy of it.";
        String rosterFull = rosterDetail;
        if (!rosterReady) {
            rosterDetail = shorten(rosterProblem, InputKind.ROSTER);
            rosterFull = rosterProblem;
        }

        VBox steps = new VBox(
                checkRow(ASSESSMENT_STEP, assessmentReady, "The assessment",
                        assessmentDetail, assessmentFull, "Edit assessment",
                        () -> getWindow().editAssessment(activeName())),
                checkRow(SCORE_SHEET_STEP, rosterReady, "The score sheet",
                        rosterDetail, rosterFull, "Choose score sheet",
                        () -> chooseScoreSheet()));
        if (!anyRun) {
            steps.getChildren().add(firstGradeRow());
        }
        checklist.getChildren().add(checklistCard(anyRun, steps));
    }

    // Builds the checklist step that grades the first section.
    private HBox firstGradeRow() {
        String firstSection = firstSection();
        return checkRow(FIRST_GRADE_STEP, null, "Grade your first section",
                "Choose the students' zip files and start grading. You can "
                + "check every fix and flag before you export.", null,
                "Set up " + firstSection, () -> openSetUp(firstSection));
    }

    // Builds the checklist card: a title and a line of help above the
    // steps. Before the first run it is a getting started list.
    private VBox checklistCard(boolean anyRun, VBox steps) {
        String titleText = "Getting started";
        String helpText = "Click any \"?\" to see what a word means.";
        if (anyRun) {
            titleText = "Something needs fixing before the next batch";
            helpText = "This list goes away once everything is found.";
        }
        Label title = Parts.title(titleText);
        Label help = Parts.muted(helpText);
        help.getStyleClass().add("small-text");
        help.setWrapText(true);
        VBox card = Parts.card(null, new Node[] {title, help, steps});
        card.getStyleClass().add("checklist");
        return card;
    }

    // Builds one step of the checklist: a tick, a warning or a number,
    // the words and the button that does it. Done is null for a step
    // that is neither done nor a problem.
    private HBox checkRow(int number, Boolean done, String title,
            String detail, String fullDetail, String linkText,
            Runnable action) {
        StackPane dot = new StackPane();
        dot.getStyleClass().add("step-dot");
        if (Boolean.TRUE.equals(done)) {
            dot.getStyleClass().add("done");
            dot.getChildren().add(Parts.icon(Parts.CHECK, ICON_SIZE));
        } else if (Boolean.FALSE.equals(done)) {
            dot.getStyleClass().add("problem");
            dot.getChildren().add(new Label("!"));
        } else {
            dot.getStyleClass().add("later");
            dot.getChildren().add(new Label(String.valueOf(number)));
        }
        Label detailLabel = Parts.muted(detail);
        detailLabel.getStyleClass().add("small-text");
        if (Boolean.FALSE.equals(done)) {
            detailLabel.getStyleClass().add("problem-text");
        }
        detailLabel.setWrapText(true);
        if (fullDetail != null && !fullDetail.equals(detail)) {
            Parts.tip(detailLabel, fullDetail);
        }
        VBox words = new VBox(Parts.strong(title), detailLabel);
        words.setMinWidth(0);
        HBox.setHgrow(words, Priority.ALWAYS);
        HBox row = Parts.row(Parts.GAP, new Node[] {dot, words});
        if (!Boolean.TRUE.equals(done)) {
            row.getChildren().add(Parts.small(linkText, action));
        }
        row.getStyleClass().add("checklist-row");
        return row;
    }

    // Draws one card per section of the module.
    private void refreshCards(Map<String, GradingRun> runs) {
        cards.getChildren().clear();
        String module = getState().getModuleCode();
        for (String section : getBackend().getSectionCodes(module)) {
            if (shownSection == null || shownSection.equals(section)) {
                cards.getChildren().add(sectionCard(section,
                        runs.get(section)));
            }
        }
    }

    // Shows recent activity and the marks of each section, under the
    // cards.
    private void refreshLower(Map<String, GradingRun> runs) {
        lower.getChildren().clear();
        int column = 0;
        if (getBackend().supports(Capability.AUDIT_LOG)) {
            lower.add(activityCard(), column, 0);
            column++;
        }
        if (getBackend().supports(Capability.STATISTICS)) {
            lower.add(marksCard(runs), column, 0);
            column++;
        }
        show(lower, column > 0);
    }

    // ------------------------------------------------------------ cards

    // Builds the card of one section: its name and status, the four
    // steps named, the average large, one main button named for what it
    // does, and a "..." menu. A graded card opens its results when
    // clicked; a card not graded yet takes zip files dropped on it.
    private VBox sectionCard(String section, GradingRun run) {
        Label name = new Label(section);
        name.getStyleClass().add("section-name");
        HBox top = Parts.row(Parts.GAP_SMALL, new Node[] {name, Parts.grow(),
            statusPill(section, run)});
        VBox card = new VBox(Parts.GAP_SMALL, top);
        card.getStyleClass().addAll("flow-card", "section-card");
        if (section.equals(nextUpSection)) {
            card.getStyleClass().add("next-up");
        }
        card.setPrefWidth(CARD_WIDTH);
        card.setMinWidth(CARD_WIDTH);
        card.setMaxWidth(CARD_WIDTH);
        // Every card is as tall as the others, so a row lines up
        card.setMinHeight(CARD_HEIGHT);
        card.setPrefHeight(CARD_HEIGHT);

        if (run == null) {
            Node body = emptyBody(section);
            VBox.setVgrow(body, Priority.ALWAYS);
            card.getChildren().add(body);
        } else {
            card.getChildren().add(averageBlock(run));
            Node grow = Parts.grow();
            VBox.setVgrow(grow, Priority.ALWAYS);
            card.getChildren().add(grow);
        }
        card.getChildren().addAll(progress(stepOf(section, run)),
                footer(section, run));
        setCardAction(card, section, run);
        return card;
    }

    // Makes the whole card do the main thing: watch the batch, open the
    // results on the step they need, or set up grading.
    private void setCardAction(VBox card, String section, GradingRun run) {
        if (isGradingNow(section)) {
            makeClickable(card, () -> getWindow().watchGrading(
                    sectionOf(section)), "Watch " + section
                    + " being graded");
        } else if (run != null) {
            Step step = SectionProgress.stepToOpen(getState(), getBackend(),
                    sectionOf(section), run);
            makeClickable(card, () -> getWindow().openSectionStep(run),
                    "Open " + section + "'s results on "
                    + step.getLabel());
        } else {
            makeClickable(card, () -> openSetUp(section),
                    "Set up grading for " + section);
            acceptDrops(card, section);
        }
    }

    // The body of a card not graded yet: a drop area for zip files, or
    // how many students are ready on Set up.
    private Node emptyBody(String section) {
        String words = "Drop zip files here";
        int chosen = countChosen(section);
        if (chosen > 0) {
            words = Parts.plural(chosen, "student") + " ready on Set up";
        }
        Label hint = Parts.muted(words);
        hint.getStyleClass().add("small-text");
        VBox drop = new VBox(Parts.GAP_TINY,
                Parts.icon(Parts.UPLOAD, DROP_ICON_SIZE), hint);
        drop.setAlignment(Pos.CENTER);
        drop.getStyleClass().add("card-drop-hint");
        drop.setMaxHeight(Double.MAX_VALUE);
        VBox.setMargin(drop, new Insets(0, 0, Parts.GAP_SMALL, 0));
        return drop;
    }

    // Says which of the four steps a section is on.
    private int stepOf(String section, GradingRun run) {
        if (isGradingNow(section)) {
            return GRADE;
        } else if (run == null) {
            return SET_UP;
        } else if (countWaiting(run) > 0) {
            return GRADE;
        } else if (countToCheck(run) > 0) {
            return REVIEW;
        } else if (getState().isExported(run)) {
            return EXPORTED;
        }
        return EXPORT;
    }

    // Shows the average large, under a small label.
    private VBox averageBlock(GradingRun run) {
        double max = run.getAssessment().getMaxTotal();
        MarkStats stats = statsOf(run);
        if (stats.isEmpty() || max <= 0) {
            Label none = Parts.muted("No marks yet");
            none.getStyleClass().add("card-average-empty");
            return new VBox(none);
        }
        String mean = String.format(ONE_DECIMAL_FORMAT, stats.getMean());
        return cardStat("Average", mean, OUT_OF + Parts.mark(max));
    }

    // Builds one figure of a card: a small grey label above a large value
    // with its unit after it.
    private VBox cardStat(String label, String value, String unit) {
        Label name = Parts.muted(label);
        name.getStyleClass().addAll("small-text", "card-stat-label");
        Label number = new Label(value);
        number.getStyleClass().add("card-average");
        Label after = Parts.muted(unit);
        after.getStyleClass().add("card-average-max");
        HBox line = new HBox(Parts.GAP_TINY, number, after);
        line.setAlignment(Pos.BASELINE_LEFT);
        return new VBox(0, name, line);
    }

    // Returns the pill that says where a section is.
    private Label statusPill(String section, GradingRun run) {
        Label pill = null;
        String tip = "";
        if (isGradingNow(section)) {
            pill = Parts.pill(gradingWords(section), Tone.GOOD);
            tip = "This section is being graded. You can leave this page "
                    + "or grade another section; grading carries on.";
        } else if (run == null) {
            pill = Parts.pill("Not graded", Tone.NEUTRAL);
            tip = "No batch of " + activeName() + " has been graded for "
                    + "this section yet.";
        } else if (countWaiting(run) > 0) {
            pill = Parts.pill("Stopped", Tone.WARN);
            tip = "Grading stopped before every student was graded, for "
                    + "example because it was cancelled. Resume grades the "
                    + "rest and keeps the marks already given.";
        } else if (countToCheck(run) > 0) {
            pill = Parts.pill(countToCheck(run) + " to check", Tone.WARN);
            tip = "Students with a fix to keep or reject, a flag to look "
                    + "at, a total of 0, or a problem such as a compile "
                    + "error. Review shows each one.";
        } else if (getState().isExported(run)) {
            pill = Parts.pill("Exported", Tone.NEUTRAL);
            tip = "The score sheet was exported since GradeZZY opened. "
                    + "Open the results to export again.";
        } else {
            pill = Parts.pill("Graded", Tone.GOOD);
            tip = "Every student is graded and nothing waits for you. "
                    + "Export the score sheet from the results.";
        }
        Parts.tip(pill, tip);
        return pill;
    }

    // Draws the four steps as a slim bar in four parts, done ones filled,
    // the current one in blue, with "Step 3 of 4 · Review" under it.
    // Once exported, every part is filled and it says "Exported".
    private VBox progress(int current) {
        HBox bar = new HBox(PROGRESS_GAP);
        for (int index = 0; index < STEP_NAMES.length; index++) {
            Region part = new Region();
            part.getStyleClass().add("card-progress-part");
            if (index < current) {
                part.getStyleClass().add("done");
            } else if (index == current) {
                part.getStyleClass().add("current");
            }
            part.setMinSize(0, PROGRESS_HEIGHT);
            part.setPrefSize(PART_PREF_WIDTH, PROGRESS_HEIGHT);
            part.setMaxSize(Double.MAX_VALUE, PROGRESS_HEIGHT);
            HBox.setHgrow(part, Priority.ALWAYS);
            Parts.tipNode(part, STEP_NAMES[index]);
            bar.getChildren().add(part);
        }
        bar.setMinHeight(PROGRESS_HEIGHT);
        bar.setMaxWidth(Double.MAX_VALUE);
        String place = "Exported";
        if (current < EXPORTED) {
            int stepNumber = current + 1;
            place = "Step " + stepNumber + " of " + STEP_NAMES.length
                    + DOT + STEP_NAMES[current];
        }
        Label words = Parts.muted(place);
        words.getStyleClass().addAll("small-text", "card-progress-words");
        return new VBox(Parts.GAP_TINY, bar, words);
    }

    // The footer: when the section was last graded on the left, the
    // "..." menu on the right.
    private HBox footer(String section, GradingRun run) {
        String when = "Not graded yet";
        if (isGradingNow(section)) {
            when = "Being graded now";
        } else if (run != null && run.getStartedAt() != null) {
            when = "Graded " + run.getStartedAt().format(WHEN);
        } else if (run != null) {
            when = "Graded date unknown";
        }
        Label time = Parts.muted(when);
        time.getStyleClass().addAll("small-text", "card-meta");
        time.setGraphic(Parts.icon(Parts.CLOCK, ICON_SIZE));
        HBox row = Parts.row(Parts.GAP_SMALL, new Node[] {time,
            Parts.grow(), moreMenu(section, run)});
        row.setAlignment(Pos.CENTER_LEFT);
        row.getStyleClass().add("card-footer");
        return row;
    }

    // Builds the blue button that does a section's next step, named for
    // what it does.
    private Button mainButton(String section, GradingRun run) {
        if (isGradingNow(section)) {
            return cardButton("See progress",
                    () -> getWindow().show(Step.GRADE),
                    "Open the Grade step to watch the batch.");
        }
        if (run == null) {
            return cardButton("Set up grading", () -> openSetUp(section),
                    "Choose this section's zip files on Set up.");
        }
        boolean canResume = countWaiting(run) > 0
                && getBackend().supports(Capability.RESUME_RUN);
        if (canResume) {
            return cardButton("Resume grading",
                    () -> getWindow().resumeRun(run),
                    "Grade the students who were not graded yet.");
        } else if (countToCheck(run) > 0) {
            int check = countToCheck(run);
            return cardButton("Review " + Parts.plural(check, "student"),
                    () -> getWindow().openRun(run), "Go through the "
                    + "students who need a look, then export.");
        } else if (getState().isExported(run)) {
            return cardButton("See export", () -> openExport(run),
                    "Open the Export step to export again if needed.");
        }
        return cardButton("Export marks", () -> openExport(run),
                "Check the marks once more, then export the score sheet.");
    }

    // Builds the "..." menu of a section card, each item with an icon.
    private MenuButton moreMenu(String section, GradingRun run) {
        MenuButton more = new MenuButton();
        more.setGraphic(moreDots());
        more.getStyleClass().add("card-more");
        more.setAccessibleText("More for " + section);
        more.setMinWidth(MenuButton.USE_PREF_SIZE);
        Parts.tip(more, "More for " + section);
        // The next step first, in bold, then the rest
        Button next = mainButton(section, run);
        MenuItem first = menuItem(next.getText(), Parts.ARROW,
                () -> next.fire());
        first.getStyleClass().add("next-item");
        more.getItems().add(first);
        if (run != null && !isGradingNow(section)) {
            MenuItem results = menuItem("Open results", Parts.CHEVRON,
                    () -> getWindow().openRun(run));
            MenuItem again = menuItem("Grade again...", Parts.UPLOAD,
                    () -> openSetUp(section));
            more.getItems().addAll(results, again);
        }
        if (run != null && getBackend().supports(Capability.STATISTICS)) {
            more.getItems().add(menuItem("Statistics", Parts.CHART,
                    () -> openStatistics(section)));
        }
        if (run != null && getBackend().supports(Capability.HISTORY)) {
            more.getItems().add(menuItem("Earlier runs", Parts.CLOCK,
                    () -> getWindow().showPage(Page.RUNS)));
        }
        boolean canDelete = getBackend().supports(Capability.SETTINGS)
                && !isGradingNow(section);
        if (canDelete) {
            MenuItem delete = menuItem("Delete section...", Parts.CLOSE,
                    () -> askDeleteSection(section));
            delete.getStyleClass().add("danger-item");
            // A line sets the delete apart from the other items
            if (more.getItems().size() > 1) {
                more.getItems().add(new SeparatorMenuItem());
            }
            more.getItems().add(delete);
        }
        // A line sets the next step apart only when something follows it
        boolean needsLine = more.getItems().size() > 1
                && !(more.getItems().get(1) instanceof SeparatorMenuItem);
        if (needsLine) {
            more.getItems().add(1, new SeparatorMenuItem());
        }
        return more;
    }

    // Opens Statistics on a section of the chosen module.
    private void openStatistics(String section) {
        getWindow().openStatistics(getState().getModuleCode(), section);
    }

    // Asks whether to delete a section of the chosen module.
    private void askDeleteSection(String section) {
        CourseSheets.askDeleteSection(getWindow(),
                getState().getModuleCode(), section, () -> sectionAdded());
    }

    // Draws three dots, for the "..." menu; a font glyph can be cut off.
    private Node moreDots() {
        HBox dots = new HBox(MORE_DOT_GAP);
        dots.setAlignment(Pos.CENTER);
        for (int index = 0; index < MORE_DOT_COUNT; index++) {
            Circle dot = new Circle(MORE_DOT_RADIUS);
            dot.getStyleClass().add("more-dot");
            dots.getChildren().add(dot);
        }
        return dots;
    }

    // Builds one menu item with an icon.
    private MenuItem menuItem(String text, String iconPath,
            Runnable action) {
        MenuItem item = new MenuItem(text, Parts.icon(iconPath, ICON_SIZE));
        item.setOnAction(event -> action.run());
        return item;
    }

    // Makes a whole card open something when clicked anywhere but its
    // buttons, and lifts it a little under the pointer.
    private void makeClickable(VBox card, Runnable action, String tip) {
        card.getStyleClass().add("clickable");
        Parts.tipNode(card, tip);
        card.setOnMouseClicked(event -> cardClicked(event, card, action));
    }

    // Opens what a card opens, unless the click was on one of its
    // buttons.
    private void cardClicked(MouseEvent event, VBox card, Runnable action) {
        Node clicked = event.getPickResult().getIntersectedNode();
        if (!isInsideButton(clicked, card)) {
            action.run();
        }
    }

    // Says whether a clicked node is a button, a menu or inside one.
    private boolean isInsideButton(Node node, Node card) {
        Node at = node;
        while (at != null && at != card) {
            if (at instanceof ButtonBase) {
                return true;
            }
            at = at.getParent();
        }
        return false;
    }

    // Lets zip files be dropped on a card not graded yet: they are added
    // on Set up for that section, which then opens.
    private void acceptDrops(VBox card, String section) {
        DragDropHandler drops = new DragDropHandler();
        drops.install(card);
        drops.setOnDropped(() -> filesDropped(drops, section));
    }

    // Opens Set up for the section a zip file was dropped on, with it.
    private void filesDropped(DragDropHandler drops, String section) {
        List<Path> paths = new ArrayList<>(drops.getDroppedPaths());
        openSetUp(section);
        FlowScreen screen = getWindow().getScreen(Step.SET_UP);
        if (screen instanceof SetUpScreen && !paths.isEmpty()) {
            ((SetUpScreen) screen).addFiles(paths);
        }
    }

    // Opens a run and goes straight to its Export step.
    private void openExport(GradingRun run) {
        getWindow().openRun(run);
        getWindow().advanceTo(Step.EXPORT);
    }

    // Builds the blue button of a card.
    private Button cardButton(String text, Runnable action, String tip) {
        Button button = Parts.small(text, action);
        button.getStyleClass().add("accent");
        Parts.tip(button, tip);
        return button;
    }

    // Returns the figures of a run, with the pass mark in Settings.
    private MarkStats statsOf(GradingRun run) {
        double passPercent = MarkStats.parsePassPercent(
                getBackend().getSetting(ConfigKeys.PASS_MARK_PERCENT));
        return MarkStats.of(run.getSubmissions(),
                run.getAssessment().getMaxTotal(), passPercent);
    }

    // Returns the figures of a run, or null when there is no run.
    private MarkStats statsOrNull(GradingRun run) {
        if (run == null) {
            return null;
        }
        return statsOf(run);
    }

    // -------------------------------------------------------- lower row

    // Builds the card with the newest lines of the activity log.
    private VBox activityCard() {
        List<AuditEntry> entries = new ArrayList<>(
                getBackend().listAuditEntries());
        entries.sort(new AuditEntryNewestFirstComparator());
        VBox list = new VBox();
        int shown = Math.min(MOST_ACTIVITY, entries.size());
        for (int index = 0; index < shown; index++) {
            list.getChildren().add(activityLine(entries.get(index)));
        }
        if (shown == 0) {
            list.getChildren().add(Parts.muted("Nothing done yet. Grading, "
                    + "fixes, exports and backups are listed here."));
        } else {
            list.getChildren().get(0).getStyleClass().add("first");
        }
        Label title = Parts.title("Recent activity");
        Node seeAll = Parts.link("See all", () -> openActivity());
        HBox top = Parts.row(Parts.GAP,
                new Node[] {title, Parts.grow(), seeAll});
        VBox card = Parts.card(null, new Node[] {top, list});
        card.getStyleClass().add("home-lower-card");
        return card;
    }

    // Builds one line of the activity card: what was done on the left,
    // when on the right, and the full detail in the tip.
    private HBox activityLine(AuditEntry entry) {
        Label when = Parts.muted(entry.getTimestamp().format(WHEN));
        when.getStyleClass().add("small-text");
        when.setMinWidth(Label.USE_PREF_SIZE);
        Label what = new Label(entry.getAction() + ACTION_SEPARATOR
                + Parts.shortenPaths(entry.getShownDetail()));
        what.getStyleClass().add("small-text");
        what.setMinWidth(0);
        Parts.tip(what, entry.getAction() + ACTION_SEPARATOR
                + entry.getDetail());
        HBox line = Parts.row(Parts.GAP, new Node[] {what, Parts.grow(),
            when});
        line.getStyleClass().add("card-row");
        return line;
    }

    // Opens the activity log on Runs.
    private void openActivity() {
        getWindow().showPage(Page.RUNS);
        FlowScreen screen = getWindow().getPageScreen(Page.RUNS);
        if (screen instanceof RunsScreen) {
            ((RunsScreen) screen).showActivity();
        }
    }

    // Builds the card with each graded section's average and pass rate.
    private VBox marksCard(Map<String, GradingRun> runs) {
        VBox list = new VBox();
        int shown = 0;
        for (String section : getBackend().getSectionCodes(
                getState().getModuleCode())) {
            MarkStats stats = statsOrNull(runs.get(section));
            if (stats != null && !stats.isEmpty()) {
                list.getChildren().add(marksLine(section, stats));
                shown++;
            }
        }
        if (shown == 0) {
            list.getChildren().add(Parts.muted("Grade a section to "
                    + "compare its marks here."));
        } else {
            list.getChildren().get(0).getStyleClass().add("first");
        }
        Label title = Parts.title("Marks across sections");
        Node compare = Parts.link("Compare",
                () -> getWindow().showPage(Page.STATISTICS));
        HBox top = Parts.row(Parts.GAP,
                new Node[] {title, Parts.grow(), compare});
        VBox card = Parts.card(null, new Node[] {top, list});
        card.getStyleClass().add("home-lower-card");
        return card;
    }

    // Builds one line of the marks card: the section, a bar filled to
    // its average, and its average and pass rate.
    private HBox marksLine(String section, MarkStats stats) {
        Label name = Parts.strong(section);
        name.setMinWidth(Label.USE_PREF_SIZE);
        String mean = String.format(ONE_DECIMAL_FORMAT, stats.getMean());
        String max = Parts.mark(stats.getMaxTotal());
        long passed = Math.round(stats.getPassRate() * PERCENT);
        Label figures = Parts.muted(mean + MARK_SEPARATOR + max + DOT + passed
                + Parts.PERCENT_SIGN + " passed");
        figures.getStyleClass().add("small-text");
        figures.setMinWidth(Label.USE_PREF_SIZE);
        // An assessment with no marks to earn shows an empty bar
        double filled = 0.0;
        if (stats.getMaxTotal() > 0.0) {
            filled = stats.getMean() / stats.getMaxTotal();
        }
        StackPane bar = Parts.track(filled, null);
        HBox.setHgrow(bar, Priority.ALWAYS);
        HBox line = Parts.row(Parts.GAP, new Node[] {name, bar, figures});
        line.getStyleClass().add("card-row");
        return line;
    }

    // ---------------------------------------------------------- actions

    // Switches the assessment in use.
    private void chooseAssessment(String chosen) {
        if (chosen.equals(activeName())) {
            return;
        }
        getWindow().useAssessment(chosen);
        refresh();
    }

    // Opens Set up on a section of the chosen module. With no section
    // listed yet, it asks for one first.
    private void openSetUp(String section) {
        List<String> sections = getBackend().getSectionCodes(
                getState().getModuleCode());
        if (!sections.contains(section)) {
            openAddSection();
            return;
        }
        getWindow().openSetUp(getState().getModuleCode(), section);
    }

    // Opens the sheet that adds a module, then shows it.
    private void openAddModule() {
        CourseSheets.openAddModule(getWindow(), code -> moduleAdded(code));
    }

    // Shows a module just added, clearing zip files chosen for another.
    private void moduleAdded(String code) {
        getWindow().chooseSection(code, getState().getSectionCode());
        shownSection = null;
        refresh();
    }

    // Shows every section's card once a section was added or deleted.
    private void sectionAdded() {
        shownSection = null;
        refresh();
    }

    // Opens the sheet that adds a section to this module or every module.
    private void openAddSection() {
        CourseSheets.openAddSection(getWindow(), getState().getModuleCode(),
                code -> sectionAdded());
    }

    // ---------------------------------------------------------- reading

    // Finds the run shown for each section of the chosen module and the
    // assessment in use: the run open on Review, else the current one,
    // else the newest.
    private Map<String, GradingRun> findRuns(List<RunRecord> records) {
        return SectionProgress.findShownRuns(records,
                getState().getModuleCode(), activeName(),
                getState().getRun());
    }

    // Returns the newest run shown for its section that stopped early.
    private GradingRun findStoppedRun(List<RunRecord> records) {
        if (!getBackend().supports(Capability.RESUME_RUN)) {
            return null;
        }
        for (RunRecord record : records) {
            GradingRun run = record.getRun();
            boolean stopped = record.isCurrent() && run.getSection() != null
                    && countWaiting(run) > 0;
            if (stopped) {
                return run;
            }
        }
        return null;
    }

    // Says whether a section of the chosen module is being graded now.
    private boolean isGradingNow(String section) {
        return getState().isGrading(sectionOf(section));
    }

    // Says how far a section's batch has got, such as "Grading 12 of
    // 65", or "Grading now" before it knows how many it grades.
    private String gradingWords(String section) {
        return SectionProgress.describeGrading(getState(),
                sectionOf(section));
    }

    // Returns a section of the chosen module.
    private Section sectionOf(String section) {
        return new Section(getState().getModuleCode(), section);
    }

    // Says which sections are being graded, such as "G1 is being graded
    // now" or "G1 and G5 are being graded now", and that it carries on.
    private String describeGrading(List<GradingJob> running) {
        List<String> labels = new ArrayList<>();
        for (GradingJob job : running) {
            labels.add(job.getSection().getLabel());
        }
        String verb = " is";
        if (labels.size() > 1) {
            verb = " are";
        }
        return joinWithAnd(labels) + verb + " being graded now. You can "
                + "leave this page or grade another section; grading "
                + "carries on.";
    }

    // Joins names as "A", "A and B" or "A, B and C".
    private static String joinWithAnd(List<String> names) {
        int lastIndex = names.size() - 1;
        if (lastIndex <= 0) {
            return TextUtils.join("", names);
        }
        return TextUtils.join(NAME_SEPARATOR, names.subList(0, lastIndex))
                + " and " + names.get(lastIndex);
    }

    // Counts the students not graded yet.
    private int countWaiting(GradingRun run) {
        return SectionProgress.countWaiting(run);
    }

    // Counts the students who need the instructor: open items, or a
    // submission that could not be graded or did not compile.
    private int countToCheck(GradingRun run) {
        return SectionProgress.countToCheck(run, getBackend());
    }

    // Says what is wrong with the configured file or folder of a kind,
    // or returns an empty string when it can be used.
    private String problemOf(InputKind kind) {
        Path path = getBackend().getConfiguredPath(kind);
        // Each section of the assessment in use has its own score sheet
        if (kind == InputKind.ROSTER && !getBackend().isDemo()) {
            path = findSectionScoreSheet();
        }
        if (path == null && getBackend().isDemo()) {
            // The demo uses its own sample files, chosen on Set up
            return "";
        } else if (path == null) {
            return "Not chosen yet.";
        }
        ImportItem item = getBackend().inspect(kind, path);
        if (item.getState().isUsable()) {
            return "";
        }
        return item.getMessage();
    }

    // Returns the score sheet of the section chosen now, else the one for
    // every section, or null when the assessment in use has none.
    private Path findSectionScoreSheet() {
        String sheet = getBackend().getScoreSheet(getState().getSection());
        if (sheet == null || sheet.trim().isEmpty()) {
            return null;
        }
        try {
            return Paths.get(sheet.trim());
        } catch (InvalidPathException exception) {
            return null;
        }
    }

    // Asks for the score sheet and saves it as the one in Settings. The
    // demo has no files to choose, so it opens Settings instead.
    private void chooseScoreSheet() {
        if (getBackend().isDemo()) {
            getWindow().showPage(Page.SETTINGS);
            return;
        }
        FileChooser chooser = new FileChooser();
        chooser.setTitle("Choose the score sheet exported from eLearn");
        chooser.getExtensionFilters().add(new FileChooser.ExtensionFilter(
                "CSV files", CSV_PATTERN));
        Choosers.prepare(chooser);
        File file = chooser.showOpenDialog(getWindow().getWindow());
        Choosers.remember(file);
        if (file == null) {
            return;
        }
        Map<String, String> changes = new HashMap<>();
        changes.put(ConfigKeys.SCORE_SHEET_FILE,
                Parts.configPath(file.toPath()));
        ActionResult result = getBackend().saveSettings(changes);
        getWindow().notify(result.getMessage());
        refresh();
    }

    // Returns the first section of the chosen module, or "a section".
    private String firstSection() {
        List<String> sections = getBackend().getSectionCodes(
                getState().getModuleCode());
        if (sections.isEmpty()) {
            return "a section";
        }
        return sections.get(0);
    }

    // Returns the name of the assessment in use.
    private String activeName() {
        Assessment assessment = getBackend().getAssessment();
        if (assessment == null) {
            return "";
        }
        return assessment.getName();
    }

    // Counts the students whose zip files are chosen on Set up for a
    // section, or 0 when Set up is on another section.
    private int countChosen(String section) {
        Section chosen = getState().getSection();
        boolean otherSection = chosen == null || getState().isGrading()
                || !section.equalsIgnoreCase(chosen.getSectionCode())
                || !getState().getModuleCode().equalsIgnoreCase(
                        chosen.getModuleCode());
        if (otherSection) {
            return 0;
        }
        return getState().getBatch().countStudents();
    }

    // Returns "needs" for one and "need" for more.
    private static String needOrNeeds(int count) {
        if (count == 1) {
            return "needs";
        }
        return "need";
    }

    // Shortens a message about a file or folder: its full path becomes
    // its name, so the line stays readable. The full message is in the
    // tip.
    private String shorten(String message, InputKind kind) {
        Path path = getBackend().getConfiguredPath(kind);
        if (kind == InputKind.ROSTER && !getBackend().isDemo()) {
            path = findSectionScoreSheet();
        }
        if (path == null || path.getFileName() == null) {
            return message;
        }
        String name = path.getFileName().toString();
        return message.replace(path.toAbsolutePath().toString(), name)
                .replace(path.toString(), name);
    }

    // Describes how big the assessment is, such as "5 questions, 20
    // marks".
    private String describeAssessmentSize() {
        Assessment assessment = getBackend().getAssessment();
        if (assessment == null) {
            return "";
        }
        return Parts.plural(assessment.getQuestions().size(), "question")
                + ", " + Parts.mark(assessment.getMaxTotal()) + " marks";
    }

    // Describes the assessment in use, such as "Lab Test · 5 questions ·
    // 20 marks".
    private String describeAssessment() {
        Assessment assessment = getBackend().getAssessment();
        if (assessment == null) {
            return "No assessment is set";
        }
        int questionCount = assessment.getQuestions().size();
        return assessment.getName() + DOT
                + Parts.plural(questionCount, "question") + DOT
                + Parts.mark(assessment.getMaxTotal()) + " marks";
    }

    // Shows or hides a node and frees its space.
    private void show(Node node, boolean visible) {
        node.setVisible(visible);
        node.setManaged(visible);
    }
}
