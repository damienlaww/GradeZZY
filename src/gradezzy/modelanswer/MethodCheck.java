/*
 * File: MethodCheck.java
 *
 * Purpose: One method of a question ready to check: the inputs, the
 * program that calls the method with them, and what the model answer
 * gave for each.
 *
 * Where it fits: Made once per question and run by RandomInputChecker,
 * then reused for every student, so the model answer runs only once.
 */
package gradezzy.modelanswer;

import java.util.List;
import java.util.Map;

/**
 * One method of a question ready to check against the model answer.
 *
 * <p>Tier: COULD. Owner: Jing Yuan.
 */
public class MethodCheck {

    // The method
    private final MethodSignature method;

    // The inputs, one argument per parameter, as Java text
    private final List<List<String>> inputs;

    // The program that calls the method with every input
    private final String driverSource;

    // What the model answer gave, by call number from 1
    private final Map<Integer, CallResult> modelResults;

    /**
     * Creates a method ready to check.
     *
     * <p>Tier: COULD.
     *
     * @param method the method
     * @param inputs the inputs
     * @param driverSource the program that calls the method
     * @param modelResults what the model answer gave, by call number
     */
    public MethodCheck(MethodSignature method, List<List<String>> inputs,
            String driverSource, Map<Integer, CallResult> modelResults) {
        this.method = method;
        this.inputs = inputs;
        this.driverSource = driverSource;
        this.modelResults = modelResults;
    }

    /**
     * Returns the method.
     *
     * <p>Tier: COULD.
     *
     * @return the method
     */
    public MethodSignature getMethod() {
        return method;
    }

    /**
     * Returns the inputs.
     *
     * <p>Tier: COULD.
     *
     * @return the inputs, one argument per parameter, as Java text
     */
    public List<List<String>> getInputs() {
        return inputs;
    }

    /**
     * Returns the program that calls the method with every input.
     *
     * <p>Tier: COULD.
     *
     * @return the source
     */
    public String getDriverSource() {
        return driverSource;
    }

    /**
     * Returns what the model answer gave.
     *
     * <p>Tier: COULD.
     *
     * @return the results, by call number from 1
     */
    public Map<Integer, CallResult> getModelResults() {
        return modelResults;
    }
}
