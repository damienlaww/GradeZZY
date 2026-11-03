/*
 * File: FlowWindow.java
 *
 * Purpose: The window: a top bar with the tabs Marking, Statistics, Runs
 * and Settings; under it, on Marking only, the row of four steps; one
 * screen at a time in the middle; and the action bar at the bottom on the
 * screens that use it. Opens Help and small sheets over the screen.
 *
 * Where it fits: MainApp builds one and shows its scene. Every screen
 * reaches the backend, the shared state and the action bar through it.
 */
package gradezzy.ui.flow;

import java.net.URL;
import java.util.HashMap;
import java.util.Map;

import javafx.animation.PauseTransition;
import javafx.geometry.Pos;
import javafx.scene.Node;
import javafx.scene.Scene;
import javafx.scene.control.Button;
import javafx.scene.control.Label;
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
import javafx.util.Duration;

import gradezzy.model.GradingRun;
import gradezzy.ui.ThemeManager;
import gradezzy.ui.backend.DashboardBackend;

/**
 * The window of GradeZZY.
 */
public class FlowWindow {

    /** The stylesheet of the guided window. */
    public static final String STYLESHEET = "/gradezzy/ui/css/flow.css";

    // How long a notice stays on screen
    private static final Duration NOTICE_TIME = Duration.seconds(3.5);

    // The size of the icon of the Help button
    private static final double TOP_ICON_SIZE = 16.0;

    // The shortcut that presses the blue button: Ctrl or Cmd and Enter
    private static final KeyCombination NEXT_KEYS = new KeyCodeCombination(
            KeyCode.ENTER, KeyCombination.SHORTCUT_DOWN);

    // With Ctrl or Cmd, the keys that open steps 1 to 4
    private static final KeyCode[] STEP_KEYS = {KeyCode.DIGIT1,
        KeyCode.DIGIT2, KeyCode.DIGIT3, KeyCode.DIGIT4};

    // The backend every screen calls
    private final DashboardBackend backend;

    // What the window remembers between steps
    private final FlowState state = new FlowState();

    // Sets the AtlantaFX theme
    private final ThemeManager themes = new ThemeManager();

    // The outer layout
    private final BorderPane frame = new BorderPane();

    // Holds the screen, with sheets and notices on top
    private final StackPane layers = new StackPane();

    // Holds the current screen
    private final StackPane content = new StackPane();

    // The tabs along the top
    private final TabBar tabBar = new TabBar(page -> showPage(page));

    // The four steps
    private final StepBar stepBar = new StepBar(step -> show(step));

    // The row that holds the steps, shown on Marking only
    private final HBox stepRow = new HBox(stepBar);

    // The bottom bar
    private final ActionBar actionBar = new ActionBar();

    // The step screens, created when first shown
    private final Map<Step, FlowScreen> screens = new HashMap<>();

    // The screens of Statistics, Runs and Settings, created when first
    // shown
    private final Map<Page, FlowScreen> pages = new HashMap<>();

    // The notice that fades in at the bottom
    private final Label notice = new Label();

    // The step Marking shows
    private Step currentStep = Step.SET_UP;

    // The tab on screen
    private Page currentPage = Page.MARKING;

    // The open sheet, or null
    private Node sheet = null;

    // Hides the notice after a while
    private PauseTransition noticeTimer = null;

    // True for the dark theme
    private boolean dark;

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

    /**
     * Builds the scene and shows the first step.
     *
     * @param width the starting width
     * @param height the starting height
     * @return the scene
     */
    public Scene build(double width, double height) {
        themes.apply(themeName());
        frame.getStyleClass().add("flow-root");
        stepRow.getStyleClass().add("step-row");
        stepRow.setAlignment(Pos.CENTER);
        frame.setTop(new VBox(createTopBar(), stepRow));

        content.getStyleClass().add("flow-content");
        notice.getStyleClass().add("notice");
        notice.setVisible(false);
        notice.setMouseTransparent(true);
        StackPane.setAlignment(notice, Pos.BOTTOM_CENTER);
        layers.getChildren().addAll(content, notice);
        frame.setCenter(layers);
        applyDarkClass();

        Scene scene = new Scene(frame, width, height);
        URL css = FlowWindow.class.getResource(STYLESHEET);
        if (css != null) {
            scene.getStylesheets().add(css.toExternalForm());
        }
        addShortcuts(scene);
        show(Step.SET_UP);
        return scene;
    }

    /**
     * Shows a step on the Marking tab, if the instructor may open it.
     *
     * @param step the step
     */
    public void show(Step step) {
        if (step.ordinal() > state.getFurthest().ordinal()) {
            return;
        }
        currentStep = step;
        currentPage = Page.MARKING;
        display(getScreen(step));
    }

    /**
     * Shows a tab. Marking shows the step the instructor was on.
     *
     * @param page the tab
     */
    public void showPage(Page page) {
        if (page == Page.MARKING) {
            show(currentStep);
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
     * Lets the instructor open the next step and shows it.
     *
     * @param step the step to show
     */
    public void advanceTo(Step step) {
        state.reach(step);
        show(step);
    }


    /**
     * Starts grading the batch chosen on Set up and shows the Grade
     * step. Does nothing while a batch is being graded.
     */
    public void startGrading() {
        if (state.isGrading() || !state.getBatch().isReady()) {
            return;
        }
        GradeScreen grade = gradeScreen();
        state.restart();
        advanceTo(Step.GRADE);
        grade.start();
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
     * Opens an earlier run on the Review step.
     *
     * @param run the run
     */
    public void openRun(GradingRun run) {
        if (state.isGrading()) {
            notify("Wait for the current batch to finish first.");
            return;
        }
        state.restart();
        state.setRun(run);
        state.setModuleCode(run.getSection().getModuleCode());
        state.setSectionCode(run.getSection().getSectionCode());
        state.reach(Step.REVIEW);
        show(Step.REVIEW);
    }

    /**
     * Shows the screen on display again with fresh data, keeping any open
     * sheet. Called when grading ends while another screen is open.
     */
    public void refreshScreen() {
        FlowScreen screen = currentScreen();
        actionBar.reset();
        content.getChildren().setAll(screen.getView());
        stepBar.update(currentStep, state.getFurthest(), false);
        screen.onShow();
    }

    /**
     * Forgets the run shown on Review and Export, so Marking starts again
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
     * Returns the step the Marking tab shows.
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
        layers.getChildren().add(layers.getChildren().size() - 1, dim);
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
        Button cancel = Parts.secondary("Cancel", () -> closeSheet());
        Button ok = Parts.primary(okText);
        ok.setOnAction(event -> confirmed(onOk));
        Label text = Parts.subtitle(detail);
        text.setMaxWidth(Parts.TEXT_WIDTH);
        HBox buttons = Parts.row(Parts.GAP,
                new Node[] {Parts.grow(), cancel, ok});
        VBox body = new VBox(Parts.GAP, Parts.title(title), text,
                buttons);
        body.getStyleClass().add("confirm");
        showSheet(body);
        ok.requestFocus();
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
     * Shows a short notice at the bottom of the window for a few
     * seconds.
     *
     * @param message the notice
     */
    public void notify(String message) {
        notice.setText(message);
        notice.setVisible(true);
        if (noticeTimer != null) {
            noticeTimer.stop();
        }
        noticeTimer = new PauseTransition(NOTICE_TIME);
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
        if (state.isGrading()) {
            notify("Wait for the current batch to finish first.");
            return;
        }
        GradeScreen grade = gradeScreen();
        state.restart();
        state.setRun(run);
        state.setModuleCode(run.getSection().getModuleCode());
        state.setSectionCode(run.getSection().getSectionCode());
        advanceTo(Step.GRADE);
        grade.continueRun(run, retryFailed);
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
     * Returns the screen of a tab other than Marking, creating it if
     * needed. Used by the developer checks.
     *
     * @param page Statistics, Runs or Settings
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

    // Puts a screen in the middle of the window and fills the bars.
    private void display(FlowScreen screen) {
        closeSheet();
        actionBar.reset();
        content.getChildren().setAll(screen.getView());
        boolean marking = currentPage == Page.MARKING;
        stepRow.setVisible(marking);
        stepRow.setManaged(marking);
        tabBar.select(currentPage);
        stepBar.update(currentStep, state.getFurthest(), false);
        if (screen.usesActionBar()) {
            frame.setBottom(actionBar);
        } else {
            frame.setBottom(null);
        }
        screen.onShow();
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

    // Creates the screen of a tab other than Marking.
    private FlowScreen createPage(Page page) {
        if (page == Page.STATISTICS) {
            return new StatisticsScreen(this);
        } else if (page == Page.RUNS) {
            return new RunsScreen(this);
        }
        return new SettingsScreen(this);
    }

    // Builds the logo on the left, the tabs in the exact middle of the
    // window and Help on the right.
    private StackPane createTopBar() {
        Label mark = new Label("G");
        mark.getStyleClass().add("logo-mark");
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
     * Enter presses the blue button, Escape closes a sheet or goes back,
     * and Ctrl or Cmd and 1 to 4 open a step.
     *
     * @param event the key press; consumed when it was a shortcut
     */
    private void handleShortcut(KeyEvent event) {
        if (NEXT_KEYS.match(event)) {
            actionBar.firePrimary();
            event.consume();
        } else if (event.getCode() == KeyCode.ESCAPE && sheet != null) {
            closeSheet();
            event.consume();
        } else if (event.getCode() == KeyCode.ESCAPE
                && currentScreen().onEscape()) {
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

    // Returns the theme name ThemeManager expects.
    private String themeName() {
        if (dark) {
            return ThemeManager.DARK_THEME;
        }
        return ThemeManager.LIGHT_THEME;
    }

    // Adds or removes the dark class on the root.
    private void applyDarkClass() {
        frame.getStyleClass().remove("dark");
        if (dark) {
            frame.getStyleClass().add("dark");
        }
    }
}
