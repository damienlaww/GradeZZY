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

import java.io.File;
import java.nio.file.Path;

import gradezzy.model.AnomalyType;
import gradezzy.model.Submission;

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
        } else if (type == AnomalyType.WRONG_ENCODING) {
            return "A file was saved in the wrong text encoding";
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
        } else if (type == AnomalyType.COPIED_HEADER) {
            return "The header names another student";
        } else if (type == AnomalyType.SIMILAR_CODE) {
            return "Very similar to another student's code";
        } else if (type == AnomalyType.DIFFERENT_FROM_MODEL_ANSWER) {
            return "Some random inputs give a different answer";
        } else if (type == AnomalyType.DUPLICATE_SUBMISSION) {
            return "More than one file for this student";
        } else if (type == AnomalyType.INTERNAL_ERROR) {
            return "GradeZZY could not finish grading this student";
        }
        return "Something to check";
    }

    /**
     * Returns a short title for a kind of problem that a fix has put
     * right.
     *
     * @param type the kind
     * @return the title
     */
    public static String fixedTitle(AnomalyType type) {
        if (type == AnomalyType.MISSING_QUESTION_FOLDER) {
            return "A question folder was moved or renamed";
        } else if (type == AnomalyType.MISSING_FILE) {
            return "A file was in another question's folder";
        }
        return title(type);
    }

    /**
     * Shortens the paths in a problem's message for the screen: a file in
     * the working copy is shown from the student's folder, such as
     * david.2024/Q3/Q3.java, and the uploaded file by its name only.
     *
     * @param message the message
     * @param submission the student's submission
     * @return the message with short paths
     */
    public static String shortenPaths(String message,
            Submission submission) {
        if (message == null) {
            return "";
        }
        String shown = message;
        Path workingCopy = submission.getWorkingCopyPath();
        if (workingCopy != null) {
            shown = shortenFrom(shown, workingCopy.toString());
        }
        Path upload = submission.getOriginalPath();
        if (upload != null && upload.getFileName() != null) {
            shown = shown.replace(upload.toString(),
                    upload.getFileName().toString());
        }
        return shown;
    }

    // Removes a folder from the start of the path that follows it in the
    // message, and writes the rest with forward slashes.
    private static String shortenFrom(String message, String folder) {
        int start = message.indexOf(folder + File.separator);
        if (start < 0) {
            return message;
        }
        String before = message.substring(0, start);
        String rest = message.substring(start + folder.length()
                + File.separator.length());
        int end = rest.indexOf(' ');
        if (end < 0) {
            end = rest.length();
        }
        String path = rest.substring(0, end).replace(File.separatorChar,
                '/');
        return before + path + rest.substring(end);
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
                || type == AnomalyType.COPIED_HEADER
                || type == AnomalyType.SIMILAR_CODE
                || type == AnomalyType.DIFFERENT_FROM_MODEL_ANSWER;
    }
}
