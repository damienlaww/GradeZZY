/*
 * File: FlowWindow.java
 *
 * Purpose: The guided window: a top bar with the four steps, one screen
 * at a time in the middle, and the action bar at the bottom. Moves
 * between steps and opens History, Settings, Help and small sheets.
 *
 * Where it fits: MainApp builds one and shows its scene. Every screen
 * reaches the backend, the shared state and the action bar through it.
 */
package gradezzy.ui.flow;

import java.net.URL;
import java.util.EnumMap;
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
import javafx.scene.layout.BorderPane;
import javafx.scene.layout.HBox;
import javafx.scene.layout.StackPane;
import javafx.scene.layout.VBox;
import javafx.stage.Window;
import javafx.util.Duration;

import gradezzy.model.GradingRun;
import gradezzy.ui.ThemeManager;
import gradezzy.ui.backend.DashboardBackend;

/**
 * The window of the guided steps design.
 */
public class FlowWindow {

    /** The stylesheet of the guided window. */
    public static final String STYLESHEET = "/gradezzy/ui/css/flow.css";

    // How long a notice stays on screen
    private static final Duration NOTICE_TIME = Duration.seconds(3.5);

    // The size of the icons in the top bar
    private static final double TOP_ICON_SIZE = 16.0;

    // The gap between items in the top bar
    private static final double TOP_GAP = 4.0;

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

    // The four steps
    private final StepBar stepBar = new StepBar(step -> show(step));

    // The bottom bar
    private final ActionBar actionBar = new ActionBar();

    // The step screens, created when first shown
    private final Map<Step, FlowScreen> screens = new EnumMap<>(Step.class);

    // The notice that fades in at the bottom
    private final Label notice = new Label();

    // The step on screen, or under the open page
    private Step currentStep = Step.SET_UP;

    // The open page (History or Settings), or null
    private FlowScreen page = null;

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
        frame.setTop(createTopBar());
        frame.setBottom(actionBar);

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
     * Shows a step, if the instructor may open it.
     *
     * @param step the step
     */
    public void show(Step step) {
        if (step.ordinal() > state.getFurthest().ordinal()) {
            return;
        }
        closeSheet();
        page = null;
        currentStep = step;
        FlowScreen screen = screens.get(step);
        if (screen == null) {
            screen = createScreen(step);
            screens.put(step, screen);
        }
        actionBar.reset();
        content.getChildren().setAll(screen.getView());
        screen.onShow();
        stepBar.update(currentStep, state.getFurthest(), false);
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
     * Says whether a screen is the one the instructor sees now.
     *
     * @param screen the screen
     * @return true when on screen
     */
    public boolean isOnScreen(FlowScreen screen) {
        if (page != null) {
            return page == screen;
        }
        return screens.get(currentStep) == screen;
    }

    /**
     * Returns the step on screen, or under the open page.
     *
     * @return the step
     */
    public Step getCurrentStep() {
        return currentStep;
    }

    /**
     * Opens History over the current step.
     */
    public void showHistory() {
        openPage(new HistoryScreen(this));
    }

    /**
     * Opens Settings over the current step.
     */
    public void showSettings() {
        openPage(new SettingsScreen(this));
    }

    /**
     * Opens the Help sheet over the current screen.
     */
    public void showHelp() {
        showSheet(HelpSheet.create(this));
    }

    /**
     * Closes History or Settings and returns to the step.
     */
    public void closePage() {
        show(currentStep);
    }

    /**
     * Says whether History or Settings is open.
     *
     * @return true while a page is open
     */
    public boolean isPageOpen() {
        return page != null;
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
        dim.setOnMouseClicked(event -> {
            if (event.getTarget() == dim) {
                closeSheet();
            }
        });
        sheet = dim;
        layers.getChildren().add(layers.getChildren().size() - 1, dim);
    }

    /**
     * Closes the open sheet, if any.
     */
    public void closeSheet() {
        if (sheet != null) {
            layers.getChildren().remove(sheet);
            sheet = null;
        }
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
        ok.setOnAction(event -> {
            closeSheet();
            onOk.run();
        });
        Label text = Parts.subtitle(detail);
        text.setMaxWidth(Parts.TEXT_WIDTH);
        HBox buttons = Parts.row(Parts.GAP, Parts.grow(), cancel, ok);
        VBox body = new VBox(Parts.GAP, Parts.title(title), text,
                buttons);
        body.getStyleClass().add("confirm");
        showSheet(body);
        ok.requestFocus();
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

    // Shows History or Settings in place of the step.
    private void openPage(FlowScreen newPage) {
        closeSheet();
        page = newPage;
        actionBar.reset();
        content.getChildren().setAll(newPage.getView());
        newPage.onShow();
        stepBar.update(currentStep, state.getFurthest(), true);
    }

    // Builds the logo, step bar and the three links on the right.
    private HBox createTopBar() {
        Label mark = new Label("G");
        mark.getStyleClass().add("logo-mark");
        Label name = new Label("GradeZZY");
        name.getStyleClass().add("logo-name");
        HBox logo = Parts.row(Parts.GAP_SMALL, mark, name);

        HBox links = Parts.row(TOP_GAP,
                topLink("History", Parts.CLOCK, () -> showHistory()),
                topLink("Settings", Parts.GEAR, () -> showSettings()),
                topLink("Help", Parts.HELP, () -> showHelp()));

        HBox bar = Parts.row(0, logo, Parts.grow(), stepBar, Parts.grow(),
                links);
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
        KeyCombination next = new KeyCodeCombination(KeyCode.ENTER,
                KeyCombination.SHORTCUT_DOWN);
        KeyCode[] numbers = {KeyCode.DIGIT1, KeyCode.DIGIT2,
            KeyCode.DIGIT3, KeyCode.DIGIT4};
        scene.addEventFilter(KeyEvent.KEY_PRESSED, event -> {
            if (next.match(event)) {
                actionBar.firePrimary();
                event.consume();
            } else if (event.getCode() == KeyCode.ESCAPE && sheet != null) {
                closeSheet();
                event.consume();
            } else if (event.getCode() == KeyCode.ESCAPE && page != null) {
                closePage();
                event.consume();
            } else if (event.isShortcutDown()) {
                for (int i = 0; i < numbers.length; i++) {
                    if (event.getCode() == numbers[i]) {
                        show(Step.values()[i]);
                        event.consume();
                    }
                }
            }
        });
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
