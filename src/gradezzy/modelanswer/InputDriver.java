/*
 * File: InputDriver.java
 *
 * Purpose: Writes the source of a small Java program that calls one
 * method once per input and prints what each call returned, or the
 * exception it threw, one line per call.
 *
 * Where it fits: RandomInputChecker compiles and runs the program in the
 * model answer's folder and in each student's question folder, through
 * TesterRunner, so it gets the same sandbox and limits as a tester. The
 * program is started as the "tester", so only its own lines are
 * trusted; anything the student's method prints is marked as student
 * output and ignored.
 */
package gradezzy.modelanswer;

import java.util.ArrayList;
import java.util.List;

import gradezzy.util.TextUtils;

/**
 * Writes the program that calls one method with every input.
 *
 * <p>Each line it prints is the call's number, then VALUE_MARK and the
 * result, or THREW_MARK and the exception's class, separated by tabs.
 * Every character Java counts as a line break, and tabs, are written as
 * escapes such as \n and \t, so one call is always one line.
 *
 * <p>Tier: COULD. Owner: Jing Yuan.
 */
public class InputDriver {

    /**
     * The name of the program's class. A student class with this name is
     * very unlikely.
     */
    public static final String CLASS_NAME = "GradeZZYRandomInputs";

    /**
     * The name of the program's source file.
     */
    public static final String FILE_NAME = CLASS_NAME + ".java";

    /**
     * Separates the parts of each printed line.
     */
    public static final String FIELD_SEPARATOR = "\t";

    /**
     * Marks a line holding a call's result.
     */
    public static final String VALUE_MARK = "=";

    /**
     * Marks a line holding the class of the exception a call threw.
     */
    public static final String THREW_MARK = "!";

    // The start of the name of the method holding each call
    private static final String CALL_METHOD = "call";

    // Calls are numbered from 1, as they are shown to the instructor
    private static final int FIRST_NUMBER = 1;

    // Ends each line of the written source
    private static final String NEW_LINE = "\n";

    // Joins the class name to the method call, as in Q1.compress("aab")
    private static final String MEMBER_SEPARATOR = ".";

    // The parts of the source that are the same for every method: the
    // start of the class, and the helpers that turn any result into one
    // line of text. Arrays print their elements, not their address.
    // Every class of Java is named in full, such as java.lang.System, so
    // a student class called System or Arrays cannot take its place.
    private static final String[] CLASS_START = {
        "public class " + CLASS_NAME + " {",
        ""
    };
    private static final String[] HELPERS = {
        "    private static void report(int number,",
        "            java.lang.String text) {",
        "        java.lang.String oneLine = text",
        "                .replace(\"\\\\\", \"\\\\\\\\\")",
        "                .replace(\"\\n\", \"\\\\n\")",
        "                .replace(\"\\r\", \"\\\\r\")",
        "                .replace(\"\\t\", \"\\\\t\")",
        "                .replace(java.lang.String.valueOf((char) 11),",
        "                        \"\\\\u000b\")",
        "                .replace(java.lang.String.valueOf((char) 12),",
        "                        \"\\\\u000c\")",
        "                .replace(java.lang.String.valueOf((char) 133),",
        "                        \"\\\\u0085\")",
        "                .replace(java.lang.String.valueOf((char) 8232),",
        "                        \"\\\\u2028\")",
        "                .replace(java.lang.String.valueOf((char) 8233),",
        "                        \"\\\\u2029\");",
        "        java.lang.System.out.println(number + \"\\t" + VALUE_MARK
                + "\\t\" + oneLine);",
        "    }",
        "",
        "    private static void reportThrown(int number,",
        "            java.lang.Throwable problem) {",
        "        java.lang.System.out.println(number + \"\\t" + THREW_MARK
                + "\\t\" + problem.getClass().getName());",
        "    }",
        "",
        "    private static java.lang.String show(java.lang.Object value) {",
        "        return java.lang.String.valueOf(value);",
        "    }",
        "",
        "    private static java.lang.String show(int[] value) {",
        "        return java.util.Arrays.toString(value);",
        "    }",
        "",
        "    private static java.lang.String show(long[] value) {",
        "        return java.util.Arrays.toString(value);",
        "    }",
        "",
        "    private static java.lang.String show(double[] value) {",
        "        return java.util.Arrays.toString(value);",
        "    }",
        "",
        "    private static java.lang.String show(boolean[] value) {",
        "        return java.util.Arrays.toString(value);",
        "    }",
        "",
        "    private static java.lang.String show(char[] value) {",
        "        return java.util.Arrays.toString(value);",
        "    }",
        "",
        "    private static java.lang.String show(java.lang.Object[] value) {",
        "        return java.util.Arrays.deepToString(value);",
        "    }",
        "}"
    };

    /**
     * Writes the program for one method and its inputs. Each call sits
     * in its own method, so a long list of inputs never makes one method
     * too large for Java.
     *
     * <p>Tier: COULD.
     *
     * @param method the method to call
     * @param inputs the inputs, one argument per parameter, as Java text
     * @return the program's source
     */
    public String write(MethodSignature method, List<List<String>> inputs) {
        List<String> lines = new ArrayList<>();
        addAll(lines, CLASS_START);
        addMain(lines, inputs.size());
        for (int index = 0; index < inputs.size(); index++) {
            int number = index + FIRST_NUMBER;
            addCall(lines, method, inputs.get(index), number);
        }
        addAll(lines, HELPERS);
        return TextUtils.join(NEW_LINE, lines) + NEW_LINE;
    }

    // Adds the main method, which makes every call in order.
    private void addMain(List<String> lines, int count) {
        lines.add("    public static void main(java.lang.String[] args) {");
        for (int number = FIRST_NUMBER; number <= count; number++) {
            lines.add("        " + CALL_METHOD + number + "();");
        }
        lines.add("    }");
        lines.add("");
    }

    // Adds the method that makes one call and reports its result.
    private void addCall(List<String> lines, MethodSignature method,
            List<String> arguments, int number) {
        String call = method.getClassName() + MEMBER_SEPARATOR
                + method.describeCall(arguments);
        lines.add("    private static void " + CALL_METHOD + number
                + "() {");
        lines.add("        try {");
        lines.add("            report(" + number + ", show(" + call + "));");
        lines.add("        } catch (java.lang.Throwable problem) {");
        lines.add("            reportThrown(" + number + ", problem);");
        lines.add("        }");
        lines.add("    }");
        lines.add("");
    }

    // Adds every line of a fixed part of the source.
    private void addAll(List<String> lines, String[] part) {
        for (String line : part) {
            lines.add(line);
        }
    }
}
