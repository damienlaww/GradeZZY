/*
 * File: DemoExports.java
 *
 * Purpose: Builds the CSV and JSON text that demo mode shows as an
 * export preview.
 *
 * Where it fits: Used only by DemoBackend.export. Nothing is written to
 * a file. Live mode uses the real exporters through ExportService.
 * Ungraded marks are left blank, never written as zero.
 *
 * AI use: drafted with Claude Code, reviewed by Jun Sheng.
 */
package gradezzy.ui.demo;

import java.util.List;

import gradezzy.model.GradingRun;
import gradezzy.model.Question;
import gradezzy.model.QuestionResult;
import gradezzy.model.Student;
import gradezzy.model.Submission;
import gradezzy.ui.backend.ResultState;

/**
 * Export previews for demo mode.
 *
 * <p>Tier: SHOULD. Owner: Damien.
 */
public final class DemoExports {

    // The line ending of the preview, matching the score sheet
    private static final String CSV_LINE_END = "\r\n";

    // The separator between CSV values
    private static final String COMMA = ",";

    // The quote around a CSV value that holds a comma
    private static final String QUOTE = "\"";

    // The indentation of one JSON level
    private static final String INDENT = "  ";

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
        StringBuilder csv = new StringBuilder("Username,Last Name,"
                + "First Name");
        for (Question question : run.getAssessment().getQuestions()) {
            csv.append(COMMA).append(question.getId());
        }
        csv.append(",Total").append(CSV_LINE_END);

        for (Submission submission : scope) {
            Student student = submission.getStudent();
            csv.append(csvValue(submission.getUsername())).append(COMMA)
                    .append(csvValue(lastName(student))).append(COMMA)
                    .append(csvValue(firstName(student)));
            boolean graded = ResultState.hasTotal(submission);
            for (Question question : run.getAssessment().getQuestions()) {
                csv.append(COMMA);
                QuestionResult result = find(submission, question);
                if (graded && result != null) {
                    csv.append(format(result.getScore()));
                }
            }
            csv.append(COMMA);
            if (graded) {
                csv.append(format(submission.getTotalScore()));
            }
            csv.append(CSV_LINE_END);
        }
        return csv.toString();
    }

    /**
     * Builds a JSON preview with one object per student.
     *
     * @param run the run
     * @param scope the students to include
     * @return the JSON text
     */
    public static String toJson(GradingRun run, List<Submission> scope) {
        StringBuilder json = new StringBuilder("{\n");
        json.append(INDENT).append("\"run\": ").append(jsonText(run.getId()))
                .append(",\n");
        json.append(INDENT).append("\"section\": ")
                .append(jsonText(run.getSection().getLabel()))
                .append(",\n");
        json.append(INDENT).append("\"students\": [");

        for (int index = 0; index < scope.size(); index++) {
            Submission submission = scope.get(index);
            if (index > 0) {
                json.append(',');
            }
            json.append('\n').append(INDENT).append(INDENT).append("{ ")
                    .append("\"username\": ")
                    .append(jsonText(submission.getUsername()))
                    .append(", \"scores\": {");
            appendScores(json, run, submission);
            json.append(" }, \"total\": ");
            if (ResultState.hasTotal(submission)) {
                json.append(format(submission.getTotalScore()));
            } else {
                json.append("null");
            }
            json.append(" }");
        }
        json.append('\n').append(INDENT).append("]\n}\n");
        return json.toString();
    }

    // Adds "Q1a": 3, ... for one student; ungraded marks are null.
    private static void appendScores(StringBuilder json, GradingRun run,
            Submission submission) {
        boolean graded = ResultState.hasTotal(submission);
        boolean first = true;
        for (Question question : run.getAssessment().getQuestions()) {
            if (!first) {
                json.append(',');
            }
            first = false;
            json.append(' ').append(jsonText(question.getId()))
                    .append(": ");
            QuestionResult result = find(submission, question);
            if (graded && result != null) {
                json.append(format(result.getScore()));
            } else {
                json.append("null");
            }
        }
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
        return "\"" + value.replace("\\", "\\\\").replace("\"", "\\\"")
                + "\"";
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
