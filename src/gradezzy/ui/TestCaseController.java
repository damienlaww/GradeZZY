/*
 * File: TestCaseController.java
 *
 * Purpose: Controller of the Assessments screen.
 *
 * Where it fits: Shows the rubric and time limits, and lets the
 * instructor choose a tester folder or typed test cases. The choice is
 * kept in UiState, so the Submissions screen shows it too.
 */
package gradezzy.ui;

import java.io.File;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.List;

import javafx.beans.property.ReadOnlyStringWrapper;
import javafx.collections.FXCollections;
import javafx.collections.ListChangeListener;
import javafx.fxml.FXML;
import javafx.geometry.Pos;
import javafx.scene.Node;
import javafx.scene.control.Button;
import javafx.scene.control.ComboBox;
import javafx.scene.control.Control;
import javafx.scene.control.Label;
import javafx.scene.control.SelectionMode;
import javafx.scene.control.TableCell;
import javafx.scene.control.TableColumn;
import javafx.scene.control.TableView;
import javafx.scene.control.TextField;
import javafx.scene.control.ToggleButton;
import javafx.scene.control.ToggleGroup;
import javafx.scene.control.Tooltip;
import javafx.scene.layout.GridPane;
import javafx.scene.layout.HBox;
import javafx.scene.layout.Priority;
import javafx.scene.layout.VBox;
import javafx.stage.DirectoryChooser;

import atlantafx.base.theme.Styles;

import gradezzy.config.ConfigKeys;
import gradezzy.model.Assessment;
import gradezzy.model.Question;
import gradezzy.testcase.TestCase;
import gradezzy.ui.backend.Capability;
import gradezzy.ui.backend.ImportBatch;
import gradezzy.ui.backend.ImportItem;
import gradezzy.ui.backend.InputKind;
import gradezzy.ui.component.EmptyState;
import gradezzy.ui.component.Icon;
import gradezzy.ui.component.Labels;
import gradezzy.ui.component.Responsive;
import gradezzy.ui.component.Sheet;
import gradezzy.ui.component.StatusBadge;
import gradezzy.ui.component.Tone;

/**
 * Controller of the Assessments screen.
 */
public class TestCaseController extends BaseController {

    // The share of the width given to the rubric panel
    private static final double RUBRIC_PERCENT = 62.0;

    // The width below which the limits panel moves under the rubric
    private static final double STACK_WIDTH = 760.0;

    // The height of one rubric row
    private static final double ROW_HEIGHT = 36.0;

    // The extra height for the rubric's column headers
    private static final double HEADER_HEIGHT = 44.0;

    // The height of the rubric when it has no questions to list
    private static final double EMPTY_RUBRIC_HEIGHT = 220.0;

    // The narrowest the question column may get
    private static final double QUESTION_WIDTH = 80.0;

    // The narrowest the folder column may get
    private static final double FOLDER_WIDTH = 70.0;

    // The narrowest the required file column may get
    private static final double FILE_WIDTH = 110.0;

    // The narrowest the tester column may get
    private static final double TESTER_WIDTH = 130.0;

    // The narrowest the maximum column may get
    private static final double MAXIMUM_WIDTH = 80.0;

    // The narrowest the method column may get
    private static final double METHOD_WIDTH = 130.0;

    // The narrowest the arguments column may get
    private static final double ARGUMENTS_WIDTH = 160.0;

    // The narrowest the expected result column may get
    private static final double EXPECTED_WIDTH = 120.0;

    // The gap between a form label and its field
    private static final double FIELD_GAP = 4.0;

    // The gap between the fields of the Add Test Case form
    private static final double FORM_GAP = 12.0;

    // The gap between a file name and its message
    private static final double TEXT_GAP = 4.0;

    // The gap between the parts of the tester folder row
    private static final double ROW_GAP = 12.0;

    // The file extension of a tester source file
    private static final String JAVA_EXTENSION = ".java";

    // The style class of code text
    private static final String MONO = "mono";

    @FXML
    private Label statusLabel;

    @FXML
    private GridPane columns;

    @FXML
    private VBox rubricPanel;

    @FXML
    private VBox limitsPanel;

    @FXML
    private TableView<Question> rubricTable;

    @FXML
    private Label totalLabel;

    @FXML
    private Label limitsLabel;

    @FXML
    private Label outputLabel;

    @FXML
    private ToggleButton folderToggle;

    @FXML
    private ToggleButton typedToggle;

    @FXML
    private VBox folderBox;

    @FXML
    private Label folderHelp;

    @FXML
    private HBox testerStatus;

    @FXML
    private VBox typedBox;

    @FXML
    private TableView<TestCase> caseTable;

    @FXML
    private Button removeButton;

    @FXML
    private Label caseCountLabel;

    @FXML
    private Label typedNote;

    // The question chosen in the last Add Test Case form
    private String lastQuestionId = "";

    /**
     * Creates a new test case controller.
     */
    public TestCaseController() {
        // nothing to set up yet
    }

    /**
     * Called by JavaFX after the FXML file is loaded.
     */
    @FXML
    public void initialize() {
        Responsive.twoColumns(columns, rubricPanel, limitsPanel,
                RUBRIC_PERCENT, STACK_WIDTH);

        ToggleGroup source = new ToggleGroup();
        folderToggle.setToggleGroup(source);
        typedToggle.setToggleGroup(source);
        folderToggle.setOnAction(event -> sourceChosen(false));
        typedToggle.setOnAction(event -> sourceChosen(true));

        rubricTable.setColumnResizePolicy(
                TableView.CONSTRAINED_RESIZE_POLICY_FLEX_LAST_COLUMN);
        rubricTable.setFixedCellSize(ROW_HEIGHT);

        caseTable.getSelectionModel().setSelectionMode(
                SelectionMode.MULTIPLE);
        caseTable.setColumnResizePolicy(
                TableView.CONSTRAINED_RESIZE_POLICY_FLEX_LAST_COLUMN);
        caseTable.getSelectionModel().getSelectedItems().addListener(
                (ListChangeListener<TestCase>) change
                -> refreshCaseButtons());
        buildCaseColumns();
        EmptyState empty = new EmptyState(Icon.ASSESSMENTS,
                "No typed test cases yet", "Add one case for each check a "
                + "tester should make.");
        empty.setAction("Add Test Case", () -> handleAddTestCase());
        caseTable.setPlaceholder(empty);
    }

    /**
     * Fills in the rubric and the limits from the backend.
     */
    @Override
    public void onAttach() {
        Assessment assessment = getBackend().getAssessment();
        buildRubricColumns();
        rubricTable.setItems(FXCollections.observableArrayList(
                assessment.getQuestions()));
        rubricTable.setPrefHeight(HEADER_HEIGHT
                + assessment.getQuestions().size() * ROW_HEIGHT);
        if (assessment.getQuestions().isEmpty()) {
            rubricTable.setPrefHeight(EMPTY_RUBRIC_HEIGHT);
        }
        rubricTable.setMinHeight(rubricTable.getPrefHeight());
        rubricTable.setPlaceholder(new EmptyState(Icon.ASSESSMENTS,
                "No questions configured", "config.properties lists no "
                + "questions. Add them to assessment.question.ids, then "
                + "open GradeZZY again."));
        totalLabel.setText("Total " + Labels.formatScore(
                assessment.getMaxTotal()) + " marks");

        limitsLabel.setText("Time limits come from config.properties: "
                + getBackend().getRunTimeoutSeconds() + " s for one tester "
                + "run and " + getBackend().getCompileTimeoutSeconds()
                + " s to compile one question folder. One tester run "
                + "covers every case of one question.");
        outputLabel.setText("Each tester prints "
                + setting(ConfigKeys.PASS_MARKER, "Passed")
                + ", or a line starting with "
                + setting(ConfigKeys.FAIL_MARKER, "Failed")
                + ", for each case, then the total as a number such as "
                + "3.0.");

        String testers = "the testers";
        if (!assessment.getQuestions().isEmpty()) {
            testers = testerFile(assessment.getQuestions().get(0))
                    + " and the other testers";
        }
        folderHelp.setText("Required unless you type test cases. The "
                + "folder with " + testers + ", plus their data files.");
        typedNote.setText(typedCasesNote());
    }

    /**
     * Refreshes the tester source each time the screen is shown.
     */
    @Override
    public void onShow() {
        refresh();
    }

    /**
     * Opens the Submissions screen, the next step.
     */
    @FXML
    public void handleOpenSubmissions() {
        getShell().navigate(Screen.UPLOAD);
    }

    /**
     * Adds the tester folder; demo mode uses the sample testers.
     */
    @FXML
    public void handleUploadTesters() {
        Path path = chooseTesterFolder();
        if (path == null) {
            return;
        }
        ImportItem item = getBackend().inspect(InputKind.TESTERS, path);
        ImportBatch batch = getState().getImportBatch();
        batch.add(item);
        batch.setUsingTypedTestCases(false);
        refresh();
        showInfo("Added " + item.getName() + " as the tester folder.");
    }

    /**
     * Opens a form for one typed test case.
     */
    @FXML
    public void handleAddTestCase() {
        List<String> ids = new ArrayList<>();
        for (Question question : getBackend().getAssessment()
                .getQuestions()) {
            ids.add(question.getId());
        }
        ComboBox<String> question = new ComboBox<>(
                FXCollections.observableArrayList(ids));
        question.setAccessibleText("Question");
        if (ids.contains(lastQuestionId)) {
            question.setValue(lastQuestionId);
        } else if (!ids.isEmpty()) {
            question.setValue(ids.get(0));
        }
        TextField method = codeField("Method name");
        TextField arguments = codeField("Arguments");
        TextField expected = codeField("Expected result");
        Label error = new Label();
        error.getStyleClass().add("field-error");
        error.setWrapText(true);
        show(error, false);

        VBox form = new VBox(FORM_GAP,
                formField("Question", question, "The question whose "
                        + "tester runs this case."),
                formField("Method name", method, "The student method "
                        + "the tester calls, such as countVowels."),
                formField("Arguments", arguments, "Java expressions "
                        + "separated by commas, such as \"banana\", 2."),
                formField("Expected result", expected, "A Java "
                        + "expression, such as 3."),
                error);

        Sheet sheet = new Sheet("Add Test Case").setMessage("Every field "
                + "is required. The case is kept with this batch.");
        sheet.addContent(form);
        sheet.addSecondaryButton("Cancel", () -> getShell().closeSheet());
        sheet.addPrimaryButton("Save", false, () -> saveTestCase(
                question.getValue(), method, arguments, expected, error));
        getShell().showSheet(sheet);
        method.requestFocus();
    }

    /**
     * Removes the selected typed test cases.
     */
    @FXML
    public void handleRemoveTestCase() {
        List<TestCase> selected = new ArrayList<>(
                caseTable.getSelectionModel().getSelectedItems());
        if (selected.isEmpty()) {
            showError("Select the test cases to remove first.");
            return;
        }
        getState().getImportBatch().getTypedTestCases().removeAll(selected);
        getBackend().recordAudit("Test cases removed", "Removed "
                + selected.size() + " typed test case"
                + Labels.plural(selected.size()) + ".");
        refresh();
        showInfo("Removed " + selected.size() + " test case"
                + Labels.plural(selected.size()) + ".");
    }

    // Checks the form, then adds the test case and closes the sheet.
    private void saveTestCase(String questionId, TextField method,
            TextField arguments, TextField expected, Label error) {
        String name = method.getText().trim();
        String argumentText = arguments.getText().trim();
        String expectedText = expected.getText().trim();
        String problem = checkTestCase(questionId, name, argumentText,
                expectedText);
        if (!problem.isEmpty()) {
            error.setText(problem);
            show(error, true);
            if (questionId != null) {
                markProblem(method, arguments, expected, name,
                        argumentText);
            }
            return;
        }

        getState().getImportBatch().getTypedTestCases().add(new TestCase(
                questionId, name, argumentText, expectedText));
        lastQuestionId = questionId;
        getBackend().recordAudit("Test case added", questionId + ": "
                + name + "(" + argumentText + ") should give "
                + expectedText + ".");
        getShell().closeSheet();
        refresh();
        showInfo("Added a test case for " + questionId + ".");
    }

    // Marks the field a problem is about and moves focus to it.
    private void markProblem(TextField method, TextField arguments,
            TextField expected, String name, String argumentText) {
        TextField faulty = expected;
        if (name.isEmpty() || !isJavaName(name)) {
            faulty = method;
        } else if (argumentText.isEmpty()) {
            faulty = arguments;
        }
        TextField[] fields = {method, arguments, expected};
        for (TextField field : fields) {
            field.pseudoClassStateChanged(Styles.STATE_DANGER,
                    field == faulty);
        }
        faulty.requestFocus();
    }

    // Returns what is wrong with a typed test case, or an empty string.
    private String checkTestCase(String questionId, String name,
            String argumentText, String expectedText) {
        if (questionId == null || questionId.isEmpty()) {
            return "Choose a question.";
        }
        if (name.isEmpty()) {
            return "Enter the method name.";
        }
        if (!isJavaName(name)) {
            return "The method name must be one Java name, such as "
                    + "countVowels, without spaces or brackets.";
        }
        if (argumentText.isEmpty()) {
            return "Enter the arguments, such as \"banana\", 2.";
        }
        if (expectedText.isEmpty()) {
            return "Enter the expected result, such as 3.";
        }
        return "";
    }

    // Says whether a text is a single Java identifier.
    private boolean isJavaName(String text) {
        if (!Character.isJavaIdentifierStart(text.charAt(0))) {
            return false;
        }
        for (int index = 1; index < text.length(); index++) {
            if (!Character.isJavaIdentifierPart(text.charAt(index))) {
                return false;
            }
        }
        return true;
    }

    // Asks for the tester folder, or returns the sample in demo mode.
    private Path chooseTesterFolder() {
        if (getBackend().isDemo()) {
            List<Path> samples = getBackend().getSampleFiles(
                    InputKind.TESTERS);
            if (samples.isEmpty()) {
                return null;
            }
            return samples.get(0);
        }

        DirectoryChooser chooser = new DirectoryChooser();
        chooser.setTitle("Choose the tester folder");
        File folder = chooser.showDialog(getShell().getWindow());
        if (folder == null) {
            return null;
        }
        return folder.toPath();
    }

    // Switches between the tester folder and typed test cases.
    private void sourceChosen(boolean typed) {
        getState().getImportBatch().setUsingTypedTestCases(typed);
        refresh();
    }

    // Redraws the status line and the tester source.
    private void refresh() {
        ImportBatch batch = getState().getImportBatch();
        boolean typed = batch.isUsingTypedTestCases();
        folderToggle.setSelected(!typed);
        typedToggle.setSelected(typed);
        show(folderBox, !typed);
        show(typedBox, typed);

        showTesterFolder(batch);
        caseTable.setItems(FXCollections.observableArrayList(
                batch.getTypedTestCases()));
        refreshCaseButtons();

        Assessment assessment = getBackend().getAssessment();
        int questions = assessment.getQuestions().size();
        String status = assessment.getName() + ", " + Labels.formatScore(
                assessment.getMaxTotal()) + " marks in " + questions
                + " question" + Labels.plural(questions) + ".";
        int cases = batch.getTypedTestCases().size();
        if (typed && cases == 0) {
            status += " Grading will use typed test cases, none added yet.";
        } else if (typed) {
            status += " Grading will use " + cases + " typed test case"
                    + Labels.plural(cases) + ".";
        } else if (batch.getItems(InputKind.TESTERS).isEmpty()) {
            status += " Grading will use a tester folder, not chosen yet.";
        } else {
            status += " Grading will use the chosen tester folder.";
        }
        statusLabel.setText(status);
    }

    // Shows the chosen tester folder, or that none is chosen yet.
    private void showTesterFolder(ImportBatch batch) {
        testerStatus.getChildren().clear();
        List<ImportItem> chosen = batch.getItems(InputKind.TESTERS);
        if (chosen.isEmpty()) {
            testerStatus.getChildren().add(new StatusBadge("Not chosen "
                    + "yet. Required before grading.", Tone.WARNING));
            return;
        }

        ImportItem item = chosen.get(0);
        Label name = new Label(item.getName());
        name.getStyleClass().add(MONO);
        Label message = new Label(item.getMessage());
        message.getStyleClass().add("secondary-text");
        message.setWrapText(true);
        VBox text = new VBox(TEXT_GAP, name, message);
        text.setMaxWidth(Double.MAX_VALUE);
        HBox.setHgrow(text, Priority.ALWAYS);
        HBox row = new HBox(ROW_GAP, Icon.FOLDER.create(), text,
                StatusBadge.forImport(item.getState()));
        row.setAlignment(Pos.CENTER_LEFT);
        HBox.setHgrow(row, Priority.ALWAYS);
        testerStatus.getChildren().add(row);
    }

    // Enables Remove Selected and counts the typed test cases.
    private void refreshCaseButtons() {
        int selected = caseTable.getSelectionModel().getSelectedItems()
                .size();
        int total = caseTable.getItems().size();
        removeButton.setDisable(selected == 0);
        caseCountLabel.setText(total + " test case" + Labels.plural(total)
                + ", " + selected + " selected.");
    }

    // Explains what happens to typed test cases in this mode.
    private String typedCasesNote() {
        if (getBackend().isDemo()) {
            return "Demo mode keeps typed cases for this session; the "
                    + "sample run uses the sample testers.";
        }
        Capability capability = Capability.TYPED_TEST_CASES;
        return "Generating testers from typed cases waits for "
                + capability.getServiceMethod() + " ("
                + capability.getOwner() + "). Status: "
                + capability.getStatus().getLabel() + ".";
    }

    // Adds the five columns of the rubric table.
    private void buildRubricColumns() {
        TableColumn<Question, String> id = new TableColumn<>("Question");
        id.setCellValueFactory(data -> new ReadOnlyStringWrapper(
                data.getValue().getId()));
        id.setMinWidth(QUESTION_WIDTH);

        TableColumn<Question, String> folder = new TableColumn<>("Folder");
        folder.setCellValueFactory(data -> new ReadOnlyStringWrapper(
                data.getValue().getFolderName()));
        folder.setMinWidth(FOLDER_WIDTH);
        folder.setCellFactory(column -> new MonoCell<>());

        TableColumn<Question, String> file = new TableColumn<>(
                "Required file");
        file.setCellValueFactory(data -> new ReadOnlyStringWrapper(
                data.getValue().getFileName()));
        file.setMinWidth(FILE_WIDTH);
        file.setCellFactory(column -> new MonoCell<>());

        TableColumn<Question, String> tester = new TableColumn<>("Tester");
        tester.setCellValueFactory(data -> new ReadOnlyStringWrapper(
                testerFile(data.getValue())));
        tester.setMinWidth(TESTER_WIDTH);
        tester.setCellFactory(column -> new MonoCell<>());

        TableColumn<Question, String> maximum = new TableColumn<>(
                "Maximum");
        maximum.setCellValueFactory(data -> new ReadOnlyStringWrapper(
                Labels.formatScore(data.getValue().getMaxScore())));
        maximum.setMinWidth(MAXIMUM_WIDTH);
        maximum.getStyleClass().add("num");

        rubricTable.getColumns().add(id);
        rubricTable.getColumns().add(folder);
        rubricTable.getColumns().add(file);
        rubricTable.getColumns().add(tester);
        rubricTable.getColumns().add(maximum);
    }

    // Adds the Question, Method, Arguments and Expected columns.
    private void buildCaseColumns() {
        TableColumn<TestCase, String> id = new TableColumn<>("Question");
        id.setCellValueFactory(data -> new ReadOnlyStringWrapper(
                data.getValue().getQuestionId()));
        id.setMinWidth(QUESTION_WIDTH);

        TableColumn<TestCase, String> method = new TableColumn<>("Method");
        method.setCellValueFactory(data -> new ReadOnlyStringWrapper(
                data.getValue().getMethodName()));
        method.setMinWidth(METHOD_WIDTH);
        method.setCellFactory(column -> new MonoCell<>());

        TableColumn<TestCase, String> arguments = new TableColumn<>(
                "Arguments");
        arguments.setCellValueFactory(data -> new ReadOnlyStringWrapper(
                data.getValue().getArgumentExpressions()));
        arguments.setMinWidth(ARGUMENTS_WIDTH);
        arguments.setCellFactory(column -> new MonoCell<>());

        TableColumn<TestCase, String> expected = new TableColumn<>(
                "Expected");
        expected.setCellValueFactory(data -> new ReadOnlyStringWrapper(
                data.getValue().getExpectedExpression()));
        expected.setMinWidth(EXPECTED_WIDTH);
        expected.setCellFactory(column -> new MonoCell<>());

        caseTable.getColumns().add(id);
        caseTable.getColumns().add(method);
        caseTable.getColumns().add(arguments);
        caseTable.getColumns().add(expected);
    }

    // Returns the tester file of a question, such as Q1aTester.java.
    private String testerFile(Question question) {
        return question.getId() + getBackend().getSetting(
                ConfigKeys.TESTER_CLASS_SUFFIX) + JAVA_EXTENSION;
    }

    // Returns a setting, or a fallback when it is empty.
    private String setting(String key, String fallback) {
        String value = getBackend().getSetting(key);
        if (value.isEmpty()) {
            return fallback;
        }
        return value;
    }

    // Creates a text field for code, with a spoken name.
    private TextField codeField(String name) {
        TextField field = new TextField();
        field.setAccessibleText(name);
        field.getStyleClass().add(MONO);
        return field;
    }

    // Puts a visible label above a form control, with help under it.
    private VBox formField(String name, Control control, String help) {
        Label label = new Label(name);
        label.setLabelFor(control);
        label.getStyleClass().add("context-value");
        control.setMaxWidth(Double.MAX_VALUE);
        Label helpLabel = new Label(help);
        helpLabel.getStyleClass().add("field-help");
        helpLabel.setWrapText(true);
        return new VBox(FIELD_GAP, label, control, helpLabel);
    }

    // Shows or hides a node and frees its space.
    private void show(Node node, boolean visible) {
        node.setVisible(visible);
        node.setManaged(visible);
    }

    /**
     * A table cell for code, with the full text in a tooltip.
     *
     * @param <S> the type of the table rows
     */
    private static class MonoCell<S> extends TableCell<S, String> {

        // Creates a cell for code text.
        MonoCell() {
            getStyleClass().add(MONO);
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
            if (empty || text == null) {
                setText(null);
                setTooltip(null);
                return;
            }
            setText(text);
            setTooltip(new Tooltip(text));
        }
    }
}
