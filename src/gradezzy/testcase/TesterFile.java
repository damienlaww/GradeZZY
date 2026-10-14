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
}
