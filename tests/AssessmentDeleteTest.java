/*
 * File: AssessmentDeleteTest.java
 *
 * Purpose: Checks that deleting an assessment clears the files chosen
 * for it on Set up in every section, that Undo puts all of it back, that
 * a new assessment with the same name inherits nothing, and that a
 * rename moves the chosen files to the new name.
 *
 * Where it fits: Run by hand after compiling, as tests/README.md says.
 * It works on a copy of the fixed test settings, a temporary assessments
 * folder and temporary chosen files, so no real setting is changed.
 */
import java.io.Reader;
import java.io.Writer;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.time.LocalDateTime;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Properties;

import gradezzy.config.AppConfig;
import gradezzy.config.ConfigKeys;
import gradezzy.model.Question;
import gradezzy.model.SavedAssessment;
import gradezzy.service.AssessmentLibrary;
import gradezzy.service.ChosenSubmissionsStore;
import gradezzy.service.DeletedAssessment;
import gradezzy.service.SettingsService;
import gradezzy.ui.flow.FlowState;
import gradezzy.util.FileUtils;

/**
 * Runs the assessment delete checks and prints how many passed. Compile:
 * javac -cp classes -d /tmp/gradezzy-tests tests/*.java; run:
 * java -cp "classes:lib/*:/tmp/gradezzy-tests" AssessmentDeleteTest
 */
public class AssessmentDeleteTest {

    // The assessment deleted and made again
    private static final String NAME = "Leak Test 3";

    // The name it is renamed to
    private static final String NEW_NAME = "Leak Test 4";

    // Another assessment, whose chosen files must stay
    private static final String OTHER = "Other Test";

    // An assessment whose name starts with NAME but is not it
    private static final String LONGER = NAME + "0";

    // The setting that keeps each section's score sheet
    private static final String SHEETS_KEY =
            ConfigKeys.SCORE_SHEET_BY_SECTION;

    // The setting that keeps the score sheet of any other section
    private static final String SHEET_KEY = ConfigKeys.SCORE_SHEET_FILE;

    // The score sheets the deleted assessment kept
    private static final String OWN_SHEETS = "CS102/G1=/sheets/g1.csv";

    // The number of checks passed so far
    private static int checks = 0;

    /**
     * Runs every check in a temporary folder, then removes the folder.
     *
     * @param args not used
     * @throws Exception if a check fails or a file cannot be written
     */
    public static void main(String[] args) throws Exception {
        Path root = Files.createTempDirectory("gradezzy-delete-tests-")
                .toRealPath();
        try {
            AppConfig config = TestConfig.copyInto(root);
            config.setString(ConfigKeys.ASSESSMENTS_DIR,
                    root.resolve("assessments").toString());
            Path chosenFile = root.resolve("chosen-submissions.properties");
            config.setString(ConfigKeys.CHOSEN_SUBMISSIONS_FILE,
                    chosenFile.toString());
            AssessmentLibrary library = new AssessmentLibrary(config,
                    new SettingsService(config));
            ChosenSubmissionsStore submissions =
                    ChosenSubmissionsStore.forSubmissions(
                            chosenFile.toString());
            ChosenSubmissionsStore classFiles =
                    ChosenSubmissionsStore.forClassFiles(
                            chosenFile.toString());

            checkKeys();
            SavedAssessment draft = draft(root, NAME);
            checkNewFile(root, library, draft);
            checkDeleteAndUndo(root, library, submissions, classFiles);
            checkMadeAgain(root, library, submissions, classFiles, draft);
            checkRename(root, library, submissions, classFiles);
            checkFlowState();
            System.out.println("Passed " + checks
                    + " assessment delete checks.");
        } finally {
            FileUtils.deleteDirectory(root);
        }
    }

    // Checks which keys belong to an assessment, and how they are renamed.
    private static void checkKeys() {
        check(ChosenSubmissionsStore.isKeyOf("CS102/G1/" + NAME, NAME),
                "A section's key belongs to its assessment");
        check(ChosenSubmissionsStore.isKeyOf("CS102/G1/leak test 3", NAME),
                "The name is matched in any case");
        check(ChosenSubmissionsStore.isKeyOf("CS102/G1/" + NAME + "|ROSTER",
                NAME), "A class file's key belongs to its assessment");
        check(!ChosenSubmissionsStore.isKeyOf("CS102/G1/" + LONGER, NAME),
                "A longer name is another assessment");
        check(!ChosenSubmissionsStore.isKeyOf("CS102/G1", NAME),
                "A key for the section only names no assessment");
        check(!ChosenSubmissionsStore.isKeyOf("CS102/G1/" + NAME + "|x",
                NAME), "Only a kind of class file may follow the bar");
        String renamed = ChosenSubmissionsStore.renameKey(
                "CS102/G2/" + NAME + "|STARTER", NAME, NEW_NAME);
        check(("CS102/G2/" + NEW_NAME + "|STARTER").equals(renamed),
                "A renamed key keeps its section and kind: " + renamed);
    }

    // Checks that a new assessment is written with empty score sheets of
    // its own, so it can never take another's as an older file would.
    private static void checkNewFile(Path root, AssessmentLibrary library,
            SavedAssessment draft) throws Exception {
        LocalDateTime before = LocalDateTime.now();
        library.save(draft, null);
        Properties file = readProperties(findFile(root, NAME));
        check("".equals(file.getProperty(SHEETS_KEY)),
                "A new assessment is written with empty section sheets");
        check("".equals(file.getProperty(SHEET_KEY)),
                "A new assessment is written with an empty score sheet");
        SavedAssessment saved = find(library, NAME);
        check(saved.isScoreSheetsSaved(),
                "A new assessment keeps its own score sheets");
        boolean createdNow = saved.getCreatedAt() != null
                && !saved.getCreatedAt().isBefore(before.withNano(0));
        check(createdNow, "A new assessment notes when it was made");
    }

    // Checks that a delete clears the assessment's chosen files in every
    // section, and only its own, and that Undo puts all of it back.
    private static void checkDeleteAndUndo(Path root,
            AssessmentLibrary library, ChosenSubmissionsStore submissions,
            ChosenSubmissionsStore classFiles) throws Exception {
        // The assessment's own score sheets, chosen on Set up
        Path file = findFile(root, NAME);
        Properties values = readProperties(file);
        values.setProperty(SHEETS_KEY, OWN_SHEETS);
        writeProperties(file, values);
        submissions.save(chosenSubmissions(root));
        classFiles.save(chosenClassFiles(root));

        DeletedAssessment deleted = library.delete(NAME);
        check(deleted.hadChosenFiles(), "The delete notes the chosen files");
        check(find(library, NAME) == null, "The assessment is deleted");
        Map<String, List<Path>> left = submissions.load();
        boolean ownCleared = !left.containsKey("CS102/G1/" + NAME)
                && !left.containsKey("CS102/G2/" + NAME)
                && !left.containsKey("IS111/G1/" + NAME);
        check(ownCleared, "Its submissions are cleared in every section: "
                + left);
        boolean othersStay = left.containsKey("CS102/G1/" + OTHER)
                && left.containsKey("CS102/G1/" + LONGER)
                && left.containsKey("CS102/G1");
        check(othersStay, "Other assessments' submissions stay: " + left);
        Map<String, List<Path>> leftClassFiles = classFiles.load();
        boolean classFilesCleared = !leftClassFiles.containsKey("CS102/G1/"
                + NAME + "|ROSTER")
                && !leftClassFiles.containsKey("CS102/G2/" + NAME
                + "|TESTERS");
        check(classFilesCleared, "Its score sheet and testers are cleared: "
                + leftClassFiles);
        check(leftClassFiles.containsKey("CS102/G1/" + OTHER + "|ROSTER"),
                "Another assessment's score sheet stays");

        library.restore(deleted);
        SavedAssessment restored = find(library, NAME);
        check(restored != null, "Undo saves the assessment again");
        check(OWN_SHEETS.equals(restored.getScoreSheetsBySection()),
                "Undo keeps its own score sheets: "
                        + restored.getScoreSheetsBySection());
        check(submissions.load().equals(chosenSubmissions(root)),
                "Undo puts back its submissions in every section");
        check(classFiles.load().equals(chosenClassFiles(root)),
                "Undo puts back its score sheet and testers");
    }

    // Checks that an assessment made again with a deleted one's name
    // starts with no chosen files and no score sheets.
    private static void checkMadeAgain(Path root, AssessmentLibrary library,
            ChosenSubmissionsStore submissions,
            ChosenSubmissionsStore classFiles, SavedAssessment draft)
            throws Exception {
        LocalDateTime firstMade = find(library, NAME).getCreatedAt();
        library.delete(NAME);
        // A copy of the deleted one, as Duplicate would start from
        SavedAssessment again = draft.copy();
        again.setScoreSheetsBySection(OWN_SHEETS);
        library.save(again, null);
        SavedAssessment made = find(library, NAME);
        boolean emptySheets = "".equals(made.getScoreSheetsBySection())
                && "".equals(made.getScoreSheetFile())
                && made.isScoreSheetsSaved();
        check(emptySheets, "The new one has empty score sheets of its own");
        for (String key : submissions.load().keySet()) {
            check(!ChosenSubmissionsStore.isKeyOf(key, NAME),
                    "The new one inherits no submissions: " + key);
        }
        for (String key : classFiles.load().keySet()) {
            check(!ChosenSubmissionsStore.isKeyOf(key, NAME),
                    "The new one inherits no class files: " + key);
        }
        boolean newer = made.getCreatedAt() != null
                && !made.getCreatedAt().isBefore(firstMade);
        check(newer, "The new one is newer than the deleted one");
    }

    // Checks that a rename moves the chosen files to the new name.
    private static void checkRename(Path root, AssessmentLibrary library,
            ChosenSubmissionsStore submissions,
            ChosenSubmissionsStore classFiles) throws Exception {
        submissions.save(chosenSubmissions(root));
        classFiles.save(chosenClassFiles(root));
        SavedAssessment edit = find(library, NAME);
        edit.setName(NEW_NAME);
        library.save(edit, NAME);

        Map<String, List<Path>> moved = submissions.load();
        boolean submissionsMoved = !moved.containsKey("CS102/G1/" + NAME)
                && moved.containsKey("CS102/G1/" + NEW_NAME)
                && moved.containsKey("CS102/G2/" + NEW_NAME);
        check(submissionsMoved, "A rename moves its submissions: " + moved);
        check(moved.containsKey("CS102/G1/" + LONGER),
                "A rename leaves a longer name alone");
        Map<String, List<Path>> movedClassFiles = classFiles.load();
        boolean sheetMoved = movedClassFiles.containsKey("CS102/G1/"
                + NEW_NAME + "|ROSTER")
                && !movedClassFiles.containsKey("CS102/G1/" + NAME
                + "|ROSTER");
        check(sheetMoved, "A rename moves its score sheet: "
                + movedClassFiles);
    }

    // Checks that the window's state forgets and moves the files kept
    // for an assessment, so they are not saved back under its old name.
    private static void checkFlowState() {
        FlowState state = new FlowState();
        Map<String, List<Path>> saved = new HashMap<>();
        saved.put("CS102/G1/" + NAME, pathsOf("a.zip"));
        saved.put("CS102/G2/" + NAME, new ArrayList<>());
        saved.put("CS102/G1/" + OTHER, pathsOf("b.zip"));
        state.setSavedPaths(saved);

        state.moveAssessment(NAME, NEW_NAME);
        Map<String, List<Path>> moved = state.listChosenPaths();
        boolean stateMoved = moved.containsKey("CS102/G1/" + NEW_NAME)
                && moved.containsKey("CS102/G2/" + NEW_NAME)
                && !moved.containsKey("CS102/G1/" + NAME);
        check(stateMoved, "The state moves a renamed assessment's files: "
                + moved);

        state.forgetAssessment(NEW_NAME);
        Map<String, List<Path>> left = state.listChosenPaths();
        check(left.size() == 1 && left.containsKey("CS102/G1/" + OTHER),
                "The state forgets a deleted assessment's files: " + left);

        Map<String, List<Path>> back = new HashMap<>();
        back.put("CS102/G1/" + NAME, pathsOf("a.zip"));
        state.putBackSavedPaths(back);
        check(state.listChosenPaths().containsKey("CS102/G1/" + NAME),
                "Undo brings the files back into the state");
    }

    // Returns the submissions chosen on Set up for the checks.
    private static Map<String, List<Path>> chosenSubmissions(Path root) {
        Map<String, List<Path>> chosen = new HashMap<>();
        chosen.put("CS102/G1/" + NAME, pathsOf(root + "/g1.zip"));
        chosen.put("CS102/G2/" + NAME, pathsOf(root + "/g2.zip"));
        chosen.put("IS111/G1/" + NAME, new ArrayList<>());
        chosen.put("CS102/G1/" + OTHER, pathsOf(root + "/other.zip"));
        chosen.put("CS102/G1/" + LONGER, pathsOf(root + "/longer.zip"));
        chosen.put("CS102/G1", pathsOf(root + "/old.zip"));
        return chosen;
    }

    // Returns the class files chosen on Set up for the checks.
    private static Map<String, List<Path>> chosenClassFiles(Path root) {
        Map<String, List<Path>> chosen = new HashMap<>();
        chosen.put("CS102/G1/" + NAME + "|ROSTER",
                pathsOf(root + "/g1.csv"));
        chosen.put("CS102/G2/" + NAME + "|TESTERS",
                pathsOf(root + "/testers"));
        chosen.put("CS102/G1/" + OTHER + "|ROSTER",
                pathsOf(root + "/other.csv"));
        return chosen;
    }

    // Returns a list of one path, made absolute as the store saves it.
    private static List<Path> pathsOf(String path) {
        List<Path> paths = new ArrayList<>();
        paths.add(Path.of(path).toAbsolutePath());
        return paths;
    }

    // Returns a valid new assessment with one question, making its tester
    // and starter folders.
    private static SavedAssessment draft(Path root, String name)
            throws Exception {
        Path testers = root.resolve("testers");
        Path starter = root.resolve("starter");
        Files.createDirectories(testers);
        Files.createDirectories(starter);
        Files.writeString(testers.resolve("Q1Tester.java"),
                "public class Q1Tester {\n    int score = 0;\n"
                        + "    void run() {\n        score += 5;\n    }\n}\n");
        SavedAssessment draft = new SavedAssessment();
        draft.setName(name);
        draft.setModuleCode("CS102");
        draft.setTestersDirectory(testers.toString());
        draft.setStarterDirectory(starter.toString());
        int maxScore = 5;
        draft.getQuestions().add(new Question("Q1", "Q1", "Q1.java",
                maxScore));
        return draft;
    }

    // Returns the saved assessment with a name, or null.
    private static SavedAssessment find(AssessmentLibrary library,
            String name) throws Exception {
        for (SavedAssessment saved : library.list()) {
            if (saved.getName().equalsIgnoreCase(name)) {
                return saved;
            }
        }
        return null;
    }

    // Returns the file that holds the assessment with a name.
    private static Path findFile(Path root, String name) throws Exception {
        Path folder = root.resolve("assessments");
        for (Path file : FileUtils.listFiles(folder, ".properties")) {
            String saved = readProperties(file).getProperty(
                    ConfigKeys.ASSESSMENT_NAME, "");
            if (saved.equalsIgnoreCase(name)) {
                return file;
            }
        }
        throw new AssertionError("No file holds " + name);
    }

    // Reads a properties file.
    private static Properties readProperties(Path file) throws Exception {
        Properties values = new Properties();
        Reader reader = null;
        try {
            reader = Files.newBufferedReader(file, StandardCharsets.UTF_8);
            values.load(reader);
        } finally {
            FileUtils.closeIfOpen(reader);
        }
        return values;
    }

    // Writes a properties file.
    private static void writeProperties(Path file, Properties values)
            throws Exception {
        Writer writer = null;
        try {
            writer = Files.newBufferedWriter(file, StandardCharsets.UTF_8);
            values.store(writer, null);
        } finally {
            FileUtils.closeIfOpen(writer);
        }
    }

    // Counts a passed check, or stops with the message of a failed one.
    private static void check(boolean condition, String message) {
        if (!condition) {
            throw new AssertionError(message);
        }
        checks++;
    }
}
