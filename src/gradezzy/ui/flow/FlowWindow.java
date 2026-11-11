/*
 * File: FlowWindow.java
 *
 * Purpose: The window: a top bar with the tabs Home, Grading, Statistics,
 * Runs and Settings; under it, on Grading only, the row of four steps; one
 * screen at a time in the middle; and the action bar at the bottom on the
 * screens that use it. Opens Help and small sheets over the screen, and
 * switches the assessment in use.
 *
 * Where it fits: MainApp builds one and shows its scene. Every screen
 * reaches the backend, the shared state and the action bar through it.
 */
package gradezzy.ui.flow;

import java.io.File;
import java.net.URL;
import java.nio.file.Path;
import java.time.format.DateTimeFormatter;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.function.Supplier;

import javafx.animation.PauseTransition;
import javafx.application.HostServices;
import javafx.geometry.Pos;
import javafx.scene.Node;
import javafx.scene.Scene;
import javafx.scene.control.Button;
import javafx.scene.control.ContentDisplay;
import javafx.scene.control.Hyperlink;
import javafx.scene.control.Label;
import javafx.scene.image.Image;
import javafx.scene.image.ImageView;
import javafx.scene.input.KeyCode;
import javafx.scene.input.KeyCodeCombination;
import javafx.scene.input.KeyCombination;
import javafx.scene.input.KeyEvent;
import javafx.scene.input.MouseEvent;
import javafx.scene.layout.BorderPane;
import javafx.scene.layout.HBox;
import javafx.scene.layout.Region;
import javafx.scene.layout.StackPane;
import javafx.scene.layout.VBox;
import javafx.stage.Window;
import javafx.stage.WindowEvent;
import javafx.util.Duration;

import gradezzy.config.ConfigKeys;
import gradezzy.config.ModuleSections;
import gradezzy.model.Assessment;
import gradezzy.model.GradingRun;
import gradezzy.model.SavedAssessment;
import gradezzy.model.Section;
import gradezzy.model.Student;
import gradezzy.service.ChosenSubmissionsStore;
import gradezzy.ui.AppFonts;
import gradezzy.ui.AppIcons;
import gradezzy.ui.ThemeManager;
import gradezzy.ui.backend.ActionResult;
import gradezzy.ui.backend.DashboardBackend;

/**
 * The window of GradeZZY.
 *
 * <p>Tier: SHOULD.
 */
public class FlowWindow {

    /** The stylesheet of the guided window. */
    public static final String STYLESHEET = "/gradezzy/ui/css/flow.css";

    // Added to the name of a deleted assessment in the activity log, as
    // the files chosen for it on Set up go with it
    private static final String DELETED_INPUTS = " and the files chosen "
            + "for it on Set up";

    // How long a notice stays on screen
    private static final Duration NOTICE_TIME = Duration.seconds(3.5);

    // How many times longer a notice with a link stays
    private static final double ACTION_NOTICE_FACTOR = 2.0;

    // How often to check whether a stopped batch has finished, before
    // closing
    private static final Duration CLOSE_CHECK_TIME = Duration.millis(200);

    // The size of the icon of the Help button
    private static final double TOP_ICON_SIZE = 16.0;

    // The width and height of the logo in the top bar
    private static final double LOGO_SIZE = 22.0;

    // The shortcut that presses the blue button: Ctrl or Cmd and Enter
    private static final KeyCombination NEXT_KEYS = new KeyCodeCombination(
            KeyCode.ENTER, KeyCombination.SHORTCUT_DOWN);

    // The shortcut that puts the cursor in the Review search: Ctrl or
    // Cmd and F
    private static final KeyCombination FIND_KEYS = new KeyCodeCombination(
            KeyCode.F, KeyCombination.SHORTCUT_DOWN);

    // With Ctrl or Cmd, the keys that open steps 1 to 4
    private static final KeyCode[] STEP_KEYS = {KeyCode.DIGIT1,
        KeyCode.DIGIT2, KeyCode.DIGIT3, KeyCode.DIGIT4};

    // The first word of a confirm button that deletes or removes
    // something, in lower case
    private static final String[] DESTRUCTIVE_WORDS = {"delete", "remove",
        "discard"};

    // The button of a question that does nothing
    private static final String CANCEL_TEXT = "Cancel";

    // When the run named above the steps was graded, such as "11 Nov,
    // 00:32"
    private static final DateTimeFormatter CONTEXT_WHEN =
            DateTimeFormatter.ofPattern("d MMM, HH:mm");

    // Comes between the module, section and assessment, and between the
    // parts of the line under the steps
    private static final String CONTEXT_DOT = " \u00B7 ";

    // The name of the thread that looks up the students with no
    // submission
    private static final String ROSTER_THREAD = "gradezzy-roster";

    // The style class of the outer layout and the root in the dark theme
    private static final String DARK_CLASS = "dark";

    // The backend every screen calls
    private final DashboardBackend backend;

    // What the window remembers between steps
    private final FlowState state = new FlowState();

    // The lookup of students with no submission still running, or null
    private MissingStudentsLookup missingLookup = null;

    // What to run when that lookup ends
    private final List<Runnable> missingCallbacks = new ArrayList<>();

    // Sets the AtlantaFX theme
    private final ThemeManager themes = new ThemeManager();

    // The root of the scene: the outer layout, with the tour on top
    private final StackPane root = new StackPane();

    // The outer layout
    private final BorderPane frame = new BorderPane();

    // The guided tour, created when first started
    private GuidedTour tour = null;

    // Holds the screen, with sheets and notices on top
    private final StackPane layers = new StackPane();

    // Holds the current screen
    private final StackPane content = new StackPane();

    // The tabs along the top
    private final TabBar tabBar = new TabBar(page -> showPage(page));

    // The four steps
    private final StepBar stepBar = new StepBar(step -> show(step));

    // The row that holds the steps, shown on Grading only
    private final HBox stepRow = new HBox(stepBar);

    // The line under the steps that names the module, section and
    // assessment on screen, with a link to change the section
    private final HBox contextRow = new HBox();

    // The bottom bar
    private final ActionBar actionBar = new ActionBar();

    // The step screens, created when first shown
    private final Map<Step, FlowScreen> screens = new HashMap<>();

    // The screens of Home, Statistics, Runs and Settings, created when
    // first shown
    private final Map<Page, FlowScreen> pages = new HashMap<>();

    // The notice that fades in at the bottom
    private final Label notice = new Label();

    // The step Grading shows
    private Step currentStep = Step.SET_UP;

    // The tab on screen
    private Page currentPage = Page.HOME;

    // The open sheet, or null
    private Node sheet = null;

    // Hides the notice after a while
    private PauseTransition noticeTimer = null;

    // True for the dark theme
    private boolean dark;

    // Opens folders in Finder or File Explorer; null in the checks, which
    // have no application to ask
    private HostServices hostServices = null;

    /**
     * Creates the window.
     *
     * @param backend the live or demo backend
     * @param dark true to start in the dark theme
     */
    public FlowWindow(DashboardBackend backend, boolean dark) {
        this.backend = backend;
        this.dark = dark;
    }

    // Generated by Claude; modified for opening on Home, with a tour layer
    /**
     * Builds the scene and shows Home.
     *
     * @param width the starting width
     * @param height the starting height
     * @return the scene
     */
    public Scene build(double width, double height) {
        AppFonts.load();
        themes.apply(themeName());
        frame.getStyleClass().add("flow-root");
        stepRow.getStyleClass().add("step-row");
        stepRow.setAlignment(Pos.CENTER);
        contextRow.getStyleClass().add("grading-context");
        contextRow.setAlignment(Pos.CENTER);
        frame.setTop(new VBox(createTopBar(), stepRow, contextRow));

        content.getStyleClass().add("flow-content");
        notice.getStyleClass().add("notice");
        notice.setVisible(false);
        notice.setMouseTransparent(true);
        StackPane.setAlignment(notice, Pos.BOTTOM_CENTER);
        layers.getChildren().addAll(content, notice);
        frame.setCenter(layers);
        root.getChildren().add(frame);
        applyDarkClass();

        Scene scene = new Scene(root, width, height);
        URL css = FlowWindow.class.getResource(STYLESHEET);
        if (css != null) {
            scene.getStylesheets().add(css.toExternalForm());
        }
        addShortcuts(scene);
        noteAssessmentInUse();
        showPage(Page.HOME);
        return scene;
    }

    // Tells the state which assessment is in use, so Set up lists the
    // zip files chosen for it and not those of another lab test.
    private void noteAssessmentInUse() {
        Assessment inUse = backend.getAssessment();
        String name = "";
        if (inUse != null) {
            name = inUse.getName();
        }
        state.setAssessmentName(name);
    }

    /**
     * Shows a step on the Grading tab, if the instructor may open it.
     *
     * @param step the step
     */
    public void show(Step step) {
        if (step.ordinal() > getFurthestOpen().ordinal()) {
            return;
        }
        currentStep = step;
        currentPage = Page.MARKING;
        display(getScreen(step));
    }

    /**
     * Shows a tab. Grading shows the step the instructor was on. Leaving
     * Settings with changes that are not saved asks first.
     *
     * @param page the tab
     */
    public void showPage(Page page) {
        boolean leavesSettings = currentPage == Page.SETTINGS
                && page != Page.SETTINGS;
        if (leavesSettings && hasUnsavedSettings()) {
            askLeaveSettings(page);
            return;
        }
        if (page == Page.MARKING) {
            Step step = currentStep;
            if (step.ordinal() > getFurthestOpen().ordinal()) {
                // Another section was chosen meanwhile, which has not
                // got this far
                step = getFurthestOpen();
            }
            show(step);
            return;
        }
        currentPage = page;
        display(getPageScreen(page));
    }

    /**
     * Returns the tab on screen.
     *
     * @return the tab
     */
    public Page getCurrentPage() {
        return currentPage;
    }

    /**
     * Opens the Statistics tab on one section.
     *
     * @param moduleCode the module, such as CS102
     * @param sectionCode the section, such as G1
     */
    public void openStatistics(String moduleCode, String sectionCode) {
        StatisticsScreen statistics =
                (StatisticsScreen) getPageScreen(Page.STATISTICS);
        statistics.selectSection(moduleCode, sectionCode);
        showPage(Page.STATISTICS);
    }

    /**
     * Opens the Statistics tab on one run: its section and assessment,
     * with that run picked, even when it is an earlier one.
     *
     * @param run the run, which has a section and an assessment
     */
    public void openStatistics(GradingRun run) {
        StatisticsScreen statistics =
                (StatisticsScreen) getPageScreen(Page.STATISTICS);
        statistics.selectRun(run);
        showPage(Page.STATISTICS);
    }

    /**
     * Opens Set up on a section, as Home's cards do. Zip files chosen
     * for another section are cleared, so they are not graded as this
     * one.
     *
     * @param moduleCode the module, such as CS102
     * @param sectionCode the section, such as G2
     */
    public void openSetUp(String moduleCode, String sectionCode) {
        if (!chooseSection(moduleCode, sectionCode)) {
            notify("Wait until " + state.getSection().getLabel()
                    + " has finished grading.");
            show(Step.GRADE);
            return;
        }
        show(Step.SET_UP);
    }

    /**
     * Makes a section the one being set up, as Home's dropdowns do. Zip
     * files chosen for another section are put away, so they are not
     * graded as this one. A section being graded carries on grading
     * while another is set up or graded.
     *
     * @param moduleCode the module, such as CS102
     * @param sectionCode the section, such as G2
     * @return true when the section is now the one set up
     */
    public boolean chooseSection(String moduleCode, String sectionCode) {
        boolean otherSection = !sectionCode.equals(state.getSectionCode())
                || !moduleCode.equals(state.getModuleCode());
        if (otherSection) {
            // FlowState puts this section's zip files away and brings
            // back the ones chosen earlier for the new section
            state.setModuleCode(moduleCode);
            state.setSectionCode(sectionCode);
        } else if (!state.isGrading()) {
            // Opening a graded section again shows the files it was
            // graded with, if they were put away by Grade another
            state.restoreSubmissions();
        }
        return true;
    }

    /**
     * Opens the list of saved assessments.
     */
    public void openAssessments() {
        showSheet(AssessmentsSheet.create(this));
    }

    /**
     * Opens the assessment editor on a saved assessment, or on a new one.
     *
     * @param name the assessment to edit, or null to create one
     */
    public void editAssessment(String name) {
        SavedAssessment start = null;
        String originalName = null;
        if (name != null) {
            for (SavedAssessment saved : backend.listAssessments()) {
                if (saved.getName().equalsIgnoreCase(name)) {
                    start = saved;
                    originalName = saved.getName();
                }
            }
        }
        if (start == null) {
            start = newAssessment();
        }
        showSheet(AssessmentEditor.create(this, start, originalName));
    }

    /**
     * Grades with a saved assessment from now on, and says so. Does
     * nothing while a batch is being graded.
     *
     * @param name the assessment's name
     * @return true when it is now in use
     */
    public boolean useAssessment(String name) {
        // Grading reads the assessment's tester and starter folders from
        // the settings, so they stay while any section grades
        if (state.isAnyGrading()) {
            notify("Wait until every section has finished grading, then "
                    + "switch the assessment.");
            return false;
        }
        ActionResult result = backend.useAssessment(name);
        notify(result.getMessage());
        if (result.isDone()) {
            assessmentChanged();
        }
        return result.isDone();
    }

    /**
     * Deletes a saved assessment that is not in use, with its own score
     * sheets and the files chosen for it on Set up in every module and
     * section, so a new assessment with its name starts with none. The
     * delete is logged, and the notice and History offer Undo, which puts
     * all of it back.
     *
     * @param name the assessment's name
     * @param afterUndo what to show once Undo from the notice put it
     *        back, or null
     * @return true when it was deleted
     */
    public boolean deleteAssessment(String name, Runnable afterUndo) {
        ActionResult result = backend.deleteAssessment(name);
        if (!result.isDone()) {
            notify(result.getMessage());
            return false;
        }
        state.forgetAssessment(name);
        reloadSetUpClassFiles();
        Supplier<ActionResult> restore = ActivityUndo.record(backend,
                ActivityUndo.ASSESSMENT_DELETED, name + DELETED_INPUTS,
                () -> restoreAssessment(name));
        notify(result.getMessage(), "Undo",
                () -> undoAssessmentDeleted(restore, afterUndo));
        return true;
    }

    // The Undo of a deleted assessment from its notice.
    private void undoAssessmentDeleted(Supplier<ActionResult> restore,
            Runnable afterUndo) {
        ActionResult result = restore.get();
        if (!result.isDone()) {
            notify(result.getMessage());
        } else if (afterUndo != null) {
            afterUndo.run();
        }
    }

    // Puts back a deleted assessment and the files chosen for it on Set
    // up, so Set up lists them again.
    private ActionResult restoreAssessment(String name) {
        ActionResult result = backend.restoreAssessment(name);
        if (!result.isDone()) {
            return result;
        }
        if (!backend.isDemo()) {
            ChosenSubmissionsStore store =
                    ChosenSubmissionsStore.forSubmissions(backend.getSetting(
                            ConfigKeys.CHOSEN_SUBMISSIONS_FILE));
            Map<String, List<Path>> saved = store.load();
            Map<String, List<Path>> own = new HashMap<>();
            for (String key : saved.keySet()) {
                if (ChosenSubmissionsStore.isKeyOf(key, name)) {
                    own.put(key, saved.get(key));
                }
            }
            state.putBackSavedPaths(own);
        }
        reloadSetUpClassFiles();
        return result;
    }

    /**
     * Moves the files chosen on Set up for a renamed assessment to its
     * new name, after the library moved the saved ones.
     *
     * @param oldName the name it had
     * @param newName its new name
     */
    public void assessmentRenamed(String oldName, String newName) {
        state.moveAssessment(oldName, newName);
        reloadSetUpClassFiles();
    }

    // Has Set up read the class files chosen for each section again, as
    // an assessment's lines in their file changed.
    private void reloadSetUpClassFiles() {
        FlowScreen setUp = screens.get(Step.SET_UP);
        if (setUp instanceof SetUpScreen) {
            ((SetUpScreen) setUp).reloadClassFiles();
        }
    }

    /**
     * Updates the screens after the assessment in use or one of its
     * settings changed. Settings is built again when next shown, unless
     * it holds changes that are not saved.
     */
    public void assessmentChanged() {
        if (!state.isAnyGrading()) {
            noteAssessmentInUse();
        }
        if (!hasUnsavedSettings()) {
            pages.remove(Page.SETTINGS);
        }
        FlowScreen setUp = screens.get(Step.SET_UP);
        if (setUp instanceof SetUpScreen) {
            ((SetUpScreen) setUp).assessmentChanged();
        }
        boolean keepsSettings = currentPage == Page.SETTINGS
                && hasUnsavedSettings();
        if (sheet == null && !keepsSettings) {
            refreshScreen();
        }
    }

    /**
     * Lets the instructor open the next step and shows it.
     *
     * @param step the step to show
     */
    public void advanceTo(Step step) {
        state.reach(step);
        currentStep = step;
        currentPage = Page.MARKING;
        display(getScreen(step));
    }

    // Generated by Claude; modified for waiting while a fix is regraded
    /**
     * Starts grading the batch chosen on Set up and shows the Grade
     * step. Does nothing while a batch is being graded.
     */
    public void startGrading() {
        if (state.isGrading() || !state.getBatch().isReady()) {
            return;
        }
        if (blockedByFix()) {
            return;
        }
        GradeScreen grade = gradeScreen();
        state.restart();
        advanceTo(Step.GRADE);
        grade.start();
        refreshContext();
    }

    /**
     * Grades the batch chosen on Set up into the section's existing run
     * and shows the Grade step. Does nothing while a batch is being
     * graded.
     *
     * @param run the run the batch joins
     */
    public void addToRun(GradingRun run) {
        if (state.isGrading() || !state.getBatch().isReady()) {
            return;
        }
        if (blockedByFix()) {
            return;
        }
        GradeScreen grade = gradeScreen();
        state.restart();
        advanceTo(Step.GRADE);
        grade.startAdding(run);
        refreshContext();
    }

    /**
     * Asks a question with two ways to go on, in a sheet. Esc or a click
     * outside closes it and does neither.
     *
     * @param title the question
     * @param detail one or two sentences under it
     * @param otherText the text of the second choice
     * @param onOther what to do for the second choice
     * @param primaryText the text of the main choice, filled in
     * @param onPrimary what to do for the main choice
     */
    public void choose(String title, String detail, String otherText,
            Runnable onOther, String primaryText, Runnable onPrimary) {
        Label text = Parts.subtitle(detail);
        text.setMaxWidth(Parts.TEXT_WIDTH);
        text.setWrapText(true);
        Button other = Parts.secondary(otherText, () -> confirmed(onOther));
        Button primary = Parts.secondary(primaryText,
                () -> confirmed(onPrimary));
        primary.getStyleClass().add("confirm-ok");
        HBox buttons = Parts.row(Parts.GAP,
                new Node[] {Parts.grow(), other, primary});
        VBox body = new VBox(Parts.GAP, Parts.title(title), text, buttons);
        body.getStyleClass().add("confirm");
        showSheet(body);
        primary.requestFocus();
    }

    /**
     * Grades the rest of a stopped run and shows the Grade step.
     *
     * @param run the stopped run
     */
    public void resumeRun(GradingRun run) {
        continueRun(run, false);
    }

    /**
     * Grades a run's failed submissions again and shows the Grade step.
     *
     * @param run the run
     */
    public void retryRun(GradingRun run) {
        continueRun(run, true);
    }

    /**
     * Says whether a fix job or a Grade again job still grades a student
     * again, and if so tells the instructor to wait, as grading cannot
     * start meanwhile.
     *
     * @return true while such a job runs
     */
    public boolean blockedByFix() {
        if (state.isRegrading()) {
            notify("Wait until the students have been graded again.");
            return true;
        }
        if (state.getFixInProgress() == null) {
            return false;
        }
        notify("Wait until the fix has finished grading "
                + state.getFixInProgress().getUsername() + " again.");
        return true;
    }

    /**
     * Shows a section's batch being graded: makes the section the one on
     * screen and opens the Grade step. A batch that has ended, such as
     * one stopped early, shows its run there as it ended.
     *
     * @param section the section
     */
    public void watchGrading(Section section) {
        GradingJob job = state.getJob(section);
        boolean ended = job != null && job.isEnded() && job.getRun() != null;
        if (ended) {
            // Its run is the one Grade shows, as it ended
            openRunOn(job.getRun(), Step.GRADE);
            return;
        }
        chooseSection(section.getModuleCode(), section.getSectionCode());
        state.reach(Step.GRADE);
        show(Step.GRADE);
    }

    /**
     * Shows a batch's progress: on Grade when its section is on screen,
     * and on Home's cards each time a student finishes. GradingJob calls
     * it on the window's thread.
     *
     * @param job the batch
     * @param counted true when a student has just finished
     */
    public void gradingProgressed(GradingJob job, boolean counted) {
        // The Grade step shows the section on screen and links to the
        // others with how far each has got
        gradeScreen().progressed(job);
        boolean homeShown = currentPage == Page.HOME && sheet == null;
        if (counted && homeShown) {
            refreshScreen();
        }
    }

    /**
     * Takes in a batch that has ended. The batch of the section on screen
     * becomes the run Review and Export show, and Grade moves on; a batch
     * of another section only says how it ended, with a link to it, so
     * the work on screen is not taken away. GradingJob calls it on the
     * window's thread.
     *
     * @param job the batch, ended
     */
    public void gradingEnded(GradingJob job) {
        GradingRun run = job.getRun();
        state.attachSkippedFiles(run, job.getSkippedFiles());
        if (job.isOf(state.getSection())) {
            endOnScreen(job);
            return;
        }
        Section section = job.getSection();
        String label = section.getLabel();
        if (job.getFailure() != null) {
            notify(label + " could not be graded.", "Show why",
                    () -> watchGrading(section));
        } else if (job.hasUnfinished()) {
            notify(label + " stopped before every student was graded.",
                    "Show", () -> watchGrading(section));
        } else if (run != null) {
            notify(label + " has finished grading.", "Open results",
                    () -> openRun(run));
        }
        if (sheet == null) {
            refreshScreen();
        }
    }

    // Takes in the ended batch of the section on screen: its run is the
    // one Review and Export show, and a finished one opens Review.
    private void endOnScreen(GradingJob job) {
        GradingRun run = job.getRun();
        if (run != null) {
            state.setRun(run);
            // A late batch adds students, so who has no submission changes
            state.forgetMissingStudents();
        }
        boolean finished = run != null && job.getFailure() == null
                && !job.hasUnfinished();
        if (finished) {
            state.reach(Step.REVIEW);
            notify("Grading of " + job.getSection().getLabel()
                    + " finished.");
        } else if (job.getFailure() != null && currentStep != Step.GRADE) {
            notify("Grading could not run. See step 2.");
        }
        gradeScreen().ended(job);
        refreshContext();
    }

    /**
     * Opens an earlier run on the Review step.
     *
     * @param run the run
     */
    public void openRun(GradingRun run) {
        openRunOn(run, Step.REVIEW);
    }

    /**
     * Opens a section's run on the step it needs, as Home's cards and the
     * Continue card on Set up do: Grade while it is being graded or
     * stopped early, Review while students need a look, else Export.
     *
     * @param run the run, which has a section
     */
    public void openSectionStep(GradingRun run) {
        Section section = run.getSection();
        if (state.isGrading(section)) {
            watchGrading(section);
            return;
        }
        openRunOn(run, SectionProgress.stepToOpen(state, backend, section,
                run));
    }

    /**
     * Opens a section on the step it needs: Set up when it has no run,
     * else its run as openSectionStep does. Used by the section picker of
     * the Grading tab.
     *
     * @param section the section
     * @param run the run shown for it, or null
     */
    public void openSection(Section section, GradingRun run) {
        if (state.isGrading(section)) {
            watchGrading(section);
        } else if (run == null) {
            openSetUp(section.getModuleCode(), section.getSectionCode());
        } else {
            openSectionStep(run);
        }
    }

    // Opens an earlier run on a step: Review and the steps before it can
    // be opened, and Export too when it is the step asked for.
    private void openRunOn(GradingRun run, Step step) {
        if (state.isGrading(run.getSection())) {
            notify("Wait until " + run.getSection().getLabel()
                    + " has finished grading.");
            return;
        }
        matchAssessment(run);
        state.restart();
        state.setRun(run);
        state.setModuleCode(run.getSection().getModuleCode());
        state.setSectionCode(run.getSection().getSectionCode());
        state.reach(Step.REVIEW);
        state.reach(step);
        show(step);
    }

    /**
     * Switches to a module's first assessment when the one in use is not
     * one of its own, so Home and Set up show a matching one. Does
     * nothing while any section grades, or when the module has none.
     *
     * @param module the module, such as CS102
     */
    public void useModuleAssessment(String module) {
        if (state.isAnyGrading()) {
            return;
        }
        List<SavedAssessment> own = listModuleAssessments(module);
        String active = "";
        if (backend.getAssessment() != null) {
            active = backend.getAssessment().getName();
        }
        for (SavedAssessment saved : own) {
            if (saved.getName().equalsIgnoreCase(active)) {
                return;
            }
        }
        if (!own.isEmpty()) {
            useAssessment(own.get(0).getName());
        }
    }

    /**
     * Lists a module's own assessments, and those of every module unless
     * it has only its own sections, as Home's dropdown lists them.
     *
     * @param module the module, such as CS102
     * @return its assessments
     */
    public List<SavedAssessment> listModuleAssessments(String module) {
        String setting = backend.getSetting(
                ConfigKeys.OWN_SECTIONS_ONLY_MODULES);
        boolean ownOnly = ModuleSections.hasOwnOnly(setting, module);
        return SavedAssessment.listedFor(backend.listAssessments(), module,
                ownOnly);
    }

    /**
     * Returns the furthest step the instructor may open for the section
     * chosen now. Steps reached for the run on screen count only when
     * that run is of this section. Else a section being graded, or whose
     * batch could not run, may open Grade, and any other only Set up, so
     * another section's results never show under its name. Its own
     * earlier run opens from the Continue card, Home or History.
     *
     * @return the step
     */
    public Step getFurthestOpen() {
        Section section = state.getSection();
        if (isRunOfSection(state.getRun())) {
            return state.getFurthest();
        } else if (state.isGrading(section)) {
            return Step.GRADE;
        }
        GradingJob job = state.getJob(section);
        if (job != null && job.getFailure() != null) {
            return Step.GRADE;
        }
        return Step.SET_UP;
    }

    // Says whether a run is of the section chosen now.
    private boolean isRunOfSection(GradingRun run) {
        return run != null && run.getSection() != null
                && run.getSection().getLabel().equalsIgnoreCase(
                        state.getSection().getLabel());
    }

    // Returns the step before which every step is done for the run on
    // screen: the furthest step reached, when the run open is of the
    // section chosen, else Set up, so a section not graded yet shows no
    // ticks past the step on screen.
    private Step findDoneBefore() {
        if (!isRunOfSection(state.getRun())) {
            return Step.SET_UP;
        }
        return state.getFurthest();
    }

    /**
     * Shows the screen on display again with fresh data, keeping any open
     * sheet. Called when grading ends while another screen is open.
     */
    public void refreshScreen() {
        FlowScreen screen = currentScreen();
        actionBar.reset();
        content.getChildren().setAll(screen.getView());
        stepBar.update(currentStep, getFurthestOpen(), findDoneBefore(),
                false);
        screen.onShow();
        refreshContext();
    }

    /**
     * Names the module, section and assessment on the Grading tab above
     * the steps, such as "CS102 · G1 · Lab Test 2 · graded 11 Nov, 00:32
     * · Change section", so a step opened again never hides which
     * section it is about. Set up calls it when its dropdowns change.
     */
    public void refreshContext() {
        contextRow.getChildren().clear();
        if (currentPage != Page.MARKING) {
            return;
        }
        Section section = state.getSection();
        GradingRun run = findContextRun();
        String names = section.getModuleCode() + CONTEXT_DOT
                + section.getSectionCode();
        String assessmentName = findContextAssessment(run);
        if (!assessmentName.isEmpty()) {
            names = names + CONTEXT_DOT + assessmentName;
        }
        Label namesLabel = Parts.strong(names);
        namesLabel.getStyleClass().addAll("small-text",
                "grading-context-names");
        Label status = Parts.muted(CONTEXT_DOT + describeContext(run,
                assessmentName) + CONTEXT_DOT);
        status.getStyleClass().addAll("small-text", "grading-context-status");
        Hyperlink change = Parts.link("Change section",
                () -> openSectionPicker());
        change.getStyleClass().addAll("small-text", "grading-context-link");
        Parts.tip(change, "Open another section of this module, or of "
                + "another module, on the step it needs.");
        contextRow.getChildren().addAll(namesLabel, status, change);
    }

    /**
     * Opens the sheet that lists the sections with how far each has got,
     * to open another one on the step it needs.
     */
    public void openSectionPicker() {
        showSheet(SectionPicker.create(this));
    }

    // Returns the run the line under the steps is about: the run on
    // screen when it is of the chosen section, else the section's run
    // graded with the assessment in use, or null.
    private GradingRun findContextRun() {
        Section section = state.getSection();
        GradingRun open = state.getRun();
        if (isRunOfSection(open)) {
            return open;
        }
        GradingRun last = backend.getRun(section);
        boolean lastMatches = last != null && last.getAssessment() != null
                && backend.getAssessment() != null
                && SectionProgress.isGradedWith(last,
                        backend.getAssessment().getName());
        if (lastMatches) {
            return last;
        }
        return null;
    }

    // Returns the assessment the line under the steps names: the run's,
    // else the one in use when it belongs to the chosen module, else "".
    private String findContextAssessment(GradingRun run) {
        if (run != null && run.getAssessment() != null) {
            return run.getAssessment().getName();
        }
        Assessment inUse = backend.getAssessment();
        if (inUse == null) {
            return "";
        }
        for (SavedAssessment saved : listModuleAssessments(
                state.getModuleCode())) {
            if (saved.getName().equalsIgnoreCase(inUse.getName())) {
                return inUse.getName();
            }
        }
        return "";
    }

    // Says how far the section on screen has got, such as "graded 11
    // Nov, 00:32", "not graded yet" or "grading now...".
    private String describeContext(GradingRun run, String assessmentName) {
        if (state.isGrading(state.getSection())) {
            return "grading now...";
        } else if (assessmentName.isEmpty()) {
            return "no assessment yet";
        } else if (run == null) {
            return "not graded yet";
        }
        String when = "graded";
        if (run.getStartedAt() != null) {
            when = "graded " + run.getStartedAt().format(CONTEXT_WHEN);
        }
        int waiting = SectionProgress.countWaiting(run);
        if (waiting > 0) {
            return when + ", stopped with " + waiting + " not graded";
        }
        return when;
    }

    /**
     * Forgets the run shown on Review and Export, so Grading starts again
     * on Set up. Called after a restore replaced the saved results.
     */
    public void forgetRun() {
        state.restart();
        currentStep = Step.SET_UP;
    }

    /**
     * Says whether a screen is the one the instructor sees now.
     *
     * @param screen the screen
     * @return true when on screen
     */
    public boolean isOnScreen(FlowScreen screen) {
        if (currentPage != Page.MARKING) {
            return pages.get(currentPage) == screen;
        }
        return screens.get(currentStep) == screen;
    }

    /**
     * Returns the step the Grading tab shows.
     *
     * @return the step
     */
    public Step getCurrentStep() {
        return currentStep;
    }

    /**
     * Opens the Help sheet over the current screen.
     */
    public void showHelp() {
        showSheet(HelpSheet.create(this));
    }

    /**
     * Closes any sheet and starts the guided tour. COULD.
     */
    public void startTour() {
        closeSheet();
        if (tour == null) {
            tour = new GuidedTour(this);
        }
        tour.start();
    }

    /**
     * Offers the guided tour, the first time GradeZZY opens only. COULD.
     */
    public void offerTourOnce() {
        if (!GuidedTour.firstOffer(backend)) {
            return;
        }
        choose("New to GradeZZY?", "A one-minute tour points at each "
                + "part of the window and says what it does. You can take "
                + "it later from Help.", "Not now", () -> closeSheet(),
                "Take the tour", () -> startTour());
    }

    /**
     * Returns the guided tour, for the developer checks.
     *
     * @return the tour, or null before it was first started
     */
    public GuidedTour getTour() {
        return tour;
    }

    // Lays the tour over the whole window, top bar and bottom bar too.
    void showOverlay(Node overlay) {
        if (!root.getChildren().contains(overlay)) {
            root.getChildren().add(overlay);
        }
    }

    // Takes the tour off the window.
    void hideOverlay(Node overlay) {
        root.getChildren().remove(overlay);
    }

    // Generated by Claude; modified for a one-line lambda, a named index
    /**
     * Shows a sheet over the screen, dimming what is behind it.
     *
     * @param body the sheet's content
     */
    public void showSheet(Node body) {
        closeSheet();
        VBox box = new VBox(body);
        box.getStyleClass().add("sheet");
        box.setMaxWidth(VBox.USE_PREF_SIZE);
        box.setMaxHeight(VBox.USE_PREF_SIZE);
        StackPane dim = new StackPane(box);
        dim.getStyleClass().add("sheet-dim");
        dim.setOnMouseClicked(event -> clickBehindSheet(event, dim));
        sheet = dim;
        // Under the notice, which is the last layer
        int belowNotice = layers.getChildren().size() - 1;
        layers.getChildren().add(belowNotice, dim);
        actionBar.setDisable(true);
    }

    /**
     * Closes the sheet when the dimmed area around it is clicked, but
     * not when the sheet itself is clicked.
     *
     * @param event the click
     * @param dim the dimmed area that holds the sheet
     */
    private void clickBehindSheet(MouseEvent event, StackPane dim) {
        if (event.getTarget() == dim) {
            closeSheet();
        }
    }

    /**
     * Closes the open sheet, if any.
     */
    public void closeSheet() {
        if (sheet != null) {
            layers.getChildren().remove(sheet);
            sheet = null;
        }
        actionBar.setDisable(false);
    }

    /**
     * Asks a yes or no question in a sheet.
     *
     * @param title the question
     * @param detail one or two sentences under it
     * @param okText the text of the button that goes ahead
     * @param onOk what to do when the instructor goes ahead
     */
    public void confirm(String title, String detail, String okText,
            Runnable onOk) {
        Label text = Parts.subtitle(detail);
        text.setMaxWidth(Parts.TEXT_WIDTH);
        confirm(title, text, okText, onOk);
    }

    /**
     * Asks a yes or no question in a sheet, with something richer than
     * a sentence under it, such as a table.
     *
     * @param title the question
     * @param content what is shown under the question
     * @param okText the text of the button that goes ahead
     * @param onOk what to do when the instructor goes ahead
     */
    public void confirm(String title, Node content, String okText,
            Runnable onOk) {
        confirm(title, content, null, null, okText, onOk);
    }

    /**
     * Asks a question in a sheet with two ways to go ahead besides
     * Cancel, such as Just this one and Keep all.
     *
     * @param title the question
     * @param content what is shown under the question
     * @param otherText the text of the plain button that goes ahead the
     *        other way, or null for none
     * @param onOther what to do when that button is pressed
     * @param okText the text of the filled button that goes ahead
     * @param onOk what to do when the instructor goes ahead
     */
    public void confirm(String title, Node content, String otherText,
            Runnable onOther, String okText, Runnable onOk) {
        confirm(title, content, CANCEL_TEXT, otherText, onOther, okText,
                onOk, true);
    }

    /**
     * Asks a question in a sheet, naming the button that does nothing,
     * such as Keep grading, and saying which button is the default: the
     * filled one, which has the focus.
     *
     * @param title the question
     * @param content what is shown under the question
     * @param cancelText the text of the button that does nothing
     * @param otherText the text of the plain button that goes ahead the
     *        other way, or null for none
     * @param onOther what to do when that button is pressed
     * @param okText the text of the button that goes ahead
     * @param onOk what to do when the instructor goes ahead
     * @param okIsDefault true when going ahead is the default; false
     *        makes the button that does nothing the default, as when
     *        going ahead is not safe
     */
    public void confirm(String title, Node content, String cancelText,
            String otherText, Runnable onOther, String okText,
            Runnable onOk, boolean okIsDefault) {
        Button cancel = Parts.secondary(cancelText, () -> closeSheet());
        // The buttons have the same shape; the default one is filled, in
        // red when going ahead deletes something
        Button ok = Parts.secondary(okText, () -> confirmed(onOk));
        if (isDestructive(okText)) {
            ok.getStyleClass().add("confirm-danger");
        } else if (okIsDefault) {
            ok.getStyleClass().add("confirm-ok");
        } else {
            cancel.getStyleClass().add("confirm-ok");
        }
        HBox buttons = Parts.row(Parts.GAP,
                new Node[] {Parts.grow(), cancel, ok});
        if (otherText != null) {
            Button other = Parts.secondary(otherText,
                    () -> confirmed(onOther));
            // Between Cancel and the filled button
            int beforeOk = buttons.getChildren().size() - 1;
            buttons.getChildren().add(beforeOk, other);
        }
        VBox body = new VBox(Parts.GAP, Parts.title(title), content,
                buttons);
        body.getStyleClass().add("confirm");
        showSheet(body);
        if (okIsDefault) {
            ok.requestFocus();
        } else {
            cancel.requestFocus();
        }
    }

    /**
     * Asks before the window closes while a batch is being graded or a
     * setting is not saved. Closes at once otherwise.
     *
     * @param event the request to close the window
     */
    public void closeRequested(WindowEvent event) {
        if (state.isAnyGrading()) {
            event.consume();
            confirm("Grading is still running. Stop and close?",
                    "Students already graded keep their marks. GradeZZY "
                    + "closes after the current student.", "Stop and close",
                    () -> stopAndClose());
        } else if (hasUnsavedSettings()) {
            event.consume();
            confirm("Close without saving?", "Your changes in Settings "
                    + "are not saved yet.", "Close without saving",
                    () -> closeWindow());
        }
    }

    // Says whether the Settings page holds changes that are not saved,
    // in a setting or in its assessment form.
    private boolean hasUnsavedSettings() {
        FlowScreen screen = pages.get(Page.SETTINGS);
        if (!(screen instanceof SettingsScreen)) {
            return false;
        }
        SettingsScreen settings = (SettingsScreen) screen;
        return settings.hasUnsavedChanges()
                || settings.hasUnsavedAssessment();
    }

    /**
     * Shows a dot on the Settings tab while Settings holds changes that
     * are not saved, and hides it otherwise.
     */
    public void refreshSettingsMark() {
        tabBar.setPending(Page.SETTINGS, hasUnsavedSettings());
    }

    // Asks whether to save the changes in Settings before showing
    // another tab: Save, Discard or Stay on Settings.
    private void askLeaveSettings(Page page) {
        SettingsScreen settings = (SettingsScreen) pages.get(Page.SETTINGS);
        Label text = Parts.subtitle(settings.describeUnsaved());
        text.setMaxWidth(Parts.TEXT_WIDTH);
        Button stay = Parts.secondary("Stay", () -> closeSheet());
        Button discard = Parts.secondary("Discard",
                () -> leaveSettings(page, false));
        Button save = Parts.secondary("Save", () -> leaveSettings(page,
                true));
        save.getStyleClass().add("confirm-ok");
        HBox buttons = Parts.row(Parts.GAP,
                new Node[] {Parts.grow(), stay, discard, save});
        VBox body = new VBox(Parts.GAP,
                Parts.title("Save your Settings changes?"), text, buttons);
        body.getStyleClass().add("confirm");
        showSheet(body);
        save.requestFocus();
    }

    // Saves or throws away the changes in Settings, then shows the tab.
    // A change that cannot be saved keeps Settings on screen.
    private void leaveSettings(Page page, boolean saving) {
        closeSheet();
        SettingsScreen settings = (SettingsScreen) pages.get(Page.SETTINGS);
        if (saving) {
            settings.saveAll();
        } else {
            settings.discardAll();
        }
        if (!hasUnsavedSettings()) {
            showPage(page);
        }
    }

    // Stops grading after the current student, then closes the window.
    private void stopAndClose() {
        for (GradingJob job : state.getRunningJobs()) {
            job.markCancelling();
            backend.cancelGrading(job.getSection());
        }
        actionBar.setStatus("Stopping after the current student, then "
                + "closing.");
        closeWhenStopped();
    }

    // Closes the window once grading has stopped, checking again later
    // while it has not.
    private void closeWhenStopped() {
        if (!state.isAnyGrading()) {
            closeWindow();
            return;
        }
        PauseTransition wait = new PauseTransition(CLOSE_CHECK_TIME);
        wait.setOnFinished(event -> closeWhenStopped());
        wait.play();
    }

    // Closes the window without asking again.
    private void closeWindow() {
        frame.getScene().getWindow().hide();
    }

    // Says whether a confirm button's words delete or remove something.
    private static boolean isDestructive(String okText) {
        String words = okText.toLowerCase(Locale.ROOT);
        for (String start : DESTRUCTIVE_WORDS) {
            if (words.startsWith(start)) {
                return true;
            }
        }
        return false;
    }

    /**
     * Closes the question's sheet, then goes ahead.
     *
     * @param onOk what to do when the instructor goes ahead
     */
    private void confirmed(Runnable onOk) {
        closeSheet();
        onOk.run();
    }

    /**
     * Returns the students on a run's score sheet with no submission. The
     * first time, live mode reads the score sheet on a background thread,
     * returns an empty list and runs whenDone once the students are known.
     *
     * @param run the run
     * @param whenDone what to run once a background lookup ends
     * @return the students, or an empty list while they are looked up
     */
    public List<Student> findMissingStudents(GradingRun run,
            Runnable whenDone) {
        List<Student> known = state.getMissingStudents(run);
        if (known != null) {
            return known;
        }
        if (backend.isDemo()) {
            state.setMissingStudents(run, backend.listMissingStudents(run));
            return state.getMissingStudents(run);
        }
        missingCallbacks.add(whenDone);
        if (missingLookup == null || missingLookup.getRun() != run) {
            MissingStudentsLookup lookup = new MissingStudentsLookup(
                    backend, run);
            lookup.setOnSucceeded(event -> missingFound(lookup));
            lookup.setOnFailed(event -> missingNotFound(lookup));
            missingLookup = lookup;
            Thread worker = new Thread(lookup, ROSTER_THREAD);
            worker.setDaemon(true);
            worker.start();
        }
        return new ArrayList<>();
    }

    // Keeps the students a finished lookup found and tells the screens.
    private void missingFound(MissingStudentsLookup lookup) {
        if (lookup != missingLookup) {
            return;
        }
        missingLookup = null;
        state.setMissingStudents(lookup.getRun(), lookup.getValue());
        runMissingCallbacks();
    }

    // Treats a failed lookup as no student missing, so it is not retried.
    private void missingNotFound(MissingStudentsLookup lookup) {
        if (lookup != missingLookup) {
            return;
        }
        missingLookup = null;
        state.setMissingStudents(lookup.getRun(), new ArrayList<>());
        runMissingCallbacks();
    }

    // Runs, once each, what the screens asked to run after the lookup.
    private void runMissingCallbacks() {
        List<Runnable> callbacks = new ArrayList<>(missingCallbacks);
        missingCallbacks.clear();
        for (Runnable callback : callbacks) {
            callback.run();
        }
    }

    /**
     * Gives the window the application's host services, so it can open
     * a folder in Finder or File Explorer.
     *
     * @param services the host services, or null for none
     */
    public void setHostServices(HostServices services) {
        hostServices = services;
    }

    /**
     * Opens a folder in Finder or File Explorer. When the folder does
     * not exist yet, or nothing can open it, a notice says where it is
     * instead.
     *
     * @param folder the folder, as the settings give it
     */
    public void openFolder(String folder) {
        File file = new File(folder).getAbsoluteFile();
        if (!file.isDirectory()) {
            notify("Nothing is saved in " + folder + " yet.");
            return;
        }
        if (hostServices == null) {
            notify("The files are in " + file.getPath() + ".");
            return;
        }
        hostServices.showDocument(file.toURI().toString());
    }

    /**
     * Shows a short notice at the bottom of the window for a few
     * seconds.
     *
     * @param message the notice
     */
    public void notify(String message) {
        showNotice(message, null, null);
    }

    /**
     * Shows a short notice with a link that takes the change back, such
     * as "Marked as checked. Undo". It stays a little longer, so there is
     * time to press the link.
     *
     * @param message the notice
     * @param actionText the link's words, such as "Undo"
     * @param action what the link does
     */
    public void notify(String message, String actionText, Runnable action) {
        showNotice(message, actionText, action);
    }

    // Hides the notice, then does what its link offered.
    private void noticeActionChosen(Runnable action) {
        notice.setVisible(false);
        action.run();
    }

    // Shows the notice, with a link when an action is given.
    private void showNotice(String message, String actionText,
            Runnable action) {
        notice.setText(message);
        notice.setVisible(true);
        Duration time = NOTICE_TIME;
        if (action == null) {
            notice.setGraphic(null);
            notice.setMouseTransparent(true);
        } else {
            Hyperlink link = new Hyperlink(actionText);
            link.getStyleClass().add("notice-action");
            link.setOnAction(event -> noticeActionChosen(action));
            notice.setGraphic(link);
            notice.setContentDisplay(ContentDisplay.RIGHT);
            notice.setMouseTransparent(false);
            time = NOTICE_TIME.multiply(ACTION_NOTICE_FACTOR);
        }
        if (noticeTimer != null) {
            noticeTimer.stop();
        }
        noticeTimer = new PauseTransition(time);
        noticeTimer.setOnFinished(event -> notice.setVisible(false));
        noticeTimer.play();
    }

    /**
     * Switches between the light and dark theme.
     *
     * @param useDark true for the dark theme
     */
    public void setDark(boolean useDark) {
        dark = useDark;
        themes.apply(themeName());
        applyDarkClass();
    }

    /**
     * Says whether the dark theme is on.
     *
     * @return true for dark
     */
    public boolean isDark() {
        return dark;
    }

    /**
     * Returns the backend every screen calls.
     *
     * @return the backend
     */
    public DashboardBackend getBackend() {
        return backend;
    }

    /**
     * Returns the shared state.
     *
     * @return the state
     */
    public FlowState getState() {
        return state;
    }

    /**
     * Returns the bottom bar.
     *
     * @return the action bar
     */
    public ActionBar getActionBar() {
        return actionBar;
    }

    /**
     * Returns the operating-system window, for file choosers.
     *
     * @return the window, or null before the scene is shown
     */
    public Window getWindow() {
        if (frame.getScene() == null) {
            return null;
        }
        return frame.getScene().getWindow();
    }

    /**
     * Returns the tabs, for the developer checks.
     *
     * @return the tab bar
     */
    public TabBar getTabBar() {
        return tabBar;
    }

    /**
     * Says whether a sheet is open, for the developer checks.
     *
     * @return true while a sheet is open
     */
    public boolean isSheetOpen() {
        return sheet != null;
    }

    /**
     * Returns the root of the scene, for the developer checks.
     *
     * @return the root
     */
    public BorderPane getRoot() {
        return frame;
    }

    // Resumes or retries a run on the Grade step.
    private void continueRun(GradingRun run, boolean retryFailed) {
        if (state.isGrading(run.getSection())) {
            notify("Wait until " + run.getSection().getLabel()
                    + " has finished grading.");
            return;
        }
        if (blockedByFix()) {
            return;
        }
        matchAssessment(run);
        GradeScreen grade = gradeScreen();
        state.restart();
        state.setRun(run);
        state.setModuleCode(run.getSection().getModuleCode());
        state.setSectionCode(run.getSection().getSectionCode());
        advanceTo(Step.GRADE);
        grade.continueRun(run, retryFailed);
        refreshContext();
    }

    /**
     * Returns the screen of a step, creating it if needed. Used by the
     * developer checks.
     *
     * @param step the step
     * @return the screen
     */
    public FlowScreen getScreen(Step step) {
        FlowScreen screen = screens.get(step);
        if (screen == null) {
            screen = createScreen(step);
            screens.put(step, screen);
        }
        return screen;
    }

    /**
     * Returns the screen of a tab other than Grading, creating it if
     * needed. Used by the developer checks.
     *
     * @param page Home, Statistics, Runs or Settings
     * @return the screen
     */
    public FlowScreen getPageScreen(Page page) {
        FlowScreen screen = pages.get(page);
        if (screen == null) {
            screen = createPage(page);
            pages.put(page, screen);
        }
        return screen;
    }

    // Switches to the assessment a run was graded with, when another is
    // in use, so its questions, testers and checks match the run. A run
    // whose assessment is no longer saved is shown with a note.
    private void matchAssessment(GradingRun run) {
        Assessment graded = run.getAssessment();
        Assessment inUse = backend.getAssessment();
        boolean nothingToSwitch = graded == null || inUse == null
                || graded.getName().equalsIgnoreCase(inUse.getName());
        // The run keeps its own questions and testers, so while other
        // sections grade the assessment in use is left as it is
        if (nothingToSwitch || state.isAnyGrading()) {
            return;
        }
        for (SavedAssessment saved : backend.listAssessments()) {
            if (saved.getName().equalsIgnoreCase(graded.getName())) {
                ActionResult result = backend.useAssessment(saved.getName());
                if (result.isDone()) {
                    notify("Switched to " + saved.getName() + ", the "
                            + "assessment this run was graded with.");
                    assessmentChanged();
                } else {
                    notify(result.getMessage());
                }
                return;
            }
        }
        notify("This run was graded with " + graded.getName() + ", which "
                + "is no longer saved. Grading again uses "
                + inUse.getName() + ".");
    }

    // Returns the start of a new assessment: every field empty, for the
    // module chosen now.
    SavedAssessment newAssessment() {
        // Every field starts empty: the instructor fills it in, or the
        // chosen folders do. It belongs to the module chosen now.
        SavedAssessment fresh = new SavedAssessment();
        fresh.setModuleCode(state.getModuleCode());
        return fresh;
    }

    // Puts a screen in the middle of the window and fills the bars.
    private void display(FlowScreen screen) {
        closeSheet();
        actionBar.reset();
        content.getChildren().setAll(screen.getView());
        boolean marking = currentPage == Page.MARKING;
        stepRow.setVisible(marking);
        stepRow.setManaged(marking);
        contextRow.setVisible(marking);
        contextRow.setManaged(marking);
        tabBar.select(currentPage);
        refreshSettingsMark();
        stepBar.update(currentStep, getFurthestOpen(), findDoneBefore(),
                false);
        if (screen.usesActionBar()) {
            frame.setBottom(actionBar);
        } else {
            frame.setBottom(null);
        }
        screen.onShow();
        refreshContext();
    }

    // Returns the screen on display.
    private FlowScreen currentScreen() {
        if (currentPage == Page.MARKING) {
            return getScreen(currentStep);
        }
        return getPageScreen(currentPage);
    }

    // Returns the Grade screen, creating it if needed.
    private GradeScreen gradeScreen() {
        return (GradeScreen) getScreen(Step.GRADE);
    }

    // Creates the screen of a step.
    private FlowScreen createScreen(Step step) {
        if (step == Step.GRADE) {
            return new GradeScreen(this);
        } else if (step == Step.REVIEW) {
            return new ReviewScreen(this);
        } else if (step == Step.EXPORT) {
            return new ExportScreen(this);
        }
        return new SetUpScreen(this);
    }

    // Creates the screen of a tab other than Grading.
    private FlowScreen createPage(Page page) {
        if (page == Page.HOME) {
            return new HomeScreen(this);
        } else if (page == Page.STATISTICS) {
            return new StatisticsScreen(this);
        } else if (page == Page.RUNS) {
            return new RunsScreen(this);
        }
        return new SettingsScreen(this);
    }

    // Builds the logo on the left, the tabs in the exact middle of the
    // window and Help on the right.
    private StackPane createTopBar() {
        Node mark = createLogoMark();
        Label name = new Label("GradeZZY");
        name.getStyleClass().add("logo-name");
        HBox logo = Parts.row(Parts.GAP_SMALL, new Node[] {mark, name});
        logo.setMaxWidth(Region.USE_PREF_SIZE);
        StackPane.setAlignment(logo, Pos.CENTER_LEFT);

        tabBar.setMaxWidth(Region.USE_PREF_SIZE);
        StackPane.setAlignment(tabBar, Pos.CENTER);

        Button help = topLink("Help", Parts.HELP, () -> showHelp());
        StackPane.setAlignment(help, Pos.CENTER_RIGHT);

        StackPane bar = new StackPane(logo, tabBar, help);
        bar.getStyleClass().add("top-bar");
        return bar;
    }

    // Returns the logo picture, or a "G" tile if it cannot be read.
    private Node createLogoMark() {
        Image picture = AppIcons.loadTopBarLogo();
        if (picture == null) {
            Label tile = new Label("G");
            tile.getStyleClass().add("logo-mark");
            return tile;
        }
        ImageView logo = new ImageView(picture);
        logo.setFitWidth(LOGO_SIZE);
        logo.setFitHeight(LOGO_SIZE);
        logo.setPreserveRatio(true);
        logo.setSmooth(true);
        return logo;
    }

    // Builds one text button with an icon for the top bar.
    private Button topLink(String text, String iconPath, Runnable action) {
        Button button = Parts.secondary(text, action);
        button.getStyleClass().setAll("button", "top-link");
        button.setGraphic(Parts.icon(iconPath, TOP_ICON_SIZE));
        return button;
    }

    // Adds the keyboard shortcuts of the window.
    private void addShortcuts(Scene scene) {
        scene.addEventFilter(KeyEvent.KEY_PRESSED,
                event -> handleShortcut(event));
    }

    /**
     * Acts on a key press before any control sees it: Ctrl or Cmd and
     * Enter presses the blue button, Ctrl or Cmd and F puts the cursor
     * in the Review search, Escape closes a sheet or goes back, and Ctrl
     * or Cmd and 1 to 4 open a step. While the tour is on screen, the
     * tour takes every key instead.
     *
     * @param event the key press; consumed when it was a shortcut
     */
    private void handleShortcut(KeyEvent event) {
        boolean escape = event.getCode() == KeyCode.ESCAPE;
        if (tour != null && tour.isOpen()) {
            tour.handleKey(event);
        } else if (NEXT_KEYS.match(event)) {
            actionBar.firePrimary();
            event.consume();
        } else if (FIND_KEYS.match(event) && isReviewShown()) {
            ((ReviewScreen) getScreen(Step.REVIEW)).focusSearch();
            event.consume();
        } else if (escape && sheet != null) {
            closeSheet();
            event.consume();
        } else if (escape && currentScreen().onEscape()) {
            event.consume();
        } else if (event.isShortcutDown() && sheet == null) {
            for (int i = 0; i < STEP_KEYS.length; i++) {
                if (event.getCode() == STEP_KEYS[i]) {
                    show(Step.values()[i]);
                    event.consume();
                }
            }
        }
    }

    // Says whether the Review step is on screen with no sheet over it.
    private boolean isReviewShown() {
        return sheet == null && currentPage == Page.MARKING
                && currentStep == Step.REVIEW
                && state.getRun() != null;
    }

    // Returns the theme name ThemeManager expects.
    private String themeName() {
        if (dark) {
            return ThemeManager.DARK_THEME;
        }
        return ThemeManager.LIGHT_THEME;
    }

    // Adds or removes the dark class on the outer layout and on the root
    // of the scene, which also holds the tour.
    private void applyDarkClass() {
        frame.getStyleClass().remove(DARK_CLASS);
        root.getStyleClass().remove(DARK_CLASS);
        if (dark) {
            frame.getStyleClass().add(DARK_CLASS);
            root.getStyleClass().add(DARK_CLASS);
        }
    }
}
