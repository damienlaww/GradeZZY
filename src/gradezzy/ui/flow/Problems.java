/*
 * File: Problems.java
 *
 * Purpose: Says each kind of problem the grader can find in plain words,
 * and which kinds are flags (a suspicion the instructor judges) rather
 * than facts.
 *
 * Where it fits: The student panel uses it for the titles of its fix,
 * flag and problem cards.
 */
package gradezzy.ui.flow;

import gradezzy.model.AnomalyType;

/**
 * Plain words for problem types.
 */
public final class Problems {

    // Not used: every method is static.
    private Problems() {
    }

    /**
     * Returns a short title for a kind of problem.
     *
     * @param type the kind
     * @return the title
     */
    public static String title(AnomalyType type) {
        if (type == AnomalyType.MISSING_WRAPPER_FOLDER) {
            return "Files were not in a folder named after the student";
        } else if (type == AnomalyType.WRONG_FOLDER_NAME) {
            return "The folder had the wrong name";
        } else if (type == AnomalyType.MISSING_QUESTION_FOLDER) {
            return "A question folder is missing";
        } else if (type == AnomalyType.MISSING_FILE) {
            return "A required file is missing";
        } else if (type == AnomalyType.WRONG_FILE_NAME) {
            return "A file had the wrong name";
        } else if (type == AnomalyType.MISSING_HEADER_NAME) {
            return "The name at the top of a file was blank";
        } else if (type == AnomalyType.UNKNOWN_STUDENT) {
            return "Not on the score sheet";
        } else if (type == AnomalyType.INVALID_ARCHIVE) {
            return "The zip file could not be opened";
        } else if (type == AnomalyType.COMPILE_ERROR) {
            return "Did not compile";
        } else if (type == AnomalyType.TIMEOUT) {
            return "A test ran too long and was stopped";
        } else if (type == AnomalyType.RUNTIME_ERROR) {
            return "The program crashed during a test";
        } else if (type == AnomalyType.UNTOUCHED_STARTER) {
            return "Starter file handed in unchanged";
        } else if (type == AnomalyType.EMPTY_ANSWER) {
            return "Answer left empty";
        } else if (type == AnomalyType.HARD_CODED_ANSWER) {
            return "The answer may be hard-coded";
        } else if (type == AnomalyType.SIMILAR_CODE) {
            return "Very similar to another student's code";
        } else if (type == AnomalyType.DUPLICATE_SUBMISSION) {
            return "More than one file for this student";
        } else if (type == AnomalyType.INTERNAL_ERROR) {
            return "GradeZZY could not finish grading this student";
        }
        return "Something to check";
    }

    /**
     * Says whether a kind of problem is a flag: a suspicion that never
     * changes a mark and that the instructor judges.
     *
     * @param type the kind
     * @return true for a flag
     */
    public static boolean isFlag(AnomalyType type) {
        return type == AnomalyType.UNTOUCHED_STARTER
                || type == AnomalyType.EMPTY_ANSWER
                || type == AnomalyType.HARD_CODED_ANSWER
                || type == AnomalyType.SIMILAR_CODE;
    }
}
