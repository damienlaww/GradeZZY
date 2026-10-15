/*
 * File: HelpController.java
 *
 * Purpose: Controller of the Help screen.
 *
 * Where it fits: Explains how to use GradeZZY: the seven demo flows as
 * numbered steps, the keyboard shortcuts as this computer writes them,
 * what each result state means and how to recover from common problems.
 * Its Backend connections table lists every Capability with what it
 * does for the instructor, its owner, service method and status, so each
 * teammate can see which parts of the interface wait for their code. In
 * demo mode it also offers the large sample cohort.
 *
 * AI use: drafted with Claude Code, reviewed by Jun Sheng.
 */
package gradezzy.ui;

import java.util.function.Function;

import javafx.beans.property.ReadOnlyObjectWrapper;
import javafx.beans.property.ReadOnlyStringWrapper;
import javafx.collections.FXCollections;
import javafx.fxml.FXML;
import javafx.geometry.Pos;
import javafx.scene.control.Button;
import javafx.scene.control.Label;
import javafx.scene.control.TableCell;
import javafx.scene.control.TableColumn;
import javafx.scene.control.TableView;
import javafx.scene.input.KeyCode;
import javafx.scene.input.KeyCodeCombination;
import javafx.scene.input.KeyCombination;
import javafx.scene.layout.FlowPane;
import javafx.scene.layout.GridPane;
import javafx.scene.layout.HBox;
import javafx.scene.layout.Priority;
import javafx.scene.layout.Region;
import javafx.scene.layout.VBox;

import atlantafx.base.controls.ToggleSwitch;

import gradezzy.model.AnomalyType;
import gradezzy.ui.backend.Capability;
import gradezzy.ui.backend.CapabilityStatus;
import gradezzy.ui.backend.ResultState;
import gradezzy.ui.component.Icon;
import gradezzy.ui.component.Labels;
import gradezzy.ui.component.Responsive;
import gradezzy.ui.component.StatusBadge;
import gradezzy.ui.component.Tone;

/**
 * Controller of the Help screen.
 *
 * <p>Tier: SHOULD. Owner: Damien.
 */
public class HelpController extends BaseController {

    // The share of the width given to the screen shortcuts
    private static final double KEYS_PERCENT = 50.0;

    // The width below which the two shortcut lists stack
    private static final double KEYS_STACK_WIDTH = 620.0;

    // The share of the width given to the result states panel
    private static final double STATES_PERCENT = 55.0;

    // The width below which the states and advice panels stack
    private static final double STATES_STACK_WIDTH = 760.0;

    // The narrowest a step number may get
    private static final double NUMBER_WIDTH = 22.0;

    // The gap between a step number and its text
    private static final double STEP_GAP = 8.0;

    // The gap between the text of a step and its links
    private static final double LINK_GAP = 4.0;

    // The gap between a recovery title and its advice
    private static final double ADVICE_GAP = 4.0;

    // The narrowest the feature column may get
    private static final double FEATURE_WIDTH = 120.0;

    // The narrowest the purpose column may get
    private static final double PURPOSE_WIDTH = 160.0;

    // The narrowest the status column may get
    private static final double STATUS_WIDTH = 190.0;

    // The narrowest the owner column may get
    private static final double OWNER_WIDTH = 90.0;

    // The narrowest the service method column may get
    private static final double SERVICE_WIDTH = 220.0;

    // The narrowest the needs column may get
    private static final double NEED_WIDTH = 140.0;

    // The width of a cell taken by its left and right padding
    private static final double CELL_PADDING = 24.0;

    // The space above and below the text of a row
    private static final double ROW_PADDING = 16.0;

    // The lowest a row may be
    private static final double MIN_ROW_HEIGHT = 44.0;

    // The number keys of the screens reached with Shortcut and a digit
    private static final KeyCode[] DIGITS = {KeyCode.DIGIT1, KeyCode.DIGIT2,
        KeyCode.DIGIT3, KeyCode.DIGIT4, KeyCode.DIGIT5, KeyCode.DIGIT6,
        KeyCode.DIGIT7, KeyCode.DIGIT8, KeyCode.DIGIT9};

    // The screens reached with Shortcut+1 to Shortcut+9, in order
    private static final Screen[] NUMBERED = {Screen.OVERVIEW,
        Screen.MODULES, Screen.TEST_CASES, Screen.UPLOAD,
        Screen.ANOMALY_LOG, Screen.RESULTS, Screen.STATISTICS,
        Screen.PLAGIARISM, Screen.HISTORY};

    @FXML
    private Label statusLabel;

    @FXML
    private VBox stepRows;

    @FXML
    private Label keysCaption;

    @FXML
    private GridPane keyColumns;

    @FXML
    private VBox screenKeysBox;

    @FXML
    private VBox workKeysBox;

    @FXML
    private GridPane screenKeys;

    @FXML
    private GridPane workKeys;

    @FXML
    private GridPane stateColumns;

    @FXML
    private VBox statesPanel;

    @FXML
    private VBox recoveryPanel;

    @FXML
    private GridPane stateRows;

    @FXML
    private VBox adviceRows;

    @FXML
    private VBox demoPanel;

    @FXML
    private Label cohortLabel;

    @FXML
    private ToggleSwitch cohortSwitch;

    @FXML
    private Label connectionsCaption;

    @FXML
    private TableView<Capability> capabilityTable;

    /**
     * Creates a new help controller.
     */
    public HelpController() {
        // nothing to set up
    }

    /**
     * Called by JavaFX after the FXML file is loaded.
     */
    @FXML
    public void initialize() {
        Responsive.twoColumns(keyColumns, screenKeysBox, workKeysBox,
                KEYS_PERCENT, KEYS_STACK_WIDTH);
        Responsive.twoColumns(stateColumns, statesPanel, recoveryPanel,
                STATES_PERCENT, STATES_STACK_WIDTH);
        cohortLabel.setLabelFor(cohortSwitch);

        buildSteps();
        buildShortcuts();
        buildStates();
        buildConnections();
    }

    /**
     * Sets up the parts that depend on the backend.
     */
    @Override
    public void onAttach() {
        buildAdvice();
        boolean demo = getBackend().isDemo();
        demoPanel.setVisible(demo);
        demoPanel.setManaged(demo);
        cohortSwitch.selectedProperty().addListener(
                (observable, oldValue, newValue) -> cohortChanged(newValue));
    }

    /**
     * Refreshes the status line each time the screen is shown.
     */
    @Override
    public void onShow() {
        statusLabel.setText("How to use GradeZZY, what each state means, "
                + "and which backend features are connected.");
    }

    /**
     * Opens the Submissions screen, where the walkthrough starts.
     */
    @FXML
    public void handleStartWalkthrough() {
        getShell().navigate(Screen.UPLOAD);
    }

    // Switches the demo data between six and 240 students.
    private void cohortChanged(boolean large) {
        getBackend().useLargeSampleCohort(large);
        getState().markDataChanged();
        getShell().refreshContext();
        if (large) {
            showInfo("Loaded 240 generated students into the sample "
                    + "section.");
        } else {
            showInfo("Restored the six sample students.");
        }
    }

    // Lists the seven demo flows as numbered steps.
    private void buildSteps() {
        addStep(1, "Submissions: add six ZIP files, replace the unreadable "
                + "one, add the roster and testers, then press Start "
                + "Grading.", Screen.UPLOAD);
        addStep(2, "Watch Grading Progress. One compile error and one "
                + "timeout are recorded and the batch carries on. Cancel "
                + "keeps completed results; Resume grades the rest.",
                Screen.PROGRESS);
        addStep(3, "Validation: approve a repair. Review Anomalies: reject "
                + "a flag, then Undo.", Screen.VALIDATION,
                Screen.ANOMALY_LOG);
        addStep(4, "Results: open learner.bravo, see the Q1a timeout on the "
                + "Cases tab, then press Retry Q1a.", Screen.RESULTS);
        addStep(5, "In Student Evidence, press Adjust Score, give a "
                + "reason, see Saved, then Undo.", Screen.RESULTS);
        addStep(6, "Results: change the section, select rows, then preview "
                + "the CSV and JSON exports.", Screen.RESULTS);
        addStep(7, "Switch Light and Dark from the context bar at any step. "
                + "Filters, selection, progress and decisions are kept.");
    }

    // Adds one numbered step with a link to each screen it needs.
    private void addStep(int number, String text, Screen... screens) {
        Label numberLabel = new Label(number + ".");
        numberLabel.getStyleClass().add("context-value");
        numberLabel.setMinWidth(NUMBER_WIDTH);
        Label textLabel = new Label(text);
        textLabel.setWrapText(true);
        VBox body = new VBox(LINK_GAP, textLabel);
        HBox.setHgrow(body, Priority.ALWAYS);
        body.setMaxWidth(Double.MAX_VALUE);

        if (screens.length > 0) {
            FlowPane links = new FlowPane();
            for (Screen screen : screens) {
                Button link = new Button("Open " + screen.getTitle());
                link.getStyleClass().add("link-button");
                link.setMinWidth(Region.USE_PREF_SIZE);
                link.setOnAction(event -> getShell().navigate(screen));
                links.getChildren().add(link);
            }
            body.getChildren().add(links);
        }

        HBox row = new HBox(STEP_GAP, numberLabel, body);
        row.setAlignment(Pos.TOP_LEFT);
        stepRows.getChildren().add(row);
    }

    // Lists the keyboard shortcuts as this computer writes them.
    private void buildShortcuts() {
        keysCaption.setText("Shortcut keys use Ctrl on Windows and Cmd on "
                + "macOS. They are written below as this computer shows "
                + "them.");
        for (int index = 0; index < NUMBERED.length; index++) {
            addKey(screenKeys, index, shortcut(DIGITS[index]),
                    NUMBERED[index].getTitle());
        }
        addKey(screenKeys, NUMBERED.length, shortcut(KeyCode.COMMA),
                Screen.SETTINGS.getTitle());
        addKey(screenKeys, NUMBERED.length + 1,
                new KeyCodeCombination(KeyCode.F1).getDisplayText(),
                Screen.HELP.getTitle());

        addKey(workKeys, 0, shortcut(KeyCode.F),
                "Search on the screen shown");
        addKey(workKeys, 1, shortcut(KeyCode.OPEN_BRACKET),
                "Go back to the screen before");
        addKey(workKeys, 2, shortcut(KeyCode.CLOSE_BRACKET),
                "Go to the next step of the grading journey");
        addKey(workKeys, 3, shortcut(KeyCode.A),
                "Select every row in a table");
        addKey(workKeys, 4, KeyCode.ENTER.getName(),
                "Open the selected student on Results");
        addKey(workKeys, 5,
                new KeyCodeCombination(KeyCode.ESCAPE).getDisplayText(),
                "Close a sheet without changing anything");
    }

    // Returns a key with the Shortcut modifier as this computer writes
    // it, such as "Ctrl+F".
    private String shortcut(KeyCode code) {
        return new KeyCodeCombination(code, KeyCombination.SHORTCUT_DOWN)
                .getDisplayText();
    }

    // Adds one shortcut row: the keys and what they do.
    private void addKey(GridPane grid, int row, String keys,
            String meaning) {
        Label keyLabel = new Label(keys);
        keyLabel.getStyleClass().add("key-hint");
        keyLabel.setMinWidth(Region.USE_PREF_SIZE);
        Label meaningLabel = new Label(meaning);
        meaningLabel.setWrapText(true);
        grid.add(keyLabel, 0, row);
        grid.add(meaningLabel, 1, row);
    }

    // Lists every result state with its badge and meaning.
    private void buildStates() {
        int row = 0;
        for (ResultState state : ResultState.values()) {
            StatusBadge badge = StatusBadge.forResult(state);
            badge.setMinWidth(Region.USE_PREF_SIZE);
            Label meaning = new Label(meaningOf(state));
            meaning.setWrapText(true);
            stateRows.add(badge, 0, row);
            stateRows.add(meaning, 1, row);
            row++;
        }
    }

    // Returns one sentence about what a result state means.
    private String meaningOf(ResultState state) {
        if (state == ResultState.UNGRADED) {
            return "Not graded yet, or grading was cancelled before it.";
        } else if (state == ResultState.COULD_NOT_GRADE) {
            return "Grading stopped on this submission; the others carried "
                    + "on.";
        } else if (state == ResultState.COMPILE_ERROR) {
            return "A question folder did not compile, so its tester could "
                    + "not run. Other questions keep their marks.";
        } else if (state == ResultState.TIMED_OUT) {
            return "The tester was stopped at the time limit; earlier cases "
                    + "keep their marks.";
        } else if (state == ResultState.NEEDS_REVIEW) {
            return "Graded, with a flag or repair that waits for your "
                    + "decision. Flags never change marks.";
        } else if (state == ResultState.ZERO) {
            return "Graded, and every question scored 0. This is different "
                    + "from Ungraded.";
        }
        return "Graded, with nothing left to review.";
    }

    // Lists what to do about the common problems.
    private void buildAdvice() {
        addAdvice(StatusBadge.forResult(ResultState.COMPILE_ERROR),
                "Read the compiler message in Student Evidence. If a repair "
                + "renamed a file or folder, approve it on Validation, then "
                + "press Retry Failed Submissions on Grading Progress.");
        addAdvice(StatusBadge.forResult(ResultState.TIMED_OUT),
                "The tester was stopped after "
                + getBackend().getRunTimeoutSeconds() + " s, the limit in "
                + "config.properties. Look for an endless loop, then press "
                + "Retry Q1a (or the question that timed out) in Student "
                + "Evidence.");
        addAdvice(new StatusBadge(Labels.anomalyTitle(
                AnomalyType.INVALID_ARCHIVE), Tone.ERROR),
                "Select it on the Submissions screen and press Replace "
                + "File with a new copy from the student.");
        addAdvice(new StatusBadge(Labels.anomalyTitle(
                AnomalyType.MISSING_FILE), Tone.WARNING),
                "GradeZZY never writes a missing answer for the student. "
                + "The question scores 0 unless a corrected submission is "
                + "graded.");
        addAdvice(StatusBadge.forResult(ResultState.COULD_NOT_GRADE),
                "Press Retry Failed Submissions on Grading Progress. "
                + "Completed results are kept.");
    }

    // Adds one problem with its advice.
    private void addAdvice(StatusBadge badge, String advice) {
        Label adviceLabel = new Label(advice);
        adviceLabel.setWrapText(true);
        adviceRows.getChildren().add(new VBox(ADVICE_GAP, badge,
                adviceLabel));
    }

    // Fills the Backend connections table and its caption.
    private void buildConnections() {
        int works = 0;
        int waiting = 0;
        int proposed = 0;
        for (Capability capability : Capability.values()) {
            if (capability.getStatus() == CapabilityStatus.WORKS_NOW) {
                works++;
            } else if (capability.getStatus() == CapabilityStatus.WAITING) {
                waiting++;
            } else {
                proposed++;
            }
        }
        connectionsCaption.setText(works + " work now, " + waiting
                + " wait for a teammate's service method and " + proposed
                + " are proposed with no contract yet. When you finish a "
                + "service method, change its status in Capability.java.");

        capabilityTable.setColumnResizePolicy(
                TableView.CONSTRAINED_RESIZE_POLICY_FLEX_LAST_COLUMN);
        capabilityTable.getColumns().add(textColumn("Feature",
                FEATURE_WIDTH, false, Capability::getTitle));
        capabilityTable.getColumns().add(textColumn("What it does",
                PURPOSE_WIDTH, false, Capability::getPurpose));
        TableColumn<Capability, CapabilityStatus> status =
                new TableColumn<>("Status");
        status.setCellValueFactory(data -> new ReadOnlyObjectWrapper<>(
                data.getValue().getStatus()));
        status.setCellFactory(column -> new StatusCell());
        status.setMinWidth(STATUS_WIDTH);
        capabilityTable.getColumns().add(status);
        capabilityTable.getColumns().add(textColumn("Owner", OWNER_WIDTH,
                false, Capability::getOwner));
        capabilityTable.getColumns().add(textColumn("Service method",
                SERVICE_WIDTH, true, Capability::getServiceMethod));
        capabilityTable.getColumns().add(textColumn("What it needs",
                NEED_WIDTH, false, Capability::getNeed));
        capabilityTable.setItems(FXCollections.observableArrayList(
                Capability.values()));
    }

    // Creates a column of wrapped text read from each capability.
    private TableColumn<Capability, String> textColumn(String title,
            double minWidth, boolean mono,
            Function<Capability, String> reader) {
        TableColumn<Capability, String> column = new TableColumn<>(title);
        column.setCellValueFactory(data -> new ReadOnlyStringWrapper(
                reader.apply(data.getValue())));
        column.setCellFactory(cell -> new WrapCell(column, mono));
        column.setMinWidth(minWidth);
        return column;
    }

    /**
     * Shows how ready a capability is as an icon and words. The badge is
     * neutral, because green, amber and red mean grading status only.
     */
    private static class StatusCell
            extends TableCell<Capability, CapabilityStatus> {

        /**
         * Shows one status.
         *
         * @param status the row's status
         * @param empty whether the row is empty
         */
        @Override
        protected void updateItem(CapabilityStatus status, boolean empty) {
            super.updateItem(status, empty);
            setText(null);
            if (empty || status == null) {
                setGraphic(null);
                return;
            }
            Icon icon = Icon.EDIT;
            if (status == CapabilityStatus.WORKS_NOW) {
                icon = Icon.CHECK;
            } else if (status == CapabilityStatus.WAITING) {
                icon = Icon.HISTORY;
            }
            setGraphic(new StatusBadge(status.getLabel(), Tone.NEUTRAL,
                    icon));
        }
    }

    /**
     * Shows text that wraps within its column, so long needs stay
     * readable at any window width.
     */
    private static class WrapCell extends TableCell<Capability, String> {

        // The wrapped text
        private final Label label = new Label();

        // The column this cell belongs to
        private final TableColumn<Capability, String> column;

        // Creates a cell; mono is used for service method names.
        WrapCell(TableColumn<Capability, String> column, boolean mono) {
            this.column = column;
            label.setWrapText(true);
            label.setMinHeight(Region.USE_PREF_SIZE);
            if (mono) {
                label.getStyleClass().add("mono");
            }
        }

        /**
         * Shows one value.
         *
         * @param text the value
         * @param empty whether the row is empty
         */
        @Override
        protected void updateItem(String text, boolean empty) {
            super.updateItem(text, empty);
            setText(null);
            if (empty || text == null) {
                setGraphic(null);
                setAccessibleText(null);
                return;
            }
            label.setText(text);
            setGraphic(label);
            setAccessibleText(text);
        }

        /**
         * Returns the height the wrapped text needs at the column's
         * width.
         *
         * @param width ignored; the column's width is used
         * @return the preferred height
         */
        @Override
        protected double computePrefHeight(double width) {
            if (isEmpty() || getGraphic() == null) {
                return MIN_ROW_HEIGHT;
            }
            double textWidth = column.getWidth() - CELL_PADDING;
            double height = label.prefHeight(textWidth) + ROW_PADDING;
            return Math.max(MIN_ROW_HEIGHT, height);
        }
    }
}
