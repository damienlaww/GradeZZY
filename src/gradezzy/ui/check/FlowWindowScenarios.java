/*
 * File: FlowWindowScenarios.java
 *
 * Purpose: The scenarios of the window test: each tab, each step, Set
 * up, Review, Export, Statistics, Runs, Settings, Help and the keyboard
 * shortcuts, all driven with real mouse and keyboard input.
 *
 * Where it fits: FlowWindowTest runs them in order on the demo data and
 * ends each one with a picture and the sheet and shadow checks.
 */
package gradezzy.ui.check;

import java.util.List;

import javafx.scene.Node;
import javafx.scene.Parent;
import javafx.scene.control.ComboBox;
import javafx.scene.control.Labeled;
import javafx.scene.control.TableRow;
import javafx.scene.control.TableView;
import javafx.scene.control.TextField;
import javafx.scene.control.Toggle;
import javafx.scene.input.KeyCode;

import atlantafx.base.controls.ToggleSwitch;

import gradezzy.ui.backend.InputKind;
import gradezzy.ui.demo.DemoData;
import gradezzy.ui.flow.FlowWindow;
import gradezzy.ui.flow.Page;
import gradezzy.ui.flow.Step;

/**
 * The scenarios of the window test.
 */
public class FlowWindowScenarios {

    // The text typed in the Review search field
    private static final String SEARCH_TEXT = "alpha";

    // The keys that open the four steps, in order
    private static final KeyCode[] STEP_KEYS = {KeyCode.DIGIT1,
        KeyCode.DIGIT2, KeyCode.DIGIT3, KeyCode.DIGIT4};

    // The test that records checks and takes pictures
    private final FlowWindowTest test;

    // Sends real input
    private final RobotDriver driver;

    // The window being tested
    private final FlowWindow window;

    /**
     * Creates the scenarios for a test.
     *
     * @param test the running test
     */
    public FlowWindowScenarios(FlowWindowTest test) {
        this.test = test;
        this.driver = test.getDriver();
        this.window = test.getWindow();
    }

    /**
     * Runs every scenario in order.
     */
    public void runAll() {
        tabs();
        setUp();
        openResults();
        steps();
        review();
        export();
        statistics();
        runs();
        settings();
        help();
        shortcuts();
    }

    // ------------------------------------------------------- scenarios

    // Clicks every tab; the step row shows only on Marking.
    private void tabs() {
        test.section("Tabs");
        for (Page page : Page.values()) {
            test.clickIn(window.getTabBar(), page.getLabel());
            boolean shown = test.waitFor(()
                    -> window.getCurrentPage() == page);
            test.check(shown, "clicking the " + page.getLabel() + " tab "
                    + "shows it");
            boolean stepRow = test.firstStyled("step-row") != null;
            test.check(stepRow == (page == Page.MARKING), "the step row "
                    + "is shown only on Marking (" + page.getLabel()
                    + ")");
        }
        test.endScenario("01-tabs");
    }

    // Adds the sample zip files with "choose files", then removes one.
    private void setUp() {
        test.section("Set up");
        showMarking();
        driver.shortcut(KeyCode.DIGIT1);
        int before = zipCount();
        test.clickText("choose files");
        boolean added = test.waitFor(() -> zipCount() > before);
        test.check(added, "\"choose files\" adds the sample zip files");
        int withFiles = zipCount();
        test.clickText("Remove");
        boolean removed = test.waitFor(() -> zipCount() == withFiles - 1);
        test.check(removed, "Remove takes one zip file off the list");
        test.endScenario("02-set-up");
    }

    // Opens G1's results from Set up.
    private void openResults() {
        test.section("Open G1's results");
        String section = driver.onFx(() -> window.getState()
                .getSectionCode());
        test.note("Set up shows section " + section);
        test.clickText("Open its results");
        boolean opened = test.waitFor(() -> window.getCurrentStep()
                == Step.REVIEW && window.getState().getRun() != null);
        test.check(opened, "\"Open its results\" opens Review with a run");
        String shown = driver.onFx(() -> window.getState().getRun()
                .getSection().getSectionCode());
        test.check(DemoData.SAMPLE_SECTION.equals(shown), "the run opened "
                + "is " + DemoData.SAMPLE_SECTION + "'s");
        test.endScenario("03-open-results");
    }

    // Clicks each reachable step in the step row.
    private void steps() {
        test.section("Steps");
        Step[] reachable = {Step.SET_UP, Step.GRADE, Step.REVIEW};
        for (Step step : reachable) {
            clickStep(step);
        }
        test.endScenario("04-steps");
    }

    // Filters, search, the student panel and the adjust-mark sheet.
    private void review() {
        test.section("Review");
        int total = rowCount();
        test.check(total > 0, "Review lists the students (" + total + ")");

        clickToggle("Needs review");
        test.check(test.waitFor(() -> isSelected("Needs review")),
                "the Needs review filter is selected");
        test.note("Needs review shows " + rowCount() + " of " + total);
        clickToggle("Failed");
        test.check(test.waitFor(() -> isSelected("Failed")),
                "the Failed filter is selected");
        test.note("Failed shows " + rowCount() + " of " + total);
        clickToggle("All");
        test.check(test.waitFor(() -> rowCount() == total),
                "the All filter shows every student again");

        searchFor(SEARCH_TEXT, total);

        Node row = firstRow();
        test.check(row != null, "a row of the table is on show");
        if (row == null) {
            test.endScenario("05-review");
            return;
        }
        driver.click(row);
        test.check(test.waitFor(() -> panelShown()), "clicking a row "
                + "opens the student panel");
        test.endScenario("05-review-panel");

        Node close = panelClose();
        test.check(close != null, "the panel has a close button");
        if (close != null) {
            driver.click(close);
            test.check(test.waitFor(() -> !panelShown()), "the close "
                    + "button closes the panel");
        }

        driver.click(firstRow());
        test.waitFor(() -> panelShown());
        driver.press(KeyCode.ESCAPE);
        test.check(test.waitFor(() -> !panelShown()), "Esc closes the "
                + "student panel");

        adjustAndCancel();
        test.endScenario("05-review");
    }

    // Continues to Export, then opens all statistics from its link.
    private void export() {
        test.section("Export");
        showMarking();
        driver.shortcut(KeyCode.DIGIT3);
        test.clickText("Continue to export");
        if (test.waitFor(() -> window.isSheetOpen())) {
            test.clickText("Continue anyway");
        }
        test.check(test.waitFor(() -> window.getCurrentStep()
                == Step.EXPORT), "Continue to export opens Export");
        clickStep(Step.REVIEW);
        clickStep(Step.EXPORT);
        test.endScenario("06-export");

        test.clickText("See all statistics");
        test.check(test.waitFor(() -> window.getCurrentPage()
                == Page.STATISTICS), "\"See all statistics\" opens "
                + "Statistics");
        test.check(test.waitFor(() -> showsChoice(
                DemoData.SAMPLE_SECTION)), "Statistics shows section "
                + DemoData.SAMPLE_SECTION);
        test.endScenario("07-export-to-statistics");
    }

    // Switches Statistics to Compare sections and back.
    private void statistics() {
        test.section("Statistics");
        openTab(Page.STATISTICS);
        clickToggle("Compare sections");
        test.check(test.waitFor(() -> isSelected("Compare sections")),
                "the Compare sections switch is selected");
        test.endScenario("08-statistics-compare");
        clickToggle("One section");
        test.check(test.waitFor(() -> isSelected("One section")),
                "the One section switch is selected again");
        test.endScenario("08-statistics-one");
    }

    // Shows the three views of Runs.
    private void runs() {
        test.section("Runs");
        openTab(Page.RUNS);
        clickToggle("Backups");
        test.check(test.waitFor(() -> shows("Back up now")),
                "Backups shows Back up now");
        test.endScenario("09-runs-backups");
        clickToggle("Activity");
        test.check(test.waitFor(() -> shows("When")),
                "Activity shows the activity table");
        test.endScenario("09-runs-activity");
        clickToggle("Runs");
        test.check(test.waitFor(() -> shows("Students")),
                "Runs shows the table of runs");
        test.endScenario("09-runs");
    }

    // Flips a switch so Discard can be pressed, then discards.
    private void settings() {
        test.section("Settings");
        openTab(Page.SETTINGS);
        test.check(test.waitFor(() -> discardDisabled()),
                "Discard is off before anything changes");
        Node toggle = test.firstStyled("toggle-switch");
        test.check(toggle != null, "Settings shows an on and off switch");
        if (toggle == null) {
            test.endScenario("10-settings");
            return;
        }
        boolean before = isOn(toggle);
        driver.click(toggle);
        test.check(test.waitFor(() -> isOn(toggle) != before
                && !discardDisabled()), "flipping a switch turns on "
                + "Discard");
        test.endScenario("10-settings-changed");
        test.clickText("Discard");
        test.check(test.waitFor(() -> isOn(toggle) == before
                && discardDisabled()), "Discard puts the switch back");
        test.endScenario("10-settings");
    }

    // Opens Help and closes it with Esc.
    private void help() {
        test.section("Help");
        test.clickIn(window.getRoot(), "Help");
        test.check(test.waitFor(() -> window.isSheetOpen()),
                "Help opens a sheet");
        test.picture("11-help-open");
        driver.press(KeyCode.ESCAPE);
        test.check(test.waitFor(() -> !window.isSheetOpen()),
                "Esc closes Help");
        test.endScenario("11-help");
    }

    // Cmd or Ctrl with 1 to 4, Cmd or Ctrl with Enter, and Esc.
    private void shortcuts() {
        test.section("Keyboard shortcuts");
        Step[] steps = Step.values();
        for (int i = 0; i < steps.length; i++) {
            Step step = steps[i];
            driver.shortcut(STEP_KEYS[i]);
            test.check(test.waitFor(() -> window.getCurrentPage()
                    == Page.MARKING && window.getCurrentStep() == step),
                    "Cmd or Ctrl + " + (i + 1) + " opens "
                    + step.getLabel());
        }

        driver.shortcut(KeyCode.DIGIT3);
        driver.shortcut(KeyCode.ENTER);
        boolean pressed = test.waitFor(() -> window.isSheetOpen()
                || window.getCurrentStep() == Step.EXPORT);
        test.check(pressed, "Cmd or Ctrl + Enter presses Continue to "
                + "export on Review");
        if (driver.onFx(() -> window.isSheetOpen())) {
            driver.press(KeyCode.ESCAPE);
            test.check(test.waitFor(() -> !window.isSheetOpen()),
                    "Esc closes the question sheet");
        }
        test.endScenario("12-shortcuts");
    }

    // --------------------------------------------------------- helpers

    // Opens a tab by clicking it.
    private void openTab(Page page) {
        test.clickIn(window.getTabBar(), page.getLabel());
        test.waitFor(() -> window.getCurrentPage() == page);
    }

    // Shows the Marking tab.
    private void showMarking() {
        openTab(Page.MARKING);
    }

    // Clicks a step in the step row and checks it is shown.
    private void clickStep(Step step) {
        Labeled name = null;
        for (Node node : driver.findAll(window.getRoot(), "step-name")) {
            if (node instanceof Labeled && step.getLabel().equals(
                    ((Labeled) node).getText())) {
                name = (Labeled) node;
            }
        }
        test.check(name != null, "the step row shows " + step.getLabel());
        if (name == null) {
            return;
        }
        if (driver.onFx(() -> window.getCurrentStep() == step)) {
            test.note(step.getLabel() + " is already shown");
            return;
        }
        driver.click(name);
        test.check(test.waitFor(() -> window.getCurrentStep() == step),
                "clicking " + step.getLabel() + " in the step row shows "
                + "it");
    }

    // Types in the search field and clears it again.
    private void searchFor(String text, int total) {
        TextField field = searchField();
        test.check(field != null, "Review has a search field");
        if (field == null) {
            return;
        }
        driver.click(field);
        driver.type(text);
        test.check(test.waitFor(() -> rowCount() > 0
                && rowCount() < total), "typing \"" + text + "\" narrows "
                + "the list");
        driver.shortcut(KeyCode.A);
        driver.press(KeyCode.BACK_SPACE);
        test.check(test.waitFor(() -> rowCount() == total),
                "clearing the search shows every student again");
    }

    // Opens the adjust-mark sheet from the panel and cancels it.
    private void adjustAndCancel() {
        driver.click(firstRow());
        test.waitFor(() -> panelShown());
        Parent panel = (Parent) test.firstStyled("student-panel");
        if (panel == null || driver.find(panel, "Adjust") == null) {
            test.check(false, "the panel offers Adjust");
            return;
        }
        test.clickIn(panel, "Adjust");
        test.check(test.waitFor(() -> window.isSheetOpen()),
                "Adjust opens the adjust-mark sheet");
        test.picture("05-review-adjust");
        test.clickIn((Parent) test.firstStyled("sheet-dim"), "Cancel");
        test.check(test.waitFor(() -> !window.isSheetOpen()),
                "Cancel closes the adjust-mark sheet");
        Node close = panelClose();
        if (close != null) {
            driver.click(close);
        }
    }

    // Returns the number of zip files on Set up.
    private int zipCount() {
        return driver.onFx(() -> window.getState().getBatch()
                .getItems(InputKind.SUBMISSION).size());
    }

    // Returns the number of students the Review table lists.
    private int rowCount() {
        return driver.onFx(() -> {
            Node table = window.getRoot().lookup(".results-table");
            if (!(table instanceof TableView)) {
                return 0;
            }
            return ((TableView<?>) table).getItems().size();
        });
    }

    // Returns the first table row that shows a student, or null.
    private Node firstRow() {
        List<Node> rows = driver.findAll(window.getRoot(),
                "table-row-cell");
        for (Node row : rows) {
            boolean filled = driver.onFx(() -> row instanceof TableRow
                    && ((TableRow<?>) row).getItem() != null);
            if (filled) {
                return row;
            }
        }
        return null;
    }

    // Returns the search field of Review, or null.
    private TextField searchField() {
        for (Node node : driver.findAll(window.getRoot(), "text-field")) {
            if (node instanceof TextField) {
                return (TextField) node;
            }
        }
        return null;
    }

    // Says whether the student panel is on show.
    private boolean panelShown() {
        Node panel = window.getRoot().lookup(".student-panel");
        return panel != null && panel.isVisible();
    }

    // Returns the close button of the student panel, or null.
    private Node panelClose() {
        Parent panel = (Parent) test.firstStyled("student-panel");
        if (panel == null) {
            return null;
        }
        List<Node> buttons = driver.findAll(panel, "icon-button");
        if (buttons.isEmpty()) {
            return null;
        }
        return buttons.get(0);
    }

    // Says whether a toggle button with some words is selected. Runs on
    // the JavaFX thread.
    private boolean isSelected(String text) {
        Labeled control = driver.find(window.getRoot(), text);
        return control instanceof Toggle && ((Toggle) control).isSelected();
    }

    // Says whether a visible control shows some words.
    private boolean shows(String text) {
        return driver.find(window.getRoot(), text) != null;
    }

    // Says whether a visible picker shows a value.
    private boolean showsChoice(String value) {
        for (Node node : driver.findAll(window.getRoot(), "combo-box")) {
            if (node instanceof ComboBox
                    && value.equals(((ComboBox<?>) node).getValue())) {
                return true;
            }
        }
        return false;
    }

    // Says whether the Discard button of Settings is off.
    private boolean discardDisabled() {
        Labeled discard = driver.find(window.getRoot(), "Discard");
        return discard != null && discard.isDisabled();
    }

    // Says whether an on and off switch is on.
    private boolean isOn(Node toggle) {
        return driver.onFx(() -> toggle instanceof ToggleSwitch
                && ((ToggleSwitch) toggle).isSelected());
    }

    // Clicks the switch button with some words, such as Backups.
    private void clickToggle(String text) {
        for (Node node : driver.findAll(window.getRoot(),
                "toggle-button")) {
            if (node instanceof Labeled
                    && text.equals(((Labeled) node).getText())) {
                driver.click(node);
                return;
            }
        }
        test.note("no switch button with the words: " + text);
    }
}
