/*
 * File: CompileStage.java
 *
 * Purpose: Compiles the student's code and the testers, one question at a time.
 *
 * Where it fits: Sixth stage. A question that does not compile gets a
 * COMPILE_ERROR anomaly and the others still continue.
 */
package gradezzy.grading;

import gradezzy.exec.SourceCompiler;
import gradezzy.model.Submission;

/**
 * Compiles the student's code and the testers, one question at a time.
 *
 * <p>Tier: MUST. Owner: Zach.
 */
public class CompileStage implements GradingStage {

    /**
     * The name shown while this stage runs.
     */
    public static final String NAME = "Compiling";

    // The helper that runs javac
    private final SourceCompiler sourceCompiler;

    /**
     * Creates a new compile stage.
     *
     * @param sourceCompiler the helper that runs javac
     */
    public CompileStage(SourceCompiler sourceCompiler) {
        this.sourceCompiler = sourceCompiler;
    }

    /**
     * Returns the name of this stage.
     *
     * @return the name of this stage
     */
    @Override
    public String getName() {
        return NAME;
    }

    /**
     * Compiles the student's code and the testers, one question at a time.
     *
     * @param submission the submission
     * @param context the context
     * @throws GradingException if the operation fails
     */
    @Override
    public void process(Submission submission,
            GradingContext context) throws GradingException {
        // TODO(Zach): implement: compiling each question folder, creating a
        //     QuestionResult per question and recording compile errors as
        //     anomalies
    }
}
