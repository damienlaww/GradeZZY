/*
 * File: DemoExports.java
 *
 * Purpose: Builds the CSV and JSON text that demo mode shows as an
 * export preview.
 *
 * Where it fits: Used only by DemoBackend.export; nothing is written to a
 * file. Ungraded marks are left blank (null in JSON), never zero.
 */
package gradezzy.ui.demo;

import java.util.ArrayList;
import java.util.List;

import gradezzy.model.GradingRun;
import gradezzy.model.Question;
import gradezzy.model.QuestionResult;
import gradezzy.model.Student;
import gradezzy.model.Submission;
import gradezzy.ui.backend.ResultState;
import gradezzy.util.TextUtils;

/**
 * Export previews for demo mode.
 *
 * <p>Tier: COULD.
 */
public final class DemoExports {

    // The line ending, matching the score sheet
    private static final String CSV_LINE_END = "\r\n";

    // The separator between CSV values
    private static final String COMMA = ",";

    // The quote around a CSV value that holds a comma or a quote
    private static final String QUOTE = "\"";

    // The indentation of one JSON level
    private static final String INDENT = "  ";

    // The line ending of the JSON preview
    private static final String JSON_LINE_END = "\n";

    // The separator between JSON values
    private static final String JSON_SEPARATOR = ",";

    // The JSON value of a mark that was not graded
    private static final String JSON_NULL = "null";

    // The character that escapes a quote or a backslash in JSON
    private static final String BACKSLASH = "\\";

    // Utility class, never instantiated.
    private DemoExports() {
        // nothing to set up
    }

    /**
     * Builds a CSV preview with one row per student.
     *
     * @param run the run
     * @param scope the students to include
     * @return the CSV text
     */
    public static String toCsv(GradingRun run, List<Submission> scope) {
        String csv = "Username,Last Name,First Name";
        for (Question question : run.getAssessment().getQuestions()) {
            csv += COMMA + question.getId();
        }
        csv += COMMA + "Total" + CSV_LINE_END;

        for (Submission submission : scope) {
            Student student = submission.getStudent();
            csv += csvValue(submission.getUsername()) + COMMA
                    + csvValue(lastName(student)) + COMMA
                    + csvValue(firstName(student));
            boolean graded = ResultState.hasTotal(submission);
            for (Question question : run.getAssessment().getQuestions()) {
                csv += COMMA;
                QuestionResult result = find(submission, question);
                if (graded && result != null) {
                    csv += format(result.getScore());
                }
            }
            csv += COMMA;
            if (graded) {
                csv += format(submission.getTotalScore());
            }
            csv += CSV_LINE_END;
        }
        return csv;
    }

    /**
     * Builds a JSON preview with one object per student.
     *
     * @param run the run
     * @param scope the students to include
     * @return the JSON text
     */
    public static String toJson(GradingRun run, List<Submission> scope) {
        String json = "{" + JSON_LINE_END;
        json += INDENT + "\"run\": " + jsonText(run.getId())
                + JSON_SEPARATOR + JSON_LINE_END;
        json += INDENT + "\"section\": "
                + jsonText(run.getSection().getLabel()) + JSON_SEPARATOR
                + JSON_LINE_END;
        json += INDENT + "\"students\": [";

        List<String> students = new ArrayList<>();
        for (Submission submission : scope) {
            students.add(studentJson(run, submission));
        }
        json += TextUtils.join(JSON_SEPARATOR, students);
        json += JSON_LINE_END + INDENT + "]" + JSON_LINE_END + "}"
                + JSON_LINE_END;
        return json;
    }

    // Returns the JSON object of one student, on a line of its own.
    private static String studentJson(GradingRun run,
            Submission submission) {
        String json = JSON_LINE_END + INDENT + INDENT + "{ "
                + "\"username\": " + jsonText(submission.getUsername())
                + ", \"scores\": {";
        json += scoresText(run, submission);
        json += " }, \"total\": ";
        if (ResultState.hasTotal(submission)) {
            json += format(submission.getTotalScore());
        } else {
            json += JSON_NULL;
        }
        json += " }";
        return json;
    }

    // Returns "Q1a": 3, ... for one student; ungraded marks are null.
    private static String scoresText(GradingRun run, Submission submission) {
        boolean graded = ResultState.hasTotal(submission);
        List<String> marks = new ArrayList<>();
        for (Question question : run.getAssessment().getQuestions()) {
            String mark = " " + jsonText(question.getId()) + ": ";
            QuestionResult result = find(submission, question);
            if (graded && result != null) {
                mark += format(result.getScore());
            } else {
                mark += JSON_NULL;
            }
            marks.add(mark);
        }
        return TextUtils.join(JSON_SEPARATOR, marks);
    }

    // Finds the result of one question.
    private static QuestionResult find(Submission submission,
            Question question) {
        for (QuestionResult result : submission.getQuestionResults()) {
            if (result.getQuestion().getId().equals(question.getId())) {
                return result;
            }
        }
        return null;
    }

    // Quotes a CSV value when it holds a comma or a quote.
    private static String csvValue(String value) {
        if (value.contains(COMMA) || value.contains(QUOTE)) {
            return QUOTE + value.replace(QUOTE, QUOTE + QUOTE) + QUOTE;
        }
        return value;
    }

    // Writes a JSON string with quotes and backslashes escaped.
    private static String jsonText(String value) {
        String escaped = value.replace(BACKSLASH, BACKSLASH + BACKSLASH)
                .replace(QUOTE, BACKSLASH + QUOTE);
        return QUOTE + escaped + QUOTE;
    }

    // Writes a mark without a decimal point when it is whole.
    private static String format(double score) {
        if (score == Math.rint(score)) {
            return String.valueOf((long) score);
        }
        return String.valueOf(score);
    }

    // Returns the last name, or an empty string.
    private static String lastName(Student student) {
        if (student == null) {
            return "";
        }
        return student.getLastName();
    }

    // Returns the first name, or an empty string.
    private static String firstName(Student student) {
        if (student == null) {
            return "";
        }
        return student.getFirstName();
    }
}
