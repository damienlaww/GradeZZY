/*
 * File: ReviewScreen.java
 *
 * Purpose: Step 3. One table of every student with a mark per question,
 * the total and a status, filtered by All, Needs review or Failed and
 * searchable. Clicking a student opens the student panel beside it.
 *
 * Where it fits: Replaces the old Results and Review Anomalies screens.
 * Approves fixes in bulk and leads to Export.
 */
package gradezzy.ui.flow;

import java.util.ArrayList;
import java.util.Comparator;
import java.util.List;

import javafx.beans.property.ReadOnlyObjectWrapper;
import javafx.collections.FXCollections;
import javafx.collections.ObservableList;
import javafx.collections.transformation.FilteredList;
import javafx.collections.transformation.SortedList;
import javafx.geometry.Pos;
import javafx.scene.Node;
import javafx.scene.control.CheckBox;
import javafx.scene.control.Label;
import javafx.scene.control.TableCell;
import javafx.scene.control.TableColumn;
import javafx.scene.control.TableView;
import javafx.scene.control.TextField;
import javafx.scene.control.ToggleButton;
import javafx.scene.control.ToggleGroup;
import javafx.scene.layout.HBox;
import javafx.scene.layout.Priority;
import javafx.scene.layout.StackPane;
import javafx.scene.layout.VBox;

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
    private static final double QUESTION_WIDTH = 58.0;

    // Below this width the question columns hide while the panel is open
    private static final double NARROW_WIDTH = 1100.0;

    // The width of the tick column
    private static final double TICK_WIDTH = 44.0;

    // The width of the total column
    private static final double TOTAL_WIDTH = 72.0;

    // The width of the status column
    private static final double STATUS_WIDTH = 170.0;

    // The smallest width of the student column
    private static final double STUDENT_WIDTH = 190.0;

    // The width of the search field
    private static final double SEARCH_WIDTH = 240.0;

    // The columns that exist before the question columns are added
    private static final int FIXED_COLUMNS = 4;

    // Averages are shown to one decimal place
    private static final double ONE_DECIMAL = 10.0;

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

    // The filter buttons
    private final ToggleButton allButton = new ToggleButton();
    private final ToggleButton reviewButton = new ToggleButton();
    private final ToggleButton failedButton = new ToggleButton();

    // The search field
    private final TextField search = new TextField();

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
        panel = new StudentPanel(window, () -> refreshStatuses());
        panel.setVisible(false);
        panel.setManaged(false);

        createFilters();
        createTable();
        banner.getStyleClass().add("banner");

        search.setPromptText("Search by name or username");
        search.setPrefWidth(SEARCH_WIDTH);
        search.textProperty().addListener((observable, before, after)
                -> applyFilter());
        HBox tools = Parts.row(Parts.GAP, createFilterBar(), Parts.grow(),
                search);

        main.getChildren().addAll(new VBox(Parts.heading(
                "Review the results"), subtitle), tools, banner, table);
        main.getStyleClass().add("review-main");
        VBox.setVgrow(table, Priority.ALWAYS);
        HBox.setHgrow(main, Priority.ALWAYS);

        empty.getChildren().addAll(Parts.heading("Nothing to review yet"),
                Parts.subtitle("Grade a section first, or open an earlier "
                + "run from History."),
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
        if (panel.getSubmission() != null
                && !rows.contains(panel.getSubmission())) {
            panel.show(null);
            panel.setVisible(false);
            panel.setManaged(false);
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

    // ------------------------------------------------------------ build

    // Builds the All, Needs review and Failed buttons.
    private void createFilters() {
        ToggleGroup group = new ToggleGroup();
        allButton.setToggleGroup(group);
        reviewButton.setToggleGroup(group);
        failedButton.setToggleGroup(group);
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
        return bar;
    }

    // Builds the tick, student, total and status columns.
    private void createTable() {
        table.getStyleClass().add("results-table");
        table.setPlaceholder(Parts.muted("No student matches."));
        table.setColumnResizePolicy(
                TableView.CONSTRAINED_RESIZE_POLICY_FLEX_LAST_COLUMN);

        TableColumn<Submission, Submission> tick = new TableColumn<>();
        CheckBox all = new CheckBox();
        all.setOnAction(event -> tickAll(all.isSelected()));
        tick.setGraphic(all);
        tick.setSortable(false);
        tick.setPrefWidth(TICK_WIDTH);
        tick.setMaxWidth(TICK_WIDTH);
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
        total.setPrefWidth(TOTAL_WIDTH);
        total.setMaxWidth(TOTAL_WIDTH);
        total.setCellValueFactory(cell -> new ReadOnlyObjectWrapper<>(
                cell.getValue().getTotalScore()));
        total.setCellFactory(column -> new MarkCell(true));
        total.getStyleClass().add("centred");

        TableColumn<Submission, Submission> status =
                new TableColumn<>("Status");
        status.setPrefWidth(STATUS_WIDTH);
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
    }

    // Adds one column per question, between Student and Total.
    private void addQuestionColumns() {
        Assessment assessment = getBackend().getAssessment();
        if (assessment == null) {
            return;
        }
        int index = 2;
        for (Question question : assessment.getQuestions()) {
            TableColumn<Submission, Double> column = new TableColumn<>(
                    question.getId());
            column.setPrefWidth(QUESTION_WIDTH);
            column.setMaxWidth(QUESTION_WIDTH);
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
        int needReview = 0;
        int failed = 0;
        double sum = 0;
        for (Submission submission : rows) {
            ResultState state = Students.state(submission, getBackend());
            if (Students.openCount(submission, getBackend()) > 0) {
                needReview++;
            }
            if (Students.isFailed(state)) {
                failed++;
            }
            sum += submission.getTotalScore();
        }
        allButton.setText("All  " + rows.size());
        reviewButton.setText("Needs review  " + needReview);
        failedButton.setText("Failed  " + failed);

        String average = "";
        Assessment assessment = getBackend().getAssessment();
        if (!rows.isEmpty() && assessment != null) {
            average = "  ·  average " + Parts.mark(Math.round(
                    sum / rows.size() * ONE_DECIMAL) / ONE_DECIMAL) + " out of "
                    + Parts.mark(assessment.getMaxTotal());
        }
        subtitle.setText(run.getSection().getModuleCode() + "  ·  "
                + run.getSection().getSectionCode() + "  ·  "
                + run.getAssessment().getName() + average);

        refreshBanner(run);
        applyFilter();
        table.refresh();
        refreshActions();
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

    // Applies the filter buttons and the search text.
    private void applyFilter() {
        String text = search.getText().trim().toLowerCase();
        boolean onlyReview = reviewButton.isSelected();
        boolean onlyFailed = failedButton.isSelected();
        filtered.setPredicate(submission -> {
            ResultState state = Students.state(submission, getBackend());
            if (onlyReview
                    && Students.openCount(submission, getBackend()) == 0) {
                return false;
            }
            if (onlyFailed && !Students.isFailed(state)) {
                return false;
            }
            if (text.isEmpty()) {
                return true;
            }
            return Students.name(submission).toLowerCase().contains(text)
                    || submission.getUsername().toLowerCase()
                    .contains(text);
        });
    }

    // Shows a student in the panel.
    private void showStudent(Submission submission) {
        if (submission == null) {
            return;
        }
        panel.show(submission);
        panel.setVisible(true);
        panel.setManaged(true);
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
        int ticked = getState().getSelected().size();
        if (ticked > 0) {
            actions.setStatus(Parts.plural(ticked, "student")
                    + " ticked for export.");
        }
        List<RepairItem> open = openRepairs();
        if (!open.isEmpty()) {
            actions.addSecondary(Parts.secondary("Approve all "
                    + Parts.plural(open.size(), "fix", "fixes"),
                    () -> approveAll(open)));
        }
        actions.setPrimary("Continue to export", () -> continueToExport(),
                true);
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
            VBox box = new VBox(name, Parts.muted(submission.getUsername()));
            box.getStyleClass().add("student-cell");
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
                setText("–");
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
