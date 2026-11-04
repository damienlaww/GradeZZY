/*
 * File: SavedAssessment.java
 *
 * Purpose: One assessment the instructor has saved: its name, questions,
 * tester folder, starter folder and the files that come with them.
 *
 * Where it fits: AssessmentLibrary reads and writes these, one file each.
 * Choosing one copies its settings into config.properties, so the rest of
 * GradeZZY grades it with no other change.
 */
package gradezzy.model;

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

    // Class files given to students without their source
    private final List<String> providedClassFiles = new ArrayList<>();

    // Data files copied next to each tester
    private final List<String> testerDataFiles = new ArrayList<>();

    // Starter folder names a student may forget to rename
    private final List<String> placeholderFolderNames = new ArrayList<>();

    /**
     * Creates an empty assessment, to be filled with the setters.
     */
    public SavedAssessment() {
        // every field starts empty
    }

    /**
     * Returns a copy whose lists can be changed without changing this one.
     *
     * @return the copy
     */
    public SavedAssessment copy() {
        SavedAssessment copy = new SavedAssessment();
        copy.setName(name);
        copy.getQuestions().addAll(questions);
        copy.setTestersDirectory(testersDirectory);
        copy.setStarterDirectory(starterDirectory);
        copy.getProvidedClassFiles().addAll(providedClassFiles);
        copy.getTesterDataFiles().addAll(testerDataFiles);
        copy.getPlaceholderFolderNames().addAll(placeholderFolderNames);
        return copy;
    }

    /**
     * Returns the name, such as Lab Test 2.
     *
     * @return the name
     */
    public String getName() {
        return name;
    }

    /**
     * Changes the name.
     *
     * @param name the new name
     */
    public void setName(String name) {
        this.name = name;
    }

    /**
     * Returns the questions, which the caller may change.
     *
     * @return the questions, in grading order
     */
    public List<Question> getQuestions() {
        return questions;
    }

    /**
     * Returns the tester folder.
     *
     * @return the folder, as typed or chosen
     */
    public String getTestersDirectory() {
        return testersDirectory;
    }

    /**
     * Changes the tester folder.
     *
     * @param testersDirectory the new folder
     */
    public void setTestersDirectory(String testersDirectory) {
        this.testersDirectory = testersDirectory;
    }

    /**
     * Returns the starter folder.
     *
     * @return the folder, as typed or chosen
     */
    public String getStarterDirectory() {
        return starterDirectory;
    }

    /**
     * Changes the starter folder.
     *
     * @param starterDirectory the new folder
     */
    public void setStarterDirectory(String starterDirectory) {
        this.starterDirectory = starterDirectory;
    }

    /**
     * Returns the class files given without source, which the caller may
     * change.
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
     * @return the file names, such as personstester.txt
     */
    public List<String> getTesterDataFiles() {
        return testerDataFiles;
    }

    /**
     * Returns the starter folder names a student may forget to rename,
     * which the caller may change.
     *
     * @return the folder names, such as RenameToYourUsername
     */
    public List<String> getPlaceholderFolderNames() {
        return placeholderFolderNames;
    }

    /**
     * Returns the most marks a student can earn.
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
