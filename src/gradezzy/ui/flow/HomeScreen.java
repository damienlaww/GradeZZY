/*
 * File: HomeScreen.java
 *
 * Purpose: The Home tab: every section of a module at a glance for the
 * assessment in use, with the one thing to do next in each (set up,
 * resume, review or open the results). A banner offers to continue a
 * stopped batch, and a short checklist guides a first-time user.
 *
 * Where it fits: The first screen FlowWindow shows. Its buttons lead into
 * the Marking steps with the section already chosen. The assessment
 * picker here and on Set up both switch the assessment in use.
 */
package gradezzy.ui.flow;

import java.io.File;
import java.nio.file.Path;
import java.time.format.DateTimeFormatter;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

import javafx.geometry.Pos;
import javafx.scene.Node;
import javafx.scene.control.Button;
import javafx.scene.control.ComboBox;
import javafx.scene.control.Label;
import javafx.scene.control.ScrollPane;
import javafx.scene.control.TextField;
import javafx.scene.layout.FlowPane;
import javafx.scene.layout.HBox;
import javafx.scene.layout.Priority;
import javafx.scene.layout.StackPane;
import javafx.scene.layout.VBox;
import javafx.stage.FileChooser;

import gradezzy.config.ConfigKeys;
import gradezzy.model.Assessment;
import gradezzy.model.GradingRun;
import gradezzy.model.SavedAssessment;
import gradezzy.model.Section;
import gradezzy.model.Submission;
import gradezzy.model.SubmissionStatus;
import gradezzy.ui.backend.ActionResult;
import gradezzy.ui.backend.Capability;
import gradezzy.ui.backend.ImportItem;
import gradezzy.ui.backend.InputKind;
import gradezzy.ui.backend.ResultState;
import gradezzy.ui.backend.RunRecord;

/**
 * The Home tab.
 */
public class HomeScreen extends FlowScreen {

    // When a run was graded, such as "15 Oct, 09:42"
    private static final DateTimeFormatter WHEN =
            DateTimeFormatter.ofPattern("d MMM, HH:mm");

    // The separator between facts on one line
    private static final String DOT = "  ·  ";

    // The width of one section card
    private static final double CARD_WIDTH = 252.0;

    // The width of the module and assessment choices
    private static final double CHOICE_WIDTH = 220.0;

    // The width of the field that names a new section
    private static final double NEW_SECTION_WIDTH = 120.0;

    // The width of the average bar on a card
    private static final double BAR_WIDTH = 110.0;

    // The size of the icon on the Add section card
    private static final double PLUS_SIZE = 18.0;

    // A section code is letters, digits and dashes, such as G1 or T-02
    private static final String SECTION_PATTERN = "[A-Za-z0-9-]+";

    // The longest section code
    private static final int MAX_SECTION_LENGTH = 12;

    // The file name pattern of a score sheet
    private static final String CSV_PATTERN = "*.csv";

    // The separator between sections in the setting
    private static final String LIST_SEPARATOR = ",";

    // The scrolling page
    private final ScrollPane view;

    // The module choice
    private final ComboBox<String> moduleBox = new ComboBox<>();

    // The assessment choice, which switches the assessment in use
    private final ComboBox<String> assessmentBox = new ComboBox<>();

    // The line under the choices that describes the assessment
    private final Label assessmentLine = Parts.muted("");

    // The banner that offers to continue a batch, when there is one
    private final VBox banner = new VBox();

    // The first-time checklist, shown until a section is graded
    private final VBox checklist = new VBox();

    // One card per section, then the Add section card
    private final FlowPane cards = new FlowPane();

    // The field that names a new section
    private final TextField newSection = new TextField();

    // True while the choices are being filled, so no change is handled
    private boolean filling = false;

    /**
     * Creates the Home tab.
     *
     * @param window the window
     */
    public HomeScreen(FlowWindow window) {
        super(window);
        Node header = Parts.header("Your sections", "Pick what to do next "
                + "in each section. Every card shows the assessment chosen "
                + "below.");
        cards.setHgap(Parts.CARD_GAP);
        cards.setVgap(Parts.CARD_GAP);
        cards.getStyleClass().add("section-cards");
        view = Parts.widePage(new Node[] {header, createChoices(), banner,
            checklist, cards});
    }

    /**
     * Returns the Home tab built in the constructor.
     */
    @Override
    public Node getView() {
        return view;
    }

    /**
     * Fills the choices, the banner, the checklist and the cards again.
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

    // Builds the module and assessment choices and the assessment links.
    private VBox createChoices() {
        moduleBox.setPrefWidth(CHOICE_WIDTH);
        assessmentBox.setPrefWidth(CHOICE_WIDTH);
        moduleBox.setOnAction(event -> chooseModule());
        assessmentBox.setOnAction(event -> chooseAssessment());
        Parts.tip(moduleBox, "The course code. Add or rename modules in "
                + "Settings, under Course.");
        Parts.tip(assessmentBox, "The lab test or exam you are grading: its "
                + "questions, marks, tester files and starter files. "
                + "Changing it here changes it everywhere, including Set "
                + "up.");

        Button create = Parts.secondary("New assessment",
                () -> getWindow().editAssessment(null));
        create.setGraphic(Parts.icon(Parts.PLUS, PLUS_SIZE * 2 / 3));
        Parts.tip(create, "Add the next lab test or exam: its questions, "
                + "marks, tester folder and starter folder.");
        HBox top = Parts.row(Parts.GAP, new Node[] {
            field("Module", moduleBox),
            field("Assessment", assessmentBox),
            Parts.grow(), create});
        top.setAlignment(Pos.BOTTOM_LEFT);

        assessmentLine.getStyleClass().add("small-text");
        HBox line = Parts.row(Parts.GAP_SMALL, new Node[] {assessmentLine,
            Parts.link("Manage assessments",
                    () -> getWindow().openAssessments())});
        return new VBox(Parts.GAP_SMALL, top, line);
    }

    // Puts a small label above a control.
    private VBox field(String label, Node control) {
        Label name = Parts.muted(label);
        name.getStyleClass().add("field-label");
        return new VBox(Parts.GAP_SMALL / 2, name, control);
    }

    // ---------------------------------------------------------- refresh

    // Redraws everything from the backend.
    private void refresh() {
        fillChoices();
        List<RunRecord> records = new ArrayList<>();
        if (getBackend().supports(Capability.HISTORY)) {
            records = getBackend().listRuns();
        }
        refreshBanner(records);
        refreshChecklist(records);
        refreshCards(records);
    }

    // Fills the module and assessment choices.
    private void fillChoices() {
        filling = true;
        List<String> modules = getBackend().getModuleCodes();
        moduleBox.getItems().setAll(modules);
        String module = getState().getModuleCode();
        if (!modules.contains(module) && !modules.isEmpty()) {
            module = modules.get(0);
            getState().setModuleCode(module);
        }
        moduleBox.setValue(module);

        List<String> names = new ArrayList<>();
        for (SavedAssessment saved : getBackend().listAssessments()) {
            names.add(saved.getName());
        }
        assessmentBox.getItems().setAll(names);
        assessmentBox.setValue(activeName());
        assessmentBox.setDisable(getState().isGrading());
        assessmentLine.setText(describeAssessment());
        filling = false;
    }

    // Shows the batch being graded, or the newest stopped one.
    private void refreshBanner(List<RunRecord> records) {
        banner.getChildren().clear();
        Node content = null;
        if (getState().isGrading()) {
            content = bannerRow("A batch of " + getState().getSection()
                    .getLabel() + " is being graded now.", "See progress",
                    () -> getWindow().show(Step.GRADE));
        } else {
            GradingRun stopped = findStoppedRun(records);
            if (stopped != null) {
                int total = stopped.getSubmissions().size();
                int waiting = countWaiting(stopped);
                content = bannerRow(stopped.getSection().getLabel() + DOT
                        + stopped.getAssessment().getName() + " stopped "
                        + "after " + (total - waiting) + " of " + total
                        + " students. Marks already given are kept.",
                        "Resume", () -> getWindow().resumeRun(stopped));
            }
        }
        show(banner, content != null);
        if (content != null) {
            banner.getChildren().add(content);
        }
    }

    // Builds the row inside the banner.
    private HBox bannerRow(String text, String buttonText, Runnable action) {
        Label words = Parts.strong(text);
        words.setWrapText(true);
        HBox.setHgrow(words, Priority.ALWAYS);
        Button go = Parts.primary(buttonText);
        go.setOnAction(event -> action.run());
        HBox row = Parts.row(Parts.GAP, new Node[] {words, go});
        row.getStyleClass().add("home-banner");
        return row;
    }

    // Shows the three first steps until a section has been graded.
    private void refreshChecklist(List<RunRecord> records) {
        checklist.getChildren().clear();
        boolean anyRun = !records.isEmpty();
        show(checklist, !anyRun);
        if (anyRun) {
            return;
        }
        String testersProblem = problemOf(InputKind.TESTERS);
        String starterProblem = problemOf(InputKind.STARTER);
        String rosterProblem = problemOf(InputKind.ROSTER);
        boolean assessmentReady = testersProblem.isEmpty()
                && starterProblem.isEmpty();
        String assessmentDetail = describeAssessment() + ". Its tester "
                + "files and starter files were found.";
        if (!testersProblem.isEmpty()) {
            assessmentDetail = "Tester files: " + testersProblem;
        } else if (!starterProblem.isEmpty()) {
            assessmentDetail = "Starter files: " + starterProblem;
        }
        String rosterDetail = "The class list from eLearn (CSV). It "
                + "matches each zip file to a student, and Export fills the "
                + "marks into a copy of it.";
        if (!rosterProblem.isEmpty()) {
            rosterDetail = rosterProblem + " " + rosterDetail;
        }
        String firstSection = firstSection();

        VBox steps = new VBox(
                checkRow(1, assessmentReady, "Check the assessment",
                        assessmentDetail, "Edit assessment",
                        () -> getWindow().editAssessment(activeName())),
                checkRow(2, rosterProblem.isEmpty(), "Check the score "
                        + "sheet", rosterDetail, "Choose score sheet",
                        () -> chooseScoreSheet()),
                checkRow(3, false, "Grade your first section", "Choose the "
                        + "students' zip files and start grading. You can "
                        + "check every fix and flag before you export.",
                        "Set up " + firstSection,
                        () -> openSetUp(firstSection)));
        Label title = Parts.title("Getting started");
        Label help = Parts.muted("Three steps to your first marked section. "
                + "Rest the pointer on any \"?\" to see what a word means.");
        help.getStyleClass().add("small-text");
        help.setWrapText(true);
        VBox card = Parts.card(null, new Node[] {title, help, steps});
        card.getStyleClass().add("checklist");
        checklist.getChildren().add(card);
    }

    // Builds one step of the checklist: a number or tick, the words and
    // the link that does it.
    private HBox checkRow(int number, boolean done, String title,
            String detail, String linkText, Runnable action) {
        StackPane dot = new StackPane();
        dot.getStyleClass().add("step-dot");
        if (done) {
            dot.getStyleClass().add("done");
            dot.getChildren().add(Parts.icon(Parts.CHECK, PLUS_SIZE * 2 / 3));
        } else {
            dot.getStyleClass().add("later");
            dot.getChildren().add(new Label(String.valueOf(number)));
        }
        Label detailLabel = Parts.muted(detail);
        detailLabel.getStyleClass().add("small-text");
        detailLabel.setWrapText(true);
        VBox words = new VBox(Parts.strong(title), detailLabel);
        HBox.setHgrow(words, Priority.ALWAYS);
        Button go = Parts.small(linkText, action);
        HBox row = Parts.row(Parts.GAP, new Node[] {dot, words, go});
        row.getStyleClass().add("checklist-row");
        return row;
    }

    // Draws one card per section of the module, then Add section.
    private void refreshCards(List<RunRecord> records) {
        cards.getChildren().clear();
        Map<String, GradingRun> runs = findRuns(records);
        String module = getState().getModuleCode();
        for (String section : getBackend().getSectionCodes(module)) {
            cards.getChildren().add(sectionCard(section,
                    runs.get(section)));
        }
        cards.getChildren().add(addSectionCard());
    }

    // ------------------------------------------------------------ cards

    // Builds the card of one section.
    private VBox sectionCard(String section, GradingRun run) {
        Label name = new Label(section);
        name.getStyleClass().add("section-name");
        HBox top = Parts.row(Parts.GAP_SMALL, new Node[] {name, Parts.grow(),
            statusPill(section, run)});
        VBox card = new VBox(Parts.GAP_SMALL, top);
        card.getStyleClass().addAll("flow-card", "section-card");
        card.setPrefWidth(CARD_WIDTH);
        card.setMinWidth(CARD_WIDTH);
        card.setMaxWidth(CARD_WIDTH);

        if (run == null) {
            Label none = Parts.muted("Nothing graded yet for "
                    + activeName() + ".");
            none.getStyleClass().add("small-text");
            none.setWrapText(true);
            card.getChildren().add(none);
        } else {
            Label when = Parts.muted(Parts.plural(run.getSubmissions()
                    .size(), "student") + DOT + "graded "
                    + run.getStartedAt().format(WHEN));
            when.getStyleClass().add("small-text");
            card.getChildren().addAll(when, average(run));
        }
        Node grow = Parts.grow();
        card.getChildren().addAll(grow, actions(section, run));
        return card;
    }

    // Returns the pill that says where a section is.
    private Label statusPill(String section, GradingRun run) {
        Label pill;
        String tip;
        if (isGradingNow(section)) {
            pill = Parts.pill("Grading now", Tone.NEUTRAL);
            tip = "This section is being graded. You can leave this page; "
                    + "grading carries on.";
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
            tip = "Students with a fix to approve, a flag to look at, or a "
                    + "problem such as a compile error. Review shows each "
                    + "one.";
        } else {
            pill = Parts.pill("Graded", Tone.GOOD);
            tip = "Every student is graded and nothing waits for you. "
                    + "Export the score sheet from the results.";
        }
        Parts.tip(pill, tip);
        return pill;
    }

    // Returns the button that does the next thing, and the extra links.
    private HBox actions(String section, GradingRun run) {
        Button main;
        if (isGradingNow(section)) {
            main = cardButton("See progress",
                    () -> getWindow().show(Step.GRADE),
                    "Open the Grade step to watch the batch.");
        } else if (run == null) {
            main = cardButton("Set up grading", () -> openSetUp(section),
                    "Choose this section's zip files on Set up.");
        } else if (countWaiting(run) > 0
                && getBackend().supports(Capability.RESUME_RUN)) {
            main = cardButton("Resume", () -> getWindow().resumeRun(run),
                    "Grade the students who were not graded yet.");
        } else if (countToCheck(run) > 0) {
            main = cardButton("Review", () -> getWindow().openRun(run),
                    "Go through the students who need a look, then "
                    + "export.");
        } else {
            main = cardButton("Open results", () -> getWindow().openRun(run),
                    "See every student's marks, then export the score "
                    + "sheet.");
        }
        HBox row = Parts.row(Parts.GAP_SMALL, new Node[] {main});
        if (run != null && !isGradingNow(section)) {
            Button again = Parts.small("Grade again",
                    () -> openSetUp(section));
            Parts.tip(again, "Grade a new batch for this section, for "
                    + "example late submissions. The results shown now are "
                    + "kept on Runs.");
            row.getChildren().add(again);
        }
        if (run != null && getBackend().supports(Capability.STATISTICS)) {
            row.getChildren().add(Parts.link("Statistics",
                    () -> getWindow().openStatistics(
                    getState().getModuleCode(), section)));
        }
        return row;
    }

    // Builds the blue button of a card.
    private Button cardButton(String text, Runnable action, String tip) {
        Button button = Parts.small(text, action);
        button.getStyleClass().add("accent");
        Parts.tip(button, tip);
        return button;
    }

    // Shows a run's average as a small bar and "x / max".
    private HBox average(GradingRun run) {
        double max = run.getAssessment().getMaxTotal();
        MarkStats stats = MarkStats.of(run.getSubmissions(), max,
                MarkStats.parsePassPercent(getBackend().getSetting(
                ConfigKeys.PASS_MARK_PERCENT)));
        if (stats.isEmpty() || max <= 0) {
            return Parts.row(0, new Node[] {Parts.muted("No marks yet")});
        }
        StackPane bar = Parts.track(stats.getMean() / max, null);
        bar.setPrefWidth(BAR_WIDTH);
        bar.setMaxWidth(BAR_WIDTH);
        Label value = new Label("Average " + String.format("%.1f",
                stats.getMean()) + " / " + Parts.mark(max));
        value.getStyleClass().add("small-text");
        value.setMinWidth(Label.USE_PREF_SIZE);
        return Parts.row(Parts.GAP_SMALL, new Node[] {bar, value});
    }

    // Builds the card that adds a section to the settings.
    private VBox addSectionCard() {
        newSection.setPromptText("Such as G11");
        newSection.setPrefWidth(NEW_SECTION_WIDTH);
        newSection.setOnAction(event -> addSection());
        Button add = Parts.small("Add", () -> addSection());
        add.setGraphic(Parts.icon(Parts.PLUS, PLUS_SIZE * 2 / 3));
        Label title = Parts.strong("Add a section");
        Label help = Parts.muted("A class group you teach, such as G11. It "
                + "is added for every module.");
        help.getStyleClass().add("small-text");
        help.setWrapText(true);
        VBox card = new VBox(Parts.GAP_SMALL, title, help,
                Parts.row(Parts.GAP_SMALL, new Node[] {newSection, add}));
        card.getStyleClass().addAll("flow-card", "section-card",
                "add-card");
        card.setPrefWidth(CARD_WIDTH);
        card.setMinWidth(CARD_WIDTH);
        card.setMaxWidth(CARD_WIDTH);
        return card;
    }

    // ---------------------------------------------------------- actions

    // Handles a new module.
    private void chooseModule() {
        if (filling || moduleBox.getValue() == null) {
            return;
        }
        getState().setModuleCode(moduleBox.getValue());
        refresh();
    }

    // Switches the assessment in use.
    private void chooseAssessment() {
        String chosen = assessmentBox.getValue();
        if (filling || chosen == null || chosen.equals(activeName())) {
            return;
        }
        getWindow().useAssessment(chosen);
        refresh();
    }

    // Opens Set up on a section of the chosen module. With no section
    // listed yet, it asks for one first.
    private void openSetUp(String section) {
        if (!getBackend().getSectionCodes(getState().getModuleCode())
                .contains(section)) {
            getWindow().notify("Add a section first, on the last card.");
            newSection.requestFocus();
            return;
        }
        getWindow().openSetUp(getState().getModuleCode(), section);
    }

    // Adds the typed section to the settings, after checking it.
    private void addSection() {
        String code = newSection.getText().trim();
        List<String> sections = getBackend().getSectionCodes(
                getState().getModuleCode());
        String problem = "";
        if (!code.matches(SECTION_PATTERN)
                || code.length() > MAX_SECTION_LENGTH) {
            problem = "Use letters, digits and dashes only, such as G11.";
        }
        for (String existing : sections) {
            if (existing.equalsIgnoreCase(code)) {
                problem = existing + " is already listed.";
            }
        }
        if (!problem.isEmpty()) {
            getWindow().notify(problem);
            newSection.requestFocus();
            return;
        }
        String value = getBackend().getSetting(ConfigKeys.SECTION_CODES)
                .trim();
        if (!value.isEmpty()) {
            value += LIST_SEPARATOR;
        }
        Map<String, String> changes = new HashMap<>();
        changes.put(ConfigKeys.SECTION_CODES, value + code);
        ActionResult result = getBackend().saveSettings(changes);
        if (result.isDone()) {
            getWindow().notify("Added section " + code + ".");
            newSection.clear();
        } else {
            getWindow().notify(result.getMessage());
        }
        refresh();
    }

    // ---------------------------------------------------------- reading

    // Finds the run shown for each section of the chosen module and the
    // assessment in use: the current one, else the newest.
    private Map<String, GradingRun> findRuns(List<RunRecord> records) {
        Map<String, GradingRun> runs = new HashMap<>();
        Map<String, Boolean> fromCurrent = new HashMap<>();
        String module = getState().getModuleCode();
        for (RunRecord record : records) {
            GradingRun run = record.getRun();
            Section section = run.getSection();
            boolean matches = section != null
                    && module.equalsIgnoreCase(section.getModuleCode())
                    && isActiveAssessment(run);
            String code = "";
            if (matches) {
                code = section.getSectionCode();
            }
            boolean better = matches && (!runs.containsKey(code)
                    || (record.isCurrent() && !fromCurrent.get(code)));
            if (better) {
                runs.put(code, run);
                fromCurrent.put(code, record.isCurrent());
            }
        }
        return runs;
    }

    // Returns the newest run shown for its section that stopped early.
    private GradingRun findStoppedRun(List<RunRecord> records) {
        if (!getBackend().supports(Capability.RESUME_RUN)) {
            return null;
        }
        for (RunRecord record : records) {
            GradingRun run = record.getRun();
            if (record.isCurrent() && run.getSection() != null
                    && countWaiting(run) > 0) {
                return run;
            }
        }
        return null;
    }

    // Says whether a run was graded with the assessment in use.
    private boolean isActiveAssessment(GradingRun run) {
        Assessment assessment = run.getAssessment();
        return assessment != null
                && activeName().equalsIgnoreCase(assessment.getName());
    }

    // Says whether a section of the chosen module is being graded now.
    private boolean isGradingNow(String section) {
        Section graded = getState().getSection();
        return getState().isGrading()
                && section.equalsIgnoreCase(graded.getSectionCode())
                && getState().getModuleCode().equalsIgnoreCase(
                        graded.getModuleCode());
    }

    // Counts the students not graded yet.
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

    // Counts the students who need the instructor: open items, or a
    // submission that could not be graded or did not compile.
    private int countToCheck(GradingRun run) {
        int count = 0;
        for (Submission submission : run.getSubmissions()) {
            ResultState state = Students.state(submission, getBackend());
            if (state == ResultState.NEEDS_REVIEW
                    || Students.isFailed(state)) {
                count++;
            }
        }
        return count;
    }

    // Says what is wrong with the configured file or folder of a kind,
    // or returns an empty string when it can be used.
    private String problemOf(InputKind kind) {
        Path path = getBackend().getConfiguredPath(kind);
        if (path == null) {
            return "Not chosen yet.";
        }
        ImportItem item = getBackend().inspect(kind, path);
        if (item.getState().isUsable()) {
            return "";
        }
        return item.getMessage();
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
        File file = chooser.showOpenDialog(getWindow().getWindow());
        if (file == null) {
            return;
        }
        Map<String, String> changes = new HashMap<>();
        changes.put(ConfigKeys.SCORE_SHEET_FILE, file.getPath());
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

    // Describes the assessment in use, such as "Lab Test · 5 questions ·
    // 20 marks".
    private String describeAssessment() {
        Assessment assessment = getBackend().getAssessment();
        if (assessment == null) {
            return "No assessment is set";
        }
        return assessment.getName() + DOT + Parts.plural(assessment
                .getQuestions().size(), "question") + DOT
                + Parts.mark(assessment.getMaxTotal()) + " marks";
    }

    // Shows or hides a node and frees its space.
    private void show(Node node, boolean visible) {
        node.setVisible(visible);
        node.setManaged(visible);
    }
}
