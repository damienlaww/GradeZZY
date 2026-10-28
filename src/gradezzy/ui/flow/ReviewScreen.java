/*
 * File: ReviewScreen.java
 *
 * Purpose: Step 3. One table of every student with a mark per question,
 * the total and a status, filtered by All, Needs review or Failed and
 * searchable by name or, where the backend can, inside the code.
 * Clicking a student opens the student panel beside it.
 *
 * Where it fits: Replaces the old Results and Review Anomalies screens.
 * Approves fixes in bulk and leads to Export.
 */
package gradezzy.ui.flow;

import java.util.ArrayList;
import java.util.Comparator;
import java.util.List;

import javafx.animation.PauseTransition;
import javafx.beans.binding.Bindings;
import javafx.beans.property.ReadOnlyObjectWrapper;
import javafx.collections.FXCollections;
import javafx.collections.ObservableList;
import javafx.collections.transformation.FilteredList;
import javafx.collections.transformation.SortedList;
import javafx.css.PseudoClass;
import javafx.geometry.Insets;
import javafx.geometry.Pos;
import javafx.scene.Node;
import javafx.scene.control.CheckBox;
import javafx.scene.control.ContentDisplay;
import javafx.scene.control.Label;
import javafx.scene.control.TableCell;
import javafx.scene.control.TableColumn;
import javafx.scene.control.TableRow;
import javafx.scene.control.TableView;
import javafx.scene.control.ToggleButton;
import javafx.scene.control.ToggleGroup;
import javafx.scene.layout.HBox;
import javafx.scene.layout.Priority;
import javafx.scene.layout.StackPane;
import javafx.scene.layout.VBox;
import javafx.scene.shape.Rectangle;
import javafx.util.Duration;

import atlantafx.base.controls.CustomTextField;

import gradezzy.model.Anomaly;
import gradezzy.model.Assessment;
import gradezzy.model.FixStatus;
import gradezzy.model.GradingRun;
import gradezzy.model.Question;
import gradezzy.model.QuestionResult;
import gradezzy.model.Submission;
import gradezzy.ui.backend.Capability;
import gradezzy.ui.backend.RepairItem;
import gradezzy.ui.backend.ResultState;

/**
 * The Review step.
 */
public class ReviewScreen extends FlowScreen {

    // The width of a question column
    private static final double QUESTION_WIDTH = 52.0;

    // Below this width the question columns hide while the panel is open
    private static final double NARROW_WIDTH = 1100.0;

    // The width of the tick column
    private static final double TICK_WIDTH = 40.0;

    // The width of the total column
    private static final double TOTAL_WIDTH = 60.0;

    // The width of the status column
    private static final double STATUS_WIDTH = 160.0;

    // The smallest width of the student column
    private static final double STUDENT_WIDTH = 190.0;

    // The width of the search field
    private static final double SEARCH_WIDTH = 220.0;

    // The hint in the search field when it looks at names
    private static final String NAMES_PROMPT = "Name or username";

    // The hint in the search field when it looks inside the code
    private static final String CODE_PROMPT = "Text in the code";

    // The search field is never narrower than this
    private static final double SEARCH_MIN_WIDTH = 140.0;

    // The gap between the heading and the line under it
    private static final double HEADING_GAP = 4.0;

    // The size of the search icon
    private static final double SEARCH_ICON_SIZE = 14.0;

    // The height of one row of the table
    private static final double ROW_HEIGHT = 46.0;

    // The height of the header row; matches flow.css
    private static final double HEADER_HEIGHT = 36.0;

    // The table's border, top and bottom together
    private static final double BORDERS = 2.0;

    // The table is never shorter than this, so its empty note fits
    private static final double MIN_TABLE_HEIGHT = 160.0;

    // The corner of the table box (twice the 12 pixel radius)
    private static final double CORNER_ARC = 24.0;

    // How long typing must pause before the code is searched
    private static final Duration CODE_SEARCH_DELAY = Duration.millis(300);

    // The columns that exist before the question columns are added
    private static final int FIXED_COLUMNS = 4;

    // Where the first question column goes, after tick and student
    private static final int FIRST_QUESTION_COLUMN = 2;

    // Averages are shown to one decimal place
    private static final double ONE_DECIMAL = 10.0;

    // Marks the last row, so it has no line under it
    private static final PseudoClass LAST_ROW =
            PseudoClass.getPseudoClass("last-row");

    // The table and the panel side by side
    private final HBox view = new HBox();

    // The run's students
    private final ObservableList<Submission> rows =
            FXCollections.observableArrayList();

    // The rows that pass the filter and search
    private final FilteredList<Submission> filtered =
            new FilteredList<>(rows);

    // The table
    private final TableView<Submission> table = new TableView<>();

    // The note shown in the table when no row passes the filter
    private final Label placeholder = Parts.muted("No student matches.");

    // The tick in the header that ticks every student shown
    private final CheckBox tickAllBox = new CheckBox();

    // The filter buttons
    private final ToggleButton allButton = new ToggleButton("All");
    private final ToggleButton reviewButton =
            new ToggleButton("Needs review");
    private final ToggleButton failedButton = new ToggleButton("Failed");

    // The counts beside the filter names
    private final Label allCount = new Label();
    private final Label reviewCount = new Label();
    private final Label failedCount = new Label();

    // The search field
    private final CustomTextField search = new CustomTextField();

    // Chooses whether the search looks at names or inside the code
    private final ToggleButton namesButton = new ToggleButton("Names");
    private final ToggleButton codeButton = new ToggleButton("Code");

    // Waits for typing to pause before searching the code
    private final PauseTransition codeDelay =
            new PauseTransition(CODE_SEARCH_DELAY);

    // The students whose code holds the search text
    private final List<Submission> codeMatches = new ArrayList<>();

    // The line under the heading
    private final Label subtitle = Parts.subtitle("");

    // A note when some students could not be graded
    private final HBox banner = Parts.row(Parts.GAP);

    // The details of the clicked student
    private final StudentPanel panel;

    // Shown instead of the table when there is no run
    private final VBox empty = new VBox(Parts.GAP);

    // One column per question
    private final List<TableColumn<Submission, Double>> questionColumns =
            new ArrayList<>();

    // The left part: heading, filters and table
    private final VBox main = new VBox(Parts.GAP);

    /**
     * Creates the Review step.
     *
     * @param window the window
     */
    public ReviewScreen(FlowWindow window) {
        super(window);
        panel = new StudentPanel(window, () -> refreshStatuses(),
                () -> closePanel());
        panel.setVisible(false);
        panel.setManaged(false);

        createFilters();
        createSearch();
        createTable();
        banner.getStyleClass().add("banner");

        HBox tools = Parts.row(Parts.GAP, createFilterBar(), Parts.grow());
        if (getBackend().supports(Capability.CODE_SEARCH)) {
            tools.getChildren().add(createSearchModeBar());
        }
        tools.getChildren().add(search);
        VBox.setMargin(tools, new Insets(Parts.GAP_SMALL / 2, 0, 0, 0));

        VBox header = new VBox(HEADING_GAP, Parts.heading(
                "Review the results"), subtitle);
        main.getChildren().addAll(header, tools, banner, table);
        main.getStyleClass().add("review-main");
        VBox.setVgrow(table, Priority.ALWAYS);
        HBox.setHgrow(main, Priority.ALWAYS);
        main.setMinWidth(0);

        empty.getChildren().addAll(Parts.heading("Nothing to review yet"),
                Parts.subtitle("Grade a section first, or open an earlier "
                + "run from Runs."),
                Parts.link("Go to Set up", () -> window.show(Step.SET_UP)));
        empty.getStyleClass().add("empty-state");
        empty.setAlignment(Pos.CENTER);

        view.getChildren().addAll(main, panel);
        view.getStyleClass().add("review-view");
        view.widthProperty().addListener((observable, before, after)
                -> fitColumns());
        panel.visibleProperty().addListener((observable, before, after)
                -> fitColumns());
    }

    @Override
    public Node getView() {
        if (getState().getRun() == null) {
            return new StackPane(empty);
        }
        return view;
    }

    @Override
    public void onShow() {
        GradingRun run = getState().getRun();
        if (run == null) {
            getActions().setBack(() -> getWindow().show(Step.SET_UP));
            return;
        }
        if (table.getColumns().size() <= FIXED_COLUMNS) {
            addQuestionColumns();
        }
        rows.setAll(run.getSubmissions());
        codeMatches.clear();
        if (codeButton.isSelected()) {
            searchCode();
        }
        if (panel.getSubmission() != null
                && !rows.contains(panel.getSubmission())) {
            closePanel();
        }
        refreshStatuses();
    }

    /**
     * Selects a student in the table and opens the student panel. Used
     * by the developer checks.
     *
     * @param submission the student
     */
    public void selectStudent(Submission submission) {
        table.getSelectionModel().select(submission);
        showStudent(submission);
    }

    /**
     * Closes the panel and shows only the students who need review.
     * Used by the developer checks.
     */
    public void showNeedsReview() {
        closePanel();
        reviewButton.setSelected(true);
    }

    /**
     * Opens a student and the sheet that adjusts one of their marks.
     * Used by the developer checks.
     *
     * @param submission the student
     * @param questionId the question, such as Q1a
     */
    public void adjustMark(Submission submission, String questionId) {
        selectStudent(submission);
        panel.openAdjust(questionId);
    }

    // ------------------------------------------------------------ build

    // Builds the All, Needs review and Failed buttons with their counts.
    private void createFilters() {
        ToggleGroup group = new ToggleGroup();
        ToggleButton[] buttons = {allButton, reviewButton, failedButton};
        Label[] counts = {allCount, reviewCount, failedCount};
        for (int index = 0; index < buttons.length; index++) {
            buttons[index].setToggleGroup(group);
            counts[index].getStyleClass().add("count");
            buttons[index].setGraphic(counts[index]);
            buttons[index].setContentDisplay(ContentDisplay.RIGHT);
        }
        allButton.setSelected(true);
        allButton.getStyleClass().add("left-pill");
        reviewButton.getStyleClass().add("center-pill");
        failedButton.getStyleClass().add("right-pill");
        group.selectedToggleProperty().addListener((observable, before,
                after) -> {
            if (after == null) {
                before.setSelected(true);
            }
            applyFilter();
        });
    }

    // Puts the filter buttons side by side.
    private HBox createFilterBar() {
        HBox bar = new HBox(allButton, reviewButton, failedButton);
        bar.getStyleClass().add("segmented");
        bar.setMinWidth(HBox.USE_PREF_SIZE);
        return bar;
    }

    // Builds the search field with its icon.
    private void createSearch() {
        search.setPromptText(NAMES_PROMPT);
        search.setLeft(Parts.icon(Parts.SEARCH, SEARCH_ICON_SIZE));
        search.setPrefWidth(SEARCH_WIDTH);
        search.setMinWidth(SEARCH_MIN_WIDTH);
        search.getStyleClass().add("review-search");
        search.textProperty().addListener((observable, before, after)
                -> searchChanged());
        codeDelay.setOnFinished(event -> searchCode());
    }

    // Builds the Names and Code switch beside the search field.
    private HBox createSearchModeBar() {
        ToggleGroup group = new ToggleGroup();
        namesButton.setToggleGroup(group);
        codeButton.setToggleGroup(group);
        namesButton.setSelected(true);
        namesButton.getStyleClass().add("left-pill");
        codeButton.getStyleClass().add("right-pill");
        group.selectedToggleProperty().addListener((observable, before,
                after) -> {
            if (after == null) {
                before.setSelected(true);
                return;
            }
            searchModeChanged();
        });
        HBox bar = new HBox(namesButton, codeButton);
        bar.getStyleClass().addAll("segmented", "search-mode");
        bar.setMinWidth(HBox.USE_PREF_SIZE);
        return bar;
    }

    // Builds the tick, student, total and status columns.
    private void createTable() {
        table.getStyleClass().add("results-table");
        table.setPlaceholder(placeholder);
        table.setFixedCellSize(ROW_HEIGHT);
        table.setColumnResizePolicy(
                TableView.CONSTRAINED_RESIZE_POLICY_ALL_COLUMNS);
        table.setRowFactory(view -> new ResultRow());

        TableColumn<Submission, Submission> tick = new TableColumn<>();
        tickAllBox.setOnAction(event -> tickAll(tickAllBox.isSelected()));
        tick.setGraphic(tickAllBox);
        tick.setSortable(false);
        fixWidth(tick, TICK_WIDTH);
        tick.setCellValueFactory(cell -> new ReadOnlyObjectWrapper<>(
                cell.getValue()));
        tick.setCellFactory(column -> new TickCell());

        TableColumn<Submission, Submission> student =
                new TableColumn<>("Student");
        student.setMinWidth(STUDENT_WIDTH);
        student.setCellValueFactory(cell -> new ReadOnlyObjectWrapper<>(
                cell.getValue()));
        student.setCellFactory(column -> new StudentCell());
        student.setComparator(Comparator.comparing(
                submission -> Students.name(submission).toLowerCase()));

        TableColumn<Submission, Double> total = new TableColumn<>("Total");
        fixWidth(total, TOTAL_WIDTH);
        total.setCellValueFactory(cell -> new ReadOnlyObjectWrapper<>(
                cell.getValue().getTotalScore()));
        total.setCellFactory(column -> new MarkCell(true));
        total.getStyleClass().add("centred");

        TableColumn<Submission, Submission> status =
                new TableColumn<>("Status");
        fixWidth(status, STATUS_WIDTH);
        status.setCellValueFactory(cell -> new ReadOnlyObjectWrapper<>(
                cell.getValue()));
        status.setCellFactory(column -> new StatusCell());
        status.setComparator(Comparator.comparing(
                submission -> Students.state(submission, getBackend())
                .ordinal()));

        table.getColumns().add(tick);
        table.getColumns().add(student);
        table.getColumns().add(total);
        table.getColumns().add(status);

        SortedList<Submission> sorted = new SortedList<>(filtered);
        sorted.comparatorProperty().bind(table.comparatorProperty());
        table.setItems(sorted);
        table.getSelectionModel().selectedItemProperty().addListener(
                (observable, before, after) -> showStudent(after));

        // The box ends at the last row, or fills the space and scrolls
        table.maxHeightProperty().bind(Bindings.max(MIN_TABLE_HEIGHT,
                Bindings.size(sorted).multiply(ROW_HEIGHT)
                .add(HEADER_HEIGHT + BORDERS)));
        table.setMinHeight(MIN_TABLE_HEIGHT);

        // Rounds the corners of the header and the last row too
        Rectangle corners = new Rectangle();
        corners.setArcWidth(CORNER_ARC);
        corners.setArcHeight(CORNER_ARC);
        corners.widthProperty().bind(table.widthProperty());
        corners.heightProperty().bind(table.heightProperty());
        table.setClip(corners);
    }

    // Gives a column one width that never changes.
    private void fixWidth(TableColumn<Submission, ?> column, double width) {
        column.setMinWidth(width);
        column.setPrefWidth(width);
        column.setMaxWidth(width);
        column.setResizable(false);
    }

    // Adds one column per question, between Student and Total.
    private void addQuestionColumns() {
        Assessment assessment = getBackend().getAssessment();
        if (assessment == null) {
            return;
        }
        int index = FIRST_QUESTION_COLUMN;
        for (Question question : assessment.getQuestions()) {
            TableColumn<Submission, Double> column = new TableColumn<>(
                    question.getId());
            fixWidth(column, QUESTION_WIDTH);
            String id = question.getId();
            column.setCellValueFactory(cell -> new ReadOnlyObjectWrapper<>(
                    scoreOf(cell.getValue(), id)));
            column.setCellFactory(cell -> new MarkCell(false));
            column.getStyleClass().add("centred");
            questionColumns.add(column);
            table.getColumns().add(index, column);
            index++;
        }
    }

    // Hides the question columns in a narrow window while the panel is
    // open; the panel shows every question's mark instead.
    private void fitColumns() {
        boolean narrow = view.getWidth() > 0
                && view.getWidth() < NARROW_WIDTH && panel.isVisible();
        for (TableColumn<Submission, Double> column : questionColumns) {
            column.setVisible(!narrow);
        }
    }

    // Returns a student's mark for one question, or null.
    private Double scoreOf(Submission submission, String questionId) {
        for (QuestionResult result : submission.getQuestionResults()) {
            if (result.getQuestion().getId().equals(questionId)) {
                return result.getScore();
            }
        }
        return null;
    }

    // ---------------------------------------------------------- refresh

    // Redraws counts, statuses, the banner and the action bar.
    private void refreshStatuses() {
        GradingRun run = getState().getRun();
        if (run == null) {
            return;
        }
        int needReview = countNeedingReview();
        int failed = 0;
        double sum = 0;
        for (Submission submission : rows) {
            if (Students.isFailed(Students.state(submission,
                    getBackend()))) {
                failed++;
            }
            sum += submission.getTotalScore();
        }
        allCount.setText(String.valueOf(rows.size()));
        reviewCount.setText(String.valueOf(needReview));
        failedCount.setText(String.valueOf(failed));

        String average = "";
        Assessment assessment = getBackend().getAssessment();
        if (!rows.isEmpty() && assessment != null) {
            average = " · average " + Parts.mark(Math.round(
                    sum / rows.size() * ONE_DECIMAL) / ONE_DECIMAL)
                    + " out of " + Parts.mark(assessment.getMaxTotal());
        }
        subtitle.setText(run.getSection().getModuleCode() + " · "
                + run.getSection().getSectionCode() + " · "
                + run.getAssessment().getName() + average);

        refreshBanner(run);
        applyFilter();
        table.refresh();
        refreshActions();
    }

    // Counts the students with something still to check.
    private int countNeedingReview() {
        int count = 0;
        for (Submission submission : rows) {
            if (Students.openCount(submission, getBackend()) > 0) {
                count++;
            }
        }
        return count;
    }

    // Shows the retry note when some students could not be graded.
    private void refreshBanner(GradingRun run) {
        banner.getChildren().clear();
        int couldNot = 0;
        for (Submission submission : rows) {
            if (Students.state(submission, getBackend())
                    == ResultState.COULD_NOT_GRADE) {
                couldNot++;
            }
        }
        boolean show = couldNot > 0
                && getBackend().supports(Capability.RETRY_FAILED);
        banner.setVisible(show);
        banner.setManaged(show);
        if (show) {
            banner.getChildren().addAll(Parts.pill("Could not grade",
                    Tone.BAD), Parts.strong(Parts.plural(couldNot,
                    "submission") + " did not finish."),
                    Parts.link("Retry " + (couldNot == 1 ? "it" : "them"),
                    () -> getWindow().retryRun(run)));
        }
    }

    // ----------------------------------------------------------- search

    // Filters again when the search text changes.
    private void searchChanged() {
        if (!codeButton.isSelected()) {
            applyFilter();
            return;
        }
        codeDelay.playFromStart();
    }

    // Switches the search between names and code.
    private void searchModeChanged() {
        codeDelay.stop();
        codeMatches.clear();
        if (codeButton.isSelected()) {
            search.setPromptText(CODE_PROMPT);
            searchCode();
            return;
        }
        search.setPromptText(NAMES_PROMPT);
        applyFilter();
    }

    // Asks the backend which students' code holds the search text.
    private void searchCode() {
        codeMatches.clear();
        String text = search.getText().trim();
        GradingRun run = getState().getRun();
        if (!text.isEmpty() && run != null) {
            codeMatches.addAll(getBackend().searchCode(run, text));
        }
        applyFilter();
    }

    // Applies the filter buttons and the search text.
    private void applyFilter() {
        String text = search.getText().trim().toLowerCase();
        boolean inCode = codeButton.isSelected();
        boolean onlyReview = reviewButton.isSelected();
        boolean onlyFailed = failedButton.isSelected();
        filtered.setPredicate(submission -> {
            if (onlyReview
                    && Students.openCount(submission, getBackend()) == 0) {
                return false;
            }
            if (onlyFailed && !Students.isFailed(Students.state(
                    submission, getBackend()))) {
                return false;
            }
            if (text.isEmpty()) {
                return true;
            }
            if (inCode) {
                return codeMatches.contains(submission);
            }
            return Students.name(submission).toLowerCase().contains(text)
                    || submission.getUsername().toLowerCase()
                    .contains(text);
        });
        placeholder.setText(emptyNote(text, inCode, onlyReview,
                onlyFailed));
    }

    // Returns the note shown when no student passes the filter.
    private String emptyNote(String text, boolean inCode,
            boolean onlyReview, boolean onlyFailed) {
        if (!text.isEmpty() && inCode) {
            return "No student's code contains \"" + search.getText().trim()
                    + "\". The search is case sensitive.";
        } else if (!text.isEmpty()) {
            return "No student matches \"" + search.getText().trim()
                    + "\".";
        } else if (onlyReview) {
            return "Every student is checked.";
        } else if (onlyFailed) {
            return "No student failed to grade.";
        }
        return "No students in this run.";
    }

    // ------------------------------------------------------------ panel

    // Shows a student in the panel.
    private void showStudent(Submission submission) {
        if (submission == null) {
            return;
        }
        panel.show(submission);
        panel.setVisible(true);
        panel.setManaged(true);
    }

    /**
     * Closes the student panel when Esc is pressed.
     *
     * @return true when the panel was open
     */
    @Override
    public boolean onEscape() {
        if (!panel.isVisible()) {
            return false;
        }
        closePanel();
        return true;
    }

    // Hides the panel and forgets the clicked row, so it can be clicked
    // again.
    private void closePanel() {
        panel.show(null);
        panel.setVisible(false);
        panel.setManaged(false);
        table.getSelectionModel().clearSelection();
    }

    // Ticks or unticks every student shown.
    private void tickAll(boolean ticked) {
        List<Submission> selected = getState().getSelected();
        selected.clear();
        if (ticked) {
            selected.addAll(filtered);
        }
        table.refresh();
        refreshActions();
    }

    // ---------------------------------------------------------- actions

    // Fills the action bar.
    private void refreshActions() {
        if (!isOnScreen()) {
            return;
        }
        ActionBar actions = getActions();
        actions.reset();
        actions.setBack(() -> getWindow().show(Step.GRADE));
        actions.setStatus(statusText());
        List<RepairItem> open = openRepairs();
        if (!open.isEmpty()) {
            actions.addSecondary(Parts.secondary("Approve all "
                    + Parts.plural(open.size(), "fix", "fixes"),
                    () -> approveAll(open)));
        }
        actions.setPrimary("Continue to export", () -> continueToExport(),
                true);
    }

    // Returns the sentence beside Back.
    private String statusText() {
        int needReview = countNeedingReview();
        String text;
        if (needReview == 0) {
            text = "Every student is checked.";
        } else if (needReview == 1) {
            text = "1 student still needs review.";
        } else {
            text = needReview + " students still need review.";
        }
        int ticked = getState().getSelected().size();
        if (ticked > 0) {
            text = text + " " + Parts.plural(ticked, "student")
                    + " ticked for export.";
        }
        return text;
    }

    // Returns the fixes still waiting, for the ticked students or all.
    private List<RepairItem> openRepairs() {
        List<RepairItem> open = new ArrayList<>();
        GradingRun run = getState().getRun();
        if (run == null
                || !getBackend().supports(Capability.REPAIR_DECISIONS)) {
            return open;
        }
        List<Submission> scope = getState().getSelected();
        for (RepairItem repair : getBackend().listRepairs(run)) {
            Anomaly anomaly = repair.getAnomaly();
            if (anomaly.getFixStatus() == FixStatus.APPLIED
                    && !repair.isBlocked()
                    && (scope.isEmpty() || isTicked(anomaly.getUsername()))) {
                open.add(repair);
            }
        }
        return open;
    }

    // Says whether the student with a username is ticked.
    private boolean isTicked(String username) {
        for (Submission submission : getState().getSelected()) {
            if (submission.getUsername().equals(username)) {
                return true;
            }
        }
        return false;
    }

    // Approves every waiting fix.
    private void approveAll(List<RepairItem> open) {
        int approved = 0;
        for (RepairItem repair : open) {
            if (getBackend().approveRepair(repair).isDone()) {
                approved++;
            }
        }
        getWindow().notify("Approved " + Parts.plural(approved, "fix",
                "fixes") + ".");
        panel.refresh();
        refreshStatuses();
    }

    // Counts what the instructor can still settle before exporting.
    private int countOpen() {
        int count = 0;
        boolean flags = getBackend().supports(Capability.FLAG_REVIEW);
        boolean fixes = getBackend().supports(Capability.REPAIR_DECISIONS);
        for (Submission submission : rows) {
            for (Anomaly anomaly : submission.getAnomalies()) {
                boolean isFix = anomaly.getFixStatus() == FixStatus.APPLIED;
                if (getBackend().needsReview(anomaly)
                        && ((isFix && fixes) || (!isFix && flags))) {
                    count++;
                }
            }
        }
        return count;
    }

    // Moves to Export, asking once when things are still open.
    private void continueToExport() {
        int open = countOpen();
        if (open == 0) {
            getWindow().advanceTo(Step.EXPORT);
            return;
        }
        getWindow().confirm(Parts.plural(open, "thing")
                + " not checked yet", "You can export now and come back "
                + "to Review later. Marks do not change until you decide.",
                "Continue anyway", () -> getWindow().advanceTo(Step.EXPORT));
    }

    // ------------------------------------------------------------ cells

    // A row that knows whether it is the last, so it draws no line
    // under itself on top of the table's border.
    private class ResultRow extends TableRow<Submission> {

        @Override
        public void updateIndex(int index) {
            super.updateIndex(index);
            markLast();
        }

        @Override
        protected void updateItem(Submission submission, boolean isEmpty) {
            super.updateItem(submission, isEmpty);
            markLast();
        }

        // Sets the last-row style when this row shows the last student.
        private void markLast() {
            boolean last = !isEmpty()
                    && getIndex() == table.getItems().size() - 1;
            pseudoClassStateChanged(LAST_ROW, last);
        }
    }

    // A check box that adds the row's student to the export scope.
    private class TickCell extends TableCell<Submission, Submission> {

        // The check box
        private final CheckBox box = new CheckBox();

        // Creates the cell.
        TickCell() {
            box.setOnAction(event -> {
                Submission submission = getItem();
                List<Submission> selected = getState().getSelected();
                if (box.isSelected() && !selected.contains(submission)) {
                    selected.add(submission);
                } else if (!box.isSelected()) {
                    selected.remove(submission);
                    tickAllBox.setSelected(false);
                }
                refreshActions();
            });
        }

        @Override
        protected void updateItem(Submission submission, boolean isEmpty) {
            super.updateItem(submission, isEmpty);
            if (isEmpty || submission == null) {
                setGraphic(null);
                return;
            }
            box.setSelected(getState().getSelected().contains(submission));
            setGraphic(box);
        }
    }

    // The student's name above the username.
    private class StudentCell extends TableCell<Submission, Submission> {

        @Override
        protected void updateItem(Submission submission, boolean isEmpty) {
            super.updateItem(submission, isEmpty);
            if (isEmpty || submission == null) {
                setGraphic(null);
                return;
            }
            Label name = Parts.strong(Students.name(submission));
            Label username = Parts.muted(submission.getUsername());
            username.getStyleClass().add("small-text");
            VBox box = new VBox(name, username);
            box.getStyleClass().add("student-cell");
            box.setAlignment(Pos.CENTER_LEFT);
            box.setMinWidth(0);
            setGraphic(box);
        }
    }

    // A mark, grey when zero, a dash when there is none.
    private static class MarkCell extends TableCell<Submission, Double> {

        // True for the bold total column
        private final boolean bold;

        // Creates the cell.
        MarkCell(boolean bold) {
            this.bold = bold;
            setAlignment(Pos.CENTER);
        }

        @Override
        protected void updateItem(Double mark, boolean isEmpty) {
            super.updateItem(mark, isEmpty);
            getStyleClass().removeAll("zero", "total");
            if (isEmpty) {
                setText(null);
                return;
            }
            if (mark == null) {
                setText("-");
                getStyleClass().add("zero");
                return;
            }
            setText(Parts.mark(mark));
            if (mark == 0.0) {
                getStyleClass().add("zero");
            }
            if (bold) {
                getStyleClass().add("total");
            }
        }
    }

    // The status pill.
    private class StatusCell extends TableCell<Submission, Submission> {

        @Override
        protected void updateItem(Submission submission, boolean isEmpty) {
            super.updateItem(submission, isEmpty);
            if (isEmpty || submission == null) {
                setGraphic(null);
                return;
            }
            setGraphic(Students.pill(submission, getBackend()));
        }
    }
}
