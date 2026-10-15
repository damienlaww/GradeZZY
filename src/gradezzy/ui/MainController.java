/*
 * File: MainController.java
 *
 * Purpose: Controller of the main window that hosts every other screen.
 *
 * Where it fits: Owns navigation (Next, Back, the screen on show and the
 * keyboard shortcuts) and the theme, and is the AppShell every screen
 * uses. The sidebar, the context bar, sheets and messages are drawn by
 * Sidebar, ContextBar, SheetLayer and ToastLayer in gradezzy.ui.view.
 *
 * AI use: drafted with Claude Code, reviewed by Jun Sheng.
 */
package gradezzy.ui;

import java.io.IOException;
import java.util.ArrayDeque;
import java.util.ArrayList;
import java.util.Deque;
import java.util.List;

import javafx.animation.FadeTransition;
import javafx.animation.Interpolator;
import javafx.fxml.FXML;
import javafx.geometry.Insets;
import javafx.scene.Node;
import javafx.scene.Parent;
import javafx.scene.Scene;
import javafx.scene.control.ComboBox;
import javafx.scene.control.Label;
import javafx.scene.control.MenuButton;
import javafx.scene.control.Separator;
import javafx.scene.control.TextArea;
import javafx.scene.control.ToggleButton;
import javafx.scene.control.ToggleGroup;
import javafx.scene.input.Clipboard;
import javafx.scene.input.ClipboardContent;
import javafx.scene.input.KeyCode;
import javafx.scene.input.KeyCodeCombination;
import javafx.scene.input.KeyCombination;
import javafx.scene.input.KeyEvent;
import javafx.scene.layout.BorderPane;
import javafx.scene.layout.HBox;
import javafx.scene.layout.Region;
import javafx.scene.layout.StackPane;
import javafx.scene.layout.VBox;
import javafx.stage.Window;
import javafx.util.Duration;

import gradezzy.model.Anomaly;
import gradezzy.model.GradingRun;
import gradezzy.ui.backend.Capability;
import gradezzy.ui.component.Icon;
import gradezzy.ui.component.NotConnectedPanel;
import gradezzy.ui.component.Responsive;
import gradezzy.ui.component.Sheet;
import gradezzy.ui.view.ContextBar;
import gradezzy.ui.view.ScreenShortcuts;
import gradezzy.ui.view.SheetLayer;
import gradezzy.ui.view.Sidebar;
import gradezzy.ui.view.ToastLayer;

/**
 * Controller of the main window that hosts every other screen.
 *
 * <p>Tier: MUST. Owner: Damien.
 */
public class MainController extends BaseController implements AppShell {

    /** The screens of the main grading journey, for Next and Back. */
    public static final Screen[] JOURNEY = {Screen.OVERVIEW, Screen.MODULES,
        Screen.TEST_CASES, Screen.UPLOAD, Screen.PROGRESS,
        Screen.ANOMALY_LOG, Screen.RESULTS, Screen.STATISTICS};

    // How long a screen takes to fade in
    private static final Duration FADE_TIME = Duration.millis(160);

    // The rows of a preview text area
    private static final int PREVIEW_ROWS = 14;

    // The style class of a compact sidebar
    private static final String COMPACT = "compact";

    // The style class of the window while animations are off
    private static final String NO_MOTION = "no-motion";

    // The gaps the assessment, run and separator add to the first row
    private static final int MOVED_GAPS = 3;

    @FXML
    private StackPane windowRoot;

    @FXML
    private BorderPane shellRoot;

    @FXML
    private StackPane sheetLayer;

    @FXML
    private VBox sheetHolder;

    @FXML
    private VBox sidebar;

    @FXML
    private VBox navList;

    @FXML
    private VBox footerNav;

    @FXML
    private VBox contextBar;

    @FXML
    private HBox contextRow;

    @FXML
    private HBox contextDetails;

    @FXML
    private MenuButton navMenu;

    @FXML
    private Label moduleLabel;

    @FXML
    private ComboBox<String> moduleBox;

    @FXML
    private Label sectionLabel;

    @FXML
    private ComboBox<String> sectionBox;

    @FXML
    private Separator contextSeparator;

    @FXML
    private VBox assessmentBox;

    @FXML
    private Label assessmentCaption;

    @FXML
    private Label assessmentLabel;

    @FXML
    private VBox runBox;

    @FXML
    private Label runCaption;

    @FXML
    private Label runLabel;

    @FXML
    private Label demoBadge;

    @FXML
    private ToggleButton lightButton;

    @FXML
    private ToggleButton darkButton;

    @FXML
    private StackPane overlayHost;

    @FXML
    private StackPane contentHost;

    @FXML
    private VBox toastLayer;

    // The screens shown before the current one, for Back
    private final Deque<Screen> history = new ArrayDeque<>();

    // Loads and keeps the screens
    private ScreenNavigator navigator = null;

    // Switches the theme
    private ThemeManager themeManager = null;

    // Draws the sidebar and its narrow-window menu
    private Sidebar sidebarView = null;

    // Keeps the context bar in step with the state
    private ContextBar context = null;

    // Shows sheets over the window
    private SheetLayer sheets = null;

    // Shows messages at the bottom of the window
    private ToastLayer toasts = null;

    // The screen on show
    private Screen current = null;

    // Whether confirmations run without asking, for the self-test
    private boolean autoConfirm = false;

    /**
     * Creates a new main controller.
     */
    public MainController() {
        // nothing to set up
    }

    /**
     * Called by JavaFX after the FXML file is loaded.
     */
    @FXML
    public void initialize() {
        ToggleGroup themeGroup = new ToggleGroup();
        lightButton.setToggleGroup(themeGroup);
        darkButton.setToggleGroup(themeGroup);
        lightButton.setGraphic(Icon.SUN.create());
        darkButton.setGraphic(Icon.MOON.create());
        navMenu.setGraphic(Icon.MENU.create());
        navMenu.setText("");
        navMenu.setAccessibleText("Open the navigation menu");
        lightButton.setAccessibleText("Use the light theme");
        darkButton.setAccessibleText("Use the dark theme");
        moduleLabel.setLabelFor(moduleBox);
        sectionLabel.setLabelFor(sectionBox);
        moduleBox.setMinWidth(Region.USE_PREF_SIZE);
        sectionBox.setMinWidth(Region.USE_PREF_SIZE);
        demoBadge.setMinWidth(Region.USE_PREF_SIZE);
        assessmentLabel.setMinWidth(Region.USE_PREF_SIZE);
        runLabel.setMinWidth(Region.USE_PREF_SIZE);
        Responsive.keepButtonsWhole(contextBar);
    }

    /**
     * Fills in the window and shows the first screen. Called by MainApp
     * after the scene exists.
     *
     * @param screenNavigator loads and keeps the screens
     * @param manager switches the theme
     */
    public void start(ScreenNavigator screenNavigator,
            ThemeManager manager) {
        this.navigator = screenNavigator;
        this.themeManager = manager;
        navigator.setShell(this);

        sheets = new SheetLayer(sheetLayer, sheetHolder, shellRoot,
                getState().animationsEnabledProperty());
        toasts = new ToastLayer(toastLayer,
                getState().animationsEnabledProperty());
        sidebarView = new Sidebar(navList, footerNav, navMenu, this);
        sidebarView.build();

        List<Node> captions = new ArrayList<>();
        captions.add(moduleLabel);
        captions.add(sectionLabel);
        captions.add(assessmentCaption);
        captions.add(runCaption);
        context = new ContextBar(moduleBox, sectionBox, runLabel,
                assessmentLabel, demoBadge, captions, getBackend(),
                getState(), () -> contextChanged());
        context.build();
        buildThemeButtons();
        bindLayout();
        installShortcuts();
        getState().animationsEnabledProperty().addListener(
                (observable, oldValue, newValue) -> applyMotion(newValue));
        applyMotion(getState().animationsEnabledProperty().get());

        GradingMonitor monitor = getState().getMonitor();
        monitor.statusProperty().addListener(
                (observable, oldValue, newValue) -> refreshContext());
        monitor.doneProperty().addListener(
                (observable, oldValue, newValue) -> refreshContext());

        navigate(Screen.OVERVIEW);
    }

    /**
     * Makes confirmations run without asking. Used by the self-test,
     * which cannot press buttons.
     *
     * @param autoConfirm true to skip confirmation sheets
     */
    public void setAutoConfirm(boolean autoConfirm) {
        this.autoConfirm = autoConfirm;
    }

    /**
     * Moves to the next step of the main grading journey.
     *
     * <p>Tier: SHOULD.
     */
    @FXML
    public void handleNext() {
        int index = journeyIndex(current);
        if (index >= 0 && index < JOURNEY.length - 1) {
            navigate(JOURNEY[index + 1]);
        }
    }

    /**
     * Returns to the screen shown before this one.
     *
     * <p>Tier: SHOULD.
     */
    @FXML
    public void handleBack() {
        if (history.isEmpty()) {
            return;
        }
        Screen previous = history.pop();
        showScreen(previous, false);
    }

    /**
     * Leaves the current step after a confirmation, and returns to the
     * Overview screen. A running batch is cancelled first.
     *
     * <p>Tier: SHOULD.
     */
    @FXML
    public void handleCancel() {
        GradingMonitor monitor = getState().getMonitor();
        if (monitor.isActive()) {
            confirm("Cancel grading?", "Grading stops after the current "
                    + "submission. Completed results are kept and you can "
                    + "resume later.", "Cancel Grading", "Keep Grading",
                    true, () -> {
                        monitor.markCancelling();
                        getBackend().cancelGrading();
                        navigate(Screen.OVERVIEW);
                    });
            return;
        }
        navigate(Screen.OVERVIEW);
    }

    /**
     * Switches light and dark mode. The choice lasts for this session;
     * Save on the Settings screen keeps it in config.properties.
     *
     * <p>Tier: SHOULD.
     */
    @FXML
    public void handleToggleTheme() {
        setDark(!getState().darkProperty().get());
    }

    /**
     * Switches to a theme without reloading any screen.
     *
     * @param dark true for the dark theme
     */
    @Override
    public void setDark(boolean dark) {
        getState().darkProperty().set(dark);
        Scene scene = shellRoot.getScene();
        if (scene != null && themeManager != null) {
            try {
                themeManager.applyTo(scene, dark);
            } catch (IOException exception) {
                showError(exception.getMessage());
            }
        }
        if (dark) {
            darkButton.setSelected(true);
        } else {
            lightButton.setSelected(true);
        }
    }

    // ------------------------------------------------------- AppShell

    /**
     * Shows a screen and keeps the state of the one left.
     *
     * @param screen the screen to show
     */
    @Override
    public void navigate(Screen screen) {
        showScreen(screen, true);
    }

    /**
     * Shows a short message that disappears by itself.
     *
     * @param message a plain sentence in the past tense
     */
    @Override
    public void showToast(String message) {
        toasts.showMessage(message);
    }

    /**
     * Shows a problem that stays until the instructor closes it.
     *
     * @param message what went wrong and what to do next
     */
    @Override
    public void showError(String message) {
        toasts.showProblem(message);
    }

    /**
     * Shows content in a sheet over the window.
     *
     * @param content the content
     */
    @Override
    public void showSheet(Node content) {
        sheets.show(content);
    }

    /**
     * Closes the sheet on show.
     */
    @Override
    public void closeSheet() {
        sheets.close();
    }

    /**
     * Says whether a sheet is on show.
     *
     * @return true while a sheet covers the window
     */
    public boolean isSheetShown() {
        return sheets.isShown();
    }

    /**
     * Asks the instructor to confirm an action in a sheet.
     *
     * @param title the question
     * @param message what will happen and what is kept
     * @param actionLabel the verb on the confirm button
     * @param destructive true to colour the confirm button as a warning
     * @param onConfirm what to do when the instructor confirms
     */
    @Override
    public void confirm(String title, String message, String actionLabel,
            boolean destructive, Runnable onConfirm) {
        confirm(title, message, actionLabel, "Cancel", destructive,
                onConfirm);
    }

    /**
     * Asks the instructor to confirm an action, with chosen words on the
     * dismiss button.
     *
     * @param title the question
     * @param message what will happen and what is kept
     * @param actionLabel the verb on the confirm button
     * @param dismissLabel the words on the button that changes nothing
     * @param destructive true to colour the confirm button as a warning
     * @param onConfirm what to do when the instructor confirms
     */
    @Override
    public void confirm(String title, String message, String actionLabel,
            String dismissLabel, boolean destructive, Runnable onConfirm) {
        if (autoConfirm) {
            onConfirm.run();
            return;
        }

        Sheet sheet = new Sheet(title).setMessage(message);
        sheet.addDismissButton(dismissLabel, () -> closeSheet());
        sheet.addPrimaryButton(actionLabel, destructive, () -> {
            closeSheet();
            onConfirm.run();
        });
        showSheet(sheet);
    }

    /**
     * Explains that a feature has no backend yet.
     *
     * @param capability the missing feature
     */
    @Override
    public void showNotConnected(Capability capability) {
        Sheet sheet = new Sheet(capability.getTitle());
        sheet.addContent(new NotConnectedPanel(capability));
        Sheet.markEscape(sheet.addPrimaryButton("OK", false,
                () -> closeSheet()));
        showSheet(sheet);
    }

    /**
     * Shows read-only text in a sheet, with a Copy button.
     *
     * @param title the sheet title
     * @param note one sentence about the text
     * @param text the text
     */
    @Override
    public void showPreview(String title, String note, String text) {
        TextArea area = new TextArea(text);
        area.setEditable(false);
        area.setWrapText(false);
        area.setPrefRowCount(PREVIEW_ROWS);
        area.getStyleClass().add("code-view");
        area.setAccessibleText(title);

        Sheet sheet = new Sheet(title).setMessage(note);
        sheet.addContent(area);
        sheet.addSecondaryButton("Copy", () -> copyText(text));
        Sheet.markEscape(sheet.addPrimaryButton("Done", false,
                () -> closeSheet()));
        showSheet(sheet);
    }

    /**
     * Refreshes the context bar and the sidebar.
     */
    @Override
    public void refreshContext() {
        if (navigator == null) {
            return;
        }
        context.refreshRun();
        GradingMonitor monitor = getState().getMonitor();
        // The Grading row stays while a run goes and while a cancelled
        // run can be resumed; it goes once the run has finished.
        boolean resumable = monitor.statusProperty().get()
                == GradingMonitor.RunStatus.CANCELLED;
        sidebarView.setShown(Screen.PROGRESS, monitor.isActive()
                || resumable);
        GradingRun run = getBackend().getRun(getState().getSection());
        sidebarView.setBadge(Screen.ANOMALY_LOG, countNeedingReview(run));
        sidebarView.refreshMenu();
    }

    /**
     * Returns the window, as the owner of file pickers.
     *
     * @return the window, or null when the shell is not on screen
     */
    @Override
    public Window getWindow() {
        Scene scene = shellRoot.getScene();
        if (scene == null) {
            return null;
        }
        return scene.getWindow();
    }

    /**
     * Returns the part of the window under the sheet layer.
     *
     * @return the sidebar and content
     */
    public BorderPane getRoot() {
        return shellRoot;
    }

    /**
     * Returns the screen on show.
     *
     * @return the screen
     */
    public Screen getCurrentScreen() {
        return current;
    }

    // ------------------------------------------------------ navigation

    // Shows a screen, optionally remembering the one left for Back.
    private void showScreen(Screen screen, boolean remember) {
        Parent view;
        BaseController controller;
        try {
            view = navigator.getView(screen);
            controller = navigator.getController(screen);
        } catch (IOException exception) {
            showError("The " + screen.getTitle() + " screen could not be "
                    + "opened: " + exception.getMessage());
            return;
        }

        if (screen != current) {
            if (remember && current != null) {
                history.push(current);
            }
            current = screen;
            getState().currentScreenProperty().set(screen);
            Responsive.keepButtonsWhole(view);
            ScreenShortcuts.addTooltips(view);
            contentHost.getChildren().setAll(view);
            fadeIn(view);
        }

        sidebarView.select(current);
        refreshContext();
        if (controller != null) {
            controller.onShow();
        }
    }

    // Returns the position of a screen in the main journey.
    private int journeyIndex(Screen screen) {
        for (int index = 0; index < JOURNEY.length; index++) {
            if (JOURNEY[index] == screen) {
                return index;
            }
        }
        return -1;
    }

    // Fades a screen in, unless animations are switched off.
    private void fadeIn(Node view) {
        view.setOpacity(1.0);
        if (!getState().animationsEnabledProperty().get()) {
            return;
        }
        FadeTransition fade = new FadeTransition(FADE_TIME, view);
        fade.setFromValue(0.0);
        fade.setToValue(1.0);
        fade.setInterpolator(Interpolator.EASE_OUT);
        fade.play();
    }

    // Marks the window while animations are off, so styled motion such
    // as a sliding switch stops too.
    private void applyMotion(boolean enabled) {
        if (enabled) {
            windowRoot.getStyleClass().remove(NO_MOTION);
        } else if (!windowRoot.getStyleClass().contains(NO_MOTION)) {
            windowRoot.getStyleClass().add(NO_MOTION);
        }
    }

    // Reloads the screen on show after the module or section changed.
    private void contextChanged() {
        getState().getSelectedResults().clear();
        getState().markDataChanged();
        refreshContext();
        if (current == null) {
            return;
        }
        try {
            BaseController controller = navigator.getController(current);
            if (controller != null) {
                controller.onShow();
            }
        } catch (IOException exception) {
            showError(exception.getMessage());
        }
    }

    // Selects the theme button that matches the state and wires both.
    private void buildThemeButtons() {
        if (getState().darkProperty().get()) {
            darkButton.setSelected(true);
        } else {
            lightButton.setSelected(true);
        }
        lightButton.setOnAction(event -> setDark(false));
        darkButton.setOnAction(event -> setDark(true));
    }

    // ----------------------------------------------------------- layout

    // Narrows the sidebar, hides it, and hides context captions as the
    // window narrows.
    private void bindLayout() {
        shellRoot.widthProperty().addListener(
                (observable, oldValue, newValue) -> applyWidth(
                newValue.doubleValue()));
        applyWidth(shellRoot.getWidth());

        getState().narrowProperty().addListener(
                (observable, oldValue, newValue) -> applyNarrow(newValue));
        contextBar.widthProperty().addListener(
                (observable, oldValue, newValue) -> fitContextBar());
        applyNarrow(getState().narrowProperty().get());
    }

    // Updates the compact and narrow properties from the window width.
    private void applyWidth(double width) {
        if (width <= 0) {
            return;
        }
        boolean compact = width < UiState.COMPACT_WIDTH;
        getState().compactProperty().set(compact);
        getState().narrowProperty().set(width < UiState.NARROW_WIDTH);
        boolean marked = sidebar.getStyleClass().contains(COMPACT);
        if (compact && !marked) {
            sidebar.getStyleClass().add(COMPACT);
        } else if (!compact && marked) {
            sidebar.getStyleClass().remove(COMPACT);
        }

        // A compact window keeps every value and drops the captions.
        context.applyCompact(compact);
        String lightText = "Light";
        String darkText = "Dark";
        if (compact) {
            lightText = "";
            darkText = "";
        }
        lightButton.setText(lightText);
        darkButton.setText(darkText);
        fitContextBar();
    }

    // Swaps the sidebar for a menu button in a narrow window.
    private void applyNarrow(boolean narrow) {
        sidebar.setVisible(!narrow);
        sidebar.setManaged(!narrow);
        navMenu.setVisible(narrow);
        navMenu.setManaged(narrow);
        fitContextBar();
    }

    // Moves the assessment and run to a second row of the context bar
    // when one row would not fit, so neither is ever cut short.
    private void fitContextBar() {
        double available = contextBar.getWidth();
        if (available <= 0) {
            return;
        }
        Insets insets = contextBar.getInsets();
        double needed = insets.getLeft() + insets.getRight()
                + contextRow.prefWidth(-1);
        boolean onFirstRow = contextRow.getChildren().contains(
                assessmentBox);
        if (!onFirstRow) {
            needed += assessmentBox.prefWidth(-1) + runBox.prefWidth(-1)
                    + contextSeparator.prefWidth(-1)
                    + contextRow.getSpacing() * MOVED_GAPS;
        }
        placeContextDetails(needed > available);
    }

    // Puts the assessment and run on the first or the second row.
    private void placeContextDetails(boolean twoRows) {
        boolean onFirstRow = contextRow.getChildren().contains(
                assessmentBox);
        if (twoRows && onFirstRow) {
            contextRow.getChildren().removeAll(assessmentBox, runBox);
            contextDetails.getChildren().setAll(assessmentBox, runBox);
        } else if (!twoRows && !onFirstRow) {
            contextDetails.getChildren().clear();
            int after = contextRow.getChildren().indexOf(contextSeparator)
                    + 1;
            contextRow.getChildren().add(after, runBox);
            contextRow.getChildren().add(after, assessmentBox);
        }
        contextDetails.setVisible(twoRows);
        contextDetails.setManaged(twoRows);
        contextSeparator.setVisible(!twoRows);
        contextSeparator.setManaged(!twoRows);
    }

    // ------------------------------------------------------- shortcuts

    // Adds the keyboard shortcuts. SHORTCUT is Cmd on macOS and Ctrl on
    // Windows. None works while a sheet is on show.
    private void installShortcuts() {
        Scene scene = shellRoot.getScene();
        if (scene == null) {
            return;
        }

        scene.getAccelerators().put(new KeyCodeCombination(KeyCode.F,
                KeyCombination.SHORTCUT_DOWN),
                unlessSheet(() -> focusCurrentSearch()));
        scene.getAccelerators().put(new KeyCodeCombination(
                KeyCode.OPEN_BRACKET, KeyCombination.SHORTCUT_DOWN),
                unlessSheet(() -> handleBack()));
        scene.getAccelerators().put(new KeyCodeCombination(
                KeyCode.CLOSE_BRACKET, KeyCombination.SHORTCUT_DOWN),
                unlessSheet(() -> handleNext()));

        for (Screen screen : Screen.values()) {
            KeyCombination keys = ScreenShortcuts.combinationFor(screen);
            if (keys != null) {
                scene.getAccelerators().put(keys,
                        unlessSheet(() -> navigate(screen)));
            }
        }

        scene.addEventFilter(KeyEvent.KEY_PRESSED, event -> {
            if (event.getCode() == KeyCode.ESCAPE && isSheetShown()) {
                closeSheet();
                event.consume();
            }
        });
    }

    // Wraps a shortcut so it does nothing while a sheet is on show.
    private Runnable unlessSheet(Runnable action) {
        return () -> {
            if (!isSheetShown()) {
                action.run();
            }
        };
    }

    // Moves focus to the search field of the screen on show.
    private void focusCurrentSearch() {
        try {
            BaseController controller = navigator.getController(current);
            if (controller == null || !controller.focusSearch()) {
                showToast("This screen has no search field.");
            }
        } catch (IOException exception) {
            showError(exception.getMessage());
        }
    }

    // ---------------------------------------------------------- helpers

    // Puts text on the clipboard.
    private void copyText(String text) {
        ClipboardContent content = new ClipboardContent();
        content.putString(text);
        Clipboard.getSystemClipboard().setContent(content);
        showToast("Copied the preview to the clipboard.");
    }

    // Counts the anomalies of a run that wait for the instructor.
    private int countNeedingReview(GradingRun run) {
        int count = 0;
        for (Anomaly anomaly : getBackend().listAnomalies(run)) {
            if (getBackend().needsReview(anomaly)) {
                count++;
            }
        }
        return count;
    }
}
