/*
 * File: MethodSignature.java
 *
 * Purpose: The name, parameter types and return type of one static
 * method of a model answer that can be called with made-up inputs.
 *
 * Where it fits: Read by SignatureReader; InputMaker makes inputs for
 * its parameter types and InputDriver writes the calls.
 */
package gradezzy.modelanswer;

import java.util.ArrayList;
import java.util.List;

import gradezzy.util.TextUtils;

/**
 * One static method that can be called with made-up inputs.
 *
 * <p>Tier: COULD. Owner: Jing Yuan.
 */
public class MethodSignature {

    // Separates the arguments of a call
    private static final String ARGUMENT_SEPARATOR = ", ";

    // The brackets around the arguments of a call
    private static final String OPEN_CALL = "(";
    private static final String CLOSE_CALL = ")";

    // The class the method is in, such as Q1
    private final String className;

    // The method's name, such as compress
    private final String methodName;

    // The return type, such as String or int[]
    private final String returnType;

    // The type of each parameter, such as String or ArrayList<Integer>
    private final List<String> parameterTypes = new ArrayList<>();

    /**
     * Creates a signature with no parameters yet.
     *
     * <p>Tier: COULD.
     *
     * @param className the class the method is in
     * @param methodName the method's name
     * @param returnType the return type
     */
    public MethodSignature(String className, String methodName,
            String returnType) {
        this.className = className;
        this.methodName = methodName;
        this.returnType = returnType;
    }

    /**
     * Returns the class the method is in.
     *
     * <p>Tier: COULD.
     *
     * @return the class name, such as Q1
     */
    public String getClassName() {
        return className;
    }

    /**
     * Returns the method's name.
     *
     * <p>Tier: COULD.
     *
     * @return the method name, such as compress
     */
    public String getMethodName() {
        return methodName;
    }

    /**
     * Returns the return type.
     *
     * <p>Tier: COULD.
     *
     * @return the return type, such as String
     */
    public String getReturnType() {
        return returnType;
    }

    /**
     * Returns the type of each parameter, in order. The list can be
     * added to.
     *
     * <p>Tier: COULD.
     *
     * @return the parameter types
     */
    public List<String> getParameterTypes() {
        return parameterTypes;
    }

    /**
     * Writes a call of the method with some arguments, as Java, such as
     * compress("aab").
     *
     * <p>Tier: COULD.
     *
     * @param arguments each argument as Java text
     * @return the call
     */
    public String describeCall(List<String> arguments) {
        return methodName + OPEN_CALL + TextUtils.join(ARGUMENT_SEPARATOR,
                arguments) + CLOSE_CALL;
    }
}
