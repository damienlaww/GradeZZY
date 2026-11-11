/*
 * File: AssessmentEditor.java
 *
 * Purpose: A sheet that creates or edits one assessment: its name, its
 * questions with their folders, files and maximum marks, its tester,
 * starter and model answers folders, and the files that come with them.
 *
 * Where it fits: Opened with FlowWindow.editAssessment, and from
 * AssessmentsSheet for a copy. It checks each folder with the same check
 * Set up uses, and saves through DashboardBackend.saveAssessment.
 */
package gradezzy.ui.flow;

import java.io.File;
import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.InvalidPathException;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.function.Consumer;

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
import gradezzy.model.Assessment;
import gradezzy.model.Question;
import gradezzy.model.SavedAssessment;
import gradezzy.modelanswer.ModelAnswerCopy;
import gradezzy.service.AssessmentFolderScanner;
import gradezzy.testcase.TesterMarks;
import gradezzy.ui.backend.ActionResult;
import gradezzy.ui.backend.ImportItem;
import gradezzy.ui.backend.ImportState;
import gradezzy.ui.backend.InputKind;
import gradezzy.util.TextUtils;

/**
 * Builds the sheet that creates or edits an assessment.
 *
 * <p>Tier: SHOULD.
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

    // The separator between list items written back to a field
    private static final String LIST_JOINER = LIST_SEPARATOR + " ";

    // The separator between the names in the note of filled fields
    private static final String NOTE_NAME_SEPARATOR = ", ";

    // The link of the notice after a new assessment is saved, which
    // switches to it
    private static final String USE_IT = "Use this one";

    // The separator between the added and removed parts of that note
    private static final String NOTE_PART_SEPARATOR = "; ";

    // The ending of a tester's source file
    private static final String JAVA_EXTENSION = ".java";

    // Goes between the fields when the form is read as one line
    private static final String FORM_TEXT_SEPARATOR = "\n";

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

    // The model answers folder, which may be left empty, and whether it
    // holds the questions' answers
    private final TextField modelAnswers = new TextField();
    private final HBox modelAnswersStatus = Parts.row(0);

    // The lists that come with the folders
    private final TextField classFiles = new TextField();
    private final TextField dataFiles = new TextField();
    private final TextField placeholders = new TextField();

    // True while the saved assessment is put in the form
    private boolean opening = false;

    // The folder each kind last filled the fields from, so leaving a
    // field without changing it fills nothing again
    private final Map<InputKind, String> lastFilled = new HashMap<>();

    // Says which fields were filled in from the chosen folders
    private final Label filledNote = new Label();

    // Says what is wrong with the form
    private final Label problem = new Label();

    // True when the form sits inside a page instead of a sheet
    private boolean inline = false;

    // Called with the saved name after an inline save, or null
    private Consumer<String> onSaved = null;

    // Called with a new assessment's name when its notice's link asks to
    // use it, or null to switch as Home does
    private Consumer<String> onUse = null;

    // The inline form, once built
    private Node view = null;

    // What the inline form held when it was filled in, to tell whether
    // it changed
    private String savedForm = "";

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
     * open. Revert puts the saved values back. onUse is what the link of
     * the notice after a new assessment is saved does, such as Settings'
     * own Use this one. Returns the editor, so the page can ask whether
     * the form holds changes that are not saved; getView gives the form.
     *
     * @param window the window that shows it
     * @param start the assessment to fill the form from
     * @param originalName the name of the assessment being edited, or
     *        null when the form creates a new one
     * @param onSaved called with the saved name after a save
     * @param onUse called with the saved name when the notice's link
     *        asks to use it
     * @return the editor
     */
    public static AssessmentEditor inline(FlowWindow window,
            SavedAssessment start, String originalName,
            Consumer<String> onSaved, Consumer<String> onUse) {
        AssessmentEditor editor = new AssessmentEditor(window, start,
                originalName);
        editor.inline = true;
        editor.onSaved = onSaved;
        editor.onUse = onUse;
        editor.view = editor.build();
        editor.savedForm = editor.formText();
        return editor;
    }

    /**
     * Returns the inline form built by inline.
     *
     * @return the form
     */
    public Node getView() {
        return view;
    }

    /**
     * Says whether the inline form was changed since it was filled in.
     *
     * @return true if a field holds a change that is not saved
     */
    public boolean hasUnsavedChanges() {
        return !formText().equals(savedForm);
    }

    /**
     * Saves the inline form as its Save assessment button does.
     *
     * @return true if it was saved; false shows what is wrong under it
     */
    public boolean saveChanges() {
        return save();
    }

    /**
     * Puts back the values saved for this assessment, as Revert does.
     */
    public void revertChanges() {
        revert();
    }

    // Returns everything the form holds as one line, to tell whether
    // it changed.
    private String formText() {
        SavedAssessment draft = readDraft();
        String text = draft.getName();
        for (Question question : draft.getQuestions()) {
            text = text + FORM_TEXT_SEPARATOR + question.getId()
                    + FORM_TEXT_SEPARATOR + question.getFolderName()
                    + FORM_TEXT_SEPARATOR + question.getFileName()
                    + FORM_TEXT_SEPARATOR + question.getMaxScore();
        }
        text = text + FORM_TEXT_SEPARATOR + draft.getTestersDirectory()
                + FORM_TEXT_SEPARATOR + draft.getStarterDirectory()
                + FORM_TEXT_SEPARATOR + draft.getModelAnswersDirectory()
                + FORM_TEXT_SEPARATOR + draft.getProvidedClassFiles()
                + FORM_TEXT_SEPARATOR + draft.getTesterDataFiles()
                + FORM_TEXT_SEPARATOR + draft.getPlaceholderFolderNames();
        return text;
    }

    // Builds the form and its buttons.
    private Node build() {
        filledNote.getStyleClass().addAll("small-text", "filled-note");
        filledNote.setWrapText(true);
        show(filledNote, false);
        fill();
        VBox form = formBox();
        ScrollPane scroll = new ScrollPane(form);
        scroll.setFitToWidth(true);
        scroll.setHbarPolicy(ScrollPane.ScrollBarPolicy.NEVER);
        scroll.setMaxHeight(FORM_HEIGHT);
        scroll.getStyleClass().add("page-scroll");

        problem.getStyleClass().add("field-problem");
        problem.setWrapText(true);
        show(problem, false);

        if (inline) {
            return inlineBody(form);
        }
        HBox top = topBar();
        return sheetBody(top, scroll);
    }

    // Builds the sheet's title and its close button.
    private HBox topBar() {
        String heading = "New assessment";
        if (originalName != null) {
            heading = "Edit " + originalName;
        }
        Button close = new Button();
        close.setGraphic(Parts.icon(Parts.CLOSE, CLOSE_SIZE));
        close.getStyleClass().addAll("flat", "icon-button");
        close.setOnAction(event -> window.closeSheet());
        Parts.tip(close, "Close without saving");
        return Parts.row(Parts.GAP, new Node[] {Parts.title(heading),
            Parts.grow(), close});
    }

    // Builds the form: the name and the three numbered sections.
    private VBox formBox() {
        HBox nameRow = formRow("Name", "What you call this test, such as "
                + "Lab Test 2. It is shown on Home, Set up and History.",
                name);
        VBox files = filesSection();
        VBox questionsSection = section("2", "Questions", "One row per "
                + "question the students answer. Marks are read from each "
                + "tester.", new Node[] {questionBlock()});
        VBox found = foundSection();
        return new VBox(Parts.GAP, nameRow, files, questionsSection, found);
    }

    // Builds section 1: the tester and starter folders.
    private VBox filesSection() {
        HBox testersRow = formRow("Tester files", "The folder of tester "
                + "programs, such as Q1aTester.java, that run each answer "
                + "and award marks, with the data files they read. "
                + "Choosing it fills in the questions, their marks and the "
                + "tester data files.",
                folderField(testers, testersStatus, InputKind.TESTERS));
        HBox starterRow = formRow("Starter files", "The folder students "
                + "were given before the test, such as "
                + "RenameToYourUsername. Used to spot untouched answers and "
                + "to supply the provided class files. Choosing it fills in "
                + "the class files and unrenamed folder names.",
                folderField(starter, starterStatus, InputKind.STARTER));
        HBox modelAnswersRow = formRow("Model answers", "Optional. A "
                + "folder laid out like the starter folder, with a correct "
                + "answer to each question. With it, GradeZZY checks how "
                + "strong each tester is and tries random inputs on the "
                + "students' answers. Leave it empty to skip both checks. "
                + "Their switches are under Model answer checks in "
                + "Settings, the same for every assessment.",
                modelAnswersField());
        return section("1", "Files", "Choose these first. The questions, "
                + "their marks and the lists below fill in from them.",
                new Node[] {testersRow, starterRow, modelAnswersRow,
                    filledNote});
    }

    // Builds section 3: the lists filled in from the folders.
    private VBox foundSection() {
        HBox classRow = formRow("Provided class files", "Compiled files "
                + "given without source, such as DataException.class. They "
                + "are copied from the starter folder into each student's "
                + "copy. Separate with commas.", classFiles);
        HBox dataRow = formRow("Tester data files", "Files the testers "
                + "read, such as personstester.txt, copied next to each "
                + "tester. Separate with commas.", dataFiles);
        HBox placeholderRow = formRow("Unrenamed folder names", "Starter "
                + "folder names a student may forget to rename, such as "
                + "RenameToYourUsername. Separate with commas.",
                placeholders);
        return section("3", "Found in the files", "Filled in from the "
                + "folders. Change them only if something is missing.",
                new Node[] {classRow, dataRow, placeholderRow});
    }

    // Builds the form for inside a page: the page scrolls, and Revert
    // reloads it.
    private VBox inlineBody(VBox form) {
        Button revert = Parts.secondary("Revert", () -> revert());
        Parts.tip(revert, "Put back the values saved for this "
                + "assessment.");
        Button saveInline = Parts.primary("Save assessment");
        saveInline.setOnAction(event -> save());
        HBox buttons = Parts.row(Parts.GAP, new Node[] {Parts.grow(),
            revert, saveInline});
        VBox inlineBody = new VBox(Parts.GAP, form, problem, buttons);
        inlineBody.getStyleClass().addAll("assessment-editor", "inline");
        return inlineBody;
    }

    // Builds the sheet: the title, the scrolling form and its buttons.
    private VBox sheetBody(HBox top, ScrollPane scroll) {
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

    // Puts back the values saved for this assessment, by reloading the
    // page the inline form sits in.
    private void revert() {
        onSaved.accept(originalName);
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
        modelAnswers.setText(start.getModelAnswersDirectory());
        modelAnswers.setPromptText("Leave empty for none");

        classFiles.setText(joinList(start.getProvidedClassFiles()));
        dataFiles.setText(joinList(start.getTesterDataFiles()));
        placeholders.setText(joinList(start.getPlaceholderFolderNames()));
        classFiles.setPromptText("DataException.class, Shape.class");
        dataFiles.setPromptText("personstester.txt");
        placeholders.setPromptText("RenameToYourUsername");
        opening = true;
        checkFolder(testers, testersStatus, InputKind.TESTERS);
        checkFolder(starter, starterStatus, InputKind.STARTER);
        checkModelAnswers();

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

    // Builds the model answers field with its Choose button and status.
    private VBox modelAnswersField() {
        modelAnswers.setMaxWidth(Double.MAX_VALUE);
        HBox.setHgrow(modelAnswers, Priority.ALWAYS);
        modelAnswers.focusedProperty().addListener((seen, before, now) ->
                modelAnswersFocusChanged(now));
        Button choose = Parts.small("Choose", () -> chooseModelAnswers());
        HBox line = Parts.row(Parts.GAP_SMALL,
                new Node[] {modelAnswers, choose});
        return new VBox(Parts.GAP_TINY, line, modelAnswersStatus);
    }

    // Checks the model answers field once the cursor leaves it.
    private void modelAnswersFocusChanged(boolean focused) {
        if (!focused) {
            checkModelAnswers();
        }
    }

    // Asks for the model answers folder, then checks it.
    private void chooseModelAnswers() {
        DirectoryChooser chooser = new DirectoryChooser();
        chooser.setTitle("Choose the model answers folder");
        Choosers.prepare(chooser);
        File folder = chooser.showDialog(window.getWindow());
        Choosers.remember(folder);
        if (folder != null) {
            modelAnswers.setText(Parts.configPath(folder.toPath()));
            checkModelAnswers();
        }
    }

    // Shows whether the model answers folder can be used, and for how
    // many of the questions in the form it holds an answer.
    private void checkModelAnswers() {
        String cleaned = Parts.cleanPath(modelAnswers.getText());
        if (!cleaned.equals(modelAnswers.getText())) {
            modelAnswers.setText(cleaned);
        }
        modelAnswersStatus.getChildren().clear();
        modelAnswersStatus.setSpacing(Parts.GAP_SMALL);
        String text = modelAnswers.getText().trim();
        if (text.isEmpty()) {
            modelAnswersStatus.getChildren().add(smallMuted("None. The "
                    + "tester strength and random inputs checks are off."));
            return;
        }
        Path folder = existingFolder(text);
        if (folder == null) {
            modelAnswersStatus.getChildren().addAll(
                    Parts.pill("Cannot be used", Tone.BAD),
                    smallMuted("This folder could not be found."));
            return;
        }
        List<Question> filled = formQuestions();
        int found = 0;
        for (Question question : filled) {
            if (ModelAnswerCopy.findAnswerFile(folder, question) != null) {
                found++;
            }
        }
        Label pill = Parts.pill("Ready", Tone.GOOD);
        if (found < filled.size()) {
            pill = Parts.pill("Ready, check note", Tone.WARN);
        }
        Label note = smallMuted("Has an answer for " + found + " of "
                + Parts.plural(filled.size(), "question") + ".");
        note.setWrapText(true);
        note.setMinWidth(0);
        modelAnswersStatus.getChildren().addAll(pill, note);
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
                () -> questionChanged());
        fields.setOnRemove(() -> removeQuestion(fields));
        questions.add(fields);
        questionRows.getChildren().add(fields.getRow());
    }

    // Adds an empty row and puts the cursor in it.
    private void addEmptyQuestion() {
        addQuestion(null);
        int lastIndex = questions.size() - 1;
        questions.get(lastIndex).focus();
        updateTotal();
    }

    // Removes a question row.
    private void removeQuestion(QuestionFields fields) {
        questions.remove(fields);
        questionRows.getChildren().remove(fields.getRow());
        updateTotal();
        checkStarterAgain();
    }

    // Reads the marks and checks the starter folder again after a
    // question's id, folder or file changed.
    private void questionChanged() {
        readAllMarks();
        checkStarterAgain();
    }

    // Checks the starter folder against the questions now in the form,
    // so its status is right before the assessment is saved.
    private void checkStarterAgain() {
        checkFolder(starter, starterStatus, InputKind.STARTER);
        checkModelAnswers();
    }

    // Returns the questions in the form that have an id, folder and
    // file, for checking the starter folder.
    private List<Question> formQuestions() {
        List<Question> filled = new ArrayList<>();
        for (QuestionFields fields : questions) {
            boolean complete = !fields.getId().isEmpty()
                    && !fields.getFolder().isEmpty()
                    && !fields.getFile().isEmpty();
            if (complete) {
                filled.add(new Question(fields.getId(), fields.getFolder(),
                        fields.getFile(), fields.getMaxScore()));
            }
        }
        return filled;
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
        String suffix = window.getBackend().getSetting(
                ConfigKeys.TESTER_CLASS_SUFFIX);
        String testerFile = questionId + suffix + JAVA_EXTENSION;
        Path tester = null;
        try {
            tester = folder.resolve(testerFile);
        } catch (InvalidPathException exception) {
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
            folderChanged(kind, text, null);
            return;
        }
        ImportItem item = null;
        try {
            if (kind == InputKind.STARTER) {
                // The questions in the form, saved or not
                item = window.getBackend().inspectStarter(Path.of(text),
                        formQuestions());
            } else {
                item = window.getBackend().inspect(kind, Path.of(text));
            }
        } catch (RuntimeException exception) {
            // A path with characters this computer does not allow
            status.getChildren().add(Parts.pill("Cannot be used",
                    Tone.BAD));
            folderChanged(kind, text, null);
            return;
        }
        Label pill = statusPill(item);
        Label note = smallMuted(item.getMessage());
        note.setWrapText(true);
        note.setMinWidth(0);
        status.setSpacing(Parts.GAP_SMALL);
        status.getChildren().addAll(pill, note);

        Path usable = null;
        if (item.getState() != ImportState.INVALID) {
            usable = Path.of(text);
        }
        folderChanged(kind, text, usable);
    }

    // Returns the pill that says whether a checked folder can be used.
    private Label statusPill(ImportItem item) {
        if (item.getState() == ImportState.INVALID) {
            return Parts.pill("Cannot be used", Tone.BAD);
        }
        if (item.getState() == ImportState.WARNING) {
            return Parts.pill("Ready, check note", Tone.WARN);
        }
        return Parts.pill("Ready", Tone.GOOD);
    }

    // Updates the fields when the instructor chose or changed a folder.
    // Opening a saved assessment leaves it exactly as it was saved, and
    // leaving a field without changing it does nothing.
    private void folderChanged(InputKind kind, String text, Path usable) {
        if (opening || text.equals(lastFilled.get(kind))) {
            return;
        }

        Path previous = existingFolder(lastFilled.get(kind));
        lastFilled.put(kind, text);
        fillFromFolder(kind, previous, usable);

        if (kind == InputKind.TESTERS) {
            // The new tester folder may have added or removed questions
            checkStarterAgain();
        }
    }

    // Returns the folder a path names when it exists, or null.
    private Path existingFolder(String text) {
        if (text == null || text.isEmpty()) {
            return null;
        }

        try {
            Path folder = Path.of(text);
            if (Files.isDirectory(folder)) {
                return folder;
            }
            return null;
        } catch (RuntimeException exception) {
            // A path with characters this computer does not allow
            return null;
        }
    }

    // Brings the fields in line with a newly chosen folder. Names the
    // previous folder supplied that the new one lacks are removed, names
    // the new one has are added, and names the instructor typed are
    // kept. Either folder may be null. A note under the fields says what
    // changed.
    private void fillFromFolder(InputKind kind, Path previous, Path folder) {
        List<String> added = new ArrayList<>();
        List<String> removed = new ArrayList<>();
        if (kind == InputKind.TESTERS) {
            fillFromTesters(previous, folder, added, removed);
        } else if (kind == InputKind.STARTER) {
            fillFromStarter(previous, folder, added, removed);
        }

        if (added.isEmpty() && removed.isEmpty()) {
            return;
        }

        List<String> parts = new ArrayList<>();
        if (!added.isEmpty()) {
            parts.add("added " + TextUtils.join(NOTE_NAME_SEPARATOR, added));
        }
        if (!removed.isEmpty()) {
            parts.add("removed " + TextUtils.join(NOTE_NAME_SEPARATOR,
                    removed) + " from the previous folder");
        }

        filledNote.setText("Updated from the folder: "
                + TextUtils.join(NOTE_PART_SEPARATOR, parts)
                + ". Check them below.");
        show(filledNote, true);
    }

    // Brings the questions and tester data files in line with a new
    // tester folder, noting what was added and removed.
    private void fillFromTesters(Path previous, Path folder,
            List<String> added, List<String> removed) {
        List<String> oldIds = questionIds(previous);
        List<String> newIds = questionIds(folder);
        note(removed, removeQuestionsFrom(oldIds, newIds), "question");

        List<String> oldData = AssessmentFolderScanner.findDataFiles(
                previous);
        List<String> newData = AssessmentFolderScanner.findDataFiles(
                folder);
        note(removed, removeFrom(dataFiles, oldData, newData),
                "tester data file");

        if (folder != null) {
            note(added, addQuestionsFrom(folder), "question");
            note(added, addTo(dataFiles, newData), "tester data file");
        }
    }

    // Brings the provided class files and unrenamed folder names in line
    // with a new starter folder, noting what was added and removed.
    private void fillFromStarter(Path previous, Path folder,
            List<String> added, List<String> removed) {
        List<String> questionFolders = new ArrayList<>();
        for (QuestionFields fields : questions) {
            questionFolders.add(fields.getFolder());
        }

        List<String> oldClasses = AssessmentFolderScanner
                .findProvidedClassFiles(previous);
        List<String> newClasses = AssessmentFolderScanner
                .findProvidedClassFiles(folder);
        note(removed, removeFrom(classFiles, oldClasses, newClasses),
                "provided class file");

        List<String> oldNames = AssessmentFolderScanner
                .findStarterFolderNames(previous, questionFolders);
        List<String> newNames = AssessmentFolderScanner
                .findStarterFolderNames(folder, questionFolders);
        note(removed, removeFrom(placeholders, oldNames, newNames),
                "unrenamed folder name");

        if (folder != null) {
            note(added, addTo(classFiles, newClasses),
                    "provided class file");
            note(added, addTo(placeholders, newNames),
                    "unrenamed folder name");
        }
    }

    // Adds "count noun(s)" to a list of changes when count is above 0.
    private void note(List<String> changes, int count, String noun) {
        if (count > 0) {
            changes.add(Parts.plural(count, noun));
        }
    }

    // Returns the ids of the questions a tester folder holds; empty when
    // the folder is null or cannot be read.
    private List<String> questionIds(Path folder) {
        List<String> ids = new ArrayList<>();
        if (folder == null) {
            return ids;
        }
        String suffix = window.getBackend().getSetting(
                ConfigKeys.TESTER_CLASS_SUFFIX);
        List<Question> found = AssessmentFolderScanner.findQuestions(folder,
                suffix, null);
        for (Question question : found) {
            ids.add(question.getId());
        }

        return ids;
    }

    // Removes the question rows whose id came from the previous tester
    // folder and is not in the new one, ignoring case. Keeps one empty
    // row when none are left. Returns how many were removed.
    private int removeQuestionsFrom(List<String> oldIds,
            List<String> newIds) {
        int removedCount = 0;
        for (QuestionFields fields : new ArrayList<>(questions)) {
            String id = fields.getId().trim();
            if (containsIgnoreCase(oldIds, id)
                    && !containsIgnoreCase(newIds, id)) {
                removeQuestion(fields);
                removedCount++;
            }
        }

        if (questions.isEmpty()) {
            addQuestion(null);
            updateTotal();
        }

        return removedCount;
    }

    // Removes from a field the names the previous folder supplied that
    // the new folder does not have, ignoring case. Returns how many were
    // removed.
    private int removeFrom(TextField field, List<String> oldNames,
            List<String> newNames) {
        List<String> items = splitList(field.getText());
        List<String> kept = new ArrayList<>();
        for (String item : items) {
            if (!containsIgnoreCase(oldNames, item)
                    || containsIgnoreCase(newNames, item)) {
                kept.add(item);
            }
        }

        int removedCount = items.size() - kept.size();
        if (removedCount > 0) {
            field.setText(joinList(kept));
        }

        return removedCount;
    }

    // Whether a list holds a name, ignoring case.
    private boolean containsIgnoreCase(List<String> names, String name) {
        for (String each : names) {
            if (each.equalsIgnoreCase(name)) {
                return true;
            }
        }

        return false;
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
        String suffix = window.getBackend().getSetting(
                ConfigKeys.TESTER_CLASS_SUFFIX);
        List<Question> found = AssessmentFolderScanner.findQuestions(folder,
                suffix, starterFolder);
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

    // Reads the form, checks it, saves it and closes the sheet. Returns
    // whether it was saved.
    private boolean save() {
        SavedAssessment draft = readDraft();
        if (window.getState().isAnyGrading() && isInUse()) {
            showProblem("A batch is being graded with this assessment. Save "
                    + "after it finishes.");
            return false;
        }
        String check = window.getBackend().validateAssessment(draft,
                originalName);
        if (!check.isEmpty()) {
            showProblem(check);
            return false;
        }
        ActionResult result = window.getBackend().saveAssessment(draft,
                originalName);
        if (!result.isDone()) {
            showProblem(result.getMessage());
            return false;
        }
        if (!inline) {
            window.closeSheet();
        }
        // The page shows the saved assessment first, so a page built
        // again after the save shows it too
        if (inline && onSaved != null) {
            onSaved.accept(draft.getName().trim());
        }
        announceSaved(draft, result);
        return true;
    }

    // Reads the form into a new assessment, not saved yet.
    private SavedAssessment readDraft() {
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
        draft.setModelAnswersDirectory(modelAnswers.getText().trim());

        draft.getProvidedClassFiles().addAll(splitList(
                classFiles.getText()));
        draft.getTesterDataFiles().addAll(splitList(dataFiles.getText()));
        draft.getPlaceholderFolderNames().addAll(splitList(
                placeholders.getText()));
        return draft;
    }

    // Says the assessment was saved. A new assessment is not switched
    // to: the notice offers to use it, as Home and Set up can later.
    private void announceSaved(SavedAssessment draft, ActionResult result) {
        if (originalName == null) {
            String savedName = draft.getName().trim();
            window.notify("Saved " + savedName + ". The assessment in use "
                    + "did not change.", USE_IT, () -> use(savedName));
            // Home and Set up list it from now on
            window.assessmentChanged();
        } else {
            window.notify(result.getMessage());
            // The files chosen on Set up for each section move to the new
            // name, and a renamed assessment in use keeps the zip files
            // listed
            String newName = draft.getName().trim();
            boolean renamed = !originalName.equals(newName);
            if (renamed) {
                window.assessmentRenamed(originalName, newName);
            }
            boolean renamedInUse = renamed && originalName.equalsIgnoreCase(
                    window.getState().getAssessmentName());
            if (renamedInUse) {
                window.getState().renameAssessment(newName);
            }
            window.assessmentChanged();
        }
    }

    // Grades with a saved assessment from now on, as the notice's link
    // asks.
    private void use(String savedName) {
        if (onUse == null) {
            window.useAssessment(savedName);
        } else {
            onUse.accept(savedName);
        }
    }

    // Says whether the form edits the assessment in use.
    private boolean isInUse() {
        if (originalName == null) {
            return false;
        }
        Assessment inUse = window.getBackend().getAssessment();
        return inUse != null && originalName.equalsIgnoreCase(inUse.getName());
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
        return TextUtils.join(LIST_JOINER, items);
    }

    // Shows or hides a node and frees its space.
    private void show(Node node, boolean visible) {
        node.setVisible(visible);
        node.setManaged(visible);
    }
}
