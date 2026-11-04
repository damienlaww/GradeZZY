/*
 * File: AssessmentEditor.java
 *
 * Purpose: A sheet that creates or edits one assessment: its name, its
 * questions with their folders, files and maximum marks, its tester and
 * starter folders, and the files that come with them.
 *
 * Where it fits: Opened with FlowWindow.editAssessment, and from
 * AssessmentsSheet for a copy. It checks each folder with the same check
 * Set up uses, and saves through DashboardBackend.saveAssessment.
 */
package gradezzy.ui.flow;

import java.io.File;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.List;

import javafx.scene.Node;
import javafx.scene.control.Button;
import javafx.scene.control.CheckBox;
import javafx.scene.control.Label;
import javafx.scene.control.ScrollPane;
import javafx.scene.control.TextField;
import javafx.scene.layout.HBox;
import javafx.scene.layout.Priority;
import javafx.scene.layout.VBox;
import javafx.stage.DirectoryChooser;

import gradezzy.model.Question;
import gradezzy.model.SavedAssessment;
import gradezzy.ui.backend.ActionResult;
import gradezzy.ui.backend.ImportItem;
import gradezzy.ui.backend.ImportState;
import gradezzy.ui.backend.InputKind;

/**
 * Builds the sheet that creates or edits an assessment.
 */
public final class AssessmentEditor {

    // The width of the sheet
    private static final double WIDTH = 720.0;

    // The tallest the form may get before it scrolls
    private static final double FORM_HEIGHT = 470.0;

    // The size of the close icon
    private static final double CLOSE_SIZE = 16.0;

    // The width of the label column of the form
    private static final double LABEL_WIDTH = 150.0;

    // The separator between list items
    private static final String LIST_SEPARATOR = ",";

    // The window that shows the sheet
    private final FlowWindow window;

    // The assessment the form starts from
    private final SavedAssessment start;

    // The name it had before, or null for a new assessment
    private final String originalName;

    // The name field
    private final TextField name = new TextField();

    // One set of fields per question
    private final List<QuestionFields> questions = new ArrayList<>();

    // The rows of the questions
    private final VBox questionRows = new VBox(Parts.GAP_SMALL);

    // The total of the maximum marks
    private final Label total = Parts.muted("");

    // The tester and starter folders
    private final TextField testers = new TextField();
    private final TextField starter = new TextField();

    // Whether each folder can be used
    private final HBox testersStatus = Parts.row(0);
    private final HBox starterStatus = Parts.row(0);

    // The lists that come with the folders
    private final TextField classFiles = new TextField();
    private final TextField dataFiles = new TextField();
    private final TextField placeholders = new TextField();

    // Whether to grade with a new assessment at once
    private final CheckBox useNow = new CheckBox("Grade with this "
            + "assessment from now on");

    // Says what is wrong with the form
    private final Label problem = new Label();

    // Not used: create builds the sheet.
    private AssessmentEditor(FlowWindow window, SavedAssessment start,
            String originalName) {
        this.window = window;
        this.start = start;
        this.originalName = originalName;
    }

    /**
     * Builds the sheet.
     *
     * @param window the window that shows it
     * @param start the assessment to fill the form from
     * @param originalName the name of the assessment being edited, or
     *        null when the form creates a new one
     * @return the sheet's content
     */
    public static Node create(FlowWindow window, SavedAssessment start,
            String originalName) {
        return new AssessmentEditor(window, start, originalName).build();
    }

    // Builds the form and its buttons.
    private Node build() {
        String heading = "New assessment";
        if (originalName != null) {
            heading = "Edit " + originalName;
        }
        Button close = new Button();
        close.setGraphic(Parts.icon(Parts.CLOSE, CLOSE_SIZE));
        close.getStyleClass().addAll("flat", "icon-button");
        close.setOnAction(event -> window.closeSheet());
        Parts.tip(close, "Close without saving");
        HBox top = Parts.row(Parts.GAP, new Node[] {Parts.title(heading),
            Parts.grow(), close});

        fill();
        VBox form = new VBox(Parts.GAP,
                formRow("Name", "What you call this test, such as Lab Test "
                        + "2. It is shown on Home, Set up and Runs.", name),
                questionBlock(),
                formRow("Tester files", "The folder of tester programs, "
                        + "such as Q1aTester.java, that run each answer and "
                        + "award marks, with the data files they read.",
                        folderField(testers, testersStatus,
                        InputKind.TESTERS)),
                formRow("Starter files", "The folder students were given "
                        + "before the test, such as RenameToYourUsername. "
                        + "Used to spot untouched answers and to supply "
                        + "the provided class files.",
                        folderField(starter, starterStatus,
                        InputKind.STARTER)),
                formRow("Provided class files", "Compiled files given "
                        + "without source, such as DataException.class. "
                        + "They are copied from the starter folder into "
                        + "each student's copy. Separate with commas.",
                        classFiles),
                formRow("Tester data files", "Files the testers read, such "
                        + "as personstester.txt, copied next to each tester. "
                        + "Separate with commas.", dataFiles),
                formRow("Unrenamed folder names", "Starter folder names a "
                        + "student may forget to rename, such as "
                        + "RenameToYourUsername. Separate with commas.",
                        placeholders));
        ScrollPane scroll = new ScrollPane(form);
        scroll.setFitToWidth(true);
        scroll.setHbarPolicy(ScrollPane.ScrollBarPolicy.NEVER);
        scroll.setMaxHeight(FORM_HEIGHT);
        scroll.getStyleClass().add("page-scroll");

        problem.getStyleClass().add("field-problem");
        problem.setWrapText(true);
        show(problem, false);
        boolean isNew = originalName == null;
        useNow.setSelected(isNew);
        show(useNow, isNew);

        Button cancel = Parts.secondary("Cancel", () -> window.closeSheet());
        Button save = Parts.primary("Save");
        save.setOnAction(event -> save());
        HBox bottom = Parts.row(Parts.GAP, new Node[] {useNow, Parts.grow(),
            cancel, save});

        VBox body = new VBox(Parts.GAP, top, scroll, problem, bottom);
        body.setPrefWidth(WIDTH);
        body.getStyleClass().add("assessment-editor");
        return body;
    }

    // Fills the fields from the starting assessment.
    private void fill() {
        name.setText(start.getName());
        name.setPromptText("Lab Test 2");
        for (Question question : start.getQuestions()) {
            addQuestion(question);
        }
        if (questions.isEmpty()) {
            addQuestion(null);
        }
        updateTotal();
        testers.setText(start.getTestersDirectory());
        starter.setText(start.getStarterDirectory());
        classFiles.setText(joinList(start.getProvidedClassFiles()));
        dataFiles.setText(joinList(start.getTesterDataFiles()));
        placeholders.setText(joinList(start.getPlaceholderFolderNames()));
        classFiles.setPromptText("DataException.class, Shape.class");
        dataFiles.setPromptText("personstester.txt");
        placeholders.setPromptText("RenameToYourUsername");
        checkFolder(testers, testersStatus, InputKind.TESTERS);
        checkFolder(starter, starterStatus, InputKind.STARTER);
    }

    // Builds the questions part: a header, one row each, Add and the
    // total.
    private VBox questionBlock() {
        HBox header = Parts.row(Parts.GAP_SMALL, new Node[] {
            columnName("Id", QuestionFields.SHORT_WIDTH),
            columnName("Folder", QuestionFields.FOLDER_WIDTH),
            growingColumnName("File"),
            columnName("Max mark", QuestionFields.SHORT_WIDTH),
            columnName("", QuestionFields.LINK_WIDTH)});
        Button add = Parts.small("Add question", () -> addEmptyQuestion());
        add.setGraphic(Parts.icon(Parts.PLUS, CLOSE_SIZE * 3 / 4));
        Parts.tip(add, "Add a row. Type its id first; the folder and file "
                + "are filled in for you.");
        total.getStyleClass().add("small-text");
        HBox under = Parts.row(Parts.GAP, new Node[] {add, Parts.grow(),
            total});
        Label title = Parts.strong("Questions");
        HBox titleLine = Parts.labelWithInfo(title, "One row per question "
                + "the students answer. The folder and file must match the "
                + "starter folder exactly.");
        return new VBox(Parts.GAP_SMALL, titleLine, header, questionRows,
                under);
    }

    // Builds a grey column name of a fixed width.
    private Label columnName(String text, double width) {
        Label label = Parts.muted(text);
        label.getStyleClass().add("field-label");
        label.setMinWidth(width);
        label.setPrefWidth(width);
        return label;
    }

    // Builds a grey column name that takes the spare width.
    private Label growingColumnName(String text) {
        Label label = Parts.muted(text);
        label.getStyleClass().add("field-label");
        label.setMaxWidth(Double.MAX_VALUE);
        HBox.setHgrow(label, Priority.ALWAYS);
        return label;
    }

    // Builds one form row: the name and its "?" on the left, the control
    // on the right.
    private HBox formRow(String label, String help, Node control) {
        HBox names = Parts.labelWithInfo(Parts.strong(label), help);
        names.setMinWidth(LABEL_WIDTH);
        names.setPrefWidth(LABEL_WIDTH);
        HBox.setHgrow(control, Priority.ALWAYS);
        if (control instanceof TextField) {
            ((TextField) control).setMaxWidth(Double.MAX_VALUE);
        }
        return Parts.row(Parts.GAP, new Node[] {names, control});
    }

    // Builds a folder field with its Choose button and status.
    private VBox folderField(TextField field, HBox status, InputKind kind) {
        field.setMaxWidth(Double.MAX_VALUE);
        HBox.setHgrow(field, Priority.ALWAYS);
        field.focusedProperty().addListener((seen, before, now) -> {
            if (!now) {
                checkFolder(field, status, kind);
            }
        });
        Button choose = Parts.small("Choose", () -> chooseFolder(field,
                status, kind));
        HBox line = Parts.row(Parts.GAP_SMALL, new Node[] {field, choose});
        return new VBox(Parts.GAP_SMALL / 2, line, status);
    }

    // ---------------------------------------------------------- actions

    // Adds a question row filled from a question.
    private void addQuestion(Question question) {
        QuestionFields fields = new QuestionFields(question,
                () -> updateTotal());
        fields.setOnRemove(() -> removeQuestion(fields));
        questions.add(fields);
        questionRows.getChildren().add(fields.getRow());
    }

    // Adds an empty row and puts the cursor in it.
    private void addEmptyQuestion() {
        addQuestion(null);
        questions.get(questions.size() - 1).focus();
        updateTotal();
    }

    // Removes a question row.
    private void removeQuestion(QuestionFields fields) {
        questions.remove(fields);
        questionRows.getChildren().remove(fields.getRow());
        updateTotal();
    }

    // Shows the total of the maximum marks typed so far.
    private void updateTotal() {
        double sum = 0;
        boolean allNumbers = true;
        for (QuestionFields fields : questions) {
            try {
                sum += Double.parseDouble(fields.getMarkText());
            } catch (NumberFormatException exception) {
                allNumbers = false;
            }
        }
        String text = Parts.plural(questions.size(), "question") + ", "
                + Parts.mark(sum) + " marks in total";
        if (!allNumbers) {
            text += " so far";
        }
        total.setText(text);
    }

    // Asks for a folder, or uses the demo sample, then checks it.
    private void chooseFolder(TextField field, HBox status, InputKind kind) {
        Path chosen = null;
        if (window.getBackend().isDemo()) {
            List<Path> samples = window.getBackend().getSampleFiles(kind);
            if (!samples.isEmpty()) {
                chosen = samples.get(0);
            }
        } else {
            DirectoryChooser chooser = new DirectoryChooser();
            chooser.setTitle("Choose the " + kind.getTitle().toLowerCase()
                    + " folder");
            File folder = chooser.showDialog(window.getWindow());
            if (folder != null) {
                chosen = folder.toPath();
            }
        }
        if (chosen != null) {
            field.setText(chosen.toString());
            checkFolder(field, status, kind);
        }
    }

    // Shows whether a folder can be used, with the check Set up uses.
    private void checkFolder(TextField field, HBox status, InputKind kind) {
        status.getChildren().clear();
        String text = field.getText().trim();
        if (text.isEmpty()) {
            status.getChildren().add(smallMuted("Not chosen yet."));
            return;
        }
        ImportItem item;
        try {
            item = window.getBackend().inspect(kind, Path.of(text));
        } catch (RuntimeException exception) {
            // A path with characters this computer does not allow
            status.getChildren().add(Parts.pill("Cannot be used",
                    Tone.BAD));
            return;
        }
        Label pill;
        if (item.getState() == ImportState.INVALID) {
            pill = Parts.pill("Cannot be used", Tone.BAD);
        } else if (item.getState() == ImportState.WARNING) {
            pill = Parts.pill("Ready, check note", Tone.WARN);
        } else {
            pill = Parts.pill("Ready", Tone.GOOD);
        }
        Label note = smallMuted(item.getMessage());
        note.setWrapText(true);
        note.setMinWidth(0);
        status.setSpacing(Parts.GAP_SMALL);
        status.getChildren().addAll(pill, note);
    }

    // Reads the form, checks it, saves it and closes the sheet.
    private void save() {
        SavedAssessment draft = new SavedAssessment();
        draft.setName(name.getText().trim());
        for (QuestionFields fields : questions) {
            double mark;
            try {
                mark = Double.parseDouble(fields.getMarkText());
            } catch (NumberFormatException exception) {
                String id = fields.getId();
                if (id.isEmpty()) {
                    id = "each question";
                }
                showProblem("Type the maximum mark of " + id + " as a "
                        + "number, such as 3.");
                return;
            }
            draft.getQuestions().add(new Question(fields.getId(),
                    fields.getFolder(), fields.getFile(), mark));
        }
        draft.setTestersDirectory(testers.getText().trim());
        draft.setStarterDirectory(starter.getText().trim());
        draft.getProvidedClassFiles().addAll(splitList(
                classFiles.getText()));
        draft.getTesterDataFiles().addAll(splitList(dataFiles.getText()));
        draft.getPlaceholderFolderNames().addAll(splitList(
                placeholders.getText()));

        if (window.getState().isGrading() && isInUse()) {
            showProblem("A batch is being graded with this assessment. Save "
                    + "after it finishes.");
            return;
        }
        String check = window.getBackend().validateAssessment(draft,
                originalName);
        if (!check.isEmpty()) {
            showProblem(check);
            return;
        }
        ActionResult result = window.getBackend().saveAssessment(draft,
                originalName);
        if (!result.isDone()) {
            showProblem(result.getMessage());
            return;
        }
        window.closeSheet();
        if (useNow.isVisible() && useNow.isSelected()) {
            window.useAssessment(draft.getName());
        } else {
            window.notify(result.getMessage());
            window.assessmentChanged();
        }
    }

    // Says whether the form edits the assessment in use.
    private boolean isInUse() {
        return originalName != null
                && window.getBackend().getAssessment() != null
                && originalName.equalsIgnoreCase(window.getBackend()
                .getAssessment().getName());
    }

    // Shows what is wrong under the form.
    private void showProblem(String text) {
        problem.setText(text);
        show(problem, true);
    }

    // Returns small grey text.
    private Label smallMuted(String text) {
        Label label = Parts.muted(text);
        label.getStyleClass().add("small-text");
        return label;
    }

    // Splits a comma-separated list, leaving out blank items.
    private List<String> splitList(String text) {
        List<String> items = new ArrayList<>();
        for (String part : text.split(LIST_SEPARATOR)) {
            if (!part.trim().isEmpty()) {
                items.add(part.trim());
            }
        }
        return items;
    }

    // Joins a list with commas and spaces, for a field.
    private String joinList(List<String> items) {
        String text = "";
        for (String item : items) {
            if (!text.isEmpty()) {
                text += LIST_SEPARATOR + " ";
            }
            text += item;
        }
        return text;
    }

    // Shows or hides a node and frees its space.
    private void show(Node node, boolean visible) {
        node.setVisible(visible);
        node.setManaged(visible);
    }
}
