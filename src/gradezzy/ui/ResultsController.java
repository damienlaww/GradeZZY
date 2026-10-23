/*
 * File: ResultsController.java
 *
 * Purpose: Controller of the results screen.
 *
 * Where it fits: Shows each student's scores and starts exports. Uses
 * ResultsColumns and StudentInspector; keeps search and selection in UiState.
 *
 * Note on untaught Java: exports and code search use a javafx.concurrent.Task
 * on a background thread, so slow file work never freezes the window.
 */
package gradezzy.ui;

import java.util.ArrayList;
import java.util.Collection;
import java.util.List;

import javafx.collections.FXCollections;
import javafx.collections.ListChangeListener;
import javafx.concurrent.Task;
import javafx.fxml.FXML;
import javafx.scene.control.Button;
import javafx.scene.control.ComboBox;
import javafx.scene.control.Label;
import javafx.scene.control.SelectionMode;
import javafx.scene.control.TableRow;
import javafx.scene.control.TableView;
import javafx.scene.control.TextField;
import javafx.scene.control.ToggleButton;
import javafx.scene.control.ToggleGroup;
import javafx.scene.input.KeyCode;
import javafx.scene.layout.HBox;
import javafx.scene.layout.VBox;
import javafx.util.StringConverter;

import org.controlsfx.control.textfield.TextFields;

import gradezzy.model.GradingRun;
import gradezzy.model.Submission;
import gradezzy.ui.backend.ActionResult;
import gradezzy.ui.backend.ResultState;
import gradezzy.ui.component.EmptyState;
import gradezzy.ui.component.Icon;
import gradezzy.ui.component.Labels;
import gradezzy.ui.component.Responsive;
import gradezzy.ui.component.StatusBadge;
import gradezzy.ui.component.Tone;
import gradezzy.ui.view.ResultsColumns;
import gradezzy.ui.view.StudentInspector;

/**
 * Controller of the results screen.
 */
public class ResultsController extends BaseController {

    /** The format name of the score sheet export. */
    public static final String CSV_FORMAT = "csv";

    /** The format name of the JSON export. */
    public static final String JSON_FORMAT = "json";

    // The most search suggestions shown
    private static final int MAX_SUGGESTIONS = 8;

    // The words shown for "every state" in the status filter
    private static final String ALL_STATES = "All states";

    @FXML
    private Label statusLabel;

    @FXML
    private Button jsonButton;

    @FXML
    private Button csvButton;

    @FXML
    private TextField searchField;

    @FXML
    private ToggleButton namesScope;

    @FXML
    private ToggleButton codeScope;

    @FXML
    private Label stateLabel;

    @FXML
    private ComboBox<ResultState> stateFilter;

    @FXML
    private Label sortLabel;

    @FXML
    private ComboBox<SortOrder> sortBox;

    @FXML
    private Button selectAllButton;

    @FXML
    private Button clearSelectionButton;

    @FXML
    private ToggleButton inspectorToggle;

    @FXML
    private TableView<Submission> table;

    @FXML
    private HBox legend;

    @FXML
    private VBox inspector;

    @FXML
    private Label nameLabel;

    @FXML
    private Label usernameLabel;

    @FXML
    private Label totalLabel;

    @FXML
    private Label maxLabel;

    @FXML
    private HBox stateHolder;

    @FXML
    private VBox questionRows;

    @FXML
    private Label noteLabel;

    @FXML
    private Button openButton;

    // The students shown, after search, filter and sort
    private final List<Submission> shown = new ArrayList<>();

    // Fills the inspector, once the backend is known
    private StudentInspector studentInspector = null;

    // Whether the table selection is being restored by code
    private boolean restoring = false;

    // The number of exports running in the background
    private int exportsRunning = 0;

    // The code search running in the background, or null
    private Task<List<Submission>> codeSearch = null;

    // The status note about the running export, or ""
    private String exportNote = "";

    // The status note about the running code search, or ""
    private String searchNote = "";

    /**
     * Creates a new results controller.
     */
    public ResultsController() {
        // nothing to set up
    }

    /**
     * Called by JavaFX after the FXML file is loaded.
     */
    @FXML
    public void initialize() {
        stateLabel.setLabelFor(stateFilter);
        sortLabel.setLabelFor(sortBox);
        inspectorToggle.setGraphic(Icon.INSPECTOR.create());
        searchField.setPromptText("Search students");

        ToggleGroup scope = new ToggleGroup();
        namesScope.setToggleGroup(scope);
        codeScope.setToggleGroup(scope);
        namesScope.setSelected(true);
        namesScope.setOnAction(event -> scopeChanged());
        codeScope.setOnAction(event -> scopeChanged());

        sortBox.setItems(FXCollections.observableArrayList(
                SortOrder.NAME_ASCENDING, SortOrder.NAME_DESCENDING,
                SortOrder.TOTAL_DESCENDING, SortOrder.TOTAL_ASCENDING));
        List<ResultState> states = new ArrayList<>();
        states.add(null);
        for (ResultState state : ResultState.values()) {
            states.add(state);
        }
        stateFilter.setItems(FXCollections.observableArrayList(states));
        stateFilter.setConverter(new StateConverter());

        table.getSelectionModel().setSelectionMode(SelectionMode.MULTIPLE);
        table.setColumnResizePolicy(
                TableView.CONSTRAINED_RESIZE_POLICY_FLEX_LAST_COLUMN);
        table.getSelectionModel().getSelectedItems().addListener(
                (ListChangeListener<Submission>) change
                -> selectionChanged());
        table.getSelectionModel().selectedItemProperty().addListener(
                (observable, oldValue, newValue) -> focusChanged(newValue));
        table.setRowFactory(view -> createRow());
        table.setOnKeyPressed(event -> {
            if (event.getCode() == KeyCode.ENTER) {
                handleOpenStudent();
            }
        });
        buildLegend();
    }

    /**
     * Sets up the parts that depend on the backend and the state.
     */
    @Override
    public void onAttach() {
        new ResultsColumns(getBackend()).addTo(table,
                getBackend().getAssessment());
        studentInspector = new StudentInspector(nameLabel, usernameLabel,
                totalLabel, maxLabel, stateHolder, questionRows, noteLabel,
                openButton, getBackend());
        Responsive.bindInspector(inspector, inspectorToggle,
                getState().compactProperty(),
                getState().inspectorRequestedProperty());

        TextFields.bindAutoCompletion(searchField,
                request -> suggestions(request.getUserText()))
                .setOnAutoCompleted(event -> handleSearch());
        searchField.textProperty().addListener(
                (observable, oldValue, newValue) -> {
                    if (!restoring) {
                        handleSearch();
                    }
                });
        stateFilter.valueProperty().addListener(
                (observable, oldValue, newValue) -> {
                    if (!restoring) {
                        getState().setResultsFilter(newValue);
                        refresh();
                    }
                });
        sortBox.valueProperty().addListener(
                (observable, oldValue, newValue) -> {
                    if (!restoring && newValue != null) {
                        getState().setResultsSort(newValue);
                        table.getSortOrder().clear();
                        refresh();
                    }
                });
    }

    /**
     * Restores the search, filter, sort and selection, then reloads the rows.
     */
    @Override
    public void onShow() {
        restoring = true;
        searchField.setText(getState().getResultsQuery());
        codeScope.setSelected(getState().isResultsCodeSearch());
        namesScope.setSelected(!getState().isResultsCodeSearch());
        stateFilter.setValue(getState().getResultsFilter());
        sortBox.setValue(getState().getResultsSort());
        restoring = false;
        refresh();
    }

    /**
     * Moves keyboard focus to the search field.
     *
     * @return true, as this screen has a search field
     */
    @Override
    public boolean focusSearch() {
        searchField.requestFocus();
        searchField.selectAll();
        return true;
    }

    /**
     * Exports the score sheet for the selected or shown students.
     */
    @FXML
    public void handleExportCsv() {
        export(CSV_FORMAT, "Score sheet preview");
    }

    /**
     * Exports the results as JSON for the selected or shown students.
     */
    @FXML
    public void handleExportJson() {
        export(JSON_FORMAT, "JSON preview");
    }

    /**
     * Applies the search text.
     */
    @FXML
    public void handleSearch() {
        getState().setResultsQuery(searchField.getText());
        refresh();
    }

    /**
     * Opens the evidence of the selected student.
     */
    @FXML
    public void handleOpenStudent() {
        Submission selected = table.getSelectionModel().getSelectedItem();
        if (selected == null) {
            showError("Select a student first.");
            return;
        }
        getState().setSelectedUsername(selected.getUsername());
        getState().setEvidenceTab("");
        getState().setEvidenceQuestionId("");
        getShell().navigate(Screen.STUDENT_REPORT);
    }

    /**
     * Selects every student shown.
     */
    @FXML
    public void handleSelectAll() {
        table.getSelectionModel().selectAll();
    }

    /**
     * Clears the selection.
     */
    @FXML
    public void handleClearSelection() {
        table.getSelectionModel().clearSelection();
        getState().getSelectedResults().clear();
        refreshStatus();
    }

    /**
     * Clears the search and the status filter.
     */
    public void clearFilters() {
        getState().setResultsQuery("");
        getState().setResultsFilter(null);
        onShow();
    }

    /**
     * Returns the students shown. Used by the self-test.
     *
     * @return the students in table order
     */
    public List<Submission> getShownSubmissions() {
        return new ArrayList<>(shown);
    }

    /**
     * Returns the selected students, or all shown ones if none is selected.
     *
     * @return the export scope
     */
    public List<Submission> getExportScope() {
        List<Submission> selected = new ArrayList<>(
                table.getSelectionModel().getSelectedItems());
        if (selected.isEmpty()) {
            return new ArrayList<>(shown);
        }
        return selected;
    }

    /**
     * Selects students by username. Used by the self-test.
     *
     * @param usernames the students to select
     */
    public void selectUsernames(Collection<String> usernames) {
        getState().getSelectedResults().clear();
        getState().getSelectedResults().addAll(usernames);
        restoreSelection();
    }

    // Exports the scope in the background, with the buttons disabled.
    private void export(String format, String previewTitle) {
        GradingRun run = currentRun();
        List<Submission> scope = getExportScope();
        Task<ActionResult> task = new Task<>() {

            /**
             * Asks the backend for the export, on a background thread.
             *
             * @return what happened
             */
            @Override
            protected ActionResult call() {
                return getBackend().export(run, scope, format);
            }
        };
        task.setOnSucceeded(event -> finishExport(task.getValue(),
                previewTitle));
        task.setOnFailed(event -> exportStopped(task.getException()));
        exportsRunning++;
        exportNote = "Exporting " + format.toUpperCase() + " for "
                + scope.size() + " student" + Labels.plural(scope.size())
                + ". ";
        refreshExportButtons();
        refreshStatus();
        runInBackground(task, "gradezzy-export");
    }

    // Shows how an export ended: the preview, or the message.
    private void finishExport(ActionResult result, String previewTitle) {
        endExport();
        if (result.isDone() && !result.getPreview().isEmpty()) {
            getBackend().recordAudit("Export previewed",
                    result.getMessage());
            getShell().showPreview(previewTitle, result.getMessage(),
                    result.getPreview());
            return;
        }
        showResult(result, "Results exported");
    }

    // Reports an export that ended with an exception.
    private void exportStopped(Throwable problem) {
        endExport();
        showError("The export stopped unexpectedly (" + describe(problem)
                + "). Try again, or check the console for details.");
    }

    // Makes the export buttons available again after an export.
    private void endExport() {
        exportsRunning--;
        if (exportsRunning <= 0) {
            exportsRunning = 0;
            exportNote = "";
        }
        refreshExportButtons();
        refreshStatus();
    }

    // Searches the source code in the background, then shows the matches.
    private void startCodeSearch(GradingRun run, String query) {
        Task<List<Submission>> task = new Task<>() {

            /**
             * Asks the backend for the matches, on a background thread.
             *
             * @return the matching students
             */
            @Override
            protected List<Submission> call() {
                return getBackend().searchCode(run, query);
            }
        };
        task.setOnSucceeded(event -> finishCodeSearch(task, run));
        task.setOnFailed(event -> codeSearchStopped(task));
        codeSearch = task;
        searchNote = "Searching the source code for \"" + query + "\". ";
        refreshStatus();
        runInBackground(task, "gradezzy-code-search");
    }

    // Shows the code search matches, unless it was cancelled or replaced.
    private void finishCodeSearch(Task<List<Submission>> task,
            GradingRun run) {
        if (task != codeSearch) {
            return;
        }
        codeSearch = null;
        searchNote = "";
        showMatches(run, task.getValue());
    }

    // Reports a code search that ended with an exception.
    private void codeSearchStopped(Task<List<Submission>> task) {
        if (task != codeSearch) {
            return;
        }
        codeSearch = null;
        searchNote = "";
        refreshStatus();
        showError("The code search stopped unexpectedly ("
                + describe(task.getException()) + "). Try again, or "
                + "search by name instead.");
    }

    // Stops waiting for a code search that is no longer wanted.
    private void cancelCodeSearch() {
        if (codeSearch != null) {
            codeSearch.cancel();
            codeSearch = null;
            searchNote = "";
        }
    }

    // Returns the message of an exception, or a plain fallback.
    private static String describe(Throwable problem) {
        if (problem == null || problem.getMessage() == null) {
            return "no details were given";
        }
        return problem.getMessage();
    }

    // Starts a task on a background thread that never keeps the app open.
    private static void runInBackground(Task<?> task, String name) {
        Thread worker = new Thread(task, name);
        worker.setDaemon(true);
        worker.start();
    }

    // Returns the run of the section in the context bar.
    private GradingRun currentRun() {
        return getBackend().getRun(getState().getSection());
    }

    // Reacts to the Names or Code choice.
    private void scopeChanged() {
        boolean code = codeScope.isSelected();
        if (!code && !namesScope.isSelected()) {
            namesScope.setSelected(true);
        }
        getState().setResultsCodeSearch(code);
        if (code) {
            searchField.setPromptText("Search inside source code");
        } else {
            searchField.setPromptText("Search students");
        }
        refresh();
    }

    // Reloads the rows, or starts a code search in the background.
    private void refresh() {
        GradingRun run = currentRun();
        String query = getState().getResultsQuery();
        cancelCodeSearch();
        if (run != null && getState().isResultsCodeSearch()
                && !query.isEmpty()) {
            startCodeSearch(run, query);
            return;
        }

        List<Submission> matches = new ArrayList<>();
        if (run != null) {
            matches = getBackend().search(run, query);
        }
        showMatches(run, matches);
    }

    // Filters and sorts the matches, shows them and restores the selection.
    private void showMatches(GradingRun run, List<Submission> matches) {
        shown.clear();
        if (run != null) {
            ResultState wanted = getState().getResultsFilter();
            for (Submission submission : matches) {
                if (wanted == null || ResultState.of(submission,
                        getBackend()) == wanted) {
                    shown.add(submission);
                }
            }
            shown.sort(ResultsColumns.comparatorFor(
                    getState().getResultsSort()));
        }

        table.setPlaceholder(emptyStateFor(run));
        table.setItems(FXCollections.observableArrayList(shown));
        // Rows may hold new marks after a retry, so redraw every cell.
        table.refresh();
        restoreSelection();
        selectAllButton.setDisable(shown.isEmpty());
        refreshExportButtons();
        refreshStatus();
        refreshInspector();
    }

    // Enables the export buttons when there are rows and no export runs.
    private void refreshExportButtons() {
        boolean unavailable = shown.isEmpty() || exportsRunning > 0;
        csvButton.setDisable(unavailable);
        jsonButton.setDisable(unavailable);
    }

    // Builds the empty state that fits why no row is shown.
    private EmptyState emptyStateFor(GradingRun run) {
        String section = getState().getSection().getLabel();
        if (run == null) {
            EmptyState empty = new EmptyState(Icon.RESULTS,
                    "No results for " + section + " yet",
                    "Grade a batch for this section on the Submissions "
                    + "screen, or choose another section above.");
            empty.setAction("Go to Submissions",
                    () -> getShell().navigate(Screen.UPLOAD));
            return empty;
        }
        if (run.getSubmissions().isEmpty()) {
            return new EmptyState(Icon.RESULTS, "This run has no students",
                    "The batch held no usable ZIP files.");
        }
        EmptyState empty = new EmptyState(Icon.SEARCH,
                "No students match", "Change the search or the status "
                + "filter to see more students.");
        empty.setAction("Clear Filters", () -> clearFilters());
        return empty;
    }

    // Selects the rows remembered in the state.
    private void restoreSelection() {
        restoring = true;
        table.getSelectionModel().clearSelection();
        for (int index = 0; index < shown.size(); index++) {
            String username = shown.get(index).getUsername();
            if (getState().getSelectedResults().contains(username)) {
                table.getSelectionModel().select(index);
            }
        }
        restoring = false;
        refreshStatus();
        refreshInspector();
    }

    // Remembers the selection.
    private void selectionChanged() {
        if (restoring) {
            return;
        }
        getState().getSelectedResults().clear();
        for (Submission submission
                : table.getSelectionModel().getSelectedItems()) {
            if (submission != null) {
                getState().getSelectedResults().add(
                        submission.getUsername());
            }
        }
        refreshStatus();
    }

    // Shows the selected item; JavaFX updates it after the selectedItems list.
    private void focusChanged(Submission focused) {
        if (restoring || focused == null) {
            return;
        }
        getState().setSelectedUsername(focused.getUsername());
        refreshInspector();
    }

    // Describes what is shown, selected and exported.
    private void refreshStatus() {
        GradingRun run = currentRun();
        if (run == null) {
            statusLabel.setText("No results for "
                    + getState().getSection().getLabel() + " yet.");
            return;
        }
        int selected = table.getSelectionModel().getSelectedItems().size();
        String max = Labels.formatScore(run.getAssessment().getMaxTotal());
        String scope = "the " + shown.size() + " students shown";
        if (selected > 0) {
            scope = "the " + selected + " selected student"
                    + Labels.plural(selected);
        }
        statusLabel.setText(searchNote + exportNote + shown.size()
                + " of " + run.getSubmissions().size() + " students shown, "
                + selected + " selected. Maximum " + max
                + " marks. Exports use " + scope + ".");
        clearSelectionButton.setDisable(selected == 0);
    }

    // Shows the focused student in the inspector.
    private void refreshInspector() {
        studentInspector.show(findShown(getState().getSelectedUsername()),
                currentRun());
    }

    // Explains the marks that are not plain numbers.
    private void buildLegend() {
        legend.getChildren().addAll(
                new StatusBadge(Labels.NO_MARK + " Ungraded", Tone.NEUTRAL,
                        Icon.UNGRADED),
                new StatusBadge("0 Scored zero", Tone.NEUTRAL, Icon.ZERO),
                new StatusBadge("Not compiled", Tone.ERROR, Icon.ERROR),
                new StatusBadge("Timed out", Tone.ERROR, Icon.TIMEOUT),
                new StatusBadge("Needs review", Tone.WARNING,
                        Icon.WARNING));
    }

    // Creates a table row that opens the evidence on a double click.
    private TableRow<Submission> createRow() {
        TableRow<Submission> row = new TableRow<>();
        row.setOnMouseClicked(event -> {
            if (event.getClickCount() == 2 && !row.isEmpty()) {
                handleOpenStudent();
            }
        });
        return row;
    }

    // Returns the usernames that hold the text.
    private List<String> suggestions(String text) {
        List<String> matches = new ArrayList<>();
        GradingRun run = currentRun();
        if (run == null || text == null || text.trim().isEmpty()
                || getState().isResultsCodeSearch()) {
            return matches;
        }
        String wanted = text.trim().toLowerCase();
        for (Submission submission : run.getSubmissions()) {
            if (matches.size() >= MAX_SUGGESTIONS) {
                return matches;
            }
            String username = submission.getUsername();
            if (username.toLowerCase().contains(wanted)
                    && !username.equalsIgnoreCase(text.trim())) {
                matches.add(username);
            }
        }
        return matches;
    }

    // Finds a shown student by username.
    private Submission findShown(String username) {
        for (Submission submission : shown) {
            if (submission.getUsername().equals(username)) {
                return submission;
            }
        }
        return null;
    }

    /**
     * Shows "All states" for the empty filter choice.
     */
    private static final class StateConverter
            extends StringConverter<ResultState> {

        /**
         * Returns the words for a state.
         *
         * @param state the state, or null for every state
         * @return the words
         */
        @Override
        public String toString(ResultState state) {
            if (state == null) {
                return ALL_STATES;
            }
            return state.getLabel();
        }

        /**
         * Reads a state back from its words.
         *
         * @param text the words
         * @return the state, or null for every state
         */
        @Override
        public ResultState fromString(String text) {
            for (ResultState state : ResultState.values()) {
                if (state.getLabel().equals(text)) {
                    return state;
                }
            }
            return null;
        }
    }
}
