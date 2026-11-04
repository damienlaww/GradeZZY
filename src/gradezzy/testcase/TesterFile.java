/*
 * File: TesterFile.java
 *
 * Purpose: One tester source file ready to be used for a question.
 *
 * Where it fits: Produced by a TestCaseSource and copied into each working copy
 * by TesterInstaller.
 */
package gradezzy.testcase;

import java.nio.file.Path;
import java.util.HashMap;
import java.util.List;

/**
 * One tester source file ready to be used for a question.
 *
 * <p>Tier: MUST. Owner: Zach.
 */
public class TesterFile {

    // The question this tester grades
    private final String questionId;

    // The tester class name, such as Q1aTester
    private final String className;

    // The location of the tester source
    private final Path sourceFile;

    // The snapshot of this question's folder for each student, saved after
    // compiling and taken back right before the tester runs
    private final HashMap<String, FolderSnapshot> snapshots = new HashMap<>();

    /**
     * Creates a new tester file.
     *
     * @param questionId the question this tester grades
     * @param className the tester class name, such as Q1aTester
     * @param sourceFile the location of the tester source
     */
    public TesterFile(String questionId, String className, Path sourceFile) {
        this.questionId = questionId;
        this.className = className;
        this.sourceFile = sourceFile;
    }

    /**
     * Returns the question this tester grades.
     *
     * @return the question this tester grades
     */
    public String getQuestionId() {
        return questionId;
    }

    /**
     * Returns the tester class name, such as Q1aTester.
     *
     * @return the tester class name, such as Q1aTester
     */
    public String getClassName() {
        return className;
    }

    /**
     * Returns the location of the tester source.
     *
     * @return the location of the tester source
     */
    public Path getSourceFile() {
        return sourceFile;
    }

    /**
     * Keeps a student's snapshot until the tester runs. The worker threads
     * share this object, so only one of them changes the map at a time.
     *
     * @param username the student
     * @param snapshot the snapshot of the question folder
     */
    public synchronized void saveSnapshot(String username,
            FolderSnapshot snapshot) {
        snapshots.put(username, snapshot);
    }

    /**
     * Hands back a student's snapshot and forgets it, to free the memory.
     *
     * @param username the student
     * @return the snapshot, or null if none was saved
     */
    public synchronized FolderSnapshot takeSnapshot(String username) {
        return snapshots.remove(username);
    }

    /**
     * Finds the tester of a question in a list of testers.
     *
     * @param testers the testers to search
     * @param questionId the question
     * @return the tester, or null if the question has none
     */
    public static TesterFile findFor(List<TesterFile> testers,
            String questionId) {
        for (TesterFile tester : testers) {
            if (tester.getQuestionId().equals(questionId)) {
                return tester;
            }
        }
        return null;
    }
}
