/*
 * File: ReviewScreen.java
 *
 * Purpose: Step 3. One table of every student with a mark per question,
 * the total and a status, filtered by All, Needs a look, Not fully graded
 * or No submission and searchable by name or, where the backend can, inside
 * the code.
 * Clicking a student opens the student panel beside it.
 *
 * Where it fits: Replaces the old Results and Review Anomalies screens.
 * Approves fixes in bulk and leads to Export.
 */
package gradezzy.ui.flow;

import java.util.ArrayList;
import java.util.List;

import javafx.beans.binding.Bindings;
import javafx.beans.binding.NumberBinding;
import javafx.beans.property.ReadOnlyObjectWrapper;
import javafx.beans.value.ObservableValue;
import javafx.collections.FXCollections;
import javafx.collections.ObservableList;
import javafx.collections.transformation.FilteredList;
import javafx.collections.transformation.SortedList;
import javafx.geometry.Insets;
import javafx.geometry.Pos;
import javafx.scene.Node;
import javafx.scene.control.CheckBox;
import javafx.scene.control.ContentDisplay;
import javafx.scene.control.Label;
import javafx.scene.control.TableColumn;
import javafx.scene.control.TableColumn.CellDataFeatures;
import javafx.scene.control.TableView;
import javafx.scene.control.Toggle;
import javafx.scene.control.ToggleButton;
import javafx.scene.control.ToggleGroup;
import javafx.scene.layout.HBox;
import javafx.scene.layout.Priority;
import javafx.scene.layout.Region;
import javafx.scene.layout.StackPane;
import javafx.scene.layout.VBox;
import javafx.scene.shape.Rectangle;
import javafx.scene.text.Font;
import javafx.scene.text.FontWeight;
import javafx.scene.text.Text;

import atlantafx.base.controls.CustomTextField;

import gradezzy.config.ConfigKeys;
import gradezzy.model.Anomaly;
import gradezzy.model.AnomalyType;
import gradezzy.model.Assessment;
import gradezzy.model.FixStatus;
import gradezzy.model.GradingRun;
import gradezzy.model.Question;
import gradezzy.model.QuestionResult;
import gradezzy.model.Student;
import gradezzy.model.Submission;
import gradezzy.model.SubmissionStatus;
import gradezzy.ui.backend.Capability;
import gradezzy.ui.backend.ImportItem;
import gradezzy.ui.backend.RepairItem;
import gradezzy.ui.backend.ResultState;
import gradezzy.ui.backend.ReviewDecision;
import gradezzy.ui.backend.ReviewRules;
import gradezzy.ui.backend.StatusCounts;

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

    // The width of the status column, room for "Q1a, Q1b, Q2a did not
    // compile"
    private static final double STATUS_WIDTH = 220.0;

    // The narrowest the status column gets, so "Status" fits
    private static final double STATUS_MIN_WIDTH = 110.0;

    // A pill's dot, gap and padding, and the cell's padding, beyond its
    // words; and the font of its words, from flow.css
    private static final double PILL_EXTRA = 56.0;
    private static final String PILL_FONT = "IBM Plex Sans";
    private static final double PILL_FONT_SIZE = 12.0;

    // The width of the arrow column that shows a row can be opened
    private static final double OPEN_WIDTH = 36.0;

    // The size of the icon in front of the hint under the tools
    private static final double HINT_ICON_SIZE = 14.0;

    // The smallest width of the student column
    private static final double STUDENT_WIDTH = 190.0;



    // The widest the student column gets
    // It must be finite: with Double.MAX_VALUE the table makes the
    // column endlessly wide and cuts off the table's right border
    private static final double STUDENT_MAX_WIDTH = 4000.0;

    // The width of the search field
    private static final double SEARCH_WIDTH = 220.0;

    // The hint in the search field
    private static final String NAMES_PROMPT = "Name or username  ("
            + Parts.shortcut("F") + ")";

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

    // A class file problem is shown once only when at least this many
    // graded students share it
    private static final int MIN_SHARED_STUDENTS = 3;

    // Where the first question column goes, after tick and student
    private static final int FIRST_QUESTION_COLUMN = 2;

    // The most fixes named when keeping them all
    private static final int MAX_LISTED_FIXES = 8;

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

    // The rounded frame around the table, which draws its border
    private final StackPane tableFrame = new StackPane();

    // The note shown in the table when no row passes the filter
    private final Label placeholder = Parts.muted("No student matches.");

    // The tick in the header that ticks every student shown
    private final CheckBox tickAllBox = new CheckBox();

    // The filter buttons
    private final ToggleButton allButton = new ToggleButton("All");
    private final ToggleButton reviewButton =
            new ToggleButton("Needs a look");
    private final ToggleButton failedButton =
            new ToggleButton("Not fully graded");
    private final ToggleButton missingButton =
            new ToggleButton("No submission");

    // The status column, as wide as the widest pill shown
    private final TableColumn<Submission, Submission> statusColumn =
            new TableColumn<>("Status");

    // The student column, narrower while the student panel is open
    private final TableColumn<Submission, Submission> studentColumn =
            new TableColumn<>("Student");

    // The arrow column, hidden while the student panel is open
    private final TableColumn<Submission, Submission> openColumn =
            new TableColumn<>();

    // The Total column, whose tip names the run's maximum mark
    private final TableColumn<Submission, Double> totalColumn =
            new TableColumn<>("Total");

    // The counts beside the filter names
    private final Label allCount = new Label();
    private final Label reviewCount = new Label();
    private final Label failedCount = new Label();
    private final Label missingCount = new Label();

    // The rows made for students on the score sheet with no submission
    private final List<Submission> missingRows = new ArrayList<>();

    // The run the missing rows were made for, or null
    private GradingRun missingRowsRun = null;

    // The rows made for chosen files grading skipped
    private final List<Submission> skippedRows = new ArrayList<>();

    // The run the skipped rows were made for, or null
    private GradingRun skippedRowsRun = null;

    // The search field
    private final CustomTextField search = new CustomTextField();

    // The line under the heading
    private final Label subtitle = Parts.subtitle("");

    // A note when some students could not be graded, in the filter row
    private final HBox banner = Parts.row(Parts.GAP);

    // Says how to open a student and what ticking does, or, once any are
    // ticked, how many are ticked for export
    private final HBox guide = Parts.row(Parts.GAP_SMALL);
    // One note per problem every student shares, above the table
    private final VBox sharedBanner = new VBox(Parts.GAP_SMALL);

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
        // The retry note has its own line above the table, as the shared
        // note does, so a narrow window never cuts its words off
        banner.getStyleClass().add("banner");
        banner.setMinWidth(0);
        sharedBanner.getStyleClass().addAll("banner", "warn");

        HBox tools = Parts.row(Parts.GAP,
                new Node[] {createFilterBar(), Parts.grow()});
        tools.getChildren().add(search);
        VBox.setMargin(tools, new Insets(Parts.GAP_SMALL / 2, 0, 0, 0));

        VBox header = new VBox(HEADING_GAP, Parts.heading(
                "Review the results"), subtitle);
        guide.getStyleClass().add("review-guide");
        main.getChildren().addAll(header, tools, guide, banner, sharedBanner,
                tableFrame);
        main.getStyleClass().add("review-main");
        VBox.setVgrow(tableFrame, Priority.ALWAYS);
        HBox.setHgrow(main, Priority.ALWAYS);
        main.setMinWidth(0);

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

    /**
     * Returns the Review step, or an empty state when nothing has
     * been graded yet.
     */
    @Override
    public Node getView() {
        if (getState().getRun() == null) {
            return new StackPane(empty);
        }
        return view;
    }

    /**
     * Fills the table with the current run and sets up the action
     * bar.
     */
    @Override
    public void onShow() {
        GradingRun run = getState().getRun();
        if (run == null) {
            getActions().setBack(() -> getWindow().show(Step.SET_UP));
            return;
        }
        Assessment graded = run.getAssessment();
        if (graded == null) {
            graded = getBackend().getAssessment();
        }
        if (!showsQuestionsOf(graded)) {
            table.getColumns().removeAll(questionColumns);
            questionColumns.clear();
            addQuestionColumns(graded);
        }
        header(totalColumn, "Total", "/ " + Parts.mark(graded.getMaxTotal()),
                "Out of " + Parts.mark(graded.getMaxTotal()) + ", the sum "
                + "of the question marks.");
        rows.setAll(run.getSubmissions());
        rows.addAll(findSkippedRows(run));
        rows.addAll(findMissingRows(run));
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
     * Puts the cursor in the search field.
     */
    public void focusSearch() {
        search.requestFocus();
        search.selectAll();
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

    // Returns one "Could not open" row per skipped file of an ungraded user.
    private List<Submission> findSkippedRows(GradingRun run) {
        if (run == skippedRowsRun) {
            return skippedRows;
        }
        skippedRows.clear();
        skippedRowsRun = run;
        for (ImportItem item : getState().getSkippedFiles(run)) {
            String username = item.getUsername();
            // A student graded from another file is shown as graded
            if (username.isEmpty()) {
                skippedRows.add(createSkippedRow(run, item, item.getName()));
            } else if (!isInRun(run, username)) {
                skippedRows.add(createSkippedRow(run, item, username));
            }
        }
        return skippedRows;
    }

    // Makes the "Could not open" row of a skipped file.
    private Submission createSkippedRow(GradingRun run, ImportItem item,
            String username) {
        Submission row = new Submission(username, run.getSection(),
                item.getPath());
        row.setStatus(SubmissionStatus.FAILED);
        row.setIngestFailure(ResultState.COULD_NOT_OPEN_PREFIX
                + item.getName() + ". " + item.getMessage());
        return row;
    }

    // Says whether the run graded a submission of a username.
    private boolean isInRun(GradingRun run, String username) {
        for (Submission submission : run.getSubmissions()) {
            if (submission.getUsername().equalsIgnoreCase(username)) {
                return true;
            }
        }
        return false;
    }

    // Returns a "No submission" row per student with no file, once read.
    private List<Submission> findMissingRows(GradingRun run) {
        if (run == missingRowsRun) {
            return missingRows;
        }
        List<Student> missing = getState().getMissingStudents(run);
        if (missing == null) {
            getWindow().findMissingStudents(run, () -> missingFound());
            return new ArrayList<>();
        }
        missingRows.clear();
        missingRowsRun = run;
        for (Student student : missing) {
            Submission skipped = findSkippedRowOf(student);
            if (skipped == null) {
                missingRows.add(createMissingRow(run, student));
            } else if (skipped.getStudent() == null) {
                skipped.setStudent(student);
            }
        }
        return missingRows;
    }

    // Makes the row of a student with no submission.
    private Submission createMissingRow(GradingRun run, Student student) {
        Submission row = new Submission(student.getUsername(),
                run.getSection(), null);
        row.setStudent(student);
        row.setIngestFailure(ResultState.NO_SUBMISSION_REASON);
        return row;
    }

    // Returns the skipped file row of a student, by username or file name.
    private Submission findSkippedRowOf(Student student) {
        String username = student.getUsername().toLowerCase();
        for (Submission row : skippedRows) {
            String shown = row.getUsername().toLowerCase();
            if (shown.equals(username)) {
                return row;
            } else if (isNameless(row) && shown.contains(username)) {
                return row;
            }
        }
        return null;
    }

    // Says whether a skipped file row has no username, only a file name.
    private boolean isNameless(Submission row) {
        return row.getOriginalPath() != null && row.getUsername().equals(
                String.valueOf(row.getOriginalPath().getFileName()));
    }

    // Shows the students with no submission once the score sheet is read.
    private void missingFound() {
        if (isOnScreen()) {
            onShow();
        }
    }

    // Builds the All, Needs a look, Not fully graded and No submission
    // buttons with their counts.
    private void createFilters() {
        ToggleGroup group = new ToggleGroup();
        ToggleButton[] buttons = {allButton, reviewButton, failedButton,
            missingButton};
        Label[] counts = {allCount, reviewCount, failedCount, missingCount};
        for (int index = 0; index < buttons.length; index++) {
            buttons[index].setToggleGroup(group);
            counts[index].getStyleClass().add("count");
            buttons[index].setGraphic(counts[index]);
            buttons[index].setContentDisplay(ContentDisplay.RIGHT);
        }
        allButton.setSelected(true);
        Parts.tip(allButton, "Every student in this run.");
        Parts.tip(reviewButton, "Students with a fix to approve, a flag to "
                + "look at, a total of 0, or code that did not compile or "
                + "timed out. Check them before you export.");
        Parts.tip(failedButton, "Students whose code did not compile, "
                + "timed out or could not be graded, so their marks may "
                + "be incomplete. Files that could not be opened show "
                + "under All.");
        allButton.getStyleClass().add("left-pill");
        reviewButton.getStyleClass().add("center-pill");
        failedButton.getStyleClass().add("center-pill");
        missingButton.getStyleClass().add("right-pill");
        group.selectedToggleProperty().addListener((observable, before,
                after) -> filterClicked(before, after));
    }

    /**
     * Follows a click on a filter button. Clicking the
     * button that is already on would turn every button off, so it
     * stays on.
     *
     * @param before the button that was on
     * @param after the button that is on now, or null for none
     */
    private void filterClicked(Toggle before, Toggle after) {
        if (after == null) {
            before.setSelected(true);
        }
        applyFilter();
    }

    // Puts the filter buttons side by side.
    private HBox createFilterBar() {
        HBox bar = new HBox(allButton, reviewButton, failedButton,
                missingButton);
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
        search.setAccessibleText("Search students");
        search.textProperty().addListener((observable, before, after)
                -> applyFilter());
    }

    // Builds the tick, student, total and status columns.
    private void createTable() {
        table.getStyleClass().add("results-table");
        table.setPlaceholder(placeholder);
        table.setFixedCellSize(ROW_HEIGHT);
        table.setColumnResizePolicy(
                TableView.CONSTRAINED_RESIZE_POLICY_ALL_COLUMNS);
        table.setRowFactory(tableView -> new ReviewRow(table));

        TableColumn<Submission, Submission> tick = new TableColumn<>();
        tickAllBox.setOnAction(event -> tickAll(tickAllBox.isSelected()));
        tickAllBox.setAccessibleText("Tick every student shown");
        tick.setGraphic(tickAllBox);
        Parts.tip(tickAllBox, "Tick every student shown, to approve their "
                + "fixes together or export only them.");
        tick.setSortable(false);
        fixWidth(tick, TICK_WIDTH);
        tick.setCellValueFactory(cell -> studentOf(cell));
        tick.setCellFactory(column -> new ReviewTickCell(this));

        TableColumn<Submission, Submission> student = studentColumn;
        student.setMinWidth(STUDENT_WIDTH);
        // The name column takes the spare width, so the marks, status and
        // arrow sit flush on the right
        student.setPrefWidth(STUDENT_WIDTH);
        student.setMaxWidth(STUDENT_MAX_WIDTH);
        student.setCellValueFactory(cell -> studentOf(cell));
        student.setCellFactory(column -> new ReviewStudentCell());
        student.setComparator(new StudentNameComparator());

        TableColumn<Submission, Double> total = totalColumn;
        fixMarkWidth(total, TOTAL_WIDTH);
        total.setCellValueFactory(cell -> totalOf(cell));
        total.setCellFactory(column -> new ReviewMarkCell(true));
        total.getStyleClass().add("centred");

        TableColumn<Submission, Submission> status = statusColumn;
        fixWidth(status, STATUS_WIDTH);
        status.setCellValueFactory(cell -> studentOf(cell));
        status.setCellFactory(column -> new ReviewStatusCell(getBackend()));
        status.setComparator(new StudentStateComparator(getBackend()));

        TableColumn<Submission, Submission> open = openColumn;
        fixWidth(open, OPEN_WIDTH);
        open.setSortable(false);
        open.setCellValueFactory(cell -> studentOf(cell));
        open.setCellFactory(column -> new ReviewOpenCell());

        table.getColumns().add(tick);
        table.getColumns().add(student);
        table.getColumns().add(total);
        table.getColumns().add(status);
        table.getColumns().add(open);

        SortedList<Submission> sorted = new SortedList<>(filtered);
        sorted.comparatorProperty().bind(table.comparatorProperty());
        table.setItems(sorted);
        table.getSelectionModel().selectedItemProperty().addListener(
                (observable, before, after) -> showStudent(after));

        // The box ends at the last row, or fills the space and scrolls.
        // Only an empty table keeps room for its message.
        NumberBinding fitted = Bindings.size(sorted).multiply(ROW_HEIGHT)
                .add(HEADER_HEIGHT + BORDERS);
        NumberBinding shown = Bindings.when(Bindings.isEmpty(sorted))
                .then(MIN_TABLE_HEIGHT).otherwise(fitted);
        tableFrame.maxHeightProperty().bind(shown);
        tableFrame.minHeightProperty().bind(Bindings.min(shown,
                MIN_TABLE_HEIGHT));
        table.setMinHeight(0);

        // The frame draws the rounded border, so nothing clips it; the
        // table inside is clipped a little smaller, so the header and
        // the last row have round corners too
        tableFrame.getChildren().add(table);
        tableFrame.getStyleClass().add("table-frame");
        Rectangle corners = new Rectangle();
        corners.setArcWidth(CORNER_ARC - 2);
        corners.setArcHeight(CORNER_ARC - 2);
        corners.widthProperty().bind(table.widthProperty());
        corners.heightProperty().bind(table.heightProperty());
        table.setClip(corners);
    }

    // Gives a column of students one width that never changes.
    private void fixWidth(TableColumn<Submission, Submission> column,
            double width) {
        column.setMinWidth(width);
        column.setPrefWidth(width);
        column.setMaxWidth(width);
        column.setResizable(false);
    }

    // Gives a column of marks one width that never changes.
    private void fixMarkWidth(TableColumn<Submission, Double> column,
            double width) {
        column.setMinWidth(width);
        column.setPrefWidth(width);
        column.setMaxWidth(width);
        column.setResizable(false);
    }

    /**
     * Returns the student of a row, as the value of a cell.
     *
     * @param cell the row and column of the cell
     * @return the student, wrapped so the table can read it
     */
    private ObservableValue<Submission> studentOf(
            CellDataFeatures<Submission, Submission> cell) {
        return new ReadOnlyObjectWrapper<>(cell.getValue());
    }

    /**
     * Returns the total of a row's student, as the value of a cell. A
     * student with no marks yet has none, so the cell shows a dash.
     *
     * @param cell the row and column of the cell
     * @return the total, or a wrapped null when there are no marks
     */
    private ObservableValue<Double> totalOf(
            CellDataFeatures<Submission, Double> cell) {
        Submission submission = cell.getValue();
        if (submission.getQuestionResults().isEmpty()) {
            return new ReadOnlyObjectWrapper<>(null);
        }
        return new ReadOnlyObjectWrapper<>(submission.getTotalScore());
    }

    /**
     * Returns a row's student's mark for one question, as the value of a
     * cell.
     *
     * @param cell the row and column of the cell
     * @param questionId the question of the column
     * @return the mark, or a wrapped null when there is none
     */
    private ObservableValue<Double> questionMarkOf(
            CellDataFeatures<Submission, Double> cell, String questionId) {
        return new ReadOnlyObjectWrapper<>(scoreOf(cell.getValue(),
                questionId));
    }

    // Says whether the question columns are those of an assessment.
    private boolean showsQuestionsOf(Assessment assessment) {
        List<Question> questions = assessment.getQuestions();
        if (questions.size() != questionColumns.size()) {
            return false;
        }
        for (int index = 0; index < questions.size(); index++) {
            Object id = questionColumns.get(index).getUserData();
            if (!questions.get(index).getId().equals(id)) {
                return false;
            }
        }
        return true;
    }

    // Shows a column's name, and under it the most marks when given, in
    // a box with a tip, because a column header has no tip of its own.
    // The labels have their own style class: the header's label padding
    // would otherwise squeeze a narrow column's name to "...".
    private void header(TableColumn<Submission, ?> column, String name,
            String max, String tip) {
        Label nameLabel = new Label(name);
        nameLabel.getStyleClass().add("head-name");
        VBox box = new VBox(nameLabel);
        box.getStyleClass().add("head-box");
        if (max != null) {
            Label maxLabel = new Label(max);
            maxLabel.getStyleClass().add("head-max");
            box.getChildren().add(maxLabel);
        }
        box.setAlignment(Pos.CENTER);
        box.setMinWidth(VBox.USE_PREF_SIZE);
        Parts.tipNode(box, tip);
        column.setText("");
        column.setGraphic(box);
    }

    // Adds one column per question of the run's assessment, between
    // Student and Total.
    private void addQuestionColumns(Assessment assessment) {
        if (assessment == null) {
            return;
        }
        int index = FIRST_QUESTION_COLUMN;
        for (Question question : assessment.getQuestions()) {
            TableColumn<Submission, Double> column = new TableColumn<>();
            column.setUserData(question.getId());
            header(column, question.getId(), "/ " + Parts.mark(
                    question.getMaxScore()), question.getId() + ": "
                    + question.getFileName() + " in the "
                    + question.getFolderName() + " folder, out of "
                    + Parts.mark(question.getMaxScore()) + ".");
            fixMarkWidth(column, QUESTION_WIDTH);
            String id = question.getId();
            column.setCellValueFactory(cell -> questionMarkOf(cell, id));
            column.setCellFactory(tableColumn -> new ReviewMarkCell(false));
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
        // The panel already shows the student the arrow would open
        openColumn.setVisible(!panel.isVisible());
        // The name column always takes the spare width, so the marks and
        // status stay flush on the right; its minimum keeps names readable
        // and the status column's fixed width keeps every pill whole
        studentColumn.setMaxWidth(STUDENT_MAX_WIDTH);
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
        StatusCounts counted = StatusCounts.of(rows, getBackend());
        allCount.setText(String.valueOf(counted.getTotal()));
        reviewCount.setText(String.valueOf(counted.getNeedsLook()));
        failedCount.setText(String.valueOf(counted.getNotFullyGraded()));
        missingCount.setText(String.valueOf(counted.count(
                ResultState.NO_SUBMISSION)));

        // The same figures as Export and Statistics: graded students only
        String average = "";
        MarkStats stats = MarkStats.of(rows,
                run.getAssessment().getMaxTotal(),
                MarkStats.parsePassPercent(getBackend().getSetting(
                ConfigKeys.PASS_MARK_PERCENT)));
        if (!stats.isEmpty()) {
            average = " · average " + Parts.mark(Math.round(
                    stats.getMean() * ONE_DECIMAL) / ONE_DECIMAL)
                    + " out of " + Parts.mark(stats.getMaxTotal())
                    + ", of " + stats.getCount() + " graded";
        }
        subtitle.setText(run.getSection().getModuleCode() + " · "
                + run.getSection().getSectionCode() + " · "
                + run.getAssessment().getName() + average);

        fitStatusColumn();
        refreshBanner(run);
        refreshSharedBanner(run);
        applyFilter();
        table.refresh();
        refreshActions();
    }

    // Makes the status column as wide as its widest pill, so the marks
    // and statuses end at the table's right edge with no gap after them.
    private void fitStatusColumn() {
        Font font = Font.font(PILL_FONT, FontWeight.BOLD, PILL_FONT_SIZE);
        double widest = 0;
        for (Submission submission : rows) {
            Text text = new Text(Students.pill(submission, getBackend())
                    .getText());
            text.setFont(font);
            widest = Math.max(widest, text.getLayoutBounds().getWidth());
        }
        double width = Math.max(STATUS_MIN_WIDTH, Math.min(STATUS_WIDTH,
                Math.ceil(widest + PILL_EXTRA)));
        statusColumn.setMinWidth(width);
        statusColumn.setPrefWidth(width);
        statusColumn.setMaxWidth(width);
    }

    // Counts the students with something still to check.
    private int countNeedingReview() {
        return StatusCounts.of(rows, getBackend()).getNeedsLook();
    }

    // Shows the retry note when some students could not be graded.
    private void refreshBanner(GradingRun run) {
        banner.getChildren().clear();
        int couldNot = 0;
        for (Submission submission : rows) {
            // A file rejected on loading, such as one with no username,
            // or a zip that could not be unpacked, cannot be retried
            if (Students.state(submission, getBackend())
                    == ResultState.COULD_NOT_GRADE
                    && submission.getIngestFailure() == null
                    && !ResultState.isRejectedUpload(submission)) {
                couldNot++;
            }
        }
        boolean show = couldNot > 0
                && getBackend().supports(Capability.RETRY_FAILED);
        banner.setVisible(show);
        banner.setManaged(show);
        if (show) {
            Label text = Parts.strong(Parts.plural(couldNot, "submission")
                    + " did not finish.");
            text.setWrapText(true);
            text.setMinWidth(0);
            HBox.setHgrow(text, Priority.ALWAYS);
            banner.getChildren().addAll(Parts.pill("Could not grade",
                    Tone.BAD), text, retryAction(run, couldNot));
        }
    }

    // Shows each class file problem all graded students share, once.
    private void refreshSharedBanner(GradingRun run) {
        sharedBanner.getChildren().clear();
        List<Anomaly> shared = findSharedProblems(run);
        panel.setSharedProblems(shared);
        for (Anomaly anomaly : shared) {
            Label text = Parts.strong(anomaly.getMessage());
            text.setWrapText(true);
            text.setMinWidth(0);
            HBox.setHgrow(text, Priority.ALWAYS);
            HBox note = Parts.row(Parts.GAP, new Node[] {Parts.pill(
                    "Every graded student", Tone.WARN), text});
            if (isSharedOpen(anomaly)
                    && getBackend().supports(Capability.FLAG_REVIEW)) {
                note.getChildren().add(Parts.link("Mark as checked",
                        () -> markSharedChecked(anomaly)));
            }
            sharedBanner.getChildren().add(note);
        }
        boolean show = !shared.isEmpty();
        sharedBanner.setVisible(show);
        sharedBanner.setManaged(show);
    }

    // Finds the class file problems that every graded student shares.
    private List<Anomaly> findSharedProblems(GradingRun run) {
        List<Anomaly> shared = new ArrayList<>();
        List<Submission> graded = new ArrayList<>();
        for (Submission submission : run.getSubmissions()) {
            if (ResultState.hasTotal(submission)) {
                graded.add(submission);
            }
        }
        if (graded.size() < MIN_SHARED_STUDENTS) {
            return shared;
        }

        for (Anomaly anomaly : graded.get(0).getAnomalies()) {
            if (anomaly.getType() == AnomalyType.INTERNAL_ERROR
                    && isOnEvery(anomaly, graded)) {
                shared.add(anomaly);
            }
        }
        return shared;
    }

    // Says whether any student's copy of a shared problem is still open.
    private boolean isSharedOpen(Anomaly shared) {
        for (Anomaly anomaly : findSharedCopies(shared)) {
            if (getBackend().needsReview(anomaly)) {
                return true;
            }
        }
        return false;
    }

    // Marks every student's copy of a shared problem as checked.
    private void markSharedChecked(Anomaly shared) {
        int marked = 0;
        for (Anomaly anomaly : findSharedCopies(shared)) {
            if (getBackend().setDecision(anomaly,
                    ReviewDecision.REVIEWED).isDone()) {
                marked++;
            }
        }
        getWindow().notify("Marked the problem as checked for "
                + Parts.plural(marked, "student") + ". No mark changed.");
        panel.refresh();
        refreshStatuses();
    }

    // Lists each student's copy of a shared problem.
    private List<Anomaly> findSharedCopies(Anomaly shared) {
        List<Anomaly> copies = new ArrayList<>();
        for (Submission submission : rows) {
            for (Anomaly anomaly : submission.getAnomalies()) {
                if (anomaly.getType() == shared.getType()
                        && anomaly.getMessage().equals(
                                shared.getMessage())) {
                    copies.add(anomaly);
                }
            }
        }
        return copies;
    }

    // Says whether every student has a problem of the same type and words.
    private boolean isOnEvery(Anomaly anomaly, List<Submission> students) {
        for (Submission submission : students) {
            if (!hasSameProblem(submission, anomaly)) {
                return false;
            }
        }
        return true;
    }

    // Says whether a student has a problem of the same type and words.
    private boolean hasSameProblem(Submission submission, Anomaly anomaly) {
        for (Anomaly other : submission.getAnomalies()) {
            if (other.getType() == anomaly.getType()
                    && other.getMessage().equals(anomaly.getMessage())) {
                return true;
            }
        }
        return false;
    }

    // Returns the Retry link, or why this run cannot be retried.
    private Node retryAction(GradingRun run, int couldNot) {
        String them = "them";
        if (couldNot == 1) {
            them = "it";
        }
        if (getBackend().canRetryFailed(run)) {
            return Parts.link("Retry " + them,
                    () -> getWindow().retryRun(run));
        }
        Label reason = Parts.muted("Only the last run graded since "
                + "GradeZZY opened can be retried, so grade " + them
                + " again from Set up.");
        reason.setWrapText(true);
        reason.setMinWidth(0);
        return reason;
    }

    // ----------------------------------------------------------- search

    // Applies the filter buttons and the search text.
    private void applyFilter() {
        String text = search.getText().trim().toLowerCase();
        boolean onlyReview = reviewButton.isSelected();
        boolean onlyFailed = failedButton.isSelected();
        boolean onlyMissing = missingButton.isSelected();
        filtered.setPredicate(submission -> passesFilter(submission, text,
                onlyReview, onlyFailed, onlyMissing));
        placeholder.setText(emptyNote(text, onlyReview, onlyFailed,
                onlyMissing));
    }

    /**
     * Says whether a student passes the filter buttons and the search.
     *
     * @param submission the student
     * @param text the search text in lower case, or an empty string
     * @param onlyReview true when only students who need a look are shown
     * @param onlyFailed true when only students not fully graded are
     *        shown
     * @param onlyMissing true when only students with no submission are
     *        shown
     * @return true when the student is shown
     */
    private boolean passesFilter(Submission submission, String text,
            boolean onlyReview, boolean onlyFailed, boolean onlyMissing) {
        if (onlyMissing && !ResultState.isNoSubmission(submission)) {
            return false;
        }
        if (onlyReview && !StatusCounts.needsLook(submission,
                getBackend())) {
            return false;
        }
        // A file that could not be opened shows only under All
        if (onlyFailed && (ResultState.isSkippedFile(submission)
                || !StatusCounts.isNotFullyGraded(Students.state(
                submission, getBackend())))) {
            return false;
        }
        if (text.isEmpty()) {
            return true;
        }
        return Students.name(submission).toLowerCase().contains(text)
                || submission.getUsername().toLowerCase().contains(text);
    }

    // Returns the note shown when no student passes the filter.
    private String emptyNote(String text, boolean onlyReview,
            boolean onlyFailed, boolean onlyMissing) {
        if (!text.isEmpty()) {
            return "No student matches \"" + search.getText().trim()
                    + "\".";
        } else if (onlyReview) {
            return "Every student is checked.";
        } else if (onlyFailed) {
            return "Every student was fully graded.";
        } else if (onlyMissing) {
            return "Every student on the score sheet has a submission.";
        }
        return "No students in this run.";
    }

    // ------------------------------------------------------------ panel

    // Opens a student in the panel, or says why a made-up row has none.
    private void showStudent(Submission submission) {
        if (submission == null) {
            return;
        }
        if (ResultState.isMadeOnReview(submission)) {
            panel.show(null);
            panel.setVisible(false);
            panel.setManaged(false);
            if (ResultState.isSkippedFile(submission)) {
                getWindow().confirm(Students.SKIPPED_WORDS,
                        describeMadeRow(submission) + " Choose a new copy "
                        + "of the zip on Set up, then grade it.",
                        "Replace file", () -> getWindow().show(
                        Step.SET_UP));
            } else {
                getWindow().notify(describeMadeRow(submission));
            }
            return;
        }
        panel.show(submission);
        panel.setVisible(true);
        panel.setManaged(true);
    }

    // Says why a row made on Review has no marks.
    private String describeMadeRow(Submission submission) {
        if (ResultState.isSkippedFile(submission)) {
            return submission.getIngestFailure();
        }
        return submission.getUsername() + " is on the score sheet but has "
                + "no submission in this run.";
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

    /**
     * Says whether a student is ticked for export.
     *
     * @param submission the student
     * @return true when ticked
     */
    public boolean isTickedForExport(Submission submission) {
        return getState().getSelected().contains(submission);
    }

    /**
     * Ticks or unticks one student for export. Unticking a student also
     * unticks the box that ticks every student.
     *
     * @param submission the student
     * @param ticked true to tick, false to untick
     */
    public void setTickedForExport(Submission submission, boolean ticked) {
        List<Submission> selected = getState().getSelected();
        if (ticked && !selected.contains(submission)) {
            selected.add(submission);
        } else if (!ticked) {
            selected.remove(submission);
            tickAllBox.setSelected(false);
        }
        refreshActions();
    }

    // Ticks or unticks every student shown.
    private void tickAll(boolean ticked) {
        List<Submission> selected = getState().getSelected();
        selected.clear();
        if (!ticked) {
            table.refresh();
            refreshActions();
            return;
        }
        // A row made on Review has no marks to export
        for (Submission submission : filtered) {
            if (!ResultState.isMadeOnReview(submission)) {
                selected.add(submission);
            }
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
        refreshGuide();
        ActionBar actions = getActions();
        actions.reset();
        actions.setBack(() -> getWindow().show(Step.GRADE));
        actions.setStatus(statusText());
        List<RepairItem> open = openRepairs();
        if (!open.isEmpty()) {
            actions.addSecondary(Parts.secondary("Keep all "
                    + Parts.plural(open.size(), "fix", "fixes"),
                    () -> askApproveAll(open)));
        }
        List<Anomaly> flags = openFlags();
        if (!flags.isEmpty()) {
            actions.addSecondary(Parts.secondary("Mark all "
                    + flags.size() + " as checked",
                    () -> askCheckAll(flags)));
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
            text = "1 student still needs a look.";
        } else {
            text = needReview + " students still need a look.";
        }
        int ticked = getState().getSelected().size();
        if (ticked > 0) {
            text = text + " " + Parts.plural(ticked, "student")
                    + " ticked.";
        }
        return text;
    }

    // Under the tools: how to open a student and what a tick does, or,
    // once students are ticked, how many will be exported.
    private void refreshGuide() {
        guide.getChildren().clear();
        guide.getStyleClass().remove("ticked");
        int ticked = getState().getSelected().size();
        if (ticked == 0) {
            Label text = Parts.muted("Click a student to see each "
                    + "question's tests, approve fixes and check flags. "
                    + "Tick students to act on several at once. You choose "
                    + "who to export on Export.");
            text.getStyleClass().add("small-text");
            text.setWrapText(true);
            text.setMinWidth(0);
            text.setMaxWidth(Double.MAX_VALUE);
            text.setMinHeight(Region.USE_PREF_SIZE);
            HBox.setHgrow(text, Priority.ALWAYS);
            guide.getChildren().addAll(Parts.icon(Parts.HELP,
                    HINT_ICON_SIZE), text);
            return;
        }
        guide.getStyleClass().add("ticked");
        Label text = Parts.strong(Parts.plural(ticked, "student")
                + " ticked");
        Label rest = Parts.muted("Approve their fixes together, or export "
                + "only them.");
        rest.getStyleClass().add("small-text");
        rest.setMinWidth(0);
        guide.getChildren().addAll(text, rest, Parts.grow());
        if (getBackend().supports(Capability.EXPORT_SCOPE)) {
            guide.getChildren().add(Parts.link("Export only these",
                    () -> exportTicked()));
        }
        guide.getChildren().add(Parts.link("Clear ticks",
                () -> clearTicks()));
    }

    // Chooses the ticked students on Export and goes there.
    private void exportTicked() {
        List<Submission> picked = getState().getExportPicked();
        picked.clear();
        for (Submission submission : getState().getSelected()) {
            if (!ResultState.isMadeOnReview(submission)) {
                picked.add(submission);
            }
        }
        getState().setExportOnlyPicked(!picked.isEmpty());
        continueToExport();
    }

    // Unticks every student.
    private void clearTicks() {
        tickAllBox.setSelected(false);
        tickAll(false);
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

    // Asks before approving every waiting fix at once.
    private void askApproveAll(List<RepairItem> open) {
        String whose = "every student";
        if (!getState().getSelected().isEmpty()) {
            whose = "the ticked students";
        }
        VBox content = new VBox(Parts.GAP_SMALL);
        Label about = Parts.muted("The fixed files of " + whose + " are "
                + "kept and their marks stay as they are now:");
        about.setWrapText(true);
        content.getChildren().add(about);
        int listed = 0;
        for (RepairItem repair : open) {
            if (listed == MAX_LISTED_FIXES) {
                content.getChildren().add(Parts.muted("and "
                        + (open.size() - listed) + " more"));
                break;
            }
            Anomaly anomaly = repair.getAnomaly();
            Label line = new Label("• " + anomaly.getUsername() + ": "
                    + Problems.fixedTitle(anomaly.getType()));
            line.setWrapText(true);
            content.getChildren().add(line);
            listed++;
        }
        getWindow().confirm("Keep all " + Parts.plural(open.size(),
                "fix", "fixes") + "?", content, "Keep all",
                () -> approveAll(open));
    }

    // Returns the open flags that are not fixes, for the ticked students
    // or all, that may be checked together: compile errors, timeouts,
    // unknown students and the like are checked one at a time.
    private List<Anomaly> openFlags() {
        List<Anomaly> open = new ArrayList<>();
        if (!getBackend().supports(Capability.FLAG_REVIEW)) {
            return open;
        }
        List<Submission> scope = getState().getSelected();
        for (Submission submission : rows) {
            if (!scope.isEmpty() && !scope.contains(submission)) {
                continue;
            }
            for (Anomaly anomaly : submission.getAnomalies()) {
                if (anomaly.getFixStatus() != FixStatus.APPLIED
                        && ReviewRules.canCheckAll(anomaly.getType())
                        && getBackend().needsReview(anomaly)) {
                    open.add(anomaly);
                }
            }
        }
        return open;
    }

    // Asks before marking every open flag and problem as checked,
    // naming each one.
    private void askCheckAll(List<Anomaly> flags) {
        String whose = "every student";
        if (!getState().getSelected().isEmpty()) {
            whose = "the ticked students";
        }
        VBox content = new VBox(Parts.GAP_SMALL);
        Label about = Parts.muted("These flags of " + whose
                + " are marked as checked. No mark changes, and you can "
                + "undo it. Compile errors, timeouts and students not on "
                + "the score sheet are left for you to check one at a "
                + "time:");
        about.setWrapText(true);
        content.getChildren().add(about);
        int listed = 0;
        for (Anomaly anomaly : flags) {
            if (listed == MAX_LISTED_FIXES) {
                content.getChildren().add(Parts.muted("and "
                        + (flags.size() - listed) + " more"));
                break;
            }
            Label line = new Label("• " + anomaly.getUsername() + ": "
                    + Problems.title(anomaly.getType()));
            line.setWrapText(true);
            content.getChildren().add(line);
            listed++;
        }
        getWindow().confirm("Mark all " + flags.size() + " as checked?",
                content, "Mark all as checked", () -> checkAll(flags));
    }

    // Marks every open flag and problem as checked, and offers Undo.
    private void checkAll(List<Anomaly> flags) {
        List<Anomaly> done = new ArrayList<>();
        for (Anomaly anomaly : flags) {
            if (getBackend().setDecision(anomaly,
                    ReviewDecision.REVIEWED).isDone()) {
                done.add(anomaly);
            }
        }
        panel.refresh();
        refreshStatuses();
        getWindow().notify("Marked " + Parts.plural(done.size(), "item")
                + " as checked.", "Undo", () -> uncheckAll(done));
    }

    // Puts flags marked as checked together back to be checked.
    // Puts back to check the flags Mark all checked. A flag decided
    // otherwise since, such as marked as looking fine, keeps that.
    private void uncheckAll(List<Anomaly> flags) {
        int reopened = 0;
        for (Anomaly anomaly : flags) {
            if (getBackend().getDecision(anomaly) == ReviewDecision.REVIEWED
                    && getBackend().setDecision(anomaly,
                    ReviewDecision.OPEN).isDone()) {
                reopened++;
            }
        }
        getWindow().notify("Put " + Parts.plural(reopened, "item")
                + " back to check.");
        panel.refresh();
        refreshStatuses();
    }

    // Approves every waiting fix.
    private void approveAll(List<RepairItem> open) {
        List<RepairItem> kept = new ArrayList<>();
        for (RepairItem repair : open) {
            if (getBackend().approveRepair(repair).isDone()) {
                kept.add(repair);
            }
        }
        int approved = kept.size();
        if (getBackend().supports(Capability.REPAIR_DECISION_UNDO)) {
            getWindow().notify("Kept " + Parts.plural(approved, "fix",
                    "fixes") + ".", "Undo", () -> unkeepAll(kept));
        } else {
            getWindow().notify("Kept " + Parts.plural(approved, "fix",
                    "fixes") + ".");
        }
        panel.refresh();
        refreshStatuses();
    }

    // Takes back fixes kept together, so they wait for review again.
    private void unkeepAll(List<RepairItem> kept) {
        for (RepairItem repair : kept) {
            getBackend().undoRepairDecision(repair);
        }
        getWindow().notify("Put " + Parts.plural(kept.size(), "fix",
                "fixes") + " back to review.");
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
}
