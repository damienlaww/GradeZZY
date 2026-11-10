/*
 * File: Question.java
 *
 * Purpose: One gradable question of an assessment, such as Q1a.
 *
 * Where it fits: Built from config.properties; validators use it to know which
 * folder and file must exist and scoring uses its maximum score.
 */
package gradezzy.model;

import java.util.ArrayList;
import java.util.List;

/**
 * One gradable question of an assessment, such as Q1a.
 *
 * <p>Tier: MUST. Owner: Jing Yuan.
 */
public class Question {

    // The question id, for example Q1a
    private final String id;

    // The folder the file must be in, such as Q1
    private final String folderName;

    // The required source file, such as Q1a.java
    private final String fileName;

    // The most marks this question can earn
    private final double maxScore;

    // The other source files the student writes for this question, such
    // as GoldMember.java next to Q3.java
    private final List<String> extraFileNames = new ArrayList<>();

    /**
     * Creates a new question.
     *
     * <p>Tier: MUST.
     *
     * @param id the question id, for example Q1a
     * @param folderName the folder the file must be in, such as Q1
     * @param fileName the required source file, such as Q1a.java
     * @param maxScore the most marks this question can earn
     */
    public Question(String id, String folderName, String fileName,
            double maxScore) {
        this.id = id;
        this.folderName = folderName;
        this.fileName = fileName;
        this.maxScore = maxScore;
    }

    /**
     * Returns the question id, for example Q1a.
     *
     * <p>Tier: MUST.
     *
     * @return the question id, for example Q1a
     */
    public String getId() {
        return id;
    }

    /**
     * Returns the folder the file must be in, such as Q1.
     *
     * <p>Tier: MUST.
     *
     * @return the folder the file must be in, such as Q1
     */
    public String getFolderName() {
        return folderName;
    }

    /**
     * Returns the required source file, such as Q1a.java.
     *
     * <p>Tier: MUST.
     *
     * @return the required source file, such as Q1a.java
     */
    public String getFileName() {
        return fileName;
    }

    /**
     * Returns the most marks this question can earn.
     *
     * <p>Tier: MUST.
     *
     * @return the most marks this question can earn
     */
    public double getMaxScore() {
        return maxScore;
    }

    /**
     * Returns the other source files the student writes for this
     * question, such as GoldMember.java next to Q3.java. They are the
     * starter's other .java files in the question's folder, filled in
     * when a batch is graded; the list is empty when there are none or no
     * starter folder is known. The list can be changed.
     *
     * <p>Tier: MUST.
     *
     * @return the other source file names
     */
    public List<String> getExtraFileNames() {
        return extraFileNames;
    }
}
