/*
 * File: MutantBatch.java
 *
 * Purpose: The broken copies of one question waiting to be run, with
 * what each run needs, handed out one at a time to the workers.
 *
 * Where it fits: Made by TesterStrengthChecker for each question. Each
 * MutantWorker asks it for the next copy, the way GradingWorker asks the
 * SubmissionHandout for the next student.
 */
package gradezzy.modelanswer;

import java.nio.file.Path;
import java.util.List;

import gradezzy.model.Question;

/**
 * The broken copies of one question, handed out one at a time.
 *
 * <p>Tier: COULD. Owner: Jing Yuan.
 */
public class MutantBatch {

    /**
     * Returned by next when every copy has been handed out.
     */
    public static final int NONE_LEFT = -1;

    // The question the copies are of
    private final Question question;

    // The broken copies
    private final List<Mutant> mutants;

    // The compiled model answer, with its tester and data files
    private final Path modelFolder;

    // The folder each copy gets its own folder in
    private final Path workFolder;

    // The tester's class name and source file name
    private final String testerClassName;
    private final String testerFileName;

    // The index of the next copy to hand out
    private int nextIndex;

    /**
     * Creates a batch with no copy handed out yet.
     *
     * @param question the question the copies are of
     * @param mutants the broken copies
     * @param modelFolder the compiled model answer with its tester
     * @param workFolder the folder each copy gets its own folder in
     * @param testerClassName the tester's class name, such as Q1Tester
     * @param testerFileName the tester's file name, such as Q1Tester.java
     */
    public MutantBatch(Question question, List<Mutant> mutants,
            Path modelFolder, Path workFolder, String testerClassName,
            String testerFileName) {
        this.question = question;
        this.mutants = mutants;
        this.modelFolder = modelFolder;
        this.workFolder = workFolder;
        this.testerClassName = testerClassName;
        this.testerFileName = testerFileName;
    }

    /**
     * Hands out the next copy. Only one worker at a time gets in, so no
     * copy is run twice.
     *
     * @return the index of the copy, or NONE_LEFT
     */
    public synchronized int next() {
        if (nextIndex >= mutants.size()) {
            return NONE_LEFT;
        }
        int index = nextIndex;
        nextIndex++;
        return index;
    }

    /**
     * Returns the question the copies are of.
     *
     * @return the question
     */
    public Question getQuestion() {
        return question;
    }

    /**
     * Returns the broken copies.
     *
     * @return the copies
     */
    public List<Mutant> getMutants() {
        return mutants;
    }

    /**
     * Returns the folder of the compiled model answer and its tester.
     *
     * @return the folder
     */
    public Path getModelFolder() {
        return modelFolder;
    }

    /**
     * Returns the folder each copy gets its own folder in.
     *
     * @return the folder
     */
    public Path getWorkFolder() {
        return workFolder;
    }

    /**
     * Returns the tester's class name.
     *
     * @return the class name, such as Q1Tester
     */
    public String getTesterClassName() {
        return testerClassName;
    }

    /**
     * Returns the tester's file name.
     *
     * @return the file name, such as Q1Tester.java
     */
    public String getTesterFileName() {
        return testerFileName;
    }
}
