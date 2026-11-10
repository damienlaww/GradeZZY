/*
 * File: MutantWorker.java
 *
 * Purpose: Runs broken copies of the model answer, one after another,
 * on its own thread.
 *
 * Where it fits: TesterStrengthChecker starts exec.thread.count of these
 * for each question, so the copies are compiled and run at the same
 * time, the way GradingWorker grades students.
 */
package gradezzy.modelanswer;

/**
 * Runs broken copies of the model answer until none is left.
 *
 * <p>Tier: COULD. Owner: Jing Yuan.
 */
public class MutantWorker implements Runnable {

    // The checker that runs one copy
    private final TesterStrengthChecker checker;

    // Where the worker gets its next copy
    private final MutantBatch batch;

    /**
     * Creates a worker.
     *
     * <p>Tier: COULD.
     *
     * @param checker the checker that runs one copy
     * @param batch where the worker gets its next copy
     */
    public MutantWorker(TesterStrengthChecker checker, MutantBatch batch) {
        this.checker = checker;
        this.batch = batch;
    }

    /**
     * Takes copies from the batch and runs each one, until there are none
     * left.
     *
     * <p>Tier: COULD.
     */
    @Override
    public void run() {
        int index = batch.next();

        while (index != MutantBatch.NONE_LEFT) {
            checker.runMutant(batch, index);
            index = batch.next();
        }
    }
}
