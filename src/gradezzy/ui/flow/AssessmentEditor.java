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

import java.io.IOException;
import java.io.File;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.List;

import javafx.scene.Node;
import javafx.scene.control.Button;
import javafx.scene.control.Label;
import javafx.scene.control.ScrollPane;
import javafx.scene.control.TextField;
import javafx.scene.layout.HBox;
import javafx.scene.layout.Priority;
import javafx.scene.layout.VBox;
import javafx.stage.DirectoryChooser;

import gradezzy.config.ConfigKeys;
import gradezzy.model.Question;
import gradezzy.model.SavedAssessment;
import gradezzy.testcase.TesterMarks;
import gradezzy.service.AssessmentFolderScanner;
import gradezzy.ui.backend.ActionResult;
import gradezzy.ui.backend.ImportItem;
import gradezzy.ui.backend.ImportState;
import gradezzy.ui.backend.InputKind;
import gradezzy.util.TextUtils;

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

    // The size of the plus icon of Add question
    private static final double ADD_ICON_SIZE = 12.0;

    // The gap between a section's heading and the line under it
    private static final double HEADING_GAP = 2.0;

    // The gap between the parts of a section
    private static final double SECTION_GAP = 10.0;

    // The width of the label column of the form
    private static final double LABEL_WIDTH = 150.0;

    // The separator between list items
    private static final String LIST_SEPARATOR = ",";

    // The ending of a tester's source file
    private static final String JAVA_EXTENSION = ".java";

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

    // True while the saved assessment is put in the form
    private boolean opening = false;

    // The folder each kind last filled the fields from, so leaving a
    // field without changing it fills nothing again
    private final java.util.Map<InputKind, String> lastFilled =
            new java.util.HashMap<>();

    // Says which fields were filled in from the chosen folders
    private final Label filledNote = new Label();


    // Says what is wrong with the form
    private final Label problem = new Label();

    // True when the form sits inside a page instead of a sheet
    private boolean inline = false;

    // Called with the saved name after an inline save, or null
    private java.util.function.Consumer<String> onSaved = null;

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

    /**
     * Builds the same form to sit inside a page, such as Settings: no
     * title, close button or scroll of its own, and Save keeps the form
     * open. Revert puts the saved values back.
     *
     * @param window the window that shows it
     * @param start the assessment to fill the form from
     * @param originalName the name of the assessment being edited, or
     *        null when the form creates a new one
     * @param onSaved called with the saved name after a save
     * @return the form
     */
    public static Node createInline(FlowWindow window, SavedAssessment start,
            String originalName, java.util.function.Consumer<String> onSaved) {
        AssessmentEditor editor = new AssessmentEditor(window, start,
                originalName);
        editor.inline = true;
        editor.onSaved = onSaved;
        return editor.build();
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

        filledNote.getStyleClass().addAll("small-text", "filled-note");
        filledNote.setWrapText(true);
        show(filledNote, false);
        fill();
        VBox form = new VBox(Parts.GAP,
                formRow("Name", "What you call this test, such as Lab Test "
                        + "2. It is shown on Home, Set up and History.", name),
                section("1", "Files", "Choose these first. The questions, "
                        + "their marks and the lists below fill in from them.",
                        new Node[] {
                    formRow("Tester files", "The folder of tester programs, "
                            + "such as Q1aTester.java, that run each answer "
                            + "and award marks, with the data files they "
                            + "read. Choosing it fills in the questions, "
                            + "their marks and the tester data files.",
                            folderField(testers, testersStatus,
                            InputKind.TESTERS)),
                    formRow("Starter files", "The folder students were "
                            + "given before the test, such as "
                            + "RenameToYourUsername. Used to spot untouched "
                            + "answers and to supply the provided class "
                            + "files. Choosing it fills in the class files "
                            + "and unrenamed folder names.",
                            folderField(starter, starterStatus,
                            InputKind.STARTER)),
                    filledNote}),
                section("2", "Questions", "One row per question the "
                        + "students answer. Marks are read from each tester.",
                        new Node[] {questionBlock()}),
                section("3", "Found in the files", "Filled in from the "
                        + "folders. Change them only if something is missing.",
                        new Node[] {
                    formRow("Provided class files", "Compiled files given "
                            + "without source, such as DataException.class. "
                            + "They are copied from the starter folder into "
                            + "each student's copy. Separate with commas.",
                            classFiles),
                    formRow("Tester data files", "Files the testers read, "
                            + "such as personstester.txt, copied next to each "
                            + "tester. Separate with commas.", dataFiles),
                    formRow("Unrenamed folder names", "Starter folder names "
                            + "a student may forget to rename, such as "
                            + "RenameToYourUsername. Separate with commas.",
                            placeholders)}));
        ScrollPane scroll = new ScrollPane(form);
        scroll.setFitToWidth(true);
        scroll.setHbarPolicy(ScrollPane.ScrollBarPolicy.NEVER);
        scroll.setMaxHeight(FORM_HEIGHT);
        scroll.getStyleClass().add("page-scroll");

        problem.getStyleClass().add("field-problem");
        problem.setWrapText(true);
        show(problem, false);


        if (inline) {
            // Inside a page: the page scrolls, and Revert reloads it
            Button revert = Parts.secondary("Revert", () -> onSaved.accept(
                    originalName));
            Parts.tip(revert, "Put back the values saved for this "
                    + "assessment.");
            Button saveInline = Parts.primary("Save assessment");
            saveInline.setOnAction(event -> save());
            HBox buttons = Parts.row(Parts.GAP, new Node[] {Parts.grow(),
                revert, saveInline});
            VBox inlineBody = new VBox(Parts.GAP, form, problem, buttons);
            inlineBody.getStyleClass().addAll("assessment-editor",
                    "inline");
            return inlineBody;
        }
        Button cancel = Parts.secondary("Cancel", () -> window.closeSheet());
        Button save = Parts.primary("Save");
        save.setOnAction(event -> save());
        HBox bottom = Parts.row(Parts.GAP, new Node[] {Parts.grow(),
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
        opening = true;
        checkFolder(testers, testersStatus, InputKind.TESTERS);
        checkFolder(starter, starterStatus, InputKind.STARTER);

        opening = false;
        lastFilled.put(InputKind.TESTERS, testers.getText().trim());
        lastFilled.put(InputKind.STARTER, starter.getText().trim());
    }

    // Builds the questions part: a header, one row each, Add and the
    // total.
    private VBox questionBlock() {
        HBox header = Parts.row(Parts.GAP_SMALL, new Node[] {
            columnName("Id", QuestionFields.SHORT_WIDTH),
            columnName("Folder", QuestionFields.FOLDER_WIDTH),
            growingColumnName("File"),
            columnName("Max (tester)", QuestionFields.SHORT_WIDTH),
            columnName("", QuestionFields.LINK_WIDTH)});
        Button add = Parts.small("Add question", () -> addEmptyQuestion());
        add.setGraphic(Parts.icon(Parts.PLUS, ADD_ICON_SIZE));
        Parts.tip(add, "Add a row. Type its id first; the folder and file "
                + "are filled in for you, and its maximum mark is read from "
                + "its tester.");
        total.getStyleClass().add("small-text");
        HBox under = Parts.row(Parts.GAP, new Node[] {add, Parts.grow(),
            total});
        return new VBox(Parts.GAP_SMALL, header, questionRows, under);
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

    // Builds a numbered section of the form: a heading with a line that
    // says what it is for, then its rows, in a light box.
    private VBox section(String number, String title, String help,
            Node[] rows) {
        Label badge = new Label(number);
        badge.getStyleClass().add("editor-step");
        badge.setMinWidth(Label.USE_PREF_SIZE);
        Label heading = Parts.strong(title);
        heading.getStyleClass().add("editor-section-title");
        Label about = Parts.muted(help);
        about.getStyleClass().add("small-text");
        about.setWrapText(true);
        VBox words = new VBox(HEADING_GAP, heading, about);
        HBox head = Parts.row(Parts.GAP_SMALL, new Node[] {badge, words});
        VBox box = new VBox(SECTION_GAP, head);
        box.getChildren().addAll(rows);
        box.getStyleClass().add("editor-section");
        return box;
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
        field.focusedProperty().addListener((seen, before, now) ->
                focusChanged(now, field, status, kind));
        Button choose = Parts.small("Choose", () -> chooseFolder(field,
                status, kind));
        HBox line = Parts.row(Parts.GAP_SMALL, new Node[] {field, choose});
        return new VBox(Parts.GAP_TINY, line, status);
    }

    // Checks a folder field once the cursor leaves it.
    private void focusChanged(boolean focused, TextField field,
            HBox status, InputKind kind) {
        if (!focused) {
            checkFolder(field, status, kind);
        }
    }

    // ---------------------------------------------------------- actions

    // Adds a question row filled from a question.
    private void addQuestion(Question question) {
        QuestionFields fields = new QuestionFields(question,
                () -> readAllMarks());
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

    // Reads one question's maximum mark from its tester in the chosen
    // folder, when that folder exists, then updates the total.
    private void readMark(QuestionFields fields) {
        Path folder = Parts.toPath(testers.getText());
        if (folder != null && Files.isDirectory(folder)) {
            fields.setMaxScore(markFromTester(folder, fields.getId()));
        }
        updateTotal();
    }

    // Reads every question's maximum mark again, after the tester folder
    // changed. A folder that does not exist leaves the marks as they were.
    private void readAllMarks() {
        for (QuestionFields fields : questions) {
            readMark(fields);
        }
        updateTotal();
    }

    // Returns the sum of the "score += <number>" lines of a question's
    // tester, or TesterMarks.UNKNOWN.
    private double markFromTester(Path folder, String questionId) {
        if (questionId.isEmpty()) {
            return TesterMarks.UNKNOWN;
        }
        Path tester = null;
        try {
            tester = folder.resolve(questionId + window.getBackend()
                    .getSetting(ConfigKeys.TESTER_CLASS_SUFFIX)
                    + JAVA_EXTENSION);
        } catch (java.nio.file.InvalidPathException exception) {
            return TesterMarks.UNKNOWN;
        }
        if (!Files.isRegularFile(tester)) {
            return TesterMarks.UNKNOWN;
        }
        try {
            return TesterMarks.readMaxScore(tester);
        } catch (IOException exception) {
            return TesterMarks.UNKNOWN;
        }
    }

    // Shows the total of the maximum marks read from the testers.
    private void updateTotal() {
        double sum = 0;
        boolean allKnown = true;
        for (QuestionFields fields : questions) {
            if (fields.getMaxScore() > TesterMarks.UNKNOWN) {
                sum += fields.getMaxScore();
            } else {
                allKnown = false;
            }
        }
        String text = Parts.plural(questions.size(), "question") + ", "
                + Parts.mark(sum) + " marks in total";
        if (!allKnown) {
            text += " so far (\"?\" means no tester marks yet)";
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
            Choosers.prepare(chooser);
            File folder = chooser.showDialog(window.getWindow());
            Choosers.remember(folder);
            if (folder != null) {
                chosen = folder.toPath();
            }
        }
        if (chosen != null) {
            // Saved as testers.dir or starter.dir when in use
            field.setText(Parts.configPath(chosen));
            checkFolder(field, status, kind);
        }
    }

    // Shows whether a folder can be used, with the check Set up uses.
    private void checkFolder(TextField field, HBox status, InputKind kind) {
        String cleaned = Parts.cleanPath(field.getText());
        if (!cleaned.equals(field.getText())) {
            field.setText(cleaned);
        }
        if (kind == InputKind.TESTERS) {
            readAllMarks();
        }
        status.getChildren().clear();
        String text = field.getText().trim();
        if (text.isEmpty()) {
            status.getChildren().add(smallMuted("Not chosen yet."));
            return;
        }
        ImportItem item = null;
        try {
            item = window.getBackend().inspect(kind, Path.of(text));
        } catch (RuntimeException exception) {
            // A path with characters this computer does not allow
            status.getChildren().add(Parts.pill("Cannot be used",
                    Tone.BAD));
            return;
        }
        Label pill = null;
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
        // Only a folder the instructor chose or changed fills the fields;
        // opening a saved assessment leaves it exactly as it was saved
        if (item.getState() != ImportState.INVALID && !opening
                && !text.equals(lastFilled.get(kind))) {
            lastFilled.put(kind, text);
            fillFromFolder(kind, Path.of(text));
        }
    }

    // Fills the data files, provided class files and unrenamed folder
    // names from a chosen folder. Names found are added to what is
    // there; nothing typed is removed. A note under the fields says
    // what was added.
    private void fillFromFolder(InputKind kind, Path folder) {
        List<String> added = new ArrayList<>();
        if (kind == InputKind.TESTERS) {
            int found = addQuestionsFrom(folder);
            if (found > 0) {
                added.add(Parts.plural(found, "question"));
            }
            int count = addTo(dataFiles,
                    AssessmentFolderScanner.findDataFiles(folder));
            if (count > 0) {
                added.add(Parts.plural(count, "tester data file"));
            }
        } else if (kind == InputKind.STARTER) {
            int classes = addTo(classFiles,
                    AssessmentFolderScanner.findProvidedClassFiles(folder));
            if (classes > 0) {
                added.add(Parts.plural(classes, "provided class file"));
            }
            List<String> questionFolders = new ArrayList<>();
            for (QuestionFields fields : questions) {
                questionFolders.add(fields.getFolder());
            }
            int names = addTo(placeholders, AssessmentFolderScanner
                    .findStarterFolderNames(folder, questionFolders));
            if (names > 0) {
                added.add(Parts.plural(names, "unrenamed folder name"));
            }
        }
        if (added.isEmpty()) {
            return;
        }
        filledNote.setText("Filled in from the folder: "
                + TextUtils.join(", ", added) + ". Check them below.");
        show(filledNote, true);
    }

    // Adds one question per tester the form does not list yet, with its
    // file, folder and marks. Empty rows are dropped first, so a new
    // assessment shows just the questions found. Returns how many were
    // added.
    private int addQuestionsFrom(Path folder) {
        Path starterFolder = null;
        String starterText = starter.getText().trim();
        if (!starterText.isEmpty()) {
            try {
                starterFolder = Path.of(starterText);
            } catch (RuntimeException exception) {
                starterFolder = null;
            }
        }
        List<Question> found = AssessmentFolderScanner.findQuestions(folder,
                window.getBackend().getSetting(
                ConfigKeys.TESTER_CLASS_SUFFIX), starterFolder);
        if (found.isEmpty()) {
            return 0;
        }
        for (QuestionFields fields : new ArrayList<>(questions)) {
            if (fields.getId().trim().isEmpty()) {
                removeQuestion(fields);
            }
        }
        int added = 0;
        for (Question question : found) {
            boolean listed = false;
            for (QuestionFields fields : questions) {
                if (fields.getId().trim().equalsIgnoreCase(
                        question.getId())) {
                    listed = true;
                }
            }
            if (!listed) {
                addQuestion(question);
                added++;
            }
        }
        updateTotal();
        return added;
    }

    // Adds names a field does not list yet, ignoring case, and returns
    // how many were added.
    private int addTo(TextField field, List<String> names) {
        List<String> items = splitList(field.getText());
        int added = 0;
        for (String name : names) {
            boolean listed = false;
            for (String item : items) {
                if (item.equalsIgnoreCase(name)) {
                    listed = true;
                }
            }
            if (!listed) {
                items.add(name);
                added++;
            }
        }
        if (added > 0) {
            field.setText(joinList(items));
        }
        return added;
    }

    // Reads the form, checks it, saves it and closes the sheet.
    private void save() {
        SavedAssessment draft = new SavedAssessment();
        draft.setName(name.getText().trim());
        draft.setModuleCode(start.getModuleCode());
        for (QuestionFields fields : questions) {
            draft.getQuestions().add(new Question(fields.getId(),
                    fields.getFolder(), fields.getFile(),
                    fields.getMaxScore()));
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
        if (!inline) {
            window.closeSheet();
        }
        // A new assessment is the one graded from now on; it can be
        // switched back on Home or Set up
        if (originalName == null && window.useAssessment(draft.getName())) {
            window.notify("Saved " + draft.getName() + ". It is now the "
                    + "assessment in use.");
        } else if (originalName == null) {
            // useAssessment already said why it was not switched
            window.notify("Saved " + draft.getName() + ". The assessment "
                    + "in use did not change.");
        } else {
            window.notify(result.getMessage());
            window.assessmentChanged();
        }
        if (inline && onSaved != null) {
            onSaved.accept(draft.getName());
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
        return TextUtils.join(LIST_SEPARATOR + " ", items);
    }

    // Shows or hides a node and frees its space.
    private void show(Node node, boolean visible) {
        node.setVisible(visible);
        node.setManaged(visible);
    }
}
