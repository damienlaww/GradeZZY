/*
 * File: CallResultReader.java
 *
 * Purpose: Reads what the input program printed into the result of each
 * call, by call number. The lines InputDriver's program prints are kept;
 * lines the student's code printed are skipped.
 *
 * Where it fits: RandomInputChecker runs the input program in the model
 * answer and in each student's folder, and gives its output to this
 * class to read.
 */
package gradezzy.modelanswer;

import java.util.HashMap;
import java.util.Map;

import gradezzy.exec.TesterRunner;

/**
 * Reads the input program's output into the result of each call.
 *
 * <p>Tier: COULD. Owner: Jing Yuan.
 */
class CallResultReader {

    // Splits the program's output into lines
    private static final String LINE_BREAK = "\\R";

    // The parts of one printed line: number, mark, text
    private static final int FIELD_COUNT = 3;
    private static final int NUMBER_FIELD = 0;
    private static final int MARK_FIELD = 1;
    private static final int TEXT_FIELD = 2;

    // Reads the program's trusted lines into results by call number.
    // Lines the student's code printed carry TesterRunner's prefix and
    // are skipped. No result in the map is ever null.
    Map<Integer, CallResult> read(String output) {
        Map<Integer, CallResult> results = new HashMap<>();
        for (String line : output.split(LINE_BREAK)) {
            boolean fromStudent = line.startsWith(
                    TesterRunner.STUDENT_LINE_PREFIX);
            String[] fields = line.split(InputDriver.FIELD_SEPARATOR,
                    FIELD_COUNT);
            if (!fromStudent && fields.length == FIELD_COUNT) {
                addResult(results, fields);
            }
        }
        return results;
    }

    // Adds the result one line holds, when the line is well formed.
    private void addResult(Map<Integer, CallResult> results,
            String[] fields) {
        String mark = fields[MARK_FIELD];
        boolean threw = mark.equals(InputDriver.THREW_MARK);
        boolean value = mark.equals(InputDriver.VALUE_MARK);
        if (!threw && !value) {
            return;
        }
        try {
            int number = Integer.parseInt(fields[NUMBER_FIELD]);
            results.put(number, new CallResult(threw, fields[TEXT_FIELD]));
        } catch (NumberFormatException exception) {
            // Not one of the program's lines
        }
    }
}
