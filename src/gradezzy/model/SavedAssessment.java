/*
 * File: SavedAssessment.java
 *
 * Purpose: One assessment the instructor has saved: its name, questions,
 * tester folder, starter folder, model answers folder, score sheets and
 * the files that come with them.
 *
 * Where it fits: AssessmentLibrary reads and writes these, one file each.
 * Choosing one copies its settings into config.properties, so the rest of
 * GradeZZY grades it with no other change.
 */
package gradezzy.model;

import java.time.LocalDateTime;
import java.util.ArrayList;
import java.util.List;

/**
 * One assessment the instructor has saved, such as Lab Test 2.
 *
 * <p>Tier: SHOULD. Owner: Damien.
 */
public class SavedAssessment {

    // The name shown everywhere, such as Lab Test 2
    private String name = "";

    // The questions, in the order they are graded
    private final List<Question> questions = new ArrayList<>();

    // The folder of tester files and the data files they read
    private String testersDirectory = "";

    // The folder of starter files given to students
    private String starterDirectory = "";

    // The folder of model answers, laid out like the starter folder, or
    // "" when this assessment has none
    private String modelAnswersDirectory = "";

    // The module the assessment belongs to, or "" for every module
    private String moduleCode = "";

    // Class files given to students without their source
    private final List<String> providedClassFiles = new ArrayList<>();

    // Data files copied next to each tester
    private final List<String> testerDataFiles = new ArrayList<>();

    // Starter folder names a student may forget to rename
    private final List<String> placeholderFolderNames = new ArrayList<>();

    // Each section's score sheet, written as in
    // paths.score.sheet.by.section, such as CS102/G1=g1.csv;CS102/G2=g2.csv
    private String scoreSheetsBySection = "";

    // The score sheet of a section with none of its own, or "" for none
    private String scoreSheetFile = "";

    // False only for a file saved before assessments kept their score
    // sheets; such an assessment takes the ones in the settings
    private boolean scoreSheetsSaved = true;

    // When it was first saved, or null when a file saved before
    // assessments kept the time does not say
    private LocalDateTime createdAt = null;

    /**
     * Creates an empty assessment, to be filled with the setters.
     *
     * <p>Tier: SHOULD.
     */
    public SavedAssessment() {
        // every field starts empty
    }

    /**
     * Returns a copy whose lists can be changed without changing this one.
     * The Question objects in the question list are shared, not copied,
     * so a change to a question's extra file names shows in both.
     *
     * <p>Tier: SHOULD.
     *
     * @return the copy
     */
    public SavedAssessment copy() {
        SavedAssessment copy = new SavedAssessment();
        copy.setName(name);
        copy.getQuestions().addAll(questions);
        copy.setTestersDirectory(testersDirectory);
        copy.setStarterDirectory(starterDirectory);
        copy.setModelAnswersDirectory(modelAnswersDirectory);
        copy.setModuleCode(moduleCode);
        copy.getProvidedClassFiles().addAll(providedClassFiles);
        copy.getTesterDataFiles().addAll(testerDataFiles);
        copy.getPlaceholderFolderNames().addAll(placeholderFolderNames);
        copy.setScoreSheetsBySection(scoreSheetsBySection);
        copy.setScoreSheetFile(scoreSheetFile);
        copy.setScoreSheetsSaved(scoreSheetsSaved);
        copy.setCreatedAt(createdAt);
        return copy;
    }

    /**
     * Returns the name, such as Lab Test 2.
     *
     * <p>Tier: SHOULD.
     *
     * @return the name
     */
    public String getName() {
        return name;
    }

    /**
     * Changes the name.
     *
     * <p>Tier: SHOULD.
     *
     * @param name the new name
     */
    public void setName(String name) {
        this.name = name;
    }

    /**
     * Returns the questions, which the caller may change.
     *
     * <p>Tier: SHOULD.
     *
     * @return the questions, in grading order
     */
    public List<Question> getQuestions() {
        return questions;
    }

    /**
     * Returns the tester folder.
     *
     * <p>Tier: SHOULD.
     *
     * @return the folder, as typed or chosen
     */
    public String getTestersDirectory() {
        return testersDirectory;
    }

    /**
     * Changes the tester folder.
     *
     * <p>Tier: SHOULD.
     *
     * @param testersDirectory the new folder
     */
    public void setTestersDirectory(String testersDirectory) {
        this.testersDirectory = testersDirectory;
    }

    /**
     * Returns the starter folder.
     *
     * <p>Tier: SHOULD.
     *
     * @return the folder, as typed or chosen
     */
    public String getStarterDirectory() {
        return starterDirectory;
    }

    /**
     * Changes the starter folder.
     *
     * <p>Tier: SHOULD.
     *
     * @param starterDirectory the new folder
     */
    public void setStarterDirectory(String starterDirectory) {
        this.starterDirectory = starterDirectory;
    }

    /**
     * Returns the model answers folder, used to check the testers and
     * to try random inputs.
     *
     * <p>Tier: COULD.
     *
     * @return the folder, as typed or chosen, or "" for none
     */
    public String getModelAnswersDirectory() {
        return modelAnswersDirectory;
    }

    /**
     * Changes the model answers folder.
     *
     * <p>Tier: COULD.
     *
     * @param modelAnswersDirectory the new folder, or "" for none
     */
    public void setModelAnswersDirectory(String modelAnswersDirectory) {
        if (modelAnswersDirectory == null) {
            this.modelAnswersDirectory = "";
        } else {
            this.modelAnswersDirectory = modelAnswersDirectory;
        }
    }

    /**
     * Returns the module this assessment belongs to.
     *
     * <p>Tier: SHOULD.
     *
     * @return the module, such as CS102, or "" for every module
     */
    public String getModuleCode() {
        return moduleCode;
    }

    /**
     * Changes the module this assessment belongs to.
     *
     * <p>Tier: SHOULD.
     *
     * @param moduleCode the module, or "" for every module
     */
    public void setModuleCode(String moduleCode) {
        if (moduleCode == null) {
            this.moduleCode = "";
        } else {
            this.moduleCode = moduleCode.trim();
        }
    }

    /**
     * Says whether this assessment is listed for a module: one of that
     * module, or one made before assessments had a module, which every
     * module that has the shared sections can use.
     *
     * <p>Tier: SHOULD.
     *
     * @param module the module
     * @param ownOnly true for a module added with only its own sections
     * @return true when it is listed for the module
     */
    public boolean belongsTo(String module, boolean ownOnly) {
        if (moduleCode.isEmpty()) {
            return !ownOnly;
        }
        return moduleCode.equalsIgnoreCase(module);
    }

    /**
     * Returns the assessments listed for a module, in the order given,
     * as belongsTo decides. A null module lists none.
     *
     * <p>Tier: SHOULD.
     *
     * @param saved the saved assessments
     * @param module the module, or null
     * @param ownOnly true for a module added with only its own sections
     * @return the module's assessments
     */
    public static List<SavedAssessment> listedFor(List<SavedAssessment> saved,
            String module, boolean ownOnly) {
        List<SavedAssessment> listed = new ArrayList<>();
        if (module == null) {
            return listed;
        }
        for (SavedAssessment assessment : saved) {
            if (assessment.belongsTo(module, ownOnly)) {
                listed.add(assessment);
            }
        }
        return listed;
    }

    /**
     * Returns the class files given without source, which the caller may
     * change.
     *
     * <p>Tier: SHOULD.
     *
     * @return the file names, such as DataException.class
     */
    public List<String> getProvidedClassFiles() {
        return providedClassFiles;
    }

    /**
     * Returns the data files copied next to each tester, which the caller
     * may change.
     *
     * <p>Tier: SHOULD.
     *
     * @return the file names, such as personstester.txt
     */
    public List<String> getTesterDataFiles() {
        return testerDataFiles;
    }

    /**
     * Returns the starter folder names a student may forget to rename,
     * which the caller may change.
     *
     * <p>Tier: SHOULD.
     *
     * @return the folder names, such as RenameToYourUsername
     */
    public List<String> getPlaceholderFolderNames() {
        return placeholderFolderNames;
    }

    /**
     * Returns each section's score sheet, written as in
     * paths.score.sheet.by.section.
     *
     * <p>Tier: SHOULD.
     *
     * @return the sheets, such as CS102/G1=g1.csv;CS102/G2=g2.csv, or ""
     *         when no section has one
     */
    public String getScoreSheetsBySection() {
        return scoreSheetsBySection;
    }

    /**
     * Changes each section's score sheet.
     *
     * <p>Tier: SHOULD.
     *
     * @param scoreSheetsBySection the sheets, written as in
     *        paths.score.sheet.by.section, or "" for none
     */
    public void setScoreSheetsBySection(String scoreSheetsBySection) {
        if (scoreSheetsBySection == null) {
            this.scoreSheetsBySection = "";
        } else {
            this.scoreSheetsBySection = scoreSheetsBySection.trim();
        }
    }

    /**
     * Returns the score sheet of a section with none of its own.
     *
     * <p>Tier: SHOULD.
     *
     * @return the file, or "" for none
     */
    public String getScoreSheetFile() {
        return scoreSheetFile;
    }

    /**
     * Changes the score sheet of a section with none of its own.
     *
     * <p>Tier: SHOULD.
     *
     * @param scoreSheetFile the file, or "" for none
     */
    public void setScoreSheetFile(String scoreSheetFile) {
        if (scoreSheetFile == null) {
            this.scoreSheetFile = "";
        } else {
            this.scoreSheetFile = scoreSheetFile.trim();
        }
    }

    /**
     * Says whether this assessment keeps its own score sheets. Only one
     * read from a file saved before assessments kept them does not.
     *
     * <p>Tier: SHOULD.
     *
     * @return true when the score sheets are its own
     */
    public boolean isScoreSheetsSaved() {
        return scoreSheetsSaved;
    }

    /**
     * Notes whether this assessment keeps its own score sheets.
     *
     * <p>Tier: SHOULD.
     *
     * @param scoreSheetsSaved false for a file saved before assessments
     *        kept them
     */
    public void setScoreSheetsSaved(boolean scoreSheetsSaved) {
        this.scoreSheetsSaved = scoreSheetsSaved;
    }

    /**
     * Returns when the assessment was first saved. A new assessment with
     * the name of a deleted one is newer than the deleted one's runs, so
     * Set up does not list their files for it.
     *
     * <p>Tier: SHOULD.
     *
     * @return the time, or null when it is not known
     */
    public LocalDateTime getCreatedAt() {
        return createdAt;
    }

    /**
     * Notes when the assessment was first saved.
     *
     * <p>Tier: SHOULD.
     *
     * @param createdAt the time, or null when it is not known
     */
    public void setCreatedAt(LocalDateTime createdAt) {
        this.createdAt = createdAt;
    }

    /**
     * Returns the most marks a student can earn.
     *
     * <p>Tier: SHOULD.
     *
     * @return the sum of the questions' maximum marks
     */
    public double getMaxTotal() {
        double total = 0;
        for (Question question : questions) {
            total += question.getMaxScore();
        }
        return total;
    }

    /**
     * Builds the Assessment the grading screens use, for one module.
     *
     * <p>Tier: SHOULD.
     *
     * @param moduleCode the module, such as CS102
     * @return the assessment
     */
    public Assessment toAssessment(String moduleCode) {
        Assessment assessment = new Assessment(name, moduleCode);
        for (Question question : questions) {
            assessment.addQuestion(question);
        }
        assessment.getPlaceholderFolderNames().addAll(
                placeholderFolderNames);
        return assessment;
    }
}
